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
 * <p><b>The GLINT CLOCK (design S24, owner "Yes to the glint clock", TEST 115 round 2026-07-20).</b> The
 * snow/ice glint is promoted from decoration to the diegetic TIME-OF-DAY tell for the polar sky. During polar
 * night the landscape can look day-bright under a dark sky (and midnight sun is the mirror), so the player can
 * lose track of noon; the glints now PULSE with the sun's clock so their presence == daytime, their absence ==
 * clock-night. Two additions, both pure:
 * <ul>
 *   <li>a noon-peaked {@link #clockDayCurve01} over the 24000-tick day (peak 1 at noon 6000, a broad daytime
 *       plateau, symmetric dawn/dusk twilight ramps, 0 across clock-night ~13000-23000) multiplies
 *       {@link #glintWeight} EVERYWHERE -- all latitudes, all season states -- so the glint is a planet-wide
 *       DAYTIME phenomenon and the owner's "midnight glints" (his night flights) are gone; it also keeps the
 *       pulse consistent under midnight sun (his "keep it consistent" ask);</li>
 *   <li>a POLAR-NIGHT / MIDNIGHT-SUN band EXTENSION: when the caller reports that the observer's latitude is in
 *       an around-the-clock solar band ({@link SolarTilt.FunctionalBand#POLAR_NIGHT} /
 *       {@link SolarTilt.FunctionalBand#MIDNIGHT_SUN} -- the {@code functionalBandActive} flag), the glint's
 *       equatorward onset drops from {@link #ONSET_DEG} (75) to {@link #FUNCTIONAL_EXTENDED_ONSET_DEG} (60, the
 *       solar band onset at full tilt), same smoothstep width, so the signal exists everywhere the
 *       dark-sky/day-sky confusion does (60 up), not just in the 75+ deep cap. The 80-82 snowfall crossfade is
 *       UNCHANGED -- blizzard country keeps its blizzard.</li>
 * </ul>
 * The law stays MC-free: the client adapter gathers {@code functionalBandActive} (from the established
 * {@link SolarTilt#functionalBand} evaluator) and {@code dayTime} (the vanilla-synced overworld clock) and
 * hands them in as PURE inputs, so the whole clock is plain-JVM unit-testable.
 *
 * <p><b>No state / no accumulator.</b> {@link #sparkleBudget} is a pure function of its arguments -- a FIXED
 * per-spawn-tick budget the caller then scales by the vanilla Particles setting / enclosure / reduce-snow
 * comfort option exactly like the ambient snow, so the B-3b anti-backlog law and the {@code isPaused} guard
 * are untouched. The budget counts GLINT CLUSTERS; how many particles the caller spawns per unit is a
 * spawn-loop detail this pure law never sees (S21(b)(iii) spawned a twin per unit; GLINT v5 / TEST 113
 * retired the twin -- one bright FIREWORK spark per unit).
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

    /** The glint band's smoothstep WIDTH (deg): {@link #FULL_DEG} - {@link #ONSET_DEG} (= 1). Pulled out as a
     *  constant so the polar-night / midnight-sun onset EXTENSION ({@link #bandRamp01(double, boolean)}) can
     *  re-use the SAME rise width from the extended onset -- "same smoothstep width" (design S24). */
    public static final double BAND_WIDTH_DEG = FULL_DEG - ONSET_DEG;

    /** Absolute latitude (deg) the glint's equatorward onset EXTENDS down to when the observer is inside an
     *  around-the-clock solar band (polar night / midnight sun) -- the GLINT CLOCK band extension (design S24).
     *  60 is the {@link SolarTilt} band onset at full tilt ({@code 90 - |δ|}, δ_max 30 => 60) and the functional
     *  floor since TEST 114, so the glint-as-clock signal exists everywhere the dark-sky/day-sky confusion does
     *  (60 up), not only in the 75+ deep cap. Mirrored here (not imported) to keep the pure law self-describing,
     *  the same way {@link #SNOWFALL_ONSET_DEG} mirrors {@link PolarHazardWindow#AMBIENT_ONSET_DEG}. */
    public static final double FUNCTIONAL_EXTENDED_ONSET_DEG = 60.0;

    // ---- the GLINT CLOCK curve constants (design S24) ------------------------------------------------------

    /** Ticks in one Minecraft day -- the clock-day curve's period. Mirrors {@link SolarTilt#TICKS_PER_DAY}. */
    public static final long CLOCK_TICKS_PER_DAY = 24000L;
    /** MC {@code dayTime} at solar noon (sun highest) -- the peak of {@link #clockDayCurve01}. */
    public static final double CLOCK_NOON_TICK = 6000.0;
    /** Half-width (ticks from noon) of the broad DAYTIME PLATEAU where the clock curve holds at 1: |ticks-from-
     *  noon| at/below this => full day. 5000 => the plateau spans ticks ~1000..11000 (dawn-complete to
     *  dusk-onset), peaking at noon (6000). */
    public static final double CLOCK_DAY_FULL_HALFWIDTH_TICKS = 5000.0;
    /** Half-width (ticks from noon) at/beyond which the clock curve is 0 (clock-night): |ticks-from-noon| at/
     *  above this => no pulse. 7000 => night from ticks ~13000..23000 (inclusive of midnight 18000). The
     *  twilight ramp lives in the 5000..7000 gap (dawn 23000->1000, dusk 11000->13000, mirror images). */
    public static final double CLOCK_NIGHT_HALFWIDTH_TICKS = 7000.0;

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
     *  History: 1 -> 3 (S17(c)(i), TEST 107 "more sparkle overall") -> 4 (S19b, TEST 109 "density up a notch")
     *  -> 2 (GLINT v5, owner flight TEST 113, 2026-07-19). WHY the halving: the 3/4-era escalation was
     *  compensating for the DIM lilac WAX_OFF quad; v5 swapped the particle to the bright white FIREWORK spark
     *  and retired the twin cluster (caller's SPARKLE_TWIN_COUNT 2 -> 1), so per-spark brightness now carries
     *  the shimmer and the old density read as noise (TEST 113: "worst in a village", one oversized blob). At
     *  the shared every-4th-tick cadence (~5 spawn-ticks/s) peak 2 x single-spark clusters lands as ~10 glint
     *  particles/s at full band strength (vs the old ~40), still tapering with the band ramp, the snowfall
     *  crossfade, and every perf/enclosure/reduce-snow scale. One-line P4 dial (raise for denser). */
    public static final int DEFAULT_PEAK_BUDGET = 2;

    /**
     * The GLINT band ramp 0..1 with the NORMAL onset: 0 at/below {@link #ONSET_DEG} (75), smoothstep up to 1 at
     * {@link #FULL_DEG} (76), then held at 1 above (the upper edge is owned by the snowfall crossfade, not this
     * ramp). Symmetric about the equator (caller passes {@code |lat|}). NaN-safe (0). Equivalent to
     * {@link #bandRamp01(double, boolean) bandRamp01(absLatDeg, false)} -- the un-extended band.
     */
    public static double bandRamp01(double absLatDeg) {
        return bandRamp01(absLatDeg, false);
    }

    /**
     * The GLINT band ramp 0..1 with the GLINT CLOCK band extension (design S24). When
     * {@code functionalBandActive} is true (the observer is in a polar-night / midnight-sun around-the-clock
     * band, per {@link SolarTilt#functionalBand}), the equatorward onset drops from {@link #ONSET_DEG} (75) to
     * {@link #FUNCTIONAL_EXTENDED_ONSET_DEG} (60) using the SAME {@link #BAND_WIDTH_DEG} rise width, so the
     * clock signal exists everywhere the dark-sky/day-sky confusion does; otherwise it is the normal 75->76
     * ramp. The ramp is then held at 1 above the (extended or normal) full point -- the snowfall crossfade owns
     * the upper edge either way, UNCHANGED. Symmetric about the equator (caller passes {@code |lat|}). NaN-safe
     * (0, the conservative side on bad data).
     */
    public static double bandRamp01(double absLatDeg, boolean functionalBandActive) {
        double onset = functionalBandActive
                ? Math.min(ONSET_DEG, FUNCTIONAL_EXTENDED_ONSET_DEG)
                : ONSET_DEG;
        double full = onset + BAND_WIDTH_DEG;
        if (Double.isNaN(absLatDeg) || absLatDeg <= onset) {
            return 0.0;
        }
        if (absLatDeg >= full) {
            return 1.0;
        }
        return smoothstep01((absLatDeg - onset) / (full - onset));
    }

    /**
     * The GLINT CLOCK day curve 0..1 (design S24): a smooth, noon-peaked pulse over the 24000-tick MC day that
     * multiplies {@link #glintWeight} EVERYWHERE so the glint reads as a DAYTIME phenomenon (present == day,
     * absent == clock-night). Shape: peak 1 at solar noon ({@link #CLOCK_NOON_TICK} 6000) held across a broad
     * daytime plateau (|ticks-from-noon| &le; {@link #CLOCK_DAY_FULL_HALFWIDTH_TICKS}, ticks ~1000..11000),
     * 0 across clock-night (|ticks-from-noon| &ge; {@link #CLOCK_NIGHT_HALFWIDTH_TICKS}, ticks ~13000..23000 incl.
     * midnight), joined by mirror-image smoothstep TWILIGHT ramps (dawn 23000->1000, dusk 11000->13000). Uses the
     * {@link #smoothstep01} idiom (no RNG); symmetric about noon by construction (it keys off |ticks-from-noon|).
     *
     * <p>The argument is a tick count -- the caller may pass the RAW vanilla-synced overworld clock or a value
     * already reduced mod 24000; this folds it via {@code floorMod} so a negative {@code /time set} rewind still
     * yields a well-defined phase (same discipline as {@link SolarTilt#timeOfDayFrac}). NaN-safe: a bad clock
     * reads as clock-night (0 -> no pulse), the conservative side.
     */
    public static double clockDayCurve01(double dayTime) {
        if (Double.isNaN(dayTime)) {
            return 0.0; // bad clock -> treat as clock-night -> no pulse (conservative)
        }
        double m = floorMod(dayTime, (double) CLOCK_TICKS_PER_DAY);   // [0, 24000)
        double fromNoon = Math.abs(m - CLOCK_NOON_TICK);              // 0 at noon; up to 18000 at tick 24000
        double half = CLOCK_TICKS_PER_DAY / 2.0;                      // 12000
        if (fromNoon > half) {
            fromNoon = CLOCK_TICKS_PER_DAY - fromNoon;                // wrap: max distance is 12000 (midnight 18000)
        }
        if (fromNoon <= CLOCK_DAY_FULL_HALFWIDTH_TICKS) {
            return 1.0;                                               // daytime plateau -> peak 1 at noon
        }
        if (fromNoon >= CLOCK_NIGHT_HALFWIDTH_TICKS) {
            return 0.0;                                               // clock-night -> no pulse
        }
        // twilight: smoothstep DOWN 1->0 from the day-full edge (5000) to the night edge (7000); dawn & dusk mirror.
        double t = (fromNoon - CLOCK_DAY_FULL_HALFWIDTH_TICKS)
                / (CLOCK_NIGHT_HALFWIDTH_TICKS - CLOCK_DAY_FULL_HALFWIDTH_TICKS);
        return 1.0 - smoothstep01(t);
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
     * The GLINT v4 crossfade weight 0..1 with the GLINT CLOCK (design S24):
     * {@code bandRamp01(|lat|, functionalBandActive) x (1 - snowfallRamp01(|lat|)) x clockDayCurve01(dayTime)}.
     * The first two terms are the unchanged v4 crossfade -- rising from the (extended-or-normal) onset, HELD
     * across the calm band, then cross-fading back to 0 across 80->82 as the ambient snowfall fades in (never
     * both effects at full). The third term is the clock pulse that makes the glint a DAYTIME phenomenon
     * EVERYWHERE (peak at noon, 0 at clock-night). Pure function of its inputs; the caller multiplies by
     * {@link #calmFactor01} and the peak budget. NaN-safe (0).
     *
     * @param absLatDeg           current absolute latitude (deg)
     * @param functionalBandActive true iff the observer is in a polar-night / midnight-sun band (extends the
     *                             equatorward onset to {@link #FUNCTIONAL_EXTENDED_ONSET_DEG})
     * @param dayTime             the vanilla-synced overworld clock (ticks; folded mod 24000 by the curve)
     */
    public static double glintWeight(double absLatDeg, boolean functionalBandActive, double dayTime) {
        return bandRamp01(absLatDeg, functionalBandActive)
                * (1.0 - snowfallRamp01(absLatDeg))
                * clockDayCurve01(dayTime);
    }

    /** The calm factor 0..1: {@code 1 - clamp(stormOrBlizzard01)} -- any storm/blizzard signal snuffs the
     *  sparkle. A NaN signal reads as fully stormy (calm 0 -> no sparkle, the conservative side). */
    public static double calmFactor01(double stormOrBlizzard01) {
        double s = Double.isNaN(stormOrBlizzard01) ? 1.0 : clamp01(stormOrBlizzard01);
        return 1.0 - s;
    }

    /**
     * The FIXED per-spawn-tick sparkle budget (in glint CLUSTERS):
     * {@code round(glintWeight x calmFactor01 x peakBudget)}, clamped {@code >= 0}. Zero outside the (extended-or-
     * normal) calm glint band, in any storm, once the ambient snowfall has fully faded in (>= 82 deg), during
     * clock-night (the GLINT CLOCK curve is 0), or with a non-positive peak. A pure function of its arguments --
     * no state / no accumulator; the caller scales this by the live Particles setting, the enclosure estimate and
     * the reduce-snow comfort option before spawning (one spark per unit since GLINT v5 retired the S21b(iii)
     * twin), exactly like the ambient snow.
     *
     * @param absLatDeg           current absolute latitude (deg)
     * @param functionalBandActive true iff the observer is in a polar-night / midnight-sun band (extends the
     *                             equatorward onset to {@link #FUNCTIONAL_EXTENDED_ONSET_DEG})
     * @param dayTime             the vanilla-synced overworld clock (ticks; the GLINT CLOCK pulse, folded mod 24000)
     * @param stormOrBlizzard01   the storm/blizzard signal 0..1 (e.g. {@link PolarHazardWindow#blizzardDrive})
     * @param peakBudget          the peak per-spawn-tick cluster budget at full calm-band strength (see
     *                            {@link #DEFAULT_PEAK_BUDGET})
     */
    public static int sparkleBudget(double absLatDeg, boolean functionalBandActive, double dayTime,
                                    double stormOrBlizzard01, int peakBudget) {
        if (peakBudget <= 0) {
            return 0;
        }
        double v = glintWeight(absLatDeg, functionalBandActive, dayTime)
                * calmFactor01(stormOrBlizzard01) * peakBudget;
        long n = Math.round(v);
        return n < 0L ? 0 : (int) n;
    }

    private static double smoothstep01(double t) {
        double x = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return x * x * (3.0 - 2.0 * x);
    }

    /** Floating-point floorMod: the non-negative remainder of {@code a} by {@code m} (m &gt; 0). Keeps a
     *  negative {@code /time set} clock in {@code [0, m)}, matching {@link SolarTilt#timeOfDayFrac}'s discipline. */
    private static double floorMod(double a, double m) {
        double r = a % m;
        return r < 0.0 ? r + m : r;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
