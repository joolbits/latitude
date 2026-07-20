package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- Peetsa stipulation S6 (FROZEN WOUNDS): the pure heal-lock + frost-cue
 * predicates. Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). The MC-coupled parts --
 * the {@code LivingEntity.heal} chokepoint mixin ({@code LivingEntityHealLockMixin}) and the three input reads
 * (cold zone from latitude, S4 shelter from raw sky light, warmth from the {@link PolarWarmth} box scan) --
 * are thin shims in {@code GlobeMod}; every DECISION lives here as a pure truth table.
 *
 * <p><b>The rule ("only the warmth of a fire mends them").</b> While SHELTERED ({@link ColdShelter}) inside
 * the polar cold zone ({@code |lat| >= 85}, the frostbite onset) and NOT near warmth, ALL healing is
 * cancelled -- food/natural regen, Regeneration potions, golden apples' regen: every {@code heal()} waits.
 * Near warmth (a lit campfire, fire, lava, or a working furnace within the scan box) healing works normally --
 * pack fire or bleed. OUTDOORS (not sheltered) healing is untouched: the flight-tested eat-vs-cold race
 * stands; the lock is the INDOOR rule (it closes the S4 shelter-refill cheese where a snow burrow becomes a
 * free heal-to-full). Below 85 deg: normal healing, always. No cumulative pool, no persistent state -- the
 * lock is this pure predicate applied at heal time.
 *
 * <p><b>Accepted v1 edge:</b> golden-apple ABSORPTION (the yellow bonus hearts) is not a {@code heal()} and is
 * not blocked -- absorption is armor-over-wounds, not mending; the regen half of the apple IS blocked. Also
 * {@code /latdev}-style direct {@code setHealth} writes bypass {@code heal()} -- dev tools, not gameplay.
 *
 * <p><b>The F3 cue amendment (S6).</b> While the heal-lock is ACTIVE the frostbite frost-cue floor is KEPT
 * (hearts LOOK frozen while they cannot mend) even though the S4 shelter pause has stopped the damage-driven
 * cue; the cue clears when warmth is near or on leaving the zone/shelter. {@link #frostCueActive} is that one
 * rule in one place: {@code (cold biting AND not paused) OR heal-locked}.
 */
public final class PolarWounds {

    private PolarWounds() {
    }

    /**
     * The S6 heal lock: true iff ALL of -- in the polar cold zone ({@code |lat| >=}
     * {@code PolarHazardWindow.FROSTBITE_ONSET_DEG}), genuinely sheltered (S4 {@link ColdShelter}), and NOT
     * near a warmth source ({@link PolarWarmth} box scan). Exposed players, sub-85 players, and players by a
     * fire heal normally.
     *
     * <p>This three-arg form is the pre-B-10 / flag-OFF behaviour, UNCHANGED. It delegates to the four-arg
     * form with {@code fullyProtected=false}, so existing shims that do not yet read the suit compile and
     * behave exactly as before. B-10's shim wiring (P2) switches to the four-arg form.
     */
    public static boolean healLocked(boolean inColdZone, boolean sheltered, boolean nearWarmth) {
        return healLocked(inColdZone, sheltered, nearWarmth, false);
    }

    /**
     * The S6 heal lock with the B-10 full-suit exemption (sweep A1). A fully-suited traveller has a WARM body,
     * so their wounds MEND even sheltered in the deep cold: the lock LIFTS when {@code fullyProtected} is true.
     * This is a gameplay upgrade, not just a mute -- because the frozen-wounds whisper
     * ({@link PolarColdCues#frozenWoundsWhisperFires}) keys on this predicate's rising edge, lifting the lock
     * also means a full-suit player never hears "Your wounds are frozen." The pure law lands now; the
     * {@code GlobeMod} heal-chokepoint shim passes {@code fullyProtected} in P2.
     */
    public static boolean healLocked(boolean inColdZone, boolean sheltered, boolean nearWarmth,
                                     boolean fullyProtected) {
        return inColdZone && sheltered && !nearWarmth && !fullyProtected;
    }

    /**
     * The F3+S6 frost-cue rule: the frostbite ticksFrozen floor is active iff cold damage is actually being
     * dealt this tick ({@code coldBiting} -- already net of the S4 shelter pause and the S5 post-crossing
     * grace) OR the S6 heal-lock holds (frozen wounds look frozen). One predicate so the damage wiring and the
     * cue wiring can never drift apart.
     */
    public static boolean frostCueActive(boolean coldBiting, boolean healLocked) {
        return coldBiting || healLocked;
    }

    // --- S25(F) WHISPER HYSTERESIS (Peetsa 2026-07-20, TEST 117: "The warning message for 'your wounds are
    // --- frozen' inside the glacial caves is glitchy, it'll re-trigger.") --------------------------------
    //
    // ROOT CAUSE (diagnosed from the live wiring, PolarCuesClient.frozenWoundsWhisperTick): the whisper fired
    // on PolarColdCues.frozenWoundsWhisperFires -- a BARE rising edge (active && !prevActive) whose re-arm is
    // ANY single-tick falling edge. "Active" is re-derived EVERY tick from four threshold reads, and in the
    // glacial caves at least three of them sit exactly on flickering boundaries:
    //   (1) SHELTER: raw SKY light at the eye <= 3 (ColdShelter). The caves are entered THROUGH sky-open
    //       crevasses; walking under a skylight shaft pushes the eye read above 3 for a step or two
    //       (release + instant re-arm), then back under the roof (re-lock -> RE-FIRE). The dominant
    //       underground trigger.
    //   (2) COLD ZONE: the razor |lat| >= 85 (FROSTBITE_ONSET_DEG) with NO hysteresis -- unlike the warning
    //       ladder, which pairs its onset with RETREAT_REARM_DEG one degree below. Wandering caves near the
    //       85 line crosses it repeatedly; every equatorward step is a release, every poleward step a re-fire.
    //   (3) WARMTH: the 9x5x9 box scan is CACHED ~20 ticks; pacing at the scan-radius edge of a campfire
    //       flips nearWarmth on each re-scan (release/re-fire on a 1 s beat).
    //   (4) WOUNDED: health < max -- heals to full near warmth release it; the first freeze/fall damage tick
    //       re-fires it.
    // Each of these is a legitimate STATE change; the bug is that a ONE-TICK release re-armed the one-shot.
    //
    // THE LAW: the whisper fires ONCE on the lock's rising edge while ARMED, then stays disarmed until the
    // lock has been FULLY RELEASED (active == false) for WHISPER_REARM_RELEASE_TICKS CONSECUTIVE ticks --
    // sustained warmth / exposure / healing / zone-exit, not a boundary flicker. Any re-lock during the
    // release window resets the counter, so oscillation can never re-arm it. Pure state machine, MC-free;
    // the client shim (Crew 3's PolarCuesClient) holds one WhisperGate and calls step() per tick, firing on
    // step().fired(); world-switch reset = WhisperGate.ARMED (the fresh-login whisper still fires once).

    /**
     * S25(F): consecutive fully-released ticks (active == false) required before the frozen-wounds whisper
     * re-arms. 100 ticks = 5 s of sustained release -- long enough that no skylight flicker, 85-line jitter,
     * warmth-cache beat, or heal-to-full/damage bounce can re-arm it, short enough that a genuine second
     * episode (leave the shelter, come back later) whispers again.
     */
    public static final int WHISPER_REARM_RELEASE_TICKS = 100;

    /**
     * S25(F): the pure hysteresis state for the one-shot frozen-wounds whisper. {@code armed} = the whisper
     * may fire on the next rising edge; {@code releasedTicks} = consecutive inactive ticks accumulated toward
     * re-arm (only meaningful while disarmed; pinned 0 while armed or active). Immutable -- {@link #whisperStep}
     * returns the successor state.
     */
    public record WhisperGate(boolean armed, int releasedTicks) {
        /** The fresh state (world switch / login): armed, no release credit. */
        public static final WhisperGate ARMED = new WhisperGate(true, 0);
    }

    /** One step's outcome: whether the whisper fires THIS tick, and the state to persist for the next. */
    public record WhisperStep(boolean fired, WhisperGate next) {
    }

    /**
     * S25(F) the whisper gate's per-tick step (pure). Semantics:
     * <ul>
     *   <li><b>ARMED + active</b>: FIRE (once), disarm. The rising edge is implicit -- an armed gate can only
     *       be reached through a full release, so "armed AND active" IS the edge.</li>
     *   <li><b>DISARMED + active</b>: silent; the release counter resets to 0 (a re-lock during the window
     *       throws away all release credit -- oscillation never creeps toward re-arm).</li>
     *   <li><b>DISARMED + inactive</b>: the release counter advances; at
     *       {@link #WHISPER_REARM_RELEASE_TICKS} the gate re-arms (and a LATER rising edge may fire -- the
     *       re-arm tick itself never fires, since active is false on it).</li>
     *   <li><b>ARMED + inactive</b>: stays armed, counter stays 0.</li>
     * </ul>
     *
     * @param prev   the persisted gate (start from {@link WhisperGate#ARMED}).
     * @param active the S6 lock-bites-a-wounded-player predicate this tick
     *               ({@code wounded && }{@link #healLocked}).
     */
    public static WhisperStep whisperStep(WhisperGate prev, boolean active) {
        if (active) {
            if (prev.armed()) {
                return new WhisperStep(true, new WhisperGate(false, 0)); // FIRE once; disarm
            }
            return new WhisperStep(false, prev.releasedTicks() == 0 ? prev : new WhisperGate(false, 0));
        }
        if (prev.armed()) {
            return new WhisperStep(false, prev); // idle-armed: nothing to count
        }
        int released = prev.releasedTicks() + 1;
        if (released >= WHISPER_REARM_RELEASE_TICKS) {
            return new WhisperStep(false, WhisperGate.ARMED); // sustained release: re-armed (no fire now)
        }
        return new WhisperStep(false, new WhisperGate(false, released));
    }
}
