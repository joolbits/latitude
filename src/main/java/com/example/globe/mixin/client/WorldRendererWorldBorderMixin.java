package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public class WorldRendererWorldBorderMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/renderer/state/level/WorldBorderRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/world/phys/Vec3;D)V",
            at = @At("HEAD"),
            cancellable = true)
    private void globe$cancelVanillaWorldBorder(WorldBorderRenderState state,
                                                 RenderPass renderPass,
                                                 Vec3 cameraPos,
                                                 double viewDistanceBlocks,
                                                 CallbackInfo ci) {
        if (!GlobeClientState.DEBUG_EW_SUPPRESS_VANILLA_BORDER) return;
        ci.cancel();
    }
}
