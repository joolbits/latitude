package com.example.globe.world;

import com.example.globe.core.SubterraneanTrapPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Exact, read-only qualification for the untouched lower natural-cave evidence used by the surface trap.
 *
 * <p>The production feature retains its owner-local 0..15 bounds. Development audits may supply a wider
 * already-loaded window, but both callers share the material, headroom, biome, and eight-floor continuation
 * law here. This class has no writes and never requests chunk generation.
 */
public final class NaturalGlacialCaveQualification {
    public static final int CONTINUATION_SEARCH_CAP = 32;

    private static final List<Direction> HORIZONTAL_DIRECTIONS = List.of(
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    private static final List<Integer> VERTICAL_ORDER = List.of(0, -1, 1);
    private static final Comparator<SubterraneanTrapPlan.RouteCell> ROUTE_CELL_ORDER =
            Comparator.comparingInt(SubterraneanTrapPlan.RouteCell::x)
                    .thenComparingInt(SubterraneanTrapPlan.RouteCell::z)
                    .thenComparingInt(SubterraneanTrapPlan.RouteCell::y);

    private NaturalGlacialCaveQualification() {
    }

    /** Exact production column predicate, with coordinates local to the supplied base. */
    public static boolean naturalCaveColumn(
            WorldGenLevel level, SubterraneanTrapPlan.RouteCell local, int baseX, int baseZ) {
        if (!lowerNaturalFloorYAllowed(local.y())) {
            return false;
        }
        BlockPos floor = new BlockPos(baseX + local.x(), local.y(), baseZ + local.z());
        BlockState floorState = level.getBlockState(floor);
        if (!safeLowerNaturalSupport(level, floor, floorState)
                || !safeLowerNaturalSupport(level, floor.below(), level.getBlockState(floor.below()))) {
            return false;
        }
        for (int dy = 1; dy <= SubterraneanTrapPlan.ROUTE_CLEAR_HEIGHT; dy++) {
            BlockPos air = floor.above(dy);
            BlockState state = level.getBlockState(air);
            if (!PowderTrapWorldSafetyLaw.naturalCaveHeadroomSafe(
                    state.isAir(), !state.getFluidState().isEmpty(),
                    level.getBlockEntity(air) != null, isGravity(state))) {
                return false;
            }
        }
        return LatitudeBiomes.GLACIAL_CAVES_ID.equals(level.getBiome(floor.above()).unwrapKey()
                .map(key -> key.identifier()).orElse(null));
    }

    /**
     * Exact bounded continuation walk. Coordinates are local to {@code baseX/baseZ}; callers choose their
     * allowed window. A probe retains the production route-disjointness rule; {@code null} is a diagnostic
     * cave-volume query with no authored route to avoid.
     */
    public static List<SubterraneanTrapPlan.RouteCell> connectedLowerNaturalFloors(
            WorldGenLevel level, List<SubterraneanTrapPlan.RouteCell> seeds,
            SubterraneanTrapPlan.DestinationProbe routeToAvoid,
            int baseX, int baseZ, int minLocalX, int maxLocalX, int minLocalZ, int maxLocalZ) {
        return connectedLowerNaturalFloors(level, seeds, routeToAvoid, baseX, baseZ,
                minLocalX, maxLocalX, minLocalZ, maxLocalZ,
                cell -> naturalCaveColumn(level, cell, baseX, baseZ));
    }

    /**
     * Same continuation walk with a caller-supplied cache of the exact column predicate. The cache must cover
     * the declared bounds; production uses the overload above, while the diagnostic precomputes it once.
     */
    public static List<SubterraneanTrapPlan.RouteCell> connectedLowerNaturalFloors(
            WorldGenLevel level, List<SubterraneanTrapPlan.RouteCell> seeds,
            SubterraneanTrapPlan.DestinationProbe routeToAvoid,
            int baseX, int baseZ, int minLocalX, int maxLocalX, int minLocalZ, int maxLocalZ,
            Predicate<SubterraneanTrapPlan.RouteCell> naturalColumn) {
        if (seeds == null || seeds.isEmpty() || minLocalX > maxLocalX || minLocalZ > maxLocalZ) {
            return List.of();
        }
        if (naturalColumn == null) {
            return List.of();
        }
        if (seeds.stream().anyMatch(cell -> cell == null
                || cell.x() < minLocalX || cell.x() > maxLocalX
                || cell.z() < minLocalZ || cell.z() > maxLocalZ
                || (routeToAvoid != null
                && !SubterraneanTrapPlan.naturalContinuationColumnAvoidsAuthoredRoute(routeToAvoid, cell))
                || !naturalColumn.test(cell))) {
            return List.of();
        }
        ArrayDeque<SubterraneanTrapPlan.RouteCell> queue = new ArrayDeque<>(seeds);
        Set<SubterraneanTrapPlan.RouteCell> visited = new LinkedHashSet<>(seeds);
        while (!queue.isEmpty() && visited.size() < CONTINUATION_SEARCH_CAP) {
            SubterraneanTrapPlan.RouteCell current = queue.removeFirst();
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                int localX = current.x() + direction.getStepX();
                int localZ = current.z() + direction.getStepZ();
                if (localX < minLocalX || localX > maxLocalX || localZ < minLocalZ || localZ > maxLocalZ) {
                    continue;
                }
                SubterraneanTrapPlan.RouteCell next = null;
                for (int dy : VERTICAL_ORDER) {
                    SubterraneanTrapPlan.RouteCell candidate = new SubterraneanTrapPlan.RouteCell(
                            localX, current.y() + dy, localZ);
                    if (!visited.contains(candidate)
                            && (routeToAvoid == null
                            || SubterraneanTrapPlan.naturalContinuationColumnAvoidsAuthoredRoute(
                                    routeToAvoid, candidate))
                            && naturalColumn.test(candidate)) {
                        next = candidate;
                        break;
                    }
                }
                if (next != null && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        if (visited.size() < SubterraneanTrapPlan.MIN_NATURAL_CONTINUATION_FLOORS) {
            return List.of();
        }
        return visited.stream().sorted(ROUTE_CELL_ORDER).toList();
    }

    public static boolean lowerNaturalFloorYAllowed(int floorY) {
        return floorY > 0;
    }

    /**
     * Pure form of the continuation walk's horizontal-neighbor geometry. This does not change the
     * production walk or its 32-cell evidence cap; read-only diagnostics use it to partition an already
     * qualified set without inventing a second adjacency law.
     */
    public static boolean horizontalContinuationAdjacent(
            SubterraneanTrapPlan.RouteCell first, SubterraneanTrapPlan.RouteCell second) {
        if (first == null || second == null) {
            return false;
        }
        int horizontal = Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z());
        return horizontal == 1 && Math.abs(first.y() - second.y()) <= 1;
    }

    public static boolean lowerNaturalMaterialAllowed(BlockState state) {
        return isCarverReplaceableOrSnow(state)
                && !state.is(Blocks.DEEPSLATE)
                && !state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
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

    private static boolean isCarverReplaceableOrSnow(BlockState state) {
        return state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES) || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.PACKED_ICE);
    }

    private static boolean isGravity(BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }

    private static boolean isOre(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && id.getPath().endsWith("_ore");
    }
}
