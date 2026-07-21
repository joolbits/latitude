package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.client.EwPresentationPolicy;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererEwMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void latitude$applyFog(
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
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }

        GlobeClientState.Eval eval = GlobeClientState.evaluate(client);
        if (!eval.active()) {
            return;
        }

        FogData fog = cir.getReturnValue();
        double absoluteLatitude = GlobeClientState.absoluteLatitudeDegrees(
                level.getWorldBorder(),
                client.player.getZ());
        latitude$applyEwFog(fog, client.player.getX(), absoluteLatitude);
        latitude$applyPolarFog(fog, client, eval);
    }

    @Unique
    private static void latitude$applyEwFog(FogData fog, double x, double absoluteLatitude) {
        fog.environmentalStart = latitude$tightenStart(fog.environmentalStart, x);
        fog.renderDistanceStart = latitude$tightenStart(fog.renderDistanceStart, x);
        fog.environmentalEnd = latitude$tightenEnd(fog.environmentalEnd, x);
        fog.renderDistanceEnd = latitude$tightenEnd(fog.renderDistanceEnd, x);
        fog.skyEnd = latitude$tightenEnd(fog.skyEnd, x);
        fog.cloudEnd = latitude$tightenEnd(fog.cloudEnd, x);

        float colorIntensity = EwPresentationPolicy.sandHazeColorIntensity(
                GlobeClientState.distanceToEwBorderBlocks(x),
                GlobeClientState.ewPresentationVisibility(),
                absoluteLatitude);
        if (colorIntensity > 0.0f) {
            latitude$blendSandHazeColor(fog.color, colorIntensity);
        }
    }

    @Unique
    private static void latitude$blendSandHazeColor(Vector4f color, float intensity) {
        color.set(
                EwPresentationPolicy.blendFogColorChannel(
                        color.x(), EwPresentationPolicy.SAND_HAZE_TARGET_RED, intensity),
                EwPresentationPolicy.blendFogColorChannel(
                        color.y(), EwPresentationPolicy.SAND_HAZE_TARGET_GREEN, intensity),
                EwPresentationPolicy.blendFogColorChannel(
                        color.z(), EwPresentationPolicy.SAND_HAZE_TARGET_BLUE, intensity),
                color.w());
    }

    @Unique
    private static float latitude$tightenEnd(float currentEnd, double x) {
        double desiredEnd = GlobeClientState.computeEwFogEnd(x, currentEnd);
        if (desiredEnd < 0.0) return currentEnd;

        return (float) Math.min(currentEnd, desiredEnd);
    }

    @Unique
    private static float latitude$tightenStart(float currentStart, double x) {
        return Math.min(currentStart, GlobeClientState.computeEwFogStart(x, currentStart));
    }

    @Unique
    private static void latitude$applyPolarFog(
            FogData fog,
            Minecraft client,
            GlobeClientState.Eval eval) {
        if (!eval.surfaceOk()) {
            return;
        }

        double z = client.player.getZ();
        float polarIntensity = GlobeClientState.computePoleFogIntensity(z);
        if (polarIntensity <= 0.0f) {
            return;
        }

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
