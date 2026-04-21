package com.example.globe.dev;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.ArrayDeque;
import java.util.Queue;

public final class ChunkPregenerator {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int PROGRESS_INTERVAL = 200;
    private static final int BOSSBAR_UPDATE_CHUNK_INTERVAL = 20;
    private static final long DEFAULT_MAX_NANOS_PER_TICK = 15_000_000L;
    private static final long AUTO_REDUCED_MAX_NANOS_PER_TICK = 5_000_000L;
    private static final long AUTO_BACKPRESSURE_THRESHOLD_NANOS = 45_000_000L;

    private static boolean tickHookRegistered;
    private static long fixedMaxNanosPerTick = DEFAULT_MAX_NANOS_PER_TICK;
    private static boolean autoBudget = true;

    private static boolean active;
    private static boolean paused;
    private static long nextJobId;
    private static long jobId;
    private static int totalChunksPlanned;
    private static int chunksCompleted;
    private static int activeChunksPerTick;
    private static long activeFixedMaxNanosPerTick;
    private static boolean activeAutoBudget;
    private static String jobSummary = "none";
    private static MinecraftServer activeServer;
    private static GenerationJob activeJob;

    private ChunkPregenerator() {
    }

    public static int startTransect(MinecraftServer server,
                                    CommandSourceStack source,
                                    int zStart,
                                    int zEnd,
                                    int xHalfWidthChunks,
                                    int chunksPerTick) {
        ensureTickHookRegistered();

        if (active) {
            source.sendFailure(Component.literal("[latdev] job active; run /latdev stop"));
            return 0;
        }

        ServerLevel world = source.getLevel();
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
        long maxNanosPerTick = fixedMaxNanosPerTick;
        boolean autoBudgetEnabled = autoBudget;
        String summary = "slice[zChunks=" + negStartChunk + ".." + negEndChunk
                + " and " + zStartChunk + ".." + zEndChunk
                + ", width=" + widthChunks + "]";

        long newJobId = ++nextJobId;
        ServerBossEvent bossBar = createBossBar(summary, world);
        activeJob = new GenerationJob(server, world, source, queue, chunksPerTick, maxNanosPerTick, autoBudgetEnabled, bossBar);
        activeServer = server;
        active = true;
        paused = false;
        jobId = newJobId;
        totalChunksPlanned = totalChunks;
        chunksCompleted = 0;
        activeChunksPerTick = chunksPerTick;
        activeFixedMaxNanosPerTick = maxNanosPerTick;
        activeAutoBudget = autoBudgetEnabled;
        jobSummary = summary;

        source.sendSuccess(() -> Component.literal("[latdev] started job#" + newJobId
                + " planned=" + totalChunks
                + " " + summary
                + " chunksPerTick=" + chunksPerTick
                + " budgetMs=" + nanosToMs(maxNanosPerTick)
                + " auto=" + autoBudgetEnabled), false);
        return 1;
    }

    public static int startSliceNS(CommandSourceStack source,
                                   int centerXChunk,
                                   int zStartBlocks,
                                   int zEndBlocks,
                                   int widthChunks,
                                   int chunksPerTick) {
        ensureTickHookRegistered();

        if (active) {
            source.sendFailure(Component.literal("[latdev] job active; run /latdev stop"));
            return 0;
        }

        ServerLevel world = source.getLevel();
        int zStartChunk = Math.floorDiv(zStartBlocks, BLOCKS_PER_CHUNK);
        int zEndChunk = Math.floorDiv(zEndBlocks, BLOCKS_PER_CHUNK);

        if (zStartChunk > zEndChunk) {
            int tmp = zStartChunk;
            zStartChunk = zEndChunk;
            zEndChunk = tmp;
        }

        int startXChunk = centerXChunk - (widthChunks / 2);
        int endXChunk = startXChunk + widthChunks - 1;

        Queue<ChunkPos> queue = new ArrayDeque<>();
        for (int zChunk = zStartChunk; zChunk <= zEndChunk; zChunk++) {
            for (int xChunk = startXChunk; xChunk <= endXChunk; xChunk++) {
                queue.add(new ChunkPos(xChunk, zChunk));
            }
        }

        int totalChunks = queue.size();
        long maxNanosPerTick = fixedMaxNanosPerTick;
        boolean autoBudgetEnabled = autoBudget;
        String summary = "sliceNS[zChunks=" + zStartChunk + ".." + zEndChunk
                + ", width=" + widthChunks
                + ", centerXChunk=" + centerXChunk + "]";

        long newJobId = ++nextJobId;
        ServerBossEvent bossBar = createBossBar(summary, world);
        activeJob = new GenerationJob(source.getServer(), world, source, queue, chunksPerTick, maxNanosPerTick, autoBudgetEnabled, bossBar);
        activeServer = source.getServer();
        active = true;
        paused = false;
        jobId = newJobId;
        totalChunksPlanned = totalChunks;
        chunksCompleted = 0;
        activeChunksPerTick = chunksPerTick;
        activeFixedMaxNanosPerTick = maxNanosPerTick;
        activeAutoBudget = autoBudgetEnabled;
        jobSummary = summary;

        source.sendSuccess(() -> Component.literal("[latdev] started job#" + newJobId
                + " planned=" + totalChunks
                + " " + summary
                + " chunksPerTick=" + chunksPerTick
                + " budgetMs=" + nanosToMs(maxNanosPerTick)
                + " auto=" + autoBudgetEnabled), false);
        return 1;
    }

    public static int setDefaultBudgetMs(CommandSourceStack source, int budgetMs) {
        long nanos = budgetMs * 1_000_000L;
        fixedMaxNanosPerTick = nanos;
        source.sendSuccess(() -> Component.literal("[latdev] budgetMs=" + budgetMs + " (auto=" + autoBudget + ")"), false);
        return 1;
    }

    public static int setAutoBudget(CommandSourceStack source, boolean enabled) {
        autoBudget = enabled;
        long fixedMs = fixedMaxNanosPerTick / 1_000_000L;
        source.sendSuccess(() -> Component.literal("[latdev] budgetAuto=" + (enabled ? "on" : "off")
                + " (budgetMs=" + fixedMs + ")"), false);
        return 1;
    }

    public static int pauseJob(CommandSourceStack source) {
        if (!active) {
            source.sendSuccess(() -> Component.literal("[latdev] no active job."), false);
            return 0;
        }

        if (paused) {
            source.sendSuccess(() -> Component.literal("[latdev] already paused " + progressText()), false);
            return 1;
        }

        paused = true;
        source.sendSuccess(() -> Component.literal("[latdev] paused " + progressText()), false);
        return 1;
    }

    public static int resumeJob(CommandSourceStack source) {
        if (!active) {
            source.sendSuccess(() -> Component.literal("[latdev] no active job."), false);
            return 0;
        }

        if (!paused) {
            source.sendSuccess(() -> Component.literal("[latdev] already running " + progressText()), false);
            return 1;
        }

        paused = false;
        source.sendSuccess(() -> Component.literal("[latdev] resumed " + progressText()), false);
        return 1;
    }

    public static int stopJob(CommandSourceStack source) {
        if (!active) {
            source.sendSuccess(() -> Component.literal("[latdev] no active job."), false);
            return 0;
        }

        long stoppedJobId = jobId;
        int completed = chunksCompleted;
        int planned = totalChunksPlanned;
        String summary = jobSummary;
        clearState();

        source.sendSuccess(() -> Component.literal("[latdev] stopped job#" + stoppedJobId
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
            job.source.sendSuccess(() -> Component.literal("[latdev] stopped job#" + stoppedJobId
                    + " progress=" + completed + "/" + planned
                    + " " + summary), false);
        }
    }

    public static int status(CommandSourceStack source) {
        if (!active) {
            source.sendSuccess(() -> Component.literal("[latdev] status: active=false paused=false"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("[latdev] status: active=true paused=" + paused
                + " jobId=" + jobId
                + " progress=" + chunksCompleted + "/" + totalChunksPlanned
                + " chunksPerTick=" + activeChunksPerTick
                + " budgetMs=" + nanosToMs(activeFixedMaxNanosPerTick)
                + " auto=" + activeAutoBudget
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

        long maxNanosThisTick = resolveMaxNanosThisTick(job);
        long tickStartNanos = System.nanoTime();
        int budget = job.chunksPerTick;
        while (budget-- > 0 && !job.queue.isEmpty()) {
            if ((System.nanoTime() - tickStartNanos) >= maxNanosThisTick) {
                break;
            }

            ChunkPos next = job.queue.poll();
            if (next == null) {
                break;
            }

            job.world.getChunkSource().getChunk(next.x(), next.z(), ChunkStatus.FULL, true);
            chunksCompleted++;

            if ((chunksCompleted - job.lastBossbarUpdateChunks) >= BOSSBAR_UPDATE_CHUNK_INTERVAL || job.queue.isEmpty()) {
                updateBossBar(job, false);
            }

            if (chunksCompleted % PROGRESS_INTERVAL == 0) {
                int remaining = totalChunksPlanned - chunksCompleted;
                job.source.sendSuccess(() -> Component.literal("[latdev] progress job#" + jobId + " "
                        + chunksCompleted + "/" + totalChunksPlanned
                        + " chunks (remaining=" + remaining + ")"), false);
            }
        }
        job.lastWorkDurationNanos = System.nanoTime() - tickStartNanos;

        if (job.queue.isEmpty()) {
            complete(job);
        }
    }

    private static void complete(GenerationJob job) {
        long completedJobId = jobId;
        int completed = chunksCompleted;
        int planned = totalChunksPlanned;
        String summary = jobSummary;

        updateBossBar(job, true);
        clearState();

        job.source.sendSuccess(() -> Component.literal("[latdev] complete job#" + completedJobId
                + " progress=" + completed + "/" + planned
                + " " + summary), false);
    }

    private static void clearState() {
        if (activeJob != null) {
            clearBossBar(activeJob);
            activeJob.queue.clear();
        }
        active = false;
        paused = false;
        jobId = 0L;
        totalChunksPlanned = 0;
        chunksCompleted = 0;
        activeChunksPerTick = 0;
        activeFixedMaxNanosPerTick = 0L;
        activeAutoBudget = false;
        jobSummary = "none";
        activeServer = null;
        activeJob = null;
    }

    private static String progressText() {
        return "job#" + jobId + " progress=" + chunksCompleted + "/" + totalChunksPlanned;
    }

    private static long resolveMaxNanosThisTick(GenerationJob job) {
        if (!job.autoBudget) {
            return job.fixedMaxNanosPerTick;
        }
        if (job.lastWorkDurationNanos > AUTO_BACKPRESSURE_THRESHOLD_NANOS) {
            return AUTO_REDUCED_MAX_NANOS_PER_TICK;
        }
        return job.fixedMaxNanosPerTick;
    }

    private static ServerBossEvent createBossBar(String summary, ServerLevel world) {
        ServerBossEvent bossBar = new ServerBossEvent(Component.literal("LATDEV " + summary), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(0.0F);
        for (ServerPlayer player : world.players()) {
            bossBar.addPlayer(player);
        }
        return bossBar;
    }

    private static void updateBossBar(GenerationJob job, boolean force) {
        if (job.bossBar == null) {
            return;
        }
        if (!force && (chunksCompleted - job.lastBossbarUpdateChunks) < BOSSBAR_UPDATE_CHUNK_INTERVAL && !job.queue.isEmpty()) {
            return;
        }
        float percent = totalChunksPlanned <= 0
                ? 1.0F
                : Math.min(1.0F, chunksCompleted / (float) totalChunksPlanned);
        job.bossBar.setProgress(percent);
        job.bossBar.setName(Component.literal("LATDEV " + jobSummary + " " + chunksCompleted + "/" + totalChunksPlanned));
        job.lastBossbarUpdateChunks = chunksCompleted;
    }

    private static void clearBossBar(GenerationJob job) {
        if (job.bossBar == null) {
            return;
        }
        job.bossBar.removeAllPlayers();
        job.bossBar = null;
    }

    private static String nanosToMs(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }

    private static final class GenerationJob {
        private final MinecraftServer server;
        private final ServerLevel world;
        private final CommandSourceStack source;
        private final Queue<ChunkPos> queue;
        private final int chunksPerTick;
        private final long fixedMaxNanosPerTick;
        private final boolean autoBudget;
        private long lastWorkDurationNanos;
        private ServerBossEvent bossBar;
        private int lastBossbarUpdateChunks;

        private GenerationJob(MinecraftServer server,
                              ServerLevel world,
                              CommandSourceStack source,
                              Queue<ChunkPos> queue,
                              int chunksPerTick,
                              long fixedMaxNanosPerTick,
                              boolean autoBudget,
                              ServerBossEvent bossBar) {
            this.server = server;
            this.world = world;
            this.source = source;
            this.queue = queue;
            this.chunksPerTick = chunksPerTick;
            this.fixedMaxNanosPerTick = fixedMaxNanosPerTick;
            this.autoBudget = autoBudget;
            this.lastWorkDurationNanos = 0L;
            this.bossBar = bossBar;
            this.lastBossbarUpdateChunks = 0;
        }
    }
}
