package com.example.globe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * Rejects invalid fresh village starts before vanilla can register them. Villages are rejected
 * strictly beyond 80 degrees absolute latitude or when their declared climate conflicts with the
 * canonical Latitude band. Compatible/neutral villages and other structures are not affected.
 */
@Mixin(ChunkGenerator.class)
public abstract class ExtremePolarVillageStartGuardMixin {

    @WrapOperation(
            method = "tryGenerateStructure",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/Structure;generate(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
    private StructureStart globe$blockVillageStartsInExtremePolar(
            Structure structure,
            Holder<Structure> structureHolder,
            ResourceKey<Level> levelKey,
            RegistryAccess registryAccess,
            ChunkGenerator chunkGenerator,
            BiomeSource biomeSource,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkPos chunkPos,
            int references,
            LevelHeightAccessor heightAccessor,
            Predicate<Holder<Biome>> validBiome,
            Operation<StructureStart> original) {
        int blockZ = chunkPos.getMiddleBlockZ();
        if (LatitudeWorldgenScope.isActive()
                && chunkGenerator instanceof NoiseBasedChunkGenerator noise
                && GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            try {
                Registry<Structure> registry =
                        registryAccess.lookupOrThrow(Registries.STRUCTURE);
                Identifier structureId = registry.getKey(structure);
                if (structureId != null && structureId.getPath().startsWith("village")) {
                    int radius = GlobeMod.borderRadiusForNoiseGenerator(noise);
                    double absDeg = Math.abs((double) blockZ) * 90.0 / Math.max(1, radius);
                    LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
                    if (LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, radius)
                            || LatitudeBiomes.villageClimateVsBandMismatch(structureId.getPath(), band)) {
                        return StructureStart.INVALID_START;
                    }
                }
            } catch (Throwable ignored) {
                // Registry unavailable — fail open (allow generation).
            }
        }
        return original.call(
                structure,
                structureHolder,
                levelKey,
                registryAccess,
                chunkGenerator,
                biomeSource,
                randomState,
                templateManager,
                seed,
                chunkPos,
                references,
                heightAccessor,
                validBiome);
    }
}
