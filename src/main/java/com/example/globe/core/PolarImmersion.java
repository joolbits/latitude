package com.example.globe.core;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- Peetsa stipulation S7: POLAR IMMERSION ("polar water is three degrees
 * colder than the air"). Pure Java, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM).
 * The MC-coupled part is ONE boolean read -- {@code player.isInWater()} -- in {@code GlobeMod.borderUxTick};
 * every decision lives here.
 *
 * <p><b>The rule (one line).</b> Physical water at Y=0 or higher in polar country evaluates the EXISTING
 * {@link PolarHazardWindow} curves at {@code effectiveLat = min(90, |lat| + 3)} -- and the S4 shelter pause
 * does NOT apply (immersion OVERRIDES shelter: water conducts cold; walls do not help in the sea). Physical
 * water below Y=0 is a deep-cave pool: depth itself pauses Latitude cold damage and direct water chill,
 * whether or not a sky shaft happens to reach the pool. Consequences: swimming the open 82-85 liquid sea
 * (below the 85 freeze line -- the main surface water in polar country) bites like 85 land (frostbite onset),
 * while an underground reward pool is not secretly a lethal ocean even when terrain leaves a skylight.
 *
 * <p><b>What is deliberately UNCHANGED:</b>
 * <ul>
 *   <li>{@link ColdProtection} multiplies the (shifted) amount exactly as everywhere -- full leather is a
 *       drysuit (zero damage), the one-evaluator law.</li>
 *   <li>The F3 frost cue is active while the (shifted) bite is active -- cue and damage read the SAME
 *       effective latitude, so they cannot drift.</li>
 *   <li>The S5 post-crossing grace STILL suppresses (grace &gt; immersion -- the ceremony window is sacred;
 *       see {@link #coldDamagePaused}).</li>
 *   <li>The S6 heal-lock logic is untouched (it keys on the RAW-latitude zone + the raw shelter read;
 *       immersion is not shelter).</li>
 *   <li>Creative/spectator stay exempt via the existing gates.</li>
 * </ul>
 *
 * <p><b>Boat exemption for FREE:</b> vanilla {@code isInWater()} is {@code false} for a player in a boat, so
 * crossing polar seas by boat is safe -- story-true, zero code. This class takes the boolean; the shim reads
 * {@code isInWater()} and nothing else.
 *
 * <p><b>Tunable:</b> {@link #IMMERSION_SEVERITY_DEG} (+3) is the one number Peetsa may want to feel live
 * (flagged in the flight brief).
 */
public final class PolarImmersion {

    private PolarImmersion() {
    }

    /** How many degrees colder polar water is than the air at the same latitude (S7: +3), applied to the
     *  cold-curve EVALUATION latitude while immersed. The one live-tunable of this stipulation. */
    public static final double IMMERSION_SEVERITY_DEG = 3.0;

    /** The pole: effective latitude never exceeds it (the curves clamp there anyway; capping here keeps the
     *  contract explicit and testable). */
    public static final double MAX_LAT_DEG = 90.0;

    /**
     * Byte-compatible sea-level overload. The latitude the cold curves are EVALUATED at: {@code |lat|} on
     * land / in a boat, {@code min(90, |lat| + 3)} while immersed. NaN propagates (the curves' own NaN guards
     * treat it as "no hazard").
     */
    public static double effectiveLatDeg(double absLatDeg, boolean inWater) {
        return effectiveLatDeg(absLatDeg, inWater, 0);
    }

    /**
     * True only for actual water contact below the deep-cave boundary. The Y coordinate is the insulation
     * law: shelter/sky visibility is deliberately irrelevant.
     */
    public static boolean isDeepCaveInsulatedWater(boolean inWater, int blockY) {
        return inWater && blockY < 0;
    }

    /**
     * The latitude the cold curves are EVALUATED at, including the deep-cave water exception. Land, boats,
     * and physical water below Y=0 use raw {@code |lat|}; Y=0 and higher physical water retains the existing
     * {@code min(90, |lat| + 3)} sea rule.
     */
    public static double effectiveLatDeg(double absLatDeg, boolean inWater, int blockY) {
        if (!inWater || isDeepCaveInsulatedWater(inWater, blockY)) {
            return absLatDeg;
        }
        return Math.min(MAX_LAT_DEG, absLatDeg + IMMERSION_SEVERITY_DEG);
    }

    /**
     * Byte-compatible sea-level overload. The ONE cold-damage pause rule, S7-revised: paused iff the S5
     * post-crossing grace is open (grace beats everything -- the ceremony window), OR the player is genuinely
     * sheltered AND NOT immersed. Replaces the P1 {@code sheltered || grace} composition at the
     * {@code GlobeMod} chokepoint.
     */
    public static boolean coldDamagePaused(boolean sheltered, boolean inWater, boolean graceActive) {
        return coldDamagePaused(sheltered, inWater, 0, graceActive);
    }

    /**
     * Depth-aware cold-damage pause. Physical water below Y=0 pauses Latitude cold damage because cave depth
     * itself is the insulation rule; sky exposure does not defeat it. Y=0/higher water retains the existing
     * immersion-overrides-shelter rule, while dry shelter and crossing grace remain unchanged.
     */
    public static boolean coldDamagePaused(boolean sheltered, boolean inWater, int blockY, boolean graceActive) {
        if (graceActive) {
            return true;
        }
        if (isDeepCaveInsulatedWater(inWater, blockY)) {
            return true;
        }
        return sheltered && !inWater;
    }

    /**
     * The actual vanilla-freeze suppression decision after Globe's player/dimension/exemption gates have
     * passed. Deep-cave water is always suppressed so a lingering or externally raised frozen-tick counter
     * cannot leak vanilla's fixed freeze damage through the Latitude pause. Outside that refuge, the existing
     * lethal-band suppression law is unchanged.
     */
    public static boolean shouldSuppressVanillaFreezeDamage(
            boolean inWater,
            int blockY,
            double effectiveLatDeg
    ) {
        return isDeepCaveInsulatedWater(inWater, blockY)
                || effectiveLatDeg >= PolarHazardWindow.HAZARD_ONSET_DEG;
    }
}
