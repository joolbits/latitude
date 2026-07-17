package com.example.globe.core;

/**
 * Solar Tilt + Seasons — the ONE astronomy kernel (Phase 5B-adjacent, P1 core).
 *
 * <p>Pure Java, ZERO Minecraft imports (Core Logic layer, plain-JVM unit-testable) — same discipline as
 * {@link PolarFogLaw}. Everything the feature needs to know about where the sun is lives here: the seasonal
 * declination {@code δ(day)}, the full solar-elevation projection (§4c of
 * {@code docs/binder/solar-tilt-design-20260716.md}), the midnight-sun / polar-night band classifier, the
 * render direction vector (for P2's pose rebuild — sweep amendment A6's option B), and the effective-sun
 * predicate the server mob rules consume. There is exactly ONE evaluator; the sky renderer (P2) and the two
 * server mob rules (P1 functional layer) both read it, so a world where the sky says "polar night" but the
 * mobs disagree is structurally impossible (§8 one-evaluator law).
 *
 * <h2>Conventions (§4a)</h2>
 * <ul>
 *   <li><b>Signed latitude φ</b> in degrees, NORTH POSITIVE. Callers derive it from
 *       {@code -LatitudeMath.degreesFromZ(border, z)} (negate because the project's north is −Z, and we want
 *       north = +φ). Equator φ = 0, north pole +90, south pole −90.</li>
 *   <li><b>Declination δ</b> in degrees — the sub-solar latitude (which latitude the noon sun is directly
 *       over). {@code δ > 0} ⇒ the NORTH hemisphere is in summer.</li>
 *   <li><b>Hour angle H</b> in radians, {@code 0} at local solar noon, {@code ±π} at local solar midnight.</li>
 *   <li><b>Render world frame</b> for {@link #solarDirection}: {@code +X = east, +Y = up, +Z = south}
 *       (MC + project convention).</li>
 * </ul>
 *
 * <h2>The spec table (§12, A1-corrected)</h2>
 * The design's §12 table shipped with two WRONG midnight cells that contradicted its own §4c formula; the
 * adversarial sweep (A1, BINDING) flagged them. Recomputed from §4c here and pinned by {@code SolarTiltTest}:
 * at φ=−75, δ=+30 midnight the elevation is <b>−45°</b> (not −75°), and at φ=−90 it is <b>−30°</b> (not
 * −90°). The deep reason is the pole invariant: at {@code |φ| = 90} the {@code cos φ} term vanishes, so
 * {@code sin(alt) = sin φ · sin δ} is INDEPENDENT of H — a pole's sun sits at a CONSTANT elevation
 * {@code = sign(φ)·δ} all "day". See {@link #solarElevationDeg}.
 *
 * <p><b>NaN-safe throughout.</b> Every public method tolerates NaN inputs without throwing: the scalar
 * evaluators return {@link Double#NaN}, the boolean predicates / {@link #functionalBand} return the inert
 * answer (false / {@link FunctionalBand#NONE}), and {@link #deltaDeg} degrades a NaN dial to a safe constant.
 */
public final class SolarTilt {

    private SolarTilt() {
    }

    // ---- shipped defaults (mirrored by the live-tunable LatitudeV2Flags dials; kept here so the pure kernel
    //      is self-describing and testable without touching the flag layer) --------------------------------

    /** Axial-tilt amplitude (deg). §11 "delta pick": 30 puts the midnight-sun / polar-night onset at a round,
     *  visible 60° (real Earth is 23.5° → onset 66.5°, deep in the storm-greyed cap). */
    public static final double DEFAULT_DELTA_MAX_DEG = 30.0;
    /** Game-year length (days). 360 ⇒ exactly 180 game-days between solstices (owner's schedule). */
    public static final double DEFAULT_YEAR_LENGTH_DAYS = 360.0;
    /** Frozen-mode phase (deg). 0 ⇒ δ = +DELTA_MAX ⇒ permanent NORTHERN summer (owner's original
     *  "one pole summer forever"). */
    public static final double DEFAULT_FROZEN_PHASE_DEG = 0.0;

    /** Ticks in one Minecraft day (the clock's period). */
    public static final long TICKS_PER_DAY = 24000L;
    /** MC {@code dayTime} 6000 (sun highest) as a fraction of the 24000-tick day: this is where H = 0 lands. */
    public static final double NOON_FRACTION = 0.25;

    /**
     * Elevation (deg) above which the sun counts as "up" for the functional layer. The design recommends the
     * solar-disc upper-limb value {@code −0.833°} (the sun's centre is that far below the true horizon when
     * its top edge touches it); {@code 0.0} is "also fine". We ship the disc-edge value so "sun up" matches
     * the visible first/last gleam. Used only by {@link #effectiveSunUp}; the geometric BAND onset
     * ({@link #onsetLatDeg}, {@link #isMidnightSun}, {@link #isPolarNight}) uses the true horizon (0°) so the
     * §12 onset identity {@code 90 − |δ|} stays exact.
     */
    public static final double SUN_UP_THRESHOLD_DEG = -0.833;

    /** Which 24-hour polar regime a latitude is in on a given day. */
    public enum FunctionalBand {
        /** Sun rises and sets normally today (or below the functional-min floor). */
        NONE,
        /** Winter side of a tilted pole: the sun never rises today (dark around the clock). */
        POLAR_NIGHT,
        /** Summer side of a tilted pole: the sun never sets today (light around the clock). */
        MIDNIGHT_SUN
    }

    // ---- seasons: δ(day) (§4e) -------------------------------------------------------------------------

    /**
     * Seasonal declination δ (deg) for a continuous day count.
     * <pre>
     *   δ(day) = deltaMaxDeg · cos( 2π · day / yearLengthDays )
     * </pre>
     * Convention: {@code cos(0) = 1} ⇒ <b>world-day 0 = northern summer solstice</b> (δ = +deltaMax ⇒ north
     * pole midnight sun, south pole polar night); day = yearLen/4 = autumn equinox (δ = 0); day = yearLen/2 =
     * northern winter solstice (δ = −deltaMax, poles swapped). Cosine (not sine) so the world opens on the
     * strongest, most legible state and the frozen default lands on full tilt.
     *
     * <p><b>Frozen mode:</b> {@code yearLengthDays <= 0} (or NaN) ⇒ δ is the CONSTANT
     * {@code deltaMaxDeg · cos(frozenPhaseDeg)}. With {@code frozenPhaseDeg = 0} this is {@code +deltaMax} —
     * the owner's original "permanent summer pole" world, recovered as one config value. A seed-random SIGN on
     * {@code frozenPhaseDeg} (0 vs 180) is the clean home for "which pole is summer this world".
     *
     * <p>NaN-safe: a NaN dial degrades to a safe constant rather than propagating NaN into the whole sky.
     */
    public static double deltaDeg(double dayCount, double deltaMaxDeg, double yearLengthDays,
                                  double frozenPhaseDeg) {
        double amp = Double.isNaN(deltaMaxDeg) ? 0.0 : deltaMaxDeg;
        if (Double.isNaN(yearLengthDays) || yearLengthDays <= 0.0) {
            double phaseRad = Math.toRadians(Double.isNaN(frozenPhaseDeg) ? 0.0 : frozenPhaseDeg);
            return amp * Math.cos(phaseRad);
        }
        double d = Double.isNaN(dayCount) ? 0.0 : dayCount;
        return amp * Math.cos(2.0 * Math.PI * d / yearLengthDays);
    }

    // ---- time shim: the vanilla clock → day / time-of-day / hour angle (pure math) ---------------------
    //
    // The MC-touching read (Level.getOverworldClockTime() — sweep-verified real, survived the 26.2 WorldClock
    // rework) happens in the server helper (GlobeMod); everything below is a pure function of the returned
    // long, so it is unit-testable and identical on client and server (§7 no-netcode: MC already replicates
    // the overworld day-time to every client every tick, so both sides derive the SAME δ(day) and H with zero
    // new packets).
    //
    // SEAMS (sweep A5, documented, accepted):
    //   • /time set <backward>  — rewinds the clock, which rewinds the SEASON with it (δ is a pure function of
    //     the clock). Expected, not a bug: seasons are welded to the one clock everything else already trusts.
    //   • doDaylightCycle=false — freezes the clock, which FREEZES the season (δ stops advancing). Also
    //     expected. Sleeping / time-skip advances the season with the clock (a night's sleep ≈ ½ a game-day).
    //   • Client/server agreement holds as long as the synced day-time jitter stays ≪ 1 s (A5): a sub-second
    //     skew moves δ by a negligible fraction of a degree, so the sky (client) and the mob rules (server)
    //     never visibly disagree.

    /** Continuous day count (may be fractional; drives {@link #deltaDeg} so δ drifts smoothly). */
    public static double dayCount(long overworldClockTime) {
        return overworldClockTime / (double) TICKS_PER_DAY;
    }

    /** Time-of-day as a fraction in {@code [0,1)} of the 24000-tick day. {@link Math#floorMod} so a negative
     *  clock (a {@code /time set} rewind past 0) still yields a well-defined phase rather than a negative one. */
    public static double timeOfDayFrac(long overworldClockTime) {
        long m = Math.floorMod(overworldClockTime, TICKS_PER_DAY);
        return m / (double) TICKS_PER_DAY;
    }

    /**
     * Hour angle H (radians) from a time-of-day fraction: {@code H = 2π · (frac − NOON_FRACTION)}, so
     * {@code frac = 0.25} (MC noon, dayTime 6000) ⇒ H = 0 and {@code frac = 0.75} (MC midnight, 18000) ⇒
     * H = π. This is the design's {@code 2π(frac − 0.5) + H_offset} with the noon-anchoring
     * {@code H_offset = +π/2} baked in. (The RENDER pose's exact offset is P2's live screenshot-calibration
     * gate; the FUNCTIONAL layer's day/night correctness only needs this clean noon anchor.) NaN-safe.
     */
    public static double hourAngleRadians(double timeOfDayFrac) {
        if (Double.isNaN(timeOfDayFrac)) {
            return 0.0;
        }
        return 2.0 * Math.PI * (timeOfDayFrac - NOON_FRACTION);
    }

    // ---- the elevation evaluator (§4c) — the one function everything reads ------------------------------

    /**
     * Solar elevation (deg above the horizon) for a signed latitude, declination and hour angle — the
     * standard equatorial→horizontal projection:
     * <pre>
     *   sin(alt) = sin φ · sin δ  +  cos φ · cos δ · cos H
     *   elevation = asin( clamp(sin(alt), −1, 1) )
     * </pre>
     * Two consequences the whole feature rides on:
     * <ul>
     *   <li><b>Noon (H = 0):</b> {@code sin(alt) = cos(φ − δ)} ⇒ elevation {@code = 90 − |φ − δ|}
     *       ({@link #noonElevationDeg}).</li>
     *   <li><b>Pole invariant (|φ| = 90):</b> the {@code cos φ} term is 0, so {@code sin(alt) = sin φ · sin δ}
     *       is independent of H — the pole's sun sits at a constant {@code sign(φ)·δ}. (This is why the §12
     *       φ=−90 midnight cell is −δ = −30°, not −90°.)</li>
     * </ul>
     * NaN-safe: any NaN input ⇒ NaN result (never throws).
     */
    public static double solarElevationDeg(double signedLatDeg, double deltaDeg, double hourAngleRad) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg) || Double.isNaN(hourAngleRad)) {
            return Double.NaN;
        }
        double phi = Math.toRadians(signedLatDeg);
        double dec = Math.toRadians(deltaDeg);
        double sinAlt = Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(hourAngleRad);
        return Math.toDegrees(Math.asin(clamp(sinAlt, -1.0, 1.0)));
    }

    /** Noon (H = 0) elevation: {@code 90 − |φ − δ|}. Equal to {@code solarElevationDeg(φ, δ, 0)} across the
     *  whole valid domain (|φ − δ| ≤ 120 < 180). NaN-safe. */
    public static double noonElevationDeg(double signedLatDeg, double deltaDeg) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg)) {
            return Double.NaN;
        }
        return 90.0 - Math.abs(signedLatDeg - deltaDeg);
    }

    /**
     * Render direction UNIT vector {@code {east, up, south}} (§4c) — the target world-frame direction of the
     * sun, provided so P2's pose rebuild (sweep A6 option B, the PRIMARY render path) can be built and
     * headless-tested against a pure vector instead of guessing local mixin axes:
     * <pre>
     *   east  =  cos δ · sin H
     *   up    =  cos φ · cos δ · cos H  +  sin φ · sin δ     (= sin(alt), so asin(up) == solarElevationDeg)
     *   south =  sin φ · cos δ · cos H  −  cos φ · sin δ
     * </pre>
     * Always unit length (proof: {@code east² + up² + south² = cos²δ + sin²δ = 1}). At {@code φ = 0, δ = 0}
     * it collapses to {@code (sin H, cos H, 0)} — vanilla's equatorial arc through the zenith at noon — which
     * is the P2 equator regression guard in plain JVM. NaN-safe (NaN input ⇒ NaN vector).
     */
    public static double[] solarDirection(double signedLatDeg, double deltaDeg, double hourAngleRad) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg) || Double.isNaN(hourAngleRad)) {
            return new double[] {Double.NaN, Double.NaN, Double.NaN};
        }
        double phi = Math.toRadians(signedLatDeg);
        double dec = Math.toRadians(deltaDeg);
        double cosH = Math.cos(hourAngleRad);
        double east = Math.cos(dec) * Math.sin(hourAngleRad);
        double up = Math.cos(phi) * Math.cos(dec) * cosH + Math.sin(phi) * Math.sin(dec);
        double south = Math.sin(phi) * Math.cos(dec) * cosH - Math.cos(phi) * Math.sin(dec);
        return new double[] {east, up, south};
    }

    // ---- bands: midnight sun / polar night (§4b) -------------------------------------------------------

    /** The onset latitude {@code 90 − |δ|}: poleward of this the sun's whole daily circle is on one side of
     *  the horizon (midnight sun on the summer side, polar night on the winter side). At solstice δ = ±30 ⇒
     *  onset 60°; at equinox δ = 0 ⇒ onset 90° (bands vanish). NaN-safe. */
    public static double onsetLatDeg(double deltaDeg) {
        if (Double.isNaN(deltaDeg)) {
            return Double.NaN;
        }
        return 90.0 - Math.abs(deltaDeg);
    }

    /** True iff (φ, δ) is in the midnight-sun band: {@code |φ| > 90 − |δ|} AND φ is on the SUMMER (same-sign
     *  as δ) side. δ = 0 ⇒ never. NaN-safe (false). */
    public static boolean isMidnightSun(double signedLatDeg, double deltaDeg) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg) || deltaDeg == 0.0) {
            return false;
        }
        if (Math.abs(signedLatDeg) <= onsetLatDeg(deltaDeg)) {
            return false;
        }
        return (signedLatDeg > 0.0) == (deltaDeg > 0.0);
    }

    /** True iff (φ, δ) is in the polar-night band: {@code |φ| > 90 − |δ|} AND φ is on the WINTER (opposite
     *  sign to δ) side. δ = 0 ⇒ never. NaN-safe (false). */
    public static boolean isPolarNight(double signedLatDeg, double deltaDeg) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg) || deltaDeg == 0.0) {
            return false;
        }
        if (Math.abs(signedLatDeg) <= onsetLatDeg(deltaDeg)) {
            return false;
        }
        return (signedLatDeg > 0.0) != (deltaDeg > 0.0);
    }

    /**
     * The functional-layer band classifier — the single thing both server mob rules consume. Returns
     * {@link FunctionalBand#NONE} below the {@code functionalMinDeg} floor (sweep A2: the functional layer is
     * NARROWER than the visuals — visuals onset at 60°, but the spawn/burn overrides are gated to
     * {@code |φ| >= functionalMinDeg}, default 74.5°, so the 60–74.5° winter weeks don't besiege livable,
     * village-eligible country), and otherwise midnight-sun / polar-night per {@link #isMidnightSun} /
     * {@link #isPolarNight}. NaN-safe. A NaN floor is treated as 0 (no extra gate).
     */
    public static FunctionalBand functionalBand(double signedLatDeg, double deltaDeg, double functionalMinDeg) {
        if (Double.isNaN(signedLatDeg) || Double.isNaN(deltaDeg)) {
            return FunctionalBand.NONE;
        }
        double floor = Double.isNaN(functionalMinDeg) ? 0.0 : functionalMinDeg;
        if (Math.abs(signedLatDeg) < floor) {
            return FunctionalBand.NONE;
        }
        if (isMidnightSun(signedLatDeg, deltaDeg)) {
            return FunctionalBand.MIDNIGHT_SUN;
        }
        if (isPolarNight(signedLatDeg, deltaDeg)) {
            return FunctionalBand.POLAR_NIGHT;
        }
        return FunctionalBand.NONE;
    }

    // ---- the effective-sun predicate (§8) --------------------------------------------------------------

    /**
     * The effective-sun predicate: is the sun above the (disc-edge) horizon right now? Both mob rules'
     * "effective sun exposure" is {@code effectiveSunUp(...) && skyExposedAtPos} — the {@code skyExposed}
     * term (server: {@code Level.canSeeSky(pos)}) keeps caves inert (never sky-lit). In a polar-night band
     * this is always false (sun never rises); in a midnight-sun band always true (never sets). NaN-safe (a NaN
     * elevation ⇒ false, i.e. treated as night — the safe, spawn-permitting default off-globe).
     */
    public static boolean effectiveSunUp(double signedLatDeg, double deltaDeg, double hourAngleRad) {
        double elevation = solarElevationDeg(signedLatDeg, deltaDeg, hourAngleRad);
        if (Double.isNaN(elevation)) {
            return false;
        }
        return elevation > SUN_UP_THRESHOLD_DEG;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
