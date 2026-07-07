package com.example.globe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

/**
 * The analog compass looks (design: hud-layout-overhaul-design-20260707.md, Pillar 2b + Pillar 4).
 *
 * <p><b>Render hygiene.</b> The pre-U-D dial was a per-pixel disc: one {@code ctx.fill} per pixel,
 * O(diameter²) draw calls every frame (~4k at the default size, ~16k at the slider max). Every look here
 * is drawn in horizontal SPANS (one fill per row segment, the same batching drawContinents uses), so a
 * disc costs ~2·diameter fills — a 50-100× reduction — and the lighter looks cost a handful.
 *
 * <p><b>Reskinnable.</b> Before procedural drawing, each look checks for a resource-pack texture at
 * {@code globe:textures/gui/compass/<look>.png} (64x64; stretched over the dial box). When present it is
 * blitted as the dial art and only the needle, cardinal ticks, and "N" are drawn on top — so packs can
 * fully restyle the dial without touching code. Presence is cached per look; the cache is invalidated on
 * world switch and whenever the HUD Studio opens (covers the realistic pack-toggling flows without a
 * reload-listener registration).
 */
final class CompassDialRenderer {

    record DialColors(int face, int ring, int muted, int needle) {}

    private CompassDialRenderer() {
    }

    // ------------------------------------------------------------------------------------------------
    // Texture override
    // ------------------------------------------------------------------------------------------------

    private static final Map<CompassHudConfig.CompassLook, Boolean> TEXTURE_PRESENT =
            new EnumMap<>(CompassHudConfig.CompassLook.class);

    static void invalidateTextures() {
        TEXTURE_PRESENT.clear();
    }

    private static Identifier textureId(CompassHudConfig.CompassLook look) {
        return Identifier.fromNamespaceAndPath("globe",
                "textures/gui/compass/" + look.name().toLowerCase(java.util.Locale.ROOT) + ".png");
    }

    private static boolean hasTexture(CompassHudConfig.CompassLook look) {
        return TEXTURE_PRESENT.computeIfAbsent(look, l -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                return mc != null && mc.getResourceManager().getResource(textureId(l)).isPresent();
            } catch (Throwable t) {
                return false;
            }
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------------------------------------

    /**
     * @param angle needle angle in radians (0 = needle up = player facing north-relative convention the
     *              old dial used: {@code toRadians(wrapDegrees(yaw + 180))})
     * @param yawDegrees raw player yaw, for the TAPE look's heading window
     */
    static void draw(GuiGraphicsExtractor ctx, Font font, CompassHudConfig cfg,
                     int cx, int cy, int radius, double angle, float yawDegrees, DialColors colors) {
        CompassHudConfig.CompassLook look = cfg.analogLook;

        if (hasTexture(look)) {
            int d = radius * 2;
            ctx.blit(RenderPipelines.GUI_TEXTURED, textureId(look),
                    cx - radius, cy - radius, 0.0f, 0.0f, d, d, 64, 64, 64, 64);
            // Pack art replaces face/ring/ticks; the moving parts stay procedural on top.
            switch (look) {
                case TAPE -> drawTapeForeground(ctx, font, cfg, cx, cy, radius, yawDegrees, colors);
                case MINIMAL -> {
                    drawNeedles(ctx, cx, cy, radius, angle, colors);
                    drawCenterDot(ctx, cx, cy, colors);
                }
                default -> {
                    drawNeedles(ctx, cx, cy, radius, angle, colors);
                    drawCenterDot(ctx, cx, cy, colors);
                    drawNorthLabel(ctx, font, cx, cy, radius, colors);
                }
            }
            return;
        }

        switch (look) {
            case DISC -> drawDisc(ctx, font, cfg, cx, cy, radius, angle, colors, true);
            case RING -> drawDisc(ctx, font, cfg, cx, cy, radius, angle, colors, false);
            case ROSE -> drawRose(ctx, font, cfg, cx, cy, radius, angle, colors);
            case TAPE -> drawTape(ctx, font, cfg, cx, cy, radius, yawDegrees, colors);
            case MINIMAL -> drawMinimal(ctx, font, cx, cy, radius, angle, colors);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // DISC / RING — the classic dial, span-batched. RING is the same geometry with no face fill.
    // ------------------------------------------------------------------------------------------------

    private static void drawDisc(GuiGraphicsExtractor ctx, Font font, CompassHudConfig cfg,
                                 int cx, int cy, int radius, double angle, DialColors colors, boolean face) {
        drawDiscBase(ctx, cfg, cx, cy, radius, colors, face);
        drawCardinalTicks(ctx, cx, cy, radius, colors);
        drawNorthLabel(ctx, font, cx, cy, radius, colors);
        drawNeedles(ctx, cx, cy, radius, angle, colors);
        drawCenterDot(ctx, cx, cy, colors);
    }

    /** Largest |dx| with dx²+dy² ≤ r², or -1 when the row misses the circle. */
    private static int spanHalf(int r, int dy) {
        int rem = r * r - dy * dy;
        return rem < 0 ? -1 : (int) Math.sqrt(rem);
    }

    // ------------------------------------------------------------------------------------------------
    // ROSE — dial face + an 8-point compass rose (long cardinal diamonds, short intercardinal strokes).
    // ------------------------------------------------------------------------------------------------

    private static void drawRose(GuiGraphicsExtractor ctx, Font font, CompassHudConfig cfg,
                                 int cx, int cy, int radius, double angle, DialColors colors) {
        drawDiscBase(ctx, cfg, cx, cy, radius, colors, true);

        int len = radius - 4;
        int baseHalf = Math.max(1, radius / 8);
        // Cardinal diamonds: vertical pair (N/S) filled by row, horizontal pair (E/W) by column.
        for (int i = 0; i <= len; i++) {
            int half = Math.max(0, Math.round(baseHalf * (1.0f - i / (float) len)));
            ctx.fill(cx - half, cy - i, cx + half + 1, cy - i + 1, colors.muted()); // N
            ctx.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, colors.muted()); // S
            ctx.fill(cx - i, cy - half, cx - i + 1, cy + half + 1, colors.muted()); // W
            ctx.fill(cx + i, cy - half, cx + i + 1, cy + half + 1, colors.muted()); // E
        }
        // Intercardinal strokes at 45°, ~55% length.
        int diag = (int) Math.round((radius - 4) * 0.55 / Math.sqrt(2));
        for (int s = -1; s <= 1; s += 2) {
            for (int t = -1; t <= 1; t += 2) {
                drawLine(ctx, cx, cy, cx + s * diag, cy + t * diag, colors.muted());
            }
        }

        drawCardinalTicks(ctx, cx, cy, radius, colors);
        drawNorthLabel(ctx, font, cx, cy, radius, colors);
        drawNeedles(ctx, cx, cy, radius, angle, colors);
        drawCenterDot(ctx, cx, cy, colors);
    }

    /** Face + ring, span-batched: one fill per row segment instead of one per pixel. */
    private static void drawDiscBase(GuiGraphicsExtractor ctx, CompassHudConfig cfg,
                                     int cx, int cy, int radius, DialColors colors, boolean face) {
        int rIn = radius - 2; // ring thickness 2px, matching the old dial
        int faceColor = innerColor(cfg, colors.face());
        for (int dy = -radius; dy <= radius; dy++) {
            int half = spanHalf(radius, dy);
            if (half < 0) continue;
            int y = cy + dy;
            int halfIn = Math.abs(dy) <= rIn ? spanHalf(rIn, dy) : -1;
            if (halfIn < 0) {
                ctx.fill(cx - half, y, cx + half + 1, y + 1, colors.ring());
            } else {
                ctx.fill(cx - half, y, cx - halfIn, y + 1, colors.ring());
                ctx.fill(cx + halfIn + 1, y, cx + half + 1, y + 1, colors.ring());
                if (face) {
                    ctx.fill(cx - halfIn, y, cx + halfIn + 1, y + 1, faceColor);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // TAPE — a linear heading strip (modern-shooter style), vertically centered in the dial box so all
    // Pin & Grow / dock layout math (which sizes the compass as a diameter×diameter box) holds untouched.
    // ------------------------------------------------------------------------------------------------

    private static final String[] TAPE_POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final float TAPE_WINDOW_DEG = 60.0f; // headings shown within ±window

    private static int tapeStripHeight(int radius) {
        return Math.max(12, radius / 2);
    }

    /**
     * The look's TRUE content height inside its diameter×diameter dial box. TAPE draws a short strip
     * (plus the 2px caret overhang top and bottom) vertically centered in the box; every other look
     * fills the disc. Layout that positions the compass against other HUD geometry (the hotbar dock,
     * the Studio hitbox/border) must use this, not the box height — docking the phantom box was TEST
     * 28's "tape floats high above the hotbar" bug.
     */
    static int lookContentHeight(CompassHudConfig cfg, int diameter) {
        if (cfg.analogLook == CompassHudConfig.CompassLook.TAPE) {
            return Math.min(diameter, tapeStripHeight(diameter / 2) + 4);
        }
        return diameter;
    }

    private static void drawTape(GuiGraphicsExtractor ctx, Font font, CompassHudConfig cfg,
                                 int cx, int cy, int radius, float yawDegrees, DialColors colors) {
        int halfW = radius;
        int stripH = Math.max(12, radius / 2);
        int y0 = cy - stripH / 2;
        int y1 = cy + stripH / 2;

        ctx.fill(cx - halfW, y0, cx + halfW, y1, innerColor(cfg, colors.face()));
        ctx.fill(cx - halfW, y0, cx + halfW, y0 + 1, colors.ring());
        ctx.fill(cx - halfW, y1 - 1, cx + halfW, y1, colors.ring());

        drawTapeForeground(ctx, font, cfg, cx, cy, radius, yawDegrees, colors);
    }

    /** Ticks + labels + center caret (drawn over the strip, or over pack art in texture mode). */
    private static void drawTapeForeground(GuiGraphicsExtractor ctx, Font font, CompassHudConfig cfg,
                                           int cx, int cy, int radius, float yawDegrees, DialColors colors) {
        int halfW = radius;
        int stripH = Math.max(12, radius / 2);
        int y0 = cy - stripH / 2;
        int y1 = cy + stripH / 2;
        int usableHalf = halfW - 3;
        // Heading with 0 = north, +90 = east (same "+180" convention the needle angle uses).
        float heading = Mth.wrapDegrees(yawDegrees + 180.0f);

        // Minor ticks every 15°.
        for (int deg = 0; deg < 360; deg += 15) {
            float rel = Mth.wrapDegrees(deg - heading);
            if (Math.abs(rel) > TAPE_WINDOW_DEG) continue;
            int x = cx + Math.round(rel / TAPE_WINDOW_DEG * usableHalf);
            boolean major = deg % 45 == 0;
            int tickH = major ? 3 : 2;
            ctx.fill(x, y1 - 1 - tickH, x + 1, y1 - 1, major ? colors.ring() : colors.muted());
        }

        // Cardinal / intercardinal labels, scaled down on small strips. The label set honors the
        // Direction Format setting (TEST 28: the format button was inert for every analog look):
        // CARDINAL_4 labels only N/E/S/W (the 45° major ticks stay), DEGREES swaps letters for bare
        // heading numbers (0/45/90...), CARDINAL_8 is the classic full set.
        CompassHudConfig.DirectionMode dirMode =
                cfg.directionMode == null ? CompassHudConfig.DirectionMode.CARDINAL_8 : cfg.directionMode;
        float labelScale = Mth.clamp(radius / 32.0f, 0.55f, 1.0f);
        var pose = ctx.pose();
        for (int i = 0; i < TAPE_POINTS.length; i++) {
            int deg = i * 45;
            boolean cardinal = i % 2 == 0;
            if (dirMode == CompassHudConfig.DirectionMode.CARDINAL_4 && !cardinal) continue;
            float rel = Mth.wrapDegrees(deg - heading);
            if (Math.abs(rel) > TAPE_WINDOW_DEG) continue;
            String label = dirMode == CompassHudConfig.DirectionMode.DEGREES
                    ? Integer.toString(deg) : TAPE_POINTS[i];
            int color = i == 0 ? colors.needle() : (cardinal ? colors.ring() : colors.muted());
            float scale = cardinal ? labelScale : labelScale * 0.8f;
            if (dirMode == CompassHudConfig.DirectionMode.DEGREES) {
                scale = labelScale * (cardinal ? 0.9f : 0.8f); // 3-digit numbers need the trim
            }
            int x = cx + Math.round(rel / TAPE_WINDOW_DEG * usableHalf);
            pose.pushMatrix();
            pose.translate(x, y0 + 2);
            pose.scale(scale, scale);
            ctx.text(font, label, -font.width(label) / 2, 0, color, true);
            pose.popMatrix();
        }

        // Center caret: what you're facing right now.
        ctx.fill(cx, y0 - 2, cx + 1, y1 + 2, colors.needle());
        ctx.fill(cx - 1, y0 - 2, cx + 2, y0 - 1, colors.needle());
    }

    // ------------------------------------------------------------------------------------------------
    // MINIMAL — needle, rim ticks, and N. No face, no ring; the world shows through.
    // ------------------------------------------------------------------------------------------------

    private static void drawMinimal(GuiGraphicsExtractor ctx, Font font,
                                    int cx, int cy, int radius, double angle, DialColors colors) {
        drawCardinalTicks(ctx, cx, cy, radius, colors);
        drawNorthLabel(ctx, font, cx, cy, radius, colors);
        drawNeedles(ctx, cx, cy, radius, angle, colors);
        drawCenterDot(ctx, cx, cy, colors);
    }

    // ------------------------------------------------------------------------------------------------
    // Shared pieces (lifted verbatim from the old per-pixel dial so every look keeps its exact styling)
    // ------------------------------------------------------------------------------------------------

    private static void drawCardinalTicks(GuiGraphicsExtractor ctx, int cx, int cy, int radius, DialColors colors) {
        int tickLen = Math.max(2, radius / 6);
        ctx.fill(cx, cy - radius + 2, cx + 1, cy - radius + 2 + tickLen, colors.ring());          // N
        ctx.fill(cx, cy + radius - 2 - tickLen, cx + 1, cy + radius - 2, colors.muted());         // S
        ctx.fill(cx + radius - 2 - tickLen, cy, cx + radius - 2, cy + 1, colors.muted());         // E
        ctx.fill(cx - radius + 2, cy, cx - radius + 2 + tickLen, cy + 1, colors.muted());         // W
    }

    /** The radius-scaled "N" glyph (see the sizing rationale comment history in CompassHud @ U-A). */
    private static void drawNorthLabel(GuiGraphicsExtractor ctx, Font font, int cx, int cy, int radius, DialColors colors) {
        int tickLen = Math.max(2, radius / 6);
        String nLabel = "N";
        int nW = font.width(nLabel);
        float nScale = Mth.clamp(radius / 24.0f, 0.4f, 1.0f);
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate((float) (cx + 1), (float) (cy - radius + 2 + tickLen + 1));
        pose.scale(nScale, nScale);
        ctx.text(font, nLabel, -nW / 2, 0, colors.needle(), true);
        pose.popMatrix();
    }

    private static void drawNeedles(GuiGraphicsExtractor ctx, int cx, int cy, int radius, double angle, DialColors colors) {
        int needleLen = radius - 4;
        int nx = cx + (int) Math.round(Math.sin(angle) * needleLen);
        int ny = cy - (int) Math.round(Math.cos(angle) * needleLen);
        drawLine(ctx, cx, cy, nx, ny, colors.needle());

        int sx = cx - (int) Math.round(Math.sin(angle) * (needleLen * 0.6));
        int sy = cy + (int) Math.round(Math.cos(angle) * (needleLen * 0.6));
        drawLine(ctx, cx, cy, sx, sy, colors.ring());
    }

    private static void drawCenterDot(GuiGraphicsExtractor ctx, int cx, int cy, DialColors colors) {
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, colors.ring());
    }

    private static int innerColor(CompassHudConfig cfg, int faceRgb) {
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
}
