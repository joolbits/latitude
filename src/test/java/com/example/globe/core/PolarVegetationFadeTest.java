package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarVegetationFade} (polar small-vegetation fade,
 * {@code latitude.polarVegetationFade.enabled}).
 *
 * <p>Covers the two contracts the world-side wiring relies on: the latitude keep-chance ramp
 * ({@link PolarVegetationFade#keepChance01(double)}) and the fray strip decision
 * ({@link PolarVegetationFade#stripByNoise(double, double)}).
 */
class PolarVegetationFadeTest {

    private static final double EPS = 1e-9;

    // --- keepChance01: containment below onset (byte-identical region) ---------------------------

    @Test
    void keepIsExactlyOneWellBelowOnset() {
        assertEquals(1.0, PolarVegetationFade.keepChance01(0.0), EPS);
        assertEquals(1.0, PolarVegetationFade.keepChance01(45.0), EPS);
        assertEquals(1.0, PolarVegetationFade.keepChance01(70.0), EPS);
        assertEquals(1.0, PolarVegetationFade.keepChance01(77.999), EPS);
    }

    @Test
    void keepIsExactlyOneAtTheExactOnsetBoundary() {
        // The boundary itself must still be fully kept so the caller's keep>=1.0 short-circuit
        // makes the onset column bitwise-untouched.
        assertEquals(1.0, PolarVegetationFade.keepChance01(PolarVegetationFade.ONSET_DEG), EPS);
    }

    // --- keepChance01: pole cap ------------------------------------------------------------------

    @Test
    void keepIsExactlyZeroAtAndAboveFull() {
        assertEquals(0.0, PolarVegetationFade.keepChance01(PolarVegetationFade.FULL_DEG), EPS);
        assertEquals(0.0, PolarVegetationFade.keepChance01(PolarVegetationFade.FULL_DEG + 2.0), EPS);
        assertEquals(0.0, PolarVegetationFade.keepChance01(90.0), EPS);
        assertEquals(0.0, PolarVegetationFade.keepChance01(120.0), EPS); // clamp beyond pole
    }

    // --- keepChance01: monotonic strictly-decreasing fade in the band ----------------------------

    @Test
    void keepFadesMonotonicallyThroughTheBand() {
        double prev = PolarVegetationFade.keepChance01(PolarVegetationFade.ONSET_DEG);
        for (double deg = PolarVegetationFade.ONSET_DEG + 0.25; deg <= PolarVegetationFade.FULL_DEG; deg += 0.25) {
            double cur = PolarVegetationFade.keepChance01(deg);
            assertTrue(cur <= prev + EPS, "keep must be non-increasing at " + deg + " (" + cur + " > " + prev + ")");
            assertTrue(cur >= 0.0 && cur <= 1.0, "keep out of [0,1] at " + deg + ": " + cur);
            prev = cur;
        }
    }

    @Test
    void midBandIsRoughlyHalfKept() {
        // Smoothstep midpoint at (onset+full)/2 == 82deg -> keep == 0.5 exactly.
        double mid = (PolarVegetationFade.ONSET_DEG + PolarVegetationFade.FULL_DEG) / 2.0;
        assertEquals(0.5, PolarVegetationFade.keepChance01(mid), EPS);
    }

    // --- keepChance01: hemisphere symmetry + NaN safety ------------------------------------------

    @Test
    void bothHemispheresBehaveIdentically() {
        for (double deg : new double[] {0.0, 60.0, 78.0, 80.0, 82.0, 84.0, 86.0, 90.0}) {
            assertEquals(PolarVegetationFade.keepChance01(deg), PolarVegetationFade.keepChance01(-deg), EPS,
                    "hemisphere mismatch at " + deg);
        }
    }

    @Test
    void nanLatitudeKeepsEverything() {
        assertEquals(1.0, PolarVegetationFade.keepChance01(Double.NaN), EPS);
    }

    @Test
    void infiniteLatitudeIsTreatedAsPole() {
        assertEquals(0.0, PolarVegetationFade.keepChance01(Double.POSITIVE_INFINITY), EPS);
        assertEquals(0.0, PolarVegetationFade.keepChance01(Double.NEGATIVE_INFINITY), EPS);
    }

    // --- stripByNoise: threshold semantics -------------------------------------------------------

    @Test
    void fullKeepNeverStripsRegardlessOfNoise() {
        assertFalse(PolarVegetationFade.stripByNoise(1.0, 0.0));
        assertFalse(PolarVegetationFade.stripByNoise(1.0, 0.5));
        assertFalse(PolarVegetationFade.stripByNoise(1.0, 1.0));
        assertFalse(PolarVegetationFade.stripByNoise(1.5, 0.9)); // >1 guarded too
    }

    @Test
    void zeroKeepAlwaysStripsRegardlessOfNoise() {
        assertTrue(PolarVegetationFade.stripByNoise(0.0, 0.0));
        assertTrue(PolarVegetationFade.stripByNoise(0.0, 0.5));
        assertTrue(PolarVegetationFade.stripByNoise(0.0, 0.999));
        assertTrue(PolarVegetationFade.stripByNoise(-0.2, 0.1)); // <0 guarded too
    }

    @Test
    void partialKeepStripsWhenNoiseMeetsOrExceedsChance() {
        // survive iff noise < keep; strip iff noise >= keep.
        assertFalse(PolarVegetationFade.stripByNoise(0.6, 0.59)); // below chance -> survive
        assertTrue(PolarVegetationFade.stripByNoise(0.6, 0.60));  // at chance -> strip
        assertTrue(PolarVegetationFade.stripByNoise(0.6, 0.61));  // above chance -> strip
    }

    // --- nearSurface: surface layer eligible, lush caves protected -------------------------------

    @Test
    void nearSurfaceAcceptsPlacementsAtOrAboveTheSurfaceTop() {
        int surfaceY = 120;
        assertTrue(PolarVegetationFade.nearSurface(surfaceY, surfaceY));       // exactly on the surface
        assertTrue(PolarVegetationFade.nearSurface(surfaceY + 3, surfaceY));   // a few blocks above
    }

    @Test
    void nearSurfaceAcceptsPlacementsWithinTheMarginBelowSurface() {
        int surfaceY = 120;
        // Just inside the margin still counts as surface (grass origin can dip one/two blocks).
        assertTrue(PolarVegetationFade.nearSurface(surfaceY - PolarVegetationFade.SURFACE_MARGIN, surfaceY));
        assertTrue(PolarVegetationFade.nearSurface(surfaceY - (PolarVegetationFade.SURFACE_MARGIN - 1), surfaceY));
    }

    @Test
    void nearSurfaceRejectsLushCavePlacementsWellBelowSurface() {
        int surfaceY = 120;
        // One block past the margin is already "not surface"...
        assertFalse(PolarVegetationFade.nearSurface(surfaceY - PolarVegetationFade.SURFACE_MARGIN - 1, surfaceY));
        // ...and a real lush cave (dozens of blocks down) is firmly protected.
        assertFalse(PolarVegetationFade.nearSurface(40, surfaceY));
        assertFalse(PolarVegetationFade.nearSurface(-20, surfaceY)); // deep cave below y=0
    }

    @Test
    void surfaceMarginIsTightEnoughToSpareCaves() {
        // Guard against someone widening the margin into cave territory: it should stay a handful of
        // blocks, never tens of blocks (which would start mowing shallow cave roofs).
        assertTrue(PolarVegetationFade.SURFACE_MARGIN >= 4 && PolarVegetationFade.SURFACE_MARGIN <= 6,
                "SURFACE_MARGIN should stay in the tight 4-6 surface band, was " + PolarVegetationFade.SURFACE_MARGIN);
    }

    // ---- S11(c): the firefly bush ban (owner-flagged twice) ----------------------------------

    @Test
    void fireflyBannedFromSubpolarOnward() {
        assertEquals(50.0, PolarVegetationFade.FIREFLY_BAN_DEG, 1e-9,
                "the ban starts at the SUBPOLAR onset -- far equatorward of the 78/86 fade");
        assertTrue(PolarVegetationFade.bansFirefly(55.0), "banned at 55");
        assertTrue(PolarVegetationFade.bansFirefly(70.0), "banned at 70");
        assertTrue(PolarVegetationFade.bansFirefly(85.0), "banned at 85");
        assertTrue(PolarVegetationFade.bansFirefly(50.0), "inclusive at the 50 line");
        assertTrue(PolarVegetationFade.bansFirefly(-60.0), "hemisphere-symmetric");
    }

    @Test
    void fireflyAllowedEquatorwardOfTheBan() {
        assertFalse(PolarVegetationFade.bansFirefly(45.0), "allowed at 45 -- temperate evenings keep them");
        assertFalse(PolarVegetationFade.bansFirefly(0.0));
        assertFalse(PolarVegetationFade.bansFirefly(Double.NaN), "bad read: keep (byte-identical direction)");
    }

    @Test
    void otherVegetationUntouchedAtFiftyFive() {
        // The firefly gate is consulted ONLY for firefly_bush placements (the mixin's exact-block check);
        // every other small-vegetation placement rides the ordinary fade, whose keep chance at 55 deg is
        // exactly 1.0 (the 78-deg onset is far poleward) -- other vegetation at 55 is bitwise-untouched.
        assertEquals(1.0, PolarVegetationFade.keepChance01(55.0), 1e-9,
                "the general fade must not strip anything at 55");
        assertTrue(PolarVegetationFade.bansFirefly(55.0), "...while firefly specifically is banned there");
    }

    @Test
    void expectedSurvivingFractionMatchesKeepChance() {
        // Over a uniform noise sweep the surviving fraction should track the keep chance.
        double keep = 0.30;
        int total = 0;
        int survived = 0;
        for (int i = 0; i < 1000; i++) {
            double noise = i / 1000.0; // [0,1)
            total++;
            if (!PolarVegetationFade.stripByNoise(keep, noise)) {
                survived++;
            }
        }
        double frac = survived / (double) total;
        assertEquals(keep, frac, 0.01, "surviving fraction should approximate keep chance");
    }
}
