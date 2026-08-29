package com.example.globe.compat;

import com.example.globe.GlobeMod;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * TerraBlender re-owns surface painting for every minecraft-namespace biome of any overworld
 * noise settings it claims, and it can claim Latitude's: the globe world preset ships a vanilla
 * multi_noise biome source and Latitude substitutes its own only through the generator's biome
 * source getter, so whether TerraBlender's claim lands on globe: settings depends on when that
 * substitution has happened. Once a settings object is claimed, every minecraft-namespace biome
 * is answered from TerraBlender's own bundled copy of the vanilla rules first, and the settings'
 * real rules are reached only when that copy returns nothing.
 *
 * <p>The bundled copy is a stale snapshot, not a mirror of the current datapack. In the
 * TerraBlender build this line runs against it still bands badlands off the vanilla y=74 start
 * check and caps orange terracotta at y=256, where Latitude's shipped globe rules band off 84
 * with no cap — so wherever the claim lands, Latitude's tuned terracotta sits in an unreachable
 * fallback and the vanilla-era look paints instead. The same defect was measured live on the 26.2
 * line with runtime rule dumps (2026-08-17); this port carries the fix back rather than waiting
 * for it to be reported again here.
 *
 * <p>The clean sweep: replace TerraBlender's default minecraft-namespace rules with a
 * pass-through, so every minecraft-namespace biome falls through to the noise settings' actual
 * surface rules — Latitude's for globe worlds, the current vanilla datapack's for vanilla worlds
 * in the same profile. That restores Latitude's whole surface identity at once, and any future
 * tuning with it, instead of re-injecting rules biome by biome. Mod-registered namespace rules
 * (Biomes O' Plenty's own biomes) are keyed by biome namespace and are untouched; stage additions
 * other mods registered into TerraBlender's default are preserved by weaving them into the
 * pass-through so the sweep never silently drops a third mod's rules.
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
            // Built through the codec rather than SurfaceRules.sequence(): the factory refuses an
            // empty sequence, and an empty sequence is exactly the rule that never answers.
            SurfaceRules.RuleSource passThrough = SurfaceRules.RuleSource.CODEC
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
                            + "(vanilla-era badlands) while it is present", t);
        }
    }

    /**
     * {@code SurfaceRuleManager.setDefaultSurfaceRules(OVERWORLD, passThroughWithStageAdditions)},
     * resolved reflectively. On this Minecraft line TerraBlender's default is a finished rule
     * object rather than a lazy builder, so replacing it means TerraBlender's own stage-addition
     * weaving never runs: the pass-through re-weaves whatever additions other mods registered,
     * mirroring where TerraBlender's own assembly puts them — BEFORE_BEDROCK ones ahead of
     * everything, AFTER_BEDROCK ones inside an above-preliminary-surface gate — and ends with the
     * always-null empty sequence so the biome fall-through happens whenever no addition answered.
     *
     * <p>Because the replacement is a finished rule, those additions can only be read now, at
     * install time: a mod that registers stage additions after Latitude's initializer runs would
     * lose them. Nothing in the tested modset registers any — Biomes O' Plenty registers
     * namespace rules, which the sweep never touches — and the only way to defer the read would
     * be to plant a Latitude-owned rule source inside TerraBlender's rule tree, a far larger
     * surface than this defect warrants.
     *
     * <p>If another mod also calls setDefaultSurfaceRules for the overworld, last registration
     * wins inside TerraBlender; the install log line above is the breadcrumb for that fight.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerWithTerraBlender(SurfaceRules.RuleSource passThrough) throws Exception {
        ClassLoader loader = LatitudeTerraBlenderBridge.class.getClassLoader();
        Class<?> manager = Class.forName("terrablender.api.SurfaceRuleManager", true, loader);
        Class<?> categoryClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleCategory", true, loader);
        Class<?> stageClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleStage", true, loader);

        Object overworld = Enum.valueOf((Class<Enum>) categoryClass, "OVERWORLD");
        Object beforeBedrock = Enum.valueOf((Class<Enum>) stageClass, "BEFORE_BEDROCK");
        Object afterBedrock = Enum.valueOf((Class<Enum>) stageClass, "AFTER_BEDROCK");
        Method additionsForStage = manager.getMethod(
                "getDefaultSurfaceRuleAdditionsForStage", categoryClass, stageClass);

        List<SurfaceRules.RuleSource> woven = new ArrayList<>(
                (List<SurfaceRules.RuleSource>) additionsForStage.invoke(null, overworld, beforeBedrock));
        List<SurfaceRules.RuleSource> after =
                (List<SurfaceRules.RuleSource>) additionsForStage.invoke(null, overworld, afterBedrock);
        if (!after.isEmpty()) {
            woven.add(SurfaceRules.ifTrue(
                    SurfaceRules.abovePreliminarySurface(),
                    after.size() == 1 ? after.get(0)
                            : SurfaceRules.sequence(after.toArray(SurfaceRules.RuleSource[]::new))));
        }
        SurfaceRules.RuleSource swept = passThrough;
        if (!woven.isEmpty()) {
            woven.add(passThrough);
            swept = SurfaceRules.sequence(woven.toArray(SurfaceRules.RuleSource[]::new));
        }

        manager.getMethod("setDefaultSurfaceRules", categoryClass, SurfaceRules.RuleSource.class)
                .invoke(null, overworld, swept);
    }
}
