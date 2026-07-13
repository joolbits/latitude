package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void passageV2DefaultsToEnabled() {
        // Peetsa's post-P3 decision (2026-07-12): the Hemisphere Passage ships ON. It is consensual by
        // design (prompt-gated crossing) and was live-approved; the flag remains the kill switch.
        assertTrue(LatitudeV2Flags.PASSAGE_V2_ENABLED,
                "The Hemisphere Passage ships default-on per Peetsa's post-P3 decision");
    }
}
