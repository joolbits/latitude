package com.example.globe.core;

/**
 * Feature flags gating the Phase 2 (GeoAuthority) and Phase 3 (ClimateAuthority) portability
 * scaffolding added in Phase 0. Both default to {@code false}; flipping either on is out of
 * scope for Phase 0 and is reserved for the phases that implement real geography/climate
 * algorithms behind them.
 *
 * <p>Pure Java, no Minecraft imports -- this class belongs to the Core Logic layer per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 */
public final class LatitudeV2Flags {

    public static final boolean GEO_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.geoV2.enabled", "false"));

    public static final boolean CLIMATE_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.climateV2.enabled", "false"));

    private LatitudeV2Flags() {
    }
}
