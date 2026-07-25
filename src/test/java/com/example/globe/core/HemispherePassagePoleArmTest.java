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
 * when {@code evaluatePhase} returns {@code openPrompt}. S16(c): the crossing prompt now lives AT the wall and the
 * near-pole seeded auto-re-prompt COLLAPSED into it (edgeReprompt is the disabled sentinel), so these pins prove a
 * SEEDED / DISARMED / EDGE_PROMPTED pole arm NEVER auto-opens -- the only re-opens are the ordinary ARMED approach
 * prompt (at the wall) after a walk-out re-arm, and the explicit right-click gesture.
 */
class HemispherePassagePoleArmTest {

    private static final double CENTER = 0.0;

    private static PoleGeometry.Resolved regular() {
        // S16(c): prompt 3 (wall epsilon) / rearm 166.67 / arrival 0 (the pole line) / edgeReprompt -1 (disabled)
        return PoleGeometry.resolve(10000);
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
        // S16(c): the pole arrival (90 deg = distance 0) seeds SEEDED_DISARMED pressed against the wall, INSIDE
        // the prompt + re-arm lines. It must HOLD -- no self-prompt at the arrival column (the answer guard).
        PoleGeometry.Resolved g = regular();
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.SEEDED_DISARMED, g.arrivalDist(), true, g);
        assertFalse(d.openPrompt());
        assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, d.nextPhase());
    }

    @Test
    void seededNeverAutoOpensEvenPressedAtTheWall() {
        // S16(c): the near-pole seeded auto-re-prompt COLLAPSED into the wall prompt (edgeReprompt is the disabled
        // sentinel -1, which distToPole >= 0 can never satisfy). So a seeded arm walked ALL the way to the wall
        // (distance 0, pressed against the clamp) still HOLDS -- it does NOT auto-open. The only re-opens are a
        // walk-out re-arm (then the ordinary approach prompt) and the explicit gesture.
        PoleGeometry.Resolved g = regular();
        // At the pole line (distance 0), can-open true: HOLD the seed, never auto-fire.
        HemispherePassage.PhaseDecision atWall = step(HemispherePassage.Phase.SEEDED_DISARMED, 0.0, true, g);
        assertFalse(atWall.openPrompt(), "seeded arm never auto-opens at the wall (edge-reprompt collapsed)");
        assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, atWall.nextPhase(), "the seed holds");
        // Inside the wall prompt band (distance < promptDist) too: still HOLD.
        HemispherePassage.PhaseDecision inBand = step(HemispherePassage.Phase.SEEDED_DISARMED,
                g.promptDist() - 1.0, true, g);
        assertFalse(inBand.openPrompt());
        assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, inBand.nextPhase());
    }

    @Test
    void disarmedAndEdgePromptedNeverOpen() {
        PoleGeometry.Resolved g = regular();
        // Ordinary DISARMED anywhere in band (at the wall prompt line, and pressed on the wall): never opens.
        assertFalse(step(HemispherePassage.Phase.DISARMED, g.promptDist(), true, g).openPrompt());
        assertFalse(step(HemispherePassage.Phase.DISARMED, 0.0, true, g).openPrompt());
        // EDGE_PROMPTED behaves as ordinary disarmed: never opens again until a walk-out re-arm.
        assertFalse(step(HemispherePassage.Phase.EDGE_PROMPTED, 0.0, true, g).openPrompt());
    }

    @Test
    void strictWalkOutReArmsThenTheOrdinaryPromptSpeaks() {
        PoleGeometry.Resolved g = regular();
        // Past the re-arm line (88.5 deg): any phase returns to ARMED.
        HemispherePassage.PhaseDecision d = step(HemispherePassage.Phase.SEEDED_DISARMED, g.rearmDist() + 1.0, true, g);
        assertFalse(d.openPrompt());
        assertEquals(HemispherePassage.Phase.ARMED, d.nextPhase());
        // Turning back in re-opens the ordinary prompt AT the wall (a fresh journey).
        assertTrue(step(HemispherePassage.Phase.ARMED, g.promptDist(), true, g).openPrompt());
    }

    @Test
    void oneWallPromptRegimeAcrossEveryShippableRadius() {
        // S16(c): arrival (distance 0) sits INSIDE prompt (the ~3 wall epsilon) AND rearm on EVERY size -> a fresh
        // arrival seeds SEEDED_DISARMED and HOLDS everywhere (no self-prompt is STATE law + the disabled
        // edge-reprompt). The seeded arm never auto-opens even pressed against the wall. Pin across five radii.
        for (int zRadius : new int[]{3750, 7500, 10000, 15000, 20000}) {
            PoleGeometry.Resolved g = PoleGeometry.resolve(zRadius);
            PoleGeometry.assertChain(g); // edgeReprompt(-1) < arrival(0) < prompt < rearm
            assertTrue(g.arrivalDist() < g.promptDist(), "arrival inside prompt @" + zRadius);
            assertTrue(g.arrivalDist() < g.rearmDist(), "arrival inside rearm @" + zRadius);
            // Seeded at the arrival column (distance 0) holds; and still holds if walked deeper to the wall.
            for (double dist : new double[]{g.arrivalDist(), 0.0}) {
                HemispherePassage.PhaseDecision seeded =
                        step(HemispherePassage.Phase.SEEDED_DISARMED, dist, true, g);
                assertFalse(seeded.openPrompt(), "no auto-prompt @" + zRadius + " dist=" + dist);
                assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, seeded.nextPhase(), "seed holds @" + zRadius);
            }
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

    // ---- B-7 P3 gesture AIM GATE, pole flavor (owner, TEST 97: breaking ice at the pole wall kept ----
    // ---- re-summoning the prompt -- only a MISS/air click may re-arm) ----

    @Test
    void poleGestureAimGateOnlyAcceptsAMissClick() {
        PoleGeometry.Resolved g = regular();
        // A qualifying pole gesture: disarmed, inside the prompt band, facing the nearer (south) pole.
        boolean facingPole = HemispherePassage.facingPolewardZ(9000.0, CENTER, 0f,
                HemispherePassage.REARM_GESTURE_FACING_MIN_COS);
        assertTrue(facingPole, "sanity: south half facing south = poleward");
        assertTrue(HemispherePassage.rearmGestureArms(false, g.promptDist(), facingPole, g.promptDist(),
                HemispherePassage.GESTURE_HIT_MISS), "air click at the pole wall asks the border");
        assertFalse(HemispherePassage.rearmGestureArms(false, g.promptDist(), facingPole, g.promptDist(),
                HemispherePassage.GESTURE_HIT_BLOCK), "mining ice at the pole wall never re-prompts");
        assertFalse(HemispherePassage.rearmGestureArms(false, g.promptDist(), facingPole, g.promptDist(),
                HemispherePassage.GESTURE_HIT_ENTITY), "attacking an entity at the wall never re-prompts");
    }

    // ---- S10d crossing legibility: the prompt's destination label rides the SHARED crossing paths ----

    @Test
    void promptDestinationLabelComposesAntipodalXWithFarMeridianLabel() {
        // The prompt body computes its destination EXACTLY as the crossing does: antipodalX (L -> L+180),
        // then the arrival subtitle's formatter -- never re-derived math. Depart at 167 deg E (x=6970 on
        // xRadius 7500): the far side of the world is 13 deg W (x = 6970 - 7500 = -530 -> 12.72 -> "13°W").
        double xRadius = 7500.0;
        double dest = PoleArrivalSearch.antipodalX(6970.0, 0.0, xRadius);
        assertEquals(-530.0, dest, 1e-9);
        assertEquals("13°W", HemispherePassage.farMeridianLabel(dest, 0.0, xRadius));
        // And the TEST 97 canonical case: departing ~13 deg E must promise ~167 deg W.
        double dest2 = PoleArrivalSearch.antipodalX(541.7, 0.0, xRadius); // ~13 deg E
        assertEquals("167°W", HemispherePassage.farMeridianLabel(dest2, 0.0, xRadius));
    }
}
