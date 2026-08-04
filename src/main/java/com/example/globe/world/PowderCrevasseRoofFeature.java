package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.CaveDropTrap;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.SubterraneanTrapLayout;
import com.example.globe.core.SubterraneanTrapPlan;
import com.mojang.serialization.Codec;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Bounded Polar Barrens powder-snow trap feature. Geometry and surface acceptance live in the pure
 * {@link SubterraneanTrapLayout}/{@link SubterraneanTrapPlan} layers; this adapter performs only world reads,
 * the complete preflight, and the already-ordered writes. Each landing follows a two-wide, three-high,
 * downward-only route into either certified natural cave space or an irregular authored glacial cavern.
 */
public final class PowderCrevasseRoofFeature extends Feature<NoneFeatureConfiguration> {

    public static Feature<NoneFeatureConfiguration> INSTANCE;

    private static final BlockState POWDER_SNOW = Blocks.POWDER_SNOW.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Identifier POLAR_BARRENS_ID =
            Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "polar_barrens");
    /** Maintained through CARVERS/FEATURES; WORLD_SURFACE_WG freezes before top-layer feature writes. */
    static final Heightmap.Types FEATURE_STAGE_SURFACE_HEIGHTMAP = Heightmap.Types.WORLD_SURFACE;
    private static final boolean DEBUG = Boolean.getBoolean("latitude.debugCollapse");
    private static final java.util.concurrent.atomic.AtomicLong DEBUG_CALLS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final long AUTHORED_DIRECTION_SALT = 0xD6E8FEB86659FD93L;
    private static final int MAX_RUNTIME_ALTERNATIVES_PER_DEPTH = 4 * 34;
    private static final Object AUDIT_WRITE_TELEMETRY_LOCK = new Object();
    private static long auditWriteTelemetrySequence;
    private static MutableGenerationWriteTelemetry activeAuditWriteTelemetry;

    /** Bounded apply-path evidence for one explicitly opened fresh-generation audit interval. */
    public record GenerationWriteTelemetry(
            boolean available, long sessionId, long attempts, long appliedSuccesses,
            long failedWriteBatches, long rolledBackWriteBatches) {
    }

    private static final class MutableGenerationWriteTelemetry {
        private final long sessionId;
        private long attempts;
        private long appliedSuccesses;
        private long failedWriteBatches;
        private long rolledBackWriteBatches;

        private MutableGenerationWriteTelemetry(long sessionId) {
            this.sessionId = sessionId;
        }
    }

    public static long beginGenerationWriteTelemetry() {
        synchronized (AUDIT_WRITE_TELEMETRY_LOCK) {
            long sessionId = ++auditWriteTelemetrySequence;
            activeAuditWriteTelemetry = new MutableGenerationWriteTelemetry(sessionId);
            return sessionId;
        }
    }

    public static GenerationWriteTelemetry endGenerationWriteTelemetry(long sessionId) {
        synchronized (AUDIT_WRITE_TELEMETRY_LOCK) {
            MutableGenerationWriteTelemetry current = activeAuditWriteTelemetry;
            if (current == null || current.sessionId != sessionId) {
                return new GenerationWriteTelemetry(false, sessionId, 0, 0, 0, 0);
            }
            activeAuditWriteTelemetry = null;
            return new GenerationWriteTelemetry(true, sessionId, current.attempts,
                    current.appliedSuccesses, current.failedWriteBatches,
                    current.rolledBackWriteBatches);
        }
    }

    static void recordGenerationWriteTelemetry(boolean succeeded, boolean rollbackVerified) {
        synchronized (AUDIT_WRITE_TELEMETRY_LOCK) {
            if (activeAuditWriteTelemetry == null) {
                return;
            }
            activeAuditWriteTelemetry.attempts++;
            if (succeeded) {
                activeAuditWriteTelemetry.appliedSuccesses++;
            } else {
                activeAuditWriteTelemetry.failedWriteBatches++;
                if (rollbackVerified) {
                    activeAuditWriteTelemetry.rolledBackWriteBatches++;
                }
            }
        }
    }

    private record WorldWrite(BlockPos position, BlockState state, SubterraneanTrapPlan.Phase phase) {
    }

    private record RuntimeCandidate(SubterraneanTrapPlan.Plan plan, List<WorldWrite> writes) {
        SubterraneanTrapPlan.CavernDirection cavernDirection() {
            return plan.descentRoute().endpoint() instanceof SubterraneanTrapPlan.AuthoredCavernEndpoint cavern
                    ? cavern.direction() : null;
        }

        boolean authored() {
            return cavernDirection() != null;
        }
    }

    record PlanSelection<T>(T candidate, int placementOrdinal, int depth, int routeOrdinal) {
    }

    private record ApplyResult(boolean succeeded, boolean finalReadbackPassed, boolean rollbackVerified,
                               int completedSurfaceCovers, int completedRevealRemovals) {
    }

    private record SurfaceSnapshotCounts(int thinOverFull, int thinOther, int fullSnow, int powder,
                                         int firmGlacialIce, int other) {
    }

    enum AuthoredOwnerRejection {
        NONE,
        NOT_WORLDGEN_REGION,
        CENTER_MISMATCH,
        PRIORITY_LOSER
    }

    enum AuthoredFootprintRejection {
        NONE,
        OUTSIDE_SELECTED_TWO_CHUNKS,
        OUTSIDE_WRITE_ZONE,
        WRITE_NOT_ALLOWED
    }

    @FunctionalInterface
    interface ChunkPriority {
        long at(int chunkX, int chunkZ);
    }

    static AuthoredOwnerRejection authoredOwnerEligibility(
            boolean worldGenRegion, int ownerX, int ownerZ, int centerX, int centerZ, boolean ownerWins) {
        if (!worldGenRegion) return AuthoredOwnerRejection.NOT_WORLDGEN_REGION;
        if (ownerX != centerX || ownerZ != centerZ) return AuthoredOwnerRejection.CENTER_MISMATCH;
        return ownerWins ? AuthoredOwnerRejection.NONE : AuthoredOwnerRejection.PRIORITY_LOSER;
    }

    static boolean authoredOwnerWins(long seed, int ownerX, int ownerZ) {
        return authoredOwnerWins(ownerX, ownerZ,
                (chunkX, chunkZ) -> SubterraneanTrapLayout.priority(seed, chunkX, chunkZ));
    }

    static boolean authoredOwnerWins(int ownerX, int ownerZ, ChunkPriority priority) {
        if (priority == null) return false;
        long own = priority.at(ownerX, ownerZ);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                int otherX = ownerX + dx;
                int otherZ = ownerZ + dz;
                long other = priority.at(otherX, otherZ);
                int priorityOrder = Long.compareUnsigned(own, other);
                if (priorityOrder > 0 || (priorityOrder == 0
                        && coordinateOrder(otherX, otherZ, ownerX, ownerZ) < 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int coordinateOrder(int firstX, int firstZ, int secondX, int secondZ) {
        int x = Integer.compare(firstX, secondX);
        return x != 0 ? x : Integer.compare(firstZ, secondZ);
    }

    static List<SubterraneanTrapPlan.CavernDirection> authoredDirectionOrder(
            long seed, int ownerX, int ownerZ) {
        SubterraneanTrapPlan.CavernDirection[] values = SubterraneanTrapPlan.CavernDirection.values();
        int first = (int) Long.remainderUnsigned(
                SubterraneanTrapLayout.priority(seed, ownerX, ownerZ) ^ AUTHORED_DIRECTION_SALT, values.length);
        return java.util.stream.IntStream.range(0, values.length)
                .mapToObj(index -> values[(first + index) % values.length]).toList();
    }

    static void enterAuthoredPlacement(
            int placementIndex,
            int[] activePlacementIndex,
            SubterraneanTrapPlan.AuthoredCavernCatalogue[] activeCatalogue) {
        if (activePlacementIndex[0] != placementIndex) {
            activeCatalogue[0] = null;
            activePlacementIndex[0] = placementIndex;
        }
    }

    static boolean authoredNeighborAvailable(
            WorldGenRegion region, int ownerX, int ownerZ,
            SubterraneanTrapPlan.CavernDirection direction) {
        return region != null && direction != null
                && region.hasChunk(ownerX + direction.chunkDx(), ownerZ + direction.chunkDz());
    }

    static <T> List<T> authoredCandidates(
            boolean ownerEligible, List<SubterraneanTrapPlan.CavernDirection> directionOrder,
            Predicate<SubterraneanTrapPlan.CavernDirection> neighborAvailable,
            Function<SubterraneanTrapPlan.CavernDirection, List<T>> planFactory) {
        if (!ownerEligible || directionOrder == null || neighborAvailable == null || planFactory == null) {
            return List.of();
        }
        List<T> candidates = new ArrayList<>();
        for (SubterraneanTrapPlan.CavernDirection direction : directionOrder) {
            if (!neighborAvailable.test(direction)) continue;
            List<T> generated = planFactory.apply(direction);
            if (generated != null) candidates.addAll(generated);
        }
        return List.copyOf(candidates);
    }

    static <T> List<T> naturalFirst(List<T> natural, Supplier<List<T>> authored) {
        if (natural != null && !natural.isEmpty()) return List.copyOf(natural);
        if (authored == null) return List.of();
        List<T> generated = authored.get();
        return generated == null ? List.of() : List.copyOf(generated);
    }

    static AuthoredFootprintRejection authoredFootprintRejection(
            SubterraneanTrapPlan.CavernDirection direction, int localX, int localZ,
            boolean withinWriteZone, boolean writable) {
        if (direction == null || !direction.containsWriteCell(localX, localZ)) {
            return AuthoredFootprintRejection.OUTSIDE_SELECTED_TWO_CHUNKS;
        }
        if (!withinWriteZone) return AuthoredFootprintRejection.OUTSIDE_WRITE_ZONE;
        return writable ? AuthoredFootprintRejection.NONE : AuthoredFootprintRejection.WRITE_NOT_ALLOWED;
    }

    static boolean authoredWriteUsesOwnerOrSelectedNeighbor(
            SubterraneanTrapPlan.CavernDirection direction, int localX, int localZ) {
        if (direction == null) return false;
        int chunkDx = Math.floorDiv(localX, 16);
        int chunkDz = Math.floorDiv(localZ, 16);
        return (chunkDx == 0 && chunkDz == 0)
                || (chunkDx == direction.chunkDx() && chunkDz == direction.chunkDz());
    }

    static boolean authoredReadAllowed(
            SubterraneanTrapPlan.CavernDirection direction, int localX, int localZ,
            boolean withinWriteZone) {
        return direction != null && withinWriteZone
                && direction.containsReadShellCell(localX, localZ)
                && authoredWriteUsesOwnerOrSelectedNeighbor(direction, localX, localZ);
    }

    static boolean insideTwoBlockDilation(int dx, int dy, int dz) {
        return Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) <= 2;
    }

    static boolean authoredCavernVolumeBiomesSafe(
            List<SubterraneanTrapPlan.RouteCell> floors,
            List<SubterraneanTrapPlan.RouteCell> clears,
            Function<SubterraneanTrapPlan.RouteCell, String> biomeIdAtCell) {
        if (floors == null || floors.isEmpty() || clears == null || clears.isEmpty() || biomeIdAtCell == null) {
            return false;
        }
        return java.util.stream.Stream.concat(floors.stream(), clears.stream())
                .allMatch(cell -> cell != null && cell.y() > 0
                        && LatitudeBiomes.GLACIAL_CAVES_ID.equals(biomeIdAtCell.apply(cell)));
    }

    public PowderCrevasseRoofFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Registers {@code globe:powder_crevasse_roof} unconditionally during mod initialization. */
    public static void register() {
        INSTANCE = Registry.register(
                BuiltInRegistries.FEATURE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "powder_crevasse_roof"),
                new PowderCrevasseRoofFeature(NoneFeatureConfiguration.CODEC));
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int baseX = (ctx.origin().getX() >> 4) << 4;
        int baseZ = (ctx.origin().getZ() >> 4) << 4;
        int chunkX = baseX >> 4;
        int chunkZ = baseZ >> 4;
        if (!LatitudeV2Flags.POLAR_BARRENS_ENABLED || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            PowderTrapWorldSafetyTelemetry telemetry = new PowderTrapWorldSafetyTelemetry();
            debugRow(chunkX, chunkZ, SubterraneanTrapLayout.chunkGate(level.getSeed(), chunkX, chunkZ),
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "none",
                    "none",
                    telemetry, PowderTrapWorldSafetyResult.notChecked(),
                    new SurfaceSnapshotCounts(0, 0, 0, 0, 0, 0),
                    AuthoredOwnerRejection.NOT_WORLDGEN_REGION, null, null);
            return false;
        }

        int[][] surfaceFirstAir = new int[16][16];
        int[][] surfaceKind = new int[16][16];
        BlockState[][] surfaceSupportSnapshot = new BlockState[16][16];
        BlockState[][] surfaceLayerSnapshot = new BlockState[16][16];
        int thinOverFull = 0;
        int thinOther = 0;
        int fullSnow = 0;
        int powder = 0;
        int firmGlacialIce = 0;
        int other = 0;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int firstAir = level.getHeight(FEATURE_STAGE_SURFACE_HEIGHTMAP, baseX + localX, baseZ + localZ);
                BlockPos topPos = new BlockPos(baseX + localX, firstAir - 1, baseZ + localZ);
                BlockState top = level.getBlockState(topPos);
                BlockPos supportPos = topPos.below();
                BlockState support = level.getBlockState(supportPos);
                if (top.is(Blocks.SNOW) && certifiedThinSupport(level, supportPos, support)) {
                    // The visible layer is cosmetic relief. Plan against its exact certified support and remove
                    // only the layer over powder-replaced cells, after every powder replacement has succeeded.
                    surfaceFirstAir[localX][localZ] = firstAir - 1;
                    surfaceKind[localX][localZ] = support.is(Blocks.SNOW_BLOCK)
                            ? SubterraneanTrapPlan.THIN_OVER_FULL_SNOW
                            : SubterraneanTrapPlan.THIN_SNOW;
                    surfaceSupportSnapshot[localX][localZ] = support;
                    surfaceLayerSnapshot[localX][localZ] = top;
                    if (support.is(Blocks.SNOW_BLOCK)) {
                        thinOverFull++;
                    } else {
                        thinOther++;
                    }
                } else {
                    surfaceFirstAir[localX][localZ] = firstAir;
                    int kind = surfaceKind(top);
                    surfaceKind[localX][localZ] = kind;
                    surfaceSupportSnapshot[localX][localZ] = top;
                    if (kind == SubterraneanTrapPlan.FULL_SNOW) {
                        fullSnow++;
                    } else if (kind == SubterraneanTrapPlan.POWDER) {
                        powder++;
                    } else if (kind == SubterraneanTrapPlan.FIRM_GLACIAL_ICE) {
                        firmGlacialIce++;
                    } else {
                        other++;
                    }
                }
            }
        }
        SurfaceSnapshotCounts surfaceCounts =
                new SurfaceSnapshotCounts(thinOverFull, thinOther, fullSnow, powder, firmGlacialIce, other);

        int rejectFirstAir = 0;
        int rejectRing = 0;
        int rejectSurface = 0;
        int rejectDepth = 0;
        Map<SubterraneanTrapPlan.SurfaceRejectionReason, Integer> rejectSurfaceReasonCounts =
                new EnumMap<>(SubterraneanTrapPlan.SurfaceRejectionReason.class);
        int eligiblePlacements = 0;
        List<SubterraneanTrapPlan.Plan> acceptedPlans = new ArrayList<>();
        List<SubterraneanTrapLayout.Placement> placements =
                SubterraneanTrapLayout.placements(level.getSeed(), chunkX, chunkZ);
        for (SubterraneanTrapLayout.Placement placement : placements) {
            List<SubterraneanTrapPlan.NaturalCaveDestination> destinations = findLowerCaveDestinations(
                    level, placement, surfaceFirstAir, SubterraneanTrapPlan.PREFERRED_DEPTH, baseX, baseZ);
            SubterraneanTrapPlan.SurfaceDiagnosticResult diagnostic =
                    SubterraneanTrapPlan.planWithSurfaceDiagnostics(
                            placement, surfaceFirstAir, surfaceKind, destinations);
            SubterraneanTrapPlan.Result result = diagnostic.result();
            if (result.isAccepted()) {
                eligiblePlacements++;
                acceptedPlans.add(result.accepted());
            } else {
                switch (result.rejection()) {
                    case POWDER_RELIEF_EXCEEDS_ONE -> rejectFirstAir++;
                    case APPROACH_RING_UNSTABLE -> rejectRing++;
                    case UNSUPPORTED_SURFACE -> {
                        rejectSurface++;
                        recordSurfaceRejection(rejectSurfaceReasonCounts, diagnostic.surfaceRejectionReason());
                    }
                    case DEPTH_OUTSIDE_HARD_LIMIT, DEPTH_OUTSIDE_LEGAL_RANGE,
                            LOWER_CAVE_REQUIRED, DESCENT_ROUTE_CAPACITY, DESCENT_OWNER_BOUNDS -> rejectDepth++;
                    case INVALID_INPUT -> {
                        rejectSurface++;
                        recordSurfaceRejection(rejectSurfaceReasonCounts, diagnostic.surfaceRejectionReason());
                    }
                }
            }
        }

        // The final occurrence gate is intentionally delayed until every pure template has been calibrated for DEBUG.
        boolean gate = SubterraneanTrapLayout.chunkGate(level.getSeed(), chunkX, chunkZ);
        WorldGenRegion authoredRegion = level instanceof WorldGenRegion region ? region : null;
        ChunkPos regionCenter = authoredRegion == null ? null : authoredRegion.getCenter();
        boolean ownerWins = authoredOwnerWins(level.getSeed(), chunkX, chunkZ);
        AuthoredOwnerRejection ownerRejection = authoredOwnerEligibility(
                authoredRegion != null, chunkX, chunkZ,
                regionCenter == null ? Integer.MIN_VALUE : regionCenter.x(),
                regionCenter == null ? Integer.MIN_VALUE : regionCenter.z(), ownerWins);
        boolean authoredOwnerEligible = ownerRejection == AuthoredOwnerRejection.NONE;
        List<SubterraneanTrapPlan.CavernDirection> cavernDirectionOrder =
                authoredDirectionOrder(level.getSeed(), chunkX, chunkZ);
        int writeFailure = 0;
        int encounters = 0;
        int powderCovers = 0;
        int cushions = 0;
        int clearWrites = 0;
        int partialSurfaceCovers = 0;
        int partialRevealRemovals = 0;
        int drop = acceptedPlans.isEmpty() ? 0
                : acceptedPlans.getFirst().roofY() - acceptedPlans.getFirst().landingY();
        String firstPowder = "none";
        PowderTrapWorldSafetyTelemetry telemetry = new PowderTrapWorldSafetyTelemetry();
        PowderTrapWorldSafetyResult[] firstWorldSafety = {PowderTrapWorldSafetyResult.notChecked()};
        int[] worldSafetyRejections = {0};
        int[] authoredCataloguePlacementIndex = {-1};
        SubterraneanTrapPlan.AuthoredCavernCatalogue[] authoredCatalogue = {null};

        PlanSelection<RuntimeCandidate> selection = selectFirstSafePlan(
                gate ? placements.size() : 0,
                placementIndex -> {
                    enterAuthoredPlacement(
                            placementIndex, authoredCataloguePlacementIndex, authoredCatalogue);
                    return SubterraneanTrapPlan.landingDepthOrder(
                            surfaceRoofY(placements.get(placementIndex), surfaceFirstAir));
                },
                (placementIndex, depth) -> cushionBaseAnchorEligible(
                        level, placements.get(placementIndex), surfaceFirstAir, depth, baseX, baseZ),
                (placementIndex, depth) -> {
                    List<SubterraneanTrapPlan.Plan> natural = SubterraneanTrapPlan.planAlternatives(
                            placements.get(placementIndex), surfaceFirstAir, surfaceKind, depth,
                            findLowerCaveDestinations(level, placements.get(placementIndex),
                                    surfaceFirstAir, depth, baseX, baseZ));
                    List<RuntimeCandidate> naturalCandidates = natural.stream()
                            .map(plan -> new RuntimeCandidate(plan, translate(plan, baseX, baseZ))).toList();
                    return naturalFirst(naturalCandidates, () -> authoredCandidates(
                            authoredOwnerEligible, cavernDirectionOrder,
                            direction -> authoredNeighborAvailable(
                                    authoredRegion, chunkX, chunkZ, direction),
                            direction -> {
                                if (authoredCatalogue[0] == null) {
                                    authoredCatalogue[0] = SubterraneanTrapPlan.authoredCavernCatalogue(
                                            placements.get(placementIndex));
                                }
                                return SubterraneanTrapPlan.authoredCavernAlternatives(
                                                authoredCatalogue[0], surfaceFirstAir, surfaceKind,
                                                depth, direction)
                                        .stream().map(plan -> new RuntimeCandidate(
                                                plan, translate(plan, baseX, baseZ))).toList();
                            }));
                },
                candidate -> {
                    PowderTrapWorldSafetyResult attempt = worldSafe(
                            level, candidate.plan().descentRoute(), candidate.writes(),
                            surfaceFirstAir, surfaceKind, surfaceSupportSnapshot, surfaceLayerSnapshot, baseX, baseZ);
                    telemetry.record(attempt);
                    if (firstWorldSafety[0].reason() == PowderTrapWorldSafetyFailure.NOT_CHECKED) {
                        firstWorldSafety[0] = attempt;
                    }
                    if (!attempt.isSafe()) {
                        worldSafetyRejections[0]++;
                    }
                    return attempt.isSafe();
                });

        int selectedPlacementOrdinal = selection == null ? 0 : selection.placementOrdinal();
        int selectedDepth = selection == null ? 0 : selection.depth();
        int selectedRouteOrdinal = selection == null ? 0 : selection.routeOrdinal();
        ApplyResult applyTelemetry = null;
        if (selection != null) {
            RuntimeCandidate selected = selection.candidate();
            SubterraneanTrapPlan.Plan candidate = selected.plan();
            List<WorldWrite> writes = selected.writes();
            drop = candidate.roofY() - candidate.landingY();
            // Selection and every complete safety preflight are finished before this sole mutation boundary.
            // Whether apply succeeds or reports a partial batch, no alternative is inspected afterward.
            ApplyResult applied = apply(level, writes);
            recordGenerationWriteTelemetry(applied.succeeded(), applied.rollbackVerified());
            applyTelemetry = applied;
            if (!applied.succeeded()) {
                writeFailure++;
                partialSurfaceCovers = applied.completedSurfaceCovers();
                partialRevealRemovals = applied.completedRevealRemovals();
            } else {
                encounters = 1;
                powderCovers = count(writes, SubterraneanTrapPlan.Phase.SURFACE_POWDER);
                cushions = count(writes, SubterraneanTrapPlan.Phase.CUSHION);
                clearWrites = count(writes, SubterraneanTrapPlan.Phase.CLEAR);
                WorldWrite firstSurface = writes.stream()
                        .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.SURFACE_POWDER)
                        .findFirst().orElseThrow();
                firstPowder = firstSurface.position().getX() + "," + firstSurface.position().getY()
                        + "," + firstSurface.position().getZ();
            }
        }

        int rejectWorldSafety = worldSafetyRejections[0];
        PowderTrapWorldSafetyResult worldSafety = firstWorldSafety[0];
        debugRow(chunkX, chunkZ, gate, eligiblePlacements, rejectFirstAir, rejectRing, rejectSurface, rejectDepth,
                rejectWorldSafety, writeFailure, encounters, powderCovers, cushions, clearWrites,
                partialSurfaceCovers, partialRevealRemovals, drop,
                selectedPlacementOrdinal, selectedDepth, selectedRouteOrdinal, firstPowder,
                encodedSurfaceRejectionReasonCounts(rejectSurfaceReasonCounts),
                telemetry, worldSafety, surfaceCounts, ownerRejection,
                selection == null ? null : selection.candidate(), applyTelemetry);
        return encounters == 1;
    }

    static <T> PlanSelection<T> selectFirstSafePlan(
            int placementCount, IntFunction<? extends List<Integer>> depthOrder,
            BiPredicate<Integer, Integer> anchorEligible,
            BiFunction<Integer, Integer, List<T>> planFactory, Predicate<T> worldSafe) {
        if (placementCount <= 0 || depthOrder == null || anchorEligible == null
                || planFactory == null || worldSafe == null) {
            return null;
        }
        for (int placementIndex = 0; placementIndex < placementCount; placementIndex++) {
            List<Integer> depths = depthOrder.apply(placementIndex);
            if (depths == null) {
                continue;
            }
            for (Integer depthValue : depths) {
                if (depthValue == null) {
                    continue;
                }
                int depth = depthValue;
                if (!anchorEligible.test(placementIndex, depth)) {
                    continue;
                }
                List<T> alternatives = planFactory.apply(placementIndex, depth);
                if (alternatives == null || alternatives.isEmpty()) {
                    continue;
                }
                int attemptLimit = Math.min(MAX_RUNTIME_ALTERNATIVES_PER_DEPTH, alternatives.size());
                for (int routeIndex = 0; routeIndex < attemptLimit; routeIndex++) {
                    T candidate = alternatives.get(routeIndex);
                    if (candidate != null && worldSafe.test(candidate)) {
                        return new PlanSelection<>(
                                candidate, placementIndex + 1, depth, routeIndex + 1);
                    }
                }
            }
        }
        return null;
    }

    private static int surfaceRoofY(SubterraneanTrapLayout.Placement placement, int[][] firstAir) {
        return placement.powder().stream()
                .mapToInt(cell -> firstAir[cell.x()][cell.z()] - 1)
                .min().orElse(0);
    }

    private static void debugRow(int chunkX, int chunkZ, boolean gate, int eligiblePlacements, int rejectFirstAir,
                                 int rejectRing, int rejectSurface, int rejectDepth, int rejectWorldSafety,
                                 int writeFailure, int encounters, int powderCovers, int cushions, int clearWrites,
                                 int partialSurfaceCovers, int partialRevealRemovals, int drop,
                                 int selectedPlacementOrdinal, int selectedDepth, int selectedRouteOrdinal,
                                 String firstPowder,
                                 String surfaceRejectReasonCounts,
                                 PowderTrapWorldSafetyTelemetry telemetry, PowderTrapWorldSafetyResult worldSafety,
                                 SurfaceSnapshotCounts surfaceCounts, AuthoredOwnerRejection ownerRejection,
                                 RuntimeCandidate selected, ApplyResult applyResult) {
        if (!DEBUG) {
            return;
        }
        long call = DEBUG_CALLS.incrementAndGet();
        SubterraneanTrapPlan.AuthoredCavernEndpoint cavern = selected != null
                && selected.plan().descentRoute().endpoint() instanceof SubterraneanTrapPlan.AuthoredCavernEndpoint value
                ? value : null;
        String endpointKind = selected == null ? "none" : cavern == null ? "natural" : "authored_cavern";
        String cavernDirection = cavern == null ? "none" : cavern.direction().name();
        String neighbor = cavern == null ? "none"
                : (chunkX + cavern.direction().chunkDx()) + "," + (chunkZ + cavern.direction().chunkDz());
        GlobeMod.LOGGER.info("[LAT][SUBTERRANEAN] call={} chunk=({},{}) gate={} preferredDepthCensus={} "
                        + "preferredDepthEligiblePlacements={} preferredDepthRejectFirstAir={} "
                        + "preferredDepthRejectRing={} preferredDepthRejectSurface={} preferredDepthRejectDepth={} "
                        + "preferredDepthRejectSurfaceReasonCounts={} "
                        + "rejectWorldSafety={} "
                        + "writeFailure={} encounters={} powderCovers={} cushions={} clearWrites={} "
                        + "partialSurfaceCovers={} partialRevealRemovals={} drop={} firstPowder={} "
                        + "selectedPlacementOrdinal={} selectedDepth={} selectedRouteOrdinal={} "
                        + "worldSafetyAttempts={} worldSafetyAcceptedAttempt={} worldSafetyReasonCounts={} "
                        + "worldSafetyReasonSemantics=first_failure worldSafetyReason={} "
                        + "worldSafetyPos={} worldSafetyState={} "
                        + "authoredOwnerReject={} endpointKind={} cavernDirection={} authoredNeighbor={} "
                        + "cavernFloors={} cavernClears={} cavernShell={} "
                        + "finalReadbackPassed={} rollbackVerified={} "
                        + "thinOverFull={} thinOther={} fullSnow={} powder={} firmGlacialIce={} other={}",
                call, chunkX, chunkZ, gate, SubterraneanTrapPlan.PREFERRED_DEPTH,
                eligiblePlacements, rejectFirstAir, rejectRing, rejectSurface,
                rejectDepth, surfaceRejectReasonCounts, rejectWorldSafety, writeFailure, encounters, powderCovers,
                cushions, clearWrites,
                partialSurfaceCovers, partialRevealRemovals, drop, firstPowder,
                selectedPlacementOrdinal, selectedDepth, selectedRouteOrdinal, telemetry.attempts(),
                telemetry.acceptedAttempt(), telemetry.encodedReasonCounts(), worldSafety.reason(),
                worldSafety.position(), worldSafety.state(), ownerRejection, endpointKind, cavernDirection, neighbor,
                cavern == null ? 0 : cavern.floorCells().size(), cavern == null ? 0 : cavern.clearCells().size(),
                cavern == null ? 0 : cavern.shellProbes().size(),
                applyResult != null && applyResult.finalReadbackPassed(),
                applyResult != null && applyResult.rollbackVerified(),
                surfaceCounts.thinOverFull(), surfaceCounts.thinOther(),
                surfaceCounts.fullSnow(), surfaceCounts.powder(), surfaceCounts.firmGlacialIce(), surfaceCounts.other());
    }

    private static void recordSurfaceRejection(
            Map<SubterraneanTrapPlan.SurfaceRejectionReason, Integer> counts,
            SubterraneanTrapPlan.SurfaceRejectionReason reason) {
        if (reason != null) {
            counts.merge(reason, 1, Integer::sum);
        }
    }

    static String encodedSurfaceRejectionReasonCounts(
            Map<SubterraneanTrapPlan.SurfaceRejectionReason, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        return java.util.Arrays.stream(SubterraneanTrapPlan.SurfaceRejectionReason.values())
                .filter(reason -> counts.getOrDefault(reason, 0) > 0)
                .map(reason -> reason.name() + ":" + counts.get(reason))
                .collect(java.util.stream.Collectors.joining(","));
    }

    static int surfaceKind(BlockState state) {
        if (state.is(Blocks.SNOW_BLOCK)) {
            return SubterraneanTrapPlan.FULL_SNOW;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return SubterraneanTrapPlan.POWDER;
        }
        // Glacier-body ice can be the exposed rim or firm approach beside a concealed snow mouth. It is
        // never accepted as a powder replacement surface, and ordinary lake ice remains OTHER.
        if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return SubterraneanTrapPlan.FIRM_GLACIAL_ICE;
        }
        // A visible snow layer is accepted only by the caller-certified branch in place(), never by block id
        // alone: its hidden support must be dry, stable, entity-free, replaceable, and snapshotted exactly.
        return SubterraneanTrapPlan.OTHER;
    }

    private static List<WorldWrite> translate(SubterraneanTrapPlan.Plan plan, int baseX, int baseZ) {
        List<WorldWrite> writes = new ArrayList<>(plan.writes().size());
        for (SubterraneanTrapPlan.Write write : plan.writes()) {
            BlockState desired = switch (write.phase()) {
                case CUSHION, SURFACE_POWDER -> POWDER_SNOW;
                case CUSHION_BASE -> BLUE_ICE;
                case DESCENT_FLOOR, AUTHORED_CAVERN_FLOOR, ESCAPE_FLOOR, ESCAPE_MINE_TAIL -> SNOW_BLOCK;
                case CLEAR, DESCENT_CLEAR, AUTHORED_CAVERN_CLEAR, ESCAPE_CLEAR, REMOVE_SURFACE_LAYER -> AIR;
            };
            writes.add(new WorldWrite(new BlockPos(baseX + write.x(), write.y(), baseZ + write.z()), desired,
                    write.phase()));
        }
        return writes;
    }

    private static boolean cushionBaseAnchorEligible(
            WorldGenLevel level, SubterraneanTrapLayout.Placement placement,
            int[][] firstAir, int depth, int baseX, int baseZ) {
        List<SubterraneanTrapLayout.Cell> powder = placement.powder().stream()
                .sorted(java.util.Comparator.comparingInt(SubterraneanTrapLayout.Cell::x)
                        .thenComparingInt(SubterraneanTrapLayout.Cell::z))
                .toList();
        if (powder.isEmpty()) {
            return false;
        }
        int roofY = powder.stream()
                .mapToInt(cell -> firstAir[cell.x()][cell.z()] - 1)
                .min().orElseThrow();
        int landingY = roofY - depth;
        Map<BlockPos, WorldWrite> bases = new HashMap<>();
        for (SubterraneanTrapLayout.Cell cell : powder) {
            BlockPos pos = new BlockPos(baseX + cell.x(), landingY - 1, baseZ + cell.z());
            BlockState current = level.getBlockState(pos);
            PowderTrapWorldSafetyLaw.CushionBaseAction action =
                    PowderTrapWorldSafetyLaw.cushionBaseAction(
                            current.isAir(), current.blocksMotion(), !current.getFluidState().isEmpty(),
                            level.getBlockEntity(pos) != null, isGravity(current), level.ensureCanWrite(pos));
            if (action == PowderTrapWorldSafetyLaw.CushionBaseAction.REJECT) {
                return false;
            }
            BlockState finalState = action == PowderTrapWorldSafetyLaw.CushionBaseAction.PRESERVE_EXISTING
                    ? current
                    : BLUE_ICE;
            if (bases.put(pos, new WorldWrite(
                    pos, finalState, SubterraneanTrapPlan.Phase.CUSHION_BASE)) != null) {
                return false;
            }
        }
        return cushionBaseAnchorSafety(level, bases).isSafe();
    }

    /**
     * Certifies a real lower cave before pure planning. Reads never leave the owner chunk: authored route
     * targets use inset 1..14 and untouched destination evidence may use the chunk boundary 0..15.
     */
    private static List<SubterraneanTrapPlan.NaturalCaveDestination> findLowerCaveDestinations(
            WorldGenLevel level, SubterraneanTrapLayout.Placement placement, int[][] firstAir,
            int depth, int baseX, int baseZ) {
        List<SubterraneanTrapLayout.Cell> powder = placement.powder();
        if (powder.isEmpty()) {
            return List.of();
        }
        int roofY = powder.stream().mapToInt(cell -> firstAir[cell.x()][cell.z()] - 1)
                .min().orElseThrow();
        int landingY = roofY - depth;
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
                // Runtime evidence that is incomplete or disconnected cannot become planning authority.
            }
        }
        return List.copyOf(destinations);
    }

    private static boolean naturalCaveColumn(WorldGenLevel level, SubterraneanTrapPlan.RouteCell local,
                                             int baseX, int baseZ) {
        return NaturalGlacialCaveQualification.naturalCaveColumn(level, local, baseX, baseZ);
    }

    private static PowderTrapWorldSafetyResult worldSafe(WorldGenLevel level,
                                                         SubterraneanTrapPlan.DescentRoute descentRoute,
                                                         List<WorldWrite> writes,
                                                         int[][] firstAir, int[][] kinds,
                                                         BlockState[][] supportSnapshots,
                                                         BlockState[][] layerSnapshots, int baseX, int baseZ) {
        int ownerChunkX = baseX >> 4;
        int ownerChunkZ = baseZ >> 4;
        SubterraneanTrapPlan.AuthoredCavernEndpoint cavernEndpoint =
                descentRoute.endpoint() instanceof SubterraneanTrapPlan.AuthoredCavernEndpoint cavern
                        ? cavern : null;
        WorldGenRegion authoredRegion = null;
        if (cavernEndpoint != null) {
            authoredRegion = level instanceof WorldGenRegion region ? region : null;
            ChunkPos center = authoredRegion == null ? null : authoredRegion.getCenter();
            AuthoredOwnerRejection ownerRejection = authoredOwnerEligibility(
                    authoredRegion != null, ownerChunkX, ownerChunkZ,
                    center == null ? Integer.MIN_VALUE : center.x(),
                    center == null ? Integer.MIN_VALUE : center.z(),
                    authoredOwnerWins(level.getSeed(), ownerChunkX, ownerChunkZ));
            if (ownerRejection != AuthoredOwnerRejection.NONE) {
                return PowderTrapWorldSafetyResult.failure(switch (ownerRejection) {
                    case NOT_WORLDGEN_REGION -> PowderTrapWorldSafetyFailure.AUTHORED_NOT_WORLDGEN_REGION;
                    case CENTER_MISMATCH -> PowderTrapWorldSafetyFailure.AUTHORED_CENTER_MISMATCH;
                    case PRIORITY_LOSER -> PowderTrapWorldSafetyFailure.AUTHORED_OWNER_LOST;
                    case NONE -> throw new IllegalStateException("unreachable owner acceptance");
                }, new BlockPos(baseX, 0, baseZ), ownerRejection.name());
            }
            if (!authoredNeighborAvailable(
                    authoredRegion, ownerChunkX, ownerChunkZ, cavernEndpoint.direction())) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.AUTHORED_NEIGHBOR_UNAVAILABLE,
                        new BlockPos(baseX + 16 * cavernEndpoint.direction().chunkDx(), 0,
                                baseZ + 16 * cavernEndpoint.direction().chunkDz()),
                        cavernEndpoint.direction().name());
            }
        }
        Map<BlockPos, WorldWrite> planned = new HashMap<>();
        for (int index = 0; index < writes.size(); index++) {
            WorldWrite write = writes.get(index);
            BlockPos pos = write.position();
            int localX = pos.getX() - baseX;
            int localZ = pos.getZ() - baseZ;
            boolean writable;
            if (cavernEndpoint == null) {
                if (localX < 1 || localX > 14 || localZ < 1 || localZ > 14) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.OUTSIDE_OWNER_CHUNK, pos, "unread");
                }
                writable = level.ensureCanWrite(pos);
            } else {
                boolean withinWriteZone = authoredRegion.isWithinWriteZone(pos);
                writable = withinWriteZone && authoredRegion.ensureCanWrite(pos);
                AuthoredFootprintRejection footprint = authoredFootprintRejection(
                        cavernEndpoint.direction(), localX, localZ, withinWriteZone, writable);
                if (footprint != AuthoredFootprintRejection.NONE
                        || !authoredWriteUsesOwnerOrSelectedNeighbor(
                                cavernEndpoint.direction(), localX, localZ)) {
                    return PowderTrapWorldSafetyResult.failure(switch (footprint) {
                        case OUTSIDE_WRITE_ZONE -> PowderTrapWorldSafetyFailure.AUTHORED_OUTSIDE_WRITE_ZONE;
                        case WRITE_NOT_ALLOWED -> PowderTrapWorldSafetyFailure.WRITE_NOT_ALLOWED;
                        case NONE, OUTSIDE_SELECTED_TWO_CHUNKS ->
                                PowderTrapWorldSafetyFailure.AUTHORED_OUTSIDE_SELECTED_FOOTPRINT;
                    }, pos, footprint.name());
                }
            }
            if (planned.containsKey(pos)) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.DUPLICATE_PLANNED_POSITION, pos, "unread");
            }
            BlockState current = level.getBlockState(pos);
            boolean hasFluid = !current.getFluidState().isEmpty();
            boolean hasBlockEntity = level.getBlockEntity(pos) != null;
            if (write.phase() == SubterraneanTrapPlan.Phase.CUSHION_BASE) {
                PowderTrapWorldSafetyLaw.CushionBaseAction action =
                        PowderTrapWorldSafetyLaw.cushionBaseAction(
                                current.isAir(), current.blocksMotion(), hasFluid, hasBlockEntity,
                                isGravity(current), writable);
                if (action == PowderTrapWorldSafetyLaw.CushionBaseAction.REJECT) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.CUSHION_BASE_UNSAFE, pos, current.toString());
                }
                if (action == PowderTrapWorldSafetyLaw.CushionBaseAction.PRESERVE_EXISTING) {
                    write = new WorldWrite(pos, current, write.phase());
                    writes.set(index, write);
                }
            } else {
                if (!writable) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.WRITE_NOT_ALLOWED, pos, "unread");
                }
                if (hasFluid) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.PLANNED_FLUID, pos, current.toString());
                }
                if (hasBlockEntity) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.PLANNED_BLOCK_ENTITY, pos, current.toString());
                }
            }
            planned.put(pos, write);
        }
        PowderTrapWorldSafetyResult anchorSafety = cushionBaseAnchorSafety(level, planned);
        if (!anchorSafety.isSafe()) {
            return anchorSafety;
        }
        for (WorldWrite write : writes) {
            BlockPos pos = write.position();
            BlockState current = level.getBlockState(pos);
            switch (write.phase()) {
                case SURFACE_POWDER -> {
                    int localX = pos.getX() - baseX;
                    int localZ = pos.getZ() - baseZ;
                    if (!PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                            supportSnapshots[localX][localZ], current)) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.SURFACE_SNAPSHOT_MISMATCH, pos, current.toString());
                    }
                    if (firstAir[localX][localZ] - 1 != pos.getY()) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.SURFACE_HEIGHT_MISMATCH, pos, current.toString());
                    }
                    if (!surfaceAboveIsSafe(level, pos, kinds[localX][localZ],
                            layerSnapshots[localX][localZ])) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.SURFACE_ABOVE_UNSAFE, pos.above(),
                                level.getBlockState(pos.above()).toString());
                    }
                    if (!isPolarBarrens(level, pos.above())) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.WRONG_BIOME, pos.above(),
                                level.getBlockState(pos.above()).toString());
                    }
                }
                case REMOVE_SURFACE_LAYER -> {
                    int localX = pos.getX() - baseX;
                    int localZ = pos.getZ() - baseZ;
                    BlockPos supportPos = pos.below();
                    BlockState support = level.getBlockState(supportPos);
                    if (!PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                                    layerSnapshots[localX][localZ], current)
                            || !PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                                    supportSnapshots[localX][localZ], support)
                            || !certifiedThinSupport(level, supportPos, support)
                            || !level.getBlockState(pos.above()).isAir()) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.REVEAL_LAYER_MISMATCH, pos, current.toString());
                    }
                }
                case CUSHION_BASE -> {
                    WorldWrite cushion = planned.get(pos.above());
                    if (!finalDryStableSolid(write.state())
                            || cushion == null
                            || cushion.phase() != SubterraneanTrapPlan.Phase.CUSHION) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.CUSHION_BASE_UNSAFE, pos, current.toString());
                    }
                }
                case CUSHION -> {
                    if ((!current.isAir() && !isCarverReplaceableOrSnow(current)) || isGravity(current)) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.CUSHION_TARGET_UNSAFE, pos, current.toString());
                    }
                    WorldWrite base = planned.get(pos.below());
                    if (base == null
                            || base.phase() != SubterraneanTrapPlan.Phase.CUSHION_BASE
                            || !finalDryStableSolid(base.state())) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.CUSHION_SUPPORT_UNSAFE, pos.below(),
                                level.getBlockState(pos.below()).toString());
                    }
                }
                case DESCENT_FLOOR, AUTHORED_CAVERN_FLOOR -> {
                    if (write.phase() == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_FLOOR
                            && (current.isAir() || current.is(Blocks.POWDER_SNOW))) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.AUTHORED_EXISTING_TRAP_OR_CAVERN,
                                pos, current.toString());
                    }
                    BlockState substrate = level.getBlockState(pos.below());
                    boolean targetReplaceable = !current.isAir()
                            && isCarverReplaceableOrSnow(current)
                            && lowerNaturalMaterialAllowed(current);
                    boolean targetHasFluid = !current.getFluidState().isEmpty();
                    boolean targetHasBlockEntity = level.getBlockEntity(pos) != null;
                    boolean targetHasGravity = isGravity(current);
                    boolean writable = level.ensureCanWrite(pos);
                    boolean substrateDryStableHard =
                            safeLowerNaturalSupport(level, pos.below(), substrate);
                    if (!PowderTrapWorldSafetyLaw.descentFloorFinalStateSafe(
                            targetReplaceable, substrateDryStableHard, targetHasFluid,
                            targetHasBlockEntity, targetHasGravity, writable)) {
                        boolean targetSafe = PowderTrapWorldSafetyLaw.descentFloorFinalStateSafe(
                                targetReplaceable, true, targetHasFluid,
                                targetHasBlockEntity, targetHasGravity, writable);
                        return PowderTrapWorldSafetyResult.failure(
                                targetSafe
                                        ? PowderTrapWorldSafetyFailure.DESCENT_FLOOR_SUPPORT_UNSAFE
                                        : PowderTrapWorldSafetyFailure.DESCENT_FLOOR_TARGET_UNSAFE,
                                targetSafe ? pos.below() : pos,
                                targetSafe ? substrate.toString() : current.toString());
                    }
                }
                case CLEAR -> {
                    if (!safeClearTarget(current)) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.CLEAR_TARGET_UNSAFE, pos, current.toString());
                    }
                }
                case DESCENT_CLEAR, AUTHORED_CAVERN_CLEAR -> {
                    if (write.phase() == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_CLEAR
                            && (current.isAir() || current.is(Blocks.POWDER_SNOW))) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.AUTHORED_EXISTING_TRAP_OR_CAVERN,
                                pos, current.toString());
                    }
                    if (!safeLowerNaturalExcavationTarget(level, pos, current)) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.DESCENT_CLEAR_TARGET_UNSAFE,
                                pos, current.toString());
                    }
                }
                case ESCAPE_FLOOR, ESCAPE_MINE_TAIL, ESCAPE_CLEAR -> {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.OBSOLETE_ROUTE_PHASE, pos, write.phase().name());
                }
            }
        }

        PowderTrapWorldSafetyResult destinationSafety = cavernEndpoint == null
                ? lowerCaveDestinationStillSafe(level, descentRoute.destination(), baseX, baseZ)
                : authoredCavernStillSafe(
                        authoredRegion, cavernEndpoint, writes, planned, baseX, baseZ);
        if (!destinationSafety.isSafe()) {
            return destinationSafety;
        }
        PowderTrapWorldSafetyResult routeShell =
                dryStableDescentShell(level, descentRoute, planned, baseX, baseZ);
        if (!routeShell.isSafe()) {
            return routeShell;
        }
        PowderTrapWorldSafetyResult fallShell = dryHazardFreeShell(level, writes, planned.keySet());
        if (!fallShell.isSafe()) {
            return fallShell;
        }
        return reachableFluidSourceSafety(level, writes, descentRoute, baseX, baseZ);
    }

    private static PowderTrapWorldSafetyResult authoredCavernStillSafe(
            WorldGenRegion region, SubterraneanTrapPlan.AuthoredCavernEndpoint cavern,
            List<WorldWrite> writes, Map<BlockPos, WorldWrite> planned, int baseX, int baseZ) {
        Set<BlockPos> expectedFloors = worldPositions(cavern.floorCells(), baseX, baseZ);
        Set<BlockPos> expectedClears = worldPositions(cavern.clearCells(), baseX, baseZ);
        Set<BlockPos> actualFloors = writes.stream()
                .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_FLOOR)
                .map(WorldWrite::position).collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> actualClears = writes.stream()
                .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_CLEAR)
                .map(WorldWrite::position).collect(java.util.stream.Collectors.toSet());
        if (!expectedFloors.equals(actualFloors) || !expectedClears.equals(actualClears)) {
            return PowderTrapWorldSafetyResult.failure(
                    PowderTrapWorldSafetyFailure.AUTHORED_CAVERN_SHAPE_MISMATCH,
                    new BlockPos(baseX, cavern.floorY(), baseZ),
                    "floor=" + actualFloors.size() + "/" + expectedFloors.size()
                            + ",clear=" + actualClears.size() + "/" + expectedClears.size());
        }

        Function<BlockPos, String> settledBiomeIdAt = settledBiomeIdLookup(region);
        if (!authoredCavernVolumeBiomesSafe(cavern.floorCells(), cavern.clearCells(), local -> {
            BlockPos cell = worldPosition(local, baseX, baseZ);
            return authoredReadAllowed(region, cavern.direction(), cell, baseX, baseZ)
                    ? settledBiomeIdAt.apply(cell) : null;
        })) {
            List<SubterraneanTrapPlan.RouteCell> biomeVolume = java.util.stream.Stream.concat(
                    cavern.floorCells().stream(), cavern.clearCells().stream()).toList();
            for (SubterraneanTrapPlan.RouteCell local : biomeVolume) {
                BlockPos cell = worldPosition(local, baseX, baseZ);
                String biome = authoredReadAllowed(region, cavern.direction(), cell, baseX, baseZ)
                        ? settledBiomeIdAt.apply(cell) : null;
                if (local.y() <= 0 || !LatitudeBiomes.GLACIAL_CAVES_ID.equals(biome)) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.AUTHORED_CAVERN_BIOME,
                            cell, biome == null ? "unavailable" : biome);
                }
            }
            return PowderTrapWorldSafetyResult.failure(
                    PowderTrapWorldSafetyFailure.AUTHORED_CAVERN_BIOME,
                    new BlockPos(baseX, cavern.floorY(), baseZ), "missing-volume-evidence");
        }

        for (SubterraneanTrapPlan.RouteCell local : cavern.shellProbes()) {
            BlockPos probe = worldPosition(local, baseX, baseZ);
            if (!authoredReadAllowed(region, cavern.direction(), probe, baseX, baseZ)
                    || planned.containsKey(probe)) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.AUTHORED_CAVERN_SHELL,
                        probe, "outside-readable-shell-or-planned");
            }
            BlockState state = region.getBlockState(probe);
            if (!safeLowerNaturalSupport(region, probe, state)) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.AUTHORED_CAVERN_SHELL,
                        probe, state.toString());
            }
        }

        Set<BlockPos> magmaChecks = new HashSet<>();
        Set<BlockPos> cavernVolume = new HashSet<>(expectedFloors);
        cavernVolume.addAll(expectedClears);
        for (BlockPos cell : cavernVolume) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (!insideTwoBlockDilation(dx, dy, dz)) continue;
                        BlockPos probe = cell.offset(dx, dy, dz);
                        if (!authoredReadAllowed(region, cavern.direction(), probe, baseX, baseZ)) {
                            return PowderTrapWorldSafetyResult.failure(
                                    PowderTrapWorldSafetyFailure.AUTHORED_MAGMA_DILATION,
                                    probe, "unreadable-two-block-dilation");
                        }
                        magmaChecks.add(probe.immutable());
                    }
                }
            }
        }
        for (BlockPos probe : magmaChecks) {
            if (region.getBlockState(probe).is(Blocks.MAGMA_BLOCK)) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.AUTHORED_MAGMA_DILATION,
                        probe, Blocks.MAGMA_BLOCK.toString());
            }
        }
        return PowderTrapWorldSafetyResult.passed();
    }

    private static Set<BlockPos> worldPositions(
            List<SubterraneanTrapPlan.RouteCell> cells, int baseX, int baseZ) {
        return cells.stream().map(cell -> worldPosition(cell, baseX, baseZ))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static boolean authoredReadAllowed(
            WorldGenRegion region, SubterraneanTrapPlan.CavernDirection direction,
            BlockPos position, int baseX, int baseZ) {
        int localX = position.getX() - baseX;
        int localZ = position.getZ() - baseZ;
        return region != null && authoredReadAllowed(
                direction, localX, localZ, region.isWithinWriteZone(position));
    }

    private static Function<BlockPos, String> settledBiomeIdLookup(WorldGenLevel level) {
        return CaveDropTrapFeature.settledBiomeIdLookup(level.getSeed(),
                (quartX, quartY, quartZ) -> populatedBiomeAt(level, quartX, quartY, quartZ));
    }

    private static Holder<Biome> populatedBiomeAt(
            WorldGenLevel level, int quartX, int quartY, int quartZ) {
        int chunkX = QuartPos.toSection(quartX);
        int chunkZ = QuartPos.toSection(quartZ);
        if (!level.hasChunk(chunkX, chunkZ)) return null;
        try {
            var chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.BIOMES, false);
            return chunk == null ? null : chunk.getNoiseBiome(quartX, quartY, quartZ);
        } catch (ReportedException unavailable) {
            return null;
        }
    }

    private static boolean surfaceAboveIsSafe(WorldGenLevel level, BlockPos surface, int kind,
                                              BlockState layerSnapshot) {
        return (kind == SubterraneanTrapPlan.THIN_SNOW || kind == SubterraneanTrapPlan.THIN_OVER_FULL_SNOW)
                ? PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                        layerSnapshot, level.getBlockState(surface.above()))
                        && level.getBlockState(surface.above(2)).isAir()
                : level.getBlockState(surface.above()).isAir();
    }

    private static boolean certifiedThinSupport(WorldGenLevel level, BlockPos pos, BlockState support) {
        return PowderTrapWorldSafetyLaw.certifiedThinSupport(
                support.getFluidState().isEmpty(), support.blocksMotion(), level.getBlockEntity(pos) != null,
                isGravity(support), isCarverReplaceableOrSnow(support));
    }

    private static boolean isCarverReplaceableOrSnow(BlockState state) {
        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES) || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.PACKED_ICE);
    }

    private static boolean safeClearTarget(BlockState state) {
        return (state.isAir() || isCarverReplaceableOrSnow(state)) && !isGravity(state);
    }

    private static boolean isGravity(BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }

    private static PowderTrapWorldSafetyResult cushionBaseAnchorSafety(
            WorldGenLevel level, Map<BlockPos, WorldWrite> planned) {
        List<WorldWrite> bases = planned.values().stream()
                .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.CUSHION_BASE)
                .sorted(java.util.Comparator.comparingInt((WorldWrite write) -> write.position().getX())
                        .thenComparingInt(write -> write.position().getZ()))
                .toList();
        if (bases.isEmpty()) {
            return PowderTrapWorldSafetyResult.failure(
                    PowderTrapWorldSafetyFailure.CUSHION_BASE_UNSAFE, BlockPos.ZERO, "missing-base-footprint");
        }
        Set<BlockPos> footprint = bases.stream().map(WorldWrite::position).collect(java.util.stream.Collectors.toSet());
        Set<PowderTrapWorldSafetyLaw.NaturalAnchorCandidate> candidates = new HashSet<>();
        for (WorldWrite base : bases) {
            BlockPos basePos = base.position();
            BlockState currentBase = level.getBlockState(basePos);
            candidates.add(naturalAnchorCandidate(level, basePos, planned));
            if (currentBase.isAir()) {
                candidates.add(naturalAnchorCandidate(level, basePos.below(), planned));
            }
            for (Direction direction : List.of(
                    Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                BlockPos neighbour = basePos.relative(direction);
                if (!footprint.contains(neighbour)) {
                    candidates.add(naturalAnchorCandidate(level, neighbour, planned));
                }
            }
        }
        if (!PowderTrapWorldSafetyLaw.hasSeparatedNaturalAnchors(candidates)) {
            return PowderTrapWorldSafetyResult.failure(
                    PowderTrapWorldSafetyFailure.CUSHION_BASE_UNANCHORED,
                    bases.getFirst().position(), "natural-anchors<2-separated-by-3");
        }
        return PowderTrapWorldSafetyResult.passed();
    }

    private static PowderTrapWorldSafetyLaw.NaturalAnchorCandidate naturalAnchorCandidate(
            WorldGenLevel level, BlockPos pos, Map<BlockPos, WorldWrite> planned) {
        BlockState current = level.getBlockState(pos);
        WorldWrite authored = planned.get(pos);
        boolean untouched = authored == null || authored.state().equals(current);
        boolean dryStable = !current.isAir() && current.blocksMotion()
                && current.getFluidState().isEmpty() && level.getBlockEntity(pos) == null
                && !isGravity(current);
        return new PowderTrapWorldSafetyLaw.NaturalAnchorCandidate(
                pos.getX(), pos.getZ(), untouched, dryStable);
    }

    private static boolean finalDryStableSolid(BlockState state) {
        return !state.isAir() && state.blocksMotion()
                && state.getFluidState().isEmpty() && !isGravity(state);
    }

    private static boolean safeLowerNaturalExcavationTarget(
            WorldGenLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && safeLowerNaturalTarget(level, pos, state);
    }

    private static boolean safeLowerNaturalSupport(
            WorldGenLevel level, BlockPos pos, BlockState state) {
        return state.blocksMotion() && safeLowerNaturalTarget(level, pos, state);
    }

    private static boolean safeLowerNaturalTarget(
            WorldGenLevel level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty()
                && level.getBlockEntity(pos) == null
                && !isGravity(state)
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.MAGMA_BLOCK)
                && lowerNaturalMaterialAllowed(state)
                && !isOre(state);
    }

    static boolean lowerNaturalFloorYAllowed(int floorY) {
        return NaturalGlacialCaveQualification.lowerNaturalFloorYAllowed(floorY);
    }

    static boolean lowerNaturalMaterialAllowed(BlockState state) {
        return NaturalGlacialCaveQualification.lowerNaturalMaterialAllowed(state);
    }

    private static boolean isOre(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().endsWith("_ore");
    }

    private static PowderTrapWorldSafetyResult lowerCaveDestinationStillSafe(
            WorldGenLevel level, SubterraneanTrapPlan.NaturalCaveDestination destination,
            int baseX, int baseZ) {
        for (SubterraneanTrapPlan.RouteCell floor : destination.continuationFloors()) {
            if (!naturalCaveColumn(level, floor, baseX, baseZ)) {
                BlockPos pos = worldPosition(floor, baseX, baseZ);
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.LOWER_CAVE_DESTINATION_UNSAFE,
                        pos, level.getBlockState(pos).toString());
            }
        }
        return PowderTrapWorldSafetyResult.passed();
    }

    private static PowderTrapWorldSafetyResult dryStableDescentShell(
            WorldGenLevel level, SubterraneanTrapPlan.DescentRoute route,
            Map<BlockPos, WorldWrite> planned, int baseX, int baseZ) {
        Set<BlockPos> naturalOpening = new HashSet<>();
        if (route.endpoint() instanceof SubterraneanTrapPlan.NaturalEndpoint natural) {
            for (SubterraneanTrapPlan.RouteCell floor : natural.destination().continuationFloors()) {
                BlockPos floorPos = worldPosition(floor, baseX, baseZ);
                naturalOpening.add(floorPos);
                for (int dy = 1; dy <= SubterraneanTrapPlan.ROUTE_CLEAR_HEIGHT; dy++) {
                    naturalOpening.add(floorPos.above(dy));
                }
            }
        }
        for (SubterraneanTrapPlan.RouteCell local : route.shellProbes()) {
            BlockPos probe = worldPosition(local, baseX, baseZ);
            if (local.x() < 0 || local.x() > 15 || local.z() < 0 || local.z() > 15) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.DESCENT_SHELL_UNSAFE,
                        probe, "outside-owner-shell");
            }
            if (planned.containsKey(probe) || naturalOpening.contains(probe)) {
                continue;
            }
            BlockState state = level.getBlockState(probe);
            if (!safeLowerNaturalSupport(level, probe, state)) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.DESCENT_SHELL_UNSAFE,
                        probe, state.toString());
            }
        }
        return PowderTrapWorldSafetyResult.passed();
    }

    private static BlockPos worldPosition(SubterraneanTrapPlan.RouteCell local, int baseX, int baseZ) {
        return new BlockPos(baseX + local.x(), local.y(), baseZ + local.z());
    }

    private static PowderTrapWorldSafetyResult dryHazardFreeShell(WorldGenLevel level, List<WorldWrite> writes,
                                                                  Set<BlockPos> planned) {
        for (WorldWrite write : writes) {
            if (write.phase() != SubterraneanTrapPlan.Phase.CLEAR
                    && write.phase() != SubterraneanTrapPlan.Phase.CUSHION) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = write.position().relative(direction);
                if (planned.contains(neighbour)) {
                    continue;
                }
                BlockState state = level.getBlockState(neighbour);
                PowderTrapWorldSafetyLaw.ShellRejection rejection = PowderTrapWorldSafetyLaw.shellRejection(
                        state.isAir(), state.blocksMotion(), !state.getFluidState().isEmpty(),
                        level.getBlockEntity(neighbour) != null, isGravity(state));
                if (rejection == PowderTrapWorldSafetyLaw.ShellRejection.FLUID) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.SHELL_FLUID, neighbour, state.toString());
                }
                if (rejection == PowderTrapWorldSafetyLaw.ShellRejection.BLOCK_ENTITY) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.SHELL_BLOCK_ENTITY, neighbour, state.toString());
                }
                if (rejection == PowderTrapWorldSafetyLaw.ShellRejection.GRAVITY) {
                    return PowderTrapWorldSafetyResult.failure(
                            PowderTrapWorldSafetyFailure.SHELL_GRAVITY, neighbour, state.toString());
                }
            }
        }
        return PowderTrapWorldSafetyResult.passed();
    }

    /**
     * Reverse-flow search from every authored fall/route air cell. Water can reach those cells only through
     * horizontal or upward passable space; a solid interruption blocks the search, and sources beyond seven
     * steps are deliberately outside this bounded safety veto.
     */
    private static PowderTrapWorldSafetyResult reachableFluidSourceSafety(
            WorldGenLevel level, List<WorldWrite> writes, SubterraneanTrapPlan.DescentRoute route,
            int baseX, int baseZ) {
        SubterraneanTrapPlan.AuthoredCavernEndpoint cavern =
                route.endpoint() instanceof SubterraneanTrapPlan.AuthoredCavernEndpoint endpoint
                        ? endpoint : null;
        WorldGenRegion authoredRegion = cavern != null && level instanceof WorldGenRegion region ? region : null;
        Set<BlockPos> plannedFinalSolid = new HashSet<>();
        Set<BlockPos> plannedFuturePassable = new HashSet<>();
        for (WorldWrite write : writes) {
            if (write.phase() == SubterraneanTrapPlan.Phase.DESCENT_FLOOR
                    || write.phase() == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_FLOOR
                    || write.phase() == SubterraneanTrapPlan.Phase.CUSHION_BASE) {
                plannedFinalSolid.add(write.position());
            }
            if (PowderTrapWorldSafetyLaw.plannedWriteSeedsFluidReachability(
                    write.phase(), false)) {
                plannedFuturePassable.add(write.position());
            }
        }
        Set<PowderTrapWorldSafetyLaw.FluidSearchCell> seeds = new HashSet<>();
        for (BlockPos seed : plannedFuturePassable) {
            seeds.add(new PowderTrapWorldSafetyLaw.FluidSearchCell(
                    seed.getX(), seed.getY(), seed.getZ()));
        }
        Function<PowderTrapWorldSafetyLaw.FluidSearchCell,
                PowderTrapWorldSafetyLaw.FluidTraversalCell> lookup = cell -> {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (cavern != null && !authoredReadAllowed(
                    authoredRegion, cavern.direction(), pos, baseX, baseZ)) {
                return PowderTrapWorldSafetyLaw.FluidTraversalCell.BLOCKED;
            }
            BlockState state = level.getBlockState(pos);
            return PowderTrapWorldSafetyLaw.fluidTraversalCell(
                    plannedFinalSolid.contains(pos),
                    plannedFuturePassable.contains(pos),
                    !state.getFluidState().isEmpty(),
                    fluidPassable(level, pos, state));
        };
        PowderTrapWorldSafetyLaw.FluidSearchCell fluid =
                PowderTrapWorldSafetyLaw.firstReachableFluidWithinSeven(seeds, lookup);
        if (fluid == null) {
            return PowderTrapWorldSafetyResult.passed();
        }
        BlockPos source = new BlockPos(fluid.x(), fluid.y(), fluid.z());
        return PowderTrapWorldSafetyResult.failure(
                PowderTrapWorldSafetyFailure.REACHABLE_FLUID_SOURCE, source,
                level.getBlockState(source).toString());
    }

    private static boolean fluidPassable(WorldGenLevel level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null
                && (state.isAir() || !state.blocksMotion()) && !isGravity(state);
    }

    private static ApplyResult apply(WorldGenLevel level, List<WorldWrite> writes) {
        List<CaveDropTrap.AtomicStateChange<BlockState>> changes = writes.stream()
                .map(write -> new CaveDropTrap.AtomicStateChange<>(level.getBlockState(write.position()), write.state()))
                .toList();
        boolean[] finalReadbackPassed = {false};
        CaveDropTrap.AtomicResult result = CaveDropTrap.applyAtomically(changes,
                new CaveDropTrap.AtomicStateAdapter<>() {
                    @Override public BlockState read(int index) { return level.getBlockState(writes.get(index).position()); }
                    @Override public boolean write(int index, BlockState state) {
                        return level.setBlock(writes.get(index).position(), state, Block.UPDATE_ALL);
                    }
                }, null, () -> {
                    finalReadbackPassed[0] = postWritesMatch(level, writes);
                    return finalReadbackPassed[0];
                });
        if (!result.success()) {
            ApplyResult residual = residualApplyResult(level, writes);
            return new ApplyResult(false, false, result.rollbackVerified(),
                    residual.completedSurfaceCovers(), residual.completedRevealRemovals());
        }
        int completedSurfaceCovers = count(writes, SubterraneanTrapPlan.Phase.SURFACE_POWDER);
        int completedRevealRemovals = count(writes, SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER);
        return new ApplyResult(true, finalReadbackPassed[0], result.rollbackVerified(),
                completedSurfaceCovers, completedRevealRemovals);
    }

    private static boolean postWritesMatch(WorldGenLevel level, List<WorldWrite> writes) {
        for (WorldWrite write : writes) {
            if (!level.getBlockState(write.position()).equals(write.state())) return false;
        }
        return true;
    }

    private static ApplyResult residualApplyResult(WorldGenLevel level, List<WorldWrite> writes) {
        int surfaces = 0;
        int removals = 0;
        for (WorldWrite write : writes) {
            if (!level.getBlockState(write.position()).equals(write.state())) {
                continue;
            }
            if (write.phase() == SubterraneanTrapPlan.Phase.SURFACE_POWDER) {
                surfaces++;
            } else if (write.phase() == SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER) {
                removals++;
            }
        }
        return new ApplyResult(false, false, false, surfaces, removals);
    }

    private static boolean isPolarBarrens(WorldGenLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey()
                .map(key -> key.identifier().equals(POLAR_BARRENS_ID))
                .orElse(false);
    }

    private static int count(List<WorldWrite> writes, SubterraneanTrapPlan.Phase phase) {
        return (int) writes.stream().filter(write -> write.phase() == phase).count();
    }
}

enum PowderTrapWorldSafetyFailure {
    NONE,
    NOT_CHECKED,
    OUTSIDE_OWNER_CHUNK,
    DUPLICATE_PLANNED_POSITION,
    WRITE_NOT_ALLOWED,
    PLANNED_FLUID,
    PLANNED_BLOCK_ENTITY,
    SURFACE_SNAPSHOT_MISMATCH,
    SURFACE_HEIGHT_MISMATCH,
    SURFACE_ABOVE_UNSAFE,
    WRONG_BIOME,
    REVEAL_LAYER_MISMATCH,
    CUSHION_BASE_UNSAFE,
    CUSHION_BASE_UNANCHORED,
    CUSHION_TARGET_UNSAFE,
    CUSHION_SUPPORT_UNSAFE,
    CLEAR_TARGET_UNSAFE,
    DESCENT_FLOOR_TARGET_UNSAFE,
    DESCENT_FLOOR_SUPPORT_UNSAFE,
    DESCENT_CLEAR_TARGET_UNSAFE,
    DESCENT_SHELL_UNSAFE,
    LOWER_CAVE_DESTINATION_UNSAFE,
    AUTHORED_NOT_WORLDGEN_REGION,
    AUTHORED_CENTER_MISMATCH,
    AUTHORED_OWNER_LOST,
    AUTHORED_NEIGHBOR_UNAVAILABLE,
    AUTHORED_OUTSIDE_WRITE_ZONE,
    AUTHORED_OUTSIDE_SELECTED_FOOTPRINT,
    AUTHORED_CAVERN_SHAPE_MISMATCH,
    AUTHORED_CAVERN_BIOME,
    AUTHORED_CAVERN_SHELL,
    AUTHORED_MAGMA_DILATION,
    AUTHORED_EXISTING_TRAP_OR_CAVERN,
    AUTHORED_CAVERN_UNSAFE,
    OBSOLETE_ROUTE_PHASE,
    /** Legacy telemetry values retained only so older evidence rows remain decodable. */
    ESCAPE_FLOOR_TARGET_UNSAFE,
    ESCAPE_FLOOR_SUPPORT_UNSAFE,
    ESCAPE_CLEAR_TARGET_UNSAFE,
    ESCAPE_TAIL_TARGET_UNSAFE,
    ESCAPE_SURFACE_PLUG_UNSAFE,
    ESCAPE_SHELL_UNSAFE,
    REACHABLE_FLUID_SOURCE,
    SHELL_FLUID,
    SHELL_BLOCK_ENTITY,
    SHELL_GRAVITY
}

record PowderTrapWorldSafetyResult(boolean isSafe, PowderTrapWorldSafetyFailure reason,
                                   String position, String state) {

    static PowderTrapWorldSafetyResult passed() {
        return new PowderTrapWorldSafetyResult(true, PowderTrapWorldSafetyFailure.NONE, "none", "none");
    }

    static PowderTrapWorldSafetyResult notChecked() {
        return new PowderTrapWorldSafetyResult(false, PowderTrapWorldSafetyFailure.NOT_CHECKED, "none", "none");
    }

    static PowderTrapWorldSafetyResult failure(PowderTrapWorldSafetyFailure reason, BlockPos position,
                                               String state) {
        if (reason == PowderTrapWorldSafetyFailure.NONE || reason == PowderTrapWorldSafetyFailure.NOT_CHECKED) {
            throw new IllegalArgumentException("A failure result requires a concrete failure reason");
        }
        return new PowderTrapWorldSafetyResult(false, reason,
                position.getX() + "," + position.getY() + "," + position.getZ(), state);
    }
}

final class PowderTrapWorldSafetyLaw {

    enum CushionBaseAction {
        PRESERVE_EXISTING,
        AUTHOR_BLUE_ICE,
        REJECT
    }

    enum ShellRejection {
        NONE,
        FLUID,
        BLOCK_ENTITY,
        GRAVITY
    }

    record FluidSearchCell(int x, int y, int z) {
    }

    enum FluidTraversalCell {
        BLOCKED,
        PASSABLE,
        FLUID
    }

    record NaturalAnchorCandidate(int x, int z, boolean untouched, boolean dryStable) {
    }

    private PowderTrapWorldSafetyLaw() {
    }

    /**
     * Wet, block-entity, or falling-block neighbours make an immediately adjacent authored volume unsafe.
     * Existing dry air and ordinary dry terrain remain valid cave geometry; this law is applied only to the
     * bounded one-cell shell, so remote gravel does not become a false veto.
     */
    static ShellRejection shellRejection(boolean isAir, boolean blocksMotion, boolean hasFluid,
                                         boolean hasBlockEntity) {
        return shellRejection(isAir, blocksMotion, hasFluid, hasBlockEntity, false);
    }

    static ShellRejection shellRejection(boolean isAir, boolean blocksMotion, boolean hasFluid,
                                         boolean hasBlockEntity, boolean hasGravity) {
        if (hasFluid) {
            return ShellRejection.FLUID;
        }
        if (hasBlockEntity) {
            return ShellRejection.BLOCK_ENTITY;
        }
        if (hasGravity) {
            return ShellRejection.GRAVITY;
        }
        return ShellRejection.NONE;
    }

    static CushionBaseAction cushionBaseAction(boolean isAir, boolean blocksMotion, boolean hasFluid,
                                               boolean hasBlockEntity, boolean hasGravity, boolean writable) {
        if (!writable || hasFluid || hasBlockEntity || hasGravity) {
            return CushionBaseAction.REJECT;
        }
        if (!isAir && blocksMotion) {
            return CushionBaseAction.PRESERVE_EXISTING;
        }
        if (isAir && !blocksMotion) {
            return CushionBaseAction.AUTHOR_BLUE_ICE;
        }
        return CushionBaseAction.REJECT;
    }

    static boolean hasSeparatedNaturalAnchors(Set<NaturalAnchorCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        List<NaturalAnchorCandidate> valid = candidates.stream()
                .filter(candidate -> candidate != null && candidate.untouched() && candidate.dryStable())
                .toList();
        for (int first = 0; first < valid.size(); first++) {
            for (int second = first + 1; second < valid.size(); second++) {
                NaturalAnchorCandidate a = valid.get(first);
                NaturalAnchorCandidate b = valid.get(second);
                if (Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) >= 3) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean descentFloorFinalStateSafe(boolean targetReplaceable, boolean substrateDryStableHard,
                                              boolean targetHasFluid, boolean targetHasBlockEntity,
                                              boolean targetHasGravity, boolean writable) {
        return targetReplaceable && substrateDryStableHard
                && !targetHasFluid && !targetHasBlockEntity
                && !targetHasGravity && writable;
    }

    /** Legacy pure-law alias; active production calls {@link #descentFloorFinalStateSafe}. */
    static boolean escapeFloorFinalStateSafe(boolean targetReplaceable, boolean substrateDryStableHard,
                                             boolean targetHasFluid, boolean targetHasBlockEntity,
                                             boolean targetHasGravity, boolean writable) {
        return descentFloorFinalStateSafe(targetReplaceable, substrateDryStableHard,
                targetHasFluid, targetHasBlockEntity, targetHasGravity, writable);
    }

    static boolean naturalCaveHeadroomSafe(boolean isAir, boolean hasFluid,
                                           boolean hasBlockEntity, boolean hasGravity) {
        return isAir && !hasFluid && !hasBlockEntity && !hasGravity;
    }

    static boolean certifiedSurfaceCapShellSafe(
            int surfaceKind, int capturedFirstAirY, int actualLayerY,
            boolean exactLayerSnapshot, boolean exactlyOneLayer,
            boolean exactSupportSnapshot, boolean supportIsSnowBlock,
            boolean airAbove, boolean polarBiome, boolean supportPlanned,
            boolean hasFluid, boolean hasBlockEntity, boolean hasGravity) {
        return surfaceKind == SubterraneanTrapPlan.THIN_OVER_FULL_SNOW
                && capturedFirstAirY == actualLayerY
                && exactLayerSnapshot && exactlyOneLayer
                && exactSupportSnapshot && supportIsSnowBlock
                && airAbove && polarBiome && !supportPlanned
                && !hasFluid && !hasBlockEntity && !hasGravity;
    }

    static boolean plannedWriteSeedsFluidReachability(
            SubterraneanTrapPlan.Phase phase, boolean retainedTailFloor) {
        return phase == SubterraneanTrapPlan.Phase.CLEAR
                || phase == SubterraneanTrapPlan.Phase.DESCENT_CLEAR
                || phase == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_CLEAR
                || phase == SubterraneanTrapPlan.Phase.CUSHION;
    }

    static FluidTraversalCell fluidTraversalCell(boolean plannedFinalSolid, boolean plannedFuturePassable,
                                                 boolean currentFluid, boolean currentPassable) {
        if (plannedFinalSolid) {
            return FluidTraversalCell.BLOCKED;
        }
        if (currentFluid) {
            return FluidTraversalCell.FLUID;
        }
        if (plannedFuturePassable || currentPassable) {
            return FluidTraversalCell.PASSABLE;
        }
        return FluidTraversalCell.BLOCKED;
    }

    /**
     * The real bounded traversal shared by runtime and tests. Seeds are authored cells that are passable now or
     * become passable when the player falls/mines; authored floors are deliberately absent. Only horizontal and
     * upward reverse-flow edges are followed, because a source below the route cannot flow upward into it.
     */
    static FluidSearchCell firstReachableFluidWithinSeven(
            Set<FluidSearchCell> seeds, Function<FluidSearchCell, FluidTraversalCell> lookup) {
        if (seeds == null || seeds.isEmpty() || lookup == null) {
            return null;
        }
        ArrayDeque<FluidSearchCell> queue = new ArrayDeque<>();
        Map<FluidSearchCell, Integer> distanceByCell = new HashMap<>();
        for (FluidSearchCell seed : seeds) {
            if (seed != null && distanceByCell.putIfAbsent(seed, 0) == null) {
                queue.addLast(seed);
            }
        }
        int[][] upstreamOffsets = {
                {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}
        };
        while (!queue.isEmpty()) {
            FluidSearchCell current = queue.removeFirst();
            int currentDistance = distanceByCell.get(current);
            if (currentDistance >= 7) {
                continue;
            }
            for (int[] offset : upstreamOffsets) {
                FluidSearchCell neighbour = new FluidSearchCell(
                        current.x() + offset[0], current.y() + offset[1], current.z() + offset[2]);
                int distance = currentDistance + 1;
                Integer known = distanceByCell.get(neighbour);
                if (known != null && known <= distance) {
                    continue;
                }
                FluidTraversalCell cell = lookup.apply(neighbour);
                if (cell == FluidTraversalCell.FLUID) {
                    return neighbour;
                }
                if (cell == FluidTraversalCell.PASSABLE && distance < 7) {
                    distanceByCell.put(neighbour, distance);
                    queue.addLast(neighbour);
                }
            }
        }
        return null;
    }

    static boolean certifiedThinSupport(boolean isDry, boolean blocksMotion, boolean hasBlockEntity,
                                        boolean hasGravity, boolean safelyReplaceable) {
        return isDry && blocksMotion && !hasBlockEntity && !hasGravity && safelyReplaceable;
    }

    static boolean exactSnapshotMatches(Object expected, Object current) {
        return expected != null && expected.equals(current);
    }

    /**
     * Pure seam for the runtime breadth-first search: each array element is one consecutive passable step from
     * planned air toward a source. A source can flow into the route only when the whole path is open and no more
     * than seven horizontal/upstream steps away.
     */
    static boolean fluidSourceWithinSevenPassableAirStepsRejects(boolean[] passableSteps) {
        if (passableSteps == null || passableSteps.length == 0 || passableSteps.length > 7) {
            return false;
        }
        for (boolean passable : passableSteps) {
            if (!passable) {
                return false;
            }
        }
        return true;
    }
}
