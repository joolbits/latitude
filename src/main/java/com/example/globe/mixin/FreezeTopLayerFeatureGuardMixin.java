package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.FreezeTopLayerFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;
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
@Mixin(FreezeTopLayerFeature.class)
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
        LatitudeMath.LatitudeZone zone = LatitudeMath.zoneForRadius(borderRadius, blockZ);
        return zone == LatitudeMath.LatitudeZone.EQUATOR
                || zone == LatitudeMath.LatitudeZone.TROPICAL
                || zone == LatitudeMath.LatitudeZone.SUBTROPICAL
                || zone == LatitudeMath.LatitudeZone.TEMPERATE;
    }

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void globe$blockFreezeInWarmBands(FeatureContext<DefaultFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.getOrigin();
        if (globe$isWarmBand(origin.getZ())) {
            if (GUARD_LOG_COUNT.incrementAndGet() <= 10) {
                LOGGER.warn("[FREEZE_GUARD] Blocked FreezeTopLayer at chunk origin x={} z={} band={}",
                        origin.getX(), origin.getZ(),
                        LatitudeMath.zoneForRadius(
                                LatitudeBiomes.getActiveRadiusBlocks() > 0 ? LatitudeBiomes.getActiveRadiusBlocks() : GlobeMod.BORDER_RADIUS,
                                origin.getZ()));
            }
            cir.setReturnValue(false);
        }
    }
}
