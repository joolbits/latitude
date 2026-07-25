package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.SubterraneanTrapLayout;
import com.example.globe.core.SubterraneanTrapPlan;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bounded Polar Barrens powder-snow trap feature. Geometry and surface acceptance live in the pure
 * {@link SubterraneanTrapLayout}/{@link SubterraneanTrapPlan} layers; this adapter performs only world reads,
 * the complete preflight, and the already-ordered writes. No exposed-opening planner remains here.
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

    private record WorldWrite(BlockPos position, BlockState state, SubterraneanTrapPlan.Phase phase) {
    }

    private record RuntimeCandidate(SubterraneanTrapPlan.Plan plan, List<WorldWrite> writes) {
    }

    record PlanSelection<T>(T candidate, int placementOrdinal, int depth, int routeOrdinal) {
    }

    private record ApplyResult(boolean succeeded, int completedSurfaceCovers, int completedRevealRemovals) {
    }

    private record SurfaceSnapshotCounts(int thinOverFull, int thinOther, int fullSnow, int powder, int other) {
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
                    telemetry, PowderTrapWorldSafetyResult.notChecked(),
                    new SurfaceSnapshotCounts(0, 0, 0, 0, 0));
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
                    surfaceKind[localX][localZ] = surfaceKind(top);
                    surfaceSupportSnapshot[localX][localZ] = top;
                    if (surfaceKind[localX][localZ] == SubterraneanTrapPlan.FULL_SNOW) {
                        fullSnow++;
                    } else if (surfaceKind[localX][localZ] == SubterraneanTrapPlan.POWDER) {
                        powder++;
                    } else {
                        other++;
                    }
                }
            }
        }
        SurfaceSnapshotCounts surfaceCounts =
                new SurfaceSnapshotCounts(thinOverFull, thinOther, fullSnow, powder, other);

        int rejectFirstAir = 0;
        int rejectRing = 0;
        int rejectSurface = 0;
        int rejectDepth = 0;
        int eligiblePlacements = 0;
        List<SubterraneanTrapPlan.Plan> acceptedPlans = new ArrayList<>();
        List<SubterraneanTrapLayout.Placement> placements =
                SubterraneanTrapLayout.placements(level.getSeed(), chunkX, chunkZ);
        for (SubterraneanTrapLayout.Placement placement : placements) {
            SubterraneanTrapPlan.Result result = SubterraneanTrapPlan.plan(placement, surfaceFirstAir, surfaceKind);
            if (result.isAccepted()) {
                eligiblePlacements++;
                acceptedPlans.add(result.accepted());
            } else {
                switch (result.rejection()) {
                    case POWDER_RELIEF_EXCEEDS_ONE -> rejectFirstAir++;
                    case APPROACH_RING_UNSTABLE -> rejectRing++;
                    case UNSUPPORTED_SURFACE -> rejectSurface++;
                    case DEPTH_OUTSIDE_HARD_LIMIT, DEPTH_OUTSIDE_LEGAL_RANGE,
                            ESCAPE_ELEVATION_CAPACITY, ESCAPE_OWNER_BOUNDS -> rejectDepth++;
                    case INVALID_INPUT -> rejectSurface++;
                }
            }
        }

        // The final occurrence gate is intentionally delayed until every pure template has been calibrated for DEBUG.
        boolean gate = SubterraneanTrapLayout.chunkGate(level.getSeed(), chunkX, chunkZ);
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

        PlanSelection<RuntimeCandidate> selection = selectFirstSafePlan(
                gate ? placements.size() : 0,
                (placementIndex, depth) -> cushionBaseAnchorEligible(
                        level, placements.get(placementIndex), surfaceFirstAir, depth, baseX, baseZ),
                (placementIndex, depth) -> SubterraneanTrapPlan.planAlternatives(
                                placements.get(placementIndex), surfaceFirstAir, surfaceKind, depth).stream()
                        .map(plan -> new RuntimeCandidate(plan, translate(plan, baseX, baseZ)))
                        .toList(),
                candidate -> {
                    PowderTrapWorldSafetyResult attempt = worldSafe(
                            level, candidate.plan().escapeRoute(), candidate.writes(),
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
        if (selection != null) {
            RuntimeCandidate selected = selection.candidate();
            SubterraneanTrapPlan.Plan candidate = selected.plan();
            List<WorldWrite> writes = selected.writes();
            drop = candidate.roofY() - candidate.landingY();
            // Selection and every complete safety preflight are finished before this sole mutation boundary.
            // Whether apply succeeds or reports a partial batch, no alternative is inspected afterward.
            ApplyResult applied = apply(level, writes);
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
                telemetry, worldSafety, surfaceCounts);
        return encounters == 1;
    }

    static <T> PlanSelection<T> selectFirstSafePlan(
            int placementCount, BiPredicate<Integer, Integer> anchorEligible,
            BiFunction<Integer, Integer, List<T>> planFactory, Predicate<T> worldSafe) {
        if (placementCount <= 0 || anchorEligible == null || planFactory == null || worldSafe == null) {
            return null;
        }
        for (int placementIndex = 0; placementIndex < placementCount; placementIndex++) {
            for (int depth : SubterraneanTrapPlan.preferredDepthOrder()) {
                if (!anchorEligible.test(placementIndex, depth)) {
                    continue;
                }
                List<T> alternatives = planFactory.apply(placementIndex, depth);
                if (alternatives == null || alternatives.isEmpty()) {
                    continue;
                }
                int attemptLimit = Math.min(34, alternatives.size());
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

    private static void debugRow(int chunkX, int chunkZ, boolean gate, int eligiblePlacements, int rejectFirstAir,
                                 int rejectRing, int rejectSurface, int rejectDepth, int rejectWorldSafety,
                                 int writeFailure, int encounters, int powderCovers, int cushions, int clearWrites,
                                 int partialSurfaceCovers, int partialRevealRemovals, int drop,
                                 int selectedPlacementOrdinal, int selectedDepth, int selectedRouteOrdinal,
                                 String firstPowder,
                                 PowderTrapWorldSafetyTelemetry telemetry, PowderTrapWorldSafetyResult worldSafety,
                                 SurfaceSnapshotCounts surfaceCounts) {
        if (!DEBUG) {
            return;
        }
        long call = DEBUG_CALLS.incrementAndGet();
        GlobeMod.LOGGER.info("[LAT][SUBTERRANEAN] call={} chunk=({},{}) gate={} preferredDepthCensus={} "
                        + "preferredDepthEligiblePlacements={} preferredDepthRejectFirstAir={} "
                        + "preferredDepthRejectRing={} preferredDepthRejectSurface={} preferredDepthRejectDepth={} "
                        + "rejectWorldSafety={} "
                        + "writeFailure={} encounters={} powderCovers={} cushions={} clearWrites={} "
                        + "partialSurfaceCovers={} partialRevealRemovals={} drop={} firstPowder={} "
                        + "selectedPlacementOrdinal={} selectedDepth={} selectedRouteOrdinal={} "
                        + "worldSafetyAttempts={} worldSafetyAcceptedAttempt={} worldSafetyReasonCounts={} "
                        + "worldSafetyReasonSemantics=first_failure worldSafetyReason={} "
                        + "worldSafetyPos={} worldSafetyState={} "
                        + "thinOverFull={} thinOther={} fullSnow={} powder={} other={}",
                call, chunkX, chunkZ, gate, SubterraneanTrapPlan.PREFERRED_DEPTH,
                eligiblePlacements, rejectFirstAir, rejectRing, rejectSurface,
                rejectDepth, rejectWorldSafety, writeFailure, encounters, powderCovers, cushions, clearWrites,
                partialSurfaceCovers, partialRevealRemovals, drop, firstPowder,
                selectedPlacementOrdinal, selectedDepth, selectedRouteOrdinal, telemetry.attempts(),
                telemetry.acceptedAttempt(), telemetry.encodedReasonCounts(), worldSafety.reason(),
                worldSafety.position(), worldSafety.state(), surfaceCounts.thinOverFull(), surfaceCounts.thinOther(),
                surfaceCounts.fullSnow(), surfaceCounts.powder(), surfaceCounts.other());
    }

    private static int surfaceKind(BlockState state) {
        if (state.is(Blocks.SNOW_BLOCK)) {
            return SubterraneanTrapPlan.FULL_SNOW;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return SubterraneanTrapPlan.POWDER;
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
                case ESCAPE_FLOOR, ESCAPE_MINE_TAIL -> SNOW_BLOCK;
                case CLEAR, ESCAPE_CLEAR, REMOVE_SURFACE_LAYER -> AIR;
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

    private static PowderTrapWorldSafetyResult worldSafe(WorldGenLevel level,
                                                         SubterraneanTrapPlan.EscapeRoute escapeRoute,
                                                         List<WorldWrite> writes,
                                                         int[][] firstAir, int[][] kinds,
                                                         BlockState[][] supportSnapshots,
                                                         BlockState[][] layerSnapshots, int baseX, int baseZ) {
        Map<BlockPos, WorldWrite> planned = new HashMap<>();
        for (int index = 0; index < writes.size(); index++) {
            WorldWrite write = writes.get(index);
            BlockPos pos = write.position();
            if (pos.getX() < baseX + 1 || pos.getX() > baseX + 14
                    || pos.getZ() < baseZ + 1 || pos.getZ() > baseZ + 14) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.OUTSIDE_OWNER_CHUNK, pos, "unread");
            }
            if (planned.containsKey(pos)) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.DUPLICATE_PLANNED_POSITION, pos, "unread");
            }
            BlockState current = level.getBlockState(pos);
            boolean writable = level.ensureCanWrite(pos);
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
                case ESCAPE_FLOOR -> {
                    BlockState substrate = level.getBlockState(pos.below());
                    boolean targetReplaceable = current.isAir() || isCarverReplaceableOrSnow(current);
                    boolean targetHasFluid = !current.getFluidState().isEmpty();
                    boolean targetHasBlockEntity = level.getBlockEntity(pos) != null;
                    boolean targetHasGravity = isGravity(current);
                    boolean writable = level.ensureCanWrite(pos);
                    boolean substrateDryStableHard =
                            dryStableHardSubstrate(level, pos.below(), substrate);
                    if (!PowderTrapWorldSafetyLaw.escapeFloorFinalStateSafe(
                            targetReplaceable, substrateDryStableHard, targetHasFluid,
                            targetHasBlockEntity, targetHasGravity, writable)) {
                        boolean targetSafe = PowderTrapWorldSafetyLaw.escapeFloorFinalStateSafe(
                                targetReplaceable, true, targetHasFluid,
                                targetHasBlockEntity, targetHasGravity, writable);
                        return PowderTrapWorldSafetyResult.failure(
                                targetSafe
                                        ? PowderTrapWorldSafetyFailure.ESCAPE_FLOOR_SUPPORT_UNSAFE
                                        : PowderTrapWorldSafetyFailure.ESCAPE_FLOOR_TARGET_UNSAFE,
                                targetSafe ? pos.below() : pos,
                                targetSafe ? substrate.toString() : current.toString());
                    }
                }
                case ESCAPE_MINE_TAIL -> {
                    if (!safeEscapeTailTarget(level, pos, current, firstAir, kinds,
                            supportSnapshots, layerSnapshots, baseX, baseZ)) {
                        return PowderTrapWorldSafetyResult.failure(
                                PowderTrapWorldSafetyFailure.ESCAPE_TAIL_TARGET_UNSAFE, pos, current.toString());
                    }
                }
                case CLEAR, ESCAPE_CLEAR -> {
                    if (!safeClearTarget(current)) {
                        return PowderTrapWorldSafetyResult.failure(
                                write.phase() == SubterraneanTrapPlan.Phase.CLEAR
                                        ? PowderTrapWorldSafetyFailure.CLEAR_TARGET_UNSAFE
                                        : PowderTrapWorldSafetyFailure.ESCAPE_CLEAR_TARGET_UNSAFE,
                                pos, current.toString());
                    }
                }
            }
        }

        PowderTrapWorldSafetyResult plugSafety = escapeSurfacePlugSafe(level, escapeRoute, planned,
                firstAir, kinds, supportSnapshots, layerSnapshots, baseX, baseZ);
        if (!plugSafety.isSafe()) {
            return plugSafety;
        }
        PowderTrapWorldSafetyResult routeShell =
                dryStableEscapeShell(level, escapeRoute, planned, firstAir, kinds,
                        supportSnapshots, layerSnapshots, baseX, baseZ);
        if (!routeShell.isSafe()) {
            return routeShell;
        }
        PowderTrapWorldSafetyResult fallShell = dryHazardFreeShell(level, writes, planned.keySet());
        if (!fallShell.isSafe()) {
            return fallShell;
        }
        return reachableFluidSourceSafety(level, writes, escapeRoute, baseX, baseZ);
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

    private static boolean safeEscapeTailTarget(
            WorldGenLevel level, BlockPos pos, BlockState state, int[][] firstAir, int[][] kinds,
            BlockState[][] supportSnapshots, BlockState[][] layerSnapshots, int baseX, int baseZ) {
        int localX = pos.getX() - baseX;
        int localZ = pos.getZ() - baseZ;
        int surfaceSupportY = firstAir[localX][localZ] - 1;
        if (pos.getY() > surfaceSupportY) {
            return false;
        }
        if (pos.getY() == surfaceSupportY) {
            int kind = kinds[localX][localZ];
            return (kind == SubterraneanTrapPlan.FULL_SNOW
                    || kind == SubterraneanTrapPlan.THIN_OVER_FULL_SNOW)
                    && state.is(Blocks.SNOW_BLOCK)
                    && PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                            supportSnapshots[localX][localZ], state)
                    && surfaceAboveIsSafe(level, pos, kind, layerSnapshots[localX][localZ])
                    && isPolarBarrens(level, pos.above());
        }
        return !state.isAir() && state.blocksMotion()
                && isCarverReplaceableOrSnow(state) && !isGravity(state);
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

    private static boolean dryStableHardSubstrate(WorldGenLevel level, BlockPos pos, BlockState state) {
        return finalDryStableSolid(state) && level.getBlockEntity(pos) == null;
    }

    private static PowderTrapWorldSafetyResult escapeSurfacePlugSafe(
            WorldGenLevel level, SubterraneanTrapPlan.EscapeRoute route, Map<BlockPos, WorldWrite> planned,
            int[][] firstAir, int[][] kinds, BlockState[][] supportSnapshots,
            BlockState[][] layerSnapshots, int baseX, int baseZ) {
        SubterraneanTrapPlan.EscapeStep plug = route.surfacePlug();
        if (plug.x() < 1 || plug.x() > 14 || plug.z() < 1 || plug.z() > 14) {
            return PowderTrapWorldSafetyResult.failure(PowderTrapWorldSafetyFailure.ESCAPE_SURFACE_PLUG_UNSAFE,
                    worldPosition(plug, baseX, baseZ), "outside-owner");
        }
        int kind = kinds[plug.x()][plug.z()];
        BlockPos virtualFloor = worldPosition(plug, baseX, baseZ);
        BlockPos supportPos = virtualFloor.above();
        BlockState support = level.getBlockState(supportPos);
        WorldWrite virtualWrite = planned.get(virtualFloor);
        WorldWrite supportWrite = planned.get(supportPos);
        if (!isMineTailSnowWrite(virtualWrite) || !isMineTailSnowWrite(supportWrite)
                || firstAir[plug.x()][plug.z()] - 2 != plug.y()
                || (kind != SubterraneanTrapPlan.FULL_SNOW
                        && kind != SubterraneanTrapPlan.THIN_OVER_FULL_SNOW)
                || !support.is(Blocks.SNOW_BLOCK)
                || !PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                        supportSnapshots[plug.x()][plug.z()], support)
                || !support.blocksMotion() || !support.getFluidState().isEmpty()
                || level.getBlockEntity(supportPos) != null || isGravity(support)
                || !surfaceAboveIsSafe(level, supportPos, kind, layerSnapshots[plug.x()][plug.z()])
                || !isPolarBarrens(level, supportPos.above())) {
            return PowderTrapWorldSafetyResult.failure(PowderTrapWorldSafetyFailure.ESCAPE_SURFACE_PLUG_UNSAFE,
                    supportPos, support.toString());
        }
        return PowderTrapWorldSafetyResult.passed();
    }

    private static boolean isMineTailSnowWrite(WorldWrite write) {
        return write != null && write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL
                && write.state().equals(SNOW_BLOCK);
    }

    private static PowderTrapWorldSafetyResult dryStableEscapeShell(
            WorldGenLevel level, SubterraneanTrapPlan.EscapeRoute route, Map<BlockPos, WorldWrite> planned,
            int[][] firstAir, int[][] kinds, BlockState[][] supportSnapshots,
            BlockState[][] layerSnapshots, int baseX, int baseZ) {
        SubterraneanTrapPlan.EscapeStep closure = route.closureCell();
        BlockPos closurePos = worldPosition(closure, baseX, baseZ);
        boolean closureShellSafe = isDryStableShellCell(level, closurePos)
                || certifiedSurfaceCapShellCell(level, closurePos, planned, firstAir, kinds,
                supportSnapshots, layerSnapshots, baseX, baseZ);
        if (closure.x() < 0 || closure.x() > 15 || closure.z() < 0 || closure.z() > 15
                || planned.containsKey(closurePos) || !closureShellSafe) {
            return PowderTrapWorldSafetyResult.failure(
                    PowderTrapWorldSafetyFailure.ESCAPE_SHELL_UNSAFE, closurePos,
                    level.getBlockState(closurePos).toString());
        }
        SubterraneanTrapPlan.EscapeStep plug = route.surfacePlug();
        BlockPos plugSupport = worldPosition(plug, baseX, baseZ).above();
        BlockPos intendedOpening = plugSupport.above();
        for (SubterraneanTrapPlan.EscapeStep local : route.shellProbes()) {
            BlockPos probe = worldPosition(local, baseX, baseZ);
            if (local.x() < 0 || local.x() > 15 || local.z() < 0 || local.z() > 15) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.ESCAPE_SHELL_UNSAFE, probe, "outside-owner-shell");
            }
            if (planned.containsKey(probe)) {
                continue;
            }
            if (probe.equals(intendedOpening)) {
                if (surfaceAboveIsSafe(level, plugSupport, kinds[plug.x()][plug.z()],
                        layerSnapshots[plug.x()][plug.z()])) {
                    continue;
                }
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.ESCAPE_SHELL_UNSAFE, probe,
                        level.getBlockState(probe).toString());
            }
            if (certifiedSurfaceCapShellCell(level, probe, planned, firstAir, kinds,
                    supportSnapshots, layerSnapshots, baseX, baseZ)) {
                continue;
            }
            if (!isDryStableShellCell(level, probe)) {
                return PowderTrapWorldSafetyResult.failure(
                        PowderTrapWorldSafetyFailure.ESCAPE_SHELL_UNSAFE, probe,
                        level.getBlockState(probe).toString());
            }
        }
        return PowderTrapWorldSafetyResult.passed();
    }

    private static boolean certifiedSurfaceCapShellCell(
            WorldGenLevel level, BlockPos layerPos, Map<BlockPos, WorldWrite> planned,
            int[][] firstAir, int[][] kinds, BlockState[][] supportSnapshots,
            BlockState[][] layerSnapshots, int baseX, int baseZ) {
        int localX = layerPos.getX() - baseX;
        int localZ = layerPos.getZ() - baseZ;
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return false;
        }
        BlockState layer = level.getBlockState(layerPos);
        BlockPos supportPos = layerPos.below();
        BlockState support = level.getBlockState(supportPos);
        boolean exactlyOneLayer =
                layer.is(Blocks.SNOW) && layer.getValue(SnowLayerBlock.LAYERS) == 1;
        return PowderTrapWorldSafetyLaw.certifiedSurfaceCapShellSafe(
                kinds[localX][localZ], firstAir[localX][localZ], layerPos.getY(),
                PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                        layerSnapshots[localX][localZ], layer),
                exactlyOneLayer,
                PowderTrapWorldSafetyLaw.exactSnapshotMatches(
                        supportSnapshots[localX][localZ], support),
                support.is(Blocks.SNOW_BLOCK),
                level.getBlockState(layerPos.above()).isAir(),
                isPolarBarrens(level, layerPos),
                planned.containsKey(supportPos),
                !layer.getFluidState().isEmpty() || !support.getFluidState().isEmpty(),
                level.getBlockEntity(layerPos) != null || level.getBlockEntity(supportPos) != null,
                isGravity(layer) || isGravity(support));
    }

    private static boolean isDryStableShellCell(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.blocksMotion() && state.getFluidState().isEmpty()
                && level.getBlockEntity(pos) == null && !isGravity(state);
    }

    private static BlockPos worldPosition(SubterraneanTrapPlan.EscapeStep local, int baseX, int baseZ) {
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
            WorldGenLevel level, List<WorldWrite> writes, SubterraneanTrapPlan.EscapeRoute route,
            int baseX, int baseZ) {
        Set<BlockPos> retainedTailFloors = new HashSet<>();
        for (SubterraneanTrapPlan.EscapeStep floor : route.tailSteps()) {
            retainedTailFloors.add(worldPosition(floor, baseX, baseZ));
        }
        Set<BlockPos> plannedFinalSolid = new HashSet<>(retainedTailFloors);
        Set<BlockPos> plannedFuturePassable = new HashSet<>();
        for (WorldWrite write : writes) {
            if (write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_FLOOR
                    || write.phase() == SubterraneanTrapPlan.Phase.CUSHION_BASE) {
                plannedFinalSolid.add(write.position());
            }
            boolean retainedTailFloor = write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL
                    && retainedTailFloors.contains(write.position());
            if (PowderTrapWorldSafetyLaw.plannedWriteSeedsFluidReachability(
                    write.phase(), retainedTailFloor)) {
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
        int completedSurfaceCovers = 0;
        int completedRevealRemovals = 0;
        for (WorldWrite write : writes) {
            BlockState before = level.getBlockState(write.position());
            if (before.equals(write.state())) {
                if (write.phase() == SubterraneanTrapPlan.Phase.SURFACE_POWDER) {
                    completedSurfaceCovers++;
                } else if (write.phase() == SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER) {
                    completedRevealRemovals++;
                }
                continue;
            }
            if (!level.setBlock(write.position(), write.state(), Block.UPDATE_ALL)
                    || !level.getBlockState(write.position()).equals(write.state())) {
                return new ApplyResult(false, completedSurfaceCovers, completedRevealRemovals);
            }
            if (write.phase() == SubterraneanTrapPlan.Phase.SURFACE_POWDER) {
                completedSurfaceCovers++;
            } else if (write.phase() == SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER) {
                completedRevealRemovals++;
            }
        }
        for (WorldWrite write : writes) {
            if (!level.getBlockState(write.position()).equals(write.state())) {
                return residualApplyResult(level, writes);
            }
        }
        return new ApplyResult(true, completedSurfaceCovers, completedRevealRemovals);
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
        return new ApplyResult(false, surfaces, removals);
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

    static boolean escapeFloorFinalStateSafe(boolean targetReplaceable, boolean substrateDryStableHard,
                                             boolean targetHasFluid, boolean targetHasBlockEntity,
                                             boolean targetHasGravity, boolean writable) {
        return targetReplaceable && substrateDryStableHard
                && !targetHasFluid && !targetHasBlockEntity
                && !targetHasGravity && writable;
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
                || phase == SubterraneanTrapPlan.Phase.ESCAPE_CLEAR
                || phase == SubterraneanTrapPlan.Phase.CUSHION
                || (phase == SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL && !retainedTailFloor);
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
