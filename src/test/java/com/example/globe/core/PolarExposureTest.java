package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarExposure} -- the TEST 78 continuous enclosure-estimate math that replaced the
 * binary sky-exposure bit for the polar-storm presentation systems. Covers the sample->fraction contract
 * (including the arch and sealed-room cases), the whiteout linear scale, and the particle-budget scale +
 * rounding. The wind-muffle blend is tested in {@link PolarWindSoundTest} beside its shelter-scale constant.
 */
class PolarExposureTest {

    private static final float EPS = 1e-5f;

    @Test
    void fractionIsSeenOverTotal() {
        assertEquals(0.0f, PolarExposure.fraction(0, 13), EPS);
        assertEquals(1.0f, PolarExposure.fraction(13, 13), EPS);
        assertEquals(0.5f, PolarExposure.fraction(6, 12), EPS);
    }

    @Test
    void archCaseReadsMostlyExposed() {
        // Peetsa's open arch: only the center column (under the flat lintel) is blocked, the 12-sample ring
        // sees sky -> ~0.92, comfortably "effectively outdoors".
        float archExposure = PolarExposure.fraction(12, 13);
        assertTrue(archExposure > 0.85f, "an open arch must read as effectively outdoors, was " + archExposure);
    }

    @Test
    void sealedRoomReadsFullyEnclosed() {
        assertEquals(0.0f, PolarExposure.fraction(0, 13), EPS);
    }

    @Test
    void doorwayReadsIntermediate() {
        // A doorway in a wall: roughly half the samples see out.
        float doorway = PolarExposure.fraction(7, 13);
        assertTrue(doorway > 0.3f && doorway < 0.75f, "a doorway must read intermediate, was " + doorway);
    }

    @Test
    void fractionClampsAndGuardsDegenerateTotals() {
        assertEquals(1.0f, PolarExposure.fraction(0, 0), EPS, "no samples -> exposed, never falsely indoors");
        assertEquals(1.0f, PolarExposure.fraction(0, -3), EPS);
        assertEquals(1.0f, PolarExposure.fraction(20, 13), EPS, "seen clamps to total");
        assertEquals(0.0f, PolarExposure.fraction(-5, 13), EPS, "seen clamps to 0");
    }

    @Test
    void whiteoutScaleIsLinearAndClamped() {
        assertEquals(0.0f, PolarExposure.whiteoutScale(0.0f), EPS);
        assertEquals(0.5f, PolarExposure.whiteoutScale(0.5f), EPS);
        assertEquals(1.0f, PolarExposure.whiteoutScale(1.0f), EPS);
        assertEquals(0.0f, PolarExposure.whiteoutScale(-1.0f), EPS);
        assertEquals(1.0f, PolarExposure.whiteoutScale(2.0f), EPS);
        assertEquals(0.0f, PolarExposure.whiteoutScale(Float.NaN), EPS);
    }

    @Test
    void particleBudgetScalesAndRounds() {
        assertEquals(60, PolarExposure.particleBudget(60, 1.0f), "full exposure keeps the whole budget");
        assertEquals(0, PolarExposure.particleBudget(60, 0.0f), "sealed room spawns nothing");
        assertEquals(30, PolarExposure.particleBudget(60, 0.5f));
        assertEquals(54, PolarExposure.particleBudget(60, 0.9f), "under the arch ~0.9 -> nearly full");
        assertEquals(0, PolarExposure.particleBudget(0, 1.0f), "zero base stays zero");
        assertEquals(60, PolarExposure.particleBudget(60, 5.0f), "over-unit exposure clamps");
        assertEquals(0, PolarExposure.particleBudget(60, -2.0f), "negative exposure -> 0");
    }

    @Test
    void particleBudgetNeverExceedsBase() {
        for (int base = 0; base <= 120; base += 7) {
            for (float e = 0f; e <= 1.0f; e += 0.05f) {
                int b = PolarExposure.particleBudget(base, e);
                assertTrue(b >= 0 && b <= base, "budget out of [0,base] at base=" + base + " e=" + e);
            }
        }
    }

    // ---- B-5 item 3: warning-banner + vignette exposure gate ------------------------------------

    @Test
    void warningAlphaIsFullAtAndAboveHalfExposure() {
        // Under a tree/arch (exposure ~0.9) or at exactly the 0.5 threshold, the banner is fully visible --
        // one leaf overhead no longer binary-hides it (TEST 83).
        assertEquals(1.0f, PolarExposure.warningAlpha(0.5f), EPS);
        assertEquals(1.0f, PolarExposure.warningAlpha(0.9f), EPS);
        assertEquals(1.0f, PolarExposure.warningAlpha(1.0f), EPS);
        assertEquals(1.0f, PolarExposure.warningAlpha(2.0f), EPS, "over-unit clamps to full");
    }

    @Test
    void warningAlphaFadesLinearlyBelowHalf() {
        assertEquals(0.5f, PolarExposure.warningAlpha(0.25f), EPS, "half of the 0.5 threshold -> half alpha");
        assertEquals(0.6f, PolarExposure.warningAlpha(0.3f), EPS, "a doorway ~0.3 reads ~60%");
        assertEquals(0.2f, PolarExposure.warningAlpha(0.1f), EPS);
    }

    @Test
    void warningAlphaHiddenOnlyNearZero() {
        // Genuinely sealed in / deep underground: the sampler returns exposure 0 (below sea-2 short-circuit),
        // so the banners hide -- "cave = no storm banners", consistent with the surface-only passage.
        assertEquals(0.0f, PolarExposure.warningAlpha(0.0f), EPS);
        assertEquals(0.0f, PolarExposure.warningAlpha(-1.0f), EPS, "negative clamps to 0");
        assertEquals(0.0f, PolarExposure.warningAlpha(Float.NaN), EPS, "NaN clamps to 0");
    }

    @Test
    void warningAlphaIsMonotonicNonDecreasing() {
        float prev = -1.0f;
        for (float e = 0f; e <= 1.0f; e += 0.05f) {
            float a = PolarExposure.warningAlpha(e);
            assertTrue(a >= prev - EPS, "warningAlpha must not decrease as exposure rises, at e=" + e);
            assertTrue(a >= 0.0f && a <= 1.0f, "warningAlpha out of [0,1] at e=" + e);
            prev = a;
        }
    }

    // ---- B-5 sweep HIGH: the polar-warning ARM gate shares the render gate's function -----------
    // GlobeWarningOverlay.maybeTriggerPoleWarning freezes the episode ladder (no tier advance, no fire) when
    // warningAlpha(exposure01) <= 0, so a tier crossed while sealed is never burned invisibly and fires on
    // surfacing. These pin the two sides of that gate through the SAME shared function the render path fades
    // on, so arm-gate and render-gate can never disagree about "sealed".

    @Test
    void poleWarningArmGateFreezesWhenSealed() {
        // exposure 0 (deep cave / sealed bunker; the sampler's below-sea-2 short-circuit): gate closed --
        // the ladder must NOT advance, so the one-shot is preserved for the moment the player surfaces.
        assertTrue(PolarExposure.warningAlpha(0.0f) <= 0.0f, "sealed (exposure 0) must freeze the arm");
        assertTrue(PolarExposure.warningAlpha(-0.5f) <= 0.0f, "negative exposure must freeze the arm");
        assertTrue(PolarExposure.warningAlpha(Float.NaN) <= 0.0f, "NaN exposure must freeze the arm");
    }

    @Test
    void poleWarningArmGateArmsUnderAnyRealSky() {
        // The smallest nonzero exposure the 13-sample grid can produce (1/13): even one visible sky sample
        // means the player is not sealed, so the tier arms (and renders faded, never burned unseen).
        float oneSample = PolarExposure.fraction(1, 13);
        assertTrue(PolarExposure.warningAlpha(oneSample) > 0.0f, "one sky sample must open the arm gate");
        assertTrue(PolarExposure.warningAlpha(0.9f) > 0.0f, "under a tree/arch the ladder arms as before");
    }

    // ---- B-5 item 2: shared "below the surface" depth cut ---------------------------------------

    @Test
    void isBelowSurfaceMatchesSeaMinusTwo() {
        int sea = 63; // vanilla sea level; sea - MARGIN(2) = 61.
        assertTrue(PolarExposure.isBelowSurface(60, sea), "Y60 < 61 -> below surface");
        assertFalse(PolarExposure.isBelowSurface(61, sea), "Y61 is the cut, not below it");
        assertFalse(PolarExposure.isBelowSurface(62, sea));
        assertFalse(PolarExposure.isBelowSurface(63, sea), "at sea level -> surface");
        assertFalse(PolarExposure.isBelowSurface(120, sea), "high up -> surface");
    }
}
