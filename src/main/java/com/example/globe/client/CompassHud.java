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

        String directionText = switch (cfg.directionMode) {
            case CARDINAL_8 -> direction8(client.player.getYaw());
            case CARDINAL_4 -> direction4(client.player.getYaw());
            case DEGREES -> degrees(client.player.getYaw());
        };

        String hudText;
        if (Boolean.TRUE.equals(cfg.showLatitude)) {
            String latText = formatLatitudeDeg(client.player.getZ(), client.world.getWorldBorder());
            String sep = cfg.compactHud ? " " : " \u00b7 ";
            hudText = directionText + sep + latText;
        } else {
            hudText = directionText;
        }

        String[] lines = new String[]{hudText};
        HudBounds b = computeBounds(screenW, screenH, client, cfg, lines);
        renderAt(ctx, client, cfg, lines, b.x, b.y, forceVisible);
    }

    public static HudBounds computeBounds(MinecraftClient client, CompassHudConfig cfg) {
        return computeBounds(client, cfg, sampleLines(cfg));
    }

    public static HudPoint computeBasePosition(MinecraftClient client, CompassHudConfig cfg) {
        String[] lines = sampleLines(cfg);
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
                int slotX = hotbarLeft + slotIndex * 20 + 2;
                int slotY = screenH - 22 + 3;
                x = slotX;
                y = slotY - (scaledBoxH + 6);
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

    public static HudBounds computeBounds(MinecraftClient client, CompassHudConfig cfg, Text text) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        return computeBounds(screenW, screenH, client, cfg, new String[]{text.getString()});
    }

    public static HudBounds computeBounds(MinecraftClient client, CompassHudConfig cfg, String[] lines) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        return computeBounds(screenW, screenH, client, cfg, lines);
    }

    private static HudBounds computeBounds(int screenW, int screenH, MinecraftClient client, CompassHudConfig cfg, String[] lines) {

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
                int slotX = hotbarLeft + slotIndex * 20 + 2;
                int slotY = screenH - 22 + 3;
                x = slotX;
                y = slotY - (scaledBoxH + 6);
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
        renderAt(ctx, client, cfg, sampleLines(cfg), x, y, true);
    }

    private static void renderAt(DrawContext ctx, MinecraftClient client, CompassHudConfig cfg, String[] lines, int x, int y, boolean isPreview) {
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

    private static int maxLineWidth(MinecraftClient client, String[] lines) {
        int w = 0;
        for (String s : lines) {
            w = Math.max(w, client.textRenderer.getWidth(s));
        }
        return w;
    }

    private static String formatLatitudeDeg(double playerZ, net.minecraft.world.border.WorldBorder border) {
        if (border == null) return "0\u00b0";

        double radius = border.getSize() * 0.5;
        if (radius <= 0.0001) return "0\u00b0";

        double frac = Math.min(1.0, Math.abs(playerZ) / radius);
        double absDeg = frac * 90.0;

        double lat = (playerZ < 0) ? absDeg : -absDeg;
        int deg = (int) Math.round(Math.abs(lat));
        if (deg == 0) return "0\u00b0";

        String hemi = lat > 0 ? "N" : "S";
        return deg + "\u00b0" + hemi;
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
