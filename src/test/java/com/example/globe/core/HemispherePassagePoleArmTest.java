package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for the B-7 P2 POLE arm: the SHARED {@link HemispherePassage#evaluatePhase} machine driven by
 * {@link PoleGeometry} distances (the pole arm is a second instance of the same machine -- no forked state law),
 * plus the poleward re-prompt facing predicates ({@link HemispherePassage#lookDirZ} /
 * {@link HemispherePassage#facingPolewardZ}).
 *
 * <p>The HARD LAW (design item 2): the client only ever answers from an OPEN prompt, and the prompt opens ONLY
 * when {@code evaluatePhase} returns {@code openPrompt}. These pins prove a SEEDED / DISARMED / EDGE_PROMPTED
 * pole arm never opens (so never answers) except the single SEEDED walk-to-the-wall re-prompt.
 */
class HemispherePassagePoleArmTest {

    private static final double CENTER = 0.0;

    private static PoleGeometry.Resolved regular() {
        return PoleGeometry.resolve(10000); // prompt 88.89 / rearm 166.67 / arrival 55.56 / edgeReprompt 11.11
    }

    private static HemispherePassage.PhaseDecision step(HemispherePassage.Phase phase, double dist,
                                                        boolean canOpen, PoleGeometry.Resolved g) {
        return HemispherePassage.evaluatePhase(phase, dist, canOpen,
                g.promptDist(), g.rearmDist(), g.edgeRepromptDist());
    }

    // ---- ordinary approach ----

    @Test
    void armedApproachOpensAtThePromptLine() {
        PoleGeometry.Resolved g = regular();
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.ARMED, g.promptDist(), true, g);
        assertTrue(d.openPrompt());
        assertEquals(HemispherePassage.Phase.DISARMED, d.nextPhase());
    }

    @Test
    void armedHoldsWhenSurfaceBlockedAtThePromptLine() {
        PoleGeometry.Resolved g = regular();
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.ARMED, g.promptDist(), false, g);
        assertFalse(d.openPrompt());
        assertEquals(HemispherePassage.Phase.ARMED, d.nextPhase()); // HOLD, one-shot not burned
    }

    // ---- HARD LAW: seeded / disarmed arms never answer ----

    @Test
    void seededArrivalColumnNeverOpensAPrompt() {
        // The pole arrival (89.5 deg) seeds SEEDED_DISARMED INSIDE the prompt + re-arm lines. It must HOLD --
        // no self-prompt at the arrival column (the answer guard: SEEDED never answers here).
        PoleGeometry.Resolved g = regular();
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.SEEDED_DISARMED, g.arrivalDist(), true, g);
        assertFalse(d.openPrompt());
        assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, d.nextPhase());
    }

    @Test
    void seededOpensOnceAtTheWall() {
        // Walking a seeded arm ALL the way to the pole re-offers the crossing ONCE (SEEDED_DISARMED -> EDGE_PROMPTED).
        PoleGeometry.Resolved g = regular();
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.SEEDED_DISARMED, g.edgeRepromptDist(), true, g);
        assertTrue(d.openPrompt());
        assertEquals(HemispherePassage.Phase.EDGE_PROMPTED, d.nextPhase());
    }

    @Test
    void seededHoldsAtTheWallWhenBlocked() {
        PoleGeometry.Resolved g = regular();
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.SEEDED_DISARMED, g.edgeRepromptDist(), false, g);
        assertFalse(d.openPrompt());
        assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, d.nextPhase()); // HOLD the seed, don't burn it
    }

    @Test
    void disarmedAndEdgePromptedNeverOpen() {
        PoleGeometry.Resolved g = regular();
        // Ordinary DISARMED anywhere in band: never opens.
        assertFalse(step(HemispherePassage.Phase.DISARMED, g.promptDist(), true, g).openPrompt());
        assertFalse(step(HemispherePassage.Phase.DISARMED, g.edgeRepromptDist(), true, g).openPrompt());
        // The seeded one-shot already consumed: never opens again until a walk-out re-arm.
        assertFalse(step(HemispherePassage.Phase.EDGE_PROMPTED, g.edgeRepromptDist(), true, g).openPrompt());
    }

    @Test
    void strictWalkOutReArmsThenTheOrdinaryPromptSpeaks() {
        PoleGeometry.Resolved g = regular();
        // Past the re-arm line: any phase returns to ARMED.
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.SEEDED_DISARMED, g.rearmDist() + 1.0, true, g);
        assertFalse(d.openPrompt());
        assertEquals(HemispherePassage.Phase.ARMED, d.nextPhase());
        // Turning back in re-opens the ordinary prompt at 89.2.
        assertTrue(step(HemispherePassage.Phase.ARMED, g.promptDist(), true, g).openPrompt());
    }

    @Test
    void uniformSeededRegimeAcrossEveryShippableRadius() {
        // S5: arrival (89.5) sits INSIDE prompt AND rearm on EVERY size -> a fresh arrival seeds SEEDED_DISARMED
        // and HOLDS everywhere (no self-prompt is STATE law, not geometry). Pin it across five radii.
        for (int zRadius : new int[]{3750, 7500, 10000, 15000, 20000}) {
            PoleGeometry.Resolved g = PoleGeometry.resolve(zRadius);
            PoleGeometry.assertChain(g); // edgeReprompt < arrival < prompt < rearm
            assertTrue(g.arrivalDist() < g.promptDist(), "arrival inside prompt @" + zRadius);
            assertTrue(g.arrivalDist() < g.rearmDist(), "arrival inside rearm @" + zRadius);
            HemispherePassage.PhaseDecision seeded =
                    step(HemispherePassage.Phase.SEEDED_DISARMED, g.arrivalDist(), true, g);
            assertFalse(seeded.openPrompt(), "no self-prompt at arrival @" + zRadius);
            assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, seeded.nextPhase(), "seed holds @" + zRadius);
        }
    }

    // ---- poleward re-prompt facing predicates ----

    @Test
    void lookDirZFollowsTheMinecraftYawConvention() {
        assertEquals(1.0, HemispherePassage.lookDirZ(0f), 1e-9);    // yaw 0 -> +Z (south)
        assertEquals(-1.0, HemispherePassage.lookDirZ(180f), 1e-9); // yaw 180 -> -Z (north)
        assertEquals(0.0, HemispherePassage.lookDirZ(90f), 1e-9);   // yaw 90 -> due west, no Z component
    }

    @Test
    void facingPolewardZIsTrueOnlyTowardTheNearerPole() {
        double minCos = HemispherePassage.REARM_GESTURE_FACING_MIN_COS; // cos 60 = 0.5
        // Southern half (z > center): poleward = +Z. Facing south (yaw 0) is poleward; facing north (yaw 180) is not.
        assertTrue(HemispherePassage.facingPolewardZ(1000.0, CENTER, 0f, minCos));
        assertFalse(HemispherePassage.facingPolewardZ(1000.0, CENTER, 180f, minCos));
        // Northern half (z < center): poleward = -Z. Facing north (yaw 180) is poleward; facing south (yaw 0) is not.
        assertTrue(HemispherePassage.facingPolewardZ(-1000.0, CENTER, 180f, minCos));
        assertFalse(HemispherePassage.facingPolewardZ(-1000.0, CENTER, 0f, minCos));
        // Exactly on the equator: no poleward side.
        assertFalse(HemispherePassage.facingPolewardZ(CENTER, CENTER, 0f, minCos));
        // Sideways (yaw 90, due west) is outside the 60-deg cone on either half.
        assertFalse(HemispherePassage.facingPolewardZ(1000.0, CENTER, 90f, minCos));
    }
}
