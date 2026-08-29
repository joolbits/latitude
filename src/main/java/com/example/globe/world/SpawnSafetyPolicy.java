package com.example.globe.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Dependency-free policy for initial-spawn coordinates and hazardous ground.
 */
public final class SpawnSafetyPolicy {
    public static final int FALLBACK_STEP_BLOCKS = 256;
    public static final int FALLBACK_MAX_RINGS = 1;
    public static final int SPAWN_PREPARATION_NEIGHBOR_RADIUS_CHUNKS = 1;
    public static final int COMPACT_PROBE_STEP_BLOCKS = 32;
    public static final int COMPACT_PROBE_X_HALF_SPAN_BLOCKS = 256;
    public static final int COMPACT_PROBE_Z_HALF_SPAN_BLOCKS = 64;
    public static final int MAX_COMPACT_PROBES = 85;
    public static final int MAX_VALIDATION_CANDIDATES = 9;

    public record FallbackCandidate(int x, int z) {
    }

    public record CompactCohort(
            List<FallbackCandidate> probes,
            List<FallbackCandidate> validationCandidates,
            int landCandidateCount,
            boolean terrainOnlyFallback) {
        public CompactCohort {
            probes = List.copyOf(probes);
            validationCandidates = List.copyOf(validationCandidates);
        }
    }

    private record WindowChoice(
            List<FallbackCandidate> candidates,
            int landCount,
            long centerDistanceSquared,
            int centerX,
            int centerZ) {
    }

    private SpawnSafetyPolicy() {
    }

    /**
     * Returns the largest absolute X that is both inside the terrain-search margin and outside
     * Latitude's east/west warning zone. Sampling inside this bound avoids moving a validated
     * spawn to a different, unvalidated terrain column afterward.
     */
    public static int safeSearchMaxAbsX(
            int borderHalf,
            int terrainMargin,
            int warningDistance,
            int warningPadding) {
        int terrainLimit = Math.max(0, borderHalf - Math.max(0, terrainMargin));
        int warningLimit = Math.max(
                0,
                borderHalf - Math.max(0, warningDistance) - Math.max(0, warningPadding));
        return Math.min(terrainLimit, warningLimit);
    }

    /**
     * Produces a finite, deterministic search around the requested latitude. Every coordinate is
     * already inside both the terrain margin and the east/west warning margin; production must
     * still validate the actual terrain column before accepting it.
     */
    public static List<FallbackCandidate> safeFallbackCandidates(
            int borderHalf,
            int targetZ,
            int terrainMargin,
            int warningDistance,
            int warningPadding,
            int stepBlocks,
            int maxRings) {
        int maxAbsX = safeSearchMaxAbsX(
                borderHalf,
                terrainMargin,
                warningDistance,
                warningPadding);
        int maxAbsZ = Math.max(0, borderHalf - Math.max(0, terrainMargin));
        int centerZ = Math.max(-maxAbsZ, Math.min(maxAbsZ, targetZ));
        int step = Math.max(1, stepBlocks);
        int rings = Math.max(0, maxRings);

        LinkedHashSet<FallbackCandidate> candidates = new LinkedHashSet<>();
        candidates.add(new FallbackCandidate(0, centerZ));
        for (int ring = 1; ring <= rings; ring++) {
            long rawOffset = (long) ring * step;
            int xOffset = (int) Math.min(maxAbsX, rawOffset);
            int northZ = (int) Math.min(maxAbsZ, (long) centerZ + rawOffset);
            int southZ = (int) Math.max(-maxAbsZ, (long) centerZ - rawOffset);

            candidates.add(new FallbackCandidate(xOffset, centerZ));
            candidates.add(new FallbackCandidate(-xOffset, centerZ));
            candidates.add(new FallbackCandidate(0, northZ));
            candidates.add(new FallbackCandidate(0, southZ));
            candidates.add(new FallbackCandidate(xOffset, northZ));
            candidates.add(new FallbackCandidate(-xOffset, northZ));
            candidates.add(new FallbackCandidate(xOffset, southZ));
            candidates.add(new FallbackCandidate(-xOffset, southZ));
        }
        return List.copyOf(candidates);
    }

    /**
     * Uses chunk-free biome probes to select one compact 3x3 terrain-validation cohort. The
     * complete deterministic probe lattice spans X +/-256 and Z +/-64 around the requested
     * latitude in 32-block steps. Coordinates are clamped and deduplicated inside the existing
     * terrain and warning bounds before the callback is invoked.
     */
    public static CompactCohort compactValidationCohort(
            int borderHalf,
            int targetZ,
            int terrainMargin,
            int warningDistance,
            int warningPadding,
            BiPredicate<Integer, Integer> isLandProbe) {
        if (isLandProbe == null) {
            return centralCompactValidationCohort(
                    borderHalf, targetZ, terrainMargin, warningDistance, warningPadding);
        }

        int maxAbsX = safeSearchMaxAbsX(
                borderHalf,
                terrainMargin,
                warningDistance,
                warningPadding);
        int maxAbsZ = Math.max(0, borderHalf - Math.max(0, terrainMargin));
        int centerZ = clamp(targetZ, -maxAbsZ, maxAbsZ);

        int xCount = COMPACT_PROBE_X_HALF_SPAN_BLOCKS * 2 / COMPACT_PROBE_STEP_BLOCKS + 1;
        int zCount = COMPACT_PROBE_Z_HALF_SPAN_BLOCKS * 2 / COMPACT_PROBE_STEP_BLOCKS + 1;
        FallbackCandidate[][] lattice = new FallbackCandidate[xCount][zCount];
        LinkedHashSet<FallbackCandidate> uniqueProbes = new LinkedHashSet<>();
        for (int xIndex = 0; xIndex < xCount; xIndex++) {
            int rawX = -COMPACT_PROBE_X_HALF_SPAN_BLOCKS
                    + xIndex * COMPACT_PROBE_STEP_BLOCKS;
            int x = clamp(rawX, -maxAbsX, maxAbsX);
            for (int zIndex = 0; zIndex < zCount; zIndex++) {
                int rawZ = centerZ - COMPACT_PROBE_Z_HALF_SPAN_BLOCKS
                        + zIndex * COMPACT_PROBE_STEP_BLOCKS;
                int z = clamp(rawZ, -maxAbsZ, maxAbsZ);
                FallbackCandidate probe = new FallbackCandidate(x, z);
                lattice[xIndex][zIndex] = probe;
                uniqueProbes.add(probe);
            }
        }

        Map<FallbackCandidate, Boolean> landByProbe = new LinkedHashMap<>();
        try {
            for (FallbackCandidate probe : uniqueProbes) {
                landByProbe.put(probe, isLandProbe.test(probe.x(), probe.z()));
            }
        } catch (RuntimeException classificationFailure) {
            return centralCompactValidationCohort(
                    borderHalf, targetZ, terrainMargin, warningDistance, warningPadding);
        }

        WindowChoice best = null;
        for (int xStart = 0; xStart <= xCount - 3; xStart++) {
            for (int zStart = 0; zStart <= zCount - 3; zStart++) {
                LinkedHashSet<FallbackCandidate> window = new LinkedHashSet<>();
                for (int xOffset = 0; xOffset < 3; xOffset++) {
                    for (int zOffset = 0; zOffset < 3; zOffset++) {
                        window.add(lattice[xStart + xOffset][zStart + zOffset]);
                    }
                }
                List<FallbackCandidate> windowCandidates = List.copyOf(window);
                int landCount = 0;
                for (FallbackCandidate candidate : windowCandidates) {
                    if (Boolean.TRUE.equals(landByProbe.get(candidate))) {
                        landCount++;
                    }
                }
                FallbackCandidate windowCenter = lattice[xStart + 1][zStart + 1];
                WindowChoice choice = new WindowChoice(
                        windowCandidates,
                        landCount,
                        distanceSquared(windowCenter, 0, centerZ),
                        windowCenter.x(),
                        windowCenter.z());
                if (isBetterWindow(choice, best)) {
                    best = choice;
                }
            }
        }

        if (best == null) {
            return centralCompactValidationCohort(
                    borderHalf, targetZ, terrainMargin, warningDistance, warningPadding);
        }

        List<FallbackCandidate> ordered = new ArrayList<>(best.candidates());
        ordered.sort(Comparator
                .comparing((FallbackCandidate candidate) ->
                        !Boolean.TRUE.equals(landByProbe.get(candidate)))
                .thenComparingLong(candidate -> distanceSquared(candidate, 0, centerZ))
                .thenComparingInt(FallbackCandidate::x)
                .thenComparingInt(FallbackCandidate::z));
        if (ordered.size() > MAX_VALIDATION_CANDIDATES) {
            ordered = new ArrayList<>(ordered.subList(0, MAX_VALIDATION_CANDIDATES));
        }
        int orderedLandCount = 0;
        for (FallbackCandidate candidate : ordered) {
            if (Boolean.TRUE.equals(landByProbe.get(candidate))) {
                orderedLandCount++;
            }
        }
        return new CompactCohort(
                List.copyOf(uniqueProbes),
                ordered,
                orderedLandCount,
                false);
    }

    /**
     * Central terrain-only cohort used when biome sampler setup or classification is unavailable.
     */
    public static CompactCohort centralCompactValidationCohort(
            int borderHalf,
            int targetZ,
            int terrainMargin,
            int warningDistance,
            int warningPadding) {
        int maxAbsX = safeSearchMaxAbsX(
                borderHalf,
                terrainMargin,
                warningDistance,
                warningPadding);
        int maxAbsZ = Math.max(0, borderHalf - Math.max(0, terrainMargin));
        int centerZ = clamp(targetZ, -maxAbsZ, maxAbsZ);
        LinkedHashSet<FallbackCandidate> candidates = new LinkedHashSet<>();
        for (int xOffset = -COMPACT_PROBE_STEP_BLOCKS;
                xOffset <= COMPACT_PROBE_STEP_BLOCKS;
                xOffset += COMPACT_PROBE_STEP_BLOCKS) {
            for (int zOffset = -COMPACT_PROBE_STEP_BLOCKS;
                    zOffset <= COMPACT_PROBE_STEP_BLOCKS;
                    zOffset += COMPACT_PROBE_STEP_BLOCKS) {
                candidates.add(new FallbackCandidate(
                        clamp(xOffset, -maxAbsX, maxAbsX),
                        clamp(centerZ + zOffset, -maxAbsZ, maxAbsZ)));
            }
        }
        List<FallbackCandidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingLong((FallbackCandidate candidate) ->
                        distanceSquared(candidate, 0, centerZ))
                .thenComparingInt(FallbackCandidate::x)
                .thenComparingInt(FallbackCandidate::z));
        return new CompactCohort(List.of(), ordered, 0, true);
    }

    /**
     * Applies at most nine expensive validators in deterministic cohort order and stops at the
     * first accepted result. A null validator result rejects that coordinate.
     */
    public static <T> Optional<T> firstValidatedCandidate(
            List<FallbackCandidate> candidates,
            Function<FallbackCandidate, T> validator) {
        if (candidates == null || validator == null) {
            return Optional.empty();
        }
        int limit = Math.min(MAX_VALIDATION_CANDIDATES, candidates.size());
        for (int index = 0; index < limit; index++) {
            T accepted = validator.apply(candidates.get(index));
            if (accepted != null) {
                return Optional.of(accepted);
            }
        }
        return Optional.empty();
    }

    private static boolean isBetterWindow(WindowChoice candidate, WindowChoice current) {
        if (current == null) {
            return true;
        }
        if (candidate.landCount() != current.landCount()) {
            return candidate.landCount() > current.landCount();
        }
        if (candidate.centerDistanceSquared() != current.centerDistanceSquared()) {
            return candidate.centerDistanceSquared() < current.centerDistanceSquared();
        }
        if (candidate.centerX() != current.centerX()) {
            return candidate.centerX() < current.centerX();
        }
        return candidate.centerZ() < current.centerZ();
    }

    private static long distanceSquared(FallbackCandidate candidate, int centerX, int centerZ) {
        long dx = (long) candidate.x() - centerX;
        long dz = (long) candidate.z() - centerZ;
        return dx * dx + dz * dz;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Worst-case synchronous FULL-chunk requests when each candidate loads only its own chunk for
     * validation and the accepted candidate then loads the surrounding neighbor ring once.
     */
    public static int maximumFallbackChunkLoadCalls(
            int candidateCount,
            int preparationNeighborRadiusChunks) {
        int candidates = Math.max(0, candidateCount);
        int radius = Math.max(0, preparationNeighborRadiusChunks);
        int side = radius * 2 + 1;
        int neighborChunks = Math.max(0, side * side - 1);
        return candidates + neighborChunks;
    }

    /**
     * True for blocks that are intrinsically unsafe directly beneath a newly spawned player.
     * Unknown provider blocks fail open; production separately requires a sturdy upper face.
     */
    public static boolean isDangerousSurfaceId(String blockId) {
        if (blockId == null) {
            return false;
        }
        return switch (blockId.toLowerCase(Locale.ROOT)) {
            case "minecraft:magma_block",
                    "minecraft:cactus",
                    "minecraft:powder_snow",
                    "minecraft:campfire",
                    "minecraft:soul_campfire",
                    "minecraft:pointed_dripstone",
                    "minecraft:fire",
                    "minecraft:soul_fire",
                    "minecraft:wither_rose",
                    "minecraft:sweet_berry_bush" -> true;
            default -> false;
        };
    }
}
