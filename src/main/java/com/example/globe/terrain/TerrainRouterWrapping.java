package com.example.globe.terrain;

import com.example.globe.GlobeMod;
import com.example.globe.adapter.geo.GeoAuthorityProvider;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.mixin.terrain.RandomStateAccessor;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Shared Phase 4 (Terrain Integration Spike) install logic: the triple gate (design §1.2) + the 15-field
 * {@link NoiseRouter} rebuild wrapping only {@code finalDensity} (#12).
 *
 * <p><b>Why this exists as a standalone helper, not inlined in one mixin.</b> {@code RandomState.create(...)}
 * (the static factory {@code RandomState.create(NoiseGeneratorSettings, HolderGetter, long)}) is called from
 * TWO places in this codebase, not one: vanilla's own {@code ChunkMap} constructor (real gameplay chunk
 * generation) AND this mod's own dev/atlas tooling, {@code com.example.globe.dev.BiomePreviewExporter}
 * (two direct call sites). A mixin on {@code ChunkMap} alone would leave the atlas/dev-tooling path -- and
 * any future headless proof harness built the way design §6.2 describes ("builds a RandomState for
 * globe:overworld at a fixed test seed") -- permanently unwrapped, so the §6 mechanical proof gate would
 * silently never exercise the wrapper even at strength&gt;0. That is the "test passes for the wrong reason"
 * failure class this project's own LESSONS L14 / adversarial-review finding #5 warn about, and it was caught
 * in review before this landed. There is no single vanilla choke point both paths go through with a usable
 * positive globe check in scope (hooking {@code RandomState.create} itself has no {@code ResourceKey}/
 * generator/{@code ServerLevel} available -- only a flattened {@code NoiseGeneratorSettings} value, and the
 * six globe presets' settings values are byte-for-byte structurally identical to vanilla's own coarse fields
 * (sea_level=63, min_y=-64, height=384, stone/water) so there is no cheap registry-free fingerprint either --
 * see the design's §1.2 discussion of why the {@code RandomState}-construction site cannot gate reliably on
 * its own). The fix is therefore to make BOTH paths call this same helper directly, at their own call sites.
 *
 * <p><b>Why there are TWO overloads, not one shared {@code ServerLevel} signature.</b> The obvious design
 * -- one {@code installIfArmed(RandomState, ServerLevel)} calling the full
 * {@link GlobeMod#isGlobeOverworld(ServerLevel)} check (which covers both the persisted-{@code LatitudeWorldState}
 * mechanism dev/atlas tooling uses AND the generator-settings-key mechanism real gameplay uses) -- was tried
 * and does NOT work from the {@code ChunkMap} constructor: {@code isGlobeOverworld} internally calls
 * {@code world.getDataStorage() -> world.getChunkSource().getDataStorage()}, and {@code ServerLevel.chunkSource}
 * is verified (via the 26.2 jar) to still be {@code null} at that exact point -- {@code ChunkMap} is
 * constructed *by* {@code ServerChunkCache}'s own constructor, which only assigns its {@code chunkMap} field
 * (and by extension makes itself visible as {@code ServerLevel.chunkSource}) AFTER the {@code ChunkMap}
 * constructor returns. Calling the full check there throws a {@code NullPointerException} on every single
 * invocation (caught safely by the outer try/catch below, but permanently unable to arm real gameplay).
 * The fix is two narrower overloads instead of one broad one:
 * <ul>
 *   <li>{@link #installIfArmed(RandomState, NoiseBasedChunkGenerator)} -- for the {@code ChunkMap} mixin,
 *       using ONLY the generator-settings-key check ({@link GlobeMod#isGlobeNoiseGenerator}), fed from the
 *       generator the {@code ChunkMap} constructor already received as a plain argument (no
 *       {@code getChunkSource()} indirection needed, so no ordering hazard).</li>
 *   <li>{@link #installIfArmed(RandomState, ServerLevel)} -- for {@code BiomePreviewExporter}'s dev/atlas
 *       call sites, using the full {@link GlobeMod#isGlobeOverworld(ServerLevel)} check (both mechanisms),
 *       safe there because the exporter's {@code ServerLevel} is already fully constructed and ticking by
 *       the time it runs.</li>
 * </ul>
 *
 * <p><b>Package note:</b> deliberately NOT in {@code com.example.globe.mixin.*} -- that package tree is
 * reserved for {@code @Mixin} classes by {@code globe.mixins.json}'s {@code "package"} declaration, and
 * Fabric/Mixin throws {@code IllegalClassLoadError} if ordinary code (like
 * {@code com.example.globe.dev.BiomePreviewExporter}) references a class under it directly. This class (and
 * {@link GeoTerrainBiasFunction}) live in plain code so both the {@code ChunkMap} mixin and the dev-tooling
 * call sites can call them.
 *
 * <p>Design of record: {@code docs/design/terrain-wrapper-design-20260705.md} (locked r2), §1.2/§4.2/§8
 * item 6/6a.
 */
public final class TerrainRouterWrapping {

    private static final AtomicBoolean INSTALL_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean(false);
    /**
     * Slice B (audit P1-2): {@code terrainV2} armed while {@code geoV2} is off is a silently-nonsensical
     * combo (the wrapper reads GeoAuthority's geography and can never install). Warn once per JVM --
     * deliberately NOT re-armed per world, because both flags are {@code static final} and the condition
     * cannot change within a JVM.
     */
    private static final AtomicBoolean TERRAINV2_WITHOUT_GEOV2_WARNED = new AtomicBoolean(false);

    private TerrainRouterWrapping() {
    }

    /**
     * Result of an {@code installIfArmed} attempt -- lets a caller (notably
     * {@code com.example.globe.dev.TerrainProofHarness}) assert DEFINITIVELY whether the wrapper was
     * installed on a given {@link RandomState}, instead of scraping a once-per-JVM install log (which cannot
     * distinguish "did not install" from "already logged on an earlier world in this JVM"). Sweeper audit
     * findings #2-6/#8: the report needs an install-status field it can assert on, not just a label.
     */
    public enum InstallResult {
        /**
         * The 15-field {@link NoiseRouter} was rebuilt and {@code finalDensity} (#12) is now structurally
         * wrapped by {@link GeoTerrainBiasFunction}. This does NOT guarantee the bias is currently taking
         * effect: {@code GeoTerrainBiasFunction.compute()} independently re-checks, on every call, whether
         * {@code GEO_V2_PROVIDER} is a real {@link GeoAuthorityProvider} yet (not the {@code NoOp}
         * placeholder) and silently passes through the unmodified delegate value until it is -- see that
         * class's javadoc for why this check lives there now, not here.
         */
        INSTALLED,
        /** Gate (2) {@link LatitudeV2Flags#TERRAIN_V2_ENABLED} or (3) {@link LatitudeV2Flags#GEO_V2_ENABLED} is off. */
        SKIPPED_FLAG_OFF,
        /** The real positive globe check returned false (vanilla / Terralith-only / non-globe world). */
        SKIPPED_NOT_GLOBE,
        /** {@code randomState} was null, its router was null, or the rebuild threw (fell back to vanilla). */
        SKIPPED_NULL_OR_ERROR
    }

    /**
     * Real-gameplay overload: installs the wrapper iff {@code generator} is keyed to one of the six
     * {@code globe:overworld*} presets ({@link GlobeMod#isGlobeNoiseGenerator}). Use this overload only from
     * a site (like the {@code ChunkMap} constructor) where a fully-constructed {@code ServerLevel} is NOT
     * yet safely available -- see the class javadoc for why {@code isGlobeOverworld(ServerLevel)} cannot be
     * used there.
     *
     * @return the {@link InstallResult} so callers can assert on the outcome directly.
     */
    public static InstallResult installIfArmed(RandomState randomState, NoiseBasedChunkGenerator generator) {
        return installIfArmedCore(randomState, () -> GlobeMod.isGlobeNoiseGenerator(generator));
    }

    /**
     * Dev/atlas-tooling overload: installs the wrapper iff {@code world} is a genuine globe world by ANY of
     * three mechanisms: {@link GlobeMod#isGlobeOverworld(ServerLevel)} (persisted {@code LatitudeWorldState}
     * radius, stamped by the real create-world flow; or generator-settings-key, for real gameplay), OR
     * {@link LatitudeBiomes#getActiveRadiusBlocks()}{@code > 0} -- the static radius this mod's own
     * headless/atlas tooling ({@code BiomePreviewHeadlessRunner}) sets directly via
     * {@code LatitudeBiomes.setActiveRadiusBlocks(...)} on a plain {@code minecraft:normal} world, entirely
     * independent of {@code LatitudeWorldState} (the headless runner never goes through the create-world
     * flow that stamps it). This third check is also semantically the right one regardless of mechanism:
     * it is the EXACT condition under which {@code GEO_V2_PROVIDER} itself stops being the NEUTRAL
     * all-ocean no-op provider (see {@code LatitudeBiomes.rebuildGeoAuthority()} -- it only builds a real
     * {@code GeoAuthorityProvider} when {@code seed != 0 && zRadius > 0}), so gating on it ties this
     * wrapper's install decision to the same condition that already governs whether the geography field it
     * reads is live at all. Use this overload only from a site where {@code world.getChunkSource()} is
     * already safely non-null (i.e. NOT from inside {@code ChunkMap}'s own constructor -- see the class
     * javadoc).
     *
     * <p><b>Process-global leak note (sweeper finding #18).</b> The {@code getActiveRadiusBlocks() > 0} branch
     * reads a JVM-static radius. If a globe world armed it and a DIFFERENT world (e.g. a second world booted in
     * the same JVM, or a dev/atlas run that set the static radius directly) then calls this overload, that
     * second world would be treated as armed even if it is not itself a globe world. In real gameplay this is
     * a non-issue (one JVM = one world; and the {@code isGlobeOverworld(world)} branch is the authoritative
     * per-world check), but for dev-tooling/harness contexts where multiple worlds may boot in one JVM it is a
     * real hazard. The two mitigations already in place: (1) {@code GeoTerrainBiasFunction.compute()} still
     * independently re-checks, on every call, that {@code GEO_V2_PROVIDER} is a real {@link GeoAuthorityProvider}
     * (finding #7's original protection, now checked per-call rather than once at install time -- see that
     * class's javadoc), which no-ops the bias whenever the currently-active provider isn't real for whatever
     * world is actually generating; and (2) the proof harness boots exactly one world per forked JVM. The
     * {@code ChunkMap} real-gameplay path uses the OTHER ({@code NoiseBasedChunkGenerator}) overload, which
     * has no static-radius branch at all.
     */
    public static InstallResult installIfArmed(RandomState randomState, ServerLevel world) {
        return installIfArmedCore(randomState, () ->
                LatitudeBiomes.getActiveRadiusBlocks() > 0 || (world != null && GlobeMod.isGlobeOverworld(world)));
    }

    /**
     * Shared core: the triple gate (design §1.2) + the 15-field {@link NoiseRouter} rebuild wrapping only
     * {@code finalDensity} (#12). {@code globeCheck} supplies gate (1) (the real positive globe check,
     * whichever mechanism is safe for the caller's site); gates (2)/(3) are
     * {@link LatitudeV2Flags#TERRAIN_V2_ENABLED} / {@link LatitudeV2Flags#GEO_V2_ENABLED}. If any gate fails,
     * or {@code randomState} is null, this is a no-op and the router is left completely untouched -&gt;
     * byte-identical output.
     *
     * <p>Idempotent-safe to call more than once on the same {@code RandomState} in the sense that calling it
     * again after a successful install would wrap an already-wrapped {@code finalDensity} a second time;
     * callers must call this exactly once per freshly-constructed {@code RandomState}, immediately after
     * {@code RandomState.create(...)} returns (all current call sites already satisfy this).
     *
     * <p>The entire body is wrapped in {@code try/catch(Throwable)}: any failure logs once and leaves the
     * vanilla {@link NoiseRouter} untouched (design §4.2, the outer of the two independent fallback layers;
     * {@link GeoTerrainBiasFunction#compute} is the inner one).
     */
    private static InstallResult installIfArmedCore(RandomState randomState, BooleanSupplier globeCheck) {
        // S36: the ONE router rebuild now serves two independent layers. terrainArmed keeps its original
        // double gate (the bias reads GeoAuthority, useless without geoV2 -- warn preserved verbatim).
        // voidArmed needs NEITHER terrain nor geo (it reads only router #9 depth + the armed radius; sweep
        // REQUIRED-FIX 2), and per sweep REQUIRED-FIX 3 it arms ONLY with strength > 0 -- "strength 0 =
        // exact no-op" holds by construction because the layer is simply never built.
        boolean terrainArmed = LatitudeV2Flags.TERRAIN_V2_ENABLED && LatitudeV2Flags.GEO_V2_ENABLED;
        boolean voidArmed = LatitudeV2Flags.VOID_TAMING_ENABLED && LatitudeV2Flags.VOID_TAMING_STRENGTH > 0.0;
        if (LatitudeV2Flags.TERRAIN_V2_ENABLED && !LatitudeV2Flags.GEO_V2_ENABLED
                && TERRAINV2_WITHOUT_GEOV2_WARNED.compareAndSet(false, true)) {
            GlobeMod.LOGGER.warn(
                    "[Latitude] latitude.terrainV2.enabled=true but latitude.geoV2.enabled is OFF -- the "
                            + "terrain wrapper reads GeoAuthority's geography and will NEVER install in this "
                            + "configuration. Add -Dlatitude.geoV2.enabled=true (or drop the terrainV2 flag).");
        }
        if (!terrainArmed && !voidArmed) {
            return InstallResult.SKIPPED_FLAG_OFF;
        }
        try {
            if (randomState == null) {
                return InstallResult.SKIPPED_NULL_OR_ERROR;
            }
            // Real positive globe check: true for a genuine Latitude globe world (mechanism depends on
            // caller -- see class javadoc), false for vanilla / Terralith-only / other-mod / non-globe
            // worlds (design §1.2).
            if (!globeCheck.getAsBoolean()) {
                return InstallResult.SKIPPED_NOT_GLOBE;
            }

            // Sweeper finding #7 (CRITICAL) originally added an `instanceof GeoAuthorityProvider` check
            // HERE, at one-time install time, to stop the wrapper installing while GEO_V2_PROVIDER is still
            // NoOpGeoSummaryProvider.INSTANCE (land01==0.0 everywhere -- reads as "100% ocean", not "no
            // signal"). That was necessary but wrongly homed: a real ordering bug (found live, 2026-07-06)
            // showed this install path (the ChunkMap-constructor mixin, real gameplay) runs BEFORE
            // GlobeMod's create-world flow rebuilds GEO_V2_PROVIDER for the new world's actual seed/radius,
            // so the one-time check always saw the still-NoOp provider and PERMANENTLY refused to install --
            // on every freshly created world, not just seed-0 ones. The realness check now lives instead in
            // GeoTerrainBiasFunction.compute() itself (see that class's javadoc), re-evaluated on every call
            // rather than snapshotted once here, so the wrapper always installs structurally once the gates
            // above pass, and simply no-ops per-column until the real provider is ready -- which resolves
            // itself moments after world load, long before any chunk is actually generated for a player, and
            // stays a no-op forever on a genuine seed-0 world where the provider never becomes real at all.
            NoiseRouter original = randomState.router();
            if (original == null) {
                return InstallResult.SKIPPED_NULL_OR_ERROR;
            }
            DensityFunction originalFinalDensity = original.finalDensity();

            // S36 composition (sweep REQUIRED-FIX 1: each layer gates on ITS OWN armed flag, never the
            // other's -- a void-only world must not wake the terrain bias via a stray terrainV2.strength,
            // and #11's bathymetry clamp belongs to the terrain feature alone). Void wraps OUTERMOST
            // (architect (d)): it reads the ORIGINAL #9 depth field as its sky/underground discriminator,
            // so composition order cannot change its gate; both layers independently no-op per their own
            // strength discipline.
            DensityFunction f12 = originalFinalDensity;
            if (terrainArmed) {
                f12 = new GeoTerrainBiasFunction(f12);
            }
            if (voidArmed) {
                f12 = new VoidTamingFunction(f12, original.depth());
            }
            DensityFunction f11 = terrainArmed
                    // 11: Slice C-2 — wrapped with the block-unit-correct bathymetry ceiling clamp
                    // (min(prelim, carveCeilY)); exact pass-through whenever no carve applies. See
                    // GeoTerrainPrelimSurfaceFunction's javadoc for why the locked design's refusal to
                    // touch #11 (an additive DENSITY term — a unit mismatch, L16) does not apply to a
                    // block-Y clamp, and why the fluid/aquifer system requires it once real carving exists.
                    ? new GeoTerrainPrelimSurfaceFunction(original.preliminarySurfaceLevel())
                    : original.preliminarySurfaceLevel();

            // Rebuild the 15-field canonical NoiseRouter, wrapping ONLY #11/#12 as armed above and passing
            // every other field through by identity. Field order verified against the 26.2 deobf jar.
            NoiseRouter rebuilt = new NoiseRouter(
                    original.barrierNoise(),               // 1
                    original.fluidLevelFloodednessNoise(), // 2
                    original.fluidLevelSpreadNoise(),      // 3
                    original.lavaNoise(),                  // 4
                    original.temperature(),                // 5
                    original.vegetation(),                 // 6
                    original.continents(),                 // 7
                    original.erosion(),                    // 8
                    original.depth(),                      // 9
                    original.ridges(),                     // 10
                    f11,                                   // 11
                    f12,                                   // 12
                    original.veinToggle(),                 // 13
                    original.veinRidged(),                 // 14
                    original.veinGap());                   // 15

            ((RandomStateAccessor) (Object) randomState).globe$setRouter(rebuilt);

            if (INSTALL_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.info(
                        "[Latitude] Router wrap installed for a globe world: terrain={} (strength={}, "
                                + "oceanStrengthRatio={}), voidTaming={} (strength={}, onset={}, full={}, floorY={}).",
                        terrainArmed,
                        LatitudeV2Flags.TERRAIN_V2_STRENGTH,
                        LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO,
                        voidArmed,
                        LatitudeV2Flags.VOID_TAMING_STRENGTH,
                        LatitudeV2Flags.VOID_TAMING_ONSET_DEG,
                        LatitudeV2Flags.VOID_TAMING_FULL_DEG,
                        LatitudeV2Flags.VOID_TAMING_PROTECT_FLOOR_Y);
            }
            return InstallResult.INSTALLED;
        } catch (Throwable t) {
            // Any failure: leave the vanilla NoiseRouter untouched, log once (design §4.2).
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.warn("[Latitude] Phase 4 terrain-bias install failed; leaving vanilla NoiseRouter in place.", t);
            }
            return InstallResult.SKIPPED_NULL_OR_ERROR;
        }
    }

    /**
     * Resets the once-per-JVM "log latches" so an install (or install failure) on a SECOND world loaded in
     * the same JVM is logged again instead of silently suppressed (sweeper finding #16). Call on world
     * unload/reload. This does NOT affect install behavior -- it only re-arms the informational one-shot
     * logs; the actual install decision is recomputed per {@code RandomState} regardless.
     */
    public static void resetLogLatchesForNewWorld() {
        INSTALL_LOGGED.set(false);
        FAILURE_LOGGED.set(false);
        // Slice B: the per-call wrapper's own one-shots (not-ready / engaged / bias-failure) and the
        // "authorities inert for this world" warn re-arm per world too, so a second world's story is told.
        GeoTerrainBiasFunction.resetLogLatchesForNewWorld();
        VoidTamingFunction.resetLogLatchesForNewWorld();
        LatitudeBiomes.resetV2InertWarnLatchForNewWorld();
    }
}
