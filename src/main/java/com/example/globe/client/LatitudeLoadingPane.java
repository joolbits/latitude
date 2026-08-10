package com.example.globe.client;

import com.example.globe.GlobeMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

/**
 * The Latitude loading pane, drawn identically by every screen a Latitude world load passes
 * through.
 *
 * <p>Extracted from {@code LevelLoadingScreenLatitudeOverlayMixin} because a resumed world does
 * not reach {@code LevelLoadingScreen} for several seconds: {@code WorldOpenFlows.openWorld}
 * shows three sequential {@code GenericMessageScreen}s first (its own head, then
 * {@code openWorldLoadLevelData}, then {@code openWorldLoadLevelStem}), and the overlay only ever
 * painted the last screen in that chain. That is exactly the "vanilla loading screen, last minute
 * switches to bespoke" a maintainer reported on reload, 2026-08-09. World creation never showed it
 * because {@code createFreshLevel} passes through a single such screen with no level data to read
 * or stem to resolve, so it flashes by.
 *
 * <p>The animation state is deliberately <b>static</b>, not per-screen. Vanilla constructs a new
 * screen instance at each step of that chain, so instance state would restart the phrase cycle and
 * snap the compass needle back on every swap — visible as a stutter precisely at the moments this
 * class exists to smooth over. One shared clock makes the pane continuous across the handoff. Only
 * one world load is ever in flight at a time, which is what makes a single static clock correct
 * rather than merely convenient.
 */
public final class LatitudeLoadingPane {

    // ── Theme ──
    private static final int PANE_BG = 0xE62C2420;
    private static final int PANE_BORDER = 0xFF5C4A3A;
    private static final int GOLD = 0xFFD4A74A;
    private static final int WARM_WHITE = 0xFFEDE0D0;
    private static final int MUTED = 0xFF8C8078;
    private static final int GRID_COLOR = 0x14504840;
    private static final int GRID_STEP = 16;
    // Mirrors LatitudeCreateWorldScreen's title treatment: vanilla's nine-pixel UI font plus four
    // points, with intentional tracking between letters via literal spaces in the string.
    private static final float LOADING_TITLE_SCALE = 13.0f / 9.0f;
    private static final String LOADING_TITLE_TEXT = "L A T I T U D E";
    // Deliberately quiet: smaller than body text, muted rather than warm-white, italic — a
    // background detail, not something competing with the title or the rotating phrase.
    private static final float ZONE_LABEL_SCALE = 0.75f;
    private static final float VERSION_LABEL_SCALE = 0.67f;
    private static final int VERSION_LABEL_GAP = 4;
    private static final int VERSION_LABEL_SCREEN_MARGIN = 2;
    private static final String VERSION_LABEL = FabricLoader.getInstance()
            .getModContainer(GlobeMod.MOD_ID)
            .map(container -> "v" + container.getMetadata().getVersion().getFriendlyString())
            .orElse("");

    /** Passed as the progress argument by screens that have no progress to report yet. */
    public static final float NO_PROGRESS = 0f;

    // ── Loading phrases ──
    private static final String[] PHRASES = {
            "Defusing creepers...",
            "Charting the frontier...",
            "Following the compass...",
            "Packing snow boots...",
            "Crossing climate bands...",
            "Calibrating the equator...",
            "Warming the tropics...",
            "Freezing the poles...",
            "Planting bamboo groves...",
            "Surveying the horizon...",
            "Stacking tectonic plates...",
            "Cloning sheep...",
            "Folding mountain ranges...",
            "Teaching villagers cartography...",
            "Nudging continents apart...",
    };

    // The Latitude-feature splashes are the last FEATURED_PHRASE_COUNT entries of PHRASES.
    // Always start there so even a fast load shows one player-facing Latitude detail.
    private static final int FEATURED_PHRASE_COUNT = 14;
    private static final long PHRASE_CYCLE_MS = 4800;
    private static final long DIRECTION_CHANGE_INTERVAL_MS = 2200;

    // ── Shared animation state (see class javadoc on why this is static) ──
    private static long overlayStartMs = 0L;
    private static int phraseSeedIdx = 0;
    private static double needleAngle = 0.0;
    private static double needleTarget = Math.PI * 0.5;
    private static long lastDirectionChangeMs = 0L;

    private LatitudeLoadingPane() {
    }

    /** True once {@link #start(long)} has run and no {@link #reset()} has followed. */
    public static boolean hasStarted() {
        return overlayStartMs != 0L;
    }

    /** Begins (or restarts) the pane's clock. Idempotent while the pane is already running. */
    public static void start(long now) {
        if (overlayStartMs != 0L) {
            return;
        }
        overlayStartMs = now;
        lastDirectionChangeMs = now;
        phraseSeedIdx = pickSeedIndex();
    }

    /** Clears the clock so the next load starts a fresh phrase cycle. */
    public static void reset() {
        overlayStartMs = 0L;
    }

    public static long elapsedMs(long now) {
        return overlayStartMs == 0L ? 0L : now - overlayStartMs;
    }

    private static int pickSeedIndex() {
        int total = PHRASES.length;
        int featuredStart = Math.max(0, total - FEATURED_PHRASE_COUNT);
        if (featuredStart < total) {
            return featuredStart + (int) (Math.random() * (total - featuredStart));
        }
        return (int) (Math.random() * total);
    }

    /**
     * Paints the pane. {@code progress} is clamped to [0,1]; pass {@link #NO_PROGRESS} from screens
     * that run before vanilla has any chunk progress to report — the track still draws, empty, so
     * the pane's geometry does not shift when the real loading screen takes over.
     */
    public static void render(GuiGraphics context, Font font, float delta, float progress, long now) {
        long elapsed = elapsedMs(now);

        int sw = context.guiWidth();
        int sh = context.guiHeight();

        // ── Brown pane (centered, covers the vanilla chunk grid) ──
        int paneW = Math.min(sw - 40, 340);
        int paneH = Math.min(sh - 40, 200);
        int paneX = (sw - paneW) / 2;
        int paneY = (sh - paneH) / 2;

        // Border
        context.fill(paneX - 1, paneY - 1, paneX + paneW + 1, paneY + paneH + 1, PANE_BORDER);
        // Fill
        context.fill(paneX, paneY, paneX + paneW, paneY + paneH, PANE_BG);

        // ── Grid decoration ──
        drawGrid(context, paneX, paneY, paneW, paneH);

        // ── Title ── (mirrors LatitudeCreateWorldScreen's treatment: scaled + letter-spaced when
        // it fits the pane, falling back to compact unspaced text on very small windows)
        int cx = sw / 2;
        int cursorY = paneY + 12;
        int titleAreaW = paneW - 16;
        int scaledTitleWidth = Math.round(font.width(LOADING_TITLE_TEXT) * LOADING_TITLE_SCALE);
        int titleHeight;
        if (scaledTitleWidth <= titleAreaW) {
            drawScaledCentered(context, font, LOADING_TITLE_TEXT, cx, cursorY, LOADING_TITLE_SCALE, GOLD, true);
            titleHeight = Math.round(font.lineHeight * LOADING_TITLE_SCALE);
        } else {
            drawCentered(context, font, "LATITUDE", cx, cursorY, GOLD, true);
            titleHeight = font.lineHeight;
        }
        cursorY += titleHeight + 3;

        // ── Zone label (optional) — the climate zone this load is entering or resuming ──
        String zoneLabel = LatitudeClientState.loadingZoneLabel();
        if (zoneLabel != null) {
            drawMutedItalicCentered(context, font, "Loading " + zoneLabel, cx, cursorY, ZONE_LABEL_SCALE, MUTED);
            cursorY += Math.round(font.lineHeight * ZONE_LABEL_SCALE) + 3;
        }

        // ── Loading hint ──
        drawCentered(context, font, "Press F9 in-game for HUD options", cx, cursorY, MUTED, false);

        // ── Compass with wandering needle ──
        int compassCY = paneY + paneH / 2 - 4;
        int compassR = Math.min(28, Math.min(paneW, paneH) / 5);
        updateNeedle(now, delta);
        drawCompass(context, font, cx, compassCY, compassR);

        // ── Rotating phrase with fade ──
        int phraseY = compassCY + compassR + 16;
        drawPhrase(context, font, cx, phraseY, elapsed);

        // ── Progress bar (strictly bounded by the caller's progress) ──
        int barW = Math.min(160, paneW - 40);
        int barX = cx - barW / 2;
        int barY = paneY + paneH - 20;
        float clamped = Mth.clamp(progress, 0f, 1f);
        context.fill(barX, barY, barX + barW, barY + 3, 0xFF1A1410);
        int fillW = Math.round(clamped * barW);
        if (fillW > 0) {
            context.fill(barX, barY, barX + fillW, barY + 3, GOLD);
        }

        // Small, quiet build identity just below the pane's right edge.
        drawVersionLabel(context, font, paneX, paneY, paneW, paneH);
    }

    private static void drawCentered(GuiGraphics context, Font font, String text, int cx, int y, int color, boolean shadow) {
        int w = font.width(text);
        context.drawString(font, text, cx - w / 2, y, color, shadow);
    }

    /** Mirrors LatitudeCreateWorldScreen's drawScaledText, centered on cx instead of a left edge. */
    private static void drawScaledCentered(GuiGraphics context, Font font, String text, int cx, int y, float scale, int color, boolean shadow) {
        int w = Math.round(font.width(text) * scale);
        int x = cx - w / 2;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawString(font, text, 0, 0, color, shadow);
        matrices.popMatrix();
    }

    /** Same scaled/centered treatment as drawScaledCentered, but italic — used only for
     * the quiet zone-label line, never for the title. */
    private static void drawMutedItalicCentered(GuiGraphics context, Font font, String text, int cx, int y, float scale, int color) {
        Component styled = Component.literal(text).withStyle(Style.EMPTY.withItalic(true));
        int w = Math.round(font.width(styled) * scale);
        int x = cx - w / 2;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawString(font, styled, 0, 0, color, false);
        matrices.popMatrix();
    }

    private static void drawVersionLabel(GuiGraphics context, Font font, int paneX, int paneY, int paneW, int paneH) {
        if (VERSION_LABEL.isEmpty()) {
            return;
        }
        float scaledWidth = font.width(VERSION_LABEL) * VERSION_LABEL_SCALE;
        float scaledHeight = font.lineHeight * VERSION_LABEL_SCALE;
        float paneRight = paneX + paneW;
        float paneBottom = paneY + paneH;
        float x = paneRight - scaledWidth;
        float preferredY = paneBottom + VERSION_LABEL_GAP;
        float maxY = context.guiHeight() - scaledHeight - VERSION_LABEL_SCREEN_MARGIN;
        // Current pane geometry always reserves at least 20 px below the pane. The
        // clamp keeps the label attached to the lower-right edge at compact heights.
        float y = Math.min(preferredY, maxY);
        float drawX = x / VERSION_LABEL_SCALE;
        float drawY = y / VERSION_LABEL_SCALE;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.scale(VERSION_LABEL_SCALE, VERSION_LABEL_SCALE);
        context.drawString(font, VERSION_LABEL, Math.round(drawX), Math.round(drawY), MUTED, false);
        matrices.popMatrix();
    }

    private static void updateNeedle(long now, float delta) {
        // Change target direction at intervals — random wandering
        if (now - lastDirectionChangeMs > DIRECTION_CHANGE_INTERVAL_MS) {
            lastDirectionChangeMs = now;
            // Pick a new random target angle (full 360°)
            needleTarget += (Math.PI * 0.4) + (Math.random() * Math.PI * 1.2);
            // Randomly reverse direction sometimes
            if (Math.random() < 0.35) {
                needleTarget = needleAngle - (needleTarget - needleAngle);
            }
        }
        // Smooth interpolation toward target
        double diff = needleTarget - needleAngle;
        // Normalize diff to [-PI, PI]
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        needleAngle += diff * 0.03 * delta;
    }

    private static void drawCompass(GuiGraphics context, Font font, int cx, int cy, int radius) {
        // Compass face — dark circle with gold ring
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int dist2 = dx * dx + dy * dy;
                if (dist2 <= r2) {
                    int px = cx + dx;
                    int py = cy + dy;
                    if (dist2 > (radius - 2) * (radius - 2)) {
                        context.fill(px, py, px + 1, py + 1, GOLD);
                    } else {
                        context.fill(px, py, px + 1, py + 1, 0xFF1A1410);
                    }
                }
            }
        }

        // Cardinal tick marks (N/S/E/W)
        int tickLen = Math.max(2, radius / 6);
        // N tick — white
        context.fill(cx, cy - radius + 2, cx + 1, cy - radius + 2 + tickLen, WARM_WHITE);
        // S
        context.fill(cx, cy + radius - 2 - tickLen, cx + 1, cy + radius - 2, MUTED);
        // E
        context.fill(cx + radius - 2 - tickLen, cy, cx + radius - 2, cy + 1, MUTED);
        // W
        context.fill(cx - radius + 2, cy, cx - radius + 2 + tickLen, cy + 1, MUTED);

        // Red 'N' label at north
        String nLabel = "N";
        int nW = font.width(nLabel);
        context.drawString(font, nLabel, cx - nW / 2 + 1, cy - radius + 2 + tickLen + 1, 0xFFCC3333, true);

        // Wandering needle
        double angle = needleAngle;
        int needleLen = radius - 4;

        // Red north half
        int nx = cx + (int) Math.round(Math.sin(angle) * needleLen);
        int ny = cy - (int) Math.round(Math.cos(angle) * needleLen);
        drawLine(context, cx, cy, nx, ny, 0xFFCC3333);

        // White south half (shorter)
        int sx = cx - (int) Math.round(Math.sin(angle) * (needleLen * 0.6));
        int sy = cy + (int) Math.round(Math.cos(angle) * (needleLen * 0.6));
        drawLine(context, cx, cy, sx, sy, WARM_WHITE);

        // Center dot
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD);
    }

    private static void drawLine(GuiGraphics context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private static void drawPhrase(GuiGraphics context, Font font, int cx, int y, long elapsedMs) {
        long cyclePos = elapsedMs % PHRASE_CYCLE_MS;
        int phraseIdx = (phraseSeedIdx + (int) ((elapsedMs / PHRASE_CYCLE_MS) % PHRASES.length)) % PHRASES.length;
        String phrase = PHRASES[phraseIdx];

        // Fade: quick in (first 15%), steady (60%), quick out (last 25%)
        float t = (float) cyclePos / PHRASE_CYCLE_MS;
        float alpha;
        if (t < 0.15f) {
            alpha = t / 0.15f;
        } else if (t < 0.75f) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - (t - 0.75f) / 0.25f;
        }
        alpha = Mth.clamp(alpha, 0f, 1f);

        int a = Math.round(alpha * 255);
        if (a < 4) return;
        int color = (a << 24) | (WARM_WHITE & 0x00FFFFFF);

        int w = font.width(phrase);
        context.drawString(font, phrase, cx - w / 2, y, color, false);
    }

    private static void drawGrid(GuiGraphics context, int paneX, int paneY, int paneW, int paneH) {
        for (int gy = GRID_STEP; gy < paneH; gy += GRID_STEP) {
            context.fill(paneX, paneY + gy, paneX + paneW, paneY + gy + 1, GRID_COLOR);
        }
        for (int gx = GRID_STEP; gx < paneW; gx += GRID_STEP) {
            context.fill(paneX + gx, paneY, paneX + gx + 1, paneY + paneH, GRID_COLOR);
        }
    }
}
