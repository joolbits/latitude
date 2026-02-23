package com.example.globe.dev;

import com.example.globe.GlobeMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ChunkBiomeDataS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public final class ChunkRegenerator {
    private static final int SET_BLOCK_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    private ChunkRegenerator() {
    }

    public static int regenSquare(
            ServerWorld realWorld,
            int centerChunkX,
            int centerChunkZ,
            int radiusChunks,
            boolean copyBiomes,
            OptionalLong seedOverride,
            ServerCommandSource source
    ) {
        int clampedRadius = Math.max(0, Math.min(8, radiusChunks));
        List<ChunkPos> targets = computeChunkSquare(centerChunkX, centerChunkZ, clampedRadius);
        long seed = seedOverride.orElse(realWorld.getSeed());
        long startNanos = System.nanoTime();

        source.sendFeedback(
                () -> Text.literal("[latdev] regenChunk start center=(" + centerChunkX + "," + centerChunkZ + ")"
                        + " radius=" + clampedRadius
                        + " chunks=" + targets.size()
                        + " biomes=" + copyBiomes
                        + " seed=" + seed),
                false
        );

        Path tempDir = null;
        GeneratorOptions previousGeneratorOptions = null;
        try {
            tempDir = Files.createTempDirectory("latdev-regen");
            LevelStorage levelStorage = LevelStorage.create(tempDir);
            try (LevelStorage.Session session = levelStorage.createSession("LatDevTempGen")) {
                SaveProperties saveProperties = realWorld.getServer().getSaveProperties();
                if (seedOverride.isPresent()) {
                    previousGeneratorOptions = saveProperties.getGeneratorOptions();
                    GeneratorOptions patched = previousGeneratorOptions.withSeed(OptionalLong.of(seed));
                    setGeneratorOptions(saveProperties, patched);
                }

                DimensionOptions dimensionOptions = new DimensionOptions(
                        realWorld.getDimensionEntry(),
                        realWorld.getChunkManager().getChunkGenerator()
                );
                ServerWorldProperties worldProperties = asServerWorldProperties(realWorld);

                try (ServerWorld tempWorld = new ServerWorld(
                        realWorld.getServer(),
                        Util.getMainWorkerExecutor(),
                        session,
                        worldProperties,
                        realWorld.getRegistryKey(),
                        dimensionOptions,
                        realWorld.isDebugWorld(),
                        seed,
                        List.of(),
                        false,
                        realWorld.getRandomSequences()
                )) {
                    Map<Long, Chunk> generated = generateToFeatures(tempWorld, targets);
                    RegenStats stats = copyIntoLiveWorld(realWorld, targets, generated, copyBiomes);

                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    source.sendFeedback(
                            () -> Text.literal("[latdev] regenChunk done chunks=" + targets.size()
                                    + " blocksChanged=" + stats.blocksChanged
                                    + " blockEntities=" + stats.blockEntitiesApplied
                                    + " blockEntitiesCleared=" + stats.blockEntitiesCleared
                                    + " biomes=" + (copyBiomes ? "copied" : "skipped")
                                    + " ms=" + elapsedMs),
                            true
                    );
                }
            }
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("[latdev] regenChunk failed: " + e.getMessage()));
            GlobeMod.LOGGER.error("latdev regenChunk failed", e);
            return 0;
        } finally {
            if (previousGeneratorOptions != null) {
                try {
                    setGeneratorOptions(realWorld.getServer().getSaveProperties(), previousGeneratorOptions);
                } catch (Exception restoreError) {
                    GlobeMod.LOGGER.warn("Failed to restore generator options after regen", restoreError);
                }
            }
            if (tempDir != null) {
                deleteTempDirectory(tempDir);
            }
        }
    }

    private static Map<Long, Chunk> generateToFeatures(ServerWorld tempWorld, List<ChunkPos> targets) {
        List<CompletableFuture<OptionalChunk<Chunk>>> futures = new ArrayList<>(targets.size());
        for (ChunkPos chunkPos : targets) {
            futures.add(tempWorld.getChunkManager().getChunkFutureSyncOnMainThread(
                    chunkPos.x,
                    chunkPos.z,
                    ChunkStatus.FEATURES,
                    true
            ));
        }

        CompletableFuture<?>[] allFutures = futures.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(allFutures).join();

        Map<Long, Chunk> generated = new HashMap<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            ChunkPos chunkPos = targets.get(i);
            OptionalChunk<Chunk> maybeChunk = futures.get(i).join();
            if (!maybeChunk.isPresent()) {
                throw new IllegalStateException("Failed to generate chunk " + chunkPos.x + "," + chunkPos.z
                        + " (" + maybeChunk.getError() + ")");
            }
            generated.put(chunkPos.toLong(), maybeChunk.orElseThrow(() ->
                    new IllegalStateException("Chunk " + chunkPos.x + "," + chunkPos.z + " unavailable")));
        }
        return generated;
    }

    private static RegenStats copyIntoLiveWorld(
            ServerWorld realWorld,
            List<ChunkPos> targets,
            Map<Long, Chunk> generated,
            boolean copyBiomes
    ) {
        RegenStats stats = new RegenStats();
        RegistryWrapper.WrapperLookup registries = realWorld.getRegistryManager();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        List<WorldChunk> biomeRefreshChunks = copyBiomes ? new ArrayList<>(targets.size()) : List.of();
        ServerLightingProvider lightingProvider = realWorld.getChunkManager().getLightingProvider();

        int minY = realWorld.getBottomY();
        int maxY = minY + realWorld.getHeight();

        for (ChunkPos chunkPos : targets) {
            Chunk sourceChunk = generated.get(chunkPos.toLong());
            if (sourceChunk == null) {
                throw new IllegalStateException("Missing generated chunk " + chunkPos.x + "," + chunkPos.z);
            }
            WorldChunk targetChunk = realWorld.getChunkManager().getWorldChunk(chunkPos.x, chunkPos.z);
            if (targetChunk == null) {
                throw new IllegalStateException("Target chunk not loaded " + chunkPos.x + "," + chunkPos.z);
            }

            int startX = chunkPos.getStartX();
            int startZ = chunkPos.getStartZ();

            for (int localX = 0; localX < 16; localX++) {
                int worldX = startX + localX;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldZ = startZ + localZ;
                    for (int y = minY; y < maxY; y++) {
                        cursor.set(worldX, y, worldZ);
                        BlockState sourceState = sourceChunk.getBlockState(cursor);
                        BlockState targetState = realWorld.getBlockState(cursor);
                        NbtCompound sourceBlockEntityNbt = sourceChunk.getPackedBlockEntityNbt(cursor, registries);

                        boolean stateChanged = !targetState.equals(sourceState);
                        if (stateChanged) {
                            realWorld.setBlockState(cursor, sourceState, SET_BLOCK_FLAGS, 0);
                            stats.blocksChanged++;
                        }

                        if (sourceBlockEntityNbt != null) {
                            realWorld.removeBlockEntity(cursor);
                            BlockEntity recreated = BlockEntity.createFromNbt(
                                    cursor,
                                    sourceState,
                                    sourceBlockEntityNbt.copy(),
                                    registries
                            );
                            if (recreated != null) {
                                realWorld.addBlockEntity(recreated);
                                recreated.markDirty();
                                stats.blockEntitiesApplied++;
                            }
                        } else if (!stateChanged && realWorld.getBlockEntity(cursor) != null) {
                            realWorld.removeBlockEntity(cursor);
                            stats.blockEntitiesCleared++;
                        }
                    }
                }
            }

            if (copyBiomes) {
                copyChunkBiomes(sourceChunk, targetChunk);
                biomeRefreshChunks.add(targetChunk);
            }

            targetChunk.markNeedsSaving();
            lightingProvider.propagateLight(chunkPos);
        }

        if (copyBiomes && !biomeRefreshChunks.isEmpty()) {
            ChunkBiomeDataS2CPacket biomePacket = ChunkBiomeDataS2CPacket.create(biomeRefreshChunks);
            for (ServerPlayerEntity player : realWorld.getPlayers()) {
                player.networkHandler.sendPacket(biomePacket);
            }
        }

        for (int i = 0; i < 4096; i++) {
            if (lightingProvider.doLightUpdates() <= 0) {
                break;
            }
        }

        return stats;
    }

    @SuppressWarnings("unchecked")
    private static void copyChunkBiomes(Chunk sourceChunk, WorldChunk targetChunk) {
        ChunkSection[] sourceSections = sourceChunk.getSectionArray();
        ChunkSection[] targetSections = targetChunk.getSectionArray();
        int sectionCount = Math.min(sourceSections.length, targetSections.length);

        for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
            ChunkSection sourceSection = sourceSections[sectionIndex];
            ChunkSection targetSection = targetSections[sectionIndex];

            PalettedContainer<RegistryEntry<Biome>> sourceBiomes =
                    (PalettedContainer<RegistryEntry<Biome>>) sourceSection.getBiomeContainer().copy();
            PalettedContainer<RegistryEntry<Biome>> targetBiomes =
                    (PalettedContainer<RegistryEntry<Biome>>) targetSection.getBiomeContainer();

            targetBiomes.lock();
            try {
                for (int biomeX = 0; biomeX < 4; biomeX++) {
                    for (int biomeY = 0; biomeY < 4; biomeY++) {
                        for (int biomeZ = 0; biomeZ < 4; biomeZ++) {
                            targetBiomes.set(biomeX, biomeY, biomeZ, sourceBiomes.get(biomeX, biomeY, biomeZ));
                        }
                    }
                }
            } finally {
                targetBiomes.unlock();
            }
        }
    }

    private static List<ChunkPos> computeChunkSquare(int centerChunkX, int centerChunkZ, int radiusChunks) {
        int side = (radiusChunks * 2) + 1;
        List<ChunkPos> chunks = new ArrayList<>(side * side);
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                chunks.add(new ChunkPos(centerChunkX + dx, centerChunkZ + dz));
            }
        }
        return chunks;
    }

    private static ServerWorldProperties asServerWorldProperties(ServerWorld world) {
        if (world.getLevelProperties() instanceof ServerWorldProperties properties) {
            return properties;
        }
        throw new IllegalStateException("World properties do not implement ServerWorldProperties");
    }

    private static void setGeneratorOptions(SaveProperties saveProperties, GeneratorOptions generatorOptions) throws Exception {
        if (!(saveProperties instanceof LevelProperties levelProperties)) {
            throw new IllegalStateException("Unsupported save properties type: " + saveProperties.getClass().getName());
        }

        Field generatorOptionsField = LevelProperties.class.getDeclaredField("generatorOptions");
        generatorOptionsField.setAccessible(true);
        generatorOptionsField.set(levelProperties, generatorOptions);
    }

    private static void deleteTempDirectory(Path tempDir) {
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    GlobeMod.LOGGER.debug("Failed to delete temp regen path {}", path, e);
                }
            });
        } catch (IOException e) {
            GlobeMod.LOGGER.debug("Failed to clean regen temp dir {}", tempDir, e);
        }
    }

    private static final class RegenStats {
        private int blocksChanged;
        private int blockEntitiesApplied;
        private int blockEntitiesCleared;
    }
}
