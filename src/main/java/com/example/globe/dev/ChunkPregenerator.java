package com.example.globe.dev;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayDeque;
import java.util.Queue;

public final class ChunkPregenerator {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int PROGRESS_INTERVAL = 200;

    private static boolean tickHookRegistered;
    private static GenerationJob activeJob;

    private ChunkPregenerator() {
    }

    public static void startTransect(MinecraftServer server,
                                     ServerCommandSource source,
                                     int zStart,
                                     int zEnd,
                                     int xHalfWidthChunks,
                                     int chunksPerTick) {
        ensureTickHookRegistered();

        if (activeJob != null) {
            source.sendError(Text.literal("[latdev] Pregenerator already running."));
            return;
        }

        ServerWorld world = source.getWorld();
        int zStartChunk = Math.floorDiv(zStart, BLOCKS_PER_CHUNK);
        int zEndChunk = Math.floorDiv(zEnd, BLOCKS_PER_CHUNK);
        int negStartChunk = Math.floorDiv(-zEnd, BLOCKS_PER_CHUNK);
        int negEndChunk = Math.floorDiv(-zStart, BLOCKS_PER_CHUNK);

        Queue<ChunkPos> queue = new ArrayDeque<>();
        for (int zChunk = negStartChunk; zChunk <= negEndChunk; zChunk++) {
            for (int xChunk = -xHalfWidthChunks; xChunk <= xHalfWidthChunks; xChunk++) {
                queue.add(new ChunkPos(xChunk, zChunk));
            }
        }

        for (int zChunk = zStartChunk; zChunk <= zEndChunk; zChunk++) {
            if (zChunk == 0 && (zStart > 0 || (negStartChunk <= 0 && negEndChunk >= 0))) {
                continue;
            }
            for (int xChunk = -xHalfWidthChunks; xChunk <= xHalfWidthChunks; xChunk++) {
                queue.add(new ChunkPos(xChunk, zChunk));
            }
        }

        int totalChunks = queue.size();
        activeJob = new GenerationJob(server, world, source, queue, chunksPerTick, totalChunks);

        int widthChunks = xHalfWidthChunks * 2 + 1;
        source.sendFeedback(() -> Text.literal("[latdev] Queued " + totalChunks
                + " chunks (zChunks=" + negStartChunk + ".." + negEndChunk
                + " and " + zStartChunk + ".." + zEndChunk
                + ", width=" + widthChunks + " chunks, "
                + chunksPerTick + " chunks/tick)."), false);
    }

    private static void ensureTickHookRegistered() {
        if (tickHookRegistered) {
            return;
        }

        ServerTickEvents.END_SERVER_TICK.register(ChunkPregenerator::onEndServerTick);
        tickHookRegistered = true;
    }

    private static void onEndServerTick(MinecraftServer server) {
        GenerationJob job = activeJob;
        if (job == null) {
            return;
        }

        if (job.server != server || job.queue.isEmpty()) {
            complete(job);
            return;
        }

        int budget = job.chunksPerTick;
        while (budget-- > 0 && !job.queue.isEmpty()) {
            ChunkPos next = job.queue.poll();
            if (next == null) {
                break;
            }

            job.world.getChunkManager().getChunk(next.x, next.z, ChunkStatus.FULL, true);
            job.processed++;

            if (job.processed % PROGRESS_INTERVAL == 0) {
                int remaining = job.totalChunks - job.processed;
                job.source.sendFeedback(() -> Text.literal("[latdev] Progress: "
                        + job.processed + "/" + job.totalChunks
                        + " chunks (remaining=" + remaining + ")"), false);
            }
        }

        if (job.queue.isEmpty()) {
            complete(job);
        }
    }

    private static void complete(GenerationJob job) {
        job.source.sendFeedback(() -> Text.literal("[latdev] Generation complete. Processed "
                + job.processed + " chunks."), false);
        activeJob = null;
    }

    private static final class GenerationJob {
        private final MinecraftServer server;
        private final ServerWorld world;
        private final ServerCommandSource source;
        private final Queue<ChunkPos> queue;
        private final int chunksPerTick;
        private final int totalChunks;
        private int processed;

        private GenerationJob(MinecraftServer server,
                              ServerWorld world,
                              ServerCommandSource source,
                              Queue<ChunkPos> queue,
                              int chunksPerTick,
                              int totalChunks) {
            this.server = server;
            this.world = world;
            this.source = source;
            this.queue = queue;
            this.chunksPerTick = chunksPerTick;
            this.totalChunks = totalChunks;
            this.processed = 0;
        }
    }
}
