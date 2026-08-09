package com.example.globe.mixin.client;

import com.example.globe.client.LatitudeFogPresentation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Both halves of Latitude's fog: the E/W sandstorm walls and the polar closing-in.
 *
 * <p>On 26.2 a single {@code setupFog} injection covered both, because it returned the
 * {@code FogData} that carried the colour. Here {@code setupFog} returns the colour instead, so the
 * two passes hook different points in the same method.
 *
 * <p><b>Colour</b> targets {@code computeFogColor}, not {@code setupFog}'s return: {@code setupFog}
 * has already handed the colour to {@code updateBuffer} by the time it returns, so blending into its
 * return value would compile cleanly and never reach the screen.
 *
 * <p><b>Distances</b> must land after vanilla's own writes. Disassembling 1.21.11's
 * {@code FogRenderer.setupFog} shows the order is: call {@code FogEnvironment.setupFog} (bci 106),
 * then unconditionally {@code putfield renderDistanceStart} (139) and {@code renderDistanceEnd}
 * (146), then read all six fields into {@code updateBuffer} (186-214). An earlier revision applied
 * distances from {@code AtmosphericFogEnvironment.setupFog}, i.e. at bci 106 — so the two
 * renderDistance writes were clobbered three instructions later and the far fog wall never
 * appeared, while environmentalStart/End and skyEnd/cloudEnd (never re-written) did work. Hooking
 * after the last putfield fixes that, and moving off the environment class also means the pass
 * still runs when vanilla skips the atmospheric environment (Blindness/Darkness). Injecting
 * immediately before {@code updateBuffer} would be too late — its arguments are already on the
 * stack by then.
 *
 * <p>Widening is impossible by construction: every write in the distance pass is a {@code tighten}
 * or a {@code Math.min}, so re-applying after vanilla can only narrow fog, never undo a vanilla
 * effect. Submerged cameras are excluded by the presentation gate itself, not by the hook site.
 */
@Mixin(value = FogRenderer.class, priority = 900)
public class FogRendererEwMixin {

    // GitHub #7 rule: fail soft -- a missed target costs the storm/polar fog distance tightening,
    // never a crash.
    @Inject(
            method = "setupFog",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER),
            require = 0,
            expect = 1)
    private void latitude$applyFogDistances(
            Camera camera,
            int viewDistance,
            DeltaTracker deltaTracker,
            float partialTick,
            ClientLevel level,
            CallbackInfoReturnable<Vector4f> cir,
            @Local FogData fogData) {
        LatitudeFogPresentation.applyDistances(fogData, camera);
    }

    // GitHub #7 rule: fail soft -- a missed target costs the storm/polar fog tint, never a
    // crash. The E/W warning overlay and particles remain as the gameplay signal.
    @Inject(method = "computeFogColor", at = @At("RETURN"), require = 0, expect = 1)
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
