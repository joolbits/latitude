package com.example.globe.world;

import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Worldgen-only false-floor snow for inner-cave traps.
 *
 * <p>All contact, sinking, freezing, collision, fall-cushion, particle, and bucket behavior is inherited
 * unchanged from {@link PowderSnowBlock}. The distinct registered block identity is intentional: vanilla's
 * walk-node classifier assigns the exact {@code Blocks.POWDER_SNOW} singleton its avoided powder path type.
 * Every other block reaches {@link BlockState#isPathfindable(PathComputationType)}. A {@code true} LAND
 * result classifies the occupied cell as open air, so the standing cell above it is unsupported and a ground
 * route cannot cross. Returning {@code false} classifies only this custom occupied cell as blocked support;
 * the evaluator's normal rule then classifies the air above as walkable. Mobs plan across the false floor and
 * inherited powder-snow collision still makes them sink when they arrive. Normal vanilla powder snow and all
 * non-pathfinding powder behavior remain untouched.
 */
public final class CaveTrapPowderSnowBlock extends PowderSnowBlock {

    public CaveTrapPowderSnowBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
