package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- S5's post-crossing COLD GRACE window. Pure tick arithmetic, zero
 * Minecraft imports (Core Logic layer, unit-testable in a plain JVM). The MC-coupled part (the per-player
 * {@code graceUntil} map, stamped on a successful pole crossing and read in the damage tick) is a thin shim in
 * {@code GlobeMod}.
 *
 * <p><b>Why (S5 guard-rail).</b> The S5 arrival lands at 89.5 deg -- ~2.27 HP/s unprotected -- behind an
 * opaque client curtain (~0.85 s fade + reading time). Cold damage is suppressed ONLY for this short window
 * (the ceremony), so a low-health crosser cannot die inside the cutscene; the moment it lapses, the blizzard
 * owns them ("no damage reprieve -- still trekking for their lives"). The window suppresses BOTH cold-damage
 * bands (frostbite + lethal core) and the F3 frost-cue floor; it does NOT suppress the S6 heal lock (the lock
 * logic evaluates independently -- a crosser arriving exposed is not sheltered, so this is nearly moot by
 * construction, and wiring grace into the lock would couple two unrelated rules).
 *
 * <p><b>One-shot per crossing.</b> {@code graceUntil} is stamped once per successful crossing
 * ({@code crossTick + }{@link #GRACE_TICKS}); {@link #isActive} is a pure comparison, so once the window
 * lapses it can never re-open without a NEW crossing stamping a new {@code graceUntil} (pinned by test).
 * Tick-counter arithmetic, never wall-clock (the B-3b anti-backlog discipline).
 */
public final class PoleCrossingGrace {

    private PoleCrossingGrace() {
    }

    /** Cold-grace window length in server ticks (~2.25 s at 20 tps): covers the ~0.85 s arrival-curtain fade
     *  plus orientation time, and nothing more. */
    public static final long GRACE_TICKS = 45L;

    /** Sentinel for "no grace stamped" (also what the shim treats a missing map entry as). */
    public static final long NONE = Long.MIN_VALUE;

    /** The game-time tick at which a crossing's grace window ENDS: {@code crossTick + GRACE_TICKS}. */
    public static long graceUntil(long crossTick) {
        return crossTick + GRACE_TICKS;
    }

    /** True iff the grace window stamped as {@code graceUntilTick} is still open at {@code now} ({@code now}
     *  strictly before the end tick: active on ticks {@code crossTick + 0 .. crossTick + GRACE_TICKS - 1},
     *  cold resumes exactly at {@code crossTick + GRACE_TICKS}). {@link #NONE} is never active. */
    public static boolean isActive(long graceUntilTick, long now) {
        if (graceUntilTick == NONE) {
            return false;
        }
        return now < graceUntilTick;
    }
}
