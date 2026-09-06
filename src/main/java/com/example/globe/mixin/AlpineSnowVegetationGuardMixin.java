package com.example.globe.mixin;

import com.example.globe.world.AlpineVegetationPolicy;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nothing grows in the alpine snow cap (maintainer ruling, 2026-08-15).
 *
 * <p>{@link AlpineSurfaceMixin} rewrites high natural surfaces into alpine rock or snow, and its
 * note argued that the snow zone therefore leaves no grass block for vegetation to root in, so
 * nothing could poke through. That holds for the surface pass and only the surface pass. Decoration
 * runs afterwards with plants of its own, and saved chunks showed grass, cornflowers and tulips
 * standing directly on {@code snow_block} well above the height where the cap is unconditional —
 * the ground was already snow when the feature placed on it. The cap decides what the ground is; it
 * never had a say over what decoration puts on top.
 *
 * <p><b>Why the block write.</b> Same reasoning as
 * {@link ProtoChunkPolarVegetationGuardMixin}: feature classes are an unbounded set, and
 * enumerating them is exactly what left fallen trees, huge mushrooms and modded shrubs outside the
 * polar guard. Guarding the write cannot be outflanked by a feature type nobody has enumerated.
 *
 * <p><b>Determinism.</b> Cancelling here cannot desynchronise worldgen: the feature has already run
 * and consumed its randomness by the time it asks the chunk to store a block. Only whether the
 * resulting block is kept changes.
 *
 * <p><b>Scope.</b> Ground cover only. Woody content above the same height is already refused by the
 * tree line, which sits at the alpine rock line, so this guard deliberately does not repeat that
 * tier. Terrain, snow, ice and fluids are neither tagged foliage nor {@code BushBlock}s, so
 * the test cannot erase the cap it is protecting.
 */
@Mixin(ProtoChunk.class)
public class AlpineSnowVegetationGuardMixin {

    @Unique
    private static final TagKey<Block> GLOBE_ALPINE_FOLIAGE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("globe", "polar_foliage"));

    @Unique
    private static final BlockState GLOBE_ALPINE_AIR = Blocks.AIR.defaultBlockState();

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void globe$suppressAlpineSnowVegetation(
            BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        if (state == null || state.isAir()) {
            return;
        }
        // Worldgen only, and only in a world Latitude actually generates. Outside the scope this
        // must be inert: bonemeal, sapling growth and /place are the player's business.
        if (!LatitudeWorldgenScope.isActive()) {
            return;
        }
        if (pos.getY() < LatitudeBiomes.ALPINE_ROCK_Y) {
            return;
        }

        boolean foliage = state.is(GLOBE_ALPINE_FOLIAGE);
        boolean vegetation = state.getBlock() instanceof BushBlock;
        if (!foliage && !vegetation) {
            return;
        }

        int radius = LatitudeBiomes.getActiveRadiusBlocks();
        if (radius <= 0) {
            return;
        }

        // Judge the block the plant is ROOTED ON, never the plant's own position. The snow test
        // rises with height, so judging the plant itself removed plants rooted on real ground one
        // block below the line, and — because each half of a two-block plant sits at a different
        // height — split tall plants down the middle when the threshold fell between them. Both
        // halves resolve to this same footing position, so they cannot disagree.
        boolean upperHalf = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        BlockPos footing = pos.below(AlpineVegetationPolicy.footingOffsetBlocks(upperHalf));

        int kind = LatitudeBiomes.alpineSurfaceKind(
                footing.getX(), footing.getY(), footing.getZ(), radius);
        // The cap's own question, plus the block really there. The kind alone would strip village
        // crops off farmland on the strength of a theoretical snow height; the block alone would
        // reach snow the cap never placed.
        boolean footingIsSnow = ((ProtoChunk) (Object) this)
                .getBlockState(footing).is(Blocks.SNOW_BLOCK);
        if (AlpineVegetationPolicy.shouldSuppressAlpineVegetation(
                kind, footingIsSnow, foliage, vegetation)) {
            cir.setReturnValue(GLOBE_ALPINE_AIR);
        }
    }
}
