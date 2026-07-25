package com.example.globe.adapter.climate;

import com.example.globe.core.climate.ClimateAuthority;
import com.example.globe.core.climate.ClimateSummary;

/**
 * Real {@link ClimateSummaryProvider} backed by a per-world {@link ClimateAuthority} (Phase 3). Wired
 * at the flag-gated call site in {@code LatitudeBiomes.pick} when {@code latitude.climateV2.enabled=true};
 * replaces {@code NoOpClimateSummaryProvider}. In Phase 3 the summary is still computed-and-discarded
 * (climate is measured via the offline Atlas tool, not yet driving biome selection), so with the flag at
 * its default {@code false} worldgen output is byte-identical.
 */
public final class ClimateAuthorityProvider implements ClimateSummaryProvider {

    private final ClimateAuthority authority;

    public ClimateAuthorityProvider(ClimateAuthority authority) {
        this.authority = authority;
    }

    public ClimateAuthority authority() {
        return authority;
    }

    @Override
    public ClimateSummary summarize(int blockX, int blockZ) {
        return authority.sample(blockX, blockZ);
    }
}
