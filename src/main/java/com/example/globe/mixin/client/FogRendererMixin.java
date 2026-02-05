package com.example.globe.mixin.client;

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

@Mixin(value = FogRenderer.class, priority = 2000)
public class FogRendererMixin {
    private static final ThreadLocal<Boolean> IS_ATMOSPHERIC = ThreadLocal.withInitial(() -> false);

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
        if (!GlobeClientState.DEBUG_EW_FOG || !IS_ATMOSPHERIC.get()) return;

        double dist = GlobeClientState.getDistanceToNearestEWBorder();
        if (Double.isNaN(dist) || dist >= 500.0) return;

        float fogStart = (float) Math.max(32.0, dist * 0.5);
        float fogEnd = (float) Math.max(64.0, dist * 0.8);

        // indices: 0=ByteBuffer, 1=int, 2=Vector4f color, 3=envStart, 4=envEnd, 5=renderStart, 6=renderEnd, 7=skyEnd, 8=cloudEnd
        Vector4f color = args.get(2);
        color.set(0.15f, 0.20f, 0.30f, color.w());
        args.set(3, fogStart);
        args.set(4, fogEnd);
        args.set(5, fogStart);
        args.set(6, fogEnd);
    }
}
