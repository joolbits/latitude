package com.example.globe.dev;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

public final class ChunkPregenerator {
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int PROGRESS_INTERVAL = 200;
    private static final int BOSSBAR_UPDATE_CHUNK_INTERVAL = 20;
    private static final long PREGEN_PREVIEW_TTL_MS = 30_000L;
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
    private static PregenPreview pendingPregenPreview;

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
        long maxNanosPerTick = fixedMaxNanosPerTick;
        boolean autoBudgetEnabled = autoBudget;
        String summary = "slice[zChunks=" + negStartChunk + ".." + negEndChunk
                + " and " + zStartChunk + ".." + zEndChunk
                + ", width=" + widthChunks + "]";

        long newJobId = ++nextJobId;
        ServerBossBar bossBar = createBossBar(summary, world, source, false);
        activeJob = new GenerationJob(server, world, source, queue, chunksPerTick, maxNanosPerTick, autoBudgetEnabled, bossBar, false);
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

        source.sendFeedback(() -> Text.literal("[latdev] started job#" + newJobId
                + " planned=" + totalChunks
                + " " + summary
                + " chunksPerTick=" + chunksPerTick
                + " budgetMs=" + nanosToMs(maxNanosPerTick)
                + " auto=" + autoBudgetEnabled), false);
        return 1;
    }

    public static int startSliceNS(ServerCommandSource source,
                                   int centerXChunk,
                                   int zStartBlocks,
                                   int zEndBlocks,
                                   int widthChunks,
                                   int chunksPerTick) {
        ensureTickHookRegistered();

        if (active) {
            source.sendError(Text.literal("[latdev] job active; run /latdev stop"));
            return 0;
        }

        ServerWorld world = source.getWorld();
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
        ServerBossBar bossBar = createBossBar(summary, world, source, false);
        activeJob = new GenerationJob(source.getServer(), world, source, queue, chunksPerTick, maxNanosPerTick, autoBudgetEnabled, bossBar, false);
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

        source.sendFeedback(() -> Text.literal("[latdev] started job#" + newJobId
                + " planned=" + totalChunks
                + " " + summary
                + " chunksPerTick=" + chunksPerTick
                + " budgetMs=" + nanosToMs(maxNanosPerTick)
                + " auto=" + autoBudgetEnabled), false);
        return 1;
    }

    public static boolean isPregenPaused() {
        return active && paused && activeJob != null && activeJob.pregen;
    }

    public static int previewPregen(ServerCommandSource source,
                                    int chunkMinX,
                                    int chunkMaxX,
                                    int chunkMinZ,
                                    int chunkMaxZ,
                                    int totalChunks,
                                    int radiusBlocks,
                                    PregenSpeedPreset preset) {
        ensureTickHookRegistered();

        if (active) {
            source.sendError(Text.literal("[latdev] job active; run /latdev pregen stop"));
            return 0;
        }

        if (preset == null) {
            source.sendError(Text.literal("[latdev] pregen preview failed: missing speed preset"));
            return 0;
        }

        if (chunkMinX > chunkMaxX || chunkMinZ > chunkMaxZ || totalChunks <= 0) {
            source.sendError(Text.literal("[latdev] pregen preview failed: invalid chunk bounds"));
            return 0;
        }

        pendingPregenPreview = new PregenPreview(
                source.getServer(),
                source.getWorld(),
                chunkMinX,
                chunkMaxX,
                chunkMinZ,
                chunkMaxZ,
                totalChunks,
                radiusBlocks,
                preset,
                System.currentTimeMillis());

        String previewText = "[latdev] pregen preview: R=" + radiusBlocks
                + " chunks x=" + chunkMinX + ".." + chunkMaxX
                + " z=" + chunkMinZ + ".." + chunkMaxZ
                + " total=" + formatCount(totalChunks)
                + " preset=" + preset.id
                + " (chunksPerTick=" + preset.chunksPerTick
                + ", budgetMs=" + preset.budgetMs + ")"
                + " confirm within " + (PREGEN_PREVIEW_TTL_MS / 1000) + "s using /latdev pregen confirm";
        source.sendFeedback(() -> Text.literal(previewText), false);
        return 1;
    }

    public static int confirmPregen(ServerCommandSource source) {
        ensureTickHookRegistered();

        if (active) {
            source.sendError(Text.literal("[latdev] job active; run /latdev pregen stop"));
            return 0;
        }

        PregenPreview preview = pendingPregenPreview;
        if (preview == null) {
            source.sendError(Text.literal("[latdev] no pregen preview queued. Run /latdev pregen first."));
            return 0;
        }

        if (preview.server != source.getServer()) {
            pendingPregenPreview = null;
            source.sendError(Text.literal("[latdev] pregen preview is from a different server; run /latdev pregen again."));
            return 0;
        }

        long ageMs = System.currentTimeMillis() - preview.createdAtMs;
        if (ageMs > PREGEN_PREVIEW_TTL_MS) {
            pendingPregenPreview = null;
            source.sendError(Text.literal("[latdev] pregen preview expired after " + (ageMs / 1000.0) + "s; run /latdev pregen again."));
            return 0;
        }

        pendingPregenPreview = null;
        return startPregenFromPreview(source, preview);
    }

    public static int pregenPause(ServerCommandSource source) {
        if (!isActivePregen()) {
            source.sendFeedback(() -> Text.literal("[latdev] no active pregen job."), false);
            return 0;
        }

        if (paused) {
            source.sendFeedback(() -> Text.literal("[latdev] Pregen already paused"), false);
            return 1;
        }

        paused = true;
        source.sendFeedback(() -> Text.literal("[latdev] Pregen paused"), false);
        return 1;
    }

    public static int pregenResume(ServerCommandSource source) {
        if (!isActivePregen()) {
            source.sendFeedback(() -> Text.literal("[latdev] no active pregen job."), false);
            return 0;
        }

        if (!paused) {
            source.sendFeedback(() -> Text.literal("[latdev] Pregen already running"), false);
            showBossBarToSource(activeJob, source);
            return 1;
        }

        paused = false;
        source.sendFeedback(() -> Text.literal("[latdev] Pregen resumed"), false);
        showBossBarToSource(activeJob, source);
        return 1;
    }

    public static int pregenStop(ServerCommandSource source) {
        if (!isActivePregen()) {
            source.sendFeedback(() -> Text.literal("[latdev] no active pregen job."), false);
            return 0;
        }

        int completed = chunksCompleted;
        int planned = totalChunksPlanned;
        clearState();
        source.sendFeedback(() -> Text.literal("[latdev] Pregen stopped at " + formatCount(completed) + "/" + formatCount(planned)), false);
        return 1;
    }

    public static int pregenStatus(ServerCommandSource source) {
        if (isActivePregen()) {
            showBossBarToSource(activeJob, source);
            updateBossBar(activeJob, true);
            source.sendFeedback(() -> Text.literal("[latdev] status: active=true paused=" + paused
                    + " progress=" + formatCount(chunksCompleted) + "/" + formatCount(totalChunksPlanned)
                    + " chunksPerTick=" + activeChunksPerTick
                    + " budgetMs=" + nanosToMs(activeFixedMaxNanosPerTick)
                    + " preset=" + activeJob.presetName), false);
            return 1;
        }

        if (pendingPregenPreview != null) {
            long ageMs = System.currentTimeMillis() - pendingPregenPreview.createdAtMs;
            if (ageMs <= PREGEN_PREVIEW_TTL_MS) {
                long remainingMs = PREGEN_PREVIEW_TTL_MS - ageMs;
                PregenPreview preview = pendingPregenPreview;
                source.sendFeedback(() -> Text.literal("[latdev] status: active=false preview=true "
                        + "total=" + formatCount(preview.totalChunks)
                        + " preset=" + preview.preset.id
                        + " expiresIn=" + String.format(Locale.ROOT, "%.1fs", remainingMs / 1000.0)), false);
                return 1;
            }
            pendingPregenPreview = null;
        }

        source.sendFeedback(() -> Text.literal("[latdev] status: active=false preview=false"), false);
        return 1;
    }

    public static int setDefaultBudgetMs(ServerCommandSource source, int budgetMs) {
        long nanos = budgetMs * 1_000_000L;
        fixedMaxNanosPerTick = nanos;
        source.sendFeedback(() -> Text.literal("[latdev] budgetMs=" + budgetMs + " (auto=" + autoBudget + ")"), false);
        return 1;
    }

    public static int setAutoBudget(ServerCommandSource source, boolean enabled) {
        autoBudget = enabled;
        long fixedMs = fixedMaxNanosPerTick / 1_000_000L;
        source.sendFeedback(() -> Text.literal("[latdev] budgetAuto=" + (enabled ? "on" : "off")
                + " (budgetMs=" + fixedMs + ")"), false);
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

        showBossBarToSource(activeJob, source);
        updateBossBar(activeJob, true);
        source.sendFeedback(() -> Text.literal("[latdev] status: active=true paused=" + paused
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
            if (pendingPregenPreview != null && pendingPregenPreview.server == server) {
                pendingPregenPreview = null;
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

            job.world.getChunkManager().getChunk(next.x, next.z, ChunkStatus.FULL, true);
            chunksCompleted++;

            if ((chunksCompleted - job.lastBossbarUpdateChunks) >= BOSSBAR_UPDATE_CHUNK_INTERVAL || job.queue.isEmpty()) {
                updateBossBar(job, false);
            }

            if (chunksCompleted % PROGRESS_INTERVAL == 0) {
                int remaining = totalChunksPlanned - chunksCompleted;
                job.source.sendFeedback(() -> Text.literal("[latdev] progress job#" + jobId + " "
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

        if (job.pregen) {
            job.source.sendFeedback(() -> Text.literal("[latdev] Pregen complete: " + formatCount(completed) + " chunks"), false);
            return;
        }

        job.source.sendFeedback(() -> Text.literal("[latdev] complete job#" + completedJobId
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

    private static ServerBossBar createBossBar(String summary, ServerWorld world, ServerCommandSource source, boolean senderOnly) {
        ServerBossBar bossBar = new ServerBossBar(Text.literal("LATDEV " + summary), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
        bossBar.setPercent(0.0F);
        if (senderOnly) {
            if (source.getEntity() instanceof ServerPlayerEntity player) {
                bossBar.addPlayer(player);
            }
        } else {
            for (ServerPlayerEntity player : world.getPlayers()) {
                bossBar.addPlayer(player);
            }
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
        job.bossBar.setPercent(percent);
        if (job.pregen) {
            double percentValue = totalChunksPlanned <= 0 ? 100.0 : (chunksCompleted * 100.0) / totalChunksPlanned;
            job.bossBar.setName(Text.literal("Pregen: "
                    + formatCount(chunksCompleted) + " / " + formatCount(totalChunksPlanned)
                    + " (" + String.format(Locale.ROOT, "%.1f%%", percentValue) + ")"));
        } else {
            job.bossBar.setName(Text.literal("LATDEV " + jobSummary + " " + chunksCompleted + "/" + totalChunksPlanned));
        }
        job.lastBossbarUpdateChunks = chunksCompleted;
    }

    private static void showBossBarToSource(GenerationJob job, ServerCommandSource source) {
        if (job == null || job.bossBar == null) {
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!job.bossBar.getPlayers().contains(player)) {
            job.bossBar.addPlayer(player);
        }
    }

    private static void clearBossBar(GenerationJob job) {
        if (job.bossBar == null) {
            return;
        }
        job.bossBar.clearPlayers();
        job.bossBar = null;
    }

    private static int startPregenFromPreview(ServerCommandSource source, PregenPreview preview) {
        ensureTickHookRegistered();

        if (active) {
            source.sendError(Text.literal("[latdev] job active; run /latdev pregen stop"));
            return 0;
        }

        Queue<ChunkPos> queue = new ArrayDeque<>(preview.totalChunks);
        for (int z = preview.chunkMinZ; z <= preview.chunkMaxZ; z++) {
            for (int x = preview.chunkMinX; x <= preview.chunkMaxX; x++) {
                queue.add(new ChunkPos(x, z));
            }
        }

        String summary = "pregen[x=" + preview.chunkMinX + ".." + preview.chunkMaxX
                + ", z=" + preview.chunkMinZ + ".." + preview.chunkMaxZ
                + ", preset=" + preview.preset.id + "]";
        long newJobId = ++nextJobId;
        ServerBossBar bossBar = createBossBar(summary, preview.world, source, true);
        activeJob = new GenerationJob(
                source.getServer(),
                preview.world,
                source,
                queue,
                preview.preset.chunksPerTick,
                preview.preset.budgetMs * 1_000_000L,
                false,
                bossBar,
                true,
                preview.preset.id);
        activeServer = source.getServer();
        active = true;
        paused = false;
        jobId = newJobId;
        totalChunksPlanned = preview.totalChunks;
        chunksCompleted = 0;
        activeChunksPerTick = preview.preset.chunksPerTick;
        activeFixedMaxNanosPerTick = preview.preset.budgetMs * 1_000_000L;
        activeAutoBudget = false;
        jobSummary = summary;

        updateBossBar(activeJob, true);
        showBossBarToSource(activeJob, source);
        source.sendFeedback(() -> Text.literal("[latdev] Pregen queued: "
                + formatCount(preview.totalChunks)
                + " chunks (preset=" + preview.preset.id
                + ", chunksPerTick=" + preview.preset.chunksPerTick
                + ", budgetMs=" + preview.preset.budgetMs + ")"), false);
        return 1;
    }

    private static boolean isActivePregen() {
        return active && activeJob != null && activeJob.pregen;
    }

    private static String formatCount(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String nanosToMs(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }

    public enum PregenSpeedPreset {
        SLOW("slow", 24, 4),
        NORMAL("normal", 72, 8),
        FAST("fast", 160, 12),
        INSANE("insane", 320, 20);

        private final String id;
        private final int chunksPerTick;
        private final int budgetMs;

        PregenSpeedPreset(String id, int chunksPerTick, int budgetMs) {
            this.id = id;
            this.chunksPerTick = chunksPerTick;
            this.budgetMs = budgetMs;
        }
    }

    private static final class PregenPreview {
        private final MinecraftServer server;
        private final ServerWorld world;
        private final int chunkMinX;
        private final int chunkMaxX;
        private final int chunkMinZ;
        private final int chunkMaxZ;
        private final int totalChunks;
        private final int radiusBlocks;
        private final PregenSpeedPreset preset;
        private final long createdAtMs;

        private PregenPreview(MinecraftServer server,
                              ServerWorld world,
                              int chunkMinX,
                              int chunkMaxX,
                              int chunkMinZ,
                              int chunkMaxZ,
                              int totalChunks,
                              int radiusBlocks,
                              PregenSpeedPreset preset,
                              long createdAtMs) {
            this.server = server;
            this.world = world;
            this.chunkMinX = chunkMinX;
            this.chunkMaxX = chunkMaxX;
            this.chunkMinZ = chunkMinZ;
            this.chunkMaxZ = chunkMaxZ;
            this.totalChunks = totalChunks;
            this.radiusBlocks = radiusBlocks;
            this.preset = preset;
            this.createdAtMs = createdAtMs;
        }
    }

    private static final class GenerationJob {
        private final MinecraftServer server;
        private final ServerWorld world;
        private final ServerCommandSource source;
        private final Queue<ChunkPos> queue;
        private final int chunksPerTick;
        private final long fixedMaxNanosPerTick;
        private final boolean autoBudget;
        private final boolean pregen;
        private final String presetName;
        private long lastWorkDurationNanos;
        private ServerBossBar bossBar;
        private int lastBossbarUpdateChunks;

        private GenerationJob(MinecraftServer server,
                              ServerWorld world,
                              ServerCommandSource source,
                              Queue<ChunkPos> queue,
                              int chunksPerTick,
                              long fixedMaxNanosPerTick,
                              boolean autoBudget,
                              ServerBossBar bossBar,
                              boolean pregen) {
            this(server, world, source, queue, chunksPerTick, fixedMaxNanosPerTick, autoBudget, bossBar, pregen, pregen ? "custom" : "n/a");
        }

        private GenerationJob(MinecraftServer server,
                              ServerWorld world,
                              ServerCommandSource source,
                              Queue<ChunkPos> queue,
                              int chunksPerTick,
                              long fixedMaxNanosPerTick,
                              boolean autoBudget,
                              ServerBossBar bossBar,
                              boolean pregen,
                              String presetName) {
            this.server = server;
            this.world = world;
            this.source = source;
            this.queue = queue;
            this.chunksPerTick = chunksPerTick;
            this.fixedMaxNanosPerTick = fixedMaxNanosPerTick;
            this.autoBudget = autoBudget;
            this.pregen = pregen;
            this.presetName = presetName;
            this.lastWorkDurationNanos = 0L;
            this.bossBar = bossBar;
            this.lastBossbarUpdateChunks = 0;
        }
    }
}
