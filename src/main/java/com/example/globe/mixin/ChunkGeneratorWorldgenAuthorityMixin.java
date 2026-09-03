package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Establishes dimension-scoped authority around base chunk-generator paths. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorWorldgenAuthorityMixin {
    @WrapMethod(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V")
    private void globe$withFeatureAuthority(
            WorldGenLevel world,
            ChunkAccess chunk,
            StructureManager structureManager,
            Operation<Void> original) {
        boolean active = world != null
                && Level.OVERWORLD.equals(world.getLevel().dimension())
                && globe$isAuthorizedGenerator();
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(world, chunk, structureManager);
        }
    }

    @WrapOperation(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean globe$withPlacedFeatureAuthority(
            FeaturePlacer placer,
            PlacedFeature feature,
            RandomSource random,
            BlockPos origin,
            Operation<Boolean> original) {
        boolean active = LatitudeWorldgenScope.isActive();
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enterFeatures(active)) {
            return original.call(placer, feature, random, origin);
        }
    }

    @WrapMethod(method = "createStructures")
    private void globe$withStructureAuthority(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> dimension,
            Operation<Void> original) {
        boolean active = Level.OVERWORLD.equals(dimension) && globe$isAuthorizedGenerator();
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(registryAccess, structureState, structureManager, chunk, structureTemplateManager, dimension);
        }
    }

    private boolean globe$isAuthorizedGenerator() {
        return (Object) this instanceof NoiseBasedChunkGenerator noise
                && GlobeMod.shouldApplyLatitudeWorldgen(noise);
    }
}
