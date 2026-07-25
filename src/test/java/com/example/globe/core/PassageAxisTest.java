package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-JVM tests for {@link PassageAxis} (B-7) -- the EW/POLE discriminator carried on the netcode records.
 * The wire ids (EW=0, POLE=1) are lockstep-locked (client+server ship in one jar) and must stay stable, and a
 * rogue/out-of-range id must decode defensively rather than throw.
 */
class PassageAxisTest {

    @Test
    void wireIdsAreStable() {
        assertEquals(0, PassageAxis.EW.id());
        assertEquals(1, PassageAxis.POLE.id());
    }

    @Test
    void idRoundTrips() {
        assertEquals(PassageAxis.EW, PassageAxis.fromId(PassageAxis.EW.id()));
        assertEquals(PassageAxis.POLE, PassageAxis.fromId(PassageAxis.POLE.id()));
    }

    @Test
    void outOfRangeIdDecodesToEwDefensively() {
        assertEquals(PassageAxis.EW, PassageAxis.fromId(-1));
        assertEquals(PassageAxis.EW, PassageAxis.fromId(2));
        assertEquals(PassageAxis.EW, PassageAxis.fromId(999));
    }
}
