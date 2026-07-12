package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
