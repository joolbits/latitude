package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LatitudeV2FlagsTest {

    @Test
    void geoV2DefaultsToDisabled() {
        assertFalse(LatitudeV2Flags.GEO_V2_ENABLED,
                "Phase 0 must ship with GeoAuthority v2 disabled by default");
    }

    @Test
    void climateV2DefaultsToDisabled() {
        assertFalse(LatitudeV2Flags.CLIMATE_V2_ENABLED,
                "Phase 0 must ship with ClimateAuthority v2 disabled by default");
    }

    @Test
    void biomeConsumerV2DefaultsToDisabled() {
        assertFalse(LatitudeV2Flags.BIOME_CONSUMER_V2_ENABLED,
                "Biome Consumer slice must ship with the consumer flag disabled by default");
    }

    @Test
    void biomeConsumerV2OceanAuthorityDefaultsToDisabled() {
        assertFalse(LatitudeV2Flags.BIOME_CONSUMER_V2_OCEAN_AUTHORITY_ENABLED,
                "The known-land-fraction-collapse ocean-authority swap must stay off by default, "
                        + "independent of the consumer flag, until Phase 4 or a redesign fixes it");
    }

    @Test
    void carveAwareLabelsDefaultsToDisabled() {
        assertFalse(LatitudeV2Flags.TERRAIN_V2_CARVE_AWARE_LABELS,
                "Phase 5 carve-aware ocean labels must ship default-off (byte-identical flag-off) "
                        + "until the atlas proof gate passes");
    }

    @Test
    void passageV2DefaultsToDisabled() {
        assertFalse(LatitudeV2Flags.PASSAGE_V2_ENABLED,
                "Phase 5 B-5 Hemisphere Passage must ship default-off (inert edge behavior) until P3 live "
                        + "and a Peetsa default-on decision");
    }
}
