package com.example.globe.core.ui;

/**
 * Pure math + constants for the zone-enter / hemisphere title's outline, diffuse-shadow-glow, and
 * rainbow fade-in shimmer (title-styling overhaul 2026-07-11). No rendering dependencies live here so the
 * offset geometry and the shimmer envelope are unit-testable; {@code ZoneEnterTitleOverlay} is the thin glue
 * that turns these numbers into {@code ctx.text} passes.
 *
 * <p>All offsets are expressed in SCREEN pixels (the caller divides by the title's draw scale before
 * translating the already-scaled pose matrix, so a "1px" outline stays a crisp 1px at any Title Size rather
 * than fattening with scale).
 */
public final class TitleStyle {
    private TitleStyle() {
    }

    /** The classic 8-direction (4 orthogonal + 4 diagonal) unit offsets for a crisp 1px text outline: the
     *  main text is stamped once in the outline color at each of these, one screen pixel out, behind the
     *  main pass. */
    public static final int[][] OUTLINE_OFFSETS_8 = {
        {-1, -1}, {0, -1}, {1, -1},
        {-1,  0},          {1,  0},
        {-1,  1}, {0,  1}, {1,  1},
    };

    /** Diffuse-shadow-glow ring radii in SCREEN pixels: a soft dark halo is built by stamping the text in
     *  low-alpha black at each of the 8 directions, once per ring, radiating outward. Outer rings are fainter
     *  ({@link #GLOW_RING_ALPHA}), which is what makes the halo read as a soft glow rather than a hard edge. */
    public static final float[] GLOW_RING_RADII_PX = {1.5f, 3.0f, 4.5f};

    /** Per-ring alpha as a fraction of the title's own alpha, matched 1:1 to {@link #GLOW_RING_RADII_PX}
     *  (nearest ring strongest, outermost faintest). Kept low on purpose so the glow is a whisper, not a
     *  drop shadow. */
    public static final float[] GLOW_RING_ALPHA = {0.16f, 0.10f, 0.05f};

    /** Peak fractional brightness boost of the shimmer crest (very faint per the brief: ~10-15%). */
    public static final float SHIMMER_AMPLITUDE = 0.14f;

    /** Gaussian half-width of the crest, in units of the string's normalized [0,1] letter position. ~0.22
     *  makes the crest cover roughly a third of the word at a time -- a soft travelling highlight, not a
     *  hard spotlight. */
    public static final float SHIMMER_SIGMA = 0.22f;

    /** Extra travel (fraction of the string) the crest runs beyond each end, so it sweeps fully ON at the
     *  left edge and fully OFF past the right edge across the fade-in window rather than starting/ending
     *  mid-word. */
    static final float SHIMMER_MARGIN = 0.25f;

    /**
     * Brightness boost in {@code [0, SHIMMER_AMPLITUDE]} for one letter, given the title's fade-in progress.
     * A single Gaussian crest travels left&rarr;right as {@code fadeInProgress} runs 0&rarr;1 (the
     * travelling-Gaussian idiom borrowed from the atlas bands), so each letter briefly brightens as the crest
     * passes it -- exactly once, tied to fade-in, never looping.
     *
     * @param fadeInProgress 0..1 through the fade-in window; any value {@code < 0} means "no shimmer" and
     *                       returns 0 (used for solid presets, Reduce Motion, and the static Studio preview).
     */
    public static float shimmerBoost(float fadeInProgress, int visibleIdx, int visibleCount) {
        if (fadeInProgress < 0f) {
            return 0f;
        }
        int count = Math.max(1, visibleCount);
        float p = count <= 1 ? 0f : (float) visibleIdx / (float) (count - 1);
        float crest = -SHIMMER_MARGIN + fadeInProgress * (1f + 2f * SHIMMER_MARGIN);
        float d = p - crest;
        float g = (float) Math.exp(-(d * d) / (2f * SHIMMER_SIGMA * SHIMMER_SIGMA));
        return SHIMMER_AMPLITUDE * g;
    }

    /** Multiplies an {@code 0xRRGGBB} color's brightness by {@code (1 + boost)}, clamping each channel to 255.
     *  Alpha is not part of the input and is left to the caller to OR back in. {@code boost <= 0} is a no-op. */
    public static int brighten(int rgb, float boost) {
        if (boost <= 0f) {
            return rgb & 0xFFFFFF;
        }
        float m = 1f + boost;
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * m));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * m));
        int b = Math.min(255, Math.round((rgb & 0xFF) * m));
        return (r << 16) | (g << 8) | b;
    }
}
