package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 has no {@code WorldBorderRenderer} and no render-state object: the border wall is drawn by
 * {@code LevelRenderer.renderWorldBorder(Camera)}. That private method is the equivalent hook, and
 * cancelling it suppresses the same wall the newer lines suppress by cancelling the renderer.
 */
@Mixin(LevelRenderer.class)
public class WorldRendererWorldBorderMixin {
    // GitHub #7 rule: fail soft -- a missed target means the vanilla border wall renders
    // alongside Latitude's presentation, never a crash.
    @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private void globe$cancelVanillaWorldBorder(Camera camera, CallbackInfo ci) {
        if (!GlobeClientState.SUPPRESS_VANILLA_EW_BORDER) return;
        ci.cancel();
    }
}
