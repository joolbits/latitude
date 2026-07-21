package com.example.globe.world;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldgenAuthorityPolicyTest {
    public static void main(String[] args) throws Exception {
        contextLifecycleIsExplicitAndClearsAllAuthority();
        ordinarySettersDoNotActivateGlobalGuards();
        generationScopeIsDimensionIsolatedAndNestSafe();
        generationScopeCleansUpOnFailureAndAcrossThreads();
        registeredHookIntegrationIsClosed();
        System.out.println("WORLDGEN_AUTHORITY_POLICY_TEST_PASS");
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
                "ExtremePolarVillageGuardMixin.java",
                "ExtremePolarVillageStartGuardMixin.java",
                "TreeLineVegetationGuardMixin.java",
                "NoiseChunkGeneratorCarveMixin.java",
                "ExtremePolarVegetationGuardMixin.java",
                "ExtremePolarSimpleFoliageGuardMixin.java",
                "StructureBiomeMatchGuardMixin.java"}) {
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

    private static void assertNull(Object actual, String message) {
        if (actual != null) throw new AssertionError(message + ": expected null, actual=" + actual);
    }

    private static void assertNotNull(Object actual, String message) {
        if (actual == null) throw new AssertionError(message + ": expected non-null");
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
