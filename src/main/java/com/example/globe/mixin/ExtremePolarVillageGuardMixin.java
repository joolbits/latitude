package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
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
 * Prevents village structures from being placed at or beyond the polar VILLAGE-veto latitude
 * ({@link LatitudeBiomes#isBlockInPolarVillageVetoBand(int, int)} = 80 deg since S13, "civilization
 * ends where the storm begins"). Gates ONLY structures whose registry id path starts with
 * {@code "village"} (every {@code minecraft:village_*} variant + any pack village) -- it cancels the
 * block PLACEMENT at {@code placeInChunk} HEAD; the structure start still exists but stamps no blocks.
 * Other structures (igloos, outposts, etc.) are untouched, an igloo being a mercy shelter in the death
 * band rather than an immersion break.
 *
 * <p>S13 note: the veto band moved 74.5->80 by switching from {@code isBlockInExtremePolarCap} to the
 * dedicated {@code isBlockInPolarVillageVetoBand}. The old 74.5 constant ALSO drives the deep-cap biome
 * monoculture and the tree/vegetation guards, so it stays at 74.5; only this village veto moved. Villages
 * therefore now generate in the 74.5-80 band (new chunks only).
 */
@Mixin(StructureStart.class)
public abstract class ExtremePolarVillageGuardMixin {

    @Shadow
    public abstract Structure getStructure();

    @Shadow
    public abstract ChunkPos getChunkPos();

    @Inject(method = "placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"), cancellable = true)
    private void globe$blockVillagesInExtremePolar(WorldGenLevel world,
                                                    StructureManager structureAccessor,
                                                    ChunkGenerator chunkGenerator,
                                                    RandomSource random,
                                                    BoundingBox chunkBox,
                                                    ChunkPos chunkPos,
                                                    CallbackInfo ci) {
        int blockZ = this.getChunkPos().getMiddleBlockZ();
        if (!LatitudeBiomes.isBlockInPolarVillageVetoBand(blockZ, GlobeMod.BORDER_RADIUS)) {
            return;
        }
        Structure structure = this.getStructure();
        try {
            Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Identifier structureId = registry.getKey(structure);
            if (structureId != null && structureId.getPath().startsWith("village")) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Registry unavailable — fail open (allow placement).
        }
    }
}
