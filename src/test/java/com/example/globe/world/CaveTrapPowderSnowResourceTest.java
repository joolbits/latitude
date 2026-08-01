package com.example.globe.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused contract proof for the worldgen-only false-floor snow. The path test calls Minecraft's real
 * compiled {@link WalkNodeEvaluator} raw classifier and inspects its support-resolution bytecode. A real mob
 * route remains a headless-world proof.
 */
class CaveTrapPowderSnowResourceTest {

    @Test
    void registrationIsWorldgenOnlyAndUsesVanillaPowderVisuals() throws Exception {
        Method register = CaveTrapBlocks.class.getDeclaredMethod("register");
        assertTrue(Modifier.isPublic(register.getModifiers()) && Modifier.isStatic(register.getModifiers()));
        Field block = CaveTrapBlocks.class.getDeclaredField("CAVE_TRAP_POWDER_SNOW");
        assertTrue(Modifier.isPublic(block.getModifiers()) && Modifier.isStatic(block.getModifiers()));
        assertEquals(CaveTrapPowderSnowBlock.class, block.getType(),
                "the public placement seam keeps its distinct block identity");

        String holderBytecode = classfileText(CaveTrapBlocks.class);
        assertTrue(holderBytecode.contains("cave_trap_powder_snow"));
        assertTrue(holderBytecode.contains("PowderSnowBlock") || holderBytecode.contains("CaveTrapPowderSnowBlock"));
        assertFalse(holderBytecode.contains("BlockItem"), "the trap-only snow cannot enter inventories");

        String globeModBytecode = classfileText(com.example.globe.GlobeMod.class);
        assertTrue(globeModBytecode.contains("CaveTrapBlocks"),
                "GlobeMod unconditionally registers the block before cave features run");
        assertFalse(resourceExists("/assets/globe/items/cave_trap_powder_snow.json"));
        assertFalse(resourceExists("/assets/globe/models/item/cave_trap_powder_snow.json"));
        assertFalse(resourceExists("/data/globe/loot_table/blocks/cave_trap_powder_snow.json"),
                "breaking a worldgen-only false floor yields no loot");

        JsonObject variants = loadJson("/assets/globe/blockstates/cave_trap_powder_snow.json")
                .getAsJsonObject("variants");
        assertEquals(1, variants.size());
        assertEquals("minecraft:block/powder_snow",
                variants.getAsJsonObject("").get("model").getAsString(),
                "the false floor deliberately renders with vanilla powder snow's exact model and texture");
    }

    @Test
    void compiledClassifierTreatsCustomPowderAsSupportInsteadOfOpenAir() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertEquals(PowderSnowBlock.class, CaveTrapPowderSnowBlock.class.getSuperclass(),
                "sinking/freezing/fall behavior comes from the real PowderSnowBlock");
        Method pathMethod = CaveTrapPowderSnowBlock.class.getDeclaredMethod(
                "isPathfindable",
                net.minecraft.world.level.block.state.BlockState.class,
                PathComputationType.class);
        assertTrue(Modifier.isProtected(pathMethod.getModifiers()),
                "the custom delta is the normal block pathfinding API, not a broad navigation hook");

        var customModel = java.lang.classfile.ClassFile.of().parse(
                classfileBytes(CaveTrapPowderSnowBlock.class));
        var customPath = customModel.methods().stream()
                .filter(method -> method.methodName().equalsString("isPathfindable")
                        && method.code().isPresent())
                .findFirst().orElseThrow();
        List<String> customConstants = customPath.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.ConstantInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.ConstantInstruction.class::cast)
                .map(constant -> constant.constantValue().toString())
                .toList();
        long customReturns = customPath.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.ReturnInstruction.class::isInstance)
                .count();
        long customCalls = customPath.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .count();
        assertEquals(List.of("0"), customConstants,
                "the compiled custom path decision must return false");
        assertEquals(1L, customReturns);
        assertEquals(0L, customCalls,
                "the custom path decision must not delegate into broader powder behavior");

        PathType blockedFallback = ExposedWalkNodeEvaluator.classify(
                new SingleStateGetter(Blocks.STONE.defaultBlockState()), BlockPos.ZERO);
        PathType vanillaRaw = ExposedWalkNodeEvaluator.classify(
                new SingleStateGetter(Blocks.POWDER_SNOW.defaultBlockState()), BlockPos.ZERO);
        assertEquals(PathType.BLOCKED, blockedFallback,
                "the real non-vanilla false-pathfindable branch produces solid support");
        assertEquals(PathType.POWDER_SNOW, vanillaRaw,
                "normal powder snow must retain Minecraft's avoided powder path type");

        var vanillaModel = java.lang.classfile.ClassFile.of().parse(classfileBytes(WalkNodeEvaluator.class));
        var classifier = vanillaModel.methods().stream()
                .filter(method -> method.methodName().equalsString("getPathTypeFromState")
                        && method.code().isPresent())
                .findFirst().orElseThrow();
        List<String> classifierFields = classifier.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.FieldInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.FieldInstruction.class::cast)
                .map(field -> field.owner().asInternalName() + "." + field.name().stringValue())
                .toList();
        List<String> classifierCalls = classifier.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .map(call -> call.owner().asInternalName() + "." + call.name().stringValue())
                .toList();
        assertTrue(classifierFields.contains("net/minecraft/world/level/block/Blocks.POWDER_SNOW"),
                "Minecraft's real classifier identifies vanilla's exact powder singleton");
        assertTrue(classifierFields.contains("net/minecraft/world/level/pathfinder/PathType.POWDER_SNOW"),
                "that exact singleton returns the rejected POWDER_SNOW path type");
        assertTrue(classifierFields.contains("net/minecraft/world/level/pathfinder/PathComputationType.LAND"),
                "the general fallback asks the LAND computation");
        assertTrue(classifierCalls.contains(
                        "net/minecraft/world/level/block/state/BlockState.isPathfindable"),
                "non-vanilla identities reach their block's actual pathfindable decision");

        var supportResolver = vanillaModel.methods().stream()
                .filter(method -> method.methodName().equalsString("getPathTypeStatic")
                        && method.code().isPresent())
                .filter(method -> method.code().orElseThrow().elementStream()
                        .filter(java.lang.classfile.instruction.FieldInstruction.class::isInstance)
                        .map(java.lang.classfile.instruction.FieldInstruction.class::cast)
                        .anyMatch(field -> field.owner().asInternalName().equals(
                                        "net/minecraft/world/level/pathfinder/PathType")
                                && field.name().equalsString("WALKABLE")))
                .findFirst().orElseThrow();
        List<String> supportFields = supportResolver.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.FieldInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.FieldInstruction.class::cast)
                .map(field -> field.owner().asInternalName() + "." + field.name().stringValue())
                .toList();
        long rawStateReads = supportResolver.code().orElseThrow().elementStream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .filter(call -> call.owner().asInternalName().equals(
                                "net/minecraft/world/level/pathfinder/PathfindingContext")
                        && call.name().equalsString("getPathTypeFromState"))
                .count();
        assertTrue(supportFields.contains("net/minecraft/world/level/pathfinder/PathType.OPEN"));
        assertTrue(supportFields.contains("net/minecraft/world/level/pathfinder/PathType.WALKABLE"));
        assertTrue(rawStateReads >= 2,
                "the real support resolver must inspect both the standing cell and the block below it");
    }

    @Test
    void subclassDoesNotReplacePowderContactOrCollisionPhysics() {
        var declaredNames = Arrays.stream(CaveTrapPowderSnowBlock.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertTrue(declaredNames.contains("isPathfindable"), "the only gameplay override is the narrow path seam");
        for (String inheritedPhysics : new String[]{
                "entityInside", "fallOn", "getCollisionShape", "getEntityInsideCollisionShape",
                "getVisualShape", "pickupBlock", "getPickupSound"
        }) {
            assertFalse(declaredNames.contains(inheritedPhysics),
                    inheritedPhysics + " must remain the inherited vanilla powder-snow implementation");
        }
    }

    private static JsonObject loadJson(String resourcePath) {
        InputStream stream = CaveTrapPowderSnowResourceTest.class.getResourceAsStream(resourcePath);
        assertNotNull(stream, "missing required resource: " + resourcePath);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean resourceExists(String resourcePath) throws IOException {
        try (InputStream stream = CaveTrapPowderSnowResourceTest.class.getResourceAsStream(resourcePath)) {
            return stream != null;
        }
    }

    private static String classfileText(Class<?> type) throws IOException {
        return new String(classfileBytes(type), StandardCharsets.ISO_8859_1);
    }

    private static byte[] classfileBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = CaveTrapPowderSnowResourceTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "compiled class must be readable: " + type.getName());
            return stream.readAllBytes();
        }
    }

    private static final class ExposedWalkNodeEvaluator extends WalkNodeEvaluator {
        private static PathType classify(BlockGetter level, BlockPos position) {
            return getPathTypeFromState(level, position);
        }
    }

    private record SingleStateGetter(BlockState state) implements BlockGetter {

        @Override
        public BlockEntity getBlockEntity(BlockPos position) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            return state;
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return state.getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }
}
