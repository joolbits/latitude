package com.example.globe.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract checks for the production sweep and the placement that schedules it. */
class MagmaQuenchSweepFeatureContractTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void dedicatedAndLegacyDebugSwitchesAreIndependentDefaultOffTriggers() {
        assertTrue(MagmaQuenchSweepFeature.debugEnabled(true, false),
                "the dedicated glacial-dressing property enables quench telemetry by itself");
        assertTrue(MagmaQuenchSweepFeature.debugEnabled(false, true),
                "the legacy collapse property remains a backward-compatible alternate trigger");
        assertFalse(MagmaQuenchSweepFeature.debugEnabled(false, false),
                "quench telemetry stays off when both default-off properties are false");
    }

    @Test
    void placementHasNoRandomYGateAndTheRealSweepQuenchesAcrossTheChunkBorder()
            throws IOException {
        JsonArray placement = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/globe/worldgen/placed_feature/magma_quench_sweep.json")))
                .getAsJsonObject().getAsJsonArray("placement");
        assertEquals(1, placement.size(),
                "the sweep owns a whole chunk and must not have a random-Y placement gate");
        assertEquals("minecraft:in_square", placement.get(0).getAsJsonObject().get("type").getAsString(),
                "only the chunk-local X/Z origin may vary; the feature gates each magma by its real biome");

        RecordingWorld world = new RecordingWorld();
        BlockPos magma = new BlockPos(15, 20, 0);
        world.put(magma, Blocks.MAGMA_BLOCK.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos shell = magma.offset(dx, dy, dz);
                    world.put(shell, shell.getX() == 16
                            ? Blocks.WATER.defaultBlockState()
                            : Blocks.PACKED_ICE.defaultBlockState());
                }
            }
        }

        MagmaQuenchSweepFeature feature = new MagmaQuenchSweepFeature(NoneFeatureConfiguration.CODEC);
        assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                magma, NoneFeatureConfiguration.INSTANCE)));
        assertEquals(26, world.writeAttempts(), "every eligible flooded shell cell is attempted once");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        assertEquals(Blocks.OBSIDIAN, world.block(magma.offset(dx, dy, dz)).getBlock(),
                                "the flooded shell must include the neighboring chunk at "
                                        + magma.offset(dx, dy, dz));
                    }
                }
            }
        }
        int writesAfterFirstPass = world.writeAttempts();
        assertFalse(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                magma, NoneFeatureConfiguration.INSTANCE)),
                "an already-quenched shell performs no writes and is not reported as a new success");
        assertEquals(writesAfterFirstPass, world.writeAttempts(),
                "the idempotent second pass must not call setBlock again");

        BlockPos belowBandMagma = new BlockPos(4, MagmaQuenchSweepFeature.SCAN_BOTTOM_Y - 1, 4);
        BlockPos belowBandIce = belowBandMagma.west();
        world.put(belowBandMagma, Blocks.MAGMA_BLOCK.defaultBlockState());
        world.put(belowBandIce, Blocks.PACKED_ICE.defaultBlockState());

        assertFalse(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                belowBandMagma, NoneFeatureConfiguration.INSTANCE)));
        assertEquals(Blocks.PACKED_ICE, world.block(belowBandIce).getBlock(),
                "the deliberate below-diffusion cellar exemption remains outside the sweep");
    }

    @Test
    void finalSweepQuenchesTheWholeSubYZeroIceDiffusionBandWithoutSpillingBelowIt() {
        assertEquals(-10, MagmaQuenchSweepFeature.SCAN_BOTTOM_Y,
                "the sweep bottom derives from the current Y0-to-Y-10 diffusion definition");
        RecordingWorld world = new RecordingWorld();
        BlockPos justBelowY0 = new BlockPos(4, -1, 4);
        BlockPos bandBottom = new BlockPos(10, MagmaQuenchSweepFeature.SCAN_BOTTOM_Y, 4);
        BlockPos cellar = new BlockPos(4, MagmaQuenchSweepFeature.SCAN_BOTTOM_Y - 1, 10);
        world.put(justBelowY0, Blocks.MAGMA_BLOCK.defaultBlockState());
        world.put(bandBottom, Blocks.MAGMA_BLOCK.defaultBlockState());
        world.put(cellar, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(world, justBelowY0, justBelowY0.east());
        fillShell(world, bandBottom, bandBottom.east());
        world.put(bandBottom.below(), Blocks.WATER.defaultBlockState());
        fillShell(world, cellar, cellar.east());

        MagmaQuenchSweepFeature feature = new MagmaQuenchSweepFeature(NoneFeatureConfiguration.CODEC);
        assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                justBelowY0, NoneFeatureConfiguration.INSTANCE)));
        assertSealedObsidianShell(world, justBelowY0);
        assertBandFloorShellQuenchedWithoutSpill(world, bandBottom);
        assertEquals(Blocks.WATER, world.block(cellar.east()).getBlock(),
                "magma below the diffusion band stays untouched");
    }

    @Test
    void exactStoredBiomeQuartControlsBoundaryOwnershipForBothGlacialHosts() {
        RecordingWorld world = new RecordingWorld();
        BlockPos exactGlacial = new BlockPos(4, 20, 4);
        world.put(exactGlacial, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(world, exactGlacial, exactGlacial.east());
        world.setFuzzyBiome("minecraft", "plains");
        world.setStoredBiome("globe", "glacial_caves");

        MagmaQuenchSweepFeature feature = new MagmaQuenchSweepFeature(NoneFeatureConfiguration.CODEC);
        assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                exactGlacial, NoneFeatureConfiguration.INSTANCE)),
                "a glacial-caves quart must quench even when the fuzzy lookup points across its boundary");
        assertSealedObsidianShell(world, exactGlacial);
        assertEquals(0, world.fuzzyBiomeQueries(), "the sweep must not use fuzzy host-biome resolution");

        RecordingWorld polarBarrensWorld = new RecordingWorld();
        BlockPos exactBarrens = new BlockPos(4, 20, 4);
        polarBarrensWorld.put(exactBarrens, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(polarBarrensWorld, exactBarrens, exactBarrens.east());
        polarBarrensWorld.setStoredBiome("globe", "polar_barrens");
        assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(), polarBarrensWorld.level(), null, null,
                exactBarrens, NoneFeatureConfiguration.INSTANCE)),
                "polar-barrens is the second scheduled host biome and must use the same exact quart rule");
        assertSealedObsidianShell(polarBarrensWorld, exactBarrens);

        RecordingWorld nonGlacialWorld = new RecordingWorld();
        BlockPos outsideExactHost = new BlockPos(4, 20, 4);
        nonGlacialWorld.put(outsideExactHost, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(nonGlacialWorld, outsideExactHost, outsideExactHost.east());
        nonGlacialWorld.setFuzzyBiome("globe", "glacial_caves");
        nonGlacialWorld.setStoredBiome("minecraft", "plains");
        assertFalse(feature.place(new FeaturePlaceContext<>(Optional.empty(), nonGlacialWorld.level(), null, null,
                outsideExactHost, NoneFeatureConfiguration.INSTANCE)),
                "a neighboring fuzzy glacial sample must not quench a non-glacial stored quart");
        assertEquals(Blocks.WATER, nonGlacialWorld.block(outsideExactHost.east()).getBlock());
    }

    @Test
    void neighborFinalSweepCatchesVanillaRadiusOneSpillIntoAnAlreadySweptOwner() {
        assertEquals(1, MagmaQuenchSweepFeature.SCAN_HORIZONTAL_HALO,
                "vanilla 26.2 underwater_magma placement_radius_around_floor is exactly one");
        RecordingWorld world = new RecordingWorld();
        MagmaQuenchSweepFeature feature = new MagmaQuenchSweepFeature(NoneFeatureConfiguration.CODEC);
        BlockPos ownerAOrigin = new BlockPos(0, 20, 0);
        assertFalse(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                ownerAOrigin, NoneFeatureConfiguration.INSTANCE)), "owner A sweeps before the spill and sees no magma");

        BlockPos lateSpillIntoA = new BlockPos(15, 20, 4);
        world.put(lateSpillIntoA, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(world, lateSpillIntoA, lateSpillIntoA.east());

        BlockPos ownerBOrigin = new BlockPos(16, 20, 0);
        assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                ownerBOrigin, NoneFeatureConfiguration.INSTANCE)),
                "owner B's final radius-one halo must catch underwater_magma spilled west into owner A");
        assertSealedObsidianShell(world, lateSpillIntoA);
    }

    @Test
    void diagonalShellWaterBeatsFaceIceAndSealsTheFloodedPocket() {
        RecordingWorld world = new RecordingWorld();
        BlockPos magma = new BlockPos(4, 20, 4);
        BlockPos diagonalWater = magma.offset(1, 0, 1);
        world.put(magma, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(world, magma, diagonalWater);

        MagmaQuenchSweepFeature feature = new MagmaQuenchSweepFeature(NoneFeatureConfiguration.CODEC);
        assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                magma, NoneFeatureConfiguration.INSTANCE)));
        assertSealedObsidianShell(world, magma);
    }

    @Test
    void sweepStatisticsCountAcceptedAndRejectedWritesAndPostWriteResiduals() {
        BlockPos magma = new BlockPos(4, 20, 4);

        RecordingWorld acceptedWorld = new RecordingWorld();
        acceptedWorld.put(magma, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(acceptedWorld, magma, magma.east());
        MagmaQuenchSweepFeature.SweepStats accepted =
                MagmaQuenchSweepFeature.sweep(acceptedWorld.level(), 0, 0);
        assertEquals(1, accepted.candidateMagma());
        assertEquals(1, accepted.flooded());
        assertEquals(0, accepted.dry());
        assertEquals(26, accepted.attemptedWrites());
        assertEquals(26, accepted.successfulWrites());
        assertEquals(0, accepted.failedWrites());
        assertEquals(0, accepted.residualShellCells());
        assertTrue(accepted.completedWithoutResidual());

        RecordingWorld rejectedWorld = new RecordingWorld(false);
        rejectedWorld.put(magma, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(rejectedWorld, magma, magma.east());
        MagmaQuenchSweepFeature.SweepStats rejected =
                MagmaQuenchSweepFeature.sweep(rejectedWorld.level(), 0, 0);
        assertEquals(1, rejected.candidateMagma());
        assertEquals(1, rejected.flooded());
        assertEquals(0, rejected.dry());
        assertEquals(26, rejected.attemptedWrites());
        assertEquals(0, rejected.successfulWrites());
        assertEquals(26, rejected.failedWrites());
        assertEquals(26, rejected.residualShellCells());
        assertFalse(rejected.completedWithoutResidual());
    }

    @Test
    void realPlaceDoesNotCallRejectedWritesAsSuccess() {
        RecordingWorld world = new RecordingWorld(false);
        BlockPos magma = new BlockPos(4, 20, 4);
        world.put(magma, Blocks.MAGMA_BLOCK.defaultBlockState());
        fillShell(world, magma, magma.east());

        MagmaQuenchSweepFeature feature = new MagmaQuenchSweepFeature(NoneFeatureConfiguration.CODEC);
        assertFalse(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                magma, NoneFeatureConfiguration.INSTANCE)),
                "a sweep whose writes were all rejected must fail closed");
        assertEquals(26, world.writeAttempts());
        assertTrue(world.block(magma.east()).is(Blocks.WATER),
                "the rejected write remains a measurable residual water cell");
    }

    @Test
    void debugLineAndBiomePreviewForwardingExposeWriteTruthDefaultOff() throws IOException {
        MagmaQuenchSweepFeature.SweepStats stats =
                new MagmaQuenchSweepFeature.SweepStats(3, 1, 1, 17, 13, 4, 4);
        assertEquals(
                "[LAT][QUENCH] chunk=(4,-3) invoked=true candidateMagma=3 flooded=1 dry=1"
                        + " attemptedWrites=17 successfulWrites=13 failedWrites=4 residualShellCells=4",
                MagmaQuenchSweepFeature.debugLine(4, -3, stats));

        String buildScript = Files.readString(Path.of("build.gradle"));
        String dedicatedForwarding =
                "vmArg \"-Dlatitude.debugGlacialDressing=${System.getProperty('latitude.debugGlacialDressing', 'false')}\"";
        String legacyForwarding =
                "vmArg \"-Dlatitude.debugCollapse=${System.getProperty('latitude.debugCollapse', 'false')}\"";
        assertTrue(buildScript.contains(dedicatedForwarding),
                "biomePreview must forward the dedicated glacial-dressing property default false");
        assertEquals(buildScript.indexOf(dedicatedForwarding), buildScript.lastIndexOf(dedicatedForwarding),
                "the dedicated debug property is forwarded exactly once");
        assertTrue(buildScript.contains(legacyForwarding),
                "biomePreview must preserve the legacy default-off collapse property");
        assertEquals(buildScript.indexOf(legacyForwarding), buildScript.lastIndexOf(legacyForwarding),
                "the legacy debug property is forwarded exactly once");
    }

    private static void fillShell(RecordingWorld world, BlockPos magma, BlockPos waterCell) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos shell = magma.offset(dx, dy, dz);
                    world.put(shell, shell.equals(waterCell)
                            ? Blocks.WATER.defaultBlockState()
                            : Blocks.PACKED_ICE.defaultBlockState());
                }
            }
        }
    }

    private static void assertSealedObsidianShell(RecordingWorld world, BlockPos magma) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        assertEquals(Blocks.OBSIDIAN, world.block(magma.offset(dx, dy, dz)).getBlock(),
                                "flooded magma shell cell must seal at " + magma.offset(dx, dy, dz));
                    }
                }
            }
        }
    }

    private static void assertBandFloorShellQuenchedWithoutSpill(RecordingWorld world, BlockPos magma) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos shell = magma.offset(dx, dy, dz);
                    if (shell.getY() >= MagmaQuenchSweepFeature.SCAN_BOTTOM_Y) {
                        assertEquals(Blocks.OBSIDIAN, world.block(shell).getBlock(),
                                "in-band shell cell must quench at " + shell);
                    } else {
                        assertEquals(shell.equals(magma.below()) ? Blocks.WATER : Blocks.PACKED_ICE,
                                world.block(shell).getBlock(),
                                "below-band water/ice must remain untouched at " + shell);
                    }
                }
            }
        }
    }

    private static final class RecordingWorld {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final boolean acceptWrites;
        private final WorldGenLevel level;
        private int writeAttempts;
        private int fuzzyBiomeQueries;
        private ResourceKey<Biome> fuzzyBiome = biomeKey("globe", "glacial_caves");
        private ResourceKey<Biome> storedBiome = biomeKey("globe", "glacial_caves");

        private RecordingWorld() {
            this(true);
        }

        private RecordingWorld(boolean acceptWrites) {
            this.acceptWrites = acceptWrites;
            level = (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getBlockState" -> block((BlockPos) arguments[0]);
                        case "getBiome" -> {
                            fuzzyBiomeQueries++;
                            yield biomeHolder(fuzzyBiome);
                        }
                        case "getNoiseBiome" -> biomeHolder(storedBiome);
                        case "setBlock" -> setBlock(
                                (BlockPos) arguments[0], (BlockState) arguments[1]);
                        case "getMinY" -> -64;
                        case "getMaxY" -> 320;
                        case "getHeight" -> 384;
                        case "toString" -> "MagmaQuenchSweepFeatureContractTest.RecordingWorld";
                        default -> throw new UnsupportedOperationException(method.toString());
                    });
        }

        WorldGenLevel level() {
            return level;
        }

        BlockState block(BlockPos pos) {
            return blocks.getOrDefault(pos.immutable(), Blocks.STONE.defaultBlockState());
        }

        void put(BlockPos pos, BlockState state) {
            blocks.put(pos.immutable(), state);
        }

        int writeAttempts() {
            return writeAttempts;
        }

        int fuzzyBiomeQueries() {
            return fuzzyBiomeQueries;
        }

        void setFuzzyBiome(String namespace, String path) {
            fuzzyBiome = biomeKey(namespace, path);
        }

        void setStoredBiome(String namespace, String path) {
            storedBiome = biomeKey(namespace, path);
        }

        private boolean setBlock(BlockPos pos, BlockState state) {
            writeAttempts++;
            if (!acceptWrites) {
                return false;
            }
            put(pos, state);
            return true;
        }

        private static ResourceKey<Biome> biomeKey(String namespace, String path) {
            return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(namespace, path));
        }

        private static Holder<Biome> biomeHolder(ResourceKey<Biome> key) {
            return Holder.Reference.createStandAlone(new HolderOwner<>() { }, key);
        }
    }
}
