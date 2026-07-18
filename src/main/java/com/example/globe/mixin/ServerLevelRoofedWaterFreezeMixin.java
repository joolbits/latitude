package com.example.globe.mixin;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarWaterFreezeRule;
import com.example.globe.util.LatitudeMath;
import com.example.globe.world.LatitudeBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S15(b) PERSISTENT ICE, part 1 -- the SKY WAIVER. In the polar full-freeze zone, water under a roof
 * freezes too. Server-side; a sibling of {@code BiomePolarWaterFreezeMixin} (which waives the temperature
 * veto inside {@code Biome.shouldFreeze}). Together they make sheltered polar water freeze the way the owner
 * asked ("as soon as there is any shelter, the water is liquid instead of ice").
 *
 * <p><b>Where the sky requirement lives.</b> {@code Biome.shouldFreeze} has no sky check -- the "must see the
 * sky" requirement is implicit in the CALLER. {@code ServerLevel.tickPrecipitation(BlockPos)} freezes only
 * {@code getHeightmapPos(MOTION_BLOCKING, pos).below()}, i.e. the topmost motion-blocking block of the column.
 * When a roof (any motion-blocking block) sits above water, THAT roof is the heightmap top, so the water below
 * it is never tested and never freezes. This mixin reaches under the roof.
 *
 * <p><b>What it does.</b> At the head of {@code tickPrecipitation}, only on an armed globe world with the
 * kill switch on and only in the forced-freeze zone (the SAME {@code >= 85} frayed front the open-water
 * freeze uses, selected exactly like {@code BiomePolarWaterFreezeMixin}): if the block vanilla would test is
 * NOT already open water, descend up to {@link PolarWaterFreezeRule#ROOFED_FREEZE_REACH_BLOCKS} blocks to the
 * topmost water surface and let vanilla's OWN {@code Biome.shouldFreeze} decide (genuine-water / light &lt; 10
 * / edge checks intact, temperature veto already waived in-zone by {@code BiomePolarWaterFreezeMixin}). So a
 * roofed pond freezes on vanilla's normal edge-inward tick cadence; open water is left entirely to vanilla
 * (byte-identical), and the deep cave reservoir below the reach stays liquid (the B-9 reservation).
 *
 * <p><b>Byte-identical off-path.</b> Non-globe worlds and {@code POLAR_WATER_FREEZE_ENABLED == false} bail at
 * the head before any work; columns equatorward of the frayable strip bail after a single latitude read (no
 * heightmap, no descent). This injector never cancels vanilla -- it only ADDS the roofed-water freeze; vanilla's
 * own freeze (on the roof, a no-op) and its snow/precipitation pass run unchanged. Snow still accumulates on the
 * roof, not under it.
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
        boolean inZone;
        if (LatitudeV2Flags.POLAR_BARRENS_ENABLED && PolarWaterFreezeRule.inFreezeFrayBand(absLatDeg)) {
            inZone = PolarWaterFreezeRule.freezeIgnoresSkyFrayed(true, absLatDeg,
                    LatitudeBiomes.polarSeaFreezeFrayNoise(columnPos.getX(), columnPos.getZ()));
        } else {
            inZone = PolarWaterFreezeRule.freezeIgnoresSky(true, absLatDeg);
        }
        if (!inZone) {
            return;
        }
        // Vanilla freezes the MOTION_BLOCKING top-1. If THAT block is already open (source) water, vanilla
        // handles it byte-identically (edge-inward, same cadence) -- leave that path untouched.
        BlockPos top = self.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, columnPos);
        BlockPos surface = top.below();
        if (self.getFluidState(surface).getType() == Fluids.WATER) {
            return;
        }
        // Covered/dry column: reach under the roof for the topmost water surface within shelter reach, then let
        // vanilla's OWN shouldFreeze (genuine-water / light / edge, temperature veto already waived in-zone)
        // freeze it on its normal edge-inward tick cadence. Water sealed deeper than the reach stays liquid.
        int floorY = Math.max(self.getMinY(), top.getY() - PolarWaterFreezeRule.ROOFED_FREEZE_REACH_BLOCKS);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(top.getX(), 0, top.getZ());
        for (int y = surface.getY() - 1; y >= floorY; y--) {
            cursor.setY(y);
            if (self.getFluidState(cursor).getType() == Fluids.WATER) {
                Biome biome = self.getBiome(cursor).value();
                if (biome.shouldFreeze(self, cursor)) {
                    self.setBlockAndUpdate(cursor.immutable(), Blocks.ICE.defaultBlockState());
                }
                return; // topmost water surface handled
            }
        }
    }
}
