package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PoleCrossingGrace} (B-7 S5c) -- the one-shot post-pole-crossing cold-grace
 * window. Mandated pins: grace active on ticks 0-44 after the crossing, cold resumes exactly at tick 45,
 * and the window never re-triggers once lapsed (only a NEW crossing stamps a new window).
 */
class PoleCrossingGraceTest {

    @Test
    void graceActiveTicksZeroThroughFortyFour() {
        long crossTick = 1000L;
        long until = PoleCrossingGrace.graceUntil(crossTick);
        assertEquals(crossTick + PoleCrossingGrace.GRACE_TICKS, until);
        for (long t = 0; t < PoleCrossingGrace.GRACE_TICKS; t++) {
            assertTrue(PoleCrossingGrace.isActive(until, crossTick + t),
                    "grace must be active at cross+"+ t);
        }
    }

    @Test
    void coldResumesExactlyAtTickFortyFive() {
        long crossTick = 1000L;
        long until = PoleCrossingGrace.graceUntil(crossTick);
        assertFalse(PoleCrossingGrace.isActive(until, crossTick + PoleCrossingGrace.GRACE_TICKS),
                "cold resumes exactly at cross+45");
        assertFalse(PoleCrossingGrace.isActive(until, crossTick + PoleCrossingGrace.GRACE_TICKS + 1));
        assertFalse(PoleCrossingGrace.isActive(until, crossTick + 100));
    }

    @Test
    void lapsedGraceNeverRetriggers() {
        // Once inactive it stays inactive for every later tick (monotone) -- only a NEW crossing stamping a
        // NEW graceUntil can open another window; the stamp site is the single pole-cross success path.
        long until = PoleCrossingGrace.graceUntil(2000L);
        boolean seenInactive = false;
        for (long now = 2000L; now <= 2200L; now++) {
            boolean active = PoleCrossingGrace.isActive(until, now);
            if (seenInactive) {
                assertFalse(active, "grace re-triggered at " + now + " without a new crossing");
            }
            if (!active) {
                seenInactive = true;
            }
        }
        assertTrue(seenInactive);
    }

    @Test
    void noneSentinelIsNeverActive() {
        assertFalse(PoleCrossingGrace.isActive(PoleCrossingGrace.NONE, 0L));
        assertFalse(PoleCrossingGrace.isActive(PoleCrossingGrace.NONE, Long.MAX_VALUE / 2));
    }
}
