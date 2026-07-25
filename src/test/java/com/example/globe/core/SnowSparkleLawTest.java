package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link SnowSparkleLaw} -- the GLINT v4 (S21b) snow-sparkle budget law PLUS the GLINT CLOCK
 * (S24): the glint band ramp (75->76), the ambient-snowfall crossfade (80->82), the storm/blizzard calm-gate,
 * the fixed per-spawn-tick CLUSTER budget, and the S24 additions -- the noon-peaked clock-day curve (glints
 * become a DAYTIME phenomenon everywhere) and the polar-night / midnight-sun band EXTENSION (onset drops 75->60
 * so the clock signal exists wherever the day-bright/dark-sky confusion does). NaN-safe, no state.
 */
class SnowSparkleLawTest {

    private static final double EPS = 1e-9;
    /** MC clock at solar noon (glint pulse peak) / midnight (pulse zero) -- the two reference phases. */
    private static final double NOON = 6000.0;
    private static final double MIDNIGHT = 18000.0;

    // ---- the band constants (GLINT v4 moved the glint BELOW the snowfall) ----

    @Test
    void bandConstantsAreTheGlintV4Values() {
        assertEquals(75.0, SnowSparkleLaw.ONSET_DEG, EPS);          // glint rises from 75
        assertEquals(76.0, SnowSparkleLaw.FULL_DEG, EPS);           // full by 76
        assertEquals(1.0, SnowSparkleLaw.BAND_WIDTH_DEG, EPS);      // 1-deg smoothstep width
        assertEquals(60.0, SnowSparkleLaw.FUNCTIONAL_EXTENDED_ONSET_DEG, EPS); // S24 extended onset (== solar onset)
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
        // The single-arg ramp is exactly the un-extended (functionalBandActive=false) overload.
        assertEquals(SnowSparkleLaw.bandRamp01(75.5), SnowSparkleLaw.bandRamp01(75.5, false), EPS);
    }

    // ---- S24 BAND EXTENSION: in a polar-night / midnight-sun band the onset drops 75 -> 60 (same width) ----

    @Test
    void bandExtendsToSolarOnsetWhenFunctionalBandActive() {
        // Not in a functional band: onset stays 75, so 60-74 do not glint.
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(60.0, false), EPS);
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(65.0, false), EPS);
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(74.0, false), EPS);
        // In a functional band: onset drops to 60, full by 61 (the SAME 1-deg smoothstep width).
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(60.0, true), EPS);   // at the extended onset: still 0
        assertEquals(0.5, SnowSparkleLaw.bandRamp01(60.5, true), EPS);   // smoothstep midpoint of the extended ramp
        assertEquals(1.0, SnowSparkleLaw.bandRamp01(61.0, true), EPS);   // full by 61
        assertEquals(1.0, SnowSparkleLaw.bandRamp01(65.0, true), EPS);   // held full through the whole extended band
        assertEquals(1.0, SnowSparkleLaw.bandRamp01(78.0, true), EPS);   // ...and up into the original band
        assertEquals(0.0, SnowSparkleLaw.bandRamp01(Double.NaN, true), EPS); // NaN-safe
    }

    // ---- the ambient-snowfall fade-in (80 -> 82) -- UNCHANGED by the clock / extension ----

    @Test
    void snowfallRampFadesInFrom80ToFullAt82() {
        assertEquals(0.0, SnowSparkleLaw.snowfallRamp01(79.0), EPS);  // below the snow onset
        assertEquals(0.0, SnowSparkleLaw.snowfallRamp01(80.0), EPS);  // at the snow onset: still 0
        assertEquals(0.5, SnowSparkleLaw.snowfallRamp01(81.0), EPS);  // smoothstep midpoint
        assertEquals(1.0, SnowSparkleLaw.snowfallRamp01(82.0), EPS);  // full snow by 82
        assertEquals(1.0, SnowSparkleLaw.snowfallRamp01(85.0), EPS);  // held full
        assertEquals(1.0, SnowSparkleLaw.snowfallRamp01(Double.NaN), EPS); // NaN -> full snow (conservative)
    }

    // ---- S24 CLOCK CURVE: noon-peaked pulse over the 24000-tick day ----

    @Test
    void clockCurvePeaksAtNoonZeroAtClockNight() {
        assertEquals(1.0, SnowSparkleLaw.clockDayCurve01(6000.0), EPS);    // noon peak
        assertEquals(1.0, SnowSparkleLaw.clockDayCurve01(1000.0), EPS);    // dawn complete -> plateau
        assertEquals(1.0, SnowSparkleLaw.clockDayCurve01(11000.0), EPS);   // dusk onset -> still full
        assertEquals(0.0, SnowSparkleLaw.clockDayCurve01(13000.0), EPS);   // dusk complete -> clock-night
        assertEquals(0.0, SnowSparkleLaw.clockDayCurve01(18000.0), EPS);   // midnight
        assertEquals(0.0, SnowSparkleLaw.clockDayCurve01(23000.0), EPS);   // pre-dawn -> still clock-night
        assertEquals(0.0, SnowSparkleLaw.clockDayCurve01(Double.NaN), EPS); // NaN -> clock-night (conservative)
    }

    @Test
    void clockCurveTwilightIsSmoothMonotoneAndSymmetric() {
        // Dawn (tick 0, 6am) and dusk (tick 12000, 6pm) are mirror images about noon -> equal, at the smoothstep mid.
        assertEquals(0.5, SnowSparkleLaw.clockDayCurve01(0.0), EPS);
        assertEquals(SnowSparkleLaw.clockDayCurve01(0.0), SnowSparkleLaw.clockDayCurve01(12000.0), EPS);
        // Dawn ramp monotone UP (night edge 23000 -> plateau 1000, through tick 0).
        double a = SnowSparkleLaw.clockDayCurve01(23000.0); // 0
        double b = SnowSparkleLaw.clockDayCurve01(23500.0);
        double c = SnowSparkleLaw.clockDayCurve01(0.0);     // 0.5
        double d = SnowSparkleLaw.clockDayCurve01(500.0);
        double e = SnowSparkleLaw.clockDayCurve01(1000.0);  // 1
        assertTrue(a <= b && b <= c && c <= d && d <= e, "dawn twilight ramp monotone up");
        // Dusk ramp monotone DOWN (plateau 11000 -> night 13000).
        double p = SnowSparkleLaw.clockDayCurve01(11000.0); // 1
        double q = SnowSparkleLaw.clockDayCurve01(11500.0);
        double r = SnowSparkleLaw.clockDayCurve01(12000.0); // 0.5
        double s = SnowSparkleLaw.clockDayCurve01(12500.0);
        double t = SnowSparkleLaw.clockDayCurve01(13000.0); // 0
        assertTrue(p >= q && q >= r && r >= s && s >= t, "dusk twilight ramp monotone down");
        // Bounded 0..1 across the whole day.
        for (double tick = 0.0; tick < 24000.0; tick += 200.0) {
            double v = SnowSparkleLaw.clockDayCurve01(tick);
            assertTrue(v >= 0.0 && v <= 1.0, "clock curve bounded at tick " + tick);
        }
    }

    @Test
    void clockCurveFoldsRawAndNegativeClocks() {
        // A raw clock (many days in) folds mod 24000: noon + 2 whole days is still noon.
        assertEquals(1.0, SnowSparkleLaw.clockDayCurve01(6000.0 + 2 * 24000.0), EPS);
        assertEquals(0.0, SnowSparkleLaw.clockDayCurve01(18000.0 + 5 * 24000.0), EPS);
        // A negative (/time set rewind) is still well-defined: -18000 folds to +6000 (noon).
        assertEquals(1.0, SnowSparkleLaw.clockDayCurve01(-18000.0), EPS);
    }

    // ---- the crossfade + clock: glint fades OUT as snow fades IN, and pulses with the clock ----

    @Test
    void glintWeightCrossfadesAgainstSnowfallAtNoon() {
        // At noon (clock 1), not in a functional band: the classic v4 crossfade shape is preserved.
        assertEquals(0.0, SnowSparkleLaw.glintWeight(74.0, false, NOON), EPS);   // below the glint band
        assertEquals(1.0, SnowSparkleLaw.glintWeight(76.0, false, NOON), EPS);   // full glint, no snow
        assertEquals(1.0, SnowSparkleLaw.glintWeight(79.0, false, NOON), EPS);   // held across the calm 76-80 band
        assertEquals(1.0, SnowSparkleLaw.glintWeight(80.0, false, NOON), EPS);   // still full at the snow onset...
        assertEquals(0.5, SnowSparkleLaw.glintWeight(81.0, false, NOON), EPS);   // ...crossfading out as snow rises
        assertEquals(0.0, SnowSparkleLaw.glintWeight(82.0, false, NOON), EPS);   // gone: snow has fully taken over
        assertEquals(0.0, SnowSparkleLaw.glintWeight(85.0, false, NOON), EPS);   // deep snow: nothing
    }

    @Test
    void glintAndSnowfallHandOffCleanlyAcrossTheCrossfadeAtNoon() {
        // At noon (clock 1) the band ramp is 1 across 80->82, so glintWeight == 1 - snowfallRamp01: the two ALWAYS
        // sum to exactly 1 (a pure handoff, never both at full) -- the clock does not disturb the crossfade at noon.
        for (double deg : new double[] {80.0, 80.5, 81.0, 81.5, 82.0}) {
            assertEquals(1.0, SnowSparkleLaw.glintWeight(deg, false, NOON) + SnowSparkleLaw.snowfallRamp01(deg), EPS,
                    "glint + snowfall must hand off (sum 1) at " + deg);
        }
    }

    @Test
    void normalBandIsNowClockGated() {
        // The 75-82 band still glints at noon...
        assertTrue(SnowSparkleLaw.glintWeight(78.0, false, NOON) > 0.0, "78 deg glints at noon");
        // ...but is SHUTTERED at clock-night everywhere (the owner's midnight-glint fix, kept consistent).
        assertEquals(0.0, SnowSparkleLaw.glintWeight(78.0, false, MIDNIGHT), EPS);
        assertEquals(0.0, SnowSparkleLaw.glintWeight(78.0, true, MIDNIGHT), EPS);
    }

    @Test
    void polarNightBandGlintsAtNoonNotAtMidnight() {
        // 65 deg polar-night country: at NOON it glints (band extended to 60, clock 1); at MIDNIGHT it does not.
        assertTrue(SnowSparkleLaw.glintWeight(65.0, true, NOON) > 0.0, "extended band + noon -> glint");
        assertEquals(0.0, SnowSparkleLaw.glintWeight(65.0, true, MIDNIGHT), EPS); // clock-night kills the pulse
        // Without a functional band (tilt off / not in-band), 65 deg is below the normal 75 onset -> no glint even at noon.
        assertEquals(0.0, SnowSparkleLaw.glintWeight(65.0, false, NOON), EPS);
    }

    @Test
    void midnightSunSharesTheExactClockBehaviour() {
        // functionalBandActive is band-agnostic: midnight sun extends + clock-gates identically to polar night
        // (the caller passes a single "am I in an around-the-clock band" boolean for either regime).
        assertTrue(SnowSparkleLaw.glintWeight(65.0, true, NOON) > 0.0);
        assertEquals(0.0, SnowSparkleLaw.glintWeight(65.0, true, MIDNIGHT), EPS);
    }

    @Test
    void snowfallCrossfadeUnchangedByExtensionAndClock() {
        // 82+ blizzard country: the snowfall crossfade has fully taken over -> no glint, regardless of band-ext or clock.
        assertEquals(0.0, SnowSparkleLaw.glintWeight(82.0, false, NOON), EPS);
        assertEquals(0.0, SnowSparkleLaw.glintWeight(82.0, true, NOON), EPS);   // the extension does NOT revive it
        assertEquals(0.0, SnowSparkleLaw.glintWeight(85.0, true, NOON), EPS);
        // and the snowfall ramp itself is byte-for-byte untouched.
        assertEquals(0.5, SnowSparkleLaw.snowfallRamp01(81.0), EPS);
        assertEquals(1.0, SnowSparkleLaw.snowfallRamp01(82.0), EPS);
    }

    @Test
    void glintWeightIsBandTimesWindowTimesClock() {
        // The multiplier composition, checked across latitudes x clock phases: glintWeight == band x window x clock.
        for (double deg : new double[] {65.0, 78.0, 81.0, 82.0}) {
            for (double tick : new double[] {0.0, 3000.0, 6000.0, 12000.0, 18000.0, 21000.0}) {
                double band = SnowSparkleLaw.bandRamp01(deg, true);
                double window = 1.0 - SnowSparkleLaw.snowfallRamp01(deg);
                double clock = SnowSparkleLaw.clockDayCurve01(tick);
                assertEquals(band * window * clock, SnowSparkleLaw.glintWeight(deg, true, tick), EPS,
                        "glintWeight == band x window x clock at " + deg + " deg, tick " + tick);
            }
        }
    }

    // ---- the calm gate (unchanged) ----

    @Test
    void calmFactorFallsWithStorm() {
        assertEquals(1.0, SnowSparkleLaw.calmFactor01(0.0), EPS);   // dead calm -> full
        assertEquals(0.5, SnowSparkleLaw.calmFactor01(0.5), EPS);
        assertEquals(0.0, SnowSparkleLaw.calmFactor01(1.0), EPS);   // full storm -> none
        assertEquals(0.0, SnowSparkleLaw.calmFactor01(Double.NaN), EPS); // NaN -> fully stormy -> none
    }

    // ---- the cluster budget ----

    @Test
    void sparkleBudgetIsGlintWeightTimesCalmTimesPeakAtNoon() {
        assertEquals(4, SnowSparkleLaw.sparkleBudget(76.0, false, NOON, 0.0, 4)); // full glint, dead calm, noon
        assertEquals(4, SnowSparkleLaw.sparkleBudget(79.0, false, NOON, 0.0, 4)); // full band
        assertEquals(4, SnowSparkleLaw.sparkleBudget(80.0, false, NOON, 0.0, 4)); // still full at the snow onset
        assertEquals(2, SnowSparkleLaw.sparkleBudget(81.0, false, NOON, 0.0, 4)); // mid crossfade -> round(0.5*4)
        assertEquals(0, SnowSparkleLaw.sparkleBudget(82.0, false, NOON, 0.0, 4)); // gone: snow taken over
        assertEquals(0, SnowSparkleLaw.sparkleBudget(74.0, false, NOON, 0.0, 4)); // below the glint band
        assertEquals(2, SnowSparkleLaw.sparkleBudget(75.5, false, NOON, 0.0, 4)); // half band-ramp -> round(0.5*4)
        assertEquals(0, SnowSparkleLaw.sparkleBudget(76.0, false, NOON, 1.0, 4)); // full storm snuffs it
    }

    @Test
    void sparkleBudgetComposesClockCalmCrossfadeAndExtension() {
        // Full band + dead calm + noon => full peak; the SAME point at clock-night => 0 (the clock scales the whole budget).
        assertEquals(4, SnowSparkleLaw.sparkleBudget(78.0, false, NOON, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, false, MIDNIGHT, 0.0, 4));
        // Extended polar-night band at 65 deg, noon, calm => full peak (the day/night tell in the confusion zone).
        assertEquals(4, SnowSparkleLaw.sparkleBudget(65.0, true, NOON, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(65.0, true, MIDNIGHT, 0.0, 4)); // dark clock -> nothing
        assertEquals(0, SnowSparkleLaw.sparkleBudget(65.0, false, NOON, 0.0, 4));    // no band -> below 75 -> nothing
        // Storm still snuffs it even at noon in-band.
        assertEquals(0, SnowSparkleLaw.sparkleBudget(65.0, true, NOON, 1.0, 4));
        // Compound scaling: 81 deg (window 0.5) at tick 0 (clock 0.5) => round(0.5*0.5*4) = 1.
        assertEquals(1, SnowSparkleLaw.sparkleBudget(81.0, false, 0.0, 0.0, 4));
    }

    @Test
    void sparkleGoneOnceSnowHasTakenOver() {
        // The structural fix: at/above 82 the ambient snow owns the snowfield, so no glint competes with it.
        assertEquals(0, SnowSparkleLaw.sparkleBudget(82.0, false, NOON, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(84.0, false, NOON, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(88.0, false, NOON, 0.0, 4));
        // ...and it is alive across the calm sub-snow band at noon.
        assertTrue(SnowSparkleLaw.sparkleBudget(78.0, false, NOON, 0.0, 4) > 0, "glint alive in the calm 76-80 band at noon");
    }

    @Test
    void sparkleBudgetNonPositivePeakAndNaNSafe() {
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, false, NOON, 0.0, 0));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, false, NOON, 0.0, -3));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(Double.NaN, false, NOON, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, false, NOON, Double.NaN, 4)); // NaN storm -> calm 0 -> 0
        assertEquals(0, SnowSparkleLaw.sparkleBudget(78.0, false, Double.NaN, 0.0, 4));  // NaN clock -> curve 0 -> 0
        // History: 1 -> 3 (S17c(i)) -> 4 (S19b density-up) -> 2 (GLINT v5, owner flight TEST 113 2026-07-19:
        // WAX_OFF -> bright white FIREWORK + twin cluster retired, so the peak halves -- ~10 sparks/s, not ~40).
        assertEquals(2, SnowSparkleLaw.DEFAULT_PEAK_BUDGET, "GLINT v5 de-purple peak");
    }
}
