package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-curve tests for {@link PowderRoofTrap} (S25b powder-roof crevasse traps): the depth candidacy test,
 * the roofable-span detector, and the roof-fraction gate. No Minecraft on the classpath -- the 2-D chunk
 * weave + block placement live in {@code world.PowderCrevasseRoofFeature} and are proven at live flight.
 */
class PowderRoofTrapTest {

    // --- constant pins (a future accidental change is caught here) ---------------------------------------

    @Test
    void constantsPinnedToDesign() {
        assertEquals(10, PowderRoofTrap.MIN_SHAFT_DEPTH_BLOCKS, "shaft must descend >= 10 to be a fall trap");
        assertEquals(5, PowderRoofTrap.MAX_ROOF_SPAN_WIDTH, "only narrow slots (<= 5) get roofed");
        assertEquals(4, PowderRoofTrap.REFERENCE_WINDOW_RADIUS, "9x9 window catches slots up to ~8 wide");
        assertEquals(0.40f, PowderRoofTrap.ROOF_FRACTION, 1e-9f, "a minority of slots are trapped -- fair");
    }

    // --- S30 deep-drop pins + gates ----------------------------------------------------------------------

    @Test
    void deepDropConstantsPinnedToDesign() {
        assertEquals(0.30f, PowderRoofTrap.DEEP_DROP_FRACTION, 1e-9f, "a minority of roofed spans drop deep");
        assertEquals(16, PowderRoofTrap.DEEP_DROP_PROBE_DEPTH, "probe <= 16 blocks below the crevasse floor");
        assertEquals(4, PowderRoofTrap.MIN_DEEP_VOID_AIR, ">= 4 contiguous air counts as a connectable cave");
    }

    @Test
    void deepDropFractionGateBounds() {
        assertTrue(PowderRoofTrap.shouldDeepDrop(0.0f), "roll 0 deep-drops");
        assertTrue(PowderRoofTrap.shouldDeepDrop(0.299f), "just under 0.30 deep-drops");
        assertFalse(PowderRoofTrap.shouldDeepDrop(0.30f), "exactly 0.30 does NOT (half-open interval)");
        assertFalse(PowderRoofTrap.shouldDeepDrop(0.95f), "high roll stays a shallow crevasse trap");
        assertFalse(PowderRoofTrap.shouldDeepDrop(-0.01f), "a negative/out-of-range roll never deep-drops");
    }

    @Test
    void deepVoidQualifiesAtFourContiguousAir() {
        assertFalse(PowderRoofTrap.qualifiesDeepDrop(3), "3 air is a seam, not a cave");
        assertTrue(PowderRoofTrap.qualifiesDeepDrop(4), "exactly 4 air qualifies (inclusive floor)");
        assertTrue(PowderRoofTrap.qualifiesDeepDrop(12), "a tall void qualifies");
        assertFalse(PowderRoofTrap.qualifiesDeepDrop(0), "no air -> no connectable cave");
    }

    // --- isTrapCandidate: shaft-depth gate ---------------------------------------------------------------

    @Test
    void candidateExactlyAtAndAboveTheDepthFloor() {
        // reference snowfield at 80; a column is a candidate iff it sits >= 10 below it (<= 70).
        assertFalse(PowderRoofTrap.isTrapCandidate(71, 80), "9-deep shaft is too shallow -- not a trap");
        assertTrue(PowderRoofTrap.isTrapCandidate(70, 80), "exactly 10 deep qualifies (inclusive floor)");
        assertTrue(PowderRoofTrap.isTrapCandidate(40, 80), "a 40-deep crevasse slot qualifies");
        assertFalse(PowderRoofTrap.isTrapCandidate(80, 80), "flat snowfield -- no shaft, never a candidate");
        assertFalse(PowderRoofTrap.isTrapCandidate(85, 80),
                "a column ABOVE the local max (a bump) is never a candidate");
    }

    // --- roofableSpans: contiguous narrow runs only ------------------------------------------------------

    @Test
    void emptyOrNullRunYieldsNoSpans() {
        assertTrue(PowderRoofTrap.roofableSpans(null).isEmpty(), "null run -> no spans");
        assertTrue(PowderRoofTrap.roofableSpans(new boolean[0]).isEmpty(), "empty run -> no spans");
        assertTrue(PowderRoofTrap.roofableSpans(new boolean[]{false, false, false}).isEmpty(),
                "all-open run -> no spans");
    }

    @Test
    void singleNarrowSpanIsDetectedWithCorrectStartAndLength() {
        // .  X X X  .  .   -> one span {start=1, length=3}
        boolean[] run = {false, true, true, true, false, false};
        List<int[]> spans = PowderRoofTrap.roofableSpans(run);
        assertEquals(1, spans.size());
        assertEquals(1, spans.get(0)[0], "span start index");
        assertEquals(3, spans.get(0)[1], "span length");
    }

    @Test
    void widthFivePassesWidthSixIsDroppedAsGrandCanyon() {
        assertEquals(1, PowderRoofTrap.roofableSpans(
                new boolean[]{true, true, true, true, true}).size(), "width exactly 5 is roofable");
        assertTrue(PowderRoofTrap.roofableSpans(
                new boolean[]{true, true, true, true, true, true}).isEmpty(),
                "width 6 is a grand canyon -- stays open");
    }

    @Test
    void multipleSpansIncludingRunsAtBothEdges() {
        // X X . X X X X X X . X  -> narrow{0,2}, wide{3,6}=dropped, narrow{10,1}
        boolean[] run = {true, true, false, true, true, true, true, true, true, false, true};
        List<int[]> spans = PowderRoofTrap.roofableSpans(run);
        assertEquals(2, spans.size(), "the width-6 middle canyon is dropped; the two edge slots survive");
        assertEquals(0, spans.get(0)[0]);
        assertEquals(2, spans.get(0)[1]);
        assertEquals(10, spans.get(1)[0]);
        assertEquals(1, spans.get(1)[1], "a run touching the far edge is still detected");
    }

    // --- shouldRoofSpan: the 40% fraction gate -----------------------------------------------------------

    @Test
    void roofFractionGateBounds() {
        assertTrue(PowderRoofTrap.shouldRoofSpan(0.0f), "roll 0 roofs");
        assertTrue(PowderRoofTrap.shouldRoofSpan(0.399f), "just under 0.40 roofs");
        assertFalse(PowderRoofTrap.shouldRoofSpan(0.40f), "exactly 0.40 does NOT (half-open interval)");
        assertFalse(PowderRoofTrap.shouldRoofSpan(0.99f), "high roll leaves the slot open");
        assertFalse(PowderRoofTrap.shouldRoofSpan(-0.01f), "a negative/out-of-range roll never roofs");
    }
}
