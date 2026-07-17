package com.example.globe.world;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 B-8 gate-2 false-green regression test (2026-07-14): pins snowy_plains -> polar_barrens
 * THROUGH the exact entry point the headless atlas samplers call --
 * {@code LatitudeBiomes.pick(Registry, base, x, z, y, radius, sampler, "ATLAS_SAMPLER", null, null, null)},
 * verbatim the call shape of {@code BiomeSamplerTools.sampleBiomeId} (inventory sampler) and
 * {@code BiomePreviewExporter.ExportJob.processBudget} (map sampler); the LIVE game
 * ({@code ChunkGeneratorPopulateBiomesMixin}, ctx {@code "MIXIN"}) enters the same Registry twin.
 * The world is armed exactly the way {@code BiomePreviewHeadlessRunner.runExportAndStop} arms it
 * ({@code setWorldSeed} + {@code setActiveRadiusBlocks}), with the very seed/radius of the diagnosed
 * runs (20260714 / 10000).
 *
 * <p><b>Flag handling:</b> {@code latitude.polarBarrens.enabled} is static-final, read once at
 * class-init -- it CANNOT be flipped inside the suite JVM (the {@link com.example.globe.core.LatitudeV2Flags}
 * testing note; a mid-suite {@code setProperty} would silently test the wrong path, and forcing it via
 * the gradle test JVM would break the default-off flag tests). So this class pins BOTH halves at the
 * same coordinates: (a) the DEFAULT-off suite JVM traverses the override seam through the real
 * {@code pick()} entry and never emits barrens (traversal + byte-identity, via the unconditional
 * traversal counters), and (b) the flag-parameterized seam -- the SAME single implementation the
 * production tail executes, with {@code enabled=true} exactly as a {@code -D}-armed atlas JVM folds
 * it -- rewrites that same column to barrens. Flag-on through a real forked JVM remains the atlas
 * proof's job (per project law), provable from the log alone via {@code -Dlatitude.debugBarrens}.
 */
class PolarBarrensAtlasEntryPointTest {

    private static final long ATLAS_SEED = 20260714L;   // the diagnosed runs' pinned seed
    private static final int RADIUS = 10000;            // --size regular
    private static final int DEEP_CAP_Z = 9889;         // |lat| = 89.00 deg -> barrens fraction 1.0
    private static final int FRAY_Z = 9222;             // |lat| = 83.00 deg -> barrens fraction ~0.5 (S13 82->84 band)

    private static Registry<Biome> registry;
    private static Holder<Biome> snowyPlains;
    private static Holder<Biome> iceSpikes;

    @BeforeAll
    static void bootstrapMinecraftAndArmWorld() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        MappedRegistry<Biome> reg = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        snowyPlains = register(reg, "minecraft:snowy_plains");
        register(reg, "minecraft:snowy_taiga");
        register(reg, "minecraft:grove");
        iceSpikes = register(reg, "minecraft:ice_spikes");
        register(reg, "minecraft:plains");
        register(reg, "minecraft:river");
        register(reg, "minecraft:frozen_river");
        register(reg, "minecraft:ocean");
        register(reg, LatitudeBiomes.POLAR_BARRENS_ID);
        reg.bindAllTagsToEmpty(); // Reference.is(TagKey) throws until tags are bound; empty == "no tags"
        reg.freeze();
        registry = reg;

        // Exactly how BiomePreviewHeadlessRunner.runExportAndStop arms the world before sampling.
        LatitudeBiomes.setWorldSeed(ATLAS_SEED);
        LatitudeBiomes.setActiveRadiusBlocks(RADIUS);
    }

    @AfterAll
    static void disarmWorld() {
        // Leave no globe-armed statics behind for later test classes.
        LatitudeBiomes.setActiveRadiusBlocks(0);
        LatitudeBiomes.setWorldSeed(0L);
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
        return reg.register(
                ResourceKey.create(Registries.BIOME, Identifier.parse(id)),
                biome,
                RegistrationInfo.BUILT_IN);
    }

    private static String id(Holder<Biome> holder) {
        return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("<direct>");
    }

    /** The verbatim atlas-sampler entry: BiomeSamplerTools.sampleBiomeId / ExportJob default mode. */
    private static Holder<Biome> atlasSamplerPick(int blockX, int blockZ) {
        Holder<Biome> picked = LatitudeBiomes.pick(
                registry, snowyPlains, blockX, blockZ, 64, RADIUS, null, "ATLAS_SAMPLER", null, null, null);
        return picked != null ? picked : snowyPlains;
    }

    /** Find a deep-cap column the full flag-off pipeline resolves to snowy_plains (not ice_spikes etc.). */
    private static int findSnowyPlainsColumn(int blockZ) {
        for (int x = -4096; x <= 4096; x += 512) {
            if ("minecraft:snowy_plains".equals(id(atlasSamplerPick(x, blockZ)))) {
                return x;
            }
        }
        throw new AssertionError("no snowy_plains column found at z=" + blockZ
                + " -- minimal-registry harness drifted from the polar pipeline");
    }

    @Test
    void atlasEntryPointTraversesOverrideAndFlagOffNeverEmitsBarrens() {
        long callsBefore = LatitudeBiomes.polarBarrensOverrideCalls();
        long rewritesBefore = LatitudeBiomes.polarBarrensOverrideRewrites();

        // DEFAULT-INDEPENDENT (branch-local flight staging flips the production default ON, B-6/B-7
        // precedent): whichever way the flag points, the entry point must traverse the seam AND honor
        // the matching contract — flag-off: never barrens + zero rewrites (byte-identity); flag-on:
        // the deep cap emits barrens through the REAL pick() (the live/atlas truth TEST flights ride).
        boolean enabled = com.example.globe.core.LatitudeV2Flags.POLAR_BARRENS_ENABLED;
        boolean sawBarrens = false;
        for (int x = -2048; x <= 2048; x += 1024) {
            String north = id(atlasSamplerPick(x, DEEP_CAP_Z));
            String south = id(atlasSamplerPick(x, -DEEP_CAP_Z));
            if (!enabled) {
                assertNotEquals(LatitudeBiomes.POLAR_BARRENS_ID, north,
                        "flag-off must never emit barrens through the atlas entry point");
                assertNotEquals(LatitudeBiomes.POLAR_BARRENS_ID, south,
                        "southern hemisphere flag-off must never emit barrens either");
            } else {
                sawBarrens |= LatitudeBiomes.POLAR_BARRENS_ID.equals(north)
                        || LatitudeBiomes.POLAR_BARRENS_ID.equals(south);
            }
        }

        assertTrue(LatitudeBiomes.polarBarrensOverrideCalls() > callsBefore,
                "the real pick() entry point must traverse the barrens override seam (gate-2 false-green "
                        + "class: a pipeline that dodges the seam would silently drop the feature)");
        if (!enabled) {
            assertEquals(rewritesBefore, LatitudeBiomes.polarBarrensOverrideRewrites(),
                    "flag-off traversals must perform zero rewrites (byte-identity through the entry point)");
        } else {
            assertTrue(sawBarrens,
                    "flight-staging flag-on: the deep cap must emit barrens through the real pick()");
            assertTrue(LatitudeBiomes.polarBarrensOverrideRewrites() > rewritesBefore,
                    "flight-staging flag-on: the rewrite counter must record the deep-cap rewrites");
        }
    }

    @Test
    void enabledSeamRewritesDeepCapSnowyPlainsColumnFromTheEntryPointPipeline() {
        boolean enabled = com.example.globe.core.LatitudeV2Flags.POLAR_BARRENS_ENABLED;
        if (enabled) {
            // Flight staging (production default ON): the pipeline already rewrites at the tail, so a
            // "snowy_plains column" arrives AS barrens — and since ONLY snowy_plains ever rewrites
            // (pinned below and in PolarBarrensBandTest), a deep-cap barrens column IS the proof the
            // pipeline produced snowy_plains beneath and the seam rewrote it. The flag-off identity
            // half stays pinned directly through the seam.
            long rewritesBefore = LatitudeBiomes.polarBarrensOverrideRewrites();
            int x = findDeepCapColumn(DEEP_CAP_Z, LatitudeBiomes.POLAR_BARRENS_ID);
            assertTrue(LatitudeBiomes.polarBarrensOverrideRewrites() > rewritesBefore,
                    "the rewrite counter must record the deep-cap rewrites (log-alone proof surface)");
            int landBandIndex = LatitudeBiomes.authoritativeLandBandIndex(x, DEEP_CAP_Z, RADIUS);
            double latDeg = Math.abs((double) DEEP_CAP_Z) * 90.0 / RADIUS;
            assertSame(snowyPlains, LatitudeBiomes.applyPolarBarrensOverride(
                            registry, snowyPlains, landBandIndex, latDeg, x, DEEP_CAP_Z, false),
                    "a disabled seam must remain the identity on the same column (byte-identity half)");
            return;
        }

        int x = findSnowyPlainsColumn(DEEP_CAP_Z);
        Holder<Biome> flagOffOut = atlasSamplerPick(x, DEEP_CAP_Z);
        assertEquals("minecraft:snowy_plains", id(flagOffOut));

        // The same column, through the same single seam the production tail executes, with the flag
        // value an armed atlas JVM would fold in. landBandIndex from the exporter's own authority helper.
        int landBandIndex = LatitudeBiomes.authoritativeLandBandIndex(x, DEEP_CAP_Z, RADIUS);
        double latDeg = Math.abs((double) DEEP_CAP_Z) * 90.0 / RADIUS; // latitudeDegreesFromRadius formula
        Holder<Biome> rewritten = LatitudeBiomes.applyPolarBarrensOverride(
                registry, flagOffOut, landBandIndex, latDeg, x, DEEP_CAP_Z, true);
        assertEquals(LatitudeBiomes.POLAR_BARRENS_ID, id(rewritten),
                "at 89 deg (fraction 1.0) an enabled seam must rewrite the pipeline's snowy_plains to barrens");

        long rewrites = LatitudeBiomes.polarBarrensOverrideRewrites();
        assertTrue(rewrites > 0, "the rewrite counter must record the rewrite (log-alone proof surface)");
    }

    /** Find a deep-cap column the full pipeline resolves to the given id (flight-staging variant). */
    private static int findDeepCapColumn(int blockZ, String targetId) {
        for (int x = -4096; x <= 4096; x += 512) {
            if (targetId.equals(id(atlasSamplerPick(x, blockZ)))) {
                return x;
            }
        }
        throw new AssertionError("no " + targetId + " column found at z=" + blockZ
                + " -- minimal-registry harness drifted from the polar pipeline");
    }

    @Test
    void enabledSeamFraysAtEightyThreeDegrees() {
        double latDeg = Math.abs((double) FRAY_Z) * 90.0 / RADIUS;
        int barrensX = Integer.MIN_VALUE;
        int keepX = Integer.MIN_VALUE;
        for (int x = -8192; x <= 8192 && (barrensX == Integer.MIN_VALUE || keepX == Integer.MIN_VALUE); x += 64) {
            boolean isBarrens = com.example.globe.core.PolarBarrensBand.isBarrens(
                    latDeg, LatitudeBiomes.polarBarrensFrayNoise(x, FRAY_Z));
            if (isBarrens && barrensX == Integer.MIN_VALUE) {
                barrensX = x;
            } else if (!isBarrens && keepX == Integer.MIN_VALUE) {
                keepX = x;
            }
        }
        assertTrue(barrensX != Integer.MIN_VALUE && keepX != Integer.MIN_VALUE,
                "83 deg (~50% fray) must contain both barrens and surviving snowy_plains columns");

        int landBand = LatitudeBiomes.authoritativeLandBandIndex(barrensX, FRAY_Z, RADIUS);
        assertEquals(LatitudeBiomes.POLAR_BARRENS_ID, id(LatitudeBiomes.applyPolarBarrensOverride(
                        registry, snowyPlains, landBand, latDeg, barrensX, FRAY_Z, true)),
                "fray-true column must rewrite");
        assertSame(snowyPlains, LatitudeBiomes.applyPolarBarrensOverride(
                        registry, snowyPlains, LatitudeBiomes.authoritativeLandBandIndex(keepX, FRAY_Z, RADIUS),
                        latDeg, keepX, FRAY_Z, true),
                "fray-false column must keep its snowy_plains (the owner-approved surviving patches)");
    }

    @Test
    void enabledSeamNeverTouchesNonSnowyPlainsAndDegradesWithoutRegistryEntry() {
        int landBand = LatitudeBiomes.authoritativeLandBandIndex(0, DEEP_CAP_Z, RADIUS);
        // ice_spikes accents survive by construction (owner decision #3 mechanism).
        assertSame(iceSpikes, LatitudeBiomes.applyPolarBarrensOverride(
                registry, iceSpikes, landBand, 89.0, 0, DEEP_CAP_Z, true));
        // Collection twin: pool WITH barrens rewrites; pool WITHOUT degrades to the unchanged pick.
        Holder<Biome> barrensHolder = registry.get(Identifier.parse(LatitudeBiomes.POLAR_BARRENS_ID)).orElseThrow();
        assertEquals(LatitudeBiomes.POLAR_BARRENS_ID, id(LatitudeBiomes.applyPolarBarrensOverride(
                List.of(snowyPlains, iceSpikes, barrensHolder), snowyPlains, landBand, 89.0, 0, DEEP_CAP_Z, true)));
        assertSame(snowyPlains, LatitudeBiomes.applyPolarBarrensOverride(
                List.of(snowyPlains, iceSpikes), snowyPlains, landBand, 89.0, 0, DEEP_CAP_Z, true),
                "a pool lacking the barrens must leave the pick unchanged, never throw");
    }
}
