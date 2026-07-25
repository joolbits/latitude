package com.example.globe.core.ui;

/**
 * Phase 5 (understudy SWING): the pure WALL-CLOCK envelope for the polar-warning VIGNETTE -- a subtle
 * edge-darkening pulse that fires in time with the DANGER / LETHAL episodic warning text (see
 * {@code GlobeWarningOverlay} + {@code core.PolarWarningEpisode}), so the words and the world darken as ONE
 * moment. Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM): callers pass the fired
 * tier, elapsed wall-clock ms since the pulse armed, whether the lethal state still persists, and the Reduce
 * Motion flag, and read back a single screen-EDGE alpha in {@code [0,1]}.
 *
 * <p><b>Only the two serious tiers earn atmosphere.</b> WARN_1 (tier 1) and WARN_2 (tier 2) return a flat
 * {@code 0} -- no vignette. DANGER (tier 3) pulses to a subtle edge peak; LETHAL (tier 4) pulses deeper and
 * then lingers at a faint whisper for as long as the player stays in the lethal zone. Any other tier value
 * (0, negatives, &gt;4) is inert, so the whole feature is a provable no-op whenever no DANGER/LETHAL episode
 * is armed.
 *
 * <p><b>Choreography</b> (wall-clock ms, matching the ~10 s text window it punctuates): rise quickly to a
 * crest, settle to a fainter hold that carries while the warning text is on screen, then melt out with the
 * text's fade. LETHAL melts down to {@link #LETHAL_LINGER} (not 0) while {@code lethalPersists}. The tick-clock
 * lesson is law here (title-family migration): this envelope is driven by wall-clock elapsed ms, never game
 * ticks, so it never rubber-bands when the server tick stalls during a teleport into the pole.
 *
 * <p><b>Reduce Motion</b>: the pulse is replaced by a STATIC faint vignette held at the tier's hold level for
 * the life of the window (then the lethal linger) -- the information (something serious just fired) is
 * preserved, the motion is removed.
 */
public final class PolarWarningVignette {

    private PolarWarningVignette() {
    }

    /** Tier indices mirror {@code core.PolarWarningEpisode}: only these two earn a vignette. */
    public static final int TIER_DANGER = 3;
    public static final int TIER_LETHAL = 4;

    // --- Envelope timing (wall-clock ms). RISE + SETTLE + HOLD + MELT ~= the ~10 s episodic text window. ---
    /** Quick rise from 0 to the crest peak. */
    public static final long RISE_MS = 250L;
    /** Settle from the crest peak down to the faint HOLD level (so it "rises quickly, holds faintly"). */
    public static final long SETTLE_MS = 500L;
    /** End of the faint hold / start of the melt (ms since arm). Chosen so RISE+SETTLE+hold ~= the text's ~9 s. */
    public static final long HOLD_END_MS = 9000L;
    /** Melt fade-out, aligned with the warning text's ~1 s fade. */
    public static final long MELT_MS = 1000L;
    /** End of the whole pulse (ms since arm); after this only the lethal linger (if any) remains. */
    public static final long PULSE_END_MS = HOLD_END_MS + MELT_MS; // 10000

    // --- Edge-alpha peaks (fraction of full screen-edge darkening; the DRAW keeps the center transparent). ---
    /** DANGER crest: subtle. */
    public static final float DANGER_PEAK = 0.25f;
    /** LETHAL crest: deeper. */
    public static final float LETHAL_PEAK = 0.40f;
    /** The faint HOLD level as a fraction of the crest peak -- the "holds faintly" floor the pulse settles to. */
    public static final float HOLD_FRAC = 0.5f;
    /** LETHAL residual whisper that lingers while the player stays in the lethal zone. */
    public static final float LETHAL_LINGER = 0.08f;

    // --- CD F3: LETHAL-only WARM tint pulse -----------------------------------------------------------
    // DANGER and LETHAL fire 0.7 deg apart and both read as the same cold near-black vignette. To make the
    // final rung a DISTINCT, worse beat, LETHAL (and only LETHAL) briefly warms the vignette toward a deep
    // ember at its onset -- a small warm shift on the crest, decaying back to the cold storm palette well
    // before the hold ends. DANGER stays fully cold. This is a scalar the DRAW blends the tint by; the
    // envelope alpha (edgeAlpha) is unchanged.

    /** End of LETHAL's warm-tint pulse (ms since arm): the ember warmth decays from the crest back to 0 here,
     *  so the warmth is a brief accent at the LETHAL onset, not a persistent red vignette. */
    public static final long LETHAL_WARM_DECAY_END_MS = 2500L;
    /** Reduce Motion: a flat, faint static warmth for the window's life instead of the animated pulse. */
    public static final float LETHAL_WARM_STATIC = 0.5f;

    /**
     * LETHAL-only warm-tint amount in {@code [0,1]} (0 for every other tier) the vignette DRAW uses to blend
     * its cold tint toward a deep ember at the LETHAL onset. Rises with the crest (0 -> 1 over {@link #RISE_MS}),
     * then decays back to 0 by {@link #LETHAL_WARM_DECAY_END_MS}. Reduce Motion substitutes a flat
     * {@link #LETHAL_WARM_STATIC} for the on-screen window (no pulse). A provable no-op for DANGER / any other
     * tier (returns 0), so DANGER's vignette stays fully cold.
     */
    public static float lethalWarmth(int tier, long elapsedMs, boolean reduceMotion) {
        if (tier != TIER_LETHAL) {
            return 0f;
        }
        if (reduceMotion) {
            return (elapsedMs < 0L || elapsedMs >= HOLD_END_MS) ? 0f : LETHAL_WARM_STATIC;
        }
        if (elapsedMs < 0L) {
            return 0f;
        }
        if (elapsedMs < RISE_MS) {
            return (float) elapsedMs / (float) RISE_MS;
        }
        if (elapsedMs < LETHAL_WARM_DECAY_END_MS) {
            float p = (float) (elapsedMs - RISE_MS) / (float) (LETHAL_WARM_DECAY_END_MS - RISE_MS);
            return 1f - p;
        }
        return 0f;
    }

    /** The crest peak edge alpha for a tier (0 for any tier that earns no vignette). */
    public static float peakForTier(int tier) {
        if (tier == TIER_LETHAL) {
            return LETHAL_PEAK;
        }
        if (tier == TIER_DANGER) {
            return DANGER_PEAK;
        }
        return 0f;
    }

    /**
     * Screen-EDGE alpha in {@code [0,1]} for the vignette at this moment.
     *
     * @param tier           the fired episodic tier (only {@link #TIER_DANGER}/{@link #TIER_LETHAL} draw)
     * @param elapsedMs      wall-clock ms since the pulse armed (negatives treated as "not yet risen")
     * @param lethalPersists player is still in the lethal zone -- only meaningful for {@link #TIER_LETHAL}
     * @param reduceMotion   accessibility: return a static faint level instead of the animated pulse
     */
    public static float edgeAlpha(int tier, long elapsedMs, boolean lethalPersists, boolean reduceMotion) {
        float peak = peakForTier(tier);
        if (peak <= 0f) {
            return 0f; // WARN_1/WARN_2 and any inactive tier: no atmosphere.
        }
        float hold = peak * HOLD_FRAC;
        float linger = (tier == TIER_LETHAL && lethalPersists) ? LETHAL_LINGER : 0f;

        // Reduce Motion: no pulse. A flat faint vignette for the window's life, then the lethal linger.
        if (reduceMotion) {
            if (elapsedMs < 0L || elapsedMs >= PULSE_END_MS) {
                return linger;
            }
            return Math.max(hold, linger);
        }

        // Before the pulse arms, only a persisting lethal whisper shows (never for danger).
        if (elapsedMs < 0L) {
            return linger;
        }
        // Rise: 0 -> crest peak.
        if (elapsedMs < RISE_MS) {
            float p = (float) elapsedMs / (float) RISE_MS;
            return Math.max(linger, peak * p);
        }
        // Settle: crest peak -> faint hold.
        long settleEnd = RISE_MS + SETTLE_MS;
        if (elapsedMs < settleEnd) {
            float p = (float) (elapsedMs - RISE_MS) / (float) SETTLE_MS;
            return Math.max(linger, peak + (hold - peak) * p);
        }
        // Hold faintly while the warning text is on screen.
        if (elapsedMs < HOLD_END_MS) {
            return Math.max(linger, hold);
        }
        // Melt: faint hold -> linger (lethal) or 0, aligned with the text's fade.
        if (elapsedMs < PULSE_END_MS) {
            float p = (float) (elapsedMs - HOLD_END_MS) / (float) MELT_MS;
            float v = hold + (linger - hold) * p;
            return Math.max(linger, v);
        }
        // After the pulse: only the lethal whisper remains (0 otherwise).
        return linger;
    }
}
