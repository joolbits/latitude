package com.example.globe.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/**
 * Latitude's fog presentation, split into a distance pass and a colour pass.
 *
 * <p>On 26.2 both lived in a single {@code FogRenderer.setupFog} injection, because that method
 * returned the {@link FogData} and the colour hung off it. On this target {@code setupFog} returns
 * the colour {@link Vector4f} instead, and it has <em>already uploaded that colour to the fog UBO</em>
 * by the time it returns — so mutating its return value would compile and silently do nothing.
 *
 * <p>The two passes therefore hook different points of the same method, and both must keep the
 * same gate:
 * <ul>
 *   <li>distances — {@code FogRenderer.setupFog}, immediately after vanilla's last
 *       {@code renderDistanceEnd} write and before the six fields are read into the UBO. Applying
 *       them any earlier (as an {@code AtmosphericFogEnvironment.setupFog} hook once did) lets
 *       vanilla clobber {@code renderDistanceStart}/{@code End} three instructions later, which
 *       silently removes the far fog wall while the near fog still works.</li>
 *   <li>colour — {@code FogRenderer.computeFogColor}, which runs before the buffer upload</li>
 * </ul>
 *
 * <p>Every distance write here is a {@code tighten} or {@code Math.min}, so the pass can only
 * narrow fog. That is what makes it safe to run after vanilla and outside the atmospheric
 * environment (e.g. under Blindness) without overriding a vanilla effect.
 */
public final class LatitudeFogPresentation {

    private LatitudeFogPresentation() {
    }

    /** Non-null only when Latitude should be modifying fog this frame. */
    private record Gate(Minecraft client, GlobeClientState.Eval eval) {
    }

    private static Gate gate(Camera camera) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) {
            return null;
        }
        // Submerged fog is owned by the water/lava environments; leave it alone.
        if (camera.getFluidInCamera() != FogType.NONE) {
            return null;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return null;
        }
        GlobeClientState.Eval eval = GlobeClientState.evaluate(client);
        if (!eval.active()) {
            return null;
        }
        return new Gate(client, eval);
    }

    /** E/W sandstorm and polar fog distance tightening. */
    public static void applyDistances(FogData fog, Camera camera) {
        Gate gate = gate(camera);
        if (gate == null) {
            return;
        }

        applyEwDistances(fog, gate.client().player.getX());
        applyPolarDistances(fog, gate.client().player.getZ(), gate.eval());
    }

    /** E/W sandstorm and polar fog colour blending, in that order, as on 26.2. */
    public static void applyColor(Vector4f color, Camera camera, ClientLevel level) {
        Gate gate = gate(camera);
        if (gate == null || color == null) {
            return;
        }

        double x = gate.client().player.getX();
        double z = gate.client().player.getZ();

        float sandIntensity = EwPresentationPolicy.sandHazeColorIntensity(
                GlobeClientState.distanceToEwBorderBlocks(x),
                GlobeClientState.ewPresentationVisibility(),
                GlobeClientState.absoluteLatitudeDegrees(level.getWorldBorder(), z));
        if (sandIntensity > 0.0f) {
            blendSandHazeColor(color, sandIntensity);
        }

        if (!gate.eval().surfaceOk()) {
            return;
        }
        float polarIntensity = GlobeClientState.computePoleFogIntensity(z);
        if (polarIntensity > 0.0f) {
            blendPolarFogColor(color, polarIntensity);
        }
    }

    private static void applyEwDistances(FogData fog, double x) {
        fog.environmentalStart = tightenStart(fog.environmentalStart, x);
        fog.renderDistanceStart = tightenStart(fog.renderDistanceStart, x);
        fog.environmentalEnd = tightenEnd(fog.environmentalEnd, x);
        fog.renderDistanceEnd = tightenEnd(fog.renderDistanceEnd, x);
        fog.skyEnd = tightenEnd(fog.skyEnd, x);
        fog.cloudEnd = tightenEnd(fog.cloudEnd, x);
    }

    private static void applyPolarDistances(FogData fog, double z, GlobeClientState.Eval eval) {
        if (!eval.surfaceOk()) {
            return;
        }
        float polarIntensity = GlobeClientState.computePoleFogIntensity(z);
        if (polarIntensity <= 0.0f) {
            return;
        }

        float originalEnvironmentalStart = fog.environmentalStart;
        float originalRenderStart = fog.renderDistanceStart;

        fog.environmentalEnd = polarEnd(fog.environmentalEnd, z);
        fog.renderDistanceEnd = polarEnd(fog.renderDistanceEnd, z);
        fog.skyEnd = polarEnd(fog.skyEnd, z);
        fog.cloudEnd = polarEnd(fog.cloudEnd, z);

        fog.environmentalStart = Math.min(
                fog.environmentalStart,
                PolarPresentationPolicy.polarFogStart(originalEnvironmentalStart, polarIntensity));
        fog.renderDistanceStart = Math.min(
                fog.renderDistanceStart,
                PolarPresentationPolicy.polarFogStart(originalRenderStart, polarIntensity));
    }

    private static float tightenEnd(float currentEnd, double x) {
        double desiredEnd = GlobeClientState.computeEwFogEnd(x, currentEnd);
        if (desiredEnd < 0.0) {
            return currentEnd;
        }
        return (float) Math.min(currentEnd, desiredEnd);
    }

    private static float tightenStart(float currentStart, double x) {
        return Math.min(currentStart, GlobeClientState.computeEwFogStart(x, currentStart));
    }

    private static float polarEnd(float currentEnd, double z) {
        float polarEnd = GlobeClientState.computePoleFogEnd(z, currentEnd);
        if (polarEnd < 0.0f) {
            return currentEnd;
        }
        return Math.min(currentEnd, polarEnd);
    }

    private static void blendSandHazeColor(Vector4f color, float intensity) {
        color.set(
                EwPresentationPolicy.blendFogColorChannel(
                        color.x(), EwPresentationPolicy.SAND_HAZE_TARGET_RED, intensity),
                EwPresentationPolicy.blendFogColorChannel(
                        color.y(), EwPresentationPolicy.SAND_HAZE_TARGET_GREEN, intensity),
                EwPresentationPolicy.blendFogColorChannel(
                        color.z(), EwPresentationPolicy.SAND_HAZE_TARGET_BLUE, intensity),
                color.w());
    }

    private static void blendPolarFogColor(Vector4f color, float intensity) {
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
