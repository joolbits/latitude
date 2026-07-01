package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels a structure whose id DECLARES a climate (village_desert, village_savanna, a modded desert/snowy
 * outpost, ...) when it lands in a biome of the WRONG climate — a savanna village in a forest, a desert-styled
 * pillager outpost in a tundra, etc.
 *
 * <p>Root cause: vanilla picks the structure variant from the RAW biome source at the structure-starts step,
 * but Latitude repaints biomes later at the populate step (ChunkGeneratorPopulateBiomesMixin, doCreateBiomes).
 * By {@code placeInChunk} time the block-placement runs AFTER populate, so {@code world.getBiome()} is
 * Latitude's biome and can disagree with the variant vanilla chose. We can't re-pick the variant here, so we
 * cancel the clearly-mismatched ones (better a missing structure than a savanna village in a forest).
 * Conservative: only climate-named structures are judged, and it fails open (no cancel) on any uncertainty, so
 * plains villages and neutral structures are never touched.</p>
 */
@Mixin(StructureStart.class)
public abstract class StructureBiomeMatchGuardMixin {

    @Shadow
    public abstract Structure getStructure();

    @Shadow
    public abstract ChunkPos getChunkPos();

    @Inject(method = "placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"), cancellable = true)
    private void globe$cancelClimateMismatchedStructure(WorldGenLevel world,
                                                        StructureManager structureAccessor,
                                                        ChunkGenerator chunkGenerator,
                                                        RandomSource random,
                                                        BoundingBox chunkBox,
                                                        ChunkPos chunkPos,
                                                        CallbackInfo ci) {
        try {
            Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Identifier id = registry.getKey(this.getStructure());
            if (id == null) {
                return;
            }
            ChunkPos cp = this.getChunkPos();
            BlockPos center = new BlockPos(cp.getMiddleBlockX(), chunkBox.minY(), cp.getMiddleBlockZ());
            if (LatitudeBiomes.structureClimateMismatch(id.getPath(), world.getBiome(center))) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Registry/biome unavailable — fail open (allow placement).
        }
    }
}
