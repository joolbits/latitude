package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarPrecipitationRule} -- the "no rain at the poles" correctness rule.
 *
 * <p>The rule forces any RAIN column to render as SNOW at {@code |lat| >= FORCE_SNOW_DEG} (75 deg)
 * on globe worlds only. These tests pin the threshold boundary, hemisphere symmetry (the caller
 * feeds an absolute latitude, but the helper also abs-es internally so a signed input works), the
 * non-globe safety gate, and NaN handling.
 */
class PolarPrecipitationRuleTest {

    // ---- threshold boundary ----------------------------------------------------------------

    @Test
    void doesNotForceSnowJustBelowThreshold() {
        // 74.999 deg is still equatorward of the polar clamp: vanilla weather stands.
        assertFalse(PolarPrecipitationRule.forcesSnow(true, 74.999));
    }

    @Test
    void forcesSnowExactlyAtThreshold() {
        // The threshold itself is inclusive (>=), so 75.0 forces snow.
        assertTrue(PolarPrecipitationRule.forcesSnow(true, PolarPrecipitationRule.FORCE_SNOW_DEG));
    }

    @Test
    void forcesSnowWellPastThreshold() {
        assertTrue(PolarPrecipitationRule.forcesSnow(true, 90.0));
    }

    @Test
    void doesNotForceSnowInSubpolarRainBand() {
        // ~67 deg subpolar taiga: vanilla rain is plausible here and must NOT be clobbered.
        assertFalse(PolarPrecipitationRule.forcesSnow(true, 67.0));
    }

    // ---- hemisphere symmetry (magnitude is taken internally) -------------------------------

    @Test
    void forcesSnowInSouthernHemisphereToo() {
        // A negative (southern) latitude of the same magnitude behaves identically.
        assertTrue(PolarPrecipitationRule.forcesSnow(true, -80.0));
    }

    @Test
    void southernSubThresholdDoesNotForce() {
        assertFalse(PolarPrecipitationRule.forcesSnow(true, -70.0));
    }

    @Test
    void bothHemispheresAgreeAtThreshold() {
        assertTrue(PolarPrecipitationRule.forcesSnow(true, 75.0));
        assertTrue(PolarPrecipitationRule.forcesSnow(true, -75.0));
    }

    // ---- non-globe safety gate -------------------------------------------------------------

    @Test
    void neverForcesSnowOnNonGlobeWorldEvenAtPole() {
        // Vanilla / non-globe worlds are never touched, even at 90 deg.
        assertFalse(PolarPrecipitationRule.forcesSnow(false, 90.0));
    }

    @Test
    void neverForcesSnowOnNonGlobeWorldAnywhere() {
        assertFalse(PolarPrecipitationRule.forcesSnow(false, 80.0));
        assertFalse(PolarPrecipitationRule.forcesSnow(false, 10.0));
    }

    // ---- NaN safety ------------------------------------------------------------------------

    @Test
    void nanLatitudeDoesNotForceSnow() {
        assertFalse(PolarPrecipitationRule.forcesSnow(true, Double.NaN));
    }
}
