package com.example.globe.mixin.client;

import com.example.globe.GlobeMod;
import com.example.globe.client.GlobeClientState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.util.math.MathHelper;

@Mixin(value = FogRenderer.class, priority = 2000)
public class FogRendererMixin {
    private static final boolean ENABLE_EW_FOG_TWEAKS = false;
    private static final ThreadLocal<Boolean> IS_ATMOSPHERIC = ThreadLocal.withInitial(() -> false);
    // Distances in blocks (500m -> 100m)
    private static final float STORM_START = 500.0f;
    private static final float STORM_MAX = 100.0f;

    private static int DEBUG_FOG_HITS = 0;
    private static boolean LOGGED_ARGS_ONCE = false;

    @Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;", at = @At("HEAD"))
    private static void globe$ewFog_markAtmospheric(Camera camera, int viewDistance, RenderTickCounter tickCounter, float tickDelta, ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        IS_ATMOSPHERIC.set(camera.getSubmersionType() == CameraSubmersionType.NONE || camera.getSubmersionType() == CameraSubmersionType.ATMOSPHERIC);
    }

    @Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;", at = @At("RETURN"))
    private static void globe$ewFog_clearAtmospheric(CallbackInfoReturnable<Vector4f> cir) {
        IS_ATMOSPHERIC.set(false);
    }
    @ModifyArgs(
            method = "applyFog",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            )
    )
    private void globe$ewFog_modifyFogArgs(Args args, Camera camera, int viewDistance, RenderTickCounter tickCounter, float tickDelta, ClientWorld world) {
        if (!ENABLE_EW_FOG_TWEAKS) return;
        if (!GlobeClientState.DEBUG_EW_FOG) return;

        if (!LOGGED_ARGS_ONCE) {
            LOGGED_ARGS_ONCE = true;
            for (int i = 0; i < args.size(); i++) {
                Object o = args.get(i);
                GlobeMod.LOGGER.info("[Latitude] applyFogInternal arg[{}] = {}", i, (o == null ? "null" : o.getClass().getName()));
                GlobeMod.LOGGER.info("[Latitude] applyFogInternal val[{}] = {}", i, o);
            }
        }

        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;

        float dist = (float) GlobeClientState.getDistanceToNearestEWBorder();
        if (Float.isNaN(dist)) return;

        float t = 0.0f;
        if (dist < STORM_START) {
            float clamped = Math.max(dist, STORM_MAX);
            t = (STORM_START - clamped) / (STORM_START - STORM_MAX);
            t = MathHelper.clamp(t, 0.0f, 1.0f);
        }

        if (t > 0.0f) {
            float start = MathHelper.lerp(t, 96.0f, 64.0f);
            float end = MathHelper.lerp(t, 256.0f, 192.0f);

            // indices: 0=ByteBuffer, 1=int, 2=Vector4f color, 3=envStart, 4=envEnd, 5=renderStart, 6=renderEnd, 7=skyEnd, 8=cloudEnd
            args.set(3, start);
            args.set(4, end);
            args.set(5, start);
            args.set(6, end);
        }

        if ((++DEBUG_FOG_HITS % 60) == 0) {
            GlobeMod.LOGGER.info("[Latitude] fog mixin hit: {}", DEBUG_FOG_HITS);
        }
    }
}
