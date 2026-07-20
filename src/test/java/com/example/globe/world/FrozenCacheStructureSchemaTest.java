package com.example.globe.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-9 P2 "frozen expedition cache" schema tripwires -- the first-loot single-piece jigsaw structure
 * (structure + structure_set + template_pool + biome tag + loot table + the PROGRAMMATICALLY authored
 * template NBT). Every JSON grammar is verbatim-mirrored from 26.2 vanilla files extracted from the
 * loom merged jar ({@code trail_ruins.json} for the underground jigsaw form, {@code igloos.json} for
 * random_spread, {@code trail_ruins/tower.json} for the pool element form, {@code igloo_chest.json}
 * for the loot form); the template NBT schema is mirrored from {@code igloo/bottom.nbt} (root
 * {size, entities, blocks, palette, DataVersion}) and read back here through Minecraft's OWN
 * {@link NbtIo} -- the same reader the structure manager uses at load, so a malformed template fails
 * in the suite instead of at first placement. The block-entity loot key is pinned to "LootTable"
 * (verified: {@code RandomizableContainer.LOOT_TABLE_TAG == "LootTable"} in the 26.2 bytecode).
 */
class FrozenCacheStructureSchemaTest {

    private static final String LOOT_TABLE_ID = "globe:chests/frozen_expedition_cache";

    /** NO op loot (owner law): the cache is flavor-tier -- leather, coal, string, packed ice, a few
     *  emeralds, the odd name tag. Anything outside this allowlist fails the pin. */
    private static final Set<String> LOOT_ALLOWLIST = Set.of(
            "minecraft:coal", "minecraft:string", "minecraft:packed_ice", "minecraft:emerald",
            "minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings",
            "minecraft:leather_boots", "minecraft:name_tag");

    private static JsonObject loadJson(String resourcePath) {
        InputStream stream = FrozenCacheStructureSchemaTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "must be on the classpath (main resources): " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void structureJsonIsAWellFormedUndergroundJigsaw() {
        JsonObject structure = loadJson("/data/globe/worldgen/structure/frozen_cache.json");
        assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
        assertEquals("#globe:has_structure/frozen_cache", structure.get("biomes").getAsString());
        assertEquals("globe:frozen_cache/campsite", structure.get("start_pool").getAsString());
        assertEquals("underground_structures", structure.get("step").getAsString());
        assertEquals(1, structure.get("size").getAsInt(), "single-piece jigsaw");
        int maxDistance = structure.get("max_distance_from_center").getAsInt();
        assertTrue(maxDistance >= 1 && maxDistance <= 128, "codec range [1,128]");
        assertTrue(structure.getAsJsonObject("start_height").has("absolute"),
                "start_height is a VerticalAnchor OBJECT (trail_ruins form)");
        // TEST 114 sweep RECOMMEND: the original surface-projected -16 buried the cache in the glacier
        // BODY, but the cache is the CAVES' reward — it now starts on a deep ABSOLUTE height with no
        // heightmap projection (ancient_city precedent), inside the glacial_caves band (below the Y48
        // biome-swap ceiling, within the 30-90 tunnel band).
        assertFalse(structure.has("project_start_to_heightmap"),
                "no surface projection — the cache lives at a fixed depth in the caves");
        int startY = structure.get("start_height").getAsJsonObject().get("absolute").getAsInt();
        assertTrue(startY < 48 && startY >= 20,
                "absolute start must sit inside the glacial_caves band (below the Y48 swap ceiling)");
        assertTrue(structure.has("spawn_overrides"), "required codec field");
        assertTrue(structure.has("terrain_adaptation"), "bury = entombed (and carvers may re-expose it)");
    }

    @Test
    void structureSetIsRareRandomSpread() {
        JsonObject set = loadJson("/data/globe/worldgen/structure_set/frozen_caches.json");
        JsonObject placement = set.getAsJsonObject("placement");
        assertEquals("minecraft:random_spread", placement.get("type").getAsString());
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(spacing > separation, "codec law: spacing must exceed separation or worldgen crashes");
        assertTrue(spacing >= 40, "RARE spacing (first loot, not a common drop)");
        assertTrue(placement.get("salt").getAsInt() != 0, "dedicated placement salt");
        assertEquals("globe:frozen_cache",
                set.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString());
    }

    @Test
    void templatePoolPointsAtTheCampsitePiece() {
        JsonObject pool = loadJson("/data/globe/worldgen/template_pool/frozen_cache/campsite.json");
        assertEquals("minecraft:empty", pool.get("fallback").getAsString());
        JsonArray elements = pool.getAsJsonArray("elements");
        assertEquals(1, elements.size());
        JsonObject element = elements.get(0).getAsJsonObject().getAsJsonObject("element");
        assertEquals("minecraft:single_pool_element", element.get("element_type").getAsString());
        assertEquals("globe:frozen_cache/campsite", element.get("location").getAsString());
        assertEquals("minecraft:empty", element.get("processors").getAsString());
        assertEquals("rigid", element.get("projection").getAsString());
    }

    @Test
    void biomeTagListsBothPolarHosts() {
        JsonObject tag = loadJson("/data/globe/tags/worldgen/biome/has_structure/frozen_cache.json");
        List<String> values = new ArrayList<>();
        tag.getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
        assertTrue(values.contains("globe:glacial_caves"),
                "the cave biome hosts the cache underground");
        assertTrue(values.contains("globe:polar_barrens"),
                "the barrens must ALSO be listed: structure eligibility samples the SOURCE-path biome, "
                        + "which emits the surface identity -- without it the cache would never place");
    }

    @Test
    void lootTableIsFlavorTierOnly() {
        JsonObject loot = loadJson("/data/globe/loot_table/chests/frozen_expedition_cache.json");
        assertEquals("minecraft:chest", loot.get("type").getAsString());
        JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertTrue(pool.has("rolls"), "loot pool requires rolls (igloo_chest form)");
        List<String> names = new ArrayList<>();
        for (var entry : pool.getAsJsonArray("entries")) {
            JsonObject e = entry.getAsJsonObject();
            assertEquals("minecraft:item", e.get("type").getAsString());
            names.add(e.get("name").getAsString());
        }
        for (String name : names) {
            assertTrue(LOOT_ALLOWLIST.contains(name),
                    "NO op loot (owner law): unexpected entry " + name);
        }
        for (String required : new String[]{"minecraft:coal", "minecraft:string", "minecraft:emerald",
                "minecraft:packed_ice", "minecraft:name_tag"}) {
            assertTrue(names.contains(required), "curated list must include " + required);
        }
        assertTrue(names.stream().anyMatch(n -> n.startsWith("minecraft:leather_")),
                "leather armor pieces are the cache's signature find");
    }

    @Test
    void templateNbtParsesThroughMinecraftsOwnReaderWithLootWiredChest() throws IOException {
        CompoundTag root;
        try (InputStream stream = FrozenCacheStructureSchemaTest.class.getResourceAsStream(
                "/data/globe/structure/frozen_cache/campsite.nbt")) {
            assertNotNull(stream, "campsite.nbt must be on the classpath (main resources)");
            root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        }

        ListTag size = root.getListOrEmpty("size");
        assertEquals(3, size.size(), "size is [x,y,z]");
        assertEquals(7, size.getIntOr(0, -1));
        assertEquals(5, size.getIntOr(1, -1));
        assertEquals(7, size.getIntOr(2, -1));
        assertEquals(4903, root.getIntOr("DataVersion", -1),
                "the 26.2 world_version (loom jar version.json) -- a stale template would trip "
                        + "DataFixer churn at load");
        assertEquals(0, root.getListOrEmpty("entities").size(), "no entities in the collapsed camp");

        ListTag palette = root.getListOrEmpty("palette");
        assertTrue(palette.size() > 0, "palette present");
        boolean chestInPalette = false;
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.getCompoundOrEmpty(i).getStringOr("Name", "");
            chestInPalette |= "minecraft:chest".equals(name);
        }
        assertTrue(chestInPalette, "the loot chest block must be in the palette");

        ListTag blocks = root.getListOrEmpty("blocks");
        assertTrue(blocks.size() > 0, "blocks present");
        boolean lootChest = false;
        boolean lootBarrel = false;
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            assertEquals(3, pos.size());
            int x = pos.getIntOr(0, -1);
            int y = pos.getIntOr(1, -1);
            int z = pos.getIntOr(2, -1);
            assertTrue(x >= 0 && x < 7 && y >= 0 && y < 5 && z >= 0 && z < 7,
                    "block pos in bounds: " + x + "," + y + "," + z);
            int state = block.getIntOr("state", -1);
            assertTrue(state >= 0 && state < palette.size(), "state index in palette range");
            CompoundTag nbt = block.getCompoundOrEmpty("nbt");
            String id = nbt.getStringOr("id", "");
            if ("minecraft:chest".equals(id)) {
                lootChest = true;
                assertEquals(LOOT_TABLE_ID, nbt.getStringOr("LootTable", ""),
                        "chest must reference the curated loot table via the LootTable key");
            }
            if ("minecraft:barrel".equals(id)) {
                lootBarrel = true;
                assertEquals(LOOT_TABLE_ID, nbt.getStringOr("LootTable", ""),
                        "barrel shares the curated table");
            }
        }
        assertTrue(lootChest, "exactly the promised first-loot chest must exist");
        assertTrue(lootBarrel, "the campsite barrel must exist");
    }

    @Test
    void lootTableIdMatchesTheFileTheTemplateReferences() {
        // The chest nbt points at globe:chests/frozen_expedition_cache -- which must resolve to
        // data/globe/loot_table/chests/frozen_expedition_cache.json (26.2 singular loot_table dir,
        // verified against the vanilla jar layout). A rename on either side breaks silently (empty
        // chests), so pin the pair.
        assertNotNull(FrozenCacheStructureSchemaTest.class.getResourceAsStream(
                        "/data/globe/loot_table/chests/frozen_expedition_cache.json"),
                "the loot table file the template's LootTable key references must exist");
    }
}
