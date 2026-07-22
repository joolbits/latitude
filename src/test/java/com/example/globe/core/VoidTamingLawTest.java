package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for the S36 {@link VoidTamingLaw} (owner: mechanism C, onset 82, tame not eliminate).
 * These pin the three untouchability guarantees -- open sky, the protect floor, strength 0 -- and the
 * band/gate shapes the sweep amended (floor feather, dv-space band, smoothstep latitude).
 */
class VoidTamingLawTest {

    private static final double EPS = 1e-9;

    // --- latitude gate ---------------------------------------------------------------------------------

    @Test
    void latGateIsZeroEquatorwardOfOnsetAndOneAtFull() {
        assertEquals(0.0, VoidTamingLaw.latGate(0.0, 82.0, 85.0), EPS);
        assertEquals(0.0, VoidTamingLaw.latGate(82.0, 82.0, 85.0), EPS, "onset itself is still zero (feather starts)");
        assertEquals(1.0, VoidTamingLaw.latGate(85.0, 82.0, 85.0), EPS);
        assertEquals(1.0, VoidTamingLaw.latGate(90.0, 82.0, 85.0), EPS);
        double mid = VoidTamingLaw.latGate(83.5, 82.0, 85.0);
        assertEquals(0.5, mid, 1e-6, "smoothstep midpoint");
    }

    @Test
    void latGateIsMonotoneAndNanSafe() {
        double prev = -1.0;
        for (double lat = 80.0; lat <= 90.0; lat += 0.25) {
            double g = VoidTamingLaw.latGate(lat, 82.0, 85.0);
            assertTrue(g >= prev - EPS, "gate must not decrease at lat=" + lat);
            prev = g;
        }
        assertEquals(0.0, VoidTamingLaw.latGate(Double.NaN, 82.0, 85.0), EPS);
    }

    @Test
    void degenerateFullCollapsesToHardStepNotDivideByZero() {
        assertEquals(0.0, VoidTamingLaw.latGate(81.9, 82.0, 82.0), EPS);
        assertEquals(1.0, VoidTamingLaw.latGate(82.0, 82.0, 82.0), EPS);
        assertEquals(1.0, VoidTamingLaw.latGate(88.0, 82.0, 80.0), EPS, "full below onset still steps at onset");
    }

    // --- depth band ------------------------------------------------------------------------------------

    @Test
    void bandIsZeroInOpenSkyAndBeyondTheFade() {
        assertEquals(0.0, VoidTamingLaw.bandWeight(0.0), EPS, "the nominal surface itself");
        assertEquals(0.0, VoidTamingLaw.bandWeight(-0.5), EPS, "OPEN SKY (dv<0) is untouchable, always");
        assertEquals(0.0, VoidTamingLaw.bandWeight(VoidTamingLaw.FADE_DV), EPS, "deep caverns preserved");
        assertEquals(0.0, VoidTamingLaw.bandWeight(VoidTamingLaw.FADE_DV + 1.0), EPS);
        assertEquals(0.0, VoidTamingLaw.bandWeight(Double.NaN), EPS);
    }

    @Test
    void bandHoldsFullThroughTheNeckBandAndFeathersBothEnds() {
        assertEquals(1.0, VoidTamingLaw.bandWeight(VoidTamingLaw.FEATHER_DV), EPS, "feather tops out");
        assertEquals(1.0, VoidTamingLaw.bandWeight(VoidTamingLaw.HOLD_DV), EPS, "hold band is full strength");
        double topFeather = VoidTamingLaw.bandWeight(VoidTamingLaw.FEATHER_DV * 0.5);
        assertTrue(topFeather > 0.0 && topFeather < 1.0, "top feather is a real ramp, not a step");
        double fadeMid = VoidTamingLaw.bandWeight((VoidTamingLaw.HOLD_DV + VoidTamingLaw.FADE_DV) / 2.0);
        assertTrue(fadeMid > 0.0 && fadeMid < 1.0, "deep fade is a real ramp");
        // In vanilla dv-per-block terms the band reads 3/24/40 blocks below the nominal surface.
        assertEquals(3.0, VoidTamingLaw.FEATHER_DV / VoidTamingLaw.DV_PER_BLOCK, EPS);
        assertEquals(24.0, VoidTamingLaw.HOLD_DV / VoidTamingLaw.DV_PER_BLOCK, EPS);
        assertEquals(40.0, VoidTamingLaw.FADE_DV / VoidTamingLaw.DV_PER_BLOCK, EPS);
    }

    // --- protect floor ---------------------------------------------------------------------------------

    @Test
    void floorFeatherIsHardZeroAtAndBelowTheFloor() {
        assertEquals(0.0, VoidTamingLaw.floorFeather(48, 48, 10), EPS, "the floor itself");
        assertEquals(0.0, VoidTamingLaw.floorFeather(40, 48, 10), EPS, "the labyrinth below");
        assertEquals(1.0, VoidTamingLaw.floorFeather(58, 48, 10), EPS, "full strength a feather above");
        double mid = VoidTamingLaw.floorFeather(53, 48, 10);
        assertTrue(mid > 0.0 && mid < 1.0, "sweep fix 4: a RAMP into the floor, no shelf at floor+1");
        assertEquals(1.0, VoidTamingLaw.floorFeather(49, 48, 0), EPS, "zero feather degrades to the hard cut");
    }

    // --- the fill --------------------------------------------------------------------------------------

    @Test
    void fillActsOnAirOnly() {
        assertEquals(0.25, VoidTamingLaw.fillDensity(0.25, 1.5, 1.0), EPS, "solid untouched");
        assertEquals(0.0, VoidTamingLaw.fillDensity(0.0, 1.5, 1.0), EPS, "the surface iso-value untouched");
        assertEquals(-0.5 * (1.0 - 1.5), VoidTamingLaw.fillDensity(-0.5, 1.5, 1.0), EPS, "air fills");
    }

    @Test
    void strengthZeroAndGateZeroAreExactPassThroughs() {
        assertEquals(-0.7, VoidTamingLaw.fillDensity(-0.7, 0.0, 1.0), EPS);
        assertEquals(-0.7, VoidTamingLaw.fillDensity(-0.7, -3.0, 1.0), EPS, "negative strength clamps to none");
        assertEquals(-0.7, VoidTamingLaw.fillDensity(-0.7, 1.5, 0.0), EPS);
        assertEquals(-0.7, VoidTamingLaw.fillDensity(-0.7, 1.5, Double.NaN), EPS);
    }

    @Test
    void fillIsMonotoneTowardSolidAndBitesOnlyPastOne() {
        double base = -0.4;
        double prev = base;
        for (double k = 0.0; k <= 2.0; k += 0.1) {
            double f = VoidTamingLaw.fillDensity(base, k, 1.0);
            assertTrue(f >= prev - EPS, "fill must be monotone in K at k=" + k);
            prev = f;
        }
        // K*g <= 1: thins the margin but stays air (sweep finding 8 -- document the bite point).
        assertTrue(VoidTamingLaw.fillDensity(base, 0.9, 1.0) < 0.0, "sub-1 strength never converts");
        assertEquals(0.0, VoidTamingLaw.fillDensity(base, 1.0, 1.0), EPS, "K*g=1 lands exactly on the iso-surface");
        assertTrue(VoidTamingLaw.fillDensity(base, 1.5, 1.0) > 0.0, "past 1 the cap is genuinely solid");
        // Overshoot bound: (K-1)*|base|.
        assertEquals(0.5 * 0.4, VoidTamingLaw.fillDensity(base, 1.5, 1.0), EPS);
    }

    @Test
    void gateAboveOneClampsInsteadOfOverfilling() {
        assertEquals(0.0, VoidTamingLaw.fillDensity(-0.4, 1.0, 5.0), EPS, "g clamps to 1");
    }
}
