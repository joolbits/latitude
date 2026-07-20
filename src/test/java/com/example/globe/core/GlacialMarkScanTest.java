package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure tests for {@link GlacialMarkScan#windowedMax} -- the local snowfield reference behind the ground-truth
 * {@code /latdev markGlacial} command (S29). The MC-glued half (heightmap reads, powder-snow probes, the green
 * particle beacons) lives in {@code LatitudeDevCommands.markGlacial} and is proven at live flight; the depth
 * gate it feeds is {@link PowderRoofTrap#isTrapCandidate} (covered by {@code PowderRoofTrapTest}). This locks
 * the two things that are easy to get wrong: skipping the {@link GlacialMarkScan#UNLOADED} sentinel, and
 * clamping the window to the grid at the edges.
 */
class GlacialMarkScanTest {

    @Test
    void sentinelIsFarBelowAnyRealHeight() {
        // The sentinel must never collide with a real block-Y (world floor ~ -64), so a plain != test skips it.
        assertEquals(Integer.MIN_VALUE, GlacialMarkScan.UNLOADED);
    }

    @Test
    void plainLocalMaxOverTheWindow() {
        int[][] grid = {
            {60, 61, 62},
            {63, 70, 64}, // the 70 is the local snowfield peak
            {65, 66, 67},
        };
        assertEquals(70, GlacialMarkScan.windowedMax(grid, 1, 1, 1), "radius-1 window sees the 70 peak");
    }

    @Test
    void radiusZeroReturnsTheCentreCell() {
        int[][] grid = {{60, 61}, {62, 63}};
        assertEquals(63, GlacialMarkScan.windowedMax(grid, 1, 1, 0), "radius 0 is just the centre column");
    }

    @Test
    void aPeakOutsideTheRadiusIsNotCounted() {
        // A 90 five cells away must not raise a radius-1 reference around the origin.
        int[][] grid = new int[6][6];
        for (int[] row : grid) {
            java.util.Arrays.fill(row, 64);
        }
        grid[5][5] = 90;
        assertEquals(64, GlacialMarkScan.windowedMax(grid, 1, 1, 1), "the far peak is out of the window");
        assertEquals(90, GlacialMarkScan.windowedMax(grid, 4, 4, 1), "and IS seen once the window reaches it");
    }

    @Test
    void unloadedCellsAreSkipped() {
        int U = GlacialMarkScan.UNLOADED;
        int[][] grid = {
            {U, U, U},
            {U, 72, U}, // only one real height in the whole window
            {U, U, U},
        };
        assertEquals(72, GlacialMarkScan.windowedMax(grid, 1, 1, 1), "the lone loaded column supplies the max");
    }

    @Test
    void centreUnloadedButANeighbourSuppliesTheReference() {
        int U = GlacialMarkScan.UNLOADED;
        // A cut/edge column can read unloaded itself yet still get a reference from surrounding snowfield.
        int[][] grid = {
            {80, 80, 80},
            {80, U, 80},
            {80, 80, 80},
        };
        assertEquals(80, GlacialMarkScan.windowedMax(grid, 1, 1, 1),
                "an unloaded centre still resolves from its loaded neighbours");
    }

    @Test
    void allUnloadedWindowReturnsUnloaded() {
        int U = GlacialMarkScan.UNLOADED;
        int[][] grid = {{U, U}, {U, U}};
        assertEquals(GlacialMarkScan.UNLOADED, GlacialMarkScan.windowedMax(grid, 0, 0, 1),
                "no real height in range -> no reference (UNLOADED), never a bogus min-value height");
    }

    @Test
    void windowClampsAtTheGridCornerWithoutOverrun() {
        int[][] grid = {
            {50, 51, 52},
            {53, 54, 55},
            {56, 57, 58},
        };
        // Corner centre with a radius that overruns both edges: must clamp, scan the whole grid, not throw.
        assertEquals(58, GlacialMarkScan.windowedMax(grid, 0, 0, 4), "overrunning radius clamps to the grid");
    }

    @Test
    void emptyOrDegenerateInputsDegradeToUnloaded() {
        assertEquals(GlacialMarkScan.UNLOADED, GlacialMarkScan.windowedMax(null, 0, 0, 1), "null grid");
        assertEquals(GlacialMarkScan.UNLOADED, GlacialMarkScan.windowedMax(new int[0][0], 0, 0, 1), "empty grid");
        assertEquals(GlacialMarkScan.UNLOADED, GlacialMarkScan.windowedMax(new int[][]{{64}}, 0, 0, -1),
                "a negative radius yields no reference");
    }
}
