package com.example.globe.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S25b JSON schema tripwire for the powder-roof crevasse trap feature ({@code globe:powder_crevasse_roof}) --
 * a parse failure breaks world creation. Mirrors {@code GlacialDressingJsonSchemaTest}'s style: assert the
 * configured feature declares the mod's custom type (registered in code at mod-init), the placed feature
 * points back at it and ends with the biome filter, and {@code polar_barrens.json} lists the placed feature
 * at the TOP_LAYER_MODIFICATION step AFTER {@code minecraft:freeze_top_layer}, with the magma quench as the
 * final flooded-magma writer. Full codec validation happens at boot.
 */
class PowderCrevasseRoofJsonSchemaTest {

    private static final String ID = "globe:powder_crevasse_roof";

    private static JsonObject load(String resourcePath) {
        InputStream stream = PowderCrevasseRoofJsonSchemaTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<String> featureStep(JsonObject biome, int step) {
        JsonArray features = biome.getAsJsonArray("features");
        List<String> ids = new ArrayList<>();
        features.get(step).getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
        return ids;
    }

    @Test
    void configuredFeatureDeclaresTheCustomTypeWithNoneConfig() {
        JsonObject configured = load("/data/globe/worldgen/configured_feature/powder_crevasse_roof.json");
        assertEquals(ID, configured.get("type").getAsString(),
                "must declare the mod's custom feature type (registered into BuiltInRegistries.FEATURE at init)");
        assertTrue(configured.has("config"), "a NoneFeatureConfiguration feature still needs a (empty) config");
        assertTrue(configured.getAsJsonObject("config").isEmpty(),
                "config is empty -- NoneFeatureConfiguration.CODEC accepts {}");
    }

    @Test
    void placedFeaturePointsAtTheConfiguredFeatureAndEndsWithBiome() {
        JsonObject placed = load("/data/globe/worldgen/placed_feature/powder_crevasse_roof.json");
        assertEquals(ID, placed.get("feature").getAsString(),
                "placed feature must reference the configured feature by id");
        JsonArray placement = placed.getAsJsonArray("placement");
        assertTrue(placement.size() >= 1, "placement chain must exist");
        List<String> types = new ArrayList<>();
        placement.forEach(e -> types.add(e.getAsJsonObject().get("type").getAsString()));
        assertTrue(types.contains("minecraft:in_square"),
                "in_square spreads the single per-chunk placement into the chunk (once-per-chunk scan idiom)");
        assertEquals("minecraft:biome", types.get(types.size() - 1),
                "the biome filter must be LAST so the trap never leaks outside polar_barrens columns");
    }

    @Test
    void barrensTopLayerKeepsFreezeAndQuenchAndNoLongerSchedulesTheRoof() {
        JsonObject barrens = load("/data/globe/worldgen/biome/polar_barrens.json");
        // Step 10 = top_layer_modification. The freeze still leads and the magma quench still runs last so a
        // late spring/freeze writer cannot leave flooded magma bare -- those two pins are untouched.
        List<String> topLayer = featureStep(barrens, 10);
        assertEquals("minecraft:freeze_top_layer", topLayer.get(0),
                "freeze_top_layer stays first in the top-layer step");
        assertEquals("globe:magma_quench_sweep", topLayer.get(topLayer.size() - 1),
                "the magma quench is appended LAST in the top-layer step");
        // The surface powder roof is UNWIRED: the hidden glacial chamber pass retired it from the barrens.
        // Its configured/placed JSON halves and its registration remain (existing-world compatibility);
        // only its biome membership ended, so no biome may schedule it any more.
        for (int step = 0; step < barrens.getAsJsonArray("features").size(); step++) {
            assertFalse(featureStep(barrens, step).contains(ID),
                    "the powder-roof trap is unwired from polar_barrens; found it in step " + step);
        }
        JsonObject caves = load("/data/globe/worldgen/biome/glacial_caves.json");
        for (int step = 0; step < caves.getAsJsonArray("features").size(); step++) {
            assertFalse(featureStep(caves, step).contains(ID),
                    "the powder-roof trap is a barrens surface feature, never listed in glacial_caves");
        }
    }
}
