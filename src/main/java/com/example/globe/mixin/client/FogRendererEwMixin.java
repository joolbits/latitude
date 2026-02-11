package com.example.globe.mixin.client;

import com.example.globe.GlobeMod;
import com.example.globe.client.GlobeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public class FogRendererEwMixin {

    @Unique
    private static long latitude$lastLogMs = 0L;

    // Primary attempt: fogStart ordinal=0, fogEnd ordinal=1.
    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 0, require = 0)
    private static float latitude$ewFogStart(float fogStart) {
        latitude$logHit("start", fogStart, fogStart);
        return latitude$tightenStart(fogStart);
    }

    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 1, require = 0)
    private static float latitude$ewFogEnd(float fogEnd) {
        latitude$logHit("end", fogEnd, fogEnd);
        return latitude$tightenEnd(fogEnd);
    }

    @Unique
    private static float latitude$tightenEnd(float currentEnd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return currentEnd;

        double x = mc.player.getX();
        double i = GlobeClientState.ewIntensity01(x);
        if (i <= 0.0) return currentEnd;

        double desiredEnd = GlobeClientState.computeEwFogEnd(x);
        if (desiredEnd < 0.0) return currentEnd;

        return (float) Math.min(currentEnd, desiredEnd);
    }

    @Unique
    private static void latitude$logHit(String phase, float input, float output) {
        long now = System.currentTimeMillis();
        if (now - latitude$lastLogMs <= 1000L) return;
        latitude$lastLogMs = now;

        MinecraftClient mc = MinecraftClient.getInstance();
        double x = mc != null && mc.player != null ? mc.player.getX() : Double.NaN;
        double i = mc != null && mc.player != null ? GlobeClientState.ewIntensity01(x) : -1.0;
        double desiredEnd = mc != null && mc.player != null ? GlobeClientState.computeEwFogEnd(x) : -1.0;

        GlobeMod.LOGGER.info("[LAT][EW_FOG] phase={} camX={} i={} desiredEnd={} in={} out={}", phase, x, i, desiredEnd, input, output);
    }

    @Unique
    private static float latitude$tightenStart(float currentStart) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return currentStart;

        double x = mc.player.getX();
        double i = GlobeClientState.ewIntensity01(x);
        if (i <= 0.0) return currentStart;

        // Mild push so start moves forward with intensity; end tightening does the heavy lift.
        return (float) (currentStart + (currentStart * (i * 0.25)));
    }
}
