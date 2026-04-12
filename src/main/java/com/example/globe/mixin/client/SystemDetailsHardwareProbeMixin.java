package com.example.globe.mixin.client;

import net.minecraft.util.SystemDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SystemDetails.class)
public class SystemDetailsHardwareProbeMixin {
    private static final boolean LATITUDE_SKIP_SYSTEM_DETAILS_HARDWARE = Boolean.parseBoolean(
            System.getProperty("latitude.skipSystemDetailsHardware", "true"));

    @Inject(method = "addHardwareGroup", at = @At("HEAD"), cancellable = true)
    private void globe$skipHardwareGroupWhenRequested(CallbackInfo ci) {
        if (LATITUDE_SKIP_SYSTEM_DETAILS_HARDWARE) {
            ci.cancel();
        }
    }
}
