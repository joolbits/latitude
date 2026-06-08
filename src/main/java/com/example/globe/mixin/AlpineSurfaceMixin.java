package com.example.globe.mixin;

import com.example.globe.world.LatitudeBiomes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Alpine surface: above {@link LatitudeBiomes#ALPINE_ROCK_Y}, rewrite the natural surface block to
 * stone (the "very highest bits" turn rocky) — with a patchy fade so meadow survives between the tree
 * line and the rock. Cold-latitude peaks (subpolar/polar) cap with snow instead of bare rock.
 *
 * <p>Rewrites the BlockState argument of {@link ProtoChunk#setBlockState} during worldgen, mirroring
 * {@code ChunkRegionWarmSnowTrapMixin} — no extra setBlockState calls, no recursion. Only natural
 * surface materials are touched, so features / ores / structures are unaffected.</p>
 */
@Mixin(ProtoChunk.class)
public abstract class AlpineSurfaceMixin {

    @Unique private static final BlockState GLOBE_ALPINE_STONE = Blocks.STONE.getDefaultState();
    @Unique private static final BlockState GLOBE_ALPINE_SNOW = Blocks.SNOW_BLOCK.getDefaultState();

    @ModifyVariable(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private BlockState globe$alpineSurface(BlockState state, BlockPos pos) {
        if (state == null) {
            return state;
        }
        int radius = LatitudeBiomes.ACTIVE_RADIUS_BLOCKS;
        if (radius <= 0 || pos.getY() < LatitudeBiomes.ALPINE_ROCK_Y) {
            return state; // not a Globe world, or below the rock line — fast out
        }
        Block b = state.getBlock();
        // Only natural ground surface materials; leave logs/leaves/ores/structures/etc. alone.
        if (b != Blocks.GRASS_BLOCK && b != Blocks.DIRT && b != Blocks.COARSE_DIRT
                && b != Blocks.PODZOL && b != Blocks.MYCELIUM && b != Blocks.GRAVEL) {
            return state;
        }
        int kind = LatitudeBiomes.alpineSurfaceKind(pos.getX(), pos.getY(), pos.getZ(), radius);
        return switch (kind) {
            case 1 -> GLOBE_ALPINE_STONE;
            case 2 -> GLOBE_ALPINE_SNOW;
            default -> state;
        };
    }
}
