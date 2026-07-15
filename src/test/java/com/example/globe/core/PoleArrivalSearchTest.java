package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleArrivalSearch} (B-7) -- the ANTIPODAL-meridian transform (P3 fix 2026-07-14),
 * the corner X-clamp (A2), and the ordered/clamped arrival-parallel candidate list that the MC-coupled
 * arrival search consumes.
 */
class PoleArrivalSearchTest {

    private static final double EPS = 1e-6;

    // ---- P3 fix 2026-07-14: the ANTIPODAL-meridian transform (not mirrorX) -------------------

    @Test
    void antipodalMeridianTransform_ownersRepro() {
        // The owner's TEST 97 repro: crossed at x=+530 on xRadius 7500 (~12.7 degE). mirrorX landed him at
        // -530 (12.7 degW) -- geographically wrong. The antipodal meridian is L+180: x = 530 - 7500 = -6970
        // (~167.3 degW).
        double target = PoleArrivalSearch.antipodalX(530.0, 0.0, 7500.0);
        assertEquals(-6970.0, target, EPS);
        // Longitude property: lon(target) == lon(x) - 180 (i.e. +180 wrapped). lon = dx/xRadius*180.
        double lonBefore = 530.0 / 7500.0 * 180.0;
        double lonAfter = target / 7500.0 * 180.0;
        assertEquals(lonBefore - 180.0, lonAfter, 1e-9, "antipodal = L+180 (wrapped)");
        // And it is NOT the mirrorX answer (mirrorX is the EW antimeridian formula, L -> -L).
        assertTrue(target != HemispherePassage.mirrorX(530.0, 0.0), "antipodal != mirrorX");
        // The repro target survives the A2 clamp un-moved (|-6970| < clampAbs(7500) ~= 7288.7).
        assertEquals(-6970, PoleArrivalSearch.clampX((int) Math.round(target), 0.0,
                PoleArrivalSearch.xClampAbs(7500.0)));
    }

    @Test
    void antipodalTransform_westToEast() {
        // Westward departure mirrors the law: x = -2000 on xRadius 7500 -> -2000 + 7500 = +5500.
        assertEquals(5500.0, PoleArrivalSearch.antipodalX(-2000.0, 0.0, 7500.0), EPS);
        // Off-center border: the transform is centered on centerX.
        assertEquals(100.0 + 5500.0, PoleArrivalSearch.antipodalX(100.0 - 2000.0, 100.0, 7500.0), EPS);
    }

    @Test
    void antipodalCornerPrimeMeridian_mapsToEdgeAndClampPullsInward() {
        // sign(0) = +1 by convention: the prime meridian (x == centerX) maps to the WEST edge -xRadius --
        // +-180 are the SAME meridian, either edge is correct. That target IS the E/W border corner, and the
        // documented path is the existing A2 clamp pulling every target/candidate inward of the EW band.
        double target = PoleArrivalSearch.antipodalX(0.0, 0.0, 7500.0);
        assertEquals(-7500.0, target, EPS, "prime meridian -> the west edge (sign(0)=+1)");
        double clampAbs = PoleArrivalSearch.xClampAbs(7500.0);
        int clamped = PoleArrivalSearch.clampX((int) Math.round(target), 0.0, clampAbs);
        assertEquals((int) Math.round(0.0 - clampAbs), clamped, "clamp pulls the corner target to the A2 bound");
        assertTrue(Math.abs(clamped) < 7500, "clamped strictly inside the border corner");
        // ...and equatorward of the whole EW ceremony (>= rearm-ish in from the edge; the +-0.5 rounding of
        // the clamp bound is far inside the 64-block A2 margin).
        double distFromEwEdge = 7500.0 - Math.abs(clamped);
        assertTrue(distFromEwEdge >= EdgeGeometry.resolve(7500.0).rearmDist(),
                "corner arrival stays outside the EW prompt/fog/re-arm band");
    }

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
