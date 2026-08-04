package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.core.GlacialMarkScan;
import com.example.globe.core.PowderRoofTrap;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.PowderCrevasseRoofFeature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Development-only fresh-world proof for the physical surface-trap scanner used by {@code markGlacial}.
 * It requests a caller-declared FULL chunk window, performs no manual world writes, and then reconstructs
 * traps strictly from the resulting blocks and cave biomes.
 */
public final class SurfaceTrapPhysicalAudit {
    static final String PREFIX = "latdev.surfaceTrapPhysicalAudit";
    static final String SCHEMA = "surface-trap-physical-audit-v2";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SurfaceTrapPhysicalAudit() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PREFIX, "false"));
    }

    public static void start(MinecraftServer server) {
        server.execute(() -> runAndStop(server));
    }

    static void runAndStop(MinecraftServer server) {
        Path fallback = server.getServerDirectory().resolve("latdev")
                .resolve("surface-trap-physical-audit.json").toAbsolutePath().normalize();
        Path out = fallback;
        JsonObject report = baseReport();
        String stage = "config";
        long telemetrySession = 0L;
        try {
            Config config = Config.read(server);
            out = config.out();
            report.add("config", config.toJson());
            report.addProperty("seed", server.overworld().getSeed());
            report.addProperty("world", server.getWorldData().getLevelName());
            if (!config.errors().isEmpty()) {
                fail(report, "config", config.errors());
                write(out, report);
                return;
            }

            ServerLevel world = server.overworld();
            stage = "full-chunk-generation";
            telemetrySession = PowderCrevasseRoofFeature.beginGenerationWriteTelemetry();
            List<ChunkCoordinate> requested = requestedChunks(
                    config.scanChunkMinX(), config.scanChunkMinZ(), config.scanChunkSpan());
            int generated = 0;
            List<String> generationFailures = new ArrayList<>();
            for (ChunkCoordinate chunk : requested) {
                if (world.getChunkSource().getChunk(
                        chunk.x(), chunk.z(), ChunkStatus.FULL, true) == null) {
                    generationFailures.add("FULL chunk unavailable " + chunk.x() + "," + chunk.z());
                } else {
                    generated++;
                }
            }
            PowderCrevasseRoofFeature.GenerationWriteTelemetry writeTelemetry =
                    PowderCrevasseRoofFeature.endGenerationWriteTelemetry(telemetrySession);
            telemetrySession = 0L;

            stage = "physical-scan";
            Snapshot snapshot = snapshot(world, config);
            int targetMinX = (config.targetChunkMinX() - config.scanChunkMinX()) * 16;
            int targetMinZ = (config.targetChunkMinZ() - config.scanChunkMinZ()) * 16;
            int targetSpanBlocks = config.targetChunkSpan() * 16;
            GlacialMarkScan.PhysicalScanReport scan =
                    GlacialMarkScan.scanPhysicalTrapVolumeInHorizontalBounds(
                            snapshot.cells(), snapshot.biomes(), PowderRoofTrap.MIN_SHAFT_DEPTH_BLOCKS,
                            snapshot.worldMinY(),
                            targetMinX, targetMinX + targetSpanBlocks,
                            targetMinZ, targetMinZ + targetSpanBlocks);

            stage = "json";
            finishReport(report, scan, requested.size(), generated,
                    snapshot.measuredColumns(), snapshot.measuredCells(), generationFailures,
                    writeTelemetry, snapshot.originX(), snapshot.worldMinY(), snapshot.originZ());
            write(out, report);
            GlobeMod.LOGGER.info(
                    "[latdev][surface-trap-physical-audit] status={} requested={} generated={} valid={} report={}",
                    report.get("status").getAsString(), requested.size(), generated,
                    scan.validTraps(), out);
        } catch (Throwable failure) {
            if (telemetrySession != 0L) {
                PowderCrevasseRoofFeature.endGenerationWriteTelemetry(telemetrySession);
            }
            fail(report, stage, List.of(describe(failure)));
            try {
                write(out, report);
            } catch (Throwable writeFailure) {
                failure.addSuppressed(writeFailure);
            }
            GlobeMod.LOGGER.error("[latdev][surface-trap-physical-audit] failed", failure);
        } finally {
            // No forced chunks, region tickets, or manual block writes are ever created by this audit.
            server.halt(false);
        }
    }

    private static Snapshot snapshot(ServerLevel world, Config config) {
        int width = config.scanChunkSpan() * 16;
        int depth = width;
        int originX = config.scanChunkMinX() << 4;
        int originZ = config.scanChunkMinZ() << 4;
        int minY = world.getMinY();
        int highest = minY;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                highest = Math.max(highest, world.getHeight(
                        Heightmap.Types.WORLD_SURFACE, originX + x, originZ + z));
            }
        }
        int maxYInclusive = GlacialMarkScan.physicalSampleMaxYInclusive(highest, world.getMaxY());
        int height = maxYInclusive - minY + 1;
        GlacialMarkScan.PhysicalCellKind[][][] cells =
                new GlacialMarkScan.PhysicalCellKind[width][height][depth];
        GlacialMarkScan.PhysicalBiomeKind[][][] biomes =
                new GlacialMarkScan.PhysicalBiomeKind[width][height][depth];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long measured = 0L;
        for (int x = 0; x < width; x++) {
            int worldX = originX + x;
            for (int z = 0; z < depth; z++) {
                int worldZ = originZ + z;
                for (int y = 0; y < height; y++) {
                    cursor.set(worldX, minY + y, worldZ);
                    BlockState state = world.getBlockState(cursor);
                    cells[x][y][z] = physicalCell(world, cursor, state);
                    String biome = world.getBiome(cursor).unwrapKey()
                            .map(key -> key.identifier().toString()).orElse("");
                    biomes[x][y][z] = LatitudeBiomes.GLACIAL_CAVES_ID.equals(biome)
                            ? GlacialMarkScan.PhysicalBiomeKind.GLACIAL_CAVES
                            : GlacialMarkScan.PhysicalBiomeKind.OTHER;
                    measured++;
                }
            }
        }
        return new Snapshot(cells, biomes, (long) width * depth, measured, minY, originX, originZ);
    }

    private static GlacialMarkScan.PhysicalCellKind physicalCell(
            ServerLevel world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) != null) {
            return GlacialMarkScan.PhysicalCellKind.BLOCK_ENTITY;
        }
        if (!state.getFluidState().isEmpty()) {
            return GlacialMarkScan.PhysicalCellKind.FLUID;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return GlacialMarkScan.PhysicalCellKind.POWDER_SNOW;
        }
        if (state.is(Blocks.SNOW_BLOCK)) {
            return GlacialMarkScan.PhysicalCellKind.SNOW_BLOCK;
        }
        if (state.is(Blocks.SNOW)) {
            return GlacialMarkScan.PhysicalCellKind.SNOW_LAYER;
        }
        if (state.isAir()) {
            return GlacialMarkScan.PhysicalCellKind.AIR;
        }
        if (state.getBlock() instanceof FallingBlock) {
            return GlacialMarkScan.PhysicalCellKind.GRAVITY_SOLID;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return GlacialMarkScan.PhysicalCellKind.MAGMA;
        }
        if (state.is(Blocks.BEDROCK)) {
            return GlacialMarkScan.PhysicalCellKind.BEDROCK;
        }
        if (state.is(Blocks.DEEPSLATE)) {
            return GlacialMarkScan.PhysicalCellKind.DEEPSLATE;
        }
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId != null && blockId.getPath().endsWith("_ore")) {
            return GlacialMarkScan.PhysicalCellKind.ORE;
        }
        if (state.getCollisionShape(world, pos).isEmpty()) {
            return GlacialMarkScan.PhysicalCellKind.PASSABLE_DRY;
        }
        if (state.isCollisionShapeFullBlock(world, pos)) {
            return GlacialMarkScan.PhysicalCellKind.DRY_SOLID;
        }
        return GlacialMarkScan.PhysicalCellKind.DRY_UNSTABLE;
    }

    static List<ChunkCoordinate> requestedChunks(int minX, int minZ, int span) {
        List<ChunkCoordinate> chunks = new ArrayList<>(Math.max(0, span * span));
        for (int z = minZ; z < minZ + span; z++) {
            for (int x = minX; x < minX + span; x++) {
                chunks.add(new ChunkCoordinate(x, z));
            }
        }
        return List.copyOf(chunks);
    }

    static JsonObject finishReport(
            JsonObject report,
            GlacialMarkScan.PhysicalScanReport scan,
            int requested,
            int generated,
            long measuredColumns,
            long measuredCells,
            List<String> generationFailures,
            PowderCrevasseRoofFeature.GenerationWriteTelemetry writeTelemetry,
            int originWorldX,
            int originWorldY,
            int originWorldZ) {
        JsonObject counts = new JsonObject();
        counts.addProperty("requestedChunks", requested);
        counts.addProperty("generatedFullChunks", generated);
        counts.addProperty("measuredColumns", measuredColumns);
        counts.addProperty("measuredCells", measuredCells);
        counts.addProperty("candidates", scan.candidates());
        counts.addProperty("valid", scan.validTraps());
        counts.addProperty("encounters", scan.encounters());
        counts.addProperty("partials", scan.partialComponents());
        counts.addProperty("unsafe", scan.unsafeComponents());
        int authoredCavernCount = (int) scan.validTrapEvidence().stream()
                .filter(evidence -> evidence.endpointKind()
                        == GlacialMarkScan.EndpointKind.AUTHORED_CAVERN).count();
        counts.addProperty("authoredCavernCount", authoredCavernCount);
        if (writeTelemetry != null && writeTelemetry.available()) {
            counts.addProperty("writeFailures", writeTelemetry.failedWriteBatches());
        } else {
            counts.add("writeFailures", JsonNull.INSTANCE);
        }
        report.add("counts", counts);
        report.add("rejections", JSON.toJsonTree(scan.rejectionReasons()));
        report.add("validTrapEvidence", JSON.toJsonTree(scan.validTrapEvidence()));
        JsonObject writeTelemetryJson = new JsonObject();
        boolean telemetryAvailable = writeTelemetry != null && writeTelemetry.available();
        writeTelemetryJson.addProperty("available", telemetryAvailable);
        writeTelemetryJson.addProperty("source", "PowderCrevasseRoofFeature.apply");
        if (telemetryAvailable) {
            writeTelemetryJson.addProperty("sessionId", writeTelemetry.sessionId());
            writeTelemetryJson.addProperty("attempts", writeTelemetry.attempts());
            writeTelemetryJson.addProperty("appliedSuccesses", writeTelemetry.appliedSuccesses());
            writeTelemetryJson.addProperty("failedWriteBatches", writeTelemetry.failedWriteBatches());
            writeTelemetryJson.addProperty("rolledBackWriteBatches", writeTelemetry.rolledBackWriteBatches());
        }
        report.add("writeFailureTelemetry", writeTelemetryJson);
        JsonObject coordinates = new JsonObject();
        coordinates.addProperty("space", "scan-local-block-index");
        coordinates.addProperty("originWorldX", originWorldX);
        coordinates.addProperty("originWorldY", originWorldY);
        coordinates.addProperty("originWorldZ", originWorldZ);
        report.add("evidenceCoordinates", coordinates);
        JsonArray failures = new JsonArray();
        generationFailures.forEach(failures::add);
        report.add("generationFailures", failures);

        JsonObject assertions = new JsonObject();
        assertions.addProperty("allRequestedChunksFull", generated == requested);
        assertions.addProperty("atLeastOneValidTrap", scan.validTraps() >= 1);
        assertions.addProperty("atLeastOneEncounter", scan.encounters() >= 1);
        assertions.addProperty("atLeastOneAuthoredCavern", authoredCavernCount >= 1);
        boolean downward = scan.validTrapEvidence().stream().anyMatch(evidence ->
                evidence.route() != null && evidence.route().downwardOrLevel()
                        && evidence.route().stations().size() >= 2
                        && evidence.route().stations().getLast().floorY()
                                < evidence.route().stations().getFirst().floorY());
        assertions.addProperty("atLeastOneDownwardRoute", downward);
        boolean cavernContracts = scan.validTrapEvidence().stream()
                .filter(evidence -> evidence.endpointKind()
                        == GlacialMarkScan.EndpointKind.AUTHORED_CAVERN)
                .allMatch(evidence -> evidence.cavern() != null
                        && evidence.cavern().contractPassed()
                        && evidence.cavern().floorColumns() >= 96
                        && evidence.cavern().span() >= 18
                        && evidence.cavern().boundingFill() >= 0.45
                        && evidence.cavern().boundingFill() <= 0.75
                        && evidence.cavern().distinctClearHeights() >= 3
                        && evidence.cavern().throatWidth() >= 3
                        && evidence.cavern().throatWidth() <= 5
                        && evidence.cavern().passageDirections() >= 2
                        && evidence.cavern().bent()
                        && evidence.cavern().crossesOwnerNeighbour()
                        && evidence.route().turns() <= 2
                        && evidence.route().neverReversesInitial());
        assertions.addProperty("everyAuthoredCavernMeetsPhysicalContract", cavernContracts);
        assertions.addProperty("writeFailureTelemetryAvailable", telemetryAvailable);
        assertions.addProperty("atLeastOneGenerationWriteAttempt",
                telemetryAvailable && writeTelemetry.attempts() >= 1);
        assertions.addProperty("zeroObservedWriteFailures",
                telemetryAvailable && writeTelemetry.failedWriteBatches() == 0);
        assertions.addProperty("noAuditTicketsCreated", true);
        report.add("assertions", assertions);

        boolean ok = generated == requested && generationFailures.isEmpty()
                && scan.validTraps() >= 1 && scan.encounters() >= 1
                && authoredCavernCount >= 1 && downward && cavernContracts
                && telemetryAvailable && writeTelemetry.attempts() >= 1
                && writeTelemetry.failedWriteBatches() == 0;
        report.addProperty("status", ok ? "ok" : "failed");
        return report;
    }

    private static JsonObject baseReport() {
        JsonObject report = new JsonObject();
        report.addProperty("schema", SCHEMA);
        report.addProperty("status", "failed");
        return report;
    }

    private static void fail(JsonObject report, String stage, List<String> errors) {
        report.addProperty("status", "failed");
        report.addProperty("failureStage", stage);
        JsonArray array = new JsonArray();
        errors.forEach(array::add);
        report.add("errors", array);
    }

    private static void write(Path out, JsonObject report) throws IOException {
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, JSON.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    record ChunkCoordinate(int x, int z) {
    }

    private record Snapshot(
            GlacialMarkScan.PhysicalCellKind[][][] cells,
            GlacialMarkScan.PhysicalBiomeKind[][][] biomes,
            long measuredColumns,
            long measuredCells,
            int worldMinY,
            int originX,
            int originZ) {
    }

    record Config(
            int targetChunkMinX,
            int targetChunkMinZ,
            int targetChunkSpan,
            int scanChunkMinX,
            int scanChunkMinZ,
            int scanChunkSpan,
            int centerX,
            int centerZ,
            int radiusChunks,
            Path out,
            List<String> errors) {

        static Config read(MinecraftServer server) {
            List<String> errors = new ArrayList<>();
            int targetMinX = requiredInt("targetChunkMinX", errors);
            int targetMinZ = requiredInt("targetChunkMinZ", errors);
            int targetSpan = requiredInt("targetChunkSpan", errors);
            int scanMinX = requiredInt("scanChunkMinX", errors);
            int scanMinZ = requiredInt("scanChunkMinZ", errors);
            int scanSpan = requiredInt("scanChunkSpan", errors);
            int centerX = requiredInt("centerX", errors);
            int centerZ = requiredInt("centerZ", errors);
            int radius = requiredInt("radiusChunks", errors);
            String rawOut = System.getProperty(PREFIX + ".out", "").trim();
            if (rawOut.isEmpty()) {
                errors.add(PREFIX + ".out is required");
            }
            Path out = rawOut.isEmpty()
                    ? server.getServerDirectory().resolve("latdev/surface-trap-physical-audit.json")
                    : Path.of(rawOut);
            return validated(targetMinX, targetMinZ, targetSpan, scanMinX, scanMinZ, scanSpan,
                    centerX, centerZ, radius, out, errors);
        }

        static Config validated(
                int targetMinX, int targetMinZ, int targetSpan,
                int scanMinX, int scanMinZ, int scanSpan,
                int centerX, int centerZ, int radius, Path out) {
            return validated(targetMinX, targetMinZ, targetSpan, scanMinX, scanMinZ, scanSpan,
                    centerX, centerZ, radius, out, new ArrayList<>());
        }

        private static Config validated(
                int targetMinX, int targetMinZ, int targetSpan,
                int scanMinX, int scanMinZ, int scanSpan,
                int centerX, int centerZ, int radius, Path out, List<String> errors) {
            if (targetSpan < 1 || targetSpan > 64) {
                errors.add("targetChunkSpan must be 1..64");
            }
            if (scanSpan < 1 || scanSpan > 65) {
                errors.add("scanChunkSpan must be 1..65");
            }
            if (radius < 0 || radius > 32) {
                errors.add("radiusChunks must be 0..32");
            }
            if (scanSpan != 2 * radius + 1) {
                errors.add("scanChunkSpan must equal 2*radiusChunks+1");
            }
            if (scanMinX != Math.floorDiv(centerX, 16) - radius
                    || scanMinZ != Math.floorDiv(centerZ, 16) - radius) {
                errors.add("scan chunk minimum must match centerX/centerZ and radiusChunks");
            }
            long targetMaxX = (long) targetMinX + targetSpan;
            long targetMaxZ = (long) targetMinZ + targetSpan;
            long scanMaxX = (long) scanMinX + scanSpan;
            long scanMaxZ = (long) scanMinZ + scanSpan;
            if (targetMinX < scanMinX || targetMinZ < scanMinZ
                    || targetMaxX > scanMaxX || targetMaxZ > scanMaxZ) {
                errors.add("target chunk square must be contained by the scan square");
            }
            if (targetSpan == 16 && scanSpan != 17) {
                errors.add("historical target span 16 requires scan span 17 (256 target / 289 scan chunks)");
            }
            return new Config(targetMinX, targetMinZ, targetSpan, scanMinX, scanMinZ, scanSpan,
                    centerX, centerZ, radius, out.toAbsolutePath().normalize(), List.copyOf(errors));
        }

        int targetChunkCount() {
            return targetChunkSpan * targetChunkSpan;
        }

        int scanChunkCount() {
            return scanChunkSpan * scanChunkSpan;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("targetChunkMinX", targetChunkMinX);
            json.addProperty("targetChunkMinZ", targetChunkMinZ);
            json.addProperty("targetChunkSpan", targetChunkSpan);
            json.addProperty("targetChunkCount", targetChunkCount());
            json.addProperty("scanChunkMinX", scanChunkMinX);
            json.addProperty("scanChunkMinZ", scanChunkMinZ);
            json.addProperty("scanChunkSpan", scanChunkSpan);
            json.addProperty("scanChunkCount", scanChunkCount());
            json.addProperty("centerX", centerX);
            json.addProperty("centerZ", centerZ);
            json.addProperty("radiusChunks", radiusChunks);
            json.addProperty("out", out.toString());
            return json;
        }

        private static int requiredInt(String suffix, List<String> errors) {
            String key = PREFIX + "." + suffix;
            String raw = System.getProperty(key, "").trim();
            if (raw.isEmpty()) {
                errors.add(key + " is required");
                return 0;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException malformed) {
                errors.add(key + " must be an integer");
                return 0;
            }
        }
    }
}
