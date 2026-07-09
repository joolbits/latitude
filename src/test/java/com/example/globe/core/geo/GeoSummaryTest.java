package com.example.globe.core.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GeoSummaryTest {

    @Test
    void neutralHasNoLandOrOceanIntent() {
        assertEquals(0.0, GeoSummary.NEUTRAL.land01());
        assertFalse(GeoSummary.NEUTRAL.isOceanIntent());
    }

    @Test
    void neutralLeavesAllIdsUnassigned() {
        assertEquals(-1, GeoSummary.NEUTRAL.continentId());
        assertEquals(-1, GeoSummary.NEUTRAL.oceanBasinId());
        assertEquals(-1, GeoSummary.NEUTRAL.orogenId());
        assertEquals(-1, GeoSummary.NEUTRAL.drainageBasinId());
        assertEquals(-1, GeoSummary.NEUTRAL.coastOutletId());
    }

    @Test
    void neutralZerosAllIntentFields() {
        assertEquals(0.0, GeoSummary.NEUTRAL.coastDistanceBlocks());
        assertEquals(0.0, GeoSummary.NEUTRAL.shelf01());
        assertEquals(0.0, GeoSummary.NEUTRAL.islandArc01());
        assertEquals(0.0, GeoSummary.NEUTRAL.archipelago01());
        assertEquals(0.0, GeoSummary.NEUTRAL.mountainIntent01());
        assertEquals(0.0, GeoSummary.NEUTRAL.ruggednessIntent01());
        assertEquals(0.0, GeoSummary.NEUTRAL.projectionEdgeSuitability01());
        assertEquals(0.0, GeoSummary.NEUTRAL.flowDirection());
    }

    @Test
    void isPureDataNoMinecraftDependency() {
        // Compile-time proof: this test module has no Minecraft classpath at all, so the mere
        // fact that GeoSummary compiles here proves it is a Core Logic type.
        GeoSummary custom = new GeoSummary(0.5, true, 3, 4, 120.0, 0.2, 0.0, 0.0, 0.8, 7, 0.6, 0.1, 9, 1.57, 2, 0.0);
        assertEquals(3, custom.continentId());
    }
}
