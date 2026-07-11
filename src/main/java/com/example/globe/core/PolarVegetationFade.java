package com.example.globe.core;

/**
 * Pure latitude-driven keep-chance math for the polar small-vegetation fade
 * ({@code latitude.polarVegetationFade.enabled}). Peetsa 2026-07-10: grass / ferns / flowers /
 * sugarcane sprouting at 84-86deg reads as unimmersive, so surface vegetation should THIN with
 * latitude and be essentially gone against the bare snow/ice cap.
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
     * exactly 1.0, so those columns are bitwise-untouched. Chosen just inside the subpolar band so
     * mid-latitude meadows are never affected; the visible thinning only begins in the deep polar band.
     */
    public static final double ONSET_DEG = 78.0;

    /**
     * Absolute latitude (deg) at/above which vegetation is fully gone (keep-chance exactly 0.0) --
     * the bare snow/ice cap Peetsa asked for. The 78->86 span puts the ~half-stripped point near 82deg,
     * so grass/flowers are clearly sparse by the low-80s and absent by the mid-80s.
     */
    public static final double FULL_DEG = 86.0;

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
