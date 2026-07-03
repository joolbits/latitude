package com.example.globe.adapter;

import com.example.globe.adapter.climate.NoOpClimateSummaryProvider;
import com.example.globe.adapter.geo.NoOpGeoSummaryProvider;
import com.example.globe.core.climate.ClimateSummary;
import com.example.globe.core.climate.LatitudeBand;
import com.example.globe.core.geo.GeoSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * These two provider implementations are the only ones wired into
 * {@code LatitudeBiomes.pick()}'s disabled-by-default call sites (Phase 0). They must stay
 * neutral/no-op until Phase 2 (GeoAuthority) and Phase 3 (ClimateAuthority) implement real
 * algorithms behind their respective flags.
 */
class NoOpProvidersTest {

    @Test
    void geoProviderAlwaysReturnsNeutral() {
        GeoSummary summary = NoOpGeoSummaryProvider.INSTANCE.summarize(123, -456);
        assertSame(GeoSummary.NEUTRAL, summary);
    }

    @Test
    void climateProviderAlwaysReturnsNeutralForGivenLatitudeAndBand() {
        ClimateSummary summary = NoOpClimateSummaryProvider.INSTANCE.summarize(51.0, LatitudeBand.SUBPOLAR);
        assertEquals(51.0, summary.latitudeDeg());
        assertEquals(LatitudeBand.SUBPOLAR, summary.band());
        assertEquals(0.0, summary.temperature01());
    }
}
