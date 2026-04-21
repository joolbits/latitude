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
 * Prevents village structures from being placed past the extreme-polar-cap
 * latitude cutoff. Other structures (igloos, etc.) are not affected.
 */
@Mixin(StructureStart.class)
public abstract class ExtremePolarVillageGuardMixin {

    @Shadow
    public abstract Structure getStructure();

    @Shadow
    public abstract ChunkPos getPos();

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void globe$blockVillagesInExtremePolar(WorldGenLevel world,
                                                    StructureManager structureAccessor,
                                                    ChunkGenerator chunkGenerator,
                                                    RandomSource random,
                                                    BoundingBox chunkBox,
                                                    ChunkPos chunkPos,
                                                    CallbackInfo ci) {
        int blockZ = this.getPos().getMiddleBlockZ();
        if (!LatitudeBiomes.isBlockInExtremePolarCap(blockZ, GlobeMod.BORDER_RADIUS)) {
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
