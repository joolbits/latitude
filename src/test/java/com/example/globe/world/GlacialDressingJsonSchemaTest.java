package com.example.globe.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-9 P2 JSON schema tripwires for the four glacial dressing feature pairs (configured + placed) --
 * a parse failure breaks world creation, so every field grammar here is verbatim-mirrored from real
 * 26.2 vanilla worldgen JSONs extracted from the loom merged jar: {@code cave_vine}/{@code sugar_cane}
 * for the block_column form, {@code cave_vines} (placed) for the environment-scan ceiling idiom,
 * {@code pile_snow}/{@code pile_ice} for block_pile, {@code ice_patch} for disk, and
 * {@code glow_lichen} for multiface_growth + the deep-only surface_relative filter. The grammar forms
 * that actually break parses are pinned explicitly: height-provider bounds are VerticalAnchor OBJECTS,
 * int/float-provider bounds are PLAIN NUMBERS.
 */
class GlacialDressingJsonSchemaTest {

    private static JsonObject load(String resourcePath) {
        InputStream stream = GlacialDressingJsonSchemaTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject configured(String name) {
        return load("/data/globe/worldgen/configured_feature/" + name + ".json");
    }

    private static JsonObject placed(String name) {
        return load("/data/globe/worldgen/placed_feature/" + name + ".json");
    }

    private static List<JsonObject> placementChain(JsonObject placedJson) {
        List<JsonObject> chain = new ArrayList<>();
        placedJson.getAsJsonArray("placement").forEach(e -> chain.add(e.getAsJsonObject()));
        return chain;
    }

    private static JsonObject modifier(List<JsonObject> chain, String type) {
        return chain.stream().filter(m -> type.equals(m.get("type").getAsString())).findFirst()
                .orElseThrow(() -> new AssertionError("placement chain must contain " + type));
    }

    /** Every dressing placement must end with the biome filter (so a feature never leaks outside
     *  glacial_caves cells) and must cap its height range BELOW the glacial-caves ceiling as a
     *  VerticalAnchor OBJECT (the grammar that breaks world creation if written as a plain number). */
    private static void assertCaveBandPlacement(String name, List<JsonObject> chain) {
        assertEquals("minecraft:biome", chain.get(chain.size() - 1).get("type").getAsString(),
                name + ": the biome filter must be the LAST placement modifier");
        JsonObject height = modifier(chain, "minecraft:height_range").getAsJsonObject("height");
        JsonElement max = height.get("max_inclusive");
        assertTrue(max.isJsonObject(),
                name + ": height-provider max_inclusive must be a VerticalAnchor OBJECT, not a number");
        int maxY = max.getAsJsonObject().get("absolute").getAsInt();
        assertTrue(maxY < LatitudeBiomes.GLACIAL_CAVES_CEILING_Y,
                name + ": dressing must stay below the glacial-caves ceiling (" + maxY + " < 48)");
        assertTrue(height.get("min_inclusive").getAsJsonObject().has("above_bottom"),
                name + ": range must start at the world floor (above_bottom anchor)");
    }

    @Test
    void hangingIciclesAreDownwardIceColumnsCeilingScanned() {
        JsonObject config = configured("hanging_icicles");
        assertEquals("minecraft:block_column", config.get("type").getAsString());
        JsonObject c = config.getAsJsonObject("config");
        assertEquals("down", c.get("direction").getAsString(), "icicles hang");
        assertTrue(c.has("allowed_placement"), "block_column requires allowed_placement");
        assertTrue(c.has("prioritize_tip"), "block_column requires prioritize_tip");
        JsonArray layers = c.getAsJsonArray("layers");
        assertEquals(1, layers.size());
        JsonObject height = layers.get(0).getAsJsonObject().getAsJsonObject("height");
        assertEquals("minecraft:uniform", height.get("type").getAsString());
        // Int-provider bounds are PLAIN NUMBERS (the design length 1-5).
        assertEquals(1, height.get("min_inclusive").getAsInt(), "icicle length floor");
        assertEquals(5, height.get("max_inclusive").getAsInt(), "icicle length cap");
        assertEquals("minecraft:ice",
                layers.get(0).getAsJsonObject().getAsJsonObject("provider")
                        .getAsJsonObject("state").get("Name").getAsString());

        JsonObject placedJson = placed("hanging_icicles");
        assertEquals("globe:hanging_icicles", placedJson.get("feature").getAsString());
        List<JsonObject> chain = placementChain(placedJson);
        assertCaveBandPlacement("hanging_icicles", chain);
        JsonObject scan = modifier(chain, "minecraft:environment_scan");
        assertEquals("up", scan.get("direction_of_search").getAsString(), "scan UP for the ceiling");
        assertEquals("down", scan.getAsJsonObject("target_condition").get("direction").getAsString(),
                "the ceiling block is the one with a sturdy DOWN face (cave_vines idiom)");
        JsonObject offset = modifier(chain, "minecraft:random_offset");
        assertEquals(-1, offset.get("y_spread").getAsInt(),
                "step one below the matched ceiling block into the air cell (cave_vines idiom)");
    }

    @Test
    void snowDriftsAreWeightedLayerPilesOnFloors() {
        JsonObject config = configured("glacial_snow_drift");
        assertEquals("minecraft:block_pile", config.get("type").getAsString());
        JsonArray entries = config.getAsJsonObject("config").getAsJsonObject("state_provider")
                .getAsJsonArray("entries");
        assertTrue(entries.size() >= 2, "randomized layer heights need multiple weighted states");
        for (var e : entries) {
            JsonObject state = e.getAsJsonObject().getAsJsonObject("data");
            assertEquals("minecraft:snow", state.get("Name").getAsString(), "drifts are snow layers");
            int layers = Integer.parseInt(state.getAsJsonObject("Properties").get("layers").getAsString());
            assertTrue(layers >= 1 && layers <= 3, "dusting stays shallow (layers 1-3)");
        }

        JsonObject placedJson = placed("glacial_snow_drift");
        assertEquals("globe:glacial_snow_drift", placedJson.get("feature").getAsString());
        List<JsonObject> chain = placementChain(placedJson);
        assertCaveBandPlacement("glacial_snow_drift", chain);
        JsonObject scan = modifier(chain, "minecraft:environment_scan");
        assertEquals("down", scan.get("direction_of_search").getAsString(), "scan DOWN for the floor");
        assertEquals("up", scan.getAsJsonObject("target_condition").get("direction").getAsString(),
                "the floor block is the one with a sturdy UP face");
        assertEquals(1, modifier(chain, "minecraft:random_offset").get("y_spread").getAsInt(),
                "step one above the matched floor block into the air cell");
    }

    @Test
    void powderPocketsAreSparseFloorDisks() {
        JsonObject config = configured("glacial_powder_pocket");
        assertEquals("minecraft:disk", config.get("type").getAsString());
        JsonObject c = config.getAsJsonObject("config");
        assertEquals("minecraft:powder_snow",
                c.getAsJsonObject("state_provider").getAsJsonObject("state").get("Name").getAsString());
        assertTrue(c.has("half_height"), "disk requires half_height");
        JsonObject radius = c.getAsJsonObject("radius");
        assertTrue(radius.get("max_inclusive").isJsonPrimitive(),
                "int-provider bounds are plain numbers");
        assertTrue(radius.get("max_inclusive").getAsInt() <= 2,
                "pockets stay small -- hidden traps, never a death carpet (the barrens surface law)");
        String targets = c.getAsJsonObject("target").getAsJsonArray("blocks").toString();
        assertTrue(targets.contains("minecraft:packed_ice") && targets.contains("minecraft:blue_ice"),
                "pockets must be placeable in the glacier body strata");

        JsonObject placedJson = placed("glacial_powder_pocket");
        assertEquals("globe:glacial_powder_pocket", placedJson.get("feature").getAsString());
        List<JsonObject> chain = placementChain(placedJson);
        assertCaveBandPlacement("glacial_powder_pocket", chain);
        assertTrue(modifier(chain, "minecraft:rarity_filter").get("chance").getAsInt() >= 2,
                "pockets are rare punctuation");
        assertEquals("down", modifier(chain, "minecraft:environment_scan")
                .get("direction_of_search").getAsString(), "disk sits AT the floor (ice_patch idiom)");
    }

    @Test
    void glowLichenIsSparseDeepAndIceAware() {
        JsonObject config = configured("glacial_glow_lichen");
        assertEquals("minecraft:multiface_growth", config.get("type").getAsString());
        String canPlaceOn = config.getAsJsonObject("config").getAsJsonArray("can_be_placed_on").toString();
        for (String host : new String[]{"minecraft:packed_ice", "minecraft:blue_ice", "minecraft:stone"}) {
            assertTrue(canPlaceOn.contains(host),
                    "aurora-green on blue ice: lichen must accept " + host);
        }

        JsonObject placedJson = placed("glacial_glow_lichen");
        assertEquals("globe:glacial_glow_lichen", placedJson.get("feature").getAsString());
        List<JsonObject> chain = placementChain(placedJson);
        assertCaveBandPlacement("glacial_glow_lichen", chain);
        JsonObject count = modifier(chain, "minecraft:count").getAsJsonObject("count");
        assertTrue(count.get("max_inclusive").getAsInt() <= 8,
                "LOW count -- punctuation, not illumination (vanilla glow_lichen runs 104-157)");
        JsonObject deepOnly = modifier(chain, "minecraft:surface_relative_threshold_filter");
        assertEquals("OCEAN_FLOOR_WG", deepOnly.get("heightmap").getAsString());
        assertEquals(-13, deepOnly.get("max_inclusive").getAsInt(),
                "deep-only placement (vanilla glow_lichen's own threshold)");
    }

    @Test
    void everyGlobeFeatureTheBiomeListsHasBothJsonHalves() {
        JsonObject biome = load("/data/globe/worldgen/biome/glacial_caves.json");
        JsonArray features = biome.getAsJsonArray("features");
        int globeFeatures = 0;
        for (var step : features) {
            for (var id : step.getAsJsonArray()) {
                String featureId = id.getAsString();
                if (!featureId.startsWith("globe:")) {
                    continue;
                }
                globeFeatures++;
                String name = featureId.substring("globe:".length());
                assertNotNull(GlacialDressingJsonSchemaTest.class.getResourceAsStream(
                                "/data/globe/worldgen/placed_feature/" + name + ".json"),
                        "biome lists " + featureId + " but the placed_feature JSON is missing "
                                + "-- an unresolvable reference breaks world creation");
                assertNotNull(GlacialDressingJsonSchemaTest.class.getResourceAsStream(
                                "/data/globe/worldgen/configured_feature/" + name + ".json"),
                        "placed feature " + featureId + " needs its configured_feature JSON");
            }
        }
        assertEquals(4, globeFeatures, "the four dressing features, no silent drops");
    }
}
