package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link HemispherePassage} -- the E/W-edge crossing-prompt arm/re-arm state logic.
 *
 * <p>Pins the asymmetric hysteresis (disarm on prompt at 100, re-arm only past 564), the no-oscillation
 * guarantee at {@code PROMPT_AT}, the spawned/arrived-in-band => disarmed contract, the server anti-exploit
 * gate, the mirror-X geometry (identical Classic/Mercator), NaN safety, and that the band fits sanely inside
 * the smallest (Itty-Bitty Classic, xRadius 3750) world.
 */
class HemispherePassageTest {

    // ---- threshold sanity / band discipline ------------------------------------------------

    @Test
    void promptIsInsideFogWhichIsInsideRearm() {
        // PROMPT_AT < FOG_START < REARM_AT -- the whole point of the asymmetric hysteresis.
        assertTrue(HemispherePassage.PROMPT_AT < HemispherePassage.FOG_START);
        assertTrue(HemispherePassage.FOG_START < HemispherePassage.REARM_AT);
    }

    @Test
    void rearmMarginMeetsDeadZoneDiscipline() {
        // margin >= DEAD_ZONE (64); the sweeper's band floor is DEAD_ZONE+32 (96) and PROMPT_AT (100) clears it.
        assertTrue(HemispherePassage.REARM_MARGIN >= HemisphereCrossing.DEAD_ZONE_BLOCKS);
        assertTrue(HemispherePassage.PROMPT_AT >= HemisphereCrossing.DEAD_ZONE_BLOCKS + 32.0);
    }

    @Test
    void bandFitsInsideSmallestClassicWorld() {
        // Itty-Bitty Classic xRadius = 3750. The whole arm/prompt geometry (out to REARM_AT = 564) must sit
        // well inside it, leaving the vast interior prompt-free, and FOG_START <= ~15% of the radius.
        final double ittyBittyXRadius = 3750.0;
        assertTrue(HemispherePassage.REARM_AT < ittyBittyXRadius);
        assertTrue(HemispherePassage.FOG_START <= 0.15 * ittyBittyXRadius);
    }

    // ---- arming / re-arming ----------------------------------------------------------------

    @Test
    void armedPlayerReachingEdgePromptsThenDisarms() {
        HemispherePassage.Decision d = HemispherePassage.evaluate(true, 80.0);
        assertTrue(d.openPrompt(), "armed + inside PROMPT_AT should open the prompt");
        assertFalse(d.nextArmed(), "opening the prompt disarms");
    }

    @Test
    void armedPlayerStillOutsidePromptDoesNotPromptButStaysArmed() {
        HemispherePassage.Decision d = HemispherePassage.evaluate(true, 300.0); // in fog, not yet at prompt
        assertFalse(d.openPrompt());
        assertTrue(d.nextArmed(), "still armed until they actually reach PROMPT_AT");
    }

    @Test
    void disarmedPlayerDoesNotRearmInsideTheStickyBand() {
        // Between PROMPT_AT (100) and REARM_AT (564): a disarmed player never re-arms and never re-prompts.
        for (double dist : new double[] {101.0, 200.0, 400.0, 500.0, 563.0}) {
            HemispherePassage.Decision d = HemispherePassage.evaluate(false, dist);
            assertFalse(d.openPrompt(), "no prompt while disarmed at dist=" + dist);
            assertFalse(d.nextArmed(), "must stay disarmed at dist=" + dist);
        }
    }

    @Test
    void disarmedPlayerRearmsOnlyPastRearmThreshold() {
        assertFalse(HemispherePassage.evaluate(false, HemispherePassage.REARM_AT).nextArmed(),
                "exactly at REARM_AT is not yet past it");
        assertTrue(HemispherePassage.evaluate(false, HemispherePassage.REARM_AT + 1.0).nextArmed(),
                "past REARM_AT the arm re-arms");
    }

    // ---- no oscillation at PROMPT_AT --------------------------------------------------------

    @Test
    void noOscillationHoveringAtPromptDistance() {
        // Player hovers right at the prompt distance for many ticks. The prompt must fire exactly ONCE, then
        // the state stays disarmed (no flapping) as long as they never clear REARM_AT.
        boolean armed = true;
        int prompts = 0;
        for (int tick = 0; tick < 100; tick++) {
            double dist = 100.0 + (tick % 2 == 0 ? -1.0 : +1.0); // wobble 99 <-> 101 across PROMPT_AT
            HemispherePassage.Decision d = HemispherePassage.evaluate(armed, dist);
            if (d.openPrompt()) {
                prompts++;
            }
            armed = d.nextArmed();
        }
        assertEquals(1, prompts, "hovering across PROMPT_AT must prompt exactly once");
    }

    @Test
    void slidingAlongEdgeAtConstantSmallDistanceNeverRepromptsAfterFirst() {
        // Simulate sliding N/S along the edge: dist stays ~50 for a long time.
        boolean armed = true;
        int prompts = 0;
        for (int tick = 0; tick < 200; tick++) {
            HemispherePassage.Decision d = HemispherePassage.evaluate(armed, 50.0);
            if (d.openPrompt()) {
                prompts++;
            }
            armed = d.nextArmed();
        }
        assertEquals(1, prompts, "sliding along the edge prompts once, never again without leaving the band");
    }

    @Test
    void blockedOpenRetriesWhileCallerHoldsArmCommit() {
        // P2 sweep A10 (third option): when the client can't actually open the prompt (a container is up), the
        // caller does NOT commit the disarm and re-evaluates next tick with armed still true. Pin that evaluate
        // is pure/stateless about this: the same armed=true input keeps yielding openPrompt until the caller
        // commits, so a blocked prompt fires the moment the blocker clears instead of being silently consumed.
        HemispherePassage.Decision blockedTick1 = HemispherePassage.evaluate(true, 80.0);
        assertTrue(blockedTick1.openPrompt());
        // Caller could not open -> keeps armed=true instead of committing blockedTick1.nextArmed().
        HemispherePassage.Decision blockedTick2 = HemispherePassage.evaluate(true, 80.0);
        assertTrue(blockedTick2.openPrompt(), "still armed => still asks to open on the retry tick");
        // Blocker clears -> caller opens and commits the disarm; next tick must not re-prompt.
        assertFalse(blockedTick2.nextArmed());
        assertFalse(HemispherePassage.evaluate(blockedTick2.nextArmed(), 80.0).openPrompt());
    }

    // ---- spawned / arrived-in-band => disarmed ---------------------------------------------

    @Test
    void arrivedInBandSeededDisarmedDoesNotReprompt() {
        // Mirror-X lands the player at the same small border distance on the far side. Arrival seeds
        // armed=false; the player must NOT be re-prompted while still inside the band.
        boolean armed = false; // externally seeded by the S2C arrival
        double arrivalDist = 90.0; // identical border distance to where they crossed from
        int prompts = 0;
        for (int tick = 0; tick < 50; tick++) {
            HemispherePassage.Decision d = HemispherePassage.evaluate(armed, arrivalDist);
            if (d.openPrompt()) {
                prompts++;
            }
            armed = d.nextArmed();
        }
        assertEquals(0, prompts, "an arrived-in-band disarmed player must not self-reprompt");
    }

    @Test
    void arrivedPlayerRearmsAfterFullExitThenCanPromptAgain() {
        boolean armed = false; // arrived disarmed in-band
        // Walk fully inland past REARM_AT -> re-arm.
        armed = HemispherePassage.evaluate(armed, 1000.0).nextArmed();
        assertTrue(armed);
        // Return to the edge -> a fresh, legitimate prompt.
        HemispherePassage.Decision back = HemispherePassage.evaluate(armed, 60.0);
        assertTrue(back.openPrompt());
        assertFalse(back.nextArmed());
    }

    // ---- server anti-exploit gate ----------------------------------------------------------

    @Test
    void serverAcceptsCrossOnlyNearEdge() {
        assertTrue(HemispherePassage.serverAcceptsCross(0.0));
        assertTrue(HemispherePassage.serverAcceptsCross(HemispherePassage.PROMPT_AT));
        assertTrue(HemispherePassage.serverAcceptsCross(
                HemispherePassage.PROMPT_AT + HemispherePassage.SERVER_CROSS_SLACK));
    }

    @Test
    void serverRejectsSpoofedCrossFromInland() {
        assertFalse(HemispherePassage.serverAcceptsCross(
                HemispherePassage.PROMPT_AT + HemispherePassage.SERVER_CROSS_SLACK + 1.0));
        assertFalse(HemispherePassage.serverAcceptsCross(400.0), "deep in the fog band but past the prompt+slack");
        assertFalse(HemispherePassage.serverAcceptsCross(3000.0), "inland spoof rejected");
    }

    @Test
    void serverRejectsNanOrNegativeDistance() {
        assertFalse(HemispherePassage.serverAcceptsCross(Double.NaN));
        assertFalse(HemispherePassage.serverAcceptsCross(-1.0));
    }

    // ---- mirror-X geometry (identical Classic / Mercator) ----------------------------------

    @Test
    void mirrorXReflectsAboutCenterKeepingLatitudeConcept() {
        // Classic: xRadius 3750, player at +3000 -> -3000.
        assertEquals(-3000.0, HemispherePassage.mirrorX(3000.0, 0.0), 1e-9);
        // Mercator: wider xRadius 7500, player at +7000 -> -7000. SAME formula, only the range differs.
        assertEquals(-7000.0, HemispherePassage.mirrorX(7000.0, 0.0), 1e-9);
        // West-side player mirrors to the east.
        assertEquals(2500.0, HemispherePassage.mirrorX(-2500.0, 0.0), 1e-9);
    }

    @Test
    void mirrorXIsAnInvolutionAndPreservesBorderDistance() {
        // Mirroring twice returns the original; and the mirrored point is the same distance from either edge,
        // which is exactly why arrival lands in-band (the spawned-in-band contract above).
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
        HemispherePassage.Decision d = HemispherePassage.evaluate(false, Double.NaN);
        assertFalse(d.openPrompt());
        assertTrue(d.nextArmed(), "a bad distance read must not trap the player disarmed");
    }

    // ---- B-5-P2 approach fog (A2): the pure distance->fog curves ---------------------------

    @Test
    void approachProgressEndpointsAndClamp() {
        // 0 at/beyond FOG_START (500); 1 at/inside PROMPT_AT (100); clamped both sides.
        assertEquals(0.0, HemispherePassage.approachProgress(HemispherePassage.FOG_START), 1e-9);
        assertEquals(0.0, HemispherePassage.approachProgress(600.0), 1e-9);
        assertEquals(1.0, HemispherePassage.approachProgress(HemispherePassage.PROMPT_AT), 1e-9);
        assertEquals(1.0, HemispherePassage.approachProgress(0.0), 1e-9);
    }

    @Test
    void approachProgressIsMonotonicIntoTheEdge() {
        double prev = -1.0;
        // Walking IN (500 -> 100), progress must strictly increase.
        for (double dist = 500.0; dist >= 100.0; dist -= 20.0) {
            double p = HemispherePassage.approachProgress(dist);
            assertTrue(p >= prev, "approach progress must not decrease as the edge nears (dist=" + dist + ")");
            prev = p;
        }
    }

    @Test
    void approachFogIsSeamFreeAtAndBeyondFogStart() {
        // At/beyond FOG_START the caller's own vanilla fog is returned unchanged -- no onset ring.
        float vEnd = 240.0f;
        float vStart = 40.0f;
        assertEquals(vEnd, HemispherePassage.approachFogEnd(vEnd, HemispherePassage.FOG_START), 1e-4f);
        assertEquals(vEnd, HemispherePassage.approachFogEnd(vEnd, 700.0), 1e-4f);
        assertEquals(vStart,
                HemispherePassage.approachFogStart(vStart, vEnd, HemispherePassage.FOG_START), 1e-4f);
        assertEquals(0.0f, HemispherePassage.approachFogEndFraction(HemispherePassage.FOG_START), 1e-6f);
    }

    @Test
    void approachFogOnlyEverTightens() {
        float vEnd = 240.0f;
        for (double dist = 500.0; dist >= 0.0; dist -= 25.0) {
            float end = HemispherePassage.approachFogEnd(vEnd, dist);
            assertTrue(end <= vEnd + 1e-4f, "fog end must never loosen past vanilla (dist=" + dist + ")");
            float start = HemispherePassage.approachFogStart(40.0f, end, dist);
            assertTrue(start < end, "START must always stay below END (dist=" + dist + ")");
        }
    }

    @Test
    void approachFogReachesNearOpaqueWallAtTheEdge() {
        // At the edge the render-distance end collapses to ~APPROACH_FOG_END_NEAR (a near-opaque wall) regardless
        // of how far the player's vanilla view distance was.
        float end = HemispherePassage.approachFogEnd(512.0f, 0.0);
        assertEquals(HemispherePassage.APPROACH_FOG_END_NEAR, end, 1e-3f);
        float start = HemispherePassage.approachFogStart(40.0f, end, 0.0);
        assertEquals(HemispherePassage.APPROACH_FOG_START_NEAR, start,
                Math.abs(HemispherePassage.APPROACH_FOG_START_NEAR - end)); // near NEAR, still below end
        assertTrue(start < end);
    }

    @Test
    void approachFogIsEaseInNotLinear() {
        // "Weather rolling in": at the mid-point of the band the eased END fraction is BELOW the linear fraction
        // (thin far out, thickening steeply toward the edge). Progress 0.5 -> 0.5^1.6 < 0.5.
        double dist = (HemispherePassage.FOG_START + HemispherePassage.PROMPT_AT) / 2.0; // progress ~0.5
        double linear = HemispherePassage.approachProgress(dist);
        float eased = HemispherePassage.approachFogEndFraction(dist);
        assertTrue(eased < linear, "ease-in: the fog builds slower than linear through the mid-band");
    }

    @Test
    void approachFogNanDistanceIsNoFog() {
        assertEquals(0.0, HemispherePassage.approachProgress(Double.NaN), 1e-9);
        assertEquals(240.0f, HemispherePassage.approachFogEnd(240.0f, Double.NaN), 1e-4f);
    }
}
