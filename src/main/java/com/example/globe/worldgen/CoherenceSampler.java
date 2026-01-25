package com.example.globe.worldgen;

import java.util.HashMap;
import java.util.Map;
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

    public static String mostCommonNeighbor(BiomeLookup lookup, int blockX, int blockZ, int radiusChunks, Predicate<String> includePredicate) {
        if (lookup == null) return "";

        int centerChunkX = blockX >> 4;
        int centerChunkZ = blockZ >> 4;

        Map<String, Integer> counts = new HashMap<>();
        for (int[] o : OFFSETS_CHUNKS) {
            int dx = o[0];
            int dz = o[1];
            if (dx == 0 && dz == 0) {
                continue;
            }
            if (Math.abs(dx) > radiusChunks || Math.abs(dz) > radiusChunks) {
                continue;
            }

            int sampleChunkX = centerChunkX + dx;
            int sampleChunkZ = centerChunkZ + dz;
            int sampleBlockX = (sampleChunkX << 4) + 8;
            int sampleBlockZ = (sampleChunkZ << 4) + 8;

            String id = lookup.get(sampleBlockX, sampleBlockZ);
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (includePredicate != null && !includePredicate.test(id)) {
                continue;
            }
            counts.put(id, counts.getOrDefault(id, 0) + 1);
        }

        String best = "";
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            int c = e.getValue();
            if (c > bestCount) {
                bestCount = c;
                best = e.getKey();
            }
        }

        return best;
    }
}
