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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-9 P2 JSON schema tripwire for {@code data/globe/worldgen/biome/glacial_caves.json} (a parse
 * failure breaks world creation; grammar verbatim-mirrored from the 26.2 vanilla biome JSONs extracted
 * from the loom merged jar -- snowy_plains/dripstone_caves for the feature-step and spawner forms,
 * basalt_deltas for the {@code minecraft:audio/ambient_sounds} attribute form). Cheap tripwire like
 * {@code GlacialCarverJsonSchemaTest}: renamed/missing keys fail here in the unit suite; full codec
 * validation happens at boot (world creation IS the datapack parse gate).
 *
 * <h2>The owner laws this file must keep (pinned below)</h2>
 * <ul>
 *   <li><b>Underground stays alive</b> (reskin-plus, never a strip): the lake/geode/monster-room/ore/
 *       spring steps are EXACTLY polar_barrens' (which are exactly snowy_plains' underground subset).</li>
 *   <li><b>TEST 103 spawn law</b>: caves keep the NORMAL monster set -- strays-only is a SURFACE rule;
 *       stray is merely ADDED at moderate weight for flavor.</li>
 *   <li><b>Semi-ice lakes WITH FISH</b> (owner-locked idea): salmon + cod water creatures.</li>
 * </ul>
 */
class GlacialCavesBiomeJsonSchemaTest {

    private static JsonObject load(String resourcePath) {
        InputStream stream = GlacialCavesBiomeJsonSchemaTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject glacialCaves() {
        return load("/data/globe/worldgen/biome/glacial_caves.json");
    }

    private static JsonObject polarBarrens() {
        return load("/data/globe/worldgen/biome/polar_barrens.json");
    }

    private static List<String> featureStep(JsonObject biome, int step) {
        JsonArray features = biome.getAsJsonArray("features");
        List<String> ids = new ArrayList<>();
        features.get(step).getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
        return ids;
    }

    @Test
    void requiredCodecFieldsArePresent() {
        JsonObject biome = glacialCaves();
        for (String key : new String[]{"temperature", "downfall", "has_precipitation", "effects",
                "spawners", "spawn_costs", "features", "carvers", "attributes"}) {
            assertTrue(biome.has(key), "required biome codec field missing: " + key);
        }
        assertEquals(11, biome.getAsJsonArray("features").size(),
                "the 26.2 decoration-step array is 11 entries (raw_generation .. top_layer_modification)");
        assertEquals(-0.5, biome.get("temperature").getAsDouble(), 1e-9,
                "polar-cold temperature, matching polar_barrens");
    }

    @Test
    void undergroundFeatureStepsAreExactlyTheBarrensUndergroundSubset() {
        JsonObject caves = glacialCaves();
        JsonObject barrens = polarBarrens();
        // Steps by 26.2 index: 1 lakes, 2 local_modifications (geode), 3 underground_structures
        // (monster rooms), 8 fluid_springs, 10 top_layer_modification. These carry the "underground stays
        // alive" law and must match the barrens (itself pinned as snowy_plains' underground subset)
        // entry-for-entry, in order.
        for (int step : new int[]{1, 2, 3, 8, 10}) {
            assertEquals(featureStep(barrens, step), featureStep(caves, step),
                    "underground step " + step + " must be exactly the barrens/snowy_plains subset");
        }
        // Step 6 (underground_ores): S24 APPENDS the two glacial ice BLOBS after the barrens ore run so the
        // deep caverns read glacial in every wall. The "underground stays alive" law still binds: the barrens
        // ore run must be present, IN ORDER, as the exact PREFIX (no ore dropped or reordered) -- and because
        // packed_ice/blue_ice are not #base_stone_overworld and the blobs run LAST, they cement leftover base
        // stone without ever eating an ore. The only allowed addition is those two globe blobs, appended.
        List<String> barrensOres = featureStep(barrens, 6);
        List<String> cavesOres = featureStep(caves, 6);
        assertEquals(barrensOres, cavesOres.subList(0, barrensOres.size()),
                "the barrens ore run must be the exact ordered PREFIX of the caves ore step");
        assertEquals(List.of("globe:glacial_ice_blob", "globe:glacial_blue_ice_blob"),
                cavesOres.subList(barrensOres.size(), cavesOres.size()),
                "only the two S24 glacial ice blobs may be appended, packed then blue, after the ores");
        assertTrue(cavesOres.contains("minecraft:ore_diamond"),
                "sanity: the ore step really is the full ore run");
    }

    @Test
    void dressingStepsCarryTheGlacialFeatures() {
        JsonObject caves = glacialCaves();
        assertEquals(List.of("globe:hanging_icicles", "globe:glacial_snow_drift", "globe:glacial_powder_pocket",
                        "globe:glacial_frost_carpet"),
                featureStep(caves, 7),
                "underground_decoration (step 7) = the glacial dressing features, in authored order "
                        + "(S24 appended the frost-floor carpet)");
        assertEquals(List.of("globe:glacial_glow_lichen"), featureStep(caves, 9),
                "vegetal step replaces vanilla glow_lichen (count 104-157) with the sparse glacial one "
                        + "-- punctuation, not illumination");
    }

    @Test
    void monsterSetIsTheVanillaCaveSetPlusStray() {
        JsonObject caves = glacialCaves();
        JsonArray monsters = caves.getAsJsonObject("spawners").getAsJsonArray("monster");
        List<String> types = new ArrayList<>();
        int strayWeight = -1;
        int maxOtherWeight = 0;
        for (var e : monsters) {
            JsonObject entry = e.getAsJsonObject();
            String type = entry.get("type").getAsString();
            types.add(type);
            int weight = entry.get("weight").getAsInt();
            if ("minecraft:stray".equals(type)) {
                strayWeight = weight;
            } else {
                maxOtherWeight = Math.max(maxOtherWeight, weight);
            }
        }
        // TEST 103 owner law: caves are OK for normal monsters -- strays-only is a SURFACE rule.
        for (String required : new String[]{"minecraft:spider", "minecraft:zombie", "minecraft:skeleton",
                "minecraft:creeper", "minecraft:slime", "minecraft:enderman", "minecraft:witch",
                "minecraft:drowned"}) {
            assertTrue(types.contains(required), "vanilla cave monster set must keep " + required);
        }
        assertTrue(types.contains("minecraft:stray"), "stray joins for polar flavor");
        assertTrue(strayWeight > 0 && strayWeight < maxOtherWeight,
                "stray is MODERATE flavor weight, not dominant (owner law: never strays-only underground)");
    }

    @Test
    void fishSwimTheSemiIceLakes() {
        JsonObject caves = glacialCaves();
        JsonArray water = caves.getAsJsonObject("spawners").getAsJsonArray("water_creature");
        int salmonWeight = -1;
        int codWeight = -1;
        for (var e : water) {
            JsonObject entry = e.getAsJsonObject();
            if ("minecraft:salmon".equals(entry.get("type").getAsString())) {
                salmonWeight = entry.get("weight").getAsInt();
            }
            if ("minecraft:cod".equals(entry.get("type").getAsString())) {
                codWeight = entry.get("weight").getAsInt();
            }
        }
        assertTrue(salmonWeight > 0, "salmon must swim the semi-ice lakes (owner-locked idea)");
        assertTrue(codWeight > 0 && codWeight < salmonWeight, "cod joins at lower weight");
        JsonArray underground = caves.getAsJsonObject("spawners").getAsJsonArray("underground_water_creature");
        assertTrue(underground.toString().contains("minecraft:glow_squid"),
                "glow squid stays -- the vanilla cave water baseline");
    }

    @Test
    void artDirectionPaletteAndAmbienceAreWired() {
        JsonObject caves = glacialCaves();
        JsonObject attributes = caves.getAsJsonObject("attributes");
        assertEquals("#2e6f8f", attributes.get("minecraft:visual/fog_color").getAsString(),
                "glacial-teal fog (art director's palette)");
        assertEquals("#1e3a52", attributes.get("minecraft:visual/water_fog_color").getAsString(),
                "dense steel-blue water fog");
        assertEquals("#2e6f8f", caves.getAsJsonObject("effects").get("water_color").getAsString(),
                "glacial-teal water");

        JsonObject ambient = attributes.getAsJsonObject("minecraft:audio/ambient_sounds");
        assertNotNull(ambient, "cave ambience block must exist (loop + mood)");
        assertTrue(ambient.has("loop"), "ambience loop slot");
        JsonObject mood = ambient.getAsJsonObject("mood");
        assertEquals("minecraft:ambient.cave", mood.get("sound").getAsString(), "the classic cave mood");
        for (String key : new String[]{"tick_delay", "block_search_extent", "offset"}) {
            assertTrue(mood.has(key), "mood codec field (basalt_deltas-verified grammar): " + key);
        }
        assertTrue(attributes.has("minecraft:audio/background_music"), "music slot must be filled");
    }

    @Test
    void carverListMirrorsTheBarrensDeadWiringTrio() {
        // The carver list is DEAD WIRING at the applyCarvers seam (design ground truth 3) but stays the
        // inherited vanilla trio for consistency with polar_barrens -- pin so nobody "fixes" it into a
        // divergence, or worse, attaches globe carvers here expecting them to fire.
        assertEquals(polarBarrens().getAsJsonArray("carvers"), glacialCaves().getAsJsonArray("carvers"));
    }
}
