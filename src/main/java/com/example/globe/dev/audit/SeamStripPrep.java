package com.example.globe.dev.audit;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Strip-shaped chunk-prep plan for seam audits.
 *
 * <p>The strip runs along the seam's tangent direction (E-W, X axis, since
 * Latitude seams are bands of constant Z) and is shallow across the seam (Z
 * axis) so the resulting bundle shows the transition from both sides without
 * over-generating circular neighborhoods of unrelated terrain.
 *
 * <p>This is shape + counting only. The actual generation pacing lives in the
 * audit job's tick state machine; this class never mutates world state.
 */
public final class SeamStripPrep {

    /**
     * A strip plan.
     *
     * @param min inclusive min chunk corner
     * @param max inclusive max chunk corner
     * @param totalChunks total chunks in the rectangle
     * @param alongSeamSpanBlocks the block span parallel to the seam
     * @param crossSeamHalfWidthBlocks the block half-width perpendicular to the seam
     */
    public record Plan(
            ChunkPos min,
            ChunkPos max,
            int totalChunks,
            int alongSeamSpanBlocks,
            int crossSeamHalfWidthBlocks) {
    }

    private SeamStripPrep() {
    }

    public static Plan buildPlan(int seamX, int seamZ, int alongSeamSpanBlocks, int crossSeamHalfWidthBlocks) {
        int halfAlong = Math.max(16, alongSeamSpanBlocks / 2);
        int halfCross = Math.max(16, crossSeamHalfWidthBlocks);
        int xMin = seamX - halfAlong;
        int xMax = seamX + halfAlong;
        int zMin = seamZ - halfCross;
        int zMax = seamZ + halfCross;
        int cxMin = Math.floorDiv(xMin, 16);
        int cxMax = Math.floorDiv(xMax, 16);
        int czMin = Math.floorDiv(zMin, 16);
        int czMax = Math.floorDiv(zMax, 16);
        int total = (cxMax - cxMin + 1) * (czMax - czMin + 1);
        return new Plan(
                new ChunkPos(cxMin, czMin),
                new ChunkPos(cxMax, czMax),
                total,
                halfAlong * 2,
                halfCross);
    }

    /**
     * Build a deterministic chunk queue: scans along-seam first, then steps
     * cross-seam. The deterministic ordering is important so identical runs
     * produce identical bundles.
     */
    public static Deque<ChunkPos> buildQueue(Plan plan) {
        Deque<ChunkPos> queue = new ArrayDeque<>(plan.totalChunks());
        for (int cz = plan.min().z; cz <= plan.max().z; cz++) {
            for (int cx = plan.min().x; cx <= plan.max().x; cx++) {
                queue.add(new ChunkPos(cx, cz));
            }
        }
        return queue;
    }

    /**
     * Counts strip chunks that have reached at least {@link ChunkStatus#FULL} without
     * forcing load. Used by the readiness gate.
     */
    public static int countLoadedFull(ServerLevel world, Plan plan) {
        int loaded = 0;
        for (int cz = plan.min().z; cz <= plan.max().z; cz++) {
            for (int cx = plan.min().x; cx <= plan.max().x; cx++) {
                if (world.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    /**
     * Counts strip chunks that have reached at least {@link ChunkStatus#BIOMES}.
     * Used to sanity-check probe validity (probes only need biome data).
     */
    public static int countLoadedBiomes(ServerLevel world, Plan plan) {
        int loaded = 0;
        for (int cz = plan.min().z; cz <= plan.max().z; cz++) {
            for (int cx = plan.min().x; cx <= plan.max().x; cx++) {
                if (world.getChunkSource().getChunk(cx, cz, ChunkStatus.BIOMES, false) != null) {
                    loaded++;
                }
            }
        }
        return loaded;
    }
}
