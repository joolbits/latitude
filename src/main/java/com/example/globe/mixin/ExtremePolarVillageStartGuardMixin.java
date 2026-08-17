package com.example.globe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.StructureSitingPolicy;
import com.example.globe.world.VillageTerrainSuitabilityPolicy;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Gives every fresh structure start Latitude's final biome authority before vanilla registers it,
 * so each structure's own declared biome predicate sees the same terrain that is generated.
 * Villages are additionally rejected strictly beyond 80 degrees absolute latitude, when their
 * declared climate conflicts with the canonical Latitude band, or when a bounded physical sample
 * crosses cliff-scale terrain.
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
        BiomeSource structureBiomeSource = biomeSource;
        boolean latitudeOwned = LatitudeWorldgenScope.isActive()
                && chunkGenerator instanceof NoiseBasedChunkGenerator noise
                && GlobeMod.shouldApplyLatitudeWorldgen(noise);
        Identifier generatedStructureId = null;
        boolean generatedVillage = false;
        int generatedWorldRadius = 0;
        Registry<Biome> generatedBiomeRegistry = null;
        if (latitudeOwned && chunkGenerator instanceof NoiseBasedChunkGenerator noise) {
            try {
                int radius = GlobeMod.borderRadiusForNoiseGenerator(noise);
                Registry<Biome> biomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME);
                structureBiomeSource = LatitudeBiomeSource.forStructure(
                        biomeSource,
                        biomeRegistry,
                        radius,
                        noise,
                        randomState,
                        heightAccessor);
                // Only arm the footprint law once Latitude's own biome source is installed, so the
                // footprint can never be judged against the vanilla source.
                generatedWorldRadius = radius;
                generatedBiomeRegistry = biomeRegistry;
                Registry<Structure> registry =
                        registryAccess.lookupOrThrow(Registries.STRUCTURE);
                Identifier structureId = registry.getKey(structure);
                boolean village = structureHolder.is(StructureTags.VILLAGE)
                        || (structureId != null && structureId.getPath().contains("village"));
                generatedStructureId = structureId;
                generatedVillage = village;
                if (structureId != null && village) {
                    int blockX = chunkPos.getMiddleBlockX();
                    double absDeg = Math.abs((double) blockZ) * 90.0 / Math.max(1, radius);
                    LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
                    if (LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, radius)
                            || LatitudeBiomes.villageClimateVsBandMismatch(structureId.getPath(), band)) {
                        return StructureStart.INVALID_START;
                    }

                    int[] terrainHeights = new int[VillageTerrainSuitabilityPolicy.SAMPLE_COUNT];
                    int terrainIndex = 0;
                    for (int dz = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                            dz <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                            dz += VillageTerrainSuitabilityPolicy.SAMPLE_STEP_BLOCKS) {
                        for (int dx = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                                dx <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS;
                                dx += VillageTerrainSuitabilityPolicy.SAMPLE_STEP_BLOCKS) {
                            terrainHeights[terrainIndex++] = noise.getBaseHeight(
                                    blockX + dx,
                                    blockZ + dz,
                                    Heightmap.Types.WORLD_SURFACE_WG,
                                    heightAccessor,
                                    randomState);
                        }
                    }
                    if (!VillageTerrainSuitabilityPolicy.isSuitable(terrainHeights)) {
                        return StructureStart.INVALID_START;
                    }
                    Holder<Biome> baseBiome = biomeSource.getNoiseBiome(
                            Math.floorDiv(blockX, 4),
                            Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                            Math.floorDiv(blockZ, 4),
                            randomState.sampler());
                    Holder<Biome> pickedBiome = LatitudeBiomes.pick(
                            biomeRegistry,
                            baseBiome,
                            blockX,
                            blockZ,
                            LatitudeBiomes.SURFACE_CLASSIFY_Y,
                            radius,
                            randomState.sampler(),
                            "VILLAGE_START",
                            noise,
                            randomState,
                            heightAccessor);
                    Holder<Biome> finalBiome = pickedBiome != null ? pickedBiome : baseBiome;
                    Identifier finalBiomeId = biomeRegistry.getKey(finalBiome.value());
                    if (finalBiomeId != null
                            && LatitudeBiomes.villageVariantVsBiomeMismatch(
                            structureId.getPath(), finalBiomeId.toString())) {
                        return StructureStart.INVALID_START;
                    }
                }
            } catch (RuntimeException ignored) {
                // Registry unavailable — fail open (allow generation). Disarm the footprint law
                // too: without a resolved registry and Latitude biome source there is nothing
                // trustworthy to judge the footprint against.
                generatedWorldRadius = 0;
                generatedBiomeRegistry = null;
            }
        }
        StructureStart generated = original.call(
                structure,
                structureHolder,
                levelKey,
                registryAccess,
                chunkGenerator,
                structureBiomeSource,
                randomState,
                templateManager,
                seed,
                chunkPos,
                references,
                heightAccessor,
                validBiome);
        if (generated == null
                || !latitudeOwned
                || !generated.isValid()
                || generatedStructureId == null
                || generatedBiomeRegistry == null) {
            // Fail open: another mixin may hand back nothing at all, and the footprint law never
            // invents a start it was not given.
            return generated;
        }

        try {
            BoundingBox footprint = generated.getBoundingBox();
            if (StructureSitingPolicy.intersectsEastWestDangerZone(
                    footprint.minX(), footprint.maxX(), generatedWorldRadius)) {
                return StructureStart.INVALID_START;
            }
            if (!StructureSitingPolicy.requiresBadlandsFreeFootprint(
                    generatedStructureId.getPath(), generatedVillage)) {
                return generated;
            }

            List<String> sampledBiomes = new ArrayList<>();
            for (StructureSitingPolicy.FootprintSample sample :
                    StructureSitingPolicy.footprintSamples(
                            footprint.minX(),
                            footprint.maxX(),
                            footprint.minZ(),
                            footprint.maxZ())) {
                Holder<Biome> finalBiome = structureBiomeSource.getNoiseBiome(
                        Math.floorDiv(sample.x(), 4),
                        Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                        Math.floorDiv(sample.z(), 4),
                        randomState.sampler());
                Identifier biomeId = generatedBiomeRegistry.getKey(finalBiome.value());
                if (biomeId != null) {
                    sampledBiomes.add(biomeId.toString());
                }
            }
            if (StructureSitingPolicy.shouldRejectBadlandsFootprint(
                    generatedStructureId.getPath(), generatedVillage, sampledBiomes)) {
                return StructureStart.INVALID_START;
            }
        } catch (RuntimeException ignored) {
            // Post-generation policy could not resolve; preserve the existing fail-open boundary.
        }
        return generated;
    }
}
