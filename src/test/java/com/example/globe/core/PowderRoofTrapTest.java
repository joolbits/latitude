package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // --- hidden-bridge footprint -----------------------------------------------------------------------

    @Test
    void patchEligibilityRequiresMeaningfulWidthAndArea() {
        assertEquals(3, PowderRoofTrap.PATCH_MIN_MINOR_DIMENSION);
        assertEquals(7, PowderRoofTrap.PATCH_MAX_MINOR_DIMENSION, "one under the 9x9 window's 8-wide certification");
        assertEquals(12, PowderRoofTrap.PATCH_MIN_AREA,
                "minimum is a contiguous 3x4 crossing, never S35's one/two-block lid");
        assertEquals(48, PowderRoofTrap.PATCH_MAX_AREA);
        // The signature bridge: 4 wide x 12 long = 48 cells -- eligible at both caps.
        List<int[]> bridge = new ArrayList<>();
        for (int lx = 1; lx <= 12; lx++) {
            for (int lz = 1; lz <= 4; lz++) {
                bridge.add(new int[]{lx, lz});
            }
        }
        assertTrue(PowderRoofTrap.patchEligible(bridge));
        // An 8x8 basin: minor dimension 8 > 7 -- terrain, not a trap.
        List<int[]> basin = new ArrayList<>();
        for (int lx = 1; lx <= 8; lx++) {
            for (int lz = 1; lz <= 8; lz++) {
                basin.add(new int[]{lx, lz});
            }
        }
        assertFalse(PowderRoofTrap.patchEligible(basin));
        // S35's 1x1/two-cell lids are the reported bug, not valid encounters.
        List<int[]> pinhole = new ArrayList<>();
        pinhole.add(new int[]{7, 7});
        assertFalse(PowderRoofTrap.patchEligible(pinhole));
        List<int[]> narrowStrip = new ArrayList<>();
        for (int lx = 1; lx <= 14; lx++) {
            narrowStrip.add(new int[]{lx, 7});
        }
        assertFalse(PowderRoofTrap.patchEligible(narrowStrip), "a long one-wide strip still is not a field");

        List<int[]> minimumBridge = new ArrayList<>();
        for (int lx = 1; lx <= 4; lx++) {
            for (int lz = 1; lz <= 3; lz++) {
                minimumBridge.add(new int[]{lx, lz});
            }
        }
        assertTrue(PowderRoofTrap.patchEligible(minimumBridge), "3x4 is the smallest accepted hidden bridge");
        // Empty (fully clipped) and null are ineligible.
        assertFalse(PowderRoofTrap.patchEligible(new ArrayList<>()));
        assertFalse(PowderRoofTrap.patchEligible(null));
        // Area cap binds even when the minor dimension passes: 7x7 = 49 > 48.
        List<int[]> huge = new ArrayList<>();
        for (int lx = 1; lx <= 7; lx++) {
            for (int lz = 1; lz <= 7; lz++) {
                huge.add(new int[]{lx, lz});
            }
        }
        assertTrue(huge.size() > PowderRoofTrap.PATCH_MAX_AREA);
        assertFalse(PowderRoofTrap.patchEligible(huge), "area cap binds even when the minor dimension passes");
    }

    @Test
    void patchEligibilityRejectsAnInteriorHole() {
        List<int[]> holed = new ArrayList<>();
        for (int lx = 1; lx <= 5; lx++) {
            for (int lz = 1; lz <= 5; lz++) {
                if (lx != 3 || lz != 3) {
                    holed.add(new int[]{lx, lz});
                }
            }
        }
        assertFalse(PowderRoofTrap.patchEligible(holed), "an accepted cover cannot have a visible hole inside it");
    }

    @Test
    void largeCrevasseProducesOneAuthoredBridgeSegmentInsteadOfBeingRejectedOrChunkClipped() {
        List<int[]> throughRunningCrevasse = new ArrayList<>();
        for (int lx = 0; lx <= 15; lx++) {
            for (int lz = 5; lz <= 8; lz++) {
                throughRunningCrevasse.add(new int[]{lx, lz});
            }
        }
        List<int[]> bridge = PowderRoofTrap.selectBridgeFootprint(throughRunningCrevasse);
        assertEquals(PowderRoofTrap.PATCH_TARGET_AREA, bridge.size(),
                "the planner selects a deliberate 4x8 bridge from a larger fissure");
        assertTrue(PowderRoofTrap.patchEligible(bridge));
        assertTrue(bridge.stream().allMatch(c -> PowderRoofTrap.isInteriorCell(c[0], c[1])),
                "the atomic local bridge never writes a chunk-border cell");
    }

    @Test
    void footprintPlannerCannotSliceAWideBasinIntoAFakeNarrowBridge() {
        for (int side : new int[]{7, 8, 9, 14}) {
            List<int[]> basin = new ArrayList<>();
            for (int lx = 1; lx <= side; lx++) {
                for (int lz = 1; lz <= side; lz++) {
                    basin.add(new int[]{lx, lz});
                }
            }
            assertTrue(PowderRoofTrap.bridgeFootprintCandidates(basin).isEmpty(),
                    side + "x" + side + " basin must stay open rather than receiving a sliced partial lid");
        }
        for (int[] dimensions : new int[][]{{7, 8}, {8, 7}}) {
            List<int[]> nearSquareBasin = new ArrayList<>();
            for (int lx = 1; lx <= dimensions[0]; lx++) {
                for (int lz = 1; lz <= dimensions[1]; lz++) {
                    nearSquareBasin.add(new int[]{lx, lz});
                }
            }
            assertTrue(PowderRoofTrap.bridgeFootprintCandidates(nearSquareBasin).isEmpty(),
                    dimensions[0] + "x" + dimensions[1]
                            + " basin must stay open rather than receiving a sliced partial lid");
        }
    }

    @Test
    void footprintPlannerTriesBothAxesForCurvingCrevasses() {
        List<int[]> bent = new ArrayList<>();
        for (int lx = 1; lx <= 12; lx++) {
            for (int lz = 5; lz <= 8; lz++) {
                bent.add(new int[]{lx, lz});
            }
        }
        for (int lx = 9; lx <= 12; lx++) {
            for (int lz = 9; lz <= 14; lz++) {
                bent.add(new int[]{lx, lz});
            }
        }
        List<List<int[]>> candidates = PowderRoofTrap.bridgeFootprintCandidates(bent);
        assertTrue(candidates.stream().anyMatch(cells -> {
            int minZ = cells.stream().mapToInt(c -> c[1]).min().orElseThrow();
            int maxZ = cells.stream().mapToInt(c -> c[1]).max().orElseThrow();
            return maxZ - minZ + 1 >= 6;
        }), "a bend can supply a viable segment along the secondary axis");
    }

    @Test
    void footprintPlannerStillRejectsTinyAndOneWideOpenings() {
        List<int[]> pinhole = List.of(new int[]{7, 7});
        assertTrue(PowderRoofTrap.selectBridgeFootprint(pinhole).isEmpty());
        List<int[]> strip = new ArrayList<>();
        for (int lx = 0; lx <= 15; lx++) {
            strip.add(new int[]{lx, 7});
        }
        assertTrue(PowderRoofTrap.selectBridgeFootprint(strip).isEmpty());
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
}
