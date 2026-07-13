package com.example.globe.terrain;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.MirrorGeometry;
import com.example.globe.world.LatitudeBiomes;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) P1 -- the PRODUCTION band predicate + reflection map, shared by the
 * terrain leaf reflector ({@link EvatorReflectLeafFunction}) and the biome-path remap
 * ({@code LatitudeBiomeSource.getNoiseBiome}). This is the single place the "is this column in the EAST mirror
 * band, and is the evator actually live for THIS world?" question is answered, so the two axes (biome + terrain)
 * can never drift.
 *
 * <h2>Gating (never the raw global flag alone)</h2>
 * {@link #active()} is {@code EVATOR_V2_ENABLED (global flag) && LatitudeBiomes.isEvatorActive() (per-world
 * capture)}. The per-world capture is re-read on EVERY sample rather than snapshotted at router-install time,
 * for the exact ordering reason Phase 4's {@code GeoTerrainBiasFunction} re-checks its provider per call: the
 * router is wrapped in the {@code ChunkMap} constructor, which can run BEFORE {@code LatitudeWorldState} pushes
 * the captured value into the live cache. Installing structurally (flag-gated) and no-op-ing per call until the
 * capture arrives means (a) a captured-OFF world stays byte-identical (the predicate returns {@code false}
 * forever, so every leaf passes through un-reflected), and (b) an evator world starts mirroring the instant the
 * capture is pushed -- long before any chunk is generated for a player.
 *
 * <h2>West is canonical, EAST is the mirror (design "copy direction")</h2>
 * Only columns on the {@code +x} (EAST) side of the band reflect; the {@code -x} (WEST) band is generated
 * canonically. The reflection {@code x -> -x} (about the origin-centered world border) lands an east column on
 * its canonical west twin, so the east band becomes the exact reflection of the west band with zero per-noise
 * edits (the remap rides through every X-keyed leaf downstream). See {@link MirrorGeometry}.
 *
 * <h2>Cheap by construction</h2>
 * The steady-state per-sample cost is: two volatile reads (the flag is a {@code static final} constant folded
 * at class-init; the capture + radius are volatile fields), one {@code int} subtract, and one compare against a
 * memoized threshold. {@link MirrorGeometry#resolve} (which allocates a {@code Resolved} record) is called at
 * most once per distinct {@code xRadius} and cached in {@link #cachedFrontierAbsX}, so there is no per-sample
 * allocation on the hot density-fill path.
 *
 * <h2>Origin-centered</h2>
 * The globe world border is centered at {@code (0,0)} and all of Latitude's latitude/longitude math is
 * origin-centered (latitude is {@code |Z|/zRadius}, longitude keyed off {@code |X|}), so this class uses
 * {@code centerX == 0}: {@code reflect(x) == -x}. This matches the biome pick path (which consumes raw block-X
 * with an implicit center of 0) and {@link MirrorGeometry#reflect}(x, 0).
 */
public final class EvatorMirror {

    private EvatorMirror() {
    }

    /** Memoized {@code xRadius -> east-band inner-frontier |x| threshold}. A column at {@code |x| >= this} (and
     *  on the east side) is inside the mirror band. Volatile check-then-set race is benign (worst case a
     *  redundant recompute of the same value). */
    private static volatile int cachedXRadius = -1;
    private static volatile double cachedFrontierAbsX = Double.POSITIVE_INFINITY;

    /**
     * The evator is live: the global flag is on AND this loaded world captured the evator at birth AND the
     * terrain mirror is ACTUALLY INSTALLED ({@link EvatorTerrainReflection#isTerrainMirrorInstalled()} -- the
     * P1 sweep HIGH fix). The third clause keeps the biome remap and the terrain reflection engaged TOGETHER:
     * if the install refused (unknown leaf on a future MC/Terralith update) or errored, the terrain is
     * canonical, and without this clause a captured-ON world would get MIRRORED biomes painted over
     * UNMIRRORED terrain -- worse than either extreme, and a violation of the guard's "unmirrored-but-sane"
     * promise. Folding the signal in HERE means every consumer -- the biome remap, the leaf reflector's own
     * per-sample predicate (for which it is trivially true, since the reflector only executes inside an
     * installed router), and the P2 crossing trigger (design-doc CONTRACT: consult this same signal; degrade
     * to the B-5 prompt path when the mirror is not installed) -- inherits the correct gate automatically.
     */
    public static boolean active() {
        return LatitudeV2Flags.EVATOR_V2_ENABLED
                && LatitudeBiomes.isEvatorActive()
                && EvatorTerrainReflection.isTerrainMirrorInstalled();
    }

    private static double frontierAbsX(int xRadius) {
        if (xRadius == cachedXRadius) {
            return cachedFrontierAbsX;
        }
        double f = MirrorGeometry.frontierX(xRadius); // one MirrorGeometry.resolve() per distinct radius
        cachedXRadius = xRadius;
        cachedFrontierAbsX = f;
        return f;
    }

    /**
     * True iff the block-X column lies in the EAST mirror band of a live-evator world -- i.e. its terrain and
     * biome are to be generated at the reflected (canonical WEST) coordinate. Returns {@code false} for the
     * west band, the whole interior, a degenerate radius, and any non-evator world (so those stay canonical /
     * byte-identical).
     */
    public static boolean reflectEastBlock(int blockX) {
        if (!active()) {
            return false;
        }
        int xRadius = LatitudeBiomes.getActiveXRadiusBlocks();
        if (xRadius <= 0) {
            return false;
        }
        // centerX == 0: east side is blockX > 0, in-band is blockX >= frontierAbsX (>= 0).
        return blockX >= frontierAbsX(xRadius);
    }

    /** The reflection map for block-X (origin-centered): {@code -blockX}. Identical to
     *  {@link MirrorGeometry#reflect}(blockX, 0) and B-5's {@code HemispherePassage.mirrorX} at center 0. */
    public static int reflectBlockX(int blockX) {
        return -blockX;
    }

    // ---- quart-scale helpers for the biome path (quart coord = block >> 2; center 0 -> reflect(q) = -q) ----

    /** True iff the quart-X column is in the EAST mirror band of a live-evator world (the biome-path predicate).
     *  {@code quartX << 2} is the block-X of the quart cell; center 0 makes the block reflection {@code -blockX}
     *  land exactly on quart {@code -quartX}. */
    public static boolean reflectEastQuart(int quartX) {
        return reflectEastBlock(quartX << 2);
    }

    /** The reflection map for quart-X (origin-centered): {@code -quartX}. */
    public static int reflectQuartX(int quartX) {
        return -quartX;
    }
}
