package com.example.globe.terrain;

import com.example.globe.GlobeMod;
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

    private TerrainRouterWrapping() {
    }

    /**
     * Real-gameplay overload: installs the wrapper iff {@code generator} is keyed to one of the six
     * {@code globe:overworld*} presets ({@link GlobeMod#isGlobeNoiseGenerator}). Use this overload only from
     * a site (like the {@code ChunkMap} constructor) where a fully-constructed {@code ServerLevel} is NOT
     * yet safely available -- see the class javadoc for why {@code isGlobeOverworld(ServerLevel)} cannot be
     * used there.
     */
    public static void installIfArmed(RandomState randomState, NoiseBasedChunkGenerator generator) {
        installIfArmedCore(randomState, () -> GlobeMod.isGlobeNoiseGenerator(generator));
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
     */
    public static void installIfArmed(RandomState randomState, ServerLevel world) {
        installIfArmedCore(randomState, () ->
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
    private static void installIfArmedCore(RandomState randomState, BooleanSupplier globeCheck) {
        if (!LatitudeV2Flags.TERRAIN_V2_ENABLED || !LatitudeV2Flags.GEO_V2_ENABLED) {
            return;
        }
        try {
            if (randomState == null) {
                return;
            }
            // Real positive globe check: true for a genuine Latitude globe world (mechanism depends on
            // caller -- see class javadoc), false for vanilla / Terralith-only / other-mod / non-globe
            // worlds (design §1.2).
            if (!globeCheck.getAsBoolean()) {
                return;
            }

            NoiseRouter original = randomState.router();
            if (original == null) {
                return;
            }
            DensityFunction originalFinalDensity = original.finalDensity();

            // Rebuild the 15-field canonical NoiseRouter, wrapping ONLY finalDensity (#12) and passing every
            // other field through by identity. Field order verified against the 26.2 deobf jar this session.
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
                    original.preliminarySurfaceLevel(),    // 11 (passed through UNCHANGED)
                    new GeoTerrainBiasFunction(originalFinalDensity), // 12 (the ONLY wrapped field)
                    original.veinToggle(),                 // 13
                    original.veinRidged(),                 // 14
                    original.veinGap());                   // 15

            ((RandomStateAccessor) (Object) randomState).globe$setRouter(rebuilt);

            if (INSTALL_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.info(
                        "[Latitude] Phase 4 terrain bias installed: wrapped NoiseRouter.finalDensity for a globe world "
                                + "(strength={}, oceanStrengthRatio={}).",
                        LatitudeV2Flags.TERRAIN_V2_STRENGTH,
                        LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO);
            }
        } catch (Throwable t) {
            // Any failure: leave the vanilla NoiseRouter untouched, log once (design §4.2).
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.warn("[Latitude] Phase 4 terrain-bias install failed; leaving vanilla NoiseRouter in place.", t);
            }
        }
    }
}
