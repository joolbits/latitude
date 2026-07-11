package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-math gate for the title's outline geometry and rainbow fade-in shimmer (title-styling overhaul
 * 2026-07-11). Guards the properties the renderer relies on: the outline stamps a symmetric 8-neighbour ring
 * (crisp 1px halo, no doubled/missing directions), and the shimmer is a single travelling Gaussian crest that
 * sweeps once across the word over the fade-in window and is fully off outside it.
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

    /** Shimmer is off (boost 0) whenever fade-in progress is negative -- the "no shimmer" sentinel used for
     *  solid presets, Reduce Motion, and the static Studio preview. */
    @Test
    void negativeProgressMeansNoShimmer() {
        for (int i = 0; i < 8; i++) {
            assertEquals(0f, TitleStyle.shimmerBoost(-1f, i, 8), TOL);
        }
    }

    /** The crest travels: the letter with the peak boost moves strictly left->right as fade-in progresses,
     *  and the boost is always within [0, SHIMMER_AMPLITUDE]. */
    @Test
    void crestTravelsLeftToRightAndStaysFaint() {
        int count = 10;
        int prevPeak = -1;
        for (float progress = 0f; progress <= 1.0001f; progress += 0.1f) {
            int peakIdx = 0;
            float peakVal = -1f;
            for (int i = 0; i < count; i++) {
                float b = TitleStyle.shimmerBoost(progress, i, count);
                assertTrue(b >= 0f && b <= TitleStyle.SHIMMER_AMPLITUDE + TOL,
                        "boost stays within [0, amplitude]");
                if (b > peakVal) {
                    peakVal = b;
                    peakIdx = i;
                }
            }
            assertTrue(peakIdx >= prevPeak, "brightest letter should not move backwards as fade-in advances");
            prevPeak = peakIdx;
        }
    }

    /** Early in the fade-in the crest sits at/near the FIRST letter; late in the fade-in it sits at/near the
     *  LAST letter -- i.e. exactly one full sweep across the word tied to fade-in, not a loop. */
    @Test
    void sweepSpansFirstToLastLetterAcrossFadeIn() {
        int count = 12;
        float startFirst = TitleStyle.shimmerBoost(0f, 0, count);
        float startLast = TitleStyle.shimmerBoost(0f, count - 1, count);
        assertTrue(startFirst > startLast, "at fade-in start the left edge is the brightest");

        float endFirst = TitleStyle.shimmerBoost(1f, 0, count);
        float endLast = TitleStyle.shimmerBoost(1f, count - 1, count);
        assertTrue(endLast > endFirst, "at fade-in end the right edge is the brightest");
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
}
