package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EdgeGeometry} -- the single source of truth for the degree-anchored,
 * intended-radius E/W edge geometry (edge-flow rework 2026-07-13). Pins:
 * <ul>
 *   <li>degree -&gt; block conversion,</li>
 *   <li>the strict ordering chain
 *       {@code edgeReprompt < climax < prompt(==arrival) < rampStart < rearm < advisory} in the pure DEGREE
 *       case AND under the small-world floors (Itty / Small-Wide / Regular-Wide matrices),</li>
 *   <li>the exact effective block geometry on each world size,</li>
 *   <li>the advisory as the OUTERMOST element leading the fog onset (the banner cap moved rampStart -&gt;
 *       advisory),</li>
 *   <li>the fog band (onset {@code rampStartDist} up to full {@code fogClimaxDist} @179 deg) unchanged,</li>
 *   <li>arrival landing EXACTLY on the prompt line (178 deg on every world), symmetric both hemispheres,
 *       Classic + Mercator,</li>
 *   <li>the post-arrival EDGE auto-re-prompt line hard by the wall (179.6 deg) and clear of the vanilla border
 *       warning/damage-safe zone, and</li>
 *   <li>the immunity regression: the geometry is a function of the intended X radius ONLY, so a live border
 *       size != intended (a mid-lerp read) can never move a feature line.</li>
 * </ul>
 */
class EdgeGeometryTest {

    private static final double EPS = 1e-6;

    private static final EdgeGeometry.Resolved ITTY = EdgeGeometry.resolve(3750.0);    // Itty-Bitty Classic
    private static final EdgeGeometry.Resolved SMALL = EdgeGeometry.resolve(15000.0);  // Small Wide (z 7500)
    private static final EdgeGeometry.Resolved REG = EdgeGeometry.resolve(20000.0);    // Regular Wide (z 10000)

    // ---- degree -> block conversion --------------------------------------------------------

    @Test
    void blocksPerDegreeAndDistForDeg() {
        assertEquals(3750.0 / 180.0, EdgeGeometry.blocksPerDegree(3750.0), EPS);
        assertEquals(20000.0 / 180.0, EdgeGeometry.blocksPerDegree(20000.0), EPS);
        // distance-from-edge for a degree: (180 - deg) * bpd. The edge (180) is distance 0; 178 deg (the prompt
        // and arrival anchor) is 2 deg in; 176 deg (the advisory) is 4 deg in.
        assertEquals(0.0, EdgeGeometry.distForDeg(180.0, 20000.0), EPS);
        assertEquals(2.0 * (20000.0 / 180.0), EdgeGeometry.distForDeg(178.0, 20000.0), EPS);
        assertEquals(4.0 * (20000.0 / 180.0), EdgeGeometry.distForDeg(176.0, 20000.0), EPS);
    }

    // ---- exact effective geometry per world size -------------------------------------------

    @Test
    void ittyBittyClassicEffectiveGeometry_floorsBind() {
        // xRadius 3750: 1 deg = 20.833 blocks, so several floors engage. climax floors to 40; prompt = 2 deg =
        // 41.67 > 40 (degree wins); rampStart is driven by the 60-block fog-band floor to climax+60 = 100; rearm
        // by the ordering step to rampStart+8 = 108; advisory by the ordering step to rearm+8 = 116; edgeReprompt
        // = 0.4 deg = 8.33 > the 8-block floor.
        assertEquals(40.0, ITTY.fogClimaxDist(), EPS, "climax floored to 40 (1 deg = 20.8 < 40)");
        assertEquals(2.0 * (3750.0 / 180.0), ITTY.promptDist(), EPS, "prompt = 2 deg = 41.67 (degree beats the 40 floor)");
        assertEquals(100.0, ITTY.rampStartDist(), EPS, "rampStart = climax + FOG_BAND (40 + 60)");
        assertEquals(108.0, ITTY.rearmDist(), EPS, "rearm = rampStart + ORDER_STEP (100 + 8), dominates prompt+64=105.67");
        assertEquals(116.0, ITTY.advisoryDist(), EPS, "advisory = rearm + ORDER_STEP (108 + 8)");
        assertEquals(ITTY.promptDist(), ITTY.arrivalDist(), EPS, "arrival lands exactly on the prompt line");
        assertEquals(0.4 * (3750.0 / 180.0), ITTY.edgeRepromptDist(), EPS, "edgeReprompt = 0.4 deg = 8.33 (> 8 floor)");
        // Ordering chain holds on the smallest world.
        assertChain(ITTY);
    }

    @Test
    void smallWideEffectiveGeometry_pureDegreesHold() {
        // xRadius 15000: 1 deg = 83.333 blocks; the pure degree values hold un-floored. Anchors: edgeReprompt
        // 179.6, climax 179, prompt/arrival 178, rampStart 177.5, rearm 177, advisory 176.
        double bpd = 15000.0 / 180.0;
        assertEquals(0.4 * bpd, SMALL.edgeRepromptDist(), EPS);// 179.6 deg
        assertEquals(1.0 * bpd, SMALL.fogClimaxDist(), EPS);   // 179 deg
        assertEquals(2.0 * bpd, SMALL.promptDist(), EPS);      // 178 deg
        assertEquals(2.0 * bpd, SMALL.arrivalDist(), EPS);     // 178 deg (== prompt)
        assertEquals(2.5 * bpd, SMALL.rampStartDist(), EPS);   // 177.5 deg
        assertEquals(3.0 * bpd, SMALL.rearmDist(), EPS);       // 177 deg
        assertEquals(4.0 * bpd, SMALL.advisoryDist(), EPS);    // 176 deg
        assertEquals(SMALL.promptDist(), SMALL.arrivalDist(), EPS, "arrival lands exactly on the prompt line");
        assertChain(SMALL);
    }

    @Test
    void regularWideEffectiveGeometry_pureDegreesHold() {
        double bpd = 20000.0 / 180.0;
        assertEquals(0.4 * bpd, REG.edgeRepromptDist(), EPS); // 179.6 deg
        assertEquals(1.0 * bpd, REG.fogClimaxDist(), EPS);    // 179 deg
        assertEquals(2.0 * bpd, REG.promptDist(), EPS);       // 178 deg
        assertEquals(2.0 * bpd, REG.arrivalDist(), EPS);      // 178 deg (== prompt)
        assertEquals(2.5 * bpd, REG.rampStartDist(), EPS);    // 177.5 deg
        assertEquals(3.0 * bpd, REG.rearmDist(), EPS);        // 177 deg
        assertEquals(4.0 * bpd, REG.advisoryDist(), EPS);     // 176 deg
        assertEquals(REG.promptDist(), REG.arrivalDist(), EPS, "arrival lands exactly on the prompt line");
        assertChain(REG);
    }

    // ---- ordering chain + floor disciplines ------------------------------------------------

    /** The full edge-flow ordering chain, strictly increasing in distance-from-edge. */
    private static void assertChain(EdgeGeometry.Resolved g) {
        assertTrue(g.edgeRepromptDist() < g.fogClimaxDist(), "edgeReprompt < climax");
        assertTrue(g.fogClimaxDist() < g.promptDist(), "climax < prompt");
        assertTrue(g.promptDist() < g.rampStartDist(), "prompt < rampStart (prompt now sits INSIDE the fog onset)");
        assertTrue(g.rampStartDist() < g.rearmDist(), "rampStart < rearm (re-arm now sits OUTSIDE the fog)");
        assertTrue(g.rearmDist() < g.advisoryDist(), "rearm < advisory (advisory is the outermost element)");
        assertEquals(g.promptDist(), g.arrivalDist(), EPS, "arrival == prompt exactly");
    }

    @Test
    void orderingChainHoldsOnEveryWorldSize() {
        for (double xr : new double[] {3750.0, 5000.0, 7500.0, 10000.0, 15000.0, 20000.0, 40000.0}) {
            assertChain(EdgeGeometry.resolve(xr));
        }
    }

    @Test
    void floorsAreRespectedOnEveryWorldSize() {
        for (double xr : new double[] {3750.0, 5000.0, 7500.0, 10000.0, 15000.0, 20000.0}) {
            EdgeGeometry.Resolved g = EdgeGeometry.resolve(xr);
            assertTrue(g.promptDist() >= EdgeGeometry.PROMPT_MIN_DIST_BLOCKS - EPS,
                    "prompt >= 40 @ " + xr);
            assertTrue(g.rearmDist() - g.promptDist() >= EdgeGeometry.DEAD_ZONE_MIN_BLOCKS - EPS,
                    "rearm - prompt >= DEAD_ZONE (64) @ " + xr);
            assertTrue(g.rampStartDist() - g.fogClimaxDist() >= EdgeGeometry.FOG_BAND_MIN_BLOCKS - EPS,
                    "fog band width >= 60 @ " + xr);
            assertTrue(g.edgeRepromptDist() >= EdgeGeometry.EDGE_REPROMPT_MIN_DIST_BLOCKS - EPS,
                    "edgeReprompt >= 8 @ " + xr);
        }
    }

    @Test
    void deadZoneFloorMatchesTheHysteresisDiscipline() {
        // The re-arm dead band floor is exactly the shared DEAD_ZONE the crossing-center machine uses.
        assertEquals(HemisphereCrossing.DEAD_ZONE_BLOCKS, EdgeGeometry.DEAD_ZONE_MIN_BLOCKS, EPS);
    }

    // ---- advisory is the outermost element and leads the fog -------------------------------

    @Test
    void advisoryIsOutermostAndLeadsTheFogOnset() {
        // Edge-flow rework: the banner cap moved from the fog onset (rampStartDist) out to the advisory anchor
        // (advisoryDist, 176 deg), so the heads-up leads the fog by a degree of margin on every world.
        for (EdgeGeometry.Resolved g : new EdgeGeometry.Resolved[] {ITTY, SMALL, REG}) {
            assertTrue(g.advisoryDist() > g.rampStartDist(), "advisory leads the fog onset");
            assertTrue(g.advisoryDist() > g.rearmDist(), "advisory is the outermost element");
        }
        // On an un-floored world the advisory is exactly the 176-deg distance.
        double bpd = 20000.0 / 180.0;
        assertEquals((180.0 - EdgeGeometry.ADVISORY_DEG) * bpd, REG.advisoryDist(), EPS);
    }

    // ---- shared fog band: onset (177.5) up to full (179) -----------------------------------

    @Test
    void fogBandOnsetAndClimaxAreUnchangedByTheRework() {
        // The fog band is untouched by the edge-flow rework: onset at rampStartDist (177.5 deg) up to full at
        // fogClimaxDist (179 deg, one degree POLEWARD of the 178-deg prompt line now that they are decoupled).
        double bpd = 20000.0 / 180.0;
        assertEquals((180.0 - EdgeGeometry.RAMP_START_DEG) * bpd, REG.rampStartDist(), EPS);
        assertEquals((180.0 - EdgeGeometry.CLIMAX_DEG) * bpd, REG.fogClimaxDist(), EPS);
        // The climax is one degree inside the prompt line (decoupled), NOT equal to it.
        assertTrue(REG.fogClimaxDist() < REG.promptDist(), "fog full-opacity is poleward of the prompt line");
    }

    // ---- arrival target (exactly on the prompt line; symmetric; Classic + Mercator) --------

    @Test
    void arrivalIsOnThePromptLineAndInsideTheFogOnEveryWorld() {
        // Arrival == prompt (178 deg) and 0.5 deg inside the fog onset (177.5 deg) -- you emerge into the thinning
        // fog looking inward. It is INSIDE the re-arm band on every world now (prompt 2 deg < rearm 3 deg).
        for (EdgeGeometry.Resolved g : new EdgeGeometry.Resolved[] {ITTY, SMALL, REG}) {
            assertEquals(g.promptDist(), g.arrivalDist(), EPS, "arrival on the prompt line");
            assertTrue(g.arrivalDist() < g.rampStartDist(), "arrival inside the fog onset");
            assertTrue(g.arrivalDist() < g.rearmDist(), "arrival inside the re-arm band");
        }
    }

    @Test
    void arrivalIs178DegOnEveryWorld() {
        // Since ARRIVAL_DEG == PROMPT_DEG == 178 and the 2-deg distance (41.67 on Itty) beats the 40-block prompt
        // floor even on the tiniest world, arrival sits at 178 deg on every world size.
        assertEquals(178.0, longitudeDegOfArrival(ITTY), 1e-6, "Itty-Bitty arrival at 178 deg");
        assertEquals(178.0, longitudeDegOfArrival(SMALL), 1e-6, "Small Wide arrival at 178 deg");
        assertEquals(178.0, longitudeDegOfArrival(REG), 1e-6, "Regular Wide arrival at 178 deg");
    }

    @Test
    void arrivalTargetMirrorPlusInlandPull_bothHemispheresClassicAndMercator() {
        // Reproduce HemispherePassageService.resolveArrival's pure math: mirror to pick the side, then use the
        // resolved arrivalAbsX for the inland depth. Classic (xRadius == zRadius) and Mercator (xRadius = 2z)
        // differ ONLY in the radius; the formula is identical.
        double centerX = 0.0;
        for (double xRadius : new double[] {3750.0 /*Classic z3750*/, 7500.0 /*Mercator z3750*/}) {
            double arrivalAbsX = EdgeGeometry.resolve(xRadius).arrivalAbsX();
            // East-side player -> mirrors West -> arrival on the West (negative X).
            double eastPlayer = xRadius - 30.0;
            double mirroredWest = HemispherePassage.mirrorX(eastPlayer, centerX);
            double signWest = mirroredWest >= centerX ? 1.0 : -1.0;
            assertEquals(-arrivalAbsX, centerX + signWest * arrivalAbsX, EPS,
                    "east->west arrival sits at -arrivalAbsX @ xRadius " + xRadius);
            // West-side player -> mirrors East -> arrival on the East (positive X).
            double westPlayer = -(xRadius - 30.0);
            double mirroredEast = HemispherePassage.mirrorX(westPlayer, centerX);
            double signEast = mirroredEast >= centerX ? 1.0 : -1.0;
            assertEquals(arrivalAbsX, centerX + signEast * arrivalAbsX, EPS,
                    "west->east arrival sits at +arrivalAbsX @ xRadius " + xRadius);
            // The arrival is the same distance from EITHER edge (symmetric), and clearly inside the world.
            assertTrue(arrivalAbsX > 0 && arrivalAbsX < xRadius);
            assertEquals(arrivalAbsX, xRadius - EdgeGeometry.resolve(xRadius).arrivalDist(), EPS);
        }
    }

    // ---- edge auto-re-prompt line: hard by the wall, clear of the vanilla damage zone -----

    @Test
    void edgeRepromptIsHardByTheWallAndClearOfTheBorderDamageZone() {
        // The post-arrival EDGE auto-re-prompt sits at 179.6 deg (0.4 deg out) but never closer than 8 blocks --
        // comfortably outside the vanilla WorldBorder's ~5-block warning/damage-safe zone even on the tiniest
        // world. It is the innermost of ALL the lines (closest to the wall).
        for (EdgeGeometry.Resolved g : new EdgeGeometry.Resolved[] {ITTY, SMALL, REG}) {
            assertTrue(g.edgeRepromptDist() >= 8.0 - EPS, "edgeReprompt >= 8 blocks (outside the ~5-block border zone)");
            assertTrue(g.edgeRepromptDist() < g.fogClimaxDist(), "edgeReprompt is inside the fog climax");
            assertTrue(g.edgeRepromptDist() < g.promptDist(), "edgeReprompt is inside the prompt line");
        }
        // On an un-floored world it is exactly the 179.6-deg distance.
        double bpd = 20000.0 / 180.0;
        assertEquals((180.0 - EdgeGeometry.EDGE_REPROMPT_DEG) * bpd, REG.edgeRepromptDist(), EPS);
    }

    // ---- immunity regression: geometry depends on intended radius ONLY ---------------------

    @Test
    void geometryIsAFunctionOfIntendedRadiusOnly_immuneToALerpingBorder() {
        // Simulate Peetsa's TEST-86 world: intended X radius 3750, but the LIVE border is mid-lerp at 3647.
        double intended = 3750.0;
        double liveHalfMidLerp = 3647.0;

        // The feature geometry is resolved from the intended radius, so it is byte-identical regardless of what
        // the live border currently reads -- resolve() literally never sees the live value.
        EdgeGeometry.Resolved g = EdgeGeometry.resolve(intended);
        assertEquals(2.0 * (intended / 180.0), g.promptDist(), EPS, "prompt = 2 deg = 41.67");
        assertEquals(100.0, g.rampStartDist(), EPS);

        // The distance-to-edge the OLD code computed off the live half WOULD have differed from the intended
        // one at the same world-X, which is exactly how the old lines slid ~100 blocks during his session.
        double x = 3700.0, centerX = 0.0;
        double distIntended = EdgeGeometry.distanceToEdge(intended, centerX, x);       // 50
        double distLive = EdgeGeometry.distanceToEdge(liveHalfMidLerp, centerX, x);    // 0 (clamped; 3647-3700 < 0)
        assertNotEquals(distIntended, distLive,
                "the live (mid-lerp) border yields a different edge distance -- the bug the redesign fixes");
        assertEquals(50.0, distIntended, EPS, "the intended-radius distance is stable and correct");
        assertEquals(0.0, distLive, EPS, "the mid-lerp live half would have read the player as already past the wall");
    }

    @Test
    void distanceToEdgeClampsAndCentersCorrectly() {
        assertEquals(0.0, EdgeGeometry.distanceToEdge(3750.0, 0.0, 4000.0), EPS, "beyond the edge clamps to 0");
        assertEquals(3750.0, EdgeGeometry.distanceToEdge(3750.0, 0.0, 0.0), EPS, "at center, full radius away");
        assertEquals(250.0, EdgeGeometry.distanceToEdge(3750.0, 0.0, -3500.0), EPS, "west side, symmetric");
    }

    /** Longitude degree (0..180) of a resolved arrival's |x|. */
    private static double longitudeDegOfArrival(EdgeGeometry.Resolved g) {
        return g.arrivalAbsX() / g.xRadiusIntended() * 180.0;
    }
}
