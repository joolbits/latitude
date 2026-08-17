package com.example.globe.world;

import com.example.globe.client.create.RecreatedWorldTypePolicy;
import com.example.globe.client.create.RecreatedWorldMetadata;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        sulfurCaveDecorationIsUnrestricted();
        globeSurfaceRulesKeepEveryVanillaBiomeSubstrate();
        terraBlenderBridgeRestoresSulfurSubstrate();
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
        onlyWetProvincesAdmitWetlandClaims();
        wetlandLocateFilterMatchesFinalIdentityLaw();
        biomeLocateServiceClaimsAllSupportedTargets();
        structureLocateServiceIsBoundedAndTickDelivered();
        customSurfaceLocatePreviewUsesRegistryAuthority();
        LatitudeLocateBudgetPolicyTest.main(new String[0]);
        BiomeProviderSelectionPolicyTest.run();
        registeredHookIntegrationIsClosed();
        structureAdmissionUsesResolvedLatitudeBiomeSource();
        System.out.println("WORLDGEN_AUTHORITY_POLICY_TEST_PASS");
    }

    private static void structureAdmissionUsesResolvedLatitudeBiomeSource() throws Exception {
        String mixin = normalize(read(
                "src/main/java/com/example/globe/mixin/StructureBiomeMatchGuardMixin.java"));
        String config = normalize(read("src/main/resources/globe.mixins.json"));

        assertTrue(
                mixin.contains("@Mixin(Structure.class)"),
                "the admission redirect must target Minecraft's actual structure biome predicate");
        assertTrue(
                mixin.contains("method = \"isValidBiome\"")
                        && mixin.contains("ChunkGenerator;getBiomeSource()")
                        && mixin.contains("Structure.GenerationStub ignoredStub")
                        && mixin.contains("Structure.GenerationContext context"),
                "the redirect must match the exact 26.2 biome-validation frame and raw-generator lookup");
        assertTrue(
                mixin.contains("BiomeSource contextSource = context.biomeSource()")
                        && mixin.contains("contextSource instanceof LatitudeBiomeSource"),
                "Latitude structure starts must validate against their resolved context biome source");
        assertTrue(
                mixin.contains("? contextSource")
                        && mixin.contains(": generator.getBiomeSource()"),
                "non-Latitude worlds must retain Minecraft's original biome-source behavior");
        assertTrue(
                mixin.contains("@Inject(method = \"generate\", at = @At(\"RETURN\"), cancellable = true)")
                        && mixin.contains("BlockPos center = start.getBoundingBox().getCenter()")
                        && mixin.contains("Holder<Biome> resolved = latitudeSource.getNoiseBiome(")
                        && mixin.contains("if (resolved == null")
                        && mixin.contains("|| !validBiome.test(resolved)")
                        && mixin.contains("|| woodlandMansionInCustomBiome")
                        && mixin.contains("|| badlandsDesolationMismatch"),
                "a completed Latitude structure must keep its visible center in a legal biome");
        assertTrue(
                mixin.contains("boolean woodlandMansionInCustomBiome = structureId != null")
                        && mixin.contains("\"minecraft\".equals(structureId.getNamespace())")
                        && mixin.contains("\"mansion\".equals(structureId.getPath())")
                        && !mixin.contains("\"woodland_mansion\".equals(structureId.getPath())")
                        && mixin.contains("!\"minecraft\".equals(biomeId.getNamespace())")
                        && mixin.contains("|| woodlandMansionInCustomBiome"),
                "a woodland mansion must not treat a third-party biome tag as Latitude admission");
        assertTrue(
                mixin.contains("boolean badlandsDesolationMismatch = structureId != null")
                        && mixin.contains(
                        "VillageBiomeAdmissionPolicy.shouldRefuseStructureInVillageFreeBiome("),
                "badlands must refuse desert-declared surface structures and surface outposts at final admission");
        assertFalse(
                mixin.contains("int[][] samples = {")
                        || mixin.contains("legalSamples")
                        || mixin.contains("box.minX()"),
                "fresh-world structure admission must not multiply terrain-aware probes across every footprint corner");
        assertTrue(
                mixin.contains("if (!(biomeSource instanceof LatitudeBiomeSource latitudeSource))")
                        && mixin.contains("cir.setReturnValue(StructureStart.INVALID_START)"),
                "footprint admission must fail closed only for the Latitude-resolved structure source");
        assertTrue(
                config.contains("\"StructureBiomeMatchGuardMixin\""),
                "the structure admission redirect must be registered at runtime");
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

        BiomeDescriptorLedger.Descriptor vanillaDesert =
                BiomeDescriptorLedger.descriptor("minecraft:desert");
        assertTrue(
                aridTerrainCompatible(vanillaDesert, false),
                "minecraft:desert is accepted on lowland arid terrain");
        assertFalse(
                aridTerrainCompatible(vanillaDesert, true),
                "minecraft:desert is rejected when the final physical terrain class is upland");

        BiomeDescriptorLedger.Descriptor highlandArid =
                BiomeDescriptorLedger.descriptor("clifftree:desert_cliff");
        assertTrue(
                aridTerrainCompatible(highlandArid, true),
                "an explicitly ARID_UPLAND custom descriptor remains eligible on upland arid terrain");

        BiomeDescriptorLedger.Descriptor ordinaryCustomDesert =
                BiomeDescriptorLedger.descriptor("biomesoplenty:lush_desert");
        assertFalse(
                aridTerrainCompatible(ordinaryCustomDesert, true),
                "an ordinary custom lowland desert is not promoted merely because it is third-party");

        BiomeDescriptorLedger.Descriptor nonAridUpland =
                BiomeDescriptorLedger.descriptor("minecraft:stony_peaks");
        assertTrue(
                aridTerrainCompatible(nonAridUpland, true),
                "existing non-arid upland behavior is unchanged");
        assertTrue(
                aridTerrainCompatible(nonAridUpland, false),
                "existing non-arid lowland behavior is unchanged");
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
        assertEquals(
                2,
                occurrences(
                        source,
                        "TerrainBiomeCohesionPolicy.shouldEnforceFinalTemperateUpland( forceTemperateUpland, hasBiomeRoute(out, BiomeRoute.TEMPERATE_UPLAND))"),
                "both picker paths preserve descriptor-approved physical upland identities "
                        + "through the final return");
        assertEquals(
                2,
                occurrences(source, "boolean finalPhysicalUpland = TerrainBiomeCohesionPolicy.isPhysicalUpland("),
                "both picker paths derive the arid elevation gate from final physical terrain evidence");
        assertEquals(
                2,
                occurrences(source, "out = enforceFinalAridTerrainAuthority("),
                "both picker paths enforce the descriptor-owned arid terrain route at final return");
        assertEquals(
                2,
                occurrences(source, "PreviewTerrain finalAridProbe = onDemandFinalAridTerrain("),
                "both picker paths classify a final lowland-arid candidate without generator re-entry");
        assertTrue(
                source.contains(
                        "if (\"MIXIN\".equals(normalized) || \"CAVE_CLAMP\".equals(normalized)) { return true; }"),
                "MIXIN and CAVE_CLAMP unconditionally skip generator-reentrant terrain previews");
        String onDemandGate = methodBody(source, "private static PreviewTerrain onDemandGateTerrain(");
        String onDemandArid = methodBody(source, "private static PreviewTerrain onDemandFinalAridTerrain(");
        String nonReentrantEvidence = methodBody(
                source, "private static PreviewTerrain nonReentrantTerrainEvidence(");
        for (String criticalPath : new String[]{onDemandGate, onDemandArid, nonReentrantEvidence}) {
            assertFalse(
                    criticalPath.contains("previewTerrain(")
                            || criticalPath.contains("previewHeight(")
                            || criticalPath.contains("getBaseHeight("),
                    "live on-demand terrain classification cannot re-enter the chunk generator");
        }
        for (String routedPath : new String[]{onDemandGate, onDemandArid}) {
            assertTrue(
                    routedPath.contains("nonReentrantTerrainEvidence("),
                    "live terrain classification reuses cached height plus sampler relief");
        }
        assertTrue(
                nonReentrantEvidence.contains("new PreviewTerrain(")
                        && nonReentrantEvidence.contains(
                                "ruggedNoiseLike ? TerrainBiomeCohesionPolicy.RUGGED_RELIEF_BLOCKS : 0"),
                "the terminal live evidence contains only cached height and the sampler ruggedness proxy");
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
        int firstCoverage = source.indexOf("out = applyVanillaCoverage(");
        int firstPolarClamp = source.indexOf(
                "out = clampFinalPolarNonMountainAlpineOutput(", firstCoverage);
        int firstJungleGate = source.indexOf("out = gateWarmJungleSurvival(", firstCoverage);
        int firstWarmWetDesertGate = source.indexOf(
                "out = gateWarmWetDesertSurvival(", firstJungleGate);
        int firstAridLatitude = source.indexOf("out = applyFinalAridLatitudeLaw(", firstCoverage);
        int firstAridTerrain = source.indexOf("out = enforceFinalAridTerrainAuthority(");
        int secondCoverage = source.indexOf("out = applyVanillaCoverage(", firstCoverage + 1);
        int secondPolarClamp = source.indexOf(
                "out = clampFinalPolarNonMountainAlpineOutput(", secondCoverage);
        int secondJungleGate = source.indexOf("out = gateWarmJungleSurvival(", secondCoverage);
        int secondWarmWetDesertGate = source.indexOf(
                "out = gateWarmWetDesertSurvival(", secondJungleGate);
        int secondAridLatitude = source.indexOf("out = applyFinalAridLatitudeLaw(", secondCoverage);
        int secondAridTerrain = source.indexOf(
                "out = enforceFinalAridTerrainAuthority(", firstAridTerrain + 1);
        assertTrue(
                firstCoverage >= 0
                        && firstPolarClamp > firstCoverage
                        && firstJungleGate > firstPolarClamp
                        && firstWarmWetDesertGate > firstJungleGate
                        && firstAridLatitude > firstWarmWetDesertGate
                        && firstAridTerrain > firstCoverage
                        && secondCoverage > firstAridTerrain
                        && secondPolarClamp > secondCoverage
                        && secondJungleGate > secondPolarClamp
                        && secondWarmWetDesertGate > secondJungleGate
                        && secondAridLatitude > secondWarmWetDesertGate
                        && secondAridTerrain > secondCoverage,
                "both picker paths run polar, bidirectional humidity, arid-latitude, and physical-terrain "
                        + "authorities after land coverage");
        assertEquals(
                2,
                occurrences(source, "out = gateWarmWetDesertSurvival("),
                "each picker path has exactly one final inverse-humidity desert gate");
        assertEquals(
                2,
                occurrences(source, "out = applyVanillaCoverage("),
                "each picker path has exactly one final-admission land coverage point");
        assertFalse(
                source.contains(
                        "if (isBiomeId(chosen, \"minecraft:cherry_grove\") && landBandIndex < BAND_POLAR) { return chosen; }"),
                "Cherry Grove must pass through the same final land admission sequence");
        assertTrue(
                source.contains("isBiomeId(candidate, \"minecraft:sunflower_plains\")"),
                "the exact lowland biome visible in TEST 19 is classified with the plains family");
    }

    private static boolean aridTerrainCompatible(
            BiomeDescriptorLedger.Descriptor descriptor,
            boolean physicalUpland) {
        assertTrue(descriptor != null, "the discriminator biome has a ledger descriptor");
        return TerrainBiomeCohesionPolicy.isAridBiomeCompatibleWithTerrain(
                physicalUpland,
                descriptor.routes().contains(BiomeRoute.ARID_LOWLAND),
                descriptor.routes().contains(BiomeRoute.ARID_UPLAND));
    }

    private static String methodBody(String source, String declaration) {
        int start = source.indexOf(declaration);
        assertTrue(start >= 0, "expected method declaration: " + declaration);
        int brace = source.indexOf('{', start);
        assertTrue(brace >= 0, "expected method body: " + declaration);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace + 1, i);
                }
            }
        }
        throw new AssertionError("unterminated method body: " + declaration);
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

        Path worldRoot = Path.of("build", "tmp", "recreated-world-metadata-test");
        Path statePath = worldRoot.resolve(Path.of(
                "dimensions", "minecraft", "overworld", "data", "globe", "latitude_world_state.dat"));
        Files.createDirectories(statePath.getParent());
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

    /** Wetland claims require a genuinely wet province; mangrove remains separately governed. */
    private static void onlyWetProvincesAdmitWetlandClaims() throws Exception {
        int radius = 10_000;
        ProvinceAuthority probe = new ProvinceAuthority(7L, radius);
        int[] warmDry = findProvinceColumn(probe, ProvinceAuthority.Province.WARM_DRY);
        int[] coldDry = findProvinceColumn(probe, ProvinceAuthority.Province.COLD_DRY);
        int[] warmMedium = findProvinceColumn(probe, ProvinceAuthority.Province.WARM_MEDIUM);
        int[] coldMedium = findProvinceColumn(probe, ProvinceAuthority.Province.COLD_MEDIUM);
        int[] warmWet = findProvinceColumn(probe, ProvinceAuthority.Province.WARM_WET);
        int[] coldWet = findProvinceColumn(probe, ProvinceAuthority.Province.COLD_WET);

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
        assertEquals(5, occurrences(source, "&& wetlandProvinceEligible(blockX, blockZ)"),
                "coverage planning, both final wetland authorities, and the locator broad phase "
                        + "share the wet-province law");
        assertTrue(source.contains(
                        "case TEMPERATE_WETLAND -> band == BAND_TEMPERATE && !mountain"
                                + " && wetlandProvinceEligible(blockX, blockZ)"),
                "the temperate wetland route arm shares the final wet-province law");
        assertTrue(source.contains(
                        "case SUBPOLAR_WETLAND -> band == BAND_SUBPOLAR && !mountain"
                                + " && wetlandProvinceEligible(blockX, blockZ)"),
                "the subpolar wetland route arm shares the final wet-province law");
    }

    /** First column the given authority classifies as the wanted province; deterministic scan. */
    private static int[] findProvinceColumn(
            ProvinceAuthority authority,
            ProvinceAuthority.Province wanted) {
        for (int blockZ = 2_400; blockZ <= 7_000; blockZ += 200) {
            for (int blockX = -9_600; blockX <= 9_600; blockX += 400) {
                if (authority.classify(blockX, blockZ) == wanted) {
                    return new int[] {blockX, blockZ};
                }
            }
        }
        throw new AssertionError("no column classifies as " + wanted + " in the probe scan");
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

    private static void structureLocateServiceIsBoundedAndTickDelivered() throws Exception {
        String service = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeStructureLocateService.java"));
        String mixin = normalize(read(
                "src/main/java/com/example/globe/mixin/LocateCommandMixin.java"));

        assertTrue(
                mixin.contains("@Inject(method = \"locateStructure\", at = @At(\"HEAD\"), cancellable = true, require = 1)")
                        && mixin.contains("LatitudeStructureLocateService.beginIfApplicable(source, target)")
                        && mixin.contains("cir.setReturnValue(1);"),
                "the exact 26.2 structure command must return immediately through the Latitude service");
        assertTrue(
                service.contains("CompletableFuture.supplyAsync(")
                        && service.contains("Util.backgroundExecutor()")
                        && !service.contains("whenCompleteAsync("),
                "heavy structure candidate evaluation must leave the server thread without delivering UI off-thread");
        assertTrue(
                service.contains("ServerTickEvents.END_SERVER_TICK.register(LatitudeStructureLocateService::tick)")
                        && service.contains("if (job.isDone()) { ACTIVE_JOBS.remove(server); job.complete(); }"),
                "normal server ticks must poll and deliver the completed result");
        assertTrue(
                service.contains("new ServerBossEvent(")
                        && service.contains("BossEvent.BossBarColor.BLUE")
                        && service.contains("bossBar.setProgress(percent / 100.0F)")
                        && service.contains("bossBar.setName(Component.literal("),
                "the player must receive one blue progress bar updated in place");
        assertTrue(
                service.contains("private void finishWithResult(SearchOutcome outcome)")
                        && service.contains("private void finishWithFailure(Throwable failure)")
                        && service.contains("private void cancel()")
                        && occurrences(service, "clearBossBar();") >= 3
                        && service.contains("bossBar.removeAllPlayers();"),
                "success, failure, and cancellation must all clear the structure progress bar");
        assertTrue(
                service.contains("ServerPlayConnectionEvents.DISCONNECT.register(")
                        && service.contains("ServerLifecycleEvents.SERVER_STOPPED.register(server -> {")
                        && service.contains("cancel(server, null);")
                        && service.contains("PENDING_TELEPORTS.remove(server);")
                        && service.contains("clearPendingTeleport(server, handler.player);"),
                "disconnect and server stop must cancel the owned structure search and clear teleport tokens");
        assertTrue(
                service.contains("private static final Map<MinecraftServer, StructureLocateJob> ACTIVE_JOBS")
                        && service.contains("if (ACTIVE_JOBS.containsKey(server))"),
                "one server may own only one active Latitude structure search");
        assertTrue(
                service.contains("if (!(placement instanceof RandomSpreadStructurePlacement spread)) { return false; }")
                        && service.contains("placement().isStructureChunk("),
                "unsupported placements must defer wholly to vanilla and supported candidates must pass Minecraft's full placement predicate");
        assertTrue(
                service.contains("structureState.possibleStructureSets()")
                        && service.contains("structureSet.structures().stream()")
                        && service.contains("StructurePlacement placement = structureSet.placement();")
                        && !service.contains("structureState.getPlacementsForStructure(")
                        && !service.contains("structureState.ensureStructuresGenerated();"),
                "command admission must inspect the existing structure-set roster without triggering lazy structure generation on the server thread");
        assertTrue(
                service.contains("SearchBounds bounds = new SearchBounds(")
                        && service.contains("if (!context.bounds().contains(locatePos))"),
                "the custom search must stay inside the playable Latitude border snapshot");
        assertTrue(
                service.contains("LatitudeBiomeSource finalBiomeSource = LatitudeBiomeSource.forStructure(")
                        && service.contains("candidate.structure().generate(")
                        && service.contains("context.templateManager()")
                        && service.contains("if (generatedStart == null || !generatedStart.isValid())")
                        && service.contains("BlockPos center = generatedStart.getBoundingBox().getCenter()")
                        && service.contains("generatedStart.getBoundingBox().maxY() + 2")
                        && service.contains("ringBest = Pair.of(generatedTarget, candidate.holder())")
                        && service.contains("LatitudeBiomes.villageVariantVsBiomeMismatch("),
                "locate candidates must pass Minecraft's real generation-point and return the generated structure center");
        assertTrue(
                service.contains("showTeleportLocateResult(source, target, context.origin(), outcome.result())")
                        && service.contains("new ClickEvent.RunCommand(\"/latitude_locate_teleport \" + token)")
                        && service.contains("pending == null || !pending.token().equals(token)")
                        && service.contains("Util.getMillis() > pending.expiresAtMs()")
                        && service.contains("serverTeleports.remove(player.getUUID())")
                        && service.contains("Math.max(player.getY(), pending.minimumY())")
                        && service.contains("\"globe.locate.buried_structure.teleported\"")
                        && service.contains("\"globe.locate.buried_structure.hint\"")
                        && !service.contains("Centered above the generated desert pyramid")
                        && !service.contains("new ClickEvent.RunCommand(\"/tp ")
                        && service.contains("\"commands.locate.structure.not_found\""),
                "the coordinate click must use a player-bound action centered safely above the generated structure");
        assertTrue(
                service.contains("try { job.start(); } catch (Throwable failure) { ACTIVE_JOBS.remove(server); job.finishWithFailure(failure); }")
                        && service.contains("finally { finished = true; clearBossBar(); }")
                        && service.contains("future.cancel(false);"),
                "startup, delivery, and worker failures must release the server job slot and clear the boss bar");

        String net = normalize(read("src/main/java/com/example/globe/GlobeNet.java"));
        String server = normalize(read("src/main/java/com/example/globe/GlobeMod.java"));
        String client = normalize(read("src/main/java/com/example/globe/GlobeModClient.java"));
        String tools = normalize(read("src/main/java/com/example/globe/tools/LatitudeToolsCommand.java"));
        String launcher = normalize(read(
                "src/main/java/com/example/globe/client/create/LatitudeWorldLauncher.java"));
        assertTrue(
                net.contains("record GlobeStatePayload(boolean isGlobe, String loadingBandId)")
                        && net.contains("GlobeStatePayload::loadingBandId"),
                "the existing globe handshake must carry the authoritative loading-band id");
        assertTrue(
                server.contains("recordLastKnownBand(overworld, overworld.getWorldBorder(), handler.player);")
                        && server.contains("new GlobeNet.GlobeStatePayload(isGlobe, loadingBandId)"),
                "the first Latitude join must snapshot the actual band before it sends the loading state");
        assertTrue(
                tools.contains("Commands.literal(\"latitude_locate_teleport\")")
                        && tools.contains("LatitudeStructureLocateService.runPendingTeleport(")
                        && !server.contains("LatitudeStructureLocateService.registerTeleportCommand(dispatcher)"),
                "the warning-free locate action must be registered only inside the audited shipping command surface");
        assertTrue(
                client.contains("LatitudeBands.fromCanonicalId(payload.loadingBandId())")
                        && client.contains("LatitudeClientState.setLoadingZoneLabel(band.displayName())"),
                "the handshake must restore the italic loading label after a new-world disconnect boundary");
        assertTrue(
                launcher.contains("LatitudeClientState.setLoadingZoneLabel(randomSpawnZone ? null : spawnZone.displayName())"),
                "the immediate selected-zone label remains available before the server handshake arrives");
    }

    private static void customSurfaceLocatePreviewUsesRegistryAuthority() throws Exception {
        String source = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        int methodStart = source.indexOf("Holder<Biome> getLocatePreviewNoiseBiome(");
        int methodEnd = source.indexOf("private record SurfaceLocateOutcome", methodStart);
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

    private static void sulfurCaveDecorationIsUnrestricted() throws Exception {
        String populate = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java"));
        assertFalse(
                populate.contains("SULFUR_SURFACE_REACH_BLOCKS")
                        || populate.contains("sulfurMayReachSurface")
                        || populate.contains("sulfurSurfaceIncompatible")
                        || populate.contains("isSulfurCaves"),
                "surface policy no longer erases the shallow sulfur-cave biome before decoration");

        String mixins = normalize(read("src/main/resources/globe.mixins.json"));
        assertFalse(
                mixins.contains("SulfurPoolSurfaceGuardMixin")
                        || Files.exists(Path.of(
                                "src/main/java/com/example/globe/mixin/SulfurPoolSurfaceGuardMixin.java"))
                        || Files.exists(Path.of(
                                "src/main/java/com/example/globe/world/SulfurSurfaceExpressionPolicy.java")),
                "Latitude does not register or retain a sulfur pool/spike placement restriction");

        String speleothemGuard = normalize(read(
                "src/main/java/com/example/globe/mixin/SurfaceDripstoneLawnmowerMixin.java"));
        assertTrue(
                speleothemGuard.contains("latitude$isSulfurSpeleothem(context.config())")
                        && speleothemGuard.contains("speleothem.pointedBlock().is(Blocks.SULFUR_SPIKE)")
                        && speleothemGuard.contains("cluster.pointedBlock().is(Blocks.SULFUR_SPIKE)")
                        && speleothemGuard.contains("if (nearSurfaceByHeightmap || skyVisible)"),
                "sulfur speleothems fail open before the ordinary dripstone surface guard");
    }

    /**
     * TerraBlender swaps the surface rules of every overworld noise settings for its own bundled
     * pre-sulfur copy of the vanilla rules, whose deepslate catch-all answers for everything below
     * roughly y=0 — precisely the stratum sulfur caves occupy. Measured on one seed with the live
     * modset: 3 substrate blocks below y=0 before the bridge, 76,071 after. The bridge must stay
     * wired, fail-open, reflection-only, and sourced from the shipped noise settings.
     */
    private static void terraBlenderBridgeRestoresSulfurSubstrate() throws Exception {
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

    private static final String[] GLOBE_NOISE_SETTINGS = {
        "overworld", "overworld_large", "overworld_massive",
        "overworld_regular", "overworld_small", "overworld_xsmall"
    };

    /**
     * Latitude serves its own overworld noise settings, so per-biome substrate painting is never
     * inherited from a new Minecraft version automatically. When 26.2 added sulfur caves, the fork
     * kept 27 of 28 clauses and dropped that one: the biome still generated, but nothing painted
     * sulfur or cinnabar, so every substrate-gated feature no-opped with no error anywhere. This
     * fails the build if a version bump ever drops another biome's substrate the same way.
     */
    private static void globeSurfaceRulesKeepEveryVanillaBiomeSubstrate() throws Exception {
        Set<String> vanillaBiomes = paintedBiomes(vanillaOverworldSurfaceRule());
        assertTrue(vanillaBiomes.contains("minecraft:sulfur_caves"),
                "the vanilla overworld surface rules on the test classpath must paint sulfur caves");

        String shared = null;
        for (String name : GLOBE_NOISE_SETTINGS) {
            String body = read("src/main/resources/data/globe/worldgen/noise_settings/"
                    + name + ".json");
            if (shared == null) {
                shared = body;
            }
            assertTrue(body.equals(shared),
                    "every world size must share one set of surface rules; " + name + " diverged");

            JsonObject surfaceRule = JsonParser.parseString(body).getAsJsonObject()
                    .getAsJsonObject("surface_rule");

            Set<String> dropped = new LinkedHashSet<>(vanillaBiomes);
            dropped.removeAll(paintedBiomes(surfaceRule));
            assertTrue(dropped.isEmpty(),
                    "globe surface rules drop substrate that Minecraft paints " + dropped + " in "
                            + name + "; port those clauses when moving Minecraft versions");

            List<JsonObject> sulfurClauses = biomeClauses(surfaceRule, "minecraft:sulfur_caves");
            assertTrue(sulfurClauses.size() == 3,
                    "sulfur caves need their deep clause and both floor clauses in " + name
                            + "; found " + sulfurClauses.size());
            for (JsonObject clause : sulfurClauses) {
                Set<String> painted = paintedBlocks(clause);
                assertTrue(painted.contains("minecraft:sulfur")
                                && painted.contains("minecraft:cinnabar"),
                        "each sulfur clause must paint sulfur and cinnabar in " + name);
                assertTrue(referencedNoises(clause).contains("minecraft:sulfur_cave_gradient"),
                        "sulfur substrate must band on the vanilla gradient noise in " + name);
            }

            JsonArray topLevel = surfaceRule.getAsJsonArray("sequence");
            int sulfurIndex = -1;
            boolean gradientFollowsSulfur = false;
            for (int i = 0; i < topLevel.size(); i++) {
                JsonObject entry = topLevel.get(i).getAsJsonObject();
                if (!entry.has("if_true") || !entry.get("if_true").isJsonObject()) {
                    continue;
                }
                JsonObject condition = entry.getAsJsonObject("if_true");
                if (isType(condition, "minecraft:biome")
                        && biomeIds(condition).contains("minecraft:sulfur_caves")) {
                    sulfurIndex = i;
                } else if (sulfurIndex >= 0 && isType(condition, "minecraft:vertical_gradient")) {
                    gradientFollowsSulfur = true;
                }
            }
            assertTrue(sulfurIndex >= 0,
                    "the deep sulfur clause must sit in the top-level rule sequence in " + name);
            assertTrue(gradientFollowsSulfur,
                    "the deep sulfur clause must be evaluated before the deepslate gradient in "
                            + name + ", or deepslate wins underground and no substrate is painted");
        }
    }

    private static JsonObject vanillaOverworldSurfaceRule() throws Exception {
        String resource = "/data/minecraft/worldgen/noise_settings/overworld.json";
        try (InputStream stream =
                     WorldgenAuthorityPolicyTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null,
                    "the vanilla overworld noise settings must be readable from the Minecraft jar "
                            + "on the test classpath (" + resource + ")");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("surface_rule");
        }
    }

    private static boolean isType(JsonObject object, String type) {
        return object.has("type") && type.equals(object.get("type").getAsString());
    }

    private static Set<String> biomeIds(JsonObject condition) {
        Set<String> ids = new LinkedHashSet<>();
        JsonElement value = condition.get("biome_is");
        if (value == null) {
            return ids;
        }
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                ids.add(entry.getAsString());
            }
        } else {
            ids.add(value.getAsString());
        }
        return ids;
    }

    private static List<JsonObject> objectsIn(JsonElement root) {
        List<JsonObject> objects = new ArrayList<>();
        collectObjects(root, objects);
        return objects;
    }

    private static void collectObjects(JsonElement node, List<JsonObject> objects) {
        if (node == null) {
            return;
        }
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            objects.add(object);
            for (String key : object.keySet()) {
                collectObjects(object.get(key), objects);
            }
        } else if (node.isJsonArray()) {
            for (JsonElement entry : node.getAsJsonArray()) {
                collectObjects(entry, objects);
            }
        }
    }

    private static Set<String> paintedBiomes(JsonElement root) {
        Set<String> biomes = new LinkedHashSet<>();
        for (JsonObject object : objectsIn(root)) {
            if (isType(object, "minecraft:biome")) {
                biomes.addAll(biomeIds(object));
            }
        }
        return biomes;
    }

    private static List<JsonObject> biomeClauses(JsonElement root, String biome) {
        List<JsonObject> clauses = new ArrayList<>();
        for (JsonObject object : objectsIn(root)) {
            if (!isType(object, "minecraft:condition") || !object.has("if_true")
                    || !object.get("if_true").isJsonObject()) {
                continue;
            }
            JsonObject condition = object.getAsJsonObject("if_true");
            if (isType(condition, "minecraft:biome") && biomeIds(condition).contains(biome)) {
                clauses.add(object);
            }
        }
        return clauses;
    }

    private static Set<String> paintedBlocks(JsonElement root) {
        Set<String> blocks = new LinkedHashSet<>();
        for (JsonObject object : objectsIn(root)) {
            if (isType(object, "minecraft:block") && object.has("result_state")) {
                JsonObject state = object.getAsJsonObject("result_state");
                if (state.has("Name")) {
                    blocks.add(state.get("Name").getAsString());
                }
            }
        }
        return blocks;
    }

    private static Set<String> referencedNoises(JsonElement root) {
        Set<String> noises = new LinkedHashSet<>();
        for (JsonObject object : objectsIn(root)) {
            if (isType(object, "minecraft:noise_threshold") && object.has("noise")) {
                noises.add(object.get("noise").getAsString());
            }
        }
        return noises;
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
        assertTrue(
                features.contains("LATITUDE_CUSTOM_INDEX_FAILURE_WARNED.compareAndSet(false, true)")
                        && features.contains("indexExpansion result=blocked exceptionType={}"),
                "a custom feature-index failure emits one bounded warning instead of silently disabling retention");

        String populate = normalize(read(
                "src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java"));
        String treeLine = normalize(read(
                "src/main/java/com/example/globe/mixin/TreeLineVegetationGuardMixin.java"));
        assertTrue(
                populate.contains("ChunkAccess;fillBiomesFromNoise")
                        && populate.contains("require = 1")
                        && !populate.contains("require=0")
                        && !populate.contains("require = 0"),
                "the final chunk-biome owner must fail during startup if its exact 26.2 hook drifts");
        assertTrue(
                treeLine.contains("@Mixin(TreeFeature.class)")
                        && treeLine.contains("require = 1")
                        && !treeLine.contains("require=0")
                        && !treeLine.contains("require = 0"),
                "the exact 26.2 TreeFeature tree-line hook must not fail open");

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
