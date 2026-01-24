package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class OverlayProof {
    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        // Draw a fat bar so it's obvious even if text is hard to see.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        int w = client.getWindow().getScaledWidth();
        // Use a high Z-index or ensure render order (mixin does this)
        ctx.fill(0, 0, w, 18, 0xAA000000);
        ctx.drawTextWithShadow(
                client.textRenderer,
                "PROOF OVERLAY LIVE (Globe - Mixin)",
                4, 4,
                0xFFFFFFFF
        );
    }

    private OverlayProof() {}
}
