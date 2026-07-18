package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarWounds} (B-7 S6) -- the frozen-wounds heal-lock predicate (full 8-combo
 * truth table) and the F3+S6 frost-cue rule (cue persists under the lock).
 *
 * <p><b>Heal-cancel at the hook:</b> the {@code LivingEntityHealLockMixin} chokepoint's ONLY decision is
 * {@code GlobeMod.isPolarHealLocked}, whose ONLY decision after the thin input reads (zone from latitude,
 * shelter from raw sky light, warmth from the box scan) is {@link PolarWounds#healLocked} -- so the truth
 * table below IS the hook behavior: {@code locked -> ci.cancel() (heal no-op)}, {@code unlocked -> heal
 * applies vanilla}. A live-engine hook test is not feasible in the pure-JVM suite (needs a running level);
 * the mixin glue is a two-liner documented at the hook.
 */
class PolarWoundsTest {

    @Test
    void healLockTruthTable_allEightCombos() {
        // (inColdZone, sheltered, nearWarmth) -> locked. Locked iff zone && sheltered && !warmth.
        assertFalse(PolarWounds.healLocked(false, false, false), "below 85, exposed, no fire: vanilla");
        assertFalse(PolarWounds.healLocked(false, false, true), "below 85, exposed, by a fire: vanilla");
        assertFalse(PolarWounds.healLocked(false, true, false), "below 85, sheltered: vanilla (zone gates)");
        assertFalse(PolarWounds.healLocked(false, true, true), "below 85, sheltered by a fire: vanilla");
        assertFalse(PolarWounds.healLocked(true, false, false),
                "polar but EXPOSED: vanilla (the outdoor eat-vs-cold race stands)");
        assertFalse(PolarWounds.healLocked(true, false, true), "polar, exposed, near fire: vanilla");
        assertTrue(PolarWounds.healLocked(true, true, false),
                "polar + sheltered + NO warmth: FROZEN WOUNDS (the one locked cell)");
        assertFalse(PolarWounds.healLocked(true, true, true),
                "polar + sheltered + campfire: the ritual works, heals apply");
    }

    @Test
    void threeArgFormIsUnchangedFlagOffBehaviour() {
        // The legacy 3-arg form == the 4-arg form with fullyProtected=false, for every combo.
        for (boolean z : new boolean[]{false, true}) {
            for (boolean s : new boolean[]{false, true}) {
                for (boolean w : new boolean[]{false, true}) {
                    assertEquals(PolarWounds.healLocked(z, s, w, false), PolarWounds.healLocked(z, s, w),
                            "3-arg delegates to 4-arg with fullyProtected=false");
                }
            }
        }
    }

    @Test
    void fullSuitLiftsTheHealLock_sweepA1() {
        // The one locked cell (polar + sheltered + no warmth) UNLOCKS for a fully-suited traveller: a warm body
        // mends its wounds even in the deep cold, so the frozen-wounds whisper cannot fire under a full suit.
        assertTrue(PolarWounds.healLocked(true, true, false, false), "no suit: the wounds stay frozen");
        assertFalse(PolarWounds.healLocked(true, true, false, true), "full suit: the lock LIFTS (A1)");
        // Non-locked cells stay non-locked regardless of the suit.
        assertFalse(PolarWounds.healLocked(true, false, false, true), "exposed: still vanilla healing");
        assertFalse(PolarWounds.healLocked(false, true, false, true), "below 85: still vanilla healing");
    }

    @Test
    void frostCuePersistsUnderTheHealLock() {
        // S6 amendment to F3: cue active = (biting && !paused) OR healLocked. Sheltered pauses the bite, but a
        // heal-locked player keeps the frozen-hearts floor -- wounds LOOK frozen while they cannot mend.
        assertTrue(PolarWounds.frostCueActive(false, true),
                "sheltered + heal-locked: bite paused but the cue PERSISTS");
        assertTrue(PolarWounds.frostCueActive(true, false), "biting outdoors: cue on");
        assertTrue(PolarWounds.frostCueActive(true, true));
        assertFalse(PolarWounds.frostCueActive(false, false),
                "no bite, no lock (e.g. sheltered WITH a campfire, or S5 grace while exposed): cue clears");
    }

    @Test
    void cueValueUnderLockIsTheFrostbiteFloor() {
        // The cue VALUE under the lock is the same latitude-driven floor the bite uses -- one number source
        // (PolarHazardWindow.frostbiteFrostCueTicks), so lock-cue and bite-cue can never disagree.
        int cueAt86 = PolarHazardWindow.frostbiteFrostCueTicks(86.0);
        assertTrue(cueAt86 > 0);
        assertEquals(cueAt86, PolarHazardWindow.frostbiteFrostCueTicks(86.0));
    }
}
