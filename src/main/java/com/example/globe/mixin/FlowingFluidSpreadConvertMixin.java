package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarBarrensBand;
import com.example.globe.core.PolarInstrument;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S22 WATER v6 (c) -- CONVERT-AT-SPREAD, the REAL spread-stopper (owner flight TEST 113, 2026-07-19: the
 * FOURTH live water failure). At exactly 85S the owner's pour flooded tens of blocks in 12 s and sat at
 * 20-30% checkerboard ice at 36 s: the freeze could not gain ground because NO spread-stopper existed -- the
 * S21(d) paragraph that claimed one was a coverage ARGUMENT, not code, and forensics found its three holes:
 * <ol>
 *   <li><b>Not-landed-over-water is invisible.</b> A flowing block whose below is water (the re-spread layer
 *       riding pooled supply) is "not landed", so BOTH existing consumers -- the flow-tick hunter and the
 *       settled sweep -- skip it, and it spreads freely.</li>
 *   <li><b>The RATCHET.</b> Every freeze's {@code setBlockAndUpdate} neighbour updates schedule FRESH fluid
 *       ticks on the adjacent supply, which immediately re-spreads a NEW water layer ON TOP of the fresh ice.
 *       Each conversion buys one block and hands the supply a new path -- net zero.</li>
 *   <li><b>Source-freezes-last keeps the supply alive.</b> Correct in itself (S21d), but combined with the
 *       ratchet it means the pour never starves: the source outlives an endlessly-recycling flow.</li>
 * </ol>
 *
 * <p><b>The seam.</b> {@code FlowingFluid.spreadTo(LevelAccessor, BlockPos, BlockState, Direction,
 * FluidState)} (javap-verified against the 26.2 merged-deobf jar: {@code protected void spreadTo(...)}; its
 * body either delegates to a {@code LiquidBlockContainer.placeLiquid} or {@code setBlock}s the fluid's legacy
 * block) is the single point where moving water CLAIMS A NEW CELL. Injected at {@code HEAD} and cancellable:
 * when the destination is in the tick front, non-ocean, above the freeze floor, and ICE-ADJACENT (the
 * existing hunter touch set -- BELOW + the 4 horizontals, {@code BlockTags.ICE}), we place plain {@code ICE}
 * at the destination INSTEAD of water and cancel vanilla's placement -- which also cancels the fluid-tick
 * SCHEDULING that placement would have triggered ({@code LiquidBlock.onPlace}). <b>CONVERT, not deny:</b>
 * denying the spread would leave the supply recomputing the same spread forever (the fluid engine re-tries
 * while the cell stays open); conversion ADVANCES the freeze one cell and TERMINATES the path -- the engine
 * sees a solid neighbour and stops. Each conversion also extends the ice, so the next spread attempt is
 * ice-adjacent one cell further out: the freeze now out-paces the spread instead of chasing it. The decision
 * is the pure {@link PolarWaterFreezeRule#spreadConvertsToIce}.
 *
 * <p><b>Invariants, verbatim from the family:</b>
 * <ul>
 *   <li><b>FALLS RUN FREE</b> (S18): a STRAIGHT-DOWN spread converts ONLY when the destination's below is
 *       already ice (the fall landing ON the frozen pile -- the zipper's contact, caught one tick earlier
 *       than the hunter would). A fall dropping past an ice wall into open air stays water, so a cascade
 *       always reaches the ground live -- never beheaded mid-air.</li>
 *   <li><b>SOURCE-FREEZES-LAST untouched</b> (S21d): a spread destination is always FLOWING water-to-be,
 *       never a source; starving the outflow is exactly how the source comes to freeze last.</li>
 *   <li><b>THE SEA IS EXEMPT</b>: ocean-family destinations never convert
 *       ({@link PolarWaterFreezeRule#tickFreezesFlowing} checks ocean FIRST -- liquid under the pack ice, the
 *       S7 immersion mechanic).</li>
 *   <li><b>The B-9 reservoir</b>: destinations at/below the {@link PolarWaterFreezeRule#landWaterFreezeFloorY
 *       freeze floor} (glacier sole, surface-40) never convert -- deep springs stay liquid.</li>
 *   <li><b>Pure function of block state, tick-time only</b>: the {@code level instanceof ServerLevel} gate
 *       (the S21-sweep precedent) plus the fact that {@code spreadTo} is only reached from the live fluid
 *       tick keep worldgen bytes untouched; no RNG anywhere.</li>
 * </ul>
 *
 * <p><b>Destinations that are NOT plainly claimable are left to vanilla</b> (documented choice): a
 * {@code LiquidBlockContainer} destination (waterlogging a stairs/slab -- converting would silently delete
 * the container block) and a non-air, non-fluid destination (a flower/torch vanilla is about to destroy WITH
 * DROPS via {@code beforeDestroyingBlock} -- converting would eat the drops) both pass through untouched; the
 * water they place is then caught by the hunter/sweep like any other. Air and existing-fluid destinations --
 * the entire ratchet surface -- convert.
 *
 * <p><b>Byte-identical off-path.</b> Kill switch off, barrens family off, non-globe world, non-water fluid
 * (lava spreads are excluded by the {@code FLOWING_WATER} type gate), or equatorward of the 82 tick-front
 * onset: bail before any heightmap/biome read. Only in-front spreads pay for the adjacency reads -- and only
 * ice-adjacent ones for anything more.
 *
 * <p>Registered in {@code globe.mixins.json} (appended, v6). Instrument: each conversion counts
 * {@code spreadFroze} on the {@code -Dlatitude.debugFreeze} recorder -- the proof channel for the next
 * self-fly (a pour meeting ice shows {@code spreadFroze} climbing, then the path terminating).
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidSpreadConvertMixin {

    @Inject(
            method = "spreadTo(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void globe$convertSpreadToIce(LevelAccessor level, BlockPos pos, BlockState destState,
                                          Direction direction, FluidState fluidState, CallbackInfo ci) {
        // Byte-identical fast bail, cheapest first: flags, then the tick-time gate, then the fluid type.
        if (!LatitudeV2Flags.POLAR_WATER_FREEZE_ENABLED || !LatitudeV2Flags.POLAR_BARRENS_ENABLED
                || LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return;
        }
        // TICK-TIME ONLY (the S21-sweep precedent): spreadTo is reached from the live fluid tick, but the
        // instanceof gate is kept as the hard guarantee that no worldgen path (WorldGenRegion implements
        // LevelAccessor) can ever be converted -- worldgen bytes stay identical by construction.
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // Only WATER spread: the spread state at a destination is always flowing; lava (FLOWING_LAVA) and any
        // modded fluid are excluded in one check.
        if (fluidState.getType() != Fluids.FLOWING_WATER) {
            return;
        }
        // Claimability (documented choice, see class javadoc): waterloggable containers keep vanilla's
        // placeLiquid; a destroy-with-drops block keeps vanilla's beforeDestroyingBlock. Air and existing
        // fluid -- the whole ratchet surface -- are claimable.
        if (destState.getBlock() instanceof LiquidBlockContainer) {
            return;
        }
        if (!destState.isAir() && destState.getFluidState().isEmpty()) {
            return;
        }
        // Cheap latitude gate at the DESTINATION's Z, then the S22 TICK FRONT -- the SAME shared barrens band
        // decision (ONSET 82 -> FULL 84 on the coherent barrens fray) as the hunter/sweep/biome/glacier/
        // carvers; the fray sample is skipped at/above FULL_DEG (fraction 1.0, any sample passes).
        double absLatDeg = LatitudeMath.absLatDegExact(server.getWorldBorder(), pos.getZ());
        if (Double.isNaN(absLatDeg) || absLatDeg < PolarWaterFreezeRule.TICK_FRONT_ONSET_DEG) {
            return;
        }
        double barrensFray = absLatDeg < PolarBarrensBand.FULL_DEG
                ? LatitudeBiomes.polarBarrensFrayNoise(pos.getX(), pos.getZ())
                : 0.0;
        // Ice-adjacency FIRST among the world reads: most in-front spreads are nowhere near ice, and the
        // below-block read doubles for the falls-run-free clause. Touch set = the existing hunter set
        // (BELOW + 4 horizontals, ABOVE excluded -- the source direction; BlockTags.ICE = ice/packed/blue/
        // frosted), reused verbatim so the converter and the hunter agree on what "touching ice" means.
        boolean belowIsIce = server.getBlockState(pos.below()).is(BlockTags.ICE);
        boolean touchingIce = PolarWaterFreezeRule.touchingIce(
                belowIsIce,
                server.getBlockState(pos.north()).is(BlockTags.ICE),
                server.getBlockState(pos.east()).is(BlockTags.ICE),
                server.getBlockState(pos.south()).is(BlockTags.ICE),
                server.getBlockState(pos.west()).is(BlockTags.ICE));
        boolean spreadIsDown = direction == Direction.DOWN;
        if (!touchingIce) {
            // Not ice-adjacent at all (belowIsIce is a member of the touch set, so this also rules out the
            // falls-landing-on-ice clause): an ordinary spread -- vanilla places water, hunter/sweep own it.
            return;
        }
        // Freeze floor + ocean exemption (the shared predicates, resolved only for ice-adjacent spreads):
        // worldSurface = fluid top, oceanFloor = submerged bed -- the same two heightmaps every consumer
        // feeds landWaterFreezeFloorY.
        int worldSurfaceY = server.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int oceanFloorY = server.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ());
        boolean aboveFreezeFloor =
                pos.getY() > PolarWaterFreezeRule.landWaterFreezeFloorY(worldSurfaceY, oceanFloorY);
        boolean isOcean = server.getBiome(pos).is(BiomeTags.IS_OCEAN);
        boolean eligible = PolarWaterFreezeRule.tickFreezesFlowing(
                true, isOcean, absLatDeg, barrensFray, aboveFreezeFloor);
        if (!PolarWaterFreezeRule.spreadConvertsToIce(eligible, spreadIsDown, belowIsIce, touchingIce)) {
            return; // ocean / below-floor / mid-air fall past an ice wall: water spreads exactly as vanilla
        }
        // CONVERT: plain ice at the destination instead of water, vanilla placement + its tick scheduling
        // cancelled. The freeze advances one cell; the path terminates; the ratchet is dead.
        server.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        if (PolarInstrument.FREEZE) {
            PolarInstrument.freezeSpreadFroze();
        }
        ci.cancel();
    }
}
