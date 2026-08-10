package com.example.globe.client;

import com.example.globe.client.create.CreateWorldIntroClock;

/**
 * Guards the create-world intro title's fade against the timing model that produced it being
 * unwatchable.
 *
 * <p>Reported live (maintainer, 2026-08-10): "blank for a second, then the latitude title bursts
 * onto screen ... if you cancel, go back, and create a new world, it is smooth". The clock was
 * wall-clock based. A cold first create-world blocks the render thread while datapacks load, so no
 * frames are drawn during that pause — yet a wall-clock fade keeps burning through it against a
 * screen nobody is painting. The first frame drawn afterwards was already at full alpha: the
 * animation had been spent while the screen sat blank. The warm second attempt looked right only
 * because its load was too short to steal anything, which is why the bug read as intermittent
 * rather than as a model that was wrong on every single run.
 *
 * <p>These cases drive the real clock rather than scanning its source, so they fail if the clamp is
 * removed, widened past the fade's own phases, or quietly reverted to wall-clock arithmetic.
 */
public final class CreateWorldIntroClockPolicyTest {

    private CreateWorldIntroClockPolicyTest() {
    }

    public static void main(String[] args) {
        runAll();
        System.out.println("CREATE_WORLD_INTRO_CLOCK_POLICY_TEST_PASS");
    }

    public static void runAll() {
        aRenderThreadStallCannotConsumeTheFade();
        theFadeStillPlaysAtRealSpeedWhenFramesAreDrawn();
        theFadeRunsInAscendingHoldDescendingOrder();
        aNewCreateWorldAttemptRestartsTheFadeFromTheBeginning();
        theHandoffFromThePreparingScreenDoesNotRestartTheFade();
    }

    /**
     * The whole point. A multi-second freeze with no frames drawn must cost at most one clamped
     * frame, leaving the fade essentially untouched and still ahead of the player.
     */
    private static void aRenderThreadStallCannotConsumeTheFade() {
        long t = 1_000L;
        CreateWorldIntroClock.beginForOwner(new Object(), t);
        CreateWorldIntroClock.advance(t);
        assertEquals(0L, CreateWorldIntroClock.progressMs(),
                "the fade starts at zero progress on the frame the preparing screen first paints");

        // A 4-second datapack stall: exactly one frame is drawn on the far side of it.
        t += 4_000L;
        CreateWorldIntroClock.advance(t);

        assertTrue(CreateWorldIntroClock.progressMs() <= CreateWorldIntroClock.MAX_FRAME_ADVANCE_MS,
                "a stall must be charged at most one clamped frame — with wall-clock arithmetic this "
                        + "reads 4000ms, past the whole animation, and the next frame drawn shows the "
                        + "title already at or past full alpha instead of fading in");
        assertTrue(CreateWorldIntroClock.active(),
                "the fade must still have time left after a stall, or the player sees a blank screen "
                        + "and then a burst");
        assertTrue(CreateWorldIntroClock.alpha() < 1.0f,
                "the title must still be mid-fade-in after a stall, not already fully opaque");
    }

    /** The clamp must not slow down an animation that is being drawn normally. */
    private static void theFadeStillPlaysAtRealSpeedWhenFramesAreDrawn() {
        long t = 50_000L;
        CreateWorldIntroClock.beginForOwner(new Object(), t);
        CreateWorldIntroClock.advance(t);

        // 60fps for exactly the fade-in duration.
        long frame = 16L;
        long frames = CreateWorldIntroClock.FADE_IN_MS / frame;
        for (long i = 0; i < frames; i++) {
            t += frame;
            CreateWorldIntroClock.advance(t);
        }

        assertTrue(frame < CreateWorldIntroClock.MAX_FRAME_ADVANCE_MS,
                "a normal frame must sit below the clamp, or every frame would be throttled");
        assertEquals(frames * frame, CreateWorldIntroClock.progressMs(),
                "normally-drawn frames advance the fade by their real duration — the clamp must only "
                        + "bite on stalls");
        assertTrue(CreateWorldIntroClock.alpha() > 0.9f,
                "after the fade-in's worth of real frames the title is essentially fully faded in");
    }

    /**
     * The maintainer's stated shape: fade in, hold, fade out. A clock that reached full alpha but
     * never came back down, or never held, would still satisfy a naive "it animates" check.
     */
    private static void theFadeRunsInAscendingHoldDescendingOrder() {
        long t = 100_000L;
        CreateWorldIntroClock.beginForOwner(new Object(), t);
        CreateWorldIntroClock.advance(t);

        float midFadeIn = alphaAfter(t, CreateWorldIntroClock.FADE_IN_MS / 2);
        float atHold = alphaAfter(t, CreateWorldIntroClock.FADE_IN_MS + (CreateWorldIntroClock.HOLD_MS / 2));
        float midFadeOut = alphaAfter(t, CreateWorldIntroClock.FADE_IN_MS + CreateWorldIntroClock.HOLD_MS
                + (CreateWorldIntroClock.FADE_OUT_MS / 2));
        float afterEnd = alphaAfter(t, CreateWorldIntroClock.TOTAL_MS + 100L);

        assertTrue(midFadeIn > 0f && midFadeIn < 1f,
                "mid-fade-in the title is partly visible — a hard cut would read as a burst");
        assertEquals(1.0f, atHold, "the title holds at full opacity between the two fades");
        assertTrue(midFadeOut > 0f && midFadeOut < 1f, "mid-fade-out the title is partly visible");
        assertTrue(midFadeOut < atHold, "the fade-out descends from the hold");
        assertEquals(0f, afterEnd, "the title is gone once the animation completes");
    }

    /** Cancelling back out and creating again must replay the whole animation, not resume a spent one. */
    private static void aNewCreateWorldAttemptRestartsTheFadeFromTheBeginning() {
        long t = 200_000L;
        Object firstAttempt = new Object();
        CreateWorldIntroClock.beginForOwner(firstAttempt, t);
        CreateWorldIntroClock.advance(t);
        for (long i = 0; i < CreateWorldIntroClock.TOTAL_MS; i += 16L) {
            t += 16L;
            CreateWorldIntroClock.advance(t);
        }
        assertFalse(CreateWorldIntroClock.active(), "the first attempt's fade has run out");

        // A second create-world attempt is a distinct preparing-screen instance.
        t += 5_000L;
        CreateWorldIntroClock.beginForOwner(new Object(), t);
        assertEquals(0L, CreateWorldIntroClock.progressMs(),
                "a new attempt replays the fade from the start");
        assertTrue(CreateWorldIntroClock.active(), "a new attempt's fade is live again");
    }

    /**
     * The bespoke screen adopts the clock the preparing screen already started; restarting it there
     * is what made the vanilla wait pure overhead in front of a full-length animation.
     */
    private static void theHandoffFromThePreparingScreenDoesNotRestartTheFade() {
        long t = 300_000L;
        CreateWorldIntroClock.beginForOwner(new Object(), t);
        CreateWorldIntroClock.advance(t);
        for (int i = 0; i < 10; i++) {
            t += 16L;
            CreateWorldIntroClock.advance(t);
        }
        long carried = CreateWorldIntroClock.progressMs();
        assertTrue(carried > 0L, "the preparing screen advanced the fade before the handoff");

        // LatitudeCreateWorldScreen.init() runs on the far side of the handoff.
        CreateWorldIntroClock.beginIfInactive(t);
        assertEquals(carried, CreateWorldIntroClock.progressMs(),
                "the bespoke screen adopts the in-flight fade instead of restarting it");

        // But a run that never passed through a preparing screen must still get its own animation.
        for (long i = 0; i < CreateWorldIntroClock.TOTAL_MS; i += 16L) {
            t += 16L;
            CreateWorldIntroClock.advance(t);
        }
        assertFalse(CreateWorldIntroClock.active(), "that fade has now run out");
        CreateWorldIntroClock.beginIfInactive(t);
        assertEquals(0L, CreateWorldIntroClock.progressMs(),
                "a spent clock is restarted, so a direct-to-create path still plays the full fade");
    }

    /** Advances the clock in normal-sized frames until it has accrued targetProgressMs, then reads alpha. */
    private static float alphaAfter(long startMs, long targetProgressMs) {
        long t = startMs;
        while (CreateWorldIntroClock.progressMs() < targetProgressMs) {
            t += 10L;
            CreateWorldIntroClock.advance(t);
        }
        return CreateWorldIntroClock.alpha();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 1.0e-6f) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }
}
