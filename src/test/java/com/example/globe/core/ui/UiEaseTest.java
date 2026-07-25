package com.example.globe.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Probes for the HUD Studio row-transition easing (audit H1). Pure math, no Minecraft: verifies the ease
 * never overshoots, always moves toward the target, terminates in finite frames, and that reveal stays in
 * [0,1] both rolling out (0-&gt;1) and rolling in (1-&gt;0).
 */
class UiEaseTest {

    private static final float DELTA = 1.0f; // ~1 partial-tick, a typical frame

    @Test
    void approachMovesTowardTargetAndNeverOvershoots() {
        float v = 0f;
        float prev = v;
        for (int i = 0; i < 5; i++) {
            v = UiEase.approach(v, 1f, DELTA);
            assertTrue(v >= prev, "must be monotonic toward target");
            assertTrue(v <= 1f, "must never overshoot the target");
            prev = v;
        }
    }

    @Test
    void approachConvergesExactlyToTargetInFiniteFrames() {
        float v = 0f;
        int frames = 0;
        while (v != 1f && frames < 200) {
            v = UiEase.approach(v, 1f, DELTA);
            frames++;
        }
        assertEquals(1f, v, 0f, "must snap exactly to target");
        assertTrue(frames < 200, "must terminate quickly, not crawl forever (took " + frames + ")");
    }

    @Test
    void approachSnapsWhenWithinEpsilon() {
        float near = 1f - (UiEase.SETTLE_EPSILON / 2f);
        assertEquals(1f, UiEase.approach(near, 1f, DELTA), 0f);
    }

    @Test
    void revealRollOutAndRollInStayInUnitRange() {
        // Roll out 0 -> 1
        float v = 0f;
        for (int i = 0; i < 50 && v != 1f; i++) {
            v = UiEase.advanceReveal(v, true, DELTA);
            assertTrue(v >= 0f && v <= 1f, "reveal out of [0,1]: " + v);
        }
        assertEquals(1f, v, 0f);
        // Roll in 1 -> 0
        for (int i = 0; i < 50 && v != 0f; i++) {
            v = UiEase.advanceReveal(v, false, DELTA);
            assertTrue(v >= 0f && v <= 1f, "reveal out of [0,1]: " + v);
        }
        assertEquals(0f, v, 0f);
    }

    @Test
    void settledReflectsTargetProximity() {
        assertTrue(UiEase.isSettled(1f, true));
        assertTrue(UiEase.isSettled(0f, false));
        assertFalse(UiEase.isSettled(0.5f, true));
        assertFalse(UiEase.isSettled(0.5f, false));
    }

    @Test
    void largerDeltaSettlesFasterThanSmaller() {
        float fast = UiEase.approach(0f, 1f, 2.0f);
        float slow = UiEase.approach(0f, 1f, 0.25f);
        assertTrue(fast > slow, "a larger frame delta should cover more ground");
    }
}
