package com.example.globe.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Temporary probe to locate the fog-end float inside FogRenderer's uniform packer.
 * Adjust PROBE_ORDINAL 0..5 until fog end visibly collapses under Sodium+Iris.
 */
@Mixin(net.minecraft.client.render.fog.FogRenderer.class)
public class FogUniformPackerProbeMixin {

    static {
        System.out.println("[Latitude] Fog probe mixin class loaded");
    }

    // Toggle during probing
    private static final boolean ENABLE_PROBE = true;
    private static long globe$lastLogMs = 0L;

    private static void globe$logOncePerSecond(String msg) {
        long now = System.currentTimeMillis();
        if (now - globe$lastLogMs >= 1000L) {
            globe$lastLogMs = now;
            System.out.println(msg);
        }
    }

    @Inject(method = "method_71110", at = @At("HEAD"), remap = false)
    private void globe$fogPackerProbe(ByteBuffer buf, int off, Vector4f color,
                                      float f0, float f1, float f2, float f3, float f4, float f5,
                                      CallbackInfo ci) {
        if (!ENABLE_PROBE) return;
        if (!FabricLoader.getInstance().isModLoaded("iris")) return;

        long now = System.currentTimeMillis();
        if (now - globe$lastLogMs >= 1000L) {
            globe$lastLogMs = now;
            System.out.println("[Latitude FOG PROBE] f0=" + f0 + " f1=" + f1 + " f2=" + f2
                    + " f3=" + f3 + " f4=" + f4 + " f5=" + f5);
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void globe$fogCtor(CallbackInfo ci) {
        System.out.println("[Latitude] FogRenderer ctor hook hit");
    }
}
