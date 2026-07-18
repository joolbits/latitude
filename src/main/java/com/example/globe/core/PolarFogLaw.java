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

    // ---- S11(f) -> S14(c): the fog colour follows the FINAL sky --------------------------------------------
    //
    // The night/polar-night darkening AND the midnight-sun dusk hold now live in ONE place —
    // SolarSkyMood.atmosphereTint (the same POLAR_NIGHT_SKY_RGB / MIDNIGHT_SUN_DUSK_RGB palette + the
    // polarNightGloom01 / twilightHold01 curves the sky dome paints) — applied client-side by
    // GlobeClientState.polarSkyTint for BOTH the depth fog and the whiteout top-coat. S11(f)'s dedicated
    // NIGHT_FOG_RGB / NIGHT_FOG_MAX_BLEND / nightFogDarkness01 were the DUPLICATED night palette the S14(c)
    // one-source-of-truth mandate retires; the darkness curve is SolarSkyMood.polarNightGloom01 directly.

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

    /** Latitude at which the horizon-gloom reach is full (S15(c)). */
    public static final double HORIZON_GLOOM_FULL_DEG = 85.0;

    /**
     * S15(c) HORIZON-GLOOM REACH 0..1 -- how strongly the fog COLOUR (the atmospheric {@code FOG_COLOR} that
     * paints the sky's lower rim / horizon band) is pulled to the FINAL night/dusk sky at a latitude. The owner
     * (TEST 105) saw a LIGHT horizon band under a DARK polar-night sky: the sky DOME is gloomed to near-black by
     * {@code SkyRendererSolarTiltMixin} (onset ~60 deg, full deep in the night), but the fog/horizon colour was
     * only pulled toward the gloom by the slow {@code colorBlend01} blizzard-haze curve (~0.84 even at 88 deg),
     * so the horizon stayed lighter than the sky. This reach lets {@code FogRendererPolarSetupMixin} apply the
     * SAME night/dusk gloom the sky uses to the fog colour at FULL strength -- but eased in from the 80-deg fog
     * onset (0 at 80, smoothstep to 1 by {@link #HORIZON_GLOOM_FULL_DEG}) so there is no colour seam at the onset
     * (where the fog is untouched below 80) and the horizon fully matches the gloomed sky by 85 deg and through
     * the pole. 0 at/below the onset; 1 at/beyond {@code HORIZON_GLOOM_FULL_DEG}. NaN-safe (0).
     */
    public static float horizonGloomReach01(double absLatDeg) {
        if (Double.isNaN(absLatDeg) || absLatDeg <= FOG_ONSET_DEG) {
            return 0.0f;
        }
        if (absLatDeg >= HORIZON_GLOOM_FULL_DEG) {
            return 1.0f;
        }
        double t = (absLatDeg - FOG_ONSET_DEG) / (HORIZON_GLOOM_FULL_DEG - FOG_ONSET_DEG);
        return (float) (t * t * (3.0 - 2.0 * t)); // smoothstep
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

    // ---- S17(a): ENTITY FOG CULLING -- nothing renders beyond the wall of white ----------------------------
    //
    // Owner, TEST 107 (video): entities (mobs, dropped items, boats...) kept rendering PAST the polar fog END,
    // reading as figures floating in the white void over terrain that had itself faded into fog / gone unloaded.
    // The fog hides the WORLD past its END but the entity renderer still drew entities out there. This law culls
    // any entity farther than the fog END (plus a small margin) while the camera is inside the polar fog band --
    // so nothing is visible beyond the wall of white (and, as a bonus, it is a genuine perf win: those entities
    // are never submitted). It is the SAME latitude->visibility curve the depth fog uses ({@link
    // #fogEndCapBlocks}), so the cull edge sits exactly where the world already dissolved -- there is no seam
    // where an entity pops in ahead of visible terrain.
    //
    // Un-gated (no dedicated feature flag), exactly like the fog itself (S11f precedent): the render hook gates
    // only on isGlobeWorld + the shared debug fog kill switches, never a separate toggle. Below the 80-deg fog
    // onset {@link #fogEndCapBlocks} returns MAX_VALUE, so this is a provable no-op (never culls) outside polar
    // country, and even at the 80-deg entry the cap (512) sits beyond every render distance, so the cull only
    // ever bites once the fog has genuinely closed in.

    /** Small distance past the fog END (blocks) an entity may still render, so the cull edge sits just BEYOND the
     *  visible fog wall rather than at it (no entity blinking out at the exact fog boundary). ~8 blocks = half a
     *  chunk. One-line P4 dial. */
    public static final double ENTITY_CULL_MARGIN_BLOCKS = 8.0;

    /**
     * Whether an entity at {@code horizontalDistBlocks} from the camera should be CULLED (not rendered) because
     * it lies beyond the polar fog wall. True iff the camera is in the polar fog band (latitude at/above the
     * 80-deg onset, where {@link #fogEndCapBlocks} is finite) AND the entity is farther than the fog END plus
     * {@link #ENTITY_CULL_MARGIN_BLOCKS}. Below the onset (cap {@code == Float.MAX_VALUE}) it always returns
     * false -- a provable no-op outside polar country. NaN distance / latitude read as "do not cull" (defensive:
     * the render path must never hide an entity on a bad sample). Horizontal (xz-cylindrical) distance is used to
     * match how the fog END caps sight along the ground; a void-floating entity sits near the camera's Y, so the
     * horizontal term is the one that matters.
     *
     * @param absLatDeg            camera absolute latitude (deg)
     * @param horizontalDistBlocks horizontal (xz) distance from the camera to the entity, in blocks
     */
    public static boolean cullEntityBeyondFog(double absLatDeg, double horizontalDistBlocks) {
        if (Double.isNaN(absLatDeg) || Double.isNaN(horizontalDistBlocks)) {
            return false;
        }
        float cap = fogEndCapBlocks(absLatDeg);
        if (cap == Float.MAX_VALUE) {
            return false; // below the 80-deg fog onset: never cull (seam-free with the fog)
        }
        return horizontalDistBlocks > cap + ENTITY_CULL_MARGIN_BLOCKS;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
