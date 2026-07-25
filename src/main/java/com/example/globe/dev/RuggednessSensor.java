package com.example.globe.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ruggedness Sensor: samples the surface height around a point to estimate local relief.
 * Uses a 3x3 ring (center + 8 neighbors) at a given radius to keep it cheap and column-stable.
 */
public final class RuggednessSensor {
    private RuggednessSensor() {
    }

    public static Measurement measure(ServerLevel world, BlockPos pos, int ringBlocks) {
        // Snap to 4-block grid to avoid 1-block jitter
        int x = pos.getX() & ~3;
        int z = pos.getZ() & ~3;

        int c = surfaceY(world, x, z);
        int n = surfaceY(world, x, z - ringBlocks);
        int s = surfaceY(world, x, z + ringBlocks);
        int e = surfaceY(world, x + ringBlocks, z);
        int w = surfaceY(world, x - ringBlocks, z);
        int ne = surfaceY(world, x + ringBlocks, z - ringBlocks);
        int nw = surfaceY(world, x - ringBlocks, z - ringBlocks);
        int se = surfaceY(world, x + ringBlocks, z + ringBlocks);
        int sw = surfaceY(world, x - ringBlocks, z + ringBlocks);

        int[] ys = {n, s, e, w, ne, nw, se, sw};
        int[] deltas = new int[ys.length];
        int maxAbs = 0;
        int sumAbs = 0;
        for (int i = 0; i < ys.length; i++) {
            int d = Math.abs(ys[i] - c);
            deltas[i] = d;
            maxAbs = Math.max(maxAbs, d);
            sumAbs += d;
        }
        java.util.Arrays.sort(deltas);
        int robustDelta = deltas[deltas.length - 2]; // second-highest to reject single spikes
        double meanAbs = sumAbs / (double) ys.length;
        double axisGradient = (Math.abs(n - s) + Math.abs(e - w)) * 0.5;

        return new Measurement(ringBlocks, c, n, s, e, w, ne, nw, se, sw, maxAbs, meanAbs, axisGradient, robustDelta);
    }

    private static int surfaceY(ServerLevel world, int x, int z) {
        // -1 so we report the topmost solid block position, matching surface usage elsewhere.
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    public record Measurement(int ringBlocks,
                              int centerY, int northY, int southY, int eastY, int westY,
                              int northEastY, int northWestY, int southEastY, int southWestY,
                              int maxAbsDelta, double meanAbsDelta, double axisGradient, int robustDelta) {

        public boolean isRugged(int maxDeltaThreshold) {
            return maxAbsDelta >= maxDeltaThreshold;
        }

        public boolean isRuggedRobust(int deltaThreshold, int hyst) {
            return robustDelta >= (deltaThreshold + hyst);
        }
    }
}
