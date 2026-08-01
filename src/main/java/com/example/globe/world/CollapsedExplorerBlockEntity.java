package com.example.globe.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Persistence-safe render anchor for the paired collapsed explorer.
 *
 * <p>Facing and half are already persisted in the block state. This object intentionally has no ticker,
 * inventory, custom save data, update packet, or gameplay behavior.
 */
public final class CollapsedExplorerBlockEntity extends BlockEntity {

    public CollapsedExplorerBlockEntity(BlockPos pos, BlockState state) {
        super(CollapsedExplorerBlocks.COLLAPSED_EXPLORER_BLOCK_ENTITY, pos, state);
    }
}
