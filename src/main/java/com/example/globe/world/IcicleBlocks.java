package com.example.globe.world;

import com.example.globe.GlobeMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * S37 (Dressing Crew): {@code globe:icicle} -- a reshaded POINTED DRIPSTONE clone for copious cave icicles
 * that still drip water (owner verbatim: "make sure to generate copious numbers of dripstone shaded to be
 * icicles. They can still drip water."). Second-ever Java content registration in the mod (after {@link
 * com.example.globe.content.PolarOutfitting}); mirrors that class's UNCONDITIONAL-registration /
 * flag-free-behaviour posture -- this is pure decoration, no gameplay flag applies.
 *
 * <p><b>javap findings (26.2 merged jar, {@code net.minecraft.world.level.block.PointedDripstoneBlock}):</b>
 * the class is CONCRETE (not abstract) and its constructor is PUBLIC: {@code PointedDripstoneBlock(BlockState
 * blockToGrowOn, BlockBehaviour.Properties)}. 26.2 generalized dripstone / the new sulfur-spike block into a
 * shared abstract {@code SpeleothemBlock} parent that takes the "block it grows on" as a CONSTRUCTOR
 * PARAMETER (a {@code protected final BlockState blockToGrowOn} field) rather than a hardcoded {@code
 * Blocks.DRIPSTONE_BLOCK} reference -- so nothing in the drip / fall / growth / shape code paths compares
 * block IDENTITY against a vanilla constant; {@code calculateTipDirection}, {@code
 * calculateSpeleothemThickness}, {@code isValidSpeleothemPlacement}, {@code isStalactite}/{@code
 * isStalagmite}, the drip-particle and cauldron-fill logic all operate on the BLOCKSTATE at each position
 * (its own {@code thickness}/{@code vertical_direction} properties), never on a captured vanilla {@code
 * Block} reference. A plain {@code new PointedDripstoneBlock(...)} registered under our own id is therefore
 * 100% vanilla-identical behaviour (falls when unsupported, drips water, merges tips, grows) with NO
 * subclass required.
 *
 * <p><b>DEVIATION</b> from the task brief's suggested "a class extending PointedDripstoneBlock": no
 * subclass exists because none is needed -- direct instantiation is more honest here (this really is
 * unmodified vanilla pointed-dripstone behaviour, just reshaded textures registered under a new id). The
 * vanilla {@code speleothem}/{@code speleothem_cluster} WORLDGEN FEATURE TYPES are ALSO fully data-driven in
 * 26.2 (their config carries a {@code pointed_block}/{@code base_block} {@code BlockState} field, verified
 * via {@code javap} on {@code SpeleothemClusterConfiguration}) -- i.e. the task brief's fallback condition
 * ("if the vanilla feature types hardcode dripstone blocks...") does not trigger. Generation nonetheless
 * follows the brief's explicit instruction to reuse THIS repo's own proven mechanism (see {@code
 * icicle_cluster.json}, a {@code block_column} pair mirroring {@code globe:hanging_icicles}) rather than
 * introduce an unproven vanilla feature type into this codebase.
 *
 * <p>{@code blockToGrowOn} is set to {@link Blocks#PACKED_ICE} (not dripstone_block) so that if a player
 * ever bonemeals/grows an icicle in-world it grows from packed ice, matching the reskin's fiction; since
 * worldgen here places icicles directly (not via the growth path), this only matters for the rare
 * in-world-grow case.
 *
 * <p><b>Wiring TODO for the orchestrator</b> (this crew does not edit {@code GlobeMod.java}): call {@code
 * com.example.globe.world.IcicleBlocks.register();} from {@code GlobeMod.onInitialize()}, alongside the
 * other UNCONDITIONAL content registrations ({@code PolarOutfitting.register()}, {@code
 * PowderCrevasseRoofFeature.register()}, {@code GlobeParticles.register()}) -- BEFORE registry freeze.
 */
public final class IcicleBlocks {

    private IcicleBlocks() {
    }

    /** The registered singleton (populated by {@link #register()}), mirroring {@code PolarOutfitting} /
     *  {@code PowderCrevasseRoofFeature}'s static-field pattern. */
    public static Block ICICLE;
    public static Item ICICLE_ITEM;

    /**
     * Register {@code globe:icicle} (block + {@link BlockItem}) UNCONDITIONALLY. Called from {@code
     * GlobeMod.onInitialize} during the mod-init window, before registry freeze -- registries must be
     * consistent across sessions (a world saved with the block, reopened without this call, must not
     * reference a missing block).
     */
    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("icicle"));
        // ofFullCopy mirrors vanilla pointed_dripstone's full properties (strength, sound, dynamicShape,
        // offsetType, pushReaction, isRedstoneConductor, noOcclusion, randomTicks, ...) verbatim -- the
        // reshade changes only the textures, never the physical behaviour.
        BlockBehaviour.Properties properties =
                BlockBehaviour.Properties.ofFullCopy(Blocks.POINTED_DRIPSTONE).setId(blockKey);
        ICICLE = new PointedDripstoneBlock(Blocks.PACKED_ICE.defaultBlockState(), properties);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, ICICLE);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("icicle"));
        ICICLE_ITEM = new BlockItem(ICICLE, new Item.Properties().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, ICICLE_ITEM);

        GlobeMod.LOGGER.info("[S37] registered globe:icicle (reshaded pointed-dripstone clone; drips water)");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, path);
    }
}
