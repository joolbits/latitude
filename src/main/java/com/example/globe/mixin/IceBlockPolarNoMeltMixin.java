package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S15(b) PERSISTENT ICE, part 2 -- NO MELT. In the polar full-freeze zone, plain ice never melts, so a torch
 * cannot thaw the pole ("Ice needs to be a little more persistent"). Sibling of the SKY WAIVER
 * ({@code ServerLevelRoofedWaterFreezeMixin}) and the temperature-veto waiver ({@code BiomePolarWaterFreezeMixin}).
 *
 * <p><b>The melt path.</b> Plain {@code IceBlock.randomTick} melts the block whenever block light exceeds
 * {@code 11 - opacity} (a torch beside a frozen pond re-opens water). This is the ONLY light-driven thaw path
 * for plain ice, and it is the whole body of {@code randomTick}, so cancelling {@code randomTick} at its head
 * exactly cancels the melt and nothing else.
 *
 * <p><b>Scope.</b> Gated to armed globe worlds with the kill switch on, and only in the forced-freeze zone --
 * the SAME {@code >= 85} frayed front the ice formed on (selected exactly like the freeze consumers), so a
 * column that froze (frayed) also will not thaw (frayed), and ice melts normally equatorward of the front
 * (vanilla). Off-path is byte-identical: non-globe / flag-off / equatorward columns bail before any cancel.
 *
 * <p><b>Plain ice only.</b> {@code @Mixin(IceBlock.class)} targets {@code IceBlock.randomTick}; packed and blue
 * ice are plain {@code Block}s with no melt tick, and frosted ice (frost-walker) melts via its OWN scheduled
 * neighbour-count tick, not this random tick -- none of those are disturbed.
 */
@Mixin(IceBlock.class)
public abstract class IceBlockPolarNoMeltMixin {

    @Inject(
            method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void globe$noPolarIceMelt(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                      CallbackInfo ci) {
        // Byte-identical fast bail: non-globe world or the kill switch off -> vanilla melt, no latitude math.
        if (!LatitudeV2Flags.POLAR_WATER_FREEZE_ENABLED || LatitudeBiomes.getActiveRadiusBlocks() <= 0) {
            return;
        }
        double absLatDeg = LatitudeMath.absLatDegExact(level.getWorldBorder(), pos.getZ());
        // Equatorward of BOTH fronts ice melts normally (vanilla); NaN degrades to vanilla too. The cheap
        // bail must use the WIDEST front the tick machinery can freeze on: with the barrens on, land ice now
        // forms from the 80-deg tick front (TEST 114 sweep REQUIRED-FIX at 82, re-anchored by S25's 80->82
        // ramp — tick ice must not torch-melt into a melt/re-freeze flicker against the 1.6 s sweep), so the
        // bail floor rides TICK_FRONT_ONSET_DEG and follows it automatically.
        double bailFloorDeg = LatitudeV2Flags.POLAR_BARRENS_ENABLED
                ? PolarWaterFreezeRule.TICK_FRONT_ONSET_DEG
                : PolarWaterFreezeRule.FREEZE_ALL_DEG - PolarWaterFreezeRule.FRAY_HALF_WIDTH_DEG;
        if (Double.isNaN(absLatDeg) || absLatDeg < bailFloorDeg) {
            return;
        }
        // Cancel the melt exactly on the front the ice formed on, so the no-melt boundary tracks the freeze
        // boundary per column: NON-OCEAN columns with the barrens on ride the SAME S25 80->82 tick-front ramp
        // (the same tickFrontFreezes the freeze consumers use — "where ice forms, ice does not melt" holds on
        // ONE front; the fray sample is cheap enough here that no skip-at-full branch is taken); ocean columns
        // and barrens-off keep the razor/frayed 85 law (the approved sea-ice line), exactly mirroring
        // BiomePolarWaterFreezeMixin's branch structure.
        boolean cancelled;
        if (LatitudeV2Flags.POLAR_BARRENS_ENABLED
                && !level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_OCEAN)) {
            cancelled = PolarWaterFreezeRule.tickFrontFreezes(true, absLatDeg,
                    LatitudeBiomes.polarBarrensFrayNoise(pos.getX(), pos.getZ()));
        } else if (LatitudeV2Flags.POLAR_BARRENS_ENABLED && PolarWaterFreezeRule.inFreezeFrayBand(absLatDeg)) {
            cancelled = PolarWaterFreezeRule.iceMeltCancelledFrayed(true, absLatDeg,
                    LatitudeBiomes.polarSeaFreezeFrayNoise(pos.getX(), pos.getZ()));
        } else {
            cancelled = PolarWaterFreezeRule.iceMeltCancelled(true, absLatDeg);
        }
        if (cancelled) {
            ci.cancel(); // a torch cannot thaw the pole
        }
    }
}
