package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Deterministic seam-audit coordinator for dev tooling.
 *
 * <p>Responsibilities (this slice only):
 * <ul>
 *   <li>Teleport the player to a chosen canonical band boundary using the existing
 *       size-aware band authority (dynamic radius, canonical band degrees).</li>
 *   <li>Force-load chunks and wait a deterministic number of server ticks for settle.</li>
 *   <li>Run two fixed probes (r=512 and r=1024) at the target and record counts.</li>
 *   <li>Request a client screenshot via an injected bridge (dev client only).</li>
 *   <li>Bundle metadata/summary/probe outputs under {@code run/latdev/seam-audits/}.</li>
 * </ul>
 *
 * <p>This coordinator never changes band math, biome picker logic, or atlas authority.
 * It is evidence capture only.
 */
public final class SeamAuditCoordinator {

    /** Dev client bridge hook; the client-side helper supplies an implementation. */
    public interface ScreenshotHandler {
        void requestScreenshot(Path pngPath);
    }

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static volatile ScreenshotHandler screenshotHandler;
    private static boolean tickHookRegistered;
    private static volatile Job activeJob;

    private SeamAuditCoordinator() {
    }

    /** Called once by the dev client bridge to wire up screenshot capture. */
    public static synchronized void setScreenshotHandler(ScreenshotHandler handler) {
        screenshotHandler = handler;
    }

    /**
     * Begin a seam-audit run. Returns 1 on success, 0 on validation failure.
     *
     * @param radiusBlocks dynamic world radius in blocks (caller must supply the
     *                     authoritative value; this class does not guess).
     */
    public static int start(ServerCommandSource source,
                            String bandAArg,
                            String bandBArg,
                            String edgeArg,
                            int samples,
                            int waitTicks,
                            int radiusBlocks) {
        try {
            ensureTickHook();

            if (activeJob != null) {
                source.sendError(Text.literal("[seamAudit] job already active; wait for completion"));
                return 0;
            }

            LatitudeBands.Band bandA = LatitudeBands.fromCanonicalId(bandAArg);
            LatitudeBands.Band bandB = LatitudeBands.fromCanonicalId(bandBArg);
            if (bandA == null || bandB == null) {
                source.sendError(Text.literal("[seamAudit] bandA/bandB must be canonical: "
                        + String.join("|", LatitudeBands.canonicalIds())));
                return 0;
            }
            if (bandA == bandB || Math.abs(bandA.ordinal() - bandB.ordinal()) != 1) {
                source.sendError(Text.literal("[seamAudit] bands must be adjacent "
                        + "(tropical subtropical | subtropical temperate | temperate subpolar | subpolar polar)"));
                return 0;
            }

            LatitudeBands.Band lower = bandA.ordinal() < bandB.ordinal() ? bandA : bandB;
            LatitudeBands.Band higher = bandA.ordinal() < bandB.ordinal() ? bandB : bandA;
            double seamDeg = higher.lowDeg();

            EdgeChoice edge = EdgeChoice.fromArg(edgeArg);
            double targetDeg = edge.applyTo(seamDeg);

            if (radiusBlocks <= 0) {
                source.sendError(Text.literal("[seamAudit] invalid world radius: " + radiusBlocks));
                return 0;
            }

            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();

            int absZ = LatitudeMath.zForLatitudeDeg(targetDeg, radiusBlocks);
            int sign = player.getZ() < 0.0 ? -1 : 1;
            int targetZ = sign * absZ;
            int targetX = MathHelper.floor(player.getX());

            int clampedSamples = MathHelper.clamp(samples, 50, 4000);
            int clampedWait = MathHelper.clamp(waitTicks, 0, 600);

            String timestamp = TIMESTAMP_FMT.format(LocalDateTime.now());
            String pairId = lower.id() + "-" + higher.id();
            String dirName = timestamp + "_" + pairId + "_" + edge.argName();
            Path auditDir = source.getServer().getRunDirectory()
                    .toAbsolutePath().normalize()
                    .resolve("latdev").resolve("seam-audits").resolve(dirName);
            Files.createDirectories(auditDir);

            Job job = new Job(
                    source, world, player, auditDir,
                    lower, higher, edge,
                    seamDeg, targetDeg,
                    radiusBlocks,
                    targetX, targetZ,
                    clampedSamples, clampedWait,
                    world.getSeed());

            // Pre-load the 3x3 chunk area around the target and teleport the player.
            forceLoadAndTeleport(job);

            // Metadata first so the folder is non-empty even if later stages fail.
            writeMetadata(job);

            activeJob = job;

            String outName = auditDir.getFileName() != null ? auditDir.getFileName().toString() : auditDir.toString();
            ServerCommandSource src = source;
            src.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "[seamAudit] started pair=%s edge=%s seamDeg=%.2f targetDeg=%.2f target=(%d,%d) R=%d waitTicks=%d samples=%d out=latdev/seam-audits/%s",
                    pairId, edge.argName(), seamDeg, targetDeg,
                    job.targetX, job.targetZ, radiusBlocks, clampedWait, clampedSamples,
                    outName)), false);
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendError(Text.literal("[seamAudit] command requires a player: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            source.sendError(Text.literal("[seamAudit] start error: " + e.getMessage()));
            GlobeMod.LOGGER.error("[seamAudit] start failed", e);
            activeJob = null;
            return 0;
        }
    }

    private static void ensureTickHook() {
        if (tickHookRegistered) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(SeamAuditCoordinator::onEndServerTick);
        tickHookRegistered = true;
    }

    private static void onEndServerTick(MinecraftServer server) {
        Job job = activeJob;
        if (job == null) return;
        try {
            if (job.completed) {
                activeJob = null;
                return;
            }
            job.ticksSinceStart++;
            if (job.ticksSinceStart < job.waitTicks) {
                return;
            }

            runProbesAndWrite(job);
            writeHereCapture(job);
            requestScreenshot(job);
            writeSummary(job);

            Path outDir = job.auditDir;
            ServerCommandSource src = job.source;
            src.sendFeedback(() -> Text.literal("[seamAudit] done → " + outDir), false);

            job.completed = true;
            activeJob = null;
        } catch (Exception e) {
            GlobeMod.LOGGER.error("[seamAudit] tick stage failed", e);
            try {
                job.source.sendError(Text.literal("[seamAudit] failed: " + e.getMessage()));
            } catch (Exception ignored) {
            }
            activeJob = null;
        }
    }

    private static void forceLoadAndTeleport(Job job) {
        ServerWorld world = job.world;
        int cx = Math.floorDiv(job.targetX, 16);
        int cz = Math.floorDiv(job.targetZ, 16);

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                world.getChunkManager().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
            }
        }

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, job.targetX, job.targetZ);
        int worldMaxY = world.getBottomY() + world.getHeight() - 1;
        int targetY = MathHelper.clamp(topY + 1, world.getBottomY() + 1, worldMaxY);
        job.targetY = targetY;

        job.player.teleport(
                world,
                job.targetX + 0.5,
                targetY,
                job.targetZ + 0.5,
                EnumSet.noneOf(PositionFlag.class),
                job.player.getYaw(),
                job.player.getPitch(),
                true);
    }

    private static void writeMetadata(Job job) throws IOException {
        String branch = tryRunGit("rev-parse", "--abbrev-ref", "HEAD");
        String commit = tryRunGit("rev-parse", "--short", "HEAD");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schema\": \"seam-audit/v1\",\n");
        json.append("  \"timestamp\": \"").append(LocalDateTime.now()).append("\",\n");
        json.append("  \"bandPair\": \"").append(job.lower.id()).append("-").append(job.higher.id()).append("\",\n");
        json.append("  \"lowerBand\": \"").append(job.lower.id()).append("\",\n");
        json.append("  \"higherBand\": \"").append(job.higher.id()).append("\",\n");
        json.append("  \"edge\": \"").append(job.edge.argName()).append("\",\n");
        json.append("  \"seamDeg\": ").append(fmt(job.seamDeg)).append(",\n");
        json.append("  \"targetDeg\": ").append(fmt(job.targetDeg)).append(",\n");
        json.append("  \"radiusBlocks\": ").append(job.radiusBlocks).append(",\n");
        json.append("  \"targetX\": ").append(job.targetX).append(",\n");
        json.append("  \"targetY\": ").append(job.targetY).append(",\n");
        json.append("  \"targetZ\": ").append(job.targetZ).append(",\n");
        json.append("  \"worldSeed\": ").append(job.worldSeed).append(",\n");
        json.append("  \"samples\": ").append(job.samples).append(",\n");
        json.append("  \"waitTicks\": ").append(job.waitTicks).append(",\n");
        json.append("  \"gitBranch\": \"").append(escape(branch)).append("\",\n");
        json.append("  \"gitCommit\": \"").append(escape(commit)).append("\"\n");
        json.append("}\n");
        Files.writeString(job.auditDir.resolve("metadata.json"), json.toString(), StandardCharsets.UTF_8);
    }

    private static void runProbesAndWrite(Job job) throws IOException {
        ProbeResult small = runProbe(job, 512, job.samples);
        ProbeResult large = runProbe(job, 1024, Math.min(4000, job.samples * 2));
        job.probeSmall = small;
        job.probeLarge = large;

        Files.writeString(job.auditDir.resolve("probe_512.txt"), formatProbe(job, small), StandardCharsets.UTF_8);
        Files.writeString(job.auditDir.resolve("probe_1024.txt"), formatProbe(job, large), StandardCharsets.UTF_8);

        ServerCommandSource src = job.source;
        src.sendFeedback(() -> Text.literal("[seamAudit] probe r=512 top=" + topN(small.biomeCounts, small.loaded, 5)), false);
        src.sendFeedback(() -> Text.literal("[seamAudit] probe r=1024 top=" + topN(large.biomeCounts, large.loaded, 5)), false);
    }

    private static ProbeResult runProbe(Job job, int probeRadiusBlocks, int samples) {
        ServerWorld world = job.world;
        int centerX = job.targetX;
        int centerZ = job.targetZ;
        int sampleY;
        if (job.targetY > 0) {
            sampleY = job.targetY;
        } else {
            int top = world.getBottomY() + world.getHeight() - 1;
            sampleY = MathHelper.clamp(96, world.getBottomY() + 1, top);
        }

        long seed = world.getSeed() ^ mix64(((long) centerX << 32) ^ (centerZ & 0xffffffffL) ^ probeRadiusBlocks);
        Random rng = new Random(seed);

        Map<String, Integer> biomeCounts = new HashMap<>();
        Map<String, Integer> bandCounts = new HashMap<>();
        int unloaded = 0;

        for (int i = 0; i < samples; i++) {
            double r = Math.sqrt(rng.nextDouble()) * probeRadiusBlocks;
            double theta = rng.nextDouble() * (Math.PI * 2.0);
            int dx = (int) Math.round(r * Math.cos(theta));
            int dz = (int) Math.round(r * Math.sin(theta));
            int sx = centerX + dx;
            int sz = centerZ + dz;

            int cx = Math.floorDiv(sx, 16);
            int cz = Math.floorDiv(sz, 16);
            if (world.getChunkManager().getChunk(cx, cz, ChunkStatus.BIOMES, false) == null) {
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
        return new ProbeResult(probeRadiusBlocks, samples, loaded, unloaded, seed, biomeCounts, bandCounts);
    }

    private static String formatProbe(Job job, ProbeResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# seam-audit probe\n");
        sb.append("pair=").append(job.lower.id()).append("-").append(job.higher.id()).append("\n");
        sb.append("edge=").append(job.edge.argName()).append("\n");
        sb.append("targetDeg=").append(fmt(job.targetDeg)).append("\n");
        sb.append("centerX=").append(job.targetX).append(" centerY=").append(job.targetY).append(" centerZ=").append(job.targetZ).append("\n");
        sb.append("probeRadiusBlocks=").append(r.radiusBlocks).append("\n");
        sb.append("samples=").append(r.samples)
                .append(" loaded=").append(r.loaded)
                .append(" unloaded=").append(r.unloaded)
                .append(" seed=").append(r.seed)
                .append("\n");
        sb.append("\n[biomes]\n");
        appendSortedCounts(sb, r.biomeCounts, r.loaded);
        sb.append("\n[bands]\n");
        appendSortedCounts(sb, r.bandCounts, r.loaded);
        return sb.toString();
    }

    private static void appendSortedCounts(StringBuilder sb, Map<String, Integer> counts, int total) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        for (Map.Entry<String, Integer> e : list) {
            double pct = total > 0 ? (e.getValue() * 100.0) / total : 0.0;
            sb.append(String.format(Locale.ROOT, "%-60s %6d  %5.1f%%%n", e.getKey(), e.getValue(), pct));
        }
    }

    private static String topN(Map<String, Integer> counts, int total, int n) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : list) {
            if (i++ >= n) break;
            if (sb.length() > 0) sb.append(", ");
            double pct = total > 0 ? (e.getValue() * 100.0) / total : 0.0;
            sb.append(shortId(e.getKey())).append(" ").append(String.format(Locale.ROOT, "%.1f%%", pct));
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static String shortId(String id) {
        int c = id.indexOf(':');
        return c >= 0 ? id.substring(c + 1) : id;
    }

    private static void writeHereCapture(Job job) throws IOException {
        ServerWorld world = job.world;
        BlockPos pos = new BlockPos(job.targetX, job.targetY, job.targetZ);
        String bid = biomeId(world.getBiome(pos));
        double absLatDeg = Math.abs(job.targetZ) * 90.0 / (double) Math.max(1, job.radiusBlocks);
        String bandId = LatitudeBands.fromAbsoluteLatitudeDeg(absLatDeg).id();

        String text = String.format(Locale.ROOT,
                "# seam-audit here%n"
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
                Files.writeString(job.auditDir.resolve("screenshot.pending.txt"),
                        "No screenshot handler registered (not a dev client).\n",
                        StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
            return;
        }
        Path pngPath = job.auditDir.resolve("screenshot.png");
        try {
            handler.requestScreenshot(pngPath);
        } catch (Exception e) {
            GlobeMod.LOGGER.warn("[seamAudit] screenshot request failed", e);
        }
    }

    private static void writeSummary(Job job) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"bandPair\": \"").append(job.lower.id()).append("-").append(job.higher.id()).append("\",\n");
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
        json.append("  \"probe512\": ").append(probeSummaryJson(job.probeSmall)).append(",\n");
        json.append("  \"probe1024\": ").append(probeSummaryJson(job.probeLarge)).append(",\n");
        json.append("  \"screenshotAnalysis\": \"analysis_pending\"\n");
        json.append("}\n");
        Files.writeString(job.auditDir.resolve("summary.json"), json.toString(), StandardCharsets.UTF_8);
    }

    private static String probeSummaryJson(ProbeResult r) {
        if (r == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{ \"radiusBlocks\": ").append(r.radiusBlocks);
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

    private static String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        return biome.getKey().map(k -> k.getValue().toString()).orElse("?");
    }

    private static String tryRunGit(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            for (String a : args) cmd.add(a);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[512];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            }
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "unknown";
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Edge choice relative to the canonical seam degree. */
    public enum EdgeChoice {
        CENTER("center"),
        LOW("low"),
        HIGH("high");

        private final String argName;

        EdgeChoice(String argName) {
            this.argName = argName;
        }

        public String argName() {
            return argName;
        }

        public double applyTo(double seamDeg) {
            return switch (this) {
                case LOW -> Math.max(0.0, seamDeg - 1.0);
                case HIGH -> Math.min(90.0, seamDeg + 1.0);
                default -> seamDeg;
            };
        }

        public static EdgeChoice fromArg(String raw) {
            if (raw == null) return CENTER;
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "seam", "center" -> CENTER;
                case "below", "low" -> LOW;
                case "above", "high" -> HIGH;
                default -> CENTER;
            };
        }

        public static List<String> argNames() {
            return List.of("center", "low", "high");
        }
    }

    private record ProbeResult(
            int radiusBlocks,
            int samples,
            int loaded,
            int unloaded,
            long seed,
            Map<String, Integer> biomeCounts,
            Map<String, Integer> bandCounts) {
    }

    private static final class Job {
        final ServerCommandSource source;
        final ServerWorld world;
        final ServerPlayerEntity player;
        final Path auditDir;
        final LatitudeBands.Band lower;
        final LatitudeBands.Band higher;
        final EdgeChoice edge;
        final double seamDeg;
        final double targetDeg;
        final int radiusBlocks;
        final int targetX;
        final int targetZ;
        int targetY = -1;
        final int samples;
        final int waitTicks;
        final long worldSeed;
        int ticksSinceStart;
        boolean completed;
        ProbeResult probeSmall;
        ProbeResult probeLarge;
        String hereBiomeId = "?";
        String hereBandId = "?";
        double hereDeg;

        Job(ServerCommandSource source,
            ServerWorld world,
            ServerPlayerEntity player,
            Path auditDir,
            LatitudeBands.Band lower,
            LatitudeBands.Band higher,
            EdgeChoice edge,
            double seamDeg,
            double targetDeg,
            int radiusBlocks,
            int targetX,
            int targetZ,
            int samples,
            int waitTicks,
            long worldSeed) {
            this.source = source;
            this.world = world;
            this.player = player;
            this.auditDir = auditDir;
            this.lower = lower;
            this.higher = higher;
            this.edge = edge;
            this.seamDeg = seamDeg;
            this.targetDeg = targetDeg;
            this.radiusBlocks = radiusBlocks;
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.samples = samples;
            this.waitTicks = waitTicks;
            this.worldSeed = worldSeed;
        }
    }
}
