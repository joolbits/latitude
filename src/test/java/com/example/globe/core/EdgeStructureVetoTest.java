package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EdgeStructureVeto} -- the E/W-edge structure-free band math (B-5 item 1). Pins the
 * band boundary, both edges, the fixed absolute 500-block width, the interior being untouched on the smallest
 * world, and the fail-open on degenerate geometry.
 */
class EdgeStructureVetoTest {

    // Itty-Bitty Classic: the smallest world, where the band must still leave a vast interior.
    private static final double SMALL_RADIUS = 3750.0;
    private static final double CENTER = 0.0;
    private static final double BAND = EdgeStructureVeto.EDGE_BAND_BLOCKS; // 500

    @Test
    void bandWidthIsAFixedAbsolute500() {
        // Placement-determinism concern: a FIXED absolute width, deliberately NOT tied to the degree-anchored
        // visual fog ramp (which is now per-world) -- it just stays wider than every feature line so the whole
        // edge experience sits on a clean, empty frontier regardless of world size.
        assertTrue(EdgeStructureVeto.EDGE_BAND_BLOCKS == 500.0,
                "edge veto band is a fixed absolute 500 blocks");
    }

    @Test
    void insideBandNearEastEdgeIsVetoed() {
        // dist-to-edge = 3750 - 3700 = 50 <= 500.
        assertTrue(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, SMALL_RADIUS, BAND));
    }

    @Test
    void insideBandNearWestEdgeIsVetoed() {
        // Symmetric on the west (negative-X) side: dist = 3750 - 3700 = 50.
        assertTrue(EdgeStructureVeto.inEdgeBand(-3700.0, CENTER, SMALL_RADIUS, BAND));
    }

    @Test
    void exactlyAtBandBoundaryIsVetoed() {
        // dist-to-edge = 3750 - 3250 = 500 == BAND -> inclusive.
        assertTrue(EdgeStructureVeto.inEdgeBand(3250.0, CENTER, SMALL_RADIUS, BAND));
        assertTrue(EdgeStructureVeto.inEdgeBand(-3250.0, CENTER, SMALL_RADIUS, BAND));
    }

    @Test
    void justInsideBandBoundaryIsNotVetoed() {
        // dist-to-edge = 3750 - 3249 = 501 > 500 -> interior, allowed.
        assertFalse(EdgeStructureVeto.inEdgeBand(3249.0, CENTER, SMALL_RADIUS, BAND));
        assertFalse(EdgeStructureVeto.inEdgeBand(-3249.0, CENTER, SMALL_RADIUS, BAND));
    }

    @Test
    void deepInteriorIsNeverVetoed() {
        assertFalse(EdgeStructureVeto.inEdgeBand(0.0, CENTER, SMALL_RADIUS, BAND));
        assertFalse(EdgeStructureVeto.inEdgeBand(1000.0, CENTER, SMALL_RADIUS, BAND));
        assertFalse(EdgeStructureVeto.inEdgeBand(-2000.0, CENTER, SMALL_RADIUS, BAND));
    }

    @Test
    void atOrBeyondTheEdgeIsVetoed() {
        // At the edge (dist 0) and just outside (negative dist) both fall inside the band.
        assertTrue(EdgeStructureVeto.inEdgeBand(3750.0, CENTER, SMALL_RADIUS, BAND));
        assertTrue(EdgeStructureVeto.inEdgeBand(3800.0, CENTER, SMALL_RADIUS, BAND));
    }

    @Test
    void interiorDominatesTheSmallestWorld() {
        // The vast middle of even the smallest world is structure-allowed: only the outer 500 per side vetoes.
        // Sample the interior at a coarse step and assert none of it is in the band.
        for (double x = -3000.0; x <= 3000.0; x += 250.0) {
            assertFalse(EdgeStructureVeto.inEdgeBand(x, CENTER, SMALL_RADIUS, BAND),
                    "interior x=" + x + " must be structure-allowed");
        }
    }

    @Test
    void offCenterBorderIsHonoured() {
        // A border centered at 1000: east edge at 1000 + 3750 = 4750, band starts at 4250.
        assertTrue(EdgeStructureVeto.inEdgeBand(4700.0, 1000.0, SMALL_RADIUS, BAND));
        assertFalse(EdgeStructureVeto.inEdgeBand(4200.0, 1000.0, SMALL_RADIUS, BAND));
    }

    @Test
    void degenerateGeometryFailsOpen() {
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, 0.0, BAND), "zero radius -> never veto");
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, -1.0, BAND), "negative radius -> never veto");
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, SMALL_RADIUS, 0.0), "zero band -> never veto");
        assertFalse(EdgeStructureVeto.inEdgeBand(3700.0, CENTER, SMALL_RADIUS, -5.0), "negative band -> never veto");
    }
}
