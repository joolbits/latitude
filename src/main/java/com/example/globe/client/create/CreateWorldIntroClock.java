package com.example.globe.client.create;

/**
 * The create-world intro title's fade clock. Pure timing state, deliberately free of any Minecraft
 * type so it can be exercised directly by the policy suite; {@link CreateWorldIntroTitle} owns the
 * drawing that uses it.
 *
 * <p><b>The fade advances per rendered frame, not by wall clock</b>, and that distinction is the
 * entire reason this class exists. Reported live (maintainer, 2026-08-10): "blank for a second,
 * then the latitude title bursts onto screen ... if you cancel, go back, and create a new world, it
 * is smooth". A cold first create-world blocks the render thread while datapacks load, so no frames
 * are drawn at all during that pause. A wall-clock fade keeps burning through that freeze against a
 * screen nobody is drawing, and the first frame painted afterwards is already at full alpha — the
 * animation was spent while the screen sat blank. The second attempt looked correct only because
 * warm caches made the load short enough to steal nothing, which is exactly why the bug read as
 * intermittent rather than as a timing model that was wrong every time.
 *
 * <p>Accumulating {@link #advance} deltas and clamping each to {@link #MAX_FRAME_ADVANCE_MS} means a
 * stall of any length costs at most one clamped frame, so the fade-in/hold/fade-out the player
 * actually sees is always the full intended animation.
 */
public final class CreateWorldIntroClock {
    private CreateWorldIntroClock() {
    }

    public static final long FADE_IN_MS = 450L;
    public static final long HOLD_MS = 750L;
    public static final long FADE_OUT_MS = 400L;
    public static final long TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;

    /**
     * Ceiling on how much a single frame may advance the fade. Frames drawn at any normal rate sit
     * far below this, so the animation plays in real time; a multi-second render-thread stall is
     * charged this much and no more, which is what keeps a slow datapack load from consuming the
     * fade while the screen is frozen.
     */
    public static final long MAX_FRAME_ADVANCE_MS = 50L;

    /** Frames-rendered progress into the animation, NOT wall-clock elapsed. */
    private static volatile long progressMs;
    /** Timestamp of the last {@link #advance}, or -1 when the clock has never been started. */
    private static volatile long lastFrameMs = -1L;
    private static volatile Object owner;

    /** Starts the clock for a newly-seen requester, resetting unconditionally -- every new
     *  "Preparing for world creation" screen instance is a new create-world attempt. The identity
     *  check keeps repeated frames from the same instance from restarting it. */
    public static void beginForOwner(Object requester, long nowMs) {
        if (owner != requester) {
            owner = requester;
            reset(nowMs);
        }
    }

    /** Starts the clock only if it is not already mid-flight -- used by LatitudeCreateWorldScreen
     *  so a run that already passed through the preparing-screen mixin keeps that clock instead of
     *  restarting, while a run that reached this screen directly (dev auto-create probe, or any
     *  path that skips vanilla's preparing screen) still gets its own full animation. */
    public static void beginIfInactive(long nowMs) {
        if (!active()) {
            owner = null;
            reset(nowMs);
        }
    }

    private static void reset(long nowMs) {
        progressMs = 0L;
        lastFrameMs = nowMs;
    }

    /**
     * Advances the fade by one rendered frame. Call exactly once per frame from whichever screen
     * currently owns the intro, before reading {@link #active()} or {@link #alpha()}. A negative
     * delta (clock skew) counts as zero; anything longer than {@link #MAX_FRAME_ADVANCE_MS} -- i.e.
     * a stall, during which nothing was on screen to animate -- is charged only that ceiling.
     */
    public static void advance(long nowMs) {
        if (lastFrameMs < 0L) {
            lastFrameMs = nowMs;
            return;
        }
        long delta = nowMs - lastFrameMs;
        lastFrameMs = nowMs;
        if (delta <= 0L) {
            return;
        }
        progressMs += Math.min(delta, MAX_FRAME_ADVANCE_MS);
    }

    public static long progressMs() {
        return progressMs;
    }

    public static boolean active() {
        return lastFrameMs >= 0L && progressMs < TOTAL_MS;
    }

    public static float alpha() {
        long t = progressMs;
        if (t < FADE_IN_MS) {
            return Math.max(0f, t / (float) FADE_IN_MS);
        }
        if (t < FADE_IN_MS + HOLD_MS) {
            return 1.0f;
        }
        long fadeOutT = t - FADE_IN_MS - HOLD_MS;
        if (fadeOutT < FADE_OUT_MS) {
            return 1.0f - (fadeOutT / (float) FADE_OUT_MS);
        }
        return 0f;
    }
}
