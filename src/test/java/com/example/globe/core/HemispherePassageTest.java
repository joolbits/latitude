package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link HemispherePassage} -- the E/W-edge crossing-prompt arm/re-arm logic, the
 * arrival-aware PHASE state machine (edge-flow rework 2026-07-13), and the approach-fog curves. All distances
 * are DEGREE-ANCHORED and resolved PER WORLD by {@link EdgeGeometry}, so these tests drive them off
 * representative worlds (Itty-Bitty Classic xRadius 3750 where the floors bind; Regular Wide 20000 for clean
 * pure-degree numbers).
 *
 * <p>Pins: the asymmetric hysteresis; no-oscillation at the prompt line; the TEST-83 declined-prompt
 * re-offer; the new edge-flow ordering (re-arm now sits OUTSIDE the fog onset); the PHASE machine -- the
 * post-arrival EDGE auto-re-prompt one-shot, the SEEDED_DISARMED vs ordinary DISARMED distinction, and the
 * exact Peetsa-confirmed flow; arrival landing ON the prompt line with NO self-prompt; the terrain-independent
 * re-arm; the click re-prompt gesture; the server anti-exploit gate; mirror-X; NaN safety; and the fog curves.
 */
class HemispherePassageTest {

    // Representative resolved geometry: Itty-Bitty Classic (the smallest world, where the floors bind).
    private static final EdgeGeometry.Resolved ITTY = EdgeGeometry.resolve(3750.0);
    private static final double PROMPT = ITTY.promptDist();     // 41.67 (2 deg, beats the 40 floor)
    private static final double REARM = ITTY.rearmDist();       // 108 (rampStart + ORDER_STEP)
    private static final double RAMP = ITTY.rampStartDist();    // 100 (climax + FOG_BAND)
    private static final double CLIMAX = ITTY.fogClimaxDist();  // 40 (179 deg, floored)
    private static final double PAST_REARM = REARM + 20.0;      // a clean walk-out past the re-arm line

    // Regular Wide (pure degrees) for the phase-machine walks.
    private static final EdgeGeometry.Resolved REG = EdgeGeometry.resolve(20000.0);
    private static final double P = REG.promptDist();           // 222.22 (178 deg)
    private static final double RA = REG.rearmDist();           // 333.33 (177 deg)
    private static final double EDGE = REG.edgeRepromptDist();  // 44.44 (179.6 deg)

    private static final HemispherePassage.Phase ARMED = HemispherePassage.Phase.ARMED;
    private static final HemispherePassage.Phase DISARMED = HemispherePassage.Phase.DISARMED;
    private static final HemispherePassage.Phase SEEDED = HemispherePassage.Phase.SEEDED_DISARMED;
    private static final HemispherePassage.Phase EDGE_PROMPTED = HemispherePassage.Phase.EDGE_PROMPTED;

    private static HemispherePassage.Decision eval(boolean armed, double dist) {
        return HemispherePassage.evaluate(armed, dist, PROMPT, REARM);
    }

    private static HemispherePassage.PhaseDecision ph(HemispherePassage.Phase phase, double dist) {
        return HemispherePassage.evaluatePhase(phase, dist, true, P, RA, EDGE);
    }

    private static HemispherePassage.PhaseDecision phGated(HemispherePassage.Phase phase, double dist, boolean canOpen) {
        return HemispherePassage.evaluatePhase(phase, dist, canOpen, P, RA, EDGE);
    }

    // ---- threshold sanity / edge-flow ordering ---------------------------------------------

    @Test
    void edgeFlowOrdering_promptInsideFogOnsetInsideRearm() {
        // Edge-flow rework: the prompt (178) now sits INSIDE the fog onset (177.5), and the re-arm line (177)
        // sits OUTSIDE the fog. In distance-from-edge terms (closer to the wall = smaller): prompt < rampStart
        // < rearm. (The old nest was prompt > rearm > rampStart; moving the prompt in flipped it.)
        assertTrue(PROMPT < RAMP, "prompt sits inside the fog onset");
        assertTrue(RAMP < REARM, "the fog onset sits inside the re-arm line (re-arm is now outside the fog)");
    }

    @Test
    void rearmMarginBeatsJitterAndSitsOutsideTheFog() {
        // The re-arm dead band is at least the DEAD_ZONE (64), far wider than any per-tick jitter, so it cannot
        // machine-gun; and it now sits OUTSIDE the visual fog band (walk out of the fog to re-arm).
        assertTrue(REARM - PROMPT >= HemisphereCrossing.DEAD_ZONE_BLOCKS);
        assertTrue(REARM > RAMP);
    }

    @Test
    void wholeGeometryFitsInsideSmallestClassicWorld() {
        // Itty-Bitty Classic xRadius = 3750. The whole arm/prompt geometry (out to the advisory, the outermost
        // element) must sit well inside it, leaving the vast interior prompt-free.
        assertTrue(ITTY.advisoryDist() < 3750.0);
        assertTrue(ITTY.advisoryDist() <= 0.15 * 3750.0 + 1e-6); // ~116 = ~3% of the radius
    }

    // ---- arming / re-arming (the ordinary boolean cycle) -----------------------------------

    @Test
    void armedPlayerReachingEdgePromptsThenDisarms() {
        HemispherePassage.Decision d = eval(true, PROMPT - 20.0);
        assertTrue(d.openPrompt(), "armed + inside the prompt line should open the prompt");
        assertFalse(d.nextArmed(), "opening the prompt disarms");
    }

    @Test
    void armedPlayerStillOutsidePromptDoesNotPromptButStaysArmed() {
        HemispherePassage.Decision d = eval(true, (PROMPT + REARM) / 2.0); // in the sticky band, not yet at prompt
        assertFalse(d.openPrompt());
        assertTrue(d.nextArmed(), "still armed until they actually reach the prompt line");
    }

    @Test
    void disarmedPlayerDoesNotRearmInsideTheStickyBand() {
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
    void declinedPromptReoffersAfterWalkBackPastRearmAndReturn() {
        // arm -> reach edge (prompt #1, disarm) -> turn back -> walk out past the re-arm line -> return.
        double[] walk = {
                PROMPT - 20.0,                    // approach: prompt #1 fires, disarm
                PROMPT - 10.0, PROMPT + 20.0,     // dithering at the edge -- must NOT re-prompt
                REARM - 20.0, REARM + 5.0,        // walk out past the re-arm line -> re-arm
                REARM - 40.0, PROMPT - 10.0,      // return to the edge -> prompt #2
        };
        assertEquals(2, countPromptsAlongWalk(true, walk),
                "a declined prompt must re-offer after a walk-out past the re-arm line and back");
    }

    @Test
    void escDismissReoffersIdenticallyToTurnBack() {
        double[] walk = {PROMPT - 25.0, PROMPT + 10.0, REARM + 5.0 /*out past re-arm*/, PROMPT, PROMPT - 40.0};
        assertEquals(2, countPromptsAlongWalk(true, walk),
                "ESC-dismiss must re-offer the same as turn-back after a walk-out and return");
    }

    @Test
    void walkOutPastRearmRearmsAndItSitsOutsideTheFog() {
        // A disarmed player who walks out past the re-arm line RE-ARMS -- and, edge-flow rework, that line now
        // sits OUTSIDE the visual fog band, so re-arming means "walk out of the fog and a touch further".
        assertTrue(eval(false, REARM + 1.0).nextArmed(), "past the re-arm line must re-arm");
        assertTrue(REARM > RAMP, "and the re-arm line is outside the fog onset now");
    }

    @Test
    void arrivalSeededPlayerReoffersAfterWalkOut() {
        // A disarmed-seeded player (belt-and-suspenders arrival seed) re-offers after the walk-out past re-arm.
        double[] walk = {
                PROMPT + 5.0, PROMPT + 20.0,      // in-band, dithering -- no prompt
                REARM - 10.0, REARM + 5.0,        // walk-out past the re-arm line -> re-arm
                REARM - 50.0, PROMPT - 20.0,      // return -> first legitimate prompt on the far side
        };
        assertEquals(1, countPromptsAlongWalk(false, walk),
                "an arrival-seeded player must be able to prompt after a walk-out and return");
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
        assertTrue(HemispherePassage.evaluateGated(false, PAST_REARM, true, PROMPT, REARM).nextArmed(),
                "re-arm past the re-arm line on the surface");
        assertTrue(HemispherePassage.evaluateGated(false, PAST_REARM, false, PROMPT, REARM).nextArmed(),
                "re-arm past the re-arm line UNDERGROUND too -- re-arm is distance-only, never gated on terrain");
    }

    @Test
    void undergroundWalkOutStillRearms_theTest84LiveBug() {
        double[] dists   = { PROMPT - 20.0, PROMPT + 20.0, REARM - 5.0, REARM + 5.0, PAST_REARM, REARM - 5.0, PROMPT + 20.0, PROMPT - 30.0 };
        boolean[] canOpen = { true,          true,          false,       false,       false,      false,       true,          true };
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
        double[] dists = {
                PAST_REARM + 40.0, REARM, PROMPT - 10.0,   // approach #1 -> prompt (disarm)
                PROMPT - 10.0, PROMPT + 50.0, REARM - 5.0, // turn back, walk out (still < re-arm, no re-arm)
                REARM + 5.0, PAST_REARM + 60.0,            // ...now past the re-arm line -> re-arm
                REARM, PROMPT + 20.0, PROMPT - 20.0,       // approach #2 -> prompt (disarm)
                REARM + 5.0, PROMPT, PROMPT - 40.0,        // out past re-arm and back -> would be #3 if not blocked...
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

    // ---- edge-flow rework: the PHASE machine (post-arrival EDGE auto-re-prompt) -------------

    @Test
    void peetsaConfirmedFlow_arriveThenEdgeRepromptDeclineThenNormalApproach() {
        // The exact Peetsa-confirmed B-5 flow as a pure phase walk. (The 176-deg ADVISORY that leads the fog is
        // the EwBannerEnvelope's job, tested separately; here we track only the passage phase.)
        HemispherePassage.Phase phase = ARMED;

        // 1) approach from beyond the re-arm line -> stays ARMED, no prompt yet.
        HemispherePassage.PhaseDecision d = ph(phase, RA + 200.0);
        assertFalse(d.openPrompt());
        assertEquals(ARMED, d.nextPhase());
        phase = d.nextPhase();

        // 2) reach the prompt line (178 deg) -> prompt #1 opens, disarm.
        d = ph(phase, P);
        assertTrue(d.openPrompt(), "prompt #1 at 178");
        assertEquals(DISARMED, d.nextPhase());
        // player accepts -> crosses -> the S2C arrival SEEDS the far side (client onArrival): SEEDED_DISARMED,
        // landing on the arrival column (== the prompt line).
        phase = SEEDED;

        // 3) turn around, walk back toward the wall: NO prompt at 178 (arrival is on the prompt line, disarmed).
        d = ph(phase, P);
        assertFalse(d.openPrompt(), "no prompt at 178 after arriving (seeded disarmed)");
        assertEquals(SEEDED, d.nextPhase());
        phase = d.nextPhase();
        // deeper toward the wall but still short of the edge threshold: still no prompt.
        d = ph(phase, EDGE + 40.0);
        assertFalse(d.openPrompt());
        assertEquals(SEEDED, d.nextPhase());
        phase = d.nextPhase();

        // 4) reach the very edge (179.6 deg) -> the post-arrival EDGE auto-re-prompt fires ONCE.
        d = ph(phase, EDGE);
        assertTrue(d.openPrompt(), "auto edge re-prompt at the wall");
        assertEquals(EDGE_PROMPTED, d.nextPhase());
        phase = d.nextPhase();

        // 5) decline: sit / mill about at the wall -- the one-shot is consumed, it never re-opens.
        for (double dist : new double[] {EDGE - 5.0, 10.0, EDGE, EDGE - 2.0}) {
            d = ph(phase, dist);
            assertFalse(d.openPrompt(), "the declined edge prompt does not re-open at dist=" + dist);
            assertEquals(EDGE_PROMPTED, d.nextPhase());
            phase = d.nextPhase();
        }

        // 6) walk out past the re-arm line (177 deg) -> ARMED (normal rules resume).
        d = ph(phase, RA + 5.0);
        assertFalse(d.openPrompt());
        assertEquals(ARMED, d.nextPhase());
        phase = d.nextPhase();

        // 7) return -> a fresh approach prompts at 178 again.
        d = ph(phase, P - 5.0);
        assertTrue(d.openPrompt(), "fresh approach prompts at 178 again");
        assertEquals(DISARMED, d.nextPhase());
    }

    @Test
    void arrivalOnThePromptLineNeverSelfPrompts() {
        // Arrival lands EXACTLY on the prompt line (arrivalDist == promptDist). The seed is SEEDED_DISARMED, and
        // the ordinary prompt requires ARMED, so it never self-prompts -- and it does NOT re-arm (arrival is well
        // inside the re-arm line), so it HOLDS. Pinned for both the phase machine and the boolean sub-model.
        assertEquals(REG.promptDist(), REG.arrivalDist(), 1e-9, "arrival IS the prompt line");
        HemispherePassage.PhaseDecision d = ph(SEEDED, REG.arrivalDist());
        assertFalse(d.openPrompt(), "seeded-disarmed on the prompt line never self-prompts");
        assertEquals(SEEDED, d.nextPhase(), "and holds (does not re-arm; arrival is inside the re-arm band)");
        // The boolean sub-model agrees: disarmed on the prompt line neither opens nor re-arms.
        HemispherePassage.Decision b = HemispherePassage.evaluate(false, REG.arrivalDist(),
                REG.promptDist(), REG.rearmDist());
        assertFalse(b.openPrompt());
        assertFalse(b.nextArmed());
    }

    @Test
    void edgeRepromptFiresOnlyForSeededDisarmedNotOrdinaryDisarmed() {
        // The one thing that distinguishes SEEDED_DISARMED from ordinary DISARMED: only the post-arrival state
        // gets the EDGE auto-re-prompt. An ordinarily-disarmed player standing at the very edge gets nothing.
        HemispherePassage.PhaseDecision seeded = ph(SEEDED, EDGE);
        assertTrue(seeded.openPrompt(), "seeded-disarmed at the wall re-prompts");
        assertEquals(EDGE_PROMPTED, seeded.nextPhase());
        HemispherePassage.PhaseDecision ordinary = ph(DISARMED, EDGE);
        assertFalse(ordinary.openPrompt(), "ordinary disarmed at the wall does NOT edge-re-prompt");
        assertEquals(DISARMED, ordinary.nextPhase());
    }

    @Test
    void edgeRepromptIsAOneShotUntilFreshArrivalOrRearm() {
        // Consume the edge one-shot; it stays consumed (EDGE_PROMPTED behaves as ordinary disarmed) until either
        // a walk-out re-arms OR a fresh arrival re-seeds SEEDED_DISARMED.
        HemispherePassage.Phase phase = ph(SEEDED, EDGE).nextPhase(); // -> EDGE_PROMPTED
        assertEquals(EDGE_PROMPTED, phase);
        assertFalse(ph(phase, EDGE).openPrompt(), "no re-fire while EDGE_PROMPTED");
        // walk-out past re-arm -> ARMED, and a fresh arrival would re-seed SEEDED_DISARMED (client onArrival),
        // which re-enables the one-shot.
        assertEquals(ARMED, ph(phase, RA + 5.0).nextPhase());
        assertTrue(ph(SEEDED, EDGE).openPrompt(), "a fresh arrival re-enables the edge one-shot");
    }

    @Test
    void leavingPastRearmFromAnyPhaseReturnsToArmed() {
        for (HemispherePassage.Phase phase : HemispherePassage.Phase.values()) {
            HemispherePassage.PhaseDecision d = ph(phase, RA + 1.0);
            assertFalse(d.openPrompt(), "walking out never opens a prompt (from " + phase + ")");
            assertEquals(ARMED, d.nextPhase(), "a clean walk-out past the re-arm line re-arms from " + phase);
        }
    }

    @Test
    void edgeRepromptHoldNotBurnWhenBlocked() {
        // A seeded player at the very edge but blocked (underground / a screen up) HOLDS the seed (the edge
        // one-shot is not burned) and fires the instant the block clears.
        HemispherePassage.PhaseDecision blocked = phGated(SEEDED, EDGE, false);
        assertFalse(blocked.openPrompt(), "blocked: no open");
        assertEquals(SEEDED, blocked.nextPhase(), "blocked: the seed is HELD, not consumed");
        HemispherePassage.PhaseDecision cleared = phGated(SEEDED, EDGE, true);
        assertTrue(cleared.openPrompt(), "the held edge one-shot fires the instant the block clears");
        assertEquals(EDGE_PROMPTED, cleared.nextPhase());
    }

    @Test
    void armedApproachHoldNotBurnWhenBlocked_phaseParity() {
        // The ARMED phase reproduces evaluateGated's HOLD-not-burn contract for the ordinary approach prompt.
        HemispherePassage.PhaseDecision blocked = phGated(ARMED, P - 10.0, false);
        assertFalse(blocked.openPrompt());
        assertEquals(ARMED, blocked.nextPhase(), "blocked at the prompt line HOLDS armed");
        HemispherePassage.PhaseDecision cleared = phGated(ARMED, P - 10.0, true);
        assertTrue(cleared.openPrompt());
        assertEquals(DISARMED, cleared.nextPhase());
    }

    @Test
    void phaseArmedAndDisarmedReproduceEvaluateGated() {
        // The ARMED / DISARMED phases are a strict superset of the boolean evaluateGated cycle: same open, same
        // re-arm, across the band and the walk-out. (SEEDED_DISARMED / EDGE_PROMPTED add the edge one-shot.)
        for (double dist : new double[] {EDGE, P - 10.0, P, (P + RA) / 2.0, RA, RA + 5.0}) {
            for (boolean canOpen : new boolean[] {true, false}) {
                HemispherePassage.Decision armedB = HemispherePassage.evaluateGated(true, dist, canOpen, P, RA);
                HemispherePassage.PhaseDecision armedP = HemispherePassage.evaluatePhase(ARMED, dist, canOpen, P, RA, EDGE);
                assertEquals(armedB.openPrompt(), armedP.openPrompt(), "ARMED open parity @dist=" + dist + " canOpen=" + canOpen);
                assertEquals(armedB.nextArmed(), armedP.nextPhase() == ARMED, "ARMED next parity @dist=" + dist);

                HemispherePassage.Decision disB = HemispherePassage.evaluateGated(false, dist, canOpen, P, RA);
                HemispherePassage.PhaseDecision disP = HemispherePassage.evaluatePhase(DISARMED, dist, canOpen, P, RA, EDGE);
                assertEquals(disB.openPrompt(), disP.openPrompt(), "DISARMED open parity @dist=" + dist);
                assertEquals(disB.nextArmed(), disP.nextPhase() == ARMED, "DISARMED next parity @dist=" + dist);
            }
        }
    }

    @Test
    void phaseNanTreatedAsFarAndRearmsFromAnyPhase() {
        for (HemispherePassage.Phase phase : HemispherePassage.Phase.values()) {
            HemispherePassage.PhaseDecision d = ph(phase, Double.NaN);
            assertFalse(d.openPrompt(), "NaN never prompts (from " + phase + ")");
            assertEquals(ARMED, d.nextPhase(), "a bad read must not trap the player disarmed (from " + phase + ")");
        }
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
        assertFalse(HemispherePassage.serverAcceptsCross(REARM, PROMPT), "out past the prompt+slack");
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

    // ---- click re-prompt gesture: facing cone + band predicates -----------------------------

    @Test
    void lookDirXMatchesMinecraftYawConvention() {
        assertEquals(0.0, HemispherePassage.lookDirX(0.0f), 1e-9);    // south: no X component
        assertEquals(-1.0, HemispherePassage.lookDirX(90.0f), 1e-9);  // west: -X
        assertEquals(1.0, HemispherePassage.lookDirX(-90.0f), 1e-9);  // east: +X
        assertEquals(0.0, HemispherePassage.lookDirX(180.0f), 1e-9);  // north: no X component
    }

    @Test
    void facingOutwardXTrueWhenLookingAtTheNearerWall() {
        double minCos = HemispherePassage.REARM_GESTURE_FACING_MIN_COS; // cos 60 deg = 0.5
        assertTrue(HemispherePassage.facingOutwardX(3000.0, 0.0, -90.0f, minCos), "east half facing east");
        assertTrue(HemispherePassage.facingOutwardX(3000.0, 0.0, -45.0f, minCos), "east half 45 deg off-east still within 60");
        assertFalse(HemispherePassage.facingOutwardX(3000.0, 0.0, 0.0f, minCos), "east half facing south (90 deg off) fails");
        assertFalse(HemispherePassage.facingOutwardX(3000.0, 0.0, 90.0f, minCos), "east half facing inward (west) fails");
        assertTrue(HemispherePassage.facingOutwardX(-3000.0, 0.0, 90.0f, minCos), "west half facing west");
        assertFalse(HemispherePassage.facingOutwardX(-3000.0, 0.0, -90.0f, minCos), "west half facing inward (east) fails");
    }

    @Test
    void facingOutwardXFalseExactlyOnCenter() {
        assertFalse(HemispherePassage.facingOutwardX(0.0, 0.0, -90.0f, 0.5), "no outward side on the meridian itself");
    }

    @Test
    void facingConeIsAboutSixtyDegrees() {
        double minCos = HemispherePassage.REARM_GESTURE_FACING_MIN_COS; // cos 60 deg = 0.5
        assertTrue(HemispherePassage.facingOutwardX(3000.0, 0.0, -31.0f, minCos), "59 deg off-out is inside the cone");
        assertFalse(HemispherePassage.facingOutwardX(3000.0, 0.0, -29.0f, minCos), "61 deg off-out is outside the cone");
    }

    @Test
    void rearmGestureArmsOnlyWhenDisarmedInBandAndFacingOut() {
        assertTrue(HemispherePassage.rearmGestureArms(false, PROMPT - 10.0, true, PROMPT));
        assertTrue(HemispherePassage.rearmGestureArms(false, PROMPT, true, PROMPT), "exactly at the prompt line is in-band");
        assertFalse(HemispherePassage.rearmGestureArms(true, PROMPT - 10.0, true, PROMPT), "already armed -> no");
        assertFalse(HemispherePassage.rearmGestureArms(false, PROMPT - 10.0, false, PROMPT), "not facing out -> no hijack");
        assertFalse(HemispherePassage.rearmGestureArms(false, PROMPT + 1.0, true, PROMPT), "outside the prompt band -> no");
        assertFalse(HemispherePassage.rearmGestureArms(false, Double.NaN, true, PROMPT), "NaN distance -> no");
    }

    @Test
    void rearmGestureThenEvaluatePhaseOpensThePrompt() {
        // The gesture only re-arms; evaluatePhase(ARMED,..) then opens the prompt under the usual gate.
        boolean armedBool = false; // disarmed at the wall (declined the last prompt)
        double dist = P - 10.0;
        assertTrue(HemispherePassage.rearmGestureArms(armedBool, dist, true, P), "gesture approves at the wall facing out");
        HemispherePassage.PhaseDecision d = HemispherePassage.evaluatePhase(ARMED, dist, true, P, RA, EDGE);
        assertTrue(d.openPrompt(), "re-armed at the wall -> prompt re-opens without the walk-out");
        assertEquals(DISARMED, d.nextPhase(), "and disarms again on open");
    }

    // ---- B-7 P3 gesture AIM GATE (owner, TEST 97: breaking ice at the wall kept re-summoning the prompt) --

    @Test
    void gestureAimGateOnlyAcceptsAMissClickEw() {
        // EW arm: a genuinely qualifying gesture (disarmed, in band, facing the E/W wall) ...
        boolean facingOut = HemispherePassage.facingOutwardX(3000.0, 0.0, -90.0f, // east half facing east
                HemispherePassage.REARM_GESTURE_FACING_MIN_COS);
        assertTrue(facingOut, "sanity: facing the east wall");
        // ... fires on a MISS (air) click,
        assertTrue(HemispherePassage.rearmGestureArms(false, PROMPT - 10.0, facingOut, PROMPT,
                HemispherePassage.GESTURE_HIT_MISS), "air click asks the border");
        // ... is suppressed when the crosshair rests on a BLOCK (mining ice),
        assertFalse(HemispherePassage.rearmGestureArms(false, PROMPT - 10.0, facingOut, PROMPT,
                HemispherePassage.GESTURE_HIT_BLOCK), "block target = ordinary mining click");
        // ... and when it rests on an ENTITY (attacking).
        assertFalse(HemispherePassage.rearmGestureArms(false, PROMPT - 10.0, facingOut, PROMPT,
                HemispherePassage.GESTURE_HIT_ENTITY), "entity target = ordinary attack click");
    }

    @Test
    void gestureAimGateStillAppliesTheBasePredicateOnAMiss() {
        // A MISS click does not bypass the base gesture law: armed / out-of-band / not-facing all still deny.
        assertFalse(HemispherePassage.rearmGestureArms(true, PROMPT - 10.0, true, PROMPT,
                HemispherePassage.GESTURE_HIT_MISS), "already armed -> no");
        assertFalse(HemispherePassage.rearmGestureArms(false, PROMPT + 1.0, true, PROMPT,
                HemispherePassage.GESTURE_HIT_MISS), "outside the band -> no");
        assertFalse(HemispherePassage.rearmGestureArms(false, PROMPT - 10.0, false, PROMPT,
                HemispherePassage.GESTURE_HIT_MISS), "not facing the wall -> no");
    }

    // ---- B-7 P3 arrival legibility: the far-meridian label (pure twin of LatitudeMath.formatLongitudeDeg) --

    @Test
    void farMeridianLabelFormatsDegreesAndHemisphere() {
        // The coordinator's pinned case: x=-6970 on xRadius 7500 -> 167.28 deg -> "167°W".
        assertEquals("167°W", HemispherePassage.farMeridianLabel(-6970.0, 0.0, 7500.0));
        assertEquals("167°E", HemispherePassage.farMeridianLabel(6970.0, 0.0, 7500.0));
        assertEquals("0°", HemispherePassage.farMeridianLabel(0.0, 0.0, 7500.0), "prime meridian has no suffix");
        assertEquals("180°W", HemispherePassage.farMeridianLabel(-7500.0, 0.0, 7500.0));
        assertEquals("180°E", HemispherePassage.farMeridianLabel(7500.0, 0.0, 7500.0));
        assertEquals("180°E", HemispherePassage.farMeridianLabel(9000.0, 0.0, 7500.0), "beyond the edge clamps to 180");
        // Off-center border: measured against |x - centerX| like the distance math, not raw |x|.
        assertEquals("90°E", HemispherePassage.farMeridianLabel(3850.0, 100.0, 7500.0));
        // Degenerate inputs degrade to the neutral label rather than garbage.
        assertEquals("0°", HemispherePassage.farMeridianLabel(Double.NaN, 0.0, 7500.0));
        assertEquals("0°", HemispherePassage.farMeridianLabel(1000.0, 0.0, 0.0));
    }

    // ---- NaN safety (boolean model) --------------------------------------------------------

    @Test
    void nanDistanceTreatedAsFarAndRearms() {
        HemispherePassage.Decision d = eval(false, Double.NaN);
        assertFalse(d.openPrompt());
        assertTrue(d.nextArmed(), "a bad distance read must not trap the player disarmed");
    }

    // ---- B-5-P2 approach fog (A2): the pure distance->fog curves ---------------------------
    // The fog band runs from RAMP (onset) down to CLIMAX (full opacity, the 179-deg line).

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
        assertEquals(0.0, HemispherePassage.approachProgress(50.0, 40.0, 40.0), 1e-9);
        assertEquals(0.0, HemispherePassage.approachProgress(50.0, 30.0, 40.0), 1e-9);
    }
}
