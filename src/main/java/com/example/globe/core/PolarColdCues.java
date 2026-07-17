package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- P2 client presentation LOGIC for the reworked polar-cold experience.
 * Pure Java, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM): every DECISION the P2
 * client makes about the warning ladder + the two cold whispers lives here as a truth table, so the wiring in
 * {@code GlobeWarningOverlay} / {@code GlobeModClient} stays a thin shim (armor read, sky-light read, warmth
 * scan, whisper trigger) over provable logic.
 *
 * <p><b>These present the GLOBAL S1/S3/S6 server mechanics</b> (frostbite band [85,88), full-set freeze
 * negation, the S6 heal lock) -- they are NOT gated on {@code POLE_PASSAGE_V2_ENABLED} (design item 8: the
 * cold PACING rebalance ships regardless of the crossing flag). Only the crossing UX (prompt/title/curtain)
 * is flag-gated, and that lives in the passage arm, not here.
 *
 * <h2>The reworked warning ladder (design item 4)</h2>
 * The B-4 four-rung ladder (85/87/89/89.7 in {@link PolarWarningEpisode}, still used by the vignette) is
 * SUPERSEDED for the pole-warning TEXT by a five-rung ladder that narrates the S3 two-stage cold:
 * <ul>
 *   <li>{@link Rung#APPROACH} -- PINNED symbolically to {@link PolarHazardWindow#AMBIENT_ONSET_DEG} (80 deg
 *       since S8, 2026-07-14; 82 under the earlier owner revision; the P2 draft said 83): the rung IS the
 *       snow onset, so the first words land with the first snowflakes wherever the ambient constant moves.
 *       Owner-verbatim copy "Entering polar storm country. Proceed with caution." (the old WARN_1 snow line
 *       is retired).</li>
 *   <li>{@link Rung#HYPOTHERMIA} 85 deg -- NEW: the frostbite-DAMAGE onset ({@link PolarHazardWindow#FROSTBITE_ONSET_DEG}),
 *       owner-verbatim copy "The cold begins to bite." (2026-07-14; replaces the draft "Hypothermia sets in.").
 *       SUPPRESSED when the player wears a FULL freeze-immune set (honesty law: full leather = zero frostbite =
 *       no bite = no warning). Its removal-reactive sibling is {@link #removalWhisperFires}.</li>
 *   <li>{@link Rung#BLIZZARD} 87 deg -- WARN_2, owner-verbatim (do not touch).</li>
 *   <li>{@link Rung#DANGER} 88 deg -- S13c re-time (2026-07-17): 89 -&gt; 88 (the lethal-core onset) + reworded
 *       present-tense "DANGER: Lethal blizzard. Turn back." (owner-approved change to his line); earns the
 *       vignette.</li>
 *   <li>{@link Rung#LETHAL} 89.7 deg -- earns the vignette; its TEXT swaps to the protected line when a full
 *       freeze-immune set is worn ({@link #lethalTextProtected}) -- a protected player must not be told they
 *       are freezing to death when the damage is negated.</li>
 * </ul>
 * Episode model (identical to {@link PolarWarningEpisode}): a rung fires ONCE when first crossed and the whole
 * ladder re-arms only on a full retreat below {@link #RETREAT_REARM_DEG}. The overlay owns the persisted
 * {@code highestFired} exactly as it did for the four-rung ladder.
 */
public final class PolarColdCues {

    private PolarColdCues() {
    }

    /** The five warning rungs, equator -&gt; pole. {@link #tier()} is the 1-based ladder index the overlay
     *  persists as {@code highestFired}; {@link #deg()} is the latitude onset. */
    public enum Rung {
        /** Approach -- PINNED to the ambient snow onset ({@link PolarHazardWindow#AMBIENT_ONSET_DEG}: 80 deg
         *  since S8) so "Entering polar storm country. Proceed with caution." (owner verbatim 2026-07-14)
         *  lands with the first snowflakes wherever the onset moves. */
        APPROACH(PolarHazardWindow.AMBIENT_ONSET_DEG),
        /** 85 deg -- NEW frostbite-onset "The cold begins to bite." (owner verbatim 2026-07-14);
         *  protection-suppressed. */
        HYPOTHERMIA(85.0),
        /** 87 deg -- WARN_2, owner-verbatim. */
        BLIZZARD(87.0),
        /** 88 deg -- DANGER (S13c re-time 2026-07-17: 89 -&gt; 88, = the lethal-core onset, so "Lethal blizzard"
         *  is honest instead of two degrees early; earns the vignette). Reworded present-tense (see
         *  {@code GlobeWarningOverlay.POLE_DANGER_TEXT}). NB: this is the TEXT rung's onset only -- the
         *  KEEP-SHARED fog/vignette-severity ladder ({@code PolarWarningEpisode.TIER_3_DEG},
         *  {@code LatitudeMath.POLAR_STAGE_*}) stays at 89. */
        DANGER(88.0),
        /** 89.7 deg -- LETHAL; earns the vignette; protection-swapped text. */
        LETHAL(89.7);

        private final double deg;

        Rung(double deg) {
            this.deg = deg;
        }

        /** Latitude onset (deg) for this rung. */
        public double deg() {
            return deg;
        }

        /** 1-based ladder tier index ({@code APPROACH == 1 ... LETHAL == 5}). */
        public int tier() {
            return ordinal() + 1;
        }

        /** The rung for a 1-based tier index, or {@code null} for 0 / out of range. */
        public static Rung forTier(int tier) {
            if (tier < 1 || tier > values().length) {
                return null;
            }
            return values()[tier - 1];
        }
    }

    /** Below this absolute latitude the whole ladder re-arms -- ONE degree below the first rung, DERIVED from
     *  it ({@code AMBIENT_ONSET_DEG - 1}: 79 since S8 moved the rung to 80; was 81 under the 82 revision),
     *  keeping the same 1-deg hysteresis width the P2 draft's 83/82 pairing (and B-4's 85/84,
     *  {@link PolarWarningEpisode#RETREAT_REARM_DEG}) had, so jitter on the onset line can never machine-gun
     *  the APPROACH rung -- and so the hysteresis moves WITH the rung automatically. */
    public static final double RETREAT_REARM_DEG = PolarHazardWindow.AMBIENT_ONSET_DEG - 1.0;

    /** The removal-reactive whisper (design S1 addendum): shown once when a FULL freeze-immune set is LOST while
     *  in the cold zone -- it REPLACES the (suppressed) hypothermia rung re-fire at that moment. Whisper family,
     *  black-outlined ({@code LatitudeWhisperOverlay}). */
    public static final String REMOVAL_WHISPER_TEXT = "Hypothermia is imminent.";

    /** The S6 frozen-wounds whisper (design item 5): shown once when the heal lock first bites while wounded. */
    public static final String FROZEN_WOUNDS_WHISPER_TEXT = "Your wounds are frozen. Only warmth can mend them.";

    /** Outcome of {@link #evaluateLadder}: the rung to FIRE now (or {@code null}) and the next {@code highestFired}
     *  ladder state to persist (which advances even when a fire is suppressed, so a suppressed rung never retries). */
    public record LadderStep(Rung fire, int nextHighestFired) {
    }

    /** Highest rung (1..5) whose onset {@code absLatDeg} is at/above, or 0 below the first rung. Symmetric about
     *  the equator (caller passes {@code |lat|}). */
    public static int tierForLat(double absLatDeg) {
        int tier = 0;
        for (Rung r : Rung.values()) {
            if (absLatDeg >= r.deg()) {
                tier = r.tier();
            }
        }
        return tier;
    }

    /**
     * Pure episode transition for the five-rung ladder. Fires the deepest rung newly reached this sample
     * ({@code tier > highestFired}); a full retreat below {@link #RETREAT_REARM_DEG} re-arms the whole ladder.
     *
     * <p><b>Hypothermia suppression (honesty law).</b> When the deepest newly-reached rung is exactly
     * {@link Rung#HYPOTHERMIA} AND a full freeze-immune set is worn, the FIRE is suppressed (returns {@code null})
     * but {@code highestFired} STILL advances to that rung -- so the rung never retries, and losing protection
     * later speaks through {@link #removalWhisperFires} instead (never a back-to-back double message). Only the
     * hypothermia rung is protection-suppressed; APPROACH is always shown and the three severe rungs always fire
     * (LETHAL only swaps its TEXT under protection, it is never suppressed).
     *
     * @param absLatDeg      current absolute latitude in degrees
     * @param highestFired   highest tier (1..5) fired since the last retreat, or 0
     * @param protectionFull true iff a full freeze-immune set is worn (from {@link ColdProtection#negatesFreezeDamage})
     */
    public static LadderStep evaluateLadder(double absLatDeg, int highestFired, boolean protectionFull) {
        if (absLatDeg < RETREAT_REARM_DEG) {
            return new LadderStep(null, 0); // full retreat: re-arm, never fire on the way out.
        }
        int tier = tierForLat(absLatDeg);
        if (tier <= highestFired) {
            return new LadderStep(null, highestFired); // nothing deeper than already announced.
        }
        Rung rung = Rung.forTier(tier);
        if (rung == Rung.HYPOTHERMIA && protectionFull) {
            // Fully protected: suppress the hypothermia rung's FIRE but advance past it so it never retries;
            // the removal whisper owns "you just lost your protection here".
            return new LadderStep(null, tier);
        }
        return new LadderStep(rung, tier);
    }

    /** True iff the LETHAL rung should show the PROTECTED line ("The bitter cold envelops you.") rather than the
     *  owner-verbatim "you are freezing to death" line -- i.e. a full freeze-immune set is worn. One evaluator,
     *  one truth: the same {@code protectionFull} that scales the damage to zero swaps the text. */
    public static boolean lethalTextProtected(boolean protectionFull) {
        return protectionFull;
    }

    /**
     * The removal-reactive whisper decision (design S1 addendum): fire once on the falling edge of full
     * protection while in the cold zone ({@code |lat| >=} {@link PolarHazardWindow#FROSTBITE_ONSET_DEG}). The
     * falling edge (was full, now not) is inherently one-shot per equip cycle -- re-gaining a full set (a rising
     * edge) is the natural re-arm, and losing it below 85 fires nothing. The caller persists
     * {@code prevProtectionFull} across samples.
     */
    public static boolean removalWhisperFires(double absLatDeg, boolean protectionFull, boolean prevProtectionFull) {
        boolean inZone = absLatDeg >= PolarHazardWindow.FROSTBITE_ONSET_DEG;
        return inZone && prevProtectionFull && !protectionFull;
    }

    /**
     * The S6 frozen-wounds whisper decision (design item 5): fire once on the rising edge of the heal lock
     * biting a wounded player ({@code active == }{@link PolarWounds#healLocked} {@code && health < max}). The
     * caller persists {@code prevActive}; the lock releasing (warmth / exposure / zone-exit / heal-to-full) is
     * the natural re-arm.
     */
    public static boolean frozenWoundsWhisperFires(boolean active, boolean prevActive) {
        return active && !prevActive;
    }
}
