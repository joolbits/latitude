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
 * (a) a VISUAL frost ramp that CROSSES vanilla's 140 fully-frozen threshold at the ~88 deg damage onset
 * (TEST 77 -- so the blue HUD hearts fire off our set value exactly when damage begins) and holds at/above
 * 140 to the pole, and (b) a SEPARATELY-scaled damage curve (interval shrinks + amount grows toward the
 * pole) the mod applies itself, so damage begins ~88 deg and worsens to a lethal pole. Vanilla's own fixed
 * 1 HP/40-tick auto-damage -- which also keys off the 140 threshold -- is cancelled at its aiStep source for
 * in-band players by {@code LivingEntityFreezeDamageMixin}, so our curve stays the SOLE freeze-damage source
 * (the double-damage regression this suite guards against below). The AMBIENT window {@code [85,90]}
 * (snow/fog) and the blizzard VISUAL window {@code [87,90]} are unchanged. Every function under test is a
 * pure function of latitude/progress with no time input.
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
        // WHY: the ambient window opens at AMBIENT_ONSET_DEG (moved 85 -> 82 in B-7 S3); below it there is no
        // snow and no ambient progress at all. Anchored symbolically so the value move can't silently break it.
        assertEquals(0.0, PolarHazardWindow.ambientProgress(PolarHazardWindow.AMBIENT_ONSET_DEG - 0.001));
        assertEquals(0, PolarHazardWindow.snowCount(PolarHazardWindow.AMBIENT_ONSET_DEG - 0.001));
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
        // History: 80 -> 30 (B-4 r2) -> 60 (TEST 78, to fill the new ~16-block-tall spawn volume that
        // replaced the thin overhead band; see GlobeModClient.spawnAmbientPolarSnow).
        assertEquals(60, PolarHazardWindow.SNOW_MAX_COUNT);
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

    // ---- (5) Frost VISUAL ramp (CROSSES vanilla's fully-frozen threshold at the damage onset) -----

    @Test
    void frostVisualCrossesFullyFrozenThresholdExactlyAtDamageOnset() {
        // WHY (the TEST 77 fix): the HUD hearts tint blue only when isFullyFrozen() is true (ticksFrozen >=
        // 140). The old 139 cap kept them from EVER going blue, even while our curve took HP -- Peetsa: "the
        // hearts aren't turning blue while I'm taking damage." The frost must reach EXACTLY 140 at the damage
        // onset (~88 deg) so the blue-heart cue lands the instant freeze damage begins, driven off our value.
        assertEquals(140, PolarHazardWindow.FROZEN_THRESHOLD_TICKS);
        // Clearly below the damage onset (87.75, 87.9 deg): below the threshold -> hearts not blue, no damage.
        assertTrue(PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(87.75))
                        < PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                "frost must be below 140 well under the damage onset (87.75 deg)");
        assertTrue(PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(87.9))
                        < PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                "frost must be below 140 just under the damage onset (87.9 deg)");
        // At the damage onset: exactly the fully-frozen threshold -> isFullyFrozen becomes true.
        assertEquals(PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                PolarHazardWindow.frostVisualTicks(PolarHazardWindow.DAMAGE_ONSET_PROGRESS));
        assertEquals(PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(88.0)));
    }

    @Test
    void frostVisualBuildsThenHoldsAtOrAboveThresholdMonotonicWithHeadroom() {
        // WHY: frost builds 0 -> 140 across the 87.5 -> 88 grace band, then HOLDS at/above 140 (easing up to
        // the pole hold, 148, for decay-headroom) to the pole -- a smooth approach that keeps isFullyFrozen
        // solidly true through the whole damage band so the blue hearts never flicker off.
        assertEquals(148, PolarHazardWindow.FROST_VISUAL_POLE_TICKS);
        assertEquals(PolarHazardWindow.FROZEN_THRESHOLD_TICKS + PolarHazardWindow.FROST_POLE_HEADROOM_TICKS,
                PolarHazardWindow.FROST_VISUAL_POLE_TICKS);
        assertEquals(0, PolarHazardWindow.frostVisualTicks(0.0));
        assertEquals(0, PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(87.5)));
        assertTrue(PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(87.75)) > 0,
                "frost builds visibly in the grace band before the pole");
        // At/after the damage onset it holds >= 140 (fully frozen) all the way to the pole.
        assertTrue(PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(88.0))
                >= PolarHazardWindow.FROZEN_THRESHOLD_TICKS);
        assertTrue(PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(89.0))
                >= PolarHazardWindow.FROZEN_THRESHOLD_TICKS);
        assertEquals(PolarHazardWindow.FROST_VISUAL_POLE_TICKS,
                PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(90.0)));
        assertEquals(PolarHazardWindow.FROST_VISUAL_POLE_TICKS, PolarHazardWindow.frostVisualTicks(1.0));
        // Never runs away above the pole hold, never dips.
        int prev = PolarHazardWindow.frostVisualTicks(0.0);
        for (int i = 0; i <= 1000; i++) {
            int cur = PolarHazardWindow.frostVisualTicks(i / 1000.0);
            assertTrue(cur >= prev, "frostVisualTicks decreased at p=" + i / 1000.0);
            assertTrue(cur <= PolarHazardWindow.FROST_VISUAL_POLE_TICKS,
                    "frostVisualTicks exceeded the pole hold at p=" + i / 1000.0 + ": " + cur);
            prev = cur;
        }
    }

    @Test
    void blueHeartsShowWheneverFreezeDamageIsTaken_noDamageWithoutFullyFrozen() {
        // THE double-damage / feedback invariant: for EVERY progress where our curve deals freeze damage,
        // ticksFrozen is >= 140 (isFullyFrozen -> blue hearts). This is exactly the pairing Peetsa wanted
        // ("hearts blue WHILE taking damage") AND it is the state in which vanilla's own auto-damage would
        // otherwise fire -- which LivingEntityFreezeDamageMixin cancels in-band, so our curve stays the sole
        // source. Below the damage onset there is no damage AND no fully-frozen state, so nothing double-dips.
        for (int i = 0; i <= 1000; i++) {
            double p = i / 1000.0;
            if (PolarHazardWindow.appliesFreezeDamage(p)) {
                // THE cue Peetsa wants: hearts blue for EVERY latitude that takes damage.
                assertTrue(PolarHazardWindow.frostVisualTicks(p) >= PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                        "taking freeze damage but not fully frozen (hearts wouldn't be blue) at p=" + p);
            } else if (p < PolarHazardWindow.DAMAGE_ONSET_PROGRESS - 0.01) {
                // Clearly below the onset (avoiding the ~0.0017 deg rounding sliver right at 88): not yet
                // fully frozen, so nothing is freezing the player and there is no damage of either kind.
                assertTrue(PolarHazardWindow.frostVisualTicks(p) < PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                        "fully frozen well before the damage onset at p=" + p);
            }
        }
    }

    @Test
    void freezeDamagePerSecondComesOnlyFromTheModCurve() {
        // Double-damage REGRESSION GUARD (the top-priority check): pin the mod's expected freeze DPS at several
        // sampled latitudes to the closed form from PolarHazardWindow's OWN constants -- amount * 20 / interval.
        // Live damage-per-unit-time at these latitudes must equal THIS and nothing more; any excess would mean
        // vanilla's 1 HP/40-tick (= 0.5 HP/s) auto-damage is still stacking on top (the regression). With
        // LivingEntityFreezeDamageMixin cancelling vanilla in-band, the mod curve below is the whole budget.
        double[] samples = {88.0, 88.5, 89.0, 89.5, 90.0};
        for (double lat : samples) {
            double p = PolarHazardWindow.hazardProgress(lat);
            assertTrue(PolarHazardWindow.appliesFreezeDamage(p), "expected damage active at " + lat);
            float amount = PolarHazardWindow.freezeDamageAmount(p);
            int interval = PolarHazardWindow.freezeDamageIntervalTicks(p);
            double modDps = amount * 20.0 / interval;
            // Expected value recomputed from the SAME public constants (not a magic number) -- this is the
            // sole-source budget. If a future change reintroduced vanilla stacking, the live rate would exceed
            // modDps by ~0.5 HP/s; this test documents the exact number a playtest should observe.
            double d = (p - PolarHazardWindow.DAMAGE_ONSET_PROGRESS) / (1.0 - PolarHazardWindow.DAMAGE_ONSET_PROGRESS);
            double expAmount = PolarHazardWindow.FREEZE_DAMAGE_MIN_HP
                    + (PolarHazardWindow.FREEZE_DAMAGE_MAX_HP - PolarHazardWindow.FREEZE_DAMAGE_MIN_HP) * d;
            double expInterval = PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_FAR
                    + (PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_NEAR - PolarHazardWindow.FREEZE_DAMAGE_INTERVAL_FAR) * d;
            double expDps = expAmount * 20.0 / Math.max(1, Math.round(expInterval));
            assertEquals(expDps, modDps, 1e-6, "mod freeze DPS drifted from its own curve at " + lat + " deg");
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
        assertEquals(PolarHazardWindow.FROST_VISUAL_POLE_TICKS, PolarHazardWindow.frostVisualTicks(p95));
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
    void stormLevelKeepsItsOwn85OnsetAndAmbientNowLeadsIt() {
        // WHY: stormLevel keeps its OWN 85 onset (STORM_ONSET_DEG), UNCHANGED by B-7 S3 -- still clearly
        // overcast (0.4) at 86 and full by 87.5 (the sun is gone well before the pole). The client ambient
        // snow/fog now begins EARLIER (82, S3), so at 86 the whiteout has out-ramped the storm sky (ambient
        // leads, the sun-fade follows on its own steeper 85->87.5 curve). Documents the decoupled onsets.
        assertEquals(0.4f, PolarHazardWindow.stormLevel(86.0), 1e-4);
        assertTrue(PolarHazardWindow.ambientProgress(86.0) > PolarHazardWindow.stormLevel(86.0),
                "ambient (82 onset) now leads the storm sky (85 onset) at 86 deg");
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

    // ---- TEST 77 r2 item 1: DEPTH-BASED polar fog curves (render-distance fog tightening) ---------
    //
    // Test inputs model a 12-chunk view distance: vanilla renderDistanceEnd == 192 (viewDist*16) and
    // renderDistanceStart == 172.8 (== 192 - clamp(19.2,4,64)); any values work, these are just samples.
    private static final float V_END = 192.0f;
    private static final float V_START = 172.8f;

    @Test
    void polarFogFractionsAreExactlyZeroAtAndBelowAmbientOnset() {
        // WHY: seam-free -- at/below the ambient onset (now 82, B-7 S3) the fog must be vanilla's, bitwise
        // unchanged (no fog pop at onset). Anchored symbolically to the onset constant.
        assertEquals(0.0f, PolarHazardWindow.polarFogEndFraction(PolarHazardWindow.AMBIENT_ONSET_DEG));
        assertEquals(0.0f, PolarHazardWindow.polarFogEndFraction(PolarHazardWindow.AMBIENT_ONSET_DEG - 0.001));
        assertEquals(0.0f, PolarHazardWindow.polarFogStartFraction(PolarHazardWindow.AMBIENT_ONSET_DEG));
        assertEquals(0.0f, PolarHazardWindow.polarFogStartFraction(0.0));
    }

    @Test
    void polarFogEndAndStartReturnVanillaUnchangedAtAndBelowOnset() {
        // WHY: the seam-free contract at the value level -- callers get their own vanilla distances back at/below
        // the ambient onset (now 82, B-7 S3). 80 deg is still below the onset.
        assertEquals(V_END, PolarHazardWindow.polarFogEnd(V_END, PolarHazardWindow.AMBIENT_ONSET_DEG));
        assertEquals(V_END, PolarHazardWindow.polarFogEnd(V_END, 80.0));
        // start clamps below end, but with no tightening end==V_END so start==V_START (< V_END-1).
        assertEquals(V_START, PolarHazardWindow.polarFogStart(V_START, V_END, PolarHazardWindow.AMBIENT_ONSET_DEG));
    }

    @Test
    void polarFogEndOnlyTightensAndReachesNearAtThePole() {
        // WHY: the fog must pull IN (never loosen) and bottom out at the documented pole whiteout distance.
        assertEquals(16.0f, PolarHazardWindow.POLAR_FOG_END_NEAR);
        assertEquals(PolarHazardWindow.POLAR_FOG_END_NEAR, PolarHazardWindow.polarFogEnd(V_END, 90.0), 1e-3);
        assertEquals(PolarHazardWindow.POLAR_FOG_END_NEAR, PolarHazardWindow.polarFogEnd(V_END, 95.0), 1e-3);
        // Everywhere in the window the tightened end is <= vanilla and >= the pole floor.
        for (int i = 0; i <= 1000; i++) {
            double deg = 85.0 + i * (5.0 / 1000.0);
            float end = PolarHazardWindow.polarFogEnd(V_END, deg);
            assertTrue(end <= V_END + 1e-3f, "polar fog end loosened vanilla at " + deg);
            assertTrue(end >= PolarHazardWindow.POLAR_FOG_END_NEAR - 1e-3f, "polar fog end below pole floor at " + deg);
        }
    }

    @Test
    void polarFogEndIsMonotonicNonIncreasingTowardThePole() {
        // WHY: the haze must thicken (sight shorten) smoothly toward 90 -- any rebound would read as flickering.
        float prev = PolarHazardWindow.polarFogEnd(V_END, 85.0);
        for (int i = 1; i <= 1000; i++) {
            double deg = 85.0 + i * (5.0 / 1000.0);
            float cur = PolarHazardWindow.polarFogEnd(V_END, deg);
            assertTrue(cur <= prev + 1e-4f, "polar fog end increased (sight lengthened) at " + deg);
            prev = cur;
        }
    }

    @Test
    void polarFogStartAlwaysStaysBelowTheTightenedEnd() {
        // WHY: START < END is required for a valid linear fog band; the clamp must hold across the whole window.
        for (int i = 0; i <= 1000; i++) {
            double deg = 85.0 + i * (5.0 / 1000.0);
            float end = PolarHazardWindow.polarFogEnd(V_END, deg);
            float start = PolarHazardWindow.polarFogStart(V_START, end, deg);
            assertTrue(start <= end - 1.0f + 1e-4f, "fog start not below end at " + deg + " (" + start + " vs " + end + ")");
        }
    }

    @Test
    void polarFogStartPullsInFasterThanEndSoTheBandWidens() {
        // WHY: START uses a faster curve (0.45 < 0.80) so the fog BAND widens into a gradual heavy haze rather
        // than a hard wall at one range -- so for every interior latitude the START fraction leads the END.
        assertTrue(PolarHazardWindow.POLAR_FOG_START_CURVE < PolarHazardWindow.POLAR_FOG_END_CURVE);
        for (int i = 1; i < 1000; i++) {
            double deg = 85.0 + i * (5.0 / 1000.0); // strictly inside (85,90)
            float sf = PolarHazardWindow.polarFogStartFraction(deg);
            float ef = PolarHazardWindow.polarFogEndFraction(deg);
            assertTrue(sf > ef - 1e-6f, "start fraction did not lead end fraction at " + deg);
        }
    }

    @Test
    void polarFogEndIsClearlyHeavyAtEightyEightAndLightNearTheOnset() {
        // WHY: Peetsa's shelter is ~88 deg and must read HEAVY (sight roughly halved from vanilla). The fog now
        // begins at 82 (B-7 S3), so the LIGHT far haze is the early approach just inside the onset (~83); 86 is
        // now mid-ramp, no longer "light". Documents the numbers a playtest should observe (12-chunk sample).
        float end83 = PolarHazardWindow.polarFogEnd(V_END, 83.0);
        float end88 = PolarHazardWindow.polarFogEnd(V_END, 88.0);
        assertTrue(end83 > 140.0f, "83 deg should still be a light far haze, was " + end83);
        assertTrue(end88 < 120.0f, "88 deg should read clearly heavy, was " + end88);
    }

    @Test
    void test78FogRetuneEndDistances() {
        // The TEST 78 curve tuning (NEAR 24->16, END_CURVE 0.85->0.80) is UNCHANGED; B-7 S3 moved the ambient
        // ONSET 85 -> 82, so at any given latitude the fog is now further along its ramp (heavier) than before.
        // Documents the sight distances (blocks) at a 12-chunk sample (V_END=192) under the new 82 onset: 86 is
        // now mid-ramp (~91), 88 heavy (~52), 90 the pole floor (16, unchanged).
        float end86 = PolarHazardWindow.polarFogEnd(V_END, 86.0);
        float end88 = PolarHazardWindow.polarFogEnd(V_END, 88.0);
        float end90 = PolarHazardWindow.polarFogEnd(V_END, 90.0);
        assertEquals(90.9f, end86, 1.5f, "86 deg end distance (now mid-ramp under the 82 onset)");
        assertEquals(52.2f, end88, 1.5f, "88 deg end distance");
        assertEquals(16.0f, end90, 1e-3f, "90 deg end distance == pole floor (unchanged)");
        // Still heavier than the pre-TEST-78 tuning at 88 and 90 (the parity band).
        assertTrue(end88 < 83.2f, "88 must be heavier than the old 83, was " + end88);
        assertTrue(end90 < 24.0f, "90 must be heavier than the old 24, was " + end90);
    }

    // ---- TEST 77 r2 item 2: blizzard particle drive MAGNITUDES (wind / fall speed vs latitude) -----

    @Test
    void blizzardWindMagnitudeIsBaseThroughTheApproachThenRampsToGaleAtThePole() {
        // WHY: 85-87 deg is the gentle approach (drive 0), so the wind is exactly the base; from 87 it ramps to
        // base+gale at the pole. The large gale ceiling is deliberate (SnowflakeParticle decays horizontal
        // velocity ~5%/tick, so the spawn value must overshoot to still read as sideways over a flake's life).
        assertEquals(0.10, PolarHazardWindow.BLIZZARD_WIND_BASE, 1e-9);
        assertEquals(1.00, PolarHazardWindow.BLIZZARD_WIND_GALE, 1e-9);
        assertEquals(PolarHazardWindow.BLIZZARD_WIND_BASE, PolarHazardWindow.blizzardWindMagnitude(85.0), 1e-9);
        assertEquals(PolarHazardWindow.BLIZZARD_WIND_BASE, PolarHazardWindow.blizzardWindMagnitude(87.0), 1e-9);
        assertEquals(PolarHazardWindow.BLIZZARD_WIND_BASE + PolarHazardWindow.BLIZZARD_WIND_GALE,
                PolarHazardWindow.blizzardWindMagnitude(90.0), 1e-9);
        // Roughly doubled vs the OLD ceiling at Peetsa's 88 deg (old base+gale*1/3 = 0.09+0.34/3 ~ 0.20).
        assertTrue(PolarHazardWindow.blizzardWindMagnitude(88.0) > 0.40,
                "88 deg wind should be clearly driven, was " + PolarHazardWindow.blizzardWindMagnitude(88.0));
    }

    @Test
    void blizzardFallSpeedIsBaseThroughTheApproachThenRampsAtThePole() {
        assertEquals(0.05, PolarHazardWindow.BLIZZARD_FALL_BASE, 1e-9);
        assertEquals(0.25, PolarHazardWindow.BLIZZARD_FALL_GALE, 1e-9);
        assertEquals(PolarHazardWindow.BLIZZARD_FALL_BASE, PolarHazardWindow.blizzardFallSpeed(86.0), 1e-9);
        assertEquals(PolarHazardWindow.BLIZZARD_FALL_BASE + PolarHazardWindow.BLIZZARD_FALL_GALE,
                PolarHazardWindow.blizzardFallSpeed(90.0), 1e-9);
    }

    @Test
    void blizzardWindAndFallAreMonotonicNonDecreasingToThePole() {
        double prevW = PolarHazardWindow.blizzardWindMagnitude(85.0);
        double prevF = PolarHazardWindow.blizzardFallSpeed(85.0);
        for (int i = 1; i <= 1000; i++) {
            double deg = 85.0 + i * (5.0 / 1000.0);
            double w = PolarHazardWindow.blizzardWindMagnitude(deg);
            double f = PolarHazardWindow.blizzardFallSpeed(deg);
            assertTrue(w >= prevW - 1e-9, "wind decreased at " + deg);
            assertTrue(f >= prevF - 1e-9, "fall decreased at " + deg);
            prevW = w;
            prevF = f;
        }
    }

    // ---- B-7 S3: the FROSTBITE band [85,88) + the ambient-onset move + the 89.2 lethal pin --------

    /** DPS helper: HP per hit / (interval in ticks / 20 ticks-per-second). */
    private static double dpsFrostbite(double deg) {
        return PolarHazardWindow.frostbiteDamageAmount(deg)
                / (PolarHazardWindow.frostbiteIntervalTicks(deg) / 20.0);
    }

    @Test
    void frostbiteBandAppliesOnlyOn85to88() {
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(84.99), "no frostbite below 85");
        assertTrue(PolarHazardWindow.appliesFrostbiteDamage(85.0), "frostbite onset at 85");
        assertTrue(PolarHazardWindow.appliesFrostbiteDamage(87.99), "frostbite through just under 88");
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(88.0),
                "frostbite hands off to the lethal core exactly at 88 -- it does NOT apply at/above 88");
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(89.2), "no frostbite in the lethal core");
    }

    @Test
    void frostbiteAndLethalCoreAreMutuallyExclusiveAtTheBoundary() {
        // In [87.5,88): frostbite applies, the lethal core does NOT (grace band, progress < 0.2).
        assertTrue(PolarHazardWindow.appliesFrostbiteDamage(87.7));
        assertFalse(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(87.7)));
        // At exactly 88.0: frostbite stops, the lethal core takes over -- no gap, no overlap.
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(88.0));
        assertTrue(PolarHazardWindow.appliesFreezeDamage(PolarHazardWindow.hazardProgress(88.0)));
    }

    @Test
    void frostbiteDpsRampsGentlyFromQuarterToOneHpPerSecond() {
        // 85 deg: 1.0 HP / 80 ticks = 0.25 HP/s (a distant nibble).
        assertEquals(80, PolarHazardWindow.frostbiteIntervalTicks(85.0));
        assertEquals(0.25, dpsFrostbite(85.0), 1e-9);
        // Just under 88 (the endpoint): 1.0 HP / 20 ticks = 1.0 HP/s (the escalating last warning).
        assertEquals(20, PolarHazardWindow.frostbiteIntervalTicks(88.0));
        assertEquals(1.0, dpsFrostbite(88.0), 1e-9);
        // Midpoint 86.5: interval 50, 0.4 HP/s.
        assertEquals(50, PolarHazardWindow.frostbiteIntervalTicks(86.5));
        assertEquals(0.4, dpsFrostbite(86.5), 1e-9);
        // The interval is monotonically non-increasing (damage never gets gentler poleward within the band).
        int prev = PolarHazardWindow.frostbiteIntervalTicks(85.0);
        for (int i = 1; i <= 300; i++) {
            double deg = 85.0 + i * (3.0 / 300.0);
            int iv = PolarHazardWindow.frostbiteIntervalTicks(deg);
            assertTrue(iv <= prev, "frostbite interval grew (damage got gentler) at " + deg);
            prev = iv;
        }
    }

    @Test
    void ambientOnsetMovedTo82_clientAtmosphereLeadsTheDanger() {
        // B-7 S3: the pure-client ambient snow/fog onset moved 85 -> 82 so the whiteout leads the danger.
        assertEquals(82.0, PolarHazardWindow.AMBIENT_ONSET_DEG, 1e-9);
        // Snow/fog now begin at 82, ahead of the frostbite (85) and lethal (88) bands.
        assertEquals(0, PolarHazardWindow.snowCount(81.99), "no snow just below the 82 onset");
        assertTrue(PolarHazardWindow.snowCount(82.5) > 0, "snow present just inside the 82 onset");
        assertTrue(PolarHazardWindow.AMBIENT_ONSET_DEG < PolarHazardWindow.FROSTBITE_ONSET_DEG);
    }

    @Test
    void lethalCoreAt89point2IsUnchanged_promptZoneSurvivalPinned() {
        // The B-7 prompt sits at 89.2 deg. The design survival table depends on the [88,90] lethal curve being
        // bit-for-bit unchanged by S3: at 89.2 deg the curve deals 2.2 HP every 30 ticks = 1.4667 HP/s. Pin it.
        double progress = PolarHazardWindow.hazardProgress(89.2);
        assertEquals(2.2f, PolarHazardWindow.freezeDamageAmount(progress), 1e-4f,
                "89.2 deg lethal amount must stay 2.2 HP/hit (S3 must not touch the lethal core)");
        assertEquals(30, PolarHazardWindow.freezeDamageIntervalTicks(progress),
                "89.2 deg lethal interval must stay 30 ticks");
        double dps = PolarHazardWindow.freezeDamageAmount(progress)
                / (PolarHazardWindow.freezeDamageIntervalTicks(progress) / 20.0);
        assertEquals(1.4667, dps, 1e-3, "89.2 deg DPS must stay ~1.47 HP/s (design table)");
        // And the frostbite band must not leak into the prompt zone.
        assertFalse(PolarHazardWindow.appliesFrostbiteDamage(89.2));
    }

    // ---- B-7 F3: the frostbite frost-cue floor (no silent damage) ------------------------------

    @Test
    void frostbiteCueZeroOutsideTheBand() {
        assertEquals(0, PolarHazardWindow.frostbiteFrostCueTicks(84.99), "no cue below 85");
        assertEquals(0, PolarHazardWindow.frostbiteFrostCueTicks(0.0));
        assertEquals(0, PolarHazardWindow.frostbiteFrostCueTicks(88.0),
                "at/above 88 the lethal frost visual owns the cue (already at/above 140 there)");
        assertEquals(0, PolarHazardWindow.frostbiteFrostCueTicks(89.5));
    }

    @Test
    void frostbiteCueVisibleFloorAndMonotonicRampToTheHandoff() {
        // A visible creep (min 20) right at the onset -- the damage is never silent...
        assertEquals(PolarHazardWindow.FROSTBITE_CUE_MIN_TICKS,
                PolarHazardWindow.frostbiteFrostCueTicks(85.0), "onset cue = the visible minimum");
        // ...ramping monotonically within the band, never overshooting 140...
        int prev = PolarHazardWindow.frostbiteFrostCueTicks(85.0);
        for (int i = 1; i <= 300; i++) {
            double deg = 85.0 + i * (2.999 / 300.0);
            int cue = PolarHazardWindow.frostbiteFrostCueTicks(deg);
            assertTrue(cue >= prev, "cue decreased at " + deg);
            assertTrue(cue <= PolarHazardWindow.FROZEN_THRESHOLD_TICKS, "cue overshot 140 at " + deg);
            prev = cue;
        }
        // ...to the fully-frozen threshold exactly at the hand-off (continuous with the lethal path's 140 at
        // 88). Mid-band checkpoint 86.5 = half progress = 70 ticks (~50% vignette).
        assertEquals(70, PolarHazardWindow.frostbiteFrostCueTicks(86.5));
        assertEquals(PolarHazardWindow.FROZEN_THRESHOLD_TICKS,
                PolarHazardWindow.frostbiteFrostCueTicks(87.999999), "cue meets 140 at the hand-off");
    }

    @Test
    void frostbiteCueNeverDecreasesWhatTheLethalPathSets() {
        // The wiring composites max(lethalFrostVisual, cueFloor) in the overlap [87.5,88) and applies the cue
        // as a RAISE-only floor elsewhere (if (getTicksFrozen() < cue) set(cue) -- so a vanilla powder-snow
        // value above the cue is also never lowered). Pin the property that makes the composite safe: it is
        // monotone non-decreasing across the whole approach 84.9 -> 90 (no pop-down at 87.5 or 88) and never
        // sits below either input.
        int prevComposite = 0;
        for (int i = 0; i <= 1020; i++) {
            double deg = 84.9 + i * (5.1 / 1020.0);
            int lethal = deg >= PolarHazardWindow.HAZARD_ONSET_DEG
                    ? PolarHazardWindow.frostVisualTicks(PolarHazardWindow.hazardProgress(deg))
                    : 0;
            int cue = PolarHazardWindow.frostbiteFrostCueTicks(deg);
            int composite = Math.max(lethal, cue);
            assertTrue(composite >= lethal, "composite dropped below the lethal set at " + deg);
            assertTrue(composite >= cue, "composite dropped below the cue floor at " + deg);
            assertTrue(composite >= prevComposite,
                    "composite frost popped DOWN at " + deg + " (" + prevComposite + " -> " + composite + ")");
            prevComposite = composite;
        }
    }
}
