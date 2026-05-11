package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public class PlacedFeatureBopDebugMixin {
    @Unique
    private static final boolean LATITUDE_DEBUG_BOP_FEATURES = Boolean.getBoolean("latitude.debugBopFeatures");
    @Unique
    private static final boolean LATITUDE_DEBUG_BOP_FEATURES_EXPENSIVE = Boolean.getBoolean("latitude.debugBopFeatures.expensive");
    @Unique
    private static final int LATITUDE_BOP_FEATURE_TARGET_X = Integer.getInteger("latitude.debugBopFeatures.x", 8512);
    @Unique
    private static final int LATITUDE_BOP_FEATURE_TARGET_Z = Integer.getInteger("latitude.debugBopFeatures.z", 4647);
    @Unique
    private static final int LATITUDE_BOP_FEATURE_RADIUS = Integer.getInteger("latitude.debugBopFeatures.radius", 512);
    @Unique
    private static final int LATITUDE_BOP_FEATURE_DETAIL_LIMIT = Integer.getInteger("latitude.debugBopFeatures.detailLimit", 20);
    @Unique
    private static final int LATITUDE_BOP_FEATURE_SUMMARY_EVERY = Integer.getInteger("latitude.debugBopFeatures.summaryEvery", 5000);
    @Unique
    private static final int LATITUDE_BOP_FEATURE_ATTEMPT_LIMIT = Integer.getInteger("latitude.debugBopFeatures.attemptLimit", 20000);
    @Unique
    private static final AtomicInteger LATITUDE_BOP_FEATURE_ATTEMPTS = new AtomicInteger();
    @Unique
    private static final AtomicInteger LATITUDE_BOP_FEATURE_SEASONAL_BIOME = new AtomicInteger();
    @Unique
    private static final AtomicInteger LATITUDE_BOP_FEATURE_BOP_CONFIGURED = new AtomicInteger();
    @Unique
    private static final AtomicInteger LATITUDE_BOP_FEATURE_BOP_IN_SEASONAL = new AtomicInteger();
    @Unique
    private static final AtomicInteger LATITUDE_BOP_FEATURE_SUCCESSES = new AtomicInteger();
    @Unique
    private static final AtomicInteger LATITUDE_BOP_FEATURE_DETAILS = new AtomicInteger();
    @Unique
    private static final AtomicInteger LATITUDE_BOP_FEATURE_DISABLED_SUMMARY = new AtomicInteger();

    @Inject(method = "placeWithContext", at = @At("RETURN"))
    private void latitude$debugBopFeaturePlacement(PlacementContext context,
                                                   net.minecraft.util.RandomSource random,
                                                   BlockPos origin,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!LATITUDE_DEBUG_BOP_FEATURES || !latitude$isNearTarget(origin)) {
            return;
        }
        if (!LATITUDE_DEBUG_BOP_FEATURES_EXPENSIVE) {
            if (LATITUDE_BOP_FEATURE_DISABLED_SUMMARY.getAndIncrement() == 0) {
                GlobeMod.LOGGER.info("[LAT][BOP_FEATURE] summary expensive=false target={},{} radius={} action=skipping per-feature biome/configured-feature inspection",
                        LATITUDE_BOP_FEATURE_TARGET_X, LATITUDE_BOP_FEATURE_TARGET_Z, LATITUDE_BOP_FEATURE_RADIUS);
            }
            return;
        }
        int attempts = LATITUDE_BOP_FEATURE_ATTEMPTS.incrementAndGet();
        if (attempts > LATITUDE_BOP_FEATURE_ATTEMPT_LIMIT) {
            return;
        }
        String biomeId = latitude$biomeId(context.getLevel().getBiome(origin));
        String configuredFeatureId = latitude$configuredFeatureId();
        boolean seasonalForest = "biomesoplenty:seasonal_forest".equals(biomeId);
        boolean bopConfiguredFeature = configuredFeatureId.startsWith("biomesoplenty:");
        boolean placed = Boolean.TRUE.equals(cir.getReturnValue());
        if (seasonalForest) {
            LATITUDE_BOP_FEATURE_SEASONAL_BIOME.incrementAndGet();
        }
        if (bopConfiguredFeature) {
            LATITUDE_BOP_FEATURE_BOP_CONFIGURED.incrementAndGet();
        }
        if (seasonalForest && bopConfiguredFeature) {
            LATITUDE_BOP_FEATURE_BOP_IN_SEASONAL.incrementAndGet();
        }
        if (placed) {
            LATITUDE_BOP_FEATURE_SUCCESSES.incrementAndGet();
        }
        if ((seasonalForest || bopConfiguredFeature) && LATITUDE_BOP_FEATURE_DETAILS.getAndIncrement() < LATITUDE_BOP_FEATURE_DETAIL_LIMIT) {
            GlobeMod.LOGGER.info("[LAT][BOP_FEATURE] detail origin={} y={} z={} biome={} seasonal={} configured={} bopConfigured={} placed={}",
                    origin.getX(), origin.getY(), origin.getZ(), biomeId, seasonalForest, configuredFeatureId, bopConfiguredFeature, placed);
        }
        if (attempts == 1 || attempts % LATITUDE_BOP_FEATURE_SUMMARY_EVERY == 0) {
            GlobeMod.LOGGER.info("[LAT][BOP_FEATURE] summary target={},{} radius={} attempts={} seasonalBiome={} bopConfigured={} bopConfiguredInSeasonal={} placed={}",
                    LATITUDE_BOP_FEATURE_TARGET_X, LATITUDE_BOP_FEATURE_TARGET_Z, LATITUDE_BOP_FEATURE_RADIUS,
                    attempts, LATITUDE_BOP_FEATURE_SEASONAL_BIOME.get(), LATITUDE_BOP_FEATURE_BOP_CONFIGURED.get(),
                    LATITUDE_BOP_FEATURE_BOP_IN_SEASONAL.get(), LATITUDE_BOP_FEATURE_SUCCESSES.get());
        }
        if (attempts == LATITUDE_BOP_FEATURE_ATTEMPT_LIMIT) {
            GlobeMod.LOGGER.info("[LAT][BOP_FEATURE] capped target={},{} radius={} attemptLimit={} seasonalBiome={} bopConfigured={} bopConfiguredInSeasonal={} placed={}",
                    LATITUDE_BOP_FEATURE_TARGET_X, LATITUDE_BOP_FEATURE_TARGET_Z, LATITUDE_BOP_FEATURE_RADIUS,
                    LATITUDE_BOP_FEATURE_ATTEMPT_LIMIT, LATITUDE_BOP_FEATURE_SEASONAL_BIOME.get(),
                    LATITUDE_BOP_FEATURE_BOP_CONFIGURED.get(), LATITUDE_BOP_FEATURE_BOP_IN_SEASONAL.get(),
                    LATITUDE_BOP_FEATURE_SUCCESSES.get());
        }
    }

    @Unique
    private static boolean latitude$isNearTarget(BlockPos origin) {
        return Math.abs(origin.getX() - LATITUDE_BOP_FEATURE_TARGET_X) <= LATITUDE_BOP_FEATURE_RADIUS
                && Math.abs(origin.getZ() - LATITUDE_BOP_FEATURE_TARGET_Z) <= LATITUDE_BOP_FEATURE_RADIUS;
    }

    @Unique
    private String latitude$configuredFeatureId() {
        Holder<ConfiguredFeature<?, ?>> feature = ((PlacedFeature) (Object) this).feature();
        return feature.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("unregistered");
    }

    @Unique
    private static String latitude$biomeId(Holder<Biome> biome) {
        Identifier id = biome.unwrapKey()
                .map(key -> key.identifier())
                .orElse(null);
        return id != null ? id.toString() : "unknown";
    }
}
