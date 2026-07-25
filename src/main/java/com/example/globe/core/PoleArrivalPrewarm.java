package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- S9 ARRIVAL PRE-WARM decisions (TEST 99 live-freeze mitigation). Pure
 * Java, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM).
 *
 * <p><b>Why (TEST 99, Regular-Wide, live).</b> The owner's pole crossing landed on VIRGIN antipodal terrain:
 * the integrated server was already 60+ ticks behind generating it, the crossing itself paid a visible
 * density-compile + 3x3-ring generation burst at answer time, and opening the JourneyMap fullscreen right
 * after arrival hard-stalled the client. A second visit over now-existing chunks did not freeze. Mitigation:
 * while a player who could cross is inside the pole PROMPT BAND, the server keeps a small, timeout-style
 * chunk ticket alive at their CURRENT antipodal arrival target, so by answer time the landing already exists.
 *
 * <p><b>Pure vs shim.</b> This class owns every DECISION: band membership ({@link #inPrewarmBand}), gamemode
 * eligibility ({@link #eligibleGameMode}), the re-anchor drift test ({@link #needsReanchor}), and the
 * refresh cadence ({@link #shouldRefresh}, tick-counter, never wall-clock). The MC-coupled shim
 * ({@code GlobeMod.tickPolePrewarm}/{@code dropPolePrewarm}) is thin: it reads the player's position/gamemode,
 * computes the target via the SAME code path the crossing uses
 * ({@code HemispherePassageService.poleArrivalTarget} -- never duplicated math), and add/removes the vanilla
 * {@code TicketType.PORTAL} radius ticket the decisions call for.
 *
 * <p><b>Budget honesty.</b> ONE active ticket per player, only while in-band: the worst case is N players all
 * camped inside the pole prompt band = N simultaneous {@code (2*RADIUS+1)^2} = 5x5-chunk load tickets (plus
 * their generation cost the FIRST time -- which is exactly the cost being moved off the answer-time critical
 * path). Out-of-band players cost zero. The EW passage deliberately has NO pre-warm: its arrival is the
 * same-|X| mirror column at the player's own Z -- terrain at the identical border distance the player is
 * already standing at, loaded-or-near-loaded by their own approach, never a virgin antipode at this scale.
 */
public final class PoleArrivalPrewarm {

    private PoleArrivalPrewarm() {
    }

    /** Extra blocks past the prompt line that still count as in-band (~ the server cross slack, 32): a player
     *  hovering just outside the prompt line is about to be promptable, so warm their landing already. */
    public static final double BAND_MARGIN_BLOCKS = 32.0;

    /**
     * S16(c) PREWARM LEAD FLOOR (blocks): the MINIMUM distance-from-pole at which the pre-warm arms, independent
     * of the prompt line. When S16(c) collapsed the prompt onto the wall ({@code promptAt} dropped from ~89-121
     * blocks to the ~3-block wall epsilon), {@code promptAt + BAND_MARGIN} alone would have shrunk the pre-warm
     * lead to ~35 blocks -- far too little on a Regular-Wide world to generate the virgin antipode before the
     * player reaches the wall and answers, reintroducing the TEST 99 client freeze. The pre-warm's job is
     * decoupled from the tiny wall prompt: it arms whenever the player is within this generous lead OR within
     * {@code promptAt + margin} (whichever is larger). 128 = 8 chunks, comfortably more lead than the pre-S16
     * degree-based band gave on every size (Itty ~72, Small ~99, Regular ~121), so the async {@code PORTAL}
     * ticket has ample time to build the 5x5 landing while the player crosses the last stretch of whiteout.
     */
    public static final double PREWARM_LEAD_FLOOR_BLOCKS = 128.0;

    /** Re-anchor when the player's drift has moved the antipodal target more than this many blocks (either
     *  axis) from the ticketed anchor: drop the old ticket, add one at the new target. 64 blocks = 4 chunks,
     *  well inside the 5x5 ticket's slack, so the landing column never escapes the warmed square between
     *  re-anchors. */
    public static final double REANCHOR_DRIFT_BLOCKS = 64.0;

    /** Ticket refresh cadence (server ticks, ~3 s): the PORTAL ticket type has a 300-tick timeout, so
     *  re-adding on this cadence keeps it alive indefinitely while in-band, and a dropped/forgotten ticket
     *  self-expires within 15 s (the timeout is the cleanup backstop -- see the shim). Tick-counter
     *  arithmetic, never wall-clock (the B-3b anti-backlog discipline). */
    public static final long REFRESH_CADENCE_TICKS = 60L;

    /** Ticket radius in chunks (2 -> a 5x5 chunk square centered on the arrival target). Matches the ~3x3-5x5
     *  the S9 design allows; one ring wider than the crossing's own synchronous 3x3 FULL ring so the arrival
     *  search's first +-16-block candidates are warm too. */
    public static final int TICKET_RADIUS_CHUNKS = 2;

    /** Sentinel for "no refresh has ever happened" -- {@link #shouldRefresh} always says yes. */
    public static final long NEVER = Long.MIN_VALUE;

    /**
     * Band membership: inside the pole PROMPT band plus {@link #BAND_MARGIN_BLOCKS}, OR within the S16(c)
     * {@link #PREWARM_LEAD_FLOOR_BLOCKS} generous lead (whichever is larger) --
     * {@code distToPole <= max(promptAt + margin, LEAD_FLOOR)}. The lead floor keeps the pre-warm arming well
     * ahead of the wall now that {@code promptAt} is the ~3-block wall epsilon; without it the lead would collapse
     * to ~35 blocks and the virgin-antipode generation would not finish before the crossing (TEST 99). NaN
     * distance (bad read) is never in-band, so a transient bad read drops the ticket rather than pinning one
     * forever.
     */
    public static boolean inPrewarmBand(double distToPole, double promptAt) {
        if (Double.isNaN(distToPole) || Double.isNaN(promptAt)) {
            return false;
        }
        return distToPole <= Math.max(promptAt + BAND_MARGIN_BLOCKS, PREWARM_LEAD_FLOOR_BLOCKS);
    }

    /** Gamemode eligibility: any mode that can CROSS pre-warms -- survival/adventure AND creative; SPECTATOR
     *  is excluded exactly like the prompt (a spectating fly-through must not drag 5x5 generation tickets
     *  around the pole). */
    public static boolean eligibleGameMode(boolean creative, boolean spectator) {
        return !spectator;
    }

    /**
     * Re-anchor test: true when the freshly-computed target has drifted more than
     * {@link #REANCHOR_DRIFT_BLOCKS} from the ticketed anchor on EITHER axis (Chebyshev -- chunk-grid
     * aligned, and the target's Z is pinned to the arrival parallel so in practice this is X drift).
     */
    public static boolean needsReanchor(int anchorX, int anchorZ, int targetX, int targetZ) {
        return Math.max(Math.abs(targetX - anchorX), Math.abs(targetZ - anchorZ)) > REANCHOR_DRIFT_BLOCKS;
    }

    /** Refresh-cadence test: first time ever ({@link #NEVER}) refreshes immediately; afterwards every
     *  {@link #REFRESH_CADENCE_TICKS}. A backwards game-time (world/time reset) counts as elapsed so a reset
     *  can never freeze the refresh loop. */
    public static boolean shouldRefresh(long lastRefreshTick, long now) {
        if (lastRefreshTick == NEVER) {
            return true;
        }
        long since = now - lastRefreshTick;
        return since < 0 || since >= REFRESH_CADENCE_TICKS;
    }
}
