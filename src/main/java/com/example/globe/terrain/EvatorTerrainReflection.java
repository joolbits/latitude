package com.example.globe.terrain;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.mixin.terrain.RandomStateAccessor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) P1 -- the PRODUCTION terrain-mirror install helper: the promoted,
 * gated, guarded successor of the throwaway {@code EvatorSpike} (which the design proved mechanism "b" with,
 * and which is retained untouched as evidence). It rewrites {@code preliminarySurfaceLevel} (#11) and
 * {@code finalDensity} (#12) so every coordinate-bearing LEAF is wrapped in {@link EvatorReflectLeafFunction},
 * installing the rebuilt {@link NoiseRouter} through the same {@link RandomStateAccessor#globe$setRouter}
 * seam Phase 4's {@link TerrainRouterWrapping} uses. The other 13 router fields (incl. the aquifer noises
 * #1-#4) are left CANONICAL, matching the design's "mirror terrain SHAPE, accept unmirrored aquifer/ore/carver"
 * scope.
 *
 * <h2>Gate</h2>
 * Install is gated ONLY on {@link LatitudeV2Flags#EVATOR_V2_ENABLED} (the global flag) plus the caller's own
 * globe check -- NOT on the per-world capture. The per-world capture ({@code LatitudeBiomes.isEvatorActive()})
 * is re-checked per SAMPLE inside {@link EvatorMirror#reflectEastBlock}, so the wrapper installs structurally
 * whenever the flag is on for a globe world and simply no-ops (passes through) until the capture is pushed and
 * forever on a captured-off world -- see {@link EvatorMirror} for why this per-call re-check is required (the
 * {@code ChunkMap}-constructor install-ordering hazard). Flag OFF -> this is a no-op and the router is left
 * completely untouched (the hard byte-identity gate).
 *
 * <h2>Fail-loud unknown-leaf guard (future MC/Terralith update)</h2>
 * Before rebuilding, {@link #install} walks the real #11/#12 graphs and classifies every node's simple class
 * name as either a {@link #REFLECT_LEAF_NAMES reflected coordinate leaf} or a {@link #KNOWN_SAFE_NAMES known
 * X-independent / structural node}. Any class in NEITHER set is an UNKNOWN, possibly-coordinate-bearing leaf
 * introduced by a future Minecraft or Terralith update -- reflecting it wrongly (or failing to) would silently
 * break the mirror. In that case the install REFUSES: it logs the offending class name(s) once and returns
 * {@link InstallResult#REFUSED_UNKNOWN_LEAF} WITHOUT touching the router, so the world generates
 * unmirrored-but-sane rather than subtly-wrong. The inventory below was audited against the live 26.2
 * vanilla+Terralith overworld router (5756 nodes on #12, 3206 on #11) by the spike probe's leaf-inventory dump.
 */
public final class EvatorTerrainReflection {

    private EvatorTerrainReflection() {
    }

    /** The coordinate-(X)-bearing leaf types (reflected). Matched on the class's simple name (after the {@code $}).
     *  Same inventory the spike proved coverage-complete against the live 26.2 router. */
    static final Set<String> REFLECT_LEAF_NAMES = Set.of(
            "Noise",                     // DensityFunctions$Noise
            "ShiftedNoise",              // reads ctx directly + has shift children
            "ShiftA", "ShiftB", "Shift", // ShiftNoise family
            "ShiftNoise",                // abstract base, defensive
            "BlendedNoise",              // present (3x) in the 26.2 router
            "WeirdScaledSampler",        // absent in 26.2, defensive
            "EndIslandDensityFunction"   // end-only, defensive
    );

    /** X-INDEPENDENT / structural nodes that reach coordinates ONLY through their children (which are visited
     *  and wrapped bottom-up), plus Y-only / constant nodes, cache/interpolation markers, and this mod's own
     *  wrapper classes. Audited against the live 26.2 vanilla+Terralith #11/#12 inventory (every "skip" class
     *  the spike's dump reported), with a few defensive vanilla entries. Anything NOT here and NOT in
     *  {@link #REFLECT_LEAF_NAMES} trips the fail-loud guard. */
    static final Set<String> KNOWN_SAFE_NAMES = Set.of(
            // observed in the live 26.2 finalDensity(#12) + preliminarySurfaceLevel(#11) graphs:
            "YClampedGradient", "BlendOffset", "BlendAlpha", "Marker", "MulOrAdd", "Ap2", "HolderHolder",
            "Constant", "Mapped", "Spline", "IntervalSelect", "Clamp", "RangeChoice", "FindTopSurface",
            // defensive vanilla structural / cache node names not present in this router but harmless to allow:
            "Beardifier", "BeardifierMarker", "CacheAllInCell", "Cache2D", "CacheOnce", "FlatCache",
            "Interpolated", "NoiseHolder",
            // this mod's own density-function wrappers (present when terrainV2 also wrapped the graph, or when
            // a prior evator install is re-walked): pass through, never re-reflect.
            "EvatorReflectLeafFunction", "GeoTerrainBiasFunction", "GeoTerrainPrelimSurfaceFunction"
    );

    private static final AtomicBoolean INSTALL_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean UNKNOWN_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean(false);

    /**
     * P1 sweep HIGH fix -- the install-success SIGNAL. True only after {@link #install} actually returned
     * {@link InstallResult#INSTALLED} for the current world; false at boot, after any refusal/failure, and
     * after the per-world reset ({@link #resetInstalledForServerStop}, chained from
     * {@code LatitudeBiomes.resetWorldgenStateForServerStop()} with the other worldgen statics).
     *
     * <p><b>Why this exists:</b> the biome remap and the terrain reflection MUST engage together. If the
     * install refuses (unknown leaf, a future MC/Terralith update) or errors, the terrain stays canonical --
     * and a biome remap gated only on flag+capture would then paint MIRRORED biomes over UNMIRRORED terrain:
     * worse than either extreme, and a violation of the guard's "unmirrored-but-sane" promise.
     * {@link EvatorMirror#active()} therefore folds this signal in ({@code flag && captured && installed}),
     * so EVERY consumer -- the biome remap, the leaf reflector's own per-sample predicate, and the P2
     * crossing trigger (CONTRACT: it must consult this same signal and degrade to the B-5 prompt path when
     * the mirror is not actually installed) -- inherits the correct gate automatically.
     */
    private static volatile boolean TERRAIN_MIRROR_INSTALLED = false;

    /** True iff the leaf-reflected router was actually installed for the current world (see field javadoc). */
    public static boolean isTerrainMirrorInstalled() {
        return TERRAIN_MIRROR_INSTALLED;
    }

    /** Per-world reset, chained from {@code LatitudeBiomes.resetWorldgenStateForServerStop()} alongside the
     *  other worldgen statics (EVATOR_ACTIVE etc.), so a next world in the same JVM never inherits a stale
     *  "installed" from a previous one. */
    public static void resetInstalledForServerStop() {
        TERRAIN_MIRROR_INSTALLED = false;
    }

    /** Outcome of an {@link #install} attempt, so callers (notably the proof harness) can assert on it. */
    public enum InstallResult {
        /** #11 + #12 were rebuilt with leaf reflectors installed below the interpolation markers. */
        INSTALLED,
        /** {@link LatitudeV2Flags#EVATOR_V2_ENABLED} is off -- router left completely untouched (byte-identical). */
        SKIPPED_FLAG_OFF,
        /** An unknown coordinate-bearing leaf class was found -- refused, router untouched, world unmirrored-sane. */
        REFUSED_UNKNOWN_LEAF,
        /** {@code randomState}/router was null or the rebuild threw -- fell back to the vanilla router. */
        SKIPPED_NULL_OR_ERROR
    }

    /**
     * Install the terrain mirror on {@code randomState}'s router iff {@link LatitudeV2Flags#EVATOR_V2_ENABLED}.
     * The caller is responsible for the globe check (real gameplay: {@code isGlobeNoiseGenerator}; the proof
     * harness: it is a controlled globe proof). Idempotent-unsafe like {@link TerrainRouterWrapping}: call once
     * per freshly-created {@code RandomState}.
     */
    public static InstallResult install(RandomState randomState) {
        if (!LatitudeV2Flags.EVATOR_V2_ENABLED) {
            return InstallResult.SKIPPED_FLAG_OFF;
        }
        try {
            if (randomState == null) {
                return InstallResult.SKIPPED_NULL_OR_ERROR;
            }
            NoiseRouter original = randomState.router();
            if (original == null) {
                return InstallResult.SKIPPED_NULL_OR_ERROR;
            }
            DensityFunction prelim = original.preliminarySurfaceLevel();
            DensityFunction finalDensity = original.finalDensity();

            // Fail-loud guard: refuse (leave the router untouched) if the graph contains a node class we neither
            // reflect nor know to be safe to pass through -- a future MC/Terralith density-graph change.
            Set<String> unknown = collectUnknownClasses(prelim);
            unknown.addAll(collectUnknownClasses(finalDensity));
            if (!unknown.isEmpty()) {
                if (UNKNOWN_LOGGED.compareAndSet(false, true)) {
                    GlobeMod.LOGGER.error(
                            "[Latitude evator] REFUSING to install the terrain mirror: unknown coordinate-bearing "
                                    + "density-leaf class(es) {} in the overworld router -- a Minecraft/Terralith update "
                                    + "changed the density graph since this inventory was audited (26.2). The world will "
                                    + "generate UNMIRRORED but sane (the biome remap and the P2 crossing trigger both "
                                    + "gate on the install-success signal, so neither engages); update "
                                    + "EvatorTerrainReflection's REFLECT/KNOWN_SAFE inventory to restore the evator.",
                            unknown);
                }
                TERRAIN_MIRROR_INSTALLED = false;
                return InstallResult.REFUSED_UNKNOWN_LEAF;
            }

            NoiseRouter rebuilt = rebuild(original, reflectLeaves(prelim), reflectLeaves(finalDensity));
            ((RandomStateAccessor) (Object) randomState).globe$setRouter(rebuilt);
            TERRAIN_MIRROR_INSTALLED = true;
            if (INSTALL_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.info(
                        "[Latitude evator] terrain mirror installed: leaf-reflected preliminarySurfaceLevel(#11) + "
                                + "finalDensity(#12) for a globe world (per-world capture gates each sample).");
            }
            return InstallResult.INSTALLED;
        } catch (Throwable t) {
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                GlobeMod.LOGGER.warn("[Latitude evator] terrain-mirror install failed; leaving vanilla router in place.", t);
            }
            TERRAIN_MIRROR_INSTALLED = false;
            return InstallResult.SKIPPED_NULL_OR_ERROR;
        }
    }

    /** Walk {@code graph} and wrap every {@link #REFLECT_LEAF_NAMES} leaf in {@link EvatorReflectLeafFunction};
     *  all other nodes pass through so their (already-visited) leaf children carry the reflection. */
    static DensityFunction reflectLeaves(DensityFunction graph) {
        return graph.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction fn) {
                return isReflectLeaf(fn) ? new EvatorReflectLeafFunction(fn) : fn;
            }
        });
    }

    /** Non-mutating walk: collect the simple names of any nodes that are neither a reflected leaf nor a
     *  known-safe passthrough. Public so the proof harness can audit the live router's guard coverage. */
    public static Set<String> collectUnknownClasses(DensityFunction graph) {
        Set<String> unknown = new LinkedHashSet<>();
        graph.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction fn) {
                String name = simpleName(fn.getClass());
                if (!REFLECT_LEAF_NAMES.contains(name) && !KNOWN_SAFE_NAMES.contains(name)) {
                    unknown.add(name);
                }
                return fn;
            }
        });
        return unknown;
    }

    private static boolean isReflectLeaf(DensityFunction fn) {
        if (fn instanceof EvatorReflectLeafFunction) {
            return false; // already wrapped; never double-wrap.
        }
        return REFLECT_LEAF_NAMES.contains(simpleName(fn.getClass()));
    }

    private static String simpleName(Class<?> c) {
        String n = c.getName();
        int dollar = n.lastIndexOf('$');
        int dot = n.lastIndexOf('.');
        return n.substring(Math.max(dollar, dot) + 1);
    }

    /** Rebuild the 15-field router substituting only #11 (prelim) and #12 (finalDensity). Field order verified
     *  against the 26.2 deobf jar (identical to {@link TerrainRouterWrapping}'s rebuild). */
    private static NoiseRouter rebuild(NoiseRouter original, DensityFunction prelim11, DensityFunction final12) {
        return new NoiseRouter(
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
                prelim11,                              // 11
                final12,                               // 12
                original.veinToggle(),                 // 13
                original.veinRidged(),                 // 14
                original.veinGap());                   // 15
    }

    /** Re-arm the once-per-JVM install/refusal/failure logs so a second world in the same JVM tells its story. */
    public static void resetLogLatchesForNewWorld() {
        INSTALL_LOGGED.set(false);
        UNKNOWN_LOGGED.set(false);
        FAILURE_LOGGED.set(false);
    }
}
