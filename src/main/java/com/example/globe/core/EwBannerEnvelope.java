package com.example.globe.core;

/**
 * Pure timing/threshold + direction state machine for the E/W (antimeridian edge) storm WARNING BANNER,
 * split out of the client overlay ({@code GlobeWarningOverlay}) so it is unit-testable without Minecraft
 * (Core Logic layer, per {@code docs/porting/PORTABILITY_ARCHITECTURE.md}). Everything keys off the player's
 * block distance to the nearest E/W border edge.
 *
 * <h2>Single white advisory (TEST 89 owner decision)</h2>
 * The banner USED to be a two-tier system (a mild LEVEL_1 line escalating to a severe/yellow LEVEL_2
 * whiteout/sandstorm line). Peetsa retired the second tier: the edge now shows ONE white advisory on entering
 * the band, and nothing else. So this machine is single-tier -- it arms one episode when the player APPROACHES
 * into the band and lets it play its wall-clock fade envelope; there is no severe boundary, no thin-window
 * skip, no per-tier text. The severe-tier degree anchor ({@code SEVERE_DEG}) is gone from {@link EdgeGeometry}
 * with it.
 *
 * <h2>The one boundary: the band cap (the advisory anchor, the OUTERMOST edge element)</h2>
 * The advisory's only boundary is the PER-WORLD {@code capDist} resolved from longitude degrees against the
 * mod's intended X radius ({@link EdgeGeometry#resolve}) and passed IN. Edge-flow rework (2026-07-13): the cap
 * is now {@code advisoryDist} (~176 deg), the OUTERMOST edge element -- so the advisory arms 0.5 deg BEFORE the
 * fog onset ({@code rampStartDist}, ~177.5 deg) rather than sharing it. Peetsa's confirmed flow wants the
 * advisory as a genuine heads-up that leads the fog, not a callout that "lands almost simultaneously" with it.
 * No banner shows farther out than {@code capDist}, and it is where the advisory arms on approach. Because it
 * scales with the world, the callout leads the crossing prompt by the same DEGREE spacing on every size instead
 * of a fixed block margin that read absurdly early on wide worlds.
 *
 * <h2>Preserved behaviours (TEST 85 feedback, still honoured)</h2>
 * <ol>
 *   <li><b>It fades.</b> The advisory gets a wall-clock fade-in / hold / fade-out envelope ({@link #bannerAlpha})
 *       and does not persist while the player lingers in the band.</li>
 *   <li><b>Direction-aware, approach-only.</b> The advisory fires ONLY when the band is ENTERED by approaching
 *       the edge (an upward geometry transition); a retreat re-shows nothing.</li>
 *   <li><b>Re-arm on leave + re-enter.</b> Once faded out it stays gone until the player leaves the band
 *       (geometry back to NONE) and approaches again.</li>
 * </ol>
 *
 * <h2>Wall-clock, never ticks</h2>
 * Every fade is driven by ms since the episode armed ({@code System.currentTimeMillis} at the call site), per
 * the title/warning-family law.
 */
public final class EwBannerEnvelope {

    private EwBannerEnvelope() {
    }

    // ---- banner tiers (plain ints so the client maps them onto its own draw path without a new enum) ----

    /** No banner. */
    public static final int TIER_NONE = 0;
    /** The single white advisory -- "Approaching the Prime Meridian..." (approach into the band). */
    public static final int TIER_ADVISORY = 1;

    // ---- fade envelope (wall-clock ms) ----

    /** Full-opacity hold, in ms, before a banner episode begins fading out (matches the polar ~10 s hold). */
    public static final long HOLD_MS = 10_000L;

    /** Fade-in and fade-out ramp length, in ms (matches the polar ladder's ~1 s alpha ramp). Total episode =
     *  {@link #FADE_MS} in + {@link #HOLD_MS} hold + {@link #FADE_MS} out = 12 s. */
    public static final long FADE_MS = 1_000L;

    /**
     * Pure geometry tier for a distance-to-edge: {@link #TIER_ADVISORY} inside the band cap
     * ({@code distToEdge <= capDist}), otherwise {@link #TIER_NONE}. NaN -&gt; NONE (a bad distance read never
     * draws a banner).
     *
     * @param distToEdge blocks to the nearest E/W edge ({@code >= 0}).
     * @param capDist    the resolved band cap -- the advisory anchor ({@code EdgeGeometry.Resolved.advisoryDist},
     *                   ~176 deg, the outermost edge element).
     */
    public static int geometryTier(double distToEdge, double capDist) {
        if (Double.isNaN(distToEdge) || Double.isNaN(capDist)) {
            return TIER_NONE;
        }
        return distToEdge <= capDist ? TIER_ADVISORY : TIER_NONE;
    }

    /**
     * Banner-episode alpha in {@code [0,1]} as a function of ms since the episode armed: fade IN over
     * {@link #FADE_MS}, hold FULL through {@link #HOLD_MS}, fade OUT over the following {@link #FADE_MS}, then 0.
     * Defensive at the edges: a non-positive age is 0, an age past the tail is 0.
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
     * @param firedTier  the tier that FRESHLY armed this tick (0 = none) -- an upward, approach-into-the-band
     *                   transition. A retreat is always 0.
     * @param next       the state to persist for the next tick.
     */
    public record Decision(int shownTier, float alpha, int firedTier, State next) {
    }

    /**
     * Pure per-tick banner transition: fold the geometry tier, the direction-aware trigger, and the wall-clock
     * fade into ONE testable function. A fresh episode arms ONLY on an UPWARD geometry transition (the player
     * APPROACHED into the band from outside); a retreat or lingering arms nothing.
     *
     * @param prev        previous persisted {@link State}.
     * @param distToEdge  blocks to the nearest E/W edge ({@code >= 0}; NaN treated as "far" -&gt; NONE).
     * @param capDist     the resolved band cap / fog onset for this world.
     * @param nowMs       wall-clock ms ({@code System.currentTimeMillis} at the call site).
     */
    public static Decision evaluate(State prev, double distToEdge, double capDist, long nowMs) {
        int geo = geometryTier(distToEdge, capDist);
        int episodeTier = prev.episodeTier();
        long armMs = prev.episodeArmMs();
        int firedTier = TIER_NONE;

        // Direction-aware trigger: fire ONLY when the geometry tier rose above where we were last tick (the
        // player crossed into the band heading INWARD). geo <= prevTier (retreat or linger) never arms.
        if (geo > prev.prevTier()) {
            episodeTier = geo;
            armMs = nowMs;
            firedTier = geo;
        }

        // Resolve the active episode's fade. Once fully faded out it is consumed (episodeTier -> 0) and stays
        // gone until a fresh upward trigger re-arms it -- it "fades out and stays gone while lingering."
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
