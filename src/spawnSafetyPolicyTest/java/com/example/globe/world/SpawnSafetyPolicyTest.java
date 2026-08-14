package com.example.globe.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SpawnSafetyPolicyTest {
    private static final int TERRAIN_MARGIN = 320;
    private static final int WARNING_DISTANCE = 500;
    private static final int WARNING_PADDING = 64;

    public static void main(String[] args) throws Exception {
        searchBoundsStayInsideTerrainAndWarningMargins();
        initialSpawnStaysInRequestedLatitudeWithBoundedFallback();
        fallbackCandidatesAreDeterministicBoundedAndValidated();
        hazardousSurfacesAreRejected();
        heightmapPositionIsTheSpawnSpaceAboveGround();
        productionUsesTheValidatedCoordinateAndSurfacePolicy();
        System.out.println("SPAWN_SAFETY_POLICY_TEST_PASS");
    }

    private static void initialSpawnStaysInRequestedLatitudeWithBoundedFallback() throws IOException {
        assertEquals(
                0,
                SpawnSafetyPolicy.INITIAL_SPAWN_TERRAIN_VALIDATION_BUDGET,
                "initial creation performs no speculative FULL-chunk terrain validation");
        assertEquals(
                0,
                SpawnSafetyPolicy.maximumInitialSpawnChunkLoadCalls(
                        SpawnSafetyPolicy.INITIAL_SPAWN_TERRAIN_VALIDATION_BUDGET),
                "the initial biome-targeted choice cannot synchronously generate a remote chunk");

        List<SpawnSafetyPolicy.FallbackCandidate> initialFallback =
                SpawnSafetyPolicy.safeFallbackCandidates(
                        10_000,
                        4_720,
                        TERRAIN_MARGIN,
                        WARNING_DISTANCE,
                        WARNING_PADDING,
                        SpawnSafetyPolicy.FALLBACK_STEP_BLOCKS,
                        SpawnSafetyPolicy.FALLBACK_MAX_RINGS);
        assertEquals(9, initialFallback.size(),
                "initial fallback remains the bounded center plus one eight-point ring");
        assertEquals(9, SpawnSafetyPolicy.INITIAL_SPAWN_TERRAIN_VALIDATION_BUDGET
                        + initialFallback.size(),
                "initial creation generates only the bounded fallback destination candidates");
        for (SpawnSafetyPolicy.FallbackCandidate candidate : initialFallback) {
            double degrees = Math.abs(candidate.z()) * 90.0 / 10_000.0;
            assertTrue(degrees >= 35.0 && degrees < 50.0,
                    "Temperate fallback stays in Temperate latitude rather than returning vanilla to 0 degrees");
        }

        String source = normalize(Files.readString(
                Path.of("src/main/java/com/example/globe/GlobeMod.java")));
        assertTrue(
                source.contains("resolveInitialSpawnChoice(world, pendingZone)"),
                "initial creation uses the dedicated bounded spawn resolver");
        assertTrue(
                source.contains(
                        "SpawnSafetyPolicy.INITIAL_SPAWN_TERRAIN_VALIDATION_BUDGET, false, true"),
                "initial creation retains the selected latitude through its bounded safe fallback without teleport-neighbor preload");
        int initialBudgetGuard = source.indexOf(
                "if (terrainValidationAttempts >= Math.max(0, terrainValidationBudget)) { return null; }");
        int speculativeTerrainLoad = source.indexOf(
                "BlockPos candidate = placeSafeY(world, x, z, prepareTeleportNeighbors);");
        assertTrue(
                initialBudgetGuard >= 0
                        && speculativeTerrainLoad >= 0
                        && initialBudgetGuard < speculativeTerrainLoad,
                "the zero initial budget returns before the first FULL-chunk terrain validation");
        assertTrue(
                source.contains("findSafeFallbackSpawn(world, radius, targetZ, prepareTeleportNeighbors)"),
                "an exhausted initial candidate uses only the terrain-validated fallback at the requested latitude");
        assertFalse(
                source.contains("applySpawnChoice(handler.player, zoneToApply)"),
                "first join must not retry the synchronous globe scan after vanilla takes over");
    }

    private static void fallbackCandidatesAreDeterministicBoundedAndValidated() throws IOException {
        int radius = 3_750;
        int safeMaxX = SpawnSafetyPolicy.safeSearchMaxAbsX(
                radius,
                TERRAIN_MARGIN,
                WARNING_DISTANCE,
                WARNING_PADDING);
        int safeMaxZ = radius - TERRAIN_MARGIN;
        List<SpawnSafetyPolicy.FallbackCandidate> productionCandidates =
                SpawnSafetyPolicy.safeFallbackCandidates(
                        radius,
                        1_900,
                        TERRAIN_MARGIN,
                        WARNING_DISTANCE,
                        WARNING_PADDING,
                        SpawnSafetyPolicy.FALLBACK_STEP_BLOCKS,
                        SpawnSafetyPolicy.FALLBACK_MAX_RINGS);
        assertEquals(
                9,
                productionCandidates.size(),
                "production fallback is limited to the center plus one eight-point ring");
        assertEquals(
                17,
                SpawnSafetyPolicy.maximumFallbackChunkLoadCalls(
                        productionCandidates.size(),
                        SpawnSafetyPolicy.SPAWN_PREPARATION_NEIGHBOR_RADIUS_CHUNKS),
                "production fallback makes at most nine validation loads plus eight final preparation loads");

        List<SpawnSafetyPolicy.FallbackCandidate> candidates =
                SpawnSafetyPolicy.safeFallbackCandidates(
                        radius,
                        1_900,
                        TERRAIN_MARGIN,
                        WARNING_DISTANCE,
                        WARNING_PADDING,
                        192,
                        8);

        assertTrue(!candidates.isEmpty(), "fallback search must have bounded candidates");
        assertEquals(0, candidates.get(0).x(), "fallback search starts at the central X");
        assertEquals(1_900, candidates.get(0).z(), "fallback search starts at the requested latitude");
        assertEquals(
                candidates,
                SpawnSafetyPolicy.safeFallbackCandidates(
                        radius,
                        1_900,
                        TERRAIN_MARGIN,
                        WARNING_DISTANCE,
                        WARNING_PADDING,
                        192,
                        8),
                "fallback candidate order is deterministic");
        for (SpawnSafetyPolicy.FallbackCandidate candidate : candidates) {
            assertTrue(
                    Math.abs(candidate.x()) <= safeMaxX,
                    "fallback X remains outside the east/west warning zone");
            assertTrue(
                    Math.abs(candidate.z()) <= safeMaxZ,
                    "fallback Z remains inside the terrain margin");
        }

        String source = normalize(Files.readString(
                Path.of("src/main/java/com/example/globe/GlobeMod.java")));
        assertFalse(
                source.contains("new BlockPos(0, world.getSeaLevel() + 2, targetZ)"),
                "an unchecked sea-level coordinate must never be returned as Latitude's spawn");
        assertTrue(
                source.contains("findSafeFallbackSpawn(world, radius, targetZ, prepareTeleportNeighbors)"),
                "no-candidate and biome-probe failures use the bounded safe fallback search");
        assertTrue(
                source.contains("placeSafeY( world, candidate.x(), candidate.z(), prepareTeleportNeighbors)"),
                "every deterministic fallback coordinate is terrain-validated");
        assertTrue(
                source.contains(
                        "SpawnSafetyPolicy.FALLBACK_STEP_BLOCKS, SpawnSafetyPolicy.FALLBACK_MAX_RINGS"),
                "production uses the tested nine-candidate fallback bound");
        assertTrue(
                source.indexOf("loadSpawnTargetChunk(world, x, z)")
                        < source.indexOf("loadSpawnTargetNeighborRing(world, x, z)"),
                "neighbor chunks are prepared only after the candidate column passes validation");
        assertTrue(
                source.contains("throw new IllegalStateException("),
                "Latitude declines to return a spawn when no terrain-validated coordinate exists");
    }

    private static void searchBoundsStayInsideTerrainAndWarningMargins() {
        int[] radii = {3_750, 5_000, 7_500, 10_000, 15_000, 20_000};
        for (int radius : radii) {
            int actual = SpawnSafetyPolicy.safeSearchMaxAbsX(
                    radius,
                    TERRAIN_MARGIN,
                    WARNING_DISTANCE,
                    WARNING_PADDING);
            int terrainLimit = Math.max(0, radius - TERRAIN_MARGIN);
            int warningLimit = Math.max(0, radius - WARNING_DISTANCE - WARNING_PADDING);
            assertEquals(
                    Math.min(terrainLimit, warningLimit),
                    actual,
                    "search bound uses the stricter safety margin at radius " + radius);
            assertTrue(
                    actual <= warningLimit,
                    "every sampled X remains outside the east/west warning zone at radius " + radius);
        }
    }

    private static void hazardousSurfacesAreRejected() {
        for (String id : new String[]{
                "minecraft:magma_block",
                "minecraft:cactus",
                "minecraft:powder_snow",
                "minecraft:campfire",
                "minecraft:soul_campfire",
                "minecraft:pointed_dripstone",
                "minecraft:fire",
                "minecraft:soul_fire",
                "minecraft:wither_rose",
                "minecraft:sweet_berry_bush"}) {
            assertTrue(
                    SpawnSafetyPolicy.isDangerousSurfaceId(id),
                    id + " must not be accepted beneath a new player");
        }
        for (String id : new String[]{
                "minecraft:grass_block",
                "minecraft:sand",
                "minecraft:stone"}) {
            assertFalse(
                    SpawnSafetyPolicy.isDangerousSurfaceId(id),
                    id + " is not intrinsically hazardous");
        }
        assertFalse(
                SpawnSafetyPolicy.isDangerousSurfaceId("example:unknown"),
                "unknown provider blocks remain fail-open after the sturdy-surface check");
    }

    private static void heightmapPositionIsTheSpawnSpaceAboveGround() throws IOException {
        String source = normalize(Files.readString(
                Path.of("src/main/java/com/example/globe/GlobeMod.java")));
        assertTrue(
                source.contains(
                        "BlockPos spawn = world.getHeightmapPos( Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, world.getMinY(), z)); BlockPos ground = spawn.below();"),
                "Minecraft's heightmap result is the first open spawn block, so ground is one block below it");
        assertFalse(
                source.contains(
                        "BlockPos ground = world.getHeightmapPos( Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, world.getMinY(), z)); BlockPos spawn = ground.above();"),
                "the first open heightmap block must never be tested as if it were sturdy ground");
    }

    private static void productionUsesTheValidatedCoordinateAndSurfacePolicy() throws IOException {
        String source = normalize(Files.readString(
                Path.of("src/main/java/com/example/globe/GlobeMod.java")));
        assertTrue(
                source.contains(
                        "SpawnSafetyPolicy.safeSearchMaxAbsX( borderHalf, margin, EW_WARNING_DISTANCE_BLOCKS, EW_SPAWN_PADDING_BLOCKS)"),
                "spawn search samples only already-safe east/west coordinates");
        assertFalse(
                source.contains("clampSpawnAwayFromEwWarning(spawnPos, radius)"),
                "validated coordinates are not shifted to a different unvalidated terrain column");
        assertTrue(
                source.contains("SpawnSafetyPolicy.isDangerousSurfaceId(groundBlockId.toString())"),
                "ground hazards are checked by the tested policy");
        assertTrue(
                source.contains("groundState.isFaceSturdy(world, ground, Direction.UP)"),
                "spawn ground must support the player");
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
