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
 *   <li>{@link Rung#BLIZZARD} 87 deg -- WARN_2; S16d (2026-07-18) swept the "Turn back" scold + "--" out of
 *       its text (see {@code GlobeWarningOverlay.POLE_WARN_2_TEXT}).</li>
 *   <li>{@link Rung#DANGER} 88 deg -- S13c re-time (2026-07-17): 89 -&gt; 88 (the lethal-core onset) + reworded
 *       present-tense; S16d (2026-07-18, owner TEST 106) dropped the trailing "Turn back." -&gt; now
 *       "DANGER: Lethal blizzard." (exploration is never scolded); earns the vignette.</li>
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
        /** 87 deg -- WARN_2 (S16d swept the "Turn back" scold + "--" out; see {@code POLE_WARN_2_TEXT}). */
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

    /** B-10 leather-line copy (design §4.3, sweep matrix row "Leather"): the once-per-zone-entry reassurance a
     *  leather-wearing (but not fully-suited) traveller earns. Whisper family, like the removal/frozen-wounds
     *  lines. Owner-verbatim. */
    public static final String LEATHER_PROTECTION_TEXT =
            "Your leather armour provides some protection against the bitter cold.";

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

    /**
     * B-10 full-suit total-silence ladder (design §4, sweep A6). Generalizes today's HYPOTHERMIA-only
     * suppression ({@link #evaluateLadder}) to EVERY rung: when a full polar SUIT is worn ({@code fullSuit})
     * NO rung FIRES -- the pole is quiet, the owner's "not a single warning message" -- but {@code highestFired}
     * still advances so nothing retries; losing the suit speaks through {@link #removalWhisperFires}. When
     * {@code fullSuit} is false (bare / leather / a PARTIAL 1-3 suit) every rung fires HONESTLY, including
     * HYPOTHERMIA: a partial-suit or leather traveller still feels the bite, they just take less damage. This
     * is the flag-ON path; the legacy {@link #evaluateLadder(double, int, boolean)} (HYPOTHERMIA-only
     * suppression under a full freeze-immune leather set) is the flag-OFF path.
     *
     * <p><b>Silence boundary (sweep A6).</b> The full-suit silence covers the five-rung ladder here + (via the
     * vignette earning only on DANGER/LETHAL, both suppressed) the vignette + the leather line
     * ({@link #leatherLineStep} -- a full-suit player wears no leather-only) + the hypothermia family. Purely
     * NAVIGATIONAL lines (the pack-ice wall actionbar, clamp feedback) are informational for everyone and are
     * NOT gated here.
     *
     * @param absLatDeg    current absolute latitude in degrees
     * @param highestFired highest tier (1..5) fired since the last retreat, or 0
     * @param fullSuit     true iff a full four-piece polar suit is worn ({@link ColdProtection#fullyProtected})
     */
    public static LadderStep evaluateLadderFullSuit(double absLatDeg, int highestFired, boolean fullSuit) {
        if (absLatDeg < RETREAT_REARM_DEG) {
            return new LadderStep(null, 0); // full retreat: re-arm, never fire on the way out.
        }
        int tier = tierForLat(absLatDeg);
        if (tier <= highestFired) {
            return new LadderStep(null, highestFired); // nothing deeper than already announced.
        }
        Rung rung = Rung.forTier(tier);
        if (fullSuit) {
            // Total silence: suppress the FIRE but advance past it so no rung ever retries.
            return new LadderStep(null, tier);
        }
        return new LadderStep(rung, tier);
    }

    /** Outcome of {@link #leatherLineStep}: fire the leather reassurance now (or not), and the next armed
     *  state to persist (re-arms on a full retreat, exactly like the ladder). */
    public record LeatherLineStep(boolean fire, boolean nextFired) {
    }

    /**
     * B-10 leather-line decision (design §4.3, sweep matrix). Fire {@link #LEATHER_PROTECTION_TEXT} ONCE on
     * first crossing the frostbite onset ({@link PolarHazardWindow#FROSTBITE_ONSET_DEG}, where the cold begins
     * to bite and protection first matters) while wearing at least one leather (other freeze-immune) piece and
     * NOT fully suited; the whole line re-arms on a full retreat below {@link #RETREAT_REARM_DEG} -- the same
     * hysteresis the ladder uses, so onset jitter can never machine-gun it. A full-suit traveller (silenced) and
     * a bare one (nothing to reassure) never see it. It ACCOMPANIES the honest HYPOTHERMIA rung -- a leather
     * wearer still feels the bite -- it does not replace it. Caller persists {@code alreadyFired}.
     *
     * @param absLatDeg    current absolute latitude in degrees
     * @param wearsLeather true iff at least one worn piece is a leather / other freeze-immune (non-suit) piece
     * @param fullSuit     true iff a full four-piece polar suit is worn (then this line is silent)
     * @param alreadyFired the persisted armed state (true = already fired this zone entry)
     */
    public static LeatherLineStep leatherLineStep(double absLatDeg, boolean wearsLeather, boolean fullSuit,
                                                  boolean alreadyFired) {
        if (absLatDeg < RETREAT_REARM_DEG) {
            return new LeatherLineStep(false, false); // full retreat: re-arm.
        }
        boolean inZone = absLatDeg >= PolarHazardWindow.FROSTBITE_ONSET_DEG;
        if (inZone && wearsLeather && !fullSuit && !alreadyFired) {
            return new LeatherLineStep(true, true);
        }
        return new LeatherLineStep(false, alreadyFired);
    }

    /**
     * @deprecated B-10 (sweep A7): RETIRING. This swapped the LETHAL rung to "The bitter cold envelops you."
     *     for a player who NEGATED freeze damage yet still saw LETHAL -- a state that, under the B-10 warning
     *     matrix, no longer exists: the ONLY damage-negating outfit is the full suit, and the full suit
     *     SILENCES the LETHAL rung entirely ({@link #evaluateLadderFullSuit}). Partial-suit and leather players
     *     take real damage, so the raw LETHAL line is honest for them. Kept only for the legacy flag-OFF path +
     *     the existing {@code GlobeWarningOverlay} wiring; the client-string removal is P2. Do not wire into any
     *     new path. (Owner may instead repurpose the line to a partial-suit deep-end -- their veto.)
     */
    @Deprecated
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
