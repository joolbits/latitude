package com.example.globe.core;

/**
 * Feature flags gating the Phase 2 (GeoAuthority), Phase 3 (ClimateAuthority), and Biome Consumer
 * slices of the Latitude 2.0 overhaul. All default to {@code false}.
 *
 * <p>{@link #GEO_V2_ENABLED} / {@link #CLIMATE_V2_ENABLED} gate whether each authority is
 * constructed and sampled at all (Phase 2/3: computed-and-discarded). {@link #BIOME_CONSUMER_V2_ENABLED}
 * is a separate, later flag: it gates whether the computed summaries actually CHANGE biome selection.
 * Kept distinct on purpose -- the geoV2/climateV2 flags have an established, tested contract from
 * Phases 2-3 ("computed and discarded, zero biome change"); overloading them to also mean "and drives
 * biomes" would silently break that contract. The consumer flag depends on its authority's flag also
 * being on (if geoV2 is off, there is no GeoSummary to consume no matter what this flag says).
 *
 * <p>{@link #BIOME_CONSUMER_V2_OCEAN_AUTHORITY_ENABLED} is a further, independently-gated sub-flag
 * (2026-07-04). The Biome Consumer slice's proof gate found that letting GeoAuthority's
 * {@code isOceanIntent} replace {@code OceanDistanceField} collapses live land fraction from
 * GeoAuthority's own calibrated ~39% to ~13% (see docs/binder/biome-consumer-slice-20260704.md) --
 * a terrain-integration gap (Phase 4), not a GeoAuthority miscalibration. Rather than gate that known-bad
 * behavior behind a code comment saying "don't flip this yet," it gets its OWN flag: with
 * {@code BIOME_CONSUMER_V2_ENABLED=true} alone, only the proven-safe ClimateAuthority land-family reroll
 * is active; the ocean-authority swap additionally requires this flag, so it cannot be enabled by
 * accident and stays clearly walled off until Phase 4 (or an explicit decision to revisit the ocean
 * composition logic) makes it safe to turn on by default.
 *
 * <p>Pure Java, no Minecraft imports -- this class belongs to the Core Logic layer per
 * {@code docs/porting/PORTABILITY_ARCHITECTURE.md}.
 */
public final class LatitudeV2Flags {

    public static final boolean GEO_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.geoV2.enabled", "false"));

    public static final boolean CLIMATE_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.climateV2.enabled", "false"));

    public static final boolean BIOME_CONSUMER_V2_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.biomeConsumerV2.enabled", "false"));

    public static final boolean BIOME_CONSUMER_V2_OCEAN_AUTHORITY_ENABLED =
            Boolean.parseBoolean(System.getProperty("latitude.biomeConsumerV2.oceanAuthority.enabled", "false"));

    private LatitudeV2Flags() {
    }
}
