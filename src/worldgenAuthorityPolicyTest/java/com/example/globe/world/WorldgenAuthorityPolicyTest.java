package com.example.globe.world;

import com.example.globe.client.create.RecreatedWorldTypePolicy;
import com.example.globe.client.create.RecreatedWorldMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public final class WorldgenAuthorityPolicyTest {
    public static void main(String[] args) throws Exception {
        contextLifecycleIsExplicitAndClearsAllAuthority();
        ordinarySettersDoNotActivateGlobalGuards();
        inlineGeneratorAuthorityIsBoundToTheExactOverworld();
        oldWorldStateDefaultsAreConservativeAndVanillaReadsAreNonCreating();
        biomeColumnCacheIsWorldBoundAndAvoidsDuplicateVerticalPicks();
        frozenRiverVegetationIsScopedWithoutMutatingVanillaBiomes();
        generationScopeIsDimensionIsolatedAndNestSafe();
        generationScopeCleansUpOnFailureAndAcrossThreads();
        finalAridLatitudeLawCannotBeBypassedByLateGates();
        paleGardenCoreClearanceCoversTheWholeWobblingCore();
        paleGardenCoreCannotOverwriteOceanBiomes();
        raisedTerrainCannotKeepAnOceanBiome();
        terrainBiomeCohesionUsesRealSurfaceEvidence();
        recreatedLatitudeWorldKeepsItsWorldTypeAndSize();
        coastalSwampUsesMangroveIdentity();
        wetlandLocateFilterMatchesFinalIdentityLaw();
        profileAdoptionRefusesWorldsLatitudeDidNotGenerate();
        offDiskStateReaderDerivesItsPathFromTheSavedDataId();
        LatitudeLocateBudgetPolicyTest.main(new String[0]);
        BiomeProviderSelectionPolicyTest.run();
        registeredHookIntegrationIsClosed();
        System.out.println("WORLDGEN_AUTHORITY_POLICY_TEST_PASS");
    }

    private static void terrainBiomeCohesionUsesRealSurfaceEvidence() throws Exception {
        int seaLevel = 63;
        int highTerrain = seaLevel + TerrainBiomeCohesionPolicy.HIGH_ABOVE_SEA_BLOCKS;

        assertFalse(
                TerrainBiomeCohesionPolicy.shouldApplyLandGate(
                        true, false, true, highTerrain - 1, 5, seaLevel),
                "ordinary rolling temperate terrain keeps its lowland biome family");
        assertTrue(
                TerrainBiomeCohesionPolicy.shouldApplyLandGate(
                        true, false, true, highTerrain, 0, seaLevel),
                "the first clearly high temperate column runs the terrain compatibility gate");
        assertTrue(
                TerrainBiomeCohesionPolicy.shouldApplyLandGate(
                        true, false, true, seaLevel + 8,
                        TerrainBiomeCohesionPolicy.RUGGED_RELIEF_BLOCKS, seaLevel),
                "a steep temperate shoulder runs the terrain compatibility gate");
        assertTrue(
                TerrainBiomeCohesionPolicy.shouldUseTemperateUplandFamily(
                        true, highTerrain, 0, seaLevel),
                "clearly high temperate terrain is forced into the dedicated upland family");
        assertTrue(
                TerrainBiomeCohesionPolicy.shouldUseWarmUplandFamily(
                        true, seaLevel + 8,
                        TerrainBiomeCohesionPolicy.RUGGED_RELIEF_BLOCKS, seaLevel),
                "a steep warm-band shoulder is forced into the dedicated upland family too");
        assertFalse(
                TerrainBiomeCohesionPolicy.shouldUseWarmUplandFamily(
                        true, seaLevel + 2, 0, seaLevel),
                "ordinary flat warm ground still keeps its lowland family");
        assertTrue(
                TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland(true, false),
                "a late lowland rewrite cannot survive the physical upland authority");
        assertFalse(
                TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland(true, true),
                "a final biome already in the temperate mountain tag is preserved");
        assertFalse(
                TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland(false, false),
                "ordinary temperate land is not globally promoted");
        assertFalse(
                TerrainBiomeCohesionPolicy.shouldApplyLandGate(
                        true, false, false, highTerrain + 30, 30, seaLevel),
                "missing physical terrain evidence fails open");

        assertFalse(
                TerrainBiomeCohesionPolicy.shouldReplaceRiverWithLand(
                        true, highTerrain - 1, seaLevel, true),
                "a river below the clearly-high boundary remains a river");
        assertFalse(
                TerrainBiomeCohesionPolicy.shouldReplaceRiverWithLand(
                        true, highTerrain + 20, seaLevel, false),
                "a genuine raised valley without the mountain signal remains a river");
        assertFalse(
                TerrainBiomeCohesionPolicy.shouldReplaceRiverWithLand(
                        false, highTerrain + 20, seaLevel, true),
                "an unmeasured river is never rewritten from proxy evidence alone");
        assertTrue(
                TerrainBiomeCohesionPolicy.shouldReplaceRiverWithLand(
                        true, highTerrain, seaLevel, true),
                "a river label on a measured high mountain face is rerouted through land selection");

        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertEquals(
                2,
                occurrences(source, "TerrainBiomeCohesionPolicy.shouldReplaceRiverWithLand("),
                "registry and collection pickers both reject high mountain-face rivers");
        assertEquals(
                2,
                occurrences(
                        source,
                        "shouldApplyTerrainGate( landBandIndex, terrainGateDelta, terrainGateHeight, seaLevel, terrainEvidenceAvailable)"),
                "both live picker paths gate against the real terrain height rather than the synthetic preview");
        assertEquals(
                2,
                occurrences(source, "boolean forceTemperateUpland = isLandGateBand(landBandIndex)"),
                "both picker paths force high columns in gated bands through the dedicated mountain tag");
        assertEquals(
                2,
                occurrences(source, "PreviewTerrain gateProbe = onDemandGateTerrain("),
                "both picker paths probe real terrain on demand so the gate is not inert under the"
                        + " worldgen preview fast path");
        assertEquals(
                2,
                occurrences(
                        source,
                        "TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland( forceTemperateUpland, out != null && out.is(LAT_TEMPERATE_MOUNTAIN))"),
                "both picker paths preserve physical upland authority through the final return");
        int firstWetlandLaw = source.indexOf("out = applyFinalWetlandIdentityLaw(");
        int firstFinalUpland = source.indexOf(
                "TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland(");
        int secondWetlandLaw = source.indexOf("out = applyFinalWetlandIdentityLaw(", firstWetlandLaw + 1);
        int secondFinalUpland = source.indexOf(
                "TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland(", firstFinalUpland + 1);
        assertTrue(
                firstWetlandLaw >= 0
                        && firstFinalUpland > firstWetlandLaw
                        && secondWetlandLaw > firstFinalUpland
                        && secondFinalUpland > secondWetlandLaw,
                "the final physical-upland clamp runs after every late arid/wetland rewrite");
        assertTrue(
                source.contains("isBiomeId(candidate, \"minecraft:sunflower_plains\")"),
                "the exact lowland biome visible in TEST 19 is classified with the plains family");
    }

    private static void recreatedLatitudeWorldKeepsItsWorldTypeAndSize() throws Exception {
        assertEquals(
                "globe:globe_large",
                RecreatedWorldTypePolicy.effectivePresetId(
                        true,
                        "minecraft:normal",
                        "globe:overworld_large"),
                "Re-create must recover the Latitude preset from the saved overworld generator");
        assertEquals(
                "globe:globe_xsmall",
                RecreatedWorldTypePolicy.effectivePresetId(
                        true,
                        "minecraft:normal",
                        "globe:overworld_xsmall"),
                "Re-create must preserve the exact Latitude world size");
        assertEquals(
                "minecraft:normal",
                RecreatedWorldTypePolicy.effectivePresetId(
                        true,
                        "minecraft:normal",
                        "minecraft:overworld"),
                "a genuinely vanilla recreated world must stay vanilla");
        assertEquals(
                "minecraft:normal",
                RecreatedWorldTypePolicy.effectivePresetId(
                        false,
                        "minecraft:normal",
                        "globe:overworld_large"),
                "fresh creation must keep the explicit selector state");
        assertEquals(
                "globe:globe_large",
                RecreatedWorldTypePolicy.effectivePresetId(
                        true,
                        "minecraft:normal",
                        "globe:globe_large",
                        null),
                "persisted Latitude identity must override vanilla's generic Normal classification");

        // The fixture MUST be written where Minecraft actually puts the overworld's SavedData on
        // this target -- <world>/data/<SavedData id>.dat -- and the id must come from the same
        // constant the production reader uses. The previous fixture hardcoded 26.2's nested
        // dimensions/minecraft/overworld/data/globe/ path, which happened to match the reader's
        // own stale literal: both sides were wrong in the same way, so this assertion passed
        // while the shipped feature found nothing on every real world.
        Path worldRoot = Path.of("build", "tmp", "recreated-world-metadata-test");
        Path statePath = worldRoot.resolve(
                Path.of("data", LatitudeWorldState.STATE_ID + ".dat"));
        Files.createDirectories(statePath.getParent());
        Files.deleteIfExists(statePath);
        CompoundTag data = new CompoundTag();
        data.putInt("globe_radius", 10000);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        NbtIo.writeCompressed(root, statePath);
        assertEquals(
                "globe:globe_large",
                RecreatedWorldMetadata.latitudePresetId(worldRoot),
                "the live persisted-state format must recover the exact Regular Latitude preset");
        assertTrue(
                RecreatedWorldMetadata.latitudePresetId(worldRoot.resolve("missing-vanilla-world")) == null,
                "a world without Latitude state must not be reclassified");

        String source = normalize(read(
                "src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java"));
        assertTrue(
                source.contains("effectivePresetKey(initialState, recreated, recreatedPresetId)"),
                "the live Re-create hydration path must use persisted Latitude identity");
        assertFalse(
                source.contains("noise.generatorSettings().value().equals(registered.value())"),
                "identical noise settings across Latitude sizes cannot truthfully recover the saved size");

        String entryMixin = normalize(read(
                "src/main/java/com/example/globe/mixin/client/WorldSelectionListEntryMixin.java"));
        assertTrue(
                entryMixin.contains("RecreatedWorldMetadata.latitudePresetId(worldRoot)"),
                "the actual world-list Re-Create call path must read the source world's persisted state");
        assertTrue(
                normalize(read("src/main/resources/globe.mixins.json"))
                        .contains("client.WorldSelectionListEntryMixin"),
                "the persisted Re-Create handoff mixin must be registered at runtime");
        assertTrue(
                Files.isRegularFile(Path.of(
                        "src/main/java/com/example/globe/client/create/RecreatedWorldPresetCarrier.java")),
                "the Re-Create carrier must live outside the reserved mixin package");
        assertFalse(
                Files.exists(Path.of(
                        "src/main/java/com/example/globe/mixin/client/RecreatedWorldPresetCarrier.java")),
                "ordinary helper classes inside a declared mixin package crash Fabric at runtime");
    }

    private static void coastalSwampUsesMangroveIdentity() throws Exception {
        assertTrue(
                WetlandIdentityPolicy.shouldUseMangrove(
                        true, 0, 1, false, 64, 384, true),
                "a suitable tropical coastal swamp is mangrove habitat");
        assertTrue(
                WetlandIdentityPolicy.shouldUseMangrove(
                        true, 1, 1, false, 384, 384, true),
                "the established coastal boundary remains mangrove habitat");
        assertFalse(
                WetlandIdentityPolicy.shouldUseMangrove(
                        true, 2, 1, false, 64, 384, true),
                "temperate lowland swamp remains ordinary swamp");
        assertFalse(
                WetlandIdentityPolicy.shouldUseMangrove(
                        true, 0, 1, false, 385, 384, true),
                "inland swamp remains ordinary swamp");
        assertFalse(
                WetlandIdentityPolicy.shouldUseMangrove(
                        true, 0, 1, true, 64, 384, true),
                "mountainous wetland cannot be promoted to mangrove");
        assertFalse(
                WetlandIdentityPolicy.shouldUseMangrove(
                        true, 0, 1, false, 64, 384, false),
                "coastal proximity alone cannot override unsuitable terrain");
        assertFalse(
                WetlandIdentityPolicy.shouldUseMangrove(
                        false, 0, 1, false, 64, 384, true),
                "non-swamp biomes are not repainted by the wetland identity law");

        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertEquals(
                2,
                occurrences(source, "out = applyFinalWetlandIdentityLaw("),
                "registry and collection pickers both run the final wetland identity law");
        assertEquals(
                0,
                occurrences(source, "decision.suitable())"),
                "final coastal swamp identity must use physical lowland terrain rather than the rejecting climate proxy");
    }

    private static void wetlandLocateFilterMatchesFinalIdentityLaw() throws Exception {
        String biomes = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        String service = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomeLocateService.java"));
        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));

        assertTrue(
                biomes.contains("int landBandIndex = authoritativeLandBandIndex(blockX, blockZ, borderRadiusBlocks);"),
                "the cheap wetland filter must use the same world-size-aware land-band authority as the final picker");
        assertTrue(
                biomes.contains("LatitudeLocateBudgetPolicy.allowsSwampProxyForTarget( includeSwamp, includeMangrove, landBandIndex, BAND_SUBTROPICAL)"),
                "swamp proxy candidates must be filtered by the final swamp-to-mangrove identity boundary");
        assertTrue(
                biomes.contains("boolean directMangroveClimate = includeMangrove"),
                "the independent direct-mangrove candidate path must remain available");

        assertTrue(
                service.contains("private static final int COMMAND_RADIUS = 6_400;")
                        && service.contains("private static final int COMMAND_HORIZONTAL_STEP = 32;")
                        && service.contains("BlockPos.spiralAround("),
                "the optimization must preserve the full-radius nearest-first grid");
        assertTrue(
                service.contains("latitudeSource, worldRadius, randomState.sampler()"),
                "the live service must pass the exact world radius into its job");
        assertTrue(
                service.contains("LatitudeBiomes.isPotentialWetlandLocateCandidate( blockX, blockZ, worldRadius, sampler, includesSwamp, includesMangrove)"),
                "the live service must wire the exact world radius into the cheap filter");
        assertTrue(
                service.contains("Holder<Biome> exact;")
                        && service.contains("exact = latitudeSource.getNoiseBiome("),
                "every admitted candidate must still use the complete terrain-aware biome resolver");
        assertTrue(
                source.contains("boolean isPotentialWetlandLocateSourceCandidate(")
                        && source.contains("LatitudeBiomes.pick( biomeRegistry, base")
                        && source.contains("\"SOURCE\", generator, null, null)"),
                "the second broad phase must use the registry picker and real sea level without resolving terrain height");
        assertTrue(
                source.contains("LatitudeLocateBudgetPolicy.sourcePreviewCanBecomeWetland("),
                "the registry preview must retain wetland and terrain-sensitive shoreline divergence cases");
        assertTrue(
                source.contains("LatitudeBiomes.hasWetlandLocateOceanAuthority("),
                "coarse ocean authority must remain eligible even when its preview identity is rewritten");
        assertTrue(
                service.contains("latitudeSource.isPotentialWetlandLocateSourceCandidate("),
                "the live tick job must use the terrain-free registry preview before exact resolution");
        assertTrue(
                service.contains("tickExactProbes < LatitudeLocateBudgetPolicy.MAX_WETLAND_EXACT_PROBES_PER_TICK")
                        && service.contains("tickExactProbes++;"),
                "the live scheduler must enforce and consume the one-exact-probe tick budget");
    }

    private static void raisedTerrainCannotKeepAnOceanBiome() throws Exception {
        int seaLevel = 63;
        int maximumCoastalRelief = 16;

        assertFalse(
                OceanTerrainCompatibilityPolicy.isClearlyRaisedLand(
                        false, 200, seaLevel, maximumCoastalRelief),
                "missing terrain evidence must fail open");
        assertFalse(
                OceanTerrainCompatibilityPolicy.isClearlyRaisedLand(
                        true, seaLevel + maximumCoastalRelief, seaLevel, maximumCoastalRelief),
                "the established coastal-relief boundary remains ocean-compatible");
        assertTrue(
                OceanTerrainCompatibilityPolicy.isClearlyRaisedLand(
                        true, seaLevel + maximumCoastalRelief + 1, seaLevel, maximumCoastalRelief),
                "the first block above the coastal allowance is unambiguously raised land");

        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertEquals(
                2,
                occurrences(
                        source,
                        "OceanTerrainCompatibilityPolicy.isClearlyRaisedLand("),
                "registry and collection pickers both apply the terrain compatibility policy");
        assertEquals(
                2,
                occurrences(
                        source,
                        "(base.is(BiomeTags.IS_OCEAN) && !clearlyRaisedLand) || oceanAuthority"),
                "neither picker can return the donor ocean biome for clearly raised terrain");
    }

    private static void finalAridLatitudeLawCannotBeBypassedByLateGates() throws Exception {
        assertSame(
                AridLatitudePolicy.Replacement.SAVANNA,
                AridLatitudePolicy.replacementFor(true, 2_611, 10_000, 23.5, 35.5),
                "the last tropical block rejects arid output");
        assertSame(
                AridLatitudePolicy.Replacement.KEEP,
                AridLatitudePolicy.replacementFor(true, 2_612, 10_000, 23.5, 35.5),
                "the subtropical boundary remains available to the normal noise ramp");
        assertSame(
                AridLatitudePolicy.Replacement.KEEP,
                AridLatitudePolicy.replacementFor(true, 3_944, 10_000, 23.5, 35.5),
                "the arid belt interior remains untouched");
        assertSame(
                AridLatitudePolicy.Replacement.PLAINS,
                AridLatitudePolicy.replacementFor(true, 3_945, 10_000, 23.5, 35.5),
                "the first fully poleward block rejects arid output");
        assertSame(
                AridLatitudePolicy.Replacement.KEEP,
                AridLatitudePolicy.replacementFor(false, 0, 10_000, 23.5, 35.5),
                "non-arid biomes are never rewritten");

        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertTrue(
                occurrences(source, "out = applyFinalAridLatitudeLaw(") == 2,
                "both picker overloads run the hard law after every late biome gate");
        assertTrue(
                occurrences(source, "out = enforceFinalWetlandAuthority(") == 2,
                "both picker overloads revalidate wetlands after every late reroll");
        int registryArid = source.indexOf(
                "out = applyFinalAridLatitudeLaw( biomeRegistry");
        int registryWetlandAuthority = source.indexOf(
                "out = enforceFinalWetlandAuthority( biomeRegistry", registryArid);
        int registryWetlandIdentity = source.indexOf(
                "out = applyFinalWetlandIdentityLaw( biomeRegistry", registryWetlandAuthority);
        assertTrue(
                registryArid >= 0
                        && registryWetlandAuthority > registryArid
                        && registryWetlandIdentity > registryWetlandAuthority,
                "the registry picker validates raw wetlands before applying swamp-to-mangrove identity");
        int collectionArid = source.indexOf(
                "out = applyFinalAridLatitudeLaw( biomePool");
        int collectionWetlandAuthority = source.indexOf(
                "out = enforceFinalWetlandAuthority( biomePool", collectionArid);
        int collectionWetlandIdentity = source.indexOf(
                "out = applyFinalWetlandIdentityLaw( biomePool", collectionWetlandAuthority);
        assertTrue(
                collectionArid >= 0
                        && collectionWetlandAuthority > collectionArid
                        && collectionWetlandIdentity > collectionWetlandAuthority,
                "the collection picker validates raw wetlands before applying swamp-to-mangrove identity");
    }

    private static void paleGardenCoreCannotOverwriteOceanBiomes() throws Exception {
        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertTrue(
                occurrences(
                        source,
                        "base == null || base.is(BiomeTags.IS_OCEAN)")
                        == 2,
                "both registry and collection paths reject ocean base biomes before the Pale Garden override");
        assertTrue(
                source.contains(
                        "contiguousPaleGardenCoreOverride( biomeRegistry, base, blockX, blockZ"),
                "the registry path gives the override the exact base biome");
        assertTrue(
                source.contains(
                        "contiguousPaleGardenCoreOverride( biomePool, base, blockX, blockZ"),
                "the collection path gives the override the exact base biome");
        assertTrue(
                source.contains("PALE_GARDEN_V3_ANCHOR_GRID_SIDE = 16")
                        && source.contains("? 1 + 2 * PALE_GARDEN_V3_ANCHOR_GRID_SIDE * PALE_GARDEN_V3_ANCHOR_GRID_SIDE")
                        && source.contains("? hemisphereSign : -hemisphereSign")
                        && source.contains(": PALE_GARDEN_ANCHOR_CANDIDATE_COUNT"),
                "fresh V3 compact worlds search both hemispheres with stratified anchor coverage "
                        + "while legacy policies retain their original candidate sequence");
    }

    private static void paleGardenCoreClearanceCoversTheWholeWobblingCore() {
        assertEquals(
                256,
                OceanDistanceField.GRID_CELL_SIZE_BLOCKS,
                "Pale Garden uncertainty is tied to the distance field's physical grid cell");
        double oceanLimited = PaleGardenCohesionPolicy.maximumCoreRadius(
                500.0,
                1_000,
                384,
                2_000,
                64);
        assertApproximately(
                (1_000.0 - 384.0) / Math.sqrt(2.0),
                oceanLimited,
                "the four-neighbor distance field uses a conservative diagonal bound");
        assertTrue(
                PaleGardenCohesionPolicy.wholeCoreMeetsOceanClearance(
                        1_000,
                        oceanLimited,
                        384),
                "the computed radius keeps every possible core point beyond ocean clearance");
        assertFalse(
                PaleGardenCohesionPolicy.wholeCoreMeetsOceanClearance(
                        1_000,
                        oceanLimited + 1.0,
                        384),
                "a larger unchecked core radius is rejected");

        assertApproximately(
                236.0,
                PaleGardenCohesionPolicy.maximumCoreRadius(
                        500.0,
                        4_000,
                        384,
                        300,
                        64),
                "latitude-band clearance independently caps the whole core");
        assertApproximately(
                0.0,
                PaleGardenCohesionPolicy.maximumCoreRadius(
                        500.0,
                        300,
                        384,
                        2_000,
                        64),
                "an anchor without minimum ocean clearance cannot create a core");

        double preservedBase = PaleGardenCohesionPolicy.baseRadiusPreservingWobble(
                900.0,
                0.12,
                500.0);
        assertApproximately(
                500.0,
                preservedBase * 1.12,
                "the organic boundary reaches but cannot exceed the safe maximum");
        assertTrue(
                preservedBase * 0.88 > 0.0,
                "the scaled star-shaped core keeps a positive radius in every direction");
    }

    private static void contextLifecycleIsExplicitAndClearsAllAuthority() {
        LatitudeBiomes.clearWorldgenContext();
        assertFalse(LatitudeBiomes.hasActiveWorldgenAuthority(), "clean process starts inactive");
        assertEquals(0, LatitudeBiomes.getActiveRadiusBlocks(), "clean process has no radius");
        assertNull(LatitudeBiomes.getProvinceAuthority(), "clean process has no province authority");

        LatitudeBiomes.activateWorldgenContext(7_500, 123_456_789L);
        assertTrue(LatitudeBiomes.hasActiveWorldgenAuthority(), "Globe activation publishes authority");
        assertEquals(7_500, LatitudeBiomes.getActiveRadiusBlocks(), "Globe activation publishes radius");
        assertNotNull(LatitudeBiomes.getProvinceAuthority(), "Globe activation publishes seed-backed province authority");

        LatitudeBiomes.clearWorldgenContext();
        assertFalse(LatitudeBiomes.hasActiveWorldgenAuthority(), "server stop clears authority");
        assertEquals(0, LatitudeBiomes.getActiveRadiusBlocks(), "server stop clears radius");
        assertNull(LatitudeBiomes.getProvinceAuthority(), "server stop clears province authority");
    }

    private static void ordinarySettersDoNotActivateGlobalGuards() {
        LatitudeBiomes.clearWorldgenContext();
        LatitudeBiomes.setRadius(7_500);
        LatitudeBiomes.setWorldSeed(987_654_321L);
        assertFalse(
                LatitudeBiomes.hasActiveWorldgenAuthority(),
                "atlas/helper setters cannot activate globally registered world hooks");
        LatitudeBiomes.clearWorldgenContext();
    }

    private static void inlineGeneratorAuthorityIsBoundToTheExactOverworld() throws Exception {
        Object overworld = new Object();
        Object customDimension = new Object();

        assertTrue(
                WorldgenGeneratorAuthorityPolicy.shouldApply(true, false, customDimension, null),
                "registered Latitude settings retain their explicit authority");
        assertTrue(
                WorldgenGeneratorAuthorityPolicy.shouldApply(false, true, overworld, overworld),
                "the exact loaded inline Latitude overworld is authorized");
        assertFalse(
                WorldgenGeneratorAuthorityPolicy.shouldApply(false, true, customDimension, overworld),
                "an unrelated inline-noise dimension cannot inherit overworld authority");
        assertFalse(
                WorldgenGeneratorAuthorityPolicy.shouldApply(false, false, overworld, overworld),
                "a stale generator identity cannot survive cleared worldgen authority");
        assertFalse(
                WorldgenGeneratorAuthorityPolicy.shouldApply(false, true, null, overworld),
                "a missing candidate generator fails closed");

        String source = normalize(read("src/main/java/com/example/globe/GlobeMod.java"));
        assertFalse(
                source.contains("LatitudeBiomes.hasActiveWorldgenAuthority() && hasInlineSettings(noise)"),
                "production must never authorize every inline generator in the process");
        assertTrue(
                source.contains("activeLatitudeOverworldGenerator = generator instanceof NoiseBasedChunkGenerator noise"),
                "world load captures the exact overworld generator identity");
        assertTrue(
                source.contains("activeLatitudeOverworldGenerator = null; LatitudeBiomes.clearWorldgenContext();"),
                "server stop clears generator identity alongside global biome authority");
    }

    private static void oldWorldStateDefaultsAreConservativeAndVanillaReadsAreNonCreating()
            throws Exception {
        assertSame(
                LatitudeWorldState.WorldgenPolicyVersion.MODERN_1_3,
                LatitudeWorldState.decodeWorldgenPolicy("MODERN_1_3"),
                "known modern policy decodes exactly");
        assertSame(
                LatitudeWorldState.WorldgenPolicyVersion.LEGACY_1_2_X,
                LatitudeWorldState.decodeWorldgenPolicy("FUTURE_UNKNOWN_POLICY"),
                "unknown future policy fails safely to old-world behavior");
        assertSame(
                LatitudeWorldState.WorldgenPolicyVersion.LEGACY_1_2_X,
                LatitudeWorldState.decodeWorldgenPolicy(null),
                "missing policy fails safely to old-world behavior");

        String state = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeWorldState.java"));
        assertTrue(
                state.contains("return world.getDataStorage().get(STATE_TYPE);"),
                "non-creating state lookup uses SavedDataStorage.get");
        assertFalse(
                state.contains("return world.getGameTime() < 100L"),
                "a very young old save is not guessed to be modern from game time alone");

        String mod = normalize(read("src/main/java/com/example/globe/GlobeMod.java"));
        int globeCheck = mod.indexOf("if (!isGlobeOverworld(world))");
        int stateCreate = mod.indexOf("LatitudeWorldState worldState = LatitudeWorldState.get(world)", globeCheck);
        assertTrue(
                globeCheck >= 0 && stateCreate > globeCheck,
                "world load proves Latitude identity before creating saved state");
        assertTrue(
                mod.contains("LatitudeWorldState worldState = isGlobe ? LatitudeWorldState.get(overworld) : null;"),
                "joining a vanilla world cannot create Latitude saved state");
        assertTrue(
                mod.contains("LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V4_CAVE_COVERAGE"),
                "new UI-created Latitude worlds explicitly lock size-aware V4 coverage before generation");
        assertTrue(
                mod.indexOf("worldState.setProviderTicketProfile(profile);")
                        < mod.indexOf("worldState.setVanillaRepresentationProfile(")
                        && mod.indexOf("worldState.setVanillaRepresentationProfile(")
                        < mod.indexOf("worldState.setCaveRepresentationProfile(")
                        && mod.indexOf("worldState.setCaveRepresentationProfile(")
                        < mod.indexOf("LatitudeBiomes.activateWorldgenContext(radius, seed, worldState.getWorldgenPolicy()"),
                "the provider roster and surface/cave representation contracts are captured before spawn-chunk biome authority activates");
    }

    private static void biomeColumnCacheIsWorldBoundAndAvoidsDuplicateVerticalPicks()
            throws Exception {
        String biomes = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertTrue(
                biomes.contains("generator == candidateGenerator")
                        && biomes.contains("noiseConfig == candidateNoiseConfig")
                        && biomes.contains("heightView == candidateHeightView")
                        && biomes.contains("chunkKey == candidateChunkKey"),
                "surface-height cache ownership includes generator, noise state, height view, and chunk");

        String populate = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java"));
        assertFalse(
                populate.contains("columnDecisionYCache"),
                "populate-biomes no longer performs a redundant surface decision solely for its cache gate");
        int caveReturn = populate.indexOf("if (caveCurrent) { return LatitudeBiomes.caveCoverageOverride(biomes, current, blockX, blockY, blockZ); }");
        int columnCache = populate.indexOf(
                "Holder<Biome> cachedPick = columnPickCache.get(colKey);",
                caveReturn);
        int pick = populate.indexOf("Holder<Biome> picked = globe$pickOrNull(", columnCache);
        assertTrue(
                caveReturn >= 0 && columnCache > caveReturn && pick > columnCache,
                "every non-cave quart-Y cell reuses one biome pick per column/base while cave cells use their final V4 identity");
    }

    /**
     * Profile adoption is irreversible: it persists a globe radius, which makes isGlobeOverworld
     * true forever after and arms Latitude's biome authority plus a world border. Run on a world
     * Latitude did not generate, that silently converts someone's vanilla save with no way back.
     * The /latitude retrofit path reached it with no world-type check at all. The guard has to sit
     * in the HANDLER, not on the command node -- ShippingToolsPolicyTest S5 requires zero gated
     * command descendants, so a .requires() would trade this bug for a red suite.
     */
    private static void profileAdoptionRefusesWorldsLatitudeDidNotGenerate() throws Exception {
        String mod = normalize(read("src/main/java/com/example/globe/GlobeMod.java"));
        int guard = mod.indexOf("if (!isLatitudeOverworld(world))");
        int adopt = mod.indexOf("public static void adoptProviderTicketProfile(");
        int firstWrite = mod.indexOf("LatitudeWorldState.get(world)", adopt);
        assertTrue(adopt >= 0, "adoptProviderTicketProfile still exists");
        assertTrue(
                guard > adopt && guard < firstWrite,
                "adoptProviderTicketProfile refuses non-Latitude worlds BEFORE it touches world state");

        String retrofit = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeDecorationRetrofit.java"));
        int request = retrofit.indexOf("public static List<String> requestEnable(");
        int arm = retrofit.indexOf("pendingConfirmDeadlineMs = System.currentTimeMillis()", request);
        int requestGuard = retrofit.indexOf("GlobeMod.isLatitudeOverworld(world)", request);
        assertTrue(
                requestGuard > request && requestGuard < arm,
                "retrofit refuses a non-Latitude world before arming the confirm window");
        assertTrue(
                retrofit.indexOf("GlobeMod.isLatitudeOverworld(world)",
                        retrofit.indexOf("public static List<String> confirmEnable(")) > 0,
                "retrofit confirm re-checks the world type rather than trusting the enable step");
        assertFalse(
                retrofit.contains("LatitudeWorldState.get(world)")
                        && retrofit.indexOf("LatitudeWorldState.get(world)")
                                < retrofit.indexOf("public static List<String> confirmEnable("),
                "warning-only and disable paths read state without creating a .dat on a vanilla save");
    }

    /**
     * The SavedData id IS the on-disk filename. Code that reads that file without a loaded server
     * must derive its path from the id, never re-spell it: the port changed the id from an
     * Identifier to a plain String -- moving the file from
     * dimensions/minecraft/overworld/data/globe/latitude_world_state.dat to
     * data/globe_latitude_world_state.dat -- and the off-disk reader kept the old literal, so it
     * found nothing on every world and every feature built on it went silently dead.
     */
    private static void offDiskStateReaderDerivesItsPathFromTheSavedDataId() throws Exception {
        String state = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeWorldState.java"));
        assertTrue(
                state.contains("public static final String STATE_ID = \"globe_latitude_world_state\""),
                "the SavedData id is a shared constant, not an inline literal");

        String reader = normalize(read(
                "src/main/java/com/example/globe/client/create/RecreatedWorldMetadata.java"));
        assertTrue(
                reader.contains("LatitudeWorldState.STATE_ID"),
                "the off-disk reader derives its filename from the SavedData id");
        assertFalse(
                reader.contains("\"dimensions\"") || reader.contains("latitude_world_state.dat"),
                "no re-spelled path literal can drift from the id again");
    }

    private static void frozenRiverVegetationIsScopedWithoutMutatingVanillaBiomes()
            throws Exception {
        String mod = normalize(read("src/main/java/com/example/globe/GlobeMod.java"));
        assertFalse(
                mod.contains("BiomeFeatureStripping"),
                "Latitude initialization must not mutate the global frozen-river biome definition");
        assertFalse(
                Files.exists(Path.of(
                        "src/main/java/com/example/globe/world/BiomeFeatureStripping.java")),
                "the obsolete global biome-modification implementation is removed");

        String scheduler = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorGenerateFeaturesBiomeSetMixin.java"));
        assertTrue(
                scheduler.contains("LatitudeWorldgenScope.isActive()")
                        && scheduler.contains("biome.is(Biomes.FROZEN_RIVER)")
                        && scheduler.contains("GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()")
                        && scheduler.contains("return Stream.empty();"),
                "frozen-river vegetal contributions are omitted only inside Latitude decoration scope");
        assertTrue(
                scheduler.contains("this::latitude$featuresForScopedIndex")
                        && scheduler.contains("filtered.set(vegetalStep, HolderSet.empty())")
                        && scheduler.contains("this.featuresPerStep = () -> expandedIndex;"),
                "Latitude rebuilds its feature index with only frozen-river vegetation omitted");

        String boundary = normalize(read(
                "src/main/java/com/example/globe/mixin/FrozenRiverVegetationGuardMixin.java"));
        assertTrue(
                boundary.contains("@Mixin(BiomeFilter.class)")
                        && boundary.contains("BiomeGenerationSettings;hasFeature(")
                        && boundary.contains("LatitudeWorldgenScope.isActive()")
                        && boundary.contains("GlobeMod.shouldApplyLatitudeWorldgen(noise)"),
                "neighbor-scheduled features are guarded at the exact scoped biome-filter path");
        assertTrue(
                boundary.contains("return foundInVegetal && !foundInOtherStep;"),
                "features shared with another generation step remain allowed like the old step-only removal");

        String mixins = normalize(read("src/main/resources/globe.mixins.json"));
        assertEquals(
                1,
                occurrences(mixins, "\"FrozenRiverVegetationGuardMixin\""),
                "the scoped frozen-river boundary guard is registered exactly once");
    }

    private static void generationScopeIsDimensionIsolatedAndNestSafe() {
        assertFalse(LatitudeWorldgenScope.isActive(), "scope starts inactive");
        try (LatitudeWorldgenScope.Scope overworld = LatitudeWorldgenScope.enter(true)) {
            assertTrue(LatitudeWorldgenScope.isActive(), "authorized overworld scope is active");
            try (LatitudeWorldgenScope.Scope nether = LatitudeWorldgenScope.enter(false)) {
                assertFalse(LatitudeWorldgenScope.isActive(), "nested non-overworld generation cannot inherit authority");
                try (LatitudeWorldgenScope.Scope nestedNether = LatitudeWorldgenScope.enter(false)) {
                    assertFalse(LatitudeWorldgenScope.isActive(), "nested inactive scope stays inactive");
                }
                assertFalse(LatitudeWorldgenScope.isActive(), "closing nested inactive scope restores inactive parent");
            }
            assertTrue(LatitudeWorldgenScope.isActive(), "closing non-overworld scope restores authorized outer scope");
        }
        assertFalse(LatitudeWorldgenScope.isActive(), "outer close removes all authority");
    }

    private static void generationScopeCleansUpOnFailureAndAcrossThreads() throws Exception {
        try {
            try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(true)) {
                assertTrue(LatitudeWorldgenScope.isActive(), "failing scope was entered");
                throw new ExpectedFailure();
            }
        } catch (ExpectedFailure expected) {
            // Expected: try-with-resources must still clear the authority frame.
        }
        assertFalse(LatitudeWorldgenScope.isActive(), "exceptional exit cannot leak authority");

        AtomicBoolean workerSawAuthority = new AtomicBoolean(true);
        try (LatitudeWorldgenScope.Scope ignored = LatitudeWorldgenScope.enter(true)) {
            Thread worker = new Thread(
                    () -> workerSawAuthority.set(LatitudeWorldgenScope.isActive()),
                    "latitude-authority-isolation-test");
            worker.start();
            worker.join();
            assertTrue(LatitudeWorldgenScope.isActive(), "worker inspection cannot disturb owner thread");
        }
        assertFalse(workerSawAuthority.get(), "authority is thread-local");
        assertFalse(LatitudeWorldgenScope.isActive(), "thread-isolation test cleans up owner scope");
    }

    private static void registeredHookIntegrationIsClosed() throws Exception {
        String mixinConfig = normalize(read("src/main/resources/globe.mixins.json"));
        assertFalse(
                mixinConfig.contains("BiomeNoSnowInWarmBandsMixin"),
                "unregistered biome helper must not be counted as proof");
        assertTrue(
                mixinConfig.contains("ChunkGeneratorWorldgenAuthorityMixin"),
                "generator authority wrapper is registered");
        assertTrue(
                mixinConfig.contains("NoiseChunkGeneratorWorldgenAuthorityMixin"),
                "noise-generator authority wrapper is registered");

        for (String file : new String[]{
                "ChunkRegionWarmSnowTrapMixin.java",
                "ProtoChunkSnowBlockGuardMixin.java",
                "AlpineSurfaceMixin.java",
                "SurfaceDripstoneLawnmowerMixin.java",
                "ExtremePolarVillageStartGuardMixin.java",
                "TreeLineVegetationGuardMixin.java",
                "NoiseChunkGeneratorCarveMixin.java",
                "ExtremePolarVegetationGuardMixin.java",
                "ExtremePolarSimpleFoliageGuardMixin.java"}) {
            String source = normalize(read("src/main/java/com/example/globe/mixin/" + file));
            assertTrue(
                    source.contains("LatitudeWorldgenScope.isActive()"),
                    file + " requires the current generator-owned scope");
        }

        String features = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorGenerateFeaturesBiomeSetMixin.java"));
        assertTrue(
                occurrences(features, "LatitudeWorldgenScope.isActive()") >= 2,
                "both feature-index and retainAll mutations fail open outside an authorized scope");

        String generatorScope = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorWorldgenAuthorityMixin.java"));
        String noiseScope = normalize(read(
                "src/main/java/com/example/globe/mixin/NoiseChunkGeneratorWorldgenAuthorityMixin.java"));
        assertTrue(
                occurrences(generatorScope, "try (LatitudeWorldgenScope.Scope") >= 2,
                "feature and structure paths close authority through try-with-resources");
        assertTrue(
                occurrences(noiseScope, "try (LatitudeWorldgenScope.Scope") >= 2,
                "surface and carver paths close authority through try-with-resources");
        assertTrue(
                occurrences(generatorScope + noiseScope, "Level.OVERWORLD") >= 3,
                "each dimension-bearing wrapper explicitly restricts authority to the overworld");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertApproximately(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0e-9) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNull(Object actual, String message) {
        if (actual != null) throw new AssertionError(message + ": expected null, actual=" + actual);
    }

    private static void assertNotNull(Object actual, String message) {
        if (actual == null) throw new AssertionError(message + ": expected non-null");
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
