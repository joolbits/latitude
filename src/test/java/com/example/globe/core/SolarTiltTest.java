package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground-truth unit tests for {@link SolarTilt} (§12 of the design + the A1 errata). The §12 spec table is
 * RECOMPUTED here from §4c (A1, BINDING): the two wrong midnight cells (φ=−75 was −75, φ=−90 was −90) are
 * pinned to their correct values (−45, −30), and the pole invariant (elevation constant = ±δ for all H at
 * |φ|=90) is asserted directly.
 */
class SolarTiltTest {

    private static final double TOL = 1e-6;   // the math is exact; the design's ±0.01° is comfortably met
    private static final double UNIT_TOL = 1e-9;

    // ----------------------------------------------------------------------------------------------------
    // §12 elevation table — RECOMPUTED from §4c (A1). Columns: φ, δ=+30 noon (H=0), δ=+30 midnight (H=π),
    // δ=0 noon. The two A1-corrected cells are φ=−75 midnight (−45, was −75) and φ=−90 midnight (−30, was −90).
    // ----------------------------------------------------------------------------------------------------
    private static final double[][] SPEC = {
            //  φ      δ=+30 noon   δ=+30 midnight   δ=0 noon
            {   0.0,       60.0,        -60.0,          90.0},
            {  30.0,       90.0,        -30.0,          60.0},
            {  40.0,       80.0,        -20.0,          50.0},
            {  60.0,       60.0,          0.0,          30.0},
            {  75.0,       45.0,         15.0,          15.0},
            {  90.0,       30.0,         30.0,           0.0},
            { -60.0,        0.0,        -60.0,          30.0},
            { -75.0,      -15.0,        -45.0,          15.0},   // A1: midnight −45, NOT −75
            { -90.0,      -30.0,        -30.0,           0.0},   // A1: midnight −30, NOT −90
    };

    @Test
    void specTableSolsticeAndEquinox() {
        for (double[] row : SPEC) {
            double phi = row[0];
            // δ=+30 noon
            assertEquals(row[1], SolarTilt.solarElevationDeg(phi, 30.0, 0.0), TOL,
                    "δ=+30 noon elevation at φ=" + phi);
            // noonElevationDeg must agree with the full evaluator at H=0
            assertEquals(row[1], SolarTilt.noonElevationDeg(phi, 30.0), TOL,
                    "noonElevationDeg agrees with §4c at φ=" + phi);
            // δ=+30 midnight (H=π) — the A1-corrected column
            assertEquals(row[2], SolarTilt.solarElevationDeg(phi, 30.0, Math.PI), TOL,
                    "δ=+30 midnight elevation at φ=" + phi);
            // δ=0 noon
            assertEquals(row[3], SolarTilt.solarElevationDeg(phi, 0.0, 0.0), TOL,
                    "δ=0 noon elevation at φ=" + phi);
        }
    }

    @Test
    void poleElevationIsConstantForAllHourAngles() {
        // A1 invariant: at |φ| = 90 the cos φ term vanishes, so elevation = sign(φ)·δ independent of H.
        for (double delta : new double[] {0.0, 15.0, 23.5, 30.0, -30.0}) {
            for (double hourFrac = 0.0; hourFrac < 1.0; hourFrac += 0.05) {
                double h = 2.0 * Math.PI * hourFrac;
                assertEquals(delta, SolarTilt.solarElevationDeg(90.0, delta, h), TOL,
                        "N pole elevation == +δ for all H (δ=" + delta + ", H=" + h + ")");
                assertEquals(-delta, SolarTilt.solarElevationDeg(-90.0, delta, h), TOL,
                        "S pole elevation == -δ for all H (δ=" + delta + ", H=" + h + ")");
            }
        }
    }

    @Test
    void onsetThresholdsAreNinetyMinusAbsDelta() {
        for (double delta : new double[] {0.0, 15.0, 23.5, 30.0}) {
            assertEquals(90.0 - Math.abs(delta), SolarTilt.onsetLatDeg(delta), TOL,
                    "onset = 90 - |δ| for δ=" + delta);
            assertEquals(90.0 - Math.abs(delta), SolarTilt.onsetLatDeg(-delta), TOL,
                    "onset uses |δ| (δ=" + (-delta) + ")");
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // §4e — δ(day)
    // ----------------------------------------------------------------------------------------------------
    @Test
    void deltaDayTableAtDefaultYear() {
        double max = 30.0, year = 360.0, phase = 0.0;
        assertEquals(30.0, SolarTilt.deltaDeg(0.0, max, year, phase), TOL, "day 0 → +30 (N summer solstice)");
        assertEquals(0.0, SolarTilt.deltaDeg(90.0, max, year, phase), TOL, "day 90 → 0 (equinox)");
        assertEquals(-30.0, SolarTilt.deltaDeg(180.0, max, year, phase), TOL, "day 180 → -30 (S summer solstice)");
        assertEquals(0.0, SolarTilt.deltaDeg(270.0, max, year, phase), TOL, "day 270 → 0 (equinox)");
        assertEquals(30.0, SolarTilt.deltaDeg(360.0, max, year, phase), TOL, "day 360 → +30 (back to N summer)");
        assertEquals(30.0 * Math.cos(Math.PI / 4.0), SolarTilt.deltaDeg(45.0, max, year, phase), TOL,
                "day 45 → +21.213");
        assertEquals(-30.0 * Math.cos(Math.PI / 4.0), SolarTilt.deltaDeg(135.0, max, year, phase), TOL,
                "day 135 → -21.213");
        // the quarter-point magnitude the design cites explicitly
        assertEquals(21.213203, SolarTilt.deltaDeg(45.0, max, year, phase), 1e-5, "day 45 magnitude");
    }

    // ----------------------------------------------------------------------------------------------------
    // §12 band migration — the astronomy predicates (no functional floor); bands swap poles + vanish at equinox
    // ----------------------------------------------------------------------------------------------------
    @Test
    void bandMigrationDay0Solstice() {
        double delta = SolarTilt.deltaDeg(0.0, 30.0, 360.0, 0.0); // +30
        // south (winter side) poleward of 60 = polar night; north (summer side) = midnight sun
        assertTrue(SolarTilt.isPolarNight(-70.0, delta), "S 70° polar night at N-summer solstice");
        assertTrue(SolarTilt.isPolarNight(-61.0, delta), "S 61° just inside the polar-night band");
        assertFalse(SolarTilt.isPolarNight(-59.0, delta), "S 59° is outside (onset is 60°)");
        assertTrue(SolarTilt.isMidnightSun(70.0, delta), "N 70° midnight sun");
        assertFalse(SolarTilt.isMidnightSun(-70.0, delta), "S side is NOT midnight sun");
        assertFalse(SolarTilt.isPolarNight(70.0, delta), "N side is NOT polar night");
    }

    @Test
    void bandMigrationDay90Equinox() {
        double delta = SolarTilt.deltaDeg(90.0, 30.0, 360.0, 0.0); // ~0
        for (double phi = -90.0; phi <= 90.0; phi += 5.0) {
            assertFalse(SolarTilt.isPolarNight(phi, delta), "no polar night at equinox (φ=" + phi + ")");
            assertFalse(SolarTilt.isMidnightSun(phi, delta), "no midnight sun at equinox (φ=" + phi + ")");
        }
    }

    @Test
    void bandMigrationDay180SolsticeSwapsPoles() {
        double delta = SolarTilt.deltaDeg(180.0, 30.0, 360.0, 0.0); // -30
        assertTrue(SolarTilt.isPolarNight(70.0, delta), "N 70° polar night at S-summer solstice (poles swapped)");
        assertTrue(SolarTilt.isMidnightSun(-70.0, delta), "S 70° midnight sun at S-summer solstice");
    }

    // ----------------------------------------------------------------------------------------------------
    // §4e frozen mode — reproduces the fixed δ=+30 table bit-for-bit
    // ----------------------------------------------------------------------------------------------------
    @Test
    void frozenModeEqualsFixedTilt() {
        // yearLength <= 0 → δ constant at deltaMax·cos(frozenPhase); phase 0 → +30 regardless of day
        for (double day : new double[] {0.0, 12.3, 500.0, -77.0}) {
            assertEquals(30.0, SolarTilt.deltaDeg(day, 30.0, 0.0, 0.0), TOL,
                    "frozen (year=0) δ constant +30 at day " + day);
            assertEquals(30.0, SolarTilt.deltaDeg(day, 30.0, -1.0, 0.0), TOL,
                    "frozen (year<0) δ constant +30 at day " + day);
        }
        // frozen phase 180 → -30 (the other pole is summer)
        assertEquals(-30.0, SolarTilt.deltaDeg(5.0, 30.0, 0.0, 180.0), TOL, "frozen phase 180 → -30");
        // the full δ=+30 spec table reproduced through the frozen delta
        double frozen = SolarTilt.deltaDeg(999.0, 30.0, 0.0, 0.0);
        for (double[] row : SPEC) {
            assertEquals(row[1], SolarTilt.solarElevationDeg(row[0], frozen, 0.0), TOL,
                    "frozen reproduces δ=+30 noon at φ=" + row[0]);
            assertEquals(row[2], SolarTilt.solarElevationDeg(row[0], frozen, Math.PI), TOL,
                    "frozen reproduces δ=+30 midnight at φ=" + row[0]);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // §4c/§5 — render direction vector: asin(up) == elevation, unit length, and vanilla at φ=0,δ=0 (A6)
    // ----------------------------------------------------------------------------------------------------
    @Test
    void directionVectorMatchesElevationAndIsUnit() {
        for (double phi = -90.0; phi <= 90.0; phi += 15.0) {
            for (double delta = -30.0; delta <= 30.0; delta += 15.0) {
                for (double hf = 0.0; hf < 1.0; hf += 0.1) {
                    double h = 2.0 * Math.PI * hf;
                    double[] v = SolarTilt.solarDirection(phi, delta, h);
                    double mag = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                    assertEquals(1.0, mag, UNIT_TOL, "unit vector (φ=" + phi + ", δ=" + delta + ", H=" + h + ")");
                    double elevFromUp = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, v[1]))));
                    assertEquals(SolarTilt.solarElevationDeg(phi, delta, h), elevFromUp, TOL,
                            "asin(up) == solarElevationDeg (φ=" + phi + ", δ=" + delta + ", H=" + h + ")");
                }
            }
        }
    }

    @Test
    void directionVectorEquatorZeroDeclinationIsVanilla() {
        // A6: at φ=0, δ=0 the sun rides the equatorial arc (sin H, cos H, 0) — the P2 equator regression guard.
        for (double hf = 0.0; hf < 1.0; hf += 0.05) {
            double h = 2.0 * Math.PI * hf;
            double[] v = SolarTilt.solarDirection(0.0, 0.0, h);
            assertEquals(Math.sin(h), v[0], UNIT_TOL, "east == sin H");
            assertEquals(Math.cos(h), v[1], UNIT_TOL, "up == cos H (zenith at noon)");
            assertEquals(0.0, v[2], UNIT_TOL, "south == 0");
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // §8 — effective-sun predicate
    // ----------------------------------------------------------------------------------------------------
    @Test
    void effectiveSunUpMatchesThreshold() {
        for (double phi = -90.0; phi <= 90.0; phi += 10.0) {
            for (double delta = -30.0; delta <= 30.0; delta += 10.0) {
                for (double hf = 0.0; hf < 1.0; hf += 0.05) {
                    double h = 2.0 * Math.PI * hf;
                    double elev = SolarTilt.solarElevationDeg(phi, delta, h);
                    assertEquals(elev > SolarTilt.SUN_UP_THRESHOLD_DEG, SolarTilt.effectiveSunUp(phi, delta, h),
                            "effectiveSunUp flips exactly at the disc-edge threshold");
                }
            }
        }
    }

    @Test
    void effectiveSunUpConstantInsideBands() {
        double delta = 30.0;
        for (double hf = 0.0; hf < 1.0; hf += 0.05) {
            double h = 2.0 * Math.PI * hf;
            assertFalse(SolarTilt.effectiveSunUp(-80.0, delta, h), "polar night: sun never up (H=" + h + ")");
            assertTrue(SolarTilt.effectiveSunUp(80.0, delta, h), "midnight sun: sun always up (H=" + h + ")");
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // §8 — functionalBand: the A2 floor + midnight-sun/polar-night classification
    // ----------------------------------------------------------------------------------------------------
    @Test
    void functionalBandClassificationAndFloor() {
        double delta = 30.0, floor = 74.5;
        assertEquals(SolarTilt.FunctionalBand.MIDNIGHT_SUN, SolarTilt.functionalBand(80.0, delta, floor),
                "N 80° at N-summer solstice → midnight sun");
        assertEquals(SolarTilt.FunctionalBand.POLAR_NIGHT, SolarTilt.functionalBand(-80.0, delta, floor),
                "S 80° at N-summer solstice → polar night");
        // A2: 60–74.5° is astronomically in-band but the FUNCTIONAL layer stays out (no village sieges)
        assertTrue(SolarTilt.isMidnightSun(70.0, delta), "70° IS astronomically midnight sun");
        assertEquals(SolarTilt.FunctionalBand.NONE, SolarTilt.functionalBand(70.0, delta, floor),
                "70° is below the 74.5° functional floor → NONE (A2)");
        assertEquals(SolarTilt.FunctionalBand.NONE, SolarTilt.functionalBand(-63.0, delta, floor),
                "the A2 example: a 63° village is NOT under the functional rules");
        // equinox → no bands anywhere
        assertEquals(SolarTilt.FunctionalBand.NONE, SolarTilt.functionalBand(89.0, 0.0, floor),
                "equinox → NONE even at 89°");
        // NaN floor degrades to 0 (no extra gate)
        assertEquals(SolarTilt.FunctionalBand.MIDNIGHT_SUN, SolarTilt.functionalBand(80.0, delta, Double.NaN),
                "NaN floor → treated as 0");
    }

    // ----------------------------------------------------------------------------------------------------
    // Time shim — day / time-of-day / hour angle (pure)
    // ----------------------------------------------------------------------------------------------------
    @Test
    void timeShimNoonMidnightAndFloorMod() {
        assertEquals(0.25, SolarTilt.timeOfDayFrac(6000L), TOL, "MC 6000 = noon fraction 0.25");
        assertEquals(0.75, SolarTilt.timeOfDayFrac(18000L), TOL, "MC 18000 = midnight fraction 0.75");
        assertEquals(0.0, SolarTilt.hourAngleRadians(0.25), TOL, "noon → H = 0");
        assertEquals(Math.PI, SolarTilt.hourAngleRadians(0.75), TOL, "midnight → H = π");
        // a full day later the clock wraps but the phase is identical
        assertEquals(0.25, SolarTilt.timeOfDayFrac(6000L + SolarTilt.TICKS_PER_DAY), TOL, "wraps each day");
        // floorMod: a /time-set rewind past 0 still yields a well-defined phase
        assertEquals(0.75, SolarTilt.timeOfDayFrac(-6000L), TOL, "negative clock (rewind) via floorMod");
        // dayCount is continuous (fractional)
        assertEquals(1.0, SolarTilt.dayCount(24000L), TOL, "one full day");
        assertEquals(1.5, SolarTilt.dayCount(36000L), TOL, "1.5 days (δ drifts smoothly)");
        // noon (H=0) at the equator with δ=0 → sun at the zenith (effective-sun end-to-end sanity)
        double h = SolarTilt.hourAngleRadians(SolarTilt.timeOfDayFrac(6000L));
        assertEquals(90.0, SolarTilt.solarElevationDeg(0.0, 0.0, h), TOL, "equator δ=0 noon → zenith");
        assertTrue(SolarTilt.effectiveSunUp(0.0, 0.0, h), "equator noon → sun up");
        double hMid = SolarTilt.hourAngleRadians(SolarTilt.timeOfDayFrac(18000L));
        assertFalse(SolarTilt.effectiveSunUp(0.0, 0.0, hMid), "equator midnight → sun down");
    }

    // ----------------------------------------------------------------------------------------------------
    // NaN safety — nothing throws; predicates return the inert answer
    // ----------------------------------------------------------------------------------------------------
    @Test
    void nanSafeThroughout() {
        assertTrue(Double.isNaN(SolarTilt.solarElevationDeg(Double.NaN, 30.0, 0.0)), "NaN φ → NaN elevation");
        assertTrue(Double.isNaN(SolarTilt.solarElevationDeg(0.0, Double.NaN, 0.0)), "NaN δ → NaN elevation");
        assertTrue(Double.isNaN(SolarTilt.noonElevationDeg(Double.NaN, 0.0)), "NaN → NaN noon elevation");
        assertTrue(Double.isNaN(SolarTilt.onsetLatDeg(Double.NaN)), "NaN δ → NaN onset");
        assertFalse(SolarTilt.effectiveSunUp(Double.NaN, 0.0, 0.0), "NaN → sun not up (safe default)");
        assertFalse(SolarTilt.isMidnightSun(Double.NaN, 30.0), "NaN → not midnight sun");
        assertFalse(SolarTilt.isPolarNight(0.0, Double.NaN), "NaN → not polar night");
        assertEquals(SolarTilt.FunctionalBand.NONE, SolarTilt.functionalBand(Double.NaN, 30.0, 74.5),
                "NaN φ → NONE band");
        // deltaDeg degrades NaN dials to safe finite values (never poisons the whole sky)
        assertTrue(Double.isFinite(SolarTilt.deltaDeg(Double.NaN, 30.0, 360.0, 0.0)), "NaN day → finite δ");
        assertEquals(0.0, SolarTilt.deltaDeg(0.0, Double.NaN, 360.0, 0.0), TOL, "NaN deltaMax → 0 amplitude");
        assertEquals(30.0, SolarTilt.deltaDeg(0.0, 30.0, Double.NaN, 0.0), TOL, "NaN year → frozen at phase 0");
        double[] v = SolarTilt.solarDirection(Double.NaN, 0.0, 0.0);
        assertTrue(Double.isNaN(v[0]) && Double.isNaN(v[1]) && Double.isNaN(v[2]), "NaN → NaN direction vector");
        assertEquals(0.0, SolarTilt.hourAngleRadians(Double.NaN), TOL, "NaN frac → H 0");
    }
}
