package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S15(b) PERSISTENT ICE part 1 (the SKY WAIVER) + S16(b) TICK-FREEZE v2 (the FLOWING-water/waterfall freeze) +
 * S17(b) WATERFALL FREEZE v3 (the UPWARD SCAN). In the polar full-freeze zone, water under a roof freezes too,
 * flowing water (waterfalls, post-generation flows) freezes to a plain-ice cascade, AND a bounded belt scanned
 * UPWARD from the surface freezes standing free-fall columns. Server-side; a sibling of
 * {@code BiomePolarWaterFreezeMixin} (which waives the temperature veto inside {@code Biome.shouldFreeze}) and of
 * {@code FlowingFluidWaterfallFreezeMixin} (which freezes MOVING water at the flow tick). Together they make
 * sheltered polar water freeze the way the owner asked ("as soon as there is any shelter, the water is liquid
 * instead of ice") and close the waterfall gap.
 *
 * <p><b>S17(b) UPWARD SCAN -- why the downward descent alone was not enough.</b> An open-air cascade FREE-FALLS
 * above the terrain surface, i.e. ABOVE the {@code MOTION_BLOCKING} heightmap top (water is not motion-blocking
 * terrain), so walking DOWNWARD from that top never reaches the falling column -- the exact two-flight root cause.
 * The FLOW TICK ({@code FlowingFluidWaterfallFreezeMixin}) now freezes moving water at the instant it flows; this
 * mixin adds the complementary net for STANDING fall columns that already reached equilibrium and stopped
 * re-ticking: a belt of {@link PolarWaterFreezeRule#WATERFALL_UPWARD_SCAN_BLOCKS} blocks scanned UPWARD from the
 * surface, freezing any FLOWING water found (stopping early at the first solid block). Every belt block is above
 * the surface, hence far above the surface-40 freeze floor, so {@code aboveFreezeFloor} is true by construction --
 * the frozen ice is always above the B-9 deep-cave reservoir. Bounded, O(1)-ish per tick.
 *
 * <p><b>Where the sky requirement lives, and why FLOWING water survives.</b> {@code Biome.shouldFreeze} has no
 * sky check -- the "must see the sky" requirement is implicit in the CALLER. {@code
 * ServerLevel.tickPrecipitation(BlockPos)} freezes only {@code getHeightmapPos(MOTION_BLOCKING, pos).below()},
 * i.e. the topmost motion-blocking block of the column. Two things fall through it: (1) water UNDER a roof (the
 * roof is the heightmap top, the water below is never tested); (2) a waterfall's FLOWING blocks on a cliff FACE,
 * which sit BELOW the column's heightmap top (the ledge) and are never reached. NB the fluid TYPE is not the
 * blocker -- 26.2's {@code shouldFreeze} gates on {@code fluidState.is(Fluids.WATER)} (verified by javap;
 * {@code WaterFluid.isSame} matches BOTH source and flowing), so vanilla would freeze an exposed flowing block
 * that happened to be the tested top-1; the reason waterfalls persist is purely that {@code tickPrecipitation}
 * never TESTS the cascade blocks. This mixin reaches under the roof AND down the cascade.
 *
 * <p><b>What it does.</b> At the head of {@code tickPrecipitation}, only on an armed globe world with the kill
 * switch on and only in the forced-freeze zone (the SAME {@code >= 85} frayed front the open-water freeze uses,
 * selected exactly like {@code BiomePolarWaterFreezeMixin}): if the block vanilla would test is NOT already open
 * source water, descend up to {@link PolarWaterFreezeRule#ROOFED_FREEZE_REACH_BLOCKS} blocks and, for each block
 * in reach: a SOURCE water surface is left to vanilla's OWN {@code Biome.shouldFreeze} (genuine-water / light
 * &lt; 10 / edge checks intact, temperature veto already waived in-zone) and ends the descent (a surface skin);
 * a FLOWING water block freezes DIRECTLY to plain {@code ice} (the pure {@link
 * PolarWaterFreezeRule#freezesFlowing} decision -- barrens-family flag, ocean-EXEMPT, same frayed front) and the
 * descent CONTINUES to claim the rest of the cascade in reach. So a roofed pond freezes on vanilla's edge-inward
 * cadence; a waterfall's exposed cascade dies to ice over seconds as random columns are ticked; open source water
 * is left entirely to vanilla (byte-identical); the deep cave reservoir below the reach stays liquid (B-9), and
 * the ocean's under-ice liquid is never touched (the sacred pin -- {@code freezesFlowing} checks ocean FIRST).
 *
 * <p><b>Byte-identical off-path.</b> Non-globe worlds and {@code POLAR_WATER_FREEZE_ENABLED == false} bail at the
 * head before any work; columns equatorward of the frayable strip bail after a single latitude read (no heightmap,
 * no descent). The flowing-water freeze additionally requires {@code POLAR_BARRENS_ENABLED} (the S14 worldgen
 * freeze family -- barrens-off leaves flowing water exactly as vanilla), and its ocean classification is computed
 * ONLY when a flowing block is actually found (dry/roofed-source columns pay nothing extra). This injector never
 * cancels vanilla -- it only ADDS ice; vanilla's own freeze (on the roof, a no-op) and its snow/precipitation
 * pass run unchanged. Snow still accumulates on the roof, not under it.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelRoofedWaterFreezeMixin {

    @Inject(
            method = "tickPrecipitation(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            require = 1
    )
    private void globe$freezeRoofedPolarWater(BlockPos columnPos, CallbackInfo ci) {
        // Byte-identical fast bail: non-globe world or the kill switch off -> vanilla only, no latitude math.
        if (!LatitudeV2Flags.POLAR_WATER_FREEZE_ENABLED || LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return;
        }
        ServerLevel self = (ServerLevel) (Object) this;
        // Cheap latitude gate from the column's Z (the heightmap never changes X/Z): everything equatorward of
        // the frayable strip is left entirely to vanilla, so only the deep polar cap pays for the heightmap +
        // descent below.
        double absLatDeg = LatitudeMath.absLatDegExact(self.getWorldBorder(), columnPos.getZ());
        if (Double.isNaN(absLatDeg)
                || absLatDeg < PolarWaterFreezeRule.FREEZE_ALL_DEG - PolarWaterFreezeRule.FRAY_HALF_WIDTH_DEG) {
            return;
        }
        // In the forced-freeze zone? Mirror BiomePolarWaterFreezeMixin's razor/frayed selection so the roofed
        // water freeze and the open-water freeze share ONE front per column (barrens-fray branch flag-gated).
        // The fray sample is retained: the S16(b) flowing-water freeze rides the SAME front, so it must not
        // re-sample the noise and risk disagreeing with the surface-ice edge on the frayed strip.
        double fray = Double.NaN;
        boolean sourceZone;
        if (LatitudeV2Flags.POLAR_BARRENS_ENABLED && PolarWaterFreezeRule.inFreezeFrayBand(absLatDeg)) {
            fray = LatitudeBiomes.polarSeaFreezeFrayNoise(columnPos.getX(), columnPos.getZ());
            sourceZone = PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, absLatDeg, fray);
        } else {
            sourceZone = PolarWaterFreezeRule.freezeIgnoresSky(true, absLatDeg);
        }
        // freezesFlowing rides the SAME 85-deg frayed front (freezesWaterFrayed) plus a barrens-flag + ocean
        // gate, so it is a SUBSET of sourceZone: a column outside the sky-waiver front can never flow-freeze
        // either, and bailing on !sourceZone here also short-circuits the flowing path (one front, one truth).
        if (!sourceZone) {
            return;
        }
        BlockPos top = self.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, columnPos);
        // Per-column flowing-freeze eligibility (barrens flag + ocean exemption + the frayed front), resolved
        // once lazily and SHARED by both the upward and downward scans below. aboveFreezeFloor is TRUE by
        // construction for every block either scan touches (above the surface, or within the 16-block roof reach
        // -- both far above the 40-block glacier-sole floor), so the sky/skyExposed arm is irrelevant.
        int flowFreezes = -1; // lazy tri-state: -1 unknown, 0 no, 1 yes

        // S17(b) UPWARD SCAN: an open-air cascade free-falls ABOVE the MOTION_BLOCKING top (water is not
        // motion-blocking terrain), so the downward roofed descent below never reaches it. Scan a bounded belt
        // UPWARD from the surface and freeze standing flowing columns the flow tick did not re-visit; stop at the
        // first solid (non-fluid) block -- nothing of THIS fall lies higher.
        int ceilY = Math.min(self.getMaxY(), top.getY() + PolarWaterFreezeRule.WATERFALL_UPWARD_SCAN_BLOCKS);
        BlockPos.MutableBlockPos up = new BlockPos.MutableBlockPos(top.getX(), 0, top.getZ());
        for (int y = top.getY() + 1; y <= ceilY; y++) {
            up.setY(y);
            BlockState above = self.getBlockState(up);
            FluidState fs = above.getFluidState();
            if (fs.getType() == Fluids.FLOWING_WATER) {
                flowFreezes = globe$resolveFlowFreeze(self, top, absLatDeg, fray, flowFreezes);
                if (flowFreezes == 1) {
                    self.setBlockAndUpdate(up.immutable(), Blocks.ICE.defaultBlockState()); // frozen standing fall
                }
            } else if (!above.isAir() && fs.isEmpty()) {
                break; // a solid (non-fluid) block caps this cascade
            }
        }

        // Vanilla freezes the MOTION_BLOCKING top-1. If THAT block is already open (source) water, vanilla
        // handles it byte-identically (edge-inward, same cadence) -- leave that path untouched (and an open
        // source surface means there is no waterfall to freeze in this column).
        BlockPos surface = top.below();
        if (self.getFluidState(surface).getType() == Fluids.WATER) {
            return;
        }
        // Covered/dry column: reach under the roof (and down any waterfall) within the shelter reach. A SOURCE
        // water surface is left to vanilla's OWN shouldFreeze (genuine-water / light / edge, temperature veto
        // already waived in-zone) and ends the descent (a surface skin). A FLOWING block freezes DIRECTLY to a
        // plain-ice cascade (S16(b); vanilla's tickPrecipitation never tests it) and the descent CONTINUES to
        // claim the rest of the cascade. Water/flow sealed deeper than the reach stays liquid (the B-9 reservoir).
        int floorY = Math.max(self.getMinY(), top.getY() - PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(top.getX(), 0, top.getZ());
        for (int y = surface.getY(); y >= floorY; y--) {
            cursor.setY(y);
            FluidState fs = self.getFluidState(cursor);
            if (fs.getType() == Fluids.WATER) {
                // SOURCE water surface under a roof (sky-waiver): let vanilla's own shouldFreeze decide.
                Biome biome = self.getBiome(cursor).value();
                if (biome.shouldFreeze(self, cursor)) {
                    self.setBlockAndUpdate(cursor.immutable(), Blocks.ICE.defaultBlockState());
                }
                return; // topmost water surface handled
            }
            if (fs.getType() == Fluids.FLOWING_WATER) {
                flowFreezes = globe$resolveFlowFreeze(self, top, absLatDeg, fray, flowFreezes);
                if (flowFreezes == 1) {
                    self.setBlockAndUpdate(cursor.immutable(), Blocks.ICE.defaultBlockState()); // frozen cascade
                }
            }
        }
    }

    /**
     * Per-column flowing-freeze eligibility, resolved once and cached in the tri-state (-1 unknown / 0 no / 1
     * yes). The barrens-family flag gates it; {@link PolarWaterFreezeRule#freezesFlowing} checks the ocean
     * exemption FIRST and rides the same frayed front. {@code aboveFreezeFloor} is passed {@code true} -- true by
     * construction for every block the upward/downward scans touch (all far above the glacier-sole freeze floor).
     */
    private static int globe$resolveFlowFreeze(ServerLevel self, BlockPos top, double absLatDeg, double fray,
                                               int cached) {
        if (cached != -1) {
            return cached;
        }
        boolean eligible = false;
        if (LatitudeV2Flags.POLAR_BARRENS_ENABLED) {
            boolean isOcean = self.getBiome(top).is(BiomeTags.IS_OCEAN);
            eligible = PolarWaterFreezeRule.freezesFlowing(true, isOcean, absLatDeg, fray, false, true);
        }
        return eligible ? 1 : 0;
    }
}
