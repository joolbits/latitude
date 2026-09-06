package com.example.globe.world;

import com.example.globe.world.LatitudeWorldState.WorldgenPolicyVersion;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic provider-ticket, descriptor admission, and fresh-world coverage checks. */
final class BiomeProviderSelectionPolicyTest {
    // Fixed before sampling.  Points are 4,096 blocks apart, well beyond both coherent fields.
    private static final long[] AUDIT_SEEDS = {
            3L, 17L, 41L, 59L, 71L, 101L, 131L, 173L,
            211L, 241L, 281L, 313L, 353L, 401L, 431L, 461L
    };
    private static final int[] NONADJACENT_POINTS = {
            -32_768, -28_672, -24_576, -20_480, -16_384, -12_288, -8_192, -4_096,
            0, 4_096, 8_192, 12_288, 16_384, 20_480, 24_576, 28_672, 32_768
    };

    private BiomeProviderSelectionPolicyTest() {}

    static void run() throws Exception {
        // Minecraft 1.21.11 static-initialises BuiltInRegistries from BiomeSource's own clinit, so any
        // test that touches LatitudeBiomeSource live needs the bootstrap to have already run. The
        // 26.2 twin of this suite gets away without it because its BiomeSource does not reach the
        // registries that early. Bootstrap is idempotent, so the later call in buildTestBiomeRegistry
        // becomes a no-op.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        descriptorAdmissionIsClosedAndCanonical();
        wetlandRoutesMatchRealBiomeClimate();
        climateLowlandDescriptorsRemainRouteBounded();
        everySupportedStackGetsEqualRouteTickets();
        selectionIsWorldSeededAndCoherent();
        routeSelectionCannotFallBackToAnUnclassifiedTag();
        providerTicketHotPathCachesAndInvalidates();
        providerProfileCompatibilityIsBirthLocked();
        polarIceSpikeAccentStaysAMinorityInEveryPoolSize();
        polarTaigaTransitionPreservesShouldersAndTreeLine();
        polarExtremeCapCatchesNameAlikeModdedBiomesConsistently();
        windsweptFamilyIsSubpolarMountainOnly();
        warmBeltIdentityIsProvinceOrdered();
        cliffTreeLandAndOceanAreActuallyReachable();
        riverAndBeachAdmissionIsTagDrivenAndVanillaSafe();
        everyLedgerLandRouteSurvivesTheBandPoolGate();
        wetlandsAreAcceptedButNeverSubstitutedIn();
        cohesionGatePoolAgreesWithTheLedger();
        vanillaCoverageIsCompleteAndWorldSizeSafe();
        erodedBadlandsIsGuaranteedOnTheLowlandAridRoute();
        customJungleAdmissionIsProvinceBounded();
        reservedLandLocateFallsBackToExactAnchor();
        finalVanillaCoverageHonorsHumidityAndLateAuthorities();
        surfaceWaterCoverageIsCompleteAndWorldSizeSafe();
        sizeAwareVanillaRepresentationIsClosedAndBirthLocked();
        caveCoverageIsClosedAndWorldSizeSafe();
        vanillaCoverageIsV2OnlyAndClearsWithContext();
    }

    static void runHumidCoverageProof() {
        CoverageFixture regular = activateCoverageFixture(10_000, Set.of());
        try {
            assertEquals(VanillaBiomeCoveragePlan.requiredRoutes(), regular.representation().landTargets(),
                    "Regular V5 coverage retains the exact vanilla land representation");
            assertHumidCoverageFinalOutputs(regular);
            assertJungleProvinceRejectionAndRestoration(regular);
            assertHumidCoverageEvidenceIsRejected(regular);
            assertExistingWetlandAndPaleGardenProtection(regular);
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }

        CoverageFixture compact = activateCoverageFixture(
                7_500, Set.of("biomesoplenty", "terralith"));
        try {
            assertTrue(compact.representation().worldSize().compact(),
                    "Small V5 coverage uses the compact representation contract");
            for (Map.Entry<String, VanillaBiomeRepresentationProfile.Omission> omission
                    : compact.representation().omittedExactIds().entrySet()) {
                assertTrue(VanillaBiomeRepresentationProfile.areApprovedEquivalents(
                                omission.getKey(), omission.getValue()),
                        "every compact V5 representative is descriptor-approved: " + omission.getKey());
            }
            assertHumidCoverageFinalOutputs(compact);
            assertJungleProvinceRejectionAndRestoration(compact);
            assertHumidCoverageEvidenceIsRejected(compact);
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    /**
     * Exercises ordinary (non-reservation) subtropical output through both public picker owners.
     * The flat controls establish the existing province-specific lowland identities; the rugged
     * case then proves the final V5 authority selects a province-compatible upland identity.
     */
    static void runWarmUplandProof() {
        int radius = 10_000;
        long worldSeed = 41L;
        TestBiomeRegistry registry = buildTestBiomeRegistry(registryFor(Set.of()));
        Holder<Biome> desert = holder(registry.registry(), "minecraft:desert");
        Holder<Biome> erodedBadlands = holder(registry.registry(), "minecraft:eroded_badlands");
        Climate.Sampler flatTerrain = fixedTerrainSampler(0.30, 0.20, 0.0);
        Climate.Sampler ruggedTerrain = fixedTerrainSampler(0.30, -0.60, 0.70);

        LatitudeBiomes.activateWorldgenContext(
                radius, worldSeed, WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE,
                null, null, flatTerrain, null, 63);
        try {
            int[] sample = findLegacySubtropicalWetDesertSample(
                    registry, desert, radius, flatTerrain);
            int blockX = sample[0];
            int blockZ = sample[1];

            long wetSeed = findProvinceSeed(
                    radius, blockX, blockZ, ProvinceAuthority.Province.WARM_WET);
            ProvinceAuthority original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(wetSeed, radius));
            try {
                assertBothBiomeId(
                        "minecraft:desert",
                        pickRegistry(registry, desert, blockX, blockZ, radius, flatTerrain),
                        pickCollection(registry, desert, blockX, blockZ, radius, flatTerrain),
                        "V4 preserves its legacy WARM_WET subtropical desert result");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            LatitudeBiomes.activateWorldgenContext(
                    radius, worldSeed, v5FinalAdmissionPolicy(), null, null, flatTerrain, null, 63);
            long mediumSeed = findProvinceSeed(
                    radius, blockX, blockZ, ProvinceAuthority.Province.WARM_MEDIUM);
            long drySeed = findProvinceSeed(
                    radius, blockX, blockZ, ProvinceAuthority.Province.WARM_DRY);
            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(drySeed, radius));
            try {
                assertBothFamily(
                        BiomeDescriptorLedger.Family.ARID,
                        pickRegistry(registry, desert, blockX, blockZ, radius, flatTerrain),
                        pickCollection(registry, desert, blockX, blockZ, radius, flatTerrain),
                        "physical WARM_DRY subtropical lowland keeps its ordinary arid-family "
                                + "identity [x=" + blockX + " z=" + blockZ + " lat="
                                + String.format(java.util.Locale.ROOT, "%.1f",
                                        Math.abs(blockZ) * 90.0 / radius)
                                + "deg, settled belt starts at " + ARID_SETTLED_BELT_MIN_DEG
                                + "deg] -- a savanna result at or above the settled edge is a real "
                                + "defect, because both arid latitude gates return early there; "
                                + "below it, savanna is the lawful phase-in answer and the sample "
                                + "itself is wrong");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(mediumSeed, radius));
            try {
                assertBothFamily(
                        warmMediumExpectedFamily(blockX, blockZ, radius),
                        pickRegistry(registry, desert, blockX, blockZ, radius, flatTerrain),
                        pickCollection(registry, desert, blockX, blockZ, radius, flatTerrain),
                        "physical WARM_MEDIUM subtropical lowland keeps its ordinary warm-medium "
                                + "identity -- savanna-family inside a savanna country or the dry "
                                + "fringe, the forest staple outside both");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            // The coarse WARM_WET label is not the whole desert law (maintainer ruling,
            // 2026-08-16): an active arid hotspot — the designed desert-oasis mechanism — may
            // preserve desert despite the humid province, exactly as on the 26.2 line. The
            // focused legacy sample is NOT a hotspot, so it pins the ordinary rejection; the
            // separately-found hotspot sample pins the exception.
            assertFalse(LatitudeBiomes.debugAridHotspot(blockX, blockZ),
                    "the focused legacy sample is an ordinary (non-hotspot) column");
            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(wetSeed, radius));
            try {
                assertBothWarmWetLowlandCompatible(
                        pickRegistry(registry, desert, blockX, blockZ, radius, flatTerrain),
                        pickCollection(registry, desert, blockX, blockZ, radius, flatTerrain),
                        "V5 WARM_WET subtropical lowland rejects ordinary (non-hotspot) desert");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            long[] hotspot = findAridHotspotSubtropicalSample(radius, flatTerrain);
            int hotspotX = (int) hotspot[1];
            int hotspotZ = (int) hotspot[2];
            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(hotspot[3], radius));
            try {
                assertBothBiomeId(
                        "minecraft:desert",
                        pickRegistry(registry, desert, hotspotX, hotspotZ, radius, flatTerrain),
                        pickCollection(registry, desert, hotspotX, hotspotZ, radius, flatTerrain),
                        "V5 WARM_WET subtropical lowland preserves desert at an active arid hotspot");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            LatitudeBiomes.activateWorldgenContext(
                    radius, worldSeed, v5FinalAdmissionPolicy(), null, null, ruggedTerrain, null, 63);
            assertTrue(
                    TerrainBiomeCohesionPolicy.shouldUseWarmUplandFamily(true, 63, 6, 63),
                    "the focused rugged sample is a physical warm upland");

            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(drySeed, radius));
            try {
                assertBothBiomeId(
                        "minecraft:eroded_badlands",
                        pickRegistry(registry, desert, blockX, blockZ, radius, ruggedTerrain),
                        pickCollection(registry, desert, blockX, blockZ, radius, ruggedTerrain),
                        "V5 WARM_DRY physical upland rewrites ordinary desert to the arid-upland representative");
                assertBothBiomeId(
                        "minecraft:eroded_badlands",
                        pickRegistry(registry, erodedBadlands, blockX, blockZ, radius, ruggedTerrain),
                        pickCollection(registry, erodedBadlands, blockX, blockZ, radius, ruggedTerrain),
                        "V5 retains an existing descriptor-owned arid-upland identity");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            assertProvinceCompatibleWarmUpland(
                    registry, desert, radius, ruggedTerrain, blockX, blockZ,
                    ProvinceAuthority.Province.WARM_MEDIUM,
                    "V5 WARM_MEDIUM physical upland replaces desert with a dedicated warm-upland savanna");

            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(wetSeed, radius));
            try {
                assertBothBiomeId(
                        "minecraft:plains",
                        pickRegistry(registry, desert, blockX, blockZ, radius, ruggedTerrain),
                        pickCollection(registry, desert, blockX, blockZ, radius, ruggedTerrain),
                        "V5 WARM_WET physical upland grass-caps ordinary desert without a forest canopy");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            LatitudeBiomes.activateWorldgenContext(
                    radius, worldSeed, WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE,
                    null, null, ruggedTerrain, null, 63);
            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(drySeed, radius));
            try {
                assertBothFamily(
                        BiomeDescriptorLedger.Family.ARID,
                        pickRegistry(registry, desert, blockX, blockZ, radius, ruggedTerrain),
                        pickCollection(registry, desert, blockX, blockZ, radius, ruggedTerrain),
                        "V4 keeps its ordinary subtropical arid-family behavior unchanged");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    private static void assertProvinceCompatibleWarmUpland(
            TestBiomeRegistry registry,
            Holder<Biome> desert,
            int radius,
            Climate.Sampler sampler,
            int blockX,
            int blockZ,
            ProvinceAuthority.Province province,
            String message) {
        long provinceSeed = findProvinceSeed(radius, blockX, blockZ, province);
        ProvinceAuthority original = LatitudeBiomes.swapProvinceAuthorityForTest(
                new ProvinceAuthority(provinceSeed, radius));
        try {
            Holder<Biome> registryResult = pickRegistry(
                    registry, desert, blockX, blockZ, radius, sampler);
            Holder<Biome> collectionResult = pickCollection(
                    registry, desert, blockX, blockZ, radius, sampler);
            BiomeDescriptorLedger.Descriptor registryDescriptor = BiomeDescriptorLedger.descriptor(
                    LatitudeBiomes.biomeIdPublic(registryResult));
            BiomeDescriptorLedger.Descriptor collectionDescriptor = BiomeDescriptorLedger.descriptor(
                    LatitudeBiomes.biomeIdPublic(collectionResult));
            boolean registryCompatible = isWarmUplandFamilyCompatible(
                    registryResult, registryDescriptor, province, blockX, blockZ, radius);
            boolean collectionCompatible = isWarmUplandFamilyCompatible(
                    collectionResult, collectionDescriptor, province, blockX, blockZ, radius);
            assertTrue(registryCompatible && collectionCompatible,
                    message + ": registry=" + LatitudeBiomes.biomeIdPublic(registryResult)
                            + " collection=" + LatitudeBiomes.biomeIdPublic(collectionResult));
        } finally {
            LatitudeBiomes.restoreProvinceAuthorityForTest(original);
        }
    }

    private static boolean isWarmUplandFamilyCompatible(
            Holder<Biome> biome,
            BiomeDescriptorLedger.Descriptor descriptor,
            ProvinceAuthority.Province province,
            int blockX,
            int blockZ,
            int radius) {
        if (descriptor == null || descriptor.family() == BiomeDescriptorLedger.Family.ARID) {
            return false;
        }
        if (province == ProvinceAuthority.Province.WARM_MEDIUM) {
            // Outside a savanna country and outside the dry fringe the warm-medium staple is
            // forest, so the savanna-plateau representative is only the RIGHT answer where savanna
            // owns the column (maintainer approval, 2026-08-18). windswept_savanna stays acceptable
            // either way: it is exempt from the country rule in both directions, being the mountain
            // identity rather than the flat staple the country governs.
            String id = LatitudeBiomes.biomeIdPublic(biome);
            if ("minecraft:windswept_savanna".equals(id)) {
                return true;
            }
            if (warmMediumExpectedFamily(blockX, blockZ, radius)
                    == BiomeDescriptorLedger.Family.FOREST) {
                return descriptor.family() == BiomeDescriptorLedger.Family.FOREST;
            }
            return "minecraft:savanna_plateau".equals(id);
        }
        return descriptor.family() == BiomeDescriptorLedger.Family.FOREST
                || descriptor.family() == BiomeDescriptorLedger.Family.JUNGLE;
    }

    /**
     * The explain diagnostic must disclose the arid-hotspot override next to the coarse province
     * (diagnostic follow-up to maintainer ruling, 2026-08-16). Before this, explain printed only
     * {@code province=WARM_WET} at a hotspot-preserved desert, making a sanctioned desert oasis
     * indistinguishable from a broken humidity label — the exact misreading that started the V5
     * anomaly triage. Both surfaces must tell the story: the drivers block carries the raw flag,
     * and the plain-language summary explains it when the override is active.
     */
    static void explainDisclosesTheAridHotspotOverride() throws Exception {
        int radius = 10_000;
        Climate.Sampler flatTerrain = fixedTerrainSampler(0.30, 0.20, 0.0);
        long[] hotspot = findAridHotspotSubtropicalSample(radius, flatTerrain);
        int hotspotX = (int) hotspot[1];
        int hotspotZ = (int) hotspot[2];
        try {
            ProvinceAuthority original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(hotspot[3], radius));
            try {
                LatitudeBiomes.BiomeDiagnostics active = LatitudeBiomes.explainBiomeAt(
                        "minecraft:desert", hotspotX, hotspotZ, 64, radius, flatTerrain,
                        null, null, null, false, null, null, false, Integer.MIN_VALUE);
                assertTrue(active.driversBlock().contains("province=WARM_WET"),
                        "the coarse province stays visible");
                assertTrue(active.driversBlock().contains("aridHotspot=true"),
                        "the drivers block discloses the active arid-hotspot override");
                assertTrue(active.summaryLine().contains("arid hotspot"),
                        "the plain-language summary explains the override when it is active");

                // latFrac > 0.58 is outside the hotspot belt by construction: the flag must read
                // false there, and the summary must not mention an override that is not active.
                int quietZ = 6_200;
                LatitudeBiomes.BiomeDiagnostics quiet = LatitudeBiomes.explainBiomeAt(
                        "minecraft:plains", hotspotX, quietZ, 64, radius, flatTerrain,
                        null, null, null, false, null, null, false, Integer.MIN_VALUE);
                assertTrue(quiet.driversBlock().contains("aridHotspot=false"),
                        "the drivers block reports an inactive hotspot honestly");
                assertFalse(quiet.summaryLine().contains("arid hotspot"),
                        "the summary stays quiet when no override is active");
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    /**
     * A subtropical lowland column where the arid-hotspot noise is active, so the desert-oasis
     * exception applies. Hotspot blobs are world-size-scale noise cells (lowest 18% of a
     * ~radius*0.6 field, latFrac 0.18..0.58), so one fixed world seed may genuinely contain no
     * hit inside the subtropical band; the search therefore walks a deterministic candidate
     * world-seed list and returns with the winning V5 context ACTIVE. It also requires a
     * WARM_WET-classifying authority seed so the exception is exercised against the exact humid
     * label it is meant to outrank.
     *
     * <p>The scan starts poleward of DESERT_LAT_RAMP_HIGH_DEG (27deg -> z>=3_100 at this radius):
     * below that line the 1.4 equator-thinning law demotes desert by latitude alone, which is a
     * separate, older ruling this proof must not disturb. Above it, only the WARM_WET authority
     * can reject desert — exactly the law under test.
     *
     * @return {worldSeed, blockX, blockZ, wetAuthoritySeed}
     */
    private static long[] findAridHotspotSubtropicalSample(int radius, Climate.Sampler sampler) {
        for (long worldSeed = 41L; worldSeed <= 104L; worldSeed++) {
            LatitudeBiomes.activateWorldgenContext(
                    radius, worldSeed, v5FinalAdmissionPolicy(), null, null, sampler, null, 63);
            for (int blockZ = 3_100; blockZ <= 3_800; blockZ += 100) {
                for (int blockX = -9_600; blockX <= 9_600; blockX += 400) {
                    if (LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, radius) != 1
                            || !LatitudeBiomes.debugAridHotspot(blockX, blockZ)) {
                        continue;
                    }
                    long wetSeed = findProvinceSeedOrZero(
                            radius, blockX, blockZ, ProvinceAuthority.Province.WARM_WET);
                    if (wetSeed != 0L) {
                        return new long[] {worldSeed, blockX, blockZ, wetSeed};
                    }
                }
            }
            LatitudeBiomes.clearWorldgenContext();
        }
        throw new AssertionError(
                "no active arid hotspot with a WARM_WET-classifying seed across the candidate "
                        + "world seeds");
    }

    /**
     * The equatorward edge of the SETTLED subtropical arid belt, in degrees.
     *
     * <p>Mirrors {@code BADLANDS_LAT_RAMP_HIGH_DEG} and {@code DESERT_LAT_RAMP_HIGH_DEG} in
     * LatitudeBiomes, which share the {@code latitude.aridRampHigh} knob and both default to 27.0.
     * Below this edge, down to 23.5, is the arid PHASE-IN: {@code demoteEquatorialBadlands} and
     * {@code demoteEquatorialDesert} deliberately hand a coherent, latitude-weighted fraction of
     * arid columns to savanna there, so that the desert belt fades in rather than starting at a
     * hard line. Above it both gates return early and every arid pick is kept unconditionally.
     *
     * <p>Any proof asserting "a dry province keeps its arid identity" must sample at or above this
     * edge. Below it, savanna IS the lawful answer for a coherent share of columns, and a proof that
     * samples there is asserting a law the generator does not have.
     */
    private static final double ARID_SETTLED_BELT_MIN_DEG = 27.0;

    private static int[] findLegacySubtropicalWetDesertSample(
            TestBiomeRegistry registry,
            Holder<Biome> desert,
            int radius,
            Climate.Sampler sampler) {
        int[] xs = {-4_096, -3_072, -2_048, -1_024, 0, 1_024, 2_048, 3_072, 4_096};
        // 3_000 == 27.0deg at radius 10_000, the settled-belt edge. z=2_800 (25.2deg) was removed
        // from this list on 2026-08-19: it sits inside the arid phase-in ramp, where the latitude
        // gates lawfully demote a share of arid picks to savanna, so the WARM_DRY assertion below
        // held there only by luck. The guard inside the loop is what actually enforces the rule --
        // the list is just the search order.
        int[] zs = {3_000, 3_200, 3_400};
        for (int blockZ : zs) {
            if (Math.abs(blockZ) * 90.0 / radius < ARID_SETTLED_BELT_MIN_DEG) {
                continue; // arid phase-in: savanna is lawful here, so it cannot anchor an arid proof
            }
            for (int blockX : xs) {
                long wetSeed = findProvinceSeedOrZero(
                        radius, blockX, blockZ, ProvinceAuthority.Province.WARM_WET);
                if (wetSeed == 0L) {
                    continue;
                }
                ProvinceAuthority original = LatitudeBiomes.swapProvinceAuthorityForTest(
                        new ProvinceAuthority(wetSeed, radius));
                try {
                    Holder<Biome> registryResult = pickRegistry(
                            registry, desert, blockX, blockZ, radius, sampler);
                    Holder<Biome> collectionResult = pickCollection(
                            registry, desert, blockX, blockZ, radius, sampler);
                    if (LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, radius)
                                    == 1
                            && "minecraft:desert".equals(
                                    LatitudeBiomes.biomeIdPublic(registryResult))
                            && "minecraft:desert".equals(
                                    LatitudeBiomes.biomeIdPublic(collectionResult))) {
                        return new int[] {blockX, blockZ};
                    }
                } finally {
                    LatitudeBiomes.restoreProvinceAuthorityForTest(original);
                }
            }
        }
        throw new AssertionError("no legacy WARM_WET subtropical desert sample for the focused fixture");
    }

    private static long findProvinceSeed(
            int radius,
            int blockX,
            int blockZ,
            ProvinceAuthority.Province expected) {
        long found = findProvinceSeedOrZero(radius, blockX, blockZ, expected);
        if (found != 0L) {
            return found;
        }
        throw new AssertionError("no province seed for focused warm-upland fixture: " + expected);
    }

    private static long findProvinceSeedOrZero(
            int radius,
            int blockX,
            int blockZ,
            ProvinceAuthority.Province expected) {
        for (long candidate = 1; candidate <= 20_000; candidate++) {
            if (new ProvinceAuthority(candidate, radius).classify(blockX, blockZ) == expected) {
                return candidate;
            }
        }
        return 0L;
    }

    private static Holder<Biome> pickRegistry(
            TestBiomeRegistry registry,
            Holder<Biome> base,
            int blockX,
            int blockZ,
            int radius,
            Climate.Sampler sampler) {
        return LatitudeBiomes.pick(
                registry.registry(), base, blockX, blockZ, 63, radius, sampler, "ATLAS_SAMPLER");
    }

    private static Holder<Biome> pickCollection(
            TestBiomeRegistry registry,
            Holder<Biome> base,
            int blockX,
            int blockZ,
            int radius,
            Climate.Sampler sampler) {
        return LatitudeBiomes.pick(
                registry.holders(), base, blockX, blockZ, 63, radius, sampler, "ATLAS_SAMPLER");
    }

    private static void assertBothBiomeId(
            String expected,
            Holder<Biome> registryResult,
            Holder<Biome> collectionResult,
            String message) {
        String registryId = LatitudeBiomes.biomeIdPublic(registryResult);
        String collectionId = LatitudeBiomes.biomeIdPublic(collectionResult);
        assertTrue(expected.equals(registryId) && expected.equals(collectionId),
                message + ": registry=" + registryId + " collection=" + collectionId);
    }

    /**
     * What a WARM_MEDIUM column is entitled to finish as, now that savanna is a REGION rather than
     * the whole belt (maintainer approval, 2026-08-18).
     *
     * <p>Savanna has exactly two homes inside this province -- its countries, and the dry fringe
     * hugging an arid province -- and outside both the warm-medium staple is minecraft:forest. Three
     * assertions in this suite predate that ruling and asserted SAVANNA unconditionally; they were
     * correct when WARM_MEDIUM had one answer. They ask the geography now rather than being widened
     * to "savanna or forest", which would have passed on a picker that had stopped consulting the
     * country at all -- the exact "looks wired, does nothing" failure this slice was most at risk of.
     *
     * <p>Both predicates read the LIVE province authority, so they answer for the same swapped
     * authority the picker under test is running against.
     */
    private static BiomeDescriptorLedger.Family warmMediumExpectedFamily(
            int blockX, int blockZ, int radius) {
        boolean savannaOwnsColumn =
                LatitudeBiomes.savannaCountryHitForPolicyTest(blockX, blockZ)
                        || LatitudeBiomes.savannaDryFringeHitForPolicyTest(blockX, blockZ);
        return savannaOwnsColumn
                ? BiomeDescriptorLedger.Family.SAVANNA
                : BiomeDescriptorLedger.Family.FOREST;
    }

    private static void assertBothFamily(
            BiomeDescriptorLedger.Family expected,
            Holder<Biome> registryResult,
            Holder<Biome> collectionResult,
            String message) {
        BiomeDescriptorLedger.Descriptor registryDescriptor = BiomeDescriptorLedger.descriptor(
                LatitudeBiomes.biomeIdPublic(registryResult));
        BiomeDescriptorLedger.Descriptor collectionDescriptor = BiomeDescriptorLedger.descriptor(
                LatitudeBiomes.biomeIdPublic(collectionResult));
        assertTrue(registryDescriptor != null && collectionDescriptor != null
                        && registryDescriptor.family() == expected
                        && collectionDescriptor.family() == expected,
                message + ": registry=" + LatitudeBiomes.biomeIdPublic(registryResult)
                        + " collection=" + LatitudeBiomes.biomeIdPublic(collectionResult));
    }

    private static void assertBothWarmWetLowlandCompatible(
            Holder<Biome> registryResult,
            Holder<Biome> collectionResult,
            String message) {
        BiomeDescriptorLedger.Descriptor registryDescriptor = BiomeDescriptorLedger.descriptor(
                LatitudeBiomes.biomeIdPublic(registryResult));
        BiomeDescriptorLedger.Descriptor collectionDescriptor = BiomeDescriptorLedger.descriptor(
                LatitudeBiomes.biomeIdPublic(collectionResult));
        assertTrue(isWarmWetLowlandCompatible(registryDescriptor)
                        && isWarmWetLowlandCompatible(collectionDescriptor),
                message + ": registry=" + LatitudeBiomes.biomeIdPublic(registryResult)
                        + " collection=" + LatitudeBiomes.biomeIdPublic(collectionResult));
    }

    private static boolean isWarmWetLowlandCompatible(BiomeDescriptorLedger.Descriptor descriptor) {
        return descriptor != null
                && descriptor.terrain() != BiomeDescriptorLedger.Terrain.ARID
                && (descriptor.family() == BiomeDescriptorLedger.Family.FOREST
                    || descriptor.family() == BiomeDescriptorLedger.Family.JUNGLE
                    || descriptor.family() == BiomeDescriptorLedger.Family.WETLAND);
    }

    static void polarTaigaTransitionPreservesShouldersAndTreeLine() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Registry<Biome> registry = buildTestBiomeRegistry(List.of()).registry();
        List<Holder<Biome>> pool = registry.holders().map(h -> (Holder<Biome>) h).toList();
        java.lang.reflect.Method registryGate = LatitudeBiomes.class.getDeclaredMethod(
                "gatePolarTaigaSurvival", Registry.class, Holder.class,
                int.class, double.class, int.class, int.class);
        java.lang.reflect.Method collectionGate = LatitudeBiomes.class.getDeclaredMethod(
                "gatePolarTaigaSurvival", java.util.Collection.class, Holder.class,
                int.class, double.class, int.class, int.class);
        registryGate.setAccessible(true);
        collectionGate.setAccessible(true);
        String[] taigas = {"minecraft:taiga", "minecraft:snowy_taiga",
                "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga"};
        try {
            LatitudeBiomes.setWorldSeed(1L);
            for (int radius : new int[]{3750, 5000, 7500, 10000, 15000, 20000}) {
                for (int sign : new int[]{-1, 1}) {
                    int samples = 0, below = 0, above = 0, middle = 0, changedNeighbors = 0;
                    for (int x = -radius; x <= radius; x += 64) {
                        samples++;
                        for (double latitude : new double[]{66.4, 66.6, 69.25, 72.0, 74.5, 80.0}) {
                            int z = sign * (int) Math.round(radius * latitude / 90.0);
                            double actualLatitude = Math.abs(z) * 90.0 / radius;
                            for (String id : taigas) {
                                Holder<Biome> input = holder(registry, id);
                                Object a = registryGate.invoke(null, registry, input, 3, actualLatitude, x, z);
                                Object b = collectionGate.invoke(null, pool, input, 3, actualLatitude, x, z);
                                assertEquals(a, b, "both final picker paths must make the same transition decision");
                                if (latitude <= 66.4) assertEquals(input, a, "subpolar taiga below the transition is unchanged");
                                if (latitude >= 72.0) {
                                    assertEquals(holder(registry, "minecraft:snowy_plains"), a,
                                            "taiga must not extend past the existing woody tree line");
                                }
                                if (id.equals("minecraft:taiga")) {
                                    if (latitude == 66.4 && a == input) below++;
                                    if (latitude == 66.6 && a == input) above++;
                                    if (latitude == 69.25 && a == input) middle++;
                                    if (latitude == 69.25) {
                                        Object neighbor = registryGate.invoke(null, registry, input, 3, actualLatitude, x + 1, z);
                                        if (neighbor != a) changedNeighbors++;
                                    }
                                }
                                Object polar = registryGate.invoke(null, registry, input, 4, actualLatitude, x, z);
                                assertEquals(polar, collectionGate.invoke(null, pool, input, 4, actualLatitude, x, z),
                                        "both final picker paths preserve the polar selection exclusion");
                                assertEquals(holder(registry, "minecraft:snowy_plains"), polar,
                                        "a genuinely polar blended selection must remain treeless");
                            }
                            Holder<Biome> meadow = holder(registry, "minecraft:meadow");
                            assertEquals(meadow, registryGate.invoke(null, registry, meadow, 3, actualLatitude, x, z),
                                    "the taiga transition must not replace another biome family");
                        }
                    }
                    Holder<Biome> edgeTaiga = holder(registry, "minecraft:taiga");
                    int edgeZ = sign * (int) Math.round(radius * 66.5 / 90.0);
                    assertEquals(edgeTaiga, registryGate.invoke(null, registry, edgeTaiga, 3, 66.5, 0, edgeZ),
                            "the exact transition start must preserve a blended subpolar selection");
                    assertEquals(edgeTaiga, collectionGate.invoke(null, pool, edgeTaiga, 3, 66.5, 0, edgeZ),
                            "collection path must preserve the exact transition start too");
                    assertEquals(samples, below, "all pre-boundary subpolar taiga control points survive");
                    assertTrue(above > samples * 0.95,
                            "crossing 66.5 degrees must not synchronously erase the subpolar taiga pool");
                    assertTrue(middle > 0 && middle < samples,
                            "the shoulder must contain both surviving taiga and snow replacements");
                    assertTrue(changedNeighbors < samples * 0.05,
                            "survival decisions must form coherent patches rather than per-block noise");
                }
            }
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    private static void descriptorAdmissionIsClosedAndCanonical() {
        List<String> reversedRegistry = new ArrayList<>(registryFor(Set.of("biomesoplenty", "terralith")));
        reversedRegistry.add("terralith:amethyst_canyon");
        reversedRegistry.add("clifftree:temperate_grove");
        reversedRegistry.add("unreviewed:random_desert");
        List<String> normalRegistry = new ArrayList<>(reversedRegistry);
        java.util.Collections.reverse(reversedRegistry);

        BiomeSelectionProfile normal = BiomeSelectionProfile.capture(normalRegistry);
        BiomeSelectionProfile reversed = BiomeSelectionProfile.capture(reversedRegistry);
        assertEquals(normal.encode(), reversed.encode(), "registry order cannot change the birth profile");
        assertTrue(normal.providers().equals(List.of("biomesoplenty", "minecraft", "terralith")),
                "only explicitly supported providers enter the roster");
        for (BiomeRoute route : BiomeRoute.values()) {
            assertFalse(normal.contains(route, "terralith:amethyst_canyon"),
                    "unreviewed warm upland identity receives no ticket");
            assertFalse(normal.contains(route, "clifftree:temperate_grove"),
                    "unsupported provider receives no ticket");
            assertFalse(normal.contains(route, "unreviewed:random_desert"),
                    "an unknown custom biome receives no ticket");
            for (String id : normal.entries(route)) {
                BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
                assertTrue(descriptor != null && descriptor.routes().contains(route),
                        "every selected row has one matching verified descriptor: " + id);
            }
        }
        assertEquals(normal.encode(), BiomeSelectionProfile.decode(normal.encode()).encode(),
                "profile serialization is canonical");
        assertTrue(normal.contains(BiomeRoute.TEMPERATE_LOWLAND, "biomesoplenty:redwood_forest"),
                "new profiles route redwood forest through temperate lowlands");
        assertFalse(normal.contains(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND, "biomesoplenty:redwood_forest"),
                "new profiles do not retain the corrected subtropical redwood route");
        BiomeSelectionProfile legacyRedwood = BiomeSelectionProfile.decode(
                "provider_ticket_v1\nSUBTROPICAL_HUMID_LOWLAND|biomesoplenty:redwood_forest");
        assertTrue(legacyRedwood.contains(
                        BiomeRoute.SUBTROPICAL_HUMID_LOWLAND,
                        "biomesoplenty:redwood_forest"),
                "an existing world's birth-locked redwood route remains readable without migration");
        assertThrows(() -> BiomeSelectionProfile.decode(
                        "provider_ticket_v1\nSUBTROPICAL_HUMID_LOWLAND|biomesoplenty:redwood_forest|extra"),
                "a malformed legacy-looking redwood row is rejected");
        assertThrows(() -> BiomeSelectionProfile.decode(
                        "provider_ticket_v1\nSUBTROPICAL_HUMID_LOWLAND|biomesoplenty:seasonal_forest"),
                "the redwood compatibility exception does not migrate other stale descriptor routes");
        assertThrows(() -> BiomeSelectionProfile.decode("provider_ticket_v1\nTEMPERATE_LOWLAND|terralith:amethyst_canyon"),
                "a descriptorless saved row is rejected");
        assertThrows(() -> BiomeSelectionProfile.decode("provider_ticket_v1\nTEMPERATE_LOWLAND|minecraft:forest\nTEMPERATE_LOWLAND|minecraft:forest"),
                "duplicate saved route rows are rejected");
    }

    /**
     * Wetland routing is checked against REAL biome climate, not against the ledger's own opinion.
     *
     * <p>This test used to assert {@code Set.of(TEMPERATE_WETLAND)} for muskeg and ice_marsh — it
     * read the ledger's {@code routes()} and compared them to literals typed into the test, so it
     * passed by construction and pinned a reported defect in place as expected behaviour. The
     * temperatures below are ground truth lifted from the shipped datapack JSON of the providers
     * themselves, so the assertion can now FAIL when a route contradicts the climate.
     *
     * <p>The rule is vanilla's own snow threshold: {@code Biome.coldEnoughToSnow} is
     * {@code temperature < 0.15f}. A wetland that snows permanently belongs in the subpolar band;
     * one that does not belongs in the temperate band. muskeg (0.0) and ice_marsh (0.14, plus a
     * frozen modifier) are the two that snow; bog at 0.2 does not, and deliberately stays temperate.
     */
    private static void wetlandRoutesMatchRealBiomeClimate() {
        // biome id -> temperature, from the providers' own worldgen/biome JSON.
        String[][] wetlandClimate = {
                {"biomesoplenty:muskeg", "0.0"},
                {"terralith:ice_marsh", "0.14"},
                {"biomesoplenty:bog", "0.2"},
                {"biomesoplenty:wetland", "0.6"},
                {"biomesoplenty:marsh", "0.65"},
                {"terralith:orchid_swamp", "0.8"},
                {"minecraft:swamp", "0.8"},
        };
        double snowThreshold = 0.15;
        for (String[] row : wetlandClimate) {
            String id = row[0];
            double temperature = Double.parseDouble(row[1]);
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
            assertTrue(descriptor != null, "admitted wetland has an explicit descriptor: " + id);

            BiomeRoute expected = temperature < snowThreshold
                    ? BiomeRoute.SUBPOLAR_WETLAND
                    : BiomeRoute.TEMPERATE_WETLAND;
            assertEquals(Set.of(expected), descriptor.routes(),
                    "wetland route must match its real climate (temperature " + temperature
                            + (temperature < snowThreshold ? ", snows permanently" : ", never snows")
                            + "), and must carry no generic dry, highland or fallback route: " + id);
            assertEquals(BiomeDescriptorLedger.Terrain.WETLAND, descriptor.terrain(),
                    "wetland is never admitted as ordinary land: " + id);
            assertEquals(BiomeDescriptorLedger.Water.WETLAND, descriptor.water(),
                    "wetland requires the wetland water authority: " + id);
        }

        // A cold wetland must now be authorable at all — the old invariant threw for any wetland
        // that did not own TEMPERATE_WETLAND, which is why the cold pair could not be fixed in place.
        assertTrue(BiomeDescriptorLedger.descriptor("biomesoplenty:muskeg")
                        .routes().contains(BiomeRoute.SUBPOLAR_WETLAND),
                "the cold wetland route must actually be owned, or it is dead config — the old "
                        + "invariant threw for any wetland that did not own TEMPERATE_WETLAND, "
                        + "which is why this pair could not be fixed in place");

        assertTrue(BiomeDescriptorLedger.descriptor("biomesoplenty:bayou") == null,
                "warm bayou remains closed until Latitude owns a warm-wetland route");
        assertTrue(BiomeDescriptorLedger.descriptor("biomesoplenty:floodplain") == null,
                "tropical floodplain remains closed until Latitude owns a warm-wetland route");
    }

    private static void climateLowlandDescriptorsRemainRouteBounded() {
        assertClimateLowland(BiomeRoute.TROPICAL_HUMID_LOWLAND, BiomeDescriptorLedger.Family.JUNGLE,
                "biomesoplenty:fungal_jungle");
        assertClimateLowland(BiomeRoute.SUBTROPICAL_HUMID_LOWLAND, BiomeDescriptorLedger.Family.FOREST,
                "biomesoplenty:mystic_grove");
        assertClimateLowland(BiomeRoute.TEMPERATE_LOWLAND, BiomeDescriptorLedger.Family.FOREST,
                "biomesoplenty:redwood_forest", "biomesoplenty:lavender_field",
                "biomesoplenty:overgrown_greens",
                "terralith:moonlight_grove", "terralith:moonlight_valley");
        assertClimateLowland(BiomeRoute.WARM_TRANSITION, BiomeDescriptorLedger.Family.FOREST,
                "biomesoplenty:mediterranean_forest", "terralith:brushland");
        assertClimateLowland(BiomeRoute.WARM_TRANSITION, BiomeDescriptorLedger.Family.ARID,
                "terralith:hot_shrubland", "terralith:shrubland");
        assertAridLowland("biomesoplenty:lush_desert");
        assertClimateLowland(BiomeRoute.SUBPOLAR_LOWLAND, BiomeDescriptorLedger.Family.TAIGA,
                "biomesoplenty:field", "biomesoplenty:pumpkin_patch",
                "terralith:lush_valley", "terralith:shield", "terralith:shield_clearing",
                "terralith:yosemite_lowlands");
        assertColdLowland("biomesoplenty:auroral_garden", "biomesoplenty:snowblossom_grove",
                "biomesoplenty:wintry_origin_valley", "terralith:cold_shrubland",
                "terralith:snowy_cherry_grove");
    }

    private static void assertClimateLowland(
            BiomeRoute route,
            BiomeDescriptorLedger.Family family,
            String... ids) {
        for (String id : ids) {
            assertDescriptor(id, Set.of(route), BiomeDescriptorLedger.Terrain.LOWLAND, family);
        }
    }

    private static void assertAridLowland(String... ids) {
        for (String id : ids) {
            assertDescriptor(id, Set.of(BiomeRoute.ARID_LOWLAND), BiomeDescriptorLedger.Terrain.ARID,
                    BiomeDescriptorLedger.Family.ARID);
        }
    }

    private static void assertColdLowland(String... ids) {
        for (String id : ids) {
            assertDescriptor(id, Set.of(BiomeRoute.SUBPOLAR_LOWLAND, BiomeRoute.POLAR_LOWLAND),
                    BiomeDescriptorLedger.Terrain.LOWLAND, BiomeDescriptorLedger.Family.POLAR);
        }
    }

    private static void assertDescriptor(
            String id,
            Set<BiomeRoute> routes,
            BiomeDescriptorLedger.Terrain terrain,
            BiomeDescriptorLedger.Family family) {
        BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
        assertTrue(descriptor != null, "climate biome has an explicit descriptor: " + id);
        assertEquals(routes, descriptor.routes(), "climate biome has only its verified route: " + id);
        assertEquals(terrain, descriptor.terrain(), "climate biome has its verified terrain class: " + id);
        assertEquals(BiomeDescriptorLedger.Water.LAND, descriptor.water(),
                "climate land biome cannot enter a wetland or coastal route: " + id);
        assertEquals(family, descriptor.family(), "climate biome preserves village-family cohesion: " + id);
    }

    /**
     * Uses vanilla-only, vanilla+BOP, vanilla+Terralith, and the combined supported stack.
     * The four-sigma bounds are binomial bounds declared before any world/map sample is run.
     */
    private static void everySupportedStackGetsEqualRouteTickets() {
        for (Set<String> stack : List.of(
                Set.<String>of(),
                Set.of("biomesoplenty"),
                Set.of("terralith"),
                Set.of("biomesoplenty", "terralith"))) {
            BiomeSelectionProfile profile = BiomeSelectionProfile.capture(registryFor(stack));
            for (BiomeRoute route : BiomeRoute.values()) {
                List<String> ids = profile.entries(route);
                if (ids.isEmpty()) continue;
                BiomeProviderSelectionPolicy.Pool pool = BiomeProviderSelectionPolicy.createPool(ids);
                Map<String, Integer> providerCounts = new HashMap<>();
                Map<String, Integer> biomeCounts = new HashMap<>();
                int samples = 0;
                for (long seed : AUDIT_SEEDS) {
                    for (int x : NONADJACENT_POINTS) {
                        for (int z : NONADJACENT_POINTS) {
                            String id = BiomeProviderSelectionPolicy.selectId(pool, seed, x, z, 2, route.name(), 0L);
                            providerCounts.merge(namespace(id), 1, Integer::sum);
                            biomeCounts.merge(id, 1, Integer::sum);
                            samples++;
                        }
                    }
                }
                List<String> providers = profile.providers(route);
                assertEquals(providers.size(), pool.providers().size(), "route roster and pool agree: " + route);
                for (String provider : providers) {
                    assertWithinFourSigma(providerCounts, provider, samples, 1.0 / providers.size(),
                            "equal provider ticket for " + route + " / " + provider);
                    int providerSamples = providerCounts.getOrDefault(provider, 0);
                    List<String> providerIds = ids.stream().filter(id -> namespace(id).equals(provider)).toList();
                    for (String id : providerIds) {
                        assertWithinFourSigma(biomeCounts, id, providerSamples, 1.0 / providerIds.size(),
                                "equal winning-provider biome ticket for " + route + " / " + id);
                    }
                }
            }
        }
    }

    private static void selectionIsWorldSeededAndCoherent() {
        BiomeSelectionProfile profile = BiomeSelectionProfile.capture(registryFor(Set.of("biomesoplenty", "terralith")));
        BiomeProviderSelectionPolicy.Pool pool = BiomeProviderSelectionPolicy.createPool(profile.entries(BiomeRoute.TEMPERATE_LOWLAND));
        Set<String> reached = new HashSet<>();
        int compared = 0;
        int changedSeed = 0;
        int neighborMatches = 0;
        for (int x = -20_000; x <= 20_000; x += 128) {
            for (int z = -20_000; z <= 20_000; z += 128) {
                String first = BiomeProviderSelectionPolicy.selectId(pool, AUDIT_SEEDS[0], x, z, 2, BiomeRoute.TEMPERATE_LOWLAND.name(), 0L);
                String otherSeed = BiomeProviderSelectionPolicy.selectId(pool, AUDIT_SEEDS[1], x, z, 2, BiomeRoute.TEMPERATE_LOWLAND.name(), 0L);
                String neighbor = BiomeProviderSelectionPolicy.selectId(pool, AUDIT_SEEDS[0], x + 16, z, 2, BiomeRoute.TEMPERATE_LOWLAND.name(), 0L);
                reached.add(first);
                if (!first.equals(otherSeed)) changedSeed++;
                if (first.equals(neighbor)) neighborMatches++;
                compared++;
            }
        }
        assertTrue(reached.containsAll(pool.ids()), "every admitted temperate identity remains reachable");
        assertGreaterThan(0.35, changedSeed / (double) compared, "world seed materially changes identity provinces");
        assertGreaterThan(0.90, neighborMatches / (double) compared, "neighboring chunks remain coherent, not confetti");
    }

    private static void routeSelectionCannotFallBackToAnUnclassifiedTag() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/example/globe/world/LatitudeBiomes.java")).replaceAll("\\s+", " ");
        assertEquals(2, occurrences(source, "entriesForProviderTicketRoute(biomes, providerRoute)"),
                "registry and collection routes resolve the saved descriptor profile before tag contents");
        assertTrue(source.contains("for (Holder<Biome> entry : entriesForTag(biomes, tag))"),
                "late registry land-pool rewrites cannot re-admit descriptorless tag entries");
        assertTrue(source.contains("ACTIVE_PROVIDER_TICKET_PROFILE = isProviderTicketPolicy(ACTIVE_WORLDGEN_POLICY)"),
                "V1 and V2 cannot silently use the mutable tag pool when their birth profile is absent");
        assertTrue(source.contains("BiomeProviderSelectionPolicy.selectIndex("),
                "the live biome selector invokes the two-stage provider policy");
    }

    /** Source-level guard for the hot path: first context-bound use may resolve, later picks may not. */
    private static void providerTicketHotPathCachesAndInvalidates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        for (String cache : List.of(
                "PROVIDER_TICKET_REGISTRY_ROUTE_CACHE",
                "PROVIDER_TICKET_SOURCE_ROUTE_CACHE",
                "ALLOWED_LAND_POOL_REGISTRY_CACHE",
                "ALLOWED_LAND_POOL_SOURCE_CACHE",
                "FILTERED_LAND_POOL_REGISTRY_CACHE",
                "FILTERED_LAND_POOL_SOURCE_CACHE",
                "REROLL_LAND_POOL_REGISTRY_CACHE",
                "REROLL_LAND_POOL_SOURCE_CACHE")) {
            assertTrue(source.contains(cache + ".clear();"),
                    "context cleanup invalidates " + cache);
        }
        String registryRoute = method(source,
                "private static List<Holder<Biome>> entriesForProviderTicketRoute(Registry<Biome> biomes, BiomeRoute route)");
        String collectionRoute = method(source,
                "private static List<Holder<Biome>> entriesForProviderTicketRoute(Collection<Holder<Biome>> biomes, BiomeRoute route)");
        String registryLandPool = method(source,
                "private static List<Holder<Biome>> allowedLandPool(Registry<Biome> biomes, int bandIndex)");
        String collectionLandPool = method(source,
                "private static List<Holder<Biome>> allowedLandPool(Collection<Holder<Biome>> biomes, int bandIndex)");
        String registryFiltered = method(source,
                "private static List<Holder<Biome>> filteredAllowedLandPool(Registry<Biome> biomes,");
        String collectionFiltered = method(source,
                "private static List<Holder<Biome>> filteredAllowedLandPool(Collection<Holder<Biome>> biomes,");
        String registryReroll = method(source,
                "private static List<Holder<Biome>> rerollLandPoolForBand(Registry<Biome> biomes,");
        String collectionReroll = method(source,
                "private static List<Holder<Biome>> rerollLandPoolForBand(Collection<Holder<Biome>> biomes,");
        for (String body : List.of(registryRoute, collectionRoute, registryLandPool, collectionLandPool)) {
            assertTrue(body.indexOf("if (cached != null) return cached;") < body.indexOf("new ArrayList"),
                    "cache hit returns before allocation/traversal");
            assertTrue(body.contains("putIfAbsent"), "first context-bound resolution publishes one immutable pool");
        }
        assertFalse(registryRoute.contains("getTagOrEmpty"), "route cache never traverses raw registry tags");
        assertFalse(collectionRoute.contains("entry.is(tag)"), "route cache never traverses source tags after its first resolution");
        assertTrue(registryLandPool.contains("entriesForTag(biomes, tag)"),
                "registry late pool shares the descriptor-resolved route cache");
        assertTrue(collectionLandPool.contains("entriesForTag(biomes, tag)"),
                "collection late pool shares the descriptor-resolved route cache");
        for (String body : List.of(registryFiltered, collectionFiltered, registryReroll, collectionReroll)) {
            assertTrue(body.indexOf("if (cached != null) return cached;") < body.indexOf("List.copyOf"),
                    "terrain pool cache hit returns before filtered-pool allocation");
            assertTrue(body.contains("putIfAbsent"), "first context-bound terrain variant publishes one immutable pool");
        }
        assertTrue(source.contains("List<Holder<Biome>> candidates = entriesForTag(biomes, tag);"),
                "tropical override cannot bypass the descriptor-resolved tag path");
    }

    private static void providerProfileCompatibilityIsBirthLocked() throws Exception {
        BiomeSelectionProfile bornWithBop = BiomeSelectionProfile.capture(
                registryFor(Set.of("biomesoplenty")));
        BiomeSelectionProfile reloaded = BiomeSelectionProfile.decode(bornWithBop.encode());
        assertEquals(bornWithBop.encode(), reloaded.encode(),
                "reload preserves the exact birth-time provider roster and route rows");

        List<String> registryAfterAddingTerralith =
                registryFor(Set.of("biomesoplenty", "terralith"));
        assertTrue(reloaded.missingIds(registryAfterAddingTerralith).isEmpty(),
                "adding a provider later does not invalidate the locked profile");
        for (BiomeRoute route : BiomeRoute.values()) {
            assertTrue(reloaded.entries(route).stream().noneMatch(id -> id.startsWith("terralith:")),
                    "a later provider receives no ticket in an existing world: " + route);
        }

        List<String> missingAfterRemovingBop = reloaded.missingIds(registryFor(Set.of()));
        assertTrue(!missingAfterRemovingBop.isEmpty()
                        && missingAfterRemovingBop.stream().allMatch(id -> id.startsWith("biomesoplenty:")),
                "removing a locked provider is detected without inventing replacement custom IDs");
        String mod = Files.readString(Path.of("src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(mod.contains("No new provider is substituted")
                        && mod.contains("removing biome mods can still leave saved chunks unreadable"),
                "the live compatibility warning states both the closed fallback and saved-ID risk");
    }

    private static void vanillaCoverageIsCompleteAndWorldSizeSafe() {
        BiomeSelectionProfile vanilla = BiomeSelectionProfile.capture(registryFor(Set.of()));
        assertTrue(VanillaBiomeCoveragePlan.verifiedCounterparts().isEmpty(),
                "no custom biome silently substitutes for a vanilla biome");
        for (Map.Entry<String, BiomeRoute> requirement
                : VanillaBiomeCoveragePlan.requiredRoutes().entrySet()) {
            BiomeDescriptorLedger.Descriptor descriptor =
                    BiomeDescriptorLedger.descriptor(requirement.getKey());
            assertTrue(descriptor != null && descriptor.provider().equals("minecraft"),
                    "every guaranteed identity is an explicit vanilla descriptor: " + requirement.getKey());
            assertTrue(descriptor.routes().contains(requirement.getValue()),
                    "every guaranteed identity owns its exact coverage route: " + requirement.getKey());
        }

        int[] radii = {3_750, 5_000, 7_500, 10_000, 15_000, 20_000};
        long[] seeds = {3L, 41L, 131L, 461L};
        for (int radius : radii) {
            for (long seed : seeds) {
                VanillaBiomeCoveragePlan plan = VanillaBiomeCoveragePlan.build(
                        radius, seed, vanilla,
                        (id, route, x, z) -> insideSyntheticRoute(route, x, z, radius));
                assertTrue(plan.complete(),
                        "every route-managed vanilla biome gets an anchor at radius=" + radius
                                + " seed=" + seed + " missing=" + plan.missingBiomeIds());
                assertEquals(VanillaBiomeCoveragePlan.requiredRoutes().size(), plan.anchors().size(),
                        "one coherent anchor exists per required identity");
                Set<String> ids = new HashSet<>();
                for (VanillaBiomeCoveragePlan.Anchor anchor : plan.anchors()) {
                    assertTrue(ids.add(anchor.biomeId()), "coverage identities are unique");
                    assertTrue(anchor.radiusBlocks() >= 64,
                            "coverage is a province, not a one-chunk token");
                    assertTrue(plan.matches(anchor.blockX(), anchor.blockZ()).contains(anchor),
                            "each guaranteed province remains reachable at its center even when a "
                                    + "mutually-exclusive physical route shares the map area");
                    assertTrue(insideSyntheticRoute(
                                    anchor.route(), anchor.blockX(), anchor.blockZ(), radius),
                            "anchor remains in its declared climate/terrain route");
                    long centerDistanceSquared = (long) anchor.blockX() * anchor.blockX()
                            + (long) anchor.blockZ() * anchor.blockZ();
                    long safeRadius = radius - anchor.radiusBlocks() - 48L;
                    assertTrue(centerDistanceSquared <= safeRadius * safeRadius,
                            "the whole province remains inside the circular world border");
                }
            }
        }

        // Real mountain ridges are materially narrower than lowland climate provinces. The
        // coverage planner must fit a substantial six-chunk-radius upland province into a
        // coherent ridge instead of requiring a fourteen-chunk-radius disk and omitting every
        // mountain identity, as the live TEST 31 seed did.
        int liveRadius = 10_000;
        long liveSeed = 1_266_034_320_117_822_817L;
        VanillaBiomeCoveragePlan narrowRidgePlan = VanillaBiomeCoveragePlan.build(
                liveRadius,
                liveSeed,
                vanilla,
                (id, route, x, z) -> {
                    if (!insideSyntheticRoute(route, x, z, liveRadius)) return false;
                    if (!isUplandRoute(route)) return true;
                    int stripe = Math.floorMod(x, 512);
                    return stripe <= 96 || stripe >= 416;
                });
        assertTrue(narrowRidgePlan.complete(),
                "narrow but coherent upland ridges must not omit required vanilla identities: "
                        + narrowRidgePlan.missingBiomeIds());
        for (VanillaBiomeCoveragePlan.Anchor anchor : narrowRidgePlan.anchors()) {
            if (isUplandRoute(anchor.route())) {
                assertEquals(64, anchor.radiusBlocks(),
                        "upland reservations use a ridge-scale coherent radius");
            }
        }
        List<VanillaBiomeCoveragePlan.Anchor> occupiedRidge = List.of(
                new VanillaBiomeCoveragePlan.Anchor(
                        "minecraft:cherry_grove", BiomeRoute.TEMPERATE_UPLAND,
                        0, 0, 64, 17L));
        assertTrue(VanillaBiomeCoveragePlan.hasDistinctVisibleCore(
                        occupiedRidge, BiomeRoute.TEMPERATE_UPLAND, 96, 0),
                "same-route ridge provinces may share their outer shoulders when the later "
                        + "identity retains a distinct two-chunk core");
        assertTrue(!VanillaBiomeCoveragePlan.hasDistinctVisibleCore(
                        occupiedRidge, BiomeRoute.TEMPERATE_UPLAND, 16, 0),
                "a later same-route reservation cannot be hidden inside an earlier province");
        // Wetlands are the same story as the ridges above, on a different field. An anchor needs
        // the centre AND four probes at +/-radius/2 to hold together, so a sparse, fast-varying
        // wetland field cannot support a fourteen-chunk-radius disk: measured on certified beta.3
        // bytes, the swamp province failed to anchor on 3 of 12 real seeds at the 224 default
        // (4 of 12 on the 26.2 line) and on 12 of 12 at 112.
        //
        // Pinned as a RELATIONSHIP, not a number: a wetland reservation must fit inside a field
        // whose eligible band is narrower than the default province, whatever radius the code
        // chooses to achieve that. A future change to a different sufficient radius passes; a
        // change back to the default-width disk fails.
        int wetlandBandHalfWidth = 128;
        VanillaBiomeCoveragePlan narrowWetlandPlan = VanillaBiomeCoveragePlan.build(
                liveRadius,
                liveSeed,
                vanilla,
                (id, route, x, z) -> {
                    if (!insideSyntheticRoute(route, x, z, liveRadius)) return false;
                    if (route != BiomeRoute.TEMPERATE_WETLAND
                            && route != BiomeRoute.SUBPOLAR_WETLAND) return true;
                    int stripe = Math.floorMod(x, 1_024);
                    return stripe <= wetlandBandHalfWidth
                            || stripe >= 1_024 - wetlandBandHalfWidth;
                });
        assertTrue(narrowWetlandPlan.complete(),
                "a wetland province must fit a band narrower than the default disk rather than "
                        + "omitting the identity: " + narrowWetlandPlan.missingBiomeIds());
        for (VanillaBiomeCoveragePlan.Anchor anchor : narrowWetlandPlan.anchors()) {
            if (anchor.route() == BiomeRoute.TEMPERATE_WETLAND
                    || anchor.route() == BiomeRoute.SUBPOLAR_WETLAND) {
                assertTrue(anchor.radiusBlocks() <= wetlandBandHalfWidth,
                        "wetland reservations fit inside the eligible band they must hold, so the "
                                + "centre-plus-four-shoulder test can actually be satisfied");
                assertTrue(anchor.radiusBlocks() >= 64,
                        "and remain a province rather than shrinking to a token");
            }
        }

        VanillaBiomeCoveragePlan impossibleLand = VanillaBiomeCoveragePlan.build(
                liveRadius, liveSeed, vanilla, (id, route, x, z) -> false);
        VanillaBiomeCoveragePlan.SearchStats landStats =
                impossibleLand.missingDiagnostics().get("minecraft:windswept_hills");
        assertTrue(landStats != null && landStats.centerEligible() == 0,
                "land-plan diagnostics distinguish absent eligible terrain from topology/capacity");
    }

    /**
     * Eroded Badlands is guaranteed on the lowland arid route, not the upland one (maintainer
     * ruling, 2026-08-12).
     *
     * <p>ARID_UPLAND demands mountain terrain AND a WARM_DRY province at the anchor centre and at
     * all four shoulders, and those are independent sparse fields. On the live Regular seed
     * 8507730871486520283 that intersection held at a centre 66 times and never once survived the
     * shoulders, so the coverage guarantee simply could not be issued. The shape below is that
     * live failure in miniature: an upland pocket narrower than the reservation, so every eligible
     * centre loses a shoulder, while lowland arid terrain stays coherent. Revert the route to
     * ARID_UPLAND and this fails — the planner finds a centre and no anchor.</p>
     */
    private static void erodedBadlandsIsGuaranteedOnTheLowlandAridRoute() {
        assertEquals(BiomeRoute.ARID_LOWLAND,
                VanillaBiomeCoveragePlan.requiredRoutes().get("minecraft:eroded_badlands"),
                "Eroded Badlands is guaranteed on the same arid route its representation profile "
                        + "and descriptor ledger already use");

        BiomeSelectionProfile vanilla = BiomeSelectionProfile.capture(registryFor(Set.of()));
        int radius = 10_000;
        long seed = 8_507_730_871_486_520_283L;
        // Only ARID_UPLAND is pinched. Every other route keeps its ordinary synthetic eligibility,
        // so this cannot pass by making the rest of the map easier.
        VanillaBiomeCoveragePlan plan = VanillaBiomeCoveragePlan.build(
                radius, seed, vanilla,
                (id, route, x, z) -> {
                    if (!insideSyntheticRoute(route, x, z, radius)) return false;
                    if (route != BiomeRoute.ARID_UPLAND) return true;
                    // 48-block stripe against a 64-block reservation: centres land on it (they are
                    // 16-aligned), but x+32 or x-32 always falls off it.
                    return Math.floorMod(x, 512) < 48;
                });
        assertTrue(plan.complete(),
                "an unsatisfiable upland arid pocket cannot cost the world its Eroded Badlands: "
                        + plan.missingBiomeIds());

        VanillaBiomeCoveragePlan.Anchor eroded = plan.anchors().stream()
                .filter(anchor -> anchor.biomeId().equals("minecraft:eroded_badlands"))
                .findFirst().orElse(null);
        assertTrue(eroded != null && eroded.route() == BiomeRoute.ARID_LOWLAND,
                "the reservation Eroded Badlands receives is a lowland arid province");

        // The pinch is real: the same shape still starves a route that genuinely requires upland.
        VanillaBiomeCoveragePlan uplandOnly = VanillaBiomeCoveragePlan.build(
                radius, seed, vanilla,
                Map.of("minecraft:eroded_badlands", BiomeRoute.ARID_UPLAND),
                (id, route, x, z) -> insideSyntheticRoute(route, x, z, radius)
                        && Math.floorMod(x, 512) < 48);
        VanillaBiomeCoveragePlan.SearchStats pinched =
                uplandOnly.missingDiagnostics().get("minecraft:eroded_badlands");
        assertTrue(pinched != null && pinched.centerEligible() > 0
                        && pinched.topologyEligible() == 0,
                "the modelled pocket reproduces the live centre-eligible/shoulder-failing shape");
    }

    private static void customJungleAdmissionIsProvinceBounded() {
        BiomeDescriptorLedger.Descriptor tropicalJungle =
                BiomeDescriptorLedger.descriptor("terralith:tropical_jungle");
        assertTrue(tropicalJungle != null,
                "the reviewed Terralith tropical jungle has a descriptor");
        assertEquals(BiomeDescriptorLedger.Family.JUNGLE, tropicalJungle.family(),
                "the reviewed Terralith tropical jungle retains its jungle family");
        assertTrue(
                VanillaCoverageFinalAdmissionPolicy.mayPreserveCustomInWarmProvince(
                        tropicalJungle, ProvinceAuthority.Province.WARM_WET),
                "custom jungle is preserved only in WARM_WET");
        assertFalse(
                VanillaCoverageFinalAdmissionPolicy.mayPreserveCustomInWarmProvince(
                        tropicalJungle, ProvinceAuthority.Province.WARM_MEDIUM),
                "custom jungle is not preserved in WARM_MEDIUM");
        assertFalse(
                VanillaCoverageFinalAdmissionPolicy.mayPreserveCustomInWarmProvince(
                        tropicalJungle, ProvinceAuthority.Province.WARM_DRY),
                "custom jungle is not preserved in WARM_DRY");
    }

    private static void reservedLandLocateFallsBackToExactAnchor() throws Exception {
        int radius = 10_000;
        BiomeSelectionProfile vanilla = BiomeSelectionProfile.capture(registryFor(Set.of()));
        VanillaBiomeCoveragePlan plan = VanillaBiomeCoveragePlan.build(
                radius,
                41L,
                vanilla,
                Map.of("minecraft:grove", BiomeRoute.TEMPERATE_UPLAND),
                (id, route, x, z) -> insideSyntheticRoute(route, x, z, radius));
        assertTrue(plan.complete(), "the focused Grove reservation has one complete exact-land anchor");
        VanillaBiomeCoveragePlan.Anchor grove = plan.anchors().stream().findFirst().orElseThrow();
        assertEquals("minecraft:grove", grove.biomeId(), "the focused reservation is Grove");

        Method nearest = VanillaBiomeCoveragePlan.class.getDeclaredMethod(
                "nearestAnchorFor", java.util.Collection.class, int.class, int.class);
        assertFalse(Modifier.isPublic(nearest.getModifiers())
                        || Modifier.isProtected(nearest.getModifiers())
                        || Modifier.isPrivate(nearest.getModifiers()),
                "the exact-land locate capability remains package-private");
        nearest.setAccessible(true);
        VanillaBiomeCoveragePlan.Anchor located = (VanillaBiomeCoveragePlan.Anchor) nearest.invoke(
                plan, List.of("minecraft:grove"), 0, 0);
        assertEquals(grove, located,
                "the exact Grove locate fallback resolves the reserved anchor, not an approximate route");
        assertEquals(grove.blockX(), located.blockX(), "the exact Grove fallback preserves its X coordinate");
        assertEquals(grove.blockZ(), located.blockZ(), "the exact Grove fallback preserves its Z coordinate");
        assertTrue(nearest.invoke(plan, List.of("minecraft:the_void"), 0, 0) == null,
                "an unreserved identity has no exact-land fallback anchor");

        List<BlockPos> samples = LatitudeBiomeSource.plannedLandCoverageSamplePositions(
                grove, new BlockPos(grove.blockX() + grove.radiusBlocks(), 80, grove.blockZ()));
        int half = grove.radiusBlocks() / 2;
        assertEquals(5, samples.size(),
                "planned land locate checks the centre and all four planner-certified shoulders");
        assertEquals(new BlockPos(grove.blockX() + half, 80, grove.blockZ()), samples.getFirst(),
                "planned land locate returns the nearest final-output sample first");
        assertTrue(samples.contains(new BlockPos(grove.blockX(), 80, grove.blockZ()))
                        && samples.contains(new BlockPos(grove.blockX() - half, 80, grove.blockZ()))
                        && samples.contains(new BlockPos(grove.blockX(), 80, grove.blockZ() + half))
                        && samples.contains(new BlockPos(grove.blockX(), 80, grove.blockZ() - half)),
                "planned land locate retains every planner-certified center-or-shoulder sample");

        String biomeSource = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeBiomeSource.java"))
                .replaceAll("\\s+", " ");
        int ordinaryCoarseFallback = biomeSource.indexOf("Pair<BlockPos, Holder<Biome>> fallback =");
        int ordinaryCombinedFallback = biomeSource.indexOf(
                "findPlannedSurfaceCoverage(", ordinaryCoarseFallback);
        assertTrue(ordinaryCoarseFallback >= 0 && ordinaryCombinedFallback > ordinaryCoarseFallback,
                "ordinary surface locate calls the combined planned fallback only after its coarse fallback");
        assertTrue(
                biomeSource.contains("LatitudeBiomes.nearestPlannedVanillaCoverageAnchor(")
                        && biomeSource.contains("LatitudeBiomes.nearestPlannedSurfaceWaterCoverageAnchor("),
                "combined planned surface fallback checks exact land identity without removing water fallback");
        assertTrue(
                biomeSource.contains("plannedLandCoverageSamplePositions(anchor, origin)")
                        && biomeSource.contains("had no final surviving center-or-shoulder sample"),
                "a saved land anchor is verified at every certified footprint sample and reports a true exhaustion");

        String locateService = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeBiomeLocateService.java"))
                .replaceAll("\\s+", " ");
        int tickCoarseFallback = locateService.indexOf("if (fallbackOffsets.hasNext())");
        int tickCombinedFallback = locateService.indexOf(
                "findPlannedSurfaceCoverage(", tickCoarseFallback);
        assertTrue(tickCoarseFallback >= 0 && tickCombinedFallback > tickCoarseFallback,
                "tick-sliced surface locate calls the combined planned fallback only after its coarse fallback");
    }

    /**
     * Exercises the real V5 plan through both public biome-picker owners. Coverage is intentionally
     * built against coordinate-aware terrain so the test proves the saved identity survives the
     * same final climate and physical authorities used by live worldgen.
     */
    private static void finalVanillaCoverageHonorsHumidityAndLateAuthorities() {
        CoverageFixture regular = activateCoverageFixture(10_000, Set.of());
        try {
            assertEquals(VanillaBiomeCoveragePlan.requiredRoutes(), regular.representation().landTargets(),
                    "Regular V5 coverage retains the exact vanilla land representation");
            assertCoverageFinalOutputs(regular);
            assertJungleProvinceRejectionAndRestoration(regular);
            assertIncompatibleCoverageEvidenceIsRejected(regular);
            assertExistingWetlandAndPaleGardenProtection(regular);
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }

        CoverageFixture compact = activateCoverageFixture(
                7_500, Set.of("biomesoplenty", "terralith"));
        try {
            assertTrue(compact.representation().worldSize().compact(),
                    "Small V5 coverage uses the compact representation contract");
            for (Map.Entry<String, VanillaBiomeRepresentationProfile.Omission> omission
                    : compact.representation().omittedExactIds().entrySet()) {
                assertTrue(VanillaBiomeRepresentationProfile.areApprovedEquivalents(
                                omission.getKey(), omission.getValue()),
                        "every compact V5 representative is descriptor-approved: " + omission.getKey());
            }
            assertCoverageFinalOutputs(compact);
            assertJungleProvinceRejectionAndRestoration(compact);
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    private static CoverageFixture activateCoverageFixture(int radius, Set<String> providers) {
        List<String> providerIds = registryFor(providers);
        BiomeSelectionProfile providerProfile = BiomeSelectionProfile.capture(providerIds);
        TestBiomeRegistry testRegistry = buildTestBiomeRegistry(providerIds);
        Climate.Sampler sampler = stripedTerrainSampler();
        for (long seed : AUDIT_SEEDS) {
            VanillaBiomeRepresentationProfile representation =
                    VanillaBiomeRepresentationProfile.capture(radius, seed, providerProfile);
            LatitudeBiomes.activateWorldgenContext(
                    radius,
                    seed,
                    v5FinalAdmissionPolicy(),
                    providerProfile,
                    representation,
                    null,
                    sampler,
                    null,
                    63);
            VanillaBiomeCoveragePlan plan = LatitudeBiomes.activeVanillaCoveragePlanForTest();
            if (plan != null && plan.complete() && everyAuditSampleIsUnshadowed(plan)) {
                return new CoverageFixture(radius, seed, representation, plan, sampler,
                        testRegistry.registry(), testRegistry.holders(),
                        holder(testRegistry.registry(), "minecraft:plains"));
            }
            LatitudeBiomes.clearWorldgenContext();
        }
        throw new AssertionError("no existing audit seed produced a complete, unshadowed V5 land plan at radius="
                + radius);
    }

    private static WorldgenPolicyVersion v5FinalAdmissionPolicy() {
        try {
            return WorldgenPolicyVersion.valueOf("PROVIDER_TICKET_V5_FINAL_ADMISSION");
        } catch (IllegalArgumentException missingV5) {
            throw new AssertionError(
                    "missing V5 final-admission policy PROVIDER_TICKET_V5_FINAL_ADMISSION",
                    missingV5);
        }
    }

    private static boolean everyAuditSampleIsUnshadowed(VanillaBiomeCoveragePlan plan) {
        for (VanillaBiomeCoveragePlan.Anchor anchor : plan.anchors()) {
            for (int[] sample : coverageSamples(anchor)) {
                VanillaBiomeCoveragePlan.Anchor firstEligible = plan.matches(sample[0], sample[1]).stream()
                        .filter(candidate -> candidate.route() == anchor.route())
                        .findFirst()
                        .orElse(null);
                if (!anchor.equals(firstEligible)) return false;
            }
        }
        return true;
    }

    private static void assertCoverageFinalOutputs(CoverageFixture fixture) {
        for (VanillaBiomeCoveragePlan.Anchor anchor : fixture.plan().anchors()) {
            Holder<Biome> expected = holder(fixture.registry(), anchor.biomeId());
            for (int[] sample : coverageSamples(anchor)) {
                assertSameBiome(expected, pickRegistry(fixture, sample[0], sample[1], fixture.sampler()),
                        "registry picker retains V5 land target " + anchor.biomeId()
                                + " at x=" + sample[0] + " z=" + sample[1]
                                + " seed=" + fixture.seed());
                assertSameBiome(expected, pickCollection(fixture, sample[0], sample[1], fixture.sampler()),
                        "collection picker retains V5 land target " + anchor.biomeId()
                                + " at x=" + sample[0] + " z=" + sample[1]
                                + " seed=" + fixture.seed());
                if (anchor.route() == BiomeRoute.TROPICAL_HUMID_LOWLAND) {
                    assertEquals(0, LatitudeBiomes.authoritativeLandBandIndex(
                                    sample[0], sample[1], fixture.radius()),
                            "jungle-family coverage is tropical");
                    assertFalse(isSyntheticMountain(fixture.sampler(), sample[0], sample[1]),
                            "jungle-family coverage is not mountain terrain");
                    assertEquals(ProvinceAuthority.Province.WARM_WET,
                            LatitudeBiomes.classifyProvince(sample[0], sample[1]),
                            "jungle-family coverage is admitted only by WARM_WET");
                }
            }
        }
    }

    private static void assertHumidCoverageFinalOutputs(CoverageFixture fixture) {
        for (VanillaBiomeCoveragePlan.Anchor anchor : fixture.plan().anchors()) {
            if (anchor.route() != BiomeRoute.TROPICAL_HUMID_LOWLAND) continue;
            Holder<Biome> expected = holder(fixture.registry(), anchor.biomeId());
            for (int[] sample : coverageSamples(anchor)) {
                assertSameBiome(expected, pickRegistry(fixture, sample[0], sample[1], fixture.sampler()),
                        "registry picker retains V5 humid target " + anchor.biomeId());
                assertSameBiome(expected, pickCollection(fixture, sample[0], sample[1], fixture.sampler()),
                        "collection picker retains V5 humid target " + anchor.biomeId());
                assertEquals(0, LatitudeBiomes.authoritativeLandBandIndex(
                                sample[0], sample[1], fixture.radius()),
                        "jungle-family coverage is tropical");
                assertFalse(isSyntheticMountain(fixture.sampler(), sample[0], sample[1]),
                        "jungle-family coverage is not mountain terrain");
                assertEquals(ProvinceAuthority.Province.WARM_WET,
                        LatitudeBiomes.classifyProvince(sample[0], sample[1]),
                        "jungle-family coverage is admitted only by WARM_WET");
            }
        }
    }

    private static void assertJungleProvinceRejectionAndRestoration(CoverageFixture fixture) {
        for (VanillaBiomeCoveragePlan.Anchor anchor : fixture.plan().anchors()) {
            if (anchor.route() != BiomeRoute.TROPICAL_HUMID_LOWLAND) continue;
            int x = anchor.blockX();
            int z = anchor.blockZ();
            Holder<Biome> exact = holder(fixture.registry(), anchor.biomeId());
            String context = " [fixtureRadius=" + fixture.radius()
                    + " anchor=" + anchor.biomeId() + " x=" + x + " z=" + z + "]";

            long mediumSeed = findProvinceSeed(fixture, x, z, ProvinceAuthority.Province.WARM_MEDIUM);
            ProvinceAuthority original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(mediumSeed, fixture.radius()));
            try {
                assertEquals(ProvinceAuthority.Province.WARM_MEDIUM,
                        LatitudeBiomes.classifyProvince(x, z),
                        "swapped WARM_MEDIUM authority is live at the anchor" + context);
                boolean savannaOwnsColumn =
                        LatitudeBiomes.savannaCountryHitForPolicyTest(x, z)
                                || LatitudeBiomes.savannaDryFringeHitForPolicyTest(x, z);
                assertWarmMediumStaleJungleFinish(
                        pickRegistry(fixture, x, z, fixture.sampler()), savannaOwnsColumn,
                        "registry WARM_MEDIUM stale jungle coverage" + context);
                assertWarmMediumStaleJungleFinish(
                        pickCollection(fixture, x, z, fixture.sampler()), savannaOwnsColumn,
                        "collection WARM_MEDIUM stale jungle coverage" + context);
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            long drySeed = findProvinceSeed(fixture, x, z, ProvinceAuthority.Province.WARM_DRY);
            original = LatitudeBiomes.swapProvinceAuthorityForTest(
                    new ProvinceAuthority(drySeed, fixture.radius()));
            try {
                assertEquals(ProvinceAuthority.Province.WARM_DRY,
                        LatitudeBiomes.classifyProvince(x, z),
                        "swapped WARM_DRY authority is live at the anchor" + context);
                assertDryWarmFamily(pickRegistry(fixture, x, z, fixture.sampler()),
                        "registry WARM_DRY stale jungle coverage finishes as dry-warm family" + context);
                assertDryWarmFamily(pickCollection(fixture, x, z, fixture.sampler()),
                        "collection WARM_DRY stale jungle coverage finishes as dry-warm family" + context);
            } finally {
                LatitudeBiomes.restoreProvinceAuthorityForTest(original);
            }

            assertSameBiome(exact, pickRegistry(fixture, x, z, fixture.sampler()),
                    "registry restored WARM_WET retains the exact saved jungle identity" + context);
            assertSameBiome(exact, pickCollection(fixture, x, z, fixture.sampler()),
                    "collection restored WARM_WET retains the exact saved jungle identity" + context);
        }
    }

    /**
     * What a WARM_MEDIUM column carrying a stale jungle coverage identity is allowed to finish as.
     *
     * <p>WHY THIS IS NOT SIMPLY {@link #warmMediumExpectedFamily}. That helper predicts what the
     * province ENFORCER answers, and it is exactly right wherever the enforcer is what decides the
     * column — which is why the two physical-lowland/upland call sites still use it. On THIS route
     * the enforcer is not always what decides, and the reason is a specific, traceable consequence
     * of the savanna-country slice (2026-08-18):
     *
     * <ol>
     *   <li>{@code minecraft:forest} became pool-legal in the tropical band, because the belt's new
     *       staple cannot survive {@code enforceLandBandPool} otherwise. A column whose tag pick was
     *       already forest is therefore no longer rerolled into the jungle family — it now reaches
     *       the final clamp still holding forest.</li>
     *   <li>{@code applyFinalSavannaClimateClamp} only performs the province rewrite when
     *       {@code allowWarmMediumSavannaClamp} passes, and that allowlist is savanna-family,
     *       plains and sunflower_plains. Forest is none of them, so the enforcer is never called
     *       and the country is never asked.</li>
     *   <li>{@code gateWarmJungleSurvival} — the other place the country is consulted — early
     *       returns on {@code !isJungleFamily(out)}, and forest is not jungle-family, so it does not
     *       fire either.</li>
     * </ol>
     *
     * <p>The column therefore finishes forest even INSIDE a savanna country. That is the dilution
     * the 26.2 line measured and accepted rather than fixed (inside-country savanna 85-93%, not
     * 100%), documented on the enforcer's WARM_MEDIUM arm as accepted leak (b). It is the country
     * system being bypassed, not a picker that forgot to ask.
     *
     * <p>So this asserts the three things that ARE absolute for such a column, and deliberately
     * makes no per-column positive claim it cannot honestly support:
     *
     * <ol>
     *   <li>the stale jungle identity is REJECTED — the invariant this method is named for, and the
     *       one the WARM_WET half restores at the end;</li>
     *   <li>a WARM_MEDIUM province never finishes arid — the province still governs the column even
     *       when the country does not;</li>
     *   <li>savanna is a REGION: a column savanna does not own must never finish savanna-family.</li>
     * </ol>
     *
     * <p>(3) is the direction with teeth against the regression this proof exists to catch — a
     * picker that stamps savanna across the whole warm belt again would fail it on the first
     * unowned anchor. The opposite direction (that the country is consulted AT ALL, rather than the
     * belt being answered forest everywhere) is pinned structurally in
     * {@link #warmBeltIdentityIsProvinceOrdered}, at all four consult points and in both picker
     * overloads, so it is not restated here as a per-column assertion that the bypass above would
     * make false. Widening this to "savanna or forest, either is fine" WOULD have been the wrong
     * fix: it passes on a picker that never consults the country.
     *
     * <p>ONE ACCEPTED ESCAPE, named so a future failure is diagnosed in seconds rather than
     * rediscovered: the equatorial demote gates hand back savanna country-blind (accepted leak (a)
     * on the same arm), so an ARID pick reaching them in the tropics finishes savanna regardless of
     * ownership. That cannot happen on this route — the tropical band pool holds no arid identity
     * and {@code sanitizeLandBiome} routes any warm-family pick through the enforcer first — but if
     * (3) ever fails with {@code minecraft:savanna} on an unowned column, that gate is where to
     * look, not the country field.
     */
    private static void assertWarmMediumStaleJungleFinish(
            Holder<Biome> actual, boolean savannaOwnsColumn, String message) {
        String id = LatitudeBiomes.biomeIdPublic(actual);
        BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
        String where = ": actual=" + id + " savannaOwnsColumn=" + savannaOwnsColumn;
        assertTrue(descriptor != null,
                message + " must finish on a ledger-admitted biome" + where);
        assertFalse(descriptor.family() == BiomeDescriptorLedger.Family.JUNGLE,
                message + " must REJECT the stale jungle identity — a jungle-family finish means "
                        + "the coverage plan re-admitted an identity its own province forbids"
                        + where);
        assertFalse(descriptor.family() == BiomeDescriptorLedger.Family.ARID,
                message + " must not finish arid — a WARM_MEDIUM province still governs the column, "
                        + "and the tropical band bans arid outright" + where);
        if (!savannaOwnsColumn) {
            assertFalse(descriptor.family() == BiomeDescriptorLedger.Family.SAVANNA,
                    message + " must not finish savanna-family on a column savanna does not own — "
                            + "savanna is a COUNTRY plus the dry fringe, not the whole warm belt "
                            + "(maintainer approval, 2026-08-18), and a savanna here is the belt-wide "
                            + "monoculture that slice removed coming back" + where);
        }
    }

    private static long findProvinceSeed(CoverageFixture fixture, int x, int z,
                                         ProvinceAuthority.Province expected) {
        for (long candidate : AUDIT_SEEDS) {
            if (new ProvinceAuthority(candidate, fixture.radius()).classify(x, z) == expected) return candidate;
        }
        for (long candidate = 1; candidate <= 10_000; candidate++) {
            if (new ProvinceAuthority(candidate, fixture.radius()).classify(x, z) == expected) return candidate;
        }
        throw new AssertionError("no deterministic seed produced " + expected + " at " + x + "," + z);
    }

    private static void assertIncompatibleCoverageEvidenceIsRejected(CoverageFixture fixture) {
        VanillaBiomeCoveragePlan.Anchor wetland = fixture.plan().anchors().stream()
                .filter(anchor -> anchor.route() == BiomeRoute.TEMPERATE_WETLAND)
                .findFirst().orElseThrow();
        Climate.Sampler failedWetland = fixedTerrainSampler(0.95, 0.20, 0.0);
        assertNotSameBiome(wetland.biomeId(),
                pickRegistry(fixture, wetland.blockX(), wetland.blockZ(), failedWetland),
                "registry rejects saved wetland when real wetland evidence fails");
        assertNotSameBiome(wetland.biomeId(),
                pickCollection(fixture, wetland.blockX(), wetland.blockZ(), failedWetland),
                "collection rejects saved wetland when real wetland evidence fails");

        VanillaBiomeCoveragePlan.Anchor upland = fixture.plan().anchors().stream()
                .filter(anchor -> isUplandRoute(anchor.route()))
                .findFirst().orElseThrow();
        Climate.Sampler lowlandOnly = fixedTerrainSampler(0.30, 0.20, 0.0);
        assertNotSameBiome(upland.biomeId(),
                pickRegistry(fixture, upland.blockX(), upland.blockZ(), lowlandOnly),
                "registry rejects an upland reservation on lowland evidence");
        assertNotSameBiome(upland.biomeId(),
                pickCollection(fixture, upland.blockX(), upland.blockZ(), lowlandOnly),
                "collection rejects an upland reservation on lowland evidence");

        VanillaBiomeCoveragePlan.Anchor tropical = fixture.plan().anchors().stream()
                .filter(anchor -> anchor.route() == BiomeRoute.TROPICAL_HUMID_LOWLAND)
                .findFirst().orElseThrow();
        int wrongLatitudeRadius = Math.max(512, Math.abs(tropical.blockZ()) * 2);
        LatitudeBiomes.setRadius(wrongLatitudeRadius);
        assertNotSameBiome(tropical.biomeId(),
                pickRegistry(fixture, tropical.blockX(), tropical.blockZ(), fixture.sampler()),
                "registry rejects a saved jungle reservation after its latitude becomes incompatible");
        assertNotSameBiome(tropical.biomeId(),
                pickCollection(fixture, tropical.blockX(), tropical.blockZ(), fixture.sampler()),
                "collection rejects a saved jungle reservation after its latitude becomes incompatible");
        LatitudeBiomes.setRadius(fixture.radius());
        LatitudeBiomes.setWorldSeed(fixture.seed());
    }

    private static void assertHumidCoverageEvidenceIsRejected(CoverageFixture fixture) {
        VanillaBiomeCoveragePlan.Anchor tropical = fixture.plan().anchors().stream()
                .filter(anchor -> anchor.route() == BiomeRoute.TROPICAL_HUMID_LOWLAND)
                .findFirst().orElseThrow();
        int wrongLatitudeRadius = Math.max(512, Math.abs(tropical.blockZ()) * 2);
        LatitudeBiomes.setRadius(wrongLatitudeRadius);
        assertNotSameBiome(tropical.biomeId(),
                pickRegistry(fixture, tropical.blockX(), tropical.blockZ(), fixture.sampler()),
                "registry rejects a saved jungle reservation after its latitude becomes incompatible");
        assertNotSameBiome(tropical.biomeId(),
                pickCollection(fixture, tropical.blockX(), tropical.blockZ(), fixture.sampler()),
                "collection rejects a saved jungle reservation after its latitude becomes incompatible");
        LatitudeBiomes.setRadius(fixture.radius());
        LatitudeBiomes.setWorldSeed(fixture.seed());

        // Ordinary WARM_WET composition may legitimately produce the anchor's own identity at the
        // anchor coords (the plan placed the anchor where the equator pool favors it), so the
        // rejection proof observes the coverage ADMISSION, not the final identity: upland evidence
        // must prevent the reservation from being applied.
        Climate.Sampler mountainEvidence = fixedTerrainSampler(0.30, -0.60, 0.70);
        Holder<Biome> uplandRegistry =
                pickRegistry(fixture, tropical.blockX(), tropical.blockZ(), mountainEvidence);
        assertFalse(LatitudeBiomes.hasExactVanillaCoverageAdmissionForTest(uplandRegistry),
                "registry rejects a saved humid lowland reservation on upland evidence");
        Holder<Biome> uplandCollection =
                pickCollection(fixture, tropical.blockX(), tropical.blockZ(), mountainEvidence);
        assertFalse(LatitudeBiomes.hasExactVanillaCoverageAdmissionForTest(uplandCollection),
                "collection rejects a saved humid lowland reservation on upland evidence");
    }

    private static void assertExistingWetlandAndPaleGardenProtection(CoverageFixture fixture) {
        Holder<Biome> swamp = holder(fixture.registry(), "minecraft:swamp");
        Holder<Biome> mangrove = holder(fixture.registry(), "minecraft:mangrove_swamp");
        Holder<Biome> paleGarden = holder(fixture.registry(), "minecraft:pale_garden");
        VanillaBiomeCoveragePlan.Anchor unrelated = new VanillaBiomeCoveragePlan.Anchor(
                "minecraft:desert", BiomeRoute.ARID_LOWLAND, 0, 0, 96, 1L);
        VanillaBiomeCoveragePlan.Anchor matchingWetland = new VanillaBiomeCoveragePlan.Anchor(
                "minecraft:swamp", BiomeRoute.TEMPERATE_WETLAND, 0, 0, 96, 2L);
        ProvinceAuthority guardOriginal = LatitudeBiomes.swapProvinceAuthorityForTest(null);
        try {
            // Null authority = wetland-eligible everywhere: the protective semantics hold.
            assertFalse(LatitudeBiomes.landCoverageMayReplace(swamp, unrelated, 0, 0),
                    "unrelated land coverage cannot erase an existing swamp");
            assertFalse(LatitudeBiomes.landCoverageMayReplace(mangrove, unrelated, 0, 0),
                    "unrelated land coverage cannot erase an existing mangrove");
            assertFalse(LatitudeBiomes.landCoverageMayReplace(paleGarden, unrelated, 0, 0),
                    "land coverage cannot erase Pale Garden");
            assertTrue(LatitudeBiomes.landCoverageMayReplace(swamp, matchingWetland, 0, 0),
                    "matching wetland coverage may restore its saved wetland identity");
            assertTrue(LatitudeBiomes.landCoverageMayReplace(mangrove, matchingWetland, 0, 0),
                    "matching wetland coverage may replace mangrove with the saved wetland identity");
        } finally {
            LatitudeBiomes.restoreProvinceAuthorityForTest(guardOriginal);
        }

        // At a dry-province column the wetland is doomed by the dry-province law, so it yields
        // to a real reserved identity instead of blocking it (26.2 parity port, 2026-08-16).
        int dryX = 400;
        int dryZ = 3_200;
        long drySeed = findProvinceSeed(
                fixture.radius(), dryX, dryZ, ProvinceAuthority.Province.WARM_DRY);
        guardOriginal = LatitudeBiomes.swapProvinceAuthorityForTest(
                new ProvinceAuthority(drySeed, fixture.radius()));
        try {
            assertTrue(LatitudeBiomes.landCoverageMayReplace(swamp, unrelated, dryX, dryZ),
                    "a dry-province swamp yields to a reserved land identity");
            assertTrue(LatitudeBiomes.landCoverageMayReplace(mangrove, unrelated, dryX, dryZ),
                    "a dry-province mangrove yields to a reserved land identity");
            assertFalse(LatitudeBiomes.landCoverageMayReplace(paleGarden, unrelated, dryX, dryZ),
                    "Pale Garden protection is not wetland law and does not yield");
        } finally {
            LatitudeBiomes.restoreProvinceAuthorityForTest(guardOriginal);
        }
    }

    private static List<int[]> coverageSamples(VanillaBiomeCoveragePlan.Anchor anchor) {
        int shoulder = anchor.radiusBlocks() / 2;
        return List.of(
                new int[]{anchor.blockX(), anchor.blockZ()},
                new int[]{anchor.blockX() + shoulder, anchor.blockZ()},
                new int[]{anchor.blockX() - shoulder, anchor.blockZ()},
                new int[]{anchor.blockX(), anchor.blockZ() + shoulder},
                new int[]{anchor.blockX(), anchor.blockZ() - shoulder});
    }

    private static Holder<Biome> pickRegistry(CoverageFixture fixture, int x, int z,
                                               Climate.Sampler sampler) {
        return LatitudeBiomes.pick(fixture.registry(), fixture.plains(), x, z, 63,
                fixture.radius(), sampler, "ATLAS_SAMPLER");
    }

    private static Holder<Biome> pickCollection(CoverageFixture fixture, int x, int z,
                                                 Climate.Sampler sampler) {
        return LatitudeBiomes.pick(fixture.holders(), fixture.plains(), x, z, 63,
                fixture.radius(), sampler, "ATLAS_SAMPLER");
    }

    /**
     * WARM_DRY's final identity spans ARID and SAVANNA: the deep-equator arid law
     * (demoteEquatorialBadlands/demoteEquatorialDesert) lawfully demotes badlands and part of the
     * desert share to savanna below the equatorial ramp, so a WARM_DRY anchor deep in the tropics
     * finishes savanna-family while anchors above the ramp finish arid-family. This mirrors
     * production's own isDryWarmIdentity (savanna | badlands | desert).
     */
    private static void assertDryWarmFamily(Holder<Biome> actual, String message) {
        BiomeDescriptorLedger.Descriptor descriptor =
                BiomeDescriptorLedger.descriptor(LatitudeBiomes.biomeIdPublic(actual));
        assertTrue(descriptor != null
                        && (descriptor.family() == BiomeDescriptorLedger.Family.ARID
                        || descriptor.family() == BiomeDescriptorLedger.Family.SAVANNA),
                message + ": actual=" + LatitudeBiomes.biomeIdPublic(actual));
    }

    private static void assertSameBiome(Holder<Biome> expected, Holder<Biome> actual, String message) {
        assertEquals(LatitudeBiomes.biomeIdPublic(expected), LatitudeBiomes.biomeIdPublic(actual), message);
    }

    private static void assertNotSameBiome(String unexpectedId, Holder<Biome> actual, String message) {
        assertFalse(unexpectedId.equals(LatitudeBiomes.biomeIdPublic(actual)),
                message + ": actual=" + LatitudeBiomes.biomeIdPublic(actual));
    }

    private static TestBiomeRegistry buildTestBiomeRegistry(List<String> providerIds) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TreeSet<String> ids = new TreeSet<>(providerIds);
        ids.addAll(VanillaBiomeCoveragePlan.requiredRoutes().keySet());
        ids.addAll(VanillaSurfaceWaterCoveragePlan.requirements().keySet());
        ids.addAll(List.of(
                "minecraft:pale_garden", "minecraft:mangrove_swamp", "minecraft:mushroom_fields",
                "minecraft:river", "minecraft:frozen_river", "minecraft:beach",
                "minecraft:snowy_beach", "minecraft:stony_shore"));
        MappedRegistry<Biome> writable = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        for (String id : ids) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id));
            writable.register(key, minimalBiome(), RegistrationInfo.BUILT_IN);
        }
        writable.bindAllTagsToEmpty();
        List<Holder<Biome>> temperateMountainHolders = new ArrayList<>();
        for (String id : List.of(
                "minecraft:meadow",
                "minecraft:cherry_grove",
                "minecraft:grove",
                "minecraft:windswept_hills",
                "minecraft:windswept_forest",
                "minecraft:windswept_gravelly_hills",
                "minecraft:stony_peaks",
                "terralith:caldera",
                "byg:howling_peaks",
                "byg:skyris_vale",
                "byg:dacite_ridges",
                "byg:crag_gardens",
                "terrestria:caldera",
                "terrestria:canyon")) {
            writable.get(ResourceLocation.parse(id)).ifPresent(temperateMountainHolders::add);
        }
        writable.bindTag(
                TagKey.create(
                        Registries.BIOME,
                        ResourceLocation.fromNamespaceAndPath("globe", "lat_temperate_mountain")),
                temperateMountainHolders);
        Registry<Biome> registry = writable.freeze();
        List<Holder<Biome>> holders = registry.holders()
                .map(holder -> (Holder<Biome>) holder)
                .toList();
        return new TestBiomeRegistry(registry, holders);
    }

    private static Biome minimalBiome() {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.8F)
                .downfall(0.4F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3f76e4).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }

    private static Holder<Biome> holder(Registry<Biome> registry, String id) {
        return registry.get(ResourceLocation.parse(id))
                .orElseThrow(() -> new AssertionError("missing test biome holder: " + id));
    }

    private static Climate.Sampler stripedTerrainSampler() {
        return new Climate.Sampler(
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.constant(0.30),
                new StripeDensity(false),
                DensityFunctions.zero(),
                new StripeDensity(true),
                List.of());
    }

    private static Climate.Sampler fixedTerrainSampler(double continentalness, double erosion,
                                                        double weirdness) {
        return new Climate.Sampler(
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.constant(continentalness),
                DensityFunctions.constant(erosion),
                DensityFunctions.zero(),
                DensityFunctions.constant(weirdness),
                List.of());
    }

    private static boolean syntheticUpland(int blockX) {
        return Math.floorMod(Math.floorDiv(blockX, 1_024), 2) == 1;
    }

    private static boolean isSyntheticMountain(Climate.Sampler sampler, int blockX, int blockZ) {
        Climate.TargetPoint point = sampler.sample(blockX >> 2, 64 >> 2, blockZ >> 2);
        return Climate.unquantizeCoord(point.continentalness()) > 0.10
                && Climate.unquantizeCoord(point.erosion()) < -0.25
                && Math.abs(Climate.unquantizeCoord(point.weirdness())) > 0.25;
    }

    private record StripeDensity(boolean weirdness) implements DensityFunction {
        @Override
        public double compute(FunctionContext context) {
            boolean upland = syntheticUpland(context.blockX() << 2);
            if (!upland) return weirdness ? 0.0 : 0.20;
            return weirdness ? 0.70 : -0.60;
        }

        @Override
        public void fillArray(double[] values, ContextProvider contextProvider) {
            for (int i = 0; i < values.length; i++) values[i] = compute(contextProvider.forIndex(i));
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(this);
        }

        @Override public double minValue() { return -0.60; }
        @Override public double maxValue() { return 0.70; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return DensityFunctions.zero().codec();
        }
    }

    private record TestBiomeRegistry(Registry<Biome> registry, List<Holder<Biome>> holders) {}

    private record CoverageFixture(
            int radius,
            long seed,
            VanillaBiomeRepresentationProfile representation,
            VanillaBiomeCoveragePlan plan,
            Climate.Sampler sampler,
            Registry<Biome> registry,
            List<Holder<Biome>> holders,
            Holder<Biome> plains) {}

    private static void caveCoverageIsClosedAndWorldSizeSafe() throws Exception {
        // Three, not 26.2's four: minecraft:sulfur_caves does not exist on Minecraft 1.21.11.
        // Requiring it made CaveBiomeRepresentationProfile.validate() throw and hard-crashed world
        // creation, while this suite stayed green because its synthetic registry was built from a
        // ledger that still carried the 26.2 entry. Both are corrected; this pins the trap.
        List<String> required = List.of(
                "minecraft:deep_dark", "minecraft:dripstone_caves",
                "minecraft:lush_caves");
        assertEquals(Set.copyOf(required), CaveBiomeRepresentationProfile.mandatoryIds().keySet(),
                "the native cave identities available on this target are mandatory, exact, and closed");
        assertTrue(!CaveBiomeRepresentationProfile.mandatoryIds().containsKey("minecraft:sulfur_caves"),
                "a biome absent from this Minecraft version can never be a mandatory identity");
        assertTrue(BiomeDescriptorLedger.descriptor("minecraft:sulfur_caves") == null,
                "the descriptor ledger must not model biomes this target's registry does not have");

        BiomeSelectionProfile combined = BiomeSelectionProfile.capture(
                registryFor(Set.of("biomesoplenty", "terralith")));
        for (String id : required) {
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
            assertTrue(descriptor != null && descriptor.terrain() == BiomeDescriptorLedger.Terrain.CAVE
                            && descriptor.water() == BiomeDescriptorLedger.Water.UNDERGROUND,
                    "native cave identity has one explicit underground descriptor: " + id);
        }
        assertTrue(BiomeDescriptorLedger.descriptor("biomesoplenty:crystalline_chasm") == null,
                "a name-only BOP cave is excluded without verified cave-tag evidence");
        assertTrue(BiomeDescriptorLedger.descriptor("terralith:amethyst_canyon") == null,
                "a surface Terralith biome cannot enter through cave routing");
        assertEquals(13L, combined.entries(BiomeRoute.CAVE_SHALLOW).stream()
                        .filter(id -> !id.startsWith("minecraft:")).count()
                        + combined.entries(BiomeRoute.CAVE_DEEP).stream()
                        .filter(id -> !id.startsWith("minecraft:")).count(),
                "only the explicit 2 BOP and 11 Terralith caves are eligible custom candidates");

        int[] radii = {3_750, 5_000, 7_500, 10_000, 15_000, 20_000};
        long[] seeds = {3L, 41L, 131L, 461L};
        for (int radius : radii) {
            for (long seed : seeds) {
                CaveBiomeRepresentationProfile profile = CaveBiomeRepresentationProfile.capture(radius, combined);
                assertEquals(profile.encode(), CaveBiomeRepresentationProfile.decode(profile.encode()).encode(),
                        "cave profile is reload-stable at radius=" + radius + " seed=" + seed);
                CaveBiomeCoveragePlan plan = CaveBiomeCoveragePlan.build(radius, seed, profile,
                        (route, x, y, z) -> (long) x * x + (long) z * z < (long) radius * radius
                                && y <= 96 && (route != BiomeRoute.CAVE_DEEP || y <= -16));
                assertTrue(plan.complete(), "all mandatory and birth-present custom cave targets fit at radius="
                        + radius + " seed=" + seed + " missing=" + plan.missingBiomeIds());
                assertTrue(plan.anchors().stream().map(CaveBiomeCoveragePlan.Anchor::biomeId)
                                .collect(java.util.stream.Collectors.toSet()).containsAll(required),
                        "every native cave identity has a real-cave anchor at every world size");
                for (CaveBiomeCoveragePlan.Anchor anchor : plan.anchors()) {
                    assertTrue(anchor.y() <= 96, "cave reservation never reaches the surface: " + anchor.biomeId());
                    assertTrue(anchor.route() != BiomeRoute.CAVE_DEEP || anchor.y() <= -16,
                            "Deep Dark/deep custom caves stay below the deep threshold: " + anchor.biomeId());
                    assertEquals(anchor, plan.match(anchor.x(), anchor.y(), anchor.z()),
                            "each cave identity is reachable at its actual underground anchor");
                    assertTrue(anchor.horizontalRadius() >= 80 && anchor.verticalRadius() == 24,
                            "cave coverage is a compact underground region, not a one-cell token");
                }
                CaveBiomeCoveragePlan.Anchor locateAnchor = plan.anchors().get(0);
                assertEquals(locateAnchor, plan.nearestAnchorFor(
                                Set.of(locateAnchor.biomeId()),
                                locateAnchor.x(), locateAnchor.z(), 0),
                        "a cave reservation is directly locatable at its exact planned column");
                assertTrue(plan.nearestAnchorFor(
                                Set.of(locateAnchor.biomeId()),
                                locateAnchor.x() + 100, locateAnchor.z(), 99) == null,
                        "cave planned locate preserves the caller's horizontal radius");
                assertEquals(locateAnchor, plan.nearestAnchorFor(
                                Set.of(locateAnchor.biomeId()),
                                locateAnchor.x() + 100, locateAnchor.z(), 100),
                        "the exact cave locate radius boundary is inclusive");
                assertTrue(plan.nearestAnchorFor(
                                Set.of("minecraft:the_void"), 0, 0, radius) == null,
                        "planned cave locate cannot invent an unreserved identity");
                Map<String, BiomeRoute> showcase = profile.customShowcaseTargets(seed);
                assertEquals(3, showcase.size(), "combined stack contributes one deterministic BOP shallow and Terralith shallow/deep showcase each");
                assertTrue(showcase.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("biomesoplenty:")
                                && entry.getValue() == BiomeRoute.CAVE_SHALLOW),
                        "BOP cave selection remains shallow-only");
                assertTrue(showcase.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("terralith:")
                                && entry.getValue() == BiomeRoute.CAVE_DEEP),
                        "Terralith deep cave selection remains below the deep threshold");
            }
        }

        String biomes = Files.readString(Path.of("src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        String source = Files.readString(Path.of("src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        String mixin = Files.readString(Path.of("src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java"));
        String state = Files.readString(Path.of("src/main/java/com/example/globe/world/LatitudeWorldState.java"));
        String mod = Files.readString(Path.of("src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(biomes.contains("ACTIVE_CAVE_COVERAGE_PLAN")
                        && biomes.contains("isUndergroundCaveBiome(current)")
                        && biomes.contains("blockY <= 96"),
                "the V5 final-admission override is donor-cave-gated and cannot create a surface cave");
        assertTrue(source.contains("LatitudeBiomes.caveCoverageOverride")
                        && mixin.contains("return LatitudeBiomes.caveCoverageOverride(biomes, current, blockX, blockY, blockZ);"),
                "biome-source, locate-preview, and chunk-population paths share final cave identity");
        assertTrue(state.contains("PROVIDER_TICKET_V1") && state.contains("PROVIDER_TICKET_V2_COVERAGE")
                        && state.contains("PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE")
                        && state.contains("PROVIDER_TICKET_V4_CAVE_COVERAGE")
                        && state.contains("PROVIDER_TICKET_V5_FINAL_ADMISSION"),
                "legacy/V1/V2/V3/V4 policies remain explicit alongside fresh-only V5");
        assertTrue(mod.contains("CaveBiomeRepresentationProfile.capture(captureRadius, profile)")
                        && mod.contains("PROVIDER_TICKET_V5_FINAL_ADMISSION"),
                "only a newly created or adopted fresh world captures the V5 cave profile before spawn generation");
    }

    private static void vanillaCoverageIsV2OnlyAndClearsWithContext() throws Exception {
        String biomes = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeBiomes.java"))
                .replaceAll("\\s+", " ");
        String state = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeWorldState.java"))
                .replaceAll("\\s+", " ");
        String mod = Files.readString(Path.of("src/main/java/com/example/globe/GlobeMod.java"))
                .replaceAll("\\s+", " ");
        assertTrue(biomes.contains("boolean exactV2 = ACTIVE_WORLDGEN_POLICY == WorldgenPolicyVersion.PROVIDER_TICKET_V2_COVERAGE")
                        && biomes.contains("PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE")
                        && biomes.contains("PROVIDER_TICKET_V4_CAVE_COVERAGE")
                        && biomes.contains("PROVIDER_TICKET_V5_FINAL_ADMISSION"),
                "V2 keeps exact coverage while V3/V4/V5 consume the saved size-aware surface contract");
        assertTrue(biomes.contains("ACTIVE_VANILLA_COVERAGE_PLAN = null;"),
                "world/context cleanup clears the coverage plan");
        assertTrue(biomes.contains("ACTIVE_SURFACE_WATER_COVERAGE_PLAN = null;"),
                "world/context cleanup clears the surface/water coverage plan");
        assertTrue(biomes.contains("ACTIVE_CAVE_COVERAGE_PLAN = null;"),
                "world/context cleanup clears birth-locked cave coverage");
        assertTrue(state.contains("policy == WorldgenPolicyVersion.PROVIDER_TICKET_V1")
                        && state.contains("policy == WorldgenPolicyVersion.PROVIDER_TICKET_V2_COVERAGE")
                        && state.contains("policy == WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE")
                        && state.contains("policy == WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE"),
                "saved V1/V2/V3/V4 profiles remain readable without adopting new V5 behavior");
        assertTrue(mod.contains(
                        "WorldgenPolicyVersion.PROVIDER_TICKET_V5_FINAL_ADMISSION"),
                "only freshly UI-created worlds opt into V5 size-aware coverage");
        assertTrue(mod.contains("randomState().sampler()"),
                "fresh and reloaded V2 plans use the live world climate sampler");
        assertTrue(mod.contains("donorBiomeSource(generator)"),
                "surface/water plans resolve against the original donor geography");
    }

    private static void sizeAwareVanillaRepresentationIsClosedAndBirthLocked() throws Exception {
        BiomeSelectionProfile providers = BiomeSelectionProfile.capture(
                registryFor(Set.of("biomesoplenty", "terralith")));
        Set<String> classifiedSurface = new HashSet<>(VanillaBiomeCoveragePlan.requiredRoutes().keySet());
        classifiedSurface.addAll(VanillaSurfaceWaterCoveragePlan.requirements().keySet());
        classifiedSurface.add("minecraft:pale_garden");
        assertEquals(51, classifiedSurface.size(),
                "all 51 vanilla Overworld surface identities are classified by land, water, or Pale Garden authority");
        assertEquals(Set.of("minecraft:deep_dark", "minecraft:dripstone_caves",
                        "minecraft:lush_caves", "minecraft:sulfur_caves"),
                VanillaBiomeRepresentationProfile.nativeUndergroundIds(),
                "the four underground identities are explicitly classified outside the surface planner");

        int[] radii = {3_750, 5_000, 7_500, 10_000, 15_000, 20_000};
        long[] seeds = {3L, 41L, 131L, 461L};
        boolean customRepresentativeObserved = false;
        for (int radius : radii) {
            for (long seed : seeds) {
                VanillaBiomeRepresentationProfile profile =
                        VanillaBiomeRepresentationProfile.capture(radius, seed, providers);
                VanillaBiomeRepresentationProfile reloaded =
                        VanillaBiomeRepresentationProfile.decode(profile.encode());
                assertEquals(profile.encode(), reloaded.encode(),
                        "representation profile is byte-stable through reload");
                assertTrue(profile.landTargets().keySet().containsAll(
                                VanillaBiomeRepresentationProfile.mandatoryLandIds()),
                        "mandatory exact gameplay identities are never omitted");
                assertTrue(profile.hasCherryGameplayRepresentation(),
                        "Cherry Grove is exact or replaced only by a verified cherry-resource counterpart");
                assertEquals(VanillaSurfaceWaterCoveragePlan.requirements(),
                        profile.surfaceWaterTargets(),
                        "surface/water hard authorities stay exact at every size");

                Set<String> accounted = new HashSet<>(profile.landTargets().keySet());
                accounted.addAll(profile.omittedExactIds().keySet());
                assertTrue(accounted.containsAll(VanillaBiomeCoveragePlan.requiredRoutes().keySet()),
                        "every old exact land target is selected or truthfully reported as omitted");
                for (Map.Entry<String, VanillaBiomeRepresentationProfile.Omission> omission
                        : profile.omittedExactIds().entrySet()) {
                    VanillaBiomeRepresentationProfile.Omission decision = omission.getValue();
                    String replacement = decision.representativeId();
                    assertTrue(profile.landTargets().containsKey(replacement),
                            "every omission names a selected representative");
                    assertTrue(VanillaBiomeRepresentationProfile.areApprovedEquivalents(
                                    omission.getKey(), decision),
                            "every compact compromise belongs to one closed, explicit equivalence group");
                    if (decision.compromise()
                            == VanillaBiomeRepresentationProfile.Compromise.APPROVED_GAMEPLAY_COUNTERPART
                            && !"minecraft:cherry_grove".equals(omission.getKey())) {
                        assertEquals(VanillaBiomeCoveragePlan.requiredRoutes().get(omission.getKey()),
                                profile.landTargets().get(replacement),
                                "a gameplay counterpart stays in the omitted biome's physical route");
                    }
                }
                if (profile.worldSize().compact()) {
                    assertTrue(profile.landTargets().size() <= profile.worldSize().compactLandBudget(),
                            "compact targets respect the predeclared capacity budget");
                    assertTrue(!profile.omittedExactIds().isEmpty(),
                            "compact profiles report their exact-ID compromises");
                    customRepresentativeObserved |= profile.targetTiers().entrySet().stream()
                            .anyMatch(entry -> entry.getValue()
                                    == VanillaBiomeRepresentationProfile.Tier.REPRESENTATION_FAMILY
                                    && !entry.getKey().startsWith("minecraft:"));
                } else {
                    assertEquals(VanillaBiomeCoveragePlan.requiredRoutes(), profile.landTargets(),
                            "Regular/Large/Massive retain every exact V2 land target");
                    assertTrue(profile.omittedExactIds().isEmpty(),
                            "full-size profiles do not claim omissions");
                }

                VanillaBiomeCoveragePlan plan = VanillaBiomeCoveragePlan.build(
                        radius, seed, providers, profile.landTargets(), profile.worldSize().compact(),
                        (id, route, x, z) -> insideSyntheticRoute(route, x, z, radius));
                assertTrue(plan.complete(),
                        "size-aware land targets fit as coherent provinces: " + plan.missingBiomeIds());
                assertEquals(profile.landTargets().keySet(),
                        plan.anchors().stream().map(VanillaBiomeCoveragePlan.Anchor::biomeId)
                                .collect(java.util.stream.Collectors.toSet()),
                        "the actual planner realizes exactly the saved land targets");
                if (profile.worldSize().compact()) {
                    for (VanillaBiomeCoveragePlan.Anchor anchor : plan.anchors()) {
                        if (anchor.route() == BiomeRoute.ARID_LOWLAND) {
                            assertTrue(anchor.radiusBlocks() <= 160,
                                    "compact Desert and Badlands-family reservations leave room "
                                            + "for distinct substantial cores");
                        }
                    }
                }
            }
        }
        assertTrue(customRepresentativeObserved,
                "an explicitly approved custom variant can represent its vanilla family");
        assertTrue(VanillaBiomeRepresentationProfile.areApprovedEquivalents(
                        "minecraft:savanna",
                        new VanillaBiomeRepresentationProfile.Omission(
                                "biomesoplenty:lush_savanna",
                                VanillaBiomeRepresentationProfile.Compromise.REPRESENTATION_FAMILY)),
                "BOP Lush Savanna is an explicit lowland-savanna representative");
        assertTrue(!VanillaBiomeRepresentationProfile.areApprovedEquivalents(
                        "minecraft:savanna",
                        new VanillaBiomeRepresentationProfile.Omission(
                                "terralith:hot_shrubland",
                                VanillaBiomeRepresentationProfile.Compromise.REPRESENTATION_FAMILY)),
                "a biome cannot substitute merely because its descriptor is warm and dry");
        assertTrue(VanillaBiomeRepresentationProfile.areApprovedEquivalents(
                        "minecraft:eroded_badlands",
                        new VanillaBiomeRepresentationProfile.Omission(
                                "minecraft:badlands",
                                VanillaBiomeRepresentationProfile.Compromise.REPRESENTATION_FAMILY)),
                "compact worlds may report an omitted terrain variant when its biome family remains represented");
        VanillaBiomeRepresentationProfile terralithCompact =
                VanillaBiomeRepresentationProfile.capture(5_000, 41L, providers);
        assertTrue(!terralithCompact.landTargets().containsKey("minecraft:cherry_grove")
                        && Set.of("terralith:sakura_grove", "terralith:sakura_valley").stream()
                        .anyMatch(terralithCompact.landTargets()::containsKey),
                "Terralith Sakura terrain supplies the verified compact cherry-resource counterpart");
        VanillaBiomeRepresentationProfile vanillaCompact =
                VanillaBiomeRepresentationProfile.capture(5_000, 41L,
                        BiomeSelectionProfile.capture(registryFor(Set.of("biomesoplenty"))));
        assertTrue(vanillaCompact.landTargets().containsKey("minecraft:cherry_grove")
                        && !vanillaCompact.omittedExactIds().containsKey("minecraft:cherry_grove"),
                "without a verified provider counterpart, vanilla Cherry Grove stays exact");
        LatitudeBiomes.clearWorldgenContext();
        assertTrue(LatitudeBiomes.authoritativeTropicalAridBan(0, 0, 5_000),
                "the final arid guard rejects the canonical tropical band");
        assertTrue(!LatitudeBiomes.authoritativeTropicalAridBan(0, 2_500, 5_000),
                "the final arid guard does not erase valid non-tropical arid geography");

        String state = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/LatitudeWorldState.java"));
        String mod = Files.readString(Path.of("src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(state.contains("vanilla_representation_profile")
                        && state.contains("PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE")
                        && state.contains("PROVIDER_TICKET_V4_CAVE_COVERAGE")
                        && state.contains("PROVIDER_TICKET_V5_FINAL_ADMISSION"),
                "V3/V4 saved profiles remain readable while V5 adds final-admission capture");
        assertTrue(mod.contains("VanillaBiomeRepresentationProfile.capture(captureRadius, seed, profile)")
                        && mod.contains("PROVIDER_TICKET_V5_FINAL_ADMISSION")
                        && mod.contains("boolean creationWindow = world.getGameTime() < 100L;"),
                "only the birth-locked fresh-world path (create screen OR adopted fresh dedicated world, "
                        + "inside the creation window) captures the V5 surface/cave profiles");
        assertThrows(() -> VanillaBiomeRepresentationProfile.decode(
                        VanillaBiomeRepresentationProfile.FORMAT
                                + "\nSIZE|ITTY_BITTY\nLAND|TEMPERATE_LOWLAND|REPRESENTATION_FAMILY|terralith:caldera"),
                "a custom biome cannot be admitted through a mismatched route");
    }

    private static void surfaceWaterCoverageIsCompleteAndWorldSizeSafe() throws Exception {
        Set<String> exactRequired = Set.of(
                "minecraft:ocean", "minecraft:deep_ocean",
                "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
                "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
                "minecraft:warm_ocean", "minecraft:beach", "minecraft:snowy_beach",
                "minecraft:stony_shore", "minecraft:river", "minecraft:frozen_river",
                "minecraft:mangrove_swamp", "minecraft:mushroom_fields");
        assertEquals(exactRequired, VanillaSurfaceWaterCoveragePlan.requirements().keySet(),
                "V2 surface/water contract owns exactly the requested vanilla identities");
        assertTrue(VanillaSurfaceWaterCoveragePlan.verifiedCounterparts().isEmpty(),
                "no custom biome is counted as a vanilla surface/water substitute");

        int[] radii = {3_750, 5_000, 7_500, 10_000, 15_000, 20_000};
        long[] seeds = {3L, 41L, 131L, 461L};
        for (int radius : radii) {
            for (long seed : seeds) {
                VanillaSurfaceWaterCoveragePlan.CandidateEvaluator evaluator =
                        (id, route, x, z) -> insideSyntheticSurfaceWaterRoute(route, x, z, radius);
                VanillaSurfaceWaterCoveragePlan plan = VanillaSurfaceWaterCoveragePlan.build(
                        radius, seed, 63, evaluator);
                VanillaSurfaceWaterCoveragePlan reloaded = VanillaSurfaceWaterCoveragePlan.build(
                        radius, seed, 63, evaluator);
                assertTrue(plan.complete(),
                        "all surface/water identities fit radius=" + radius + " seed=" + seed
                                + " missing=" + plan.missingBiomeIds());
                assertEquals(exactRequired.size(), plan.anchors().size(),
                        "one coherent surface/water province exists per identity");
                assertEquals(plan.stableFingerprint(), reloaded.stableFingerprint(),
                        "reload reconstructs the identical birth-locked surface/water plan");
                Set<String> ids = new HashSet<>();
                for (VanillaSurfaceWaterCoveragePlan.Anchor anchor : plan.anchors()) {
                    assertTrue(ids.add(anchor.biomeId()), "surface/water identities are unique");
                    assertTrue(anchor.radiusBlocks() >= 128,
                            "surface/water coverage is a province, not a token chunk");
                    int expectedRouteScale = switch (anchor.route().family()) {
                        case SHORE, RIVER -> 128;
                        case MANGROVE -> 128;
                        case MUSHROOM -> Math.max(192, Math.min(384, radius / 18));
                        case OCEAN -> Math.max(128, Math.min(320, radius / 18));
                    };
                    assertEquals(expectedRouteScale, anchor.radiusBlocks(),
                            "surface/water reservations use the physical feature's own scale");
                    assertTrue(plan.match(anchor.route().family(), anchor.blockX(), anchor.blockZ()) == anchor,
                            "the actual family-order selector reaches every anchor center");
                    assertTrue(insideSyntheticSurfaceWaterRoute(
                                    anchor.route(), anchor.blockX(), anchor.blockZ(), radius),
                            "surface/water anchor remains in its declared physical route");
                    long centerDistanceSquared = (long) anchor.blockX() * anchor.blockX()
                            + (long) anchor.blockZ() * anchor.blockZ();
                    long safeRadius = radius - anchor.radiusBlocks() - 48L;
                    assertTrue(centerDistanceSquared <= safeRadius * safeRadius,
                            "the whole surface/water province remains inside the border");
                }
                assertEquals(exactRequired, ids, "no required exact ID is omitted");

                VanillaSurfaceWaterCoveragePlan.Anchor mushroom = plan.anchors().stream()
                        .filter(a -> a.route().family() == VanillaSurfaceWaterCoveragePlan.Family.MUSHROOM)
                        .findFirst().orElseThrow();
                VanillaSurfaceWaterCoveragePlan.Anchor plannedLocate = plan.nearestAnchorFor(
                        Set.of("minecraft:mushroom_fields"),
                        -mushroom.blockX(),
                        -mushroom.blockZ());
                assertEquals(mushroom, plannedLocate,
                        "a required planned biome remains directly locatable even when its compact province "
                                + "falls outside the ordinary bounded scan");
                assertTrue(plan.nearestAnchorFor(Set.of("minecraft:the_void"), 0, 0) == null,
                        "planned locate cannot invent an unreserved biome identity");
                assertTrue(plan.mushroomDensity(-1.0, mushroom.blockX(), 64, mushroom.blockZ()) > -1.0,
                        "reserved Mushroom Fields gains real above-water density");
                assertTrue(plan.isMushroomSolid(mushroom.blockX(), 64, mushroom.blockZ()),
                        "reserved Mushroom Fields materializes a solid block above sea level");
                assertTrue(!plan.isMushroomSolid(mushroom.blockX(), 256, mushroom.blockZ()),
                        "reserved Mushroom Fields does not fill air above its planned surface");
                assertEquals(-1.0, plan.mushroomDensity(-1.0,
                                mushroom.blockX() + mushroom.radiusBlocks() * 2, 64, mushroom.blockZ()),
                        "density outside the reserved island remains byte-for-byte unchanged");

                VanillaSurfaceWaterCoveragePlan compactPlan = VanillaSurfaceWaterCoveragePlan.build(
                        radius, seed, 63, VanillaSurfaceWaterCoveragePlan.requirements(),
                        radius <= 7_500, evaluator);
                assertTrue(compactPlan.complete(),
                        "V3 compact surface targets fit without weakening exact identities");
                VanillaSurfaceWaterCoveragePlan.Anchor compactMushroom = compactPlan.anchors().stream()
                        .filter(a -> a.route().family() == VanillaSurfaceWaterCoveragePlan.Family.MUSHROOM)
                        .findFirst().orElseThrow();
                assertEquals(radius <= 7_500
                                ? 128 : Math.max(192, Math.min(384, radius / 18)),
                        compactMushroom.radiusBlocks(),
                        "V3 uses the compact substantial Mushroom Fields footprint only in compact worlds");
                for (VanillaSurfaceWaterCoveragePlan.Anchor anchor : compactPlan.anchors()) {
                    if (radius <= 7_500
                            && anchor.route().family() == VanillaSurfaceWaterCoveragePlan.Family.OCEAN) {
                        assertEquals(96, anchor.radiusBlocks(),
                                "compact ocean variants use a substantial twelve-chunk diameter");
                    }
                }
                double minimumLandEdge = Double.POSITIVE_INFINITY;
                double maximumLandEdge = Double.NEGATIVE_INFINITY;
                for (int bearing = 0; bearing < 24; bearing++) {
                    double angle = bearing * Math.PI * 2.0 / 24.0;
                    double edge = 0.0;
                    for (int distance = 0; distance <= mushroom.radiusBlocks(); distance += 2) {
                        int x = mushroom.blockX() + (int) Math.round(Math.cos(angle) * distance);
                        int z = mushroom.blockZ() + (int) Math.round(Math.sin(angle) * distance);
                        if (plan.isMushroomLand(x, z)) edge = distance;
                    }
                    minimumLandEdge = Math.min(minimumLandEdge, edge);
                    maximumLandEdge = Math.max(maximumLandEdge, edge);
                }
                assertTrue(maximumLandEdge - minimumLandEdge >= mushroom.radiusBlocks() * 0.08,
                        "Mushroom Fields shoreline must be visibly organic, not a circular density stamp");
                for (int dz = -mushroom.radiusBlocks(); dz <= mushroom.radiusBlocks(); dz += 8) {
                    for (int dx = -mushroom.radiusBlocks(); dx <= mushroom.radiusBlocks(); dx += 8) {
                        int x = mushroom.blockX() + dx;
                        int z = mushroom.blockZ() + dz;
                        if (plan.isMushroomLand(x, z)) {
                            assertTrue(plan.mushroomDensity(-100.0, x, 64, z) > 0.0,
                                    "every Mushroom Fields label has solid terrain above sea level");
                            assertTrue(plan.isMushroomSolid(x, 64, z),
                                    "every Mushroom Fields label reaches the live solid-block authority");
                        }
                    }
                }
            }
        }

        String source = Files.readString(Path.of("src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertTrue(source.indexOf("Family.SHORE") < source.indexOf("return out;", source.indexOf("Family.SHORE")),
                "shore coverage executes before the beach early return");
        assertTrue(source.indexOf("Family.RIVER") < source.indexOf("return out;", source.indexOf("Family.RIVER")),
                "river coverage executes before the river early return");
        assertTrue(source.contains("if (ACTIVE_SURFACE_WATER_COVERAGE_PLAN == null)"),
                "legacy random Mushroom Fields behavior is retained only when V2 has no plan");
        assertTrue(LatitudeBiomes.rockyShoreClimateSignal(-0.30, 0.35),
                "low-erosion coastline with strong terrain signal is eligible for Stony Shore");
        assertTrue(!LatitudeBiomes.rockyShoreClimateSignal(-0.10, 0.35),
                "ordinary gently eroded beach is not promoted to Stony Shore");
        assertTrue(!LatitudeBiomes.rockyShoreClimateSignal(-0.30, 0.10),
                "low erosion without strong terrain relief is not enough for Stony Shore");
        assertEquals(-5598, LatitudeLocateBudgetPolicy.quartCenterBlock(-5600),
                "negative locate coordinate returns the exact quart center evaluated by population");
        assertEquals(102, LatitudeLocateBudgetPolicy.quartCenterBlock(100),
                "vertical locate coordinate returns the exact quart center evaluated by population");
        String densityMixin = Files.readString(
                Path.of("src/main/java/com/example/globe/mixin/NoiseChunkMushroomIslandDensityMixin.java"));
        String authorityMixin = Files.readString(
                Path.of("src/main/java/com/example/globe/mixin/NoiseChunkGeneratorWorldgenAuthorityMixin.java"));
        String mixins = Files.readString(Path.of("src/main/resources/globe.mixins.json"));
        assertTrue(densityMixin.contains("LatitudeWorldgenScope.isActive()"),
                "Mushroom island density is dimension/generator scoped");
        assertTrue(densityMixin.contains("getInterpolatedState()Lnet/minecraft/world/level/block/state/BlockState;"),
                "Mushroom island authority reaches the live chunk block-writing path");
        // The 26.2 getInterpolatedDensity hook must not be harvested back: its only consumer on
        // 26.2 was the F3 debug readout (verified across the whole jar), the method does not exist
        // on this target, and height queries are already served by the state hook -- both versions'
        // iterateNoiseColumn read getInterpolatedState and substitute the default block on null.
        assertTrue(!densityMixin.contains("method = \"getInterpolatedDensity"),
                "Mushroom island hook must not target the removed 26.x density accessor");
        for (String owner : List.of("doFill", "getBaseHeight", "getBaseColumn")) {
            assertTrue(authorityMixin.contains(owner), "density authority covers " + owner);
        }
        assertTrue(mixins.contains("NoiseChunkMushroomIslandDensityMixin"),
                "Mushroom island density hook is registered");
        String locateSource = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        assertTrue(locateSource.contains("findPlannedSurfaceWaterCoverage("),
                "bounded locate has a constant-cost fallback to the birth plan");
        int exactFallback = locateSource.indexOf(
                "Pair<BlockPos, Holder<Biome>> fallback = centerQuartResult(");
        int plannedFallback = locateSource.indexOf(
                "Pair<BlockPos, Holder<Biome>> plannedFallback = fallback == null", exactFallback);
        int selectedFallback = locateSource.indexOf(
                "return fallback != null ? fallback : plannedFallback;", plannedFallback);
        assertTrue(exactFallback >= 0 && plannedFallback > exactFallback
                        && selectedFallback > plannedFallback,
                "birth-plan fallback runs only after the exact fallback misses and its result is returned");
        VanillaSurfaceWaterCoveragePlan impossibleWater = VanillaSurfaceWaterCoveragePlan.build(
                10_000, 41L, 63, (id, route, x, z) -> false);
        VanillaSurfaceWaterCoveragePlan.SearchStats shoreStats =
                impossibleWater.missingDiagnostics().get("minecraft:stony_shore");
        assertTrue(shoreStats != null && shoreStats.centerEligible() == 0,
                "surface-plan diagnostics distinguish absent eligible terrain from topology/capacity");

    }

    private static boolean insideSyntheticSurfaceWaterRoute(
            VanillaSurfaceWaterCoveragePlan.Route route, int x, int z, int radius) {
        double latitudeFraction = Math.abs(z) / (double) radius;
        if ((long) x * x + (long) z * z >= (long) radius * radius) return false;
        return latitudeFraction >= route.minimumLatitudeFraction()
                && latitudeFraction <= route.maximumLatitudeFraction();
    }

    private static boolean insideSyntheticRoute(BiomeRoute route, int x, int z, int radius) {
        double latitudeFraction = Math.abs(z) / (double) radius;
        if ((long) x * x + (long) z * z >= (long) radius * radius) return false;
        return switch (route) {
            case TROPICAL_HUMID_LOWLAND -> latitudeFraction >= 0.04 && latitudeFraction <= 0.24;
            case SUBTROPICAL_HUMID_LOWLAND, WARM_TRANSITION, WARM_UPLAND,
                    ARID_LOWLAND, ARID_UPLAND -> latitudeFraction >= 0.27 && latitudeFraction <= 0.39;
            case TEMPERATE_LOWLAND, TEMPERATE_WETLAND, TEMPERATE_UPLAND ->
                    latitudeFraction >= 0.41 && latitudeFraction <= 0.56;
            case SUBPOLAR_LOWLAND, SUBPOLAR_WETLAND, SUBPOLAR_UPLAND ->
                    latitudeFraction >= 0.59 && latitudeFraction <= 0.73;
            case POLAR_LOWLAND -> latitudeFraction >= 0.77 && latitudeFraction <= 0.91;
            case COLD_UPLAND -> latitudeFraction >= 0.61 && latitudeFraction <= 0.89;
            case CAVE_SHALLOW, CAVE_DEEP -> false;
        };
    }

    private static boolean isUplandRoute(BiomeRoute route) {
        return route == BiomeRoute.TEMPERATE_UPLAND
                || route == BiomeRoute.SUBPOLAR_UPLAND
                || route == BiomeRoute.COLD_UPLAND
                || route == BiomeRoute.WARM_UPLAND
                || route == BiomeRoute.ARID_UPLAND;
    }

    private static List<String> registryFor(Set<String> optionalProviders) {
        TreeSet<String> ids = new TreeSet<>();
        for (BiomeDescriptorLedger.Descriptor descriptor : BiomeDescriptorLedger.descriptors()) {
            if (descriptor.provider().equals("minecraft") || optionalProviders.contains(descriptor.provider())) {
                ids.add(descriptor.biomeId());
            }
        }
        return List.copyOf(ids);
    }

    private static String namespace(String id) { return id.substring(0, id.indexOf(':')); }

    /**
     * CliffTree shipped entirely inert: its biomes appeared in five {@code lat_*} tag files but had
     * no ledger descriptor, and the ledger is authoritative on every world this build creates, so
     * installing the mod changed nothing about the land Latitude painted. The 2026-08-10 audit
     * found this; the maintainer confirmed CliffTree is a keeper and every biome should be
     * classified. This asserts the land routes actually exist and, critically, that they carry
     * REAL climate-consistent placement rather than name-derived guesses.
     */
    /**
     * The windswept family belongs to subpolar mountains and nowhere else.
     *
     * <p>Measured on the 26.2 line's shipped build with vanilla biomes only:
     * {@code minecraft:windswept_hills} was 12.78% of all polar land (66.5-90 degrees) and 15.58%
     * of land above 74.5 — the second most common land biome at the pole, arriving snowless, with
     * green grass, flowers and passive animals at 80 north. Its route said "subpolar or polar AND
     * mountain", but the mountain half was enforced nowhere downstream, so the terrain-compatibility
     * reroll walked into it on ordinary polar shelves.
     *
     * <p>Three independent halves are asserted, because each one alone has already been shown to be
     * bypassable: the ledger route (which band pool may contain it), the extreme-polar cap's
     * explicit id list (which named only windswept_forest, and read as complete because the path
     * catch-all matches "forest"), and the legality predicate itself (which nothing upstream can
     * outvote once the reroll consults it).
     *
     * <p>SCOPE, stated rather than implied: this suite asserts the tables, the saved-roster
     * migration, the enforcement source text and the legality predicate. It does NOT drive
     * {@code LatitudeBiomes.pick} across the polar and subpolar bands the way the 26.2 twin does —
     * that sweep needs a picker band-index hook this line does not carry, and its column and hit
     * floors were measured against the 26.2 generator, so importing those numbers unmeasured would
     * assert something nobody on this line has checked. Until it is ported and re-measured here,
     * the behavioural claim "no green windswept grass at 80 north" rests on the ban being stated in
     * every gate below rather than on a map measured on this line.
     */
    private static void windsweptFamilyIsSubpolarMountainOnly() throws Exception {
        String[] windswept = {
                "minecraft:windswept_hills",
                "minecraft:windswept_forest",
                "minecraft:windswept_gravelly_hills"};

        // 1. Ledger and coverage plan agree, and both say subpolar.
        Map<String, BiomeRoute> required = VanillaBiomeCoveragePlan.requiredRoutes();
        for (String id : windswept) {
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
            assertTrue(descriptor != null, "windswept identity must stay ledger-admitted: " + id);
            assertEquals(Set.of(BiomeRoute.SUBPOLAR_UPLAND), descriptor.routes(),
                    "the windswept family owns SUBPOLAR_UPLAND and only that (2026-08-18): " + id);
            assertEquals(BiomeRoute.SUBPOLAR_UPLAND, required.get(id),
                    "the vanilla coverage guarantee must name the SAME route as the ledger, or it "
                            + "anchors a province in a band the picker will never choose it in: " + id);
        }
        // windswept_savanna is a hot savanna variant, not a member of this family. Any fix that
        // matched on the substring "windswept" would move it too.
        assertFalse(BiomeDescriptorLedger.descriptor("minecraft:windswept_savanna")
                        .routes().contains(BiomeRoute.SUBPOLAR_UPLAND),
                "minecraft:windswept_savanna is a warm biome and must not follow the cold windswept "
                        + "family into the subpolar uplands");

        // 2. A vanilla-only world still gets every guaranteed identity after the re-route.
        BiomeSelectionProfile vanilla = BiomeSelectionProfile.capture(registryFor(Set.of()));
        for (String id : windswept) {
            assertTrue(vanilla.contains(BiomeRoute.SUBPOLAR_UPLAND, id),
                    "a vanilla-only birth roster must carry the windswept family on its new route: " + id);
        }
        for (int radius : new int[]{3_750, 10_000, 20_000}) {
            for (long seed : new long[]{3L, 131L, 461L}) {
                VanillaBiomeCoveragePlan plan = VanillaBiomeCoveragePlan.build(
                        radius, seed, vanilla,
                        (id, route, x, z) -> insideSyntheticRoute(route, x, z, radius));
                assertTrue(plan.missingBiomeIds().isEmpty(),
                        "the narrower subpolar latitude window must not make any guaranteed identity "
                                + "unplaceable at radius=" + radius + " seed=" + seed
                                + " missing=" + plan.missingBiomeIds());
                for (VanillaBiomeCoveragePlan.Anchor anchor : plan.anchors()) {
                    if (anchor.route() != BiomeRoute.SUBPOLAR_UPLAND) continue;
                    double latitudeFraction = Math.abs(anchor.blockZ()) / (double) radius;
                    assertTrue(latitudeFraction <= 0.73,
                            "a guaranteed windswept province must stay out of the polar band: "
                                    + anchor.biomeId() + " at latitudeFraction=" + latitudeFraction);
                }
            }
        }

        // 3. An existing world born under the 2026-08-10 route still loads. The decoder throws on
        // the first row it cannot explain, so without the migration the whole birth roster — every
        // provider-ticket decision, not just windswept — would be silently discarded.
        BiomeSelectionProfile legacy = BiomeSelectionProfile.decode(
                "provider_ticket_v1\nCOLD_UPLAND|minecraft:windswept_hills");
        assertTrue(legacy.contains(BiomeRoute.SUBPOLAR_UPLAND, "minecraft:windswept_hills"),
                "a saved COLD_UPLAND windswept row must be readable, and must be read at the route "
                        + "the ruling moved it to");
        assertFalse(legacy.contains(BiomeRoute.COLD_UPLAND, "minecraft:windswept_hills"),
                "the legacy row must NOT stay eligible on COLD_UPLAND — that route reaches the "
                        + "polar band, which is exactly what the re-route forbids");

        // 3b. The OTHER legacy route, which is the bigger population: every jar on this line built
        // before the windswept family moved to COLD_UPLAND wrote TEMPERATE_UPLAND rows, so those
        // worlds — TEST jars included — are the ones most likely to exist, and they are ALREADY
        // roster-less: the re-route made savedRouteRemainsValid reject their rows the moment the
        // ledger stopped listing TEMPERATE_UPLAND for these ids. Both saved profiles are exercised
        // whole, built by rewriting a real capture, because a hand-written two-line profile would
        // not prove the rest of the roster survives alongside the migrated rows.
        List<String> vanillaIds = registryFor(Set.of());
        BiomeSelectionProfile current = BiomeSelectionProfile.capture(vanillaIds);
        for (BiomeRoute legacyRoute : List.of(BiomeRoute.TEMPERATE_UPLAND, BiomeRoute.COLD_UPLAND)) {
            String aged = current.encode();
            for (String id : windswept) {
                aged = aged.replace(BiomeRoute.SUBPOLAR_UPLAND.name() + "|" + id,
                        legacyRoute.name() + "|" + id);
            }
            BiomeSelectionProfile reopened = BiomeSelectionProfile.decode(aged);
            for (String id : windswept) {
                assertTrue(reopened.contains(BiomeRoute.SUBPOLAR_UPLAND, id),
                        "a world saved with " + legacyRoute + "|" + id + " must still open, and "
                                + "must read that row at the route the ruling moved it to");
                assertFalse(reopened.contains(legacyRoute, id),
                        "the legacy row must not stay eligible on " + legacyRoute + ": " + id);
            }
            assertEquals(current.encode(), reopened.encode(),
                    "migrating the windswept rows must leave the REST of the birth roster byte "
                            + "identical — the failure this repairs is not losing windswept, it is "
                            + "losing the whole roster to one unreadable row (legacyRoute="
                            + legacyRoute + ")");

            // The representation profile decodes its own LAND rows and must accept the same set,
            // or the coverage plan queries a route the roster no longer lists the biome under.
            VanillaBiomeRepresentationProfile currentRepresentation =
                    VanillaBiomeRepresentationProfile.capture(10_000, 131L, current);
            String agedRepresentation = currentRepresentation.encode();
            for (String id : windswept) {
                agedRepresentation = agedRepresentation.replace(
                        "LAND|" + BiomeRoute.SUBPOLAR_UPLAND.name(),
                        "LAND|" + legacyRoute.name());
            }
            assertEquals(currentRepresentation.encode(),
                    VanillaBiomeRepresentationProfile.decode(agedRepresentation).encode(),
                    "the representation profile must migrate the same legacy routes as the "
                            + "provider ticket, or one of the two rejects a saved world the other "
                            + "accepts (legacyRoute=" + legacyRoute + ")");
        }

        // 4. Enforcement in LatitudeBiomes: the extreme-polar cap now names all three ids, and the
        // family predicate stays an exact-id match.
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");
        String leakMethod = method(source, "isExtremePolarSoftColdLeak(Holder<Biome> candidate) {");
        for (String id : windswept) {
            assertTrue(leakMethod.contains("\"" + id + "\""),
                    "the extreme-polar cap must name every windswept identity explicitly — "
                            + "windswept_hills and windswept_gravelly_hills match none of the "
                            + "\"forest\"/\"taiga\"/\"grove\" catch-alls and passed straight "
                            + "through: " + id);
        }
        String legality = method(source, "isWindsweptFamilyLegal(int bandIndex,");
        // Truncated at the first statement terminator on purpose. method() runs to the next
        // "private static", so the slice also swallows the package-private
        // windsweptFamilyLegalForPolicyTest hook that sits directly below — whose parameter list
        // names all three evidence terms and would satisfy every contains() below on its own.
        String legalityBody = legality.substring(legality.indexOf("return "));
        String legalityReturn = legalityBody.substring(0, legalityBody.indexOf(';') + 1);
        assertTrue(legalityReturn.startsWith("return bandIndex == BAND_SUBPOLAR && (")
                        && legalityReturn.contains("mountainLike")
                        && legalityReturn.contains("mountainNoiseLike")
                        && legalityReturn.contains("rawMountainTruth"),
                "the reroll's legality predicate must demand BOTH the subpolar band and real "
                        + "mountain evidence — the band alone is what the old COLD_UPLAND route "
                        + "effectively enforced, and it is how flat polar shelves got windswept; "
                        + "it must also still consult rawMountainTruth, the raw isMountainLike "
                        + "read, which is the only one of the three terms that can ever be true in "
                        + "this band: " + legalityReturn.trim());
        String family = method(source, "isColdWindsweptFamilyBiome(Holder<Biome> candidate) {");
        assertFalse(family.contains("contains(\"windswept\")") || family.contains("getPath()"),
                "the family predicate must match exact ids, never the substring \"windswept\" — "
                        + "that would drag minecraft:windswept_savanna out of the warm bands");
        // The late ownership veto must read the family's CURRENT route. Reading COLD_UPLAND here
        // would condemn windswept on the subpolar mountains that are now its only legal home while
        // still waving it through at the pole.
        String ownership = method(source,
                "clampTemperateWindsweptMountainOwnership(Registry<Biome> biomes,");
        assertTrue(ownership.contains("bandIndex == BAND_SUBPOLAR")
                        && ownership.contains("BiomeRoute.SUBPOLAR_UPLAND"),
                "the mountain-ownership veto must key off SUBPOLAR_UPLAND and the subpolar band");
        assertFalse(ownership.contains("BiomeRoute.COLD_UPLAND"),
                "the mountain-ownership veto must no longer read COLD_UPLAND, which still spans "
                        + "the polar band the re-route removed windswept from");

        // The veto's own body is not the whole escape. It delegates the exact-preservation question
        // to VanillaCoverageFinalAdmissionPolicy, which lives in ANOTHER FILE -- so the assertion
        // above, a substring check on this method only, cannot see a hardcoded route over there.
        // It was hardcoded to COLD_UPLAND, and decide() rejects any descriptor that does not carry
        // the route it is handed, so the escape was dead for the one family it guards: a reserved
        // windswept column reading UPLAND terrain but not raw-mountain lost its guaranteed identity
        // to the band fallback. Pin the RESOLVED route, not a substring.
        String admissionSource = normalize(read(
                "src/main/java/com/example/globe/world/VanillaCoverageFinalAdmissionPolicy.java"));
        String exactUpland = method(admissionSource,
                "static Decision decideExactColdUplandPreservation(");
        assertTrue(exactUpland.contains("BiomeRoute.SUBPOLAR_UPLAND")
                        && exactUpland.contains("descriptor.routes().contains("),
                "the exact-upland preservation escape must resolve the descriptor's OWN upland "
                        + "route, or it can never preserve the windswept family it guards");
        assertEquals(2,
                occurrences(normalize(source), "out = clampPolarWindsweptOutput(biomes, out, landBandIndex);"),
                "both picker overloads must run the final polar windswept clamp");

        // 5. The polar staple must be allowed on a raised shelf, or the reroll fires band-wide and
        // this whole failure mode comes back through some other biome.
        String terrainCompat = method(source,
                "isBiomeCompatibleWithTerrain(Holder<Biome> candidate,");
        assertTrue(terrainCompat.contains("bandIndex == BAND_POLAR")
                        && terrainCompat.contains("TERRAIN_CLASS_RAISED_SHOULDER")
                        && terrainCompat.contains("minecraft:snowy_plains"),
                "snowy_plains must be accepted on a polar raised shoulder; rejecting the polar "
                        + "band's staple on any column 4 blocks above the sea is what forced the "
                        + "terrain reroll on nearly every polar column");

        // 6. THE LEVER ITSELF, asserted directly on the predicate.
        //
        // The defect this closes: isWindsweptFamilyLegal could not return true on ANY subpolar
        // column, mountain or not. It read only mountainNoiseLike — computed at the call site as
        // landBandIndex == BAND_TEMPERATE && ... — and mountainLike, which comes from
        // temperateMountainTerrainAuthority and is force-set true only under
        // landBandIndex >= BAND_POLAR. The subpolar band sits between the two and received
        // neither, so the family's one legal home was locked shut. The fix adds rawMountainTruth,
        // the raw isMountainLike read — deliberately the SAME signal the late ownership clamp uses,
        // so gate and veto agree by construction and the gate cannot admit a column the veto will
        // then silently overwrite.
        //
        // Asserted through a dedicated hook rather than through pick(), because the public picker
        // is called here with a null chunk generator: preview terrain is synthetic, every subpolar
        // column classifies flat, the incoming pick is already terrain-compatible, and the reroll
        // walk this predicate steers never runs. These four cases are the whole truth table that
        // matters, and every one of them fails if the rawMountainTruth term is removed or the band
        // test is loosened.
        // 3 == BAND_SUBPOLAR, 4 == BAND_POLAR, 2 == BAND_TEMPERATE.
        assertTrue(LatitudeBiomes.windsweptFamilyLegalForPolicyTest(3, false, false, true),
                "a subpolar column that the raw isMountainLike read calls a mountain must be "
                        + "legal windswept ground — this is the entire lever, and with the two "
                        + "band-scoped signals structurally false in this band it is the only "
                        + "term that can ever open the gate");
        assertFalse(LatitudeBiomes.windsweptFamilyLegalForPolicyTest(3, false, false, false),
                "a subpolar column with NO mountain evidence must stay illegal — the family is "
                        + "a mountain identity in this band, not merely a subpolar one");
        assertFalse(LatitudeBiomes.windsweptFamilyLegalForPolicyTest(4, true, true, true),
                "the polar band must stay illegal for the windswept family on every terrain, "
                        + "including a confirmed mountain; the raw mountain read must not have "
                        + "reopened the pole that the 2026-08-18 re-route closed");
        assertFalse(LatitudeBiomes.windsweptFamilyLegalForPolicyTest(2, false, false, true),
                "the temperate band must not become legal windswept ground — the family's "
                        + "route is SUBPOLAR_UPLAND, and a raw mountain read is true on plenty "
                        + "of temperate mountains");
    }

    private static void cliffTreeLandAndOceanAreActuallyReachable() throws Exception {
        // biome id -> {temperature, expected route}. Temperatures are ground truth from the
        // shipped CliffTree datapack JSON.
        String[][] expected = {
                {"clifftree:bog", "0.25", "TEMPERATE_WETLAND"},
                {"clifftree:sparse_forest", "0.7", "TEMPERATE_LOWLAND"},
                {"clifftree:coniferous_badlands", "2.0", "ARID_LOWLAND"},
                {"clifftree:oasis", "2.0", "ARID_LOWLAND"},
                {"clifftree:shrubland", "2.0", "ARID_LOWLAND"},
                {"clifftree:glacier_valley", "0.0", "POLAR_LOWLAND"},
                // glacier_cliff intentionally excluded from this table: it is COLD_UPLAND, not a
                // POLAR_LOWLAND lookalike, and is checked on its own below because it needs
                // Terrain.UPLAND asserted too, not just the route.
                {"clifftree:snowy_old_growth_taiga", "0.0", "POLAR_LOWLAND"},
                {"clifftree:tundra", "0.25", "POLAR_LOWLAND"},
        };
        // The three *_shore biomes are intentionally absent from this land table: they are a
        // beach authority, asserted in riverAndBeachAdmissionIsTagDrivenAndVanillaSafe().
        for (String[] row : expected) {
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(row[0]);
            assertTrue(descriptor != null,
                    "CliffTree land biome must have a descriptor or the mod is inert: " + row[0]);
            assertTrue(descriptor.routes().contains(BiomeRoute.valueOf(row[2])),
                    "CliffTree land biome must own its climate-appropriate route (temperature "
                            + row[1] + "): " + row[0] + " expected " + row[2]
                            + " actual " + descriptor.routes());
        }

        // glacier_cliff is COLD_UPLAND (50-90 degrees, mountain-gated), the same route already
        // owned by minecraft:snowy_slopes/frozen_peaks/jagged_peaks -- not a new route. This was a
        // real correction: it was first placed on POLAR_LOWLAND with Terrain.LOWLAND, which the
        // ledger's own invariant rejected (an UPLAND descriptor must own TEMPERATE_UPLAND or
        // COLD_UPLAND), because a "cliff" is rugged terrain and COLD_UPLAND's mountain gate is the
        // correct fit for that, not a workaround.
        BiomeDescriptorLedger.Descriptor glacierCliff =
                BiomeDescriptorLedger.descriptor("clifftree:glacier_cliff");
        assertTrue(glacierCliff != null && glacierCliff.routes().contains(BiomeRoute.COLD_UPLAND),
                "clifftree:glacier_cliff owns COLD_UPLAND, the existing cold mountain route");
        assertEquals(BiomeDescriptorLedger.Terrain.UPLAND, glacierCliff.terrain(),
                "clifftree:glacier_cliff is genuinely rugged terrain, not a flat polar lowland");

        // desert_cliff is the one intentionally dual-routed entry: an arid cliff face reachable as
        // both lowland and upland arid terrain.
        BiomeDescriptorLedger.Descriptor desertCliff =
                BiomeDescriptorLedger.descriptor("clifftree:desert_cliff");
        assertTrue(desertCliff != null && desertCliff.routes().contains(BiomeRoute.ARID_UPLAND),
                "clifftree:desert_cliff keeps its arid upland route");

        // The name-vs-climate trap this whole audit line exists to prevent: "coniferous_badlands"
        // is temperature 2.0 (maximum heat, identical to vanilla badlands). Routing it by name
        // would put a desert-hot identity in a cold band.
        BiomeDescriptorLedger.Descriptor conifBadlands =
                BiomeDescriptorLedger.descriptor("clifftree:coniferous_badlands");
        assertFalse(conifBadlands.routes().contains(BiomeRoute.SUBPOLAR_LOWLAND)
                        || conifBadlands.routes().contains(BiomeRoute.POLAR_LOWLAND),
                "clifftree:coniferous_badlands is temperature 2.0 despite its name — it must never "
                        + "be routed to a cold band on the strength of the word 'coniferous'");

        // Caves: categorized from CliffTree's OWN worldgen/biome tags (caves.json, deep_caves.json),
        // not guessed. inferno is grouped by CliffTree itself with minecraft:deep_dark rather than
        // its other caves, matching CAVE_DEEP's real Y<=-16 depth gate.
        String[] shallowCaves = {
                "clifftree:caves", "clifftree:warm_caves", "clifftree:lukewarm_caves",
                "clifftree:cold_caves", "clifftree:frozen_caves", "clifftree:mushroom_caves",
                "clifftree:dirt_caves",
        };
        for (String id : shallowCaves) {
            BiomeDescriptorLedger.Descriptor cave = BiomeDescriptorLedger.descriptor(id);
            assertTrue(cave != null, "CliffTree cave must have a descriptor: " + id);
            assertTrue(cave.routes().contains(BiomeRoute.CAVE_SHALLOW),
                    "CliffTree cave must own CAVE_SHALLOW: " + id);
            assertEquals(BiomeDescriptorLedger.Water.UNDERGROUND, cave.water(),
                    "CliffTree cave must be classified underground: " + id);
        }
        BiomeDescriptorLedger.Descriptor inferno = BiomeDescriptorLedger.descriptor("clifftree:inferno");
        assertTrue(inferno != null && inferno.routes().contains(BiomeRoute.CAVE_DEEP),
                "clifftree:inferno is CAVE_DEEP (CliffTree's own tags pair it with deep_dark, not "
                        + "its other caves) despite its surface-hot temperature -- it never reaches "
                        + "the surface, so that temperature never governs its placement");
        assertFalse(inferno.routes().contains(BiomeRoute.CAVE_SHALLOW),
                "inferno must not ALSO be shallow, or it can surface where its heat is nonsensical");

        // Oceans are a separate live authority: the lat_ocean_* tags, which (unlike the land lat_*
        // tags) are NOT shadowed by the ledger. Both CliffTree oceans are shallow is_ocean members.
        String oceanTag = read(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_ocean_temperate.json");
        assertTrue(oceanTag.contains("clifftree:stone_ocean")
                        && oceanTag.contains("clifftree:kelp_forest"),
                "CliffTree's two ocean biomes must be admitted through the live lat_ocean_* tag "
                        + "authority — they have no ledger route and are otherwise unreachable");
        assertTrue(oceanTag.contains("\"required\": false"),
                "optional pack biomes must be tagged required:false so a vanilla-only install does "
                        + "not fail datapack load");
    }

    /**
     * Rivers and beaches were hard authorities hardcoded to vanilla ids: pickBeachForBand returned
     * minecraft:beach/snowy_beach/stony_shore literals and the river branch returned
     * minecraft:river/frozen_river literals, so NO pack's river or beach could be admitted by any
     * means. This asserts the tag authority exists, that every tag still carries its vanilla
     * fallback (the vanilla-only install must be unchanged), and that the deliberate 70/30
     * snowy-vs-rocky category roll was NOT folded into the tag pick.
     */
    private static void riverAndBeachAdmissionIsTagDrivenAndVanillaSafe() throws Exception {
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");

        // Every new tag must exist, and must contain its vanilla identity so a no-pack world is
        // unaffected. A tag whose only members are modded would silently empty the authority.
        String[][] tagVanillaFloor = {
                {"lat_beach_tropical", "minecraft:beach"},
                {"lat_beach_temperate", "minecraft:beach"},
                {"lat_beach_cold_snowy", "minecraft:snowy_beach"},
                {"lat_beach_cold_rocky", "minecraft:stony_shore"},
                {"lat_river_warm", "minecraft:river"},
                {"lat_river_temperate", "minecraft:river"},
                {"lat_river_frozen", "minecraft:frozen_river"},
        };
        for (String[] row : tagVanillaFloor) {
            String tag = read("src/main/resources/data/globe/tags/worldgen/biome/" + row[0] + ".json");
            assertTrue(tag.contains(row[1]),
                    row[0] + " must retain its vanilla identity so a vanilla-only install is "
                            + "unchanged by this authority: expected " + row[1]);
            assertTrue(source.contains("\"" + row[0] + "\""),
                    "tag must actually be wired into LatitudeBiomes, not just shipped as data: " + row[0]);
        }

        // The 70/30 cold-beach split is a deliberate tuning. Folding both identities into one tag
        // would let coherent noise pick between them near 50/50, visibly changing every polar
        // coastline on vanilla-only worlds for a reason unrelated to pack support.
        // Both overloads (registry-source and collection-source) must keep the roll. Asserting mere
        // presence was NOT enough: a teeth check that removed it from one overload still passed,
        // because the other overload's copy satisfied a contains() check.
        assertEquals(3, occurrences(source, "0xBEEFBEEF"),
                "the cold-beach category roll must survive in BOTH pickBeachForBand overloads — it "
                        + "decides snowy vs rocky, and only the identity within that category is "
                        + "tag-driven; losing it in either path silently rerolls every polar "
                        + "coastline on vanilla-only worlds. The third sanctioned site is "
                        + "vanillaBeachIdForBand: the quarantine's beach-identity restore must use "
                        + "the SAME roll, or a restored cold shoreline lands on the wrong "
                        + "snowy/rocky side of its own neighbours");
        assertTrue(method(source, "String vanillaBeachIdForBand(int blockX, int blockZ, int bandIndex)").contains("0xBEEFBEEF"),
                "the quarantine beach-identity restore must reuse the cold-beach category roll, "
                        + "not invent its own salt");
        String beachMethod = method(source, "pickBeachForBand(Registry<Biome> biomes,");
        assertTrue(beachMethod.contains("LAT_BEACH_COLD_SNOWY") && beachMethod.contains("LAT_BEACH_COLD_ROCKY"),
                "both cold categories resolve through their own tag");
        assertFalse(beachMethod.contains("biome(biomes, \"minecraft:beach\")"),
                "the hardcoded vanilla beach literal must be gone from the pick path — it is now "
                        + "only a fallback argument");

        // The freeze verdict is a latitude ramp and must NOT have become tag- or band-driven.
        assertTrue(source.contains("shouldFreezeRiver(blockX, blockZ)")
                        && source.contains("LAT_RIVER_FROZEN"),
                "shouldFreezeRiver still decides frozen vs liquid; the tag only decides identity");

        // The mesic clamp is the third true-latitude ramp (siblings: ARID_POLEWARD_RAMP,
        // FROZEN_RIVER_RAMP). Band jitter + blend + warp promoted true-33degN desert-belt columns
        // to the TEMPERATE pool (maple/seasonal-forest islands in erg desert, live 2026-08-24);
        // the clamp must gate that promotion from TRUE latitude inside the blend resolution and
        // must run BEFORE temperate/subpolar ownership enforcement so the ramp cannot be undone.
        String blendMethod = method(source, "latitudeBandIndexWithBlend(int blockX, int blockZ, int radius,");
        assertTrue(blendMethod.contains("TEMPERATE_EQUATORWARD_RAMP_LOW_DEG")
                        && blendMethod.contains("TEMPERATE_EQUATORWARD_RAMP_HIGH_DEG"),
                "temperate promotion out of the subtropical belt is gated by the true-latitude "
                        + "equatorward ramp inside the blend resolution");
        assertTrue(blendMethod.indexOf("TEMPERATE_EQUATORWARD_RAMP_LOW_DEG")
                        < blendMethod.indexOf("enforceTemperateSubpolarOwnership(canonicalBandIndex, resolvedBandIndex)"),
                "the mesic clamp must resolve before ownership enforcement, not after it");
        assertTrue(source.contains("latitude.temperateEquatorwardRampLow"),
                "the mesic clamp stays -D tunable like its two sibling ramps");

        // The riparian water sweep answers with a DISTANCE, and the bank taper consumes it
        // directly, so the probe table must be ordered by true distance. Rounding rings onto the
        // block grid does not preserve ring order — whether a later ring holds a column nearer
        // than an earlier ring's farthest depends on the arithmetic of (direction count x radius
        // set), and the current table is inversion-free only by that coincidence. Pin the sort so
        // a future edit to either constant cannot silently return the wrong distance.
        String riparian = read("src/main/java/com/example/globe/world/feature/RiparianPlacement.java");
        assertTrue(riparian.contains("offsets.sort(java.util.Comparator.comparingInt(offset -> offset[2]))"),
                "the riparian probe table is sorted by true squared distance, not left in ring "
                        + "order — the taper reads this distance and a ring-ordered walk can "
                        + "report a farther column than the nearest one sampled");
        assertTrue(riparian.contains("return Math.sqrt(offset[2]);"),
                "the sweep returns the hit column's true distance, not its nominal ring radius "
                        + "(rounding puts those up to 0.69 blocks apart)");
        assertFalse(riparian.contains("hasWaterNearby"),
                "the 16-point compass stencil must stay gone: it missed a bank column one block "
                        + "from a narrow river because both probes on its ray overshot the far bank");

        // Shores are a beach authority, never ledger land. Latitude's own isBeachLike() matches any
        // path containing "shore", so routing one as land would place a coastal identity inland.
        for (String shore : new String[]{
                "clifftree:granite_shore", "clifftree:diorite_shore", "clifftree:snowy_diorite_shore"}) {
            assertTrue(BiomeDescriptorLedger.descriptor(shore) == null,
                    "shore biomes must NOT hold a ledger land route — vanilla stony_shore has none "
                            + "either, and isBeachLike() treats them as beaches: " + shore);
        }
        String coldRocky = read(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_beach_cold_rocky.json");
        String temperateBeach = read(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_beach_temperate.json");
        assertTrue(coldRocky.contains("clifftree:diorite_shore")
                        && coldRocky.contains("clifftree:snowy_diorite_shore"),
                "the cold shores are admitted through the rocky beach category");
        assertTrue(temperateBeach.contains("clifftree:granite_shore"),
                "the temperate shore is admitted through the temperate beach category");
    }

    /**
     * The band pool gate must accept everything the ledger admits, or a biome is selected and then
     * silently rerolled away with no error anywhere.
     *
     * <p>This is the defect the maintainer hit live on two separate worlds (2026-08-10): she could
     * not locate {@code biomesoplenty:muskeg} or {@code clifftree:glacier_cliff}.
     * {@code enforceLandBandPool} validated the final pick against {@code allowedLandPool}, which
     * was built from the {@code lat_*} tags and a small hardcoded extras list — never from the
     * ledger. Selection under the provider-ticket policy is ledger-driven, so the two authorities
     * disagreed. muskeg and {@code terralith:ice_marsh} appear in NO {@code lat_*} tag at all and
     * were therefore unplaceable in every world ever generated, before AND after their cold-wetland
     * re-route; glacier_cliff sat only in {@code lat_polar_secondary} and so was rerolled across
     * the whole subpolar half of its COLD_UPLAND range.
     *
     * <p>Asserted structurally rather than by listing ids, so a future ledger addition cannot
     * reintroduce the same silent hole by being forgotten in a tag file.
     */
    private static void everyLedgerLandRouteSurvivesTheBandPoolGate() throws Exception {
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");
        assertTrue(source.contains("ledgerLandIdsForBand(bandIndex)"),
                "allowedLandPool must union in the ledger's own band roster — building the gate "
                        + "from lat_* tags alone lets ledger-admitted biomes be selected and then "
                        + "immediately rerolled away, with no error raised anywhere");
        assertEquals(2, occurrences(source, "ledgerLandIdsForBand(bandIndex)"),
                "BOTH allowedLandPool overloads (registry-source and collection-source) must union "
                        + "the ledger roster; fixing only one leaves the hole open on that path");

        // landRoutesForBand must stay the exact inverse of landRouteEligible's switch. If a route
        // is added to the enum and omitted here, its biomes silently stop being placeable.
        String routesForBand = method(source, "landRoutesForBand(int bandIndex) {");
        for (BiomeRoute route : BiomeRoute.values()) {
            if (route == BiomeRoute.CAVE_SHALLOW || route == BiomeRoute.CAVE_DEEP) {
                assertFalse(routesForBand.contains(route.name()),
                        "cave routes must never enter a LAND band pool: " + route);
                continue;
            }
            assertTrue(routesForBand.contains(route.name()),
                    "every non-cave route must map to a band in landRoutesForBand, or biomes owning "
                            + "it become unplaceable: " + route);
        }

        // The three measured casualties, named so the specific regression cannot return quietly.
        for (String id : new String[]{
                "biomesoplenty:muskeg", "terralith:ice_marsh", "clifftree:glacier_cliff"}) {
            BiomeDescriptorLedger.Descriptor d = BiomeDescriptorLedger.descriptor(id);
            assertTrue(d != null && !d.routes().isEmpty(),
                    "regression anchor must still hold a ledger route: " + id);
        }
    }

    /**
     * Acceptance and substitution must not use the same pool.
     *
     * <p>Maintainer, live 2026-08-10: teleporting to muskeg landed on a flat expanse of ice.
     * {@code /latdev explain} showed {@code cont=-0.611} at a sea-level coastal column — far below
     * the {@code cont > -0.20} that {@code evaluateSwamp} demands, so the wetland route was never
     * eligible there. The cause was a regression from the band-pool fix earlier the same day:
     * unioning the ledger roster into {@code allowedLandPool} correctly stopped muskeg being
     * rerolled AWAY, but also made it available to be rerolled IN, and
     * {@code pickFromAllowedLandPool} performs a raw pick that re-checks no route condition. At
     * temperature 0.0 every bit of the bog's water then froze.
     *
     * <p>So the acceptance pool must keep wetlands and the substitution pool must not. Asserting
     * both halves, because dropping either reopens a different bug: drop the union and muskeg is
     * unplaceable again, drop this filter and it lands on frozen ocean.
     */
    private static void wetlandsAreAcceptedButNeverSubstitutedIn() throws Exception {
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");

        // Acceptance keeps them (the earlier fix).
        assertTrue(source.contains("ledgerLandIdsForBand(bandIndex)"),
                "the acceptance pool must still union the ledger roster, or a legitimately picked "
                        + "wetland is thrown away and becomes unplaceable again");

        // Substitution drops them.
        String reroll = method(source,
                "rerollLandPoolForBand(List<Holder<Biome>> allowedPool,");
        assertTrue(reroll.contains("removeConditionalWetlandFamily"),
                "the substitution pool must drop route-conditional wetlands — pickFromAllowedLandPool "
                        + "re-checks nothing, so anything left here can be dropped onto a column "
                        + "whose route conditions were never evaluated");

        String filter = method(source, "removeConditionalWetlandFamily(List<Holder<Biome>> pool,");
        assertTrue(filter.contains("Terrain.WETLAND"),
                "the filter keys on ledger wetland terrain, not on a hardcoded id list, so a future "
                        + "wetland descriptor is covered automatically");
        // Assert the seed list is CONSULTED, not merely fetched. A contains() on the method name
        // passed with the check gutted to `&& true`, because the variable stayed assigned — the
        // third time this exact weak-assertion shape has slipped through in this campaign.
        assertTrue(filter.contains("!deliberateSeeds.contains(id)"),
                "deliberate per-band seeds (vanilla swamp in the tropics, mangrove in the "
                        + "subtropics) must actually be consulted and exempted, not just looked up — "
                        + "those are an existing intentional decision this filter must not undo");

        // The gate the substitution was bypassing must still be the real one.
        assertTrue(source.contains("cont > -0.20"),
                "evaluateSwamp's continentalness floor is the condition this protects; if it moves, "
                        + "this test's premise needs rechecking");

        // Both wetland routes must be gated on the dry-province law AND the swamp evaluation
        // (26.2 parity port, authorized 2026-08-16 — matches the 26.2 line's own pin).
        String eligible = source.replaceAll("\\s+", " ");
        assertTrue(eligible.contains("case TEMPERATE_WETLAND -> band == BAND_TEMPERATE && !mountain && wetlandProvinceEligible(blockX, blockZ) && evaluateSwamp("),
                "temperate wetland stays province- and swamp-gated");
        assertTrue(eligible.contains("case SUBPOLAR_WETLAND -> band == BAND_SUBPOLAR && !mountain && wetlandProvinceEligible(blockX, blockZ) && evaluateSwamp("),
                "subpolar wetland stays province- and swamp-gated");
    }

    /**
     * The land-cohesion gate's paint pool must agree with the ledger about which band its members
     * belong to.
     *
     * <p>Found live (maintainer, 2026-08-10): meadow painted at Y=79 with mountainNoiseLike=false.
     * Root cause chain, proven from her own /latdev explain values: robustDelta=8 >= the gate's
     * relief threshold of 6, so TerrainBiomeCohesionPolicy forced the upland family AFTER
     * enforceLandBandPool, painting from the hardcoded TEMPERATE_UPLAND_BIOMES array. That array
     * still contained windswept_hills/windswept_forest after their ledger route moved to
     * COLD_UPLAND — a second, independent placement mechanism the route move missed, quietly
     * reintroducing windswept into temperate through the cohesion gate.
     *
     * <p>The invariant, asserted structurally: every id in the gate array must hold a ledger route
     * that reaches the temperate band, so the array cannot silently disagree with a future routing
     * decision the way it disagreed with the windswept one.
     */
    private static void cohesionGatePoolAgreesWithTheLedger() throws Exception {
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");
        int start = source.indexOf("private static final String[] TEMPERATE_UPLAND_BIOMES = {");
        assertTrue(start >= 0, "the cohesion-gate paint pool must exist");
        int end = source.indexOf("};", start);
        String arrayBlock = source.substring(start, end);

        java.util.List<String> ids = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"").matcher(arrayBlock);
        while (m.find()) {
            ids.add(m.group(1));
        }
        assertTrue(!ids.isEmpty(), "the gate pool must not be empty — an empty pool would make the "
                + "cohesion gate a no-op and quietly revert the sunflower-plains-on-a-ridge fix");

        java.util.Set<BiomeRoute> temperateRoutes = java.util.Set.of(
                BiomeRoute.TEMPERATE_LOWLAND, BiomeRoute.TEMPERATE_WETLAND, BiomeRoute.TEMPERATE_UPLAND);
        for (String id : ids) {
            BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(id);
            assertTrue(descriptor != null,
                    "every cohesion-gate pool member must be ledger-admitted: " + id);
            boolean reachesTemperate = descriptor.routes().stream().anyMatch(temperateRoutes::contains);
            assertTrue(reachesTemperate,
                    "cohesion-gate pool member must hold a temperate-band ledger route — the gate "
                            + "paints AFTER enforceLandBandPool with no re-check, so an entry routed "
                            + "elsewhere (windswept -> SUBPOLAR_UPLAND) is smuggled into temperate "
                            + "through the back door: " + id);
        }
        assertFalse(arrayBlock.contains("windswept"),
                "windswept must not return to the temperate cohesion-gate pool (maintainer rulings, "
                        + "2026-08-10 and 2026-08-18: the windswept family belongs to subpolar "
                        + "mountains, 50-66.5 degrees)");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int at = value.indexOf(needle); at >= 0; at = value.indexOf(needle, at + needle.length())) count++;
        return count;
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("missing method: " + signature);
        int next = source.indexOf("\n    private static ", start + signature.length());
        return source.substring(start, next >= 0 ? next : source.length());
    }

    /**
     * The 2026-08-10 biome-picker audit measured this cap at its PRE-fix threshold (0.45) and
     * found it retained ~59% of ice_spikes picks rather than capping them — a vanilla-only world
     * (the hard "must work with no providers" case) still finished with ice_spikes on ~27% of the
     * polar band, not the minority accent {@code lat_polar_accent.json} declares. This proves the
     * RETUNED threshold (0.78) against the real pipeline: BiomeProviderSelectionPolicy's argmax
     * pick over the actual POLAR_LOWLAND pool, then PolarIceSpikeAccentPolicy's cap on top,
     * sampled on a coherent, non-adjacent grid so no single accent patch is double-counted.
     */
    private static void polarIceSpikeAccentStaysAMinorityInEveryPoolSize() {
        List<String> vanillaOnly = List.of("minecraft:ice_spikes", "minecraft:snowy_plains");
        List<String> withProviders = List.of(
                "minecraft:ice_spikes", "minecraft:snowy_plains",
                "biomesoplenty:auroral_garden", "biomesoplenty:snowblossom_grove",
                "biomesoplenty:snowy_coniferous_forest", "biomesoplenty:snowy_fir_clearing",
                "biomesoplenty:tundra", "biomesoplenty:wintry_origin_valley",
                "terralith:cold_shrubland", "terralith:siberian_grove", "terralith:siberian_taiga",
                "terralith:snowy_cherry_grove", "terralith:wintry_forest", "terralith:wintry_lowlands");

        double vanillaOnlyShare = measurePolarIceSpikeShare(vanillaOnly);
        double withProvidersShare = measurePolarIceSpikeShare(withProviders);

        assertTrue(vanillaOnlyShare < 0.08,
                "vanilla-only polar band must not read as an ice_spikes monoculture — the hard "
                        + "'must work with no providers' case is the worst case for this cap "
                        + "because the pool is smallest; measured ~5.9% on this exact grid when the "
                        + "policy's threshold was tuned, so 8% leaves headroom without masking a "
                        + "real regression: observed=" + vanillaOnlyShare);
        assertTrue(withProvidersShare <= vanillaOnlyShare + 0.02,
                "installing providers must not INCREASE ice_spikes' share of the polar band — "
                        + "the cap is source-agnostic, so a wider pool should only dilute it further: "
                        + "vanillaOnly=" + vanillaOnlyShare + " withProviders=" + withProvidersShare);
        assertTrue(withProvidersShare > 0.0,
                "the accent must still be reachable, not fully eliminated, with providers installed");
    }

    private static double measurePolarIceSpikeShare(List<String> biomeIds) {
        BiomeProviderSelectionPolicy.Pool pool = BiomeProviderSelectionPolicy.createPool(biomeIds);
        int total = 0;
        int iceSpikes = 0;
        int bandIndex = 4; // BAND_POLAR
        for (long seed : AUDIT_SEEDS) {
            for (int x : NONADJACENT_POINTS) {
                for (int z : NONADJACENT_POINTS) {
                    int index = BiomeProviderSelectionPolicy.selectIndex(
                            pool, seed, x, z, bandIndex, "POLAR_LOWLAND", 0L);
                    total++;
                    if ("minecraft:ice_spikes".equals(pool.ids().get(index))
                            && PolarIceSpikeAccentPolicy.keepPolarIceSpike(seed, x, z)) {
                        iceSpikes++;
                    }
                }
            }
        }
        return iceSpikes / (double) total;
    }

    /**
     * terralith:siberian_grove and terralith:siberian_taiga carry IDENTICAL ground-truth climate
     * and content (temperature 0.13, trees + mushrooms + logs — from the 2026-08-10 audit corpus)
     * but the extreme-polar-cap catch-all only matched "forest"/"taiga" substrings, so the two
     * were banned inconsistently by name alone. Source-scan rather than a live pick, matching this
     * suite's existing convention for LatitudeBiomes internals that are not independently public.
     */
    private static void polarExtremeCapCatchesNameAlikeModdedBiomesConsistently() throws Exception {
        String source = normalize(read("src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        String leakMethod = method(source, "isExtremePolarSoftColdLeak(Holder<Biome> candidate) {");
        assertTrue(leakMethod.contains("path.contains(\"grove\")"),
                "the catch-all must include \"grove\", or terralith:siberian_grove and "
                        + "terralith:siberian_taiga (identical climate/content, different name) are "
                        + "banned inconsistently at the extreme polar cap");
        assertTrue(leakMethod.contains("path.contains(\"forest\")")
                        && leakMethod.contains("path.contains(\"taiga\")"),
                "the pre-existing forest/taiga catch-all must remain — this is additive, not a "
                        + "replacement");
        assertTrue(source.contains("EXTREME_POLAR_CAP_MIN_DEG = 74.5"),
                "the constant this javadoc describes must still be 74.5, or the corrected javadoc "
                        + "(fixed 2026-08-10; previously claimed 85 against this same 74.5 constant) "
                        + "is itself now wrong");
    }


    /**
     * The warm belt has one answer per province, and this pins it.
     *
     * <p>Four rulings from 2026-08-18 land here as one shape. In the DRY province desert is the
     * staple and badlands is the country inside it, so every path that resolves a dry warm column
     * must try desert first. In the MEDIUM province savanna is a country plus the dry fringe hugging
     * an arid province, and forest is the belt around both. And the two paths that can still hand
     * out arid AFTER applyFinalSavannaClimateClamp has run must re-apply that clamp's three latitude
     * demotes themselves, or a gate-made desert stands at the equator with nothing downstream left
     * to take it back.
     *
     * <p>STRUCTURAL BY DESIGN, and the scope limit is worth stating rather than rediscovering. Every
     * defect in this chain was a rewrite stage disagreeing with its own twin, or with the province
     * authority, about what a column IS -- and every admission, descriptor and route assertion in
     * this file passed for the whole life of all four. A share census would be the stronger
     * instrument, but the shares that would give it teeth were measured on the 26.2 generator, and
     * this line carries desert levers 26.2 does not (ProvinceAuthority's subtropical dry belt), so
     * its arid balance starts somewhere else. Numbers from there would be assertions about a
     * different world. These pins hold the SHAPE until this line has its own atlas census; see the
     * port report for the specific 26.2 thresholds deliberately not carried over.
     */
    private static void warmBeltIdentityIsProvinceOrdered() throws Exception {
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");

        // 1. THE DRY PROVINCE ORDERS DESERT FIRST, in both picker overloads. These two had disagreed
        //    since they were written -- registry badlands -> savanna -> desert against collection
        //    desert -> badlands -- which is live chunk generation and the atlas drawing two
        //    different worlds from the same seed.
        for (String overload : new String[]{"Registry<Biome> biomes,", "Collection<Holder<Biome>> biomes,"}) {
            String arm = warmProvinceArm(
                    memberBody(source, "private static Holder<Biome> enforceWarmProvinceFamily(" + overload),
                    "case WARM_DRY ->");
            int desert = arm.indexOf("\"minecraft:desert\"");
            int badlands = arm.indexOf("\"minecraft:badlands\"");
            int savanna = arm.indexOf("\"minecraft:savanna\"");
            assertTrue(desert >= 0 && badlands >= 0 && savanna >= 0,
                    "the dry province's chain must still name all three identities (" + overload
                            + "): " + arm);
            assertTrue(desert < badlands && badlands < savanna,
                    "the dry warm province resolves desert, THEN badlands, THEN savanna as the last "
                            + "resort (maintainer ruling, 2026-08-18) -- badlands is placed by "
                            + "badlandsProvinceAuthorityHit, not by being the fallback for everything "
                            + "dry, and the two overloads must name them in the same order or the "
                            + "world stops matching the map (" + overload + "): " + arm);
        }

        // 2. THE MEDIUM PROVINCE ASKS THE COUNTRY AND THE FRINGE, in both overloads, and answers
        //    forest outside both. An arm that resolves to savanna without consulting either is the
        //    monoculture this slice removed.
        for (String overload : new String[]{"Registry<Biome> biomes,", "Collection<Holder<Biome>> biomes,"}) {
            String arm = warmProvinceArm(
                    memberBody(source, "private static Holder<Biome> enforceWarmProvinceFamily(" + overload),
                    "case WARM_MEDIUM ->");
            assertTrue(arm.contains("savannaOwnsColumnAt(blockX, blockZ)"),
                    "savanna has TWO homes in the warm-medium belt -- its countries and the dry "
                            + "fringe hugging an arid province -- and this arm must consult "
                            + "ownership before it hands a column savanna (" + overload + "): " + arm);
            assertTrue(arm.indexOf("warmMediumForestStaple(biomes)")
                            < arm.indexOf("\"minecraft:savanna\""),
                    "outside a country and outside the fringe the warm-medium staple is forest, and "
                            + "it must be reached BEFORE the savanna chain, or the belt re-derives "
                            + "its old identity (" + overload + "): " + arm);
            assertTrue(arm.contains("isBiomeId(pick, \"minecraft:windswept_savanna\")"),
                    "windswept_savanna stays exempt from the country rule in both directions -- it "
                            + "is the mountain identity with exactly one legal home, not the flat "
                            + "staple this country governs (" + overload + "): " + arm);
        }

        // 3. THE TWO LATE ARID PRODUCERS RE-APPLY THE LATITUDE LAW. Both of these run downstream of
        //    applyFinalSavannaClimateClamp, which is where "no arid in the tropics, none past the
        //    temperate line" lives. Producing sand here and stopping would leave desert standing at
        //    the equator. Neither may name an identity itself: the province helper is the one
        //    answer, and the three demotes have to follow it in the clamp's own order.
        for (String gate : new String[]{"gateDryWarmIdentity", "gateWarmJungleSurvival"}) {
            String registryGate = memberBody(source,
                    "private static Holder<Biome> " + gate + "(Registry<Biome> biomes,");
            String collectionGate = memberBody(source,
                    "private static Holder<Biome> " + gate + "(Collection<Holder<Biome>> biomes,");
            for (String body : new String[]{normalize(registryGate), normalize(collectionGate)}) {
                assertFalse(body.contains("pickDryWarmFallback("),
                        gate + " must not answer a dry warm column with the hardcoded fallback -- "
                                + "that path returns arid with no latitude law at all, which is how "
                                + "desert reached the equator: " + body);
                int enforce = body.indexOf("ProvinceAuthority.Province.WARM_DRY, blockX, blockZ)");
                int badlands = body.indexOf("demoteEquatorialBadlands(");
                int desert = body.indexOf("demoteEquatorialDesert(");
                int poleward = body.indexOf("demotePolewardArid(");
                assertTrue(enforce >= 0,
                        gate + " must resolve its dry answer through the same helper every other "
                                + "dry province column resolves through: " + body);
                assertTrue(badlands > enforce && desert > badlands && poleward > desert,
                        gate + " runs after applyFinalSavannaClimateClamp, so it must re-apply that "
                                + "clamp's three latitude demotes, in the clamp's order, to whatever "
                                + "it answers -- without them a gate-made desert stands at the "
                                + "equator with nothing downstream left to take it back: " + body);
            }
            // The two overloads have to be the same world, not merely both correct. Everything from
            // the dry reroute onward is compared literally.
            String marker = "enforceWarmProvinceFamily(";
            assertEquals(
                    normalize(registryGate.substring(registryGate.lastIndexOf(marker))),
                    normalize(collectionGate.substring(collectionGate.lastIndexOf(marker))),
                    gate + "'s two overloads must resolve identically, call for call -- a divergence "
                            + "here is live generation parting company with the map the atlas drew");
        }

        // 3b. OWNERSHIP MEANS COUNTRY *OR* FRINGE. The call sites above ask one memoised question
        //     instead of naming both predicates inline, so the "both homes are consulted" claim now
        //     lives in that one definition -- pin it there, or a memo that quietly dropped the
        //     fringe would satisfy every call-site check above.
        String ownershipBody = normalize(memberBody(source,
                "private static boolean savannaOwnsColumnAt("));
        assertTrue(ownershipBody.contains(
                        "savannaCountryHere(blockX, blockZ) || savannaDryFringeHere(blockX, blockZ)"),
                "savanna ownership must remain the country OR the dry fringe: " + ownershipBody);
        assertTrue(ownershipBody.contains("authority == candidateAuthority")
                        || normalize(source).contains("authority == candidateAuthority"),
                "the ownership memo must invalidate on province-authority IDENTITY, or a swapped "
                        + "authority in a proof could read a stale answer");

        // 4. THE JUNGLE GATE IS WHERE THE COUNTRY IS DECISIVE. It runs AFTER enforceLandBandPool,
        //    and minecraft:savanna is not pool-legal in the tropical band, so every tropical savanna
        //    reaching the surface from a jungle donor comes through here. A gate that "looks wired"
        //    because the enforcer downstream knows about countries would still stamp savanna.
        for (String overload : new String[]{"Registry<Biome> biomes,", "Collection<Holder<Biome>> biomes,"}) {
            String body = normalize(memberBody(source,
                    "private static Holder<Biome> gateWarmJungleSurvival(" + overload));
            assertTrue(body.contains("savannaOwnsColumnAt(blockX, blockZ)"),
                    "the jungle-survival gate must consult savanna ownership HERE, explicitly -- it "
                            + "is the largest single savanna producer in the pipeline and the only "
                            + "one downstream of the band-pool gate (" + overload + "): " + body);
            assertTrue(body.contains("warmMediumOutsideCountryStaple(biomes, out)"),
                    "outside both homes this gate answers the forest staple (" + overload + "): "
                            + body);
        }

        // 5. THE PROVINCE IS THE AUTHORITY OUTSIDE ITS OWN COUNTRY. Both arid fallbacks must resolve
        //    desert DIRECTLY out here rather than handing `base` to the province enforcer, which
        //    returns any badlands-family pick untouched -- that is how a column where vanilla had
        //    already placed badlands re-admitted badlands outside its own province, no matter what
        //    the province said.
        for (String overload : new String[]{"Registry<Biome> biomes,", "Collection<Holder<Biome>> biomes,"}) {
            String body = normalize(memberBody(source,
                    "private static Holder<Biome> pickAridRegionFallback(" + overload));
            int outsideGate = body.indexOf("BADLANDS_OUTSIDE_PROVINCE_THRESHOLD");
            int outsideDesert = body.indexOf("\"minecraft:desert\"");
            int enforcerHandoff = body.indexOf("enforceWarmProvinceFamily(");
            assertTrue(outsideGate >= 0 && outsideDesert > outsideGate,
                    "outside a badlands province the arid belt must resolve to desert (" + overload
                            + "): " + body);
            assertTrue(outsideDesert < enforcerHandoff,
                    "the outside-province answer must be desert resolved DIRECTLY, before any "
                            + "handoff to the province enforcer -- the enforcer returns an incoming "
                            + "badlands untouched, which re-admits mesa outside its own country ("
                            + overload + "): " + body);
        }
        assertTrue(normalize(source).contains("BADLANDS_OUTSIDE_PROVINCE_THRESHOLD = 0.06"),
                "the outlier-mesa allowance stays narrow (0.34 -> 0.06, maintainer ruling "
                        + "2026-08-18). At 0.34 this was not an outlier allowance, it was a second "
                        + "badlands province covering a third of everything outside the first one");

        // 6. THE EQUATORIAL GATES ARE A PAIR, AND THE LAW IS NOT LOOSENED. Desert leaves the
        //    badlands predicate so it is gated once rather than twice, which is what gives it the
        //    23.5-27 degree phase-in -- but only because its own gate reads the same tropical ban
        //    and the same ramp edge, where smoothstep clamps the gate to zero.
        String badlandsPredicate = normalize(memberBody(source,
                "private static boolean shouldDemoteEquatorialBadlands(Holder<Biome> pick,"));
        assertTrue(badlandsPredicate.contains("isBiomeId(pick, \"minecraft:desert\")"),
                "vanilla desert must be handed to its own gate here, not gated twice: "
                        + badlandsPredicate);
        assertTrue(badlandsPredicate.contains("isAridFamily(pick)"),
                "modded arid variants must STAY in this predicate -- the desert gate matches a "
                        + "literal vanilla id and would never catch them: " + badlandsPredicate);
        String desertPredicate = normalize(memberBody(source,
                "private static boolean shouldDemoteEquatorialDesert(Holder<Biome> pick,"));
        assertTrue(desertPredicate.contains("authoritativeTropicalAridBan(blockX, blockZ, radius)")
                        && desertPredicate.contains("DESERT_LAT_RAMP_LOW_DEG"),
                "splitting the pair is only lawful because the desert gate enforces the SAME "
                        + "tropical ban over the SAME ramp edge -- if either goes, the tropics can "
                        + "grow desert: " + desertPredicate);

        // 7. FOREST HAS TO BE POOL-LEGAL IN THE TROPICS or the belt's new staple is rerolled away
        //    by enforceLandBandPool and the old identity comes straight back.
        String extras = memberBody(source,
                "private static List<String> allowedExtraBiomeIdsForBand(int bandIndex) {");
        String tropicalExtras = extras.substring(extras.indexOf("case BAND_TROPICAL ->"),
                extras.indexOf("case BAND_SUBTROPICAL ->"));
        assertTrue(tropicalExtras.contains("\"minecraft:forest\""),
                "minecraft:forest is a deliberate tropical band-pool seed (the swamp precedent) -- "
                        + "without it the warm-medium staple cannot survive the pool gate: "
                        + tropicalExtras);

        // 8. THE FRINGE READS THE PROVINCE'S OWN ARITHMETIC, and this line's dry-belt levers are
        //    still there. A wholesale copy of 26.2's ProvinceAuthority would silently delete them:
        //    26.2's release line has no subtropical dry belt, and that belt is one of this line's
        //    two desert levers.
        String province = read("src/main/java/com/example/globe/world/ProvinceAuthority.java");
        for (String lever : new String[]{
                "SUBTROPICAL_DRY_BELT_START_DEG", "SUBTROPICAL_DRY_BELT_PEAK_LOW_DEG",
                "SUBTROPICAL_DRY_BELT_PEAK_HIGH_DEG", "SUBTROPICAL_DRY_BELT_END_DEG",
                "SUBTROPICAL_DRY_BIAS"}) {
            assertTrue(province.contains(lever + " ="),
                    "this line's subtropical dry-belt lever must survive every port from 26.2 -- the "
                            + "26.2 release line does not have it, so a file-level copy reverts it "
                            + "without a diff anyone reads: " + lever);
        }
        String fringe = normalize(province.substring(
                province.indexOf("public boolean warmDryFringeForBand(")));
        assertTrue(fringe.contains("warmDryMoisture("),
                "the fringe must measure the SAME quantity classifyWarm thresholds against for "
                        + "WARM_DRY -- on this line that is the dry-belt-biased moisture, so a "
                        + "fringe read off the plain moisture would sit at a different distance "
                        + "from the province edge than the edge itself: " + fringe);
        assertTrue(fringe.contains("WARM_DRY_THRESHOLD + WARM_DRY_FRINGE_WIDTH"),
                "the fringe is the shell immediately outside the dry threshold, expressed in the "
                        + "code's own constants rather than a copied number: " + fringe);
        String classifyWarm = normalize(memberBody(province,
                "private Province classifyWarm(int blockX, int blockZ) {"));
        assertTrue(classifyWarm.contains("dryMoisture < WARM_DRY_THRESHOLD")
                        && classifyWarm.contains("moisture > WARM_WET_THRESHOLD"),
                "extracting warmMoisture must not merge this line's two moisture quantities: the "
                        + "dry boundary reads the dry-belt-biased value and the wet boundary reads "
                        + "the plain one, and collapsing them would move the humid tier the "
                        + "dry-belt lever was written never to touch: " + classifyWarm);
    }

    /**
     * A member's source text, from its signature to its own closing brace.
     *
     * <p>Deliberately not {@link #method}: that helper slices to the next {@code private static}
     * declaration, so it runs past the end of a method whose neighbour is declared any other way
     * and can pick up matches from the method after it. Every nested closer in this codebase is
     * indented at least eight spaces, so the first four-space {@code }} after the signature is the
     * member's own.
     */
    private static String memberBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("missing member: " + signature);
        int end = source.indexOf("\n    }", start + signature.length());
        if (end < 0) throw new AssertionError("unterminated member: " + signature);
        return source.substring(start, end + "\n    }".length());
    }

    /** One arrow-switch arm of a warm-province enforcer, from its label to the next label. */
    private static String warmProvinceArm(String methodBody, String label) {
        int start = methodBody.indexOf(label);
        if (start < 0) throw new AssertionError("missing province arm: " + label);
        int next = methodBody.indexOf("\n            case ", start + label.length());
        int end = next >= 0 ? next : methodBody.indexOf("\n            default ->", start);
        return normalize(methodBody.substring(start, end >= 0 ? end : methodBody.length()));
    }

    private static void assertWithinFourSigma(Map<String, Integer> counts, String key, int samples, double expected, String message) {
        int count = counts.getOrDefault(key, 0);
        double observed = count / (double) samples;
        double sigma = Math.sqrt(expected * (1.0 - expected) / samples);
        if (Math.abs(observed - expected) > 4.0 * sigma) {
            throw new AssertionError(message + ": expected=" + expected + " observed=" + observed + " samples=" + samples);
        }
    }

    private static void assertThrows(ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            return;
        } catch (Exception unexpected) {
            throw new AssertionError(message, unexpected);
        }
        throw new AssertionError(message);
    }

    private static void assertTrue(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void assertFalse(boolean condition, String message) { assertTrue(!condition, message); }
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
    private static void assertGreaterThan(double floor, double actual, String message) {
        if (!(actual > floor)) throw new AssertionError(message + ": floor=" + floor + " actual=" + actual);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
