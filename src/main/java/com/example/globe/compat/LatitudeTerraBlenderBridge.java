package com.example.globe.compat;

import com.example.globe.GlobeMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * TerraBlender replaces the surface rules of every overworld-dimension noise settings — custom
 * globe: settings included — with a wrapper that consults its own bundled copy of the vanilla
 * rules first for any minecraft-namespace biome, falling through to the settings' real rules only
 * when that copy stays silent. The copy predates sulfur caves and ends in a deepslate catch-all
 * that answers for every position below roughly y=0, so Latitude's sulfur substrate painting is
 * never consulted exactly where sulfur caves live (measured: 3 substrate blocks below y=0 across
 * ten chunks, versus 55,867 above y=8).
 *
 * <p>This bridge hands TerraBlender the same sulfur clause Latitude ships in its noise settings,
 * at the one injection stage that runs ahead of the deepslate catch-all. The clause's thresholds,
 * blocks, and noise are read out of the shipped JSON so the bridged rule cannot drift from the
 * datapack truth; the rule itself is assembled through the vanilla SurfaceRules factories inside
 * TerraBlender's builder callback, because the biome condition needs the biome HolderGetter that
 * only exists there. TerraBlender itself is resolved reflectively so Latitude neither requires it
 * nor loads any of its classes when it is absent. Every failure path is fail-open: worlds keep
 * generating, only the bridge stays off.
 */
public final class LatitudeTerraBlenderBridge {
    private static final String DISABLE_PROP = "latitude.terrablenderBridge.disable";
    private static final String SETTINGS_RESOURCE = "/data/globe/worldgen/noise_settings/overworld.json";
    private static final String SULFUR_CAVES_ID = "minecraft:sulfur_caves";
    private static final String BEDROCK_GRADIENT_NAME = "minecraft:bedrock_floor";
    private static final ResourceKey<Biome> SULFUR_CAVES_KEY =
            ResourceKey.create(Registries.BIOME, Identifier.parse(SULFUR_CAVES_ID));

    private LatitudeTerraBlenderBridge() {
    }

    public static void install() {
        if (Boolean.getBoolean(DISABLE_PROP)) {
            GlobeMod.LOGGER.info("[Latitude] TerraBlender surface bridge disabled by {}", DISABLE_PROP);
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("terrablender")) {
            return;
        }
        try {
            SulfurClause clause = readSulfurClause();
            if (clause == null) {
                return;
            }
            registerWithTerraBlender(clause);
            GlobeMod.LOGGER.info(
                    "[Latitude] TerraBlender surface bridge active: restored {} substrate painting "
                            + "({} noise bands)",
                    SULFUR_CAVES_ID, clause.bands.size());
        } catch (Throwable t) {
            GlobeMod.LOGGER.warn(
                    "[Latitude] TerraBlender surface bridge failed to install; sulfur caves will "
                            + "stay bare below y=0 while TerraBlender is present", t);
        }
    }

    /** One painted band: when the gradient noise is inside [min, max], place the block. */
    private record NoiseBand(ResourceKey<NormalNoise.NoiseParameters> noise, double min, double max, Block block) {
    }

    /** The shipped sulfur clause, plus the bedrock gradient anchors used to guard it. */
    private record SulfurClause(List<NoiseBand> bands, VerticalAnchor bedrockFrom, VerticalAnchor bedrockTo) {
    }

    /**
     * Reads the sulfur clause out of the same noise-settings file the generator uses, so the
     * bridged rule can never drift from what Latitude actually ships. Returns null (fail-open,
     * with a log line) whenever the file's shape is not the one this bridge understands.
     */
    private static SulfurClause readSulfurClause() throws Exception {
        JsonObject root;
        try (InputStream stream = LatitudeTerraBlenderBridge.class.getResourceAsStream(SETTINGS_RESOURCE)) {
            if (stream == null) {
                GlobeMod.LOGGER.warn("[Latitude] TerraBlender bridge: {} missing from the mod jar",
                        SETTINGS_RESOURCE);
                return null;
            }
            root = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }

        JsonArray sequence = root.getAsJsonObject("surface_rule").getAsJsonArray("sequence");
        JsonObject sulfurClause = null;
        JsonObject bedrockCondition = null;
        for (JsonElement entry : sequence) {
            JsonObject clause = entry.getAsJsonObject();
            if (!clause.has("if_true") || !clause.get("if_true").isJsonObject()) {
                continue;
            }
            JsonObject condition = clause.getAsJsonObject("if_true");
            String type = condition.has("type") ? condition.get("type").getAsString() : "";
            if (type.equals("minecraft:vertical_gradient")
                    && condition.has("random_name")
                    && BEDROCK_GRADIENT_NAME.equals(condition.get("random_name").getAsString())) {
                bedrockCondition = condition;
            } else if (type.equals("minecraft:biome") && mentionsSulfur(condition.get("biome_is"))) {
                sulfurClause = clause;
            }
        }
        if (sulfurClause == null || bedrockCondition == null) {
            GlobeMod.LOGGER.warn(
                    "[Latitude] TerraBlender bridge: expected clauses not found in {} "
                            + "(sulfur={}, bedrock={})",
                    SETTINGS_RESOURCE, sulfurClause != null, bedrockCondition != null);
            return null;
        }

        List<NoiseBand> bands = new ArrayList<>();
        for (JsonElement bandElement : sulfurClause.getAsJsonObject("then_run")
                .getAsJsonArray("sequence")) {
            JsonObject band = bandElement.getAsJsonObject();
            JsonObject bandCondition = band.getAsJsonObject("if_true");
            JsonObject bandResult = band.getAsJsonObject("then_run");
            if (bandCondition == null || bandResult == null
                    || !"minecraft:noise_threshold".equals(bandCondition.get("type").getAsString())
                    || !bandCondition.has("is_3d") || !bandCondition.get("is_3d").getAsBoolean()
                    || !"minecraft:block".equals(bandResult.get("type").getAsString())) {
                GlobeMod.LOGGER.warn(
                        "[Latitude] TerraBlender bridge: sulfur clause band has an unexpected "
                                + "shape; leaving the bridge off rather than guessing: {}", band);
                return null;
            }
            Identifier blockId = Identifier.parse(
                    bandResult.getAsJsonObject("result_state").get("Name").getAsString());
            bands.add(new NoiseBand(
                    ResourceKey.create(Registries.NOISE,
                            Identifier.parse(bandCondition.get("noise").getAsString())),
                    bandCondition.get("min_threshold").getAsDouble(),
                    bandCondition.get("max_threshold").getAsDouble(),
                    BuiltInRegistries.BLOCK.getValue(blockId)));
        }
        if (bands.isEmpty()) {
            GlobeMod.LOGGER.warn("[Latitude] TerraBlender bridge: sulfur clause has no noise bands");
            return null;
        }
        return new SulfurClause(
                bands,
                anchor(bedrockCondition.getAsJsonObject("true_at_and_below")),
                anchor(bedrockCondition.getAsJsonObject("false_at_and_above")));
    }

    private static VerticalAnchor anchor(JsonObject json) {
        if (json.has("above_bottom")) {
            return VerticalAnchor.aboveBottom(json.get("above_bottom").getAsInt());
        }
        if (json.has("below_top")) {
            return VerticalAnchor.belowTop(json.get("below_top").getAsInt());
        }
        return VerticalAnchor.absolute(json.get("absolute").getAsInt());
    }

    private static boolean mentionsSulfur(JsonElement biomeIs) {
        if (biomeIs == null) {
            return false;
        }
        if (biomeIs.isJsonArray()) {
            for (JsonElement id : biomeIs.getAsJsonArray()) {
                if (SULFUR_CAVES_ID.equals(id.getAsString())) {
                    return true;
                }
            }
            return false;
        }
        return SULFUR_CAVES_ID.equals(biomeIs.getAsString());
    }

    /**
     * Assembles the guarded rule. Runs inside TerraBlender's builder callback because
     * {@link SurfaceRules#isBiome} needs the biome HolderGetter that only exists there.
     */
    private static SurfaceRules.RuleSource buildRule(SulfurClause clause, HolderGetter<Biome> biomes) {
        List<SurfaceRules.RuleSource> painted = new ArrayList<>();
        for (NoiseBand band : clause.bands) {
            painted.add(SurfaceRules.ifTrue(
                    SurfaceRules.noiseCondition3d(band.noise, band.min, band.max),
                    SurfaceRules.state(band.block.defaultBlockState())));
        }
        SurfaceRules.RuleSource bands = SurfaceRules.sequence(
                painted.toArray(SurfaceRules.RuleSource[]::new));
        return SurfaceRules.ifTrue(
                SurfaceRules.not(SurfaceRules.verticalGradient(
                        BEDROCK_GRADIENT_NAME, clause.bedrockFrom, clause.bedrockTo)),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes, SULFUR_CAVES_KEY), bands));
    }

    /**
     * Equivalent of {@code SurfaceRuleManager.addToDefaultSurfaceRulesAtStage(OVERWORLD,
     * BEFORE_BEDROCK, 0, biomes -> rule)}, resolved reflectively. RuleBuilder is a plain
     * {@code Function<HolderGetter<Biome>, RuleSource>} sub-interface, so a proxy answering
     * {@code apply} is a complete implementation.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerWithTerraBlender(SulfurClause clause) throws Exception {
        ClassLoader loader = LatitudeTerraBlenderBridge.class.getClassLoader();
        Class<?> manager = Class.forName("terrablender.api.SurfaceRuleManager", true, loader);
        Class<?> categoryClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleCategory", true, loader);
        Class<?> stageClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleStage", true, loader);
        Class<?> builderClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleBuilder", true, loader);

        Object overworld = Enum.valueOf((Class<Enum>) categoryClass, "OVERWORLD");
        Object beforeBedrock = Enum.valueOf((Class<Enum>) stageClass, "BEFORE_BEDROCK");
        Object builder = Proxy.newProxyInstance(loader, new Class<?>[] {builderClass}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "apply" -> buildRule(clause, (HolderGetter<Biome>) args[0]);
                    case "toString" -> "latitude sulfur cave surface bridge";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "latitude terrablender bridge: unexpected call " + method.getName());
                });

        Method register = manager.getMethod(
                "addToDefaultSurfaceRulesAtStage", categoryClass, stageClass, int.class, builderClass);
        register.invoke(null, overworld, beforeBedrock, 0, builder);
    }
}
