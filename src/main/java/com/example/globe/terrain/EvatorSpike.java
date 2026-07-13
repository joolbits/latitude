package com.example.globe.terrain;

import com.example.globe.mixin.terrain.RandomStateAccessor;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================ SPIKE — THROWAWAY PROOF CODE (B-6 P1 STEP 0) ============================
 *
 * <p>Install helper for the B-6 terrain-mirror mechanism spike. Lives in the {@code terrain} package (not
 * {@code dev}) for the same reason {@link TerrainRouterWrapping} does: it must reference
 * {@link RandomStateAccessor} (in the mixin package), which ordinary {@code dev} code may not touch
 * directly (Fabric/Mixin {@code IllegalClassLoadError}). The probe in {@code com.example.globe.dev} calls
 * into here.
 *
 * <p>Two mechanisms, selected by {@code -Dlatitude.evatorSpike}:
 * <ul>
 *   <li>{@code =wrap} (or legacy {@code =true}) — mechanism (a): rebuild the 15-field {@link NoiseRouter}
 *       wrapping ONLY {@code finalDensity} (#12) at its ROOT with {@link EvatorMirrorSpikeFunction}. REFUTED
 *       (foreign-context interpolator fallback → raw east band). Kept as evidence.</li>
 *   <li>{@code =leaf} — mechanism (b): rebuild the router with {@code preliminarySurfaceLevel} (#11) and
 *       {@code finalDensity} (#12) each rewritten by {@link #reflectLeaves} — every coordinate-bearing LEAF
 *       wrapped in {@link EvatorLeafReflectFunction}, BELOW the interpolation markers. The other 13 fields
 *       (incl. the aquifer noises #1–#4) are left CANONICAL, matching the design's "mirror terrain SHAPE,
 *       accept unmirrored aquifer/ore/carver" scope.</li>
 * </ul>
 * No {@code LatitudeV2Flags} entry; delete with the rest of the spike.
 */
public final class EvatorSpike {

    private EvatorSpike() {
    }

    /**
     * SPIKE leaf whitelist — the coordinate-(X)-bearing {@link DensityFunction} leaf types. Matched on the
     * class's simple name (after the {@code $}) so we need not import package-private inner classes. The
     * inventory dump ({@link #dumpLeafInventory}) logs every class actually seen so coverage can be audited
     * against the REAL router graph rather than trusted from this list.
     */
    static final Set<String> REFLECT_LEAF_NAMES = Set.of(
            "Noise",                     // DensityFunctions$Noise
            "ShiftedNoise",              // DensityFunctions$ShiftedNoise (reads ctx directly + shift children)
            "ShiftA", "ShiftB", "Shift", // DensityFunctions$ShiftA/$ShiftB/$Shift (ShiftNoise family)
            "ShiftNoise",                // abstract base, defensive
            "WeirdScaledSampler",        // absent in 26.2, defensive
            "BlendedNoise",              // absent in 26.2, defensive
            "EndIslandDensityFunction"   // end-only, defensive
    );

    /** Any non-"false" value arms the spike. */
    public static boolean armed() {
        return !"false".equalsIgnoreCase(System.getProperty("latitude.evatorSpike", "false").trim());
    }

    /**
     * "leaf" | "leafall" | "wrap"; legacy "true" maps to "wrap" (mechanism a). Default (unarmed) → "wrap".
     * "leafall" is a DIAGNOSTIC variant that reflects ALL 15 router fields (incl. the aquifer noises #1–#4),
     * isolating "terrain-shape mirror" residuals caused by the deliberately-unmirrored aquifer from any deeper
     * fill-path asymmetry.
     */
    public static String mode() {
        String v = System.getProperty("latitude.evatorSpike", "false").trim().toLowerCase();
        if (v.equals("leaf") || v.equals("leafall")) {
            return v;
        }
        return "wrap";
    }

    /**
     * Wrap {@code randomState}'s router with the mirror reflection per {@link #mode()}. Returns the wrapped
     * {@code finalDensity} for direct-compute probing, or the original if not armed / on error.
     */
    public static DensityFunction installMirror(RandomState randomState) {
        String m = mode();
        if (m.equals("leaf")) {
            return installMirrorLeaf(randomState, false);
        }
        if (m.equals("leafall")) {
            return installMirrorLeaf(randomState, true);
        }
        return installMirrorWrap(randomState);
    }

    /** Mechanism (a): root-wrap finalDensity (#12) with {@link EvatorMirrorSpikeFunction}. */
    private static DensityFunction installMirrorWrap(RandomState randomState) {
        NoiseRouter original = randomState.router();
        DensityFunction originalFinalDensity = original.finalDensity();
        NoiseRouter rebuilt = rebuild(original,
                original.preliminarySurfaceLevel(),
                new EvatorMirrorSpikeFunction(originalFinalDensity));
        ((RandomStateAccessor) (Object) randomState).globe$setRouter(rebuilt);
        return rebuilt.finalDensity();
    }

    /**
     * Mechanism (b): rewrite preliminarySurfaceLevel (#11) and finalDensity (#12) via {@link #reflectLeaves}
     * (per-leaf reflectors below the interpolation markers). Other fields left canonical.
     */
    private static DensityFunction installMirrorLeaf(RandomState randomState, boolean allFields) {
        NoiseRouter original = randomState.router();
        NoiseRouter rebuilt;
        if (allFields) {
            rebuilt = new NoiseRouter(
                    reflectLeaves(original.barrierNoise()),               // 1
                    reflectLeaves(original.fluidLevelFloodednessNoise()), // 2
                    reflectLeaves(original.fluidLevelSpreadNoise()),      // 3
                    reflectLeaves(original.lavaNoise()),                  // 4
                    reflectLeaves(original.temperature()),                // 5
                    reflectLeaves(original.vegetation()),                 // 6
                    reflectLeaves(original.continents()),                 // 7
                    reflectLeaves(original.erosion()),                    // 8
                    reflectLeaves(original.depth()),                      // 9
                    reflectLeaves(original.ridges()),                     // 10
                    reflectLeaves(original.preliminarySurfaceLevel()),    // 11
                    reflectLeaves(original.finalDensity()),               // 12
                    reflectLeaves(original.veinToggle()),                 // 13
                    reflectLeaves(original.veinRidged()),                 // 14
                    reflectLeaves(original.veinGap()));                   // 15
        } else {
            rebuilt = rebuild(original,
                    reflectLeaves(original.preliminarySurfaceLevel()),
                    reflectLeaves(original.finalDensity()));
        }
        ((RandomStateAccessor) (Object) randomState).globe$setRouter(rebuilt);
        return rebuilt.finalDensity();
    }

    /** Rebuild the 15-field router substituting only #11 (prelim) and #12 (finalDensity). */
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

    /**
     * Walk {@code graph} bottom-up via {@code mapAll} and wrap every coordinate-bearing leaf
     * ({@link #REFLECT_LEAF_NAMES}) in {@link EvatorLeafReflectFunction}. All other nodes (constants, Y-only
     * gradients, markers, arithmetic/transformer/spline nodes) are returned unchanged so their leaf children
     * — visited earlier in the same bottom-up pass — carry the reflection instead.
     */
    static DensityFunction reflectLeaves(DensityFunction graph) {
        return graph.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction fn) {
                return isReflectLeaf(fn) ? new EvatorLeafReflectFunction(fn) : fn;
            }
        });
    }

    private static boolean isReflectLeaf(DensityFunction fn) {
        if (fn instanceof EvatorLeafReflectFunction) {
            return false; // already wrapped (shouldn't recur, but be safe)
        }
        return REFLECT_LEAF_NAMES.contains(simpleName(fn.getClass()));
    }

    private static String simpleName(Class<?> c) {
        String n = c.getName();
        int dollar = n.lastIndexOf('$');
        int dot = n.lastIndexOf('.');
        return n.substring(Math.max(dollar, dot) + 1);
    }

    /**
     * Diagnostic: walk {@code graph} and tally every node class encountered, marking which the reflect
     * whitelist wraps. Returns a human-readable inventory (one line per class). Uses a NON-mutating visitor
     * (returns each node unchanged) so it only observes.
     */
    public static String dumpLeafInventory(DensityFunction graph, String label) {
        Map<String, int[]> counts = new LinkedHashMap<>(); // name -> {count, wrapped(0/1)}
        AtomicInteger total = new AtomicInteger();
        graph.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction fn) {
                total.incrementAndGet();
                String name = simpleName(fn.getClass());
                int wrapped = REFLECT_LEAF_NAMES.contains(name) ? 1 : 0;
                counts.computeIfAbsent(name, k -> new int[]{0, wrapped})[0]++;
                return fn;
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("[evatorSpike] LEAF INVENTORY ").append(label).append(" (nodes=").append(total.get()).append("):\n");
        counts.forEach((name, cw) ->
                sb.append(String.format("  %-28s count=%-4d %s%n", name, cw[0], cw[1] == 1 ? "REFLECTED" : "skip")));
        return sb.toString();
    }
}
