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

    @Test
    void oneArgLiveVolumeIsTheSkyExposedOverload() {
        // The convenience one-arg form must equal the sky-exposed two-arg form at every latitude.
        for (double lat = 84.0; lat <= 90.0; lat += 0.25) {
            assertEquals(PolarWindSound.liveVolume(lat, true), PolarWindSound.liveVolume(lat),
                    EPS, "one-arg liveVolume must equal liveVolume(lat, true) at lat=" + lat);
        }
    }

    @Test
    void shelteredMufflesButDoesNotSilence() {
        // Peetsa's ask: keep the wind howling from inside a shelter, just muffled. Deep in the storm the
        // sheltered volume is exactly SHELTERED_VOLUME_SCALE of the open-air volume -- quieter, never zero.
        for (double lat : new double[]{86.0, 87.5, 89.0, 90.0}) {
            float exposed = PolarWindSound.liveVolume(lat, true);
            float sheltered = PolarWindSound.liveVolume(lat, false);
            assertTrue(exposed > 0f, "sanity: exposed must be audible at lat=" + lat);
            assertTrue(sheltered > 0f, "sheltered wind must NOT be silenced at lat=" + lat);
            assertTrue(sheltered < exposed, "sheltered wind must be quieter than open-air at lat=" + lat);
            assertEquals(exposed * PolarWindSound.SHELTERED_VOLUME_SCALE, sheltered, EPS,
                    "sheltered must be SHELTERED_VOLUME_SCALE of exposed at lat=" + lat);
        }
    }

    @Test
    void shelteredMuffleFactorIsInTheStatedRange() {
        // The muffle is a real attenuation (a fraction of full), in the ~0.3-0.4 band the design specifies.
        assertTrue(PolarWindSound.SHELTERED_VOLUME_SCALE > 0.0f
                && PolarWindSound.SHELTERED_VOLUME_SCALE < 1.0f, "muffle must attenuate, not amplify/silence");
        assertTrue(PolarWindSound.SHELTERED_VOLUME_SCALE >= 0.3f
                && PolarWindSound.SHELTERED_VOLUME_SCALE <= 0.4f, "muffle should sit in the ~0.3-0.4 band");
    }

    @Test
    void windMuffleFactorBlendsFromFloorToFull() {
        // TEST 78: the continuous muffle blends the 0.35 sealed-room floor up to full open-air at exposure 1.
        assertEquals(PolarWindSound.SHELTERED_VOLUME_SCALE, PolarWindSound.windMuffleFactor(0.0f), EPS,
                "exposure 0 must be the sheltered floor (unchanged sealed-room behaviour)");
        assertEquals(1.0f, PolarWindSound.windMuffleFactor(1.0f), EPS, "exposure 1 must be full volume");
        // Halfway sits between floor and full.
        float mid = PolarWindSound.windMuffleFactor(0.5f);
        assertTrue(mid > PolarWindSound.SHELTERED_VOLUME_SCALE && mid < 1.0f, "half exposure blends, was " + mid);
        assertEquals(0.5f * (1.0f + PolarWindSound.SHELTERED_VOLUME_SCALE), mid, EPS);
        // Under Peetsa's arch (~0.9) the wind is nearly full, NOT muffled to a third.
        assertTrue(PolarWindSound.windMuffleFactor(0.9f) > 0.9f, "arch must read near full");
        // Clamped outside [0,1].
        assertEquals(PolarWindSound.SHELTERED_VOLUME_SCALE, PolarWindSound.windMuffleFactor(-1f), EPS);
        assertEquals(1.0f, PolarWindSound.windMuffleFactor(2f), EPS);
    }

    @Test
    void windMuffleFactorIsMonotonicInExposure() {
        float prev = -1f;
        for (float e = 0f; e <= 1.0f; e += 0.05f) {
            float f = PolarWindSound.windMuffleFactor(e);
            assertTrue(f >= prev - EPS, "muffle must rise with exposure at e=" + e);
            prev = f;
        }
    }

    @Test
    void floatLiveVolumeEndpointsMatchTheBooleanOverload() {
        // The boolean overload must be exactly the exposure 1.0 (exposed) / 0.0 (sheltered) endpoints, so the
        // old 1.0/0.35 behaviour is preserved and existing gates that pass a boolean are unaffected.
        for (double lat = 84.0; lat <= 90.0; lat += 0.25) {
            assertEquals(PolarWindSound.liveVolume(lat, true), PolarWindSound.liveVolume(lat, 1.0f), EPS,
                    "exposed endpoint at lat=" + lat);
            assertEquals(PolarWindSound.liveVolume(lat, false), PolarWindSound.liveVolume(lat, 0.0f), EPS,
                    "sheltered endpoint at lat=" + lat);
        }
    }

    @Test
    void floatLiveVolumeIsMonotonicInExposureDeepInStorm() {
        // Deep in the storm (89 deg, above the alive floor) more exposure means more volume, never less.
        float prev = -1f;
        for (float e = 0f; e <= 1.0f; e += 0.1f) {
            float v = PolarWindSound.liveVolume(89.0, e);
            assertTrue(v >= prev - EPS, "live volume must rise with exposure at e=" + e);
            prev = v;
        }
    }

    @Test
    void shelteredStillFlooredAliveInHysteresisBand() {
        // Even sheltered, a live loop in the dead band keeps the MIN_ALIVE floor so the channel is not culled
        // (0.35 * 0 is still 0, which would otherwise let the engine reclaim the channel and defeat hysteresis).
        assertEquals(PolarWindSound.MIN_ALIVE_VOLUME, PolarWindSound.liveVolume(84.7, false), EPS);
        assertEquals(PolarWindSound.MIN_ALIVE_VOLUME, PolarWindSound.liveVolume(85.0, false), EPS);
        // Below STOP the loop is meant to stop, so no floor even when sheltered.
        assertEquals(0f, PolarWindSound.liveVolume(84.0, false), EPS);
    }
}
