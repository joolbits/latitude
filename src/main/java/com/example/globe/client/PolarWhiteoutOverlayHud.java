package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Phase 5 B-3b: the VISIBLE polar whiteout -- a full-screen fill whose alpha ramps CONTINUOUSLY over
 * the ambient window [85,90] deg ({@code PolarHazardWindow.fogIntensity}, read through the existing
 * {@code GlobeClientState.computePoleWhiteoutFactor} screen-fog path), so fog density thickens with
 * the ambient snowfall on the SAME progress ramp. Before B-3 no polar screen haze rendered at all:
 * the stage-based severity plumbing (Eval.polarWhiteoutSeverity, GlobeRegions.polarWhiteoutSeverity)
 * was computed but consumed by nothing -- this class is the ramp's rendering consumer. Mirrors the
 * {@link EwSandstormOverlayHud} precedent: a HUD-layer fill, always-on for globe worlds (atmosphere,
 * not config-gated), rendered from {@code InGameHudMixin} before the hotbar. True volumetric
 * fog-renderer wiring (FogRenderer/computePoleFogEnd) stays OUT -- that is a B-4 decision.
 */
public final class PolarWhiteoutOverlayHud {
    private PolarWhiteoutOverlayHud() {
    }

    // Near-white cool tint -- the same endpoint color the EW cold-band whiteout lerps toward.
    private static final int FOG_R = 238;
    private static final int FOG_G = 242;
    private static final int FOG_B = 248;
    // Heavy but not opaque at 90 deg: the pole reads as a whiteout while the warning text stays legible.
    private static final float MAX_ALPHA = 0.90f;

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.gui.hud.isHidden()) {
            return;
        }

        // Same activation + surface gate as the ambient snow tick (globe world, sky-exposed): the
        // blizzard is a surface phenomenon -- no whiteout deep in a cave under the pole.
        var eval = GlobeClientState.evaluate(mc);
        if (!eval.active() || !eval.surfaceOk()) {
            return;
        }

        // Continuous 85->90 ramp: 0 at/below 85 deg, 1.0 at the pole (both hemispheres via |lat|).
        float intensity = GlobeClientState.computePoleWhiteoutFactor(mc.player.getZ());
        if (intensity <= 0.001f) {
            return;
        }

        // Quadratic ease-in: barely-there at the 85 deg onset, thickening fast across the 87-90 hazard
        // window toward the MAX_ALPHA whiteout at 90 deg.
        float a = intensity * intensity;
        int alpha = (int) (Math.min(MAX_ALPHA, a * MAX_ALPHA) * 255.0f);
        if (alpha <= 0) {
            return;
        }

        int argb = (alpha << 24) | (FOG_R << 16) | (FOG_G << 8) | FOG_B;
        ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), argb);
    }
}
