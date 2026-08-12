package com.example.globe.client;

import com.example.globe.client.create.CreateWorldIntroClock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        theIntroPlaysOncePerScreenInstance();
        preparingSurfaceHandsOffTheActiveFade();
        preparingTextIsAbsentFromTheCreateWorldPath();
    }

    /**
     * Once-per-screen: re-claiming the clock for the SAME screen instance (widget rebuilds --
     * resize, size cycling, sub-screen return) must never restart the fade, while a NEW screen
     * instance always starts fresh instead of inheriting a stale mid-fade.
     */
    private static void theIntroPlaysOncePerScreenInstance() {
        Object firstScreen = new Object();
        long now = 50_000L;
        CreateWorldIntroClock.beginForOwner(firstScreen, now);
        CreateWorldIntroClock.advance(now);
        now = advanceBy(now, CreateWorldIntroClock.FADE_IN_MS / 2);
        long midFadeProgress = CreateWorldIntroClock.progressMs();
        assertTrue(midFadeProgress > 0L, "the fade must have visibly begun before the re-claim");

        CreateWorldIntroClock.beginForOwner(firstScreen, now);
        assertTrue(CreateWorldIntroClock.progressMs() == midFadeProgress,
                "a repeated claim by the same screen instance must not restart the intro");

        CreateWorldIntroClock.beginForOwner(new Object(), now + 10L);
        assertTrue(CreateWorldIntroClock.progressMs() == 0L,
                "a new screen instance must start its own full fade from the beginning");
        assertTrue(CreateWorldIntroClock.active(),
                "the fresh fade must be active for the new screen instance");
    }

    private static void preparingSurfaceHandsOffTheActiveFade() {
        Object preparingScreen = new Object();
        Object createWorldScreen = new Object();
        long now = 70_000L;
        CreateWorldIntroClock.beginForOwner(preparingScreen, now);
        CreateWorldIntroClock.advance(now);
        now = advanceBy(now, CreateWorldIntroClock.FADE_IN_MS / 2);
        long visibleProgress = CreateWorldIntroClock.progressMs();

        CreateWorldIntroClock.continueForOwner(createWorldScreen, now + 10L);
        assertTrue(CreateWorldIntroClock.progressMs() == visibleProgress,
                "the completed create screen must continue the preparing surface's visible fade");

        advanceBy(now, CreateWorldIntroClock.TOTAL_MS);
        CreateWorldIntroClock.continueForOwner(createWorldScreen, now + CreateWorldIntroClock.TOTAL_MS + 10L);
        assertFalse(CreateWorldIntroClock.active(),
                "reinitializing the completed screen must not replay a finished intro");
    }

    private static void preparingTextIsAbsentFromTheCreateWorldPath() {
        try {
            String createScreen = Files.readString(Path.of(
                    "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
            String preparingScreen = Files.readString(Path.of(
                    "src/main/java/com/example/globe/client/create/CreateWorldPreparingScreen.java"));

            assertFalse(createScreen.contains("createWorld.preparing"),
                    "the create-world path must not install vanilla's visible preparation message");
            assertTrue(createScreen.contains("new CreateWorldPreparingScreen()"),
                    "the create-world path must install the Latitude title surface immediately");
            assertTrue(preparingScreen.contains("CreateWorldIntroTitle.render"),
                    "the preparing surface must draw the shared Latitude title");
        } catch (IOException e) {
            throw new AssertionError("unable to inspect the create-world intro sources", e);
        }
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
