package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarHazardWindow} (Phase 5 Slice B-3, P1: B-3a continuous hazard
 * window + B-3b ambient snow/fog ramp).
 *
 * <p>See {@code docs/binder/phase5-boundary-experience-plan-20260709.md}, "Peetsa's B-3/B-5
 * design intents": B-3a replaces the old stage-STEPPED hazard ladder with CONTINUOUS scaling
 * across {@code [88.5,90]} deg (onset moved in from 87 per Peetsa's TEST 75 note) so the pole stays
 * fully explorable below onset and only the last ~1.5 deg are player-affecting; B-3b starts ambient
 * snow/fog 2 deg earlier at 85 deg ("atmosphere BEFORE
 * danger") and ramps a FIXED per-tick particle budget (never a wall-clock/backlog accumulator —
 * the anti-backlog guardrail) up to a VERY-heavy ceiling at 90 deg. Every function under test is
 * a pure function of latitude with no time input, matching that anti-backlog design: there is no
 * accumulator to "catch up" because nothing here is stateful.
 */
class PolarHazardWindowTest {

    // ---- (1) Containment: hazardProgress == exactly 0.0 at/below HAZARD_ONSET_DEG (87) --------

    @Test
    void hazardProgressIsExactlyZeroJustBelowOnset() {
        // WHY: 88.499 deg is inside the "fully explorable" region per the B-3a design intent (TEST 75 moved
        // onset 87 -> 88.5) — only [88.5,90] is player-affecting, so anything below onset must be bitwise
        // zero, not merely small. 86.999 (still below onset) is likewise a hard zero.
        assertEquals(0.0, PolarHazardWindow.hazardProgress(88.499));
        assertEquals(0.0, PolarHazardWindow.hazardProgress(86.999));
    }

    @Test
    void hazardProgressIsExactlyZeroAtOnsetThreshold() {
        // WHY: HAZARD_ONSET_DEG (88.5) is the documented onset boundary itself, not yet past it.
        assertEquals(88.5, PolarHazardWindow.HAZARD_ONSET_DEG);
        assertEquals(0.0, PolarHazardWindow.hazardProgress(PolarHazardWindow.HAZARD_ONSET_DEG));
    }

    @Test
    void hazardProgressIsPositiveJustAboveNewOnset() {
        // WHY: TEST 75 timing reshape — the hazard must engage immediately past 88.5, not stay dormant to 87.
        assertTrue(PolarHazardWindow.hazardProgress(88.501) > 0.0);
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
        // WHY: B-3b's ambient window opens at 85 deg (AMBIENT_ONSET_DEG); below it there is no
        // snow and no ambient progress at all -- the ambient window is strictly narrower than the
        // "fully explorable" claim only in the sense that it starts atmosphere before danger, but
        // below 85 deg neither exists yet.
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
    void freezeTicksAtFullProgressMatchesVanillaFreezeDeathThreshold() {
        // WHY: FREEZE_MAX_TICKS's javadoc cites vanilla powder-snow freeze damage beginning at 140
        // ticks frozen -- the B-3a full-lethal endpoint must reproduce that exact vanilla value.
        assertEquals(140, PolarHazardWindow.FREEZE_MAX_TICKS);
        assertEquals(140, PolarHazardWindow.freezeTicks(1.0));
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

    // ---- (4) Amplifier tiers --------------------------------------------------------------------

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
        assertEquals(2, PolarHazardWindow.SLOWNESS_MAX_AMP); // == old LETHAL Slowness III
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
        assertEquals(1, PolarHazardWindow.WEAKNESS_MAX_AMP); // == old LETHAL Weakness II
    }

    @Test
    void miningFatigueAppliesAtAndAboveDocumentedProgressThreshold() {
        // WHY: MINING_FATIGUE_PROGRESS == 1/3, documented as "~89 deg" (88.5 + 1.5*(1/3) == 89) after the
        // TEST 75 onset move; the window is now [88.5,90] (1.5 deg wide), not [87,90].
        assertFalse(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.MINING_FATIGUE_PROGRESS - 0.01));
        assertTrue(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.MINING_FATIGUE_PROGRESS));

        double justBelow89 = 88.5 + 1.5 * (PolarHazardWindow.MINING_FATIGUE_PROGRESS - 0.001);
        double atOrAbove89 = 88.5 + 1.5 * PolarHazardWindow.MINING_FATIGUE_PROGRESS;
        assertFalse(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.hazardProgress(justBelow89)));
        assertTrue(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.hazardProgress(atOrAbove89)));
    }

    // B-4 removed the Blindness effect from the polar hazard (the smooth whiteout overlay now carries
    // vision loss), so appliesBlindness/BLINDNESS_PROGRESS no longer exist and are no longer tested.

    // ---- (5) Above-90 clamping: behaves as exactly 90 (progress capped at 1) ------------------

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
    void freezeTicksAndAmplifiersAboveLethalDegClampToLethalValues() {
        double progressAt95 = PolarHazardWindow.hazardProgress(95.0);
        assertEquals(PolarHazardWindow.FREEZE_MAX_TICKS, PolarHazardWindow.freezeTicks(progressAt95));
        assertEquals(PolarHazardWindow.SLOWNESS_MAX_AMP, PolarHazardWindow.slownessAmplifier(progressAt95));
        assertEquals(PolarHazardWindow.WEAKNESS_MAX_AMP, PolarHazardWindow.weaknessAmplifier(progressAt95));
        assertTrue(PolarHazardWindow.appliesMiningFatigue(progressAt95));
    }

    // ---- B-4 round 3 item 1: steadyFreezeTicks (per-tick freeze maintenance target) ------------

    @Test
    void steadyFreezeTicksIsZeroAtAndBelowOnset() {
        // WHY: below the hazard onset (now 88.5) there is no frost at all -- the maintenance target must be a
        // hard 0, never a stray +margin that would frost a fully-explorable latitude.
        assertEquals(0, PolarHazardWindow.steadyFreezeTicks(PolarHazardWindow.hazardProgress(88.5)));
        assertEquals(0, PolarHazardWindow.steadyFreezeTicks(PolarHazardWindow.hazardProgress(87.0)));
        assertEquals(0, PolarHazardWindow.steadyFreezeTicks(PolarHazardWindow.hazardProgress(80.0)));
    }

    @Test
    void steadyFreezeTicksAddsDecayMarginOverRawFreeze() {
        // WHY: the per-tick target must sit above the raw freezeTicks by exactly the decay margin, so a
        // single tick of vanilla ~2/tick decay can never drop the counter below the intended level.
        double p = PolarHazardWindow.hazardProgress(89.25); // mid-band of [88.5,90], raw freeze > 0
        int raw = PolarHazardWindow.freezeTicks(p);
        assertTrue(raw > 0);
        assertEquals(raw + PolarHazardWindow.FREEZE_DECAY_MARGIN, PolarHazardWindow.steadyFreezeTicks(p));
    }

    @Test
    void steadyFreezeTicksAtPoleExceedsFullyFrozenThreshold() {
        // WHY: at the pole the steady target must clear FREEZE_MAX_TICKS (140), the vanilla fully-frozen
        // threshold, and hold there -- that is what makes real freeze damage actually tick (the whole bug).
        int atPole = PolarHazardWindow.steadyFreezeTicks(PolarHazardWindow.hazardProgress(90.0));
        assertTrue(atPole >= PolarHazardWindow.FREEZE_MAX_TICKS,
                "steady target at the pole (" + atPole + ") must be >= " + PolarHazardWindow.FREEZE_MAX_TICKS);
        assertEquals(PolarHazardWindow.FREEZE_MAX_TICKS + PolarHazardWindow.FREEZE_DECAY_MARGIN, atPole);
    }

    @Test
    void realFreezeDamageFirstLandsNearThePole() {
        // WHY (TEST 75 timing intent): "start affecting the player at ~88.5, severe at 90." The steady freeze
        // target crosses vanilla's fully-frozen/damage threshold (140) only in the last fraction of a degree,
        // so real freeze DAMAGE lands as the player reaches the pole itself -- not out at 89. Below ~89.8 the
        // target is still under 140 (blue hearts, no damage yet); at 90 it is clamped above 140 (damage ticks).
        assertTrue(PolarHazardWindow.steadyFreezeTicks(PolarHazardWindow.hazardProgress(89.0))
                < PolarHazardWindow.FREEZE_MAX_TICKS);
        assertTrue(PolarHazardWindow.steadyFreezeTicks(PolarHazardWindow.hazardProgress(89.8))
                < PolarHazardWindow.FREEZE_MAX_TICKS);
        assertTrue(PolarHazardWindow.steadyFreezeTicks(PolarHazardWindow.hazardProgress(90.0))
                >= PolarHazardWindow.FREEZE_MAX_TICKS);
    }

    @Test
    void slownessRespreadAcrossTheNewHazardBand() {
        // WHY (TEST 75): the slowness tiers re-spread across [88.5,90] (three equal thirds of 1.5 deg):
        // Slowness I from 88.5, II from ~89.0, III from ~89.5 -- so nothing slows the player below 88.5.
        assertEquals(0, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(88.0)));
        assertEquals(0, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(88.6)));
        assertEquals(1, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(89.1)));
        assertEquals(2, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(89.6)));
        assertEquals(2, PolarHazardWindow.slownessAmplifier(PolarHazardWindow.hazardProgress(90.0)));
    }

    // ---- B-4 round 3 item 3: stormLevel (steepened storm-sky lift) ----------------------------

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

    // ---- B-4 round 3 item 2: blizzardDrive (fall speed / wind / second-pass gate) --------------

    @Test
    void blizzardDriveIsZeroThroughTheGentleApproachBand() {
        // WHY: 85-87 deg is the gentle-approach flurry -- the blizzard drive (and thus the dense second
        // particle pass) must be exactly 0 there, only ramping inside the [87,90] VISUAL band. TEST 75
        // DECOUPLED this from HAZARD_ONSET_DEG (which moved to 88.5) -- the blizzard LOOK stays on 87.
        assertEquals(87.0, PolarHazardWindow.BLIZZARD_ONSET_DEG);
        assertEquals(0.0f, PolarHazardWindow.blizzardDrive(85.0));
        assertEquals(0.0f, PolarHazardWindow.blizzardDrive(86.5));
        assertEquals(0.0f, PolarHazardWindow.blizzardDrive(PolarHazardWindow.BLIZZARD_ONSET_DEG));
    }

    @Test
    void blizzardDriveIsDecoupledFromTheMovedHazardOnset() {
        // WHY (TEST 75): moving the player-hazard onset 87 -> 88.5 must NOT change the blizzard VISUALS
        // (Peetsa: keep the ambient band as-is). The visual drive still ramps from 87, so at the moved
        // hazard onset (88.5) it is already half-driven, not just starting.
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
