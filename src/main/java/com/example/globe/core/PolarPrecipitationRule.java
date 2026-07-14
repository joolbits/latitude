package com.example.globe.core;

/**
 * Pure decision for the "no RAIN at the poles" correctness rule (Core Logic layer, zero Minecraft
 * imports, unit-testable in a plain JVM).
 *
 * <p><b>The bug.</b> Standing at 90 deg latitude over ice-spike terrain, Peetsa saw vanilla RAIN
 * streaks. Vanilla picks rain-vs-snow per COLUMN via {@code ClientLevel.getPrecipitationAt(pos) ->
 * Biome.getPrecipitationAt(pos, seaLevel)}, which keys off the biome's precipitation type + a
 * temperature/height check. Ice spikes are SNOW-only (temperature 0.0), so the rain is NOT coming
 * from ice spikes: it is coming from the LATITUDE-BLIND columns the vanilla noise router places
 * regardless of our latitude bands -- {@code river} and {@code ocean} both have temperature 0.5 and
 * precipitation RAIN, and {@code LatitudeBiomeSource} never re-classifies them, so a river/ocean
 * column at the pole reports RAIN. The weather renderer samples a whole grid of columns around the
 * player (weatherRadius), so even standing on snow, a nearby polar river/ocean renders rain.
 *
 * <p><b>The rule.</b> At extreme latitudes precipitation must always read as SNOW, never rain,
 * biome-independent. This decision is applied at the single client chokepoint
 * {@code ClientLevel.getPrecipitationAt} (see {@code ClientLevelPolarSnowMixin}), which governs
 * ALL three vanilla rain surfaces at once: the falling rain STREAKS ({@code WeatherEffectRenderer}),
 * the ground SPLASH particles, and the rain SOUND ({@code WEATHER_RAIN}/{@code WEATHER_RAIN_ABOVE})
 * -- all three query this one method for RAIN vs SNOW.
 *
 * <p><b>Threshold = 75 deg (its OWN client anchor).</b> Chosen to sit comfortably poleward of any latitude
 * where vanilla rain is plausible: subpolar taiga (the rainiest cold band) ends at {@code SUBPOLAR_MAX_DEG} =
 * 67 deg, so rain at 70 deg is arguably fine and NOT force-converted. 75 deg is well inside the polar cap and
 * still equatorward of the client ambient snow/fog onset (B-7 S3 moved
 * {@code PolarHazardWindow.AMBIENT_ONSET_DEG} 85 -> 82; this rule keeps its own literal 75 and did NOT move
 * with it). This rule is PURE CLIENT ATMOSPHERE (it only swaps rain rendering for snow rendering -- it places
 * no blocks), so it carries no worldgen-seam risk; it is decoupled here purely so the two anchors stay legible
 * independently. The clamp simply guarantees that whatever precipitation renders across the cap is snow.
 */
public final class PolarPrecipitationRule {

    private PolarPrecipitationRule() {
    }

    /**
     * Latitude (deg) at/above which any RAIN column is forced to render as SNOW. Poleward of the
     * subpolar rain-plausible band ({@code SUBPOLAR_MAX_DEG} ~= 67) and well equatorward of the
     * 85 deg ambient storm window, so it covers the whole polar cap without overlapping either.
     */
    public static final double FORCE_SNOW_DEG = 75.0;

    /**
     * Should a column's precipitation be forced from RAIN to SNOW at this latitude?
     *
     * @param isGlobeWorld true only on a Latitude globe world; non-globe (vanilla/other) worlds are
     *                     never overridden, so this returns false and vanilla weather is untouched.
     * @param latDeg       signed OR absolute latitude in degrees; magnitude is taken internally so
     *                     both hemispheres (e.g. +80 and -80) behave identically. NaN -> false.
     * @return true iff on a globe world and {@code |latDeg| >= FORCE_SNOW_DEG}.
     */
    public static boolean forcesSnow(boolean isGlobeWorld, double latDeg) {
        if (!isGlobeWorld || Double.isNaN(latDeg)) {
            return false;
        }
        return Math.abs(latDeg) >= FORCE_SNOW_DEG;
    }
}
