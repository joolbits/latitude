package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for the S35 {@link PowderRoofTrap} patch law (Peetsa 2026-07-21 verbatim spec + the
 * level-designer placement spec): powder covers over 2D candidate PATCHES (not 1-D strips), the 2D rim-ring
 * flushness invariant, the 12-block depth floor ("not just a drop of like three blocks"), the patch depth
 * vote, and the widened deep-drop pipeline.
 */
class PowderRoofTrapTest {

    // --- candidacy -------------------------------------------------------------------------------------

    @Test
    void candidacyRequiresTheFullShaftDepth() {
        assertEquals(12, PowderRoofTrap.MIN_SHAFT_DEPTH_BLOCKS, "S35 depth floor: 10 -> 12 (owner mocked 3-block scoops)");
        assertTrue(PowderRoofTrap.isTrapCandidate(88, 100), "exactly 12 below the reference qualifies");
        assertFalse(PowderRoofTrap.isTrapCandidate(89, 100), "11 below is the scoop band -- excluded");
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

    // --- interior clip + eligibility -------------------------------------------------------------------

    @Test
    void interiorBoxExcludesTheChunkBorderRing() {
        assertTrue(PowderRoofTrap.isInteriorCell(1, 1));
        assertTrue(PowderRoofTrap.isInteriorCell(14, 14));
        assertFalse(PowderRoofTrap.isInteriorCell(0, 7), "border cells have unreadable neighbours -- clipped");
        assertFalse(PowderRoofTrap.isInteriorCell(7, 15));
    }

    @Test
    void patchEligibilityCapsMinorDimensionAndArea() {
        assertEquals(7, PowderRoofTrap.PATCH_MAX_MINOR_DIMENSION, "one under the 9x9 window's 8-wide certification");
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
        // A 1x1 pinhole chimney stays legal.
        List<int[]> pinhole = new ArrayList<>();
        pinhole.add(new int[]{7, 7});
        assertTrue(PowderRoofTrap.patchEligible(pinhole));
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

    // --- rim ring + flushness --------------------------------------------------------------------------

    @Test
    void rimRingSpreadGateIsThreeBlocks() {
        assertEquals(4, PowderRoofTrap.PATCH_RIM_MAX_SPREAD, "census-calibrated: real ring undulation is ~4");
        assertTrue(PowderRoofTrap.rimRingBridgeable(100, 104), "natural snowfield undulation bridges");
        assertFalse(PowderRoofTrap.rimRingBridgeable(100, 105), "one past the band does not");
        // The fragmentation killer: a slope's ring spread is ~the slot depth.
        assertFalse(PowderRoofTrap.rimRingBridgeable(100, 100 + PowderRoofTrap.MIN_SHAFT_DEPTH_BLOCKS));
    }

    @Test
    void patchRoofYIsTheLowestRimTopBlock() {
        // firstAir 100 -> top block 99: flush with the LOWEST rim, never above ANY rim -- the anti-floating
        // invariant that killed the TEST 122 fragmentation, in its 2D form.
        assertEquals(99, PowderRoofTrap.patchRoofY(100));
    }

    @Test
    void columnDepthIsMeasuredAgainstTheRealCoverHeight() {
        int roofY = 99;
        assertTrue(PowderRoofTrap.columnDeepEnoughForRoof(88, roofY), "drop of exactly 12 places");
        assertFalse(PowderRoofTrap.columnDeepEnoughForRoof(89, roofY), "drop of 11 stays an open window");
    }

    @Test
    void patchDepthVoteRequiresSixtyPercent() {
        assertEquals(0.50f, PowderRoofTrap.PATCH_MIN_DEEP_FRACTION, 1e-6f, "census-calibrated majority vote");
        assertTrue(PowderRoofTrap.patchDeepEnough(3, 5), "3/5 -- places");
        assertTrue(PowderRoofTrap.patchDeepEnough(3, 6), "half exactly -- places");
        assertFalse(PowderRoofTrap.patchDeepEnough(2, 5), "2/5 -- a scoop with pores, abandoned");
        assertTrue(PowderRoofTrap.patchDeepEnough(1, 1), "the pinhole trap votes with itself");
        assertFalse(PowderRoofTrap.patchDeepEnough(0, 0), "empty never places");
    }

    // --- fraction gates --------------------------------------------------------------------------------

    @Test
    void roofFractionGateIsHalfOpenAtTheNewRate() {
        assertEquals(0.50f, PowderRoofTrap.ROOF_FRACTION, 1e-6f, "S35: 0.40 -> 0.50, the single calibration dial");
        assertTrue(PowderRoofTrap.shouldRoofPatch(0.0f));
        assertTrue(PowderRoofTrap.shouldRoofPatch(0.49f));
        assertFalse(PowderRoofTrap.shouldRoofPatch(0.50f), "exactly the fraction does NOT roof (half-open)");
        assertFalse(PowderRoofTrap.shouldRoofPatch(-0.01f), "out-of-range never roofs (safe direction: open)");
    }

    @Test
    void deepDropGateAndProbeWidenedForTheCaveStory() {
        assertEquals(0.50f, PowderRoofTrap.DEEP_DROP_FRACTION, 1e-6f, "S35: half of roofed patches attempt the punch");
        assertEquals(24, PowderRoofTrap.DEEP_DROP_PROBE_DEPTH, "S35: 16 -> 24, reaching glacial-caves country");
        assertTrue(PowderRoofTrap.shouldDeepDrop(0.49f));
        assertFalse(PowderRoofTrap.shouldDeepDrop(0.50f));
        assertFalse(PowderRoofTrap.shouldDeepDrop(1.01f));
    }

    @Test
    void voidQualificationUnchanged() {
        assertEquals(4, PowderRoofTrap.MIN_DEEP_VOID_AIR);
        assertTrue(PowderRoofTrap.qualifiesDeepDrop(4));
        assertFalse(PowderRoofTrap.qualifiesDeepDrop(3), "a 3-air seam is not a cave");
    }

    @Test
    void shaftWidensUnderWidePatches() {
        assertEquals(2, PowderRoofTrap.shaftSide(1), "narrow slot -> 2x2 throat");
        assertEquals(2, PowderRoofTrap.shaftSide(3));
        assertEquals(3, PowderRoofTrap.shaftSide(PowderRoofTrap.SHAFT_WIDE_MINOR_DIM),
                "a wide cover gets a 3x3 throat that can actually swallow its centre mass");
        assertEquals(3, PowderRoofTrap.shaftSide(7));
    }
}
