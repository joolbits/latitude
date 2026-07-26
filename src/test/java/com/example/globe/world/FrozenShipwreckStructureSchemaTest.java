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
 * S47/S48 schema tripwires for {@code globe:frozen_shipwreck} — owner-approved 2026-07-26 ("I love the
 * shipwrecks"). A parse failure in any of these files breaks world creation, and the structure carries TWO
 * owner laws: (1) the ZOMBIE CREW — "zombies are an approved mob spawn in the radius around glacial
 * shipwrecks" — rides the structure's own {@code spawn_overrides} (the ocean-monument rail), which is
 * exactly what keeps the S25b biome-wide zombie ban intact: the override lives in the wreck's bounding box,
 * the biome list never changes; (2) the frozen-wreck PROCESSOR palette must stay glacial (ice takeover,
 * no green, no speleothem shapes).
 */
class FrozenShipwreckStructureSchemaTest {

    private static JsonObject load(String resourcePath) {
        InputStream stream = FrozenShipwreckStructureSchemaTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void structureCarriesTheZombieCrewOverrideInsideItsOwnBoxOnly() {
        JsonObject st = load("/data/globe/worldgen/structure/frozen_shipwreck.json");
        assertEquals("minecraft:jigsaw", st.get("type").getAsString());
        assertEquals("#globe:has_structure/frozen_shipwreck", st.get("biomes").getAsString());
        JsonObject overrides = st.getAsJsonObject("spawn_overrides");
        JsonObject monster = overrides.getAsJsonObject("monster");
        assertEquals("full", monster.get("bounding_box").getAsString(),
                "the crew haunts the WHOLE wreck box (the owner's 'radius around'), not just piece interiors");
        List<String> types = new ArrayList<>();
        int zombieWeight = -1;
        for (var e : monster.getAsJsonArray("spawns")) {
            JsonObject entry = e.getAsJsonObject();
            types.add(entry.get("type").getAsString());
            if ("minecraft:zombie".equals(entry.get("type").getAsString())) {
                zombieWeight = entry.get("weight").getAsInt();
            }
        }
        assertTrue(types.contains("minecraft:zombie"),
                "owner 2026-07-26: zombies are an approved spawn around glacial shipwrecks (the frozen crew)");
        assertTrue(zombieWeight >= 50, "the crew LEADS the wreck's spawn table -- it is their ship");
        // The override is the ONLY place zombies are legal: the biome JSONs must stay zombie-free
        // (S25b law, asserted by GlacialCavesBiomeJsonSchemaTest's banned list).
    }

    @Test
    void wildPlacementIsWiredRareAndDeep() {
        JsonObject set = load("/data/globe/worldgen/structure_set/frozen_shipwrecks.json");
        JsonObject placement = set.getAsJsonObject("placement");
        assertEquals("minecraft:random_spread", placement.get("type").getAsString());
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(spacing > separation, "codec law: spacing must exceed separation");
        assertTrue(spacing >= 40 && spacing <= 48,
                "S51: spacing 44 -- the S48 'rarer than caches' law bent after the owner crossed two flights "
                        + "without meeting one (\"i didnt see any wrecks\"); discoverable-but-special, "
                        + "never common");
        JsonObject st = load("/data/globe/worldgen/structure/frozen_shipwreck.json");
        JsonObject height = st.getAsJsonObject("start_height");
        assertEquals(18, height.getAsJsonObject("min_inclusive").get("absolute").getAsInt());
        assertEquals(64, height.getAsJsonObject("max_inclusive").get("absolute").getAsInt(),
                "depth spread keeps hulls inside the ice body, below the surface country");
        JsonObject tag = load("/data/globe/tags/worldgen/biome/has_structure/frozen_shipwreck.json");
        List<String> members = new ArrayList<>();
        tag.getAsJsonArray("values").forEach(v -> members.add(v.getAsString()));
        assertTrue(members.contains("globe:glacial_caves") && members.contains("globe:polar_barrens"),
                "both glacial biomes host wrecks");
    }

    @Test
    void frozenWreckProcessorStaysGlacialAndTemplatesExist() {
        JsonObject pl = load("/data/globe/worldgen/processor_list/frozen_wreck.json");
        JsonArray rules = pl.getAsJsonArray("processors").get(0).getAsJsonObject().getAsJsonArray("rules");
        assertTrue(rules.size() >= 10, "plank + log families all carry takeover rules");
        for (var r : rules) {
            String out = r.getAsJsonObject().getAsJsonObject("output_state").get("Name").getAsString();
            assertTrue(out.equals("minecraft:packed_ice") || out.equals("minecraft:blue_ice"),
                    "the takeover writes ONLY glacial ice (no green, no foreign palette): " + out);
        }
        for (String t : new String[]{"with_mast", "sideways_full", "upsidedown_full"}) {
            assertNotNull(FrozenShipwreckStructureSchemaTest.class.getResourceAsStream(
                            "/data/globe/structure/frozen_shipwreck/" + t + ".nbt"),
                    "globe-owned template (with the spliced anchor jigsaw) must ship: " + t);
        }
        JsonObject pool = load("/data/globe/worldgen/template_pool/frozen_shipwreck/wreck.json");
        for (var e : pool.getAsJsonArray("elements")) {
            String loc = e.getAsJsonObject().getAsJsonObject("element").get("location").getAsString();
            assertTrue(loc.startsWith("globe:frozen_shipwreck/"),
                    "pool must reference the globe template copies (vanilla NBTs lack the anchor): " + loc);
            assertEquals("globe:frozen_wreck",
                    e.getAsJsonObject().getAsJsonObject("element").get("processors").getAsString(),
                    "every variant wears the frozen-wreck takeover");
        }
        assertFalse(load("/data/globe/worldgen/structure/frozen_shipwreck.json").toString().contains("moss"),
                "palette law: nothing green near the wreck");
    }
}
