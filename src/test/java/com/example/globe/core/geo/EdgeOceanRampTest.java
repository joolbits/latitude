package com.example.globe.core.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EdgeOceanRamp} (Phase 5 Slice B-2 Fix 1).
 *
 * <p>See {@code docs/binder/phase5-boundary-experience-plan-20260709.md}, "B-1 design" + "B-1
 * amendments": the ramp must be bitwise-untouched (chance == 0.0 exactly) below
 * {@code EDGE_OCEAN_START} so that boundaryV2-on with edgeB≈0 columns stays byte-identical to
 * flag-off (amendment 2), must never exceed {@code EDGE_OCEAN_MAX_SHARE} so a frayed sliver of
 * land always survives at the border (Art VI — no straight ring; amendment 4), and must be
 * monotonic so the moat reads as smoothly intentional rather than jagged only by noise.
 */
class EdgeOceanRampTest {

    // ---- (1) Containment invariant: exactly 0.0 at/below EDGE_OCEAN_START -------------------

    @Test
    void oceanChanceIsExactlyZeroAtOnsetThreshold() {
        // WHY: EDGE_OCEAN_START is the documented onset; at-or-below it, the interior must be
        // bitwise untouched (class javadoc "Bitwise flag-off / edgeB==0 guarantee").
        assertEquals(0.0, EdgeOceanRamp.oceanChance01(EdgeOceanRamp.EDGE_OCEAN_START));
    }

    @Test
    void oceanChanceIsExactlyZeroAtZeroEdgeB() {
        // WHY: edgeB == 0 means |x| <= 0.80*xRadius (far interior) — the case the plan's
        // "flag-off byte-identical" acceptance target hinges on.
        assertEquals(0.0, EdgeOceanRamp.oceanChance01(0.0));
    }

    @Test
    void oceanChanceIsExactlyZeroForNegativeEdgeB() {
        // WHY: edgeB is a smoothstep output and should never be negative in practice, but the
        // guard must not silently misbehave (e.g. produce a negative ramp) on defensive callers.
        assertEquals(0.0, EdgeOceanRamp.oceanChance01(-1.0));
    }

    @Test
    void oceanChanceIsExactlyZeroJustBelowOnsetThreshold() {
        assertEquals(0.0, EdgeOceanRamp.oceanChance01(EdgeOceanRamp.EDGE_OCEAN_START - 1e-9));
    }

    @Test
    void oceanChanceIsPositiveJustAboveOnsetThreshold() {
        // WHY: confirms the onset is a true threshold (fades in), not an off-by-one that leaves
        // the ramp dead past EDGE_OCEAN_START too.
        assertTrue(EdgeOceanRamp.oceanChance01(EdgeOceanRamp.EDGE_OCEAN_START + 1e-6) > 0.0);
    }

    // ---- (2) Monotonic non-decreasing over edgeB in [0, 1] -----------------------------------

    @Test
    void oceanChanceIsMonotonicNonDecreasingAcrossUnitRange() {
        double previous = EdgeOceanRamp.oceanChance01(0.0);
        for (int i = 1; i <= 200; i++) {
            double edgeB = i / 200.0;
            double current = EdgeOceanRamp.oceanChance01(edgeB);
            assertTrue(current >= previous - 1e-12,
                    "ramp decreased at edgeB=" + edgeB + " (" + previous + " -> " + current + ")");
            previous = current;
        }
    }

    // ---- (3) Cap: never exceeds EDGE_OCEAN_MAX_SHARE, even at edgeB == 1 ---------------------

    @Test
    void oceanChanceSaturatesAtMaxShareForEdgeBAtOne() {
        // WHY: the very border still leaves EDGE_OCEAN_MAX_SHARE (0.94) as the ceiling, not 1.0 —
        // the "frayed coast, not a perfect ring" guarantee (Art VI / amendment 4).
        assertEquals(EdgeOceanRamp.EDGE_OCEAN_MAX_SHARE, EdgeOceanRamp.oceanChance01(1.0), 1e-12);
    }

    @Test
    void oceanChanceNeverExceedsMaxShareBeyondFullThreshold() {
        assertEquals(EdgeOceanRamp.EDGE_OCEAN_MAX_SHARE, EdgeOceanRamp.oceanChance01(1.5), 1e-12);
        assertTrue(EdgeOceanRamp.oceanChance01(10.0) <= EdgeOceanRamp.EDGE_OCEAN_MAX_SHARE);
    }

    // ---- (4) frayedEdgeOcean gating -----------------------------------------------------------

    @Test
    void frayedEdgeOceanIsFalseWheneverChanceIsZeroRegardlessOfFray() {
        // WHY: chance == 0.0 must veto the flip outright, even if frayNoise01 is 0.0 (which would
        // otherwise satisfy fray < chance under a naive "<=" reading of a zero chance).
        assertFalse(EdgeOceanRamp.frayedEdgeOcean(0.0, 0.0));
        assertFalse(EdgeOceanRamp.frayedEdgeOcean(0.0, 0.5));
        assertFalse(EdgeOceanRamp.frayedEdgeOcean(0.0, 1.0));
        assertFalse(EdgeOceanRamp.frayedEdgeOcean(EdgeOceanRamp.EDGE_OCEAN_START, 0.0));
    }

    @Test
    void frayedEdgeOceanFlipsWhenFrayBelowChance() {
        double edgeB = 1.0; // chance == EDGE_OCEAN_MAX_SHARE == 0.94
        double chance = EdgeOceanRamp.oceanChance01(edgeB);
        assertTrue(EdgeOceanRamp.frayedEdgeOcean(edgeB, chance - 0.01));
    }

    @Test
    void frayedEdgeOceanDoesNotFlipWhenFrayAboveChance() {
        double edgeB = 1.0;
        double chance = EdgeOceanRamp.oceanChance01(edgeB);
        assertFalse(EdgeOceanRamp.frayedEdgeOcean(edgeB, chance + 0.01));
    }

    @Test
    void frayedEdgeOceanDoesNotFlipWhenFrayExactlyEqualsChance() {
        // WHY: the comparison is a strict "<" (frayNoise01 < chance), so fray == chance is the
        // documented boundary that must NOT flip.
        double edgeB = 0.5;
        double chance = EdgeOceanRamp.oceanChance01(edgeB);
        assertFalse(EdgeOceanRamp.frayedEdgeOcean(edgeB, chance));
    }

    // ---- (5) Documented anchor: edgeB ~= 0.5 (|x| ~= 0.90*xRadius) ---------------------------

    @Test
    void oceanChanceAtHalfEdgeMatchesDocumentedAnchorFormula() {
        // WHY: plan doc / class javadoc anchor claim: "with START=0.10 and FULL=0.70 the ramp at
        // edgeB~=0.5 is ~0.70*MAX_SHARE" (roughly |x|=0.90*xRadius). Verify against the actual
        // smoothstep formula rather than hardcoding the resulting magic number, so the test tracks
        // the formula's intent rather than merely re-asserting one sampled value.
        double edgeB = 0.5;
        double t = (edgeB - EdgeOceanRamp.EDGE_OCEAN_START)
                / (EdgeOceanRamp.EDGE_OCEAN_FULL - EdgeOceanRamp.EDGE_OCEAN_START);
        double expectedSmoothstep = t * t * (3.0 - 2.0 * t);
        double expected = EdgeOceanRamp.EDGE_OCEAN_MAX_SHARE * expectedSmoothstep;

        assertEquals(expected, EdgeOceanRamp.oceanChance01(edgeB), 1e-12);
        // Sanity-check the doc's own rough anchor (~0.70 * MAX_SHARE) without hardcoding it as the
        // assertion source of truth.
        assertEquals(0.70 * EdgeOceanRamp.EDGE_OCEAN_MAX_SHARE, expected, 0.05);
    }
}
