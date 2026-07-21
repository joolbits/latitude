package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;

/** Establishes dimension-scoped authority around synchronous noise-generator block writes. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkGeneratorWorldgenAuthorityMixin {
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

    @WrapMethod(method = "applyCarvers(Lnet/minecraft/server/level/WorldGenRegion;JLnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V")
    private void globe$withCarverAuthority(
            WorldGenRegion world,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            Operation<Void> original) {
        boolean active = globe$isAuthorizedOverworld(world);
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(active)) {
            original.call(world, seed, randomState, biomeManager, structureManager, chunk);
        }
    }

    private boolean globe$isAuthorizedOverworld(WorldGenRegion world) {
        NoiseBasedChunkGenerator self = (NoiseBasedChunkGenerator) (Object) this;
        return world != null
                && Level.OVERWORLD.equals(world.getLevel().dimension())
                && GlobeMod.shouldApplyLatitudeWorldgen(self);
    }
}
