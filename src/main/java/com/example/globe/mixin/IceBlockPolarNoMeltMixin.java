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
        // Equatorward of the frayable strip ice melts normally (vanilla); NaN degrades to vanilla too.
        if (Double.isNaN(absLatDeg)
                || absLatDeg < PolarWaterFreezeRule.FREEZE_ALL_DEG - PolarWaterFreezeRule.FRAY_HALF_WIDTH_DEG) {
            return;
        }
        // Cancel the melt exactly on the front the ice formed on (barrens-fray branch flag-gated, mirroring the
        // freeze consumers), so the no-melt boundary tracks the freeze boundary per column.
        boolean cancelled;
        if (LatitudeV2Flags.POLAR_BARRENS_ENABLED && PolarWaterFreezeRule.inFreezeFrayBand(absLatDeg)) {
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
