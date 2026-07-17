package com.example.globe.world;

/**
 * Dependency-free latitude boundary for village structure origins.
 */
public final class VillageLatitudePolicy {
    /**
     * Villages remain allowed through exactly this absolute latitude.
     */
    public static final double MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES = 80.0;

    private VillageLatitudePolicy() {
    }

    /**
     * Returns true only when the village origin is strictly beyond the allowed latitude.
     *
     * <p>Coordinates are measured from Z=0 because Latitude worlds set their world border center
     * to (0, 0). Radius selection mirrors LatitudeBiomes' established extreme-polar predicate:
     * use the active world-size radius when available, otherwise use the caller's fallback, and
     * clamp an invalid fallback to one block.
     */
    public static boolean shouldVetoVillageOrigin(
            double blockZ,
            int activeRadiusBlocks,
            int fallbackRadiusBlocks) {
        int radius = activeRadiusBlocks > 0 ? activeRadiusBlocks : fallbackRadiusBlocks;
        int effectiveRadius = Math.max(1, radius);
        double latitudeNumerator = Math.abs(blockZ) * 90.0;
        double allowedNumerator =
                MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES * (double) effectiveRadius;
        return latitudeNumerator > allowedNumerator;
    }

    public static double absoluteLatitudeDegrees(
            double blockZ,
            int activeRadiusBlocks,
            int fallbackRadiusBlocks) {
        int radius = activeRadiusBlocks > 0 ? activeRadiusBlocks : fallbackRadiusBlocks;
        return Math.abs(blockZ) * 90.0 / (double) Math.max(1, radius);
    }
}
