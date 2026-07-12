package com.example.globe.core;

/**
 * Pure timing/threshold + direction state machine for the E/W (prime-meridian edge) storm WARNING BANNER,
 * split out of the client overlay ({@code GlobeWarningOverlay}) so it is unit-testable without Minecraft
 * (Core Logic layer, per {@code docs/porting/PORTABILITY_ARCHITECTURE.md}). Everything keys off the player's
 * block distance to the nearest E/W border edge.
 *
 * <h2>TEST 85 feedback (Peetsa) rewrite</h2>
 * The old banner was two independent concerns bolted on the overlay: a fixed distance gate that split
 * LEVEL_1/LEVEL_2, and a fade envelope that only the mild tier got. Peetsa's TEST 85 report retired both
 * shapes:
 * <ol>
 *   <li><b>Both tiers now fade.</b> LEVEL_2 used to PERSIST the whole time ("just kind of annoying"). It now
 *       gets the SAME wall-clock fade-in / hold / fade-out envelope LEVEL_1 has ({@link #bannerAlpha}); after
 *       fade-out a tier stays gone while the player lingers, until a legitimate re-trigger.</li>
 *   <li><b>Direction-aware triggers.</b> A tier fires ONLY when reached by APPROACHING the border (entered
 *       from the milder side). Walking OUTWARD from the severe zone back through the mild zone re-showed the
 *       mild warning ("doesn't make sense" -- a retreat is not an approach). {@link #evaluate} tracks the
 *       previous geometry tier and fires a fresh episode only on an UPWARD transition; a retreat fires
 *       nothing.</li>
 *   <li><b>Thin-window skip.</b> On a small world the LEVEL_1 onset can sit only a few blocks outside the
 *       LEVEL_2 gate (his Itty-Bitty world: onset ~208, gate 175 -> a 33-block window), so the severe tier
 *       "popped only a few blocks after the mild one and stole its turn." When that window is too thin to
 *       read ({@link #THIN_WINDOW_MIN_BLOCKS}) LEVEL_1 is skipped entirely and only the clean LEVEL_2 shows
 *       ({@link #thinWindow} / {@link #geometryTier}).</li>
 *   <li><b>Early-start cap.</b> The LEVEL_1 onset is PROGRESS-based (KEEP-SHARED constant
 *       {@code LatitudeMath.POLAR_STAGE_1_PROGRESS}, computed by the caller -- NOT moved here), so on large
 *       worlds it fires absurdly far out (600-800+ blocks). {@link #VISIBILITY_CAP_BLOCKS} is a fixed-distance
 *       cap: no banner of any tier shows beyond it. On his small world the cap is a no-op (onset 208 < cap);
 *       on large worlds it kills the too-early start.</li>
 * </ol>
 *
 * <h2>Wall-clock, never ticks</h2>
 * Every fade is driven by ms since the episode armed ({@code System.currentTimeMillis} at the call site), per
 * the title/warning-family law: fades never key off game ticks (a paused/stalled tick clock or a teleport
 * tick jump must not rubber-band the fade).
 *
 * <h2>Threshold nesting</h2>
 * All thresholds are absolute blocks and nest cleanly inside the passage's own fixed geometry:
 * {@code HemispherePassage.PROMPT_AT} (100) &lt; {@link #DANGER_MAX_DIST_BLOCKS} (175) &lt;
 * {@link #VISIBILITY_CAP_BLOCKS} (== {@code HemispherePassage.REARM_AT}, 250) &lt;
 * {@code HemispherePassage.FOG_START} (500). The cap is aligned EXACTLY to {@code REARM_AT} so there is ONE
 * "edge-zone radius" a maintainer has to reason about, not two almost-matching magic numbers.
 */
public final class EwBannerEnvelope {

    private EwBannerEnvelope() {
    }

    // ---- banner tiers (plain ints so the client maps them onto its own EwStormStage without a new enum) ----

    /** No banner. */
    public static final int TIER_NONE = 0;
    /** Mild tier -- "heavy storms... rough but passable" (approach). */
    public static final int TIER_LEVEL_1 = 1;
    /** Severe tier -- whiteout / sandstorm at the world's edge (right before the crossing). */
    public static final int TIER_LEVEL_2 = 2;

    // ---- distance thresholds (all fixed blocks) ----

    /**
     * Distance-to-edge (blocks) at/inside which the top-severity LEVEL_2 tier applies. 175 leaves a fixed
     * ~75-block lead before {@code HemispherePassage.PROMPT_AT} (100) on EVERY world size (both are fixed
     * block distances), so the severe callout precedes the crossing prompt by the same margin everywhere.
     */
    public static final double DANGER_MAX_DIST_BLOCKS = 175.0;

    /**
     * Outer visibility cap (blocks): NO EW banner of ANY tier shows farther than this from the edge. Aligned
     * EXACTLY to {@link HemispherePassage#REARM_AT} (250) -- one shared "edge-zone radius" rather than a
     * second almost-matching constant. The banner (a display concern reading distance-to-edge) and the passage
     * arm state (an arming concern reading the same distance) stay logically independent; they simply AGREE on
     * where "the edge zone" begins. Nests: PROMPT_AT (100) &lt; DANGER_MAX (175) &lt; CAP (250) &lt; FOG (500).
     *
     * <p>On Peetsa's Itty-Bitty world the LEVEL_1 onset (~208) is INSIDE this cap, so the cap is a no-op there;
     * on large worlds (onset 600-800+) it truncates the LEVEL_1 band to a sane [{@link #DANGER_MAX_DIST_BLOCKS},
     * {@code CAP}] window so the banner no longer starts absurdly far out.
     */
    public static final double VISIBILITY_CAP_BLOCKS = HemispherePassage.REARM_AT; // 250

    /**
     * Minimum readable width (blocks) of the LEVEL_1 band (onset distance minus the LEVEL_2 gate). Below this
     * the mild tier and the severe tier would overlap so tightly that LEVEL_2 "steals the turn" of a LEVEL_1
     * the player barely registers, so LEVEL_1 is skipped entirely and only the clean LEVEL_2 shows. 60 comfortably
     * catches Peetsa's Itty-Bitty world (onset 208 - gate 175 = 33 &lt; 60) while leaving LEVEL_1 intact on any
     * world whose onset sits a readable distance outside the gate.
     */
    public static final double THIN_WINDOW_MIN_BLOCKS = 60.0;

    // ---- fade envelope (wall-clock ms; IDENTICAL for both tiers) ----

    /** Full-opacity hold, in ms, before a banner episode begins fading out (matches the polar ~10 s hold). */
    public static final long HOLD_MS = 10_000L;

    /** Fade-in and fade-out ramp length, in ms (matches the polar ladder's ~1 s alpha ramp). Total episode =
     *  {@link #FADE_MS} in + {@link #HOLD_MS} hold + {@link #FADE_MS} out = 12 s, the same for LEVEL_1 and LEVEL_2. */
    public static final long FADE_MS = 1_000L;

    /** True iff the player is close enough to the E/W edge for the top-severity (LEVEL_2) tier. */
    public static boolean isDanger(double distToEdgeBlocks) {
        return distToEdgeBlocks <= DANGER_MAX_DIST_BLOCKS;
    }

    /**
     * True iff the LEVEL_1 window (onset distance down to the LEVEL_2 gate) is too thin to read, so LEVEL_1 is
     * skipped and only LEVEL_2 shows. NaN-safe: an unknown onset is treated as thin (suppress the ambiguous
     * mild tier). Defensive: an onset at or inside the LEVEL_2 gate (never seen on supported world sizes, where
     * the smallest onset ~208 &gt; 175) is also "thin" so LEVEL_1 can never invert below LEVEL_2.
     */
    public static boolean thinWindow(double onsetDist) {
        if (Double.isNaN(onsetDist)) {
            return true;
        }
        return (onsetDist - DANGER_MAX_DIST_BLOCKS) < THIN_WINDOW_MIN_BLOCKS;
    }

    /**
     * Pure geometry tier for a distance-to-edge: which banner tier the player's POSITION alone puts them in,
     * before any direction/fade logic. LEVEL_2 inside {@link #DANGER_MAX_DIST_BLOCKS}; LEVEL_1 in the readable
     * band ({@code DANGER_MAX}, {@code min(onset, }{@link #VISIBILITY_CAP_BLOCKS}{@code )}] unless the window is
     * {@link #thinWindow thin}; otherwise NONE (too far / capped / thin-skipped). NaN distance -&gt; NONE.
     *
     * @param distToEdge blocks to the nearest E/W edge ({@code >= 0}).
     * @param onsetDist   the progress-based LEVEL_1 onset distance for THIS world (caller computes it from the
     *                    KEEP-SHARED {@code POLAR_STAGE_1_PROGRESS}; NOT moved into this pure class).
     */
    public static int geometryTier(double distToEdge, double onsetDist) {
        if (Double.isNaN(distToEdge)) {
            return TIER_NONE;
        }
        if (isDanger(distToEdge)) {
            return TIER_LEVEL_2; // <= 175 is always well inside the cap, so LEVEL_2 is never capped away
        }
        if (thinWindow(onsetDist)) {
            return TIER_NONE; // window too thin to read -> skip LEVEL_1, only LEVEL_2 will fire nearer in
        }
        double outer = Math.min(onsetDist, VISIBILITY_CAP_BLOCKS);
        return distToEdge <= outer ? TIER_LEVEL_1 : TIER_NONE;
    }

    /**
     * Banner-episode alpha in {@code [0,1]} as a function of ms since the episode armed: fade IN over
     * {@link #FADE_MS}, hold FULL through {@link #HOLD_MS}, fade OUT over the following {@link #FADE_MS}, then 0
     * (gone). Identical for both tiers. Defensive at the edges: a non-positive age is 0 (nothing yet), an age
     * past the tail is 0 (fully gone), so a bad/huge delta never yields an out-of-range alpha.
     */
    public static float bannerAlpha(long ageMs) {
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

    /** True once a banner episode has fully faded out (age at/past {@link #HOLD_MS} + {@link #FADE_MS}). */
    public static boolean bannerExpired(long ageMs) {
        return ageMs >= HOLD_MS + FADE_MS;
    }

    /** @deprecated tier-agnostic alias retained for the existing envelope tests; the fade now serves BOTH tiers. */
    @Deprecated
    public static float level1Alpha(long ageMs) {
        return bannerAlpha(ageMs);
    }

    /** @deprecated tier-agnostic alias retained for the existing envelope tests; the fade now serves BOTH tiers. */
    @Deprecated
    public static boolean level1Expired(long ageMs) {
        return bannerExpired(ageMs);
    }

    /**
     * Persisted banner state between ticks (the client holds ONE of these). {@code prevTier} = the geometry
     * tier last tick (for direction detection); {@code episodeTier} = the tier of the currently-armed fade
     * episode (0 = none active); {@code episodeArmMs} = wall-clock ms the episode armed.
     */
    public record State(int prevTier, int episodeTier, long episodeArmMs) {
        /** No banner, no episode -- the world-entry / left-the-storm reset. */
        public static final State INITIAL = new State(TIER_NONE, TIER_NONE, Long.MIN_VALUE);
    }

    /**
     * Per-tick banner decision + the next {@link State} to persist.
     *
     * @param shownTier  the tier to DRAW this tick (0 = nothing): the active episode's tier while its fade is
     *                   alive, else 0.
     * @param alpha      the episode's fade alpha in {@code [0,1]} for {@code shownTier} (0 when nothing shows).
     * @param firedTier  the tier that FRESHLY armed this tick (0 = none) -- an upward, approach-from-the-milder-
     *                   side transition. Exposed for direction assertions/logging; a retreat is always 0.
     * @param next       the state to persist for the next tick.
     */
    public record Decision(int shownTier, float alpha, int firedTier, State next) {
    }

    /**
     * Pure per-tick banner transition: fold the geometry tier, the direction-aware trigger, and the wall-clock
     * fade into ONE testable function. A fresh episode arms ONLY on an UPWARD geometry transition (the player
     * APPROACHED into a higher tier from the milder side); a retreat (lower tier) or lingering (same tier) arms
     * nothing, so the mild tier is never re-shown on a walk-out, and a faded-out tier stays gone until a genuine
     * re-approach re-arms it. Both tiers share {@link #bannerAlpha}.
     *
     * @param prev        previous persisted {@link State}.
     * @param distToEdge  blocks to the nearest E/W edge ({@code >= 0}; NaN treated as "far" -&gt; NONE).
     * @param onsetDist   the progress-based LEVEL_1 onset distance for this world (see {@link #geometryTier}).
     * @param nowMs       wall-clock ms ({@code System.currentTimeMillis} at the call site).
     */
    public static Decision evaluate(State prev, double distToEdge, double onsetDist, long nowMs) {
        int geo = geometryTier(distToEdge, onsetDist);
        int episodeTier = prev.episodeTier();
        long armMs = prev.episodeArmMs();
        int firedTier = TIER_NONE;

        // Direction-aware trigger: fire ONLY when the geometry tier rose above where we were last tick, i.e.
        // the player crossed a threshold heading INWARD (from the milder side). geo <= prevTier (retreat or
        // linger) never arms a new episode.
        if (geo > prev.prevTier()) {
            episodeTier = geo;
            armMs = nowMs;
            firedTier = geo;
        }

        // Resolve the active episode's fade. Once it has fully faded out it is consumed (episodeTier -> 0) and
        // stays gone until a fresh upward trigger re-arms it -- so BOTH tiers "fade out and stay gone while
        // lingering," matching Peetsa's TEST 85 "both warnings should fade out."
        int shownTier = TIER_NONE;
        float alpha = 0.0f;
        if (episodeTier > TIER_NONE) {
            long age = nowMs - armMs;
            if (bannerExpired(age)) {
                episodeTier = TIER_NONE;
                armMs = Long.MIN_VALUE;
            } else {
                shownTier = episodeTier;
                alpha = bannerAlpha(age);
            }
        }

        return new Decision(shownTier, alpha, firedTier, new State(geo, episodeTier, armMs));
    }
}
