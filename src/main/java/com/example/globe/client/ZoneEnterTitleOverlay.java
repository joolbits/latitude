package com.example.globe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ZoneEnterTitleOverlay {
    private static Component title;
    private static long startWorldTime = Long.MIN_VALUE;
    private static long endWorldTime = Long.MIN_VALUE;
    private static float scale = 1.8f;

    private static final int FADE_TICKS = 10;

    private ZoneEnterTitleOverlay() {
    }

    public static void trigger(String titleText, int durationTicks, double scale) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }

        int dt = Math.max(1, durationTicks);
        float s = (float) scale;

        ZoneEnterTitleOverlay.title = Component.literal(titleText);
        ZoneEnterTitleOverlay.scale = s;
        ZoneEnterTitleOverlay.startWorldTime = client.level.getGameTime();
        ZoneEnterTitleOverlay.endWorldTime = ZoneEnterTitleOverlay.startWorldTime + dt;
    }

    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || title == null) {
            return false;
        }

        long now = client.level.getGameTime();
        return now >= startWorldTime && now < endWorldTime;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || title == null) {
            return;
        }

        long now = client.level.getGameTime();
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

        Font tr = client.font;
        int cx = (screenW / 2) + LatitudeConfig.zoneEnterTitleOffsetX;
        int cy = (screenH / 2) + LatitudeConfig.zoneEnterTitleOffsetY;

        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(cx, cy);
            m.scale(scale, scale);
            ctx.centeredText(tr, title, 0, 0, argb);
        } finally {
            m.popMatrix();
        }
    }

    public static void renderStatic(GuiGraphicsExtractor ctx, int screenW, int screenH, String titleText, double scale) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        renderStaticAt(ctx, cx, cy, titleText, scale);
    }

    public static void renderStaticAt(GuiGraphicsExtractor ctx, int screenW, int screenH, String text, double scale, int offsetX, int offsetY) {
        int cx = (screenW / 2) + offsetX;
        int cy = (screenH / 2) + offsetY;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }

        Font tr = client.font;
        int argb = 0xFFFFFFFF;

        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(cx, cy);
            float s = (float) scale;
            m.scale(s, s);

            int w = tr.width(text);
            int x = -w / 2;
            int y = -tr.lineHeight / 2;
            ctx.text(tr, text, x, y, argb, true);
        } finally {
            m.popMatrix();
        }
    }

    public static void renderStaticAt(GuiGraphicsExtractor ctx, int cx, int cy, String titleText, double scale) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }

        Font tr = client.font;
        int argb = 0xFFFFFFFF;

        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(cx, cy);
            float s = (float) scale;
            m.scale(s, s);
            ctx.centeredText(tr, Component.literal(titleText), 0, 0, argb);
        } finally {
            m.popMatrix();
        }
    }
}
