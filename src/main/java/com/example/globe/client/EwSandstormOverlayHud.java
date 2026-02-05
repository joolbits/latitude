package com.example.globe.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

public final class EwSandstormOverlayHud {
    private EwSandstormOverlayHud() {}

    // Start fading in at 500 blocks from border; reach “full” at 100 blocks.
    private static final double FADE_START = 900.0;  // haze begins farther out
    private static final double FADE_FULL  = 250.0;  // reaches strong earlier

    // Dust tint (tan)
    private static final int DUST_R = 214;
    private static final int DUST_G = 186;
    private static final int DUST_B = 132;

    public static void register() {
        HudRenderCallback.EVENT.register(EwSandstormOverlayHud::onHudRender);
    }

    private static void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;

        // IMPORTANT: Replace this with your authoritative border source later.
        // For bringup, keep it literal to avoid wiring errors.
        final double borderX = 3750.0;

        double px = mc.player.getX();
        double distToBorder = borderX - Math.abs(px);

        // Far from border => no overlay
        if (distToBorder >= FADE_START) return;

        // dist=500 => 0, dist=100 => 1
        double t = (FADE_START - distToBorder) / (FADE_START - FADE_FULL);
        float a = (float) MathHelper.clamp(t, 0.0, 1.0);

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // Layer A: global sandstorm sepia tint (subtle -> strong)
        float baseAlpha = Math.min(0.75f, 0.08f + a * 0.67f);
        int base = argb(baseAlpha, 120, 92, 55);
        ctx.fill(0, 0, w, h, base);

        // Layer B: sky “storm front” (top-heavy gradient)
        float skyAlphaTop = Math.min(0.45f, a * 0.45f);
        float skyAlphaBottom = 0.0f;
        fillHorizontalGradient(ctx, 0, 0, w, (int) (h * 0.65f),
                170, 140, 85, skyAlphaTop, skyAlphaBottom, 24);

        // Layer C: smooth vignette (edges only, keeps center readable)
        float edgeAlpha = Math.min(0.28f, a * 0.28f);
        int thickness = (int) (Math.min(w, h) * 0.18f);
        fillEdgeVignette(ctx, w, h, 25, 18, 10, edgeAlpha, thickness, 16);
    }

    private static int argb(float alpha, int r, int g, int b) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void fillHorizontalGradient(DrawContext ctx, int x0, int y0, int x1, int y1,
                                               int r, int g, int b, float alphaTop, float alphaBottom, int steps) {
        int h = Math.max(1, y1 - y0);
        steps = Math.max(1, steps);
        for (int i = 0; i < steps; i++) {
            float t0 = (float) i / (float) steps;
            float t1 = (float) (i + 1) / (float) steps;
            int ya = y0 + Math.round(h * t0);
            int yb = y0 + Math.round(h * t1);
            float a = alphaTop + (alphaBottom - alphaTop) * t0;
            ctx.fill(x0, ya, x1, yb, argb(a, r, g, b));
        }
    }

    private static void fillEdgeVignette(DrawContext ctx, int w, int h, int r, int g, int b, float alpha, int thickness, int steps) {
        // top gradient
        fillHorizontalGradient(ctx, 0, 0, w, thickness, r, g, b, alpha, 0f, steps);
        // bottom gradient
        fillHorizontalGradient(ctx, 0, h - thickness, w, h, r, g, b, 0f, alpha, steps);

        int t = Math.max(1, thickness);
        steps = Math.max(1, steps);
        for (int i = 0; i < steps; i++) {
            float u0 = (float) i / (float) steps;
            float u1 = (float) (i + 1) / (float) steps;
            int xb = Math.round(t * u1);
            float a = alpha + (0f - alpha) * u0;

            // left
            ctx.fill(0, 0, xb, h, argb(a, r, g, b));
            // right
            ctx.fill(w - xb, 0, w, h, argb(a, r, g, b));
        }
    }
}
