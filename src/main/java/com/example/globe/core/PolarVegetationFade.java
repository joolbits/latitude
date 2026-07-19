package com.example.globe.core;

/**
 * Pure latitude-driven keep-chance math for the polar small-vegetation fade
 * ({@code latitude.polarVegetationFade.enabled}). Peetsa 2026-07-10: grass / ferns / flowers /
 * sugarcane sprouting in the deep polar low-80s reads as unimmersive, so surface vegetation should
 * THIN with latitude and be essentially gone against the bare snow/ice cap.
 *
 * <p>This class holds ONLY the decision math, mirroring {@link com.example.globe.core.geo.EdgeOceanRamp}:
 * a {@link #keepChance01(double)} latitude ramp plus a {@link #stripByNoise(double, double)} fray
 * decision that the caller feeds a coherent {@code ValueNoise2D} sample. The coherent noise (Art VI --
 * never a hard ring/shelf) makes the thinning a natural frayed fade rather than a straight cutoff line.
 *
 * <p>Zero Minecraft imports -- Core Logic layer, unit-testable in a plain JVM. The world-side wiring
 * ({@code LatitudeBiomes.polarVegetationFadeStrips}) derives {@code |lat|} from the column Z and supplies
 * the fray noise; this class owns the ramp and the keep/strip threshold.
 *
 * <p><b>Byte-identical / containment guarantee:</b> {@link #keepChance01(double)} returns exactly
 * {@code 1.0} for any {@code |lat| <= }{@link #ONSET_DEG} (in particular the exact boundary), so the
 * caller's {@code keep >= 1.0} short-circuit means every column below the onset is untouched. It returns
 * exactly {@code 0.0} at/above {@link #FULL_DEG}, and a {@code NaN} latitude degrades to {@code 1.0}
 * (keep everything -- never strip on a bad input). Both hemispheres are covered because the ramp keys on
 * {@code Math.abs(deg)}.
 */
public final class PolarVegetationFade {

    /**
     * Absolute latitude (deg) at/below which vegetation is fully kept. Below this the keep-chance is
     * exactly 1.0, so those columns are bitwise-untouched. Chosen inside the subpolar band so mid-latitude
     * meadows are never affected; the visible thinning begins where Earth's tree line does (~66-70 deg) and
     * completes by the polar-desert latitude. Live-tunable via
     * {@code -Dlatitude.polarVegetationFade.onsetDeg} (defaults 72) so a polar look session can retune the
     * ramp without a rebuild; malformed values degrade to the default.
     * (S13 2026-07-17: 78->76. S21c 2026-07-19: onset lowered 76->72 as the fade finish moved 82->80 --
     * "veg to 80", Earth-calibrated: tree line 66-70, tundra shrubs to ~78, polar desert 80+.)
     */
    public static final double ONSET_DEG =
            parseDegOrDefault(System.getProperty("latitude.polarVegetationFade.onsetDeg"), 72.0);

    /**
     * Absolute latitude (deg) at/above which vegetation is fully gone (keep-chance exactly 0.0) --
     * the bare snow/ice cap Peetsa asked for. The 72->80 span (S21c) puts the ~half-stripped point near
     * 76deg, so grass/flowers are clearly sparse by the mid-70s and absent by 80deg -- the ONE threshold
     * where surface vegetation, villages, and calm air all end.
     *
     * <p><b>Barrens coupling BROKEN (S21c, deliberate).</b> This finish USED to be KEEP-SHARED with the
     * Polar Barrens onset ({@link LatitudeV2Flags#POLAR_BARRENS_ONSET_DEG}) -- the barrens began exactly
     * where the last vegetation died. S21c decoupled them at the owner's request: the fade now finishes at
     * 80 while the Barrens onset stays independently at 82, so the 80-82 band reads as BARE TUNDRA (stripped
     * of grass, but not yet the Barrens biome) before the frozen waste claims the land at 82.
     *
     * <p>Live-tunable via {@code -Dlatitude.polarVegetationFade.fullDeg} (defaults 80; S13 moved it 86->82,
     * S21c 82->80); clamped above {@link #ONSET_DEG} so the smoothstep denominator can never be zero or
     * negative.
     */
    public static final double FULL_DEG = Math.max(
            parseDegOrDefault(System.getProperty("latitude.polarVegetationFade.fullDeg"), 80.0),
            ONSET_DEG + 0.5);

    /**
     * S11(c) FIREFLY BUSH BAN (Peetsa, TEST 101 -- the owner has flagged firefly bushes TWICE with
     * exclamation marks): {@code firefly_bush} placement is banned OUTRIGHT at/above this absolute latitude
     * -- 50 deg, the SUBPOLAR onset ({@code LatitudeMath.TEMPERATE_MAX_FRAC} boundary), far equatorward of
     * the general fade's 72-deg onset, because a glowing summer-evening plant reads absurd anywhere in the
     * subpolar/polar cold, not just at the cap. A HARD line, deliberately un-frayed: a scattered decorative
     * plant simply stops appearing -- there is no contiguous visual seam for a fray to soften (unlike the
     * barrens/sea-freeze edges). FIREFLY-SPECIFIC: every other small-vegetation placement keeps the ordinary
     * 72/80 fade untouched. Live-tunable via {@code -Dlatitude.polarVegetationFade.fireflyBanDeg} (the
     * veg-fade dial family; forwarded in build.gradle in the SAME pass, L17 discipline).
     */
    public static final double FIREFLY_BAN_DEG =
            parseDegOrDefault(System.getProperty("latitude.polarVegetationFade.fireflyBanDeg"), 50.0);

    /** True iff {@code firefly_bush} placement is banned at this absolute latitude ({@code |deg| >=}
     *  {@link #FIREFLY_BAN_DEG}). NaN degrades to false (keep -- the safe, byte-identical direction). */
    public static boolean bansFirefly(double absLatDeg) {
        double a = Math.abs(absLatDeg);
        if (Double.isNaN(a)) {
            return false;
        }
        return a >= FIREFLY_BAN_DEG;
    }

    private static double parseDegOrDefault(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            double v = Double.parseDouble(raw);
            return Double.isFinite(v) ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Vertical margin (blocks) below the column's world-surface heightmap within which a placement
     * still counts as "surface" and is therefore eligible for the fade. Surface grass / ferns / flowers /
     * sugarcane place ON (or one block into) the world-surface top, so a small margin comfortably catches
     * them; lush-cave vegetation (moss, pale moss, spore blossom, dripleaf, cave vines, glow berries,
     * cave mushrooms) generates dozens of blocks under the surface and stays well outside this band, so
     * the fade never strips underground growth. Kept deliberately tight (5) so only the true surface
     * layer under the polar cap is thinned.
     */
    public static final int SURFACE_MARGIN = 5;

    private PolarVegetationFade() {
    }

    /**
     * True when a placement at {@code originY} is close enough to the column's world-surface
     * ({@code surfaceY}) to count as surface vegetation eligible for the polar fade -- i.e.
     * {@code originY >= surfaceY - }{@link #SURFACE_MARGIN}. Underground (lush-cave) placements sit far
     * below the surface heightmap and return {@code false}, so they are never stripped. Pure integer
     * comparison; no Minecraft types, so the caller reads {@code surfaceY} from the heightmap and passes
     * both values in.
     */
    public static boolean nearSurface(int originY, int surfaceY) {
        return originY >= surfaceY - SURFACE_MARGIN;
    }

    /**
     * Keep-chance in {@code [0,1]} for a column at the given (signed or unsigned) latitude degrees.
     * Monotonically NON-INCREASING in {@code |deg|}: exactly {@code 1.0} at/below {@link #ONSET_DEG}
     * (incl. the exact boundary), smoothstep-fading to exactly {@code 0.0} at/above {@link #FULL_DEG}.
     * A {@code NaN} input returns {@code 1.0} (keep -- never strip on bad data).
     */
    public static double keepChance01(double deg) {
        double a = Math.abs(deg);
        if (Double.isNaN(a)) {
            return 1.0;
        }
        if (a <= ONSET_DEG) {
            return 1.0;
        }
        if (a >= FULL_DEG) {
            return 0.0;
        }
        double t = (a - ONSET_DEG) / (FULL_DEG - ONSET_DEG);
        double s = t * t * (3.0 - 2.0 * t); // smoothstep 0->1
        return 1.0 - s;                      // keep fades 1->0
    }

    /**
     * Decide whether a vegetation placement should be STRIPPED (cancelled), given the column's
     * {@link #keepChance01(double) keep chance} and a coherent fray-noise sample in {@code [0,1]}.
     * A placement survives when {@code noise01 < keepChance01}, so the expected surviving fraction over
     * a neighbourhood equals the keep chance there. {@code keepChance01 >= 1.0} never strips (below onset);
     * {@code keepChance01 <= 0.0} always strips (the pole cap).
     */
    public static boolean stripByNoise(double keepChance01, double noise01) {
        if (keepChance01 >= 1.0) {
            return false;
        }
        if (keepChance01 <= 0.0) {
            return true;
        }
        return noise01 >= keepChance01;
    }
}
