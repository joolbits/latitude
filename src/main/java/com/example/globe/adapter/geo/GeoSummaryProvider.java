package com.example.globe.adapter.geo;

import com.example.globe.core.geo.GeoSummary;

/**
 * Bridges a {@code (blockX, blockZ)} column to a {@link GeoSummary}. This is the seam Phase 2
 * (GeoAuthority Prototype) implements for real; Phase 0 only wires the no-op call site so the
 * seam exists and compiles cleanly while {@code latitude.geoV2.enabled} stays {@code false}.
 */
public interface GeoSummaryProvider {

    GeoSummary summarize(int blockX, int blockZ);
}
