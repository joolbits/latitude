package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard guard: prevent snow_block and snow from being written into ProtoChunks
 * (worldgen only) in warm latitude bands. This is the definitive fix for
 * "snow at cave mouths in jungle" — it catches ALL sources of snow placement
 * during world generation regardless of biome container state.
 */
@Mixin(ProtoChunk.class)
public class ProtoChunkSnowBlockGuardMixin {

    @Unique
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LatitudeSnowGuard");

    @Unique
    private static final java.util.concurrent.atomic.AtomicInteger GUARD_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    @Unique
    private static final BlockState STONE_STATE = Blocks.STONE.getDefaultState();

    @Unique
    private static final BlockState DIRT_STATE = Blocks.DIRT.getDefaultState();

    @Unique
    private static final BlockState AIR_STATE = Blocks.AIR.getDefaultState();

    @Unique
    private static final boolean DEBUG_SNOW_GUARD = Boolean.getBoolean("latitude.debugSnowGuard");

    @Unique
    private static boolean globe$isWarmBand(int blockZ) {
        int borderRadius = GlobeMod.BORDER_RADIUS;
        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (activeRadius > 0) borderRadius = activeRadius;
        LatitudeMath.LatitudeZone zone = LatitudeMath.zoneForRadius(borderRadius, blockZ);
        return zone == LatitudeMath.LatitudeZone.EQUATOR
                || zone == LatitudeMath.LatitudeZone.TROPICAL
                || zone == LatitudeMath.LatitudeZone.SUBTROPICAL
                || zone == LatitudeMath.LatitudeZone.TEMPERATE;
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void globe$blockSnowInWarmBands(BlockPos pos, BlockState state, boolean flags, CallbackInfoReturnable<BlockState> cir) {
        if (state == null || !globe$isWarmBand(pos.getZ())) return;

        boolean isSnowBlock = state.isOf(Blocks.SNOW_BLOCK);
        boolean isSnowLayer = state.isOf(Blocks.SNOW);
        boolean isPowder = state.isOf(Blocks.POWDER_SNOW);
        if (!(isSnowBlock || isSnowLayer || isPowder)) return;

        BlockState replacement;
        if (isSnowBlock) {
            // Cosmetic: dirt on hillsides (above sea level), stone underground
            replacement = pos.getY() >= 63 ? DIRT_STATE : STONE_STATE;
        } else {
            replacement = AIR_STATE;
        }

        if (DEBUG_SNOW_GUARD) {
            int count = GUARD_LOG_COUNT.incrementAndGet();
            if (count <= 25) {
                LOGGER.warn("[SNOWBLOCK_GUARD] x={} y={} z={} band={} replace {} -> {}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        LatitudeMath.zoneForRadius(
                                LatitudeBiomes.getActiveRadiusBlocks() > 0 ? LatitudeBiomes.getActiveRadiusBlocks() : GlobeMod.BORDER_RADIUS,
                                pos.getZ()),
                        state.getBlock(),
                        replacement.getBlock());
            }
        }

        cir.setReturnValue(replacement);
    }
}
