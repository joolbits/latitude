package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents trees from generating strictly beyond 80 degrees absolute latitude.
 * Smaller foliage is handled separately after SimpleBlockFeature samples its state.
 */
@Mixin(TreeFeature.class)
public class ExtremePolarVegetationGuardMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void globe$blockVegetationInExtremePolar(WorldGenLevel level,
                                                      ChunkGenerator generator,
                                                      RandomSource random,
                                                      BlockPos origin,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeWorldgenScope.isActive()
                || !(generator instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return;
        }

        if (LatitudeBiomes.isBlockBeyondPolarWoodyLimit(origin.getZ(), GlobeMod.BORDER_RADIUS)) {
            cir.setReturnValue(false);
        }
    }
}
