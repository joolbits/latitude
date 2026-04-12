package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.RandomPatchFeature;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents trees and grass/flower patches from generating past the
 * extreme-polar-cap latitude cutoff, enforcing a dead barren cap.
 */
@Mixin({TreeFeature.class, RandomPatchFeature.class})
public class ExtremePolarVegetationGuardMixin {

    @Inject(method = "generate(Lnet/minecraft/world/gen/feature/util/FeatureContext;)Z",
            at = @At("HEAD"), cancellable = true)
    private void globe$blockVegetationInExtremePolar(FeatureContext<?> context,
                                                      CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.getOrigin();
        if (LatitudeBiomes.isBlockInExtremePolarCap(origin.getZ(), GlobeMod.BORDER_RADIUS)) {
            cir.setReturnValue(false);
        }
    }
}
