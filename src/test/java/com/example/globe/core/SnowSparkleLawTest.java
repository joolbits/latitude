package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link SnowSparkleLaw} -- the GLINT v4 (S21b) snow-sparkle budget law: the glint band
 * ramp (75->76), the ambient-snowfall crossfade (80->82) that hands the snowfield off from glint to falling
 * snow, the storm/blizzard calm-gate, and the fixed per-spawn-tick CLUSTER budget (NaN-safe, no state).
 */
class SnowSparkleLawTest {

    private static final double EPS = 1e-9;

    // ---- the band constants (GLINT v4 moved the glint BELOW the snowfall) ----

    @Test
    void bandConstantsAreTheGlintV4Values() {
        assertEquals(75.0, SnowSparkleLaw.ONSET_DEG, EPS);          // glint rises from 75
        assertEquals(76.0, SnowSparkleLaw.FULL_DEG, EPS);           // full by 76
        assertEquals(PolarHazardWindow.AMBIENT_ONSET_DEG, SnowSparkleLaw.SNOWFALL_ONSET_DEG, EPS); // 80 (snow onset)
        assertEquals(82.0, SnowSparkleLaw.SNOWFALL_FULL_DEG, EPS);  // glint gone by 82 (== the Barrens onset)
    }

    // ---- the glint band ramp (75 -> 76, then held at 1; the crossfade owns the upper edge) ----

    @Test
    void bandRampRisesFrom75ToFullAt76ThenHolds() {
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(74.0), EPS);    // below onset
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(75.0), EPS);    // at onset: still 0
        assertEquals(0.5, SnowSparkleLaw.bandRamp01(75.5), EPS);    // smoothstep midpoint
        assertEquals(1.0, SnowSparkleLaw.bandRamp01(76.0), EPS);    // full by 76
        assertEquals(1.0, SnowSparkleLaw.bandRamp01(79.0), EPS);    // held full
        assertEquals(1.0, SnowSparkleLaw.bandRamp01(85.0), EPS);    // ramp never falls (snowfall crossfade cuts it)
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(Double.NaN), EPS); // NaN-safe
    }

    // ---- the ambient-snowfall fade-in (80 -> 82) ----

    @Test
    void snowfallRampFadesInFrom80ToFullAt82() {
        assertEquals(0.0, SnowSparkleLaw.snowfallRamp01(79.0), EPS);  // below the snow onset
        assertEquals(0.0, SnowSparkleLaw.snowfallRamp01(80.0), EPS);  // at the snow onset: still 0
        assertEquals(0.5, SnowSparkleLaw.snowfallRamp01(81.0), EPS);  // smoothstep midpoint
        assertEquals(1.0, SnowSparkleLaw.snowfallRamp01(82.0), EPS);  // full snow by 82
        assertEquals(1.0, SnowSparkleLaw.snowfallRamp01(85.0), EPS);  // held full
        assertEquals(1.0, SnowSparkleLaw.snowfallRamp01(Double.NaN), EPS); // NaN -> full snow (conservative)
    }

    // ---- the crossfade: glint fades OUT exactly as snow fades IN (never both at full) ----

    @Test
    void glintWeightCrossfadesAgainstSnowfall() {
        assertEquals(0.0, SnowSparkleLaw.glintWeight(74.0), EPS);   // below the glint band
        assertEquals(1.0, SnowSparkleLaw.glintWeight(76.0), EPS);   // full glint, no snow
        assertEquals(1.0, SnowSparkleLaw.glintWeight(79.0), EPS);   // held across the calm 76-80 band
        assertEquals(1.0, SnowSparkleLaw.glintWeight(80.0), EPS);   // still full at the snow onset...
        assertEquals(0.5, SnowSparkleLaw.glintWeight(81.0), EPS);   // ...crossfading out as the snow rises
        assertEquals(0.0, SnowSparkleLaw.glintWeight(82.0), EPS);   // gone: the snow has fully taken over
        assertEquals(0.0, SnowSparkleLaw.glintWeight(85.0), EPS);   // deep snow: nothing
    }

    @Test
    void glintAndSnowfallHandOffCleanlyAcrossTheCrossfade() {
        // The structural fix for "two competing effects": across the 80->82 crossfade the band ramp is at 1,
        // so glintWeight == 1 - snowfallRamp01 -> the two ALWAYS sum to exactly 1 (a pure handoff, never both
        // at full at once).
        for (double deg : new double[] {80.0, 80.5, 81.0, 81.5, 82.0}) {
            assertEquals(1.0, SnowSparkleLaw.glintWeight(deg) + SnowSparkleLaw.snowfallRamp01(deg), EPS,
                    "glint + snowfall must hand off (sum 1) at " + deg);
        }
    }

    // ---- the calm gate ----

    @Test
    void calmFactorFallsWithStorm() {
        assertEquals(1.0, SnowSparkleLaw.calmFactor01(0.0), EPS);   // dead calm -> full
        assertEquals(0.5, SnowSparkleLaw.calmFactor01(0.5), EPS);
        assertEquals(0.0, SnowSparkleLaw.calmFactor01(1.0), EPS);   // full storm -> none
        assertEquals(0.0, SnowSparkleLaw.calmFactor01(Double.NaN), EPS); // NaN -> fully stormy -> none
    }

    // ---- the cluster budget ----

    @Test
    void sparkleBudgetIsGlintWeightTimesCalmTimesPeak() {
        assertEquals(4, SnowSparkleLaw.sparkleBudget(76.0, 0.0, 4)); // full glint, dead calm
        assertEquals(4, SnowSparkleLaw.sparkleBudget(79.0, 0.0, 4)); // full band
        assertEquals(4, SnowSparkleLaw.sparkleBudget(80.0, 0.0, 4)); // still full at the snow onset
        assertEquals(2, SnowSparkleLaw.sparkleBudget(81.0, 0.0, 4)); // mid crossfade -> round(0.5*4)
        assertEquals(0, SnowSparkleLaw.sparkleBudget(82.0, 0.0, 4)); // gone: snow taken over
        assertEquals(0, SnowSparkleLaw.sparkleBudget(74.0, 0.0, 4)); // below the glint band
        assertEquals(2, SnowSparkleLaw.sparkleBudget(75.5, 0.0, 4)); // half band-ramp -> round(0.5*4)
        assertEquals(0, SnowSparkleLaw.sparkleBudget(76.0, 1.0, 4)); // full storm snuffs it
    }

    @Test
    void sparkleGoneOnceSnowHasTakenOver() {
        // The structural fix: at/above 82 the ambient snow owns the snowfield, so no glint competes with it.
        assertEquals(0, SnowSparkleLaw.sparkleBudget(82.0, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(84.0, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(88.0, 0.0, 4));
        // ...and it is alive across the calm sub-snow band.
        assertTrue(SnowSparkleLaw.sparkleBudget(78.0, 0.0, 4) > 0, "glint alive in the calm 76-80 band");
    }

    @Test
    void sparkleBudgetNonPositivePeakAndNaNSafe() {
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, 0.0, 0));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, 0.0, -3));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(Double.NaN, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, Double.NaN, 4)); // NaN storm -> calm 0 -> 0
        // History: 1 -> 3 (S17c(i)) -> 4 (S19b density-up) -> 2 (GLINT v5, owner flight TEST 113 2026-07-19:
        // WAX_OFF -> bright white FIREWORK + twin cluster retired, so the peak halves -- ~10 sparks/s, not ~40).
        assertEquals(2, SnowSparkleLaw.DEFAULT_PEAK_BUDGET, "GLINT v5 de-purple peak");
    }
}
