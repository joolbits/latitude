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
 *
 * <p><b>S43 MINESHAFT VETO (Peetsa 2026-07-25, B-9 punchlist: "mineshafts inside caves doesn't make much
 * sense"):</b> the same placeInChunk cancel now also gates structures whose id path starts with
 * {@code "mineshaft"} (both vanilla variants + any pack mineshaft) at/beyond the BARRENS onset (82 deg,
 * {@code isBlockInPolarMineshaftVetoBand}) -- the glacial underground line. Timber mines through a living
 * glacier break the fiction; the frozen expedition cache remains the polar underground's human story.
 * Same accepted trade as villages: the structure START still exists (locate finds it), no blocks stamp.
 * Structure placement resolves biomes from the RAW multi_noise source (the carver dead-wiring class), so
 * a biome-tag exclusion could never work here -- the code-side veto is the only real seam.
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
        boolean villageBand = LatitudeBiomes.isBlockInPolarVillageVetoBand(blockZ, GlobeMod.BORDER_RADIUS);
        boolean mineshaftBand = LatitudeBiomes.isBlockInPolarMineshaftVetoBand(blockZ, GlobeMod.BORDER_RADIUS);
        if (!villageBand && !mineshaftBand) {
            return;
        }
        Structure structure = this.getStructure();
        try {
            Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Identifier structureId = registry.getKey(structure);
            if (structureId == null) {
                return;
            }
            String path = structureId.getPath();
            if ((villageBand && path.startsWith("village"))
                    || (mineshaftBand && path.startsWith("mineshaft"))) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Registry unavailable — fail open (allow placement).
        }
    }
}
