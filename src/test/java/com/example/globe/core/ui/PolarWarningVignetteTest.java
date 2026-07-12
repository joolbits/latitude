package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarWarningVignette} -- the wall-clock edge-darkening envelope synced to the
 * DANGER/LETHAL polar warning text. Covers: the two mild tiers earn NOTHING (provable no-op), the rise/
 * settle/hold/melt boundaries and peak alphas per serious tier, the lethal linger whisper, and the Reduce
 * Motion static-level substitution.
 */
class PolarWarningVignetteTest {

    private static final float EPS = 1e-4f;

    // --- Only the two serious tiers earn atmosphere ------------------------------------------------------

    @Test
    void mildTiersAndInactiveAreAlwaysInert() {
        // WARN_1 (1), WARN_2 (2), none (0), and out-of-range tiers return a flat 0 at every phase.
        for (int tier : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 5, Integer.MAX_VALUE}) {
            for (long t : new long[] {-100L, 0L, 125L, 250L, 500L, 5000L,
                    PolarWarningVignette.HOLD_END_MS, PolarWarningVignette.PULSE_END_MS, 999999L}) {
                assertEquals(0f, PolarWarningVignette.edgeAlpha(tier, t, true, false), EPS,
                        "tier " + tier + " must never draw a vignette");
                assertEquals(0f, PolarWarningVignette.edgeAlpha(tier, t, true, true), EPS,
                        "tier " + tier + " must never draw a vignette (reduceMotion)");
            }
        }
    }

    @Test
    void peakForTierMatchesConstants() {
        assertEquals(PolarWarningVignette.DANGER_PEAK,
                PolarWarningVignette.peakForTier(PolarWarningVignette.TIER_DANGER), EPS);
        assertEquals(PolarWarningVignette.LETHAL_PEAK,
                PolarWarningVignette.peakForTier(PolarWarningVignette.TIER_LETHAL), EPS);
        assertEquals(0f, PolarWarningVignette.peakForTier(2), EPS);
    }

    // --- Envelope shape (DANGER, non-persisting) ---------------------------------------------------------

    @Test
    void dangerEnvelopeBoundaries() {
        int d = PolarWarningVignette.TIER_DANGER;
        float peak = PolarWarningVignette.DANGER_PEAK;
        float hold = peak * PolarWarningVignette.HOLD_FRAC;

        // Before arm: nothing (danger never lingers).
        assertEquals(0f, PolarWarningVignette.edgeAlpha(d, -50L, false, false), EPS);
        // Arm instant: 0, rising.
        assertEquals(0f, PolarWarningVignette.edgeAlpha(d, 0L, false, false), EPS);
        // Mid-rise (125 of 250ms): half the peak.
        assertEquals(peak * 0.5f, PolarWarningVignette.edgeAlpha(d, 125L, false, false), EPS);
        // End of rise == crest peak.
        assertEquals(peak, PolarWarningVignette.edgeAlpha(d, PolarWarningVignette.RISE_MS, false, false), EPS);
        // End of settle == faint hold.
        long settleEnd = PolarWarningVignette.RISE_MS + PolarWarningVignette.SETTLE_MS;
        assertEquals(hold, PolarWarningVignette.edgeAlpha(d, settleEnd, false, false), EPS);
        // Deep in the hold: still the faint hold.
        assertEquals(hold, PolarWarningVignette.edgeAlpha(d, 5000L, false, false), EPS);
        assertEquals(hold, PolarWarningVignette.edgeAlpha(d, PolarWarningVignette.HOLD_END_MS - 1L, false, false), EPS);
        // Mid-melt (halfway through the 1s melt): halfway from hold to 0.
        long midMelt = PolarWarningVignette.HOLD_END_MS + PolarWarningVignette.MELT_MS / 2L;
        assertEquals(hold * 0.5f, PolarWarningVignette.edgeAlpha(d, midMelt, false, false), EPS);
        // After the pulse: fully gone.
        assertEquals(0f, PolarWarningVignette.edgeAlpha(d, PolarWarningVignette.PULSE_END_MS, false, false), EPS);
        assertEquals(0f, PolarWarningVignette.edgeAlpha(d, 999999L, false, false), EPS);
    }

    @Test
    void crestIsTheGlobalMaximum() {
        // The rise-end crest is the single highest point of the whole envelope.
        int l = PolarWarningVignette.TIER_LETHAL;
        float crest = PolarWarningVignette.edgeAlpha(l, PolarWarningVignette.RISE_MS, false, false);
        assertEquals(PolarWarningVignette.LETHAL_PEAK, crest, EPS);
        for (long t = 0; t <= PolarWarningVignette.PULSE_END_MS + 500L; t += 37L) {
            assertTrue(PolarWarningVignette.edgeAlpha(l, t, false, false) <= crest + EPS,
                    "no sample may exceed the crest peak; t=" + t);
        }
    }

    // --- Lethal linger ----------------------------------------------------------------------------------

    @Test
    void lethalLingersAtWhisperWhilePersisting() {
        int l = PolarWarningVignette.TIER_LETHAL;
        float linger = PolarWarningVignette.LETHAL_LINGER;
        // Before arm, while already in the lethal zone: the whisper is present.
        assertEquals(linger, PolarWarningVignette.edgeAlpha(l, -10L, true, false), EPS);
        // After the pulse ends, still lethal: the whisper persists (not 0).
        assertEquals(linger, PolarWarningVignette.edgeAlpha(l, PolarWarningVignette.PULSE_END_MS, true, false), EPS);
        assertEquals(linger, PolarWarningVignette.edgeAlpha(l, 999999L, true, false), EPS);
        // Same moment but no longer lethal: nothing.
        assertEquals(0f, PolarWarningVignette.edgeAlpha(l, 999999L, false, false), EPS);
        // The melt floors at the linger (never dips below it) while persisting.
        long midMelt = PolarWarningVignette.HOLD_END_MS + PolarWarningVignette.MELT_MS / 2L;
        assertTrue(PolarWarningVignette.edgeAlpha(l, midMelt, true, false) >= linger - EPS);
    }

    // --- Reduce Motion ----------------------------------------------------------------------------------

    @Test
    void reduceMotionIsFlatHoldThenLinger() {
        int d = PolarWarningVignette.TIER_DANGER;
        float dHold = PolarWarningVignette.DANGER_PEAK * PolarWarningVignette.HOLD_FRAC;
        // No crest: even at the moment the pulse would peak, reduceMotion is the flat faint hold.
        assertEquals(dHold, PolarWarningVignette.edgeAlpha(d, PolarWarningVignette.RISE_MS, false, true), EPS);
        assertEquals(dHold, PolarWarningVignette.edgeAlpha(d, 0L, false, true), EPS);
        assertEquals(dHold, PolarWarningVignette.edgeAlpha(d, 5000L, false, true), EPS);
        // The static level never exceeds the animated crest (calmer, not louder).
        assertTrue(dHold <= PolarWarningVignette.DANGER_PEAK + EPS);
        // After the window: gone for danger, whisper for persisting lethal.
        assertEquals(0f, PolarWarningVignette.edgeAlpha(d, PolarWarningVignette.PULSE_END_MS, false, true), EPS);
        int l = PolarWarningVignette.TIER_LETHAL;
        assertEquals(PolarWarningVignette.LETHAL_LINGER,
                PolarWarningVignette.edgeAlpha(l, PolarWarningVignette.PULSE_END_MS, true, true), EPS);
        // Lethal reduce-motion hold is the max of its faint hold and the whisper.
        float lHold = PolarWarningVignette.LETHAL_PEAK * PolarWarningVignette.HOLD_FRAC;
        assertEquals(Math.max(lHold, PolarWarningVignette.LETHAL_LINGER),
                PolarWarningVignette.edgeAlpha(l, 3000L, true, true), EPS);
    }
}
