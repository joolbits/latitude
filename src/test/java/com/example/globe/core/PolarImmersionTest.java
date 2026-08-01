package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarImmersion} (B-7 S7: "polar water is three degrees colder than the air").
 * Pins the +3 effective-latitude shift (with the 90 cap), the pause truth table (immersion overrides the S4
 * shelter pause; the S5 grace beats everything), the band consequences (frostbite starts at 82 in water,
 * the lethal core at 85 in water), the boat exemption contract, and the leather-drysuit (ColdProtection)
 * interaction.
 */
class PolarImmersionTest {

    private static final double EPS = 1e-9;

    // ---- the +3 shift + cap ------------------------------------------------------------------

    @Test
    void effectiveLatAddsThreeInWaterOnly() {
        assertEquals(85.0, PolarImmersion.effectiveLatDeg(82.0, true), EPS, "82 in water reads 85");
        assertEquals(82.0, PolarImmersion.effectiveLatDeg(82.0, false), EPS, "82 on land reads 82");
        assertEquals(0.0, PolarImmersion.effectiveLatDeg(0.0, false), EPS);
        assertEquals(3.0, PolarImmersion.IMMERSION_SEVERITY_DEG, EPS, "the one live-tunable: +3");
    }

    @Test
    void effectiveLatCapsAtNinety() {
        assertEquals(90.0, PolarImmersion.effectiveLatDeg(88.5, true), EPS, "88.5 + 3 caps at 90");
        assertEquals(90.0, PolarImmersion.effectiveLatDeg(90.0, true), EPS, "the pole itself stays 90");
        assertEquals(89.9, PolarImmersion.effectiveLatDeg(86.9, true), EPS, "just under the cap");
    }

    @Test
    void boatIsNotImmersed_byContract() {
        // The shim passes player.isInWater() and NOTHING else -- vanilla isInWater() is FALSE for a player in
        // a boat, so a boat crossing of the polar sea evaluates at the RAW latitude (the free, story-true
        // exemption). This pure function only ever sees that boolean; inWater=false IS the boat case.
        assertEquals(83.0, PolarImmersion.effectiveLatDeg(83.0, false), EPS,
                "boat at 83: no shift, no frostbite (83 < 85)");
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(PolarImmersion.effectiveLatDeg(83.0, false)));
    }

    // ---- the pause truth table (immersion overrides shelter; grace beats everything) -----------

    @Test
    void pauseTruthTable_immersionOverridesShelter_graceBeatsAll() {
        // (sheltered, inWater, grace) -> paused
        assertFalse(PolarImmersion.coldDamagePaused(false, false, false), "exposed dry: biting (no pause)");
        assertFalse(PolarImmersion.coldDamagePaused(false, true, false), "open-water swimmer: biting");
        assertTrue(PolarImmersion.coldDamagePaused(true, false, false), "sheltered dry: S4 pause holds");
        assertFalse(PolarImmersion.coldDamagePaused(true, true, false),
                "sheltered-reading but IMMERSED (under-ice swimmer): S7 overrides -- walls do not help in the sea");
        assertTrue(PolarImmersion.coldDamagePaused(false, false, true), "grace: paused");
        assertTrue(PolarImmersion.coldDamagePaused(false, true, true),
                "grace beats immersion -- the ceremony window is sacred");
        assertTrue(PolarImmersion.coldDamagePaused(true, false, true), "grace + shelter: paused");
        assertTrue(PolarImmersion.coldDamagePaused(true, true, true), "grace + shelter + water: still paused");
    }

    @Test
    void protectedSwimmerTakesZero_leatherDrysuit() {
        // ColdProtection multiplies the (shifted) amount exactly as on land -- one evaluator, one truth. A
        // full freeze-immune set is a drysuit: the immersed frostbite/lethal amounts multiply to zero.
        double eff = PolarImmersion.effectiveLatDeg(84.0, true); // 87 -> frostbite band, 0.5 HP/s-ish
        float frostbiteHit = PolarHazardWindow.frostbiteDamageAmount(eff)
                * (float) ColdProtection.damageMultiplier(4);
        assertEquals(0.0f, frostbiteHit, 1e-9f, "full set = drysuit = zero frostbite in water");
        double effLethal = PolarImmersion.effectiveLatDeg(86.0, true); // 89 -> lethal core
        float lethalHit = PolarHazardWindow.freezeDamageAmount(PolarHazardWindow.hazardProgress(effLethal))
                * (float) ColdProtection.damageMultiplier(4);
        assertEquals(0.0f, lethalHit, 1e-9f, "full set = zero lethal-core damage in water");
        // ...and a bare swimmer takes the full shifted amount (multiplier 1).
        assertTrue(PolarHazardWindow.frostbiteDamageAmount(eff) * ColdProtection.damageMultiplier(0) > 0.0);
    }

    // ---- band consequences at the S7-shifted latitudes ----------------------------------------

    @Test
    void frostbiteStartsAt82InWater() {
        // 82 + 3 = 85 = FROSTBITE_ONSET: the open liquid sea below the 85 freeze line has teeth.
        assertTrue(PolarHazardWindow.appliesFrostbiteDamage(PolarImmersion.effectiveLatDeg(82.0, true)),
                "swimming at 82 bites like 85 land");
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(PolarImmersion.effectiveLatDeg(81.99, true)),
                "swimming at 81.99 (eff 84.99) is still below the frostbite onset");
        // The same latitude DRY is untouched: 82 land has no damage at all.
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(82.0));
    }

    @Test
    void lethalCoreStartsAt85InWater() {
        // 85 + 3 = 88 = the lethal-core damage onset: an immersed 85-deg swimmer reads the lethal curve...
        double eff = PolarImmersion.effectiveLatDeg(85.0, true);
        assertEquals(88.0, eff, EPS);
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(eff)),
                "swimming at 85 evaluates the lethal core (88)");
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(eff),
                "...and leaves the frostbite band (the two stay mutually exclusive at the shifted 88)");
        // ...while a DRY 85-deg walker is frostbite-only, exactly as before S7.
        assertTrue(PolarHazardWindow.appliesFrostbiteDamage(85.0));
        assertFalse(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(85.0)));
        // Under-ice at 87+: eff 90 = the full 6 HP/s pole cadence -- the wall trek is gated on protection.
        assertEquals(90.0, PolarImmersion.effectiveLatDeg(87.0, true), EPS);
    }

    @Test
    void frostCueFollowsTheShiftedBite() {
        // The cue reads the SAME effective latitude as the bite (wired in GlobeMod): a swimmer at 83 (eff 86)
        // gets the 86-deg cue floor; the same latitude dry gets none.
        int wetCue = PolarHazardWindow.frostbiteFrostCueTicks(PolarImmersion.effectiveLatDeg(83.0, true));
        int dryCue = PolarHazardWindow.frostbiteFrostCueTicks(PolarImmersion.effectiveLatDeg(83.0, false));
        assertTrue(wetCue > 0, "immersed bite shows the frost cue");
        assertEquals(0, dryCue, "dry 83 has no bite and no cue");
        assertEquals(PolarHazardWindow.frostbiteFrostCueTicks(86.0), wetCue, "cue value == the shifted band's");
    }

    @Test
    void deepCaveWaterPausesLatitudeDamageRegardlessOfSkyExposure() {
        assertTrue(PolarImmersion.isDeepCaveInsulatedWater(true, -1),
                "water below Y=0 is a deep-cave pool, not exposed polar sea");
        assertFalse(PolarImmersion.isDeepCaveInsulatedWater(true, 0), "Y=0 remains cold immersion");
        assertFalse(PolarImmersion.isDeepCaveInsulatedWater(true, 64), "open-sea water remains cold immersion");
        assertFalse(PolarImmersion.isDeepCaveInsulatedWater(false, -24),
                "dry players and boat riders are never classified as insulated water");

        assertEquals(89.0, PolarImmersion.effectiveLatDeg(89.0, true, -1), EPS,
                "insulated deep-cave water receives no +3 immersion shift");
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(89.0)),
                "the chosen latitude is genuinely lethal unless the depth pause controls the hurt path");
        assertTrue(PolarImmersion.coldDamagePaused(false, true, -1, false),
                "a sky-breached below-Y0 swimmer is paused by depth itself");
        assertTrue(PolarImmersion.coldDamagePaused(true, true, -1, false),
                "a sheltered below-Y0 swimmer is paused by the same depth rule");

        assertEquals(90.0, PolarImmersion.effectiveLatDeg(89.0, true, 0), EPS,
                "Y=0 water keeps the existing capped +3 cold-sea shift");
        assertEquals(85.0, PolarImmersion.effectiveLatDeg(82.0, true, 64), EPS,
                "open sea keeps the existing +3 shift");
        assertFalse(PolarImmersion.coldDamagePaused(false, true, 0, false),
                "Y=0 is the exact cold-water boundary");
        assertFalse(PolarImmersion.coldDamagePaused(true, true, 0, false),
                "water still overrides shelter at Y=0");
        assertFalse(PolarImmersion.coldDamagePaused(false, true, 64, false),
                "exposed water above Y=0 remains cold");
        assertFalse(PolarImmersion.coldDamagePaused(true, true, 64, false),
                "shelter still does not insulate the surface polar sea");
    }

    @Test
    void deepCaveRuleDoesNotPauseDryOrOtherwiseInapplicableCases() {
        assertEquals(82.0, PolarImmersion.effectiveLatDeg(82.0, false, -24), EPS,
                "dry/boat behavior remains raw latitude at every depth");
        assertFalse(PolarImmersion.coldDamagePaused(false, false, -24, false),
                "depth alone never pauses an exposed dry player");
        assertTrue(PolarImmersion.coldDamagePaused(true, false, -24, false),
                "the existing dry shelter pause remains unchanged underground");
        assertTrue(PolarImmersion.coldDamagePaused(false, true, -24, true),
                "crossing grace still pauses every applicable cold path");
    }

    @Test
    void deepCaveWaterSuppressesVanillaFreezeDamageAtTheActualDecisionSeam() {
        assertTrue(PolarImmersion.shouldSuppressVanillaFreezeDamage(true, -1, 20.0),
                "below-Y0 water suppresses vanilla auto-freeze even far outside Latitude's hazard bands");
        assertTrue(PolarImmersion.shouldSuppressVanillaFreezeDamage(true, -80, 90.0),
                "sky exposure and lethal latitude cannot pierce the deep-water refuge");
        assertFalse(PolarImmersion.shouldSuppressVanillaFreezeDamage(true, 0, 20.0),
                "Y=0 ordinary water is not granted the deep-cave exception");
        assertFalse(PolarImmersion.shouldSuppressVanillaFreezeDamage(true, 64, 87.49),
                "above-Y0 water below the existing lethal band stays vanilla");
        assertTrue(PolarImmersion.shouldSuppressVanillaFreezeDamage(true, 64, 87.5),
                "the existing lethal-band vanilla suppression begins at the same boundary");
        assertFalse(PolarImmersion.shouldSuppressVanillaFreezeDamage(false, -24, 20.0),
                "a dry below-Y0 player does not receive a water-only exception");
    }
}
