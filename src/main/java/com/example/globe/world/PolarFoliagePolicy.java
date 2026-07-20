package com.example.globe.world;

/**
 * Strict polar-foliage boundary and extensible foliage classification.
 *
 * <p>This policy is deliberately independent from the 74.5-degree biome ecology clamp and the
 * village placement policy. Foliage remains eligible through exactly 80 degrees and is suppressed
 * only beyond it.
 */
public final class PolarFoliagePolicy {
    public static final double MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES = 80.0;

    private PolarFoliagePolicy() {
    }

    public static double absoluteLatitudeDegrees(
            double blockZ,
            int activeRadiusBlocks,
            int borderRadiusFallback) {
        int radius = activeRadiusBlocks > 0 ? activeRadiusBlocks : borderRadiusFallback;
        return Math.abs(blockZ) * 90.0 / Math.max(1, radius);
    }

    public static boolean isBeyondLimit(
            double blockZ,
            int activeRadiusBlocks,
            int borderRadiusFallback) {
        return absoluteLatitudeDegrees(blockZ, activeRadiusBlocks, borderRadiusFallback)
                > MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES;
    }

    public static boolean shouldSuppressSimpleBlock(
            boolean beyondLimit,
            boolean foliage,
            boolean sweetBerryBush) {
        if (sweetBerryBush) {
            return false;
        }
        return beyondLimit && foliage;
    }
}
