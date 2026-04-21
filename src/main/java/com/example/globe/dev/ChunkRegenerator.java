package com.example.globe.dev;

import com.example.globe.GlobeMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public final class ChunkRegenerator {
    private static final int SET_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private ChunkRegenerator() {
    }

    public static int regenSquare(
            ServerLevel realWorld,
            int centerChunkX,
            int centerChunkZ,
            int radiusChunks,
            boolean copyBiomes,
            OptionalLong seedOverride,
            CommandSourceStack source
    ) {
        int clampedRadius = Math.max(0, Math.min(8, radiusChunks));
        List<ChunkPos> targets = computeChunkSquare(centerChunkX, centerChunkZ, clampedRadius);
        long seed = seedOverride.orElse(realWorld.getSeed());
        long startNanos = System.nanoTime();

        source.sendSuccess(
                () -> Component.literal("[latdev] regenChunk start center=(" + centerChunkX + "," + centerChunkZ + ")"
                        + " radius=" + clampedRadius
                        + " chunks=" + targets.size()
                        + " biomes=" + copyBiomes
                        + " seed=" + seed),
                false
        );

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("latdev-regen");
            LevelStorageSource levelStorage = LevelStorageSource.createDefault(tempDir);
            try (LevelStorageSource.LevelStorageAccess session = levelStorage.validateAndCreateAccess("LatDevTempGen")) {
                LevelStem dimensionOptions = new LevelStem(
                        realWorld.dimensionTypeRegistration(),
                        realWorld.getChunkSource().getGenerator()
                );
                ServerLevelData worldProperties = asServerWorldProperties(realWorld);

                try (ServerLevel tempWorld = new ServerLevel(
                        realWorld.getServer(),
                        Util.backgroundExecutor(),
                        session,
                        worldProperties,
                        realWorld.dimension(),
                        dimensionOptions,
                        realWorld.isDebug(),
                        seed,
                        List.of(),
                        false
                )) {
                    Map<Long, ChunkAccess> generated = generateToFeatures(tempWorld, targets);
                    RegenStats stats = copyIntoLiveWorld(realWorld, targets, generated, copyBiomes);

                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    source.sendSuccess(
                            () -> Component.literal("[latdev] regenChunk done chunks=" + targets.size()
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
            source.sendFailure(Component.literal("[latdev] regenChunk failed: " + e.getMessage()));
            GlobeMod.LOGGER.error("latdev regenChunk failed", e);
            return 0;
        } finally {
            if (tempDir != null) {
                deleteTempDirectory(tempDir);
            }
        }
    }

    private static Map<Long, ChunkAccess> generateToFeatures(ServerLevel tempWorld, List<ChunkPos> targets) {
        List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new ArrayList<>(targets.size());
        for (ChunkPos chunkPos : targets) {
            futures.add(tempWorld.getChunkSource().getChunkFuture(
                    chunkPos.x(),
                    chunkPos.z(),
                    ChunkStatus.FEATURES,
                    true
            ));
        }

        CompletableFuture<?>[] allFutures = futures.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(allFutures).join();

        Map<Long, ChunkAccess> generated = new HashMap<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            ChunkPos chunkPos = targets.get(i);
            ChunkResult<ChunkAccess> maybeChunk = futures.get(i).join();
            if (!maybeChunk.isSuccess()) {
                throw new IllegalStateException("Failed to generate chunk " + chunkPos.x() + "," + chunkPos.z()
                        + " (" + maybeChunk.getError() + ")");
            }
            generated.put(chunkPos.pack(), maybeChunk.orElseThrow(() ->
                    new IllegalStateException("Chunk " + chunkPos.x() + "," + chunkPos.z() + " unavailable")));
        }
        return generated;
    }

    private static RegenStats copyIntoLiveWorld(
            ServerLevel realWorld,
            List<ChunkPos> targets,
            Map<Long, ChunkAccess> generated,
            boolean copyBiomes
    ) {
        RegenStats stats = new RegenStats();
        HolderLookup.Provider registries = realWorld.registryAccess();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<LevelChunk> biomeRefreshChunks = copyBiomes ? new ArrayList<>(targets.size()) : List.of();
        ThreadedLevelLightEngine lightingProvider = realWorld.getChunkSource().getLightEngine();

        int minY = realWorld.getMinY();
        int maxY = minY + realWorld.getHeight();

        for (ChunkPos chunkPos : targets) {
            ChunkAccess sourceChunk = generated.get(chunkPos.pack());
            if (sourceChunk == null) {
                throw new IllegalStateException("Missing generated chunk " + chunkPos.x() + "," + chunkPos.z());
            }
            LevelChunk targetChunk = realWorld.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
            if (targetChunk == null) {
                throw new IllegalStateException("Target chunk not loaded " + chunkPos.x() + "," + chunkPos.z());
            }

            int startX = chunkPos.getMinBlockX();
            int startZ = chunkPos.getMinBlockZ();

            for (int localX = 0; localX < 16; localX++) {
                int worldX = startX + localX;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldZ = startZ + localZ;
                    for (int y = minY; y < maxY; y++) {
                        cursor.set(worldX, y, worldZ);
                        BlockState sourceState = sourceChunk.getBlockState(cursor);
                        BlockState targetState = realWorld.getBlockState(cursor);
                        CompoundTag sourceBlockEntityNbt = sourceChunk.getBlockEntityNbtForSaving(cursor, registries);

                        boolean stateChanged = !targetState.equals(sourceState);
                        if (stateChanged) {
                            realWorld.setBlock(cursor, sourceState, SET_BLOCK_FLAGS, 0);
                            stats.blocksChanged++;
                        }

                        if (sourceBlockEntityNbt != null) {
                            realWorld.removeBlockEntity(cursor);
                            BlockEntity recreated = BlockEntity.loadStatic(
                                    cursor,
                                    sourceState,
                                    sourceBlockEntityNbt.copy(),
                                    registries
                            );
                            if (recreated != null) {
                                realWorld.setBlockEntity(recreated);
                                recreated.setChanged();
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

            targetChunk.markUnsaved();
            lightingProvider.propagateLightSources(chunkPos);
        }

        if (copyBiomes && !biomeRefreshChunks.isEmpty()) {
            ClientboundChunksBiomesPacket biomePacket = ClientboundChunksBiomesPacket.forChunks(biomeRefreshChunks);
            for (ServerPlayer player : realWorld.players()) {
                player.connection.send(biomePacket);
            }
        }

        for (int i = 0; i < 4096; i++) {
            if (lightingProvider.runLightUpdates() <= 0) {
                break;
            }
        }

        return stats;
    }

    @SuppressWarnings("unchecked")
    private static void copyChunkBiomes(ChunkAccess sourceChunk, LevelChunk targetChunk) {
        LevelChunkSection[] sourceSections = sourceChunk.getSections();
        LevelChunkSection[] targetSections = targetChunk.getSections();
        int sectionCount = Math.min(sourceSections.length, targetSections.length);

        for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
            LevelChunkSection sourceSection = sourceSections[sectionIndex];
            LevelChunkSection targetSection = targetSections[sectionIndex];

            PalettedContainer<Holder<Biome>> sourceBiomes =
                    (PalettedContainer<Holder<Biome>>) sourceSection.getBiomes().copy();
            PalettedContainer<Holder<Biome>> targetBiomes =
                    (PalettedContainer<Holder<Biome>>) targetSection.getBiomes();

            for (int biomeX = 0; biomeX < 4; biomeX++) {
                for (int biomeY = 0; biomeY < 4; biomeY++) {
                    for (int biomeZ = 0; biomeZ < 4; biomeZ++) {
                        targetBiomes.set(biomeX, biomeY, biomeZ, sourceBiomes.get(biomeX, biomeY, biomeZ));
                    }
                }
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

    private static ServerLevelData asServerWorldProperties(ServerLevel world) {
        if (world.getLevelData() instanceof ServerLevelData properties) {
            return properties;
        }
        throw new IllegalStateException("World properties do not implement ServerWorldProperties");
    }

    private static void setGeneratorOptions(WorldData saveProperties, WorldOptions generatorOptions) throws Exception {
        if (!(saveProperties instanceof PrimaryLevelData levelProperties)) {
            throw new IllegalStateException("Unsupported save properties type: " + saveProperties.getClass().getName());
        }

        Field generatorOptionsField = PrimaryLevelData.class.getDeclaredField("generatorOptions");
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
