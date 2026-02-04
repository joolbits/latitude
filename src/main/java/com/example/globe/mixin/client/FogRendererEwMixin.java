package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public class FogRendererEwMixin {

    @Unique private static long latitude$lastLogMs = 0L;

    // Attempt A: likely fogStart ordinal=0, fogEnd ordinal=1.
    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 0, require = 0)
    private static float latitude$ewFogStartA(float fogStart) {
        return latitude$tightenStart(fogStart);
    }

    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 1, require = 0)
    private static float latitude$ewFogEndA(float fogEnd) {
        return latitude$tightenEnd(fogEnd);
    }

    // Attempt B: swapped ordinals (in case the locals are ordered differently).
    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 1, require = 0)
    private static float latitude$ewFogStartB(float fogStart) {
        return latitude$tightenStart(fogStart);
    }

    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 0, require = 0)
    private static float latitude$ewFogEndB(float fogEnd) {
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

        float tightened = (float) Math.min(currentEnd, desiredEnd);
        latitude$logOncePerSecond("end", currentEnd, tightened, i);
        return tightened;
    }

    @Unique
    private static float latitude$tightenStart(float currentStart) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return currentStart;

        double x = mc.player.getX();
        double i = GlobeClientState.ewIntensity01(x);
        if (i <= 0.0) return currentStart;

        // Mild push so start moves forward with intensity; end tightening does the heavy lift.
        float pushed = (float) (currentStart + (currentStart * (i * 0.25)));
        latitude$logOncePerSecond("start", currentStart, pushed, i);
        return pushed;
    }

    @Unique
    private static void latitude$logOncePerSecond(String which, float before, float after, double i) {
        long now = System.currentTimeMillis();
        if (now - latitude$lastLogMs < 1000L) return;
        latitude$lastLogMs = now;
        System.out.println("[Latitude] EW fog " + which + " tighten: " + before + " -> " + after + " (i=" + i + ")");
    }
}
