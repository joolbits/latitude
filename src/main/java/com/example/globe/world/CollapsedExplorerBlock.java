package com.example.globe.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Static, worldgen-only remains for the lost-explorer cave ending. Two block states form one full-length
 * fallen warrior. {@link #FACING} points from feet to skull; the client renderer draws the complete body
 * once from the {@link BedPart#HEAD} anchor.
 *
 * <p>The paired selection, raycast, and collision footprint remains physical and low enough to step over.
 * No item places it, and its block entity stores no custom data and has no ticker; it only gives the client
 * a stable render anchor that survives chunk save/load.
 */
public final class CollapsedExplorerBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CollapsedExplorerBlock> CODEC = simpleCodec(CollapsedExplorerBlock::new);
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<BedPart> PART =
            BlockStateProperties.BED_PART;

    /*
     * NORTH-authored physical proxy, in model pixels. The client renders the real vanilla SkeletonModel,
     * while these two low unions preserve the candidate's paired selection, raycast, and collision feel.
     */
    private static final double[][] HEAD_BOXES = {
            // Eight-pixel skull silhouette, split jaw, and face-up sockets.
            {4, 0.5, 0, 12, 8.5, 6.5},
            {4.75, 0.5, 6.5, 7.5, 5.5, 8},
            {8.5, 0.5, 6.5, 11.25, 5.5, 8},
            {6.5, 0.5, 7.5, 9.5, 3.5, 8},
            {5, 8.5, 2.5, 7.25, 8.68, 4.75},
            {8.75, 8.5, 2.5, 11, 8.68, 4.75},
            {7.4, 8.5, 4.75, 8.6, 8.7, 6.15},
            {6.15, 5.5, 7, 9.85, 5.7, 7.85},
            {3.82, 3, 2.5, 4, 6, 4.75},
            {12, 3, 2.5, 12.18, 6, 4.75},
            // Neck, spine, clavicle, four ribs, and rib-side rails.
            {7.25, 0.75, 8, 8.75, 2.75, 16},
            {3.5, 1.25, 8.5, 12.5, 3.75, 9.5},
            {4, 1, 9.75, 12, 3.5, 10.45},
            {4.25, 1, 11.15, 11.75, 3.5, 11.85},
            {4.5, 1, 12.55, 11.5, 3.5, 13.25},
            {4.75, 1, 13.95, 11.25, 3.5, 14.65},
            {4.75, 1, 15.3, 11.25, 3.5, 16},
            {4, 0.75, 9.5, 5, 3.5, 16},
            {11, 0.75, 9.5, 12, 3.5, 16},
            // Two distinct arms: one straight, one collapsed inward at the elbow.
            {0, 0.5, 9, 4, 4.5, 16},
            {12, 0.5, 9, 16, 4.5, 14},
            {10.5, 0.5, 13.5, 16, 4.5, 16},
            // Subtle warrior cue: partial helmet and one worn shoulder plate.
            {3.75, 8.5, 0, 12.25, 9, 2.5},
            {3.75, 4, 0.5, 4.25, 8.5, 3.5},
            {11.5, 4.5, 8.5, 15.75, 5.25, 11.5}
    };

    private static final double[][] FOOT_BOXES = {
            // Lower spine, rib cage, and both continued arms.
            {7.25, 0.75, 0, 8.75, 2.75, 4.25},
            {4.75, 1, 0, 11.25, 3.5, 0.7},
            {5, 1, 1.45, 11, 3.5, 2.15},
            {5.25, 1, 2.9, 10.75, 3.5, 3.6},
            {4.5, 0.75, 0, 5.5, 3.5, 4},
            {10.5, 0.75, 0, 11.5, 3.5, 4},
            {0, 0.5, 0, 4, 4.5, 4},
            {9, 0.5, 0, 14, 4.5, 3.5},
            {8.5, 0.5, 2.75, 12.5, 4.5, 4.75},
            // Split pelvis with a visible central opening.
            {4, 0.5, 4, 7.5, 4.5, 7},
            {8.5, 0.5, 4, 12, 4.5, 7},
            {7.25, 0.5, 4.75, 8.75, 3.25, 7.25},
            {6.35, 4.5, 4.65, 9.65, 4.68, 6.65},
            // Two full legs; the right leg is bent and ends at a different angle.
            {4, 0.5, 6.5, 7.75, 4.5, 16},
            {3.25, 0.5, 14.75, 7.75, 4.5, 16},
            {8.25, 0.5, 6.5, 12, 4.5, 11.25},
            {10.5, 0.5, 10.5, 14.5, 4.5, 14.25},
            {12, 0.5, 13.75, 16, 4.5, 16},
            // A broken belt/hip plate, deliberately much smaller than the bones.
            {4, 4.5, 4, 7, 5.1, 5.25}
    };

    private static final VoxelShape HEAD_NORTH = shapeFrom(HEAD_BOXES, Direction.NORTH);
    private static final VoxelShape HEAD_EAST = shapeFrom(HEAD_BOXES, Direction.EAST);
    private static final VoxelShape HEAD_SOUTH = shapeFrom(HEAD_BOXES, Direction.SOUTH);
    private static final VoxelShape HEAD_WEST = shapeFrom(HEAD_BOXES, Direction.WEST);
    private static final VoxelShape FOOT_NORTH = shapeFrom(FOOT_BOXES, Direction.NORTH);
    private static final VoxelShape FOOT_EAST = shapeFrom(FOOT_BOXES, Direction.EAST);
    private static final VoxelShape FOOT_SOUTH = shapeFrom(FOOT_BOXES, Direction.SOUTH);
    private static final VoxelShape FOOT_WEST = shapeFrom(FOOT_BOXES, Direction.WEST);

    public CollapsedExplorerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, BedPart.FOOT));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CollapsedExplorerBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING), state.getValue(PART));
    }

    /**
     * Bed-like partner law: a later neighbour update removes an orphaned or mismatched half. Worldgen
     * writes both halves in one transaction with known-shape updates suppressed, so neither self-removes
     * while that atomic pair is being installed.
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess scheduledTickAccess,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            RandomSource random) {
        Direction partnerDirection = partnerDirection(state.getValue(FACING), state.getValue(PART));
        if (direction == partnerDirection) {
            return isMatchingPartner(state, neighborState)
                    ? state
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(
                state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
    }

    /** Package-visible proof seam returning the exact shape shared by selection, raycast, and collision. */
    static VoxelShape shapeFor(Direction facing, BedPart part) {
        if (part == BedPart.HEAD) {
            return switch (facing) {
                case EAST -> HEAD_EAST;
                case SOUTH -> HEAD_SOUTH;
                case WEST -> HEAD_WEST;
                default -> HEAD_NORTH;
            };
        }
        return switch (facing) {
            case EAST -> FOOT_EAST;
            case SOUTH -> FOOT_SOUTH;
            case WEST -> FOOT_WEST;
            default -> FOOT_NORTH;
        };
    }

    /** Direction from this state to its required partner. */
    static Direction partnerDirection(Direction facing, BedPart part) {
        return part == BedPart.FOOT ? facing : facing.getOpposite();
    }

    /** Pure state-level seam used by the focused orphan-law test. */
    static boolean isMatchingPartner(BlockState state, BlockState neighborState) {
        return neighborState.is(state.getBlock())
                && isMatchingPartner(
                        state.getValue(FACING),
                        state.getValue(PART),
                        neighborState.getValue(FACING),
                        neighborState.getValue(PART));
    }

    /** Registry-free form of the paired-state law, kept package-visible for focused proof. */
    static boolean isMatchingPartner(
            Direction facing, BedPart part,
            Direction neighborFacing, BedPart neighborPart) {
        BedPart expected = part == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT;
        return neighborFacing == facing && neighborPart == expected;
    }

    private static VoxelShape shapeFrom(double[][] boxes, Direction facing) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            shape = Shapes.or(shape, rotatedBox(box, facing));
        }
        return shape.optimize();
    }

    private static VoxelShape rotatedBox(double[] box, Direction facing) {
        return switch (facing) {
            case EAST -> Block.box(
                    16 - box[5], box[1], box[0],
                    16 - box[2], box[4], box[3]);
            case SOUTH -> Block.box(
                    16 - box[3], box[1], 16 - box[5],
                    16 - box[0], box[4], 16 - box[2]);
            case WEST -> Block.box(
                    box[2], box[1], 16 - box[3],
                    box[5], box[4], 16 - box[0]);
            default -> Block.box(
                    box[0], box[1], box[2],
                    box[3], box[4], box[5]);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
