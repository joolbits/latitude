package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EvatorCrossing} -- B-6 P2's silent-crossing state machine. Pins: the trigger
 * one-shot (with the gated HOLD, never a burn), the pre-warm one-shot (once per approach, never per tick),
 * the re-arm hysteresis band (leave past the pre-warm line), the DISARMED fresh-tracking/arrival contract,
 * NaN safety, the Itty-Bitty floored lines, and the full walk-in / turn-around / walk-in-again script the
 * design demands -- all against the REAL resolved {@link MirrorGeometry} lines per world size.
 */
class EvatorCrossingTest {

    // The real per-world lines the server resolves (MirrorGeometry, intended-radius anchored).
    private static final MirrorGeometry.Resolved ITTY = MirrorGeometry.resolve(3750.0);     // Classic Itty
    private static final MirrorGeometry.Resolved REG_C = MirrorGeometry.resolve(7500.0);    // Classic Regular
    private static final MirrorGeometry.Resolved REG_W = MirrorGeometry.resolve(20000.0);   // Wide Regular

    private static EvatorCrossing.Outcome eval(EvatorCrossing.State s, double dist, boolean canCross,
                                               MirrorGeometry.Resolved geo) {
        return EvatorCrossing.evaluate(s, dist, canCross, geo.triggerDist(), geo.preWarmDist());
    }

    // ---- the resolved band numbers themselves (documentation-pin; MirrorGeometryTest owns the math) -----

    @Test
    void resolvedLinesPerWorldSize_pinned() {
        // Classic Regular: the pure 0.5-deg trigger (20.8) sits UNDER the 24-block floor -> floored.
        assertEquals(24.0, REG_C.triggerDist(), 1e-6, "Regular Classic trigger floored to 24");
        assertEquals(125.0, REG_C.preWarmDist(), 1e-6, "Regular Classic pre-warm at 177 deg");
        // Wide Regular: pure degrees hold.
        assertEquals(55.555556, REG_W.triggerDist(), 1e-4, "Regular Wide trigger 179.5 deg");
        assertEquals(333.333333, REG_W.preWarmDist(), 1e-4, "Regular Wide pre-warm 177 deg");
        // Itty: trigger floored, pre-warm holds; the sticky band never collapses below the 32-block gap.
        assertEquals(24.0, ITTY.triggerDist(), 1e-6);
        assertEquals(62.5, ITTY.preWarmDist(), 1e-6);
        assertTrue(ITTY.preWarmDist() - ITTY.triggerDist() >= MirrorGeometry.PRE_WARM_MIN_GAP_BLOCKS,
                "Itty sticky band >= 32 blocks");
    }

    // ---- fresh-tracking contract -----------------------------------------------------------------------

    @Test
    void freshTrackingOutsideTheBand_reArmsOnFirstTick() {
        // Absent state = DISARMED; the first evaluated tick outside the pre-warm line re-arms both one-shots.
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.DISARMED, 5000.0, true, REG_C);
        assertFalse(out.firePreWarm());
        assertFalse(out.fireCross());
        assertEquals(EvatorCrossing.State.ARMED, out.next());
    }

    @Test
    void freshTrackingInsideTheBand_staysDisarmed_neverInstaTeleportsAtLogin() {
        // A player who logs in ALREADY inside the trigger band stays disarmed: no pre-warm, no cross.
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.DISARMED, REG_C.triggerDist() - 1.0, true, REG_C);
        assertFalse(out.firePreWarm());
        assertFalse(out.fireCross());
        assertEquals(EvatorCrossing.State.DISARMED, out.next());
        // ...tick after tick, until a genuine walk-out.
        out = eval(out.next(), REG_C.triggerDist() - 0.5, true, REG_C);
        assertFalse(out.firePreWarm());
        assertFalse(out.fireCross());
    }

    // ---- pre-warm one-shot -------------------------------------------------------------------------------

    @Test
    void preWarmFiresOncePerApproach_neverPerTick() {
        EvatorCrossing.State s = EvatorCrossing.State.ARMED;
        // Crossing the pre-warm line inbound: fires exactly once.
        EvatorCrossing.Outcome out = eval(s, REG_C.preWarmDist() - 1.0, true, REG_C);
        assertTrue(out.firePreWarm(), "pre-warm fires on entering the line");
        assertFalse(out.fireCross(), "still outside the trigger line");
        // Loitering inside the band: no repeat fire.
        out = eval(out.next(), REG_C.preWarmDist() - 2.0, true, REG_C);
        assertFalse(out.firePreWarm(), "one-shot: no per-tick refire");
        out = eval(out.next(), REG_C.preWarmDist() - 10.0, true, REG_C);
        assertFalse(out.firePreWarm());
    }

    @Test
    void preWarmIsNotGatedByCanCross() {
        // canCross=false (cooldown/underground) must NOT block the chunk pre-warm -- it is harmless anywhere.
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.ARMED, REG_C.preWarmDist() - 1.0, false, REG_C);
        assertTrue(out.firePreWarm(), "pre-warm ignores the cross gate");
        assertFalse(out.fireCross());
    }

    @Test
    void exactlyOnThePreWarmLine_isInside() {
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.ARMED, REG_C.preWarmDist(), true, REG_C);
        assertTrue(out.firePreWarm(), "dist == preWarmAt counts as inside (fires)");
    }

    // ---- trigger one-shot + gated hold --------------------------------------------------------------------

    @Test
    void triggerFiresOnceAtTheLine_thenDisarms() {
        EvatorCrossing.State s = EvatorCrossing.State.ARMED;
        EvatorCrossing.Outcome out = eval(s, REG_C.triggerDist(), true, REG_C);
        assertTrue(out.fireCross(), "dist == triggerAt fires");
        assertFalse(out.next().triggerArmed(), "one-shot burned on fire");
        // Still at the wall next tick (e.g. the cross REFUSED -- unsafe mirror column): no refire.
        out = eval(out.next(), 1.0, true, REG_C);
        assertFalse(out.fireCross(), "burned one-shot cannot machine-gun at the wall");
    }

    @Test
    void gatedTriggerIsHeldNotBurned() {
        // canCross=false at the trigger line (cooldown / underground / spectator): HOLD the one-shot.
        EvatorCrossing.State s = new EvatorCrossing.State(true, false); // pre-warm already spent this approach
        EvatorCrossing.Outcome out = eval(s, REG_C.triggerDist() - 2.0, false, REG_C);
        assertFalse(out.fireCross(), "gate closed: no fire");
        assertTrue(out.next().triggerArmed(), "one-shot HELD, not burned");
        // The instant the gate clears, it fires.
        out = eval(out.next(), REG_C.triggerDist() - 2.0, true, REG_C);
        assertTrue(out.fireCross(), "held one-shot fires when the gate opens");
        assertFalse(out.next().triggerArmed());
    }

    // ---- re-arm hysteresis (leave past the pre-warm line) --------------------------------------------------

    @Test
    void reArmOnlyPastThePreWarmLine() {
        EvatorCrossing.State spent = EvatorCrossing.State.DISARMED; // post-crossing seed
        // Anywhere at/inside the pre-warm line: still disarmed (sticky band).
        EvatorCrossing.Outcome out = eval(spent, REG_C.preWarmDist(), true, REG_C);
        assertEquals(EvatorCrossing.State.DISARMED, out.next(), "dist == preWarmAt does NOT re-arm (sticky)");
        out = eval(spent, REG_C.preWarmDist() - 40.0, true, REG_C);
        assertEquals(EvatorCrossing.State.DISARMED, out.next());
        // Strictly past it: both one-shots re-arm, and nothing fires on the re-arm tick.
        out = eval(spent, REG_C.preWarmDist() + 0.5, true, REG_C);
        assertEquals(EvatorCrossing.State.ARMED, out.next(), "leaving past the pre-warm line re-arms");
        assertFalse(out.firePreWarm());
        assertFalse(out.fireCross());
    }

    @Test
    void stickyBandJitterCannotReFire() {
        // Post-arrival, the player jitters between the trigger and the pre-warm line: nothing ever fires.
        EvatorCrossing.State s = EvatorCrossing.State.DISARMED;
        for (double d : new double[] {30.0, 50.0, 90.0, 120.0, 60.0, 25.0, 124.9}) {
            EvatorCrossing.Outcome out = eval(s, d, true, REG_C);
            assertFalse(out.firePreWarm(), "no pre-warm at d=" + d);
            assertFalse(out.fireCross(), "no cross at d=" + d);
            s = out.next();
        }
        assertEquals(EvatorCrossing.State.DISARMED, s, "still disarmed after in-band jitter");
    }

    // ---- the full script: walk in, cross, walk out, walk in again -----------------------------------------

    @Test
    void walkIn_cross_arriveDisarmed_walkOut_walkInAgain() {
        // Approach 1: deep interior -> pre-warm -> trigger.
        EvatorCrossing.State s = EvatorCrossing.State.DISARMED;
        EvatorCrossing.Outcome out = eval(s, 4000.0, true, REG_C);        // interior: re-arms
        assertEquals(EvatorCrossing.State.ARMED, out.next());
        out = eval(out.next(), REG_C.preWarmDist() - 5.0, true, REG_C);   // crosses 177 deg
        assertTrue(out.firePreWarm());
        out = eval(out.next(), 70.0, true, REG_C);                        // walking deeper
        assertFalse(out.firePreWarm());
        assertFalse(out.fireCross());
        out = eval(out.next(), REG_C.triggerDist() - 1.0, true, REG_C);   // crosses 179.5 deg
        assertTrue(out.fireCross(), "the silent crossing fires");

        // The server performs the teleport and seeds the ARRIVED player DISARMED (mirror preserves distance).
        s = EvatorCrossing.State.DISARMED;
        // Landing tick: same border distance, moving inward -- nothing may fire.
        out = eval(s, REG_C.triggerDist() - 1.0, true, REG_C);
        assertFalse(out.firePreWarm());
        assertFalse(out.fireCross());
        // Walking inward (momentum preserved): still inside the sticky band, still quiet.
        out = eval(out.next(), 80.0, true, REG_C);
        assertFalse(out.fireCross());
        // Passes the pre-warm line outbound: re-arms (no fire on that tick).
        out = eval(out.next(), REG_C.preWarmDist() + 1.0, true, REG_C);
        assertEquals(EvatorCrossing.State.ARMED, out.next());
        assertFalse(out.firePreWarm());

        // Approach 2 (turn around, walk back in): pre-warm fires again, trigger fires again.
        out = eval(out.next(), REG_C.preWarmDist() - 1.0, true, REG_C);
        assertTrue(out.firePreWarm(), "fresh approach: pre-warm re-fires");
        out = eval(out.next(), REG_C.triggerDist() - 0.5, true, REG_C);
        assertTrue(out.fireCross(), "fresh approach: crossing re-fires");
    }

    @Test
    void walkInTurnAroundBeforeTrigger_thenWalkInAgain() {
        // Enter the band (pre-warm fires), turn around BEFORE the trigger, leave, come back: pre-warm again.
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.ARMED, REG_C.preWarmDist() - 1.0, true, REG_C);
        assertTrue(out.firePreWarm());
        assertFalse(out.fireCross());
        out = eval(out.next(), REG_C.preWarmDist() - 20.0, true, REG_C);  // wanders, never reaches trigger
        assertFalse(out.fireCross());
        out = eval(out.next(), REG_C.preWarmDist() + 10.0, true, REG_C);  // walks out: re-arm
        assertEquals(EvatorCrossing.State.ARMED, out.next());
        out = eval(out.next(), REG_C.preWarmDist() - 1.0, true, REG_C);   // back in: fresh episode
        assertTrue(out.firePreWarm(), "pre-warm one-shot re-armed by the walk-out");
    }

    // ---- Itty floors: the machine still one-shots cleanly on the floored geometry --------------------------

    @Test
    void ittyFlooredLines_scriptStillHolds() {
        // trigger 24 (floored), preWarm 62.5: the sticky band is 38.5 blocks -- beyond jitter, still asymmetric.
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.ARMED, 62.0, true, ITTY);
        assertTrue(out.firePreWarm(), "pre-warm inside 62.5");
        out = eval(out.next(), 24.0, true, ITTY);
        assertTrue(out.fireCross(), "trigger at the floored 24");
        // Post-arrival seed; jitter around the floored trigger cannot re-fire.
        EvatorCrossing.State s = EvatorCrossing.State.DISARMED;
        for (double d : new double[] {20.0, 30.0, 45.0, 62.5}) {
            EvatorCrossing.Outcome o = eval(s, d, true, ITTY);
            assertFalse(o.fireCross());
            assertFalse(o.firePreWarm());
            s = o.next();
        }
        // Walk out past 62.5 re-arms.
        assertEquals(EvatorCrossing.State.ARMED, eval(s, 63.0, true, ITTY).next());
    }

    // ---- Wide/Mercator geometry sanity ---------------------------------------------------------------------

    @Test
    void wideWorldLines_pureDegreeMachine() {
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.ARMED, 300.0, true, REG_W); // inside 333.3
        assertTrue(out.firePreWarm());
        assertFalse(out.fireCross(), "300 blk is still outside the 55.6-blk trigger");
        out = eval(out.next(), 55.0, true, REG_W);
        assertTrue(out.fireCross());
    }

    // ---- NaN / degenerate safety ----------------------------------------------------------------------------

    @Test
    void nanDistanceReadsAsFar_reArmsAndNeverFires() {
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.DISARMED, Double.NaN, true, REG_C);
        assertFalse(out.firePreWarm());
        assertFalse(out.fireCross());
        assertEquals(EvatorCrossing.State.ARMED, out.next(), "NaN re-arms (treated as far)");
        // An ARMED player with a NaN read must not fire either.
        out = eval(EvatorCrossing.State.ARMED, Double.NaN, true, REG_C);
        assertFalse(out.fireCross());
        assertFalse(out.firePreWarm());
    }

    @Test
    void zeroDistance_atTheWall_firesWhenArmedAndGateOpen() {
        // The wall itself (dist 0) is inside the trigger line: the HELD one-shot stays fire-eligible all the
        // way down to 0 (the real can't-outrun-it guarantee -- the border is a damage boundary, not collision).
        EvatorCrossing.Outcome out = eval(EvatorCrossing.State.ARMED, 0.0, true, REG_C);
        assertTrue(out.fireCross(), "at the wall: the held one-shot fires");
        assertTrue(out.firePreWarm(), "pre-warm also catches up if the approach skipped its line tick");
    }

    // ---- arrival-Y decision (P2 sweep refinement): keep the player's Y only when probed FREE ---------------

    @Test
    void arrivalY_freeAtMirror_keepsThePlayersExactY() {
        // Airborne flyer: probed free air at the mirror -> exact altitude kept (no ground-slam).
        assertEquals(200.5, EvatorCrossing.chooseArrivalY(true, 71.0, 200.5), 1e-9);
        // Grounded walker at matching mirrored height (the norm): exact Y kept, mid-stride.
        assertEquals(72.0, EvatorCrossing.chooseArrivalY(true, 72.0, 72.0), 1e-9);
        // Walker slightly ABOVE the mirrored surface (downhill wobble): still their own Y (free air).
        assertEquals(74.2, EvatorCrossing.chooseArrivalY(true, 72.0, 74.2), 1e-9);
    }

    @Test
    void arrivalY_notFreeAtMirror_fallsBackToTheProbedSafeSurface() {
        // Under a mirrored solid overhang (player's Y is inside/under blocks at the mirror): safe surface.
        assertEquals(91.0, EvatorCrossing.chooseArrivalY(false, 91.0, 70.0), 1e-9);
        // Flyer level with a mirrored floating island (their Y is inside it): validated surface, no suffocation.
        assertEquals(71.0, EvatorCrossing.chooseArrivalY(false, 71.0, 140.0), 1e-9);
        // Unloaded destination reads as not-free by contract -> same safe fallback.
        assertEquals(65.0, EvatorCrossing.chooseArrivalY(false, 65.0, 65.5), 1e-9);
    }
}
