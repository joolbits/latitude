package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarMusicLaw} -- the S26 "music fades out at the damage line" envelope + ease.
 * Covers: the latitude curve (full music at/below the 82 deg Barrens onset, silent at/above the 85 deg
 * frostbite onset, smoothstep midpoint), the shelter override winning at any latitude, exponential-ease
 * frame-rate independence (one step of N ticks == N steps of one tick), the ~4 s time-constant behaviour,
 * NaN/degenerate inputs degrading to full music (never silence), and the flag-off pin.
 */
class PolarMusicLawTest {

    private static final float EPS = 1e-4f;

    // --- latitude envelope ---

    @Test
    void fullMusicEquatorwardOfFadeBand() {
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.latitudeTarget01(0.0), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.latitudeTarget01(60.0), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.latitudeTarget01(80.0), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.latitudeTarget01(PolarMusicLaw.FADE_START_DEG), EPS);
    }

    @Test
    void silentAtAndPolewardOfDamageLine() {
        assertEquals(PolarMusicLaw.SILENT, PolarMusicLaw.latitudeTarget01(PolarMusicLaw.FADE_END_DEG), EPS);
        assertEquals(PolarMusicLaw.SILENT, PolarMusicLaw.latitudeTarget01(87.0), EPS);
        assertEquals(PolarMusicLaw.SILENT, PolarMusicLaw.latitudeTarget01(90.0), EPS);
    }

    @Test
    void damageLineEqualsFrostbiteOnset() {
        // The music must die EXACTLY where cold damage begins.
        assertEquals(PolarHazardWindow.FROSTBITE_ONSET_DEG, PolarMusicLaw.FADE_END_DEG, 1e-9);
        assertEquals(LatitudeV2Flags.POLAR_BARRENS_ONSET_DEG, PolarMusicLaw.FADE_START_DEG, 1e-9);
    }

    @Test
    void smoothstepMidpointIsHalf() {
        double mid = (PolarMusicLaw.FADE_START_DEG + PolarMusicLaw.FADE_END_DEG) / 2.0;
        // smoothstep(0.5) = 0.5 -> factor = 1 - 0.5 = 0.5
        assertEquals(0.5f, PolarMusicLaw.latitudeTarget01(mid), EPS);
    }

    @Test
    void latitudeCurveIsMonotonicNonIncreasing() {
        float prev = PolarMusicLaw.latitudeTarget01(80.0);
        for (double lat = 80.0; lat <= 86.0; lat += 0.25) {
            float f = PolarMusicLaw.latitudeTarget01(lat);
            assertTrue(f <= prev + EPS, "factor must not rise with latitude at " + lat);
            prev = f;
        }
    }

    // --- shelter override ---

    @Test
    void shelterRestoresFullMusicAtAnyLatitude() {
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.target01(90.0, true), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.target01(85.0, true), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.target01(83.0, true), EPS);
    }

    @Test
    void exposedPolarTargetIsSilent() {
        assertEquals(PolarMusicLaw.SILENT, PolarMusicLaw.target01(88.0, false), EPS);
    }

    // --- ease: frame-rate independence + time constant ---

    @Test
    void easeIsFrameRateIndependent() {
        // One step of 4 ticks must equal four steps of 1 tick (exponential ease is composable).
        float target = 0.0f;
        float oneShot = PolarMusicLaw.ease(1.0f, target, 4.0);

        float stepwise = 1.0f;
        for (int i = 0; i < 4; i++) {
            stepwise = PolarMusicLaw.ease(stepwise, target, 1.0);
        }
        assertEquals(stepwise, oneShot, EPS);
    }

    @Test
    void easeMovesTowardTargetButNotPast() {
        float f = PolarMusicLaw.ease(1.0f, 0.0f, 1.0);
        assertTrue(f < 1.0f && f > 0.0f, "one tick eases partway, not instant and not overshoot");
    }

    @Test
    void fullFadeLandsNearSilenceInAboutFourSeconds() {
        // ~4 s = 80 ticks at 20 TPS; ~4 time constants should be ~98% faded.
        float f = PolarMusicLaw.ease(1.0f, 0.0f, 80.0);
        assertTrue(f < 0.03f, "after ~4 s the music should be essentially silent, was " + f);
        // ...and after 1 s (one tau) it should be well underway but not yet gone.
        float oneSec = PolarMusicLaw.ease(1.0f, 0.0f, 20.0);
        assertTrue(oneSec > 0.2f && oneSec < 0.5f, "after ~1 s (one tau) ~37% remains, was " + oneSec);
    }

    @Test
    void easeFadesBackInSymmetrically() {
        float f = PolarMusicLaw.ease(0.0f, 1.0f, 80.0);
        assertTrue(f > 0.97f, "music fades back in over ~4 s, was " + f);
    }

    // --- degenerate inputs degrade to FULL (never silence) ---

    @Test
    void nanLatitudeDegradesToFull() {
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.latitudeTarget01(Double.NaN), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.musicFactor01(true, Double.NaN, false, 0.0f, 1.0), EPS);
    }

    @Test
    void nanDeltaOrPrevDegradesToFull() {
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.ease(0.0f, 0.0f, Double.NaN), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.ease(Float.NaN, 0.0f, 1.0), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.ease(0.0f, Float.NaN, 1.0), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.ease(0.0f, 0.0f, -5.0), EPS);
    }

    @Test
    void nanPrevInCombinedTreatedAsFull() {
        // Degenerate prior factor is treated as full before easing, so it eases DOWN from 1.0 (never a jump to 0).
        float f = PolarMusicLaw.musicFactor01(true, 90.0, false, Float.NaN, 1.0);
        assertTrue(f < 1.0f && f > 0.9f, "eases down from full, was " + f);
    }

    // --- flag-off pin ---

    @Test
    void flagOffPinsFullRegardlessOfLatitude() {
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.musicFactor01(false, 90.0, false, 0.0f, 1.0), EPS);
        assertEquals(PolarMusicLaw.FULL, PolarMusicLaw.musicFactor01(false, 88.0, false, 0.5f, 100.0), EPS);
    }
}
