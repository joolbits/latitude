package com.example.globe.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class BiomeFeatureStripping {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");
    private static final Identifier STRIP_FROZEN_RIVER_ID = Identifier.fromNamespaceAndPath("globe", "strip_frozen_river_vegetal");

    private BiomeFeatureStripping() {
    }

    public static void init() {
        if (Boolean.getBoolean("latitude.disableFeatureStripping")) {
            LOGGER.info("[Latitude] Biome feature stripping disabled by system property.");
            return;
        }
        BiomeModifications.create(STRIP_FROZEN_RIVER_ID)
                .add(ModificationPhase.REMOVALS,
                        ctx -> ctx.getBiomeKey().equals(BiomeKeys.FROZEN_RIVER),
                        BiomeFeatureStripping::stripFrozenRiverVegetation);
    }

    private static void stripFrozenRiverVegetation(BiomeModificationContext ctx) {
        GenerationStep.Decoration step = GenerationStep.Decoration.VEGETAL_DECORATION;
        int attempted = 0;
        int removed = 0;

        boolean enumerated = false;
        try {
            List<ResourceKey<PlacedFeature>> stepKeys = findStepFeatures(ctx, step);
            enumerated = !stepKeys.isEmpty();
            attempted += stepKeys.size();
            for (ResourceKey<PlacedFeature> key : stepKeys) {
                if (ctx.getGenerationSettings().removeFeature(step, key)) {
                    removed++;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[Latitude] Frozen river vegetal enumeration failed: {}", t.toString());
        }

        if (!enumerated) {
            LOGGER.warn("[Latitude] Frozen river vegetal enumeration empty; no VEGETAL_DECORATION features removed.");
        }

        LOGGER.info("[Latitude] Frozen river vegetal removal attempted={} removed={} step={}", attempted, removed, step);
    }

    private static List<ResourceKey<PlacedFeature>> findStepFeatures(BiomeModificationContext ctx, GenerationStep.Decoration step) {
        List<ResourceKey<PlacedFeature>> keys = new ArrayList<>();
        Object generationSettings = extractGenerationSettings(ctx.getGenerationSettings());
        if (generationSettings == null) {
            return keys;
        }
        try {
            Object features = generationSettings.getClass().getMethod("getFeatures").invoke(generationSettings);
            if (features instanceof List<?> steps) {
                int idx = step.ordinal();
                if (idx >= 0 && idx < steps.size()) {
                    Object stepList = steps.get(idx);
                    if (stepList instanceof List<?> placedList) {
                        for (Object entry : placedList) {
                            if (entry instanceof Holder<?> registryEntry) {
                                Optional<? extends ResourceKey<?>> key = registryEntry.unwrapKey();
                                if (key.isPresent()) {
                                    @SuppressWarnings("unchecked")
                                    ResourceKey<PlacedFeature> cast = (ResourceKey<PlacedFeature>) key.get();
                                    keys.add(cast);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[Latitude] Frozen river feature list unavailable: {}", t.toString());
        }
        return keys;
    }

    private static Object extractGenerationSettings(Object generationSettingsContext) {
        try {
            var field = generationSettingsContext.getClass().getDeclaredField("generationSettings");
            field.setAccessible(true);
            return field.get(generationSettingsContext);
        } catch (Throwable ignored) {
            return null;
        }
    }

}
