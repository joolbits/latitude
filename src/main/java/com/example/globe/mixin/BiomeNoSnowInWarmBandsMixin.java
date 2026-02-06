package com.example.globe.mixin;

import com.example.globe.GlobeMod;
import com.example.globe.util.LatitudeMath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeNoSnowInWarmBandsMixin {

    private static boolean globe$isWarmBand(int z) {
        LatitudeMath.LatitudeZone zone = LatitudeMath.zoneForRadius(GlobeMod.BORDER_RADIUS, z);
        return zone == LatitudeMath.LatitudeZone.EQUATOR
                || zone == LatitudeMath.LatitudeZone.TROPICAL
                || zone == LatitudeMath.LatitudeZone.TEMPERATE;
    }

    @Inject(method = "doesNotSnow", at = @At("HEAD"), cancellable = true)
    private void globe$blockSnowInWarmBands(BlockPos pos, int seaLevel, CallbackInfoReturnable<Boolean> cir) {
        if (globe$isWarmBand(pos.getZ())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
    private void globe$forceRainInWarmBands(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (globe$isWarmBand(pos.getZ())) {
            Biome self = (Biome) (Object) this;
            cir.setReturnValue(self.hasPrecipitation() ? Biome.Precipitation.RAIN : Biome.Precipitation.NONE);
        }
    }
}
