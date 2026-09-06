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
        if (args.length == 1 && "polar-transition".equals(args[0])) {
            BiomeProviderSelectionPolicyTest.polarTaigaTransitionPreservesShouldersAndTreeLine();
            System.out.println("POLAR_TAIGA_TRANSITION_TEST_PASS");
            return;
        }
        if (args.length == 1 && "humid-coverage".equals(args[0])) {
            landCoveragePrecedesEveryFinalAdmissionAuthority();
            BiomeProviderSelectionPolicyTest.runHumidCoverageProof();
            System.out.println("HUMID_COVERAGE_PROOF_PASS");
            return;
        }
        if (args.length == 1 && "warm-upland".equals(args[0])) {
            BiomeProviderSelectionPolicyTest.runWarmUplandProof();
            finalSubtropicalUplandAuthorityIsShared();
            BiomeProviderSelectionPolicyTest.explainDisclosesTheAridHotspotOverride();
            System.out.println("WARM_UPLAND_PROOF_PASS");
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("expected no arguments, polar-transition, humid-coverage, or warm-upland");
        }
        contextLifecycleIsExplicitAndClearsAllAuthority();
        ordinarySettersDoNotActivateGlobalGuards();
        inlineGeneratorAuthorityIsBoundToTheExactOverworld();
        oldWorldStateDefaultsAreConservativeAndVanillaReadsAreNonCreating();
        biomeColumnCacheIsWorldBoundAndAvoidsDuplicateVerticalPicks();
        frozenRiverVegetationIsScopedWithoutMutatingVanillaBiomes();
        generationScopeIsDimensionIsolatedAndNestSafe();
        generationScopeCleansUpOnFailureAndAcrossThreads();
        landCoveragePrecedesEveryFinalAdmissionAuthority();
        finalAridLatitudeLawCannotBeBypassedByLateGates();
        paleGardenCoreClearanceCoversTheWholeWobblingCore();
        paleGardenCoreCannotOverwriteOceanBiomes();
        raisedTerrainCannotKeepAnOceanBiome();
        terrainBiomeCohesionUsesRealSurfaceEvidence();
        recreatedLatitudeWorldKeepsItsWorldTypeAndSize();
        coastalSwampUsesMangroveIdentity();
        onlyWetProvincesAdmitWetlandClaims();
        aridHotspotsAreWorldSizeStableAndPresentOnEverySeed();
        badlandsCountriesAreWorldSizeStableAndEarthlikeRare();
        warmDryProvinceShareMatchesTheEarthAnalogBelt();
        wetlandLocateFilterMatchesFinalIdentityLaw();
        biomeLocateServiceClaimsAllSupportedTargets();
        structureLocateUsesTheLatitudeBossBarSurface();
        woodlandMansionKeepsVanillaBiomeSiting();
        customSurfaceLocatePreviewUsesRegistryAuthority();
        profileAdoptionRefusesWorldsLatitudeDidNotGenerate();
        offDiskStateReaderDerivesItsPathFromTheSavedDataId();
        terraBlenderSweepKeepsLatitudeSurfaceIdentity();
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
        assertTrue(
                TerrainBiomeCohesionPolicy.shouldUseWarmUplandFamily(
                        true, highTerrain, 0, seaLevel),
                "a real measured warm/subtropical surface at the high-terrain threshold is physical upland");
        assertFalse(
                TerrainBiomeCohesionPolicy.shouldUseWarmUplandFamily(
                        true, seaLevel + 2, 0, seaLevel),
                "ordinary flat warm ground still keeps its lowland family");
        VanillaCoverageFinalAdmissionPolicy.Facts subtropicalUplandFacts =
                new VanillaCoverageFinalAdmissionPolicy.Facts(
                        VanillaCoverageFinalAdmissionPolicy.LatitudeZone.SUBTROPICAL,
                        VanillaCoverageFinalAdmissionPolicy.LatitudeZone.SUBTROPICAL,
                        ProvinceAuthority.Province.WARM_DRY,
                        VanillaCoverageFinalAdmissionPolicy.PhysicalTerrain.UPLAND,
                        false,
                        false,
                        true,
                        false);
        assertSame(
                VanillaCoverageFinalAdmissionPolicy.Decision.DEFER_TO_PHYSICAL_TERRAIN,
                VanillaCoverageFinalAdmissionPolicy.decide(
                        BiomeDescriptorLedger.descriptor("minecraft:desert"),
                        BiomeRoute.ARID_LOWLAND,
                        subtropicalUplandFacts),
                "an arid lowland reservation defers to measured subtropical upland terrain");
        assertSame(
                VanillaCoverageFinalAdmissionPolicy.Decision.PRESERVE_EXACT,
                VanillaCoverageFinalAdmissionPolicy.decide(
                        BiomeDescriptorLedger.descriptor("clifftree:desert_cliff"),
                        BiomeRoute.ARID_UPLAND,
                        subtropicalUplandFacts),
                "a descriptor-owned arid upland reservation survives the same measured terrain");
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
                "both picker paths derive non-reentrant terrain evidence under the worldgen fast path");
        assertTrue(
                source.contains(
                        "if (\"MIXIN\".equals(normalized) || \"CAVE_CLAMP\".equals(normalized)) { return true; }"),
                "MIXIN and CAVE_CLAMP unconditionally skip generator-reentrant terrain previews");
        String onDemandGate = methodBody(
                source, "private static PreviewTerrain onDemandGateTerrain(", "on-demand gate terrain");
        String nonReentrantEvidence = methodBody(
                source, "private static PreviewTerrain nonReentrantTerrainEvidence(",
                "non-reentrant terrain evidence");
        for (String criticalPath : new String[]{onDemandGate, nonReentrantEvidence}) {
            assertFalse(
                    criticalPath.contains("previewTerrain(")
                            || criticalPath.contains("previewHeight(")
                            || criticalPath.contains("getBaseHeight("),
                    "live on-demand terrain classification cannot re-enter the chunk generator");
        }
        assertTrue(
                onDemandGate.contains("nonReentrantTerrainEvidence("),
                "live terrain classification reuses cached height plus sampler relief");
        assertTrue(
                nonReentrantEvidence.contains("new PreviewTerrain(")
                        && nonReentrantEvidence.contains(
                                "ruggedNoiseLike ? TerrainBiomeCohesionPolicy.RUGGED_RELIEF_BLOCKS : 0"),
                "the terminal live evidence contains only cached height and the sampler ruggedness proxy");
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
                "sunflower plains remains classified with the plains family");
        assertFinalAdmissionPhysicalTerrainWiring(registryPicker(source), "registry");
        assertFinalAdmissionPhysicalTerrainWiring(collectionPicker(source), "collection");
    }

    private static String registryPicker(String source) {
        return pickerBody(
                source,
                "public static Holder<Biome> pick(Registry<Biome> biomeRegistry",
                "public static Holder<Biome> pick(Collection<Holder<Biome>> biomePool");
    }

    private static String collectionPicker(String source) {
        return pickerBody(
                source,
                "public static Holder<Biome> pick(Collection<Holder<Biome>> biomePool",
                "private static Holder<Biome> applyFinalAridLatitudeLaw(");
    }

    private static void assertFinalAdmissionPhysicalTerrainWiring(String picker, String label) {
        // polarMountainNoiseLike was renamed rawMountainTruth: the value is the raw, ungated
        // isMountainLike sample, and calling it "polar" hid that the windswept legality gate and the
        // late ownership veto both read it outside the polar band. Same definition, same coordinates.
        String physicalUpland = "boolean finalAdmissionPhysicalUpland = rawMountainTruth || "
                + "TerrainBiomeCohesionPolicy.shouldUseWarmUplandFamily( gateEvidence, gateHeight, gateDelta, seaLevel);";
        assertEquals(
                1,
                occurrences(picker, "boolean finalAdmissionPhysicalUpland ="),
                label + " picker computes one shared V5 final-admission physical-upland fact");
        assertTrue(
                picker.contains(physicalUpland),
                label + " picker derives V5 physical upland from climate mountain or measured gate evidence");
        assertEquals(
                2,
                occurrences(picker, "sampler, finalAdmissionPhysicalUpland)"),
                label + " picker passes the same physical-upland fact to V5 coverage and exact-admission facts");
    }

    private static void finalSubtropicalUplandAuthorityIsShared() throws Exception {
        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        String registryPicker = registryPicker(source);
        String collectionPicker = collectionPicker(source);
        assertFinalSubtropicalUplandAuthorityOrder(registryPicker, "registry");
        assertFinalSubtropicalUplandAuthorityOrder(collectionPicker, "collection");
    }

    private static void assertFinalSubtropicalUplandAuthorityOrder(String picker, String label) {
        String authority = "out = enforceFinalSubtropicalUplandAuthority(";
        int wetlandIdentity = picker.indexOf("out = applyFinalWetlandIdentityLaw(");
        int authorityAt = picker.indexOf(authority);
        int legacy = picker.indexOf("if (ACTIVE_WORLDGEN_POLICY != "
                + "WorldgenPolicyVersion.PROVIDER_TICKET_V5_FINAL_ADMISSION)");
        assertEquals(1, occurrences(picker, authority),
                label + " picker applies the one shared V5 subtropical physical-upland authority");
        assertTrue(wetlandIdentity >= 0 && authorityAt > wetlandIdentity && legacy > authorityAt,
                label + " picker applies subtropical physical-upland admission after wetland identity "
                        + "and before the V4-and-older legacy coverage tail");
    }

    private static void landCoveragePrecedesEveryFinalAdmissionAuthority() throws Exception {
        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        String registryPicker = pickerBody(
                source,
                "public static Holder<Biome> pick(Registry<Biome> biomeRegistry",
                "public static Holder<Biome> pick(Collection<Holder<Biome>> biomePool");
        String collectionPicker = pickerBody(
                source,
                "public static Holder<Biome> pick(Collection<Holder<Biome>> biomePool",
                "private static Holder<Biome> applyFinalAridLatitudeLaw(");

        assertV5FinalLandCoverageOrder(registryPicker, "registry");
        assertV5FinalLandCoverageOrder(collectionPicker, "collection");
        assertCoverageAdmissionOwnerIsShared(source);
        assertEquals(
                2,
                occurrences(source, "if (hasExactTemperateLowlandCoverageAdmission(out)) {"),
                "both final taiga gates preserve an exact TEMPERATE_LOWLAND coverage identity");
        // The late wetland authorities are the only post-jungle-gate steps that re-pick from band
        // pools; without warm-province enforcement their tropical re-picks re-mint jungle-family
        // after every province gate has already run.
        //
        // The trailing blockX/blockZ are load-bearing, not plumbing: since 2026-08-18 the enforcer's
        // WARM_MEDIUM arm asks the savanna country and the dry fringe where the column IS before it
        // answers, so an overload that cannot see the column cannot answer correctly.
        assertEquals(
                2,
                occurrences(source, "out = enforceWarmProvinceFamily(biomes,"
                        + " pickSwampFallback(biomes, base, blockX, blockZ, t, bandIndex),"
                        + " warmProvinceClass(blockX, blockZ, bandIndex), blockX, blockZ);"),
                "both late swamp fallbacks honor the warm-province family");
        assertEquals(
                2,
                occurrences(source, "out = enforceWarmProvinceFamily(biomes,"
                        + " pickMangroveFallback(biomes, base, blockX, blockZ, t, bandIndex),"
                        + " warmProvinceClass(blockX, blockZ, bandIndex), blockX, blockZ);"),
                "both late mangrove fallbacks honor the warm-province family");
        assertEquals(
                2,
                occurrences(source, "return enforceWarmProvinceFamily(biomes, fallback,"
                        + " warmProvinceClass(blockX, blockZ, landBandIndex), blockX, blockZ);"),
                "both final wetland authorities honor the warm-province family");
    }

    private static String pickerBody(String source, String signature, String nextSignature) {
        int wrapper = source.indexOf(signature);
        int implementation = source.indexOf(signature, wrapper + signature.length());
        int end = source.indexOf(nextSignature, implementation + signature.length());
        if (wrapper < 0 || implementation < 0 || end < 0) {
            throw new AssertionError("missing picker boundary for " + signature);
        }
        return source.substring(implementation, end);
    }

    private static void assertV5FinalLandCoverageOrder(String picker, String label) {
        String coverage = "out = applyVanillaCoverage(";
        String v5 = "WorldgenPolicyVersion.PROVIDER_TICKET_V5_FINAL_ADMISSION";
        String v5Guard = "if (ACTIVE_WORLDGEN_POLICY == " + v5 + ")";
        String legacyGuard = "if (ACTIVE_WORLDGEN_POLICY != " + v5 + ")";
        String cherryGuard = "if (ACTIVE_WORLDGEN_POLICY != " + v5
                + " && isBiomeId(chosen, \"minecraft:cherry_grove\")";
        int v5At = picker.indexOf(v5Guard);
        int cherryAt = picker.indexOf(cherryGuard);
        assertTrue(v5At >= 0,
                label + " picker has an explicit V5 final-admission branch");
        assertTrue(cherryAt >= 0,
                label + " picker preserves the V4-and-older Cherry Grove shortcut behind a non-V5 guard");
        String v5Admission = conditionalBody(picker, v5At, label + " V5 final-admission branch");
        assertEquals(1, occurrences(v5Admission, coverage),
                label + " V5 branch applies vanilla coverage exactly once");

        int savanna = picker.indexOf("out = applyFinalSavannaClimateClamp(");
        int coverageAt = picker.indexOf(coverage, v5At);
        int preClamp = picker.indexOf("Holder<Biome> preClampOut = out;");
        int polar = picker.indexOf("out = clampFinalPolarNonMountainAlpineOutput(", preClamp);
        int jungle = picker.indexOf("out = gateWarmJungleSurvival(", polar);
        int sparse = picker.indexOf("out = gateWarmWetSparseJungleSurvival(", jungle);
        int dry = picker.indexOf("out = gateDryWarmIdentity(", sparse);
        int taiga = picker.indexOf("out = gatePolarTaigaSurvival(", dry);
        int wetland = picker.indexOf("out = clampLateWetlandSurvival(", taiga);
        int arid = picker.indexOf("out = applyFinalAridLatitudeLaw(", wetland);
        int wetlandAuthority = picker.indexOf("out = enforceFinalWetlandAuthority(", arid);
        int wetlandIdentity = picker.indexOf("out = applyFinalWetlandIdentityLaw(", wetlandAuthority);
        int physicalTerrain = picker.indexOf(
                "TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland(", wetlandIdentity);
        int legacyAt = picker.indexOf(legacyGuard, physicalTerrain);
        assertTrue(legacyAt >= 0,
                label + " picker keeps V4 and older worlds on their explicitly separate late legacy path");
        String legacyAdmission = conditionalBody(picker, legacyAt, label + " late legacy final-admission branch");
        assertEquals(1, occurrences(legacyAdmission, coverage),
                label + " V4-and-older branch retains exactly one late vanilla-coverage application");

        assertTrue(cherryAt < v5At && cherryAt < preClamp,
                label + " V4-and-older Cherry Grove shortcut remains before V5 final admission");
        assertTrue(savanna >= 0 && savanna < coverageAt && coverageAt < preClamp,
                label + " V5 picker completes ordinary warm composition before its early land admission");
        assertTrue(preClamp < polar,
                label + " V5 picker applies land coverage before every final admission authority");
        assertTrue(polar < jungle && jungle < sparse && sparse < dry && dry < taiga
                        && taiga < wetland && wetland < arid && arid < wetlandAuthority
                        && wetlandAuthority < wetlandIdentity && wetlandIdentity < physicalTerrain,
                label + " picker preserves the shared polar/humidity/taiga/wetland/arid/terrain order");
        int legacyCoverageAt = legacyAdmission.indexOf(coverage);
        assertTrue(legacyCoverageAt >= 0,
                label + " legacy V4-and-older branch retains its late land coverage application");
        assertTrue(legacyAt > physicalTerrain,
                label + " legacy V4-and-older land coverage remains at the committed return tail");
    }

    private static void assertCoverageAdmissionOwnerIsShared(String source) {
        String policy = "VanillaCoverageFinalAdmissionPolicy";
        String registryCoverage = methodBody(
                source,
                "private static Holder<Biome> applyVanillaCoverage( Registry<Biome> biomes,",
                "registry applyVanillaCoverage");
        String collectionCoverage = methodBody(
                source,
                "private static Holder<Biome> applyVanillaCoverage( Collection<Holder<Biome>> biomes,",
                "collection applyVanillaCoverage");
        assertTrue(registryCoverage.contains(policy),
                "registry applyVanillaCoverage consults the shared final-admission policy");
        assertTrue(collectionCoverage.contains(policy),
                "collection applyVanillaCoverage consults the shared final-admission policy");

        int plannerStart = source.indexOf("boolean exactV2 =");
        int plannerEnd = source.indexOf("if (ACTIVE_VANILLA_COVERAGE_PLAN != null", plannerStart);
        assertTrue(plannerStart >= 0 && plannerEnd > plannerStart,
                "missing vanilla-coverage planner eligibility boundary");
        assertTrue(source.substring(plannerStart, plannerEnd).contains(policy),
                "V5 vanilla-coverage planner eligibility consults the shared final-admission policy");
    }

    private static String methodBody(String source, String signature, String label) {
        int methodAt = source.indexOf(signature);
        if (methodAt < 0) throw new AssertionError("missing " + label + " method");
        return conditionalBody(source, methodAt, label + " method");
    }

    private static String conditionalBody(String source, int conditionalAt, String label) {
        int openBrace = source.indexOf('{', conditionalAt);
        if (openBrace < 0) throw new AssertionError("missing opening brace for " + label);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            if (current == '}' && --depth == 0) return source.substring(openBrace, index + 1);
        }
        throw new AssertionError("missing closing brace for " + label);
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
                service.contains("latitudeSource, worldRadius, searchRadius, randomState.sampler()"),
                "the live service must pass the exact world and search radii into its job");
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
                occurrences(source, "LatitudeBiomes.isPotentialDirectMangroveLocateCandidate(") >= 1
                        && source.contains("isPotentialWetlandLocateSourceCandidate( quartX, quartY, quartZ, sampler)"),
                "the direct locator must retain the same direct-mangrove and shoreline-promotable candidates as the tick job");
        int directWetlandCandidate = source.indexOf(
                "LatitudeBiomes.isPotentialWetlandLocateCandidate( blockX, blockZ, borderRadiusBlocks");
        int directWetlandExit = source.indexOf("if (wetlandOnlyTarget) {", directWetlandCandidate);
        assertTrue(
                directWetlandCandidate >= 0 && directWetlandExit > directWetlandCandidate,
                "direct wetland locate may stop only after the complete shared broad phase");
        assertTrue(
                service.contains("tickExactProbes < LatitudeLocateBudgetPolicy.MAX_WETLAND_EXACT_PROBES_PER_TICK")
                        && service.contains("tickExactProbes++;"),
                "the live scheduler must enforce and consume the one-exact-probe tick budget");
    }

    private static void biomeLocateServiceClaimsAllSupportedTargets() throws Exception {
        String service = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomeLocateService.java"));
        String mixin = normalize(read(
                "src/main/java/com/example/globe/mixin/LocateCommandMixin.java"));

        assertTrue(
                mixin.contains("LatitudeBiomeLocateService.beginIfLatitudeBiome(source, target)"),
                "the command hook must route all supported Latitude biome requests into the progress worker");
        assertTrue(
                service.contains("else if (!includesCave) { job = new SurfaceLocateJob(")
                        && service.contains("else { job = new ThreeDimensionalLocateJob("),
                "surface, cave, and mixed biome targets must all receive bounded tick-sliced routes");
        assertTrue(
                service.contains("ServerPlayConnectionEvents.DISCONNECT.register(")
                        && service.contains("ServerLifecycleEvents.SERVER_STOPPED.register(server -> cancel(server, null))"),
                "disconnect and server-stop cancellation must reach the active locate job");
        assertTrue(
                occurrences(service, "bossBar.removeAllPlayers()") >= 3,
                "success, failure, and cancellation must all clear the locate boss bar");
        assertTrue(
                service.contains("LatitudeLocateBudgetPolicy.MAX_SURFACE_PREVIEW_PROBES_PER_TICK")
                        && service.contains("LatitudeLocateBudgetPolicy.MAX_THREE_DIMENSIONAL_EXACT_PROBES_PER_TICK"),
                "general biome routes must remain explicitly bounded per tick");
        assertTrue(
                service.contains("LatitudeLocateBudgetPolicy.fullWorldSearchRadius(")
                        && service.contains("isWithinLatitudeWorld(blockX, blockZ)"),
                "a boss-bar locate must cover the playable Latitude world without reporting outside it");
        String biomeSource = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        assertTrue(
                occurrences(biomeSource, "findPlannedCaveCoverage(") >= 2
                        && biomeSource.contains("Holder<Biome> exact = getNoiseBiome(quartX, quartY, quartZ, sampler)")
                        && biomeSource.contains("anchor.biomeId().equals(LatitudeBiomes.biomeIdPublic(exact))"),
                "a direct cave locate miss may use a planned anchor only after final-output identity verification");
        assertTrue(
                service.contains("latitudeSource.findPlannedCaveCoverage( matching, origin, searchRadius, target, sampler)"),
                "the tick-sliced cave route shares the same verified planned fallback");
    }

    private static void customSurfaceLocatePreviewUsesRegistryAuthority() throws Exception {
        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        int methodStart = source.indexOf("Holder<Biome> getLocatePreviewNoiseBiome(");
        int methodEnd = source.indexOf("private static boolean shouldPreserveCave", methodStart);
        assertTrue(methodStart >= 0 && methodEnd > methodStart,
                "the custom surface locate preview must remain a distinct, reviewable helper");
        String preview = source.substring(methodStart, methodEnd);

        assertTrue(
                preview.contains("if (biomeRegistry != null) { return LatitudeBiomes.pick( biomeRegistry, base"),
                "an admitted custom surface target must preview through Latitude's existing registry authority");
        assertTrue(
                preview.indexOf("if (biomeRegistry != null)")
                        < preview.indexOf("Collection<Holder<Biome>> sourceCandidates"),
                "the donor-only preview fallback must remain unreachable when the locate registry is available");
    }

    private static void structureLocateUsesTheLatitudeBossBarSurface() throws Exception {
        String service = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeStructureLocateService.java"));
        assertTrue(
                service.contains("new ServerBossEvent(")
                        && service.contains("BossEvent.BossBarColor.BLUE")
                        && service.contains("bossBar.removeAllPlayers()"),
                "Latitude structure searches must use the same removable blue boss-bar surface as biomes");
        assertTrue(
                !service.contains("commands.latitude.locate.structure.searching"),
                "Latitude structure searches must not retain a separate legacy chat acknowledgement");

        // The answer must be the generated structure's own settled footprint, not the placement
        // cell's min corner at getLocatePos()'s hardcoded Y=0.
        assertTrue(
                service.contains("BlockPos center = start.getBoundingBox().getCenter()")
                        && service.contains("start.getBoundingBox().maxY() + 2")
                        && service.contains("ringBest = Pair.of(generatedTarget, candidate.holder())")
                        && !service.contains("getLocatePos(candidateChunk)"),
                "a structure locate must report the generated start's center, lifted above its top");

        // The coordinate is clickable through a player-bound, expiring, single-use token rather
        // than a raw /tp, and the teleport can never descend below the reported top.
        String tools = normalize(read(
                "src/main/java/com/example/globe/tools/LatitudeToolsCommand.java"));
        assertTrue(
                service.contains("showTeleportLocateResult(source, target, origin, result)")
                        && service.contains("new ClickEvent(ClickEvent.Action.RUN_COMMAND, \"/latitude_locate_teleport \" + token)")
                        && service.contains("pending == null || !pending.token().equals(token)")
                        && service.contains("Util.getMillis() > pending.expiresAtMs()")
                        && service.contains("serverTeleports.remove(player.getUUID())")
                        && !service.contains("new ClickEvent(ClickEvent.Action.RUN_COMMAND, \"/tp ")
                        && service.contains("\"commands.locate.structure.not_found\""),
                "the coordinate click must use a player-bound, expiring, single-use action");

        // SAFE LANDING. The previous pin here asserted the literal
        // Math.max(player.getY(), pending.minimumY()) -- which proved the formula existed, not that
        // it was safe, and it was not: a buried pyramid's top-plus-two sits inside the dune above it
        // (arrive encased in sand), and a player who happened to be flying kept that altitude and
        // fell. Pin the two properties that actually make the landing safe, so restoring either
        // hazard fails here.
        assertTrue(
                service.contains("Heightmap.Types.MOTION_BLOCKING_NO_LEAVES")
                        && service.contains("Math.max(surfaceY, pending.minimumY())"),
                "the locate teleport must land on the surface column, never at a computed altitude");
        assertFalse(
                service.contains("Math.max(player.getY(), pending.minimumY())"),
                "the locate teleport must not preserve the player's own altitude -- that is the "
                        + "long-fall hazard this landing rule replaced");
        assertTrue(
                tools.contains("Commands.literal(\"latitude_locate_teleport\")")
                        && tools.contains("LatitudeStructureLocateService.runPendingTeleport("),
                "the warning-free locate action must be registered only inside the audited shipping command surface");
        assertTrue(
                service.contains("PENDING_TELEPORTS.remove(server)")
                        && service.contains("clearPendingTeleport(server, handler.player)"),
                "disconnect and server stop must clear outstanding locate teleport tokens");
        assertTrue(
                service.contains("Centered above the generated desert pyramid")
                        && service.contains("Desert pyramids may be buried"),
                "a buried desert pyramid must say so instead of reading as an empty destination");
    }

    /**
     * A woodland mansion must come to rest in a vanilla biome. A third-party pack can add its own
     * biome to the mansion's very small biome tag; Latitude never classified that column, so the
     * mansion lands somewhere that reads as a placement bug. The rule lives in the shared siting
     * policy so the generation guard and the locate evaluator cannot drift apart.
     */
    private static void woodlandMansionKeepsVanillaBiomeSiting() throws Exception {
        String policy = normalize(read(
                "src/main/java/com/example/globe/world/StructureSitingPolicy.java"));
        String guard = normalize(read(
                "src/main/java/com/example/globe/mixin/ExtremePolarVillageStartGuardMixin.java"));
        String service = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeStructureLocateService.java"));

        assertTrue(
                policy.contains("\"minecraft\".equals(structureNamespace) && \"mansion\".equals(structurePath)")
                        && !policy.contains("\"woodland_mansion\".equals(structurePath)"),
                "the mansion rule must name the structure id minecraft:mansion, not its biome tag");
        assertTrue(
                policy.contains("&& !\"minecraft\".equals(biomeNamespace)"),
                "the mansion rule must refuse by biome namespace so every vanilla mansion biome stays legal");
        assertTrue(
                guard.contains("StructureSitingPolicy.requiresVanillaBiomeSiting(")
                        && guard.contains("StructureSitingPolicy.shouldRejectCustomBiomeSiting(")
                        && guard.contains("return StructureStart.INVALID_START;"),
                "real generation must refuse a mansion whose settled center is a custom biome");
        assertTrue(
                service.contains("StructureSitingPolicy.requiresVanillaBiomeSiting(")
                        && service.contains("CandidateVerdict.REJECTED_CUSTOM_BIOME_SITING"),
                "locate must mirror the mansion rule so it never reports a mansion the guard refuses");
    }

    /** Wetland claims require a genuinely wet province; mangrove remains separately governed. */
    private static void onlyWetProvincesAdmitWetlandClaims() throws Exception {
        int radius = 10_000;
        ProvinceAuthority probe = new ProvinceAuthority(7L, radius);
        int[] warmDry = findProvinceColumn(probe, ProvinceAuthority.Province.WARM_DRY, radius);
        int[] coldDry = findProvinceColumn(probe, ProvinceAuthority.Province.COLD_DRY, radius);
        int[] warmMedium = findProvinceColumn(probe, ProvinceAuthority.Province.WARM_MEDIUM, radius);
        int[] coldMedium = findProvinceColumn(probe, ProvinceAuthority.Province.COLD_MEDIUM, radius);
        int[] warmWet = findProvinceColumn(probe, ProvinceAuthority.Province.WARM_WET, radius);
        int[] coldWet = findProvinceColumn(probe, ProvinceAuthority.Province.COLD_WET, radius);

        ProvinceAuthority original = LatitudeBiomes.swapProvinceAuthorityForTest(probe);
        try {
            assertFalse(LatitudeBiomes.wetlandProvinceEligible(warmDry[0], warmDry[1]),
                    "an explicit WARM_DRY province refuses wetland admission");
            assertFalse(LatitudeBiomes.wetlandProvinceEligible(coldDry[0], coldDry[1]),
                    "an explicit COLD_DRY province refuses wetland admission");
            assertFalse(LatitudeBiomes.wetlandProvinceEligible(warmMedium[0], warmMedium[1]),
                    "WARM_MEDIUM lacks the moisture required for wetland admission");
            assertFalse(LatitudeBiomes.wetlandProvinceEligible(coldMedium[0], coldMedium[1]),
                    "COLD_MEDIUM lacks the moisture required for wetland admission");
            assertTrue(LatitudeBiomes.wetlandProvinceEligible(warmWet[0], warmWet[1]),
                    "a WARM_WET province keeps its wetland eligibility");
            assertTrue(LatitudeBiomes.wetlandProvinceEligible(coldWet[0], coldWet[1]),
                    "a COLD_WET province keeps its wetland eligibility");
        } finally {
            LatitudeBiomes.restoreProvinceAuthorityForTest(original);
        }

        original = LatitudeBiomes.swapProvinceAuthorityForTest(null);
        try {
            assertTrue(LatitudeBiomes.wetlandProvinceEligible(warmDry[0], warmDry[1]),
                    "a missing/uninitialized authority keeps the intended compatibility behavior");
        } finally {
            LatitudeBiomes.restoreProvinceAuthorityForTest(original);
        }

        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertEquals(7, occurrences(source, "&& wetlandProvinceEligible(blockX, blockZ)"),
                "all wetland planners, facts, final authorities, and locate share the wet-province law");
        assertTrue(source.contains("|| !wetlandProvinceEligible(blockX, blockZ)"),
                "the coverage guard's wetland protection yields at dry-province columns");
        assertTrue(source.contains("!paleGardenCoreAuthorityHit( WORLD_SEED, x, z"),
                "V5 coverage planning refuses anchors owned by the stronger Pale Garden core");
        assertTrue(source.contains(
                        "case TEMPERATE_WETLAND -> band == BAND_TEMPERATE && !mountain"
                                + " && wetlandProvinceEligible(blockX, blockZ)"),
                "the temperate wetland route arm shares the final wet-province law");
        assertTrue(source.contains(
                        "case SUBPOLAR_WETLAND -> band == BAND_SUBPOLAR && !mountain"
                                + " && wetlandProvinceEligible(blockX, blockZ)"),
                "the subpolar wetland route arm shares the final wet-province law");
    }

    /**
     * The desert-oasis mechanism must exist on every seed and at every world size (maintainer
     * ruling, 2026-08-16, desert-abundance lever 1). The census proved the previous behavior:
     * the hotspot noise scale grew with the radius, so the field always had ~3 cells across the
     * whole map and two of three measured seeds had literally ZERO hotspot area — the ruled-in
     * oasis exception never fired. This pins the repaired geometry: the scale is capped, every
     * seed in a fixed list carries at least one hotspot, and a flood ceiling keeps the tuning
     * honest in both directions.
     */
    private static void aridHotspotsAreWorldSizeStableAndPresentOnEverySeed() {
        assertEquals(2250, LatitudeBiomes.aridHotspotScaleBlocks(3_750),
                "small worlds keep their proportional hotspot scale");
        assertEquals(3000, LatitudeBiomes.aridHotspotScaleBlocks(5_000),
                "the proportional scale still applies below the cap");
        assertEquals(3072, LatitudeBiomes.aridHotspotScaleBlocks(10_000),
                "regular worlds hit the cap, giving a world-size-stable cell count");
        assertEquals(3072, LatitudeBiomes.aridHotspotScaleBlocks(20_000),
                "massive worlds hit the same cap instead of scaling the cells up with the map");

        for (int radius : new int[] {10_000, 20_000}) {
            double aggregate = 0.0;
            for (long seed = 41L; seed <= 52L; seed++) {
                int samples = 0;
                int hits = 0;
                for (int blockZ = -radius; blockZ < radius; blockZ += 250) {
                    for (int blockX = -radius; blockX < radius; blockX += 250) {
                        samples++;
                        if (LatitudeBiomes.aridHotspotHere(seed, radius, blockX, blockZ)) {
                            hits++;
                        }
                    }
                }
                double fraction = (double) hits / samples;
                aggregate += fraction;
                assertTrue(hits >= 1,
                        "seed " + seed + " at radius " + radius + " carries at least one arid "
                                + "hotspot — a seed with zero oases leaves the desert-oasis "
                                + "ruling inert");
                assertTrue(fraction <= 0.08,
                        "seed " + seed + " at radius " + radius + " stays under the hotspot "
                                + "flood ceiling (got " + fraction + ")");
            }
            double mean = aggregate / 12.0;
            assertTrue(mean >= 0.004 && mean <= 0.05,
                    "mean hotspot area at radius " + radius + " stays in the oasis band "
                            + "(got " + mean + ")");
        }
    }

    /**
     * The badlands country is a capped, world-size-stable field, not a per-seed coin flip
     * (maintainer ruling, 2026-08-19: badlands is an earthlike-rare accent of the dry belt and
     * desert is the staple). The positive control mirrors the arid-hotspot one directly above,
     * because the defect was the same one: a noise scale that grew WITH the world radius, so the
     * map held ~7 primary cells at any size and the whole belt was decided by a handful of lattice
     * draws. Measured on this line before the cap, as a share of the geometric 23.5-35 degree belt
     * over these same twelve seeds, radius 10000 swung 24%-71% -- a seed lottery that on the
     * heavy rolls squeezed desert, the ruled staple, down to a sliver.
     *
     * <p>SCOPE, and it is deliberately narrow. This asserts the NOISE FIELD's own coverage of a
     * geometric latitude band, which is arithmetic every Latitude line shares: identical
     * ValueNoise2D, identical band boundaries, identical latitude-to-z mapping. It says nothing
     * about badlands' share of subtropical LAND, and it must not be widened into such a claim
     * here. This line carries a subtropical dry-belt widening in ProvinceAuthority that 26.2 has
     * no equivalent of, so the same country coverage lands on a different amount of ground; the
     * in-world share is a question only this line's own atlas census can answer. The numbers below
     * were re-measured against this line's arithmetic, not quoted from the other generator.
     */
    private static void badlandsCountriesAreWorldSizeStableAndEarthlikeRare() {
        assertEquals(512, LatitudeBiomes.badlandsCountryScaleBlocks(3_000),
                "small worlds keep the coherence floor");
        assertEquals(600, LatitudeBiomes.badlandsCountryScaleBlocks(3_750),
                "the proportional scale still applies below the cap");
        assertEquals(640, LatitudeBiomes.badlandsCountryScaleBlocks(10_000),
                "regular worlds hit the cap, giving a world-size-stable cell count");
        assertEquals(640, LatitudeBiomes.badlandsCountryScaleBlocks(20_000),
                "massive worlds hit the same cap instead of scaling the countries up with the map");

        for (int radius : new int[] {10_000, 20_000}) {
            int dryLowAbsZ = com.example.globe.util.LatitudeMath.zForLatitudeDeg(23.5, radius);
            int dryHighAbsZ = com.example.globe.util.LatitudeMath.zForLatitudeDeg(35.0, radius);
            double aggregate = 0.0;
            for (long seed = 41L; seed <= 52L; seed++) {
                int samples = 0;
                int hits = 0;
                for (int blockZ = dryLowAbsZ; blockZ < dryHighAbsZ; blockZ += 64) {
                    for (int blockX = -radius; blockX < radius; blockX += 64) {
                        samples++;
                        if (LatitudeBiomes.badlandsCountryNoiseHit(seed, radius, blockX, blockZ)) {
                            hits++;
                        }
                    }
                }
                double fraction = (double) hits / samples;
                aggregate += fraction;
                assertTrue(hits >= 1,
                        "seed " + seed + " at radius " + radius + " carries at least one badlands "
                                + "country — a seed with zero badlands starves the biome");
                assertTrue(fraction <= 0.35,
                        "seed " + seed + " at radius " + radius + " stays under the badlands "
                                + "flood ceiling (got " + fraction + ") — the pre-fix lottery let "
                                + "single seeds hand badlands most of the dry belt");
            }
            double mean = aggregate / 12.0;
            assertTrue(mean >= 0.08 && mean <= 0.22,
                    "mean badlands-country coverage of the dry belt at radius " + radius
                            + " stays in the earthlike-rare band (got " + mean + ")");
        }
    }

    /**
     * The subtropical dry belt carries an Earth-analog share of WARM_DRY (maintainer ruling,
     * 2026-08-16, desert-abundance lever 2). The census measured WARM_DRY at 6.06% of the world
     * — a ~23% share of the subtropical band against WARM_MEDIUM's dominance — because the
     * composite moisture signal (two averaged noises) concentrates around 0.5 and the 0.38
     * threshold only caught its tail. Desert itself is healthy INSIDE WARM_DRY (~17%); the
     * province was simply rare. This pins the widened belt, and pins two older rulings so this
     * lever cannot disturb them: the deep tropics stay humid (the 1.4 wet-bias law), and the
     * WARM_WET share is untouched (the jungle belt is not this lever's business).
     */
    private static void warmDryProvinceShareMatchesTheEarthAnalogBelt() {
        int radius = 10_000;
        double dryAggregate = 0.0;
        double wetAggregate = 0.0;
        double tropicalDryAggregate = 0.0;
        for (long seed = 41L; seed <= 52L; seed++) {
            ProvinceAuthority authority = new ProvinceAuthority(seed, radius);
            int subtropical = 0;
            int subtropicalDry = 0;
            int subtropicalWet = 0;
            int tropical = 0;
            int tropicalDry = 0;
            for (int blockZ = -radius; blockZ < radius; blockZ += 250) {
                for (int blockX = -radius; blockX < radius; blockX += 250) {
                    int band = LatitudeBiomes.authoritativeLandBandIndex(blockX, blockZ, radius);
                    if (band > 1) {
                        continue;
                    }
                    ProvinceAuthority.Province province = authority.classify(blockX, blockZ);
                    if (band == 1) {
                        subtropical++;
                        if (province == ProvinceAuthority.Province.WARM_DRY) {
                            subtropicalDry++;
                        } else if (province == ProvinceAuthority.Province.WARM_WET) {
                            subtropicalWet++;
                        }
                    } else {
                        tropical++;
                        if (province == ProvinceAuthority.Province.WARM_DRY) {
                            tropicalDry++;
                        }
                    }
                }
            }
            double dryShare = (double) subtropicalDry / Math.max(1, subtropical);
            double wetShare = (double) subtropicalWet / Math.max(1, subtropical);
            double tropicalDryShare = (double) tropicalDry / Math.max(1, tropical);
            dryAggregate += dryShare;
            wetAggregate += wetShare;
            tropicalDryAggregate += tropicalDryShare;
            assertTrue(dryShare >= 0.20,
                    "seed " + seed + " keeps a real subtropical dry belt (got " + dryShare + ")");
            // Ceiling raised 0.55 -> 0.57 with the mesic equatorward clamp (2026-08-24): the
            // clamp returns the 34.5-35.5deg ring — previously leaking to TEMPERATE picks via
            // jitter+blend+warp — to subtropical band accounting. ProvinceAuthority.classify is
            // untouched, so the WARM_DRY province itself did not grow; the band DENOMINATOR now
            // honestly includes the dry belt's own poleward edge, which lifts the worst-seed
            // share to 0.559. The monoculture law still binds: the ceiling stays a hard cap.
            assertTrue(dryShare <= 0.57,
                    "seed " + seed + " does not flip the subtropics into a desert monoculture "
                            + "(got " + dryShare + ")");
        }
        double dryMean = dryAggregate / 12.0;
        double wetMean = wetAggregate / 12.0;
        double tropicalDryMean = tropicalDryAggregate / 12.0;
        assertTrue(dryMean >= 0.32 && dryMean <= 0.45,
                "mean subtropical WARM_DRY share sits in the Earth-analog belt band "
                        + "(got " + dryMean + ")");
        assertTrue(wetMean >= 0.15 && wetMean <= 0.35,
                "the subtropical WARM_WET share stays in its pre-lever range — this lever must "
                        + "not eat the humid belt (got " + wetMean + ")");
        assertTrue(tropicalDryMean <= 0.10,
                "the deep tropics stay humid — the 1.4 wet-bias law is untouched "
                        + "(got " + tropicalDryMean + ")");
    }

    /** First column the given authority classifies as the wanted province; deterministic scan. */
    private static int[] findProvinceColumn(
            ProvinceAuthority authority,
            ProvinceAuthority.Province wanted,
            int radius) {
        for (int blockZ = 2_400; blockZ <= 7_000; blockZ += 200) {
            for (int blockX = -9_600; blockX <= 9_600; blockX += 400) {
                if (authority.classify(blockX, blockZ) == wanted) {
                    return new int[] {blockX, blockZ};
                }
            }
        }
        throw new AssertionError("no column classifies as " + wanted + " in the probe scan");
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
        // 1.21.1 passes the saved-data id at lookup time rather than carrying it on the type.
        assertTrue(
                state.contains("return world.getDataStorage().get(STATE_TYPE, STATE_ID);"),
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
                mod.contains("LatitudeWorldState.WorldgenPolicyVersion.PROVIDER_TICKET_V5_FINAL_ADMISSION"),
                "new UI-created Latitude worlds explicitly lock V5 final admission before generation");
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
     * ResourceLocation to a plain String -- moving the file from
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
        assertFalse(LatitudeWorldgenScope.isFeatureActive(), "feature scope starts inactive");
        try (LatitudeWorldgenScope.Scope overworld = LatitudeWorldgenScope.enter(true)) {
            assertTrue(LatitudeWorldgenScope.isActive(), "authorized overworld scope is active");
            assertFalse(LatitudeWorldgenScope.isFeatureActive(),
                    "ordinary structure/surface authority does not authorize decoration block filtering");
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

        try (LatitudeWorldgenScope.Scope features = LatitudeWorldgenScope.enterFeatures(true)) {
            assertTrue(LatitudeWorldgenScope.isFeatureActive(),
                    "the explicit biome-decoration scope authorizes vegetation block filtering");
            try (LatitudeWorldgenScope.Scope nested = LatitudeWorldgenScope.enter(true)) {
                assertTrue(LatitudeWorldgenScope.isFeatureActive(),
                        "nested generator queries inherit the active decoration phase");
            }
            try (LatitudeWorldgenScope.Scope inactive = LatitudeWorldgenScope.enter(false)) {
                assertFalse(LatitudeWorldgenScope.isFeatureActive(),
                        "an inactive nested dimension cannot inherit decoration authority");
            }
            assertTrue(LatitudeWorldgenScope.isFeatureActive(),
                    "closing an inactive nested frame restores decoration authority");
        }
        assertFalse(LatitudeWorldgenScope.isFeatureActive(),
                "closing the decoration scope clears its phase marker");
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

    /**
     * TerraBlender re-owns surface painting for every minecraft-namespace biome of any overworld
     * noise settings it claims, answering from its own bundled copy of the vanilla rules before
     * the settings' real rules are ever consulted. That copy is a stale snapshot — it still bands
     * badlands off the vanilla y=74 start check and caps orange terracotta at y=256, where
     * Latitude's shipped globe rules band off 84 with no cap — so any claim on globe: settings
     * buries Latitude's tuned surface in an unreachable fallback. The sweep must stay wired,
     * fail-open, reflection-only, and a pass-through rather than a re-injection.
     */
    private static void terraBlenderSweepKeepsLatitudeSurfaceIdentity() throws Exception {
        String mod = normalize(read("src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(mod.contains("LatitudeTerraBlenderBridge.install()"),
                "the TerraBlender surface sweep must be installed during common mod init");

        String bridge = normalize(read(
                "src/main/java/com/example/globe/compat/LatitudeTerraBlenderBridge.java"));
        assertTrue(bridge.contains("isModLoaded(\"terrablender\")"),
                "the sweep must only engage when TerraBlender is actually present");
        assertTrue(bridge.contains("latitude.terrablenderBridge.disable"),
                "the sweep must keep its kill switch for live diagnosis");
        assertTrue(bridge.contains("catch (Throwable t)")
                        && bridge.contains("failed to install"),
                "a sweep failure must degrade to TerraBlender's stock behavior, never a crash");
        assertTrue(bridge.contains("setDefaultSurfaceRules"),
                "the sweep must replace TerraBlender's default minecraft-namespace rules");
        assertTrue(bridge.contains("{\\\"type\\\":\\\"minecraft:sequence\\\",\\\"sequence\\\":[]}"),
                "the replacement must be the canonical always-null empty sequence, so every "
                        + "minecraft-namespace biome falls through to the real noise-settings rules");
        assertTrue(bridge.contains("getDefaultSurfaceRuleAdditionsForStage")
                        && bridge.contains("\"BEFORE_BEDROCK\"") && bridge.contains("\"AFTER_BEDROCK\""),
                "stage additions other mods registered must be woven in, not silently dropped");
        assertTrue(bridge.contains("Class.forName(\"terrablender.api.SurfaceRuleManager\"")
                        && !bridge.contains("import terrablender"),
                "TerraBlender must stay a reflective soft dependency, never a compile-time one");
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

        String protoVegetation = normalize(read(
                "src/main/java/com/example/globe/mixin/ProtoChunkPolarVegetationGuardMixin.java"));
        assertTrue(
                protoVegetation.contains("LatitudeWorldgenScope.isFeatureActive()"),
                "the block-write foliage backstop is restricted to decoration, not structures");

        String features = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorGenerateFeaturesBiomeSetMixin.java"));
        assertTrue(
                occurrences(features, "LatitudeWorldgenScope.isActive()") >= 2,
                "both feature-index and retainAll mutations fail open outside an authorized scope");
        String retainAll = methodBody(
                features,
                "private boolean globe$logRetainAll(Set<?> biomes, Collection<?> retainSet) {",
                "custom-biome retainAll");
        assertTrue(
                retainAll.contains("boolean debugRetainAll = LATITUDE_DEBUG_CUSTOM_RETAINALL_GATES;")
                        && retainAll.contains("this.globe$customBiomeIndexSafe || debugRetainAll"),
                "release worldgen keeps the custom-biome preservation snapshot but does not create it solely for disabled diagnostics");
        assertTrue(
                retainAll.indexOf("if (debugRetainAll)")
                        < retainAll.indexOf("boolean after = latitude$hasPolicyCustomBiome"),
                "the second full custom-biome scan is debug-only rather than per-chunk release work");
        assertTrue(
                features.contains("LATITUDE_CUSTOM_INDEX_FAILURE_WARNED.compareAndSet(false, true)")
                        && features.contains("indexExpansion result=blocked exceptionType={}"),
                "a custom feature-index failure emits one bounded warning instead of silently disabling retention");

        String generatorScope = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorWorldgenAuthorityMixin.java"));
        String noiseScope = normalize(read(
                "src/main/java/com/example/globe/mixin/NoiseChunkGeneratorWorldgenAuthorityMixin.java"));
        int decorationWrapperStart = generatorScope.indexOf("private void globe$withFeatureAuthority(");
        int placedFeatureWrapperStart = generatorScope.indexOf("private boolean globe$withPlacedFeatureAuthority(");
        int structureWrapperStart = generatorScope.indexOf("private void globe$withStructureAuthority(");
        assertTrue(
                decorationWrapperStart >= 0
                        && placedFeatureWrapperStart > decorationWrapperStart
                        && structureWrapperStart > placedFeatureWrapperStart,
                "feature, placed-feature, and structure authority boundaries stay independently reviewable");
        String decorationWrapper = generatorScope.substring(
                decorationWrapperStart, placedFeatureWrapperStart);
        String placedFeatureWrapper = generatorScope.substring(
                placedFeatureWrapperStart, structureWrapperStart);
        assertTrue(
                decorationWrapper.contains("LatitudeWorldgenScope.enter(active)")
                        && !decorationWrapper.contains("enterFeatures("),
                "the whole decoration method must not classify its earlier structure-placement phase as foliage");
        assertTrue(
                generatorScope.contains("PlacedFeature;placeWithBiomeCheck")
                        && placedFeatureWrapper.contains("LatitudeWorldgenScope.enterFeatures(active)"),
                "only the actual placed-feature invocation may authorize vegetation block filtering");
        assertTrue(
                occurrences(generatorScope, "try (LatitudeWorldgenScope.Scope") >= 3,
                "feature and structure paths use distinct authority phases and both close through try-with-resources");
        assertTrue(
                occurrences(noiseScope, "try (LatitudeWorldgenScope.Scope") >= 2,
                "surface and carver paths close authority through try-with-resources");
        assertTrue(
                occurrences(generatorScope + noiseScope, "Level.OVERWORLD") >= 2,
                "each dimension-bearing wrapper explicitly restricts authority to the overworld");
        // 1.21.1's ChunkGenerator.createStructures takes no dimension key -- that parameter arrived
        // later -- so that wrapper has no dimension to read. It must therefore gate on the
        // generator identity, which is the discriminator that actually decides this: only
        // Latitude's own configured noise generator answers true.
        String structureWrapper = generatorScope.substring(structureWrapperStart);
        assertTrue(
                !structureWrapper.contains("ResourceKey<Level>")
                        && structureWrapper.contains("globe$isAuthorizedGenerator()"),
                "the structure wrapper, which has no dimension parameter on this target, gates on "
                        + "the Latitude generator identity instead");
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
