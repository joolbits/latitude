package com.example.globe.worldgen;

import java.util.function.Predicate;

public final class CoherenceSampler {
    private CoherenceSampler() {
    }

    @FunctionalInterface
    public interface BiomeLookup {
        String get(int blockX, int blockZ);
    }

    private static final int[][] OFFSETS_CHUNKS = new int[][]{
            {0, 0},
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    public static int sampleCount() {
        return OFFSETS_CHUNKS.length;
    }

    public static int countNeighborsMatching(BiomeLookup lookup, int blockX, int blockZ, int radiusChunks, Predicate<String> predicate) {
        if (lookup == null || predicate == null) return 0;

        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;

        int matches = 0;
        for (int[] o : OFFSETS_CHUNKS) {
            int dx = o[0];
            int dz = o[1];
            if (Math.abs(dx) > radiusChunks || Math.abs(dz) > radiusChunks) {
                continue;
            }

            int sampleChunkX = centerChunkX + dx;
            int sampleChunkZ = centerChunkZ + dz;

            int sampleBlockX = (sampleChunkX << 4) + 8;
            int sampleBlockZ = (sampleChunkZ << 4) + 8;

            String id = lookup.get(sampleBlockX, sampleBlockZ);
            if (predicate.test(id)) {
                matches++;
            }
        }
        return matches;
    }
}
