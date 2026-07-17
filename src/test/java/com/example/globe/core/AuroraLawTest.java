package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link AuroraLaw} -- the S13(a) aurora VISUAL LAW. Pins the gate (day / midnight-sun /
 * storm / equatorward all zero), the latitude ramp, the deterministic world-clock motion, the colour/alpha
 * profile, and NaN-safety.
 */
class AuroraLawTest {

    private static final double EPS = 1e-9;

    // ---- latitude ramp ----

    @Test
    void latitudeRampIsZeroEquatorwardAndFullDeepPolar() {
        assertEquals(0.0, AuroraLaw.latitudeRamp01(0.0), EPS);
        assertEquals(0.0, AuroraLaw.latitudeRamp01(64.9), EPS);
        assertEquals(0.0, AuroraLaw.latitudeRamp01(AuroraLaw.LAT_ONSET_DEG), EPS); // at onset: still 0
        assertEquals(1.0, AuroraLaw.latitudeRamp01(AuroraLaw.LAT_FULL_DEG), EPS);  // at full: 1
        assertEquals(1.0, AuroraLaw.latitudeRamp01(88.0), EPS);
        double mid = AuroraLaw.latitudeRamp01(68.5); // between 65 and 72
        assertTrue(mid > 0.0 && mid < 1.0, "ramp interior in (0,1): " + mid);
        // monotonic non-decreasing across the ramp
        assertTrue(AuroraLaw.latitudeRamp01(67.0) <= AuroraLaw.latitudeRamp01(70.0));
    }

    // ---- the dark-sky gate (delegates to SolarSkyMood.polarNightGloom01) ----

    @Test
    void darkSkyZeroWhenSunUpFullWhenDeepBelow() {
        assertEquals(0.0, AuroraLaw.darkSky01(5.0), EPS);   // daylight -> 0
        assertEquals(0.0, AuroraLaw.darkSky01(0.0), EPS);   // exactly the horizon -> 0
        assertEquals(0.0, AuroraLaw.darkSky01(0.5), EPS);   // midnight sun (never dips) -> 0
        assertEquals(1.0, AuroraLaw.darkSky01(-20.0), EPS); // deep polar night -> full
        assertEquals(0.5, AuroraLaw.darkSky01(-6.0), 1e-6); // halfway down the 12-deg ramp
    }

    // ---- the master gate (product) ----

    @Test
    void intensityZeroUnderDaylight() {
        // Deep-polar latitude, clear sky, but the sun is UP -> aurora absent (covers ordinary day).
        assertEquals(0.0, AuroraLaw.intensity01(85.0, 5.0, 0.0), EPS);
    }

    @Test
    void intensityZeroUnderMidnightSun() {
        // Midnight sun = the sun never sets = elevation stays positive all "night" -> darkSky 0 -> aurora 0.
        assertEquals(0.0, AuroraLaw.intensity01(85.0, 2.0, 0.0), EPS);
    }

    @Test
    void intensityZeroUnderStorm() {
        // Deep polar night, full latitude, but full storm/overcast suppresses -> 0 (the 85+ whiteout owns it).
        assertEquals(0.0, AuroraLaw.intensity01(85.0, -20.0, 1.0), EPS);
    }

    @Test
    void intensityZeroEquatorward() {
        // Dark, calm, but below the latitude onset -> 0.
        assertEquals(0.0, AuroraLaw.intensity01(60.0, -20.0, 0.0), EPS);
    }

    @Test
    void intensityFullInDeepPolarNightClearAndCalm() {
        assertEquals(1.0, AuroraLaw.intensity01(80.0, -20.0, 0.0), EPS);
    }

    @Test
    void intensityIsTheProductOfTheThreeFactors() {
        double lat = 68.0;
        double elev = -6.0;    // darkSky 0.5
        double expected = AuroraLaw.latitudeRamp01(lat) * 0.5 * (1.0 - 0.0);
        assertEquals(expected, AuroraLaw.intensity01(lat, elev, 0.0), 1e-6);
        // a partial storm halves it
        assertEquals(expected * 0.5, AuroraLaw.intensity01(lat, elev, 0.5), 1e-6);
    }

    @Test
    void intensityNaNSafe() {
        assertEquals(0.0, AuroraLaw.intensity01(Double.NaN, -20.0, 0.0), EPS);
        assertEquals(0.0, AuroraLaw.intensity01(80.0, Double.NaN, 0.0), EPS);
        assertEquals(0.0, AuroraLaw.intensity01(80.0, -20.0, Double.NaN), EPS); // NaN storm -> fully stormy -> 0
    }

    // ---- deterministic world-clock motion ----

    @Test
    void motionIsDeterministicOverTheWorldClock() {
        // Same clock in -> same phases out (pure, no wall-clock accumulator).
        assertEquals(AuroraLaw.verticalWaverBlocks(1234.0, 1, 0.4),
                AuroraLaw.verticalWaverBlocks(1234.0, 1, 0.4), EPS);
        assertEquals(AuroraLaw.azimuthDriftRad(5000.0), AuroraLaw.azimuthDriftRad(5000.0), EPS);
        // ...and it actually MOVES over time (two well-separated ticks differ).
        assertNotEquals(AuroraLaw.verticalWaverBlocks(0.0, 0, 0.5),
                AuroraLaw.verticalWaverBlocks(400.0, 0, 0.5), "the waver must advance with the world clock");
    }

    @Test
    void waverBoundedByAmplitudeAndNaNSafe() {
        for (int b = 0; b < AuroraLaw.BAND_COUNT; b++) {
            for (int k = 0; k <= 20; k++) {
                double w = AuroraLaw.verticalWaverBlocks(k * 137.0, b, k / 20.0);
                assertTrue(Math.abs(w) <= AuroraLaw.WAVER_AMP_BLOCKS + 1e-9, "waver within amplitude: " + w);
            }
        }
        assertEquals(0.0, AuroraLaw.verticalWaverBlocks(Double.NaN, 0, 0.5), EPS);
        assertEquals(0.0, AuroraLaw.verticalWaverBlocks(100.0, 0, Double.NaN), EPS);
        assertEquals(0.0, AuroraLaw.azimuthDriftRad(Double.NaN), EPS);
    }

    // ---- shape + colour profile ----

    @Test
    void bandCountInDesignRange() {
        assertTrue(AuroraLaw.BAND_COUNT >= 2 && AuroraLaw.BAND_COUNT <= 4, "design says 2-4 bands");
    }

    @Test
    void colourIsGreenTealCoreToPurpleFringe() {
        assertEquals(AuroraLaw.CORE_RGB, AuroraLaw.levelColorRgb(0));                 // bright lower edge = core
        assertEquals(AuroraLaw.FRINGE_RGB, AuroraLaw.levelColorRgb(AuroraLaw.LEVELS - 1)); // top = purple fringe
        // core is green-teal: green channel dominant; fringe is purple: red+blue over green.
        int core = AuroraLaw.CORE_RGB;
        assertTrue(((core >> 8) & 0xFF) > (core & 0xFF) && ((core >> 8) & 0xFF) > ((core >> 16) & 0xFF),
                "core green channel dominant");
        int fringe = AuroraLaw.FRINGE_RGB;
        assertTrue(((fringe >> 16) & 0xFF) > ((fringe >> 8) & 0xFF) && (fringe & 0xFF) > ((fringe >> 8) & 0xFF),
                "fringe is purple (R,B over G)");
        // alpha profile brightest at the base, fading up
        assertTrue(AuroraLaw.levelAlpha01(0) > AuroraLaw.levelAlpha01(AuroraLaw.LEVELS - 1));
    }

    @Test
    void arcEdgeFadeZeroAtEndsFullInMiddle() {
        assertEquals(0.0, AuroraLaw.arcEdgeFade01(0.0), EPS);
        assertEquals(0.0, AuroraLaw.arcEdgeFade01(1.0), EPS);
        assertEquals(1.0, AuroraLaw.arcEdgeFade01(0.5), EPS);
        assertEquals(0.0, AuroraLaw.arcEdgeFade01(Double.NaN), EPS);
    }

    @Test
    void vertexAlphaFollowsIntensityAndEdges() {
        // Zero master intensity -> fully transparent vertex.
        assertEquals(0, (AuroraLaw.vertexArgb(0, 0.5, 0.0) >>> 24) & 0xFF);
        // Zero at the arc ends regardless of intensity.
        assertEquals(0, (AuroraLaw.vertexArgb(0, 0.0, 1.0) >>> 24) & 0xFF);
        // Interior, full intensity, bright base level -> a visible alpha.
        int a = (AuroraLaw.vertexArgb(0, 0.5, 1.0) >>> 24) & 0xFF;
        assertTrue(a > 0, "interior base vertex visible: " + a);
        // NaN intensity -> transparent (never a NaN vertex).
        assertEquals(0, (AuroraLaw.vertexArgb(0, 0.5, Double.NaN) >>> 24) & 0xFF);
    }
}
