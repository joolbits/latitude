package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents climate-named village variants from being placed in a clearly incompatible
 * Latitude band. Existing generated village blocks are intentionally untouched.
 */
@Mixin(StructureStart.class)
public abstract class StructureBiomeMatchGuardMixin {

    @Shadow
    public abstract Structure getStructure();

    @Shadow
    public abstract ChunkPos getChunkPos();

    @Inject(method = "placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"), cancellable = true)
    private void globe$cancelClimateMismatchedVillage(
            WorldGenLevel world,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox chunkBox,
            ChunkPos chunkPos,
            CallbackInfo ci) {
        try {
            if (!LatitudeWorldgenScope.isActive()
                    || !(chunkGenerator instanceof NoiseBasedChunkGenerator noise)
                    || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
                return;
            }

            Registry<Structure> registry =
                    world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Identifier structureId = registry.getKey(this.getStructure());
            if (structureId == null) {
                return;
            }

            var border = world.getWorldBorder();
            double halfSize = LatitudeMath.halfSize(border);
            if (!(halfSize > 0.0 && halfSize < 1_000_000.0)) {
                return;
            }

            int blockZ = this.getChunkPos().getMiddleBlockZ();
            double absDeg = Math.abs(LatitudeMath.degreesFromZ(border, blockZ));
            LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
            if (LatitudeBiomes.villageClimateVsBandMismatch(structureId.getPath(), band)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Registry, border, or band unavailable — fail open (allow placement).
        }
    }
}
