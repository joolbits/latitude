package com.example.globe.core.ui;

/**
 * Pure math + constants for the zone-enter / hemisphere title's outline, diffuse-shadow-glow, and the
 * one-shot GLIMMER wave (title-styling overhaul 2026-07-11). No rendering dependencies live here so the
 * offset geometry and the glimmer envelope are unit-testable; {@code ZoneEnterTitleOverlay} is the thin glue
 * that turns these numbers into {@code ctx.text} passes.
 *
 * <p>The glimmer is a single, rapid, color-aware sheen that sweeps once left&rarr;right across the title
 * right after it appears (it generalizes the old round-15 fade-in "shimmer" that only rode the RAINBOW/AURORA
 * presets): one crest, tied to the title's age in ticks, that BRIGHTENS whatever color each letter already is
 * ({@link #brighten} lerps the letter's own RGB toward white by the crest's boost, so a gold letter flashes a
 * brilliant near-white gold and a rainbow letter a brighter band -- a strong sheen ON the color that still
 * leaves a recognizable sliver of the original hue at the peak, never a full white-out). It fires exactly once
 * and never loops. Unlike a plain multiply, the lerp lifts EVERY starting color -- including pure primaries and
 * black, whose maxed/zeroed channels a multiply would leave visually unchanged.
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

    /** First tick (title age, 0 = the frame it appeared) at which the glimmer crest starts moving. Slightly
     *  after fade-in begins (fade-in is {@code ZoneEnterTitleOverlay.FADE_TICKS}=10 ticks) so the title is
     *  already legible as the crest crosses it. */
    public static final int GLIMMER_START_TICK = 2;

    /** Number of ticks the single crest takes to sweep the whole word (~0.7s at 20 tps): a rapid, crisp wave,
     *  noticeably quicker than the old fade-in shimmer. After {@code GLIMMER_START_TICK + GLIMMER_SPAN_TICKS}
     *  the glimmer is done (never loops) and the title renders in its plain colors. */
    public static final int GLIMMER_SPAN_TICKS = 14;

    /** Peak lerp-toward-white fraction of the glimmer crest, fed to {@link #brighten}. Raised to a strong,
     *  obvious flash (0.85) per Peetsa's "10x stronger / bright" ask -- at the crest each channel travels 85%
     *  of the way to 255, a brilliant near-white glint that still leaves a ~15% sliver of the letter's original
     *  hue (so it never blinks to a hue-erasing pure white). Below 1.0 the fill's identity is always still
     *  present; only boost==1.0 would converge every color to flat white, which is exactly what we avoid. */
    public static final float GLIMMER_AMPLITUDE = 0.85f;

    /** Gaussian half-width of the crest, in units of the string's normalized [0,1] letter position. A crisp
     *  crest (~0.20) covers roughly a fifth of the word at a time -- a travelling glint, not a broad wash. */
    public static final float GLIMMER_SIGMA = 0.20f;

    /** Extra travel (fraction of the string) the crest runs beyond each end, so it enters fully OFF at the
     *  left edge and exits fully OFF past the right edge rather than snapping on/off mid-word. Wide enough
     *  (0.45) that both the first and last letters are near-zero boost at the sweep's start/end. */
    static final float GLIMMER_MARGIN = 0.45f;

    /**
     * Normalized progress {@code [0,1]} through the one-shot glimmer sweep for a title of the given age in
     * ticks, or {@code -1} when the glimmer is not running this frame -- BEFORE it starts ({@code age <
     * GLIMMER_START_TICK}) or AFTER the single sweep has completed ({@code age >= GLIMMER_START_TICK +
     * GLIMMER_SPAN_TICKS}). Callers still apply their own gates (the config toggle, Reduce Motion) on top of
     * this; keeping the pure window math here makes the one-shot timing unit-testable.
     */
    public static float glimmerProgress(long ageTicks) {
        long rel = ageTicks - GLIMMER_START_TICK;
        if (rel < 0 || rel >= GLIMMER_SPAN_TICKS) {
            return -1f;
        }
        return (float) rel / (float) GLIMMER_SPAN_TICKS;
    }

    /**
     * Brightness boost in {@code [0, GLIMMER_AMPLITUDE]} for one letter, given normalized sweep progress.
     * A single Gaussian crest travels left&rarr;right as {@code progress} runs 0&rarr;1 (the travelling-Gaussian
     * idiom borrowed from the atlas bands), so each letter briefly brightens as the crest passes it -- exactly
     * once, never looping. The returned boost is meant to be fed straight into {@link #brighten} on the
     * letter's OWN color, which is what makes the glimmer color-aware rather than a white flash.
     *
     * @param progress 0..1 through the glimmer window; any value {@code < 0} means "no glimmer" and returns 0
     *                 (used for solid presets with the toggle off, Reduce Motion, and the static Studio preview).
     */
    public static float glimmerBoost(float progress, int visibleIdx, int visibleCount) {
        if (progress < 0f) {
            return 0f;
        }
        int count = Math.max(1, visibleCount);
        float p = count <= 1 ? 0f : (float) visibleIdx / (float) (count - 1);
        float crest = -GLIMMER_MARGIN + progress * (1f + 2f * GLIMMER_MARGIN);
        float d = p - crest;
        float g = (float) Math.exp(-(d * d) / (2f * GLIMMER_SIGMA * GLIMMER_SIGMA));
        return GLIMMER_AMPLITUDE * g;
    }

    /** Lifts an {@code 0xRRGGBB} color toward white by {@code boost}: each channel becomes
     *  {@code channel + (255 - channel) * boost} (a linear interpolation from the channel's own value toward
     *  255). This is what makes the glimmer COLOR-AWARE yet always visible: every channel moves a proportional
     *  step toward white, so a saturated fill (gold/red/rainbow) flashes a brilliant lighter version of its own
     *  hue while its channel ordering -- and thus a recognizable sliver of the hue -- survives for any
     *  {@code boost < 1}. Crucially this fixes the old multiply's blind spot: a maxed channel (255) or a zeroed
     *  channel (a pure primary or black) that {@code channel * (1 + boost)} left visually unchanged now lifts
     *  like every other channel. By construction the result stays in {@code [channel, 255]} -- it cannot
     *  overflow or wrap -- so no clamping is needed for {@code boost} in {@code [0,1]}. Alpha is not part of the
     *  input and is left to the caller to OR back in. {@code boost <= 0} is a no-op. */
    public static int brighten(int rgb, float boost) {
        if (boost <= 0f) {
            return rgb & 0xFFFFFF;
        }
        float t = boost > 1f ? 1f : boost; // clamp so a channel can never overshoot 255
        int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
        int r = Math.round(r0 + (255 - r0) * t);
        int g = Math.round(g0 + (255 - g0) * t);
        int b = Math.round(b0 + (255 - b0) * t);
        return (r << 16) | (g << 8) | b;
    }
}
