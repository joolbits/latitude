package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomeSource;
import com.example.globe.world.VillageBiomeAdmissionPolicy;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Latitude's final structure footprint inside the structure's biome law. Minecraft 26.3's
 * generation context now carries the prepared biome resolver directly, so the 26.2 redirect away
 * from the generator's raw provider source is no longer needed.
 */
@Mixin(Structure.class)
public abstract class StructureBiomeMatchGuardMixin {

    /**
     * A completed structure must retain its biome law at its visible center. This is deliberately
     * one terrain-aware probe: structure creation runs while fresh spawn chunks are prepared.
     */
    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    private void globe$rejectIllegalLatitudeFootprint(
            Holder<Structure> structureHolder,
            ResourceKey<Level> levelKey,
            RegistryAccess registryAccess,
            ChunkGenerator generator,
            BiomeSource biomeSource,
            Climate.Sampler climateSampler,
            RandomState randomState,
            StructureTemplateManager templateManager,
            long seed,
            ChunkPos chunkPos,
            int references,
            LevelHeightAccessor heightAccessor,
            Predicate<Holder<Biome>> validBiome,
            CallbackInfoReturnable<StructureStart> cir) {
        if (!(biomeSource instanceof LatitudeBiomeSource latitudeSource)) {
            return;
        }
        StructureStart start = cir.getReturnValue();
        if (start == null || !start.isValid()) {
            return;
        }

        BlockPos center = start.getBoundingBox().getCenter();
        try {
            Holder<Biome> resolved = latitudeSource.getNoiseBiome(
                    Math.floorDiv(center.getX(), 4),
                    Math.floorDiv(center.getY(), 4),
                    Math.floorDiv(center.getZ(), 4),
                    climateSampler);
            Registry<Structure> structureRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
            Registry<Biome> biomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME);
            Identifier structureId = structureRegistry.getKey((Structure) (Object) this);
            Identifier biomeId = resolved == null ? null : biomeRegistry.getKey(resolved.value());
            boolean woodlandMansionInCustomBiome = structureId != null
                    && "minecraft".equals(structureId.getNamespace())
                    && "mansion".equals(structureId.getPath())
                    && biomeId != null
                    && !"minecraft".equals(biomeId.getNamespace());
            boolean badlandsDesolationMismatch = structureId != null
                    && biomeId != null
                    && VillageBiomeAdmissionPolicy.shouldRefuseStructureInVillageFreeBiome(
                            structureId.getPath(), biomeId.toString());
            if (resolved == null
                    || !validBiome.test(resolved)
                    || woodlandMansionInCustomBiome
                    || badlandsDesolationMismatch) {
                cir.setReturnValue(StructureStart.INVALID_START);
            }
        } catch (RuntimeException resolutionFailure) {
            cir.setReturnValue(StructureStart.INVALID_START);
        }
    }
}
