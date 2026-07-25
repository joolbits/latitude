package com.example.globe.adapter.geo;

import com.example.globe.core.geo.GeoSummary;

/**
 * Always returns {@link GeoSummary#NEUTRAL}. The only implementation in use while
 * {@code latitude.geoV2.enabled} is {@code false}.
 */
public final class NoOpGeoSummaryProvider implements GeoSummaryProvider {

    public static final NoOpGeoSummaryProvider INSTANCE = new NoOpGeoSummaryProvider();

    private NoOpGeoSummaryProvider() {
    }

    @Override
    public GeoSummary summarize(int blockX, int blockZ) {
        return GeoSummary.NEUTRAL;
    }
}
