package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererFogMixin {

    @Inject(method = "applyFog", at = @At("RETURN"), require = 0)
    private static void latitude$applyEwBorderFog(
            Camera camera,
            FogType fogType,
            float viewDistance,
            boolean thickFog,
            float tickDelta,
            CallbackInfo ci
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        ClientWorld world = client.world;
        if (world == null || client.player == null) return;

        // Overworld only
        if (!world.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) return;

        // Only terrain fog; don't fight water/lava fog
        if (fogType != FogType.FOG_TERRAIN) return;

        double camX = camera.getPos().x;

        // Return -1 when no EW fog should apply
        float ewEnd = GlobeClientState.computeEwFogEnd(camX);

        // Optional debug heartbeat (implement as no-op unless enabled)
        GlobeClientState.debugLogEwFogOncePerSec("BackgroundRenderer.applyFog@RETURN", ewEnd, camX);

        if (ewEnd < 0.0f) return;

        float end = Math.max(8.0f, ewEnd);
        float start = Math.max(0.0f, end * 0.35f);

        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }

    @ModifyArg(
            method = "applyFog",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogEnd(F)V"),
            index = 0,
            require = 0
    )
    private static float latitude$modifyFogEnd(float originalEnd) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return originalEnd;
        if (!client.world.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) return originalEnd;

        double x = client.player.getX(); // approximation; RETURN-inject uses true camera X
        float ewEnd = GlobeClientState.computeEwFogEnd(x);
        if (ewEnd < 0.0f) return originalEnd;

        float end = Math.max(8.0f, ewEnd);
        return Math.min(originalEnd, end);
    }
}
