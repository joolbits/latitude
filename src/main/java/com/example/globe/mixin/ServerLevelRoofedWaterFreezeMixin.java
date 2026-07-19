package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarInstrument;
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
 * S15(b) PERSISTENT ICE part 1 (the SKY WAIVER) + S16(b) TICK-FREEZE v2 + S17(b) UPWARD SCAN, and now S20 THE
 * SETTLED SWEEP -- the PRIMARY polar water-freezer. In the full-freeze zone, water under a roof freezes, and any
 * flowing water (pools, post-generation flows, standing cascades) freezes to plain ice ONCE IT HAS SETTLED --
 * the game's own at-rest definition (no pending scheduled fluid tick). Server-side; a sibling of
 * {@code BiomePolarWaterFreezeMixin} (which waives the temperature veto inside {@code Biome.shouldFreeze}) and of
 * {@code FlowingFluidWaterfallFreezeMixin} (which now only runs the ICE-TOUCH HUNTER at the flow tick).
 *
 * <p><b>S20 -- why the sweep, not the flow tick, owns ground/pool freezing (the THIRD failed water round).</b> A
 * flowing block only receives flow TICKS while it is actively SPREADING; at equilibrium the ticks STOP. The
 * S18/S19 attempt hosted the pool freeze on a per-flow-tick chance, so most water SETTLED before a roll landed and
 * then never ticked again -- "the water is not freezing" (TEST 108/109/110, live). {@code tickPrecipitation},
 * however, keeps visiting a column at random every tick regardless of whether its water still ticks. So S20 moves
 * ALL at-rest freezing here: this sweep freezes SETTLED + LANDED flowing blocks -- CERTAIN, no dice -- bottom-up,
 * one-or-two per column per pass, and a pool freezes over seconds ring by ring as its cells come to rest.
 * Still-SPREADING water (a pending fluid tick, under EITHER water key) is NEVER swept -- it is left to the flow
 * tick (falls run free; the ice-touch hunter locks reroutes) until it settles, then the sweep claims it.
 *
 * <p><b>S17(b) UPWARD SCAN -- why the downward descent alone was not enough (S18 bottom-up).</b> An open-air
 * cascade FREE-FALLS above the terrain surface, i.e. ABOVE the {@code MOTION_BLOCKING} heightmap top (water is
 * not motion-blocking terrain), so walking DOWNWARD from that top never reaches the falling column -- the exact
 * two-flight root cause. The FLOW TICK ({@code FlowingFluidWaterfallFreezeMixin}) freezes moving water once it
 * LANDS; this mixin adds the complementary net for STANDING fall columns that already reached equilibrium and
 * stopped re-ticking: a belt of {@link PolarWaterFreezeRule#WATERFALL_UPWARD_SCAN_BLOCKS} blocks scanned UPWARD
 * from the surface. Per the S18 landed law it freezes only the LOWEST unfrozen LANDED
 * ({@link PolarWaterFreezeRule#landedOnSupport}) flowing block -- ONE per column per pass -- so a standing column
 * ices from the ground UP over successive ticks (the ice laid this pass is the support the block above lands on
 * next pass) instead of snapping frozen; ice layers from earlier passes are skipped, a genuine solid block caps
 * the scan. Every belt block is above the surface, hence far above the surface-40 freeze floor, so
 * {@code aboveFreezeFloor} is true by construction -- the frozen ice is always above the B-9 deep-cave reservoir.
 * The DOWNWARD roofed descent likewise now freezes only LANDED flowing blocks, so a covered cascade climbs the
 * same way. Bounded, O(1)-ish per tick.
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
 * a LANDED FLOWING water block that is SETTLED (no pending fluid tick) freezes to plain {@code ice} -- CERTAIN, no
 * dice (the S20 {@link PolarWaterFreezeRule#sweepFreezesSettled} decision + the {@link PolarWaterFreezeRule#freezesFlowing}
 * eligibility, barrens-family flag, ocean-EXEMPT, same frayed front) -- and the descent CONTINUES to claim the rest
 * of the cascade in reach. A still-SPREADING flowing block (a pending fluid tick) is skipped, so the sweep never
 * touches water the flow tick still owns. The UPWARD SCAN is identical: it freezes the LOWEST landed+settled
 * flowing block per pass, bottom-up. So a roofed/settled pond freezes ring by ring on the tickPrecipitation
 * cadence; open source water is left entirely to vanilla (byte-identical); the deep cave reservoir below the reach
 * stays liquid (B-9), and the ocean's under-ice liquid is never touched (the sacred pin -- {@code freezesFlowing}
 * checks ocean FIRST).
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
        // S20 FREEZE recorder heartbeat (default off, static-final gate -> dead-code when unset). tickPrecipitation
        // runs weather-independently for random polar columns every tick, so this is the reliable ~5s-window driver:
        // it flushes the accumulated flow-tick + sweep counts even when no water is present in this column.
        if (PolarInstrument.FREEZE) {
            String line = PolarInstrument.pollFreezeLine(self.getGameTime());
            if (line != null) {
                GlobeMod.LOGGER.info(line);
            }
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

        // S17(b) UPWARD SCAN, S18 BOTTOM-UP: an open-air cascade free-falls ABOVE the MOTION_BLOCKING top (water
        // is not motion-blocking terrain), so the downward roofed descent below never reaches it. Scan a bounded
        // belt UPWARD from the surface and freeze the LOWEST unfrozen LANDED (supported below) flowing block --
        // exactly ONE per column per pass -- so a standing fall column ices from the ground UP over successive
        // ticks instead of snapping frozen all at once (the S18 climb: the ice laid this pass is the support the
        // block above lands on next pass). Frozen ice from earlier passes is skipped (it is the growing pile, not
        // a cap); a genuine solid (non-ice, non-fluid) block caps the cascade and ends the scan.
        int ceilY = Math.min(self.getMaxY(), top.getY() + PolarWaterFreezeRule.WATERFALL_UPWARD_SCAN_BLOCKS);
        BlockPos.MutableBlockPos up = new BlockPos.MutableBlockPos(top.getX(), 0, top.getZ());
        BlockPos.MutableBlockPos belowUp = new BlockPos.MutableBlockPos(top.getX(), 0, top.getZ());
        for (int y = top.getY() + 1; y <= ceilY; y++) {
            up.setY(y);
            BlockState above = self.getBlockState(up);
            FluidState fs = above.getFluidState();
            if (fs.getType() == Fluids.FLOWING_WATER) {
                // S20: only a LANDED (supported below) AND SETTLED (no pending fluid tick) flowing block is swept.
                // Scanning upward, the FIRST such block is the lowest one: freeze exactly it and stop (one per pass
                // -> the climb). A still-FALLING block (air/fluid below) or a still-SPREADING one (pending tick) is
                // left to the flow tick; the sweep claims it next pass once it lands and settles. Certain, no dice.
                belowUp.setY(y - 1);
                BlockState belowState = self.getBlockState(belowUp);
                boolean landed = PolarWaterFreezeRule.landedOnSupport(
                        belowState.isAir(), !belowState.getFluidState().isEmpty());
                if (landed && globe$settled(self, up)) {
                    if (PolarInstrument.FREEZE) {
                        PolarInstrument.freezeSweptSettled();
                    }
                    flowFreezes = globe$resolveFlowFreeze(self, top, absLatDeg, fray, flowFreezes);
                    if (PolarWaterFreezeRule.sweepFreezesSettled(flowFreezes == 1, true, true)) {
                        self.setBlockAndUpdate(up.immutable(), Blocks.ICE.defaultBlockState()); // one frozen layer
                        if (PolarInstrument.FREEZE) {
                            PolarInstrument.freezeSweptFroze();
                        }
                    }
                }
                break; // the lowest flowing block decides this pass (landed+settled -> froze; else nothing this pass)
            } else if (above.is(Blocks.ICE)) {
                continue; // a frozen cascade layer from an earlier pass -- keep climbing to the next flowing block
            } else if (!above.isAir() && fs.isEmpty()) {
                break; // a genuine solid (non-ice, non-fluid) block caps this cascade
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
        // already waived in-zone) and ends the descent (a surface skin). A LANDED, SETTLED FLOWING block freezes
        // to plain ice (S20 -- certain; vanilla's tickPrecipitation never tests it) and the descent CONTINUES to
        // claim the rest. Water/flow sealed deeper than the reach stays liquid (the B-9 reservoir).
        int floorY = Math.max(self.getMinY(), top.getY() - PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(top.getX(), 0, top.getZ());
        BlockPos.MutableBlockPos belowCursor = new BlockPos.MutableBlockPos(top.getX(), 0, top.getZ());
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
                // S20: only a LANDED and SETTLED (no pending fluid tick) flowing block is swept; a still-falling
                // block (air/fluid below) or a still-spreading one (pending tick) is left to run, so a covered
                // cascade also ices from the ground UP over successive passes (only its bottom block is supported
                // and at-rest this pass; the next up lands on that ice and settles next pass). Certain, no dice.
                belowCursor.setY(y - 1);
                BlockState belowState = self.getBlockState(belowCursor);
                boolean landed = PolarWaterFreezeRule.landedOnSupport(
                        belowState.isAir(), !belowState.getFluidState().isEmpty());
                if (landed && globe$settled(self, cursor)) {
                    if (PolarInstrument.FREEZE) {
                        PolarInstrument.freezeSweptSettled();
                    }
                    flowFreezes = globe$resolveFlowFreeze(self, top, absLatDeg, fray, flowFreezes);
                    if (PolarWaterFreezeRule.sweepFreezesSettled(flowFreezes == 1, true, true)) {
                        self.setBlockAndUpdate(cursor.immutable(), Blocks.ICE.defaultBlockState()); // frozen layer
                        if (PolarInstrument.FREEZE) {
                            PolarInstrument.freezeSweptFroze();
                        }
                    }
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

    /**
     * S20 SETTLED gate: is this fluid block at rest -- i.e. has the game stopped scheduling its fluid tick? A
     * still-spreading fluid keeps RESCHEDULING its tick, so a pending tick means "still moving" and the sweep must
     * leave it alone. The 26.2 tick container matches a scheduled tick's fluid type by REFERENCE IDENTITY
     * ({@code ScheduledTick.UNIQUE_TICK_HASH}), and flowing water is scheduled under TWO different singletons
     * depending on the path: {@code Fluids.FLOWING_WATER} via {@code FlowingFluid.spreadTo}, and {@code Fluids.WATER}
     * via {@code LiquidBlock.onPlace} (the water {@code LiquidBlock} holds {@code Fluids.WATER}). So we probe BOTH
     * keys; SETTLED iff NEITHER has a pending tick. This can only ever make the sweep MORE conservative about
     * declaring a block at-rest, so it never freezes actively-spreading water. Both probes are O(1) hash lookups.
     */
    private static boolean globe$settled(ServerLevel self, BlockPos pos) {
        return !self.getFluidTicks().hasScheduledTick(pos, Fluids.FLOWING_WATER)
                && !self.getFluidTicks().hasScheduledTick(pos, Fluids.WATER);
    }
}
