package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.PolarFoliagePolicy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
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
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("globe", "polar_foliage"));

    /**
     * 26.2 suppressed a placement by returning null from {@code BlockStateProvider.getOptionalState},
     * which that version's {@code SimpleBlockFeature.place} treated as "nothing to place". Neither
     * the method nor the nullable contract exists here: 1.21.11 has only
     * {@code getState(RandomSource, BlockPos)}, whose result is fed straight into
     * {@code canSurvive}, so returning null would NPE rather than skip.
     *
     * <p>This wraps {@code canSurvive} instead. {@code place} is bytecode-verified to do
     * {@code if (!state.canSurvive(level, origin)) return false;} with no writes before that point,
     * so forcing false is observably identical to 26.2's null: the feature places nothing and
     * reports failure. {@code getState} still runs untouched, so the random source advances exactly
     * as vanilla would and worldgen stays deterministic.
     */
    @WrapOperation(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean globe$filterSimpleFoliageBeyondPolarLimit(
            BlockState sampledState,
            LevelReader levelReader,
            BlockPos placementPos,
            Operation<Boolean> original,
            FeaturePlaceContext<SimpleBlockConfiguration> context) {
        if (sampledState == null) {
            return original.call(sampledState, levelReader, placementPos);
        }

        if (!LatitudeWorldgenScope.isActive()
                || !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return original.call(sampledState, levelReader, placementPos);
        }

        boolean sweetBerryBush = sampledState.is(Blocks.SWEET_BERRY_BUSH);
        if (sweetBerryBush) {
            return original.call(sampledState, levelReader, placementPos);
        }

        boolean beyondLimit = LatitudeBiomes.isBlockBeyondPolarFoliageLimit(
                context.origin().getZ(),
                GlobeMod.BORDER_RADIUS);
        // 1.21.1 predates the firefly bush; no block to exempt on this target.
        boolean fireflyBush = false;
        boolean snowySubpolarOrPolar = fireflyBush
                && PolarFoliagePolicy.isSubpolarOrPolar(
                        context.origin().getZ(),
                        LatitudeBiomes.getActiveRadiusBlocks(),
                        GlobeMod.BORDER_RADIUS)
                && context.level()
                        .getBiome(context.origin())
                        .value()
                        .coldEnoughToSnow(context.origin());
        boolean foliage = sampledState.is(POLAR_FOLIAGE);
        return PolarFoliagePolicy.shouldSuppressSimpleBlock(
                beyondLimit,
                snowySubpolarOrPolar,
                fireflyBush,
                foliage,
                sweetBerryBush)
                ? false
                : original.call(sampledState, levelReader, placementPos);
    }
}
