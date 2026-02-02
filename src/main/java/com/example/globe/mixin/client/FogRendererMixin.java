package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private float globe$clampEnvironmentalEnd(float environmentalEnd) {
        return globe$clampFogEnd(environmentalEnd);
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3
    )
    private float globe$clampRenderDistanceEnd(float renderDistanceEnd) {
        return globe$clampFogEnd(renderDistanceEnd);
    }

    private float globe$clampFogEnd(float currentEnd) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return currentEnd;
        }

        if (!client.world.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) {
            return currentEnd;
        }

        if (!GlobeClientState.isGlobeWorld()) {
            return currentEnd;
        }

        float ewEnd = GlobeClientState.computeEwFogEnd(client.player.getX());
        float poleEnd = GlobeClientState.computePoleFogEnd(client.player.getZ());
        float targetEnd = ewEnd;
        if (poleEnd > 0.0f && (targetEnd <= 0.0f || poleEnd < targetEnd)) {
            targetEnd = poleEnd;
        }

        if (targetEnd <= 0.0f) {
            return currentEnd;
        }

        float clamped = Math.min(currentEnd, targetEnd);
        GlobeClientState.debugLogEwFogOncePerSec("FOG_RENDERER", targetEnd, client.player.getX());
        return globe$sanitizeFogEnd(currentEnd, clamped);
    }

    private static float globe$sanitizeFogEnd(float currentEnd, float clamped) {
        if (Float.isNaN(clamped) || Float.isInfinite(clamped)) return currentEnd;

        float safe = clamped;
        safe = Math.min(safe, currentEnd);
        safe = Math.max(2.0f, safe);
        if (safe < 0.0f) safe = currentEnd;
        return safe;
    }
}
