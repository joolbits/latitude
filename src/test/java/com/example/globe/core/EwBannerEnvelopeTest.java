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

    @Test
    void bannerAlphaAndLevel1AliasAreTheSameEnvelope() {
        // TEST 85 (a): BOTH tiers now use ONE envelope. level1Alpha/level1Expired are retained aliases of the
        // tier-agnostic bannerAlpha/bannerExpired, so LEVEL_2 fades on exactly the same curve LEVEL_1 does.
        for (long age : new long[] {-10L, 0L, 500L, 1_000L, 5_000L, 10_500L, 11_000L, 50_000L}) {
            assertEquals(EwBannerEnvelope.bannerAlpha(age), EwBannerEnvelope.level1Alpha(age), 1e-6f,
                    "bannerAlpha and its level1 alias must match at age=" + age);
            assertEquals(EwBannerEnvelope.bannerExpired(age), EwBannerEnvelope.level1Expired(age),
                    "bannerExpired and its level1 alias must match at age=" + age);
        }
    }

    // --- early-start cap + nesting (TEST 85 d) -----------------------------------------------------

    @Test
    void visibilityCapEqualsRearmAndNestsCleanly() {
        // The cap is aligned EXACTLY to the passage re-arm radius so there is ONE "edge-zone radius", and the
        // whole banner geometry nests inside the passage geometry: PROMPT (100) < DANGER (175) < CAP (250) < FOG.
        assertEquals(HemispherePassage.REARM_AT, EwBannerEnvelope.VISIBILITY_CAP_BLOCKS, 1e-9,
                "the banner cap must equal the passage re-arm distance (one shared edge-zone radius)");
        assertTrue(HemispherePassage.PROMPT_AT < EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS);
        assertTrue(EwBannerEnvelope.DANGER_MAX_DIST_BLOCKS < EwBannerEnvelope.VISIBILITY_CAP_BLOCKS);
        assertTrue(EwBannerEnvelope.VISIBILITY_CAP_BLOCKS < HemispherePassage.FOG_START);
    }

    // --- thin-window skip (TEST 85 c) --------------------------------------------------------------

    @Test
    void thinWindowCutoffCatchesPeetsaWorldButNotWideWorlds() {
        assertTrue(EwBannerEnvelope.thinWindow(208.0), "his Itty-Bitty onset 208 (208-175=33 < 60) is too thin");
        assertTrue(EwBannerEnvelope.thinWindow(234.0), "59-block window is still too thin");
        assertFalse(EwBannerEnvelope.thinWindow(235.0), "60-block window is exactly readable (>= cutoff)");
        assertFalse(EwBannerEnvelope.thinWindow(600.0), "a wide world's onset is comfortably readable");
        assertTrue(EwBannerEnvelope.thinWindow(Double.NaN), "an unknown onset is treated as thin (skip LEVEL_1)");
    }

    // --- geometry tier (cap + thin + L2) -----------------------------------------------------------

    @Test
    void geometryTierWideWorldLaddersNoneToLevel1ToLevel2() {
        double onset = 600.0; // wide world: not thin, onset far beyond the cap
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(400.0, onset),
                "past the cap (250): no banner even though the storm particles run out to 600");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_1, EwBannerEnvelope.geometryTier(240.0, onset),
                "inside the cap, outside the danger gate: LEVEL_1");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, EwBannerEnvelope.geometryTier(170.0, onset),
                "inside the danger gate: LEVEL_2");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, EwBannerEnvelope.geometryTier(100.0, onset),
                "at the crossing-prompt distance: still LEVEL_2");
    }

    @Test
    void geometryTierCapBoundaryIsExact() {
        double onset = 600.0;
        assertEquals(EwBannerEnvelope.TIER_LEVEL_1, EwBannerEnvelope.geometryTier(250.0, onset),
                "exactly at the cap still shows LEVEL_1");
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(251.0, onset),
                "one block past the cap shows nothing");
    }

    @Test
    void geometryTierThinWorldSkipsLevel1Entirely() {
        double onset = 208.0; // Peetsa's Itty-Bitty world
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(200.0, onset),
                "thin window -> no LEVEL_1 on approach (the 'stole a turn' fix)");
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(180.0, onset),
                "still no LEVEL_1 anywhere in the thin band");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, EwBannerEnvelope.geometryTier(170.0, onset),
                "only the clean LEVEL_2 shows, nearer in");
    }

    @Test
    void geometryTierNaNDistanceIsNone() {
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(Double.NaN, 600.0),
                "a bad distance read never draws a banner");
    }

    // --- direction-aware triggers (TEST 85 b) ------------------------------------------------------

    /** Drive one tick, append the fired + shown tiers, return the next state. */
    private static EwBannerEnvelope.State step(EwBannerEnvelope.State s, double dist, double onset, long t,
                                               java.util.List<Integer> fired, java.util.List<Integer> shown) {
        EwBannerEnvelope.Decision d = EwBannerEnvelope.evaluate(s, dist, onset, t);
        fired.add(d.firedTier());
        shown.add(d.shownTier());
        return d.next();
    }

    @Test
    void approachFiresLevel1ThenLevel2FromTheMilderSide() {
        // NONE -> LEVEL_1 fires LEVEL_1; LEVEL_1 -> LEVEL_2 fires LEVEL_2. A wide world so LEVEL_1 is a real tier.
        double onset = 600.0;
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, 260.0, onset, 0L, fired, shown);   // NONE (past cap)
        s = step(s, 240.0, onset, 100L, fired, shown); // -> LEVEL_1 fires
        s = step(s, 170.0, onset, 200L, fired, shown); // -> LEVEL_2 fires
        assertEquals(java.util.List.of(0, 1, 2), fired,
                "approach fires LEVEL_1 then LEVEL_2, each once, on the inward crossing");
    }

    @Test
    void retreatThroughMildTierFiresNothing_test85Scenario() {
        // THE exact retreat Peetsa hit: walk IN to the severe zone, then OUT back through the mild zone. The
        // walk-out must fire NOTHING (a retreat is not an approach) -- the mild LEVEL_1 must never re-show.
        double onset = 600.0;
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        // approach
        s = step(s, 260.0, onset, 0L, fired, shown);   // NONE
        s = step(s, 240.0, onset, 100L, fired, shown); // LEVEL_1 fires (idx 1)
        s = step(s, 200.0, onset, 200L, fired, shown); // LEVEL_1 linger
        s = step(s, 170.0, onset, 300L, fired, shown); // LEVEL_2 fires (idx 3)
        s = step(s, 160.0, onset, 400L, fired, shown); // LEVEL_2 linger
        // retreat OUT through the mild band
        s = step(s, 180.0, onset, 500L, fired, shown); // back to LEVEL_1 geometry -- must NOT fire
        s = step(s, 210.0, onset, 600L, fired, shown); // still LEVEL_1 geometry -- must NOT fire
        s = step(s, 260.0, onset, 700L, fired, shown); // back to NONE
        assertEquals(java.util.List.of(0, 1, 0, 2, 0, 0, 0, 0), fired,
                "exactly one LEVEL_1 and one LEVEL_2 fire on the way in; the walk-out fires nothing");
        // And during the retreat the MILD tier is never freshly SHOWN (only the fading LEVEL_2 tail, tier 2).
        for (int i = 5; i <= 7; i++) {
            assertFalse(shown.get(i) == EwBannerEnvelope.TIER_LEVEL_1,
                    "walking outward must never re-show the mild LEVEL_1 banner (idx " + i + ")");
        }
    }

    @Test
    void thinWorldApproachFiresOnlyLevel2() {
        // NONE -> LEVEL_2 directly on a thin world (LEVEL_1 skipped): only the severe tier ever fires.
        double onset = 208.0; // Itty-Bitty
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, 205.0, onset, 0L, fired, shown);   // in the thin band -> NONE (skip LEVEL_1)
        s = step(s, 190.0, onset, 100L, fired, shown); // still thin band -> NONE
        s = step(s, 170.0, onset, 200L, fired, shown); // -> LEVEL_2 fires
        s = step(s, 150.0, onset, 300L, fired, shown); // LEVEL_2 linger
        assertFalse(fired.contains(EwBannerEnvelope.TIER_LEVEL_1),
                "a thin world never fires the mild tier");
        assertEquals(1, java.util.Collections.frequency(fired, EwBannerEnvelope.TIER_LEVEL_2),
                "a thin world fires the severe tier exactly once on approach");
    }

    @Test
    void leftPastCapThenReapproachRefiresLevel1() {
        // LEVEL_1 -> (retreat past the cap == NONE) -> re-approach re-fires LEVEL_1 (a genuine re-approach).
        double onset = 600.0;
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, 240.0, onset, 0L, fired, shown);   // LEVEL_1 fires
        s = step(s, 300.0, onset, 100L, fired, shown); // out past the cap -> NONE
        s = step(s, 240.0, onset, 200L, fired, shown); // re-approach -> LEVEL_1 fires again
        assertEquals(2, java.util.Collections.frequency(fired, EwBannerEnvelope.TIER_LEVEL_1),
                "a genuine leave-and-return re-fires the mild tier");
    }

    // --- both tiers fade out and stay gone while lingering (TEST 85 a + #1) ------------------------

    @Test
    void level2FadesOutAndStaysGoneWhileLingering() {
        // Peetsa #1/#3: LEVEL_2 used to PERSIST. It now fades in, holds ~10 s, fades out, then stays gone while
        // the player lingers at the edge -- exactly like LEVEL_1, on the same envelope.
        double onset = 600.0;
        double edge = 150.0; // constant, inside the danger gate -> LEVEL_2 geometry throughout
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;

        EwBannerEnvelope.Decision d0 = EwBannerEnvelope.evaluate(s, edge, onset, 0L);
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, d0.firedTier(), "LEVEL_2 arms on the inward crossing");
        s = d0.next();

        EwBannerEnvelope.Decision d500 = EwBannerEnvelope.evaluate(s, edge, onset, 500L);
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, d500.shownTier());
        assertEquals(0.5f, d500.alpha(), 1e-6f, "mid fade-in");
        s = d500.next();

        EwBannerEnvelope.Decision d5000 = EwBannerEnvelope.evaluate(s, edge, onset, 5_000L);
        assertEquals(1.0f, d5000.alpha(), 1e-6f, "full during the hold");
        s = d5000.next();

        EwBannerEnvelope.Decision d10500 = EwBannerEnvelope.evaluate(s, edge, onset, 10_500L);
        assertEquals(0.5f, d10500.alpha(), 1e-6f, "mid fade-out");
        s = d10500.next();

        EwBannerEnvelope.Decision d11000 = EwBannerEnvelope.evaluate(s, edge, onset, 11_000L);
        assertEquals(EwBannerEnvelope.TIER_NONE, d11000.shownTier(), "fully faded out -> nothing drawn");
        s = d11000.next();

        EwBannerEnvelope.Decision d20000 = EwBannerEnvelope.evaluate(s, edge, onset, 20_000L);
        assertEquals(EwBannerEnvelope.TIER_NONE, d20000.shownTier(),
                "stays gone while lingering at the edge (no persistence, no re-fire without leaving)");
        assertEquals(0, d20000.firedTier(), "lingering never re-arms LEVEL_2");
    }
}
