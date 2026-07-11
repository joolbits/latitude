package com.example.globe.core.ui;

/**
 * Pure, framerate-independent easing math for the HUD Studio's gentle roll-out/roll-in transitions
 * (UI round 13, audit H1). Extracted so the curve is unit-testable with zero Minecraft dependencies,
 * mirroring the create screen's proven {@code easeScroll} exponential ease-out
 * (LatitudeCreateWorldScreen.easeScroll) rather than inventing a new feel.
 *
 * <p>The client glue tracks a per-row "reveal" value in [0,1] (1 = fully present, 0 = collapsed) and, each
 * render frame, nudges it toward its target with {@link #advanceReveal}. New rows start at 0 and roll out;
 * removed rows roll in to 0. A "Reduce Motion" setting bypasses this entirely by snapping to the target.
 */
public final class UiEase {
    private UiEase() {
    }

    /** Below this distance the ease snaps to the target, so a transition terminates in finite frames instead
     *  of crawling asymptotically forever (and so an "is it settled?" check is exact). */
    public static final float SETTLE_EPSILON = 0.004f;

    /** Ease rate. {@code delta} is Minecraft's render partial-tick (~1.0 per tick, ~0.83 per frame at 60fps),
     *  so at 60fps a transition settles in a few frames — the same cadence as the create screen's scroll ease
     *  ({@code 1 - exp(-0.45 * delta * 3)}). Higher = snappier. */
    private static final float RATE = 0.45f * 3f;

    /**
     * Framerate-independent exponential ease of {@code display} toward {@code target}. Never overshoots: the
     * result is always between {@code display} and {@code target} (inclusive). Snaps exactly to {@code target}
     * once within {@link #SETTLE_EPSILON}.
     *
     * @param delta Minecraft render partial-ticks since the last frame (clamped to a tiny positive floor).
     */
    public static float approach(float display, float target, float delta) {
        float diff = target - display;
        if (Math.abs(diff) < SETTLE_EPSILON) {
            return target;
        }
        float factor = 1f - (float) Math.exp(-RATE * Math.max(0.001f, delta));
        if (factor < 0f) factor = 0f;
        if (factor > 1f) factor = 1f;
        return display + diff * factor;
    }

    /**
     * Advance a reveal value in [0,1] toward 1 (when the row should be shown) or 0 (when hidden). The result is
     * always clamped to [0,1]. Convenience wrapper over {@link #approach} for the row transition layer.
     */
    public static float advanceReveal(float current, boolean shown, float delta) {
        float v = approach(current, shown ? 1f : 0f, delta);
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    /** True once a reveal value is within {@link #SETTLE_EPSILON} of its target — i.e. the transition is done. */
    public static boolean isSettled(float current, boolean shown) {
        return Math.abs((shown ? 1f : 0f) - current) < SETTLE_EPSILON;
    }
}
