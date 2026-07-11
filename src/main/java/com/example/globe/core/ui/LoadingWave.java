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
 * LevelLoadingScreenLatitudeOverlayMixin}) turns each segment's {@link #boost} into a color via
 * {@link TitleStyle#brighten} and is responsible for freezing the wave (passing/using boost 0) under
 * {@code LatitudeConfig.reduceMotion} — this class is intentionally motion-policy-agnostic.
 *
 * <p>Design intent: this is a caress, not the title glimmer. The crest lift is modest ({@link #AMPLITUDE}
 * ≈ 30% toward white via {@code brighten}), so the base text stays fully readable at its normal color at all
 * times and only the crest word lifts.
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
        int count = Math.max(1, segmentCount);
        if (count == 1) {
            // A lone segment has no neighbour to hand off to; give it a slow, gentle breathing lift rather than
            // a hard-on crest, so a single-element summary still feels alive but never blinks.
            double phase = (timeMs % (SEGMENT_PERIOD_MS * 2L)) / (double) (SEGMENT_PERIOD_MS * 2L);
            float breathe = (float) (0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI)); // 0→1→0, smooth
            return AMPLITUDE * breathe;
        }
        long cycleMs = SEGMENT_PERIOD_MS * count;
        double phase = (timeMs % cycleMs) / (double) SEGMENT_PERIOD_MS; // crest position in [0, count)
        double raw = Math.abs(segmentIdx - phase);
        double d = Math.min(raw, count - raw); // circular distance around the ring of segments
        float g = (float) Math.exp(-(d * d) / (2.0 * SIGMA * SIGMA));
        float boost = AMPLITUDE * g;
        // Numerical safety: keep strictly within [0, AMPLITUDE].
        if (boost < 0f) {
            return 0f;
        }
        return boost > AMPLITUDE ? AMPLITUDE : boost;
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
