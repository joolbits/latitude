package com.example.globe.core;

/**
 * Phase 5 Slice B-7 S10(b)(c) -- FOG LAW v2 (owner, TEST 99: "not convincingly COLD... fog is very lacking in
 * general... at 90 you can't see really anything in front of you"). ONE continuous latitude->fog law for the
 * whole polar approach, pure Java, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM).
 * Two renderers implement it -- the depth fog ({@code FogRendererPolarSetupMixin}, wall-aware, correct
 * sheltered-looking-out) and the exposure-gated whiteout top-coat ({@code PolarWhiteoutOverlayHud}) -- but the
 * LAW (which latitude gets which visibility) lives only here. The S10c atmosphere plus-ups (early overcast,
 * wind GUSTS) ride in this class too, so every polar-atmosphere dial is one file.
 *
 * <h2>The curve (S10b): "light haze from 80, thickening per degree, heavy by 88, near-total whiteout in the
 * last half-degree -- the fog IS the wall"</h2>
 * {@link #POLAR_FOG_CURVE} maps latitude to an ABSOLUTE cylindrical fog-END cap in blocks (~visibility: the
 * fog shader fades everything past END to fog colour). ABSOLUTE, not eased-from-vanilla like the superseded
 * {@code PolarHazardWindow.polarFogEnd} -- the old law scaled with the player's video render distance, so a
 * 32-chunk player saw ~2.7x thinner fog at every latitude than the law intended (the root of TEST 99's "fog is
 * very lacking"). A cap is the same felt visibility on every machine; the caller min()s it against vanilla so
 * it only ever tightens (seam-free: the 80-deg entry sits above every realistic view distance).
 *
 * <p><b>Superseded, deliberately left in place:</b> {@code PolarHazardWindow.fogIntensity/polarFogEnd/
 * polarFogStart/polarFogEndFraction/polarFogStartFraction} (and {@code GlobeClientState.computePoleFogEnd/
 * computePoleWhiteoutFactor}) are no longer read by the polar renderers. PolarHazardWindow is shared with the
 * server crew's in-flight round, so this pass does not touch it; a later hygiene pass may retire them.
 *
 * <p><b>Interior-storm split unchanged:</b> the law says how thick the fog is; WHERE it may paint is the
 * renderers' law (depth fog needs no exposure gate -- it is depth-correct; the flat top-coat stays
 * exposure-scaled exactly as before).
 */
public final class PolarFogLaw {

    private PolarFogLaw() {
    }

    /** Below this latitude the law is silent (vanilla fog untouched). */
    public static final double FOG_ONSET_DEG = 80.0;
    /** The pole line. */
    public static final double FOG_FULL_DEG = 90.0;

    /**
     * THE owner-tunable curve (S10b): {@code {latitudeDeg, fogEndCapBlocks}} rows, strictly increasing in
     * degrees, non-increasing in blocks; piecewise-LINEAR between rows ({@link #fogEndCapBlocks}). The cap is
     * ~visibility in blocks. P4 tuning = edit a number.
     * <pre>
     *   deg    cap    reads as
     *   80.0   512    seam entry (no-op at every realistic view distance; the "onset" is analytic)
     *   82.0   140    light haze -- far terrain softens
     *   84.0   100    haze thickening per degree
     *   86.0    70    fog proper
     *   88.0    40    HEAVY (the owner's "heavy by 88"): ~2.5 chunks of sight
     *   89.0    24    blizzard closing in
     *   89.5    12    the near-total whiteout begins (the last half-degree)
     *   90.0     4    the pole line: a few blocks -- the fog IS the wall (replaces the striped plane)
     * </pre>
     */
    public static final double[][] POLAR_FOG_CURVE = {
            {80.0, 512.0},
            {82.0, 140.0},
            {84.0, 100.0},
            {86.0, 70.0},
            {88.0, 40.0},
            {89.0, 24.0},
            {89.5, 12.0},
            {90.0, 4.0},
    };

    /** Fog START as a fraction of the (capped) END: the band {@code [START, END]} scales with the fog so the
     *  whiteout closes in as one front, not a hard wall at a fixed range. S11(g) (TEST 101, owner screenshot:
     *  "sharp unrendered mountains" popping at the cap): 0.30 -> 0.08 -- START hugs the camera, so nearly the
     *  whole [START, END] span is a gradient and distant terrain FADES through haze instead of appearing at
     *  the fog wall. One-line P4 dial (ordered window 0.05-0.1). */
    public static final double FOG_START_FRACTION = 0.08;
    /** START never below this (blocks): your own hands/the block you stand on stay clear even at the line. */
    public static final double FOG_START_MIN_BLOCKS = 2.0;

    /**
     * The cylindrical fog-END cap (blocks) for a latitude: {@link Float#MAX_VALUE} below
     * {@link #FOG_ONSET_DEG} (silent -- callers min() against vanilla, so MAX_VALUE = untouched), the
     * piecewise-linear {@link #POLAR_FOG_CURVE} inside {@code [80,90]}, and the last row's value at/beyond the
     * pole line (a Wide-world survivor past 90 keeps the full whiteout). NaN latitude reads as "far" (silent).
     */
    public static float fogEndCapBlocks(double absLatDeg) {
        if (Double.isNaN(absLatDeg) || absLatDeg < POLAR_FOG_CURVE[0][0]) {
            return Float.MAX_VALUE;
        }
        double[][] c = POLAR_FOG_CURVE;
        if (absLatDeg >= c[c.length - 1][0]) {
            return (float) c[c.length - 1][1];
        }
        for (int i = 1; i < c.length; i++) {
            if (absLatDeg <= c[i][0]) {
                double t = (absLatDeg - c[i - 1][0]) / (c[i][0] - c[i - 1][0]);
                return (float) (c[i - 1][1] + t * (c[i][1] - c[i - 1][1]));
            }
        }
        return (float) c[c.length - 1][1]; // unreachable (loop covers <= last row); defensive.
    }

    /** Fog START (blocks) for an already-capped END: {@code max(2, FOG_START_FRACTION * end)}, and the caller
     *  additionally clamps below END so {@code START < END} always holds. */
    public static float fogStartBlocks(float cappedEnd) {
        return (float) Math.max(FOG_START_MIN_BLOCKS, cappedEnd * FOG_START_FRACTION);
    }

    // ---- S11(f): the fog colour follows the EFFECTIVE sun (night / polar night darken the fog) ----------

    /** Deep night fog tone the daylight storm/white palette darkens toward when the (effective) sun is down --
     *  a cold near-black blue, so night polar fog reads as darkness thickened by snow, not a glowing white
     *  wall (TEST 101: "white fog at a dark sky" wrongness). */
    public static final int NIGHT_FOG_RGB = 0x121826;
    /** Max fraction of the fog colour the night darkening may take (1.0 would black out the fog entirely;
     *  0.85 keeps a breath of the storm tone so the whiteout still reads as WEATHER in the dark). */
    public static final double NIGHT_FOG_MAX_BLEND = 0.85;

    /**
     * Fog darkness 0..1 from the (effective) solar elevation: 0 with the sun at/above the horizon, full when
     * it sits {@code 12°} below -- DELEGATES to {@link SolarSkyMood#polarNightGloom01} so the fog, the
     * polar-night sky gloom and the star lift all ride ONE darkness curve (one law, three surfaces; the §8
     * one-evaluator discipline applied to presentation). Callers feed it {@link SolarTilt#solarElevationDeg}
     * with the tilt's (φ, δ) when solar tilt is enabled, or (0, 0) -- the plain vanilla clock arc -- when it
     * is not, so ordinary vanilla night darkens the polar fog too. NaN-safe (0 = day).
     */
    public static double nightFogDarkness01(double solarElevationDeg) {
        return SolarSkyMood.polarNightGloom01(solarElevationDeg);
    }

    /** Plain 0..1 progress across the fog window (80 -> 90); the storm->white palette lerp both renderers use. */
    public static float linear01(double absLatDeg) {
        if (Double.isNaN(absLatDeg)) {
            return 0.0f;
        }
        return (float) clamp01((absLatDeg - FOG_ONSET_DEG) / (FOG_FULL_DEG - FOG_ONSET_DEG));
    }

    /** How far the fog COLOUR is pulled to the storm/white palette: front-loaded ({@code linear01^0.8}) so the
     *  far haze reads cold storm-grey early rather than biome-blue. */
    public static float colorBlend01(double absLatDeg) {
        float p = linear01(absLatDeg);
        return p <= 0.0f ? 0.0f : (float) Math.pow(p, 0.8);
    }

    // ---- the exposure-gated whiteout TOP-COAT (the "engulfed" flat veil over the depth fog) ----

    /** Top-coat envelope rows {@code {deg, strength01}}: quiet through the mid-band (the depth fog carries the
     *  distance haze), lifting from 86 and FULL at the line -- composited with the 4-block depth fog + snow it
     *  completes the owner's "can't see really anything in front of you". Piecewise-linear. */
    public static final double[][] WHITEOUT_TOPCOAT_CURVE = {
            {86.0, 0.0},
            {88.0, 0.35},
            {89.0, 0.6},
            {89.5, 0.85},
            {90.0, 1.0},
    };

    /** Whiteout top-coat strength 0..1 for a latitude (0 at/below 86; 1 at/beyond the pole line). The overlay
     *  multiplies this by its alpha cap and the exposure scale (interior-split law unchanged). */
    public static float whiteoutTopcoat01(double absLatDeg) {
        if (Double.isNaN(absLatDeg) || absLatDeg <= WHITEOUT_TOPCOAT_CURVE[0][0]) {
            return 0.0f;
        }
        double[][] c = WHITEOUT_TOPCOAT_CURVE;
        if (absLatDeg >= c[c.length - 1][0]) {
            return (float) c[c.length - 1][1];
        }
        for (int i = 1; i < c.length; i++) {
            if (absLatDeg <= c[i][0]) {
                double t = (absLatDeg - c[i - 1][0]) / (c[i][0] - c[i - 1][0]);
                return (float) (c[i - 1][1] + t * (c[i][1] - c[i - 1][1]));
            }
        }
        return (float) c[c.length - 1][1];
    }

    // ---- S10c: greyer skies EARLIER (early-overcast floor under the 85-deg storm-sky ramp) ----

    /** Latitude at which the sky starts greying (S10c "greyer skies earlier"), leading the 85-deg storm ramp. */
    public static final double EARLY_OVERCAST_START_DEG = 81.0;
    /** Rain-level floor reached AT 85 deg and held (the 85 full-storm anchor is untouched: from 85 the
     *  existing steep {@code PolarHazardWindow.stormLevel} ramp overtakes this floor at ~85.9 and carries to
     *  full overcast by 87.5). 0.35 = clearly greying sky + light vanilla snowfall joining the ambient flakes. */
    public static final double EARLY_OVERCAST_MAX = 0.35;

    /** The early-overcast rain-level floor: 0 at/below 81, linear to {@link #EARLY_OVERCAST_MAX} at 85, held
     *  poleward. The client storm-sky mixin takes {@code max(stormLevel, this)} so the pre-85 approach greys in
     *  while the flight-tested 85->87.5 full-storm ramp is preserved bit-for-bit. */
    public static float earlyOvercast01(double absLatDeg) {
        if (Double.isNaN(absLatDeg)) {
            return 0.0f;
        }
        double t = clamp01((absLatDeg - EARLY_OVERCAST_START_DEG)
                / (PolarHazardWindow.STORM_ONSET_DEG - EARLY_OVERCAST_START_DEG));
        return (float) (t * EARLY_OVERCAST_MAX);
    }

    // ---- S10c: WIND AS GUSTS (periodic surges; NEVER constant sideways drift -- standing owner veto) ----

    /** Gust cycle period (wall-clock ms). 14 s sits mid of the ordered 8-20 s window. */
    public static final long GUST_PERIOD_MS = 14000L;
    /** Peak gust boost: at full depth + gust crest the snowfall count and fall speed surge to 1 + this
     *  (= the ordered ~1.5x ceiling). */
    public static final double GUST_MAX_BOOST = 0.5;
    /** Gust strength ramps with latitude depth: 0 at/below this... */
    public static final double GUST_DEPTH_START_DEG = 82.0;
    /** ...to full at/beyond this (the DANGER rung -- deep polar gusts hit full force). */
    public static final double GUST_DEPTH_FULL_DEG = 89.0;

    /** Latitude-depth scale for gust strength: 0 at/below 82, 1 at/beyond 89. */
    public static double gustDepth01(double absLatDeg) {
        if (Double.isNaN(absLatDeg)) {
            return 0.0;
        }
        return clamp01((absLatDeg - GUST_DEPTH_START_DEG) / (GUST_DEPTH_FULL_DEG - GUST_DEPTH_START_DEG));
    }

    /** The gust wave 0..1 over wall time: a slow sine SQUARED, so the cycle reads as calm -> building surge ->
     *  calm (a gust every {@link #GUST_PERIOD_MS}) rather than a constant hum. Deterministic per call -- a pure
     *  function of the clock, never an accumulator (B-3b anti-backlog law). */
    public static double gustWave01(long nowMs) {
        double s = 0.5 + 0.5 * Math.sin((2.0 * Math.PI * (nowMs % GUST_PERIOD_MS)) / (double) GUST_PERIOD_MS);
        return s * s;
    }

    /** The composed gust factor applied multiplicatively to the snowfall COUNT budgets and DOWNWARD fall speed
     *  (never the sideways wind): {@code 1 + GUST_MAX_BOOST * depth * wave}, i.e. in {@code [1, 1.5]} -- 1.0
     *  exactly at/below 82 deg or in the calm trough. */
    public static double gustFactor(long nowMs, double absLatDeg) {
        return 1.0 + GUST_MAX_BOOST * gustDepth01(absLatDeg) * gustWave01(nowMs);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
