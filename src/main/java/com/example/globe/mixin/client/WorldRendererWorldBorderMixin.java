package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public class WorldRendererWorldBorderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void globe$cancelVanillaWorldBorder(WorldBorderRenderState state, Vec3 cameraPos, double viewDistanceBlocks, double farPlaneDistance, CallbackInfo ci) {
        if (!GlobeClientState.DEBUG_EW_SUPPRESS_VANILLA_BORDER) return;
        ci.cancel();
    }
}
