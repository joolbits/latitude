package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses trees above the Latitude tree line in Globe worlds, with a fade band below it.
 */
@Mixin(TreeFeature.class)
public class TreeLineVegetationGuardMixin {

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void globe$treeLineGuard(FeaturePlaceContext<?> context,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (LatitudeBiomes.ACTIVE_RADIUS_BLOCKS <= 0) {
            return;
        }

        double suppress = LatitudeBiomes.treeLineSuppression(context.origin().getY());
        if (suppress <= 0.0) {
            return;
        }
        if (suppress >= 1.0 || context.random().nextDouble() < suppress) {
            cir.setReturnValue(false);
        }
    }
}
