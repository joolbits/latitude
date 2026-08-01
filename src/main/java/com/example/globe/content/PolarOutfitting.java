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

    /** The P1 spike armour piece (HEAD slot). Populated by {@link #register()}. */
    public static Item POLAR_HOOD;

    // ---- B-10 P2 (owner co-design session 2026-07-26): the rest of the outfitting family. ------------
    // Registered UNCONDITIONALLY like the hood, and just as INERT: no recipes, no tag membership, no
    // behaviour wiring -- those ship with the mechanics round (the weighted ColdProtection score, the
    // warning matrix, the leather demotion, amendments A1-A8), flag-gated. Owner decisions banked this
    // session: the suit lives in the NORMAL ARMOR SLOTS (over-armor via the hidden BODY slot was
    // investigated against the 26.2 jar and rejected -- no inventory widget, no vanilla rendering,
    // off-label; recorded as a possible future "expedition overcoat" follow-up), and defence is
    // LEATHER-TIER (warmth, not combat -- POLAR_MATERIAL above, unchanged).

    /** Suit chest piece (CHEST slot). Populated by {@link #register()}. */
    public static Item POLAR_PARKA;

    /** Suit leg piece (LEGS slot). Populated by {@link #register()}. */
    public static Item POLAR_LEGGINGS;

    /** Suit foot piece (FEET slot). Populated by {@link #register()}. */
    public static Item POLAR_BOOTS;

    /** Snow goggles (HEAD slot, sight-not-warmth): a plain {@code equippable} wearable, NOT armour -- no
     *  defence points, no durability; its v1 power (warning-vignette removal) wires in the mechanics
     *  round. Wears its own thin eye-band equipment asset so the hood-vs-goggles comparison reads on
     *  camera. Populated by {@link #register()}. */
    public static Item SNOW_GOGGLES;

    /**
     * GOGGLED HOOD (Peetsa 2026-07-27: "let's do the make goggles cosmetic thing"). The head slot can
     * only hold one item, so wearing the suit hood meant giving up the goggles' LOOK -- the hood already
     * carried the visor's POWER. This variant resolves it cosmetically: craft hood + goggles together and
     * wear a hood with the goggle band visibly strapped across it. Mechanically it IS the hood -- same
     * leather-tier armour, same {@code #globe:polar_suit} membership (so it still completes the set), same
     * dyeable cloth, and it grants the same goggle sight. Populated by {@link #register()}.
     */
    public static Item POLAR_HOOD_GOGGLED;

    /** Insulated Hide -- the crafted intermediate (leather + any-colour wool + string) the whole suit is
     *  sewn from. Plain item; recipes ship with the mechanics round. Populated by {@link #register()}. */
    public static Item INSULATED_HIDE;

    /** Equipment-asset key for the goggles' own worn layer (a thin band across the eyes). */
    public static final ResourceKey<EquipmentAsset> GOGGLES_EQUIPMENT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, id("goggles"));

    /** Equipment-asset key for the GOGGLED HOOD -- three stacked layers (dyeable cloth, the suit's
     *  untinted fur/trim identity, then the goggle band) so the wearer shows hood AND goggles at once. */
    public static final ResourceKey<EquipmentAsset> POLAR_GOGGLED_EQUIPMENT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, id("polar_goggled"));

    /** The single spike status effect ({@code globe:cold_protection}, BENEFICIAL). Populated by
     *  {@link #register()}. */
    public static Holder<MobEffect> COLD_PROTECTION_EFFECT;

    /**
     * Register the outfitting family's game objects. Called UNCONDITIONALLY from
     * {@code GlobeMod.onInitialize} during the mod-init window, before registry freeze.
     */
    public static void register() {
        POLAR_HOOD = registerArmor("polar_hood", ArmorType.HELMET);
        POLAR_HOOD_GOGGLED = registerArmor("polar_hood_goggled", ArmorType.HELMET,
                POLAR_GOGGLED_EQUIPMENT_ASSET);
        POLAR_PARKA = registerArmor("polar_parka", ArmorType.CHESTPLATE);
        POLAR_LEGGINGS = registerArmor("polar_leggings", ArmorType.LEGGINGS);
        POLAR_BOOTS = registerArmor("polar_boots", ArmorType.BOOTS);
        SNOW_GOGGLES = registerGoggles();
        INSULATED_HIDE = registerPlainItem("insulated_hide");
        COLD_PROTECTION_EFFECT = registerColdProtectionEffect();
        GlobeMod.LOGGER.info("[B-10] registered the polar outfitting family "
                + "(hood/parka/leggings/boots + snow_goggles + insulated_hide + cold_protection; "
                + "all inert pending the mechanics round)");
    }

    private static Item registerGoggles() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id("snow_goggles"));
        // Not armour: a bare equippable HEAD wearable (the carved-pumpkin pattern) -- no defence
        // attributes, no durability. Builder shapes jar-confirmed in the B-10 recon (Equippable.builder /
        // setEquipSound / setAsset; elytra uses the identical idiom).
        Item item = new Item(new Item.Properties()
                .component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                        net.minecraft.world.item.equipment.Equippable.builder(
                                        net.minecraft.world.entity.EquipmentSlot.HEAD)
                                .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                                .setAsset(GOGGLES_EQUIPMENT_ASSET)
                                .build())
                .setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Item registerPlainItem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static Item registerArmor(String name, ArmorType type) {
        return registerArmor(name, type, null);
    }

    /** @param assetOverride when non-null, the piece wears this equipment asset instead of the suit's
     *      default (the goggled hood's three-layer asset). The MATERIAL -- and therefore the armour
     *      values, durability and repair -- is identical either way. */
    private static Item registerArmor(String name, ArmorType type,
            ResourceKey<EquipmentAsset> assetOverride) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        // 26.2: the item MUST carry its registry key in Properties (setId) BEFORE construction; humanoidArmor
        // wires the material's durability + equippable component + attributes + the equipment-asset key.
        Item.Properties props = new Item.Properties().humanoidArmor(POLAR_MATERIAL, type).setId(key);
        if (assetOverride != null) {
            // Re-point ONLY the worn-layer asset; everything else the material gave us stands.
            net.minecraft.world.item.equipment.Equippable base =
                    net.minecraft.world.item.equipment.Equippable
                            .builder(type.getSlot())
                            .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                            .setAsset(assetOverride)
                            .build();
            props = props.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, base);
        }
        Item item = new Item(props);
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
