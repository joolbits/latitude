package com.example.globe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.StructureSitingPolicy;
import com.example.globe.world.VillageBiomeAdmissionPolicy;
import com.example.globe.world.VillageTerrainSuitabilityPolicy;
import net.minecraft.core.BlockPos;
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
 * Rejects invalid fresh village starts before vanilla can register them. Villages are rejected
 * strictly beyond 80 degrees absolute latitude, when their declared climate conflicts with the
 * canonical Latitude band, or when a bounded physical sample crosses cliff-scale terrain.
 * Compatible/neutral villages on suitable terrain are not affected.
 *
 * <p>The post-generation block below applies to every structure, not only villages: it holds the
 * east/west danger-zone rule, the woodland-mansion vanilla-biome rule, and the badlands-desolation
 * footprint ruling, each judged against the finished start rather than the start chunk.
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
        BiomeSource effectiveBiomeSource = biomeSource;
        boolean latitudeOwned = LatitudeWorldgenScope.isActive()
                && chunkGenerator instanceof NoiseBasedChunkGenerator noise
                && GlobeMod.shouldApplyLatitudeWorldgen(noise);
        Identifier generatedStructureId = null;
        boolean generatedVillage = false;
        int generatedWorldRadius = 0;
        Registry<Biome> generatedBiomeRegistry = null;
        if (latitudeOwned && chunkGenerator instanceof NoiseBasedChunkGenerator noise) {
            // Hand vanilla the biome the player will actually stand in.
            //
            // ChunkGenerator.tryGenerateStructure reads the raw `biomeSource` FIELD, while
            // ServerLevel builds its StructureCheck from getBiomeSource() — which our
            // ChunkGeneratorBiomeSourceMixin overrides to the Latitude wrapper. So vanilla's
            // "does a structure exist here" prediction (what /locate answers from) judged the
            // repainted biome while actual generation judged the raw one. That disagreement is
            // exactly the reported defect in both directions: /locate promising a desert pyramid
            // that never generated, and pyramids generating on raw desert that Latitude then
            // repainted to snow. Substituting the wrapper here makes siting judge the final
            // biome, so prediction and generation agree and structures land in the biome a
            // player sees.
            BiomeSource authoritative = chunkGenerator.getBiomeSource();
            if (authoritative instanceof com.example.globe.world.LatitudeBiomeSource) {
                effectiveBiomeSource = authoritative;
            }
            try {
                Registry<Structure> registry =
                        registryAccess.lookupOrThrow(Registries.STRUCTURE);
                Identifier structureId = registry.getKey(structure);
                boolean village = structureHolder.is(StructureTags.VILLAGE)
                        || (structureId != null && structureId.getPath().contains("village"));
                generatedStructureId = structureId;
                generatedVillage = village;
                generatedWorldRadius = GlobeMod.borderRadiusForNoiseGenerator(noise);
                generatedBiomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME);
                if (structureId != null && village) {
                    int radius = generatedWorldRadius;
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
                    Registry<Biome> biomeRegistry = generatedBiomeRegistry;
                    // Judge the biome the world will PAINT at this column — the authoritative
                    // wrapper source — not a fresh pick with different terrain evidence.
                    // Re-picking under VILLAGE_START computed preview heights the SOURCE paint
                    // path skips, and the two disagreed about where desert is: a measured
                    // world at ~1.7% desert produced 0/1348 accepted desert-village candidates
                    // with a rejection profile identical to a world with half the desert.
                    // One authority, agreement by construction (2026-08-16).
                    Holder<Biome> finalBiome = effectiveBiomeSource.getNoiseBiome(
                            Math.floorDiv(blockX, 4),
                            Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                            Math.floorDiv(blockZ, 4),
                            randomState.sampler());
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
                    // already rejects the base, any exception) falls through and generates. That
                    // asymmetry has one proven hole: when the raw base FAILS the predicate, this
                    // branch stands down entirely, and vanilla's own check then samples the
                    // substituted source at the structure's start height, which can disagree with
                    // the painted surface — live worlds grew desert pyramids and desert outposts on
                    // badlands that no tag permits. The badlands ruling below closes that hole for
                    // the structures the ruling names, judged against the final SURFACE biome; the
                    // conservative predicate re-test is unchanged for everything else, so
                    // underground structures whose biomes are never surface picks stay safe.
                    int radius = generatedWorldRadius;
                    int blockX = chunkPos.getMiddleBlockX();
                    Registry<Biome> biomeRegistry = generatedBiomeRegistry;
                    Holder<Biome> baseBiome = biomeSource.getNoiseBiome(
                            Math.floorDiv(blockX, 4),
                            Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                            Math.floorDiv(blockZ, 4),
                            randomState.sampler());
                    // Same single-authority rule as the village branch: the painted biome comes
                    // from the wrapper source, never from a re-pick with different evidence.
                    Holder<Biome> finalBiome = effectiveBiomeSource.getNoiseBiome(
                            Math.floorDiv(blockX, 4),
                            Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                            Math.floorDiv(blockZ, 4),
                            randomState.sampler());
                    Identifier finalBiomeId = biomeRegistry.getKey(finalBiome.value());
                    if (finalBiomeId != null
                            && VillageBiomeAdmissionPolicy.shouldRefuseStructureInVillageFreeBiome(
                                    structureId.getPath(), finalBiomeId.toString())) {
                        return StructureStart.INVALID_START;
                    }
                    if (validBiome.test(baseBiome)
                            && finalBiome != baseBiome && !validBiome.test(finalBiome)) {
                        return StructureStart.INVALID_START;
                    }
                }
            } catch (RuntimeException ignored) {
                // Registry unavailable — fail open (allow generation).
            }
        }
        StructureStart generated = original.call(
                structure,
                structureHolder,
                levelKey,
                registryAccess,
                chunkGenerator,
                effectiveBiomeSource,
                randomState,
                templateManager,
                seed,
                chunkPos,
                references,
                heightAccessor,
                validBiome);
        if (!latitudeOwned
                || !generated.isValid()
                || generatedStructureId == null
                || generatedBiomeRegistry == null) {
            return generated;
        }

        try {
            BoundingBox footprint = generated.getBoundingBox();
            if (StructureSitingPolicy.intersectsEastWestDangerZone(
                    footprint.minX(), footprint.maxX(), generatedWorldRadius)) {
                return StructureStart.INVALID_START;
            }
            // A woodland mansion must come to rest in a vanilla biome. Everything else in this
            // block judges the footprint; this one judges the finished start's own center,
            // because that is the column the mansion actually occupies once the jigsaw has
            // settled and it can be a long way from the start chunk. Sampled at the same
            // SURFACE_CLASSIFY_Y as every other authority in this guard, so the mansion is judged
            // against the biome the world paints rather than a depth-dependent cave reading.
            if (StructureSitingPolicy.requiresVanillaBiomeSiting(
                    generatedStructureId.getNamespace(), generatedStructureId.getPath())) {
                BlockPos center = footprint.getCenter();
                Holder<Biome> centerBiome = effectiveBiomeSource.getNoiseBiome(
                        Math.floorDiv(center.getX(), 4),
                        Math.floorDiv(LatitudeBiomes.SURFACE_CLASSIFY_Y, 4),
                        Math.floorDiv(center.getZ(), 4),
                        randomState.sampler());
                Identifier centerBiomeId = generatedBiomeRegistry.getKey(centerBiome.value());
                if (centerBiomeId != null && StructureSitingPolicy.shouldRejectCustomBiomeSiting(
                        generatedStructureId.getNamespace(),
                        generatedStructureId.getPath(),
                        centerBiomeId.getNamespace())) {
                    return StructureStart.INVALID_START;
                }
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
                Holder<Biome> finalBiome = effectiveBiomeSource.getNoiseBiome(
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
