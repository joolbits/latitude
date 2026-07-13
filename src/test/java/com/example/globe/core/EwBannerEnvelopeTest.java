package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EwBannerEnvelope} -- the E/W storm WARNING BANNER's onset gate and fade envelope,
 * now a SINGLE white advisory (TEST 89: Peetsa retired the two-tier severe/yellow system). The one boundary is
 * the PER-WORLD band cap ({@code capDist == rampStartDist} ~177.5 deg; TEST 92 tightened it) resolved by
 * {@link EdgeGeometry} and passed in. These tests drive it off a representative Wide world (xRadius 15000) and
 * pin the direction-aware approach-only trigger, the wall-clock fade, and the leave+re-enter re-arm.
 */
class EwBannerEnvelopeTest {

    private static final EdgeGeometry.Resolved WIDE = EdgeGeometry.resolve(15000.0);
    private static final double CAP = WIDE.rampStartDist();  // ~208.33 (177.5 deg, TEST 92)

    // --- geometry tier (single onset boundary) -----------------------------------------------------

    @Test
    void geometryTierIsAdvisoryInsideTheCapNoneBeyond() {
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(CAP + 100.0, CAP),
                "past the cap: no banner");
        assertEquals(EwBannerEnvelope.TIER_ADVISORY, EwBannerEnvelope.geometryTier(CAP - 50.0, CAP),
                "inside the cap: the advisory");
        assertEquals(EwBannerEnvelope.TIER_ADVISORY, EwBannerEnvelope.geometryTier(0.0, CAP),
                "at the very edge: still the advisory");
    }

    @Test
    void geometryTierCapBoundaryIsExact() {
        assertEquals(EwBannerEnvelope.TIER_ADVISORY, EwBannerEnvelope.geometryTier(CAP, CAP),
                "exactly at the cap shows the advisory");
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(CAP + 0.01, CAP),
                "just past the cap shows nothing");
    }

    @Test
    void geometryTierNaNIsNone() {
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(Double.NaN, CAP),
                "a bad distance read never draws a banner");
        assertEquals(EwBannerEnvelope.TIER_NONE, EwBannerEnvelope.geometryTier(100.0, Double.NaN),
                "an unknown cap never draws a banner");
    }

    @Test
    void capComesFromEdgeGeometryAndIsTheFogOnset() {
        // The cap IS the resolved fog onset (rampStartDist), and it leads the crossing prompt by degree spacing.
        assertTrue(WIDE.promptDist() < CAP, "the advisory cap leads the crossing prompt");
        double bpd = EdgeGeometry.blocksPerDegree(WIDE.xRadiusIntended());
        assertEquals(2.5 * bpd, CAP, 1e-6, "cap is the 177.5-deg distance (2.5 deg of blocks, TEST 92)");
    }

    // --- fade envelope ----------------------------------------------------------------------------

    @Test
    void bannerAlphaIsZeroBeforeAndAtArm() {
        assertEquals(0.0f, EwBannerEnvelope.bannerAlpha(-100L), 1e-6f, "a bad negative age never shows text");
        assertEquals(0.0f, EwBannerEnvelope.bannerAlpha(0L), 1e-6f, "nothing yet at the instant of arming");
    }

    @Test
    void bannerAlphaFadesInHoldsFadesOut() {
        assertEquals(0.5f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.FADE_MS / 2), 1e-6f, "mid fade-in");
        assertEquals(1.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.FADE_MS), 1e-6f, "full at end of fade-in");
        assertEquals(1.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.HOLD_MS / 2), 1e-6f, "mid hold");
        assertEquals(1.0f, EwBannerEnvelope.bannerAlpha(EwBannerEnvelope.HOLD_MS), 1e-6f, "fade-out just beginning");
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

    // --- direction-aware trigger (approach only) ---------------------------------------------------

    private static EwBannerEnvelope.State step(EwBannerEnvelope.State s, double dist, long t,
                                               java.util.List<Integer> fired, java.util.List<Integer> shown) {
        EwBannerEnvelope.Decision d = EwBannerEnvelope.evaluate(s, dist, CAP, t);
        fired.add(d.firedTier());
        shown.add(d.shownTier());
        return d.next();
    }

    @Test
    void approachIntoTheBandFiresTheAdvisoryOnce() {
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, CAP + 20.0, 0L, fired, shown);   // NONE (past cap)
        s = step(s, CAP - 50.0, 100L, fired, shown); // -> ADVISORY fires
        s = step(s, CAP - 100.0, 200L, fired, shown);// deeper in -> no re-fire
        assertEquals(java.util.List.of(0, 1, 0), fired,
                "the advisory fires exactly once on the inward crossing, not again while advancing");
    }

    @Test
    void retreatOutOfTheBandFiresNothing() {
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, CAP + 20.0, 0L, fired, shown);   // NONE
        s = step(s, CAP - 50.0, 100L, fired, shown); // ADVISORY fires (idx 1)
        s = step(s, CAP - 20.0, 200L, fired, shown); // still in band -- no re-fire
        s = step(s, CAP + 20.0, 300L, fired, shown); // walk back out -- must NOT fire
        assertEquals(java.util.List.of(0, 1, 0, 0), fired,
                "exactly one fire on the way in; walking out fires nothing");
    }

    @Test
    void leftBandThenReapproachRefiresTheAdvisory() {
        java.util.List<Integer> fired = new java.util.ArrayList<>();
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;
        s = step(s, CAP - 50.0, 0L, fired, shown);   // ADVISORY fires
        s = step(s, CAP + 40.0, 100L, fired, shown); // out past the cap -> NONE
        s = step(s, CAP - 50.0, 200L, fired, shown); // re-approach -> ADVISORY fires again
        assertEquals(2, java.util.Collections.frequency(fired, EwBannerEnvelope.TIER_ADVISORY),
                "a genuine leave-and-return re-fires the advisory");
    }

    @Test
    void advisoryFadesOutAndStaysGoneWhileLingering() {
        double edge = CAP - 50.0; // constant, inside the band -> ADVISORY geometry throughout
        EwBannerEnvelope.State s = EwBannerEnvelope.State.INITIAL;

        EwBannerEnvelope.Decision d0 = EwBannerEnvelope.evaluate(s, edge, CAP, 0L);
        assertEquals(EwBannerEnvelope.TIER_ADVISORY, d0.firedTier(), "advisory arms on the inward crossing");
        s = d0.next();

        EwBannerEnvelope.Decision d500 = EwBannerEnvelope.evaluate(s, edge, CAP, 500L);
        assertEquals(EwBannerEnvelope.TIER_ADVISORY, d500.shownTier());
        assertEquals(0.5f, d500.alpha(), 1e-6f, "mid fade-in");
        s = d500.next();

        EwBannerEnvelope.Decision d5000 = EwBannerEnvelope.evaluate(s, edge, CAP, 5_000L);
        assertEquals(1.0f, d5000.alpha(), 1e-6f, "full during the hold");
        s = d5000.next();

        EwBannerEnvelope.Decision d10500 = EwBannerEnvelope.evaluate(s, edge, CAP, 10_500L);
        assertEquals(0.5f, d10500.alpha(), 1e-6f, "mid fade-out");
        s = d10500.next();

        EwBannerEnvelope.Decision d11000 = EwBannerEnvelope.evaluate(s, edge, CAP, 11_000L);
        assertEquals(EwBannerEnvelope.TIER_NONE, d11000.shownTier(), "fully faded out -> nothing drawn");
        s = d11000.next();

        EwBannerEnvelope.Decision d20000 = EwBannerEnvelope.evaluate(s, edge, CAP, 20_000L);
        assertEquals(EwBannerEnvelope.TIER_NONE, d20000.shownTier(),
                "stays gone while lingering at the edge (no persistence, no re-fire without leaving)");
        assertEquals(0, d20000.firedTier(), "lingering never re-arms the advisory");
    }
}
