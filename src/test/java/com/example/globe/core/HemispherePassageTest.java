package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link HemispherePassage} -- the E/W-edge crossing-prompt arm/re-arm state logic and the
 * approach-fog curves, now DEGREE-ANCHORED (redesign 2026-07-12): the prompt/re-arm/fog distances are resolved
 * PER WORLD by {@link EdgeGeometry} from the intended X radius and passed IN, so these tests drive them off a
 * representative world (Itty-Bitty Classic, xRadius 3750) and off the raw {@link EdgeGeometry.Resolved} numbers.
 *
 * <p>Pins the asymmetric hysteresis (disarm on the prompt line, re-arm past a MODEST re-arm line), the
 * no-oscillation guarantee at the prompt distance, the TEST-83 "declined prompt re-offers after a modest
 * walk-back" fix, the arrived-out-of-band arm behaviour, the server anti-exploit gate, the mirror-X geometry
 * (identical Classic/Mercator), NaN safety, and the fog curves keyed on the resolved rampStart/climax.
 */
class HemispherePassageTest {

    // Representative resolved geometry: Itty-Bitty Classic (the smallest world, where the floors bind).
    private static final EdgeGeometry.Resolved ITTY = EdgeGeometry.resolve(3750.0);
    private static final double PROMPT = ITTY.promptDist();     // 40 (floored)
    private static final double REARM = ITTY.rearmDist();       // 104 (dead-zone floor)
    private static final double RAMP = ITTY.rampStartDist();    // 160 (fog-band floor)
    private static final double CLIMAX = ITTY.fogClimaxDist();  // 40 (== prompt)

    private static HemispherePassage.Decision eval(boolean armed, double dist) {
        return HemispherePassage.evaluate(armed, dist, PROMPT, REARM);
    }

    // ---- threshold sanity / band discipline ------------------------------------------------

    @Test
    void promptIsInsideRearmWhichIsInsideRampStart() {
        // prompt < rearm < rampStart -- re-arm is a MODEST walk-back that sits INSIDE the (purely visual) fog
        // band, NOT past it (the TEST-83 fix). In distance-from-edge terms, closer to the wall = smaller.
        assertTrue(PROMPT < REARM);
        assertTrue(REARM < RAMP);
    }

    @Test
    void rearmMarginBeatsJitterWithoutStranding() {
        // The re-arm dead band must be at least the DEAD_ZONE (64), far wider than any per-tick jitter, so it
        // cannot machine-gun, yet it stays inside the visual fog band (a modest "I left and came back").
        assertTrue(REARM - PROMPT >= HemisphereCrossing.DEAD_ZONE_BLOCKS);
        assertTrue(REARM < RAMP);
    }

    @Test
    void bandFitsInsideSmallestClassicWorld() {
        // Itty-Bitty Classic xRadius = 3750. The whole arm/prompt geometry (out to the fog onset) must sit well
        // inside it, leaving the vast interior prompt-free.
        assertTrue(RAMP < 3750.0);
        assertTrue(RAMP <= 0.15 * 3750.0 + 1e-6); // ~160 = ~4.3% of the radius
    }

    // ---- arming / re-arming ----------------------------------------------------------------

    @Test
    void armedPlayerReachingEdgePromptsThenDisarms() {
        HemispherePassage.Decision d = eval(true, PROMPT - 20.0);
        assertTrue(d.openPrompt(), "armed + inside the prompt line should open the prompt");
        assertFalse(d.nextArmed(), "opening the prompt disarms");
    }

    @Test
    void armedPlayerStillOutsidePromptDoesNotPromptButStaysArmed() {
        HemispherePassage.Decision d = eval(true, (REARM + RAMP) / 2.0); // in fog, not yet at prompt
        assertFalse(d.openPrompt());
        assertTrue(d.nextArmed(), "still armed until they actually reach the prompt line");
    }

    @Test
    void disarmedPlayerDoesNotRearmInsideTheStickyBand() {
        // Between the prompt line and the re-arm line: a disarmed player never re-arms and never re-prompts.
        for (double dist : new double[] {PROMPT + 1.0, (PROMPT + REARM) / 2.0, REARM - 1.0}) {
            HemispherePassage.Decision d = eval(false, dist);
            assertFalse(d.openPrompt(), "no prompt while disarmed at dist=" + dist);
            assertFalse(d.nextArmed(), "must stay disarmed at dist=" + dist);
        }
    }

    @Test
    void disarmedPlayerRearmsOnlyPastRearmThreshold() {
        assertFalse(eval(false, REARM).nextArmed(), "exactly at the re-arm line is not yet past it");
        assertTrue(eval(false, REARM + 1.0).nextArmed(), "past the re-arm line the arm re-arms");
    }

    // ---- no oscillation at the prompt line -------------------------------------------------

    @Test
    void noOscillationHoveringAtPromptDistance() {
        boolean armed = true;
        int prompts = 0;
        for (int tick = 0; tick < 100; tick++) {
            double dist = PROMPT + (tick % 2 == 0 ? -1.0 : +1.0); // wobble across the prompt line
            HemispherePassage.Decision d = eval(armed, dist);
            if (d.openPrompt()) {
                prompts++;
            }
            armed = d.nextArmed();
        }
        assertEquals(1, prompts, "hovering across the prompt line must prompt exactly once");
    }

    @Test
    void slidingAlongEdgeAtConstantSmallDistanceNeverRepromptsAfterFirst() {
        boolean armed = true;
        int prompts = 0;
        for (int tick = 0; tick < 200; tick++) {
            HemispherePassage.Decision d = eval(armed, PROMPT / 2.0);
            if (d.openPrompt()) {
                prompts++;
            }
            armed = d.nextArmed();
        }
        assertEquals(1, prompts, "sliding along the edge prompts once, never again without leaving the band");
    }

    @Test
    void blockedOpenRetriesWhileCallerHoldsArmCommit() {
        HemispherePassage.Decision blockedTick1 = eval(true, PROMPT - 20.0);
        assertTrue(blockedTick1.openPrompt());
        HemispherePassage.Decision blockedTick2 = eval(true, PROMPT - 20.0);
        assertTrue(blockedTick2.openPrompt(), "still armed => still asks to open on the retry tick");
        assertFalse(blockedTick2.nextArmed());
        assertFalse(eval(blockedTick2.nextArmed(), PROMPT - 20.0).openPrompt());
    }

    // ---- TEST-83 live regression: a declined/dismissed prompt re-offers after a modest walk-back ----

    private int countPromptsAlongWalk(boolean startArmed, double[] distSequence) {
        boolean armed = startArmed;
        int prompts = 0;
        for (double dist : distSequence) {
            HemispherePassage.Decision d = eval(armed, dist);
            if (d.openPrompt()) {
                prompts++;
            }
            armed = d.nextArmed();
        }
        return prompts;
    }

    @Test
    void declinedPromptReoffersAfterModestWalkBackAndReturn() {
        // arm -> reach edge (prompt #1, disarm) -> turn back -> walk out just past the re-arm line -> return.
        double[] walk = {
                PROMPT - 20.0,                 // approach: prompt #1 fires, disarm
                PROMPT - 10.0, PROMPT + 20.0,  // dithering at the edge -- must NOT re-prompt
                REARM - 20.0, REARM + 5.0,     // walk out past the re-arm line -> re-arm
                REARM - 40.0, PROMPT - 10.0,   // return to the edge -> prompt #2
        };
        assertEquals(2, countPromptsAlongWalk(true, walk),
                "a declined prompt must re-offer after a modest walk-out past the re-arm line and back");
    }

    @Test
    void escDismissReoffersIdenticallyToTurnBack() {
        double[] walk = {PROMPT - 25.0, PROMPT + 10.0, RAMP - 20.0 /*out past re-arm*/, PROMPT, PROMPT - 40.0};
        assertEquals(2, countPromptsAlongWalk(true, walk),
                "ESC-dismiss must re-offer the same as turn-back after a walk-out and return");
    }

    @Test
    void modestWalkBackShortOfFogOnsetNowRearms() {
        // At a distance past the re-arm line but still inside the visual fog band, a disarmed player RE-ARMS.
        double midFog = (REARM + RAMP) / 2.0;
        assertTrue(eval(false, midFog).nextArmed(),
                "past the re-arm line must re-arm");
        assertTrue(midFog < RAMP,
                "and it is still inside the visual fog band, matching 'I left the heavy fog and came back'");
    }

    @Test
    void arrivalSeededPlayerReoffersAfterModestWalkBack() {
        // A disarmed-seeded player (belt-and-suspenders arrival seed) re-offers after the modest walk-back.
        double[] walk = {
                PROMPT + 5.0, PROMPT + 40.0,   // in-band, dithering -- no prompt
                REARM - 10.0, REARM + 5.0,     // modest walk-out past the re-arm line -> re-arm
                REARM - 50.0, PROMPT - 20.0,   // return -> first legitimate prompt on the far side
        };
        assertEquals(1, countPromptsAlongWalk(false, walk),
                "an arrival-seeded player must be able to prompt after a modest walk-out and return");
    }

    // ---- TEST-84 live regression: the underground GATE must never freeze the re-arm (caller-integration) ----

    private int countPromptsGatedWalk(boolean startArmed, double[] dists, boolean[] canOpen) {
        assertEquals(dists.length, canOpen.length, "test walk arrays must be the same length");
        boolean armed = startArmed;
        int prompts = 0;
        for (int i = 0; i < dists.length; i++) {
            HemispherePassage.Decision d = HemispherePassage.evaluateGated(armed, dists[i], canOpen[i], PROMPT, REARM);
            if (d.openPrompt()) {
                prompts++;
            }
            armed = d.nextArmed();
        }
        return prompts;
    }

    @Test
    void rearmIsTerrainIndependent_disarmedPastRearmRearmsEvenUnderground() {
        double past = (REARM + RAMP) / 2.0;
        assertTrue(HemispherePassage.evaluateGated(false, past, true, PROMPT, REARM).nextArmed(),
                "re-arm past the re-arm line on the surface");
        assertTrue(HemispherePassage.evaluateGated(false, past, false, PROMPT, REARM).nextArmed(),
                "re-arm past the re-arm line UNDERGROUND too -- re-arm is distance-only, never gated on terrain");
    }

    @Test
    void undergroundWalkOutStillRearms_theTest84LiveBug() {
        double past = (REARM + RAMP) / 2.0;
        double[] dists   = { PROMPT - 20.0, PROMPT + 20.0, REARM - 5.0, REARM + 5.0, past, REARM - 5.0, PROMPT + 20.0, PROMPT - 30.0 };
        boolean[] canOpen = { true,          true,          false,       false,       false, false,       true,          true };
        assertEquals(2, countPromptsGatedWalk(true, dists, canOpen),
                "a walk-out that crosses the re-arm line while underground must STILL re-arm and re-prompt on return");
    }

    @Test
    void undergroundAtEdgeHoldsTheOneShotThenOpensOnSurfacing() {
        double[] dists   = { PROMPT - 10.0, PROMPT - 20.0, PROMPT - 25.0, PROMPT - 30.0, PROMPT - 30.0 };
        boolean[] canOpen = { false, false, false, true, true };
        assertEquals(1, countPromptsGatedWalk(true, dists, canOpen),
                "underground at the edge holds the one-shot and fires exactly once on surfacing");
    }

    @Test
    void screenBlockedOpenHoldsArmThenOpensOnce_gatedParityWithA10() {
        double[] dists   = { PROMPT - 20.0, PROMPT - 20.0, PROMPT - 20.0, PROMPT - 20.0 };
        boolean[] canOpen = { false, false, true, true };
        assertEquals(1, countPromptsGatedWalk(true, dists, canOpen),
                "a screen-blocked prompt is held (not consumed) and fires once when the screen clears");
    }

    @Test
    void mixedTerrainRepeatedDeclineCyclesPromptExactlyOncePerApproach() {
        double past = (REARM + RAMP) / 2.0;
        double[] dists = {
                past + 40.0, REARM, PROMPT - 10.0,       // approach #1 -> prompt (disarm)
                PROMPT - 10.0, PROMPT + 50.0, REARM - 5.0, // turn back, walk out (still < re-arm, no re-arm)
                REARM + 5.0, past + 60.0,                 // ...now past the re-arm line -> re-arm
                REARM, PROMPT + 20.0, PROMPT - 20.0,      // approach #2 -> prompt (disarm)
                REARM + 5.0, PROMPT, PROMPT - 40.0,       // out past re-arm and back -> would be #3 if not blocked...
        };
        boolean[] canOpen = {
                true,  true,  true,
                true,  true,  false,
                false, true,
                true,  true,  true,
                true,  false, false,
        };
        assertEquals(2, countPromptsGatedWalk(true, dists, canOpen),
                "exactly one prompt per genuine surface approach; the final roofed approach is held, not burned");
    }

    // ---- arrived-out-of-band => arms naturally ---------------------------------------------

    @Test
    void arrivedOutOfBandSeededDisarmedRearmsNextTickWithoutPrompting() {
        // The redesign drops the player PAST the fog (dist == arrivalDist > rearm). The disarmed seed is
        // harmless: the very next tick re-arms it (no prompt), exactly like a walk-out.
        boolean armed = false; // externally seeded by the S2C arrival
        HemispherePassage.Decision d = eval(armed, ITTY.arrivalDist());
        assertFalse(d.openPrompt(), "arriving past the fog never self-prompts");
        assertTrue(d.nextArmed(), "arriving out-of-band re-arms on the next tick");
    }

    @Test
    void arrivalDistanceIsBeyondTheRearmLine() {
        // Pin the invariant the arrival seed relies on: arrival lands past the re-arm line, so the arm re-arms
        // naturally there (no in-band self-reprompt possible).
        assertTrue(ITTY.arrivalDist() > REARM, "arrival must land past the re-arm line");
        assertTrue(ITTY.arrivalDist() > RAMP, "and past the fog onset (past the fog, not in the thick of it)");
    }

    @Test
    void arrivedPlayerRearmsAfterFullExitThenCanPromptAgain() {
        boolean armed = false; // arrived disarmed
        armed = eval(armed, 3000.0).nextArmed();
        assertTrue(armed);
        HemispherePassage.Decision back = eval(armed, PROMPT - 40.0);
        assertTrue(back.openPrompt());
        assertFalse(back.nextArmed());
    }

    // ---- server anti-exploit gate ----------------------------------------------------------

    @Test
    void serverAcceptsCrossOnlyNearEdge() {
        assertTrue(HemispherePassage.serverAcceptsCross(0.0, PROMPT));
        assertTrue(HemispherePassage.serverAcceptsCross(PROMPT, PROMPT));
        assertTrue(HemispherePassage.serverAcceptsCross(PROMPT + HemispherePassage.SERVER_CROSS_SLACK, PROMPT));
    }

    @Test
    void serverRejectsSpoofedCrossFromInland() {
        assertFalse(HemispherePassage.serverAcceptsCross(PROMPT + HemispherePassage.SERVER_CROSS_SLACK + 1.0, PROMPT));
        assertFalse(HemispherePassage.serverAcceptsCross(RAMP, PROMPT), "deep in the fog band but past the prompt+slack");
        assertFalse(HemispherePassage.serverAcceptsCross(3000.0, PROMPT), "inland spoof rejected");
    }

    @Test
    void serverRejectsNanOrNegativeDistance() {
        assertFalse(HemispherePassage.serverAcceptsCross(Double.NaN, PROMPT));
        assertFalse(HemispherePassage.serverAcceptsCross(-1.0, PROMPT));
    }

    // ---- mirror-X geometry (identical Classic / Mercator) ----------------------------------

    @Test
    void mirrorXReflectsAboutCenterKeepingLatitudeConcept() {
        assertEquals(-3000.0, HemispherePassage.mirrorX(3000.0, 0.0), 1e-9);
        assertEquals(-7000.0, HemispherePassage.mirrorX(7000.0, 0.0), 1e-9);
        assertEquals(2500.0, HemispherePassage.mirrorX(-2500.0, 0.0), 1e-9);
    }

    @Test
    void mirrorXIsAnInvolutionAndPreservesBorderDistance() {
        double x = 6800.0;
        double centerX = 0.0;
        double mx = HemispherePassage.mirrorX(x, centerX);
        assertEquals(x, HemispherePassage.mirrorX(mx, centerX), 1e-9);
        double xRadius = 7500.0;
        double distFromEastEdge = xRadius - Math.abs(x - centerX);
        double mirroredDistFromWestEdge = xRadius - Math.abs(mx - centerX);
        assertEquals(distFromEastEdge, mirroredDistFromWestEdge, 1e-9);
    }

    // ---- NaN safety ------------------------------------------------------------------------

    @Test
    void nanDistanceTreatedAsFarAndRearms() {
        HemispherePassage.Decision d = eval(false, Double.NaN);
        assertFalse(d.openPrompt());
        assertTrue(d.nextArmed(), "a bad distance read must not trap the player disarmed");
    }

    // ---- B-5-P2 approach fog (A2): the pure distance->fog curves ---------------------------
    // The fog band runs from RAMP (onset) down to CLIMAX (full). Same numbers the prompt/particles use.

    @Test
    void approachProgressEndpointsAndClamp() {
        assertEquals(0.0, HemispherePassage.approachProgress(RAMP, RAMP, CLIMAX), 1e-9);
        assertEquals(0.0, HemispherePassage.approachProgress(RAMP + 100.0, RAMP, CLIMAX), 1e-9);
        assertEquals(1.0, HemispherePassage.approachProgress(CLIMAX, RAMP, CLIMAX), 1e-9);
        assertEquals(1.0, HemispherePassage.approachProgress(0.0, RAMP, CLIMAX), 1e-9);
    }

    @Test
    void approachProgressIsMonotonicIntoTheEdge() {
        double prev = -1.0;
        for (double dist = RAMP; dist >= CLIMAX; dist -= 5.0) {
            double p = HemispherePassage.approachProgress(dist, RAMP, CLIMAX);
            assertTrue(p >= prev, "approach progress must not decrease as the edge nears (dist=" + dist + ")");
            prev = p;
        }
    }

    @Test
    void approachFogIsSeamFreeAtAndBeyondFogOnset() {
        float vEnd = 240.0f;
        float vStart = 40.0f;
        assertEquals(vEnd, HemispherePassage.approachFogEnd(vEnd, RAMP, RAMP, CLIMAX), 1e-4f);
        assertEquals(vEnd, HemispherePassage.approachFogEnd(vEnd, RAMP + 200.0, RAMP, CLIMAX), 1e-4f);
        assertEquals(vStart, HemispherePassage.approachFogStart(vStart, vEnd, RAMP, RAMP, CLIMAX), 1e-4f);
        assertEquals(0.0f, HemispherePassage.approachFogEndFraction(RAMP, RAMP, CLIMAX), 1e-6f);
    }

    @Test
    void approachFogOnlyEverTightens() {
        float vEnd = 240.0f;
        for (double dist = RAMP; dist >= 0.0; dist -= 8.0) {
            float end = HemispherePassage.approachFogEnd(vEnd, dist, RAMP, CLIMAX);
            assertTrue(end <= vEnd + 1e-4f, "fog end must never loosen past vanilla (dist=" + dist + ")");
            float start = HemispherePassage.approachFogStart(40.0f, end, dist, RAMP, CLIMAX);
            assertTrue(start < end, "START must always stay below END (dist=" + dist + ")");
        }
    }

    @Test
    void approachFogReachesNearOpaqueWallAtTheEdge() {
        float end = HemispherePassage.approachFogEnd(512.0f, 0.0, RAMP, CLIMAX);
        assertEquals(HemispherePassage.APPROACH_FOG_END_NEAR, end, 1e-3f);
        float start = HemispherePassage.approachFogStart(40.0f, end, 0.0, RAMP, CLIMAX);
        assertEquals(HemispherePassage.APPROACH_FOG_START_NEAR, start,
                Math.abs(HemispherePassage.APPROACH_FOG_START_NEAR - end));
        assertTrue(start < end);
    }

    @Test
    void approachFogIsEaseInNotLinear() {
        double dist = (RAMP + CLIMAX) / 2.0; // progress ~0.5
        double linear = HemispherePassage.approachProgress(dist, RAMP, CLIMAX);
        float eased = HemispherePassage.approachFogEndFraction(dist, RAMP, CLIMAX);
        assertTrue(eased < linear, "ease-in: the fog builds slower than linear through the mid-band");
    }

    @Test
    void approachFogNanDistanceIsNoFog() {
        assertEquals(0.0, HemispherePassage.approachProgress(Double.NaN, RAMP, CLIMAX), 1e-9);
        assertEquals(240.0f, HemispherePassage.approachFogEnd(240.0f, Double.NaN, RAMP, CLIMAX), 1e-4f);
    }

    @Test
    void approachFogDegenerateBandIsNoFog() {
        // A pathological rampStart <= climax yields no band (0 progress), never a divide-by-zero.
        assertEquals(0.0, HemispherePassage.approachProgress(50.0, 40.0, 40.0), 1e-9);
        assertEquals(0.0, HemispherePassage.approachProgress(50.0, 30.0, 40.0), 1e-9);
    }
}
