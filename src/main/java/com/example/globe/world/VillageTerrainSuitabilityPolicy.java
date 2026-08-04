package com.example.globe.world;

/**
 * Dependency-free physical terrain gate for fresh village starts.
 *
 * <p>A village can tolerate ordinary rolling ground, but not a footprint that crosses a cliff or
 * deep ravine. Production samples a fixed 5x5 grid across a 128-block start-centered square.
 * The bounded 25-column cost is paid only for a candidate village start. This is deliberately a
 * broad-footprint proxy, not a promise that every later jigsaw piece stays inside the square.
 */
public final class VillageTerrainSuitabilityPolicy {
    public static final int SAMPLE_RADIUS_BLOCKS = 64;
    public static final int SAMPLE_STEP_BLOCKS = 32;
    public static final int GRID_WIDTH = 5;
    public static final int SAMPLE_COUNT = GRID_WIDTH * GRID_WIDTH;
    // Permit broad hills up to a 3:8 rise across the full 128-block sample span.
    public static final int MAX_TOTAL_RELIEF_BLOCKS = SAMPLE_RADIUS_BLOCKS * 3 / 4;
    // Reject a cliff-like 3:4 rise between adjacent 32-block samples.
    public static final int MAX_NEIGHBOR_STEP_BLOCKS = SAMPLE_STEP_BLOCKS * 3 / 4;

    private VillageTerrainSuitabilityPolicy() {
    }

    /** Invalid or incomplete samples fail open; the production caller also fails open on errors. */
    public static boolean isSuitable(int[] heights) {
        if (heights == null || heights.length != SAMPLE_COUNT) {
            return true;
        }

        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int height : heights) {
            if (height == Integer.MIN_VALUE) {
                return true;
            }
            minimum = Math.min(minimum, height);
            maximum = Math.max(maximum, height);
        }
        if (maximum - minimum > MAX_TOTAL_RELIEF_BLOCKS) {
            return false;
        }

        for (int z = 0; z < GRID_WIDTH; z++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                int index = z * GRID_WIDTH + x;
                if (x + 1 < GRID_WIDTH
                        && Math.abs(heights[index] - heights[index + 1])
                        > MAX_NEIGHBOR_STEP_BLOCKS) {
                    return false;
                }
                if (z + 1 < GRID_WIDTH
                        && Math.abs(heights[index] - heights[index + GRID_WIDTH])
                        > MAX_NEIGHBOR_STEP_BLOCKS) {
                    return false;
                }
            }
        }
        return true;
    }
}
