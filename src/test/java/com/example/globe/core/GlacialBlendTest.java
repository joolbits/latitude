package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link GlacialBlend} (Phase 5 Crew 7 Slice S28 "The Underground Glacial Blend").
 *
 * <p>Pins the two contracts the underground consumers ride: the wide smoothstep
 * {@link GlacialBlend#undergroundGlacialThreshold(double)} band (onset/full/ordering/midpoint/monotone/NaN)
 * and the {@link GlacialBlend#undergroundGlacial(double, double)} region-noise decision (below-threshold =
 * glacial, both NaN idioms, determinism). No {@code -D}, no world seed -- the band is fixed literals.
 */
class GlacialBlendTest {

    private static final double EPS = 1e-9;

    // --- the band constants (owner decision, 2026-07-20: a wide 78-86 transition) ------------------

    @Test
    void bandConstantsAreTheOwnerApprovedWideTransition() {
        assertEquals(78.0, GlacialBlend.BLEND_ONSET_DEG, EPS,
                "onset 78 deg -- far equatorward of the surface barrens (82), so the underground onsets first");
        assertEquals(86.0, GlacialBlend.BLEND_FULL_DEG, EPS, "full 86 deg -- an 8-degree-wide blend band");
        assertTrue(GlacialBlend.BLEND_FULL_DEG > GlacialBlend.BLEND_ONSET_DEG,
                "the smoothstep denominator must be strictly positive");
    }

    // --- threshold curve: 0 at <=78, 1 at >=86, smoothstep midpoint 0.5 at 82, monotone --------------

    @Test
    void thresholdIsZeroAtAndBelowOnset() {
        assertEquals(0.0, GlacialBlend.undergroundGlacialThreshold(78.0), EPS, "exactly at onset -> 0");
        assertEquals(0.0, GlacialBlend.undergroundGlacialThreshold(70.0), EPS, "well below onset -> 0");
        assertEquals(0.0, GlacialBlend.undergroundGlacialThreshold(0.0), EPS, "equator -> 0");
    }

    @Test
    void thresholdIsOneAtAndAboveFull() {
        assertEquals(1.0, GlacialBlend.undergroundGlacialThreshold(86.0), EPS, "exactly at full -> 1");
        assertEquals(1.0, GlacialBlend.undergroundGlacialThreshold(89.5), EPS, "deep cap -> 1");
        assertEquals(1.0, GlacialBlend.undergroundGlacialThreshold(90.0), EPS, "the pole -> 1");
    }

    @Test
    void thresholdSmoothstepMidpointIsHalfAt82() {
        // (82-78)/(86-78) = 0.5 -> smoothstep(0.5) = 0.5, so 82 deg is the ~50% glacial line.
        assertEquals(0.5, GlacialBlend.undergroundGlacialThreshold(82.0), EPS,
                "82 deg is the smoothstep midpoint -> threshold exactly 0.5");
        // smoothstep endpoints have zero slope: just inside onset/full stays very close to 0/1.
        assertTrue(GlacialBlend.undergroundGlacialThreshold(78.5) < 0.02,
                "smoothstep eases in from onset (near-zero slope at 78)");
        assertTrue(GlacialBlend.undergroundGlacialThreshold(85.5) > 0.98,
                "smoothstep eases out to full (near-zero slope at 86)");
    }

    @Test
    void thresholdIsMonotoneNonDecreasingAndBounded() {
        double prev = Double.NEGATIVE_INFINITY;
        for (double deg = 70.0; deg <= 92.0; deg += 0.1) {
            double t = GlacialBlend.undergroundGlacialThreshold(deg);
            assertTrue(t >= 0.0 && t <= 1.0, "threshold in [0,1] at deg=" + deg);
            assertTrue(t >= prev - EPS, "monotone non-decreasing at deg=" + deg);
            prev = t;
        }
    }

    @Test
    void thresholdUsesAbsoluteLatitudeSoBothHemispheresMatch() {
        for (double deg = 0.0; deg <= 90.0; deg += 3.0) {
            assertEquals(GlacialBlend.undergroundGlacialThreshold(deg),
                    GlacialBlend.undergroundGlacialThreshold(-deg), EPS,
                    "north and south of the equator resolve identically at |deg|=" + deg);
        }
    }

    @Test
    void thresholdNaNDegradesToZeroNeverGlacialOnBadData() {
        assertEquals(0.0, GlacialBlend.undergroundGlacialThreshold(Double.NaN), EPS,
                "NaN latitude -> 0 threshold (the safe, byte-identical direction)");
    }

    // --- undergroundGlacial: below-threshold = glacial ----------------------------------------------

    @Test
    void belowOnsetNeverGlacialWhateverTheNoise() {
        for (double n = 0.0; n < 1.0; n += 0.05) {
            assertFalse(GlacialBlend.undergroundGlacial(78.0, n), "at onset (t=0) never glacial, n=" + n);
            assertFalse(GlacialBlend.undergroundGlacial(60.0, n), "well below onset never glacial, n=" + n);
        }
    }

    @Test
    void atAndAboveFullAlwaysGlacialForAnyValidNoise() {
        for (double n = 0.0; n < 1.0; n += 0.05) {
            assertTrue(GlacialBlend.undergroundGlacial(86.0, n), "at full (t=1) always glacial, n=" + n);
            assertTrue(GlacialBlend.undergroundGlacial(89.0, n), "deep cap always glacial, n=" + n);
        }
    }

    @Test
    void midBandSplitsExactlyAtTheThreshold() {
        double t82 = GlacialBlend.undergroundGlacialThreshold(82.0); // 0.5
        assertTrue(GlacialBlend.undergroundGlacial(82.0, t82 - 1e-6), "noise just below threshold -> glacial");
        assertFalse(GlacialBlend.undergroundGlacial(82.0, t82), "noise AT threshold -> not glacial (strict <)");
        assertFalse(GlacialBlend.undergroundGlacial(82.0, t82 + 1e-6), "noise above threshold -> not glacial");
    }

    @Test
    void expectedGlacialFractionOverUniformNoiseEqualsTheThreshold() {
        // With noise ~ uniform[0,1), the glacial share at a latitude is exactly its threshold.
        double deg = 83.0;
        double t = GlacialBlend.undergroundGlacialThreshold(deg);
        int glacial = 0;
        int total = 0;
        for (double n = 0.0; n < 1.0; n += 0.001) {
            if (GlacialBlend.undergroundGlacial(deg, n)) {
                glacial++;
            }
            total++;
        }
        double share = glacial / (double) total;
        assertEquals(t, share, 0.01, "the glacial areal fraction equals the threshold at deg=" + deg);
    }

    // --- NaN idioms + determinism -------------------------------------------------------------------

    @Test
    void nanLatitudeIsNeverGlacial() {
        assertFalse(GlacialBlend.undergroundGlacial(Double.NaN, 0.0),
                "NaN latitude -> false outright (never place glacial on a bad column)");
        assertFalse(GlacialBlend.undergroundGlacial(Double.NaN, Double.NaN),
                "NaN latitude wins even with NaN noise");
    }

    @Test
    void nanNoiseDegradesToMidRegionHalf() {
        // NaN noise reads as 0.5: glacial iff 0.5 < threshold. At 82 (t=0.5) that's 0.5<0.5 = false;
        // past the midpoint (higher latitude, t>0.5) NaN noise resolves glacial, matching a median region.
        assertFalse(GlacialBlend.undergroundGlacial(82.0, Double.NaN),
                "NaN noise at the midpoint reads as 0.5 -> not glacial (0.5 < 0.5 is false)");
        assertTrue(GlacialBlend.undergroundGlacial(85.0, Double.NaN),
                "NaN noise poleward of the midpoint (t>0.5) resolves as a median region would -> glacial");
        assertFalse(GlacialBlend.undergroundGlacial(80.0, Double.NaN),
                "NaN noise equatorward of the midpoint (t<0.5) -> not glacial");
    }

    @Test
    void isDeterministic() {
        for (double deg = 75.0; deg <= 90.0; deg += 1.0) {
            for (double n = 0.0; n < 1.0; n += 0.1) {
                assertEquals(GlacialBlend.undergroundGlacial(deg, n),
                        GlacialBlend.undergroundGlacial(deg, n),
                        "same inputs, same answer at deg=" + deg + " n=" + n);
                assertEquals(GlacialBlend.undergroundGlacialThreshold(deg),
                        GlacialBlend.undergroundGlacialThreshold(deg), EPS, "threshold deterministic");
            }
        }
    }
}
