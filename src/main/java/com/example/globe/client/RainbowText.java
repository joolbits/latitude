package com.example.globe.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws text with each non-space letter in a different color from a fixed rainbow palette. Used to make the
 * "HUD Studio" entry (Latitude Settings screen + the world-creation screen's Rules panel) pop visually against
 * the plain gray option buttons around it.
 *
 * <p>Vanilla {@code Button} forces its own label to white and its render pipeline is sealed in 26.2
 * ({@code extractWidgetRenderState} is final, {@code extractContents} package-private), so there is no way to
 * recolor a button's own text. The pattern instead: give the button a blank {@code Component} message, then
 * call {@link #drawCentered} after the button renders to draw the real lettering on top.
 */
public final class RainbowText {
    private RainbowText() {
    }

    // ROYGBIV-ish, tuned to stay legible against this mod's dark panel backgrounds (no near-black indigo).
    private static final int[] PALETTE = {
            0xFFFF5555, // red
            0xFFFFAA00, // orange
            0xFFFFFF55, // yellow
            0xFF55FF55, // green
            0xFF55FFFF, // cyan
            0xFF5599FF, // blue
            0xFFFF66FF, // magenta/violet
    };

    /** Draws {@code text} centered at (centerX, centerY), cycling one palette color per non-space character. */
    public static void drawCentered(GuiGraphicsExtractor ctx, Font font, String text, int centerX, int centerY, boolean shadow) {
        drawCentered(ctx, font, text, centerX, centerY, shadow, 0xFF);
    }

    /** Same as {@link #drawCentered(GuiGraphicsExtractor, Font, String, int, int, boolean)}, but with an extra
     *  alpha byte (0-255) applied to every palette color -- lets callers with a fade-in/out effect (e.g. the
     *  zone-enter title) use the Rainbow preset without losing their fade. */
    public static void drawCentered(GuiGraphicsExtractor ctx, Font font, String text, int centerX, int centerY, boolean shadow, int alpha) {
        int totalWidth = font.width(text);
        int x = centerX - totalWidth / 2;
        int y = centerY - font.lineHeight / 2;
        int alphaMask = (alpha & 0xFF) << 24;
        int colorIdx = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String s = String.valueOf(c);
            if (c != ' ') {
                int color = alphaMask | (PALETTE[colorIdx % PALETTE.length] & 0xFFFFFF);
                ctx.text(font, s, x, y, color, shadow);
                colorIdx++;
            }
            x += font.width(s);
        }
    }
}
