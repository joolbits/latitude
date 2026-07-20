package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class EwSandstormOverlayHud {
    private EwSandstormOverlayHud() {}

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        // Deliberately empty. Depth fog and particles own the EW storm now; a flat
        // full-screen tan veil erased scene depth and ignored shelter exposure.
    }
}
