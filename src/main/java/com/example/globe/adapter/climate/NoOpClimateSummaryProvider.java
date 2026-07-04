package com.example.globe.adapter.climate;

import com.example.globe.core.climate.ClimateSummary;
import com.example.globe.core.climate.LatitudeBand;

/**
 * Always returns a neutral {@link ClimateSummary}. The only implementation in use while
 * {@code latitude.climateV2.enabled} is {@code false}.
 */
public final class NoOpClimateSummaryProvider implements ClimateSummaryProvider {

    public static final NoOpClimateSummaryProvider INSTANCE = new NoOpClimateSummaryProvider();

    private NoOpClimateSummaryProvider() {
    }

    @Override
    public ClimateSummary summarize(int blockX, int blockZ) {
        return ClimateSummary.neutral(0.0, LatitudeBand.TROPICAL);
    }
}
