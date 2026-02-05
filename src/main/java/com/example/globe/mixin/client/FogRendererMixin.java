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
    private static final ThreadLocal<Boolean> IS_ATMOSPHERIC = ThreadLocal.withInitial(() -> false);
    // Distances in blocks (500m -> 100m)
    private static final float STORM_START = 500.0f;
    private static final float STORM_MAX = 100.0f;

    // Tan sandstorm fog color
    private static final float FOG_R = 0.78f;
    private static final float FOG_G = 0.67f;
    private static final float FOG_B = 0.48f;

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
            float start = 0.0f;
            float end = MathHelper.lerp(t, 220.0f, 18.0f);

            args.set(1, 1); // force alternate fog mode/shape during storm

            // indices: 0=ByteBuffer, 1=int, 2=Vector4f color, 3=envStart, 4=envEnd, 5=renderStart, 6=renderEnd, 7=skyEnd, 8=cloudEnd
            Vector4f color = args.get(2);
            float r = MathHelper.lerp(t, color.x, FOG_R);
            float g = MathHelper.lerp(t, color.y, FOG_G);
            float b = MathHelper.lerp(t, color.z, FOG_B);
            color.set(r, g, b, 1.0f);
            args.set(2, color);

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
