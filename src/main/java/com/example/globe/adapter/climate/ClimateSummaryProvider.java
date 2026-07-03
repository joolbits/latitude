package com.example.globe.adapter.climate;

import com.example.globe.core.climate.ClimateSummary;
import com.example.globe.core.climate.LatitudeBand;

/**
 * Bridges a latitude/band pair to a {@link ClimateSummary}. This is the seam Phase 3
 * (ClimateAuthority Prototype) implements for real; Phase 0 only wires the no-op call site so
 * the seam exists and compiles cleanly while {@code latitude.climateV2.enabled} stays
 * {@code false}.
 */
public interface ClimateSummaryProvider {

    ClimateSummary summarize(double latitudeDeg, LatitudeBand band);
}
