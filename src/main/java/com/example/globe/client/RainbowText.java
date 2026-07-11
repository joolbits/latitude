package com.example.globe.client;

import com.example.globe.core.ui.FlowingGradient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws text as a smooth, time-flowing rainbow gradient (one hue per non-space letter, neighbors blending).
 * Used to make the
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

    /** Live 0xRRGGBB (no alpha) gradient color for the {@code visibleIdx}-th visible letter of a
     *  {@code visibleCount}-letter string, drifting through the wheel over {@code cycleSeconds}. The single
     *  shared entry point every rainbow renderer in the mod calls, so they all match. Callers OR in alpha. */
    public static int flowingColor(int visibleIdx, int visibleCount, float cycleSeconds) {
        return FlowingGradient.colorFor(System.currentTimeMillis(), visibleIdx, visibleCount, cycleSeconds);
    }

    private static int visibleLetterCount(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') n++;
        }
        return n;
    }

    /** Draws {@code text} centered at (centerX, centerY) as a smooth flowing rainbow gradient. */
    public static void drawCentered(GuiGraphicsExtractor ctx, Font font, String text, int centerX, int centerY, boolean shadow) {
        drawCentered(ctx, font, text, centerX, centerY, shadow, 0xFF);
    }

    /** Same as {@link #drawCentered(GuiGraphicsExtractor, Font, String, int, int, boolean)}, but with an extra
     *  alpha byte (0-255) applied to every gradient color -- lets callers with a fade-in/out effect (e.g. the
     *  zone-enter title) use the Rainbow treatment without losing their fade. */
    public static void drawCentered(GuiGraphicsExtractor ctx, Font font, String text, int centerX, int centerY, boolean shadow, int alpha) {
        int totalWidth = font.width(text);
        int x = centerX - totalWidth / 2;
        int y = centerY - font.lineHeight / 2;
        int alphaMask = (alpha & 0xFF) << 24;
        int visibleCount = visibleLetterCount(text);
        int colorIdx = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String s = String.valueOf(c);
            if (c != ' ') {
                int color = alphaMask | flowingColor(colorIdx, visibleCount, FlowingGradient.DEFAULT_CYCLE_SECONDS);
                ctx.text(font, s, x, y, color, shadow);
                colorIdx++;
            }
            x += font.width(s);
        }
    }
}
