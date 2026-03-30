package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class CompassHud {
    private static final int ANALOG_FACE = 0xFF1A1410;
    private static final int ANALOG_RING = 0xFFD4A74A;
    private static final int ANALOG_MUTED = 0xFF8C8078;
    private static final int ANALOG_N_COLOR = 0xFFCC3333;
    private static final int ANALOG_PREVIEW_BORDER = 0x55FFFFFF;
    private static final int ANALOG_LAT_GAP = 6;

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

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        renderInternal(ctx, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), false);
    }

    public static void render(DrawContext ctx, int screenW, int screenH) {
        renderInternal(ctx, screenW, screenH, false);
    }

    public static void renderAdjustPreview(DrawContext ctx, int screenW, int screenH) {
        renderInternal(ctx, screenW, screenH, true);
    }

    private static void renderInternal(DrawContext ctx, int screenW, int screenH, boolean forceVisible) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        if (!forceVisible && client.currentScreen != null) {
            return;
        }

        var cfg = CompassHudConfig.get();
        if (!forceVisible && !cfg.enabled) {
            return;
        }

        boolean isHoldingCompass = client.player.getMainHandStack().isOf(Items.COMPASS)
                || client.player.getOffHandStack().isOf(Items.COMPASS);

        long t = client.world.getTime();
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
            HudBounds b = computeAnalogBounds(screenW, screenH, client, cfg, latText);
            renderAnalogAt(ctx, client, cfg, latText, b.x, b.y, forceVisible);
        } else {
            String directionText = switch (cfg.directionMode) {
                case CARDINAL_8 -> direction8(client.player.getYaw());
                case CARDINAL_4 -> direction4(client.player.getYaw());
                case DEGREES -> degrees(client.player.getYaw());
            };

            String hudText;
            if (Boolean.TRUE.equals(cfg.showLatitude)) {
                String latText = LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.world.getWorldBorder());
                String sep = cfg.compactHud ? " " : " \u00b7 ";
                hudText = directionText + sep + latText;
            } else {
                hudText = directionText;
            }

            String[] lines = new String[]{hudText};
            HudBounds b = computeBounds(screenW, screenH, client, cfg, lines);
            renderDigitalAt(ctx, client, cfg, lines, b.x, b.y, forceVisible);
        }
    }

    public static HudBounds computeBounds(MinecraftClient client, CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), client, cfg, analogSampleLatitude(cfg));
        }
        return computeBounds(client, cfg, sampleLines(cfg));
    }

    public static HudPoint computeBasePosition(MinecraftClient client, CompassHudConfig cfg) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBasePosition(client, cfg, analogSampleLatitude(cfg));
        }
        return computeDigitalBasePosition(client, cfg, sampleLines(cfg));
    }

    public static HudBounds computeBounds(MinecraftClient client, CompassHudConfig cfg, Text text) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, analogSampleLatitude(cfg));
        }
        return computeBounds(screenW, screenH, client, cfg, new String[]{text.getString()});
    }

    public static HudBounds computeBounds(MinecraftClient client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            return computeAnalogBounds(screenW, screenH, client, cfg, analogSampleLatitude(cfg));
        }
        return computeBounds(screenW, screenH, client, cfg, lines);
    }

    private static HudBounds computeBounds(int screenW, int screenH, MinecraftClient client, CompassHudConfig cfg, String[] lines) {
        return computeDigitalBounds(screenW, screenH, client, cfg, lines);
    }

    private static HudBounds computeDigitalBounds(int screenW, int screenH, MinecraftClient client, CompassHudConfig cfg, String[] lines) {

        int pad = cfg.padding;
        int textW = maxLineWidth(client, lines);
        int textH = client.textRenderer.fontHeight * lines.length;

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
                int hotbarLeft = screenW / 2 - 91;
                int hotbarTop = screenH - 22;
                int hotbarRight = hotbarLeft + 182;

                int margin = 4;

                x = hotbarRight + margin;
                y = hotbarTop + (22 - scaledBoxH) / 2;

                if (x + scaledBoxW > screenW - margin) {
                    x = hotbarLeft - margin - scaledBoxW;
                }
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

    public static void renderPreview(DrawContext ctx, MinecraftClient client, CompassHudConfig cfg, int x, int y) {
        if (cfg.style == CompassHudConfig.CompassStyle.ANALOG) {
            renderAnalogAt(ctx, client, cfg, analogSampleLatitude(cfg), x, y, true);
        } else {
            renderDigitalAt(ctx, client, cfg, sampleLines(cfg), x, y, true);
        }
    }

    private static void renderDigitalAt(DrawContext ctx, MinecraftClient client, CompassHudConfig cfg, String[] lines, int x, int y, boolean isPreview) {
        int pad = cfg.padding;
        int textW = maxLineWidth(client, lines);
        int textH = client.textRenderer.fontHeight * lines.length;

        int boxW = textW + pad * 2;
        int boxH = textH + pad * 2;

        float s = cfg.scale;

        var m = ctx.getMatrices();
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
                int lineY = ty + i * client.textRenderer.fontHeight;
                Text line = Text.literal(lines[i]);
                if (cfg.shadow) {
                    ctx.drawTextWithShadow(client.textRenderer, line, tx, lineY, color);
                } else {
                    ctx.drawText(client.textRenderer, line, tx, lineY, color, false);
                }
            }
        } finally {
            m.popMatrix();
        }
    }

    private static void renderAnalogAt(DrawContext ctx, MinecraftClient client, CompassHudConfig cfg, String latText, int x, int y, boolean isPreview) {
        int diameter = analogDiameter(cfg);
        int radius = diameter / 2;
        int cx = x + radius;
        int cy = y + radius;

        double angle = Math.toRadians(MathHelper.wrapDegrees(client.player.getYaw() + 180.0f));

        drawAnalogCompass(ctx, cx, cy, radius, angle);

        if (isPreview) {
            int boxW = diameter;
            int boxH = diameter;
            if (latText != null && !latText.isEmpty()) {
                boxW += ANALOG_LAT_GAP + client.textRenderer.getWidth(latText);
                boxH = Math.max(boxH, client.textRenderer.fontHeight);
            }
            ctx.fill(x, y, x + boxW, y + 1, ANALOG_PREVIEW_BORDER);
            ctx.fill(x, y + boxH - 1, x + boxW, y + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(x, y, x + 1, y + boxH, ANALOG_PREVIEW_BORDER);
            ctx.fill(x + boxW - 1, y, x + boxW, y + boxH, ANALOG_PREVIEW_BORDER);
        }

        if (latText != null && !latText.isEmpty()) {
            int color = cfg.textArgb();
            int textX = x + diameter + ANALOG_LAT_GAP;
            int textY = cy - client.textRenderer.fontHeight / 2;
            if (cfg.shadow) {
                ctx.drawTextWithShadow(client.textRenderer, Text.literal(latText), textX, textY, color);
            } else {
                ctx.drawText(client.textRenderer, Text.literal(latText), textX, textY, color, false);
            }
        }
    }

    private static void drawAnalogCompass(DrawContext ctx, int cx, int cy, int radius, double angle) {
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int dist2 = dx * dx + dy * dy;
                if (dist2 > r2) continue;
                int px = cx + dx;
                int py = cy + dy;
                if (dist2 > (radius - 2) * (radius - 2)) {
                    ctx.fill(px, py, px + 1, py + 1, ANALOG_RING);
                } else {
                    ctx.fill(px, py, px + 1, py + 1, ANALOG_FACE);
                }
            }
        }

        int tickLen = Math.max(2, radius / 6);
        // North tick (up)
        ctx.fill(cx, cy - radius + 2, cx + 1, cy - radius + 2 + tickLen, ANALOG_RING);
        // South tick
        ctx.fill(cx, cy + radius - 2 - tickLen, cx + 1, cy + radius - 2, ANALOG_MUTED);
        // East tick
        ctx.fill(cx + radius - 2 - tickLen, cy, cx + radius - 2, cy + 1, ANALOG_MUTED);
        // West tick
        ctx.fill(cx - radius + 2, cy, cx - radius + 2 + tickLen, cy + 1, ANALOG_MUTED);

        String nLabel = "N";
        int nW = MinecraftClient.getInstance().textRenderer.getWidth(nLabel);
        ctx.drawText(MinecraftClient.getInstance().textRenderer, nLabel, cx - nW / 2, cy - radius + 2 + tickLen + 1, ANALOG_N_COLOR, true);

        int needleLen = radius - 4;
        int nx = cx + (int) Math.round(Math.sin(angle) * needleLen);
        int ny = cy - (int) Math.round(Math.cos(angle) * needleLen);
        drawLine(ctx, cx, cy, nx, ny, ANALOG_N_COLOR);

        int sx = cx - (int) Math.round(Math.sin(angle) * (needleLen * 0.6));
        int sy = cy + (int) Math.round(Math.cos(angle) * (needleLen * 0.6));
        drawLine(ctx, cx, cy, sx, sy, ANALOG_RING);

        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, ANALOG_RING);
    }

    private static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
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
        String dir = switch (cfg.directionMode) {
            case CARDINAL_8 -> "NW";
            case CARDINAL_4 -> "W";
            case DEGREES -> "360\u00b0";
        };

        if (Boolean.TRUE.equals(cfg.showLatitude)) {
            String sep = cfg.compactHud ? " " : " \u00b7 ";
            return new String[]{dir + sep + "1\u00b0S"};
        }
        return new String[]{dir};
    }

    private static String analogSampleLatitude(CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.analogShowLatitude)) return null;
        return "1\u00b0S";
    }

    private static String analogLatitudeText(MinecraftClient client, CompassHudConfig cfg) {
        if (!Boolean.TRUE.equals(cfg.analogShowLatitude)) return null;
        if (client.player == null || client.world == null) return analogSampleLatitude(cfg);
        return LatitudeMath.formatLatitudeDeg(client.player.getZ(), client.world.getWorldBorder());
    }

    private static HudPoint computeAnalogBasePosition(MinecraftClient client, CompassHudConfig cfg, String latText) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int diameter = analogDiameter(cfg);
        int boxW = diameter;
        int boxH = diameter;
        if (latText != null && !latText.isEmpty()) {
            boxW += ANALOG_LAT_GAP + client.textRenderer.getWidth(latText);
            boxH = Math.max(boxH, client.textRenderer.fontHeight);
        }

        int x;
        int y;
        if (cfg.attachToHotbarCompass && client.player != null) {
            int slotIndex = findHotbarCompassSlot(client.player);
            if (slotIndex >= 0) {
                int hotbarLeft = screenW / 2 - 91;
                int hotbarTop = screenH - 22;
                int hotbarRight = hotbarLeft + 182;
                int margin = 4;
                x = hotbarRight + margin;
                y = hotbarTop + (22 - boxH) / 2;
                if (x + boxW > screenW - margin) {
                    x = hotbarLeft - margin - boxW;
                }
            } else {
                x = anchoredX(cfg, screenW, boxW);
                y = anchoredY(cfg, screenH, boxH);
            }
        } else {
            x = anchoredX(cfg, screenW, boxW);
            y = anchoredY(cfg, screenH, boxH);
        }

        x = clamp(x, 0, Math.max(0, screenW - boxW));
        y = clamp(y, 0, Math.max(0, screenH - boxH));

        return new HudPoint(x, y);
    }

    private static HudBounds computeAnalogBounds(int screenW, int screenH, MinecraftClient client, CompassHudConfig cfg, String latText) {
        int diameter = analogDiameter(cfg);
        int boxW = diameter;
        int boxH = diameter;
        if (latText != null && !latText.isEmpty()) {
            boxW += ANALOG_LAT_GAP + client.textRenderer.getWidth(latText);
            boxH = Math.max(boxH, client.textRenderer.fontHeight);
        }

        int x;
        int y;
        if (cfg.attachToHotbarCompass && client.player != null) {
            int slotIndex = findHotbarCompassSlot(client.player);
            if (slotIndex >= 0) {
                int hotbarLeft = screenW / 2 - 91;
                int hotbarTop = screenH - 22;
                int hotbarRight = hotbarLeft + 182;
                int margin = 4;
                x = hotbarRight + margin;
                y = hotbarTop + (22 - boxH) / 2;
                if (x + boxW > screenW - margin) {
                    x = hotbarLeft - margin - boxW;
                }
            } else {
                x = anchoredX(cfg, screenW, boxW);
                y = anchoredY(cfg, screenH, boxH);
            }
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

    private static int analogDiameter(CompassHudConfig cfg) {
        return (int) Math.ceil(cfg.analogSize);
    }

    private static HudPoint computeDigitalBasePosition(MinecraftClient client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int pad = cfg.padding;
        int textW = maxLineWidth(client, lines);
        int textH = client.textRenderer.fontHeight * lines.length;

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
                int hotbarLeft = screenW / 2 - 91;
                int hotbarTop = screenH - 22;
                int hotbarRight = hotbarLeft + 182;

                int margin = 4;

                x = hotbarRight + margin;
                y = hotbarTop + (22 - scaledBoxH) / 2;

                if (x + scaledBoxW > screenW - margin) {
                    x = hotbarLeft - margin - scaledBoxW;
                }
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

    private static int maxLineWidth(MinecraftClient client, String[] lines) {
        int w = 0;
        for (String s : lines) {
            w = Math.max(w, client.textRenderer.getWidth(s));
        }
        return w;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean hasCompassAnywhere(PlayerEntity player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (containsCompass(inv.getStack(i), 0)) return true;
        }
        // Extra safety: offhand
        return containsCompass(player.getOffHandStack(), 0);
    }

    private static boolean containsCompass(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isOf(Items.COMPASS)) return true;

        // Prevent infinite recursion
        if (depth >= 6) return false;

        // Bundle contents (modern data component)
        if (stack.isOf(Items.BUNDLE)) {
            BundleContentsComponent contents = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
            if (contents != null) {
                for (ItemStack inside : contents.iterate()) {
                    if (containsCompass(inside, depth + 1)) return true;
                }
            }
        }

        return false;
    }

    private static int findHotbarCompassSlot(PlayerEntity player) {
        var inv = player.getInventory();
        for (int i = 0; i < 9 && i < inv.size(); i++) {
            if (containsCompass(inv.getStack(i), 0)) return i;
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

    private static String direction4(float yawDegrees) {
        float yaw = MathHelper.wrapDegrees(yawDegrees); // -180..180
        int idx = MathHelper.floor((yaw + 180.0f + 45.0f) / 90.0f) & 3;

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
        return MathHelper.floor(deg) + "\u00b0";
    }

    private static String direction8(float yawDegrees) {
        float yaw = MathHelper.wrapDegrees(yawDegrees); // -180..180
        int idx = MathHelper.floor((yaw + 180.0f + 22.5f) / 45.0f) & 7;

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
}
