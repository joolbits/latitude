package com.example.globe.compat;

import com.example.globe.GlobeMod;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

/**
 * TerraBlender replaces the surface rules of every overworld-dimension noise settings — custom
 * globe: settings included, gated only by a dimension-type tag check — with a wrapper that, for
 * any minecraft-namespace biome, consults its own bundled copy of the vanilla rules first and
 * falls through to the settings' real rules only when that copy returns nothing. The copy is
 * years stale: it predates sulfur caves (its deepslate catch-all answered everything below y=0,
 * leaving sulfur caves bare — measured at 3 substrate blocks below y=0 versus 76,071 once
 * bypassed), and it carries the vanilla-era badlands rules (74-anchor banding, orange cap at
 * y=256) in place of Latitude's tuned ones (84 anchor, no cap) — measured via runtime rule-object
 * dumps: with TerraBlender loaded, the first-consulted branch held the stale copy and Latitude's
 * rules sat in an unreachable fallback.
 *
 * <p>The clean sweep: replace TerraBlender's default minecraft-namespace rules with a
 * pass-through, so every minecraft-namespace biome falls through to the noise settings' actual
 * surface rules — Latitude's for globe worlds, the current vanilla datapack's for vanilla worlds
 * in the same profile. This restores Latitude's entire surface identity at once (sulfur caves,
 * badlands, and any future tuning) instead of re-injecting rules biome by biome. Mod-registered
 * namespace rules (Biomes O' Plenty's own biomes) are keyed by biome namespace and are untouched;
 * stage additions other mods registered into TerraBlender's default are preserved by weaving them
 * into the pass-through so the sweep never silently drops a third mod's rules.
 *
 * <p>TerraBlender is resolved reflectively so Latitude neither requires it nor loads any of its
 * classes when it is absent. Every failure path is fail-open: worlds keep generating with
 * TerraBlender's stock behavior, only the sweep stays off.
 */
public final class LatitudeTerraBlenderBridge {
    private static final String DISABLE_PROP = "latitude.terrablenderBridge.disable";
    /** The canonical always-pass rule: an empty sequence answers null for every position. */
    private static final String EMPTY_SEQUENCE_JSON = "{\"type\":\"minecraft:sequence\",\"sequence\":[]}";

    private LatitudeTerraBlenderBridge() {
    }

    public static void install() {
        if (Boolean.getBoolean(DISABLE_PROP)) {
            GlobeMod.LOGGER.info("[Latitude] TerraBlender surface sweep disabled by {}", DISABLE_PROP);
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("terrablender")) {
            return;
        }
        try {
            MaterialRule passThrough = MaterialRule.DIRECT_CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(EMPTY_SEQUENCE_JSON))
                    .getOrThrow(error -> new IllegalStateException(
                            "empty sequence rule failed to parse: " + error));
            registerWithTerraBlender(passThrough);
            GlobeMod.LOGGER.info(
                    "[Latitude] TerraBlender surface sweep active: minecraft-namespace biomes fall "
                            + "through to the actual noise-settings surface rules");
        } catch (Throwable t) {
            GlobeMod.LOGGER.warn(
                    "[Latitude] TerraBlender surface sweep failed to install; TerraBlender's stale "
                            + "bundled surface rules stay in charge of minecraft-namespace biomes "
                            + "(bare sulfur caves, vanilla-era badlands) while it is present", t);
        }
    }

    /**
     * Equivalent of {@code SurfaceRuleManager.setDefaultSurfaceRules(OVERWORLD, biomes ->
     * passThroughWithStageAdditions)}, resolved reflectively. Replacing the default builder means
     * TerraBlender's own stage-addition weaving never runs, so the pass-through re-weaves any
     * additions other mods registered: BEFORE_BEDROCK ones first (their contract is to run ahead
     * of everything), AFTER_BEDROCK ones inside an above-preliminary-surface gate (matching where
     * TerraBlender's own builder placed them), then the always-null empty sequence so the biome
     * fall-through happens whenever no addition answered. RuleBuilder is a plain
     * {@code Function<HolderGetter<Biome>, RuleSource>} sub-interface, so a proxy answering
     * {@code apply} is a complete implementation.
     *
     * <p>If another mod also calls setDefaultSurfaceRules for the overworld, last registration
     * wins inside TerraBlender; the fail-open log line above is the breadcrumb for that fight.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerWithTerraBlender(MaterialRule passThrough) throws Exception {
        ClassLoader loader = LatitudeTerraBlenderBridge.class.getClassLoader();
        Class<?> manager = Class.forName("terrablender.api.SurfaceRuleManager", true, loader);
        Class<?> categoryClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleCategory", true, loader);
        Class<?> stageClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleStage", true, loader);
        Class<?> builderClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleBuilder", true, loader);

        Object overworld = Enum.valueOf((Class<Enum>) categoryClass, "OVERWORLD");
        Object beforeBedrock = Enum.valueOf((Class<Enum>) stageClass, "BEFORE_BEDROCK");
        Object afterBedrock = Enum.valueOf((Class<Enum>) stageClass, "AFTER_BEDROCK");
        Method additionsForStage = manager.getMethod(
                "getDefaultSurfaceRuleAdditionsForStage", categoryClass, stageClass, HolderGetter.class);

        Object builder = Proxy.newProxyInstance(loader, new Class<?>[] {builderClass}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "apply" -> {
                        HolderGetter<Biome> biomes = (HolderGetter<Biome>) args[0];
                        List<MaterialRule> woven = new ArrayList<>(
                                (List<MaterialRule>) additionsForStage
                                        .invoke(null, overworld, beforeBedrock, biomes));
                        List<MaterialRule> after =
                                (List<MaterialRule>) additionsForStage
                                        .invoke(null, overworld, afterBedrock, biomes);
                        if (!after.isEmpty()) {
                            woven.add(MaterialRules.ifTrue(
                                    MaterialRules.abovePreliminarySurface(),
                                    after.size() == 1 ? after.get(0)
                                            : MaterialRules.sequence(after)));
                        }
                        if (woven.isEmpty()) {
                            yield passThrough;
                        }
                        woven.add(passThrough);
                        yield MaterialRules.sequence(woven);
                    }
                    case "toString" -> "latitude surface sweep (pass through to noise settings)";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "latitude terrablender sweep: unexpected call " + method.getName());
                });

        manager.getMethod("setDefaultSurfaceRules", categoryClass, builderClass)
                .invoke(null, overworld, builder);
    }
}
