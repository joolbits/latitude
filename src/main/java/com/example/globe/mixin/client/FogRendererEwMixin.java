package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeFogPresentation;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both halves of Latitude's fog: the E/W sandstorm walls and the polar closing-in.
 *
 * <p>1.21.1's {@code FogRenderer} is static and holds no shareable fog record: {@code setupFog}
 * builds a package-private holder and pushes {@code start}/{@code end}/{@code shape} into
 * {@code RenderSystem} as its last act, and {@code setupColor} does the same for the colour. There
 * is therefore nothing to capture with {@code @Local} and nothing whose return value could be
 * blended — both passes hook {@code TAIL} and re-apply through {@code RenderSystem}, which is the
 * only state the shaders actually read.
 *
 * <p>{@code setupFog} runs once per {@code FogMode} per frame, so the sky and terrain ranges each
 * receive the tightening on their own call. Every write in the distance pass is a {@code tighten}
 * or a {@code Math.min}, so re-applying after vanilla can only narrow fog, never undo a vanilla
 * effect. Submerged cameras are excluded by the presentation gate itself, not by the hook site.
 */
@Mixin(value = FogRenderer.class, priority = 900)
public class FogRendererEwMixin {

    // GitHub #7 rule: fail soft -- a missed target costs the storm/polar fog distance tightening,
    // never a crash.
    @Inject(method = "setupFog", at = @At("TAIL"), require = 0, expect = 1)
    private static void latitude$applyFogDistances(
            Camera camera,
            FogRenderer.FogMode fogMode,
            float renderDistance,
            boolean isFoggy,
            float partialTick,
            CallbackInfo ci) {
        LatitudeFogPresentation.applyDistances(camera);
    }

    // GitHub #7 rule: fail soft -- a missed target costs the storm/polar fog tint, never a
    // crash. The E/W warning overlay and particles remain as the gameplay signal.
    @Inject(method = "setupColor", at = @At("TAIL"), require = 0, expect = 1)
    private static void latitude$applyFogColor(
            Camera camera,
            float partialTick,
            ClientLevel level,
            int renderDistanceChunks,
            float darkenAmount,
            CallbackInfo ci) {
        LatitudeFogPresentation.applyColor(camera, level);
    }
}
