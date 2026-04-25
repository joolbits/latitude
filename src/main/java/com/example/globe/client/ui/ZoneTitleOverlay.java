package com.example.globe.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ZoneTitleOverlay {
    private static Component title;
    private static Component subtitle;
    private static long startWorldTime = Long.MIN_VALUE;

    private static final int FADE_IN_TICKS = 10;
    private static final int HOLD_TICKS = 40;
    private static final int FADE_OUT_TICKS = 20;
    private static final int TOTAL_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

    private ZoneTitleOverlay() {
    }

    public static void show(Component title, Component subtitle) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        ZoneTitleOverlay.title = title;
        ZoneTitleOverlay.subtitle = subtitle;
        ZoneTitleOverlay.startWorldTime = client.level.getGameTime();
    }

    public static void render(GuiGraphicsExtractor ctx, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null) {
            return;
        }
        if (title == null || startWorldTime == Long.MIN_VALUE) {
            return;
        }

        float age = (float) (client.level.getGameTime() - startWorldTime) + tickDelta;
        if (age < 0.0f) {
            return;
        }
        if (age >= TOTAL_TICKS) {
            title = null;
            subtitle = null;
            startWorldTime = Long.MIN_VALUE;
            return;
        }

        float alpha;
        if (age < FADE_IN_TICKS) {
            alpha = age / (float) FADE_IN_TICKS;
        } else if (age < (FADE_IN_TICKS + HOLD_TICKS)) {
            alpha = 1.0f;
        } else {
            float t = (age - (FADE_IN_TICKS + HOLD_TICKS)) / (float) FADE_OUT_TICKS;
            alpha = 1.0f - t;
        }

        if (alpha <= 0.001f) {
            return;
        }

        if (alpha > 1.0f) alpha = 1.0f;
        if (alpha < 0.0f) alpha = 0.0f;

        int a = (int) (alpha * 255.0f);
        int argb = (a << 24) | 0xFFFFFF;

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        Font tr = client.font;

        int baseY = screenH / 2 - 24;

        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(screenW / 2, baseY);
            m.scale(2.0f, 2.0f);
            ctx.centeredText(tr, title, 0, 0, argb);
        } finally {
            m.popMatrix();
        }

        if (subtitle != null) {
            int subY = baseY + (tr.lineHeight * 2) + 6;
            ctx.centeredText(tr, subtitle, screenW / 2, subY, argb);
        }
    }
}
