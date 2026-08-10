package com.example.globe.client;

import com.example.globe.client.create.CreateWorldIntroClock;

/** Focused regression checks for the create-world intro's frame-driven timing. */
public final class CreateWorldIntroClockPolicyTest {

    private CreateWorldIntroClockPolicyTest() {
    }

    public static void main(String[] args) {
        runAll();
        System.out.println("CREATE_WORLD_INTRO_CLOCK_POLICY_TEST_PASS");
    }

    public static void runAll() {
        aRenderThreadStallCannotConsumeTheFade();
        normalFramesProduceFadeInHoldAndFadeOut();
    }

    private static void aRenderThreadStallCannotConsumeTheFade() {
        long now = 1_000L;
        CreateWorldIntroClock.beginForOwner(new Object(), now);
        CreateWorldIntroClock.advance(now);

        CreateWorldIntroClock.advance(now + 4_000L);

        assertTrue(CreateWorldIntroClock.progressMs() <= CreateWorldIntroClock.MAX_FRAME_ADVANCE_MS,
                "a render stall must advance by at most one clamped frame");
        assertTrue(CreateWorldIntroClock.active(), "the fade must remain active after a render stall");
        assertTrue(CreateWorldIntroClock.alpha() > 0f && CreateWorldIntroClock.alpha() < 1f,
                "the first frame after a stall must still be fading in");
    }

    private static void normalFramesProduceFadeInHoldAndFadeOut() {
        long now = 10_000L;
        CreateWorldIntroClock.beginForOwner(new Object(), now);
        CreateWorldIntroClock.advance(now);

        now = advanceBy(now, CreateWorldIntroClock.FADE_IN_MS / 2);
        float midFadeIn = CreateWorldIntroClock.alpha();
        assertTrue(midFadeIn > 0f && midFadeIn < 1f, "fade-in must pass through partial opacity");

        now = advanceBy(now, CreateWorldIntroClock.FADE_IN_MS - (CreateWorldIntroClock.FADE_IN_MS / 2));
        assertEquals(1f, CreateWorldIntroClock.alpha(), "the title must reach full opacity");

        now = advanceBy(now, CreateWorldIntroClock.HOLD_MS);
        assertEquals(1f, CreateWorldIntroClock.alpha(), "the title must hold at full opacity");

        now = advanceBy(now, CreateWorldIntroClock.FADE_OUT_MS / 2);
        float midFadeOut = CreateWorldIntroClock.alpha();
        assertTrue(midFadeOut > 0f && midFadeOut < 1f, "fade-out must pass through partial opacity");

        advanceBy(now, CreateWorldIntroClock.FADE_OUT_MS - (CreateWorldIntroClock.FADE_OUT_MS / 2));
        assertEquals(0f, CreateWorldIntroClock.alpha(), "the title must disappear after fade-out");
        assertFalse(CreateWorldIntroClock.active(), "the clock must be inactive after the full sequence");
    }

    private static long advanceBy(long now, long durationMs) {
        long remaining = durationMs;
        while (remaining > 0L) {
            long step = Math.min(10L, remaining);
            now += step;
            CreateWorldIntroClock.advance(now);
            remaining -= step;
        }
        return now;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 1.0e-6f) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }
}
