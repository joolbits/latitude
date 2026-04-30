package com.example.globe.world;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Coarse, deterministic ocean-distance approximation.
 *
 * - Samples continentalness on a 256-block grid to classify ocean-like cells.
 * - Computes cell distance to nearest ocean-like cell with a bounded BFS.
 * - Converts to block distance with bilinear interpolation to avoid chunky flips.
 *
 * All randomness comes from WORLD_SEED in LatitudeBiomes; no chunk/block scanning.
 */
public final class OceanDistanceField {

    private static final int CELL_SIZE = 256;
    private static final int MAX_SEARCH_CELLS = 64; // 64 cells * 256 = 16384 blocks cap
    private static final double OCEAN_LIKE_THRESHOLD = -0.19; // continentalness below this is treated as ocean

    private final long worldSeed;
    private final Long2IntOpenHashMap distanceCache = new Long2IntOpenHashMap();
    private final Short2BooleanOpenHashMap oceanFlagCache = new Short2BooleanOpenHashMap();
    private final Object cacheLock = new Object();

    public OceanDistanceField(long worldSeed) {
        this.worldSeed = worldSeed;
        distanceCache.defaultReturnValue(Integer.MIN_VALUE);
        oceanFlagCache.defaultReturnValue(false);
    }

    public int oceanDistanceBlocks(int blockX, int blockZ, MultiNoiseUtil.MultiNoiseSampler sampler) {
        if (sampler == null) {
            return Integer.MAX_VALUE;
        }
        // Identify surrounding cells for bilinear smoothing.
        int cellX = floorDiv(blockX, CELL_SIZE);
        int cellZ = floorDiv(blockZ, CELL_SIZE);
        int localX = Math.floorMod(blockX, CELL_SIZE);
        int localZ = Math.floorMod(blockZ, CELL_SIZE);
        double fx = localX / (double) CELL_SIZE;
        double fz = localZ / (double) CELL_SIZE;

        int d00 = cellDistance(cellX, cellZ, sampler);
        int d10 = cellDistance(cellX + 1, cellZ, sampler);
        int d01 = cellDistance(cellX, cellZ + 1, sampler);
        int d11 = cellDistance(cellX + 1, cellZ + 1, sampler);

        double i1 = lerp(d00, d10, fx);
        double i2 = lerp(d01, d11, fx);
        double interp = lerp(i1, i2, fz);
        return (int) Math.round(interp * CELL_SIZE);
    }

    private int cellDistance(int cx, int cz, MultiNoiseUtil.MultiNoiseSampler sampler) {
        long key = cellKey(cx, cz);
        int cached = cachedDistance(key);
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        if (isOceanCell(cx, cz, sampler)) {
            cacheDistance(key, 0);
            return 0;
        }
        // BFS in cell space (4-neighbor).
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cz, 0});
        Long2IntOpenHashMap visited = new Long2IntOpenHashMap();
        visited.defaultReturnValue(Integer.MAX_VALUE);
        visited.put(key, 0);

        int best = MAX_SEARCH_CELLS;
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            int x = cur[0], z = cur[1], dist = cur[2];
            if (dist >= MAX_SEARCH_CELLS || dist >= best) continue;
            int nx = x + 1, px = x - 1, nz = z + 1, pz = z - 1;
            int[][] neighbors = {{nx, z}, {px, z}, {x, nz}, {x, pz}};
            for (int[] n : neighbors) {
                long nk = cellKey(n[0], n[1]);
                int nextDist = dist + 1;
                if (nextDist >= visited.get(nk)) continue;
                visited.put(nk, nextDist);
                if (isOceanCell(n[0], n[1], sampler)) {
                    best = Math.min(best, nextDist);
                    continue;
                }
                q.add(new int[]{n[0], n[1], nextDist});
            }
        }
        cacheDistance(key, best);
        return best;
    }

    private boolean isOceanCell(int cx, int cz, MultiNoiseUtil.MultiNoiseSampler sampler) {
        short key = (short) ((cx * 131) ^ cz);
        Boolean cached = cachedOceanFlag(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        int sampleX = cx * CELL_SIZE + CELL_SIZE / 2;
        int sampleZ = cz * CELL_SIZE + CELL_SIZE / 2;
        int nx = sampleX >> 2;
        int nz = sampleZ >> 2;
        MultiNoiseUtil.NoiseValuePoint p = sampler.sample(nx, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, nz);
        double cont = MultiNoiseUtil.toFloat(p.continentalnessNoise());
        boolean ocean = cont < OCEAN_LIKE_THRESHOLD;
        cacheOceanFlag(key, ocean);
        return ocean;
    }

    private int cachedDistance(long key) {
        synchronized (cacheLock) {
            return distanceCache.get(key);
        }
    }

    private void cacheDistance(long key, int distance) {
        synchronized (cacheLock) {
            distanceCache.put(key, distance);
        }
    }

    private Boolean cachedOceanFlag(short key) {
        synchronized (cacheLock) {
            return oceanFlagCache.containsKey(key) ? Boolean.valueOf(oceanFlagCache.get(key)) : null;
        }
    }

    private void cacheOceanFlag(short key, boolean ocean) {
        synchronized (cacheLock) {
            oceanFlagCache.put(key, ocean);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        // If the signs are different and modulo not zero, round down.
        if ((a ^ b) < 0 && (a % b != 0)) {
            r--;
        }
        return r;
    }

    private static long cellKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFF_FFFFL);
    }
}
