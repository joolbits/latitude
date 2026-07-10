package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link ZoneTitleBanding} (Phase 5 B-4: zone/climate-band title anti-spam,
 * mirroring the hemisphere-title band hysteresis). The FULL title fires ONCE on a genuine zone
 * change; a zone flip while the player LINGERS within one band-width of the boundary just crossed
 * shows only the SMALL action-bar message; the FULL title re-arms once the player SETTLES more than
 * a band-width from the nearest band boundary (deep inside a zone).
 */
class ZoneTitleBandingTest {

    private static final double BAND = 100.0; // one band-width, in the caller's unit (blocks)

    // ---- nearestBoundaryDistanceDeg: distance to the closest of {23.5,35,50,66.5}, not equator/pole ----

    @Test
    void nearestBoundaryDistanceIsToTheClosestBandEdge() {
        // Middle of TEMPERATE [35,50] == 42.5 deg -> 7.5 deg to either 35 or 50.
        assertEquals(7.5, ZoneTitleBanding.nearestBoundaryDistanceDeg(42.5), 1e-9);
        // Just inside SUBTROPICAL, 1 deg above the 23.5 edge.
        assertEquals(1.0, ZoneTitleBanding.nearestBoundaryDistanceDeg(24.5), 1e-9);
    }

    @Test
    void equatorAndPoleAreNotBoundariesSoDeepInteriorReadsAsFar() {
        // Deep in TROPICAL (equator, 0 deg): nearest boundary is 23.5, NOT the equator -> far from any edge.
        assertEquals(23.5, ZoneTitleBanding.nearestBoundaryDistanceDeg(0.0), 1e-9);
        // Deep in POLAR (85 deg): nearest boundary is 66.5, NOT the pole -> 18.5 deg away, "settled".
        assertEquals(18.5, ZoneTitleBanding.nearestBoundaryDistanceDeg(85.0), 1e-9);
    }

    // ---- (1) full-once: an armed zone change fires FULL and consumes the arm ----

    @Test
    void firstZoneChangeWhileArmedFiresFullAndConsumesTheArm() {
        // WHY: the big zone title must fire ONCE on a genuine change. At the boundary the player is right
        // on the edge (distToBoundary ~ 0 < band), so the re-arm cannot fire on the same sample.
        ZoneTitleBanding.Result r = ZoneTitleBanding.evaluate(
                /* zoneChanged */ true, /* fullArmed */ true, /* distToBoundary */ 0.0, BAND);
        assertEquals(ZoneTitleBanding.Fire.FULL, r.fire());
        assertFalse(r.nextFullArmed(), "full is consumed until the player settles deep in a zone again");
    }

    // ---- (2) small-on-re-cross-near-edge: a zone flip while still near the boundary is SMALL ----

    @Test
    void reCrossWhileLingeringNearBoundaryShowsSmallNotFull() {
        // WHY: walking/building along a band edge re-flips the zone key; while still within a band-width of
        // that boundary the big title must NOT re-fire -- only the small action-bar message.
        ZoneTitleBanding.Result first = ZoneTitleBanding.evaluate(true, true, 0.0, BAND);
        assertEquals(ZoneTitleBanding.Fire.FULL, first.fire());

        // Still near the boundary (10 blocks < band 100), zone flips again -> SMALL, arm stays spent.
        ZoneTitleBanding.Result second = ZoneTitleBanding.evaluate(
                true, first.nextFullArmed(), 10.0, BAND);
        assertEquals(ZoneTitleBanding.Fire.SMALL, second.fire());
        assertFalse(second.nextFullArmed(), "still near the boundary => full stays suppressed");
    }

    @Test
    void noZoneChangeNeverFiresRegardlessOfArmState() {
        assertEquals(ZoneTitleBanding.Fire.NONE,
                ZoneTitleBanding.evaluate(false, true, 0.0, BAND).fire());
        assertEquals(ZoneTitleBanding.Fire.NONE,
                ZoneTitleBanding.evaluate(false, false, 500.0, BAND).fire());
    }

    // ---- (3) full-re-arms-when-settled-deep: leaving the linger radius re-arms FULL ----

    @Test
    void settlingDeepInsideAZoneReArmsFull() {
        // WHY: the big title re-arms only after the player settles more than a band-width from the nearest
        // boundary. Isolated on a non-change sample (no fire) to prove the re-arm is driven purely by the
        // settle distance, not by a zone flip.
        ZoneTitleBanding.Result r = ZoneTitleBanding.evaluate(
                /* zoneChanged */ false, /* fullArmed */ false, /* distToBoundary */ BAND + 1.0, BAND);
        assertEquals(ZoneTitleBanding.Fire.NONE, r.fire());
        assertTrue(r.nextFullArmed(), "settling deep inside a zone re-arms the full title");
    }

    @Test
    void settleReArmThenNextChangeFiresFullAgain() {
        // WHY: end-to-end -- consume the arm at a boundary, wander deep (re-arm), then a fresh boundary
        // crossing must announce with the big title again.
        boolean armed = true;
        armed = ZoneTitleBanding.evaluate(true, armed, 0.0, BAND).nextFullArmed();      // FULL, armed->false
        assertFalse(armed);
        armed = ZoneTitleBanding.evaluate(false, armed, BAND + 5.0, BAND).nextFullArmed(); // settle -> re-arm
        assertTrue(armed);
        ZoneTitleBanding.Result next = ZoneTitleBanding.evaluate(true, armed, 0.0, BAND);
        assertEquals(ZoneTitleBanding.Fire.FULL, next.fire());
    }
}
