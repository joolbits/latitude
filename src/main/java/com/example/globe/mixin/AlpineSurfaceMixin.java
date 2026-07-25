package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites high natural surface blocks into alpine rock or snow caps in Latitude worlds.
 */
@Mixin(ProtoChunk.class)
public abstract class AlpineSurfaceMixin {

    @Unique
    private static final BlockState GLOBE_ALPINE_STONE = Blocks.STONE.defaultBlockState();
    @Unique
    private static final BlockState GLOBE_ALPINE_SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    @Unique
    private static final boolean DEBUG_ALPINE = Boolean.getBoolean("latitude.debugAlpine");
    @Unique
    private static final java.util.concurrent.atomic.AtomicBoolean GLOBE_ALPINE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @ModifyVariable(
            method = "setBlockState",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private BlockState globe$alpineSurface(BlockState state, BlockPos pos) {
        if (state == null) {
            return state;
        }
        int radius = LatitudeBiomes.ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0 || pos.getY() < LatitudeBiomes.ALPINE_ROCK_Y) {
            return state;
        }

        Block block = state.getBlock();
        if (block != Blocks.GRASS_BLOCK && block != Blocks.DIRT && block != Blocks.COARSE_DIRT
                && block != Blocks.PODZOL && block != Blocks.MYCELIUM && block != Blocks.GRAVEL) {
            return state;
        }

        int kind = LatitudeBiomes.alpineSurfaceKind(pos.getX(), pos.getY(), pos.getZ(), radius);
        if (DEBUG_ALPINE && kind != 0 && GLOBE_ALPINE_LOGGED.compareAndSet(false, true)) {
            com.example.globe.GlobeMod.LOGGER.info(
                    "[Latitude] Alpine surface active: first {} at y={} (rockLine={})",
                    kind == 2 ? "snow" : "rock", pos.getY(), LatitudeBiomes.ALPINE_ROCK_Y);
        }
        return switch (kind) {
            case 1 -> GLOBE_ALPINE_STONE;
            case 2 -> GLOBE_ALPINE_SNOW;
            default -> state;
        };
    }
}
