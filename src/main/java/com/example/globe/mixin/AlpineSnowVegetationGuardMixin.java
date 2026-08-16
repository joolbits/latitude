package com.example.globe.mixin;

import com.example.globe.world.AlpineVegetationPolicy;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents decoration from rooting vegetation directly in Latitude's alpine snow cap. */
@Mixin(ProtoChunk.class)
public class AlpineSnowVegetationGuardMixin {

    @Unique
    private static final TagKey<Block> GLOBE_ALPINE_FOLIAGE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("globe", "polar_foliage"));

    @Unique
    private static final BlockState GLOBE_ALPINE_AIR = Blocks.AIR.defaultBlockState();

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void globe$suppressAlpineSnowVegetation(
            BlockPos pos,
            BlockState state,
            int flags,
            CallbackInfoReturnable<BlockState> cir) {
        if (state == null || state.isAir() || !LatitudeWorldgenScope.isFeatureActive()) {
            return;
        }
        if (pos.getY() < LatitudeBiomes.ALPINE_ROCK_Y) {
            return;
        }

        boolean foliage = state.is(GLOBE_ALPINE_FOLIAGE);
        boolean vegetation = state.getBlock() instanceof VegetationBlock;
        if (!foliage && !vegetation) {
            return;
        }

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            return;
        }

        boolean upperHalf = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        BlockPos footing = pos.below(AlpineVegetationPolicy.footingOffsetBlocks(upperHalf));
        int kind = LatitudeBiomes.alpineSurfaceKind(
                footing.getX(), footing.getY(), footing.getZ(), radius);
        boolean footingIsSnow = ((ProtoChunk) (Object) this)
                .getBlockState(footing).is(Blocks.SNOW_BLOCK);
        if (AlpineVegetationPolicy.shouldSuppressAlpineVegetation(
                kind, footingIsSnow, foliage, vegetation)) {
            cir.setReturnValue(GLOBE_ALPINE_AIR);
        }
    }
}
