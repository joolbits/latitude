package com.example.globe.core;

/**
 * Phase 5 Crew 8 / S29 -- the one piece of non-trivial pure math behind the ground-truth {@code /latdev
 * markGlacial} command (Peetsa 2026-07-20, verbatim: "None of this is working. Locate crevasse and teleport
 * just puts me in the same spot... there is no falling through the snow... To make it easier just for dev,
 * can you turn on a simple color filter for the trap crevasses -- maybe typing a command causes them to glow
 * green?"). Where {@link CrevasseLocator} PREDICTS a carver's seeded start chunk (and so can miss the visible
 * opening, which the arc may cut up to 8 chunks away), {@code markGlacial} instead inspects REAL generated
 * blocks and marks what is actually there -- so it needs no seed math, only a local snowfield reference to
 * decide "does this column sit in a deep open shaft below the surrounding snow".
 *
 * <p>That reference is the same one {@code world.PowderCrevasseRoofFeature} uses to place the powder-snow
 * roofs: the local MAXIMUM of the WORLD_SURFACE height over a small square window (the feature's {@link
 * PowderRoofTrap#REFERENCE_WINDOW_RADIUS}). A cut slot column's low floor never raises that max; the
 * surrounding snowfield does -- so {@code windowedMax - ownSurface} is the shaft depth, fed straight into the
 * already-tested {@link PowderRoofTrap#isTrapCandidate(int, int)} depth gate. This class holds ONLY the
 * windowed-max (the feature computes it inline over a single 16x16 chunk; the command scans a multi-chunk
 * radius where some chunks are unloaded, so the grid carries an {@link #UNLOADED} sentinel the max must skip).
 * Zero Minecraft imports -- Core Logic layer, unit-testable in a plain JVM (mirrors {@link PowderRoofTrap},
 * {@link GlacialBlend}, {@link CrevasseLocator}); the MC-coupled glue (heightmap reads, powder-snow block
 * probes, particle beacons) lives in {@code LatitudeDevCommands.markGlacial}.
 */
public final class GlacialMarkScan {

    /**
     * Sentinel height for a column the scan could not read -- its chunk was not loaded, so we must NOT
     * force-generate it (that would silently change the world the tester is verifying). Chosen as {@link
     * Integer#MIN_VALUE}, far below any real block-Y (world floor is around -64), so a genuine height can
     * never collide with it and {@link #windowedMax} can skip it with a plain {@code != UNLOADED} test.
     */
    public static final int UNLOADED = Integer.MIN_VALUE;

    private GlacialMarkScan() {
    }

    /**
     * The local snowfield reference: the MAXIMUM value over the square window of Chebyshev {@code radius}
     * centred on grid cell ({@code cx}, {@code cz}), skipping {@link #UNLOADED} cells and clamping the window
     * to the grid's bounds. This is {@code world.PowderCrevasseRoofFeature}'s per-column reference (windowed
     * WORLD_SURFACE max) generalised to a cross-chunk grid with holes: the feature clamps its window to the
     * decorating chunk's 16x16, whereas a ground-truth detector spanning several chunks sees the true local
     * snowfield across chunk borders (an intentional fidelity gain, not a reinvention -- the window shape and
     * radius are the feature's).
     *
     * <p>Returns {@link #UNLOADED} iff no in-window cell holds a real height (an all-unloaded neighbourhood,
     * an empty grid, or a negative radius -- all degrade to "no reference here" rather than throwing). A
     * {@code radius} of 0 returns the centre cell's own value (or {@link #UNLOADED} if the centre is out of
     * bounds or unloaded); the centre itself does not need to be loaded for a neighbour to supply the max.
     * Pure and allocation-free; only array reads.
     *
     * @param grid    row-major surface heights, {@code grid[x][z]}; ragged rows are tolerated (each row's own
     *                length bounds its z-scan) and {@link #UNLOADED} marks unread columns
     * @param cx      centre X index into {@code grid}
     * @param cz      centre Z index into the row
     * @param radius  Chebyshev half-extent of the window (>= 0; a negative radius yields {@link #UNLOADED})
     */
    public static int windowedMax(int[][] grid, int cx, int cz, int radius) {
        if (grid == null || grid.length == 0 || radius < 0) {
            return UNLOADED;
        }
        int max = UNLOADED;
        int xLo = Math.max(0, cx - radius);
        int xHi = Math.min(grid.length - 1, cx + radius);
        for (int x = xLo; x <= xHi; x++) {
            int[] row = grid[x];
            if (row == null) {
                continue;
            }
            int zLo = Math.max(0, cz - radius);
            int zHi = Math.min(row.length - 1, cz + radius);
            for (int z = zLo; z <= zHi; z++) {
                int v = row[z];
                if (v != UNLOADED && v > max) {
                    max = v;
                }
            }
        }
        return max;
    }
}
