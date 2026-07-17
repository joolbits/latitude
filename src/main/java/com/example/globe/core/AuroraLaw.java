package com.example.globe.core;

/**
 * Phase 5 Slice S13(a) -- AURORA BOREALIS: the pure VISUAL LAW for the polar-night aurora curtains. Pure Java,
 * ZERO Minecraft imports (Core Logic layer, plain-JVM unit-testable) -- same discipline as {@link SolarTilt} /
 * {@link SolarSkyMood} / {@link PolarFogLaw}. Everything the aurora needs to decide (is it visible, how bright,
 * where the ribbons ripple, what colour each vertex is) lives here as pure functions; the renderer
 * ({@code client.AuroraRenderer}) is a thin shim that maps these to camera-relative sky geometry.
 *
 * <h2>The gate (design S12(1)/S13(a))</h2>
 * The aurora's intensity is the PRODUCT of three factors, so it appears ONLY where all three agree -- the
 * mood-idiom composition {@link SolarSkyMood} already uses for the sky wash:
 * <pre>
 *   intensity = latitudeRamp(|lat|, from ~65 poleward)
 *             x darkSky(solarElevation)          // = SolarSkyMood.polarNightGloom01: strongest in deep polar
 *                                                //   night, ZERO under daylight AND under the midnight sun
 *             x (1 - stormLevel)                 // overcast/storm suppresses; the 85+ whiteout hides the sky
 * </pre>
 * Reusing {@link SolarSkyMood#polarNightGloom01} for the dark-sky term is deliberate (§8 one-evaluator law):
 * the aurora, the polar-night sky gloom and the star lift all ride ONE darkness curve, so a night the sky
 * calls "polar dark" is the SAME night the aurora lights up. Under the midnight sun the sun never dips below
 * the horizon, so {@code polarNightGloom01} is 0 and the aurora is structurally absent -- no separate check.
 *
 * <h2>Motion (deterministic, WORLD CLOCK)</h2>
 * Every time-driven function takes a {@code worldTimeTicks} (the replicated world game-time, optionally plus a
 * partial-tick fraction) and is a PURE function of it -- {@link #verticalWaverBlocks} (layered incommensurate
 * sines whose crests travel along the curtain = the slow horizontal drift, while the sum undulates = the
 * vertical waver) and {@link #azimuthDriftRad} (a slow sideways sway). NO wall-clock accumulator (unlike the
 * legacy {@code PolarFogLaw.gustFactor}, which reads {@code System.currentTimeMillis}): same clock in -> same
 * phases out, so two clients at the same world tick draw the same aurora, and it never jumps on a settings
 * change or a rejoin. NaN-safe throughout (a NaN time/parameter degrades to 0, never a NaN vertex that would
 * blank the sky pass).
 *
 * <h2>Shape + colour</h2>
 * {@link #BAND_COUNT} ribbon bands (design "2-4"), each a tall thin curtain arced across the poleward sky. A
 * curtain is coloured by a vertical profile ({@link #LEVEL_V}/{@link #LEVEL_ALPHA}): a bright green-teal core
 * at the sharp lower edge ({@link #CORE_RGB}) fading UP through a dim purple fringe ({@link #FRINGE_RGB}) -- the
 * iconic aurora gradient. The renderer supplies the render-space scalars (radius, height, segment count); this
 * class supplies the band count, colours, per-band offsets, the waver, and the along-arc edge fade.
 */
public final class AuroraLaw {

    private AuroraLaw() {
    }

    // ---- the latitude ramp (design "from ~65 poleward") ------------------------------------------------

    /** Absolute latitude (deg) where the aurora begins to ramp in -- just poleward of the solar-tilt band onset
     *  (60 deg at delta 30), so the curtains belong to the high-latitude night, not the temperate sky. */
    public static final double LAT_ONSET_DEG = 65.0;
    /** Absolute latitude (deg) at/above which the latitude factor is full. 72 keeps the ramp inside the deep
     *  polar band while leaving headroom below the 85-deg storm suppression. One-line P4 dial. */
    public static final double LAT_FULL_DEG = 72.0;

    /** Latitude factor 0..1: 0 at/below {@link #LAT_ONSET_DEG}, smoothstep to 1 at/above {@link #LAT_FULL_DEG}.
     *  Symmetric about the equator (caller passes {@code |lat|}). NaN-safe (0). */
    public static double latitudeRamp01(double absLatDeg) {
        if (Double.isNaN(absLatDeg) || absLatDeg <= LAT_ONSET_DEG) {
            return 0.0;
        }
        if (absLatDeg >= LAT_FULL_DEG) {
            return 1.0;
        }
        return smoothstep01((absLatDeg - LAT_ONSET_DEG) / (LAT_FULL_DEG - LAT_ONSET_DEG));
    }

    /** The dark-sky factor 0..1 -- DELEGATES to {@link SolarSkyMood#polarNightGloom01} (§8 one-evaluator law):
     *  0 with the sun at/above the horizon (daylight AND the never-setting midnight sun), ramping to full as the
     *  sun sinks toward {@code -12} deg (deep polar night / the dead of an ordinary high-latitude night).
     *  NaN-safe (0). */
    public static double darkSky01(double solarElevationDeg) {
        return SolarSkyMood.polarNightGloom01(solarElevationDeg);
    }

    /**
     * Master aurora intensity 0..1 = {@link #latitudeRamp01} x {@link #darkSky01} x {@code (1 - stormLevel01)}.
     * Zero if ANY factor is zero: equatorward of the onset, under daylight or the midnight sun, or in full
     * storm/overcast. Cheap early-outs (latitude, then dark) so the renderer can bail before touching geometry.
     * A NaN {@code stormLevel01} reads as fully stormy (1.0 -> intensity 0, the conservative side, matching
     * {@link SolarSkyMood#stormDamp}). Clamped to {@code [0,1]}.
     */
    public static double intensity01(double absLatDeg, double solarElevationDeg, double stormLevel01) {
        double lat = latitudeRamp01(absLatDeg);
        if (lat <= 0.0) {
            return 0.0;
        }
        double dark = darkSky01(solarElevationDeg);
        if (dark <= 0.0) {
            return 0.0;
        }
        double storm = Double.isNaN(stormLevel01) ? 1.0 : clamp01(stormLevel01);
        return clamp01(lat * dark * (1.0 - storm));
    }

    // ---- bands (design "2-4 ribbon bands") -------------------------------------------------------------

    /** Number of ribbon bands (design "2-4"). Each is a curtain arced across the poleward sky at its own
     *  azimuth offset, height and wave phase so they read as several independent sheets, not one. */
    public static final int BAND_COUNT = 3;

    /** Per-band azimuth offset (rad) from the poleward centre -- spreads the bands across the poleward sky. */
    private static final double[] BAND_AZIMUTH_OFFSET_RAD = {-0.38, 0.02, 0.40};
    /** Per-band wave-phase offset (rad) so the bands do not ripple in unison. */
    private static final double[] BAND_PHASE_RAD = {0.0, 2.10, 4.20};
    /** Per-band base-height offset (blocks) so the bands stack/recede for parallax depth. */
    private static final double[] BAND_HEIGHT_OFFSET_BLOCKS = {0.0, 12.0, 24.0};

    /** Azimuth offset (rad) for a band, clamped to a valid band index (out-of-range -> band 0). */
    public static double bandAzimuthOffsetRad(int bandIndex) {
        return BAND_AZIMUTH_OFFSET_RAD[clampBand(bandIndex)];
    }

    /** Wave-phase offset (rad) for a band, clamped to a valid band index. */
    public static double bandPhaseRad(int bandIndex) {
        return BAND_PHASE_RAD[clampBand(bandIndex)];
    }

    /** Base-height offset (blocks) for a band, clamped to a valid band index. */
    public static double bandHeightOffsetBlocks(int bandIndex) {
        return BAND_HEIGHT_OFFSET_BLOCKS[clampBand(bandIndex)];
    }

    // ---- motion: deterministic over the WORLD CLOCK ----------------------------------------------------

    /** Peak vertical undulation of a curtain (blocks). The waver returns {@code [-this, +this]}. */
    public static final double WAVER_AMP_BLOCKS = 10.0;

    // Traveling-wave rates (radians per world tick). Small -> SLOW: the fastest layer completes a cycle in
    // ~1600 ticks (~80 s), so the curtains breathe rather than flicker. Incommensurate so the sum never
    // obviously repeats. One-line P4 dials.
    private static final double WAVE1_RATE = (2.0 * Math.PI) / 1600.0;
    private static final double WAVE2_RATE = (2.0 * Math.PI) / 2600.0;
    private static final double WAVE3_RATE = (2.0 * Math.PI) / 900.0;
    // Spatial frequencies along the arc (radians per unit u): how many crests span one curtain.
    private static final double WAVE1_SPATIAL = 2.0 * Math.PI * 1.5;
    private static final double WAVE2_SPATIAL = 2.0 * Math.PI * 2.7;
    private static final double WAVE3_SPATIAL = 2.0 * Math.PI * 4.3;

    /**
     * Vertical waver (blocks) of a curtain at along-arc parameter {@code u01 in [0,1]} for a band at world time
     * {@code worldTimeTicks} -- three layered sines whose crests TRAVEL as time advances (the slow horizontal
     * drift) while their sum undulates the curtain up and down (the vertical waver). Amplitude
     * {@link #WAVER_AMP_BLOCKS}. Pure + deterministic (same args -> same value); NaN time/parameter -> 0.
     */
    public static double verticalWaverBlocks(double worldTimeTicks, int bandIndex, double u01) {
        if (Double.isNaN(worldTimeTicks) || Double.isNaN(u01)) {
            return 0.0;
        }
        double ph = bandPhaseRad(bandIndex);
        double w = 0.60 * Math.sin(u01 * WAVE1_SPATIAL + worldTimeTicks * WAVE1_RATE + ph)
                 + 0.30 * Math.sin(u01 * WAVE2_SPATIAL - worldTimeTicks * WAVE2_RATE + ph * 1.7)
                 + 0.10 * Math.sin(u01 * WAVE3_SPATIAL + worldTimeTicks * WAVE3_RATE + ph * 0.5);
        return w * WAVER_AMP_BLOCKS;
    }

    /** Peak azimuth sway (rad) of the whole aurora -- a slow sideways drift of the curtains across the sky. */
    public static final double AZIMUTH_DRIFT_AMP_RAD = 0.12;
    private static final double AZIMUTH_DRIFT_RATE = (2.0 * Math.PI) / 6000.0; // one sway cycle ~5 min

    /** Slow azimuth sway (rad) of the whole aurora at world time {@code worldTimeTicks}:
     *  {@code AZIMUTH_DRIFT_AMP_RAD * sin(t * rate)}. Pure + deterministic; NaN time -> 0. */
    public static double azimuthDriftRad(double worldTimeTicks) {
        if (Double.isNaN(worldTimeTicks)) {
            return 0.0;
        }
        return AZIMUTH_DRIFT_AMP_RAD * Math.sin(worldTimeTicks * AZIMUTH_DRIFT_RATE);
    }

    // ---- colour + vertical/along-arc alpha profile -----------------------------------------------------

    /** Green-teal aurora core (the bright lower edge). One-line P4 dial. */
    public static final int CORE_RGB = 0x3CFFC8;
    /** Purple upper fringe the curtain fades to at its top. */
    public static final int FRINGE_RGB = 0x9A4BE6;

    /** Vertical levels of a curtain as a fraction of its height (0 = sharp bright lower edge, 1 = wispy top). */
    public static final double[] LEVEL_V = {0.0, 0.5, 1.0};
    /** Per-level alpha shape (before the master intensity): brightest at the lower edge, fading to a faint
     *  purple top so the fringe is just visible, never a hard wall. */
    public static final double[] LEVEL_ALPHA = {0.85, 0.55, 0.12};

    /** Number of vertical levels ({@link #LEVEL_V}) -> {@code LEVELS-1} stacked quad rows per arc segment. */
    public static final int LEVELS = 3;

    /** The RGB colour for vertical level index {@code k}: green-teal core lerped toward the purple fringe by
     *  {@link #LEVEL_V}{@code [k]}. Clamped level index. */
    public static int levelColorRgb(int levelIndex) {
        return lerpRgb(CORE_RGB, FRINGE_RGB, LEVEL_V[clampLevel(levelIndex)]);
    }

    /** The base (pre-intensity) alpha 0..1 for vertical level index {@code k}. Clamped level index. */
    public static double levelAlpha01(int levelIndex) {
        return LEVEL_ALPHA[clampLevel(levelIndex)];
    }

    /** Fraction of each arc END over which the curtain fades to nothing (so it does not stop abruptly). */
    public static final double ARC_EDGE_FRAC = 0.2;

    /** Along-arc alpha fade 0..1: smoothstep 0 at the two ends of the arc ({@code u01} 0 and 1) to 1 within
     *  {@link #ARC_EDGE_FRAC} of each end. NaN-safe (0). */
    public static double arcEdgeFade01(double u01) {
        if (Double.isNaN(u01)) {
            return 0.0;
        }
        double u = clamp01(u01);
        double d = Math.min(u, 1.0 - u);
        if (d >= ARC_EDGE_FRAC) {
            return 1.0;
        }
        return smoothstep01(d / ARC_EDGE_FRAC);
    }

    /**
     * The final packed ARGB colour for a vertex at vertical level {@code k} and along-arc parameter {@code u01}
     * under a master {@code intensity01}: the level's colour, with alpha =
     * {@code levelAlpha[k] * arcEdgeFade(u) * intensity}, clamped and packed. Alpha 0 -> a fully transparent
     * (invisible) vertex. Pure; NaN inputs collapse to alpha 0.
     */
    public static int vertexArgb(int levelIndex, double u01, double intensity01) {
        int rgb = levelColorRgb(levelIndex);
        double a = levelAlpha01(levelIndex) * arcEdgeFade01(u01)
                * (Double.isNaN(intensity01) ? 0.0 : clamp01(intensity01));
        int alpha = (int) Math.round(clamp01(a) * 255.0);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private static int lerpRgb(int fromRgb, int toRgb, double t01) {
        double t = clamp01(t01);
        int r = lerpChannel((fromRgb >> 16) & 0xFF, (toRgb >> 16) & 0xFF, t);
        int g = lerpChannel((fromRgb >> 8) & 0xFF, (toRgb >> 8) & 0xFF, t);
        int b = lerpChannel(fromRgb & 0xFF, toRgb & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    /** Smoothstep {@code 3t^2 - 2t^3} on an already-normalised {@code t}; clamps to {@code [0,1]}. */
    private static double smoothstep01(double t) {
        double x = clamp01(t);
        return x * x * (3.0 - 2.0 * x);
    }

    private static int clampBand(int bandIndex) {
        if (bandIndex < 0) {
            return 0;
        }
        return bandIndex >= BAND_COUNT ? BAND_COUNT - 1 : bandIndex;
    }

    private static int clampLevel(int levelIndex) {
        if (levelIndex < 0) {
            return 0;
        }
        return levelIndex >= LEVELS ? LEVELS - 1 : levelIndex;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
