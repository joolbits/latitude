package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarInstrument;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
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
 * S17(b) WATERFALL FREEZE v3 -- THE FLOW-TICK SEAM, refined by S18 (bottom-up), S19 (ice-contact), and now S20
 * into the flow tick's ONLY two jobs: (C) FALLS RUN FREE and (B) THE ICE-TOUCH HUNTER. It freezes MOVING polar
 * water only where it has LANDED AND is TOUCHING ICE -- a CERTAIN lock, drawing no random. Everything else that
 * has come to rest (settled pools, standing cascades) is now owned by the SETTLED SWEEP in
 * {@code ServerLevelRoofedWaterFreezeMixin}. See {@link com.example.globe.core.PolarWaterFreezeRule} for the S20
 * division of labour, and {@link com.example.globe.core.PolarWaterFreezeRule#landedOnSupport},
 * {@link com.example.globe.core.PolarWaterFreezeRule#touchingIce} and
 * {@link com.example.globe.core.PolarWaterFreezeRule#flowTickHunterFreezes}.
 *
 * <p><b>S20 ROOT CAUSE (the THIRD failed water round, TEST 110).</b> A flowing block only receives flow TICKS
 * while it is actively SPREADING; at equilibrium the ticks STOP. Hosting the ground/pool freeze on a per-flow-tick
 * CHANCE (S19: 0.2 on solid) meant most blocks SETTLED before a roll ever landed and then never ticked again --
 * "the water is not freezing" live. S20 moves ALL at-rest freezing to the tickPrecipitation SWEEP (which keeps
 * visiting a column after its water stops ticking) and leaves the flow tick only the certain ice-touch hunter,
 * which cannot be outrun: the moment water touches ice it locks, so reroutes and the vertical climb are
 * deterministic.
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
 * plain {@code ice} and the vanilla flow/spread is cancelled -- BUT ONLY once it has LANDED (S18) AND is TOUCHING
 * ICE (S20 -- the ice-touch hunter). A block still FALLING (air or another fluid directly below) is left to
 * vanilla, so the fall runs to the ground live; a landed block on ORDINARY support (not touching ice) is ALSO
 * left to keep spreading -- S20 removed the on-solid chance, so the flow tick no longer freezes bare ground at
 * all; that water is claimed later by the SETTLED SWEEP once it comes to rest. The hunter freeze is CERTAIN and
 * draws no random. The freeze then CLIMBS by construction (each frozen block is the floor the water above then
 * lands on, and touching that ice makes the next block certain -- a ~4 blocks/s zipper), so a fall ices from the
 * bottom UP into a layered pile, and a horizontal reroute onto/beside fresh ice locks one block out (the TEST-109
 * speckle heals). SETTLED pools and STANDING cascade columns that reached equilibrium (and stopped re-ticking)
 * are frozen by the sibling SWEEP in {@code ServerLevelRoofedWaterFreezeMixin} (settled + landed, bottom-up). The
 * SOURCE at the top of a fall is {@code Fluids.WATER}, not {@code FLOWING_WATER}, so it is never touched here --
 * it freezes LAST, on the existing still-water surface cadence, once its outflow has locked below it. Together
 * with the S14 worldgen freeze (gen-time water), the S15/16 roofed descent, and the S20 settled sweep, moving and
 * at-rest water are now both covered -- the complete coverage argument.
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
        // S20 instrument (default off): an eligible in-zone flowing-water flow tick -- the denominator the FREEZE
        // recorder reports, so a live flight can see whether the pour is ticking at all.
        if (PolarInstrument.FREEZE) {
            PolarInstrument.freezeFlowTick();
        }
        // S18 FALLS RUN FREE (consumer (C)): water may FALL freely; only LANDED water freezes. A flowing block
        // still FALLING -- air or another fluid directly below -- is left to vanilla so the fall runs to the
        // ground live; only a block SUPPORTED below (solid or ice -- motion-terminated, or a base spreading
        // horizontally over solid) is landed and a candidate for the ice-touch hunter.
        BlockState belowState = level.getBlockState(pos.below());
        boolean landed = PolarWaterFreezeRule.landedOnSupport(
                belowState.isAir(), !belowState.getFluidState().isEmpty());
        if (!landed) {
            if (PolarInstrument.FREEZE) {
                PolarInstrument.freezePassedFalling();
            }
            return; // still falling / pouring onto water -> vanilla flow (the fall reaches the ground first)
        }
        // S20 THE ICE-TOUCH HUNTER (consumer (B)) -- the flow tick's ONLY freeze job now. The on-solid chance is
        // GONE: a landed block on ORDINARY support is left to keep spreading and is claimed later by the SETTLED
        // SWEEP (ServerLevelRoofedWaterFreezeMixin) once it comes to rest -- so there is NO RNG in the flow path.
        // This clause only LOCKS reroutes / drives the vertical zipper the instant water touches existing ice. The
        // touch set is the block BELOW plus the 4 horizontal neighbours (ABOVE excluded -- it is the source
        // direction); touching ice-family (#minecraft:ice: ice/packed/blue/frosted) -> CERTAIN freeze.
        boolean touchingIce = PolarWaterFreezeRule.touchingIce(
                belowState.is(BlockTags.ICE),
                level.getBlockState(pos.north()).is(BlockTags.ICE),
                level.getBlockState(pos.east()).is(BlockTags.ICE),
                level.getBlockState(pos.south()).is(BlockTags.ICE),
                level.getBlockState(pos.west()).is(BlockTags.ICE));
        if (!PolarWaterFreezeRule.flowTickHunterFreezes(true, true, touchingIce)) {
            return; // landed but not touching ice -> keep spreading; the settled sweep will claim it once at rest
        }
        // Landed AND touching ice -> lock it (certain, no dice). Replace this flowing block with a plain-ice
        // cascade and cancel vanilla's spread. The ice is the support the water above now rests on -- AND makes it
        // touching-ice -- so the fall freezes deterministically upward (the ~4 blocks/s zipper), and a horizontal
        // reroute onto/beside this ice is itself touching-ice next tick (the speckle heals).
        level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
        if (PolarInstrument.FREEZE) {
            PolarInstrument.freezeHunterFroze();
        }
        ci.cancel();
    }
}
