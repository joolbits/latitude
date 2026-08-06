package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeFogPresentation;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Colour half of Latitude's fog. Distances are handled by {@link AtmosphericFogEnvironmentMixin}.
 *
 * <p>This deliberately targets {@code computeFogColor} rather than {@code setupFog}. On 26.2 a
 * single {@code setupFog} injection covered both, because it returned the {@code FogData} that
 * carried the colour. Here {@code setupFog} returns the colour itself but has already handed it to
 * {@code updateBuffer} before returning, so blending into its return value would compile cleanly and
 * never reach the screen. {@code computeFogColor} runs before that upload.
 */
@Mixin(value = FogRenderer.class, priority = 900)
public class FogRendererEwMixin {

    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void latitude$applyFogColor(
            Camera camera,
            float partialTick,
            ClientLevel level,
            int viewDistance,
            float darkenAmount,
            CallbackInfoReturnable<Vector4f> cir) {
        // Blended in place: setupFog uploads this same instance.
        LatitudeFogPresentation.applyColor(cir.getReturnValue(), camera, level);
    }
}
