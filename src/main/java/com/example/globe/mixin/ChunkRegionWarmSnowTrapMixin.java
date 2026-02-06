package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.debug.WarmSnowTrapStats;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Universal warm-band snow/powder_snow guard at the worldgen write API.
 * Rewrites the BlockState argument of ChunkRegion#setBlockState before
 * vanilla processes it. No recursion, no extra setBlockState calls.
 */
@Mixin(ChunkRegion.class)
public abstract class ChunkRegionWarmSnowTrapMixin {

    @Unique
    private static final BlockState STONE = Blocks.STONE.getDefaultState();
    @Unique
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

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
        WarmSnowTrapStats.lastPos = pos.toImmutable();

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            radius = GlobeMod.BORDER_RADIUS;
        }
        double t = Math.abs((double) pos.getZ()) / (double) radius;
        WarmSnowTrapStats.lastT = t;

        LatitudeMath.LatitudeZone zone = LatitudeMath.zoneForRadius(radius, pos.getZ());
        boolean warm = zone == LatitudeMath.LatitudeZone.EQUATOR
                || zone == LatitudeMath.LatitudeZone.TROPICAL
                || zone == LatitudeMath.LatitudeZone.SUBTROPICAL
                || zone == LatitudeMath.LatitudeZone.TEMPERATE;

        if (!warm) return state;

        WarmSnowTrapStats.rewrites++;
        if (state.getBlock() == Blocks.SNOW_BLOCK) return STONE;
        return AIR;
    }
}
