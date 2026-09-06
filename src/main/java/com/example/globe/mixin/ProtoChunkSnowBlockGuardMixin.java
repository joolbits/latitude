package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
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

    /**
     * True when this column legitimately keeps snow: either vanilla's own temperature gate says so,
     * or Latitude's windswept snow line does. The single source of truth for BOTH the snow-layer
     * strip and the SNOWY-property clear above — separate copies of this test are exactly how the
     * orphaned white-topped grass got produced in the first place.
     */
    @Unique
    private boolean globe$columnKeepsSnow(BlockPos pos) {
        Holder<Biome> biome = ((ProtoChunk) (Object) this).getNoiseBiome(
                pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
        if (biome == null) {
            return false;
        }
        if (biome.value().coldEnoughToSnow(pos)) {
            return true;
        }
        return com.example.globe.world.WindsweptSnowLinePolicy.appliesTo(
                biome.unwrapKey().map(key -> key.location().toString()).orElse(null),
                pos.getY(), LatitudeBiomes.getActiveSeaLevel(),
                com.example.globe.world.WindsweptSnowLinePolicy.absoluteLatitudeDegrees(
                        pos.getZ(), LatitudeBiomes.getActiveRadiusBlocks(), GlobeMod.BORDER_RADIUS));
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void globe$blockSnowInWarmBands(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        if (state == null) return;
        if (!LatitudeWorldgenScope.isActive()) return;

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

        // Orphaned white-topped grass. grass_block carries SnowyDirtBlock.SNOWY, which paints the
        // block's top edges white; vanilla keeps it in sync via neighbour updates, but worldgen
        // writes states directly, so stripping the snow layer above leaves SNOWY=true with nothing
        // on top. That is the "grey grass with snow-covered edges" defect (maintainer, 2026-08-10),
        // and it is a state desync, not an aesthetic complaint — previously measured on this line
        // at 159 snowy grass blocks against 13 surviving snow layers in temperate windswept.
        //
        // Until now the only defence was never to strip where snow had been placed, which made the
        // whole snow-line policy load-bearing for a RENDERING invariant. Clearing SNOWY on the same
        // condition that strips the layer makes the two agree by construction, so a future snow-line
        // change cannot resurrect the orphan.
        if (state.is(Blocks.GRASS_BLOCK)
                && state.hasProperty(net.minecraft.world.level.block.SnowyDirtBlock.SNOWY)
                && state.getValue(net.minecraft.world.level.block.SnowyDirtBlock.SNOWY)
                && !globe$columnKeepsSnow(pos)) {
            cir.setReturnValue(state.setValue(
                    net.minecraft.world.level.block.SnowyDirtBlock.SNOWY, Boolean.FALSE));
            return;
        }

        boolean isSnowBlock = state.is(Blocks.SNOW_BLOCK);
        boolean isSnowLayer = state.is(Blocks.SNOW);
        boolean isPowder = state.is(Blocks.POWDER_SNOW);
        if (!(isSnowBlock || isSnowLayer || isPowder)) return;
        if (pos.getY() >= LatitudeBiomes.ALPINE_ROCK_Y) return;

        // This guard exists to kill "snow at cave mouths in jungle" — not to de-snow genuinely cold
        // columns. The latitude band alone cannot tell those apart: TEMPERATE is a warm band here,
        // but vanilla legitimately snows a temperature-0.2 biome (windswept forest/hills) above
        // roughly y=120, because height drops temperature past the snow threshold. Stripping on the
        // band alone therefore erased every snow layer between ~y=120 and ALPINE_ROCK_Y at temperate
        // latitudes, leaving the desaturated olive-grey grass those biomes are *supposed* to have —
        // but with no snow on top to explain it, which is exactly what reads as broken.
        //
        // Ask the biome the same question vanilla asks. A column cold enough to snow keeps its snow;
        // a warm column (the jungle cave mouth this guard was written for) still loses it. This also
        // re-aligns the two snow write paths: SnowAndFreezeFeature applies vanilla's own
        // cold-enough test and is unguarded since the 26.2 pivot, so band-only stripping made the
        // two disagree and produced the patchy result rather than a clean one.
        if (globe$columnKeepsSnow(pos)) {
            return;
        }
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
