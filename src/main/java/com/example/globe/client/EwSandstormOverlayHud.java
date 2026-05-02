package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

public final class EwSandstormOverlayHud {
    private EwSandstormOverlayHud() {}

    // Start fading in at 500 blocks from border; reach “full” at 100 blocks.
    private static final double FADE_START = 500.0;
    private static final double FADE_FULL  = 100.0;

    // Dust tint (tan)
    private static final int DUST_R = 214;
    private static final int DUST_G = 186;
    private static final int DUST_B = 132;
    private static final int HUD_SAFE_BOTTOM_PX = 86;
    private static final int FADE_BANDS = 96;

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;

        double distToBorder = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());

        // Far from border => no overlay
        if (distToBorder >= FADE_START) return;

        // dist=500 => 0, dist=100 => 1
        double t = (FADE_START - distToBorder) / (FADE_START - FADE_FULL);
        float a = (float) MathHelper.clamp(t, 0.0, 1.0);

        a = a * a; // ramps up faster as you approach the border

        if ((net.minecraft.client.MinecraftClient.getInstance().world.getTime() % 40L) == 0L) {
            com.example.globe.GlobeMod.LOGGER.info("[Latitude] EW haze tick: x={} a={}", mc.player.getX(), a);
        }

        float baseAlpha = Math.min(0.90f, 0.10f + a * 0.80f);
        int alpha = (int) (baseAlpha * 255.0f);
        if (alpha <= 0) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int clearBottomStart = Math.max(0, h - HUD_SAFE_BOTTOM_PX);
        if (clearBottomStart <= 0) return;

        for (int i = 0; i < FADE_BANDS; i++) {
            int y0 = i * clearBottomStart / FADE_BANDS;
            int y1 = (i + 1) * clearBottomStart / FADE_BANDS;
            float yNorm = ((y0 + y1) * 0.5f) / (float) clearBottomStart;
            float tFade = MathHelper.clamp((yNorm - 0.55f) / 0.45f, 0.0f, 1.0f);
            float fade = 1.0f - (tFade * tFade * (3.0f - 2.0f * tFade));
            int bandAlpha = (int) (alpha * fade);
            if (bandAlpha <= 0) continue;

            int bandArgb = (bandAlpha << 24) | (DUST_R << 16) | (DUST_G << 8) | DUST_B;
            ctx.fill(0, y0, w, y1, bandArgb);
        }
    }
}
