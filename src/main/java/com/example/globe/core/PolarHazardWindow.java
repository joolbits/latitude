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
 *       VISUAL frost ramp ({@link #frostVisualTicks}, which CROSSES vanilla's 140 fully-frozen threshold at
 *       the ~88 deg damage onset so the blue frozen hearts + all vanilla freeze visuals fire off our set
 *       value -- TEST 77) and a SEPARATELY-scaled damage curve ({@link #freezeDamageIntervalTicks} +
 *       {@link #freezeDamageAmount}) the mod applies itself. Vanilla's OWN fixed 1 HP/40-tick auto-damage,
 *       which also keys off the 140 threshold, is cancelled at its {@code aiStep} source for in-band players
 *       ({@code LivingEntityFreezeDamageMixin}) so our curve stays the SOLE freeze-damage source -- the cold
 *       builds visibly from 87.5, bites from ~88, and gets worse and worse to a lethal pole, instead of the
 *       old near-binary flip at the doorstep. (B-4 removed the Blindness
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

    // ---- Freeze: a VISUAL frost ramp + a SEPARATELY-scaled damage curve (TEST 77 redesign) ----
    //
    // Why two pieces instead of vanilla's one: vanilla only deals freeze damage while an entity is FULLY
    // frozen (ticksFrozen >= getTicksRequiredToFreeze() == 140), and then a FIXED 1.0 HP every 40 ticks
    // (both verified in LivingEntity.aiStep / Entity for 26.2). Driving that single knob (ticksFrozen) made
    // damage a near-binary flip: the pre-TEST-76 curve pushed ticksFrozen past 140 only in the last ~0.03 deg,
    // so Peetsa stood at 89 deg and took nothing. So we apply our OWN latitude-scaled freeze damage
    // ({@link #freezeDamageIntervalTicks} + {@link #freezeDamageAmount}), a cadence + amount that build from
    // ~88 deg and worsen to a lethal pole. Same damage TYPE (freeze) and death screen as vanilla, only the
    // timing is ours.
    //
    // TEST 76 kept the frost VISUAL capped at 139 -- one tick BELOW the fully-frozen threshold -- specifically
    // so vanilla's own fixed auto-damage never fired and couldn't double-dip with our curve. But that also
    // permanently disabled a piece of vanilla feedback Peetsa WANTS: the HUD hearts only tint blue when
    // {@code isFullyFrozen()} is true ({@code Hud$HeartType.forPlayer} returns FROZEN iff ticksFrozen >= 140).
    // Capping at 139 meant the hearts never went blue even while our curve was taking HP -- TEST 77 report:
    // "the hearts aren't turning blue while I'm taking damage." TEST 77 fix: let ticksFrozen CROSS 140 (so
    // the blue hearts + every vanilla freeze-visual state fire correctly, driven off our set value) and instead
    // suppress vanilla's own automatic freeze-DAMAGE call at its source -- {@code LivingEntityFreezeDamageMixin}
    // cancels the {@code hurtServer(..., damageSources().freeze(), 1.0F)} invocation in {@code aiStep} ONLY for
    // players in this mod's polar hazard band (see {@code GlobeMod.isInPolarFreezeDamageBand}), so our
    // latitude-scaled curve remains the SOLE freeze-damage source while real powder snow / non-globe play stays
    // 100% vanilla. ticksFrozen and every OTHER vanilla check (isFullyFrozen / getPercentFrozen / heart render)
    // are left untouched and driven off our set value.

    /** Vanilla's fully-frozen threshold ({@code Entity.getTicksRequiredToFreeze()} in 26.2): at/above this
     *  {@code isFullyFrozen()} is true, so the HUD hearts tint blue and the frost overlay reads as fully
     *  frozen. We now DELIBERATELY reach it (see {@link #frostVisualTicks}); vanilla's own auto-damage that
     *  also keys off this threshold is cancelled for in-band players by {@code LivingEntityFreezeDamageMixin}
     *  so {@link #freezeDamageAmount} stays the ONLY freeze-damage source. */
    public static final int FROZEN_THRESHOLD_TICKS = 140;
    /** Extra ticksFrozen held ABOVE {@link #FROZEN_THRESHOLD_TICKS} once past the crossing, easing 0 -> this
     *  across the damage sub-window (88 -> 90 deg). Pure decay-headroom: {@code getPercentFrozen()} already
     *  caps at 1.0 at 140, so it changes nothing visually, but it keeps {@code isFullyFrozen()} solidly true
     *  (hearts stay blue, no flicker) even if a mid-tick value ever leaked -- vanilla's {@code aiStep} decays
     *  ticksFrozen by 2/tick when out of powder snow, and our per-tick set at END_SERVER_TICK is the last
     *  writer (the entity-tracker broadcast in ServerChunkCache.tick runs BEFORE aiStep's decay, so the client
     *  sees our value), but the margin makes the blue-heart cue bulletproof in the heaviest-damage band. */
    public static final int FROST_POLE_HEADROOM_TICKS = 8;
    /** ticksFrozen held at the pole: {@link #FROZEN_THRESHOLD_TICKS} + {@link #FROST_POLE_HEADROOM_TICKS} (148). */
    public static final int FROST_VISUAL_POLE_TICKS = FROZEN_THRESHOLD_TICKS + FROST_POLE_HEADROOM_TICKS;

    /**
     * Per-tick frost-visual target (ticksFrozen) for a hazard progress. Two segments, joined continuously at
     * the DAMAGE onset ({@link #DAMAGE_ONSET_PROGRESS}, ~88 deg):
     * <ul>
     *   <li><b>[0, DAMAGE_ONSET_PROGRESS]</b> (87.5 -> 88 deg): builds 0 -> exactly {@link #FROZEN_THRESHOLD_TICKS}
     *       (140). So the frost overlay thickens visibly across the grace band and the hearts flip BLUE at the
     *       instant freeze DAMAGE begins -- the "you are now truly freezing" cue lands exactly when HP starts
     *       to fall (Peetsa's TEST 77 ask), not only at the pole.</li>
     *   <li><b>(DAMAGE_ONSET_PROGRESS, 1]</b> (88 -> 90 deg): holds at/above 140, easing up to
     *       {@link #FROST_VISUAL_POLE_TICKS} (148) for decay-headroom (see {@link #FROST_POLE_HEADROOM_TICKS}).</li>
     * </ul>
     * Monotonic non-decreasing. The caller sets this EVERY server tick (vanilla decays ticksFrozen ~2/tick out
     * of powder snow, so a throttled set would sawtooth). Because ticksFrozen now crosses 140, vanilla's own
     * auto freeze-damage is suppressed at its source by {@code LivingEntityFreezeDamageMixin} (in-band only);
     * {@link #freezeDamageAmount} owns the whole damage curve, so there is no double damage past 140.
     */
    public static int frostVisualTicks(double progress) {
        double p = clamp01(progress);
        if (p <= DAMAGE_ONSET_PROGRESS) {
            double r = DAMAGE_ONSET_PROGRESS <= 0.0 ? 1.0 : p / DAMAGE_ONSET_PROGRESS;
            return (int) Math.round(clamp01(r) * FROZEN_THRESHOLD_TICKS);
        }
        double r = (p - DAMAGE_ONSET_PROGRESS) / (1.0 - DAMAGE_ONSET_PROGRESS);
        return FROZEN_THRESHOLD_TICKS + (int) Math.round(clamp01(r) * FROST_POLE_HEADROOM_TICKS);
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

    // ---- TEST 77 round 2 item 1: DEPTH-BASED polar fog (genuine vanilla render-distance fog) ----
    //
    // The flat PolarWhiteoutOverlayHud screen fill has NO depth information, so it can only be painted when
    // the player is sky-exposed (painting it while sheltered would haze the player's OWN interior walls, not
    // just the view out a doorway). Peetsa wants the exterior to stay heavily fogged "respective to the level
    // outside" even while he is standing inside looking out. The correct fix is Minecraft's OWN render-distance
    // fog (FogData.renderDistanceStart/End), which the fog shader (assets/.../include/fog.glsl) applies as a
    // linear fog of the CYLINDRICAL per-fragment distance max(|xz|,|y|): geometry nearer than START takes zero
    // fog (your shelter walls a couple of blocks away stay clear) while distant terrain past END fades to fog
    // colour. Depth-correct and wall-aware for free -- and, unlike the HUD overlay, correct whether the player
    // is exposed OR sheltered-looking-out, so it needs no sky-exposure gate. The intensity ENVELOPE reuses the
    // SAME ambient window [85,90] as the snow and the overlay ({@link #ambientProgress}); no new latitude curve.
    //
    // These are the pure, testable curves; the client mixin (FogRendererPolarSetupMixin) does the GL-side
    // FogData mutation + fog-colour tint. Only ever TIGHTENS vanilla's fog (NEAR anchors are far closer than
    // any real view distance) and is SEAM-FREE: at/below 85 deg every function returns the caller's own vanilla
    // value unchanged.

    /** Cylindrical render-distance fog END (blocks) at the pole: a heavy whiteout leaving ~1.5 chunks of sight. */
    public static final float POLAR_FOG_END_NEAR = 24.0f;
    /** Cylindrical render-distance fog START (blocks) at the pole: fog begins this close, so past a few blocks
     *  reads as white -- but a wall 2-3 blocks away is still under START, hence unfogged. */
    public static final float POLAR_FOG_START_NEAR = 5.0f;
    /** END-curve exponent over {@link #ambientProgress}. {@code <1} front-loads the ramp so genuinely heavy fog
     *  is reached a little before the pole (Peetsa's 88 deg shelter should already read heavy, not thin). */
    public static final double POLAR_FOG_END_CURVE = 0.85;
    /** START-curve exponent. Smaller than {@link #POLAR_FOG_END_CURVE} so START pulls in FASTER than END and the
     *  fog BAND widens quickly -- a gradual heavy haze building over distance, not a hard wall at one range. */
    public static final double POLAR_FOG_START_CURVE = 0.45;

    /** Eased END fraction in {@code [0,1]} over the ambient window: 0 at/below 85 deg (no change, seam-free),
     *  1 at the pole. */
    public static float polarFogEndFraction(double absLatDeg) {
        double p = ambientProgress(absLatDeg);
        return p <= 0.0 ? 0.0f : (float) Math.pow(p, POLAR_FOG_END_CURVE);
    }

    /** Eased START fraction in {@code [0,1]} over the ambient window (faster curve than END). */
    public static float polarFogStartFraction(double absLatDeg) {
        double p = ambientProgress(absLatDeg);
        return p <= 0.0 ? 0.0f : (float) Math.pow(p, POLAR_FOG_START_CURVE);
    }

    /** Tightened cylindrical fog END: eases the caller's CURRENT vanilla end toward {@link #POLAR_FOG_END_NEAR}
     *  as the pole approaches. Returns {@code vanillaEnd} unchanged at/below 85 deg (seam-free) and never loosens
     *  (NEAR is far closer than any real view distance). Easing from the caller's own end keeps the onset seam
     *  free at every video render distance. */
    public static float polarFogEnd(float vanillaEnd, double absLatDeg) {
        float f = polarFogEndFraction(absLatDeg);
        if (f <= 0.0f) {
            return vanillaEnd;
        }
        return vanillaEnd + (POLAR_FOG_END_NEAR - vanillaEnd) * f;
    }

    /** Tightened cylindrical fog START: eases the caller's vanilla start toward {@link #POLAR_FOG_START_NEAR} on
     *  the faster START curve, then clamps just below the (already tightened) end so START &lt; END always holds.
     *  Seam-free at/below 85 deg. */
    public static float polarFogStart(float vanillaStart, float tightenedEnd, double absLatDeg) {
        float f = polarFogStartFraction(absLatDeg);
        float start = f <= 0.0f ? vanillaStart : vanillaStart + (POLAR_FOG_START_NEAR - vanillaStart) * f;
        return Math.min(start, tightenedEnd - 1.0f);
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

    // ---- TEST 77 round 2 item 2: BLIZZARD particle drive MAGNITUDES (blocks/tick) ----
    //
    // Peetsa (TEST 77): the polar snow is "slow and falls down, not sideways like a blizzard." Root cause is
    // vanilla SnowflakeParticle physics, NOT a plumbing bug -- the wind vector we pass DOES reach the flake,
    // but SnowflakeParticle.tick() multiplies horizontal velocity by 0.95 EVERY tick (decays ~5%/tick, halving
    // in ~13 ticks) and pins the vertical velocity to a gravity terminal of ~0.081/tick (gravity 0.225 vs its
    // own 0.90 vertical damping) regardless of the fall speed we hand it. So the old ceilings (wind 0.09+0.34,
    // fall 0.04+0.11) died to a straight-down gentle drift within a second of each flake's life. Two
    // consequences drive these numbers: (a) the SIDEWAYS wind must be spawned LARGE so that even after the
    // per-tick decay it stays above the ~0.081 fall terminal for most of a flake's ~30-tick visible life -- i.e.
    // it keeps reading as wind-driven, not just a brief initial kick; (b) the sustained FALL speed cannot be
    // raised from here (it always converges to vanilla's terminal), so BLIZZARD_FALL_* is only a livelier entry
    // impulse -- the "blizzard" read is carried by the horizontal drive, which turns the gentle vertical drift
    // into a fast diagonal/near-horizontal gale. Gentle at the 85-87 approach (drive 0) so the approach still
    // feels like an approach; a hard gale by the pole.
    //
    // Pure functions of latitude so the wind/fall magnitude-vs-latitude curve is unit-testable; the client
    // spawner (GlobeModClient.spawnAmbientPolarSnow) reads these and does the per-flake velocity assignment.

    /** Gentle-flurry sideways wind (blocks/tick) through the 85-87 approach band (blizzard drive == 0). */
    public static final double BLIZZARD_WIND_BASE = 0.10;
    /** Sideways wind (blocks/tick) ADDED at the pole on top of the base -- the driven-gale ceiling. Large on
     *  purpose: SnowflakeParticle's 0.95/tick horizontal decay eats most of it over a flake's life, so the
     *  spawn value must overshoot to still read as sideways-driven after ~20-30 ticks. */
    public static final double BLIZZARD_WIND_GALE = 1.00;
    /** Gentle-flurry entry fall speed (blocks/tick) in the approach band. */
    public static final double BLIZZARD_FALL_BASE = 0.05;
    /** Entry fall speed ADDED at the pole. NOTE: sustained fall converges to vanilla's ~0.081/tick terminal
     *  regardless of this -- it only makes the flake's first ~1 s fall more energetically before settling. */
    public static final double BLIZZARD_FALL_GALE = 0.25;

    /** Unsigned sideways wind speed (blocks/tick) for spawned polar snow at a latitude: base through the
     *  approach, ramping to base+gale at the pole on the {@link #blizzardDrive} window. */
    public static double blizzardWindMagnitude(double absLatDeg) {
        return BLIZZARD_WIND_BASE + BLIZZARD_WIND_GALE * blizzardDrive(absLatDeg);
    }

    /** Downward entry speed (blocks/tick) for spawned polar snow at a latitude (see {@link #BLIZZARD_FALL_GALE}
     *  -- sustained fall is vanilla's terminal, this is only the spawn impulse). */
    public static double blizzardFallSpeed(double absLatDeg) {
        return BLIZZARD_FALL_BASE + BLIZZARD_FALL_GALE * blizzardDrive(absLatDeg);
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
