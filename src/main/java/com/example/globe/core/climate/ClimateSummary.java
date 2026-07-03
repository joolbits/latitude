package com.example.globe.core.climate;

import java.util.List;

/**
 * Shared climate summary for a single {@code (blockX, blockZ)} column, per the field contract
 * in {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 *
 * <p>This is a Phase 0 no-op contract: the type exists so Platform Adapter and Mixin Hook code
 * can be written against a stable shape, but nothing populates it with real wind/ocean/rain-shadow
 * intent yet -- that is Phase 3 (ClimateAuthority Prototype) work, gated behind
 * {@link com.example.globe.core.LatitudeV2Flags#CLIMATE_V2_ENABLED}. {@code seasonalityClass} and
 * {@code climateClass} are left as free-form strings (empty by default) rather than enums,
 * because Phase 3 is the phase that designs that taxonomy -- Phase 0 must not pre-decide it.
 *
 * <p>Pure Java, no Minecraft imports -- Core Logic layer.
 */
public record ClimateSummary(
        double latitudeDeg,
        LatitudeBand band,
        double temperature01,
        double altitudeCooling01,
        double continentality01,
        double prevailingWindX,
        double prevailingWindZ,
        double upwindOceanFetchBlocks,
        double precipitation01,
        double windwardLift01,
        double rainShadow01,
        double currentModifier01,
        String seasonalityClass,
        String climateClass,
        List<String> diagnosticFlags
) {

    /** Neutral/no-op summary: zero signal on every axis, no seasonality/climate class assigned. */
    public static ClimateSummary neutral(double latitudeDeg, LatitudeBand band) {
        return new ClimateSummary(
                latitudeDeg, band, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                "", "", List.of());
    }
}
