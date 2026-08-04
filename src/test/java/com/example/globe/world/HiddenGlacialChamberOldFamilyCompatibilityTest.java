package com.example.globe.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Old-family compatibility gaps {@code HiddenGlacialChamberFeatureContractTest} and
 * {@code GlacialCavesBiomeJsonSchemaTest} do not already close: both retired features' {@code register()}
 * calls must still stand in {@code GlobeMod.java} (unwiring biome membership must never unwire the
 * registry-consistency call itself, or an old world's already-placed configured_feature JSON would hard-fail
 * to load), {@code globe:powder_crevasse_roof} must be absent from {@code glacial_caves.json} specifically
 * (its own home biome, {@code polar_barrens.json}, is already checked), and
 * {@code globe:hidden_glacial_chamber} must be absent from {@code polar_barrens.json} specifically (its
 * presence in {@code glacial_caves.json} is already checked). {@code globe:cave_drop_trap}'s absence from
 * both biome JSONs, and both retired features' JSON halves staying shipped, are already fully covered.
 */
class HiddenGlacialChamberOldFamilyCompatibilityTest {

    private static JsonObject load(String resourcePath) {
        InputStream stream = HiddenGlacialChamberOldFamilyCompatibilityTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int cursor = haystack.indexOf(needle);
        while (cursor >= 0) {
            count++;
            cursor = haystack.indexOf(needle, cursor + needle.length());
        }
        return count;
    }

    @Test
    void bothRetiredFeaturesStillRegisterInGlobeMod() throws IOException {
        String globeMod = Files.readString(Path.of("src/main/java/com/example/globe/GlobeMod.java"));
        assertTrue(globeMod.contains("com.example.globe.world.CaveDropTrapFeature.register();"),
                "the drop trap's biome membership ended, but its registry-consistency call must stand for "
                        + "any world that already placed its configured_feature JSON");
        assertTrue(globeMod.contains("com.example.globe.world.PowderCrevasseRoofFeature.register();"),
                "the powder-roof's biome membership ended, but its registry-consistency call must stand for "
                        + "any world that already placed its configured_feature JSON");
    }

    @Test
    void powderCrevasseRoofIsAbsentFromGlacialCavesToo() {
        JsonObject caves = load("/data/globe/worldgen/biome/glacial_caves.json");
        assertEquals(0, occurrences(caves.toString(), "\"globe:powder_crevasse_roof\""),
                "glacial_caves.json never listed the surface powder roof and must not gain it now");
    }

    @Test
    void hiddenGlacialChamberIsAbsentFromPolarBarrens() {
        JsonObject barrens = load("/data/globe/worldgen/biome/polar_barrens.json");
        assertEquals(0, occurrences(barrens.toString(), "\"globe:hidden_glacial_chamber\""),
                "the hidden chamber is a glacial_caves-only encounter and must not schedule in polar_barrens");
    }
}
