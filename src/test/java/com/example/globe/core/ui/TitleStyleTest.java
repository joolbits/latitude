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
     *  and the boost is always within [0, GLIMMER_AMPLITUDE]. The amplitude is now a strong, bright flash
     *  (~0.80-0.90 lerp toward white), not the old faint multiplicative wash. */
    @Test
    void crestTravelsLeftToRightAndStaysWithinAmplitude() {
        assertTrue(TitleStyle.GLIMMER_AMPLITUDE >= 0.80f && TitleStyle.GLIMMER_AMPLITUDE <= 0.90f,
                "amplitude is a strong/bright glimmer (~0.80-0.90 toward white)");
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

    /** brighten() lerps each channel toward 255 by {@code boost} (channel + (255-channel)*boost), reaches
     *  pure white only at boost == 1.0, and is a no-op for boost <= 0. */
    @Test
    void brightenLerpsTowardWhite() {
        assertEquals(0x808080, TitleStyle.brighten(0x808080, 0f), "boost 0 is a no-op");
        assertEquals(0x808080, TitleStyle.brighten(0x808080, -0.5f), "negative boost is a no-op");

        // 100 + (255-100)*0.5 = 177.5 -> 178 per channel (halfway from the channel's value to white).
        int lifted = TitleStyle.brighten(0x646464, 0.5f);
        assertEquals(0xB2B2B2, lifted);

        // boost == 1.0 lands every channel exactly on 255 -- pure white -- for ANY starting color.
        int full = TitleStyle.brighten(0xF0F0F0, 1.0f);
        assertEquals(0xFFFFFF, full, "boost 1.0 converges to white; by construction no channel overshoots 255");

        assertEquals(0, TitleStyle.brighten(0x123456, 0.3f) & 0xFF000000, "no alpha bits introduced");
    }

    /** Color-awareness at the STRONG new peak: even lerping 85% toward white, the glimmer stays a sheen ON
     *  each letter's own color -- a saturated fill keeps its hue ordering (a sliver of hue survives the flash),
     *  and a near-white fill lifts toward white without ever overflowing (lerp toward 255 can't wrap). */
    @Test
    void glimmerBrightenIsColorAwareAndLiftsBrightFills() {
        // Saturated warm fill at the full crest: brighter on every channel, and R>G>B still holds -- a strong
        // near-white flash that has NOT converged to flat white (that only happens at boost == 1.0).
        int warm = 0x804020; // R=128 G=64 B=32
        int litWarm = TitleStyle.brighten(warm, TitleStyle.GLIMMER_AMPLITUDE);
        int r = (litWarm >> 16) & 0xFF, g = (litWarm >> 8) & 0xFF, b = litWarm & 0xFF;
        assertTrue(r > 128 && g > 64 && b > 32, "every channel lifts -- a brighter version of the same color");
        assertTrue(r > g && g > b, "hue order preserved -- a sliver of the original hue survives the flash");
        assertTrue(litWarm != 0xFFFFFF, "even the strong crest does not blink to a hue-erasing pure white");

        // Near-white fill (OFF_WHITE): lifts toward white and, by construction of the lerp, no channel can
        // exceed 255 -- so there is no wraparound to guard against (each channel lands in [channel, 255]).
        int offWhite = com.example.globe.core.config.LatitudeConfigData.OFF_WHITE_RGB;
        int litOff = TitleStyle.brighten(offWhite, TitleStyle.GLIMMER_AMPLITUDE);
        int or = (litOff >> 16) & 0xFF, og = (litOff >> 8) & 0xFF, ob = litOff & 0xFF;
        assertTrue(or >= ((offWhite >> 16) & 0xFF) && or <= 255, "R lifts toward white, never past 255");
        assertTrue(og >= ((offWhite >> 8) & 0xFF) && og <= 255, "G lifts toward white, never past 255");
        assertTrue(ob >= (offWhite & 0xFF) && ob <= 255, "B lifts toward white, never past 255");
        assertTrue(or > 245 && og > 245 && ob > 245, "an already near-white fill ends up all-but-white");
        assertEquals(0, litOff & 0xFF000000, "no alpha bits introduced on a bright fill");
    }

    /** REGRESSION (the bug this fix targets): a saturated/primary fill must receive a REAL brightness change.
     *  The OLD multiplicative brighten() -- {@code channel * (1 + boost)} clamped to 255 -- left pure primaries
     *  visually unchanged: a maxed channel (255) clamped straight back to 255 and a zeroed channel (0) stayed 0,
     *  so 0xFF0000 was a no-op at ANY boost and 0x000000 could never brighten. Every assertion below FAILS
     *  against that old formula (red stays 0xFF0000, black stays 0x000000); the lerp-toward-white formula lifts
     *  the zeroed channels off 0, so a saturated title now visibly glimmers. */
    @Test
    void saturatedAndPrimaryFillsActuallyBrighten() {
        // Pure red: R stays maxed, G and B lift off zero toward white -> a bright pinkish-red flash.
        int red = 0xFF0000;
        int litRed = TitleStyle.brighten(red, 0.5f);
        assertTrue(litRed != red, "pure red MUST brighten (old multiplicative formula left it 0xFF0000)");
        int r = (litRed >> 16) & 0xFF, g = (litRed >> 8) & 0xFF, b = litRed & 0xFF;
        assertEquals(255, r, "the already-maxed channel stays at 255");
        assertTrue(g > 0 && b > 0, "the zeroed channels lift off 0 (old formula left them stuck at 0)");
        assertTrue(r > g && r > b, "still red-dominant -- a sheen on the color, a sliver of hue survives");
        assertEquals(0xFF8080, litRed, "255 stays 255; 0 + (255-0)*0.5 = 127.5 -> 128 on G and B");

        // Pure black: 0 * (1+boost) is 0 for the old formula at any boost; the lerp moves it toward grey.
        int litBlack = TitleStyle.brighten(0x000000, 0.5f);
        assertTrue(litBlack != 0x000000, "pure black MUST brighten toward grey (old formula left it 0x000000)");
        assertEquals(0x808080, litBlack, "black lerps to mid-grey at boost 0.5 (0 + 255*0.5 = 127.5 -> 128)");
    }
}
