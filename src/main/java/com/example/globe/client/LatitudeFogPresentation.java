package com.example.globe.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/**
 * Latitude's fog presentation, split into a distance pass and a colour pass.
 *
 * <p>1.21.1 has no {@code FogData} record of ranges: {@code FogRenderer.setupFog} keeps a
 * package-private holder with a single {@code start}/{@code end} pair and pushes it straight into
 * {@code RenderSystem.setShaderFogStart}/{@code setShaderFogEnd} at the end of the method, and
 * {@code FogRenderer.setupColor} does the same for the colour. Both passes therefore read the
 * render-system state back and write the tightened values into it, rather than mutating a shared
 * object the way the 1.21.9+ lines do.
 *
 * <p>Range fidelity is reduced by the target, not by choice: the newer lines carry four separate
 * ranges (environmental, render-distance, sky, cloud) and Latitude narrows each. 1.21.1 exposes one
 * range per {@code FogMode}, so the same tightening is applied to that single pair on every
 * {@code setupFog} call — the sky and terrain passes each get it in their own turn.
 *
 * <p>Every distance write here is a {@code tighten} or {@code Math.min}, so the pass can only
 * narrow fog. That is what makes it safe to run after vanilla without overriding a vanilla effect.
 * Submerged cameras are excluded by the presentation gate itself.
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

    /** E/W sandstorm and polar fog distance tightening, applied to the active shader fog range. */
    public static void applyDistances(Camera camera) {
        Gate gate = gate(camera);
        if (gate == null) {
            return;
        }

        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();

        // E/W first, then polar -- the same order the 1.21.9+ lines apply them in.
        float ewStart = tightenStart(start, gate.client().player.getX());
        float ewEnd = tightenEnd(end, gate.client().player.getX());

        float finalStart = ewStart;
        float finalEnd = ewEnd;
        if (gate.eval().surfaceOk()) {
            float polarIntensity = GlobeClientState.computePoleFogIntensity(gate.client().player.getZ());
            if (polarIntensity > 0.0f) {
                finalEnd = polarEnd(ewEnd, gate.client().player.getZ());
                finalStart = Math.min(
                        ewStart,
                        PolarPresentationPolicy.polarFogStart(ewStart, polarIntensity));
            }
        }

        if (finalStart != start) {
            RenderSystem.setShaderFogStart(finalStart);
        }
        if (finalEnd != end) {
            RenderSystem.setShaderFogEnd(finalEnd);
        }
    }

    /** Reads the uploaded fog colour back, blends Latitude's tints into it, and writes it out. */
    public static void applyColor(Camera camera, ClientLevel level) {
        float[] uploaded = RenderSystem.getShaderFogColor();
        if (uploaded == null || uploaded.length < 4) {
            return;
        }
        Vector4f color = new Vector4f(uploaded[0], uploaded[1], uploaded[2], uploaded[3]);
        applyColor(color, camera, level);
        RenderSystem.setShaderFogColor(color.x(), color.y(), color.z(), color.w());
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
