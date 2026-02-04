package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public class FogRendererEwMixin {

    @Unique private static final boolean LATITUDE_SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

    // Primary attempt: fogStart ordinal=0, fogEnd ordinal=1.
    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 0, require = 0)
    private static float latitude$ewFogStart(float fogStart) {
        if (LATITUDE_SODIUM_LOADED) return fogStart; // render-distance clamp handles Sodium
        return latitude$tightenStart(fogStart);
    }

    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 1, require = 0)
    private static float latitude$ewFogEnd(float fogEnd) {
        if (LATITUDE_SODIUM_LOADED) return fogEnd; // render-distance clamp handles Sodium
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
