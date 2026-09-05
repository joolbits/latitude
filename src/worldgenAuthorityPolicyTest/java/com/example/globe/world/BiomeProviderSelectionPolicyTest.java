package com.example.globe.world;

import com.mojang.serialization.Lifecycle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

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
        descriptorAdmissionIsClosedAndCanonical();
        dappledForestHasOneCoolBorderHome();
        existingWorldDappledUpgradeIsOneTimeAndAdditive();
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
        desertIsTheStapleOfTheSubtropicalAridBelt();
        dryWarmIdentityGateUsesTheDesertFirstOrder();
        savannaIsACountryInsideTheWarmBelt();
        cliffTreeLandAndOceanAreActuallyReachable();
        oceanIdentityFollowsDonorDepthInEveryBand();
        beachShortcutQuarantineRestoresBeachIdentity();
        temperatePoolNeverResolvesEquatorwardOfTheTrueLine();
        riverAndBeachAdmissionIsTagDrivenAndVanillaSafe();
        everyLedgerLandRouteSurvivesTheBandPoolGate();
        everyLandBandRoleKeepsAVanillaFloor();
        riparianBankProbesCannotMissANarrowRiver();
        wetlandsAreAcceptedButNeverSubstitutedIn();
        cohesionGatePoolAgreesWithTheLedger();
        vanillaCoverageIsCompleteAndWorldSizeSafe();
        vanillaCoverageFinalOutputHonorsHumidityAndPickerParity();
        groveLocateFallsBackToExactLandCoverageAnchor();
        erodedBadlandsIsGuaranteedOnTheLowlandAridRoute();
        surfaceWaterCoverageIsCompleteAndWorldSizeSafe();
        sizeAwareVanillaRepresentationIsClosedAndBirthLocked();
        caveCoverageIsClosedAndWorldSizeSafe();
        vanillaCoverageIsV2OnlyAndClearsWithContext();
    }

    static void polarTaigaTransitionPreservesShouldersAndTreeLine() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements().map(h -> (Holder<Biome>) h).toList();
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
                                Holder<Biome> input = testBiomeHolder(registry, id);
                                Object a = registryGate.invoke(null, registry, input, 3, actualLatitude, x, z);
                                Object b = collectionGate.invoke(null, pool, input, 3, actualLatitude, x, z);
                                assertEquals(a, b, "both final picker paths must make the same transition decision");
                                if (latitude <= 66.4) assertEquals(input, a, "subpolar taiga below the transition is unchanged");
                                if (latitude >= 72.0) {
                                    assertEquals(testBiomeHolder(registry, "minecraft:snowy_plains"), a,
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
                                assertEquals(testBiomeHolder(registry, "minecraft:snowy_plains"), polar,
                                        "a genuinely polar blended selection must remain treeless");
                            }
                            Holder<Biome> meadow = testBiomeHolder(registry, "minecraft:meadow");
                            assertEquals(meadow, registryGate.invoke(null, registry, meadow, 3, actualLatitude, x, z),
                                    "the taiga transition must not replace another biome family");
                        }
                    }
                    Holder<Biome> edgeTaiga = testBiomeHolder(registry, "minecraft:taiga");
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
        assertThrows(() -> BiomeSelectionProfile.decode("provider_ticket_v1\nTEMPERATE_LOWLAND|terralith:amethyst_canyon"),
                "a descriptorless saved row is rejected");
        assertThrows(() -> BiomeSelectionProfile.decode("provider_ticket_v1\nTEMPERATE_LOWLAND|minecraft:forest\nTEMPERATE_LOWLAND|minecraft:forest"),
                "duplicate saved route rows are rejected");
    }

    private static void dappledForestHasOneCoolBorderHome() throws Exception {
        String dappled = DappledForestPlacementPolicy.BIOME_ID;
        BiomeDescriptorLedger.Descriptor descriptor = BiomeDescriptorLedger.descriptor(dappled);
        assertTrue(descriptor != null, "Dappled Forest has a reviewed catalog entry");
        assertEquals(Set.of(BiomeRoute.TEMPERATE_LOWLAND), descriptor.routes(),
                "Dappled Forest owns one existing lowland route and no generic cold route");
        assertEquals(BiomeDescriptorLedger.Terrain.LOWLAND, descriptor.terrain(),
                "Dappled Forest is lowland terrain");
        assertEquals(BiomeDescriptorLedger.Water.LAND, descriptor.water(),
                "Dappled Forest is land");
        assertEquals(BiomeDescriptorLedger.Family.FOREST, descriptor.family(),
                "Dappled Forest belongs to the forest family");
        assertEquals(BiomeRoute.TEMPERATE_LOWLAND,
                VanillaBiomeCoveragePlan.requiredRoutes().get(dappled),
                "the guarantee planner uses the catalog route");
        assertTrue(VanillaBiomeRepresentationProfile.mandatoryLandIds().contains(dappled),
                "the unique poplar resource keeps Dappled exact in compact worlds");

        String secondaryTag = Files.readString(Path.of(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_temperate_secondary.json"));
        assertEquals(1, occurrences(secondaryTag, "\"minecraft:dappled_forest\""),
                "Dappled Forest appears exactly once in the secondary temperate pool");

        assertTrue(DappledForestPlacementPolicy.isEligible(true, -704.0, 704.0)
                        && DappledForestPlacementPolicy.isEligible(true, 0.0, 704.0),
                "the full equatorward half of the transition envelope is eligible");
        assertFalse(DappledForestPlacementPolicy.isEligible(true, -704.01, 704.0),
                "ordinary temperate country outside the cool-border envelope is forbidden");
        assertFalse(DappledForestPlacementPolicy.isEligible(true, 0.01, 704.0),
                "the poleward half of the warped boundary is forbidden");
        assertFalse(DappledForestPlacementPolicy.isEligible(false, -200.0, 704.0),
                "upland or non-temperate terrain cannot borrow the Dappled exception");

        BiomeSelectionProfile providers = BiomeSelectionProfile.capture(
                registryFor(Set.of("biomesoplenty", "terralith")));
        List<String> withDappled = providers.entries(BiomeRoute.TEMPERATE_LOWLAND);
        List<String> withoutDappled = withDappled.stream()
                .filter(id -> !dappled.equals(id))
                .toList();
        BiomeProviderSelectionPolicy.Pool before =
                BiomeProviderSelectionPolicy.createPool(withoutDappled);
        BiomeProviderSelectionPolicy.Pool after =
                BiomeProviderSelectionPolicy.createPool(withDappled);
        int providerSamples = 0;
        for (long seed : AUDIT_SEEDS) {
            for (int x : NONADJACENT_POINTS) {
                int z = x / 2;
                String oldWinner = BiomeProviderSelectionPolicy.selectId(
                        before, seed, x, z, 2, "TEMPERATE_LOWLAND", 0L);
                String newWinner = BiomeProviderSelectionPolicy.selectId(
                        after, seed, x, z, 2, "TEMPERATE_LOWLAND", 0L);
                assertEquals(namespace(oldWinner), namespace(newWinner),
                        "adding a Minecraft biome cannot move a cell to another provider");
                if (!oldWinner.startsWith("minecraft:")) {
                    assertEquals(oldWinner, newWinner,
                            "a non-Minecraft winner stays byte-for-byte the same");
                }
                providerSamples++;
            }
        }
        assertTrue(providerSamples >= 200,
                "the provider-territory comparison must cover a meaningful coordinate matrix");

        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        Holder<Biome> donor = testBiomeHolder(registry, "minecraft:plains");
        Holder<Biome> dappledDonor = testBiomeHolder(registry, dappled);
        BiomeSelectionProfile vanilla = BiomeSelectionProfile.capture(
                registry.keySet().stream().map(Identifier::toString).toList());
        for (int radius : new int[]{3_750, 5_000, 7_500, 10_000, 15_000, 20_000}) {
            for (long seed : new long[]{3L, 131L, 461L}) {
                VanillaBiomeRepresentationProfile representation =
                        VanillaBiomeRepresentationProfile.capture(radius, seed, vanilla);
                assertTrue(representation.landTargets().containsKey(dappled),
                        "every fresh globe size keeps an exact Dappled target");
                if (representation.worldSize().compact()) {
                    assertTrue(representation.landTargets().size()
                                    <= representation.worldSize().compactLandBudget(),
                            "Dappled uses the existing compact budget instead of enlarging it");
                }
                WetlandEvidence wetland = new WetlandEvidence();
                Climate.Sampler provisionalSampler = coverageSampler(null);
                try {
                    LatitudeBiomes.activateWorldgenContext(
                            radius,
                            seed,
                            LatitudeWorldState.WorldgenPolicyVersion
                                    .PROVIDER_TICKET_V4_CAVE_COVERAGE,
                            vanilla,
                            representation,
                            CaveBiomeRepresentationProfile.capture(radius, vanilla),
                            provisionalSampler,
                            null,
                            63);
                    VanillaBiomeCoveragePlan.Anchor swamp =
                            LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest().anchors().stream()
                                    .filter(value -> value.route() == BiomeRoute.TEMPERATE_WETLAND)
                                    .findFirst()
                                    .orElseThrow();
                    wetland.add(new WetlandFootprint(
                            swamp.blockX(), swamp.blockZ(), swamp.radiusBlocks()));
                } finally {
                    LatitudeBiomes.clearWorldgenContext();
                }
                Climate.Sampler sampler = coverageSampler(wetland);
                try {
                    LatitudeBiomes.activateWorldgenContext(
                            radius,
                            seed,
                            LatitudeWorldState.WorldgenPolicyVersion
                                    .PROVIDER_TICKET_V4_CAVE_COVERAGE,
                            vanilla,
                            representation,
                            CaveBiomeRepresentationProfile.capture(radius, vanilla),
                            sampler,
                            null,
                            63);
                    VanillaBiomeCoveragePlan plan =
                            LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
                    assertTrue(plan != null && plan.complete(),
                            "the Dappled guarantee must fit alongside every existing target at radius="
                                    + radius + " seed=" + seed + " missing="
                                    + (plan == null ? "<no plan>" : plan.missingBiomeIds()));
                    assertEquals(dappled,
                            plan.anchors().get(plan.anchors().size() - 1).biomeId(),
                            "Dappled reserves space only after every established biome guarantee");
                    VanillaBiomeCoveragePlan.Anchor anchor = plan.anchors().stream()
                            .filter(value -> dappled.equals(value.biomeId()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    "the fresh-world Dappled anchor is missing at radius=" + radius
                                            + " seed=" + seed));
                    assertTrue(anchor.radiusBlocks() >= 96,
                            "Dappled receives a province, not a token cell");
                    if (representation.worldSize().compact()) {
                        assertEquals(96, anchor.radiusBlocks(),
                                "compact Dappled keeps a substantial fixed footprint that fits "
                                        + "inside the cool-border envelope");
                    }
                    int coreRadius = DappledForestPlacementPolicy.VISIBLE_CORE_RADIUS_BLOCKS;
                    for (int offsetZ = -coreRadius; offsetZ <= coreRadius; offsetZ += 16) {
                        for (int offsetX = -coreRadius; offsetX <= coreRadius; offsetX += 16) {
                            int x = anchor.blockX() + offsetX;
                            int z = anchor.blockZ() + offsetZ;
                            assertTrue(LatitudeBiomes.dappledForestEligibleForPolicyTest(
                                            x, z, radius, 2, false, sampler),
                                    "the guarantee and final picker share the cool-border rule");
                            double delta = LatitudeBiomes.dappledForestBoundaryDeltaForPolicyTest(
                                    x, z, radius);
                            assertTrue(delta >= -704.0 && delta <= 0.0,
                                    "the guaranteed patch stays on the temperate side of the warped boundary");
                            assertPickerPairReturns(
                                    registry, pool, donor, x, z, radius, sampler, dappled,
                                    "Dappled survives both final picker paths inside its guarantee");
                        }
                    }
                } finally {
                    LatitudeBiomes.clearWorldgenContext();
                }
            }
        }

        int radius = 10_000;
        long seed = 131L;
        Climate.Sampler sampler = coverageSampler(new WetlandEvidence());
        try {
            LatitudeBiomes.setWorldSeed(seed);
            LatitudeBiomes.setRadius(radius);
            assertNeitherPickerReturns(
                    registry, pool, dappledDonor, 256, 4_000, radius, sampler, dappled,
                    "Dappled cannot survive in ordinary warmer temperate country");
            assertNeitherPickerReturns(
                    registry, pool, dappledDonor, 256, 6_500, radius, sampler, dappled,
                    "Dappled cannot survive beyond the cool-border window");
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }

        String source = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        String coverageEligibility = method(
                source,
                "private static boolean vanillaCoverageRouteEligible(String biomeId");
        assertTrue(coverageEligibility.contains("dappledForestEligible("),
                "the guarantee search calls the shared Dappled rule");
        assertEquals(2, occurrences(source, "out = enforceDappledForestPlacement("),
                "both final picker overloads enforce the shared Dappled rule");
    }

    private static void existingWorldDappledUpgradeIsOneTimeAndAdditive() throws Exception {
        String dappled = DappledForestPlacementPolicy.BIOME_ID;
        List<String> activeIds = registryFor(Set.of("biomesoplenty", "terralith"));
        BiomeSelectionProfile currentBirth = BiomeSelectionProfile.capture(activeIds);
        String preDappledBirthBytes = currentBirth.encode().lines()
                .filter(line -> !line.endsWith("|" + dappled))
                .collect(java.util.stream.Collectors.joining("\n"));
        BiomeSelectionProfile preDappledBirth =
                BiomeSelectionProfile.decode(preDappledBirthBytes);
        assertFalse(preDappledBirth.contains(BiomeRoute.TEMPERATE_LOWLAND, dappled),
                "the migration fixture is a real pre-Dappled birth roster");

        int radius = 10_000;
        long seed = 131L;
        VanillaBiomeRepresentationProfile currentRepresentation =
                VanillaBiomeRepresentationProfile.capture(radius, seed, currentBirth);
        VanillaBiomeRepresentationProfile preDappledRepresentation =
                preDappledRepresentation(currentRepresentation, dappled);
        String preDappledRepresentationBytes = preDappledRepresentation.encode();
        CaveBiomeRepresentationProfile caveRepresentation =
                CaveBiomeRepresentationProfile.capture(radius, preDappledBirth);

        LatitudeWorldState oldWorld = completeV4State(
                radius, preDappledBirth, preDappledRepresentation, caveRepresentation);
        assertEquals(0, oldWorld.getContentRosterRevision(),
                "a 26.2-style state begins without a content revision");
        assertTrue(oldWorld.getContentRosterAdditions().isEmpty(),
                "a 26.2-style state begins without additions");
        assertTrue(oldWorld.tryUpgradeContentRoster(activeIds),
                "a complete V4 provider-ticket world receives the one-time stamp");
        assertEquals(ContentRosterUpgradePolicy.CURRENT_REVISION,
                oldWorld.getContentRosterRevision(),
                "the old world advances to the current content revision");
        assertEquals(List.of(dappled), oldWorld.getContentRosterAdditions(),
                "Dappled is the only saved additive identity");
        assertEquals(preDappledBirthBytes,
                oldWorld.getProviderTicketProfile().orElseThrow().encode(),
                "the world's original provider roster remains byte-for-byte unchanged");
        assertEquals(preDappledRepresentationBytes,
                oldWorld.getVanillaRepresentationProfile().orElseThrow().encode(),
                "the world's original representation profile remains byte-for-byte unchanged");

        BiomeSelectionProfile runtimeProfile = oldWorld
                .getRuntimeProviderTicketProfile(activeIds).orElseThrow();
        assertTrue(runtimeProfile.contains(BiomeRoute.TEMPERATE_LOWLAND, dappled),
                "new terrain sees Dappled through the runtime-only roster");
        assertEquals(1L, runtimeProfile.entries(BiomeRoute.TEMPERATE_LOWLAND).stream()
                        .filter(dappled::equals).count(),
                "the runtime roster contains Dappled exactly once");
        assertFalse(oldWorld.tryUpgradeContentRoster(activeIds),
                "loading the stamped world again is a no-op");
        assertEquals(List.of(dappled), oldWorld.getContentRosterAdditions(),
                "the second load cannot duplicate the addition");
        assertEquals(preDappledBirthBytes,
                oldWorld.getProviderTicketProfile().orElseThrow().encode(),
                "idempotence includes preserving the birth roster");

        for (int compactRadius : new int[]{3_750, 5_000, 7_500}) {
            VanillaBiomeRepresentationProfile compactCurrent =
                    VanillaBiomeRepresentationProfile.capture(
                            compactRadius, seed, currentBirth);
            VanillaBiomeRepresentationProfile compactOld =
                    preDappledRepresentation(compactCurrent, dappled);
            String compactOldBytes = compactOld.encode();
            LatitudeWorldState compactState = completeV4State(
                    compactRadius,
                    preDappledBirth,
                    compactOld,
                    CaveBiomeRepresentationProfile.capture(compactRadius, preDappledBirth));
            assertTrue(compactState.tryUpgradeContentRoster(activeIds),
                    "every valid compact 26.2-style profile receives the additive stamp");
            assertEquals(compactOldBytes,
                    compactState.getVanillaRepresentationProfile().orElseThrow().encode(),
                    "the compact representation profile remains byte-for-byte unchanged");
            assertFalse(compactState.getVanillaRepresentationProfile().orElseThrow()
                            .landTargets().containsKey(dappled),
                    "an upgraded compact world receives no retroactive Dappled guarantee");
            assertTrue(compactState.getRuntimeProviderTicketProfile(activeIds).orElseThrow()
                            .contains(BiomeRoute.TEMPERATE_LOWLAND, dappled),
                    "the compact old world still receives new-terrain Dappled availability");
        }

        LatitudeWorldState freshWorld = completeV4State(
                radius,
                currentBirth,
                currentRepresentation,
                CaveBiomeRepresentationProfile.capture(radius, currentBirth));
        freshWorld.markContentRosterCurrent();
        assertFalse(freshWorld.tryUpgradeContentRoster(activeIds),
                "a fresh 26.3 world does not receive a redundant runtime addition");
        assertEquals(ContentRosterUpgradePolicy.CURRENT_REVISION,
                freshWorld.getContentRosterRevision(),
                "fresh worlds are stamped current at birth");
        assertTrue(freshWorld.getContentRosterAdditions().isEmpty(),
                "fresh worlds already carry Dappled in their birth roster");

        LatitudeWorldState legacy = new LatitudeWorldState();
        legacy.setGlobeRadius(radius);
        assertFalse(legacy.tryUpgradeContentRoster(activeIds),
                "legacy and profile-less worlds fail closed");
        assertEquals(0, legacy.getContentRosterRevision(),
                "a rejected legacy world is not stamped");

        LatitudeWorldState incompleteV4 = new LatitudeWorldState();
        incompleteV4.setWorldgenPolicy(
                LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE);
        incompleteV4.setGlobeRadius(radius);
        incompleteV4.setProviderTicketProfile(preDappledBirth);
        assertFalse(incompleteV4.tryUpgradeContentRoster(activeIds),
                "a V4 world missing its saved representation profiles fails closed");
        assertEquals(0, incompleteV4.getContentRosterRevision(),
                "incomplete V4 state remains unchanged");

        List<String> registryWithoutDappled = activeIds.stream()
                .filter(id -> !dappled.equals(id))
                .toList();
        LatitudeWorldState missingRegistryIdentity = completeV4State(
                radius, preDappledBirth, preDappledRepresentation, caveRepresentation);
        assertFalse(missingRegistryIdentity.tryUpgradeContentRoster(registryWithoutDappled),
                "a runtime registry without Dappled is not modified");
        assertEquals(0, missingRegistryIdentity.getContentRosterRevision(),
                "missing runtime content leaves the save untouched");

        ContentRosterUpgradePolicy.Decision damaged = ContentRosterUpgradePolicy.evaluate(
                ContentRosterUpgradePolicy.CURRENT_REVISION,
                List.of("minecraft:forest"),
                true,
                activeIds,
                false);
        assertFalse(damaged.changed(), "unknown saved additions are never repaired by guessing");
        assertTrue(ContentRosterUpgradePolicy.validRuntimeAdditions(
                        damaged.revision(), damaged.additions(), activeIds).isEmpty(),
                "unknown saved additions fail closed at runtime");
        ContentRosterUpgradePolicy.Decision future = ContentRosterUpgradePolicy.evaluate(
                ContentRosterUpgradePolicy.CURRENT_REVISION + 1,
                List.of(),
                true,
                activeIds,
                false);
        assertFalse(future.changed(), "an unknown future revision is never downgraded or rewritten");
        ContentRosterUpgradePolicy.Decision damagedNegative = ContentRosterUpgradePolicy.evaluate(
                -1,
                List.of(),
                true,
                activeIds,
                false);
        assertFalse(damagedNegative.changed() || damagedNegative.revision() != -1,
                "a damaged negative revision remains unknown and is never treated as revision zero");

        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        Holder<Biome> donor = testBiomeHolder(registry, "minecraft:plains");
        int registryHits = 0;
        int collectionHits = 0;
        for (long runtimeSeed : new long[]{3L, 131L, 461L}) {
            WetlandEvidence wetland = new WetlandEvidence();
            try {
                LatitudeBiomes.activateWorldgenContext(
                        radius,
                        runtimeSeed,
                        LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE,
                        runtimeProfile,
                        preDappledRepresentation,
                        caveRepresentation,
                        coverageSampler(null),
                        null,
                        63);
                VanillaBiomeCoveragePlan.Anchor swamp =
                        LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest().anchors().stream()
                                .filter(anchor -> anchor.route() == BiomeRoute.TEMPERATE_WETLAND)
                                .findFirst()
                                .orElseThrow();
                wetland.add(new WetlandFootprint(
                        swamp.blockX(), swamp.blockZ(), swamp.radiusBlocks()));
            } finally {
                LatitudeBiomes.clearWorldgenContext();
            }
            Climate.Sampler sampler = coverageSampler(wetland);
            try {
                LatitudeBiomes.activateWorldgenContext(
                        radius,
                        runtimeSeed,
                        LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE,
                        runtimeProfile,
                        preDappledRepresentation,
                        caveRepresentation,
                        sampler,
                        null,
                        63);
                VanillaBiomeCoveragePlan coverage =
                        LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
                assertTrue(coverage != null && coverage.complete()
                                && coverage.anchors().stream()
                                .noneMatch(anchor -> dappled.equals(anchor.biomeId())),
                        "an upgraded old world gains availability, not a new guaranteed province");
                for (int absZ = 4_600; absZ <= 5_600; absZ += 16) {
                    for (int hemisphere : new int[]{-1, 1}) {
                        int z = absZ * hemisphere;
                        for (int x = -8_000; x <= 8_000; x += 32) {
                            if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                            if (dappled.equals(LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                                    registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER")))) {
                                registryHits++;
                            }
                            if (dappled.equals(LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                                    pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER")))) {
                                collectionHits++;
                            }
                        }
                    }
                }
            } finally {
                LatitudeBiomes.clearWorldgenContext();
            }
        }
        assertTrue(registryHits > 0 && collectionHits > 0,
                "newly generated eligible territory can select the runtime Dappled addition");

        String stateSource = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/LatitudeWorldState.java"));
        String modSource = Files.readString(Path.of(
                "src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(stateSource.contains("content_roster_revision")
                        && stateSource.contains("content_roster_additions"),
                "both optional saved roster fields are wired into Latitude state");
        assertTrue(modSource.contains("markContentRosterCurrent()")
                        && occurrences(modSource, "tryUpgradeContentRoster(activeBiomeIds)") == 2
                        && occurrences(modSource, "getRuntimeProviderTicketProfile(activeBiomeIds)") == 2,
                "fresh capture and both world activation paths use the content-roster stamp");
        String upgradeMethod = method(
                stateSource,
                "public boolean tryUpgradeContentRoster(Collection<String> activeRegistryIds)");
        assertFalse(upgradeMethod.contains("Chunk") || upgradeMethod.contains("setBiome"),
                "the saved-state upgrade cannot rewrite generated chunk biome data");
    }

    private static LatitudeWorldState completeV4State(
            int radius,
            BiomeSelectionProfile providerProfile,
            VanillaBiomeRepresentationProfile representationProfile,
            CaveBiomeRepresentationProfile caveProfile) {
        LatitudeWorldState state = new LatitudeWorldState();
        state.setWorldgenPolicy(
                LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE);
        state.setGlobeRadius(radius);
        state.setProviderTicketProfile(providerProfile);
        state.setVanillaRepresentationProfile(representationProfile);
        state.setCaveRepresentationProfile(caveProfile);
        return state;
    }

    private static VanillaBiomeRepresentationProfile preDappledRepresentation(
            VanillaBiomeRepresentationProfile current,
            String dappled) {
        String encoded = current.encode().lines()
                .filter(line -> !(line.startsWith("LAND|")
                        && line.endsWith("|" + dappled)))
                .map(line -> line.startsWith("OMIT|") && line.endsWith("|" + dappled)
                        ? line.substring(0, line.length() - dappled.length())
                        + "minecraft:dark_forest"
                        : line)
                .collect(java.util.stream.Collectors.joining("\n"));
        return VanillaBiomeRepresentationProfile.decode(encoded);
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
     * Uses every combination of the three supported optional providers, including CliffTree.
     * The four-sigma bounds are binomial bounds declared before any world/map sample is run.
     */
    private static void everySupportedStackGetsEqualRouteTickets() {
        for (Set<String> stack : List.of(
                Set.<String>of(),
                Set.of("biomesoplenty"),
                Set.of("terralith"),
                Set.of("clifftree"),
                Set.of("biomesoplenty", "terralith"),
                Set.of("biomesoplenty", "clifftree"),
                Set.of("terralith", "clifftree"),
                Set.of("biomesoplenty", "terralith", "clifftree"))) {
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
        // bytes, the swamp province failed to anchor on 4 of 12 real seeds at the 224 default
        // (3 of 12 on the 1.21.11 line) and on 0 of 12 at 112.
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

    private static void vanillaCoverageFinalOutputHonorsHumidityAndPickerParity() throws Exception {
        long seed = 131L;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        Holder<Biome> neutral = registry.get(
                        ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains")))
                .orElseThrow();
        BiomeSelectionProfile providerProfile = BiomeSelectionProfile.capture(
                registry.keySet().stream().map(Identifier::toString).toList());
        long drySwampAuditSeed = 59L;
        int drySwampAuditRadius = 10_000;
        Climate.Sampler drySwampAuditSampler = coverageSampler(null);
        try {
            LatitudeBiomes.activateWorldgenContext(
                    drySwampAuditRadius,
                    drySwampAuditSeed,
                    LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                    providerProfile,
                    VanillaBiomeRepresentationProfile.capture(
                            drySwampAuditRadius, drySwampAuditSeed, providerProfile),
                    drySwampAuditSampler,
                    null,
                    63);
            VanillaBiomeCoveragePlan.Anchor swamp =
                    LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest().anchors().stream()
                            .filter(anchor -> anchor.route() == BiomeRoute.TEMPERATE_WETLAND)
                            .findFirst()
                            .orElseThrow();
            assertPickerPairReturns(
                    registry, pool, neutral,
                    swamp.blockX(), swamp.blockZ(), drySwampAuditRadius, drySwampAuditSampler,
                    "minecraft:swamp",
                    "saved swamp remains represented after dry-province replanning");
            ProvinceAuthority.Province swampProvince =
                    LatitudeBiomes.classifyProvince(swamp.blockX(), swamp.blockZ());
            assertTrue(swampProvince != ProvinceAuthority.Province.WARM_DRY
                            && swampProvince != ProvinceAuthority.Province.COLD_DRY,
                    "saved swamp coverage must move out of the deterministic COLD_DRY anchor");
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
        // The wet-province wetland law narrows the swamp funnel: minecraft:swamp is a REQUIRED
        // coverage identity on TEMPERATE_WETLAND, and its anchor needs a centre plus four
        // shoulders that are all route-eligible. Prove the guarantee is still issuable across
        // every shipped world size and a spread of seeds rather than assuming it.
        for (int coverageRadius : new int[] {7_500, 10_000, 15_000}) {
            for (long coverageSeed : new long[] {seed, 7L, 12_345L, 99L, 2_026L, 555_555L}) {
                VanillaBiomeRepresentationProfile coverageRepresentation =
                        VanillaBiomeRepresentationProfile.capture(
                                coverageRadius, coverageSeed, providerProfile);
                LatitudeBiomes.activateWorldgenContext(
                        coverageRadius,
                        coverageSeed,
                        LatitudeWorldState.WorldgenPolicyVersion
                                .PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                        providerProfile,
                        coverageRepresentation,
                        coverageSampler(null),
                        null,
                        63);
                try {
                    VanillaBiomeCoveragePlan coveragePlan =
                            LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
                    assertTrue(coveragePlan != null && coveragePlan.complete(),
                            "the wet-province wetland law keeps every guaranteed identity "
                                    + "issuable at radius=" + coverageRadius
                                    + " seed=" + coverageSeed
                                    + " missing=" + (coveragePlan == null
                                            ? "<no plan>" : coveragePlan.missingBiomeIds()));
                    VanillaBiomeCoveragePlan.Anchor swampAnchor = coveragePlan.anchors().stream()
                            .filter(anchor -> "minecraft:swamp".equals(anchor.biomeId()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    "the swamp coverage guarantee vanished at radius="
                                            + coverageRadius + " seed=" + coverageSeed));
                    ProvinceAuthority.Province swampProvince = LatitudeBiomes.classifyProvince(
                            swampAnchor.blockX(), swampAnchor.blockZ());
                    assertTrue(swampProvince == ProvinceAuthority.Province.WARM_WET
                                    || swampProvince == ProvinceAuthority.Province.COLD_WET,
                            "the swamp anchor sits in a genuinely wet province at radius="
                                    + coverageRadius + " seed=" + coverageSeed
                                    + " (got " + swampProvince + ")");
                } finally {
                    LatitudeBiomes.clearWorldgenContext();
                }
            }
        }
        for (int radius : List.of(10_000, 5_000)) {
            VanillaBiomeRepresentationProfile representation =
                    VanillaBiomeRepresentationProfile.capture(radius, seed, providerProfile);
            Climate.Sampler provisionalSampler = coverageSampler(null);
            LatitudeBiomes.activateWorldgenContext(
                    radius,
                    seed,
                    LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                    providerProfile,
                    representation,
                    provisionalSampler,
                    null,
                    63);
            VanillaBiomeCoveragePlan provisionalPlan =
                    LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
            assertTrue(provisionalPlan != null && provisionalPlan.complete(),
                    "the provisional final-output plan must locate every saved land target");
            VanillaBiomeCoveragePlan.Anchor swampAnchor = provisionalPlan.anchors().stream()
                    .filter(anchor -> anchor.route() == BiomeRoute.TEMPERATE_WETLAND)
                    .findFirst()
                    .orElseThrow();
            WetlandEvidence wetland = new WetlandEvidence();
            wetland.add(new WetlandFootprint(
                    swampAnchor.blockX(),
                    swampAnchor.blockZ(),
                    swampAnchor.radiusBlocks()));
            LatitudeBiomes.clearWorldgenContext();
            Climate.Sampler sampler = coverageSampler(wetland);
            try {
                LatitudeBiomes.activateWorldgenContext(
                        radius,
                        seed,
                        LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                        providerProfile,
                        representation,
                        sampler,
                        null,
                        63);
            VanillaBiomeCoveragePlan plan = LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
            assertTrue(plan != null && plan.complete(),
                    "the final-output proof requires a complete birth-locked land plan");

            int samples = 0;
            for (VanillaBiomeCoveragePlan.Anchor anchor : plan.anchors()) {
                int half = anchor.radiusBlocks() / 2;
                int[][] offsets = {{0, 0}, {half, 0}, {-half, 0}, {0, half}, {0, -half}};
                for (int[] offset : offsets) {
                    int x = anchor.blockX() + offset[0];
                    int z = anchor.blockZ() + offset[1];
                    VanillaBiomeCoveragePlan.Anchor visible = plan.match(x, z);
                    assertTrue(visible != null && visible.biomeId().equals(anchor.biomeId()),
                            "every saved target remains visible at its center and four shoulders");
                    assertTrue(insideSyntheticRoute(anchor.route(), x, z, radius),
                            "the tested coverage sample remains inside its declared route");
                    Holder<Biome> registryOut = LatitudeBiomes.pick(
                            registry, neutral, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
                    Holder<Biome> collectionOut = LatitudeBiomes.pick(
                            pool, neutral, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
                    assertEquals(anchor.biomeId(), LatitudeBiomes.biomeIdPublic(registryOut),
                            "registry picker preserves the valid saved land target at final output");
                    assertEquals(anchor.biomeId(), LatitudeBiomes.biomeIdPublic(collectionOut),
                            "collection picker preserves the valid saved land target at final output");
                    if (anchor.route() == BiomeRoute.TEMPERATE_WETLAND
                            || anchor.route() == BiomeRoute.SUBPOLAR_WETLAND) {
                        ProvinceAuthority.Province province =
                                LatitudeBiomes.classifyProvince(x, z);
                        assertTrue(province != ProvinceAuthority.Province.WARM_DRY
                                        && province != ProvinceAuthority.Province.COLD_DRY,
                                "both public pickers may preserve wetland coverage only outside "
                                        + "an explicitly dry province");
                    }
                    samples++;
                }
            }
            assertEquals(plan.anchors().size() * 5, samples,
                    "every saved land target reaches final output at its center and four shoulders");

            List<VanillaBiomeCoveragePlan.Anchor> humidAnchors = plan.anchors().stream()
                    .filter(anchor -> anchor.route() == BiomeRoute.TROPICAL_HUMID_LOWLAND)
                    .toList();
            assertTrue(!humidAnchors.isEmpty(),
                    "each representation profile retains a descriptor-approved humid equivalent");
            if (radius == 10_000) {
                assertEquals(Set.of(
                                "minecraft:jungle",
                                "minecraft:bamboo_jungle",
                                "minecraft:sparse_jungle"),
                        humidAnchors.stream()
                                .map(VanillaBiomeCoveragePlan.Anchor::biomeId)
                                .collect(java.util.stream.Collectors.toSet()),
                        "the Regular profile retains every exact jungle-family target");
            }
            for (VanillaBiomeCoveragePlan.Anchor anchor : humidAnchors) {
                assertTrue(LatitudeBiomes.classifyProvince(anchor.blockX(), anchor.blockZ())
                                == ProvinceAuthority.Province.WARM_WET,
                        "humid coverage planning admits " + anchor.biomeId() + " only inside WARM_WET");
            }

            int[] medium = findUnreservedTropicalProvince(
                    plan, ProvinceAuthority.Province.WARM_MEDIUM, radius, false);
            int[] dry = findUnreservedTropicalProvince(
                    plan, ProvinceAuthority.Province.WARM_DRY, radius, false);
            // WARM_MEDIUM has two answers now, not one (maintainer approval, 2026-08-18). This
            // assertion used to demand EXACTLY minecraft:savanna here, which is the disease written
            // down as a test: a whole province with a single identity, no roll and no geography.
            // Savanna is a country inside the belt AND the dry fringe hugging an arid province;
            // everywhere else the belt is forest. Both predicates decide, so the suite asks them the
            // same pair of questions the picker does.
            String mediumExpected = LatitudeBiomes.savannaCountryHitForPolicyTest(
                    medium[0], medium[1], radius)
                    || LatitudeBiomes.savannaDryFringeHitForPolicyTest(medium[0], medium[1])
                    ? "minecraft:savanna"
                    : "minecraft:forest";
            for (String jungleId : List.of(
                    "minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle",
                    "terralith:tropical_jungle")) {
                Holder<Biome> jungle = testBiomeHolder(registry, jungleId);
                assertPickerPairReturns(
                        registry, pool, jungle, medium[0], medium[1], radius, sampler,
                        mediumExpected,
                        jungleId + " is rewritten by final admission in WARM_MEDIUM ("
                                + (mediumExpected.endsWith("savanna") ? "inside" : "outside")
                                + " a savanna country at " + medium[0] + "," + medium[1] + ")");
                assertPickerPairReturnsOneOf(
                        registry, pool, jungle, dry[0], dry[1], radius, sampler,
                        Set.of(
                                "minecraft:desert",
                                "minecraft:badlands",
                                "minecraft:wooded_badlands",
                                "minecraft:eroded_badlands",
                                "minecraft:savanna",
                                "minecraft:savanna_plateau",
                                "minecraft:windswept_savanna"),
                        jungleId + " is rewritten by final admission in WARM_DRY");
            }

            int[] dryUpland = findUnreservedTropicalProvince(
                    plan, ProvinceAuthority.Province.WARM_DRY, radius, true);
            assertPickerPairDoesNotReturn(
                    registry, pool, testBiomeHolder(registry, "minecraft:desert"),
                    dryUpland[0], dryUpland[1], radius, sampler,
                    "minecraft:desert",
                    "lowland desert is rejected by final physical admission on upland terrain");

            int[] wetSubtropical = findUnreservedWarmNonHotspotProvince(
                    plan, ProvinceAuthority.Province.WARM_WET, radius, 1, false);
            assertPickerPairReturns(
                    registry, pool, testBiomeHolder(registry, "minecraft:desert"),
                    wetSubtropical[0], wetSubtropical[1], radius, sampler,
                    "minecraft:jungle",
                    "ordinary desert is rewritten by final admission in non-hotspot WARM_WET at "
                            + wetSubtropical[0] + "," + wetSubtropical[1]);
            int[] drySubtropical = findUnreservedWarmNonHotspotProvince(
                    plan, ProvinceAuthority.Province.WARM_DRY, radius, 1, false);
            Set<String> aridLowlandIds = Set.of(
                    "minecraft:desert",
                    "minecraft:badlands",
                    "minecraft:wooded_badlands",
                    "minecraft:eroded_badlands");
            assertPickerPairEachReturnsOneOf(
                    registry, pool, testBiomeHolder(registry, "minecraft:desert"),
                    drySubtropical[0], drySubtropical[1], radius, sampler,
                    aridLowlandIds,
                    "ordinary desert remains in the arid lowland family in WARM_DRY terrain at "
                            + drySubtropical[0] + "," + drySubtropical[1]);

            if (radius == 10_000) {
                int[] temperateLowland = findUnreservedBandCoordinate(plan, radius, 0.43, false);
                assertPickerPairDoesNotReturn(
                        registry, pool, testBiomeHolder(registry, "minecraft:jungle"),
                        temperateLowland[0], temperateLowland[1], radius, sampler,
                        "minecraft:jungle",
                        "jungle moved to the wrong latitude band is rewritten");
                assertPickerPairDoesNotReturn(
                        registry, pool, testBiomeHolder(registry, "minecraft:swamp"),
                        temperateLowland[0], temperateLowland[1], radius, sampler,
                        "minecraft:swamp",
                        "a wetland target without wetland evidence is rewritten");

                int[] dryTemperateWetland = findUnreservedColdProvince(
                        plan, ProvinceAuthority.Province.COLD_DRY, radius, 2, false);
                WetlandFootprint dryEvidence = new WetlandFootprint(
                        dryTemperateWetland[0], dryTemperateWetland[1], 64);
                wetland.add(dryEvidence);
                try {
                    assertFalse(LatitudeBiomes.isPotentialWetlandLocateCandidate(
                                    dryTemperateWetland[0], dryTemperateWetland[1], radius,
                                    sampler, true, false),
                            "the wetland locator must not spend an exact probe on COLD_DRY swamp");
                } finally {
                    wetland.remove(dryEvidence);
                }

                int[] wetTemperateWetland = findUnreservedColdProvince(
                        plan, ProvinceAuthority.Province.COLD_WET, radius, 2, false);
                WetlandFootprint wetEvidence = new WetlandFootprint(
                        wetTemperateWetland[0], wetTemperateWetland[1], 64);
                wetland.add(wetEvidence);
                try {
                    assertTrue(LatitudeBiomes.isPotentialWetlandLocateCandidate(
                                    wetTemperateWetland[0], wetTemperateWetland[1], radius,
                                    sampler, true, false),
                            "the wetland locator retains a climate-valid COLD_WET swamp");
                } finally {
                    wetland.remove(wetEvidence);
                }

                assertOrdinaryTemperateTaigaStillUsesInteriorGate(registry, pool, neutral, radius);

                int[] subpolarLowland = findUnreservedBandCoordinate(plan, radius, 0.62, false);
                assertPickerPairDoesNotReturn(
                        registry, pool, testBiomeHolder(registry, "minecraft:windswept_forest"),
                        subpolarLowland[0], subpolarLowland[1], radius, sampler,
                        "minecraft:windswept_forest",
                        "subpolar-upland identity is rejected on final lowland terrain");

                // The positive control needs a cell that Latitude's own wetland
                // preselection genuinely owns once wetland evidence exists. Synthetic
                // climate evidence alone cannot conjure a wetland on a column whose
                // patch noise never fires, and only a genuinely wet province admits a
                // wetland at all, so sweep the columns each land-coverage anchor actually
                // reserves — not just its centre, which is a candidate pool far too small
                // to expect a wet province and firing swamp terrain to coincide — and keep
                // the first column the registry picker really owns as a wetland. If none
                // survives, land coverage is erasing wetland-owned cells.
                int[] unrelated = null;
                sweep:
                for (VanillaBiomeCoveragePlan.Anchor anchor : plan.anchors()) {
                    if (anchor.route() != BiomeRoute.TEMPERATE_LOWLAND) {
                        continue;
                    }
                    int reach = anchor.radiusBlocks();
                    int step = Math.max(16, reach / 8);
                    for (int dz = -reach; dz <= reach; dz += step) {
                        for (int dx = -reach; dx <= reach; dx += step) {
                            if (dx * dx + dz * dz > reach * reach) {
                                continue;
                            }
                            int probeX = anchor.blockX() + dx;
                            int probeZ = anchor.blockZ() + dz;
                            ProvinceAuthority.Province province =
                                    LatitudeBiomes.classifyProvince(probeX, probeZ);
                            if (province != ProvinceAuthority.Province.WARM_WET
                                    && province != ProvinceAuthority.Province.COLD_WET) {
                                continue;
                            }
                            WetlandFootprint probe = new WetlandFootprint(probeX, probeZ, reach);
                            wetland.add(probe);
                            try {
                                if ("minecraft:swamp".equals(LatitudeBiomes.biomeIdPublic(
                                        LatitudeBiomes.pick(
                                                registry,
                                                testBiomeHolder(registry, "minecraft:swamp"),
                                                probeX, probeZ, 80, radius,
                                                sampler, "ATLAS_SAMPLER")))) {
                                    unrelated = new int[] {probeX, probeZ, reach};
                                    break sweep;
                                }
                            } finally {
                                wetland.remove(probe);
                            }
                        }
                    }
                }
                assertTrue(unrelated != null,
                        "no wet temperate-lowland coverage column keeps a validated wetland — "
                                + "land coverage is erasing wetland-owned cells");
                int[] paleAnchor = LatitudeBiomes.paleGardenAnchorForPolicyTest(sampler);
                assertPickerPairReturns(
                        registry, pool, testBiomeHolder(registry, "minecraft:pale_garden"),
                        paleAnchor[0], paleAnchor[1], radius, sampler,
                        "minecraft:pale_garden",
                        "the real Pale Garden authority survives final selection");
                WetlandFootprint added = new WetlandFootprint(
                        unrelated[0], unrelated[1], unrelated[2]);
                wetland.add(added);
                try {
                    assertPickerPairReturns(
                            registry, pool, testBiomeHolder(registry, "minecraft:swamp"),
                            unrelated[0], unrelated[1], radius, sampler,
                            "minecraft:swamp",
                            "unrelated land coverage does not erase a validated wetland");
                } finally {
                    wetland.remove(added);
                }
            }
            } finally {
                LatitudeBiomes.clearWorldgenContext();
            }
        }

        String source;
        try {
            source = Files.readString(Path.of(
                    "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        } catch (Exception failure) {
            throw new AssertionError("unable to inspect final land-coverage authority", failure);
        }
        assertTrue(source.contains(
                        "case TROPICAL_HUMID_LOWLAND -> band == BAND_TROPICAL && !mountain\n"
                                + "                    && province == ProvinceAuthority.Province.WARM_WET;"),
                "jungle-family coverage cannot be planned in WARM_MEDIUM or WARM_DRY");
        assertTrue(source.contains("mayReplaceWithVanillaLandCoverage(out, anchor.route())"),
                "both coverage resolvers share the stronger-authority protection predicate");
        assertTrue(source.contains("&& !aridHotspotHere(WORLD_SEED, blockX, blockZ);"),
                "the inverse humidity gate preserves explicitly detected arid hotspots");
        assertEquals(5, occurrences(source, "&& wetlandProvinceEligible(blockX, blockZ)"),
                "coverage planning, both final wetland authorities, and the locator broad phase "
                        + "must share the wet-province admission law");
    }

    private static int[] findUnreservedTropicalProvince(
            VanillaBiomeCoveragePlan plan,
            ProvinceAuthority.Province province,
            int radius,
            boolean upland) {
        int tropicalLimit = radius / 5;
        for (int z = -tropicalLimit; z <= tropicalLimit; z += 47) {
            for (int x = -radius / 2; x <= radius / 2; x += 53) {
                if (syntheticUpland(x) != upland || plan.match(x, z) != null) continue;
                if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                if (LatitudeBiomes.classifyProvince(x, z) == province) {
                    return new int[]{x, z};
                }
            }
        }
        throw new AssertionError("no unreserved synthetic " + province
                + (upland ? " upland" : " lowland") + " coordinate");
    }

    private static int[] findUnreservedWarmNonHotspotProvince(
            VanillaBiomeCoveragePlan plan,
            ProvinceAuthority.Province province,
            int radius,
            int bandIndex,
            boolean upland) {
        for (int z = -radius; z <= radius; z += 47) {
            for (int x = -radius / 2; x <= radius / 2; x += 53) {
                if (syntheticUpland(x) != upland || plan.match(x, z) != null) continue;
                if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                double latitudeDegrees = Math.abs((double) z) * 90.0 / (double) radius;
                if (latitudeDegrees < 27.0 || latitudeDegrees > 33.0) continue;
                if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius)
                        != bandIndex) continue;
                if (AridLatitudePolicy.replacementFor(true, z, radius, 23.5, 35.5)
                        != AridLatitudePolicy.Replacement.KEEP) continue;
                if (LatitudeBiomes.classifyProvince(x, z) != province) continue;
                if (!LatitudeBiomes.debugAridHotspot(x, z)) {
                    return new int[]{x, z};
                }
            }
        }
        throw new AssertionError("no unreserved synthetic " + province
                + (upland ? " upland" : " lowland")
                + " coordinate in band " + bandIndex
                + " outside an arid hotspot");
    }

    private static int[] findUnreservedBandCoordinate(
            VanillaBiomeCoveragePlan plan,
            int radius,
            double zFraction,
            boolean upland) {
        int z = (int) Math.round(radius * zFraction);
        for (int x = -radius / 2; x <= radius / 2; x += 53) {
            if (syntheticUpland(x) != upland || plan.match(x, z) != null) continue;
            if ((long) x * x + (long) z * z < (long) radius * radius) {
                return new int[]{x, z};
            }
        }
        throw new AssertionError("no unreserved synthetic band coordinate at z/radius=" + zFraction);
    }

    private static int[] findUnreservedColdProvince(
            VanillaBiomeCoveragePlan plan,
            ProvinceAuthority.Province province,
            int radius,
            int bandIndex,
            boolean upland) {
        for (int z = -radius; z <= radius; z += 47) {
            for (int x = -radius / 2; x <= radius / 2; x += 53) {
                if (syntheticUpland(x) != upland || plan.match(x, z) != null) continue;
                if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius)
                        != bandIndex) continue;
                if (LatitudeBiomes.classifyProvince(x, z) == province) {
                    return new int[]{x, z};
                }
            }
        }
        throw new AssertionError("no unreserved synthetic " + province
                + (upland ? " upland" : " lowland")
                + " coordinate in band " + bandIndex);
    }

    private static Holder<Biome> testBiomeHolder(MappedRegistry<Biome> registry, String id) {
        return registry.get(ResourceKey.create(Registries.BIOME, Identifier.parse(id)))
                .orElseThrow();
    }

    private static void assertOrdinaryTemperateTaigaStillUsesInteriorGate(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> neutral,
            int radius) throws Exception {
        Holder<Biome> taiga = testBiomeHolder(registry, "minecraft:old_growth_pine_taiga");
        Set<String> transitionIds = Set.of(
                "minecraft:plains",
                "minecraft:sunflower_plains",
                "minecraft:flower_forest",
                "minecraft:birch_forest",
                "minecraft:old_growth_birch_forest");
        java.lang.reflect.Method clear = LatitudeBiomes.class.getDeclaredMethod("clearSelectionState");
        clear.setAccessible(true);
        java.lang.reflect.Method registryGate = LatitudeBiomes.class.getDeclaredMethod(
                "gateTemperateTaigaInterior",
                Registry.class,
                Holder.class,
                Holder.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                boolean.class);
        registryGate.setAccessible(true);
        java.lang.reflect.Method collectionGate = LatitudeBiomes.class.getDeclaredMethod(
                "gateTemperateTaigaInterior",
                java.util.Collection.class,
                Holder.class,
                Holder.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                boolean.class);
        collectionGate.setAccessible(true);

        int blockX = 0;
        int blockZ = radius / 2;
        int temperateBand = 2;
        clear.invoke(null);
        @SuppressWarnings("unchecked")
        Holder<Biome> registryOut = (Holder<Biome>) registryGate.invoke(
                null,
                registry,
                neutral,
                taiga,
                blockX,
                blockZ,
                radius,
                temperateBand,
                temperateBand,
                false);
        clear.invoke(null);
        @SuppressWarnings("unchecked")
        Holder<Biome> collectionOut = (Holder<Biome>) collectionGate.invoke(
                null,
                pool,
                neutral,
                taiga,
                blockX,
                blockZ,
                radius,
                temperateBand,
                temperateBand,
                false);
        String registryId = LatitudeBiomes.biomeIdPublic(registryOut);
        String collectionId = LatitudeBiomes.biomeIdPublic(collectionOut);
        assertTrue(transitionIds.contains(registryId),
                "ordinary non-coverage temperate taiga still reaches the registry interior gate: "
                        + registryId);
        assertTrue(transitionIds.contains(collectionId),
                "ordinary non-coverage temperate taiga still reaches the collection interior gate: "
                        + collectionId);
        assertEquals(registryId, collectionId,
                "ordinary non-coverage temperate taiga keeps picker parity");
    }

    private static void assertPickerPairReturns(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int x,
            int z,
            int radius,
            Climate.Sampler sampler,
            String expectedId,
            String message) {
        Holder<Biome> registryOut = LatitudeBiomes.pick(
                registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        Holder<Biome> collectionOut = LatitudeBiomes.pick(
                pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        assertEquals(expectedId, LatitudeBiomes.biomeIdPublic(registryOut), message + " (registry)");
        assertEquals(expectedId, LatitudeBiomes.biomeIdPublic(collectionOut), message + " (collection)");
    }

    private static void assertPickerPairReturnsOneOf(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int x,
            int z,
            int radius,
            Climate.Sampler sampler,
            Set<String> expectedIds,
            String message) {
        Holder<Biome> registryOut = LatitudeBiomes.pick(
                registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        Holder<Biome> collectionOut = LatitudeBiomes.pick(
                pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        String registryId = LatitudeBiomes.biomeIdPublic(registryOut);
        String collectionId = LatitudeBiomes.biomeIdPublic(collectionOut);
        assertTrue(expectedIds.contains(registryId), message + " (registry): " + registryId);
        assertTrue(expectedIds.contains(collectionId), message + " (collection): " + collectionId);
        assertEquals(registryId, collectionId, message + " keeps picker parity");
    }

    private static void assertPickerPairEachReturnsOneOf(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int x,
            int z,
            int radius,
            Climate.Sampler sampler,
            Set<String> expectedIds,
            String message) {
        Holder<Biome> registryOut = LatitudeBiomes.pick(
                registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        Holder<Biome> collectionOut = LatitudeBiomes.pick(
                pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        String registryId = LatitudeBiomes.biomeIdPublic(registryOut);
        String collectionId = LatitudeBiomes.biomeIdPublic(collectionOut);
        assertTrue(expectedIds.contains(registryId), message + " (registry): " + registryId);
        assertTrue(expectedIds.contains(collectionId), message + " (collection): " + collectionId);
    }

    private static void assertPickerPairDoesNotReturn(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int x,
            int z,
            int radius,
            Climate.Sampler sampler,
            String rejectedId,
            String message) {
        Holder<Biome> registryOut = LatitudeBiomes.pick(
                registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        Holder<Biome> collectionOut = LatitudeBiomes.pick(
                pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER");
        assertFalse(rejectedId.equals(LatitudeBiomes.biomeIdPublic(registryOut)), message + " (registry)");
        assertFalse(rejectedId.equals(LatitudeBiomes.biomeIdPublic(collectionOut)), message + " (collection)");
        assertEquals(LatitudeBiomes.biomeIdPublic(registryOut), LatitudeBiomes.biomeIdPublic(collectionOut),
                message + " keeps picker parity");
    }

    /**
     * Both public picker paths reject one identity at one column, without also demanding they
     * agree on the replacement.
     *
     * <p>The pair helper above folds a parity check into every rejection, which is right at a
     * hand-chosen column and wrong for a sweep: the two sources legitimately disagree about WHICH
     * alpine sibling replaces a rejected polar pick (measured: registry snowy_slopes vs collection
     * frozen_peaks at x=-1536 z=7800). A sweep that also asserted parity would fail on that
     * unrelated divergence and say nothing about the identity actually under test.
     */
    private static void assertNeitherPickerReturns(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int x,
            int z,
            int radius,
            Climate.Sampler sampler,
            String rejectedId,
            String message) {
        assertFalse(rejectedId.equals(LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                        registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"))),
                message + " (registry)");
        assertFalse(rejectedId.equals(LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                        pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"))),
                message + " (collection)");
    }

    private static Climate.Sampler coverageSampler(WetlandEvidence wetland) {
        var zero = new TestDensitySampler((x, z) -> 0.0).bind(SamplerContext.EMPTY_UNCACHED);
        var continentalness = new TestDensitySampler(
                (x, z) -> wetland == null || wetland.contains(x, z) ? 0.35 : 0.75)
                .bind(SamplerContext.EMPTY_UNCACHED);
        var erosion = new TestDensitySampler((x, z) -> syntheticUpland(x) ? -0.6 : 0.0)
                .bind(SamplerContext.EMPTY_UNCACHED);
        var weirdness = new TestDensitySampler((x, z) -> syntheticUpland(x) ? 0.6 : 0.0)
                .bind(SamplerContext.EMPTY_UNCACHED);
        return new Climate.Sampler(
                zero,
                zero,
                continentalness,
                erosion,
                zero,
                weirdness);
    }

    private static boolean syntheticUpland(int blockX) {
        int stripe = Math.floorMod(blockX, 512);
        return stripe <= 96 || stripe >= 416;
    }

    /**
     * True when LatitudeBiomes' own mountain-noise read fires on this column under
     * {@link #coverageSampler}.
     *
     * <p>Not the same predicate as {@link #syntheticUpland} applied to the block coordinate, and
     * the difference is a trap worth naming. {@code isMountainLike} asks the climate sampler at
     * QUART resolution ({@code blockX >> 2}), and the sampler converts back to block space before
     * the density function sees it — so the synthetic erosion/weirdness stripe is read at
     * {@code blockX & ~3}, the column snapped down to a 4-block boundary. Filtering a sweep with
     * the raw block coordinate quietly admits mountain columns: measured through the picker,
     * x=-3999 and x=-604 both have {@code syntheticUpland(x) == false} yet are mountains
     * ({@code -3999 & ~3 == -4000}, stripe 96; {@code -604 & ~3 == -604}, stripe 420), while
     * x=-701 ({@code & ~3 == -704}, stripe 320) is genuinely flat. Asserting "flat" on the first
     * two would be asserting that a legal windswept mountain must not exist.
     */
    private static boolean mountainNoiseColumn(int blockX) {
        return syntheticUpland(blockX & ~3);
    }

    private record WetlandFootprint(int blockX, int blockZ, int halfWidth) {
        boolean contains(int x, int z) {
            return Math.abs(x - blockX) <= halfWidth
                    && Math.abs(z - blockZ) <= halfWidth;
        }
    }

    private static final class WetlandEvidence {
        private final List<WetlandFootprint> footprints = new java.util.ArrayList<>();

        void add(WetlandFootprint footprint) {
            footprints.add(footprint);
        }

        void remove(WetlandFootprint footprint) {
            footprints.remove(footprint);
        }

        boolean contains(int x, int z) {
            return footprints.stream().anyMatch(footprint -> footprint.contains(x, z));
        }
    }

    private static MappedRegistry<Biome> testBiomeRegistry() {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Set<String> ids = new java.util.TreeSet<>(registryFor(Set.of()));
        ids.add("minecraft:pale_garden");
        ids.add("terralith:tropical_jungle");
        for (String id : ids) {
            registry.register(
                    ResourceKey.create(Registries.BIOME, Identifier.parse(id)),
                    testBiome(),
                    RegistrationInfo.BUILT_IN);
        }
        registry.freeze();
        registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(
                Registries.BIOME, Map.of())).apply();
        return registry;
    }

    private static Biome testBiome() {
        EnvironmentAttributeMap.Builder attributes = EnvironmentAttributeMap.builder();
        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.8F)
                .downfall(0.4F)
                .putAttributes(attributes)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }

    private record TestDensitySampler(CoordinateValue value) implements DensitySampler {
        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (float) value.at(blockX, blockZ);
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer buffer, DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, buffer, volume, this);
        }
    }

    @FunctionalInterface
    private interface CoordinateValue {
        double at(int blockX, int blockZ);
    }

    private static void groveLocateFallsBackToExactLandCoverageAnchor() throws Exception {
        int radius = 10_000;
        BiomeSelectionProfile vanilla = BiomeSelectionProfile.capture(registryFor(Set.of()));
        VanillaBiomeCoveragePlan plan = VanillaBiomeCoveragePlan.build(
                radius,
                41L,
                vanilla,
                Map.of("minecraft:grove", BiomeRoute.TEMPERATE_UPLAND),
                (id, route, x, z) -> insideSyntheticRoute(route, x, z, radius));
        assertTrue(plan.complete(), "the focused grove reservation must be available");
        VanillaBiomeCoveragePlan.Anchor reserved = plan.anchors().getFirst();
        VanillaBiomeCoveragePlan.Anchor located = plan.nearestAnchorFor(
                Set.of("minecraft:grove"), -reserved.blockX(), -reserved.blockZ());
        assertEquals(reserved, located,
                "a missed bounded scan resolves the exact reserved grove anchor");
        assertEquals(reserved.blockX(), located.blockX(),
                "planned grove locate keeps the reserved X coordinate");
        assertEquals(reserved.blockZ(), located.blockZ(),
                "planned grove locate keeps the reserved Z coordinate");
        assertTrue(plan.nearestAnchorFor(Set.of("minecraft:the_void"), 0, 0) == null,
                "land locate cannot invent an unreserved biome identity");

        List<BlockPos> samples = LatitudeBiomeSource.plannedLandCoverageSamplePositions(
                reserved, new BlockPos(reserved.blockX() + reserved.radiusBlocks(), 80, reserved.blockZ()));
        int half = reserved.radiusBlocks() / 2;
        assertEquals(5, samples.size(),
                "planned land locate checks the centre and all four planner-certified shoulders");
        assertEquals(new BlockPos(reserved.blockX() + half, 80, reserved.blockZ()), samples.getFirst(),
                "planned land locate returns the nearest final-output sample first");
        assertTrue(samples.contains(new BlockPos(reserved.blockX(), 80, reserved.blockZ()))
                        && samples.contains(new BlockPos(reserved.blockX() - half, 80, reserved.blockZ()))
                        && samples.contains(new BlockPos(reserved.blockX(), 80, reserved.blockZ() + half))
                        && samples.contains(new BlockPos(reserved.blockX(), 80, reserved.blockZ() - half)),
                "planned land locate retains every planner-certified center-or-shoulder sample");

        String locateSource = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        int preview = locateSource.indexOf("for (BlockPos.MutableBlockPos offset : BlockPos.spiralAround");
        int coarse = locateSource.indexOf("Pair<BlockPos, Holder<Biome>> fallback =");
        int planned = locateSource.indexOf(
                "? findPlannedSurfaceCoverage(matching, origin, target, sampler)");
        assertTrue(preview >= 0 && preview < coarse && coarse < planned,
                "the land plan is consulted only after preview and coarse exact searches miss");
        assertTrue(locateSource.contains("nearestPlannedLandCoverageAnchor(")
                        && locateSource.contains("findPlannedSurfaceWaterCoverage("),
                "the terminal fallback adds exact land anchors without removing water coverage");
        assertTrue(locateSource.contains("plannedLandCoverageSamplePositions(anchor, origin)")
                        && locateSource.contains("had no final surviving center-or-shoulder sample"),
                "a saved land anchor is verified at every certified footprint sample and reports a true exhaustion");

        String serviceSource = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeBiomeLocateService.java"));
        int tickCoarse = serviceSource.indexOf("if (fallbackOffsets.hasNext())");
        int tickPlanned = serviceSource.indexOf("latitudeSource.findPlannedSurfaceCoverage(");
        assertTrue(tickCoarse >= 0 && tickCoarse < tickPlanned,
                "the tick-sliced command reaches the land plan only after its coarse fallback misses");
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

    private static void caveCoverageIsClosedAndWorldSizeSafe() throws Exception {
        List<String> required = List.of(
                "minecraft:deep_dark", "minecraft:dripstone_caves",
                "minecraft:lush_caves", "minecraft:sulfur_caves");
        assertEquals(Set.copyOf(required), CaveBiomeRepresentationProfile.mandatoryIds().keySet(),
                "the four native cave identities are mandatory, exact, and closed");

        BiomeSelectionProfile combined = BiomeSelectionProfile.capture(
                registryFor(Set.of("biomesoplenty", "terralith", "clifftree")));
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
        assertEquals(21L, combined.entries(BiomeRoute.CAVE_SHALLOW).stream()
                        .filter(id -> !id.startsWith("minecraft:")).count()
                        + combined.entries(BiomeRoute.CAVE_DEEP).stream()
                        .filter(id -> !id.startsWith("minecraft:")).count(),
                "only the explicit 2 BOP, 11 Terralith, and 8 CliffTree caves are eligible custom candidates");

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
                assertEquals(5, showcase.size(),
                        "combined stack contributes one deterministic showcase per provider and cave-depth route");
                assertTrue(showcase.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("biomesoplenty:")
                                && entry.getValue() == BiomeRoute.CAVE_SHALLOW),
                        "BOP cave selection remains shallow-only");
                assertTrue(showcase.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("terralith:")
                                && entry.getValue() == BiomeRoute.CAVE_DEEP),
                        "Terralith deep cave selection remains below the deep threshold");
                assertTrue(showcase.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("clifftree:")
                                && entry.getValue() == BiomeRoute.CAVE_SHALLOW),
                        "CliffTree contributes exactly one deterministic shallow showcase");
                assertTrue(showcase.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("clifftree:")
                                && entry.getValue() == BiomeRoute.CAVE_DEEP),
                        "CliffTree inferno keeps its deep showcase without promoting all seven shallow caves");
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
                "the V4 override is donor-cave-gated and cannot create a surface cave");
        assertTrue(source.contains("LatitudeBiomes.caveCoverageOverride")
                        && mixin.contains("return LatitudeBiomes.caveCoverageOverride(biomes, current, blockX, blockY, blockZ);"),
                "biome-source, locate-preview, and chunk-population paths share final cave identity");
        assertTrue(state.contains("PROVIDER_TICKET_V1") && state.contains("PROVIDER_TICKET_V2_COVERAGE")
                        && state.contains("PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE")
                        && state.contains("PROVIDER_TICKET_V4_CAVE_COVERAGE"),
                "legacy/V1/V2/V3 policies remain explicit alongside fresh-only V4");
        assertTrue(mod.contains("CaveBiomeRepresentationProfile.capture(captureRadius, profile)")
                        && mod.contains("PROVIDER_TICKET_V4_CAVE_COVERAGE"),
                "only a newly created world captures the V4 cave profile before spawn generation");
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
                        && biomes.contains("PROVIDER_TICKET_V4_CAVE_COVERAGE"),
                "V2 keeps exact coverage while V3/V4 consume the saved size-aware surface contract");
        assertTrue(biomes.contains("ACTIVE_VANILLA_COVERAGE_PLAN = null;"),
                "world/context cleanup clears the coverage plan");
        assertTrue(biomes.contains("ACTIVE_SURFACE_WATER_COVERAGE_PLAN = null;"),
                "world/context cleanup clears the surface/water coverage plan");
        assertTrue(biomes.contains("ACTIVE_CAVE_COVERAGE_PLAN = null;"),
                "world/context cleanup clears birth-locked cave coverage");
        assertTrue(state.contains("policy == WorldgenPolicyVersion.PROVIDER_TICKET_V1")
                        && state.contains("policy == WorldgenPolicyVersion.PROVIDER_TICKET_V2_COVERAGE")
                        && state.contains("policy == WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE"),
                "saved V1/V2/V3 profiles remain readable without adopting V4 behavior");
        assertTrue(mod.contains(
                        "WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE"),
                "only freshly UI-created worlds opt into V4 size-aware coverage");
        assertTrue(mod.contains("randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED)"),
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
        assertEquals(52, classifiedSurface.size(),
                "all 52 vanilla Overworld surface identities are classified by land, water, or Pale Garden authority");
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
                        && state.contains("PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE"),
                "V3 profile and policy are persisted without changing legacy/V1/V2 identities");
        assertTrue(mod.contains("VanillaBiomeRepresentationProfile.capture(captureRadius, seed, profile)")
                        && mod.contains("PROVIDER_TICKET_V4_CAVE_COVERAGE")
                        && mod.contains("boolean creationWindow = world.getGameTime() < 100L;"),
                "only the birth-locked fresh-world path (create screen OR fresh dedicated world, "
                        + "inside the creation window) captures the V4 surface/cave profiles");
        assertThrows(() -> VanillaBiomeRepresentationProfile.decode(
                        VanillaBiomeRepresentationProfile.FORMAT
                                + "\nSIZE|ITTY_BITTY\nLAND|TEMPERATE_LOWLAND|REPRESENTATION_FAMILY|terralith:caldera"),
                "a custom biome cannot be admitted through a mismatched route");
    }

    /**
     * The first water column off a beach was labeling itself deep_ocean: outside the tropics the
     * band pickers drew from tag pools holding shallow and deep members together, and only band 0
     * ever consulted the donor's own depth. Every band's ocean identity now follows the donor —
     * a shallow donor may never resolve deep, and a deep donor may never resolve shallow. The
     * fixture registry carries no tags, so this drives the depth-matched vanilla fallback arm;
     * the tag arm shares the same depth filters, pinned below on hand-built pools.
     */
    private static void oceanIdentityFollowsDonorDepthInEveryBand() throws Exception {
        // The shared fixture is land-only (the ledger carries no oceans), so this test builds its
        // own registry carrying the vanilla ocean roster the band pickers fall back to.
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Set<String> ids = new java.util.TreeSet<>(registryFor(Set.of()));
        ids.addAll(List.of(
                "minecraft:ocean", "minecraft:deep_ocean",
                "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
                "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"));
        for (String id : ids) {
            registry.register(
                    ResourceKey.create(Registries.BIOME, Identifier.parse(id)),
                    testBiome(),
                    RegistrationInfo.BUILT_IN);
        }
        registry.freeze();
        registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(
                Registries.BIOME, Map.of())).apply();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        java.lang.reflect.Method registryOcean = LatitudeBiomes.class.getDeclaredMethod(
                "oceanByLatitudeBandOrBase",
                Registry.class, Holder.class, int.class, int.class, int.class);
        registryOcean.setAccessible(true);
        java.lang.reflect.Method collectionOcean = LatitudeBiomes.class.getDeclaredMethod(
                "oceanByLatitudeBandOrBase",
                java.util.Collection.class, Holder.class, int.class, int.class, int.class);
        collectionOcean.setAccessible(true);
        Holder<Biome> shallowDonor = testBiomeHolder(registry, "minecraft:ocean");
        Holder<Biome> deepDonor = testBiomeHolder(registry, "minecraft:deep_ocean");
        LatitudeBiomes.clearWorldgenContext();
        try {
            LatitudeBiomes.setRadius(10_000);
            LatitudeBiomes.setWorldSeed(41L);
            for (int band = 1; band <= 4; band++) {
                for (int[] at : new int[][]{{-4_211, 3_007}, {977, -6_133}, {5_501, 5_501}}) {
                    Holder<Biome> fromShallowRegistry = castOceanPick(
                            registryOcean.invoke(null, registry, shallowDonor, at[0], at[1], band));
                    Holder<Biome> fromDeepRegistry = castOceanPick(
                            registryOcean.invoke(null, registry, deepDonor, at[0], at[1], band));
                    Holder<Biome> fromShallowPool = castOceanPick(
                            collectionOcean.invoke(null, pool, shallowDonor, at[0], at[1], band));
                    Holder<Biome> fromDeepPool = castOceanPick(
                            collectionOcean.invoke(null, pool, deepDonor, at[0], at[1], band));
                    for (Holder<Biome> shallowOut : List.of(fromShallowRegistry, fromShallowPool)) {
                        String id = LatitudeBiomes.biomeIdPublic(shallowOut);
                        assertTrue(id.contains("ocean") && !id.contains("deep"),
                                "a shallow ocean donor must stay shallow in band " + band
                                        + " (was " + id + ")");
                    }
                    for (Holder<Biome> deepOut : List.of(fromDeepRegistry, fromDeepPool)) {
                        String id = LatitudeBiomes.biomeIdPublic(deepOut);
                        assertTrue(id.contains("ocean") && id.contains("deep"),
                                "a deep ocean donor must stay deep in band " + band
                                        + " (was " + id + ")");
                    }
                }
            }

            // The tag arm relies on these depth filters; pin the split on a mixed pool.
            java.lang.reflect.Method shallowFilter = LatitudeBiomes.class.getDeclaredMethod(
                    "filterShallowOcean", List.class);
            shallowFilter.setAccessible(true);
            java.lang.reflect.Method deepFilter = LatitudeBiomes.class.getDeclaredMethod(
                    "filterDeepOcean", List.class);
            deepFilter.setAccessible(true);
            List<Holder<Biome>> mixed = List.of(
                    testBiomeHolder(registry, "minecraft:cold_ocean"),
                    testBiomeHolder(registry, "minecraft:deep_cold_ocean"),
                    testBiomeHolder(registry, "minecraft:frozen_ocean"),
                    testBiomeHolder(registry, "minecraft:deep_frozen_ocean"));
            @SuppressWarnings("unchecked")
            List<Holder<Biome>> shallowSide = (List<Holder<Biome>>) shallowFilter.invoke(null, mixed);
            @SuppressWarnings("unchecked")
            List<Holder<Biome>> deepSide = (List<Holder<Biome>>) deepFilter.invoke(null, mixed);
            assertEquals(2, shallowSide.size(), "the shallow filter keeps exactly the shallow members");
            assertEquals(2, deepSide.size(), "the deep filter keeps exactly the deep members");
            for (Holder<Biome> entry : shallowSide) {
                assertTrue(!LatitudeBiomes.biomeIdPublic(entry).contains("deep"),
                        "the shallow filter must not pass a deep member");
            }
            for (Holder<Biome> entry : deepSide) {
                assertTrue(LatitudeBiomes.biomeIdPublic(entry).contains("deep"),
                        "the deep filter must not pass a shallow member");
            }
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome> castOceanPick(Object picked) {
        return (Holder<Biome>) picked;
    }

    /**
     * The equatorward mirror of the poleward arid ramp: jitter, blend, and warp could promote a
     * true-33-degree column to the TEMPERATE pool, planting mesic-forest islands inside the dry
     * belt. The clamp demotes every temperate resolution at or below true 34.5 degrees, ramps
     * noise-warped across 34.5..35.5, and keeps true temperate untouched. This drives the real
     * jittered/blended/warped resolver, so pre-clamp some swept columns genuinely resolved
     * temperate — the teeth of the equatorward assertion.
     */
    private static void temperatePoolNeverResolvesEquatorwardOfTheTrueLine() {
        int radius = 10_000;
        LatitudeBiomes.clearWorldgenContext();
        try {
            LatitudeBiomes.setRadius(radius);
            for (long seed : new long[]{41L, 461L}) {
                LatitudeBiomes.setWorldSeed(seed);
                int equatorwardTemperate = 0;
                boolean sawTrueTemperate = false;
                for (int z = 2_900; z <= 3_833; z += 13) {
                    for (int x = -6_000; x <= 6_000; x += 97) {
                        int band = LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius);
                        if (band == 2) {
                            equatorwardTemperate++;
                        }
                        int mirrored = LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, -z, radius);
                        if (mirrored == 2) {
                            equatorwardTemperate++;
                        }
                    }
                }
                assertEquals(0, equatorwardTemperate,
                        "the TEMPERATE pool must never resolve at or below true 34.5 degrees "
                                + "(seed " + seed + ")");
                for (int z = 3_950; z <= 5_400 && !sawTrueTemperate; z += 31) {
                    for (int x = -6_000; x <= 6_000; x += 97) {
                        if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius) == 2) {
                            sawTrueTemperate = true;
                            break;
                        }
                    }
                }
                assertTrue(sawTrueTemperate,
                        "true temperate columns must keep resolving temperate (seed " + seed + ")");
                for (int z = 3_834; z <= 3_943; z += 7) {
                    for (int x = -6_000; x <= 6_000; x += 193) {
                        int band = LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius);
                        assertTrue(band == 1 || band == 2,
                                "the ramp ring resolves only subtropical or temperate (seed "
                                        + seed + ", was band " + band + ")");
                    }
                }
            }
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    /**
     * A quarantined custom beach pick used to reroll through the LAND pool, so a beach column
     * became a land biome — and because the subtropical land pool deliberately seeds swamp and
     * mangrove, the beach shortcut (which returns before the final wetland authority) could
     * conjure an ungated wetland on the shoreline. A quarantined beach pick must now resolve to
     * the band's vanilla beach identity, and a vanilla pick must pass through untouched.
     */
    private static void beachShortcutQuarantineRestoresBeachIdentity() throws Exception {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Set<String> ids = new java.util.TreeSet<>(registryFor(Set.of()));
        ids.addAll(List.of(
                "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore",
                "examplemod:crystal_shore"));
        for (String id : ids) {
            registry.register(
                    ResourceKey.create(Registries.BIOME, Identifier.parse(id)),
                    testBiome(),
                    RegistrationInfo.BUILT_IN);
        }
        registry.freeze();
        registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(
                Registries.BIOME, Map.of())).apply();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        java.lang.reflect.Method registryQuarantine = LatitudeBiomes.class.getDeclaredMethod(
                "quarantineUnknownCustomBeachBiome",
                Registry.class, Holder.class, Holder.class, int.class, int.class, int.class);
        registryQuarantine.setAccessible(true);
        java.lang.reflect.Method collectionQuarantine = LatitudeBiomes.class.getDeclaredMethod(
                "quarantineUnknownCustomBeachBiome",
                java.util.Collection.class, Holder.class, Holder.class,
                int.class, int.class, int.class);
        collectionQuarantine.setAccessible(true);
        Holder<Biome> customBeach = testBiomeHolder(registry, "examplemod:crystal_shore");
        Holder<Biome> vanillaBeach = testBiomeHolder(registry, "minecraft:beach");
        Holder<Biome> neutralBase = testBiomeHolder(registry, "minecraft:plains");
        Set<String> beachFamily = Set.of(
                "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore");
        LatitudeBiomes.clearWorldgenContext();
        try {
            LatitudeBiomes.setRadius(10_000);
            LatitudeBiomes.setWorldSeed(41L);
            for (int band = 0; band <= 4; band++) {
                for (int[] at : new int[][]{{-4_211, 3_007}, {977, -6_133}, {5_501, 5_501}}) {
                    Holder<Biome> fromRegistry = castOceanPick(registryQuarantine.invoke(
                            null, registry, customBeach, neutralBase, at[0], at[1], band));
                    Holder<Biome> fromPool = castOceanPick(collectionQuarantine.invoke(
                            null, pool, customBeach, neutralBase, at[0], at[1], band));
                    for (Holder<Biome> out : List.of(fromRegistry, fromPool)) {
                        String id = LatitudeBiomes.biomeIdPublic(out);
                        assertTrue(beachFamily.contains(id),
                                "a quarantined custom beach pick must restore beach identity in "
                                        + "band " + band + " (was " + id + ")");
                    }
                    assertEquals(
                            LatitudeBiomes.biomeIdPublic(fromRegistry),
                            LatitudeBiomes.biomeIdPublic(fromPool),
                            "both pickers restore the same beach identity at the same column");
                    Holder<Biome> passThrough = castOceanPick(registryQuarantine.invoke(
                            null, registry, vanillaBeach, neutralBase, at[0], at[1], band));
                    assertTrue(passThrough == vanillaBeach,
                            "a vanilla beach pick must pass the beach quarantine untouched");
                }
            }
            // The cold arms must reuse the beach picker's own seed-free snowy/rocky roll: at a
            // chunk whose roll lands in each side, the restored identity is that side's biome.
            java.lang.reflect.Method beachId = LatitudeBiomes.class.getDeclaredMethod(
                    "vanillaBeachIdForBand", int.class, int.class, int.class);
            beachId.setAccessible(true);
            boolean sawSnowy = false;
            boolean sawRocky = false;
            for (int chunk = 0; chunk < 64 && !(sawSnowy && sawRocky); chunk++) {
                String id = (String) beachId.invoke(null, chunk << 4, 0, 3);
                sawSnowy |= id.equals("minecraft:snowy_beach");
                sawRocky |= id.equals("minecraft:stony_shore");
            }
            assertTrue(sawSnowy && sawRocky,
                    "the cold-band restore must roll both snowy and rocky shore identities");

            // Wiring: both beach-shortcut sites must route through the beach quarantine. The
            // land quarantine's four occurrences are its two definitions and the two LAND-path
            // call sites — a fifth means a beach path went back to the land reroll.
            String source = Files.readString(Path.of(
                    "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
            assertEquals(4, occurrences(source, "quarantineUnknownCustomBeachBiome("),
                    "both beach-shortcut paths must quarantine through the beach restore "
                            + "(two definitions plus two call sites)");
            assertEquals(4, occurrences(source, "quarantineUnknownCustomLandBiome("),
                    "the land quarantine may keep only its two definitions and two LAND-path "
                            + "call sites; a beach path must never reroll through the land pool");
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
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
        assertTrue(densityMixin.contains("method = \"doFill(")
                        && densityMixin.contains("method = \"iterateNoiseColumn(")
                        && occurrences(densityMixin,
                                "Aquifer;computeSubstance(IIID)Lnet/minecraft/world/level/block/state/BlockState;") == 2
                        && occurrences(densityMixin, "require = 1") == 2,
                "Mushroom island authority reaches both 26.3 density consumers and fails loud");
        for (String owner : List.of("doFill", "getBaseHeight", "getBaseColumn")) {
            assertTrue(authorityMixin.contains(owner), "density authority covers " + owner);
        }
        assertTrue(mixins.contains("NoiseChunkMushroomIslandDensityMixin"),
                "Mushroom island density hook is registered");
        String locateSource = Files.readString(
                Path.of("src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        assertTrue(locateSource.contains("findPlannedSurfaceWaterCoverage("),
                "bounded locate has a constant-cost fallback to the birth plan");
        assertTrue(locateSource.contains("plannedFallbackUsed"),
                "locate telemetry distinguishes the direct birth-plan fallback");
        VanillaSurfaceWaterCoveragePlan impossibleWater = VanillaSurfaceWaterCoveragePlan.build(
                10_000, 41L, 63, (id, route, x, z) -> false);
        VanillaSurfaceWaterCoveragePlan.SearchStats shoreStats =
                impossibleWater.missingDiagnostics().get("minecraft:stony_shore");
        assertTrue(shoreStats != null && shoreStats.centerEligible() == 0,
                "surface-plan diagnostics distinguish absent eligible terrain from topology/capacity");

        VanillaSurfaceWaterCoveragePlan.Route deepFrozen =
                VanillaSurfaceWaterCoveragePlan.Route.FROZEN_DEEP_OCEAN;
        VanillaSurfaceWaterCoveragePlan.Route frozen =
                VanillaSurfaceWaterCoveragePlan.Route.FROZEN_SHALLOW_OCEAN;
        VanillaSurfaceWaterCoveragePlan.CandidateEvaluator narrowDeepCorridor =
                (id, route, x, z) -> insideSyntheticSurfaceWaterRoute(route, x, z, 10_000)
                        && Math.floorMod(x, 640) < 96;
        VanillaSurfaceWaterCoveragePlan deepCorridor = VanillaSurfaceWaterCoveragePlan.build(
                10_000, 41L, 63, Map.of("minecraft:deep_frozen_ocean", deepFrozen),
                narrowDeepCorridor);
        assertTrue(deepCorridor.complete(),
                "a deep-ocean corridor with a multi-chunk span is a substantial province, even when "
                        + "its fixed cardinal shoulders are not all deep");

        VanillaSurfaceWaterCoveragePlan shallowCorridor = VanillaSurfaceWaterCoveragePlan.build(
                10_000, 41L, 63, Map.of("minecraft:frozen_ocean", frozen), narrowDeepCorridor);
        VanillaSurfaceWaterCoveragePlan.SearchStats shallowStats =
                shallowCorridor.missingDiagnostics().get("minecraft:frozen_ocean");
        assertTrue(shallowStats != null && shallowStats.centerEligible() > 0
                        && shallowStats.topologyEligible() == 0,
                "shallow-ocean routes retain the existing four-cardinal-shoulder topology rule");

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

        BiomeSelectionProfile cliffTreeProfile =
                BiomeSelectionProfile.capture(registryFor(Set.of("clifftree")));
        assertTrue(cliffTreeProfile.providers().contains("clifftree"),
                "an installed CliffTree registry must activate its provider ticket");
        for (String[] row : expected) {
            assertTrue(cliffTreeProfile.contains(BiomeRoute.valueOf(row[2]), row[0]),
                    "CliffTree land biome must enter its active route profile: " + row[0]);
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

        // landRoutesForBand must stay the exact inverse of landRouteEligible's switch. Presence
        // alone is insufficient: a route in the wrong band is just as dangerous as an omission.
        String routesForBand = method(source, "landRoutesForBand(int bandIndex) {");
        Map<String, Set<BiomeRoute>> expectedRoutesByArm = Map.of(
                "case BAND_TROPICAL ->", Set.of(BiomeRoute.TROPICAL_HUMID_LOWLAND),
                "case BAND_SUBTROPICAL ->", Set.of(
                        BiomeRoute.SUBTROPICAL_HUMID_LOWLAND,
                        BiomeRoute.WARM_TRANSITION,
                        BiomeRoute.WARM_UPLAND,
                        BiomeRoute.ARID_LOWLAND,
                        BiomeRoute.ARID_UPLAND),
                "case BAND_TEMPERATE ->", Set.of(
                        BiomeRoute.TEMPERATE_LOWLAND,
                        BiomeRoute.TEMPERATE_WETLAND,
                        BiomeRoute.TEMPERATE_UPLAND),
                "case BAND_SUBPOLAR ->", Set.of(
                        BiomeRoute.SUBPOLAR_LOWLAND,
                        BiomeRoute.SUBPOLAR_WETLAND,
                        BiomeRoute.SUBPOLAR_UPLAND,
                        BiomeRoute.COLD_UPLAND),
                // SUBPOLAR_UPLAND's absence from the polar arm is the whole windswept fix
                // (2026-08-18) and is asserted here, not merely implied: adding it back would put
                // the family in the polar band pool again, where the atlas measured it at 12.8% of
                // all polar land.
                "default ->", Set.of(BiomeRoute.POLAR_LOWLAND, BiomeRoute.COLD_UPLAND));
        for (Map.Entry<String, Set<BiomeRoute>> entry : expectedRoutesByArm.entrySet()) {
            assertEquals(entry.getValue(), routesInSwitchArm(routesForBand, entry.getKey()),
                    "landRoutesForBand must preserve the exact route set for " + entry.getKey());
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
     * The riparian water probe must find a river that is genuinely within reach, whatever its
     * width, distance, or orientation. The original stencil probed eight rays at exactly two
     * distances ({@code searchRadius/2} and {@code searchRadius}), which left two geometric holes:
     * a river band lying between those two distances on a ray was missed, and eight rays leave a
     * 7.85-block arc gap at radius ten (the diagonals also sampling 7.07/14.14 rather than 5/10).
     * Modelled here across every bank distance and orientation, that stencil found only 73.3% of
     * three-block-wide rivers and 92.8% of five-block ones — so the admission band was already
     * dotted before any feature placement, and the banks printed as blobs. Wide rivers hid it
     * completely, which is why it surfaced as a taste report about narrow desert streams.
     *
     * <p>Reported by the 1.21.11 line from a live look (2026-08-25) and re-derived here against
     * this line's own source before adoption; the mechanism transferred, the tuning deliberately
     * did not.
     */
    private static void riparianBankProbesCannotMissANarrowRiver() throws Exception {
        java.lang.reflect.Method offsets = com.example.globe.world.feature.RiparianPlacement.class
                .getDeclaredMethod("probeOffsetsForPolicyTest", int.class);
        offsets.setAccessible(true);
        // Every radius the codec accepts, not just the shipped default: search_radius is a
        // datapack-settable field, so a change to the ring spacing must not leave some other
        // radius with a hole nobody exercises.
        for (int radius = 1;
                radius <= com.example.globe.world.feature.RiparianPlacement.MAX_SEARCH_RADIUS;
                radius++) {
            riparianProbeTableCoversEveryRiver(
                    (int[][]) offsets.invoke(null, radius), radius);
        }

        int searchRadius = com.example.globe.world.feature.RiparianPlacement.DEFAULT_SEARCH_RADIUS;
        int[][] probes = (int[][]) offsets.invoke(null, searchRadius);
        assertTrue(probes.length > 0, "the riparian probe table must not be empty");

        // Nearest-first ordering is what lets the sweep return the TRUE distance on first hit and
        // stop there; an unordered table would report whichever ring happened to be walked first.
        double previous = -1.0;
        for (int[] probe : probes) {
            double distance = Math.hypot(probe[0], probe[1]);
            assertTrue(distance >= previous - 1.0e-9,
                    "probe offsets must be ordered nearest-first so the first hit is the true "
                            + "distance to the waterline");
            previous = distance;
            assertTrue(Math.abs(probe[0]) <= searchRadius && Math.abs(probe[1]) <= searchRadius,
                    "no probe may exceed the search radius on either axis — the decoration region "
                            + "only guarantees one chunk of reach");
        }

        // A straight river band of the given width, near edge at perpendicular distance d,
        // rotated through every orientation: some probe must land inside the water band.
        // The taper is what stops each patch ending on a hard rim: full inside the core, zero at
        // the radius, monotonically non-increasing in between.
        assertTrue(com.example.globe.world.feature.RiparianPlacement.bankDensity(1, searchRadius) == 1.0,
                "a column on the waterline plants at full density");
        assertTrue(com.example.globe.world.feature.RiparianPlacement.bankDensity(searchRadius, searchRadius) == 0.0,
                "density must reach zero at the search radius, not stop on a rim");
        double last = 1.0;
        for (int distance = 0; distance <= searchRadius; distance++) {
            double density = com.example.globe.world.feature.RiparianPlacement.bankDensity(distance, searchRadius);
            assertTrue(density <= last + 1.0e-9,
                    "bank density must never rise with distance from the water");
            last = density;
        }
        assertTrue(com.example.globe.world.feature.RiparianPlacement.bankDensity(-1, searchRadius) == 0.0,
                "a column with no water in reach plants nothing");
    }

    /**
     * A straight river band of the given width, its near edge at each plantable distance, rotated
     * through every orientation: some probe must land inside the water. Distances stop one short
     * of the search radius because a column at exactly the radius already tapers to zero density,
     * so detecting it would plant nothing — and requiring it would demand a probe lying exactly on
     * the river's normal, which no finite ring can guarantee.
     */
    private static void riparianProbeTableCoversEveryRiver(int[][] probes, int searchRadius) {
        assertTrue(probes.length > 0,
                "the riparian probe table must not be empty at radius " + searchRadius);
        for (int[] probe : probes) {
            assertTrue(Math.abs(probe[0]) <= searchRadius && Math.abs(probe[1]) <= searchRadius,
                    "no probe may exceed the search radius on either axis — the decoration region "
                            + "only guarantees one chunk of reach (radius " + searchRadius + ")");
        }
        for (int width : new int[]{3, 5, 8}) {
            for (int distance = 1; distance < searchRadius; distance++) {
                for (int step = 0; step < 72; step++) {
                    double theta = (2.0 * Math.PI * step) / 72.0;
                    double nx = Math.cos(theta);
                    double nz = Math.sin(theta);
                    boolean found = false;
                    for (int[] probe : probes) {
                        double perpendicular = probe[0] * nx + probe[1] * nz;
                        if (perpendicular >= distance && perpendicular <= distance + width - 1) {
                            found = true;
                            break;
                        }
                    }
                    assertTrue(found,
                            "a river of width " + width + " at distance " + distance
                                    + " must be detected at every orientation (radius "
                                    + searchRadius + ", missed at "
                                    + Math.round(Math.toDegrees(theta)) + " degrees)");
                }
            }
        }
    }

    /**
     * Every role {@link LatitudeBiomes#landBandTags} actually consults for selection must keep at
     * least one vanilla member. {@code pickFromWeightedTags} and {@code allowedLandPool} draw
     * straight from these tags with no vanilla-specific fallback, so a role with pack content but
     * zero vanilla members can be rolled on a modded world with no chance of a vanilla-only
     * outcome — the equal-provider-share machinery ({@link BiomeProviderSelectionPolicy}) assumes
     * every active role has a vanilla row to weight against. Found 2026-08-18/24: two of the
     * warm-belt accent tiers (the rarest, most "special" weighted slot) had this hole —
     * {@code lat_arid_accent} carried 23 modded ids and no vanilla one, {@code lat_tropics_accent}
     * 2 and none. Vanilla has no spare THIRD desert or jungle identity to give either accent its
     * own distinct flavor the way {@code lat_temperate_accent} (meadow, stony_peaks) or
     * {@code lat_polar_accent} (ice_spikes) can, so the floor reuses each band's existing
     * secondary-tier vanilla identity — wooded_badlands and eroded_badlands for arid, bamboo_jungle
     * for tropics — rather than inventing a new one.
     *
     * <p>The role roster is read from {@code landBandTags}' own source rather than hardcoded, so
     * this gate covers any future role automatically instead of needing a matching row added by
     * hand. The size pin exists only so a roster change is a deliberate, visible edit to this test.
     */
    private static void everyLandBandRoleKeepsAVanillaFloor() throws Exception {
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");
        String landBandTagsMethod = method(source,
                "private static List<TagKey<Biome>> landBandTags(int bandIndex) {");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("LAT_[A-Z0-9_]+")
                .matcher(landBandTagsMethod);
        java.util.Set<String> tagConstants = new java.util.TreeSet<>();
        while (matcher.find()) {
            tagConstants.add(matcher.group());
        }
        assertEquals(26, tagConstants.size(),
                "landBandTags' own role roster changed size — re-verify every role still keeps a "
                        + "vanilla floor, then update this count deliberately");
        for (String constant : tagConstants) {
            String fileName = constant.substring("LAT_".length()).toLowerCase(java.util.Locale.ROOT);
            String path = "src/main/resources/data/globe/tags/worldgen/biome/lat_" + fileName + ".json";
            String tag = read(path);
            assertTrue(tag.contains("\"minecraft:"),
                    "role " + fileName + " is selectable by pickFromWeightedTags/allowedLandPool "
                            + "and must keep at least one vanilla member, or a modded install can "
                            + "roll that weighted tier with zero chance of a vanilla-only outcome: "
                            + path);
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

        // Both wetland routes must actually be gated on the swamp evaluation.
        String eligible = source.replaceAll("\\s+", " ");
        assertTrue(eligible.contains("case TEMPERATE_WETLAND -> band == BAND_TEMPERATE && !mountain && wetlandProvinceEligible(blockX, blockZ) && evaluateSwamp("),
                "temperate wetland stays province- and terrain-gated");
        assertTrue(eligible.contains("case SUBPOLAR_WETLAND -> band == BAND_SUBPOLAR && !mountain && wetlandProvinceEligible(blockX, blockZ) && evaluateSwamp("),
                "subpolar wetland stays province- and terrain-gated");
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
                {"lat_river_subtropical", "minecraft:river"},
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
                "the cold-beach category roll must survive in BOTH pickBeachForBand overloads and "
                        + "in vanillaBeachIdForBand's quarantine restore — it decides snowy vs "
                        + "rocky, and only the identity within that category is tag-driven; losing "
                        + "it in any path silently rerolls every polar coastline on vanilla-only "
                        + "worlds, or restores a quarantined cold beach to the wrong category");
        String registryBeachMethod = method(source, "pickBeachForBand(Registry<Biome> biomes,");
        String collectionBeachMethod = method(source, "pickBeachForBand(Collection<Holder<Biome>> biomes,");
        for (String authority : new String[]{
                "LAT_BEACH_TROPICAL", "LAT_BEACH_TEMPERATE",
                "LAT_BEACH_COLD_SNOWY", "LAT_BEACH_COLD_ROCKY"}) {
            assertTrue(registryBeachMethod.contains(authority)
                            && collectionBeachMethod.contains(authority),
                    "both beach-pick paths must wire the tag authority: " + authority);
        }
        assertFalse(registryBeachMethod.contains("biome(biomes, \"minecraft:beach\")"),
                "the hardcoded vanilla beach literal must be gone from the pick path — it is now "
                        + "only a fallback argument");

        // The freeze verdict is a latitude ramp and must NOT have become tag- or band-driven.
        String registryPick = method(source,
                "public static Holder<Biome> pick(Registry<Biome> biomeRegistry,");
        String collectionPick = method(source,
                "public static Holder<Biome> pick(Collection<Holder<Biome>> biomePool,");
        for (String authority : new String[]{
                "LAT_RIVER_WARM", "LAT_RIVER_SUBTROPICAL",
                "LAT_RIVER_TEMPERATE", "LAT_RIVER_FROZEN"}) {
            assertTrue(registryPick.contains(authority) && collectionPick.contains(authority),
                    "both river-pick paths must wire the tag authority: " + authority);
        }
        for (String pick : new String[]{registryPick, collectionPick}) {
            assertTrue(pick.contains("blendedBandIndex == BAND_TROPICAL")
                            && pick.contains("? LAT_RIVER_WARM")
                            && pick.contains("blendedBandIndex == BAND_SUBTROPICAL")
                            && pick.contains("? LAT_RIVER_SUBTROPICAL")
                            && pick.contains(": LAT_RIVER_TEMPERATE"),
                    "each river picker must keep tropical, subtropical, and temperate identities "
                            + "as separate latitude authorities");
            assertFalse(pick.contains(
                            "blendedBandIndex <= BAND_SUBTROPICAL ? LAT_RIVER_WARM"),
                    "subtropical rivers must no longer inherit the tropical river pool");
        }
        assertTrue(registryPick.contains("shouldFreezeRiver(blockX, blockZ)")
                        && collectionPick.contains("shouldFreezeRiver(blockX, blockZ)"),
                "both river-pick paths must preserve shouldFreezeRiver as the frozen/liquid verdict");

        String tropicalRiver = read(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_river_warm.json");
        String subtropicalRiver = read(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_river_subtropical.json");
        assertTrue(tropicalRiver.contains("clifftree:tropical_river"),
                "the tropical band must retain CliffTree's explicitly tropical river");
        assertTrue(subtropicalRiver.contains("clifftree:warm_river"),
                "the subtropical band may retain CliffTree's warm river");
        assertFalse(subtropicalRiver.contains("clifftree:tropical_river"),
                "the subtropical band must not admit an explicitly tropical river");

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

    private static Set<BiomeRoute> routesInSwitchArm(String switchMethod, String armLabel) {
        int start = switchMethod.indexOf(armLabel);
        if (start < 0) throw new AssertionError("missing switch arm: " + armLabel);
        int searchFrom = start + armLabel.length();
        int nextCase = switchMethod.indexOf("case ", searchFrom);
        int nextDefault = switchMethod.indexOf("default ->", searchFrom);
        int end = switchMethod.length();
        if (nextCase >= 0) end = Math.min(end, nextCase);
        if (nextDefault >= 0) end = Math.min(end, nextDefault);
        String arm = switchMethod.substring(start, end);
        Set<BiomeRoute> routes = new HashSet<>();
        for (BiomeRoute route : BiomeRoute.values()) {
            if (arm.contains("BiomeRoute." + route.name())) routes.add(route);
        }
        return routes;
    }

    /**
     * The 2026-08-10 biome-picker audit measured this cap at its PRE-fix threshold (0.45) and
     * found it retained ~59% of ice_spikes picks rather than capping them — a vanilla-only world
     * (the hard "must work with no providers" case) still finished with ice_spikes on ~27% of the
     * polar band, not the minority accent {@code lat_polar_accent.json} declares. This proves the
     * RETUNED threshold (0.88) against the real pipeline: BiomeProviderSelectionPolicy's argmax
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
     * The windswept family belongs to subpolar mountains and nowhere else.
     *
     * <p>Measured on the shipped build with vanilla biomes only:
     * {@code minecraft:windswept_hills} was 12.78% of all polar land (66.5-90 degrees) and 15.58%
     * of land above 74.5 — the second most common land biome at the pole, arriving snowless, with
     * green grass, flowers and passive animals at 80 north. Its route said "subpolar or polar AND
     * mountain", but the mountain half was enforced nowhere downstream, so the terrain-compatibility
     * reroll walked into it on ordinary polar shelves.
     *
     * <p>Three independent halves are asserted, because each one alone has already been shown to be
     * bypassable: the ledger route (which band pool may contain it), the extreme-polar cap's
     * explicit id list (which named only windswept_forest, and read as complete because the path
     * catch-all matches "forest"), and the final clamp (which nothing upstream can outvote).
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

        // 3b. The OTHER legacy route, which is the bigger population. COLD_UPLAND was only ever
        // written between 2026-08-10 and 2026-08-18; every jar built before 3c4c97dcf (2026-08-12)
        // wrote TEMPERATE_UPLAND, so those worlds — TEST jars included — are the ones most likely
        // to exist. They are ALREADY roster-less on this pivot: the re-route made
        // savedRouteRemainsValid reject their rows the moment the ledger stopped listing
        // TEMPERATE_UPLAND for these ids. Both saved profiles are exercised whole, built by
        // rewriting a real capture, because a hand-written two-line profile would not prove the
        // rest of the roster survives alongside the migrated rows.
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
        // final clamp covers both picker paths.
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");
        String leakMethod = method(source, "isExtremePolarSoftColdLeak(Holder<Biome> candidate) {");
        for (String id : windswept) {
            assertTrue(leakMethod.contains("\"" + id + "\""),
                    "the extreme-polar cap must name every windswept identity explicitly — "
                            + "windswept_hills and windswept_gravelly_hills match none of the "
                            + "\"forest\"/\"taiga\"/\"grove\" catch-alls and passed straight "
                            + "through: " + id);
        }
        // "both overloads run the clamp" used to be asserted by counting an exact source line.
        // That count passed only because the two overloads happen to name their parameter
        // `biomes`, and it said nothing about the clamp being the LAST write to `out` — a clamp
        // called twice and then overwritten would still have satisfied it. Section 6 drives both
        // pick() overloads at the pole instead, which is the property that line was standing in
        // for and cannot be satisfied by naming.
        // Restated 2026-08-18 when rawMountainTruth was added as a third mountain-evidence term.
        // This used to pin the exact substring "(mountainLike || mountainNoiseLike)", which named
        // the two signals rather than the property, and both of those are structurally FALSE in
        // the subpolar band — mountainNoiseLike is gated on BAND_TEMPERATE and mountainLike is
        // force-set only under >= BAND_POLAR — so the predicate it was guarding could never return
        // true anywhere, and the assertion happily certified a gate that was locked shut. What the
        // law actually says is "the subpolar band AND real mountain evidence", so that is what is
        // asserted: the band term, ANDed, and a non-empty disjunction of evidence terms that names
        // all three. The startsWith is the teeth against the failure this section exists for —
        // a band-only predicate cannot satisfy it. The behavioural half of the same claim, which
        // no source-text check can substitute for, is section 6b: flat subpolar columns must never
        // return a windswept id, swept across the band.
        String legality = method(source,
                "isWindsweptFamilyLegal(int bandIndex,");
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

        // 6. THE ACTUAL BUG, through the actual picker. Everything above is a statement about
        // tables and source text, and every one of those tables was internally consistent while
        // the defect was live at 12.78% of polar land — a route ledger that agrees with a coverage
        // map proves the two agree, not that the generator obeys either. Only LatitudeBiomes.pick
        // can answer "would this world put green windswept grass at 80 degrees north".
        //
        // Terrain reachable from this harness: the policy suite calls pick() with a null generator
        // and callerContext ATLAS_SAMPLER, which takes the skip-preview path, so preview terrain is
        // synthetic (centerHeight sea-1, robustDelta 0) and mountainNoiseLike/mountainLike are
        // hard-false outside the temperate band. Polar columns here therefore classify as
        // TERRAIN_CLASS_FLAT_SHELF or TERRAIN_CLASS_FLAT_LOWLAND; TERRAIN_CLASS_RAISED_SHOULDER —
        // the class the census measured at 15.58% — needs a real chunk generator and cannot be
        // produced through the public picker. That does not soften the assertion, because the
        // polar ban has no terrain exemption: "never windswept in the polar band" has to hold for
        // every class, and a shelf a few blocks above the sea is precisely what these two classes
        // describe.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        int pickerRadius = 10_000;
        long pickerSeed = 131L;
        BiomeSelectionProfile pickerProfile = BiomeSelectionProfile.capture(
                registry.keySet().stream().map(Identifier::toString).toList());
        Climate.Sampler pickerSampler = coverageSampler(null);
        try {
            LatitudeBiomes.activateWorldgenContext(
                    pickerRadius,
                    pickerSeed,
                    LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                    pickerProfile,
                    VanillaBiomeRepresentationProfile.capture(pickerRadius, pickerSeed, pickerProfile),
                    pickerSampler,
                    null,
                    63);
            VanillaBiomeCoveragePlan pickerPlan =
                    LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
            assertTrue(pickerPlan != null && pickerPlan.complete(),
                    "the picker proof needs a complete birth-locked land plan");

            // 6a. The reported bug, end to end. Both overloads, both windswept-donor and
            // neutral-donor entries, swept across x so that both halves of the mountain-noise
            // stripe are covered: the census found windswept on ordinary shelves, not only where
            // the noise claimed a mountain.
            int polarSamples = 0;
            int extremePolarSamples = 0;
            for (double fraction : new double[]{0.78, 0.82, 0.86, 0.90, 0.94}) {
                int z = (int) Math.round(pickerRadius * fraction);
                for (int x = -2_048; x <= 2_048; x += 256) {
                    if ((long) x * x + (long) z * z >= (long) pickerRadius * pickerRadius) continue;
                    // 4 == BAND_POLAR. The band constants are private to LatitudeBiomes, so the
                    // suite reads the picker's own final band decision rather than re-deriving it.
                    if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, pickerRadius) != 4) {
                        continue;
                    }
                    polarSamples++;
                    // EXTREME_POLAR_CAP_MIN_DEG is 74.5, and latitude degrees are the z fraction
                    // times 90, so this counts the samples that also exercise the extreme cap.
                    if (fraction * 90.0 >= 74.5) extremePolarSamples++;
                    for (String id : windswept) {
                        assertNeitherPickerReturns(
                                registry, pool, testBiomeHolder(registry, id),
                                x, z, pickerRadius, pickerSampler, id,
                                "the polar band must never return " + id + " — even when the donor "
                                        + "source hands the picker that very identity — at x=" + x
                                        + " z=" + z + " (lat=" + (fraction * 90.0) + " degrees)");
                    }
                }
            }
            assertTrue(polarSamples >= 40,
                    "the polar sweep must actually reach the polar band, or it proves nothing: "
                            + "samples=" + polarSamples);
            assertTrue(extremePolarSamples >= 20,
                    "part of the sweep must sit above the 74.5-degree extreme cap, which is where "
                            + "the census measured windswept_hills at 15.58%: samples="
                            + extremePolarSamples);

            // 6b. FLAT subpolar columns. The subpolar band is the family's legal home, so the
            // route says nothing here and the mountain half of the law is enforced by the
            // ownership clamp alone — which keyed off a predicate naming only windswept_forest and
            // windswept_gravelly_hills, never windswept_hills, the id that caused the report.
            //
            // Swept rather than probed at one coordinate: the subpolar substitution pool is NOT
            // mountain-filtered (filteredAllowedLandPool does nothing for this band), so whether a
            // given flat column lands on windswept is a property of the reroll noise at that x/z.
            // A single hand-picked coordinate proves only that one column, and would silently stop
            // meaning anything if the noise moved. Asserting it across every unreserved flat
            // column in the band is the claim the law actually makes.
            int flatSubpolarColumns = 0;
            for (double fraction : new double[]{0.60, 0.63, 0.66, 0.69, 0.72}) {
                int z = (int) Math.round(pickerRadius * fraction);
                for (int x = -4_096; x <= 4_096; x += 97) {
                    if (mountainNoiseColumn(x)) continue;
                    if ((long) x * x + (long) z * z >= (long) pickerRadius * pickerRadius) continue;
                    // Guaranteed windswept provinces are a deliberate exception and are asserted
                    // as the positive control in 6c.
                    if (pickerPlan.match(x, z) != null) continue;
                    // 3 == BAND_SUBPOLAR.
                    if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, pickerRadius) != 3) {
                        continue;
                    }
                    flatSubpolarColumns++;
                    for (String id : windswept) {
                        assertNeitherPickerReturns(
                                registry, pool, testBiomeHolder(registry, id),
                                x, z, pickerRadius, pickerSampler, id,
                                "a FLAT subpolar column must not return " + id + " — the windswept "
                                        + "family is a MOUNTAIN identity in this band, not merely a "
                                        + "subpolar one, at x=" + x + " z=" + z);
                    }
                }
            }
            assertTrue(flatSubpolarColumns >= 100,
                    "the flat-subpolar sweep must actually cover the band: columns="
                            + flatSubpolarColumns);

            // 6c. Positive control. A ban that quietly became total would read as "fixed" on every
            // negative assertion above while deleting the wind-scoured identity from the 50-66.5
            // degree ridges it was moved to. The guaranteed subpolar-upland provinces are where
            // the family MUST still appear.
            List<VanillaBiomeCoveragePlan.Anchor> subpolarUplandAnchors = pickerPlan.anchors().stream()
                    .filter(anchor -> anchor.route() == BiomeRoute.SUBPOLAR_UPLAND)
                    .toList();
            assertEquals(windswept.length, subpolarUplandAnchors.size(),
                    "a Regular world reserves one guaranteed province per windswept identity");
            for (VanillaBiomeCoveragePlan.Anchor anchor : subpolarUplandAnchors) {
                assertPickerPairReturns(
                        registry, pool, testBiomeHolder(registry, "minecraft:snowy_taiga"),
                        anchor.blockX(), anchor.blockZ(), pickerRadius, pickerSampler,
                        anchor.biomeId(),
                        "the windswept family must remain PRODUCIBLE in the subpolar band, or the "
                                + "polar fix has silently degraded into banned-everywhere: "
                                + anchor.biomeId());
                double anchorLatDeg =
                        Math.abs(anchor.blockZ()) / (double) pickerRadius * 90.0;
                assertTrue(anchorLatDeg <= 66.5,
                        "a guaranteed windswept province must stay below the polar boundary: "
                                + anchor.biomeId() + " at " + anchorLatDeg + " degrees");
            }

            // 6d. The family must be producible on ORDINARY generated mountains, not only inside
            // its three reserved provinces. 6c above is satisfied entirely by coverage anchors,
            // which reach the map through a path that never consults the terrain gate — so 6c
            // would stay green even if every unreserved column in the band refused the family.
            // This sweep excludes the anchors precisely so it cannot be satisfied that way, and it
            // hands each windswept id in as its own donor: the question is whether a genuine
            // mountain column is allowed to KEEP the identity handed to it. It is the mirror image
            // of 6b, which asks the same of flat columns and demands the opposite answer, and the
            // pair is what pins the "subpolar AND mountain" law behaviourally.
            //
            // HONEST SCOPE — read before trusting this as proof of the terrain gate. It is NOT.
            // Measured 2026-08-18: shutting the windswept gate completely (passing false for
            // rawMountainTruth at both pick() call sites) leaves the counts below bit-identical at
            // 33/27 of 154 columns. What preserves the family here is the LATE ownership clamp,
            // clampTemperateWindsweptMountainOwnership, which reads its own isMountainLike and
            // keeps windswept wherever the descriptor owns the mountain — and what enforces 6b is
            // the same clamp answering false on flat ground. The gate cannot show through this
            // harness at all: pick() is called with a null chunk generator, so preview terrain is
            // synthetic (centerHeight sea-1, robustDelta 0), every subpolar column classifies
            // FLAT_SHELF or FLAT_LOWLAND, the incoming pick is already terrain-compatible there,
            // and the gate returns it untouched without ever entering the reroll walk that the
            // legality predicate steers. Section 6e asserts the predicate directly for that reason.
            // Do not "strengthen" this sweep into a gate assertion; it passes either way.
            int mountainSubpolarColumns = 0;
            int[] registryWindsweptHits = new int[windswept.length];
            int[] collectionWindsweptHits = new int[windswept.length];
            for (double fraction : new double[]{0.60, 0.63, 0.66, 0.69, 0.72}) {
                int z = (int) Math.round(pickerRadius * fraction);
                for (int x = -4_096; x <= 4_096; x += 97) {
                    // Mountain per LatitudeBiomes' OWN read, snapped to the quart boundary the
                    // climate sampler actually reads — see mountainNoiseColumn's note.
                    if (!mountainNoiseColumn(x)) continue;
                    if ((long) x * x + (long) z * z >= (long) pickerRadius * pickerRadius) continue;
                    // Guaranteed provinces are 6c's subject; counting them here would let the
                    // anchor path satisfy an assertion about the gate.
                    if (pickerPlan.match(x, z) != null) continue;
                    // 3 == BAND_SUBPOLAR.
                    if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, pickerRadius) != 3) {
                        continue;
                    }
                    mountainSubpolarColumns++;
                    for (int i = 0; i < windswept.length; i++) {
                        Holder<Biome> donor = testBiomeHolder(registry, windswept[i]);
                        if (windswept[i].equals(LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                                registry, donor, x, z, 80, pickerRadius, pickerSampler,
                                "ATLAS_SAMPLER")))) {
                            registryWindsweptHits[i]++;
                        }
                        if (windswept[i].equals(LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                                pool, donor, x, z, 80, pickerRadius, pickerSampler,
                                "ATLAS_SAMPLER")))) {
                            collectionWindsweptHits[i]++;
                        }
                    }
                }
            }
            String windsweptMeasured = "mountain subpolar columns=" + mountainSubpolarColumns
                    + "; registry/collection hits"
                    + " hills=" + registryWindsweptHits[0] + "/" + collectionWindsweptHits[0]
                    + " forest=" + registryWindsweptHits[1] + "/" + collectionWindsweptHits[1]
                    + " gravelly_hills=" + registryWindsweptHits[2] + "/" + collectionWindsweptHits[2];
            assertTrue(mountainSubpolarColumns >= 40,
                    "the mountain-subpolar sweep must actually reach genuine mountain columns in "
                            + "the band, or it proves nothing: " + windsweptMeasured);
            // Measured 2026-08-18: 154 mountain subpolar columns, of which 33 (registry) and 27
            // (collection) keep each windswept identity — roughly a fifth of unreserved cold
            // mountain ground. The floor sits well under that so ordinary noise movement does not
            // fail the build, while a regression that re-bans the family outside its provinces
            // (which produces 0, not a smaller number) still fails hard. Registry and collection
            // legitimately differ: the two sources carry different pools and so disagree about
            // which sibling replaces a rejected pick — the suite already documents that divergence
            // on the polar sweep, which is why they are counted separately, not asserted equal.
            for (int i = 0; i < windswept.length; i++) {
                assertTrue(registryWindsweptHits[i] >= 10,
                        "a genuine subpolar MOUNTAIN column outside the reserved provinces must be "
                                + "allowed to keep " + windswept[i] + " — the windswept family is "
                                + "the vegetated identity of cold mountains, and a ban that spread "
                                + "from the pole into its own band would read as green on every "
                                + "negative assertion above (registry): " + windsweptMeasured);
                assertTrue(collectionWindsweptHits[i] >= 10,
                        "the collection overload must admit " + windswept[i] + " on genuine "
                                + "subpolar mountain columns exactly as the registry overload does; "
                                + "the two gate call sites are threaded identically and must not "
                                + "drift (collection): " + windsweptMeasured);
            }

            // 6e. THE LEVER ITSELF, asserted directly on the predicate.
            //
            // The defect this closes: isWindsweptFamilyLegal could not return true on ANY subpolar
            // column, mountain or not. It read only mountainNoiseLike — computed at the call site
            // as landBandIndex == BAND_TEMPERATE && ... — and mountainLike, which comes from
            // temperateMountainTerrainAuthority and is force-set true only under
            // landBandIndex >= BAND_POLAR. The subpolar band sits between the two and received
            // neither, so the family's one legal home was locked shut and the 5.2% of subpolar
            // cold upland it still held (190 columns against the alpine trio's 3,437, three seeds,
            // radius 10,000 step 32) was arriving past the gate rather than through it. The fix
            // adds rawMountainTruth, the raw isMountainLike read — deliberately the SAME signal
            // the late ownership clamp already uses, so gate and veto agree by construction and
            // the gate cannot admit a column the veto will then silently overwrite.
            //
            // Asserted through a dedicated hook rather than through pick() because, as 6d records,
            // this harness cannot reach the predicate's effect: the reroll walk it steers never
            // runs on synthetic flat terrain. These four cases are the whole truth table that
            // matters, and every one of them fails if the rawMountainTruth term is removed or the
            // band test is loosened.
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
        } finally {
            LatitudeBiomes.clearWorldgenContext();
        }
    }

    /**
     * Desert is the staple of the subtropical arid belt, and badlands is the regional accent that
     * lives inside its own province (maintainer ruling, 2026-08-18).
     *
     * <p>The reported defect: a vanilla-only world generated almost no desert. A three-seed atlas at
     * radius 10,000 step 32 measured minecraft:desert at 182/765/785 samples against
     * minecraft:badlands at 6012/4822/4474 — badlands outnumbering desert 33:1, 6.3:1 and 5.7:1, and
     * desert appearing in no other band either. The two arid identities share one route pool,
     * {@code lat_arid_primary}, which lists exactly badlands and desert 1:1, so every table in the
     * mod said the split was even while the generator produced almost none of one of them. Three
     * things did that, none of them visible from a route table: every subtropical WARM_DRY column
     * short-circuits into pickAridRegionFallback before the fair pool is ever consulted; that
     * fallback smeared badlands over a third of the ground OUTSIDE the badlands province and then
     * defaulted the remainder to badlands as well; and enforceWarmProvinceFamily's WARM_DRY chain
     * named badlands first, so anything that reached it became mesa. The desert branch at the bottom
     * of pickAridRegionFallback was literally unreachable in a vanilla registry.
     *
     * <p>Which is why this is a PRODUCTION sweep and not an admission check. Every admission,
     * descriptor and route assertion in this file passed for the whole life of the defect. Only
     * LatitudeBiomes.pick can answer "does this world actually contain desert".
     *
     * <p>Latitudes sampled are 27, 30.6 and 34.2 degrees: past DESERT_LAT_RAMP_HIGH_DEG, so this
     * measures the settled arid belt rather than the 23.5-27 degree phase-in. The equator guard
     * below covers the other side of that ramp.
     */
    private static void desertIsTheStapleOfTheSubtropicalAridBelt() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        int radius = 10_000;
        Climate.Sampler sampler = coverageSampler(null);
        List<String> ids = registry.keySet().stream().map(Identifier::toString).toList();

        for (long seed : new long[]{3L, 131L, 461L}) {
            BiomeSelectionProfile profile = BiomeSelectionProfile.capture(ids);
            try {
                LatitudeBiomes.activateWorldgenContext(
                        radius,
                        seed,
                        LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                        profile,
                        VanillaBiomeRepresentationProfile.capture(radius, seed, profile),
                        sampler,
                        null,
                        63);
                VanillaBiomeCoveragePlan plan = LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
                assertTrue(plan != null && plan.complete(),
                        "the arid-belt proof needs a complete birth-locked land plan (seed=" + seed + ")");

                // A neutral donor asks what the picker chooses on its own. A badlands donor asks
                // whether an incoming vanilla mesa can talk it out of that — which is exactly how
                // desert used to survive at all: only where vanilla had ALREADY put desert there.
                AridBeltCensus neutral = censusSubtropicalDryProvince(
                        registry, pool, testBiomeHolder(registry, "minecraft:plains"),
                        radius, sampler, plan);
                AridBeltCensus donated = censusSubtropicalDryProvince(
                        registry, pool, testBiomeHolder(registry, "minecraft:badlands"),
                        radius, sampler, plan);

                assertTrue(neutral.dryColumns >= 50,
                        "the arid sweep must actually land in the subtropical dry province, or it "
                                + "proves nothing: seed=" + seed + " columns=" + neutral.dryColumns);

                // 1. THE REGRESSION TEST. Desert must be PRODUCED, in quantity, in the belt that is
                // named after it. Measured on these three seeds after the fix: 38%, 45% and 23% of
                // dry-province columns. Before it: zero on every one of them.
                double desertShare = neutral.dryDesert / (double) neutral.dryColumns;
                assertGreaterThan(0.15, desertShare,
                        "desert must be a staple of the subtropical dry province, not a curiosity: "
                                + "seed=" + seed + " desert=" + neutral.dryDesert
                                + " badlands=" + neutral.dryBadlands
                                + " of " + neutral.dryColumns + " dry columns");

                // 2. The province is the authority. Outside a badlands province the belt is desert,
                // give or take the deliberate outlier-mesa allowance
                // (BADLANDS_OUTSIDE_PROVINCE_THRESHOLD, narrowed 0.34 -> 0.06 by the same ruling).
                // Measured: 84%, 100%, 100% desert outside the province.
                assertTrue(neutral.outsideProvince >= 20,
                        "the sweep must reach dry ground outside the badlands province: seed=" + seed
                                + " outside=" + neutral.outsideProvince);
                double outsideDesertShare =
                        neutral.outsideProvinceDesert / (double) neutral.outsideProvince;
                assertGreaterThan(0.70, outsideDesertShare,
                        "outside its own province the arid belt must read as desert, with badlands "
                                + "left as a rare outlier mesa: seed=" + seed
                                + " desert=" + neutral.outsideProvinceDesert
                                + " badlands=" + neutral.outsideProvinceBadlands
                                + " of " + neutral.outsideProvince + " columns outside the province");

                // 3. POSITIVE CONTROL. Badlands is demoted from default to regional, never deleted.
                // A fix that inverted into "desert everywhere" would satisfy 1 and 2 and quietly
                // remove a biome the maintainer loves. Measured: 100% badlands inside the province
                // on all three seeds.
                assertTrue(neutral.insideProvince >= 20,
                        "the sweep must reach inside a badlands province, or the positive control "
                                + "proves nothing: seed=" + seed + " inside=" + neutral.insideProvince);
                double insideBadlandsShare =
                        neutral.insideProvinceBadlands / (double) neutral.insideProvince;
                assertGreaterThan(0.90, insideBadlandsShare,
                        "badlands must still own its province — it is regional now, not gone: seed="
                                + seed + " badlands=" + neutral.insideProvinceBadlands
                                + " of " + neutral.insideProvince + " columns inside the province");

                // 4. The province decides, not the donor. Identical census from a plains donor and a
                // badlands donor is the whole point of resolving desert directly in
                // pickAridRegionFallback instead of handing `base` to enforceWarmProvinceFamily,
                // which returns any badlands-family pick untouched.
                assertEquals(neutral.dryDesert, donated.dryDesert,
                        "an incoming vanilla badlands must not change how much desert the dry "
                                + "province generates (seed=" + seed + ")");
                assertEquals(neutral.dryBadlands, donated.dryBadlands,
                        "an incoming vanilla badlands must not change how much badlands the dry "
                                + "province generates (seed=" + seed + ")");

                // 5. The two picker overloads must agree on the FAMILY. They are allowed to disagree
                // about which badlands variant a column gets — chooseBadlandsVariant is written
                // differently in the two overloads, a pre-existing divergence this ruling did not
                // touch — but desert-versus-badlands is a different world, not a different texture.
                // Before the WARM_DRY chains were reconciled this sweep measured columns where the
                // registry path returned badlands and the collection path returned desert.
                assertEquals(0, neutral.familyDisagreements,
                        "the registry and collection pickers must never disagree about desert vs "
                                + "badlands at the same column — that is live generation diverging "
                                + "from the map the atlas drew (seed=" + seed + ")");
                assertEquals(0, donated.familyDisagreements,
                        "the registry and collection pickers must agree about desert vs badlands "
                                + "from a badlands donor too (seed=" + seed + ")");

                // 6. EQUATOR GUARD. The 2026-06-06 tropical dry-biome overhaul drove tropical
                // badlands and tropical desert to zero and they must stay there: Earth has neither
                // at the equator, and the demote pair that enforces it was edited by this same
                // ruling (desert now leaves the badlands gate to the desert gate rather than being
                // gated by both). Donors include desert and badlands themselves, so the tropics are
                // asked to reject arid even when handed it.
                int tropicalColumns = 0;
                for (String donorId : new String[]{
                        "minecraft:plains", "minecraft:desert", "minecraft:badlands"}) {
                    Holder<Biome> donor = testBiomeHolder(registry, donorId);
                    for (double fraction : new double[]{0.04, 0.10, 0.16, 0.22}) {
                        for (int sign : new int[]{1, -1}) {
                            int z = sign * (int) Math.round(radius * fraction);
                            for (int x = -8_192; x <= 8_192; x += 512) {
                                if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                                // 0 == BAND_TROPICAL.
                                if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius) != 0) {
                                    continue;
                                }
                                tropicalColumns++;
                                for (String aridId : ARID_IDS) {
                                    assertNeitherPickerReturns(
                                            registry, pool, donor, x, z, radius, sampler, aridId,
                                            "the tropical band must never return " + aridId
                                                    + " — even from a " + donorId + " donor — at x="
                                                    + x + " z=" + z + " (lat=" + (fraction * 90.0)
                                                    + " degrees, seed=" + seed + ")");
                                }
                            }
                        }
                    }
                }
                assertTrue(tropicalColumns >= 300,
                        "the equator guard must actually reach the tropical band: seed=" + seed
                                + " columns=" + tropicalColumns);
            } finally {
                LatitudeBiomes.clearWorldgenContext();
            }
        }
    }

    private static final String[] ARID_IDS = {
            "minecraft:desert",
            "minecraft:badlands",
            "minecraft:wooded_badlands",
            "minecraft:eroded_badlands"};

    private static final String[] BADLANDS_IDS = {
            "minecraft:badlands",
            "minecraft:wooded_badlands",
            "minecraft:eroded_badlands"};

    private static boolean isBadlandsId(String id) {
        for (String badlands : BADLANDS_IDS) {
            if (badlands.equals(id)) return true;
        }
        return false;
    }

    /** desert / badlands / neither, for comparing the two picker overloads at one column. */
    private static String aridFamilyOf(String id) {
        if ("minecraft:desert".equals(id)) return "desert";
        return isBadlandsId(id) ? "badlands" : "other";
    }

    /** What one seed's subtropical WARM_DRY province actually generates, through the real picker. */
    private static final class AridBeltCensus {
        int dryColumns;
        int dryDesert;
        int dryBadlands;
        int insideProvince;
        int insideProvinceBadlands;
        int outsideProvince;
        int outsideProvinceDesert;
        int outsideProvinceBadlands;
        int familyDisagreements;
    }

    private static AridBeltCensus censusSubtropicalDryProvince(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int radius,
            Climate.Sampler sampler,
            VanillaBiomeCoveragePlan plan) {
        AridBeltCensus census = new AridBeltCensus();
        // Sweep density is sized for the 2026-08-19 calibration that made badlands earthlike-rare
        // (~15% of the dry province): the positive control needs enough columns INSIDE a badlands
        // country, and the old 3-row/128-step sweep crossed too few country cells to guarantee that.
        // Rows stay at |z| >= 0.30*radius (27 deg): below that sits the deliberate badlands->savanna
        // latitude ramp, which would dilute the inside-the-country badlands share by design.
        for (double fraction : new double[]{0.30, 0.32, 0.34, 0.36, 0.38}) {
            for (int sign : new int[]{1, -1}) {
                int z = sign * (int) Math.round(radius * fraction);
                for (int x = -8_192; x <= 8_192; x += 64) {
                    if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                    // 1 == BAND_SUBTROPICAL. The band constants are private to LatitudeBiomes, so
                    // the suite reads the picker's own final band decision rather than re-deriving
                    // it and hoping the two agree.
                    if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius) != 1) {
                        continue;
                    }
                    // The arid belt proper. WARM_MEDIUM (savanna) and WARM_WET (jungle) columns sit
                    // in this band too and are a different question; badlands squatting in a savanna
                    // province is an explicitly deferred slice, not this one.
                    if (LatitudeBiomes.classifyProvince(x, z) != ProvinceAuthority.Province.WARM_DRY) {
                        continue;
                    }
                    // Guaranteed coverage provinces are placed by the birth plan, not chosen by the
                    // picker; counting them would measure the plan.
                    if (plan != null && plan.match(x, z) != null) continue;

                    String registryId = LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                            registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"));
                    String collectionId = LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                            pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"));
                    if (!aridFamilyOf(registryId).equals(aridFamilyOf(collectionId))) {
                        census.familyDisagreements++;
                    }

                    boolean desert = "minecraft:desert".equals(registryId);
                    boolean badlands = isBadlandsId(registryId);
                    census.dryColumns++;
                    if (desert) census.dryDesert++;
                    if (badlands) census.dryBadlands++;
                    if (LatitudeBiomes.badlandsProvinceHitForPolicyTest(x, z, radius)) {
                        census.insideProvince++;
                        if (badlands) census.insideProvinceBadlands++;
                    } else {
                        census.outsideProvince++;
                        if (desert) census.outsideProvinceDesert++;
                        if (badlands) census.outsideProvinceBadlands++;
                    }
                }
            }
        }
        return census;
    }

    /**
     * The dry-warm identity gate is the last rewrite in the picker that can still name a warm biome,
     * and until 2026-08-18 it named {@code minecraft:savanna} outright. That left it as the one
     * place answering "what is a dry warm column" with grassland while enforceWarmProvinceFamily and
     * pickAridRegionFallback -- reconciled to desert, then badlands, then savanna by the same ruling
     * on the same day -- answered with sand. This pins the gate to that single order, and pins the
     * latitude law it now has to re-apply for itself.
     *
     * <p>HONEST SCOPE — read this before treating any part of the method as proof of the gate's
     * world effect. Section 1 is the teeth: it fails on the old code and passes on the new. Sections
     * 2 and 3 were green before the change too, and they are guards, not proof. The reason is
     * structural and is worth writing down rather than rediscovering: applyFinalSavannaClimateClamp
     * runs BEFORE this gate in both pickers, and on a WARM_DRY column it always ends in the province
     * rewrite — so by the time the gate is reached the column is already desert or badlands and the
     * gate's own {@code isDryWarmIdentity} early return fires. The only stage between the clamp and
     * the gate that can put a non-arid identity back is the vanilla coverage plan, and the only
     * routes it may place on a WARM_DRY subtropical column are WARM_TRANSITION, WARM_UPLAND and the
     * ARID pair — whose VANILLA members are all savanna-family or arid-family, so they early-return
     * too. On a vanilla-only roster this gate therefore cannot fire, the census in section 2 reads
     * identically before and after, and no behavioural sweep in this suite can have teeth on it.
     * It fires for packs: a WARM_TRANSITION-route modded biome that is neither savanna-family nor an
     * arid id (terralith:brushland, biomesoplenty:mediterranean_forest) is exactly what reaches it,
     * and that is the world this fix changes.
     */
    private static void dryWarmIdentityGateUsesTheDesertFirstOrder() throws Exception {
        // 1. THE REGRESSION TEST, and the only part of this method that fails on the old code. Both
        //    overloads must resolve through the shared province helper rather than naming a savanna
        //    id themselves, and each must re-apply the three latitude demotes AFTER it, because
        //    this gate is the one arid producer that runs downstream of the clamp that owns them.
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");
        String registryGate = method(source,
                "private static Holder<Biome> gateDryWarmIdentity(Registry<Biome> biomes,");
        String collectionGate = method(source,
                "private static Holder<Biome> gateDryWarmIdentity(Collection<Holder<Biome>> biomes,");
        for (String gate : List.of(registryGate, collectionGate)) {
            String body = normalize(gate);
            assertFalse(body.contains("minecraft:savanna"),
                    "the dry-warm gate must not name a savanna id itself — savanna is the LAST "
                            + "resort of the dry province, reachable only through the shared "
                            + "province helper, and hardcoding it here is exactly how this gate "
                            + "came to disagree with the desert-first ruling: " + body);
            int enforce = body.indexOf("enforceWarmProvinceFamily(");
            int badlands = body.indexOf("demoteEquatorialBadlands(");
            int desert = body.indexOf("demoteEquatorialDesert(");
            int poleward = body.indexOf("demotePolewardArid(");
            assertTrue(enforce >= 0,
                    "the dry-warm gate must resolve through the same helper every other dry "
                            + "province column resolves through: " + body);
            // The coordinate pair joined this call on 2026-08-18: enforceWarmProvinceFamily has to
            // know WHERE the column is now that its WARM_MEDIUM arm consults the savanna country.
            assertTrue(body.contains("out, ProvinceAuthority.Province.WARM_DRY, blockX, blockZ)"),
                    "the dry-warm gate must ask that helper for the WARM_DRY family explicitly, and "
                            + "at this column: " + body);
            assertTrue(badlands > enforce && desert > badlands && poleward > desert,
                    "this gate runs after applyFinalSavannaClimateClamp, so it must re-apply that "
                            + "clamp's three latitude demotes, in the clamp's order, to whatever it "
                            + "answers — without them a gate-made desert stands at the equator with "
                            + "nothing downstream left to take it back: " + body);
        }
        // The two overloads have to be the same world, not merely both correct. Everything from the
        // helper call onward is compared literally.
        assertEquals(
                normalize(registryGate.substring(registryGate.indexOf("Holder<Biome> rerouted ="))),
                normalize(collectionGate.substring(collectionGate.indexOf("Holder<Biome> rerouted ="))),
                "the two dry-warm gate overloads must resolve identically, call for call — a "
                        + "divergence here is live generation parting company with the map the "
                        + "atlas drew");

        // 2 and 3 drive the real picker, so they need a live worldgen context.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        int radius = 10_000;
        Climate.Sampler sampler = coverageSampler(null);
        List<String> ids = registry.keySet().stream().map(Identifier::toString).toList();

        for (long seed : new long[]{3L, 131L, 461L}) {
            BiomeSelectionProfile profile = BiomeSelectionProfile.capture(ids);
            try {
                LatitudeBiomes.activateWorldgenContext(
                        radius,
                        seed,
                        LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                        profile,
                        VanillaBiomeRepresentationProfile.capture(radius, seed, profile),
                        sampler,
                        null,
                        63);
                VanillaBiomeCoveragePlan plan = LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
                assertTrue(plan != null && plan.complete(),
                        "the dry-warm gate proof needs a complete birth-locked land plan (seed="
                                + seed + ")");

                // 2. A humid donor is the shape of donor this gate exists to catch. Whatever route
                //    it takes, the subtropical dry province must hand back DESERT — never the
                //    grassland this gate used to name — and the two pickers must not disagree about
                //    which of desert/badlands the column is.
                AridBeltCensus humid = censusSubtropicalDryProvince(
                        registry, pool, testBiomeHolder(registry, "minecraft:jungle"),
                        radius, sampler, plan);
                assertTrue(humid.dryColumns >= 50,
                        "the sweep must actually land in the subtropical dry province, or it proves "
                                + "nothing: seed=" + seed + " columns=" + humid.dryColumns);
                assertGreaterThan(0.15, humid.dryDesert / (double) humid.dryColumns,
                        "a jungle donor standing in the subtropical dry province must come out as "
                                + "desert at a real rate, not be talked into savanna: seed=" + seed
                                + " desert=" + humid.dryDesert
                                + " badlands=" + humid.dryBadlands
                                + " of " + humid.dryColumns + " dry columns");
                assertTrue(humid.outsideProvince >= 20,
                        "the sweep must reach dry ground outside the badlands province: seed=" + seed
                                + " outside=" + humid.outsideProvince);
                assertGreaterThan(0.70,
                        humid.outsideProvinceDesert / (double) humid.outsideProvince,
                        "outside its own province the dry belt reads as desert for a humid donor "
                                + "too: seed=" + seed + " desert=" + humid.outsideProvinceDesert
                                + " badlands=" + humid.outsideProvinceBadlands
                                + " of " + humid.outsideProvince + " columns outside the province");
                assertEquals(0, humid.familyDisagreements,
                        "the registry and collection pickers must never disagree about desert vs "
                                + "badlands at the same column, from a humid donor either (seed="
                                + seed + ")");

                // 3. EQUATOR GUARD, from the humid donors that reach the late warm gates. "Tropical
                //    desert and badlands stay exactly zero" is a release constraint, and this gate
                //    now produces arid on a path that runs AFTER the clamp which owns the tropical
                //    law — so the law is re-applied inside the gate, and this sweep is what says so.
                //    aridFamilyOf() answers "other" for everything that is neither desert nor one of
                //    the three badlands ids, which is exactly the ARID_IDS set.
                int tropicalColumns = 0;
                for (String donorId : new String[]{"minecraft:jungle", "minecraft:forest"}) {
                    Holder<Biome> donor = testBiomeHolder(registry, donorId);
                    for (double fraction : new double[]{0.04, 0.10, 0.16, 0.22}) {
                        for (int sign : new int[]{1, -1}) {
                            int z = sign * (int) Math.round(radius * fraction);
                            for (int x = -8_192; x <= 8_192; x += 512) {
                                if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                                // 0 == BAND_TROPICAL.
                                if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius) != 0) {
                                    continue;
                                }
                                tropicalColumns++;
                                String registryId = LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                                        registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"));
                                String collectionId = LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                                        pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"));
                                String where = " — even from a " + donorId + " donor — at x=" + x
                                        + " z=" + z + " (lat=" + (fraction * 90.0) + " degrees, seed="
                                        + seed + ")";
                                assertEquals("other", aridFamilyOf(registryId),
                                        "the tropical band must never return arid" + where
                                                + " (registry): " + registryId);
                                assertEquals("other", aridFamilyOf(collectionId),
                                        "the tropical band must never return arid" + where
                                                + " (collection): " + collectionId);
                            }
                        }
                    }
                }
                assertTrue(tropicalColumns >= 300,
                        "the equator guard must actually reach the tropical band: seed=" + seed
                                + " columns=" + tropicalColumns);
            } finally {
                LatitudeBiomes.clearWorldgenContext();
            }
        }
    }

    /**
     * What one seed's tropical WARM_MEDIUM belt actually generates, through the real picker.
     *
     * <p>THREE buckets, not two (maintainer ruling, 2026-08-18). Savanna has two homes in this belt
     * — its countries, and the dry fringe hugging an arid province — so a column is inside a
     * country, in the fringe, or in neither. The {@code outsideCountry*} counters mean "outside
     * BOTH", which is what keeps the outside-country forest floor an honest 100% instead of a
     * weakened one: a fringe column that reads savanna is not a leak, it is the fix.
     */
    private static final class SavannaBeltCensus {
        int columns;
        int savanna;
        int forest;
        int jungle;
        int insideCountry;
        int insideCountrySavanna;
        int fringe;
        int fringeSavanna;
        int fringeForest;
        int outsideCountry;
        int outsideCountryForest;
        int outsideCountrySavanna;
        int parityDisagreements;
    }

    private static boolean isSavannaId(String id) {
        return "minecraft:savanna".equals(id)
                || "minecraft:savanna_plateau".equals(id)
                || "minecraft:windswept_savanna".equals(id);
    }

    private static boolean isJungleId(String id) {
        return "minecraft:jungle".equals(id)
                || "minecraft:bamboo_jungle".equals(id)
                || "minecraft:sparse_jungle".equals(id)
                || "terralith:tropical_jungle".equals(id);
    }

    /**
     * Drives both public pickers across the tropical WARM_MEDIUM belt and splits the result by the
     * savanna country the picker itself consulted.
     */
    private static SavannaBeltCensus censusTropicalWarmMediumBelt(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int radius,
            Climate.Sampler sampler,
            VanillaBiomeCoveragePlan plan) {
        // 0 == BAND_TROPICAL. Latitudes 3.6 to 18 degrees.
        return censusWarmMediumBelt(registry, pool, donor, radius, sampler, plan,
                0, new double[]{0.04, 0.08, 0.12, 0.16, 0.20});
    }

    /**
     * The same sweep across the SUBTROPICAL half of WARM_MEDIUM (2026-08-18).
     *
     * <p>WARM_MEDIUM spans tropical and subtropical together, and the enforcer call sites inside
     * {@code pickTropicalGradient} — two per picker overload — are subtropical-only, so the tropical
     * sweep above never reaches them. Latitudes 24.3 to 33.3 degrees: above the 23.5-degree band
     * boundary with room for the band-blend jitter, and below the 35-degree temperate line.
     */
    private static SavannaBeltCensus censusSubtropicalWarmMediumBelt(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int radius,
            Climate.Sampler sampler,
            VanillaBiomeCoveragePlan plan) {
        // 1 == BAND_SUBTROPICAL.
        return censusWarmMediumBelt(registry, pool, donor, radius, sampler, plan,
                1, new double[]{0.27, 0.29, 0.31, 0.33, 0.35, 0.37});
    }

    /**
     * Drives both public pickers across one band's WARM_MEDIUM columns and splits the result by the
     * savanna country the picker itself consulted.
     */
    private static SavannaBeltCensus censusWarmMediumBelt(
            MappedRegistry<Biome> registry,
            List<Holder<Biome>> pool,
            Holder<Biome> donor,
            int radius,
            Climate.Sampler sampler,
            VanillaBiomeCoveragePlan plan,
            int bandIndex,
            double[] fractions) {
        SavannaBeltCensus census = new SavannaBeltCensus();
        for (double fraction : fractions) {
            for (int sign : new int[]{1, -1}) {
                int z = sign * (int) Math.round(radius * fraction);
                for (int x = -8_192; x <= 8_192; x += 128) {
                    if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                    // Read the picker's own final band decision rather than re-deriving it, exactly
                    // as the arid-belt census does.
                    if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius)
                            != bandIndex) {
                        continue;
                    }
                    if (LatitudeBiomes.classifyProvince(x, z)
                            != ProvinceAuthority.Province.WARM_MEDIUM) {
                        continue;
                    }
                    // Guaranteed coverage provinces are placed by the birth plan, not chosen by the
                    // picker; counting them would measure the plan.
                    if (plan != null && plan.match(x, z) != null) continue;

                    String registryId = LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                            registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"));
                    String collectionId = LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                            pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"));
                    if (!registryId.equals(collectionId)) census.parityDisagreements++;

                    census.columns++;
                    if (isSavannaId(registryId)) census.savanna++;
                    if ("minecraft:forest".equals(registryId)) census.forest++;
                    if (isJungleId(registryId)) census.jungle++;
                    if (LatitudeBiomes.savannaCountryHitForPolicyTest(x, z, radius)) {
                        census.insideCountry++;
                        if (isSavannaId(registryId)) census.insideCountrySavanna++;
                    } else if (LatitudeBiomes.savannaDryFringeHitForPolicyTest(x, z)) {
                        // Savanna's second home. Counted apart from the outside-country bucket so
                        // the forest floor below keeps measuring what it was written to measure:
                        // the belt outside EVERY savanna home reading as forest.
                        census.fringe++;
                        if (isSavannaId(registryId)) census.fringeSavanna++;
                        if ("minecraft:forest".equals(registryId)) census.fringeForest++;
                    } else {
                        census.outsideCountry++;
                        if ("minecraft:forest".equals(registryId)) census.outsideCountryForest++;
                        if (isSavannaId(registryId)) census.outsideCountrySavanna++;
                    }
                }
            }
        }
        return census;
    }

    /**
     * Savanna is a COUNTRY inside the warm-medium belt, not the belt itself.
     *
     * <p>Vanilla-only, the tropical band generated savanna on roughly half of all land. The pools
     * were never the problem: {@code minecraft:savanna} is not in the tropical allowed pool at all,
     * and every tropical column starts jungle-family. The problem was that the fair pick was
     * overwritten by a chain of rewrites that all named one literal id — chiefly
     * {@code gateWarmJungleSurvival}, which runs AFTER {@code enforceLandBandPool} and therefore
     * gets the last word, and which handed every WARM_MEDIUM column to
     * {@code enforceWarmProvinceFamily} whose WARM_MEDIUM arm returned {@code minecraft:savanna}
     * with no roll, no pool and no geography.
     *
     * <p>{@code savannaProvinceAuthorityHit} is now the authority for where savanna lives, in the
     * same way {@code badlandsProvinceAuthorityHit} became the authority for badlands the same day.
     * Inside a country, nothing changes. Outside one the belt is {@code minecraft:forest} — which
     * is why forest had to join the tropical band pool, and why the teeth were checked in three
     * stages rather than one. Measured 2026-08-18, each stage reverted on its own:
     * <ul>
     *   <li>predicate neutered to "the whole province is one country" — the pre-change world —
     *       fails at 718 inside / 0 outside;</li>
     *   <li>gate left stamping savanna while everything else stays wired — fails with 220 of 518
     *       outside-country columns back to savanna, so the gate is independently load-bearing;</li>
     *   <li>tropical forest pool admission removed — fails from a savanna donor with 150
     *       outside-country columns back to savanna.</li>
     * </ul>
     * "Looks wired, does nothing" is the failure mode all three exist to make impossible.
     *
     * <p>What it does to the tropical band, measured through {@code pick} from a plains donor over
     * the whole band (8 latitudes, 128-block steps, radius 10000, this suite's roster), with the
     * predicate neutered for the "before" row:
     *
     * <pre>
     *   seed   savanna          forest          jungle family
     *      3   61.9% -> 30.3%    5.8% -> 38.5%   32.3% -> 31.2%
     *    131   54.2% -> 26.6%    5.9% -> 33.9%   40.0% -> 39.5%
     *    461   42.7% -> 28.8%    2.9% -> 17.2%   54.4% -> 54.0%
     * </pre>
     *
     * <p>The jungle family moves by at most 1.1 points on any seed, which is the constraint this
     * slice had to hold: it is WARM_MEDIUM-scoped, and the equatorial jungle core is not its
     * business. The savanna that leaves becomes forest almost exactly one for one.</p>
     *
     * <p>THE DRY FRINGE, added 2026-08-18 by the same maintainer ruling, is savanna's second home
     * and is proved by sections 1b and 2b. Making the belt forest outside a country took savanna
     * off the one border it most belongs on: measured over three vanilla seeds, lush neighbours of
     * the badlands family rose 156-&gt;350 / 189-&gt;288 / 33-&gt;131 while dry-transition neighbours fell
     * 894-&gt;658 / 788-&gt;454 / 619-&gt;343. A WARM_MEDIUM column whose effective moisture sits within
     * {@code WARM_DRY_FRINGE_WIDTH} of the dry threshold is now savanna whatever country it is or
     * is not in, which restores the buffer without a neighbour query or a new noise field. What it
     * adds to the belt, measured through {@code pick} from a jungle donor on this suite's roster:
     *
     * <pre>
     *   seed   tropical WARM_MEDIUM savanna    subtropical WARM_MEDIUM savanna
     *      3   36.2% -> 44.6%                  55.6% -> 62.9%
     *    131   47.0% -> 49.7%                  62.0% -> 65.4%
     *    461   60.8% -> 65.0%                  49.5% -> 58.9%
     * </pre>
     *
     * <p>Not one column outside BOTH homes came back savanna on any seed, and the jungle family is
     * untouched by construction — the fringe is additive on the MEDIUM side only and section 3
     * re-asserts that WARM_WET never answers true.
     *
     * <p>HONEST SCOPE, two limits. Section 1 is a source-text pin, not a world measurement — it
     * proves the country is CONSULTED where it has to be, and sections 2 through 7 prove what that
     * consult does. And nothing here touches {@code minecraft:windswept_savanna}: this suite's test
     * registry is built with an empty tag map, so the pools that biome is drawn from are empty and
     * no sweep through {@code pick} could reach it; in live generation it survives only via
     * {@code savannaTierByY}'s elevation tier, which is downstream of everything this slice edits.
     *
     * <p>Section 6's floors are DESK-DERIVED rather than measured — see the note on
     * {@link #assertSubtropicalCountrySplit}. Section 7 runs at radius 3750 and is the only part of
     * this method that exercises {@code SAVANNA_PROVINCE_MIN_SCALE_BLOCKS} at all: above radius
     * 4267 the floor never binds.
     */
    private static void savannaIsACountryInsideTheWarmBelt() throws Exception {
        // 1. STRUCTURAL. The country has to be consulted at the two places that decide, and the two
        //    overloads of each have to decide the same way. Verified in both directions against the
        //    pre-change source.
        String source = read("src/main/java/com/example/globe/world/LatitudeBiomes.java");

        String registryEnforcer = method(source,
                "private static Holder<Biome> enforceWarmProvinceFamily(Registry<Biome> biomes,");
        String collectionEnforcer = method(source,
                "private static Holder<Biome> enforceWarmProvinceFamily(Collection<Holder<Biome>> biomes,");
        String registryMediumArm = switchArm(registryEnforcer, "case WARM_MEDIUM ->");
        String collectionMediumArm = switchArm(collectionEnforcer, "case WARM_MEDIUM ->");
        // The core of this slice, pinned the same way the warm-jungle gate overloads are (below):
        // the ordered sequence of decisions each arm makes, comments stripped, must be equal. Three
        // indexOf checks say each arm is individually sane; only this says they are the same world.
        //
        // KNOWN, DELIBERATE ASYMMETRY, and this pin is drawn AROUND it: warmMediumForestStaple's two
        // overloads are not equivalent. The Registry one looks minecraft:forest up in the registry,
        // so it answers for any world that has the biome at all; the Collection one calls entryById
        // on the pool it was handed and answers null when that pool lacks forest. Both arms call the
        // same-named helper in the same order, which is what this sequence compares; the lookup
        // idiom underneath it is exactly the difference decisionSequence exists to ignore.
        assertEquals(decisionSequence(registryMediumArm), decisionSequence(collectionMediumArm),
                "the two warm-medium enforcer arms must decide identically, call for call — this "
                        + "arm is where the savanna country is consulted, and a divergence here is "
                        + "live generation parting company with the map the atlas drew");

        for (String enforcer : List.of(registryEnforcer, collectionEnforcer)) {
            String arm = normalize(switchArm(enforcer, "case WARM_MEDIUM ->"));
            int country = arm.indexOf("savannaCountryHere(blockX, blockZ)");
            int fringe = arm.indexOf("savannaDryFringeHere(blockX, blockZ)");
            int forest = arm.indexOf("warmMediumForestStaple(biomes)");
            int savanna = arm.indexOf("minecraft:savanna\"");
            assertTrue(country >= 0,
                    "the warm-medium arm must ask the savanna country where it is — a province that "
                            + "answers one literal id everywhere is the monoculture this slice "
                            + "exists to remove: " + arm);
            assertTrue(fringe > country && fringe < forest,
                    "the warm-medium arm must ALSO ask the dry fringe, in the same breath as the "
                            + "country and before the forest staple — savanna is the transition "
                            + "between arid and forest, and a belt that consults only the country "
                            + "presses lush forest straight against badlands "
                            + "(maintainer ruling, 2026-08-18): " + arm);
            assertTrue(forest >= 0 && forest < savanna,
                    "outside a savanna country the warm-medium staple is forest, and it must be "
                            + "reached BEFORE the savanna chain or the chain wins every column: "
                            + arm);
            assertTrue(arm.contains("minecraft:windswept_savanna\"")
                            && arm.indexOf("minecraft:windswept_savanna\"") < forest,
                    "windswept_savanna must be exempted from the country rule before the forest "
                            + "staple is applied — it is the WARM_UPLAND mountain identity, not the "
                            + "flat staple this country governs, and it has exactly one legal home: "
                            + arm);
        }

        String registryGate = method(source,
                "private static Holder<Biome> gateWarmJungleSurvival(Registry<Biome> biomes,");
        String collectionGate = method(source,
                "private static Holder<Biome> gateWarmJungleSurvival(Collection<Holder<Biome>> biomes,");
        for (String gate : List.of(registryGate, collectionGate)) {
            String body = normalize(gate);
            assertTrue(body.contains("(savannaCountryHere(blockX, blockZ) "
                            + "|| savannaDryFringeHere(blockX, blockZ)) ? enforceWarmProvinceFamily("),
                    "the warm-jungle gate is the LAST stage that can turn a jungle identity into a "
                            + "warm-belt one and it runs after enforceLandBandPool, so it must "
                            + "consult BOTH of savanna's homes itself — its countries and the dry "
                            + "fringe — rather than inherit an answer: " + body);
            assertTrue(body.contains("warmMediumOutsideCountryStaple("),
                    "the warm-jungle gate must resolve outside-country columns to the shared forest "
                            + "staple: " + body);
            // The WARM_DRY arm's defect, recorded in the 2026-08-18 dry-warm commit and fixed here:
            // it returned pickDryWarmFallback desert with no demote chain, on a path that runs
            // AFTER applyFinalSavannaClimateClamp, which is where the latitude law lives.
            assertFalse(body.contains("pickDryWarmFallback("),
                    "the warm-jungle gate's dry arm must ask the province for its own desert-first "
                            + "order, not a helper that names desert and stops: " + body);
            int enforce = body.indexOf("out, ProvinceAuthority.Province.WARM_DRY, blockX, blockZ)");
            int badlands = body.indexOf("demoteEquatorialBadlands(");
            int desert = body.indexOf("demoteEquatorialDesert(");
            int poleward = body.indexOf("demotePolewardArid(");
            assertTrue(enforce >= 0,
                    "the warm-jungle gate's dry arm must resolve through the shared province "
                            + "helper: " + body);
            assertTrue(badlands > enforce && desert > badlands && poleward > desert,
                    "this gate runs after applyFinalSavannaClimateClamp, so its dry arm must "
                            + "re-apply that clamp's three latitude demotes in the clamp's order — "
                            + "without them a gate-made desert stands at the equator with nothing "
                            + "downstream left to take it back: " + body);
        }
        // The two overloads have to be the same world, not merely both correct. Comments and the
        // Registry/Collection lookup idiom legitimately differ, so the DECISIONS are compared: the
        // ordered sequence of calls each overload makes.
        assertEquals(decisionSequence(registryGate), decisionSequence(collectionGate),
                "the two warm-jungle gate overloads must decide identically, call for call — a "
                        + "divergence here is live generation parting company with the map the "
                        + "atlas drew");

        // Without this admission enforceLandBandPool rerolls the forest produced UPSTREAM of it.
        // Teeth, measured by reverting only this line: from a jungle donor outside-country forest
        // falls 518/518 -> 486/518, and from a savanna donor 150 outside-country columns come back
        // savanna. The savanna donor is the live-worldgen shape, so this line is load-bearing even
        // though the downstream stages carry most of the change.
        String extras = method(source, "private static List<String> allowedExtraBiomeIdsForBand(int bandIndex) {");
        assertTrue(normalize(switchArm(extras, "case BAND_TROPICAL ->")).contains("\"minecraft:forest\""),
                "minecraft:forest must be pool-legal in the tropical band, or the warm-medium "
                        + "staple is rerolled away the moment it is produced: " + extras);

        // The two sanitize overloads ran on different openness/composition thresholds (0.92/0.20
        // against 0.76/0.06), which was masked while forest was pool-illegal and load-bearing the
        // moment it was not. Reconciled to the strict Registry pair.
        for (String signature : List.of(
                "private static Holder<Biome> sanitizeLandBiome(Registry<Biome> biomes,",
                "private static Holder<Biome> sanitizeLandBiome(Collection<Holder<Biome>> biomes,")) {
            // Comments stripped: the note left in the source names the retired numbers on purpose.
            String body = normalize(method(source, signature).replaceAll("(?m)^\\s*//.*$", ""));
            assertTrue(body.contains("openness < 0.92 || compositionBias <= 0.20"),
                    "both sanitize overloads must use the canonical tropical repaint gate: " + body);
            assertTrue(body.contains("openness >= 0.96 && compositionBias > 0.28"),
                    "both sanitize overloads must use the canonical savanna-promotion branch: " + body);
            assertTrue(body.contains("compositionBias > 0.32"),
                    "both sanitize overloads must use the canonical swamp-promotion branch: " + body);
            assertFalse(body.contains("0.76") || body.contains("0.06") || body.contains("0.78"),
                    "the old Collection-only threshold ladder must be gone: " + body);
        }

        // NOT ASSERTED HERE, on purpose: pickTropicalGradient's `boolean mountainLike = false`.
        // Threading the column's real mountain truth into it was tried in this slice and reverted
        // as INERT — filteredAllowedLandPool has already dropped windswept_savanna from the
        // subtropical pool on every non-mountain column, so the veto it feeds has nothing to veto
        // and the output never moved. A structural pin on that parameter would have asserted a
        // wiring the pipeline overrides three stages later: a test that cannot fail for the reason
        // it claims to guard. The real repair re-keys landPoolVariantKey and belongs to its own
        // slice; see the comment at the restored local.

        // 2 through 7 drive the real picker or the real country field, so they need a live worldgen
        // context. 7 activates its own, at a different radius.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MappedRegistry<Biome> registry = testBiomeRegistry();
        List<Holder<Biome>> pool = registry.listElements()
                .map(entry -> (Holder<Biome>) entry)
                .toList();
        int radius = 10_000;
        Climate.Sampler sampler = coverageSampler(null);
        List<String> ids = registry.keySet().stream().map(Identifier::toString).toList();

        for (long seed : new long[]{3L, 131L, 461L}) {
            BiomeSelectionProfile profile = BiomeSelectionProfile.capture(ids);
            try {
                LatitudeBiomes.activateWorldgenContext(
                        radius,
                        seed,
                        LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                        profile,
                        VanillaBiomeRepresentationProfile.capture(radius, seed, profile),
                        sampler,
                        null,
                        63);
                VanillaBiomeCoveragePlan plan = LatitudeBiomes.activeVanillaCoveragePlanForPolicyTest();
                assertTrue(plan != null && plan.complete(),
                        "the savanna-country proof needs a complete birth-locked land plan (seed="
                                + seed + ")");

                // 1b. THE FRINGE TRUTH TABLE, driven off the authority's own moisture field rather
                //     than through the picker: this is a statement about the PREDICATE, and the
                //     behavioural half lives in 2b below.
                assertDryFringeTruthTable(seed, radius);

                // 2. THE REGRESSION TEST. A jungle donor is what the warm-jungle gate exists to
                //    catch, and every tropical column starts jungle-family, so this is the belt's
                //    real input. Inside a country it must still come out savanna; outside one it
                //    must come out forest. Both halves matter: the first is the positive control
                //    that savanna was regionalised rather than deleted, and the second is the
                //    change itself.
                SavannaBeltCensus jungleDonor = censusTropicalWarmMediumBelt(
                        registry, pool, testBiomeHolder(registry, "minecraft:jungle"),
                        radius, sampler, plan);
                assertTrue(jungleDonor.columns >= 200,
                        "the belt sweep must actually land in the tropical warm-medium province, or "
                                + "it proves nothing: seed=" + seed + " columns=" + jungleDonor.columns);
                assertTrue(jungleDonor.insideCountry >= 40 && jungleDonor.outsideCountry >= 40,
                        "the sweep must reach BOTH sides of the country boundary, or it cannot tell "
                                + "a country from a field that is on everywhere or off everywhere: "
                                + "seed=" + seed + " inside=" + jungleDonor.insideCountry
                                + " outside=" + jungleDonor.outsideCountry
                                + " of " + jungleDonor.columns);
                // Measured: 93%, 85%, 85% savanna inside a country. The remainder is the band-blend
                // fringe, where the picker's own band decision and the authority's disagree and the
                // province enforcer is never reached at all -- untouched by this slice, and forest
                // or jungle there both predate it.
                assertGreaterThan(0.80,
                        jungleDonor.insideCountrySavanna / (double) jungleDonor.insideCountry,
                        "savanna must still own its country — it is regional now, not gone: seed="
                                + seed + " savanna=" + jungleDonor.insideCountrySavanna
                                + " of " + jungleDonor.insideCountry + " columns inside a country");
                // 100% forest outside EVERY savanna home on all three seeds. Re-measured after the
                // dry fringe joined the belt (2026-08-18), which moved fringe columns into their
                // own bucket rather than weakening this floor: 344/344, 260/260, 118/118.
                assertGreaterThan(0.95,
                        jungleDonor.outsideCountryForest / (double) jungleDonor.outsideCountry,
                        "outside every savanna home the warm belt must read as forest: seed=" + seed
                                + " forest=" + jungleDonor.outsideCountryForest
                                + " savanna=" + jungleDonor.outsideCountrySavanna
                                + " of " + jungleDonor.outsideCountry + " columns outside a country");
                assertEquals(0, jungleDonor.outsideCountrySavanna,
                        "no savanna may stand outside BOTH of savanna's homes from a jungle donor — "
                                + "that is the monoculture leaking back in (seed=" + seed + ")");
                // 2b. THE DRY FRINGE, savanna's second home (maintainer ruling, 2026-08-18). These
                //     columns are outside every country and would have read forest before this
                //     slice; they are the buffer that used to stand between arid country and the
                //     lush belt, and they have to come out savanna or the fix is inert.
                // Measured 83 / 24 / 20 fringe columns outside every country on these seeds; the
                // floor is set below the smallest of them because the fringe is a shell around the
                // arid provinces a seed happens to place, not a fixed share of the belt.
                assertTrue(jungleDonor.fringe >= 15,
                        "the sweep must actually reach the dry fringe, or the fringe assertions "
                                + "below prove nothing: seed=" + seed + " fringe="
                                + jungleDonor.fringe + " of " + jungleDonor.columns);
                // Measured 2026-08-18 at width 0.06: 60/83, 17/24, 20/20 savanna. The shortfall is
                // the same band-blend fringe that keeps inside-country savanna at 85-93% rather
                // than 100% — columns where the picker's own band decision and the authority's
                // disagree, so the province enforcer is never reached at all. It predates this
                // slice and is not what this floor is measuring.
                assertGreaterThan(0.65,
                        jungleDonor.fringeSavanna / (double) jungleDonor.fringe,
                        "the dry fringe must read as savanna — it is the transition between arid "
                                + "and forest, and leaving it forest is what pressed lush forest "
                                + "against badlands: seed=" + seed + " savanna="
                                + jungleDonor.fringeSavanna + " forest=" + jungleDonor.fringeForest
                                + " of " + jungleDonor.fringe + " fringe columns");
                // The differential is the assertion with real teeth: it fails the moment the OR is
                // dropped, whatever the absolute levels are.
                assertGreaterThan(0.60,
                        (jungleDonor.fringeSavanna / (double) jungleDonor.fringe)
                                - (jungleDonor.outsideCountrySavanna
                                        / (double) jungleDonor.outsideCountry),
                        "the fringe must MOVE the answer relative to the rest of the outside-country "
                                + "belt — equal shares mean the fringe consult is not happening: "
                                + "seed=" + seed + " fringeSavanna=" + jungleDonor.fringeSavanna
                                + "/" + jungleDonor.fringe + " outsideSavanna="
                                + jungleDonor.outsideCountrySavanna + "/"
                                + jungleDonor.outsideCountry);
                assertEquals(0, jungleDonor.parityDisagreements,
                        "the registry and collection pickers must never disagree about savanna vs "
                                + "forest at the same column — that is live generation diverging "
                                + "from the map the atlas drew (seed=" + seed + ")");

                // The province decides, not the donor. A savanna donor is the sharp case: the
                // enforcer's savanna-family early return used to hand it straight back untouched,
                // which is how savanna re-admitted itself outside its own country.
                SavannaBeltCensus savannaDonor = censusTropicalWarmMediumBelt(
                        registry, pool, testBiomeHolder(registry, "minecraft:savanna"),
                        radius, sampler, plan);
                assertEquals(0, savannaDonor.outsideCountrySavanna,
                        "an incoming vanilla savanna must not talk the belt out of forest outside "
                                + "both of savanna's homes (seed=" + seed + ")");
                assertEquals(jungleDonor.savanna, savannaDonor.savanna,
                        "the geography decides how much savanna the belt generates, not what came "
                                + "in (seed=" + seed + ")");

                // 3. WARM_WET IS UNTOUCHED. This slice is WARM_MEDIUM-scoped; the jungle core is a
                //    release constraint and must not move.
                int wetColumns = 0;
                int wetJungle = 0;
                for (double fraction : new double[]{0.04, 0.10, 0.16, 0.22}) {
                    for (int sign : new int[]{1, -1}) {
                        int z = sign * (int) Math.round(radius * fraction);
                        for (int x = -8_192; x <= 8_192; x += 256) {
                            if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                            if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius) != 0) continue;
                            if (LatitudeBiomes.classifyProvince(x, z)
                                    != ProvinceAuthority.Province.WARM_WET) continue;
                            if (plan != null && plan.match(x, z) != null) continue;
                            wetColumns++;
                            String id = LatitudeBiomes.biomeIdPublic(LatitudeBiomes.pick(
                                    registry, testBiomeHolder(registry, "minecraft:jungle"),
                                    x, z, 80, radius, sampler, "ATLAS_SAMPLER"));
                            if (isJungleId(id)) wetJungle++;
                            assertFalse(isSavannaId(id),
                                    "the savanna country must never claim a WARM_WET column at x="
                                            + x + " z=" + z + " (seed=" + seed + "): " + id);
                            assertTrue(LatitudeBiomes.savannaCountryHitForPolicyTest(x, z, radius) == false,
                                    "the country predicate must answer false outside WARM_MEDIUM at "
                                            + "x=" + x + " z=" + z + " (seed=" + seed + ")");
                            assertFalse(LatitudeBiomes.savannaDryFringeHitForPolicyTest(x, z),
                                    "the dry-fringe predicate must answer false in WARM_WET — the "
                                            + "fringe is additive on the MEDIUM side only and the "
                                            + "jungle core is not its business — at x=" + x
                                            + " z=" + z + " (seed=" + seed + ")");
                        }
                    }
                }
                assertTrue(wetColumns >= 50,
                        "the WARM_WET guard must actually reach the jungle core: seed=" + seed
                                + " columns=" + wetColumns);
                assertGreaterThan(0.90, wetJungle / (double) wetColumns,
                        "a jungle donor in WARM_WET must stay jungle-family: seed=" + seed
                                + " jungle=" + wetJungle + " of " + wetColumns);

                // 4. EQUATOR ARID GUARD. Tropical desert and badlands are exactly zero and stay
                //    there. This slice edited a gate (gateWarmJungleSurvival's dry arm) that can
                //    produce arid downstream of the clamp owning the tropical law, so the guard is
                //    re-run from the donors that reach that gate.
                int tropicalColumns = 0;
                for (String donorId : new String[]{
                        "minecraft:jungle", "minecraft:forest", "minecraft:savanna"}) {
                    Holder<Biome> donor = testBiomeHolder(registry, donorId);
                    for (double fraction : new double[]{0.04, 0.10, 0.16, 0.22}) {
                        for (int sign : new int[]{1, -1}) {
                            int z = sign * (int) Math.round(radius * fraction);
                            for (int x = -8_192; x <= 8_192; x += 512) {
                                if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                                if (LatitudeBiomes.finalPickerLandBandIndexForPolicyTest(x, z, radius) != 0) continue;
                                tropicalColumns++;
                                String where = " — even from a " + donorId + " donor — at x=" + x
                                        + " z=" + z + " (lat=" + (fraction * 90.0) + " degrees, seed="
                                        + seed + ")";
                                assertEquals("other", aridFamilyOf(LatitudeBiomes.biomeIdPublic(
                                                LatitudeBiomes.pick(registry, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"))),
                                        "the tropical band must never return arid" + where + " (registry)");
                                assertEquals("other", aridFamilyOf(LatitudeBiomes.biomeIdPublic(
                                                LatitudeBiomes.pick(pool, donor, x, z, 80, radius, sampler, "ATLAS_SAMPLER"))),
                                        "the tropical band must never return arid" + where + " (collection)");
                            }
                        }
                    }
                }
                assertTrue(tropicalColumns >= 300,
                        "the equator guard must actually reach the tropical band: seed=" + seed
                                + " columns=" + tropicalColumns);

                // 5. THE COUNTRY IS A COUNTRY. Coherent, not confetti (Article VI), and it covers a
                //    real minority-to-half of its province rather than everything or nothing.
                int seen = 0;
                int transitions = 0;
                for (double fraction : new double[]{0.04, 0.08, 0.12, 0.16, 0.20}) {
                    for (int sign : new int[]{1, -1}) {
                        int scanZ = sign * (int) Math.round(radius * fraction);
                        Boolean previous = null;
                        for (int x = -8_192; x <= 8_192; x += 128) {
                            if (LatitudeBiomes.classifyProvince(x, scanZ)
                                    != ProvinceAuthority.Province.WARM_MEDIUM) {
                                previous = null;
                                continue;
                            }
                            boolean hit = LatitudeBiomes.savannaCountryHitForPolicyTest(x, scanZ, radius);
                            assertEquals(hit,
                                    LatitudeBiomes.savannaCountryHitForPolicyTest(x, scanZ, radius),
                                    "the country predicate must be deterministic at x=" + x
                                            + " z=" + scanZ);
                            seen++;
                            if (previous != null && previous != hit) transitions++;
                            previous = hit;
                        }
                    }
                }
                double countryShare = jungleDonor.insideCountry / (double) jungleDonor.columns;
                // The tuning target the maintainer approved: savanna countries cover roughly a
                // third to a half of WARM_MEDIUM, so the belt reads as forest with savanna regions
                // in it rather than either extreme. Measured on these three seeds: 27.9%, 40.7%,
                // 55.3%. The bounds are wide on purpose -- this is a coherent noise field sampled
                // over a belt only a few cells deep, so per-seed spread is expected and is not the
                // thing under test; "is it a country at all" is.
                //
                // THE SCALE IS WHY THESE BOUNDS CAN BE THIS TIGHT. The same field at the badlands
                // province's scale (frac 0.30) measured 6.3%, 30.8% and 74.0% on these seeds,
                // because the tropical belt is only ~2600 blocks deep and fits inside a single
                // 3000-block noise cell. If a future edit widens SAVANNA_PROVINCE_SCALE_FRAC back
                // toward the badlands value, this assertion is what catches it.
                assertTrue(countryShare > 0.15 && countryShare < 0.75,
                        "a country that covers almost all or almost none of its province is not a "
                                + "country: seed=" + seed + " share=" + countryShare
                                + " of " + jungleDonor.columns + " warm-medium columns");
                // 128-block sampling across ~16k blocks per line: a coherent country crosses its
                // own border a handful of times per line. A per-block hash or a floorDiv cell field
                // would cross constantly. Article VI.
                assertTrue(transitions <= seen / 8,
                        "the savanna country must be coherent, not confetti — Article VI: seed="
                                + seed + " transitions=" + transitions + " of " + seen
                                + " scanned columns");

                // 6. THE SUBTROPICAL HALF OF THE BELT. Everything above sweeps BAND_TROPICAL, and
                //    WARM_MEDIUM spans tropical AND subtropical. The enforcer call sites inside
                //    pickTropicalGradient — the humid diversion and the ladder tail, in each of the
                //    two picker overloads, four in total — are reachable only from a subtropical
                //    column, so nothing above exercises them. They are also where the largest
                //    movement was measured (subtropical savanna down 15 to 25 points), so leaving
                //    them unswept would leave the bigger half of the change unproven.
                //
                //    The subtropical path is cleaner than the tropical one: pickTropicalGradient
                //    hands EVERY warm-medium column to enforceWarmProvinceFamily, and both
                //    minecraft:forest (SUBTROPICAL_HUMID_LOWLAND) and minecraft:savanna
                //    (WARM_TRANSITION) are ledger-legal in this band, so the pool gate rewrites
                //    neither answer. Columns whose picker band is subtropical but whose authority
                //    band is temperate resolve to forest and are counted OUTSIDE, which is what the
                //    country predicate itself answers for them.
                assertSubtropicalCountrySplit(seed, "minecraft:jungle",
                        censusSubtropicalWarmMediumBelt(
                                registry, pool, testBiomeHolder(registry, "minecraft:jungle"),
                                radius, sampler, plan));
                // The province decides, not the donor — the savanna-family early return in the
                // enforcer is the branch that used to hand an incoming savanna straight back.
                assertSubtropicalCountrySplit(seed, "minecraft:savanna",
                        censusSubtropicalWarmMediumBelt(
                                registry, pool, testBiomeHolder(registry, "minecraft:savanna"),
                                radius, sampler, plan));
            } finally {
                LatitudeBiomes.clearWorldgenContext();
            }
        }

        // 7. SMALL-WORLD GEOMETRY. Everything above runs at radius 10000, where
        //    SAVANNA_PROVINCE_SCALE_FRAC alone decides the noise scale and
        //    SAVANNA_PROVINCE_MIN_SCALE_BLOCKS never binds. The floor only binds BELOW radius 4267,
        //    which is precisely the world nobody was measuring: on Itty Bitty (radius 3750) the
        //    tropical belt is 979 blocks deep, so at the old 1024 floor the whole belt sat inside a
        //    single noise cell in Z. This pass re-runs the country-share envelope and the coherence
        //    scan down there so a future edit to either constant cannot quietly restore that.
        assertSavannaCountryGeometryAtRadius(3_750, ids, sampler);
    }

    /**
     * The country-share and coherence envelope at one world radius, driven from the predicate rather
     * than through the picker: this is a statement about the FIELD's geometry, which is what the
     * scale constants control.
     *
     * <p>The per-seed envelope is deliberately wider than the radius-10000 one and the tight
     * envelope is applied to the pooled sample. A 979-block-deep belt is a smaller slice of the same
     * noise field than a 2611-block one, so per-seed spread is expected and is not the thing under
     * test; "does a small world get countries at all, and are they coherent" is.
     */
    private static void assertSavannaCountryGeometryAtRadius(
            int radius, List<String> ids, Climate.Sampler sampler) throws Exception {
        int pooledInside = 0;
        int pooledSeen = 0;
        for (long seed : new long[]{3L, 131L, 461L}) {
            BiomeSelectionProfile profile = BiomeSelectionProfile.capture(ids);
            try {
                LatitudeBiomes.activateWorldgenContext(
                        radius,
                        seed,
                        LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V3_SIZE_AWARE_COVERAGE,
                        profile,
                        VanillaBiomeRepresentationProfile.capture(radius, seed, profile),
                        sampler,
                        null,
                        63);
                int seen = 0;
                int inside = 0;
                int transitions = 0;
                int limit = radius - 128;
                for (double fraction : new double[]{0.04, 0.08, 0.12, 0.16, 0.20}) {
                    for (int sign : new int[]{1, -1}) {
                        int scanZ = sign * (int) Math.round(radius * fraction);
                        Boolean previous = null;
                        for (int x = -limit; x <= limit; x += 64) {
                            if ((long) x * x + (long) scanZ * scanZ >= (long) radius * radius) {
                                previous = null;
                                continue;
                            }
                            if (LatitudeBiomes.classifyProvince(x, scanZ)
                                    != ProvinceAuthority.Province.WARM_MEDIUM) {
                                previous = null;
                                continue;
                            }
                            boolean hit = LatitudeBiomes.savannaCountryHitForPolicyTest(x, scanZ, radius);
                            seen++;
                            if (hit) inside++;
                            if (previous != null && previous != hit) transitions++;
                            previous = hit;
                        }
                    }
                }
                assertTrue(seen >= 200,
                        "the small-world geometry scan must actually land in the tropical "
                                + "warm-medium province: radius=" + radius + " seed=" + seed
                                + " columns=" + seen);
                double share = inside / (double) seen;
                assertTrue(share > 0.05 && share < 0.90,
                        "a savanna country that covers almost all or almost none of its province on "
                                + "a small world is the single-coin-flip geometry the scale floor "
                                + "exists to prevent: radius=" + radius + " seed=" + seed
                                + " share=" + share + " of " + seen + " columns");
                // 64-block sampling: a coherent country crosses its own border a handful of times
                // per line at any radius. Article VI.
                assertTrue(transitions <= seen / 8,
                        "the savanna country must stay coherent on a small world — Article VI: "
                                + "radius=" + radius + " seed=" + seed + " transitions="
                                + transitions + " of " + seen + " scanned columns");
                pooledInside += inside;
                pooledSeen += seen;
            } finally {
                LatitudeBiomes.clearWorldgenContext();
            }
        }
        double pooledShare = pooledInside / (double) pooledSeen;
        assertTrue(pooledShare > 0.15 && pooledShare < 0.75,
                "pooled over its seeds, a small world's savanna countries must cover the same "
                        + "minority-to-half of WARM_MEDIUM the reference world's do — a belt that "
                        + "fits inside one noise cell cannot: radius=" + radius + " share="
                        + pooledShare + " of " + pooledSeen + " columns");
    }

    /**
     * The dry-fringe predicate's truth table, stated in the authority's own numbers.
     *
     * <p>Savanna has two homes in the warm-medium belt: its countries, and the DRY FRINGE — the
     * shell of WARM_MEDIUM whose effective moisture sits within {@code WARM_DRY_FRINGE_WIDTH} of
     * {@code WARM_DRY_THRESHOLD} (maintainer ruling, 2026-08-18). The four rows that matter:
     * a near-threshold WARM_MEDIUM column is in the fringe; a mid-moisture WARM_MEDIUM column is
     * not; a WARM_DRY column is not (it is not MEDIUM at all); a WARM_WET column is not.
     *
     * <p>The moisture read here is the SAME value {@code classifyWarm} thresholds against —
     * {@code ProvinceAuthority.warmMoisture} is one method with two callers precisely so a province
     * map and a fringe map cannot drift — which is why this can assert exact agreement rather than
     * a statistical shape. It also pins the width itself: every fringe column is below
     * {@code WARM_DRY_THRESHOLD + WARM_DRY_FRINGE_WIDTH} and every near-miss column above it is not.
     */
    private static void assertDryFringeTruthTable(long seed, int radius) {
        ProvinceAuthority authority = LatitudeBiomes.getProvinceAuthority();
        assertTrue(authority != null,
                "the fringe truth table needs a live province authority (seed=" + seed + ")");
        double edge = ProvinceAuthority.WARM_DRY_THRESHOLD + ProvinceAuthority.WARM_DRY_FRINGE_WIDTH;
        assertTrue(edge < ProvinceAuthority.WARM_WET_THRESHOLD,
                "the fringe must stay inside WARM_MEDIUM — a width that reaches the wet threshold "
                        + "would make the whole belt savanna again: edge=" + edge);

        int nearThresholdMedium = 0;
        int midMedium = 0;
        int dry = 0;
        int wet = 0;
        for (double fraction : new double[]{0.04, 0.10, 0.16, 0.22, 0.28, 0.32, 0.36}) {
            for (int sign : new int[]{1, -1}) {
                int z = sign * (int) Math.round(radius * fraction);
                for (int x = -8_192; x <= 8_192; x += 128) {
                    if ((long) x * x + (long) z * z >= (long) radius * radius) continue;
                    ProvinceAuthority.Province province = authority.classify(x, z);
                    double moisture = authority.warmMoisture(x, z);
                    boolean fringe = LatitudeBiomes.savannaDryFringeHitForPolicyTest(x, z);
                    String where = " at x=" + x + " z=" + z + " (seed=" + seed
                            + ", province=" + province + ", moisture=" + moisture + ")";
                    switch (province) {
                        case WARM_DRY -> {
                            dry++;
                            assertFalse(fringe,
                                    "a WARM_DRY column is not in the warm-medium fringe — the "
                                            + "fringe is additive on the MEDIUM side only" + where);
                        }
                        case WARM_WET -> {
                            wet++;
                            assertFalse(fringe,
                                    "a WARM_WET column is not in the warm-medium fringe — the "
                                            + "jungle core must not move" + where);
                        }
                        case WARM_MEDIUM -> {
                            if (moisture < edge) {
                                nearThresholdMedium++;
                                assertTrue(fringe,
                                        "a WARM_MEDIUM column within WARM_DRY_FRINGE_WIDTH of the "
                                                + "dry threshold IS the fringe" + where);
                            } else {
                                midMedium++;
                                assertFalse(fringe,
                                        "a WARM_MEDIUM column further than WARM_DRY_FRINGE_WIDTH "
                                                + "from the dry threshold is not the fringe — it is "
                                                + "the forest belt" + where);
                            }
                        }
                        default -> {
                            assertFalse(fringe,
                                    "a cold-side column has no warm province and no fringe" + where);
                        }
                    }
                }
            }
        }
        assertTrue(nearThresholdMedium >= 50 && midMedium >= 50 && dry >= 50 && wet >= 50,
                "the truth table must reach all four of its rows, or it is asserting nothing: "
                        + "seed=" + seed + " nearThresholdMedium=" + nearThresholdMedium
                        + " midMedium=" + midMedium + " dry=" + dry + " wet=" + wet);
    }

    /**
     * The inside/outside split one subtropical WARM_MEDIUM sweep has to show.
     *
     * <p>FLOORS ARE DESK-DERIVED, not measured, and are deliberately loose (the tropical floors in
     * this method were set from three measured seeds; these were not). The traced prediction is
     * ~100% savanna inside and ~100% forest outside for both donors, because in this band
     * pickTropicalGradient routes every warm-medium column through the enforcer and the pool gate
     * accepts both answers. The differential assertion is the one with real teeth: it fails the
     * moment the enforcer stops consulting the country, whatever the absolute levels are.
     */
    private static void assertSubtropicalCountrySplit(long seed, String donorId, SavannaBeltCensus sub) {
        String where = " (seed=" + seed + ", donor=" + donorId + ", subtropical)";
        assertTrue(sub.columns >= 100,
                "the subtropical sweep must actually land in the warm-medium province, or it proves "
                        + "nothing" + where + ": columns=" + sub.columns);
        assertTrue(sub.insideCountry >= 30 && sub.outsideCountry >= 30,
                "the subtropical sweep must reach BOTH sides of the country boundary" + where
                        + ": inside=" + sub.insideCountry + " outside=" + sub.outsideCountry
                        + " of " + sub.columns);
        // The dry fringe matters MOST here: the arid belt lives in the subtropics, so this is the
        // half of the belt where savanna's job is to stand between mesa/desert country and the
        // forest (maintainer ruling, 2026-08-18).
        assertTrue(sub.fringe >= 20,
                "the subtropical sweep must reach the dry fringe — this is the band where the arid "
                        + "belt lives and the buffer matters most" + where + ": fringe="
                        + sub.fringe + " of " + sub.columns);
        // Measured at width 0.06, jungle donor / savanna donor: 57-58 of 60, 23-25 of 34, 68-71 of
        // 73. Not one fringe column in the subtropics came back forest on any seed.
        assertGreaterThan(0.60, sub.fringeSavanna / (double) sub.fringe,
                "the subtropical dry fringe must read as savanna" + where + ": savanna="
                        + sub.fringeSavanna + " forest=" + sub.fringeForest + " of " + sub.fringe
                        + " fringe columns");
        double insideSavanna = sub.insideCountrySavanna / (double) sub.insideCountry;
        double outsideSavanna = sub.outsideCountrySavanna / (double) sub.outsideCountry;
        double outsideForest = sub.outsideCountryForest / (double) sub.outsideCountry;
        assertGreaterThan(0.60, insideSavanna,
                "savanna must still own its country in the subtropics — it is regional now, not "
                        + "gone" + where + ": savanna=" + sub.insideCountrySavanna + " of "
                        + sub.insideCountry + " columns inside a country");
        assertGreaterThan(0.60, outsideForest,
                "outside a savanna country the subtropical warm belt must read as forest" + where
                        + ": forest=" + sub.outsideCountryForest + " savanna="
                        + sub.outsideCountrySavanna + " of " + sub.outsideCountry
                        + " columns outside a country");
        assertTrue(outsideSavanna < 0.20,
                "savanna standing outside its own country in the subtropics is the monoculture "
                        + "leaking back in" + where + ": savanna=" + sub.outsideCountrySavanna
                        + " of " + sub.outsideCountry + " columns outside a country");
        assertGreaterThan(0.40, insideSavanna - outsideSavanna,
                "the country must MOVE the answer at the subtropical enforcer call sites — equal "
                        + "shares inside and outside mean the consult is not happening" + where
                        + ": inside=" + insideSavanna + " outside=" + outsideSavanna);
        assertEquals(0, sub.parityDisagreements,
                "the registry and collection pickers must never disagree at the same subtropical "
                        + "column — that is live generation diverging from the map the atlas drew"
                        + where);
    }

    /**
     * The ordered decision markers a method body makes, with comment lines stripped. Comparing this
     * between two overloads catches a real divergence (one gate consulting the country, its twin
     * not) without failing on the Registry/Collection lookup idiom or on prose differences.
     */
    private static List<String> decisionSequence(String methodBody) {
        String stripped = normalize(methodBody.replaceAll("(?m)^\\s*//.*$", ""));
        List<String> markers = List.of(
                "savannaCountryHere(",
                "savannaDryFringeHere(",
                "warmMediumOutsideCountryStaple(",
                "warmMediumForestStaple(",
                "enforceWarmProvinceFamily(",
                "ProvinceAuthority.Province.WARM_DRY",
                "ProvinceAuthority.Province.WARM_MEDIUM",
                "ProvinceAuthority.Province.WARM_WET",
                "demoteEquatorialBadlands(",
                "demoteEquatorialDesert(",
                "demotePolewardArid(",
                "pickDryWarmFallback(",
                "isSavannaFamily(",
                "isReviewedJungleFamily(",
                "isCustomBiome(",
                "minecraft:savanna\"",
                "minecraft:savanna_plateau\"",
                "minecraft:windswept_savanna\"",
                "minecraft:forest\"");
        List<int[]> found = new ArrayList<>();
        for (int index = 0; index < markers.size(); index++) {
            String marker = markers.get(index);
            for (int at = stripped.indexOf(marker); at >= 0;
                    at = stripped.indexOf(marker, at + marker.length())) {
                found.add(new int[]{at, index});
            }
        }
        found.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<String> sequence = new ArrayList<>(found.size());
        for (int[] hit : found) sequence.add(markers.get(hit[1]));
        return sequence;
    }

    /**
     * The text of one {@code case X ->} arm of a switch, from the label to the brace that closes it.
     * Unlike {@link #routesInSwitchArm} this returns the raw text, which is what an arm-scoped
     * structural assertion needs.
     */
    private static String switchArm(String method, String armLabel) {
        int start = method.indexOf(armLabel);
        if (start < 0) throw new AssertionError("missing switch arm: " + armLabel);
        int searchFrom = start + armLabel.length();
        int depth = 0;
        for (int at = searchFrom; at < method.length(); at++) {
            char c = method.charAt(at);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth <= 0) return method.substring(start, at + 1);
            }
        }
        return method.substring(start);
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
