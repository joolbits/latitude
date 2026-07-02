package com.example.globe;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class PolarCapScrubber {
    // 26.2 removed the BlockTags.SAPLINGS Java constant but kept the minecraft:saplings block tag in data.
    // Reconstruct the TagKey locally to preserve the exact prior vegetation-scrub behavior.
    private static final TagKey<Block> SAPLINGS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "saplings"));

    private final int borderRadius;
    private final int poleBandStartAbsZ;
    private final LongSet scrubbedChunks = new LongOpenHashSet();

    private final Long2IntMap chunkProgress = new Long2IntOpenHashMap();

    private static final int COLUMNS_PER_TICK = 16;

    public PolarCapScrubber(int borderRadius, int poleBandStartAbsZ) {
        this.borderRadius = borderRadius;
        this.poleBandStartAbsZ = poleBandStartAbsZ;
    }

    public void tick(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            if (Math.abs(player.getZ()) < poleBandStartAbsZ) {
                continue;
            }

            ChunkPos center = ChunkPos.containing(player.blockPosition());

            boolean didScrub = false;
            for (int dz = -1; dz <= 1 && !didScrub; dz++) {
                for (int dx = -1; dx <= 1 && !didScrub; dx++) {
                    int cx = center.x() + dx;
                    int cz = center.z() + dz;

                    long key = chunkKey(cx, cz);
                    if (scrubbedChunks.contains(key)) {
                        continue;
                    }

                    int approxChunkCenterZ = (cz << 4) + 8;
                    if (Math.abs(approxChunkCenterZ) < poleBandStartAbsZ) {
                        continue;
                    }

                    LevelChunk chunk = world.getChunk(cx, cz);
                    scrubChunkSurface(world, chunk);
                    didScrub = true;
                }
            }
        }
    }

    private void scrubChunkSurface(ServerLevel world, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();

        long key = chunkKey(pos.x(), pos.z());
        int startIndex = chunkProgress.getOrDefault(key, 0);
        int endIndex = Math.min(256, startIndex + COLUMNS_PER_TICK);

        for (int i = startIndex; i < endIndex; i++) {
            int localX = i & 15;
            int localZ = (i >> 4) & 15;

            int x = baseX + localX;
            int z = baseZ + localZ;

            if (Math.abs(z) < poleBandStartAbsZ) {
                continue;
            }

            if (Math.abs(z) > borderRadius + 32) {
                continue;
            }

            int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (surfaceY <= world.getMinY()) {
                continue;
            }

            int maxY = surfaceY + 24;
            for (int y = surfaceY; y <= maxY; y++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = world.getBlockState(p);
                if (isVegetation(s)) {
                    world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }

            BlockPos surfacePos = new BlockPos(x, surfaceY, z);
            BlockState surfaceState = world.getBlockState(surfacePos);

            if (surfaceState.getBlock() == Blocks.WATER) {
                world.setBlock(surfacePos, Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
                continue;
            }

            BlockState newSurface = pickPolarSurface(x, z);
            if (surfaceState.getBlock() != newSurface.getBlock()) {
                world.setBlock(surfacePos, newSurface, Block.UPDATE_CLIENTS);
            }
        }

        if (endIndex >= 256) {
            chunkProgress.remove(key);
            scrubbedChunks.add(key);
        } else {
            chunkProgress.put(key, endIndex);
        }
    }

    private BlockState pickPolarSurface(int x, int z) {
        long h = (x * 341873128712L) ^ (z * 132897987541L);
        int r = (int) (h ^ (h >>> 32));
        int m = r & 1023;
        if (m < 716) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        if (m < 921) {
            return Blocks.STONE.defaultBlockState();
        }
        return Blocks.PACKED_ICE.defaultBlockState();
    }

    private boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(SAPLINGS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS)
                || state.getBlock() == Blocks.SHORT_GRASS
                || state.getBlock() == Blocks.TALL_GRASS
                || state.getBlock() == Blocks.FERN
                || state.getBlock() == Blocks.LARGE_FERN
                || state.getBlock() == Blocks.VINE;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xffffffffL) << 32 | ((long) cz & 0xffffffffL);
    }
}
