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
 *   <li><b>Underground stays alive</b> (reskin-plus, never a strip): the lake/geode/ore/spring steps are
 *       EXACTLY polar_barrens' (which are exactly snowy_plains' underground subset). S45 carve-out: the
 *       vanilla monster rooms are EVICTED from both biomes (mossy-cobble palette violation, owner's
 *       mineshaft logic) -- the step-3 equality law still binds, both biomes stripped identically.</li>
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
        // Step 6 (underground_ores) is now a three-course SANDWICH (S45 extends the S24 law):
        //   [vanilla ore run] + [the two S24 ice blobs, caves only] + [the five S45 ore-in-ice features]
        // The vanilla run stays the exact ordered head in BOTH biomes (no ore dropped or reordered). The
        // blobs still run before anything that puts stone back INTO the ice -- so they can never eat a
        // moraine lens -- and the S45 five run LAST: moraine lenses drop stone/gravel debris into the
        // finished ice body, then the ice-targeted veins seed coal/iron/copper into ice and lenses alike
        // (the owner's "TOO much ice without enough ores" rebalance: inclusions, never less ice).
        // S48 (owner 2026-07-26: "yep i agree w/ cut ore veins"): the three bare-ice veins are GONE --
        // rock carries the ore (moraine lenses), ice stays ice, the caves run less forgiving. Geodes stay.
        List<String> s45Ores = List.of("globe:glacial_moraine_stone", "globe:glacial_moraine_gravel");
        List<String> barrensOres = featureStep(barrens, 6);
        List<String> cavesOres = featureStep(caves, 6);
        List<String> vanillaRun = barrensOres.subList(0, barrensOres.size() - s45Ores.size());
        assertEquals(s45Ores, barrensOres.subList(vanillaRun.size(), barrensOres.size()),
                "the barrens ore step ends with the five S45 ore-in-ice features, in order");
        assertEquals(vanillaRun, cavesOres.subList(0, vanillaRun.size()),
                "the vanilla ore run must be the exact ordered HEAD of the caves ore step");
        List<String> cavesTail = new ArrayList<>(List.of("globe:glacial_ice_blob", "globe:glacial_blue_ice_blob"));
        cavesTail.addAll(s45Ores);
        assertEquals(cavesTail, cavesOres.subList(vanillaRun.size(), cavesOres.size()),
                "caves after the vanilla run: the two S24 ice blobs (packed then blue), then the S45 five");
        assertTrue(cavesOres.contains("minecraft:ore_diamond"),
                "sanity: the ore step really is the full ore run");
    }

    @Test
    void dressingStepsCarryTheGlacialFeatures() {
        JsonObject caves = glacialCaves();
        assertEquals(List.of("globe:hanging_icicles", "globe:glacial_snow_drift",
                        "globe:glacial_powder_pocket", "globe:glacial_frost_carpet", "globe:glacial_slush_floe",
                        "globe:cave_drop_trap", "globe:ice_spire_cluster", "globe:ice_spire",
                        "globe:icicle_cluster", "globe:ice_spear_patch", "globe:magma_quench_sweep"),
                featureStep(caves, 7),
                "underground_decoration (step 7): the plain-ice hanging_icicles (reinstated S40 per owner), "
                        + "the floor/pool dressing, the S44 drop trap, the S45 ice spires (cluster before "
                        + "single, vanilla sulfur order; floor forms, not the rejected speleothem silhouette), "
                        + "then the S46 icicle revival: needle clusters (owner GO 2026-07-26, \"really love\") "
                        + "and the LOW-rate floor spear patch (PROVISIONAL -- owner is \"a tad less sold\" on "
                        + "floor ones and judges them live; count stays low until her final call), then the S50 magma quench sweep LAST -- it must run after underwater_magma and every other magma source, or flooded pockets go bare again (the TEST 138 regression)");
        assertEquals(List.of("globe:glacial_glow_lichen"),
                featureStep(caves, 9),
                "vegetal step = the sparse glacial glow_lichen only (punctuation, not illumination). "
                        + "S40 (owner: \"remove pale moss, hanging moss\") deleted both pale-moss atmosphere "
                        + "features that S37 had appended here");
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
        // roster was strays + skeletons ONLY. S45 (2026-07-26) kept the SPECIES law but flipped the WEIGHTS:
        // 26.2 strays require canSeeSky (verified in the jar), so the old 85/15 split voided 85% of every
        // roofed-cave spawn roll -- the owner's "caves a bit sparse" was this table, not a perception.
        // Skeletons (cave-legal, frozen-dead on fiction) carry the roofed deep; the residual stray weight
        // serves the sky-breached shafts ("the ones that got in from above"). S46 (owner, 2026-07-26: "yeah
        // add the drowned and silverfish") widens her own species ceiling: DROWNED self-select into the
        // ponded pools (drowned spawn rules require water -- dry rolls fail free), SILVERFISH lurk as ice
        // vermin at token weight. Both stay cold-fiction; the warm-blooded ban below still binds.
        assertEquals(List.of("minecraft:stray", "minecraft:skeleton", "minecraft:drowned",
                        "minecraft:silverfish"), types,
                "the glacial-caves monster list is [stray, skeleton, drowned, silverfish], in that order");
        assertEquals(12, strayWeight, "residual strays for sky-breached shafts only (weight 12, S45)");
        assertEquals(50, skeletonWeight, "skeletons carry the roofed deep (weight 50, S45 canSeeSky fix)");
        assertTrue(skeletonWeight > strayWeight,
                "the cave-legal skeleton must dominate, or the roofed deep goes silent again (S45)");
        int drownedWeight = -1;
        int silverfishWeight = -1;
        for (var e : monsters) {
            JsonObject entry = e.getAsJsonObject();
            if ("minecraft:drowned".equals(entry.get("type").getAsString())) {
                drownedWeight = entry.get("weight").getAsInt();
            }
            if ("minecraft:silverfish".equals(entry.get("type").getAsString())) {
                silverfishWeight = entry.get("weight").getAsInt();
            }
        }
        assertEquals(10, drownedWeight, "S46 drowned: pool-bound accent (water-gated spawn rules), never lead");
        assertEquals(5, silverfishWeight, "S46 silverfish: token ice-vermin weight");
        assertTrue(skeletonWeight > drownedWeight + silverfishWeight,
                "the skeleton lead outweighs both S46 additions combined -- accents, not a new register");
        // Nothing warm-blooded (or otherwise off-fiction) may appear underground.
        for (String banned : new String[]{"minecraft:zombie", "minecraft:zombie_villager", "minecraft:spider",
                "minecraft:creeper", "minecraft:slime", "minecraft:enderman", "minecraft:witch"}) {
            assertFalse(types.contains(banned),
                    "the frozen-dead roster excludes warm-blooded/off-fiction mob " + banned);
        }
    }

    @Test
    void barrensRosterKeepsTheStrayCutAndGainsCavernSkeletons() {
        JsonObject barrens = polarBarrens();
        JsonArray monsters = barrens.getAsJsonObject("spawners").getAsJsonArray("monster");
        List<String> types = new ArrayList<>();
        int strayWeight = -1;
        int skeletonWeight = -1;
        for (var e : monsters) {
            JsonObject entry = e.getAsJsonObject();
            String type = entry.get("type").getAsString();
            types.add(type);
            if ("minecraft:stray".equals(type)) {
                strayWeight = entry.get("weight").getAsInt();
            }
            if ("minecraft:skeleton".equals(type)) {
                skeletonWeight = entry.get("weight").getAsInt();
            }
        }
        // The SURFACE strays-only law (TEST 103/104) is enforced at RUNTIME by the S13e sky veto
        // (PolarSurfaceSpawns via SpawnPlacementsPolarSurfaceMixin, default ON): any non-stray monster
        // spawning SKY-EXPOSED in the polar band is vetoed. So the skeleton entry here (S45) populates
        // ONLY the roofed Y48+ cavern country -- which, strays being canSeeSky-bound in 26.2, previously
        // had ZERO natural hostiles. The two laws compose; neither bends.
        assertEquals(List.of("minecraft:stray", "minecraft:skeleton"), types,
                "barrens monsters: the S13d stray entry then the S45 cavern skeleton entry");
        assertEquals(27, strayWeight,
                "the S13d barrens stray cut (85 -> 27) is a standing law; S45 must not touch it");
        assertEquals(40, skeletonWeight,
                "S45 cavern skeletons (sky-vetoed on the surface, so cavern-only in practice)");
        JsonArray ambient = barrens.getAsJsonObject("spawners").getAsJsonArray("ambient");
        assertEquals(1, ambient.size(), "S45: bats join the barrens cavern country");
        assertEquals("minecraft:bat", ambient.get(0).getAsJsonObject().get("type").getAsString(),
                "the one barrens ambient entry is the bat (mirrors the glacial_caves entry)");
    }

    @Test
    void monsterRoomsAreEvictedFromBothGlacialBiomes() {
        // S45 palette law: vanilla monster_room / monster_room_deep build COBBLESTONE + MOSSY-cobble
        // dungeons -- green moss in the all-ice underground, the same category of error as the S43
        // mineshafts ("doesn't make much sense") and the S40 moss removals. Both biomes must list neither.
        for (JsonObject biome : new JsonObject[]{glacialCaves(), polarBarrens()}) {
            JsonArray features = biome.getAsJsonArray("features");
            for (var step : features) {
                for (var f : step.getAsJsonArray()) {
                    String id = f.getAsString();
                    assertFalse(id.equals("minecraft:monster_room") || id.equals("minecraft:monster_room_deep"),
                            "monster rooms are evicted from the glacial underground (S45): found " + id);
                }
            }
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
