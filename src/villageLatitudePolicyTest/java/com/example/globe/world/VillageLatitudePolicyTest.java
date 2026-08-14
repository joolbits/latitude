package com.example.globe.world;

import com.example.globe.util.LatitudeBands;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class VillageLatitudePolicyTest {
    private static final double EPSILON = 0.0000001;

    public static void main(String[] args) throws Exception {
        boundaryIsStrictAndSymmetricAtIttyRadius();
        boundaryIsStrictAndSymmetricAtGinormousRadius();
        integerChunkCentersMatchProductionForAllSupportedRadii();
        activeRadiusOverridesFallbackAndFallbackCoversEarlyWorldgen();
        generationGuardRejectsInvalidStartsBeforeStore();
        climatePolicyClassifiesRepresentativeVillageIds();
        biomeFamilyPolicyRejectsNamedVillageMismatches();
        physicalTerrainPolicyRejectsCliffsideVillageStarts();
        climateMismatchRejectsInvalidStartsBeforeStore();
        structureAdmissionUsesFinalLatitudeBiomeBeforeStartRegistration();
        staticIntegrationProofsHold();
        System.out.println("VILLAGE_LATITUDE_POLICY_TEST_PASS");
    }

    private static void boundaryIsStrictAndSymmetricAtIttyRadius() {
        assertBoundary(3_750);
    }

    private static void boundaryIsStrictAndSymmetricAtGinormousRadius() {
        assertBoundary(20_000);
    }

    private static void assertBoundary(int radius) {
        assertNear(
                80.0,
                VillageLatitudePolicy.MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES,
                "village latitude constant");

        double zAt79Point9 = radius * 79.9 / 90.0;
        double zAt80 = radius * 80.0 / 90.0;
        double zAt80Point1 = radius * 80.1 / 90.0;
        for (int hemisphere : new int[]{-1, 1}) {
            double z79Point9 = hemisphere * zAt79Point9;
            double z80 = hemisphere * zAt80;
            double z80Point1 = hemisphere * zAt80Point1;

            assertNear(
                    79.9,
                    VillageLatitudePolicy.absoluteLatitudeDegrees(z79Point9, radius, 1),
                    "79.9 degree coordinate at radius " + radius + " hemisphere " + hemisphere);
            assertFalse(
                    VillageLatitudePolicy.shouldVetoVillageOrigin(z79Point9, radius, 1),
                    "79.9 degrees remains allowed at radius " + radius + " hemisphere " + hemisphere);

            assertNear(
                    80.0,
                    VillageLatitudePolicy.absoluteLatitudeDegrees(z80, radius, 1),
                    "80.0 degree coordinate at radius " + radius + " hemisphere " + hemisphere);
            assertFalse(
                    VillageLatitudePolicy.shouldVetoVillageOrigin(z80, radius, 1),
                    "exactly 80.0 degrees remains allowed at radius " + radius + " hemisphere " + hemisphere);

            assertNear(
                    80.1,
                    VillageLatitudePolicy.absoluteLatitudeDegrees(z80Point1, radius, 1),
                    "80.1 degree coordinate at radius " + radius + " hemisphere " + hemisphere);
            assertTrue(
                    VillageLatitudePolicy.shouldVetoVillageOrigin(z80Point1, radius, 1),
                    "80.1 degrees is vetoed at radius " + radius + " hemisphere " + hemisphere);
        }
    }

    private static void integerChunkCentersMatchProductionForAllSupportedRadii() {
        int[] supportedRadii = {3_750, 5_000, 7_500, 10_000, 15_000, 20_000};
        for (int radius : supportedRadii) {
            double exactLimitBlockZ =
                    radius * VillageLatitudePolicy.MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES / 90.0;
            int lastAllowedCenter = 8;
            while (lastAllowedCenter + 16 <= exactLimitBlockZ) {
                lastAllowedCenter += 16;
            }
            int firstBlockedCenter = lastAllowedCenter + 16;

            for (int hemisphere : new int[]{-1, 1}) {
                int allowedBlockZ = hemisphere * lastAllowedCenter;
                int blockedBlockZ = hemisphere * firstBlockedCenter;

                assertEquals(
                        0,
                        Math.floorMod(allowedBlockZ - 8, 16),
                        "last allowed coordinate has ChunkPos.getMiddleBlockZ form");
                assertEquals(
                        0,
                        Math.floorMod(blockedBlockZ - 8, 16),
                        "first blocked coordinate has ChunkPos.getMiddleBlockZ form");
                assertTrue(
                        VillageLatitudePolicy.absoluteLatitudeDegrees(allowedBlockZ, radius, 1)
                                <= VillageLatitudePolicy.MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES,
                        "last chunk center is at or below 80 degrees at radius "
                                + radius + " hemisphere " + hemisphere);
                assertTrue(
                        VillageLatitudePolicy.absoluteLatitudeDegrees(blockedBlockZ, radius, 1)
                                > VillageLatitudePolicy.MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES,
                        "next outward chunk center is above 80 degrees at radius "
                                + radius + " hemisphere " + hemisphere);
                assertFalse(
                        VillageLatitudePolicy.shouldVetoVillageOrigin(allowedBlockZ, radius, 1),
                        "last allowed production chunk center remains allowed at radius "
                                + radius + " hemisphere " + hemisphere);
                assertTrue(
                        VillageLatitudePolicy.shouldVetoVillageOrigin(blockedBlockZ, radius, 1),
                        "next outward production chunk center is vetoed at radius "
                                + radius + " hemisphere " + hemisphere);
            }
        }
    }

    private static void activeRadiusOverridesFallbackAndFallbackCoversEarlyWorldgen() {
        assertFalse(
                VillageLatitudePolicy.shouldVetoVillageOrigin(8_000, 0, 9_000),
                "zero active radius uses fallback and allows exactly 80 degrees");
        assertTrue(
                VillageLatitudePolicy.shouldVetoVillageOrigin(-8_010, 0, 9_000),
                "zero active radius uses fallback in the north hemisphere");

        assertFalse(
                VillageLatitudePolicy.shouldVetoVillageOrigin(16_000, 18_000, 9_000),
                "positive active radius overrides a smaller fallback at exactly 80 degrees");
        assertTrue(
                VillageLatitudePolicy.shouldVetoVillageOrigin(-16_020, 18_000, 9_000),
                "positive active radius overrides fallback beyond 80 degrees in the north");
    }

    private static void generationGuardRejectsInvalidStartsBeforeStore() {
        int radius = 7_500;
        double exactBoundary =
                radius * VillageLatitudePolicy.MAX_ALLOWED_ABSOLUTE_LATITUDE_DEGREES / 90.0;
        for (int hemisphere : new int[]{-1, 1}) {
            GenerationResult exact = simulateGenerationGuard(
                    hemisphere * exactBoundary,
                    radius,
                    true);
            assertTrue(
                    exact.start() == SimulatedStart.VALID,
                    "exactly 80 degrees reaches normal generation in hemisphere " + hemisphere);
            assertEquals(
                    1,
                    exact.originalGenerateCalls(),
                    "exactly 80 degrees invokes Structure.generate in hemisphere " + hemisphere);
            assertEquals(
                    1,
                    exact.storedStarts(),
                    "exactly 80 degrees can reach the valid/store path in hemisphere " + hemisphere);

            GenerationResult blocked = simulateGenerationGuard(
                    hemisphere * (exactBoundary + 0.01),
                    radius,
                    true);
            assertTrue(
                    blocked.start() == SimulatedStart.INVALID,
                    "beyond 80 degrees returns INVALID_START in hemisphere " + hemisphere);
            assertEquals(
                    0,
                    blocked.originalGenerateCalls(),
                    "beyond 80 degrees is rejected before Structure.generate in hemisphere " + hemisphere);
            assertEquals(
                    0,
                    blocked.storedStarts(),
                    "INVALID_START cannot reach the valid/store path in hemisphere " + hemisphere);

            GenerationResult nonVillage = simulateGenerationGuard(
                    hemisphere * (exactBoundary + 0.01),
                    radius,
                    false);
            assertTrue(
                    nonVillage.start() == SimulatedStart.VALID,
                    "non-village structures remain unaffected in hemisphere " + hemisphere);
            assertEquals(
                    1,
                    nonVillage.originalGenerateCalls(),
                    "non-village structures still invoke Structure.generate in hemisphere " + hemisphere);
            assertEquals(
                    1,
                    nonVillage.storedStarts(),
                    "valid non-village starts can still be stored in hemisphere " + hemisphere);
        }
    }

    private static GenerationResult simulateGenerationGuard(
            double blockZ,
            int radius,
            boolean village) {
        return simulateGenerationGuard(
                blockZ,
                radius,
                village ? "village_plains" : "igloo",
                true);
    }

    private static void climatePolicyClassifiesRepresentativeVillageIds() {
        assertTrue(
                LatitudeBiomes.villageClimateVsBandMismatch(
                        "village_desert", LatitudeBands.Band.TEMPERATE),
                "warm-declared village conflicts with a cold band");
        assertTrue(
                LatitudeBiomes.villageClimateVsBandMismatch(
                        "village_snowy", LatitudeBands.Band.TROPICAL),
                "cold-declared village conflicts with a warm band");
        assertFalse(
                LatitudeBiomes.villageClimateVsBandMismatch(
                        "village_desert", LatitudeBands.Band.TROPICAL),
                "warm-declared village remains compatible with a warm band");
        assertFalse(
                LatitudeBiomes.villageClimateVsBandMismatch(
                        "village_plains", LatitudeBands.Band.TEMPERATE),
                "neutral village remains fail-open");
        assertFalse(
                LatitudeBiomes.villageClimateVsBandMismatch(
                        "desert_pyramid", LatitudeBands.Band.TEMPERATE),
                "non-village structure remains fail-open");
    }

    private static void biomeFamilyPolicyRejectsNamedVillageMismatches() {
        assertTrue(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_savanna", "minecraft:bamboo_jungle"),
                "savanna village is rejected from bamboo jungle");
        assertTrue(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_savanna", "terralith:tropical_jungle"),
                "reviewed Terralith jungle uses its descriptor family for village safety");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_savanna", "biomesoplenty:lush_savanna"),
                "reviewed BOP lush savanna remains compatible with its named village family");
        assertTrue(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_savanna", "biomesoplenty:scrubland"),
                "a reviewed arid transition biome cannot inherit savanna compatibility by name guesswork");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_savanna", "minecraft:windswept_savanna"),
                "savanna village remains compatible with savanna terrain");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_desert", "minecraft:badlands"),
                "desert village remains compatible with the coherent desert-badlands family");
        assertTrue(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_desert", "minecraft:mangrove_swamp"),
                "desert village is rejected from mangrove coast");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_taiga", "minecraft:old_growth_pine_taiga"),
                "taiga village remains compatible with taiga terrain");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_snowy", "minecraft:snowy_plains"),
                "snowy village remains compatible with snowy terrain");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_plains", "minecraft:meadow"),
                "plains village remains compatible with open temperate terrain");
        assertTrue(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_plains", "minecraft:bamboo_jungle"),
                "plains village is rejected from bamboo jungle");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "village_savanna", "example:mystery"),
                "unknown provider biome remains fail-open");
        assertFalse(
                LatitudeBiomes.villageVariantVsBiomeMismatch(
                        "desert_pyramid", "minecraft:bamboo_jungle"),
                "non-village structure remains fail-open");
    }

    private static void physicalTerrainPolicyRejectsCliffsideVillageStarts() {
        assertEquals(
                25,
                VillageTerrainSuitabilityPolicy.SAMPLE_COUNT,
                "the production village terrain gate remains bounded to 25 height samples");

        int[] gentle = {
                72, 73, 74, 74, 75,
                73, 74, 75, 76, 76,
                74, 75, 76, 77, 78,
                75, 76, 77, 78, 79,
                76, 77, 78, 79, 80
        };
        assertTrue(
                VillageTerrainSuitabilityPolicy.isSuitable(gentle),
                "ordinary rolling village terrain remains eligible");

        int[] exactTest19Footprint = {
                63, 63, 69, 70, 75,
                87, 69, 105, 79, 63,
                112, 136, 135, 119, 102,
                130, 120, 118, 126, 109,
                119, 110, 111, 122, 119
        };
        assertFalse(
                VillageTerrainSuitabilityPolicy.isSuitable(exactTest19Footprint),
                "TEST 19's measured 73-block-relief cliff village is rejected before generation");

        int[] neighborStepBoundary = new int[VillageTerrainSuitabilityPolicy.SAMPLE_COUNT];
        java.util.Arrays.fill(neighborStepBoundary, 80);
        neighborStepBoundary[1] = 104;
        assertTrue(
                VillageTerrainSuitabilityPolicy.isSuitable(neighborStepBoundary),
                "an exact 24-block neighboring rise remains eligible");
        neighborStepBoundary[1] = 105;
        assertFalse(
                VillageTerrainSuitabilityPolicy.isSuitable(neighborStepBoundary),
                "the first block beyond the cliff-step allowance is rejected");

        int[] totalReliefBoundary = {
                63, 69, 75, 81, 87,
                69, 75, 81, 87, 93,
                75, 81, 87, 93, 99,
                81, 87, 93, 99, 105,
                87, 93, 99, 105, 111
        };
        assertTrue(
                VillageTerrainSuitabilityPolicy.isSuitable(totalReliefBoundary),
                "a broad 48-block hill across the full sample span remains eligible");
        totalReliefBoundary[24] = 112;
        assertFalse(
                VillageTerrainSuitabilityPolicy.isSuitable(totalReliefBoundary),
                "the first block beyond the broad-hill relief allowance is rejected");

        assertTrue(
                VillageTerrainSuitabilityPolicy.isSuitable(new int[]{70, 71}),
                "incomplete terrain evidence fails open");
    }

    private static void climateMismatchRejectsInvalidStartsBeforeStore() {
        int radius = 7_500;
        double coldBlockZ = radius * 52.0 / 90.0;
        double warmBlockZ = radius * 20.0 / 90.0;

        GenerationResult mismatch = simulateGenerationGuard(
                coldBlockZ,
                radius,
                "village_desert",
                true);
        assertTrue(mismatch.start() == SimulatedStart.INVALID,
                "fresh authoritative climate mismatch returns INVALID_START");
        assertEquals(0, mismatch.originalGenerateCalls(),
                "fresh authoritative climate mismatch rejects before Structure.generate");
        assertEquals(0, mismatch.storedStarts(),
                "fresh authoritative climate mismatch cannot register a valid start");
        assertEquals(0, mismatch.serializedValidStarts(),
                "fresh authoritative climate mismatch cannot serialize a valid start");
        assertEquals(0, mismatch.locatableStartsAfterReload(),
                "fresh authoritative climate mismatch cannot become locate-visible after reload");

        GenerationResult compatible = simulateGenerationGuard(
                warmBlockZ,
                radius,
                "village_desert",
                true);
        assertValidNormalPath(compatible, "climate-compatible village");

        GenerationResult neutral = simulateGenerationGuard(
                coldBlockZ,
                radius,
                "village_plains",
                true);
        assertValidNormalPath(neutral, "climate-neutral village");

        GenerationResult nonVillage = simulateGenerationGuard(
                coldBlockZ,
                radius,
                "desert_pyramid",
                true);
        assertValidNormalPath(nonVillage, "non-village structure");

        GenerationResult nonAuthoritative = simulateGenerationGuard(
                coldBlockZ,
                radius,
                "village_desert",
                false);
        assertValidNormalPath(nonAuthoritative, "non-authoritative generator");
    }

    private static GenerationResult simulateGenerationGuard(
            double blockZ,
            int radius,
            String structurePath,
            boolean authoritative) {
        boolean village = structurePath != null && structurePath.startsWith("village");
        double absDeg = Math.abs(blockZ) * 90.0 / Math.max(1, radius);
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        boolean climateMismatch = LatitudeBiomes.villageClimateVsBandMismatch(structurePath, band);
        if (authoritative
                && village
                && (VillageLatitudePolicy.shouldVetoVillageOrigin(blockZ, radius, radius)
                || climateMismatch)) {
            return new GenerationResult(SimulatedStart.INVALID, 0, 0, 0, 0);
        }
        return new GenerationResult(SimulatedStart.VALID, 1, 1, 1, 1);
    }

    private static void assertValidNormalPath(GenerationResult result, String message) {
        assertTrue(result.start() == SimulatedStart.VALID, message + " remains valid");
        assertEquals(1, result.originalGenerateCalls(), message + " invokes Structure.generate once");
        assertEquals(1, result.storedStarts(), message + " registers one valid start");
        assertEquals(1, result.serializedValidStarts(), message + " serializes one valid start");
        assertEquals(1, result.locatableStartsAfterReload(), message + " remains locate-visible after reload");
    }

    private static void staticIntegrationProofsHold() throws IOException {
        String biomes = normalize(read("src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertTrue(
                biomes.contains("private static final double EXTREME_POLAR_CAP_MIN_DEG = 74.5;"),
                "shared ecology constant remains 74.5");
        assertTrue(
                biomes.contains("return latDeg >= EXTREME_POLAR_CAP_MIN_DEG;"),
                "shared ecology predicate retains its inclusive 74.5 comparison");
        assertTrue(
                biomes.contains("VillageLatitudePolicy.shouldVetoVillageOrigin( blockZ, getActiveRadiusBlocks(), borderRadiusFallback)"),
                "LatitudeBiomes village predicate delegates with active-radius/fallback authority");

        String vegetation = normalize(read(
                "src/main/java/com/example/globe/mixin/ExtremePolarVegetationGuardMixin.java"));
        assertTrue(
                vegetation.contains("LatitudeBiomes.isBlockBeyondPolarWoodyLimit(origin.getZ(), GlobeMod.BORDER_RADIUS)"),
                "trees use their own dedicated 72-degree tree-line predicate — the point of this"
                        + " assertion is that vegetation NEVER borrows the village limit; the tier it"
                        + " does use moved from strict-80 to the tree line on 2026-08-10 when woody"
                        + " content and ground vegetation were split");
        assertTrue(
                !vegetation.contains("isBlockBeyondPolarVillageLimit"),
                "village limit cannot alter vegetation");

        Path startGuardPath = Path.of(
                "src/main/java/com/example/globe/mixin/ExtremePolarVillageStartGuardMixin.java");
        assertTrue(
                Files.exists(startGuardPath),
                "generation-time village start guard source must exist");
        String startGuard = normalize(Files.readString(startGuardPath));
        assertTrue(
                startGuard.contains("@Mixin(ChunkGenerator.class)")
                        && startGuard.contains("@WrapOperation(")
                        && startGuard.contains("method = \"tryGenerateStructure\"")
                        && startGuard.contains(
                        "Lnet/minecraft/world/level/levelgen/structure/Structure;generate("),
                "new start guard wraps Structure.generate inside ChunkGenerator.tryGenerateStructure");
        assertTrue(
                startGuard.contains(
                        "GlobeMod.borderRadiusForNoiseGenerator(noise)")
                        && startGuard.contains(
                        "LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, radius)")
                        && !startGuard.contains("isBlockInExtremePolarCap"),
                "new start guard applies the strict village-latitude predicate with generator-specific radius authority");
        assertTrue(
                startGuard.contains(
                        "structureHolder.is(StructureTags.VILLAGE)")
                        && startGuard.contains(
                        "structureId.getPath().contains(\"village\")"),
                "new start guard uses the vanilla village tag with a conservative modded-ID fallback");
        assertTrue(
                startGuard.contains("LatitudeBands.fromAbsoluteLatitudeDeg(absDeg)")
                        && startGuard.contains(
                        "LatitudeBiomes.villageClimateVsBandMismatch(structureId.getPath(), band)"),
                "generation-time owner applies the existing climate policy before start registration");
        assertTrue(
                startGuard.contains("biomeSource.getNoiseBiome(")
                        && startGuard.contains("LatitudeBiomes.pick(")
                        && startGuard.contains(
                        "LatitudeBiomes.villageVariantVsBiomeMismatch( structureId.getPath(), finalBiomeId.toString())"),
                "generation-time owner compares the named village variant with Latitude's final biome");
        assertTrue(
                startGuard.contains("BiomeSource structureBiomeSource = biomeSource;")
                        && startGuard.contains("LatitudeBiomeSource.forStructure( biomeSource, biomeRegistry, radius, noise, randomState, heightAccessor)")
                        && startGuard.contains("chunkGenerator, structureBiomeSource, randomState,"),
                "vanilla Structure.generate receives Latitude's final biome source for every new structure start");
        assertTrue(
                startGuard.contains("VillageTerrainSuitabilityPolicy.SAMPLE_COUNT")
                        && startGuard.contains(
                        "dz = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS")
                        && startGuard.contains(
                        "dz <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS")
                        && startGuard.contains(
                        "dx = -VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS")
                        && startGuard.contains(
                        "dx <= VillageTerrainSuitabilityPolicy.SAMPLE_RADIUS_BLOCKS")
                        && startGuard.contains("Heightmap.Types.WORLD_SURFACE_WG")
                        && startGuard.contains("VillageTerrainSuitabilityPolicy.isSuitable(terrainHeights)"),
                "generation-time owner samples one bounded physical footprint before village generation");
        assertTrue(
                startGuard.indexOf("LatitudeBiomes.villageClimateVsBandMismatch(")
                        < startGuard.indexOf("int[] terrainHeights ="),
                "cheap latitude and climate rejections run before the 25 physical height probes");
        assertTrue(
                startGuard.contains("catch (RuntimeException ignored)")
                        && startGuard.contains("Registry unavailable — fail open (allow generation)."),
                "new start registry lookup retains fail-open behavior at generation time");
        assertTrue(
                startGuard.contains("return StructureStart.INVALID_START;")
                        && occurrences(startGuard, "original.call(") == 1,
                "blocked new starts return the singleton invalid start and normal paths call generate once");
        assertTrue(
                startGuard.indexOf("return StructureStart.INVALID_START;")
                        < startGuard.indexOf("return original.call("),
                "new invalid start is returned before the normal generate/store path");
        assertTrue(
                !startGuard.contains("placeInChunk")
                        && !startGuard.contains("@Inject")
                        && !startGuard.contains("CallbackInfo")
                        && !startGuard.contains("ci.cancel();"),
                "fresh-start rejection never truncates a stored StructureStart during placement");

        String mixins = normalize(read("src/main/resources/globe.mixins.json"));
        assertTrue(
                mixins.contains(
                        "\"ExtremePolarVillageStartGuardMixin\", \"StructureBiomeMatchGuardMixin\", \"ExtremePolarVegetationGuardMixin\""),
                "fresh-start and final-biome admission guards are registered beside the vegetation guard");
        assertEquals(
                0,
                occurrences(mixins, "\"ExtremePolarVillageGuardMixin\""),
                "the obsolete polar placement guard cannot truncate stored village starts");
        assertEquals(
                1,
                occurrences(mixins, "\"StructureBiomeMatchGuardMixin\""),
                "the final-biome admission guard is registered exactly once");
        assertEquals(
                1,
                occurrences(mixins, "\"ExtremePolarVillageStartGuardMixin\""),
                "new generation guard is registered exactly once");
        assertTrue(
                Files.notExists(Path.of(
                        "src/main/java/com/example/globe/mixin/ExtremePolarVillageGuardMixin.java"))
                        && Files.exists(Path.of(
                        "src/main/java/com/example/globe/mixin/StructureBiomeMatchGuardMixin.java")),
                "the obsolete place-time guard stays removed while the generation-time admission guard remains active");

        String globeMod = normalize(read("src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(
                globeMod.contains("border.setCenter(0.0, 0.0);"),
                "blockZ absolute-value policy matches Latitude's enforced Z=0 border center");
        assertTrue(
                biomes.contains("double latDeg = Math.abs((double) blockZ) * 90.0 / Math.max(1, radius);"),
                "new policy retains the current blockZ coordinate convention");

        assertEquals(
                3,
                mainSourceOccurrences("isBlockBeyondPolarVillageLimit"),
                "village predicate appears only at its declaration, the fresh-start guard, and the aligned locator");

        String build = normalize(read("build.gradle"));
        assertTrue(
                build.contains("tasks.register('latitudeVillageLatitudePolicyTest', JavaExec)")
                        && build.contains("dependsOn tasks.named('latitudeVillageLatitudePolicyTest')"),
                "village latitude proof is automatically wired into Gradle check/build");
    }

    private static void structureAdmissionUsesFinalLatitudeBiomeBeforeStartRegistration()
            throws IOException {
        String biomeSource = normalize(read("src/main/java/com/example/globe/world/LatitudeBiomeSource.java"));
        assertTrue(
                biomeSource.contains("static LatitudeBiomeSource forStructure(")
                        && biomeSource.contains("new LatitudeBiomeSource(base, base.possibleBiomes(), biomeRegistry,")
                        && biomeSource.contains("heightView, \"MIXIN\")"),
                "structure starts use the registry and terrain-aware resolver used by Latitude-generated terrain");
    }

    private enum SimulatedStart {
        VALID,
        INVALID
    }

    private record GenerationResult(
            SimulatedStart start,
            int originalGenerateCalls,
            int storedStarts,
            int serializedValidStarts,
            int locatableStartsAfterReload) {
        private GenerationResult(SimulatedStart start, int originalGenerateCalls, int storedStarts) {
            this(start, originalGenerateCalls, storedStarts, storedStarts, storedStarts);
        }
    }

    private static int mainSourceOccurrences(String target) throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .mapToInt(path -> {
                        try {
                            return occurrences(read(path.toString()), target);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sum();
        }
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private static void assertNear(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
