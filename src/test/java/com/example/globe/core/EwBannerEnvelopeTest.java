package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EwBannerEnvelope} -- the E/W storm WARNING BANNER's tier gate and fade envelope,
 * now DEGREE-ANCHORED (redesign 2026-07-12): the tier boundaries are the PER-WORLD {@code severeDist} (~178
 * deg) and {@code capDist == rampStartDist} (~176.5 deg) resolved by {@link EdgeGeometry}, passed IN. These
 * tests drive them off a representative Wide world (xRadius 15000, where LEVEL_1 is a real tier) and off
 * explicit thin/degenerate boundaries for the defensive thin-window guard.
 */
class EwBannerEnvelopeTest {

    // Representative Wide-world geometry: severe ~166.67, cap ~291.67, a comfortably readable LEVEL_1 band.
    private static final EdgeGeometry.Resolved WIDE = EdgeGeometry.resolve(15000.0);
    private static final double SEVERE = WIDE.severeDist();     // ~166.67 (178 deg)
    private static final double CAP = WIDE.rampStartDist();     // ~291.67 (176.5 deg)

    // --- top-severity distance gate ---------------------------------------------------------------

    @Test
    void isDangerTrueAtAndInsideTheThreshold() {
        assertTrue(EwBannerEnvelope.isDanger(0.0, SEVERE), "at the very edge");
        assertTrue(EwBannerEnvelope.isDanger(SEVERE - 10.0, SEVERE), "inside the severe band");
        assertTrue(EwBannerEnvelope.isDanger(SEVERE, SEVERE), "on the boundary");
    }

    @Test
    void isDangerFalseOutsideTheThreshold() {
        assertFalse(EwBannerEnvelope.isDanger(SEVERE + 0.01, SEVERE), "just outside the severe band stays LEVEL_1");
        assertFalse(EwBannerEnvelope.isDanger(CAP, SEVERE), "at the banner cap");
        assertFalse(EwBannerEnvelope.isDanger(500.0, SEVERE), "far out");
    }

    @Test
    void tierBoundariesComeFromEdgeGeometryAndNestWithThePassage() {
        // The severe/cap boundaries ARE the resolved geometry, and the whole thing nests with the passage arm
        // geometry: prompt < severe < rearm < rampStart(cap). The lead over the prompt is a constant DEGREE
        // spacing (severe 178 deg vs prompt 179 deg == one degree of blocks), not a fixed block margin.
        assertTrue(WIDE.promptDist() < SEVERE, "severe banner leads the crossing prompt");
        assertTrue(SEVERE < WIDE.rearmDist());
        assertTrue(WIDE.rearmDist() < CAP);
        double oneDeg = EdgeGeometry.blocksPerDegree(WIDE.xRadiusIntended());
        assertEquals(oneDeg, SEVERE - WIDE.promptDist(), 1e-6,
                "severe-to-prompt lead is exactly one longitude degree of blocks");
    }

    // --- fade envelope ----------------------------------------------------------------------------

    @Test
    void bannerAlphaIsZeroBeforeAndAtArm() {
        assertEquals(0.0f, EwBannerEnvelope.bannerAlpha(-100L), 1e-6f, "a bad negative age never shows text");
        assertEquals(0.0f, EwBannerEnvelope.bannerAlpha(0L), 1e-6f, "nothing yet at the instant of arming");
    }

    @Test
    void bannerAlphaFadesInOverTheRamp() {
        assertEquals(0.5f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.FADE_MS / 2), 1e-6f, "mid fade-in");
        assertEquals(1.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.FADE_MS), 1e-6f, "full at end of fade-in");
    }

    @Test
    void bannerAlphaHoldsFullThroughTheHold() {
        assertEquals(1.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.HOLD_MS / 2), 1e-6f, "mid hold");
        assertEquals(1.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.HOLD_MS - 1L), 1e-6f, "end of hold");
        assertEquals(1.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.HOLD_MS), 1e-6f, "fade-out just beginning");
    }

    @Test
    void bannerAlphaFadesOutAfterTheHold() {
        long midFadeOut = EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS / 2;
        assertEquals(0.5f, EwBannerEnvelope.bannerAlpha(midFadeOut), 1e-6f, "half faded out");
        assertEquals(0.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS),
                1e-6f, "fully gone at the tail");
        assertEquals(0.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS + 5_000L),
                1e-6f, "stays gone while lingering");
    }

    @Test
    void bannerExpiredOnlyAfterHoldPlusFade() {
        assertFalse(EwBannerEnvelope.bannerExpired(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS - 1L),
                "still fading -> not yet expired");
        assertTrue(EwBannerEnvelope.bannerExpired(EwBannerEnvelope.HOLD_MS + EwBannerEnvelope.FADE_MS),
                "at the tail -> expired (stay hidden until re-entry re-arms)");
        assertTrue(EwBannerEnvelope.bannerExpired(999_999L), "long past the tail -> still expired");
    }

    @Test
    void bannerAlphaAndLevel1AliasAreTheSameEnvelope() {
        for (long age : new long[] {-10L, 0L, 500L, 1_000L, 5_000L, 10_500L, 11_000L, 50_000L}) {
            assertEquals(EwBannerEnvelope.bannerAlpha(age), EwBannerEnvelope.level1Alpha(age), 1e-6f,
                    "bannerAlpha and its level1 alias must match at age=" + age);
            assertEquals(EwBannerEnvelope.bannerExpired(age), EwBannerEnvelope.level1Expired(age),
                    "bannerExpired and its level1 alias must match at age=" + age);
        }
    }

    // --- thin-window skip (now defensive: the degree geometry keeps the band wide) -----------------

    @Test
    void thinWindowGuardsOnlyDegenerateNarrowBands() {
        assertFalse(EwBannerEnvelope.thinWindow(SEVERE, CAP),
                "the degree geometry's LEVEL_1 band (~125 blocks on Wide) is comfortably readable");
        assertFalse(EwBannerEnvelope.thinWindow(EdgeGeometry.resolve(3750.0).severeDist(),
                        EdgeGeometry.resolve(3750.0).rampStartDist()),
                "even Itty-Bitty keeps the band ~112 blocks -> readable");
        assertTrue(EwBannerEnvelope.thinWindow(170.0, 200.0), "an explicit 30-block window is too thin");
        assertTrue(EwBannerEnvelope.thinWindow(170.0, 229.0), "a 59-block window is still too thin");
        assertFalse(EwBannerEnvelope.thinWindow(170.0, 230.0), "a 60-block window is exactly readable (>= cutoff)");
        assertTrue(EwBannerEnvelope.thinWindow(Double.NaN, CAP), "an unknown boundary is treated as thin");
    }

    // --- geometry tier (cap + thin + L2) -----------------------------------------------------------

    @Test
    void geometryTierWideWorldLaddersNoneToLevel1ToLevel2() {
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(CAP + 100.0, SEVERE, CAP),
                "past the cap: no banner");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_1, EwBannerEnvelope.geometryTier((SEVERE + CAP) / 2.0, SEVERE, CAP),
                "inside the cap, outside the severe gate: LEVEL_1");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, EwBannerEnvelope.geometryTier(SEVERE - 6.0, SEVERE, CAP),
                "inside the severe gate: LEVEL_2");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, EwBannerEnvelope.geometryTier(SEVERE - 60.0, SEVERE, CAP),
                "nearer the edge: still LEVEL_2");
    }

    @Test
    void geometryTierCapBoundaryIsExact() {
        assertEquals(EwBannerEnvelope.TIER_LEVEL_1, EwBannerEnvelope.geometryTier(CAP, SEVERE, CAP),
                "exactly at the cap still shows LEVEL_1");
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(CAP + 0.01, SEVERE, CAP),
                "past the cap shows nothing");
    }

    @Test
    void geometryTierThinWorldSkipsLevel1Entirely() {
        double severe = 170.0, cap = 200.0; // explicit thin band
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(195.0, severe, cap),
                "thin window -> no LEVEL_1 on approach (the 'stole a turn' fix)");
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(180.0, severe, cap),
                "still no LEVEL_1 anywhere in the thin band");
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, EwBannerEnvelope.geometryTier(165.0, severe, cap),
                "only the clean LEVEL_2 shows, nearer in");
    }

    @Test
    void geometryTierNaNDistanceIsNone() {
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(Double.NaN, SEVERE, CAP),
                "a bad distance read never draws a banner");
    }

    // --- direction-aware triggers ------------------------------------------------------------------

    private static EwBannerEnvelope.State step(EwBannerEnvelope.State s, double dist, long t,
                                               java.util.List<Integer> fired, java.util.List<Integer> shown) {
        EwBannerEnvelope.Decision d = EwBannerEnvelope.evaluate(s, dist, SEVERE, CAP, t);
        fired.add(d.firedTier());
        shown.add(d.shownTier());
        return d.next();
    }

    @Test
    void approachFiresLevel1ThenLevel2FromTheMilderSide() {
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, CAP + 20.0, 0L, fired, shown);        // NONE (past cap)
        s = step(s, (SEVERE + CAP) / 2.0, 100L, fired, shown); // -> LEVEL_1 fires
        s = step(s, SEVERE - 6.0, 200L, fired, shown);    // -> LEVEL_2 fires
        assertEquals(java.util.List.of(0, 1, 2), fired,
                "approach fires LEVEL_1 then LEVEL_2, each once, on the inward crossing");
    }

    @Test
    void retreatThroughMildTierFiresNothing_test85Scenario() {
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        double mild = (SEVERE + CAP) / 2.0;
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        // approach
        s = step(s, CAP + 20.0, 0L, fired, shown);   // NONE
        s = step(s, mild, 100L, fired, shown);       // LEVEL_1 fires (idx 1)
        s = step(s, mild - 20.0, 200L, fired, shown);// LEVEL_1 linger
        s = step(s, SEVERE - 6.0, 300L, fired, shown);// LEVEL_2 fires (idx 3)
        s = step(s, SEVERE - 16.0, 400L, fired, shown);// LEVEL_2 linger
        // retreat OUT through the mild band
        s = step(s, SEVERE + 14.0, 500L, fired, shown);// back to LEVEL_1 geometry -- must NOT fire
        s = step(s, mild + 20.0, 600L, fired, shown); // still LEVEL_1 geometry -- must NOT fire
        s = step(s, CAP + 20.0, 700L, fired, shown);  // back to NONE
        assertEquals(java.util.List.of(0, 1, 0, 2, 0, 0, 0, 0), fired,
                "exactly one LEVEL_1 and one LEVEL_2 fire on the way in; the walk-out fires nothing");
        for (int i = 5; i <= 7; i++) {
            assertFalse(shown.get(i) == EwBannerEnvelope.TIER_LEVEL_1,
                    "walking outward must never re-show the mild LEVEL_1 banner (idx " + i + ")");
        }
    }

    @Test
    void thinWorldApproachFiresOnlyLevel2() {
        // Explicit thin boundaries -> LEVEL_1 skipped entirely; only LEVEL_2 ever fires.
        double severe = 170.0, cap = 200.0;
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        for (double[] step : new double[][] {{195.0, 0}, {185.0, 100}, {165.0, 200}, {150.0, 300}}) {
            EwBannerEnvelope.Decision d = EwBannerEnvelope.evaluate(s, step[0], severe, cap, (long) step[1]);
            fired.add(d.firedTier());
            s = d.next();
        }
        assertFalse(fired.contains(EwBannerEnvelope.TIER_LEVEL_1), "a thin world never fires the mild tier");
        assertEquals(1, java.util.Collections.frequency(fired, EwBannerEnvelope.TIER_LEVEL_2),
                "a thin world fires the severe tier exactly once on approach");
    }

    @Test
    void leftPastCapThenReapproachRefiresLevel1() {
        double mild = (SEVERE + CAP) / 2.0;
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, mild, 0L, fired, shown);         // LEVEL_1 fires
        s = step(s, CAP + 40.0, 100L, fired, shown); // out past the cap -> NONE
        s = step(s, mild, 200L, fired, shown);       // re-approach -> LEVEL_1 fires again
        assertEquals(2, java.util.Collections.frequency(fired, EwBannerEnvelope.TIER_LEVEL_1),
                "a genuine leave-and-return re-fires the mild tier");
    }

    // --- both tiers fade out and stay gone while lingering -----------------------------------------

    @Test
    void level2FadesOutAndStaysGoneWhileLingering() {
        double edge = SEVERE - 16.0; // constant, inside the severe gate -> LEVEL_2 geometry throughout
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;

        EwBannerEnvelope.Decision d0 = EwBannerEnvelope.evaluate(s, edge, SEVERE, CAP, 0L);
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, d0.firedTier(), "LEVEL_2 arms on the inward crossing");
        s = d0.next();

        EwBannerEnvelope.Decision d500 = EwBannerEnvelope.evaluate(s, edge, SEVERE, CAP, 500L);
        assertEquals(EwBannerEnvelope.TIER_LEVEL_2, d500.shownTier());
        assertEquals(0.5f, d500.alpha(), 1e-6f, "mid fade-in");
        s = d500.next();

        EwBannerEnvelope.Decision d5000 = EwBannerEnvelope.evaluate(s, edge, SEVERE, CAP, 5_000L);
        assertEquals(1.0f, d5000.alpha(), 1e-6f, "full during the hold");
        s = d5000.next();

        EwBannerEnvelope.Decision d10500 = EwBannerEnvelope.evaluate(s, edge, SEVERE, CAP, 10_500L);
        assertEquals(0.5f, d10500.alpha(), 1e-6f, "mid fade-out");
        s = d10500.next();

        EwBannerEnvelope.Decision d11000 = EwBannerEnvelope.evaluate(s, edge, SEVERE, CAP, 11_000L);
        assertEquals(EwBannerEnvelope.TIER_NONE, d11000.shownTier(), "fully faded out -> nothing drawn");
        s = d11000.next();

        EwBannerEnvelope.Decision d20000 = EwBannerEnvelope.evaluate(s, edge, SEVERE, CAP, 20_000L);
        assertEquals(EwBannerEnvelope.TIER_NONE, d20000.shownTier(),
                "stays gone while lingering at the edge (no persistence, no re-fire without leaving)");
        assertEquals(0, d20000.firedTier(), "lingering never re-arms LEVEL_2");
    }
}
