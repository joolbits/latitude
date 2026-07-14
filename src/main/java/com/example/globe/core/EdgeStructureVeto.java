package com.example.globe.core;

/**
 * Phase 5 Slice B-5 (Hemisphere Passage polish, item 1) -- pure band math for the E/W-EDGE structure-free
 * band. Peetsa saw a generated structure standing at/near the world's E/W border (TEST 83) and asked the
 * edge to be structure-free: "the storm belt is wild, empty land." This class answers ONE question with zero
 * Minecraft imports (Core Logic layer, unit-testable in a plain JVM): is a given block-X column within the
 * veto band inward from the nearest E/W (X) world-border edge?
 *
 * <p><b>The distance axis.</b> {@code distToEdge = xRadius - |x - centerX|} -- distance from a column to the
 * nearest E/W (X) world-border edge, the same shape the passage prompt arms on. {@code xRadius} is the square
 * world-border half (the Mercator latitude override only affects Z, never this X radius).
 *
 * <p><b>Band width is DEGREE-ANCHORED with a fan-out buffer (TEST 89 owner decision).</b> It USED to be a
 * fixed absolute 500 blocks, decoupled from the ramp -- but on a big world 500 blocks is NARROWER than the
 * visible storm band (which is degree-anchored: fog onset at {@link EdgeGeometry#RAMP_START_DEG} 177.5 deg),
 * so a structure could stand INSIDE the visible storm (Peetsa saw a village there). It also had no allowance
 * for a multi-chunk village whose houses fan 150-300 blocks toward the band from an anchor just outside it.
 * The band is now the same DEGREE geometry the visible edge uses: it vetoes structures poleward of
 * {@link #VETO_DEG} 173 deg -- the visible band (177.5) PLUS a {@code 177.5 - 173 = 4.5}-deg fan-out buffer
 * ("a few degrees" per Peetsa), so every structure that could fan a house into the visible storm is vetoed at
 * its anchor. (TEST 92 tightened the fog onset 176.5 -&gt; 177.5 while VETO_DEG held at 173, so the buffer grew
 * from 3.5 to 4.5 deg -- even more conservative; the veto still comfortably covers the now-smaller visible band
 * plus fan-out.) On a wide world 4.5 deg is ~375-500 blocks of buffer, comfortably clearing the ~300-block max
 * village fan-out; a {@link #MIN_BAND_BLOCKS} 600-block floor keeps the buffer honest on small worlds where a
 * degree is only ~20-80 blocks. Effective widths (per E/W side; rampStartDist values are post-TEST-92):
 * <ul>
 *   <li>Itty-Bitty Classic (xRadius 3750, 1 deg ~= 20.8 blk): {@code distForDeg(173) = 145.8} floored to
 *       <b>600</b>. Visible band there is {@code rampStartDist = 100}, so the buffer is {@code 600 - 112 =
 *       488} blocks &gt; the 300-block max fan-out. ~16% of the radius per side; the vast interior is free.</li>
 *   <li>Small-Wide (xRadius 15000, 1 deg ~= 83.3 blk): {@code distForDeg(173) = 583.3} floored to <b>600</b>.
 *       Visible band {@code rampStartDist = 208.3}; buffer {@code 600 - 208.3 = 391.7} &gt; 300.</li>
 *   <li>Regular-Wide (xRadius 20000, 1 deg ~= 111.1 blk): {@code distForDeg(173) = 777.8} (degree-derived
 *       wins). Visible band {@code rampStartDist = 277.8}; buffer {@code 777.8 - 277.8 = 500} &gt; 300.</li>
 * </ul>
 * The width is still a pure function of the intended X radius, so the vetoed-anchor set stays trivially
 * deterministic (same seed + flag -&gt; same vetoed anchors).
 *
 * <p><b>Edge-flow rework note (2026-07-13): the anchor is still the FOG onset, not the advisory.</b> That pass
 * moved the white advisory BANNER out to 176 deg (the outermost edge element). That is a TEXT overlay, not
 * terrain -- it never makes a structure look like it is standing in the storm. The thing the veto exists to
 * keep clear is the terrain-visible storm, which is still the FOG (onset {@link EdgeGeometry#RAMP_START_DEG}
 * 177.5 deg), so the {@link #VETO_DEG} 173-deg anchor and its 4.5-deg fan-out buffer are UNCHANGED and still
 * correct. As a bonus, 173 deg is even further out than the 176-deg advisory line, so the veto also clears the
 * advisory zone with margin.
 *
 * <p>The mixin ({@code EdgeStructureVetoMixin}) applies this at the {@code StructureStart.placeInChunk} HEAD
 * (before any block is written -- no half-built structures), keyed on the structure's ANCHOR chunk so every
 * chunk of a multi-chunk structure is vetoed uniformly, and only for SURFACE structures (underground
 * mineshafts are invisible and strongholds carry End access -- both are left alone). Deterministic: the same
 * seed + flag yields the same set of vetoed anchors, because the decision is a pure function of the anchor's
 * X and the border geometry.
 *
 * <p><b>Placement-determinism note (unchanged by this widening).</b> The decision is still anchor-keyed and
 * still a pure function of intended X radius, so existing chunks are untouched and only the placement frontier
 * shifts. Widening the band moves that frontier inward: an already-generated structure that now falls inside
 * the new band is NOT retro-removed (chunks are immutable once written); the veto only governs anchors decided
 * after the upgrade -- the same upgrade-frontier tear the old absolute band already carried.
 */
public final class EdgeStructureVeto {

    /** Poleward-of-this longitude degree the structure-free band begins. The visible storm onset is
     *  {@link EdgeGeometry#RAMP_START_DEG} 177.5 deg (TEST 92); this sits {@code 177.5 - 173 = 4.5} deg further
     *  out (the "few degrees" fan-out buffer Peetsa asked for), so a multi-chunk village anchored just outside
     *  the visible band cannot fan a house into it. Degrees, so it scales with the world instead of the old
     *  fixed 500 blocks that read narrower than the visible band on wide worlds. */
    public static final double VETO_DEG = 173.0;

    /** Block floor on the band width so the fan-out buffer survives on tiny worlds, where {@code distForDeg(173)}
     *  is only ~146 blocks (Itty) -- below the {@code rampStartDist} 160 + 300-block max village fan-out the
     *  buffer must clear. 600 keeps the buffer &gt; the max fan-out on every supported size while still leaving
     *  the vast interior structure-allowed (~16% of the radius per side on the smallest world). */
    public static final double MIN_BAND_BLOCKS = 600.0;

    private EdgeStructureVeto() {
    }

    /**
     * The structure-free band width (blocks) inward from EACH E/W edge for a world of the given intended X
     * radius: {@code max(distForDeg(VETO_DEG, xRadius), MIN_BAND_BLOCKS)} -- the 173-deg fan-out anchor, floored
     * so tiny worlds still clear the max village fan-out. Degenerate radius ({@code <= 0}) yields the floor,
     * but the mixin's {@link #inEdgeBand} still fails open on a bad radius, so nothing is vetoed there.
     */
    public static double bandBlocks(double xRadiusIntended) {
        return Math.max(EdgeGeometry.distForDeg(VETO_DEG, xRadiusIntended), MIN_BAND_BLOCKS);
    }

    /**
     * True iff the column at {@code blockX} lies within {@code bandWidth} blocks of the nearest E/W (X)
     * world-border edge, i.e. {@code xRadius - |blockX - centerX| <= bandWidth}. Degenerate geometry
     * ({@code xRadius <= 0} or {@code bandWidth <= 0}) returns {@code false} (never veto), so a bad border read
     * fails open. A column at or beyond the edge ({@code distToEdge <= 0}) is inside the band too.
     */
    public static boolean inEdgeBand(double blockX, double centerX, double xRadius, double bandWidth) {
        if (!(xRadius > 0.0) || !(bandWidth > 0.0)) {
            return false;
        }
        double distToEdge = xRadius - Math.abs(blockX - centerX);
        return distToEdge <= bandWidth;
    }
}
