package com.example.globe.dev;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

    private static boolean active;
    private static boolean paused;
    private static long nextJobId;
    private static long jobId;
    private static int totalChunksPlanned;
    private static int chunksCompleted;
    private static int activeChunksPerTick;
    private static String jobSummary = "none";
    private static MinecraftServer activeServer;
    private static GenerationJob activeJob;

    private ChunkPregenerator() {
    }

    public static int startTransect(MinecraftServer server,
                                    ServerCommandSource source,
                                    int zStart,
                                    int zEnd,
                                    int xHalfWidthChunks,
                                    int chunksPerTick) {
        ensureTickHookRegistered();

        if (active) {
            source.sendError(Text.literal("[latdev] job active; run /latdev stop"));
            return 0;
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
        int widthChunks = xHalfWidthChunks * 2 + 1;
        String summary = "slice[zChunks=" + negStartChunk + ".." + negEndChunk
                + " and " + zStartChunk + ".." + zEndChunk
                + ", width=" + widthChunks + "]";

        long newJobId = ++nextJobId;
        activeJob = new GenerationJob(server, world, source, queue, chunksPerTick);
        activeServer = server;
        active = true;
        paused = false;
        jobId = newJobId;
        totalChunksPlanned = totalChunks;
        chunksCompleted = 0;
        activeChunksPerTick = chunksPerTick;
        jobSummary = summary;

        source.sendFeedback(() -> Text.literal("[latdev] started job#" + newJobId
                + " planned=" + totalChunks
                + " " + summary
                + " chunksPerTick=" + chunksPerTick), false);
        return 1;
    }

    public static int pauseJob(ServerCommandSource source) {
        if (!active) {
            source.sendFeedback(() -> Text.literal("[latdev] no active job."), false);
            return 0;
        }

        if (paused) {
            source.sendFeedback(() -> Text.literal("[latdev] already paused " + progressText()), false);
            return 1;
        }

        paused = true;
        source.sendFeedback(() -> Text.literal("[latdev] paused " + progressText()), false);
        return 1;
    }

    public static int resumeJob(ServerCommandSource source) {
        if (!active) {
            source.sendFeedback(() -> Text.literal("[latdev] no active job."), false);
            return 0;
        }

        if (!paused) {
            source.sendFeedback(() -> Text.literal("[latdev] already running " + progressText()), false);
            return 1;
        }

        paused = false;
        source.sendFeedback(() -> Text.literal("[latdev] resumed " + progressText()), false);
        return 1;
    }

    public static int stopJob(ServerCommandSource source) {
        if (!active) {
            source.sendFeedback(() -> Text.literal("[latdev] no active job."), false);
            return 0;
        }

        long stoppedJobId = jobId;
        int completed = chunksCompleted;
        int planned = totalChunksPlanned;
        String summary = jobSummary;
        clearState();

        source.sendFeedback(() -> Text.literal("[latdev] stopped job#" + stoppedJobId
                + " progress=" + completed + "/" + planned
                + " " + summary), false);
        return 1;
    }

    public static void stopJob(boolean quiet) {
        if (!active) {
            return;
        }

        GenerationJob job = activeJob;
        long stoppedJobId = jobId;
        int completed = chunksCompleted;
        int planned = totalChunksPlanned;
        String summary = jobSummary;
        clearState();

        if (!quiet && job != null) {
            job.source.sendFeedback(() -> Text.literal("[latdev] stopped job#" + stoppedJobId
                    + " progress=" + completed + "/" + planned
                    + " " + summary), false);
        }
    }

    public static int status(ServerCommandSource source) {
        if (!active) {
            source.sendFeedback(() -> Text.literal("[latdev] status: active=false paused=false"), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("[latdev] status: active=true paused=" + paused
                + " jobId=" + jobId
                + " progress=" + chunksCompleted + "/" + totalChunksPlanned
                + " chunksPerTick=" + activeChunksPerTick
                + " " + jobSummary), false);
        return 1;
    }

    private static void ensureTickHookRegistered() {
        if (tickHookRegistered) {
            return;
        }

        ServerTickEvents.END_SERVER_TICK.register(ChunkPregenerator::onEndServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active && activeServer == server) {
                stopJob(true);
            }
        });
        tickHookRegistered = true;
    }

    private static void onEndServerTick(MinecraftServer server) {
        if (!active) {
            return;
        }

        GenerationJob job = activeJob;
        if (job == null || activeServer != server || job.server != server || job.world.getServer() != server) {
            stopJob(true);
            return;
        }

        if (paused) {
            return;
        }

        if (job.queue.isEmpty()) {
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
            chunksCompleted++;

            if (chunksCompleted % PROGRESS_INTERVAL == 0) {
                int remaining = totalChunksPlanned - chunksCompleted;
                job.source.sendFeedback(() -> Text.literal("[latdev] progress job#" + jobId + " "
                        + chunksCompleted + "/" + totalChunksPlanned
                        + " chunks (remaining=" + remaining + ")"), false);
            }
        }

        if (job.queue.isEmpty()) {
            complete(job);
        }
    }

    private static void complete(GenerationJob job) {
        long completedJobId = jobId;
        int completed = chunksCompleted;
        int planned = totalChunksPlanned;
        String summary = jobSummary;

        clearState();

        job.source.sendFeedback(() -> Text.literal("[latdev] complete job#" + completedJobId
                + " progress=" + completed + "/" + planned
                + " " + summary), false);
    }

    private static void clearState() {
        if (activeJob != null) {
            activeJob.queue.clear();
        }
        active = false;
        paused = false;
        jobId = 0L;
        totalChunksPlanned = 0;
        chunksCompleted = 0;
        activeChunksPerTick = 0;
        jobSummary = "none";
        activeServer = null;
        activeJob = null;
    }

    private static String progressText() {
        return "job#" + jobId + " progress=" + chunksCompleted + "/" + totalChunksPlanned;
    }

    private static final class GenerationJob {
        private final MinecraftServer server;
        private final ServerWorld world;
        private final ServerCommandSource source;
        private final Queue<ChunkPos> queue;
        private final int chunksPerTick;

        private GenerationJob(MinecraftServer server,
                              ServerWorld world,
                              ServerCommandSource source,
                              Queue<ChunkPos> queue,
                              int chunksPerTick) {
            this.server = server;
            this.world = world;
            this.source = source;
            this.queue = queue;
            this.chunksPerTick = chunksPerTick;
        }
    }
}
