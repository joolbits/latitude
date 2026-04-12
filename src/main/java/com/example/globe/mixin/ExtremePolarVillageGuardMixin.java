package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
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
    private void globe$blockVillagesInExtremePolar(StructureWorldAccess world,
                                                    StructureAccessor structureAccessor,
                                                    ChunkGenerator chunkGenerator,
                                                    Random random,
                                                    BlockBox chunkBox,
                                                    ChunkPos chunkPos,
                                                    CallbackInfo ci) {
        int blockZ = this.getPos().getCenterZ();
        if (!LatitudeBiomes.isBlockInExtremePolarCap(blockZ, GlobeMod.BORDER_RADIUS)) {
            return;
        }
        Structure structure = this.getStructure();
        try {
            Registry<Structure> registry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            Identifier structureId = registry.getId(structure);
            if (structureId != null && structureId.getPath().startsWith("village")) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Registry unavailable — fail open (allow placement).
        }
    }
}
