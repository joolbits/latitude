package com.example.globe.core.climate;

/**
 * The five latitude bands already established by {@code LatitudeBiomes}' {@code BAND_*}
 * constants (tropical=0 through polar=4). Reused here so {@link ClimateSummary#band()} names
 * the same bands the existing picker already reasons about, instead of inventing a second
 * taxonomy.
 *
 * <p>Pure Java, no Minecraft imports -- Core Logic layer.
 */
public enum LatitudeBand {
    TROPICAL,
    SUBTROPICAL,
    TEMPERATE,
    SUBPOLAR,
    POLAR
}
