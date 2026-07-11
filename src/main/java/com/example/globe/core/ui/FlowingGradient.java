package com.example.globe.core.ui;

/**
 * Pure math for the flowing rainbow-gradient text shared by every "rainbow" renderer in the mod (the compass
 * HUD label, the HUD Studio "Rainbow Text" preview, the rainbow "HUD Studio" button, the create screen's
 * selected-Random zone row, and the zone-enter title's Rainbow preset).
 *
 * <p>The old treatment gave each letter a distinct color from a fixed 7-entry palette, so neighbors jumped
 * (red&rarr;orange&rarr;yellow). This instead walks the HSB hue wheel by a small step per letter so adjacent
 * letters blend, and drifts the whole string through the wheel over time using the same wall-clock cadence as
 * the Aurora compass dial. No rendering dependencies live here, so the phase math is unit-testable; the
 * client renderers are thin glue that call {@link #colorFor} once per visible letter.
 */
public final class FlowingGradient {
    private FlowingGradient() {
    }

    /** A full rainbow spans ~this many times the visible string length. &gt;1 means any single word shows only
     *  a gentle slice of the wheel and neighboring letters differ only slightly (they "blend"). */
    public static final float SPREAD = 2.5f;
    public static final float SATURATION = 0.85f;
    public static final float BRIGHTNESS = 1.0f;
    /** Fallback loop length (seconds per full color cycle) for renderers with no player-tunable speed of their
     *  own; mirrors {@code CompassHudConfig.rainbowCycleSeconds}'s default so all rainbows drift in step. */
    public static final float DEFAULT_CYCLE_SECONDS = 24.0f;

    /** Time-drifting base hue in [0,1), one full loop per {@code cycleSeconds}. Same wall-clock idiom the
     *  Aurora dial uses ({@code System.currentTimeMillis() % periodMs}), factored out for testing. */
    public static float baseHue(long nowMs, float cycleSeconds) {
        long periodMs = Math.max(1000L, Math.round(cycleSeconds * 1000.0));
        return (float) Math.floorMod(nowMs, periodMs) / (float) periodMs;
    }

    /** Hue in [0,1) for the {@code visibleIdx}-th non-space letter of a string with {@code visibleCount}
     *  visible letters: the time-drifting base plus a small per-letter step so neighbors blend. */
    public static float hueFor(long nowMs, int visibleIdx, int visibleCount, float cycleSeconds) {
        int count = Math.max(1, visibleCount);
        float step = 1.0f / (SPREAD * count);
        float hue = baseHue(nowMs, cycleSeconds) + visibleIdx * step;
        return hue - (float) Math.floor(hue);
    }

    /** Packed {@code 0xRRGGBB} (no alpha) gradient color for that letter — callers OR in their own alpha. */
    public static int colorFor(long nowMs, int visibleIdx, int visibleCount, float cycleSeconds) {
        return java.awt.Color.HSBtoRGB(hueFor(nowMs, visibleIdx, visibleCount, cycleSeconds), SATURATION, BRIGHTNESS) & 0xFFFFFF;
    }
}
