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
 *   <li><b>S25b frozen-dead roster</b> (owner TEST 117 override, 2026-07-20: "Monsters inside glacial
 *       caves should be strays"): the underground monster list is strays + a few skeletons ONLY -- nothing
 *       warm-blooded (the frozen-dead fiction + the doomed-expedition register). This SUPERSEDES the earlier
 *       TEST 103 "caves keep the normal set" reading for THIS biome; the surface strays-only law
 *       (polar_barrens, TEST 103/104) is a separate, untouched rule.</li>
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
        // (monster rooms), 8 fluid_springs. These carry the "underground stays alive" law and must match the
        // barrens (itself pinned as snowy_plains' underground subset) entry-for-entry, in order.
        for (int step : new int[]{1, 2, 3, 8}) {
            assertEquals(featureStep(barrens, step), featureStep(caves, step),
                    "underground step " + step + " must be exactly the barrens/snowy_plains subset");
        }
        // Step 10 (top_layer_modification) DIVERGES since S25b: the barrens surface biome appended the
        // barrens-only powder-roof crevasse trap AFTER freeze_top_layer; the underground caves keep only the
        // bare freeze pass. The shared "underground stays alive" law is unaffected (it lives in steps 1-8).
        assertEquals(List.of("minecraft:freeze_top_layer"), featureStep(caves, 10),
                "glacial_caves top-layer step stays the bare freeze pass (the surface trap is barrens-only)");
        assertEquals("minecraft:freeze_top_layer", featureStep(barrens, 10).get(0),
                "barrens top-layer step still leads with the freeze pass, then the surface trap");
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
        assertEquals(List.of("globe:hanging_icicles", "globe:icicle_cluster", "globe:glacial_snow_drift",
                        "globe:glacial_powder_pocket", "globe:glacial_frost_carpet", "globe:glacial_slush_floe"),
                featureStep(caves, 7),
                "underground_decoration (step 7) = the glacial dressing features, in authored order "
                        + "(S24 appended the frost-floor carpet; S25 appended the slush floe; S37 appended "
                        + "the reshaded pointed-dripstone icicle cluster right after the plain-ice icicles)");
        assertEquals(List.of("globe:glacial_glow_lichen", "globe:pale_cave_hanging_moss", "globe:pale_cave_moss_patch"),
                featureStep(caves, 9),
                "vegetal step replaces vanilla glow_lichen (count 104-157) with the sparse glacial one "
                        + "-- punctuation, not illumination -- then S37 appends the pale-moss atmosphere "
                        + "(hanging strands + floor patches), sparse-to-moderate and well under the icicle "
                        + "density (owner: \"in some areas\", not everywhere)");
    }

    @Test
    void monsterRosterIsTheFrozenDeadStraysAndSkeletonsOnly() {
        JsonObject caves = glacialCaves();
        JsonArray monsters = caves.getAsJsonObject("spawners").getAsJsonArray("monster");
        List<String> types = new ArrayList<>();
        int strayWeight = -1;
        int skeletonWeight = -1;
        for (var e : monsters) {
            JsonObject entry = e.getAsJsonObject();
            String type = entry.get("type").getAsString();
            types.add(type);
            int weight = entry.get("weight").getAsInt();
            if ("minecraft:stray".equals(type)) {
                strayWeight = weight;
            }
            if ("minecraft:skeleton".equals(type)) {
                skeletonWeight = weight;
            }
        }
        // S25b owner override (TEST 117): "Monsters inside glacial caves should be strays." The frozen-dead
        // roster is strays + a few skeletons ONLY -- nothing warm-blooded underground.
        assertEquals(List.of("minecraft:stray", "minecraft:skeleton"), types,
                "the glacial-caves monster list is exactly [stray, skeleton], in that order");
        assertEquals(85, strayWeight, "strays dominate the frozen-dead roster (weight 85)");
        assertEquals(15, skeletonWeight, "a few skeletons join at low weight (15)");
        assertTrue(strayWeight > skeletonWeight, "strays are the dominant undead in the ice");
        // Nothing warm-blooded (or otherwise off-fiction) may appear underground.
        for (String banned : new String[]{"minecraft:zombie", "minecraft:zombie_villager", "minecraft:spider",
                "minecraft:creeper", "minecraft:slime", "minecraft:enderman", "minecraft:witch",
                "minecraft:drowned"}) {
            assertFalse(types.contains(banned),
                    "the frozen-dead roster excludes warm-blooded/off-fiction mob " + banned);
        }
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

    private static int creatureWeight(JsonObject biome, String type) {
        for (var e : biome.getAsJsonObject("spawners").getAsJsonArray("creature")) {
            JsonObject entry = e.getAsJsonObject();
            if (type.equals(entry.get("type").getAsString())) {
                return entry.get("weight").getAsInt();
            }
        }
        return -1;
    }

    /**
     * S25 POLAR LIFE &amp; PERIL fauna (owner TEST 117, 2026-07-20: "I don't see any polar bears or Arctic
     * foxes in polar storm country"). Polar bears LURK at low weight (vanilla frozen_ocean uses weight 1);
     * foxes join at a modest weight and hunt the barrens' own rabbits (vanilla behavior, no code). The
     * WHITE/snow fox variant is biome-tag-driven in 26.2 -- {@code Fox.Variant.byBiome} returns SNOW iff the
     * biome is in {@code #minecraft:spawns_snow_foxes} (verified via javap on the merged-deobf jar:
     * {@code Holder.is(BiomeTags.SPAWNS_SNOW_FOXES)}) -- so both globe biomes must join that tag (the same
     * merge pattern as the repo's existing {@code spawns_white_rabbits.json}) or the foxes render red.
     */
    @Test
    void polarFaunaLurkAndTheSnowFoxVariantTagIsWired() {
        JsonObject caves = glacialCaves();
        JsonObject barrens = polarBarrens();

        // Both biomes carry the lurking bear + the fox; the bear stays a LOW-weight lurker, never a herd.
        for (JsonObject biome : new JsonObject[]{caves, barrens}) {
            int bear = creatureWeight(biome, "minecraft:polar_bear");
            int fox = creatureWeight(biome, "minecraft:fox");
            assertTrue(bear > 0, "polar bear must lurk in the polar fauna");
            assertTrue(fox > 0, "arctic fox must join the polar fauna");
            assertTrue(bear <= 2, "the bear is a LOW-weight lurking risk, not a herd (owner law)");
            assertTrue(fox > bear, "foxes are a more common sight than the lurking bear");
        }
        // The barrens keeps its rabbits -- the fox's native prey (a real predator-prey loop, no behavior code).
        assertTrue(creatureWeight(barrens, "minecraft:rabbit") > 0,
                "the barrens rabbits stay -- the arctic fox's own prey");

        // The snow-fox variant tag: BOTH globe biomes must be in #minecraft:spawns_snow_foxes or foxes render
        // red (Fox.Variant.byBiome reads exactly this tag). replace:false so we MERGE with vanilla's snowy list.
        JsonObject tag = load("/data/minecraft/tags/worldgen/biome/spawns_snow_foxes.json");
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean(),
                "must MERGE with vanilla's snowy biomes, never replace them");
        List<String> values = new ArrayList<>();
        tag.getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
        assertTrue(values.contains("globe:polar_barrens"),
                "polar_barrens must join #spawns_snow_foxes so its foxes are the white variant");
        assertTrue(values.contains("globe:glacial_caves"),
                "glacial_caves must join #spawns_snow_foxes so its foxes are the white variant");
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
