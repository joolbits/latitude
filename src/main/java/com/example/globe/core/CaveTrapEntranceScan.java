package com.example.globe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure grouping law for the worldgen-only {@code globe:cave_trap_powder_snow} entrance signature.
 *
 * <p>The Minecraft command supplies only exact custom-block hits from already-loaded full chunks. A patch is
 * cardinally connected on one Y plane: an entrance is a carpet, whereas two carpets one block apart in height
 * are separate encounters. This class deliberately knows nothing about terrain planning or generation seeds.
 */
public final class CaveTrapEntranceScan {
    private static final int[][] CARDINAL_DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private CaveTrapEntranceScan() {
    }

    /** One exact custom-block hit in world block coordinates. */
    public record Cell(int x, int y, int z) {
    }

    /** One deterministic entrance patch, represented by a central real signature block. */
    public record Patch(int x, int y, int z, int blockCount, List<Cell> cells) {
        public Patch {
            cells = List.copyOf(cells);
        }
    }

    /**
     * Group exact signature blocks into one patch per connected entrance. Duplicate observations are ignored;
     * output is ordered by the first x/y/z cell so repeated scans of unchanged chunks have stable chat order.
     */
    public static List<Patch> groupPatches(Collection<Cell> observedHits) {
        if (observedHits == null || observedHits.isEmpty()) {
            return List.of();
        }
        Set<Cell> remaining = new HashSet<>(observedHits);
        List<Cell> seeds = remaining.stream()
                .sorted(Comparator.comparingInt(Cell::x)
                        .thenComparingInt(Cell::y)
                        .thenComparingInt(Cell::z))
                .toList();
        List<Patch> patches = new ArrayList<>();
        for (Cell seed : seeds) {
            if (!remaining.remove(seed)) {
                continue;
            }
            List<Cell> cells = new ArrayList<>();
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                Cell cell = queue.removeFirst();
                cells.add(cell);
                for (int[] direction : CARDINAL_DIRECTIONS) {
                    Cell neighbor = new Cell(cell.x() + direction[0], cell.y(), cell.z() + direction[1]);
                    if (remaining.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            cells.sort(Comparator.comparingInt(Cell::x)
                    .thenComparingInt(Cell::y)
                    .thenComparingInt(Cell::z));
            patches.add(toPatch(cells));
        }
        patches.sort(Comparator.comparingInt(Patch::x)
                .thenComparingInt(Patch::y)
                .thenComparingInt(Patch::z));
        return List.copyOf(patches);
    }

    /**
     * Return only the section indices whose palette can contain the exact signature. Keeping this small piece
     * of section accounting pure makes the command's empty-section fast path testable without Minecraft.
     */
    public static List<Integer> candidateSectionIndices(boolean[] paletteMayContainSignature) {
        if (paletteMayContainSignature == null || paletteMayContainSignature.length == 0) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < paletteMayContainSignature.length; index++) {
            if (paletteMayContainSignature[index]) {
                indices.add(index);
            }
        }
        return List.copyOf(indices);
    }

    /** Minecraft section zero begins at the owning chunk's real minimum build Y, not a hard-coded world floor. */
    public static int sectionStartY(int chunkMinY, int sectionIndex) {
        if (sectionIndex < 0) {
            throw new IllegalArgumentException("sectionIndex must be non-negative");
        }
        return Math.addExact(chunkMinY, Math.multiplyExact(sectionIndex, 16));
    }

    private static Patch toPatch(List<Cell> cells) {
        long sumX = 0L;
        long sumZ = 0L;
        for (Cell cell : cells) {
            sumX += cell.x();
            sumZ += cell.z();
        }
        double centreX = (double) sumX / cells.size();
        double centreZ = (double) sumZ / cells.size();
        Cell representative = cells.stream()
                .min(Comparator.comparingDouble((Cell cell) -> squaredDistance(cell, centreX, centreZ))
                        .thenComparingInt(Cell::x)
                        .thenComparingInt(Cell::z))
                .orElseThrow();
        return new Patch(representative.x(), representative.y(), representative.z(), cells.size(), cells);
    }

    private static double squaredDistance(Cell cell, double centreX, double centreZ) {
        double dx = cell.x() - centreX;
        double dz = cell.z() - centreZ;
        return dx * dx + dz * dz;
    }
}
