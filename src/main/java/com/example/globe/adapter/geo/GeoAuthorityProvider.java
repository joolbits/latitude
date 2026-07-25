package com.example.globe.adapter.geo;

import com.example.globe.core.geo.GeoAuthority;
import com.example.globe.core.geo.GeoSummary;

/**
 * Real {@link GeoSummaryProvider} backed by a per-world {@link GeoAuthority} (Phase 2). Wired at the
 * flag-gated call site in {@code LatitudeBiomes.pick} when {@code latitude.geoV2.enabled=true};
 * replaces {@code NoOpGeoSummaryProvider}. In Phase 2 the summary is still computed-and-discarded
 * (GeoAuthority is measured via the offline Atlas tool, not yet driving biome selection), so with the
 * flag at its default {@code false} worldgen output is byte-identical.
 */
public final class GeoAuthorityProvider implements GeoSummaryProvider {

    private final GeoAuthority authority;

    public GeoAuthorityProvider(GeoAuthority authority) {
        this.authority = authority;
    }

    public GeoAuthority authority() {
        return authority;
    }

    @Override
    public GeoSummary summarize(int blockX, int blockZ) {
        return authority.sample(blockX, blockZ);
    }
}
