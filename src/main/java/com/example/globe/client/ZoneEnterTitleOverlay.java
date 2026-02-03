package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class ZoneEnterTitleOverlay {
    private static Text title;
    private static long startWorldTime = Long.MIN_VALUE;
    private static long endWorldTime = Long.MIN_VALUE;
    private static float scale = 1.8f;

    private static final int FADE_TICKS = 10;

    private ZoneEnterTitleOverlay() {
    }

    public static void trigger(String titleText, int durationTicks, double scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        int dt = Math.max(1, durationTicks);
        float s = (float) scale;

        ZoneEnterTitleOverlay.title = Text.literal(titleText);
        ZoneEnterTitleOverlay.scale = s;
        ZoneEnterTitleOverlay.startWorldTime = client.world.getTime();
        ZoneEnterTitleOverlay.endWorldTime = ZoneEnterTitleOverlay.startWorldTime + dt;
    }

    public static void render(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || title == null) {
            return;
        }

        long now = client.world.getTime();
        if (now < startWorldTime || now >= endWorldTime) {
            return;
        }

        float alpha = 1.0f;
        long age = now - startWorldTime;
        long remaining = endWorldTime - now;

        if (age < FADE_TICKS) {
            alpha = (float) age / (float) FADE_TICKS;
        } else if (remaining < FADE_TICKS) {
            alpha = (float) remaining / (float) FADE_TICKS;
        }

        if (alpha <= 0.001f) {
            return;
        }

        int a = (int) (alpha * 255.0f);
        int argb = (a << 24) | 0xFFFFFF;

        TextRenderer tr = client.textRenderer;
        int cx = (screenW / 2) + LatitudeConfig.zoneEnterTitleOffsetX;
        int cy = (screenH / 2) + LatitudeConfig.zoneEnterTitleOffsetY;

        var m = ctx.getMatrices();
        m.push();
        try {
            m.translate((float) cx, (float) cy, 0.0f);
            m.scale(scale, scale, 1.0f);
            ctx.drawCenteredTextWithShadow(tr, title, 0, 0, argb);
        } finally {
            m.pop();
        }
    }

    public static void renderStatic(DrawContext ctx, int screenW, int screenH, String titleText, double scale) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        renderStaticAt(ctx, cx, cy, titleText, scale);
    }

    public static void renderStaticAt(DrawContext ctx, int screenW, int screenH, String text, double scale, int offsetX, int offsetY) {
        int cx = (screenW / 2) + offsetX;
        int cy = (screenH / 2) + offsetY;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        TextRenderer tr = client.textRenderer;
        int argb = 0xFFFFFFFF;

        var m = ctx.getMatrices();
        m.push();
        try {
            m.translate((float) cx, (float) cy, 0.0f);
            float s = (float) scale;
            m.scale(s, s, 1.0f);

            int w = tr.getWidth(text);
            int x = -w / 2;
            int y = -tr.fontHeight / 2;
            ctx.drawText(tr, text, x, y, argb, true);
        } finally {
            m.pop();
        }
    }

    public static void renderStaticAt(DrawContext ctx, int cx, int cy, String titleText, double scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        TextRenderer tr = client.textRenderer;
        int argb = 0xFFFFFFFF;

        var m = ctx.getMatrices();
        m.push();
        try {
            m.translate((float) cx, (float) cy, 0.0f);
            float s = (float) scale;
            m.scale(s, s, 1.0f);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(titleText), 0, 0, argb);
        } finally {
            m.pop();
        }
    }
}
