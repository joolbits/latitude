package com.example.globe.core.ui;

/**
 * Pure math + constants for the zone-enter / hemisphere title's outline, diffuse-shadow-glow, and the
 * one-shot GLIMMER wave (title-styling overhaul 2026-07-11). No rendering dependencies live here so the
 * offset geometry and the glimmer envelope are unit-testable; {@code ZoneEnterTitleOverlay} is the thin glue
 * that turns these numbers into {@code ctx.text} passes.
 *
 * <p>The glimmer is a single, rapid SHINE-SWEEP that travels once left&rarr;right across the title right after
 * it appears (it generalizes the old round-15 fade-in "shimmer" that only rode the RAINBOW/AURORA presets):
 * one Gaussian crest, tied to the title's age in ticks, rendered as RELATIVE CONTRAST rather than an absolute
 * brighten. {@link #glimmerGaussian} gives the raw crest height in [0,1] per letter; {@link #glimmerShade}
 * turns that into the per-letter color: letters away from the crest are GENTLY DIMMED to {@link #GLIMMER_DIM_FLOOR}
 * of their brightness, and the crest letter is lifted back to full and then popped toward white by
 * {@link #GLIMMER_WHITE_POP}. The moving bright crest against a briefly dimmed baseline is what makes the shine
 * read on ANY fill -- including a near-white title like the OFF_WHITE default (0xF3ECDD), where you cannot make
 * white brighter than white, so instead the surroundings dim and the crest pops. It fires exactly once and never
 * loops; outside the sweep window {@code glimmerGaussian} returns 0 and {@code glimmerShade} is an exact no-op,
 * so the title renders in its plain colors the rest of its on-screen life.
 *
 * <p>({@link #brighten} -- a plain lerp toward white -- is NOT the title glimmer anymore; it has no live
 * caller now (the loading wave moved to its own dim-baseline shade), and is retained only as a small
 * tested colour primitive.)
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

    /** The widest outline the "Outline Thickness" slider allows (screen pixels). */
    public static final int MAX_OUTLINE_THICKNESS = 4;

    /**
     * Precomputed outline offset sets, indexed by {@code thickness-1} (thickness 1..{@link
     * #MAX_OUTLINE_THICKNESS}). For thickness {@code t} the set is EVERY integer offset in the square
     * {@code [-t,t] x [-t,t]} except the origin -- i.e. all offsets whose Chebyshev distance is {@code <= t}.
     * The full square (not just the outer hull) is used on purpose: MC's blocky pixel font leaves visible
     * diagonal HOLES in a hull-only ring at {@code t >= 2}, so the classic correct "stamp the whole
     * neighbourhood" fill is what reads as a solid outline. Cost is honest but bounded -- the stamp count is
     * {@code (2t+1)^2 - 1} (8 at t=1, 24 at t=2, 48 at t=3, 80 at t=4), and each stamp is one full glyph-run
     * pass, so a t=4 outline draws the word 80 times per frame. That is only paid while a title is on screen
     * (a few seconds), for a handful of glyph runs, so it is acceptable for this ephemeral overlay; the arrays
     * are built once here (no per-frame allocation). */
    private static final int[][][] OUTLINE_OFFSETS_BY_THICKNESS = buildOutlineOffsetsByThickness();

    private static int[][][] buildOutlineOffsetsByThickness() {
        int[][][] all = new int[MAX_OUTLINE_THICKNESS][][];
        for (int t = 1; t <= MAX_OUTLINE_THICKNESS; t++) {
            java.util.List<int[]> offs = new java.util.ArrayList<>();
            for (int dy = -t; dy <= t; dy++) {
                for (int dx = -t; dx <= t; dx++) {
                    if (dx == 0 && dy == 0) continue; // (0,0) is the main fill, not an outline stamp
                    offs.add(new int[]{dx, dy});
                }
            }
            all[t - 1] = offs.toArray(new int[0][]);
        }
        return all;
    }

    /**
     * The cached outline offset set for a given thickness in SCREEN pixels; {@code thickness} is clamped to
     * {@code [1, MAX_OUTLINE_THICKNESS]}. Returns a shared, pre-built array (do not mutate). At thickness 1
     * this is the same 8-neighbour set as {@link #OUTLINE_OFFSETS_8} (order may differ; the renderer stamps
     * all of them, so order is irrelevant).
     */
    public static int[][] outlineOffsets(int thickness) {
        int t = thickness < 1 ? 1 : Math.min(thickness, MAX_OUTLINE_THICKNESS);
        return OUTLINE_OFFSETS_BY_THICKNESS[t - 1];
    }

    /** Diffuse-shadow-glow ring radii in SCREEN pixels: a soft dark halo is built by stamping the text in
     *  low-alpha black at each of the 8 directions, once per ring, radiating outward. Outer rings are fainter
     *  ({@link #GLOW_RING_ALPHA}), which is what makes the halo read as a soft glow rather than a hard edge. */
    public static final float[] GLOW_RING_RADII_PX = {1.5f, 3.0f, 4.5f};

    /** Per-ring alpha as a fraction of the title's own alpha, matched 1:1 to {@link #GLOW_RING_RADII_PX}
     *  (nearest ring strongest, outermost faintest). Kept low on purpose so the glow is a whisper, not a
     *  drop shadow. */
    public static final float[] GLOW_RING_ALPHA = {0.16f, 0.10f, 0.05f};

    /** Hard ceiling on any single glow ring's effective alpha (a fraction of the title's own alpha), applied
     *  AFTER the user's Glow Intensity multiply. Keeps the halo a soft glow even at max intensity -- above
     *  ~0.5 per ring the "glow" starts reading as a solid black box behind the text rather than a whisper. */
    public static final float GLOW_RING_ALPHA_CAP = 0.5f;

    /**
     * The effective per-ring glow alpha (fraction of the title's own alpha) after the user's Glow Intensity
     * multiply and the {@link #GLOW_RING_ALPHA_CAP} clamp: {@code min(GLOW_RING_ALPHA[ring] * intensity,
     * CAP)}, never negative. {@code intensity} is the config's {@code zoneEnterTitleGlowIntensity} (slider
     * range 0.2..2.0); a ring index outside {@link #GLOW_RING_ALPHA} returns 0. Pure + testable so the halo
     * math stays out of the renderer. At the shipped alphas the cap is never actually reached (0.16 * 2.0 =
     * 0.32 &lt; 0.5) -- it is a forward guard so a future louder base alpha can't turn the glow into a box.
     */
    public static float glowRingAlpha(int ring, double intensity) {
        if (ring < 0 || ring >= GLOW_RING_ALPHA.length) {
            return 0f;
        }
        double a = GLOW_RING_ALPHA[ring] * intensity;
        if (a < 0.0) a = 0.0;
        if (a > GLOW_RING_ALPHA_CAP) a = GLOW_RING_ALPHA_CAP;
        return (float) a;
    }

    /** First tick (title age, 0 = the frame it appeared) at which the glimmer crest starts moving. Slightly
     *  after fade-in begins (fade-in is {@code ZoneEnterTitleOverlay.FADE_TICKS}=10 ticks) so the title is
     *  already legible as the crest crosses it. */
    public static final int GLIMMER_START_TICK = 2;

    /** Number of ticks the single crest takes to sweep the whole word (~0.7s at 20 tps): a rapid, crisp wave,
     *  noticeably quicker than the old fade-in shimmer. After {@code GLIMMER_START_TICK + GLIMMER_SPAN_TICKS}
     *  the glimmer is done (never loops) and the title renders in its plain colors. */
    public static final int GLIMMER_SPAN_TICKS = 14;

    /** Baseline dim, as a fraction of a letter's own brightness, for letters far from the shine crest while the
     *  sweep is running: at the crest ({@code gaussian01==1}) a letter is at full brightness (factor 1.0), far
     *  from it ({@code gaussian01->0}) it settles to this floor. Chosen GENTLE (0.75) on purpose -- the whole
     *  point is a bright crest against a SOFTLY darker baseline, so the moving glint reads even on a near-white
     *  fill; a much darker floor would read as an ugly flicker rather than a shine. And because the dim only
     *  exists during the one-shot ~0.7s sweep (which rides the title's fade-in), a soft 25% dip is a shine, not
     *  a flash. See {@link #glimmerShade}. */
    public static final float GLIMMER_DIM_FLOOR = 0.75f;

    /** Specular white-pop at the crest: after the baseline dim, a letter is lerped toward pure white by
     *  {@code gaussian01 * GLIMMER_WHITE_POP}, so the exact crest gets a crisp bright glint (near-white on a
     *  light fill, a bright pink/white flash on a saturated one) that tapers off on either side. This is what
     *  lets a SATURATED color also flash bright -- you cannot out-brighten white, but you can pop the crest
     *  toward white against dimmed neighbours. See {@link #glimmerShade}. */
    public static final float GLIMMER_WHITE_POP = 0.70f;

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
     * Raw normalized crest height in {@code [0, 1]} for one letter, given normalized sweep progress: {@code 1}
     * at the crest centre, tapering to {@code ~0} far from it. A single Gaussian crest travels left&rarr;right
     * as {@code progress} runs 0&rarr;1 (the travelling-Gaussian idiom borrowed from the atlas bands) -- one
     * pass, never looping. This is {@code glimmerBoost}'s old Gaussian WITHOUT any amplitude multiply; feed the
     * result straight into {@link #glimmerShade} on the letter's OWN color to get the shine-sweep transform.
     *
     * @param progress 0..1 through the glimmer window; any value {@code < 0} means "no glimmer" and returns 0
     *                 (used for solid presets with the toggle off, Reduce Motion, and the static Studio preview).
     *                 When this returns 0, {@link #glimmerShade} is an exact no-op, so the title is its plain color.
     */
    public static float glimmerGaussian(float progress, int visibleIdx, int visibleCount) {
        if (progress < 0f) {
            return 0f;
        }
        int count = Math.max(1, visibleCount);
        float p = count <= 1 ? 0f : (float) visibleIdx / (float) (count - 1);
        float crest = -GLIMMER_MARGIN + progress * (1f + 2f * GLIMMER_MARGIN);
        float d = p - crest;
        return (float) Math.exp(-(d * d) / (2f * GLIMMER_SIGMA * GLIMMER_SIGMA));
    }

    /**
     * The shine-sweep color transform for one letter: turns a raw crest height ({@link #glimmerGaussian}'s
     * {@code [0,1]} output) into the letter's rendered {@code 0xRRGGBB}. Two stages, chosen so the shine reads
     * on ANY fill -- including a near-white title you cannot make brighter:
     * <ol>
     *   <li>Baseline dim by {@code factor = GLIMMER_DIM_FLOOR + (1 - GLIMMER_DIM_FLOOR) * gaussian01} (each
     *       channel multiplied): letters far from the crest sit at the gentle {@link #GLIMMER_DIM_FLOOR}; the
     *       crest letter is back at full brightness.</li>
     *   <li>Specular white-pop: lerp the dimmed color toward pure white by {@code gaussian01 * GLIMMER_WHITE_POP},
     *       so the crest gets a crisp bright glint that tapers off either side.</li>
     * </ol>
     * Net effect: a bright crest travelling against a briefly, softly dimmed baseline -- a visible moving shine
     * on off-white (dimmed neighbours vs a near-white crest glint) AND on a saturated fill (dark-red neighbours
     * vs a bright pink-white crest).
     *
     * <p>Alpha is not part of {@code rgb}; the caller ORs it back (same contract as {@link #brighten}).
     * {@code gaussian01 <= 0} is an EXACT no-op returning {@code rgb & 0xFFFFFF} unchanged -- so outside the
     * sweep window (where {@code glimmerGaussian} returns a hard 0) the title renders at its full, undimmed base
     * color, never dimmed.
     */
    public static int glimmerShade(int rgb, float gaussian01) {
        if (gaussian01 <= 0f) {
            return rgb & 0xFFFFFF;
        }
        float g = gaussian01 > 1f ? 1f : gaussian01; // clamp; a crest height above 1 is meaningless
        float factor = GLIMMER_DIM_FLOOR + (1f - GLIMMER_DIM_FLOOR) * g;
        float pop = g * GLIMMER_WHITE_POP; // lerp-toward-white fraction at this letter
        int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
        int r = shadeChannel(r0, factor, pop);
        int gg = shadeChannel(g0, factor, pop);
        int b = shadeChannel(b0, factor, pop);
        return (r << 16) | (gg << 8) | b;
    }

    /** One channel of {@link #glimmerShade}: dim by {@code factor}, then lerp the dimmed value toward 255 by
     *  {@code pop}. Result is rounded and clamped to {@code [0, 255]} (the lerp toward 255 cannot overshoot, and
     *  {@code factor <= 1} cannot either, so the clamp is just defensive). */
    private static int shadeChannel(int c, float factor, float pop) {
        float dimmed = c * factor;
        float lit = dimmed + (255f - dimmed) * pop;
        int v = Math.round(lit);
        return v < 0 ? 0 : (v > 255 ? 255 : v);
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
