package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
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
 * S17(b) WATERFALL FREEZE v3 -- THE FLOW-TICK SEAM, refined by S18 into a GRADUAL BOTTOM-UP freeze. Freezes
 * MOVING polar water once it LANDS, closing the owner's two-flight complaint that liquid waterfalls still cascade
 * despite the S14 worldgen freeze and the S16 tick-descent -- without the S17b "wall of ice around the water"
 * (TEST 108): water now FALLS freely and only LANDED water freezes, so the fall runs to the ground live and the
 * ice pile grows upward layer by layer. See {@link com.example.globe.core.PolarWaterFreezeRule#landedOnSupport}
 * and {@link com.example.globe.core.PolarWaterFreezeRule#FLOW_FREEZE_CHANCE}.
 *
 * <p><b>Why the previous fixes missed it (root cause).</b> S16's {@code ServerLevelRoofedWaterFreezeMixin}
 * descends DOWNWARD from a column's {@code MOTION_BLOCKING} heightmap top. An open-air cascade FREE-FALLS above
 * the terrain surface, i.e. ABOVE that heightmap top, so the downward descent never reaches the falling column;
 * S14 only froze GENERATION-time flows. Neither catches a fall that starts (a bucket poured, a spring uncovered)
 * AFTER worldgen.
 *
 * <p><b>The seam.</b> {@code FlowingFluid.tick(ServerLevel, BlockPos, BlockState, FluidState)} (verified by
 * javap: its body opens {@code if (!fluidState.isSource()) { ...getNewLiquid/spread... }}) is the SINGLE method
 * every moving water block funnels through -- it is scheduled for each flowing block as a fall descends and for a
 * source's outflow as a new spring spreads. Injected at {@code HEAD} and cancellable, on an armed globe world in
 * the forced-freeze zone (the SAME {@code >= 85} frayed front the surface ice / roofed descent use), above the
 * freeze floor, on a non-ocean column: a FLOWING (non-source) water block that attempts to flow is replaced with
 * plain {@code ice} and the vanilla flow/spread is cancelled -- BUT ONLY once it has LANDED (S18). A block still
 * FALLING (air or another fluid directly below) is left to vanilla, so the fall runs to the ground live; a block
 * SUPPORTED below (solid or ice -- motion-terminated) is landed and freezes with {@code FLOW_FREEZE_CHANCE} per
 * flow tick. The freeze then CLIMBS by construction (each frozen block is the floor the water above then lands
 * on), so the fall ices from the bottom UP into a layered pile instead of snapping to the S17b wall. A new
 * spring's outflow lands on the ground and freezes as it spreads; STANDING cascade columns that already reached
 * equilibrium (and stopped re-ticking) are swept by the sibling UPWARD SCAN in
 * {@code ServerLevelRoofedWaterFreezeMixin} (also lowest-landed-first, same bottom-up direction). The SOURCE at
 * the top of a fall is {@code Fluids.WATER}, not {@code FLOWING_WATER}, so it is never touched here -- it freezes
 * LAST, on the existing still-water surface cadence, once its outflow has locked below it. Together with the S14
 * worldgen freeze (gen-time water) and the S15/16 roofed descent (still water under any cover), moving water is
 * now covered -- the complete coverage argument.
 *
 * <p><b>Only FLOWING water, only the pure decision.</b> Source water ({@code Fluids.WATER}) is left to the surface
 * freeze ({@code BiomePolarWaterFreezeMixin} via {@code tickPrecipitation}), and lava ({@code FLOWING_LAVA}) is
 * excluded, by gating on {@code fluidState.getType() == Fluids.FLOWING_WATER}. The freeze/exempt call is the pure
 * {@link PolarWaterFreezeRule#freezesFlowing}: ocean-family columns are EXEMPT (checked first inside it -- the sea
 * keeps liquid-under-ice), and {@code skyExposed} is passed {@code false} so the {@code aboveFreezeFloor} arm is
 * the sole discriminator -- a flowing block above the surface-40 freeze floor freezes; a deep spring below it
 * (both arms false) stays liquid (the B-9 Glacial Caves reservoir). Nothing touches worldgen RNG: only the flow
 * tick's own block is rewritten.
 *
 * <p><b>Byte-identical off-path.</b> The kill switch {@code POLAR_WATER_FREEZE_ENABLED}, the worldgen-family flag
 * {@code POLAR_BARRENS_ENABLED} (which {@code freezesFlowing} also requires), and the globe-world check all bail
 * at the head before any latitude math -- so with either flag off, on a non-globe world, or equatorward of the
 * frayable strip, vanilla fluid flow runs untouched. This method ticks for every flowing fluid block worldwide,
 * so it fast-bails cheapest-first (flags, then fluid type, then the column-Z latitude gate) and only the deep
 * polar cap ever pays for the heightmap reads.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidWaterfallFreezeMixin {

    @Inject(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void globe$freezeFlowingPolarWater(ServerLevel level, BlockPos pos, BlockState state,
                                               FluidState fluidState, CallbackInfo ci) {
        // Byte-identical fast bail: kill switch off, the S14 worldgen-freeze family off, or a non-globe world ->
        // vanilla fluid flow, no latitude math. (freezesFlowing also requires POLAR_BARRENS_ENABLED; requiring it
        // here too lets the whole method short-circuit before the fluid-type and latitude checks.)
        if (!LatitudeV2Flags.POLAR_WATER_FREEZE_ENABLED || !LatitudeV2Flags.POLAR_BARRENS_ENABLED
                || LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return;
        }
        // Only MOVING water: a source block's type is Fluids.WATER (handled by the surface freeze); lava is
        // FLOWING_LAVA. FLOWING_WATER is the single "water attempting to flow" case, and excludes both at once.
        if (fluidState.getType() != Fluids.FLOWING_WATER) {
            return;
        }
        // Cheap latitude gate from the block's Z: everything equatorward of the frayable strip is left entirely to
        // vanilla, so only the deep polar cap pays for the heightmap reads below.
        double absLatDeg = LatitudeMath.absLatDegExact(level.getWorldBorder(), pos.getZ());
        if (Double.isNaN(absLatDeg)
                || absLatDeg < PolarWaterFreezeRule.FREEZE_ALL_DEG - PolarWaterFreezeRule.FRAY_HALF_WIDTH_DEG) {
            return;
        }
        // The SAME frayed front the surface ice / solid rivers / roofed descent use (barrens-fray branch): sample
        // the coherent per-column noise only inside the +-1 strip, so the flow-freeze edge matches the frozen-sea
        // edge exactly; outside the strip the frayed predicate equals the razor by construction (fray stays NaN).
        double fray = Double.NaN;
        if (PolarWaterFreezeRule.inFreezeFrayBand(absLatDeg)) {
            fray = LatitudeBiomes.polarSeaFreezeFrayNoise(pos.getX(), pos.getZ());
        }
        // Freeze floor (the surface-40 idiom, reused): above it the moving water freezes; a deep spring below it
        // stays liquid (the B-9 reservoir). worldSurface = fluid top, oceanFloor = submerged bed (same two WG
        // heightmaps landWaterFreezeFloorY consumes for the solid-lake freeze).
        int worldSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int oceanFloorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ());
        boolean aboveFreezeFloor = pos.getY() > PolarWaterFreezeRule.landWaterFreezeFloorY(worldSurfaceY, oceanFloorY);
        // Ocean-family columns are EXEMPT -- freezesFlowing checks ocean FIRST (the sacred pin: liquid under the
        // pack ice, the S7 immersion mechanic). skyExposed=false: aboveFreezeFloor is the sole discriminator here
        // (a visible open fall is above the floor; a covered deep spring below it stays liquid).
        boolean isOcean = level.getBiome(pos).is(BiomeTags.IS_OCEAN);
        if (!PolarWaterFreezeRule.freezesFlowing(true, isOcean, absLatDeg, fray, false, aboveFreezeFloor)) {
            return; // fast bail before the below-block read: ocean / out-of-zone / below the floor -> vanilla flow
        }
        // S18 THE LANDED-WATER LAW (fixes the TEST-108 "wall of ice around the water"): water may FALL freely;
        // only LANDED water freezes. A flowing block still FALLING -- air or another fluid directly below -- is
        // left to vanilla so the fall runs to the ground live; only a block SUPPORTED below (solid or ice --
        // motion-terminated, or a base block spreading horizontally over solid) is landed and eligible. The
        // freeze then CLIMBS by construction: the base freezes -> the block above now rests on ice -> landed ->
        // eligible on its next flow tick -> the pile grows upward, layer by layer.
        BlockState belowState = level.getBlockState(pos.below());
        boolean landed = PolarWaterFreezeRule.landedOnSupport(
                belowState.isAir(), !belowState.getFluidState().isEmpty());
        if (!landed) {
            return; // still falling / pouring onto water -> vanilla flow (the fall reaches the ground first)
        }
        // FLOW_FREEZE_CHANCE per LANDED flow tick, rolled with the TICK's own RandomSource (never worldgen RNG):
        // the base spread gets a few flow ticks (~0.25 s each) to widen before it locks, so the frozen pile
        // reads organic instead of snapping to a wall. A failed roll returns to vanilla flow (it re-ticks and
        // re-rolls); the block above cannot land until this one is ice, so the climb is naturally bottom-up.
        if (!PolarWaterFreezeRule.flowTickFreezesLanded(true, true, level.getRandom().nextFloat())) {
            return;
        }
        // Landed and the roll locked it: replace this flowing block with a plain-ice cascade and cancel vanilla's
        // spread. The ice is the support the water above now rests on, so the fall freezes progressively upward.
        level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        ci.cancel();
    }
}
