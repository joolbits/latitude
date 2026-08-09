package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
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
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private BlockState globe$swapWarmBandSnow(BlockState state, BlockPos pos) {
        if (state == null) return null;
        if (!LatitudeWorldgenScope.isActive()) return state;

        if (state.getBlock() != Blocks.POWDER_SNOW
            && state.getBlock() != Blocks.SNOW_BLOCK
            && state.getBlock() != Blocks.SNOW) {
            return state;
        }
        if (pos.getY() >= LatitudeBiomes.ALPINE_ROCK_Y) {
            return state;
        }

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            radius = GlobeMod.BORDER_RADIUS;
        }
        double t = Math.abs((double) pos.getZ()) / (double) radius;

        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(t * 90.0);
        boolean warm = band == LatitudeBands.Band.TROPICAL
                || band == LatitudeBands.Band.SUBTROPICAL
                || band == LatitudeBands.Band.TEMPERATE;

        if (!warm) return state;

        // Align with ProtoChunkSnowBlockGuardMixin (a626c45c): a warm BAND is not a warm COLUMN.
        // Vanilla legitimately snows temperature-0.2 biomes above ~seaLevel+57, and TEMPERATE
        // counts as warm here — without this, every snow layer SnowAndFreezeFeature paired with a
        // snowy grass block was rewritten to AIR at this layer while the snowy blockstate stayed,
        // mass-producing the orphaned white-topped grass Maintainer reported (measured: temperate
        // windswept 159 snowy grass : 13 snow layers, subpolar 183 : 222).
        WorldGenRegion region = (WorldGenRegion) (Object) this;
        var biome = region.getBiome(pos);
        if (biome.value().coldEnoughToSnow(pos, region.getSeaLevel())) {
            return state;
        }
        // Latitude's lowered windswept snow line — must match SnowAndFreezeWindsweptSnowLineMixin
        // and ProtoChunkSnowBlockGuardMixin or this layer strips what they place.
        if (com.example.globe.world.WindsweptSnowLinePolicy.appliesTo(
                biome.unwrapKey().map(key -> key.identifier().toString()).orElse(null),
                pos.getY(), region.getSeaLevel())) {
            return state;
        }

        if (state.getBlock() == Blocks.SNOW_BLOCK) return STONE;
        return AIR;
    }
}
