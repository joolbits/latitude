package com.example.globe.core.ui;

import com.example.globe.core.SolarTilt;
import com.example.globe.core.ui.SolarReadout.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-JVM probes for the vanilla-clock solar readout classifier (HUD Studio round 10 item l). Every case pins a
 * (signed latitude, declination, hour angle) to a named {@link State} + its copy, so the readout can never silently
 * disagree with {@link SolarTilt}'s own band/elevation math.
 */
class SolarReadoutTest {

    /** Noon hour angle (H = 0) for the day-sun cases. */
    private static final double NOON = 0.0;
    /** Local midnight hour angle (H = π). */
    private static final double MIDNIGHT = Math.PI;

    @Test
    void midnightSunBand() {
        // φ = +80 (north), δ = +30 (northern summer): |φ| > onset 60 AND same sign => midnight sun, any hour.
        assertEquals(State.MIDNIGHT_SUN, SolarReadout.classify(80.0, 30.0, NOON));
        assertEquals(State.MIDNIGHT_SUN, SolarReadout.classify(80.0, 30.0, MIDNIGHT));
        assertEquals("Midnight Sun", SolarReadout.copy(80.0, 30.0, MIDNIGHT));
    }

    @Test
    void polarNightBand() {
        // φ = -80 (south), δ = +30 (southern winter): |φ| > onset AND opposite sign => polar night, any hour.
        assertEquals(State.POLAR_NIGHT, SolarReadout.classify(-80.0, 30.0, NOON));
        assertEquals(State.POLAR_NIGHT, SolarReadout.classify(-80.0, 30.0, MIDNIGHT));
        assertEquals("Polar Night", SolarReadout.copy(-80.0, 30.0, MIDNIGHT));
    }

    @Test
    void nightWhenSunBelowHorizon() {
        // Equator at local midnight: elevation = -90 => below the horizon, outside any band => Night.
        assertEquals(State.NIGHT, SolarReadout.classify(0.0, 0.0, MIDNIGHT));
    }

    @Test
    void northernNoonSunIsHighInTheSouth() {
        // φ = +40, δ = 0, noon: elevation 50 (high), and the sun leans SOUTH (south component sin40 > 0).
        assertEquals(State.SUN_HIGH_SOUTH, SolarReadout.classify(40.0, 0.0, NOON));
        assertEquals("Sun high in the south", SolarReadout.copy(40.0, 0.0, NOON));
    }

    @Test
    void southernNoonSunIsHighInTheNorth() {
        // φ = -40, δ = 0, noon: elevation 50 (high), and the sun leans NORTH (south component sin(-40) < 0).
        assertEquals(State.SUN_HIGH_NORTH, SolarReadout.classify(-40.0, 0.0, NOON));
        assertEquals("Sun high in the north", SolarReadout.copy(-40.0, 0.0, NOON));
    }

    @Test
    void lowSunNearSunriseReadsLow() {
        // φ = +80, δ = -30 (northern winter, NOT polar night here: onset = 90-30 = 60, |80| > 60 AND opposite
        // sign => that IS polar night). Use a milder case: φ = +80, δ = -8 => onset 82, |80| < 82, so no band.
        // At noon the elevation is 90 - |80 - (-8)| = 2 deg: barely up => LOW, leaning south (northern noon).
        State s = SolarReadout.classify(80.0, -8.0, NOON);
        assertEquals(State.SUN_LOW_SOUTH, s);
    }

    @Test
    void nanDegradesToNight() {
        assertEquals(State.NIGHT, SolarReadout.classify(Double.NaN, 0.0, NOON));
        assertEquals(State.NIGHT, SolarReadout.classify(45.0, Double.NaN, NOON));
        assertEquals(State.NIGHT, SolarReadout.classify(45.0, 0.0, Double.NaN));
    }

    @Test
    void lowHighSplitTracksTheThreshold() {
        // The low/high cut is LOW_ELEVATION_MAX_DEG. Build two northern-noon cases straddling it: noon elevation
        // is 90 - |φ - δ| with δ = 0, so φ = 75 => elevation 15 (< 20 => low), φ = 65 => elevation 25 (> 20 => high).
        assertEquals(20.0, SolarReadout.LOW_ELEVATION_MAX_DEG);
        assertEquals(State.SUN_LOW_SOUTH, SolarReadout.classify(75.0, 0.0, NOON));
        assertEquals(State.SUN_HIGH_SOUTH, SolarReadout.classify(65.0, 0.0, NOON));
    }
}
