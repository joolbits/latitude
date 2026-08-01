package com.example.globe.world;

import com.example.globe.core.CaveDropTrap;
import com.example.globe.dev.CaveDropTrapFullChunkAudit;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused adapter checks around the pure cave-trap geometry and transaction law. */
class CaveDropTrapFeatureContractTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void caveEnclosureUsesMaintainedFeatureStageSurfaceHeightmap()
            throws ReflectiveOperationException {
        Field heightmap = CaveDropTrapFeature.class
                .getDeclaredField("FEATURE_STAGE_SURFACE_HEIGHTMAP");
        heightmap.setAccessible(true);
        assertEquals(Heightmap.Types.WORLD_SURFACE, heightmap.get(null));
        assertNotEquals(Heightmap.Types.WORLD_SURFACE_WG, heightmap.get(null),
                "the stale world-generation heightmap cannot certify a feature-stage cave");
    }

    @Test
    void censusModeIsDefaultOffNoWriteAndUsesTheExactProductionEnvelope() throws Exception {
        Field observer = CaveDropTrapFeature.class.getDeclaredField("censusCallback");
        observer.setAccessible(true);
        CaveDropTrapFeature.setCensusCallback(null);
        assertNull(observer.get(null), "normal cave generation has no census observer");

        Method censusOnly = CaveDropTrapFeature.class.getDeclaredMethod(
                "censusOnly", Consumer.class);
        censusOnly.setAccessible(true);
        assertEquals(false, censusOnly.invoke(null, new Object[] {null}),
                "the null/default observer cannot divert production generation");
        Consumer<CaveDropTrapFeature.CensusSnapshot> observerCallback = ignored -> { };
        assertEquals(true, censusOnly.invoke(null, observerCallback),
                "only the explicit development observer activates the no-write branch");

        String featureResource = "/" + CaveDropTrapFeature.class.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream stream = CaveDropTrapFeatureContractTest.class
                .getResourceAsStream(featureResource)) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        var diagnosis = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("envelopeDiagnosis"))
                .findFirst().orElseThrow();
        Set<String> diagnosisCalls = diagnosis.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(diagnosisCalls.containsAll(Set.of(
                        "requiredSolidVoxels",
                        "requiredAirVoxels",
                        "censusTemplatePosition",
                        "classifyCensusSolidDefect",
                        "airWithoutEntity",
                        "insideOwnerInset")),
                "census diagnostics must inspect the real EmbeddedRibbonPlan and production adapters");

        var place = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("place"))
                .findFirst().orElseThrow();
        Set<String> placeCalls = place.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(placeCalls.containsAll(Set.of(
                        "censusOnly", "shouldTrapPatch", "applyScene")),
                "the census guard is present before the ordinary fraction/reward/apply production path");
    }

    @Test
    void passACensusIsDefaultOffNoWriteAndCallsTheSharedPredicateAndSelectors()
            throws Exception {
        Field observer = CaveDropTrapFeature.class.getDeclaredField("passACensusCallback");
        observer.setAccessible(true);
        CaveDropTrapFeature.setPassACensusCallback(null);
        assertNull(observer.get(null), "normal generation has no Pass-A observer");
        Consumer<CaveDropTrapFeature.PassACensusSnapshot> callback = ignored -> { };
        CaveDropTrapFeature.setPassACensusCallback(callback);
        assertEquals(callback, observer.get(null));
        CaveDropTrapFeature.setPassACensusCallback(null);

        String featureResource = "/" + CaveDropTrapFeature.class.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream stream = CaveDropTrapFeatureContractTest.class
                .getResourceAsStream(featureResource)) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        var census = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("scanPassAChunk"))
                .findFirst().orElseThrow();
        Set<String> censusCalls = census.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.owner().asInternalName() + "#" + call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(censusCalls.contains(
                        "com/example/globe/world/CaveDropTrapFeature$ConformalPassageThroatSource"
                                + "#scan"),
                "Pass A acquires candidates from the conformal passage source");
        assertTrue(censusCalls.stream()
                        .map(call -> call.substring(call.indexOf('#') + 1))
                        .collect(Collectors.toSet())
                        .containsAll(Set.of(
                                "evaluatePassAWithTrace",
                                "passARewardPreflight",
                                "runPassASelectorAssignments")),
                "Pass A measures the shared planner/predicate and delegates to its real selector seam");
        assertFalse(censusCalls.stream().anyMatch(
                        call -> call.endsWith("#planRoofedGalleryThroat")),
                "the census evaluates each candidate's own passage-fitted plan, not the fixed stamp");
        var selector = classModel.methods().stream()
                .filter(method -> method.methodName()
                        .equalsString("runPassASelectorAssignments"))
                .findFirst().orElseThrow();
        Set<String> selectorCalls = selector.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(selectorCalls.containsAll(Set.of(
                        "nextFloat", "predictRoofedThroatOutcome", "recordPrediction")),
                "the diagnostic seam owns the actual RNG read and production prediction call");
        assertFalse(selectorCalls.contains("applyScene"),
                "selector proof remains read-only");
        var tracedPredicate = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("evaluatePassAWithTrace"))
                .findFirst().orElseThrow();
        List<String> tracedCalls = tracedPredicate.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .toList();
        assertEquals(
                1,
                tracedCalls.stream()
                        .filter("evaluateRoofedGalleryThroat"::equals)
                        .count(),
                "the tracing adapter calls the exact shared predicate once");
        assertFalse(tracedCalls.contains("applyScene"),
                "first-witness tracing remains read-only");
        var rewardPreflight = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("passARewardPreflight"))
                .findFirst().orElseThrow();
        Set<String> rewardCalls = rewardPreflight.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(rewardCalls.containsAll(Set.of(
                        "materializePassAThroat",
                        "ordinaryScene", "floodedScene", "prospectorScene")),
                "Pass A calls the exact existing read-only production reward planners");
        assertFalse(rewardCalls.contains("applyScene"),
                "Pass-A real-planner preflight must never apply its planned writes");

        var flooded = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("floodedScene"))
                .findFirst().orElseThrow();
        Set<String> floodedCalls = flooded.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(floodedCalls.containsAll(Set.of(
                        "planDeepFloodedLanding", "floodedGalleryQualifies",
                        "supportedNaturalMass", "poolHasSolidBoundary",
                        "planFloodedEscape", "floodedPath",
                        "exposedOreTargets", "planReward")),
                "the shared flooded planner retains depth, gallery, shore/support, boundary, "
                        + "escape, entry-route, ore-budget, and selector preconditions");
        var prospector = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("prospectorScene"))
                .findFirst().orElseThrow();
        Set<String> prospectorCalls = prospector.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(prospectorCalls.containsAll(Set.of(
                        "planReward", "findProspectorShelf",
                        "replaceRequiredGalleryAirWrite")),
                "the shared prospector planner retains content, shelf/routes, and replacement checks");

        var place = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("place"))
                .findFirst().orElseThrow();
        List<String> placeCalls = place.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .toList();
        assertTrue(placeCalls.indexOf("scanPassAChunk") >= 0);
        assertTrue(placeCalls.indexOf("scanPassAChunk") < placeCalls.indexOf("applyScene"),
                "the explicit Pass-A callback returns from its branch before production writes");
    }

    @Test
    void naturalCaveBoundsIncludeFormerlyMissedFloorsAcceptedByTheSharedPredicate() {
        for (int floorY : List.of(7, 101)) {
            CaveDropTrapFeature.NaturalCaveFloorBounds bounds =
                    CaveDropTrapFeature.NaturalCaveAnchorSource.floorBounds(
                            -64, 320, CaveDropTrap.RibbonOrientation.NORTH);
            assertTrue(bounds.contains(floorY),
                    "the build-safe source includes floor Y=" + floorY);
            CaveDropTrap.RoofedGalleryThroatPlan plan =
                    CaveDropTrap.planRoofedGalleryThroat(
                            floorY, CaveDropTrap.RibbonOrientation.NORTH);
            CaveDropTrapFeature.PassATracedEvaluation traced = tracedEvaluation(
                    plan, new BlockPos(4, floorY, 5), Map.of(), Set.of(), false);
            assertTrue(traced.evaluation().ordinaryFeasible(),
                    "the unchanged shared predicate accepts the controlled cave at Y=" + floorY);
        }
    }

    @Test
    void naturalCaveBoundsIncludeBothBuildSafeEndpointsAndExcludeTheirNeighbors() {
        CaveDropTrapFeature.NaturalCaveFloorBounds bounds =
                CaveDropTrapFeature.NaturalCaveAnchorSource.floorBounds(
                        -64, 320, CaveDropTrap.RibbonOrientation.NORTH);
        assertEquals(
                -64 - bounds.minimumEnvelopeOffset(),
                bounds.minimumFloorY());
        assertEquals(
                319 - bounds.maximumEnvelopeOffset(),
                bounds.maximumFloorY());
        assertTrue(bounds.contains(bounds.minimumFloorY()));
        assertTrue(bounds.contains(bounds.maximumFloorY()));
        assertFalse(bounds.contains(bounds.minimumFloorY() - 1));
        assertFalse(bounds.contains(bounds.maximumFloorY() + 1));
        assertEquals(
                (long) bounds.maximumFloorY() - bounds.minimumFloorY() + 1L,
                bounds.legalFloorCount());
    }

    @Test
    void naturalCaveBoundsDeriveExtremaFromEveryRotatedOwnerEnvelope() {
        Long legalFloorCount = null;
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            CaveDropTrap.RoofedGalleryThroatPlan plan =
                    CaveDropTrap.planRoofedGalleryThroat(0, orientation);
            int expectedMinimumOffset = plan.ownerEnvelopeVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::y)
                    .min()
                    .orElseThrow();
            int expectedMaximumOffset = plan.ownerEnvelopeVoxels().stream()
                    .mapToInt(CaveDropTrap.Voxel::y)
                    .max()
                    .orElseThrow();
            CaveDropTrapFeature.NaturalCaveFloorBounds bounds =
                    CaveDropTrapFeature.NaturalCaveAnchorSource.floorBounds(
                            -64, 320, orientation);
            assertEquals(expectedMinimumOffset, bounds.minimumEnvelopeOffset());
            assertEquals(expectedMaximumOffset, bounds.maximumEnvelopeOffset());
            assertEquals(-64 - expectedMinimumOffset, bounds.minimumFloorY());
            assertEquals(319 - expectedMaximumOffset, bounds.maximumFloorY());
            if (legalFloorCount == null) {
                legalFloorCount = bounds.legalFloorCount();
            } else {
                assertEquals(legalFloorCount.longValue(), bounds.legalFloorCount(),
                        "rotation must not change the legal vertical domain");
            }
        }
    }

    @Test
    void naturalCaveExtremeBoundsAndCountersFailClosedInsteadOfWrapping() {
        CaveDropTrapFeature.NaturalCaveFloorBounds normal =
                CaveDropTrapFeature.NaturalCaveAnchorSource.floorBounds(
                        -64, 320, CaveDropTrap.RibbonOrientation.NORTH);
        assertTrue(normal.minimumEnvelopeOffset() < 0,
                "the locked envelope has a negative lower extent");
        assertThrows(
                ArithmeticException.class,
                () -> CaveDropTrapFeature.NaturalCaveAnchorSource.floorBounds(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        CaveDropTrap.RibbonOrientation.NORTH),
                "subtracting the negative lower extent from MAX_VALUE must not wrap");
        assertThrows(
                ArithmeticException.class,
                () -> CaveDropTrapFeature.NaturalCaveAnchorSource.floorBounds(
                        0,
                        Integer.MIN_VALUE,
                        CaveDropTrap.RibbonOrientation.NORTH),
                "maxYExclusive minus one must not wrap below MIN_VALUE");
        assertThrows(
                ArithmeticException.class,
                () -> CaveDropTrapFeature.NaturalCaveAnchorSource
                        .incrementSourceCounter(Long.MAX_VALUE),
                "source slot arithmetic must use Math.addExact");
    }

    @Test
    void naturalCaveQualificationRequiresOnlyNaturalFloorAndTwoAirBlocks() {
        BlockPos anchor = new BlockPos(10, 40, 20);
        assertTrue(CaveDropTrapFeature.NaturalCaveAnchorSource.qualifies(
                anchor,
                position -> position.equals(anchor)
                        ? CaveDropTrap.ThroatBlockKind.SAFE_NATURAL
                        : CaveDropTrap.ThroatBlockKind.AIR));
        assertFalse(CaveDropTrapFeature.NaturalCaveAnchorSource.qualifies(
                anchor,
                position -> position.equals(anchor)
                        ? CaveDropTrap.ThroatBlockKind.OTHER
                        : CaveDropTrap.ThroatBlockKind.AIR));
        assertFalse(CaveDropTrapFeature.NaturalCaveAnchorSource.qualifies(
                anchor,
                position -> position.equals(anchor)
                        ? CaveDropTrap.ThroatBlockKind.AIR
                        : CaveDropTrap.ThroatBlockKind.AIR));
        assertFalse(CaveDropTrapFeature.NaturalCaveAnchorSource.qualifies(
                anchor,
                position -> position.equals(anchor)
                        ? CaveDropTrap.ThroatBlockKind.FLUID
                        : CaveDropTrap.ThroatBlockKind.AIR));
        assertFalse(CaveDropTrapFeature.NaturalCaveAnchorSource.qualifies(
                anchor,
                position -> position.equals(anchor)
                        ? CaveDropTrap.ThroatBlockKind.SAFE_NATURAL
                        : position.equals(anchor.above())
                                ? CaveDropTrap.ThroatBlockKind.OTHER
                                : CaveDropTrap.ThroatBlockKind.AIR));
        assertFalse(CaveDropTrapFeature.NaturalCaveAnchorSource.qualifies(
                anchor,
                position -> position.equals(anchor)
                        ? CaveDropTrap.ThroatBlockKind.SAFE_NATURAL
                        : position.equals(anchor.above(2))
                                ? CaveDropTrap.ThroatBlockKind.FLUID
                                : CaveDropTrap.ThroatBlockKind.AIR));
    }

    @Test
    void naturalCaveSourceHasNoSurfaceClimateRngOrWriteDependencies() throws Exception {
        String resource = "/"
                + CaveDropTrapFeature.NaturalCaveAnchorSource.class
                        .getName().replace('.', '/')
                + ".class";
        byte[] bytes;
        try (InputStream stream = CaveDropTrapFeatureContractTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        Set<String> calls = classModel.methods().stream()
                .filter(method -> method.code().isPresent())
                .flatMap(method -> method.code().orElseThrow().elementStream())
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(
                Set.of(
                        "getHeight", "getSeaLevel", "getBiome", "canSeeSky", "seesSky",
                        "nextFloat", "nextInt", "nextLong",
                        "setBlock", "setBlockEntity", "removeBlock", "destroyBlock",
                        "applyScene")
                        .stream()
                        .noneMatch(calls::contains),
                "the candidate source has no surface, climate, RNG, or world-write call");
    }

    @Test
    void naturalCaveDedupKeepsTheFirstCanonicalOwnerAnchor() {
        BlockPos shared = new BlockPos(20, 40, 30);
        CaveDropTrapFeature.NaturalCaveAnchorCandidate south =
                new CaveDropTrapFeature.NaturalCaveAnchorCandidate(
                        CaveDropTrap.RibbonOrientation.SOUTH, 8, 8, 40, shared);
        CaveDropTrapFeature.NaturalCaveAnchorCandidate northLater =
                new CaveDropTrapFeature.NaturalCaveAnchorCandidate(
                        CaveDropTrap.RibbonOrientation.NORTH, 7, 8, 40, shared);
        CaveDropTrapFeature.NaturalCaveAnchorCandidate northFirst =
                new CaveDropTrapFeature.NaturalCaveAnchorCandidate(
                        CaveDropTrap.RibbonOrientation.NORTH, 6, 8, 40, shared);
        CaveDropTrapFeature.NaturalCaveAnchorDedup deduplicated =
                CaveDropTrapFeature.NaturalCaveAnchorSource.deduplicate(
                        List.of(south, northLater, northFirst));
        assertEquals(List.of(northFirst), deduplicated.uniqueCandidates());
        assertEquals(List.of(northLater, south), deduplicated.duplicateCandidates());
    }

    @Test
    void realPassASelectorSeamAssignsRecordingRngOnlyToCanonicalPasses()
            throws Exception {
        int floorY = 40;
        int baseX = 160;
        int baseZ = -320;
        CaveDropTrapFeature.NaturalCaveFloorBounds reference =
                CaveDropTrapFeature.ConformalPassageThroatSource.floorBounds(
                        -64, 320, CaveDropTrap.RibbonOrientation.NORTH);
        int minimumBuildY = floorY + reference.minimumEnvelopeOffset();
        int maximumBuildYExclusive =
                floorY + reference.maximumEnvelopeOffset() + 1;
        RecordingRandom firstRandom = recordingRandom(0x51ec_70a5L);
        CaveDropTrapFeature.ConformalPassageScan observedSource =
                CaveDropTrapFeature.ConformalPassageThroatSource.scan(
                        minimumBuildY,
                        maximumBuildYExclusive,
                        baseX,
                        baseZ,
                        position -> position.getY() > floorY
                                ? CaveDropTrap.ThroatBlockKind.AIR
                                : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL);
        assertTrue(observedSource.caveAnchorRejected() > 0,
                "the open-cavern source contains pre-selector rejections");
        assertTrue(firstRandom.nextFloats().isEmpty(),
                "source qualification cannot consume selector RNG");
        assertTrue(observedSource.candidates().size() >= 4);
        List<CaveDropTrapFeature.ConformalThroatCandidate> mixedCandidates =
                new ArrayList<>(observedSource.candidates());
        CaveDropTrapFeature.ConformalThroatCandidate originalSecond =
                mixedCandidates.get(1);
        mixedCandidates.set(
                1,
                new CaveDropTrapFeature.ConformalThroatCandidate(
                        new CaveDropTrapFeature.NaturalCaveAnchorCandidate(
                                originalSecond.identity().orientation(),
                                originalSecond.identity().originLx(),
                                originalSecond.identity().originLz(),
                                originalSecond.identity().floorY(),
                                mixedCandidates.getFirst().identity().ownerAnchor()),
                        originalSecond.plan()));
        CaveDropTrapFeature.ConformalPassageScan source =
                new CaveDropTrapFeature.ConformalPassageScan(
                        observedSource.legalFloorCount(),
                        observedSource.auditDomainSlots(),
                        observedSource.caveAnchorQualified(),
                        observedSource.caveAnchorRejected(),
                        observedSource.floorBounds(),
                        mixedCandidates);
        assertTrue(source.reconciles());

        CaveDropTrapFeature.NaturalCaveAnchorDedup deduplicated =
                CaveDropTrapFeature.ConformalPassageThroatSource.deduplicate(
                        source.candidates());
        assertFalse(deduplicated.duplicateCandidates().isEmpty(),
                "the controlled source contains a real dedup duplicate");
        assertTrue(deduplicated.uniqueCandidates().size() >= 4,
                "the controlled source needs two passes plus structural and reward rejects");
        Set<CaveDropTrapFeature.NaturalCaveAnchorCandidate> duplicates =
                new HashSet<>(deduplicated.duplicateCandidates());
        CaveDropTrap.RoofedThroatMeasurements measurements =
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true);
        CaveDropTrap.RoofedThroatEvaluation pass =
                new CaveDropTrap.RoofedThroatEvaluation(
                        CaveDropTrap.PassARejection.PASS,
                        true,
                        true,
                        CaveDropTrap.RewardFeasibilityFailure.NONE,
                        true,
                        CaveDropTrap.RewardFeasibilityFailure.NONE);
        CaveDropTrap.RoofedThroatEvaluation structuralReject =
                new CaveDropTrap.RoofedThroatEvaluation(
                        CaveDropTrap.PassARejection.SAME_DOOR,
                        false,
                        false,
                        CaveDropTrap.RewardFeasibilityFailure.NONE,
                        false,
                        CaveDropTrap.RewardFeasibilityFailure.NONE);
        CaveDropTrap.RoofedThroatEvaluation rewardPreflightReject =
                new CaveDropTrap.RoofedThroatEvaluation(
                        CaveDropTrap.PassARejection.DISCONNECTED_LOWER_OR_REWARD,
                        false,
                        false,
                        CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED,
                        false,
                        CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED);
        List<CaveDropTrapFeature.PassASelectorSlot> slots = new ArrayList<>();
        List<CaveDropTrapFeature.NaturalCaveAnchorCandidate> expectedPasses =
                new ArrayList<>();
        int rejectedUniqueSlots = 0;
        for (CaveDropTrapFeature.ConformalThroatCandidate conformal
                : source.candidates()) {
            CaveDropTrapFeature.NaturalCaveAnchorCandidate candidate =
                    conformal.identity();
            if (duplicates.contains(candidate)) {
                slots.add(CaveDropTrapFeature.PassASelectorSlot.duplicate(
                        candidate,
                        selectorWitness(
                                candidate,
                                "DUPLICATE",
                                CaveDropTrapFeature.PassAPredicateRole.DUPLICATE)));
                continue;
            }
            boolean acceptedByStructure = expectedPasses.size() < 2;
            CaveDropTrap.RoofedThroatEvaluation evaluation;
            if (acceptedByStructure) {
                evaluation = pass;
                expectedPasses.add(candidate);
            } else {
                evaluation = rejectedUniqueSlots++ == 0
                        ? structuralReject : rewardPreflightReject;
            }
            slots.add(CaveDropTrapFeature.PassASelectorSlot.evaluated(
                    candidate,
                    conformal.plan(),
                    evaluation,
                    measurements,
                    selectorWitness(
                            candidate,
                            evaluation.rejection().name(),
                            acceptedByStructure
                                    ? CaveDropTrapFeature.PassAPredicateRole.PASSED_STRUCTURAL
                                    : CaveDropTrapFeature.PassAPredicateRole
                                            .AUTHORED_VOLUME_OR_OTHER)));
        }

        long worldSeed = 0x1234_5678_9abc_def0L;
        // The prediction seam gets the real world floor: the hand-built pass evaluations claim
        // flooded feasibility, so the deep-landing depth law must be satisfiable or the
        // fail-closed fallback accounting (correctly) refuses to reconcile.
        int predictionWorldFloor = -64;
        CaveDropTrapFeature.PassASelectorRun first =
                CaveDropTrapFeature.runPassASelectorAssignments(
                        10,
                        -20,
                        source,
                        slots,
                        firstRandom.source(),
                        worldSeed,
                        predictionWorldFloor);
        assertEquals(2, firstRandom.nextFloats().size(),
                "only the two final structurally feasible candidates consume nextFloat");
        assertEquals(
                expectedPasses,
                first.assignments().stream()
                        .map(CaveDropTrapFeature.PassASelectorAssignment::candidate)
                        .toList(),
                "selector rolls stay assigned to canonical qualified owners");
        assertEquals(
                firstRandom.nextFloats(),
                first.assignments().stream()
                        .map(CaveDropTrapFeature.PassASelectorAssignment::fractionRoll)
                        .toList());
        int acceptedBefore = 0;
        for (CaveDropTrapFeature.PassASelectorAssignment assignment
                : first.assignments()) {
            assertEquals(acceptedBefore, assignment.acceptedBefore());
            if (assignment.prediction().capAccepted()) {
                acceptedBefore++;
            }
        }
        assertEquals(acceptedBefore, first.capAcceptedCount());
        assertEquals(acceptedBefore, first.snapshot().capAccepted());
        assertEquals(source.caveAnchorQualified(), first.snapshot().scanSlots());
        assertEquals(
                deduplicated.duplicateCandidates().size(),
                first.snapshot().duplicateReject());
        assertEquals(2, first.snapshot().ordinaryFeasible());
        assertTrue(first.snapshot().sameDoorReject() > 0);
        assertTrue(first.snapshot().disconnectedLowerOrRewardReject() > 0);
        assertTrue(first.snapshot().reconciles());

        RecordingRandom secondRandom = recordingRandom(0x51ec_70a5L);
        CaveDropTrapFeature.PassASelectorRun second =
                CaveDropTrapFeature.runPassASelectorAssignments(
                        10,
                        -20,
                        source,
                        slots,
                        secondRandom.source(),
                        worldSeed,
                        predictionWorldFloor);
        assertEquals(first, second,
                "repeat execution produces byte-stable selector assignment rows");
        assertEquals(firstRandom.nextFloats(), secondRandom.nextFloats());

        Method jsonMethod = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "passACensusJson", CaveDropTrapFeature.PassACensusSnapshot.class);
        jsonMethod.setAccessible(true);
        String firstJson =
                ((JsonObject) jsonMethod.invoke(null, first.snapshot())).toString();
        String secondJson =
                ((JsonObject) jsonMethod.invoke(null, second.snapshot())).toString();
        assertEquals(firstJson, secondJson,
                "repeat execution produces byte-identical serialized v5 output");
    }

    @Test
    void passAV6SourceCounterEquationsFailClosedWhenForged()
            throws ReflectiveOperationException {
        CaveDropTrapFeature.PassACensusSnapshot valid = passingPassARow(
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true));
        assertTrue(valid.reconciles());
        assertFalse(copyPassARowWithLong(valid, "auditDomainSlots", 19).reconciles(),
                "auditDomainSlots must equal the 196 owner-inset anchor columns"
                        + " times legalFloorCount");
        assertFalse(copyPassARowWithLong(valid, "caveAnchorRejected", 194).reconciles(),
                "qualified and rejected anchors must partition the audit domain");
        assertFalse(copyPassARowWithLong(valid, "caveAnchorQualified", 0).reconciles(),
                "qualified anchors must equal downstream scanSlots");
        assertFalse(copyPassARowWithLong(valid, "scanSlots", 2).reconciles(),
                "scanSlots must still partition into unique anchors and duplicates");
    }

    @Test
    void passAV4ShapedSnapshotConstructorsAreUnavailable() {
        int canonicalArity =
                CaveDropTrapFeature.PassACensusSnapshot.class
                        .getRecordComponents().length;
        Set<Integer> publicConstructorArities = Arrays.stream(
                        CaveDropTrapFeature.PassACensusSnapshot.class.getConstructors())
                .map(constructor -> constructor.getParameterTypes().length)
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(canonicalArity, canonicalArity - 1),
                publicConstructorArities,
                "only canonical v5 and explicit-v5-with-synthetic-provenance construction remain");
        assertFalse(publicConstructorArities.contains(canonicalArity - 4),
                "a v4-shaped constructor with provenance must not exist");
        assertFalse(publicConstructorArities.contains(canonicalArity - 5),
                "a v4-shaped constructor without provenance must not exist");
    }

    @Test
    void passASourceIsDefaultOffScanOnlyAndProductionYLatticeRemainsFrozen()
            throws Exception {
        Field top = CaveDropTrapFeature.class.getDeclaredField("SCAN_TOP_Y");
        Field bottom = CaveDropTrapFeature.class.getDeclaredField("SCAN_BOTTOM_Y");
        top.setAccessible(true);
        bottom.setAccessible(true);
        assertEquals(100, top.getInt(null));
        assertEquals(8, bottom.getInt(null));

        String source = Files.readString(Path.of(
                "src/main/java/com/example/globe/world/CaveDropTrapFeature.java"));
        assertEquals(
                2,
                source.split("ConformalPassageThroatSource\\.scan\\(", -1).length - 1,
                "exactly two callers acquire conformal candidates: the shipping placement "
                        + "path and the no-write census");
        assertEquals(
                0,
                source.split("NaturalCaveAnchorSource\\.scan\\(", -1).length - 1,
                "the retired v5 fixed-stamp source has no remaining scan caller");
        assertEquals(
                2,
                source.split("runPassASelectorAssignments\\(", -1).length - 1,
                "the selector seam has one declaration and one real scan caller");
        Method selectorSeam = Arrays.stream(CaveDropTrapFeature.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("runPassASelectorAssignments"))
                .findFirst()
                .orElseThrow();
        int selectorModifiers = selectorSeam.getModifiers();
        assertFalse(java.lang.reflect.Modifier.isPublic(selectorModifiers));
        assertFalse(java.lang.reflect.Modifier.isProtected(selectorModifiers));
        assertFalse(java.lang.reflect.Modifier.isPrivate(selectorModifiers),
                "the proof seam is package-private for the focused executable test");
        int placeStart = source.indexOf(
                "public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx)");
        int auditScanStart = source.indexOf(
                "public static PassACensusSnapshot scanPassAChunk");
        assertTrue(placeStart >= 0 && auditScanStart > placeStart);
        String productionPlace = source.substring(placeStart, auditScanStart);
        assertTrue(productionPlace.contains(
                        "for (int floorY = SCAN_TOP_Y; floorY >= scanBottom; floorY--)"),
                "the production 100-to-8 lattice remains unchanged");
        assertFalse(productionPlace.contains("NaturalCaveAnchorSource"),
                "the retired v5 fixed-stamp source is not part of any placement");
        assertTrue(productionPlace.contains("placeConformalThroats("),
                "shipping placement now runs the conformal passage throat");
        assertTrue(productionPlace.contains("applyScene("),
                "the shipping path still writes through the atomic scene writer");
        assertFalse(productionPlace.contains("runPassASelectorAssignments"),
                "production placement cannot enter the diagnostic selector seam");
    }

    @Test
    void conformalZeroShearPlanEqualsTheLockedFactoryPlan() {
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            for (int floorY : List.of(0, 40)) {
                assertEquals(
                        CaveDropTrap.planRoofedGalleryThroat(floorY, orientation),
                        CaveDropTrapFeature.ConformalPassageThroatSource.buildPlan(
                                floorY, orientation, new int[8], new int[8]),
                        "the unbent conformal plan is identical to the locked factory plan,"
                                + " so every law proven for the locked plan carries over");
            }
        }
    }

    @Test
    void everyConformalShearAndMouthStepCombinationPassesTheFrozenTopologyValidator() {
        int validated = 0;
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            for (int nearMouthEnd = -1; nearMouthEnd <= 1; nearMouthEnd++) {
                for (int nearMouth = -1; nearMouth <= 1; nearMouth++) {
                    for (int joint3 = -1; joint3 <= 1; joint3++) {
                        for (int joint4 = -1; joint4 <= 1; joint4++) {
                            for (int farBank = 0; farBank <= 0; farBank++) {
                                for (int farMouthEnd = -1; farMouthEnd <= 0; farMouthEnd++) {
                                    for (int[] pattern : new int[][] {
                                            {0, 0, 0, 0, 0, 0, 0, 0},
                                            {-1, -1, -1, -1, -1, -1, -1, -1},
                                            {1, 1, 1, 1, 1, 1, 1, 1},
                                            {0, -1, -1, 0, 1, 0, 0, 1},
                                            {1, 0, 0, 1, -1, 0, 0, -1}}) {
                                        int[] steps = pattern.clone();
                                        CaveDropTrap.RoofedGalleryThroatPlan plan =
                                                CaveDropTrapFeature
                                                        .ConformalPassageThroatSource
                                                        .buildPlan(
                                                                40,
                                                                orientation,
                                                                conformalShear(
                                                                        nearMouthEnd,
                                                                        nearMouth,
                                                                        joint3,
                                                                        joint4,
                                                                        farBank,
                                                                        farMouthEnd),
                                                                steps);
                                        assertEquals(
                                                CaveDropTrap.PassARejection.PASS,
                                                CaveDropTrap
                                                        .validateRoofedGalleryThroatTopology(
                                                                plan),
                                                "every member of the conformal family keeps"
                                                        + " the locked topology: " + orientation
                                                        + " " + nearMouthEnd + "," + nearMouth
                                                        + "," + joint3 + "," + joint4 + ","
                                                        + farBank + "," + farMouthEnd
                                                        + " steps " + Arrays.toString(steps));
                                        validated++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(4 * 3 * 3 * 3 * 3 * 1 * 2 * 5, validated,
                "the whole bounded shear/mouth family was exhaustively validated");
    }

    @Test
    void tallGalleryRoofsQualifyWhileTheRealEnclosureProtectionsStillBite() {
        int floorY = 40;
        CaveDropTrap.RoofedGalleryThroatPlan plan =
                CaveDropTrap.planRoofedGalleryThroat(
                        floorY, CaveDropTrap.RibbonOrientation.NORTH);

        // A cave whose ceiling sits far above the old six-block search window is still a cave.
        for (int ceilingRise : List.of(3, 6, 7, 12, 30, CaveDropTrap.MAX_ROOF_SEARCH_RISE)) {
            assertEquals(
                    CaveDropTrap.PassARejection.PASS,
                    CaveDropTrap.evaluateRoofedGalleryThroat(
                                    plan, galleryWorldView(plan, ceilingRise, false, -1))
                            .rejection(),
                    "a gallery roofed " + ceilingRise + " blocks up is enclosed");
        }
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryThroat(
                                plan,
                                galleryWorldView(
                                        plan,
                                        CaveDropTrap.MAX_ROOF_SEARCH_RISE + 1,
                                        false,
                                        -1))
                        .rejection(),
                "the search still terminates: no ceiling found means no proof of enclosure");

        // The protections that were doing the real work are untouched at any ceiling height.
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryThroat(
                                plan, galleryWorldView(plan, 30, true, -1))
                        .rejection(),
                "open sky above a high ceiling is still an exposed surface trap");
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryThroat(
                                plan, galleryWorldView(plan, 30, false, 1))
                        .rejection(),
                "a one-block-thin high ceiling is still rejected");
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryThroat(
                                plan, galleryWorldView(plan, 30, false, 3))
                        .rejection(),
                "water pooled just above a high ceiling is still rejected");
    }

    /**
     * A synthetic cave: solid everywhere except a walkable void from the carpet up to a ceiling
     * {@code ceilingRise} blocks above it. {@code waterAtThickness} floods the block that many
     * blocks above the ceiling's first natural block (negative for a dry, fully solid roof).
     */
    private static CaveDropTrap.RoofedThroatWorldView galleryWorldView(
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            int ceilingRise,
            boolean skyAboveRoof,
            int waterAtThickness) {
        int ceilingY = plan.floorY() + ceilingRise;
        Set<CaveDropTrap.Cell> columns = Set.copyOf(plan.relevantFloorColumns());
        return new CaveDropTrap.RoofedThroatWorldView() {
            @Override
            public CaveDropTrap.ThroatBlockKind blockKind(CaveDropTrap.Voxel voxel) {
                CaveDropTrap.Cell column = new CaveDropTrap.Cell(voxel.x(), voxel.z());
                if (!columns.contains(column)) {
                    return CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
                }
                if (waterAtThickness >= 0
                        && voxel.y() == ceilingY + waterAtThickness) {
                    return CaveDropTrap.ThroatBlockKind.FLUID;
                }
                return voxel.y() > plan.floorY() && voxel.y() < ceilingY
                        ? CaveDropTrap.ThroatBlockKind.AIR
                        : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
            }

            @Override
            public boolean insideOwnerInset(CaveDropTrap.Voxel voxel) {
                return true;
            }

            @Override
            public boolean authored(CaveDropTrap.Voxel voxel) {
                return false;
            }

            @Override
            public boolean seesSky(CaveDropTrap.Voxel voxel) {
                return skyAboveRoof;
            }

            @Override
            public int minimumBuildY() {
                return -64;
            }
        };
    }

    @Test
    void theClimbOutStairMayEmergeIntoOpenCaveAirButTheShaftLidMayNot() {
        int floorY = 40;
        CaveDropTrap.RoofedGalleryThroatPlan plan =
                CaveDropTrap.planRoofedGalleryThroat(
                        floorY, CaveDropTrap.RibbonOrientation.NORTH);
        CaveDropTrap.Voxel stairTop = plan.stairStandingVoxels().getLast();
        assertTrue(stairTop.y() > floorY,
                "the stair's last step is above the carpet plane by construction");

        assertEquals(
                CaveDropTrap.PassARejection.PASS,
                CaveDropTrap.evaluateRoofedGalleryThroat(
                                plan,
                                tracedEvaluationWorld(
                                        plan, Map.of(
                                                stairTop, CaveDropTrap.ThroatBlockKind.AIR,
                                                stairTop.above(),
                                                CaveDropTrap.ThroatBlockKind.AIR)))
                        .rejection(),
                "a stair that opens into the passage it returns to is not a defect");

        CaveDropTrap.Voxel underCarpet = plan.powderCells().stream()
                .map(cell -> new CaveDropTrap.Voxel(cell.x(), floorY - 1, cell.z()))
                .findFirst().orElseThrow();
        assertEquals(
                CaveDropTrap.PassARejection.CAVITY_AIR_OR_FLUID,
                CaveDropTrap.evaluateRoofedGalleryThroat(
                                plan,
                                tracedEvaluationWorld(
                                        plan,
                                        Map.of(underCarpet,
                                                CaveDropTrap.ThroatBlockKind.AIR)))
                        .rejection(),
                "a void directly under the carpet still makes the entrance a thin lid");
        assertEquals(
                CaveDropTrap.PassARejection.CAVITY_AIR_OR_FLUID,
                CaveDropTrap.evaluateRoofedGalleryThroat(
                                plan,
                                tracedEvaluationWorld(
                                        plan,
                                        Map.of(stairTop,
                                                CaveDropTrap.ThroatBlockKind.FLUID)))
                        .rejection(),
                "water is still refused everywhere in the authored volume");
    }

    /** The shared synthetic world used by the traced-evaluation fixtures, without the tracer. */
    private static CaveDropTrap.RoofedThroatWorldView tracedEvaluationWorld(
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            Map<CaveDropTrap.Voxel, CaveDropTrap.ThroatBlockKind> overrides) {
        return new CaveDropTrap.RoofedThroatWorldView() {
            @Override
            public CaveDropTrap.ThroatBlockKind blockKind(CaveDropTrap.Voxel voxel) {
                return tracedKind(plan, overrides, voxel);
            }

            @Override
            public boolean insideOwnerInset(CaveDropTrap.Voxel voxel) {
                return true;
            }

            @Override
            public boolean authored(CaveDropTrap.Voxel voxel) {
                return false;
            }

            @Override
            public boolean seesSky(CaveDropTrap.Voxel voxel) {
                return false;
            }

            @Override
            public int minimumBuildY() {
                return -64;
            }
        };
    }

    @Test
    void conformalCorridorLateralIsRecoveredFromTheStairAndZeroForTheFactoryPlan() {
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            assertEquals(0, CaveDropTrapFeature.passACorridorLateral(
                            CaveDropTrap.planRoofedGalleryThroat(40, orientation)),
                    "the locked factory plan recovers a zero corridor lane");
            for (int joint3 = -1; joint3 <= 1; joint3++) {
                for (int joint4 = -1; joint4 <= 1; joint4++) {
                    CaveDropTrap.RoofedGalleryThroatPlan plan =
                            CaveDropTrapFeature.ConformalPassageThroatSource.buildPlan(
                                    40,
                                    orientation,
                                    conformalShear(0, 0, joint3, joint4, 0, 0),
                                    new int[8]);
                    assertEquals(joint3 + joint4,
                            CaveDropTrapFeature.passACorridorLateral(plan),
                            "the sheared corridor lane is recovered exactly for "
                                    + orientation + " " + joint3 + "," + joint4);
                }
            }
        }
    }

    @Test
    void driverAcceptsHonestZeroCandidateRowsAndRefusesZeroDomainRows() {
        String schema = "focused-driver-census-v2";
        long worldSeed = 77L;
        CaveDropTrapFeature.PassACensusSnapshot honestEmpty =
                emptyConformalPassARow(3, 5);
        assertTrue(honestEmpty.reconciles(),
                "a zero-candidate conformal chunk row reconciles honestly");
        CaveDropTrapFullChunkAudit.DriverCensusLedger ledger =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        3, 5, 1, schema, worldSeed);
        ledger.scanRequestedChunk(3, 5, random -> honestEmpty);
        assertTrue(ledger.failures().isEmpty(),
                "an honest zero-candidate chunk row must be accepted: " + ledger.failures());

        CaveDropTrapFullChunkAudit.DriverCensusLedger zeroDomain =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        3, 5, 1, schema, worldSeed);
        zeroDomain.scanRequestedChunk(
                3, 5, random -> CaveDropTrapFeature.PassACensusSnapshot.empty(3, 5));
        assertFalse(zeroDomain.failures().isEmpty(),
                "a zero-domain row still proves a broken scan and must be refused");
    }

    @Test
    void conformalTraceFollowsABentPassageTheLockedStampCannotFit() {
        int floorY = 40;
        int baseX = 96;
        int baseZ = 224;
        int anchorLx = 3;
        int anchorLz = 5;
        // A three-wide passage that steps one lane sideways at the second powder column.
        // Only the two headroom rows are open, so everything the trap bores through is
        // genuine solid mass.
        Function<BlockPos, CaveDropTrap.ThroatBlockKind> bentPassage = position -> {
            int u = position.getX() - baseX - anchorLx;
            int v = position.getZ() - baseZ - anchorLz;
            int centre = u <= 1 ? 0 : 1;
            boolean inBand = u >= -4 && u <= 9 && Math.abs(v - centre) <= 1;
            return inBand
                            && (position.getY() == floorY + 1
                                    || position.getY() == floorY + 2)
                    ? CaveDropTrap.ThroatBlockKind.AIR
                    : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
        };
        CaveDropTrapFeature.ConformalPassageScan scan = conformalScanAt(
                floorY, baseX, baseZ, bentPassage);
        assertTrue(scan.reconciles());
        CaveDropTrapFeature.ConformalThroatCandidate candidate =
                scan.candidates().stream()
                        .filter(entry -> entry.identity().originLx() == anchorLx
                                && entry.identity().originLz() == anchorLz)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                CaveDropTrap.RibbonOrientation.NORTH,
                candidate.identity().orientation());
        assertEquals(
                CaveDropTrapFeature.ConformalPassageThroatSource.buildPlan(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        conformalShear(0, 0, 0, 1, 0, 0),
                        new int[8]),
                candidate.plan(),
                "the trace bends laterally with the passage instead of stamping it");
        boolean stampFits = CaveDropTrap
                .planRoofedGalleryThroat(floorY, CaveDropTrap.RibbonOrientation.NORTH)
                .coverCells().stream()
                .allMatch(cell -> bentPassage.apply(new BlockPos(
                                baseX + anchorLx + cell.x(),
                                floorY + 1,
                                baseZ + anchorLz + cell.z()))
                        == CaveDropTrap.ThroatBlockKind.AIR);
        assertFalse(stampFits,
                "the fixed v5 rectangle runs into the passage wall at this anchor");
        CaveDropTrap.RoofedThroatEvaluation evaluation =
                CaveDropTrap.evaluateRoofedGalleryThroat(
                        candidate.plan(),
                        conformalWorldView(bentPassage, baseX, baseZ, anchorLx, anchorLz));
        assertEquals(CaveDropTrap.PassARejection.PASS, evaluation.rejection(),
                "the unchanged shared predicate accepts the traced conformal throat");
        assertTrue(evaluation.ordinaryFeasible());
        for (CaveDropTrap.Voxel voxel : candidate.plan().ownerEnvelopeVoxels()) {
            int localX = anchorLx + voxel.x();
            int localZ = anchorLz + voxel.z();
            assertTrue(localX >= 1 && localX <= 14);
            assertTrue(localZ >= 1 && localZ <= 14);
        }
    }

    @Test
    void conformalMouthsStandOnThePassageFloorOneStepDownAndStillPassTheSharedPredicate() {
        int floorY = 40;
        int baseX = -224;
        int baseZ = 480;
        int anchorLx = 3;
        int anchorLz = 6;
        Function<BlockPos, CaveDropTrap.ThroatBlockKind> steppedPassage = position -> {
            int u = position.getX() - baseX - anchorLx;
            int v = position.getZ() - baseZ - anchorLz;
            int y = position.getY();
            // Past the carpet the passage narrows to its two exit lanes on both ends,
            // keeping the climb-out shaft and the lateral shell in solid rock. Only the
            // approach-side floor steps one block down: beside the return mouth the stair
            // tops out at carpet level, so the frozen shell law requires the onward floor
            // there to stay at the carpet plane.
            boolean inBand;
            if (u >= 8) {
                inBand = u <= 12 && (v == -1 || v == 0);
            } else if (u <= -1) {
                inBand = u >= -4 && (v == 0 || v == 1);
            } else {
                inBand = Math.abs(v) <= 1;
            }
            if (!inBand) {
                return CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
            }
            boolean droppedApproachZone = u <= -1;
            if (droppedApproachZone) {
                return y >= floorY && y <= floorY + 2
                        ? CaveDropTrap.ThroatBlockKind.AIR
                        : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
            }
            return y == floorY + 1 || y == floorY + 2
                    ? CaveDropTrap.ThroatBlockKind.AIR
                    : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
        };
        CaveDropTrapFeature.ConformalPassageScan scan = conformalScanAt(
                floorY, baseX, baseZ, steppedPassage);
        CaveDropTrapFeature.ConformalThroatCandidate candidate =
                scan.candidates().stream()
                        .filter(entry -> entry.identity().originLx() == anchorLx
                                && entry.identity().originLz() == anchorLz)
                        .findFirst()
                        .orElseThrow();
        int[] steppedMouths = {-1, -1, -1, -1, 0, 0, 0, 0};
        assertEquals(
                CaveDropTrapFeature.ConformalPassageThroatSource.buildPlan(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        new int[CaveDropTrapFeature.ConformalPassageThroatSource.SHEAR_SLOTS],
                        steppedMouths),
                candidate.plan(),
                "the approach mouth stands on the passage's own lower floor");
        Set<CaveDropTrap.Cell> approachCells = Set.copyOf(candidate.plan().approachA());
        for (CaveDropTrap.Voxel mouthFloor
                : candidate.plan().continuationFloorVoxels()) {
            boolean approachSide = approachCells.contains(
                    new CaveDropTrap.Cell(mouthFloor.x(), mouthFloor.z()));
            assertEquals(approachSide ? floorY - 1 : floorY, mouthFloor.y(),
                    "each mouth witness is the real passage floor on its own side");
        }
        assertEquals(floorY - CaveDropTrap.MIN_DROP_AIR, candidate.plan().landingY(),
                "the drop is still bored a full ten blocks below the carpet");
        CaveDropTrap.RoofedThroatEvaluation evaluation =
                CaveDropTrap.evaluateRoofedGalleryThroat(
                        candidate.plan(),
                        conformalWorldView(
                                steppedPassage, baseX, baseZ, anchorLx, anchorLz));
        assertEquals(CaveDropTrap.PassARejection.PASS, evaluation.rejection());
    }

    @Test
    void conformalTraceSteersOffAHollowShelfOntoSolidGround() {
        int floorY = 40;
        int baseX = 160;
        int baseZ = -64;
        int anchorLx = 3;
        int anchorLz = 6;
        // A broad walkable passage whose near lane is a thin shelf over an open gallery and
        // whose remaining lanes are solid to the bottom. Only the solid lanes can host a
        // bored drop, so the trace has to bend onto them rather than give up.
        Function<BlockPos, CaveDropTrap.ThroatBlockKind> shelfPassage = position -> {
            int v = position.getZ() - baseZ - anchorLz;
            int y = position.getY();
            if (y == floorY + 1 || y == floorY + 2) {
                return CaveDropTrap.ThroatBlockKind.AIR;
            }
            boolean hollowBeneath = v <= -1
                    && y < floorY
                    && y > floorY - CaveDropTrap.MIN_DROP_AIR - 4;
            return hollowBeneath
                    ? CaveDropTrap.ThroatBlockKind.AIR
                    : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
        };
        CaveDropTrapFeature.ConformalPassageScan scan = conformalScanAt(
                floorY, baseX, baseZ, shelfPassage);
        assertTrue(scan.reconciles());
        CaveDropTrapFeature.ConformalThroatCandidate candidate =
                scan.candidates().stream()
                        .filter(entry -> entry.identity().originLx() == anchorLx
                                && entry.identity().originLz() == anchorLz)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "the trace must find the solid lane instead of giving up"));
        for (CaveDropTrap.Cell powder : candidate.plan().powderCells()) {
            for (int depth = 1; depth <= CaveDropTrap.MIN_DROP_AIR; depth++) {
                assertEquals(
                        CaveDropTrap.ThroatBlockKind.SAFE_NATURAL,
                        shelfPassage.apply(new BlockPos(
                                baseX + anchorLx + powder.x(),
                                floorY - depth,
                                baseZ + anchorLz + powder.z())),
                        "every carpet cell was steered onto genuine solid mass");
            }
        }
        assertTrue(
                candidate.plan().powderCells().stream().anyMatch(cell -> cell.z() == 2),
                "the carpet bent a full lane off the shelf: an unbent throat at this anchor"
                        + " could only reach z=1");
    }

    @Test
    void conformalTraceRejectsAPassageThatIsHollowUnderEveryLane() {
        int floorY = 40;
        int baseX = 512;
        int baseZ = 512;
        Function<BlockPos, CaveDropTrap.ThroatBlockKind> hollowEverywhere = position -> {
            int y = position.getY();
            if (y == floorY + 1 || y == floorY + 2) {
                return CaveDropTrap.ThroatBlockKind.AIR;
            }
            return y < floorY && y > floorY - CaveDropTrap.MIN_DROP_AIR
                    ? CaveDropTrap.ThroatBlockKind.AIR
                    : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
        };
        CaveDropTrapFeature.ConformalPassageScan scan = conformalScanAt(
                floorY, baseX, baseZ, hollowEverywhere);
        assertTrue(scan.reconciles());
        assertEquals(0, scan.caveAnchorQualified(),
                "a floor with nothing but void beneath it can never host a bored drop");
    }

    @Test
    void conformalDeadEndPassageYieldsNoCandidateAndStillReconciles() {
        int floorY = 40;
        int baseX = 320;
        int baseZ = -96;
        int anchorLx = 7;
        int anchorLz = 7;
        Function<BlockPos, CaveDropTrap.ThroatBlockKind> deadEnd = position -> {
            int u = position.getX() - baseX - anchorLx;
            int v = position.getZ() - baseZ - anchorLz;
            boolean inBand = u >= -4 && u <= 2 && Math.abs(v) <= 1;
            return inBand
                            && (position.getY() == floorY + 1
                                    || position.getY() == floorY + 2)
                    ? CaveDropTrap.ThroatBlockKind.AIR
                    : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
        };
        CaveDropTrapFeature.ConformalPassageScan scan = conformalScanAt(
                floorY, baseX, baseZ, deadEnd);
        assertTrue(scan.reconciles());
        assertEquals(0, scan.caveAnchorQualified(),
                "a passage without a second usable mouth cannot host the throat");
        assertTrue(scan.candidates().isEmpty());
        assertTrue(scan.caveAnchorRejected() > 0,
                "the walkable dead-end anchors are honestly counted as source rejections");
    }

    @Test
    void conformalSourceHasNoSurfaceClimateRngOrWriteDependenciesAndIsDeterministic()
            throws Exception {
        String resource = "/"
                + CaveDropTrapFeature.ConformalPassageThroatSource.class
                        .getName().replace('.', '/')
                + ".class";
        byte[] bytes;
        try (InputStream stream = CaveDropTrapFeatureContractTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        Set<String> calls = classModel.methods().stream()
                .filter(method -> method.code().isPresent())
                .flatMap(method -> method.code().orElseThrow().elementStream())
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(
                Set.of(
                        "getHeight", "getSeaLevel", "getBiome", "canSeeSky", "seesSky",
                        "nextFloat", "nextInt", "nextLong",
                        "setBlock", "setBlockEntity", "removeBlock", "destroyBlock",
                        "applyScene")
                        .stream()
                        .noneMatch(calls::contains),
                "the conformal source has no surface, climate, RNG, or world-write call");
        assertTrue(Arrays.stream(
                        CaveDropTrapFeature.ConformalPassageThroatSource.class
                                .getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(net.minecraft.util.RandomSource.class::equals),
                "the conformal source cannot consume selector RNG");

        int floorY = 40;
        int baseX = 96;
        int baseZ = 224;
        Function<BlockPos, CaveDropTrap.ThroatBlockKind> passage = position ->
                position.getY() > floorY
                        ? CaveDropTrap.ThroatBlockKind.AIR
                        : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
        CaveDropTrapFeature.ConformalPassageScan ordered =
                conformalScanAt(floorY, baseX, baseZ, passage);
        assertEquals(
                ordered,
                conformalScanAt(floorY, baseX, baseZ, passage),
                "identical cave reads produce identical ordered conformal source rows");
        assertTrue(ordered.candidates().size() >= 4);
        for (int index = 1; index < ordered.candidates().size(); index++) {
            CaveDropTrapFeature.NaturalCaveAnchorCandidate previous =
                    ordered.candidates().get(index - 1).identity();
            CaveDropTrapFeature.NaturalCaveAnchorCandidate current =
                    ordered.candidates().get(index).identity();
            int order = Integer.compare(previous.originLx(), current.originLx());
            if (order == 0) {
                order = Integer.compare(previous.originLz(), current.originLz());
            }
            if (order == 0) {
                order = Integer.compare(current.floorY(), previous.floorY());
            }
            assertTrue(order <= 0,
                    "column-major X then Z then descending Y is the canonical v6 source order");
        }
    }

    @Test
    void driverOwnsExactCoverageDeterministicRngAndHardFailureAccounting()
            throws Exception {
        String schema = "focused-driver-census-v1";
        long worldSeed = 0x1234_5678_9abc_def0L;
        List<int[]> coordinates = List.of(
                new int[] {7, 11},
                new int[] {8, 11},
                new int[] {7, 12},
                new int[] {8, 12});
        CaveDropTrapFullChunkAudit.DriverCensusLedger forward =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        7, 11, 2, schema, worldSeed);
        for (int[] coordinate : coordinates) {
            int x = coordinate[0];
            int z = coordinate[1];
            forward.scanRequestedChunk(
                    x, z, random -> deterministicDriverRow(x, z, random.nextBoolean()));
        }
        assertEquals(4, forward.rowCount(), "the exact 2x2 inner window owns four rows");
        assertTrue(forward.failures().isEmpty());

        CaveDropTrapFullChunkAudit.DriverCensusLedger reverse =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        7, 11, 2, schema, worldSeed);
        for (int index = coordinates.size() - 1; index >= 0; index--) {
            int x = coordinates.get(index)[0];
            int z = coordinates.get(index)[1];
            reverse.scanRequestedChunk(
                    x, z, random -> deterministicDriverRow(x, z, random.nextBoolean()));
        }
        assertEquals(
                forward.rowsInWindowOrder(),
                reverse.rowsInWindowOrder(),
                "load order cannot change driver RNG keys or row bytes");
        long original = CaveDropTrapFullChunkAudit.driverCensusRngKey(
                schema, worldSeed, 7, 11);
        long same = CaveDropTrapFullChunkAudit.driverCensusRngKey(
                schema, worldSeed, 7, 11);
        long changedCoordinate = CaveDropTrapFullChunkAudit.driverCensusRngKey(
                schema, worldSeed, 8, 11);
        assertEquals(original, same);
        assertNotEquals(original, changedCoordinate);
        assertEquals(
                16,
                CaveDropTrapFullChunkAudit.serializeRngKey(original).length(),
                "serialized driver keys are fixed-width and byte-stable");
        Method driverJson = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "passACensusJson",
                CaveDropTrapFeature.PassACensusSnapshot.class,
                String.class);
        driverJson.setAccessible(true);
        List<String> forwardBytes = new ArrayList<>();
        for (CaveDropTrapFullChunkAudit.DriverCensusRow row
                : forward.rowsInWindowOrder()) {
            forwardBytes.add(((JsonObject) driverJson.invoke(
                    null, row.snapshot(), row.rngKey())).toString());
        }
        List<String> reverseBytes = new ArrayList<>();
        for (CaveDropTrapFullChunkAudit.DriverCensusRow row
                : reverse.rowsInWindowOrder()) {
            reverseBytes.add(((JsonObject) driverJson.invoke(
                    null, row.snapshot(), row.rngKey())).toString());
        }
        assertEquals(forwardBytes, reverseBytes,
                "load order produces byte-identical per-row JSON");
        CaveDropTrapFullChunkAudit.DriverCensusRow firstDriverRow =
                forward.row(7, 11);
        JsonObject driverPayload = (JsonObject) driverJson.invoke(
                null, firstDriverRow.snapshot(), firstDriverRow.rngKey());
        assertEquals(
                firstDriverRow.rngKey(),
                driverPayload.get("driverRngKey").getAsString(),
                "every driver-owned row serializes its deterministic RNG key");

        Field callback = CaveDropTrapFeature.class.getDeclaredField("passACensusCallback");
        callback.setAccessible(true);
        CaveDropTrapFeature.setPassACensusCallback(null);
        assertNull(callback.get(null));
        assertEquals(4, forward.rowCount(),
                "zero production callbacks cannot remove or satisfy driver-owned coverage");

        CaveDropTrapFullChunkAudit.DriverCensusLedger wrongKey =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        0, 0, 1, schema, worldSeed);
        wrongKey.recordDriverResult(
                0, 0, "0000000000000000", deterministicDriverRow(0, 0, false));
        assertEquals(0, wrongKey.rowCount());
        assertTrue(wrongKey.failures().getFirst().contains("wrong driver census RNG key"));

        CaveDropTrapFullChunkAudit.DriverCensusLedger wrongCoordinate =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        0, 0, 1, schema, worldSeed);
        wrongCoordinate.scanRequestedChunk(
                0, 0, random -> deterministicDriverRow(1, 0, false));
        assertEquals(0, wrongCoordinate.rowCount());
        assertTrue(wrongCoordinate.failures().getFirst()
                .contains("wrong driver census row coordinate"));

        CaveDropTrapFullChunkAudit.DriverCensusLedger duplicate =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        0, 0, 1, schema, worldSeed);
        duplicate.scanRequestedChunk(
                0, 0, random -> deterministicDriverRow(0, 0, false));
        duplicate.scanRequestedChunk(
                0, 0, random -> deterministicDriverRow(0, 0, false));
        assertEquals(1, duplicate.rowCount());
        assertTrue(duplicate.failures().getFirst().contains("duplicate driver census row"));

        CaveDropTrapFullChunkAudit.DriverCensusLedger exception =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        0, 0, 1, schema, worldSeed);
        exception.scanRequestedChunk(0, 0, random -> {
            throw new IllegalStateException("focused boom");
        });
        assertEquals(0, exception.rowCount());
        assertTrue(exception.failures().getFirst().contains("focused boom"));

        CaveDropTrapFullChunkAudit.DriverCensusLedger zero =
                new CaveDropTrapFullChunkAudit.DriverCensusLedger(
                        0, 0, 1, schema, worldSeed);
        zero.scanRequestedChunk(
                0, 0, random -> CaveDropTrapFeature.PassACensusSnapshot.empty(0, 0));
        assertEquals(0, zero.rowCount(), "zero-domain rows are never inserted");
        assertTrue(zero.failures().getFirst().contains("zero-domain"));

        String auditResource = "/"
                + CaveDropTrapFullChunkAudit.class.getName().replace('.', '/') + "$Run.class";
        byte[] auditBytes;
        try (InputStream stream = CaveDropTrapFeatureContractTest.class
                .getResourceAsStream(auditResource)) {
            assertNotNull(stream);
            auditBytes = stream.readAllBytes();
        }
        var auditModel = java.lang.classfile.ClassFile.of().parse(auditBytes);
        var generate = auditModel.methods().stream()
                .filter(method -> method.methodName().equalsString("generateFullChunks"))
                .findFirst().orElseThrow();
        Set<String> generateCalls = generate.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(generateCalls.containsAll(Set.of(
                        "getChunk", "scanRequestedChunk")),
                "the driver scans each requested chunk only after requesting FULL");
        assertFalse(generateCalls.contains("applyScene"),
                "the driver-owned census has no gameplay write path");
    }

    @Test
    void firstReturnTracingDistinguishesFloorHeadroomFluidRoofAndAuthored()
            throws Exception {
        CaveDropTrap.RoofedGalleryThroatPlan plan =
                CaveDropTrap.planRoofedGalleryThroat(
                        40, CaveDropTrap.RibbonOrientation.NORTH);
        CaveDropTrap.Voxel mouth = plan.continuationFloorVoxels().getFirst();
        CaveDropTrap.Cell coverCell = plan.coverCells().getFirst();
        CaveDropTrap.Voxel cover =
                new CaveDropTrap.Voxel(coverCell.x(), plan.floorY(), coverCell.z());
        CaveDropTrap.Voxel headroom = plan.headroomVoxels().getFirst();
        BlockPos ownerAnchor = new BlockPos(100, 40, 200);

        CaveDropTrapFeature.PassATracedEvaluation floorFluid = tracedEvaluation(
                plan, ownerAnchor, Map.of(mouth, CaveDropTrap.ThroatBlockKind.FLUID),
                Set.of(), false);
        assertEquals(
                CaveDropTrap.PassARejection.SEAFLOOR_OR_FLUID_STANDING,
                floorFluid.evaluation().rejection());
        assertWitness(
                floorFluid.firstWitness(),
                CaveDropTrapFeature.PassAPredicateRole.MOUTH_OR_CONTINUATION_FLOOR,
                "minecraft:water",
                "FLUID",
                false);

        CaveDropTrapFeature.PassATracedEvaluation genericInvalid = tracedEvaluation(
                plan, ownerAnchor, Map.of(mouth, CaveDropTrap.ThroatBlockKind.OTHER),
                Set.of(), false);
        assertEquals(
                CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS,
                genericInvalid.evaluation().rejection());
        assertWitness(
                genericInvalid.firstWitness(),
                CaveDropTrapFeature.PassAPredicateRole.MOUTH_OR_CONTINUATION_FLOOR,
                "minecraft:dirt",
                "OTHER",
                false);

        CaveDropTrapFeature.PassATracedEvaluation actualAuthored = tracedEvaluation(
                plan, ownerAnchor, Map.of(mouth, CaveDropTrap.ThroatBlockKind.OTHER),
                Set.of(mouth), false);
        assertEquals(
                CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS,
                actualAuthored.evaluation().rejection());
        assertWitness(
                actualAuthored.firstWitness(),
                CaveDropTrapFeature.PassAPredicateRole.MOUTH_OR_CONTINUATION_FLOOR,
                "minecraft:dirt",
                "OTHER",
                true);
        assertNotEquals(
                genericInvalid.firstWitness().provenanceKey(),
                actualAuthored.firstWitness().provenanceKey(),
                "actual-authored and generic invalid witnesses remain separate keys");

        CaveDropTrapFeature.PassATracedEvaluation coverInvalid = tracedEvaluation(
                plan, ownerAnchor, Map.of(cover, CaveDropTrap.ThroatBlockKind.OTHER),
                Set.of(), false);
        assertEquals(
                CaveDropTrapFeature.PassAPredicateRole.COVER_FLOOR,
                coverInvalid.firstWitness().predicateRole());

        CaveDropTrapFeature.PassATracedEvaluation blockedHeadroom = tracedEvaluation(
                plan, ownerAnchor,
                Map.of(headroom, CaveDropTrap.ThroatBlockKind.OTHER),
                Set.of(), false);
        assertEquals(
                CaveDropTrapFeature.PassAPredicateRole.HEADROOM,
                blockedHeadroom.firstWitness().predicateRole());
        assertEquals("OTHER", blockedHeadroom.firstWitness().blockKind());

        CaveDropTrapFeature.PassATracedEvaluation exposedRoof = tracedEvaluation(
                plan, ownerAnchor, Map.of(), Set.of(), true);
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                exposedRoof.evaluation().rejection());
        assertEquals(
                CaveDropTrapFeature.PassAPredicateRole.ROOF,
                exposedRoof.firstWitness().predicateRole());
        assertEquals(40, exposedRoof.firstWitness().floorY());
        assertEquals(64, exposedRoof.firstWitness().surfaceY());
        assertEquals(-24, exposedRoof.firstWitness().surfaceOffset());

        List<CaveDropTrapFeature.PassATracedEvaluation> traced = List.of(
                floorFluid,
                genericInvalid,
                actualAuthored,
                coverInvalid,
                blockedHeadroom,
                exposedRoof);
        CaveDropTrapFeature.PassACensusSnapshot aggregate =
                CaveDropTrapFeature.PassACensusSnapshot.empty(0, 0);
        for (CaveDropTrapFeature.PassATracedEvaluation result : traced) {
            aggregate = aggregate.plus(rejectedPassARow(
                    0, 0, result.evaluation().rejection(), result.firstWitness()));
        }
        assertEquals(traced.size(), aggregate.scanSlots());
        assertTrue(aggregate.provenanceReconciles());
        assertTrue(aggregate.reconciles());
        assertEquals(
                traced.size(),
                aggregate.provenanceCounts().stream()
                        .mapToLong(CaveDropTrapFeature.PassAProvenanceCount::count)
                        .sum());

        Method jsonMethod = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "passACensusJson", CaveDropTrapFeature.PassACensusSnapshot.class);
        jsonMethod.setAccessible(true);
        JsonObject json = (JsonObject) jsonMethod.invoke(
                null,
                rejectedPassARow(
                        0, 0,
                        actualAuthored.evaluation().rejection(),
                        actualAuthored.firstWitness()));
        JsonObject payload = json.getAsJsonArray("firstFailureProvenance")
                .get(0).getAsJsonObject();
        for (String field : List.of(
                "key", "count", "terminalBucket", "source", "predicateRole",
                "worldPositionSemantics",
                "worldPosition", "blockId", "blockKind", "actualAuthored",
                "floorY", "surfaceY", "surfaceOffset")) {
            assertTrue(payload.has(field), "provenance payload retains " + field);
        }
        assertTrue(payload.get("actualAuthored").getAsBoolean());
        assertEquals("PREDICATE_QUERY", payload.get("source").getAsString());
        assertEquals("CAUSAL_QUERY",
                payload.get("worldPositionSemantics").getAsString());
        assertEquals("MOUTH_OR_CONTINUATION_FLOOR",
                payload.get("predicateRole").getAsString());

        CaveDropTrapFeature.PassACensusSnapshot mismatched =
                rejectedPassARowWithProvenanceCount(
                        0, 0,
                        genericInvalid.evaluation().rejection(),
                        genericInvalid.firstWitness(),
                        2);
        assertFalse(mismatched.provenanceReconciles());
        assertFalse(mismatched.reconciles(),
                "provenance totals cannot drift from the terminal bucket or scan total");
    }

    @Test
    void rewardPreflightNullUsesContextOnlyAndNeverRelabelsThePriorPredicateQuery()
            throws Exception {
        CaveDropTrap.RoofedGalleryThroatPlan plan =
                CaveDropTrap.planRoofedGalleryThroat(
                        40, CaveDropTrap.RibbonOrientation.NORTH);
        BlockPos ownerAnchor = new BlockPos(100, 40, 200);
        CaveDropTrapFeature.PassATracedEvaluation predicatePass =
                tracedEvaluation(plan, ownerAnchor, Map.of(), Set.of(), false);
        assertEquals(
                CaveDropTrap.PassARejection.PASS,
                predicatePass.evaluation().rejection());
        CaveDropTrapFeature.PassAFirstWitness priorQuery =
                predicatePass.firstWitness();
        assertEquals(
                CaveDropTrapFeature.PassAProvenanceSource.PREDICATE_QUERY,
                priorQuery.source());
        assertNotEquals("none", priorQuery.blockId());

        CaveDropTrap.RoofedThroatEvaluation rewardPreflightNull =
                new CaveDropTrap.RoofedThroatEvaluation(
                        CaveDropTrap.PassARejection.DISCONNECTED_LOWER_OR_REWARD,
                        false,
                        false,
                        CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED,
                        false,
                        CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED);
        CaveDropTrapFeature.PassAFirstWitness context =
                CaveDropTrapFeature.passAFinalWitness(
                        predicatePass, rewardPreflightNull, ownerAnchor, plan.floorY());
        assertEquals(
                CaveDropTrapFeature.PassAProvenanceSource.REWARD_PREFLIGHT,
                context.source());
        assertEquals(
                CaveDropTrapFeature.PassAPredicateRole.REWARD_PREFLIGHT,
                context.predicateRole());
        assertEquals(ownerAnchor, context.worldPosition(),
                "the coordinate is owner-anchor context, not the prior causal query");
        assertEquals("none", context.blockId());
        assertEquals("NONE", context.blockKind());
        assertFalse(context.actualAuthored());
        assertEquals(Integer.MIN_VALUE, context.surfaceY());
        assertEquals(Integer.MIN_VALUE, context.surfaceOffset());
        assertNotEquals(priorQuery.provenanceKey(), context.provenanceKey());

        CaveDropTrapFeature.PassACensusSnapshot row = rejectedPassARow(
                0, 0, rewardPreflightNull.rejection(), context);
        assertTrue(row.provenanceReconciles());
        assertTrue(row.reconciles());
        Method jsonMethod = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "passACensusJson", CaveDropTrapFeature.PassACensusSnapshot.class);
        jsonMethod.setAccessible(true);
        JsonObject payload = ((JsonObject) jsonMethod.invoke(null, row))
                .getAsJsonArray("firstFailureProvenance")
                .get(0).getAsJsonObject();
        assertEquals("REWARD_PREFLIGHT", payload.get("source").getAsString());
        assertEquals("REWARD_PREFLIGHT", payload.get("predicateRole").getAsString());
        assertEquals("CONTEXT_OWNER_ANCHOR",
                payload.get("worldPositionSemantics").getAsString());
        assertEquals("100,40,200", payload.get("worldPosition").getAsString());
        assertTrue(payload.get("blockId").isJsonNull());
        assertTrue(payload.get("blockKind").isJsonNull());
        assertTrue(payload.get("actualAuthored").isJsonNull());
        assertTrue(payload.get("surfaceY").isJsonNull());
        assertTrue(payload.get("surfaceOffset").isJsonNull());

        assertEquals(
                priorQuery,
                CaveDropTrapFeature.passAFinalWitness(
                        predicatePass,
                        predicatePass.evaluation(),
                        ownerAnchor,
                        plan.floorY()),
                "direct predicate results preserve their exact causal witness");
    }

    @Test
    void censusHypothesisAdvancesOnlyNaturalFallingSolidDefects() {
        assertEquals(
                CaveDropTrapFeature.CensusSolidDefect.FALLING_NATURAL_STABILIZABLE,
                CaveDropTrapFeature.classifyCensusSolidDefect(
                        true, Blocks.GRAVEL.defaultBlockState(), false));
        assertTrue(CaveDropTrapFeature.censusHypotheticalDefectAccepted(
                CaveDropTrapFeature.CensusSolidDefect.FALLING_NATURAL_STABILIZABLE));

        List<CaveDropTrapFeature.CensusSolidDefect> rejected = List.of(
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.AIR.defaultBlockState(), false),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.WATER.defaultBlockState(), false),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.IRON_ORE.defaultBlockState(), false),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.STONE.defaultBlockState(), true),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.BEDROCK.defaultBlockState(), false),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.MAGMA_BLOCK.defaultBlockState(), false),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                false, Blocks.GRAVEL.defaultBlockState(), false),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.SAND.defaultBlockState(), false),
                        CaveDropTrapFeature.classifyCensusSolidDefect(
                                true, Blocks.GLASS.defaultBlockState(), false));
        assertEquals(List.of(
                        CaveDropTrapFeature.CensusSolidDefect.VOID_OR_AIR,
                        CaveDropTrapFeature.CensusSolidDefect.FLUID,
                        CaveDropTrapFeature.CensusSolidDefect.ORE_OR_BLOCK_ENTITY,
                        CaveDropTrapFeature.CensusSolidDefect.ORE_OR_BLOCK_ENTITY,
                        CaveDropTrapFeature.CensusSolidDefect.PROTECTED_BEDROCK_OR_MAGMA,
                        CaveDropTrapFeature.CensusSolidDefect.PROTECTED_BEDROCK_OR_MAGMA,
                        CaveDropTrapFeature.CensusSolidDefect.OUTSIDE_OWNER,
                        CaveDropTrapFeature.CensusSolidDefect.OTHER,
                        CaveDropTrapFeature.CensusSolidDefect.OTHER),
                rejected);
        rejected.forEach(actual ->
            assertFalse(CaveDropTrapFeature.censusHypotheticalDefectAccepted(actual),
                    actual + " must remain a hard rejection"));
        assertEquals(
                CaveDropTrapFeature.CensusSolidDefect.NONE,
                CaveDropTrapFeature.classifyCensusSolidDefect(
                        true, Blocks.STONE.defaultBlockState(), false));
        assertTrue(CaveDropTrapFeature.censusHypotheticalDefectAccepted(
                CaveDropTrapFeature.CensusSolidDefect.NONE),
                "ordinary stable natural mass remains the unchanged passing case");
    }

    @Test
    void censusGravityProjectionRejectsOnlyExposedUnstabilizedFallingNeighbors() {
        assertTrue(CaveDropTrapFeature.censusExposedGravityHazard(
                Blocks.GRAVEL.defaultBlockState(), false, true));
        assertFalse(CaveDropTrapFeature.censusExposedGravityHazard(
                Blocks.GRAVEL.defaultBlockState(), true, true),
                "a projected replacement or required-solid stabilization removes the hazard");
        assertFalse(CaveDropTrapFeature.censusExposedGravityHazard(
                Blocks.GRAVEL.defaultBlockState(), false, false),
                "gravity mass is irrelevant when the block below is not projected air");
        assertFalse(CaveDropTrapFeature.censusExposedGravityHazard(
                Blocks.STONE.defaultBlockState(), false, true),
                "non-falling natural mass is not a gravity-neighbor hazard");
    }

    @Test
    void censusEnvelopeDiagnosticTranslatesStaticTemplateVoxelsToTheCandidateFloor() throws Exception {
        CaveDropTrap.EmbeddedRibbonPlan shape = CaveDropTrap.planEmbeddedRibbon(
                0, CaveDropTrap.RibbonOrientation.NORTH, ignored -> true, ignored -> true);
        assertNotNull(shape);
        CaveDropTrap.Voxel template = shape.requiredSolidVoxels().getFirst();
        Method censusPosition = CaveDropTrapFeature.class.getDeclaredMethod(
                "censusTemplatePosition",
                CaveDropTrap.EmbeddedRibbonPlan.class,
                CaveDropTrap.Voxel.class,
                int.class, int.class, int.class, int.class, int.class);
        censusPosition.setAccessible(true);
        int candidateFloorY = 57;
        int baseX = -2_864;
        int baseZ = 9_280;
        int originLx = 6;
        int originLz = 7;
        BlockPos sampled = (BlockPos) censusPosition.invoke(
                null, shape, template, candidateFloorY, baseX, baseZ, originLx, originLz);
        assertEquals(baseX + originLx + template.x(), sampled.getX());
        assertEquals(candidateFloorY + template.y() - shape.floorY(), sampled.getY(),
                "the census must sample the candidate floor's real Y, never the template's Y=0");
        assertEquals(baseZ + originLz + template.z(), sampled.getZ());
        assertNotEquals(template.y(), sampled.getY(),
                "a nonzero candidate floor must not be diagnosed near world Y=0");
    }

    @Test
    void censusSnapshotReconcilesAndSerializesEveryRequiredStageField() throws Exception {
        CaveDropTrapFeature.CensusRepresentative representative =
                new CaveDropTrapFeature.CensusRepresentative(
                        4, -179, 580, CaveDropTrap.RibbonOrientation.NORTH,
                        4, 5, 40, 3, new BlockPos(4, 39, 5), "minecraft:stone",
                        2, new BlockPos(4, 41, 5), "minecraft:water", 5, 3,
                        new CaveDropTrapFeature.CensusDefectCounts(3, 1, 1, 1, 1, 1, 1),
                        1, true);
        CaveDropTrapFeature.CensusSnapshot snapshot = new CaveDropTrapFeature.CensusSnapshot(
                -179, 580,
                10, 5, 5,
                2, 1, 0, 0, 0, 2,
                3, 1, 1, 1,
                2, 1, 1, 0,
                1, 0, 1,
                new CaveDropTrapFeature.CensusDefectCounts(3, 1, 1, 1, 1, 1, 1),
                representative, List.of(representative));
        assertTrue(snapshot.reconciles(), "every no-write slot must have one terminal stage");
        CaveDropTrapFeature.CensusSnapshot merged = snapshot.plus(snapshot);
        assertTrue(merged.reconciles());
        assertEquals(20, merged.scanSlots());

        Method censusJson = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "censusJson", CaveDropTrapFeature.CensusSnapshot.class);
        censusJson.setAccessible(true);
        JsonObject json = (JsonObject) censusJson.invoke(null, snapshot);
        for (String required : List.of(
                "chunkX", "chunkZ", "scanSlots", "referenceFloorHeadroomPass",
                "referenceFloorHeadroomFail", "strictEnvelopePass",
                "stabilizationOnlyEnvelopePass", "envelopeFailures",
                "requiredSolidDefects", "enclosurePass", "enclosureFail",
                "stabilizationOnlyEnclosurePass", "stabilizationOnlyEnclosureFail",
                "gravityHazardPass", "gravityHazardFail",
                "stabilizationOnlyGravityHazardPass",
                "stabilizationOnlyGravityHazardFail", "materializationPass",
                "materializationFail", "hypotheticalMaterialization",
                "reconciles", "representative", "terminalRepresentatives")) {
            assertTrue(json.has(required), "census JSON must retain " + required);
        }
        JsonObject envelope = json.getAsJsonObject("envelopeFailures");
        assertTrue(envelope.has("solid") && envelope.has("air")
                        && envelope.has("both") && envelope.has("other"),
                "the envelope census separates each exact predicate outcome");
        JsonObject nearMiss = json.getAsJsonObject("representative");
        assertTrue(nearMiss.has("firstSolidFailure") && nearMiss.has("firstSolidState")
                        && nearMiss.has("firstAirFailure") && nearMiss.has("firstAirState")
                        && nearMiss.has("enclosureMinimumRise")
                        && nearMiss.has("enclosureFailingColumns")
                        && nearMiss.has("solidDefects")
                        && nearMiss.has("exposedGravityHazards")
                        && nearMiss.has("stabilizationOnly"),
                "a compact near miss retains the concrete predicate evidence needed for one next fix");
        assertEquals(
                "NOT_MODELED",
                json.getAsJsonObject("hypotheticalMaterialization")
                        .get("status").getAsString(),
                "the no-write census must not fake materialization or selection counts");
    }

    @Test
    void passACensusV5ReconcilesSourceTopologyFeasibilitySelectionAndEveryRejectBucket()
            throws Exception {
        CaveDropTrapFeature.PassACensusRepresentative representative =
                new CaveDropTrapFeature.PassACensusRepresentative(
                        "CAVITY_AIR_OR_FLUID",
                        new BlockPos(4, 40, 5),
                        CaveDropTrap.RibbonOrientation.NORTH,
                        40,
                        CaveDropTrap.PassARejection.CAVITY_AIR_OR_FLUID,
                        null,
                        -1,
                        null,
                        null,
                        false);
        CaveDropTrap.RoofedThroatMeasurements measured =
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true);
        CaveDropTrapFeature.PassACensusSnapshot snapshot =
                new CaveDropTrapFeature.PassACensusSnapshot(
                        -179, 580,
                        1, 196, 10, 186,
                        10, 8, 2,
                        1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0,
                        4,
                        1, 3,
                        2, 2,
                        2, 2,
                        1, 1,
                        1, 0, 0, 1,
                        rollCounts(14, 1),
                        0, 1, 0,
                        1, 0,
                        measured,
                        List.of(representative));
        assertTrue(snapshot.reconciles());
        assertTrue(snapshot.plus(snapshot).reconciles());

        Field schema = CaveDropTrapFullChunkAudit.class.getDeclaredField("CENSUS_SCHEMA");
        schema.setAccessible(true);
        assertEquals("cave-drop-candidate-census-v6", schema.get(null));
        Method jsonMethod = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "passACensusJson", CaveDropTrapFeature.PassACensusSnapshot.class);
        jsonMethod.setAccessible(true);
        JsonObject json = (JsonObject) jsonMethod.invoke(null, snapshot);
        for (String required : List.of(
                "candidateSource", "scanSlots", "uniqueOwnerAnchors", "duplicateReject",
                "topology", "terminalRejections", "ordinaryFeasible",
                "rareFeasibility", "predictedOutcomes",
                "reconciles", "terminalRepresentatives",
                "firstFailureProvenance", "provenanceReconciles")) {
            assertTrue(json.has(required), "Pass-A census v5 retains " + required);
        }
        JsonObject candidateSource = json.getAsJsonObject("candidateSource");
        assertEquals(snapshot.legalFloorCount(),
                candidateSource.get("legalFloorCount").getAsLong());
        assertEquals(snapshot.auditDomainSlots(),
                candidateSource.get("auditDomainSlots").getAsLong());
        assertEquals(snapshot.caveAnchorQualified(),
                candidateSource.get("caveAnchorQualified").getAsLong());
        assertEquals(snapshot.caveAnchorRejected(),
                candidateSource.get("caveAnchorRejected").getAsLong());
        assertTrue(candidateSource.get("reconciles").getAsBoolean());
        JsonObject topology = json.getAsJsonObject("topology");
        assertTrue(topology.get("present").getAsBoolean());
        assertEquals(12, topology.get("coverCount").getAsInt());
        assertEquals(6, topology.get("powderCount").getAsInt());
        assertEquals(6, topology.get("firmCount").getAsInt());
        assertEquals(List.of(3, 3),
                topology.getAsJsonArray("firmComponentSizes").asList().stream()
                        .map(element -> element.getAsInt()).toList());
        assertEquals(2, topology.get("mouthCount").getAsInt());
        assertTrue(topology.get("distinctMouths").getAsBoolean());
        assertTrue(topology.get("powderVertexCut").getAsBoolean());
        assertTrue(topology.get("witnessWriteDisjoint").getAsBoolean());
        JsonObject rejects = json.getAsJsonObject("terminalRejections");
        for (String bucket : List.of(
                "sameDoor", "invalidOrSelfAuthoredWitness",
                "exposedSkyWaterOrThinRoof", "seafloorOrFluidStanding",
                "firmBypassOrWrongRoles", "cavityAirOrFluid",
                "oreBlockEntityOrProtected", "gravityOrPartialStabilization",
                "shellOrSupport", "owner", "disconnectedLowerOrReward", "duplicate")) {
            assertTrue(rejects.has(bucket), "Pass-A census retains reject bucket " + bucket);
        }
        JsonObject selection = json.getAsJsonObject("predictedOutcomes");
        assertFalse(selection.has("selectorsAreProductionMethods"),
                "the report carries trace data instead of a hard-coded proof claim");
        assertEquals(1, selection.get("resolvedOrdinary").getAsLong());
        assertEquals(1, selection.get("resolvedFallback").getAsLong());
        assertEquals(1,
                selection.getAsJsonObject("requested").get("flooded").getAsLong());
        assertEquals(1,
                selection.getAsJsonObject("requestFallbacks").get("flooded").getAsLong());
    }

    @Test
    void passACensusReconciliationFailsClosedOnForgedSelectorAccounting() {
        CaveDropTrapFeature.PassACensusSnapshot wrongRoll14Request =
                selectorLawRow(
                        rollCounts(14, 1),
                        1, 0, 0, 0,
                        1, 0, 0,
                        0, 0);
        assertFalse(wrongRoll14Request.reconciles(),
                "roll 14 cannot be relabeled as an ordinary request");

        CaveDropTrapFeature.PassACensusSnapshot silentRareFallback =
                selectorLawRow(
                        rollCounts(14, 1),
                        1, 0, 0, 0,
                        0, 1, 0,
                        0, 0);
        assertFalse(silentRareFallback.reconciles(),
                "a rare request cannot silently resolve ordinary without a fallback count");

        CaveDropTrapFeature.PassACensusSnapshot inconsistentFallbackOrdinary =
                selectorLawRow(
                        rollCounts(14, 1),
                        0, 1, 0, 1,
                        0, 1, 0,
                        1, 0);
        assertFalse(inconsistentFallbackOrdinary.reconciles(),
                "a recorded rare fallback must move that outcome into resolved ordinary");

        List<Long> negativeRollCounts = new ArrayList<>(rollCounts(0, 2));
        negativeRollCounts.set(1, -1L);
        CaveDropTrapFeature.PassACensusSnapshot negativeRoll =
                selectorLawRow(
                        negativeRollCounts,
                        1, 0, 0, 0,
                        1, 0, 0,
                        0, 0);
        assertFalse(negativeRoll.reconciles(),
                "compensating negative roll counters cannot reconcile");

        CaveDropTrapFeature.PassACensusSnapshot resolvedRareExceedsRequest =
                selectorLawRow(
                        rollCounts(0, 1),
                        0, 1, 0, 0,
                        1, 0, 0,
                        0, 0);
        assertFalse(resolvedRareExceedsRequest.reconciles(),
                "a resolved rare outcome cannot exceed matching rare requests");

        CaveDropTrapFeature.PassACensusSnapshot floodedResolutionWithoutFeasibility =
                selectorFeasibilityLawRow(
                        rollCounts(14, 1),
                        0, 1,
                        1, 0,
                        0, 1, 0, 0,
                        0, 1, 0,
                        0, 0);
        assertFalse(floodedResolutionWithoutFeasibility.reconciles(),
                "a flooded resolution cannot exceed flooded-feasible candidates");

        CaveDropTrapFeature.PassACensusSnapshot
                prospectorResolutionWithoutFeasibility =
                selectorFeasibilityLawRow(
                        rollCounts(15, 1),
                        1, 0,
                        0, 1,
                        0, 0, 1, 0,
                        0, 0, 1,
                        0, 0);
        assertFalse(prospectorResolutionWithoutFeasibility.reconciles(),
                "a prospector resolution cannot exceed prospector-feasible candidates");

        CaveDropTrapFeature.PassACensusSnapshot floodedFallbackWithoutFailure =
                selectorLawRow(
                        rollCounts(14, 1),
                        1, 0, 0, 1,
                        0, 1, 0,
                        1, 0);
        assertFalse(floodedFallbackWithoutFailure.reconciles(),
                "a flooded request fallback requires a flooded-feasibility failure");

        CaveDropTrapFeature.PassACensusSnapshot prospectorFallbackWithoutFailure =
                selectorLawRow(
                        rollCounts(15, 1),
                        1, 0, 0, 1,
                        0, 0, 1,
                        0, 1);
        assertFalse(prospectorFallbackWithoutFailure.reconciles(),
                "a prospector request fallback requires a prospector-feasibility failure");
    }

    @Test
    void passACensusAggregationFailsClosedInsteadOfWrappingBackAfterLongOverflow()
            throws ReflectiveOperationException {
        long nearMaximumDomain = Long.MAX_VALUE - Long.MAX_VALUE % 196L;
        CaveDropTrapFeature.PassACensusSnapshot nearMaximum =
                allOrdinaryPassARow(
                        nearMaximumDomain,
                        nearMaximumDomain / 196L,
                        nearMaximumDomain,
                        0L);
        CaveDropTrapFeature.PassACensusSnapshot finalEight =
                allOrdinaryPassARow(8L, 1L, 196L, 188L);
        assertTrue(nearMaximum.reconciles());
        assertTrue(finalEight.reconciles());

        CaveDropTrapFeature.PassACensusSnapshot aggregate =
                nearMaximum.plus(nearMaximum).plus(finalEight);
        assertEquals(-1, aggregate.scanSlots(),
                "overflowing scalar aggregation retains an explicit invalid sentinel");
        assertEquals(-1, aggregate.rewardRollCounts().get(0),
                "overflowing roll aggregation retains an explicit invalid sentinel");
        assertFalse(aggregate.reconciles(),
                "two near-maximum rows plus eight would wrap if unchecked");

        CaveDropTrapFeature.PassACensusSnapshot everyCounterOverflow =
                allCountersPassARow(Long.MAX_VALUE)
                        .plus(allCountersPassARow(Long.MAX_VALUE));
        for (RecordComponent component
                : CaveDropTrapFeature.PassACensusSnapshot.class.getRecordComponents()) {
            if (component.getType() == long.class) {
                assertEquals(
                        -1L,
                        ((Long) component.getAccessor().invoke(everyCounterOverflow))
                                .longValue(),
                        "every scalar census counter uses checked aggregation: "
                                + component.getName());
            }
        }
        assertTrue(
                everyCounterOverflow.rewardRollCounts().stream()
                        .allMatch(count -> count == -1),
                "all sixteen roll buckets use checked aggregation");
    }

    @Test
    void censusOutcomeGateFailsMissingRowsZeroOutcomesAndOneBelowEachMinimum() {
        CaveDropTrapFullChunkAudit.CensusReportGate exact =
                CaveDropTrapFullChunkAudit.evaluateCensusReportGate(
                        256, 256, true, true, 10,
                        40, 1, 1, 40, 1, 1);
        assertTrue(exact.diagnosticReconciliationPass());
        assertTrue(exact.outcomeGatePass());
        assertTrue(exact.pass(), "the exact 40/1/1 boundary passes");

        CaveDropTrapFullChunkAudit.CensusReportGate missing =
                CaveDropTrapFullChunkAudit.evaluateCensusReportGate(
                        256, 255, true, true, 10,
                        40, 1, 1, 40, 1, 1);
        assertFalse(missing.diagnosticReconciliationPass());
        assertFalse(missing.pass(), "one missing requested coordinate fails the root gate");
        assertEquals(
                List.of("1,0"),
                CaveDropTrapFullChunkAudit.missingCensusCoordinates(
                        0, 0, 2, Set.of("0,0", "0,1", "1,1")),
                "the exact missing requested coordinate is reportable");

        CaveDropTrapFullChunkAudit.CensusReportGate zero =
                CaveDropTrapFullChunkAudit.evaluateCensusReportGate(
                        256, 256, true, true, 10,
                        0, 0, 0, 40, 1, 1);
        assertTrue(zero.diagnosticReconciliationPass());
        assertFalse(zero.outcomeGatePass());
        assertFalse(zero.pass(), "reconciled zero outcomes cannot pass");

        for (long[] observed : List.of(
                new long[] {39, 1, 1},
                new long[] {40, 0, 1},
                new long[] {40, 1, 0})) {
            CaveDropTrapFullChunkAudit.CensusReportGate below =
                    CaveDropTrapFullChunkAudit.evaluateCensusReportGate(
                            256, 256, true, true, 10,
                            observed[0], observed[1], observed[2], 40, 1, 1);
            assertFalse(below.outcomeGatePass());
            assertFalse(below.pass());
        }
    }

    @Test
    void emptyAndCorruptRowsCannotFabricateTopologySuccess() throws Exception {
        CaveDropTrapFeature.PassACensusSnapshot empty =
                CaveDropTrapFeature.PassACensusSnapshot.empty(3, 4);
        assertNull(empty.topologyMeasurements());
        assertTrue(empty.reconciles());
        Method jsonMethod = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "passACensusJson", CaveDropTrapFeature.PassACensusSnapshot.class);
        jsonMethod.setAccessible(true);
        JsonObject emptyJson = (JsonObject) jsonMethod.invoke(null, empty);
        JsonObject topology = emptyJson.getAsJsonObject("topology");
        assertFalse(topology.get("present").getAsBoolean());
        assertFalse(topology.has("coverCount"));
        assertFalse(topology.has("powderVertexCut"));

        List<CaveDropTrap.RoofedThroatMeasurements> corrupt = List.of(
                new CaveDropTrap.RoofedThroatMeasurements(
                        11, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true),
                new CaveDropTrap.RoofedThroatMeasurements(
                        22, 8, 14, List.of(6, 8),
                        2, true, true, true, true, true, true),
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        1, false, true, true, true, true, true),
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, false, true, true),
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, false, true),
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, false));
        for (CaveDropTrap.RoofedThroatMeasurements measurement : corrupt) {
            CaveDropTrapFeature.PassACensusSnapshot row =
                    passingPassARow(measurement);
            assertFalse(row.reconciles(),
                    "wrong counts, banks, mouths, vertex cut, or witness overlap fail");
        }
    }

    @Test
    void floodedEscapeDetoursAroundExactNaturalEvidenceAndKeepsWritesDisjoint() {
        int lowerStandingY = 10;
        CaveDropTrap.Cell start = new CaveDropTrap.Cell(2, 2);
        CaveDropTrap.Cell end = new CaveDropTrap.Cell(4, 2);
        Set<CaveDropTrap.Voxel> protectedEvidence = Set.of(
                new CaveDropTrap.Voxel(3, lowerStandingY + 1, 2));

        assertNull(CaveDropTrapFeature.exactEscapePath(
                start, end, 2, Set.of(), lowerStandingY, protectedEvidence),
                "the only direct route collides with protected natural evidence");

        List<CaveDropTrap.Cell> detour = CaveDropTrapFeature.exactEscapePath(
                start, end, 4, Set.of(), lowerStandingY, protectedEvidence);
        assertNotNull(detour);
        assertEquals(5, detour.size(),
                "the exact-length search explores a protected-safe alternate detour");
        Set<CaveDropTrap.Voxel> hypotheticalWrites = new HashSet<>();
        for (int index = 0; index < detour.size() - 1; index++) {
            CaveDropTrap.Cell cell = detour.get(index);
            CaveDropTrap.Voxel standing =
                    new CaveDropTrap.Voxel(cell.x(), lowerStandingY + index, cell.z());
            assertTrue(CaveDropTrap.escapeStandingPreservesEvidence(
                    standing, protectedEvidence));
            hypotheticalWrites.add(standing.below());
            hypotheticalWrites.add(standing);
            hypotheticalWrites.add(standing.above());
        }
        assertTrue(java.util.Collections.disjoint(hypotheticalWrites, protectedEvidence),
                "every support/standing/head write in the detour is evidence-disjoint");
    }

    @Test
    void prospectorReplacementRequiresAnExistingAuthoredGalleryAirWrite()
            throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Class<?> worldWrite = Arrays.stream(CaveDropTrapFeature.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("WorldWrite"))
                .findFirst().orElseThrow();
        Constructor<?> constructor = worldWrite.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Method replace = CaveDropTrapFeature.class.getDeclaredMethod(
                "replaceRequiredGalleryAirWrite",
                Map.class,
                BlockPos.class,
                net.minecraft.world.level.block.state.BlockState.class,
                CaveDropTrapFeature.AuthoredRole.class);
        replace.setAccessible(true);

        BlockPos position = new BlockPos(5, 20, 5);
        Map<BlockPos, Object> missingPlan = new HashMap<>();
        assertFalse((Boolean) replace.invoke(
                        null, missingPlan, position,
                        Blocks.CHEST.defaultBlockState(),
                        CaveDropTrapFeature.AuthoredRole.TREASURE_CHEST),
                "incidental air without an authored gallery write must fail closed");

        Object wrongRole = constructor.newInstance(
                position,
                Blocks.STONE.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                CaveDropTrapFeature.AuthoredRole.OPENED_SHAFT,
                true);
        Map<BlockPos, Object> wrongRolePlan = new HashMap<>();
        wrongRolePlan.put(position, wrongRole);
        assertFalse((Boolean) replace.invoke(
                        null, wrongRolePlan, position,
                        Blocks.CHEST.defaultBlockState(),
                        CaveDropTrapFeature.AuthoredRole.TREASURE_CHEST),
                "another planned-air role is not the deterministic lower corridor");

        Object galleryAir = constructor.newInstance(
                position,
                Blocks.STONE.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                CaveDropTrapFeature.AuthoredRole.GALLERY_AIR,
                true);
        Map<BlockPos, Object> authoredPlan = new HashMap<>();
        authoredPlan.put(position, galleryAir);
        assertTrue((Boolean) replace.invoke(
                        null, authoredPlan, position,
                        Blocks.CHEST.defaultBlockState(),
                        CaveDropTrapFeature.AuthoredRole.TREASURE_CHEST),
                "the exact authored gallery-air projection can receive the chest");
    }

    @Test
    void fullChunkAuditPinsStrictV4SchemaAndRejectsOlderBaselines()
            throws ReflectiveOperationException {
        Field schema = CaveDropTrapFullChunkAudit.class.getDeclaredField("SCHEMA");
        schema.setAccessible(true);
        assertEquals("cave-drop-full-chunk-audit-v4", schema.get(null),
                "v4 is the first schema with explicit paired-remains evidence");
        assertNotEquals("cave-drop-full-chunk-audit-v3", schema.get(null),
                "a v3 single-remains baseline must never be rescanned as paired evidence");

        Method requireCurrentSchema = CaveDropTrapFullChunkAudit.class
                .getDeclaredMethod("requireCurrentSchema", JsonObject.class);
        requireCurrentSchema.setAccessible(true);
        JsonObject current = new JsonObject();
        current.addProperty("schema", "cave-drop-full-chunk-audit-v4");
        assertDoesNotThrow(() -> requireCurrentSchema.invoke(null, current));
        for (String legacy : List.of(
                "cave-drop-full-chunk-audit-v3",
                "cave-drop-full-chunk-audit-v2")) {
            JsonObject old = new JsonObject();
            old.addProperty("schema", legacy);
            InvocationTargetException failure = assertThrows(
                    InvocationTargetException.class,
                    () -> requireCurrentSchema.invoke(null, old));
            assertInstanceOf(IOException.class, failure.getCause(),
                    "older schemas must fail closed");
        }
    }

    @Test
    void transactionWritesSuppressNeighbourShapeWorkAndDrops() throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Field field = CaveDropTrapFeature.class.getDeclaredField("SET_BLOCK_FLAGS");
        field.setAccessible(true);
        int flags = field.getInt(null);

        assertEquals(0, flags & Block.UPDATE_NEIGHBORS,
                "the transaction must not trigger gravity or attachment neighbours mid-scene");
        assertTrue((flags & Block.UPDATE_KNOWN_SHAPE) != 0,
                "known-shape writes suppress neighbour-shape cascades while the scene is incomplete");
        assertTrue((flags & Block.UPDATE_SUPPRESS_DROPS) != 0,
                "rollback-safe writes never duplicate or scatter block drops");
        assertTrue((flags & Block.UPDATE_CLIENTS) != 0,
                "the completed authored states still reach clients");
    }

    @Test
    void auditEventExposesTopologyAndPostWriteEvidenceRatherThanAggregateCountsOnly() {
        Set<String> roles = Arrays.stream(CaveDropTrapFeature.AuthoredRole.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertTrue(roles.contains("OPENED_SHAFT"),
                "the strict schema names authored vertical air for what it actually is");
        assertFalse(roles.contains("OPENED_SHELL"),
                "the rejected thin-overhang grammar cannot survive in audit output");
        assertTrue(roles.containsAll(Set.of(
                        "COLLAPSED_EXPLORER_FOOT",
                        "COLLAPSED_EXPLORER_HEAD")),
                "the complete body needs one authored role per physical half");
        assertFalse(roles.contains("COLLAPSED_EXPLORER"),
                "an ambiguous single remains role cannot survive schema v4");

        Set<String> components = Arrays.stream(CaveDropTrapFeature.AuditEvent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertTrue(components.containsAll(Set.of(
                "approachAxis",
                "firstApproachBank",
                "secondApproachBank",
                "firmCamouflagePositions",
                "entries",
                "dryExitPositions",
                "chestFront",
                "chestToExitPath",
                "remainsFootPosition",
                "remainsHeadPosition",
                "shorePositions",
                "galleryPositions",
                "floodedExitPath",
                "authoredPositions",
                "expectedFingerprint",
                "observedFingerprint",
                "postWriteVerified",
                "upperBookendPositions",
                "firstNaturalContinuation",
                "secondNaturalContinuation",
                "upperNaturalReturn")),
                "the headless inspector needs concrete route, authored-role, and post-read witnesses");
        assertFalse(components.contains("remainsPosition"),
                "schema v4 cannot expose the old ambiguous single remains position");
        assertFalse(components.contains("allEntrancesReachExit"),
                "a self-reported boolean is not a topology proof; the event carries the actual paths");
        assertEquals(1, CaveDropTrapFeature.AuditEvent.class.getDeclaredConstructors().length,
                "the strict evidence record has no compatibility constructors that can omit v4 fields");
        assertEquals(
                CaveDropTrapFeature.AuditEvent.class.getRecordComponents().length,
                CaveDropTrapFeature.AuditEvent.class.getDeclaredConstructors()[0].getParameterCount(),
                "the sole evidence constructor is the complete canonical v4 constructor");

        Set<String> entryComponents = Arrays.stream(
                        CaveDropTrapFeature.EntryEvidence.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertTrue(entryComponents.contains("shaftPositions"),
                "every entrance must expose its complete ordered authored shaft");

        Set<String> authoredComponents = Arrays.stream(
                        CaveDropTrapFeature.AuthoredPosition.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertTrue(authoredComponents.containsAll(Set.of(
                        "priorState", "priorBlockEntityAbsent", "expected", "observed")),
                "post-write proof must retain the actual pre-write state and block-entity absence");

        Class<?> navigationAssay = Arrays.stream(
                        CaveDropTrapFullChunkAudit.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("MobNavigationAssay"))
                .findFirst()
                .orElseThrow();
        Set<String> assayComponents = Arrays.stream(navigationAssay.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertTrue(assayComponents.containsAll(Set.of(
                        "pathSelected",
                        "descentObserved",
                        "ticksObserved",
                        "finalPosition")),
                "navigation proof records real ticked descent, not path selection alone");
    }

    @Test
    void atomicReadbackUsesOneExactBlockEntityPolicyPerAuthoredRole()
            throws ReflectiveOperationException, IOException {
        Method policyMethod = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "authoredBlockEntityPolicy", CaveDropTrapFeature.AuthoredRole.class);
        policyMethod.setAccessible(true);
        Class<?> policyClass = policyMethod.getReturnType();
        Map<CaveDropTrapFeature.AuthoredRole, String> specialPolicies = Map.of(
                CaveDropTrapFeature.AuthoredRole.TREASURE_CHEST,
                "CHEST",
                CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_FOOT,
                "COLLAPSED_EXPLORER_FOOT",
                CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_HEAD,
                "COLLAPSED_EXPLORER_HEAD");
        Map<CaveDropTrapFeature.AuthoredRole, Object> policies = new HashMap<>();
        for (CaveDropTrapFeature.AuthoredRole role :
                CaveDropTrapFeature.AuthoredRole.values()) {
            Object policy = policyMethod.invoke(null, role);
            policies.put(role, policy);
            assertEquals(specialPolicies.getOrDefault(role, "NONE"), policy.toString(),
                    role + " has exactly one block-entity allowance");
        }

        Method matchesMethod = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "authoredBlockEntityMatches", policyClass, BlockEntity.class);
        matchesMethod.setAccessible(true);
        Object none = policies.get(CaveDropTrapFeature.AuthoredRole.GALLERY_AIR);
        Object chest = policies.get(CaveDropTrapFeature.AuthoredRole.TREASURE_CHEST);
        Object foot = policies.get(CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_FOOT);
        Object head = policies.get(CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_HEAD);
        BlockEntity arbitrary =
                new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        ChestBlockEntity realChest =
                new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        assertEquals(true, matchesMethod.invoke(null, none, null),
                "ordinary authored roles require the absence of a block entity");
        assertEquals(false, matchesMethod.invoke(null, none, arbitrary),
                "ordinary authored roles must reject arbitrary block entities");
        assertEquals(true, matchesMethod.invoke(null, chest, realChest),
                "only a real ChestBlockEntity satisfies TREASURE_CHEST");
        assertEquals(false, matchesMethod.invoke(null, chest, arbitrary));
        assertEquals(false, matchesMethod.invoke(null, foot, null));
        assertEquals(false, matchesMethod.invoke(null, foot, realChest));
        assertEquals(false, matchesMethod.invoke(null, head, null));
        assertEquals(false, matchesMethod.invoke(null, head, arbitrary));

        Class<?> runClass = Arrays.stream(CaveDropTrapFullChunkAudit.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Run"))
                .findFirst().orElseThrow();
        String resource = "/" + runClass.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream stream =
                CaveDropTrapFeatureContractTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "compiled full-chunk audit Run oracle is available");
            bytes = stream.readAllBytes();
        }
        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        var atomicReadback = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("validateAtomicReadback"))
                .findFirst().orElseThrow();
        List<java.lang.classfile.instruction.InvokeInstruction> atomicCalls =
                atomicReadback.code().orElseThrow().elementStream()
                        .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                        .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                        .toList();
        assertTrue(atomicCalls.stream().anyMatch(
                        call -> call.name().equalsString("validateAuthoredBlockEntity")),
                "generic atomic readback must route every role through the exact policy validator");
        assertFalse(atomicCalls.stream().anyMatch(
                        call -> call.name().equalsString("getBlockEntity")),
                "the obsolete blanket non-chest-null assertion must not recur in atomic readback");

        var validator = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("validateAuthoredBlockEntity"))
                .findFirst().orElseThrow();
        Set<String> validatorCalls = validator.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.name().stringValue())
                .collect(Collectors.toSet());
        assertTrue(validatorCalls.containsAll(Set.of(
                        "getBlockEntity",
                        "authoredBlockEntityPolicy",
                        "authoredBlockEntityMatches")),
                "the one generic adapter reads the actual BE, selects the role policy, and enforces it");
    }

    @Test
    void auditOraclePinsExactTemplateOrePayoffAndAttributedDescentThreshold()
            throws ReflectiveOperationException {
        Field entryCount = CaveDropTrapFullChunkAudit.class
                .getDeclaredField("INNER_TRAP_ENTRY_COUNT");
        entryCount.setAccessible(true);
        assertEquals(8, entryCount.getInt(null),
                "the audit oracle rejects every non-eight-entry production template");

        Field oreComposition = CaveDropTrapFullChunkAudit.class
                .getDeclaredField("FLOODED_ORE_COMPOSITION");
        oreComposition.setAccessible(true);
        assertEquals(
                Map.of(
                        "minecraft:deepslate_diamond_ore", 2,
                        "minecraft:deepslate_gold_ore", 4,
                        "minecraft:deepslate_iron_ore", 5,
                        "minecraft:deepslate_redstone_ore", 5),
                oreComposition.get(null),
                "the rich flooded payoff is an exact sixteen-ore 2/4/5/5 contract");

        Method descent = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "descentObserved",
                BlockPos.class,
                double.class,
                double.class,
                double.class);
        descent.setAccessible(true);
        BlockPos entrance = new BlockPos(10, 40, -4);
        assertEquals(true, descent.invoke(null, entrance, 10.5D, 38.0D, -3.5D),
                "two blocks down in the selected entrance column is a real descent");
        assertEquals(false, descent.invoke(null, entrance, 10.5D, 38.001D, -3.5D),
                "path selection or a shallow dip cannot pass as descent");
        assertEquals(false, descent.invoke(null, entrance, 11.0D, 37.0D, -3.5D),
                "a deep position in a different column is not attributed to the selected shaft");
    }

    @Test
    void rewardOracleAcceptsOnlyTheExactSixteenRollOutcomeMap()
            throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        for (int roll = 0; roll <= 13; roll++) {
            assertNull(rewardError(eventForReward(
                            roll, CaveDropTrap.RewardKind.ORDINARY, null)),
                    "ordinary roll " + roll + " is the only no-fallback outcome");
        }
        assertNull(rewardError(eventForReward(
                14, CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY, null)));
        assertNull(rewardError(eventForReward(
                14, CaveDropTrap.RewardKind.ORDINARY, "flooded prerequisites absent")));
        assertNull(rewardError(eventForReward(
                15, CaveDropTrap.RewardKind.LOST_PROSPECTOR, null)));
        assertNull(rewardError(eventForReward(
                15, CaveDropTrap.RewardKind.ORDINARY, "prospector prerequisites absent")));

        assertNotNull(rewardError(eventForReward(
                -1, CaveDropTrap.RewardKind.ORDINARY, null)));
        assertNotNull(rewardError(eventForReward(
                16, CaveDropTrap.RewardKind.ORDINARY, null)));
        assertNotNull(rewardError(eventForReward(
                0, CaveDropTrap.RewardKind.LOST_PROSPECTOR, null)));
        assertNotNull(rewardError(eventForReward(
                14, CaveDropTrap.RewardKind.ORDINARY, " ")));
        assertNotNull(rewardError(eventForReward(
                14, CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY, "unexpected fallback")));
        assertNotNull(rewardError(eventForReward(
                15, CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY, null)));
        assertNotNull(rewardError(eventForReward(
                15, CaveDropTrap.RewardKind.LOST_PROSPECTOR, "unexpected fallback")));
    }

    @Test
    void pairedRemainsJsonRoundTripsAndRejectsAmbiguousRows()
            throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        BlockPos anchor = new BlockPos(2, 40, 2);
        BlockPos landing = new BlockPos(2, 20, 2);
        BlockPos exit = new BlockPos(3, 20, 2);
        BlockPos foot = new BlockPos(5, 20, 5);
        BlockPos head = new BlockPos(5, 20, 4);
        BlockPos chest = new BlockPos(6, 20, 4);
        BlockPos front = new BlockPos(7, 20, 4);
        CaveDropTrapFeature.EntryEvidence entry =
                new CaveDropTrapFeature.EntryEvidence(
                        anchor,
                        landing,
                        exit,
                        List.of(landing, exit),
                        null,
                        List.of(),
                        List.of(landing, front),
                        List.of(anchor.below()));
        CaveDropTrapFeature.AuthoredPosition footWrite =
                new CaveDropTrapFeature.AuthoredPosition(
                        foot,
                        CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_FOOT,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.STONE.defaultBlockState(),
                        Blocks.STONE.defaultBlockState(),
                        true);
        CaveDropTrapFeature.AuthoredPosition headWrite =
                new CaveDropTrapFeature.AuthoredPosition(
                        head,
                        CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_HEAD,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.STONE.defaultBlockState(),
                        Blocks.STONE.defaultBlockState(),
                        true);
        CaveDropTrapFeature.AuditEvent original =
                new CaveDropTrapFeature.AuditEvent(
                        anchor,
                        15,
                        CaveDropTrap.RewardKind.LOST_PROSPECTOR,
                        List.of(anchor),
                        List.of(anchor.east()),
                        landing.getY(),
                        landing.getY(),
                        anchor.getY() - landing.getY(),
                        anchor.getY() - landing.getY(),
                        List.of(),
                        List.of(),
                        chest,
                        foot,
                        head,
                        landing,
                        front,
                        null,
                        CaveDropTrap.ApproachAxis.NORTH_SOUTH,
                        List.of(anchor.east()),
                        List.of(anchor.west()),
                        List.of(anchor.east(), anchor.west()),
                        List.of(entry),
                        List.of(exit),
                        front,
                        List.of(front, exit),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(footWrite, headWrite),
                        1L,
                        1L,
                        true,
                        List.of(anchor.east(), anchor.west()),
                        List.of(new BlockPos(2, 40, 0)),
                        List.of(new BlockPos(2, 40, 4)),
                        List.of(new BlockPos(4, 40, 4)));

        Method writeScene = CaveDropTrapFullChunkAudit.class
                .getDeclaredMethod("sceneJson", CaveDropTrapFeature.AuditEvent.class);
        Method readScene = CaveDropTrapFullChunkAudit.class
                .getDeclaredMethod("readScene", JsonObject.class);
        writeScene.setAccessible(true);
        readScene.setAccessible(true);
        JsonObject row = (JsonObject) writeScene.invoke(null, original);

        assertEquals("5,20,5", row.get("remainsFoot").getAsString());
        assertEquals("5,20,4", row.get("remainsHead").getAsString());
        assertTrue(row.has("firstNaturalContinuation"));
        assertTrue(row.has("secondNaturalContinuation"));
        assertTrue(row.has("upperNaturalReturn"));
        assertTrue(row.has("floodedExitPath"));
        assertFalse(row.has("remains"));
        assertFalse(row.has("remainsPosition"));

        CaveDropTrapFeature.AuditEvent roundTrip =
                (CaveDropTrapFeature.AuditEvent) readScene.invoke(null, row);
        assertEquals(foot, roundTrip.remainsFootPosition());
        assertEquals(head, roundTrip.remainsHeadPosition());
        assertEquals(original.firstNaturalContinuation(), roundTrip.firstNaturalContinuation());
        assertEquals(original.secondNaturalContinuation(), roundTrip.secondNaturalContinuation());
        assertEquals(original.upperNaturalReturn(), roundTrip.upperNaturalReturn());
        assertEquals(original.floodedExitPath(), roundTrip.floodedExitPath());
        assertEquals(
                Set.of(
                        CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_FOOT,
                        CaveDropTrapFeature.AuthoredRole.COLLAPSED_EXPLORER_HEAD),
                roundTrip.authoredPositions().stream()
                        .map(CaveDropTrapFeature.AuthoredPosition::role)
                        .collect(Collectors.toSet()));

        JsonObject missingHead = row.deepCopy();
        missingHead.remove("remainsHead");
        InvocationTargetException missingFailure = assertThrows(
                InvocationTargetException.class,
                () -> readScene.invoke(null, missingHead));
        assertInstanceOf(IOException.class, missingFailure.getCause());

        JsonObject missingNaturalRoute = row.deepCopy();
        missingNaturalRoute.remove("upperNaturalReturn");
        InvocationTargetException naturalFailure = assertThrows(
                InvocationTargetException.class,
                () -> readScene.invoke(null, missingNaturalRoute));
        assertInstanceOf(IOException.class, naturalFailure.getCause());

        JsonObject ambiguous = row.deepCopy();
        ambiguous.addProperty("remains", "5,20,5");
        InvocationTargetException ambiguousFailure = assertThrows(
                InvocationTargetException.class,
                () -> readScene.invoke(null, ambiguous));
        assertInstanceOf(IOException.class, ambiguousFailure.getCause());

        for (String required : List.of(
                "firstNaturalContinuation",
                "secondNaturalContinuation",
                "floodedExitPath",
                "entries",
                "authoredPositions",
                "postWriteVerified")) {
            JsonObject incomplete = row.deepCopy();
            incomplete.remove(required);
            InvocationTargetException incompleteFailure = assertThrows(
                    InvocationTargetException.class,
                    () -> readScene.invoke(null, incomplete),
                    "strict v4 JSON must reject missing " + required);
            assertInstanceOf(IOException.class, incompleteFailure.getCause());
        }
    }

    private static String rewardError(CaveDropTrapFeature.AuditEvent event)
            throws ReflectiveOperationException {
        Method method = CaveDropTrapFullChunkAudit.class.getDeclaredMethod(
                "rewardMappingError", CaveDropTrapFeature.AuditEvent.class);
        method.setAccessible(true);
        return (String) method.invoke(null, event);
    }

    private record RecordingRandom(
            net.minecraft.util.RandomSource source,
            List<Float> nextFloats) {
    }

    private static RecordingRandom recordingRandom(long seed) {
        net.minecraft.util.RandomSource delegate =
                net.minecraft.util.RandomSource.create(seed);
        List<Float> nextFloats = new ArrayList<>();
        net.minecraft.util.RandomSource recording =
                (net.minecraft.util.RandomSource) java.lang.reflect.Proxy.newProxyInstance(
                        net.minecraft.util.RandomSource.class.getClassLoader(),
                        new Class<?>[] {net.minecraft.util.RandomSource.class},
                        (proxy, method, arguments) -> {
                            Object result;
                            try {
                                result = method.invoke(delegate, arguments);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                            if (method.getName().equals("nextFloat")) {
                                nextFloats.add((Float) result);
                            }
                            return result;
                        });
        return new RecordingRandom(recording, nextFloats);
    }

    private static CaveDropTrapFeature.PassAFirstWitness selectorWitness(
            CaveDropTrapFeature.NaturalCaveAnchorCandidate candidate,
            String terminalBucket,
            CaveDropTrapFeature.PassAPredicateRole role) {
        return new CaveDropTrapFeature.PassAFirstWitness(
                terminalBucket,
                CaveDropTrapFeature.PassAProvenanceSource.NON_PREDICATE,
                role,
                candidate.ownerAnchor(),
                "none",
                "NONE",
                false,
                candidate.floorY(),
                Integer.MIN_VALUE,
                Integer.MIN_VALUE);
    }

    /**
     * Builds the conformal shear vector exactly as the trace derives it: the anchor segment is
     * pinned at zero, the two powder joints and the far bank drift relative to their neighbour,
     * the far mouth's first column is locked to the corridor lane, and only the very last return
     * column may drift (away from the stair shaft).
     */
    private static int[] conformalShear(
            int nearMouthEnd,
            int nearMouth,
            int firstPowderJoint,
            int secondPowderJoint,
            int farBank,
            int farMouthEnd) {
        int[] shear =
                new int[CaveDropTrapFeature.ConformalPassageThroatSource.SHEAR_SLOTS];
        shear[1] = nearMouth;
        shear[0] = nearMouth + nearMouthEnd;
        shear[3] = firstPowderJoint;
        shear[4] = shear[3] + secondPowderJoint;
        shear[5] = shear[4] + farBank;
        shear[6] = shear[4];
        shear[7] = shear[6] + farMouthEnd;
        return shear;
    }

    /** Runs the conformal source over one synthetic chunk with only {@code floorY} legal. */
    private static CaveDropTrapFeature.ConformalPassageScan conformalScanAt(
            int floorY,
            int baseX,
            int baseZ,
            Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind) {
        CaveDropTrapFeature.NaturalCaveFloorBounds reference =
                CaveDropTrapFeature.ConformalPassageThroatSource.floorBounds(
                        -64, 320, CaveDropTrap.RibbonOrientation.NORTH);
        return CaveDropTrapFeature.ConformalPassageThroatSource.scan(
                floorY + reference.minimumEnvelopeOffset(),
                floorY + reference.maximumEnvelopeOffset() + 1,
                baseX,
                baseZ,
                blockKind);
    }

    /** Adapts a synthetic block-kind lambda into the shared predicate's world view. */
    private static CaveDropTrap.RoofedThroatWorldView conformalWorldView(
            Function<BlockPos, CaveDropTrap.ThroatBlockKind> blockKind,
            int baseX,
            int baseZ,
            int anchorLx,
            int anchorLz) {
        return new CaveDropTrap.RoofedThroatWorldView() {
            @Override
            public CaveDropTrap.ThroatBlockKind blockKind(CaveDropTrap.Voxel voxel) {
                return blockKind.apply(new BlockPos(
                        baseX + anchorLx + voxel.x(),
                        voxel.y(),
                        baseZ + anchorLz + voxel.z()));
            }

            @Override
            public boolean insideOwnerInset(CaveDropTrap.Voxel voxel) {
                int localX = anchorLx + voxel.x();
                int localZ = anchorLz + voxel.z();
                return localX >= 1 && localX <= 14 && localZ >= 1 && localZ <= 14;
            }

            @Override
            public boolean authored(CaveDropTrap.Voxel voxel) {
                return false;
            }

            @Override
            public boolean seesSky(CaveDropTrap.Voxel voxel) {
                return false;
            }

            @Override
            public int minimumBuildY() {
                return -64;
            }
        };
    }

    /** One honest conformal chunk row whose passage trace found no candidate at all. */
    private static CaveDropTrapFeature.PassACensusSnapshot emptyConformalPassARow(
            int chunkX, int chunkZ) {
        return new CaveDropTrapFeature.PassACensusSnapshot(
                chunkX, chunkZ,
                1, 196, 0, 196,
                0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0, 0, 0,
                java.util.Collections.nCopies(16, 0L),
                0, 0, 0,
                0, 0,
                null,
                List.of());
    }

    private static CaveDropTrapFeature.PassACensusSnapshot deterministicDriverRow(
            int chunkX,
            int chunkZ,
            boolean fluid) {
        CaveDropTrap.PassARejection rejection = fluid
                ? CaveDropTrap.PassARejection.SEAFLOOR_OR_FLUID_STANDING
                : CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS;
        CaveDropTrapFeature.PassAFirstWitness witness =
                new CaveDropTrapFeature.PassAFirstWitness(
                        rejection.name(),
                        CaveDropTrapFeature.PassAPredicateRole
                                .MOUTH_OR_CONTINUATION_FLOOR,
                        new BlockPos(chunkX << 4, 40, chunkZ << 4),
                        fluid ? "minecraft:water" : "minecraft:dirt",
                        fluid ? "FLUID" : "OTHER",
                        false,
                        40,
                        64,
                        -24);
        return rejectedPassARow(chunkX, chunkZ, rejection, witness);
    }

    private static CaveDropTrapFeature.PassATracedEvaluation tracedEvaluation(
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            BlockPos ownerAnchor,
            Map<CaveDropTrap.Voxel, CaveDropTrap.ThroatBlockKind> overrides,
            Set<CaveDropTrap.Voxel> authored,
            boolean seesSky) {
        CaveDropTrap.RoofedThroatWorldView world =
                new CaveDropTrap.RoofedThroatWorldView() {
                    @Override
                    public CaveDropTrap.ThroatBlockKind blockKind(
                            CaveDropTrap.Voxel voxel) {
                        return tracedKind(plan, overrides, voxel);
                    }

                    @Override
                    public boolean insideOwnerInset(CaveDropTrap.Voxel voxel) {
                        return true;
                    }

                    @Override
                    public boolean authored(CaveDropTrap.Voxel voxel) {
                        return authored.contains(voxel);
                    }

                    @Override
                    public boolean seesSky(CaveDropTrap.Voxel voxel) {
                        return seesSky;
                    }

                    @Override
                    public int minimumBuildY() {
                        return -64;
                    }
                };
        return CaveDropTrapFeature.evaluatePassAWithTrace(
                plan,
                world,
                voxel -> {
                    CaveDropTrap.ThroatBlockKind kind =
                            tracedKind(plan, overrides, voxel);
                    String blockId = switch (kind) {
                        case AIR -> "minecraft:air";
                        case FLUID -> "minecraft:water";
                        case SAFE_NATURAL -> "minecraft:stone";
                        case GRAVEL -> "minecraft:gravel";
                        case ORE_OR_BLOCK_ENTITY -> "minecraft:iron_ore";
                        case PROTECTED -> "minecraft:bedrock";
                        case OTHER -> "minecraft:dirt";
                    };
                    return new CaveDropTrapFeature.PassAWorldSample(
                            new BlockPos(
                                    ownerAnchor.getX() + voxel.x(),
                                    voxel.y(),
                                    ownerAnchor.getZ() + voxel.z()),
                            blockId,
                            kind.name(),
                            authored.contains(voxel),
                            64);
                },
                ownerAnchor);
    }

    private static CaveDropTrap.ThroatBlockKind tracedKind(
            CaveDropTrap.RoofedGalleryThroatPlan plan,
            Map<CaveDropTrap.Voxel, CaveDropTrap.ThroatBlockKind> overrides,
            CaveDropTrap.Voxel voxel) {
        CaveDropTrap.ThroatBlockKind override = overrides.get(voxel);
        if (override != null) {
            return override;
        }
        return plan.headroomVoxels().contains(voxel)
                ? CaveDropTrap.ThroatBlockKind.AIR
                : CaveDropTrap.ThroatBlockKind.SAFE_NATURAL;
    }

    private static void assertWitness(
            CaveDropTrapFeature.PassAFirstWitness witness,
            CaveDropTrapFeature.PassAPredicateRole role,
            String blockId,
            String blockKind,
            boolean actualAuthored) {
        assertEquals(role, witness.predicateRole());
        assertEquals(
                CaveDropTrapFeature.PassAProvenanceSource.PREDICATE_QUERY,
                witness.source());
        assertEquals(blockId, witness.blockId());
        assertEquals(blockKind, witness.blockKind());
        assertEquals(actualAuthored, witness.actualAuthored());
        assertNotNull(witness.worldPosition());
        assertEquals(40, witness.floorY());
        assertEquals(64, witness.surfaceY());
        assertEquals(-24, witness.surfaceOffset());
    }

    private static CaveDropTrapFeature.PassACensusSnapshot rejectedPassARow(
            int chunkX,
            int chunkZ,
            CaveDropTrap.PassARejection rejection,
            CaveDropTrapFeature.PassAFirstWitness witness) {
        return rejectedPassARowWithProvenanceCount(
                chunkX, chunkZ, rejection, witness, 1);
    }

    private static CaveDropTrapFeature.PassACensusSnapshot
            rejectedPassARowWithProvenanceCount(
                    int chunkX,
                    int chunkZ,
                    CaveDropTrap.PassARejection rejection,
                    CaveDropTrapFeature.PassAFirstWitness witness,
                    long provenanceCount) {
        long sameDoor = rejection == CaveDropTrap.PassARejection.SAME_DOOR ? 1 : 0;
        long invalid = rejection
                        == CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS
                ? 1 : 0;
        long exposed = rejection
                        == CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF
                ? 1 : 0;
        long standing = rejection
                        == CaveDropTrap.PassARejection.SEAFLOOR_OR_FLUID_STANDING
                ? 1 : 0;
        long firm = rejection
                        == CaveDropTrap.PassARejection.FIRM_BYPASS_OR_WRONG_ROLES
                ? 1 : 0;
        long cavity = rejection == CaveDropTrap.PassARejection.CAVITY_AIR_OR_FLUID
                ? 1 : 0;
        long ore = rejection
                        == CaveDropTrap.PassARejection.ORE_BLOCK_ENTITY_OR_PROTECTED
                ? 1 : 0;
        long gravity = rejection
                        == CaveDropTrap.PassARejection.GRAVITY_OR_PARTIAL_STABILIZATION
                ? 1 : 0;
        long shell = rejection == CaveDropTrap.PassARejection.SHELL_OR_SUPPORT ? 1 : 0;
        long owner = rejection == CaveDropTrap.PassARejection.OWNER ? 1 : 0;
        long disconnected = rejection
                        == CaveDropTrap.PassARejection.DISCONNECTED_LOWER_OR_REWARD
                ? 1 : 0;
        CaveDropTrapFeature.PassACensusRepresentative representative =
                new CaveDropTrapFeature.PassACensusRepresentative(
                        rejection.name(),
                        witness.worldPosition(),
                        CaveDropTrap.RibbonOrientation.NORTH,
                        witness.floorY(),
                        rejection,
                        null,
                        -1,
                        null,
                        null,
                        false);
        CaveDropTrapFeature.PassAProvenanceCount provenance =
                new CaveDropTrapFeature.PassAProvenanceCount(
                        witness.provenanceKey(), provenanceCount, witness);
        return new CaveDropTrapFeature.PassACensusSnapshot(
                chunkX, chunkZ,
                1, 196, 1, 195,
                1, 1, 0,
                sameDoor, invalid, exposed, standing, firm, cavity, ore,
                gravity, shell, owner, disconnected,
                0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0, 0, 0,
                java.util.Collections.nCopies(16, 0L),
                0, 0, 0,
                0, 0,
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true),
                List.of(representative),
                List.of(provenance));
    }

    private static List<Long> rollCounts(int roll, long count) {
        Long[] counts = new Long[16];
        Arrays.fill(counts, 0L);
        counts[roll] = count;
        return List.of(counts);
    }

    private static CaveDropTrapFeature.PassACensusSnapshot copyPassARowWithLong(
            CaveDropTrapFeature.PassACensusSnapshot original,
            String componentName,
            long replacement)
            throws ReflectiveOperationException {
        RecordComponent[] components =
                CaveDropTrapFeature.PassACensusSnapshot.class.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        boolean replaced = false;
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            parameterTypes[index] = component.getType();
            arguments[index] = component.getAccessor().invoke(original);
            if (component.getName().equals(componentName)) {
                assertEquals(long.class, component.getType());
                arguments[index] = replacement;
                replaced = true;
            }
        }
        assertTrue(replaced, "unknown Pass-A counter " + componentName);
        Constructor<CaveDropTrapFeature.PassACensusSnapshot> constructor =
                CaveDropTrapFeature.PassACensusSnapshot.class
                        .getDeclaredConstructor(parameterTypes);
        return constructor.newInstance(arguments);
    }

    private static CaveDropTrapFeature.PassACensusSnapshot passingPassARow(
            CaveDropTrap.RoofedThroatMeasurements measurements) {
        return new CaveDropTrapFeature.PassACensusSnapshot(
                3, 4,
                1, 196, 1, 195,
                1, 1, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                1,
                1, 0,
                1, 0,
                1, 0,
                1, 0,
                1, 0, 0, 0,
                rollCounts(0, 1),
                1, 0, 0,
                0, 0,
                measurements,
                List.of());
    }

    private static CaveDropTrapFeature.PassACensusSnapshot selectorLawRow(
            List<Long> rewardRollCounts,
            long resolvedOrdinary,
            long resolvedFlooded,
            long resolvedProspector,
            long resolvedFallback,
            long requestedOrdinary,
            long requestedFlooded,
            long requestedProspector,
            long floodedRequestFallback,
            long prospectorRequestFallback) {
        return selectorFeasibilityLawRow(
                rewardRollCounts,
                1, 0,
                1, 0,
                resolvedOrdinary,
                resolvedFlooded,
                resolvedProspector,
                resolvedFallback,
                requestedOrdinary,
                requestedFlooded,
                requestedProspector,
                floodedRequestFallback,
                prospectorRequestFallback);
    }

    private static CaveDropTrapFeature.PassACensusSnapshot selectorFeasibilityLawRow(
            List<Long> rewardRollCounts,
            long floodedFeasible,
            long floodedFallback,
            long prospectorFeasible,
            long prospectorFallback,
            long resolvedOrdinary,
            long resolvedFlooded,
            long resolvedProspector,
            long resolvedFallback,
            long requestedOrdinary,
            long requestedFlooded,
            long requestedProspector,
            long floodedRequestFallback,
            long prospectorRequestFallback) {
        return new CaveDropTrapFeature.PassACensusSnapshot(
                3, 4,
                1, 196, 1, 195,
                1, 1, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                1,
                floodedFeasible, floodedFallback,
                prospectorFeasible, prospectorFallback,
                1, 0,
                1, 0,
                resolvedOrdinary, resolvedFlooded, resolvedProspector, resolvedFallback,
                rewardRollCounts,
                requestedOrdinary, requestedFlooded, requestedProspector,
                floodedRequestFallback, prospectorRequestFallback,
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true),
                List.of());
    }

    private static CaveDropTrapFeature.PassACensusSnapshot allOrdinaryPassARow(
            long count,
            long legalFloorCount,
            long auditDomainSlots,
            long caveAnchorRejected) {
        return new CaveDropTrapFeature.PassACensusSnapshot(
                3, 4,
                legalFloorCount, auditDomainSlots, count, caveAnchorRejected,
                count, count, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                count,
                count, 0,
                count, 0,
                count, 0,
                count, 0,
                count, 0, 0, 0,
                rollCounts(0, count),
                count, 0, 0,
                0, 0,
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true),
                List.of());
    }

    private static CaveDropTrapFeature.PassACensusSnapshot allCountersPassARow(long count) {
        return new CaveDropTrapFeature.PassACensusSnapshot(
                3, 4,
                count, count, count, count,
                count, count, count,
                count, count, count, count, count, count,
                count, count, count, count, count,
                count,
                count, count,
                count, count,
                count, count,
                count, count,
                count, count, count, count,
                java.util.Collections.nCopies(16, count),
                count, count, count,
                count, count,
                new CaveDropTrap.RoofedThroatMeasurements(
                        12, 6, 6, List.of(3, 3),
                        2, true, true, true, true, true, true),
                List.of());
    }

    private static CaveDropTrapFeature.AuditEvent eventForReward(
            int roll, CaveDropTrap.RewardKind kind, String fallbackReason) {
        BlockPos anchor = new BlockPos(1, 40, 1);
        return new CaveDropTrapFeature.AuditEvent(
                anchor,
                roll,
                kind,
                List.of(),
                List.of(),
                30,
                30,
                10,
                10,
                List.of(),
                List.of(),
                null,
                null,
                null,
                anchor,
                anchor,
                fallbackReason,
                CaveDropTrap.ApproachAxis.NORTH_SOUTH,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0L,
                0L,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
