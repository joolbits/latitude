package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for the S36 {@link PowderRoofTrap} hidden-bridge law (Peetsa 2026-07-21 video correction +
 * independent level-design review): a trap must read as an ordinary, walk-on continuation of the snowfield,
 * not a tiny lid down inside an already-visible hole. Covers are meaningful contiguous patches, align to two
 * opposing banks, contain no holes, and keep the deep-drop/cushion safety contract.
 */
class PowderRoofTrapTest {

    // --- candidacy -------------------------------------------------------------------------------------

    @Test
    void candidacyRequiresTheFullShaftDepth() {
        assertEquals(10, PowderRoofTrap.MIN_CANDIDATE_DEPTH_BLOCKS,
                "S36 may admit a real 10-block natural opening, but never intact ground");
        assertEquals(18, PowderRoofTrap.MIN_SHAFT_DEPTH_BLOCKS,
                "S36 drama floor: a hidden bridge must span a real crevasse, not a shallow scoop");
        assertTrue(PowderRoofTrap.isTrapCandidate(90, 100), "a 10-deep natural mouth enters planning");
        assertFalse(PowderRoofTrap.isTrapCandidate(91, 100), "a 9-deep shelf never enters planning");
        assertFalse(PowderRoofTrap.isTrapCandidate(100, 100), "flat snowfield is never a candidate");
        assertTrue(PowderRoofTrap.isCoverCandidate(98, 100),
                "a two-deep natural taper may carry solid shoulder camouflage");
        assertFalse(PowderRoofTrap.isCoverCandidate(99, 100),
                "one-deep ground has no air beneath the proposed roof and stays natural terrain");
    }

    @Test
    void completeWritePlanMustStayUniqueAndInsideItsOwnerChunk() {
        List<int[]> valid = List.of(
                new int[]{-16, 82, 32},
                new int[]{-1, 99, 47});
        assertTrue(PowderRoofTrap.ownerChunkWritePlanEligible(-16, 32, valid));
        assertFalse(PowderRoofTrap.ownerChunkWritePlanEligible(-16, 32,
                List.of(new int[]{0, 82, 32})),
                "a target in the neighbouring X chunk rejects the complete plan");
        assertFalse(PowderRoofTrap.ownerChunkWritePlanEligible(-16, 32,
                List.of(new int[]{-16, 82, 48})),
                "a target in the neighbouring Z chunk rejects the complete plan");
        assertFalse(PowderRoofTrap.ownerChunkWritePlanEligible(-16, 32,
                List.of(new int[]{-16, 82, 32}, new int[]{-16, 82, 32})),
                "duplicate positions are not a truthful complete write plan");
    }

    @Test
    void writeAccountingReportsOnlyACompleteBatch() {
        assertTrue(PowderRoofTrap.completeWriteBatchSucceeded(12, 12));
        assertFalse(PowderRoofTrap.completeWriteBatchSucceeded(12, 11),
                "one false setBlock result prevents encounter success accounting");
        assertFalse(PowderRoofTrap.completeWriteBatchSucceeded(0, 0),
                "an empty batch cannot prove an encounter was written");
    }

    @Test
    void worldWritePlanKeepsShouldersOutOfCushionClearAndShaftPhases() {
        var segment = candidates(plannerInput(3, 8, 3, 4, true, 3)).get(0);
        List<int[]> powder = segment.powder();
        List<int[]> shoulders = segment.shoulders();
        int[][] landing = new int[16][16];
        for (int[] cell : powder) {
            landing[cell[0]][cell[1]] = 80;
        }

        List<PowderRoofTrap.TrapWrite> writes = PowderRoofTrap.concealedWritePlan(
                0, 0, powder, shoulders, landing, 90, true, 5, 4, 3, 70);
        assertTrue(writes != null);
        assertFalse(writes.isEmpty());

        Set<String> powderColumns = coordinates(powder);
        Set<String> shoulderColumns = coordinates(shoulders);
        int solidShoulderWrites = 0;
        for (PowderRoofTrap.TrapWrite write : writes) {
            String column = write.x() + "," + write.z();
            if (write.phase() == PowderRoofTrap.TrapWritePhase.CUSHION
                    || write.phase() == PowderRoofTrap.TrapWritePhase.CLEAR_PATH) {
                assertTrue(powderColumns.contains(column),
                        "all landing, clear-path, and optional throat work stays under powder");
                assertFalse(shoulderColumns.contains(column));
            }
            if (shoulderColumns.contains(column)) {
                assertEquals(PowderRoofTrap.TrapWritePhase.SOLID_ROOF, write.phase(),
                        "a shoulder receives exactly its camouflage roof write and nothing below it");
                solidShoulderWrites++;
            }
        }
        assertEquals(shoulders.size(), solidShoulderWrites);
        assertTrue(PowderRoofTrap.concealedWritePlan(
                0, 0, powder, shoulders, landing, 90, true, 10, 4, 3, 70) == null,
                "a proposed throat crossing the powder edge into a shoulder rejects the complete plan");
    }

    // --- flood fill ------------------------------------------------------------------------------------

    @Test
    void floodFillFindsComponentsInDeterministicOrder() {
        boolean[][] mask = new boolean[16][16];
        // Component A: an L at (2,2),(3,2),(3,3). Component B: a lone cell at (10,10).
        mask[2][2] = true;
        mask[3][2] = true;
        mask[3][3] = true;
        mask[10][10] = true;
        List<List<int[]>> patches = PowderRoofTrap.floodFillPatches(mask);
        assertEquals(2, patches.size());
        assertEquals(3, patches.get(0).size(), "lx-major discovery: the L at (2,2) is found first");
        assertEquals(1, patches.get(1).size());
        assertEquals(10, patches.get(1).get(0)[0]);
    }

    @Test
    void floodFillIsFourConnectedNotEightConnected() {
        boolean[][] mask = new boolean[16][16];
        mask[5][5] = true;
        mask[6][6] = true; // diagonal neighbour only
        assertEquals(2, PowderRoofTrap.floodFillPatches(mask).size(),
                "diagonal cells are separate slots, not one patch");
    }

    @Test
    void floodFillHandlesNullAndEmpty() {
        assertTrue(PowderRoofTrap.floodFillPatches(null).isEmpty());
        assertTrue(PowderRoofTrap.floodFillPatches(new boolean[16][16]).isEmpty());
    }

    @Test
    void floodFillUsesTheTruthfulRectangularBounds() {
        boolean[][] mask = new boolean[48][32];
        mask[15][20] = true;
        mask[16][20] = true;
        mask[47][31] = true;

        List<List<int[]>> patches = PowderRoofTrap.floodFillPatches(mask);
        assertEquals(2, patches.size());
        assertEquals(Set.of("15,20", "16,20"), coordinates(patches.get(0)),
                "a component crossing the old owner edge remains connected");
        assertEquals(Set.of("47,31"), coordinates(patches.get(1)),
                "the final cell of a non-square grid remains addressable");
        assertTrue(PowderRoofTrap.floodFillPatches(
                new boolean[][]{new boolean[2], new boolean[3]}).isEmpty(),
                "a ragged mask has no truthful rectangular bounds");
    }

    // --- deep-first concealed segment ------------------------------------------------------------------

    @Test
    void acceptsPreferredThreeEightThreeAndOptionalFourEightFour() {
        PlannerInput preferred = plannerInput(3, 8, 3, 4, true, 3);
        var preferredSegment = candidates(preferred).get(0);
        assertEquals(8, preferredSegment.powderStations());
        assertEquals(3, preferredSegment.leftShoulderStations());
        assertEquals(3, preferredSegment.rightShoulderStations());
        assertEquals(14, stationSpan(preferredSegment.cover(), preferredSegment.majorX()));
        assertEquals(32, preferredSegment.powder().size());

        PlannerInput optional = plannerInput(4, 8, 4, 4, true, 3);
        var optionalSegment = candidates(optional).get(0);
        assertEquals(4, optionalSegment.leftShoulderStations());
        assertEquals(4, optionalSegment.rightShoulderStations());
        assertEquals(16, stationSpan(optionalSegment.cover(), optionalSegment.majorX()));
        assertTrue(PowderRoofTrap.concealedSegmentEligible(
                optionalSegment.powder(), optionalSegment.shoulders()));
    }

    @Test
    void rectangularPlannerRecoversAFirstBankBeyondTheOldOwnerEdge() {
        PlannerInput shifted = shiftedInput(apronCorridorInput(2, 2), 48, 48, 12, 10);

        var search = PowderRoofTrap.concealedSegmentSearch(
                shifted.deepComponent(), shifted.coverMask(), shifted.depthByColumn());

        assertFalse(search.candidates().isEmpty());
        assertTrue(search.candidates().stream().flatMap(segment -> segment.cover().stream())
                .anyMatch(cell -> cell[0] >= 16 || cell[1] >= 16),
                "the real planner follows the complete contour instead of treating x/z=16 as unknown");
        assertEquals(0, search.traceOwnerEdgeOrUnknown());
    }

    @Test
    void crossPlanCapsAreMonotonicAndCountOtherwiseValidOversizeContours() {
        List<PlannerInput> contours = List.of(
                crossApronCorridorInput(5),
                crossApronCorridorInput(7),
                crossApronCorridorInput(9),
                crossApronCorridorInput(11),
                crossApronCorridorInput(13),
                crossApronCorridorInput(14));
        int[] accepted = new int[5];
        int[] sizeRejected = new int[5];
        int[] caps = {16, 20, 24, 28, 32};
        for (int capIndex = 0; capIndex < caps.length; capIndex++) {
            int apronCap = caps[capIndex] <= 24 ? 12 : 16;
            for (PlannerInput contour : contours) {
                var result = PowderRoofTrap.concealedSegmentSearch(
                        contour.deepComponent(), contour.coverMask(), contour.depthByColumn(),
                        new PowderRoofTrap.CrossPlanOptions(apronCap, caps[capIndex]));
                accepted[capIndex] += result.candidates().size();
                sizeRejected[capIndex] += result.sizeRejectedCandidates().size();
            }
        }

        assertEquals(List.of(1, 2, 3, 4, 5),
                java.util.Arrays.stream(accepted).boxed().toList(),
                "raising the visual cap admits exactly the already-measured smaller contours");
        assertEquals(List.of(5, 4, 3, 2, 1),
                java.util.Arrays.stream(sizeRejected).boxed().toList());
        assertTrue(PowderRoofTrap.concealedSegmentSearch(
                contours.get(5).deepComponent(), contours.get(5).coverMask(),
                contours.get(5).depthByColumn(), new PowderRoofTrap.CrossPlanOptions(16, 32))
                .sizeRejectedCandidates().stream()
                .allMatch(segment -> stationSpan(segment.cover(), !segment.majorX()) > 32),
                "a >32 first-bank contour is named as a visual-size reject, never cropped");
    }

    @Test
    void overlappingWindowsProduceOneWorldCoordinateCanonicalIdentity() {
        PlannerInput firstWindow = crossApronCorridorInput(9);
        var firstSegment = PowderRoofTrap.concealedSegmentSearch(
                firstWindow.deepComponent(), firstWindow.coverMask(), firstWindow.depthByColumn(),
                new PowderRoofTrap.CrossPlanOptions(12, 24)).candidates().get(0);
        PlannerInput overlappingWindow = shiftedInput(firstWindow, 56, 56, 4, 4);
        var overlappingSegment = PowderRoofTrap.concealedSegmentSearch(
                overlappingWindow.deepComponent(), overlappingWindow.coverMask(),
                overlappingWindow.depthByColumn(),
                new PowderRoofTrap.CrossPlanOptions(12, 24)).candidates().get(0);

        var firstIdentity = PowderRoofTrap.crossPlanIdentity(-16, -16, firstSegment);
        var overlappingIdentity = PowderRoofTrap.crossPlanIdentity(-20, -20, overlappingSegment);
        assertEquals(firstIdentity.canonicalKey(), overlappingIdentity.canonicalKey(),
                "local coordinate shifts disappear from the world-coordinate material partition");
        assertEquals(firstIdentity.midpointWorldX(), overlappingIdentity.midpointWorldX());
        assertEquals(firstIdentity.midpointWorldZ(), overlappingIdentity.midpointWorldZ());
        assertTrue(firstIdentity.crossChunk());
    }

    @Test
    void pureCrossPlanSummaryCountsOnlyMidpointOwnedUniquePlansWithoutWorldWrites() {
        PlannerInput input = crossApronCorridorInput(9);
        boolean[][] deepMask = new boolean[48][48];
        for (int[] cell : input.deepComponent()) {
            deepMask[cell[0]][cell[1]] = true;
        }

        var summaries = PowderRoofTrap.crossPlanSummaries(
                deepMask, input.coverMask(), input.depthByColumn(),
                -16, -16, 0, 0, 12, 16, 20, 24);

        assertEquals(List.of(0, 0, 1), summaries.stream()
                .map(PowderRoofTrap.CrossPlanCapSummary::potentialPlans).toList());
        assertEquals(List.of(1, 1, 0), summaries.stream()
                .map(PowderRoofTrap.CrossPlanCapSummary::sizeRejects).toList());
        assertEquals(32, summaries.get(2).powderColumns());
        assertEquals(276, summaries.get(2).solidColumns());
        assertEquals(1, summaries.get(2).crossChunkPlans());
        assertEquals(1, summaries.get(2).selectedPlans());
        assertEquals(32, summaries.get(2).selectedPowderColumns());
        assertEquals(276, summaries.get(2).selectedSolidColumns());
        assertEquals(3, summaries.get(2).selectedTouchedChunks(),
                "the 24-wide shifted contour reaches three Z chunks in this fixture");
        assertTrue(PowderRoofTrap.crossPlanSummaries(
                deepMask, input.coverMask(), input.depthByColumn(),
                -16, -16, 99, 99, 12, 24).get(0).potentialPlans() == 0,
                "the same read-only arrays cannot report the plan from a non-midpoint owner");
    }

    @Test
    void selectedCrossPlanCollapsesManyAlternativesToOnePerDeepComponent() {
        PlannerInput input = splitMergeCorridorInput();
        boolean[][] deepMask = new boolean[16][16];
        for (int[] cell : input.deepComponent()) {
            deepMask[cell[0]][cell[1]] = true;
        }

        var summary = PowderRoofTrap.crossPlanSummaries(
                deepMask, input.coverMask(), input.depthByColumn(),
                0, 0, 0, 0, 12, 16).get(0);
        var ranked = PowderRoofTrap.concealedSegmentSearch(
                input.deepComponent(), input.coverMask(), input.depthByColumn(),
                new PowderRoofTrap.CrossPlanOptions(12, 16)).candidates();

        assertTrue(summary.potentialPlans() > 1,
                "the split/merge component deliberately exposes many exact partition alternatives");
        assertEquals(1, summary.selectedPlans(),
                "incidence projection takes only the first ranked accepted plan for the component");
        assertEquals(ranked.get(0).powder().size(), summary.selectedPowderColumns());
        assertEquals(ranked.get(0).shoulders().size(), summary.selectedSolidColumns());
        assertEquals(1, summary.selectedTouchedChunks());
    }

    @Test
    void acceptsBothAsymmetricOptionalShoulderVariants() {
        var leftShort = candidates(plannerInput(3, 8, 4, 4, true, 3)).get(0);
        assertEquals(3, leftShort.leftShoulderStations());
        assertEquals(4, leftShort.rightShoulderStations());
        assertEquals(15, stationSpan(leftShort.cover(), leftShort.majorX()));

        var rightShort = candidates(plannerInput(4, 8, 3, 4, true, 3)).get(0);
        assertEquals(4, rightShort.leftShoulderStations());
        assertEquals(3, rightShort.rightShoulderStations());
        assertEquals(15, stationSpan(rightShort.cover(), rightShort.majorX()));
    }

    @Test
    void acceptsExactThreeSixThreeFallbackAtEighteenPowderColumns() {
        PlannerInput fallback = plannerInput(3, 6, 3, 3, true, 4);
        var segment = candidates(fallback).get(0);
        assertEquals(6, segment.powderStations());
        assertEquals(18, segment.powder().size());
        assertEquals(3, segment.leftShoulderStations());
        assertEquals(3, segment.rightShoulderStations());
        assertEquals(12, stationSpan(segment.cover(), segment.majorX()));
    }

    @Test
    void rawEightStationPathDoesNotSuppressValidFallbackInTwelveStationDeepCorridor() {
        PlannerInput twelveDeepStations = deepCorridorInput(12, false);
        var search = PowderRoofTrap.concealedSegmentSearch(
                twelveDeepStations.deepComponent(),
                twelveDeepStations.coverMask(),
                twelveDeepStations.depthByColumn());

        assertEquals(0, search.preferredCandidateCount(),
                "three plus eight plus three cannot fit inside twelve stations");
        assertEquals(1, search.fallbackCandidateCount(),
                "a raw eight-station path is not an accepted preferred segment");
        var fallback = search.candidates().get(0);
        assertEquals(6, fallback.powderStations());
        assertEquals(3, fallback.leftShoulderStations());
        assertEquals(3, fallback.rightShoulderStations());
        assertEquals(12, stationSpan(fallback.cover(), fallback.majorX()));
        assertTrue(coordinates(twelveDeepStations.deepComponent())
                .containsAll(coordinates(fallback.cover())),
                "the fallback remains a bounded segment of the truthful all-deep component");
    }

    @Test
    void preferredEightStationsOutrankMuchDeeperSixStationFallback() {
        PlannerInput preferredInput = plannerInput(3, 8, 3, 4, true, 1);
        PlannerInput fallbackInput = plannerInput(3, 6, 3, 3, true, 10);
        var preferred = candidates(preferredInput).get(0);
        var fallback = candidates(fallbackInput).get(0);
        int[][] depth = new int[16][16];
        for (int[] cell : preferred.powder()) {
            depth[cell[0]][cell[1]] = 10;
        }
        for (int[] cell : fallback.powder()) {
            depth[cell[0]][cell[1]] = 50;
        }
        var ranked = PowderRoofTrap.rankConcealedSegments(List.of(fallback, preferred), depth);
        assertEquals(8, ranked.get(0).powderStations(),
                "preferred eight-station drama outranks a deeper six-station incidence fallback");
    }

    @Test
    void safePreferredCoreMayExceedThirtySixColumns() {
        PlannerInput wide = plannerInput(3, 8, 3, 5, true, 3);
        var segment = candidates(wide).get(0);
        assertEquals(40, segment.powder().size());
        assertTrue(PowderRoofTrap.powderCoreEligible(segment.powder()),
                "24..36 is a ranking preference, not a rejection ceiling");
    }

    @Test
    void selectsBoundedPreferredSegmentsInsideFourteenAndSixteenStationDeepFissures() {
        PlannerInput fourteen = deepCorridorInput(14, false);
        var fourteenSegment = candidates(fourteen).get(0);
        assertEquals(8, fourteenSegment.powderStations());
        assertEquals(3, fourteenSegment.leftShoulderStations());
        assertEquals(3, fourteenSegment.rightShoulderStations());
        assertEquals(14, stationSpan(fourteenSegment.cover(), fourteenSegment.majorX()));
        assertTrue(fourteenSegment.shoulders().stream()
                .allMatch(c -> fourteen.depthByColumn()[c[0]][c[1]] >= 10),
                "solid camouflage shoulders may cover the same long deep fissure");

        PlannerInput sixteen = deepCorridorInput(16, false);
        var sixteenSegment = candidates(sixteen).get(0);
        assertEquals(8, sixteenSegment.powderStations());
        assertEquals(4, sixteenSegment.leftShoulderStations());
        assertEquals(4, sixteenSegment.rightShoulderStations());
        assertEquals(16, stationSpan(sixteenSegment.cover(), sixteenSegment.majorX()));
        assertTrue(coordinates(sixteen.deepComponent()).containsAll(coordinates(sixteenSegment.cover())),
                "the bounded cover is selected wholly from the supplied complete deep component");
    }

    @Test
    void branchEnumerationKeepsAValidMaximalRunCorridorWithoutShavingIt() {
        PlannerInput branched = deepCorridorInput(16, true);
        var segment = candidates(branched).get(0);
        assertEquals(8, segment.powderStations());
        assertEquals(7, segment.powder().stream().filter(c -> c[0] == 7).count(),
                "the chosen broadened station keeps its complete maximal seven-cell run");
        for (int z = 4; z <= 10; z++) {
            assertTrue(coordinates(segment.powder()).contains("7," + z));
        }
        assertFalse(coordinates(segment.cover()).contains("8,10"),
                "the dead-end branch is not allowed to poison or replace the continuing corridor path");
        assertTrue(PowderRoofTrap.concealedSegmentEligible(
                segment.powder(), segment.shoulders()));
    }

    @Test
    void repeatedSplitMergeEnumerationIsDeterministicAndStructurallyBounded() {
        PlannerInput splitMerge = splitMergeCorridorInput();
        var first = PowderRoofTrap.concealedSegmentSearch(
                splitMerge.deepComponent(), splitMerge.coverMask(), splitMerge.depthByColumn());
        var second = PowderRoofTrap.concealedSegmentSearch(
                splitMerge.deepComponent(), splitMerge.coverMask(), splitMerge.depthByColumn());

        // Eight split stations have two choices; merge stations have one. Across the legal
        // 14/15/16-station partitions this fixture can produce at most 1408 complete paths:
        // 3*2^7 + 2*(2^7+2^8) + 2^8. This is a geometry-derived bound, not a search cap.
        int structuralBound = 3 * 128 + 2 * (128 + 256) + 256;
        assertFalse(first.candidates().isEmpty());
        assertEquals(structuralBound, first.candidates().size(),
                "every complete split/merge path is retained exactly once within the structural bound");
        assertEquals(first.candidates().size(), first.preferredCandidateCount());
        assertEquals(0, first.fallbackCandidateCount(),
                "accepted preferred corridors suppress fallback only after full validation");
        assertEquals(candidateKeys(first.candidates()), candidateKeys(second.candidates()),
                "the same complete paths retain deterministic order across repeated searches");
    }

    @Test
    void twoCellLateralApronsReachBothBanksAndRemainSolidInPowderStations() {
        PlannerInput input = apronCorridorInput(2, 2);
        var segment = candidates(input).get(0);
        assertTrue(segment.majorX());

        assertTrue(PowderRoofTrap.hasOpposingLongSideBankCoverage(
                segment.cover(), input.coverMask(), segment.majorX()),
                "the augmented cover reaches a real bank on every long-side station");
        int powderStart = segment.powder().stream()
                .mapToInt(c -> segment.majorX() ? c[0] : c[1]).min().orElseThrow();
        int powderEnd = segment.powder().stream()
                .mapToInt(c -> segment.majorX() ? c[0] : c[1]).max().orElseThrow();
        Set<String> shoulderCells = coordinates(segment.shoulders());
        Set<String> middleAprons = new HashSet<>();
        for (int along = powderStart; along <= powderEnd; along++) {
            int powderLow = stationMin(segment.powder(), along);
            int powderHigh = stationMax(segment.powder(), along);
            int coverLow = stationMin(segment.cover(), along);
            int coverHigh = stationMax(segment.cover(), along);
            assertEquals(2, powderLow - coverLow);
            assertEquals(2, coverHigh - powderHigh);
            for (int cross = coverLow; cross < powderLow; cross++) {
                middleAprons.add(along + "," + cross);
            }
            for (int cross = powderHigh + 1; cross <= coverHigh; cross++) {
                middleAprons.add(along + "," + cross);
            }
        }
        assertTrue(shoulderCells.containsAll(middleAprons),
                "middle-station aprons are solid shoulders, never powder");
        assertTrue(PowderRoofTrap.concealedSegmentEligible(
                segment.powder(), segment.shoulders()));

        int[][] landing = new int[16][16];
        for (int[] cell : segment.powder()) {
            landing[cell[0]][cell[1]] = 80;
        }
        List<PowderRoofTrap.TrapWrite> writes = PowderRoofTrap.concealedWritePlan(
                0, 0, segment.powder(), segment.shoulders(), landing,
                90, false, 0, 0, 0, 0);
        assertTrue(writes != null);
        int solidApronWrites = 0;
        for (PowderRoofTrap.TrapWrite write : writes) {
            if (middleAprons.contains(write.x() + "," + write.z())) {
                assertEquals(PowderRoofTrap.TrapWritePhase.SOLID_ROOF, write.phase(),
                        "an apron can never enter cushion, clear-path, shaft, or powder phases");
                solidApronWrites++;
            }
        }
        assertEquals(middleAprons.size(), solidApronWrites,
                "every lateral apron cell receives exactly one solid camouflage write");
    }

    @Test
    void mixedZeroAndOneCellNaturalContourPreservesExactPartitionAndScannerParity() {
        PlannerInput input = naturalContourCorridorInput();
        var segment = candidates(input).get(0);
        assertTrue(segment.majorX());
        assertEquals(coordinates(input.coverMask()), coordinates(segment.cover()),
                "first-bank tracing keeps each station's measured contour without smoothing");

        int powderStart = segment.powder().stream().mapToInt(c -> c[0]).min().orElseThrow();
        int powderEnd = segment.powder().stream().mapToInt(c -> c[0]).max().orElseThrow();
        Set<String> middleAprons = new HashSet<>();
        boolean sawZero = false;
        boolean sawOne = false;
        for (int along = input.firstStation(); along < input.firstStation() + 14; along++) {
            int lowDistance = stationMin(input.deepComponent(), along)
                    - stationMin(segment.cover(), along);
            int highDistance = stationMax(segment.cover(), along)
                    - stationMax(input.deepComponent(), along);
            sawZero |= lowDistance == 0 || highDistance == 0;
            sawOne |= lowDistance == 1 || highDistance == 1;
            if (along >= powderStart && along <= powderEnd) {
                for (int cross = stationMin(segment.cover(), along);
                        cross < stationMin(segment.powder(), along); cross++) {
                    middleAprons.add(along + "," + cross);
                }
                for (int cross = stationMax(segment.powder(), along) + 1;
                        cross <= stationMax(segment.cover(), along); cross++) {
                    middleAprons.add(along + "," + cross);
                }
            }
        }
        assertTrue(sawZero, "an immediate first bank is a truthful zero-distance contour");
        assertTrue(sawOne, "a one-cell opening is filled exactly rather than rejected or widened");

        int[][] landing = new int[16][16];
        for (int[] cell : segment.powder()) {
            landing[cell[0]][cell[1]] = 80;
        }
        List<PowderRoofTrap.TrapWrite> writes = PowderRoofTrap.concealedWritePlan(
                0, 0, segment.powder(), segment.shoulders(), landing,
                90, false, 0, 0, 0, 0);
        assertTrue(writes != null);
        Set<String> solidApronWrites = new HashSet<>();
        for (PowderRoofTrap.TrapWrite write : writes) {
            String column = write.x() + "," + write.z();
            if (middleAprons.contains(column)) {
                assertEquals(PowderRoofTrap.TrapWritePhase.SOLID_ROOF, write.phase());
                solidApronWrites.add(column);
            }
        }
        assertEquals(middleAprons, solidApronWrites,
                "mixed-contour aprons remain solid camouflage only");

        boolean[][] powderMask = new boolean[16][16];
        boolean[][] shoulderMask = new boolean[16][16];
        int[][] roofY = new int[16][16];
        for (int[] cell : segment.powder()) {
            powderMask[cell[0]][cell[1]] = true;
            roofY[cell[0]][cell[1]] = 90;
        }
        for (int[] cell : segment.shoulders()) {
            shoulderMask[cell[0]][cell[1]] = true;
            roofY[cell[0]][cell[1]] = 90;
        }
        var scanned = GlacialMarkScan.concealedRoofComponents(powderMask, shoulderMask, roofY);
        assertEquals(1, scanned.size());
        assertEquals(coordinates(segment.powder()), coordinates(scanned.get(0).powder()));
        assertEquals(coordinates(segment.shoulders()), coordinates(scanned.get(0).shoulders()),
                "generation and scanner recover the same exact material partition");
    }

    @Test
    void allZeroImmediateBankContourRemainsValid() {
        PlannerInput input = deepCorridorInput(14, false);
        var segment = candidates(input).get(0);
        assertEquals(coordinates(input.coverMask()), coordinates(segment.cover()));
        assertTrue(PowderRoofTrap.hasOpposingLongSideBankCoverage(
                segment.cover(), input.coverMask(), segment.majorX()));
    }

    @Test
    void convergentPreApronShoulderBranchesCountAsOneAugmentedCandidate() {
        PlannerInput input = convergentApronShoulderBranchesInput();
        var result = PowderRoofTrap.concealedSegmentSearch(
                input.deepComponent(), input.coverMask(), input.depthByColumn());

        assertEquals(1, result.candidates().size(),
                "two spine branches that expand to the same material partition are one candidate");
        assertEquals(1, result.preferredCandidateCount(),
                "preferred accounting follows unique augmented candidates, not pre-apron branches");
        assertEquals(0, result.fallbackCandidateCount());
        var segment = result.candidates().get(0);
        assertTrue(segment.majorX());
        assertTrue(PowderRoofTrap.hasOpposingLongSideBankCoverage(
                segment.cover(), input.coverMask(), segment.majorX()));
        assertEquals(14, segment.cover().stream()
                .filter(cell -> cell[0] == input.firstStation() + 2).count(),
                "both split shoulder branches converge on the same complete bank-to-bank station");
    }

    @Test
    void nineCellLateralApronIsAcceptedAtTheOwnerBound() {
        PlannerInput input = fixedBankApronCorridorInput();
        var segment = candidates(input).get(0);
        assertTrue(segment.majorX());
        int maximumApron = 0;
        for (int along = input.firstStation(); along < input.firstStation() + 14; along++) {
            maximumApron = Math.max(maximumApron,
                    stationMin(input.deepComponent(), along) - stationMin(segment.cover(), along));
            maximumApron = Math.max(maximumApron,
                    stationMax(segment.cover(), along) - stationMax(input.deepComponent(), along));
        }
        assertEquals(9, maximumApron);
        assertTrue(PowderRoofTrap.hasOpposingLongSideBankCoverage(
                segment.cover(), input.coverMask(), segment.majorX()));
    }

    @Test
    void tenCellOrMissingInChunkBankRejectsTheApronCandidate() {
        PlannerInput ten = apronCorridorInput(2, 2);
        int station = ten.firstStation();
        int formerHigh = stationMax(ten.deepComponent(), station);
        ten.deepComponent().removeIf(c -> c[0] == station && c[1] == formerHigh);
        ten.depthByColumn()[station][formerHigh] = 2;
        for (int z = stationMax(ten.deepComponent(), station) + 1; z < 16; z++) {
            ten.coverMask()[station][z] = true;
            ten.depthByColumn()[station][z] = 2;
        }
        assertEquals(10, 15 - stationMax(ten.deepComponent(), station),
                "the rejection fixture must exercise ten contiguous lateral cells");
        var tenSearch = PowderRoofTrap.concealedSegmentSearch(
                ten.deepComponent(), ten.coverMask(), ten.depthByColumn());
        assertTrue(tenSearch.candidates().isEmpty(),
                "ten contiguous lateral cells exceed the apron bound and must not be cropped");
        assertTrue(tenSearch.traceOver9() > 0,
                "the census distinguishes an over-nine trace from an unknown bank");
        assertPostSpineDiagnosticsAreExclusiveSubcounts(tenSearch);
        assertEquals(candidateKeys(tenSearch.candidates()), candidateKeys(
                PowderRoofTrap.concealedSegmentSearch(
                        ten.deepComponent(), ten.coverMask(), ten.depthByColumn()).candidates()),
                "diagnostics do not perturb the rejected candidate list or its order");

        PlannerInput missingBank = apronCorridorInput(2, 2);
        int missingStation = missingBank.firstStation() + 1;
        for (int z = 0; z < stationMin(missingBank.deepComponent(), missingStation); z++) {
            missingBank.coverMask()[missingStation][z] = true;
            missingBank.depthByColumn()[missingStation][z] = 2;
        }
        var missingBankSearch = PowderRoofTrap.concealedSegmentSearch(
                missingBank.deepComponent(), missingBank.coverMask(), missingBank.depthByColumn());
        assertTrue(missingBankSearch.candidates().isEmpty(),
                "a depressed run reaching the chunk edge has no truthful in-chunk bank");
        assertTrue(missingBankSearch.traceOwnerEdgeOrUnknown() > 0,
                "the census distinguishes an owner edge or unread cell from an over-nine trace");
        assertPostSpineDiagnosticsAreExclusiveSubcounts(missingBankSearch);
        assertEquals(candidateKeys(missingBankSearch.candidates()), candidateKeys(
                PowderRoofTrap.concealedSegmentSearch(missingBank.deepComponent(),
                        missingBank.coverMask(), missingBank.depthByColumn()).candidates()),
                "diagnostics do not perturb the rejected candidate list or its order");
    }

    @Test
    void augmentedContourStaggerHasItsOwnDiagnosticWithoutRelaxingTheShapeLaw() {
        PlannerInput rectangleAfterTrace = augmentedRectangleCorridorInput();
        var search = PowderRoofTrap.concealedSegmentSearch(
                rectangleAfterTrace.deepComponent(), rectangleAfterTrace.coverMask(),
                rectangleAfterTrace.depthByColumn());

        assertTrue(search.candidates().isEmpty(),
                "first-bank augmentation that turns the natural spine into a rectangle still rejects");
        assertTrue(search.augmentedStagger() > 0,
                "post-trace stagger loss has a distinct census owner");
        assertEquals(0, search.augmentedOtherShape(),
                "the current successful-trace invariants leave no other truthful shape failure here");
        assertPostSpineDiagnosticsAreExclusiveSubcounts(search);
        assertEquals(candidateKeys(search.candidates()), candidateKeys(
                PowderRoofTrap.concealedSegmentSearch(rectangleAfterTrace.deepComponent(),
                        rectangleAfterTrace.coverMask(),
                        rectangleAfterTrace.depthByColumn()).candidates()),
                "diagnostics do not perturb the rejected candidate list or its order");
    }

    @Test
    void overwideAndDisconnectedDeepCorridorsStillReject() {
        PlannerInput overwide = deepCorridorInput(16, false);
        for (int z = 8; z <= 11; z++) {
            addDeepCell(overwide, 7, z);
        }
        assertTrue(candidates(overwide).isEmpty(),
                "an eight-wide maximal station cannot be cropped to the seven-wide corridor law");

        PlannerInput disconnected = deepCorridorInput(14, false);
        addDeepCell(disconnected, 15, 15);
        assertTrue(candidates(disconnected).isEmpty(),
                "the supplied deep component must remain one truthful connected component");
    }

    @Test
    void boundedShouldersIgnoreUnrelatedShallowFieldButRejectAnOverwideTouchingBasin() {
        PlannerInput bounded = plannerInput(3, 8, 3, 4, true, 3);
        for (int x = 0; x < 16; x++) {
            for (int z = 12; z <= 14; z++) {
                bounded.coverMask()[x][z] = true;
            }
        }
        var segment = candidates(bounded).get(0);
        assertTrue(segment.cover().stream().noneMatch(c -> c[1] >= 12),
                "non-touching shallow terrain is not absorbed through a global flood");

        PlannerInput basin = plannerInput(3, 8, 3, 4, true, 3);
        int stationBeforePowder = basin.firstStation() + basin.leftShoulders() - 1;
        for (int z = 0; z < 16; z++) {
            basin.coverMask()[stationBeforePowder][z] = true;
        }
        assertTrue(candidates(basin).isEmpty(),
                "a maximal touching run wider than the certified corridor is rejected, never cropped");
    }

    @Test
    void searchDiagnosticsSeparateCoreShoulderShareAndStaggerOutcomes() {
        PlannerInput preferred = plannerInput(3, 8, 3, 4, true, 3);
        var preferredSearch = PowderRoofTrap.concealedSegmentSearch(
                preferred.deepComponent(), preferred.coverMask(), preferred.depthByColumn());
        assertTrue(preferredSearch.preferredCandidateCount() > 0);
        assertEquals(0, preferredSearch.fallbackCandidateCount());
        assertPostSpineDiagnosticsAreZero(preferredSearch);
        assertEquals(candidateKeys(preferredSearch.candidates()), candidateKeys(
                PowderRoofTrap.concealedSegmentSearch(preferred.deepComponent(),
                        preferred.coverMask(), preferred.depthByColumn()).candidates()),
                "accepted preferred candidates retain deterministic order");

        PlannerInput fallback = plannerInput(3, 6, 3, 3, true, 3);
        var fallbackSearch = PowderRoofTrap.concealedSegmentSearch(
                fallback.deepComponent(), fallback.coverMask(), fallback.depthByColumn());
        assertTrue(fallbackSearch.fallbackCandidateCount() > 0);
        assertEquals(0, fallbackSearch.preferredCandidateCount());
        assertPostSpineDiagnosticsAreZero(fallbackSearch);

        PlannerInput missing = plannerInput(3, 8, 3, 4, true, 3);
        clearStation(missing.coverMask(), missing.firstStation());
        assertTrue(PowderRoofTrap.concealedSegmentSearch(
                missing.deepComponent(), missing.coverMask(), missing.depthByColumn())
                .shoulderMissingRejects() > 0);

        PlannerInput basin = plannerInput(3, 8, 3, 4, true, 3);
        int beforePowder = basin.firstStation() + basin.leftShoulders() - 1;
        java.util.Arrays.fill(basin.coverMask()[beforePowder], true);
        assertTrue(PowderRoofTrap.concealedSegmentSearch(
                basin.deepComponent(), basin.coverMask(), basin.depthByColumn())
                .shoulderAmbiguousRejects() > 0);

        PlannerInput lowShare = variableWidthInput(7, 3);
        assertTrue(PowderRoofTrap.concealedSegmentSearch(
                lowShare.deepComponent(), lowShare.coverMask(), lowShare.depthByColumn())
                .powderShareRejects() > 0);

        PlannerInput rectangle = plannerInput(3, 8, 3, 4, false, 3);
        assertTrue(PowderRoofTrap.concealedSegmentSearch(
                rectangle.deepComponent(), rectangle.coverMask(), rectangle.depthByColumn())
                .staggerRejects() > 0);
    }

    @Test
    void materialBoundaryShiftsDoNotSubstituteForInternalShoulderStagger() {
        PlannerInput seamOnly = seamOnlyStaggerInput();
        var rejected = PowderRoofTrap.concealedSegmentSearch(
                seamOnly.deepComponent(), seamOnly.coverMask(), seamOnly.depthByColumn());
        assertTrue(rejected.candidates().isEmpty(),
                "straight shoulder interiors stay authored-looking even when both powder seams shift");
        assertTrue(rejected.staggerRejects() > 0);

        PlannerInput internalBothEnds = plannerInput(3, 8, 3, 4, true, 3);
        var accepted = candidates(internalBothEnds);
        assertFalse(accepted.isEmpty());
        assertTrue(PowderRoofTrap.hasNaturalShoulderStagger(accepted.get(0).cover()),
                "an edge shift within each solid shoulder remains a valid natural taper");
    }

    @Test
    void rejectsMissingTwoStationAndOneEndedShoulders() {
        PlannerInput twoLeft = plannerInput(3, 8, 3, 4, true, 3);
        clearStation(twoLeft.coverMask(), twoLeft.firstStation());
        assertTrue(candidates(twoLeft).isEmpty(), "two left stations cannot satisfy the three-station minimum");

        PlannerInput oneEnded = plannerInput(3, 8, 3, 4, true, 3);
        for (int x = oneEnded.firstStation() + oneEnded.leftShoulders() + 8;
                x < oneEnded.firstStation() + 14; x++) {
            clearStation(oneEnded.coverMask(), x);
        }
        assertTrue(candidates(oneEnded).isEmpty(), "a complete powder core never receives a one-ended cover");
    }

    @Test
    void rejectsPowderBelowHalfOfConcealedCellsAndBelowEighteenColumns() {
        PlannerInput lowShare = variableWidthInput(7, 3);
        assertTrue(candidates(lowShare).isEmpty(),
                "wide solid approaches cannot dilute the powder hazard below half of concealed cells");
        assertFalse(PowderRoofTrap.powderCoreEligible(rectangle(5, 10, 5, 6)),
                "a two-wide six-station core has only twelve cells and stays invalid");
    }

    @Test
    void rejectsCleanRectanglesHolesInventedProvenanceAndOutOfOwnerCells() {
        PlannerInput rectangle = plannerInput(3, 8, 3, 4, false, 3);
        assertTrue(candidates(rectangle).isEmpty(), "a clean rectangular platform is visibly authored");

        PlannerInput holed = plannerInput(3, 8, 3, 4, true, 3);
        int[] removed = holed.deepComponent().get(holed.deepComponent().size() / 2);
        holed.deepComponent().removeIf(c -> c[0] == removed[0] && c[1] == removed[1]);
        assertTrue(candidates(holed).isEmpty(), "a powder station with an interior hole is not repaired");

        PlannerInput provenance = plannerInput(3, 8, 3, 4, true, 3);
        int[] deepCell = provenance.deepComponent().get(0);
        provenance.coverMask()[deepCell[0]][deepCell[1]] = false;
        assertTrue(candidates(provenance).isEmpty(), "powder cells must also exist in the local cover mask");

        PlannerInput outside = plannerInput(3, 8, 3, 4, true, 3);
        outside.deepComponent().add(new int[]{16, 6});
        assertTrue(candidates(outside).isEmpty(), "an out-of-owner cell rejects the complete local plan");
    }

    @Test
    void plannerIsDeterministicAndReturnedPartitionsRemainImmutable() {
        PlannerInput input = apronCorridorInput(2, 2);
        var original = candidates(input).get(0);
        List<int[]> shuffled = new ArrayList<>(input.deepComponent());
        Collections.shuffle(shuffled, new java.util.Random(987654L));
        var reordered = PowderRoofTrap.concealedSegmentCandidates(
                shuffled, input.coverMask(), input.depthByColumn()).get(0);
        assertEquals(coordinates(original.cover()), coordinates(reordered.cover()));
        assertEquals(coordinates(original.powder()), coordinates(reordered.powder()));
        assertEquals(coordinates(original.shoulders()), coordinates(reordered.shoulders()));

        Set<String> powderBefore = coordinates(original.powder());
        Set<String> shouldersBefore = coordinates(original.shoulders());
        Set<String> coverBefore = coordinates(original.cover());
        List<int[]> exposedPowder = original.powder();
        List<int[]> exposedShoulders = original.shoulders();
        List<int[]> exposedCover = original.cover();
        exposedPowder.get(0)[0] = 99;
        exposedShoulders.get(0)[0] = 99;
        exposedCover.get(0)[0] = 99;
        assertEquals(powderBefore, coordinates(original.powder()));
        assertEquals(shouldersBefore, coordinates(original.shoulders()));
        assertEquals(coverBefore, coordinates(original.cover()));
        assertThrows(UnsupportedOperationException.class,
                () -> exposedPowder.add(new int[]{1, 1}));
        assertThrows(UnsupportedOperationException.class,
                () -> exposedShoulders.add(new int[]{1, 1}));
        assertThrows(UnsupportedOperationException.class,
                () -> exposedCover.add(new int[]{1, 1}));
    }

    @Test
    void oneNineDeepPowderCellRejectsWithoutWeakeningTheTenBlockFloor() {
        PlannerInput input = plannerInput(3, 8, 3, 4, true, 3);
        int[] cell = input.deepComponent().get(input.deepComponent().size() / 2);
        input.depthByColumn()[cell[0]][cell[1]] = 9;
        assertTrue(candidates(input).isEmpty());
    }

    @Test
    void longSideCoverageRejectsAOneSidedChunkEdgeCap() {
        boolean[][] candidate = new boolean[16][16];
        List<int[]> edgeCap = new ArrayList<>();
        for (int lx = 0; lx <= 15; lx++) {
            for (int lz = 12; lz <= 15; lz++) {
                candidate[lx][lz] = true;
                edgeCap.add(new int[]{lx, lz});
            }
        }
        assertFalse(PowderRoofTrap.hasOpposingLongSideBankCoverage(edgeCap, candidate),
                "north plus short end contacts cannot substitute for the unknown off-chunk south bank");

        boolean[][] centredCandidate = new boolean[16][16];
        List<int[]> centredCap = meanderingComponent();
        for (int[] cell : centredCap) {
            centredCandidate[cell[0]][cell[1]] = true;
        }
        assertTrue(PowderRoofTrap.hasOpposingLongSideBankCoverage(centredCap, centredCandidate),
                "the same long run is credible when both terrain-side banks cover every station");

        boolean[][] meanderCandidate = new boolean[16][16];
        List<int[]> meander = meanderingComponent();
        for (int[] cell : meander) {
            meanderCandidate[cell[0]][cell[1]] = true;
        }
        assertTrue(PowderRoofTrap.hasOpposingLongSideBankCoverage(meander, meanderCandidate),
                "long-side coverage follows each natural station instead of flattening the meander");
    }

    @Test
    void walkableLongSideCoverageMatchesScannerSemanticsAtTheEightyPercentGate() {
        boolean[][] candidate = new boolean[16][16];
        int[][] surfaceFirstAir = new int[16][16];
        List<int[]> cover = meanderingComponent();
        for (int[] cell : cover) {
            candidate[cell[0]][cell[1]] = true;
        }
        assertTrue(PowderRoofTrap.hasOpposingLongSideBankCoverage(cover, candidate),
                "the structural seam exists along all sixteen stations");

        int coverFirstAir = 100;
        List<int[]> twoPerSide = new ArrayList<>();
        for (int lx = 0; lx < 2; lx++) {
            int lowZ = stationMin(cover, lx);
            int highZ = stationMax(cover, lx);
            surfaceFirstAir[lx][lowZ - 1] = coverFirstAir;
            surfaceFirstAir[lx][highZ + 1] = coverFirstAir;
            twoPerSide.add(new int[]{lx, lowZ - 1});
            twoPerSide.add(new int[]{lx, highZ + 1});
        }
        assertEquals(99, PowderRoofTrap.selectSnowfieldRoofY(
                List.of(coverFirstAir), List.of(coverFirstAir, coverFirstAir),
                List.of(coverFirstAir, coverFirstAir), List.of(), List.of()),
                "two samples explain why the old plane selector alone false-greened this shape");
        boolean generatorTwo = PowderRoofTrap.hasOpposingLongSideWalkableBankCoverage(
                cover, candidate, surfaceFirstAir, coverFirstAir);
        boolean scannerTwo = PowderRoofTrap.hasOpposingLongSideBankContacts(cover, twoPerSide);
        assertFalse(generatorTwo, "two walkable contacts per side are far below 80%");
        assertEquals(scannerTwo, generatorTwo, "generation and scanner use the same contact semantics");

        List<int[]> thirteenPerSide = new ArrayList<>();
        for (int lx = 0; lx < 13; lx++) {
            int lowZ = stationMin(cover, lx);
            int highZ = stationMax(cover, lx);
            surfaceFirstAir[lx][lowZ - 1] = coverFirstAir;
            surfaceFirstAir[lx][highZ + 1] = coverFirstAir;
            thirteenPerSide.add(new int[]{lx, lowZ - 1});
            thirteenPerSide.add(new int[]{lx, highZ + 1});
        }
        boolean generatorThirteen = PowderRoofTrap.hasOpposingLongSideWalkableBankCoverage(
                cover, candidate, surfaceFirstAir, coverFirstAir);
        boolean scannerThirteen = PowderRoofTrap.hasOpposingLongSideBankContacts(cover, thirteenPerSide);
        assertTrue(generatorThirteen, "13/16 contacts per side clears the 80% gate");
        assertEquals(scannerThirteen, generatorThirteen, "both paths agree at the passing boundary");
    }

    @Test
    void eightyPercentBankGateScalesAcrossEveryTwelveToSixteenStationCover() {
        for (int stations = 12; stations <= 16; stations++) {
            PlannerInput input = switch (stations) {
                case 12 -> plannerInput(3, 6, 3, 3, true, 3);
                case 13 -> plannerInputWithWidths(3, 6, 4, 3, 4, true, 3);
                case 14 -> plannerInput(3, 8, 3, 3, true, 3);
                case 15 -> plannerInput(3, 8, 4, 3, true, 3);
                case 16 -> plannerInput(4, 8, 4, 3, true, 3);
                default -> throw new AssertionError(stations);
            };
            var segment = candidates(input).get(0);
            int passingContacts = (stations * PowderRoofTrap.MIN_LONG_SIDE_BANK_COVERAGE_PERCENT + 99) / 100;
            assertTrue(PowderRoofTrap.hasOpposingLongSideBankContacts(
                    segment.cover(), bankContacts(segment.cover(), segment.majorX(), passingContacts),
                    segment.majorX()), stations + " stations pass at ceil(80%) contacts");
            assertFalse(PowderRoofTrap.hasOpposingLongSideBankContacts(
                    segment.cover(), bankContacts(segment.cover(), segment.majorX(), passingContacts - 1),
                    segment.majorX()), stations + " stations reject one contact below ceil(80%)");
        }
    }

    @Test
    void fallbackClassificationUsesSixStationsNeverTwentyFourColumnArea() {
        var preferredThreeWide = candidates(plannerInput(3, 8, 3, 3, true, 3)).get(0);
        var fallbackFourWide = candidates(plannerInput(3, 6, 3, 4, true, 3)).get(0);
        assertEquals(24, preferredThreeWide.powder().size());
        assertEquals(24, fallbackFourWide.powder().size());
        assertEquals(8, PowderRoofTrap.powderStationCount(
                preferredThreeWide.powder(), preferredThreeWide.shoulders()));
        assertEquals(6, PowderRoofTrap.powderStationCount(
                fallbackFourWide.powder(), fallbackFourWide.shoulders()));
    }

    @Test
    void bankCoverageUsesTheSelectedStationAxis() {
        PlannerInput zMajor = transpose(plannerInput(4, 8, 4, 4, true, 3));
        var segment = candidates(zMajor).get(0);
        assertFalse(segment.majorX());
        assertTrue(PowderRoofTrap.concealedSegmentAxisEligible(
                segment.powder(), segment.shoulders(), segment.cover(), false));

        List<int[]> crossAxisBankContacts = new ArrayList<>();
        for (int z = 0; z < 16; z++) {
            int station = z;
            int lowX = segment.cover().stream().filter(c -> c[1] == station)
                    .mapToInt(c -> c[0]).min().orElseThrow();
            int highX = segment.cover().stream().filter(c -> c[1] == station)
                    .mapToInt(c -> c[0]).max().orElseThrow();
            crossAxisBankContacts.add(new int[]{lowX - 1, z});
            crossAxisBankContacts.add(new int[]{highX + 1, z});
        }
        assertTrue(PowderRoofTrap.hasOpposingLongSideBankContacts(
                segment.cover(), crossAxisBankContacts, false),
                "a Z-station crossing uses its west/east terrain seams");
        assertFalse(PowderRoofTrap.hasOpposingLongSideBankContacts(
                segment.cover(), crossAxisBankContacts, true),
                "the same contacts cannot be reinterpreted as north/south banks");
    }

    @Test
    void minorDimensionMeasuresTheNarrowAxis() {
        List<int[]> strip = new ArrayList<>();
        for (int lx = 2; lx <= 9; lx++) {
            strip.add(new int[]{lx, 5});
        }
        assertEquals(1, PowderRoofTrap.patchMinorDimension(strip));
        strip.add(new int[]{2, 6});
        assertEquals(2, PowderRoofTrap.patchMinorDimension(strip));
        assertEquals(0, PowderRoofTrap.patchMinorDimension(new ArrayList<>()));
        assertEquals(0, PowderRoofTrap.patchMinorDimension(null));
    }

    // --- approach-bank flushness -----------------------------------------------------------------------

    @Test
    void walkOnRoofUsesASupportedSnowfieldPlaneInsteadOfALowShelf() {
        List<Integer> references = List.of(100, 100, 101, 102);
        List<Integer> north = List.of(100, 100, 100);
        List<Integer> south = List.of(100, 100, 101);
        assertEquals(100, PowderRoofTrap.selectSnowfieldRoofY(
                references, north, south, List.of(), List.of()),
                "the supported plane nearest the footprint's median snowfield reference wins");
        assertEquals(Integer.MIN_VALUE, PowderRoofTrap.selectSnowfieldRoofY(
                references, List.of(90, 90), List.of(90, 90), List.of(), List.of()),
                "a low shelf is not in the snowfield reference set and cannot become the cover");
        assertEquals(Integer.MIN_VALUE, PowderRoofTrap.selectSnowfieldRoofY(
                List.of(), north, south, List.of(), List.of()));
    }

    @Test
    void diagonalCrevasseMayUseEitherMatchedOpposingBankPair() {
        List<Integer> tooFewNorth = List.of(100);
        List<Integer> tooFewSouth = List.of(100);
        List<Integer> west = List.of(101, 101, 101, 101);
        List<Integer> east = List.of(100, 101, 101, 101);
        assertEquals(100, PowderRoofTrap.selectSnowfieldRoofY(
                List.of(101), tooFewNorth, tooFewSouth, west, east),
                "the credible east/west approaches win when a diagonal only brushes north/south");
        assertEquals(Integer.MIN_VALUE, PowderRoofTrap.selectSnowfieldRoofY(
                List.of(101), tooFewNorth, tooFewSouth, List.of(98, 98, 98), List.of(103, 103, 103)),
                "banks away from the snowfield plane leave the crevasse visibly open");
        assertEquals(100, PowderRoofTrap.selectSnowfieldRoofY(
                List.of(101), List.of(101), List.of(101), List.of(101), List.of(101)),
                "diagonal north+west versus south+east contacts form a real opposing crossing");
    }

    @Test
    void columnDepthIsMeasuredAgainstTheRealCoverHeight() {
        int roofY = 99;
        assertTrue(PowderRoofTrap.columnDeepEnoughForRoof(82, roofY), "drop of exactly 18 places");
        assertFalse(PowderRoofTrap.columnDeepEnoughForRoof(83, roofY), "drop of 17 rejects the whole bridge");
        assertTrue(PowderRoofTrap.columnDepthEligible(-28, roofY), "drop of exactly 128 stays protectable");
        assertFalse(PowderRoofTrap.columnDepthEligible(-29, roofY),
                "a 129-block natural fall exceeds the runtime safety identity bound");
    }

    @Test
    void shallowCrevasseShouldersMayDeepenButIntactGroundMayNotBecomeATrap() {
        int roofY = 99;
        assertEquals(82, PowderRoofTrap.plannedLandingFirstAir(82, roofY),
                "an already-deep crevasse floor stays where worldgen put it");
        assertEquals(82, PowderRoofTrap.plannedLandingFirstAir(88, roofY),
                "a six-block terrace may deepen to the full 18-block fall");
        assertEquals(Integer.MIN_VALUE, PowderRoofTrap.plannedLandingFirstAir(91, roofY),
                "nine blocks of excavation would be a new shaft, not a covered crevasse");
        assertEquals(Integer.MIN_VALUE, PowderRoofTrap.plannedLandingFirstAir(-29, roofY),
                "an extreme natural floor outside the runtime protection bound stays visibly open");
    }

    @Test
    void deepDropProbeReachesTheCaveStory() {
        assertEquals(48, PowderRoofTrap.DEEP_DROP_PROBE_DEPTH, "S36 reaches the intended glacial-cave country");
    }

    @Test
    void voidQualificationUnchanged() {
        assertEquals(4, PowderRoofTrap.MIN_DEEP_VOID_AIR);
        assertTrue(PowderRoofTrap.qualifiesDeepDrop(4));
        assertFalse(PowderRoofTrap.qualifiesDeepDrop(3), "a 3-air seam is not a cave");
    }

    @Test
    void optionalCaveThroatRejectsShallowAndLavaTierExtremeDrops() {
        assertFalse(PowderRoofTrap.deepDropDepthEligible(17));
        assertTrue(PowderRoofTrap.deepDropDepthEligible(18));
        assertTrue(PowderRoofTrap.deepDropDepthEligible(96));
        assertFalse(PowderRoofTrap.deepDropDepthEligible(97));
    }

    @Test
    void shaftWidensUnderWidePatches() {
        assertEquals(2, PowderRoofTrap.shaftSide(1), "invalid narrow inputs retain a bounded fallback");
        assertEquals(3, PowderRoofTrap.shaftSide(3), "the narrowest legal bridge gets a meaningful 3x3 throat");
        assertEquals(3, PowderRoofTrap.shaftSide(PowderRoofTrap.SHAFT_WIDE_MINOR_DIM));
        assertEquals(3, PowderRoofTrap.shaftSide(7));
    }

    @Test
    void deepShaftMustStayInsideTheCoveredFootprint() {
        List<int[]> bridge = new ArrayList<>();
        for (int lx = 2; lx <= 6; lx++) {
            for (int lz = 3; lz <= 6; lz++) {
                bridge.add(new int[]{lx, lz});
            }
        }
        assertTrue(PowderRoofTrap.containsSquare(bridge, 3, 3, 3), "central 3x3 is covered above");
        assertFalse(PowderRoofTrap.containsSquare(bridge, 1, 3, 3), "a shaft may not overhang the cover");
    }

    private static Set<String> coordinates(List<int[]> cells) {
        Set<String> coordinates = new TreeSet<>();
        for (int[] cell : cells) {
            coordinates.add(cell[0] + "," + cell[1]);
        }
        return coordinates;
    }

    private static Set<String> coordinates(boolean[][] mask) {
        Set<String> coordinates = new TreeSet<>();
        for (int x = 0; x < mask.length; x++) {
            for (int z = 0; z < mask[x].length; z++) {
                if (mask[x][z]) {
                    coordinates.add(x + "," + z);
                }
            }
        }
        return coordinates;
    }

    private record PlannerInput(List<int[]> deepComponent, boolean[][] coverMask, int[][] depthByColumn,
            int firstStation, int leftShoulders, int powderStations, int rightShoulders) {
    }

    private static List<PowderRoofTrap.ConcealedSegment> candidates(PlannerInput input) {
        return PowderRoofTrap.concealedSegmentCandidates(
                input.deepComponent(), input.coverMask(), input.depthByColumn());
    }

    private static PlannerInput plannerInput(int leftShoulders, int powderStations, int rightShoulders,
            int width, boolean stagger, int baseLowZ) {
        return plannerInputWithWidths(leftShoulders, powderStations, rightShoulders,
                width, width, stagger, baseLowZ);
    }

    private static PlannerInput plannerInputWithWidths(int leftShoulders, int powderStations,
            int rightShoulders, int shoulderWidth, int powderWidth, boolean stagger, int baseLowZ) {
        int total = leftShoulders + powderStations + rightShoulders;
        int firstStation = (16 - total) / 2;
        List<int[]> deep = new ArrayList<>();
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int i = 0; i < total; i++) {
            int x = firstStation + i;
            int lowZ = baseLowZ;
            if (stagger && i >= leftShoulders - 1) {
                lowZ++;
            }
            if (stagger && i >= leftShoulders + powderStations + rightShoulders - 1) {
                lowZ++;
            }
            boolean powder = i >= leftShoulders && i < leftShoulders + powderStations;
            int width = powder ? powderWidth : shoulderWidth;
            for (int z = lowZ; z < lowZ + width; z++) {
                cover[x][z] = true;
                depth[x][z] = powder ? 10 : 2;
                if (powder) {
                    deep.add(new int[]{x, z});
                }
            }
        }
        return new PlannerInput(deep, cover, depth, firstStation,
                leftShoulders, powderStations, rightShoulders);
    }

    private static PlannerInput shiftedInput(
            PlannerInput input, int sizeX, int sizeZ, int shiftX, int shiftZ) {
        List<int[]> deep = input.deepComponent().stream()
                .map(cell -> new int[]{cell[0] + shiftX, cell[1] + shiftZ})
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean[][] cover = new boolean[sizeX][sizeZ];
        int[][] depth = new int[sizeX][sizeZ];
        for (int x = 0; x < input.coverMask().length; x++) {
            for (int z = 0; z < input.coverMask()[x].length; z++) {
                cover[x + shiftX][z + shiftZ] = input.coverMask()[x][z];
                depth[x + shiftX][z + shiftZ] = input.depthByColumn()[x][z];
            }
        }
        return new PlannerInput(deep, cover, depth, input.firstStation() + shiftX,
                input.leftShoulders(), input.powderStations(), input.rightShoulders());
    }

    private static PlannerInput variableWidthInput(int shoulderWidth, int powderWidth) {
        int left = 3;
        int powderStations = 8;
        int right = 3;
        int total = left + powderStations + right;
        int firstStation = 1;
        List<int[]> deep = new ArrayList<>();
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int i = 0; i < total; i++) {
            int x = firstStation + i;
            boolean powder = i >= left && i < left + powderStations;
            int width = powder ? powderWidth : shoulderWidth;
            int lowZ = powder ? 3 : 1;
            for (int z = lowZ; z < lowZ + width; z++) {
                cover[x][z] = true;
                depth[x][z] = powder ? 10 : 2;
                if (powder) {
                    deep.add(new int[]{x, z});
                }
            }
        }
        return new PlannerInput(deep, cover, depth, firstStation, left, powderStations, right);
    }

    private static PlannerInput seamOnlyStaggerInput() {
        int left = 3;
        int powderStations = 8;
        int right = 3;
        int total = left + powderStations + right;
        int firstStation = 1;
        List<int[]> deep = new ArrayList<>();
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int i = 0; i < total; i++) {
            int x = firstStation + i;
            boolean powder = i >= left && i < left + powderStations;
            int lowZ = powder ? 4 : 3;
            for (int z = lowZ; z < lowZ + 4; z++) {
                cover[x][z] = true;
                depth[x][z] = powder ? 10 : 2;
                if (powder) {
                    deep.add(new int[]{x, z});
                }
            }
        }
        return new PlannerInput(deep, cover, depth, firstStation, left, powderStations, right);
    }

    private static PlannerInput deepCorridorInput(int totalStations, boolean branch) {
        int left = totalStations == 16 ? 4 : 3;
        int powderStations = 8;
        int right = totalStations - left - powderStations;
        int firstStation = (16 - totalStations) / 2;
        List<int[]> deep = new ArrayList<>();
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int i = 0; i < totalStations; i++) {
            int x = firstStation + i;
            int lowZ = 3 + (i >= left - 1 ? 1 : 0) + (i >= totalStations - 1 ? 1 : 0);
            for (int z = lowZ; z < lowZ + 4; z++) {
                deep.add(new int[]{x, z});
                cover[x][z] = true;
                depth[x][z] = 10;
            }
        }
        PlannerInput input = new PlannerInput(
                deep, cover, depth, firstStation, left, powderStations, right);
        if (branch) {
            for (int z = 8; z <= 10; z++) {
                addDeepCell(input, 7, z);
            }
            for (int z = 10; z <= 12; z++) {
                addDeepCell(input, 8, z);
            }
        }
        return input;
    }

    private static PlannerInput splitMergeCorridorInput() {
        List<int[]> deep = new ArrayList<>();
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int x = 0; x < 16; x++) {
            if ((x & 1) == 0) {
                for (int z = 4; z <= 10; z++) {
                    deep.add(new int[]{x, z});
                    cover[x][z] = true;
                    depth[x][z] = 10;
                }
            } else {
                for (int z = 4; z <= 10; z++) {
                    if (z == 7) {
                        continue;
                    }
                    deep.add(new int[]{x, z});
                    cover[x][z] = true;
                    depth[x][z] = 10;
                }
            }
        }
        return new PlannerInput(deep, cover, depth, 0, 4, 8, 4);
    }

    private static PlannerInput apronCorridorInput(int lowApron, int highApron) {
        PlannerInput input = deepCorridorInput(14, false);
        int first = input.firstStation();
        for (int along = first; along < first + 14; along++) {
            int low = stationMin(input.deepComponent(), along);
            int high = stationMax(input.deepComponent(), along);
            for (int cross = low - lowApron; cross < low; cross++) {
                input.coverMask()[along][cross] = true;
                input.depthByColumn()[along][cross] = 2;
            }
            for (int cross = high + 1; cross <= high + highApron; cross++) {
                input.coverMask()[along][cross] = true;
                input.depthByColumn()[along][cross] = 2;
            }
        }
        return input;
    }

    private static PlannerInput crossApronCorridorInput(int apron) {
        PlannerInput input = shiftedInput(deepCorridorInput(14, false), 48, 48, 16, 16);
        int first = input.firstStation();
        for (int along = first; along < first + 14; along++) {
            int low = stationMin(input.deepComponent(), along);
            int high = stationMax(input.deepComponent(), along);
            for (int cross = low - apron; cross < low; cross++) {
                input.coverMask()[along][cross] = true;
                input.depthByColumn()[along][cross] = 2;
            }
            for (int cross = high + 1; cross <= high + apron; cross++) {
                input.coverMask()[along][cross] = true;
                input.depthByColumn()[along][cross] = 2;
            }
        }
        return input;
    }

    private static PlannerInput augmentedRectangleCorridorInput() {
        PlannerInput input = deepCorridorInput(14, false);
        for (int along = input.firstStation(); along < input.firstStation() + 14; along++) {
            for (int cross = 1; cross <= 10; cross++) {
                input.coverMask()[along][cross] = true;
                if (input.depthByColumn()[along][cross] == 0) {
                    input.depthByColumn()[along][cross] = 2;
                }
            }
        }
        return input;
    }

    private static PlannerInput naturalContourCorridorInput() {
        PlannerInput input = deepCorridorInput(14, false);
        int[] lowDistances = {0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0};
        int[] highDistances = {0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0};
        for (int offset = 0; offset < 14; offset++) {
            int along = input.firstStation() + offset;
            int low = stationMin(input.deepComponent(), along);
            int high = stationMax(input.deepComponent(), along);
            for (int cross = low - lowDistances[offset]; cross < low; cross++) {
                input.coverMask()[along][cross] = true;
                input.depthByColumn()[along][cross] = 2;
            }
            for (int cross = high + 1; cross <= high + highDistances[offset]; cross++) {
                input.coverMask()[along][cross] = true;
                input.depthByColumn()[along][cross] = 2;
            }
        }
        return input;
    }

    private static PlannerInput convergentApronShoulderBranchesInput() {
        int first = 1;
        int total = 14;
        List<int[]> deep = new ArrayList<>();
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int i = 0; i < total; i++) {
            int x = first + i;
            int coverLow;
            int coverHigh;
            if (i == 0) {
                coverLow = 2;
                coverHigh = 11;
            } else if (i == 2) {
                coverLow = 1;
                coverHigh = 14;
            } else if (i >= 12) {
                coverLow = 4;
                coverHigh = 13;
            } else {
                coverLow = 3;
                coverHigh = 12;
            }
            for (int z = coverLow; z <= coverHigh; z++) {
                cover[x][z] = true;
                depth[x][z] = 2;
            }

            if (i == 0) {
                addDeepRange(deep, depth, x, 4, 9);
            } else if (i == 1) {
                addDeepRange(deep, depth, x, 5, 10);
            } else if (i == 2) {
                addDeepRange(deep, depth, x, 3, 5);
                addDeepRange(deep, depth, x, 10, 12);
            } else if (i >= 12) {
                addDeepRange(deep, depth, x, 6, 11);
            } else {
                addDeepRange(deep, depth, x, 5, 10);
            }
        }
        return new PlannerInput(deep, cover, depth, first, 3, 8, 3);
    }

    private static void addDeepRange(
            List<int[]> deep, int[][] depth, int x, int lowZ, int highZ) {
        for (int z = lowZ; z <= highZ; z++) {
            deep.add(new int[]{x, z});
            depth[x][z] = 10;
        }
    }

    private static PlannerInput fixedBankApronCorridorInput() {
        int total = 14;
        int first = 1;
        List<int[]> deep = new ArrayList<>();
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int i = 0; i < total; i++) {
            int x = first + i;
            int low = 3 + (i >= 2 ? 1 : 0) + (i >= total - 1 ? 1 : 0);
            for (int z = low; z < low + 3; z++) {
                deep.add(new int[]{x, z});
                depth[x][z] = 10;
            }
            int coverLow = low - 2;
            int coverHigh = low + 4;
            if (i == 0) {
                coverHigh = 14; // 3-wide spine at z=3..5: two low-side cells and nine high-side cells.
            }
            for (int z = coverLow; z <= coverHigh; z++) {
                cover[x][z] = true;
                if (depth[x][z] == 0) {
                    depth[x][z] = 2;
                }
            }
        }
        return new PlannerInput(deep, cover, depth, first, 3, 8, 3);
    }

    private static List<String> candidateKeys(
            List<PowderRoofTrap.ConcealedSegment> segments) {
        return segments.stream().map(segment -> {
            List<String> powder = segment.powder().stream()
                    .map(cell -> cell[0] + "," + cell[1]).sorted().toList();
            List<String> shoulders = segment.shoulders().stream()
                    .map(cell -> cell[0] + "," + cell[1]).sorted().toList();
            return String.join(";", powder) + "|" + String.join(";", shoulders);
        }).toList();
    }

    private static void assertPostSpineDiagnosticsAreZero(
            PowderRoofTrap.ConcealedSearchResult search) {
        assertEquals(0, search.traceOver9());
        assertEquals(0, search.traceOwnerEdgeOrUnknown());
        assertEquals(0, search.augmentedStagger());
        assertEquals(0, search.augmentedOtherShape());
    }

    private static void assertPostSpineDiagnosticsAreExclusiveSubcounts(
            PowderRoofTrap.ConcealedSearchResult search) {
        int classified = search.traceOver9() + search.traceOwnerEdgeOrUnknown()
                + search.augmentedStagger() + search.augmentedOtherShape();
        assertTrue(classified <= search.coreShapeRejects(),
                "each post-spine reject enters at most one detailed bucket; core shape remains inclusive");
    }

    private static void addDeepCell(PlannerInput input, int x, int z) {
        if (input.deepComponent().stream().noneMatch(c -> c[0] == x && c[1] == z)) {
            input.deepComponent().add(new int[]{x, z});
        }
        input.coverMask()[x][z] = true;
        input.depthByColumn()[x][z] = 10;
    }

    private static PlannerInput transpose(PlannerInput input) {
        List<int[]> deep = input.deepComponent().stream()
                .map(c -> new int[]{c[1], c[0]})
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean[][] cover = new boolean[16][16];
        int[][] depth = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                cover[z][x] = input.coverMask()[x][z];
                depth[z][x] = input.depthByColumn()[x][z];
            }
        }
        return new PlannerInput(deep, cover, depth, input.firstStation(), input.leftShoulders(),
                input.powderStations(), input.rightShoulders());
    }

    private static void clearStation(boolean[][] mask, int x) {
        java.util.Arrays.fill(mask[x], false);
    }

    private static int stationSpan(List<int[]> cells, boolean majorX) {
        int min = cells.stream().mapToInt(c -> majorX ? c[0] : c[1]).min().orElseThrow();
        int max = cells.stream().mapToInt(c -> majorX ? c[0] : c[1]).max().orElseThrow();
        return max - min + 1;
    }

    private static List<int[]> bankContacts(List<int[]> cover, boolean majorX, int stationCount) {
        int first = cover.stream().mapToInt(c -> majorX ? c[0] : c[1]).min().orElseThrow();
        List<int[]> contacts = new ArrayList<>();
        for (int along = first; along < first + stationCount; along++) {
            final int target = along;
            int low = cover.stream().filter(c -> (majorX ? c[0] : c[1]) == target)
                    .mapToInt(c -> majorX ? c[1] : c[0]).min().orElseThrow();
            int high = cover.stream().filter(c -> (majorX ? c[0] : c[1]) == target)
                    .mapToInt(c -> majorX ? c[1] : c[0]).max().orElseThrow();
            contacts.add(majorX ? new int[]{along, low - 1} : new int[]{low - 1, along});
            contacts.add(majorX ? new int[]{along, high + 1} : new int[]{high + 1, along});
        }
        return contacts;
    }

    private static List<int[]> meanderingComponent() {
        List<int[]> cells = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            int lowZ = 3 + x / 4;
            for (int z = lowZ; z < lowZ + 4; z++) {
                cells.add(new int[]{x, z});
            }
        }
        return cells;
    }

    private static List<int[]> rectangle(int minX, int maxX, int minZ, int maxZ) {
        List<int[]> cells = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cells.add(new int[]{x, z});
            }
        }
        return cells;
    }

    private static int stationMin(List<int[]> cells, int x) {
        return cells.stream().filter(c -> c[0] == x).mapToInt(c -> c[1]).min().orElseThrow();
    }

    private static int stationMax(List<int[]> cells, int x) {
        return cells.stream().filter(c -> c[0] == x).mapToInt(c -> c[1]).max().orElseThrow();
    }
}
