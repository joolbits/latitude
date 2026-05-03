package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
public final class EwSandstormOverlayHud {
    private EwSandstormOverlayHud() {}

    // Start fading in at 500 blocks from border; reach “full” at 100 blocks.
    private static final double FADE_START = 500.0;
    private static final double FADE_FULL  = 100.0;

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;

        double distToBorder = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());
        if (distToBorder >= FADE_START) return;

        if ((net.minecraft.client.MinecraftClient.getInstance().world.getTime() % 40L) == 0L) {
            com.example.globe.GlobeMod.LOGGER.info("[Latitude] EW haze tick: x={} owner=world-pass", mc.player.getX());
        }
        // 1.20.1 EW density is owned by the world-pass veil renderer.
        // Keep the HUD hook as a readability placeholder without adding a second haze layer.
    }
}
