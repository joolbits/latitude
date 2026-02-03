package com.example.globe.client;

import net.minecraft.client.gui.DrawContext;

public final class EwHazeOverlay {
    private EwHazeOverlay() {}

    public static void render(DrawContext ctx, float tickDelta) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return;

        float a = GlobeClientState.ewIntensity01(client.player.getX());
        if (a <= 0.0f) return;

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        // Layer A: global sandstorm sepia tint (subtle -> strong)
        float baseAlpha = Math.min(0.55f, 0.08f + a * 0.47f);
        int base = argb(baseAlpha, 120, 92, 55);
        ctx.fill(0, 0, w, h, base);

        // Layer B: sky “storm front” (top-heavy)
        float skyAlpha = Math.min(0.42f, a * 0.42f);
        int sky = argb(skyAlpha, 170, 130, 70);
        ctx.fill(0, 0, w, h / 2, sky);

        // Layer C: mild vignette (edges only, keeps center readable)
        float edgeAlpha = Math.min(0.30f, a * 0.30f);
        int edge = argb(edgeAlpha, 25, 18, 10);

        int insetX = (int)(w * 0.06);
        int insetY = (int)(h * 0.06);

        ctx.fill(0, 0, w, insetY, edge);
        ctx.fill(0, h - insetY, w, h, edge);
        ctx.fill(0, insetY, insetX, h - insetY, edge);
        ctx.fill(w - insetX, insetY, w, h - insetY, edge);
    }

    private static int argb(float alpha, int r, int g, int b) {
        int a = (int)(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
