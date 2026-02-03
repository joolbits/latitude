package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class EwHazeOverlay {
    private EwHazeOverlay() {}

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return;

        float a = GlobeClientState.ewIntensity01(client.player.getX());
        if (a <= 0.0f) return;

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        // 0.15..0.85 alpha
        float alpha = Math.min(0.85f, 0.15f + a * 0.70f);
        int argb = ((int)(alpha * 255f) << 24); // black with alpha

        ctx.fill(0, 0, w, h, argb);
    }
}
