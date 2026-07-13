package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EdgeGeometry} -- the single source of truth for the degree-anchored,
 * intended-radius E/W edge geometry (redesign 2026-07-12). Pins:
 * <ul>
 *   <li>degree -&gt; block conversion,</li>
 *   <li>the nested ordering invariant ({@code prompt < rearm < rampStart}) in the pure DEGREE case
 *       AND under the small-world floors (Itty / Small-Wide / Regular-Wide matrices),</li>
 *   <li>the exact effective block geometry on each world size,</li>
 *   <li>the fog/banner shared onset (== {@code rampStartDist}),</li>
 *   <li>the arrival target (TEST 92: 176 deg / 4 deg from the wall -- past the fog on large worlds, in the
 *       re-arm band on tiny worlds; symmetric both hemispheres, Classic + Mercator formula), and</li>
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
        // distance-from-edge for a degree: (180 - deg) * bpd. The edge (180) is distance 0; 176 deg (the TEST 92
        // arrival anchor) is 4 deg in.
        assertEquals(0.0, EdgeGeometry.distForDeg(180.0, 20000.0), EPS);
        assertEquals(4.0 * (20000.0 / 180.0), EdgeGeometry.distForDeg(176.0, 20000.0), EPS);
    }

    // ---- exact effective geometry per world size -------------------------------------------

    @Test
    void ittyBittyClassicEffectiveGeometry_floorsBind() {
        // xRadius 3750: 1 deg = 20.833 blocks, so the floors engage. TEST 92: rampStart tightened (FOG_BAND_MIN
        // 120 -> 60, RAMP_START 176.5 -> 177.5), but on this tiny world the rearm+ORDER_STEP term dominates, so
        // rampStart = rearm(104) + 8 = 112 (was 160). Arrival pulled in to 176 deg = 4 deg = 83.33 blocks (was
        // the rampStart+24 floor = 184).
        assertEquals(40.0, ITTY.promptDist(), EPS, "prompt floored to 40 (1 deg = 20.8 < 40)");
        assertEquals(104.0, ITTY.rearmDist(), EPS, "rearm = prompt + DEAD_ZONE (40 + 64), 178 deg floored");
        assertEquals(112.0, ITTY.rampStartDist(), EPS, "rampStart = rearm + ORDER_STEP (104 + 8), dominates the 60 fog floor");
        assertEquals(40.0, ITTY.fogClimaxDist(), EPS, "climax == prompt line");
        assertEquals(4.0 * (3750.0 / 180.0), ITTY.arrivalDist(), EPS, "arrival = 176 deg = 4 deg = 83.33 blocks (floor 56 doesn't bind)");
        // Fog nest ordering holds on the smallest world: prompt < rearm < rampStart.
        assertTrue(ITTY.promptDist() < ITTY.rearmDist(), "prompt(40) < rearm(104)");
        assertTrue(ITTY.rearmDist() < ITTY.rampStartDist(), "rearm(104) < rampStart(112)");
        // TEST 92: on this floored world the 4-deg arrival lands INSIDE the re-arm band (arrival < rearm), which
        // the disarmed-arrival seed covers -- see arrivedInBandOnFlooredWorld_seedHoldsDisarmed.
        assertTrue(ITTY.arrivalDist() < ITTY.rearmDist(), "arrival(83.3) is INSIDE the re-arm band(104) on the tiny world");
        assertTrue(ITTY.arrivalDist() > ITTY.promptDist(), "but still past the prompt line(40)");
    }

    @Test
    void smallWideEffectiveGeometry_pureDegreesHold() {
        // xRadius 15000: 1 deg = 83.333 blocks; the pure degree values hold un-floored. TEST 92 anchors:
        // prompt 179, rearm 178, rampStart 177.5 (was 176.5), arrival 176 (was 175.5).
        double bpd = 15000.0 / 180.0;
        assertEquals(1.0 * bpd, SMALL.promptDist(), EPS);   // 179 deg
        assertEquals(2.0 * bpd, SMALL.rearmDist(), EPS);    // 178 deg
        assertEquals(2.5 * bpd, SMALL.rampStartDist(), EPS);// 177.5 deg (TEST 92: was 3.5 = 176.5)
        assertEquals(4.0 * bpd, SMALL.arrivalDist(), EPS);  // 176 deg (TEST 92: was 4.5 = 175.5)
    }

    @Test
    void regularWideEffectiveGeometry_pureDegreesHold() {
        double bpd = 20000.0 / 180.0;
        assertEquals(1.0 * bpd, REG.promptDist(), EPS);     // 179 deg
        assertEquals(2.0 * bpd, REG.rearmDist(), EPS);      // 178 deg
        assertEquals(2.5 * bpd, REG.rampStartDist(), EPS);  // 177.5 deg (TEST 92)
        assertEquals(4.0 * bpd, REG.arrivalDist(), EPS);    // 176 deg (TEST 92)
    }

    // ---- ordering invariant + floor disciplines --------------------------------------------

    @Test
    void orderingInvariantHoldsOnEveryWorldSize() {
        // The FOG NEST (prompt < rearm < rampStart) is the invariant resolve() guarantees on EVERY world size.
        // Arrival is NOT part of the nest (TEST 92): it may land inside the re-arm band on floored small worlds
        // (see arrivalIsPastFogOnLargeWorldsButInBandOnTinyOnes) -- the only universal arrival guarantee is that
        // it never lands at/inside the prompt line.
        for (double xr : new double[] {3750.0, 5000.0, 7500.0, 10000.0, 15000.0, 20000.0, 40000.0}) {
            EdgeGeometry.Resolved g = EdgeGeometry.resolve(xr);
            assertTrue(g.promptDist() < g.rearmDist(), "prompt < rearm @ " + xr);
            assertTrue(g.rearmDist() < g.rampStartDist(), "rearm < rampStart @ " + xr);
            assertTrue(g.arrivalDist() > g.promptDist(), "arrival always past the prompt line @ " + xr);
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
                    "fog band width >= 120 @ " + xr);
        }
    }

    @Test
    void deadZoneFloorMatchesTheHysteresisDiscipline() {
        // The re-arm dead band floor is exactly the shared DEAD_ZONE the crossing-center machine uses.
        assertEquals(HemisphereCrossing.DEAD_ZONE_BLOCKS, EdgeGeometry.DEAD_ZONE_MIN_BLOCKS, EPS);
    }

    // ---- shared onset: fog == particles == banner cap --------------------------------------

    @Test
    void rampStartIsTheOneSharedOnset() {
        // The fog onset and the banner visibility cap are BOTH rampStartDist -- one story ("nothing
        // edge-related visible equatorward of ~177.5 deg"; TEST 89 removed the EW particles, so it's fog +
        // banner now). Pin it is the 177.5-deg distance on a world where the floor doesn't bind.
        double bpd = 20000.0 / 180.0;
        assertEquals((180.0 - EdgeGeometry.RAMP_START_DEG) * bpd, REG.rampStartDist(), EPS);
        // And the fog climax is the prompt line, so fog reaches full opacity exactly when the prompt fires.
        assertEquals(REG.promptDist(), REG.fogClimaxDist(), EPS);
    }

    // ---- arrival target (past the fog; symmetric; Classic + Mercator) ----------------------

    @Test
    void arrivalIsPastFogOnLargeWorldsButInBandOnTinyOnes() {
        // TEST 92: arrival is a fixed 4 deg (176 deg) from the wall. On properly-sized worlds that is PAST the
        // fog onset (rampStart sits at ~2.5 deg); on the tiny Itty-Bitty world the readability floors push
        // rampStart/rearm out beyond 4 deg, so the arrival lands inside the fog/re-arm band (the disarmed seed
        // covers no-self-reprompt there -- see the HemispherePassage arrival contract).
        assertTrue(SMALL.arrivalDist() > SMALL.rampStartDist(), "Small Wide: arrival past the fog");
        assertTrue(REG.arrivalDist() > REG.rampStartDist(), "Regular Wide: arrival past the fog");
        assertTrue(ITTY.arrivalDist() < ITTY.rampStartDist(), "Itty-Bitty: arrival lands inside the fog band (floored world)");
    }

    @Test
    void arrivalIsExactly176DegOnEveryWorld() {
        // TEST 92: 4 deg from the wall = 176 deg longitude, and the defensive prompt+16 floor does NOT bind on
        // any real world (>= 3750), so arrival is exactly 176 deg everywhere -- including Itty-Bitty (where 4 deg
        // is already 83.3 blocks > prompt+16 = 56).
        assertEquals(176.0, longitudeDegOfArrival(SMALL), 1e-6, "Small Wide arrival at 176 deg");
        assertEquals(176.0, longitudeDegOfArrival(REG), 1e-6, "Regular Wide arrival at 176 deg");
        assertEquals(176.0, longitudeDegOfArrival(ITTY), 1e-6, "Itty-Bitty arrival at 176 deg (floor doesn't bind)");
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

    // ---- immunity regression: geometry depends on intended radius ONLY ---------------------

    @Test
    void geometryIsAFunctionOfIntendedRadiusOnly_immuneToALerpingBorder() {
        // Simulate Peetsa's TEST-86 world: intended X radius 3750, but the LIVE border is mid-lerp at 3647.
        double intended = 3750.0;
        double liveHalfMidLerp = 3647.0;

        // The feature geometry is resolved from the intended radius, so it is byte-identical regardless of what
        // the live border currently reads -- resolve() literally never sees the live value.
        EdgeGeometry.Resolved g = EdgeGeometry.resolve(intended);
        assertEquals(40.0, g.promptDist(), EPS);
        assertEquals(112.0, g.rampStartDist(), EPS);

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
