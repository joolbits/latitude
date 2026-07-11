package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math gate for the shared flowing rainbow gradient (UI round 13): the phase functions every "rainbow"
 * renderer now shares. Guards the properties that make it read as a smooth gradient rather than the old
 * discrete per-letter palette: neighbors blend (small hue step), the whole string spans a bounded slice of
 * the wheel, and the base hue drifts and wraps cleanly with time.
 */
class FlowingGradientTest {

    private static final float TOL = 1e-4f;

    /** Base hue is 0 at the loop start, wraps back to 0 after exactly one period, and is monotone within. */
    @Test
    void baseHueWrapsWithTime() {
        float cycle = 24.0f;
        long period = 24_000L;
        assertEquals(0.0f, FlowingGradient.baseHue(0L, cycle), TOL);
        assertEquals(0.5f, FlowingGradient.baseHue(period / 2, cycle), TOL);
        // Exactly one full period later, floorMod brings it back to the loop start (no discontinuity/pop).
        assertEquals(FlowingGradient.baseHue(0L, cycle), FlowingGradient.baseHue(period, cycle), TOL);
        assertEquals(FlowingGradient.baseHue(1234L, cycle), FlowingGradient.baseHue(1234L + period, cycle), TOL);
    }

    /** Adjacent visible letters differ by a small, constant hue step = 1/(SPREAD*count): neighbors blend. */
    @Test
    void neighboringLettersBlend() {
        int count = 10;
        float expectedStep = 1.0f / (FlowingGradient.SPREAD * count);
        for (int i = 0; i + 1 < count; i++) {
            float a = FlowingGradient.hueFor(0L, i, count, 24.0f);
            float b = FlowingGradient.hueFor(0L, i + 1, count, 24.0f);
            assertEquals(expectedStep, b - a, TOL, "per-letter hue step");
            // A blend, not a jump: well under a sixth of the wheel (the old palette's neighbor gap).
            assertTrue(Math.abs(b - a) < 1.0f / 6.0f, "neighbors should blend, not jump");
        }
    }

    /** The whole string spans ~1/SPREAD of the wheel regardless of length (full cycle ~= SPREAD x string). */
    @Test
    void wholeStringSpansBoundedSlice() {
        for (int count : new int[]{3, 9, 20, 40}) {
            float first = FlowingGradient.hueFor(0L, 0, count, 24.0f);
            float last = FlowingGradient.hueFor(0L, count - 1, count, 24.0f);
            float span = last - first;
            float budget = 1.0f / FlowingGradient.SPREAD;
            assertTrue(span > 0.0f && span < budget + TOL,
                    "span " + span + " should be within 0.." + budget + " for count " + count);
        }
    }

    /** colorFor is deterministic, alpha-free (0xRRGGBB), and time actually shifts the color. */
    @Test
    void colorIsPackedAndTimeVarying() {
        int c0 = FlowingGradient.colorFor(0L, 0, 8, 24.0f);
        assertTrue(c0 == FlowingGradient.colorFor(0L, 0, 8, 24.0f), "deterministic");
        assertTrue((c0 & 0xFF000000) == 0, "no alpha bits in the packed gradient color");
        int cLater = FlowingGradient.colorFor(6_000L, 0, 8, 24.0f); // quarter of the way round the wheel
        assertNotEquals(c0, cLater, "the gradient should drift with time");
    }
}
