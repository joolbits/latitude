package com.example.globe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
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
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * Rejects invalid fresh village starts before vanilla can register them. Villages are rejected
 * strictly beyond 80 degrees absolute latitude, when their declared climate conflicts with the
 * canonical Latitude band, or when a bounded physical sample crosses cliff-scale terrain.
 * Compatible/neutral villages on suitable terrain and other structures are not affected.
 */
@Mixin(ChunkGenerator.class)
public abstract class ExtremePolarVillageStartGuardMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");

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
                boolean village = structureHolder.is(StructureTags.VILLAGE)
                        || (structureId != null && structureId.getPath().contains("village"));
                if (structureId != null && village) {
                    int radius = GlobeMod.borderRadiusForNoiseGenerator(noise);
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
                    Registry<Biome> biomeRegistry =
                            registryAccess.lookupOrThrow(Registries.BIOME);
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
                } else if (structureId != null && validBiome != null) {
                    // General siting authority. Vanilla tests the structure's own biome predicate
                    // against the raw multi-noise biome, which is not what the player ends up
                    // standing in: Latitude repaints the column afterwards, so a desert pyramid can
                    // be sited on vanilla's desert and then find itself in snow. Re-test the very
                    // same predicate against Latitude's final biome for this column.
                    //
                    // Deliberately conservative — a structure guard that over-rejects silently
                    // empties the world. We only overrule vanilla where it would have said yes and
                    // Latitude's own biome says no; anything else (unknown biome, predicate that
                    // already rejects the base, any exception) falls through and generates.
                    int radius = GlobeMod.borderRadiusForNoiseGenerator(noise);
                    int blockX = chunkPos.getMiddleBlockX();
                    LOGGER.info("[LAT][STRUCTGUARD] enter structure={} x={} z={} radius={}",
                            structureId, blockX, blockZ, radius);
                    Registry<Biome> biomeRegistry =
                            registryAccess.lookupOrThrow(Registries.BIOME);
                    Holder<Biome> baseBiome = biomeSource.getNoiseBiome(
                            Math.floorDiv(blockX, 4),
                            Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                            Math.floorDiv(blockZ, 4),
                            randomState.sampler());
                    boolean baseValid = validBiome.test(baseBiome);
                    Identifier baseBiomeId = biomeRegistry.getKey(baseBiome.value());
                    LOGGER.info("[LAT][STRUCTGUARD] structure={} baseBiome={} baseValid={}",
                            structureId, baseBiomeId, baseValid);
                    if (baseValid) {
                        Holder<Biome> pickedBiome;
                        try {
                            pickedBiome = LatitudeBiomes.pick(
                                    biomeRegistry,
                                    baseBiome,
                                    blockX,
                                    blockZ,
                                    LatitudeBiomes.SURFACE_CLASSIFY_Y,
                                    radius,
                                    randomState.sampler(),
                                    "STRUCTURE_START",
                                    noise,
                                    randomState,
                                    heightAccessor);
                        } catch (RuntimeException pickFailure) {
                            LOGGER.warn("[LAT][STRUCTGUARD] LatitudeBiomes.pick threw for structure={} x={} z={}; "
                                            + "falling open (structure generation NOT blocked)",
                                    structureId, blockX, blockZ, pickFailure);
                            pickedBiome = null;
                        }
                        Identifier pickedBiomeId = pickedBiome != null ? biomeRegistry.getKey(pickedBiome.value()) : null;
                        boolean pickedValid = pickedBiome != null && validBiome.test(pickedBiome);
                        LOGGER.info("[LAT][STRUCTGUARD] structure={} pickedBiome={} pickedValid={} verdict={}",
                                structureId, pickedBiomeId, pickedValid,
                                pickedBiome == null ? "FALL-OPEN (null pick)" : (pickedValid ? "ALLOW" : "REJECT"));
                        if (pickedBiome != null && !pickedValid) {
                            return StructureStart.INVALID_START;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
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
