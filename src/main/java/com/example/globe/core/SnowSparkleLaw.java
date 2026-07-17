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
     *  Small on purpose: at the shared every-4th-tick spawn cadence (~5 spawn-ticks/s) a peak of 1 is ~a-few
     *  glints/second at full band strength + open sky + all-particles, tapering with the band ramp and every
     *  perf/enclosure/reduce-snow scale -- subtle jewelry, not a particle storm. One-line P4 dial. */
    public static final int DEFAULT_PEAK_BUDGET = 1;

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
     * The FIXED per-spawn-tick sparkle budget: {@code round(bandIntensity01 x calmFactor01 x peakBudget)},
     * clamped {@code >= 0}. Zero outside the calm band, in any storm, or with a non-positive peak. A pure
     * function of its arguments (no state / no accumulator) -- the caller scales this by the live Particles
     * setting, the enclosure estimate and the reduce-snow comfort option before spawning, exactly like the
     * ambient snow.
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
        double v = bandIntensity01(absLatDeg) * calmFactor01(stormOrBlizzard01) * peakBudget;
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
