package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
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
 *
 * Also enforces ocean-surface coherence: prevents grass_block from being placed
 * in ocean-family biome cells during worldgen. In vanilla, ocean biomes never
 * have terrain above water, so surface rules freely place grass on exposed
 * surfaces. Latitude can assign ocean biomes to cells at sea level (via
 * oceanAuthority), creating a biome/surface mismatch that this guard resolves.
 */
@Mixin(ProtoChunk.class)
public class ProtoChunkSnowBlockGuardMixin {

    @Unique
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LatitudeSnowGuard");

    @Unique
    private static final java.util.concurrent.atomic.AtomicInteger GUARD_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    @Unique
    private static final BlockState STONE_STATE = Blocks.STONE.defaultBlockState();

    @Unique
    private static final BlockState DIRT_STATE = Blocks.DIRT.defaultBlockState();

    @Unique
    private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();

    @Unique
    private static final BlockState GRAVEL_STATE = Blocks.GRAVEL.defaultBlockState();

    @Unique
    private static final boolean DEBUG_SNOW_GUARD = Boolean.getBoolean("latitude.debugSnowGuard");

    @Unique
    private static final boolean DEBUG_OCEAN_SURFACE_GUARD = Boolean.getBoolean("latitude.debugOceanSurfaceGuard");

    @Unique
    private static final java.util.concurrent.atomic.AtomicInteger OCEAN_GUARD_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    @Unique
    private static boolean globe$isWarmBand(int blockZ) {
        int borderRadius = GlobeMod.BORDER_RADIUS;
        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (activeRadius > 0) borderRadius = activeRadius;
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(Math.abs((double) blockZ) * 90.0 / Math.max(1, borderRadius));
        return band == LatitudeBands.Band.TROPICAL
                || band == LatitudeBands.Band.SUBTROPICAL
                || band == LatitudeBands.Band.TEMPERATE;
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void globe$blockSnowInWarmBands(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (state == null) return;

        // Ocean-surface coherence: prevent grass_block in ocean-family biome cells.
        // Biome data is already populated (BIOMES phase) by the time surface rules
        // place grass_block (SURFACE phase), so getBiomeForNoiseGen reads Latitude biome.
        if (state.is(Blocks.GRASS_BLOCK)) {
            Holder<Biome> biome = ((ProtoChunk) (Object) this).getNoiseBiome(
                    pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
            if (biome.is(BiomeTags.IS_OCEAN)) {
                if (DEBUG_OCEAN_SURFACE_GUARD) {
                    int count = OCEAN_GUARD_LOG_COUNT.incrementAndGet();
                    if (count <= 25) {
                        LOGGER.warn("[OCEAN_SURFACE_GUARD] x={} y={} z={} replace grass_block -> gravel",
                                pos.getX(), pos.getY(), pos.getZ());
                    }
                }
                cir.setReturnValue(GRAVEL_STATE);
                return;
            }
        }

        if (!globe$isWarmBand(pos.getZ())) return;

        boolean isSnowBlock = state.is(Blocks.SNOW_BLOCK);
        boolean isSnowLayer = state.is(Blocks.SNOW);
        boolean isPowder = state.is(Blocks.POWDER_SNOW);
        if (!(isSnowBlock || isSnowLayer || isPowder)) return;
        if (pos.getY() >= LatitudeBiomes.ALPINE_ROCK_Y) return;

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
                        LatitudeBands.fromAbsoluteLatitudeDeg(
                                Math.abs((double) pos.getZ()) * 90.0
                                        / Math.max(1, LatitudeBiomes.getActiveRadiusBlocks() > 0 ? LatitudeBiomes.getActiveRadiusBlocks() : GlobeMod.BORDER_RADIUS)
                        ).id(),
                        state.getBlock(),
                        replacement.getBlock());
            }
        }

        cir.setReturnValue(replacement);
    }
}
