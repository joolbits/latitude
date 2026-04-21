package com.example.globe.world;

import net.minecraft.world.level.Level;

public final class GlobeWorldSizeRuntime {
    private GlobeWorldSizeRuntime() {}

    public static int borderRadiusBlocks(Level world, int fallback) {
        if (world == null) return fallback;
        int radius = (int) Math.round(com.example.globe.util.LatitudeMath.halfSize(world.getWorldBorder()));
        return radius > 0 ? radius : fallback;
    }
}
