package com.example.globe.dev.audit;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.PrimaryLevelData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Autonomous seam-audit pipeline driven by {@link SeamAuditMode} JVM flags.
 *
 * <p>Server-tick state machine. Does NOT use chat commands. Sequences:
 * TELEPORT → SETTLE → PREP (strip-shaped force-load, paced) → READINESS gate
 * → PROBE (r=512, r=1024) → HERE capture → SCREENSHOT request → SCREENSHOT_WAIT
 * → SUMMARY (with explicit validity fields) → DONE (completion callback).
 *
 * <p>The complementary {@code /latdev seamAudit} chat command lives in
 * {@link com.example.globe.dev.SeamAuditCoordinator}; this class is the
 * startup-owned variant with strip-shaped prep and a real readiness gate.
 *
 * <p>No world-size constants appear in this class. Radius comes from
 * {@link LatitudeBiomes#getActiveRadiusBlocks()} or the world border half-size.
 * Band edges come from {@link LatitudeBands}; seam Z is computed via
 * {@link LatitudeMath#zForLatitudeDeg}.
 */
public final class AutonomousSeamAuditJob {

    public interface ScreenshotHandler {
        void requestScreenshot(Path pngPath);
    }

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int POST_SCREENSHOT_WAIT_TICKS = 30;

    private static volatile ScreenshotHandler screenshotHandler;
    private static boolean tickHookRegistered;
    private static volatile Job activeJob;

    private AutonomousSeamAuditJob() {
    }

    public static synchronized void setScreenshotHandler(ScreenshotHandler handler) {
        screenshotHandler = handler;
    }

    /**
     * Begin an autonomous seam audit. Must be called on the server thread
     * (e.g. via {@code server.execute(...)}). Triggers completion callback
     * when the bundle is written (success or recorded-as-invalid).
     */
    public static void start(MinecraftServer server,
                             String bandPairArg,
                             String edgeArg,
                             int samples,
                             int alongSeamSpanBlocks,
                             int crossSeamHalfWidthBlocks,
                             double loadedRatioThreshold,
                             int settleTicks,
                             int chunksPerTick,
                             int readinessTimeoutTicks,
                             Runnable onComplete) {
        try {
            ensureTickHook();

            if (activeJob != null) {
                GlobeMod.LOGGER.warn("[auditMode] job already active; ignoring start");
                if (onComplete != null) onComplete.run();
                return;
            }

            BandPair pair = BandPair.parse(bandPairArg);
            if (pair == null) {
                GlobeMod.LOGGER.error("[auditMode] invalid bandPair '{}' (expected adjacent canonical pair like 'temperate-subpolar')", bandPairArg);
                if (onComplete != null) onComplete.run();
                return;
            }

            Edge edge = Edge.fromArg(edgeArg);
            double seamDeg = pair.higher.lowDeg();
            double targetDeg = edge.applyTo(seamDeg);

            ServerPlayer player = null;
            if (!server.getPlayerList().getPlayers().isEmpty()) {
                player = server.getPlayerList().getPlayers().get(0);
            }
            if (player == null) {
                GlobeMod.LOGGER.warn("[auditMode] no player on integrated server; aborting");
                if (onComplete != null) onComplete.run();
                return;
            }

            ServerLevel world = player.level();
            int radiusBlocks = resolveRadius(world);
            if (radiusBlocks <= 0) {
                GlobeMod.LOGGER.warn("[auditMode] invalid world radius {}; aborting", radiusBlocks);
                if (onComplete != null) onComplete.run();
                return;
            }

            int absZ = LatitudeMath.zForLatitudeDeg(targetDeg, radiusBlocks);
            int sign = player.getZ() < 0.0 ? -1 : 1;
            int targetZ = sign * absZ;
            int targetX = Mth.floor(player.getX());

            String timestamp = TIMESTAMP_FMT.format(LocalDateTime.now());
            String dirName = timestamp + "_auto_" + pair.lower.id() + "-" + pair.higher.id() + "_" + edge.argName();
            Path auditDir = server.getServerDirectory()
                    .toAbsolutePath().normalize()
                    .resolve("latdev").resolve("seam-audits").resolve(dirName);
            Files.createDirectories(auditDir);

            SeamStripPrep.Plan plan = SeamStripPrep.buildPlan(
                    targetX, targetZ,
                    Math.max(32, alongSeamSpanBlocks),
                    Math.max(32, crossSeamHalfWidthBlocks));
            Deque<ChunkPos> queue = SeamStripPrep.buildQueue(plan);

            Job job = new Job(
                    world, player, auditDir,
                    pair, edge, seamDeg, targetDeg, radiusBlocks,
                    targetX, targetZ,
                    Mth.clamp(samples, 50, 4000),
                    plan, queue,
                    Mth.clamp(loadedRatioThreshold, 0.1, 1.0),
                    Math.max(0, settleTicks),
                    Mth.clamp(chunksPerTick, 1, 64),
                    Math.max(60, readinessTimeoutTicks),
                    world.getSeed(),
                    onComplete);

            teleportAndFixEnvironment(job);
            writeMetadata(job);

            activeJob = job;

            GlobeMod.LOGGER.info("[auditMode] started pair={} edge={} seamDeg={} targetDeg={} target=({},{}) R={} samples={} strip=along{}xcross{}blocks ({} chunks) out=latdev/seam-audits/{}",
                    pair.lower.id() + "-" + pair.higher.id(),
                    edge.argName(),
                    String.format(Locale.ROOT, "%.2f", seamDeg),
                    String.format(Locale.ROOT, "%.2f", targetDeg),
                    job.targetX, job.targetZ,
                    radiusBlocks,
                    job.samples,
                    plan.alongSeamSpanBlocks(),
                    plan.crossSeamHalfWidthBlocks() * 2,
                    plan.totalChunks(),
                    auditDir.getFileName());
        } catch (Exception e) {
            GlobeMod.LOGGER.error("[auditMode] start failed", e);
            activeJob = null;
            if (onComplete != null) onComplete.run();
        }
    }

    private static int resolveRadius(ServerLevel world) {
        int active = LatitudeBiomes.getActiveRadiusBlocks();
        if (active > 0) return active;
        WorldBorder b = world.getWorldBorder();
        return Math.max(1, (int) Math.floor(b.getSize() * 0.5) - 16);
    }

    private static void ensureTickHook() {
        if (tickHookRegistered) return;
        ServerTickEvents.END_SERVER_TICK.register(AutonomousSeamAuditJob::onEndServerTick);
        tickHookRegistered = true;
    }

    private static void onEndServerTick(MinecraftServer server) {
        Job job = activeJob;
        if (job == null) return;
        try {
            job.tick();
            if (job.stage == Stage.DONE) {
                activeJob = null;
                Runnable cb = job.onComplete;
                if (cb != null) cb.run();
            }
        } catch (Exception e) {
            GlobeMod.LOGGER.error("[auditMode] tick failed", e);
            try {
                job.writeFailureMarker(e);
            } catch (Exception ignored) {
            }
            activeJob = null;
            Runnable cb = job.onComplete;
            if (cb != null) cb.run();
        }
    }

    // ---------------- Stage operations ----------------

    private static void teleportAndFixEnvironment(Job job) {
        ServerLevel world = job.world;
        int cx = Math.floorDiv(job.targetX, 16);
        int cz = Math.floorDiv(job.targetZ, 16);
        world.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);

        int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, job.targetX, job.targetZ);
        int maxY = world.getMinY() + world.getHeight() - 1;
        int tpY = Mth.clamp(topY + 1, world.getMinY() + 1, maxY);
        job.targetY = tpY;

        // Face the equator for a deterministic screenshot frame.
        float yaw = (job.targetZ < 0) ? 180.0f : 0.0f;
        float pitch = 10.0f; // slight look-down so the ground transition is visible

        job.player.teleportTo(
                world,
                job.targetX + 0.5,
                tpY,
                job.targetZ + 0.5,
                EnumSet.noneOf(Relative.class),
                yaw,
                pitch,
                true);

        // Clamp time to noon and clear all weather for deterministic framing.
        ((PrimaryLevelData) world.getServer().getWorldData()).setGameTime(6000L);
        var weather = world.getWeatherData();
        weather.setClearWeatherTime(1_000_000);
        weather.setRainTime(0);
        weather.setThunderTime(0);
        weather.setRaining(false);
        weather.setThundering(false);
    }

    private static void writeMetadata(Job job) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schema\": \"autonomous-seam-audit/v1\",\n");
        json.append("  \"timestamp\": \"").append(LocalDateTime.now()).append("\",\n");
        json.append("  \"bandPair\": \"").append(job.pair.lower.id()).append("-").append(job.pair.higher.id()).append("\",\n");
        json.append("  \"edge\": \"").append(job.edge.argName()).append("\",\n");
        json.append("  \"seamDeg\": ").append(fmt(job.seamDeg)).append(",\n");
        json.append("  \"targetDeg\": ").append(fmt(job.targetDeg)).append(",\n");
        json.append("  \"radiusBlocks\": ").append(job.radiusBlocks).append(",\n");
        json.append("  \"targetX\": ").append(job.targetX).append(",\n");
        json.append("  \"targetY\": ").append(job.targetY).append(",\n");
        json.append("  \"targetZ\": ").append(job.targetZ).append(",\n");
        json.append("  \"worldSeed\": ").append(job.worldSeed).append(",\n");
        json.append("  \"samples\": ").append(job.samples).append(",\n");
        json.append("  \"strip\": {\n");
        json.append("    \"alongSeamSpanBlocks\": ").append(job.plan.alongSeamSpanBlocks()).append(",\n");
        json.append("    \"crossSeamHalfWidthBlocks\": ").append(job.plan.crossSeamHalfWidthBlocks()).append(",\n");
        json.append("    \"chunkMinX\": ").append(job.plan.min().x()).append(",\n");
        json.append("    \"chunkMinZ\": ").append(job.plan.min().z()).append(",\n");
        json.append("    \"chunkMaxX\": ").append(job.plan.max().x()).append(",\n");
        json.append("    \"chunkMaxZ\": ").append(job.plan.max().z()).append(",\n");
        json.append("    \"totalChunks\": ").append(job.plan.totalChunks()).append("\n");
        json.append("  },\n");
        json.append("  \"loadedRatioThreshold\": ").append(fmt(job.loadedRatioThreshold)).append(",\n");
        json.append("  \"readinessTimeoutTicks\": ").append(job.readinessTimeoutTicks).append(",\n");
        json.append("  \"chunksPerTick\": ").append(job.chunksPerTick).append(",\n");
        json.append("  \"settleTicks\": ").append(job.settleTicks).append("\n");
        json.append("}\n");
        Files.writeString(job.auditDir.resolve("metadata.json"), json.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Strip-constrained rectangular probe. Samples uniformly within [xMin,xMax]×[zMin,zMax].
     * All bounds must lie within the prepared strip so loaded counts are meaningful.
     */
    private static ProbeResult runRectProbe(Job job, int xMin, int xMax, int zMin, int zMax, int samples) {
        ServerLevel world = job.world;
        int sampleY = job.targetY > 0
                ? job.targetY
                : Mth.clamp(96, world.getMinY() + 1, world.getMinY() + world.getHeight() - 1);

        long seed = world.getSeed() ^ mix64(((long) xMin << 32) ^ (xMax & 0xffffffffL) ^ (long) zMin ^ ((long) zMax << 16));
        Random rng = new Random(seed);

        Map<String, Integer> biomeCounts = new HashMap<>();
        Map<String, Integer> bandCounts = new HashMap<>();
        int unloaded = 0;

        int xSpan = Math.max(1, xMax - xMin);
        int zSpan = Math.max(1, zMax - zMin);

        for (int i = 0; i < samples; i++) {
            int sx = xMin + (int) Math.floor(rng.nextDouble() * xSpan);
            int sz = zMin + (int) Math.floor(rng.nextDouble() * zSpan);

            int cx = Math.floorDiv(sx, 16);
            int cz = Math.floorDiv(sz, 16);
            if (world.getChunkSource().getChunk(cx, cz, ChunkStatus.BIOMES, false) == null) {
                unloaded++;
                continue;
            }
            BlockPos pos = new BlockPos(sx, sampleY, sz);
            String bid = biomeId(world.getBiome(pos));
            biomeCounts.merge(bid, 1, Integer::sum);

            double absLatDeg = Math.abs(sz) * 90.0 / (double) Math.max(1, job.radiusBlocks);
            String bandId = LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg).id();
            bandCounts.merge(bandId, 1, Integer::sum);
        }

        int loaded = samples - unloaded;
        String geometry = "rect:" + xMin + "," + xMax + "," + zMin + "," + zMax;
        return new ProbeResult(geometry, samples, loaded, unloaded, seed, biomeCounts, bandCounts);
    }

    private static void writeProbeFile(Job job, Path file, ProbeResult r) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# autonomous seam-audit probe\n");
        sb.append("pair=").append(job.pair.lower.id()).append("-").append(job.pair.higher.id()).append("\n");
        sb.append("edge=").append(job.edge.argName()).append("\n");
        sb.append("targetDeg=").append(fmt(job.targetDeg)).append("\n");
        sb.append("centerX=").append(job.targetX).append(" centerY=").append(job.targetY).append(" centerZ=").append(job.targetZ).append("\n");
        sb.append("geometry=").append(r.geometry).append("\n");
        sb.append("samples=").append(r.samples)
                .append(" loaded=").append(r.loaded)
                .append(" unloaded=").append(r.unloaded)
                .append(" seed=").append(r.seed)
                .append("\n");
        sb.append("\n[biomes]\n");
        appendSortedCounts(sb, r.biomeCounts, r.loaded);
        sb.append("\n[bands]\n");
        appendSortedCounts(sb, r.bandCounts, r.loaded);
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void appendSortedCounts(StringBuilder sb, Map<String, Integer> counts, int total) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        for (Map.Entry<String, Integer> e : list) {
            double pct = total > 0 ? (e.getValue() * 100.0) / total : 0.0;
            sb.append(String.format(Locale.ROOT, "%-60s %6d  %5.1f%%%n", e.getKey(), e.getValue(), pct));
        }
    }

    private static void writeHere(Job job) throws IOException {
        ServerLevel world = job.world;
        BlockPos pos = new BlockPos(job.targetX, job.targetY, job.targetZ);
        String bid = biomeId(world.getBiome(pos));
        double absLatDeg = Math.abs(job.targetZ) * 90.0 / (double) Math.max(1, job.radiusBlocks);
        String bandId = LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg).id();

        String text = String.format(Locale.ROOT,
                "# autonomous seam-audit here%n"
                        + "x=%d y=%d z=%d%n"
                        + "deg=%.4f band=%s biome=%s%n"
                        + "radiusBlocks=%d%n",
                job.targetX, job.targetY, job.targetZ,
                absLatDeg, bandId, bid, job.radiusBlocks);
        Files.writeString(job.auditDir.resolve("here.txt"), text, StandardCharsets.UTF_8);

        job.hereBiomeId = bid;
        job.hereBandId = bandId;
        job.hereDeg = absLatDeg;
    }

    private static void requestScreenshot(Job job) {
        ScreenshotHandler handler = screenshotHandler;
        if (handler == null) {
            try {
                Files.writeString(
                        job.auditDir.resolve("screenshot.pending.txt"),
                        "No screenshot handler registered (client bridge not installed).\n",
                        StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
            job.screenshotRequested = false;
            return;
        }
        Path pngPath = job.auditDir.resolve("screenshot_front.png");
        try {
            handler.requestScreenshot(pngPath);
            job.screenshotRequested = true;
            job.screenshotPath = pngPath;
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[auditMode] screenshot request failed", e);
            job.screenshotRequested = false;
        }
    }

    private static void writeSummary(Job job) throws IOException {
        int strip = Math.max(1, job.plan.totalChunks());
        double finalLoadedRatio = (double) job.finalLoadedFull / (double) strip;
        boolean screenshotExists = job.screenshotPath != null && Files.exists(job.screenshotPath);

        String analysisStatus;
        if (!job.prepCompleted) {
            analysisStatus = "invalid_readiness";
        } else if (!screenshotExists) {
            analysisStatus = "pending_screenshot";
        } else {
            analysisStatus = "ready_for_analysis";
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"bandPair\": \"").append(job.pair.lower.id()).append("-").append(job.pair.higher.id()).append("\",\n");
        json.append("  \"edge\": \"").append(job.edge.argName()).append("\",\n");
        json.append("  \"seamDeg\": ").append(fmt(job.seamDeg)).append(",\n");
        json.append("  \"targetDeg\": ").append(fmt(job.targetDeg)).append(",\n");
        json.append("  \"radiusBlocks\": ").append(job.radiusBlocks).append(",\n");
        json.append("  \"here\": {\n");
        json.append("    \"x\": ").append(job.targetX).append(",\n");
        json.append("    \"y\": ").append(job.targetY).append(",\n");
        json.append("    \"z\": ").append(job.targetZ).append(",\n");
        json.append("    \"deg\": ").append(fmt(job.hereDeg)).append(",\n");
        json.append("    \"band\": \"").append(escape(job.hereBandId)).append("\",\n");
        json.append("    \"biome\": \"").append(escape(job.hereBiomeId)).append("\"\n");
        json.append("  },\n");
        json.append("  \"readiness\": {\n");
        json.append("    \"totalStripChunks\": ").append(job.plan.totalChunks()).append(",\n");
        json.append("    \"finalLoadedFull\": ").append(job.finalLoadedFull).append(",\n");
        json.append("    \"finalLoadedRatio\": ").append(fmt(finalLoadedRatio)).append(",\n");
        json.append("    \"loadedRatioThreshold\": ").append(fmt(job.loadedRatioThreshold)).append(",\n");
        json.append("    \"readinessWaitedTicks\": ").append(job.readinessWaitedTicks).append(",\n");
        json.append("    \"timedOut\": ").append(job.readinessTimedOut).append("\n");
        json.append("  },\n");
        json.append("  \"probeStrip\": ").append(probeSummaryJson(job.probeSmall)).append(",\n");
        json.append("  \"probeSeam\": ").append(probeSummaryJson(job.probeLarge)).append(",\n");
        json.append("  \"validLoadedSampleRatio\": ").append(fmt(finalLoadedRatio)).append(",\n");
        json.append("  \"prepCompleted\": ").append(job.prepCompleted).append(",\n");
        json.append("  \"screenshotCaptured\": ").append(screenshotExists).append(",\n");
        json.append("  \"screenshotRequested\": ").append(job.screenshotRequested).append(",\n");
        json.append("  \"analysisStatus\": \"").append(analysisStatus).append("\"\n");
        json.append("}\n");

        Files.writeString(job.auditDir.resolve("summary.json"), json.toString(), StandardCharsets.UTF_8);

        GlobeMod.LOGGER.info("[auditMode] bundle done → {} (analysisStatus={}, prepCompleted={}, loadedRatio={}, screenshotCaptured={})",
                job.auditDir, analysisStatus, job.prepCompleted, fmt(finalLoadedRatio), screenshotExists);
    }

    private static String probeSummaryJson(ProbeResult r) {
        if (r == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{ \"geometry\": \"").append(escape(r.geometry)).append("\"");
        sb.append(", \"samples\": ").append(r.samples);
        sb.append(", \"loaded\": ").append(r.loaded);
        sb.append(", \"unloaded\": ").append(r.unloaded);
        sb.append(", \"topBiomes\": [");
        List<Map.Entry<String, Integer>> biomeList = new ArrayList<>(r.biomeCounts.entrySet());
        biomeList.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        int cap = Math.min(10, biomeList.size());
        for (int i = 0; i < cap; i++) {
            Map.Entry<String, Integer> e = biomeList.get(i);
            if (i > 0) sb.append(", ");
            sb.append("{\"id\":\"").append(escape(e.getKey())).append("\",\"count\":").append(e.getValue()).append("}");
        }
        sb.append("], \"bands\": {");
        List<Map.Entry<String, Integer>> bandList = new ArrayList<>(r.bandCounts.entrySet());
        bandList.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        boolean first = true;
        for (Map.Entry<String, Integer> e : bandList) {
            if (!first) sb.append(", ");
            sb.append("\"").append(escape(e.getKey())).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("} }");
        return sb.toString();
    }

    // ---------------- utilities ----------------

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static String biomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }

    private static String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }

    // ---------------- nested types ----------------

    enum Stage {
        SETTLE,
        PREP,
        READINESS,
        PROBE,
        HERE,
        SCREENSHOT,
        SCREENSHOT_WAIT,
        SUMMARY,
        DONE
    }

    enum Edge {
        CENTER("center"),
        LOW("low"),
        HIGH("high");

        private final String argName;

        Edge(String argName) {
            this.argName = argName;
        }

        String argName() {
            return argName;
        }

        double applyTo(double seamDeg) {
            return switch (this) {
                case LOW -> Math.max(0.0, seamDeg - 1.0);
                case HIGH -> Math.min(90.0, seamDeg + 1.0);
                default -> seamDeg;
            };
        }

        static Edge fromArg(String raw) {
            if (raw == null) return CENTER;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "seam", "center" -> CENTER;
                case "below", "low" -> LOW;
                case "above", "high" -> HIGH;
                default -> CENTER;
            };
        }
    }

    /** Adjacent canonical band pair (lower ordinal, higher ordinal). */
    record BandPair(LatitudeBands.Band lower, LatitudeBands.Band higher) {
        static BandPair parse(String raw) {
            if (raw == null) return null;
            String[] parts = raw.trim().toLowerCase(Locale.ROOT).split("-");
            if (parts.length != 2) return null;
            LatitudeBands.Band a = LatitudeBands.fromCanonicalId(parts[0]);
            LatitudeBands.Band b = LatitudeBands.fromCanonicalId(parts[1]);
            if (a == null || b == null || a == b) return null;
            if (Math.abs(a.ordinal() - b.ordinal()) != 1) return null;
            LatitudeBands.Band lo = a.ordinal() < b.ordinal() ? a : b;
            LatitudeBands.Band hi = a.ordinal() < b.ordinal() ? b : a;
            return new BandPair(lo, hi);
        }
    }

    private record ProbeResult(
            String geometry,
            int samples,
            int loaded,
            int unloaded,
            long seed,
            Map<String, Integer> biomeCounts,
            Map<String, Integer> bandCounts) {
    }

    private static final class Job {
        final ServerLevel world;
        final ServerPlayer player;
        final Path auditDir;
        final BandPair pair;
        final Edge edge;
        final double seamDeg;
        final double targetDeg;
        final int radiusBlocks;
        final int targetX;
        final int targetZ;
        int targetY = -1;
        final int samples;
        final SeamStripPrep.Plan plan;
        final Deque<ChunkPos> queue;
        final double loadedRatioThreshold;
        final int settleTicks;
        final int chunksPerTick;
        final int readinessTimeoutTicks;
        final long worldSeed;
        final Runnable onComplete;

        Stage stage = Stage.SETTLE;
        int settleTicksRemaining;
        int readinessWaitedTicks;
        int postScreenshotTicksRemaining;
        int finalLoadedFull;
        boolean prepCompleted;
        boolean readinessTimedOut;
        boolean screenshotRequested;
        Path screenshotPath;

        ProbeResult probeSmall;
        ProbeResult probeLarge;
        String hereBiomeId = "?";
        String hereBandId = "?";
        double hereDeg;

        Job(ServerLevel world,
            ServerPlayer player,
            Path auditDir,
            BandPair pair,
            Edge edge,
            double seamDeg,
            double targetDeg,
            int radiusBlocks,
            int targetX,
            int targetZ,
            int samples,
            SeamStripPrep.Plan plan,
            Deque<ChunkPos> queue,
            double loadedRatioThreshold,
            int settleTicks,
            int chunksPerTick,
            int readinessTimeoutTicks,
            long worldSeed,
            Runnable onComplete) {
            this.world = world;
            this.player = player;
            this.auditDir = auditDir;
            this.pair = pair;
            this.edge = edge;
            this.seamDeg = seamDeg;
            this.targetDeg = targetDeg;
            this.radiusBlocks = radiusBlocks;
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.samples = samples;
            this.plan = plan;
            this.queue = queue;
            this.loadedRatioThreshold = loadedRatioThreshold;
            this.settleTicks = settleTicks;
            this.chunksPerTick = chunksPerTick;
            this.readinessTimeoutTicks = readinessTimeoutTicks;
            this.worldSeed = worldSeed;
            this.onComplete = onComplete;
            this.settleTicksRemaining = settleTicks;
        }

        void tick() throws IOException {
            switch (stage) {
                case SETTLE -> {
                    if (settleTicksRemaining > 0) {
                        settleTicksRemaining--;
                        return;
                    }
                    stage = Stage.PREP;
                }
                case PREP -> {
                    int budget = chunksPerTick;
                    while (budget-- > 0 && !queue.isEmpty()) {
                        ChunkPos pos = queue.poll();
                        world.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, true);
                    }
                    if (queue.isEmpty()) {
                        stage = Stage.READINESS;
                    }
                }
                case READINESS -> {
                    readinessWaitedTicks++;
                    int loaded = SeamStripPrep.countLoadedFull(world, plan);
                    this.finalLoadedFull = loaded;
                    double ratio = (double) loaded / (double) Math.max(1, plan.totalChunks());
                    if (ratio >= loadedRatioThreshold) {
                        prepCompleted = true;
                        stage = Stage.PROBE;
                    } else if (readinessWaitedTicks >= readinessTimeoutTicks) {
                        prepCompleted = false;
                        readinessTimedOut = true;
                        GlobeMod.LOGGER.warn(
                                "[auditMode] readiness FAILED: loaded={}/{} ratio={} threshold={} (waited={}ticks) — bundle will be marked invalid",
                                loaded, plan.totalChunks(), fmt(ratio), fmt(loadedRatioThreshold), readinessWaitedTicks);
                        stage = Stage.PROBE;
                    }
                }
                case PROBE -> {
                    int sxMin = plan.min().x() * 16;
                    int sxMax = plan.max().x() * 16 + 15;
                    int szMin = plan.min().z() * 16;
                    int szMax = plan.max().z() * 16 + 15;
                    // Tight seam-crossing zone: full along-seam width, inner 1/3 of cross-seam half-width.
                    int seamZHalf = Math.max(16, plan.crossSeamHalfWidthBlocks() / 3);
                    probeSmall = runRectProbe(this, sxMin, sxMax, szMin, szMax, samples);
                    probeLarge = runRectProbe(this, sxMin, sxMax, targetZ - seamZHalf, targetZ + seamZHalf, Math.min(4000, samples * 2));
                    writeProbeFile(this, auditDir.resolve("probe_strip.txt"), probeSmall);
                    writeProbeFile(this, auditDir.resolve("probe_seam.txt"), probeLarge);
                    stage = Stage.HERE;
                }
                case HERE -> {
                    writeHere(this);
                    stage = Stage.SCREENSHOT;
                }
                case SCREENSHOT -> {
                    requestScreenshot(this);
                    postScreenshotTicksRemaining = POST_SCREENSHOT_WAIT_TICKS;
                    stage = Stage.SCREENSHOT_WAIT;
                }
                case SCREENSHOT_WAIT -> {
                    if (postScreenshotTicksRemaining > 0) {
                        postScreenshotTicksRemaining--;
                        return;
                    }
                    stage = Stage.SUMMARY;
                }
                case SUMMARY -> {
                    writeSummary(this);
                    stage = Stage.DONE;
                }
                default -> {
                }
            }
        }

        void writeFailureMarker(Exception e) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("# autonomous seam-audit failed\n");
            sb.append("stage=").append(stage).append("\n");
            sb.append("message=").append(e.getMessage()).append("\n");
            Files.writeString(auditDir.resolve("FAILED.txt"), sb.toString(), StandardCharsets.UTF_8);
        }
    }
}
