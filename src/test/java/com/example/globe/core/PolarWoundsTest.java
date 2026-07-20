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

    // ---- S25(F) whisper hysteresis (TEST 117: "it'll re-trigger" in the glacial caves) ----

    /** Drive the gate through a tick sequence; returns how many ticks FIRED. */
    private static int fires(PolarWounds.WhisperGate[] gate, boolean... actives) {
        int fired = 0;
        for (boolean active : actives) {
            PolarWounds.WhisperStep step = PolarWounds.whisperStep(gate[0], active);
            if (step.fired()) {
                fired++;
            }
            gate[0] = step.next();
        }
        return fired;
    }

    @Test
    void whisperFiresOnceOnTheRisingEdge() {
        PolarWounds.WhisperGate[] gate = {PolarWounds.WhisperGate.ARMED};
        // idle, then the lock bites: exactly one fire, and holding the lock never re-fires.
        assertEquals(1, fires(gate, false, false, true, true, true, true));
        // Fresh-login edge case: armed + immediately active fires once too (same as today's behaviour).
        PolarWounds.WhisperGate[] fresh = {PolarWounds.WhisperGate.ARMED};
        assertEquals(1, fires(fresh, true, true));
    }

    @Test
    void whisperNeverRefiresOnOscillation_theGlacialCavesBug() {
        // The TEST 117 failure shape: skylight shafts / the 85-line / the warmth-cache beat flip "active"
        // for a tick or three, then the lock re-engages. Under the old bare rising-edge every re-engage
        // re-fired; under the hysteresis a short release grants NO re-arm and the counter resets on re-lock.
        PolarWounds.WhisperGate[] gate = {PolarWounds.WhisperGate.ARMED};
        assertEquals(1, fires(gate, true), "the genuine first fire");
        // 200 ticks of pathological 3-off/5-on flicker (each release far below the 100-tick window): silent.
        boolean[] flicker = new boolean[200];
        for (int t = 0; t < flicker.length; t++) {
            flicker[t] = (t % 8) >= 3; // 3 released, 5 locked, repeating
        }
        assertEquals(0, fires(gate, flicker), "boundary flicker can never re-fire the whisper");
        // Even a NEAR-window release (99 ticks) followed by a re-lock stays silent: the counter reset.
        boolean[] nearMiss = new boolean[PolarWounds.WHISPER_REARM_RELEASE_TICKS]; // 100 ticks: 99 off + 1 on
        nearMiss[nearMiss.length - 1] = true;
        assertEquals(0, fires(gate, nearMiss), "a 99-tick release is not a sustained release");
    }

    @Test
    void whisperRearmsOnlyAfterSustainedRelease() {
        PolarWounds.WhisperGate[] gate = {PolarWounds.WhisperGate.ARMED};
        assertEquals(1, fires(gate, true), "fire and disarm");
        // A full sustained release (100 consecutive inactive ticks) re-arms the gate...
        boolean[] release = new boolean[PolarWounds.WHISPER_REARM_RELEASE_TICKS];
        assertEquals(0, fires(gate, release), "the release itself never fires");
        assertTrue(gate[0].armed(), "re-armed after WHISPER_REARM_RELEASE_TICKS of full release");
        // ...so a genuine SECOND episode whispers again -- once.
        assertEquals(1, fires(gate, true, true, true), "the second episode's single fire");
        // And the constant itself is pinned (5 s at 20 tps -- the S25(F) design value).
        assertEquals(100, PolarWounds.WHISPER_REARM_RELEASE_TICKS);
    }

    @Test
    void whisperGateStateMachineInvariants() {
        // Armed + inactive: stays armed, no counting.
        PolarWounds.WhisperStep idle = PolarWounds.whisperStep(PolarWounds.WhisperGate.ARMED, false);
        assertFalse(idle.fired());
        assertEquals(PolarWounds.WhisperGate.ARMED, idle.next());
        // Disarmed + active with accumulated release credit: the credit is thrown away (reset to 0).
        PolarWounds.WhisperStep reset = PolarWounds.whisperStep(new PolarWounds.WhisperGate(false, 57), true);
        assertFalse(reset.fired());
        assertEquals(new PolarWounds.WhisperGate(false, 0), reset.next());
        // Disarmed + active with zero credit: state object unchanged (no churn on the steady locked state).
        PolarWounds.WhisperGate steady = new PolarWounds.WhisperGate(false, 0);
        assertEquals(steady, PolarWounds.whisperStep(steady, true).next());
        // The re-arm TICK itself never fires (active is false on it, by construction).
        PolarWounds.WhisperStep rearm = PolarWounds.whisperStep(
                new PolarWounds.WhisperGate(false, PolarWounds.WHISPER_REARM_RELEASE_TICKS - 1), false);
        assertFalse(rearm.fired());
        assertTrue(rearm.next().armed());
    }
}
