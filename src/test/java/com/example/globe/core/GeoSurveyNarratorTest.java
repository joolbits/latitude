package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario tests for {@link GeoSurveyNarrator}: each builds a synthetic column that clearly matches one
 * geography/climate archetype and asserts the key cause-and-effect phrase shows up in the briefing.
 * Pure — no GeoAuthority/ClimateAuthority run needed.
 */
class GeoSurveyNarratorTest {

    private static final int R = 10000;

    private static String joined(List<String> lines) {
        return String.join("\n", lines).toLowerCase(Locale.ROOT);
    }

    /** Convenience builder with sensible neutral defaults; each test overrides the fields it cares about. */
    private static GeoSurveyNarrator.Input mk(
            double latitudeDeg, String band, String climateClass,
            double land01, boolean isOcean, double coastDist,
            double mtn, double rugged, double arc, double shelf, double archipelago,
            double temp, double precip, double cont, double windX, double windZ,
            double fetch, double lift, double shadow, double alt, double current,
            double ringMtnMax, double ringOceanFrac) {
        return new GeoSurveyNarrator.Input(
                latitudeDeg, true, band, climateClass,
                land01, isOcean, coastDist, mtn, rugged, arc, shelf, archipelago,
                temp, precip, cont, windX, windZ, fetch, lift, shadow, alt, current,
                R, ringMtnMax, ringOceanFrac);
    }

    @Test
    void rainShadowDesert() {
        // Temperate land in the lee of a ridge: strong rain shadow, low precip.
        GeoSurveyNarrator.Input in = mk(
                40, "TEMPERATE", "COLD_STEPPE",
                1.0, false, 3000, 0.2, 0.3, 0.0, 0.0, 0.0,
                0.5, 0.15, 0.5, 1.0, 0.0,
                0.0, 0.0, 0.7, 0.0, 0.0,
                0.7, 0.0);
        String out = joined(GeoSurveyNarrator.narrate(in));
        assertTrue(out.contains("rain shadow"), out);
        assertTrue(out.contains("upwind"), out);
    }

    @Test
    void windwardWetCoast() {
        // Coastal temperate slope facing a long ocean fetch: heavy windward rain.
        GeoSurveyNarrator.Input in = mk(
                45, "TEMPERATE", "TEMPERATE_OCEANIC",
                0.9, false, 400, 0.5, 0.5, 0.0, 0.0, 0.0,
                0.5, 0.85, 0.2, -1.0, 0.0,
                0.4 * R, 0.7, 0.0, 0.0, 0.0,
                0.5, 0.25);
        String out = joined(GeoSurveyNarrator.narrate(in));
        assertTrue(out.contains("soaked") || out.contains("climb these slopes"), out);
        // wind blows toward -X (west) so the seaward air arrives from the east
        assertTrue(out.contains("from the east"), out);
    }

    @Test
    void convergentMountains() {
        // High mountain intent + neighbouring high ground = young, rising belt where plates converge.
        GeoSurveyNarrator.Input in = mk(
                35, "TEMPERATE", "HUMID_CONTINENTAL",
                1.0, false, 2500, 0.85, 0.8, 0.0, 0.0, 0.0,
                0.5, 0.5, 0.5, 1.0, 0.0,
                0.0, 0.0, 0.0, 0.5, 0.0,
                0.8, 0.0);
        String out = joined(GeoSurveyNarrator.narrate(in));
        assertTrue(out.contains("converging") || out.contains("young"), out);
        assertTrue(out.contains("long range"), out); // ring context fired
    }

    @Test
    void deepContinentalInterior() {
        // Far inland, high continentality, dry: interior desert story + interior context.
        GeoSurveyNarrator.Input in = mk(
                30, "SUBTROPICAL", "HOT_DESERT",
                1.0, false, 6000, 0.1, 0.1, 0.0, 0.0, 0.0,
                0.8, 0.1, 0.9, 1.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0,
                0.1, 0.0);
        String out = joined(GeoSurveyNarrator.narrate(in));
        assertTrue(out.contains("continental interior"), out);
        assertTrue(out.contains("rarely") || out.contains("little rain"), out);
    }

    @Test
    void openOceanBasin() {
        // Deep ocean column: open-ocean basin + mild/damp air.
        GeoSurveyNarrator.Input in = mk(
                10, "TROPICAL", "OCEAN_WARM",
                0.0, true, -2000, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.9, 0.7, 0.0, 1.0, 0.0,
                0.3 * R, 0.0, 0.0, 0.0, 0.0,
                0.0, 1.0);
        String out = joined(GeoSurveyNarrator.narrate(in));
        assertTrue(out.contains("open-ocean basin"), out);
        assertTrue(out.contains("mild") && out.contains("damp"), out);
    }

    @Test
    void alpineCold() {
        // High-altitude cooling on land: altitude note must appear, colder than latitude implies.
        GeoSurveyNarrator.Input in = mk(
                20, "TROPICAL", "TEMPERATE_OCEANIC",
                1.0, false, 2000, 0.8, 0.7, 0.0, 0.0, 0.0,
                0.35, 0.6, 0.4, 1.0, 0.0,
                0.0, 0.0, 0.0, 0.7, 0.0,
                0.8, 0.0);
        String out = joined(GeoSurveyNarrator.narrate(in));
        assertTrue(out.contains("altitude keeps it colder"), out);
        assertTrue(out.contains("tropics"), out); // latitude belt line still present
    }

    @Test
    void islandArcOverOcean() {
        // Ocean column with a strong subduction-arc signal.
        GeoSurveyNarrator.Input in = mk(
                15, "TROPICAL", "OCEAN_WARM",
                0.1, true, -300, 0.0, 0.0, 0.6, 0.3, 0.2,
                0.9, 0.7, 0.0, 1.0, 0.0,
                0.3 * R, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.75);
        String out = joined(GeoSurveyNarrator.narrate(in));
        assertTrue(out.contains("island arc"), out);
        assertTrue(out.contains("trench") && out.contains("plate dives"), out);
    }

    @Test
    void briefingLengthIsBounded() {
        GeoSurveyNarrator.Input in = mk(
                40, "TEMPERATE", "COLD_STEPPE",
                1.0, false, 3000, 0.2, 0.3, 0.0, 0.0, 0.0,
                0.5, 0.15, 0.5, 1.0, 0.0,
                0.0, 0.0, 0.7, 0.6, 0.0,
                0.7, 0.0);
        List<String> lines = GeoSurveyNarrator.narrate(in);
        assertTrue(lines.size() >= 5 && lines.size() <= 9, "lines=" + lines.size());
        for (String l : lines) {
            assertFalse(l.isBlank(), "blank line");
        }
    }
}
