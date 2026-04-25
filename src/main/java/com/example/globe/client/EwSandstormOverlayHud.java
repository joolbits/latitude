package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class EwSandstormOverlayHud {
    private EwSandstormOverlayHud() {}

    // Start fading in at 500 blocks from border; reach “full” at 100 blocks.
    private static final double FADE_START = 500.0;
    private static final double FADE_FULL  = 100.0;

    // Dust tint (tan)
    private static final int DUST_R = 214;
    private static final int DUST_G = 186;
    private static final int DUST_B = 132;

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        double distToBorder = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());

        // Far from border => no overlay
        if (distToBorder >= FADE_START) return;

        // dist=500 => 0, dist=100 => 1
        double t = (FADE_START - distToBorder) / (FADE_START - FADE_FULL);
        float a = (float) Mth.clamp(t, 0.0, 1.0);

        a = a * a; // ramps up faster as you approach the border

        if ((net.minecraft.client.Minecraft.getInstance().level.getGameTime() % 40L) == 0L) {
            com.example.globe.GlobeMod.LOGGER.info("[Latitude] EW haze tick: x={} a={}", mc.player.getX(), a);
        }

        float baseAlpha = Math.min(0.90f, 0.10f + a * 0.80f);
        int alpha = (int) (baseAlpha * 255.0f);
        if (alpha <= 0) return;

        int argb = (alpha << 24) | (DUST_R << 16) | (DUST_G << 8) | (DUST_B);

        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        ctx.fill(0, 0, w, h, argb);
    }
}
