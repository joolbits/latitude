package com.example.globe;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

public final class PolarCapScrubber {
    private final int borderRadius;
    private final int poleBandStartAbsZ;
    private final LongSet scrubbedChunks = new LongOpenHashSet();

    private final Long2IntMap chunkProgress = new Long2IntOpenHashMap();

    private static final int COLUMNS_PER_TICK = 16;

    public PolarCapScrubber(int borderRadius, int poleBandStartAbsZ) {
        this.borderRadius = borderRadius;
        this.poleBandStartAbsZ = poleBandStartAbsZ;
    }

    public void tick(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (Math.abs(player.getZ()) < poleBandStartAbsZ) {
                continue;
            }

            ChunkPos center = new ChunkPos(player.getBlockPos());

            boolean didScrub = false;
            for (int dz = -1; dz <= 1 && !didScrub; dz++) {
                for (int dx = -1; dx <= 1 && !didScrub; dx++) {
                    int cx = center.x + dx;
                    int cz = center.z + dz;

                    long key = chunkKey(cx, cz);
                    if (scrubbedChunks.contains(key)) {
                        continue;
                    }

                    int approxChunkCenterZ = (cz << 4) + 8;
                    if (Math.abs(approxChunkCenterZ) < poleBandStartAbsZ) {
                        continue;
                    }

                    WorldChunk chunk = world.getChunk(cx, cz);
                    scrubChunkSurface(world, chunk);
                    didScrub = true;
                }
            }
        }
    }

    private void scrubChunkSurface(ServerWorld world, WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int baseX = pos.getStartX();
        int baseZ = pos.getStartZ();

        long key = chunkKey(pos.x, pos.z);
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

            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (surfaceY <= world.getBottomY()) {
                continue;
            }

            int maxY = surfaceY + 24;
            for (int y = surfaceY; y <= maxY; y++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = world.getBlockState(p);
                if (isVegetation(s)) {
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }

            BlockPos surfacePos = new BlockPos(x, surfaceY, z);
            BlockState surfaceState = world.getBlockState(surfacePos);

            if (surfaceState.getBlock() == Blocks.WATER) {
                world.setBlockState(surfacePos, Blocks.ICE.getDefaultState(), Block.NOTIFY_LISTENERS);
                continue;
            }

            BlockState newSurface = pickPolarSurface(x, z);
            if (surfaceState.getBlock() != newSurface.getBlock()) {
                world.setBlockState(surfacePos, newSurface, Block.NOTIFY_LISTENERS);
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
            return Blocks.SNOW_BLOCK.getDefaultState();
        }
        if (m < 921) {
            return Blocks.STONE.getDefaultState();
        }
        return Blocks.PACKED_ICE.getDefaultState();
    }

    private boolean isVegetation(BlockState state) {
        return state.isIn(BlockTags.LEAVES)
                || state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.SAPLINGS)
                || state.isIn(BlockTags.FLOWERS)
                || state.isIn(BlockTags.CROPS)
                || state.getBlock() == Blocks.GRASS
                || state.getBlock() == Blocks.TALL_GRASS
                || state.getBlock() == Blocks.FERN
                || state.getBlock() == Blocks.LARGE_FERN
                || state.getBlock() == Blocks.VINE;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xffffffffL) << 32 | ((long) cz & 0xffffffffL);
    }
}
