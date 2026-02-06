package com.example.globe.debug;

import net.minecraft.util.math.BlockPos;

public final class WarmSnowTrapStats {
    public static volatile long calls = 0;
    public static volatile long snowHits = 0;
    public static volatile long rewrites = 0;

    public static volatile BlockPos lastPos = null;
    public static volatile String lastBlock = null;
    public static volatile double lastT = -1;

    public static final boolean DEBUG_WARM_SNOW_STATS = Boolean.getBoolean("latitude.debugWarmSnowTrap");

    private WarmSnowTrapStats() {}
}
