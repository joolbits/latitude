package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // S21c DECOUPLED the Barrens onset from the veg-fade finish: the onset is now an independent 82 deg
        // (the owner's chosen value) while the vegetation fade finishes at 80 ("veg to 80"). The 80-82 band is
        // bare tundra before the Barrens biome claims the land. Full-dominance defaults to 84 (fray 82->84).
        //
        // INIT-ORDER-PROOF (S21c small-fix round): the constants below are static-final, captured from
        // System.getProperty ONCE at class-init (the LatitudeV2Flags testing note) -- so this test asserts
        // the CAPTURED CONSTANTS directly, never a re-read sysprop. That is order-independent as long as the
        // suite JVM never carries these -D keys; the guards below make any future pollution (a gradle test-JVM
        // -D, or a test that setProperty's mid-suite -- both forbidden) fail LOUDLY with the cause named,
        // instead of flaking the literal assertions by class-load order.
        assertNull(System.getProperty("latitude.polarBarrens.onsetDeg"),
                "suite JVM must not carry -Dlatitude.polarBarrens.onsetDeg (static-init capture: defaults law)");
        assertNull(System.getProperty("latitude.polarBarrens.fullDeg"),
                "suite JVM must not carry -Dlatitude.polarBarrens.fullDeg (static-init capture: defaults law)");
        assertNull(System.getProperty("latitude.polarVegetationFade.fullDeg"),
                "suite JVM must not carry -Dlatitude.polarVegetationFade.fullDeg (static-init capture: defaults law)");
        assertEquals(82.0, LatitudeV2Flags.POLAR_BARRENS_ONSET_DEG, 1e-9,
                "Barrens onset is an independent literal 82 deg (S21c decoupled it from the veg-fade finish)");
        assertNotEquals(PolarVegetationFade.FULL_DEG, LatitudeV2Flags.POLAR_BARRENS_ONSET_DEG,
                "S21c: the coupling is BROKEN -- the veg fade finishes at 80, the Barrens onset stays 82");
        assertEquals(80.0, PolarVegetationFade.FULL_DEG, 1e-9,
                "veg fade now finishes at 80 (S21c 'veg to 80')");
        assertEquals(84.0, LatitudeV2Flags.POLAR_BARRENS_FULL_DEG, 1e-9,
                "Barrens full-dominance latitude defaults to 84 deg (S13: onset+2, fray held at +2)");
    }

    @Test
    void solarTiltBranchLocalFlightStaging() {
        // P3 LIVE-TEST STAGING (branch-local): default ON for the TEST 101 first sun flight.
        // REVISIT BEFORE MERGE — ship default is the owner's post-flight call; pre-flight law was
        // default-OFF byte-identical (sweep #8 verified all three injection surfaces gated).
        assertTrue(LatitudeV2Flags.SOLAR_TILT_V2_ENABLED,
                "Branch-local flight staging: Solar Tilt ON for TEST 101; revisit before merge");
    }

    @Test
    void solarTiltDialDefaults() {
        assertEquals(30.0, LatitudeV2Flags.SOLAR_TILT_DELTA_MAX_DEG, 1e-9,
                "δ_max defaults to 30° (onset at a visible 60°)");
        assertEquals(360.0, LatitudeV2Flags.SOLAR_TILT_YEAR_LENGTH_DAYS, 1e-9,
                "year length defaults to 360 game-days (180 between solstices)");
        assertEquals(74.5, LatitudeV2Flags.SOLAR_TILT_FUNCTIONAL_MIN_DEG, 1e-9,
                "functional floor defaults to 74.5° (A2 no-villages line)");
        assertEquals(0.0, LatitudeV2Flags.SOLAR_TILT_FROZEN_PHASE_DEG, 1e-9,
                "frozen phase defaults to 0° (permanent northern summer)");
        // the dials match the pure-kernel constants they mirror
        assertEquals(SolarTilt.DEFAULT_DELTA_MAX_DEG, LatitudeV2Flags.SOLAR_TILT_DELTA_MAX_DEG, 1e-9,
                "flag default mirrors SolarTilt.DEFAULT_DELTA_MAX_DEG");
        assertEquals(SolarTilt.DEFAULT_YEAR_LENGTH_DAYS, LatitudeV2Flags.SOLAR_TILT_YEAR_LENGTH_DAYS, 1e-9,
                "flag default mirrors SolarTilt.DEFAULT_YEAR_LENGTH_DAYS");
    }

    @Test
    void polarOutfittingDefaultsToDisabled() {
        // B-10 master flag ships default-OFF: flag-off routes the LEGACY leather path (full negation at 4), so
        // leather demotes ONLY when the suit ships -- never a protection gap (the sequencing / no-gap law).
        assertFalse(LatitudeV2Flags.POLAR_OUTFITTING_ENABLED,
                "Polar Outfitting must ship default-off (legacy leather path, no protection gap)");
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
