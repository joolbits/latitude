package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeClientState;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class DownloadingTerrainScreenFirstLoadMessageMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void globe$clearFirstLoadFlag(CallbackInfo ci) {
        if (!((Object) this instanceof DownloadingTerrainScreen)) {
            return;
        }
        if (LatitudeClientState.isLatitudeWorldLoading()) {
            return;
        }
        LatitudeClientState.firstWorldLoad = false;
        LatitudeClientState.firstWorldLoadStartMs = 0L;
    }
}
