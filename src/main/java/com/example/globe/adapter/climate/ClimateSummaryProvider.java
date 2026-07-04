package com.example.globe.adapter.climate;

import com.example.globe.core.climate.ClimateSummary;

/**
 * Bridges a {@code (blockX, blockZ)} column to a {@link ClimateSummary}. Phase 3 (ClimateAuthority)
 * implements this for real; the signature was refined from the Phase 0 {@code (latitudeDeg, band)}
 * shell to {@code (blockX, blockZ)} — parallel to {@code GeoSummaryProvider} — because the real model
 * derives latitude/band and consumes GeoAuthority geography internally from the column coords.
 */
public interface ClimateSummaryProvider {

    ClimateSummary summarize(int blockX, int blockZ);
}
