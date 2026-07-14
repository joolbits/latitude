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
     */
    public static boolean healLocked(boolean inColdZone, boolean sheltered, boolean nearWarmth) {
        return inColdZone && sheltered && !nearWarmth;
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
}
