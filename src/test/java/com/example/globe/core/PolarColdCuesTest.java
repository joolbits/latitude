package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarColdCues} -- the B-7 P2 reworked polar-cold presentation decisions:
 * <ul>
 *   <li>the FIVE-rung ladder truth table (rungs/degrees, episodic monotonic fire, retreat re-arm),</li>
 *   <li>the HYPOTHERMIA-rung protection SUPPRESSION (fire suppressed but state advanced so it never retries),</li>
 *   <li>the LETHAL text swap under full protection (LETHAL never suppressed, only re-worded),</li>
 *   <li>the removal-reactive whisper one-shot (falling edge of full protection, in-zone only),</li>
 *   <li>the S6 frozen-wounds whisper one-shot / re-arm (rising edge of the heal lock while wounded).</li>
 * </ul>
 */
class PolarColdCuesTest {

    // ---- rung anchors ----

    @Test
    void rungDegreesAndTiers() {
        // 80 since S8 (2026-07-14; 82 under the earlier owner revision, draft said 83) -- the first words
        // land with the first snowflakes because the rung is PINNED to the AMBIENT_ONSET_DEG snow onset.
        assertEquals(80.0, PolarColdCues.Rung.APPROACH.deg());
        assertEquals(PolarHazardWindow.AMBIENT_ONSET_DEG, PolarColdCues.Rung.APPROACH.deg());
        assertEquals(85.0, PolarColdCues.Rung.HYPOTHERMIA.deg());
        assertEquals(87.0, PolarColdCues.Rung.BLIZZARD.deg());
        assertEquals(89.0, PolarColdCues.Rung.DANGER.deg());
        assertEquals(89.7, PolarColdCues.Rung.LETHAL.deg());
        assertEquals(1, PolarColdCues.Rung.APPROACH.tier());
        assertEquals(5, PolarColdCues.Rung.LETHAL.tier());
        assertEquals(PolarColdCues.Rung.APPROACH, PolarColdCues.Rung.forTier(1));
        assertEquals(PolarColdCues.Rung.LETHAL, PolarColdCues.Rung.forTier(5));
        assertNull(PolarColdCues.Rung.forTier(0));
        assertNull(PolarColdCues.Rung.forTier(6));
    }

    @Test
    void tierForLatClimbsThroughEveryRung() {
        assertEquals(0, PolarColdCues.tierForLat(79.9)); // below the first rung (and below the snow onset)
        assertEquals(1, PolarColdCues.tierForLat(80.0)); // first words land with the first snowflakes
        assertEquals(1, PolarColdCues.tierForLat(84.9));
        assertEquals(2, PolarColdCues.tierForLat(85.0));
        assertEquals(3, PolarColdCues.tierForLat(87.0));
        assertEquals(4, PolarColdCues.tierForLat(89.0));
        assertEquals(5, PolarColdCues.tierForLat(89.7));
        assertEquals(5, PolarColdCues.tierForLat(90.0));
    }

    // ---- the ladder episode (unprotected) ----

    @Test
    void ladderFiresEachRungOnceOnTheWayIn() {
        int highest = 0;
        // below the first rung: nothing (79.5 is also inside the 79-80 hysteresis strip: quiet, NOT a re-arm)
        PolarColdCues.LadderStep s = PolarColdCues.evaluateLadder(79.5, highest, false);
        assertNull(s.fire());
        assertEquals(0, s.nextHighestFired());
        // APPROACH (at the snow onset, 80 since S8)
        s = PolarColdCues.evaluateLadder(80.0, s.nextHighestFired(), false);
        assertEquals(PolarColdCues.Rung.APPROACH, s.fire());
        // still APPROACH band: no re-fire
        assertNull(PolarColdCues.evaluateLadder(84.0, s.nextHighestFired(), false).fire());
        // HYPOTHERMIA
        s = PolarColdCues.evaluateLadder(85.0, s.nextHighestFired(), false);
        assertEquals(PolarColdCues.Rung.HYPOTHERMIA, s.fire());
        // BLIZZARD
        s = PolarColdCues.evaluateLadder(87.0, s.nextHighestFired(), false);
        assertEquals(PolarColdCues.Rung.BLIZZARD, s.fire());
        // DANGER
        s = PolarColdCues.evaluateLadder(89.0, s.nextHighestFired(), false);
        assertEquals(PolarColdCues.Rung.DANGER, s.fire());
        // LETHAL
        s = PolarColdCues.evaluateLadder(89.7, s.nextHighestFired(), false);
        assertEquals(PolarColdCues.Rung.LETHAL, s.fire());
        assertEquals(5, s.nextHighestFired());
    }

    @Test
    void teleportSkipFiresOnlyTheDeepestRung() {
        // Fresh (highest 0), land straight at DANGER: fire DANGER once, advance to 4 (APPROACH/HYPO/BLIZZARD skipped).
        PolarColdCues.LadderStep s = PolarColdCues.evaluateLadder(89.0, 0, false);
        assertEquals(PolarColdCues.Rung.DANGER, s.fire());
        assertEquals(4, s.nextHighestFired());
    }

    @Test
    void fullRetreatBelow79ReArmsTheLadder() {
        // The re-arm line is 79 (S8) -- DERIVED as one deg below the 80 first rung (the same 1-deg hysteresis
        // width the 83/82 draft pairing had): 79.5 lingers (no re-arm, no fire), 78.9 re-arms.
        assertEquals(79.0, PolarColdCues.RETREAT_REARM_DEG, 1e-9);
        assertEquals(PolarHazardWindow.AMBIENT_ONSET_DEG - 1.0, PolarColdCues.RETREAT_REARM_DEG, 1e-9,
                "re-arm stays exactly one degree under the pinned first rung");
        assertNull(PolarColdCues.evaluateLadder(79.5, 5, false).fire());
        assertEquals(5, PolarColdCues.evaluateLadder(79.5, 5, false).nextHighestFired()); // hysteresis holds
        assertNull(PolarColdCues.evaluateLadder(78.9, 5, false).fire());
        assertEquals(0, PolarColdCues.evaluateLadder(78.9, 5, false).nextHighestFired());
        // ...and back in fires APPROACH again.
        assertEquals(PolarColdCues.Rung.APPROACH, PolarColdCues.evaluateLadder(80.0, 0, false).fire());
    }

    // ---- protection suppression of the hypothermia rung ----

    @Test
    void hypothermiaRungSuppressedUnderFullProtectionButStateAdvances() {
        // Reach 85 with a full set: no fire, but highest advances to 2 so the rung never retries.
        PolarColdCues.LadderStep s = PolarColdCues.evaluateLadder(85.0, 1, true);
        assertNull(s.fire());
        assertEquals(2, s.nextHighestFired());
        // Now REMOVE the armor at 86 (still in the hypothermia band): the RUNG does not re-fire (the removal
        // whisper owns that moment); the ladder stays quiet until BLIZZARD.
        assertNull(PolarColdCues.evaluateLadder(86.0, 2, false).fire());
        assertEquals(PolarColdCues.Rung.BLIZZARD, PolarColdCues.evaluateLadder(87.0, 2, false).fire());
    }

    @Test
    void approachAndSevereRungsAreNeverProtectionSuppressed() {
        // APPROACH shows even fully protected.
        assertEquals(PolarColdCues.Rung.APPROACH, PolarColdCues.evaluateLadder(80.0, 0, true).fire());
        // LETHAL fires regardless of protection (only its TEXT swaps).
        assertEquals(PolarColdCues.Rung.LETHAL, PolarColdCues.evaluateLadder(89.7, 4, true).fire());
    }

    // ---- LETHAL text swap ----

    @Test
    void lethalTextProtectedTracksTheFullSet() {
        assertTrue(PolarColdCues.lethalTextProtected(true));
        assertFalse(PolarColdCues.lethalTextProtected(false));
    }

    // ---- removal-reactive whisper ----

    @Test
    void removalWhisperFiresOnLosingFullSetInZoneOnly() {
        // Lost a full set at 85 (prev full, now not): fire.
        assertTrue(PolarColdCues.removalWhisperFires(85.0, false, true));
        // Still fully protected: no fire.
        assertFalse(PolarColdCues.removalWhisperFires(85.0, true, true));
        // Never had it (prev not full): no fire.
        assertFalse(PolarColdCues.removalWhisperFires(85.0, false, false));
        // Re-gaining a full set (rising edge): no fire.
        assertFalse(PolarColdCues.removalWhisperFires(85.0, true, false));
        // Lost it BELOW the zone (84 < 85): no fire.
        assertFalse(PolarColdCues.removalWhisperFires(84.0, false, true));
    }

    // ---- S6 frozen-wounds whisper ----

    @Test
    void frozenWoundsWhisperIsAOneShotRisingEdge() {
        assertTrue(PolarColdCues.frozenWoundsWhisperFires(true, false));   // lock just bit -> fire
        assertFalse(PolarColdCues.frozenWoundsWhisperFires(true, true));   // already active -> silent
        assertFalse(PolarColdCues.frozenWoundsWhisperFires(false, true));  // lock released -> re-arm, silent
        assertFalse(PolarColdCues.frozenWoundsWhisperFires(false, false)); // idle -> silent
    }

    @Test
    void whisperCopyIsPinned() {
        assertEquals("Hypothermia is imminent.", PolarColdCues.REMOVAL_WHISPER_TEXT);
        assertEquals("Your wounds are frozen. Only warmth can mend them.", PolarColdCues.FROZEN_WOUNDS_WHISPER_TEXT);
    }
}
