package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
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

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"), cancellable = true)
    private void globe$blockVegetationInExtremePolar(FeaturePlaceContext<?> context,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeWorldgenScope.isActive()
                || !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return;
        }

        BlockPos origin = context.origin();
        if (LatitudeBiomes.isBlockBeyondPolarFoliageLimit(origin.getZ(), GlobeMod.BORDER_RADIUS)) {
            cir.setReturnValue(false);
        }
    }
}
