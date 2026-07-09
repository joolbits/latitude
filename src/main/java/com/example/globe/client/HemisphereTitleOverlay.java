package com.example.globe.client;

import com.example.globe.core.HemisphereCrossing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Phase 5 Slice B-3c -- the HEMISPHERE-TITLE channel. Distinct from {@link ZoneEnterTitleOverlay}
 * (the zone-enter title, which has its own draggable HUD-Studio placement): this is ONE shared slot
 * for the two hemisphere crossings, so the 0deg,0deg intersection can never render two competing
 * titles. It holds up to two lines -- a North/South line (equator crossing) and an East/West line
 * (prime-meridian crossing) -- on a single shared display window. If only one axis is active it draws
 * a single-line title; if both are active within the window it draws ONE stacked two-line title,
 * N/S first then E/W ({@link HemisphereCrossing#composeLines}).
 *
 * <p>Visual style/casing come from {@link ZoneEnterTitleOverlay#drawTitleLineAt} so a hemisphere title
 * reads exactly like a zone title. Position is a FIXED offset ABOVE screen center ({@link #ANCHOR_OFFSET_Y})
 * -- independent of the zone title's draggable offset -- so the two channels stay visually separated by
 * construction even in the (rare) case both are showing at once.
 */
public final class HemisphereTitleOverlay {

    /** Fixed vertical anchor for the hemisphere block, relative to screen center. Negative = above
     *  center, clear of the zone-enter title (which anchors at center + its own offset). */
    private static final int ANCHOR_OFFSET_Y = -40;
    private static final int FADE_TICKS = 10;

    private static String northSouthLine;
    private static String eastWestLine;
    private static long startWorldTime = Long.MIN_VALUE;
    private static long endWorldTime = Long.MIN_VALUE;
    private static float scale = 1.8f;

    private HemisphereTitleOverlay() {
    }

    /** Clears any in-flight hemisphere title (disconnect / world change), mirroring
     *  {@link ZoneEnterTitleOverlay#reset()} -- world-time keys are per-world. */
    public static void reset() {
        northSouthLine = null;
        eastWestLine = null;
        startWorldTime = Long.MIN_VALUE;
        endWorldTime = Long.MIN_VALUE;
    }

    /**
     * Set (or refresh) one axis's line and (re)start the shared display window. If a title is already
     * showing (e.g. the equator line from a moment ago) and the other axis now fires, both lines share
     * the refreshed window and render stacked -- this is the mechanism behind the 0deg,0deg stack rule.
     *
     * @param eastWestAxis true = the E/W (prime-meridian) line; false = the N/S (equator) line
     */
    public static void trigger(boolean eastWestAxis, String titleText, int durationTicks, double titleScale) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        long now = client.level.getGameTime();
        // If the window has lapsed, this is a fresh title: clear any stale sibling line and fade in anew.
        if (now >= endWorldTime) {
            northSouthLine = null;
            eastWestLine = null;
            startWorldTime = now;
        }
        int dt = Math.max(1, durationTicks);
        endWorldTime = now + dt;
        scale = (float) titleScale;
        if (eastWestAxis) {
            eastWestLine = titleText;
        } else {
            northSouthLine = titleText;
        }
    }

    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return false;
        }
        if (northSouthLine == null && eastWestLine == null) {
            return false;
        }
        long now = client.level.getGameTime();
        return now >= startWorldTime && now < endWorldTime;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null) {
            return;
        }
        if (northSouthLine == null && eastWestLine == null) {
            return;
        }
        long now = client.level.getGameTime();
        if (now < startWorldTime || now >= endWorldTime) {
            return;
        }

        float alpha = 1.0f;
        long age = now - startWorldTime;
        long remaining = endWorldTime - now;
        if (age < FADE_TICKS) {
            alpha = (float) age / (float) FADE_TICKS;
        } else if (remaining < FADE_TICKS) {
            alpha = (float) remaining / (float) FADE_TICKS;
        }
        if (alpha <= 0.001f) {
            return;
        }
        int alphaByte = (int) (alpha * 255.0f);

        String[] lines = HemisphereCrossing.composeLines(northSouthLine, eastWestLine);
        if (lines.length == 0) {
            return;
        }

        Font font = client.font;
        int cx = screenW / 2;
        int anchorY = (screenH / 2) + ANCHOR_OFFSET_Y;
        // One line-height (scaled) between stacked lines; block is vertically centered on the anchor.
        int lineGap = Math.round(font.lineHeight * scale);
        int firstY = anchorY - (lineGap * (lines.length - 1)) / 2;
        for (int i = 0; i < lines.length; i++) {
            ZoneEnterTitleOverlay.drawTitleLineAt(ctx, cx, firstY + i * lineGap, lines[i], scale, alphaByte);
        }
    }
}
