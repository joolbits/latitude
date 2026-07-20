package com.example.globe.world;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PolarFoliagePolicyTest {
    private static final double EPSILON = 0.0000001;

    public static void main(String[] args) throws Exception {
        boundaryIsStrictAndSymmetric();
        activeRadiusOverridesFallback();
        foliageClassificationPreservesBerriesAndFailsOpen();
        staticIntegrationProofsHold();
        System.out.println("POLAR_FOLIAGE_POLICY_TEST_PASS");
    }

    private static void boundaryIsStrictAndSymmetric() {
        for (int radius : new int[]{3_750, 5_000, 7_500, 10_000, 15_000, 20_000}) {
            for (int hemisphere : new int[]{-1, 1}) {
                double z79Point9 = hemisphere * radius * 79.9 / 90.0;
                double z80 = hemisphere * radius * 80.0 / 90.0;
                double z80Point1 = hemisphere * radius * 80.1 / 90.0;

                assertFalse(
                        PolarFoliagePolicy.isBeyondLimit(z79Point9, radius, 1),
                        "79.9 degrees retains foliage");
                assertFalse(
                        PolarFoliagePolicy.isBeyondLimit(z80, radius, 1),
                        "exactly 80 degrees retains foliage");
                assertTrue(
                        PolarFoliagePolicy.isBeyondLimit(z80Point1, radius, 1),
                        "80.1 degrees rejects foliage");
                assertNear(
                        80.1,
                        PolarFoliagePolicy.absoluteLatitudeDegrees(z80Point1, radius, 1),
                        "reported latitude matches the boundary input");
            }
        }
    }

    private static void activeRadiusOverridesFallback() {
        assertFalse(
                PolarFoliagePolicy.isBeyondLimit(4_000.0, 5_000, 3_750),
                "active radius wins over fallback");
        assertTrue(
                PolarFoliagePolicy.isBeyondLimit(4_000.0, 0, 3_750),
                "fallback covers early worldgen");
    }

    private static void foliageClassificationPreservesBerriesAndFailsOpen() {
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(true, true, true),
                "sweet berry bushes remain eligible beyond 80 degrees");
        assertTrue(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(true, true, false),
                "ordinary foliage is rejected beyond 80 degrees");
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(true, false, false),
                "non-foliage simple-block features fail open");
        assertFalse(
                PolarFoliagePolicy.shouldSuppressSimpleBlock(false, true, false),
                "foliage remains eligible through exactly 80 degrees");
    }

    private static void staticIntegrationProofsHold() throws Exception {
        String biomes = normalize(read(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java"));
        assertTrue(
                biomes.contains("PolarFoliagePolicy.isBeyondLimit("),
                "LatitudeBiomes exposes the dedicated foliage boundary");
        assertTrue(
                biomes.contains("EXTREME_POLAR_CAP_MIN_DEG = 74.5"),
                "the separate 74.5-degree biome ecology clamp remains unchanged");

        String treeGuard = normalize(read(
                "src/main/java/com/example/globe/mixin/ExtremePolarVegetationGuardMixin.java"));
        assertTrue(
                treeGuard.contains("LatitudeBiomes.isBlockBeyondPolarFoliageLimit("),
                "tree generation uses the dedicated strict-80 foliage boundary");
        assertGeneratorGatePrecedesSuppression(treeGuard, "tree");

        String simpleGuard = normalize(read(
                "src/main/java/com/example/globe/mixin/ExtremePolarSimpleFoliageGuardMixin.java"));
        assertTrue(
                simpleGuard.contains("@Mixin(SimpleBlockFeature.class)")
                        && simpleGuard.contains("@ModifyExpressionValue(")
                        && simpleGuard.contains("BlockStateProvider;getOptionalState"),
                "simple foliage is filtered after one provider sample without consuming RNG twice");
        assertTrue(
                !simpleGuard.contains("@Redirect(")
                        && occurrences(simpleGuard, "getOptionalState") == 1,
                "simple foliage modifies exactly the sampled provider result instead of redirecting or resampling");
        assertTrue(
                simpleGuard.contains("Blocks.SWEET_BERRY_BUSH")
                        && simpleGuard.contains("PolarFoliagePolicy.shouldSuppressSimpleBlock("),
                "simple-block interception explicitly preserves berries");
        assertTrue(
                simpleGuard.contains("POLAR_FOLIAGE")
                        && simpleGuard.contains("sampledState.is(POLAR_FOLIAGE)"),
                "runtime classification delegates to the extensible custom foliage tag");
        assertGeneratorGatePrecedesSuppression(simpleGuard, "simple foliage");

        String foliageTag = read(
                "src/main/resources/data/globe/tags/block/polar_foliage.json");
        for (String nestedTag : new String[]{
                "#minecraft:flowers",
                "#minecraft:saplings",
                "#minecraft:leaves",
                "#minecraft:logs",
                "#minecraft:crops"}) {
            assertTrue(foliageTag.contains("\"" + nestedTag + "\""),
                    "custom foliage tag nests " + nestedTag);
        }
        for (String block : new String[]{
                "minecraft:short_grass",
                "minecraft:tall_grass",
                "minecraft:short_dry_grass",
                "minecraft:tall_dry_grass",
                "minecraft:fern",
                "minecraft:large_fern",
                "minecraft:vine",
                "minecraft:wildflowers",
                "minecraft:bush",
                "minecraft:brown_mushroom",
                "minecraft:hanging_roots",
                "minecraft:crimson_roots",
                "minecraft:warped_roots",
                "minecraft:nether_sprouts",
                "minecraft:moss_carpet",
                "minecraft:small_dripleaf",
                "minecraft:lily_pad",
                "minecraft:leaf_litter",
                "minecraft:pumpkin",
                "minecraft:melon"}) {
            assertTrue(foliageTag.contains("\"" + block + "\""),
                    "custom foliage tag includes " + block);
        }
        assertTrue(!foliageTag.contains("minecraft:sweet_berry_bush"),
                "sweet berry bush is not directly classified as suppressible polar foliage");

        String mixins = read("src/main/resources/globe.mixins.json");
        assertTrue(
                mixins.contains("\"ExtremePolarVegetationGuardMixin\"")
                        && mixins.contains("\"ExtremePolarSimpleFoliageGuardMixin\""),
                "tree and simple-foliage guards are both registered");
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

    private static void assertGeneratorGatePrecedesSuppression(String guard, String label) {
        int generatorGate = guard.indexOf("context.chunkGenerator()");
        int suppression = guard.indexOf("LatitudeBiomes.isBlockBeyondPolarFoliageLimit(");
        assertTrue(
                generatorGate >= 0
                        && guard.contains("instanceof NoiseBasedChunkGenerator noise")
                        && guard.contains("!GlobeMod.shouldApplyLatitudeWorldgen(noise)")
                        && suppression > generatorGate,
                label + " must fail open for non-Latitude generators before checking latitude");
    }

    private static void assertNear(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
