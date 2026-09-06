package com.example.globe.world;

import com.example.globe.util.LatitudeBands;
import com.example.globe.util.LatitudeMath;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

public final class SpawnSafetyPolicyTest {
    private static final int TERRAIN_MARGIN = 320;
    private static final int WARNING_DISTANCE = 500;
    private static final int WARNING_PADDING = 64;

    public static void main(String[] args) throws Exception {
        searchBoundsStayInsideTerrainAndWarningMargins();
        spawnLatitudesSitAtTheMidpointOfTheirOwnBand();
        initialSpawnStaysInRequestedLatitudeWithBoundedFallback();
        compactBiomePrefilterFindsIslandMissedByBlindFallback();
        compactCohortIsDeterministicBoundedAndOrdered();
        compactCohortStaysInEveryRequestedZone();
        compactCohortFallsBackWhenClassificationFails();
        validationStopsAtAcceptanceOrNine();
        fallbackCandidatesAreDeterministicBoundedAndValidated();
        hazardousSurfacesAreRejected();
        heightmapPositionIsTheSpawnSpaceAboveGround();
        productionUsesTheValidatedCoordinateAndSurfacePolicy();
        System.out.println("SPAWN_SAFETY_POLICY_TEST_PASS");
    }

    /**
     * Every canonical zone must spawn a player inside the zone they asked for. The retired
     * hand-picked fractions did not: SUBTROPICAL's 0.40 put the target at 36 degrees, a degree
     * past its own 35-degree upper boundary, so a Subtropical request landed in Temperate.
     */
    private static void spawnLatitudesSitAtTheMidpointOfTheirOwnBand() {
        for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
            String zoneKey = band.name();
            double targetDeg = LatitudeMath.spawnFracForZoneKey(zoneKey) * 90.0;
            double midpointDeg = (band.lowDeg() + band.highDeg()) * 0.5;
            assertEquals(
                    midpointDeg,
                    targetDeg,
                    "the " + zoneKey + " spawn target is the midpoint of its own canonical band");
            assertTrue(
                    targetDeg > band.lowDeg() && targetDeg < band.highDeg(),
                    "the " + zoneKey + " spawn target stays strictly inside its own band");
            assertEquals(
                    band,
                    LatitudeBands.fromAbsoluteLatitudeDeg(targetDeg),
                    "the " + zoneKey + " spawn target classifies back as " + zoneKey);
        }

        assertEquals(
                LatitudeBands.Band.TEMPERATE,
                LatitudeBands.fromAbsoluteLatitudeDeg(0.40 * 90.0),
                "the retired SUBTROPICAL fraction really did land a Subtropical request in Temperate");

        double equatorDeg = LatitudeMath.spawnFracForZoneKey("EQUATOR") * 90.0;
        assertEquals(
                LatitudeMath.LatitudeZone.EQUATOR,
                LatitudeMath.zoneForDeg((int) Math.round(equatorDeg)),
                "EQUATOR keeps its own display-only sub-zone fraction");
        assertEquals(
                LatitudeBands.Band.TROPICAL,
                LatitudeBands.fromAbsoluteLatitudeDeg(equatorDeg),
                "the EQUATOR sub-zone still sits inside the canonical Tropical band");

        for (int radius : new int[] {3_750, 5_000, 7_500, 10_000, 15_000, 20_000}) {
            for (LatitudeBands.Band band : LatitudeBands.Band.values()) {
                String zoneKey = band.name();
                int targetZ = (int) Math.round(radius * LatitudeMath.spawnFracForZoneKey(zoneKey));
                for (int hemisphereSign : new int[] {-1, 1}) {
                    assertEquals(
                            zoneKey,
                            LatitudeMath.zoneForRadius(radius, targetZ * hemisphereSign).name(),
                            "a " + zoneKey + " request lands in " + zoneKey
                                    + " at radius " + radius + " in both hemispheres");
                }
            }
        }
    }

    private static void compactBiomePrefilterFindsIslandMissedByBlindFallback() {
        int radius = 10_000;
        int targetZ = 4_720;
        Set<SpawnSafetyPolicy.FallbackCandidate> island = new HashSet<>();
        for (int x : new int[]{64, 96, 128}) {
            for (int z : new int[]{targetZ - 32, targetZ, targetZ + 32}) {
                island.add(new SpawnSafetyPolicy.FallbackCandidate(x, z));
            }
        }

        List<SpawnSafetyPolicy.FallbackCandidate> blindFallback =
                SpawnSafetyPolicy.safeFallbackCandidates(
                        radius,
                        targetZ,
                        TERRAIN_MARGIN,
                        WARNING_DISTANCE,
                        WARNING_PADDING,
                        SpawnSafetyPolicy.FALLBACK_STEP_BLOCKS,
                        SpawnSafetyPolicy.FALLBACK_MAX_RINGS);
        assertFalse(
                blindFallback.stream().anyMatch(island::contains),
                "the scripted island must reproduce the blind fallback miss");

        try {
            Method cohortFactory = SpawnSafetyPolicy.class.getMethod(
                    "compactValidationCohort",
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    BiPredicate.class);
            BiPredicate<Integer, Integer> isLand =
                    (x, z) -> island.contains(new SpawnSafetyPolicy.FallbackCandidate(x, z));
            Object cohort = cohortFactory.invoke(
                    null,
                    radius,
                    targetZ,
                    TERRAIN_MARGIN,
                    WARNING_DISTANCE,
                    WARNING_PADDING,
                    isLand);
            @SuppressWarnings("unchecked")
            List<SpawnSafetyPolicy.FallbackCandidate> candidates =
                    (List<SpawnSafetyPolicy.FallbackCandidate>) cohort.getClass()
                            .getMethod("validationCandidates")
                            .invoke(cohort);
            assertTrue(
                    candidates.stream().anyMatch(island::contains),
                    "compact biome-prefiltered cohort must find the interior island missed by the blind fallback");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "compact biome-prefiltered cohort must find the interior island missed by the blind fallback",
                    e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("compact cohort policy invocation failed", e);
        }
    }

    private static void initialSpawnStaysInRequestedLatitudeWithBoundedFallback() throws IOException {
        SpawnSafetyPolicy.CompactCohort initialCohort =
                SpawnSafetyPolicy.compactValidationCohort(
                        10_000,
                        4_720,
                        TERRAIN_MARGIN,
                        WARNING_DISTANCE,
                        WARNING_PADDING,
                        (x, z) -> true);
        assertTrue(initialCohort.probes().size() <= SpawnSafetyPolicy.MAX_COMPACT_PROBES,
                "initial creation performs at most 85 chunk-free biome probes");
        assertEquals(9, initialCohort.validationCandidates().size(),
                "initial creation terrain-validates one compact nine-coordinate cohort");
        for (SpawnSafetyPolicy.FallbackCandidate candidate : initialCohort.validationCandidates()) {
            double degrees = Math.abs(candidate.z()) * 90.0 / 10_000.0;
            assertTrue(degrees >= 35.0 && degrees < 50.0,
                    "Temperate cohort stays in Temperate latitude rather than returning vanilla to 0 degrees");
        }

        String source = normalize(Files.readString(
                Path.of("src/main/java/com/example/globe/GlobeMod.java")));
        assertTrue(
                source.contains("resolveInitialSpawnChoice(world, pendingZone)"),
                "initial creation uses the dedicated bounded spawn resolver");
        assertTrue(
                source.contains("SpawnSafetyPolicy.compactValidationCohort("),
                "initial creation uses the tested chunk-free compact-cohort policy");
        assertTrue(
                source.contains("(x, z) -> isLandBiome("),
                "the compact policy classifies probes through the real chunk-free biome sampler");
        assertTrue(
                source.contains("return placeSafeY(world, candidate.x(), candidate.z(), false)"),
                "every expensive compact-cohort candidate uses the existing full terrain validator");
        String initialMethod = between(
                source,
                "public static boolean trySetInitialLatitudeSpawn(",
                "private static void placeLatitudeBonusChest(");
        assertTrue(
                initialMethod.contains(
                        "if (world == null || levelData == null || debugWorld || !isGlobeOverworld(world)) { return false; }"),
                "null, debug, and non-Globe initial-spawn preconditions retain their vanilla exits");
        int acceptedRequest = initialMethod.indexOf("if (pendingZone == null) { return false; }");
        assertTrue(acceptedRequest >= 0,
                "ordinary no-pending-zone creation retains its vanilla precondition exit");
        // LAW CHANGED 2026-08-30, and the old pin here is the reason this comment exists. The
        // previous design chose crash-over-wrong-zone: after exhaustion the method was REQUIRED to
        // rethrow InitialSpawnSelectionException rather than return false, so a player never
        // silently spawned outside the selected zone. Live measurement on the certified beta.3 jar
        // falsified that trade: 41/240 create-world attempts crashed (40 seeds x 6 zones; Polar
        // 12/40, default Temperate 5/40), with Latitude alone as well as under the full provider
        // stack. The 26.2 line already ships the replacement, ported here verbatim: contain the
        // failure, warn, delegate to vanilla's own safe-spawn selection, and never crash the
        // server at Create World.
        String afterAcceptance = initialMethod.substring(acceptedRequest
                + "if (pendingZone == null) { return false; }".length());
        assertTrue(
                afterAcceptance.contains("} catch (RuntimeException e) {"),
                "an accepted selected-zone request must CONTAIN resolver failure, not let it escape");
        assertFalse(
                afterAcceptance.contains("throw"),
                "nothing after acceptance may rethrow -- the escaped exception is what crashed 41 of "
                        + "240 measured world creations on the certified beta.3 jar");
        assertTrue(
                afterAcceptance.contains("setSpawnPickerDismissed(true); LOGGER.warn("),
                "the delegation path must dismiss the picker BEFORE returning false, so the first "
                        + "join cannot retry the expensive globe search it just watched fail");
        assertTrue(
                source.contains(
                        "throw new InitialSpawnSelectionException( \"Latitude could not find a safe initial spawn in the selected climate zone after nine bounded attempts.\");"),
                "the resolver still surfaces exhaustion as the dedicated exception -- it is an "
                        + "internal signal now, caught by the wrapper, never a server crash");
        assertTrue(
                source.contains(
                        "private static SpawnChoice resolveSpawnChoice(ServerLevel world, String id) { return resolveSpawnChoice(world, id, Integer.MAX_VALUE, true, true); }"),
                "ordinary non-initial picker resolution retains its existing biome search and fallback route");
        assertFalse(
                source.contains("applySpawnChoice(handler.player, zoneToApply)"),
                "first join must not retry the synchronous globe scan after initial selection");
    }

    private static void compactCohortIsDeterministicBoundedAndOrdered() {
        int radius = 10_000;
        int targetZ = 4_720;
        Set<SpawnSafetyPolicy.FallbackCandidate> land = Set.of(
                new SpawnSafetyPolicy.FallbackCandidate(64, targetZ),
                new SpawnSafetyPolicy.FallbackCandidate(96, targetZ),
                new SpawnSafetyPolicy.FallbackCandidate(128, targetZ));
        AtomicInteger probeCalls = new AtomicInteger();
        BiPredicate<Integer, Integer> classifier = (x, z) -> {
            probeCalls.incrementAndGet();
            return land.contains(new SpawnSafetyPolicy.FallbackCandidate(x, z));
        };

        SpawnSafetyPolicy.CompactCohort cohort = SpawnSafetyPolicy.compactValidationCohort(
                radius,
                targetZ,
                TERRAIN_MARGIN,
                WARNING_DISTANCE,
                WARNING_PADDING,
                classifier);
        assertEquals(cohort.probes().size(), probeCalls.get(),
                "each unique chunk-free probe is classified exactly once");
        assertTrue(cohort.probes().size() <= 85,
                "the clamped and deduplicated 17x5 lattice performs at most 85 biome probes");
        assertEquals(9, cohort.validationCandidates().size(),
                "the selected 3x3 cohort contains at most nine unique targets");
        assertEquals(3, cohort.landCandidateCount(),
                "the land-rich subsection wins over nearer water-only subsections");
        assertEquals(
                cohort,
                SpawnSafetyPolicy.compactValidationCohort(
                        radius,
                        targetZ,
                        TERRAIN_MARGIN,
                        WARNING_DISTANCE,
                        WARNING_PADDING,
                        (x, z) -> land.contains(new SpawnSafetyPolicy.FallbackCandidate(x, z))),
                "compact probe and cohort order is deterministic");

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean reachedBackfill = false;
        long previousLandDistance = Long.MIN_VALUE;
        long previousBackfillDistance = Long.MIN_VALUE;
        for (SpawnSafetyPolicy.FallbackCandidate candidate : cohort.validationCandidates()) {
            minX = Math.min(minX, candidate.x());
            maxX = Math.max(maxX, candidate.x());
            minZ = Math.min(minZ, candidate.z());
            maxZ = Math.max(maxZ, candidate.z());
            long distance = distanceSquared(candidate, 0, targetZ);
            if (land.contains(candidate)) {
                assertFalse(reachedBackfill, "all biome-land candidates precede terrain-only backfill");
                assertTrue(distance >= previousLandDistance,
                        "land candidates use stable nearest-first ordering");
                previousLandDistance = distance;
            } else {
                reachedBackfill = true;
                assertTrue(distance >= previousBackfillDistance,
                        "terrain-only backfill uses stable nearest-first ordering");
                previousBackfillDistance = distance;
            }
        }
        assertTrue(maxX - minX <= 64, "one cohort spans no more than 64 blocks on X");
        assertTrue(maxZ - minZ <= 64, "one cohort spans no more than 64 blocks on Z");
    }

    private static void compactCohortStaysInEveryRequestedZone() {
        int[] radii = {3_750, 5_000, 7_500, 10_000, 15_000, 20_000};
        String[] zones = {"EQUATOR", "TROPICAL", "SUBTROPICAL", "TEMPERATE", "SUBPOLAR", "POLAR"};
        for (int radius : radii) {
            for (String zone : zones) {
                double targetFraction = com.example.globe.util.LatitudeMath.spawnFracForZoneKey(zone);
                int unclampedTargetAbsZ = (int) Math.round(radius * targetFraction);
                int maxInitialAbsZ = Math.max(0, radius - 256 - 500);
                int targetAbsZ = Math.max(0, Math.min(maxInitialAbsZ, unclampedTargetAbsZ));
                for (int hemisphereSign : new int[]{-1, 1}) {
                    int targetZ = targetAbsZ * hemisphereSign;
                    SpawnSafetyPolicy.CompactCohort cohort = SpawnSafetyPolicy.compactValidationCohort(
                            radius,
                            targetZ,
                            TERRAIN_MARGIN,
                            WARNING_DISTANCE,
                            WARNING_PADDING,
                            (x, z) -> true);
                    int safeMaxX = SpawnSafetyPolicy.safeSearchMaxAbsX(
                            radius,
                            TERRAIN_MARGIN,
                            WARNING_DISTANCE,
                            WARNING_PADDING);
                    int minCandidateZ = Integer.MAX_VALUE;
                    int maxCandidateZ = Integer.MIN_VALUE;
                    for (SpawnSafetyPolicy.FallbackCandidate candidate : cohort.validationCandidates()) {
                        minCandidateZ = Math.min(minCandidateZ, candidate.z());
                        maxCandidateZ = Math.max(maxCandidateZ, candidate.z());
                        assertTrue(Math.abs(candidate.x()) <= safeMaxX,
                                "cohort X stays inside terrain and warning bounds for " + zone);
                        assertTrue(Math.abs(candidate.z()) <= radius - TERRAIN_MARGIN,
                                "cohort Z stays inside the terrain bound for " + zone);
                        assertTrue(Math.abs(candidate.z() - targetZ) <= 64,
                                "cohort remains inside the exact requested-zone center envelope for " + zone);
                        assertEquals(
                                zone,
                                com.example.globe.util.LatitudeMath.zoneForRadius(radius, candidate.z()).name(),
                                "every cohort candidate remains in the selected zone in both hemispheres");
                    }
                    assertEquals(
                            zone,
                            com.example.globe.util.LatitudeMath.zoneForRadius(radius, minCandidateZ).name(),
                            "cohort minimum Z remains in the selected zone in both hemispheres");
                    assertEquals(
                            zone,
                            com.example.globe.util.LatitudeMath.zoneForRadius(radius, maxCandidateZ).name(),
                            "cohort maximum Z remains in the selected zone in both hemispheres");
                }
            }
        }
    }

    private static void compactCohortFallsBackWhenClassificationFails() {
        SpawnSafetyPolicy.CompactCohort fallback = SpawnSafetyPolicy.compactValidationCohort(
                10_000,
                4_720,
                TERRAIN_MARGIN,
                WARNING_DISTANCE,
                WARNING_PADDING,
                (x, z) -> {
                    throw new IllegalStateException("scripted classifier failure");
                });
        assertTrue(fallback.terrainOnlyFallback(),
                "classification failure selects the central terrain-only cohort");
        assertEquals(0, fallback.probes().size(),
                "failed biome classification does not claim a completed probe plan");
        assertEquals(9, fallback.validationCandidates().size(),
                "classification failure still supplies one bounded central 3x3 cohort");
    }

    private static void validationStopsAtAcceptanceOrNine() {
        List<SpawnSafetyPolicy.FallbackCandidate> candidates =
                SpawnSafetyPolicy.centralCompactValidationCohort(
                                10_000,
                                4_720,
                                TERRAIN_MARGIN,
                                WARNING_DISTANCE,
                                WARNING_PADDING)
                        .validationCandidates();
        AtomicInteger acceptedCalls = new AtomicInteger();
        SpawnSafetyPolicy.FallbackCandidate fourth = SpawnSafetyPolicy.firstValidatedCandidate(
                        candidates,
                        candidate -> acceptedCalls.incrementAndGet() == 4 ? candidate : null)
                .orElseThrow(() -> new AssertionError("the scripted fourth target must be accepted"));
        assertEquals(candidates.get(3), fourth,
                "the validator returns the first accepted terrain target");
        assertEquals(4, acceptedCalls.get(),
                "accepting the fourth target makes exactly four expensive calls");

        AtomicInteger rejectedCalls = new AtomicInteger();
        assertTrue(
                SpawnSafetyPolicy.firstValidatedCandidate(
                                candidates,
                                candidate -> {
                                    rejectedCalls.incrementAndGet();
                                    return null;
                                })
                        .isEmpty(),
                "an always-rejecting validator returns no accepted target");
        assertEquals(9, rejectedCalls.get(),
                "an exhausted compact cohort makes exactly nine expensive calls");
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
                        "BlockPos spawn = world.getHeightmapPos( Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, world.getMinBuildHeight(), z)); BlockPos ground = spawn.below();"),
                "Minecraft's heightmap result is the first open spawn block, so ground is one block below it");
        assertFalse(
                source.contains(
                        "BlockPos ground = world.getHeightmapPos( Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, world.getMinBuildHeight(), z)); BlockPos spawn = ground.above();"),
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

    private static long distanceSquared(
            SpawnSafetyPolicy.FallbackCandidate candidate,
            int centerX,
            int centerZ) {
        long dx = (long) candidate.x() - centerX;
        long dz = (long) candidate.z() - centerZ;
        return dx * dx + dz * dz;
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("source markers for focused production check were not found");
        }
        return source.substring(startIndex, endIndex);
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

    private static void assertEquals(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0e-9) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
