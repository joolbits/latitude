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
 * <p><b>Threshold = 85 deg.</b> Matches {@code PolarHazardWindow.AMBIENT_ONSET_DEG} (85), so "the water is
 * frozen" turns on at exactly the latitude where the blizzard/snow/fog ambience begins -- the visible
 * storm and the frozen ground read as one coherent polar cap. It also sits poleward of the naturally-cold
 * bands (subpolar taiga / snowy) whose water vanilla ALREADY freezes on its own, so those are untouched and
 * only the latitude-blind warm columns carried into the deep polar cap are corrected. This is deliberately
 * poleward of {@link PolarPrecipitationRule#FORCE_SNOW_DEG} (75): the precipitation clamp wants to sit a
 * safe margin BELOW the ambient window, whereas frozen water is meant to coincide WITH it.
 */
public final class PolarWaterFreezeRule {

    private PolarWaterFreezeRule() {
    }

    /**
     * Latitude (deg) at/above which any exposed, genuinely-freezable water column is forced eligible to
     * freeze into ice regardless of its (possibly latitude-blind) biome temperature. 85 deg mirrors
     * {@code PolarHazardWindow.AMBIENT_ONSET_DEG}, aligning frozen water with the snow/fog/wind onset.
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
