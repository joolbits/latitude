package com.example.globe.client.create;

/**
 * Shared frame-driven clock for the create-world intro. A screen instance claims the clock once,
 * on its first init; repeated init passes of that same instance are a no-op, so widget rebuilds
 * (resize, size cycling, sub-screen return) can never replay the intro.
 *
 * <p>The fade is frame-driven, never wall-clock: each rendered frame advances the clock by a
 * clamped delta, so a render or loading stall cannot silently spend the fade while nothing was
 * actually shown. The intro's time on screen is always the intended duration.
 */
public final class CreateWorldIntroClock {
    private CreateWorldIntroClock() {
    }

    public static final long FADE_IN_MS = 450L;
    public static final long HOLD_MS = 750L;
    public static final long FADE_OUT_MS = 400L;
    public static final long TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;
    public static final long MAX_FRAME_ADVANCE_MS = 50L;

    private static volatile long progressMs;
    private static volatile long lastFrameMs = -1L;
    private static volatile Object owner;

    /** Restarts for a new owning screen instance; repeated claims by that instance are a no-op. */
    public static void beginForOwner(Object requester, long nowMs) {
        if (owner != requester) {
            owner = requester;
            reset(nowMs);
        }
    }

    /**
     * Transfers an active fade to the screen that replaces its preparing surface. If loading took
     * longer than the complete animation, the replacement screen receives a fresh fade instead.
     * Repeated claims by the replacement screen remain a no-op after either path.
     */
    public static void continueForOwner(Object requester, long nowMs) {
        if (owner == requester) {
            return;
        }
        if (!active()) {
            reset(nowMs);
        }
        owner = requester;
    }

    private static void reset(long nowMs) {
        progressMs = 0L;
        lastFrameMs = nowMs;
    }

    /** Advances by one rendered frame, clamping long render gaps so a stall cannot spend the fade. */
    public static void advance(long nowMs) {
        if (lastFrameMs < 0L) {
            lastFrameMs = nowMs;
            return;
        }
        long delta = nowMs - lastFrameMs;
        lastFrameMs = nowMs;
        if (delta > 0L) {
            progressMs += Math.min(delta, MAX_FRAME_ADVANCE_MS);
        }
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
