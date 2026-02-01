package com.example.globe.world;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class BiomeFeatureStripping {
    private static final Logger LOGGER = LoggerFactory.getLogger("globe");
    private static final Identifier STRIP_FROZEN_RIVER_ID = Identifier.of("globe", "strip_frozen_river_vegetal");

    private BiomeFeatureStripping() {
    }

    public static void init() {
        BiomeModifications.create(STRIP_FROZEN_RIVER_ID)
                .add(ModificationPhase.REMOVALS,
                        ctx -> ctx.getBiomeKey().equals(BiomeKeys.FROZEN_RIVER),
                        BiomeFeatureStripping::stripFrozenRiverVegetation);
    }

    private static void stripFrozenRiverVegetation(BiomeModificationContext ctx) {
        GenerationStep.Feature step = GenerationStep.Feature.VEGETAL_DECORATION;
        int attempted = 0;
        int removed = 0;

        attempted++;
        removed += removeFeature(ctx, step, Identifier.of("minecraft", "patch_sugar_cane"));
        attempted++;
        removed += removeFeature(ctx, step, Identifier.of("minecraft", "patch_grass"));
        attempted++;
        removed += removeFeature(ctx, step, Identifier.of("minecraft", "patch_tall_grass"));
        attempted++;
        removed += removeFeature(ctx, step, Identifier.of("minecraft", "patch_large_fern"));

        try {
            List<Identifier> fireflyIds = findFireflyPlacedFeatures();
            for (Identifier id : fireflyIds) {
                attempted++;
                removed += removeFeature(ctx, step, id);
            }
        } catch (Throwable t) {
            LOGGER.warn("[Latitude] Frozen river vegetal scan failed: {}", t.toString());
        }

        LOGGER.info("[Latitude] Frozen river vegetal removal attempted={} removed={} step={}", attempted, removed, step);
    }

    private static List<Identifier> findFireflyPlacedFeatures() {
        List<Identifier> fireflyIds = new ArrayList<>();
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.registry.Registries");
            Object registry = registriesClass.getField("PLACED_FEATURE").get(null);
            Iterable<?> ids = (Iterable<?>) registry.getClass().getMethod("getIds").invoke(registry);
            for (Object id : ids) {
                if (id instanceof Identifier identifier && identifier.getPath().contains("firefly")) {
                    fireflyIds.add(identifier);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[Latitude] Frozen river firefly scan unavailable: {}", t.toString());
        }
        return fireflyIds;
    }

    private static int removeFeature(BiomeModificationContext ctx, GenerationStep.Feature step, Identifier id) {
        RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, id);
        return ctx.getGenerationSettings().removeFeature(step, key) ? 1 : 0;
    }
}
