package com.example.globe.world;

/**
 * Keeps the sulfur-pool surface restriction on the feature that can reach the
 * surface instead of erasing the underground sulfur-cave biome that contributes
 * every other decoration.
 */
public final class SulfurSurfaceExpressionPolicy {
    public static final int MAX_SURFACE_REACH_BLOCKS = 32;

    private SulfurSurfaceExpressionPolicy() {
    }

    public static boolean shouldSuppressPool(boolean sulfurPool,
                                             int originY,
                                             int surfaceY,
                                             boolean surfaceCompatible) {
        return sulfurPool
                && originY >= surfaceY - MAX_SURFACE_REACH_BLOCKS
                && !surfaceCompatible;
    }

    public static boolean shouldSuppressSpike(boolean sulfurSpike,
                                              boolean surfaceVisible,
                                              boolean surfaceCompatible) {
        return sulfurSpike && surfaceVisible && !surfaceCompatible;
    }
}
