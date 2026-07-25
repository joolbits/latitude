package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-JVM laws for natural-relief subterranean trap plans. */
class SubterraneanTrapPlanTest {

    private static final SubterraneanTrapLayout.Placement PLACEMENT =
            SubterraneanTrapLayout.placements(73L, 4, -9).getFirst();

    @Test
    void firstAir101MeansMinimumPowderRoofY100AndPreferredLanding68() {
        SubterraneanTrapPlan.Result result = plan(firstAir(101), fullSnow());
        assertTrue(result.isAccepted());
        assertEquals(100, result.accepted().roofY());
        assertEquals(68, result.accepted().landingY());
        assertNull(result.rejection());
    }

    @Test
    void localReliefAllowsOnePowderBlockAndAThreeBlockSmoothApproachButRejectsTheNamedLimits() {
        int[][] smoothThreeBlockApproach = firstAir(101);
        smoothThreeBlockApproach[approachCell(1).x()][approachCell(1).z()] = 102;
        smoothThreeBlockApproach[approachCell(2).x()][approachCell(2).z()] = 103;
        smoothThreeBlockApproach[approachCell(3).x()][approachCell(3).z()] = 104;
        assertTrue(plan(smoothThreeBlockApproach, fullSnow()).isAccepted(),
                "powder stays within one block while a firm long-axis approach may rise smoothly by three");

        int[][] powderReliefTwo = firstAir(101);
        SubterraneanTrapLayout.Cell raisedPowder = PLACEMENT.powder().getFirst();
        powderReliefTwo[raisedPowder.x()][raisedPowder.z()] = 103;
        assertFalse(plan(powderReliefTwo, fullSnow()).isAccepted(), "two blocks across powder is too uneven");

        int[][] adjacentStepTwo = firstAir(101);
        adjacentStepTwo[approachCell(1).x()][approachCell(1).z()] = 103;
        assertFalse(plan(adjacentStepTwo, fullSnow()).isAccepted(),
                "a two-block cardinal step in the collar or approach is unsafe");

        int[][] totalReliefFour = firstAir(101);
        totalReliefFour[approachCell(1).x()][approachCell(1).z()] = 102;
        totalReliefFour[approachCell(2).x()][approachCell(2).z()] = 103;
        totalReliefFour[approachCell(3).x()][approachCell(3).z()] = 104;
        totalReliefFour[approachCell(4).x()][approachCell(4).z()] = 105;
        assertFalse(plan(totalReliefFour, fullSnow()).isAccepted(),
                "a four-block total collar or approach relief exceeds the local terrain allowance");
    }

    @Test
    void powderWritesKeepEachNaturalRoofHeightAndShareMinimumBasedLanding() {
        int[][] heights = firstAir(101);
        SubterraneanTrapLayout.Cell raised = PLACEMENT.powder().getFirst();
        heights[raised.x()][raised.z()] = 102;
        SubterraneanTrapPlan.Plan plan = plan(heights, fullSnow()).accepted();
        assertEquals(68, plan.landingY());
        assertEquals(1, writesAt(plan.writes(), raised.x(), 101, raised.z(),
                SubterraneanTrapPlan.Phase.SURFACE_POWDER));
        assertEquals(1, writesAt(plan.writes(), raised.x(), 100, raised.z(), SubterraneanTrapPlan.Phase.CLEAR));
        for (SubterraneanTrapLayout.Cell powder : PLACEMENT.powder()) {
            int roofY = heights[powder.x()][powder.z()] - 1;
            assertEquals(1, writesAt(plan.writes(), powder.x(), roofY, powder.z(),
                    SubterraneanTrapPlan.Phase.SURFACE_POWDER));
            assertEquals(1, writesAt(plan.writes(), powder.x(), 68, powder.z(), SubterraneanTrapPlan.Phase.CUSHION));
            for (int y = roofY - 1; y >= 69; y--) {
                assertEquals(1, writesAt(plan.writes(), powder.x(), y, powder.z(), SubterraneanTrapPlan.Phase.CLEAR));
            }
        }
    }

    @Test
    void everyCushionHasOneExplicitUniqueBaseDirectlyBelowAndBasesPrecedeCushionsAndSurface() {
        Set<String> phases = java.util.Arrays.stream(SubterraneanTrapPlan.Phase.values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(phases.contains("CUSHION_BASE"),
                "RED: a safe landing must plan an explicit final-solid CUSHION_BASE, not depend on incidental cave terrain");

        SubterraneanTrapPlan.Phase basePhase = namedPhase("CUSHION_BASE");
        SubterraneanTrapPlan.Plan plan = plan(firstAir(101), fullSnow()).accepted();
        List<SubterraneanTrapPlan.Write> bases = plan.writes().stream()
                .filter(write -> write.phase() == basePhase).toList();
        List<SubterraneanTrapPlan.Write> cushions = plan.writes().stream()
                .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.CUSHION).toList();
        assertEquals(cushions.size(), bases.size(), "every cushion gets exactly one authored base");

        int firstSurface = firstPhaseIndex(plan.writes(), "SURFACE_POWDER");
        Set<String> baseCoordinates = new HashSet<>();
        for (SubterraneanTrapPlan.Write base : bases) {
            assertTrue(baseCoordinates.add(base.x() + ":" + base.y() + ":" + base.z()),
                    "cushion-base coordinates are unique");
            assertTrue(indexOf(plan.writes(), base) < firstSurface,
                    "all final-solid bases are applied before any surface is replaced");
        }
        for (SubterraneanTrapPlan.Write cushion : cushions) {
            SubterraneanTrapPlan.Write base = bases.stream()
                    .filter(candidate -> candidate.x() == cushion.x()
                            && candidate.y() == cushion.y() - 1
                            && candidate.z() == cushion.z())
                    .findFirst().orElse(null);
            assertTrue(base != null, "the base is exactly one block vertically below its cushion");
            assertTrue(indexOf(plan.writes(), base) < indexOf(plan.writes(), cushion),
                    "the solid base must be applied before the player-facing powder cushion");
        }
    }

    @Test
    void callerCertifiedThinSnowIsAcceptedForPowderAndVisualCollarAndRemovedAfterReplacement() {
        int[][] kinds = fullSnow();
        SubterraneanTrapLayout.Cell powder = PLACEMENT.powder().getFirst();
        SubterraneanTrapLayout.Cell collar = lateralVisualCollarCell();
        kinds[powder.x()][powder.z()] = SubterraneanTrapPlan.THIN_SNOW;
        kinds[collar.x()][collar.z()] = SubterraneanTrapPlan.THIN_SNOW;
        SubterraneanTrapPlan.Result result = plan(firstAir(101), kinds);
        assertTrue(result.isAccepted(), "caller-certified thin snow is a valid cosmetic layer over safe support");
        SubterraneanTrapPlan.Plan plan = result.accepted();
        assertEquals(1, writesAt(plan.writes(), powder.x(), 100, powder.z(), SubterraneanTrapPlan.Phase.SURFACE_POWDER));
        assertEquals(1, writesAt(plan.writes(), powder.x(), 101, powder.z(),
                SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER));
        assertEquals(SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER, plan.writes().getLast().phase());
        assertEquals(0, writesAt(plan.writes(), collar.x(), 101, collar.z(),
                SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER), "unreplaced collar snow remains untouched");
    }

    @Test
    void terrainFitIgnoresRemoteOldRectangleCellsButKeepsTheLocalCollarAndApproachMeaningful() {
        int[][] kinds = fullSnow();
        int[][] heights = firstAir(101);
        SubterraneanTrapLayout.Cell remote = remoteOldRectangleCell();
        kinds[remote.x()][remote.z()] = SubterraneanTrapPlan.OTHER;
        heights[remote.x()][remote.z()] = 109;
        assertTrue(plan(heights, kinds).isAccepted(),
                "unrelated legacy rectangle cells must not decide a local powder/collar/approach fit");

        kinds = fullSnow();
        kinds[lateralVisualCollarCell().x()][lateralVisualCollarCell().z()] = SubterraneanTrapPlan.OTHER;
        assertFalse(plan(firstAir(101), kinds).isAccepted(), "the immediate visual collar remains constrained");

        kinds = fullSnow();
        kinds[approachCell(1).x()][approachCell(1).z()] = SubterraneanTrapPlan.OTHER;
        assertFalse(plan(firstAir(101), kinds).isAccepted(), "the firm long-axis approach remains constrained");
    }

    @Test
    void naturalPowderIsAllowedInTheLateralVisualCollarButNotTheFirmApproach() {
        int[][] kinds = fullSnow();
        SubterraneanTrapLayout.Cell collar = lateralVisualCollarCell();
        kinds[collar.x()][collar.z()] = SubterraneanTrapPlan.POWDER;
        assertTrue(plan(firstAir(101), kinds).isAccepted(),
                "natural powder belongs in the lateral visual collar around a powder cap");

        kinds = fullSnow();
        SubterraneanTrapLayout.Cell approach = approachCell(1);
        kinds[approach.x()][approach.z()] = SubterraneanTrapPlan.POWDER;
        assertFalse(plan(firstAir(101), kinds).isAccepted(), "firm approach cells may not be powder");
    }

    @Test
    void writesAreUniqueAndOnlyTheNamedEscapePhasesTouchNonpowderColumns() {
        SubterraneanTrapPlan.Plan plan = plan(firstAir(101), fullSnow()).accepted();
        Set<String> coordinates = new HashSet<>();
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(PLACEMENT.powder());
        int escapeWrites = 0;
        for (SubterraneanTrapPlan.Write write : plan.writes()) {
            assertTrue(coordinates.add(write.x() + ":" + write.y() + ":" + write.z()));
            if (!powder.contains(new SubterraneanTrapLayout.Cell(write.x(), write.z()))) {
                escapeWrites++;
                assertTrue(write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_FLOOR
                                || write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_CLEAR
                                || write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL,
                        "one narrow doorway and route replace the old broad chamber dilation");
            }
        }
        assertTrue(escapeWrites > 0);
    }

    @Test
    void planOrderIsStableAndImmutableForRuntimeGuardColumns() {
        SubterraneanTrapPlan.Result first = plan(firstAir(101), fullSnow());
        assertEquals(first, plan(firstAir(101), fullSnow()));
        assertThrows(UnsupportedOperationException.class, () -> first.accepted().writes().add(
                new SubterraneanTrapPlan.Write(0, 0, 0, SubterraneanTrapPlan.Phase.CLEAR)));
        int phaseGroup = -1;
        for (SubterraneanTrapPlan.Write write : first.accepted().writes()) {
            int next = phaseOrder(write.phase().name());
            assertTrue(next >= phaseGroup);
            phaseGroup = next;
        }
    }

    @Test
    void acceptedPlanExposesEscapeRouteMetadataAndExplicitEscapeWritePhases() {
        SubterraneanTrapPlan.Plan plan = plan(firstAir(101), fullSnow()).accepted();
        assertTrue(recordComponentNames(plan).contains("escapeRoute"),
                "accepted Plan exposes escapeRoute metadata for scanner and write-law proof");
        Set<String> phases = java.util.Arrays.stream(SubterraneanTrapPlan.Phase.values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(phases.containsAll(Set.of("ESCAPE_FLOOR", "ESCAPE_CLEAR", "ESCAPE_MINE_TAIL")),
                "escape floor, two-high clearance, and solid mining tail have explicit write phases");
    }

    @Test
    void tooShortSyntheticGeometryRejectsAtomicallyForNamedEscapeElevationCapacity() {
        Set<String> rejections = java.util.Arrays.stream(SubterraneanTrapPlan.Rejection.values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(rejections.contains("ESCAPE_ELEVATION_CAPACITY"),
                "a plan without distinct rising route, tail, and closure capacity has a named atomic rejection");
        SubterraneanTrapPlan.Result result = SubterraneanTrapPlan.plan(tooShortEscapePlacement(), firstAir(101), fullSnow());
        assertFalse(result.isAccepted(), "too-short synthetic geometry never exposes a partial fall or route plan");
        assertNull(result.accepted());
        assertEquals("ESCAPE_ELEVATION_CAPACITY", result.rejection().name());
    }

    @Test
    void escapeRouteIsAUniqueRisingCardinalPathOutsidePowderWithFloorsAndTwoHighClearance() {
        SubterraneanTrapPlan.Plan plan = plan(firstAir(101), fullSnow()).accepted();
        Object route = requiredComponent(plan, "escapeRoute");
        List<?> steps = requiredList(route, "steps");
        assertTrue(steps.size() >= 3, "route has a real rising path before its mine-through tail");
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < steps.size(); index++) {
            Object step = steps.get(index);
            int x = requiredInt(step, "x");
            int y = requiredInt(step, "y");
            int z = requiredInt(step, "z");
            assertTrue(seen.add(x + ":" + y + ":" + z), "route cells are unique; repeated loops are forbidden");
            assertFalse(Set.copyOf(PLACEMENT.powder()).contains(new SubterraneanTrapLayout.Cell(x, z)),
                    "route never intersects a powder fall column");
            assertEquals(1, writesAt(plan.writes(), x, y, z, namedPhase("ESCAPE_FLOOR")),
                    "each route cell gets an authored snow-block floor");
            assertEquals(2, writesAt(plan.writes(), x, y + 1, z, namedPhase("ESCAPE_CLEAR"))
                            + writesAt(plan.writes(), x, y + 2, z, namedPhase("ESCAPE_CLEAR")),
                    "each route floor has exactly two clear headspace writes");
            if (index > 0) {
                Object previous = steps.get(index - 1);
                assertEquals(1, Math.abs(x - requiredInt(previous, "x")) + Math.abs(z - requiredInt(previous, "z")),
                        "route steps are cardinally adjacent");
                assertEquals(1, y - requiredInt(previous, "y"), "route floor rises exactly one per step");
            }
        }
    }

    @Test
    void escapeMetadataProvesLocalPerimeterShellElevationTailAndSurfaceClosure() {
        SubterraneanTrapPlan.Plan plan = plan(firstAir(101), fullSnow()).accepted();
        Object route = requiredComponent(plan, "escapeRoute");
        for (SubterraneanTrapPlan.Write write : plan.writes()) {
            assertTrue(write.x() >= 1 && write.x() <= 14 && write.z() >= 1 && write.z() <= 14,
                    "all trap and escape writes remain owner-chunk local 1..14");
        }
        for (Object probe : requiredList(route, "shellProbes")) {
            assertTrue(requiredInt(probe, "x") >= 0 && requiredInt(probe, "x") <= 15
                            && requiredInt(probe, "z") >= 0 && requiredInt(probe, "z") <= 15,
                    "all route shell probes stay in the owner chunk, including its 0..15 boundary");
        }
        assertTrue(requiredList(route, "tailSteps").size() >= 2 && requiredList(route, "tailSteps").size() <= 3,
                "the route ends in two or three snow mining steps");
        assertTrue(recordComponentNames(route).containsAll(Set.of("entry", "exit", "closureCell", "surfacePlug")),
                "metadata proves the landing doorway, high/low closure, and intact surface plug");
        Object entry = requiredComponent(route, "entry");
        assertEquals(plan.landingY(), requiredInt(entry, "y"));
        assertEquals(1, writesAt(plan.writes(), requiredInt(entry, "x"), requiredInt(entry, "y"),
                requiredInt(entry, "z"), SubterraneanTrapPlan.Phase.ESCAPE_FLOOR));
        assertEquals(2, writesAt(plan.writes(), requiredInt(entry, "x"), requiredInt(entry, "y") + 1,
                        requiredInt(entry, "z"), SubterraneanTrapPlan.Phase.ESCAPE_CLEAR)
                        + writesAt(plan.writes(), requiredInt(entry, "x"), requiredInt(entry, "y") + 2,
                        requiredInt(entry, "z"), SubterraneanTrapPlan.Phase.ESCAPE_CLEAR),
                "the bank has exactly one two-high landing doorway");
        assertEquals(0, writesAtAnyPhase(plan.writes(), requiredInt(entry, "x"),
                requiredInt(entry, "y") + 3, requiredInt(entry, "z")),
                "the bank wall remains intact above the two-high doorway");
        Object exit = requiredComponent(route, "exit");
        assertTrue(requiredList(route, "tailSteps").contains(exit), "the authored high exit is in the mining tail");
        Object finalTail = requiredList(route, "tailSteps").getLast();
        Object plug = requiredComponent(route, "surfacePlug");
        assertEquals(requiredInt(finalTail, "x"), requiredInt(plug, "x"));
        assertEquals(requiredInt(finalTail, "z"), requiredInt(plug, "z"));
        assertEquals(requiredInt(finalTail, "y") + 1, requiredInt(plug, "y"),
                "the virtual plug floor is the final rising tail step above its authored floor");
        assertEquals(1, writesAt(plan.writes(), requiredInt(plug, "x"), requiredInt(plug, "y"),
                requiredInt(plug, "z"), SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL));
        assertEquals(1, writesAt(plan.writes(), requiredInt(plug, "x"), requiredInt(plug, "y") + 1,
                requiredInt(plug, "z"), SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL),
                "the last future-mined head remains a snow-block support until the player reaches it");
        assertEquals(0, writesAtAnyPhase(plan.writes(), requiredInt(plug, "x"), requiredInt(plug, "y") + 2,
                requiredInt(plug, "z")), "the air above the intact support remains untouched");
        Object closure = requiredComponent(route, "closureCell");
        assertEquals(0, writesAtAnyPhase(plan.writes(), requiredInt(closure, "x"), requiredInt(closure, "y"),
                requiredInt(closure, "z")), "at least one intact perimeter cell separates high and low ends");
        Object low = requiredList(route, "steps").getFirst();
        assertTrue(Math.abs(requiredInt(plug, "x") - requiredInt(low, "x"))
                        + Math.abs(requiredInt(plug, "z") - requiredInt(low, "z")) > 1,
                "the high plug is not cardinally adjacent to the low route");
        assertTrue(plan.writes().getLast().phase() == SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER
                        || plan.writes().getLast().phase() == SubterraneanTrapPlan.Phase.SURFACE_POWDER,
                "surface powder and cosmetic layer removal remain the final material actions");
    }

    @Test
    void everyAcceptedRaisedTailCandidateEitherReplansOrKeepsItsFinalTailSealedToSnowSupport() {
        int exercised = 0;
        for (SubterraneanTrapLayout.Placement placement :
                SubterraneanTrapLayout.placements(73L, 4, -9)) {
            SubterraneanTrapPlan.Result baseline =
                    SubterraneanTrapPlan.plan(placement, firstAir(101), fullSnow());
            if (!baseline.isAccepted()) {
                continue;
            }
            exercised++;
            SubterraneanTrapPlan.EscapeStep oldTail =
                    baseline.accepted().escapeRoute().tailSteps().getLast();
            int[][] raisedTail = firstAir(101);
            raisedTail[oldTail.x()][oldTail.z()] = 111;

            SubterraneanTrapPlan.Result replanned =
                    SubterraneanTrapPlan.plan(placement, raisedTail, fullSnow());
            if (!replanned.isAccepted()) {
                continue;
            }
            SubterraneanTrapPlan.EscapeStep finalTail =
                    replanned.accepted().escapeRoute().tailSteps().getLast();
            assertEquals(raisedTail[finalTail.x()][finalTail.z()] - 1, finalTail.y() + 2,
                    "the final authored tail head must meet its own intact snow-block support with no gap");
        }
        assertTrue(exercised > 0, "synthetic sweep must exercise accepted placements in both template axes");
    }

    @Test
    void everyAcceptedReliefCandidateKeepsNonFinalTailColumnsStrictlyBuried() {
        int exercised = 0;
        for (SubterraneanTrapLayout.Placement placement :
                SubterraneanTrapLayout.placements(73L, 4, -9)) {
            SubterraneanTrapPlan.Result baseline =
                    SubterraneanTrapPlan.plan(placement, firstAir(101), fullSnow());
            if (!baseline.isAccepted()) {
                continue;
            }
            List<SubterraneanTrapPlan.EscapeStep> baselineTail =
                    baseline.accepted().escapeRoute().tailSteps();
            for (int index = 0; index < baselineTail.size() - 1; index++) {
                SubterraneanTrapPlan.EscapeStep exposed = baselineTail.get(index);
                int[][] relief = firstAir(101);
                relief[exposed.x()][exposed.z()] = exposed.y() + 3;

                SubterraneanTrapPlan.Result replanned =
                        SubterraneanTrapPlan.plan(placement, relief, fullSnow());
                if (!replanned.isAccepted()) {
                    continue;
                }
                exercised++;
                List<SubterraneanTrapPlan.EscapeStep> finalTail =
                        replanned.accepted().escapeRoute().tailSteps();
                for (int finalIndex = 0; finalIndex < finalTail.size() - 1; finalIndex++) {
                    SubterraneanTrapPlan.EscapeStep step = finalTail.get(finalIndex);
                    assertTrue(step.y() + 2 < relief[step.x()][step.z()] - 1,
                            "only the final tail may meet its local surface support");
                }
                SubterraneanTrapPlan.EscapeStep exit = finalTail.getLast();
                assertEquals(relief[exit.x()][exit.z()] - 1, exit.y() + 2,
                        "the replanned final tail still meets its exact intact surface support");
            }
        }
        assertTrue(exercised > 0, "relief matrix must retain at least one accepted replanned candidate");
    }

    @Test
    void nonFinalReliefSurfaceEqualityIsRejectedByThePureConcealmentLaw() {
        int landingY = 90;
        List<SubterraneanTrapLayout.Cell> route = List.of(
                new SubterraneanTrapLayout.Cell(1, 1),
                new SubterraneanTrapLayout.Cell(2, 1),
                new SubterraneanTrapLayout.Cell(3, 1),
                new SubterraneanTrapLayout.Cell(4, 1),
                new SubterraneanTrapLayout.Cell(5, 1));
        int[][] relief = firstAir(120);
        relief[4][1] = 96;
        relief[5][1] = 97;
        try {
            var concealment = SubterraneanTrapPlan.class.getDeclaredMethod(
                    "tailWritesStayConcealed", List.class, int.class, int.class, int.class,
                    int[][].class, int[][].class);
            concealment.setAccessible(true);
            assertFalse((boolean) concealment.invoke(
                            null, route, 3, 5, landingY, relief, fullSnow()),
                    "a non-final tail top touching its local surface would create a second opening");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("pure tail concealment law must remain directly provable", exception);
        }
    }

    @Test
    void everyAcceptedCataloguePlacementHasOneConnectedLocalEscapeToItsActualSurface() {
        Set<String> acceptedShapes = new HashSet<>();
        int[][] heights = firstAir(101);
        int[][] kinds = fullSnow();
        for (SubterraneanTrapLayout.Placement placement :
                SubterraneanTrapLayout.placements(73L, 4, -9)) {
            SubterraneanTrapPlan.Result result =
                    SubterraneanTrapPlan.plan(placement, heights, kinds);
            if (!result.isAccepted()) {
                continue;
            }
            acceptedShapes.add(placement.template().stations() + ":" + placement.template().axis());
            SubterraneanTrapPlan.Plan plan = result.accepted();
            SubterraneanTrapPlan.EscapeRoute route = plan.escapeRoute();
            Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(placement.powder());

            Set<String> writeCoordinates = new HashSet<>();
            for (SubterraneanTrapPlan.Write write : plan.writes()) {
                assertTrue(write.x() >= 1 && write.x() <= 14 && write.z() >= 1 && write.z() <= 14);
                assertTrue(writeCoordinates.add(write.x() + ":" + write.y() + ":" + write.z()));
            }
            for (SubterraneanTrapPlan.EscapeStep probe : route.shellProbes()) {
                assertTrue(probe.x() >= 0 && probe.x() <= 15 && probe.z() >= 0 && probe.z() <= 15);
            }

            long entryPowderNeighbours = powder.stream()
                    .filter(cell -> Math.abs(cell.x() - route.entry().x())
                            + Math.abs(cell.z() - route.entry().z()) == 1)
                    .count();
            assertEquals(1, entryPowderNeighbours, "there is exactly one landing doorway into the fall volume");

            Set<String> routeCoordinates = new HashSet<>();
            SubterraneanTrapPlan.EscapeStep previous = null;
            for (SubterraneanTrapPlan.EscapeStep step : route.steps()) {
                assertTrue(routeCoordinates.add(step.x() + ":" + step.y() + ":" + step.z()));
                assertFalse(powder.contains(new SubterraneanTrapLayout.Cell(step.x(), step.z())));
                if (previous != null) {
                    assertEquals(1, Math.abs(step.x() - previous.x()) + Math.abs(step.z() - previous.z()));
                    assertEquals(1, step.y() - previous.y());
                }
                previous = step;
            }
            for (SubterraneanTrapPlan.EscapeStep tail : route.tailSteps()) {
                assertTrue(routeCoordinates.add(tail.x() + ":" + tail.y() + ":" + tail.z()));
                assertFalse(powder.contains(new SubterraneanTrapLayout.Cell(tail.x(), tail.z())));
                assertEquals(1, Math.abs(tail.x() - previous.x()) + Math.abs(tail.z() - previous.z()));
                assertEquals(1, tail.y() - previous.y());
                previous = tail;
            }

            SubterraneanTrapPlan.EscapeStep finalTail = route.tailSteps().getLast();
            assertEquals(heights[finalTail.x()][finalTail.z()] - 1, finalTail.y() + 2,
                    "final mining head is the captured snow-block surface support");
            assertEquals(finalTail.x(), route.surfacePlug().x());
            assertEquals(finalTail.z(), route.surfacePlug().z());
            assertEquals(finalTail.y() + 1, route.surfacePlug().y(),
                    "surfacePlug records the virtual floor directly below the intact support");
            assertEquals(0, writesAtAnyPhase(plan.writes(), route.closureCell().x(),
                    route.closureCell().y(), route.closureCell().z()));
            assertTrue(Math.abs(route.closureCell().x() - finalTail.x())
                            + Math.abs(route.closureCell().z() - finalTail.z()) == 1,
                    "one intact perimeter closure follows the high exit");
        }
        assertEquals(Set.of("12:X", "12:Z", "14:X", "14:Z"), acceptedShapes,
                "the invariant matrix exercises every accepted catalogue size and axis");
    }

    @Test
    void preferredLandingDepthOrderIsTheExactNineValueNearFirstSequence() {
        Method order = requiredPlannerMethod("preferredDepthOrder");
        Object value = invokePlanner(order);
        assertTrue(value instanceof List<?>, "preferredDepthOrder is deterministic ordered planner metadata");
        assertEquals(List.of(32, 33, 31, 34, 30, 35, 29, 36, 28), value,
                "RED: try the preferred landing first, then alternate deeper and shallower through the bounded range");
    }

    @Test
    void routeAlternativesAreOrderedUniqueBoundedStableAndKeepLegacyPlanFirst() {
        int[][] heights = firstAir(101);
        int[][] kinds = fullSnow();
        List<SubterraneanTrapPlan.Plan> alternatives =
                planAlternatives(PLACEMENT, heights, kinds, 32);

        assertFalse(alternatives.isEmpty(), "the accepted flat fixture exposes route alternatives");
        assertTrue(alternatives.size() <= 34, "one landing depth exposes at most 34 route alternatives");
        assertEquals(alternatives, planAlternatives(PLACEMENT, heights, kinds, 32),
                "the same placement and snapshot must produce the same ordered alternatives");
        assertEquals(alternatives.size(), new HashSet<>(alternatives).size(),
                "route alternatives are unique complete plans, not duplicate retry work");

        List<AlternativeOrder> actualOrder = alternatives.stream()
                .map(plan -> alternativeOrder(PLACEMENT, plan.escapeRoute())).toList();
        List<AlternativeOrder> sortedOrder = actualOrder.stream()
                .sorted(AlternativeOrder.COMPARATOR).toList();
        assertEquals(sortedOrder, actualOrder,
                "RED: order is doorway X/Z, clockwise then counterclockwise, route length ascending, tail 2 then 3");

        SubterraneanTrapPlan.Result legacy = plan(heights, kinds);
        assertTrue(legacy.isAccepted());
        assertEquals(legacy.accepted(), alternatives.getFirst(),
                "plan(...) remains the compatibility seam and selects the first preferred-depth alternative");
    }

    @Test
    void everyAlternativeAtEveryPreferredDepthPreservesTheCompleteEscapeGeometryAndWritePhases() {
        int[][] heights = firstAir(101);
        int[][] kinds = fullSnow();
        List<Integer> depths = List.of(32, 33, 31, 34, 30, 35, 29, 36, 28);
        Set<String> expectedPhases = Set.of(
                "CUSHION_BASE", "CUSHION", "ESCAPE_FLOOR", "ESCAPE_MINE_TAIL",
                "CLEAR", "ESCAPE_CLEAR", "SURFACE_POWDER", "REMOVE_SURFACE_LAYER");
        assertEquals(expectedPhases,
                java.util.Arrays.stream(SubterraneanTrapPlan.Phase.values()).map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet()),
                "alternative planning preserves every existing material phase and adds no hidden phase");

        int exercised = 0;
        for (int depth : depths) {
            List<SubterraneanTrapPlan.Plan> alternatives =
                    planAlternatives(PLACEMENT, heights, kinds, depth);
            assertFalse(alternatives.isEmpty(), "flat snow fixture must exercise depth " + depth);
            assertTrue(alternatives.size() <= 34, "each landing has its own bounded alternative list");
            for (SubterraneanTrapPlan.Plan alternative : alternatives) {
                exercised++;
                assertCompleteAlternativeGeometry(PLACEMENT, alternative, depth, heights, expectedPhases);
            }
        }
        assertTrue(exercised >= depths.size(), "all nine preferred landing depths were inspected");
    }

    @Test
    void depth32OffersCounterclockwiseRecoveryAfterTheSameDoorwaysClockwiseAlternative() {
        List<SubterraneanTrapPlan.Plan> alternatives =
                planAlternatives(PLACEMENT, firstAir(101), fullSnow(), 32);
        java.util.Map<String, List<Integer>> directionsByDoorway = new java.util.LinkedHashMap<>();
        for (SubterraneanTrapPlan.Plan alternative : alternatives) {
            SubterraneanTrapPlan.EscapeStep entry = alternative.escapeRoute().entry();
            directionsByDoorway.computeIfAbsent(entry.x() + ":" + entry.z(), ignored -> new java.util.ArrayList<>())
                    .add(routeDirection(PLACEMENT, alternative.escapeRoute()));
        }

        List<Integer> recoverable = directionsByDoorway.values().stream()
                .filter(directions -> directions.contains(0) && directions.contains(1))
                .findFirst().orElse(null);
        assertTrue(recoverable != null,
                "RED: at least one depth-32 doorway must retain both directions so shell failure can recover");
        assertEquals(0, recoverable.getFirst(), "clockwise is tried before counterclockwise for one doorway");
        assertTrue(recoverable.indexOf(1) > recoverable.lastIndexOf(0),
                "all clockwise shapes for a doorway precede its counterclockwise recovery shapes");
    }

    private static SubterraneanTrapPlan.Result plan(int[][] heights, int[][] kinds) {
        return SubterraneanTrapPlan.plan(PLACEMENT, heights, kinds);
    }

    private static Method requiredPlannerMethod(String name, Class<?>... parameters) {
        Method method = java.util.Arrays.stream(SubterraneanTrapPlan.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && java.util.Arrays.equals(candidate.getParameterTypes(), parameters))
                .findFirst().orElse(null);
        if (method == null) {
            throw new AssertionError("RED: missing bounded planner seam " + name);
        }
        method.setAccessible(true);
        return method;
    }

    private static Object invokePlanner(Method method, Object... arguments) {
        try {
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("planner seam " + method.getName() + " must be invocable", exception);
        }
    }

    private static List<SubterraneanTrapPlan.Plan> planAlternatives(
            SubterraneanTrapLayout.Placement placement, int[][] heights, int[][] kinds, int depth) {
        Method alternatives = requiredPlannerMethod("planAlternatives",
                SubterraneanTrapLayout.Placement.class, int[][].class, int[][].class, int.class);
        Object value = invokePlanner(alternatives, placement, heights, kinds, depth);
        assertTrue(value instanceof List<?>, "planAlternatives returns one deterministic ordered plan list");
        List<?> raw = (List<?>) value;
        assertTrue(raw.stream().allMatch(SubterraneanTrapPlan.Plan.class::isInstance),
                "every alternative is a complete immutable Plan");
        @SuppressWarnings("unchecked")
        List<SubterraneanTrapPlan.Plan> plans = (List<SubterraneanTrapPlan.Plan>) raw;
        return plans;
    }

    private static void assertCompleteAlternativeGeometry(
            SubterraneanTrapLayout.Placement placement, SubterraneanTrapPlan.Plan plan, int depth,
            int[][] heights, Set<String> expectedPhases) {
        assertEquals(depth, plan.roofY() - plan.landingY(), "the requested landing depth is exact");

        Set<String> writeCoordinates = new HashSet<>();
        for (SubterraneanTrapPlan.Write write : plan.writes()) {
            assertTrue(write.x() >= 1 && write.x() <= 14 && write.z() >= 1 && write.z() <= 14,
                    "every alternative write stays in owner-local 1..14");
            assertTrue(writeCoordinates.add(write.x() + ":" + write.y() + ":" + write.z()),
                    "every alternative remains conflict-free");
            assertTrue(expectedPhases.contains(write.phase().name()), "only existing material phases are used");
        }
        int phaseGroup = -1;
        for (SubterraneanTrapPlan.Write write : plan.writes()) {
            int next = phaseOrder(write.phase().name());
            assertTrue(next >= phaseGroup, "every alternative preserves phase ordering");
            phaseGroup = next;
        }

        SubterraneanTrapPlan.EscapeRoute route = plan.escapeRoute();
        for (SubterraneanTrapPlan.EscapeStep probe : route.shellProbes()) {
            assertTrue(probe.x() >= 0 && probe.x() <= 15 && probe.z() >= 0 && probe.z() <= 15,
                    "every shell probe stays in the owner chunk");
        }
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(placement.powder());
        long entryPowderNeighbours = powder.stream()
                .filter(cell -> cardinalDistance(cell.x(), cell.z(), route.entry().x(), route.entry().z()) == 1)
                .count();
        assertEquals(1, entryPowderNeighbours, "each alternative opens exactly one bank doorway");
        assertEquals(plan.landingY(), route.entry().y());

        List<SubterraneanTrapPlan.EscapeStep> corridor = new java.util.ArrayList<>();
        corridor.addAll(route.steps());
        corridor.addAll(route.tailSteps());
        assertFalse(corridor.isEmpty());
        assertEquals(1, cardinalDistance(
                route.entry().x(), route.entry().z(), corridor.getFirst().x(), corridor.getFirst().z()));
        assertEquals(route.entry().y(), corridor.getFirst().y(),
                "the landing doorway enters the first perimeter floor at the same elevation");

        Set<String> routeCoordinates = new HashSet<>();
        SubterraneanTrapPlan.EscapeStep previous = null;
        for (SubterraneanTrapPlan.EscapeStep step : corridor) {
            assertTrue(routeCoordinates.add(step.x() + ":" + step.y() + ":" + step.z()),
                    "the complete route is duplicate-free");
            assertFalse(powder.contains(new SubterraneanTrapLayout.Cell(step.x(), step.z())),
                    "the escape never crosses a fall column");
            if (previous != null) {
                assertEquals(1, cardinalDistance(previous.x(), previous.z(), step.x(), step.z()),
                        "the escape is cardinally connected");
                assertEquals(1, step.y() - previous.y(), "the escape rises exactly one block per step");
            }
            previous = step;
        }
        assertTrue(route.tailSteps().size() == 2 || route.tailSteps().size() == 3,
                "the future-mined tail is exactly two or three steps");

        SubterraneanTrapPlan.EscapeStep finalTail = route.tailSteps().getLast();
        for (SubterraneanTrapPlan.EscapeStep step : corridor.subList(0, corridor.size() - 1)) {
            assertTrue(step.y() + 2 < heights[step.x()][step.z()] - 1,
                    "every non-final route top remains strictly buried");
        }
        assertEquals(heights[finalTail.x()][finalTail.z()] - 1, finalTail.y() + 2,
                "the final tail meets its exact intact surface support");
        assertEquals(finalTail.x(), route.surfacePlug().x());
        assertEquals(finalTail.z(), route.surfacePlug().z());
        assertEquals(finalTail.y() + 1, route.surfacePlug().y());
        assertEquals(1, cardinalDistance(
                finalTail.x(), finalTail.z(), route.closureCell().x(), route.closureCell().z()));
        assertTrue(plan.writes().stream().noneMatch(write ->
                        write.x() == route.closureCell().x() && write.z() == route.closureCell().z()),
                "the reserved perimeter closure column remains untouched");
    }

    private static AlternativeOrder alternativeOrder(
            SubterraneanTrapLayout.Placement placement, SubterraneanTrapPlan.EscapeRoute route) {
        return new AlternativeOrder(
                route.entry().x(), route.entry().z(), routeDirection(placement, route),
                route.steps().size() + route.tailSteps().size(), route.tailSteps().size());
    }

    private static int routeDirection(
            SubterraneanTrapLayout.Placement placement, SubterraneanTrapPlan.EscapeRoute route) {
        List<SubterraneanTrapLayout.Cell> perimeter = expandedPerimeter(placement);
        SubterraneanTrapPlan.EscapeStep first = route.steps().getFirst();
        SubterraneanTrapPlan.EscapeStep second = route.steps().get(1);
        int firstIndex = perimeter.indexOf(new SubterraneanTrapLayout.Cell(first.x(), first.z()));
        assertTrue(firstIndex >= 0, "route starts on the expanded powder perimeter");
        SubterraneanTrapLayout.Cell clockwise = perimeter.get(Math.floorMod(firstIndex + 1, perimeter.size()));
        SubterraneanTrapLayout.Cell counterclockwise =
                perimeter.get(Math.floorMod(firstIndex - 1, perimeter.size()));
        SubterraneanTrapLayout.Cell next = new SubterraneanTrapLayout.Cell(second.x(), second.z());
        if (next.equals(clockwise)) {
            return 0;
        }
        if (next.equals(counterclockwise)) {
            return 1;
        }
        throw new AssertionError("route does not follow either deterministic perimeter direction");
    }

    private static List<SubterraneanTrapLayout.Cell> expandedPerimeter(
            SubterraneanTrapLayout.Placement placement) {
        int minX = placement.powder().stream().mapToInt(SubterraneanTrapLayout.Cell::x).min().orElseThrow() - 2;
        int maxX = placement.powder().stream().mapToInt(SubterraneanTrapLayout.Cell::x).max().orElseThrow() + 2;
        int minZ = placement.powder().stream().mapToInt(SubterraneanTrapLayout.Cell::z).min().orElseThrow() - 2;
        int maxZ = placement.powder().stream().mapToInt(SubterraneanTrapLayout.Cell::z).max().orElseThrow() + 2;
        List<SubterraneanTrapLayout.Cell> perimeter = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            perimeter.add(new SubterraneanTrapLayout.Cell(x, minZ));
        }
        for (int z = minZ + 1; z <= maxZ; z++) {
            perimeter.add(new SubterraneanTrapLayout.Cell(maxX, z));
        }
        for (int x = maxX - 1; x >= minX; x--) {
            perimeter.add(new SubterraneanTrapLayout.Cell(x, maxZ));
        }
        for (int z = maxZ - 1; z > minZ; z--) {
            perimeter.add(new SubterraneanTrapLayout.Cell(minX, z));
        }
        return List.copyOf(perimeter);
    }

    private static int cardinalDistance(int firstX, int firstZ, int secondX, int secondZ) {
        return Math.abs(firstX - secondX) + Math.abs(firstZ - secondZ);
    }

    private record AlternativeOrder(
            int doorwayX, int doorwayZ, int direction, int routeLength, int tailLength) {
        private static final java.util.Comparator<AlternativeOrder> COMPARATOR =
                java.util.Comparator.comparingInt(AlternativeOrder::doorwayX)
                        .thenComparingInt(AlternativeOrder::doorwayZ)
                        .thenComparingInt(AlternativeOrder::direction)
                        .thenComparingInt(AlternativeOrder::routeLength)
                        .thenComparingInt(AlternativeOrder::tailLength);
    }

    private static void assertRejected(SubterraneanTrapPlan.Result result, SubterraneanTrapPlan.Rejection rejection) {
        assertFalse(result.isAccepted());
        assertNull(result.accepted());
        assertEquals(rejection, result.rejection());
    }

    private static int[][] firstAir(int value) {
        int[][] heights = new int[16][16];
        for (int x = 0; x < 16; x++) {
            java.util.Arrays.fill(heights[x], value);
        }
        return heights;
    }

    private static int[][] fullSnow() {
        int[][] kinds = new int[16][16];
        for (int x = 0; x < 16; x++) {
            java.util.Arrays.fill(kinds[x], SubterraneanTrapPlan.FULL_SNOW);
        }
        return kinds;
    }

    private static int writesAt(List<SubterraneanTrapPlan.Write> writes, int x, int y, int z,
                                SubterraneanTrapPlan.Phase phase) {
        return (int) writes.stream().filter(write -> write.x() == x && write.y() == y && write.z() == z
                && write.phase() == phase).count();
    }

    private static int writesAtAnyPhase(List<SubterraneanTrapPlan.Write> writes, int x, int y, int z) {
        return (int) writes.stream().filter(write -> write.x() == x && write.y() == y && write.z() == z).count();
    }

    private static int firstPhaseIndex(List<SubterraneanTrapPlan.Write> writes, String phase) {
        for (int index = 0; index < writes.size(); index++) {
            if (writes.get(index).phase().name().equals(phase)) {
                return index;
            }
        }
        throw new AssertionError("missing planned phase " + phase);
    }

    private static int indexOf(List<SubterraneanTrapPlan.Write> writes, SubterraneanTrapPlan.Write target) {
        int index = writes.indexOf(target);
        if (index < 0) {
            throw new AssertionError("planned write is absent from its own immutable write list");
        }
        return index;
    }

    private static int phaseOrder(String phase) {
        return switch (phase) {
            case "CUSHION_BASE" -> 0;
            case "CUSHION" -> 1;
            case "ESCAPE_FLOOR" -> 2;
            case "ESCAPE_MINE_TAIL" -> 3;
            case "CLEAR" -> 4;
            case "ESCAPE_CLEAR" -> 5;
            case "SURFACE_POWDER" -> 6;
            case "REMOVE_SURFACE_LAYER" -> 7;
            default -> throw new AssertionError("unclassified planned phase " + phase);
        };
    }

    private static SubterraneanTrapPlan.Phase namedPhase(String name) {
        return java.util.Arrays.stream(SubterraneanTrapPlan.Phase.values()).filter(phase -> phase.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("missing explicit phase " + name));
    }

    private static Set<String> recordComponentNames(Object record) {
        return java.util.Arrays.stream(record.getClass().getRecordComponents()).map(component -> component.getName())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Object requiredComponent(Object record, String name) {
        Method accessor = java.util.Arrays.stream(record.getClass().getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == 0).findFirst().orElse(null);
        assertTrue(accessor != null, "escape metadata must expose " + name);
        try {
            return accessor.invoke(record);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("escape metadata accessor " + name + " must be invocable", exception);
        }
    }

    private static List<?> requiredList(Object record, String name) {
        Object value = requiredComponent(record, name);
        assertTrue(value instanceof List<?>, name + " is ordered scanner-readable metadata");
        return (List<?>) value;
    }

    private static int requiredInt(Object record, String name) {
        Object value = requiredComponent(record, name);
        assertTrue(value instanceof Integer, name + " is an exact integer coordinate");
        return (Integer) value;
    }

    private static SubterraneanTrapLayout.Cell lateralVisualCollarCell() {
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(PLACEMENT.powder());
        int minStation = powder.stream().mapToInt(SubterraneanTrapPlanTest::station).min().orElseThrow();
        int maxStation = powder.stream().mapToInt(SubterraneanTrapPlanTest::station).max().orElseThrow();
        return PLACEMENT.solid().stream().filter(cell -> station(cell) > minStation && station(cell) < maxStation)
                .filter(cell -> powder.stream().anyMatch(powderCell -> Math.abs(powderCell.x() - cell.x())
                        + Math.abs(powderCell.z() - cell.z()) == 1)).findFirst().orElseThrow();
    }

    private static SubterraneanTrapLayout.Placement tooShortEscapePlacement() {
        List<SubterraneanTrapLayout.Cell> powder = List.of(new SubterraneanTrapLayout.Cell(2, 1));
        List<SubterraneanTrapLayout.Cell> solid = new java.util.ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                SubterraneanTrapLayout.Cell cell = new SubterraneanTrapLayout.Cell(x, z);
                if (!powder.contains(cell)) {
                    solid.add(cell);
                }
            }
        }
        return new SubterraneanTrapLayout.Placement(new SubterraneanTrapLayout.Template(
                SubterraneanTrapLayout.Axis.X, 5, 3, powder, solid), 1, 1);
    }

    private static SubterraneanTrapLayout.Cell remoteOldRectangleCell() {
        int minX = PLACEMENT.implied().stream().mapToInt(SubterraneanTrapLayout.Cell::x).min().orElseThrow();
        int minZ = PLACEMENT.implied().stream().mapToInt(SubterraneanTrapLayout.Cell::z).min().orElseThrow();
        return new SubterraneanTrapLayout.Cell(minX - 1, minZ - 1);
    }

    private static SubterraneanTrapLayout.Cell approachCell(int distanceFromPowder) {
        int lastPowderStation = PLACEMENT.powder().stream().mapToInt(SubterraneanTrapPlanTest::station).max().orElseThrow();
        SubterraneanTrapLayout.Cell endpoint = PLACEMENT.powder().stream()
                .filter(cell -> station(cell) == lastPowderStation).findFirst().orElseThrow();
        return PLACEMENT.template().axis() == SubterraneanTrapLayout.Axis.X
                ? new SubterraneanTrapLayout.Cell(endpoint.x() + distanceFromPowder, endpoint.z())
                : new SubterraneanTrapLayout.Cell(endpoint.x(), endpoint.z() + distanceFromPowder);
    }

    private static int station(SubterraneanTrapLayout.Cell cell) {
        return PLACEMENT.template().axis() == SubterraneanTrapLayout.Axis.X ? cell.x() : cell.z();
    }
}
