package com.example.globe.world;

import com.example.globe.mixin.ChunkGeneratorGenerateFeaturesBiomeSetMixin;
import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the flag-parameterized production selector that supplies custom biomes to the decoration
 * retain/index policy. The test registry binds every tag empty, so any retained Polar Barrens or
 * Glacial Caves holder must come through the selector's explicit post-biome-source policy.
 */
class ChunkGeneratorGenerateFeaturesBiomeSetMixinContractTest {

    private static MappedRegistry<Biome> registry;
    private static Method policySelector;

    @BeforeAll
    static void bootstrapRegistryAndSelector() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        register(registry, "minecraft:snowy_plains");
        register(registry, LatitudeBiomes.POLAR_BARRENS_ID);
        register(registry, LatitudeBiomes.GLACIAL_CAVES_ID);
        registry.bindAllTagsToEmpty();
        registry.freeze();

        policySelector = ChunkGeneratorGenerateFeaturesBiomeSetMixin.class.getDeclaredMethod(
                "latitude$taggedCustomPolicyBiomes", Registry.class, boolean.class, boolean.class);
        policySelector.setAccessible(true);
    }

    @Test
    void glacialFlagRetainsExactlyOneUntaggedGlacialCavesHolder() throws Exception {
        List<Holder<Biome>> policy = policyBiomes(false, true);

        assertEquals(1L, count(policy, LatitudeBiomes.GLACIAL_CAVES_ID));
        assertEquals(0L, count(policy, LatitudeBiomes.POLAR_BARRENS_ID));
        assertEquals(1, policy.size(), "empty tags plus glacial-on must produce one de-duplicated entry");
    }

    @Test
    void glacialFlagOffDoesNotAddGlacialCaves() throws Exception {
        List<Holder<Biome>> policy = policyBiomes(false, false);

        assertEquals(0L, count(policy, LatitudeBiomes.GLACIAL_CAVES_ID));
        assertEquals(0, policy.size());
    }

    @Test
    void existingPolarBarrensPolicyRemainsPinned() throws Exception {
        List<Holder<Biome>> polarOnly = policyBiomes(true, false);
        assertEquals(1L, count(polarOnly, LatitudeBiomes.POLAR_BARRENS_ID));
        assertEquals(0L, count(polarOnly, LatitudeBiomes.GLACIAL_CAVES_ID));
        assertEquals(1, polarOnly.size());

        List<Holder<Biome>> both = policyBiomes(true, true);
        assertEquals(1L, count(both, LatitudeBiomes.POLAR_BARRENS_ID));
        assertEquals(1L, count(both, LatitudeBiomes.GLACIAL_CAVES_ID));
        assertEquals(2, both.size());
    }

    @SuppressWarnings("unchecked")
    private static List<Holder<Biome>> policyBiomes(boolean polarEnabled, boolean glacialEnabled)
            throws Exception {
        return (List<Holder<Biome>>) policySelector.invoke(null, registry, polarEnabled, glacialEnabled);
    }

    private static long count(List<Holder<Biome>> policy, String id) {
        return policy.stream()
                .filter(holder -> holder.unwrapKey()
                        .map(key -> key.identifier().toString().equals(id))
                        .orElse(false))
                .count();
    }

    private static Holder<Biome> register(MappedRegistry<Biome> biomeRegistry, String id) {
        Biome biome = new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.0F)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        return biomeRegistry.register(
                ResourceKey.create(Registries.BIOME, Identifier.parse(id)),
                biome,
                RegistrationInfo.BUILT_IN
        );
    }
}
