package com.example.globe.world;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.core.PowderRoofTrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Runtime identity check for one authored S36 landing cushion. Powder snow is deliberately replaceable by
 * live water and invisible to MOTION_BLOCKING precipitation, so the exact cushion needs one narrow exception:
 * it is powder on dry support, below a bounded 18..128-block passable path whose roof is powder in Polar
 * Barrens. Ordinary powder and ordinary caves remain vanilla; water planning also treats only the single
 * passable cell immediately above the verified cushion as a collar, not the complete shaft.
 */
public final class GlacialTrapRuntimeGuard {

    private static final Identifier POLAR_BARRENS_ID =
            Identifier.fromNamespaceAndPath("globe", "polar_barrens");
    private GlacialTrapRuntimeGuard() {
    }

    /**
     * Water-planning predicate: the cushion itself plus the single passable cell immediately above it. The
     * one-cell collar prevents a downward flow from selecting the landing and then bypassing the powder-state
     * planner check, without turning the complete shaft into an invisible no-water volume.
     */
    public static boolean protectsAuthoredCushionCollar(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.POWDER_SNOW)) {
            return protectsAuthoredCushion(level, pos);
        }
        BlockPos cushionBelow = pos.below();
        return level.getBlockState(cushionBelow).is(Blocks.POWDER_SNOW)
                && protectsAuthoredCushion(level, cushionBelow);
    }

    /** True only when {@code pos} is the landing cushion of a structurally intact authored trap column. */
    public static boolean protectsAuthoredCushion(ServerLevel level, BlockPos pos) {
        BlockState cushion = level.getBlockState(pos);
        if (!cushion.is(Blocks.POWDER_SNOW)
                || !LatitudeV2Flags.POLAR_BARRENS_ENABLED
                || !LatitudeV2Flags.GLACIAL_CAVES_V1_ENABLED) {
            return false;
        }
        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            return false;
        }
        double absLatDeg = Math.abs((double) pos.getZ()) * 90.0 / radius;
        if (absLatDeg < PolarWaterFreezeRule.TICK_FRONT_ONSET_DEG) {
            return false;
        }

        BlockState support = level.getBlockState(pos.below());
        if (support.isAir() || !support.getFluidState().isEmpty()) {
            return false;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        int maxY = Math.min(level.getMaxY() - 1,
                pos.getY() + PowderRoofTrap.MAX_SHAFT_DEPTH_BLOCKS);
        for (int y = pos.getY() + 1; y <= maxY; y++) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.POWDER_SNOW)) {
                return PowderRoofTrap.columnDepthEligible(pos.getY(), y)
                        && level.getBiome(cursor.above()).unwrapKey()
                                .map(key -> key.identifier().equals(POLAR_BARRENS_ID))
                                .orElse(false);
            }
            if (!isFallPassable(state)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isFallPassable(BlockState state) {
        return state.isAir() || state.is(Blocks.SNOW) || state.getFluidState().is(Fluids.WATER);
    }
}
