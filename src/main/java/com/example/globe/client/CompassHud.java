package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

public final class CompassHud {
    private static final int ANALOG_FACE_RGB = 0x1A1410;
    private static final int ANALOG_RING = 0xFFD4A74A;
    private static final int ANALOG_MUTED = 0xFF8C8078;
    private static final int ANALOG_N_COLOR = 0xFFCC3333;
    private static final int ANALOG_PREVIEW_BORDER = 0x55FFFFFF;
    private static final int ANALOG_LAT_GAP = 6;
    private static final int PREVIEW_HOTBAR_BG = 0x33241814;
    private static final int PREVIEW_HOTBAR_BORDER = 0x66A08972;
    private static final int PREVIEW_HOTBAR_SLOT = 0x22382F26;

    private static long lastCheckWorldTime = Long.MIN_VALUE;
    private static boolean cachedHasCompass = false;

    public record HudBounds(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < (x + w) && my >= y && my < (y + h);
        }
    }

    public record HudPoint(int x, int y) {
    }

    private CompassHud() {}

    // Keep for compatibility with existing GlobeModClient init call.
    public static void init() {}

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        renderInternal(ctx, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), false);
    }

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        renderInternal(ctx, screenW, screenH, false);
    }

    public static void renderAdjustPreview(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        renderInternal(ctx, screenW, screenH, true);
    }

    private static void renderInternal(GuiGraphicsExtractor ctx, int screenW, int screenH, boolean forceVisible) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        var cfg = CompassHudConfig.get();
        boolean studioPreview = client.gui.screen() instanceof LatitudeHudStudioScreen;

        if (forceVisible && (studioPreview || client.player == null || client.level == null)) {
            if (studioPreview && shouldRenderPreviewHotbar(cfg)) {
                drawPreviewHotbar(ctx, screenW, screenH);
            }
            HudBounds previewBounds = computePreviewBounds(client, cfg);
            renderPreview(ctx, client, cfg, previewBounds.x(), previewBounds.y());
            return;
        }

        if (client.player == null || client.level == null) {
            return;
        }

        if (!forceVisible && client.gui.screen() != null) {
            return;
        }

        if (!forceVisible && !cfg.enabled) {
            return;
        }

        boolean isHoldingCompass = client.player.getMainHandItem().is(Items.COMPASS)
                || client.player.getOffhandItem().is(Items.COMPASS);

        long t = client.level.getGameTime();
        if (t != lastCheckWorldTime) {
            lastCheckWorldTime = t;
            cachedHasCompass = hasCompassAnywhere(client.player);
        }

        boolean hasCompassAnywhere = cachedHasCompass;

        if (!forceVisible) {
            switch (cfg.showMode) {
                case ALWAYS -> {
                }
                case COMPASS_PRESENT -> {
                    if (!hasCompassAnywhere) return;
                }
                case HOLDING_COMPASS -> {
                    if (!isHoldingCompass) return;
                }
            }
        }

        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            String latText = analogLatLonText(client, cfg);
            String zoneText = cfg.zoneFollowsCompass ? zoneLabel(client, cfg, false) : null;
            HudBounds b = computeAnalogBounds(screenW, screenH, client, cfg, latText, zoneText);
            renderAnalogAt(ctx, client, cfg, latText, zoneText, b.x, b.y, forceVisible);
            if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
                renderDetachedZone(ctx, client, cfg, forceVisible);
            }
        } else {
            String directionText = switch (cfg.directionMode) {
                case CARDINAL_8 -> direction8(client.player.getYRot());
                case CARDINAL_4 -> direction4(client.player.getYRot());
                case DEGREES -> degrees(client.player.getYRot());
            };

            String latLonText = joinLatLon(latitudeText(client, cfg), longitudeText(client, cfg));
            String hudText = buildDigitalLine(directionText, latLonText, zoneLabel(client, cfg, true), cfg.compactHud);

            String[] lines = new String[]{hudText};
            HudBounds b = computeBounds(screenW, screenH, client, cfg, lines);
            renderDigitalAt(ctx, client, cfg, lines, b.x, b.y, forceVisible);
            if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
                renderDetachedZone(ctx, client, cfg, forceVisible);
            }
        }
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client, cfg, analogSampleLatLon(cfg), cfg.zoneFollowsCompass ? sampleZone(cfg) : null);
        }
        return computeBounds(client, cfg, sampleLines(cfg));
    }

    public static HudPoint computeBasePosition(Minecraft client, CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBasePosition(client, cfg, analogSampleLatLon(cfg), cfg.zoneFollowsCompass ? sampleZone(cfg) : null);
        }
        return computeDigitalBasePosition(client, cfg, sampleLines(cfg));
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg, Component text) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, analogSampleLatLon(cfg), cfg.zoneFollowsCompass ? sampleZone(cfg) : null);
        }
        return computeBounds(screenW, screenH, client, cfg, new String[]{text.getString()});
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, analogSampleLatLon(cfg), cfg.zoneFollowsCompass ? sampleZone(cfg) : null);
        }
        return computeBounds(screenW, screenH, client, cfg, lines);
    }

    private static HudBounds computePreviewBounds(Minecraft client, CompassHudConfig cfg) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, analogSampleLatLon(cfg), cfg.zoneFollowsCompass ? sampleZone(cfg) : null);
        }
        return computeDigitalBounds(screenW, screenH, client, cfg, sampleLines(cfg), true);
    }

    private static HudBounds computeBounds(int screenW, int screenH, Minecraft client, CompassHudConfig cfg, String[] lines) {
        return computeDigitalBounds(screenW, screenH, client, cfg, lines, false);
    }

    private static HudBounds computeDigitalBounds(int screenW, int screenH, Minecraft client, CompassHudConfig cfg, String[] lines, boolean previewAttachToHotbar) {

        int pad = cfg.padding;
        int textW = maxLineWidth(client, lines);
        int textH = client.font.lineHeight * lines.length;

        int boxW = textW + pad * 2;
        int boxH = textH + pad * 2;

        float s = cfg.scale;
        int scaledBoxW = (int) Math.ceil(boxW * s);
        int scaledBoxH = (int) Math.ceil(boxH * s);

        int x;
        int y;
        if (cfg.style == CompassHudConfig.CompassStyle.DIGITAL && cfg.attachToHotbarCompass && previewAttachToHotbar) {
            HudPoint attached = computeAttachedCompassPosition(screenW, screenH, cfg, scaledBoxW, scaledBoxH);
            x = attached.x();
            y = attached.y();
        } else if (cfg.style == CompassHudConfig.CompassStyle.DIGITAL && cfg.attachToHotbarCompass && client.player != null) {
            int slotIndex = findHotbarCompassSlot(client.player);
            if (slotIndex >= 0) {
                HudPoint attached = computeAttachedCompassPosition(screenW, screenH, cfg, scaledBoxW, scaledBoxH);
                x = attached.x();
                y = attached.y();
            } else {
                x = anchoredX(cfg, screenW, scaledBoxW);
                y = anchoredY(cfg, screenH, scaledBoxH);
            }
        } else {
            x = anchoredX(cfg, screenW, scaledBoxW);
            y = anchoredY(cfg, screenH, scaledBoxH);
        }

        x += cfg.offsetX;
        y += cfg.offsetY;

        x = clamp(x, 0, Math.max(0, screenW - scaledBoxW));
        y = clamp(y, 0, Math.max(0, screenH - scaledBoxH));

        return new HudBounds(x, y, scaledBoxW, scaledBoxH);
    }

    public static void renderPreview(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, int x, int y) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            renderAnalogAt(ctx, client, cfg, analogSampleLatLon(cfg), cfg.zoneFollowsCompass ? sampleZone(cfg) : null, x, y, true);
            if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
                renderDetachedZone(ctx, client, cfg, true);
            }
        } else {
            renderDigitalAt(ctx, client, cfg, sampleLines(cfg), x, y, true);
            if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
                renderDetachedZone(ctx, client, cfg, true);
            }
        }
    }

    private static void renderDigitalAt(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, String[] lines, int x, int y, boolean isPreview) {
        int pad = cfg.padding;
        int textW = maxLineWidth(client, lines);
        int textH = client.font.lineHeight * lines.length;

        int boxW = textW + pad * 2;
        int boxH = textH + pad * 2;

        float s = cfg.scale;

        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(x, y);
            m.scale(s, s);

            if (cfg.showBackground || isPreview) {
                int bg;
                if (cfg.showBackground) {
                    bg = cfg.backgroundArgb();
                } else {
                    int a = 160;
                    bg = (a << 24) | (cfg.backgroundRgb & 0xFFFFFF);
                }
                ctx.fill(0, 0, boxW, boxH, bg);
                if (isPreview) {
                    int border = 0x55FFFFFF;
                    ctx.fill(0, 0, boxW, 1, border);
                    ctx.fill(0, boxH - 1, boxW, boxH, border);
                    ctx.fill(0, 0, 1, boxH, border);
                    ctx.fill(boxW - 1, 0, boxW, boxH, border);
                }
            }

            int color = cfg.textArgb();
            int tx = pad;
            int ty = pad;

            for (int i = 0; i < lines.length; i++) {
                int lineY = ty + i * client.font.lineHeight;
                Component line = Component.literal(lines[i]);
                if (cfg.shadow) {
                    ctx.text(client.font, line, tx, lineY, color);
                } else {
                    ctx.text(client.font, line, tx, lineY, color, false);
                }
            }
        } finally {
            m.popMatrix();
        }
    }

    private static void renderAnalogAt(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, String latText, String zoneText, int x, int y, boolean isPreview) {
        int diameter = analogDiameter(cfg);
        int radius = diameter / 2;
        int cx = x + radius;
        int cy = y + radius;

        float yaw = client.player != null ? client.player.getYRot() : -180.0f;
        double angle = Math.toRadians(Mth.wrapDegrees(yaw + 180.0f));

        if (isPreview && client.gui.screen() instanceof LatitudeHudStudioScreen) {
            // Transparency preview aid — HUD STUDIO ONLY (that's the only screen with the Inner Transparency
            // slider). A checkerboard behind the disc makes the see-through inner disc visible; on a plain dark
            // screen a transparent disc looks identical to an opaque one. Deliberately NOT drawn on other compass
            // previews (e.g. the Latitude Settings screen), where there's no transparency control and it just
            // looks confusing.
            drawTransparencyCheckerboard(ctx, cx - radius, cy - radius, diameter);
        }

        drawAnalogCompass(ctx, cfg, cx, cy, radius, angle);

        if (isPreview) {
            int boxW = diameter;
            int boxH = diameter;
            int extraTextW = 0;
            int extraTextH = 0;
            if (latText != null && !latText.isEmpty()) {
                extraTextW += ANALOG_LAT_GAP + client.font.width(latText);
                extraTextH = Math.max(extraTextH, client.font.lineHeight);
            }
            if (cfg.displayZoneInHud && cfg.zoneFollowsCompass && zoneText != null && !zoneText.isEmpty()) {
                if (extraTextW == 0) extraTextW += ANALOG_LAT_GAP;
                else extraTextW += (cfg.compactHud ? 1 : 6);
                extraTextW += client.font.width(zoneText);
                extraTextH = Math.max(extraTextH, client.font.lineHeight);
            }
            boxW += extraTextW;
            if (extraTextH > 0) boxH = Math.max(boxH, extraTextH);
            ctx.fill(x, y, x + boxW, y + 1, ANALOG_PREVIEW_BORDER);
            ctx.fill(x, y + boxH - 1, x + boxW, y + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(x, y, x + 1, y + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(x + boxW - 1, y, x + boxW, y + boxH, ANALOG_PREVIEW_BORDER);
        }

        int textX = x + diameter + ANALOG_LAT_GAP;
        int textY = cy - client.font.lineHeight / 2;
        int color = cfg.textArgb();
        if (latText != null && !latText.isEmpty()) {
            drawText(ctx, client, cfg, latText, textX, textY, color);
            textX += client.font.width(latText) + (cfg.compactHud ? 1 : 6);
        }
        if (cfg.displayZoneInHud && cfg.zoneFollowsCompass && zoneText != null && !zoneText.isEmpty()) {
            drawText(ctx, client, cfg, zoneText, textX, textY, color);
        }
    }

    // Photoshop-style transparency checkerboard, drawn behind the analog disc in the HUD studio preview only so
    // the Inner Transparency slider's effect is visible against it instead of against a flat dark screen.
    private static void drawTransparencyCheckerboard(GuiGraphicsExtractor ctx, int x, int y, int size) {
        int cell = Math.max(3, size / 6);
        int light = 0xFFBFBFBF;
        int dark = 0xFF6E6E6E;
        for (int gy = 0; gy < size; gy += cell) {
            for (int gx = 0; gx < size; gx += cell) {
                boolean isLight = (((gx / cell) + (gy / cell)) & 1) == 0;
                int x0 = x + gx;
                int y0 = y + gy;
                int x1 = Math.min(x + size, x0 + cell);
                int y1 = Math.min(y + size, y0 + cell);
                ctx.fill(x0, y0, x1, y1, isLight ? light : dark);
            }
        }
    }

    private static void drawAnalogCompass(GuiGraphicsExtractor ctx, CompassHudConfig cfg, int cx, int cy, int radius, double angle) {
        int r2 = radius * radius;
        var colors = analogColors(cfg);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int dist2 = dx * dx + dy * dy;
                if (dist2 > r2) continue;
                int px = cx + dx;
                int py = cy + dy;
                if (dist2 > (radius - 2) * (radius - 2)) {
                    ctx.fill(px, py, px + 1, py + 1, colors.ring());
                } else {
                    ctx.fill(px, py, px + 1, py + 1, analogInnerColor(cfg, colors.face()));
                }
            }
        }

        int tickLen = Math.max(2, radius / 6);
        // North tick (up)
        ctx.fill(cx, cy - radius + 2, cx + 1, cy - radius + 2 + tickLen, colors.ring());
        // South tick
        ctx.fill(cx, cy + radius - 2 - tickLen, cx + 1, cy + radius - 2, colors.muted());
        // East tick
        ctx.fill(cx + radius - 2 - tickLen, cy, cx + radius - 2, cy + 1, colors.muted());
        // West tick
        ctx.fill(cx - radius + 2, cy, cx - radius + 2 + tickLen, cy + 1, colors.muted());

        String nLabel = "N";
        int nW = Minecraft.getInstance().font.width(nLabel);
        ctx.text(Minecraft.getInstance().font, nLabel, cx - nW / 2 + 1, cy - radius + 2 + tickLen + 1, colors.needle(), true);

        int needleLen = radius - 4;
        int nx = cx + (int) Math.round(Math.sin(angle) * needleLen);
        int ny = cy - (int) Math.round(Math.cos(angle) * needleLen);
        drawLine(ctx, cx, cy, nx, ny, colors.needle());

        int sx = cx - (int) Math.round(Math.sin(angle) * (needleLen * 0.6));
        int sy = cy + (int) Math.round(Math.cos(angle) * (needleLen * 0.6));
        drawLine(ctx, cx, cy, sx, sy, colors.ring());

        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, colors.ring());
    }

    private static int analogInnerColor(CompassHudConfig cfg, int faceRgb) {
        int a = Mth.clamp((int) Math.round(cfg.analogInnerAlpha * 255.0f), 0, 255);
        return (a << 24) | (faceRgb & 0xFFFFFF);
    }

    private static void drawLine(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private static String[] sampleLines(CompassHudConfig cfg) {
        String dir = sampleDirection(cfg);
        String lat = Boolean.TRUE.equals(cfg.showLatitude) ? "1\u00b0S" : null;
        String lon = Boolean.TRUE.equals(cfg.showLongitude) ? "15\u00b0E" : null;
        String zone = cfg.displayZoneInHud && cfg.zoneFollowsCompass ? sampleZone(cfg) : null;
        return new String[]{buildDigitalLine(dir, joinLatLon(lat, lon), zone, cfg.compactHud)};
    }

    private static String analogSampleLatLon(CompassHudConfig cfg) {
        String lat = Boolean.TRUE.equals(cfg.analogShowLatitude) ? "1\u00b0S" : null;
        String lon = Boolean.TRUE.equals(cfg.analogShowLongitude) ? "15\u00b0E" : null;
        return joinLatLon(lat, lon);
    }

    private static String joinLatLon(String lat, String lon) {
        if (lat == null) return lon;
        if (lon == null) return lat;
        return lat + ", " + lon;
    }

    // Sample latitude used for previews/placeholders is 1S (see sampleLines/analogSampleLatLon), which is in
    // the Tropics -- keep the sample zone word consistent with that latitude so the placeholder never shows
    // an impossible pairing like "1S / Temperate". Longitude's sample value doesn't affect the zone word.
    private static String sampleZone(CompassHudConfig cfg) {
        return cfg.displayZoneInHud ? "Tropics" : null;
    }

    private static String analogLatLonText(Minecraft client, CompassHudConfig cfg) {
        String lat = Boolean.TRUE.equals(cfg.analogShowLatitude)
                ? (client.player == null || client.level == null ? "1\u00b0S" : LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.level.getWorldBorder()))
                : null;
        String lon = Boolean.TRUE.equals(cfg.analogShowLongitude)
                ? (client.player == null || client.level == null ? "15\u00b0E" : LatitudeMath.formatLongitudeDeg(client.player.getX(), client.level.getWorldBorder()))
                : null;
        return joinLatLon(lat, lon);
    }

    private static String latitudeText(Minecraft client, CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.showLatitude)) return null;
        if (client.player == null || client.level == null) return "0\u00b0";
        return LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.level.getWorldBorder());
    }

    private static String longitudeText(Minecraft client, CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.showLongitude)) return null;
        if (client.player == null || client.level == null) return "0\u00b0";
        return LatitudeMath.formatLongitudeDeg(client.player.getX(), client.level.getWorldBorder());
    }

    private static String buildDigitalLine(String directionText, String latText, String zoneText, boolean compact) {
        String sep = compact ? " " : " \u00b7 ";
        StringBuilder sb = new StringBuilder(directionText);
        if (latText != null) {
            sb.append(sep).append(latText);
        }
        if (zoneText != null) {
            if (latText != null) sb.append(compact ? " " : " \u00b7 ");
            else sb.append(sep);
            sb.append(zoneText);
        }
        return sb.toString();
    }

    private static HudPoint computeAnalogBasePosition(Minecraft client, CompassHudConfig cfg, String latText, String zoneText) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        int diameter = analogDiameter(cfg);
        int boxW = diameter;
        int boxH = diameter;
        if (latText != null && !latText.isEmpty()) {
            boxW += ANALOG_LAT_GAP + client.font.width(latText);
            boxH = Math.max(boxH, client.font.lineHeight);
        }
        if (cfg.displayZoneInHud && cfg.zoneFollowsCompass && zoneText != null && !zoneText.isEmpty()) {
            boxW += (cfg.compactHud ? 1 : 6) + client.font.width(zoneText);
            boxH = Math.max(boxH, client.font.lineHeight);
        }

        int x;
        int y;
        x = anchoredX(cfg, screenW, boxW);
        y = anchoredY(cfg, screenH, boxH);

        x = clamp(x, 0, Math.max(0, screenW - boxW));
        y = clamp(y, 0, Math.max(0, screenH - boxH));

        return new HudPoint(x, y);
    }

    private static HudBounds computeAnalogBounds(int screenW, int screenH, Minecraft client, CompassHudConfig cfg, String latText, String zoneText) {
        int diameter = analogDiameter(cfg);
        int boxW = diameter;
        int boxH = diameter;
        if (latText != null && !latText.isEmpty()) {
            boxW += ANALOG_LAT_GAP + client.font.width(latText);
            boxH = Math.max(boxH, client.font.lineHeight);
        }
        if (cfg.displayZoneInHud && cfg.zoneFollowsCompass && zoneText != null && !zoneText.isEmpty()) {
            boxW += (cfg.compactHud ? 1 : 6) + client.font.width(zoneText);
            boxH = Math.max(boxH, client.font.lineHeight);
        }

        int x;
        int y;
        x = anchoredX(cfg, screenW, boxW);
        y = anchoredY(cfg, screenH, boxH);
        x += cfg.offsetX;
        y += cfg.offsetY;

        x = clamp(x, 0, Math.max(0, screenW - boxW));
        y = clamp(y, 0, Math.max(0, screenH - boxH));
        return new HudBounds(x, y, boxW, boxH);
    }

    private static HudPoint computeAttachedCompassPosition(int screenW, int screenH, CompassHudConfig cfg, int boxW, int boxH) {
        int hotbarLeft = screenW / 2 - 91;
        int hotbarTop = screenH - 22;
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            int x = hotbarLeft + (182 - boxW) / 2;
            int y = hotbarTop + (22 - boxH) / 2;
            return new HudPoint(x, y);
        }
        int hotbarRight = hotbarLeft + 182;
        int margin = 4;
        int x = hotbarRight + margin;
        int y = hotbarTop + (22 - boxH) / 2;
        if (x + boxW > screenW - margin) {
            x = hotbarLeft - margin - boxW;
        }
        return new HudPoint(x, y);
    }

    private static int analogDiameter(CompassHudConfig cfg) {
        return (int) Math.ceil(cfg.analogSize);
    }

    private static HudPoint computeDigitalBasePosition(Minecraft client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        int pad = cfg.padding;
        int textW = maxLineWidth(client, lines);
        int textH = client.font.lineHeight * lines.length;

        int boxW = textW + pad * 2;
        int boxH = textH + pad * 2;

        float s = cfg.scale;
        int scaledBoxW = (int) Math.ceil(boxW * s);
        int scaledBoxH = (int) Math.ceil(boxH * s);

        int x;
        int y;
        if (cfg.attachToHotbarCompass && client.player != null) {
            int slotIndex = findHotbarCompassSlot(client.player);
            if (slotIndex >= 0) {
                var attached = computeAttachedCompassPosition(screenW, screenH, cfg, scaledBoxW, scaledBoxH);
                x = attached.x;
                y = attached.y;
            } else {
                x = anchoredX(cfg, screenW, scaledBoxW);
                y = anchoredY(cfg, screenH, scaledBoxH);
            }
        } else {
            x = anchoredX(cfg, screenW, scaledBoxW);
            y = anchoredY(cfg, screenH, scaledBoxH);
        }

        x = clamp(x, 0, Math.max(0, screenW - scaledBoxW));
        y = clamp(y, 0, Math.max(0, screenH - scaledBoxH));

        return new HudPoint(x, y);
    }

    private static boolean shouldRenderPreviewHotbar(CompassHudConfig cfg) {
        return cfg.style == CompassHudConfig.CompassStyle.DIGITAL && cfg.attachToHotbarCompass;
    }

    private static void drawPreviewHotbar(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        int hotbarW = 182;
        int hotbarH = 22;
        int hotbarX = (screenW - hotbarW) / 2;
        int hotbarY = screenH - hotbarH;

        ctx.fill(hotbarX, hotbarY, hotbarX + hotbarW, hotbarY + hotbarH, PREVIEW_HOTBAR_BG);
        ctx.fill(hotbarX, hotbarY, hotbarX + hotbarW, hotbarY + 1, PREVIEW_HOTBAR_BORDER);
        ctx.fill(hotbarX, hotbarY + hotbarH - 1, hotbarX + hotbarW, hotbarY + hotbarH, PREVIEW_HOTBAR_BORDER);
        ctx.fill(hotbarX, hotbarY, hotbarX + 1, hotbarY + hotbarH, PREVIEW_HOTBAR_BORDER);
        ctx.fill(hotbarX + hotbarW - 1, hotbarY, hotbarX + hotbarW, hotbarY + hotbarH, PREVIEW_HOTBAR_BORDER);

        int slotX = hotbarX + 3;
        int slotY = hotbarY + 3;
        int slotSize = 16;
        int slotStep = 20;
        for (int i = 0; i < 9; i++) {
            int x0 = slotX + i * slotStep;
            ctx.fill(x0, slotY, x0 + slotSize, slotY + slotSize, PREVIEW_HOTBAR_SLOT);
        }
    }

    private static int maxLineWidth(Minecraft client, String[] lines) {
        int w = 0;
        for (String s : lines) {
            w = Math.max(w, client.font.width(s));
        }
        return w;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean hasCompassAnywhere(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (containsCompass(inv.getItem(i), 0)) return true;
        }
        // Extra safety: offhand
        return containsCompass(player.getOffhandItem(), 0);
    }

    private static boolean containsCompass(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.COMPASS)) return true;

        // Prevent infinite recursion
        if (depth >= 6) return false;

        // Bundle contents (modern data component)
        if (stack.is(Items.BUNDLE)) {
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null) {
                for (var inside : contents.items()) {
                    if (containsCompass(inside.create(), depth + 1)) return true;
                }
            }
        }

        return false;
    }

    private static int findHotbarCompassSlot(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < 9 && i < inv.getContainerSize(); i++) {
            if (containsCompass(inv.getItem(i), 0)) return i;
        }
        return -1;
    }

    private static int anchoredX(CompassHudConfig cfg, int screenW, int scaledBoxW) {
        return switch (cfg.hAnchor) {
            case LEFT -> 4;
            case CENTER -> (screenW - scaledBoxW) / 2;
            case RIGHT -> screenW - scaledBoxW - 4;
        };
    }

    private static int anchoredY(CompassHudConfig cfg, int screenH, int scaledBoxH) {
        return switch (cfg.vAnchor) {
            case TOP -> 4;
            case CENTER -> (screenH - scaledBoxH) / 2;
            case BOTTOM -> screenH - scaledBoxH - 4;
        };
    }

    private static int anchoredZoneX(CompassHudConfig cfg, int screenW, int boxW) {
        return switch (cfg.zoneHAnchor) {
            case LEFT -> 4;
            case CENTER -> (screenW - boxW) / 2;
            case RIGHT -> screenW - boxW - 4;
        };
    }

    private static int anchoredZoneY(CompassHudConfig cfg, int screenH, int boxH) {
        return switch (cfg.zoneVAnchor) {
            case TOP -> 4;
            case CENTER -> (screenH - boxH) / 2;
            case BOTTOM -> screenH - boxH - 4;
        };
    }

    private static String direction4(float yawDegrees) {
        float yaw = Mth.wrapDegrees(yawDegrees); // -180..180
        int idx = Mth.floor((yaw + 180.0f + 45.0f) / 90.0f) & 3;

        return switch (idx) {
            case 0 -> "N";
            case 1 -> "E";
            case 2 -> "S";
            default -> "W";
        };
    }

    private static String degrees(float yawDegrees) {
        float deg = yawDegrees % 360.0f;
        if (deg < 0.0f) deg += 360.0f;
        return Mth.floor(deg) + "\u00b0";
    }

    private static String direction8(float yawDegrees) {
        float yaw = Mth.wrapDegrees(yawDegrees); // -180..180
        int idx = Mth.floor((yaw + 180.0f + 22.5f) / 45.0f) & 7;

        return switch (idx) {
            case 0 -> "N";
            case 1 -> "NE";
            case 2 -> "E";
            case 3 -> "SE";
            case 4 -> "S";
            case 5 -> "SW";
            case 6 -> "W";
            default -> "NW";
        };
    }

    private static String sampleDirection(CompassHudConfig cfg) {
        return switch (cfg.directionMode) {
            case CARDINAL_8 -> "NW";
            case CARDINAL_4 -> "W";
            case DEGREES -> "360\u00b0";
        };
    }

    private static String zoneLabel(Minecraft client, CompassHudConfig cfg, boolean respectFollow) {
        if (!cfg.displayZoneInHud) return null;
        if (respectFollow && !cfg.zoneFollowsCompass) return null;
        if (client == null || client.player == null || client.level == null) return sampleZone(cfg);
        var border = client.level.getWorldBorder();
        String zoneKey = com.example.globe.util.LatitudeMath.zoneKey(border, client.player.getZ());
        return displayZoneName(zoneKey);
    }

    private static String displayZoneName(String zoneKey) {
        if (zoneKey == null) return "Temperate";
        return switch (zoneKey) {
            case "EQUATOR", "TROPICAL" -> "Tropics";
            case "SUBTROPICAL" -> "Subtropics";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> zoneKey;
        };
    }

    private static void drawText(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, String text, int x, int y, int color) {
        if (cfg.shadow) {
            ctx.text(client.font, Component.literal(text), x, y, color);
        } else {
            ctx.text(client.font, Component.literal(text), x, y, color, false);
        }
    }

    private static void renderDetachedZone(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, boolean isPreview) {
        String zone = zoneLabel(client, cfg, false);
        if (zone == null) return;
        HudBounds zb = computeZoneBounds(client, cfg);
        if (zb == null) return;
        if (isPreview) {
            int border = ANALOG_PREVIEW_BORDER;
            ctx.fill(zb.x, zb.y, zb.x + zb.w, zb.y + 1, border);
            ctx.fill(zb.x, zb.y + zb.h - 1, zb.x + zb.w, zb.y + zb.h, border);
            ctx.fill(zb.x, zb.y, zb.x + 1, zb.y + zb.h, border);
            ctx.fill(zb.x + zb.w - 1, zb.y, zb.x + zb.w, zb.y + zb.h, border);
        }
        int color = cfg.textArgb();
        drawText(ctx, client, cfg, zone, zb.x, zb.y, color);
    }

    // Detached zone label support
    public static HudBounds computeZoneBounds(Minecraft client, CompassHudConfig cfg) {
        String zone = zoneLabel(client, cfg, false);
        if (zone == null) return null;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int w = client.font.width(zone);
        int h = client.font.lineHeight;
        int x = anchoredZoneX(cfg, screenW, w);
        int y = anchoredZoneY(cfg, screenH, h);
        x += cfg.zoneOffsetX;
        y += cfg.zoneOffsetY;
        x = clamp(x, 0, Math.max(0, screenW - w));
        y = clamp(y, 0, Math.max(0, screenH - h));
        return new HudBounds(x, y, w, h);
    }
    
    private record AnalogColors(int face, int ring, int muted, int needle) {}

    private static AnalogColors analogColors(CompassHudConfig cfg) {
        return switch (cfg.analogTheme) {
            case PALE_GOLD -> new AnalogColors(0x233029, 0xFFE5C07B, 0xFFA58C6F, 0xFFDD845A);
            case RED_IVORY -> new AnalogColors(0x292221, 0xFFE3D4C8, 0xFF9E8B83, 0xFFE05B4F);
            case CYAN_STEEL -> new AnalogColors(0x1A232A, 0xFF5CC8FF, 0xFF8FB7CC, 0xFF52E0FF);
            case MINT_BRASS -> new AnalogColors(0x1C2823, 0xFFD4B87A, 0xFF8FA58F, 0xFF6AE6B8);
            // face is plain 0xRRGGBB (alpha re-applied by analogInnerColor); ring/muted/needle are full 0xFF ARGB.
            case OBSIDIAN_RED -> new AnalogColors(0x14110F, 0xFFB0A8A0, 0xFF6E6862, 0xFFE2402E);
            case ARCTIC_BLUE -> new AnalogColors(0x16202B, 0xFFCFE8FF, 0xFF7F9DB5, 0xFF4FC3FF);
            case EMERALD -> new AnalogColors(0x122019, 0xFF7BE0A0, 0xFF6F9C82, 0xFFFFD56A);
            case ROYAL_PURPLE -> new AnalogColors(0x1A1426, 0xFFC9A6F0, 0xFF8C7AA0, 0xFFFFC04D);
            case SUNSET -> new AnalogColors(0x261712, 0xFFF2A65A, 0xFFB07E62, 0xFFFF5E5B);
            case MONOCHROME -> new AnalogColors(0x1B1B1E, 0xFFD8D8DC, 0xFF80808A, 0xFFF2F2F2);
            case CLASSIC_GOLD -> new AnalogColors(ANALOG_FACE_RGB, ANALOG_RING, ANALOG_MUTED, ANALOG_N_COLOR);
            default -> new AnalogColors(ANALOG_FACE_RGB, ANALOG_RING, ANALOG_MUTED, ANALOG_N_COLOR);
        };
    }
}
