package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Establishes dimension-scoped authority around base chunk-generator paths. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorWorldgenAuthorityMixin {
    @Unique
    private static final Identifier SULFUR_POOL_ID =
            Identifier.fromNamespaceAndPath("minecraft", "sulfur_pool");

    @Unique
    private static final boolean DEBUG_SULFUR_SURFACE_GUARD =
            Boolean.getBoolean("latitude.debugSulfurSurfaceGuard");

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
        boolean active = world != null
                && Level.OVERWORLD.equals(world.getLevel().dimension())
                && globe$isAuthorizedGenerator();
        Identifier placedFeatureId = null;
        if (active) {
            Registry<PlacedFeature> placedFeatures =
                    world.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            placedFeatureId = placedFeatures.getKey(feature);
            if (DEBUG_SULFUR_SURFACE_GUARD && SULFUR_POOL_ID.equals(placedFeatureId)) {
                GlobeMod.LOGGER.info(
                        "[LAT][SULFUR_SURFACE_GUARD] stage=placed-feature originX={} originY={} originZ={}",
                        origin.getX(),
                        origin.getY(),
                        origin.getZ());
            }
        }
        try (LatitudeWorldgenScope.Scope ignored =
                     LatitudeWorldgenScope.enterFeatures(active, placedFeatureId)) {
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
