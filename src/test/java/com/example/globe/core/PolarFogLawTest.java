package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarFogLaw} -- the S10(b)(c) unified polar fog law (TEST 99 round):
 * <ul>
 *   <li>the S10b cap table (light haze 80 / heavy 40 blocks by 88 / near-total whiteout ~4 blocks at the
 *       line), its piecewise-linear interpolation, seam below 80, hold beyond 90, monotonicity,</li>
 *   <li>START derivation ({@code max(2, 0.30*end)}, always under END),</li>
 *   <li>the whiteout top-coat envelope (0 at/below 86, 1 at the line, monotone),</li>
 *   <li>the S10c early-overcast floor (0 at 81 -> 0.35 at 85, held; never touches the full-storm ramp),</li>
 *   <li>the S10c gust law (bounds [1, 1.5], latitude-depth scaling, period, surge shape).</li>
 * </ul>
 */
class PolarFogLawTest {

    // ---- the S10b fog cap table ----

    @Test
    void fogCapTablePinnedAtItsRows() {
        assertEquals(512.0f, PolarFogLaw.fogEndCapBlocks(80.0), 1e-4, "seam row: above realistic view distances");
        assertEquals(140.0f, PolarFogLaw.fogEndCapBlocks(82.0), 1e-4);
        assertEquals(100.0f, PolarFogLaw.fogEndCapBlocks(84.0), 1e-4);
        assertEquals(70.0f, PolarFogLaw.fogEndCapBlocks(86.0), 1e-4);
        assertEquals(40.0f, PolarFogLaw.fogEndCapBlocks(88.0), 1e-4, "heavy by 88 (the owner's anchor)");
        assertEquals(24.0f, PolarFogLaw.fogEndCapBlocks(89.0), 1e-4);
        assertEquals(12.0f, PolarFogLaw.fogEndCapBlocks(89.5), 1e-4, "the near-total whiteout begins");
        assertEquals(4.0f, PolarFogLaw.fogEndCapBlocks(90.0), 1e-4, "a few blocks at the line -- the fog IS the wall");
    }

    @Test
    void fogCapSilentBelowOnsetAndHeldBeyondPole() {
        assertEquals(Float.MAX_VALUE, PolarFogLaw.fogEndCapBlocks(79.9), "below 80: vanilla untouched");
        assertEquals(Float.MAX_VALUE, PolarFogLaw.fogEndCapBlocks(0.0));
        assertEquals(Float.MAX_VALUE, PolarFogLaw.fogEndCapBlocks(Double.NaN), "NaN reads as far");
        // Beyond the line (Wide-world survivor pressed past 90): the full whiteout holds.
        assertEquals(4.0f, PolarFogLaw.fogEndCapBlocks(90.5), 1e-4);
    }

    @Test
    void fogCapInterpolatesLinearlyBetweenRows() {
        // Midpoint of the {89.0, 24} -> {89.5, 12} segment.
        assertEquals(18.0f, PolarFogLaw.fogEndCapBlocks(89.25), 1e-3);
        // Midpoint of the {80, 512} -> {82, 140} segment.
        assertEquals(326.0f, PolarFogLaw.fogEndCapBlocks(81.0), 1e-3);
    }

    @Test
    void fogCapIsMonotoneNonIncreasingAcrossTheWindow() {
        float prev = Float.MAX_VALUE;
        for (double deg = 80.0; deg <= 90.0; deg += 0.25) {
            float cap = PolarFogLaw.fogEndCapBlocks(deg);
            assertTrue(cap <= prev, "cap must never loosen poleward (deg " + deg + ")");
            prev = cap;
        }
    }

    @Test
    void fogStartScalesWithEndAndFloorsAtTwo() {
        // S11(g): fraction 0.30 -> 0.08 — START hugs the camera so the [START, END] span is nearly the whole
        // gradient and terrain fades through haze instead of popping at the cap.
        assertEquals(3.2f, PolarFogLaw.fogStartBlocks(40.0f), 1e-4, "0.08 x end (the softer-gradient dial)");
        assertEquals(2.0f, PolarFogLaw.fogStartBlocks(4.0f), 1e-4, "floored at 2 so your own hands stay clear");
        assertEquals(2.0f, PolarFogLaw.fogStartBlocks(12.0f), 1e-4, "0.08 x 12 = 0.96 -> the 2-block floor binds");
        assertTrue(PolarFogLaw.fogStartBlocks(40.0f) < 40.0f, "START under END");
    }

    // ---- S15(c) horizon-gloom reach ----

    @Test
    void horizonGloomReachSeamFreeAtOnsetFullByEightyFive() {
        // S15(c): the fog-colour night gloom eases in from the 80-deg onset (seam-free -- the fog is untouched
        // below 80) to FULL by 85, then holds through the pole, so the horizon fog matches the gloomed sky.
        assertEquals(0.0f, PolarFogLaw.horizonGloomReach01(80.0), 1e-6, "seam-free at the fog onset");
        assertEquals(0.0f, PolarFogLaw.horizonGloomReach01(79.0), 1e-6, "silent below onset");
        assertEquals(0.0f, PolarFogLaw.horizonGloomReach01(Double.NaN), 1e-6, "NaN-safe");
        assertEquals(1.0f, PolarFogLaw.horizonGloomReach01(85.0), 1e-6, "full by 85 (well before the pole)");
        assertEquals(1.0f, PolarFogLaw.horizonGloomReach01(89.5), 1e-6, "held to the pole");
        assertEquals(1.0f, PolarFogLaw.horizonGloomReach01(90.5), 1e-6, "held beyond (Wide-world survivor)");
        // Interior in (0,1), monotone non-decreasing across the 80->85 ease-in.
        float prev = 0.0f;
        for (double d = 80.0; d <= 85.0; d += 0.25) {
            float v = PolarFogLaw.horizonGloomReach01(d);
            assertTrue(v >= prev, "reach must rise across the ease-in (deg " + d + ")");
            prev = v;
        }
        assertTrue(PolarFogLaw.horizonGloomReach01(82.5) > 0.0f && PolarFogLaw.horizonGloomReach01(82.5) < 1.0f,
                "mid ease-in strictly interior");
    }

    // ---- S11(f) -> S14(c): the fog's night/dusk darkening moved to SolarSkyMood.atmosphereTint (pinned by
    //      SolarSkyMoodTest); PolarFogLaw's duplicated NIGHT_FOG_* palette + nightFogDarkness01 were retired. ----

    // ---- whiteout top-coat ----

    @Test
    void whiteoutTopcoatEnvelope() {
        assertEquals(0.0f, PolarFogLaw.whiteoutTopcoat01(86.0), 1e-6, "quiet at/below 86 (depth fog carries mid-band)");
        assertEquals(0.0f, PolarFogLaw.whiteoutTopcoat01(80.0), 1e-6);
        assertEquals(0.35f, PolarFogLaw.whiteoutTopcoat01(88.0), 1e-4);
        assertEquals(0.6f, PolarFogLaw.whiteoutTopcoat01(89.0), 1e-4);
        assertEquals(0.85f, PolarFogLaw.whiteoutTopcoat01(89.5), 1e-4);
        assertEquals(1.0f, PolarFogLaw.whiteoutTopcoat01(90.0), 1e-6, "full engulfment at the line");
        assertEquals(1.0f, PolarFogLaw.whiteoutTopcoat01(90.4), 1e-6, "held beyond the line");
        // Monotone non-decreasing.
        float prev = 0.0f;
        for (double deg = 86.0; deg <= 90.0; deg += 0.25) {
            float v = PolarFogLaw.whiteoutTopcoat01(deg);
            assertTrue(v >= prev, "topcoat must never fade poleward (deg " + deg + ")");
            prev = v;
        }
    }

    // ---- S10c early overcast ----

    @Test
    void earlyOvercastRampsToItsFloorAndHolds() {
        assertEquals(0.0f, PolarFogLaw.earlyOvercast01(81.0), 1e-6, "silent at/below the 81 onset");
        assertEquals(0.0f, PolarFogLaw.earlyOvercast01(60.0), 1e-6);
        assertEquals(0.175f, PolarFogLaw.earlyOvercast01(83.0), 1e-4, "half way at 83");
        assertEquals(0.35f, PolarFogLaw.earlyOvercast01(85.0), 1e-4, "the floor at the storm-sky anchor");
        assertEquals(0.35f, PolarFogLaw.earlyOvercast01(89.0), 1e-4, "held poleward -- stormLevel overtakes it");
        // The floor never outruns the existing steep storm ramp's ceiling (max() keeps 85->87.5 authoritative):
        assertTrue(PolarFogLaw.earlyOvercast01(87.5) < PolarHazardWindow.stormLevel(87.5),
                "full storm at 87.5 stays owned by the flight-tested stormLevel ramp");
    }

    // ---- S10c gusts ----

    @Test
    void gustFactorBoundsAndDepthScaling() {
        // Below the depth onset: factor is EXACTLY 1 at any clock (the calm equatorward guarantee).
        for (long t = 0; t < PolarFogLaw.GUST_PERIOD_MS; t += 1750) {
            assertEquals(1.0, PolarFogLaw.gustFactor(t, 82.0), 1e-9, "no gusts at/below 82");
        }
        // Bounds at full depth: never below 1, never above 1 + GUST_MAX_BOOST (the ~1.5x ceiling).
        for (long t = 0; t < PolarFogLaw.GUST_PERIOD_MS; t += 500) {
            double f = PolarFogLaw.gustFactor(t, 90.0);
            assertTrue(f >= 1.0 && f <= 1.0 + PolarFogLaw.GUST_MAX_BOOST + 1e-9, "factor out of bounds: " + f);
        }
        // Depth scaling: the same clock instant gusts harder at 89 than at 85.
        long crest = PolarFogLaw.GUST_PERIOD_MS / 4; // sin peak
        assertTrue(PolarFogLaw.gustFactor(crest, 89.0) > PolarFogLaw.gustFactor(crest, 85.0));
        // Full-depth crest hits the ceiling.
        assertEquals(1.0 + PolarFogLaw.GUST_MAX_BOOST, PolarFogLaw.gustFactor(crest, 89.0), 1e-6);
    }

    @Test
    void gustWaveIsPeriodicAndSurgesNotHums() {
        long period = PolarFogLaw.GUST_PERIOD_MS;
        // Periodic: same phase, same wave.
        assertEquals(PolarFogLaw.gustWave01(1234L), PolarFogLaw.gustWave01(1234L + period), 1e-9);
        // Crest (sin peak at period/4) = 1; trough (3*period/4) = 0 -- full calm between surges.
        assertEquals(1.0, PolarFogLaw.gustWave01(period / 4), 1e-6);
        assertEquals(0.0, PolarFogLaw.gustWave01(3 * period / 4), 1e-6);
        // Squared shape: the mid-rise point sits BELOW linear (0.25 at the sin=0.5 phase), i.e. calm-biased --
        // the cycle reads as discrete gusts, not a constant hum.
        assertEquals(0.25, PolarFogLaw.gustWave01(0L), 1e-6, "phase 0 (sin=0 -> s=0.5) squares to 0.25");
        // Period sits inside the ordered 8-20 s window.
        assertTrue(period >= 8000L && period <= 20000L);
    }

    // ---- S17(a): entity fog culling ----

    @Test
    void cullEntityBeyondFogWall() {
        // Below the 80-deg fog onset: never cull (cap is MAX_VALUE), regardless of distance.
        assertFalse(PolarFogLaw.cullEntityBeyondFog(70.0, 100000.0));
        assertFalse(PolarFogLaw.cullEntityBeyondFog(79.9, 1.0e9));
        // At the 80-deg entry the cap is 512 blocks (beyond any render distance) -> seam-free, nothing culled
        // until the fog has genuinely closed in.
        assertFalse(PolarFogLaw.cullEntityBeyondFog(80.0, 400.0));
        assertTrue(PolarFogLaw.cullEntityBeyondFog(80.0,
                512.0f + PolarFogLaw.ENTITY_CULL_MARGIN_BLOCKS + 1.0));
        // Deep in the whiteout (88 deg, cap 40 blocks): an entity just inside the wall renders, one past the
        // wall + margin is culled.
        float cap88 = PolarFogLaw.fogEndCapBlocks(88.0);
        assertEquals(40.0f, cap88, 1e-4);
        assertFalse(PolarFogLaw.cullEntityBeyondFog(88.0, cap88 - 1.0));            // inside the wall: visible
        assertFalse(PolarFogLaw.cullEntityBeyondFog(88.0, cap88 + PolarFogLaw.ENTITY_CULL_MARGIN_BLOCKS - 0.1));
        assertTrue(PolarFogLaw.cullEntityBeyondFog(88.0, cap88 + PolarFogLaw.ENTITY_CULL_MARGIN_BLOCKS + 0.1)); // past it: culled
        // At the pole line (cap 4 blocks) almost everything beyond arm's reach is gone.
        assertTrue(PolarFogLaw.cullEntityBeyondFog(90.0, 20.0));
        // NaN latitude / distance are defensively "do not cull" (never hide an entity on a bad sample).
        assertFalse(PolarFogLaw.cullEntityBeyondFog(Double.NaN, 1000.0));
        assertFalse(PolarFogLaw.cullEntityBeyondFog(89.0, Double.NaN));
    }
}
