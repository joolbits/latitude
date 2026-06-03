package com.example.globe.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldMapPreviewHeadlessRunner {
    private static final AtomicBoolean TRIGGERED = new AtomicBoolean(false);
    private static final String PROP_KEY = "latdev.worldMap";
    private static final Pattern PROP_PAIR = Pattern.compile(
            "(?i)([a-z][a-z0-9_]*)\\s*=\\s*([^;]+?)(?=(?:\\s*[;,]\\s*[a-z][a-z0-9_]*\\s*=)|$)");
    private static final Pattern TILE_FILE = Pattern.compile("x_(\\d+)_z_(\\d+)\\.png");
    private static final String CONTROL_FILE = "control.json";
    private static final String COLOR_MODEL = "surface-readable map-color with biome tinting";
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int DEFAULT_TILE_SIZE = 512;
    private static final int PREVIEW_RADIUS_ITTY = 3750;
    private static final int PREVIEW_RADIUS_TINY = 5000;
    private static final int PREVIEW_RADIUS_SMALL = 7500;
    private static final int PREVIEW_RADIUS_REGULAR = 10000;
    private static final int PREVIEW_RADIUS_LARGE = 15000;
    private static final int PREVIEW_RADIUS_GINORMOUS = 20000;

    private WorldMapPreviewHeadlessRunner() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(WorldMapPreviewHeadlessRunner::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        if (!TRIGGERED.compareAndSet(false, true)) {
            return;
        }

        Config config = parseConfig();
        if (!config.enabled()) {
            return;
        }

        server.execute(() -> runAndStop(server, config));
    }

    private static void runAndStop(MinecraftServer server, Config config) {
        ServerLevel world = server.overworld();
        if (world == null) {
            GlobeMod.LOGGER.error("[latdev][world-map] no overworld available; stopping server");
            server.halt(false);
            return;
        }

        Path jobDir = null;
        JobState state = null;
        try {
            long worldSeed = world.getSeed();
            long effectiveSeed = config.seedOverride() != null ? config.seedOverride() : worldSeed;
            int radius = radiusForSize(config.sizePreset());
            LatitudeBiomes.setWorldSeed(effectiveSeed);
            LatitudeBiomes.setActiveRadiusBlocks(radius);

            Path outputRoot = config.outDir() != null
                    ? config.outDir()
                    : server.getServerDirectory().toAbsolutePath().normalize()
                            .getParent().resolve("run-headless").resolve("latdev").resolve("world-map-runs");
            String jobId = sanitizeJobId(config.jobId());
            jobDir = outputRoot.resolve(jobId).toAbsolutePath().normalize();
            Path tilesDir = jobDir.resolve("tiles").resolve("z0");

            if (config.maxTiles() <= 0) {
                throw new IllegalArgumentException("P0 requires maxTiles > 0");
            }

            Instant started = Instant.now();
            GitStamp git = currentGitStamp();
            TilePlan plan = TilePlan.create(radius, config.tileSizeBlocks(), config.maxTiles());
            state = JobState.start(jobId, effectiveSeed, config.sizePreset(), radius,
                    config.tileSizeBlocks(), plan.totalChunkActions(), plan.tiles().size(), started);
            ControlCommand control = readControl(jobDir);
            if (control.stops() && !hasExistingSidecars(jobDir)) {
                state = state.withProgress(0, 0).withPhase(control.phase(), "");
                writeManifest(jobDir, ManifestPayload.from(config, jobId, effectiveSeed, radius, git, List.of(), control.phase()));
                writeState(jobDir, state);
                GlobeMod.LOGGER.info("[latdev][world-map] control {} before rendering job={} out={}",
                        control.command(), jobId, jobDir);
                return;
            }
            ReconciledRun reconciled = reconcileExistingRun(jobDir, plan, jobId, effectiveSeed,
                    config.sizePreset(), radius, config.tileSizeBlocks());
            Files.createDirectories(tilesDir);
            state = state.withProgress(reconciled.preservedChunkActions(plan), reconciled.records().size());
            writeState(jobDir, state);

            GlobeMod.LOGGER.info("[latdev][world-map] starting P0.5 job={} seed={} worldSeed={} size={} radius={} tileSize={} maxTiles={} preserved={} missing={} out={}",
                    jobId, effectiveSeed, worldSeed, config.sizePreset(), radius, config.tileSizeBlocks(),
                    config.maxTiles(), reconciled.records().size(), plan.tiles().size() - reconciled.records().size(), jobDir);

            List<TileRecord> records = new ArrayList<>();
            if (control.stops()) {
                records.addAll(reconciled.orderedRecords(plan));
                state = stateForRecords(state, plan, records, control.phase());
                writeManifest(jobDir, ManifestPayload.from(config, jobId, effectiveSeed, radius, git, records, control.phase()));
                writeState(jobDir, state);
                GlobeMod.LOGGER.info("[latdev][world-map] control {} before tile loop job={} preserved={}",
                        control.command(), jobId, records.size());
                return;
            }
            for (Tile tile : plan.tiles()) {
                control = readControl(jobDir);
                if (control.stops()) {
                    state = stateForRecords(state, plan, records, control.phase());
                    writeManifest(jobDir, ManifestPayload.from(config, jobId, effectiveSeed, radius, git, records, control.phase()));
                    writeState(jobDir, state);
                    GlobeMod.LOGGER.info("[latdev][world-map] control {} before tile job={} tile=({}, {}) preserved_or_rendered={}/{}",
                            control.command(), jobId, tile.tileX(), tile.tileZ(), records.size(), plan.tiles().size());
                    return;
                }

                TileRecord existing = reconciled.recordFor(tile);
                if (existing != null) {
                    records.add(existing);
                    GlobeMod.LOGGER.info("[latdev][world-map] tile preserved job={} tile=({}, {}) bounds=({}, {})..({}, {}) progress={}/{}",
                            jobId, tile.tileX(), tile.tileZ(), tile.minX(), tile.minZ(), tile.maxX(), tile.maxZ(),
                            state.tilesDone(), state.tilesTotal());
                    continue;
                }

                renderTile(world, tile, tilesDir.resolve(tile.fileName()));
                records.add(tile.record());
                state = state.afterTile(tile.chunkActions());
                writeState(jobDir, state);
                GlobeMod.LOGGER.info("[latdev][world-map] tile done job={} tile=({}, {}) bounds=({}, {})..({}, {}) progress={}/{}",
                        jobId, tile.tileX(), tile.tileZ(), tile.minX(), tile.minZ(), tile.maxX(), tile.maxZ(),
                        state.tilesDone(), state.tilesTotal());
            }

            writeManifest(jobDir, ManifestPayload.from(config, jobId, effectiveSeed, radius, git, records, "complete"));
            state = state.complete();
            writeState(jobDir, state);
            GlobeMod.LOGGER.info("[latdev][world-map] complete P0.5 job={} tiles={} chunks={} manifest={}",
                    jobId, state.tilesDone(), state.chunksDone(), jobDir.resolve("world_map_manifest.json"));
        } catch (Throwable t) {
            GlobeMod.LOGGER.error("[latdev][world-map] P0.5 export failed", t);
            if (state != null && jobDir != null) {
                try {
                    writeState(jobDir, state.failed(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
                } catch (Exception ignored) {
                }
            }
        } finally {
            GlobeMod.LOGGER.info("[latdev][world-map] stopping server");
            server.halt(false);
        }
    }

    private static void renderTile(ServerLevel world, Tile tile, Path outputPath) throws IOException {
        for (int chunkZ = Math.floorDiv(tile.minZ(), BLOCKS_PER_CHUNK);
             chunkZ <= Math.floorDiv(tile.maxZ(), BLOCKS_PER_CHUNK);
             chunkZ++) {
            for (int chunkX = Math.floorDiv(tile.minX(), BLOCKS_PER_CHUNK);
                 chunkX <= Math.floorDiv(tile.maxX(), BLOCKS_PER_CHUNK);
                 chunkX++) {
                world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }

        int width = tile.maxX() - tile.minX() + 1;
        int height = tile.maxZ() - tile.minZ() + 1;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int dz = 0; dz < height; dz++) {
            int blockZ = tile.minZ() + dz;
            for (int dx = 0; dx < width; dx++) {
                int blockX = tile.minX() + dx;
                image.setRGB(dx, dz, surfaceColor(world, blockX, blockZ));
            }
        }

        Files.createDirectories(outputPath.getParent());
        if (!ImageIO.write(image, "png", outputPath.toFile())) {
            throw new IOException("PNG writer unavailable for " + outputPath);
        }
        image.flush();
    }

    private static ReconciledRun reconcileExistingRun(Path jobDir,
                                                      TilePlan plan,
                                                      String jobId,
                                                      long seed,
                                                      String size,
                                                      int radius,
                                                      int tileSize) throws IOException {
        if (!Files.exists(jobDir)) {
            return ReconciledRun.empty();
        }
        if (!Files.isDirectory(jobDir)) {
            throw new ReconciliationException("existing job path is not a directory: " + jobDir);
        }

        Path statePath = jobDir.resolve("job_state.json");
        Path manifestPath = jobDir.resolve("world_map_manifest.json");
        if (!Files.exists(statePath) || !Files.exists(manifestPath)) {
            if (!Files.exists(statePath) && !Files.exists(manifestPath) && isFreshJobScaffold(jobDir)) {
                return ReconciledRun.empty();
            }
            throw new ReconciliationException("existing job is missing job_state.json or world_map_manifest.json: " + jobDir);
        }

        JsonObject state = readJsonObject(statePath);
        JsonObject manifest = readJsonObject(manifestPath);
        requireEquals("job_state.job_id", requireString(state, "job_id"), jobId);
        requireEquals("job_state.seed", requireLong(state, "seed"), seed);
        requireEquals("job_state.size", normalizeSize(requireString(state, "size")), normalizeSize(size));
        requireEquals("job_state.radius_blocks", requireInt(state, "radius_blocks"), radius);
        requireEquals("job_state.tile_size_blocks", requireInt(state, "tile_size_blocks"), tileSize);
        String phase = requireString(state, "phase");
        if (!phase.equals("complete") && !phase.equals("running") && !phase.equals("paused")) {
            throw new ReconciliationException("job_state.phase is not resumable: " + phase);
        }

        requireEquals("manifest.schema_version", requireInt(manifest, "schema_version"), 1);
        requireEquals("manifest.kind", requireString(manifest, "kind"), "world-map");
        if (manifest.has("job_id")) {
            requireEquals("manifest.job_id", requireString(manifest, "job_id"), jobId);
        }
        requireEquals("manifest.seed", requireLong(manifest, "seed"), seed);
        requireEquals("manifest.size", normalizeSize(requireString(manifest, "size")), normalizeSize(size));
        requireEquals("manifest.radius_blocks", requireInt(manifest, "radius_blocks"), radius);
        requireEquals("manifest.tile_size_blocks", requireInt(manifest, "tile_size_blocks"), tileSize);
        requireEquals("manifest.origin_min_x", requireInt(manifest, "origin_min_x"), -radius);
        requireEquals("manifest.origin_min_z", requireInt(manifest, "origin_min_z"), -radius);
        requireEquals("manifest.tiles_root", requireString(manifest, "tiles_root"), "tiles");
        String status = requireString(manifest, "status");
        if (!status.equals("complete") && !status.equals("paused")) {
            throw new ReconciliationException("manifest.status is not resumable: " + status);
        }
        requireEquals("manifest.color_model", requireString(manifest, "color_model"), COLOR_MODEL);

        Map<String, Tile> requested = plan.tilesByKey();
        Map<String, TileRecord> manifestRecords = new HashMap<>();
        Map<String, TileRecord> reusable = new HashMap<>();
        JsonArray tiles = requireArray(manifest, "tiles");
        for (int i = 0; i < tiles.size(); i++) {
            JsonElement element = tiles.get(i);
            if (!element.isJsonObject()) {
                throw new ReconciliationException("manifest tile " + i + " is not an object");
            }
            TileRecord record = parseTileRecord(element.getAsJsonObject(), i);
            String key = tileKey(record.tileX(), record.tileZ());
            Tile expected = requested.get(key);
            if (expected == null) {
                throw new ReconciliationException("manifest tile outside requested maxTiles plan: " + key);
            }
            if (manifestRecords.containsKey(key)) {
                throw new ReconciliationException("duplicate manifest tile: " + key);
            }
            if (!record.matches(expected)) {
                throw new ReconciliationException("manifest tile metadata does not match requested plan: " + key);
            }
            manifestRecords.put(key, record);
            if (isUsableTileImage(jobDir.resolve(record.path()), expected)) {
                reusable.put(key, record);
            } else {
                GlobeMod.LOGGER.info("[latdev][world-map] tile will be regenerated job={} tile=({}, {}) reason=missing-or-invalid-image",
                        jobId, record.tileX(), record.tileZ());
            }
        }

        validateTileFiles(jobDir, requested, manifestRecords);
        return new ReconciledRun(Map.copyOf(reusable));
    }

    private static boolean hasExistingSidecars(Path jobDir) {
        return Files.exists(jobDir.resolve("job_state.json"))
                || Files.exists(jobDir.resolve("world_map_manifest.json"));
    }

    private static boolean isFreshJobScaffold(Path jobDir) throws IOException {
        try (var stream = Files.list(jobDir)) {
            for (Path path : stream.toList()) {
                if (Files.isRegularFile(path) && path.getFileName().toString().equals(CONTROL_FILE)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private static JobState stateForRecords(JobState state, TilePlan plan, List<TileRecord> records, String phase) {
        return state.withProgress(chunkActionsForRecords(plan, records), records.size()).withPhase(phase, "");
    }

    private static int chunkActionsForRecords(TilePlan plan, List<TileRecord> records) {
        Map<String, Tile> requested = plan.tilesByKey();
        int chunks = 0;
        for (TileRecord record : records) {
            Tile tile = requested.get(record.key());
            if (tile != null) {
                chunks += tile.chunkActions();
            }
        }
        return chunks;
    }

    private static JsonObject readJsonObject(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new ReconciliationException(path + " does not contain a JSON object");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ReconciliationException("invalid JSON in " + path, e);
        }
    }

    private static String requireString(JsonObject object, String key) throws IOException {
        JsonElement element = requireKey(object, key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new ReconciliationException("expected string field: " + key);
        }
        return element.getAsString();
    }

    private static int requireInt(JsonObject object, String key) throws IOException {
        JsonElement element = requireKey(object, key);
        try {
            return element.getAsInt();
        } catch (NumberFormatException | ClassCastException | IllegalStateException e) {
            throw new ReconciliationException("expected int field: " + key, e);
        }
    }

    private static long requireLong(JsonObject object, String key) throws IOException {
        JsonElement element = requireKey(object, key);
        try {
            return element.getAsLong();
        } catch (NumberFormatException | ClassCastException | IllegalStateException e) {
            throw new ReconciliationException("expected long field: " + key, e);
        }
    }

    private static JsonArray requireArray(JsonObject object, String key) throws IOException {
        JsonElement element = requireKey(object, key);
        if (!element.isJsonArray()) {
            throw new ReconciliationException("expected array field: " + key);
        }
        return element.getAsJsonArray();
    }

    private static JsonElement requireKey(JsonObject object, String key) throws IOException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new ReconciliationException("missing required field: " + key);
        }
        return element;
    }

    private static void requireEquals(String name, String actual, String expected) throws IOException {
        if (!expected.equals(actual)) {
            throw new ReconciliationException(name + " mismatch: " + actual + " != " + expected);
        }
    }

    private static void requireEquals(String name, int actual, int expected) throws IOException {
        if (actual != expected) {
            throw new ReconciliationException(name + " mismatch: " + actual + " != " + expected);
        }
    }

    private static void requireEquals(String name, long actual, long expected) throws IOException {
        if (actual != expected) {
            throw new ReconciliationException(name + " mismatch: " + actual + " != " + expected);
        }
    }

    private static TileRecord parseTileRecord(JsonObject object, int index) throws IOException {
        return new TileRecord(
                requireInt(object, "z"),
                requireInt(object, "tile_x"),
                requireInt(object, "tile_z"),
                requireString(object, "path"),
                requireInt(object, "min_x"),
                requireInt(object, "max_x"),
                requireInt(object, "min_z"),
                requireInt(object, "max_z"));
    }

    private static boolean isUsableTileImage(Path path, Tile tile) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        BufferedImage image = null;
        try {
            image = ImageIO.read(path.toFile());
            return image != null && image.getWidth() == tile.width() && image.getHeight() == tile.height();
        } catch (IOException ignored) {
            return false;
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    private static void validateTileFiles(Path jobDir,
                                          Map<String, Tile> requested,
                                          Map<String, TileRecord> manifestRecords) throws IOException {
        Path tilesDir = jobDir.resolve("tiles").resolve("z0");
        if (!Files.exists(tilesDir)) {
            return;
        }
        try (var stream = Files.list(tilesDir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                if (!path.getFileName().toString().endsWith(".png")) {
                    throw new ReconciliationException("unexpected file in tiles/z0: " + path.getFileName());
                }
                Matcher matcher = TILE_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    throw new ReconciliationException("unexpected tile filename: " + path.getFileName());
                }
                String key = tileKey(parseInt(matcher.group(1), -1), parseInt(matcher.group(2), -1));
                if (!requested.containsKey(key)) {
                    throw new ReconciliationException("tile file outside requested maxTiles plan: " + path.getFileName());
                }
                if (!manifestRecords.containsKey(key)) {
                    throw new ReconciliationException("tile file exists without valid manifest metadata: " + path.getFileName());
                }
            }
        }
    }

    private static ControlCommand readControl(Path jobDir) throws IOException {
        if (jobDir == null) {
            return ControlCommand.none();
        }
        Path controlPath = jobDir.resolve(CONTROL_FILE);
        if (!Files.exists(controlPath)) {
            return ControlCommand.none();
        }

        JsonObject control = readJsonObject(controlPath);
        String command = requireString(control, "command").trim().toLowerCase(Locale.ROOT);
        return switch (command) {
            case "pause" -> ControlCommand.pause();
            case "resume" -> ControlCommand.none();
            case "cancel" -> ControlCommand.cancel();
            default -> throw new ControlException("unknown control command: " + command);
        };
    }

    private static int surfaceColor(ServerLevel world, int x, int z) {
        int topY = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (topY < world.getMinY()) {
            return 0x000000;
        }

        BlockPos pos = new BlockPos(x, topY, z);
        BlockState state = world.getBlockState(pos);
        FluidState fluid = world.getFluidState(pos);
        Holder<Biome> biomeHolder = world.getBiome(pos);
        Biome biome = biomeHolder.value();
        Block block = state.getBlock();

        if (fluid.is(FluidTags.WATER) || block == Blocks.WATER) {
            return biome.getWaterColor() & 0xFFFFFF;
        }
        if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS) {
            return biome.getGrassColor(x, z) & 0xFFFFFF;
        }
        if (isLeafBlock(block) || block == Blocks.VINE) {
            return biome.getFoliageColor() & 0xFFFFFF;
        }

        MapColor color = state.getMapColor(world, pos);
        int argb = color.calculateARGBColor(MapColor.Brightness.NORMAL);
        return argb & 0xFFFFFF;
    }

    private static boolean isLeafBlock(Block block) {
        return block == Blocks.OAK_LEAVES
                || block == Blocks.SPRUCE_LEAVES
                || block == Blocks.BIRCH_LEAVES
                || block == Blocks.JUNGLE_LEAVES
                || block == Blocks.ACACIA_LEAVES
                || block == Blocks.CHERRY_LEAVES
                || block == Blocks.DARK_OAK_LEAVES
                || block == Blocks.PALE_OAK_LEAVES
                || block == Blocks.MANGROVE_LEAVES
                || block == Blocks.AZALEA_LEAVES
                || block == Blocks.FLOWERING_AZALEA_LEAVES;
    }

    private static Config parseConfig() {
        Map<String, String> kv = new HashMap<>();
        String prop = System.getProperty(PROP_KEY, "");
        if (!prop.isBlank()) {
            parsePropertyOptions(prop, kv);
        }

        boolean enabled = parseBoolean(kv.get("enabled")) || !prop.isBlank();
        String size = normalizeSize(kv.getOrDefault("size", "ittybitty"));
        int tileSize = Mth.clamp(parseInt(kv.get("tilesize"), DEFAULT_TILE_SIZE), 16, 2048);
        int maxTiles = parseInt(kv.get("maxtiles"), -1);
        Long seed = parseLong(kv.get("seed"));
        Path out = kv.containsKey("out") && !kv.get("out").isBlank()
                ? Path.of(kv.get("out")).toAbsolutePath().normalize()
                : null;
        String job = kv.getOrDefault("job", "p0-world-map");
        return new Config(enabled, seed, size, tileSize, out, job, maxTiles);
    }

    private static void parsePropertyOptions(String prop, Map<String, String> out) {
        if (prop == null || prop.isBlank()) {
            return;
        }
        if (prop.equalsIgnoreCase("enabled") || prop.equalsIgnoreCase("true")) {
            out.put("enabled", "true");
            return;
        }
        Matcher matcher = PROP_PAIR.matcher(prop);
        while (matcher.find()) {
            out.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).trim());
        }
    }

    private static int radiusForSize(String size) {
        return switch (normalizeSize(size)) {
            case "itty", "ittybitty", "itty_bitty", "xsmall" -> PREVIEW_RADIUS_ITTY;
            case "tiny" -> PREVIEW_RADIUS_TINY;
            case "small", "medium" -> PREVIEW_RADIUS_SMALL;
            case "regular" -> PREVIEW_RADIUS_REGULAR;
            case "large" -> PREVIEW_RADIUS_LARGE;
            case "ginormous", "massive" -> PREVIEW_RADIUS_GINORMOUS;
            default -> PREVIEW_RADIUS_ITTY;
        };
    }

    private static String normalizeSize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return value.isBlank() ? "ittybitty" : value;
    }

    private static String sanitizeJobId(String raw) {
        String value = raw == null || raw.isBlank() ? "p0-world-map" : raw.trim();
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String tileKey(int tileX, int tileZ) {
        return tileX + "," + tileZ;
    }

    private static void writeState(Path jobDir, JobState state) throws IOException {
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve("job_state.json"), state.toJson());
    }

    private static void writeManifest(Path jobDir, ManifestPayload manifest) throws IOException {
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve("world_map_manifest.json"), manifest.toJson());
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on") || value.equals("enabled");
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static GitStamp currentGitStamp() {
        return new GitStamp(runGit("rev-parse", "--abbrev-ref", "HEAD"), runGit("rev-parse", "--short", "HEAD"));
    }

    private static String runGit(String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));
            Process process = new ProcessBuilder().command(command).start();
            byte[] out = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit == 0) {
                String text = new String(out).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private record Config(boolean enabled,
                          Long seedOverride,
                          String sizePreset,
                          int tileSizeBlocks,
                          Path outDir,
                          String jobId,
                          int maxTiles) {
    }

    private record GitStamp(String branch, String commit) {
    }

    private static final class ReconciliationException extends IOException {
        private ReconciliationException(String message) {
            super(message);
        }

        private ReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ControlException extends IOException {
        private ControlException(String message) {
            super(message);
        }
    }

    private record ControlCommand(String command, String phase) {
        private static ControlCommand none() {
            return new ControlCommand("resume", "running");
        }

        private static ControlCommand pause() {
            return new ControlCommand("pause", "paused");
        }

        private static ControlCommand cancel() {
            return new ControlCommand("cancel", "canceled");
        }

        private boolean stops() {
            return phase.equals("paused") || phase.equals("canceled");
        }
    }

    private record ReconciledRun(Map<String, TileRecord> records) {
        private static ReconciledRun empty() {
            return new ReconciledRun(Map.of());
        }

        private TileRecord recordFor(Tile tile) {
            return records.get(tile.key());
        }

        private int preservedChunkActions(TilePlan plan) {
            int chunks = 0;
            for (Tile tile : plan.tiles()) {
                if (recordFor(tile) != null) {
                    chunks += tile.chunkActions();
                }
            }
            return chunks;
        }

        private List<TileRecord> orderedRecords(TilePlan plan) {
            List<TileRecord> ordered = new ArrayList<>();
            for (Tile tile : plan.tiles()) {
                TileRecord record = recordFor(tile);
                if (record != null) {
                    ordered.add(record);
                }
            }
            return ordered;
        }
    }

    private record Tile(int tileX,
                        int tileZ,
                        int minX,
                        int maxX,
                        int minZ,
                        int maxZ) {
        private String fileName() {
            return "x_" + tileX + "_z_" + tileZ + ".png";
        }

        private String relativePath() {
            return "tiles/z0/" + fileName();
        }

        private String key() {
            return tileKey(tileX, tileZ);
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxZ - minZ + 1;
        }

        private int chunkActions() {
            int minChunkX = Math.floorDiv(minX, BLOCKS_PER_CHUNK);
            int maxChunkX = Math.floorDiv(maxX, BLOCKS_PER_CHUNK);
            int minChunkZ = Math.floorDiv(minZ, BLOCKS_PER_CHUNK);
            int maxChunkZ = Math.floorDiv(maxZ, BLOCKS_PER_CHUNK);
            return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        }

        private TileRecord record() {
            return new TileRecord(0, tileX, tileZ, relativePath(), minX, maxX, minZ, maxZ);
        }
    }

    private record TileRecord(int z,
                              int tileX,
                              int tileZ,
                              String path,
                              int minX,
                              int maxX,
                              int minZ,
                              int maxZ) {
        private String key() {
            return tileKey(tileX, tileZ);
        }

        private boolean matches(Tile tile) {
            return z == 0
                    && tileX == tile.tileX()
                    && tileZ == tile.tileZ()
                    && path.equals(tile.relativePath())
                    && minX == tile.minX()
                    && maxX == tile.maxX()
                    && minZ == tile.minZ()
                    && maxZ == tile.maxZ();
        }

        private String toJson() {
            return "{"
                    + "\"z\":" + z
                    + ",\"tile_x\":" + tileX
                    + ",\"tile_z\":" + tileZ
                    + ",\"path\":" + jsonString(path)
                    + ",\"min_x\":" + minX
                    + ",\"max_x\":" + maxX
                    + ",\"min_z\":" + minZ
                    + ",\"max_z\":" + maxZ
                    + "}";
        }
    }

    private record TilePlan(int radius, int tileSize, List<Tile> tiles) {
        private static TilePlan create(int radius, int tileSize, int maxTiles) {
            int originMinX = -radius;
            int originMinZ = -radius;
            int worldMax = radius;
            int width = (radius * 2) + 1;
            int tilesPerAxis = Math.max(1, Math.ceilDiv(width, tileSize));
            int limit = Math.min(maxTiles, tilesPerAxis * tilesPerAxis);
            List<Tile> tiles = new ArrayList<>();
            for (int tileZ = 0; tileZ < tilesPerAxis && tiles.size() < limit; tileZ++) {
                int minZ = originMinZ + (tileZ * tileSize);
                int maxZ = Math.min(worldMax, minZ + tileSize - 1);
                for (int tileX = 0; tileX < tilesPerAxis && tiles.size() < limit; tileX++) {
                    int minX = originMinX + (tileX * tileSize);
                    int maxX = Math.min(worldMax, minX + tileSize - 1);
                    tiles.add(new Tile(tileX, tileZ, minX, maxX, minZ, maxZ));
                }
            }
            return new TilePlan(radius, tileSize, List.copyOf(tiles));
        }

        private int totalChunkActions() {
            int total = 0;
            for (Tile tile : tiles) {
                total += tile.chunkActions();
            }
            return total;
        }

        private Map<String, Tile> tilesByKey() {
            Map<String, Tile> byKey = new HashMap<>();
            for (Tile tile : tiles) {
                byKey.put(tile.key(), tile);
            }
            return Map.copyOf(byKey);
        }
    }

    private record JobState(String jobId,
                            long seed,
                            String size,
                            int radiusBlocks,
                            int tileSizeBlocks,
                            String phase,
                            int chunksDone,
                            int chunksTotal,
                            int tilesDone,
                            int tilesTotal,
                            double percent,
                            Instant startedAt,
                            Instant updatedAt,
                            String error) {
        private static JobState start(String jobId,
                                      long seed,
                                      String size,
                                      int radiusBlocks,
                                      int tileSizeBlocks,
                                      int chunksTotal,
                                      int tilesTotal,
                                      Instant startedAt) {
            return new JobState(jobId, seed, size, radiusBlocks, tileSizeBlocks, "running",
                    0, chunksTotal, 0, tilesTotal, 0.0, startedAt, startedAt, "");
        }

        private JobState afterTile(int chunks) {
            int nextChunks = chunksDone + chunks;
            int nextTiles = tilesDone + 1;
            double nextPercent = tilesTotal <= 0 ? 100.0 : (nextTiles * 100.0) / tilesTotal;
            return new JobState(jobId, seed, size, radiusBlocks, tileSizeBlocks, "running",
                    nextChunks, chunksTotal, nextTiles, tilesTotal, nextPercent, startedAt, Instant.now(), "");
        }

        private JobState withProgress(int chunks, int tiles) {
            double nextPercent = tilesTotal <= 0 ? 100.0 : (tiles * 100.0) / tilesTotal;
            return new JobState(jobId, seed, size, radiusBlocks, tileSizeBlocks, "running",
                    chunks, chunksTotal, tiles, tilesTotal, nextPercent, startedAt, Instant.now(), "");
        }

        private JobState withPhase(String nextPhase, String message) {
            return new JobState(jobId, seed, size, radiusBlocks, tileSizeBlocks, nextPhase,
                    chunksDone, chunksTotal, tilesDone, tilesTotal, percent, startedAt, Instant.now(), message);
        }

        private JobState complete() {
            return new JobState(jobId, seed, size, radiusBlocks, tileSizeBlocks, "complete",
                    chunksDone, chunksTotal, tilesDone, tilesTotal, 100.0, startedAt, Instant.now(), "");
        }

        private JobState failed(String message) {
            return new JobState(jobId, seed, size, radiusBlocks, tileSizeBlocks, "failed",
                    chunksDone, chunksTotal, tilesDone, tilesTotal, percent, startedAt, Instant.now(), message);
        }

        private String toJson() {
            return "{\n"
                    + "  \"job_id\": " + jsonString(jobId) + ",\n"
                    + "  \"seed\": " + seed + ",\n"
                    + "  \"size\": " + jsonString(size) + ",\n"
                    + "  \"radius_blocks\": " + radiusBlocks + ",\n"
                    + "  \"tile_size_blocks\": " + tileSizeBlocks + ",\n"
                    + "  \"phase\": " + jsonString(phase) + ",\n"
                    + "  \"chunks_done\": " + chunksDone + ",\n"
                    + "  \"chunks_total\": " + chunksTotal + ",\n"
                    + "  \"tiles_done\": " + tilesDone + ",\n"
                    + "  \"tiles_total\": " + tilesTotal + ",\n"
                    + "  \"percent\": " + String.format(Locale.ROOT, "%.2f", percent) + ",\n"
                    + "  \"started_at\": " + jsonString(startedAt.toString()) + ",\n"
                    + "  \"updated_at\": " + jsonString(updatedAt.toString()) + ",\n"
                    + "  \"error\": " + jsonString(error) + "\n"
                    + "}\n";
        }
    }

    private record ManifestPayload(String jobId,
                                   long seed,
                                   String size,
                                   int radiusBlocks,
                                   int tileSizeBlocks,
                                   String branch,
                                   String commit,
                                   int originMinX,
                                   int originMinZ,
                                   List<TileRecord> tiles,
                                   String status) {
        private static ManifestPayload from(Config config,
                                            String jobId,
                                            long seed,
                                            int radius,
                                            GitStamp git,
                                            List<TileRecord> tiles,
                                            String status) {
            return new ManifestPayload(jobId, seed, config.sizePreset(), radius, config.tileSizeBlocks(),
                    git.branch(), git.commit(), -radius, -radius, List.copyOf(tiles), status);
        }

        private String toJson() {
            StringBuilder out = new StringBuilder();
            out.append("{\n");
            out.append("  \"schema_version\": 1,\n");
            out.append("  \"kind\": \"world-map\",\n");
            out.append("  \"job_id\": ").append(jsonString(jobId)).append(",\n");
            out.append("  \"seed\": ").append(seed).append(",\n");
            out.append("  \"size\": ").append(jsonString(size)).append(",\n");
            out.append("  \"radius_blocks\": ").append(radiusBlocks).append(",\n");
            out.append("  \"tile_size_blocks\": ").append(tileSizeBlocks).append(",\n");
            out.append("  \"branch\": ").append(jsonString(branch)).append(",\n");
            out.append("  \"commit\": ").append(jsonString(commit)).append(",\n");
            out.append("  \"color_model\": ").append(jsonString(COLOR_MODEL)).append(",\n");
            out.append("  \"origin_min_x\": ").append(originMinX).append(",\n");
            out.append("  \"origin_min_z\": ").append(originMinZ).append(",\n");
            out.append("  \"tiles_root\": \"tiles\",\n");
            out.append("  \"tiles\": [\n");
            for (int i = 0; i < tiles.size(); i++) {
                out.append("    ").append(tiles.get(i).toJson());
                if (i + 1 < tiles.size()) {
                    out.append(",");
                }
                out.append("\n");
            }
            out.append("  ],\n");
            out.append("  \"status\": ").append(jsonString(status)).append("\n");
            out.append("}\n");
            return out.toString();
        }
    }
}
