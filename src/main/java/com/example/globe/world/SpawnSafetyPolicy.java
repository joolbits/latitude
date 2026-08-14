package com.example.globe.world;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Dependency-free policy for initial-spawn coordinates and hazardous ground.
 */
public final class SpawnSafetyPolicy {
    /**
     * World creation runs on the integrated server's critical loading path. Candidate selection
     * must not synchronously generate a remote FULL chunk merely to inspect it; the bounded
     * terrain-safe fallback generates only the destination columns it may actually accept.
     */
    public static final int INITIAL_SPAWN_TERRAIN_VALIDATION_BUDGET = 0;
    public static final int FALLBACK_STEP_BLOCKS = 256;
    public static final int FALLBACK_MAX_RINGS = 1;
    public static final int SPAWN_PREPARATION_NEIGHBOR_RADIUS_CHUNKS = 1;

    public record FallbackCandidate(int x, int z) {
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
     * Initial creation makes no speculative candidate-chunk request before the deterministic
     * terrain-safe fallback. Vanilla prepares the actual initial-spawn area after this hook returns.
     */
    public static int maximumInitialSpawnChunkLoadCalls(int terrainValidationBudget) {
        return Math.max(0, terrainValidationBudget);
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
