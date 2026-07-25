package com.example.globe.core.ui;

/**
 * Pure envelope math for the loading screen's gentle "reading light" wave over the world-summary line
 * (Peetsa 2026-07-11: "a progressive illumination of each word like a gaussian gradient wave... gentle and
 * gradual... would soothe the eye from seeing so much visual clutter").
 *
 * <p>The summary line ("Itty Bitty · Square 1:1 · 7,500 × 7,500 · subpolar start") is split into its
 * {@code · }-separated SEGMENTS — the natural "elements" Peetsa named — and a single crest travels slowly and
 * continuously across the segment indices, wrapping forever. One segment glows gently brighter while the one
 * behind it eases back down; a soft Gaussian falloff gives adjacent segments a whisper of spillover, which is
 * what makes it read as a travelling wave rather than a blinking highlight.
 *
 * <p>Zero Minecraft dependencies so the feel is unit-testable. The caller ({@code
 * LevelLoadingScreenLatitudeOverlayMixin}) turns each segment's {@link #gaussian01} crest height into a color
 * via {@link #shade} and is responsible for freezing the wave (drawing the plain base line) under
 * {@code LatitudeConfig.reduceMotion} — this class is intentionally motion-policy-agnostic.
 *
 * <p>Design intent: this is a caress, not the title glimmer. But — like the title glimmer learned the hard
 * way — a warm-white line lerped <em>toward white</em> barely changes, so a "brighten only" wave is invisible.
 * The illumination is therefore a DIM-BASELINE + BRIGHT-CREST contrast (exactly Peetsa's original phrasing:
 * "illuminate each word, de-illuminate the word behind it"): every word rests at {@link #REST_DIM} of its
 * base brightness (the "waiting its turn" state) and the single crest word lifts back to full plus a gentle
 * white pop ({@link #CREST_POP}). Because there is always a crest somewhere, the resting dim is permanent
 * during loading — that's the point: un-lit words read as dimmed, and the light visibly travels across them.
 * {@link #boost} (the old lerp-toward-white amount) is retained only for callers that still want it; the
 * loading overlay uses {@link #gaussian01} + {@link #shade}.
 */
public final class LoadingWave {

    private LoadingWave() {
    }

    /** Milliseconds the crest spends travelling across ONE segment. At ~1.5 s/segment a 4-segment line completes
     *  a full loop every ~6 s — squarely in the "slow and soothing" band Peetsa asked for (a full cycle every
     *  ~5–7 s). Slow enough that the eye reads it as a gentle drift, not a pulse. */
    public static final long SEGMENT_PERIOD_MS = 1500L;

    /** Peak brightness lift at the exact crest, as the {@code boost} fed to {@link TitleStyle#brighten}
     *  (a lerp of each channel toward white). 0.30 ⇒ the crest word sits ~30% of the way to white — a gentle
     *  modest lift that keeps the base warm-white text fully legible; deliberately far below the title
     *  glimmer's brilliance. */
    public static final float AMPLITUDE = 0.30f;

    /** Gaussian width in SEGMENT units. Controls how far the crest's glow spills onto its neighbours. At 0.60 an
     *  immediately-adjacent segment (distance 1) receives {@code exp(-1/(2·0.36)) ≈ 0.25} of the crest lift —
     *  a whisper (~25% of the dominant), enough to read as a continuous wave, not enough to muddy which single
     *  segment is dominant. Segments two away get {@code ≈ 0.4%} — effectively dark. */
    public static final float SIGMA = 0.60f;

    /** The "de-illuminated" resting brightness a word sits at when no crest is on it, as a fraction of its base
     *  color (each channel × REST_DIM). 0.75 ⇒ a resting word is clearly dimmer than the crest word but stays
     *  legible — a calm floor, not muddy. This is PERMANENT during loading (there is always a crest somewhere,
     *  so the un-lit words always read as "waiting their turn"). Deliberately gentle: this is a soothing loop,
     *  not the title's punchy one-shot. See {@link #shade}. */
    public static final float REST_DIM = 0.75f;

    /** Specular white-pop at the crest, after the baseline lift back to full: the crest color is lerped toward
     *  pure white by {@code gaussian01 × CREST_POP}. 0.40 is a gentle sparkle (well below the title glimmer's
     *  0.70), enough that the illuminated word reads as "lit" against its dimmed neighbours without glaring in
     *  a loop that runs for the whole load. See {@link #shade}. */
    public static final float CREST_POP = 0.40f;

    /**
     * Gentle per-segment brightness boost at wall-clock time {@code timeMs}, in {@code [0, AMPLITUDE]}.
     *
     * <p>A single crest position advances continuously through segment index space at one segment per
     * {@link #SEGMENT_PERIOD_MS} and wraps modulo {@code segmentCount}, so the wave loops seamlessly for as long
     * as the loading screen shows. Distance from a segment to the crest is measured CIRCULARLY (the shorter way
     * around the ring), so the spillover wraps too: when the crest sits on the last segment, the first segment
     * still catches its whisper, and the loop has no visible seam.
     *
     * @param segmentIdx   index of this segment, {@code 0 <= segmentIdx < segmentCount}
     * @param segmentCount total number of illuminated segments (>= 1)
     * @param timeMs       monotonic wall-clock time in ms (e.g. elapsed since overlay start)
     * @return brightness boost in {@code [0, AMPLITUDE]} to pass to {@link TitleStyle#brighten}
     */
    public static float boost(int segmentIdx, int segmentCount, long timeMs) {
        return AMPLITUDE * gaussian01(segmentIdx, segmentCount, timeMs);
    }

    /**
     * The NORMALIZED crest height at a segment in {@code [0, 1]} — the same Gaussian envelope {@link #boost}
     * uses, but without the {@link #AMPLITUDE} multiply, so callers that colour segments themselves (via
     * {@link #shade}) get the raw crest fraction: {@code 1} at the exact crest, easing to {@code 0} away from
     * it. {@code boost == AMPLITUDE × gaussian01} by construction, so the two never disagree about where the
     * crest is.
     *
     * <p>Everything else matches {@link #boost}: a single crest advances one segment per {@link
     * #SEGMENT_PERIOD_MS}, wrapping modulo {@code segmentCount}, and distance is measured CIRCULARLY so the
     * spillover wraps seamlessly. A lone segment ({@code segmentCount == 1}) has no neighbour to hand off to, so
     * it BREATHES {@code 0→1→0} on a slow cosine instead of pinning at a crest.
     *
     * @param segmentIdx   index of this segment, {@code 0 <= segmentIdx < segmentCount}
     * @param segmentCount total number of illuminated segments (>= 1)
     * @param timeMs       monotonic wall-clock time in ms (e.g. elapsed since overlay start)
     * @return crest height in {@code [0, 1]} to pass to {@link #shade}
     */
    public static float gaussian01(int segmentIdx, int segmentCount, long timeMs) {
        int count = Math.max(1, segmentCount);
        if (count == 1) {
            // A lone segment has no neighbour to hand off to; give it a slow, gentle breathing lift rather than
            // a hard-on crest, so a single-element summary still feels alive but never blinks.
            double phase = (timeMs % (SEGMENT_PERIOD_MS * 2L)) / (double) (SEGMENT_PERIOD_MS * 2L);
            float breathe = (float) (0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI)); // 0→1→0, smooth
            return clamp01(breathe);
        }
        long cycleMs = SEGMENT_PERIOD_MS * count;
        double phase = (timeMs % cycleMs) / (double) SEGMENT_PERIOD_MS; // crest position in [0, count)
        double raw = Math.abs(segmentIdx - phase);
        double d = Math.min(raw, count - raw); // circular distance around the ring of segments
        float g = (float) Math.exp(-(d * d) / (2.0 * SIGMA * SIGMA));
        return clamp01(g);
    }

    /**
     * Colours a segment from its {@link #gaussian01} crest height using a DIM-BASELINE + BRIGHT-CREST contrast
     * (Peetsa 2026-07-11: "illuminate each word, de-illuminate the word behind it"). This is what makes the
     * wave visible on a warm-white line, where a lerp-toward-white ({@link TitleStyle#brighten}) barely moves:
     *
     * <ol>
     *   <li>Lift factor {@code = REST_DIM + (1 - REST_DIM) · g}: at {@code g = 0} the word sits at {@link
     *       #REST_DIM}× its base (the de-illuminated resting state), at {@code g = 1} it returns to full base.</li>
     *   <li>Multiply each channel by that factor.</li>
     *   <li>Gentle white pop at the crest: lerp the result toward pure white by {@code g · CREST_POP}, so the
     *       lit word sparkles a touch above full base while resting words never do.</li>
     * </ol>
     *
     * <p>{@code shade(rgb, 0)} is therefore {@code REST_DIM × rgb} — intentionally NOT the identity: the resting
     * state is dimmed on purpose. {@code shade(rgb, 1)} is brighter than the base and clearly brighter than the
     * resting state. Alpha is not part of the input; the caller ORs it back in (matching {@link #brighten}).
     *
     * @param rgb        base colour as {@code 0xRRGGBB} (any alpha bits are ignored)
     * @param gaussian01 crest height in {@code [0, 1]} (clamped defensively)
     * @return shaded colour as {@code 0xRRGGBB}
     */
    public static int shade(int rgb, float gaussian01) {
        float g = clamp01(gaussian01);
        float factor = REST_DIM + (1f - REST_DIM) * g;
        float r = ((rgb >> 16) & 0xFF) * factor;
        float gr = ((rgb >> 8) & 0xFF) * factor;
        float b = (rgb & 0xFF) * factor;
        float pop = g * CREST_POP; // gentle white sparkle at the crest only
        r += (255f - r) * pop;
        gr += (255f - gr) * pop;
        b += (255f - b) * pop;
        int ri = clampByte(Math.round(r));
        int gi = clampByte(Math.round(gr));
        int bi = clampByte(Math.round(b));
        return (ri << 16) | (gi << 8) | bi;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return v > 1f ? 1f : v;
    }

    private static int clampByte(int v) {
        if (v < 0) {
            return 0;
        }
        return v > 255 ? 255 : v;
    }

    /**
     * Splits a summary line into its illuminated segments on the {@code · } separator (U+00B7 with a single
     * space on each side, as built in {@code LatitudeWorldLauncher}). Returns the original string as a single
     * element when no separator is present, so a caller can always iterate the result. The separators
     * themselves are NOT returned — the caller re-inserts them in the base color between segments so only the
     * words illuminate.
     */
    public static String[] segments(String summary) {
        if (summary == null || summary.isEmpty()) {
            return new String[0];
        }
        return summary.split(java.util.regex.Pattern.quote(SEPARATOR), -1);
    }

    /** The exact separator drawn between segments (never illuminated). Kept here so the split and the redraw
     *  agree on one source of truth. */
    public static final String SEPARATOR = " · ";
}
