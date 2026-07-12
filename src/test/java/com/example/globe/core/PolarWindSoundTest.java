package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarWindSound} -- the latitude->volume envelope + hysteresis thresholds for the
 * looping polar wind bed. Covers: silence below the 85 deg onset, the eased whisper through 85-87 deg, the
 * full howl at the pole, monotonic rise, the clamp above 90, the START/STOP hysteresis ordering, and the
 * alive-floor that keeps the channel from being culled in the hysteresis band.
 */
class PolarWindSoundTest {

    private static final float EPS = 1e-4f;

    @Test
    void silentBelowOnset() {
        assertEquals(0f, PolarWindSound.volume(0.0), EPS);
        assertEquals(0f, PolarWindSound.volume(80.0), EPS);
        assertEquals(0f, PolarWindSound.volume(84.9), EPS);
        assertEquals(0f, PolarWindSound.volume(PolarWindSound.START_DEG), EPS);
    }

    @Test
    void whisperThroughApproachBand() {
        // 87 deg: progress 0.4, squared -> 0.16 of MAX -> ~0.128. Clearly a whisper (< a quarter of max).
        float v87 = PolarWindSound.volume(87.0);
        assertEquals(PolarWindSound.MAX_VOLUME * 0.16f, v87, EPS);
        assertTrue(v87 < PolarWindSound.MAX_VOLUME * 0.25f, "85-87 must be a whisper");
        // 85.5 deg: barely audible.
        assertTrue(PolarWindSound.volume(85.5) < 0.02f);
    }

    @Test
    void howlAtPole() {
        assertEquals(PolarWindSound.MAX_VOLUME, PolarWindSound.volume(90.0), EPS);
        // Clamped above the pole (defensive; |lat| never exceeds 90).
        assertEquals(PolarWindSound.MAX_VOLUME, PolarWindSound.volume(120.0), EPS);
    }

    @Test
    void monotonicNonDecreasing() {
        float prev = -1f;
        for (double lat = 84.0; lat <= 90.0; lat += 0.1) {
            float v = PolarWindSound.volume(lat);
            assertTrue(v >= prev - EPS, "volume must not decrease with latitude at lat=" + lat);
            prev = v;
        }
    }

    @Test
    void hysteresisThresholdsOrdered() {
        assertTrue(PolarWindSound.STOP_DEG < PolarWindSound.START_DEG, "STOP must be below START (dead band)");
        assertTrue(PolarWindSound.shouldStart(85.0));
        assertTrue(PolarWindSound.shouldStart(90.0));
        assertTrue(!PolarWindSound.shouldStart(84.9));
        assertTrue(PolarWindSound.shouldStop(84.4));
        assertTrue(!PolarWindSound.shouldStop(84.5));
        assertTrue(!PolarWindSound.shouldStop(85.0));
        // In the dead band [84.5, 85): neither a fresh start nor a stop -- the loop just carries.
        assertTrue(!PolarWindSound.shouldStart(84.7) && !PolarWindSound.shouldStop(84.7));
    }

    @Test
    void liveVolumeFloorsInsideBandButZeroBelow() {
        // In the hysteresis band the alive loop is floored to the inaudible MIN so the channel is not culled.
        assertEquals(PolarWindSound.MIN_ALIVE_VOLUME, PolarWindSound.liveVolume(84.7), EPS);
        assertEquals(PolarWindSound.MIN_ALIVE_VOLUME, PolarWindSound.liveVolume(85.0), EPS);
        // Below STOP the loop stops, so no floor is applied here.
        assertEquals(0f, PolarWindSound.liveVolume(84.0), EPS);
        // Well into the storm the real envelope is above the floor.
        assertTrue(PolarWindSound.liveVolume(89.0) > PolarWindSound.MIN_ALIVE_VOLUME);
    }
}
