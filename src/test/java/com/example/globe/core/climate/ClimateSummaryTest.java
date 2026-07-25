package com.example.globe.core.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClimateSummaryTest {

    @Test
    void neutralPreservesGivenLatitudeAndBand() {
        ClimateSummary summary = ClimateSummary.neutral(42.5, LatitudeBand.TEMPERATE);
        assertEquals(42.5, summary.latitudeDeg());
        assertEquals(LatitudeBand.TEMPERATE, summary.band());
    }

    @Test
    void neutralZerosEveryClimateSignal() {
        ClimateSummary summary = ClimateSummary.neutral(0.0, LatitudeBand.TROPICAL);
        assertEquals(0.0, summary.temperature01());
        assertEquals(0.0, summary.altitudeCooling01());
        assertEquals(0.0, summary.continentality01());
        assertEquals(0.0, summary.prevailingWindX());
        assertEquals(0.0, summary.prevailingWindZ());
        assertEquals(0.0, summary.upwindOceanFetchBlocks());
        assertEquals(0.0, summary.precipitation01());
        assertEquals(0.0, summary.windwardLift01());
        assertEquals(0.0, summary.rainShadow01());
        assertEquals(0.0, summary.currentModifierSigned());
    }

    @Test
    void neutralLeavesClassificationUnassigned() {
        ClimateSummary summary = ClimateSummary.neutral(10.0, LatitudeBand.SUBTROPICAL);
        assertEquals("", summary.seasonalityClass());
        assertEquals("", summary.climateClass());
        assertTrue(summary.diagnosticFlags().isEmpty());
    }

    @Test
    void allFiveLatitudeBandsExist() {
        assertEquals(5, LatitudeBand.values().length);
    }
}
