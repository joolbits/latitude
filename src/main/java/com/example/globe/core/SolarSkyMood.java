package com.example.globe.core;

/**
 * Solar Tilt P2 — the pure SKY-MOOD curves (design §6): polar-night gloom + midnight-sun gold, the two
 * band moods the {@code SkyRenderState} hooks paint. Pure Java, zero MC imports, plain-JVM testable.
 * The mixin ({@code SkyRendererSolarTiltMixin.extractRenderState} tail) supplies the inputs (band from
 * {@link SolarTilt}, elevation from {@link SolarTilt#solarElevationDeg}, storm level from
 * {@link PolarHazardWindow#stormLevel}) and applies the outputs to {@code skyColor} / {@code starBrightness}.
 *
 * <p><b>A4 BINDING (sweep):</b> the mood goes through SKY/FOG COLOUR only — it must NEVER un-grey
 * {@code rainLevel}/{@code rainBrightness}. The S10 early-overcast ramp (81+) and the storm sky (85 → full by
 * 87.5) always win: every mood blend is damped by {@code (1 − stormLevel)} ({@link #stormDamp}), so the gold
 * and the gloom both dissolve into the storm cap instead of fighting it. Bands coexist; payoff zone 40–80.
 *
 * <p><b>Hard rule (design §6):</b> no light-engine touch — these tint the sky PASS. Block light, torches and
 * ground brightness stay on the global clock (the documented honest seam: polar night reads as an eerie
 * bright-dark twilight).
 */
public final class SolarSkyMood {

    private SolarSkyMood() {
    }

    // ---- owner-tunable constants (one-line P4 tuning) --------------------------------------------------

    /** Deep blue-grey night wash the polar-night sky blends toward (a cold starlit night, not pure black). */
    public static final int POLAR_NIGHT_SKY_RGB = 0x0B111E;
    /** Max fraction of the sky colour the gloom may take. S14(a)(ii) DEEPENED 0.85 -> 0.97 (owner, TEST 104:
     *  "no light on the opposite pole" — the deep polar-night sky must read FULL dark even at global noon).
     *  Only the deep band saturates the gloom curve to 1.0 (the pole-invariant constant elevation), so this
     *  ceiling fully darkens ONLY where it is already fully night; near the 60-deg onset the elevation-driven
     *  gloom is partial and the sky still hangs in eerie twilight. The 3% breath of the underlying tint keeps
     *  it a cold night rather than a dead flat fill (1.0 would flat-fill to {@link #POLAR_NIGHT_SKY_RGB}). */
    public static final double GLOOM_MAX_BLEND = 0.97;
    /** The gloom reaches full depth when the (never-rising) sun sits this far below the horizon. Between 0
     *  and this the polar-night day hangs in blue twilight — the civil-twilight read. */
    public static final double GLOOM_FULL_DEPTH_DEG = 12.0;

    /** Warm low-gold the midnight-sun sky blends toward (low-hanging golden-hour palette). */
    public static final int MIDNIGHT_SUN_SKY_RGB = 0xFFC873;
    /** Max fraction of the sky colour the gold may take — a wash, never a repaint. */
    public static final double GOLD_MAX_BLEND = 0.35;
    /** The gold is full when the (never-setting) sun grazes the horizon and fades out by this elevation --
     *  a midnight sun circling at 5° is deep gold, one at 25°+ is ordinary daylight. */
    public static final double GOLD_FADE_OUT_ELEV_DEG = 25.0;

    /** S14(a)(i) — rose-gold DUSK the midnight-sun sky HOLDS through the global-clock night. RGB
     *  {@code 0xE8A07D} (232,160,125): a warm pink-gold horizon glow, the "2 a.m. arctic summer" sky. The sun
     *  never sets, so as the vanilla clock tries to paint night the sky is held HERE instead of at black.
     *  Deeper/pinker than the low-sun {@link #MIDNIGHT_SUN_SKY_RGB} gold wash — which still composes ON TOP. */
    public static final int MIDNIGHT_SUN_DUSK_RGB = 0xE8A07D;
    /** How far the twilight hold may pull the (vanilla-)night-darkened sky back to dusk: 0.80 = a strong hold
     *  (the night black never fully shows under a circling sun) that still leaves a breath of the night so the
     *  dusk is a held twilight, not a flat repaint. One-line P4 dial. */
    public static final double DUSK_HOLD_MAX_BLEND = 0.80;

    // ---- the curves ------------------------------------------------------------------------------------

    /**
     * Polar-night gloom strength 0..1 from the sun's (negative) elevation: 0 at/above the horizon, full at
     * {@link #GLOOM_FULL_DEPTH_DEG} below. Callers apply it ONLY when {@link SolarTilt#isPolarNight} (the
     * VISUAL band, onset {@code 90−|δ|} — NOT the narrower functional floor). NaN-safe (0).
     */
    public static double polarNightGloom01(double solarElevationDeg) {
        if (Double.isNaN(solarElevationDeg) || solarElevationDeg >= 0.0) {
            return 0.0;
        }
        return clamp01(-solarElevationDeg / GLOOM_FULL_DEPTH_DEG);
    }

    /**
     * Midnight-sun gold strength 0..1 from the sun's (positive) elevation: full when the sun grazes the
     * horizon, fading to 0 by {@link #GOLD_FADE_OUT_ELEV_DEG}. Callers apply it ONLY when
     * {@link SolarTilt#isMidnightSun}. Sun below the horizon (can't happen in-band; belt+suspenders) → 0.
     * NaN-safe (0).
     */
    public static double midnightSunGold01(double solarElevationDeg) {
        if (Double.isNaN(solarElevationDeg) || solarElevationDeg < 0.0) {
            return 0.0;
        }
        return clamp01((GOLD_FADE_OUT_ELEV_DEG - solarElevationDeg) / GOLD_FADE_OUT_ELEV_DEG);
    }

    /**
     * S14(a)(i) — the midnight-sun TWILIGHT HOLD strength 0..1 from the GLOBAL time-of-day fraction {@code [0,1)}:
     * 0 at global noon (frac 0.25 — the vanilla sky is already bright, nothing to hold), 0.5 at sunrise/sunset
     * (0.0 / 0.5), full 1.0 at global midnight (0.75). A smooth, PERIODIC curve —
     * {@code smoothstep((1 − sin(2π·frac))/2)} — so it is continuous across the {@code dayTime=0} wrap (no dawn
     * pop) and flat through the bright midday. This is the ONE midnight-sun curve driven by the vanilla clock
     * rather than the solar elevation, ON PURPOSE: the whole point of the band is that the sun is UP while the
     * vanilla clock darkens the sky, so the hold must track the CLOCK's night, not the (always-positive)
     * midnight-sun elevation. NaN-safe (0).
     */
    public static double twilightHold01(double globalTimeFrac) {
        if (Double.isNaN(globalTimeFrac)) {
            return 0.0;
        }
        double nightness = 0.5 * (1.0 - Math.sin(2.0 * Math.PI * globalTimeFrac));
        return smoothstep01(nightness);
    }

    /** Star brightness UNDER the midnight sun: suppressed toward 0 as the twilight hold deepens — no stars
     *  under a sun that never sets ({@code vanillaStar × (1 − hold01)}). At global midnight (hold 1) the
     *  vanilla night stars are fully quenched; by day (hold 0) vanilla's own ~0 is untouched. NOT storm-damped
     *  (an astronomical fact, not weather). NaN-safe (hold 0 ⇒ vanilla). */
    public static float suppressedStarBrightness(float vanillaStarBrightness, double hold01) {
        double h = Double.isNaN(hold01) ? 0.0 : clamp01(hold01);
        return (float) (vanillaStarBrightness * (1.0 - h));
    }

    /** The A4 storm damp: mood × {@code (1 − stormLevel01)} — the storm sky always wins; by full overcast
     *  (87.5°) every mood is 0. NaN storm reads as fully stormy (damp 0 — the conservative, A4-safe side). */
    public static double stormDamp(double mood01, double stormLevel01) {
        if (Double.isNaN(mood01)) {
            return 0.0;
        }
        double storm = Double.isNaN(stormLevel01) ? 1.0 : clamp01(stormLevel01);
        return clamp01(mood01) * (1.0 - storm);
    }

    /** Star-brightness lift for polar night: stars show in the dark day-hours as the gloom deepens. MAX with
     *  vanilla's own value, never below it (vanilla already brightens stars at real night). */
    public static float liftedStarBrightness(float vanillaStarBrightness, double gloom01) {
        double g = Double.isNaN(gloom01) ? 0.0 : clamp01(gloom01);
        return (float) Math.max(vanillaStarBrightness, g);
    }

    /** Blend an ARGB colour's RGB toward a target RGB by {@code t01}, preserving the base alpha exactly. */
    public static int blendRgb(int baseArgb, int targetRgb, double t01) {
        double t = clamp01(Double.isNaN(t01) ? 0.0 : t01);
        int a = (baseArgb >>> 24) & 0xFF;
        int r = blendChannel((baseArgb >> 16) & 0xFF, (targetRgb >> 16) & 0xFF, t);
        int g = blendChannel((baseArgb >> 8) & 0xFF, (targetRgb >> 8) & 0xFF, t);
        int b = blendChannel(baseArgb & 0xFF, targetRgb & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendChannel(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    // ---- S14(c): the shared night/dusk atmosphere tint — the ONE source of truth the FOG + WHITEOUT
    //      TOP-COAT read (they apply it to their OWN base colour instead of a duplicated palette) -------------

    /**
     * Pull a near-field surface's own base colour toward the SAME night/dusk sky the sky-dome mood paints, so
     * the depth fog and the whiteout top-coat dissolve distant terrain into the sky's colour at every hour
     * (S14c: no white wall, no popping silhouettes). ONE source of truth: the {@link #POLAR_NIGHT_SKY_RGB} /
     * {@link #MIDNIGHT_SUN_DUSK_RGB} palette + the {@link #polarNightGloom01} / {@link #twilightHold01} curves
     * the sky dome uses, applied here to the caller's base (the fog's blizzard-palette target, or the
     * top-coat's storm→white fill) — not a second copy of the colours (retires the old
     * {@code PolarFogLaw.NIGHT_FOG_RGB} duplicate).
     *
     * <p><b>Asymmetric storm damp — physical, not a hack.</b> The NIGHT darkening (gloom) is NOT storm-damped:
     * a blizzard at night is a DARK-out, so the near-field whiteout goes dark whatever the weather (the owner's
     * "white fog on a dark sky" fix; elevation-driven, so it also covers ordinary non-band vanilla night — a
     * plain world's polar whiteout darkens at night too). The midnight-sun DUSK hold IS storm-damped (via
     * {@link #stormDamp}): at the summer pole a blizzard scatters the low sun into a BRIGHT whiteout, so the
     * held dusk cedes to the storm above 85° exactly like the sky-dome mood, while below 85° (storm 0) the full
     * pink-gold dusk holds. Day / sun-up out of the midnight-sun band ⇒ base returned unchanged. NaN-safe.
     *
     * @param baseArgb          the surface's own colour (alpha preserved)
     * @param midnightSun       true iff the player is in the midnight-sun band ({@link SolarTilt#isMidnightSun})
     * @param solarElevationDeg the (effective) solar elevation — tilt (φ,δ) when enabled, the plain vanilla arc
     *                          (0,0) otherwise, so ordinary night still darkens
     * @param globalTimeFrac    the vanilla time-of-day fraction (drives the midnight-sun dusk hold)
     * @param stormLevel01      the polar storm level 0..1 (damps ONLY the dusk hold)
     */
    public static int atmosphereTint(int baseArgb, boolean midnightSun, double solarElevationDeg,
                                     double globalTimeFrac, double stormLevel01) {
        if (midnightSun) {
            double hold = stormDamp(twilightHold01(globalTimeFrac), stormLevel01);
            return blendRgb(baseArgb, MIDNIGHT_SUN_DUSK_RGB, hold * DUSK_HOLD_MAX_BLEND);
        }
        double gloom = polarNightGloom01(solarElevationDeg);
        return blendRgb(baseArgb, POLAR_NIGHT_SKY_RGB, gloom * GLOOM_MAX_BLEND);
    }

    private static double smoothstep01(double t) {
        double x = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
