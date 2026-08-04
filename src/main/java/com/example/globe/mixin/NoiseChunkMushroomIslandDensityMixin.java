package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Raises and materializes only the V2-reserved Mushroom Fields province as an ocean island. */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMushroomIslandDensityMixin {
    @ModifyReturnValue(method = "getInterpolatedDensity()D", at = @At("RETURN"))
    private double globe$raiseReservedMushroomIsland(double original) {
        if (!LatitudeWorldgenScope.isActive()) return original;
        NoiseChunk self = (NoiseChunk) (Object) this;
        return LatitudeBiomes.mushroomIslandDensity(
                original, self.blockX(), self.blockY(), self.blockZ());
    }

    @ModifyReturnValue(
            method = "getInterpolatedState()Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private BlockState globe$materializeReservedMushroomIsland(BlockState original) {
        if (!LatitudeWorldgenScope.isActive()) return original;
        NoiseChunk self = (NoiseChunk) (Object) this;
        // NoiseBasedChunkGenerator.doFill replaces null with its configured default solid block.
        // This is the live block-writing path; the density return alone serves height queries.
        return LatitudeBiomes.isMushroomIslandSolid(
                self.blockX(), self.blockY(), self.blockZ()) ? null : original;
    }
}
