package com.example.globe.content;

import com.example.globe.GlobeMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

/**
 * Phase 5 Slice B-10 (Polar Outfitting) -- the DE-RISK SPIKE (design §9). The mod's FIRST-EVER Java content
 * registration: 26.2's item / armour / equipment / mob-effect API is the part that has churned hardest across
 * recent Minecraft versions, so before building the full six-item asset bill this registers exactly ONE armour
 * piece (the polar HOOD) and ONE mob effect ({@code globe:cold_protection}) end-to-end against the live jar's
 * confirmed signatures. Every signature here was read off the mapped 26.2 jar with {@code javap} before writing
 * (design ADVERSARIAL SWEEP: ArmorMaterial = 8-arg record, {@code Item.Properties.setId}, {@code humanoidArmor},
 * {@code MobEffect(MobEffectCategory,int)}, {@code MobEffectCategory.BENEFICIAL}).
 *
 * <p><b>Registration is UNCONDITIONAL</b> ({@link #register()} is called from {@code GlobeMod.onInitialize}
 * regardless of {@link com.example.globe.core.LatitudeV2Flags#POLAR_OUTFITTING_ENABLED}) -- registries must be
 * consistent across sessions (a world saved with the item, reopened flag-off, must not reference a missing
 * item). ALL behaviour (cold weight, warning matrix, status effect, recipes, creative-tab visibility) is
 * flag-gated elsewhere; this class only creates and registers the game objects.
 *
 * <p><b>Honesty (design §9).</b> Registry-freeze SURVIVAL is proven only at first live client/server load; it
 * cannot be exercised by a unit test or by {@code compileJava}. The code is therefore written defensively and
 * matches the jar-confirmed shapes exactly. If a shape were wrong it would throw at registry freeze (a hard
 * load crash), not at compile.
 *
 * <p><b>P2 inherits</b> (this spike deliberately does NOT do): the remaining three suit pieces (parka /
 * leggings / boots) + snow goggles + insulated_hide intermediate; real UV-mapped worn-layer + inventory
 * textures + a real effect icon (this spike ships programmer-art placeholders -- see the asset TODOs below);
 * the {@code globe:polar_suit} item tag + adding the suit to {@code minecraft:freeze_immune_wearables}; the
 * conditional recipes + creative tab; and wiring the shims/matrix to the unified {@code ColdProtection} score.
 *
 * <p><b>Placeholder assets to REPLACE in P2</b> (all under {@code src/main/resources/assets/globe/}):
 * {@code textures/item/polar_hood.png} (flat 16x16 icon), {@code textures/entity/equipment/humanoid/polar.png}
 * (a 64x32 translucent wash -- NOT a real armour UV layout), {@code textures/mob_effect/cold_protection.png}
 * (18x18 placeholder snowflake).
 */
public final class PolarOutfitting {

    private PolarOutfitting() {
    }

    /** Equipment-asset key -> {@code assets/globe/equipment/polar.json} -> the humanoid worn-layer texture. */
    public static final ResourceKey<EquipmentAsset> POLAR_EQUIPMENT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, id("polar"));

    /** Repair-ingredient tag (P2 populates it with {@code globe:insulated_hide}); an empty tag is valid for the
     *  spike -- the hood just has no repair material yet. */
    public static final TagKey<Item> POLAR_REPAIR_TAG = TagKey.create(Registries.ITEM, id("polar_suit_repair"));

    /**
     * The polar-suit armour material -- LEATHER-TIER defence (design §3.1: warmth, not combat). 8-arg 26.2
     * {@code ArmorMaterial} record: durability multiplier, per-{@link ArmorType} defence map, enchantment value,
     * equip sound, toughness, knockback resistance, repair-ingredient tag, equipment-asset key.
     */
    public static final ArmorMaterial POLAR_MATERIAL = new ArmorMaterial(
            5,                                        // durability base multiplier (leather = 5)
            Map.of(
                    ArmorType.HELMET, 1,
                    ArmorType.CHESTPLATE, 3,
                    ArmorType.LEGGINGS, 2,
                    ArmorType.BOOTS, 1,
                    ArmorType.BODY, 3
            ),
            15,                                       // enchantment value (leather)
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0f,                                     // toughness
            0.0f,                                     // knockback resistance
            POLAR_REPAIR_TAG,
            POLAR_EQUIPMENT_ASSET
    );

    /** The single spike armour piece (HEAD slot). Populated by {@link #register()}. */
    public static Item POLAR_HOOD;

    /** The single spike status effect ({@code globe:cold_protection}, BENEFICIAL). Populated by
     *  {@link #register()}. */
    public static Holder<MobEffect> COLD_PROTECTION_EFFECT;

    /**
     * Register the spike's game objects. Called UNCONDITIONALLY from {@code GlobeMod.onInitialize} during the
     * mod-init window, before registry freeze.
     */
    public static void register() {
        POLAR_HOOD = registerArmor("polar_hood", ArmorType.HELMET);
        COLD_PROTECTION_EFFECT = registerColdProtectionEffect();
        GlobeMod.LOGGER.info("[B-10 spike] registered globe:polar_hood + globe:cold_protection "
                + "(first Java content registration; registry-freeze survival verified only at live load)");
    }

    private static Item registerArmor(String name, ArmorType type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        // 26.2: the item MUST carry its registry key in Properties (setId) BEFORE construction; humanoidArmor
        // wires the material's durability + equippable component + attributes + the equipment-asset key.
        Item item = new Item(new Item.Properties().humanoidArmor(POLAR_MATERIAL, type).setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Holder<MobEffect> registerColdProtectionEffect() {
        ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, id("cold_protection"));
        // MobEffect's constructor is protected; an anonymous subclass is the standard idiom for a plain effect
        // with no custom tick behaviour (it is a pure INDICATOR -- the damage negation is the ColdProtection
        // score, never this effect, so it can never be milk'd/dispelled off and desync from the armour truth).
        // The int is the effect's particle/tint colour (ice blue).
        MobEffect effect = new MobEffect(MobEffectCategory.BENEFICIAL, 0x9FD8FF) {
        };
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, key, effect);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, path);
    }
}
