package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain-JVM tests for {@link SolarSkyMood} — the P2 sky-mood curves and the A4 storm-supremacy law:
 * gloom/gold envelopes, the {@code (1 − stormLevel)} damp (the S10 overcast and the 85°+ storm always win),
 * star-brightness lift never below vanilla, and alpha-preserving colour blending.
 */
class SolarSkyMoodTest {

    @Test
    void polarNightGloomEnvelope() {
        assertEquals(0.0, SolarSkyMood.polarNightGloom01(5.0), 1e-9, "sun up: no gloom");
        assertEquals(0.0, SolarSkyMood.polarNightGloom01(0.0), 1e-9, "horizon: gloom just begins");
        assertEquals(0.5, SolarSkyMood.polarNightGloom01(-6.0), 1e-9, "half depth at -6");
        assertEquals(1.0, SolarSkyMood.polarNightGloom01(-12.0), 1e-9, "full at GLOOM_FULL_DEPTH_DEG");
        assertEquals(1.0, SolarSkyMood.polarNightGloom01(-40.0), 1e-9, "clamped past full");
        assertEquals(0.0, SolarSkyMood.polarNightGloom01(Double.NaN), 1e-9, "NaN-safe");
    }

    @Test
    void midnightSunGoldEnvelope() {
        assertEquals(1.0, SolarSkyMood.midnightSunGold01(0.0), 1e-9, "grazing sun: full gold");
        assertEquals(0.4, SolarSkyMood.midnightSunGold01(15.0), 1e-9, "fading with altitude");
        assertEquals(0.0, SolarSkyMood.midnightSunGold01(25.0), 1e-9, "ordinary daylight by 25");
        assertEquals(0.0, SolarSkyMood.midnightSunGold01(60.0), 1e-9);
        assertEquals(0.0, SolarSkyMood.midnightSunGold01(-1.0), 1e-9, "below horizon: no gold (belt+suspenders)");
        assertEquals(0.0, SolarSkyMood.midnightSunGold01(Double.NaN), 1e-9, "NaN-safe");
    }

    @Test
    void stormAlwaysWins() {
        // A4: full storm (>= 87.5 deg, stormLevel 1.0) zeroes every mood; half storm halves it.
        assertEquals(0.0, SolarSkyMood.stormDamp(1.0, 1.0), 1e-9, "full overcast: mood extinguished");
        assertEquals(0.5, SolarSkyMood.stormDamp(1.0, 0.5), 1e-9);
        assertEquals(1.0, SolarSkyMood.stormDamp(1.0, 0.0), 1e-9, "clear: mood untouched");
        assertEquals(0.0, SolarSkyMood.stormDamp(1.0, Double.NaN), 1e-9, "NaN storm reads fully stormy (safe side)");
        // Sanity against the real ramp: by STORM_FULL_DEG the mood is gone.
        assertEquals(0.0, SolarSkyMood.stormDamp(1.0, PolarHazardWindow.stormLevel(87.5)), 1e-9);
        assertTrue(SolarSkyMood.stormDamp(1.0, PolarHazardWindow.stormLevel(84.9)) > 0.99,
                "equatorward of the 85 storm onset the mood is essentially undamped");
    }

    @Test
    void starLiftNeverDimsVanilla() {
        assertEquals(0.9f, SolarSkyMood.liftedStarBrightness(0.9f, 0.2), 1e-6f, "vanilla already brighter: keep");
        assertEquals(0.8f, SolarSkyMood.liftedStarBrightness(0.1f, 0.8), 1e-6f, "gloom lifts the stars");
        assertEquals(0.1f, SolarSkyMood.liftedStarBrightness(0.1f, Double.NaN), 1e-6f, "NaN-safe");
    }

    @Test
    void blendRgbPreservesAlphaAndEndpoints() {
        int base = 0xFF3366CC; // opaque blue-ish
        assertEquals(base, SolarSkyMood.blendRgb(base, SolarSkyMood.POLAR_NIGHT_SKY_RGB, 0.0), "t=0: base");
        int full = SolarSkyMood.blendRgb(base, 0x0B111E, 1.0);
        assertEquals(0xFF0B111E, full, "t=1: target rgb under the base alpha");
        int half = SolarSkyMood.blendRgb(0x80000000, 0xFFFFFF, 0.5);
        assertEquals(0x80, (half >>> 24) & 0xFF, "alpha preserved");
        assertEquals(0x80, (half >> 16) & 0xFF, "half-blend channel");
        assertEquals(base, SolarSkyMood.blendRgb(base, 0xFFFFFF, Double.NaN), "NaN t: base (safe)");
    }
}
