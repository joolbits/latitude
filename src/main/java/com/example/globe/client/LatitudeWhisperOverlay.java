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
     * BOTTOM-ANCHORED offset (owner flight TEST 113, 2026-07-19): the whisper draws at
     * {@code y = screenH - 82}, i.e. anchored to the BOTTOM of the screen, near the health bar / hotbar
     * cluster where the owner asked the zone murmurs to live (S21a: "whispers closer to the health bar, same
     * area as the polar warnings").
     *
     * <p>HISTORY / why this replaced the center anchor: the previous anchor was {@code screenH/2 + 42} --
     * CENTER-relative (a lower-third read from the 2026-07-11 creative-director pass, see
     * docs/binder/loading-text-and-whisper-review-20260711.md Part 2). When S21a rerouted the zone titles into
     * this channel, its comment claimed the whisper already sat "near the health bar" -- that claim was WRONG
     * (center + 42px is mid-screen on any tall window), which is exactly what the owner saw on TEST 113. The
     * fix is a true bottom anchor: 82px up from the bottom edge sits ~14px ABOVE the polar hazard-warning band
     * (which draws at {@code screenH - 68} and stays the element closest to the hotbar), so a boundary murmur
     * and a hazard warning stack cleanly -- whisper one line above the warning, NO suppression of either.
     */
    private static final int BOTTOM_OFFSET_Y = 82;
    /** Hard top floor for the whisper line on absurdly small windows (matches the warning band's own
     *  {@code warnY < 18} floor in GlobeWarningOverlay, so the two floors agree). */
    private static final int MIN_Y = 18;

    // Whisper keyline (Peetsa TEST 83 "blurry!! outline it in black"): the warnings' near-black
    // (GlobeWarningOverlay.POLE_KEYLINE_RGB sibling) + the standard 1px 8-offset ring.
    private static final int WHISPER_KEYLINE_RGB = 0x080609;
    private static final int[][] KEYLINE_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1,  0},          {1,  0},
            {-1,  1}, {0,  1}, {1,  1},
    };

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
        // TEST 113: bottom-anchored near the hotbar/health cluster (one line above the screenH-68 warning
        // band), floored so a tiny window can't push the line off the top.
        int y = Math.max(screenH - BOTTOM_OFFSET_Y, MIN_Y);
        // Peetsa (TEST 83): "the whisper message is blurry!! outline it in black." Same disease the
        // warnings had — the 5-arg text() defaults dropShadow=TRUE, so the faded italic line dragged a
        // misregistered shadow smear. Same cure: 1px 8-offset near-black keyline (italic is safe to keep
        // on the stamps — unlike bold it neither double-draws nor widens advances; the literal carries no
        // style COLOR, so the dark keyline color is honored), then the fill with shadow OFF.
        int keyArgb = (alphaByte << 24) | (WHISPER_KEYLINE_RGB & 0x00FFFFFF);
        for (int[] off : KEYLINE_OFFSETS) {
            ctx.text(font, line, x + off[0], y + off[1], keyArgb, false);
        }
        ctx.text(font, line, x, y, color, false);
    }
}
