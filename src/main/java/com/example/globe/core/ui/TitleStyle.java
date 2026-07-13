package com.example.globe.core.ui;

/**
 * Pure math + constants for the zone-enter / hemisphere title's outline, diffuse-shadow-glow, and the
 * one-shot GLIMMER wave (title-styling overhaul 2026-07-11). No rendering dependencies live here so the
 * offset geometry and the glimmer envelope are unit-testable; {@code ZoneEnterTitleOverlay} is the thin glue
 * that turns these numbers into {@code ctx.text} passes.
 *
 * <p>The glimmer is a one-shot, four-phase ENVELOPE keyed on the title's wall-clock age in ms ("C v2"
 * choreography, approved 2026-07-11 1:1): a brief APPEAR (fade-in only), a HERO crest sweep left&rarr;right,
 * a whole-title BLOOM toward white with a small scale swell, then a slow MELT back to plain. {@link
 * #glimmerFrame} returns one {@link GlimmerFrame} per frame carrying the phase inputs; outside the window it
 * returns {@link GlimmerFrame#INERT} so the title renders plain. Within the HERO sweep the crest is one
 * travelling Gaussian rendered as RELATIVE CONTRAST rather than an absolute brighten: {@link #glimmerGaussian}
 * gives the raw crest height in [0,1] per letter; {@link #glimmerShade} turns that into the per-letter color --
 * letters away from the crest are GENTLY DIMMED to {@link #GLIMMER_DIM_FLOOR} of their brightness, the crest
 * letter is lifted back to full and popped toward white. The moving bright crest against a briefly dimmed
 * baseline is what makes the shine read on ANY fill -- including a near-white title like the OFF_WHITE default
 * (0xF3ECDD), where you cannot make white brighter than white, so instead the surroundings dim and the crest
 * pops. It fires exactly once and never loops; outside the sweep {@code glimmerGaussian} returns 0 and
 * {@code glimmerShade} is an exact no-op, so the title renders in its plain colors the rest of its life.
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

    /** Faded drop-shadow stamp offsets in SCREEN pixels (down &amp; down-right): a soft DIRECTIONAL shadow, built
     *  by stamping the text in low-alpha black at each offset behind the fill. Two things make this a distinct
     *  look. Unlike the omnidirectional {@link #GLOW_RING_RADII_PX} halo (8 directions radiating out from the
     *  glyph, centered), these offsets go to the lower-right ONLY, so the title reads as lit from the
     *  upper-left -- a real cast shadow, not a glow. And unlike MC's stark hard single-pixel vanilla shadow,
     *  the two tapering-alpha stamps ({@link #DROP_SHADOW_ALPHA}) make it soft and faded rather than a crisp
     *  black edge. Expressed in screen px (the caller divides by draw scale) so it stays a fixed offset at any
     *  Title Size. */
    public static final int[][] DROP_SHADOW_OFFSETS_PX = {
        {1, 1}, {2, 2},
    };

    /** Per-stamp alpha as a fraction of the title's own alpha, matched 1:1 to {@link #DROP_SHADOW_OFFSETS_PX}
     *  (nearest stamp strongest, farther one fainter). Kept low + tapering on purpose so the shadow reads as a
     *  soft, faded directional cast rather than the stark hard vanilla drop shadow. */
    public static final float[] DROP_SHADOW_ALPHA = {0.35f, 0.18f};

    // ---------------------------------------------------------------------------------------------------
    // Phase-envelope glimmer choreography ("C v3", 2026-07-12: stronger + configurable + a FAREWELL sweep).
    // Base HERO/BLOOM timings stay the "C v2" 1:1 numbers (approved 2026-07-11); v3 ADDS one fast second crest
    // sweep right before the melt, and a Glimmer Strength (intensity) scalar that scales the relative-contrast
    // mechanism (dim depth + crest pop TOGETHER), never a brighten-toward-target.
    //
    // A five-phase one-shot envelope keyed on the title's wall-clock age in ms:
    //   APPEAR   [0 .. GLIMMER_APPEAR_MS)                     -- the fade-in runs; no glimmer activity (inert).
    //   HERO     [GLIMMER_APPEAR_MS .. GLIMMER_HERO_END_MS)   -- one L->R Gaussian crest (crestProgress 0->1),
    //            pop = min(1, GLIMMER_HERO_POP * intensity), against a raised-arch baseline dim
    //            (dimScale = sin(pi*p) * intensity; deeper dim = deeper contrast). ~900ms, the hero shine.
    //   BLOOM    [GLIMMER_HERO_END_MS .. GLIMMER_BLOOM_END_MS)   -- whole title lerps uniformly toward white to
    //            GLIMMER_BLOOM_PEAK (easeOutQuad) plus a GLIMMER_SWELL_PEAK scale swell.
    //   FAREWELL [GLIMMER_BLOOM_END_MS .. GLIMMER_FAREWELL_END_MS) -- a FAST second L->R crest (~500ms, visibly
    //            quicker than the 900ms hero), same envelope math + same intensity scaling, with bloom + swell
    //            HELD at their peak so the title stays present while the parting glint travels. "One more quick
    //            glimmer" (Peetsa, 2026-07-12) before the fade.
    //   MELT     [GLIMMER_FAREWELL_END_MS .. GLIMMER_MELT_END_MS)   -- bloom + swell decay 0.65/0.02 -> 0
    //            (easeOutCubic decay), a slow graceful dissolve. Same 850ms duration/shape as v2, just delayed by
    //            the farewell.
    //   REST     [GLIMMER_MELT_END_MS .. )                    -- plain title (inert), normal hold + fade-out.
    // Total glimmer lifetime v2 -> v3: 2350ms -> 2850ms (+500ms, the farewell; the melt itself is unchanged).
    // At intensity == 1.0 the HERO/BLOOM/MELT frames are byte-identical to the v2 contract (the farewell is the
    // only always-present addition). Supersedes the old tick-window sweep.
    // ---------------------------------------------------------------------------------------------------

    /** End of APPEAR / start of the HERO sweep (ms since the title appeared). Before this the title only fades
     *  in; no glimmer activity. */
    public static final long GLIMMER_APPEAR_MS = 350L;
    /** End of the HERO crest sweep / start of BLOOM (ms). The single L->R crest travels the whole
     *  [APPEAR, HERO_END) window (~900ms -- the hero shine). */
    public static final long GLIMMER_HERO_END_MS = 1250L;
    /** End of BLOOM / start of the FAREWELL sweep (ms). Bloom and swell reach their peak exactly here. */
    public static final long GLIMMER_BLOOM_END_MS = 1500L;
    /** End of the FAREWELL quick sweep / start of MELT (ms). NEW (glimmer v3). The farewell is a fast ~500ms
     *  second crest (vs the hero's ~900ms) that rides over the peak-held bloom; after it, the melt begins as in
     *  v2. */
    public static final long GLIMMER_FAREWELL_END_MS = 2000L;
    /** End of MELT / start of REST (ms). After this the frame is inert and the title is plain again. The MELT
     *  window ({@link #GLIMMER_FAREWELL_END_MS}..here) keeps its v2 850ms duration; only its START moved later by
     *  the farewell. */
    public static final long GLIMMER_MELT_END_MS = 2850L;

    /** Default Glimmer Strength (the "C v3" intensity scalar; HUD Studio slider range
     *  {@link #GLIMMER_INTENSITY_MIN}..{@link #GLIMMER_INTENSITY_MAX}). 1.3 is deliberately stronger than the
     *  1.0 that reproduces the old v2 contract (Peetsa 2026-07-12: "a stronger glimmer on the title"). */
    public static final double GLIMMER_INTENSITY_DEFAULT = 1.3;
    /** Lower bound of the Glimmer Strength slider: a gentle glimmer (shallow dim, soft crest). */
    public static final double GLIMMER_INTENSITY_MIN = 0.5;
    /** Upper bound of the Glimmer Strength slider: a bold glimmer (deep dim, full-white crest). */
    public static final double GLIMMER_INTENSITY_MAX = 2.0;

    /** White-pop at the HERO crest (approved 0.85 -- stronger than the plain {@link #GLIMMER_WHITE_POP} 0.70
     *  baked into the 2/3-arg {@link #glimmerShade}); carried on the frame and fed to the 4-arg glimmerShade. */
    public static final float GLIMMER_HERO_POP = 0.85f;
    /** Peak of BLOOM's uniform whole-title lerp toward white, reached at {@link #GLIMMER_BLOOM_END_MS}. */
    public static final float GLIMMER_BLOOM_PEAK = 0.65f;
    /** Peak of BLOOM's scale swell (a 2% title-scale swell), reached at {@link #GLIMMER_BLOOM_END_MS}. */
    public static final float GLIMMER_SWELL_PEAK = 0.02f;

    /**
     * One frame of the glimmer choreography: the per-letter HERO crest inputs ({@code crestProgress},
     * {@code dimScale}, {@code pop}) plus the whole-title {@code bloom} (uniform lerp-toward-white amount) and
     * {@code swell} (extra scale fraction). Outside the active windows every field is inert ({@link #INERT}):
     * {@code crestProgress == -1} (no crest -- {@link #glimmerGaussian} returns 0 and {@link #glimmerShade} is an
     * exact no-op) and {@code dimScale/pop/bloom/swell == 0} (no dim, no bloom lift, no swell), so the title
     * renders in its plain colors at its plain scale.
     */
    public record GlimmerFrame(float crestProgress, float dimScale, float pop, float bloom, float swell) {
        /** The inert frame -- outside every window and when the effect is gated off (toggle / Reduce Motion). */
        public static final GlimmerFrame INERT = new GlimmerFrame(-1f, 0f, 0f, 0f, 0f);
    }

    /**
     * The glimmer envelope for a title that appeared {@code elapsedMs} ago (wall-clock), at the DEFAULT strength
     * (intensity 1.0 -- the v2 contract). Equivalent to {@link #glimmerFrame(long, double) glimmerFrame(elapsedMs,
     * 1.0)}; retained for the static Studio preview and tests that don't vary strength.
     */
    public static GlimmerFrame glimmerFrame(long elapsedMs) {
        return glimmerFrame(elapsedMs, 1.0);
    }

    /**
     * The glimmer envelope for a title that appeared {@code elapsedMs} ago (wall-clock), scaled by the Glimmer
     * Strength {@code intensity}. Returns {@link GlimmerFrame#INERT} during the pre-sweep APPEAR phase and after
     * MELT ends; a HERO frame (crest + dim + pop) during the hero sweep; a BLOOM frame (bloom + swell, no crest);
     * a FAREWELL frame (a fast second crest + dim + pop, with bloom + swell HELD at peak); and a MELT frame
     * (bloom + swell decay, no crest). Pure + testable; callers apply their own gates (config toggle, Reduce
     * Motion) on top and substitute {@link GlimmerFrame#INERT} when gated off.
     *
     * <p>{@code intensity} scales the RELATIVE-CONTRAST mechanism, not a brighten-toward-target: it deepens the
     * off-crest baseline dim (the frame's {@code dimScale} = arch * intensity, so downstream the floor drops
     * further below {@link #GLIMMER_DIM_FLOOR}) AND lifts the crest pop ({@code pop} = min(1, {@link
     * #GLIMMER_HERO_POP} * intensity)) TOGETHER. At {@code intensity == 1.0} the HERO/BLOOM/MELT frames are
     * byte-identical to the v2 contract; the FAREWELL is present at every intensity. {@code intensity} below 0 is
     * treated as 0 (no glimmer contrast, just the bloom/swell); the caller clamps to the slider's
     * {@link #GLIMMER_INTENSITY_MIN}..{@link #GLIMMER_INTENSITY_MAX} range.
     */
    public static GlimmerFrame glimmerFrame(long elapsedMs, double intensity) {
        if (elapsedMs < GLIMMER_APPEAR_MS || elapsedMs >= GLIMMER_MELT_END_MS) {
            return GlimmerFrame.INERT;
        }
        float in = (float) (Double.isNaN(intensity) || intensity < 0.0 ? 0.0 : intensity);
        float pop = GLIMMER_HERO_POP * in;
        if (pop > 1f) pop = 1f; // the crest cannot go past pure white -- extra strength lives in the deeper dim
        if (elapsedMs < GLIMMER_HERO_END_MS) {
            float p = (float) (elapsedMs - GLIMMER_APPEAR_MS) / (float) (GLIMMER_HERO_END_MS - GLIMMER_APPEAR_MS);
            float dim = (float) Math.sin(Math.PI * p) * in; // raised arch (0 at both sweep ends), scaled by strength
            return new GlimmerFrame(p, dim, pop, 0f, 0f);
        }
        if (elapsedMs < GLIMMER_BLOOM_END_MS) {
            float local = (float) (elapsedMs - GLIMMER_HERO_END_MS)
                    / (float) (GLIMMER_BLOOM_END_MS - GLIMMER_HERO_END_MS);
            float ease = easeOutQuad(local);
            return new GlimmerFrame(-1f, 0f, 0f, GLIMMER_BLOOM_PEAK * ease, GLIMMER_SWELL_PEAK * ease);
        }
        if (elapsedMs < GLIMMER_FAREWELL_END_MS) {
            // FAREWELL: a fast second crest sweep over the peak-held bloom, then the melt begins. Same envelope
            // math as HERO (a travelling Gaussian crest against a raised-arch dim), just over a shorter window,
            // so it reads as visibly quicker. Bloom + swell stay at their peak so the title is still fully
            // present while the parting glint travels.
            float p = (float) (elapsedMs - GLIMMER_BLOOM_END_MS)
                    / (float) (GLIMMER_FAREWELL_END_MS - GLIMMER_BLOOM_END_MS);
            float dim = (float) Math.sin(Math.PI * p) * in;
            return new GlimmerFrame(p, dim, pop, GLIMMER_BLOOM_PEAK, GLIMMER_SWELL_PEAK);
        }
        float local = (float) (elapsedMs - GLIMMER_FAREWELL_END_MS)
                / (float) (GLIMMER_MELT_END_MS - GLIMMER_FAREWELL_END_MS);
        float dec = 1f - easeOutCubic(local); // = (1-local)^3: 1 at melt start, 0 at melt end
        return new GlimmerFrame(-1f, 0f, 0f, GLIMMER_BLOOM_PEAK * dec, GLIMMER_SWELL_PEAK * dec);
    }

    /** Quadratic ease-out {@code 1-(1-t)^2}, {@code t} clamped to [0,1]: rises fast then settles (the BLOOM lift). */
    static float easeOutQuad(float t) {
        float u = 1f - (t < 0f ? 0f : (t > 1f ? 1f : t));
        return 1f - u * u;
    }

    /** Cubic ease-out {@code 1-(1-t)^3}, {@code t} clamped to [0,1]. The MELT decays as {@code 1-easeOutCubic}. */
    static float easeOutCubic(float t) {
        float u = 1f - (t < 0f ? 0f : (t > 1f ? 1f : t));
        return 1f - u * u * u;
    }

    /** Baseline dim, as a fraction of a letter's own brightness, for letters far from the shine crest while the
     *  sweep is running: at the crest ({@code gaussian01==1}) a letter is at full brightness (factor 1.0), far
     *  from it ({@code gaussian01->0}) it settles to this floor. Chosen GENTLE (0.75) on purpose -- the whole
     *  point is a bright crest against a SOFTLY darker baseline, so the moving glint reads even on a near-white
     *  fill; a much darker floor would read as an ugly flicker rather than a shine. And because the dim only
     *  exists during the one-shot ~0.7s sweep (which rides the title's fade-in), a soft 25% dip is a shine, not
     *  a flash. See {@link #glimmerShade}. */
    public static final float GLIMMER_DIM_FLOOR = 0.75f;

    /** Upper clamp on {@code dimScale} in {@link #glimmerShade} / {@link #dimToFloor}. v2's dimScale was the
     *  raised arch alone (always in [0,1]); v3's Glimmer Strength feeds {@code arch * intensity}, which at the
     *  slider max ({@link #GLIMMER_INTENSITY_MAX} = 2.0) reaches ~2.0 and deepens the baseline dim BELOW
     *  {@link #GLIMMER_DIM_FLOOR}. This 4.0 cap leaves headroom past the slider while keeping the effective floor
     *  {@code 1 - (1-GLIMMER_DIM_FLOOR)*dimScale} at or above 0 (it reaches 0 exactly at dimScale 4). At
     *  {@code dimScale <= 1} the behaviour is byte-identical to v2. */
    public static final float GLIMMER_DIM_SCALE_MAX = 4.0f;

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
        return glimmerShade(rgb, gaussian01, 1f, GLIMMER_WHITE_POP);
    }

    /**
     * As {@link #glimmerShade(int, float)}, but with an explicit {@code dimScale} in {@code [0,1]} that scales
     * how deep the off-crest baseline dim goes -- so the caller can ease the dim IN at the start of the sweep
     * and OUT at the end instead of snapping it on/off.
     *
     * <p>Why this exists: the baseline dim ({@link #GLIMMER_DIM_FLOOR}) is what a bright crest travels against,
     * but a FIXED dim has two visible discontinuities. At the sweep's END, {@link #glimmerGaussian} snaps to 0
     * and the bulk of the word jumps {@code GLIMMER_DIM_FLOOR -> 1.0} in a single frame -- a "brighten pop"
     * (part of the reported appears/half-vanishes/returns). At the START the full dim fights the title's
     * still-ramping fade-in. Feeding {@code dimScale} a raised arch (0 at both sweep ends, ~1 mid-sweep) removes
     * both: the dim is absent exactly where the crest is entering/exiting (near the word edges, where there is
     * nothing to contrast anyway), and full only mid-word where the crest actually needs it. {@code dimScale}
     * does NOT touch the crest white-pop, so the moving glint still reads from the first frame.
     *
     * <p>{@code dimScale} is clamped to {@code [0, }{@link #GLIMMER_DIM_SCALE_MAX}{@code ]}; at
     * {@code dimScale == 1} this is identical to the two-arg {@link #glimmerShade(int, float)} (the original
     * fixed-dim behaviour), at {@code dimScale == 0} the baseline is undimmed (factor 1.0) so only the crest's
     * white-pop shows, and at {@code dimScale > 1} (the v3 Glimmer Strength deepening the contrast) the baseline
     * dims BELOW {@link #GLIMMER_DIM_FLOOR}, with the effective floor clamped to {@code >= 0}. {@code gaussian01
     * <= 0} is still an EXACT no-op returning {@code rgb & 0xFFFFFF} unchanged, regardless of {@code dimScale}.
     */
    public static int glimmerShade(int rgb, float gaussian01, float dimScale) {
        return glimmerShade(rgb, gaussian01, dimScale, GLIMMER_WHITE_POP);
    }

    /**
     * As {@link #glimmerShade(int, float, float)}, but with an explicit {@code whitePop} in {@code [0,1]} that
     * sets how far the crest letter is lerped toward white ({@code gaussian01 * whitePop}), instead of the fixed
     * {@link #GLIMMER_WHITE_POP}. The phase envelope's HERO frame passes {@link #GLIMMER_HERO_POP} (0.85) here so
     * the appearing crest pops harder than the plain 0.70 baked into the 2/3-arg overloads (which the loading
     * wave and the legacy callers keep using). Everything else is identical: {@code gaussian01 <= 0} is still an
     * EXACT no-op, {@code dimScale} still eases the baseline dim, and the result stays in {@code [0,255]}/channel.
     */
    public static int glimmerShade(int rgb, float gaussian01, float dimScale, float whitePop) {
        if (gaussian01 <= 0f) {
            return rgb & 0xFFFFFF;
        }
        float g = gaussian01 > 1f ? 1f : gaussian01; // clamp; a crest height above 1 is meaningless
        float ds = dimScale < 0f ? 0f : (dimScale > GLIMMER_DIM_SCALE_MAX ? GLIMMER_DIM_SCALE_MAX : dimScale);
        float wp = whitePop < 0f ? 0f : (whitePop > 1f ? 1f : whitePop);
        // Effective dim floor eases from 1.0 (no dim, ds=0) down to GLIMMER_DIM_FLOOR at ds=1 (v2), and BELOW it
        // for ds>1 (v3 Glimmer Strength deepening the contrast); clamped to >=0 so it never goes negative.
        float floor = 1f - (1f - GLIMMER_DIM_FLOOR) * ds;
        if (floor < 0f) floor = 0f;
        float factor = floor + (1f - floor) * g;
        float pop = g * wp; // lerp-toward-white fraction at this letter (independent of the dim)
        int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
        int r = shadeChannel(r0, factor, pop);
        int gg = shadeChannel(g0, factor, pop);
        int b = shadeChannel(b0, factor, pop);
        return (r << 16) | (gg << 8) | b;
    }

    /**
     * Applies ONLY the shine-sweep's baseline dim (no crest, no white-pop) to a color: each channel multiplied
     * by {@code floor = 1 - (1 - GLIMMER_DIM_FLOOR) * dimScale}. This is how a NON-hero line -- the degrees line
     * of the two-line lockup, which never gets its own crest -- still sits at the same dimmed baseline the hero
     * line's crest travels against, so the whole lockup breathes together. {@code dimScale} is clamped to
     * {@code [0,1]}; at {@code dimScale == 0} this is an EXACT no-op (floor 1.0) returning {@code rgb & 0xFFFFFF}.
     * Alpha is not part of {@code rgb}; the caller ORs it back.
     */
    public static int dimToFloor(int rgb, float dimScale) {
        float ds = dimScale < 0f ? 0f : (dimScale > GLIMMER_DIM_SCALE_MAX ? GLIMMER_DIM_SCALE_MAX : dimScale);
        float floor = 1f - (1f - GLIMMER_DIM_FLOOR) * ds;
        if (floor < 0f) floor = 0f;
        int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
        int r = shadeChannel(r0, floor, 0f);
        int g = shadeChannel(g0, floor, 0f);
        int b = shadeChannel(b0, floor, 0f);
        return (r << 16) | (g << 8) | b;
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

    /** Degrees suffix token: an integer degree count, the degree sign, and an OPTIONAL hemisphere letter
     *  (N/S for latitude, E/W for longitude). Matches "66°N", "12°S", "180°E", "90°W", and
     *  the equator/prime-meridian "0°" (no letter). Deliberately conservative: only a last token of this
     *  exact shape is treated as a degrees suffix, so a hemisphere title like "Northern Hemisphere" (last word
     *  "Hemisphere") is never mistaken for a lockup. */
    private static final java.util.regex.Pattern DEGREES_TOKEN =
            java.util.regex.Pattern.compile("\\d+\\u00b0[NSEW]?");

    /**
     * Splits a zone-enter title into its two-line lockup parts: {@code [name, degrees]} when the title ends in a
     * degrees token (e.g. {@code "Subpolar 66°N"} &rarr; {@code {"Subpolar", "66°N"}}), or
     * {@code [title, null]} when it does not -- a single-token title, a degrees-off title, or a hemisphere title
     * like {@code "Northern Hemisphere"}. The split is at the LAST space, so multi-word zone names
     * ({@code "The Frozen Wastes 89°"}) keep the whole name on line 1. ONLY a last token matching
     * {@link #DEGREES_TOKEN} triggers a split; anything else (including titles with spaces but no degrees token)
     * stays a single line. Split the RAW (natural-case) title -- before any title-case transform -- so the
     * degrees letters keep their N/S/E/W casing for the match.
     */
    public static String[] splitLockup(String title) {
        if (title == null) {
            return new String[]{"", null};
        }
        int lastSpace = title.lastIndexOf(' ');
        if (lastSpace < 0 || lastSpace == title.length() - 1) {
            return new String[]{title, null};
        }
        String tail = title.substring(lastSpace + 1);
        if (DEGREES_TOKEN.matcher(tail).matches()) {
            return new String[]{title.substring(0, lastSpace), tail};
        }
        return new String[]{title, null};
    }
}
