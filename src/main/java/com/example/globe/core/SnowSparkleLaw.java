package com.example.globe.core;

/**
 * Phase 5 Slice S13(a) -> S21(b) GLINT v4 -- SNOW SPARKLE: the pure BUDGET LAW for the calm-cold snowfield
 * glints. Pure Java, ZERO Minecraft imports (Core Logic layer, plain-JVM unit-testable) -- same discipline as
 * {@link ParticleDensity} / {@link PolarExposure}. The tiny white twinkles on the snow are the calm cold's
 * JEWELRY: they exist in the gentle approach band BELOW the frostbite/blizzard tiers, and they hand OFF to the
 * ambient snowfall as the snow builds -- "the storm's absence made visible" (design S12(2)/S13(a)/S21(b)).
 *
 * <p><b>The GLINT v4 CROSSFADE (design S21(b), owner TEST 111).</b> Earlier rounds ran the glint band as a
 * trapezoid over 80-85 deg (the ambient-snow band) and gated its upper edge off the literal flakes/tick snow
 * budget, which let the glint and the falling snow visibly COEXIST -- "two competing effects" on the same
 * snowfield. v4 moves the glint DOWNWARD, into the band just BELOW the snow onset, and cross-fades it against the
 * snow so the two particle systems never both stand at full:
 * <ul>
 *   <li>the glint RISES from {@link #ONSET_DEG} (75), full by {@link #FULL_DEG} (76) -- {@link #bandRamp01};</li>
 *   <li>the ambient snowfall fades IN across {@link #SNOWFALL_ONSET_DEG} (80, the real snow onset) to
 *       {@link #SNOWFALL_FULL_DEG} (82) -- {@link #snowfallRamp01};</li>
 *   <li>the glint fades OUT on exactly that same 80->82 span as the snow rises, because the weight is the
 *       CROSSFADE {@link #glintWeight}{@code  = bandRamp01 x (1 - snowfallRamp01)}. At 80 the glint is still
 *       full and the snow is only just onset; by 82 the glint is gone and the snow has taken over. The visual
 *       story: veg thins from 72, glints begin 75, snowfall starts 80, the Barrens claim the land at 82.</li>
 * </ul>
 * This REPLACES the old {@code bandIntensity01} trapezoid + {@code snowfallWindow01(snowCount)} flakes/tick gate
 * with the single latitude-driven crossfade -- one clean handoff, no coexistence.
 *
 * <p><b>The calm gate (design "gated on the deep-blizzard tier being ~0").</b> The budget is additionally
 * multiplied by {@code (1 - stormOrBlizzard01)} ({@link #calmFactor01}), so any storm/blizzard signal snuffs the
 * sparkle. Across the 75-82 glint band the blizzard drive ({@link PolarHazardWindow#blizzardDrive}, onset 87) is
 * already 0, so this is belt-and-suspenders today -- but it keeps the law self-documenting and robust if the
 * band constants ever move: sparkle is, by construction, the calm-weather jewelry.
 *
 * <p><b>No state / no accumulator.</b> {@link #sparkleBudget} is a pure function of its arguments -- a FIXED
 * per-spawn-tick budget the caller then scales by the vanilla Particles setting / enclosure / reduce-snow
 * comfort option exactly like the ambient snow, so the B-3b anti-backlog law and the {@code isPaused} guard
 * are untouched. The budget counts GLINT CLUSTERS; the caller spawns a twin-particle micro-cluster per unit
 * (design S21(b)(iii)), which is a spawn-loop detail this pure law never sees.
 */
public final class SnowSparkleLaw {

    private SnowSparkleLaw() {
    }

    /** Absolute latitude (deg) where the glint begins to RISE. GLINT v4 (S21b) dropped this from the 80-deg
     *  snow onset to 75 -- the glint now lives in the bare-to-lightly-dusted band just BELOW the snowfall, and
     *  hands off to the falling snow above 80. Sits just above the vegetation fade-start (72), so the visual
     *  order reads veg-thin (72) -> glints (75) -> snowfall (80). */
    public static final double ONSET_DEG = 75.0;
    /** Absolute latitude (deg) at/above which the glint is at full band strength (the rise is complete). The
     *  band then HOLDS at full until the snowfall crossfade takes it down from 80 -- there is no upper band
     *  fade of its own; {@link #snowfallRamp01} owns the upper edge. */
    public static final double FULL_DEG = 76.0;

    /** Absolute latitude (deg) where the ambient snowfall begins and the glint STARTS to cross-fade out --
     *  pinned to the real snow onset ({@link PolarHazardWindow#AMBIENT_ONSET_DEG}, 80). At/below this the glint
     *  is unshuttered (snowfall term = 1). */
    public static final double SNOWFALL_ONSET_DEG = PolarHazardWindow.AMBIENT_ONSET_DEG;
    /** Absolute latitude (deg) at/above which the ambient snowfall has fully faded in and the glint is GONE --
     *  the top of the 80->82 crossfade. Above this the snow owns the snowfield; the jewelry has handed off.
     *  82 is also the Polar Barrens onset (now an independent constant, see
     *  {@link LatitudeV2Flags#POLAR_BARRENS_ONSET_DEG}) -- the glint dies exactly where the barren cap begins. */
    public static final double SNOWFALL_FULL_DEG = 82.0;

    /** Default peak sparkle budget per spawn-tick BEFORE the caller's Particles/enclosure/reduce-snow scaling.
     *  History: 1 -> 3 (S17(c)(i), TEST 107 "more sparkle overall") -> 4 (S19b, TEST 109 "density up a notch").
     *  Each budget unit is a twin-particle CLUSTER (S21b(iii)), so at the shared every-4th-tick cadence
     *  (~5 spawn-ticks/s) full band + open sky + all-particles lands as ~20 clusters/s = ~40 glint particles/s,
     *  still tapering with the band ramp, the snowfall crossfade, and every perf/enclosure/reduce-snow scale --
     *  a livelier shimmer on the snow, not a particle storm. One-line P4 dial (raise for denser). */
    public static final int DEFAULT_PEAK_BUDGET = 4;

    /**
     * The GLINT band ramp 0..1: 0 at/below {@link #ONSET_DEG} (75), smoothstep up to 1 at {@link #FULL_DEG}
     * (76), then held at 1 above (the upper edge is owned by the snowfall crossfade, not this ramp). Symmetric
     * about the equator (caller passes {@code |lat|}). NaN-safe (0, the conservative side on bad data).
     */
    public static double bandRamp01(double absLatDeg) {
        if (Double.isNaN(absLatDeg) || absLatDeg <= ONSET_DEG) {
            return 0.0;
        }
        if (absLatDeg >= FULL_DEG) {
            return 1.0;
        }
        return smoothstep01((absLatDeg - ONSET_DEG) / (FULL_DEG - ONSET_DEG));
    }

    /**
     * The ambient-snowfall fade-in 0..1: 0 at/below {@link #SNOWFALL_ONSET_DEG} (80, the real snow onset),
     * smoothstep up to 1 at {@link #SNOWFALL_FULL_DEG} (82), and 1 above. This is the snowfall side of the
     * crossfade: as it rises 0->1 the glint's {@code (1 - snowfallRamp01)} term falls 1->0, so the falling snow
     * takes over exactly as the glint hands off. NaN-safe (1 -> glint fully shuttered, the conservative side).
     */
    public static double snowfallRamp01(double absLatDeg) {
        if (Double.isNaN(absLatDeg)) {
            return 1.0; // bad data -> treat as full snow -> no glint (conservative)
        }
        if (absLatDeg <= SNOWFALL_ONSET_DEG) {
            return 0.0;
        }
        if (absLatDeg >= SNOWFALL_FULL_DEG) {
            return 1.0;
        }
        return smoothstep01((absLatDeg - SNOWFALL_ONSET_DEG) / (SNOWFALL_FULL_DEG - SNOWFALL_ONSET_DEG));
    }

    /**
     * The GLINT v4 crossfade weight 0..1: {@code bandRamp01(|lat|) x (1 - snowfallRamp01(|lat|))}. Zero below
     * the glint onset (75), rising to full by 76, HELD across the calm 76-80 band, then cross-fading back to 0
     * across 80->82 as the ambient snowfall fades in -- so the glint and the falling snow never both stand at
     * full (the structural fix for the "two competing effects" the owner saw). Pure function of latitude; the
     * caller multiplies by {@link #calmFactor01} and the peak budget. NaN-safe (0).
     */
    public static double glintWeight(double absLatDeg) {
        return bandRamp01(absLatDeg) * (1.0 - snowfallRamp01(absLatDeg));
    }

    /** The calm factor 0..1: {@code 1 - clamp(stormOrBlizzard01)} -- any storm/blizzard signal snuffs the
     *  sparkle. A NaN signal reads as fully stormy (calm 0 -> no sparkle, the conservative side). */
    public static double calmFactor01(double stormOrBlizzard01) {
        double s = Double.isNaN(stormOrBlizzard01) ? 1.0 : clamp01(stormOrBlizzard01);
        return 1.0 - s;
    }

    /**
     * The FIXED per-spawn-tick sparkle budget (in glint CLUSTERS):
     * {@code round(glintWeight x calmFactor01 x peakBudget)}, clamped {@code >= 0}. Zero outside the calm glint
     * band, in any storm, once the ambient snowfall has fully faded in (>= 82 deg), or with a non-positive peak.
     * A pure function of its arguments -- no state / no accumulator; the caller scales this by the live Particles
     * setting, the enclosure estimate and the reduce-snow comfort option before spawning (and spawns a twin
     * micro-cluster per unit, S21b(iii)), exactly like the ambient snow.
     *
     * @param absLatDeg          current absolute latitude (deg)
     * @param stormOrBlizzard01  the storm/blizzard signal 0..1 (e.g. {@link PolarHazardWindow#blizzardDrive})
     * @param peakBudget         the peak per-spawn-tick cluster budget at full calm-band strength (see
     *                           {@link #DEFAULT_PEAK_BUDGET})
     */
    public static int sparkleBudget(double absLatDeg, double stormOrBlizzard01, int peakBudget) {
        if (peakBudget <= 0) {
            return 0;
        }
        double v = glintWeight(absLatDeg) * calmFactor01(stormOrBlizzard01) * peakBudget;
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
