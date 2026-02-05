package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import net.minecraft.client.render.WorldBorderRendering;
import net.minecraft.client.render.state.WorldBorderRenderState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRendering.class)
public class WorldRendererWorldBorderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void globe$cancelVanillaWorldBorder(WorldBorderRenderState state, Vec3d cameraPos, double viewDistanceBlocks, double farPlaneDistance, CallbackInfo ci) {
        if (!GlobeClientState.DEBUG_EW_SUPPRESS_VANILLA_BORDER) return;
        if (Math.random() < 0.01) {
            System.out.println("Suppressing Vanilla Border...");
        }
        ci.cancel();
    }
}
