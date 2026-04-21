package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class OverlayProof {
    public static void render(GuiGraphics ctx, DeltaTracker tickCounter) {
        // Draw a fat bar so it's obvious even if text is hard to see.
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return;

        int w = client.getWindow().getGuiScaledWidth();
        // Use a high Z-index or ensure render order (mixin does this)
        ctx.fill(0, 0, w, 18, 0xAA000000);
        ctx.drawString(
                client.font,
                "PROOF OVERLAY LIVE (Globe - Mixin)",
                4, 4,
                0xFFFFFFFF
        );
    }

    private OverlayProof() {}
}
