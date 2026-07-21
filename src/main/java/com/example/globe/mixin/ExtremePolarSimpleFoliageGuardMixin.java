package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.PolarFoliagePolicy;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Filters the single state already sampled by SimpleBlockFeature. Non-foliage simple-block
 * features fail open, and sweet berry bushes retain their intentional polar exemption.
 */
@Mixin(SimpleBlockFeature.class)
public class ExtremePolarSimpleFoliageGuardMixin {
    private static final TagKey<Block> POLAR_FOLIAGE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("globe", "polar_foliage"));

    @ModifyExpressionValue(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/feature/stateproviders/BlockStateProvider;getOptionalState(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState globe$filterSimpleFoliageBeyondPolarLimit(
            BlockState sampledState,
            FeaturePlaceContext<SimpleBlockConfiguration> context) {
        if (sampledState == null) {
            return null;
        }

        if (!LatitudeWorldgenScope.isActive()
                || !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return sampledState;
        }

        boolean sweetBerryBush = sampledState.is(Blocks.SWEET_BERRY_BUSH);
        if (sweetBerryBush) {
            return sampledState;
        }

        boolean beyondLimit = LatitudeBiomes.isBlockBeyondPolarFoliageLimit(
                context.origin().getZ(),
                GlobeMod.BORDER_RADIUS);
        boolean foliage = sampledState.is(POLAR_FOLIAGE);
        return PolarFoliagePolicy.shouldSuppressSimpleBlock(
                beyondLimit,
                foliage,
                sweetBerryBush)
                ? null
                : sampledState;
    }
}
