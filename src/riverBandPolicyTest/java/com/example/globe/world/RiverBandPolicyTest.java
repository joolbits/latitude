package com.example.globe.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rivers wear the band they flow through (maintainer ruling, 2026-08-16).
 *
 * <p>Reported from live play: a river at 29 degrees south read "Tropical River" through badlands.
 * The warm river pool served BOTH the tropical and subtropical bands, so a pack's tropical river
 * could surface anywhere below the temperate line. The subtropical band now draws from its own
 * pool, which deliberately excludes tropical-declared rivers; the tropical band keeps the full
 * warm pool, and temperate/frozen behavior is untouched.
 */
public final class RiverBandPolicyTest {

    public static void main(String[] args) throws Exception {
        subtropicalPoolExistsAndExcludesTropicalRivers();
        tropicalPoolKeepsItsTropicalRiver();
        bothPickSitesSplitTheBands();
        System.out.println("RIVER_BAND_POLICY_TEST_PASS");
    }

    private static void subtropicalPoolExistsAndExcludesTropicalRivers() throws IOException {
        Path tag = Path.of(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_river_subtropical.json");
        assertTrue(Files.exists(tag), "the subtropical river pool tag must exist");
        String json = normalize(Files.readString(tag));
        assertTrue(json.contains("minecraft:river"),
                "vanilla river anchors the subtropical pool");
        assertTrue(json.contains("clifftree:warm_river"),
                "the warm (non-tropical) pack river stays admitted in the subtropics");
        assertTrue(!json.contains("tropical_river"),
                "no tropical-declared river may appear in the subtropical pool — that is the "
                        + "reported defect");
        assertTrue(json.contains("\"required\": false") || json.contains("\"required\":false"),
                "pack rivers remain optional so a pack-free world still loads");
    }

    private static void tropicalPoolKeepsItsTropicalRiver() throws IOException {
        String json = normalize(Files.readString(Path.of(
                "src/main/resources/data/globe/tags/worldgen/biome/lat_river_warm.json")));
        assertTrue(json.contains("clifftree:tropical_river"),
                "the tropical band keeps its tropical river — the split narrows the subtropics, "
                        + "it does not evict the biome from the world");
        assertTrue(json.contains("minecraft:river"),
                "vanilla river still anchors the tropical pool");
    }

    private static void bothPickSitesSplitTheBands() throws IOException {
        String biomes = normalize(Files.readString(Path.of(
                "src/main/java/com/example/globe/world/LatitudeBiomes.java")));
        assertTrue(biomes.contains(
                        "Identifier.fromNamespaceAndPath(\"globe\", \"lat_river_subtropical\")"),
                "the subtropical pool tag key must be declared");
        String split = "blendedBandIndex == BAND_TROPICAL ? LAT_RIVER_WARM"
                + " : blendedBandIndex == BAND_SUBTROPICAL ? LAT_RIVER_SUBTROPICAL"
                + " : LAT_RIVER_TEMPERATE";
        assertEquals(2, occurrences(biomes, split),
                "BOTH river pick sites (registry and collection twins) must split tropical from "
                        + "subtropical — a one-sided change reintroduces the drift the twins "
                        + "exist to prevent");
        assertEquals(0, occurrences(biomes, "<= BAND_SUBTROPICAL ? LAT_RIVER_WARM"),
                "the old shared-pool condition must be gone from every site");
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
