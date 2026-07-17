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
    /** Max fraction of the sky colour the gloom may take (1.0 would flat-fill; 0.85 keeps a residual of the
     *  vanilla day tint so the "day that never comes" reads eerie rather than simply nighttime). */
    public static final double GLOOM_MAX_BLEND = 0.85;
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

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
