package com.example.globe.core;

/**
 * S37 F1 -- PONDED WATER CAUSES FREEZING (Peetsa: "The water that has ponded is not causing freezing
 * damage — hearts are red"). Pure Java, zero Minecraft imports (Core Logic layer, unit-testable in a
 * plain JVM). The MC-coupled part is a handful of primitive reads (latitude, {@code isInWater()},
 * {@code canFreeze()}, {@code getTicksFrozen()}, {@code getTicksRequiredToFreeze()}) in
 * {@code GlobeMod.borderUxTick}; every decision/curve lives here.
 *
 * <p><b>The bug.</b> Standing in a polar meltwater pond, the player takes freeze DAMAGE (from the
 * existing B-7 curves — frostbite/lethal core, both gated on latitude, not on water contact) but never
 * sees vanilla's blue frozen-heart HUD tint, because nothing was raising {@code ticksFrozen} on account
 * of the water itself — only the S7 immersion latitude-shift feeds the AMBIENT cold curves, and it never
 * touches {@code ticksFrozen} directly. "Freezing damage" without "the hearts turning blue" reads as a
 * bug (TEST 77 fixed the identical symptom for the lethal core; this is the water-contact analogue).
 *
 * <p><b>The fix (direct, not the S7 curve-shift).</b> {@link PolarImmersion} answers "how much colder do
 * the AMBIENT cold curves read while immersed" (+3 deg, applied to {@code PolarHazardWindow}). This class
 * is a SEPARATE, DIRECT rule: touching water in polar country builds {@code ticksFrozen} itself, exactly
 * the way standing in vanilla powder snow does, just faster and starting equatorward of the frostbite band
 * (82 deg here vs. 85 for {@code PolarHazardWindow.FROSTBITE_ONSET_DEG}) — "polar water is colder than
 * polar air, and it touches you directly." Composes with everything downstream rather than fighting it:
 * <ul>
 *   <li>Below {@code PolarHazardWindow.FROSTBITE_ONSET_DEG} (85) nothing else in
 *       {@code GlobeMod.borderUxTick} ever touches {@code ticksFrozen}, so between the two onsets
 *       (82-85) this class's write is the SOLE driver -- vanilla's own {@code aiStep} then renders the
 *       frost visual and (once {@code isFullyFrozen()}) deals its fixed 1 HP/40-tick freeze damage,
 *       entirely on its own, exactly like real powder snow.</li>
 *   <li>At/above 85 the existing frostbite-cue-floor write only RAISES {@code ticksFrozen} (never lowers),
 *       and at/above {@code HAZARD_ONSET_DEG} (87.5) the lethal core's frost-visual write sets it to
 *       {@code max(frostVisualTicks, frostCueFloor)} EVERY tick -- both of which dominate this class's
 *       contribution without needing to know it exists, so nothing double-dips or fights.</li>
 *   <li>{@code LivingEntityFreezeDamageMixin} only suppresses vanilla's own auto-freeze-damage call at/above
 *       {@code HAZARD_ONSET_DEG} (87.5, via {@code GlobeMod.isInPolarFreezeDamageBand}); below that this
 *       class's water contact relies on vanilla's own damage call firing UNSUPPRESSED, same as real powder
 *       snow anywhere else in the game.</li>
 * </ul>
 *
 * <p><b>Decay counter-pressure (javap-verified, 26.2 {@code LivingEntity.aiStep}).</b> Vanilla decrements
 * {@code ticksFrozen} by 2/tick whenever the entity is NOT (in powder snow AND {@code canFreeze()}) --
 * i.e. every tick a player is merely standing in water (not powder snow), regardless of latitude. This
 * per-tick curve's minimum (+3 at onset) and maximum (+6 at the pole) are chosen to always net-outpace
 * that -2/tick floor (net +1/tick .. +4/tick), so a player who stays in the water keeps building toward
 * the 140-tick fully-frozen threshold instead of stalling; stepping OUT of the water lets the untouched
 * vanilla decay unwind it (no custom decay needed here -- the spec's grace).
 */
/*
 * COMPOSITION CORRECTION (S37 sweep finding 2): an 82+ swimmer is ALREADY in the frostbite DAMAGE band,
 * because the S7 immersion law evaluates ambient cold at |lat|+3 -- so this chill's vanilla auto-freeze
 * damage STACKS with S7 frostbite in 82-85 water. That stack is DELIBERATE (polar water is colder than
 * polar air; two damage sources = swim at your peril) and is called out for the owner's flight verdict.
 */
public final class PolarWaterChill {

    private PolarWaterChill() {
    }

    /** Latitude (deg) at which direct water-contact chill begins. Equatorward of
     *  {@code PolarHazardWindow.FROSTBITE_ONSET_DEG} (85) on purpose -- ordinary polar meltwater ponds
     *  well before the frostbite band start chilling a swimmer directly. */
    public static final double ONSET_LAT_DEG = 82.0;

    /** The pole: the per-tick curve's ceiling. */
    public static final double POLE_LAT_DEG = 90.0;

    /** ticksFrozen/tick contributed at the onset (82 deg) -- outpaces vanilla's -2/tick decay by +1/tick. */
    public static final int PER_TICK_AT_ONSET = 3;

    /** ticksFrozen/tick contributed at the pole (90 deg) -- outpaces vanilla's -2/tick decay by +4/tick,
     *  reaching the 140-tick fully-frozen threshold in well under vanilla's powder-snow pace. */
    public static final int PER_TICK_AT_POLE = 6;

    /** Vanilla's own per-tick {@code ticksFrozen} decay outside powder snow (26.2 {@code aiStep},
     *  javap-verified: {@code setTicksFrozen(max(0, getTicksFrozen() - 2))}). Exposed so tests can assert
     *  this curve always nets a gain, not merely a smaller loss. */
    public static final int VANILLA_DECAY_PER_TICK = 2;

    /**
     * ticksFrozen/tick this direct water-contact chill contributes at {@code absLatDeg}: 0 below
     * {@link #ONSET_LAT_DEG}, linearly rising from {@link #PER_TICK_AT_ONSET} to
     * {@link #PER_TICK_AT_POLE} across {@code [ONSET_LAT_DEG, POLE_LAT_DEG]}, rounded to the nearest
     * whole tick (a fractional-tick accumulator is unnecessary noise for a value only ever added as an
     * int). NaN -> 0 (never chill on bad data).
     */
    public static int ticksFrozenPerTick(double absLatDeg) {
        if (Double.isNaN(absLatDeg) || absLatDeg < ONSET_LAT_DEG) {
            return 0;
        }
        double clamped = Math.min(absLatDeg, POLE_LAT_DEG);
        double t = (clamped - ONSET_LAT_DEG) / (POLE_LAT_DEG - ONSET_LAT_DEG);
        double value = PER_TICK_AT_ONSET + t * (PER_TICK_AT_POLE - PER_TICK_AT_ONSET);
        return (int) Math.round(value);
    }

    /**
     * The cap this class's contribution may push {@code ticksFrozen} toward, derived from vanilla's own
     * {@code getTicksRequiredToFreeze()} (140 by default): {@code requiredToFreeze * CAP_MULTIPLIER} (3x
     * the fully-frozen threshold). This is a SAFETY ceiling only -- in practice the frostbite/lethal-core
     * writers above 85 deg dominate {@code ticksFrozen} long before this cap could bind; it exists so a
     * player who is somehow both immersed and untouched by any other writer for an extended time cannot
     * accumulate an unbounded value.
     */
    public static final int CAP_MULTIPLIER = 3;

    /** The capped next {@code ticksFrozen} value: {@code min(currentTicksFrozen + ticksFrozenPerTick(lat),
     *  requiredToFreeze * CAP_MULTIPLIER)}. Never decreases {@code currentTicksFrozen} (this class only
     *  ever raises it; vanilla's own decay is the sole decreaser, per the class javadoc). */
    public static int nextTicksFrozen(int currentTicksFrozen, double absLatDeg, int requiredToFreeze) {
        int perTick = ticksFrozenPerTick(absLatDeg);
        int cap = requiredToFreeze * CAP_MULTIPLIER;
        int next = currentTicksFrozen + perTick;
        return Math.min(next, cap);
    }
}
