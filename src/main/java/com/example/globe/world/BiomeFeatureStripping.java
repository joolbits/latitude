package com.example.globe.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class BiomeFeatureStripping {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");
    private static final Identifier STRIP_FROZEN_RIVER_ID = new Identifier("globe", "strip_frozen_river_vegetal");

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
        GenerationStep.Feature step = GenerationStep.Feature.VEGETAL_DECORATION;
        int attempted = 0;
        int removed = 0;

        boolean enumerated = false;
        try {
            List<RegistryKey<PlacedFeature>> stepKeys = findStepFeatures(ctx, step);
            enumerated = !stepKeys.isEmpty();
            attempted += stepKeys.size();
            for (RegistryKey<PlacedFeature> key : stepKeys) {
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

    private static List<RegistryKey<PlacedFeature>> findStepFeatures(BiomeModificationContext ctx, GenerationStep.Feature step) {
        List<RegistryKey<PlacedFeature>> keys = new ArrayList<>();
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
                            if (entry instanceof RegistryEntry<?> registryEntry) {
                                Optional<? extends RegistryKey<?>> key = registryEntry.getKey();
                                if (key.isPresent()) {
                                    @SuppressWarnings("unchecked")
                                    RegistryKey<PlacedFeature> cast = (RegistryKey<PlacedFeature>) key.get();
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
