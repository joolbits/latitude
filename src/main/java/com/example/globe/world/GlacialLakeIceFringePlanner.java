package com.example.globe.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, deterministic planner for the glacial-caves lake fringe. Topology includes uncovered source water
 * plus plain ice written by a prior neighboring invocation, while the source-water subset remains the only
 * legal write target. This preserves one immutable lake shape across chunk invocation order.
 */
final class GlacialLakeIceFringePlanner {

    static final int MIN_Y = 0;
    /** Highest floor/surface band the hazardous glacial-caves dressing may touch. */
    static final int MAX_Y = 47;
    private static final int SHORE_BAND_MAX = 2;
    private static final int CORE_DISTANCE = 3;
    private static final int CORE_PROXIMITY = 12;
    private static final long SALT = 0x474c414349414c4cL;

    private GlacialLakeIceFringePlanner() {
    }

    record Cell(int x, int y, int z) {
    }

    record OwnerChunk(int x, int z) {
        boolean owns(Cell cell) {
            return (cell.x >> 4) == x && (cell.z >> 4) == z;
        }
    }

    record Cluster(Cell anchor, List<Cell> cells) {
        Cluster {
            cells = List.copyOf(cells);
        }
    }

    static List<Cell> plan(long worldSeed, Set<Cell> observedSurfaceWater, OwnerChunk owner) {
        return plan(worldSeed, observedSurfaceWater, observedSurfaceWater, owner);
    }

    static List<Cell> plan(
            long worldSeed, Set<Cell> observedTopology, Set<Cell> sourceWater, OwnerChunk owner) {
        List<Cell> writes = new ArrayList<>();
        for (Cluster cluster : planClusters(worldSeed, observedTopology, sourceWater)) {
            for (Cell cell : cluster.cells()) {
                if (owner.owns(cell) && sourceWater.contains(cell)) {
                    writes.add(cell);
                }
            }
        }
        return List.copyOf(writes);
    }

    static List<Cluster> planClusters(long worldSeed, Set<Cell> observedSurfaceWater) {
        return planClusters(worldSeed, observedSurfaceWater, observedSurfaceWater);
    }

    static List<Cluster> planClusters(
            long worldSeed, Set<Cell> observedTopology, Set<Cell> sourceWater) {
        Set<Cell> topology = new HashSet<>();
        for (Cell cell : observedTopology) {
            if (cell.y >= MIN_Y && cell.y <= MAX_Y) {
                topology.add(cell);
            }
        }
        Set<Cell> eligibleSources = new HashSet<>(sourceWater);
        eligibleSources.retainAll(topology);
        Map<Cell, Component> components = components(topology, eligibleSources);
        List<Candidate> candidates = new ArrayList<>();
        for (Component component : new HashSet<>(components.values())) {
            if (!component.hasSourceWater || component.cores.isEmpty()) {
                continue; // Ice-only components and narrow pools/channels stay untouched.
            }
            for (Cell anchor : component.cells) {
                int shoreDistance = component.distance.get(anchor);
                if (shoreDistance < 1 || shoreDistance > SHORE_BAND_MAX) {
                    continue;
                }
                long hash = coordinateHash(worldSeed, anchor);
                if (Long.remainderUnsigned(hash, 7) != 0) {
                    continue; // Sparse support for the warning; open water remains dominant.
                }
                List<Cell> shape = shape(anchor, hash);
                if (!component.cells.containsAll(shape) || !isShoreBand(shape, component.distance)
                        || !hasNearbyCore(anchor, component.cores)) {
                    continue;
                }
                candidates.add(new Candidate(anchor, shape, hash));
            }
        }
        candidates.sort(Candidate.ORDER);
        Set<Cell> claimed = new HashSet<>();
        List<Cluster> accepted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.cells.stream().anyMatch(claimed::contains)) {
                continue;
            }
            claimed.addAll(candidate.cells);
            accepted.add(new Cluster(candidate.anchor, candidate.cells));
        }
        return List.copyOf(accepted);
    }

    private static Map<Cell, Component> components(Set<Cell> topology, Set<Cell> sourceWater) {
        Map<Cell, Component> membership = new HashMap<>();
        Set<Cell> unvisited = new HashSet<>(topology);
        while (!unvisited.isEmpty()) {
            Cell start = unvisited.iterator().next();
            Set<Cell> cells = new HashSet<>();
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            queue.add(start);
            unvisited.remove(start);
            while (!queue.isEmpty()) {
                Cell cell = queue.removeFirst();
                cells.add(cell);
                for (Cell neighbor : neighbors(cell)) {
                    if (unvisited.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            Map<Cell, Integer> distance = shoreDistance(cells);
            List<Cell> cores = cores(cells, distance);
            boolean hasSourceWater = cells.stream().anyMatch(sourceWater::contains);
            Component component = new Component(cells, distance, cores, hasSourceWater);
            for (Cell cell : cells) {
                membership.put(cell, component);
            }
        }
        return membership;
    }

    private static Map<Cell, Integer> shoreDistance(Set<Cell> cells) {
        Map<Cell, Integer> distance = new HashMap<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        for (Cell cell : cells) {
            boolean shore = false;
            for (Cell neighbor : neighbors(cell)) {
                if (!cells.contains(neighbor)) {
                    shore = true;
                    break;
                }
            }
            if (shore) {
                // Distance is one-based for player-facing shoreline language: edge water is the first
                // fringe block, so 1-2 means the ice can actually touch land without reaching inward.
                distance.put(cell, 1);
                queue.addLast(cell);
            }
        }
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            int nextDistance = distance.get(cell) + 1;
            for (Cell neighbor : neighbors(cell)) {
                if (cells.contains(neighbor) && !distance.containsKey(neighbor)) {
                    distance.put(neighbor, nextDistance);
                    queue.addLast(neighbor);
                }
            }
        }
        return distance;
    }

    private static List<Cell> cores(Set<Cell> cells, Map<Cell, Integer> distance) {
        List<Cell> cores = new ArrayList<>();
        for (Cell cell : cells) {
            Cell east = new Cell(cell.x + 1, cell.y, cell.z);
            Cell south = new Cell(cell.x, cell.y, cell.z + 1);
            Cell southeast = new Cell(cell.x + 1, cell.y, cell.z + 1);
            if (cells.contains(east) && cells.contains(south) && cells.contains(southeast)
                    && distance.get(cell) >= CORE_DISTANCE && distance.get(east) >= CORE_DISTANCE
                    && distance.get(south) >= CORE_DISTANCE && distance.get(southeast) >= CORE_DISTANCE) {
                cores.add(cell);
            }
        }
        return cores;
    }

    private static boolean hasNearbyCore(Cell anchor, List<Cell> cores) {
        for (Cell core : cores) {
            if (Math.abs(core.x - anchor.x) + Math.abs(core.z - anchor.z) <= CORE_PROXIMITY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isShoreBand(List<Cell> cells, Map<Cell, Integer> distance) {
        return cells.stream().allMatch(cell -> {
            int value = distance.get(cell);
            return value >= 1 && value <= SHORE_BAND_MAX;
        });
    }

    private static List<Cell> shape(Cell anchor, long hash) {
        int direction = (int) ((hash >>> 3) & 3L);
        int dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
        int dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
        int px = -dz;
        int pz = dx;
        int kind = (int) ((hash >>> 5) % 6);
        List<Cell> cells = new ArrayList<>();
        cells.add(anchor);
        switch (kind) {
            case 0 -> cells.add(offset(anchor, dx, dz));
            case 1 -> {
                cells.add(offset(anchor, dx, dz));
                cells.add(offset(anchor, dx * 2, dz * 2));
            }
            case 2 -> {
                cells.add(offset(anchor, dx, dz));
                cells.add(offset(anchor, dx * 2, dz * 2));
                cells.add(offset(anchor, dx * 3, dz * 3));
            }
            case 3 -> {
                cells.add(offset(anchor, dx, dz));
                cells.add(offset(anchor, px, pz));
            }
            case 4 -> {
                cells.add(offset(anchor, dx, dz));
                cells.add(offset(anchor, px, pz));
                cells.add(offset(anchor, dx + px, dz + pz));
            }
            default -> {
                cells.add(offset(anchor, dx, dz));
                cells.add(offset(anchor, px, pz));
                cells.add(offset(anchor, dx + px, dz + pz));
            }
        }
        return List.copyOf(cells);
    }

    private static Cell offset(Cell cell, int dx, int dz) {
        return new Cell(cell.x + dx, cell.y, cell.z + dz);
    }

    private static List<Cell> neighbors(Cell cell) {
        return List.of(offset(cell, 1, 0), offset(cell, -1, 0), offset(cell, 0, 1), offset(cell, 0, -1));
    }

    private static long coordinateHash(long seed, Cell cell) {
        long value = seed ^ SALT;
        value ^= (long) cell.x * 0x9E3779B97F4A7C15L;
        value ^= (long) cell.y * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) cell.z * 0x165667B19E3779F9L;
        return mix64(value);
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private record Component(
            Set<Cell> cells, Map<Cell, Integer> distance, List<Cell> cores, boolean hasSourceWater) {
    }

    private record Candidate(Cell anchor, List<Cell> cells, long priority) {
        private static final Comparator<Candidate> ORDER = (left, right) -> {
            int priority = Long.compareUnsigned(left.priority, right.priority);
            if (priority != 0) {
                return priority;
            }
            int x = Integer.compare(left.anchor.x, right.anchor.x);
            if (x != 0) {
                return x;
            }
            int y = Integer.compare(left.anchor.y, right.anchor.y);
            return y != 0 ? y : Integer.compare(left.anchor.z, right.anchor.z);
        };
    }
}
