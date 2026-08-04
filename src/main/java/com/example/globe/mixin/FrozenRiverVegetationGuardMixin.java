package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prevents a vegetation feature scheduled by a neighboring biome from crossing back into a
 * frozen river. The guard is active only inside Latitude overworld decoration; registry biome
 * definitions remain untouched for vanilla saves and other dimensions.
 */
@Mixin(BiomeFilter.class)
public final class FrozenRiverVegetationGuardMixin {
    @WrapOperation(
            method = "shouldPlace(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/BiomeGenerationSettings;hasFeature(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;)Z"))
    private boolean globe$blockFrozenRiverVegetationAtBiomeBoundary(
            BiomeGenerationSettings settings,
            PlacedFeature feature,
            Operation<Boolean> original,
            @Local(name = "biome") Holder<Biome> biome,
            @Local(name = "context") net.minecraft.world.level.levelgen.placement.PlacementContext context) {
        boolean registeredForBiome = original.call(settings, feature);
        if (!registeredForBiome
                || !LatitudeWorldgenScope.isActive()
                || !biome.is(Biomes.FROZEN_RIVER)
                || !(context.generator() instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return registeredForBiome;
        }
        return !globe$isVegetalOnlyFeature(settings.features(), feature);
    }

    private static boolean globe$isVegetalOnlyFeature(
            List<HolderSet<PlacedFeature>> featuresByStep,
            PlacedFeature feature) {
        int vegetalStep = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        boolean foundInVegetal = false;
        boolean foundInOtherStep = false;
        for (int step = 0; step < featuresByStep.size(); step++) {
            for (Holder<PlacedFeature> holder : featuresByStep.get(step)) {
                if (holder.value() == feature) {
                    if (step == vegetalStep) {
                        foundInVegetal = true;
                    } else {
                        foundInOtherStep = true;
                    }
                }
            }
        }
        return foundInVegetal && !foundInOtherStep;
    }
}
