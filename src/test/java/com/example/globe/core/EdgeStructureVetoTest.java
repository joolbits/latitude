package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EdgeStructureVeto} -- the E/W-edge structure-free band math (B-5 item 1, widened
 * TEST 89 to a DEGREE-anchored band with a fan-out buffer). Pins the 173-deg anchor + 600-block floor, the
 * per-world effective widths (Itty / Small-Wide / Regular-Wide), that the band always covers the visible storm
 * band ({@code rampStartDist}) plus the ~300-block max village fan-out, both edges, the interior being
 * untouched on the smallest world, and the fail-open on degenerate geometry.
 */
class EdgeStructureVetoTest {

    private static final double CENTER = 0.0;

    // Representative world sizes (intended X radius).
    private static final double ITTY = 3750.0;   // Itty-Bitty Classic: 1 deg = 20.83 blocks
    private static final double SMALL = 15000.0;  // Small-Wide: 1 deg = 83.33 blocks
    private static final double REG = 20000.0;    // Regular-Wide: 1 deg = 111.11 blocks

    // A village fans roughly 150-300 blocks from its anchor toward the band; the buffer must clear the max.
    private static final double MAX_VILLAGE_FANOUT = 300.0;

    @Test
    void bandIsDegreeAnchoredAt173WithA600Floor() {
        // The anchor is 173 deg (visible fog onset 176.5 deg PLUS a 3.5-deg fan-out buffer), floored at 600.
        assertTrue(EdgeStructureVeto.VETO_DEG == 173.0, "veto band anchored at 173 deg (176.5 - 3.5 buffer)");
        assertTrue(EdgeStructureVeto.MIN_BAND_BLOCKS == 600.0, "600-block floor keeps the buffer honest on tiny worlds");
    }

    @Test
    void effectiveBandWidthsPerWorldSize() {
        // Itty: distForDeg(173) = 7 deg * 20.833 = 145.8 -> floored to 600.
        assertEquals(600.0, EdgeStructureVeto.bandBlocks(ITTY), 1e-3, "Itty floored to 600");
        // Small-Wide: 7 deg * 83.333 = 583.3 -> floored to 600.
        assertEquals(600.0, EdgeStructureVeto.bandBlocks(SMALL), 1e-3, "Small-Wide floored to 600 (583.3 < 600)");
        // Regular-Wide: 7 deg * 111.111 = 777.8 -> degree-derived wins.
        assertEquals(7.0 * (REG / 180.0), EdgeStructureVeto.bandBlocks(REG), 1e-3, "Regular-Wide degree-derived 777.8");
    }

    @Test
    void bandAlwaysCoversVisibleStormPlusMaxFanoutOnEveryWorld() {
        // The invariant that makes this correct: a structure anchored just OUTSIDE the band (dist > bandWidth)
        // can fan at most MAX_VILLAGE_FANOUT toward the edge; for none of its blocks to enter the visible storm
        // band we need bandWidth >= rampStartDist + MAX_VILLAGE_FANOUT.
        for (double xr : new double[] {ITTY, SMALL, REG, 5000.0, 7500.0, 10000.0, 40000.0}) {
            double band = EdgeStructureVeto.bandBlocks(xr);
            double rampStart = EdgeGeometry.resolve(xr).rampStartDist();
            assertTrue(band >= rampStart + MAX_VILLAGE_FANOUT - 1e-6,
                    "band (" + band + ") must cover visible storm (" + rampStart + ") + fan-out @ xr " + xr);
        }
    }

    @Test
    void insideBandNearEastEdgeIsVetoed() {
        double band = EdgeStructureVeto.bandBlocks(ITTY); // 600
        // dist-to-edge = 3750 - 3200 = 550 <= 600.
        assertTrue(EdgeStructureVeto.inEdgeBand(3200.0, CENTER, ITTY, band));
    }

    @Test
    void insideBandNearWestEdgeIsVetoed() {
        double band = EdgeStructureVeto.bandBlocks(ITTY);
        assertTrue(EdgeStructureVeto.inEdgeBand(-3200.0, CENTER, ITTY, band));
    }

    @Test
    void exactlyAtBandBoundaryIsVetoed() {
        double band = EdgeStructureVeto.bandBlocks(ITTY); // 600
        // dist-to-edge = 3750 - 3150 = 600 == band -> inclusive.
        assertTrue(EdgeStructureVeto.inEdgeBand(3150.0, CENTER, ITTY, band));
        assertTrue(EdgeStructureVeto.inEdgeBand(-3150.0, CENTER, ITTY, band));
    }

    @Test
    void justInsideBandBoundaryIsNotVetoed() {
        double band = EdgeStructureVeto.bandBlocks(ITTY); // 600
        // dist-to-edge = 3750 - 3149 = 601 > 600 -> interior, allowed.
        assertFalse(EdgeStructureVeto.inEdgeBand(3149.0, CENTER, ITTY, band));
        assertFalse(EdgeStructureVeto.inEdgeBand(-3149.0, CENTER, ITTY, band));
    }

    @Test
    void deepInteriorIsNeverVetoed() {
        double band = EdgeStructureVeto.bandBlocks(ITTY);
        assertFalse(EdgeStructureVeto.inEdgeBand(0.0, CENTER, ITTY, band));
        assertFalse(EdgeStructureVeto.inEdgeBand(1000.0, CENTER, ITTY, band));
        assertFalse(EdgeStructureVeto.inEdgeBand(-2000.0, CENTER, ITTY, band));
    }

    @Test
    void atOrBeyondTheEdgeIsVetoed() {
        double band = EdgeStructureVeto.bandBlocks(ITTY);
        assertTrue(EdgeStructureVeto.inEdgeBand(3750.0, CENTER, ITTY, band));
        assertTrue(EdgeStructureVeto.inEdgeBand(3800.0, CENTER, ITTY, band));
    }

    @Test
    void interiorDominatesTheSmallestWorld() {
        // Even with the wider 600-block band, the vast middle of the smallest world is structure-allowed
        // (interior spans -3150..3150, over 6300 blocks wide).
        double band = EdgeStructureVeto.bandBlocks(ITTY);
        for (double x = -3000.0; x <= 3000.0; x += 250.0) {
            assertFalse(EdgeStructureVeto.inEdgeBand(x, CENTER, ITTY, band),
                    "interior x=" + x + " must be structure-allowed");
        }
    }

    @Test
    void offCenterBorderIsHonoured() {
        double band = EdgeStructureVeto.bandBlocks(ITTY); // 600
        // A border centered at 1000: east edge at 1000 + 3750 = 4750, band starts at 4150.
        assertTrue(EdgeStructureVeto.inEdgeBand(4700.0, 1000.0, ITTY, band));
        assertFalse(EdgeStructureVeto.inEdgeBand(4100.0, 1000.0, ITTY, band));
    }

    @Test
    void degenerateGeometryFailsOpen() {
        double band = EdgeStructureVeto.bandBlocks(ITTY);
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, 0.0, band), "zero radius -> never veto");
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, -1.0, band), "negative radius -> never veto");
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, ITTY, 0.0), "zero band -> never veto");
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, ITTY, -5.0), "negative band -> never veto");
    }
}
