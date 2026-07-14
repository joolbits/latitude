package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleArrivalSearch} (B-7) -- the corner X-clamp (A2) and the ordered/clamped
 * arrival-parallel candidate list that the MC-coupled arrival search consumes.
 */
class PoleArrivalSearchTest {

    private static final double EPS = 1e-6;

    @Test
    void xClampKeepsArrivalsEquatorwardOfTheEwBand() {
        double xRadius = 20000.0; // Regular-Wide
        double rearm = EdgeGeometry.resolve(xRadius).rearmDist();
        double clamp = PoleArrivalSearch.xClampAbs(xRadius);
        assertEquals(xRadius - rearm - PoleArrivalSearch.EW_MARGIN_BLOCKS, clamp, EPS,
                "clamp = xRadius - EW rearmDist - margin");
        // A clamped column sits at least (rearm + margin) blocks in from the EW edge -- OUTSIDE the whole EW
        // ceremony (prompt/fog/re-arm), so a corner crossing can never stack ceremonies.
        double distFromEwEdge = xRadius - clamp;
        assertTrue(distFromEwEdge >= rearm + PoleArrivalSearch.EW_MARGIN_BLOCKS - EPS);
    }

    @Test
    void xClampNeverNegative() {
        // A degenerate tiny radius must not produce a negative clamp (would flip the bounds).
        assertTrue(PoleArrivalSearch.xClampAbs(10.0) >= 0.0);
    }

    @Test
    void clampXBoundsToCenterPlusMinusClamp() {
        assertEquals(20, PoleArrivalSearch.clampX(100, 0.0, 20.0));
        assertEquals(-20, PoleArrivalSearch.clampX(-100, 0.0, 20.0));
        assertEquals(5, PoleArrivalSearch.clampX(5, 0.0, 20.0));
    }

    @Test
    void candidateOrderIsCenterThenAlternatingOutward() {
        int[] xs = PoleArrivalSearch.candidateXs(0, 0.0, 1000.0, 16, 5);
        assertArrayEqualsMsg(new int[]{0, 16, -16, 32, -32}, xs);
    }

    @Test
    void candidatesAreClampedAndDeduped() {
        // clampAbs 20, step 16: 0, +16, -16, +32->20, -32->-20, then everything further collapses onto +/-20
        // (dups dropped). Distinct = {0,16,-16,20,-20}.
        int[] xs = PoleArrivalSearch.candidateXs(0, 0.0, 20.0, 16, 10);
        assertArrayEqualsMsg(new int[]{0, 16, -16, 20, -20}, xs);
        // Every candidate obeys the A2 invariant |x - centerX| <= clampAbs.
        for (int x : xs) {
            assertTrue(Math.abs(x) <= 20, "candidate " + x + " exceeds the clamp");
        }
    }

    @Test
    void candidateCountIsCappedByMaxCount() {
        int[] xs = PoleArrivalSearch.candidateXs(0, 0.0, 100000.0, 16, 7);
        assertEquals(7, xs.length);
        assertEquals(0, xs[0]);
    }

    private static void assertArrayEqualsMsg(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length, "length");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "index " + i);
        }
    }
}
