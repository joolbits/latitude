package com.example.globe.core;

/**
 * Pure decision for the "all exposed water is frozen at the poles" correctness rule (Core Logic layer,
 * zero Minecraft imports, unit-testable in a plain JVM). Sibling of {@link PolarPrecipitationRule}.
 *
 * <p><b>The bug.</b> Standing at 89 deg S in a full blizzard, Peetsa saw a pool of LIQUID water in his
 * shelter doorway. Vanilla decides "should this exposed water column freeze into ice" per column via
 * {@code Biome.shouldFreeze(LevelReader, BlockPos, boolean)}, whose very first gate is
 * {@code if (this.warmEnoughToRain(pos, seaLevel)) return false;} -- i.e. a biome whose temperature is
 * >= 0.15 never freezes its water. The polar cap is riddled with LATITUDE-BLIND columns: vanilla's noise
 * router places {@code river} (base temperature 0.5) and {@code ocean} (0.5) anywhere including 89 deg,
 * and {@code LatitudeBiomeSource} never re-classifies them, so a polar river/ocean column reports
 * "warm enough to rain" and its water stays liquid forever. Player-placed water (buckets) sitting on a
 * warm-biome column has the same problem. This is the exact water-freezing analogue of the rain-at-the-pole
 * bug that {@link PolarPrecipitationRule} fixes for precipitation.
 *
 * <p><b>The rule.</b> At extreme latitudes every exposed water column must be eligible to freeze,
 * biome-independent. The fix piggybacks on vanilla's OWN freeze mechanic + cadence rather than writing a
 * new one: it neutralises only the single latitude-blind sub-decision -- the {@code warmEnoughToRain}
 * temperature gate inside {@code Biome.shouldFreeze} (see {@code BiomePolarWaterFreezeMixin}) -- so the
 * rest of vanilla's genuine "is this actually freezable exposed water" logic (inside build height, block
 * light &lt; 10, fluid is water, block is a {@code LiquidBlock}, edge-of-water) runs UNCHANGED and only
 * genuine water turns to ice. Because {@code Biome.shouldFreeze} is the single method every freeze path
 * funnels through (ongoing {@code ServerLevel.tickPrecipitation} random-tick freezing, plus the worldgen
 * {@code LakeFeature} and {@code SnowAndFreezeFeature} paths), one hook covers ongoing AND at-generation
 * freezing, and it inherits vanilla's edge-inward, over-time freeze cadence (so this needs no latitude
 * fade -- the spread is a property of vanilla's tick loop, not of latitude).
 *
 * <p><b>Threshold = 85 deg (its OWN anchor -- DECOUPLED from the ambient onset, B-7 S3).</b> This forces
 * exposed water to freeze into ice, which MODIFIES THE WORLD (places ice blocks) and is therefore a
 * WORLDGEN-facing seam that must NOT move. When B-7 S3 shifted the pure-client ambient snow/fog onset
 * {@code PolarHazardWindow.AMBIENT_ONSET_DEG} 85 -> 82 (2026-07-13; S8 moved it again to 80 on 2026-07-14),
 * this constant deliberately STAYED at 85
 * on its own literal anchor: the frozen ice sheet is world-visible and moving it would re-freeze different
 * columns in existing worlds. 85 still coincides with the B-7 frostbite DAMAGE onset
 * ({@code PolarHazardWindow.FROSTBITE_ONSET_DEG}, also 85), so "the water is frozen" and "the cold starts to
 * bite" read as one line, just now inside a whiteout that began a few degrees earlier. It also sits poleward of
 * the naturally-cold bands (subpolar taiga / snowy) whose water vanilla ALREADY freezes on its own, so those
 * are untouched and only the latitude-blind warm columns carried into the deep polar cap are corrected. It
 * stays poleward of {@link PolarPrecipitationRule#FORCE_SNOW_DEG} (75).
 */
public final class PolarWaterFreezeRule {

    private PolarWaterFreezeRule() {
    }

    /**
     * Latitude (deg) at/above which any exposed, genuinely-freezable water column is forced eligible to
     * freeze into ice regardless of its (possibly latitude-blind) biome temperature. 85 deg is this rule's OWN
     * literal anchor -- it is NOT derived from {@code PolarHazardWindow.AMBIENT_ONSET_DEG} (which B-7 S3 moved
     * to 82 and S8 to 80). Because freezing water places ice (a world modification), this worldgen-facing threshold must never
     * move under a client-atmosphere change; see the class javadoc.
     */
    public static final double FREEZE_ALL_DEG = 85.0;

    /**
     * Should an exposed water column be forced eligible to freeze at this latitude?
     *
     * <p>This answers ONLY the latitude question. The caller (the {@code Biome.shouldFreeze} hook) still
     * defers every "is there actually freezable exposed water here" check to vanilla, so a {@code true}
     * here never fabricates ice on a non-water column -- it only removes the biome-temperature veto.
     *
     * @param isGlobeWorld true only on a Latitude globe world; non-globe (vanilla/other) worlds are never
     *                     overridden, so this returns false and vanilla freezing is untouched.
     * @param latDeg       signed OR absolute latitude in degrees; magnitude is taken internally so both
     *                     hemispheres (e.g. +88 and -88) behave identically. NaN -> false.
     * @return true iff on a globe world and {@code |latDeg| >= FREEZE_ALL_DEG}.
     */
    public static boolean freezesWater(boolean isGlobeWorld, double latDeg) {
        if (!isGlobeWorld || Double.isNaN(latDeg)) {
            return false;
        }
        return Math.abs(latDeg) >= FREEZE_ALL_DEG;
    }
}
