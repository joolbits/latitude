package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void polarBarrensBranchLocalFlightStaging() {
        // P3 LIVE-TEST STAGING (branch-local, B-6/B-7 precedent): default ON for the TEST 99 flight.
        // REVISIT BEFORE MERGE — shipped default is Peetsa's call after the live look; the pre-flight
        // law was default-OFF byte-identical (atlas gate 1 proved it).
        assertTrue(LatitudeV2Flags.POLAR_BARRENS_ENABLED,
                "Branch-local flight staging: Polar Barrens ON for TEST 99; revisit before merge");
    }

    @Test
    void polarBarrensDegreeDefaults() {
        // Onset defaults to the veg-fade finish (the owner's invisible seam); full defaults to 88.
        assertEquals(PolarVegetationFade.FULL_DEG, LatitudeV2Flags.POLAR_BARRENS_ONSET_DEG, 1e-9,
                "Barrens onset defaults to the vegetation-fade finish (KEEP-SHARED)");
        assertEquals(88.0, LatitudeV2Flags.POLAR_BARRENS_FULL_DEG, 1e-9,
                "Barrens full-dominance latitude defaults to 88 deg");
    }

    @Test
    void polePassageV2BranchLocalFlightStaging() {
        // P3 LIVE-TEST STAGING (branch-local, B-6 precedent): default ON so the TEST 97 maiden pole flight
        // exercises the crossing without profile JVM args. REVISIT BEFORE MERGE -- the shipped default
        // (and this test's direction) is Peetsa's call after P3; the pre-flight law was default-OFF
        // byte-identical (including NO Wide-world pole hard-stop clamp).
        assertTrue(LatitudeV2Flags.POLE_PASSAGE_V2_ENABLED,
                "Branch-local flight staging: pole passage ON for TEST 97; revisit before merge");
    }
}
