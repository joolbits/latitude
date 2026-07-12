package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarHazardWindow} (Phase 5 Slice B-3 + the TEST 76 freeze redesign).
 *
 * <p>The player-affecting HAZARD window is {@code [87.5,90]} deg (onset moved 88.5 -> 87.5 per Peetsa's
 * TEST 76 note: at 89 deg he took ZERO freeze damage because the old curve only crossed vanilla's
 * fully-frozen threshold in the last fractional degree). Slowness ramps in at 87.5. FREEZE is split into
 * (a) a VISUAL frost ramp capped at 139 -- one tick below vanilla's 140 fully-frozen threshold, so
 * vanilla's own fixed 1 HP/40-tick auto-damage never fires -- and (b) a SEPARATELY-scaled damage curve
 * (interval shrinks + amount grows toward the pole) the mod applies itself, so damage begins ~88 deg and
 * worsens to a lethal pole instead of flipping on at the doorstep. The AMBIENT window {@code [85,90]}
 * (snow/fog) and the blizzard VISUAL window {@code [87,90]} are unchanged -- only the player-affecting
 * mechanics moved. Every function under test is a pure function of latitude/progress with no time input.
 */
class PolarHazardWindowTest {

    // ---- (1) Containment: hazardProgress == exactly 0.0 at/below HAZARD_ONSET_DEG (87.5) -------

    @Test
    void hazardProgressIsExactlyZeroJustBelowOnset() {
        // WHY: 87.499 deg is inside the "fully explorable" region (TEST 76 moved onset 88.5 -> 87.5) -- only
        // [87.5,90] is player-affecting, so anything below onset must be bitwise zero, not merely small.
        assertEquals(0.0, PolarHazardWindow.hazardProgress(87.499));
        assertEquals(0.0, PolarHazardWindow.hazardProgress(85.0));
    }

    @Test
    void hazardProgressIsExactlyZeroAtOnsetThreshold() {
        // WHY: HAZARD_ONSET_DEG (87.5) is the documented onset boundary itself, not yet past it.
        assertEquals(87.5, PolarHazardWindow.HAZARD_ONSET_DEG);
        assertEquals(0.0, PolarHazardWindow.hazardProgress(PolarHazardWindow.HAZARD_ONSET_DEG));
    }

    @Test
    void hazardProgressIsPositiveJustAboveNewOnset() {
        // WHY: TEST 76 timing reshape -- the hazard must engage immediately past 87.5.
        assertTrue(PolarHazardWindow.hazardProgress(87.501) > 0.0);
    }

    @Test
    void hazardProgressIsExactlyZeroAtZeroLatitude() {
        assertEquals(0.0, PolarHazardWindow.hazardProgress(0.0));
    }

    @Test
    void hazardProgressIsExactlyZeroForNegativeLatitude() {
        // WHY: callers pass |lat| per the class javadoc, but a defensive negative input must not
        // silently produce a negative or otherwise nonsensical progress.
        assertEquals(0.0, PolarHazardWindow.hazardProgress(-10.0));
    }

    @Test
    void hazardProgressIsZeroForNaNViaClamp01() {
        // WHY: clamp01's documented NaN->0 behavior must hold through hazardProgress's division.
        assertEquals(0.0, PolarHazardWindow.hazardProgress(Double.NaN));
    }

    @Test
    void ambientProgressAndSnowCountAreZeroBelowAmbientOnset() {
        // WHY: B-3b's ambient window opens at 85 deg (AMBIENT_ONSET_DEG); below it there is no snow and no
        // ambient progress at all. (Ambient is UNCHANGED by the TEST 76 hazard-onset move.)
        assertEquals(0.0, PolarHazardWindow.ambientProgress(84.999));
        assertEquals(0, PolarHazardWindow.snowCount(84.999));
        assertEquals(0.0, PolarHazardWindow.ambientProgress(0.0));
        assertEquals(0, PolarHazardWindow.snowCount(0.0));
    }

    // ---- (2) Monotonic non-decreasing across the approach [80,92] ------------------------------

    @Test
    void hazardProgressIsMonotonicNonDecreasingAcrossApproach() {
        double previous = PolarHazardWindow.hazardProgress(80.0);
        for (int i = 1; i <= 1200; i++) {
            double deg = 80.0 + i * (12.0 / 1200.0); // steps of 0.01 deg up to 92
            double current = PolarHazardWindow.hazardProgress(deg);
            assertTrue(current >= previous - 1e-12,
                    "hazardProgress decreased at deg=" + deg + " (" + previous + " -> " + current + ")");
            previous = current;
        }
    }

    @Test
    void ambientProgressIsMonotonicNonDecreasingAcrossApproach() {
        double previous = PolarHazardWindow.ambientProgress(80.0);
        for (int i = 1; i <= 1200; i++) {
            double deg = 80.0 + i * (12.0 / 1200.0);
            double current = PolarHazardWindow.ambientProgress(deg);
            assertTrue(current >= previous - 1e-12,
                    "ambientProgress decreased at deg=" + deg + " (" + previous + " -> " + current + ")");
            previous = current;
        }
    }

    @Test
    void snowCountIsMonotonicNonDecreasingAcrossApproach() {
        // WHY: B-3b requires the FIXED per-tick budget to thicken smoothly toward the pole -- a
        // dip anywhere in the ramp would read as flickering snowfall rather than an intentional
        // approach to a whiteout.
        int previous = PolarHazardWindow.snowCount(80.0);
        for (int i = 1; i <= 1200; i++) {
            double deg = 80.0 + i * (12.0 / 1200.0);
            int current = PolarHazardWindow.snowCount(deg);
            assertTrue(current >= previous,
                    "snowCount decreased at deg=" + deg + " (" + previous + " -> " + current + ")");
            previous = current;
        }
    }

    // ---- (3) Endpoints -------------------------------------------------------------------------

    @Test
    void hazardProgressIsExactlyOneAtLethalDeg() {
        assertEquals(1.0, PolarHazardWindow.hazardProgress(PolarHazardWindow.HAZARD_LETHAL_DEG));
    }

    @Test
    void snowCountAtLethalDegEqualsSnowMaxCount() {
        assertEquals(PolarHazardWindow.SNOW_MAX_COUNT,
                PolarHazardWindow.snowCount(PolarHazardWindow.AMBIENT_FULL_DEG));
        // B-4 round 2 returned 80->30: real vanilla snowfall (ClientLevelStormSkyMixin) now carries the
        // pole's storm density, so this ambient particle layer is back to subtle near-field texture.
        assertEquals(30, PolarHazardWindow.SNOW_MAX_COUNT);
    }

    @Test
    void snowCountJustAboveAmbientOnsetIsAtLeastSnowMinCount() {
        // WHY: B-3b's "gentle-flurry" onset value (SNOW_MIN_COUNT) must already be reached just
        // past 85 deg, not phased in from zero -- the ramp starts at the min budget, not at 0.
        assertTrue(PolarHazardWindow.snowCount(PolarHazardWindow.AMBIENT_ONSET_DEG + 0.01)
                >= PolarHazardWindow.SNOW_MIN_COUNT);
        assertEquals(2, PolarHazardWindow.SNOW_MIN_COUNT);
    }

    @Test
    void fogIntensityEndpoints() {
        assertEquals(0.0f, PolarHazardWindow.fogIntensity(PolarHazardWindow.AMBIENT_ONSET_DEG));
        assertEquals(1.0f, PolarHazardWindow.fogIntensity(PolarHazardWindow.AMBIENT_FULL_DEG));
    }

    // ---- (4) Amplifier tiers (progress-based, independent of the onset move) --------------------

    @Test
    void slownessAmplifierSpansZeroToMaxNonDecreasingAndNeverExceedsMax() {
        int previous = PolarHazardWindow.slownessAmplifier(0.0);
        assertEquals(0, previous);
        for (int i = 0; i <= 1000; i++) {
            double p = i / 1000.0;
            int current = PolarHazardWindow.slownessAmplifier(p);
            assertTrue(current >= previous, "slownessAmplifier decreased at p=" + p);
            assertTrue(current <= PolarHazardWindow.SLOWNESS_MAX_AMP,
                    "slownessAmplifier exceeded max at p=" + p);
            previous = current;
        }
        assertEquals(PolarHazardWindow.SLOWNESS_MAX_AMP, PolarHazardWindow.slownessAmplifier(1.0));
        assertEquals(2, PolarHazardWindow.SLOWNESS_MAX_AMP); // Slowness III at the pole
    }

    @Test
    void weaknessAmplifierSpansZeroToMaxNonDecreasingAndNeverExceedsMax() {
        int previous = PolarHazardWindow.weaknessAmplifier(0.0);
        assertEquals(0, previous);
        for (int i = 0; i <= 1000; i++) {
            double p = i / 1000.0;
            int current = PolarHazardWindow.weaknessAmplifier(p);
            assertTrue(current >= previous, "weaknessAmplifier decreased at p=" + p);
            assertTrue(current <= PolarHazardWindow.WEAKNESS_MAX_AMP,
                    "weaknessAmplifier exceeded max at p=" + p);
            previous = current;
        }
        assertEquals(PolarHazardWindow.WEAKNESS_MAX_AMP, PolarHazardWindow.weaknessAmplifier(1.0));
        assertEquals(1, PolarHazardWindow.WEAKNESS_MAX_AMP); // Weakness II at the pole
    }

    @Test
    void slownessRespreadAcrossTheNewHazardBand() {
        // WHY (TEST 76): slowness ramps in AT 87.5 and re-spreads across [87.5,90] (three equal thirds of
        // 2.5 deg): Slowness I from 87.5, II from ~88.33, III from ~89.17 -- nothing slows below 87.5.
        assertEquals(0, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(87.5))); // I (amp 0)
        assertEquals(0, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(88.0))); // still I
        assertEquals(1, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(88.5))); // II
        assertEquals(2, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(89.5))); // III
        assertEquals(2, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(90.0)));
    }

    @Test
    void miningFatigueAppliesAtAndAboveDocumentedProgressThreshold() {
        // WHY: MINING_FATIGUE_PROGRESS == 1/3, documented as "~88.33 deg" (87.5 + 2.5*(1/3)) after the
        // TEST 76 onset move; the window is now [87.5,90] (2.5 deg wide).
        assertFalse(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.MINING_FATIGUE_PROGRESS - 0.01));
        assertTrue(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.MINING_FATIGUE_PROGRESS));
        assertEquals(88.33, 87.5 + 2.5 * PolarHazardWindow.MINING_FATIGUE_PROGRESS, 0.01);
    }

    // ---- (5) Frost VISUAL ramp (capped below vanilla's fully-frozen threshold) ------------------

    @Test
    void frostVisualStaysBelowVanillaFullyFrozenThreshold() {
        // WHY (the whole redesign): the frost visual must top out ONE tick below vanilla's 140 fully-frozen
        // threshold so vanilla's own fixed 1 HP/40-tick auto-damage NEVER fires -- our scaled curve owns the
        // damage. If frost ever reached 140 we'd double-dip with vanilla's fixed cadence.
        assertEquals(140, PolarHazardWindow.FROZEN_THRESHOLD_TICKS);
        assertEquals(139, PolarHazardWindow.FROST_VISUAL_MAX_TICKS);
        for (int i = 0; i <= 1000; i++) {
            int f = PolarHazardWindow.frostVisualTicks(i / 1000.0);
            assertTrue(f >= 0 && f <= PolarHazardWindow.FROST_VISUAL_MAX_TICKS,
                    "frostVisualTicks out of range at p=" + i / 1000.0 + ": " + f);
            assertTrue(f < PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                    "frost must stay below the fully-frozen threshold at p=" + i / 1000.0);
        }
        assertEquals(PolarHazardWindow.FROST_VISUAL_MAX_TICKS, PolarHazardWindow.frostVisualTicks(1.0));
    }

    @Test
    void frostVisualRampsToCeilingByFullProgressAndHoldsMonotonic() {
        // WHY: frost builds visibly from the 87.5 onset and reaches its ceiling by FROST_VISUAL_FULL_PROGRESS
        // (~88.75 deg), then holds to the pole -- a smooth approach to a full whiteout, no dip.
        assertEquals(0, PolarHazardWindow.frostVisualTicks(0.0));
        assertEquals(0, PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(87.5)));
        // ~40% frosted at 88 deg, where damage begins (progress 0.2 -> 0.4 of the frost ramp -> round(0.4*139)).
        assertEquals(56, PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(88.0)));
        assertEquals(PolarHazardWindow.FROST_VISUAL_MAX_TICKS,
                PolarHazardWindow.frostVisualTicks(PolarHazardWindow.FROST_VISUAL_FULL_PROGRESS));
        assertEquals(PolarHazardWindow.FROST_VISUAL_MAX_TICKS,
                PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(88.75)));
        assertEquals(PolarHazardWindow.FROST_VISUAL_MAX_TICKS,
                PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(90.0)));
        int prev = PolarHazardWindow.frostVisualTicks(0.0);
        for (int i = 0; i <= 1000; i++) {
            int cur = PolarHazardWindow.frostVisualTicks(i / 1000.0);
            assertTrue(cur >= prev, "frostVisualTicks decreased at p=" + i / 1000.0);
            prev = cur;
        }
    }

    // ---- (6) Freeze DAMAGE curve: begins ~88 deg and intensifies to a lethal pole ---------------

    @Test
    void freezeDamageBeginsAtDamageOnsetNotHazardOnset() {
        // WHY: a 0.5 deg grace band (87.5 onset .. ~88 damage) lets the cold set in + slow you before it
        // starts taking hearts -- frost + slowness first, HP loss only from ~88.
        assertEquals(0.2, PolarHazardWindow.DAMAGE_ONSET_PROGRESS);
        assertFalse(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.DAMAGE_ONSET_PROGRESS - 1e-6));
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.DAMAGE_ONSET_PROGRESS));
        assertFalse(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(87.5)));
        assertFalse(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(87.9)));
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(88.1)));
    }

    @Test
    void freezeDamageLandsWellBeforeThePoleNotAtTheDoorstep() {
        // WHY (TEST 76 REGRESSION -- the core fix): at 89 deg Peetsa took ZERO freeze damage under the old
        // curve. Damage must be active by ~88 and clearly ticking by 89, and get WORSE toward the pole
        // (shorter interval AND bigger amount each step in), not deferred to the last fractional degree.
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(88.0)));
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(88.5)));
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(89.0)));

        int i88 = PolarHazardWindow.freezeDamageIntervalTicks(PolarHazardWindow.hazardProgress(88.0));
        int i89 = PolarHazardWindow.freezeDamageIntervalTicks(PolarHazardWindow.hazardProgress(89.0));
        int i90 = PolarHazardWindow.freezeDamageIntervalTicks(PolarHazardWindow.hazardProgress(90.0));
        assertTrue(i89 < i88, "interval must shrink 88->89 (" + i88 + " -> " + i89 + ")");
        assertTrue(i90 < i89, "interval must shrink 89->90 (" + i89 + " -> " + i90 + ")");
        // At 89 deg the cadence is a real, felt rate (<= ~2 s), not a once-at-the-pole tick.
        assertTrue(i89 <= 40, "89 deg interval should be a felt cadence, was " + i89);

        float a88 = PolarHazardWindow.freezeDamageAmount(PolarHazardWindow.hazardProgress(88.0));
        float a89 = PolarHazardWindow.freezeDamageAmount(PolarHazardWindow.hazardProgress(89.0));
        float a90 = PolarHazardWindow.freezeDamageAmount(PolarHazardWindow.hazardProgress(90.0));
        assertTrue(a89 > a88, "amount must grow 88->89 (" + a88 + " -> " + a89 + ")");
        assertTrue(a90 > a89, "amount must grow 89->90 (" + a89 + " -> " + a90 + ")");
    }

    @Test
    void freezeDamageIntervalShrinksFromFarToNearAndNeverBelowOne() {
        assertEquals(60, PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_FAR);
        assertEquals(10, PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_NEAR);
        assertEquals(PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_FAR,
                PolarHazardWindow.freezeDamageIntervalTicks(PolarHazardWindow.DAMAGE_ONSET_PROGRESS));
        assertEquals(PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_NEAR,
                PolarHazardWindow.freezeDamageIntervalTicks(1.0));
        int prev = PolarHazardWindow.freezeDamageIntervalTicks(0.0);
        for (int i = 0; i <= 1000; i++) {
            int cur = PolarHazardWindow.freezeDamageIntervalTicks(i / 1000.0);
            assertTrue(cur <= prev, "interval must be non-increasing at p=" + i / 1000.0);
            assertTrue(cur >= 1, "interval must be >= 1 at p=" + i / 1000.0);
            prev = cur;
        }
    }

    @Test
    void freezeDamageAmountGrowsFromMinToMax() {
        assertEquals(1.0f, PolarHazardWindow.FREEZE_DAMAGE_MIN_HP);
        assertEquals(3.0f, PolarHazardWindow.FREEZE_DAMAGE_MAX_HP);
        assertEquals(PolarHazardWindow.FREEZE_DAMAGE_MIN_HP,
                PolarHazardWindow.freezeDamageAmount(PolarHazardWindow.DAMAGE_ONSET_PROGRESS), 1e-4);
        assertEquals(PolarHazardWindow.FREEZE_DAMAGE_MAX_HP,
                PolarHazardWindow.freezeDamageAmount(1.0), 1e-4);
        float prev = PolarHazardWindow.freezeDamageAmount(0.0);
        for (int i = 0; i <= 1000; i++) {
            float cur = PolarHazardWindow.freezeDamageAmount(i / 1000.0);
            assertTrue(cur >= prev - 1e-6, "amount must be non-decreasing at p=" + i / 1000.0);
            prev = cur;
        }
    }

    // ---- (7) Above-90 clamping: behaves as exactly 90 (progress capped at 1) --------------------

    @Test
    void hazardProgressAboveLethalDegClampsToOne() {
        assertEquals(1.0, PolarHazardWindow.hazardProgress(95.0));
        assertEquals(PolarHazardWindow.hazardProgress(PolarHazardWindow.HAZARD_LETHAL_DEG),
                PolarHazardWindow.hazardProgress(95.0));
    }

    @Test
    void ambientProgressSnowAndFogAboveFullDegClampToCeiling() {
        assertEquals(1.0, PolarHazardWindow.ambientProgress(95.0));
        assertEquals(PolarHazardWindow.SNOW_MAX_COUNT, PolarHazardWindow.snowCount(95.0));
        assertEquals(1.0f, PolarHazardWindow.fogIntensity(95.0));
    }

    @Test
    void freezeAndAmplifiersAboveLethalDegClampToLethalValues() {
        double p95 = PolarHazardWindow.hazardProgress(95.0);
        assertEquals(1.0, p95);
        assertEquals(PolarHazardWindow.FROST_VISUAL_MAX_TICKS, PolarHazardWindow.frostVisualTicks(p95));
        assertEquals(PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_NEAR,
                PolarHazardWindow.freezeDamageIntervalTicks(p95));
        assertEquals(PolarHazardWindow.FREEZE_DAMAGE_MAX_HP, PolarHazardWindow.freezeDamageAmount(p95), 1e-4);
        assertEquals(PolarHazardWindow.SLOWNESS_MAX_AMP, PolarHazardWindow.slownessAmplifier(p95));
        assertEquals(PolarHazardWindow.WEAKNESS_MAX_AMP, PolarHazardWindow.weaknessAmplifier(p95));
        assertTrue(PolarHazardWindow.appliesMiningFatigue(p95));
        assertTrue(PolarHazardWindow.appliesFreezeDamage(p95));
    }

    // ---- B-4 round 3 item 3: stormLevel (steepened storm-sky lift) -- UNCHANGED by TEST 76 -----

    @Test
    void stormLevelIsZeroAtAndBelowOnset() {
        assertEquals(0.0f, PolarHazardWindow.stormLevel(85.0));
        assertEquals(0.0f, PolarHazardWindow.stormLevel(80.0));
    }

    @Test
    void stormLevelIsSteeperThanAmbientAt86() {
        // WHY: Peetsa saw the sun at 86 deg because the old lift was the linear 85->90 ambientProgress
        // (~0.2 at 86). stormLevel must read clearly overcast (0.4) at 86 -- strictly above ambient.
        assertEquals(0.4f, PolarHazardWindow.stormLevel(86.0), 1e-4);
        assertTrue(PolarHazardWindow.stormLevel(86.0) > (float) PolarHazardWindow.ambientProgress(86.0));
    }

    @Test
    void stormLevelReachesFullByFullDegAndClamps() {
        // WHY: full overcast (sun gone) must be reached by ~87.5 deg and stay clamped past it, not keep
        // climbing toward 90 like the ambient ramp did.
        assertEquals(1.0f, PolarHazardWindow.stormLevel(PolarHazardWindow.STORM_FULL_DEG), 1e-6);
        assertEquals(1.0f, PolarHazardWindow.stormLevel(90.0), 1e-6);
    }

    // ---- B-4 round 3 item 2: blizzardDrive (fall speed / wind / second-pass gate) -- UNCHANGED --

    @Test
    void blizzardDriveIsZeroThroughTheGentleApproachBand() {
        // WHY: 85-87 deg is the gentle-approach flurry -- the blizzard drive (and thus the dense second
        // particle pass) must be exactly 0 there, only ramping inside the [87,90] VISUAL band. This is
        // DECOUPLED from HAZARD_ONSET_DEG -- the blizzard LOOK stays on 87 regardless of the hazard onset.
        assertEquals(87.0, PolarHazardWindow.BLIZZARD_ONSET_DEG);
        assertEquals(0.0f, PolarHazardWindow.blizzardDrive(85.0));
        assertEquals(0.0f, PolarHazardWindow.blizzardDrive(86.5));
        assertEquals(0.0f, PolarHazardWindow.blizzardDrive(PolarHazardWindow.BLIZZARD_ONSET_DEG));
    }

    @Test
    void blizzardDriveIsDecoupledFromTheHazardOnset() {
        // WHY (TEST 75/76): the blizzard VISUALS must NOT move when the player-hazard onset moves (Peetsa:
        // keep the ambient/blizzard band as-is). The visual drive still ramps from 87 -- fixed at 0.5 at
        // 88.5 -- and is already partway driven at the 87.5 hazard onset, proving the two are independent.
        assertEquals(0.5f, PolarHazardWindow.blizzardDrive(88.5), 1e-4);
        assertTrue(PolarHazardWindow.blizzardDrive(PolarHazardWindow.HAZARD_ONSET_DEG) > 0.0f);
    }

    @Test
    void blizzardDriveRampsToFullAtPole() {
        assertEquals(0.5f, PolarHazardWindow.blizzardDrive(88.5), 1e-4);
        assertEquals(1.0f, PolarHazardWindow.blizzardDrive(90.0), 1e-6);
        assertEquals(1.0f, PolarHazardWindow.blizzardDrive(95.0), 1e-6);
    }
}
