package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleHardStop} (B-7 S2 + sweep F1 + P3 gamemode fix) -- the engagement decision,
 * clamped position, outward-velocity kill, and the gamemode exemption rule behind the Wide-world pole
 * hard-stop, including the F1 property that the SAME velocity law applies to a dismounted rider's VEHICLE
 * (one law, two bodies).
 */
class PoleHardStopTest {

    private static final double EPS = 1e-9;

    // ---- P3 fix 2026-07-14: vanilla-border parity -- the wall stops creative too --------------

    @Test
    void creativeIsClamped() {
        // The owner flew past 90 in creative on TEST 97 and found "no wall". The vanilla world border stops
        // creative flight, so creative must be clamped (and gets the same contact presentation).
        assertFalse(PoleHardStop.exemptFromClamp(true, false),
                "creative is NOT exempt -- vanilla-border parity");
        // Survival/adventure obviously clamped too.
        assertFalse(PoleHardStop.exemptFromClamp(false, false));
    }

    @Test
    void spectatorPasses() {
        // Spectator no-clips through the vanilla border as well -- the ONLY exemption.
        assertTrue(PoleHardStop.exemptFromClamp(false, true));
        // A pathological both-flags read still passes on the spectator bit (spectator wins).
        assertTrue(PoleHardStop.exemptFromClamp(true, true));
    }

    @Test
    void engagementBoundaryRespectsEpsilon() {
        int zRadius = 10000;
        double eps = PoleHardStop.CLAMP_EPSILON;
        // Inside, exactly at the line, and within jitter epsilon: NOT engaged (no packet churn at the line).
        assertFalse(PoleHardStop.evaluate(9999.0, 0.0, zRadius, eps).engaged());
        assertFalse(PoleHardStop.evaluate(10000.0, 0.0, zRadius, eps).engaged());
        assertFalse(PoleHardStop.evaluate(10000.0 + eps, 0.0, zRadius, eps).engaged());
        // Just beyond epsilon: engaged.
        assertTrue(PoleHardStop.evaluate(10000.0 + eps + 0.01, 0.0, zRadius, eps).engaged());
        // Symmetric at the north pole (negative Z).
        assertFalse(PoleHardStop.evaluate(-(10000.0 + eps), 0.0, zRadius, eps).engaged());
        assertTrue(PoleHardStop.evaluate(-(10000.0 + eps + 0.01), 0.0, zRadius, eps).engaged());
    }

    @Test
    void s16c_arrivalOnTheLineDoesNotRubberBand() {
        // S16(c): the crossing drops the arriving player ON the pole line (ARRIVAL_DEG_POLE == 90), standing at
        // the block whose CENTER is zRadius + 0.5. The clamp must tolerate that -- a fresh arriver standing on
        // the line, before the 90->88 escape trek, must NOT be rubber-banded. With CLAMP_EPSILON == 1.0 the
        // arrival block-center clears the tolerance with a 0.5-block margin.
        int zRadius = 10000;
        double eps = PoleHardStop.CLAMP_EPSILON;
        double arrivalBlockCenter = zRadius + 0.5; // targetZ (=zRadius) + 0.5 stand offset
        assertFalse(PoleHardStop.evaluate(arrivalBlockCenter, 0.0, zRadius, eps).engaged(),
                "standing ON the arrival line (block center zRadius+0.5) never engages the clamp");
        assertFalse(PoleHardStop.evaluate(-arrivalBlockCenter, 0.0, zRadius, eps).engaged(),
                "symmetric at the north pole");
        // But a genuine overshoot more than a block past the line still walls (outward motion is clamped).
        assertTrue(PoleHardStop.evaluate(zRadius + eps + 0.01, 0.0, zRadius, eps).engaged());
    }

    @Test
    void clampedZIsThePoleLineOnTheRightSide() {
        PoleHardStop.Decision south = PoleHardStop.evaluate(10250.0, 0.0, 10000, PoleHardStop.CLAMP_EPSILON);
        assertTrue(south.engaged());
        assertEquals(10000.0, south.clampedZ(), EPS, "south side clamps to +zRadius");
        assertEquals(1.0, south.outwardSign(), EPS);

        PoleHardStop.Decision north = PoleHardStop.evaluate(-10250.0, 0.0, 10000, PoleHardStop.CLAMP_EPSILON);
        assertTrue(north.engaged());
        assertEquals(-10000.0, north.clampedZ(), EPS, "north side clamps to -zRadius");
        assertEquals(-1.0, north.outwardSign(), EPS);

        // Off-center border: the line is centerZ +/- zRadius.
        PoleHardStop.Decision off = PoleHardStop.evaluate(10350.0, 100.0, 10000, PoleHardStop.CLAMP_EPSILON);
        assertTrue(off.engaged());
        assertEquals(10100.0, off.clampedZ(), EPS);
    }

    @Test
    void unresolvedRadiusAndNaNNeverEngage() {
        assertFalse(PoleHardStop.evaluate(99999.0, 0.0, 0, PoleHardStop.CLAMP_EPSILON).engaged(),
                "zRadius 0 = radius not resolved -> no line to clamp to");
        assertFalse(PoleHardStop.evaluate(99999.0, 0.0, -5, PoleHardStop.CLAMP_EPSILON).engaged());
        assertFalse(PoleHardStop.evaluate(Double.NaN, 0.0, 10000, PoleHardStop.CLAMP_EPSILON).engaged(),
                "NaN position never engages (defensive)");
    }

    @Test
    void killOutwardZKillsOnlyThePolewardComponent() {
        // South side (outwardSign +1): positive (poleward) vz dies, negative (equatorward) vz survives.
        assertEquals(0.0, PoleHardStop.killOutwardZ(0.35, 1.0), EPS);
        assertEquals(-0.20, PoleHardStop.killOutwardZ(-0.20, 1.0), EPS);
        // North side (outwardSign -1): negative (poleward) vz dies, positive (equatorward) vz survives.
        assertEquals(0.0, PoleHardStop.killOutwardZ(-0.35, -1.0), EPS);
        assertEquals(0.20, PoleHardStop.killOutwardZ(0.20, -1.0), EPS);
    }

    @Test
    void f1_vehicleVelocityFallsUnderTheSameLaw() {
        // F1 (sweep MEDIUM): the shim dismounts a rider at the wall and applies killOutwardZ to BOTH the
        // player's and the vehicle's velocity with the SAME outwardSign from the SAME Decision -- so a boat or
        // horse carrying momentum toward the pole stops beside its rider. Pin the shared-law property: for any
        // engaged decision, the vehicle's outward component maps to 0 and its inward component is preserved,
        // identically to the player's.
        PoleHardStop.Decision d = PoleHardStop.evaluate(10100.0, 0.0, 10000, PoleHardStop.CLAMP_EPSILON);
        assertTrue(d.engaged());
        double playerVz = PoleHardStop.killOutwardZ(0.8, d.outwardSign());   // sprinting rider
        double vehicleVz = PoleHardStop.killOutwardZ(1.4, d.outwardSign());  // galloping horse
        assertEquals(0.0, playerVz, EPS);
        assertEquals(0.0, vehicleVz, EPS, "the vehicle's poleward momentum dies under the same law");
        assertEquals(-0.6, PoleHardStop.killOutwardZ(-0.6, d.outwardSign()), EPS,
                "an already-turning-back vehicle keeps its equatorward motion");
    }
}
