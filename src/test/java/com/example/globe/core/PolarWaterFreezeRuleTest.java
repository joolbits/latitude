package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarWaterFreezeRule} -- the "all exposed water is frozen at the poles"
 * correctness rule (sibling of {@link PolarPrecipitationRuleTest}).
 *
 * <p>The rule forces any exposed water column eligible to freeze at {@code |lat| >= FREEZE_ALL_DEG}
 * (85 deg) on globe worlds only. These tests pin the threshold boundary, hemisphere symmetry (the caller
 * feeds an absolute latitude, but the helper also abs-es internally so a signed input works), the
 * non-globe safety gate, alignment with the ambient/precipitation thresholds, and NaN handling.
 */
class PolarWaterFreezeRuleTest {

    // ---- threshold boundary ----------------------------------------------------------------

    @Test
    void doesNotFreezeJustBelowThreshold() {
        // 84.999 deg is still equatorward of the freeze cap: vanilla temperature veto stands.
        assertFalse(PolarWaterFreezeRule.freezesWater(true, 84.999));
    }

    @Test
    void freezesExactlyAtThreshold() {
        // The threshold itself is inclusive (>=), so 85.0 forces freeze eligibility.
        assertTrue(PolarWaterFreezeRule.freezesWater(true, PolarWaterFreezeRule.FREEZE_ALL_DEG));
    }

    @Test
    void freezesWellPastThreshold() {
        // Peetsa's report was 89 deg S.
        assertTrue(PolarWaterFreezeRule.freezesWater(true, 89.0));
        assertTrue(PolarWaterFreezeRule.freezesWater(true, 90.0));
    }

    @Test
    void doesNotFreezeInSubpolarBand() {
        // ~66.5 deg subpolar: naturally-cold biomes here already freeze via vanilla and must NOT be
        // second-guessed by this rule, so the latitude gate is false below 85.
        assertFalse(PolarWaterFreezeRule.freezesWater(true, 66.5));
        assertFalse(PolarWaterFreezeRule.freezesWater(true, 80.0));
    }

    // ---- hemisphere symmetry (magnitude is taken internally) -------------------------------

    @Test
    void freezesInSouthernHemisphereToo() {
        // A negative (southern) latitude of the same magnitude behaves identically -- Peetsa was at 89 S.
        assertTrue(PolarWaterFreezeRule.freezesWater(true, -89.0));
    }

    @Test
    void southernSubThresholdDoesNotFreeze() {
        assertFalse(PolarWaterFreezeRule.freezesWater(true, -80.0));
    }

    @Test
    void bothHemispheresAgreeAtThreshold() {
        assertTrue(PolarWaterFreezeRule.freezesWater(true, 85.0));
        assertTrue(PolarWaterFreezeRule.freezesWater(true, -85.0));
    }

    // ---- threshold alignment ---------------------------------------------------------------

    @Test
    void thresholdMatchesAmbientOnset() {
        // Freezing turns on at exactly the ambient snow/fog/wind onset (PolarHazardWindow.AMBIENT_ONSET_DEG),
        // so the blizzard visuals and the frozen ground read as one coherent polar cap.
        assertTrue(PolarWaterFreezeRule.FREEZE_ALL_DEG == PolarHazardWindow.AMBIENT_ONSET_DEG);
    }

    @Test
    void freezeThresholdIsPolewardOfPrecipitationClamp() {
        // Frozen water coincides WITH the ambient window (85); the rain->snow clamp sits a margin BELOW it (75).
        assertTrue(PolarWaterFreezeRule.FREEZE_ALL_DEG > PolarPrecipitationRule.FORCE_SNOW_DEG);
    }

    // ---- non-globe safety gate -------------------------------------------------------------

    @Test
    void neverFreezesOnNonGlobeWorldEvenAtPole() {
        // Vanilla / non-globe worlds are never touched, even at 90 deg.
        assertFalse(PolarWaterFreezeRule.freezesWater(false, 90.0));
    }

    @Test
    void neverFreezesOnNonGlobeWorldAnywhere() {
        assertFalse(PolarWaterFreezeRule.freezesWater(false, 88.0));
        assertFalse(PolarWaterFreezeRule.freezesWater(false, 10.0));
    }

    // ---- NaN safety ------------------------------------------------------------------------

    @Test
    void nanLatitudeDoesNotFreeze() {
        assertFalse(PolarWaterFreezeRule.freezesWater(true, Double.NaN));
    }
}
