package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import com.example.globe.util.BiomeSamplerTools;

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
        ensureLayoutMigrated(client, cfg);
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
            String latText = cfg.coordsFollowsCompass ? analogLatLonText(client, cfg) : null;
            String zoneText = attachedZoneBiomeLive(client, cfg);
            HudPoint dial = computeAnalogDialPos(screenW, screenH, client, cfg, latText, zoneText);
            renderAnalogAt(ctx, client, cfg, latText, zoneText, dial.x(), dial.y(), forceVisible);
            if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
                renderDetachedZone(ctx, client, cfg, forceVisible);
            }
            if (cfg.displayBiomeInHud && !cfg.biomeFollowsCompass) {
                renderDetachedBiome(ctx, client, cfg, forceVisible);
            }
            if (!cfg.coordsFollowsCompass) {
                renderDetachedCoords(ctx, client, cfg, forceVisible);
            }
        } else {
            String directionText = liveDirection(client, cfg);

            String latLonText = cfg.coordsFollowsCompass ? joinLatLon(latitudeText(client, cfg), longitudeText(client, cfg)) : null;
            String hudText = buildDigitalLine(directionText, latLonText, attachedZoneBiomeLive(client, cfg), cfg.compactHud);

            String[] lines = new String[]{hudText};
            HudBounds b = computeBounds(screenW, screenH, client, cfg, lines);
            renderDigitalAt(ctx, client, cfg, lines, b.x, b.y, forceVisible);
            if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
                renderDetachedZone(ctx, client, cfg, forceVisible);
            }
            if (cfg.displayBiomeInHud && !cfg.biomeFollowsCompass) {
                renderDetachedBiome(ctx, client, cfg, forceVisible);
            }
            if (!cfg.coordsFollowsCompass) {
                renderDetachedCoords(ctx, client, cfg, forceVisible);
            }
        }
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), client, cfg, cfg.coordsFollowsCompass ? analogSampleLatLon(cfg) : null, attachedZoneBiomeSample(cfg));
        }
        return computeBounds(client, cfg, sampleLines(cfg));
    }

    public static HudPoint computeBasePosition(Minecraft client, CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBasePosition(client, cfg, cfg.coordsFollowsCompass ? analogSampleLatLon(cfg) : null, attachedZoneBiomeSample(cfg));
        }
        return computeDigitalBasePosition(client, cfg, sampleLines(cfg));
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg, Component text) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, cfg.coordsFollowsCompass ? analogSampleLatLon(cfg) : null, attachedZoneBiomeSample(cfg));
        }
        return computeBounds(screenW, screenH, client, cfg, new String[]{text.getString()});
    }

    public static HudBounds computeBounds(Minecraft client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, cfg.coordsFollowsCompass ? analogSampleLatLon(cfg) : null, attachedZoneBiomeSample(cfg));
        }
        return computeBounds(screenW, screenH, client, cfg, lines);
    }

    private static HudBounds computePreviewBounds(Minecraft client, CompassHudConfig cfg) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, cfg.coordsFollowsCompass ? analogSampleLatLon(cfg) : null, attachedZoneBiomeSample(cfg));
        }
        return computeDigitalBounds(screenW, screenH, client, cfg, sampleLines(cfg), true);
    }

    private static HudBounds computeBounds(int screenW, int screenH, Minecraft client, CompassHudConfig cfg, String[] lines) {
        return computeDigitalBounds(screenW, screenH, client, cfg, lines, false);
    }

    private static HudBounds computeDigitalBounds(int screenW, int screenH, Minecraft client, CompassHudConfig cfg, String[] lines, boolean previewAttachToHotbar) {

        int pad = cfg.padding;
        int textW = measuredLineWidth(client, cfg, lines);
        int textH = client.font.lineHeight * lines.length;

        int boxW = textW + pad * 2;
        int boxH = textH + pad * 2;

        float s = cfg.scale;
        int scaledBoxW = (int) Math.ceil(boxW * s);
        int scaledBoxH = (int) Math.ceil(boxH * s);

        int x;
        int y;
        if (cfg.dockMode == CompassHudConfig.DockMode.HOTBAR_RIGHT) {
            // Pin & Grow v1 hotbar dock (design Pillar 1b): position is a pure function of screen
            // geometry, growth is structurally away from the hotbar. Digital has no stacked/shrunk shape,
            // so those rungs reuse the beside box and the ladder falls through to LIFTED when the right
            // side can't fit it. (The old attach centered/side-teleported and ignored the offhand slot and
            // hotbar attack indicator -- the audited clipping class.)
            var dockBoxes = new com.example.globe.core.ui.HudLayoutMath.DockBoxes(
                    scaledBoxW, scaledBoxH, scaledBoxW, scaledBoxH, scaledBoxW, scaledBoxH);
            var dockResult = com.example.globe.core.ui.HudLayoutMath.dock(
                    screenW, screenH, dockBoxes, dockOffhandOnRight(client), dockAttackIndicatorOnHotbar(client));
            x = dockResult.x();
            y = dockResult.y();
        } else {
            x = pinPlaceX(cfg.hAnchor, cfg.offXFrac, cfg.growH, scaledBoxW, screenW);
            y = pinPlaceY(cfg.vAnchor, cfg.offYFrac, cfg.growV, scaledBoxH, screenH);
        }

        x = clamp(x, 0, Math.max(0, screenW - scaledBoxW));
        y = clamp(y, 0, Math.max(0, screenH - scaledBoxH));

        return new HudBounds(x, y, scaledBoxW, scaledBoxH);
    }

    public static void renderPreview(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, int x, int y) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            renderAnalogAt(ctx, client, cfg, cfg.coordsFollowsCompass ? analogSampleLatLon(cfg) : null, attachedZoneBiomeSample(cfg), x, y, true);
        } else {
            renderDigitalAt(ctx, client, cfg, sampleLines(cfg), x, y, true);
        }
        if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
            renderDetachedZone(ctx, client, cfg, true);
        }
        if (cfg.displayBiomeInHud && !cfg.biomeFollowsCompass) {
            renderDetachedBiome(ctx, client, cfg, true);
        }
        if (!cfg.coordsFollowsCompass) {
            renderDetachedCoords(ctx, client, cfg, true);
        }
        drawPinMarkers(ctx, client, cfg);
    }

    /** U-B pin visualization: a small gold crosshair at each element's PIN, so "what am I dragging" is a
     *  visible point, not a guess. Skipped for the compass while docked (the dock computes its own pin). */
    private static void drawPinMarkers(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        if (cfg.dockMode == CompassHudConfig.DockMode.NONE) {
            drawPin(ctx,
                    com.example.globe.core.ui.HudLayoutMath.pinX(gridCol(cfg.hAnchor), cfg.offXFrac, screenW),
                    com.example.globe.core.ui.HudLayoutMath.pinY(gridRow(cfg.vAnchor), cfg.offYFrac, screenH));
        }
        if (cfg.displayZoneInHud && !cfg.zoneFollowsCompass) {
            drawPin(ctx,
                    com.example.globe.core.ui.HudLayoutMath.pinX(gridCol(cfg.zoneHAnchor), cfg.zoneOffXFrac, screenW),
                    com.example.globe.core.ui.HudLayoutMath.pinY(gridRow(cfg.zoneVAnchor), cfg.zoneOffYFrac, screenH));
        }
        if (cfg.displayBiomeInHud && !cfg.biomeFollowsCompass) {
            drawPin(ctx,
                    com.example.globe.core.ui.HudLayoutMath.pinX(gridCol(cfg.biomeHAnchor), cfg.biomeOffXFrac, screenW),
                    com.example.globe.core.ui.HudLayoutMath.pinY(gridRow(cfg.biomeVAnchor), cfg.biomeOffYFrac, screenH));
        }
        if (!cfg.coordsFollowsCompass) {
            drawPin(ctx,
                    com.example.globe.core.ui.HudLayoutMath.pinX(gridCol(cfg.coordsHAnchor), cfg.coordsOffXFrac, screenW),
                    com.example.globe.core.ui.HudLayoutMath.pinY(gridRow(cfg.coordsVAnchor), cfg.coordsOffYFrac, screenH));
        }
    }

    private static void drawPin(GuiGraphicsExtractor ctx, int px, int py) {
        int gold = 0xFFE8B64A;
        ctx.fill(px - 3, py, px + 4, py + 1, gold);
        ctx.fill(px, py - 3, px + 1, py + 4, gold);
    }

    private static void renderDigitalAt(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, String[] lines, int x, int y, boolean isPreview) {
        int pad = cfg.padding;
        int textW = measuredLineWidth(client, cfg, lines);
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
                if (cfg.textRainbow) {
                    int alphaByte = (color >>> 24) & 0xFF;
                    drawRainbowLeftAligned(ctx, client.font, lines[i], tx, lineY, cfg.shadow, alphaByte);
                    continue;
                }
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

        CompassDialRenderer.draw(ctx, client.font, cfg, cx, cy, radius, angle, yaw, analogColors(cfg));

        int screenWForText = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int totalTextW = analogAttachedTextWidth(client, cfg, latText, zoneText);
        boolean textBelow = totalTextW > 0 && analogTextBelow(screenWForText, x, diameter, totalTextW);
        int lineH = client.font.lineHeight;
        int contentH = CompassDialRenderer.lookContentHeight(cfg, diameter);
        int contentTop = y + (diameter - contentH) / 2;

        if (isPreview) {
            // Border mirrors computeAnalogBounds' union exactly (same inputs, same math) so the Studio
            // hitbox and the visible outline can't disagree.
            int bx = x;
            int by = contentTop;
            int boxW;
            int boxH;
            if (totalTextW <= 0) {
                boxW = diameter;
                boxH = contentH;
            } else if (textBelow) {
                int textXb = clamp(cx - totalTextW / 2, 4, Math.max(4, screenWForText - 4 - totalTextW));
                bx = Math.min(x, textXb);
                boxW = Math.max(x + diameter, textXb + totalTextW) - bx;
                boxH = contentH + 2 + lineH;
            } else {
                boxW = diameter + ANALOG_LAT_GAP + totalTextW;
                boxH = Math.max(contentH, lineH);
            }
            ctx.fill(bx, by, bx + boxW, by + 1, ANALOG_PREVIEW_BORDER);
            ctx.fill(bx, by + boxH - 1, bx + boxW, by + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(bx, by, bx + 1, by + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(bx + boxW - 1, by, bx + boxW, by + boxH, ANALOG_PREVIEW_BORDER);
        }

        int textX;
        int textY;
        if (textBelow) {
            // Pin & Grow overflow guard: the dial never moves for text; when rightward text would run
            // off-screen it wraps BELOW the dial instead (deterministic, screen-geometry-keyed).
            // Hangs off the CONTENT bottom (== dial-box bottom for every look but TAPE), so the tape's
            // wrapped text doesn't strand a phantom-margin gap below the strip.
            textX = clamp(cx - totalTextW / 2, 4, Math.max(4, screenWForText - 4 - totalTextW));
            textY = contentTop + contentH + 2;
        } else {
            textX = x + diameter + ANALOG_LAT_GAP;
            textY = cy - lineH / 2;
        }
        int color = cfg.textArgb();
        if (latText != null && !latText.isEmpty()) {
            drawText(ctx, client, cfg, latText, textX, textY, color);
            textX += client.font.width(latText) + (cfg.compactHud ? 1 : 6);
        }
        if (zoneText != null && !zoneText.isEmpty()) {
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

    private static String[] sampleLines(CompassHudConfig cfg) {
        String dir = sampleDirection(cfg);
        String latLon = cfg.coordsFollowsCompass ? coordsSample(cfg) : null;
        String zoneBiome = attachedZoneBiomeSample(cfg);
        return new String[]{buildDigitalLine(dir, latLon, zoneBiome, cfg.compactHud)};
    }

    private static String analogSampleLatLon(CompassHudConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        if (previewTextSource == PreviewTextSource.LIVE && mc != null && mc.player != null && mc.level != null) {
            return analogLatLonText(mc, cfg);
        }
        boolean widest = previewTextSource == PreviewTextSource.LONGEST;
        String lat = Boolean.TRUE.equals(cfg.analogShowLatitude) ? (widest ? "89\u00b0S" : "1\u00b0S") : null;
        String lon = Boolean.TRUE.equals(cfg.analogShowLongitude) ? (widest ? "179\u00b0W" : "15\u00b0E") : null;
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
    /**
     * U-B truthful preview: what text the Studio preview measures and draws. SAMPLE is the legacy short
     * placeholder (the audited source of "the Studio lied about placement"); LONGEST is the honest
     * worst case (default); LIVE uses the real in-world values when available.
     */
    public enum PreviewTextSource { SAMPLE, LONGEST, LIVE }

    /** Studio-session state, deliberately not persisted (an editor view option, not a player setting). */
    public static PreviewTextSource previewTextSource = PreviewTextSource.LONGEST;

    private static String sampleZone(CompassHudConfig cfg) {
        if (!cfg.displayZoneInHud) return null;
        Minecraft mc = Minecraft.getInstance();
        return switch (previewTextSource) {
            case SAMPLE -> "Tropics";
            case LONGEST -> "Subtropics";
            case LIVE -> {
                if (mc != null && mc.player != null && mc.level != null) {
                    yield displayZoneName(com.example.globe.util.LatitudeMath.zoneKey(mc.level.getWorldBorder(), mc.player.getZ()));
                }
                yield "Subtropics";
            }
        };
    }

    private static String analogLatLonText(Minecraft client, CompassHudConfig cfg) {
        boolean live = client.player != null && client.level != null;
        if (live) refreshLivePosText(client, cfg);
        String lat = Boolean.TRUE.equals(cfg.analogShowLatitude) ? (live ? liveLatStr : "1\u00b0S") : null;
        String lon = Boolean.TRUE.equals(cfg.analogShowLongitude) ? (live ? liveLonStr : "15\u00b0E") : null;
        return joinLatLon(lat, lon);
    }

    private static String latitudeText(Minecraft client, CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.showLatitude)) return null;
        if (client.player == null || client.level == null) return "0\u00b0";
        refreshLivePosText(client, cfg);
        return liveLatStr;
    }

    private static String longitudeText(Minecraft client, CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.showLongitude)) return null;
        if (client.player == null || client.level == null) return "0\u00b0";
        refreshLivePosText(client, cfg);
        return liveLonStr;
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
        return computeAnalogDialPos(screenW, screenH, client, cfg, latText, zoneText);
    }

    private static HudBounds computeAnalogBounds(int screenW, int screenH, Minecraft client, CompassHudConfig cfg, String latText, String zoneText) {
        // Pin & Grow v1: the DIAL's own box is what gets placed; attached text extends AWAY from it
        // (rightward, or wrapped below when the right side would run off-screen). The returned bounds are
        // the full visual union -- used for Studio hitboxes -- but the dial position never depends on the
        // text width (the audited root cause #1).
        HudPoint dial = computeAnalogDialPos(screenW, screenH, client, cfg, latText, zoneText);
        int diameter = analogDiameter(cfg);
        int textW = analogAttachedTextWidth(client, cfg, latText, zoneText);
        int lineH = client.font.lineHeight;
        // Bounds wrap the look's TRUE content (TAPE: the strip, centered in the dial box), so the
        // Studio border/hitbox never claims the tape's phantom top/bottom margins.
        int contentH = CompassDialRenderer.lookContentHeight(cfg, diameter);
        int contentTop = dial.y() + (diameter - contentH) / 2;
        if (textW <= 0) {
            return new HudBounds(dial.x(), contentTop, diameter, contentH);
        }
        if (analogTextBelow(screenW, dial.x(), diameter, textW)) {
            int cx = dial.x() + diameter / 2;
            int textX = clamp(cx - textW / 2, 4, Math.max(4, screenW - 4 - textW));
            int x = Math.min(dial.x(), textX);
            int right = Math.max(dial.x() + diameter, textX + textW);
            return new HudBounds(x, contentTop, right - x, contentH + 2 + lineH);
        }
        return new HudBounds(dial.x(), contentTop, diameter + ANALOG_LAT_GAP + textW, Math.max(contentH, lineH));
    }



    private static int analogDiameter(CompassHudConfig cfg) {
        return (int) Math.ceil(cfg.analogSize);
    }

    private static HudPoint computeDigitalBasePosition(Minecraft client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        HudBounds b = computeDigitalBounds(screenW, screenH, client, cfg, lines, false);
        return new HudPoint(b.x, b.y);
    }

    private static boolean shouldRenderPreviewHotbar(CompassHudConfig cfg) {
        // Any docked compass (either style — dockMode superseded the digital-only legacy boolean)
        // gets the ghost hotbar in the Studio preview, so "attached" placement is judged against
        // the geometry it actually docks to.
        return cfg.dockMode == CompassHudConfig.DockMode.HOTBAR_RIGHT;
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



    // ------------------------------------------------------------------------------------------------
    // Pin & Grow v1 (design: docs/design/hud-layout-overhaul-design-20260707.md). The pure math lives in
    // core.ui.HudLayoutMath (unit-tested); everything here is the thin client glue: config-enum mapping,
    // font-measured boxes, the legacy one-time migration, and the Studio drag persistence helpers.
    // ------------------------------------------------------------------------------------------------

    private static int gridCol(CompassHudConfig.HAnchor a) {
        return switch (a) {
            case LEFT -> 0;
            case CENTER -> 1;
            case RIGHT -> 2;
        };
    }

    private static int gridRow(CompassHudConfig.VAnchor a) {
        return switch (a) {
            case TOP -> 0;
            case CENTER -> 1;
            case BOTTOM -> 2;
        };
    }

    private static int pinPlaceX(CompassHudConfig.HAnchor grid, double offFrac, com.example.globe.core.ui.HudLayoutMath.GrowH grow, int boxW, int screenW) {
        int pin = com.example.globe.core.ui.HudLayoutMath.pinX(gridCol(grid), offFrac, screenW);
        return com.example.globe.core.ui.HudLayoutMath.placeX(pin, boxW, grow);
    }

    private static int pinPlaceY(CompassHudConfig.VAnchor grid, double offFrac, com.example.globe.core.ui.HudLayoutMath.GrowV grow, int boxH, int screenH) {
        int pin = com.example.globe.core.ui.HudLayoutMath.pinY(gridRow(grid), offFrac, screenH);
        return com.example.globe.core.ui.HudLayoutMath.placeY(pin, boxH, grow);
    }

    private static com.example.globe.core.ui.HudLayoutMath.GrowH legacyGrowH(CompassHudConfig.HAnchor a) {
        return switch (a) {
            case LEFT -> com.example.globe.core.ui.HudLayoutMath.GrowH.LEFT;
            case CENTER -> com.example.globe.core.ui.HudLayoutMath.GrowH.CENTER;
            case RIGHT -> com.example.globe.core.ui.HudLayoutMath.GrowH.RIGHT;
        };
    }

    private static com.example.globe.core.ui.HudLayoutMath.GrowV legacyGrowV(CompassHudConfig.VAnchor a) {
        return switch (a) {
            case TOP -> com.example.globe.core.ui.HudLayoutMath.GrowV.TOP;
            case CENTER -> com.example.globe.core.ui.HudLayoutMath.GrowV.MIDDLE;
            case BOTTOM -> com.example.globe.core.ui.HudLayoutMath.GrowV.BOTTOM;
        };
    }

    /** Vanilla renders the offhand slot on the side OPPOSITE the main hand: left-handed players get it
     *  RIGHT of the hotbar -- one of the two overlaps the old attach mode ignored. */
    private static boolean dockOffhandOnRight(Minecraft client) {
        try {
            return client.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The hotbar-mode attack indicator draws right of the hotbar -- the other ignored overlap. */
    private static boolean dockAttackIndicatorOnHotbar(Minecraft client) {
        try {
            return client.options.attackIndicator().get() == net.minecraft.client.AttackIndicatorStatus.HOTBAR;
        } catch (Throwable t) {
            return false;
        }
    }

    // Longest biome display-name in the current world's registry, cached per registry identity. Used by
    // reserved-width mode and by the Studio's "Longest" preview text source (U-B).
    private static Object longestBiomeCacheKey;
    private static String longestBiomeCache = "Windswept Gravelly Hills";

    static String longestBiomeName(Minecraft client) {
        try {
            if (client == null || client.level == null) return longestBiomeCache;
            var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
            if (registry == longestBiomeCacheKey) return longestBiomeCache;
            Font font = client.font;
            String longest = "Plains";
            int widest = -1;
            for (var key : registry.registryKeySet()) {
                String name = BiomeSamplerTools.biomeDisplayName(key.identifier().toString());
                if (name == null) continue;
                int w = font.width(name);
                if (w > widest) {
                    widest = w;
                    longest = name;
                }
            }
            longestBiomeCacheKey = registry;
            longestBiomeCache = longest;
            return longest;
        } catch (Throwable t) {
            return longestBiomeCache;
        }
    }

    /** The zone+biome segment at its widest plausible size (longest zone word + longest biome name). */
    private static String reservedZoneBiomeSample(Minecraft client, CompassHudConfig cfg) {
        String zone = (cfg.displayZoneInHud && cfg.zoneFollowsCompass) ? "Subtropics" : null;
        String biome = (cfg.displayBiomeInHud && cfg.biomeFollowsCompass) ? longestBiomeName(client) : null;
        return joinOrdered(zone, biome, cfg.biomeBeforeZone, cfg.compactHud);
    }

    /** Digital line width, optionally reserved to the widest text this world can produce so the box (and
     *  any CENTER/RIGHT-grown placement of it) goes fully static. */
    private static int measuredLineWidth(Minecraft client, CompassHudConfig cfg, String[] lines) {
        int w = maxLineWidth(client, lines);
        if (cfg.reservedTextWidth && cfg.style == CompassHudConfig.CompassStyle.DIGITAL) {
            String reservedLine = buildDigitalLine(
                    sampleDirection(cfg),
                    cfg.coordsFollowsCompass ? coordsText(client, cfg) : null,
                    reservedZoneBiomeSample(client, cfg),
                    cfg.compactHud);
            w = Math.max(w, client.font.width(reservedLine));
        }
        return w;
    }

    private static int analogAttachedTextWidth(Minecraft client, CompassHudConfig cfg, String latText, String zoneText) {
        int w = 0;
        boolean any = false;
        if (latText != null && !latText.isEmpty()) {
            w += client.font.width(latText);
            any = true;
        }
        if (zoneText != null && !zoneText.isEmpty()) {
            if (any) w += (cfg.compactHud ? 1 : 6);
            int zw = client.font.width(zoneText);
            if (cfg.reservedTextWidth) {
                String reserved = reservedZoneBiomeSample(client, cfg);
                if (reserved != null) zw = Math.max(zw, client.font.width(reserved));
            }
            w += zw;
            any = true;
        }
        return any ? w : 0;
    }

    /** Rightward attached text wraps below the dial when it would run off-screen (never moves the dial). */
    private static boolean analogTextBelow(int screenW, int dialX, int diameter, int textW) {
        return dialX + diameter + ANALOG_LAT_GAP + textW > screenW - 4;
    }

    /** v1 dial position: pin+grow on the DIAL's own box, or the hotbar-dock ladder. Attached text never
     *  participates -- the audited root cause #1 is structurally dead here. */
    private static HudPoint computeAnalogDialPos(int screenW, int screenH, Minecraft client, CompassHudConfig cfg, String latText, String zoneText) {
        int diameter = analogDiameter(cfg);
        int lineH = client.font.lineHeight;
        if (cfg.dockMode == CompassHudConfig.DockMode.HOTBAR_RIGHT) {
            int textW = analogAttachedTextWidth(client, cfg, latText, zoneText);
            // Dock with the look's TRUE content height, not the diameter box: TAPE's strip is a
            // fraction of its box, and docking the phantom box floated the strip high above the
            // hotbar (TEST 28). At tape content heights (16-22px) the BESIDE rung now centers the
            // strip on the hotbar row like a native element.
            int contentH = CompassDialRenderer.lookContentHeight(cfg, diameter);
            int besideW = diameter + (textW > 0 ? ANALOG_LAT_GAP + textW : 0);
            int besideH = Math.max(contentH, lineH);
            int stackedW = Math.max(diameter, textW);
            int stackedH = contentH + (textW > 0 ? 2 + lineH : 0);
            // Analog keeps a two-rung ladder in U-A (beside -> stacked -> lifted); a shrink rung would
            // need dial-scale plumbed through the renderer and is deferred to U-D's look system.
            var dockResult = com.example.globe.core.ui.HudLayoutMath.dock(screenW, screenH,
                    new com.example.globe.core.ui.HudLayoutMath.DockBoxes(besideW, besideH, stackedW, stackedH, stackedW, stackedH),
                    dockOffhandOnRight(client), dockAttackIndicatorOnHotbar(client));
            // The dock slot holds the CONTENT; convert back to the dial-box top-left the renderer
            // expects (content is vertically centered in the box, so the box may start above the slot
            // — only content pixels draw there). contentH == diameter for every non-tape look, making
            // this a no-op for them.
            return new HudPoint(dockResult.x(), dockResult.y() - (diameter - contentH) / 2);
        }
        int x = pinPlaceX(cfg.hAnchor, cfg.offXFrac, cfg.growH, diameter, screenW);
        int y = pinPlaceY(cfg.vAnchor, cfg.offYFrac, cfg.growV, diameter, screenH);
        x = clamp(x, 0, Math.max(0, screenW - diameter));
        y = clamp(y, 0, Math.max(0, screenH - diameter));
        return new HudPoint(x, y);
    }

    /**
     * One-time legacy (layoutVersion 0) -> Pin & Grow (v1) migration. Runs on the first frame with a real
     * window (pixel offsets can't convert to screen fractions without dimensions). Grow defaults mirror
     * the legacy anchor side and the pin is derived from the position the OLD math produced with the OLD
     * sample text -- day-one placement matches what the player last saw; stability is what changes.
     */
    private static void ensureLayoutMigrated(Minecraft client, CompassHudConfig cfg) {
        if (cfg.layoutVersion >= CompassHudConfig.CURRENT_LAYOUT_VERSION) return;
        var window = client.getWindow();
        if (window == null) return;
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        if (screenW <= 0 || screenH <= 0) return;
        Font font = client.font;

        cfg.growH = legacyGrowH(cfg.hAnchor);
        cfg.growV = legacyGrowV(cfg.vAnchor);
        cfg.zoneGrowH = legacyGrowH(cfg.zoneHAnchor);
        cfg.zoneGrowV = legacyGrowV(cfg.zoneVAnchor);
        cfg.biomeGrowH = legacyGrowH(cfg.biomeHAnchor);
        cfg.biomeGrowV = legacyGrowV(cfg.biomeVAnchor);
        cfg.coordsGrowH = legacyGrowH(cfg.coordsHAnchor);
        cfg.coordsGrowV = legacyGrowV(cfg.coordsVAnchor);

        // Compass: legacy box under the old math (sample text), then pin the v1 reference box (the dial
        // for analog, the whole line for digital) at the equivalent alignment point.
        int boxW;
        int boxH;
        int refW;
        int refH;
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            String latText = cfg.coordsFollowsCompass ? analogSampleLatLon(cfg) : null;
            String zoneText = attachedZoneBiomeSample(cfg);
            int diameter = analogDiameter(cfg);
            boxW = diameter;
            boxH = diameter;
            if (latText != null && !latText.isEmpty()) {
                boxW += ANALOG_LAT_GAP + font.width(latText);
                boxH = Math.max(boxH, font.lineHeight);
            }
            if (zoneText != null && !zoneText.isEmpty()) {
                boxW += (cfg.compactHud ? 1 : 6) + font.width(zoneText);
                boxH = Math.max(boxH, font.lineHeight);
            }
            refW = diameter;
            refH = diameter;
        } else {
            String[] lines = sampleLines(cfg);
            boxW = (int) Math.ceil((maxLineWidth(client, lines) + cfg.padding * 2) * cfg.scale);
            boxH = (int) Math.ceil((font.lineHeight * lines.length + cfg.padding * 2) * cfg.scale);
            refW = boxW;
            refH = boxH;
        }
        int legacyX = clamp(anchoredX(cfg, screenW, boxW) + cfg.offsetX, 0, Math.max(0, screenW - boxW));
        int legacyY = clamp(anchoredY(cfg, screenH, boxH) + cfg.offsetY, 0, Math.max(0, screenH - boxH));
        cfg.offXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointX(legacyX, refW, cfg.growH), gridCol(cfg.hAnchor), screenW);
        cfg.offYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointY(legacyY, refH, cfg.growV), gridRow(cfg.vAnchor), screenH);

        // Detached labels (legacy sample text, same conversion).
        int zw = font.width("Tropics");
        int zh = font.lineHeight;
        int zx = clamp(anchoredZoneX(cfg, screenW, zw) + cfg.zoneOffsetX, 0, Math.max(0, screenW - zw));
        int zy = clamp(anchoredZoneY(cfg, screenH, zh) + cfg.zoneOffsetY, 0, Math.max(0, screenH - zh));
        cfg.zoneOffXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointX(zx, zw, cfg.zoneGrowH), gridCol(cfg.zoneHAnchor), screenW);
        cfg.zoneOffYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointY(zy, zh, cfg.zoneGrowV), gridRow(cfg.zoneVAnchor), screenH);

        int bw = font.width("Plains");
        int bx = clamp(anchoredBiomeX(cfg, screenW, bw) + cfg.biomeOffsetX, 0, Math.max(0, screenW - bw));
        int by = clamp(anchoredBiomeY(cfg, screenH, zh) + cfg.biomeOffsetY, 0, Math.max(0, screenH - zh));
        cfg.biomeOffXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointX(bx, bw, cfg.biomeGrowH), gridCol(cfg.biomeHAnchor), screenW);
        cfg.biomeOffYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointY(by, zh, cfg.biomeGrowV), gridRow(cfg.biomeVAnchor), screenH);

        String coordsSampleText = coordsSample(cfg);
        int cw = coordsSampleText == null ? 20 : font.width(coordsSampleText);
        int cxx = clamp(anchoredCoordsX(cfg, screenW, cw) + cfg.coordsOffsetX, 0, Math.max(0, screenW - cw));
        int cyy = clamp(anchoredCoordsY(cfg, screenH, zh) + cfg.coordsOffsetY, 0, Math.max(0, screenH - zh));
        cfg.coordsOffXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointX(cxx, cw, cfg.coordsGrowH), gridCol(cfg.coordsHAnchor), screenW);
        cfg.coordsOffYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(
                com.example.globe.core.ui.HudLayoutMath.alignPointY(cyy, zh, cfg.coordsGrowV), gridRow(cfg.coordsVAnchor), screenH);

        cfg.layoutVersion = CompassHudConfig.CURRENT_LAYOUT_VERSION;
        CompassHudConfig.saveCurrent();
        com.example.globe.GlobeMod.LOGGER.info(
                "[Latitude] Compass HUD layout migrated to Pin & Grow (v1): placements converted to scale-independent pins.");
    }

    private static int maybeSnap(int v) {
        return LatitudeConfig.hudSnapEnabled ? snapTo(v, LatitudeConfig.hudSnapPixels) : v;
    }

    private static int snapTo(int v, int grid) {
        return grid <= 1 ? v : Math.round(v / (float) grid) * grid;
    }

    /** Studio drag persistence (v1): the drag moves the PIN. targetX/Y is the dragged box's top-left. */
    public static void applyCompassDrag(Minecraft client, CompassHudConfig cfg, int targetX, int targetY) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int refW;
        int refH;
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            refW = analogDiameter(cfg);
            refH = analogDiameter(cfg);
            // The dragged box (computeAnalogBounds) is content-true; the pin's reference is the dial
            // BOX. Convert the dragged content top back to the box top so a drag round-trips exactly
            // (no per-drag drift). No-op for non-tape looks (content == box).
            targetY -= (refH - CompassDialRenderer.lookContentHeight(cfg, refH)) / 2;
        } else {
            HudBounds b = computeBounds(client, cfg);
            refW = b.w;
            refH = b.h;
        }
        int pinPX = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointX(targetX, refW, cfg.growH));
        int pinPY = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointY(targetY, refH, cfg.growV));
        cfg.offXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(pinPX, gridCol(cfg.hAnchor), screenW);
        cfg.offYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(pinPY, gridRow(cfg.vAnchor), screenH);
    }

    public static void applyZoneDrag(Minecraft client, CompassHudConfig cfg, int targetX, int targetY, int boxW, int boxH) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int pinPX = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointX(targetX, boxW, cfg.zoneGrowH));
        int pinPY = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointY(targetY, boxH, cfg.zoneGrowV));
        cfg.zoneOffXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(pinPX, gridCol(cfg.zoneHAnchor), screenW);
        cfg.zoneOffYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(pinPY, gridRow(cfg.zoneVAnchor), screenH);
    }

    public static void applyBiomeDrag(Minecraft client, CompassHudConfig cfg, int targetX, int targetY, int boxW, int boxH) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int pinPX = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointX(targetX, boxW, cfg.biomeGrowH));
        int pinPY = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointY(targetY, boxH, cfg.biomeGrowV));
        cfg.biomeOffXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(pinPX, gridCol(cfg.biomeHAnchor), screenW);
        cfg.biomeOffYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(pinPY, gridRow(cfg.biomeVAnchor), screenH);
    }

    public static void applyCoordsDrag(Minecraft client, CompassHudConfig cfg, int targetX, int targetY, int boxW, int boxH) {
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int pinPX = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointX(targetX, boxW, cfg.coordsGrowH));
        int pinPY = maybeSnap(com.example.globe.core.ui.HudLayoutMath.alignPointY(targetY, boxH, cfg.coordsGrowV));
        cfg.coordsOffXFrac = com.example.globe.core.ui.HudLayoutMath.offXFracFor(pinPX, gridCol(cfg.coordsHAnchor), screenW);
        cfg.coordsOffYFrac = com.example.globe.core.ui.HudLayoutMath.offYFracFor(pinPY, gridRow(cfg.coordsVAnchor), screenH);
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

    private static int anchoredBiomeX(CompassHudConfig cfg, int screenW, int boxW) {
        return switch (cfg.biomeHAnchor) {
            case LEFT -> 4;
            case CENTER -> (screenW - boxW) / 2;
            case RIGHT -> screenW - boxW - 4;
        };
    }

    private static int anchoredBiomeY(CompassHudConfig cfg, int screenH, int boxH) {
        return switch (cfg.biomeVAnchor) {
            case TOP -> 4;
            case CENTER -> (screenH - boxH) / 2;
            case BOTTOM -> screenH - boxH - 4;
        };
    }

    private static int anchoredCoordsX(CompassHudConfig cfg, int screenW, int boxW) {
        return switch (cfg.coordsHAnchor) {
            case LEFT -> 4;
            case CENTER -> (screenW - boxW) / 2;
            case RIGHT -> screenW - boxW - 4;
        };
    }

    private static int anchoredCoordsY(CompassHudConfig cfg, int screenH, int boxH) {
        return switch (cfg.coordsVAnchor) {
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

    // ---- U-D live-text cache: HUD strings rebuild only when their INPUTS change, not every frame ----
    // Position-derived strings (lat/lon/zone/biome) share one key: player pos quantized to 1/4 block,
    // the 4-block biome quad, and the display-affecting config bits. The direction string has its own
    // 1-degree yaw key. Standing still costs zero string work; the per-frame biome registry resolve,
    // degree formatting, and zone lookup all collapse to change-driven.
    private static long livePosQ = Long.MIN_VALUE;
    private static long liveBiomeQuad = Long.MIN_VALUE;
    private static int livePosCfgHash;
    private static String liveLatStr;
    private static String liveLonStr;
    private static String liveZoneStr;
    private static String liveBiomeStr;
    private static int liveYawQ = Integer.MIN_VALUE;
    private static CompassHudConfig.DirectionMode liveDirMode;
    private static String liveDirStr = "N";

    /** Called on client disconnect so nothing from the previous world (cache keys tied to its coords and
     *  game time, compass-presence, dial-texture presence) leaks into the next one. */
    public static void onWorldSwitch() {
        livePosQ = Long.MIN_VALUE;
        liveBiomeQuad = Long.MIN_VALUE;
        liveYawQ = Integer.MIN_VALUE;
        liveLatStr = null;
        liveLonStr = null;
        liveZoneStr = null;
        liveBiomeStr = null;
        liveDirMode = null;
        lastCheckWorldTime = Long.MIN_VALUE;
        cachedHasCompass = false;
        CompassDialRenderer.invalidateTextures();
    }

    private static void refreshLivePosText(Minecraft client, CompassHudConfig cfg) {
        var player = client.player;
        var level = client.level;
        if (player == null || level == null) return;
        long posQ = ((long) Mth.floor(player.getX() * 4.0) << 32) ^ (Mth.floor(player.getZ() * 4.0) & 0xFFFFFFFFL);
        var bp = player.blockPosition();
        long quad = (((long) (bp.getX() >> 2)) << 42) ^ (((long) (bp.getY() >> 2) & 0x1FFFFFL) << 21)
                ^ ((long) (bp.getZ() >> 2) & 0x1FFFFFL);
        int cfgH = java.util.Objects.hash(cfg.latitudeDecimals, cfg.displayZoneInHud, cfg.displayBiomeInHud);
        if (posQ == livePosQ && quad == liveBiomeQuad && cfgH == livePosCfgHash && liveLatStr != null) return;
        livePosQ = posQ;
        liveBiomeQuad = quad;
        livePosCfgHash = cfgH;
        var border = level.getWorldBorder();
        liveLatStr = LatitudeMath.formatLatitudeDeg(player.getZ(), border);
        liveLonStr = LatitudeMath.formatLongitudeDeg(player.getX(), border);
        liveZoneStr = displayZoneName(com.example.globe.util.LatitudeMath.zoneKey(border, player.getZ()));
        if (cfg.displayBiomeInHud) {
            var biomeHolder = level.getBiome(bp);
            String biomeId = biomeHolder.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
            liveBiomeStr = BiomeSamplerTools.biomeDisplayName(biomeId);
        } else {
            liveBiomeStr = null;
        }
    }

    private static String liveDirection(Minecraft client, CompassHudConfig cfg) {
        float yawDeg = client.player != null ? client.player.getYRot() : 0f;
        int yawQ = Math.round(Mth.wrapDegrees(yawDeg));
        if (yawQ != liveYawQ || cfg.directionMode != liveDirMode) {
            liveYawQ = yawQ;
            liveDirMode = cfg.directionMode;
            liveDirStr = switch (cfg.directionMode) {
                case CARDINAL_8 -> direction8(yawDeg);
                case CARDINAL_4 -> direction4(yawDeg);
                case DEGREES -> degrees(yawDeg);
            };
        }
        return liveDirStr;
    }

    private static String zoneLabel(Minecraft client, CompassHudConfig cfg, boolean respectFollow) {
        if (!cfg.displayZoneInHud) return null;
        if (respectFollow && !cfg.zoneFollowsCompass) return null;
        if (client == null || client.player == null || client.level == null) return sampleZone(cfg);
        refreshLivePosText(client, cfg);
        return liveZoneStr;
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

    // Biome label -- mirrors zoneLabel/sampleZone/displayZoneName above (same shape: respectFollow governs
    // whether this is used for the ATTACHED line, where it should be null if detached, vs the DETACHED render,
    // where follow state doesn't matter).
    private static String biomeLabel(Minecraft client, CompassHudConfig cfg, boolean respectFollow) {
        if (!cfg.displayBiomeInHud) return null;
        if (respectFollow && !cfg.biomeFollowsCompass) return null;
        if (client == null || client.player == null || client.level == null) return sampleBiome(cfg);
        refreshLivePosText(client, cfg);
        return liveBiomeStr;
    }

    private static String sampleBiome(CompassHudConfig cfg) {
        if (!cfg.displayBiomeInHud) return null;
        Minecraft mc = Minecraft.getInstance();
        return switch (previewTextSource) {
            case SAMPLE -> "Plains";
            case LONGEST -> longestBiomeName(mc);
            case LIVE -> {
                if (mc != null && mc.player != null && mc.level != null) {
                    var biomeHolder = mc.level.getBiome(mc.player.blockPosition());
                    String biomeId = biomeHolder.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
                    String name = BiomeSamplerTools.biomeDisplayName(biomeId);
                    if (name != null) yield name;
                }
                yield longestBiomeName(mc);
            }
        };
    }

    // Combines the zone (band) and biome text into the single opaque string that gets glued onto the compass
    // line (digital) or appended after the coords (analog), in whichever order cfg.biomeBeforeZone picks. Callers
    // just treat the result as one more optional text segment -- exactly like "zoneText" worked before biome
    // existed -- so nothing downstream needs to know biome exists at all.
    private static String attachedZoneBiomeLive(Minecraft client, CompassHudConfig cfg) {
        return joinOrdered(zoneLabel(client, cfg, true), biomeLabel(client, cfg, true), cfg.biomeBeforeZone, cfg.compactHud);
    }

    private static String attachedZoneBiomeSample(CompassHudConfig cfg) {
        String zone = cfg.zoneFollowsCompass ? sampleZone(cfg) : null;
        String biome = cfg.biomeFollowsCompass ? sampleBiome(cfg) : null;
        return joinOrdered(zone, biome, cfg.biomeBeforeZone, cfg.compactHud);
    }

    private static String joinOrdered(String zone, String biome, boolean biomeFirst, boolean compact) {
        if (zone == null || zone.isEmpty()) return (biome == null || biome.isEmpty()) ? null : biome;
        if (biome == null || biome.isEmpty()) return zone;
        String sep = compact ? " " : " · ";
        return biomeFirst ? (biome + sep + zone) : (zone + sep + biome);
    }

    // Canonical "what does the coords text say" formatter, usable both when coords rides with the compass
    // (existing behavior) and when detached to its own element. Style-aware since digital/analog have separate
    // show-latitude/show-longitude flag pairs.
    private static String coordsText(Minecraft client, CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return analogLatLonText(client, cfg);
        }
        return joinLatLon(latitudeText(client, cfg), longitudeText(client, cfg));
    }

    private static String coordsSample(CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return analogSampleLatLon(cfg);
        }
        Minecraft mc = Minecraft.getInstance();
        if (previewTextSource == PreviewTextSource.LIVE && mc != null && mc.player != null && mc.level != null) {
            return joinLatLon(latitudeText(mc, cfg), longitudeText(mc, cfg));
        }
        boolean widest = previewTextSource == PreviewTextSource.LONGEST;
        String lat = Boolean.TRUE.equals(cfg.showLatitude) ? (widest ? "89°S" : "1°S") : null;
        String lon = Boolean.TRUE.equals(cfg.showLongitude) ? (widest ? "179°W" : "15°E") : null;
        return joinLatLon(lat, lon);
    }

    private static void drawText(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, String text, int x, int y, int color) {
        if (cfg.textRainbow) {
            int alphaByte = (color >>> 24) & 0xFF;
            drawRainbowLeftAligned(ctx, client.font, text, x, y, cfg.shadow, alphaByte);
            return;
        }
        if (cfg.shadow) {
            ctx.text(client.font, Component.literal(text), x, y, color);
        } else {
            ctx.text(client.font, Component.literal(text), x, y, color, false);
        }
    }

    // Left-aligned rainbow draw (RainbowText itself only offers centered drawing) -- shared by drawText() above
    // (analog-attached + all 3 detached labels) and renderDigitalAt()'s per-line loop below, so both compass
    // styles get the same rainbow behavior from one place.
    private static void drawRainbowLeftAligned(GuiGraphicsExtractor ctx, Font font, String text, int x, int y, boolean shadow, int alphaByte) {
        int alphaMask = (alphaByte & 0xFF) << 24;
        int visibleIdx = 0;
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String s = String.valueOf(c);
            if (c != ' ') {
                int color = alphaMask | RainbowText.paletteColor(visibleIdx);
                ctx.text(font, s, cx, y, color, shadow);
                visibleIdx++;
            }
            cx += font.width(s);
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
        if (cfg.reservedTextWidth) w = Math.max(w, client.font.width("Subtropics"));
        int x = pinPlaceX(cfg.zoneHAnchor, cfg.zoneOffXFrac, cfg.zoneGrowH, w, screenW);
        int y = pinPlaceY(cfg.zoneVAnchor, cfg.zoneOffYFrac, cfg.zoneGrowV, h, screenH);
        x = clamp(x, 0, Math.max(0, screenW - w));
        y = clamp(y, 0, Math.max(0, screenH - h));
        return new HudBounds(x, y, w, h);
    }

    // Detached biome label support -- mirrors renderDetachedZone/computeZoneBounds exactly.
    private static void renderDetachedBiome(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, boolean isPreview) {
        String biome = biomeLabel(client, cfg, false);
        if (biome == null) return;
        HudBounds bb = computeBiomeBounds(client, cfg);
        if (bb == null) return;
        if (isPreview) {
            int border = ANALOG_PREVIEW_BORDER;
            ctx.fill(bb.x, bb.y, bb.x + bb.w, bb.y + 1, border);
            ctx.fill(bb.x, bb.y + bb.h - 1, bb.x + bb.w, bb.y + bb.h, border);
            ctx.fill(bb.x, bb.y, bb.x + 1, bb.y + bb.h, border);
            ctx.fill(bb.x + bb.w - 1, bb.y, bb.x + bb.w, bb.y + bb.h, border);
        }
        int color = cfg.textArgb();
        drawText(ctx, client, cfg, biome, bb.x, bb.y, color);
    }

    public static HudBounds computeBiomeBounds(Minecraft client, CompassHudConfig cfg) {
        String biome = biomeLabel(client, cfg, false);
        if (biome == null) return null;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int w = client.font.width(biome);
        int h = client.font.lineHeight;
        if (cfg.reservedTextWidth) w = Math.max(w, client.font.width(longestBiomeName(client)));
        int x = pinPlaceX(cfg.biomeHAnchor, cfg.biomeOffXFrac, cfg.biomeGrowH, w, screenW);
        int y = pinPlaceY(cfg.biomeVAnchor, cfg.biomeOffYFrac, cfg.biomeGrowV, h, screenH);
        x = clamp(x, 0, Math.max(0, screenW - w));
        y = clamp(y, 0, Math.max(0, screenH - h));
        return new HudBounds(x, y, w, h);
    }

    // Detached coords label support -- mirrors renderDetachedZone/computeZoneBounds. coordsText() is the same
    // formatter used when coords rides with the compass; here it's rendered standalone.
    private static void renderDetachedCoords(GuiGraphicsExtractor ctx, Minecraft client, CompassHudConfig cfg, boolean isPreview) {
        String coords = coordsLabelForDetached(client, cfg);
        if (coords == null || coords.isEmpty()) return;
        HudBounds cb = computeCoordsBounds(client, cfg);
        if (cb == null) return;
        if (isPreview) {
            int border = ANALOG_PREVIEW_BORDER;
            ctx.fill(cb.x, cb.y, cb.x + cb.w, cb.y + 1, border);
            ctx.fill(cb.x, cb.y + cb.h - 1, cb.x + cb.w, cb.y + cb.h, border);
            ctx.fill(cb.x, cb.y, cb.x + 1, cb.y + cb.h, border);
            ctx.fill(cb.x + cb.w - 1, cb.y, cb.x + cb.w, cb.y + cb.h, border);
        }
        int color = cfg.textArgb();
        drawText(ctx, client, cfg, coords, cb.x, cb.y, color);
    }

    private static String coordsLabelForDetached(Minecraft client, CompassHudConfig cfg) {
        if (cfg.coordsFollowsCompass) return null;
        if (client == null || client.player == null || client.level == null) return coordsSample(cfg);
        return coordsText(client, cfg);
    }

    public static HudBounds computeCoordsBounds(Minecraft client, CompassHudConfig cfg) {
        String coords = coordsLabelForDetached(client, cfg);
        if (coords == null || coords.isEmpty()) return null;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int w = client.font.width(coords);
        int h = client.font.lineHeight;
        int x = pinPlaceX(cfg.coordsHAnchor, cfg.coordsOffXFrac, cfg.coordsGrowH, w, screenW);
        int y = pinPlaceY(cfg.coordsVAnchor, cfg.coordsOffYFrac, cfg.coordsGrowV, h, screenH);
        x = clamp(x, 0, Math.max(0, screenW - w));
        y = clamp(y, 0, Math.max(0, screenH - h));
        return new HudBounds(x, y, w, h);
    }

    static CompassDialRenderer.DialColors analogColors(CompassHudConfig cfg) {
        return switch (cfg.analogTheme) {
            case PALE_GOLD -> new CompassDialRenderer.DialColors(0x233029, 0xFFE5C07B, 0xFFA58C6F, 0xFFDD845A);
            case RED_IVORY -> new CompassDialRenderer.DialColors(0x292221, 0xFFE3D4C8, 0xFF9E8B83, 0xFFE05B4F);
            case CYAN_STEEL -> new CompassDialRenderer.DialColors(0x1A232A, 0xFF5CC8FF, 0xFF8FB7CC, 0xFF52E0FF);
            case MINT_BRASS -> new CompassDialRenderer.DialColors(0x1C2823, 0xFFD4B87A, 0xFF8FA58F, 0xFF6AE6B8);
            // face is plain 0xRRGGBB (alpha re-applied by analogInnerColor); ring/muted/needle are full 0xFF ARGB.
            case OBSIDIAN_RED -> new CompassDialRenderer.DialColors(0x14110F, 0xFFB0A8A0, 0xFF6E6862, 0xFFE2402E);
            case ARCTIC_BLUE -> new CompassDialRenderer.DialColors(0x16202B, 0xFFCFE8FF, 0xFF7F9DB5, 0xFF4FC3FF);
            case EMERALD -> new CompassDialRenderer.DialColors(0x122019, 0xFF7BE0A0, 0xFF6F9C82, 0xFFFFD56A);
            case ROYAL_PURPLE -> new CompassDialRenderer.DialColors(0x1A1426, 0xFFC9A6F0, 0xFF8C7AA0, 0xFFFFC04D);
            case SUNSET -> new CompassDialRenderer.DialColors(0x261712, 0xFFF2A65A, 0xFFB07E62, 0xFFFF5E5B);
            case MONOCHROME -> new CompassDialRenderer.DialColors(0x1B1B1E, 0xFFD8D8DC, 0xFF80808A, 0xFFF2F2F2);
            case CLASSIC_GOLD -> new CompassDialRenderer.DialColors(ANALOG_FACE_RGB, ANALOG_RING, ANALOG_MUTED, ANALOG_N_COLOR);
            case CUSTOM -> new CompassDialRenderer.DialColors(cfg.customFaceRgb, cfg.customRingArgb, cfg.customMutedArgb, cfg.customNeedleArgb);
            default -> new CompassDialRenderer.DialColors(ANALOG_FACE_RGB, ANALOG_RING, ANALOG_MUTED, ANALOG_N_COLOR);
        };
    }
}
