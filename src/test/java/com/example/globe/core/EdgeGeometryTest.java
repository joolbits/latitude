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
 *   <li>the arrival target (TEST 93: 178 deg / 2 deg from the wall -- INSIDE the fog on every world, at-or-
 *       inside the re-arm line (exactly on it on large worlds, strictly inside on tiny floored worlds);
 *       symmetric both hemispheres, Classic + Mercator formula), and</li>
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
        // distance-from-edge for a degree: (180 - deg) * bpd. The edge (180) is distance 0; 178 deg (the TEST 93
        // arrival anchor) is 2 deg in.
        assertEquals(0.0, EdgeGeometry.distForDeg(180.0, 20000.0), EPS);
        assertEquals(2.0 * (20000.0 / 180.0), EdgeGeometry.distForDeg(178.0, 20000.0), EPS);
    }

    // ---- exact effective geometry per world size -------------------------------------------

    @Test
    void ittyBittyClassicEffectiveGeometry_floorsBind() {
        // xRadius 3750: 1 deg = 20.833 blocks, so the floors engage. TEST 92: rampStart tightened (FOG_BAND_MIN
        // 120 -> 60, RAMP_START 176.5 -> 177.5), but on this tiny world the rearm+ORDER_STEP term dominates, so
        // rampStart = rearm(104) + 8 = 112. TEST 93: arrival moved 176 -> 178 deg; on this tiny world 178 deg =
        // 41.7 blocks is now BELOW the prompt+16 = 56 floor, so the ARRIVAL_MIN_PAST_PROMPT floor binds and pulls
        // arrival to 56 blocks (was 83.33) -- strictly inside the re-arm band.
        assertEquals(40.0, ITTY.promptDist(), EPS, "prompt floored to 40 (1 deg = 20.8 < 40)");
        assertEquals(104.0, ITTY.rearmDist(), EPS, "rearm = prompt + DEAD_ZONE (40 + 64), 178 deg floored");
        assertEquals(112.0, ITTY.rampStartDist(), EPS, "rampStart = rearm + ORDER_STEP (104 + 8), dominates the 60 fog floor");
        assertEquals(40.0, ITTY.fogClimaxDist(), EPS, "climax == prompt line");
        assertEquals(56.0, ITTY.arrivalDist(), EPS, "arrival floored to prompt+16 (40+16); 178 deg = 41.7 < 56");
        // Fog nest ordering holds on the smallest world: prompt < rearm < rampStart.
        assertTrue(ITTY.promptDist() < ITTY.rearmDist(), "prompt(40) < rearm(104)");
        assertTrue(ITTY.rearmDist() < ITTY.rampStartDist(), "rearm(104) < rampStart(112)");
        // TEST 93: on this floored world the 2-deg arrival (floored to 56) lands strictly INSIDE the re-arm band
        // (arrival < rearm), which the disarmed-arrival seed covers -- see arrivedInBandOnFlooredWorld_seedHolds.
        assertTrue(ITTY.arrivalDist() < ITTY.rearmDist(), "arrival(56) is INSIDE the re-arm band(104) on the tiny world");
        assertTrue(ITTY.arrivalDist() > ITTY.promptDist(), "but still past the prompt line(40)");
        assertTrue(ITTY.arrivalDist() < ITTY.rampStartDist(), "and inside the fog band (56 < 112)");
    }

    @Test
    void smallWideEffectiveGeometry_pureDegreesHold() {
        // xRadius 15000: 1 deg = 83.333 blocks; the pure degree values hold un-floored. Anchors: prompt 179,
        // rearm 178, rampStart 177.5, arrival 178 (TEST 93: was 176 -- now coincides with the re-arm line).
        double bpd = 15000.0 / 180.0;
        assertEquals(1.0 * bpd, SMALL.promptDist(), EPS);   // 179 deg
        assertEquals(2.0 * bpd, SMALL.rearmDist(), EPS);    // 178 deg
        assertEquals(2.5 * bpd, SMALL.rampStartDist(), EPS);// 177.5 deg
        assertEquals(2.0 * bpd, SMALL.arrivalDist(), EPS);  // 178 deg (TEST 93: == rearm line)
        assertEquals(SMALL.rearmDist(), SMALL.arrivalDist(), EPS, "arrival lands exactly on the re-arm line");
    }

    @Test
    void regularWideEffectiveGeometry_pureDegreesHold() {
        double bpd = 20000.0 / 180.0;
        assertEquals(1.0 * bpd, REG.promptDist(), EPS);     // 179 deg
        assertEquals(2.0 * bpd, REG.rearmDist(), EPS);      // 178 deg
        assertEquals(2.5 * bpd, REG.rampStartDist(), EPS);  // 177.5 deg
        assertEquals(2.0 * bpd, REG.arrivalDist(), EPS);    // 178 deg (TEST 93: == rearm line)
        assertEquals(REG.rearmDist(), REG.arrivalDist(), EPS, "arrival lands exactly on the re-arm line");
    }

    // ---- ordering invariant + floor disciplines --------------------------------------------

    @Test
    void orderingInvariantHoldsOnEveryWorldSize() {
        // The FOG NEST (prompt < rearm < rampStart) is the invariant resolve() guarantees on EVERY world size.
        // Arrival is NOT part of the nest (TEST 93): it lands AT-or-inside the re-arm line and inside the fog on
        // every world (see arrivalIsInsideFogBandOnEveryWorld) -- the only universal arrival guarantee here is
        // that it never lands at/inside the prompt line.
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

    // ---- arrival target (inside the fog edge; symmetric; Classic + Mercator) ---------------

    @Test
    void arrivalIsInsideFogBandOnEveryWorld() {
        // TEST 93: arrival moved to 178 deg (2 deg from the wall), 0.5 deg INSIDE the 177.5-deg fog onset -- so
        // arrival now lands inside the fog on EVERY world size (you emerge into the thinning fog edge, looking
        // inward), not past it. On large worlds it sits exactly on the re-arm line; on tiny worlds the readability
        // floor pulls it strictly inside. Either way arrivalDist < rampStartDist.
        assertTrue(SMALL.arrivalDist() < SMALL.rampStartDist(), "Small Wide: arrival inside the fog");
        assertTrue(REG.arrivalDist() < REG.rampStartDist(), "Regular Wide: arrival inside the fog");
        assertTrue(ITTY.arrivalDist() < ITTY.rampStartDist(), "Itty-Bitty: arrival inside the fog band (floored world)");
        // Universal: arrival is at-or-inside the re-arm line (never strictly beyond) on every world.
        assertTrue(SMALL.arrivalDist() <= SMALL.rearmDist() + EPS, "Small Wide: arrival at-or-inside the re-arm line");
        assertTrue(REG.arrivalDist() <= REG.rearmDist() + EPS, "Regular Wide: arrival at-or-inside the re-arm line");
        assertTrue(ITTY.arrivalDist() < ITTY.rearmDist(), "Itty-Bitty: arrival strictly inside the re-arm band");
    }

    @Test
    void arrivalIs178DegOnLargeWorldsAndFlooredOnTiny() {
        // TEST 93: 2 deg from the wall = 178 deg longitude on the un-floored large worlds. On Itty-Bitty the
        // ARRIVAL_MIN_PAST_PROMPT floor (prompt+16 = 56 blocks) exceeds 178 deg (41.7 blocks), so arrival is
        // pulled slightly inland of 178 deg there -- 56 blocks = 2.688 deg in = 177.312 deg longitude.
        assertEquals(178.0, longitudeDegOfArrival(SMALL), 1e-6, "Small Wide arrival at 178 deg");
        assertEquals(178.0, longitudeDegOfArrival(REG), 1e-6, "Regular Wide arrival at 178 deg");
        assertEquals(180.0 - 56.0 / (3750.0 / 180.0), longitudeDegOfArrival(ITTY), 1e-6,
                "Itty-Bitty arrival floored to 56 blocks = 177.312 deg (prompt+16 floor binds)");
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
