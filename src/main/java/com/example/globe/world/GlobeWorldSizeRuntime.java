package com.example.globe.world;

import net.minecraft.world.World;

public final class GlobeWorldSizeRuntime {
    private GlobeWorldSizeRuntime() {}

    public static int borderRadiusBlocks(World world, int fallback) {
        if (world == null) return fallback;
        double size = world.getWorldBorder().getSize();
        int radius = (int) Math.round(size / 2.0);
        return radius > 0 ? radius : fallback;
    }
}
