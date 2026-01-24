package com.example.globe.util;

public final class ValueNoise2D {
    private ValueNoise2D() {
    }

    private static long mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    private static double toUnit(long h) {
        return ((h >>> 11) * 0x1.0p-53);
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double valueAt(long seed, int x, int z) {
        long h = seed ^ (((long) x) << 32) ^ (z & 0xffffffffL);
        return toUnit(mix64(h));
    }

    public static double sampleBlocks(long seed, int blockX, int blockZ, int scaleBlocks) {
        double x = blockX / (double) scaleBlocks;
        double z = blockZ / (double) scaleBlocks;

        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);

        double tx = smooth(x - x0);
        double tz = smooth(z - z0);

        double v00 = valueAt(seed, x0, z0);
        double v10 = valueAt(seed, x0 + 1, z0);
        double v01 = valueAt(seed, x0, z0 + 1);
        double v11 = valueAt(seed, x0 + 1, z0 + 1);

        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
    }
}
