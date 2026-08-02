package com.example.globe.world;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cave-only lake dressing: reads a bounded halo of uncovered source water plus prior plain-ice writes, then
 * writes plain ice only over source water owned by the invoking chunk. The 48 x 48 x 48 scan performs one
 * cheap block-state classification per position; biome and neighbor reads happen only for water/ice
 * candidates, and mutable cursors remove the old per-position BlockPos allocation. Bounded telemetry uses
 * {@code -Dlatitude.debugGlacialDressing=true}; the broader {@code latitude.debugCollapse} switch remains
 * a backward-compatible alternate trigger.
 */
public final class GlacialLakeIceFringeFeature extends Feature<NoneFeatureConfiguration> {

    public static Feature<NoneFeatureConfiguration> INSTANCE;
    private static final int HALO = 16;
    private static final Direction[] FACE_DIRECTIONS = Direction.values();
    private static final boolean DEBUG = debugEnabled(
            Boolean.getBoolean("latitude.debugGlacialDressing"),
            Boolean.getBoolean("latitude.debugCollapse"));

    static boolean debugEnabled(boolean dedicated, boolean legacyCollapse) {
        return dedicated || legacyCollapse;
    }

    enum TopologyKind {
        OTHER,
        SOURCE_WATER,
        PLAIN_ICE
    }

    interface TopologyAccess {
        TopologyKind topologyKind(int x, int y, int z);

        boolean isAir(int x, int y, int z);

        boolean isGlacialCavesBiome(int x, int y, int z);

        boolean isMagma(int x, int y, int z);
    }

    record ScanCounts(int visitedBlocks, int biomeQueries, int neighborReads, int allocations) {
    }

    record SurfaceTopology(
            Set<GlacialLakeIceFringePlanner.Cell> cells,
            Set<GlacialLakeIceFringePlanner.Cell> sourceWater,
            ScanCounts counts) {
        SurfaceTopology {
            cells = Set.copyOf(cells);
            sourceWater = Set.copyOf(sourceWater);
        }
    }

    public GlacialLakeIceFringeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Registered unconditionally before registry freeze; biome JSON and the flag gate actual generation. */
    public static void register() {
        INSTANCE = Registry.register(
                BuiltInRegistries.FEATURE,
                Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, "glacial_lake_ice_fringe"),
                new GlacialLakeIceFringeFeature(NoneFeatureConfiguration.CODEC));
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return false;
        }
        WorldGenLevel level = ctx.level();
        BlockState ice = Blocks.ICE.defaultBlockState();
        int baseX = (ctx.origin().getX() >> 4) << 4;
        int baseZ = (ctx.origin().getZ() >> 4) << 4;
        SurfaceTopology topology = readSurfaceTopology(level, baseX, baseZ);
        GlacialLakeIceFringePlanner.OwnerChunk owner = new GlacialLakeIceFringePlanner.OwnerChunk(baseX >> 4, baseZ >> 4);
        List<GlacialLakeIceFringePlanner.Cell> planned = GlacialLakeIceFringePlanner.plan(
                level.getSeed(), topology.cells(), topology.sourceWater(), owner);
        int successfulWrites = 0;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (GlacialLakeIceFringePlanner.Cell cell : planned) {
            position.set(cell.x(), cell.y(), cell.z());
            if (isEligibleSourceWaterForWrite(level, position, neighbor)) {
                if (level.setBlock(position, ice, 2)) {
                    successfulWrites++;
                }
            }
        }
        if (DEBUG && !topology.sourceWater().isEmpty()) {
            GlobeMod.LOGGER.info("[LAT][LAKE_FRINGE] chunk=({},{}) topology={} sourceWater={} planned={} successfulWrites={} biomeQueries={} neighborReads={}",
                    owner.x(), owner.z(), topology.cells().size(), topology.sourceWater().size(), planned.size(),
                    successfulWrites, topology.counts().biomeQueries(), topology.counts().neighborReads());
        }
        return successfulWrites > 0;
    }

    private static SurfaceTopology readSurfaceTopology(
            WorldGenLevel level, int baseX, int baseZ) {
        int minY = Math.max(GlacialLakeIceFringePlanner.MIN_Y, level.getMinY());
        int maxY = Math.min(GlacialLakeIceFringePlanner.MAX_Y, level.getMaxY() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        TopologyAccess access = new TopologyAccess() {
            @Override
            public TopologyKind topologyKind(int x, int y, int z) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (isSourceWater(state)) {
                    return TopologyKind.SOURCE_WATER;
                }
                return state.is(Blocks.ICE) ? TopologyKind.PLAIN_ICE : TopologyKind.OTHER;
            }

            @Override
            public boolean isAir(int x, int y, int z) {
                cursor.set(x, y, z);
                return level.getBlockState(cursor).isAir();
            }

            @Override
            public boolean isGlacialCavesBiome(int x, int y, int z) {
                cursor.set(x, y, z);
                return GlacialLakeIceFringeFeature.isGlacialCavesBiome(level, cursor);
            }

            @Override
            public boolean isMagma(int x, int y, int z) {
                cursor.set(x, y, z);
                return level.getBlockState(cursor).is(Blocks.MAGMA_BLOCK);
            }
        };
        return scanTopology(baseX, baseZ, minY, maxY, access);
    }

    static SurfaceTopology scanTopology(
            int baseX, int baseZ, int minY, int maxY, TopologyAccess access) {
        Set<GlacialLakeIceFringePlanner.Cell> topology = new HashSet<>();
        Set<GlacialLakeIceFringePlanner.Cell> sourceWater = new HashSet<>();
        int visitedBlocks = 0;
        int biomeQueries = 0;
        int neighborReads = 0;
        int allocations = 0;
        int minX = baseX - HALO;
        int maxXExclusive = baseX + 16 + HALO;
        int minZ = baseZ - HALO;
        int maxZExclusive = baseZ + 16 + HALO;
        for (int x = minX; x < maxXExclusive; x++) {
            for (int z = minZ; z < maxZExclusive; z++) {
                for (int y = minY; y <= maxY; y++) {
                    visitedBlocks++;
                    TopologyKind kind = access.topologyKind(x, y, z);
                    if (kind == TopologyKind.OTHER) {
                        continue;
                    }
                    neighborReads++;
                    if (!access.isAir(x, y + 1, z)) {
                        continue;
                    }
                    biomeQueries++;
                    if (!access.isGlacialCavesBiome(x, y, z)) {
                        continue;
                    }
                    boolean magmaNeighbor = false;
                    boolean allMagmaFacesReadable = true;
                    for (Direction direction : FACE_DIRECTIONS) {
                        int neighborX = x + direction.getStepX();
                        int neighborY = y + direction.getStepY();
                        int neighborZ = z + direction.getStepZ();
                        if (neighborX < minX || neighborX >= maxXExclusive
                                || neighborZ < minZ || neighborZ >= maxZExclusive) {
                            // An outer halo cell can see only five of its six magma faces inside the
                            // FEATURES-stage owner+/-1 chunk neighborhood. Unknown is not safe: retain
                            // the full contextual scan, but omit this boundary cell from planner topology.
                            allMagmaFacesReadable = false;
                            break;
                        }
                        neighborReads++;
                        if (access.isMagma(neighborX, neighborY, neighborZ)) {
                            magmaNeighbor = true;
                            break;
                        }
                    }
                    if (allMagmaFacesReadable && !magmaNeighbor) {
                        GlacialLakeIceFringePlanner.Cell cell =
                                new GlacialLakeIceFringePlanner.Cell(x, y, z);
                        allocations++;
                        topology.add(cell);
                        if (kind == TopologyKind.SOURCE_WATER) {
                            sourceWater.add(cell);
                        }
                    }
                }
            }
        }
        return new SurfaceTopology(
                topology,
                sourceWater,
                new ScanCounts(visitedBlocks, biomeQueries, neighborReads, allocations));
    }

    private static boolean isSourceWater(BlockState state) {
        return (state.is(Blocks.WATER) || state.getFluidState().is(FluidTags.WATER))
                && state.getFluidState().isSource();
    }

    private static boolean isEligibleSourceWaterForWrite(
            WorldGenLevel level, BlockPos position, BlockPos.MutableBlockPos neighbor) {
        BlockState state = level.getBlockState(position);
        if (!isSourceWater(state)
                || position.getY() < GlacialLakeIceFringePlanner.MIN_Y
                || position.getY() > GlacialLakeIceFringePlanner.MAX_Y
                || !isGlacialCavesBiome(level, position)) {
            return false;
        }
        neighbor.set(position.getX(), position.getY() + 1, position.getZ());
        return level.getBlockState(neighbor).isAir()
                && !directlyNeighborsMagma(level, position, neighbor);
    }

    /**
     * Preserve MagmaQuenchSweepFeature's flooded classification exactly: it classifies magma as flooded
     * when water occupies any of its six face neighbors. Freezing even one such source in a one-block-deep
     * pool can turn that magma into the dry ice-touching path, so those water cells never enter this plan.
     */
    private static boolean directlyNeighborsMagma(
            WorldGenLevel level, BlockPos position, BlockPos.MutableBlockPos neighbor) {
        for (Direction direction : FACE_DIRECTIONS) {
            neighbor.set(
                    position.getX() + direction.getStepX(),
                    position.getY() + direction.getStepY(),
                    position.getZ() + direction.getStepZ());
            if (level.getBlockState(neighbor).is(Blocks.MAGMA_BLOCK)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGlacialCavesBiome(WorldGenLevel level, BlockPos position) {
        // LevelReader#getBiome routes through BiomeManager's fuzzy eight-quart sampler. At the outer
        // edge of this feature's intentional one-chunk halo, that sampler may select a quart in a fourth
        // chunk, outside the FEATURES-stage WorldGenRegion. Read the stored quart that owns this exact
        // block instead: the full [-16, 31] halo then remains confined to the legal 3 x 3 chunk region.
        return level.getNoiseBiome(
                        QuartPos.fromBlock(position.getX()),
                        QuartPos.fromBlock(position.getY()),
                        QuartPos.fromBlock(position.getZ()))
                .unwrapKey()
                .map(key -> LatitudeBiomes.GLACIAL_CAVES_ID.equals(key.identifier().toString()))
                .orElse(false);
    }
}
