package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeBands;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents FreezeTopLayerFeature from placing snow layers and ice in warm
 * latitude bands. This feature runs AFTER surface building and carving,
 * which is why earlier guards (ProtoChunk, surface rules) could not catch it.
 */
@Mixin(SnowAndFreezeFeature.class)
public class FreezeTopLayerFeatureGuardMixin {

    @Unique
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LatitudeSnowGuard");

    @Unique
    private static final java.util.concurrent.atomic.AtomicInteger GUARD_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    @Unique
    private static boolean globe$isWarmBand(int blockZ) {
        int borderRadius = GlobeMod.BORDER_RADIUS;
        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        if (activeRadius > 0) borderRadius = activeRadius;
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(Math.abs((double) blockZ) * 90.0 / Math.max(1, borderRadius));
        return band == LatitudeBands.Band.TROPICAL
                || band == LatitudeBands.Band.SUBTROPICAL
                || band == LatitudeBands.Band.TEMPERATE;
    }

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void globe$blockFreezeInWarmBands(FeaturePlaceContext<NoneFeatureConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.origin();
        if (globe$isWarmBand(origin.getZ())) {
            if (GUARD_LOG_COUNT.incrementAndGet() <= 10) {
                LOGGER.warn("[FREEZE_GUARD] Blocked FreezeTopLayer at chunk origin x={} z={} band={}",
                        origin.getX(), origin.getZ(),
                        LatitudeBands.fromAbsoluteLatitudeDeg(
                                Math.abs((double) origin.getZ()) * 90.0
                                        / Math.max(1, LatitudeBiomes.getActiveRadiusBlocks() > 0 ? LatitudeBiomes.getActiveRadiusBlocks() : GlobeMod.BORDER_RADIUS)
                        ).id());
            }
            cir.setReturnValue(false);
        }
    }
}
