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
import net.minecraft.world.level.levelgen.DensityFunctions;

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
        wetlandRoutesMatchRealBiomeClimate();
        climateLowlandDescriptorsRemainRouteBounded();
        everySupportedStackGetsEqualRouteTickets();
        selectionIsWorldSeededAndCoherent();
        routeSelectionCannotFallBackToAnUnclassifiedTag();
        providerTicketHotPathCachesAndInvalidates();
        providerProfileCompatibilityIsBirthLocked();
        polarIceSpikeAccentStaysAMinorityInEveryPoolSize();
        polarExtremeCapCatchesNameAlikeModdedBiomesConsistently();
        windsweptFamilyIsSubpolarMountainOnly();
        desertIsTheStapleOfTheSubtropicalAridBelt();
        cliffTreeLandAndOceanAreActuallyReachable();
        riverAndBeachAdmissionIsTagDrivenAndVanillaSafe();
        everyLedgerLandRouteSurvivesTheBandPoolGate();
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
            for (String jungleId : List.of(
                    "minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle",
                    "terralith:tropical_jungle")) {
                Holder<Biome> jungle = testBiomeHolder(registry, jungleId);
                assertPickerPairReturns(
                        registry, pool, jungle, medium[0], medium[1], radius, sampler,
                        "minecraft:savanna",
                        jungleId + " is rewritten by final admission in WARM_MEDIUM");
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
                // patch noise never fires, and an explicitly dry province now refuses
                // wetlands outright, so probe each non-dry lowland anchor with the
                // registry picker for real wetland ownership. If none survives, land
                // coverage is erasing wetland-owned cells and the control fails here.
                VanillaBiomeCoveragePlan.Anchor unrelated = plan.anchors().stream()
                        .filter(anchor -> anchor.route() == BiomeRoute.TEMPERATE_LOWLAND)
                        .filter(anchor -> {
                            ProvinceAuthority.Province province = LatitudeBiomes.classifyProvince(
                                    anchor.blockX(), anchor.blockZ());
                            return province != ProvinceAuthority.Province.WARM_DRY
                                    && province != ProvinceAuthority.Province.COLD_DRY;
                        })
                        .filter(anchor -> {
                            WetlandFootprint probe = new WetlandFootprint(
                                    anchor.blockX(), anchor.blockZ(), anchor.radiusBlocks());
                            wetland.add(probe);
                            try {
                                return "minecraft:swamp".equals(LatitudeBiomes.biomeIdPublic(
                                        LatitudeBiomes.pick(
                                                registry,
                                                testBiomeHolder(registry, "minecraft:swamp"),
                                                anchor.blockX(), anchor.blockZ(), 80, radius,
                                                sampler, "ATLAS_SAMPLER")));
                            } finally {
                                wetland.remove(probe);
                            }
                        })
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "no non-dry temperate-lowland coverage anchor keeps a validated "
                                        + "wetland — land coverage is erasing wetland-owned cells"));
                int[] paleAnchor = LatitudeBiomes.paleGardenAnchorForPolicyTest(sampler);
                assertPickerPairReturns(
                        registry, pool, testBiomeHolder(registry, "minecraft:pale_garden"),
                        paleAnchor[0], paleAnchor[1], radius, sampler,
                        "minecraft:pale_garden",
                        "the real Pale Garden authority survives final selection");
                WetlandFootprint added = new WetlandFootprint(
                        unrelated.blockX(), unrelated.blockZ(), unrelated.radiusBlocks());
                wetland.add(added);
                try {
                    assertPickerPairReturns(
                            registry, pool, testBiomeHolder(registry, "minecraft:swamp"),
                            unrelated.blockX(), unrelated.blockZ(), radius, sampler,
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
                        + "must share the dry-province rejection");
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
        var zero = DensityFunctions.zero();
        var continentalness = new TestDensityFunction(
                (x, z) -> wetland == null || wetland.contains(x, z) ? 0.35 : 0.75);
        var erosion = new TestDensityFunction((x, z) -> syntheticUpland(x) ? -0.6 : 0.0);
        var weirdness = new TestDensityFunction((x, z) -> syntheticUpland(x) ? 0.6 : 0.0);
        return new Climate.Sampler(
                zero,
                zero,
                continentalness,
                erosion,
                zero,
                weirdness,
                List.of());
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

    private record TestDensityFunction(CoordinateValue value)
            implements net.minecraft.world.level.levelgen.DensityFunction.SimpleFunction {
        @Override
        public double compute(net.minecraft.world.level.levelgen.DensityFunction.FunctionContext context) {
            return value.at(context.blockX(), context.blockZ());
        }

        @Override
        public double minValue() {
            return -1.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends net.minecraft.world.level.levelgen.DensityFunction> codec() {
            throw new UnsupportedOperationException("policy-test density functions are not serialized");
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
        assertEquals(2, occurrences(source, "0xBEEFBEEF"),
                "the cold-beach category roll must survive in BOTH pickBeachForBand overloads — it "
                        + "decides snowy vs rocky, and only the identity within that category is "
                        + "tag-driven; losing it in either path silently rerolls every polar "
                        + "coastline on vanilla-only worlds");
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
        String legality = method(source,
                "isWindsweptFamilyLegal(int bandIndex,");
        assertTrue(legality.contains("bandIndex == BAND_SUBPOLAR")
                        && legality.contains("(mountainLike || mountainNoiseLike)"),
                "the reroll's legality predicate must demand BOTH the subpolar band and real "
                        + "mountain evidence — the band alone is what the old COLD_UPLAND route "
                        + "effectively enforced, and it is how flat polar shelves got windswept");
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
        for (double fraction : new double[]{0.30, 0.34, 0.38}) {
            for (int sign : new int[]{1, -1}) {
                int z = sign * (int) Math.round(radius * fraction);
                for (int x = -8_192; x <= 8_192; x += 128) {
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
