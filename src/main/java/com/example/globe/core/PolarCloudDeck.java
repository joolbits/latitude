package com.example.globe.core;

/**
 * TEST 79 item 2 -- pure, plain-JVM colour math for the POLAR OVERCAST cloud deck. Zero Minecraft imports
 * (Core Logic layer, unit-testable). The client mixin ({@code CloudRendererPolarOvercastMixin}) reads the
 * player's absolute latitude, asks {@link PolarHazardWindow#ambientProgress} for the [85,90] envelope, and
 * hands the vanilla cloud tint (the {@code EnvironmentAttributes.CLOUD_COLOR_VISUAL} ARGB passed into
 * {@code CloudRenderer.render}) to {@link #stormCloudColor} to darken it toward a heavy storm grey.
 *
 * <p><b>Why a colour-only intervention.</b> Peetsa's pole screenshot shows vanilla's scattered bright-white
 * cloud puffs with clear sky between/beyond them, which breaks the storm. The cloud GEOMETRY (which cells are
 * filled) is baked from {@code clouds.png} at resource-load time and can't be latitude-driven without a
 * fragile, stateful rebuild, so we do NOT touch geometry. Instead we attack the CONTRAST that makes the puffs
 * read as distinct objects floating in clear sky: pull the whole deck toward a dark, desaturated storm grey and
 * push it toward opaque. Because the sibling {@code ClientLevelStormSkyMixin} already lifts the client rain
 * level to full overcast by ~87.5 deg (greying the sky disc + fading the sun), the gaps between cloud cells then
 * show storm-grey sky that closely matches the now-dark-grey clouds -- so the deck reads as one continuous
 * overcast mass rather than bright clumps over blue sky. Fancy mode's own top-vs-side face shading survives the
 * uniform tint, giving a free hint of the "darker underside" layered look without any extra geometry.
 *
 * <p><b>Seam-free + video-setting safe.</b> {@code ambientProgress <= 0} (at/below 85 deg, off-globe, or other
 * dimensions -- all decided by the caller) returns the input ARGB byte-for-byte, so the vanilla render path is
 * untouched everywhere except the polar approach. Cloud video setting OFF never calls {@code render} at all, so
 * off stays off; FAST and FANCY both route through {@code render}'s single colour arg, so both are covered.
 */
public final class PolarCloudDeck {

    private PolarCloudDeck() {
    }

    // Heavy overcast storm-grey the deck is pulled toward -- a dark, desaturated blue-grey. Deliberately DARKER
    // than the depth-fog STORM palette (92,108,132 in FogRendererPolarSetupMixin) so the ceiling reads as the
    // darker layer sitting ABOVE the lighter ground haze: dark stormy roof, white blizzard floor.
    public static final int STORM_CLOUD_R = 74;
    public static final int STORM_CLOUD_G = 84;
    public static final int STORM_CLOUD_B = 100;

    /** Fraction of the vanilla->storm-grey blend reached at the pole (ambientProgress == 1). Held below 1.0 so a
     *  trace of the vanilla cloud luminance survives and the deck never flattens to a dead slab -- the fog
     *  whiteout carries the near-field white, the clouds stay a believable dark overcast. */
    public static final float MAX_DARKEN = 0.85f;

    /** Alpha (0..255) the cloud tint is eased toward at the pole so the deck goes solid and stops letting sky
     *  show THROUGH the cloud body in fancy mode. Only ever RAISES opacity (never below the vanilla alpha). */
    public static final int MAX_ALPHA = 255;

    /** Vanilla->storm blend fraction in {@code [0, MAX_DARKEN]} for an ambient progress in {@code [0,1]}. */
    public static float darkenFraction(double ambientProgress) {
        return (float) (clamp01(ambientProgress) * MAX_DARKEN);
    }

    /**
     * Blend a vanilla ARGB cloud tint toward the heavy storm grey and toward opaque, scaled by the ambient
     * [85,90] progress. {@code ambientProgress <= 0} returns {@code argb} unchanged (seam-free). Pure int/bit
     * math -- no Minecraft types -- so it is exercised directly in the unit tests.
     *
     * @param argb            the vanilla cloud tint (ARGB8888, alpha in the high byte)
     * @param ambientProgress {@link PolarHazardWindow#ambientProgress} of {@code |lat|}, in {@code [0,1]}
     * @return the storm-tinted ARGB, or {@code argb} verbatim below the ambient onset
     */
    public static int stormCloudColor(int argb, double ambientProgress) {
        double p = clamp01(ambientProgress);
        if (p <= 0.0) {
            return argb;
        }
        float f = darkenFraction(p);
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = lerp(r, STORM_CLOUD_R, f);
        g = lerp(g, STORM_CLOUD_G, f);
        b = lerp(b, STORM_CLOUD_B, f);
        // Ease alpha toward opaque on the raw ambient (not the capped darken fraction). Guarded so it can only
        // ever tighten the deck -- if the vanilla tint is already >= MAX_ALPHA it is left alone.
        int aTarget = Math.max(a, MAX_ALPHA);
        a = lerp(a, aTarget, (float) p);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Integer channel lerp, rounded, clamped to a byte. */
    static int lerp(int from, int to, float t) {
        int v = Math.round(from + (to - from) * t);
        if (v < 0) {
            return 0;
        }
        return Math.min(255, v);
    }

    /** Clamp to {@code [0,1]} (NaN -> 0). */
    static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, v);
    }
}
