package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarWaterChill} -- the direct water-contact ticksFrozen/tick curve (S37 F1).
 * Covers: zero below onset, monotone non-decreasing with latitude, outpacing vanilla's -2/tick decay
 * across the whole band (the whole point of the feature), the pole ceiling, NaN safety, and the capped
 * next-value helper.
 */
class PolarWaterChillTest {

    @Test
    void zeroBelowOnset() {
        assertEquals(0, PolarWaterChill.ticksFrozenPerTick(0.0));
        assertEquals(0, PolarWaterChill.ticksFrozenPerTick(60.0));
        assertEquals(0, PolarWaterChill.ticksFrozenPerTick(PolarWaterChill.ONSET_LAT_DEG - 0.1));
    }

    @Test
    void onsetAndPoleEndpoints() {
        assertEquals(PolarWaterChill.PER_TICK_AT_ONSET,
                PolarWaterChill.ticksFrozenPerTick(PolarWaterChill.ONSET_LAT_DEG));
        assertEquals(PolarWaterChill.PER_TICK_AT_POLE,
                PolarWaterChill.ticksFrozenPerTick(PolarWaterChill.POLE_LAT_DEG));
        // Clamped beyond the pole (defensive; |lat| never exceeds 90 in practice).
        assertEquals(PolarWaterChill.PER_TICK_AT_POLE, PolarWaterChill.ticksFrozenPerTick(120.0));
    }

    @Test
    void monotoneNonDecreasing() {
        int prev = -1;
        for (double lat = PolarWaterChill.ONSET_LAT_DEG; lat <= PolarWaterChill.POLE_LAT_DEG; lat += 0.25) {
            int v = PolarWaterChill.ticksFrozenPerTick(lat);
            assertTrue(v >= prev, "per-tick chill must not decrease with latitude at lat=" + lat);
            prev = v;
        }
    }

    @Test
    void alwaysOutpacesVanillaDecay() {
        // The whole point of the feature: net gain (perTick - vanilla's fixed -2/tick decay) must be
        // strictly positive everywhere in the active band, so a player who stays in the water keeps
        // building toward the fully-frozen threshold rather than stalling or losing ground.
        for (double lat = PolarWaterChill.ONSET_LAT_DEG; lat <= PolarWaterChill.POLE_LAT_DEG; lat += 0.5) {
            int perTick = PolarWaterChill.ticksFrozenPerTick(lat);
            int net = perTick - PolarWaterChill.VANILLA_DECAY_PER_TICK;
            assertTrue(net > 0, "net gain must be positive at lat=" + lat + " (perTick=" + perTick + ")");
        }
    }

    @Test
    void nanIsSafe() {
        assertEquals(0, PolarWaterChill.ticksFrozenPerTick(Double.NaN));
    }

    @Test
    void nextTicksFrozenAddsAndCaps() {
        int required = 140; // vanilla default getTicksRequiredToFreeze()
        // Ordinary add: well under the cap.
        int next = PolarWaterChill.nextTicksFrozen(0, PolarWaterChill.ONSET_LAT_DEG, required);
        assertEquals(PolarWaterChill.PER_TICK_AT_ONSET, next);
        // Never decreases the current value even if perTick were somehow 0 (below onset, defensive).
        int unchanged = PolarWaterChill.nextTicksFrozen(50, 0.0, required);
        assertEquals(50, unchanged);
        // Caps at required * CAP_MULTIPLIER regardless of how high current already is.
        int cap = required * PolarWaterChill.CAP_MULTIPLIER;
        int capped = PolarWaterChill.nextTicksFrozen(cap, PolarWaterChill.POLE_LAT_DEG, required);
        assertEquals(cap, capped);
        int overCap = PolarWaterChill.nextTicksFrozen(cap - 1, PolarWaterChill.POLE_LAT_DEG, required);
        assertEquals(cap, overCap);
    }

    @Test
    void sanityCapWellAboveFullyFrozenThreshold() {
        // The cap must sit comfortably above vanilla's 140-tick threshold (else the "fully frozen" state
        // could never be reached/held), and the per-tick numbers stay in a sane single-digit range.
        int required = 140;
        assertTrue(required * PolarWaterChill.CAP_MULTIPLIER > required);
        assertTrue(PolarWaterChill.PER_TICK_AT_ONSET > 0 && PolarWaterChill.PER_TICK_AT_ONSET < 10);
        assertTrue(PolarWaterChill.PER_TICK_AT_POLE >= PolarWaterChill.PER_TICK_AT_ONSET
                && PolarWaterChill.PER_TICK_AT_POLE < 10);
    }
}
