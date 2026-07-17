package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void waterFreezeStaysAt85_decoupledFromAmbient() {
        // B-7 S3 guard: freezing water MODIFIES THE WORLD (places ice), a worldgen-facing seam that must NOT
        // move. When S3 shifted the pure-client ambient snow/fog onset 85 -> 82 (and S8 to 80), this threshold deliberately
        // STAYED at 85 on its own literal anchor -- it is NOT derived from AMBIENT_ONSET_DEG.
        assertEquals(85.0, PolarWaterFreezeRule.FREEZE_ALL_DEG, 1e-9,
                "the water-freeze line is a worldgen seam and must stay at 85 deg");
        assertNotEquals(PolarHazardWindow.AMBIENT_ONSET_DEG, PolarWaterFreezeRule.FREEZE_ALL_DEG,
                "water-freeze (85) is decoupled from the client ambient onset (now 80)");
        // It DOES coincide with the frostbite DAMAGE onset (also its own 85 anchor), so "the water is frozen"
        // and "the cold starts to bite" still read as one line.
        assertEquals(PolarHazardWindow.FROSTBITE_ONSET_DEG, PolarWaterFreezeRule.FREEZE_ALL_DEG, 1e-9);
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

    // ---- B-9a: the sea-freeze FRAY (the razor-seam fix) --------------------------------------

    @Test
    void frayNeutralNoiseReproducesTheRazorLineExactly() {
        // Noise 0.5 = threshold exactly 85: the anchor constant does not move (its own pin above stays),
        // and a neutral sample is bit-for-bit the razor predicate.
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 84.999, 0.5));
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.0, 0.5));
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, -85.0, 0.5), "hemisphere-symmetric");
    }

    @Test
    void frayWandersPlusMinusOneDegree() {
        // Noise 1.0 pushes the front poleward to 86: 85.5 stays liquid there...
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.5, 1.0));
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 86.0, 1.0));
        // ...noise 0.0 pulls it equatorward to 84: 84.0 freezes there.
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 84.0, 0.0));
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 83.9, 0.0));
        assertEquals(1.0, PolarWaterFreezeRule.FRAY_HALF_WIDTH_DEG, 1e-9);
    }

    @Test
    void outsideTheFrayStripFrayedEqualsRazorForEveryNoise() {
        // The consumer only samples noise inside [84,86]; pin WHY that is sound -- outside the strip every
        // possible frayed threshold lands on the same side as the razor.
        for (double noise : new double[]{0.0, 0.25, 0.5, 0.75, 0.999999}) {
            assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 83.9, noise), "below the strip: liquid");
            assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 86.1, noise), "above the strip: frozen");
        }
        assertTrue(PolarWaterFreezeRule.inFreezeFrayBand(84.0));
        assertTrue(PolarWaterFreezeRule.inFreezeFrayBand(86.0));
        assertFalse(PolarWaterFreezeRule.inFreezeFrayBand(83.99));
        assertFalse(PolarWaterFreezeRule.inFreezeFrayBand(86.01));
        assertFalse(PolarWaterFreezeRule.inFreezeFrayBand(Double.NaN), "NaN falls back to the razor path");
    }

    // ---- S11(b): frozen rivers -> complete ice -----------------------------------------------

    @Test
    void riverColumnFreezesSolidInZone() {
        assertTrue(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 86.0, 0.5),
                "a river column in the full-freeze zone freezes surface to bed");
        assertTrue(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, -87.5, 0.5), "both hemispheres");
        // The zone is the SAME frayed front the surface ice uses: 84.2 freezes when the fray dips (noise 0).
        assertTrue(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 84.2, 0.0));
    }

    @Test
    void oceanColumnStaysSurfaceIceOverLiquid() {
        // THE SEA IS EXEMPT: under-ice swim / the pole wall / S7 immersion depend on liquid under the ice.
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, false, true, 89.0, 0.5));
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, true, true, 89.0, 0.5),
                "ocean classification WINS over river if a column somehow reads both");
        // Non-river, non-ocean fluid columns (ponds/lakes) wait for B-9's semi-ice lakes.
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, false, false, 89.0, 0.5));
    }

    @Test
    void belowZoneRiversAndFlagOffUnchanged() {
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 83.9, 0.0),
                "below the frayed zone (threshold 84 at noise 0): liquid river");
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(true, true, false, 80.0, 0.5));
        assertFalse(PolarWaterFreezeRule.freezesRiverSolid(false, true, false, 89.0, 0.5),
                "barrens flag off: byte-identical, rivers keep today's surface-only freeze");
    }

    @Test
    void riverIceKindPlainSurfacePackedBelow() {
        assertFalse(PolarWaterFreezeRule.riverIceIsPacked(0), "surface block: plain ice (the familiar skin)");
        assertTrue(PolarWaterFreezeRule.riverIceIsPacked(1), "below the surface: packed (no melt-back holes)");
        assertTrue(PolarWaterFreezeRule.riverIceIsPacked(12));
    }

    @Test
    void fraySafetyFallbacks() {
        // NaN noise degrades to the exact razor line (never a hole in the ice sheet on bad data)...
        assertTrue(PolarWaterFreezeRule.freezesWaterFrayed(true, 85.0, Double.NaN));
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, 84.999, Double.NaN));
        // ...and non-globe / NaN latitude never freeze, same as the razor predicate.
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(false, 90.0, 0.5));
        assertFalse(PolarWaterFreezeRule.freezesWaterFrayed(true, Double.NaN, 0.5));
    }
}
