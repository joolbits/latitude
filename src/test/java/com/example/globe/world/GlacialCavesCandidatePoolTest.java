package com.example.globe.world;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-9 P2: pins the {@code expandSourceCandidatePool} flag-parameterized twin -- the seam that decides
 * which first-party biomes join the biome source's {@code possibleBiomes()} (hence the FeatureSorter
 * graph, hence whether the glacial dressing features ever decorate). Every combo is pinned through the
 * SAME single implementation the production overload folds the real static-final flags into (house
 * pattern; the {@code LatitudeV2Flags} testing note forbids mid-suite {@code setProperty}).
 *
 * <p>The byte-identity contracts pinned here: (a) both-off returns the EXACT base pool reference;
 * (b) barrens-on+glacial-off appends exactly the barrens -- bitwise the pre-P2 behavior; (c) missing
 * registry entries degrade to no-append, never throw; (d) idempotence -- an already-expanded pool
 * passes through by reference (safe to wrap twice).
 */
class GlacialCavesCandidatePoolTest {

    private static MappedRegistry<Biome> registry;
    private static Holder<Biome> snowyPlains;

    @BeforeAll
    static void bootstrapAndBuildRegistry() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        snowyPlains = register(registry, "minecraft:snowy_plains");
        register(registry, LatitudeBiomes.POLAR_BARRENS_ID);
        register(registry, LatitudeBiomes.GLACIAL_CAVES_ID);
        registry.bindAllTagsToEmpty();
        registry.freeze();
        LatitudeBiomes.rememberSourcePolicyBiomeRegistry(registry);
    }

    @AfterAll
    static void forgetRegistry() {
        // Leave no stashed registry behind for later test classes (the volatile is normally written by
        // the populate/dev/headless entry points; a stale test registry could mask their absence).
        LatitudeBiomes.rememberSourcePolicyBiomeRegistry(null);
    }

    private static Holder<Biome> register(MappedRegistry<Biome> reg, String id) {
        Biome biome = new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.0F)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        return reg.register(ResourceKey.create(Registries.BIOME, Identifier.parse(id)), biome,
                RegistrationInfo.BUILT_IN);
    }

    private static boolean containsId(Collection<Holder<Biome>> pool, String id) {
        return pool.stream().anyMatch(h -> h.unwrapKey()
                .map(k -> k.identifier().toString().equals(id)).orElse(false));
    }

    @Test
    void bothFlagsOffReturnsTheExactBasePoolReference() {
        List<Holder<Biome>> base = List.of(snowyPlains);
        assertSame(base, LatitudeBiomes.expandSourceCandidatePool(base, false, false),
                "both-off must be byte-identical (the exact reference, not a copy)");
    }

    @Test
    void barrensOnlyIsBitwiseThePreP2Behavior() {
        List<Holder<Biome>> base = List.of(snowyPlains);
        Collection<Holder<Biome>> pool = LatitudeBiomes.expandSourceCandidatePool(base, true, false);
        assertEquals(2, pool.size());
        assertTrue(containsId(pool, LatitudeBiomes.POLAR_BARRENS_ID));
        assertFalse(containsId(pool, LatitudeBiomes.GLACIAL_CAVES_ID),
                "glacial-off must never smuggle the cave biome into the sorter graph");
    }

    @Test
    void glacialFlagAppendsTheCaveBiome() {
        List<Holder<Biome>> base = List.of(snowyPlains);
        Collection<Holder<Biome>> both = LatitudeBiomes.expandSourceCandidatePool(base, true, true);
        assertEquals(3, both.size());
        assertTrue(containsId(both, LatitudeBiomes.POLAR_BARRENS_ID));
        assertTrue(containsId(both, LatitudeBiomes.GLACIAL_CAVES_ID));

        Collection<Holder<Biome>> glacialOnly = LatitudeBiomes.expandSourceCandidatePool(base, false, true);
        assertEquals(2, glacialOnly.size());
        assertTrue(containsId(glacialOnly, LatitudeBiomes.GLACIAL_CAVES_ID));
        assertFalse(containsId(glacialOnly, LatitudeBiomes.POLAR_BARRENS_ID));
    }

    @Test
    void expansionIsIdempotentByReference() {
        List<Holder<Biome>> base = List.of(snowyPlains);
        Collection<Holder<Biome>> expanded = LatitudeBiomes.expandSourceCandidatePool(base, true, true);
        assertSame(expanded, LatitudeBiomes.expandSourceCandidatePool(expanded, true, true),
                "wrapping twice must pass the already-expanded pool through by reference");
    }

    @Test
    void missingRegistryEntriesDegradeToNoAppend() {
        MappedRegistry<Biome> sparse = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Holder<Biome> plainsOnly = register(sparse, "minecraft:snowy_plains");
        Holder<Biome> barrensOnly = register(sparse, LatitudeBiomes.POLAR_BARRENS_ID);
        sparse.bindAllTagsToEmpty();
        sparse.freeze();
        LatitudeBiomes.rememberSourcePolicyBiomeRegistry(sparse);
        try {
            List<Holder<Biome>> base = List.of(plainsOnly);
            Collection<Holder<Biome>> pool = LatitudeBiomes.expandSourceCandidatePool(base, true, true);
            assertEquals(2, pool.size(), "barrens appends; the missing glacial_caves degrades silently");
            assertTrue(pool.contains(barrensOnly));
            assertFalse(containsId(pool, LatitudeBiomes.GLACIAL_CAVES_ID));
        } finally {
            LatitudeBiomes.rememberSourcePolicyBiomeRegistry(registry);
        }
    }
}
