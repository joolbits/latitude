package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.world.LatitudeBiomes;
import com.example.globe.world.LatitudeWorldgenScope;
import com.example.globe.world.PolarFoliagePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block-level polar vegetation guard — the backstop the feature-class guards structurally cannot be.
 *
 * <p>{@link com.example.globe.mixin.ExtremePolarVegetationGuardMixin} cancels {@code TreeFeature}
 * and {@link com.example.globe.mixin.ExtremePolarSimpleFoliageGuardMixin} cancels
 * {@code SimpleBlockFeature}. Both are kept — cancelling a tree before it writes anything is
 * cheaper and cleaner than deleting its blocks one at a time, and the simple-block guard
 * deliberately lets {@code getState} run first so the {@code RandomSource} advances exactly as
 * vanilla would. But between them they name two feature classes out of an unbounded set, and the
 * gap was load-bearing:
 *
 * <ul>
 *   <li>{@code minecraft:fallen_tree} is {@code FallenTreeFeature extends Feature} — a SIBLING of
 *       {@code TreeFeature}, not a subclass — so felled trunks placed above the limit with no packs
 *       installed, and its {@code log_decorators} rode brown/red mushrooms in on top of them. That
 *       is the reported symptom: standing trees forbidden, logs and mushrooms present anyway.</li>
 *   <li>BOP's fallen-log family, {@code patch_tundra_shrubs} (up to 288 attempts per chunk), reeds,
 *       toadstools, huge mushrooms, {@code multiface_growth} glow lichen and {@code block_column}
 *       sugar cane are all raw {@code Feature}s too.</li>
 *   <li>{@code globe:polar_foliage} listed {@code #minecraft:logs} the whole time and it changed
 *       nothing, because the tag had exactly one reader and that reader only ever sees
 *       {@code SimpleBlockFeature}.</li>
 * </ul>
 *
 * <p>Guarding the block WRITE closes every one of those at once and cannot be outflanked by a
 * feature type nobody has enumerated yet. {@code ProtoChunk.setBlockState} is the same write path
 * Latitude already guards for warm-band snow, so this is a proven interception point rather than a
 * new one.
 *
 * <p><b>Determinism.</b> Cancelling here cannot desynchronise worldgen: the feature has already run
 * and consumed its randomness by the time it asks the chunk to store a block. Nothing about the
 * {@code RandomSource} sequence changes — only whether the resulting block is kept.
 *
 * <p><b>Fails closed, without depending on pack contents.</b> A tag-only test fails OPEN on any
 * modded block the pack never added to a vanilla tag, which is how BOP tundra shrubs and reeds
 * survived while the vanilla grass and ferns beside them were stripped — leaving the polar cap
 * looking MORE vegetated than a correctly-guarded biome. {@code BushBlock} is the vanilla
 * plant base; BOP's own {@code VegetationBlockBOP} extends it, as do its flower, mushroom, sapling
 * and double-plant blocks, so modded ground cover is caught by inheritance rather than by
 * enumeration. Fluids, snow, ice, terrain and logs are not {@code BushBlock}s, so the test
 * cannot erase the world it is standing on.
 */
@Mixin(ProtoChunk.class)
public class ProtoChunkPolarVegetationGuardMixin {

    @Unique
    private static final TagKey<Block> POLAR_FOLIAGE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("globe", "polar_foliage"));

    @Unique
    private static final TagKey<Block> POLAR_WOODY =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("globe", "polar_woody"));

    @Unique
    private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void globe$suppressPolarVegetation(
            BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        if (state == null || state.isAir()) {
            return;
        }
        // Worldgen only, and only in a world Latitude actually generates. Outside the scope this
        // must be inert: bonemeal, sapling growth and /place are the player's business.
        if (!LatitudeWorldgenScope.isFeatureActive()) {
            return;
        }

        boolean woody = state.is(POLAR_WOODY);
        boolean foliage = state.is(POLAR_FOLIAGE);
        boolean vegetation = state.getBlock() instanceof BushBlock;
        if (!woody && !foliage && !vegetation) {
            return;
        }

        // TreeFeature already rejects new trees at this height. This block-write backstop closes
        // the same alpine line for raw Feature siblings such as fallen trees and modded log
        // features, while deliberately leaving grass, flowers and other alpine ground cover alone.
        if (woody && pos.getY() >= LatitudeBiomes.TREE_LINE_Y) {
            cir.setReturnValue(AIR_STATE);
            return;
        }

        int activeRadius = LatitudeBiomes.getActiveRadiusBlocks();
        // Tree features still reject origins beyond the strict 72-degree limit. At this final
        // block-write seam, allow only a fixed overhang so a legal tree that begins inside the
        // line can finish its canopy instead of losing leaves across the latitude boundary.
        boolean beyondWoody = PolarFoliagePolicy.isBeyondWoodyCompletionLimit(
                pos.getZ(), activeRadius, GlobeMod.BORDER_RADIUS);

        // Dependent-support integrity. Stripping woody blocks per-block leaves the REST of a
        // custom feature standing: biomesoplenty:fallen_fir_log places its log, this guard turns
        // that log to air on the write path mid-feature, and the feature's own decorations then
        // land on nothing -- observed live as floating mushrooms at 79 degrees (maintainer
        // report, 2026-08-31). So wherever this guard can have removed a block (beyond the woody
        // latitude, or above the elevation tree line), a FLOOR PLANT placed over air is refused.
        //
        // Scoped to BushBlock deliberately, not the polar_foliage tag: BushBlock
        // subclasses are floor-supported by vanilla contract, while the tag also carries
        // wall- and ceiling-attached blocks (glow lichen) for which air below is legitimate.
        if (vegetation && (beyondWoody || pos.getY() >= LatitudeBiomes.TREE_LINE_Y)) {
            ProtoChunk self = (ProtoChunk) (Object) this;
            if (pos.getY() > self.getMinBuildHeight() && self.getBlockState(pos.below()).isAir()) {
                cir.setReturnValue(AIR_STATE);
                return;
            }
        }

        if (!beyondWoody) {
            // Below the tree line neither tier applies; the foliage limit is strictly higher.
            return;
        }
        boolean beyondFoliage = PolarFoliagePolicy.isBeyondLimit(
                pos.getZ(), activeRadius, GlobeMod.BORDER_RADIUS);

        if (PolarFoliagePolicy.shouldSuppressPolarBlock(
                beyondWoody,
                beyondFoliage,
                woody,
                foliage,
                vegetation,
                state.is(Blocks.SWEET_BERRY_BUSH))) {
            cir.setReturnValue(AIR_STATE);
        }
    }
}
