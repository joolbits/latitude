package com.example.globe.world;

import com.example.globe.GlobeMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registers the paired, static worldgen remains placed beside a collapsed explorer's treasure chest. */
public final class CollapsedExplorerBlocks {

    private CollapsedExplorerBlocks() {
    }

    /** One block ID supplies both HEAD and FOOT states. No item is registered. */
    public static Block COLLAPSED_EXPLORER;

    /** Empty, non-ticking render anchor shared by both paired states. */
    public static BlockEntityType<CollapsedExplorerBlockEntity> COLLAPSED_EXPLORER_BLOCK_ENTITY;

    /** Registers {@code globe:collapsed_explorer} during unconditional mod initialization. */
    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("collapsed_explorer"));
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .noOcclusion()
                .noLootTable()
                .strength(0.2F)
                .setId(blockKey);
        COLLAPSED_EXPLORER = new CollapsedExplorerBlock(properties);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, COLLAPSED_EXPLORER);

        ResourceKey<BlockEntityType<?>> blockEntityKey =
                ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id("collapsed_explorer"));
        COLLAPSED_EXPLORER_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                blockEntityKey,
                new BlockEntityType<>(CollapsedExplorerBlockEntity::new, java.util.Set.of(COLLAPSED_EXPLORER)));

        GlobeMod.LOGGER.info("registered globe:collapsed_explorer (static worldgen dressing + render anchor)");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GlobeMod.MOD_ID, path);
    }
}
