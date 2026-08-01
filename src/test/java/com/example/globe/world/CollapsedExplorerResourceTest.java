package com.example.globe.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resource and wiring tripwires for the static collapsed explorer beside the rare cave-trap treasure.
 *
 * <p>The visual is intentionally hybrid: the common paired block owns worldgen, persistence, selection,
 * collision, and removal; a client-only block-entity renderer submits one frozen vanilla SkeletonModel.
 */
class CollapsedExplorerResourceTest {

    private static final String HOLDER_CLASS = "com.example.globe.world.CollapsedExplorerBlocks";
    private static final String BLOCK_CLASS = "com.example.globe.world.CollapsedExplorerBlock";
    private static final String BLOCK_ENTITY_CLASS =
            "com.example.globe.world.CollapsedExplorerBlockEntity";
    private static final String RENDERER_CLASS =
            "com.example.globe.client.CollapsedExplorerBlockEntityRenderer";

    private static final Map<String, Integer> PREMIUM_LIMITS = Map.of(
            "minecraft:diamond", 1,
            "minecraft:golden_apple", 1,
            "minecraft:emerald", 6,
            "minecraft:gold_ingot", 4);
    private static final Map<String, Integer> PREMIUM_MINIMUMS = Map.of(
            "minecraft:diamond", 1,
            "minecraft:golden_apple", 1,
            "minecraft:emerald", 4,
            "minecraft:gold_ingot", 4);

    private static final Set<String> FORBIDDEN_EXACT_LOOT = Set.of(
            "minecraft:elytra",
            "minecraft:enchanted_book",
            "minecraft:totem_of_undying",
            "minecraft:trident",
            "minecraft:diamond_sword",
            "minecraft:diamond_pickaxe",
            "minecraft:diamond_axe",
            "minecraft:diamond_shovel",
            "minecraft:diamond_hoe",
            "minecraft:diamond_helmet",
            "minecraft:diamond_chestplate",
            "minecraft:diamond_leggings",
            "minecraft:diamond_boots");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void commonRegistrationUsesOneNonTickingPersistentBlockEntityType() throws Exception {
        Class<?> holder = requiredClass(HOLDER_CLASS);
        Class<?> blockClass = requiredClass(BLOCK_CLASS);
        Class<?> blockEntityClass = requiredClass(BLOCK_ENTITY_CLASS);

        Method register = holder.getDeclaredMethod("register");
        assertTrue(Modifier.isPublic(register.getModifiers()) && Modifier.isStatic(register.getModifiers()),
                "CollapsedExplorerBlocks.register() remains the unconditional common initialization seam");
        Field block = holder.getDeclaredField("COLLAPSED_EXPLORER");
        assertEquals("net.minecraft.world.level.block.Block", block.getType().getName());
        Field blockEntityType = holder.getDeclaredField("COLLAPSED_EXPLORER_BLOCK_ENTITY");
        assertEquals("net.minecraft.world.level.block.entity.BlockEntityType",
                blockEntityType.getType().getName());

        assertEquals("net.minecraft.world.level.block.HorizontalDirectionalBlock",
                blockClass.getSuperclass().getName());
        assertTrue(EntityBlock.class.isAssignableFrom(blockClass),
                "the paired block creates persistence-safe render anchors");
        assertEquals("net.minecraft.world.level.block.entity.BlockEntity",
                blockEntityClass.getSuperclass().getName());
        assertEquals(0, blockEntityClass.getDeclaredMethods().length,
                "the render anchor has no ticker, custom persistence, packets, inventory, or behavior");

        String holderBytecode = classfileText(HOLDER_CLASS);
        String blockBytecode = classfileText(BLOCK_CLASS);
        String blockEntityBytecode = classfileText(BLOCK_ENTITY_CLASS);
        assertTrue(holderBytecode.contains("collapsed_explorer")
                        && holderBytecode.contains("BLOCK_ENTITY_TYPE")
                        && holderBytecode.contains("BlockEntityType"),
                "the block and its exact block-entity type are both registered");
        assertTrue(blockBytecode.contains("newBlockEntity")
                        && blockBytecode.contains("FACING")
                        && blockBytecode.contains("PART"),
                "both paired states create the registered anchor and persist facing/half in block state");
        assertTrue(blockBytecode.contains("getCollisionShape")
                        && blockBytecode.contains("shapeFor")
                        && blockBytecode.contains("updateShape")
                        && blockBytecode.contains("isMatchingPartner"),
                "physical targeting plus paired orphan cleanup remain common block behavior");
        for (String bytecode : new String[]{holderBytecode, blockBytecode, blockEntityBytecode}) {
            assertFalse(bytecode.contains("net/minecraft/client/"),
                    "common classes must not link client rendering code");
            assertFalse(bytecode.contains("net/minecraft/world/entity/EntityType"),
                    "the static prop must not register or create a mob/entity");
        }

        String globeModBytecode = classfileText("com.example.globe.GlobeMod");
        assertTrue(globeModBytecode.contains("CollapsedExplorerBlocks")
                        && globeModBytecode.contains("register"),
                "GlobeMod registers the block and render-anchor type before worldgen references them");
        assertFalse(resourceExists("/assets/globe/items/collapsed_explorer.json"));
        assertFalse(resourceExists("/assets/globe/models/item/collapsed_explorer.json"));
        assertFalse(resourceExists("/data/globe/loot_table/blocks/collapsed_explorer.json"),
                "breaking the worldgen-only remains still drops nothing");
    }

    @Test
    void clientRendererSubmitsOneActualVanillaSkeletonWithoutAnEntity() throws IOException {
        String rendererBytecode = classfileText(RENDERER_CLASS);
        assertTrue(rendererBytecode.contains("ModelLayers")
                        && rendererBytecode.contains("SKELETON")
                        && rendererBytecode.contains("SkeletonModel"),
                "the renderer bakes and submits Minecraft's actual vanilla SkeletonModel layer");
        assertTrue(rendererBytecode.contains("textures/entity/skeleton/skeleton.png"),
                "the renderer uses the vanilla skeleton texture");
        assertTrue(rendererBytecode.contains("submitModel")
                        && rendererBytecode.contains("HEAD")
                        && rendererBytecode.contains("shouldRenderOffScreen"),
                "only the canonical HEAD anchor submits the two-cell model and opts out of one-cell culling");
        for (String forbidden : new String[]{
                "EntityType", "LivingEntity", "Mob", "Goal", "Equipment", "ItemStack", "LootTable"}) {
            assertFalse(rendererBytecode.contains(forbidden),
                    "the visual path must not acquire entity/gameplay behavior: " + forbidden);
        }

        String clientInitializer = classfileText("com.example.globe.GlobeModClient");
        assertTrue(clientInitializer.contains("BlockEntityRendererRegistry")
                        && clientInitializer.contains("CollapsedExplorerBlockEntityRenderer")
                        && clientInitializer.contains("COLLAPSED_EXPLORER_BLOCK_ENTITY"),
                "the renderer is registered only from the client initializer");
    }

    @Test
    void pairedBlockModelsAreEmptyAnchorsAndAllEightStatesResolve() {
        JsonObject variants = loadJson("/assets/globe/blockstates/collapsed_explorer.json")
                .getAsJsonObject("variants");
        assertNotNull(variants, "blockstate needs explicit facing and HEAD/FOOT variants");

        Map<String, String> expected = new LinkedHashMap<>();
        for (BedPart part : BedPart.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                expected.put(
                        "facing=" + facing.getSerializedName() + ",part=" + part.getSerializedName(),
                        "globe:block/collapsed_explorer_" + part.getSerializedName());
            }
        }
        assertEquals(expected.keySet(), variants.keySet(),
                "exactly four facings for each of HEAD and FOOT");
        expected.forEach((state, model) ->
                assertEquals(model, variants.getAsJsonObject(state).get("model").getAsString(), state));

        for (String part : new String[]{"head", "foot"}) {
            JsonObject model = loadJson("/assets/globe/models/block/collapsed_explorer_" + part + ".json");
            assertFalse(model.get("ambientocclusion").getAsBoolean());
            assertEquals("minecraft:block/bone_block",
                    model.getAsJsonObject("textures").get("particle").getAsString());
            assertTrue(model.getAsJsonArray("elements").isEmpty(),
                    "JSON remains an invisible anchor; the vanilla skeleton renderer owns all geometry");
            String rawModel = model.toString();
            assertFalse(rawModel.contains("globe:block/collapsed_explorer"),
                    "the rejected custom sculpture textures are no longer referenced");
        }
    }

    @Test
    void pairedCollisionSelectionAndOrphanLawRemainIntact() {
        for (BedPart part : BedPart.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = CollapsedExplorerBlock.shapeFor(facing, part);
                assertFalse(shape.isEmpty(), facing + " " + part + " keeps a physical footprint");
                assertTrue(shape.bounds().maxY <= 9.0 / 16.0,
                        facing + " " + part + " stays below the existing step-over ceiling");
            }
        }
        assertMethodInvokes("getShape", "shapeFor",
                "selection/raycast delegates to the preserved paired shape");
        assertMethodInvokes("getCollisionShape", "shapeFor",
                "collision delegates to the preserved paired shape");

        assertEquals(Direction.NORTH,
                CollapsedExplorerBlock.partnerDirection(Direction.NORTH, BedPart.FOOT));
        assertEquals(Direction.SOUTH,
                CollapsedExplorerBlock.partnerDirection(Direction.NORTH, BedPart.HEAD));
        assertTrue(CollapsedExplorerBlock.isMatchingPartner(
                Direction.NORTH, BedPart.FOOT, Direction.NORTH, BedPart.HEAD));
        assertTrue(CollapsedExplorerBlock.isMatchingPartner(
                Direction.NORTH, BedPart.HEAD, Direction.NORTH, BedPart.FOOT));
        assertFalse(CollapsedExplorerBlock.isMatchingPartner(
                Direction.NORTH, BedPart.FOOT, Direction.SOUTH, BedPart.HEAD));
        assertFalse(CollapsedExplorerBlock.isMatchingPartner(
                Direction.NORTH, BedPart.FOOT, Direction.NORTH, BedPart.FOOT));
        assertMethodInvokes("updateShape", "isMatchingPartner",
                "the real neighbour adapter removes orphaned or mismatched halves");
    }

    @Test
    void treasureGuaranteesOneBoundedPremiumAndForbidsPowerLoot() {
        JsonObject loot = loadJson("/data/globe/loot_table/chests/collapsed_explorer_treasure.json");
        assertEquals("minecraft:chest", loot.get("type").getAsString());
        JsonArray pools = loot.getAsJsonArray("pools");
        assertNotNull(pools);
        assertFalse(pools.isEmpty(), "treasure table needs at least one pool");

        int guaranteedPremiumPools = 0;
        Set<String> premiumItemsSeen = new HashSet<>();
        for (JsonElement poolValue : pools) {
            JsonObject pool = poolValue.getAsJsonObject();
            JsonArray entries = pool.getAsJsonArray("entries");
            assertNotNull(entries, "every loot pool declares entries");
            boolean premiumOnly = !entries.isEmpty();
            for (JsonElement entryValue : entries) {
                JsonObject entry = entryValue.getAsJsonObject();
                if (!"minecraft:item".equals(entry.get("type").getAsString())) {
                    premiumOnly = false;
                    continue;
                }
                String item = entry.get("name").getAsString();
                assertFalse(isForbiddenPowerLoot(item), "forbidden high-power loot: " + item);
                Integer premiumLimit = PREMIUM_LIMITS.get(item);
                if (premiumLimit == null) {
                    premiumOnly = false;
                } else {
                    premiumItemsSeen.add(item);
                    assertTrue(minimumCount(entry) >= PREMIUM_MINIMUMS.get(item));
                    assertTrue(maximumCount(entry) <= premiumLimit);
                }
            }
            if (premiumOnly && rollMinimum(pool.get("rolls")) == 1
                    && rollMaximum(pool.get("rolls")) == 1) {
                guaranteedPremiumPools++;
            }
        }
        assertEquals(PREMIUM_LIMITS.keySet(), premiumItemsSeen);
        assertEquals(1, guaranteedPremiumPools);

        String raw = loot.toString().toLowerCase();
        for (String forbiddenToken : new String[]{
                "netherite", "elytra", "mending", "smithing_template", "shulker_box",
                "enchanted_golden_apple", "enchanted_book", "spawner", "trial_spawner",
                "ominous_bottle"}) {
            assertFalse(raw.contains(forbiddenToken), "loot table must not contain " + forbiddenToken);
        }
    }

    private static boolean isForbiddenPowerLoot(String item) {
        return FORBIDDEN_EXACT_LOOT.contains(item)
                || item.startsWith("minecraft:netherite_")
                || item.endsWith("_smithing_template")
                || item.endsWith("shulker_box");
    }

    private static int maximumCount(JsonObject entry) {
        return countBound(entry, "max");
    }

    private static int minimumCount(JsonObject entry) {
        return countBound(entry, "min");
    }

    private static int countBound(JsonObject entry, String bound) {
        if (!entry.has("functions")) {
            return 1;
        }
        int result = 1;
        for (JsonElement functionValue : entry.getAsJsonArray("functions")) {
            JsonObject function = functionValue.getAsJsonObject();
            if (!"minecraft:set_count".equals(function.get("function").getAsString())) {
                continue;
            }
            JsonElement count = function.get("count");
            result = count.isJsonPrimitive()
                    ? count.getAsInt()
                    : count.getAsJsonObject().get(bound).getAsInt();
        }
        return result;
    }

    private static int rollMinimum(JsonElement rolls) {
        return rolls.isJsonPrimitive()
                ? rolls.getAsInt()
                : rolls.getAsJsonObject().get("min").getAsInt();
    }

    private static int rollMaximum(JsonElement rolls) {
        return rolls.isJsonPrimitive()
                ? rolls.getAsInt()
                : rolls.getAsJsonObject().get("max").getAsInt();
    }

    private static JsonObject loadJson(String resourcePath) {
        InputStream stream = CollapsedExplorerResourceTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "missing required resource: " + resourcePath);
        return JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void assertMethodInvokes(
            String methodName, String invokedMethodName, String message) {
        byte[] bytes;
        try (InputStream stream = CollapsedExplorerResourceTest.class.getResourceAsStream(
                "/com/example/globe/world/CollapsedExplorerBlock.class")) {
            assertNotNull(stream, "compiled collapsed-explorer block is available for wiring proof");
            bytes = stream.readAllBytes();
        } catch (IOException exception) {
            throw new AssertionError("compiled collapsed-explorer block must be readable", exception);
        }
        var classModel = java.lang.classfile.ClassFile.of().parse(bytes);
        var method = classModel.methods().stream()
                .filter(candidate ->
                        candidate.methodName().equalsString(methodName) && candidate.code().isPresent())
                .findFirst().orElseThrow(() -> new AssertionError(
                        "block has a compiled " + methodName + " adapter"));
        String owner = CollapsedExplorerBlock.class.getName().replace('.', '/');
        boolean delegates = method.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .anyMatch(call -> call.owner().asInternalName().equals(owner)
                        && call.name().equalsString(invokedMethodName));
        assertTrue(delegates, message);
    }

    private static Class<?> requiredClass(String name) {
        try {
            return Class.forName(name, false, CollapsedExplorerResourceTest.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("RED: missing collapsed-explorer production class " + name, missing);
        }
    }

    private static String classfileText(String className) throws IOException {
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream stream = CollapsedExplorerResourceTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "compiled class must be readable: " + className);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean resourceExists(String resourcePath) throws IOException {
        try (InputStream stream = CollapsedExplorerResourceTest.class.getResourceAsStream(resourcePath)) {
            return stream != null;
        }
    }
}
