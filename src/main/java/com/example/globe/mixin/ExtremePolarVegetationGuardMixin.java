package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents trees and grass/flower patches from generating past the
 * extreme-polar-cap latitude cutoff, enforcing a dead barren cap.
 */
// RandomPatchFeature removed in 26.1; vegetation patch guard requires separate research
@Mixin(TreeFeature.class)
public class ExtremePolarVegetationGuardMixin {

    @Inject(method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"), cancellable = true)
    private void globe$blockVegetationInExtremePolar(FeaturePlaceContext<?> context,
                                                      CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.origin();
        if (LatitudeBiomes.isBlockInExtremePolarCap(origin.getZ(), GlobeMod.BORDER_RADIUS)) {
            cir.setReturnValue(false);
        }
    }
}
