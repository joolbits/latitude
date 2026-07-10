package com.example.globe.core;

/**
 * Phase 5 Slice B-3 (P1) -- pure window math for the POLAR APPROACH experience. Zero Minecraft
 * imports (Core Logic layer, unit-testable in a plain JVM). Callers pass absolute latitude in
 * DEGREES ({@code |lat|} in {@code [0,90]}, e.g. from {@code LatitudeMath.absLatDegExact}) and read
 * back continuous effect magnitudes / a fixed particle budget. No time or wall-clock input: every
 * function is a pure function of latitude, so there is no accumulator to "catch up" (the B-3b
 * anti-backlog rule lives in the caller's {@code isPaused()} guard + fixed-per-tick budget).
 *
 * <p>Two independent windows, both symmetric about the equator because the caller feeds {@code |lat|}:
 * <ul>
 *   <li><b>B-3a HAZARD window {@code [87,90]}</b> -- the ONLY hazardous band. Onset at 87 deg,
 *       full-lethal at 90 deg, with CONTINUOUS scaling of the slowness amplifier and the freeze-tick
 *       rate across {@code progress = clamp01((|lat|-87)/3)}. Replaces the old stage-STEPPED ladder
 *       (IMPAIR/HOSTILE/WHITEOUT/LETHAL keyed off {@code POLAR_STAGE_*_PROGRESS} on {@code |z|/zR});
 *       the effect TYPES (slowness/weakness/mining-fatigue/freeze) are unchanged -- only their
 *       magnitude is now a smooth function of progress rather than a stage index. (B-4 removed the
 *       Blindness effect: the smooth whiteout overlay now carries vision loss without a hard snap.)</li>
 *   <li><b>B-3b AMBIENT window {@code [85,90]}</b> -- atmosphere BEFORE danger. Snow begins at 85 deg
 *       (2 deg ahead of the hazard onset) and the fixed per-tick particle budget + screen-fog
 *       intensity ramp smoothly to VERY heavy at 90 deg over {@code clamp01((|lat|-85)/5)}.</li>
 * </ul>
 */
public final class PolarHazardWindow {

    private PolarHazardWindow() {
    }

    // ---- B-3a: continuous hazard window [87,90] (server MobEffects) ----

    /** Latitude (deg) at which the hazard window opens; below this the pole is fully explorable. */
    public static final double HAZARD_ONSET_DEG = 87.0;
    /** Latitude (deg) at which the hazard window is full-lethal (the geographic pole). */
    public static final double HAZARD_LETHAL_DEG = 90.0;

    /** Slowness amplifier at full lethal (== old LETHAL: amplifier 2 = Slowness III). */
    public static final int SLOWNESS_MAX_AMP = 2;
    /** Weakness amplifier at full lethal (== old LETHAL: amplifier 1 = Weakness II). */
    public static final int WEAKNESS_MAX_AMP = 1;
    /** Freeze ticks at full lethal. Vanilla powder-snow freeze damage begins at 140 ticks frozen. */
    public static final int FREEZE_MAX_TICKS = 140;
    /** Progress at/above which Mining Fatigue layers in (~88 deg). */
    public static final double MINING_FATIGUE_PROGRESS = 1.0 / 3.0;

    /** Continuous hazard progress in {@code [0,1]}: 0 at/below 87 deg, 1 at/above 90 deg. */
    public static double hazardProgress(double absLatDeg) {
        return clamp01((absLatDeg - HAZARD_ONSET_DEG) / (HAZARD_LETHAL_DEG - HAZARD_ONSET_DEG));
    }

    /** Slowness MobEffect amplifier for a hazard progress: integer in {@code [0, SLOWNESS_MAX_AMP]}. */
    public static int slownessAmplifier(double progress) {
        return rampAmplifier(progress, SLOWNESS_MAX_AMP);
    }

    /** Weakness MobEffect amplifier for a hazard progress: integer in {@code [0, WEAKNESS_MAX_AMP]}. */
    public static int weaknessAmplifier(double progress) {
        return rampAmplifier(progress, WEAKNESS_MAX_AMP);
    }

    /** True once the hazard is deep enough to add Mining Fatigue. */
    public static boolean appliesMiningFatigue(double progress) {
        return clamp01(progress) >= MINING_FATIGUE_PROGRESS;
    }

    /** Freeze ticks to force for a hazard progress: 0 at onset, {@link #FREEZE_MAX_TICKS} at 90 deg. */
    public static int freezeTicks(double progress) {
        return (int) Math.round(clamp01(progress) * FREEZE_MAX_TICKS);
    }

    // ---- B-3b: ambient snow + fog window [85,90] (client particles + screen fog) ----

    /** Latitude (deg) at which ambient snow/fog begins -- 2 deg ahead of the hazard onset. */
    public static final double AMBIENT_ONSET_DEG = 85.0;
    /** Latitude (deg) at which ambient snow/fog is at its VERY-heavy ceiling (the pole). */
    public static final double AMBIENT_FULL_DEG = 90.0;
    /** Gentle-flurry per-tick snow budget at the 85 deg onset. */
    public static final int SNOW_MIN_COUNT = 2;
    /** Per-tick snow budget at 90 deg. FIXED budget -- never a catch-up accumulator. B-4 round 2 returned
     *  this 80->30: the pole's storm DENSITY is now carried by real VANILLA snowfall (ClientLevelStormSkyMixin
     *  lifts the client rain level over 85->90 deg), so this ambient particle layer is back to being subtle
     *  near-field texture rather than trying (and failing, per Peetsa) to read as the whole blizzard itself. */
    public static final int SNOW_MAX_COUNT = 30;

    /** Continuous ambient progress in {@code [0,1]}: 0 at/below 85 deg, 1 at/above 90 deg. */
    public static double ambientProgress(double absLatDeg) {
        return clamp01((absLatDeg - AMBIENT_ONSET_DEG) / (AMBIENT_FULL_DEG - AMBIENT_ONSET_DEG));
    }

    /**
     * FIXED per-tick snow particle budget from the ambient ramp: 0 below 85 deg, {@link #SNOW_MIN_COUNT}
     * at onset, ramping to {@link #SNOW_MAX_COUNT} at 90 deg. This is a per-tick BUDGET, not an amount
     * owed since the last spawn -- the caller spawns exactly this many and no more, so a paused/lagging
     * client never accrues a backlog to dump on resume.
     */
    public static int snowCount(double absLatDeg) {
        double p = ambientProgress(absLatDeg);
        if (p <= 0.0) {
            return 0;
        }
        return SNOW_MIN_COUNT + (int) Math.round(p * (SNOW_MAX_COUNT - SNOW_MIN_COUNT));
    }

    /**
     * Screen-fog / whiteout intensity in {@code [0,1]} over the ambient window -- the SAME 85->90
     * progress the snow budget uses, so fog density and snowfall thicken together. 1.0 at 90 deg
     * preserves the deep-end whiteout magnitude the stage ladder produced at the pole.
     */
    public static float fogIntensity(double absLatDeg) {
        return (float) ambientProgress(absLatDeg);
    }

    // ---- shared ----

    /**
     * Maps a continuous progress in {@code [0,1]} onto an integer amplifier in {@code [0, maxAmp]}:
     * the {@code [0,1]} range is split into {@code maxAmp+1} equal bands. Progress 0 -> amp 0 (the
     * effect is present at onset at its lowest tier); progress 1 -> {@code maxAmp}.
     */
    private static int rampAmplifier(double progress, int maxAmp) {
        double p = clamp01(progress);
        int amp = (int) Math.floor(p * (maxAmp + 1));
        return Math.min(maxAmp, amp);
    }

    /** Clamp to {@code [0,1]} (NaN -> 0). */
    public static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, v);
    }
}
