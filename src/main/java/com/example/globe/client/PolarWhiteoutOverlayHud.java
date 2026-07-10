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

    // B-4 stormy cast: the fill lerps from a cold grey-blue STORM tint at low/mid intensity toward the
    // near-white WHITEOUT endpoint at the pole. Because the fill covers the whole screen this also dims
    // the sunny/blue sky Peetsa complained about -- the approach reads as a gathering storm, not clear skies.
    private static final int STORM_R = 92;
    private static final int STORM_G = 108;
    private static final int STORM_B = 132;
    // Near-white cool tint -- the same endpoint color the EW cold-band whiteout lerps toward.
    private static final int WHITE_R = 238;
    private static final int WHITE_G = 242;
    private static final int WHITE_B = 248;
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

        // B-4 fog curve: the old quadratic (intensity^2) was ~invisible until ~88 deg then slammed shut
        // ("fog ramps then WHAM"). A sub-linear ease (intensity^0.65) lifts the low-mid range so the haze
        // is genuinely visible from ~85.5 deg (intensity 0.1 -> ~0.22 of MAX_ALPHA, was ~0.01) and its
        // slope DECREASES toward the pole -- smooth build, no sudden wall. Starts at 0 at 85 deg (no seam).
        float eased = (float) Math.pow(intensity, 0.65);
        int alpha = (int) (Math.min(MAX_ALPHA, eased * MAX_ALPHA) * 255.0f);
        if (alpha <= 0) {
            return;
        }

        // Colour follows the raw 85->90 ramp: grey-blue storm low, white-out high.
        float t = Math.min(1.0f, Math.max(0.0f, intensity));
        int r = lerp(STORM_R, WHITE_R, t);
        int g = lerp(STORM_G, WHITE_G, t);
        int b = lerp(STORM_B, WHITE_B, t);

        int argb = (alpha << 24) | (r << 16) | (g << 8) | b;
        ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), argb);
    }

    private static int lerp(int from, int to, float t) {
        return from + Math.round((to - from) * t);
    }
}
