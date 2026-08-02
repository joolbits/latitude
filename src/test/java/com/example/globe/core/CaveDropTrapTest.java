package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for the S44 {@link CaveDropTrap} law (Peetsa 2026-07-25: "traps inside caves that drop
 * the player down to a deeper layer of the cave"). Pins the walkability/solid-floor/drop thresholds, the
 * fraction gate's half-open interval, and the one-floor-panel patch join rule.
 */
class CaveDropTrapTest {

    @Test
    void cellNeedsWalkableGalleryAndCertifiedSolidFloorMass() {
        assertTrue(CaveDropTrap.cellQualifies(2, 16, false),
                "the minimal legal entrance has headroom and sixteen safe natural floor blocks");
        assertTrue(CaveDropTrap.cellQualifies(10, 24, false),
                "a roomy gallery over deeper certified floor mass remains eligible");
        assertFalse(CaveDropTrap.cellQualifies(1, 16, false),
                "one air block above is not walking clearance");
        assertFalse(CaveDropTrap.cellQualifies(2, 15, false),
                "the authored shaft, cushion, and exit need the full certified depth");
        assertFalse(CaveDropTrap.cellQualifies(2, 16, true),
                "a thin overhang over any pre-existing void is the rejected old trap grammar");
    }

    @Test
    void embeddedSolidFloorPlansOrderedShaftCushionAndDryExit() {
        CaveDropTrap.EmbeddedDropPlan plan =
                CaveDropTrap.planEmbeddedDrop(2, 16, false, 40, 10);
        assertNotNull(plan);
        assertEquals(30, plan.landingY());
        assertEquals(List.of(39, 38, 37, 36, 35, 34, 33, 32, 31),
                plan.orderedShaftAirY(),
                "the shaft is every block below the entrance, top-down, excluding its cushion");
        assertEquals(30, plan.cushionY());
        assertEquals(41, plan.dryExitStandingY(),
                "the authored stair rejoins the original cave above its solid floor");

        assertNull(CaveDropTrap.planEmbeddedDrop(2, 16, true, 40, 10),
                "the identical geometry over a pre-existing void is the rejected old overhang");
        assertNull(CaveDropTrap.planEmbeddedDrop(2, 16, false, 40, 9),
                "a shaft shorter than the ten-block owner law cannot be authored");
    }

    @Test
    void embeddedRibbonHasIrregularPowderFirmRailsBookendsAndOneSharedExit() {
        CaveDropTrap.EmbeddedRibbonPlan plan =
                CaveDropTrap.planEmbeddedRibbon(
                        40,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        ignored -> true,
                        ignored -> true);
        assertNotNull(plan);
        assertEquals(30, plan.landingY());
        assertEquals(8, plan.powderCells().size(),
                "the fixed inner ribbon has eight active fall cells");
        assertTrue(plan.powderCells().size() >= CaveDropTrap.MIN_PATCH_AREA);
        assertFourConnected(plan.powderCells(),
                "the active powder is one connected stumble ribbon");
        assertFourConnected(plan.firmRailCells(),
                "firm lateral rails and bookends form one connected upper/lower spine");
        int minX = plan.powderCells().stream()
                .mapToInt(CaveDropTrap.Cell::x).min().orElseThrow();
        int maxX = plan.powderCells().stream()
                .mapToInt(CaveDropTrap.Cell::x).max().orElseThrow();
        int minZ = plan.powderCells().stream()
                .mapToInt(CaveDropTrap.Cell::z).min().orElseThrow();
        int maxZ = plan.powderCells().stream()
                .mapToInt(CaveDropTrap.Cell::z).max().orElseThrow();
        assertTrue(plan.powderCells().size()
                        < (maxX - minX + 1) * (maxZ - minZ + 1),
                "the ribbon is visibly irregular, not a rectangular white panel");
        assertTrue(plan.firmRailCells().containsAll(plan.firstApproachBank()));
        assertTrue(plan.firmRailCells().containsAll(plan.secondApproachBank()));
        assertTrue(plan.firstApproachBank().size() >= CaveDropTrap.MIN_APPROACH_BANK_RUN);
        assertTrue(plan.secondApproachBank().size() >= CaveDropTrap.MIN_APPROACH_BANK_RUN);
        assertFourConnected(plan.firstApproachBank(),
                "the first approach bank is one transverse run");
        assertFourConnected(plan.secondApproachBank(),
                "the second approach bank is one transverse run");
        Set<Integer> firstProjection = new HashSet<>();
        plan.firstApproachBank().forEach(cell -> firstProjection.add(cell.x()));
        Set<Integer> secondProjection = new HashSet<>();
        plan.secondApproachBank().forEach(cell -> secondProjection.add(cell.x()));
        firstProjection.retainAll(secondProjection);
        assertTrue(firstProjection.size() >= CaveDropTrap.MIN_APPROACH_BANK_RUN,
                "the opposed north/south bank runs overlap across the ribbon width");
        for (CaveDropTrap.Cell bank : plan.firstApproachBank()) {
            assertTrue(plan.powderCells().contains(
                            new CaveDropTrap.Cell(bank.x(), bank.z() + 1)),
                    "every first-bank cell directly borders powder on its inward side");
        }
        for (CaveDropTrap.Cell bank : plan.secondApproachBank()) {
            assertTrue(plan.powderCells().contains(
                            new CaveDropTrap.Cell(bank.x(), bank.z() - 1)),
                    "every second-bank cell directly borders powder on its inward side");
        }
        assertEquals(CaveDropTrap.MIN_APPROACH_CONTINUATION_AREA,
                plan.firstNaturalContinuation().size());
        assertEquals(CaveDropTrap.MIN_APPROACH_CONTINUATION_AREA,
                plan.secondNaturalContinuation().size());
        Set<CaveDropTrap.Cell> authoredUpper = new HashSet<>(plan.powderCells());
        authoredUpper.addAll(plan.firmRailCells());
        authoredUpper.addAll(plan.stairCells());
        for (CaveDropTrap.Cell continuation : plan.firstNaturalContinuation()) {
            assertFalse(authoredUpper.contains(continuation),
                    "first continuation stays untouched natural cave floor");
        }
        for (CaveDropTrap.Cell continuation : plan.secondNaturalContinuation()) {
            assertFalse(authoredUpper.contains(continuation),
                    "second continuation stays untouched natural cave floor");
        }
        assertTrue(plan.upperExitBankCells().contains(
                plan.upperReturnPathCells().getFirst()));
        assertTrue(plan.secondNaturalContinuation().contains(
                plan.upperReturnPathCells().getLast()));
        assertFourConnected(plan.upperReturnPathCells(),
                "the untouched upper return joins the stair pocket to natural cave floor");
        for (CaveDropTrap.Cell returnCell : plan.upperReturnPathCells()) {
            assertFalse(authoredUpper.contains(returnCell),
                    "the upper return is certified existing cave floor, never authored");
        }
        assertEquals(plan.powderCells().size(), plan.entries().size());
        assertEquals(plan.powderCells().size(), plan.dryRoutes().size());
        for (int index = 0; index < plan.entries().size(); index++) {
            CaveDropTrap.RibbonEntry entry = plan.entries().get(index);
            assertEquals(1,
                    Math.abs(entry.powder().x() - entry.corridor().x())
                            + Math.abs(entry.powder().z() - entry.corridor().z()),
                    "every cushion is one horizontal move from the shared dry corridor");
            assertTrue(plan.lowerCorridorCells().contains(entry.corridor()));
            List<CaveDropTrap.Voxel> route = plan.dryRoutes().get(index);
            assertEquals(new CaveDropTrap.Voxel(
                            entry.powder().x(), plan.landingY(), entry.powder().z()),
                    route.getFirst());
            CaveDropTrap.Cell stairExit = plan.stairCells().getLast();
            assertTrue(route.contains(new CaveDropTrap.Voxel(
                            stairExit.x(), plan.floorY() + 1, stairExit.z())),
                    "every lower route reaches the top of the authored stair");
            assertEquals(plan.upperReturnPathCells().getLast().x(), route.getLast().x());
            assertEquals(plan.floorY() + 1, route.getLast().y(),
                    "every route continues through untouched floor to the original gallery");
            assertEquals(plan.upperReturnPathCells().getLast().z(), route.getLast().z());
        }
    }

    @Test
    void embeddedRibbonRejectsThinRoofAndIncompleteSolidEnvelope() {
        int floorY = 40;
        CaveDropTrap.Voxel hiddenVoid =
                new CaveDropTrap.Voxel(-1, floorY - 2, -5);
        assertNull(CaveDropTrap.planEmbeddedRibbon(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        voxel -> !voxel.equals(hiddenVoid),
                        ignored -> true),
                "a legal-looking cave floor over a pre-existing void is still rejected");

        CaveDropTrap.Voxel missingStairSupport =
                new CaveDropTrap.Voxel(4, 31, 1);
        assertNull(CaveDropTrap.planEmbeddedRibbon(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        voxel -> !voxel.equals(missingStairSupport),
                        ignored -> true),
                "one unsafe support in the authored stair envelope rejects the complete template");
    }

    @Test
    void embeddedRibbonRotationsPreserveTheNamedApproachBankLaw() {
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            CaveDropTrap.EmbeddedRibbonPlan plan =
                    CaveDropTrap.planEmbeddedRibbon(
                            40, orientation, ignored -> true, ignored -> true);
            assertNotNull(plan, orientation + " must retain one complete template");
            int firstDx =
                    plan.approachAxis() == CaveDropTrap.ApproachAxis.EAST_WEST ? 1 : 0;
            int firstDz =
                    plan.approachAxis() == CaveDropTrap.ApproachAxis.NORTH_SOUTH ? 1 : 0;
            for (CaveDropTrap.Cell bank : plan.firstApproachBank()) {
                assertTrue(plan.powderCells().contains(new CaveDropTrap.Cell(
                                bank.x() + firstDx, bank.z() + firstDz)),
                        orientation + " first bank must border powder inward");
            }
            for (CaveDropTrap.Cell bank : plan.secondApproachBank()) {
                assertTrue(plan.powderCells().contains(new CaveDropTrap.Cell(
                                bank.x() - firstDx, bank.z() - firstDz)),
                        orientation + " second bank must border powder inward");
            }
            Set<Integer> firstProjection = new HashSet<>();
            Set<Integer> secondProjection = new HashSet<>();
            if (plan.approachAxis() == CaveDropTrap.ApproachAxis.NORTH_SOUTH) {
                plan.firstApproachBank().forEach(cell -> firstProjection.add(cell.x()));
                plan.secondApproachBank().forEach(cell -> secondProjection.add(cell.x()));
            } else {
                plan.firstApproachBank().forEach(cell -> firstProjection.add(cell.z()));
                plan.secondApproachBank().forEach(cell -> secondProjection.add(cell.z()));
            }
            firstProjection.retainAll(secondProjection);
            assertTrue(firstProjection.size() >= CaveDropTrap.MIN_APPROACH_BANK_RUN,
                    orientation + " opposed bank projections must overlap");
            CaveDropTrap.Cell routeExit = plan.upperReturnPathCells().getLast();
            assertTrue(plan.firstNaturalContinuation().contains(routeExit)
                            || plan.secondNaturalContinuation().contains(routeExit),
                    orientation + " upper return must end on certified natural cave floor");
        }
    }

    @Test
    void completeRibbonFootprintNeedsMaintainedMultiColumnCaveEnclosure() {
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            CaveDropTrap.EmbeddedRibbonPlan plan = CaveDropTrap.planEmbeddedRibbon(
                    40, orientation, ignored -> true, ignored -> true);
            assertNotNull(plan);
            assertEquals(
                    plan.enclosureColumns().size(),
                    new HashSet<>(plan.enclosureColumns()).size(),
                    orientation + " enclosure columns are distinct");

            Set<CaveDropTrap.Cell> requiredColumns = new HashSet<>();
            plan.requiredSolidVoxels().forEach(
                    voxel -> requiredColumns.add(new CaveDropTrap.Cell(voxel.x(), voxel.z())));
            plan.requiredAirVoxels().forEach(
                    voxel -> requiredColumns.add(new CaveDropTrap.Cell(voxel.x(), voxel.z())));
            assertTrue(plan.enclosureColumns().containsAll(requiredColumns),
                    orientation + " complete solid/air contract footprint is enclosed");

            assertTrue(CaveDropTrap.caveEnclosureQualifies(
                    plan.floorY(),
                    plan.enclosureColumns(),
                    ignored -> plan.floorY() + CaveDropTrap.MIN_CAVE_ENCLOSURE_RISE));
            assertFalse(CaveDropTrap.caveEnclosureQualifies(
                    plan.floorY(),
                    plan.enclosureColumns(),
                    ignored -> plan.floorY() + 1),
                    "an exposed snowfield never becomes an inner-cave carpet");

            CaveDropTrap.Cell spire = plan.enclosureColumns().getFirst();
            assertFalse(CaveDropTrap.caveEnclosureQualifies(
                    plan.floorY(),
                    plan.enclosureColumns(),
                    cell -> cell.equals(spire) ? plan.floorY() + 20 : plan.floorY() + 1),
                    "one tall ice-spire column cannot rescue an exposed footprint");

            CaveDropTrap.Cell exposed = plan.enclosureColumns().getLast();
            assertFalse(CaveDropTrap.caveEnclosureQualifies(
                    plan.floorY(),
                    plan.enclosureColumns(),
                    cell -> cell.equals(exposed)
                            ? plan.floorY() + CaveDropTrap.MIN_CAVE_ENCLOSURE_RISE - 1
                            : plan.floorY() + CaveDropTrap.MIN_CAVE_ENCLOSURE_RISE),
                    "one exposed contract column fails the complete enclosure law");
        }
    }

    @Test
    void naturalFloorEvidenceEnvelopeProtectsSupportStandingAndHeadButNotWholeColumn() {
        CaveDropTrap.Voxel floor = new CaveDropTrap.Voxel(4, 40, 7);
        Set<CaveDropTrap.Voxel> protectedEvidence =
                CaveDropTrap.naturalFloorEvidenceEnvelope(List.of(floor));
        assertEquals(6, protectedEvidence.size());
        for (int y = 37; y <= 42; y++) {
            assertTrue(protectedEvidence.contains(new CaveDropTrap.Voxel(4, y, 7)));
        }
        assertFalse(CaveDropTrap.escapeStandingPreservesEvidence(
                new CaveDropTrap.Voxel(4, 38, 7), protectedEvidence),
                "the escape cannot use named natural support as its own support/standing/head");
        assertFalse(CaveDropTrap.escapeStandingPreservesEvidence(
                new CaveDropTrap.Voxel(4, 41, 7), protectedEvidence),
                "the escape cannot occupy the named standing/headroom evidence");
        assertTrue(CaveDropTrap.escapeStandingPreservesEvidence(
                new CaveDropTrap.Voxel(4, 20, 7), protectedEvidence),
                "the same horizontal column remains usable far below the exact 3D evidence");
    }

    @Test
    void prospectorLayoutRotatesAndUsesOnlyTheAuthoredLowerNetwork() {
        CaveDropTrap.ProspectorLayout canonical = new CaveDropTrap.ProspectorLayout(
                new CaveDropTrap.Cell(-2, 0),
                new CaveDropTrap.Cell(-3, 0),
                new CaveDropTrap.Cell(-3, 1),
                new CaveDropTrap.Cell(-2, 1),
                new CaveDropTrap.Cell(-1, 1));
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            CaveDropTrap.EmbeddedRibbonPlan plan = CaveDropTrap.planEmbeddedRibbon(
                    40, orientation, ignored -> true, ignored -> true);
            assertNotNull(plan);
            CaveDropTrap.ProspectorLayout layout = plan.prospectorLayout();
            assertEquals(orient(canonical, orientation), layout);
            assertTrue(plan.powderCells().contains(layout.cushion()));
            assertTrue(plan.lowerCorridorCells().containsAll(List.of(
                    layout.foot(), layout.head(), layout.chest(), layout.front())));

            int entryDx = layout.foot().x() - layout.cushion().x();
            int entryDz = layout.foot().z() - layout.cushion().z();
            int bodyDx = layout.head().x() - layout.foot().x();
            int bodyDz = layout.head().z() - layout.foot().z();
            int chestDx = layout.front().x() - layout.chest().x();
            int chestDz = layout.front().z() - layout.chest().z();
            assertEquals(0, entryDx * bodyDx + entryDz * bodyDz,
                    orientation + " entry and body directions are independently perpendicular");
            assertEquals(0, bodyDx * chestDx + bodyDz * chestDz,
                    orientation + " body and chest directions are independently perpendicular");

            Set<CaveDropTrap.Cell> allowed = new HashSet<>(plan.powderCells());
            allowed.addAll(plan.lowerCorridorCells());
            allowed.removeAll(List.of(layout.foot(), layout.head(), layout.chest()));
            for (CaveDropTrap.Cell cushion : plan.powderCells()) {
                assertTrue(reachable(cushion, layout.front(), allowed),
                        orientation + " every cushion reaches the scene front using authored cells only");
            }
            assertTrue(reachable(layout.front(), plan.stairCells().getFirst(), allowed),
                    orientation + " scene front reaches the authored stair");

            Set<CaveDropTrap.Cell> cushionsBlocked = new HashSet<>(allowed);
            cushionsBlocked.removeAll(plan.powderCells());
            assertFalse(reachable(layout.cushion(), layout.front(), cushionsBlocked),
                    "blocking cushions reproduces the old contradictory route contract");
        }
    }

    @Test
    void embeddedRibbonRejectsEitherMissingNaturalApproachContinuation() {
        int floorY = 40;
        CaveDropTrap.EmbeddedRibbonPlan complete =
                CaveDropTrap.planEmbeddedRibbon(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        ignored -> true,
                        ignored -> true);
        assertNotNull(complete);

        CaveDropTrap.Cell first = complete.firstNaturalContinuation().getFirst();
        CaveDropTrap.Voxel missingFirstSupport =
                new CaveDropTrap.Voxel(first.x(), floorY - 1, first.z());
        assertNull(CaveDropTrap.planEmbeddedRibbon(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        voxel -> !voxel.equals(missingFirstSupport),
                        ignored -> true),
                "the template cannot terminate at a thin first-side ledge");

        CaveDropTrap.Cell second = complete.secondNaturalContinuation().getFirst();
        CaveDropTrap.Voxel blockedSecondHeadroom =
                new CaveDropTrap.Voxel(second.x(), floorY + 2, second.z());
        assertNull(CaveDropTrap.planEmbeddedRibbon(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        ignored -> true,
                        voxel -> !voxel.equals(blockedSecondHeadroom)),
                "the template cannot terminate at a blocked second-side pocket");

        CaveDropTrap.Cell returnCell =
                complete.upperReturnPathCells()
                        .get(complete.upperReturnPathCells().size() / 2);
        CaveDropTrap.Voxel blockedUpperReturn =
                new CaveDropTrap.Voxel(returnCell.x(), floorY + 1, returnCell.z());
        assertNull(CaveDropTrap.planEmbeddedRibbon(
                        floorY,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        ignored -> true,
                        voxel -> !voxel.equals(blockedUpperReturn)),
                "a sealed upper stair pocket cannot masquerade as an exit to the original gallery");
    }

    @Test
    void embeddedRibbonProjectedUnionKeepsEveryCorridorAndStairSupport() {
        CaveDropTrap.EmbeddedRibbonPlan plan =
                CaveDropTrap.planEmbeddedRibbon(
                        40,
                        CaveDropTrap.RibbonOrientation.NORTH,
                        ignored -> true,
                        ignored -> true);
        assertNotNull(plan);

        Set<CaveDropTrap.Voxel> projectedAir =
                new HashSet<>(plan.requiredAirVoxels());
        for (CaveDropTrap.Cell powder : plan.powderCells()) {
            for (int y = plan.floorY() - 1; y > plan.landingY(); y--) {
                projectedAir.add(new CaveDropTrap.Voxel(powder.x(), y, powder.z()));
            }
        }
        for (CaveDropTrap.Cell corridor : plan.lowerCorridorCells()) {
            projectedAir.add(new CaveDropTrap.Voxel(
                    corridor.x(), plan.landingY(), corridor.z()));
            projectedAir.add(new CaveDropTrap.Voxel(
                    corridor.x(), plan.landingY() + 1, corridor.z()));
        }
        for (int index = 1; index < plan.stairCells().size() - 1; index++) {
            CaveDropTrap.Cell stair = plan.stairCells().get(index);
            int standingY = plan.landingY() + index;
            projectedAir.add(new CaveDropTrap.Voxel(stair.x(), standingY, stair.z()));
            projectedAir.add(new CaveDropTrap.Voxel(stair.x(), standingY + 1, stair.z()));
        }
        Set<CaveDropTrap.Voxel> projectedSupport =
                new HashSet<>(plan.requiredSolidVoxels());
        projectedSupport.removeAll(projectedAir);
        assertTrue(CaveDropTrap.dryRoutesRemainSupported(
                        plan.dryRoutes(),
                        projectedAir::contains,
                        projectedSupport::contains),
                "the complete corridor, shaft, and stair write union leaves every route walkable");

        CaveDropTrap.Voxel clearedSupport =
                plan.dryRoutes().getFirst().getLast().below();
        projectedAir.add(clearedSupport);
        projectedSupport.remove(clearedSupport);
        assertFalse(CaveDropTrap.dryRoutesRemainSupported(
                        plan.dryRoutes(),
                        projectedAir::contains,
                        projectedSupport::contains),
                "clearing even the named exit support is rejected by the final projected-state law");
    }

    @Test
    void combinedProjectionRejectsBranchThatClearsAnotherRoutesStairSupport() {
        CaveDropTrap.Voxel cushion = new CaveDropTrap.Voxel(-1, 0, 0);
        CaveDropTrap.Voxel stair = new CaveDropTrap.Voxel(0, 1, 0);
        CaveDropTrap.Voxel stairSupport = stair.below();
        List<List<CaveDropTrap.Voxel>> routes = List.of(List.of(cushion, stair));
        Set<CaveDropTrap.Voxel> originalSolid = Set.of(stairSupport);

        Set<CaveDropTrap.Voxel> overlappingClears =
                Set.of(stairSupport, stair, stair.above());
        assertFalse(CaveDropTrap.dryRoutesRemainSupported(
                        routes,
                        overlappingClears::contains,
                        cell -> originalSolid.contains(cell)
                                && !overlappingClears.contains(cell)),
                "RED: a landing branch may not clear the block another route uses as stair support");

        Set<CaveDropTrap.Voxel> repairedClears = Set.of(
                new CaveDropTrap.Voxel(1, 0, 0), stair, stair.above());
        assertTrue(CaveDropTrap.dryRoutesRemainSupported(
                        routes,
                        repairedClears::contains,
                        cell -> originalSolid.contains(cell)
                                && !repairedClears.contains(cell)),
                "the same stair is valid when the landing branch opens beside, not through, its support");
    }

    @Test
    void certifiedReservedBranchesGiveEveryConnectedPowderEntryAnExit() {
        List<CaveDropTrap.Cell> certified = new ArrayList<>();
        for (int x = 3; x <= 9; x++) {
            certified.add(new CaveDropTrap.Cell(x, 3));
        }
        certified.add(new CaveDropTrap.Cell(3, 4));
        certified.add(new CaveDropTrap.Cell(3, 5));

        List<CaveDropTrap.Cell> intendedPowder = new ArrayList<>();
        for (int x = 4; x <= 9; x++) {
            intendedPowder.add(new CaveDropTrap.Cell(x, 4));
        }
        intendedPowder.add(new CaveDropTrap.Cell(4, 5));

        List<CaveDropTrap.Cell> carpet = new ArrayList<>(certified);
        carpet.addAll(intendedPowder);
        assertNull(CaveDropTrap.planCertifiedBranches(
                        carpet, List.of(new CaveDropTrap.Cell(10, 4))),
                "RED: an arbitrary adjacent column outside the certified carpet cannot become a lower branch");

        CaveDropTrap.CertifiedBranchPlan plan =
                CaveDropTrap.planCertifiedBranches(carpet, certified);
        assertNotNull(plan,
                "certified solid-depth cells can be reserved into a shared lower dry branch");
        assertTrue(plan.activePowderCells().size() >= CaveDropTrap.MIN_PATCH_AREA);
        assertFourConnected(plan.activePowderCells(),
                "the retained powder remains one stumble carpet");
        assertFourConnected(plan.reservedBranchCells(),
                "all lower branches join one shared dry gallery");
        int minX = plan.activePowderCells().stream()
                .mapToInt(CaveDropTrap.Cell::x).min().orElseThrow();
        int maxX = plan.activePowderCells().stream()
                .mapToInt(CaveDropTrap.Cell::x).max().orElseThrow();
        int minZ = plan.activePowderCells().stream()
                .mapToInt(CaveDropTrap.Cell::z).min().orElseThrow();
        int maxZ = plan.activePowderCells().stream()
                .mapToInt(CaveDropTrap.Cell::z).max().orElseThrow();
        assertTrue(plan.activePowderCells().size()
                        < (maxX - minX + 1) * (maxZ - minZ + 1),
                "the active powder remains irregular after certified branches are demoted");
        assertEquals(plan.activePowderCells().size(), plan.witnesses().size(),
                "every retained powder entry has one explicit branch witness");
        assertTrue(certified.containsAll(plan.reservedBranchCells()),
                "only explicitly certified cells may be demoted into branch columns");
        assertDisjoint(plan.activePowderCells(), plan.reservedBranchCells(),
                "a cell cannot be both a powder shaft and its dry branch");
        assertEquals(new HashSet<>(carpet),
                union(plan.activePowderCells(), plan.reservedBranchCells()),
                "the branch grammar partitions the original carpet without inventing cells");
        Set<CaveDropTrap.Cell> witnessedEntries = new HashSet<>();
        for (CaveDropTrap.BranchWitness witness : plan.witnesses()) {
            assertTrue(witnessedEntries.add(witness.entry()),
                    "an entry receives exactly one stable witness");
            assertTrue(plan.activePowderCells().contains(witness.entry()));
            assertTrue(plan.reservedBranchCells().contains(witness.branch()));
            assertEquals(1,
                    Math.abs(witness.entry().x() - witness.branch().x())
                            + Math.abs(witness.entry().z() - witness.branch().z()),
                    "the dry branch begins one horizontal step from the powder cushion");
        }
    }

    @Test
    void activeApproachFloorWinsWhenOneColumnContainsTwoEligibleCaveFloors() {
        int activeApproachFloorY = 40;
        Integer selectedFloorY = CaveDropTrap.selectApproachFloor(
                List.of(70, activeApproachFloorY), activeApproachFloorY);
        assertEquals(activeApproachFloorY, selectedFloorY,
                "the higher cave floor may not win from vertical scan order");

        CaveDropTrap.EmbeddedDropPlan plan = CaveDropTrap.planEmbeddedDrop(
                2, CaveDropTrap.MIN_CERTIFIED_SOLID_DEPTH, false,
                selectedFloorY, CaveDropTrap.MIN_DROP_AIR);
        assertNotNull(plan);
        assertEquals(40, plan.floorY(), "the entrance remains on the active carpet floor");
        assertEquals(30, plan.landingY(), "the landing is derived from that active entrance");
        assertEquals(39, plan.orderedShaftAirY().getFirst(),
                "the opened shaft begins immediately below the selected active floor");

        assertEquals(41, CaveDropTrap.selectApproachFloor(
                        List.of(70, 41), activeApproachFloorY),
                "a one-block undulation remains part of the same active carpet");
        assertNull(CaveDropTrap.selectApproachFloor(
                        List.of(70, 12), activeApproachFloorY),
                "unrelated cave floors in the same column cannot materialize this carpet");
    }

    @Test
    void thresholdsAreTheS50CarpetContract() {
        assertEquals(2, CaveDropTrap.MIN_GALLERY_AIR);
        assertEquals(10, CaveDropTrap.MIN_DROP_AIR,
                "S50 owner spec: \"falling through down like at least 10 blocks down\"");
        assertEquals(13, CaveDropTrap.MAX_ORDINARY_DROP_AIR,
                "one-block cave-floor undulation is allowed without unbounding the authored descent");
        assertEquals(16, CaveDropTrap.MIN_CERTIFIED_SOLID_DEPTH,
                "the embedded entrance certifies enough untouched natural floor for the whole descent");
        assertEquals(6, CaveDropTrap.MIN_PATCH_AREA,
                "S50: single blocks and slivers never trap (owner: \"they are just single blocks right now\")");
        assertEquals(40, CaveDropTrap.PATCH_MAX_AREA,
                "S50: a carpet to stumble into -- surface-class breadth, never a floor deletion");
        assertEquals(4, CaveDropTrap.MIN_FIRM_CAMOUFLAGE_CELLS,
                "the inner entrance is a mixed snow patch, never a conspicuous all-powder panel");
        assertEquals(2, CaveDropTrap.MAX_POWDER_CELLS_PER_FIRM_CAMOUFLAGE,
                "at least one third of the combined authored cover is supported firm camouflage");
        assertEquals(2, CaveDropTrap.MIN_APPROACH_BANK_RUN,
                "two isolated edge pixels are not a walk-through approach");
        assertEquals(4, CaveDropTrap.MIN_APPROACH_CONTINUATION_AREA,
                "each bank must continue into a larger stable cave floor");
        assertTrue(CaveDropTrap.MIN_PATCH_AREA < CaveDropTrap.PATCH_MAX_AREA, "a real window");
    }

    @Test
    void deepFloodedRewardPlansAReachableBelowZeroThirtyTwoBlockFall() {
        CaveDropTrap.DeepFloodedLanding highGallery =
                CaveDropTrap.planDeepFloodedLanding(48, 49, -64);
        assertNotNull(highGallery);
        assertEquals(-1, highGallery.surfaceY(),
                "a high cave may fall all the way to the highest legal sub-Y0 pool");
        assertEquals(49, highGallery.fallMin());
        assertEquals(50, highGallery.fallMax());

        CaveDropTrap.DeepFloodedLanding lowGallery =
                CaveDropTrap.planDeepFloodedLanding(20, 21, -64);
        assertNotNull(lowGallery);
        assertEquals(-12, lowGallery.surfaceY(),
                "a lower cave still receives the full thirty-two-block reward fall");
        assertEquals(32, lowGallery.fallMin());
        assertEquals(33, lowGallery.fallMax());

        assertNull(CaveDropTrap.planDeepFloodedLanding(20, 21, -14),
                "the three-deep pool and its floor may never collide with the build floor");
        assertNull(CaveDropTrap.planDeepFloodedLanding(20, 117, -64),
                "a carpet whose entrances would exceed the ninety-six-block cap is rejected");
    }

    @Test
    void fractionGateIsHalfOpenAndSafeOutOfRange() {
        assertEquals(0.85f, CaveDropTrap.TRAP_FRACTION, 1e-6f,
                "owner 2026-08-01: raise the rate once the compact shape is fixed -- the "
                        + "conformal throat placed only ~6 traps per 256 chunks at 0.50");
        assertEquals(4, CaveDropTrap.MAX_PATCHES_PER_CHUNK,
                "feasible throats cluster, so the per-chunk cap was the binding limit");
        assertTrue(CaveDropTrap.shouldTrapPatch(0.0f));
        assertTrue(CaveDropTrap.shouldTrapPatch(0.84f));
        assertFalse(CaveDropTrap.shouldTrapPatch(0.85f), "exactly the fraction does NOT trap (half-open)");
        assertFalse(CaveDropTrap.shouldTrapPatch(-0.01f), "out-of-range never traps (floors stay honest)");
        assertFalse(CaveDropTrap.shouldTrapPatch(1.5f));
    }

    @Test
    void patchJoinKeepsOneCoherentFloorPanel() {
        assertTrue(CaveDropTrap.cellsJoinPatch(40, 40));
        assertTrue(CaveDropTrap.cellsJoinPatch(40, 41), "one block of floor undulation joins");
        assertFalse(CaveDropTrap.cellsJoinPatch(40, 42), "two apart is a different gallery level");
    }

    @Test
    void conformalThroatFloorsStayInTheUpperGlacialBandAboveTheDeepSlateCushionFloor() {
        assertFalse(CaveDropTrap.isConformalThroatFloor(23));
        assertTrue(CaveDropTrap.isConformalThroatFloor(24));
        assertTrue(CaveDropTrap.isConformalThroatFloor(45));
        assertFalse(CaveDropTrap.isConformalThroatFloor(46));

        CaveDropTrap.RoofedGalleryThroatPlan lowest =
                CaveDropTrap.planRoofedGalleryThroat(24, CaveDropTrap.RibbonOrientation.NORTH);
        CaveDropTrap.RoofedGalleryThroatPlan highest =
                CaveDropTrap.planRoofedGalleryThroat(45, CaveDropTrap.RibbonOrientation.NORTH);
        assertEquals(14, lowest.landingY(),
                "the locked factory plan retains its historical fixed ten-block fall");
        assertEquals(35, highest.landingY(), "the highest legal floor preserves the fixed ten-block fall");
        assertTrue(lowest.cushionSupportVoxels().stream()
                        .mapToInt(CaveDropTrap.Voxel::y)
                        .min().orElseThrow() >= 0,
                "the lowest legal throat keeps every authored cushion support at or above Y0");
    }

    @Test
    void betweenLayerDropUsesExactBandsAndChoosesTheDeepestQualifiedGallery() {
        assertFalse(CaveDropTrap.isBetweenLayerUpperFloor(23));
        assertTrue(CaveDropTrap.isBetweenLayerUpperFloor(24));
        assertTrue(CaveDropTrap.isBetweenLayerUpperFloor(45));
        assertFalse(CaveDropTrap.isBetweenLayerUpperFloor(46));

        assertFalse(CaveDropTrap.isBetweenLayerLowerFloor(-1));
        assertTrue(CaveDropTrap.isBetweenLayerLowerFloor(0));
        assertTrue(CaveDropTrap.isBetweenLayerLowerFloor(23));
        assertFalse(CaveDropTrap.isBetweenLayerLowerFloor(24));

        assertFalse(CaveDropTrap.isBetweenLayerSeparation(15));
        assertTrue(CaveDropTrap.isBetweenLayerSeparation(16));
        assertTrue(CaveDropTrap.isBetweenLayerSeparation(32));
        assertFalse(CaveDropTrap.isBetweenLayerSeparation(33));

        CaveDropTrap.BetweenLayerDropPlan selected =
                CaveDropTrap.planBetweenLayerDrop(40, List.of(25, 24, 8, 7, 23, 8));
        assertNotNull(selected);
        assertEquals(40, selected.upperFloorY());
        assertEquals(8, selected.lowerGalleryFloorY(),
                "the deepest qualifying lower gallery wins independent of input order");
        assertEquals(32, selected.verticalSeparation());
        assertEquals(
                selected,
                CaveDropTrap.planBetweenLayerDrop(40, List.of(8, 23, 7, 24, 25, 8)),
                "selection is stable under observation order and duplicate candidates");

        assertEquals(
                new CaveDropTrap.BetweenLayerDropPlan(24, 0, 24),
                CaveDropTrap.planBetweenLayerDrop(24, List.of(-1, 0, 8, 24)),
                "Y0 is a legal lower natural gallery floor");
        assertNull(CaveDropTrap.planBetweenLayerDrop(23, List.of(0, 7)),
                "an upper floor below Y24 fails closed");
        assertNull(CaveDropTrap.planBetweenLayerDrop(40, List.of(7, 24, 25)),
                "gap 33, gap 16 at an out-of-band lower floor, and gap 15 all reject");
        assertNull(CaveDropTrap.planBetweenLayerDrop(40, List.of()),
                "no qualifying natural lower gallery means no trap");
    }

    @Test
    void steppedLowerSurfaceRescuesTheExactY20Y21CaveAndPreservesAirThroughY25() {
        List<CaveDropTrap.Cell> footprint = lowerSurfaceFootprint();
        List<Integer> floorYs = List.of(20, 20, 21, 21, 21, 21);
        List<CaveDropTrap.LowerColumnObservation> observations = new ArrayList<>();
        for (int index = 0; index < footprint.size(); index++) {
            observations.add(lowerColumn(
                    footprint.get(index), floorYs.get(index), 44, 25, null, true));
            observations.add(lowerColumn(
                    footprint.get(index), 22, 44, 25, null, true));
        }

        CaveDropTrap.BetweenLayerSurfacePlan selected =
                CaveDropTrap.planBetweenLayerSurface(44, footprint, observations);
        assertNotNull(selected,
                "the representative anchor's connected Y20/Y21 floor is a legal lower interface");
        assertEquals(20, selected.minimumFloorY());
        assertEquals(21, selected.maximumFloorY());
        assertEquals(floorYs, selected.columns().stream()
                .map(CaveDropTrap.LowerColumnPlan::floorY).toList());
        assertTrue(selected.columns().stream().allMatch(column ->
                        column.plugBottomY() == 26
                                && column.preservedAirYs().getLast() == 25),
                "every complete natural air stack stays untouched and the shaft begins at plug Y26");

        List<CaveDropTrap.LowerColumnObservation> reversed = new ArrayList<>(observations);
        java.util.Collections.reverse(reversed);
        assertEquals(selected, CaveDropTrap.planBetweenLayerSurface(44, footprint, reversed),
                "observation order cannot change the deepest conformal surface");
    }

    @Test
    void steppedLowerSurfaceRejectsReliefTwoDisconnectedFootprintsMissingPlugsAndHazards() {
        List<CaveDropTrap.Cell> footprint = lowerSurfaceFootprint();
        List<Integer> reliefTwo = List.of(20, 20, 22, 22, 22, 22);
        List<CaveDropTrap.LowerColumnObservation> reliefObservations = new ArrayList<>();
        for (int index = 0; index < footprint.size(); index++) {
            reliefObservations.add(lowerColumn(
                    footprint.get(index), reliefTwo.get(index), 44, 25, null, true));
        }
        assertNull(CaveDropTrap.planBetweenLayerSurface(44, footprint, reliefObservations),
                "two blocks of total relief is not a coherent landing surface");

        List<CaveDropTrap.Cell> disconnected = new ArrayList<>(footprint);
        disconnected.set(disconnected.size() - 1, new CaveDropTrap.Cell(9, 9));
        assertNull(CaveDropTrap.planBetweenLayerSurface(
                        44, disconnected, representativeLowerColumns(disconnected, 20, 44)),
                "six columns that are not one cardinal footprint fail closed");

        List<CaveDropTrap.LowerColumnObservation> noPlug =
                representativeLowerColumns(footprint, 20, 44);
        noPlug.set(0, lowerColumn(footprint.getFirst(), 20, 44, 43, null, true));
        assertNull(CaveDropTrap.planBetweenLayerSurface(44, footprint, noPlug),
                "an air column with no solid plug below the upper floor cannot be authored");

        List<CaveDropTrap.LowerColumnObservation> hazard =
                representativeLowerColumns(footprint, 20, 44);
        hazard.set(0, lowerColumn(
                footprint.getFirst(), 20, 44, 25,
                CaveDropTrap.ThroatBlockKind.GRAVEL, true));
        assertNull(CaveDropTrap.planBetweenLayerSurface(44, footprint, hazard),
                "one hazardous block anywhere in the plug rejects the whole surface");

        List<CaveDropTrap.LowerColumnObservation> deepslatePlug =
                representativeLowerColumns(footprint, 20, 44);
        deepslatePlug.set(0, lowerColumn(
                footprint.getFirst(), 20, 44, 25,
                CaveDropTrap.ThroatBlockKind.DEEPSLATE, true));
        assertNull(CaveDropTrap.planBetweenLayerSurface(44, footprint, deepslatePlug),
                "deepslate in an authored lower shaft target rejects the whole surface");

        List<CaveDropTrap.LowerColumnObservation> thinFloor =
                representativeLowerColumns(footprint, 20, 44);
        thinFloor.set(0, lowerColumn(footprint.getFirst(), 20, 44, 25, null, false));
        assertNull(CaveDropTrap.planBetweenLayerSurface(44, footprint, thinFloor),
                "every cushion floor needs four safe natural supports");
    }

    @Test
    void lowerColumnDiagnosticsReuseTheQualifierAndNameTheExactFailedVoxel() {
        CaveDropTrap.Cell cell = lowerSurfaceFootprint().getFirst();
        CaveDropTrap.LowerColumnObservation green =
                lowerColumn(cell, 20, 44, 25, null, true);
        CaveDropTrap.LowerColumnObservation deepslateSupport =
                new CaveDropTrap.LowerColumnObservation(
                        cell,
                        green.floorY(),
                        false,
                        green.blocksAboveThroughUpper(),
                        2,
                        CaveDropTrap.ThroatBlockKind.DEEPSLATE);
        CaveDropTrap.LowerColumnAssessment support =
                CaveDropTrap.assessLowerColumn(44, deepslateSupport);
        assertFalse(support.feasible());
        assertEquals(
                CaveDropTrap.LowerColumnFailureClause.UNSAFE_SUPPORT_MASS,
                support.failureClause());
        assertEquals(18, support.failureY());
        assertEquals(CaveDropTrap.ThroatBlockKind.DEEPSLATE, support.blockKind());

        CaveDropTrap.LowerColumnObservation gravelPlug =
                lowerColumn(
                        cell,
                        20,
                        44,
                        25,
                        CaveDropTrap.ThroatBlockKind.GRAVEL,
                        true);
        CaveDropTrap.LowerColumnAssessment plug =
                CaveDropTrap.assessLowerColumn(44, gravelPlug);
        assertFalse(plug.feasible());
        assertEquals(
                CaveDropTrap.LowerColumnFailureClause.UNSAFE_NATURAL_PLUG,
                plug.failureClause());
        assertEquals(28, plug.failureY());
        assertEquals(CaveDropTrap.ThroatBlockKind.GRAVEL, plug.blockKind());
    }

    @Test
    void steppedLowerSurfaceKeepsBothGapAndFloorBoundariesInclusive() {
        List<CaveDropTrap.Cell> footprint = lowerSurfaceFootprint();
        CaveDropTrap.BetweenLayerSurfacePlan yZero = CaveDropTrap.planBetweenLayerSurface(
                24, footprint, representativeLowerColumns(footprint, 0, 24));
        assertNotNull(yZero);
        assertEquals(0, yZero.minimumFloorY(), "Y0 remains a legal natural landing floor");

        CaveDropTrap.BetweenLayerSurfacePlan gapThirtyTwo =
                CaveDropTrap.planBetweenLayerSurface(
                        45, footprint, representativeLowerColumns(footprint, 13, 45));
        assertNotNull(gapThirtyTwo);
        assertEquals(32, 45 - gapThirtyTwo.minimumFloorY());

        CaveDropTrap.BetweenLayerSurfacePlan gapSixteen =
                CaveDropTrap.planBetweenLayerSurface(
                        39, footprint, representativeLowerColumns(footprint, 23, 39));
        assertNotNull(gapSixteen);
        assertEquals(16, 39 - gapSixteen.maximumFloorY());
    }

    @Test
    void floodedRewardGalleryIsMateriallyLargerThanItsEntrance() {
        assertEquals(36, CaveDropTrap.MIN_FLOODED_GALLERY_FOOTPRINT);
        assertEquals(16, CaveDropTrap.MIN_FLOODED_GALLERY_ENTRANCE_MARGIN);
        assertTrue(CaveDropTrap.floodedGalleryQualifies(7, 36),
                "the smallest accepted irregular entrance still opens into a 36-cell gallery");
        assertFalse(CaveDropTrap.floodedGalleryQualifies(7, 35), "a small pocket is not a gallery");
        assertFalse(CaveDropTrap.floodedGalleryQualifies(24, 39),
                "a broad entrance needs at least sixteen additional connected gallery cells");
        assertTrue(CaveDropTrap.floodedGalleryQualifies(24, 40));
    }

    @Test
    void insetCaveCarpetRequiresExistingWalkableFloorApproachAndRejectsSuspendedBridge() {
        boolean[][] candidates = centeredIrregularCandidate();
        boolean[][] twoOpposingBanks = northSouthBanks();

        Object insetPlan = planInsetCarpet(candidates, twoOpposingBanks);
        assertNotNull(insetPlan,
                "a six-cell drop patch inset in an existing cave floor is a stumble carpet, not an overhang");
        List<?> powder = planCells(insetPlan, "powderCells");
        List<?> regular = planCells(insetPlan, "regularCells");
        List<?> firstBank = planCells(insetPlan, "firstApproachBank");
        List<?> secondBank = planCells(insetPlan, "secondApproachBank");
        assertEquals(7, powder.size(), "the complete irregular drop patch is powder, never a single-block lie");
        assertTrue(regular.size() >= Math.max(
                        CaveDropTrap.MIN_FIRM_CAMOUFLAGE_CELLS,
                        (powder.size() + CaveDropTrap.MAX_POWDER_CELLS_PER_FIRM_CAMOUFLAGE - 1)
                                / CaveDropTrap.MAX_POWDER_CELLS_PER_FIRM_CAMOUFLAGE),
                "firm supported snow shoulders make a meaningful visual mix around the deceptive powder");
        assertTrue(firstBank.size() >= CaveDropTrap.MIN_APPROACH_BANK_RUN);
        assertTrue(secondBank.size() >= CaveDropTrap.MIN_APPROACH_BANK_RUN);
        assertDisjoint(powder, regular, "a cell has one visible floor role");
        assertAllCellsFrom(powder, candidates, "every powder cell originates in the qualifying drop patch");
        assertAllCellsFrom(regular, twoOpposingBanks,
                "every regular collar cell is a pre-existing supported cave floor cell");
        assertEquals("NORTH_SOUTH", planAccessor(insetPlan, "approachAxis").toString(),
                "the two intact approach banks, rather than a suspended bridge edge, define the carpet axis");

        assertNull(planInsetCarpet(candidates, new boolean[8][8]),
                "RED: a qualifying thin 2x3 shell with no existing cave floor around it is a suspended bridge");

        boolean[][] oneBank = new boolean[8][8];
        for (int x = 2; x <= 5; x++) {
            oneBank[x][1] = true;
            oneBank[x][2] = true;
        }
        assertNull(planInsetCarpet(candidates, oneBank),
                "RED: one bank is a dangling shelf, not a natural walk-through cave-floor carpet");

        boolean[][] isolatedPixels = northSouthBanks();
        isolatedPixels[4][2] = false;
        isolatedPixels[4][6] = false;
        assertNull(planInsetCarpet(candidates, isolatedPixels),
                "RED: isolated stable pixels on each side are not contiguous approach runs");

        boolean[][] disconnectedCandidates = centeredIrregularCandidate();
        disconnectedCandidates[4][5] = false;
        disconnectedCandidates[6][5] = true;
        assertNull(planInsetCarpet(disconnectedCandidates, twoOpposingBanks),
                "RED: the public planner rejects a disconnected entrance mask");

        boolean[][] tinyLedges = new boolean[8][8];
        tinyLedges[3][2] = true;
        tinyLedges[4][2] = true;
        tinyLedges[3][6] = true;
        tinyLedges[4][6] = true;
        assertNull(planInsetCarpet(candidates, tinyLedges),
                "RED: bank runs without a larger stable floor behind them are tiny ledges");

        boolean[][] rectangularPanel = new boolean[8][8];
        for (int x = 3; x <= 4; x++) {
            for (int z = 3; z <= 5; z++) {
                rectangularPanel[x][z] = true;
            }
        }
        assertNull(planInsetCarpet(rectangularPanel, twoOpposingBanks),
                "RED: a filled rectangular powder panel repeats the conspicuous overhang grammar");

        boolean[][] edgeCandidates = new boolean[8][8];
        for (int z = 3; z <= 5; z++) {
            edgeCandidates[0][z] = true;
            edgeCandidates[1][z] = true;
        }
        assertNull(planInsetCarpet(edgeCandidates, twoOpposingBanks),
                "RED: a candidate touching the observation/owner-grid edge cannot prove its complete natural collar");
    }

    @Test
    void oversizedNaturalComponentCanYieldOneSafeBoundedIrregularInset() {
        boolean[][] natural = new boolean[16][16];
        for (int x = 8; x <= 13; x++) {
            for (int z = 3; z <= 10; z++) {
                natural[x][z] = true;
            }
        }
        for (int x = 3; x <= 8; x++) {
            natural[x][6] = true;
        }
        for (int x = 3; x <= 4; x++) {
            for (int z = 5; z <= 7; z++) {
                natural[x][z] = true;
            }
        }
        long naturalArea = java.util.Arrays.stream(natural)
                .flatMapToInt(row -> {
                    int[] values = new int[row.length];
                    for (int index = 0; index < row.length; index++) {
                        values[index] = row[index] ? 1 : 0;
                    }
                    return java.util.Arrays.stream(values);
                })
                .sum();
        assertTrue(naturalArea > CaveDropTrap.PATCH_MAX_AREA,
                "the RED fixture is one connected natural candidate region larger than the authored cap");

        boolean[][] stable = new boolean[16][16];
        for (int x = 2; x <= 5; x++) {
            stable[x][3] = true;
            stable[x][4] = true;
            stable[x][8] = true;
            stable[x][9] = true;
        }
        assertNull(CaveDropTrap.planInsetCarpet(natural, stable),
                "the whole oversized natural region is never authored as one trap");

        List<CaveDropTrap.InsetCarpetPlan> plans =
                CaveDropTrap.planInsetCarpetCandidates(natural, stable, 4);
        assertFalse(plans.isEmpty(),
                "a viable supported lobe inside the large natural region must survive bounded selection");
        CaveDropTrap.InsetCarpetPlan selected = plans.getFirst();
        assertTrue(selected.powderCells().size() >= CaveDropTrap.MIN_PATCH_AREA
                        && selected.powderCells().size() <= CaveDropTrap.PATCH_MAX_AREA);
        assertTrue(selected.powderCells().stream().allMatch(cell -> natural[cell.x()][cell.z()]),
                "bounded selection never invents a candidate outside the real natural component");
        assertEquals(selected, CaveDropTrap.planInsetCarpetCandidates(natural, stable, 4).getFirst(),
                "the same observed masks always select the same canonical inset");
    }

    @Test
    void boundedRewardRollKeepsOrdinaryTrapsCommonAndMakesBothRareEndingsExplicit() {
        List<Object> plans = new ArrayList<>();
        int ordinary = 0;
        int flooded = 0;
        int prospector = 0;
        for (int rewardRoll = 0; rewardRoll < 16; rewardRoll++) {
            Object plan = planReward(rewardRoll, 32, -24, 3, 16);
            assertNotNull(plan, "every accepted trap roll has one deterministic outcome");
            plans.add(plan);
            switch (planKind(plan)) {
                case "ORDINARY" -> ordinary++;
                case "FLOODED_ORE_GALLERY" -> flooded++;
                case "LOST_PROSPECTOR" -> prospector++;
                default -> throw new AssertionError("unrecognized cave-trap reward outcome: " + planKind(plan));
            }
        }
        assertEquals(14, ordinary, "ordinary stumble traps occupy fourteen of sixteen accepted rolls");
        assertEquals(1, flooded, "the flooded ore-gallery reward occupies one roll");
        assertEquals(1, prospector, "the lost-prospector reward occupies one roll");
        assertNull(planReward(-1, 32, -24, 3, 16), "negative reward rolls are outside the bounded selector");
        assertNull(planReward(16, 32, -24, 3, 16), "the one reward roll is bounded to sixteen outcomes");

        Object floodedPlan = planWithKind(plans, "FLOODED_ORE_GALLERY");
        assertTrue((Integer) planAccessor(floodedPlan, "landingY") < 0,
                "the flooded gallery landing is genuinely below Y=0");
        assertTrue((Integer) planAccessor(floodedPlan, "waterDepth") >= 3,
                "the flooded gallery has a real safe landing pool, not a decorative puddle");
        assertEquals(Boolean.TRUE, planAccessor(floodedPlan, "freezingForbidden"),
                "deep reward-pool water explicitly opts out of polar freezing damage");
        int oreBudget = (Integer) planAccessor(floodedPlan, "oreBudget");
        assertTrue(oreBudget >= 12 && oreBudget <= 24,
                "the ore-gallery payoff is rich but bounded, never an unlimited ore chamber");

        Object prospectorPlan = planWithKind(plans, "LOST_PROSPECTOR");
        assertEquals(Boolean.TRUE, planAccessor(prospectorPlan, "powderCushion"),
                "the lost prospector still lands on the trap's powder cushion");
        assertEquals(Boolean.TRUE, planAccessor(prospectorPlan, "solidLanding"),
                "the cushion rests on a solid landing, never a floating or fluid-only floor");
        assertEquals(Boolean.TRUE, planAccessor(prospectorPlan, "staticRemains"),
                "the remains are static world dressing, not a hostile or moving encounter");
        assertEquals(Boolean.TRUE, planAccessor(prospectorPlan, "reachableChest"),
                "the valuable chest remains accessible from the landing");
        int lootBudget = (Integer) planAccessor(prospectorPlan, "valuableLootBudget");
        assertTrue(lootBudget >= 8 && lootBudget <= 16,
                "the chest is worthwhile but has a bounded valuable-loot budget");

        for (int rewardRoll = 0; rewardRoll < 16; rewardRoll++) {
            assertEquals(plans.get(rewardRoll), planReward(rewardRoll, 32, -24, 3, 16),
                    "the same bounded roll and budget always produce the same immutable reward plan");
        }

        int floodedRoll = rollWithKind(plans, "FLOODED_ORE_GALLERY");
        Object ordinaryPlan = planWithKind(plans, "ORDINARY");
        assertEquals(ordinaryPlan, planReward(floodedRoll, 32, 0, 3, 16),
                "a flooded roll without a below-Y0 landing falls back to the identical ordinary trap");
        assertEquals(ordinaryPlan, planReward(floodedRoll, 32, -24, 2, 16),
                "a flooded roll without three-deep water falls back to ordinary, never another rare scene");
        assertEquals(ordinaryPlan, planReward(floodedRoll, 32, -24, 3, 11),
                "an under-budget ore gallery falls back to the identical ordinary trap");
        assertEquals(ordinaryPlan, planReward(floodedRoll, 32, -24, 3, 25),
                "an over-budget ore gallery falls back to the identical ordinary trap");
    }

    @Test
    void rewardRollIsBoundedStableAndDerivedOnlyFromSeedAndCanonicalAnchor() {
        long seed = 0x4c41544954554445L;
        int anchorX = -2736;
        int anchorY = 48;
        int anchorZ = 9408;
        int first = rewardRoll(seed, anchorX, anchorY, anchorZ);
        assertTrue(first >= 0 && first < 16, "a reward roll is always in the planReward 0..15 budget");
        for (int repeat = 0; repeat < 20; repeat++) {
            assertEquals(first, rewardRoll(seed, anchorX, anchorY, anchorZ),
                    "the same world seed and canonical trap anchor never consume or depend on mutable RNG state");
        }

        Set<Integer> outcomes = new HashSet<>();
        for (int chunkX = -8; chunkX <= 8; chunkX++) {
            for (int chunkZ = -8; chunkZ <= 8; chunkZ++) {
                int roll = rewardRoll(seed, chunkX * 16, -24 + Math.floorMod(chunkX + chunkZ, 4), chunkZ * 16);
                assertTrue(roll >= 0 && roll < 16, "every bounded anchor produces one legal reward roll");
                outcomes.add(roll);
            }
        }
        assertTrue(outcomes.size() > 1,
                "changing canonical anchors across a bounded grid must not collapse every trap to one outcome");
    }

    @Test
    void atomicWriteLawPostReadsAndExactlyRestoresInjectedOrPartialFailure() {
        List<CaveDropTrap.AtomicStateChange<String>> changes = List.of(
                new CaveDropTrap.AtomicStateChange<>("stone-a", "powder"),
                new CaveDropTrap.AtomicStateChange<>("stone-b", "air"),
                new CaveDropTrap.AtomicStateChange<>("stone-c", "cushion"));
        List<String> states = new ArrayList<>(List.of("stone-a", "stone-b", "stone-c"));
        AtomicBoolean finalized = new AtomicBoolean();
        CaveDropTrap.AtomicResult injected = CaveDropTrap.applyAtomically(
                changes,
                stringStateAdapter(states, -1),
                index -> index == 2,
                () -> {
                    finalized.set(true);
                    return true;
                });
        assertFalse(injected.success(), "an injected third-write failure cannot emit a successful scene");
        assertTrue(injected.rollbackVerified(), "every earlier write is read back after reverse rollback");
        assertEquals(List.of("stone-a", "stone-b", "stone-c"), states,
                "an injected failure restores the exact original states");
        assertFalse(finalized.get(), "the chest/audit finalizer is never reached after an injected write failure");

        states.clear();
        states.addAll(List.of("stone-a", "stone-b", "stone-c"));
        CaveDropTrap.AtomicResult partial = CaveDropTrap.applyAtomically(
                changes, stringStateAdapter(states, 1), null, () -> true);
        assertFalse(partial.success(), "a writer returning false after mutating is still a transaction failure");
        assertTrue(partial.rollbackVerified(),
                "the failed index is included in rollback because false may follow a partial write");
        assertEquals(List.of("stone-a", "stone-b", "stone-c"), states,
                "even a partial failing write restores byte-identical input state");

        states.clear();
        states.addAll(List.of("stone-a", "stone-b", "stone-c"));
        CaveDropTrap.AtomicResult committed = CaveDropTrap.applyAtomically(
                changes, stringStateAdapter(states, -1), null, () -> true);
        assertTrue(committed.success());
        assertTrue(committed.rollbackVerified());
        assertEquals(List.of("powder", "air", "cushion"), states,
                "success means every desired state survived the final post-read");
    }

    @Test
    void roofedGalleryThroatPinsTwoMouthVertexCutAndTwoThreeCellFirmBanks() {
        for (CaveDropTrap.RibbonOrientation orientation
                : CaveDropTrap.RibbonOrientation.values()) {
            CaveDropTrap.RoofedGalleryThroatPlan plan =
                    CaveDropTrap.planRoofedGalleryThroat(40, orientation);
            assertNotNull(plan);
            assertEquals(CaveDropTrap.PassARejection.PASS,
                    CaveDropTrap.validateRoofedGalleryThroatTopology(plan));
            assertEquals(12, plan.coverCells().size());
            assertEquals(6, plan.powderCells().size());
            assertEquals(6, plan.firmCells().size());
            assertEquals(2, plan.firmBanks().size());
            assertEquals(List.of(3, 3), plan.firmBankSizes());
            assertEquals(4, plan.approachA().size());
            assertEquals(4, plan.returnB().size());
            assertTrue(java.util.Collections.disjoint(
                    plan.approachA(), plan.returnB()));
            assertFourConnected(plan.coverCells(), "the mixed cover is one carpet");
            assertFourConnected(plan.powderCells(), "all six drop entries are one cut");

            Set<CaveDropTrap.Cell> complete = new HashSet<>(plan.coverCells());
            complete.addAll(plan.approachA());
            complete.addAll(plan.returnB());
            assertTrue(reachable(
                    plan.approachA().getFirst(), plan.returnB().getFirst(), complete),
                    "the untouched gallery crosses the complete mixed carpet");
            complete.removeAll(plan.powderCells());
            assertFalse(reachable(
                    plan.approachA().getFirst(), plan.returnB().getFirst(), complete),
                    "removing powder disconnects mouth A from mouth B");
            assertTrue(touches(plan.approachA(), plan.firmBanks().get(0)));
            assertFalse(touches(plan.approachA(), plan.firmBanks().get(1)));
            assertTrue(touches(plan.returnB(), plan.firmBanks().get(1)));
            assertFalse(touches(plan.returnB(), plan.firmBanks().get(0)));
            CaveDropTrap.RoofedThroatMeasurements measured =
                    CaveDropTrap.measureRoofedGalleryThroat(plan);
            assertNotNull(measured);
            assertEquals(12, measured.coverCount());
            assertEquals(6, measured.powderCount());
            assertEquals(6, measured.firmCount());
            assertEquals(List.of(3, 3), measured.firmComponentSizes());
            assertEquals(2, measured.mouthCount());
            assertTrue(measured.distinctMouths());
            assertTrue(measured.coverConnected());
            assertTrue(measured.powderConnected());
            assertTrue(measured.firmRolesCorrect());
            assertTrue(measured.powderVertexCut());
            assertTrue(measured.witnessWriteDisjoint());
            assertTrue(measured.lockedTopologyMatches());
        }
    }

    @Test
    void roofedGallerySharedPredicateAcceptsOnlyCompleteOccupiedNaturalPreimage() {
        CaveDropTrap.RoofedGalleryThroatPlan plan =
                CaveDropTrap.planRoofedGalleryThroat(40, CaveDropTrap.RibbonOrientation.NORTH);
        TestThroatWorld green = TestThroatWorld.green(plan);
        CaveDropTrap.RoofedThroatEvaluation accepted =
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, green);
        assertEquals(CaveDropTrap.PassARejection.PASS, accepted.rejection());
        assertTrue(accepted.ordinaryFeasible());
        assertFalse(accepted.floodedFeasible(),
                "rare feasibility belongs to the real Minecraft production planners");
        assertFalse(accepted.prospectorFeasible());
        assertEquals(CaveDropTrap.RewardFeasibilityFailure.NOT_EVALUATED,
                accepted.floodedFailure());
        assertEquals(CaveDropTrap.RewardFeasibilityFailure.NOT_EVALUATED,
                accepted.prospectorFailure());

        TestThroatWorld oneSided = TestThroatWorld.green(plan);
        oneSided.kinds.put(
                plan.continuationFloorVoxels().getFirst(),
                CaveDropTrap.ThroatBlockKind.AIR);
        assertEquals(
                CaveDropTrap.PassARejection.SEAFLOOR_OR_FLUID_STANDING,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, oneSided).rejection());

        TestThroatWorld selfAuthored = TestThroatWorld.green(plan);
        selfAuthored.authored.add(plan.continuationFloorVoxels().getFirst());
        assertEquals(
                CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, selfAuthored).rejection());

        TestThroatWorld seafloor = TestThroatWorld.green(plan);
        seafloor.kinds.put(
                plan.headroomVoxels().getFirst(), CaveDropTrap.ThroatBlockKind.FLUID);
        assertEquals(
                CaveDropTrap.PassARejection.SEAFLOOR_OR_FLUID_STANDING,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, seafloor).rejection());

        TestThroatWorld thinRoof = TestThroatWorld.green(plan);
        CaveDropTrap.Cell roofColumn = plan.relevantFloorColumns().getFirst();
        thinRoof.kinds.put(
                new CaveDropTrap.Voxel(roofColumn.x(), 44, roofColumn.z()),
                CaveDropTrap.ThroatBlockKind.AIR);
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, thinRoof).rejection());

        TestThroatWorld shaftVoid = TestThroatWorld.green(plan);
        shaftVoid.kinds.put(
                plan.carvedAirVoxels().getFirst(), CaveDropTrap.ThroatBlockKind.AIR);
        assertEquals(
                CaveDropTrap.PassARejection.CAVITY_AIR_OR_FLUID,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, shaftVoid).rejection());

        TestThroatWorld protectedTarget = TestThroatWorld.green(plan);
        protectedTarget.kinds.put(
                plan.carvedAirVoxels().getFirst(),
                CaveDropTrap.ThroatBlockKind.ORE_OR_BLOCK_ENTITY);
        assertEquals(
                CaveDropTrap.PassARejection.ORE_BLOCK_ENTITY_OR_PROTECTED,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, protectedTarget).rejection());

        TestThroatWorld partialGravity = TestThroatWorld.green(plan);
        CaveDropTrap.Cell powder = plan.powderCells().getFirst();
        CaveDropTrap.Voxel cushion =
                new CaveDropTrap.Voxel(powder.x(), plan.landingY(), powder.z());
        partialGravity.kinds.put(cushion, CaveDropTrap.ThroatBlockKind.GRAVEL);
        partialGravity.kinds.put(cushion.below(), CaveDropTrap.ThroatBlockKind.GRAVEL);
        assertEquals(
                CaveDropTrap.PassARejection.GRAVITY_OR_PARTIAL_STABILIZATION,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, partialGravity).rejection());

        TestThroatWorld badShell = TestThroatWorld.green(plan);
        badShell.kinds.put(
                plan.lateralShellVoxels().getFirst(), CaveDropTrap.ThroatBlockKind.GRAVEL);
        assertEquals(
                CaveDropTrap.PassARejection.SHELL_OR_SUPPORT,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, badShell).rejection());

        TestThroatWorld outsideOwner = TestThroatWorld.green(plan);
        outsideOwner.outside.add(plan.ownerEnvelopeVoxels().getFirst());
        assertEquals(
                CaveDropTrap.PassARejection.OWNER,
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, outsideOwner).rejection());
    }

    @Test
    void upperRoofSearchSkipsOnlyHangingIciclesAndStillRequiresTheRealRoofLaw() {
        CaveDropTrap.RoofedGalleryThroatPlan plan =
                CaveDropTrap.planRoofedGalleryThroat(
                        40, CaveDropTrap.RibbonOrientation.NORTH);
        CaveDropTrap.Cell column = plan.relevantFloorColumns().getFirst();
        CaveDropTrap.Voxel firstIcicle =
                new CaveDropTrap.Voxel(column.x(), 43, column.z());
        CaveDropTrap.Voxel secondIcicle = firstIcicle.above();
        CaveDropTrap.Voxel realCeiling = secondIcicle.above();
        CaveDropTrap.Voxel secondRoofLayer = realCeiling.above();

        TestThroatWorld clear = TestThroatWorld.green(plan);
        CaveDropTrap.UpperThroatEvaluation clearEvaluation =
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, clear);
        assertEquals(CaveDropTrap.PassARejection.PASS, clearEvaluation.rejection());

        TestThroatWorld decorated = TestThroatWorld.green(plan);
        decorated.kinds.put(firstIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        decorated.kinds.put(secondIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        CaveDropTrap.UpperThroatEvaluation decoratedEvaluation =
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, decorated);
        assertEquals(clearEvaluation.rejection(), decoratedEvaluation.rejection(),
                "one or more Latitude icicles below a real roof preserve upper eligibility");
        assertTrue(decoratedEvaluation.feasible());
        assertTrue(decoratedEvaluation.naturalWitnessVoxels().contains(realCeiling));
        assertTrue(decoratedEvaluation.naturalWitnessVoxels().contains(secondRoofLayer));
        assertFalse(decoratedEvaluation.naturalWitnessVoxels().contains(firstIcicle));
        assertFalse(decoratedEvaluation.naturalWitnessVoxels().contains(secondIcicle));
        assertTrue(java.util.Collections.disjoint(
                        List.of(firstIcicle, secondIcicle), plan.ordinaryAuthoredVoxels()),
                "accepted roof decorations are outside the generated scene's authored clearance");

        TestThroatWorld noRealRoof = TestThroatWorld.green(plan);
        for (int rise = CaveDropTrap.MIN_ROOF_SEARCH_RISE;
                rise <= CaveDropTrap.MAX_ROOF_SEARCH_RISE;
                rise++) {
            noRealRoof.kinds.put(
                    new CaveDropTrap.Voxel(column.x(), plan.floorY() + rise, column.z()),
                    CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        }
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, noRealRoof).rejection(),
                "icicles alone can never satisfy the structural ceiling");

        TestThroatWorld thinRoof = TestThroatWorld.green(plan);
        thinRoof.kinds.put(firstIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        thinRoof.kinds.put(secondIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        thinRoof.kinds.put(secondRoofLayer, CaveDropTrap.ThroatBlockKind.AIR);
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, thinRoof).rejection());

        TestThroatWorld wetRoof = TestThroatWorld.green(plan);
        wetRoof.kinds.put(firstIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        wetRoof.kinds.put(secondIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        wetRoof.kinds.put(secondRoofLayer, CaveDropTrap.ThroatBlockKind.FLUID);
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, wetRoof).rejection());

        TestThroatWorld skyRoof = TestThroatWorld.green(plan);
        skyRoof.kinds.put(firstIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        skyRoof.kinds.put(secondIcicle, CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        skyRoof.sky.add(realCeiling.above().above());
        assertEquals(
                CaveDropTrap.PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF,
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, skyRoof).rejection());

        TestThroatWorld supportIcicle = TestThroatWorld.green(plan);
        CaveDropTrap.Cell firm = plan.firmCells().getFirst();
        supportIcicle.kinds.put(
                new CaveDropTrap.Voxel(firm.x(), plan.floorY() - 1, firm.z()),
                CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        assertEquals(
                CaveDropTrap.PassARejection.SHELL_OR_SUPPORT,
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, supportIcicle).rejection());

        TestThroatWorld headroomIcicle = TestThroatWorld.green(plan);
        headroomIcicle.kinds.put(
                plan.headroomVoxels().getFirst(),
                CaveDropTrap.ThroatBlockKind.HANGING_ICICLE);
        assertEquals(
                CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS,
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, headroomIcicle).rejection());

        TestThroatWorld unrelatedOther = TestThroatWorld.green(plan);
        unrelatedOther.kinds.put(firstIcicle, CaveDropTrap.ThroatBlockKind.OTHER);
        assertEquals(
                CaveDropTrap.PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS,
                CaveDropTrap.evaluateRoofedGalleryUpper(plan, unrelatedOther).rejection(),
                "vanilla pointed dripstone and every unrelated OTHER stay strict rejects");
    }

    @Test
    void passAPredictionCallsExistingSelectorsAndFallsBackWithoutRerolling() {
        for (int roll = 0; roll < 16; roll++) {
            CaveDropTrap.RewardKind expected = roll <= 13
                    ? CaveDropTrap.RewardKind.ORDINARY
                    : roll == 14
                            ? CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY
                            : CaveDropTrap.RewardKind.LOST_PROSPECTOR;
            assertEquals(expected, CaveDropTrap.requestedRewardKind(roll));
        }
        assertNull(CaveDropTrap.requestedRewardKind(-1));
        assertNull(CaveDropTrap.requestedRewardKind(16));
        CaveDropTrap.RoofedGalleryThroatPlan plan =
                CaveDropTrap.planRoofedGalleryThroat(40, CaveDropTrap.RibbonOrientation.NORTH);
        CaveDropTrap.RoofedThroatEvaluation feasible =
                CaveDropTrap.evaluateRoofedGalleryThroat(plan, TestThroatWorld.green(plan));
        long prospectorSeed = 0;
        while (CaveDropTrap.rewardRoll(prospectorSeed, 4, 40, 5) != 15) {
            prospectorSeed++;
        }
        CaveDropTrap.RoofedThroatEvaluation noProspector =
                new CaveDropTrap.RoofedThroatEvaluation(
                        CaveDropTrap.PassARejection.PASS,
                        true, feasible.floodedFeasible(), feasible.floodedFailure(),
                        false, CaveDropTrap.RewardFeasibilityFailure.AIR_OR_FLUID);
        CaveDropTrap.PassAPrediction prediction =
                CaveDropTrap.predictRoofedThroatOutcome(
                        0.0f, 0, prospectorSeed, 4, 40, 5,
                        plan, noProspector, -64);
        assertTrue(prediction.fractionAccepted());
        assertTrue(prediction.capAccepted());
        assertEquals(15, prediction.rewardRoll());
        assertEquals(CaveDropTrap.RewardKind.LOST_PROSPECTOR, prediction.requestedKind());
        assertEquals(CaveDropTrap.RewardKind.ORDINARY, prediction.resolvedKind());
        assertTrue(prediction.ordinaryFallback());

        long floodedSeed = 0;
        while (CaveDropTrap.rewardRoll(floodedSeed, 4, 40, 5) != 14) {
            floodedSeed++;
        }
        CaveDropTrap.RoofedThroatEvaluation noFlooded =
                new CaveDropTrap.RoofedThroatEvaluation(
                        CaveDropTrap.PassARejection.PASS,
                        true, false,
                        CaveDropTrap.RewardFeasibilityFailure.REAL_PLANNER_REJECTED,
                        true, CaveDropTrap.RewardFeasibilityFailure.NONE);
        CaveDropTrap.PassAPrediction floodedFallback =
                CaveDropTrap.predictRoofedThroatOutcome(
                        0.0f, 0, floodedSeed, 4, 40, 5,
                        plan, noFlooded, -64);
        assertEquals(14, floodedFallback.rewardRoll());
        assertEquals(CaveDropTrap.RewardKind.FLOODED_ORE_GALLERY,
                floodedFallback.requestedKind());
        assertEquals(CaveDropTrap.RewardKind.ORDINARY,
                floodedFallback.resolvedKind());
        assertTrue(floodedFallback.ordinaryFallback());

        CaveDropTrap.PassAPrediction capped =
                CaveDropTrap.predictRoofedThroatOutcome(
                        0.0f, CaveDropTrap.MAX_PATCHES_PER_CHUNK,
                        prospectorSeed, 4, 40, 5, plan, feasible, -64);
        assertTrue(capped.fractionAccepted());
        assertFalse(capped.capAccepted());
        assertEquals(-1, capped.rewardRoll());
    }

    private static final class TestThroatWorld
            implements CaveDropTrap.RoofedThroatWorldView {
        private final Map<CaveDropTrap.Voxel, CaveDropTrap.ThroatBlockKind> kinds =
                new HashMap<>();
        private final Set<CaveDropTrap.Voxel> authored = new HashSet<>();
        private final Set<CaveDropTrap.Voxel> outside = new HashSet<>();
        private final Set<CaveDropTrap.Voxel> sky = new HashSet<>();

        static TestThroatWorld green(CaveDropTrap.RoofedGalleryThroatPlan plan) {
            TestThroatWorld world = new TestThroatWorld();
            for (CaveDropTrap.Voxel headroom : plan.headroomVoxels()) {
                world.kinds.put(headroom, CaveDropTrap.ThroatBlockKind.AIR);
            }
            return world;
        }

        @Override
        public CaveDropTrap.ThroatBlockKind blockKind(CaveDropTrap.Voxel voxel) {
            return kinds.getOrDefault(voxel, CaveDropTrap.ThroatBlockKind.SAFE_NATURAL);
        }

        @Override
        public boolean insideOwnerInset(CaveDropTrap.Voxel voxel) {
            return !outside.contains(voxel);
        }

        @Override
        public boolean authored(CaveDropTrap.Voxel voxel) {
            return authored.contains(voxel);
        }

        @Override
        public boolean seesSky(CaveDropTrap.Voxel voxel) {
            return sky.contains(voxel);
        }

        @Override
        public int minimumBuildY() {
            return -64;
        }
    }

    private static boolean touches(
            List<CaveDropTrap.Cell> first, List<CaveDropTrap.Cell> second) {
        return first.stream().anyMatch(left -> second.stream().anyMatch(right ->
                Math.abs(left.x() - right.x()) + Math.abs(left.z() - right.z()) == 1));
    }

    private static CaveDropTrap.AtomicStateAdapter<String> stringStateAdapter(
            List<String> states, int mutateThenFailIndex) {
        return new CaveDropTrap.AtomicStateAdapter<>() {
            @Override
            public String read(int index) {
                return states.get(index);
            }

            @Override
            public boolean write(int index, String state) {
                states.set(index, state);
                return index != mutateThenFailIndex || state.startsWith("stone");
            }
        };
    }

    private static boolean[][] centeredIrregularCandidate() {
        boolean[][] mask = new boolean[8][8];
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                if (x != 5 || z == 4) {
                    mask[x][z] = true;
                }
            }
        }
        return mask;
    }

    private static boolean[][] northSouthBanks() {
        boolean[][] mask = new boolean[8][8];
        for (int x = 2; x <= 5; x++) {
            mask[x][1] = true;
            mask[x][2] = true;
            mask[x][6] = true;
            mask[x][7] = true;
        }
        return mask;
    }

    private static Object planInsetCarpet(boolean[][] candidates, boolean[][] stableFloor) {
        try {
            Method method = CaveDropTrap.class.getMethod("planInsetCarpet", boolean[][].class, boolean[][].class);
            return method.invoke(null, candidates, stableFloor);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("RED: CaveDropTrap needs public planInsetCarpet(boolean[][], boolean[][]) "
                    + "to reject suspended cave bridges before world writes", missing);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new AssertionError("planInsetCarpet must be a usable production planning seam", failure);
        }
    }

    private static Object planReward(int rewardRoll, int ordinaryLandingY,
            int rareLandingY, int waterDepth, int oreBudget) {
        try {
            Method method = CaveDropTrap.class.getMethod("planReward",
                    int.class, int.class, int.class, int.class, int.class);
            return method.invoke(null, rewardRoll, ordinaryLandingY, rareLandingY, waterDepth, oreBudget);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("RED: CaveDropTrap needs public planReward(int, int, int, int, int) "
                    + "so one bounded roll chooses ordinary, flooded-gallery, or prospector outcomes", missing);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new AssertionError("planReward must be a usable production planning seam", failure);
        }
    }

    private static int rewardRoll(long worldSeed, int anchorX, int anchorY, int anchorZ) {
        try {
            Method method = CaveDropTrap.class.getMethod(
                    "rewardRoll", long.class, int.class, int.class, int.class);
            return (int) method.invoke(null, worldSeed, anchorX, anchorY, anchorZ);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("RED: CaveDropTrap needs public rewardRoll(long, int, int, int) "
                    + "so reward selection depends only on seed plus canonical trap anchor", missing);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new AssertionError("rewardRoll must be a usable pure production seam", failure);
        }
    }

    private static String planKind(Object plan) {
        return planAccessor(plan, "kind").toString();
    }

    private static Object planWithKind(List<?> plans, String expectedKind) {
        return plans.stream().filter(plan -> expectedKind.equals(planKind(plan))).findFirst().orElseThrow(
                () -> new AssertionError("missing required reward outcome " + expectedKind));
    }

    private static int rollWithKind(List<?> plans, String expectedKind) {
        for (int roll = 0; roll < plans.size(); roll++) {
            if (expectedKind.equals(planKind(plans.get(roll)))) {
                return roll;
            }
        }
        throw new AssertionError("missing required reward roll " + expectedKind);
    }

    private static List<?> planCells(Object plan, String accessorName) {
        Object value = planAccessor(plan, accessorName);
        assertTrue(value instanceof Collection<?>, accessorName + " exposes an immutable cell collection");
        return List.copyOf((Collection<?>) value);
    }

    private static Object planAccessor(Object plan, String accessorName) {
        try {
            Method accessor = plan.getClass().getMethod(accessorName);
            return accessor.invoke(plan);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("InsetCarpetPlan must expose " + accessorName + "()", missing);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new AssertionError("InsetCarpetPlan accessor " + accessorName + "() must be usable", failure);
        }
    }

    private static void assertDisjoint(List<?> first, List<?> second, String message) {
        for (Object cell : first) {
            assertFalse(second.contains(cell), message + ": " + cell);
        }
    }

    private static <T> Set<T> union(List<T> first, List<T> second) {
        Set<T> result = new HashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static CaveDropTrap.ProspectorLayout orient(
            CaveDropTrap.ProspectorLayout layout,
            CaveDropTrap.RibbonOrientation orientation) {
        return new CaveDropTrap.ProspectorLayout(
                orient(layout.cushion(), orientation),
                orient(layout.foot(), orientation),
                orient(layout.head(), orientation),
                orient(layout.chest(), orientation),
                orient(layout.front(), orientation));
    }

    private static CaveDropTrap.Cell orient(
            CaveDropTrap.Cell cell,
            CaveDropTrap.RibbonOrientation orientation) {
        return switch (orientation) {
            case NORTH -> cell;
            case EAST -> new CaveDropTrap.Cell(-cell.z(), cell.x());
            case SOUTH -> new CaveDropTrap.Cell(-cell.x(), -cell.z());
            case WEST -> new CaveDropTrap.Cell(cell.z(), -cell.x());
        };
    }

    private static boolean reachable(
            CaveDropTrap.Cell start,
            CaveDropTrap.Cell target,
            Set<CaveDropTrap.Cell> allowed) {
        if (!allowed.contains(start) || !allowed.contains(target)) {
            return false;
        }
        Set<CaveDropTrap.Cell> visited = new HashSet<>();
        ArrayDeque<CaveDropTrap.Cell> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            CaveDropTrap.Cell current = queue.removeFirst();
            if (current.equals(target)) {
                return true;
            }
            for (CaveDropTrap.Cell neighbour : List.of(
                    new CaveDropTrap.Cell(current.x() - 1, current.z()),
                    new CaveDropTrap.Cell(current.x() + 1, current.z()),
                    new CaveDropTrap.Cell(current.x(), current.z() - 1),
                    new CaveDropTrap.Cell(current.x(), current.z() + 1))) {
                if (allowed.contains(neighbour) && visited.add(neighbour)) {
                    queue.addLast(neighbour);
                }
            }
        }
        return false;
    }

    private static void assertFourConnected(
            List<CaveDropTrap.Cell> cells, String message) {
        Set<CaveDropTrap.Cell> remaining = new HashSet<>(cells);
        ArrayDeque<CaveDropTrap.Cell> queue = new ArrayDeque<>();
        CaveDropTrap.Cell start = cells.getFirst();
        remaining.remove(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            CaveDropTrap.Cell current = queue.removeFirst();
            List<CaveDropTrap.Cell> neighbours = List.of(
                    new CaveDropTrap.Cell(current.x() - 1, current.z()),
                    new CaveDropTrap.Cell(current.x() + 1, current.z()),
                    new CaveDropTrap.Cell(current.x(), current.z() - 1),
                    new CaveDropTrap.Cell(current.x(), current.z() + 1));
            for (CaveDropTrap.Cell neighbour : neighbours) {
                if (remaining.remove(neighbour)) {
                    queue.addLast(neighbour);
                }
            }
        }
        assertTrue(remaining.isEmpty(), message + ": disconnected " + remaining);
    }

    private static void assertAllCellsFrom(List<?> cells, boolean[][] mask, String message) {
        for (Object cell : cells) {
            int x = cellCoordinate(cell, "x");
            int z = cellCoordinate(cell, "z");
            assertTrue(x >= 0 && x < mask.length && z >= 0 && z < mask[x].length && mask[x][z],
                    message + ": " + x + "," + z);
        }
    }

    private static List<CaveDropTrap.Cell> lowerSurfaceFootprint() {
        return List.of(
                new CaveDropTrap.Cell(0, 0),
                new CaveDropTrap.Cell(0, 1),
                new CaveDropTrap.Cell(1, 0),
                new CaveDropTrap.Cell(1, 1),
                new CaveDropTrap.Cell(2, 0),
                new CaveDropTrap.Cell(2, 1));
    }

    private static List<CaveDropTrap.LowerColumnObservation> representativeLowerColumns(
            List<CaveDropTrap.Cell> footprint, int floorY, int upperFloorY) {
        List<CaveDropTrap.LowerColumnObservation> observations = new ArrayList<>();
        int airTopY = Math.min(floorY + 2, upperFloorY - 2);
        for (CaveDropTrap.Cell cell : footprint) {
            observations.add(lowerColumn(
                    cell, floorY, upperFloorY, airTopY, null, true));
        }
        return observations;
    }

    private static CaveDropTrap.LowerColumnObservation lowerColumn(
            CaveDropTrap.Cell cell,
            int floorY,
            int upperFloorY,
            int airTopY,
            CaveDropTrap.ThroatBlockKind plugHazard,
            boolean fourSafeSupports) {
        List<CaveDropTrap.ThroatBlockKind> vertical = new ArrayList<>();
        int plugBottomY = airTopY + 1;
        for (int y = floorY + 1; y < upperFloorY; y++) {
            if (y <= airTopY) {
                vertical.add(CaveDropTrap.ThroatBlockKind.AIR);
            } else if (plugHazard != null && y == plugBottomY + 2) {
                vertical.add(plugHazard);
            } else {
                vertical.add(CaveDropTrap.ThroatBlockKind.SAFE_NATURAL);
            }
        }
        return new CaveDropTrap.LowerColumnObservation(
                cell, floorY, fourSafeSupports, vertical);
    }

    private static int cellCoordinate(Object cell, String axis) {
        try {
            Method accessor = cell.getClass().getMethod(axis);
            Object value = accessor.invoke(cell);
            assertTrue(value instanceof Integer, "InsetCarpetPlan cells expose integer " + axis + " coordinates");
            return (Integer) value;
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("InsetCarpetPlan cells must expose " + axis + "()", missing);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new AssertionError("InsetCarpetPlan cell coordinate " + axis + "() must be usable", failure);
        }
    }
}
