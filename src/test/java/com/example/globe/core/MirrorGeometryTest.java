package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link MirrorGeometry} -- the B-6 mirror-band geometry, sibling of {@link EdgeGeometry}.
 * Pins: the degree/block reuse of EdgeGeometry; the 5-deg band width on Itty / Small / Regular (Classic + Wide)
 * with the small-world floor; the strict ordering {@code trigger < preWarm < frontier} on every size; the
 * reflection involution and its identity with {@link HemispherePassage#mirrorX}; SYMMETRIC band membership
 * (both edges); and degenerate/NaN safety.
 */
class MirrorGeometryTest {

    private static final double EPS = 1e-6;

    private static final MirrorGeometry.Resolved ITTY = MirrorGeometry.resolve(3750.0);   // Itty Classic
    private static final MirrorGeometry.Resolved SMALL_W = MirrorGeometry.resolve(15000.0); // Small Wide (z7500)
    private static final MirrorGeometry.Resolved REG_W = MirrorGeometry.resolve(20000.0);   // Regular Wide (z10000)

    // ---- degree/block reuse of EdgeGeometry (single source of truth) -----------------------

    @Test
    void degreeBlockHelpersDelegateToEdgeGeometry() {
        assertEquals(EdgeGeometry.blocksPerDegree(20000.0), MirrorGeometry.blocksPerDegree(20000.0), EPS);
        assertEquals(EdgeGeometry.distForDeg(175.0, 7500.0), MirrorGeometry.distForDeg(175.0, 7500.0), EPS);
        assertEquals(EdgeGeometry.distanceToEdge(3750.0, 0.0, 3700.0),
                MirrorGeometry.distanceToEdge(3750.0, 0.0, 3700.0), EPS);
    }

    // ---- band width table (design "mirror geometry" section) -------------------------------

    @Test
    void bandWidthMatchesTheDesignTable() {
        assertEquals(104.166667, MirrorGeometry.bandWidthBlocks(3750.0), 1e-4, "Itty Classic ~104 blk");
        assertEquals(138.888889, MirrorGeometry.bandWidthBlocks(5000.0), 1e-4, "Small Classic ~139 blk");
        assertEquals(208.333333, MirrorGeometry.bandWidthBlocks(7500.0), 1e-4, "Regular Classic ~208 blk");
        assertEquals(416.666667, MirrorGeometry.bandWidthBlocks(15000.0), 1e-4, "Small Wide ~417 blk");
        assertEquals(555.555556, MirrorGeometry.bandWidthBlocks(20000.0), 1e-4, "Regular Wide ~555 blk");
    }

    @Test
    void bandWidthIsTheFiveDegreeAnchorWhereTheFloorDoesNotBind() {
        // On every world but the tiniest, band width is exactly 5 deg (175->180).
        for (double xr : new double[] {5000.0, 7500.0, 15000.0, 20000.0}) {
            assertEquals(5.0 * (xr / 180.0), MirrorGeometry.bandWidthBlocks(xr), EPS, "5-deg band @ " + xr);
        }
    }

    // ---- the small-world floor: only the trigger line floors on Itty ------------------------

    @Test
    void ittyBittyFloor_onlyTheTriggerFloors() {
        // 1 deg = 20.833 blk: trigger's pure 0.5 deg (10.4) is below the 24-blk floor; preWarm/frontier hold.
        assertEquals(MirrorGeometry.TRIGGER_MIN_DIST_BLOCKS, ITTY.triggerDist(), EPS, "trigger floored to 24");
        assertEquals(3.0 * (3750.0 / 180.0), ITTY.preWarmDist(), EPS, "preWarm holds at 177 deg (62.5)");
        assertEquals(5.0 * (3750.0 / 180.0), ITTY.frontierDist(), EPS, "frontier holds at 175 deg (104.2)");
    }

    @Test
    void largeWorldLines_pureDegreesHold() {
        double bpd = 20000.0 / 180.0;
        assertEquals(0.5 * bpd, REG_W.triggerDist(), EPS, "179.5 deg");
        assertEquals(3.0 * bpd, REG_W.preWarmDist(), EPS, "177 deg");
        assertEquals(5.0 * bpd, REG_W.frontierDist(), EPS, "175 deg");
        double bpdS = 15000.0 / 180.0;
        assertEquals(0.5 * bpdS, SMALL_W.triggerDist(), EPS);
        assertEquals(3.0 * bpdS, SMALL_W.preWarmDist(), EPS);
        assertEquals(5.0 * bpdS, SMALL_W.frontierDist(), EPS);
    }

    // ---- strict ordering + floor discipline on every world size ----------------------------

    @Test
    void orderingInvariantHoldsOnEveryWorldSize() {
        for (double xr : new double[] {2000.0, 3750.0, 5000.0, 7500.0, 10000.0, 15000.0, 20000.0, 40000.0}) {
            MirrorGeometry.Resolved g = MirrorGeometry.resolve(xr);
            assertTrue(g.triggerDist() < g.preWarmDist(), "trigger < preWarm @ " + xr);
            assertTrue(g.preWarmDist() < g.frontierDist(), "preWarm < frontier @ " + xr);
            assertTrue(g.triggerDist() >= MirrorGeometry.TRIGGER_MIN_DIST_BLOCKS - EPS, "trigger floored @ " + xr);
            assertTrue(g.preWarmDist() - g.triggerDist() >= MirrorGeometry.PRE_WARM_MIN_GAP_BLOCKS - EPS,
                    "preWarm-trigger gap @ " + xr);
            // The trigger and pre-warm lines always sit INSIDE the band (nearer the edge than the frontier).
            assertTrue(g.triggerDist() < g.bandWidthBlocks() && g.preWarmDist() < g.bandWidthBlocks(),
                    "lines live inside the band @ " + xr);
        }
    }

    // ---- frontier anchoring --------------------------------------------------------------

    @Test
    void frontierXIsRadiusMinusBandWidth_symmetric() {
        assertEquals(3750.0 - MirrorGeometry.bandWidthBlocks(3750.0), MirrorGeometry.frontierX(3750.0), EPS);
        assertEquals(ITTY.frontierAbsX(), MirrorGeometry.frontierX(3750.0), EPS);
        assertTrue(MirrorGeometry.frontierX(3750.0) > 0 && MirrorGeometry.frontierX(3750.0) < 3750.0);
    }

    // ---- reflection: involution + identity with mirrorX ------------------------------------

    @Test
    void reflectIsMinusXAboutCenterAndAnInvolution() {
        double centerX = 0.0;
        for (double x : new double[] {-3700.0, -100.0, 0.0, 250.0, 3700.0}) {
            assertEquals(-x, MirrorGeometry.reflect(x, centerX), EPS, "reflect about 0 is -x");
            assertEquals(x, MirrorGeometry.reflect(MirrorGeometry.reflect(x, centerX), centerX), EPS,
                    "reflect is an involution");
            // Identical to the shipped mirror engine's mirrorX.
            assertEquals(HemispherePassage.mirrorX(x, centerX), MirrorGeometry.reflect(x, centerX), EPS,
                    "reflect == HemispherePassage.mirrorX");
        }
        // Non-zero center still reflects about that center.
        assertEquals(2.0 * 40.0 - 100.0, MirrorGeometry.reflect(100.0, 40.0), EPS);
    }

    // ---- SYMMETRIC band membership (both edges) --------------------------------------------

    @Test
    void inBandIsSymmetricAcrossBothEdges() {
        double xr = 3750.0, cx = 0.0;
        double w = MirrorGeometry.bandWidthBlocks(xr); // ~104.17
        // Deep interior: out of band, both signs.
        assertFalse(MirrorGeometry.inBand(0.0, cx, xr));
        assertFalse(MirrorGeometry.inBand(3000.0, cx, xr));
        assertFalse(MirrorGeometry.inBand(-3000.0, cx, xr));
        // Just inside each band (distToEdge = w-1): in band, both signs symmetric.
        double justInside = xr - (w - 1.0); // |x| so distToEdge = w-1
        assertTrue(MirrorGeometry.inBand(justInside, cx, xr), "east band");
        assertTrue(MirrorGeometry.inBand(-justInside, cx, xr), "west band (symmetric)");
        assertEquals(MirrorGeometry.inBand(justInside, cx, xr), MirrorGeometry.inBand(-justInside, cx, xr));
        // Just equatorward of the frontier (distToEdge = w+1) is out, both signs.
        double justOutside = xr - (w + 1.0);
        assertFalse(MirrorGeometry.inBand(justOutside, cx, xr), "east interior");
        assertFalse(MirrorGeometry.inBand(-justOutside, cx, xr), "west interior (symmetric)");
        // At/beyond the wall counts as in-band.
        assertTrue(MirrorGeometry.inBand(xr, cx, xr));
        assertTrue(MirrorGeometry.inBand(xr + 50.0, cx, xr));
    }

    @Test
    void inBandRespectsANonZeroCenter() {
        double xr = 7500.0, cx = 1000.0;
        double w = MirrorGeometry.bandWidthBlocks(xr);
        // East edge sits at cx + xr; a column just inside it is in-band.
        assertTrue(MirrorGeometry.inBand(cx + xr - 1.0, cx, xr));
        // West edge at cx - xr.
        assertTrue(MirrorGeometry.inBand(cx - xr + 1.0, cx, xr));
        // Around the shifted center is deep interior.
        assertFalse(MirrorGeometry.inBand(cx, cx, xr));
        assertTrue(w > 0);
    }

    // ---- degenerate / NaN safety -----------------------------------------------------------

    @Test
    void degenerateGeometryFailsClosed() {
        assertFalse(MirrorGeometry.inBand(100.0, 0.0, 0.0), "zero radius: never in band");
        assertFalse(MirrorGeometry.inBand(100.0, 0.0, -5.0), "negative radius: never in band");
        assertFalse(MirrorGeometry.inBand(Double.NaN, 0.0, 3750.0), "NaN x: not in band");
        assertFalse(MirrorGeometry.inBand(100.0, 0.0, Double.NaN), "NaN radius: not in band");
        assertTrue(Double.isNaN(MirrorGeometry.reflect(Double.NaN, 0.0)), "reflect propagates NaN");
    }
}
