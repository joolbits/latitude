package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math gate for the title's outline geometry and the one-shot color-aware GLIMMER wave (title-styling
 * overhaul 2026-07-11). Guards the properties the renderer relies on: the outline stamps a symmetric
 * 8-neighbour ring (crisp 1px halo, no doubled/missing directions); the glimmer is a SINGLE travelling
 * Gaussian crest that fires once over a bounded tick window right after the title appears and is fully off
 * outside that window (never loops); and the brighten primitive that makes the glimmer color-aware scales a
 * letter's own channels (a sheen ON its color) and clamps cleanly even on a near-white fill.
 */
class TitleStyleTest {

    private static final float TOL = 1e-4f;

    /** The outline offset set is exactly the 8 neighbours of the origin: all of {-1,0,1}^2 except (0,0),
     *  each once. That's what makes it a symmetric 1px outline rather than a lopsided smear. */
    @Test
    void outlineOffsetsAreTheEightNeighbours() {
        int[][] offs = TitleStyle.OUTLINE_OFFSETS_8;
        assertEquals(8, offs.length, "8-direction outline");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int[] o : offs) {
            assertTrue(o[0] >= -1 && o[0] <= 1 && o[1] >= -1 && o[1] <= 1, "unit offsets only");
            assertFalse(o[0] == 0 && o[1] == 0, "origin (0,0) is the main pass, not an outline stamp");
            assertTrue(seen.add(o[0] + "," + o[1]), "no duplicate direction");
        }
        assertEquals(8, seen.size(), "all 8 distinct neighbours present");
    }

    /** Glimmer is off (boost 0) whenever sweep progress is negative -- the "no glimmer" sentinel used for the
     *  toggle-off/solid case, Reduce Motion (the overlay maps both to progress < 0), and the static preview. */
    @Test
    void negativeProgressMeansNoGlimmer() {
        for (int i = 0; i < 8; i++) {
            assertEquals(0f, TitleStyle.glimmerBoost(-1f, i, 8), TOL);
        }
    }

    /** The glimmer is a bounded ONE-SHOT window keyed on the title's age in ticks: nothing before it starts,
     *  a normalized [0,1] ramp while it runs, and nothing again once the single sweep has completed (it never
     *  re-arms / loops). Progress is monotonic increasing across the active window. */
    @Test
    void glimmerWindowIsBoundedOneShot() {
        int start = TitleStyle.GLIMMER_START_TICK;
        int span = TitleStyle.GLIMMER_SPAN_TICKS;
        assertTrue(start >= 0 && span > 0, "sane window constants");

        assertEquals(-1f, TitleStyle.glimmerProgress(0), TOL, "no glimmer the instant the title appears");
        assertEquals(-1f, TitleStyle.glimmerProgress(start - 1L), TOL, "no glimmer before the start tick");
        assertEquals(0f, TitleStyle.glimmerProgress(start), TOL, "sweep begins at progress 0 on the start tick");

        float prev = -1f;
        for (long age = start; age < start + span; age++) {
            float p = TitleStyle.glimmerProgress(age);
            assertTrue(p >= 0f && p < 1.0001f, "progress stays in [0,1) inside the window");
            assertTrue(p > prev, "progress advances monotonically across the window");
            prev = p;
        }
        assertEquals(-1f, TitleStyle.glimmerProgress(start + span), TOL, "one-shot: off once the sweep ends");
        assertEquals(-1f, TitleStyle.glimmerProgress(start + span + 50L), TOL, "stays off -- never loops");
    }

    /** The crest travels: the letter with the peak boost moves strictly left->right as sweep progresses,
     *  and the boost is always within [0, GLIMMER_AMPLITUDE]. The amplitude is a visible glimmer, not the
     *  old faint 0.14 wash. */
    @Test
    void crestTravelsLeftToRightAndStaysWithinAmplitude() {
        assertTrue(TitleStyle.GLIMMER_AMPLITUDE >= 0.28f && TitleStyle.GLIMMER_AMPLITUDE <= 0.42f,
                "amplitude is a visible glimmer (~0.30-0.40)");
        int count = 10;
        int prevPeak = -1;
        for (float progress = 0f; progress <= 1.0001f; progress += 0.1f) {
            int peakIdx = 0;
            float peakVal = -1f;
            for (int i = 0; i < count; i++) {
                float b = TitleStyle.glimmerBoost(progress, i, count);
                assertTrue(b >= 0f && b <= TitleStyle.GLIMMER_AMPLITUDE + TOL,
                        "boost stays within [0, amplitude]");
                if (b > peakVal) {
                    peakVal = b;
                    peakIdx = i;
                }
            }
            assertTrue(peakIdx >= prevPeak, "brightest letter should not move backwards as the sweep advances");
            prevPeak = peakIdx;
        }
    }

    /** Early in the sweep the crest sits at/near the FIRST letter; late in the sweep it sits at/near the LAST
     *  letter -- i.e. exactly one full left->right pass across the word, not a loop. The crest also enters and
     *  exits near-zero (the margin) so it never snaps on/off mid-word. */
    @Test
    void sweepSpansFirstToLastLetterAndFadesAtBothEnds() {
        int count = 12;
        float startFirst = TitleStyle.glimmerBoost(0f, 0, count);
        float startLast = TitleStyle.glimmerBoost(0f, count - 1, count);
        assertTrue(startFirst > startLast, "at sweep start the left edge is the brightest");

        float endFirst = TitleStyle.glimmerBoost(1f, 0, count);
        float endLast = TitleStyle.glimmerBoost(1f, count - 1, count);
        assertTrue(endLast > endFirst, "at sweep end the right edge is the brightest");

        // Wide margin => both edges are near-off at the sweep's start/end (clean fade-in / fade-out).
        assertTrue(startFirst < 0.12f, "left edge enters gently, not at full crest");
        assertTrue(endLast < 0.12f, "right edge exits gently, not at full crest");
    }

    /** brighten() lifts channels by (1+boost), clamps at 255, and is a no-op for boost <= 0. */
    @Test
    void brightenScalesAndClamps() {
        assertEquals(0x808080, TitleStyle.brighten(0x808080, 0f), "boost 0 is a no-op");
        assertEquals(0x808080, TitleStyle.brighten(0x808080, -0.5f), "negative boost is a no-op");

        int lifted = TitleStyle.brighten(0x646464, 0.5f); // 100 -> 150 per channel
        assertEquals(0x969696, lifted);

        int clamped = TitleStyle.brighten(0xF0F0F0, 1.0f); // 240*2 -> clamp 255
        assertEquals(0xFFFFFF, clamped, "channels clamp at 255, no wraparound");

        assertEquals(0, TitleStyle.brighten(0x123456, 0.3f) & 0xFF000000, "no alpha bits introduced");
    }

    /** Color-awareness: the glimmer is a sheen ON each letter's own color, not a white overlay. A saturated
     *  fill brightens IN its own hue (channel ordering preserved, every channel lifted); a near-white fill
     *  (OFF_WHITE) lifts to a clean clamped white with no channel wraparound. */
    @Test
    void glimmerBrightenIsColorAwareAndClampsOnBrightFills() {
        // Saturated warm fill: stays warm (R>G>B preserved) and every channel gets brighter -- not washed white.
        int warm = 0x804020; // R=128 G=64 B=32
        int litWarm = TitleStyle.brighten(warm, TitleStyle.GLIMMER_AMPLITUDE);
        int r = (litWarm >> 16) & 0xFF, g = (litWarm >> 8) & 0xFF, b = litWarm & 0xFF;
        assertTrue(r > 128 && g > 64 && b > 32, "every channel lifts -- a brighter version of the same color");
        assertTrue(r > g && g > b, "hue order preserved -- a sheen on the color, not a slide to white");
        assertTrue(litWarm != 0xFFFFFF, "a saturated mid fill does not clip to pure white");

        // Near-white fill (OFF_WHITE): the peak crest reads as a clean clamped white, no wraparound.
        int offWhite = com.example.globe.core.config.LatitudeConfigData.OFF_WHITE_RGB;
        int litOff = TitleStyle.brighten(offWhite, TitleStyle.GLIMMER_AMPLITUDE);
        assertEquals(0xFFFFFF, litOff, "off-white crest clamps up to white");
        assertEquals(0, litOff & 0xFF000000, "no alpha bits introduced on a bright fill");
    }
}
