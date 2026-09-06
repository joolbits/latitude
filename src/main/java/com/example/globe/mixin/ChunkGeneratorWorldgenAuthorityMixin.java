package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
                    target = "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;placeWithBiomeCheck(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean globe$withPlacedFeatureAuthority(
            PlacedFeature feature,
            WorldGenLevel world,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos origin,
            Operation<Boolean> original) {
        // This call site lives INSIDE applyBiomeDecoration, which the wrap above already surrounds
        // with a scope carrying the identical authority answer. Re-deriving it here cost a dimension
        // lookup plus up to six holder comparisons for EVERY placed feature -- hundreds of times per
        // chunk with a biome pack installed. Read the frame instead.
        boolean active = LatitudeWorldgenScope.isActive();
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enterFeatures(active)) {
            return original.call(feature, world, generator, random, origin);
        }
    }

    @WrapMethod(method = "createStructures")
    private void globe$withStructureAuthority(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager,
            Operation<Void> original) {
        // 1.21.1's createStructures carries no dimension key -- that parameter arrived later. The
        // generator check below is the discriminator that actually matters: only Latitude's own
        // configured noise generator answers true, and no other dimension runs it.
        boolean active = globe$isAuthorizedGenerator();
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(registryAccess, structureState, structureManager, chunk, structureTemplateManager);
        }
    }

    private boolean globe$isAuthorizedGenerator() {
        return (Object) this instanceof NoiseBasedChunkGenerator noise
                && GlobeMod.shouldApplyLatitudeWorldgen(noise);
    }
}
