package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.debug.WarmSnowTrapStats;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Universal warm-band snow/powder_snow guard at the worldgen write API.
 * Rewrites the BlockState argument of ChunkRegion#setBlockState before
 * vanilla processes it. No recursion, no extra setBlockState calls.
 */
@Mixin(WorldGenRegion.class)
public abstract class ChunkRegionWarmSnowTrapMixin {

    @Unique
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    @Unique
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    @ModifyVariable(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private BlockState globe$swapWarmBandSnow(BlockState state, BlockPos pos) {
        WarmSnowTrapStats.calls++;
        if (state == null) return null;

        if (state.getBlock() != Blocks.POWDER_SNOW
            && state.getBlock() != Blocks.SNOW_BLOCK
            && state.getBlock() != Blocks.SNOW) {
            return state;
        }

        WarmSnowTrapStats.snowHits++;
        WarmSnowTrapStats.lastBlock = state.getBlock().toString();
        WarmSnowTrapStats.lastPos = pos.immutable();

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            radius = GlobeMod.BORDER_RADIUS;
        }
        double t = Math.abs((double) pos.getZ()) / (double) radius;
        WarmSnowTrapStats.lastT = t;

        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(Math.abs((double) pos.getZ()) * 90.0 / Math.max(1, radius));
        boolean warm = band == LatitudeBands.Band.TROPICAL
                || band == LatitudeBands.Band.SUBTROPICAL
                || band == LatitudeBands.Band.TEMPERATE;

        if (!warm) return state;

        WarmSnowTrapStats.rewrites++;
        if (state.getBlock() == Blocks.SNOW_BLOCK) return STONE;
        return AIR;
    }
}
