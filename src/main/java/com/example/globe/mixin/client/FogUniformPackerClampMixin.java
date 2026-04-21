package com.example.globe.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Clamp fog uniform packer floats to identify the effective fog-end slot.
 * Controlled via -Dlatitude.fogClampIndex=N (0-5) and -Dlatitude.fogClampValue=16.0
 * Defaults to no clamp when property is absent.
 */
@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public class FogUniformPackerClampMixin {

    private static final int CLAMP_INDEX = Integer.getInteger("latitude.fogClampIndex", -1);
    private static final float CLAMP_VALUE = Float.parseFloat(System.getProperty("latitude.fogClampValue", "16.0"));
    private static long lastLogMs = 0L;

    @ModifyArgs(
            method = "applyFog",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;method_71110(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            ),
            require = 0
    )
    private void globe$clampFogArgs(Args args) {
        if (CLAMP_INDEX < 0 || CLAMP_INDEX > 5) return;
        if (!FabricLoader.getInstance().isModLoaded("iris")) return;

        // args indices: 0=ByteBuffer buf, 1=int off, 2=Vector4f color, 3..8 = floats
        int floatSlot = 3 + CLAMP_INDEX;
        float original = args.<Float>get(floatSlot);
        float clamped = Math.min(original, CLAMP_VALUE);
        args.set(floatSlot, clamped);

    }
}
