package com.example.globe.core;

/**
 * Pure timing/threshold + direction state machine for the E/W (antimeridian edge) storm WARNING BANNER,
 * split out of the client overlay ({@code GlobeWarningOverlay}) so it is unit-testable without Minecraft
 * (Core Logic layer, per {@code docs/porting/PORTABILITY_ARCHITECTURE.md}). Everything keys off the player's
 * block distance to the nearest E/W border edge.
 *
 * <h2>Degree-anchored, intended-radius geometry (redesign 2026-07-12)</h2>
 * The banner's two tier boundaries are NO LONGER fixed blocks. They are the PER-WORLD distances resolved from
 * longitude degrees against the mod's intended X radius ({@link EdgeGeometry#resolve}) and passed IN:
 * <ul>
 *   <li>{@code severeDist} == {@code EdgeGeometry.Resolved.severeDist} (~178 deg): at/inside it the top
 *       LEVEL_2 (whiteout / sandstorm) tier applies.</li>
 *   <li>{@code capDist} == {@code EdgeGeometry.Resolved.rampStartDist} (~176.5 deg): the banner's outer
 *       visibility cap AND the LEVEL_1 (mild) onset -- the fog, the storm particles, and the banner all begin
 *       HERE, one shared onset. No banner of any tier shows farther out than this.</li>
 * </ul>
 * The LEVEL_1 (mild) band is therefore {@code (severeDist, capDist]} and LEVEL_2 is {@code [0, severeDist]}.
 * Because both boundaries scale with the world, the callout leads the crossing prompt by the same DEGREE
 * spacing on every size instead of a fixed block margin that read absurdly early on wide worlds.
 *
 * <h2>TEST 85 feedback (Peetsa) behaviours -- all preserved</h2>
 * <ol>
 *   <li><b>Both tiers fade.</b> LEVEL_2 no longer persists; it gets the SAME wall-clock fade-in / hold /
 *       fade-out envelope LEVEL_1 has ({@link #bannerAlpha}).</li>
 *   <li><b>Direction-aware triggers.</b> A tier fires ONLY when reached by APPROACHING the edge (an upward
 *       geometry transition); a retreat re-shows nothing.</li>
 *   <li><b>Thin-window skip.</b> If the LEVEL_1 band ({@code capDist - severeDist}) is too thin to read
 *       ({@link #THIN_WINDOW_MIN_BLOCKS}) LEVEL_1 is skipped and only the clean LEVEL_2 shows. With the
 *       degree geometry this band is comfortably wide on every supported size (Itty ~112 blocks), so the skip
 *       is now purely defensive.</li>
 * </ol>
 *
 * <h2>Wall-clock, never ticks</h2>
 * Every fade is driven by ms since the episode armed ({@code System.currentTimeMillis} at the call site), per
 * the title/warning-family law.
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

    /**
     * Minimum readable width (blocks) of the LEVEL_1 band ({@code capDist - severeDist}). Below this the mild
     * tier and the severe tier would overlap so tightly that LEVEL_2 "steals the turn" of a LEVEL_1 the player
     * barely registers, so LEVEL_1 is skipped entirely and only the clean LEVEL_2 shows. With the degree
     * geometry the band is ~112 blocks even on Itty-Bitty, so this only ever guards a pathologically tiny world.
     */
    public static final double THIN_WINDOW_MIN_BLOCKS = 60.0;

    // ---- fade envelope (wall-clock ms; IDENTICAL for both tiers) ----

    /** Full-opacity hold, in ms, before a banner episode begins fading out (matches the polar ~10 s hold). */
    public static final long HOLD_MS = 10_000L;

    /** Fade-in and fade-out ramp length, in ms (matches the polar ladder's ~1 s alpha ramp). Total episode =
     *  {@link #FADE_MS} in + {@link #HOLD_MS} hold + {@link #FADE_MS} out = 12 s, the same for LEVEL_1 and LEVEL_2. */
    public static final long FADE_MS = 1_000L;

    /** True iff the player is close enough to the E/W edge for the top-severity (LEVEL_2) tier. */
    public static boolean isDanger(double distToEdgeBlocks, double severeDist) {
        return distToEdgeBlocks <= severeDist;
    }

    /**
     * True iff the LEVEL_1 window ({@code capDist} down to {@code severeDist}) is too thin to read, so LEVEL_1
     * is skipped and only LEVEL_2 shows. NaN-safe: an unknown boundary is treated as thin. Defensive: a cap at
     * or inside the severe gate (never seen on supported world sizes) is also "thin" so LEVEL_1 can never
     * invert below LEVEL_2.
     */
    public static boolean thinWindow(double severeDist, double capDist) {
        if (Double.isNaN(severeDist) || Double.isNaN(capDist)) {
            return true;
        }
        return (capDist - severeDist) < THIN_WINDOW_MIN_BLOCKS;
    }

    /**
     * Pure geometry tier for a distance-to-edge: which banner tier the player's POSITION alone puts them in,
     * before any direction/fade logic. LEVEL_2 inside {@code severeDist}; LEVEL_1 in the readable band
     * {@code (severeDist, capDist]} unless the window is {@link #thinWindow thin}; otherwise NONE. NaN -> NONE.
     *
     * @param distToEdge blocks to the nearest E/W edge ({@code >= 0}).
     * @param severeDist the resolved severe-tier boundary ({@code EdgeGeometry.Resolved.severeDist}).
     * @param capDist    the resolved outer cap / LEVEL_1 onset ({@code EdgeGeometry.Resolved.rampStartDist}).
     */
    public static int geometryTier(double distToEdge, double severeDist, double capDist) {
        if (Double.isNaN(distToEdge)) {
            return TIER_NONE;
        }
        if (isDanger(distToEdge, severeDist)) {
            return TIER_LEVEL_2;
        }
        if (thinWindow(severeDist, capDist)) {
            return TIER_NONE; // window too thin to read -> skip LEVEL_1, only LEVEL_2 will fire nearer in
        }
        return distToEdge <= capDist ? TIER_LEVEL_1 : TIER_NONE;
    }

    /**
     * Banner-episode alpha in {@code [0,1]} as a function of ms since the episode armed: fade IN over
     * {@link #FADE_MS}, hold FULL through {@link #HOLD_MS}, fade OUT over the following {@link #FADE_MS}, then 0.
     * Identical for both tiers. Defensive at the edges: a non-positive age is 0, an age past the tail is 0.
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

    /** @deprecated tier-agnostic alias retained for the existing envelope tests; the fade serves BOTH tiers. */
    @Deprecated
    public static float level1Alpha(long ageMs) {
        return bannerAlpha(ageMs);
    }

    /** @deprecated tier-agnostic alias retained for the existing envelope tests; the fade serves BOTH tiers. */
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
     *                   side transition. A retreat is always 0.
     * @param next       the state to persist for the next tick.
     */
    public record Decision(int shownTier, float alpha, int firedTier, State next) {
    }

    /**
     * Pure per-tick banner transition: fold the geometry tier, the direction-aware trigger, and the wall-clock
     * fade into ONE testable function. A fresh episode arms ONLY on an UPWARD geometry transition (the player
     * APPROACHED into a higher tier from the milder side); a retreat or lingering arms nothing.
     *
     * @param prev        previous persisted {@link State}.
     * @param distToEdge  blocks to the nearest E/W edge ({@code >= 0}; NaN treated as "far" -&gt; NONE).
     * @param severeDist  the resolved severe-tier boundary for this world.
     * @param capDist     the resolved outer cap / LEVEL_1 onset for this world.
     * @param nowMs       wall-clock ms ({@code System.currentTimeMillis} at the call site).
     */
    public static Decision evaluate(State prev, double distToEdge, double severeDist, double capDist, long nowMs) {
        int geo = geometryTier(distToEdge, severeDist, capDist);
        int episodeTier = prev.episodeTier();
        long armMs = prev.episodeArmMs();
        int firedTier = TIER_NONE;

        // Direction-aware trigger: fire ONLY when the geometry tier rose above where we were last tick (the
        // player crossed a threshold heading INWARD). geo <= prevTier (retreat or linger) never arms.
        if (geo > prev.prevTier()) {
            episodeTier = geo;
            armMs = nowMs;
            firedTier = geo;
        }

        // Resolve the active episode's fade. Once fully faded out it is consumed (episodeTier -> 0) and stays
        // gone until a fresh upward trigger re-arms it -- BOTH tiers "fade out and stay gone while lingering."
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
