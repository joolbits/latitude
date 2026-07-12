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
 *   <li><b>B-3a HAZARD window {@code [87.5,90]}</b> -- the ONLY player-affecting hazardous band.
 *       Onset at 87.5 deg (moved in from 88.5 per Peetsa's TEST 76 note: at 89 deg he took ZERO freeze
 *       damage because the old curve only crossed vanilla's fully-frozen threshold in the last fractional
 *       degree), full-lethal at 90 deg, over {@code progress = clamp01((|lat|-87.5)/2.5)}. Slowness /
 *       weakness / mining-fatigue amplifiers scale continuously with progress. FREEZE is split into a
 *       VISUAL frost ramp ({@link #frostVisualTicks}, capped one tick below vanilla's 140 fully-frozen
 *       threshold so vanilla's OWN fixed 1 HP/40-tick auto-damage never fires) and a SEPARATELY-scaled
 *       damage curve ({@link #freezeDamageIntervalTicks} + {@link #freezeDamageAmount}) the mod applies
 *       itself -- so the cold builds visibly from 87.5, bites from ~88, and gets worse and worse to a
 *       lethal pole, instead of the old near-binary flip at the doorstep. (B-4 removed the Blindness
 *       effect: the smooth whiteout overlay now carries vision loss without a hard snap.)</li>
 *   <li><b>B-3b AMBIENT window {@code [85,90]}</b> -- atmosphere BEFORE danger. Snow begins at 85 deg
 *       (2 deg ahead of the hazard onset) and the fixed per-tick particle budget + screen-fog
 *       intensity ramp smoothly to VERY heavy at 90 deg over {@code clamp01((|lat|-85)/5)}.</li>
 * </ul>
 */
public final class PolarHazardWindow {

    private PolarHazardWindow() {
    }

    // ---- B-3a: continuous player-affecting hazard window [87.5,90] (server-side) ----

    /** Latitude (deg) at which the player-affecting hazard window opens; below this the pole is fully
     *  explorable. Moved 88.5 -> 87.5 (TEST 76: at 89 deg Peetsa took ZERO freeze damage -- the old onset
     *  + curve made damage a near-binary flip in the last fractional degree). Slowness now ramps in at
     *  87.5; frost builds visibly from here and freeze DAMAGE begins ~88 deg and intensifies to a lethal
     *  pole (see {@link #freezeDamageIntervalTicks} / {@link #freezeDamageAmount}). The AMBIENT band
     *  (snow/fog/storm sky at 85) and the blizzard VISUAL drive (87) are deliberately NOT gated on this --
     *  only the slowness/weakness/mining-fatigue/freeze mechanics are. */
    public static final double HAZARD_ONSET_DEG = 87.5;
    /** Latitude (deg) at which the hazard window is full-lethal (the geographic pole). */
    public static final double HAZARD_LETHAL_DEG = 90.0;

    /** Slowness amplifier at full lethal (amplifier 2 = Slowness III). Slowness I is present from onset. */
    public static final int SLOWNESS_MAX_AMP = 2;
    /** Weakness amplifier at full lethal (amplifier 1 = Weakness II). */
    public static final int WEAKNESS_MAX_AMP = 1;
    /** Progress at/above which Mining Fatigue layers in (~88.33 deg -- 87.5 + 2.5*(1/3)). */
    public static final double MINING_FATIGUE_PROGRESS = 1.0 / 3.0;

    /** Continuous hazard progress in {@code [0,1]}: 0 at/below 87.5 deg, 1 at/above 90 deg. */
    public static double hazardProgress(double absLatDeg) {
        return clamp01((absLatDeg - HAZARD_ONSET_DEG) / (HAZARD_LETHAL_DEG - HAZARD_ONSET_DEG));
    }

    /** Slowness MobEffect amplifier for a hazard progress: integer in {@code [0, SLOWNESS_MAX_AMP]}.
     *  Slowness I from progress 0 (87.5 deg), II from ~1/3 (~88.33 deg), III from ~2/3 (~89.17 deg). */
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

    // ---- Freeze: a VISUAL frost ramp + a SEPARATELY-scaled damage curve (TEST 76 redesign) ----
    //
    // Why two pieces instead of vanilla's one: vanilla only deals freeze damage while an entity is FULLY
    // frozen (ticksFrozen >= getTicksRequiredToFreeze() == 140), and then a FIXED 1.0 HP every 40 ticks
    // (both verified in LivingEntity/Entity for 26.2). Driving that single knob (ticksFrozen) made damage a
    // near-binary flip: the old curve pushed ticksFrozen past 140 only in the last ~0.03 deg, so Peetsa
    // stood at 89 deg and took nothing. Instead we (a) ramp a FROST VISUAL that tops out at 139 -- one tick
    // BELOW the fully-frozen threshold, so vanilla's fixed auto-damage never fires and can't fight our
    // curve -- and (b) apply our OWN freeze damage on a latitude-scaled cadence + amount, so the cold
    // visibly builds from 87.5, bites from ~88, and worsens to a lethal pole. Same damage TYPE (freeze) and
    // death screen as vanilla, so to the player it just reads as freezing to death -- only the timing is ours.

    /** Vanilla's fully-frozen threshold ({@code Entity.getTicksRequiredToFreeze()} in 26.2): at/above this
     *  the game shows fully-blue frozen hearts AND deals its own fixed 1 HP/40-tick freeze damage. We stay
     *  BELOW it (see {@link #FROST_VISUAL_MAX_TICKS}) so our scaled curve is the ONLY freeze damage. */
    public static final int FROZEN_THRESHOLD_TICKS = 140;
    /** Frost-visual ceiling: one tick below {@link #FROZEN_THRESHOLD_TICKS}. At 139/140 the frost overlay
     *  and heart tint read as fully frozen, but the player is never {@code isFullyFrozen()}, so vanilla's
     *  fixed auto-damage never triggers and {@link #freezeDamageAmount} owns the whole curve. */
    public static final int FROST_VISUAL_MAX_TICKS = FROZEN_THRESHOLD_TICKS - 1; // 139
    /** Hazard progress at which the frost visual reaches its {@link #FROST_VISUAL_MAX_TICKS} ceiling and
     *  holds (~88.75 deg): frost builds visibly across 87.5 -> 88.75, then stays maxed to the pole. */
    public static final double FROST_VISUAL_FULL_PROGRESS = 0.5;

    /** Per-tick frost-visual target (ticksFrozen) for a hazard progress: 0 at onset, ramping to
     *  {@link #FROST_VISUAL_MAX_TICKS} by {@link #FROST_VISUAL_FULL_PROGRESS} and holding. Always &lt; 140,
     *  so it can never trip vanilla's fully-frozen auto-damage. The caller sets this every server tick
     *  (vanilla decays ticksFrozen ~2/tick out of powder snow, so a throttled set would sawtooth). */
    public static int frostVisualTicks(double progress) {
        double p = clamp01(progress) / FROST_VISUAL_FULL_PROGRESS;
        return (int) Math.round(clamp01(p) * FROST_VISUAL_MAX_TICKS);
    }

    /** Hazard progress at which freeze DAMAGE begins (~88.0 deg -- 87.5 + 2.5*0.2). The 0.5 deg between
     *  the hazard onset (87.5, frost + slowness) and here is a grace band: the cold sets in and slows you
     *  before it starts taking hearts. */
    public static final double DAMAGE_ONSET_PROGRESS = 0.2;
    /** Ticks between freeze-damage hits at the damage onset (~88 deg): 60 ticks == 3 s, a slow chip. */
    public static final int FREEZE_DAMAGE_INTERVAL_FAR = 60;
    /** Ticks between freeze-damage hits at the pole: 10 ticks == 0.5 s, a rapid lethal cadence. */
    public static final int FREEZE_DAMAGE_INTERVAL_NEAR = 10;
    /** HP per freeze-damage hit at the damage onset (~88 deg): 1.0 == half a heart. */
    public static final float FREEZE_DAMAGE_MIN_HP = 1.0f;
    /** HP per freeze-damage hit at the pole: 3.0 == one and a half hearts. With the shortest interval this
     *  is ~6 HP/s at 90 deg -- lethal within a few seconds. */
    public static final float FREEZE_DAMAGE_MAX_HP = 3.0f;

    /** True once the hazard is deep enough for freeze DAMAGE to tick ({@code progress >=
     *  DAMAGE_ONSET_PROGRESS}, i.e. from ~88 deg). Below this the player frosts + slows but loses no HP. */
    public static boolean appliesFreezeDamage(double progress) {
        return clamp01(progress) >= DAMAGE_ONSET_PROGRESS;
    }

    /** Progress through the DAMAGE sub-window {@code [DAMAGE_ONSET_PROGRESS, 1]} remapped to {@code [0,1]}:
     *  0 at the damage onset (~88 deg), 1 at the pole. Drives both the interval and the amount below. */
    private static double damageProgress(double progress) {
        return clamp01((clamp01(progress) - DAMAGE_ONSET_PROGRESS) / (1.0 - DAMAGE_ONSET_PROGRESS));
    }

    /** Server-ticks between freeze-damage hits for a hazard progress: {@link #FREEZE_DAMAGE_INTERVAL_FAR}
     *  at the damage onset shrinking linearly to {@link #FREEZE_DAMAGE_INTERVAL_NEAR} at the pole (shorter
     *  == more frequent == worse). Never below 1. Below the damage onset this returns the FAR value, but
     *  callers gate on {@link #appliesFreezeDamage} first so no damage lands there. */
    public static int freezeDamageIntervalTicks(double progress) {
        double d = damageProgress(progress);
        double interval = FREEZE_DAMAGE_INTERVAL_FAR
                + (FREEZE_DAMAGE_INTERVAL_NEAR - FREEZE_DAMAGE_INTERVAL_FAR) * d;
        return Math.max(1, (int) Math.round(interval));
    }

    /** HP dealt per freeze-damage hit for a hazard progress: {@link #FREEZE_DAMAGE_MIN_HP} at the damage
     *  onset growing linearly to {@link #FREEZE_DAMAGE_MAX_HP} at the pole (bigger == worse). */
    public static float freezeDamageAmount(double progress) {
        double d = damageProgress(progress);
        return (float) (FREEZE_DAMAGE_MIN_HP + (FREEZE_DAMAGE_MAX_HP - FREEZE_DAMAGE_MIN_HP) * d);
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

    // ---- B-4 round 3 item 3: STORM-SKY level (steepened so the sun is gone well before the pole) ----

    /** Latitude (deg) at which the storm sky begins to lift (same 85 deg onset as ambient snow, so the
     *  overcast, the snowfall and the screen fog all start together with no seam). */
    public static final double STORM_ONSET_DEG = 85.0;
    /** Latitude (deg) at which the storm sky is FULLY overcast (rain level 1.0, sun fully faded). Reached
     *  by ~87.5 deg -- deliberately much steeper than the 85->90 ambient ramp: Peetsa saw the sun still
     *  shining at 86 deg because the sky lift was linear over 85->90 (only ~0.2 at 86). At 86 deg this is
     *  now 0.4 (clearly overcast); by 87.5 deg it is full storm and the sun is gone. */
    public static final double STORM_FULL_DEG = 87.5;

    /**
     * Client storm-sky level in {@code [0,1]} used to lift the client rain level (greys the sky, fades the
     * sun/moon, and thickens vanilla snowfall). STEEPER than {@link #ambientProgress(double)}: 0 at/below
     * 85 deg, 1.0 (full overcast) at/above {@link #STORM_FULL_DEG}.
     */
    public static float stormLevel(double absLatDeg) {
        return (float) clamp01((absLatDeg - STORM_ONSET_DEG) / (STORM_FULL_DEG - STORM_ONSET_DEG));
    }

    // ---- B-4 round 3 item 2: BLIZZARD drive (fall speed + sideways wind + dense second pass) ----

    /** Latitude (deg) at which the blizzard VISUAL drive begins to ramp. Held at 87 (NOT the moved
     *  {@link #HAZARD_ONSET_DEG}, now 87.5 after the TEST 76 retune -- was 88.5 for one round in between):
     *  the driven-gale look + dense second particle pass are ATMOSPHERE, which Peetsa asked to keep exactly
     *  as-is regardless of where the player-affecting hazard onset lands. */
    public static final double BLIZZARD_ONSET_DEG = 87.0;
    /** Latitude (deg) at which the blizzard visual drive is fully driven (the geographic pole). */
    public static final double BLIZZARD_FULL_DEG = 90.0;

    /**
     * Blizzard drive in {@code [0,1]} over the VISUAL window {@code [87,90]}. Formerly reused
     * {@link #hazardProgress(double)}, but DECOUPLED from it once the player-hazard onset started moving
     * (87 -> 88.5 -> 87.5, TEST 75/76): the ambient snow at 85-87 deg stays a gentle approach flurry, and
     * from 87 deg this scales the flakes'
     * fall speed and sideways wind up to a driven gale and gates the dense low second particle pass so the
     * pole reads as a real BLIZZARD. Keeping this on the original {@code [87,90]} ramp preserves the blizzard
     * LOOK exactly (Peetsa: keep the ambient band as-is) while only the mechanics moved inward. Changes how
     * flakes LOOK/MOVE and adds a fixed per-tick second budget; never a catch-up accumulator (B-3b law).
     */
    public static float blizzardDrive(double absLatDeg) {
        return (float) clamp01((absLatDeg - BLIZZARD_ONSET_DEG) / (BLIZZARD_FULL_DEG - BLIZZARD_ONSET_DEG));
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
