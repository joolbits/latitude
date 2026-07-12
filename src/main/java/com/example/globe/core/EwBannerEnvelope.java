package com.example.globe.core;

/**
 * Pure timing/threshold math for the E/W (prime-meridian edge) storm WARNING BANNER, split out of the
 * client overlay ({@code GlobeWarningOverlay}) so it is unit-testable without Minecraft (Core Logic layer,
 * per {@code docs/porting/PORTABILITY_ARCHITECTURE.md}). Two independent concerns, both keyed off the
 * player's block distance to the nearest E/W border:
 *
 * <h2>1. Top-severity distance gate ({@link #isDanger})</h2>
 * The banner's ONSET (NONE&nbsp;-&gt;&nbsp;LEVEL_1) stays PROGRESS-based in the overlay
 * ({@code GlobeClientState.computeEwStormStage}), so the mild "storms approaching" line still appears in
 * lock-step with the storm particles it shares that threshold with -- unchanged. Only the
 * LEVEL_1&nbsp;&lt;-&gt;&nbsp;LEVEL_2 split is decided here, by a FIXED block distance
 * ({@link #DANGER_MAX_DIST_BLOCKS}).
 *
 * <p><b>Why fixed distance for the top tier.</b> The crossing prompt opens at a fixed
 * {@code HemispherePassage.PROMPT_AT} = 100 blocks, world-size-independent. The old progress-based LEVEL_2
 * upgrade fired at {@code (1 - 0.9667) * xRadius} blocks from the edge -- only ~125 blocks (a mere ~25-block
 * lead before the prompt) on the smallest world (xRadius 3750), yet ~666 blocks -- OUTSIDE the 500-block
 * approach fog -- on the largest (xRadius 20000). Pinning the top tier to a fixed distance gives the same,
 * legible lead before the prompt on every world, matching the rest of the passage geometry (PROMPT_AT 100,
 * REARM_AT 250, FOG_START 500), which is all fixed-distance too. On every supported world size the
 * progress ONSET (&ge; ~208 blocks out) still fires before this gate (175), so LEVEL_1 always shows first;
 * there is never a LEVEL_2 banner without a preceding LEVEL_1.
 *
 * <h2>2. LEVEL_1 fade envelope ({@link #level1Alpha} / {@link #level1Expired})</h2>
 * The mildest tier is EPISODIC: it fades in, holds, then fades OUT and stays gone while the player lingers
 * in the LEVEL_1 band -- Peetsa (TEST 84) asked that the mild "heavy storms... rough but passable" line
 * "fade instead of continue to be visible." The overlay re-arms a fresh episode only when the player LEAVES
 * the LEVEL_1 band (to NONE or up to LEVEL_2) and re-enters, in the same spirit as the polar warning
 * ladder's re-arm-on-re-entry. LEVEL_2 (the urgent tier right before the crossing) is NOT episodic -- it
 * stays persistent; only the mild tier fades. Timings mirror the polar ladder (~10&nbsp;s hold, ~1&nbsp;s
 * ramp). Wall-clock driven (ms since arm) per the title/warning-family law: fades never key off game ticks
 * (a paused/stalled tick clock or a teleport tick jump must not rubber-band the fade).
 */
public final class EwBannerEnvelope {

    private EwBannerEnvelope() {
    }

    /**
     * Distance-to-edge (blocks) at/inside which the top-severity LEVEL_2 banner shows. 175 leaves a
     * ~75-block lead before {@code HemispherePassage.PROMPT_AT} (100) on EVERY world size (both are fixed
     * block distances). Chosen to sit cleanly inside the passage's own fixed geometry: &gt; PROMPT_AT (100)
     * so the severe callout precedes the prompt, and &lt; REARM_AT (250) &lt; FOG_START (500) so it lands
     * well within the approach fog and the re-arm band rather than out in clear air.
     */
    public static final double DANGER_MAX_DIST_BLOCKS = 175.0;

    /** Full-opacity hold, in ms, before the LEVEL_1 banner begins fading out (matches the polar ~10 s hold). */
    public static final long HOLD_MS = 10_000L;

    /** Fade-in and fade-out ramp length, in ms (matches the polar ladder's ~1 s alpha ramp). */
    public static final long FADE_MS = 1_000L;

    /** True iff the player is close enough to the E/W edge for the top-severity (LEVEL_2) banner. */
    public static boolean isDanger(double distToEdgeBlocks) {
        return distToEdgeBlocks <= DANGER_MAX_DIST_BLOCKS;
    }

    /**
     * LEVEL_1 episode alpha in {@code [0,1]} as a function of ms since the episode armed: fade IN over
     * {@link #FADE_MS}, hold FULL through {@link #HOLD_MS}, fade OUT over the following {@link #FADE_MS},
     * then 0 (gone). Defensive at the edges: a non-positive age is 0 (nothing yet), an age past the tail
     * is 0 (fully gone), so a bad/huge delta never yields an out-of-range alpha.
     */
    public static float level1Alpha(long ageMs) {
        if (ageMs <= 0L) {
            return 0.0f;
        }
        if (ageMs < FADE_MS) {
            return (float) ageMs / (float) FADE_MS;                       // fade in
        }
        if (ageMs < HOLD_MS) {
            return 1.0f;                                                  // hold
        }
        if (ageMs < HOLD_MS + FADE_MS) {
            return 1.0f - (float) (ageMs - HOLD_MS) / (float) FADE_MS;    // fade out
        }
        return 0.0f;                                                      // gone
    }

    /**
     * True once the LEVEL_1 episode has fully faded out (age at/past {@link #HOLD_MS} + {@link #FADE_MS}).
     * While expired the banner stays hidden; it only reappears when the player leaves the LEVEL_1 band and
     * re-enters, which arms a fresh episode.
     */
    public static boolean level1Expired(long ageMs) {
        return ageMs >= HOLD_MS + FADE_MS;
    }
}
