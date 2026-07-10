package com.example.globe.core;

/**
 * Phase 5 Slice B-4 (item 3) -- pure decision logic for the EPISODIC polar warning ladder. Zero
 * Minecraft imports (Core Logic layer, unit-testable in a plain JVM). Callers pass absolute latitude in
 * DEGREES ({@code |lat|} in {@code [0,90]}) plus the highest tier already fired this episode, and read
 * back which tier (if any) to fire NOW and the next episode state to persist.
 *
 * <p><b>Episode model</b> (Peetsa's round-2 design): each of the four polar warning tiers fires its
 * message ONCE when first crossed, then the caller displays it ~10 s and fades it out -- it must NOT
 * re-show while the player stays poleward. Going DEEPER fires each next tier once. The whole ladder
 * re-arms only when the player fully RETREATS below {@link #RETREAT_REARM_DEG} (a single shared
 * threshold, not per-tier partial retreats) -- so wandering 89 -> 85 -> 89 never repeats a message, but
 * dropping to 83 and climbing back does. Tracking a single monotonic "highest tier fired this episode"
 * captures exactly this: a tier fires only when the current tier index EXCEEDS the highest already fired,
 * and the counter resets to 0 only on a sub-{@code RETREAT_REARM_DEG} sample.
 *
 * <p>Tier thresholds mirror {@code LatitudeMath.POLAR_STAGE_*} (85 / 87 / 89 / 89.7 deg) so the episodic
 * text ladder lines up exactly with the hazard/ambient mechanics it narrates. Symmetric about the equator
 * because the caller feeds {@code |lat|}.
 */
public final class PolarWarningEpisode {

    private PolarWarningEpisode() {
    }

    /** Below this absolute latitude the whole ladder re-arms (the player has left the hazard region). */
    public static final double RETREAT_REARM_DEG = 84.0;

    /** Tier onset latitudes (deg), index 0 -> tier 1 ... index 3 -> tier 4. Match POLAR_STAGE_*_PROGRESS. */
    public static final double TIER_1_DEG = 85.0;   // snow onset
    public static final double TIER_2_DEG = 87.0;   // slowness onset
    public static final double TIER_3_DEG = 89.0;   // danger (leads freeze death at 90)
    public static final double TIER_4_DEG = 89.7;   // lethal (freeze near-max)

    private static final double[] TIER_DEG = {TIER_1_DEG, TIER_2_DEG, TIER_3_DEG, TIER_4_DEG};

    /** Highest tier (1..4) whose onset {@code absLatDeg} is at/above, or 0 below the first tier. */
    public static int tierForLat(double absLatDeg) {
        int tier = 0;
        for (int i = 0; i < TIER_DEG.length; i++) {
            if (absLatDeg >= TIER_DEG[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    /**
     * Pure episode transition. Given the highest tier already fired this episode, returns which tier (if
     * any) to fire on this sample plus the next {@code highestFired} to persist.
     *
     * @param absLatDeg     current absolute latitude in degrees
     * @param highestFired  highest tier (1..4) fired since the last retreat, or 0 if none
     */
    public static Step evaluate(double absLatDeg, int highestFired) {
        // Full retreat out of the hazard region re-arms the whole ladder; never fires on the way out.
        if (absLatDeg < RETREAT_REARM_DEG) {
            return new Step(0, 0);
        }
        int tier = tierForLat(absLatDeg);
        // Fire only when we have reached a DEEPER tier than anything announced this episode. A teleport that
        // skips several tiers fires just the deepest (most severe) once -- exactly one message per new depth.
        if (tier > highestFired) {
            return new Step(tier, tier);
        }
        return new Step(0, highestFired);
    }

    /** Outcome of {@link #evaluate}: the tier to fire now (1..4, or 0 = none) and the next episode state. */
    public record Step(int fireTier, int nextHighestFired) {
    }
}
