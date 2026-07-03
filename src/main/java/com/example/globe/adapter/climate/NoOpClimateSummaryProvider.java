package com.example.globe.adapter.climate;

import com.example.globe.core.climate.ClimateSummary;
import com.example.globe.core.climate.LatitudeBand;

/**
 * Always returns {@link ClimateSummary#neutral(double, LatitudeBand)}. The only implementation
 * in use while {@code latitude.climateV2.enabled} is {@code false}.
 */
public final class NoOpClimateSummaryProvider implements ClimateSummaryProvider {

    public static final NoOpClimateSummaryProvider INSTANCE = new NoOpClimateSummaryProvider();

    private NoOpClimateSummaryProvider() {
    }

    @Override
    public ClimateSummary summarize(double latitudeDeg, LatitudeBand band) {
        return ClimateSummary.neutral(latitudeDeg, band);
    }
}
