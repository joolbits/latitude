package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
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
            method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void globe$treeLineGuard(WorldGenLevel level,
                                     ChunkGenerator generator,
                                     RandomSource random,
                                     BlockPos origin,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!LatitudeWorldgenScope.isActive()
                || !(generator instanceof NoiseBasedChunkGenerator noise)
                || !GlobeMod.shouldApplyLatitudeWorldgen(noise)) {
            return;
        }

        double suppress = LatitudeBiomes.treeLineSuppression(origin.getY());
        if (suppress <= 0.0) {
            return;
        }
        if (suppress >= 1.0 || random.nextDouble() < suppress) {
            cir.setReturnValue(false);
        }
    }
}
