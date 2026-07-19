package com.example.globe.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 B-9 P1 JSON schema tripwire: parses the two new configured-carver JSONs off the classpath
 * (the same {@code data/globe/worldgen/configured_carver/} files the datapack loader reads) and
 * asserts every REQUIRED codec field key is present per the swept design's field list, plus the two
 * grammar forms that actually break parses (height-provider bounds are VerticalAnchor OBJECTS;
 * float-provider bounds are plain NUMBERS -- both verbatim-mirrored from the 26.2 vanilla
 * cave.json/canyon.json extracted from the loom jar). This is deliberately a CHEAP tripwire: a
 * missing/renamed key fails here in the unit suite; full codec validation happens at boot (world
 * creation IS the datapack parse gate, design proof plan step 2).
 */
class GlacialCarverJsonSchemaTest {

    private static final String CARVER_DIR = "/data/globe/worldgen/configured_carver/";

    private static final String[] BASE_REQUIRED_CONFIG_KEYS =
            {"probability", "y", "yScale", "lava_level", "replaceable"};

    private static JsonObject load(String fileName) {
        InputStream stream = GlacialCarverJsonSchemaTest.class.getResourceAsStream(CARVER_DIR + fileName);
        assertNotNull(stream, "Carver JSON must be on the classpath (main resources): " + fileName);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject requireConfig(JsonObject root, String fileName, String expectedType) {
        assertTrue(root.has("type"), fileName + ": wrapper must declare \"type\"");
        assertEquals(expectedType, root.get("type").getAsString(),
                fileName + ": carver type must be the vanilla type the design picked");
        assertTrue(root.has("config"), fileName + ": wrapper must carry the \"config\" object");
        JsonObject config = root.getAsJsonObject("config");
        for (String key : BASE_REQUIRED_CONFIG_KEYS) {
            assertTrue(config.has(key), fileName + ": required base codec field missing: " + key);
        }
        assertEquals("#minecraft:overworld_carver_replaceables", config.get("replaceable").getAsString(),
                fileName + ": replaceable must be the vanilla overworld tag AS-IS (sweep finding 3: "
                        + "plain ice stays uncarvable -- the frozen-sea skin and ice bridges depend on it)");
        JsonObject lavaLevel = config.getAsJsonObject("lava_level");
        assertTrue(lavaLevel.has("absolute"),
                fileName + ": lava_level must use the absolute VerticalAnchor form");
        assertEquals(-56, lavaLevel.get("absolute").getAsInt(),
                fileName + ": lava_level must sit at absolute -56 -- below everything the Y band can "
                        + "reach, no lava windows in a glacier");
        return config;
    }

    /** Height-provider bounds are VerticalAnchor OBJECTS ({"absolute": N}) -- the grammar that breaks
     *  world creation if written as plain numbers. */
    private static void assertAbsoluteHeightProviderBound(JsonObject provider, String boundKey,
                                                          int expected, String context) {
        JsonElement bound = provider.get(boundKey);
        assertNotNull(bound, context + ": height provider must carry " + boundKey);
        assertTrue(bound.isJsonObject(),
                context + ": height-provider " + boundKey + " must be a VerticalAnchor OBJECT, not a number");
        assertEquals(expected, bound.getAsJsonObject().get("absolute").getAsInt(),
                context + ": " + boundKey + " absolute anchor");
    }

    @Test
    void crevasseCanyonCarrierHasEveryRequiredCodecField() {
        JsonObject config = requireConfig(load("crevasse.json"), "crevasse.json", "minecraft:canyon");

        // Canyon-specific required fields (design codec list).
        assertTrue(config.has("vertical_rotation"), "crevasse.json: canyon requires vertical_rotation");
        assertTrue(config.has("shape"), "crevasse.json: canyon requires the shape object");
        JsonObject shape = config.getAsJsonObject("shape");
        for (String key : new String[]{"distance_factor", "thickness", "width_smoothness",
                "horizontal_radius_factor", "vertical_radius_default_factor", "vertical_radius_center_factor"}) {
            assertTrue(shape.has(key), "crevasse.json: required shape codec field missing: " + key);
        }

        // The design's Y band: absolute uniform 66..112, anchored to the real polar surface distribution.
        JsonObject y = config.getAsJsonObject("y");
        assertEquals("minecraft:uniform", y.get("type").getAsString(), "crevasse.json: y provider type");
        assertAbsoluteHeightProviderBound(y, "min_inclusive", 66, "crevasse.json y");
        assertAbsoluteHeightProviderBound(y, "max_inclusive", 112, "crevasse.json y");

        // Grammar tripwire: FLOAT-provider bounds are plain numbers (uniform float uses max_exclusive).
        JsonObject verticalRotation = config.getAsJsonObject("vertical_rotation");
        assertTrue(verticalRotation.get("min_inclusive").isJsonPrimitive(),
                "crevasse.json: float-provider min_inclusive must be a plain number, not an anchor object");
        assertTrue(verticalRotation.has("max_exclusive"),
                "crevasse.json: uniform float provider upper bound is max_exclusive");
    }

    @Test
    void glacialTunnelsCaveCarrierHasEveryRequiredCodecField() {
        JsonObject config = requireConfig(load("glacial_tunnels.json"), "glacial_tunnels.json", "minecraft:cave");

        // Cave-specific required fields (design codec list).
        for (String key : new String[]{"horizontal_radius_multiplier", "vertical_radius_multiplier", "floor_level"}) {
            assertTrue(config.has(key), "glacial_tunnels.json: required cave codec field missing: " + key);
        }

        // The design's Y band: absolute uniform 30..90.
        JsonObject y = config.getAsJsonObject("y");
        assertEquals("minecraft:uniform", y.get("type").getAsString(), "glacial_tunnels.json: y provider type");
        assertAbsoluteHeightProviderBound(y, "min_inclusive", 30, "glacial_tunnels.json y");
        assertAbsoluteHeightProviderBound(y, "max_inclusive", 90, "glacial_tunnels.json y");

        // The tightened-radii design intent (horizontal ~0.7x, vertical ~0.5x of vanilla) -- pin the
        // exact dev-pick numbers recorded in the LatitudeV2Flags javadoc.
        JsonObject horizontal = config.getAsJsonObject("horizontal_radius_multiplier");
        assertEquals(0.49, horizontal.get("min_inclusive").getAsDouble(), 1e-9,
                "glacial_tunnels.json: horizontal radius min = 0.7 x vanilla 0.7");
        assertEquals(0.98, horizontal.get("max_exclusive").getAsDouble(), 1e-9,
                "glacial_tunnels.json: horizontal radius max = 0.7 x vanilla 1.4");
        JsonObject vertical = config.getAsJsonObject("vertical_radius_multiplier");
        assertEquals(0.4, vertical.get("min_inclusive").getAsDouble(), 1e-9,
                "glacial_tunnels.json: vertical radius min = 0.5 x vanilla 0.8");
        assertEquals(0.65, vertical.get("max_exclusive").getAsDouble(), 1e-9,
                "glacial_tunnels.json: vertical radius max = 0.5 x vanilla 1.3");
    }
}
