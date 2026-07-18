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
    void gloomDeepenedTowardFullDark() {
        // S14(a)(ii): the polar-night gloom ceiling was deepened 0.85 -> 0.97 ("no light on the opposite pole").
        assertTrue(SolarSkyMood.GLOOM_MAX_BLEND > 0.9, "deep polar-night sky reads full dark at global noon");
        assertTrue(SolarSkyMood.GLOOM_MAX_BLEND <= 1.0);
    }

    @Test
    void twilightHoldCurve() {
        // S14(a)(i): 0 at global noon (frac 0.25), 1 at global midnight (0.75), 0.5 at sunrise/sunset (0.0/0.5).
        assertEquals(0.0, SolarSkyMood.twilightHold01(0.25), 1e-9, "global noon: nothing to hold");
        assertEquals(1.0, SolarSkyMood.twilightHold01(0.75), 1e-9, "global midnight: full dusk hold");
        assertEquals(0.5, SolarSkyMood.twilightHold01(0.0), 1e-9, "sunrise: half hold");
        assertEquals(0.5, SolarSkyMood.twilightHold01(0.5), 1e-9, "sunset: half hold");
        // Periodic + continuous across the dayTime-0 wrap (no dawn pop): frac->1 approaches frac=0's value.
        assertEquals(SolarSkyMood.twilightHold01(0.0), SolarSkyMood.twilightHold01(0.999999), 1e-4, "wrap-continuous");
        // Monotone rising noon -> midnight, and flat near midday (smoothstep).
        assertTrue(SolarSkyMood.twilightHold01(0.35) < SolarSkyMood.twilightHold01(0.6), "rises through the evening");
        assertTrue(SolarSkyMood.twilightHold01(0.35) < 0.1, "still ~flat in mid-afternoon");
        assertEquals(0.0, SolarSkyMood.twilightHold01(Double.NaN), 1e-9, "NaN-safe");
    }

    @Test
    void starsSuppressedUnderMidnightSun() {
        assertEquals(0.0f, SolarSkyMood.suppressedStarBrightness(0.9f, 1.0), 1e-6f, "midnight hold: no stars");
        assertEquals(0.9f, SolarSkyMood.suppressedStarBrightness(0.9f, 0.0), 1e-6f, "noon: vanilla ~0 untouched");
        assertEquals(0.25f, SolarSkyMood.suppressedStarBrightness(0.5f, 0.5), 1e-6f, "half hold: half stars");
        assertEquals(0.9f, SolarSkyMood.suppressedStarBrightness(0.9f, Double.NaN), 1e-6f, "NaN-safe (vanilla)");
    }

    @Test
    void atmosphereTintFollowsSkyThroughDayNightMidnightSunPolarNight() {
        // S14(c): the ONE fog/topcoat colour source. Day / sun-up -> base; night/polar-night -> near-black
        // (NOT storm-damped); midnight-sun global night -> held dusk (storm-damped); equinox/equator passthrough.
        int base = 0xFF5A6C84; // opaque storm-blue (a mid fog target)
        // Day (sun up, not midnight sun): untouched.
        assertEquals(base, SolarSkyMood.atmosphereTint(base, false, 10.0, 0.25, 0.0), "daylight fog = base");
        // Night / polar night (sun 12deg down): darkens toward the polar-night near-black, and does so REGARDLESS
        // of the storm (a blizzard at night is a dark-out, not a white wall).
        int expNight = SolarSkyMood.blendRgb(base, SolarSkyMood.POLAR_NIGHT_SKY_RGB, SolarSkyMood.GLOOM_MAX_BLEND);
        assertEquals(expNight, SolarSkyMood.atmosphereTint(base, false, -12.0, 0.5, 0.0), "night: near-black");
        assertEquals(expNight, SolarSkyMood.atmosphereTint(base, false, -12.0, 0.5, 1.0),
                "night darkening is NOT storm-damped (dark-out, not white wall)");
        // Midnight-sun global midnight (sun still up): holds the pink-gold dusk; storm-damped (whiteout owns 85+).
        int expDusk = SolarSkyMood.blendRgb(base, SolarSkyMood.MIDNIGHT_SUN_DUSK_RGB, SolarSkyMood.DUSK_HOLD_MAX_BLEND);
        assertEquals(expDusk, SolarSkyMood.atmosphereTint(base, true, 5.0, 0.75, 0.0), "midnight-sun midnight: dusk");
        assertEquals(base, SolarSkyMood.atmosphereTint(base, true, 5.0, 0.75, 1.0),
                "dusk hold storm-damped to nothing under full overcast");
        assertEquals(base, SolarSkyMood.atmosphereTint(base, true, 5.0, 0.25, 0.0),
                "midnight-sun NOON: no hold (the sky is already bright)");
        // Equinox/equator vanilla passthrough: no band, sun up -> base; NaN elevation -> base (safe).
        assertEquals(base, SolarSkyMood.atmosphereTint(base, false, 45.0, 0.25, 0.0), "equator noon = base");
        assertEquals(base, SolarSkyMood.atmosphereTint(base, false, Double.NaN, 0.5, 0.0), "NaN elevation-safe");
    }

    @Test
    void stormOvercastBlendScalesWithStorm() {
        // S15(d): the cloud-grey overcast blend is 0 when calm, OVERCAST_MAX_BLEND at full storm, linear between,
        // clamped above 1, NaN-safe. (It is a sky-COLOUR blend only -- it never touches rainLevel; A4-safe.)
        assertEquals(0.0, SolarSkyMood.stormOvercastBlend01(0.0), 1e-9, "calm: no overcast tint");
        assertEquals(SolarSkyMood.OVERCAST_MAX_BLEND, SolarSkyMood.stormOvercastBlend01(1.0), 1e-9,
                "full storm: full overcast ceiling");
        assertEquals(SolarSkyMood.OVERCAST_MAX_BLEND * 0.5, SolarSkyMood.stormOvercastBlend01(0.5), 1e-9,
                "half storm: half overcast");
        assertEquals(SolarSkyMood.OVERCAST_MAX_BLEND, SolarSkyMood.stormOvercastBlend01(1.7), 1e-9,
                "clamped above full storm");
        assertEquals(0.0, SolarSkyMood.stormOvercastBlend01(Double.NaN), 1e-9, "NaN-safe");
        assertTrue(SolarSkyMood.OVERCAST_MAX_BLEND > 0.0 && SolarSkyMood.OVERCAST_MAX_BLEND < 1.0,
                "a deepening toward cloud grey, never a flat repaint");
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
