package com.example.globe.core.climate;

/**
 * Annual-rhythm / dry-wet-timing taxonomy (NOT annual mean). Per Phase 3 hard rule 2, {@link #MONSOON}
 * is a seasonality CLASS — it shapes the dry/wet season split, never raises {@code precipitation01}.
 * Stored in {@link ClimateSummary#seasonalityClass()} as {@link #key()} (lowercase).
 */
public enum SeasonalityClass {
    EQUATORIAL,
    MONSOON,
    MEDITERRANEAN,
    OCEANIC,
    CONTINENTAL,
    SUBPOLAR,
    POLAR,
    TROPICAL_WETDRY,
    SEASONAL,
    MARITIME;

    /** Lowercase display/storage key (e.g. "tropical_wetdry"). */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
