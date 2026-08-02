package com.example.globe.world;

import com.example.globe.core.FrostMoteLaw;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the adapter boundary: the planner does not get to dress arbitrary water just because its biome step ran. */
class GlacialLakeIceFringeFeatureContractTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void dedicatedAndLegacyDebugSwitchesAreIndependentDefaultOffTriggers() {
        assertTrue(GlacialLakeIceFringeFeature.debugEnabled(true, false),
                "the dedicated glacial-dressing property enables lake-fringe telemetry by itself");
        assertTrue(GlacialLakeIceFringeFeature.debugEnabled(false, true),
                "the legacy collapse property remains a backward-compatible alternate trigger");
        assertFalse(GlacialLakeIceFringeFeature.debugEnabled(false, false),
                "lake-fringe telemetry stays off when both default-off properties are false");
    }

    @Test
    void hazardousCaveBandEndsAtY47() {
        assertEquals(0, GlacialLakeIceFringePlanner.MIN_Y);
        assertEquals(47, GlacialLakeIceFringePlanner.MAX_Y,
                "upper polar water and deep-dark water sit outside the hazardous glacial-cave dressing band");
        assertEquals(GlacialLakeIceFringePlanner.MIN_Y, FrostMoteLaw.DRESSING_MIN_Y,
                "world ice and client motes start at the same boundary");
        assertEquals(GlacialLakeIceFringePlanner.MAX_Y, FrostMoteLaw.DRESSING_MAX_Y,
                "world ice and client motes stop at the same boundary");
    }

    @Test
    void adapterRequiresThePerCellBiomeSourceAndUncoveredSurfaceGates() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/GlacialLakeIceFringeFeature.java"));
        assertTrue(source.contains("isGlacialCavesBiome(level, position)"),
                "both read admission and before-write recheck use the per-cell glacial-caves biome gate");
        assertTrue(source.contains("LatitudeBiomes.GLACIAL_CAVES_ID"));
        assertTrue(source.contains("level.getNoiseBiome("),
                "worldgen biome admission reads the exact stored quart without fuzzy neighbor expansion");
        assertTrue(source.contains("QuartPos.fromBlock(position.getX())"));
        assertTrue(source.contains("QuartPos.fromBlock(position.getY())"));
        assertTrue(source.contains("QuartPos.fromBlock(position.getZ())"));
        assertFalse(source.contains("level.getBiome(position)"),
                "BiomeManager's fuzzy block lookup can leave the legal FEATURES-stage region at a halo edge");
        assertTrue(source.contains("state.getFluidState().isSource()"), "flowing water is excluded");
        assertTrue(source.contains("state.is(Blocks.WATER) || state.getFluidState().is(FluidTags.WATER)"),
                "plain source-water blocks remain recognizable when a focused proxy has no loaded fluid tags");
        assertTrue(source.contains("access.isAir(x, y + 1, z)"),
                "covered or submerged topology candidates are rejected before biome/magma work");
        assertTrue(source.contains("level.getBlockState(neighbor).isAir()"),
                "the source-water-only before-write recheck also requires open air");
    }

    @Test
    void magmaAdjacentWaterIsExcludedBeforePlanningAndBeforeWriting() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/GlacialLakeIceFringeFeature.java"));
        assertTrue(source.contains("!directlyNeighborsMagma(level, position, neighbor)"),
                "the shared admission/recheck predicate must preserve every face-adjacent flooded-magma water cell");
        assertTrue(source.contains("for (Direction direction : FACE_DIRECTIONS)"),
                "the exclusion checks all six faces, matching MagmaQuenchSweepFeature flooded classification");
        assertTrue(source.contains("Blocks.MAGMA_BLOCK"));
    }

    @Test
    void scannerUsesPriorPlainIceTopologyAndCandidateGatedMutableReads() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/GlacialLakeIceFringeFeature.java"));
        assertTrue(source.contains("TopologyKind.PLAIN_ICE"),
                "plain ice from a neighboring invocation must remain in the topology");
        assertTrue(source.contains("BlockPos.MutableBlockPos"),
                "the 110,592-position scan reuses mutable cursors");
        assertTrue(source.contains("ScanCounts"),
                "visited, biome, neighbor, and allocation costs are exposed to deterministic proof");
        assertFalse(source.contains("new BlockPos(x, y, z)"),
                "the scan must not allocate one BlockPos per visited coordinate");
    }

    @Test
    void emptyWindowPaysOnlyOneBlockClassificationPerVisitedPosition() {
        GlacialLakeIceFringeFeature.SurfaceTopology result = GlacialLakeIceFringeFeature.scanTopology(
                0, 0, 0, 47, new ConstantTopologyAccess(GlacialLakeIceFringeFeature.TopologyKind.OTHER));
        assertEquals(110_592, result.counts().visitedBlocks());
        assertEquals(0, result.counts().biomeQueries(),
                "before: 110,592 biome queries; after: zero when the window has no water/ice candidates");
        assertEquals(0, result.counts().neighborReads(),
                "air and six-face magma reads are candidate-only");
        assertEquals(0, result.counts().allocations(),
                "the empty scan allocates no per-position BlockPos or planner Cell");
    }

    @Test
    void sourceWaterAndPlainIceAlonePayCandidateCosts() {
        GlacialLakeIceFringeFeature.TopologyAccess access = new GlacialLakeIceFringeFeature.TopologyAccess() {
            @Override
            public GlacialLakeIceFringeFeature.TopologyKind topologyKind(int x, int y, int z) {
                if (x == 0 && y == 0 && z == 0) {
                    return GlacialLakeIceFringeFeature.TopologyKind.SOURCE_WATER;
                }
                if (x == 1 && y == 0 && z == 0) {
                    return GlacialLakeIceFringeFeature.TopologyKind.PLAIN_ICE;
                }
                return GlacialLakeIceFringeFeature.TopologyKind.OTHER;
            }

            @Override
            public boolean isAir(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean isGlacialCavesBiome(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean isMagma(int x, int y, int z) {
                return false;
            }
        };
        GlacialLakeIceFringeFeature.SurfaceTopology result =
                GlacialLakeIceFringeFeature.scanTopology(0, 0, 0, 47, access);
        assertEquals(110_592, result.counts().visitedBlocks());
        assertEquals(2, result.counts().biomeQueries());
        assertEquals(14, result.counts().neighborReads(),
                "two air reads plus six magma-face reads for each of the two candidates");
        assertEquals(2, result.counts().allocations(), "one planner Cell per accepted topology candidate");
        assertEquals(2, result.cells().size());
        assertEquals(1, result.sourceWater().size(), "plain ice contributes topology but is never a write source");
    }

    @Test
    void exactQuartBiomeReadsKeepTheFullHaloInsideItsThreeByThreeChunkRegion() {
        Set<GlacialLakeIceFringePlanner.Cell> haloCorners = Set.of(
                new GlacialLakeIceFringePlanner.Cell(-16, 32, -16),
                new GlacialLakeIceFringePlanner.Cell(-16, 32, 31),
                new GlacialLakeIceFringePlanner.Cell(31, 32, -16),
                new GlacialLakeIceFringePlanner.Cell(31, 32, 31));
        FeatureWorld world = new FeatureWorld(2L, haloCorners);
        GlacialLakeIceFringeFeature feature =
                new GlacialLakeIceFringeFeature(NoneFeatureConfiguration.CODEC);

        feature.place(new FeaturePlaceContext<>(
                Optional.empty(), world.level(), null, null, new BlockPos(0, 32, 0),
                NoneFeatureConfiguration.INSTANCE));

        Set<NoiseBiomeQuery> queries = world.noiseBiomeQueries();
        assertTrue(queries.contains(new NoiseBiomeQuery(-4, 8, -4)),
                "the negative halo corner is classified at its exact owning quart");
        assertTrue(queries.contains(new NoiseBiomeQuery(7, 8, 7)),
                "the positive halo corner is classified at its exact owning quart");
        assertTrue(queries.stream().allMatch(query ->
                        QuartPos.toSection(query.quartX()) >= -1
                                && QuartPos.toSection(query.quartX()) <= 1
                                && QuartPos.toSection(query.quartZ()) >= -1
                                && QuartPos.toSection(query.quartZ()) <= 1),
                "all production biome reads stay inside the owner chunk plus its one-chunk halo");
    }

    @Test
    void everyHaloCornerAccessStaysBoundedAndUnknownOuterMagmaFacesAreExcluded() {
        Set<GlacialLakeIceFringePlanner.Cell> outerCorners = Set.of(
                new GlacialLakeIceFringePlanner.Cell(-16, 32, -16),
                new GlacialLakeIceFringePlanner.Cell(-16, 32, 31),
                new GlacialLakeIceFringePlanner.Cell(31, 32, -16),
                new GlacialLakeIceFringePlanner.Cell(31, 32, 31));
        Set<GlacialLakeIceFringePlanner.Cell> innerCorners = Set.of(
                new GlacialLakeIceFringePlanner.Cell(-15, 32, -15),
                new GlacialLakeIceFringePlanner.Cell(-15, 32, 30),
                new GlacialLakeIceFringePlanner.Cell(30, 32, -15),
                new GlacialLakeIceFringePlanner.Cell(30, 32, 30));
        Set<GlacialLakeIceFringePlanner.Cell> candidates = new HashSet<>(outerCorners);
        candidates.addAll(innerCorners);
        RecordingTopologyAccess access = new RecordingTopologyAccess(candidates, Set.of());

        GlacialLakeIceFringeFeature.SurfaceTopology result =
                GlacialLakeIceFringeFeature.scanTopology(0, 0, 0, 47, access);

        assertEquals(innerCorners, result.cells(),
                "one-cell-inset halo context remains eligible, while an unknown outward magma face is conservative");
        assertEquals(innerCorners, result.sourceWater());
        assertEquals(Set.of(TopologyReadKind.TOPOLOGY_KIND, TopologyReadKind.AIR,
                        TopologyReadKind.BIOME, TopologyReadKind.MAGMA),
                access.readKinds(), "the proxy observes every production scanner access authority");
        for (GlacialLakeIceFringePlanner.Cell corner : outerCorners) {
            assertTrue(access.wasRead(TopologyReadKind.TOPOLOGY_KIND, corner));
            assertTrue(access.wasRead(TopologyReadKind.AIR,
                    new GlacialLakeIceFringePlanner.Cell(corner.x(), corner.y() + 1, corner.z())));
            assertTrue(access.wasRead(TopologyReadKind.BIOME, corner));
            assertTrue(access.wasReadAtHorizontalPosition(TopologyReadKind.MAGMA, corner.x(), corner.z()),
                    "the scanner may inspect available faces but must stop before the unavailable outward face");
        }
        assertTrue(access.reads().stream().allMatch(read ->
                        Math.abs(Math.floorDiv(read.x(), 16)) <= 1
                                && Math.abs(Math.floorDiv(read.z(), 16)) <= 1),
                "topology, air, biome, and magma reads all stay inside owner+/-1 chunks");
    }

    @Test
    void eachHaloEdgeMidpointIndependentlyStopsBeforeItsUnavailableOutwardFace() {
        Set<GlacialLakeIceFringePlanner.Cell> edgeMidpoints = Set.of(
                new GlacialLakeIceFringePlanner.Cell(-16, 32, 0),
                new GlacialLakeIceFringePlanner.Cell(31, 32, 0),
                new GlacialLakeIceFringePlanner.Cell(0, 32, -16),
                new GlacialLakeIceFringePlanner.Cell(0, 32, 31));
        RecordingTopologyAccess access = new RecordingTopologyAccess(edgeMidpoints, Set.of());

        GlacialLakeIceFringeFeature.SurfaceTopology result =
                GlacialLakeIceFringeFeature.scanTopology(0, 0, 0, 47, access);

        assertTrue(result.cells().isEmpty(),
                "west, east, north, and south edge cells each conservatively reject an unknown outward face");
        assertTrue(result.sourceWater().isEmpty());
        for (GlacialLakeIceFringePlanner.Cell midpoint : edgeMidpoints) {
            assertTrue(access.wasRead(TopologyReadKind.TOPOLOGY_KIND, midpoint));
            assertTrue(access.wasRead(TopologyReadKind.AIR,
                    new GlacialLakeIceFringePlanner.Cell(midpoint.x(), midpoint.y() + 1, midpoint.z())));
            assertTrue(access.wasRead(TopologyReadKind.BIOME, midpoint));
            assertTrue(access.wasReadAtHorizontalPosition(TopologyReadKind.MAGMA, midpoint.x(), midpoint.z()));
        }
        assertTrue(access.reads().stream().allMatch(read ->
                        Math.abs(Math.floorDiv(read.x(), 16)) <= 1
                                && Math.abs(Math.floorDiv(read.z(), 16)) <= 1),
                "each independent horizontal guard stops before a distance-two read");
    }

    @Test
    void ownerCellChecksAllSixMagmaFacesAndRejectsAnAdjacentMagmaBlock() {
        GlacialLakeIceFringePlanner.Cell ownerCell =
                new GlacialLakeIceFringePlanner.Cell(0, 32, 0);
        Set<GlacialLakeIceFringePlanner.Cell> sixFaces = new HashSet<>();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            sixFaces.add(new GlacialLakeIceFringePlanner.Cell(
                    ownerCell.x() + direction.getStepX(),
                    ownerCell.y() + direction.getStepY(),
                    ownerCell.z() + direction.getStepZ()));
        }
        RecordingTopologyAccess clearAccess =
                new RecordingTopologyAccess(Set.of(ownerCell), Set.of());
        GlacialLakeIceFringeFeature.SurfaceTopology clearResult =
                GlacialLakeIceFringeFeature.scanTopology(0, 0, 0, 47, clearAccess);
        assertTrue(clearResult.cells().contains(ownerCell));
        assertEquals(sixFaces, clearAccess.readCells(TopologyReadKind.MAGMA),
                "an owner-chunk candidate retains the complete six-face magma exclusion");

        GlacialLakeIceFringePlanner.Cell eastMagma =
                new GlacialLakeIceFringePlanner.Cell(1, 32, 0);
        RecordingTopologyAccess magmaAccess =
                new RecordingTopologyAccess(Set.of(ownerCell), Set.of(eastMagma));
        GlacialLakeIceFringeFeature.SurfaceTopology magmaResult =
                GlacialLakeIceFringeFeature.scanTopology(0, 0, 0, 47, magmaAccess);
        assertFalse(magmaResult.cells().contains(ownerCell),
                "face-adjacent magma still excludes an otherwise eligible owner-chunk water cell");
        assertTrue(magmaAccess.readCells(TopologyReadKind.MAGMA).contains(eastMagma));
    }

    @Test
    void realPlaceFinalWriteRecheckIsBoundedAtBothOwnerXEdgesAndRejectsLateMagma() {
        GlacialLakeIceFringeFeature feature =
                new GlacialLakeIceFringeFeature(NoneFeatureConfiguration.CODEC);
        for (OwnerBoundaryFixture fixture : List.of(
                ownerBoundaryFixture("west", 0, -1),
                ownerBoundaryFixture("east", 15, 1))) {
            Set<GlacialLakeIceFringePlanner.Cell> sixFaces = faceNeighbors(fixture.target());
            FeatureWorld clearWorld = new FeatureWorld(
                    fixture.seed(), fixture.lake(), true, true, Set.of());
            feature.place(new FeaturePlaceContext<>(
                    Optional.empty(), clearWorld.level(), null, null, new BlockPos(0, 32, 0),
                    NoneFeatureConfiguration.INSTANCE));

            assertTrue(clearWorld.iceWriteAttempts().contains(fixture.target()),
                    fixture.label() + " owner-edge fixture must reach the real final write recheck");
            assertTrue(clearWorld.iceWrites().contains(fixture.target()));
            assertTrue(clearWorld.finalBlockStateReads().containsAll(sixFaces),
                    fixture.label() + " owner-edge final recheck reads all six magma faces");
            assertTrue(clearWorld.finalBlockStateReads().stream().allMatch(
                            GlacialLakeIceFringeFeatureContractTest::isInsideOwnerPlusOneChunks),
                    fixture.label() + " owner-edge final reads stay inside the legal generation region");

            FeatureWorld magmaWorld = new FeatureWorld(
                    fixture.seed(), fixture.lake(), true, true, Set.of(fixture.outwardMagma()));
            feature.place(new FeaturePlaceContext<>(
                    Optional.empty(), magmaWorld.level(), null, null, new BlockPos(0, 32, 0),
                    NoneFeatureConfiguration.INSTANCE));

            assertTrue(magmaWorld.finalBlockStateReads().contains(fixture.outwardMagma()),
                    fixture.label() + " outward face is read during the final write recheck");
            assertFalse(magmaWorld.iceWriteAttempts().contains(fixture.target()),
                    fixture.label() + " adjacent magma rejects the planned write before setBlock");
            assertTrue(magmaWorld.finalBlockStateReads().stream().allMatch(
                            GlacialLakeIceFringeFeatureContractTest::isInsideOwnerPlusOneChunks),
                    fixture.label() + " magma rejection never requests a distance-two chunk");
        }
    }

    @Test
    void realPlaceWritesClusteredFringeAcrossABroadChunkBorderLakeAndKeepsTheCoreOpen() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(0, 31, 0, 12, 32);
        GlacialLakeIceFringePlanner.OwnerChunk owner = new GlacialLakeIceFringePlanner.OwnerChunk(0, 0);
        long seed = 2L;
        Set<GlacialLakeIceFringePlanner.Cell> expected =
                Set.copyOf(GlacialLakeIceFringePlanner.plan(seed, lake, lake, owner));
        assertEquals(12, expected.size(), "the fixed seed has twelve owner-A fringe writes");
        FeatureWorld world = new FeatureWorld(seed, lake);
        GlacialLakeIceFringeFeature feature =
                new GlacialLakeIceFringeFeature(NoneFeatureConfiguration.CODEC);

        assertTrue(feature.place(new FeaturePlaceContext<>(
                Optional.empty(), world.level(), null, null, new BlockPos(0, 32, 0),
                NoneFeatureConfiguration.INSTANCE)),
                "the real feature adapter should turn eligible source water into plain ice");

        Set<GlacialLakeIceFringePlanner.Cell> writes = world.iceWrites();
        assertEquals(expected, writes,
                "actual WorldGenLevel writes must match the owner-chunk plan after every live recheck");
        assertTrue(components(writes).stream().anyMatch(component -> component.size() >= 2 && component.size() <= 4),
                "the blocks actually written by Feature.place form at least one connected 2-4 cell floe");
        assertTrue(writes.stream().allMatch(owner::owns),
                "the broad lake crosses chunk borders, but this invocation writes only its owner chunk");
        for (int x = 7; x <= 8; x++) {
            for (int z = 7; z <= 8; z++) {
                GlacialLakeIceFringePlanner.Cell core = new GlacialLakeIceFringePlanner.Cell(x, 32, z);
                assertTrue(world.blockState(core).is(Blocks.WATER),
                        "the measurable 2x2 interior core remains open source water");
            }
        }
    }

    @Test
    void realPlaceReturnsFalseWhenEveryEligibleWriteIsRejected() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(0, 31, 0, 12, 32);
        GlacialLakeIceFringePlanner.OwnerChunk owner = new GlacialLakeIceFringePlanner.OwnerChunk(0, 0);
        long seed = 2L;
        Set<GlacialLakeIceFringePlanner.Cell> expected =
                Set.copyOf(GlacialLakeIceFringePlanner.plan(seed, lake, lake, owner));
        assertFalse(expected.isEmpty(), "the fixture must exercise real eligible write attempts");
        FeatureWorld world = new FeatureWorld(seed, lake, false, true);
        GlacialLakeIceFringeFeature feature =
                new GlacialLakeIceFringeFeature(NoneFeatureConfiguration.CODEC);

        assertFalse(feature.place(new FeaturePlaceContext<>(
                Optional.empty(), world.level(), null, null, new BlockPos(0, 32, 0),
                NoneFeatureConfiguration.INSTANCE)),
                "Feature.place must report false when WorldGenLevel rejects every setBlock attempt");
        assertEquals(expected, world.iceWriteAttempts(),
                "the deterministic eligible plan was attempted, so false cannot mean there were no candidates");
        assertTrue(world.iceWrites().isEmpty(), "rejected setBlock calls must not appear as successful ice writes");
    }

    @Test
    void realPlaceDoesNotDressBelowYZeroOrOutsideGlacialCaves() {
        GlacialLakeIceFringeFeature feature =
                new GlacialLakeIceFringeFeature(NoneFeatureConfiguration.CODEC);
        Set<GlacialLakeIceFringePlanner.Cell> belowZero = squareLake(0, 31, 0, 12, -1);
        FeatureWorld belowWorld = new FeatureWorld(2L, belowZero, true, true);
        assertFalse(feature.place(new FeaturePlaceContext<>(
                Optional.empty(), belowWorld.level(), null, null, new BlockPos(0, -1, 0),
                NoneFeatureConfiguration.INSTANCE)),
                "the real adapter must reject lake dressing below absolute Y0");
        assertTrue(belowWorld.iceWriteAttempts().isEmpty(), "below-Y0 water never reaches setBlock");

        Set<GlacialLakeIceFringePlanner.Cell> nonGlacialLake = squareLake(0, 31, 0, 12, 32);
        FeatureWorld nonGlacialWorld = new FeatureWorld(2L, nonGlacialLake, true, false);
        assertFalse(feature.place(new FeaturePlaceContext<>(
                Optional.empty(), nonGlacialWorld.level(), null, null, new BlockPos(0, 32, 0),
                NoneFeatureConfiguration.INSTANCE)),
                "the real adapter must reject water outside glacial_caves");
        assertTrue(nonGlacialWorld.iceWriteAttempts().isEmpty(),
                "non-glacial water never reaches setBlock");
    }

    private static Set<GlacialLakeIceFringePlanner.Cell> squareLake(
            int minX, int maxX, int minZ, int maxZ, int y) {
        Set<GlacialLakeIceFringePlanner.Cell> cells = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cells.add(new GlacialLakeIceFringePlanner.Cell(x, y, z));
            }
        }
        return cells;
    }

    private static Set<GlacialLakeIceFringePlanner.Cell> faceNeighbors(
            GlacialLakeIceFringePlanner.Cell cell) {
        Set<GlacialLakeIceFringePlanner.Cell> neighbors = new HashSet<>();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            neighbors.add(new GlacialLakeIceFringePlanner.Cell(
                    cell.x() + direction.getStepX(),
                    cell.y() + direction.getStepY(),
                    cell.z() + direction.getStepZ()));
        }
        return Set.copyOf(neighbors);
    }

    private static OwnerBoundaryFixture ownerBoundaryFixture(
            String label, int boundaryX, int outwardStepX) {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(0, 30, 0, 12, 32);
        GlacialLakeIceFringePlanner.OwnerChunk owner =
                new GlacialLakeIceFringePlanner.OwnerChunk(0, 0);
        for (long seed = 0; seed < 10_000; seed++) {
            for (GlacialLakeIceFringePlanner.Cell cell :
                    GlacialLakeIceFringePlanner.plan(seed, lake, lake, owner)) {
                if (cell.x() == boundaryX) {
                    return new OwnerBoundaryFixture(
                            label, seed, lake, cell,
                            new GlacialLakeIceFringePlanner.Cell(
                                    cell.x() + outwardStepX, cell.y(), cell.z()));
                }
            }
        }
        throw new AssertionError("fixture should plan a write on the owner chunk's " + label + " edge");
    }

    private static boolean isInsideOwnerPlusOneChunks(GlacialLakeIceFringePlanner.Cell cell) {
        return Math.abs(Math.floorDiv(cell.x(), 16)) <= 1
                && Math.abs(Math.floorDiv(cell.z(), 16)) <= 1;
    }

    private static List<Set<GlacialLakeIceFringePlanner.Cell>> components(
            Set<GlacialLakeIceFringePlanner.Cell> cells) {
        Set<GlacialLakeIceFringePlanner.Cell> unseen = new HashSet<>(cells);
        List<Set<GlacialLakeIceFringePlanner.Cell>> result = new ArrayList<>();
        while (!unseen.isEmpty()) {
            Set<GlacialLakeIceFringePlanner.Cell> component = new HashSet<>();
            ArrayDeque<GlacialLakeIceFringePlanner.Cell> queue = new ArrayDeque<>();
            GlacialLakeIceFringePlanner.Cell start = unseen.iterator().next();
            unseen.remove(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                GlacialLakeIceFringePlanner.Cell cell = queue.removeFirst();
                component.add(cell);
                for (GlacialLakeIceFringePlanner.Cell neighbor : List.of(
                        new GlacialLakeIceFringePlanner.Cell(cell.x() + 1, cell.y(), cell.z()),
                        new GlacialLakeIceFringePlanner.Cell(cell.x() - 1, cell.y(), cell.z()),
                        new GlacialLakeIceFringePlanner.Cell(cell.x(), cell.y(), cell.z() + 1),
                        new GlacialLakeIceFringePlanner.Cell(cell.x(), cell.y(), cell.z() - 1))) {
                    if (unseen.remove(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            result.add(component);
        }
        return result;
    }

    private static final class FeatureWorld {
        private static final GlacialLakeIceFringePlanner.Cell LAST_TOPOLOGY_SCAN_CELL =
                new GlacialLakeIceFringePlanner.Cell(31, 47, 31);
        private static final ResourceKey<Biome> GLACIAL_CAVES = ResourceKey.create(
                Registries.BIOME, Identifier.fromNamespaceAndPath("globe", "glacial_caves"));
        private static final ResourceKey<Biome> PLAINS = ResourceKey.create(
                Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", "plains"));

        private final long seed;
        private final Set<GlacialLakeIceFringePlanner.Cell> water;
        private final Set<GlacialLakeIceFringePlanner.Cell> ice = new HashSet<>();
        private final Set<GlacialLakeIceFringePlanner.Cell> iceWriteAttempts = new HashSet<>();
        private final Set<NoiseBiomeQuery> noiseBiomeQueries = new HashSet<>();
        private final Set<GlacialLakeIceFringePlanner.Cell> finalOnlyMagma;
        private final Set<GlacialLakeIceFringePlanner.Cell> finalBlockStateReads = new HashSet<>();
        private final boolean writesSucceed;
        private final Holder<Biome> biome;
        private final WorldGenLevel level;
        private boolean topologyScanComplete;

        private FeatureWorld(long seed, Set<GlacialLakeIceFringePlanner.Cell> water) {
            this(seed, water, true, true);
        }

        private FeatureWorld(
                long seed, Set<GlacialLakeIceFringePlanner.Cell> water,
                boolean writesSucceed, boolean glacialBiome) {
            this(seed, water, writesSucceed, glacialBiome, Set.of());
        }

        private FeatureWorld(
                long seed, Set<GlacialLakeIceFringePlanner.Cell> water,
                boolean writesSucceed, boolean glacialBiome,
                Set<GlacialLakeIceFringePlanner.Cell> finalOnlyMagma) {
            this.seed = seed;
            this.water = Set.copyOf(water);
            this.writesSucceed = writesSucceed;
            this.biome = biomeHolder(glacialBiome);
            this.finalOnlyMagma = Set.copyOf(finalOnlyMagma);
            this.level = (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getSeed" -> this.seed;
                        case "getMinY" -> -64;
                        case "getMaxY" -> 320;
                        case "getBiome" -> throw new AssertionError(
                                "fuzzy getBiome(BlockPos) lookup must not be used during lake worldgen");
                        case "getNoiseBiome" -> getNoiseBiome(
                                (int) args[0], (int) args[1], (int) args[2]);
                        case "getBlockState" -> blockState(cell((BlockPos) args[0]));
                        case "setBlock" -> setBlock((BlockPos) args[0], (BlockState) args[1]);
                        case "toString" -> "GlacialLakeIceFringeFeatureContractTest.World";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new AssertionError("unexpected WorldGenLevel call: " + method);
                    });
        }

        private WorldGenLevel level() {
            return level;
        }

        private Set<GlacialLakeIceFringePlanner.Cell> iceWrites() {
            return Set.copyOf(ice);
        }

        private Set<GlacialLakeIceFringePlanner.Cell> iceWriteAttempts() {
            return Set.copyOf(iceWriteAttempts);
        }

        private Set<NoiseBiomeQuery> noiseBiomeQueries() {
            return Set.copyOf(noiseBiomeQueries);
        }

        private Set<GlacialLakeIceFringePlanner.Cell> finalBlockStateReads() {
            return Set.copyOf(finalBlockStateReads);
        }

        private Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
            noiseBiomeQueries.add(new NoiseBiomeQuery(quartX, quartY, quartZ));
            return biome;
        }

        private BlockState blockState(GlacialLakeIceFringePlanner.Cell cell) {
            boolean finalRecheck = topologyScanComplete;
            if (finalRecheck) {
                finalBlockStateReads.add(cell);
            }
            assertTrue(isInsideOwnerPlusOneChunks(cell),
                    "production WorldGenLevel block reads must stay inside owner+/-1 chunks: " + cell);
            BlockState state;
            if (finalRecheck && finalOnlyMagma.contains(cell)) {
                state = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (ice.contains(cell)) {
                state = Blocks.ICE.defaultBlockState();
            } else {
                state = water.contains(cell) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            if (cell.equals(LAST_TOPOLOGY_SCAN_CELL)) {
                topologyScanComplete = true;
            }
            return state;
        }

        private boolean setBlock(BlockPos position, BlockState state) {
            GlacialLakeIceFringePlanner.Cell cell = cell(position);
            assertTrue(state.is(Blocks.ICE), "the lake fringe adapter writes only plain ice");
            assertTrue(water.contains(cell), "the lake fringe adapter writes only fixture source water");
            iceWriteAttempts.add(cell);
            if (!writesSucceed) {
                return false;
            }
            ice.add(cell);
            return true;
        }

        private static GlacialLakeIceFringePlanner.Cell cell(BlockPos position) {
            return new GlacialLakeIceFringePlanner.Cell(position.getX(), position.getY(), position.getZ());
        }

        private static Holder<Biome> biomeHolder(boolean glacialBiome) {
            return Holder.Reference.createStandAlone(
                    new HolderOwner<>() {}, glacialBiome ? GLACIAL_CAVES : PLAINS);
        }
    }

    private record NoiseBiomeQuery(int quartX, int quartY, int quartZ) {
    }

    private record OwnerBoundaryFixture(
            String label,
            long seed,
            Set<GlacialLakeIceFringePlanner.Cell> lake,
            GlacialLakeIceFringePlanner.Cell target,
            GlacialLakeIceFringePlanner.Cell outwardMagma) {
        private OwnerBoundaryFixture {
            lake = Set.copyOf(lake);
        }
    }

    private enum TopologyReadKind {
        TOPOLOGY_KIND,
        AIR,
        BIOME,
        MAGMA
    }

    private record TopologyRead(TopologyReadKind kind, int x, int y, int z) {
    }

    private static final class RecordingTopologyAccess implements GlacialLakeIceFringeFeature.TopologyAccess {
        private final Set<GlacialLakeIceFringePlanner.Cell> candidates;
        private final Set<GlacialLakeIceFringePlanner.Cell> magma;
        private final List<TopologyRead> reads = new ArrayList<>();

        private RecordingTopologyAccess(
                Set<GlacialLakeIceFringePlanner.Cell> candidates,
                Set<GlacialLakeIceFringePlanner.Cell> magma) {
            this.candidates = Set.copyOf(candidates);
            this.magma = Set.copyOf(magma);
        }

        @Override
        public GlacialLakeIceFringeFeature.TopologyKind topologyKind(int x, int y, int z) {
            record(TopologyReadKind.TOPOLOGY_KIND, x, y, z);
            return candidates.contains(new GlacialLakeIceFringePlanner.Cell(x, y, z))
                    ? GlacialLakeIceFringeFeature.TopologyKind.SOURCE_WATER
                    : GlacialLakeIceFringeFeature.TopologyKind.OTHER;
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            record(TopologyReadKind.AIR, x, y, z);
            return true;
        }

        @Override
        public boolean isGlacialCavesBiome(int x, int y, int z) {
            record(TopologyReadKind.BIOME, x, y, z);
            return true;
        }

        @Override
        public boolean isMagma(int x, int y, int z) {
            record(TopologyReadKind.MAGMA, x, y, z);
            return magma.contains(new GlacialLakeIceFringePlanner.Cell(x, y, z));
        }

        private void record(TopologyReadKind kind, int x, int y, int z) {
            reads.add(new TopologyRead(kind, x, y, z));
        }

        private List<TopologyRead> reads() {
            return List.copyOf(reads);
        }

        private Set<TopologyReadKind> readKinds() {
            Set<TopologyReadKind> kinds = new HashSet<>();
            for (TopologyRead read : reads) {
                kinds.add(read.kind());
            }
            return Set.copyOf(kinds);
        }

        private Set<GlacialLakeIceFringePlanner.Cell> readCells(TopologyReadKind kind) {
            Set<GlacialLakeIceFringePlanner.Cell> cells = new HashSet<>();
            for (TopologyRead read : reads) {
                if (read.kind() == kind) {
                    cells.add(new GlacialLakeIceFringePlanner.Cell(read.x(), read.y(), read.z()));
                }
            }
            return Set.copyOf(cells);
        }

        private boolean wasRead(TopologyReadKind kind, GlacialLakeIceFringePlanner.Cell cell) {
            return reads.contains(new TopologyRead(kind, cell.x(), cell.y(), cell.z()));
        }

        private boolean wasReadAtHorizontalPosition(TopologyReadKind kind, int x, int z) {
            return reads.stream().anyMatch(read -> read.kind() == kind && read.x() == x && read.z() == z);
        }
    }

    private record ConstantTopologyAccess(GlacialLakeIceFringeFeature.TopologyKind kind)
            implements GlacialLakeIceFringeFeature.TopologyAccess {
        @Override
        public GlacialLakeIceFringeFeature.TopologyKind topologyKind(int x, int y, int z) {
            return kind;
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean isGlacialCavesBiome(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean isMagma(int x, int y, int z) {
            return false;
        }
    }
}
