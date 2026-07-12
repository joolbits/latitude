package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EwBannerEnvelope} (Phase 5 Slice B-5 P3, TEST 84 feedback): the E/W storm
 * WARNING BANNER's top-severity distance gate and the mild LEVEL_1 fade envelope.
 */
class EwBannerEnvelopeTest {

    // --- top-severity distance gate ---------------------------------------------------------------

    @Test
    void isDangerTrueAtAndInsideTheThreshold() {
        assertTrue(EwBannerEnvelope.isDanger(0.0), "at the very edge");
        assertTrue(EwBannerEnvelope.isDanger(100.0), "at the crossing-prompt distance");
        assertTrue(EwBannerEnvelope.isDanger(EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS), "on the boundary");
    }

    @Test
    void isDangerFalseOutsideTheThreshold() {
        assertFalse(EwBannerEnvelope.isDanger(EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS + 0.01),
                "just outside the danger band stays LEVEL_1");
        assertFalse(EwBannerEnvelope.isDanger(250.0), "at the passage re-arm distance");
        assertFalse(EwBannerEnvelope.isDanger(500.0), "at the approach-fog onset");
    }

    @Test
    void topSeverityKeepsAConsistentLeadBeforeThePrompt() {
        // WHY: the whole point of the fixed distance gate -- the severe callout must lead the fixed-distance
        // crossing prompt (PROMPT_AT) by the same block margin on every world size. 175 - 100 = 75 blocks.
        double lead = EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS - HemispherePassage.PROMPT_AT;
        assertEquals(75.0, lead, 1e-9, "top-severity banner fires ~75 blocks before the crossing prompt");
        // And it must sit inside the passage's own fixed geometry (prompt < danger < rearm < fog), so the
        // ordering the player experiences approaching the edge reads cleanly nested.
        assertTrue(EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS > HemispherePassage.PROMPT_AT);
        assertTrue(EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS < HemispherePassage.REARM_AT);
        assertTrue(EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS < HemispherePassage.FOG_START);
    }

    // --- LEVEL_1 fade envelope --------------------------------------------------------------------

    @Test
    void level1AlphaIsZeroBeforeAndAtArm() {
        assertEquals(0.0f, EwBannerEnvelope.level1Alpha(-100L), 1e-6f, "a bad negative age never shows text");
        assertEquals(0.0f, EwBannerEnvelope.level1Alpha(0L), 1e-6f, "nothing yet at the instant of arming");
    }

    @Test
    void level1AlphaFadesInOverTheRamp() {
        assertEquals(0.5f, EwBannerEnvelope.level1Alpha(EwBannerEnvelope.FADE_MS / 2), 1e-6f, "mid fade-in");
        assertEquals(1.0f, EwBannerEnvelope.level1Alpha(EwBannerEnvelope.FADE_MS), 1e-6f, "full at end of fade-in");
    }

    @Test
    void level1AlphaHoldsFullThroughTheHold() {
        assertEquals(1.0f, EwBannerEnvelope.level1Alpha(EwBannerEnvelope.HOLD_MS / 2), 1e-6f, "mid hold");
        assertEquals(1.0f, EwBannerEnvelope.level1Alpha(EwBannerEnvelope.HOLD_MS - 1L), 1e-6f, "end of hold");
        assertEquals(1.0f, EwBannerEnvelope.level1Alpha(EwBannerEnvelope.HOLD_MS), 1e-6f, "fade-out just beginning");
    }

    @Test
    void level1AlphaFadesOutAfterTheHold() {
        long midFadeOut = EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS / 2;
        assertEquals(0.5f, EwBannerEnvelope.level1Alpha(midFadeOut), 1e-6f, "half faded out");
        assertEquals(0.0f, EwBannerEnvelope.level1Alpha(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS),
                1e-6f, "fully gone at the tail");
        assertEquals(0.0f, EwBannerEnvelope.level1Alpha(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS + 5_000L),
                1e-6f, "stays gone while lingering");
    }

    @Test
    void level1ExpiredOnlyAfterHoldPlusFade() {
        assertFalse(EwBannerEnvelope.level1Expired(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS - 1L),
                "still fading -> not yet expired");
        assertTrue(EwBannerEnvelope.level1Expired(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS),
                "at the tail -> expired (stay hidden until re-entry re-arms)");
        assertTrue(EwBannerEnvelope.level1Expired(999_999L), "long past the tail -> still expired");
    }
}
