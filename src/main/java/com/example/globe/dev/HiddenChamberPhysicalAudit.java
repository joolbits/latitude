package com.example.globe.dev;

import com.example.globe.GlobeMod;
import com.example.globe.core.HiddenChamberPlan;
import com.example.globe.core.HiddenChamberScan;
import com.example.globe.core.PolarBarrensBand;
import com.example.globe.world.CaveTrapBlocks;
import com.example.globe.world.HiddenGlacialChamberFeature;
import com.example.globe.world.IcicleBlocks;
import com.example.globe.world.LatitudeBiomes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Development-only fresh-world PHYSICAL proof for {@code globe:hidden_glacial_chamber}.
 *
 * <p>{@link HiddenGlacialChamberFeature} claims it wrote chambers and {@link HiddenChamberPlan} claims what
 * one should look like. This harness believes neither. It asks a real server to generate a declared chunk
 * window, lets the world settle, and then reconstructs every chamber back out of the standing BLOCKS through
 * {@link HiddenChamberScan} -- the same pure reconstruction the shipped locator command uses. A counter is
 * evidence only of what the generator believed; the report's verdict rests on cells that were read.
 *
 * <p>Beyond the reconstruction it proves four things a block sweep alone cannot:
 * <ul>
 *   <li><b>Biome containment</b> -- every quart the chamber occupies is really {@code globe:glacial_caves},
 *       read off the SETTLED stored quart ({@code chunk.getNoiseBiome}) rather than
 *       {@code LevelReader#getBiome}, whose fuzzy eight-quart sampler can answer for a neighbour.</li>
 *   <li><b>The mob assay</b> -- one real server-owned zombie is pathed across the concealed mouth and must be
 *       observed BELOW it, inside the chamber's own void box. A trap nothing falls into is not a trap.</li>
 *   <li><b>The locator cross-check</b> -- each completed chamber is re-classified through a FRESH live-chunk
 *       reader, so the shipped locator's read path is shown to reproduce the audit's physical verdict.</li>
 *   <li><b>Preserved gates</b> -- the magma quench, the glacial lakes and the ice spires that shipped in
 *       earlier rounds are re-measured in the same window, so a chamber round cannot quietly undo them.</li>
 * </ul>
 *
 * <p>Everything is fail-closed: the report is born {@code "failed"} and only becomes {@code "ok"} when every
 * APPLICABLE assertion holds; a cell no chunk can answer for is {@link HiddenChamberScan.ScanCell#UNREADABLE}
 * and never a guess; and the JSON is written on every path, including the throwing one, before the server is
 * halted. This class lives in the dev package and is stripped from playable jars.
 */
public final class HiddenChamberPhysicalAudit {

    static final String PREFIX = "latdev.chamberAudit";
    static final String SCHEMA = "hidden-chamber-physical-audit-v1";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /* ---------------------------------------------------------------------------------------------------- */
    /* Preserved-gate thresholds                                                                             */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * The quench band, re-derived rather than imported: {@code MagmaQuenchSweepFeature.SCAN_BOTTOM_Y} and
     * {@code SCAN_TOP_Y} are package-visible inside {@code com.example.globe.world} and this audit lives in
     * {@code com.example.globe.dev}, so the bottom is rebuilt from the very {@link PolarBarrensBand}
     * constants the sweep itself uses and the top mirrors the sweep's own literal. If the sweep ever retunes
     * its band these two lines are the one place that must follow.
     */
    static final int MAGMA_SCAN_BOTTOM_Y =
            PolarBarrensBand.ICE_BODY_FLOOR_Y - PolarBarrensBand.PERMAFROST_BAND_BLOCKS;
    static final int MAGMA_SCAN_TOP_Y = 100;

    /** A spire is a run of standing ice this tall; shorter stacks are wall or floor dressing. */
    static final int SPIRE_MIN_RUN = 5;
    /**
     * How many of a spire's four horizontal sides must be open for it to be free-standing. Mirrors
     * {@code HiddenChamberScan}'s own private {@code INTERIOR_ICE_MIN_OPEN_SIDES}: the chamber's wall seal is
     * packed ice too, and a wall is not a spire.
     */
    static final int SPIRE_MIN_OPEN_SIDES = 3;
    /** "Reaches the gallery roof": base-to-ceiling clearance beyond this counts as a tall spire. */
    static final int SPIRE_CEILING_BEYOND = 32;
    /** Bounds the lake-shore flood fill on a window that is one continuous frozen coast. */
    private static final int LAKE_MAX_SHORE_CELLS = 200_000;

    /* ---------------------------------------------------------------------------------------------------- */
    /* Mob assay                                                                                             */
    /* ---------------------------------------------------------------------------------------------------- */

    /** How long the descent watch runs before the assay gives up. */
    static final int MOB_ASSAY_TICK_LIMIT = 400;
    private static final double MOB_NAVIGATION_SPEED = 1.0D;
    /**
     * How far under the mouth floor the zombie must be seen. The authored fall is at least
     * {@link HiddenChamberPlan#DROP_MIN} blocks, so eight blocks is unambiguously "through the false floor"
     * and not "standing in a dip beside it", while still crediting a mob caught on the cushion.
     */
    static final int MOB_DESCENT_BELOW_MOUTH = 8;
    /** How far from the mouth footprint an approach stand cell is looked for. */
    private static final int APPROACH_SEARCH_RADIUS = 4;
    /** Ticks between re-assertions of the far-side walk target; the zombie's own goals fight the path. */
    private static final int ASSAY_REPATH_INTERVAL = 20;

    /** How far around a mouth the locator cross-check re-sweeps for its own collapse patch. */
    private static final int LOCATOR_NEIGHBOURHOOD_XZ = 8;
    private static final int LOCATOR_NEIGHBOURHOOD_Y = 4;

    private static final int GENERATION_LOG_INTERVAL = 32;

    private static Run activeRun;
    private static boolean tickHookRegistered;

    private HiddenChamberPhysicalAudit() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(PREFIX, "false"));
    }

    /** SERVER_STARTED entry. Generation runs on the server thread; the rest is tick-driven. */
    public static void start(MinecraftServer server) {
        server.execute(() -> begin(server));
    }

    private static void begin(MinecraftServer server) {
        Path fallback = server.getServerDirectory().resolve("latdev")
                .resolve("hidden-chamber-physical-audit.json").toAbsolutePath().normalize();
        Config config = Config.read(fallback);
        if (!config.errors().isEmpty()) {
            JsonObject report = baseReport();
            report.add("config", config.toJson());
            fail(report, "config", config.errors());
            try {
                write(config.out(), report);
            } catch (Throwable writeFailure) {
                GlobeMod.LOGGER.error("[latdev][hidden-chamber-audit] could not write the config report",
                        writeFailure);
            }
            GlobeMod.LOGGER.error("[latdev][hidden-chamber-audit] invalid configuration: {}", config.errors());
            server.halt(false);
            return;
        }

        Run run = new Run(server, config);
        activeRun = run;
        try {
            run.generate();
        } catch (Throwable failure) {
            activeRun = null;
            run.abortAndHalt("full-chunk-generation", failure);
            return;
        }
        run.pendingTicks = config.settleTicks();
        if (!tickHookRegistered) {
            tickHookRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(HiddenChamberPhysicalAudit::onServerTick);
        }
        GlobeMod.LOGGER.info("[latdev][hidden-chamber-audit] prepared scan=({},{}) span={} target=({},{}) "
                        + "span={} settleTicks={} mobAssay={} locatorCheck={} out={}",
                config.scanChunkMinX(), config.scanChunkMinZ(), config.scanChunkSpan(),
                config.targetChunkMinX(), config.targetChunkMinZ(), config.targetChunkSpan(),
                config.settleTicks(), config.mobAssay(), config.locatorCheck(), config.out());
    }

    private static void onServerTick(MinecraftServer server) {
        Run run = activeRun;
        if (run == null || run.server != server) {
            return;
        }
        try {
            if (run.pendingTicks-- > 0) {
                return;
            }
            if (!run.reconstructed) {
                run.reconstruct();
                if (run.beginMobAssay()) {
                    return; // the assay now owns a live mob and must be advanced by later ticks
                }
            } else if (!run.tickMobAssay()) {
                return;
            }
            activeRun = null;
            run.completeAndHalt();
        } catch (Throwable failure) {
            activeRun = null;
            run.abortAndHalt(run.stage, failure);
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* The run                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    private static final class Run {

        private final MinecraftServer server;
        private final ServerLevel world;
        private final Config config;
        private final String sessionId;

        private final List<String> generationFailures = new ArrayList<>();
        private final List<ChunkPos> forcedChunks = new ArrayList<>();
        private int requestedChunkCount;
        private int generatedFullChunks;

        private HiddenGlacialChamberFeature.Counters counters;
        private HiddenGlacialChamberFeature.WriteTelemetry.Session writeTelemetry;
        private boolean telemetryOpen;

        private HiddenChamberScan.ChamberScanReport scan;
        private List<HiddenChamberScan.Completed> completedInTarget = List.of();
        private final JsonArray partialDetails = new JsonArray();
        private final List<ChamberEvidence> chamberEvidence = new ArrayList<>();
        private int loadedScanChunks;

        private MobAssay mobAssay = MobAssay.skipped("the audit has not reached the navigation assay");
        private Mob assayMob;
        private BlockPos assayTargetFeet;
        private int assayRepathCooldown;
        private int assayTicksRemaining;
        private HiddenChamberScan.Completed assayChamber;
        private VoidBox assayVoidBox;

        private LocatorCheck locatorCheck = LocatorCheck.skipped();
        private MagmaGate magmaGate = MagmaGate.notRun();
        private LakeCensus lakeCensus = LakeCensus.notRun();
        private SpireCensus spireCensus = SpireCensus.notRun();

        private boolean reconstructed;
        private int pendingTicks;
        /** The phase currently running, so a throw names the stage it really died in. */
        private String stage = "setup";
        private String failureStage;
        private final List<String> errors = new ArrayList<>();

        private Run(MinecraftServer server, Config config) {
            this.server = server;
            this.config = config;
            this.world = server.overworld();
            if (world == null) {
                throw new IllegalStateException("no overworld");
            }
            this.sessionId = "hidden-chamber-physical-audit-" + System.nanoTime();
        }

        /* ------------------------------------------------------------------------------------------------ */
        /* 1-2. Begin + generate                                                                             */
        /* ------------------------------------------------------------------------------------------------ */

        private void generate() {
            stage = "full-chunk-generation";
            HiddenGlacialChamberFeature.resetCounters();
            HiddenGlacialChamberFeature.WriteTelemetry.beginSession(sessionId);
            telemetryOpen = true;

            List<ChunkCoordinate> requested = requestedChunks(
                    config.scanChunkMinX(), config.scanChunkMinZ(), config.scanChunkSpan());
            requestedChunkCount = requested.size();
            int index = 0;
            for (ChunkCoordinate chunk : requested) {
                ChunkAccess generated =
                        world.getChunkSource().getChunk(chunk.x(), chunk.z(), ChunkStatus.FULL, true);
                if (generated == null) {
                    generationFailures.add("FULL chunk unavailable " + chunk.x() + "," + chunk.z());
                } else {
                    generatedFullChunks++;
                    // The reconstruction reads only RESIDENT chunks and never regenerates mid-sweep, and the
                    // settle window is only meaningful over chunks the server is really ticking. Both need the
                    // window pinned for the length of the run; every ticket is released in the terminal
                    // finally, exactly like the assay's own 3x3.
                    if (world.setChunkForced(chunk.x(), chunk.z(), true)) {
                        forcedChunks.add(new ChunkPos(chunk.x(), chunk.z()));
                    }
                }
                if (++index % GENERATION_LOG_INTERVAL == 0 || index == requested.size()) {
                    GlobeMod.LOGGER.info("[latdev][hidden-chamber-audit] generated {}/{} chunks",
                            index, requested.size());
                }
            }
        }

        /* ------------------------------------------------------------------------------------------------ */
        /* 4-6. Reconstruct, counters, biome containment                                                     */
        /* ------------------------------------------------------------------------------------------------ */

        private void reconstruct() {
            reconstructed = true;
            stage = "physical-scan";

            AuditCellReader reader = newReader();
            HiddenChamberScan.Bounds bounds = scanBounds();
            scan = HiddenChamberScan.scan(reader, bounds);
            loadedScanChunks = reader.loadedChunks();

            closeTelemetry();
            counters = HiddenGlacialChamberFeature.countersSnapshot();

            List<HiddenChamberScan.Completed> inTarget = new ArrayList<>();
            for (HiddenChamberScan.Completed chamber : scan.completed()) {
                if (inTargetSquare(chamber.mouthCentroid())) {
                    inTarget.add(chamber);
                }
            }
            completedInTarget = List.copyOf(inTarget);

            for (HiddenChamberScan.Completed chamber : completedInTarget) {
                chamberEvidence.add(measureChamber(chamber));
            }

            // Diagnosis aid (2026-08-03, architect edit): serialize every partial's position and reason, and
            // for NO_EXIT partials census fluid cells inside the exit-route search space. A written chamber
            // that reconstructs to the void but fails its exit needs a visible cause in the report, not just
            // a tally. Read-only; uses a fresh reader so the scan's own caching is untouched.
            AuditCellReader diagnosisReader = newReader();
            for (HiddenChamberScan.Partial partial : scan.partial()) {
                JsonObject row = new JsonObject();
                row.addProperty("mouth", partial.mouthCentroid().x() + ","
                        + partial.mouthCentroid().y() + "," + partial.mouthCentroid().z());
                row.addProperty("reason", partial.reason().name());
                row.addProperty("inTarget", inTargetSquare(partial.mouthCentroid()));
                if (partial.reason() == HiddenChamberScan.PartialReason.NO_EXIT) {
                    int mx = partial.mouthCentroid().x();
                    int my = partial.mouthCentroid().y();
                    int mz = partial.mouthCentroid().z();
                    int fluidCells = 0;
                    int fluidBelowMouth = 0;
                    int highestFluidY = Integer.MIN_VALUE;
                    for (int dx = -34; dx <= 34; dx++) {
                        for (int dz = -34; dz <= 34; dz++) {
                            for (int y = Math.max(1, my - 25); y <= my + 6; y++) {
                                HiddenChamberScan.ScanCell cell = diagnosisReader.cell(mx + dx, y, mz + dz);
                                if (cell == HiddenChamberScan.ScanCell.WATER
                                        || cell == HiddenChamberScan.ScanCell.OTHER_FLUID) {
                                    fluidCells++;
                                    if (y < my - 2) {
                                        fluidBelowMouth++;
                                    }
                                    if (y > highestFluidY) {
                                        highestFluidY = y;
                                    }
                                }
                            }
                        }
                    }
                    row.addProperty("routeSpaceFluidCells", fluidCells);
                    row.addProperty("routeSpaceFluidBelowMouth", fluidBelowMouth);
                    if (highestFluidY != Integer.MIN_VALUE) {
                        row.addProperty("highestFluidY", highestFluidY);
                    }
                    // Phantom-solid census: cells the scan classifies OTHER_SOLID whose real blockstate has an
                    // EMPTY collision shape (glow lichen, wall decorations). A player walks straight through
                    // them, but a naive classifier reads them as corridor blockers. Tally by block id.
                    java.util.Map<String, Integer> phantomIds = new java.util.TreeMap<>();
                    int phantomCells = 0;
                    for (int dx = -34; dx <= 34; dx++) {
                        for (int dz = -34; dz <= 34; dz++) {
                            for (int y = Math.max(1, my - 25); y <= my + 6; y++) {
                                if (diagnosisReader.cell(mx + dx, y, mz + dz)
                                        != HiddenChamberScan.ScanCell.OTHER_SOLID) {
                                    continue;
                                }
                                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(mx + dx, y, mz + dz);
                                net.minecraft.world.level.block.state.BlockState state = world.getBlockState(pos);
                                if (state.getCollisionShape(world, pos).isEmpty()) {
                                    phantomCells++;
                                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                            .getKey(state.getBlock()).toString();
                                    phantomIds.merge(id, 1, Integer::sum);
                                }
                            }
                        }
                    }
                    row.addProperty("phantomSolidCells", phantomCells);
                    JsonObject phantomJson = new JsonObject();
                    phantomIds.forEach(phantomJson::addProperty);
                    row.add("phantomSolidsByBlock", phantomJson);

                    // Exit-walk trace: re-classify just this patch with the scanner narrating its own walk.
                    java.util.List<HiddenChamberScan.Position> patchCells = new java.util.ArrayList<>();
                    for (int dx = -4; dx <= 4; dx++) {
                        for (int dz = -4; dz <= 4; dz++) {
                            for (int dy = -2; dy <= 2; dy++) {
                                if (diagnosisReader.cell(mx + dx, my + dy, mz + dz)
                                        == HiddenChamberScan.ScanCell.COLLAPSE_POWDER) {
                                    patchCells.add(new HiddenChamberScan.Position(mx + dx, my + dy, mz + dz));
                                }
                            }
                        }
                    }
                    JsonArray traceJson = new JsonArray();
                    java.util.List<String> traceLines = new java.util.ArrayList<>();
                    HiddenChamberScan.EXIT_TRACE = traceLines::add;
                    try {
                        HiddenChamberScan.classifyPatch(newReader(), patchCells);
                    } finally {
                        HiddenChamberScan.EXIT_TRACE = null;
                    }
                    traceLines.forEach(traceJson::add);
                    row.add("exitTrace", traceJson);

                    // Ground-truth layers: one character per cell over mouth±16, a few bands around the
                    // landing so a failed exit's surroundings are readable straight from the report.
                    int approxLanding = highestFluidY != Integer.MIN_VALUE ? highestFluidY + 1 : my - 14;
                    JsonObject layers = new JsonObject();
                    for (int y : new int[]{approxLanding - 1, approxLanding, approxLanding + 1,
                            approxLanding + 3, approxLanding + 6, approxLanding + 9}) {
                        if (y < 1) {
                            continue;
                        }
                        StringBuilder grid = new StringBuilder();
                        for (int dz = -16; dz <= 16; dz++) {
                            for (int dx = -16; dx <= 16; dx++) {
                                grid.append(glyph(diagnosisReader.cell(mx + dx, y, mz + dz)));
                            }
                            grid.append('\n');
                        }
                        layers.addProperty("y" + y, grid.toString());
                    }
                    row.add("layers", layers);
                }
                partialDetails.add(row);
            }

            GlobeMod.LOGGER.info("[latdev][hidden-chamber-audit] reconstruction completed={} inTarget={} "
                            + "partial={} legacy={} collapseCells={} loadedScanChunks={}",
                    scan.completedCount(), completedInTarget.size(), scan.partialCount(),
                    scan.legacyCount(), scan.collapseCells(), loadedScanChunks);
        }

        /** One readable character per {@link HiddenChamberScan.ScanCell} for the diagnosis layer dumps. */
        private static char glyph(HiddenChamberScan.ScanCell cell) {
            return switch (cell) {
                case AIR -> '.';
                case COLLAPSE_POWDER -> 'M';
                case POWDER_SNOW -> 'p';
                case SNOW_FIRM -> 's';
                case ICE_PACKED -> 'P';
                case ICE_BLUE -> 'B';
                case ICE_PLAIN -> 'i';
                case ICICLE -> 'v';
                case BONE -> 'b';
                case WATER -> '~';
                case LANTERN -> 'L';
                case CHEST -> 'C';
                case WOOD_DEBRIS -> 'w';
                case OTHER_SOLID -> '#';
                case OTHER_FLUID -> '%';
                case UNREADABLE -> '?';
            };
        }

        private void closeTelemetry() {
            if (!telemetryOpen) {
                return;
            }
            telemetryOpen = false;
            writeTelemetry = HiddenGlacialChamberFeature.WriteTelemetry.endSession(sessionId);
        }

        private boolean inTargetSquare(HiddenChamberScan.Position mouth) {
            int chunkX = Math.floorDiv(mouth.x(), 16);
            int chunkZ = Math.floorDiv(mouth.z(), 16);
            return chunkX >= config.targetChunkMinX()
                    && chunkX < config.targetChunkMinX() + config.targetChunkSpan()
                    && chunkZ >= config.targetChunkMinZ()
                    && chunkZ < config.targetChunkMinZ() + config.targetChunkSpan();
        }

        /**
         * Biome containment and the above-Y0 law for one chamber.
         *
         * <p>Every quart the chamber overlaps -- its mouth cells, its landing, its second opening, and the
         * corners and centre of its own void box -- must read {@code globe:glacial_caves} off the SETTLED
         * stored quart. {@code LevelReader#getBiome} is deliberately not used: its fuzzy eight-quart sampler
         * can answer for a neighbouring quart at an exact boundary, which is the same trap the magma sweep
         * documents.
         */
        private ChamberEvidence measureChamber(HiddenChamberScan.Completed chamber) {
            VoidBox box = voidBox(chamber);
            List<BlockPos> samples = new ArrayList<>();
            for (HiddenChamberScan.Position cell : chamber.mouthCells()) {
                samples.add(new BlockPos(cell.x(), cell.y(), cell.z()));
            }
            samples.add(new BlockPos(chamber.landing().x(), chamber.landing().y(), chamber.landing().z()));
            samples.add(new BlockPos(chamber.exitOpening().x(), chamber.exitOpening().y(),
                    chamber.exitOpening().z()));
            // Biome containment is a law about AUTHORED cells only (mouth, landing, exit — the anchors the
            // scan proves were built). The void ANALYSIS box is deliberately not sampled: incorporated
            // natural cave air may legally stretch past the authored envelope into foreign quarts
            // (2026-08-03: a gate-1 chamber's box corner reached a minecraft:frozen_peaks quart 18 blocks
            // beyond its writes and false-REDed the old corner sampling). Same principle as the Y check
            // below. The plan-time probe already rejects any WRITE cell in a non-glacial quart, fail-closed.

            Set<Long> seenQuarts = new HashSet<>();
            boolean allGlacial = true;
            int measured = 0;
            String firstOffender = null;
            for (BlockPos sample : samples) {
                long quart = quartKey(sample);
                if (!seenQuarts.add(quart)) {
                    continue;
                }
                measured++;
                String biome = storedQuartBiome(sample.getX(), sample.getY(), sample.getZ());
                if (!LatitudeBiomes.GLACIAL_CAVES_ID.equals(biome)) {
                    allGlacial = false;
                    if (firstOffender == null) {
                        firstOffender = pos(sample) + "=" + (biome == null ? "unreadable" : biome);
                    }
                }
            }

            // The S49 cellar ruling, measured on cells that were actually authored. The void ANALYSIS box is
            // deliberately not part of this test: its floor is landing minus (LAKE_MAX_DIG + 1), so a legal
            // chamber landing at the planner's own MIN_LANDING_Y puts the box horizon at Y-1 and asserting on
            // it would fail a chamber that never wrote a block down there. The box floor is reported instead.
            int lowest = Math.min(Math.min(chamber.mouthCentroid().y(), chamber.landing().y()),
                    chamber.exitOpening().y());
            for (HiddenChamberScan.Position cell : chamber.mouthCells()) {
                lowest = Math.min(lowest, cell.y());
            }
            boolean aboveY0 = lowest >= HiddenChamberPlan.MIN_AUTHORED_Y
                    && chamber.landing().y() >= HiddenChamberPlan.MIN_LANDING_Y;

            return new ChamberEvidence(chamber, box, allGlacial, measured, firstOffender, aboveY0, lowest);
        }

        /**
         * The scan's own void analysis box, rebuilt from its PUBLIC constants: the box is anchored on the
         * mouth centroid horizontally and on the landing vertically, and is capped one block under the mouth
         * floor. {@code HiddenChamberScan} keeps the below-landing depth private, so it is re-derived from
         * {@link HiddenChamberPlan#LAKE_MAX_DIG} (the lake floor plus its bed) exactly as the scan does.
         */
        private VoidBox voidBox(HiddenChamberScan.Completed chamber) {
            int centreX = chamber.mouthCentroid().x();
            int centreZ = chamber.mouthCentroid().z();
            int landingY = chamber.landing().y();
            int mouthFloorY = chamber.mouthCentroid().y();
            int minX = centreX - HiddenChamberScan.VOID_BOX_X / 2;
            int maxX = minX + HiddenChamberScan.VOID_BOX_X - 1;
            int minZ = centreZ - HiddenChamberScan.VOID_BOX_Z / 2;
            int maxZ = minZ + HiddenChamberScan.VOID_BOX_Z - 1;
            int minY = landingY - (HiddenChamberPlan.LAKE_MAX_DIG + 1);
            int maxY = Math.min(minY + HiddenChamberScan.VOID_BOX_Y - 1, mouthFloorY - 1);
            return new VoidBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        /* ------------------------------------------------------------------------------------------------ */
        /* 7. Mob assay                                                                                      */
        /* ------------------------------------------------------------------------------------------------ */

        /**
         * Path one real, server-owned zombie ACROSS the concealed mouth of the nearest completed chamber.
         *
         * <p>Returning {@code true} means the assay now owns a live mob and must be advanced by later server
         * ticks. The walk target is deliberately the FAR side of the mouth, not the mouth itself: a
         * pathfinder treats the collapse cell as blocked support and will settle for the nearest node beside
         * it, so aiming at the mouth stalls the mob on the lip. The selected path must additionally contain a
         * node standing ON a mouth cell -- a route that walks around the hole proves nothing.
         */
        private boolean beginMobAssay() {
            stage = "mob-assay";
            if (!config.mobAssay()) {
                mobAssay = MobAssay.skipped("latdev.chamberAudit.mobAssay=false");
                return false;
            }
            if (completedInTarget.isEmpty()) {
                mobAssay = MobAssay.skipped("no completed chamber stands inside the target square");
                return false;
            }

            HiddenChamberScan.Completed chamber = nearestCompleted();
            VoidBox box = voidBox(chamber);
            int mouthFloorY = chamber.mouthCentroid().y();
            Mob candidate = null;
            try {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", "zombie"));
                Object created = type == null ? null : type.create(world, EntitySpawnReason.COMMAND);
                if (!(created instanceof Mob mob)) {
                    mobAssay = MobAssay.blocked(pos(chamber.mouthCentroid()),
                            "minecraft:zombie did not create a normal Mob");
                    return false;
                }
                candidate = mob;

                AuditCellReader reader = newReader();
                List<BlockPos> approaches = approachStandCells(reader, chamber);
                if (approaches.isEmpty()) {
                    mob.discard();
                    mobAssay = MobAssay.blocked(pos(chamber.mouthCentroid()),
                            "no walkable approach cell stands beside the mouth patch");
                    return false;
                }

                // Force the mouth neighbourhood: nothing holds tickets on an audited chunk once generation
                // ends, so an added entity would otherwise be unloaded within a few ticks.
                forceAround(new BlockPos(chamber.mouthCentroid().x(), mouthFloorY,
                        chamber.mouthCentroid().z()));

                for (BlockPos start : approaches) {
                    BlockPos target = farSideTarget(reader, chamber, start);
                    world.getChunk(start);
                    world.getChunk(target);
                    mob.setPos(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
                    // A created-but-unticked entity reports onGround=false and the navigator refuses to path
                    // for an airborne mob -- the exact reason an earlier assay never selected any path.
                    mob.setOnGround(true);
                    net.minecraft.world.level.pathfinder.Path path =
                            mob.getNavigation().createPath(target, 0);
                    if (path == null) {
                        continue;
                    }
                    BlockPos entrance = entranceNode(path, chamber.mouthCells());
                    if (entrance == null) {
                        continue;
                    }
                    mob.setInvulnerable(true);
                    mob.setPersistenceRequired();
                    if (!world.addFreshEntity(mob)) {
                        mob.discard();
                        mobAssay = new MobAssay("BLOCKED", pos(chamber.mouthCentroid()), pos(start),
                                pos(target), path.getNodeCount(), pos(entrance), false, 0, null,
                                "the selected zombie could not be added to the server world");
                        return false;
                    }
                    assayMob = mob;
                    assayChamber = chamber;
                    assayVoidBox = box;
                    assayTargetFeet = target;
                    assayTicksRemaining = MOB_ASSAY_TICK_LIMIT;
                    assayRepathCooldown = ASSAY_REPATH_INTERVAL;
                    if (!mob.getNavigation().moveTo(path, MOB_NAVIGATION_SPEED)) {
                        mobAssay = new MobAssay("FAIL", pos(chamber.mouthCentroid()), pos(start), pos(target),
                                path.getNodeCount(), pos(entrance), false, 0, entityPosition(mob),
                                "the server-added zombie rejected its selected path");
                        discardAssayMob();
                        return false;
                    }
                    mobAssay = new MobAssay("RUNNING", pos(chamber.mouthCentroid()), pos(start), pos(target),
                            path.getNodeCount(), pos(entrance), false, 0, entityPosition(mob),
                            "server-added zombie is walking the selected mouth-crossing path");
                    return true;
                }
                mob.discard();
                mobAssay = new MobAssay("FAIL", pos(chamber.mouthCentroid()), null, null, 0, null,
                        false, 0, null,
                        "no real zombie path from any approach cell crossed a mouth cell");
                return false;
            } catch (Throwable failure) {
                if (candidate != null && candidate != assayMob) {
                    candidate.discard();
                }
                discardAssayMob();
                mobAssay = MobAssay.blocked(pos(chamber.mouthCentroid()), describe(failure));
                return false;
            }
        }

        /**
         * Advance the live assay by one genuine server tick. A selected path is not a pass: the zombie's own
         * feet must be observed {@link #MOB_DESCENT_BELOW_MOUTH} blocks under the mouth floor and inside the
         * chamber's void box -- it genuinely fell the shaft.
         */
        private boolean tickMobAssay() {
            Mob mob = assayMob;
            if (mob == null || assayChamber == null) {
                return true;
            }
            BlockPos target = assayTargetFeet;
            if (!mob.isRemoved() && target != null) {
                assayRepathCooldown--;
                if (mob.getNavigation().isDone() || assayRepathCooldown <= 0) {
                    mob.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                            MOB_NAVIGATION_SPEED);
                    assayRepathCooldown = ASSAY_REPATH_INTERVAL;
                }
                mob.getLookControl().setLookAt(
                        target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
            }
            int ticksObserved = mobAssay.ticksObserved() + 1;
            String finalPosition = entityPosition(mob);
            if (descended(mob)) {
                mobAssay = mobAssay.finish("PASS", true, ticksObserved, finalPosition,
                        "server-added zombie fell at least " + MOB_DESCENT_BELOW_MOUTH
                                + " blocks through the mouth into the chamber void box");
                discardAssayMob();
                return true;
            }
            if (mob.isRemoved()) {
                mobAssay = mobAssay.finish("FAIL", false, ticksObserved, finalPosition,
                        "the server-added zombie was removed before it fell through the mouth");
                discardAssayMob();
                return true;
            }
            if (--assayTicksRemaining <= 0) {
                mobAssay = mobAssay.finish("FAIL", false, ticksObserved, finalPosition,
                        "the server-added zombie did not fall through the mouth within "
                                + MOB_ASSAY_TICK_LIMIT + " server ticks");
                discardAssayMob();
                return true;
            }
            mobAssay = mobAssay.running(ticksObserved, finalPosition);
            return false;
        }

        /** Feet under the mouth floor AND horizontally inside the chamber's own void box. */
        private boolean descended(Mob mob) {
            if (assayChamber == null || assayVoidBox == null) {
                return false;
            }
            double y = mob.getY();
            double x = mob.getX();
            double z = mob.getZ();
            return y <= assayChamber.mouthCentroid().y() - MOB_DESCENT_BELOW_MOUTH
                    && x >= assayVoidBox.minX() && x <= assayVoidBox.maxX() + 1
                    && z >= assayVoidBox.minZ() && z <= assayVoidBox.maxZ() + 1;
        }

        private HiddenChamberScan.Completed nearestCompleted() {
            return completedInTarget.stream()
                    .min(Comparator.<HiddenChamberScan.Completed>comparingLong(chamber -> {
                        long dx = (long) chamber.mouthCentroid().x() - config.centerX();
                        long dz = (long) chamber.mouthCentroid().z() - config.centerZ();
                        return dx * dx + dz * dz;
                    }).thenComparing(HiddenChamberScan.Completed::mouthCentroid))
                    .orElseThrow();
        }

        /**
         * Walkable cells beside the mouth patch, nearest first. A stand cell is firm footing on the SAME cave
         * floor plane as the mouth with two clear blocks over it, at least one block clear of every collapse
         * cell so the zombie starts on real ground rather than on the false floor.
         */
        private List<BlockPos> approachStandCells(AuditCellReader reader,
                                                  HiddenChamberScan.Completed chamber) {
            int floorY = chamber.mouthCentroid().y();
            int centreX = chamber.mouthCentroid().x();
            int centreZ = chamber.mouthCentroid().z();
            Set<Long> mouthColumns = new HashSet<>();
            for (HiddenChamberScan.Position cell : chamber.mouthCells()) {
                mouthColumns.add(columnKey(cell.x(), cell.z()));
            }
            List<BlockPos> found = new ArrayList<>();
            for (int dx = -APPROACH_SEARCH_RADIUS; dx <= APPROACH_SEARCH_RADIUS; dx++) {
                for (int dz = -APPROACH_SEARCH_RADIUS; dz <= APPROACH_SEARCH_RADIUS; dz++) {
                    int x = centreX + dx;
                    int z = centreZ + dz;
                    if (mouthColumns.contains(columnKey(x, z))) {
                        continue;
                    }
                    if (standCell(reader, x, floorY, z)) {
                        found.add(new BlockPos(x, floorY + 1, z));
                    }
                }
            }
            found.sort(Comparator
                    .<BlockPos>comparingInt(cell -> Math.max(Math.abs(cell.getX() - centreX),
                            Math.abs(cell.getZ() - centreZ)))
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getZ));
            return List.copyOf(found);
        }

        /** The mirror of the start through the mouth centroid: a target the walk can only reach by crossing. */
        private BlockPos farSideTarget(AuditCellReader reader, HiddenChamberScan.Completed chamber,
                                       BlockPos start) {
            int floorY = chamber.mouthCentroid().y();
            int mirroredX = 2 * chamber.mouthCentroid().x() - start.getX();
            int mirroredZ = 2 * chamber.mouthCentroid().z() - start.getZ();
            BlockPos best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int dx = -APPROACH_SEARCH_RADIUS; dx <= APPROACH_SEARCH_RADIUS; dx++) {
                for (int dz = -APPROACH_SEARCH_RADIUS; dz <= APPROACH_SEARCH_RADIUS; dz++) {
                    int x = mirroredX + dx;
                    int z = mirroredZ + dz;
                    if (!standCell(reader, x, floorY, z)) {
                        continue;
                    }
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new BlockPos(x, floorY + 1, z);
                    }
                }
            }
            return best != null ? best : new BlockPos(mirroredX, floorY + 1, mirroredZ);
        }

        private static boolean standCell(AuditCellReader reader, int x, int floorY, int z) {
            return reader.cell(x, floorY, z).isFloor()
                    && reader.cell(x, floorY + 1, z).isAir()
                    && reader.cell(x, floorY + 2, z).isAir();
        }

        /** The path must stand ON the false floor, not walk around it. Mirrors the drop-trap entrance law. */
        private static BlockPos entranceNode(net.minecraft.world.level.pathfinder.Path path,
                                             List<HiddenChamberScan.Position> mouthCells) {
            Map<Long, Integer> mouthY = new HashMap<>();
            for (HiddenChamberScan.Position cell : mouthCells) {
                mouthY.put(columnKey(cell.x(), cell.z()), cell.y());
            }
            for (int index = 0; index < path.getNodeCount(); index++) {
                Node node = path.getNode(index);
                Integer top = mouthY.get(columnKey(node.x, node.z));
                if (top != null && (node.y == top || node.y == top + 1)) {
                    return new BlockPos(node.x, node.y, node.z);
                }
            }
            return null;
        }

        private void forceAround(BlockPos centre) {
            ChunkPos chunk = ChunkPos.containing(centre);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos forced = new ChunkPos(chunk.x() + dx, chunk.z() + dz);
                    if (world.setChunkForced(forced.x(), forced.z(), true)) {
                        forcedChunks.add(forced);
                    }
                }
            }
        }

        private void discardAssayMob() {
            if (assayMob != null && !assayMob.isRemoved()) {
                assayMob.discard();
            }
            assayMob = null;
            assayTargetFeet = null;
        }

        private void releaseForcedChunks() {
            for (ChunkPos forced : forcedChunks) {
                try {
                    world.setChunkForced(forced.x(), forced.z(), false);
                } catch (RuntimeException ignored) {
                    // A halt already under way must never be blocked by a ticket release.
                }
            }
            forcedChunks.clear();
        }

        /* ------------------------------------------------------------------------------------------------ */
        /* 8. Locator cross-check                                                                            */
        /* ------------------------------------------------------------------------------------------------ */

        /**
         * Re-classify every completed chamber through a FRESH live-chunk reader, re-grouping its collapse
         * patch from scratch in a small neighbourhood exactly as the shipped locator does. A verdict that
         * survives a second, independent read proves the locator points at chambers that PHYSICALLY stand,
         * not at a planner's prediction cached by the first sweep.
         */
        private void runLocatorCheck() {
            if (!config.locatorCheck()) {
                locatorCheck = LocatorCheck.skipped();
                return;
            }
            List<LocatorVerdict> verdicts = new ArrayList<>();
            boolean consistent = true;
            for (HiddenChamberScan.Completed chamber : completedInTarget) {
                // A brand-new reader: no memoised cell, no cached chunk, nothing carried over from the sweep.
                AuditCellReader fresh = newReader();
                HiddenChamberScan.Position centroid = chamber.mouthCentroid();
                List<HiddenChamberScan.Position> collapse = new ArrayList<>();
                for (int x = centroid.x() - LOCATOR_NEIGHBOURHOOD_XZ;
                        x <= centroid.x() + LOCATOR_NEIGHBOURHOOD_XZ; x++) {
                    for (int y = centroid.y() - LOCATOR_NEIGHBOURHOOD_Y;
                            y <= centroid.y() + LOCATOR_NEIGHBOURHOOD_Y; y++) {
                        for (int z = centroid.z() - LOCATOR_NEIGHBOURHOOD_XZ;
                                z <= centroid.z() + LOCATOR_NEIGHBOURHOOD_XZ; z++) {
                            if (fresh.cell(x, y, z) == HiddenChamberScan.ScanCell.COLLAPSE_POWDER) {
                                collapse.add(new HiddenChamberScan.Position(x, y, z));
                            }
                        }
                    }
                }
                Set<HiddenChamberScan.Position> wanted = new TreeSet<>(chamber.mouthCells());
                List<HiddenChamberScan.Position> patch = null;
                for (List<HiddenChamberScan.Position> candidate
                        : HiddenChamberScan.groupCollapsePatches(collapse)) {
                    if (candidate.stream().anyMatch(wanted::contains)) {
                        patch = candidate;
                        break;
                    }
                }
                if (patch == null) {
                    consistent = false;
                    verdicts.add(new LocatorVerdict(pos(centroid), false,
                            "the fresh reader found no collapse patch at this mouth"));
                    continue;
                }
                HiddenChamberScan.PatchOutcome outcome = HiddenChamberScan.classifyPatch(fresh, patch);
                if (!(outcome instanceof HiddenChamberScan.Completed repeat)) {
                    consistent = false;
                    verdicts.add(new LocatorVerdict(pos(centroid), false,
                            "the fresh reader reclassified this chamber as " + describeOutcome(outcome)));
                    continue;
                }
                boolean identical = repeat.mouthCentroid().equals(chamber.mouthCentroid())
                        && repeat.landing().equals(chamber.landing())
                        && repeat.exitOpening().equals(chamber.exitOpening())
                        && repeat.themeGuess() == chamber.themeGuess()
                        && repeat.drop() == chamber.drop()
                        && repeat.voidVolume() == chamber.voidVolume()
                        && repeat.bends() == chamber.bends();
                if (!identical) {
                    consistent = false;
                }
                verdicts.add(new LocatorVerdict(pos(centroid), identical, identical
                        ? "identical Completed verdict and coordinates"
                        : "the fresh reader returned a different Completed: mouth=" + pos(repeat.mouthCentroid())
                                + " landing=" + pos(repeat.landing())
                                + " exit=" + pos(repeat.exitOpening())
                                + " theme=" + repeat.themeGuess()
                                + " drop=" + repeat.drop()
                                + " void=" + repeat.voidVolume()
                                + " bends=" + repeat.bends()));
            }
            locatorCheck = new LocatorCheck(true, consistent, List.copyOf(verdicts));
        }

        private static String describeOutcome(HiddenChamberScan.PatchOutcome outcome) {
            if (outcome instanceof HiddenChamberScan.Partial partial) {
                return "Partial(" + partial.reason() + ")";
            }
            if (outcome instanceof HiddenChamberScan.Legacy legacy) {
                return "Legacy(" + legacy.patchSize() + " cells)";
            }
            return "an unknown outcome";
        }

        /* ------------------------------------------------------------------------------------------------ */
        /* 9. Preserved gates                                                                                */
        /* ------------------------------------------------------------------------------------------------ */

        /**
         * One traversal of the resident scan window that re-measures the three shipped rounds this one must
         * not have disturbed: the magma quench, the glacial lakes, and the ice spires. Only the quench and
         * the open-air spire count are assertions; the lake census is reported for the eye.
         */
        private void runPreservedGates() {
            int minX = config.scanChunkMinX() << 4;
            int minZ = config.scanChunkMinZ() << 4;
            int width = config.scanChunkSpan() << 4;
            int worldMinY = world.getMinY();
            int worldMaxY = world.getMinY() + world.getHeight() - 1;

            int magmaScanned = 0;
            int magmaResidual = 0;
            int lakeCores = 0;
            int spireTotal = 0;
            int spireBeyond = 0;
            int spireOpenAir = 0;
            Set<Long> shoreIce = new LinkedHashSet<>();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

            for (int x = minX; x < minX + width; x++) {
                for (int z = minZ; z < minZ + width; z++) {
                    ChunkAccess chunk = loadedChunk(x >> 4, z >> 4);
                    if (chunk == null) {
                        continue;
                    }
                    int surfaceY = Math.min(
                            chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1, worldMaxY);
                    int bottom = Math.max(worldMinY + 1, MAGMA_SCAN_BOTTOM_Y);
                    int top = Math.max(bottom, surfaceY);
                    int runLength = 0;
                    int runBaseY = 0;
                    for (int y = bottom; y <= top; y++) {
                        cursor.set(x, y, z);
                        BlockState state = chunk.getBlockState(cursor);

                        /* Magma quench residue. */
                        if (y <= MAGMA_SCAN_TOP_Y && state.is(Blocks.MAGMA_BLOCK)
                                && isGlacialHost(x, y, z)) {
                            magmaScanned++;
                            if (magmaResidue(x, y, z)) {
                                magmaResidual++;
                            }
                        }

                        /* Lake census: open-water cores and the shore ice beside them. */
                        if (y > PolarBarrensBand.ICE_BODY_FLOOR_Y && isWater(state)) {
                            if (waterCore(x, y, z)) {
                                lakeCores++;
                            }
                        } else if (state.is(Blocks.ICE) && touchesWater(x, y, z)
                                && shoreIce.size() < LAKE_MAX_SHORE_CELLS
                                && LatitudeBiomes.GLACIAL_CAVES_ID.equals(storedQuartBiome(x, y, z))) {
                            shoreIce.add(BlockPos.asLong(x, y, z));
                        }

                        /* Spire runs: standing packed/blue ice, measured base to ceiling. */
                        boolean standingIce = state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
                        if (standingIce) {
                            if (runLength == 0) {
                                runBaseY = y;
                            }
                            runLength++;
                            if (y < top) {
                                continue;
                            }
                        }
                        if (runLength >= SPIRE_MIN_RUN) {
                            int runTopY = standingIce ? y : y - 1;
                            SpireVerdict verdict = classifySpire(x, runBaseY, runTopY, z, worldMaxY);
                            switch (verdict) {
                                case ROOFED_NEAR -> spireTotal++;
                                case ROOFED_TALL -> {
                                    spireTotal++;
                                    spireBeyond++;
                                }
                                case OPEN_AIR -> spireOpenAir++;
                                case NOT_A_SPIRE -> {
                                }
                            }
                        }
                        runLength = 0;
                    }
                }
            }

            magmaGate = new MagmaGate(true, magmaScanned, magmaResidual);
            lakeCensus = new LakeCensus(true, lakeCores, shoreGroups(shoreIce));
            spireCensus = new SpireCensus(true, spireTotal, spireBeyond, spireOpenAir);
            GlobeMod.LOGGER.info("[latdev][hidden-chamber-audit] preserved gates magmaScanned={} residual={} "
                            + "lakeCores={} shoreGroups={} spires={} beyond{}={} openAir={}",
                    magmaScanned, magmaResidual, lakeCores, lakeCensus.shoreGroups(),
                    spireTotal, SPIRE_CEILING_BEYOND, spireBeyond, spireOpenAir);
        }

        /**
         * A quenched pocket never leaves magma FACE-touching water or ice: the flooded shell turns both to
         * obsidian and the dry shell melts the face ice to air. Either survivor is unquenched residue.
         */
        private boolean magmaResidue(int x, int y, int z) {
            int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] face : faces) {
                BlockState neighbour = blockAt(x + face[0], y + face[1], z + face[2]);
                if (neighbour == null) {
                    continue;
                }
                if (isWater(neighbour) || isIceFamily(neighbour)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The min corner of a 2x2 of open water with air above it: a lake surface, not a flooded crack. The
         * cheap block tests run FIRST and the stored-quart lookup only on a surviving core -- the window holds
         * millions of water cells and a biome read per cell would dominate the whole gate pass.
         */
        private boolean waterCore(int x, int y, int z) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    BlockState state = blockAt(x + dx, y, z + dz);
                    if (state == null || !isWater(state)) {
                        return false;
                    }
                    BlockState above = blockAt(x + dx, y + 1, z + dz);
                    if (above == null || !above.isAir()) {
                        return false;
                    }
                }
            }
            return LatitudeBiomes.GLACIAL_CAVES_ID.equals(storedQuartBiome(x, y, z));
        }

        private boolean touchesWater(int x, int y, int z) {
            int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] face : faces) {
                BlockState neighbour = blockAt(x + face[0], y + face[1], z + face[2]);
                if (neighbour != null && isWater(neighbour)) {
                    return true;
                }
            }
            return false;
        }

        /** Six-connected components of shore ice; a coastline is one shore, not five hundred blocks. */
        private static int shoreGroups(Set<Long> cells) {
            Set<Long> remaining = new HashSet<>(cells);
            int groups = 0;
            int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (long seed : cells) {
                if (!remaining.remove(seed)) {
                    continue;
                }
                groups++;
                ArrayDeque<Long> queue = new ArrayDeque<>();
                queue.add(seed);
                while (!queue.isEmpty()) {
                    long key = queue.removeFirst();
                    BlockPos cell = BlockPos.of(key);
                    for (int[] step : steps) {
                        long neighbour = BlockPos.asLong(
                                cell.getX() + step[0], cell.getY() + step[1], cell.getZ() + step[2]);
                        if (remaining.remove(neighbour)) {
                            queue.addLast(neighbour);
                        }
                    }
                }
            }
            return groups;
        }

        private enum SpireVerdict {
            NOT_A_SPIRE,
            ROOFED_NEAR,
            ROOFED_TALL,
            OPEN_AIR
        }

        /**
         * Is this run of standing ice a spire, and does it reach a roof? A spire is rooted on a floor, free
         * standing on at least {@link #SPIRE_MIN_OPEN_SIDES} sides at mid height (a wall seal is packed ice
         * too), and open above its tip. A run whose upward scan never meets a ceiling stands under the sky.
         *
         * <p>The census is confined to this mod's own host biomes. Vanilla's {@code ice_spikes} builds real
         * free-standing packed-ice pillars under open sky by design, so an unfiltered sweep would report a
         * neighbouring vanilla biome clipping the window as an open-air regression in OUR spires.
         */
        private SpireVerdict classifySpire(int x, int baseY, int topY, int z, int worldMaxY) {
            BlockState root = blockAt(x, baseY - 1, z);
            if (root == null || root.isAir() || isWater(root)) {
                return SpireVerdict.NOT_A_SPIRE;
            }
            BlockState tipAbove = blockAt(x, topY + 1, z);
            if (tipAbove == null || !tipAbove.isAir()) {
                return SpireVerdict.NOT_A_SPIRE;
            }
            int midY = baseY + (topY - baseY) / 2;
            int open = 0;
            int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] side : sides) {
                BlockState neighbour = blockAt(x + side[0], midY, z + side[1]);
                if (neighbour != null && (neighbour.isAir() || isWater(neighbour))) {
                    open++;
                }
            }
            if (open < SPIRE_MIN_OPEN_SIDES) {
                return SpireVerdict.NOT_A_SPIRE;
            }
            if (!isGlacialHost(x, midY, z)) {
                return SpireVerdict.NOT_A_SPIRE;
            }
            for (int y = topY + 1; y <= worldMaxY; y++) {
                BlockState above = blockAt(x, y, z);
                if (above == null) {
                    return SpireVerdict.NOT_A_SPIRE; // unreadable overhead is never proof of open sky
                }
                if (!above.isAir()) {
                    return y - baseY > SPIRE_CEILING_BEYOND
                            ? SpireVerdict.ROOFED_TALL
                            : SpireVerdict.ROOFED_NEAR;
                }
            }
            return SpireVerdict.OPEN_AIR;
        }

        /* ------------------------------------------------------------------------------------------------ */
        /* Resident world reads                                                                              */
        /* ------------------------------------------------------------------------------------------------ */

        private final Map<Long, ChunkAccess> residentChunks = new HashMap<>();
        private final Set<Long> absentChunks = new HashSet<>();
        /** One cursor for the gate pass; {@code getBlockState} never retains the position it is handed. */
        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

        /** Resident chunks only. This audit reads what generation left standing and never regenerates. */
        private ChunkAccess loadedChunk(int chunkX, int chunkZ) {
            long key = columnKey(chunkX, chunkZ);
            ChunkAccess cached = residentChunks.get(key);
            if (cached != null) {
                return cached;
            }
            if (absentChunks.contains(key)) {
                return null;
            }
            ChunkAccess chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                absentChunks.add(key);
                return null;
            }
            residentChunks.put(key, chunk);
            return chunk;
        }

        private BlockState blockAt(int x, int y, int z) {
            if (y < world.getMinY() || y > world.getMinY() + world.getHeight() - 1) {
                return null;
            }
            ChunkAccess chunk = loadedChunk(x >> 4, z >> 4);
            return chunk == null ? null : chunk.getBlockState(scratch.set(x, y, z));
        }

        /**
         * The SETTLED stored quart, read straight off the chunk. {@code LevelReader#getBiome} is deliberately
         * avoided: its fuzzy eight-quart sampler can answer for a neighbouring quart at an exact boundary.
         */
        private String storedQuartBiome(int x, int y, int z) {
            ChunkAccess chunk = loadedChunk(x >> 4, z >> 4);
            if (chunk == null) {
                return null;
            }
            try {
                Holder<Biome> biome = chunk.getNoiseBiome(
                        QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
                return biome == null ? null
                        : biome.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
            } catch (RuntimeException unreadable) {
                return null;
            }
        }

        private boolean isGlacialHost(int x, int y, int z) {
            String biome = storedQuartBiome(x, y, z);
            return LatitudeBiomes.GLACIAL_CAVES_ID.equals(biome)
                    || LatitudeBiomes.POLAR_BARRENS_ID.equals(biome);
        }

        private AuditCellReader newReader() {
            return new AuditCellReader(world,
                    config.scanChunkMinX() << 4,
                    config.scanChunkMinZ() << 4,
                    config.scanChunkSpan() << 4);
        }

        private HiddenChamberScan.Bounds scanBounds() {
            int minX = config.scanChunkMinX() << 4;
            int minZ = config.scanChunkMinZ() << 4;
            int width = config.scanChunkSpan() << 4;
            // The collapse mouth can only sit in the authored cave-floor band, so the sweep never walks the
            // whole column: HiddenChamberPlan's own floor window IS the horizon.
            return new HiddenChamberScan.Bounds(
                    minX, HiddenChamberPlan.MOUTH_FLOOR_MIN_Y, minZ,
                    minX + width - 1, HiddenChamberPlan.MOUTH_FLOOR_MAX_Y, minZ + width - 1);
        }

        /* ------------------------------------------------------------------------------------------------ */
        /* 10. Report                                                                                        */
        /* ------------------------------------------------------------------------------------------------ */

        private void completeAndHalt() {
            stage = "locator-check";
            try {
                runLocatorCheck();
                stage = "preserved-gates";
                runPreservedGates();
            } catch (Throwable failure) {
                failStage(stage, failure);
            } finally {
                writeAndHalt();
            }
        }

        private void abortAndHalt(String stage, Throwable failure) {
            failStage(stage, failure);
            GlobeMod.LOGGER.error("[latdev][hidden-chamber-audit] failed during {}", stage, failure);
            writeAndHalt();
        }

        private void failStage(String stage, Throwable failure) {
            if (failureStage == null) {
                failureStage = stage;
            }
            errors.add(describe(failure));
        }

        private void writeAndHalt() {
            try {
                closeTelemetry();
                discardAssayMob();
                JsonObject report = buildReport();
                try {
                    write(config.out(), report);
                    GlobeMod.LOGGER.info("[latdev][hidden-chamber-audit] status={} completedInTarget={} "
                                    + "mobAssay={} report={}",
                            report.get("status").getAsString(), completedInTarget.size(),
                            mobAssay.status(), config.out());
                } catch (Throwable writeFailure) {
                    GlobeMod.LOGGER.error("[latdev][hidden-chamber-audit] could not write the report",
                            writeFailure);
                }
            } catch (Throwable reportFailure) {
                GlobeMod.LOGGER.error("[latdev][hidden-chamber-audit] could not assemble the report",
                        reportFailure);
            } finally {
                releaseForcedChunks();
                server.halt(false);
            }
        }

        private JsonObject buildReport() {
            JsonObject report = baseReport();
            report.add("config", config.toJson());
            report.addProperty("seed", world.getSeed());
            report.addProperty("levelName", server.getWorldData().getLevelName());
            if (failureStage != null) {
                report.addProperty("failureStage", failureStage);
            }
            if (!errors.isEmpty()) {
                JsonArray array = new JsonArray();
                errors.forEach(array::add);
                report.add("errors", array);
            }

            /* counts */
            JsonObject counts = new JsonObject();
            counts.addProperty("requestedChunks", requestedChunkCount);
            counts.addProperty("generatedFullChunks", generatedFullChunks);
            counts.addProperty("residentScanChunks", loadedScanChunks);
            counts.addProperty("completed", scan == null ? 0 : scan.completedCount());
            counts.addProperty("completedInTarget", completedInTarget.size());
            JsonObject partials = new JsonObject();
            for (HiddenChamberScan.PartialReason reason : HiddenChamberScan.PartialReason.values()) {
                int tally = scan == null ? 0 : (int) scan.partial().stream()
                        .filter(partial -> partial.reason() == reason).count();
                partials.addProperty(reason.name(), tally);
            }
            counts.add("partialsByReason", partials);
            counts.addProperty("legacy", scan == null ? 0 : scan.legacyCount());
            JsonObject featureCounters = new JsonObject();
            if (counters != null) {
                featureCounters.addProperty("noEntrance", counters.noEntrance());
                featureCounters.addProperty("unsafeVolume", counters.unsafeVolume());
                featureCounters.addProperty("noExit", counters.noExit());
                featureCounters.addProperty("rarityRejected", counters.rarityRejected());
                featureCounters.addProperty("completedChamber", counters.completedChamber());
                featureCounters.addProperty("abortedRolledBack", counters.abortedRolledBack());
            }
            counts.add("featureCounters", featureCounters);
            JsonObject telemetry = new JsonObject();
            boolean telemetryAvailable = writeTelemetry != null && writeTelemetry.available();
            telemetry.addProperty("available", telemetryAvailable);
            telemetry.addProperty("source", "HiddenGlacialChamberFeature.place");
            if (telemetryAvailable) {
                telemetry.addProperty("sessionId", writeTelemetry.sessionId());
                telemetry.addProperty("attempts", writeTelemetry.attempts());
                telemetry.addProperty("appliedSuccesses", writeTelemetry.appliedSuccesses());
                telemetry.addProperty("failedWriteBatches", writeTelemetry.failedWriteBatches());
                telemetry.addProperty("rolledBackWriteBatches", writeTelemetry.rolledBackWriteBatches());
            }
            counts.add("writeTelemetry", telemetry);
            report.add("counts", counts);

            /* chambers */
            JsonArray chambers = new JsonArray();
            for (ChamberEvidence evidence : chamberEvidence) {
                chambers.add(evidence.toJson());
            }
            report.add("chambers", chambers);
            report.add("partials", partialDetails);

            /* mob assay, locator, preserved gates */
            report.add("mobAssay", mobAssay.toJson());
            report.add("locatorCheck", locatorCheck.toJson());
            report.add("magma", magmaGate.toJson());
            report.add("lake", lakeCensus.toJson());
            report.add("spires", spireCensus.toJson());
            JsonArray failures = new JsonArray();
            generationFailures.forEach(failures::add);
            report.add("generationFailures", failures);

            /* assertions */
            boolean allChunks = requestedChunkCount > 0
                    && generatedFullChunks == requestedChunkCount
                    && generationFailures.isEmpty();
            boolean minCompleted = completedInTarget.size() >= config.minCompleted();
            boolean everyAboveY0 = !chamberEvidence.isEmpty()
                    && chamberEvidence.stream().allMatch(ChamberEvidence::aboveY0);
            boolean everyGlacial = !chamberEvidence.isEmpty()
                    && chamberEvidence.stream().allMatch(ChamberEvidence::quartsAllGlacial);
            Boolean assayPass = mobAssay.applicable() ? "PASS".equals(mobAssay.status()) : null;
            Boolean locatorConsistent = locatorCheck.applicable() ? locatorCheck.consistent() : null;
            boolean zeroFailed = telemetryAvailable && writeTelemetry.failedWriteBatches() == 0;
            boolean zeroRolledBack = telemetryAvailable && writeTelemetry.rolledBackWriteBatches() == 0;
            boolean magmaZero = magmaGate.ran() && magmaGate.residual() == 0;
            boolean noOpenAir = spireCensus.ran() && spireCensus.openAir() == 0;
            Boolean tallSpires = config.expectGallerySpires()
                    ? Boolean.valueOf(spireCensus.ran() && spireCensus.beyond32() >= 1)
                    : null;
            Boolean themeMatched = config.expectTheme() == null ? null : Boolean.valueOf(
                    !completedInTarget.isEmpty() && completedInTarget.stream()
                            .allMatch(chamber -> chamber.themeGuess() == config.expectedTheme()));

            JsonObject assertions = new JsonObject();
            assertions.addProperty("allRequestedChunksFull", allChunks);
            assertions.addProperty("atLeastMinCompletedInTarget", minCompleted);
            assertions.addProperty("everyChamberAboveY0", everyAboveY0);
            assertions.addProperty("everyChamberAllGlacialQuarts", everyGlacial);
            addNullable(assertions, "mobAssayPass", assayPass);
            addNullable(assertions, "locatorConsistent", locatorConsistent);
            assertions.addProperty("zeroFailedWriteBatches", zeroFailed);
            assertions.addProperty("zeroRolledBackWriteBatches", zeroRolledBack);
            assertions.addProperty("magmaResidualZero", magmaZero);
            assertions.addProperty("noOpenAirSpires", noOpenAir);
            addNullable(assertions, "gallerySpiresBeyond32", tallSpires);
            addNullable(assertions, "expectedThemeMatched", themeMatched);
            report.add("assertions", assertions);

            boolean ok = failureStage == null && errors.isEmpty()
                    && allChunks && minCompleted && everyAboveY0 && everyGlacial
                    && (assayPass == null || assayPass)
                    && (locatorConsistent == null || locatorConsistent)
                    && zeroFailed && zeroRolledBack && magmaZero && noOpenAir
                    && (tallSpires == null || tallSpires)
                    && (themeMatched == null || themeMatched);
            report.addProperty("status", ok ? "ok" : "failed");
            return report;
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Cell reader                                                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * The audit's block-to-{@link HiddenChamberScan.ScanCell} adapter, mirroring the shipped locator's
     * {@code ChamberCellReader} vocabulary exactly so both read the same world the same way.
     *
     * <p>Two deliberate differences: this reader NEVER generates -- a chunk that is not already resident is
     * {@link HiddenChamberScan.ScanCell#UNREADABLE}, so the audit measures what generation left standing and
     * never quietly manufactures the terrain it is judging -- and it answers UNREADABLE outside the declared
     * scan square, so a chamber that leans out of the window fails closed instead of being half-read.
     */
    static final class AuditCellReader implements HiddenChamberScan.CellReader {

        private final ServerLevel world;
        private final int minX;
        private final int minZ;
        private final int width;
        private final int minY;
        private final int maxY;
        private final Map<Long, ChunkAccess> chunks = new HashMap<>();
        private final Set<Long> unavailable = new HashSet<>();

        AuditCellReader(ServerLevel world, int minX, int minZ, int width) {
            this.world = world;
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.minY = world.getMinY();
            this.maxY = world.getMinY() + world.getHeight() - 1;
        }

        int loadedChunks() {
            return chunks.size();
        }

        @Override
        public HiddenChamberScan.ScanCell cell(int worldX, int y, int worldZ) {
            if (y < minY || y > maxY
                    || worldX < minX || worldX >= minX + width
                    || worldZ < minZ || worldZ >= minZ + width) {
                return HiddenChamberScan.ScanCell.UNREADABLE;
            }
            ChunkAccess chunk = chunkAt(worldX >> 4, worldZ >> 4);
            if (chunk == null) {
                return HiddenChamberScan.ScanCell.UNREADABLE;
            }
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            return classify(chunk.getBlockState(pos), pos);
        }

        private ChunkAccess chunkAt(int chunkX, int chunkZ) {
            long key = columnKey(chunkX, chunkZ);
            ChunkAccess cached = chunks.get(key);
            if (cached != null) {
                return cached;
            }
            if (unavailable.contains(key)) {
                return null;
            }
            ChunkAccess chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                unavailable.add(key);
                return null;
            }
            chunks.put(key, chunk);
            return chunk;
        }

        private HiddenChamberScan.ScanCell classify(BlockState state, BlockPos pos) {
            if (CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW != null
                    && state.is(CaveTrapBlocks.CAVE_TRAP_POWDER_SNOW)) {
                return HiddenChamberScan.ScanCell.COLLAPSE_POWDER;
            }
            if (state.is(Blocks.POWDER_SNOW)) {
                return HiddenChamberScan.ScanCell.POWDER_SNOW;
            }
            if (state.is(Blocks.SNOW_BLOCK)) {
                return HiddenChamberScan.ScanCell.SNOW_FIRM;
            }
            if (state.is(Blocks.PACKED_ICE)) {
                return HiddenChamberScan.ScanCell.ICE_PACKED;
            }
            if (state.is(Blocks.BLUE_ICE)) {
                return HiddenChamberScan.ScanCell.ICE_BLUE;
            }
            if (state.is(Blocks.ICE)) {
                return HiddenChamberScan.ScanCell.ICE_PLAIN;
            }
            if (IcicleBlocks.ICICLE != null && state.is(IcicleBlocks.ICICLE)) {
                return HiddenChamberScan.ScanCell.ICICLE;
            }
            if (state.is(Blocks.BONE_BLOCK)) {
                return HiddenChamberScan.ScanCell.BONE;
            }
            if (state.is(Blocks.LANTERN)) {
                return HiddenChamberScan.ScanCell.LANTERN;
            }
            if (state.is(Blocks.CHEST)) {
                return HiddenChamberScan.ScanCell.CHEST;
            }
            if (state.is(Blocks.SPRUCE_FENCE)) {
                return HiddenChamberScan.ScanCell.WOOD_DEBRIS;
            }
            if (state.isAir()) {
                return HiddenChamberScan.ScanCell.AIR;
            }
            if (!state.getFluidState().isEmpty()) {
                return state.getFluidState().is(FluidTags.WATER)
                        ? HiddenChamberScan.ScanCell.WATER
                        : HiddenChamberScan.ScanCell.OTHER_FLUID;
            }
            if (state.getCollisionShape(world, pos).isEmpty()) {
                return HiddenChamberScan.ScanCell.AIR; // walk-through dressing is clear space to a player
            }
            return HiddenChamberScan.ScanCell.OTHER_SOLID;
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Evidence records                                                                                      */
    /* ---------------------------------------------------------------------------------------------------- */

    record VoidBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private record ChamberEvidence(HiddenChamberScan.Completed chamber,
                                   VoidBox voidBox,
                                   boolean quartsAllGlacial,
                                   int measuredQuarts,
                                   String firstNonGlacialQuart,
                                   boolean aboveY0,
                                   int lowestY) {

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("mouth", pos(chamber.mouthCentroid()));
            json.addProperty("landing", pos(chamber.landing()));
            json.addProperty("exit", pos(chamber.exitOpening()));
            json.addProperty("theme", chamber.themeGuess().name());
            json.addProperty("drop", chamber.drop());
            json.addProperty("voidVolume", chamber.voidVolume());
            json.addProperty("bends", chamber.bends());
            json.addProperty("quartsAllGlacial", quartsAllGlacial);
            json.addProperty("measuredQuarts", measuredQuarts);
            if (firstNonGlacialQuart != null) {
                json.addProperty("firstNonGlacialQuart", firstNonGlacialQuart);
            }
            json.addProperty("aboveY0", aboveY0);
            json.addProperty("lowestY", lowestY);
            JsonObject box = new JsonObject();
            box.addProperty("min", voidBox.minX() + "," + voidBox.minY() + "," + voidBox.minZ());
            box.addProperty("max", voidBox.maxX() + "," + voidBox.maxY() + "," + voidBox.maxZ());
            json.add("voidBox", box);
            return json;
        }
    }

    private record MobAssay(String status, String sceneMouth, String start, String target,
                            int pathNodeCount, String entranceHit, boolean descentObserved,
                            int ticksObserved, String finalPosition, String detail) {

        static MobAssay skipped(String detail) {
            return new MobAssay("SKIPPED", null, null, null, 0, null, false, 0, null, detail);
        }

        static MobAssay blocked(String sceneMouth, String detail) {
            return new MobAssay("BLOCKED", sceneMouth, null, null, 0, null, false, 0, null, detail);
        }

        boolean applicable() {
            return !"SKIPPED".equals(status);
        }

        MobAssay running(int ticks, String finalPosition) {
            return new MobAssay("RUNNING", sceneMouth, start, target, pathNodeCount, entranceHit,
                    false, ticks, finalPosition, detail);
        }

        MobAssay finish(String status, boolean descended, int ticks, String finalPosition, String detail) {
            return new MobAssay(status, sceneMouth, start, target, pathNodeCount, entranceHit,
                    descended, ticks, finalPosition, detail);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("status", status);
            addNullableString(json, "sceneMouth", sceneMouth);
            addNullableString(json, "start", start);
            addNullableString(json, "target", target);
            json.addProperty("pathNodeCount", pathNodeCount);
            addNullableString(json, "entranceHit", entranceHit);
            json.addProperty("descentObserved", descentObserved);
            json.addProperty("ticksObserved", ticksObserved);
            addNullableString(json, "finalPosition", finalPosition);
            json.addProperty("detail", detail);
            return json;
        }
    }

    private record LocatorVerdict(String mouth, boolean consistent, String detail) {
    }

    private record LocatorCheck(boolean applicable, boolean consistent, List<LocatorVerdict> perChamber) {

        static LocatorCheck skipped() {
            return new LocatorCheck(false, false, List.of());
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("applicable", applicable);
            if (applicable) {
                json.addProperty("consistent", consistent);
            } else {
                json.add("consistent", JsonNull.INSTANCE);
            }
            JsonArray rows = new JsonArray();
            for (LocatorVerdict verdict : perChamber) {
                JsonObject row = new JsonObject();
                row.addProperty("mouth", verdict.mouth());
                row.addProperty("consistent", verdict.consistent());
                row.addProperty("detail", verdict.detail());
                rows.add(row);
            }
            json.add("perChamber", rows);
            return json;
        }
    }

    private record MagmaGate(boolean ran, int scanned, int residual) {

        static MagmaGate notRun() {
            return new MagmaGate(false, 0, 0);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("ran", ran);
            json.addProperty("bottomY", MAGMA_SCAN_BOTTOM_Y);
            json.addProperty("topY", MAGMA_SCAN_TOP_Y);
            json.addProperty("scanned", scanned);
            json.addProperty("residual", residual);
            return json;
        }
    }

    private record LakeCensus(boolean ran, int cores, int shoreGroups) {

        static LakeCensus notRun() {
            return new LakeCensus(false, 0, 0);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("ran", ran);
            json.addProperty("reportOnly", true);
            json.addProperty("cores", cores);
            json.addProperty("shoreGroups", shoreGroups);
            return json;
        }
    }

    private record SpireCensus(boolean ran, int total, int beyond32, int openAir) {

        static SpireCensus notRun() {
            return new SpireCensus(false, 0, 0, 0);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("ran", ran);
            json.addProperty("minRun", SPIRE_MIN_RUN);
            json.addProperty("total", total);
            json.addProperty("beyond32", beyond32);
            json.addProperty("openAir", openAir);
            return json;
        }
    }

    record ChunkCoordinate(int x, int z) {
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Config                                                                                                */
    /* ---------------------------------------------------------------------------------------------------- */

    record Config(int targetChunkMinX,
                  int targetChunkMinZ,
                  int targetChunkSpan,
                  int scanChunkMinX,
                  int scanChunkMinZ,
                  int scanChunkSpan,
                  int centerX,
                  int centerZ,
                  int radiusChunks,
                  Path out,
                  int settleTicks,
                  boolean mobAssay,
                  boolean locatorCheck,
                  String expectTheme,
                  int minCompleted,
                  boolean expectGallerySpires,
                  List<String> errors) {

        static final int DEFAULT_SETTLE_TICKS = 100;
        static final int DEFAULT_MIN_COMPLETED = 1;

        static Config read(Path fallbackOut) {
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
            int settleTicks = optionalInt("settleTicks", DEFAULT_SETTLE_TICKS, errors);
            int minCompleted = optionalInt("minCompleted", DEFAULT_MIN_COMPLETED, errors);
            boolean mobAssay = optionalBoolean("mobAssay", true, errors);
            boolean locatorCheck = optionalBoolean("locatorCheck", true, errors);
            boolean expectGallerySpires = optionalBoolean("expectGallerySpires", false, errors);
            String expectTheme = optionalTheme(errors);

            String rawOut = System.getProperty(PREFIX + ".out", "").trim();
            if (rawOut.isEmpty()) {
                errors.add(PREFIX + ".out is required");
            }
            Path out = rawOut.isEmpty() ? fallbackOut : Path.of(rawOut);
            return validated(targetMinX, targetMinZ, targetSpan, scanMinX, scanMinZ, scanSpan,
                    centerX, centerZ, radius, out, settleTicks, mobAssay, locatorCheck,
                    expectTheme, minCompleted, expectGallerySpires, errors);
        }

        static Config validated(int targetMinX, int targetMinZ, int targetSpan,
                                int scanMinX, int scanMinZ, int scanSpan,
                                int centerX, int centerZ, int radius, Path out,
                                int settleTicks, boolean mobAssay, boolean locatorCheck,
                                String expectTheme, int minCompleted, boolean expectGallerySpires) {
            return validated(targetMinX, targetMinZ, targetSpan, scanMinX, scanMinZ, scanSpan,
                    centerX, centerZ, radius, out, settleTicks, mobAssay, locatorCheck,
                    expectTheme, minCompleted, expectGallerySpires, new ArrayList<>());
        }

        private static Config validated(int targetMinX, int targetMinZ, int targetSpan,
                                        int scanMinX, int scanMinZ, int scanSpan,
                                        int centerX, int centerZ, int radius, Path out,
                                        int settleTicks, boolean mobAssay, boolean locatorCheck,
                                        String expectTheme, int minCompleted,
                                        boolean expectGallerySpires, List<String> errors) {
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
            if (settleTicks < 0 || settleTicks > 24_000) {
                errors.add("settleTicks must be 0..24000");
            }
            if (minCompleted < 0) {
                errors.add("minCompleted must be non-negative");
            }
            return new Config(targetMinX, targetMinZ, targetSpan, scanMinX, scanMinZ, scanSpan,
                    centerX, centerZ, radius, out.toAbsolutePath().normalize(), settleTicks,
                    mobAssay, locatorCheck, expectTheme, minCompleted, expectGallerySpires,
                    List.copyOf(errors));
        }

        int targetChunkCount() {
            return targetChunkSpan * targetChunkSpan;
        }

        int scanChunkCount() {
            return scanChunkSpan * scanChunkSpan;
        }

        /** The scan Theme the {@code expectTheme} name selects, or {@code null} when the knob is unset. */
        HiddenChamberScan.Theme expectedTheme() {
            if (expectTheme == null) {
                return null;
            }
            return switch (expectTheme) {
                case "ice_cathedral" -> HiddenChamberScan.Theme.ICE_CATHEDRAL;
                case "frigid_lake" -> HiddenChamberScan.Theme.FRIGID_LAKE;
                case "lost_expedition" -> HiddenChamberScan.Theme.LOST_EXPEDITION;
                default -> null;
            };
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
            json.addProperty("settleTicks", settleTicks);
            json.addProperty("mobAssay", mobAssay);
            json.addProperty("locatorCheck", locatorCheck);
            addNullableString(json, "expectTheme", expectTheme);
            json.addProperty("minCompleted", minCompleted);
            json.addProperty("expectGallerySpires", expectGallerySpires);
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

        /** A blank forward is the DEFAULT, never zero: build.gradle forwards every knob with an empty value. */
        private static int optionalInt(String suffix, int fallback, List<String> errors) {
            String key = PREFIX + "." + suffix;
            String raw = System.getProperty(key, "").trim();
            if (raw.isEmpty()) {
                return fallback;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException malformed) {
                errors.add(key + " must be an integer");
                return fallback;
            }
        }

        /**
         * A blank forward is the DEFAULT, never {@code false}. {@code Boolean.parseBoolean("")} is false, so a
         * naive read would silently disarm every default-ON knob the moment build.gradle forwarded it empty.
         */
        private static boolean optionalBoolean(String suffix, boolean fallback, List<String> errors) {
            String key = PREFIX + "." + suffix;
            String raw = System.getProperty(key, "").trim().toLowerCase(Locale.ROOT);
            if (raw.isEmpty()) {
                return fallback;
            }
            if (raw.equals("true")) {
                return true;
            }
            if (raw.equals("false")) {
                return false;
            }
            errors.add(key + " must be true or false");
            return fallback;
        }

        private static String optionalTheme(List<String> errors) {
            String key = PREFIX + ".expectTheme";
            String raw = System.getProperty(key, "").trim().toLowerCase(Locale.ROOT);
            if (raw.isEmpty()) {
                return null;
            }
            if (!raw.equals("ice_cathedral") && !raw.equals("frigid_lake")
                    && !raw.equals("lost_expedition")) {
                errors.add(key + " must be ice_cathedral, frigid_lake, or lost_expedition");
                return null;
            }
            return raw;
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Shared helpers                                                                                        */
    /* ---------------------------------------------------------------------------------------------------- */

    static List<ChunkCoordinate> requestedChunks(int minX, int minZ, int span) {
        List<ChunkCoordinate> chunks = new ArrayList<>(Math.max(0, span * span));
        for (int z = minZ; z < minZ + span; z++) {
            for (int x = minX; x < minX + span; x++) {
                chunks.add(new ChunkCoordinate(x, z));
            }
        }
        return List.copyOf(chunks);
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

    private static void addNullable(JsonObject json, String key, Boolean value) {
        if (value == null) {
            json.add(key, JsonNull.INSTANCE);
        } else {
            json.addProperty(key, value);
        }
    }

    private static void addNullableString(JsonObject json, String key, String value) {
        if (value == null) {
            json.add(key, JsonNull.INSTANCE);
        } else {
            json.addProperty(key, value);
        }
    }

    private static boolean isWater(BlockState state) {
        return state.is(Blocks.WATER) || state.getFluidState().is(FluidTags.WATER);
    }

    private static boolean isIceFamily(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK);
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Dedupe key for a sampled quart. Signed-safe: the standard block packing over quart coordinates. */
    private static long quartKey(BlockPos pos) {
        return BlockPos.asLong(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ()));
    }

    private static String pos(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static String pos(HiddenChamberScan.Position position) {
        return position.x() + "," + position.y() + "," + position.z();
    }

    private static String entityPosition(Mob mob) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", mob.getX(), mob.getY(), mob.getZ());
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
