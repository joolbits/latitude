package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link SnowSparkleLaw} -- the S13(a) snow-sparkle budget law: the calm-band trapezoid
 * (80-85), the storm/blizzard calm-gate, and the fixed per-spawn-tick budget (NaN-safe, no state).
 */
class SnowSparkleLawTest {

    private static final double EPS = 1e-9;

    // ---- the calm band (80-85) ----

    @Test
    void bandIntensityTrapezoidOverEightyToEightyFive() {
        assertEquals(0.0, SnowSparkleLaw.bandIntensity01(79.9), EPS);  // below the snow onset
        assertEquals(0.0, SnowSparkleLaw.bandIntensity01(80.0), EPS);  // at the onset: still 0
        assertEquals(1.0, SnowSparkleLaw.bandIntensity01(81.5), EPS);  // full by the calm mid-band start
        assertEquals(1.0, SnowSparkleLaw.bandIntensity01(82.5), EPS);  // held across the calm plateau
        assertEquals(1.0, SnowSparkleLaw.bandIntensity01(84.0), EPS);  // held to the shoulder
        assertEquals(0.0, SnowSparkleLaw.bandIntensity01(85.0), EPS);  // gone at the frostbite onset
        assertEquals(0.0, SnowSparkleLaw.bandIntensity01(88.0), EPS);  // deep in the blizzard: nothing
        // ramps
        double up = SnowSparkleLaw.bandIntensity01(80.75); // halfway 80->81.5
        assertTrue(up > 0.0 && up < 1.0, "fade-in interior: " + up);
        double down = SnowSparkleLaw.bandIntensity01(84.5); // halfway 84->85
        assertTrue(down > 0.0 && down < 1.0, "fade-out interior: " + down);
    }

    @Test
    void bandBoundariesPinnedToTheHazardConstants() {
        assertEquals(PolarHazardWindow.AMBIENT_ONSET_DEG, SnowSparkleLaw.ONSET_DEG, EPS);   // snow onset (80)
        assertEquals(PolarHazardWindow.FROSTBITE_ONSET_DEG, SnowSparkleLaw.END_DEG, EPS);   // frostbite (85)
    }

    // ---- the calm gate ----

    @Test
    void calmFactorFallsWithStorm() {
        assertEquals(1.0, SnowSparkleLaw.calmFactor01(0.0), EPS);  // dead calm -> full
        assertEquals(0.5, SnowSparkleLaw.calmFactor01(0.5), EPS);
        assertEquals(0.0, SnowSparkleLaw.calmFactor01(1.0), EPS);  // full storm -> none
        assertEquals(0.0, SnowSparkleLaw.calmFactor01(Double.NaN), EPS); // NaN -> fully stormy -> none
    }

    // ---- S17(c)(iii): the snowfall window (sparkle only during very-light-to-light snow) ----

    @Test
    void snowfallWindowTrapezoid() {
        assertEquals(0.0, SnowSparkleLaw.snowfallWindow01(0.0), EPS);   // no snow -> no glint
        assertEquals(0.0, SnowSparkleLaw.snowfallWindow01(0.5), EPS);   // below the min-flake floor
        assertEquals(1.0, SnowSparkleLaw.snowfallWindow01(2.0), EPS);   // very light -> full
        assertEquals(1.0, SnowSparkleLaw.snowfallWindow01(SnowSparkleLaw.SNOWFALL_LIGHT_MAX), EPS); // light ceiling
        assertEquals(0.0, SnowSparkleLaw.snowfallWindow01(SnowSparkleLaw.SNOWFALL_MEDIUM), EPS);    // medium+ -> off
        assertEquals(0.0, SnowSparkleLaw.snowfallWindow01(40.0), EPS);  // heavy -> off
        double mid = SnowSparkleLaw.snowfallWindow01(18.0);             // between light (14) and medium (22)
        assertTrue(mid > 0.0 && mid < 1.0, "light->medium fade: " + mid);
        assertEquals(0.5, SnowSparkleLaw.snowfallWindow01(18.0), EPS);  // exactly halfway 14->22
        assertEquals(0.0, SnowSparkleLaw.snowfallWindow01(Double.NaN), EPS); // NaN-safe
    }

    // ---- the budget ----

    @Test
    void sparkleBudgetIsBandTimesCalmTimesWindowTimesPeak() {
        assertEquals(4, SnowSparkleLaw.sparkleBudget(81.5, 0.0, 4)); // full band + full window, dead calm
        assertEquals(0, SnowSparkleLaw.sparkleBudget(81.5, 1.0, 4)); // full storm snuffs it
        assertEquals(0, SnowSparkleLaw.sparkleBudget(79.0, 0.0, 4)); // below the band (and below the snow onset)
        assertEquals(0, SnowSparkleLaw.sparkleBudget(86.0, 0.0, 4)); // above the band (in the blizzard)
        assertEquals(2, SnowSparkleLaw.sparkleBudget(80.75, 0.0, 4)); // half band-ramp, still light snow -> round(0.5*4)
    }

    @Test
    void sparkleGatedOffAtMediumSnowEvenInsideTheLatitudeBand() {
        // 84 deg is INSIDE the latitude band (bandIntensity01 == 1.0 there) but the ambient snow is medium+ at
        // that latitude, so the S17(c)(iii) snowfall window snuffs the sparkle -- "only during light snow".
        assertEquals(1.0, SnowSparkleLaw.bandIntensity01(84.0), EPS);
        assertEquals(0, SnowSparkleLaw.sparkleBudget(84.0, 0.0, 4));
        // ...and it is alive in the light-snow shoulder near the onset.
        assertTrue(SnowSparkleLaw.sparkleBudget(81.5, 0.0, 4) > 0, "sparkle alive in light snow");
    }

    @Test
    void sparkleBudgetNonPositivePeakAndNaNSafe() {
        assertEquals(0, SnowSparkleLaw.sparkleBudget(82.5, 0.0, 0));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(82.5, 0.0, -3));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(Double.NaN, 0.0, 4));
        assertEquals(0, SnowSparkleLaw.sparkleBudget(82.5, Double.NaN, 4)); // NaN storm -> calm 0 -> 0
        assertTrue(SnowSparkleLaw.DEFAULT_PEAK_BUDGET > 0, "a sane default peak");
    }
}
