package com.example.globe.world;

/**
 * Closed placement rule for Minecraft's Dappled Forest.
 *
 * <p>The biome is a temperate lowland forest, but it is not a general-purpose temperate forest.
 * Mojang describes it as living near cold biomes, so Latitude admits it only in the equatorward
 * half of the existing noise-warped temperate/subpolar transition envelope. Both ordinary biome
 * selection and the exact-coverage planner call this same rule.</p>
 */
public final class DappledForestPlacementPolicy {
    public static final String BIOME_ID = "minecraft:dappled_forest";
    public static final int VISIBLE_CORE_RADIUS_BLOCKS = 32;

    private DappledForestPlacementPolicy() {
    }

    public static boolean isEligible(
            boolean temperateLowland,
            double boundaryDeltaBlocks,
            double transitionHalfWidthBlocks) {
        return temperateLowland
                && Double.isFinite(boundaryDeltaBlocks)
                && transitionHalfWidthBlocks > 0.0
                && boundaryDeltaBlocks >= -transitionHalfWidthBlocks
                && boundaryDeltaBlocks <= 0.0;
    }
}
