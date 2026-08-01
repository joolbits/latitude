package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.core.CaveDropTrap;
import com.example.globe.world.CaveDropTrapFeature;
import com.example.globe.world.CaveDropTrapFeature.AuthoredPosition;
import com.example.globe.world.CaveDropTrapFeature.AuthoredRole;
import com.example.globe.world.CaveDropTrapFeature.EntryEvidence;
import com.example.globe.world.CaveTrapBlocks;
import com.example.globe.world.CollapsedExplorerBlock;
import com.example.globe.world.CollapsedExplorerBlockEntity;
import com.example.globe.world.CollapsedExplorerBlocks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development-only, strict full-chunk evidence harness for inner-cave traps.
 *
 * <p>The harness observes only successful production writes. Every gameplay assertion is then made
 * against the exact authored cells and routes carried by {@link CaveDropTrapFeature.AuditEvent}; it
 * never treats an unrelated open cave cell as proof. This class is excluded from playable jars.
 */
public final class CaveDropTrapFullChunkAudit {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String PREFIX = "latdev.caveDropAudit";
    private static final String SCHEMA = "cave-drop-full-chunk-audit-v4";
    private static final String CENSUS_SCHEMA = "cave-drop-candidate-census-v6";
    private static final int FLOODED_GALLERY_MIN_FOOTPRINT = 36;
    private static final int FLOODED_GALLERY_ENTRANCE_MARGIN = 16;
    private static final int MIN_STABLE_SUPPORT_DEPTH = 4;
    private static final int MOB_DESCENT_TICK_LIMIT = 200;
    private static final double MOB_NAVIGATION_SPEED = 1.0D;
    private static final String TREASURE_LOOT = "globe:chests/collapsed_explorer_treasure";
    private static final Map<String, Integer> FLOODED_ORE_COMPOSITION = Map.of(
            "minecraft:deepslate_diamond_ore", 2,
            "minecraft:deepslate_gold_ore", 4,
            "minecraft:deepslate_iron_ore", 5,
            "minecraft:deepslate_redstone_ore", 5);
    private static volatile Run activeRun;

    private CaveDropTrapFullChunkAudit() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PREFIX, "false"));
    }

    public static void start(MinecraftServer server) {
        Config config = Config.read(server);
        if (!config.errors().isEmpty()) {
            GlobeMod.LOGGER.error("[latdev][cave-drop-audit] invalid configuration: {}", config.errors());
            server.halt(false);
            return;
        }
        try {
            Run run = new Run(server, config);
            activeRun = run;
            if (config.census()) {
                try {
                    CaveDropTrapFeature.setPassACensusCallback(
                            run::captureProductionCensusTelemetry);
                    run.generateFullChunks();
                } finally {
                    CaveDropTrapFeature.setPassACensusCallback(null);
                }
                activeRun = null;
                boolean passed = run.writeCensusReport();
                GlobeMod.LOGGER.info(
                        "[latdev][cave-drop-audit] census verdict={} chunks={} scanSlots={} "
                                + "ordinaryFeasible={} report={}",
                        passed ? "PASS" : "FAIL",
                        run.censusChunkCount(), run.censusTotals().scanSlots(),
                        run.censusTotals().ordinaryFeasible(), config.out());
                server.halt(false);
                return;
            }
            if (config.generate()) {
                CaveDropTrapFeature.setAuditCallback(run::capture);
                run.generateFullChunks();
                // FULL generation is synchronous here. Freeze the observed scene set before the
                // settling window so unrelated later chunk work cannot enter without an immediate read.
                CaveDropTrapFeature.setAuditCallback(null);
            } else {
                run.loadBaseline();
                run.loadBaselineChunks();
            }
            run.captureImmediateFingerprints();
            run.pendingTicks = config.afterTicks();
            ServerTickEvents.END_SERVER_TICK.register(CaveDropTrapFullChunkAudit::onServerTick);
            GlobeMod.LOGGER.info(
                    "[latdev][cave-drop-audit] prepared mode={} chunks=({}, {}) span={} afterTicks={} out={}",
                    config.mode(), config.chunkMinX(), config.chunkMinZ(), config.chunkSpan(),
                    config.afterTicks(), config.out());
        } catch (Throwable failure) {
            CaveDropTrapFeature.setAuditCallback(null);
            CaveDropTrapFeature.setCensusCallback(null);
            CaveDropTrapFeature.setPassACensusCallback(null);
            activeRun = null;
            GlobeMod.LOGGER.error("[latdev][cave-drop-audit] setup failed", failure);
            server.halt(false);
        }
    }

    private static void onServerTick(MinecraftServer server) {
        Run run = activeRun;
        if (run == null || run.server != server) {
            return;
        }
        try {
            if (!run.auditPrepared) {
                if (run.pendingTicks-- > 0) {
                    return;
                }
                run.prepareAudit();
                if (run.beginMobNavigationAssay()) {
                    return;
                }
            } else if (!run.tickMobNavigationAssay()) {
                return;
            }
            completeRun(server, run);
        } catch (Throwable failure) {
            activeRun = null;
            run.discardAssayMob();
            GlobeMod.LOGGER.error("[latdev][cave-drop-audit] audit failed", failure);
            CaveDropTrapFeature.setAuditCallback(null);
            CaveDropTrapFeature.setCensusCallback(null);
            CaveDropTrapFeature.setPassACensusCallback(null);
            server.halt(false);
        }
    }

    private static void completeRun(MinecraftServer server, Run run) throws IOException {
        activeRun = null;
        CaveDropTrapFeature.setAuditCallback(null);
        CaveDropTrapFeature.setCensusCallback(null);
        CaveDropTrapFeature.setPassACensusCallback(null);
        boolean passed = run.writeReport();
        run.discardAssayMob();
        GlobeMod.LOGGER.info(
                "[latdev][cave-drop-audit] verdict={} scenes={} ordinary={} flooded={} "
                        + "prospector={} failures={} mobNavigationAssay={} report={}",
                passed ? "PASS" : "FAIL",
                run.stableScenes.size(),
                run.count(CaveDropTrap.RewardKind.ORDINARY),
                run.count(CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY),
                run.count(CaveDropTrap.RewardKind.LOST_PROSPECTOR),
                run.failures.size(),
                run.mobNavigationAssay.status(),
                run.config.out());
        server.halt(false);
    }

    private record Config(
            String mode,
            int chunkMinX,
            int chunkMinZ,
            int chunkSpan,
            int afterTicks,
            Path out,
            Path baseline,
            int minOrdinary,
            int minFlooded,
            int minProspector,
            List<String> errors) {

        boolean generate() {
            return "generate".equals(mode);
        }

        boolean census() {
            return "census".equals(mode);
        }

        static Config read(MinecraftServer server) {
            List<String> errors = new ArrayList<>();
            String mode = System.getProperty(PREFIX + ".mode", "generate")
                    .trim().toLowerCase(Locale.ROOT);
            if (!mode.equals("generate") && !mode.equals("rescan") && !mode.equals("census")) {
                errors.add("mode must be generate, rescan, or census");
            }
            int minX = integer("chunkMinX", 0, errors, false);
            int minZ = integer("chunkMinZ", 0, errors, false);
            int span = integer("chunkSpan", 1, errors, false);
            int ticks = integer("afterTicks", 200, errors, false);
            if (span < 1 || span > 128) {
                errors.add("chunkSpan must be 1..128");
            }
            if (ticks < 0 || ticks > 24_000) {
                errors.add("afterTicks must be 0..24000");
            }
            Path out = path(
                    "out",
                    server.getServerDirectory().resolve("latdev").resolve("cave-drop-audit.json"));
            Path baseline = optionalPath("baseline");
            if (mode.equals("rescan") && baseline == null) {
                errors.add("rescan requires latdev.caveDropAudit.baseline");
            }

            // Every audit mode has an explicit outcome gate. Census remains no-write, but its
            // predicted resolved outcomes must meet the caller's declared window minima.
            int ordinary = integer("minOrdinary", 0, errors, true);
            int flooded = integer("minFlooded", 0, errors, true);
            int prospector = integer("minProspector", 0, errors, true);
            if (ordinary <= 0) {
                errors.add("minOrdinary must be positive");
            }
            if (flooded < 0 || prospector < 0) {
                errors.add("rare minimum counts must be non-negative");
            }
            return new Config(
                    mode, minX, minZ, span, ticks, out, baseline,
                    ordinary, flooded, prospector, List.copyOf(errors));
        }

        private static int integer(
                String name, int fallback, List<String> errors, boolean required) {
            String raw = System.getProperty(PREFIX + "." + name, "").trim();
            if (raw.isEmpty()) {
                if (required) {
                    errors.add(name + " must be supplied explicitly");
                }
                return fallback;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException failure) {
                errors.add(name + " must be an integer");
                return fallback;
            }
        }

        private static Path path(String name, Path fallback) {
            Path path = optionalPath(name);
            return path == null ? fallback.toAbsolutePath().normalize() : path;
        }

        private static Path optionalPath(String name) {
            String raw = System.getProperty(PREFIX + "." + name, "").trim();
            return raw.isEmpty() ? null : Path.of(raw).toAbsolutePath().normalize();
        }
    }

    public record CensusReportGate(
            boolean diagnosticReconciliationPass,
            boolean outcomeGatePass,
            boolean pass) {
    }

    public static CensusReportGate evaluateCensusReportGate(
            int requestedRows,
            int observedRows,
            boolean everyObservedRowReconciles,
            boolean aggregateReconciles,
            long scanSlots,
            long resolvedOrdinary,
            long resolvedFlooded,
            long resolvedProspector,
            int minimumOrdinary,
            int minimumFlooded,
            int minimumProspector) {
        boolean diagnostic = requestedRows > 0
                && observedRows == requestedRows
                && everyObservedRowReconciles
                && aggregateReconciles
                && scanSlots > 0;
        boolean outcomes = resolvedOrdinary >= minimumOrdinary
                && resolvedFlooded >= minimumFlooded
                && resolvedProspector >= minimumProspector;
        return new CensusReportGate(diagnostic, outcomes, diagnostic && outcomes);
    }

    public static List<String> missingCensusCoordinates(
            int chunkMinX,
            int chunkMinZ,
            int chunkSpan,
            Set<String> observedCoordinates) {
        List<String> missing = new ArrayList<>();
        for (int z = chunkMinZ; z < chunkMinZ + chunkSpan; z++) {
            for (int x = chunkMinX; x < chunkMinX + chunkSpan; x++) {
                String coordinate = x + "," + z;
                if (!observedCoordinates.contains(coordinate)) {
                    missing.add(coordinate);
                }
            }
        }
        return List.copyOf(missing);
    }

    @FunctionalInterface
    public interface DriverChunkScanner {
        CaveDropTrapFeature.PassACensusSnapshot scan(RandomSource random) throws Exception;
    }

    /** One driver-owned row and the deterministic audit-only RNG key that produced it. */
    public record DriverCensusRow(
            String rngKey,
            CaveDropTrapFeature.PassACensusSnapshot snapshot) {

        public DriverCensusRow {
            rngKey = java.util.Objects.requireNonNull(rngKey);
            snapshot = java.util.Objects.requireNonNull(snapshot);
        }
    }

    /**
     * Exact requested-window ledger. Callback telemetry never enters this map and therefore cannot
     * satisfy coverage.
     */
    public static final class DriverCensusLedger {
        private final int chunkMinX;
        private final int chunkMinZ;
        private final int chunkSpan;
        private final String schema;
        private final long worldSeed;
        private final Map<String, DriverCensusRow> rows = new ConcurrentHashMap<>();
        private final List<String> failures =
                Collections.synchronizedList(new ArrayList<>());

        public DriverCensusLedger(
                int chunkMinX,
                int chunkMinZ,
                int chunkSpan,
                String schema,
                long worldSeed) {
            if (chunkSpan < 1) {
                throw new IllegalArgumentException("chunkSpan must be positive");
            }
            this.chunkMinX = chunkMinX;
            this.chunkMinZ = chunkMinZ;
            this.chunkSpan = chunkSpan;
            this.schema = java.util.Objects.requireNonNull(schema);
            this.worldSeed = worldSeed;
        }

        public void scanRequestedChunk(
                int chunkX,
                int chunkZ,
                DriverChunkScanner scanner) {
            java.util.Objects.requireNonNull(scanner);
            String coordinate = coordinate(chunkX, chunkZ);
            if (!requested(chunkX, chunkZ)) {
                failures.add("driver census coordinate outside requested window " + coordinate);
                return;
            }
            if (rows.containsKey(coordinate)) {
                failures.add("duplicate driver census row " + coordinate);
                return;
            }
            long rngKey = driverCensusRngKey(schema, worldSeed, chunkX, chunkZ);
            String serializedKey = serializeRngKey(rngKey);
            CaveDropTrapFeature.PassACensusSnapshot snapshot;
            try {
                snapshot = scanner.scan(RandomSource.create(rngKey));
            } catch (Exception failure) {
                failures.add("driver census exception " + coordinate + " "
                        + failure.getClass().getSimpleName() + ": "
                        + String.valueOf(failure.getMessage()));
                return;
            }
            recordDriverResult(chunkX, chunkZ, serializedKey, snapshot);
        }

        public void recordDriverResult(
                int chunkX,
                int chunkZ,
                String rngKey,
                CaveDropTrapFeature.PassACensusSnapshot snapshot) {
            String coordinate = coordinate(chunkX, chunkZ);
            if (!requested(chunkX, chunkZ)) {
                failures.add("driver census coordinate outside requested window " + coordinate);
                return;
            }
            String expectedKey = serializeRngKey(
                    driverCensusRngKey(schema, worldSeed, chunkX, chunkZ));
            if (!expectedKey.equals(rngKey)) {
                failures.add("wrong driver census RNG key " + coordinate);
                return;
            }
            if (snapshot == null) {
                failures.add("null driver census row " + coordinate);
                return;
            }
            if (snapshot.chunkX() != chunkX || snapshot.chunkZ() != chunkZ) {
                failures.add("wrong driver census row coordinate " + coordinate
                        + " got " + snapshot.chunkX() + "," + snapshot.chunkZ());
                return;
            }
            // A chunk whose passage trace found no candidate is an honest, reconciling row
            // under the conformal source (qualified 0, everything source-rejected); only a
            // row whose audit DOMAIN is empty proves a broken or miswired scan.
            if (snapshot.auditDomainSlots() <= 0) {
                failures.add("zero-domain driver census row " + coordinate);
                return;
            }
            DriverCensusRow prior =
                    rows.putIfAbsent(coordinate, new DriverCensusRow(rngKey, snapshot));
            if (prior != null) {
                failures.add("duplicate driver census row " + coordinate);
            }
        }

        public void recordUnavailableChunk(int chunkX, int chunkZ) {
            String coordinate = coordinate(chunkX, chunkZ);
            if (!requested(chunkX, chunkZ)) {
                failures.add("unavailable census coordinate outside requested window "
                        + coordinate);
                return;
            }
            failures.add("requested FULL chunk unavailable " + coordinate);
        }

        public DriverCensusRow row(int chunkX, int chunkZ) {
            return rows.get(coordinate(chunkX, chunkZ));
        }

        public List<DriverCensusRow> rowsInWindowOrder() {
            List<DriverCensusRow> ordered = new ArrayList<>();
            for (int z = chunkMinZ; z < chunkMinZ + chunkSpan; z++) {
                for (int x = chunkMinX; x < chunkMinX + chunkSpan; x++) {
                    DriverCensusRow row = row(x, z);
                    if (row != null) {
                        ordered.add(row);
                    }
                }
            }
            return List.copyOf(ordered);
        }

        public int rowCount() {
            return rows.size();
        }

        public List<String> failures() {
            synchronized (failures) {
                return List.copyOf(failures);
            }
        }

        private boolean requested(int chunkX, int chunkZ) {
            return chunkX >= chunkMinX
                    && chunkX < chunkMinX + chunkSpan
                    && chunkZ >= chunkMinZ
                    && chunkZ < chunkMinZ + chunkSpan;
        }

        private static String coordinate(int chunkX, int chunkZ) {
            return chunkX + "," + chunkZ;
        }
    }

    /** Stable audit-only seed; production continues to use the feature context's random source. */
    public static long driverCensusRngKey(
            String schema,
            long worldSeed,
            int chunkX,
            int chunkZ) {
        byte[] schemaBytes = java.util.Objects.requireNonNull(schema)
                .getBytes(StandardCharsets.UTF_8);
        long hash = 0xcbf29ce484222325L;
        for (byte value : schemaBytes) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        hash = mixRngKey(hash, worldSeed);
        hash = mixRngKey(hash, chunkX);
        hash = mixRngKey(hash, chunkZ);
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        return hash ^ (hash >>> 33);
    }

    public static String serializeRngKey(long rngKey) {
        return String.format(Locale.ROOT, "%016x", rngKey);
    }

    private static long mixRngKey(long hash, long value) {
        long mixed = hash;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    private record MobNavigationAssay(
            String status,
            String sceneAnchor,
            String start,
            String target,
            int pathNodeCount,
            String entranceHit,
            boolean pathSelected,
            boolean descentObserved,
            int ticksObserved,
            String finalPosition,
            String detail) {

        static MobNavigationAssay notRun(String detail) {
            return new MobNavigationAssay(
                    "NOT_RUN", null, null, null, 0, null,
                    false, false, 0, null, detail);
        }
    }

    private enum AuthoredBlockEntityPolicy {
        NONE,
        CHEST,
        COLLAPSED_EXPLORER_FOOT,
        COLLAPSED_EXPLORER_HEAD
    }

    private static final class Run {
        private final MinecraftServer server;
        private final ServerLevel world;
        private final Config config;
        /** Chunk workers may emit callbacks concurrently. */
        private final List<CaveDropTrapFeature.AuditEvent> scenes =
                Collections.synchronizedList(new ArrayList<>());
        private final List<String> failures = new ArrayList<>();
        private final Map<String, Long> immediateFingerprints = new HashMap<>();
        /** Production callbacks are telemetry only and cannot satisfy driver-owned coverage. */
        private final Map<Long, Long> productionCensusInvocations =
                new ConcurrentHashMap<>();
        private final DriverCensusLedger driverCensus;
        private List<CaveDropTrapFeature.AuditEvent> stableScenes = List.of();
        private List<JsonObject> sceneReports = List.of();
        private MobNavigationAssay mobNavigationAssay =
                MobNavigationAssay.notRun("audit has not reached navigation assay");
        private Mob assayMob;
        private BlockPos assayEntrance;
        private BlockPos assayTarget;
        private int assayRepathCooldown;
        private final List<net.minecraft.world.level.ChunkPos> assayForcedChunks =
                new ArrayList<>();
        private int mobTicksRemaining;
        private boolean auditPrepared;
        private int pendingTicks;

        private Run(MinecraftServer server, Config config) {
            this.server = server;
            this.world = server.overworld();
            this.config = config;
            if (world == null) {
                throw new IllegalStateException("no overworld");
            }
            driverCensus = new DriverCensusLedger(
                    config.chunkMinX(),
                    config.chunkMinZ(),
                    config.chunkSpan(),
                    CENSUS_SCHEMA,
                    world.getSeed());
        }

        private void capture(CaveDropTrapFeature.AuditEvent event) {
            int cx = Math.floorDiv(event.canonicalAnchor().getX(), 16);
            int cz = Math.floorDiv(event.canonicalAnchor().getZ(), 16);
            if (cx >= config.chunkMinX() && cx < config.chunkMinX() + config.chunkSpan()
                    && cz >= config.chunkMinZ() && cz < config.chunkMinZ() + config.chunkSpan()) {
                scenes.add(event);
            }
        }

        private void captureProductionCensusTelemetry(
                CaveDropTrapFeature.PassACensusSnapshot snapshot) {
            int cx = snapshot.chunkX();
            int cz = snapshot.chunkZ();
            productionCensusInvocations.merge(columnKey(cx, cz), 1L, Long::sum);
        }

        private int censusChunkCount() {
            return config.chunkSpan() * config.chunkSpan();
        }

        private CaveDropTrapFeature.PassACensusSnapshot censusTotals() {
            CaveDropTrapFeature.PassACensusSnapshot total =
                    CaveDropTrapFeature.PassACensusSnapshot.empty(0, 0);
            for (DriverCensusRow row : driverCensus.rowsInWindowOrder()) {
                total = total.plus(row.snapshot());
            }
            return total;
        }

        private void generateFullChunks() {
            Set<Long> targets = new LinkedHashSet<>();
            for (int z = config.chunkMinZ() - 1;
                    z <= config.chunkMinZ() + config.chunkSpan(); z++) {
                for (int x = config.chunkMinX() - 1;
                        x <= config.chunkMinX() + config.chunkSpan(); x++) {
                    targets.add(columnKey(x, z));
                }
            }
            for (long key : targets) {
                int x = unpackX(key);
                int z = unpackZ(key);
                ChunkAccess chunk =
                        world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, true);
                if (chunk == null) {
                    if (config.census() && requestedCensusCoordinate(x, z)) {
                        driverCensus.recordUnavailableChunk(x, z);
                    } else if (!config.census()) {
                        failures.add("full chunk unavailable " + x + "," + z);
                    }
                    continue;
                }
                if (config.census() && requestedCensusCoordinate(x, z)) {
                    driverCensus.scanRequestedChunk(
                            x,
                            z,
                            random -> CaveDropTrapFeature.scanPassAChunk(
                                    world, x, z, random));
                }
            }
        }

        private boolean requestedCensusCoordinate(int chunkX, int chunkZ) {
            return chunkX >= config.chunkMinX()
                    && chunkX < config.chunkMinX() + config.chunkSpan()
                    && chunkZ >= config.chunkMinZ()
                    && chunkZ < config.chunkMinZ() + config.chunkSpan();
        }

        private void loadBaseline() throws IOException {
            JsonObject root = JsonParser.parseString(
                    Files.readString(config.baseline(), StandardCharsets.UTF_8)).getAsJsonObject();
            requireCurrentSchema(root);
            validateBaselineRoot(root);
            JsonArray rows = root.getAsJsonArray("scenes");
            Set<String> anchors = new HashSet<>();
            for (JsonElement row : rows) {
                JsonObject scene = row.getAsJsonObject();
                require(
                        scene,
                        "immediateFingerprint",
                        "settledFingerprint",
                        "assertionFailures",
                        "verdict");
                if (!"PASS".equals(scene.get("verdict").getAsString())
                        || !scene.getAsJsonArray("assertionFailures").isEmpty()) {
                    throw new IOException("baseline contains a scene without a clean PASS");
                }
                if (!scene.get("immediateFingerprint").getAsString()
                        .equals(scene.get("settledFingerprint").getAsString())) {
                    throw new IOException("baseline contains an unsettled scene fingerprint");
                }
                CaveDropTrapFeature.AuditEvent event = readScene(scene);
                if (!anchors.add(key(event))) {
                    throw new IOException("baseline contains duplicate anchor " + key(event));
                }
                scenes.add(event);
            }
            if (scenes.isEmpty()) {
                failures.add("baseline contained no scenes");
            }
            validateBaselineCounts(root);
        }

        private void validateBaselineRoot(JsonObject root) throws IOException {
            require(
                    root,
                    "schema",
                    "mode",
                    "worldSeed",
                    "chunkMinX",
                    "chunkMinZ",
                    "chunkSpan",
                    "afterTicks",
                    "requiredMinimums",
                    "counts",
                    "mobNavigationAssay",
                    "verdict",
                    "assertionFailures",
                    "scenes");
            if (!"PASS".equals(root.get("verdict").getAsString())
                    || !root.getAsJsonArray("assertionFailures").isEmpty()) {
                throw new IOException("baseline verdict is not a clean PASS");
            }
            if (root.get("worldSeed").getAsLong() != world.getSeed()) {
                throw new IOException("baseline world seed differs from loaded world");
            }
            if (root.get("chunkMinX").getAsInt() != config.chunkMinX()
                    || root.get("chunkMinZ").getAsInt() != config.chunkMinZ()
                    || root.get("chunkSpan").getAsInt() != config.chunkSpan()) {
                throw new IOException("baseline chunk window differs from rescan configuration");
            }
            if (root.get("afterTicks").getAsInt() != config.afterTicks()) {
                throw new IOException("baseline settling window differs from rescan configuration");
            }
            JsonObject minima = root.getAsJsonObject("requiredMinimums");
            require(minima, "ordinary", "flooded", "prospector");
            if (minima.get("ordinary").getAsInt() != config.minOrdinary()
                    || minima.get("flooded").getAsInt() != config.minFlooded()
                    || minima.get("prospector").getAsInt() != config.minProspector()) {
                throw new IOException("baseline required minima differ from rescan configuration");
            }
            JsonObject counts = root.getAsJsonObject("counts");
            require(counts, "total", "ordinary", "flooded", "prospector");
            JsonObject assay = root.getAsJsonObject("mobNavigationAssay");
            require(
                    assay,
                    "status",
                    "sceneAnchor",
                    "start",
                    "target",
                    "pathNodeCount",
                    "entranceHit",
                    "pathSelected",
                    "descentObserved",
                    "ticksObserved",
                    "finalPosition",
                    "detail");
            if (!"PASS".equals(assay.get("status").getAsString())
                    || !assay.get("pathSelected").getAsBoolean()
                    || !assay.get("descentObserved").getAsBoolean()
                    || assay.get("pathNodeCount").getAsInt() <= 0
                    || assay.get("ticksObserved").getAsInt() <= 0) {
                throw new IOException("baseline mob navigation assay lacks a proven descent");
            }
        }

        private void validateBaselineCounts(JsonObject root) throws IOException {
            List<CaveDropTrapFeature.AuditEvent> baselineScenes = snapshotScenes();
            JsonObject counts = root.getAsJsonObject("counts");
            int ordinary = (int) baselineScenes.stream()
                    .filter(event -> event.resolvedKind() == CaveDropTrap.RewardKind.ORDINARY)
                    .count();
            int flooded = (int) baselineScenes.stream()
                    .filter(event ->
                            event.resolvedKind() == CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY)
                    .count();
            int prospector = (int) baselineScenes.stream()
                    .filter(event ->
                            event.resolvedKind() == CaveDropTrap.RewardKind.LOST_PROSPECTOR)
                    .count();
            if (counts.get("total").getAsInt() != baselineScenes.size()
                    || counts.get("ordinary").getAsInt() != ordinary
                    || counts.get("flooded").getAsInt() != flooded
                    || counts.get("prospector").getAsInt() != prospector) {
                throw new IOException("baseline counts do not match its serialized scenes");
            }
            String assayAnchor = root.getAsJsonObject("mobNavigationAssay")
                    .get("sceneAnchor").getAsString();
            if (baselineScenes.stream().noneMatch(event -> key(event).equals(assayAnchor))) {
                throw new IOException("baseline mob navigation assay names an unknown scene");
            }
        }

        private void loadBaselineChunks() {
            Set<Long> chunks = new LinkedHashSet<>();
            for (CaveDropTrapFeature.AuditEvent event : snapshotScenes()) {
                chunks.add(columnKey(
                        event.canonicalAnchor().getX() >> 4,
                        event.canonicalAnchor().getZ() >> 4));
            }
            for (long key : chunks) {
                int x = unpackX(key);
                int z = unpackZ(key);
                if (world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, true) == null) {
                    failures.add("baseline full chunk unavailable " + x + "," + z);
                }
            }
        }

        private void captureImmediateFingerprints() {
            for (CaveDropTrapFeature.AuditEvent event : snapshotScenes()) {
                immediateFingerprints.put(key(event), actualEvidenceFingerprint(world, event));
            }
        }

        private void prepareAudit() throws IOException {
            stableScenes = new ArrayList<>(snapshotScenes());
            stableScenes.sort(EVENT_ORDER);
            Map<String, Long> baseline = config.generate()
                    ? Map.of()
                    : baselineFingerprints(config.baseline());
            List<JsonObject> reports = new ArrayList<>();
            for (CaveDropTrapFeature.AuditEvent event : stableScenes) {
                reports.add(validate(event, baseline));
            }
            sceneReports = List.copyOf(reports);
            verifyMinimums();
            auditPrepared = true;
        }

        private boolean writeReport() throws IOException {
            if (!"PASS".equals(mobNavigationAssay.status())) {
                failures.add("mob navigation assay " + mobNavigationAssay.status()
                        + ": " + mobNavigationAssay.detail());
            }

            JsonObject report = new JsonObject();
            report.addProperty("schema", SCHEMA);
            report.addProperty("mode", config.mode());
            report.addProperty("worldSeed", world.getSeed());
            report.addProperty("chunkMinX", config.chunkMinX());
            report.addProperty("chunkMinZ", config.chunkMinZ());
            report.addProperty("chunkSpan", config.chunkSpan());
            report.addProperty("afterTicks", config.afterTicks());
            JsonObject minima = new JsonObject();
            minima.addProperty("ordinary", config.minOrdinary());
            minima.addProperty("flooded", config.minFlooded());
            minima.addProperty("prospector", config.minProspector());
            report.add("requiredMinimums", minima);

            JsonObject counts = new JsonObject();
            counts.addProperty("total", stableScenes.size());
            counts.addProperty("ordinary", count(CaveDropTrap.RewardKind.ORDINARY));
            counts.addProperty(
                    "flooded", count(CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY));
            counts.addProperty(
                    "prospector", count(CaveDropTrap.RewardKind.LOST_PROSPECTOR));
            report.add("counts", counts);
            report.add("mobNavigationAssay", mobNavigationJson(mobNavigationAssay));
            report.addProperty("verdict", failures.isEmpty() ? "PASS" : "FAIL");
            JsonArray allFailures = new JsonArray();
            failures.forEach(allFailures::add);
            report.add("assertionFailures", allFailures);
            JsonArray rows = new JsonArray();
            sceneReports.forEach(rows::add);
            report.add("scenes", rows);

            Path parent = config.out().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(config.out(), JSON.toJson(report) + "\n", StandardCharsets.UTF_8);
            return failures.isEmpty();
        }

        private boolean writeCensusReport() throws IOException {
            List<String> censusFailures = new ArrayList<>(failures);
            censusFailures.addAll(driverCensus.failures());
            CaveDropTrapFeature.PassACensusSnapshot total = censusTotals();
            JsonArray chunks = new JsonArray();
            JsonArray missingCoordinates = new JsonArray();
            Set<String> observedCoordinates = driverCensus.rowsInWindowOrder().stream()
                    .map(row -> row.snapshot().chunkX() + "," + row.snapshot().chunkZ())
                    .collect(java.util.stream.Collectors.toSet());
            List<String> missingCoordinateList = missingCensusCoordinates(
                    config.chunkMinX(), config.chunkMinZ(),
                    config.chunkSpan(), observedCoordinates);
            missingCoordinateList.forEach(missingCoordinates::add);
            missingCoordinateList.forEach(coordinate ->
                    censusFailures.add("missing chunk census row " + coordinate));
            boolean everyObservedRowReconciles = true;
            int observedRows = 0;
            for (int z = config.chunkMinZ(); z < config.chunkMinZ() + config.chunkSpan(); z++) {
                for (int x = config.chunkMinX(); x < config.chunkMinX() + config.chunkSpan(); x++) {
                    DriverCensusRow driverRow = driverCensus.row(x, z);
                    CaveDropTrapFeature.PassACensusSnapshot row =
                            driverRow == null ? null : driverRow.snapshot();
                    if (row == null) {
                        JsonObject missing = new JsonObject();
                        missing.addProperty("chunkX", x);
                        missing.addProperty("chunkZ", z);
                        missing.addProperty("missing", true);
                        chunks.add(missing);
                        continue;
                    }
                    observedRows++;
                    if (!row.reconciles()) {
                        everyObservedRowReconciles = false;
                        censusFailures.add("chunk stage counters do not reconcile " + x + "," + z);
                    }
                    chunks.add(passACensusJson(row, driverRow.rngKey()));
                }
            }
            if (!total.reconciles()) {
                censusFailures.add("aggregate stage counters do not reconcile");
            }
            if (total.scanSlots() <= 0) {
                censusFailures.add("no scan slots observed");
            }
            if (total.resolvedOrdinary() < config.minOrdinary()) {
                censusFailures.add("resolved ordinary "
                        + total.resolvedOrdinary() + " below minimum " + config.minOrdinary());
            }
            if (total.resolvedFlooded() < config.minFlooded()) {
                censusFailures.add("resolved flooded "
                        + total.resolvedFlooded() + " below minimum " + config.minFlooded());
            }
            if (total.resolvedProspector() < config.minProspector()) {
                censusFailures.add("resolved prospector "
                        + total.resolvedProspector() + " below minimum " + config.minProspector());
            }
            CensusReportGate gate = evaluateCensusReportGate(
                    censusChunkCount(), observedRows,
                    everyObservedRowReconciles, total.reconciles(), total.scanSlots(),
                    total.resolvedOrdinary(), total.resolvedFlooded(),
                    total.resolvedProspector(),
                    config.minOrdinary(), config.minFlooded(), config.minProspector());

            JsonObject report = new JsonObject();
            report.addProperty("schema", CENSUS_SCHEMA);
            report.addProperty("mode", config.mode());
            report.addProperty("worldSeed", world.getSeed());
            report.addProperty("chunkMinX", config.chunkMinX());
            report.addProperty("chunkMinZ", config.chunkMinZ());
            report.addProperty("chunkSpan", config.chunkSpan());
            JsonObject requestedWindow = new JsonObject();
            requestedWindow.addProperty("chunkMinX", config.chunkMinX());
            requestedWindow.addProperty("chunkMinZ", config.chunkMinZ());
            requestedWindow.addProperty("chunkSpan", config.chunkSpan());
            requestedWindow.addProperty("requestedRows", censusChunkCount());
            report.add("requestedWindow", requestedWindow);
            JsonObject requestedMinima = new JsonObject();
            requestedMinima.addProperty("ordinary", config.minOrdinary());
            requestedMinima.addProperty("flooded", config.minFlooded());
            requestedMinima.addProperty("prospector", config.minProspector());
            report.add("requestedMinimums", requestedMinima);
            report.addProperty("observedRows", observedRows);
            report.add("missingCoordinates", missingCoordinates);
            report.add("aggregate", passACensusJson(total));
            report.add("chunks", chunks);
            JsonObject productionTelemetry = new JsonObject();
            long invocationCount = productionCensusInvocations.values().stream()
                    .mapToLong(Long::longValue)
                    .sum();
            productionTelemetry.addProperty("invocations", invocationCount);
            productionTelemetry.addProperty(
                    "distinctChunks", productionCensusInvocations.size());
            productionTelemetry.addProperty("satisfiesCoverage", false);
            report.add("productionInvocationTelemetry", productionTelemetry);
            JsonObject reconciliation = new JsonObject();
            reconciliation.addProperty("aggregatePass", total.reconciles());
            reconciliation.addProperty(
                    "allObservedRowsPass", everyObservedRowReconciles);
            reconciliation.addProperty(
                    "allChunkRowsPresent", observedRows == censusChunkCount());
            reconciliation.addProperty("scanSlotsObserved", total.scanSlots() > 0);
            reconciliation.addProperty(
                    "auditDomainSlotsObserved", total.auditDomainSlots() > 0);
            reconciliation.addProperty(
                    "uniqueOwnerAnchorsObserved", total.uniqueOwnerAnchors() > 0);
            reconciliation.addProperty(
                    "diagnosticReconciliationPass",
                    gate.diagnosticReconciliationPass());
            reconciliation.addProperty("outcomeGatePass", gate.outcomeGatePass());
            report.add("reconciliation", reconciliation);
            report.addProperty("verdict", gate.pass() && censusFailures.isEmpty()
                    ? "PASS" : "FAIL");
            JsonArray assertions = new JsonArray();
            censusFailures.forEach(assertions::add);
            report.add("assertionFailures", assertions);

            Path parent = config.out().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(config.out(), JSON.toJson(report) + "\n", StandardCharsets.UTF_8);
            return gate.pass() && censusFailures.isEmpty();
        }

        private JsonObject validate(
                CaveDropTrapFeature.AuditEvent event, Map<String, Long> baseline) {
            List<String> local = new ArrayList<>();
            requireRichEvidence(event, local);
            String rewardError = rewardMappingError(event);
            check(rewardError == null, local, key(event) + " " + rewardError);
            validateOwnerChunk(event, local);
            validateAtomicReadback(event, local);
            validateCarpetAndBanks(event, local);
            validateNaturalRoutes(event, local);
            validateEntries(event, local);
            validateRouteProvenance(event, local);
            if (event.resolvedKind() == CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
                validateFlooded(event, local);
            } else if (event.resolvedKind() == CaveDropTrap.RewardKind.LOST_PROSPECTOR) {
                validateProspector(event, local);
            } else {
                validateOrdinary(event, local);
            }

            long settledFingerprint = actualEvidenceFingerprint(world, event);
            Long immediate = immediateFingerprints.get(key(event));
            check(
                    immediate != null && immediate == settledFingerprint,
                    local,
                    key(event) + " authored cells changed between immediate and settled reads");
            if (!config.generate()) {
                Long prior = baseline.get(key(event));
                check(
                        prior != null && prior == settledFingerprint,
                        local,
                        key(event) + " authored-cell fingerprint changed after restart");
            }

            failures.addAll(local);
            JsonObject row = sceneJson(event);
            row.addProperty("immediateFingerprint", unsignedHex(immediate));
            row.addProperty("settledFingerprint", unsignedHex(settledFingerprint));
            JsonArray sceneFailures = new JsonArray();
            local.forEach(sceneFailures::add);
            row.add("assertionFailures", sceneFailures);
            row.addProperty("verdict", local.isEmpty() ? "PASS" : "FAIL");
            return row;
        }

        private void requireRichEvidence(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            check(event.approachAxis() != null, local, key(event) + " missing approach axis");
            check(!event.firstApproachBank().isEmpty(), local, key(event) + " missing first bank");
            check(!event.secondApproachBank().isEmpty(), local, key(event) + " missing second bank");
            check(
                    !event.upperBookendPositions().isEmpty(),
                    local,
                    key(event) + " missing upper bookend evidence");
            check(
                    event.entries().size() == event.powderPositions().size(),
                    local,
                    key(event) + " entry evidence does not partition powder entrances");
            for (EntryEvidence entry : event.entries()) {
                check(
                        !entry.shaftPositions().isEmpty(),
                        local,
                        key(event) + " entry lacks an attributed authored shaft at "
                                + pos(entry.entranceTop()));
            }
            check(!event.authoredPositions().isEmpty(), local, key(event) + " missing typed writes");
            check(event.postWriteVerified(), local, key(event) + " producer post-write verification false");
        }

        private void validateOwnerChunk(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            int ownerX = Math.floorDiv(event.canonicalAnchor().getX(), 16);
            int ownerZ = Math.floorDiv(event.canonicalAnchor().getZ(), 16);
            for (BlockPos position : allEvidencePositions(event)) {
                check(
                        Math.floorDiv(position.getX(), 16) == ownerX
                                && Math.floorDiv(position.getZ(), 16) == ownerZ,
                        local,
                        key(event) + " evidence crosses owner chunk: " + pos(position));
            }
        }

        private void validateAtomicReadback(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            check(
                    event.expectedFingerprint() == event.observedFingerprint(),
                    local,
                    key(event) + " producer expected/observed fingerprints differ");
            long actual = actualAuthoredFingerprint(world, event);
            check(
                    actual == event.expectedFingerprint(),
                    local,
                    key(event) + " current authored fingerprint differs from expected");
            Set<BlockPos> attributed = new HashSet<>();
            for (AuthoredPosition authored : event.authoredPositions()) {
                check(
                        attributed.add(authored.position()),
                        local,
                        key(event) + " duplicate attributed write at " + pos(authored.position()));
                BlockState actualState = world.getBlockState(authored.position());
                check(
                        authored.expected().equals(authored.observed()),
                        local,
                        key(event) + " producer readback differs at " + pos(authored.position()));
                check(
                        actualState.equals(authored.expected()),
                        local,
                        key(event) + " actual state differs at " + pos(authored.position())
                                + " role=" + authored.role());
                validateRole(authored, actualState, local, event);
                check(
                        authored.priorBlockEntityAbsent(),
                        local,
                        key(event) + " attributed " + authored.role()
                                + " replaced a block entity at " + pos(authored.position()));
                check(
                        certifiedNaturalSolid(authored.priorState()),
                        local,
                        key(event) + " attributed " + authored.role()
                                + " did not replace certified safe-natural solid at "
                                + pos(authored.position())
                                + " prior=" + blockId(authored.priorState()));
                validateAuthoredBlockEntity(authored, local, event);
                check(
                        !actualState.is(Blocks.LAVA)
                                && !actualState.is(Blocks.MAGMA_BLOCK)
                                && !actualState.is(Blocks.BEDROCK),
                        local,
                        key(event) + " attributed hazard at " + pos(authored.position()));
            }
        }

        private void validateAuthoredBlockEntity(
                AuthoredPosition authored,
                List<String> local,
                CaveDropTrapFeature.AuditEvent event) {
            BlockEntity blockEntity = world.getBlockEntity(authored.position());
            AuthoredBlockEntityPolicy policy = authoredBlockEntityPolicy(authored.role());
            check(
                    authoredBlockEntityMatches(policy, blockEntity),
                    local,
                    key(event) + " attributed " + authored.role()
                            + " violates exact block-entity policy " + policy + " at "
                            + pos(authored.position()));
        }

        private void validateRole(
                AuthoredPosition authored,
                BlockState actual,
                List<String> local,
                CaveDropTrapFeature.AuditEvent event) {
            boolean roleMatch = switch (authored.role()) {
                case ENTRANCE_POWDER ->
                        CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW != null
                                && actual.is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW);
                case FIRM_CAMOUFLAGE -> actual.is(Blocks.SNOW_BLOCK);
                case OPENED_SHAFT, GALLERY_AIR -> actual.isAir();
                case POWDER_CUSHION -> actual.is(Blocks.POWDER_SNOW);
                case GALLERY_WATER ->
                        actual.getFluidState().is(Fluids.WATER)
                                && actual.getFluidState().isSource();
                case ORE_REWARD -> isOre(actual);
                case TREASURE_CHEST -> actual.is(Blocks.CHEST);
                case COLLAPSED_EXPLORER_FOOT ->
                        collapsedExplorerState(actual, BedPart.FOOT);
                case COLLAPSED_EXPLORER_HEAD ->
                        collapsedExplorerState(actual, BedPart.HEAD);
            };
            check(
                    roleMatch,
                    local,
                    key(event) + " role/state mismatch " + authored.role()
                            + " at " + pos(authored.position()) + " actual=" + blockId(actual));
        }

        private void validateCarpetAndBanks(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            List<BlockPos> powder = event.powderPositions();
            List<BlockPos> firm = event.firmCamouflagePositions();
            List<BlockPos> bookends = event.upperBookendPositions();
            Map<BlockPos, AuthoredPosition> authored = authoredByPosition(event);
            check(
                    powder.size() >= CaveDropTrap.MIN_PATCH_AREA
                            && powder.size() <= CaveDropTrap.PATCH_MAX_AREA,
                    local,
                    key(event) + " powder area outside production bounds");
            check(connectedColumns(powder), local, key(event) + " powder footprint is not connected");
            check(irregular(powder), local, key(event) + " powder footprint is rectangular");
            check(
                    new HashSet<>(firm).equals(new HashSet<>(event.regularPositions())),
                    local,
                    key(event) + " firm camouflage differs from regular collar");
            int requiredFirm = Math.max(
                    CaveDropTrap.MIN_FIRM_CAMOUFLAGE_CELLS, (powder.size() + 1) / 2);
            check(
                    firm.size() >= requiredFirm,
                    local,
                    key(event) + " firm/powder mix below production law");
            Set<BlockPos> expectedBookends = new LinkedHashSet<>(event.firstApproachBank());
            expectedBookends.addAll(event.secondApproachBank());
            check(
                    bookends.size() == new HashSet<>(bookends).size(),
                    local,
                    key(event) + " upper bookend evidence contains duplicates");
            check(
                    new LinkedHashSet<>(bookends).equals(expectedBookends),
                    local,
                    key(event) + " upper bookends do not exactly cover both approach banks");

            Set<Long> powderColumns = columns(powder);
            for (BlockPos top : powder) {
                check(
                        CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW != null
                                && world.getBlockState(top)
                                        .is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW),
                        local,
                        key(event) + " entrance is not custom trap powder at " + pos(top));
                int lx = Math.floorMod(top.getX(), 16);
                int lz = Math.floorMod(top.getZ(), 16);
                check(
                        lx > 0 && lx < 15 && lz > 0 && lz < 15,
                        local,
                        key(event) + " powder touches owner edge at " + pos(top));
            }
            for (BlockPos floor : firm) {
                check(
                        world.getBlockState(floor).is(Blocks.SNOW_BLOCK),
                        local,
                        key(event) + " firm camouflage is not snow block at " + pos(floor));
                check(
                        certifiedNaturalMassBelow(
                                floor, MIN_STABLE_SUPPORT_DEPTH, authored),
                        local,
                        key(event) + " firm camouflage lacks unmodified certified mass at "
                                + pos(floor));
                check(
                        twoAirAbove(floor),
                        local,
                        key(event) + " firm camouflage is not walkable at " + pos(floor));
                check(
                        !powderColumns.contains(columnKey(floor.getX(), floor.getZ())),
                        local,
                        key(event) + " firm cell overlaps a fall column at " + pos(floor));
            }
            Set<BlockPos> firmSet = new HashSet<>(firm);
            for (BlockPos floor : bookends) {
                check(
                        firmSet.contains(floor),
                        local,
                        key(event) + " upper bookend is not firm camouflage at " + pos(floor));
                check(
                        certifiedNaturalMassBelow(
                                floor, MIN_STABLE_SUPPORT_DEPTH, authored),
                        local,
                        key(event) + " upper bookend lacks unmodified certified mass at "
                                + pos(floor));
                check(
                        twoAirAbove(floor),
                        local,
                        key(event) + " upper bookend is not walkable at " + pos(floor));
                check(
                        !powderColumns.contains(columnKey(floor.getX(), floor.getZ())),
                        local,
                        key(event) + " upper bookend overlaps a fall column at " + pos(floor));
            }
            validateBank(
                    event, event.firstApproachBank(), true, powderColumns, firm, authored, local);
            validateBank(
                    event, event.secondApproachBank(), false, powderColumns, firm, authored, local);
            // End-cap law: the two firm banks stand at opposite ends of the carpet with the
            // powder cut between them -- they may never touch or share cells, and no firm
            // cell may exist outside them (a third-party firm cell could bridge the cut).
            Set<BlockPos> firstBankSet = new HashSet<>(event.firstApproachBank());
            Set<BlockPos> secondBankSet = new HashSet<>(event.secondApproachBank());
            check(
                    java.util.Collections.disjoint(firstBankSet, secondBankSet)
                            && event.firstApproachBank().stream().noneMatch(one ->
                                    event.secondApproachBank().stream().anyMatch(two ->
                                            cardinalAdjacent(one, two))),
                    local,
                    key(event) + " opposed end banks touch: no powder separates them");
            check(
                    event.firmCamouflagePositions().stream().allMatch(floor ->
                            firstBankSet.contains(floor) || secondBankSet.contains(floor)),
                    local,
                    key(event) + " firm cell outside both end banks could bridge the powder cut");
        }

        private void validateBank(
                CaveDropTrapFeature.AuditEvent event,
                List<BlockPos> bank,
                boolean first,
                Set<Long> powderColumns,
                List<BlockPos> firm,
                Map<BlockPos, AuthoredPosition> authored,
                List<String> local) {
            check(
                    bank.size() >= CaveDropTrap.MIN_APPROACH_BANK_RUN,
                    local,
                    key(event) + " approach bank shorter than production minimum");
            check(connectedColumns(bank), local, key(event) + " approach bank is discontinuous");
            Set<BlockPos> firmSet = new HashSet<>(firm);
            for (BlockPos floor : bank) {
                check(
                        firmSet.contains(floor),
                        local,
                        key(event) + " bank cell is not in firm collar: " + pos(floor));
                check(
                        certifiedNaturalMassBelow(
                                        floor, MIN_STABLE_SUPPORT_DEPTH, authored)
                                && twoAirAbove(floor),
                        local,
                        key(event) + " bank cell lacks unmodified certified mass/walkability: "
                                + pos(floor));
            }
            // The conformal banks are end caps, not flanking rails: the bank as a whole must
            // border the powder carpet (any direction), and the frozen pre-write topology law
            // already proved the powder is a vertex cut between the two mouths.
            check(
                    bank.stream().anyMatch(floor ->
                            powderColumns.contains(columnKey(floor.getX() + 1, floor.getZ()))
                                    || powderColumns.contains(
                                            columnKey(floor.getX() - 1, floor.getZ()))
                                    || powderColumns.contains(
                                            columnKey(floor.getX(), floor.getZ() + 1))
                                    || powderColumns.contains(
                                            columnKey(floor.getX(), floor.getZ() - 1))),
                    local,
                    key(event) + " approach bank never borders the powder carpet");
        }

        private void validateNaturalRoutes(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            Map<BlockPos, AuthoredPosition> authored = authoredByPosition(event);
            validateNaturalContinuation(
                    event,
                    event.firstNaturalContinuation(),
                    event.firstApproachBank(),
                    event.secondApproachBank(),
                    true,
                    authored,
                    local);
            validateNaturalContinuation(
                    event,
                    event.secondNaturalContinuation(),
                    event.secondApproachBank(),
                    event.firstApproachBank(),
                    false,
                    authored,
                    local);

            List<BlockPos> upper = event.upperNaturalReturn();
            check(!upper.isEmpty()
                            && upper.size() == new HashSet<>(upper).size()
                            && connectedColumns(upper),
                    local,
                    key(event) + " upper natural return is empty, duplicate, or disconnected");
            Set<BlockPos> continuations = new HashSet<>(event.firstNaturalContinuation());
            continuations.addAll(event.secondNaturalContinuation());
            Set<BlockPos> powder = new HashSet<>(event.powderPositions());
            Set<BlockPos> firm = new HashSet<>(event.firmCamouflagePositions());
            Set<BlockPos> authoredPositions = authored.keySet();
            for (int index = 0; index < upper.size(); index++) {
                BlockPos floor = upper.get(index);
                check(!authoredPositions.contains(floor)
                                && !powder.contains(floor)
                                && !firm.contains(floor),
                        local,
                        key(event) + " upper natural return overlaps authored trap terrain at "
                                + pos(floor));
                check(!continuations.contains(floor) || index == upper.size() - 1, local,
                        key(event) + " upper natural return enters a continuation before its end");
                validateUntouchedNaturalFloor(event, floor, authored, local, "upper return");
            }
            check(!upper.isEmpty() && continuations.contains(upper.getLast()), local,
                    key(event) + " upper natural return does not end in a named continuation");

            if (event.resolvedKind() == CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
                check(!event.floodedExitPath().isEmpty(), local,
                        key(event) + " flooded scene lacks its explicit exit path");
                for (EntryEvidence entry : event.entries()) {
                    check(!event.floodedExitPath().isEmpty()
                                    && entry.dryExit() != null
                                    && entry.dryExit().equals(event.floodedExitPath().getLast()),
                            local,
                            key(event) + " flooded entry final exit differs from named exit path");
                }
                return;
            }

            List<BlockPos> naturalStanding =
                    upper.stream().map(BlockPos::above).toList();
            for (EntryEvidence entry : event.entries()) {
                List<BlockPos> route = entry.dryPath();
                check(endsWith(route, naturalStanding), local,
                        key(event) + " dry route lacks the exact upper-natural-return suffix");
                check(entry.dryExit() != null
                                && !upper.isEmpty()
                                && entry.dryExit().below().equals(upper.getLast()),
                        local,
                        key(event) + " final dry exit is not supported by the named natural return");
                int suffixStart = route.size() - naturalStanding.size();
                if (suffixStart > 0 && !naturalStanding.isEmpty()) {
                    BlockPos prior = route.get(suffixStart - 1);
                    AuthoredPosition priorWrite = authored.get(prior);
                    // The stair's top standing may be authored gallery air, or -- when the
                    // stair emerges into the passage's own pre-existing air -- an untouched
                    // node that is still genuinely walkable.
                    check(validWalkingStep(prior, naturalStanding.getFirst())
                                    && (priorWrite != null
                                            ? priorWrite.role() == AuthoredRole.GALLERY_AIR
                                            : dryWalkableStanding(prior)),
                            local,
                            key(event) + " upper return does not start at the stair top");
                }
            }
            if (event.resolvedKind() == CaveDropTrap.RewardKind.LOST_PROSPECTOR) {
                check(endsWith(event.chestToExitPath(), naturalStanding), local,
                        key(event) + " chest-to-exit route lacks the natural-return suffix");
            }
        }

        private void validateNaturalContinuation(
                CaveDropTrapFeature.AuditEvent event,
                List<BlockPos> continuation,
                List<BlockPos> namedBank,
                List<BlockPos> oppositeBank,
                boolean first,
                Map<BlockPos, AuthoredPosition> authored,
                List<String> local) {
            String label = first ? "first" : "second";
            check(continuation.size() == CaveDropTrap.MIN_APPROACH_CONTINUATION_AREA
                            && continuation.size() == new HashSet<>(continuation).size()
                            && connectedColumns(continuation),
                    local,
                    key(event) + " " + label
                            + " natural continuation is not exactly four unique connected cells");
            Set<BlockPos> bank = new HashSet<>(namedBank);
            Set<BlockPos> other = new HashSet<>(oppositeBank);
            Set<BlockPos> firm = new HashSet<>(event.firmCamouflagePositions());
            Set<BlockPos> powder = new HashSet<>(event.powderPositions());
            Direction outward = outwardDirection(event.approachAxis(), first);
            boolean touchesNamed = false;
            for (BlockPos floor : continuation) {
                boolean namedAdjacent = namedBank.stream()
                        .anyMatch(bankFloor -> cardinalAdjacent(bankFloor, floor));
                touchesNamed |= namedAdjacent;
                check(other.stream().noneMatch(bankFloor -> cardinalAdjacent(bankFloor, floor)),
                        local,
                        key(event) + " " + label
                                + " continuation touches the opposite bank");
                // Outward-ness, orientation-free: a continuation cell is the passage floor
                // beyond its end bank, so it may never sit cardinally against the powder cut.
                check(!bordersPowderColumn(floor, powder), local,
                        key(event) + " " + label
                                + " continuation touches the powder carpet at " + pos(floor));
                for (BlockPos firmFloor : firm) {
                    if (cardinalAdjacent(firmFloor, floor)) {
                        check(bank.contains(firmFloor), local,
                                key(event) + " " + label
                                        + " continuation reaches authored firm floor outside its bank");
                    }
                }
                check(!authored.containsKey(floor)
                                && !powder.contains(floor)
                                && !firm.contains(floor),
                        local,
                        key(event) + " " + label
                                + " continuation overlaps authored trap terrain at " + pos(floor));
                validateUntouchedNaturalFloor(
                        event, floor, authored, local, label + " continuation");
            }
            check(touchesNamed, local,
                    key(event) + " " + label + " continuation never reaches its named bank");
        }

        private void validateUntouchedNaturalFloor(
                CaveDropTrapFeature.AuditEvent event,
                BlockPos floor,
                Map<BlockPos, AuthoredPosition> authored,
                List<String> local,
                String label) {
            for (int offset = 0;
                    offset < CaveDropTrap.APPROACH_CONTINUATION_SUPPORT_DEPTH;
                    offset++) {
                BlockPos support = floor.below(offset);
                check(!authored.containsKey(support)
                                && world.getBlockEntity(support) == null
                                && certifiedNaturalSolid(world.getBlockState(support)),
                        local,
                        key(event) + " " + label
                                + " lacks untouched safe-natural support at " + pos(support));
            }
            for (int offset = 1; offset <= 2; offset++) {
                BlockPos headroom = floor.above(offset);
                check(!authored.containsKey(headroom)
                                && world.getBlockEntity(headroom) == null
                                && world.getBlockState(headroom).isAir(),
                        local,
                        key(event) + " " + label
                                + " lacks untouched two-air headroom at " + pos(headroom));
            }
        }

        private void validateEntries(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            Set<BlockPos> tops = new HashSet<>(event.powderPositions());
            Set<BlockPos> exits = new HashSet<>(event.dryExitPositions());
            Set<BlockPos> seenTops = new HashSet<>();
            Set<BlockPos> seenLandings = new HashSet<>();
            Set<BlockPos> evidencedShaft = new LinkedHashSet<>();
            Map<BlockPos, AuthoredPosition> authored = authoredByPosition(event);
            int expectedEntries = event.powderPositions().size();
            check(expectedEntries >= CaveDropTrap.MIN_PATCH_AREA
                            && tops.size() == expectedEntries,
                    local,
                    key(event) + " powder tops are duplicated or below the minimum carpet area");
            check(event.entries().size() == expectedEntries, local,
                    key(event) + " entry evidence does not cover every powder top");
            for (EntryEvidence entry : event.entries()) {
                check(tops.contains(entry.entranceTop()), local,
                        key(event) + " entry top is not a powder entrance");
                check(seenTops.add(entry.entranceTop()), local,
                        key(event) + " duplicate entry top " + pos(entry.entranceTop()));
                check(seenLandings.add(entry.landing()), local,
                        key(event) + " duplicate entry landing " + pos(entry.landing()));
                check(
                        entry.entranceTop().getX() == entry.landing().getX()
                                && entry.entranceTop().getZ() == entry.landing().getZ(),
                        local,
                        key(event) + " landing is not under entrance");
                int fall = entry.entranceTop().getY() - entry.landing().getY();
                check(
                        fall >= CaveDropTrap.MIN_DROP_AIR,
                        local,
                        key(event) + " entry fall is below the independent "
                                + CaveDropTrap.MIN_DROP_AIR + "-block minimum");
                check(
                        fall >= event.fallMin() && fall <= event.fallMax(),
                        local,
                        key(event) + " entry fall disagrees with recorded range");
                check(
                        exits.contains(entry.dryExit()),
                        local,
                        key(event) + " entry dry exit absent from scene exit set");
                validateEmbeddedShaft(event, entry, authored, evidencedShaft, local);
                if (event.resolvedKind()
                        != CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
                    validateDryEntry(event, entry, local);
                }
            }
            check(
                    seenTops.equals(tops),
                    local,
                    key(event) + " entry evidence does not cover every entrance exactly once");
            check(seenLandings.size() == expectedEntries, local,
                    key(event) + " entry evidence does not cover a unique landing per entrance");
            Set<BlockPos> authoredShaft = event.authoredPositions().stream()
                    .filter(position -> position.role() == AuthoredRole.OPENED_SHAFT)
                    .map(AuthoredPosition::position)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            check(
                    evidencedShaft.equals(authoredShaft),
                    local,
                    key(event) + " per-entry shaft union differs from all OPENED_SHAFT writes");

            if (event.resolvedKind() == CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
                for (BlockPos landing : seenLandings) {
                    for (int depth = 0; depth < 3; depth++) {
                        BlockPos water = landing.below(depth);
                        AuthoredPosition write = authored.get(water);
                        check(write != null
                                        && write.role() == AuthoredRole.GALLERY_WATER
                                        && sourceWater(water),
                                local,
                                key(event) + " flooded landing column lacks exact typed source water at "
                                        + pos(water));
                    }
                }
            } else {
                Set<BlockPos> cushionWrites = event.authoredPositions().stream()
                        .filter(position -> position.role() == AuthoredRole.POWDER_CUSHION)
                        .map(AuthoredPosition::position)
                        .collect(java.util.stream.Collectors.toSet());
                check(seenLandings.equals(cushionWrites), local,
                        key(event) + " dry landing set differs from exact POWDER_CUSHION writes");
            }
        }

        private void validateEmbeddedShaft(
                CaveDropTrapFeature.AuditEvent event,
                EntryEvidence entry,
                Map<BlockPos, AuthoredPosition> authored,
                Set<BlockPos> evidencedShaft,
                List<String> local) {
            List<BlockPos> expected = new ArrayList<>();
            for (int y = entry.entranceTop().getY() - 1;
                    y > entry.landing().getY(); y--) {
                expected.add(new BlockPos(
                        entry.entranceTop().getX(), y, entry.entranceTop().getZ()));
            }
            check(
                    entry.shaftPositions().equals(expected),
                    local,
                    key(event) + " shaft evidence is not the exact ordered top-1..landing+1 column at "
                            + pos(entry.entranceTop()));

            AuthoredPosition entrance = authored.get(entry.entranceTop());
            check(
                    entrance != null && entrance.role() == AuthoredRole.ENTRANCE_POWDER,
                    local,
                    key(event) + " entrance lacks its typed ENTRANCE_POWDER write at "
                            + pos(entry.entranceTop()));
            if (entrance != null) {
                check(
                        entrance.priorBlockEntityAbsent()
                                && certifiedNaturalSolid(entrance.priorState()),
                        local,
                        key(event) + " entrance was not formerly certified natural solid at "
                                + pos(entry.entranceTop()));
            }

            for (BlockPos shaft : entry.shaftPositions()) {
                check(
                        evidencedShaft.add(shaft),
                        local,
                        key(event) + " shaft cell is attributed to more than one entry: "
                                + pos(shaft));
                AuthoredPosition write = authored.get(shaft);
                check(
                        write != null && write.role() == AuthoredRole.OPENED_SHAFT,
                        local,
                        key(event) + " shaft cell lacks its typed OPENED_SHAFT write at "
                                + pos(shaft));
                if (write == null) {
                    continue;
                }
                check(
                        write.priorBlockEntityAbsent()
                                && certifiedNaturalSolid(write.priorState()),
                        local,
                        key(event) + " shaft reused non-solid/pre-existing void at " + pos(shaft));
                check(
                        world.getBlockState(shaft).isAir(),
                        local,
                        key(event) + " authored shaft is blocked at " + pos(shaft));
            }
        }

        private void validateRouteProvenance(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            Map<BlockPos, AuthoredPosition> authored = authoredByPosition(event);
            if (event.resolvedKind() == CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
                Set<BlockPos> explicitUntouched = new HashSet<>(event.galleryPositions());
                explicitUntouched.addAll(event.shorePositions());
                explicitUntouched.addAll(event.dryExitPositions());
                explicitUntouched.addAll(event.floodedExitPath());
                for (EntryEvidence entry : event.entries()) {
                    validateFloodedRouteProvenance(
                            event, entry.shorePath(), authored, explicitUntouched,
                            local, "entry flooded route");
                }
                validateFloodedRouteProvenance(
                        event, event.floodedExitPath(), authored, explicitUntouched,
                        local, "flooded exit route");
                return;
            }

            Set<BlockPos> naturalStanding = event.upperNaturalReturn().stream()
                    .map(BlockPos::above)
                    .collect(java.util.stream.Collectors.toSet());
            for (EntryEvidence entry : event.entries()) {
                validateDryRouteProvenance(
                        event, entry.dryPath(), authored, naturalStanding,
                        true, local, "entry dry route");
                if (event.resolvedKind() == CaveDropTrap.RewardKind.LOST_PROSPECTOR) {
                    validateDryRouteProvenance(
                            event, entry.chestPath(), authored, Set.of(),
                            true, local, "entry chest route");
                }
            }
            if (event.resolvedKind() == CaveDropTrap.RewardKind.LOST_PROSPECTOR) {
                validateDryRouteProvenance(
                        event, event.chestToExitPath(), authored, naturalStanding,
                        false, local, "chest-to-exit route");
            }
        }

        private void validateDryRouteProvenance(
                CaveDropTrapFeature.AuditEvent event,
                List<BlockPos> path,
                Map<BlockPos, AuthoredPosition> authored,
                Set<BlockPos> naturalSuffix,
                boolean cushionStart,
                List<String> local,
                String label) {
            for (int index = 0; index < path.size(); index++) {
                BlockPos node = path.get(index);
                if (naturalSuffix.contains(node)) {
                    check(!authored.containsKey(node), local,
                            key(event) + " " + label
                                    + " natural suffix was unexpectedly authored at " + pos(node));
                    continue;
                }
                AuthoredPosition write = authored.get(node);
                if (index == 0 && cushionStart) {
                    check(write != null && write.role() == AuthoredRole.POWDER_CUSHION, local,
                            key(event) + " " + label + " lacks typed "
                                    + AuthoredRole.POWDER_CUSHION
                                    + " provenance at " + pos(node));
                    continue;
                }
                // The wade-out node: the victim stands in the bored shaft column, in the
                // powder it just landed in, before walking out through the gallery.
                if (index == 1 && cushionStart && node.equals(path.getFirst().above())) {
                    check(write != null && write.role() == AuthoredRole.OPENED_SHAFT, local,
                            key(event) + " " + label + " wade-out lacks typed "
                                    + AuthoredRole.OPENED_SHAFT
                                    + " provenance at " + pos(node));
                    continue;
                }
                if (write == null) {
                    // At/above the carpet plane the climb-out stair may emerge into the
                    // passage's own pre-existing air, which the trap deliberately never
                    // writes; below the carpet every route node must remain a typed write.
                    check(node.getY() >= event.canonicalAnchor().getY()
                                    && dryWalkableStanding(node)
                                    && world.getBlockEntity(node) == null,
                            local,
                            key(event) + " " + label
                                    + " untouched node is not safe passage air at " + pos(node));
                    continue;
                }
                boolean shaftStanding = write.role() == AuthoredRole.OPENED_SHAFT
                        && index > 0
                        && path.get(index - 1).getY() + 1 >= node.getY();
                check(write.role() == AuthoredRole.GALLERY_AIR || shaftStanding, local,
                        key(event) + " " + label + " lacks typed " + AuthoredRole.GALLERY_AIR
                                + " provenance at " + pos(node));
            }
        }

        private void validateFloodedRouteProvenance(
                CaveDropTrapFeature.AuditEvent event,
                List<BlockPos> path,
                Map<BlockPos, AuthoredPosition> authored,
                Set<BlockPos> explicitUntouched,
                List<String> local,
                String label) {
            Set<BlockPos> water = new HashSet<>(event.waterPositions());
            for (BlockPos node : path) {
                AuthoredPosition write = authored.get(node);
                AuthoredRole expectedRole =
                        water.contains(node)
                                ? AuthoredRole.GALLERY_WATER
                                : AuthoredRole.GALLERY_AIR;
                if (write != null) {
                    check(write.role() == expectedRole, local,
                            key(event) + " " + label + " has wrong typed provenance at "
                                    + pos(node));
                    continue;
                }
                check(explicitUntouched.contains(node), local,
                        key(event) + " " + label
                                + " crosses unrelated pre-existing void at " + pos(node));
                check(water.contains(node) ? sourceWater(node) : dryWalkableStanding(node),
                        local,
                        key(event) + " " + label
                                + " untouched evidence is not currently safe at " + pos(node));
                check(world.getBlockEntity(node) == null, local,
                        key(event) + " " + label
                                + " untouched evidence has a block entity at " + pos(node));
            }
        }

        private void validateDryEntry(
                CaveDropTrapFeature.AuditEvent event,
                EntryEvidence entry,
                List<String> local) {
            check(
                    world.getBlockState(entry.landing()).is(Blocks.POWDER_SNOW),
                    local,
                    key(event) + " dry landing is not vanilla powder cushion at "
                            + pos(entry.landing()));
            check(
                    stableSupportBelow(entry.landing(), MIN_STABLE_SUPPORT_DEPTH),
                    local,
                    key(event) + " cushion lacks stable dry support at " + pos(entry.landing()));
            validateDryPath(
                    event, entry.dryPath(), entry.landing(), entry.dryExit(), true, local, "exit");
        }

        private void validateDryPath(
                CaveDropTrapFeature.AuditEvent event,
                List<BlockPos> path,
                BlockPos start,
                BlockPos end,
                boolean cushionStart,
                List<String> local,
                String label) {
            check(!path.isEmpty(), local, key(event) + " missing " + label + " path");
            if (path.isEmpty()) {
                return;
            }
            check(path.getFirst().equals(start), local,
                    key(event) + " " + label + " path starts at wrong position");
            check(path.getLast().equals(end), local,
                    key(event) + " " + label + " path ends at wrong position");
            Set<BlockPos> sceneCushions = new HashSet<>();
            for (EntryEvidence sceneEntry : event.entries()) {
                sceneCushions.add(sceneEntry.landing());
            }
            for (int i = 0; i < path.size(); i++) {
                BlockPos node = path.get(i);
                boolean wadeOut = i == 1 && cushionStart
                        && node.equals(path.getFirst().above());
                if (i > 0) {
                    // The route's first move is the victim standing up out of the cushion --
                    // a pure vertical step in the shaft column -- before walking out.
                    check(wadeOut || validWalkingStep(path.get(i - 1), node), local,
                            key(event) + " invalid step in " + label + " path at " + i);
                }
                if (i == 0 && cushionStart) {
                    check(world.getBlockState(node).is(Blocks.POWDER_SNOW), local,
                            key(event) + " " + label + " path does not start in cushion");
                    continue;
                }
                // Standing over one of the scene's own powder cushions is legitimate route
                // ground: the landing gallery floor IS the cushion field.
                boolean cushionStanding = sceneCushions.contains(node.below())
                        && world.getBlockState(node).isAir()
                        && world.getBlockState(node.above()).isAir();
                check(cushionStanding || dryWalkableStanding(node), local,
                        key(event) + " non-walkable " + label + " node " + pos(node));
            }
            check(dryWalkableStanding(end), local,
                    key(event) + " named dry exit is not supported/passable");
        }

        private void validateOrdinary(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            check(
                    event.waterPositions().isEmpty()
                            && event.orePositions().isEmpty()
                            && event.shorePositions().isEmpty()
                            && event.galleryPositions().isEmpty()
                            && event.chestPosition() == null
                            && event.chestFront() == null
                            && event.remainsFootPosition() == null
                            && event.remainsHeadPosition() == null
                            && !hasCollapsedExplorerRoles(event),
                    local,
                    key(event) + " ordinary scene contains a rare-outcome signature");
        }

        private void validateFlooded(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            check(
                    event.landingMinY() < 0 && event.landingMaxY() < 0,
                    local,
                    key(event) + " flooded surface is not below Y0");
            check(
                    event.fallMin() >= 32 && event.fallMax() <= 96,
                    local,
                    key(event) + " flooded fall is outside 32..96");
            Set<BlockPos> waters = new HashSet<>(event.waterPositions());
            Set<BlockPos> shores = new HashSet<>(event.shorePositions());
            Set<BlockPos> gallery = new HashSet<>(event.galleryPositions());
            check(
                    gallery.size()
                            >= Math.max(
                                    FLOODED_GALLERY_MIN_FOOTPRINT,
                                    event.powderPositions().size()
                                            + FLOODED_GALLERY_ENTRANCE_MARGIN),
                    local,
                    key(event) + " flooded gallery does not exceed entrance footprint");
            check(connectedColumns(event.galleryPositions()), local,
                    key(event) + " flooded gallery is not connected");
            for (BlockPos standing : gallery) {
                check(
                        world.getBlockState(standing).isAir()
                                && world.getBlockState(standing.above()).isAir(),
                        local,
                        key(event) + " gallery standing space blocked at " + pos(standing));
            }

            for (EntryEvidence entry : event.entries()) {
                BlockPos water = entry.landing();
                check(water.getY() < 0, local,
                        key(event) + " entry water landing is not below Y0");
                for (int depth = 0; depth < 3; depth++) {
                    BlockPos source = water.below(depth);
                    check(waters.contains(source), local,
                            key(event) + " source water absent from authored water list at "
                                    + pos(source));
                    check(sourceWater(source), local,
                            key(event) + " entry lacks three-deep source water at " + pos(source));
                }
                check(entry.shore() != null && shores.contains(entry.shore()), local,
                        key(event) + " entry lacks its named dry shore");
                validateFloodedPath(event, entry, waters, shores, local);
            }

            Set<BlockPos> oreWrites = event.authoredPositions().stream()
                    .filter(position -> position.role() == AuthoredRole.ORE_REWARD)
                    .map(AuthoredPosition::position)
                    .collect(java.util.stream.Collectors.toSet());
            check(event.orePositions().size() == 16
                            && new HashSet<>(event.orePositions()).size() == 16
                            && oreWrites.size() == 16
                            && oreWrites.equals(new HashSet<>(event.orePositions())),
                    local,
                    key(event) + " flooded payoff is not exactly sixteen typed ore writes");
            Set<BlockPos> connectedGalleryWrites = event.authoredPositions().stream()
                    .filter(position ->
                            position.role() == AuthoredRole.GALLERY_AIR
                                    || position.role() == AuthoredRole.GALLERY_WATER)
                    .map(AuthoredPosition::position)
                    .collect(java.util.stream.Collectors.toSet());
            Map<String, Integer> composition = new HashMap<>();
            for (BlockPos ore : event.orePositions()) {
                BlockState actual = world.getBlockState(ore);
                check(isOre(actual), local,
                        key(event) + " authored ore is not an _ore block at " + pos(ore));
                composition.merge(blockId(actual), 1, Integer::sum);
                boolean galleryExposed = false;
                for (Direction direction : Direction.values()) {
                    if (connectedGalleryWrites.contains(ore.relative(direction))) {
                        galleryExposed = true;
                        break;
                    }
                }
                check(galleryExposed, local,
                        key(event) + " ore is not exposed to the authored connected gallery at "
                                + pos(ore));
            }
            check(composition.equals(FLOODED_ORE_COMPOSITION), local,
                    key(event) + " flooded ore composition differs from 2/4/5/5 contract: "
                            + composition);
            check(event.chestPosition() == null
                            && event.remainsFootPosition() == null
                            && event.remainsHeadPosition() == null
                            && !hasCollapsedExplorerRoles(event),
                    local,
                    key(event) + " flooded scene contains prospector signature");
        }

        private void validateFloodedPath(
                CaveDropTrapFeature.AuditEvent event,
                EntryEvidence entry,
                Set<BlockPos> water,
                Set<BlockPos> shores,
                List<String> local) {
            List<BlockPos> path = entry.shorePath();
            check(!path.isEmpty(), local, key(event) + " missing entry shore path");
            if (path.isEmpty()) {
                return;
            }
            check(path.getFirst().equals(entry.landing()), local,
                    key(event) + " shore path starts away from water landing");
            check(path.getLast().equals(entry.dryExit()), local,
                    key(event) + " shore path does not end at named dry exit");
            check(entry.shore() != null && path.contains(entry.shore()), local,
                    key(event) + " shore path does not pass through named shore");
            boolean reachedDry = false;
            for (int i = 0; i < path.size(); i++) {
                BlockPos node = path.get(i);
                if (i > 0) {
                    check(validWalkingStep(path.get(i - 1), node), local,
                            key(event) + " invalid flooded route step at " + i);
                }
                if (water.contains(node)) {
                    check(!reachedDry, local,
                            key(event) + " flooded route re-enters water after shore");
                    check(sourceWater(node), local,
                            key(event) + " flooded route water is not source at " + pos(node));
                } else {
                    reachedDry = true;
                    check(dryWalkableStanding(node), local,
                            key(event) + " flooded route dry node is unsafe at " + pos(node));
                }
            }
            check(entry.shore() != null && shores.contains(entry.shore())
                            && dryWalkableStanding(entry.shore()),
                    local,
                    key(event) + " named shore is not dry/passable");
            check(dryWalkableStanding(entry.dryExit()), local,
                    key(event) + " flooded exit is not dry/passable");
        }

        private void validateProspector(
                CaveDropTrapFeature.AuditEvent event, List<String> local) {
            long footWrites = event.authoredPositions().stream()
                    .filter(position ->
                            position.role() == AuthoredRole.COLLAPSED_EXPLORER_FOOT)
                    .count();
            long headWrites = event.authoredPositions().stream()
                    .filter(position ->
                            position.role() == AuthoredRole.COLLAPSED_EXPLORER_HEAD)
                    .count();
            long chestWrites = event.authoredPositions().stream()
                    .filter(position -> position.role() == AuthoredRole.TREASURE_CHEST)
                    .count();
            check(footWrites == 1, local,
                    key(event) + " prospector scene must author exactly one FOOT remains block");
            check(headWrites == 1, local,
                    key(event) + " prospector scene must author exactly one HEAD remains block");
            check(chestWrites == 1, local,
                    key(event) + " prospector scene must author exactly one chest");
            BlockPos foot = event.remainsFootPosition();
            BlockPos head = event.remainsHeadPosition();
            BlockPos chest = event.chestPosition();
            BlockPos front = event.chestFront();
            Map<BlockPos, AuthoredPosition> authored = authoredByPosition(event);
            AuthoredPosition footWrite = foot == null ? null : authored.get(foot);
            AuthoredPosition headWrite = head == null ? null : authored.get(head);
            check(footWrite != null
                            && footWrite.role() == AuthoredRole.COLLAPSED_EXPLORER_FOOT
                            && collapsedExplorerState(footWrite.observed(), BedPart.FOOT),
                    local,
                    key(event) + " explicit FOOT position lacks its observed authored FOOT state");
            check(headWrite != null
                            && headWrite.role() == AuthoredRole.COLLAPSED_EXPLORER_HEAD
                            && collapsedExplorerState(headWrite.observed(), BedPart.HEAD),
                    local,
                    key(event) + " explicit HEAD position lacks its observed authored HEAD state");

            BlockState footState = foot == null ? null : world.getBlockState(foot);
            BlockState headState = head == null ? null : world.getBlockState(head);
            check(collapsedExplorerState(footState, BedPart.FOOT),
                    local,
                    key(event) + " collapsed explorer FOOT block/state missing");
            check(collapsedExplorerState(headState, BedPart.HEAD),
                    local,
                    key(event) + " collapsed explorer HEAD block/state missing");
            check(chest != null && world.getBlockState(chest).is(Blocks.CHEST), local,
                    key(event) + " chest block missing");
            if (foot == null || head == null || chest == null || front == null
                    || !collapsedExplorerState(footState, BedPart.FOOT)
                    || !collapsedExplorerState(headState, BedPart.HEAD)) {
                return;
            }

            Direction bodyFacing = footState.getValue(HorizontalDirectionalBlock.FACING);
            Direction headFacing = headState.getValue(HorizontalDirectionalBlock.FACING);
            check(headFacing == bodyFacing, local,
                    key(event) + " collapsed explorer FOOT/HEAD facings differ");
            check(head.equals(foot.relative(bodyFacing)), local,
                    key(event) + " HEAD is not one block beyond FOOT in the shared facing");
            Direction lateral = horizontalDirection(head, chest);
            BlockState chestState = world.getBlockState(chest);
            Direction chestFacing = chestState.hasProperty(ChestBlock.FACING)
                    ? chestState.getValue(ChestBlock.FACING) : null;
            check(lateral != null
                            && lateral.getAxis() != bodyFacing.getAxis()
                            && chestFacing == lateral,
                    local,
                    key(event) + " chest is not lateral to HEAD and facing outward");
            check(chestFacing != null && front.equals(chest.relative(chestFacing)), local,
                    key(event) + " chest front is not one block farther in chest facing");

            check(insideOwnerInset(foot) && insideOwnerInset(head), local,
                    key(event) + " collapsed explorer body leaves the owner inset");
            check(certifiedNaturalMassBelow(foot, MIN_STABLE_SUPPORT_DEPTH, authored), local,
                    key(event) + " FOOT lacks unmodified four-deep safe-natural support");
            check(certifiedNaturalMassBelow(head, MIN_STABLE_SUPPORT_DEPTH, authored), local,
                    key(event) + " HEAD lacks unmodified four-deep safe-natural support");
            check(stableSupportBelow(chest, MIN_STABLE_SUPPORT_DEPTH), local,
                    key(event) + " chest lacks firm dry shelf");
            Set<BlockPos> cushions = event.entries().stream()
                    .map(EntryEvidence::landing)
                    .collect(java.util.stream.Collectors.toSet());
            Set<BlockPos> body = new HashSet<>(List.of(foot, head));
            check(!foot.equals(head)
                            && !body.contains(chest)
                            && !body.contains(front)
                            && cushions.stream().noneMatch(body::contains)
                            && !cushions.contains(chest)
                            && !cushions.contains(front),
                    local,
                    key(event) + " body/chest/front/cushion positions are not distinct");
            check(cardinalAdjacent(chest, front) && dryWalkableStanding(front), local,
                    key(event) + " chest front is not adjacent/passable/supported");
            check(collapsedExplorerBlockEntity(world, foot, BedPart.FOOT),
                    local,
                    key(event) + " collapsed explorer FOOT render anchor is missing, wrong-type, "
                            + "wrong-half, or failed persistence");
            check(collapsedExplorerBlockEntity(world, head, BedPart.HEAD),
                    local,
                    key(event) + " collapsed explorer HEAD render anchor is missing, wrong-type, "
                            + "wrong-half, or failed persistence");
            BlockEntity blockEntity = world.getBlockEntity(chest);
            check(blockEntity instanceof ChestBlockEntity, local,
                    key(event) + " chest block entity missing");
            if (blockEntity instanceof ChestBlockEntity chestEntity) {
                check(
                        chestEntity.getLootTable() != null
                                && TREASURE_LOOT.equals(
                                        chestEntity.getLootTable().identifier().toString()),
                        local,
                        key(event) + " chest loot table differs from " + TREASURE_LOOT);
            }
            for (EntryEvidence entry : event.entries()) {
                check(entry.chestPath().stream().noneMatch(body::contains), local,
                        key(event) + " entry chest path intersects the complete body");
                validateDryPath(
                        event, entry.chestPath(), entry.landing(), front, true, local, "chest");
                validateDryPath(
                        event, entry.dryPath(), entry.landing(), entry.dryExit(),
                        true, local, "exit");
            }
            List<BlockPos> chestExit = event.chestToExitPath();
            check(chestExit.stream().noneMatch(body::contains), local,
                    key(event) + " chest-to-exit route intersects the complete body");
            BlockPos namedExit = chestExit.isEmpty() ? null : chestExit.getLast();
            check(namedExit != null && event.dryExitPositions().contains(namedExit), local,
                    key(event) + " chest-to-exit route lacks a named dry exit");
            if (namedExit != null) {
                validateDryPath(
                        event, chestExit, front, namedExit, false, local, "chest-to-exit");
            }
        }

        /**
         * Starts one real, server-owned zombie on a path that the production pathfinder routed
         * through a named ordinary-trap entrance. Returning {@code true} means the assay now owns
         * a live mob and must be advanced by later server ticks.
         */
        private boolean beginMobNavigationAssay() {
            List<CaveDropTrapFeature.AuditEvent> ordinary = stableScenes.stream()
                    .filter(event -> event.resolvedKind() == CaveDropTrap.RewardKind.ORDINARY)
                    .toList();
            if (ordinary.isEmpty()) {
                mobNavigationAssay = MobNavigationAssay.notRun(
                        "no ordinary scene was available");
                return false;
            }
            Mob candidate = null;
            try {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", "zombie"));
                Object created = type == null
                        ? null : type.create(world, EntitySpawnReason.COMMAND);
                if (!(created instanceof Mob mob)) {
                    mobNavigationAssay = new MobNavigationAssay(
                            "BLOCKED", null, null, null, 0, null,
                            false, false, 0, null,
                            "minecraft:zombie did not create a normal Mob");
                    return false;
                }
                candidate = mob;
                for (CaveDropTrapFeature.AuditEvent event : ordinary) {
                    // Mouth-to-mouth first: the frozen vertex-cut law says the only carpet
                    // crossing between the two passage mouths runs over powder. The end
                    // banks are second-tier because they stand on the carpet itself.
                    List<BlockPos[]> attempts = new ArrayList<>();
                    for (BlockPos first : event.firstNaturalContinuation()) {
                        for (BlockPos second : event.secondNaturalContinuation()) {
                            attempts.add(new BlockPos[] {first, second});
                        }
                    }
                    for (BlockPos first : event.firstApproachBank()) {
                        for (BlockPos second : event.secondApproachBank()) {
                            attempts.add(new BlockPos[] {first, second});
                        }
                    }
                    for (BlockPos[] attempt : attempts) {
                        {
                            BlockPos start = attempt[0].above();
                            BlockPos target = attempt[1].above();
                            world.getChunk(start);
                            world.getChunk(target);
                            mob.setPos(
                                    start.getX() + 0.5D,
                                    start.getY(),
                                    start.getZ() + 0.5D);
                            // A created-but-unticked entity reports onGround=false, and the
                            // navigator refuses to path for an airborne mob -- the reason
                            // this assay never selected a path in any stored report.
                            mob.setOnGround(true);
                            net.minecraft.world.level.pathfinder.Path path =
                                    mob.getNavigation().createPath(target, 0);
                            if (path == null) {
                                continue;
                            }
                            BlockPos hit = entranceNode(path, event.powderPositions());
                            boolean attributedShaft = hit != null
                                    && event.entries().stream().anyMatch(entry ->
                                            entry.entranceTop().equals(hit)
                                                    && !entry.shaftPositions().isEmpty());
                            if (!attributedShaft) {
                                continue;
                            }
                            mob.setInvulnerable(true);
                            mob.setPersistenceRequired();
                            // Nothing holds tickets on the audited chunks once generation
                            // ends, so an added entity would be unloaded within a few ticks.
                            // Force a 3x3 window around the entrance for the descent watch.
                            net.minecraft.world.level.ChunkPos centre =
                                    net.minecraft.world.level.ChunkPos.containing(hit);
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    net.minecraft.world.level.ChunkPos forced =
                                            new net.minecraft.world.level.ChunkPos(
                                                    centre.x() + dx, centre.z() + dz);
                                    if (world.setChunkForced(forced.x(), forced.z(), true)) {
                                        assayForcedChunks.add(forced);
                                    }
                                }
                            }
                            if (!world.addFreshEntity(mob)) {
                                mob.discard();
                                mobNavigationAssay = new MobNavigationAssay(
                                        "BLOCKED", key(event), pos(start), pos(target),
                                        path.getNodeCount(), pos(hit),
                                        true, false, 0, entityPosition(mob),
                                        "the selected zombie could not be added to the server world");
                                return false;
                            }
                            assayMob = mob;
                            assayEntrance = hit;
                            assayTarget = target;
                            mobTicksRemaining = MOB_DESCENT_TICK_LIMIT;
                            if (!mob.getNavigation().moveTo(path, MOB_NAVIGATION_SPEED)) {
                                mobNavigationAssay = new MobNavigationAssay(
                                        "FAIL", key(event), pos(start), pos(target),
                                        path.getNodeCount(), pos(hit),
                                        true, false, 0, entityPosition(mob),
                                        "the server-added zombie rejected its selected path");
                                discardAssayMob();
                                return false;
                            }
                            mobNavigationAssay = new MobNavigationAssay(
                                    "RUNNING", key(event), pos(start), pos(target),
                                    path.getNodeCount(), pos(hit),
                                    true, false, 0, entityPosition(mob),
                                    "server-added zombie is following the selected entrance path");
                            return true;
                        }
                    }
                }
                mob.discard();
                mobNavigationAssay = new MobNavigationAssay(
                        "FAIL", null, null, null, 0, null,
                        false, false, 0, null,
                        "real zombie paths never selected a custom trap entrance");
                return false;
            } catch (Throwable failure) {
                if (candidate != null && candidate != assayMob) {
                    candidate.discard();
                }
                discardAssayMob();
                mobNavigationAssay = new MobNavigationAssay(
                        "BLOCKED", null, null, null, 0, null,
                        false, false, 0, null,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage());
                return false;
            }
        }

        /**
         * Advances the live assay by one genuine server tick. A path selection alone is not a
         * pass: the mob's feet must be observed two blocks below the selected powder top while
         * still in that exact, attributed shaft column.
         */
        private boolean tickMobNavigationAssay() {
            Mob mob = assayMob;
            BlockPos entrance = assayEntrance;
            if (mob == null || entrance == null) {
                return true;
            }
            // The zombie's own wander goals will happily replace the scripted path, so the
            // assay re-asserts the entrance as the navigation target -- but only when the
            // navigator has gone idle or on a slow keep-alive beat: recomputing every tick
            // resets path progress and the mob only inches forward.
            BlockPos walkTarget = assayTarget != null ? assayTarget : entrance.above();
            if (!mob.isRemoved()) {
                assayRepathCooldown--;
                if (mob.getNavigation().isDone() || assayRepathCooldown <= 0) {
                    // Aim BEYOND the carpet at the far mouth: the crossing itself is what
                    // drops the mob. Aiming at the entrance cell stalls at the carpet edge,
                    // because the pathfinder treats the powder cell as blocked support and
                    // settles for the nearest adjacent node.
                    mob.getNavigation().moveTo(
                            walkTarget.getX() + 0.5D,
                            walkTarget.getY(),
                            walkTarget.getZ() + 0.5D,
                            MOB_NAVIGATION_SPEED);
                    assayRepathCooldown = 20;
                }
                mob.getLookControl().setLookAt(
                        walkTarget.getX() + 0.5D,
                        walkTarget.getY(),
                        walkTarget.getZ() + 0.5D);
            }
            int ticksObserved = mobNavigationAssay.ticksObserved() + 1;
            String finalPosition = entityPosition(mob);
            if (descentObserved(entrance, mob.getX(), mob.getY(), mob.getZ())) {
                mobNavigationAssay = new MobNavigationAssay(
                        "PASS",
                        mobNavigationAssay.sceneAnchor(),
                        mobNavigationAssay.start(),
                        mobNavigationAssay.target(),
                        mobNavigationAssay.pathNodeCount(),
                        mobNavigationAssay.entranceHit(),
                        true,
                        true,
                        ticksObserved,
                        finalPosition,
                        "server-added zombie descended at least two blocks through "
                                + "the selected attributed trap shaft");
                discardAssayMob();
                return true;
            }
            if (mob.isRemoved()) {
                mobNavigationAssay = new MobNavigationAssay(
                        "FAIL",
                        mobNavigationAssay.sceneAnchor(),
                        mobNavigationAssay.start(),
                        mobNavigationAssay.target(),
                        mobNavigationAssay.pathNodeCount(),
                        mobNavigationAssay.entranceHit(),
                        true,
                        false,
                        ticksObserved,
                        finalPosition,
                        "the server-added zombie was removed before descending through "
                                + "the selected attributed trap shaft");
                discardAssayMob();
                return true;
            }
            if (--mobTicksRemaining <= 0) {
                mobNavigationAssay = new MobNavigationAssay(
                        "FAIL",
                        mobNavigationAssay.sceneAnchor(),
                        mobNavigationAssay.start(),
                        mobNavigationAssay.target(),
                        mobNavigationAssay.pathNodeCount(),
                        mobNavigationAssay.entranceHit(),
                        true,
                        false,
                        ticksObserved,
                        finalPosition,
                        "the server-added zombie did not descend through the selected "
                                + "attributed trap shaft within " + MOB_DESCENT_TICK_LIMIT
                                + " server ticks");
                discardAssayMob();
                return true;
            }
            mobNavigationAssay = new MobNavigationAssay(
                    "RUNNING",
                    mobNavigationAssay.sceneAnchor(),
                    mobNavigationAssay.start(),
                    mobNavigationAssay.target(),
                    mobNavigationAssay.pathNodeCount(),
                    mobNavigationAssay.entranceHit(),
                    true,
                    false,
                    ticksObserved,
                    finalPosition,
                    "server-added zombie is following the selected entrance path");
            return false;
        }

        private void discardAssayMob() {
            if (assayMob != null && !assayMob.isRemoved()) {
                assayMob.discard();
            }
            assayMob = null;
            assayEntrance = null;
            assayTarget = null;
            for (net.minecraft.world.level.ChunkPos forced : assayForcedChunks) {
                world.setChunkForced(forced.x(), forced.z(), false);
            }
            assayForcedChunks.clear();
        }

        private static boolean bordersPowderColumn(BlockPos floor, Set<BlockPos> powder) {
            for (BlockPos cell : powder) {
                int dx = Math.abs(cell.getX() - floor.getX());
                int dz = Math.abs(cell.getZ() - floor.getZ());
                if (dx + dz == 1) {
                    return true;
                }
            }
            return false;
        }

        private BlockPos entranceNode(
                net.minecraft.world.level.pathfinder.Path path, List<BlockPos> powder) {
            Set<Long> columns = columns(powder);
            Map<Long, Integer> yByColumn = new HashMap<>();
            for (BlockPos position : powder) {
                yByColumn.put(columnKey(position.getX(), position.getZ()), position.getY());
            }
            for (int index = 0; index < path.getNodeCount(); index++) {
                Node node = path.getNode(index);
                long column = columnKey(node.x, node.z);
                Integer topY = yByColumn.get(column);
                if (columns.contains(column)
                        && topY != null
                        && (node.y == topY || node.y == topY + 1)) {
                    return new BlockPos(node.x, topY, node.z);
                }
            }
            return null;
        }

        private int count(CaveDropTrap.RewardKind kind) {
            return (int) stableScenes.stream()
                    .filter(event -> event.resolvedKind() == kind)
                    .count();
        }

        private void verifyMinimums() {
            check(
                    count(CaveDropTrap.RewardKind.ORDINARY) >= config.minOrdinary(),
                    "ordinary count " + count(CaveDropTrap.RewardKind.ORDINARY)
                            + " < " + config.minOrdinary());
            check(
                    count(CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY)
                            >= config.minFlooded(),
                    "flooded count "
                            + count(CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY)
                            + " < " + config.minFlooded());
            check(
                    count(CaveDropTrap.RewardKind.LOST_PROSPECTOR)
                            >= config.minProspector(),
                    "prospector count "
                            + count(CaveDropTrap.RewardKind.LOST_PROSPECTOR)
                            + " < " + config.minProspector());
            int partition = count(CaveDropTrap.RewardKind.ORDINARY)
                    + count(CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY)
                    + count(CaveDropTrap.RewardKind.LOST_PROSPECTOR);
            check(partition == stableScenes.size(), "resolved outcomes do not partition scenes");
        }

        private List<CaveDropTrapFeature.AuditEvent> snapshotScenes() {
            synchronized (scenes) {
                return List.copyOf(scenes);
            }
        }

        private boolean sourceWater(BlockPos position) {
            BlockState state = world.getBlockState(position);
            return state.getFluidState().is(Fluids.WATER)
                    && state.getFluidState().isSource();
        }

        private Map<BlockPos, AuthoredPosition> authoredByPosition(
                CaveDropTrapFeature.AuditEvent event) {
            Map<BlockPos, AuthoredPosition> result = new HashMap<>();
            for (AuthoredPosition authored : event.authoredPositions()) {
                result.putIfAbsent(authored.position(), authored);
            }
            return result;
        }

        private boolean certifiedNaturalMassBelow(
                BlockPos occupied,
                int depth,
                Map<BlockPos, AuthoredPosition> authored) {
            for (int offset = 1; offset <= depth; offset++) {
                BlockPos support = occupied.below(offset);
                if (authored.containsKey(support)
                        || world.getBlockEntity(support) != null
                        || !certifiedNaturalSolid(world.getBlockState(support))) {
                    return false;
                }
            }
            return true;
        }

        private boolean stableSupportBelow(BlockPos occupied, int depth) {
            for (int offset = 1; offset <= depth; offset++) {
                BlockPos support = occupied.below(offset);
                BlockState state = world.getBlockState(support);
                if (state.isAir()
                        || !state.getFluidState().isEmpty()
                        || !state.blocksMotion()
                        || state.getBlock() instanceof FallingBlock
                        || state.is(Blocks.MAGMA_BLOCK)
                        || state.is(Blocks.BEDROCK)
                        || world.getBlockEntity(support) != null) {
                    return false;
                }
            }
            return true;
        }

        private boolean twoAirAbove(BlockPos floor) {
            return world.getBlockState(floor.above()).isAir()
                    && world.getBlockState(floor.above(2)).isAir();
        }

        private boolean dryWalkableStanding(BlockPos standing) {
            return insideOwnerInset(standing)
                    && world.getBlockState(standing).isAir()
                    && world.getBlockState(standing.above()).isAir()
                    && stableSupportBelow(standing, 1);
        }

        private boolean insideOwnerInset(BlockPos position) {
            int lx = Math.floorMod(position.getX(), 16);
            int lz = Math.floorMod(position.getZ(), 16);
            return lx >= 1 && lx <= 14 && lz >= 1 && lz <= 14;
        }

        private void check(boolean pass, String message) {
            if (!pass) {
                failures.add(message);
            }
        }

        private void check(boolean pass, List<String> local, String message) {
            if (!pass) {
                local.add(message);
            }
        }
    }

    private static final Comparator<CaveDropTrapFeature.AuditEvent> EVENT_ORDER =
            Comparator.comparingInt(
                            (CaveDropTrapFeature.AuditEvent event) ->
                                    event.canonicalAnchor().getX())
                    .thenComparingInt(event -> event.canonicalAnchor().getY())
                    .thenComparingInt(event -> event.canonicalAnchor().getZ());

    private static List<BlockPos> allEvidencePositions(
            CaveDropTrapFeature.AuditEvent event) {
        Set<BlockPos> result = new LinkedHashSet<>();
        result.add(event.canonicalAnchor());
        result.add(event.sceneMin());
        result.add(event.sceneMax());
        result.addAll(event.powderPositions());
        result.addAll(event.regularPositions());
        result.addAll(event.waterPositions());
        result.addAll(event.orePositions());
        result.addAll(event.firstApproachBank());
        result.addAll(event.secondApproachBank());
        result.addAll(event.firstNaturalContinuation());
        result.addAll(event.secondNaturalContinuation());
        result.addAll(event.upperNaturalReturn());
        result.addAll(event.firmCamouflagePositions());
        result.addAll(event.upperBookendPositions());
        result.addAll(event.dryExitPositions());
        result.addAll(event.chestToExitPath());
        result.addAll(event.shorePositions());
        result.addAll(event.galleryPositions());
        result.addAll(event.floodedExitPath());
        add(result, event.chestPosition());
        add(result, event.remainsFootPosition());
        add(result, event.remainsHeadPosition());
        add(result, event.chestFront());
        for (EntryEvidence entry : event.entries()) {
            result.add(entry.entranceTop());
            result.add(entry.landing());
            add(result, entry.dryExit());
            add(result, entry.shore());
            result.addAll(entry.dryPath());
            result.addAll(entry.shorePath());
            result.addAll(entry.chestPath());
            result.addAll(entry.shaftPositions());
        }
        Set<BlockPos> supportedUpper = new LinkedHashSet<>(event.firmCamouflagePositions());
        supportedUpper.addAll(event.upperBookendPositions());
        supportedUpper.addAll(event.firstApproachBank());
        supportedUpper.addAll(event.secondApproachBank());
        for (BlockPos floor : supportedUpper) {
            for (int offset = 1; offset <= MIN_STABLE_SUPPORT_DEPTH; offset++) {
                result.add(floor.below(offset));
            }
        }
        Set<BlockPos> naturalFloors = new LinkedHashSet<>(event.firstNaturalContinuation());
        naturalFloors.addAll(event.secondNaturalContinuation());
        naturalFloors.addAll(event.upperNaturalReturn());
        for (BlockPos floor : naturalFloors) {
            for (int offset = 1;
                    offset < CaveDropTrap.APPROACH_CONTINUATION_SUPPORT_DEPTH;
                    offset++) {
                result.add(floor.below(offset));
            }
        }
        event.authoredPositions().stream()
                .map(AuthoredPosition::position)
                .forEach(result::add);
        return List.copyOf(result);
    }

    private static void add(Set<BlockPos> positions, BlockPos position) {
        if (position != null) {
            positions.add(position);
        }
    }

    private static boolean hasCollapsedExplorerRoles(
            CaveDropTrapFeature.AuditEvent event) {
        return event.authoredPositions().stream().anyMatch(position ->
                position.role() == AuthoredRole.COLLAPSED_EXPLORER_FOOT
                        || position.role() == AuthoredRole.COLLAPSED_EXPLORER_HEAD);
    }

    private static String rewardMappingError(CaveDropTrapFeature.AuditEvent event) {
        int roll = event.rewardRoll();
        CaveDropTrap.RewardKind kind = event.resolvedKind();
        boolean hasFallback =
                event.fallbackReason() != null && !event.fallbackReason().isBlank();
        if (roll < 0 || roll > 15) {
            return "reward roll is outside 0..15";
        }
        if (roll <= 13) {
            return kind == CaveDropTrap.RewardKind.ORDINARY && !hasFallback
                    ? null : "roll 0..13 must be ordinary with no fallback";
        }
        if (roll == 14) {
            if (kind == CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY) {
                return hasFallback ? "successful flooded reward carries a fallback" : null;
            }
            return kind == CaveDropTrap.RewardKind.ORDINARY && hasFallback
                    ? null : "roll 14 must be flooded or a named ordinary fallback";
        }
        if (kind == CaveDropTrap.RewardKind.LOST_PROSPECTOR) {
            return hasFallback ? "successful prospector reward carries a fallback" : null;
        }
        return kind == CaveDropTrap.RewardKind.ORDINARY && hasFallback
                ? null : "roll 15 must be prospector or a named ordinary fallback";
    }

    private static boolean collapsedExplorerState(BlockState state, BedPart part) {
        return state != null
                && CollapsedExplorerBlocks.COLLAPSED_EXPLORER != null
                && state.is(CollapsedExplorerBlocks.COLLAPSED_EXPLORER)
                && state.hasProperty(HorizontalDirectionalBlock.FACING)
                && state.hasProperty(CollapsedExplorerBlock.PART)
                && state.getValue(CollapsedExplorerBlock.PART) == part;
    }

    private static AuthoredBlockEntityPolicy authoredBlockEntityPolicy(AuthoredRole role) {
        return switch (role) {
            case TREASURE_CHEST -> AuthoredBlockEntityPolicy.CHEST;
            case COLLAPSED_EXPLORER_FOOT ->
                    AuthoredBlockEntityPolicy.COLLAPSED_EXPLORER_FOOT;
            case COLLAPSED_EXPLORER_HEAD ->
                    AuthoredBlockEntityPolicy.COLLAPSED_EXPLORER_HEAD;
            default -> AuthoredBlockEntityPolicy.NONE;
        };
    }

    private static boolean authoredBlockEntityMatches(
            AuthoredBlockEntityPolicy policy, BlockEntity blockEntity) {
        return switch (policy) {
            case NONE -> blockEntity == null;
            case CHEST -> blockEntity instanceof ChestBlockEntity;
            case COLLAPSED_EXPLORER_FOOT ->
                    collapsedExplorerBlockEntity(blockEntity, BedPart.FOOT);
            case COLLAPSED_EXPLORER_HEAD ->
                    collapsedExplorerBlockEntity(blockEntity, BedPart.HEAD);
        };
    }

    private static boolean collapsedExplorerBlockEntity(
            ServerLevel world, BlockPos position, BedPart part) {
        return collapsedExplorerBlockEntity(world.getBlockEntity(position), part);
    }

    private static boolean collapsedExplorerBlockEntity(
            BlockEntity blockEntity, BedPart part) {
        return blockEntity instanceof CollapsedExplorerBlockEntity
                && CollapsedExplorerBlocks.COLLAPSED_EXPLORER_BLOCK_ENTITY != null
                && blockEntity.getType() == CollapsedExplorerBlocks.COLLAPSED_EXPLORER_BLOCK_ENTITY
                && collapsedExplorerState(blockEntity.getBlockState(), part);
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return null;
    }

    private static Direction outwardDirection(
            CaveDropTrap.ApproachAxis axis, boolean first) {
        if (axis == CaveDropTrap.ApproachAxis.NORTH_SOUTH) {
            return first ? Direction.NORTH : Direction.SOUTH;
        }
        return first ? Direction.WEST : Direction.EAST;
    }

    private static boolean correctContinuationSide(
            BlockPos floor, List<BlockPos> bank, Direction outward) {
        if (bank.isEmpty()) {
            return false;
        }
        return switch (outward) {
            case NORTH ->
                    floor.getZ() < bank.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            case SOUTH ->
                    floor.getZ() > bank.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
            case WEST ->
                    floor.getX() < bank.stream().mapToInt(BlockPos::getX).min().orElseThrow();
            case EAST ->
                    floor.getX() > bank.stream().mapToInt(BlockPos::getX).max().orElseThrow();
            default -> false;
        };
    }

    private static boolean endsWith(List<BlockPos> path, List<BlockPos> suffix) {
        if (suffix.isEmpty() || path.size() < suffix.size()) {
            return false;
        }
        int offset = path.size() - suffix.size();
        for (int index = 0; index < suffix.size(); index++) {
            if (!path.get(offset + index).equals(suffix.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean validWalkingStep(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX())
                        + Math.abs(from.getZ() - to.getZ()) == 1
                && Math.abs(from.getY() - to.getY()) <= 1;
    }

    private static boolean cardinalAdjacent(BlockPos first, BlockPos second) {
        return first.getY() == second.getY()
                && Math.abs(first.getX() - second.getX())
                        + Math.abs(first.getZ() - second.getZ()) == 1;
    }

    private static boolean connectedColumns(List<BlockPos> positions) {
        Set<Long> remaining = columns(positions);
        if (remaining.isEmpty()) {
            return false;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long start = remaining.iterator().next();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            long column = queue.removeFirst();
            int x = unpackX(column);
            int z = unpackZ(column);
            for (long next : List.of(
                    columnKey(x - 1, z),
                    columnKey(x + 1, z),
                    columnKey(x, z - 1),
                    columnKey(x, z + 1))) {
                if (remaining.contains(next) && seen.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return seen.size() == remaining.size();
    }

    private static boolean irregular(List<BlockPos> positions) {
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        return positions.size() < (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    private static int projectedOverlap(
            List<BlockPos> first,
            List<BlockPos> second,
            CaveDropTrap.ApproachAxis axis) {
        if (axis == null) {
            return 0;
        }
        Set<Integer> projection = new HashSet<>();
        for (BlockPos position : first) {
            projection.add(axis == CaveDropTrap.ApproachAxis.NORTH_SOUTH
                    ? position.getX() : position.getZ());
        }
        Set<Integer> other = new HashSet<>();
        for (BlockPos position : second) {
            other.add(axis == CaveDropTrap.ApproachAxis.NORTH_SOUTH
                    ? position.getX() : position.getZ());
        }
        projection.retainAll(other);
        return projection.size();
    }

    private static Set<Long> columns(List<BlockPos> positions) {
        Set<Long> result = new HashSet<>();
        for (BlockPos position : positions) {
            result.add(columnKey(position.getX(), position.getZ()));
        }
        return result;
    }

    private static long actualAuthoredFingerprint(
            ServerLevel world, CaveDropTrapFeature.AuditEvent event) {
        long value = 0x4354564552494659L;
        for (AuthoredPosition authored : event.authoredPositions()) {
            value = fingerprintState(
                    value, authored.position(), world.getBlockState(authored.position()));
        }
        return value;
    }

    private static long actualEvidenceFingerprint(
            ServerLevel world, CaveDropTrapFeature.AuditEvent event) {
        long value = 0x45564944454e4345L;
        List<BlockPos> positions = new ArrayList<>(allEvidencePositions(event));
        positions.sort(Comparator.comparingInt((BlockPos position) -> position.getX())
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ));
        for (BlockPos position : positions) {
            value = fingerprintState(value, position, world.getBlockState(position));
            BlockEntity blockEntity = world.getBlockEntity(position);
            if (blockEntity == null) {
                value = Long.rotateLeft(value ^ 0x4e4f5f42455f4845L, 11);
            } else {
                Identifier type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
                value = Long.rotateLeft(value ^ 0x4841535f42455f21L, 11);
                value = Long.rotateLeft(value ^ type.toString().hashCode(), 23);
            }
        }
        return value;
    }

    private static long fingerprintState(long prior, BlockPos position, BlockState state) {
        long value = Long.rotateLeft(prior ^ position.asLong(), 17);
        value = Long.rotateLeft(value ^ state.toString().hashCode(), 29);
        return value * 0x9e3779b97f4a7c15L;
    }

    private static JsonObject sceneJson(CaveDropTrapFeature.AuditEvent event) {
        JsonObject out = new JsonObject();
        out.addProperty("anchor", pos(event.canonicalAnchor()));
        out.addProperty("rewardRoll", event.rewardRoll());
        out.addProperty("resolvedKind", event.resolvedKind().name());
        nullable(out, "fallbackReason", event.fallbackReason());
        out.add("powder", positions(event.powderPositions()));
        out.add("regular", positions(event.regularPositions()));
        out.addProperty("landingMinY", event.landingMinY());
        out.addProperty("landingMaxY", event.landingMaxY());
        out.addProperty("fallMin", event.fallMin());
        out.addProperty("fallMax", event.fallMax());
        out.add("water", positions(event.waterPositions()));
        out.add("ores", positions(event.orePositions()));
        nullablePos(out, "chest", event.chestPosition());
        nullablePos(out, "remainsFoot", event.remainsFootPosition());
        nullablePos(out, "remainsHead", event.remainsHeadPosition());
        out.addProperty("sceneMin", pos(event.sceneMin()));
        out.addProperty("sceneMax", pos(event.sceneMax()));
        out.addProperty("approachAxis", event.approachAxis().name());
        out.add("firstApproachBank", positions(event.firstApproachBank()));
        out.add("secondApproachBank", positions(event.secondApproachBank()));
        out.add("firmCamouflage", positions(event.firmCamouflagePositions()));
        out.add("upperBookends", positions(event.upperBookendPositions()));
        out.add("firstNaturalContinuation", positions(event.firstNaturalContinuation()));
        out.add("secondNaturalContinuation", positions(event.secondNaturalContinuation()));
        out.add("upperNaturalReturn", positions(event.upperNaturalReturn()));
        JsonArray entries = new JsonArray();
        event.entries().forEach(entry -> entries.add(entryJson(entry)));
        out.add("entries", entries);
        out.add("dryExits", positions(event.dryExitPositions()));
        nullablePos(out, "chestFront", event.chestFront());
        out.add("chestToExitPath", positions(event.chestToExitPath()));
        out.add("shores", positions(event.shorePositions()));
        out.add("connectedGallery", positions(event.galleryPositions()));
        out.add("floodedExitPath", positions(event.floodedExitPath()));
        JsonArray authored = new JsonArray();
        event.authoredPositions().forEach(position -> authored.add(authoredJson(position)));
        out.add("authoredPositions", authored);
        out.addProperty("expectedFingerprint", unsignedHex(event.expectedFingerprint()));
        out.addProperty("observedFingerprint", unsignedHex(event.observedFingerprint()));
        out.addProperty("postWriteVerified", event.postWriteVerified());
        return out;
    }

    private static JsonObject entryJson(EntryEvidence entry) {
        JsonObject out = new JsonObject();
        out.addProperty("entranceTop", pos(entry.entranceTop()));
        out.addProperty("landing", pos(entry.landing()));
        nullablePos(out, "dryExit", entry.dryExit());
        out.add("dryPath", positions(entry.dryPath()));
        nullablePos(out, "shore", entry.shore());
        out.add("shorePath", positions(entry.shorePath()));
        out.add("chestPath", positions(entry.chestPath()));
        out.add("shaftPositions", positions(entry.shaftPositions()));
        return out;
    }

    private static JsonObject authoredJson(AuthoredPosition authored) {
        JsonObject out = new JsonObject();
        out.addProperty("position", pos(authored.position()));
        out.addProperty("role", authored.role().name());
        out.add("expected", stateJson(authored.expected()));
        out.add("observed", stateJson(authored.observed()));
        out.add("priorState", stateJson(authored.priorState()));
        out.addProperty("priorBlockEntityAbsent", authored.priorBlockEntityAbsent());
        return out;
    }

    private static JsonObject stateJson(BlockState state) {
        JsonObject out = new JsonObject();
        out.addProperty("block", blockId(state));
        JsonObject properties = new JsonObject();
        for (Property<?> property : state.getProperties()) {
            properties.addProperty(property.getName(), propertyValue(state, property));
        }
        out.add("properties", properties);
        return out;
    }

    private static CaveDropTrapFeature.AuditEvent readScene(JsonObject row)
            throws IOException {
        require(
                row,
                "anchor",
                "rewardRoll",
                "resolvedKind",
                "powder",
                "regular",
                "landingMinY",
                "landingMaxY",
                "fallMin",
                "fallMax",
                "water",
                "ores",
                "sceneMin",
                "sceneMax",
                "approachAxis",
                "firstApproachBank",
                "secondApproachBank",
                "firmCamouflage",
                "upperBookends",
                "firstNaturalContinuation",
                "secondNaturalContinuation",
                "upperNaturalReturn",
                "entries",
                "dryExits",
                "chestToExitPath",
                "shores",
                "connectedGallery",
                "floodedExitPath",
                "authoredPositions",
                "expectedFingerprint",
                "observedFingerprint",
                "postWriteVerified");
        requirePresent(
                row,
                "fallbackReason",
                "chest",
                "remainsFoot",
                "remainsHead",
                "chestFront");
        if (row.has("remains") || row.has("remainsPosition")) {
            throw new IOException("ambiguous legacy single-remains field is forbidden");
        }
        JsonArray entryRows = row.getAsJsonArray("entries");
        JsonArray authoredRows = row.getAsJsonArray("authoredPositions");
        if (entryRows == null || authoredRows == null || entryRows.isEmpty()
                || authoredRows.isEmpty()) {
            throw new IOException("legacy baseline row lacks strict topology/write evidence");
        }
        List<EntryEvidence> entries = new ArrayList<>();
        for (JsonElement element : entryRows) {
            entries.add(readEntry(element.getAsJsonObject()));
        }
        List<AuthoredPosition> authored = new ArrayList<>();
        for (JsonElement element : authoredRows) {
            authored.add(readAuthored(element.getAsJsonObject()));
        }
        return new CaveDropTrapFeature.AuditEvent(
                parsePos(row.get("anchor").getAsString()),
                row.get("rewardRoll").getAsInt(),
                CaveDropTrap.RewardKind.valueOf(row.get("resolvedKind").getAsString()),
                parsePositions(row.getAsJsonArray("powder")),
                parsePositions(row.getAsJsonArray("regular")),
                row.get("landingMinY").getAsInt(),
                row.get("landingMaxY").getAsInt(),
                row.get("fallMin").getAsInt(),
                row.get("fallMax").getAsInt(),
                parsePositions(row.getAsJsonArray("water")),
                parsePositions(row.getAsJsonArray("ores")),
                readNullablePos(row.get("chest")),
                readNullablePos(row.get("remainsFoot")),
                readNullablePos(row.get("remainsHead")),
                parsePos(row.get("sceneMin").getAsString()),
                parsePos(row.get("sceneMax").getAsString()),
                nullableString(row.get("fallbackReason")),
                CaveDropTrap.ApproachAxis.valueOf(row.get("approachAxis").getAsString()),
                parsePositions(row.getAsJsonArray("firstApproachBank")),
                parsePositions(row.getAsJsonArray("secondApproachBank")),
                parsePositions(row.getAsJsonArray("firmCamouflage")),
                List.copyOf(entries),
                parsePositions(row.getAsJsonArray("dryExits")),
                readNullablePos(row.get("chestFront")),
                parsePositions(row.getAsJsonArray("chestToExitPath")),
                parsePositions(row.getAsJsonArray("shores")),
                parsePositions(row.getAsJsonArray("connectedGallery")),
                parsePositions(row.getAsJsonArray("floodedExitPath")),
                List.copyOf(authored),
                parseUnsignedHex(row.get("expectedFingerprint").getAsString()),
                parseUnsignedHex(row.get("observedFingerprint").getAsString()),
                row.get("postWriteVerified").getAsBoolean(),
                parsePositions(row.getAsJsonArray("upperBookends")),
                parsePositions(row.getAsJsonArray("firstNaturalContinuation")),
                parsePositions(row.getAsJsonArray("secondNaturalContinuation")),
                parsePositions(row.getAsJsonArray("upperNaturalReturn")));
    }

    private static EntryEvidence readEntry(JsonObject row) throws IOException {
        require(
                row,
                "entranceTop",
                "landing",
                "dryPath",
                "shorePath",
                "chestPath",
                "shaftPositions");
        requirePresent(row, "dryExit", "shore");
        return new EntryEvidence(
                parsePos(row.get("entranceTop").getAsString()),
                parsePos(row.get("landing").getAsString()),
                readNullablePos(row.get("dryExit")),
                parsePositions(row.getAsJsonArray("dryPath")),
                readNullablePos(row.get("shore")),
                parsePositions(row.getAsJsonArray("shorePath")),
                parsePositions(row.getAsJsonArray("chestPath")),
                parsePositions(row.getAsJsonArray("shaftPositions")));
    }

    private static AuthoredPosition readAuthored(JsonObject row) throws IOException {
        require(
                row,
                "position",
                "role",
                "expected",
                "observed",
                "priorState",
                "priorBlockEntityAbsent");
        return new AuthoredPosition(
                parsePos(row.get("position").getAsString()),
                AuthoredRole.valueOf(row.get("role").getAsString()),
                readState(row.getAsJsonObject("expected")),
                readState(row.getAsJsonObject("observed")),
                readState(row.getAsJsonObject("priorState")),
                row.get("priorBlockEntityAbsent").getAsBoolean());
    }

    private static BlockState readState(JsonObject row) throws IOException {
        require(row, "block", "properties");
        Identifier id = Identifier.parse(row.get("block").getAsString());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IOException("unknown block in baseline: " + id);
        }
        BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        JsonObject properties = row.getAsJsonObject("properties");
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            Property<?> property =
                    state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                throw new IOException("unknown property " + entry.getKey() + " for " + id);
            }
            state = setProperty(state, property, entry.getValue().getAsString(), id);
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, Property<T> property, String value, Identifier id)
            throws IOException {
        Optional<T> parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            throw new IOException(
                    "invalid property value " + property.getName() + "=" + value + " for " + id);
        }
        return state.setValue(property, parsed.get());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propertyValue(
            BlockState state, Property<?> untyped) {
        Property<T> property = (Property<T>) untyped;
        return property.getName(state.getValue(property));
    }

    private static Map<String, Long> baselineFingerprints(Path path) throws IOException {
        JsonObject root = JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        requireCurrentSchema(root);
        require(root, "scenes");
        JsonArray rows = root.getAsJsonArray("scenes");
        Map<String, Long> result = new HashMap<>();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            require(row, "anchor", "settledFingerprint");
            String anchor = row.get("anchor").getAsString();
            Long prior = result.putIfAbsent(
                    anchor,
                    parseUnsignedHex(row.get("settledFingerprint").getAsString()));
            if (prior != null) {
                throw new IOException("baseline contains duplicate anchor " + anchor);
            }
        }
        return result;
    }

    private static JsonObject mobNavigationJson(MobNavigationAssay assay) {
        JsonObject out = new JsonObject();
        out.addProperty("status", assay.status());
        nullable(out, "sceneAnchor", assay.sceneAnchor());
        nullable(out, "start", assay.start());
        nullable(out, "target", assay.target());
        out.addProperty("pathNodeCount", assay.pathNodeCount());
        nullable(out, "entranceHit", assay.entranceHit());
        out.addProperty("pathSelected", assay.pathSelected());
        out.addProperty("descentObserved", assay.descentObserved());
        out.addProperty("ticksObserved", assay.ticksObserved());
        nullable(out, "finalPosition", assay.finalPosition());
        out.addProperty("detail", assay.detail());
        return out;
    }

    private static JsonObject passACensusJson(
            CaveDropTrapFeature.PassACensusSnapshot census) {
        return passACensusJson(census, null);
    }

    private static JsonObject passACensusJson(
            CaveDropTrapFeature.PassACensusSnapshot census,
            String driverRngKey) {
        JsonObject row = new JsonObject();
        row.addProperty("chunkX", census.chunkX());
        row.addProperty("chunkZ", census.chunkZ());
        if (driverRngKey != null) {
            row.addProperty("driverRngKey", driverRngKey);
        }
        JsonObject candidateSource = new JsonObject();
        candidateSource.addProperty("legalFloorCount", census.legalFloorCount());
        candidateSource.addProperty("auditDomainSlots", census.auditDomainSlots());
        candidateSource.addProperty(
                "caveAnchorQualified", census.caveAnchorQualified());
        candidateSource.addProperty(
                "caveAnchorRejected", census.caveAnchorRejected());
        candidateSource.addProperty(
                "reconciles", census.candidateSourceReconciles());
        row.add("candidateSource", candidateSource);
        row.addProperty("scanSlots", census.scanSlots());
        row.addProperty("uniqueOwnerAnchors", census.uniqueOwnerAnchors());
        row.addProperty("duplicateReject", census.duplicateReject());
        JsonObject topology = new JsonObject();
        CaveDropTrap.RoofedThroatMeasurements measurements = census.topologyMeasurements();
        topology.addProperty("present", measurements != null);
        if (measurements != null) {
            topology.addProperty("coverCount", measurements.coverCount());
            topology.addProperty("powderCount", measurements.powderCount());
            topology.addProperty("firmCount", measurements.firmCount());
            JsonArray componentSizes = new JsonArray();
            measurements.firmComponentSizes().forEach(componentSizes::add);
            topology.add("firmComponentSizes", componentSizes);
            topology.addProperty("mouthCount", measurements.mouthCount());
            topology.addProperty("distinctMouths", measurements.distinctMouths());
            topology.addProperty("coverConnected", measurements.coverConnected());
            topology.addProperty("powderConnected", measurements.powderConnected());
            topology.addProperty("firmRolesCorrect", measurements.firmRolesCorrect());
            topology.addProperty("powderVertexCut", measurements.powderVertexCut());
            topology.addProperty(
                    "witnessWriteDisjoint", measurements.witnessWriteDisjoint());
            topology.addProperty(
                    "lockedTopologyMatches", measurements.lockedTopologyMatches());
        }
        row.add("topology", topology);

        JsonObject rejections = new JsonObject();
        rejections.addProperty("sameDoor", census.sameDoorReject());
        rejections.addProperty(
                "invalidOrSelfAuthoredWitness",
                census.invalidOrSelfAuthoredWitnessReject());
        rejections.addProperty(
                "exposedSkyWaterOrThinRoof",
                census.exposedSkyWaterOrThinRoofReject());
        rejections.addProperty(
                "seafloorOrFluidStanding", census.seafloorOrFluidStandingReject());
        rejections.addProperty(
                "firmBypassOrWrongRoles", census.firmBypassOrWrongRolesReject());
        rejections.addProperty("cavityAirOrFluid", census.cavityAirOrFluidReject());
        rejections.addProperty(
                "oreBlockEntityOrProtected", census.oreBlockEntityOrProtectedReject());
        rejections.addProperty(
                "gravityOrPartialStabilization",
                census.gravityOrPartialStabilizationReject());
        rejections.addProperty("shellOrSupport", census.shellOrSupportReject());
        rejections.addProperty("owner", census.ownerReject());
        rejections.addProperty(
                "disconnectedLowerOrReward",
                census.disconnectedLowerOrRewardReject());
        rejections.addProperty("duplicate", census.duplicateReject());
        row.add("terminalRejections", rejections);

        row.addProperty("ordinaryFeasible", census.ordinaryFeasible());
        JsonObject rareFeasibility = new JsonObject();
        rareFeasibility.addProperty("floodedPass", census.floodedFeasible());
        rareFeasibility.addProperty("floodedFallback", census.floodedFallback());
        rareFeasibility.addProperty("prospectorPass", census.prospectorFeasible());
        rareFeasibility.addProperty("prospectorFallback", census.prospectorFallback());
        row.add("rareFeasibility", rareFeasibility);
        JsonObject selection = new JsonObject();
        selection.addProperty("fractionAccepted", census.fractionAccepted());
        selection.addProperty("fractionRejected", census.fractionRejected());
        selection.addProperty("capAccepted", census.capAccepted());
        selection.addProperty("capRejected", census.capRejected());
        selection.addProperty("resolvedOrdinary", census.resolvedOrdinary());
        selection.addProperty("resolvedFlooded", census.resolvedFlooded());
        selection.addProperty("resolvedProspector", census.resolvedProspector());
        selection.addProperty("resolvedFallback", census.resolvedFallback());
        JsonArray rolls = new JsonArray();
        for (int roll = 0; roll < census.rewardRollCounts().size(); roll++) {
            JsonObject count = new JsonObject();
            count.addProperty("roll", roll);
            count.addProperty("count", census.rewardRollCounts().get(roll));
            rolls.add(count);
        }
        selection.add("rollCounts", rolls);
        JsonObject requested = new JsonObject();
        requested.addProperty("ordinary", census.requestedOrdinary());
        requested.addProperty("flooded", census.requestedFlooded());
        requested.addProperty("prospector", census.requestedProspector());
        selection.add("requested", requested);
        JsonObject requestFallbacks = new JsonObject();
        requestFallbacks.addProperty("flooded", census.floodedRequestFallback());
        requestFallbacks.addProperty("prospector", census.prospectorRequestFallback());
        selection.add("requestFallbacks", requestFallbacks);
        row.add("predictedOutcomes", selection);
        row.addProperty("reconciles", census.reconciles());

        JsonArray representatives = new JsonArray();
        for (CaveDropTrapFeature.PassACensusRepresentative representative
                : census.terminalRepresentatives()) {
            JsonObject item = new JsonObject();
            item.addProperty("stage", representative.stage());
            nullablePos(item, "ownerAnchor", representative.ownerAnchor());
            item.addProperty("orientation", representative.orientation().name());
            item.addProperty("floorY", representative.floorY());
            if (representative.rejection() == null) {
                item.add("rejection", null);
            } else {
                item.addProperty("rejection", representative.rejection().name());
            }
            if (representative.rewardFailure() == null) {
                item.add("rewardFailure", null);
            } else {
                item.addProperty("rewardFailure", representative.rewardFailure().name());
            }
            if (representative.rewardRoll() < 0) {
                item.add("rewardRoll", null);
            } else {
                item.addProperty("rewardRoll", representative.rewardRoll());
            }
            if (representative.requestedKind() == null) {
                item.add("requestedKind", null);
            } else {
                item.addProperty("requestedKind", representative.requestedKind().name());
            }
            if (representative.resolvedKind() == null) {
                item.add("resolvedKind", null);
            } else {
                item.addProperty("resolvedKind", representative.resolvedKind().name());
            }
            item.addProperty("fallback", representative.fallback());
            representatives.add(item);
        }
        row.add("terminalRepresentatives", representatives);
        JsonArray provenance = new JsonArray();
        for (CaveDropTrapFeature.PassAProvenanceCount count
                : census.provenanceCounts()) {
            CaveDropTrapFeature.PassAFirstWitness witness = count.representative();
            JsonObject item = new JsonObject();
            item.addProperty("key", count.key());
            item.addProperty("count", count.count());
            item.addProperty("terminalBucket", witness.terminalBucket());
            item.addProperty("source", witness.source().name());
            item.addProperty("predicateRole", witness.predicateRole().name());
            nullablePos(item, "worldPosition", witness.worldPosition());
            boolean causalQuery = witness.source()
                    == CaveDropTrapFeature.PassAProvenanceSource.PREDICATE_QUERY;
            item.addProperty(
                    "worldPositionSemantics",
                    causalQuery ? "CAUSAL_QUERY" : "CONTEXT_OWNER_ANCHOR");
            if (causalQuery) {
                item.addProperty("blockId", witness.blockId());
                item.addProperty("blockKind", witness.blockKind());
                item.addProperty("actualAuthored", witness.actualAuthored());
            } else {
                item.add("blockId", com.google.gson.JsonNull.INSTANCE);
                item.add("blockKind", com.google.gson.JsonNull.INSTANCE);
                item.add("actualAuthored", com.google.gson.JsonNull.INSTANCE);
            }
            item.addProperty("floorY", witness.floorY());
            if (causalQuery) {
                item.addProperty("surfaceY", witness.surfaceY());
                item.addProperty("surfaceOffset", witness.surfaceOffset());
            } else {
                item.add("surfaceY", com.google.gson.JsonNull.INSTANCE);
                item.add("surfaceOffset", com.google.gson.JsonNull.INSTANCE);
            }
            provenance.add(item);
        }
        row.add("firstFailureProvenance", provenance);
        row.addProperty("provenanceReconciles", census.provenanceReconciles());
        return row;
    }

    private static JsonObject censusJson(CaveDropTrapFeature.CensusSnapshot census) {
        JsonObject row = new JsonObject();
        row.addProperty("chunkX", census.chunkX());
        row.addProperty("chunkZ", census.chunkZ());
        row.addProperty("scanSlots", census.scanSlots());
        row.addProperty("referenceFloorHeadroomPass", census.referenceFloorHeadroomPass());
        row.addProperty("referenceFloorHeadroomFail", census.referenceFloorHeadroomFail());
        row.addProperty("envelopePass", census.envelopePass());
        row.addProperty("strictEnvelopePass", census.envelopePass());
        row.addProperty(
                "stabilizationOnlyEnvelopePass", census.stabilizationOnlyEnvelopePass());
        JsonObject envelopeFailures = new JsonObject();
        envelopeFailures.addProperty("solid", census.envelopeSolidFail());
        envelopeFailures.addProperty("air", census.envelopeAirFail());
        envelopeFailures.addProperty("both", census.envelopeBothFail());
        envelopeFailures.addProperty("other", census.envelopeOtherFail());
        row.add("envelopeFailures", envelopeFailures);
        CaveDropTrapFeature.CensusDefectCounts defects = census.solidDefects();
        JsonObject defectCounts = new JsonObject();
        defectCounts.addProperty(
                "fallingNaturalStabilizable", defects.fallingNaturalStabilizable());
        defectCounts.addProperty("voidOrAir", defects.voidOrAir());
        defectCounts.addProperty("fluid", defects.fluid());
        defectCounts.addProperty("oreOrBlockEntity", defects.oreOrBlockEntity());
        defectCounts.addProperty(
                "protectedBedrockOrMagma", defects.protectedBedrockOrMagma());
        defectCounts.addProperty("outsideOwner", defects.outsideOwner());
        defectCounts.addProperty("other", defects.other());
        defectCounts.addProperty("total", defects.total());
        row.add("requiredSolidDefects", defectCounts);
        row.addProperty("enclosurePass", census.enclosurePass());
        row.addProperty("enclosureFail", census.enclosureFail());
        row.addProperty(
                "stabilizationOnlyEnclosurePass", census.stabilizationOnlyEnclosurePass());
        row.addProperty(
                "stabilizationOnlyEnclosureFail", census.stabilizationOnlyEnclosureFail());
        row.addProperty("gravityHazardPass", census.gravityHazardPass());
        row.addProperty("gravityHazardFail", census.gravityHazardFail());
        row.addProperty(
                "stabilizationOnlyGravityHazardPass",
                census.stabilizationOnlyGravityHazardPass());
        row.addProperty(
                "stabilizationOnlyGravityHazardFail",
                census.stabilizationOnlyGravityHazardFail());
        row.addProperty("materializationPass", census.materializationPass());
        row.addProperty("materializationFail", census.materializationFail());
        JsonObject hypotheticalMaterialization = new JsonObject();
        hypotheticalMaterialization.addProperty("status", "NOT_MODELED");
        hypotheticalMaterialization.addProperty(
                "envelopeEnclosureGravitySurvivors",
                census.stabilizationOnlyMaterializationNotModeled());
        row.add("hypotheticalMaterialization", hypotheticalMaterialization);
        row.addProperty("reconciles", census.reconciles());
        row.add("representative", censusRepresentativeJson(census.representative()));
        JsonArray terminalRepresentatives = new JsonArray();
        census.terminalRepresentatives().stream()
                .sorted(Comparator.comparingInt(
                        CaveDropTrapFeature.CensusRepresentative::stageRank))
                .map(CaveDropTrapFullChunkAudit::censusRepresentativeJson)
                .forEach(terminalRepresentatives::add);
        row.add("terminalRepresentatives", terminalRepresentatives);
        return row;
    }

    private static JsonObject censusRepresentativeJson(
            CaveDropTrapFeature.CensusRepresentative nearMiss) {
        JsonObject representative = new JsonObject();
        if (nearMiss == null) {
            representative.addProperty("present", false);
            representative.add("stage", null);
            representative.add("orientation", null);
            representative.add("origin", null);
            representative.add("floorY", null);
            representative.add("solidFailures", null);
            representative.add("firstSolidFailure", null);
            representative.add("firstSolidState", null);
            representative.add("airFailures", null);
            representative.add("firstAirFailure", null);
            representative.add("firstAirState", null);
            representative.add("enclosureMinimumRise", null);
            representative.add("enclosureFailingColumns", null);
            representative.add("solidDefects", null);
            representative.add("exposedGravityHazards", null);
            representative.add("stabilizationOnly", null);
        } else {
            representative.addProperty("present", true);
            representative.addProperty("stage", nearMiss.stageRank());
            representative.addProperty("orientation", nearMiss.orientation().name());
            representative.addProperty(
                    "origin", nearMiss.originLx() + "," + nearMiss.originLz());
            representative.addProperty("floorY", nearMiss.floorY());
            representative.addProperty("solidFailures", nearMiss.solidFailureCount());
            nullablePos(representative, "firstSolidFailure", nearMiss.firstSolidFailure());
            nullable(representative, "firstSolidState", nearMiss.firstSolidState());
            representative.addProperty("airFailures", nearMiss.airFailureCount());
            nullablePos(representative, "firstAirFailure", nearMiss.firstAirFailure());
            nullable(representative, "firstAirState", nearMiss.firstAirState());
            representative.addProperty("enclosureMinimumRise", nearMiss.enclosureMinimumRise());
            representative.addProperty("enclosureFailingColumns", nearMiss.enclosureFailingColumns());
            CaveDropTrapFeature.CensusDefectCounts defects = nearMiss.solidDefects();
            JsonObject defectCounts = new JsonObject();
            defectCounts.addProperty(
                    "fallingNaturalStabilizable", defects.fallingNaturalStabilizable());
            defectCounts.addProperty("voidOrAir", defects.voidOrAir());
            defectCounts.addProperty("fluid", defects.fluid());
            defectCounts.addProperty("oreOrBlockEntity", defects.oreOrBlockEntity());
            defectCounts.addProperty(
                    "protectedBedrockOrMagma", defects.protectedBedrockOrMagma());
            defectCounts.addProperty("outsideOwner", defects.outsideOwner());
            defectCounts.addProperty("other", defects.other());
            representative.add("solidDefects", defectCounts);
            representative.addProperty(
                    "exposedGravityHazards", nearMiss.exposedGravityHazards());
            representative.addProperty("stabilizationOnly", nearMiss.stabilizationOnly());
        }
        return representative;
    }

    private static void requireCurrentSchema(JsonObject root) throws IOException {
        String actual = string(root, "schema");
        if (!SCHEMA.equals(actual)) {
            throw new IOException(
                    "baseline schema " + actual
                            + " rejected; strict rescan requires " + SCHEMA
                            + " paired-remains evidence");
        }
    }

    private static void require(JsonObject object, String... names) throws IOException {
        for (String name : names) {
            if (!object.has(name) || object.get(name).isJsonNull()) {
                throw new IOException("strict baseline scene missing " + name);
            }
        }
    }

    private static void requirePresent(JsonObject object, String... names) throws IOException {
        for (String name : names) {
            if (!object.has(name)) {
                throw new IOException("strict baseline scene missing " + name);
            }
        }
    }

    private static boolean isOre(BlockState state) {
        return blockId(state).endsWith("_ore");
    }

    private static boolean certifiedNaturalSolid(BlockState state) {
        return state != null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && state.blocksMotion()
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.MAGMA_BLOCK)
                && !(state.getBlock() instanceof FallingBlock)
                && naturalMaterial(state)
                && !isOre(state);
    }

    private static boolean naturalMaterial(BlockState state) {
        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.ICE);
    }

    private static String blockId(BlockState state) {
        if (state == null) {
            return "null";
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "unknown" : id.toString();
    }

    private static JsonArray positions(List<BlockPos> positions) {
        JsonArray out = new JsonArray();
        positions.forEach(position -> out.add(pos(position)));
        return out;
    }

    private static List<BlockPos> parsePositions(JsonArray array) {
        List<BlockPos> result = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                result.add(parsePos(element.getAsString()));
            }
        }
        return List.copyOf(result);
    }

    private static void nullablePos(JsonObject out, String key, BlockPos position) {
        if (position == null) {
            out.add(key, null);
        } else {
            out.addProperty(key, pos(position));
        }
    }

    private static void nullable(JsonObject out, String key, String value) {
        if (value == null) {
            out.add(key, null);
        } else {
            out.addProperty(key, value);
        }
    }

    private static BlockPos readNullablePos(JsonElement element) {
        return element == null || element.isJsonNull()
                ? null : parsePos(element.getAsString());
    }

    private static String nullableString(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String pos(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static boolean descentObserved(
            BlockPos entrance, double x, double y, double z) {
        // The carpet is one connected false floor: a mob that ends up two-plus blocks below
        // the carpet plane within the trap's small footprint can only have fallen through one
        // of its powder entrances (the surrounding envelope is certified solid, and the climb-
        // out stair rises the other way). Crediting only the single pre-selected column made
        // the watch flaky whenever the zombie stepped onto a neighbouring entrance cell first.
        return Math.abs(x - (entrance.getX() + 0.5D)) <= 4.0D
                && Math.abs(z - (entrance.getZ() + 0.5D)) <= 4.0D
                && y <= entrance.getY() - 2.0D;
    }

    private static String entityPosition(Mob mob) {
        return String.format(
                Locale.ROOT, "%.3f,%.3f,%.3f", mob.getX(), mob.getY(), mob.getZ());
    }

    private static BlockPos parsePos(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("bad position " + raw);
        }
        return new BlockPos(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
    }

    private static String key(CaveDropTrapFeature.AuditEvent event) {
        return pos(event.canonicalAnchor());
    }

    private static long columnKey(int x, int z) {
        return ((long) z << 32) | (x & 0xffffffffL);
    }

    private static int unpackX(long key) {
        return (int) key;
    }

    private static int unpackZ(long key) {
        return (int) (key >> 32);
    }

    private static String unsignedHex(Long value) {
        return value == null ? null : Long.toUnsignedString(value, 16);
    }

    private static long parseUnsignedHex(String value) {
        return Long.parseUnsignedLong(value, 16);
    }
}
