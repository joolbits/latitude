package com.example.globe.world;

/** Dependency-free decision for vegetation rooted in Latitude's alpine snow cap. */
public final class AlpineVegetationPolicy {

    /** Surface kind painted as snow by {@code AlpineSurfaceMixin}. */
    public static final int SNOW_SURFACE_KIND = 2;

    private AlpineVegetationPolicy() {
    }

    /**
     * Blocks downward from a plant write to its actual footing. Both halves of a double plant
     * therefore judge the same block and cannot receive opposite decisions.
     */
    public static int footingOffsetBlocks(boolean upperHalfOfDoublePlant) {
        return upperHalfOfDoublePlant ? 2 : 1;
    }

    /** True only for plant material actually rooted in the snow cap. */
    public static boolean shouldSuppressAlpineVegetation(
            int footingSurfaceKind,
            boolean footingIsSnowBlock,
            boolean foliage,
            boolean vegetationBlock) {
        return footingSurfaceKind == SNOW_SURFACE_KIND
                && footingIsSnowBlock
                && (foliage || vegetationBlock);
    }
}
