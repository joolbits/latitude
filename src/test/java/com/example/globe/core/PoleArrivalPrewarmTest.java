package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleArrivalPrewarm} (B-7 S9, the TEST 99 virgin-antipode freeze mitigation) --
 * the band-membership predicate, gamemode eligibility, the 64-block re-anchor drift test, and the
 * timeout-refresh cadence. The ticket plumbing itself (addTicketWithRadius/removeTicketWithRadius on
 * {@code TicketType.PORTAL}, the per-player anchor map, the drop hooks on band-exit/cross/disconnect) is the
 * thin MC shim {@code GlobeMod.tickPolePrewarm}/{@code dropPolePrewarm}, documented there; every decision it
 * takes comes from the functions under test here.
 */
class PoleArrivalPrewarmTest {

    // ---- band membership (prompt band + margin) ------------------------------------------------

    @Test
    void inBandUpToPromptPlusMarginAndNotBeyond() {
        // Regular-Wide pole geometry: promptDist = 88.89; band edge = 88.89 + 32 = 120.89.
        double promptAt = PoleGeometry.resolve(10000.0).promptDist();
        assertTrue(PoleArrivalPrewarm.inPrewarmBand(0.0, promptAt), "at the pole line: in-band");
        assertTrue(PoleArrivalPrewarm.inPrewarmBand(promptAt, promptAt), "on the prompt line: in-band");
        assertTrue(PoleArrivalPrewarm.inPrewarmBand(promptAt + PoleArrivalPrewarm.BAND_MARGIN_BLOCKS, promptAt),
                "exactly at prompt+margin: in-band (about to be promptable)");
        assertFalse(PoleArrivalPrewarm.inPrewarmBand(promptAt + PoleArrivalPrewarm.BAND_MARGIN_BLOCKS + 0.1, promptAt),
                "just past prompt+margin: out (drop the ticket)");
        assertFalse(PoleArrivalPrewarm.inPrewarmBand(5000.0, promptAt), "mid-world: out");
    }

    @Test
    void nanReadsAreNeverInBand() {
        double promptAt = PoleGeometry.resolve(10000.0).promptDist();
        assertFalse(PoleArrivalPrewarm.inPrewarmBand(Double.NaN, promptAt),
                "a bad distance read drops the ticket rather than pinning one forever");
        assertFalse(PoleArrivalPrewarm.inPrewarmBand(50.0, Double.NaN));
    }

    // ---- gamemode eligibility (spectator excluded like the prompt) ------------------------------

    @Test
    void everyModeThatCanCrossPrewarmsButSpectatorDoesNot() {
        assertTrue(PoleArrivalPrewarm.eligibleGameMode(false, false), "survival/adventure pre-warms");
        assertTrue(PoleArrivalPrewarm.eligibleGameMode(true, false), "creative crosses too -- pre-warms");
        assertFalse(PoleArrivalPrewarm.eligibleGameMode(false, true), "spectator excluded like the prompt");
        assertFalse(PoleArrivalPrewarm.eligibleGameMode(true, true), "spectator bit wins");
    }

    // ---- re-anchor drift (Chebyshev, > 64 blocks) -----------------------------------------------

    @Test
    void reanchorOnlyBeyondSixtyFourBlocksEitherAxis() {
        assertFalse(PoleArrivalPrewarm.needsReanchor(1000, -9944, 1000, -9944), "no drift: hold");
        assertFalse(PoleArrivalPrewarm.needsReanchor(1000, -9944, 1064, -9944),
                "exactly 64 X-drift: hold (strictly-greater threshold)");
        assertTrue(PoleArrivalPrewarm.needsReanchor(1000, -9944, 1065, -9944), "65 X-drift: re-anchor");
        assertTrue(PoleArrivalPrewarm.needsReanchor(1000, -9944, 935, -9944), "westward drift symmetric");
        assertTrue(PoleArrivalPrewarm.needsReanchor(1000, -9944, 1000, -9944 + 65),
                "Z drift counts too (Chebyshev), though the arrival parallel pins Z in practice");
        assertFalse(PoleArrivalPrewarm.needsReanchor(1000, -9944, 1040, -9904),
                "diagonal drift inside 64 on both axes: hold");
    }

    // ---- refresh cadence (timeout-style keep-alive, tick counter) --------------------------------

    @Test
    void refreshCadenceKeepsTheTimeoutTicketAlive() {
        assertTrue(PoleArrivalPrewarm.shouldRefresh(PoleArrivalPrewarm.NEVER, 12345L),
                "first ever: refresh immediately (the ARM add)");
        long last = 2000L;
        assertFalse(PoleArrivalPrewarm.shouldRefresh(last, last), "same tick: no");
        assertFalse(PoleArrivalPrewarm.shouldRefresh(last, last + PoleArrivalPrewarm.REFRESH_CADENCE_TICKS - 1),
                "just inside the cadence: no");
        assertTrue(PoleArrivalPrewarm.shouldRefresh(last, last + PoleArrivalPrewarm.REFRESH_CADENCE_TICKS),
                "at the cadence: refresh");
        assertTrue(PoleArrivalPrewarm.shouldRefresh(last, 5L),
                "backwards game-time (reset) counts as elapsed -- the loop can never freeze");
        // The cadence must sit comfortably inside PORTAL's 300-tick timeout so the ticket never lapses in-band.
        assertTrue(PoleArrivalPrewarm.REFRESH_CADENCE_TICKS < 300L,
                "refresh cadence must be well inside the PORTAL ticket timeout");
    }

    // ---- drop conditions (documented decision surface) ------------------------------------------

    @Test
    void dropConditionsAreTheBandAndModePredicates() {
        // The shim drops the ticket exactly when either predicate goes false (band-exit / gamemode change),
        // plus the two event drops (successful crossing, disconnect) that are shim-side by nature. Pin the
        // predicate side: a player walking out re-arms nothing until back inside prompt+margin.
        double promptAt = PoleGeometry.resolve(3750.0).promptDist(); // Itty: floored 40
        assertTrue(PoleArrivalPrewarm.inPrewarmBand(40.0 + 32.0, promptAt));
        assertFalse(PoleArrivalPrewarm.inPrewarmBand(40.0 + 32.1, promptAt));
        assertFalse(PoleArrivalPrewarm.eligibleGameMode(false, true),
                "switching into spectator mid-band drops the ticket");
    }
}
