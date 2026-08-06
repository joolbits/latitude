package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int DEFAULT_DETACHED_DETAIL_GAP = 4;
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

    private record DigitalContent(String direction, String latitudeSegment, String detailSegment) {
    }

    private record DigitalMetrics(
            int padding,
            int directionWidth,
            int latitudeWidth,
            int detailWidth,
            int textHeight,
            int boxWidth,
            int boxHeight) {
    }

    private CompassHud() {}

    // Keep for compatibility with existing GlobeModClient init call.
    public static void init() {}

    public static void render(GuiGraphics ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        renderInternal(ctx, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), false);
    }

    public static void render(GuiGraphics ctx, int screenW, int screenH) {
        renderInternal(ctx, screenW, screenH, false);
    }

    public static void renderAdjustPreview(GuiGraphics ctx, int screenW, int screenH) {
        renderInternal(ctx, screenW, screenH, true);
    }

    private static void renderInternal(GuiGraphics ctx, int screenW, int screenH, boolean forceVisible) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        var cfg = CompassHudConfig.get();
        boolean studioPreview = client.screen instanceof LatitudeHudStudioScreen;

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

        // Outside a Latitude world there is no latitude to report, and drawing the compass anyway
        // reads as "Latitude is working" while it silently is not — the exact signal that made a
        // failed Latitude world look healthy. The HUD Studio preview keeps rendering (forceVisible).
        if (!forceVisible && !GlobeClientState.isGlobeWorld()) {
            return;
        }

        if (!forceVisible && client.screen != null) {
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
            String latText = analogLatitudeText(client, cfg);
            String locationDetailText = locationDetailLabel(client, cfg, true);
            HudBounds b = computeAnalogBounds(screenW, screenH, client, cfg, latText, locationDetailText);
            renderAnalogAt(ctx, client, cfg, latText, locationDetailText, b.x, b.y, forceVisible);
            if (cfg.hasLocationDetail() && !cfg.zoneFollowsCompass) {
                renderDetachedLocationDetail(ctx, client, cfg, forceVisible);
            }
        } else {
            DigitalContent content = digitalContent(
                    currentDirectionText(client, cfg),
                    latitudeText(client, cfg),
                    locationDetailLabel(client, cfg, true),
                    cfg.compactHud);
            HudBounds b = computeDigitalBounds(screenW, screenH, client, cfg, content, false);
            renderDigitalAt(ctx, client, cfg, content, b.x, b.y, forceVisible);
            if (cfg.hasLocationDetail() && !cfg.zoneFollowsCompass) {
                renderDetachedLocationDetail(ctx, client, cfg, forceVisible);
            }
        }
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg) {
        boolean studioPreview = client.screen instanceof LatitudeHudStudioScreen;
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight(),
                    client,
                    cfg,
                    studioPreview ? analogSampleLatitude(cfg) : analogLatitudeText(client, cfg),
                    studioPreview
                            ? sampleLocationDetail(cfg, true)
                            : locationDetailLabel(client, cfg, true));
        }
        return computeDigitalBounds(
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                client,
                cfg,
                studioPreview ? sampleDigitalContent(cfg) : currentDigitalContent(client, cfg),
                false);
    }

    public static HudPoint computeBasePosition(Minecraft client, CompassHudConfig cfg) {
        boolean studioPreview = client.screen instanceof LatitudeHudStudioScreen;
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBasePosition(
                    client,
                    cfg,
                    studioPreview ? analogSampleLatitude(cfg) : analogLatitudeText(client, cfg),
                    studioPreview
                            ? sampleLocationDetail(cfg, true)
                            : locationDetailLabel(client, cfg, true));
        }
        return computeDigitalBasePosition(
                client,
                cfg,
                studioPreview ? sampleDigitalContent(cfg) : currentDigitalContent(client, cfg));
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg, Component text) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(
                    screenW,
                    screenH,
                    client,
                    cfg,
                    analogSampleLatitude(cfg),
                    sampleLocationDetail(cfg, true));
        }
        return computeBounds(screenW, screenH, client, cfg, new String[]{text.getString()});
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(
                    screenW,
                    screenH,
                    client,
                    cfg,
                    analogSampleLatitude(cfg),
                    sampleLocationDetail(cfg, true));
        }
        return computeBounds(screenW, screenH, client, cfg, lines);
    }

    private static HudBounds computePreviewBounds(Minecraft client, CompassHudConfig cfg) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(
                    screenW,
                    screenH,
                    client,
                    cfg,
                    analogSampleLatitude(cfg),
                    sampleLocationDetail(cfg, true));
        }
        return computeDigitalBounds(screenW, screenH, client, cfg, sampleDigitalContent(cfg), true);
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

    public static void renderPreview(GuiGraphics ctx, Minecraft client, CompassHudConfig cfg, int x, int y) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            renderAnalogAt(
                    ctx,
                    client,
                    cfg,
                    analogSampleLatitude(cfg),
                    sampleLocationDetail(cfg, true),
                    x,
                    y,
                    true);
            if (cfg.hasLocationDetail() && !cfg.zoneFollowsCompass) {
                renderDetachedLocationDetail(ctx, client, cfg, true);
            }
        } else {
            renderDigitalAt(ctx, client, cfg, sampleDigitalContent(cfg), x, y, true);
            if (cfg.hasLocationDetail() && !cfg.zoneFollowsCompass) {
                renderDetachedLocationDetail(ctx, client, cfg, true);
            }
        }
    }

    private static HudBounds computeDigitalBounds(
            int screenW,
            int screenH,
            Minecraft client,
            CompassHudConfig cfg,
            DigitalContent content,
            boolean previewAttachToHotbar) {
        DigitalMetrics metrics = digitalMetrics(client, cfg, content);
        int boxW = metrics.boxWidth();
        int boxH = metrics.boxHeight();

        int x;
        int y;
        if (cfg.attachToHotbarCompass
                && (previewAttachToHotbar || client.player != null && findHotbarCompassSlot(client.player) >= 0)) {
            HudPoint attached = computeAttachedCompassPosition(screenW, screenH, cfg, boxW, boxH);
            x = attached.x();
            y = attached.y();
        } else {
            x = anchoredX(cfg, screenW, boxW);
            y = anchoredY(cfg, screenH, boxH);
        }

        x += cfg.offsetX;
        y += cfg.offsetY;
        x = clamp(x, 0, Math.max(0, screenW - boxW));
        y = clamp(y, 0, Math.max(0, screenH - boxH));
        return new HudBounds(x, y, boxW, boxH);
    }

    private static void renderDigitalAt(
            GuiGraphics ctx,
            Minecraft client,
            CompassHudConfig cfg,
            DigitalContent content,
            int x,
            int y,
            boolean isPreview) {
        DigitalMetrics metrics = digitalMetrics(client, cfg, content);
        int boxW = metrics.boxWidth();
        int boxH = metrics.boxHeight();

        if (cfg.showBackground || isPreview) {
            int bg = cfg.showBackground
                    ? cfg.backgroundArgb()
                    : (160 << 24) | (cfg.backgroundRgb & 0xFFFFFF);
            ctx.fill(x, y, x + boxW, y + boxH, bg);
            if (isPreview) {
                int border = 0x55FFFFFF;
                ctx.fill(x, y, x + boxW, y + 1, border);
                ctx.fill(x, y + boxH - 1, x + boxW, y + boxH, border);
                ctx.fill(x, y, x + 1, y + boxH, border);
                ctx.fill(x + boxW - 1, y, x + boxW, y + boxH, border);
            }
        }

        int color = cfg.textArgb();
        int textX = x + metrics.padding();
        int directionY = y + (boxH - scaledTextHeight(client, cfg.scale)) / 2;
        drawScaledText(ctx, client, cfg, content.direction(), textX, directionY, color, cfg.scale);
        textX += metrics.directionWidth();

        int locationY = y + (boxH - scaledTextHeight(client, cfg.locationTextScale)) / 2;
        if (content.latitudeSegment() != null) {
            drawScaledText(
                    ctx,
                    client,
                    cfg,
                    content.latitudeSegment(),
                    textX,
                    locationY,
                    color,
                    cfg.locationTextScale);
            textX += metrics.latitudeWidth();
        }
        if (content.detailSegment() != null) {
            drawScaledText(
                    ctx,
                    client,
                    cfg,
                    content.detailSegment(),
                    textX,
                    locationY,
                    color,
                    cfg.locationTextScale);
        }
    }

    private static void renderAnalogAt(
            GuiGraphics ctx,
            Minecraft client,
            CompassHudConfig cfg,
            String latText,
            String locationDetailText,
            int x,
            int y,
            boolean isPreview) {
        int diameter = analogDiameter(cfg);
        int radius = diameter / 2;
        int textHeight = (latText != null && !latText.isEmpty())
                        || (locationDetailText != null && !locationDetailText.isEmpty())
                ? scaledTextHeight(client, cfg.locationTextScale)
                : 0;
        int boxHeight = Math.max(diameter, textHeight);
        int compassY = y + (boxHeight - diameter) / 2;
        int cx = x + radius;
        int cy = compassY + radius;

        float yaw = client.player != null ? client.player.getYRot() : -180.0f;
        double angle = Math.toRadians(Mth.wrapDegrees(yaw + 180.0f));

        if (isPreview
                && client.screen instanceof LatitudeHudStudioScreen studio
                && studio.faceOpacityAdjustActive()) {
            drawTransparencyCheckerboard(ctx, x, compassY, diameter, diameter);
        }
        drawAnalogCompass(ctx, cfg, cx, cy, radius, angle);

        if (isPreview) {
            int boxW = diameter;
            int boxH = diameter;
            int extraTextW = 0;
            int extraTextH = 0;
            if (latText != null && !latText.isEmpty()) {
                extraTextW += ANALOG_LAT_GAP + scaledTextWidth(client, latText, cfg.locationTextScale);
                extraTextH = Math.max(extraTextH, scaledTextHeight(client, cfg.locationTextScale));
            }
            if (locationDetailText != null && !locationDetailText.isEmpty()) {
                extraTextW += analogLocationGap(cfg, latText);
                extraTextW += scaledTextWidth(client, locationDetailText, cfg.locationTextScale);
                extraTextH = Math.max(extraTextH, scaledTextHeight(client, cfg.locationTextScale));
            }
            boxW += extraTextW;
            if (extraTextH > 0) boxH = Math.max(boxH, extraTextH);
            ctx.fill(x, y, x + boxW, y + 1, ANALOG_PREVIEW_BORDER);
            ctx.fill(x, y + boxH - 1, x + boxW, y + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(x, y, x + 1, y + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(x + boxW - 1, y, x + boxW, y + boxH, ANALOG_PREVIEW_BORDER);
        }

        int textX = x + diameter + ANALOG_LAT_GAP;
        int textY = y + (boxHeight - scaledTextHeight(client, cfg.locationTextScale)) / 2;
        int color = cfg.textArgb();
        if (latText != null && !latText.isEmpty()) {
            drawScaledText(ctx, client, cfg, latText, textX, textY, color, cfg.locationTextScale);
            textX += scaledTextWidth(client, latText, cfg.locationTextScale) + (cfg.compactHud ? 1 : 6);
        }
        if (locationDetailText != null && !locationDetailText.isEmpty()) {
            drawScaledText(ctx, client, cfg, locationDetailText, textX, textY, color, cfg.locationTextScale);
        }
    }

    private static void drawAnalogCompass(GuiGraphics ctx, CompassHudConfig cfg, int cx, int cy, int radius, double angle) {
        var colors = analogColors(cfg);
        int innerRadius = radius - 2;
        int faceColor = analogInnerColor(cfg, colors.face());
        for (int dy = -radius; dy <= radius; dy++) {
            int half = analogSpanHalf(radius, dy);
            if (half < 0) continue;
            int py = cy + dy;
            int halfIn = Math.abs(dy) <= innerRadius
                    ? analogSpanHalf(innerRadius, dy)
                    : -1;
            if (halfIn < 0) {
                ctx.fill(cx - half, py, cx + half + 1, py + 1, colors.ring());
            } else {
                ctx.fill(cx - half, py, cx - halfIn, py + 1, colors.ring());
                ctx.fill(cx + halfIn + 1, py, cx + half + 1, py + 1, colors.ring());
                ctx.fill(cx - halfIn, py, cx + halfIn + 1, py + 1, faceColor);
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
        float nScale = Mth.clamp(radius / 24.0f, 0.4f, 1.0f);
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate((float) (cx + 1), (float) (cy - radius + 2 + tickLen + 1));
        pose.scale(nScale, nScale);
        ctx.drawString(Minecraft.getInstance().font, nLabel, -nW / 2, 0, colors.needle(), true);
        pose.popMatrix();

        int needleLen = radius - 4;
        int nx = cx + (int) Math.round(Math.sin(angle) * needleLen);
        int ny = cy - (int) Math.round(Math.cos(angle) * needleLen);
        drawLine(ctx, cx, cy, nx, ny, colors.needle());

        int sx = cx - (int) Math.round(Math.sin(angle) * (needleLen * 0.6));
        int sy = cy + (int) Math.round(Math.cos(angle) * (needleLen * 0.6));
        drawLine(ctx, cx, cy, sx, sy, colors.ring());

        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, colors.ring());
    }

    private static int analogSpanHalf(int radius, int dy) {
        int remaining = radius * radius - dy * dy;
        return remaining < 0 ? -1 : (int) Math.sqrt(remaining);
    }

    private static int analogInnerColor(CompassHudConfig cfg, int faceRgb) {
        int a = Mth.clamp((int) Math.round(cfg.analogInnerAlpha * 255.0f), 0, 255);
        return (a << 24) | (faceRgb & 0xFFFFFF);
    }

    private static void drawTransparencyCheckerboard(GuiGraphics ctx, int x, int y, int w, int h) {
        int cell = Math.max(3, Math.min(w, h) / 6);
        int light = 0xFFBFBFBF;
        int dark = 0xFF6E6E6E;
        for (int gy = 0; gy < h; gy += cell) {
            for (int gx = 0; gx < w; gx += cell) {
                boolean isLight = (((gx / cell) + (gy / cell)) & 1) == 0;
                int x0 = x + gx;
                int y0 = y + gy;
                int x1 = Math.min(x + w, x0 + cell);
                int y1 = Math.min(y + h, y0 + cell);
                ctx.fill(x0, y0, x1, y1, isLight ? light : dark);
            }
        }
    }

    private static void drawLine(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color) {
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

    private static DigitalContent sampleDigitalContent(CompassHudConfig cfg) {
        String lat = Boolean.TRUE.equals(cfg.showLatitude) ? "1\u00b0S" : null;
        return digitalContent(
                sampleDirection(cfg),
                lat,
                sampleLocationDetail(cfg, true),
                cfg.compactHud);
    }

    private static DigitalContent currentDigitalContent(
            Minecraft client,
            CompassHudConfig cfg) {
        return digitalContent(
                currentDirectionText(client, cfg),
                latitudeText(client, cfg),
                locationDetailLabel(client, cfg, true),
                cfg.compactHud);
    }

    private static String currentDirectionText(
            Minecraft client,
            CompassHudConfig cfg) {
        if (client.player == null) {
            return sampleDirection(cfg);
        }
        return switch (cfg.directionMode) {
            case CARDINAL_8 -> direction8(client.player.getYRot());
            case CARDINAL_4 -> direction4(client.player.getYRot());
            case DEGREES -> degrees(client.player.getYRot());
        };
    }

    private static DigitalContent digitalContent(
            String direction,
            String latitude,
            String locationDetail,
            boolean compact) {
        String separator = compact ? " " : " \u00b7 ";
        String latitudeSegment = latitude == null ? null : separator + latitude;
        String detailSegment = locationDetail == null
                ? null
                : (latitude == null ? separator : (compact ? " " : " \u00b7 ")) + locationDetail;
        return new DigitalContent(direction, latitudeSegment, detailSegment);
    }

    private static DigitalMetrics digitalMetrics(
            Minecraft client,
            CompassHudConfig cfg,
            DigitalContent content) {
        int padding = (int) Math.ceil(cfg.padding * cfg.scale);
        int directionWidth = scaledTextWidth(client, content.direction(), cfg.scale);
        int latitudeWidth = scaledTextWidth(client, content.latitudeSegment(), cfg.locationTextScale);
        int detailWidth = scaledTextWidth(client, content.detailSegment(), cfg.locationTextScale);
        boolean hasLocationText =
                content.latitudeSegment() != null || content.detailSegment() != null;
        int textHeight = HudTextLayoutPolicy.combinedTextHeight(
                client.font.lineHeight,
                cfg.scale,
                cfg.locationTextScale,
                hasLocationText);
        int boxWidth = HudTextLayoutPolicy.digitalBoxWidth(
                cfg.padding,
                cfg.scale,
                client.font.width(content.direction()),
                content.latitudeSegment() == null ? 0 : client.font.width(content.latitudeSegment()),
                content.detailSegment() == null ? 0 : client.font.width(content.detailSegment()),
                cfg.locationTextScale);
        int boxHeight = padding * 2 + textHeight;
        return new DigitalMetrics(
                padding,
                directionWidth,
                latitudeWidth,
                detailWidth,
                textHeight,
                boxWidth,
                boxHeight);
    }

    private static String analogSampleLatitude(CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.analogShowLatitude)) return null;
        return "1\u00b0S";
    }

    // The preview latitude is 1°S, so Plains + Tropical is a coherent biome/zone sample.
    private static String sampleLocationDetail(CompassHudConfig cfg, boolean respectFollow) {
        if (respectFollow && !cfg.zoneFollowsCompass) {
            return null;
        }
        String previewBiome = LocationDetailPolicy.studioPreviewBiomeLabel(
                "minecraft:plains",
                cfg.showCustomBiomeSource);
        return LocationDetailPolicy.compose(cfg.locationDetailMode(), previewBiome, "Tropical");
    }

    private static String analogLatitudeText(Minecraft client, CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.analogShowLatitude)) return null;
        if (client.player == null || client.level == null) return analogSampleLatitude(cfg);
        return LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.level.getWorldBorder());
    }

    private static String latitudeText(Minecraft client, CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.showLatitude)) return null;
        if (client.player == null || client.level == null) return "0\u00b0";
        return LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.level.getWorldBorder());
    }

    private static HudPoint computeAnalogBasePosition(
            Minecraft client,
            CompassHudConfig cfg,
            String latText,
            String locationDetailText) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        int diameter = analogDiameter(cfg);
        int boxW = diameter;
        int boxH = diameter;
        if (latText != null && !latText.isEmpty()) {
            boxW += ANALOG_LAT_GAP + scaledTextWidth(client, latText, cfg.locationTextScale);
            boxH = Math.max(boxH, scaledTextHeight(client, cfg.locationTextScale));
        }
        if (locationDetailText != null && !locationDetailText.isEmpty()) {
            boxW += analogLocationGap(cfg, latText) + scaledTextWidth(client, locationDetailText, cfg.locationTextScale);
            boxH = Math.max(boxH, scaledTextHeight(client, cfg.locationTextScale));
        }

        int x;
        int y;
        x = anchoredX(cfg, screenW, boxW);
        y = anchoredY(cfg, screenH, boxH);

        x = clamp(x, 0, Math.max(0, screenW - boxW));
        y = clamp(y, 0, Math.max(0, screenH - boxH));

        return new HudPoint(x, y);
    }

    private static HudBounds computeAnalogBounds(
            int screenW,
            int screenH,
            Minecraft client,
            CompassHudConfig cfg,
            String latText,
            String locationDetailText) {
        int diameter = analogDiameter(cfg);
        int boxW = diameter;
        int boxH = diameter;
        if (latText != null && !latText.isEmpty()) {
            boxW += ANALOG_LAT_GAP + scaledTextWidth(client, latText, cfg.locationTextScale);
            boxH = Math.max(boxH, scaledTextHeight(client, cfg.locationTextScale));
        }
        if (locationDetailText != null && !locationDetailText.isEmpty()) {
            boxW += analogLocationGap(cfg, latText) + scaledTextWidth(client, locationDetailText, cfg.locationTextScale);
            boxH = Math.max(boxH, scaledTextHeight(client, cfg.locationTextScale));
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

    private static int analogLocationGap(CompassHudConfig cfg, String latText) {
        return latText == null || latText.isEmpty()
                ? ANALOG_LAT_GAP
                : (cfg.compactHud ? 1 : 6);
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

    private static HudPoint computeDigitalBasePosition(
            Minecraft client,
            CompassHudConfig cfg,
            DigitalContent content) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        DigitalMetrics metrics = digitalMetrics(client, cfg, content);
        int boxW = metrics.boxWidth();
        int boxH = metrics.boxHeight();

        int x;
        int y;
        if (cfg.attachToHotbarCompass
                && client.player != null
                && findHotbarCompassSlot(client.player) >= 0) {
            HudPoint attached = computeAttachedCompassPosition(screenW, screenH, cfg, boxW, boxH);
            x = attached.x();
            y = attached.y();
        } else {
            x = anchoredX(cfg, screenW, boxW);
            y = anchoredY(cfg, screenH, boxH);
        }

        x = clamp(x, 0, Math.max(0, screenW - boxW));
        y = clamp(y, 0, Math.max(0, screenH - boxH));
        return new HudPoint(x, y);
    }

    private static boolean shouldRenderPreviewHotbar(CompassHudConfig cfg) {
        return cfg.style == CompassHudConfig.CompassStyle.DIGITAL && cfg.attachToHotbarCompass;
    }

    private static void drawPreviewHotbar(GuiGraphics ctx, int screenW, int screenH) {
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
                    if (containsCompass(inside, depth + 1)) return true;
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

    private static String locationDetailLabel(
            Minecraft client,
            CompassHudConfig cfg,
            boolean respectFollow) {
        if (!cfg.hasLocationDetail()) return null;
        if (respectFollow && !cfg.zoneFollowsCompass) return null;
        if (client == null || client.player == null || client.level == null) {
            return sampleLocationDetail(cfg, false);
        }

        var border = client.level.getWorldBorder();
        String zoneKey = com.example.globe.util.LatitudeMath.zoneKey(border, client.player.getZ());
        return LocationDetailPolicy.compose(
                cfg.locationDetailMode(),
                biomeLabel(client, cfg),
                displayZoneName(zoneKey));
    }

    private static String biomeLabel(Minecraft client, CompassHudConfig cfg) {
        var biome = client.level.getBiome(client.player.blockPosition());
        return biome.unwrapKey()
                .map(key -> LocationDetailPolicy.biomeLabel(
                        key.identifier().toString(),
                        cfg.showCustomBiomeSource))
                .orElse("Unknown");
    }

    private static String displayZoneName(String zoneKey) {
        if (zoneKey == null) return "Temperate";
        return switch (zoneKey) {
            case "EQUATOR", "TROPICAL" -> "Tropical";
            case "SUBTROPICAL" -> "Subtropical";
            case "TEMPERATE" -> "Temperate";
            case "SUBPOLAR" -> "Subpolar";
            case "POLAR" -> "Polar";
            default -> zoneKey;
        };
    }

    private static void drawText(GuiGraphics ctx, Minecraft client, CompassHudConfig cfg, String text, int x, int y, int color) {
        if (cfg.shadow) {
            ctx.drawString(client.font, Component.literal(text), x, y, color);
        } else {
            ctx.drawString(client.font, Component.literal(text), x, y, color, false);
        }
    }

    private static void drawScaledText(
            GuiGraphics ctx,
            Minecraft client,
            CompassHudConfig cfg,
            String text,
            int x,
            int y,
            int color,
            float scale) {
        var pose = ctx.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scale, scale);
            drawText(ctx, client, cfg, text, 0, 0, color);
        } finally {
            pose.popMatrix();
        }
    }

    private static int scaledTextWidth(Minecraft client, String text, float scale) {
        return text == null || text.isEmpty()
                ? 0
                : HudTextLayoutPolicy.scaledPixels(client.font.width(text), scale);
    }

    private static int scaledTextHeight(Minecraft client, float scale) {
        return HudTextLayoutPolicy.scaledPixels(client.font.lineHeight, scale);
    }

    private static void renderDetachedLocationDetail(
            GuiGraphics ctx,
            Minecraft client,
            CompassHudConfig cfg,
            boolean isPreview) {
        String locationDetail = locationDetailLabel(client, cfg, false);
        if (locationDetail == null) return;
        HudBounds detailBounds = computeLocationDetailBounds(client, cfg);
        if (detailBounds == null) return;
        if (isPreview) {
            int border = ANALOG_PREVIEW_BORDER;
            ctx.fill(
                    detailBounds.x,
                    detailBounds.y,
                    detailBounds.x + detailBounds.w,
                    detailBounds.y + 1,
                    border);
            ctx.fill(
                    detailBounds.x,
                    detailBounds.y + detailBounds.h - 1,
                    detailBounds.x + detailBounds.w,
                    detailBounds.y + detailBounds.h,
                    border);
            ctx.fill(
                    detailBounds.x,
                    detailBounds.y,
                    detailBounds.x + 1,
                    detailBounds.y + detailBounds.h,
                    border);
            ctx.fill(
                    detailBounds.x + detailBounds.w - 1,
                    detailBounds.y,
                    detailBounds.x + detailBounds.w,
                    detailBounds.y + detailBounds.h,
                    border);
        }
        int color = cfg.textArgb();
        drawScaledText(
                ctx,
                client,
                cfg,
                locationDetail,
                detailBounds.x,
                detailBounds.y,
                color,
                cfg.locationTextScale);
    }

    // Detached location detail uses the legacy zone placement fields as one combined unit.
    public static HudBounds computeLocationDetailBounds(
            Minecraft client,
            CompassHudConfig cfg) {
        String locationDetail = locationDetailLabel(client, cfg, false);
        if (locationDetail == null) return null;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int w = scaledTextWidth(client, locationDetail, cfg.locationTextScale);
        int h = scaledTextHeight(client, cfg.locationTextScale);
        int x = anchoredZoneX(cfg, screenW, w);
        int y = anchoredZoneY(cfg, screenH, h);
        x += cfg.zoneOffsetX;
        y += cfg.zoneOffsetY;
        x = clamp(x, 0, Math.max(0, screenW - w));
        y = clamp(y, 0, Math.max(0, screenH - h));
        HudBounds detailBounds = new HudBounds(x, y, w, h);
        if (!isPristineDefaultDetachedPlacement(cfg)) {
            return detailBounds;
        }

        HudBounds compassBounds = computeBounds(client, cfg);
        return moveDefaultDetachedDetailOutsideCompass(
                detailBounds,
                compassBounds,
                screenH);
    }

    private static boolean isPristineDefaultDetachedPlacement(CompassHudConfig cfg) {
        return !cfg.zoneFollowsCompass
                && cfg.zoneHAnchor == CompassHudConfig.HAnchor.CENTER
                && cfg.zoneVAnchor == CompassHudConfig.VAnchor.TOP
                && cfg.zoneOffsetX == 0
                && cfg.zoneOffsetY == 0;
    }

    private static HudBounds moveDefaultDetachedDetailOutsideCompass(
            HudBounds detailBounds,
            HudBounds compassBounds,
            int screenH) {
        int movedY = HudTextLayoutPolicy.movePristineDetachedY(
                detailBounds.x(),
                detailBounds.y(),
                detailBounds.w(),
                detailBounds.h(),
                compassBounds.x(),
                compassBounds.y(),
                compassBounds.w(),
                compassBounds.h(),
                screenH,
                DEFAULT_DETACHED_DETAIL_GAP);
        return movedY == detailBounds.y()
                ? detailBounds
                : new HudBounds(
                        detailBounds.x(),
                        movedY,
                        detailBounds.w(),
                        detailBounds.h());
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
