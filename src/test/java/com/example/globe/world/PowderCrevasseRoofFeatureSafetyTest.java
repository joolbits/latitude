package com.example.globe.world;

import com.example.globe.core.SubterraneanTrapPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the shell-neighbour law and the compact first-failure fields used by the worldgen debug row. */
class PowderCrevasseRoofFeatureSafetyTest {

    @Test
    void featureStageSurfaceAuthorityUsesMaintainedFinalHeightmap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        assertEquals(Heightmap.Types.WORLD_SURFACE,
                PowderCrevasseRoofFeature.FEATURE_STAGE_SURFACE_HEIGHTMAP,
                "TOP_LAYER_MODIFICATION must read the final heightmap maintained through FEATURES");
        assertFalse(PowderCrevasseRoofFeature.FEATURE_STAGE_SURFACE_HEIGHTMAP
                        == Heightmap.Types.WORLD_SURFACE_WG,
                "WORLD_SURFACE_WG freezes before freeze_top_layer writes its visible snow layer");
    }

    @Test
    void existingDryAirAndOrdinaryDrySolidNeighboursAreSafe() {
        assertEquals(PowderTrapWorldSafetyLaw.ShellRejection.NONE,
                PowderTrapWorldSafetyLaw.shellRejection(true, false, false, false),
                "Natural cave air beside a planned clear cell must not veto the complete trap");
        assertEquals(PowderTrapWorldSafetyLaw.ShellRejection.NONE,
                PowderTrapWorldSafetyLaw.shellRejection(false, true, false, false),
                "An ordinary dry solid shell remains safe");
        assertEquals(PowderTrapWorldSafetyLaw.ShellRejection.NONE,
                PowderTrapWorldSafetyLaw.shellRejection(false, false, false, false),
                "Dry non-motion-blocking terrain is not a fluid or block-entity hazard");
    }

    @Test
    void anyFluidOrBlockEntityNeighbourStillRejects() {
        assertEquals(PowderTrapWorldSafetyLaw.ShellRejection.FLUID,
                PowderTrapWorldSafetyLaw.shellRejection(true, false, true, false));
        assertEquals(PowderTrapWorldSafetyLaw.ShellRejection.BLOCK_ENTITY,
                PowderTrapWorldSafetyLaw.shellRejection(false, true, false, true));
        assertEquals(PowderTrapWorldSafetyLaw.ShellRejection.FLUID,
                PowderTrapWorldSafetyLaw.shellRejection(false, true, true, true),
                "Fluid wins the stable first-failure order when both hazards are present");
    }

    @Test
    void thinSnowSupportRequiresEveryCertificationAndExactSnapshotIdentity() {
        assertTrue(PowderTrapWorldSafetyLaw.certifiedThinSupport(true, true, false, false, true));
        assertFalse(PowderTrapWorldSafetyLaw.certifiedThinSupport(false, true, false, false, true),
                "wet support is never certified");
        assertFalse(PowderTrapWorldSafetyLaw.certifiedThinSupport(true, false, false, false, true),
                "non-motion-blocking support is not a stable walking surface");
        assertFalse(PowderTrapWorldSafetyLaw.certifiedThinSupport(true, true, true, false, true),
                "block-entity support is never replaced");
        assertFalse(PowderTrapWorldSafetyLaw.certifiedThinSupport(true, true, false, false, false),
                "stable but non-replaceable material remains protected");

        String captured = new String("minecraft:snow_block[variant=captured]");
        assertTrue(PowderTrapWorldSafetyLaw.exactSnapshotMatches(captured,
                "minecraft:snow_block[variant=captured]"));
        assertFalse(PowderTrapWorldSafetyLaw.exactSnapshotMatches(captured,
                "minecraft:snow_block[variant=changed]"), "classification equality cannot hide a state change");
        assertFalse(PowderTrapWorldSafetyLaw.exactSnapshotMatches(null, captured));
    }

    @Test
    void thinSnowSupportRejectsGravityEvenWhenOtherwiseCertifiable() {
        Method certification = requiredLawMethod("certifiedThinSupport",
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);

        assertTrue((boolean) invokeLaw(certification, true, true, false, false, true),
                "ordinary dry motion-blocking replaceable support remains certifiable");
        assertFalse((boolean) invokeLaw(certification, true, true, false, true, true),
                "gravity-affected support must reject even when the carver-replaceable tag admits it");
    }

    @Test
    void failureDiagnosticHasStableReasonPositionAndStateFields() {
        PowderTrapWorldSafetyResult failure = PowderTrapWorldSafetyResult.failure(
                PowderTrapWorldSafetyFailure.SHELL_FLUID,
                new BlockPos(-2813, 80, 9363),
                "minecraft:water[level=0]");

        assertFalse(failure.isSafe());
        assertEquals(PowderTrapWorldSafetyFailure.SHELL_FLUID, failure.reason());
        assertEquals("-2813,80,9363", failure.position());
        assertEquals("minecraft:water[level=0]", failure.state());

        PowderTrapWorldSafetyResult passed = PowderTrapWorldSafetyResult.passed();
        assertTrue(passed.isSafe());
        assertEquals(PowderTrapWorldSafetyFailure.NONE, passed.reason());
        assertEquals("none", passed.position());
        assertEquals("none", passed.state());
        assertThrows(IllegalArgumentException.class, () -> PowderTrapWorldSafetyResult.failure(
                PowderTrapWorldSafetyFailure.NONE, BlockPos.ZERO, "minecraft:air"));
    }

    @Test
    void cushionBaseResolutionPreservesDrySolidAuthorsBlueIceIntoDryAirAndRejectsEveryHazard() {
        Method action = requiredLawMethod("cushionBaseAction",
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);

        assertEquals("PRESERVE_EXISTING", enumName(invokeLaw(action,
                        false, true, false, false, false, true)),
                "an existing dry solid base is an exact no-op, not gratuitous terrain replacement");
        assertEquals("AUTHOR_BLUE_ICE", enumName(invokeLaw(action,
                        true, false, false, false, false, true)),
                "dry cave air resolves to an explicit non-gravity final solid below the cushion");
        assertEquals("REJECT", enumName(invokeLaw(action,
                        true, false, true, false, false, true)),
                "a fluid base target rejects the complete plan");
        assertEquals("REJECT", enumName(invokeLaw(action,
                        true, false, false, true, false, true)),
                "a block-entity base target rejects the complete plan");
        assertEquals("REJECT", enumName(invokeLaw(action,
                        true, false, false, false, true, true)),
                "a gravity-affected base target rejects the complete plan");
        assertEquals("REJECT", enumName(invokeLaw(action,
                        true, false, false, false, false, false)),
                "an unwritable base target rejects before any cushion or surface write");
        assertEquals("REJECT", enumName(invokeLaw(action,
                        false, false, false, false, false, true)),
                "an unsafe non-air, non-solid target is never silently replaced");
    }

    @Test
    void twoUntouchedDryNaturalBaseAnchorsSeparatedByThreeBlocksPass() {
        assertTrue(naturalAnchorsPass(new Object[][]{
                {0, 0, true, true},
                {3, 0, true, true}
        }), "two untouched natural contacts separated by three X/Z blocks anchor the exact cushion footprint");
    }

    @Test
    void missingCloseAuthoredPlannedOrUnsafeBaseContactsNeverAnchorAFloatingRaft() {
        assertFalse(naturalAnchorsPass(new Object[][]{}), "zero natural contacts reject");
        assertFalse(naturalAnchorsPass(new Object[][]{
                {0, 0, true, true}
        }), "one natural contact rejects");
        assertFalse(naturalAnchorsPass(new Object[][]{
                {0, 0, true, true},
                {2, 0, true, true}
        }), "two contacts closer than three X/Z blocks reject");
        assertFalse(naturalAnchorsPass(new Object[][]{
                {0, 0, false, true},
                {4, 0, false, true}
        }), "authored blue ice is not untouched natural support");
        assertFalse(naturalAnchorsPass(new Object[][]{
                {0, 0, false, true},
                {4, 0, true, true}
        }), "a planned replacement or clear cannot become the second anchor");
        assertFalse(naturalAnchorsPass(new Object[][]{
                {0, 0, true, false},
                {4, 0, true, true}
        }), "wet, gravity-affected, block-entity, or otherwise unstable support cannot anchor");
    }

    @Test
    void escapeFloorRequiresDryStableHardSubstrateWhileTargetShellAndFluidHazardsStillReject() {
        Method finalState = requiredLawMethod("escapeFloorFinalStateSafe",
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);

        assertFalse((boolean) invokeLaw(finalState,
                        true, false, false, false, false, true),
                "RED: air or unstable material below ESCAPE_FLOOR cannot support a safe authored stair");
        for (String stableSubstrate : List.of("packed ice", "blue ice", "snow block", "stone")) {
            assertTrue((boolean) invokeLaw(finalState,
                            true, true, false, false, false, true),
                    stableSubstrate + " is a dry stable hard substrate below the final snow-block floor");
        }
        assertFalse((boolean) invokeLaw(finalState,
                        false, true, false, false, false, true),
                "hard substrate must not make a protected target replaceable");
        assertFalse((boolean) invokeLaw(finalState,
                        true, true, true, false, false, true),
                "fluid in the target still rejects");
        assertFalse((boolean) invokeLaw(finalState,
                        true, true, false, true, false, true),
                "a block entity in the target still rejects");
        assertFalse((boolean) invokeLaw(finalState,
                        true, true, false, false, true, true),
                "a gravity target still rejects");
        assertFalse((boolean) invokeLaw(finalState,
                        true, true, false, false, false, false),
                "an unwritable target still rejects");

        assertEquals(PowderTrapWorldSafetyLaw.ShellRejection.FLUID,
                PowderTrapWorldSafetyLaw.shellRejection(false, true, true, false, false),
                "the independent strict shell hazard law remains active");
        assertTrue(boundedFluidHit(1, -1),
                "the independent reachable-fluid veto remains active beside authored route volume");
    }

    @Test
    void onlyExactCapturedThinOverFullSnowSurfaceCapIsSafeForTheEscapeShell() {
        Method cap = requiredLawMethod("certifiedSurfaceCapShellSafe",
                int.class, int.class, int.class,
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class,
                boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);

        Object[] valid = {
                SubterraneanTrapPlan.THIN_OVER_FULL_SNOW, 88, 88,
                true, true, true, true, true,
                true, false, false, false, false
        };
        assertTrue((boolean) invokeLaw(cap, valid),
                "the exact captured one-layer cap over an untouched snow block is intact surface, not an opening");
        assertSurfaceCapMutantRejects(cap, valid, 0, SubterraneanTrapPlan.THIN_SNOW,
                "wrong surface kind");
        assertSurfaceCapMutantRejects(cap, valid, 1, 89, "wrong captured Y");
        assertSurfaceCapMutantRejects(cap, valid, 3, false, "changed layer snapshot");
        assertSurfaceCapMutantRejects(cap, valid, 4, false, "not exactly one snow layer");
        assertSurfaceCapMutantRejects(cap, valid, 5, false, "changed support snapshot");
        assertSurfaceCapMutantRejects(cap, valid, 6, false, "support is not a snow block");
        assertSurfaceCapMutantRejects(cap, valid, 7, false, "air above is obstructed");
        assertSurfaceCapMutantRejects(cap, valid, 8, false, "wrong biome");
        assertSurfaceCapMutantRejects(cap, valid, 9, true, "support overlaps another planned write");
        assertSurfaceCapMutantRejects(cap, valid, 10, true, "fluid contamination");
        assertSurfaceCapMutantRejects(cap, valid, 11, true, "block entity");
        assertSurfaceCapMutantRejects(cap, valid, 12, true, "gravity-affected support");
    }

    @Test
    void worldSafetyAttemptTelemetryKeepsAllEarlierReasonCountsWhenALaterCandidateSucceeds() {
        Class<?> telemetryType;
        try {
            telemetryType = Class.forName("com.example.globe.world.PowderTrapWorldSafetyTelemetry");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(
                    "RED: world safety needs deterministic per-reason attempt telemetry and acceptedAttempt",
                    exception);
        }
        try {
            var constructor = telemetryType.getDeclaredConstructor();
            Method record = telemetryType.getDeclaredMethod("record", PowderTrapWorldSafetyResult.class);
            Method reasonCounts = telemetryType.getDeclaredMethod("reasonCounts");
            Method acceptedAttempt = telemetryType.getDeclaredMethod("acceptedAttempt");
            Method attempts = telemetryType.getDeclaredMethod("attempts");
            constructor.setAccessible(true);
            record.setAccessible(true);
            reasonCounts.setAccessible(true);
            acceptedAttempt.setAccessible(true);
            attempts.setAccessible(true);

            Object telemetry = constructor.newInstance();
            record.invoke(telemetry, PowderTrapWorldSafetyResult.failure(
                    PowderTrapWorldSafetyFailure.ESCAPE_SHELL_UNSAFE, new BlockPos(-2781, 88, 9402),
                    "minecraft:snow[layers=1]"));
            record.invoke(telemetry, PowderTrapWorldSafetyResult.failure(
                    PowderTrapWorldSafetyFailure.CUSHION_SUPPORT_UNSAFE, new BlockPos(-2857, 60, 9270),
                    "minecraft:air"));
            record.invoke(telemetry, PowderTrapWorldSafetyResult.passed());

            assertEquals(3, attempts.invoke(telemetry), "every candidate preflight is counted");
            assertEquals(3, acceptedAttempt.invoke(telemetry),
                    "the successful later candidate reports its one-based attempt number");
            Object rawCounts = reasonCounts.invoke(telemetry);
            assertTrue(rawCounts instanceof java.util.Map<?, ?>, "reasonCounts is scanner-readable structured data");
            java.util.Map<?, ?> counts = (java.util.Map<?, ?>) rawCounts;
            assertEquals(1, counts.get(PowderTrapWorldSafetyFailure.CUSHION_SUPPORT_UNSAFE));
            assertEquals(1, counts.get(PowderTrapWorldSafetyFailure.ESCAPE_SHELL_UNSAFE));
            assertFalse(counts.containsKey(PowderTrapWorldSafetyFailure.NONE),
                    "a success is reported by acceptedAttempt, not disguised as a failure counter");
            List<?> nonzeroReasons = counts.entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof Number number && number.intValue() > 0)
                    .map(java.util.Map.Entry::getKey).toList();
            assertEquals(List.of(PowderTrapWorldSafetyFailure.CUSHION_SUPPORT_UNSAFE,
                            PowderTrapWorldSafetyFailure.ESCAPE_SHELL_UNSAFE),
                    nonzeroReasons,
                    "counter iteration is deterministic enum order, not candidate encounter order");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "world-safety attempt telemetry must expose record/result counters and acceptedAttempt",
                    exception);
        }
    }

    @Test
    void reachableFluidWithinSevenDryAirStepsRejectsButDistantOrBlockedFluidDoesNot() {
        Method reachability = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("fluidSourceWithinSevenPassableAirStepsRejects")
                        && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{boolean[].class}))
                .findFirst().orElse(null);
        assertTrue(reachability != null, "pure safety law must expose fluidSourceWithinSevenPassableAirStepsRejects");
        try {
            reachability.setAccessible(true);
            assertTrue((boolean) reachability.invoke(null, (Object) new boolean[]{true, true, true, true, true, true, true}),
                    "a fluid source connected through seven dry-air steps reaches fall or route volume and rejects");
            assertFalse((boolean) reachability.invoke(null, (Object) new boolean[]{true, true, true, true, true, true, true, true}),
                    "a source beyond seven passable steps does not reject");
            assertFalse((boolean) reachability.invoke(null, (Object) new boolean[]{true, true, false, true, true}),
                    "a solid interruption prevents a fluid source from reaching planned volume");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("fluid reachability law must be invocable", exception);
        }
    }

    @Test
    void plannedCushionSeedsTheSameBoundedFluidReachabilityVetoAsAuthoredAir() {
        Method seedLaw = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("plannedWriteSeedsFluidReachability")
                        && java.util.Arrays.equals(method.getParameterTypes(),
                        new Class<?>[]{SubterraneanTrapPlan.Phase.class, boolean.class}))
                .findFirst().orElse(null);
        Method reachability = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("fluidSourceWithinSevenPassableAirStepsRejects")
                        && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{boolean[].class}))
                .findFirst().orElseThrow();
        assertTrue(seedLaw != null, "the real bounded search needs one explicit planned-phase seed law");
        try {
            seedLaw.setAccessible(true);
            reachability.setAccessible(true);
            assertTrue((boolean) seedLaw.invoke(null, SubterraneanTrapPlan.Phase.CUSHION, false)
                            && (boolean) reachability.invoke(null,
                            (Object) new boolean[]{true, true, true, true, true, true, true}),
                    "a source within seven passable steps of the landing cushion must reject");
            assertTrue((boolean) seedLaw.invoke(null, SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL, false),
                    "future-mined tail snow becomes passable and must seed the same bounded scan");
            assertFalse((boolean) seedLaw.invoke(null, SubterraneanTrapPlan.Phase.ESCAPE_FLOOR, false),
                    "remote authored solids do not seed an irrelevant fluid scan");
            assertFalse((boolean) reachability.invoke(null,
                            (Object) new boolean[]{true, true, true, true, true, true, true, true}),
                    "the cushion uses the same distance-eight acceptance boundary");
            assertFalse((boolean) reachability.invoke(null,
                            (Object) new boolean[]{true, true, false, true}),
                    "a solid interruption still blocks the cushion path");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("planned cushion fluid-seed law must be invocable", exception);
        }
    }

    @Test
    void futureMinedTailHeadspaceSeedsButAuthoredTailFloorBlocksFluidBehindIt() {
        Method seedLaw = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("plannedWriteSeedsFluidReachability")
                        && java.util.Arrays.equals(method.getParameterTypes(),
                        new Class<?>[]{SubterraneanTrapPlan.Phase.class, boolean.class}))
                .findFirst().orElse(null);
        assertTrue(seedLaw != null, "tail fluid reachability must distinguish floor from headspace metadata");
        try {
            seedLaw.setAccessible(true);
            assertFalse((boolean) seedLaw.invoke(
                            null, SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL, true),
                    "the retained snow stair floor is a blocker, never a fluid-search seed");
            assertTrue((boolean) seedLaw.invoke(
                            null, SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL, false),
                    "the two future-mined headspace blocks are fluid-search seeds");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("tail floor/headspace seed law must be invocable", exception);
        }

        assertFalse(tailRoleFluidHit(false),
                "fluid reachable only beside/below the retained tail floor stays blocked");
        assertTrue(tailRoleFluidHit(true),
                "fluid directly beside future-mined tail headspace still vetoes");
    }

    @Test
    void authoredFinalSolidFloorOverridesCurrentAirWhileMinedHeadspaceRemainsTraversable() {
        Method classifier = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("fluidTraversalCell")
                        && java.util.Arrays.equals(method.getParameterTypes(),
                        new Class<?>[]{boolean.class, boolean.class, boolean.class, boolean.class}))
                .findFirst().orElse(null);
        assertTrue(classifier != null,
                "runtime traversal must classify planned final solids before falling back to current cave air");
        try {
            classifier.setAccessible(true);
            Object finalSolidCurrentAir = classifier.invoke(null, true, false, false, true);
            Object futureMinedHeadspace = classifier.invoke(null, false, true, false, false);
            Object fluid = classifier.invoke(null, false, false, true, true);
            assertEquals("BLOCKED", ((Enum<?>) finalSolidCurrentAir).name(),
                    "ESCAPE_FLOOR and retained tail bases stay blockers even when their current target is air");
            assertEquals("PASSABLE", ((Enum<?>) futureMinedHeadspace).name(),
                    "future-mined headspace is traversable even while currently solid");
            assertFalse(fluidHitAcrossMiddleCell(finalSolidCurrentAir, fluid),
                    "current-air authored floor between the seed and fluid must prevent a false veto");
            assertTrue(fluidHitAcrossMiddleCell(futureMinedHeadspace, fluid),
                    "the corresponding future-mined headspace still exposes reachable fluid");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("planned/current fluid traversal classifier must be invocable", exception);
        }
    }

    @Test
    void immediateGravityShellRejectsWithoutTurningOrdinaryDryShellIntoARemoteVeto() {
        Method gravityAware = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("shellRejection")
                        && java.util.Arrays.equals(method.getParameterTypes(),
                        new Class<?>[]{boolean.class, boolean.class, boolean.class,
                                boolean.class, boolean.class}))
                .findFirst().orElse(null);
        assertTrue(gravityAware != null, "shell law must classify immediate falling-block collapse hazards");
        try {
            gravityAware.setAccessible(true);
            Object gravity = gravityAware.invoke(null, false, true, false, false, true);
            assertEquals("GRAVITY", ((Enum<?>) gravity).name(),
                    "sand or gravel directly beside authored passable volume can collapse into it");
            Object ordinaryDry = gravityAware.invoke(null, false, true, false, false, false);
            assertEquals("NONE", ((Enum<?>) ordinaryDry).name(),
                    "ordinary dry shell stays safe; only inspected immediate neighbours are classified");
            Object wetGravity = gravityAware.invoke(null, false, true, true, false, true);
            assertEquals("FLUID", ((Enum<?>) wetGravity).name(),
                    "fluid preserves first-failure precedence when multiple hazards coexist");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("gravity-aware shell law must be invocable", exception);
        }
    }

    @Test
    void realBoundedTraversalPinsTailAndCushionDistanceSevenEightAndSolidInterruption() {
        assertTrue(boundedFluidHit(1, -1),
                "water or lava directly beside a future-mined tail/cushion volume rejects");
        assertTrue(boundedFluidHit(7, -1), "the real bounded traversal includes distance seven");
        assertFalse(boundedFluidHit(8, -1), "the real bounded traversal excludes distance eight");
        assertFalse(boundedFluidHit(7, 3), "a solid interruption stops the real traversal");
    }

    @Test
    void pureSelectorRecoversCounterclockwiseAfterClockwiseShellFailureAndPlaceUsesItBeforeApply() {
        Method selector = requiredSelectorMethod();
        List<String> events = new java.util.ArrayList<>();
        RouteCandidate clockwise = new RouteCandidate("clockwise-shell-failure", 32, 1);
        RouteCandidate counterclockwise = new RouteCandidate("counterclockwise-safe", 32, 2);
        RouteCandidate later = new RouteCandidate("later-route", 32, 3);

        Object selection = invokeSelector(
                selector,
                2,
                (java.util.function.BiPredicate<Integer, Integer>) (placement, depth) -> {
                    events.add("anchor:" + placement + ":" + depth);
                    return true;
                },
                (java.util.function.BiFunction<Integer, Integer, List<RouteCandidate>>) (placement, depth) -> {
                    events.add("routes:" + placement + ":" + depth);
                    return placement == 0 && depth == 32
                            ? List.of(clockwise, counterclockwise, later)
                            : List.of();
                },
                (java.util.function.Predicate<RouteCandidate>) candidate -> {
                    events.add("world:" + candidate.label());
                    return candidate.equals(counterclockwise);
                });

        assertTrue(selection != null, "the first complete world-safe alternative is selected");
        assertEquals(counterclockwise, selectionComponent(selection, "candidate"));
        assertEquals(1, selectionInt(selection, "placementOrdinal"), "placement telemetry is one-based");
        assertEquals(32, selectionInt(selection, "depth"), "selection telemetry identifies the actual depth");
        assertEquals(2, selectionInt(selection, "routeOrdinal"), "route telemetry is one-based");
        assertEquals(List.of(
                        "anchor:0:32",
                        "routes:0:32",
                        "world:clockwise-shell-failure",
                        "world:counterclockwise-safe"),
                events,
                "RED: placement -> depth -> anchor -> alternatives -> complete safety stops at first safe");

        assertTrue(assertPlaceInvokesSelectorBeforeItsSingleApply(),
                "the tested pure selector must be wired into real feature placement, never left as a dead helper");
    }

    @Test
    void selectorReachesDepth28OnlyAfterNearerDepthsFailAndBoundsEveryLandingTo34Routes() {
        Method selector = requiredSelectorMethod();
        List<Integer> preferred = List.of(32, 33, 31, 34, 30, 35, 29, 36, 28);
        List<Integer> inspectedDepths = new java.util.ArrayList<>();
        Object fallback = invokeSelector(
                selector,
                1,
                (java.util.function.BiPredicate<Integer, Integer>) (placement, depth) -> true,
                (java.util.function.BiFunction<Integer, Integer, List<RouteCandidate>>) (placement, depth) ->
                        List.of(new RouteCandidate("depth-" + depth, depth, 1)),
                (java.util.function.Predicate<RouteCandidate>) candidate -> {
                    inspectedDepths.add(candidate.depth());
                    return candidate.depth() == 28;
                });
        assertEquals(preferred, inspectedDepths,
                "depth 28 is inspected only after every nearer preferred landing fails complete safety");
        assertTrue(fallback != null);
        assertEquals(28, selectionInt(fallback, "depth"));
        assertEquals(1, selectionInt(fallback, "placementOrdinal"));
        assertEquals(1, selectionInt(fallback, "routeOrdinal"));
        assertTrue(inspectedDepths.stream().allMatch(depth -> depth >= 28 && depth <= 36),
                "no landing outside the exact 28..36 recovery band is attempted");

        List<RouteCandidate> oversized = java.util.stream.IntStream.rangeClosed(1, 40)
                .mapToObj(route -> new RouteCandidate("route-" + route, 0, route)).toList();
        List<Integer> boundedDepths = new java.util.ArrayList<>();
        Object none = invokeSelector(
                selector,
                2,
                (java.util.function.BiPredicate<Integer, Integer>) (placement, depth) -> true,
                (java.util.function.BiFunction<Integer, Integer, List<RouteCandidate>>) (placement, depth) ->
                        oversized.stream()
                                .map(candidate -> new RouteCandidate(
                                        "p" + placement + "-d" + depth + "-" + candidate.label(),
                                        depth, candidate.routeOrdinal()))
                                .toList(),
                (java.util.function.Predicate<RouteCandidate>) candidate -> {
                    boundedDepths.add(candidate.depth());
                    return false;
                });
        assertNull(none, "no safe candidate produces no selection and therefore no mutation input");
        assertEquals(2 * preferred.size() * 34, boundedDepths.size(),
                "two placements times nine depths times at most 34 alternatives is the hard attempt bound");
        assertTrue(boundedDepths.stream().allMatch(depth -> depth >= 28 && depth <= 36));
        for (int placement = 0; placement < 2; placement++) {
            for (int depthOrdinal = 0; depthOrdinal < preferred.size(); depthOrdinal++) {
                int from = (placement * preferred.size() + depthOrdinal) * 34;
                assertEquals(java.util.Collections.nCopies(34, preferred.get(depthOrdinal)),
                        boundedDepths.subList(from, from + 34),
                        "the placement/depth nesting stays deterministic under the route cap");
            }
        }
    }

    @Test
    void selectorRejectsUnanchoredLandingsBeforeRoutesAndOpenShellsBeforeSelection() {
        Method selector = requiredSelectorMethod();
        java.util.concurrent.atomic.AtomicInteger routeFactories = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger safetyChecks = new java.util.concurrent.atomic.AtomicInteger();
        Object unanchored = invokeSelector(
                selector,
                1,
                (java.util.function.BiPredicate<Integer, Integer>) (placement, depth) -> false,
                (java.util.function.BiFunction<Integer, Integer, List<RouteCandidate>>) (placement, depth) -> {
                    routeFactories.incrementAndGet();
                    return List.of(new RouteCandidate("must-not-be-planned", depth, 1));
                },
                (java.util.function.Predicate<RouteCandidate>) candidate -> {
                    safetyChecks.incrementAndGet();
                    return true;
                });
        assertNull(unanchored, "an unanchored landing never becomes a selection");
        assertEquals(0, routeFactories.get(), "anchor preflight precedes route alternative construction");
        assertEquals(0, safetyChecks.get(), "unanchored geometry never reaches complete world safety");

        Object openShell = invokeSelector(
                selector,
                1,
                (java.util.function.BiPredicate<Integer, Integer>) (placement, depth) -> true,
                (java.util.function.BiFunction<Integer, Integer, List<RouteCandidate>>) (placement, depth) ->
                        List.of(new RouteCandidate("open-shell", depth, 1)),
                (java.util.function.Predicate<RouteCandidate>) candidate -> false);
        assertNull(openShell,
                "an alternative with an open or otherwise unsafe complete shell never becomes mutation input");
    }

    private static Method requiredSelectorMethod() {
        Method selector = java.util.Arrays.stream(PowderCrevasseRoofFeature.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("selectFirstSafePlan")
                        && java.util.Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{
                        int.class,
                        java.util.function.BiPredicate.class,
                        java.util.function.BiFunction.class,
                        java.util.function.Predicate.class
                }))
                .findFirst().orElse(null);
        if (selector == null) {
            throw new AssertionError(
                    "RED: feature needs one pure production-wired bounded selectFirstSafePlan seam");
        }
        selector.setAccessible(true);
        return selector;
    }

    private static Object invokeSelector(
            Method selector, int placementCount,
            java.util.function.BiPredicate<Integer, Integer> anchorSafe,
            java.util.function.BiFunction<Integer, Integer, ? extends List<?>> alternatives,
            java.util.function.Predicate<?> worldSafe) {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            return selector.invoke(null, placementCount, anchorSafe, alternatives, worldSafe);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("pure first-safe selector must be invocable", exception);
        }
    }

    private static Object selectionComponent(Object selection, String name) {
        Method accessor = java.util.Arrays.stream(selection.getClass().getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == 0)
                .findFirst().orElse(null);
        assertTrue(accessor != null, "selection telemetry exposes " + name);
        try {
            return accessor.invoke(selection);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("selection accessor " + name + " must be invocable", exception);
        }
    }

    private static int selectionInt(Object selection, String name) {
        Object value = selectionComponent(selection, name);
        assertTrue(value instanceof Integer, name + " is exact integer selection metadata");
        return (Integer) value;
    }

    private static boolean assertPlaceInvokesSelectorBeforeItsSingleApply() {
        byte[] bytes;
        try (var input = PowderCrevasseRoofFeature.class.getResourceAsStream(
                "PowderCrevasseRoofFeature.class")) {
            assertTrue(input != null, "compiled feature bytecode is available for wiring proof");
            bytes = input.readAllBytes();
        } catch (java.io.IOException exception) {
            throw new AssertionError("compiled feature bytecode must be readable", exception);
        }
        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        var place = classModel.methods().stream()
                .filter(method -> method.methodName().equalsString("place") && method.code().isPresent())
                .findFirst().orElseThrow(() -> new AssertionError("feature place method has compiled code"));
        String owner = PowderCrevasseRoofFeature.class.getName().replace('.', '/');
        List<String> localCalls = place.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .filter(instruction -> instruction.owner().asInternalName().equals(owner))
                .map(instruction -> instruction.name().stringValue())
                .toList();
        int selector = localCalls.indexOf("selectFirstSafePlan");
        int apply = localCalls.indexOf("apply");
        assertTrue(selector >= 0, "RED: real place(...) must invoke the pure selector");
        assertEquals(1L, localCalls.stream().filter("selectFirstSafePlan"::equals).count(),
                "real placement performs one bounded selection and never searches again after mutation");
        assertTrue(apply > selector, "mutation starts only after pure first-safe selection has returned");
        assertEquals(1L, localCalls.stream().filter("apply"::equals).count(),
                "real placement invokes the mutation adapter once, never once per rejected alternative");
        return true;
    }

    private record RouteCandidate(String label, int depth, int routeOrdinal) {
    }

    private static Method requiredLawMethod(String name, Class<?>... parameters) {
        Method method = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && java.util.Arrays.equals(candidate.getParameterTypes(), parameters))
                .findFirst().orElse(null);
        if (method == null) {
            throw new AssertionError("RED: missing narrow world-safety law " + name);
        }
        method.setAccessible(true);
        return method;
    }

    private static Object invokeLaw(Method method, Object... arguments) {
        try {
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("world-safety law " + method.getName() + " must be invocable", exception);
        }
    }

    private static String enumName(Object value) {
        assertTrue(value instanceof Enum<?>, "safety decision is a named enum, not an ambiguous boolean");
        return ((Enum<?>) value).name();
    }

    private static void assertSurfaceCapMutantRejects(Method law, Object[] valid, int index, Object mutant,
                                                      String mutation) {
        Object[] changed = valid.clone();
        changed[index] = mutant;
        assertFalse((boolean) invokeLaw(law, changed), mutation + " must reject the exact-cap exception");
    }

    private static boolean naturalAnchorsPass(Object[][] specifications) {
        Class<?> candidateType = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("NaturalAnchorCandidate"))
                .findFirst().orElse(null);
        Method law = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("hasSeparatedNaturalAnchors")
                        && method.getParameterCount() == 1)
                .findFirst().orElse(null);
        assertTrue(candidateType != null && law != null,
                "RED: landing bases need one testable two-contact natural-anchor law");
        try {
            var constructor = candidateType.getDeclaredConstructor(
                    int.class, int.class, boolean.class, boolean.class);
            constructor.setAccessible(true);
            java.util.Set<Object> candidates = new java.util.HashSet<>();
            for (Object[] specification : specifications) {
                candidates.add(constructor.newInstance(specification));
            }
            law.setAccessible(true);
            return (boolean) law.invoke(null, candidates);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("natural-anchor law must be invocable", exception);
        }
    }

    private static boolean boundedFluidHit(int fluidDistance, int blockedAt) {
        Class<?> pointClass = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("FluidSearchCell")).findFirst().orElse(null);
        Class<?> cellClass = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("FluidTraversalCell")).findFirst().orElse(null);
        Method traversal = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("firstReachableFluidWithinSeven")
                        && method.getParameterCount() == 2).findFirst().orElse(null);
        assertTrue(pointClass != null && cellClass != null && traversal != null,
                "the WorldGenLevel adapter must use one testable bounded traversal implementation");
        try {
            var constructor = pointClass.getDeclaredConstructor(int.class, int.class, int.class);
            constructor.setAccessible(true);
            Object passable = java.util.Arrays.stream(cellClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals("PASSABLE")).findFirst().orElseThrow();
            Object blocked = java.util.Arrays.stream(cellClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals("BLOCKED")).findFirst().orElseThrow();
            Object fluid = java.util.Arrays.stream(cellClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals("FLUID")).findFirst().orElseThrow();
            java.util.Map<Object, Object> cells = new java.util.HashMap<>();
            Object origin = constructor.newInstance(0, 0, 0);
            cells.put(origin, passable);
            for (int x = 1; x < fluidDistance; x++) {
                cells.put(constructor.newInstance(x, 0, 0), x == blockedAt ? blocked : passable);
            }
            cells.put(constructor.newInstance(fluidDistance, 0, 0), fluid);
            java.util.function.Function<Object, Object> lookup =
                    point -> cells.getOrDefault(point, blocked);
            traversal.setAccessible(true);
            return traversal.invoke(null, java.util.Set.of(origin), lookup) != null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("bounded runtime traversal seam must be invocable", exception);
        }
    }

    private static boolean tailRoleFluidHit(boolean fluidBesideHeadspace) {
        Class<?> pointClass = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("FluidSearchCell")).findFirst().orElseThrow();
        Class<?> cellClass = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("FluidTraversalCell")).findFirst().orElseThrow();
        Method traversal = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("firstReachableFluidWithinSeven")
                        && method.getParameterCount() == 2).findFirst().orElseThrow();
        try {
            var constructor = pointClass.getDeclaredConstructor(int.class, int.class, int.class);
            constructor.setAccessible(true);
            Object blocked = java.util.Arrays.stream(cellClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals("BLOCKED")).findFirst().orElseThrow();
            Object passable = java.util.Arrays.stream(cellClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals("PASSABLE")).findFirst().orElseThrow();
            Object fluid = java.util.Arrays.stream(cellClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals("FLUID")).findFirst().orElseThrow();
            Object headspace = constructor.newInstance(0, 1, 0);
            Object floor = constructor.newInstance(0, 0, 0);
            Object fluidCell = constructor.newInstance(1, fluidBesideHeadspace ? 1 : 0, 0);
            java.util.Map<Object, Object> cells = new java.util.HashMap<>();
            cells.put(headspace, passable);
            cells.put(floor, blocked);
            cells.put(fluidCell, fluid);
            java.util.function.Function<Object, Object> lookup =
                    point -> cells.getOrDefault(point, blocked);
            traversal.setAccessible(true);
            return traversal.invoke(null, java.util.Set.of(headspace), lookup) != null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("tail-role bounded traversal seam must be invocable", exception);
        }
    }

    private static boolean fluidHitAcrossMiddleCell(Object middle, Object fluid) {
        Class<?> pointClass = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("FluidSearchCell")).findFirst().orElseThrow();
        Class<?> cellClass = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("FluidTraversalCell")).findFirst().orElseThrow();
        Method traversal = java.util.Arrays.stream(PowderTrapWorldSafetyLaw.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("firstReachableFluidWithinSeven")
                        && method.getParameterCount() == 2).findFirst().orElseThrow();
        try {
            var constructor = pointClass.getDeclaredConstructor(int.class, int.class, int.class);
            constructor.setAccessible(true);
            Object blocked = java.util.Arrays.stream(cellClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals("BLOCKED")).findFirst().orElseThrow();
            Object origin = constructor.newInstance(0, 0, 0);
            Object middleCell = constructor.newInstance(1, 0, 0);
            Object fluidCell = constructor.newInstance(2, 0, 0);
            java.util.Map<Object, Object> cells = new java.util.HashMap<>();
            cells.put(middleCell, middle);
            cells.put(fluidCell, fluid);
            java.util.function.Function<Object, Object> lookup =
                    point -> cells.getOrDefault(point, blocked);
            traversal.setAccessible(true);
            return traversal.invoke(null, java.util.Set.of(origin), lookup) != null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("planned-final-state traversal seam must be invocable", exception);
        }
    }
}
