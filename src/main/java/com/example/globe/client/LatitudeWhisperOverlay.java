package com.example.globe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * B-4 round 3 item 5 -- the "whisper" channel for LINGER re-announcements (a zone edge or a hemisphere line
 * re-crossed while still lingering near the boundary just crossed). Replaces the old vanilla action-bar
 * message ({@code Hud.setOverlayMessage}), which popped in at stark full-opacity white and only faded on the
 * way out. This is a small, ITALIC, TRANSLUCENT line (alpha capped well below full) with a symmetric
 * fade-IN + hold + fade-OUT, so a re-announcement reads as an unobtrusive murmur rather than a shout.
 *
 * <p>Rendered on the HUD layer via {@code InGameHudMixin} (so it hides under F1 with the rest of the HUD),
 * on its own world-time window -- the same fade idiom as {@link ZoneEnterTitleOverlay} /
 * {@link HemisphereTitleOverlay}. The trigger sites (the linger branches in {@code GlobeWarningOverlay}) are
 * unchanged; only the renderer swapped.
 */
public final class LatitudeWhisperOverlay {

    private static final int FADE_TICKS = 8;
    private static final int DEFAULT_DURATION_TICKS = 50; // ~2.5 s, fade-in + hold + fade-out
    /** Never full opacity -- a whisper, not a shout. Peak alpha cap (~70%). */
    private static final float MAX_ALPHA = 0.70f;
    /** Vertical offset below screen center; unobtrusive, clear of the center-screen titles above it. */
    private static final int ANCHOR_OFFSET_Y = 34;

    private static String text;
    private static long startWorldTime = Long.MIN_VALUE;
    private static long endWorldTime = Long.MIN_VALUE;

    private LatitudeWhisperOverlay() {
    }

    /** Clears any in-flight whisper (disconnect / world change) -- world-time keys are per-world. */
    public static void reset() {
        text = null;
        startWorldTime = Long.MIN_VALUE;
        endWorldTime = Long.MIN_VALUE;
    }

    /** Show a whisper line, (re)starting the shared fade window. */
    public static void trigger(String line) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || line == null) {
            return;
        }
        text = line;
        startWorldTime = client.level.getGameTime();
        endWorldTime = startWorldTime + DEFAULT_DURATION_TICKS;
    }

    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || text == null) {
            return false;
        }
        long now = client.level.getGameTime();
        return now >= startWorldTime && now < endWorldTime;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null || text == null) {
            return;
        }
        long now = client.level.getGameTime();
        if (now < startWorldTime || now >= endWorldTime) {
            return;
        }

        // Symmetric fade-in / hold / fade-out.
        float alpha = 1.0f;
        long age = now - startWorldTime;
        long remaining = endWorldTime - now;
        if (age < FADE_TICKS) {
            alpha = (float) age / (float) FADE_TICKS;
        } else if (remaining < FADE_TICKS) {
            alpha = (float) remaining / (float) FADE_TICKS;
        }
        alpha *= MAX_ALPHA;
        if (alpha <= 0.004f) {
            return;
        }

        Font font = client.font;
        Component line = Component.literal(text).withStyle(s -> s.withItalic(true));
        int alphaByte = (int) (alpha * 255.0f) & 0xFF;
        int color = (alphaByte << 24) | 0x00FFFFFF; // translucent white
        int w = font.width(line);
        int x = Math.max(2, (screenW - w) / 2);
        int y = (screenH / 2) + ANCHOR_OFFSET_Y;
        ctx.text(font, line, x, y, color);
    }
}
