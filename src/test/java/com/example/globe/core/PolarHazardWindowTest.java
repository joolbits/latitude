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
 * across {@code [87,90]} deg so the pole stays fully explorable below onset and only the last
 * 2-3 deg are hazardous; B-3b starts ambient snow/fog 2 deg earlier at 85 deg ("atmosphere BEFORE
 * danger") and ramps a FIXED per-tick particle budget (never a wall-clock/backlog accumulator —
 * the anti-backlog guardrail) up to a VERY-heavy ceiling at 90 deg. Every function under test is
 * a pure function of latitude with no time input, matching that anti-backlog design: there is no
 * accumulator to "catch up" because nothing here is stateful.
 */
class PolarHazardWindowTest {

    // ---- (1) Containment: hazardProgress == exactly 0.0 at/below HAZARD_ONSET_DEG (87) --------

    @Test
    void hazardProgressIsExactlyZeroJustBelowOnset() {
        // WHY: 86.999 deg is inside the "fully explorable" region per the B-3a design intent —
        // only [87,90] is hazardous, so anything below onset must be bitwise zero, not merely small.
        assertEquals(0.0, PolarHazardWindow.hazardProgress(86.999));
    }

    @Test
    void hazardProgressIsExactlyZeroAtOnsetThreshold() {
        // WHY: HAZARD_ONSET_DEG (87) is the documented onset boundary itself, not yet past it.
        assertEquals(0.0, PolarHazardWindow.hazardProgress(PolarHazardWindow.HAZARD_ONSET_DEG));
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
        // WHY: MINING_FATIGUE_PROGRESS == 1/3, documented as "~88 deg" (87 + 3*(1/3) == 88).
        assertFalse(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.MINING_FATIGUE_PROGRESS - 0.01));
        assertTrue(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.MINING_FATIGUE_PROGRESS));

        double justBelow88 = 87.0 + 3.0 * (PolarHazardWindow.MINING_FATIGUE_PROGRESS - 0.001);
        double atOrAbove88 = 87.0 + 3.0 * PolarHazardWindow.MINING_FATIGUE_PROGRESS;
        assertFalse(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.hazardProgress(justBelow88)));
        assertTrue(PolarHazardWindow.appliesMiningFatigue(PolarHazardWindow.hazardProgress(atOrAbove88)));
    }

    @Test
    void blindnessAppliesAtAndAboveDocumentedProgressThreshold() {
        // WHY: BLINDNESS_PROGRESS == 2/3, documented as "~89 deg" (87 + 3*(2/3) == 89).
        assertFalse(PolarHazardWindow.appliesBlindness(PolarHazardWindow.BLINDNESS_PROGRESS - 0.01));
        assertTrue(PolarHazardWindow.appliesBlindness(PolarHazardWindow.BLINDNESS_PROGRESS));

        double justBelow89 = 87.0 + 3.0 * (PolarHazardWindow.BLINDNESS_PROGRESS - 0.001);
        double atOrAbove89 = 87.0 + 3.0 * PolarHazardWindow.BLINDNESS_PROGRESS;
        assertFalse(PolarHazardWindow.appliesBlindness(PolarHazardWindow.hazardProgress(justBelow89)));
        assertTrue(PolarHazardWindow.appliesBlindness(PolarHazardWindow.hazardProgress(atOrAbove89)));
    }

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
        assertTrue(PolarHazardWindow.appliesBlindness(progressAt95));
    }
}
