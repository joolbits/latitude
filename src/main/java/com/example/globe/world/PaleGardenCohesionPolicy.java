package com.example.globe.world;

/**
 * Dependency-free geometry for keeping the entire Pale Garden core inside both its latitude band
 * and the coarse Manhattan ocean-distance field.
 */
public final class PaleGardenCohesionPolicy {
    private static final double MANHATTAN_TO_EUCLIDEAN_BOUND = Math.sqrt(2.0);

    private PaleGardenCohesionPolicy() {
    }

    public static double maximumCoreRadius(
            double requestedMaximum,
            int centerOceanDistance,
            int minimumOceanClearance,
            int centerBandClearance,
            int bandEdgePadding) {
        double oceanSafeRadius = (
                (double) centerOceanDistance - Math.max(0, minimumOceanClearance))
                / MANHATTAN_TO_EUCLIDEAN_BOUND;
        double bandSafeRadius = (double) centerBandClearance - Math.max(0, bandEdgePadding);
        return Math.max(
                0.0,
                Math.min(
                        Math.max(0.0, requestedMaximum),
                        Math.min(oceanSafeRadius, bandSafeRadius)));
    }

    public static boolean wholeCoreMeetsOceanClearance(
            int centerOceanDistance,
            double coreRadius,
            int minimumOceanClearance) {
        double worstCaseCoreDistance = (double) centerOceanDistance
                - (Math.max(0.0, coreRadius) * MANHATTAN_TO_EUCLIDEAN_BOUND);
        return worstCaseCoreDistance + 1.0e-9 >= Math.max(0, minimumOceanClearance);
    }

    /**
     * Scales a radial blob's base radius so its full positive wobble reaches, but never exceeds,
     * the safe maximum. Scaling the base preserves the boundary variation; clipping every radial
     * sample to the maximum would turn a constrained blob into a visible circle.
     */
    public static double baseRadiusPreservingWobble(
            double requestedBaseRadius,
            double wobbleFraction,
            double safeMaximumRadius) {
        double requested = Math.max(0.0, requestedBaseRadius);
        double wobble = Math.max(0.0, wobbleFraction);
        double safeMaximum = Math.max(0.0, safeMaximumRadius);
        return Math.min(requested, safeMaximum / (1.0 + wobble));
    }
}
