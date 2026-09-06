package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;

/** Establishes dimension-scoped authority around synchronous noise-generator block writes. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkGeneratorWorldgenAuthorityMixin {
    @WrapMethod(method = "doFill(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;II)Lnet/minecraft/world/level/chunk/ChunkAccess;")
    private ChunkAccess globe$withNoiseFillAuthority(
            Blender blender,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk,
            int minimumCellY,
            int cellCountY,
            Operation<ChunkAccess> original) {
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(globe$isAuthorizedGenerator())) {
            return original.call(blender, structureManager, randomState, chunk, minimumCellY, cellCountY);
        }
    }

    @WrapMethod(method = "getBaseHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I")
    private int globe$withBaseHeightAuthority(
            int blockX,
            int blockZ,
            Heightmap.Types heightmap,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            Operation<Integer> original) {
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(globe$isAuthorizedGenerator())) {
            return original.call(blockX, blockZ, heightmap, heightAccessor, randomState);
        }
    }

    @WrapMethod(method = "getBaseColumn(IILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/NoiseColumn;")
    private NoiseColumn globe$withBaseColumnAuthority(
            int blockX,
            int blockZ,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            Operation<NoiseColumn> original) {
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(globe$isAuthorizedGenerator())) {
            return original.call(blockX, blockZ, heightAccessor, randomState);
        }
    }

    @WrapMethod(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V")
    private void globe$withSurfaceAuthority(
            WorldGenRegion world,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk,
            Operation<Void> original) {
        boolean active = globe$isAuthorizedOverworld(world);
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(world, structureManager, randomState, chunk);
        }
    }

    @WrapMethod(method = "applyCarvers(Lnet/minecraft/server/level/WorldGenRegion;JLnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/GenerationStep$Carving;)V")
    private void globe$withCarverAuthority(
            WorldGenRegion world,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            // 1.21.1 still runs carvers per carving step, so this method carries the step
            // parameter 1.21.2 removed. It is passed straight through: the authority scope is
            // step-independent.
            GenerationStep.Carving carverStep,
            Operation<Void> original) {
        boolean active = globe$isAuthorizedOverworld(world);
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(world, seed, randomState, biomeManager, structureManager, chunk, carverStep);
        }
    }

    private boolean globe$isAuthorizedOverworld(WorldGenRegion world) {
        NoiseBasedChunkGenerator self = (NoiseBasedChunkGenerator) (Object) this;
        return world != null
                && Level.OVERWORLD.equals(world.getLevel().dimension())
                && GlobeMod.shouldApplyLatitudeWorldgen(self);
    }

    private boolean globe$isAuthorizedGenerator() {
        return GlobeMod.shouldApplyLatitudeWorldgen((NoiseBasedChunkGenerator) (Object) this);
    }
}
