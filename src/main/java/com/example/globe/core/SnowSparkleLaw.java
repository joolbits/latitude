package com.example.globe.core;

/**
 * Phase 5 Slice S13(a) -- SNOW SPARKLE: the pure BUDGET LAW for the calm-cold snowfield glints. Pure Java,
 * ZERO Minecraft imports (Core Logic layer, plain-JVM unit-testable) -- same discipline as
 * {@link ParticleDensity} / {@link PolarExposure}. The tiny white twinkles on the snow are the calm cold's
 * JEWELRY: they exist in the gentle approach band (80-85 deg) BELOW the frostbite/blizzard tiers, and are
 * gated OFF the moment the storm builds -- "the storm's absence made visible" (design S12(2)/S13(a)).
 *
 * <p><b>The band (design "80-85").</b> A trapezoid over absolute latitude: fades in at
 * {@link #ONSET_DEG} (the ambient snow onset -- snow is on the ground from here), full across the calm
 * mid-band, fades back out approaching {@link #END_DEG} (the frostbite-DAMAGE onset,
 * {@link PolarHazardWindow#FROSTBITE_ONSET_DEG} -- where the cold begins to bite and the calm is over).
 * Zero outside {@code [ONSET_DEG, END_DEG]}.
 *
 * <p><b>The calm gate (design "gated on the deep-blizzard tier being ~0").</b> The budget is additionally
 * multiplied by {@code (1 - stormOrBlizzard01)}, so any storm/blizzard signal snuffs the sparkle. In the
 * 80-85 band the blizzard drive ({@link PolarHazardWindow#blizzardDrive}, onset 87) is already 0, so this is
 * belt-and-suspenders today -- but it keeps the law self-documenting and robust if the band constants ever
 * move: sparkle is, by construction, the calm-weather jewelry.
 *
 * <p><b>S17(c)(iii) the SNOWFALL WINDOW (owner, TEST 107: "only during very light to light snow").</b> The
 * calm gate above only cuts at BLIZZARD (blizzardDrive, onset 87) -- useless across the 80-85 band where it is
 * always 0. The real signal the owner asked for is the ambient SNOWFALL itself: sparkle is the glint of light
 * snow catching the low sun, so it must be ON only while snow is falling GENTLY, and OFF both when there is no
 * snow at all AND once the snow builds to medium or heavier. {@link #snowfallWindow01} expresses that as a
 * trapezoid over the literal ambient snow budget ({@link PolarHazardWindow#snowCount} flakes/tick): 0 below
 * {@link #SNOWFALL_MIN} (no snow -> no glint), full through the very-light-to-light range
 * ({@code <= }{@link #SNOWFALL_LIGHT_MAX}), fading out to 0 by {@link #SNOWFALL_MEDIUM} (medium+). It multiplies
 * into {@link #sparkleBudget}, so the sparkle now lives ONLY in the light-snow shoulder near the ambient onset
 * (~80-83.4 deg on today's snow curve), not across the whole latitude band -- the band trapezoid still shapes
 * the fade-IN at the onset, but the snowfall window governs the upper cutoff.
 *
 * <p><b>No state / no accumulator.</b> {@link #sparkleBudget} is a pure function of its arguments -- a FIXED
 * per-spawn-tick budget the caller then scales by the vanilla Particles setting / enclosure / reduce-snow
 * comfort option exactly like the ambient snow, so the B-3b anti-backlog law and the {@code isPaused} guard
 * are untouched.
 */
public final class SnowSparkleLaw {

    private SnowSparkleLaw() {
    }

    /** Absolute latitude (deg) where the sparkle fades in -- the ambient snow onset
     *  ({@link PolarHazardWindow#AMBIENT_ONSET_DEG}, 80): glints appear once snow is on the ground. */
    public static final double ONSET_DEG = PolarHazardWindow.AMBIENT_ONSET_DEG;
    /** Absolute latitude (deg) at/above which the sparkle is at full band-strength (the calm mid-band start). */
    public static final double FULL_DEG = 81.5;
    /** Absolute latitude (deg) where the sparkle begins to fade back out (the calm band's shoulder). */
    public static final double FADE_DEG = 84.0;
    /** Absolute latitude (deg) at/above which the sparkle is gone -- the frostbite-DAMAGE onset
     *  ({@link PolarHazardWindow#FROSTBITE_ONSET_DEG}, 85): the cold bites, the calm is over, no more jewelry. */
    public static final double END_DEG = PolarHazardWindow.FROSTBITE_ONSET_DEG;

    /** Default peak sparkle budget per spawn-tick BEFORE the caller's Particles/enclosure/reduce-snow scaling.
     *  S17(c)(i) DENSITY UP (owner, TEST 107: "more sparkle overall"): 1 -> 3 (~3x), so at the shared
     *  every-4th-tick cadence (~5 spawn-ticks/s) full band + open sky + all-particles lands as ~15 glints/s
     *  instead of ~5, still tapering with the band ramp, the snowfall window and every perf/enclosure/reduce-snow
     *  scale -- a livelier shimmer on the snow, not a particle storm. One-line P4 dial (raise for denser). */
    public static final int DEFAULT_PEAK_BUDGET = 3;

    // ---- S17(c)(iii): the SNOWFALL WINDOW -- sparkle only during very-light-to-light ambient snow ----
    // The window is expressed in the LITERAL ambient snow budget (PolarHazardWindow.snowCount, flakes/tick):
    // SNOW_MIN_COUNT (2) at the 80-deg onset ramping to SNOW_MAX_COUNT (60) at the pole. "Light" is the gentle
    // shoulder just past the onset; "medium+" is where the snow starts to read as a real fall. P4 dials.
    /** Minimum ambient snowfall (flakes/tick) for ANY sparkle. At/below this there is effectively no snow, so no
     *  glint -- this is what makes the sparkle vanish below the 80-deg snow onset (snowCount 0) as well as any
     *  future calm-with-no-snow case. 1.0 = "at least one flake is falling". */
    public static final double SNOWFALL_MIN = 1.0;
    /** Ambient snowfall (flakes/tick) at/below which snow is still "very light to light" and the sparkle is at
     *  FULL window strength. 14 flakes/tick ~ 82 deg on today's curve -- the gentle glittering shoulder. */
    public static final double SNOWFALL_LIGHT_MAX = 14.0;
    /** Ambient snowfall (flakes/tick) at/above which snow reads as "medium or heavier" and the sparkle is fully
     *  OFF. 22 flakes/tick ~ 83.4 deg -- the snow is now a fall, not a glitter, so the jewelry is gone. */
    public static final double SNOWFALL_MEDIUM = 22.0;

    /**
     * The snowfall window 0..1: a trapezoid over the ambient snow budget ({@link PolarHazardWindow#snowCount},
     * flakes/tick). 0 below {@link #SNOWFALL_MIN} (no snow -> no glint), 1 through the very-light-to-light range
     * ({@code <= }{@link #SNOWFALL_LIGHT_MAX}), a linear fade to 0 as the snow builds to {@link #SNOWFALL_MEDIUM}
     * (medium+), and 0 at/above it. NaN-safe (0). This is the S17(c)(iii) gate the owner asked for: sparkle only
     * while snow is falling GENTLY.
     */
    public static double snowfallWindow01(double snowCount) {
        if (Double.isNaN(snowCount) || snowCount < SNOWFALL_MIN) {
            return 0.0; // no snow at all -> no glint
        }
        if (snowCount <= SNOWFALL_LIGHT_MAX) {
            return 1.0; // very light to light -> full sparkle
        }
        if (snowCount >= SNOWFALL_MEDIUM) {
            return 0.0; // medium or heavier -> no sparkle
        }
        return (SNOWFALL_MEDIUM - snowCount) / (SNOWFALL_MEDIUM - SNOWFALL_LIGHT_MAX);
    }

    /**
     * Band intensity 0..1 across the calm snow band -- a trapezoid: 0 at/below {@link #ONSET_DEG}, smoothstep up
     * to 1 at {@link #FULL_DEG}, held to {@link #FADE_DEG}, smoothstep back down to 0 at {@link #END_DEG}, and 0
     * at/above {@link #END_DEG}. Symmetric about the equator (caller passes {@code |lat|}). NaN-safe (0).
     */
    public static double bandIntensity01(double absLatDeg) {
        if (Double.isNaN(absLatDeg) || absLatDeg <= ONSET_DEG || absLatDeg >= END_DEG) {
            return 0.0;
        }
        if (absLatDeg < FULL_DEG) {
            return smoothstep01((absLatDeg - ONSET_DEG) / (FULL_DEG - ONSET_DEG));
        }
        if (absLatDeg <= FADE_DEG) {
            return 1.0;
        }
        return smoothstep01((END_DEG - absLatDeg) / (END_DEG - FADE_DEG));
    }

    /** The calm factor 0..1: {@code 1 - clamp(stormOrBlizzard01)} -- any storm/blizzard signal snuffs the
     *  sparkle. A NaN signal reads as fully stormy (calm 0 -> no sparkle, the conservative side). */
    public static double calmFactor01(double stormOrBlizzard01) {
        double s = Double.isNaN(stormOrBlizzard01) ? 1.0 : clamp01(stormOrBlizzard01);
        return 1.0 - s;
    }

    /**
     * The FIXED per-spawn-tick sparkle budget:
     * {@code round(bandIntensity01 x calmFactor01 x snowfallWindow01 x peakBudget)}, clamped {@code >= 0}. Zero
     * outside the calm band, in any storm, outside the very-light-to-light SNOWFALL window
     * ({@link #snowfallWindow01}, S17(c)(iii)), or with a non-positive peak. A pure function of its arguments
     * (the snowfall level is read from the same {@link PolarHazardWindow#snowCount} ambient curve the falling
     * snow uses, so the two can never disagree) -- no state / no accumulator; the caller scales this by the live
     * Particles setting, the enclosure estimate and the reduce-snow comfort option before spawning, exactly like
     * the ambient snow.
     *
     * @param absLatDeg          current absolute latitude (deg)
     * @param stormOrBlizzard01  the storm/blizzard signal 0..1 (e.g. {@link PolarHazardWindow#blizzardDrive})
     * @param peakBudget         the peak per-spawn-tick budget at full calm-band strength (see
     *                           {@link #DEFAULT_PEAK_BUDGET})
     */
    public static int sparkleBudget(double absLatDeg, double stormOrBlizzard01, int peakBudget) {
        if (peakBudget <= 0) {
            return 0;
        }
        double snowfall = PolarHazardWindow.snowCount(absLatDeg); // the literal ambient snowfall at this latitude
        double v = bandIntensity01(absLatDeg) * calmFactor01(stormOrBlizzard01)
                * snowfallWindow01(snowfall) * peakBudget;
        long n = Math.round(v);
        return n < 0L ? 0 : (int) n;
    }

    private static double smoothstep01(double t) {
        double x = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
