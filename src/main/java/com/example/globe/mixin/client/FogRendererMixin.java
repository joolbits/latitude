package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.GlobeMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector4f;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    private static float globe$smoothedEdgeEnd = -1.0f;
    private static float globe$smoothedPoleEnd = -1.0f;

    private static final int EW_FOG_WARN_DISTANCE = GlobeMod.POLE_WARNING_DISTANCE_BLOCKS;
    private static final int EW_FOG_DANGER_DISTANCE = GlobeMod.POLE_LETHAL_DISTANCE_BLOCKS;
    private static final int EW_FOG_BLACKOUT_DISTANCE = 20;

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private float globe$clampEnvironmentalEnd(float environmentalEnd) {
        float clamped = globe$computeFogEndClamp(environmentalEnd);
        return clamped;
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3
    )
    private float globe$clampRenderDistanceEnd(float renderDistanceEnd) {
        float clamped = globe$computeFogEndClamp(renderDistanceEnd);
        return clamped;
    }

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private static void globe$mixFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness,
                                         CallbackInfoReturnable<Vector4f> cir) {
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }

        float w = GlobeClientState.computePoleWhiteoutFactor(client.player.getZ());
        w = Math.max(0.0f, Math.min(1.0f, w));
        if (w <= 0.001f) {
            return;
        }

        Vector4f c = cir.getReturnValue();
        float r = c.x();
        float g = c.y();
        float b = c.z();
        float a = c.w();

        r = r + (1.0f - r) * w;
        g = g + (1.0f - g) * w;
        b = b + (1.0f - b) * w;

        cir.setReturnValue(new Vector4f(r, g, b, a));
    }

    private float globe$computeFogEndClamp(float currentEnd) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return currentEnd;
        }

        if (!client.world.getRegistryKey().getValue().equals(World.OVERWORLD.getValue())) {
            globe$resetSmoothing();
            return currentEnd;
        }

        if (!GlobeClientState.isGlobeWorld()) {
            globe$resetSmoothing();
            return currentEnd;
        }

        float poleDesired = GlobeClientState.computePoleFogEnd(client.player.getZ());

        float edgeDesired = GlobeClientState.computeEwFogEnd(client.player.getX());

        float poleEnd = -1.0f;
        if (poleDesired >= 0.0f) {
            globe$smoothedPoleEnd = globe$smoothToward(globe$smoothedPoleEnd, poleDesired, 0.18f);
            poleEnd = globe$smoothedPoleEnd;
        } else {
            globe$smoothedPoleEnd = -1.0f;
        }

        float edgeEnd = -1.0f;
        if (edgeDesired >= 0.0f) {
            globe$smoothedEdgeEnd = globe$smoothToward(globe$smoothedEdgeEnd, edgeDesired, 0.18f);
            edgeEnd = globe$smoothedEdgeEnd;
        } else {
            globe$smoothedEdgeEnd = -1.0f;
        }

        float clamped = currentEnd;
        if (poleEnd >= 0.0f) {
            clamped = Math.min(clamped, poleEnd);
        }
        if (edgeEnd >= 0.0f) {
            clamped = Math.min(clamped, edgeEnd);
        }

        return globe$sanitizeFogEnd(currentEnd, clamped);
    }

    private static float globe$smoothToward(float prev, float target, float alpha) {
        if (prev < 0.0f) return target;
        return prev + (target - prev) * alpha;
    }

    private static float globe$sanitizeFogEnd(float currentEnd, float clamped) {
        if (Float.isNaN(clamped) || Float.isInfinite(clamped)) return currentEnd;

        float safe = clamped;
        safe = Math.min(safe, currentEnd);
        safe = Math.max(2.0f, safe);
        if (safe < 0.0f) safe = currentEnd;
        return safe;
    }

    private static void globe$resetSmoothing() {
        globe$smoothedEdgeEnd = -1.0f;
        globe$smoothedPoleEnd = -1.0f;
    }
}
