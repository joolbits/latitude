package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.core.SubterraneanTrapLayout;
import com.example.globe.core.SubterraneanTrapPlan;
import com.example.globe.world.NaturalGlacialCaveQualification;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Development-only, no-write audit for the saved historical surface-trap window.
 *
 * <p>It reconstructs the feature's 64 templates and preferred-depth classification from a saved world, then
 * measures the closest separately-qualified natural glacial-cave volume. It deliberately does not propose or
 * write a connector. All source under {@code com.example.globe.dev} is excluded from release artifacts.
 */
public final class SurfaceTrapConnectorAudit {
    private static final String PREFIX = "latdev.surfaceConnectorAudit";
    private static final String SCHEMA = "surface-trap-connector-audit-v1";
    static final int EXPECTED_HISTORICAL_CALLS = 106;
    static final int EXPECTED_TEMPLATES_PER_CALL = 64;
    static final int EXPECTED_TEMPLATE_EVALUATIONS = 6_784;
    static final int EXPECTED_HISTORICAL_TARGET_ROWS = 665;
    static final String EXPECTED_INPUT_SHA256 =
            "4f24cf5e1a8486ff62f236019e5e873a380256ebda86349f5eb3cf04b11aae08";
    private static final int[][] HORIZONTAL_STEPS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final int[] VERTICAL_STEPS = {0, -1, 1};
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Comparator<ChunkCoordinate> CHUNK_ORDER = Comparator
            .comparingInt(ChunkCoordinate::x).thenComparingInt(ChunkCoordinate::z);
    private static final Comparator<SubterraneanTrapPlan.RouteCell> CELL_ORDER = Comparator
            .comparingInt(SubterraneanTrapPlan.RouteCell::x)
            .thenComparingInt(SubterraneanTrapPlan.RouteCell::z)
            .thenComparingInt(SubterraneanTrapPlan.RouteCell::y);

    private SurfaceTrapConnectorAudit() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PREFIX, "false"));
    }

    public static void runAndStop(MinecraftServer server) {
        Path fallbackOut = server.getServerDirectory().resolve("latdev")
                .resolve("surface-trap-connector-audit.json").toAbsolutePath().normalize();
        Path out = fallbackOut;
        JsonObject report = baseReport();
        String failureStage = "config";
        try {
            Config config = Config.read(server);
            out = config.out();
            report.addProperty("seed", server.overworld().getSeed());
            if (!config.errors().isEmpty()) {
                markFailed(report, "config", config.errors());
                write(out, report);
                GlobeMod.LOGGER.error("[latdev][surface-connector-audit] invalid configuration: {}", config.errors());
                return;
            }
            failureStage = "input-read";
            InputPopulation input = readInputPopulation(config.chunks());
            failureStage = "saved-world-read";
            run(server.overworld(), config, input, report);
            write(out, report);
            GlobeMod.LOGGER.info("[latdev][surface-connector-audit] status={} rows={} report={}",
                    report.get("status").getAsString(),
                    report.has("preferredDepthStageRows") ? report.getAsJsonArray("preferredDepthStageRows").size() : 0,
                    out);
        } catch (Throwable failure) {
            markFailed(report, failureStage, List.of(failureDescription(failure)));
            try {
                write(out, report);
            } catch (Throwable writeFailure) {
                failure.addSuppressed(writeFailure);
            }
            GlobeMod.LOGGER.error("[latdev][surface-connector-audit] failed", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void run(
            ServerLevel world, Config config, InputPopulation input, JsonObject report) {
        List<ChunkCoordinate> targets = input.targets();
        Bounds inputBounds = Bounds.forChunks(targets);
        LoadedTargets loaded = loadedFullTargetChunks(world, targets);
        Set<ChunkCoordinate> loadedSet = Set.copyOf(loaded.loaded());

        report.addProperty("seed", world.getSeed());
        report.add("input", inputJson(config, input, inputBounds, loaded));
        report.add("loadedTargetChunks", chunkArray(loaded.loaded()));
        report.add("missingTargetChunks", chunkArray(loaded.missing()));

        List<Row> rows = new ArrayList<>();
        EnumMap<SubterraneanTrapPlan.Rejection, Integer> rejections =
                new EnumMap<>(SubterraneanTrapPlan.Rejection.class);
        int evaluatedCalls = 0;
        int templateEvaluations = 0;
        boolean everyCallHasExpectedTemplates = true;
        for (ChunkCoordinate chunk : loaded.loaded()) {
            evaluatedCalls++;
            Snapshot snapshot = snapshot(world, chunk);
            List<SubterraneanTrapLayout.Placement> placements =
                    SubterraneanTrapLayout.placements(world.getSeed(), chunk.x(), chunk.z());
            templateEvaluations += placements.size();
            everyCallHasExpectedTemplates &= placements.size() == EXPECTED_TEMPLATES_PER_CALL;
            for (int placementOrdinal = 0; placementOrdinal < placements.size(); placementOrdinal++) {
                SubterraneanTrapLayout.Placement placement = placements.get(placementOrdinal);
                List<SubterraneanTrapPlan.NaturalCaveDestination> direct = directDestinations(
                        world, placement, snapshot.firstAir(), SubterraneanTrapPlan.PREFERRED_DEPTH,
                        chunk.x() << 4, chunk.z() << 4);
                SubterraneanTrapPlan.SurfaceDiagnosticResult diagnostic =
                        SubterraneanTrapPlan.planWithSurfaceDiagnostics(
                                placement, snapshot.firstAir(), snapshot.kind(), direct);
                SubterraneanTrapPlan.Result result = diagnostic.result();
                if (result.rejection() != null) {
                    rejections.merge(result.rejection(), 1, Integer::sum);
                }
                if (!isPreferredDepthStage(result.rejection())) {
                    continue;
                }
                List<SubterraneanTrapPlan.DestinationProbe> probes = SubterraneanTrapPlan.destinationProbes(
                        placement, roofY(placement, snapshot.firstAir()) - SubterraneanTrapPlan.PREFERRED_DEPTH);
                List<SubterraneanTrapPlan.RouteCell> anchors = powderAnchors(
                        placement, snapshot.firstAir(), chunk.x() << 4, chunk.z() << 4);
                rows.add(new Row(chunk, placementOrdinal, anchors, roofY(placement, snapshot.firstAir()),
                        result.rejection().name(), direct.size(), probes.size(),
                        requestedRadiusClipped(anchors, config.maxHorizontal(), loadedSet)));
            }
        }
        rows.sort(Comparator.comparing(Row::chunk, CHUNK_ORDER).thenComparingInt(Row::placementOrdinal));

        SearchBounds search = SearchBounds.forRows(rows, loaded.loaded(), config.maxVerticalDrop());
        List<Volume> volumes = rows.isEmpty() ? List.of() : findVolumes(world, search, loadedSet);
        List<RowResult> resultRows = rows.stream()
                .map(row -> new RowResult(row, nearestVolume(
                        row.anchors(), row.chunk(), volumes, config.maxHorizontal(), config.maxVerticalDrop())))
                .toList();

        Reconciliation reconciliation = reconciliation(input, evaluatedCalls, templateEvaluations,
                everyCallHasExpectedTemplates, rows.size());
        report.addProperty("status", reconciliation.passes() && loaded.missing().isEmpty() ? "ok" : "failed");

        report.add("preferredDepthStageRows", rowArray(resultRows));
        report.add("rejectionBreakdown", rejectionJson(rejections));
        report.add("searchBounds", search.toJson());
        report.add("histograms", histogramJson(resultRows, volumes));
        report.add("reconciliation", reconciliation.toJson());
    }

    private static boolean isPreferredDepthStage(SubterraneanTrapPlan.Rejection rejection) {
        return rejection == SubterraneanTrapPlan.Rejection.DEPTH_OUTSIDE_HARD_LIMIT
                || rejection == SubterraneanTrapPlan.Rejection.DEPTH_OUTSIDE_LEGAL_RANGE
                || rejection == SubterraneanTrapPlan.Rejection.LOWER_CAVE_REQUIRED
                || rejection == SubterraneanTrapPlan.Rejection.DESCENT_ROUTE_CAPACITY
                || rejection == SubterraneanTrapPlan.Rejection.DESCENT_OWNER_BOUNDS;
    }

    private static Snapshot snapshot(ServerLevel level, ChunkCoordinate chunk) {
        int[][] firstAir = new int[16][16];
        int[][] kind = new int[16][16];
        int baseX = chunk.x() << 4;
        int baseZ = chunk.z() << 4;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int first = level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + localX, baseZ + localZ);
                BlockPos topPos = new BlockPos(baseX + localX, first - 1, baseZ + localZ);
                BlockState top = level.getBlockState(topPos);
                BlockPos supportPos = topPos.below();
                BlockState support = level.getBlockState(supportPos);
                if (top.is(Blocks.SNOW) && certifiedThinSupport(level, supportPos, support)) {
                    firstAir[localX][localZ] = first - 1;
                    kind[localX][localZ] = support.is(Blocks.SNOW_BLOCK)
                            ? SubterraneanTrapPlan.THIN_OVER_FULL_SNOW : SubterraneanTrapPlan.THIN_SNOW;
                } else {
                    firstAir[localX][localZ] = first;
                    kind[localX][localZ] = surfaceKind(top);
                }
            }
        }
        return new Snapshot(firstAir, kind);
    }

    private static List<SubterraneanTrapPlan.NaturalCaveDestination> directDestinations(
            ServerLevel level, SubterraneanTrapLayout.Placement placement, int[][] firstAir,
            int depth, int baseX, int baseZ) {
        int landingY = roofY(placement, firstAir) - depth;
        List<SubterraneanTrapPlan.NaturalCaveDestination> destinations = new ArrayList<>();
        for (SubterraneanTrapPlan.DestinationProbe probe
                : SubterraneanTrapPlan.destinationProbes(placement, landingY)) {
            List<SubterraneanTrapPlan.RouteCell> continuation =
                    NaturalGlacialCaveQualification.connectedLowerNaturalFloors(
                            level, probe.targetFloors(), probe, baseX, baseZ, 0, 15, 0, 15);
            if (continuation.size() < SubterraneanTrapPlan.MIN_NATURAL_CONTINUATION_FLOORS) {
                continue;
            }
            try {
                destinations.add(new SubterraneanTrapPlan.NaturalCaveDestination(probe, continuation));
            } catch (IllegalArgumentException ignored) {
                // The feature also fails closed on malformed runtime evidence.
            }
        }
        return List.copyOf(destinations);
    }

    private static List<Volume> findVolumes(
            ServerLevel level, SearchBounds bounds, Set<ChunkCoordinate> loadedChunks) {
        Set<SubterraneanTrapPlan.RouteCell> columns = new HashSet<>();
        for (ChunkCoordinate chunk : bounds.loadedChunks()) {
            int baseX = chunk.x() << 4;
            int baseZ = chunk.z() << 4;
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        SubterraneanTrapPlan.RouteCell cell = new SubterraneanTrapPlan.RouteCell(
                                baseX + localX, y, baseZ + localZ);
                        if (NaturalGlacialCaveQualification.naturalCaveColumn(level, cell, 0, 0)) {
                            columns.add(cell);
                        }
                    }
                }
            }
        }
        return connectedComponents(columns, loadedChunks);
    }

    static List<Volume> connectedComponents(Set<SubterraneanTrapPlan.RouteCell> qualifiedColumns) {
        return connectedComponents(qualifiedColumns, null);
    }

    static List<Volume> connectedComponents(
            Set<SubterraneanTrapPlan.RouteCell> qualifiedColumns, Set<ChunkCoordinate> loadedChunks) {
        if (qualifiedColumns == null || qualifiedColumns.isEmpty()) {
            return List.of();
        }
        List<SubterraneanTrapPlan.RouteCell> ordered = qualifiedColumns.stream().sorted(CELL_ORDER).toList();
        Set<SubterraneanTrapPlan.RouteCell> unseen = new HashSet<>(ordered);
        List<Volume> volumes = new ArrayList<>();
        for (SubterraneanTrapPlan.RouteCell seed : ordered) {
            if (!unseen.remove(seed)) {
                continue;
            }
            ArrayDeque<SubterraneanTrapPlan.RouteCell> queue = new ArrayDeque<>();
            List<SubterraneanTrapPlan.RouteCell> component = new ArrayList<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                SubterraneanTrapPlan.RouteCell current = queue.removeFirst();
                component.add(current);
                for (int[] horizontal : HORIZONTAL_STEPS) {
                    for (int dy : VERTICAL_STEPS) {
                        SubterraneanTrapPlan.RouteCell candidate = new SubterraneanTrapPlan.RouteCell(
                                current.x() + horizontal[0], current.y() + dy,
                                current.z() + horizontal[1]);
                        if (unseen.contains(candidate)
                                && NaturalGlacialCaveQualification.horizontalContinuationAdjacent(
                                        current, candidate)
                                && unseen.remove(candidate)) {
                            queue.addLast(candidate);
                        }
                    }
                }
            }
            if (component.size() < SubterraneanTrapPlan.MIN_NATURAL_CONTINUATION_FLOORS) {
                continue;
            }
            component.sort(CELL_ORDER);
            List<SubterraneanTrapPlan.RouteCell> cells = List.copyOf(component);
            volumes.add(new Volume(cells.get(0), cells,
                    loadedChunks != null && componentTouchesUnloadedChunk(cells, loadedChunks)));
        }
        volumes.sort(Comparator.comparing(Volume::id, CELL_ORDER));
        return List.copyOf(volumes);
    }

    private static boolean componentTouchesUnloadedChunk(
            List<SubterraneanTrapPlan.RouteCell> cells, Set<ChunkCoordinate> loadedChunks) {
        for (SubterraneanTrapPlan.RouteCell cell : cells) {
            for (int[] horizontal : HORIZONTAL_STEPS) {
                ChunkCoordinate neighborChunk = chunkAt(
                        cell.x() + horizontal[0], cell.z() + horizontal[1]);
                if (!loadedChunks.contains(neighborChunk)) {
                    return true;
                }
            }
        }
        return false;
    }

    static Nearest nearestVolume(
            List<SubterraneanTrapPlan.RouteCell> anchors, ChunkCoordinate owner,
            List<Volume> volumes, int maxHorizontal, int maxVerticalDrop) {
        Nearest best = null;
        for (Volume volume : volumes) {
            boolean componentIntersectsOwner = volume.cells().stream()
                    .anyMatch(cell -> chunkAt(cell.x(), cell.z()).equals(owner));
            for (SubterraneanTrapPlan.RouteCell anchor : anchors) {
                for (SubterraneanTrapPlan.RouteCell cell : volume.cells()) {
                    int horizontal = Math.abs(cell.x() - anchor.x()) + Math.abs(cell.z() - anchor.z());
                    int verticalDrop = anchor.y() - cell.y();
                    if (horizontal > maxHorizontal || verticalDrop <= 0 || verticalDrop > maxVerticalDrop) {
                        continue;
                    }
                    boolean nearestCellSameOwner = chunkAt(cell.x(), cell.z()).equals(owner);
                    Nearest candidate = new Nearest(anchor, cell, horizontal, verticalDrop,
                            nearestCellSameOwner, componentIntersectsOwner, volume.id(), volume.cells().size(),
                            volume.boundaryClipped());
                    if (best == null || candidate.compareTo(best) < 0) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static int roofY(SubterraneanTrapLayout.Placement placement, int[][] firstAir) {
        return placement.powder().stream().mapToInt(cell -> firstAir[cell.x()][cell.z()] - 1).min().orElseThrow();
    }

    static List<SubterraneanTrapPlan.RouteCell> powderAnchors(
            SubterraneanTrapLayout.Placement placement, int[][] firstAir, int baseX, int baseZ) {
        return placement.powder().stream()
                .map(cell -> new SubterraneanTrapPlan.RouteCell(
                        baseX + cell.x(), firstAir[cell.x()][cell.z()] - 1, baseZ + cell.z()))
                .distinct()
                .sorted(CELL_ORDER)
                .toList();
    }

    private static boolean certifiedThinSupport(ServerLevel level, BlockPos pos, BlockState support) {
        return support.getFluidState().isEmpty()
                && support.blocksMotion()
                && level.getBlockEntity(pos) == null
                && !isGravity(support)
                && isCarverReplaceableOrSnow(support);
    }

    private static int surfaceKind(BlockState state) {
        if (state.is(Blocks.SNOW_BLOCK)) {
            return SubterraneanTrapPlan.FULL_SNOW;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return SubterraneanTrapPlan.POWDER;
        }
        if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return SubterraneanTrapPlan.FIRM_GLACIAL_ICE;
        }
        return SubterraneanTrapPlan.OTHER;
    }

    private static boolean isCarverReplaceableOrSnow(BlockState state) {
        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES) || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.PACKED_ICE);
    }

    private static boolean isGravity(BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }

    private static LoadedTargets loadedFullTargetChunks(
            ServerLevel world, List<ChunkCoordinate> targets) {
        List<ChunkCoordinate> loaded = new ArrayList<>();
        List<ChunkCoordinate> missing = new ArrayList<>();
        for (ChunkCoordinate target : targets) {
            int x = target.x();
            int z = target.z();
            ChunkAccess chunk = world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk == null) {
                missing.add(target);
            } else {
                loaded.add(target);
            }
        }
        return new LoadedTargets(List.copyOf(loaded), List.copyOf(missing));
    }

    static boolean requestedRadiusClipped(
            List<SubterraneanTrapPlan.RouteCell> anchors, int radius, Set<ChunkCoordinate> loadedChunks) {
        if (anchors == null || anchors.isEmpty() || radius < 0 || loadedChunks == null) {
            return true;
        }
        for (SubterraneanTrapPlan.RouteCell anchor : anchors) {
            for (int dx = -radius; dx <= radius; dx++) {
                int remaining = radius - Math.abs(dx);
                for (int dz = -remaining; dz <= remaining; dz++) {
                    if (!loadedChunks.contains(chunkAt(anchor.x() + dx, anchor.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ChunkCoordinate chunkAt(int blockX, int blockZ) {
        return new ChunkCoordinate(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    static List<ChunkCoordinate> readTargetChunks(Path chunks) throws IOException {
        return readInputPopulation(chunks).targets();
    }

    static InputPopulation readInputPopulation(Path chunks) throws IOException {
        byte[] bytes = Files.readAllBytes(chunks);
        List<ChunkCoordinate> out = new ArrayList<>();
        Set<ChunkCoordinate> seen = new LinkedHashSet<>();
        int lineNumber = 0;
        for (String raw : new String(bytes, StandardCharsets.UTF_8).split("\\R", -1)) {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length != 2) {
                throw new IOException("invalid chunk line " + lineNumber + ": " + raw);
            }
            try {
                ChunkCoordinate coordinate = new ChunkCoordinate(
                        Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                if (!seen.add(coordinate)) {
                    throw new IOException("duplicate chunk line " + lineNumber + ": " + raw);
                }
                out.add(coordinate);
            } catch (NumberFormatException badNumber) {
                throw new IOException("invalid chunk line " + lineNumber + ": " + raw, badNumber);
            }
        }
        if (out.isEmpty()) {
            throw new IOException("chunk input is empty");
        }
        out.sort(CHUNK_ORDER);
        return new InputPopulation(List.copyOf(out), sha256(bytes));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static void writeInputPreflightReport(Path chunks, Path out) throws IOException {
        JsonObject report = baseReport();
        try {
            InputPopulation input = readInputPopulation(chunks);
            report.addProperty("status", "ok");
            report.addProperty("inputChunkCount", input.targets().size());
            report.addProperty("inputSha256", input.sha256());
        } catch (Throwable failure) {
            markFailed(report, "input-read", List.of(failureDescription(failure)));
        }
        write(out, report);
    }

    private static void write(Path out, JsonObject report) throws IOException {
        Path parent = out.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, JSON.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static JsonObject inputJson(
            Config config, InputPopulation population, Bounds input, LoadedTargets loaded) {
        JsonObject json = new JsonObject();
        json.addProperty("chunkFile", config.chunks().toString());
        json.addProperty("inputChunkCount", population.targets().size());
        json.addProperty("inputSha256", population.sha256());
        json.add("inputChunkBounds", input.toJson());
        json.addProperty("maxHorizontal", config.maxHorizontal());
        json.addProperty("maxVerticalDrop", config.maxVerticalDrop());
        json.addProperty("loadedTargetChunkCount", loaded.loaded().size());
        if (loaded.loaded().isEmpty()) {
            json.add("loadedTargetChunkBounds", JsonNull.INSTANCE);
        } else {
            json.add("loadedTargetChunkBounds", Bounds.forChunks(loaded.loaded()).toJson());
        }
        json.addProperty("missingTargetChunkCount", loaded.missing().size());
        json.addProperty("searchScope", "presently-loaded-target-chunks-only");
        return json;
    }

    private static JsonObject rejectionJson(EnumMap<SubterraneanTrapPlan.Rejection, Integer> counts) {
        JsonObject json = new JsonObject();
        for (SubterraneanTrapPlan.Rejection rejection : SubterraneanTrapPlan.Rejection.values()) {
            json.addProperty(rejection.name(), counts.getOrDefault(rejection, 0));
        }
        return json;
    }

    static Reconciliation reconciliation(
            InputPopulation input, int evaluatedCalls, int templateEvaluations,
            boolean everyCallHasExpectedTemplates, int rows) {
        return new Reconciliation(input.targets().size(), input.sha256(), evaluatedCalls,
                templateEvaluations, everyCallHasExpectedTemplates, rows);
    }

    private static JsonObject histogramJson(List<RowResult> rows, List<Volume> volumes) {
        JsonObject json = new JsonObject();
        Map<Integer, Integer> horizontal = new java.util.TreeMap<>();
        Map<Integer, Integer> vertical = new java.util.TreeMap<>();
        int found = 0;
        int sameOwner = 0;
        int componentOwner = 0;
        int clipped = 0;
        int globallyCertified = 0;
        for (RowResult result : rows) {
            if (result.row().requestedRadiusBoundaryClipped()) {
                clipped++;
            } else {
                globallyCertified++;
            }
            if (result.nearest() == null) {
                continue;
            }
            found++;
            horizontal.merge(result.nearest().horizontalManhattan(), 1, Integer::sum);
            vertical.merge(result.nearest().verticalDrop(), 1, Integer::sum);
            if (result.nearest().nearestCellSameOwner()) {
                sameOwner++;
            }
            if (result.nearest().componentIntersectsOwner()) {
                componentOwner++;
            }
        }
        json.addProperty("precomputedQualifyingVolumes", volumes.size());
        json.addProperty("rowsWithNearestVolume", found);
        json.addProperty("rowsWithNearestCellInOwner", sameOwner);
        json.addProperty("rowsWhoseNearestComponentIntersectsOwner", componentOwner);
        json.addProperty("rowsWithRequestedRadiusBoundaryClipped", clipped);
        json.addProperty("rowsWithGlobalNearestCertified", globallyCertified);
        json.add("horizontalManhattan", integerHistogram(horizontal));
        json.add("verticalDrop", integerHistogram(vertical));
        return json;
    }

    private static JsonObject integerHistogram(Map<Integer, Integer> histogram) {
        JsonObject json = new JsonObject();
        histogram.forEach((key, value) -> json.addProperty(String.valueOf(key), value));
        return json;
    }

    private static JsonArray rowArray(List<RowResult> rows) {
        JsonArray out = new JsonArray();
        for (RowResult result : rows) {
            Row row = result.row();
            JsonObject json = new JsonObject();
            json.addProperty("chunkX", row.chunk().x());
            json.addProperty("chunkZ", row.chunk().z());
            json.addProperty("placementOrdinal", row.placementOrdinal());
            json.add("connectorEntryAnchors", coordinateArray(row.anchors()));
            json.addProperty("preferredRoofY", row.preferredRoofY());
            json.addProperty("rejection", row.rejection());
            json.addProperty("directProbeCount", row.directProbeCount());
            json.addProperty("directCertifiedDestinationCount", row.directCertifiedDestinationCount());
            json.addProperty("requestedRadiusBoundaryClipped", row.requestedRadiusBoundaryClipped());
            json.addProperty("globalNearestWithinRequestedRadius",
                    !row.requestedRadiusBoundaryClipped());
            json.addProperty("nearestScope", row.requestedRadiusBoundaryClipped()
                    ? "loaded-target-chunks-only" : "complete-requested-radius");
            if (result.nearest() == null) {
                json.add("nearest", JsonNull.INSTANCE);
            } else {
                Nearest nearest = result.nearest();
                json.add("selectedAnchor", coordinateJson(
                        nearest.anchor().x(), nearest.anchor().y(), nearest.anchor().z()));
                json.add("nearest", coordinateJson(nearest.cell().x(), nearest.cell().y(), nearest.cell().z()));
                json.addProperty("horizontalManhattan", nearest.horizontalManhattan());
                json.addProperty("verticalDrop", nearest.verticalDrop());
                json.addProperty("nearestCellSameOwner", nearest.nearestCellSameOwner());
                json.addProperty("componentIntersectsOwner", nearest.componentIntersectsOwner());
                json.add("componentId", coordinateJson(
                        nearest.componentId().x(), nearest.componentId().y(), nearest.componentId().z()));
                json.addProperty("componentCellCount", nearest.componentCellCount());
                json.addProperty("componentBoundaryClipped", nearest.componentBoundaryClipped());
                json.addProperty("componentCellCountComplete", !nearest.componentBoundaryClipped());
            }
            out.add(json);
        }
        return out;
    }

    private static JsonObject coordinateJson(int x, int y, int z) {
        JsonObject json = new JsonObject();
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        return json;
    }

    private static JsonArray coordinateArray(List<SubterraneanTrapPlan.RouteCell> cells) {
        JsonArray array = new JsonArray();
        for (SubterraneanTrapPlan.RouteCell cell : cells) {
            array.add(coordinateJson(cell.x(), cell.y(), cell.z()));
        }
        return array;
    }

    private static JsonObject baseReport() {
        JsonObject report = new JsonObject();
        report.addProperty("schema", SCHEMA);
        report.addProperty("readOnly", true);
        report.addProperty("generatesChunks", false);
        report.addProperty("writesWorldBlocks", false);
        report.addProperty("usesEnsureCanWrite", false);
        return report;
    }

    private static void markFailed(JsonObject report, String stage, List<String> errors) {
        report.addProperty("status", "failed");
        report.addProperty("failureStage", stage);
        report.add("errors", strings(errors));
    }

    private static String failureDescription(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonArray chunkArray(List<ChunkCoordinate> values) {
        JsonArray array = new JsonArray();
        for (ChunkCoordinate value : values) {
            JsonObject json = new JsonObject();
            json.addProperty("x", value.x());
            json.addProperty("z", value.z());
            array.add(json);
        }
        return array;
    }

    record ChunkCoordinate(int x, int z) {
    }

    record InputPopulation(List<ChunkCoordinate> targets, String sha256) {
    }

    private record LoadedTargets(List<ChunkCoordinate> loaded, List<ChunkCoordinate> missing) {
    }

    private record Snapshot(int[][] firstAir, int[][] kind) {
    }

    record Row(ChunkCoordinate chunk, int placementOrdinal, List<SubterraneanTrapPlan.RouteCell> anchors,
               int preferredRoofY, String rejection, int directCertifiedDestinationCount,
               int directProbeCount, boolean requestedRadiusBoundaryClipped) {
    }

    private record RowResult(Row row, Nearest nearest) {
    }

    record Volume(SubterraneanTrapPlan.RouteCell id, List<SubterraneanTrapPlan.RouteCell> cells,
                  boolean boundaryClipped) {
    }

    record Nearest(SubterraneanTrapPlan.RouteCell anchor, SubterraneanTrapPlan.RouteCell cell,
                   int horizontalManhattan, int verticalDrop, boolean nearestCellSameOwner,
                   boolean componentIntersectsOwner, SubterraneanTrapPlan.RouteCell componentId,
                   int componentCellCount, boolean componentBoundaryClipped) implements Comparable<Nearest> {
        @Override
        public int compareTo(Nearest other) {
            int horizontal = Integer.compare(horizontalManhattan, other.horizontalManhattan);
            if (horizontal != 0) {
                return horizontal;
            }
            int vertical = Integer.compare(verticalDrop, other.verticalDrop);
            if (vertical != 0) {
                return vertical;
            }
            int byAnchor = CELL_ORDER.compare(anchor, other.anchor);
            if (byAnchor != 0) {
                return byAnchor;
            }
            int byCell = CELL_ORDER.compare(cell, other.cell);
            return byCell != 0 ? byCell : CELL_ORDER.compare(componentId, other.componentId);
        }
    }

    record Reconciliation(int observedInputCalls, String observedInputSha256, int observedEvaluatedCalls,
                          int observedTemplateEvaluations, boolean everyCallHasExpectedTemplates,
                          int observedPreferredDepthRows) {
        boolean passes() {
            return observedInputCalls == EXPECTED_HISTORICAL_CALLS
                    && EXPECTED_INPUT_SHA256.equals(observedInputSha256)
                    && observedEvaluatedCalls == EXPECTED_HISTORICAL_CALLS
                    && everyCallHasExpectedTemplates
                    && observedTemplateEvaluations == EXPECTED_TEMPLATE_EVALUATIONS
                    && observedPreferredDepthRows == EXPECTED_HISTORICAL_TARGET_ROWS;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("expectedInputCalls", EXPECTED_HISTORICAL_CALLS);
            json.addProperty("observedInputCalls", observedInputCalls);
            json.addProperty("expectedInputSha256", EXPECTED_INPUT_SHA256);
            json.addProperty("observedInputSha256", observedInputSha256);
            json.addProperty("expectedEvaluatedCalls", EXPECTED_HISTORICAL_CALLS);
            json.addProperty("observedEvaluatedCalls", observedEvaluatedCalls);
            json.addProperty("expectedTemplatesPerCall", EXPECTED_TEMPLATES_PER_CALL);
            json.addProperty("everyCallHasExpectedTemplates", everyCallHasExpectedTemplates);
            json.addProperty("expectedTemplateEvaluations", EXPECTED_TEMPLATE_EVALUATIONS);
            json.addProperty("observedTemplateEvaluations", observedTemplateEvaluations);
            json.addProperty("expectedPreferredDepthStageRows", EXPECTED_HISTORICAL_TARGET_ROWS);
            json.addProperty("observedPreferredDepthStageRows", observedPreferredDepthRows);
            json.addProperty("passes", passes());
            return json;
        }
    }

    private record Bounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        static Bounds forChunks(List<ChunkCoordinate> chunks) {
            return new Bounds(chunks.stream().mapToInt(ChunkCoordinate::x).min().orElseThrow(),
                    chunks.stream().mapToInt(ChunkCoordinate::x).max().orElseThrow(),
                    chunks.stream().mapToInt(ChunkCoordinate::z).min().orElseThrow(),
                    chunks.stream().mapToInt(ChunkCoordinate::z).max().orElseThrow());
        }

        Bounds expandChunks(int margin) {
            return new Bounds(minChunkX - margin, maxChunkX + margin, minChunkZ - margin, maxChunkZ + margin);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("minChunkX", minChunkX);
            json.addProperty("maxChunkX", maxChunkX);
            json.addProperty("minChunkZ", minChunkZ);
            json.addProperty("maxChunkZ", maxChunkZ);
            return json;
        }
    }

    private record SearchBounds(
            List<ChunkCoordinate> loadedChunks, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        static SearchBounds forRows(
                List<Row> rows, List<ChunkCoordinate> loadedChunks, int maxVerticalDrop) {
            int minY = rows.stream().flatMap(row -> row.anchors().stream())
                    .mapToInt(anchor -> Math.max(1, anchor.y() - maxVerticalDrop)).min().orElse(1);
            int maxY = rows.stream().flatMap(row -> row.anchors().stream())
                    .mapToInt(SubterraneanTrapPlan.RouteCell::y).max().orElse(1);
            if (loadedChunks.isEmpty()) {
                return new SearchBounds(List.of(), 0, -1, 0, -1, minY, maxY);
            }
            Bounds loaded = Bounds.forChunks(loadedChunks);
            return new SearchBounds(List.copyOf(loadedChunks), loaded.minChunkX() << 4,
                    (loaded.maxChunkX() << 4) + 15, loaded.minChunkZ() << 4,
                    (loaded.maxChunkZ() << 4) + 15, minY, maxY);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("minX", minX);
            json.addProperty("maxX", maxX);
            json.addProperty("minZ", minZ);
            json.addProperty("maxZ", maxZ);
            json.addProperty("minY", minY);
            json.addProperty("maxY", maxY);
            json.addProperty("loadedChunkCount", loadedChunks.size());
            json.addProperty("columnPredicateCallsBound", (long) loadedChunks.size()
                    * 16L * 16L * (maxY - minY + 1));
            return json;
        }
    }

    private record Config(Path chunks, int maxHorizontal, int maxVerticalDrop, Path out, List<String> errors) {
        static Config read(MinecraftServer server) {
            List<String> errors = new ArrayList<>();
            Path chunks = requiredPath("chunks", errors);
            int horizontal = integer("maxHorizontal", 64, errors);
            int vertical = integer("maxVerticalDrop", 96, errors);
            if (horizontal < 1 || horizontal > 128) {
                errors.add("maxHorizontal must be 1..128");
            }
            if (vertical < 1 || vertical > 128) {
                errors.add("maxVerticalDrop must be 1..128");
            }
            Path out = optionalPath("out");
            if (out == null) {
                out = server.getServerDirectory().resolve("latdev").resolve("surface-trap-connector-audit.json")
                        .toAbsolutePath().normalize();
            }
            return new Config(chunks, horizontal, vertical, out, List.copyOf(errors));
        }

        private static int integer(String suffix, int fallback, List<String> errors) {
            String raw = System.getProperty(PREFIX + "." + suffix, "").trim();
            if (raw.isEmpty()) {
                return fallback;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException failure) {
                errors.add(suffix + " must be an integer");
                return fallback;
            }
        }

        private static Path requiredPath(String suffix, List<String> errors) {
            Path path = optionalPath(suffix);
            if (path == null) {
                errors.add(suffix + " must be supplied");
            }
            return path;
        }

        private static Path optionalPath(String suffix) {
            String raw = System.getProperty(PREFIX + "." + suffix, "").trim();
            return raw.isEmpty() ? null : Path.of(raw).toAbsolutePath().normalize();
        }
    }
}
