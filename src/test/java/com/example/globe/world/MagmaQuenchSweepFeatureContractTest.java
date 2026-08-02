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

        BlockPos subY0Magma = new BlockPos(4, -1, 4);
        BlockPos subY0Ice = subY0Magma.west();
        world.put(subY0Magma, Blocks.MAGMA_BLOCK.defaultBlockState());
        world.put(subY0Ice, Blocks.PACKED_ICE.defaultBlockState());

        assertFalse(feature.place(new FeaturePlaceContext<>(Optional.empty(), world.level(), null, null,
                subY0Magma, NoneFeatureConfiguration.INSTANCE)));
        assertEquals(Blocks.PACKED_ICE, world.block(subY0Ice).getBlock(),
                "the deliberate sub-Y0 cellar exemption remains outside the sweep");
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

    private static final class RecordingWorld {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final boolean acceptWrites;
        private final WorldGenLevel level;
        private int writeAttempts;

        private RecordingWorld() {
            this(true);
        }

        private RecordingWorld(boolean acceptWrites) {
            this.acceptWrites = acceptWrites;
            level = (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getBlockState" -> block((BlockPos) arguments[0]);
                        case "getBiome" -> glacialCavesHolder();
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

        private boolean setBlock(BlockPos pos, BlockState state) {
            writeAttempts++;
            if (!acceptWrites) {
                return false;
            }
            put(pos, state);
            return true;
        }

        private static Holder<Biome> glacialCavesHolder() {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME,
                    Identifier.fromNamespaceAndPath("globe", "glacial_caves"));
            return Holder.Reference.createStandAlone(new HolderOwner<>() { }, key);
        }
    }
}
