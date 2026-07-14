package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleGeometry} -- the Z-axis sibling of {@link EdgeGeometry} for the N/S pole
 * crossing (B-7, S5-revised). Pins:
 * <ul>
 *   <li>degree -&gt; block conversion ({@code bpd = zRadius/90}),</li>
 *   <li>the effective per-world geometry on the three shipped pole radii (Itty 3750 / Small-Wide 7500 /
 *       Regular-Wide 10000), including which floors bind,</li>
 *   <li>the S5 ordering chain {@code edgeReprompt < arrival < prompt < rearm} (the old
 *       {@code prompt < arrival} invariant is RETIRED),</li>
 *   <li><b>the S5 UNIFORM SEEDED regime</b> across FIVE radii (3750/7500/10000/15000/20000, superseding the
 *       old dual-regime pins and closing sweep INFO-b): arrival is inside prompt AND rearm everywhere; a fresh
 *       arrival seeded {@code SEEDED_DISARMED} HOLDS (no self-prompt -- state law, driven through the shared
 *       {@link HemispherePassage#evaluatePhase}); the walk-to-the-pole one-shot fires at the edge-re-prompt
 *       line; a walk-out past re-arm returns to ARMED and the ordinary prompt speaks at 89.2,</li>
 *   <li>{@link PoleGeometry#distanceToPole} incl. the beyond-the-line clamp-to-0 (the mercy exit) and NaN,</li>
 *   <li>lerp-immunity: the geometry is a function of the Z radius ONLY.</li>
 * </ul>
 */
class PoleGeometryTest {

    private static final double EPS = 1e-6;

    private static final PoleGeometry.Resolved ITTY = PoleGeometry.resolve(3750.0);   // Itty (z 3750)
    private static final PoleGeometry.Resolved SMALL = PoleGeometry.resolve(7500.0);  // Small-Wide (z 7500)
    private static final PoleGeometry.Resolved REG = PoleGeometry.resolve(10000.0);   // Regular-Wide (z 10000)

    /** The S5 uniform-regime pin matrix: the three shipped Z radii plus two larger hypotheticals (the sweep's
     *  INFO-b ask), so "arrival inside rearm on EVERY size" is pinned beyond the shipped range. */
    private static final double[] PIN_RADII = {3750.0, 7500.0, 10000.0, 15000.0, 20000.0};

    // ---- degree -> block conversion --------------------------------------------------------

    @Test
    void blocksPerDegreeAndDistForDeg() {
        assertEquals(3750.0 / 90.0, PoleGeometry.blocksPerDegree(3750.0), EPS);
        assertEquals(10000.0 / 90.0, PoleGeometry.blocksPerDegree(10000.0), EPS);
        // distance-from-pole for a degree: (90 - deg) * bpd. The pole (90) is distance 0; 89.5 deg (the S5
        // arrival) is 0.5 deg in; 89.2 deg (prompt) is 0.8 deg in.
        assertEquals(0.0, PoleGeometry.distForDeg(90.0, 10000.0), EPS);
        assertEquals(0.5 * (10000.0 / 90.0), PoleGeometry.distForDeg(89.5, 10000.0), EPS);
        assertEquals(0.8 * (10000.0 / 90.0), PoleGeometry.distForDeg(89.2, 10000.0), EPS);
    }

    // ---- exact effective geometry per world size -------------------------------------------

    @Test
    void ittyEffectiveGeometry_floorsBind() {
        // z 3750, bpd 41.667: edgeReprompt = 0.1 deg = 4.17 floored to 8; arrival = 0.5 deg = 20.83 UNFLOORED
        // (between the 8 and 40 floors); prompt = 0.8 deg = 33.3 floored to 40; rearm = 1.5 deg = 62.5 floored
        // to prompt+DEAD_ZONE(64) = 104.
        assertEquals(8.0, ITTY.edgeRepromptDist(), EPS, "edgeReprompt floored to 8 (0.1 deg = 4.17 < 8)");
        assertEquals(0.5 * (3750.0 / 90.0), ITTY.arrivalDist(), EPS, "arrival = 0.5 deg = 20.83 (S5, unfloored)");
        assertEquals(40.0, ITTY.promptDist(), EPS, "prompt floored to 40 (0.8 deg = 33.3 < 40)");
        assertEquals(104.0, ITTY.rearmDist(), EPS, "rearm floored to prompt + DEAD_ZONE (40 + 64)");
    }

    @Test
    void smallEffectiveGeometry_rearmFloorBinds() {
        // z 7500, bpd 83.333: edgeReprompt = 8.33; arrival = 41.67; prompt = 66.67; rearm = 125 floored to
        // prompt+64 = 130.67.
        assertEquals(0.1 * (7500.0 / 90.0), SMALL.edgeRepromptDist(), EPS);
        assertEquals(0.5 * (7500.0 / 90.0), SMALL.arrivalDist(), EPS, "arrival = 0.5 deg = 41.67 (S5)");
        assertEquals(0.8 * (7500.0 / 90.0), SMALL.promptDist(), EPS);
        assertEquals(SMALL.promptDist() + EdgeGeometry.DEAD_ZONE_MIN_BLOCKS, SMALL.rearmDist(), EPS,
                "rearm floored to prompt + DEAD_ZONE (degree 125 < 130.67)");
    }

    @Test
    void regularEffectiveGeometry_pureDegrees() {
        // z 10000, bpd 111.111: all pure degrees (no floor binds).
        assertEquals(0.1 * (10000.0 / 90.0), REG.edgeRepromptDist(), EPS);
        assertEquals(0.5 * (10000.0 / 90.0), REG.arrivalDist(), EPS, "arrival = 0.5 deg = 55.56 (S5)");
        assertEquals(0.8 * (10000.0 / 90.0), REG.promptDist(), EPS);
        assertEquals(1.5 * (10000.0 / 90.0), REG.rearmDist(), EPS);
        // arrivalAbsZ = zRadius - arrivalDist = the 89.5 deg column.
        assertEquals(10000.0 - REG.arrivalDist(), REG.arrivalAbsZ(), EPS);
    }

    // ---- the S5 ordering chain (the old prompt < arrival invariant is RETIRED) ---------------

    @Test
    void s5OrderingChainHoldsOnEveryPinnedRadius() {
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            assertTrue(r.edgeRepromptDist() < r.arrivalDist(), "edgeReprompt < arrival @" + zRadius);
            assertTrue(r.arrivalDist() < r.promptDist(),
                    "arrival < prompt (S5: arrival is INSIDE the prompt band) @" + zRadius);
            assertTrue(r.promptDist() < r.rearmDist(), "prompt < rearm @" + zRadius);
            PoleGeometry.assertChain(r); // throws if any radius ever ties/inverts
        }
    }

    // ---- THE S5 UNIFORM SEEDED REGIME (supersedes the old Itty-vs-Regular dual pins) ----------

    @Test
    void uniformSeededRegime_arrivalInsideRearmOnEverySize() {
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            assertTrue(r.arrivalDist() < r.rearmDist(),
                    "S5: arrival must sit INSIDE the re-arm line on EVERY size @" + zRadius);
        }
    }

    @Test
    void uniformSeededRegime_arrivalHoldsSeededAndNeverSelfPrompts() {
        // State law, driven through the SHARED phase machine on every pinned size: a fresh arrival (seeded
        // SEEDED_DISARMED at the arrival column, which now sits inside the prompt band) does NOT open a prompt
        // and does NOT re-arm -- it HOLDS the seed. This is the S5 replacement for the retired geometric
        // "prompt < arrival" self-prompt guarantee.
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            HemispherePassage.PhaseDecision atArrival = HemispherePassage.evaluatePhase(
                    HemispherePassage.Phase.SEEDED_DISARMED, r.arrivalDist(), true,
                    r.promptDist(), r.rearmDist(), r.edgeRepromptDist());
            assertFalse(atArrival.openPrompt(), "no self-prompt at the arrival column @" + zRadius);
            assertEquals(HemispherePassage.Phase.SEEDED_DISARMED, atArrival.nextPhase(),
                    "arrival HOLDS the seed (uniform regime) @" + zRadius);
        }
    }

    @Test
    void uniformSeededRegime_edgeRepromptFiresOnceAtThePole() {
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            HemispherePassage.PhaseDecision atPole = HemispherePassage.evaluatePhase(
                    HemispherePassage.Phase.SEEDED_DISARMED, r.edgeRepromptDist(), true,
                    r.promptDist(), r.rearmDist(), r.edgeRepromptDist());
            assertTrue(atPole.openPrompt(), "walking to the pole fires the SEEDED one-shot @" + zRadius);
            assertEquals(HemispherePassage.Phase.EDGE_PROMPTED, atPole.nextPhase());
        }
    }

    @Test
    void uniformSeededRegime_walkOutReArmsThenOrdinaryPromptSpeaks() {
        for (double zRadius : PIN_RADII) {
            PoleGeometry.Resolved r = PoleGeometry.resolve(zRadius);
            // Walking out past the re-arm line returns to ARMED from the seeded state...
            HemispherePassage.PhaseDecision walkOut = HemispherePassage.evaluatePhase(
                    HemispherePassage.Phase.SEEDED_DISARMED, r.rearmDist() + 1.0, true,
                    r.promptDist(), r.rearmDist(), r.edgeRepromptDist());
            assertFalse(walkOut.openPrompt());
            assertEquals(HemispherePassage.Phase.ARMED, walkOut.nextPhase(), "walk-out re-arms @" + zRadius);
            // ...and turning back in opens the ordinary prompt at the 89.2 line (a fresh journey).
            HemispherePassage.PhaseDecision atPrompt = HemispherePassage.evaluatePhase(
                    HemispherePassage.Phase.ARMED, r.promptDist(), true,
                    r.promptDist(), r.rearmDist(), r.edgeRepromptDist());
            assertTrue(atPrompt.openPrompt(), "ordinary prompt after re-arm @" + zRadius);
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
                "a beyond-the-line survivor (dist 0) is accepted -- the mercy exit");
        // NaN z propagates to NaN, which serverAcceptsCross rejects and the phase machine treats as 'far'.
        assertTrue(Double.isNaN(PoleGeometry.distanceToPole(10000.0, 0.0, Double.NaN)));
        assertFalse(HemispherePassage.serverAcceptsCross(
                PoleGeometry.distanceToPole(10000.0, 0.0, Double.NaN), REG.promptDist()));
    }

    // ---- lerp-immunity: geometry is a pure function of the Z radius -------------------------

    @Test
    void geometryIsAFunctionOfZRadiusOnly() {
        // Same radius -> identical geometry regardless of any (absent) live-border input.
        assertEquals(REG.promptDist(), PoleGeometry.resolve(10000.0).promptDist(), EPS);
        assertEquals(REG.rearmDist(), PoleGeometry.resolve(10000.0).rearmDist(), EPS);
        assertEquals(REG.arrivalDist(), PoleGeometry.resolve(10000.0).arrivalDist(), EPS);
    }
}
