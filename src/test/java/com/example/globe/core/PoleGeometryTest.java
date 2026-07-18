package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleGeometry} -- the Z-axis sibling of {@link EdgeGeometry} for the N/S pole
 * crossing (B-7, S16(c)-revised: PROMPT and ARRIVAL both at the pole line = "one prompt, at the wall"). Pins:
 * <ul>
 *   <li>degree -&gt; block conversion ({@code bpd = zRadius/90}); the pole (90 deg) is distance 0,</li>
 *   <li>the effective per-world geometry on the three shipped pole radii (Itty 3750 / Small-Wide 7500 /
 *       Regular-Wide 10000): prompt = the {@link PoleGeometry#WALL_PROMPT_DIST_BLOCKS} wall epsilon (NOT the
 *       40-block approach floor), arrival = 0 (the pole line), edgeReprompt = the disabled sentinel,</li>
 *   <li>the S16(c) ordering chain {@code edgeReprompt(-1) < arrival(0) < prompt(wall eps) < rearm},</li>
 *   <li><b>the S16(c) "one wall prompt" regime</b> across FIVE radii: arrival (distance 0) is inside prompt AND
 *       rearm everywhere; a fresh arrival seeded {@code SEEDED_DISARMED} HOLDS and NEVER auto-opens (the near-pole
 *       edge-re-prompt collapsed -- its distance is a disabled sentinel); a walk-out past re-arm returns to ARMED
 *       and the ordinary prompt speaks AT the wall,</li>
 *   <li>{@link PoleGeometry#distanceToPole} incl. the beyond-the-line clamp-to-0 (the mercy exit) and NaN,</li>
 *   <li>lerp-immunity: the geometry is a function of the Z radius ONLY.</li>
 * </ul>
 */
class PoleGeometryTest {

    private static final double EPS = 1e-6;
    private static final double WALL = PoleGeometry.WALL_PROMPT_DIST_BLOCKS;         // 3
    private static final double DISABLED = PoleGeometry.EDGE_REPROMPT_DISABLED_DIST; // -1

    private static final PoleGeometry.Resolved ITTY = PoleGeometry.resolve(3750.0);   // Itty (z 3750)
    private static final PoleGeometry.Resolved SMALL = PoleGeometry.resolve(7500.0);  // Small-Wide (z 7500)
    private static final PoleGeometry.Resolved REG = PoleGeometry.resolve(10000.0);   // Regular-Wide (z 10000)

    /** The S16(c) uniform-regime pin matrix: the three shipped Z radii plus two larger hypotheticals, so "arrival
     *  inside prompt AND rearm, seed holds, on EVERY size" is pinned beyond the shipped range. */
    private static final double[] PIN_RADII = {3750.0, 7500.0, 10000.0, 15000.0, 20000.0};

    // ---- degree -> block conversion --------------------------------------------------------

    @Test
    void blocksPerDegreeAndDistForDeg() {
        assertEquals(3750.0 / 90.0, PoleGeometry.blocksPerDegree(3750.0), EPS);
        assertEquals(10000.0 / 90.0, PoleGeometry.blocksPerDegree(10000.0), EPS);
        // distance-from-pole for a degree: (90 - deg) * bpd. The pole (90) -- where both the S16(c) prompt AND
        // arrival anchor -- is distance 0.
        assertEquals(0.0, PoleGeometry.distForDeg(90.0, 10000.0), EPS);
        assertEquals(0.5 * (10000.0 / 90.0), PoleGeometry.distForDeg(89.5, 10000.0), EPS);
        assertEquals(1.5 * (10000.0 / 90.0), PoleGeometry.distForDeg(88.5, 10000.0), EPS); // the re-arm degree
    }

    // ---- exact effective geometry per world size -------------------------------------------

    @Test
    void ittyEffectiveGeometry_rearmFloorBinds() {
        // z 3750, bpd 41.667: edgeReprompt = disabled sentinel -1; arrival = 90 deg = 0; prompt = WALL epsilon 3;
        // rearm = max(1.5 deg = 62.5, prompt + DEAD_ZONE 64 = 67) = 67 (the DEAD_ZONE floor binds here).
        assertEquals(DISABLED, ITTY.edgeRepromptDist(), EPS, "edgeReprompt disabled (collapsed into the wall prompt)");
        assertEquals(0.0, ITTY.arrivalDist(), EPS, "arrival = 90 deg = the pole line (distance 0)");
        assertEquals(WALL, ITTY.promptDist(), EPS, "prompt = the wall epsilon, NOT the 40-block approach floor");
        assertEquals(WALL + EdgeGeometry.DEAD_ZONE_MIN_BLOCKS, ITTY.rearmDist(), EPS,
                "rearm floored to prompt + DEAD_ZONE (62.5 < 67)");
    }

    @Test
    void smallEffectiveGeometry_pureRearmDegree() {
        // z 7500, bpd 83.333: rearm = 1.5 deg = 125 (exceeds the prompt+64 = 67 floor, so the pure degree wins).
        assertEquals(DISABLED, SMALL.edgeRepromptDist(), EPS);
        assertEquals(0.0, SMALL.arrivalDist(), EPS);
        assertEquals(WALL, SMALL.promptDist(), EPS);
        assertEquals(1.5 * (7500.0 / 90.0), SMALL.rearmDist(), EPS, "rearm = 1.5 deg = 125 (pure degree)");
    }

    @Test
    void regularEffectiveGeometry_pureRearmDegree() {
        // z 10000, bpd 111.111: rearm = 1.5 deg = 166.67 (pure degree; the floor does not bind).
        assertEquals(DISABLED, REG.edgeRepromptDist(), EPS);
        assertEquals(0.0, REG.arrivalDist(), EPS);
        assertEquals(WALL, REG.promptDist(), EPS);
        assertEquals(1.5 * (10000.0 / 90.0), REG.rearmDist(), EPS);
        // arrivalAbsZ = zRadius - arrivalDist = zRadius exactly (the pole line, where the crossing drops you).
        assertEquals(10000.0, REG.arrivalAbsZ(), EPS);
    }

    // ---- the S16(c) ordering chain ----------------------------------------------------------

    @Test
    void s16cOrderingChainHoldsOnEveryPinnedRadius() {
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            assertTrue(r.edgeRepromptDist() < r.arrivalDist(), "edgeReprompt(-1) < arrival(0) @" + zRadius);
            assertTrue(r.arrivalDist() < r.promptDist(),
                    "arrival(0) < prompt (the wall epsilon) @" + zRadius);
            assertTrue(r.promptDist() < r.rearmDist(), "prompt < rearm @" + zRadius);
            PoleGeometry.assertChain(r); // throws if any radius ever ties/inverts
        }
    }

    // ---- THE S16(c) "ONE WALL PROMPT" REGIME ------------------------------------------------

    @Test
    void oneWallPromptRegime_arrivalInsidePromptAndRearmOnEverySize() {
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            assertTrue(r.arrivalDist() < r.promptDist(), "arrival inside the wall prompt @" + zRadius);
            assertTrue(r.arrivalDist() < r.rearmDist(), "arrival inside the re-arm line @" + zRadius);
        }
    }

    @Test
    void oneWallPromptRegime_arrivalHoldsSeededAndNeverSelfPrompts() {
        // State law + disabled edge-reprompt, driven through the SHARED phase machine on every pinned size: a fresh
        // arrival (seeded SEEDED_DISARMED at the pole line, distance 0, inside the wall prompt band) does NOT open
        // a prompt and does NOT re-arm -- it HOLDS the seed. And it still HOLDS if walked deeper to the wall.
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            for (double dist : new double[]{r.arrivalDist(), 0.0, r.promptDist() - 1.0}) {
                HemispherePassage.PhaseDecision d = HemispherePassage.evaluatePhase(
                        HemispherePassage.Phase.SEEDED_DISARMED, Math.max(0.0, dist), true,
                        r.promptDist(), r.rearmDist(), r.edgeRepromptDist());
                assertFalse(d.openPrompt(), "no self-prompt in the seeded band @" + zRadius + " dist=" + dist);
                assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, d.nextPhase(),
                        "arrival HOLDS the seed (one-wall-prompt regime) @" + zRadius);
            }
        }
    }

    @Test
    void oneWallPromptRegime_walkOutReArmsThenOrdinaryPromptSpeaksAtTheWall() {
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            // Walking out past the re-arm line returns to ARMED from the seeded state...
            HemispherePassage.PhaseDecision walkOut = HemispherePassage.evaluatePhase(
                    HemispherePassage.Phase.SEEDED_DISARMED, r.rearmDist() + 1.0, true,
                    r.promptDist(), r.rearmDist(), r.edgeRepromptDist());
            assertFalse(walkOut.openPrompt());
            assertEquals(HemispherePassage.Phase.ARMED, walkOut.nextPhase(), "walk-out re-arms @" + zRadius);
            // ...and turning back in opens the ordinary prompt AT the wall (a fresh journey).
            HemispherePassage.PhaseDecision atPrompt = HemispherePassage.evaluatePhase(
                    HemispherePassage.Phase.ARMED, r.promptDist(), true,
                    r.promptDist(), r.rearmDist(), r.edgeRepromptDist());
            assertTrue(atPrompt.openPrompt(), "ordinary wall prompt after re-arm @" + zRadius);
            assertEquals(HemispherePassage.Phase.DISARMED, atPrompt.nextPhase());
        }
    }

    // ---- distanceToPole: interior, at-pole, beyond-the-line (mercy exit), NaN ----------------

    @Test
    void distanceToPole_interiorAtPoleBeyondAndNaN() {
        assertEquals(1000.0, PoleGeometry.distanceToPole(10000.0, 0.0, 9000.0), EPS, "interior");
        assertEquals(1000.0, PoleGeometry.distanceToPole(10000.0, 0.0, -9000.0), EPS, "symmetric south");
        assertEquals(0.0, PoleGeometry.distanceToPole(10000.0, 0.0, 10000.0), EPS, "at the pole line");
        // Beyond the pole line on a Wide world (|z| > zRadius): clamps to 0 -> still eligible for the crossing
        // (the mercy exit through the hard-stop clamp).
        assertEquals(0.0, PoleGeometry.distanceToPole(10000.0, 0.0, 10500.0), EPS, "beyond the line clamps to 0");
        assertTrue(HemispherePassage.serverAcceptsCross(
                PoleGeometry.distanceToPole(10000.0, 0.0, 10500.0), REG.promptDist()),
                "a beyond-the-line survivor (dist 0) is accepted -- the mercy exit (wall prompt + 32 slack)");
        // NaN z propagates to NaN, which serverAcceptsCross rejects and the phase machine treats as 'far'.
        assertTrue(Double.isNaN(PoleGeometry.distanceToPole(10000.0, 0.0, Double.NaN)));
        assertFalse(HemispherePassage.serverAcceptsCross(
                PoleGeometry.distanceToPole(10000.0, 0.0, Double.NaN), REG.promptDist()));
    }

    @Test
    void serverAcceptsCross_atTheWallWithinTheThirtyTwoBlockSlack() {
        // S16(c) netcode: the wall prompt (~3) + the 32-block SERVER_CROSS_SLACK = a ~35-block acceptance window
        // against distanceToPole ~0. A player at the wall (dist 0-3) is accepted; one 40 blocks out is rejected.
        double promptAt = REG.promptDist();
        assertTrue(HemispherePassage.serverAcceptsCross(0.0, promptAt), "at the pole line: accepted");
        assertTrue(HemispherePassage.serverAcceptsCross(promptAt, promptAt), "at the wall prompt line: accepted");
        assertTrue(HemispherePassage.serverAcceptsCross(promptAt + HemispherePassage.SERVER_CROSS_SLACK, promptAt),
                "at prompt + 32 slack: accepted (drift tolerance)");
        assertFalse(HemispherePassage.serverAcceptsCross(promptAt + HemispherePassage.SERVER_CROSS_SLACK + 0.1, promptAt),
                "just past the slack: rejected (possible spoof)");
    }

    // ---- lerp-immunity: geometry is a pure function of the Z radius -------------------------

    @Test
    void geometryIsAFunctionOfZRadiusOnly() {
        // Same radius -> identical geometry regardless of any (absent) live-border input.
        assertEquals(REG.promptDist(), PoleGeometry.resolve(10000.0).promptDist(), EPS);
        assertEquals(REG.rearmDist(), PoleGeometry.resolve(10000.0).rearmDist(), EPS);
        assertEquals(REG.arrivalDist(), PoleGeometry.resolve(10000.0).arrivalDist(), EPS);
        assertEquals(REG.edgeRepromptDist(), PoleGeometry.resolve(10000.0).edgeRepromptDist(), EPS);
    }
}
