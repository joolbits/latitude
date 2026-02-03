package com.example.globe.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class ZoneTitleOverlay {
    private static Text title;
    private static Text subtitle;
    private static long startWorldTime = Long.MIN_VALUE;

    private static final int FADE_IN_TICKS = 10;
    private static final int HOLD_TICKS = 40;
    private static final int FADE_OUT_TICKS = 20;
    private static final int TOTAL_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

    private ZoneTitleOverlay() {
    }

    public static void show(Text title, Text subtitle) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        ZoneTitleOverlay.title = title;
        ZoneTitleOverlay.subtitle = subtitle;
        ZoneTitleOverlay.startWorldTime = client.world.getTime();
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.textRenderer == null) {
            return;
        }
        if (title == null || startWorldTime == Long.MIN_VALUE) {
            return;
        }

        float age = (float) (client.world.getTime() - startWorldTime) + tickDelta;
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

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        TextRenderer tr = client.textRenderer;

        int baseY = screenH / 2 - 24;

        var m = ctx.getMatrices();
        m.push();
        try {
            m.translate((float) (screenW / 2), (float) baseY, 0.0f);
            m.scale(2.0f, 2.0f, 1.0f);
            ctx.drawCenteredTextWithShadow(tr, title, 0, 0, argb);
        } finally {
            m.pop();
        }

        if (subtitle != null) {
            int subY = baseY + (tr.fontHeight * 2) + 6;
            ctx.drawCenteredTextWithShadow(tr, subtitle, screenW / 2, subY, argb);
        }
    }
}
