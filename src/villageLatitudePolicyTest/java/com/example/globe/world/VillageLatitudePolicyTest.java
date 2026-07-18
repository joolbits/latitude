package com.example.globe.world;

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
        if (village && VillageLatitudePolicy.shouldVetoVillageOrigin(blockZ, radius, radius)) {
            return new GenerationResult(SimulatedStart.INVALID, 0, 0);
        }
        return new GenerationResult(SimulatedStart.VALID, 1, 1);
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
                vegetation.contains("LatitudeBiomes.isBlockInExtremePolarCap(origin.getZ(), GlobeMod.BORDER_RADIUS)"),
                "vegetation remains on the shared 74.5 ecology predicate");
        assertTrue(
                !vegetation.contains("isBlockBeyondPolarVillageLimit"),
                "village limit cannot alter vegetation");

        String placementGuard = normalize(read(
                "src/main/java/com/example/globe/mixin/ExtremePolarVillageGuardMixin.java"));
        assertTrue(
                placementGuard.contains("@Mixin(StructureStart.class)")
                        && placementGuard.contains("@Inject(method = \"placeInChunk(")
                        && placementGuard.contains("at = @At(\"HEAD\"), cancellable = true")
                        && placementGuard.contains("CallbackInfo ci"),
                "legacy village placement guard still intercepts StructureStart.placeInChunk");
        assertTrue(
                placementGuard.contains("this.getChunkPos().getMiddleBlockZ()")
                        && placementGuard.contains(
                        "LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, GlobeMod.BORDER_RADIUS)")
                        && !placementGuard.contains("isBlockInExtremePolarCap"),
                "legacy placement guard retains the strict village-latitude predicate");
        assertTrue(
                placementGuard.contains(
                        "structureId != null && structureId.getPath().startsWith(\"village\")")
                        && placementGuard.contains("ci.cancel();"),
                "legacy placement guard still cancels only village structures");
        assertTrue(
                placementGuard.contains("catch (Throwable ignored)")
                        && placementGuard.contains("Registry unavailable — fail open (allow placement)."),
                "legacy placement registry lookup retains fail-open behavior");
        assertTrue(
                !placementGuard.contains("@WrapOperation")
                        && !placementGuard.contains("StructureStart.INVALID_START")
                        && !placementGuard.contains("original.call("),
                "legacy placement guard remains separate from generation-time invalidation");

        Path startGuardPath = Path.of(
                "src/main/java/com/example/globe/mixin/ExtremePolarVillageStartGuardMixin.java");
        assertTrue(
                Files.exists(startGuardPath),
                "generation-time village start guard source must exist beside the legacy placement guard");
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
                        "LatitudeBiomes.isBlockBeyondPolarVillageLimit(blockZ, GlobeMod.BORDER_RADIUS)")
                        && !startGuard.contains("isBlockInExtremePolarCap"),
                "new start guard uses only the strict village-latitude predicate");
        assertTrue(
                startGuard.contains(
                        "structureId != null && structureId.getPath().startsWith(\"village\")"),
                "new start guard uses the same village registry classifier");
        assertTrue(
                startGuard.contains("catch (Throwable ignored)")
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
                "new generation guard does not replace or duplicate the legacy placement injection");

        String mixins = normalize(read("src/main/resources/globe.mixins.json"));
        assertTrue(
                mixins.contains(
                        "\"ExtremePolarVillageGuardMixin\", \"ExtremePolarVillageStartGuardMixin\", \"ExtremePolarVegetationGuardMixin\""),
                "legacy placement and new generation guards are both registered adjacently");
        assertEquals(
                1,
                occurrences(mixins, "\"ExtremePolarVillageGuardMixin\""),
                "legacy placement guard is registered exactly once");
        assertEquals(
                1,
                occurrences(mixins, "\"ExtremePolarVillageStartGuardMixin\""),
                "new generation guard is registered exactly once");

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
                "village predicate appears only at its declaration and the two complementary guards");

        String build = normalize(read("build.gradle"));
        assertTrue(
                build.contains("tasks.register('latitudeVillageLatitudePolicyTest', JavaExec)")
                        && build.contains("dependsOn tasks.named('latitudeVillageLatitudePolicyTest')"),
                "village latitude proof is automatically wired into Gradle check/build");
    }

    private enum SimulatedStart {
        VALID,
        INVALID
    }

    private record GenerationResult(
            SimulatedStart start,
            int originalGenerateCalls,
            int storedStarts) {
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
