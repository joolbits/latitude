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
 * on its own WALL-CLOCK window -- the same fade idiom as {@link ZoneEnterTitleOverlay} /
 * {@link HemisphereTitleOverlay}. The trigger sites (the linger branches in {@code GlobeWarningOverlay}) are
 * unchanged; only the renderer swapped.
 */
public final class LatitudeWhisperOverlay {

    private static final int FADE_TICKS = 8;
    private static final int DEFAULT_DURATION_TICKS = 50; // ~2.5 s, fade-in + hold + fade-out
    /** Fade ramp length in wall-clock ms (the tick count kept for parity with the old timing). */
    private static final long FADE_MS = FADE_TICKS * 50L;
    /** Whisper lifetime in wall-clock ms (fade-in + hold + fade-out). */
    private static final long DEFAULT_DURATION_MS = DEFAULT_DURATION_TICKS * 50L;
    /** Never full opacity -- a whisper, not a shout. Peak alpha cap (~70%). */
    private static final float MAX_ALPHA = 0.70f;
    /**
     * Vertical offset below screen center; unobtrusive, clear of the center-screen titles above it.
     * Creative-director verdict 2026-07-11 (delegated by Peetsa — "a little too high, lower it"):
     * 34 -> 42, an 8px drop into the lower-third read. HARD FLOOR: do NOT go below 42. The hazard-warning
     * band draws at screenH-68 and the whisper is triggered from that same warning file, so a boundary
     * murmur and a hazard warning can share the screen at a pole; +42 keeps a safe gap above the warning
     * band even on a small (~240px) window, while a larger offset would let the two overlays stack.
     * See docs/binder/loading-text-and-whisper-review-20260711.md Part 2.
     */
    private static final int ANCHOR_OFFSET_Y = 42;

    private static String text;
    // WALL-CLOCK lifecycle (System.currentTimeMillis at trigger + duration in ms), matching
    // ZoneEnterTitleOverlay / HemisphereTitleOverlay. The fade alpha is driven from wall time, NOT
    // client.level.getGameTime(): the game-tick clock stalls and can even snap BACKWARDS during teleport
    // chunk-gen (the integrated ClientLevel free-runs its tick count while the server thread is blocked, then
    // gets corrected to the server's lower authoritative value on the next time sync), which made whispers
    // freeze, pop, and flicker through boundary re-crossings. Wall-clock is strictly monotonic and advances
    // every frame, so the fade stays smooth through a hitch. Cross-world resurrection is handled by reset() on
    // disconnect (wired in GlobeModClient).
    private static long startMs = Long.MIN_VALUE;
    private static long durationMs = 0L;

    private LatitudeWhisperOverlay() {
    }

    /** Clears any in-flight whisper (disconnect / world change). On wall-clock timing this is the only
     *  resurrection guard needed (ms never runs backwards, so there is no stale-tick-window race to defend
     *  against). */
    public static void reset() {
        text = null;
        startMs = Long.MIN_VALUE;
        durationMs = 0L;
    }

    /** Show a whisper line, (re)starting the shared fade window. */
    public static void trigger(String line) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || line == null) {
            return;
        }
        text = line;
        startMs = System.currentTimeMillis();
        durationMs = DEFAULT_DURATION_MS;
    }

    public static boolean isActive() {
        if (text == null || startMs == Long.MIN_VALUE) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        return elapsed >= 0L && elapsed < durationMs;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null || text == null
                || startMs == Long.MIN_VALUE) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed < 0L || elapsed >= durationMs) {
            return;
        }

        // Symmetric fade-in / hold / fade-out.
        float alpha = 1.0f;
        long remaining = durationMs - elapsed;
        if (elapsed < FADE_MS) {
            alpha = (float) elapsed / (float) FADE_MS;
        } else if (remaining < FADE_MS) {
            alpha = (float) remaining / (float) FADE_MS;
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
