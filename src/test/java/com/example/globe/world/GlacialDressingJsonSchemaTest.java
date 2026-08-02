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

    private static List<JsonObject> modifiers(List<JsonObject> chain, String type) {
        List<JsonObject> out = new ArrayList<>();
        chain.stream().filter(m -> type.equals(m.get("type").getAsString())).forEach(out::add);
        return out;
    }

    /** Every dressing placement must end with the biome filter (so a feature never leaks outside its
     *  listed biomes) and must express its height range as a VerticalAnchor OBJECT (the grammar that
     *  breaks world creation if written as a plain number). S38: the old &lt;48 ceiling cap is retired --
     *  the floor/pool dressing spans the FULL cavern column (bounded to glacial country by the biome
     *  filter), so the cap assertion is "must reach ABOVE the ceiling and cover the p95 surface band". */
    private static void assertCaveBandPlacement(String name, List<JsonObject> chain) {
        assertEquals("minecraft:biome", chain.get(chain.size() - 1).get("type").getAsString(),
                name + ": the biome filter must be the LAST placement modifier");
        JsonObject height = modifier(chain, "minecraft:height_range").getAsJsonObject("height");
        JsonElement max = height.get("max_inclusive");
        assertTrue(max.isJsonObject(),
                name + ": height-provider max_inclusive must be a VerticalAnchor OBJECT, not a number");
        int maxY = max.getAsJsonObject().get("absolute").getAsInt();
        assertTrue(maxY > LatitudeBiomes.GLACIAL_CAVES_CEILING_Y,
                name + ": S38 -- dressing must reach ABOVE the glacial-caves ceiling into the upper cavern "
                        + "country (" + maxY + " > 48)");
        assertTrue(maxY >= 110,
                name + ": S38 -- the range must cover the p95 surface band (~Y120), was " + maxY);
        assertEquals(0, height.get("min_inclusive").getAsJsonObject().get("absolute").getAsInt(),
                name + ": S49 (owner, 84N deepslate screenshot: \"Remove icicles and ice/snow from the "
                        + "deepslate layers, deep dark\") -- ice dressing floors at ABSOLUTE Y0, the S37 ice "
                        + "country's own floor; the stone/deepslate/deep-dark cellar keeps its own story");
    }

    /** S38 exception: the two ICE BLOBS stay DEEP-band only (max &lt; 48). Above Y0 the S37 body is already
     *  solid ice, so blobs would be invisible no-ops there -- they seam the remaining sub-Y48 stone country. */
    private static void assertDeepCaveBandPlacement(String name, List<JsonObject> chain) {
        assertEquals("minecraft:biome", chain.get(chain.size() - 1).get("type").getAsString(),
                name + ": the biome filter must be the LAST placement modifier");
        JsonObject height = modifier(chain, "minecraft:height_range").getAsJsonObject("height");
        JsonElement max = height.get("max_inclusive");
        assertTrue(max.isJsonObject(),
                name + ": height-provider max_inclusive must be a VerticalAnchor OBJECT, not a number");
        int maxY = max.getAsJsonObject().get("absolute").getAsInt();
        assertTrue(maxY < LatitudeBiomes.GLACIAL_CAVES_CEILING_Y,
                name + ": blobs stay below the glacial-caves ceiling (" + maxY + " < 48)");
        assertEquals(0, height.get("min_inclusive").getAsJsonObject().get("absolute").getAsInt(),
                name + ": S49 -- the blobs seam the Y0..48 stone country ONLY; below Y0 the cellar is the "
                        + "deepslate/deep-dark's own (owner: no ice in the deepslate layers)");
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
        // S23/S24 ROOFED-ONLY: the powder trap must sit under an intact ROOF (tunnels), never on an open-top
        // crevasse floor where it reads as pre-fallen snow and muddies the future walk-triggered trap fiction.
        // The _WG heightmaps a surface_relative filter reads are FROZEN at the pre-carve surface (carvers use
        // FINAL_HEIGHTMAPS -- verified against the 26.2 ChunkStatus), so they CANNOT tell an open crevasse
        // floor from a roofed one. The sky-occlusion gate is instead an UP environment_scan that must find a
        // ceiling (sturdy DOWN face) within its reach: an open-top floor has only air above and is DROPPED;
        // a roofed floor finds the ceiling, steps one below it, then a DOWN scan lands the disk on the floor.
        List<JsonObject> scans = modifiers(chain, "minecraft:environment_scan");
        assertEquals(2, scans.size(),
                "roofed gate = UP ceiling scan + DOWN floor scan (the trap needs a real roof above it)");
        JsonObject up = scans.get(0);
        assertEquals("up", up.get("direction_of_search").getAsString(), "first scan requires a ceiling above");
        assertEquals("down", up.getAsJsonObject("target_condition").get("direction").getAsString(),
                "the ceiling is the block with a sturdy DOWN face -- open-top floors have none and drop");
        assertEquals(-1, modifier(chain, "minecraft:random_offset").get("y_spread").getAsInt(),
                "step one below the ceiling into the air cell before scanning down to the floor");
        JsonObject down = scans.get(1);
        assertEquals("down", down.get("direction_of_search").getAsString(), "then find the floor below");
        assertEquals("up", down.getAsJsonObject("target_condition").get("direction").getAsString(),
                "disk sits AT the floor (sturdy UP face, ice_patch idiom)");
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
        // S49 exception: lichen is STONE-legal (not ice-family), so it alone keeps the full column --
        // sub-Y0 lichen on deepslate is vanilla's own look, and the owner's "no ice in the deepslate
        // layers" ruling names icicles/ice/snow, not glow lichen.
        assertEquals("minecraft:biome", chain.get(chain.size() - 1).get("type").getAsString(),
                "glacial_glow_lichen: the biome filter must be the LAST placement modifier");
        JsonObject lichenHeight = modifier(chain, "minecraft:height_range").getAsJsonObject("height");
        assertTrue(lichenHeight.getAsJsonObject("max_inclusive").get("absolute").getAsInt() >= 110,
                "glacial_glow_lichen: S38 -- the range must still cover the p95 surface band");
        assertTrue(lichenHeight.getAsJsonObject("min_inclusive").has("above_bottom"),
                "glacial_glow_lichen: full column (the one S49 exception, stone-legal)");
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
        assertEquals(19, globeFeatures, "nineteen globe features after S50 added the magma quench sweep to the S48 eighteen: "
                + "(icicle_cluster needles + the PROVISIONAL low-rate ice_spear_patch) onto the S45 nineteen: the S44 nine "
                + "(hanging_icicles, snow_drift, powder_pocket, frost_carpet, slush_floe, cave_drop_trap, "
                + "glow_lichen + 2 ice blobs) + frost_bloom, brine_pool (lakes), ice_geode (step 2), "
                + "moraine stone/gravel + ice-ore coal/iron/copper (step 6), ice_spire_cluster, ice_spire "
                + "(step 7), no silent drops");
    }

    /** The cave-only lake fringe is a custom feature because single data-pack blocks cannot reason about a
     *  connected water body, its shore distance, or the owner chunk. */
    @Test
    void glacialLakeIceFringeUsesTheCustomOwnerChunkFeature() {
        JsonObject config = configured("glacial_lake_ice_fringe");
        assertEquals("globe:glacial_lake_ice_fringe", config.get("type").getAsString(),
                "connected shore fringes require the custom feature, not a data-only single block");

        JsonObject placedJson = placed("glacial_lake_ice_fringe");
        assertEquals("globe:glacial_lake_ice_fringe", placedJson.get("feature").getAsString());
        List<JsonObject> chain = placementChain(placedJson);
        assertTrue(chain.isEmpty(),
                "invoke once at the stable chunk origin; the feature owns all Y and per-cell biome gates");
    }

    /** Polar Barrens keeps the older data-only floe: the cave migration must not silently delete that
     *  separate placement contract. */
    @Test
    void slushFloesSpeckleWaterSurfacesWithoutFreezingThePool() {
        JsonObject config = configured("glacial_slush_floe");
        assertEquals("minecraft:simple_block", config.get("type").getAsString(),
                "the floe is a single-block placement, not a patch (small chunks in the water)");
        assertEquals("minecraft:ice", config.getAsJsonObject("config").getAsJsonObject("to_place")
                        .getAsJsonObject("state").get("Name").getAsString(),
                "plain ice reads as a floe -- NOT packed/blue ice (those are the glacier body strata)");

        JsonObject placedJson = placed("glacial_slush_floe");
        assertEquals("globe:glacial_slush_floe", placedJson.get("feature").getAsString());
        List<JsonObject> chain = placementChain(placedJson);
        assertCaveBandPlacement("glacial_slush_floe", chain);
        assertTrue(modifier(chain, "minecraft:count").get("count").getAsInt() <= 6,
                "floes are SPARSE -- little ice chunks dotting the surface, never a lid over the pool");
        JsonObject scan = modifier(chain, "minecraft:environment_scan");
        assertEquals("down", scan.get("direction_of_search").getAsString(), "scan DOWN to the pool surface");
        assertEquals("minecraft:matching_fluids", scan.getAsJsonObject("target_condition").get("type").getAsString(),
                "the scan lands on the water surface");
        assertEquals("minecraft:water", scan.getAsJsonObject("target_condition").get("fluids").getAsString());
        assertEquals(1, modifier(chain, "minecraft:random_offset").get("y_spread").getAsInt(),
                "step +1 into the air cell above the water (SimpleBlockFeature places only into air)");
        JsonObject predicate = modifier(chain, "minecraft:block_predicate_filter").getAsJsonObject("predicate");
        assertEquals("minecraft:all_of", predicate.get("type").getAsString());
        boolean airHere = false;
        boolean waterBelow = false;
        for (JsonElement pe : predicate.getAsJsonArray("predicates")) {
            JsonObject p = pe.getAsJsonObject();
            if ("minecraft:matching_block_tag".equals(p.get("type").getAsString())
                    && "minecraft:air".equals(p.get("tag").getAsString())) {
                airHere = true;
            }
            if ("minecraft:matching_fluids".equals(p.get("type").getAsString())
                    && "minecraft:water".equals(p.get("fluids").getAsString())
                    && p.getAsJsonArray("offset").get(1).getAsInt() == -1) {
                JsonArray offset = p.getAsJsonArray("offset");
                assertEquals(0, offset.get(0).getAsInt());
                assertEquals(-1, offset.get(1).getAsInt(), "water must be DIRECTLY BELOW the floe cell");
                assertEquals(0, offset.get(2).getAsInt());
                waterBelow = true;
            }
        }
        assertTrue(airHere, "floe cell must be air (the only cell SimpleBlockFeature accepts)");
        assertTrue(waterBelow, "the old floe remains immediately over water, never overwriting it");
    }

    /** S24 GLACIAL ICE BLOBS: ore-type blobs of packed_ice (common, big) and blue_ice (rarer, small)
     *  attached across the whole glacial_caves Y band so even the deep noise caverns read glacial in every
     *  wall. Grammar verbatim-mirrored from vanilla {@code ore_granite}/{@code ore_gravel} (the loom merged
     *  jar): {@code minecraft:ore} config with {@code size}, {@code discard_chance_on_air_exposure} and a
     *  {@code targets} list of {state, tag_match target}. Ints are PLAIN NUMBERS. */
    @Test
    void iceBlobsAreOreTypeGlacierVeins() {
        for (String name : new String[]{"glacial_ice_blob", "glacial_blue_ice_blob"}) {
            JsonObject config = configured(name);
            assertEquals("minecraft:ore", config.get("type").getAsString(), name + " is an ore-type blob");
            JsonObject c = config.getAsJsonObject("config");
            assertTrue(c.has("discard_chance_on_air_exposure"),
                    name + ": ore config requires discard_chance_on_air_exposure");
            assertEquals(0.0, c.get("discard_chance_on_air_exposure").getAsDouble(), 1e-9,
                    name + ": keep blobs even when air-exposed so the ice shows in cave WALLS");
            assertTrue(c.get("size").getAsInt() > 0, name + ": ore config requires a positive size");
            JsonArray targets = c.getAsJsonArray("targets");
            assertEquals(1, targets.size(), name + ": one target (glacier ice replaces base stone)");
            JsonObject t0 = targets.get(0).getAsJsonObject();
            String block = t0.getAsJsonObject("state").get("Name").getAsString();
            assertEquals(name.contains("blue") ? "minecraft:blue_ice" : "minecraft:packed_ice", block,
                    name + ": the blob block");
            JsonObject target = t0.getAsJsonObject("target");
            assertEquals("minecraft:tag_match", target.get("predicate_type").getAsString());
            assertEquals("minecraft:base_stone_overworld", target.get("tag").getAsString(),
                    name + ": veins replace base stone only (ores/deepslate-ores keep their homes)");

            JsonObject placedJson = placed(name);
            assertEquals("globe:" + name, placedJson.get("feature").getAsString());
            List<JsonObject> chain = placementChain(placedJson);
            assertDeepCaveBandPlacement(name, chain);
            assertTrue(chain.stream().anyMatch(m -> "minecraft:count".equals(m.get("type").getAsString())),
                    name + ": count-based placement fills the band");
        }
        // Packed ice is the COMMON, larger vein; blue ice is the RARER, smaller one (compression banding).
        assertTrue(configured("glacial_ice_blob").getAsJsonObject("config").get("size").getAsInt()
                        > configured("glacial_blue_ice_blob").getAsJsonObject("config").get("size").getAsInt(),
                "packed-ice blobs are larger than blue-ice blobs");
        assertTrue(modifier(placementChain(placed("glacial_ice_blob")), "minecraft:count").get("count").getAsInt()
                        > modifier(placementChain(placed("glacial_blue_ice_blob")), "minecraft:count").get("count").getAsInt(),
                "packed ice is common, blue ice is rarer");
    }

    /** S24 FROST FLOOR CARPET: dusts cave floors with randomized snow layers and OCCASIONAL ice patches,
     *  floor-scanned underground placement (snow_drift idiom). block_pile weighted_state_provider. */
    @Test
    void frostCarpetDustsFloorsWithSnowAndOccasionalIce() {
        JsonObject config = configured("glacial_frost_carpet");
        assertEquals("minecraft:block_pile", config.get("type").getAsString());
        JsonArray entries = config.getAsJsonObject("config").getAsJsonObject("state_provider")
                .getAsJsonArray("entries");
        boolean hasSnow = false;
        boolean hasIce = false;
        int snowWeight = 0;
        int iceWeight = 0;
        for (var e : entries) {
            JsonObject entry = e.getAsJsonObject();
            String block = entry.getAsJsonObject("data").get("Name").getAsString();
            int weight = entry.get("weight").getAsInt();
            if ("minecraft:snow".equals(block)) {
                hasSnow = true;
                snowWeight += weight;
                int layers = Integer.parseInt(entry.getAsJsonObject("data")
                        .getAsJsonObject("Properties").get("layers").getAsString());
                assertTrue(layers >= 1 && layers <= 3, "carpet snow stays shallow (layers 1-3)");
            } else if ("minecraft:ice".equals(block)) {
                hasIce = true;
                iceWeight += weight;
            }
        }
        assertTrue(hasSnow && hasIce, "the carpet is snow layers PLUS occasional ice patches");
        assertTrue(iceWeight < snowWeight, "ice is OCCASIONAL -- snow dominates the floor dusting");

        JsonObject placedJson = placed("glacial_frost_carpet");
        assertEquals("globe:glacial_frost_carpet", placedJson.get("feature").getAsString());
        List<JsonObject> chain = placementChain(placedJson);
        assertCaveBandPlacement("glacial_frost_carpet", chain);
        assertEquals("down", modifier(chain, "minecraft:environment_scan")
                .get("direction_of_search").getAsString(), "carpet dusts the FLOOR (scan down)");
        assertEquals(1, modifier(chain, "minecraft:random_offset").get("y_spread").getAsInt(),
                "step one above the floor block into the air cell");
    }
}
