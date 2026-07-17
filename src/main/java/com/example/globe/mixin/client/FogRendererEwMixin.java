package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.client.PolarPresentationPolicy;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererEwMixin {

    // Primary attempt: fogStart ordinal=0, fogEnd ordinal=1.
    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 0, require = 0)
    private static float latitude$ewFogStart(float fogStart) {
        return latitude$tightenStart(fogStart);
    }

    @ModifyVariable(method = "applyFog", at = @At("STORE"), ordinal = 1, require = 0)
    private static float latitude$ewFogEnd(float fogEnd) {
        return latitude$tightenEnd(fogEnd);
    }

    @Unique
    private static float latitude$tightenEnd(float currentEnd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return currentEnd;

        double x = mc.player.getX();
        double i = GlobeClientState.ewIntensity01(x);
        if (i <= 0.0) return currentEnd;

        double desiredEnd = GlobeClientState.computeEwFogEnd(x);
        if (desiredEnd < 0.0) return currentEnd;

        return (float) Math.min(currentEnd, desiredEnd);
    }

    @Unique
    private static float latitude$tightenStart(float currentStart) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return currentStart;

        double x = mc.player.getX();
        double i = GlobeClientState.ewIntensity01(x);
        if (i <= 0.0) return currentStart;

        // Mild push so start moves forward with intensity; end tightening does the heavy lift.
        return (float) (currentStart + (currentStart * (i * 0.25)));
    }
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void latitude$polarFog(
            Camera camera,
            int viewDistance,
            DeltaTracker tickCounter,
            float tickDelta,
            ClientLevel level,
            CallbackInfoReturnable<FogData> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) {
            return;
        }
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }

        GlobeClientState.Eval eval = GlobeClientState.evaluate(client);
        if (!eval.active() || !eval.surfaceOk()) {
            return;
        }

        double z = client.player.getZ();
        float polarIntensity = GlobeClientState.computePoleFogIntensity(z);
        if (polarIntensity <= 0.0f) {
            return;
        }

        FogData fog = cir.getReturnValue();
        latitude$tightenPolarFogDistances(fog, z, polarIntensity);
        latitude$blendPolarFogColor(fog.color, polarIntensity);
    }

    @Unique
    private static void latitude$tightenPolarFogDistances(FogData fog, double z, float polarIntensity) {
        float originalEnvironmentalStart = fog.environmentalStart;
        float originalRenderStart = fog.renderDistanceStart;

        fog.environmentalEnd = latitude$polarEnd(fog.environmentalEnd, z);
        fog.renderDistanceEnd = latitude$polarEnd(fog.renderDistanceEnd, z);
        fog.skyEnd = latitude$polarEnd(fog.skyEnd, z);
        fog.cloudEnd = latitude$polarEnd(fog.cloudEnd, z);

        fog.environmentalStart = Math.min(
                fog.environmentalStart,
                PolarPresentationPolicy.polarFogStart(originalEnvironmentalStart, polarIntensity));
        fog.renderDistanceStart = Math.min(
                fog.renderDistanceStart,
                PolarPresentationPolicy.polarFogStart(originalRenderStart, polarIntensity));
    }

    @Unique
    private static float latitude$polarEnd(float currentEnd, double z) {
        float polarEnd = GlobeClientState.computePoleFogEnd(z, currentEnd);
        if (polarEnd < 0.0f) {
            return currentEnd;
        }
        return Math.min(currentEnd, polarEnd);
    }

    @Unique
    private static void latitude$blendPolarFogColor(Vector4f color, float intensity) {
        color.set(
                PolarPresentationPolicy.blendFogColorChannel(
                        color.x(), PolarPresentationPolicy.FOG_TARGET_RED, intensity),
                PolarPresentationPolicy.blendFogColorChannel(
                        color.y(), PolarPresentationPolicy.FOG_TARGET_GREEN, intensity),
                PolarPresentationPolicy.blendFogColorChannel(
                        color.z(), PolarPresentationPolicy.FOG_TARGET_BLUE, intensity),
                color.w());
    }
}
