package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

/** Establishes dimension-scoped authority around synchronous noise-generator block writes. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkGeneratorWorldgenAuthorityMixin {
    @WrapMethod(method = "doFill(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/chunk/ChunkAccess;)V")
    private void globe$withNoiseFillAuthority(
            NoiseChunk noiseChunk,
            ChunkAccess chunk,
            Operation<Void> original) {
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(globe$isAuthorizedGenerator())) {
            original.call(noiseChunk, chunk);
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

    @WrapMethod(method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Ljava/util/Set;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V")
    private void globe$withSurfaceAuthority(
            ChunkAccess chunk,
            NoiseChunk noiseChunk,
            RandomState randomState,
            BiomeManager biomeManager,
            Set<Holder<Biome>> biomes,
            MaterialRule materialRule,
            Operation<Void> original) {
        boolean active = globe$isAuthorizedGenerator();
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(chunk, noiseChunk, randomState, biomeManager, biomes, materialRule);
        }
    }

    @WrapMethod(method = "generateCarvers(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;)V")
    private void globe$withCarverAuthority(
            ChunkAccess chunk,
            Blender blender,
            NoiseChunk noiseChunk,
            RandomState randomState,
            BiomeManager biomeManager,
            WorldGenRegion world,
            MaterialRule materialRule,
            Operation<Void> original) {
        boolean active = globe$isAuthorizedOverworld(world);
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(chunk, blender, noiseChunk, randomState, biomeManager, world, materialRule);
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
