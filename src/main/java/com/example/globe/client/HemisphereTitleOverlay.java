package com.example.globe.client;

import com.example.globe.core.HemisphereCrossing;
import com.example.globe.core.ui.OverlayLayout;
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
    /** Fade ramp length in wall-clock ms (see {@link ZoneEnterTitleOverlay}; ticks kept for timing parity). */
    private static final long FADE_MS = FADE_TICKS * 50L;

    /** GUI-scale parity (audit M1/M4): fit-to-width margin + floor, mirroring {@link ZoneEnterTitleOverlay}. */
    private static final int SIDE_MARGIN = 6;
    private static final double MIN_TITLE_SCALE = 0.5;

    private static String northSouthLine;
    private static String eastWestLine;
    // WALL-CLOCK window (System.currentTimeMillis), matching ZoneEnterTitleOverlay: the fade alpha and the
    // shared glimmer sweep are driven off wall time so they stay smooth through the tick stalls a hemisphere
    // crossing (often reached by teleport) triggers. See ZoneEnterTitleOverlay's field note for the full why.
    private static long startMs = Long.MIN_VALUE;
    private static long endMs = Long.MIN_VALUE;
    private static float scale = 1.8f;

    private HemisphereTitleOverlay() {
    }

    /** Clears any in-flight hemisphere title (disconnect / world change), mirroring
     *  {@link ZoneEnterTitleOverlay#reset()} -- the only resurrection guard needed on wall-clock timing. */
    public static void reset() {
        northSouthLine = null;
        eastWestLine = null;
        startMs = Long.MIN_VALUE;
        endMs = Long.MIN_VALUE;
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
        long now = System.currentTimeMillis();
        // If the window has lapsed, this is a fresh title: clear any stale sibling line and fade in anew.
        if (now >= endMs) {
            northSouthLine = null;
            eastWestLine = null;
            startMs = now;
        }
        int dt = Math.max(1, durationTicks);
        endMs = now + dt * 50L;
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
        long now = System.currentTimeMillis();
        return now >= startMs && now < endMs;
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null) {
            return;
        }
        if (northSouthLine == null && eastWestLine == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < startMs || now >= endMs) {
            return;
        }

        float alpha = 1.0f;
        long age = now - startMs;
        long remaining = endMs - now;
        if (age < FADE_MS) {
            alpha = (float) age / (float) FADE_MS;
        } else if (remaining < FADE_MS) {
            alpha = (float) remaining / (float) FADE_MS;
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

        // M1/M4 -- fit-to-width: a long hemisphere line (e.g. "Northern Hemisphere") at a large scale on a
        // small GUI-scale canvas would overflow both edges. Fit against the WIDEST line so both lines share
        // ONE scale and the stacked block stays visually uniform. The block is center-anchored (cx = screenW/2)
        // so it needs no horizontal clamp -- fitting the width keeps it on-screen.
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, ZoneEnterTitleOverlay.styledWidth(font, line));
        }
        double drawScale = OverlayLayout.fitScale(scale, widest, screenW - 2 * SIDE_MARGIN, MIN_TITLE_SCALE);

        // One line-height (scaled) between stacked lines; block is vertically centered on the anchor.
        int lineGap = Math.round(font.lineHeight * (float) drawScale);
        int firstY = anchorY - (lineGap * (lines.length - 1)) / 2;
        // Share the zone title's one-shot color-aware glimmer choreography so a hemisphere title reads as the
        // same visual system (styling already comes from the shared draw; this carries the glimmer envelope too).
        // Hemisphere titles never carry a degrees token, so each composed line renders as its own HERO line
        // (crest + bloom); the two-line degrees LOCKUP applies only to zone titles.
        com.example.globe.core.ui.TitleStyle.GlimmerFrame glimmer = ZoneEnterTitleOverlay.glimmerFrame(age);
        for (int i = 0; i < lines.length; i++) {
            ZoneEnterTitleOverlay.drawTitleLineAt(ctx, cx, firstY + i * lineGap, lines[i], drawScale, alphaByte, glimmer);
        }
    }
}
