package com.example.globe.client.create;

import com.example.globe.client.GlobeWorldSize;
import com.example.globe.util.LatitudeBands;
import net.minecraft.client.gui.DrawContext;

import java.util.Arrays;

/**
 * Renders a live 2D latitude disc for the bespoke create-world screen.
 * Responds to zone and size selection.
 */
public final class LatitudePlanisphereRenderer {

    private LatitudePlanisphereRenderer() {}

    // ── Scaling source of truth: diameter ──
    private static final int MAX_DIAMETER = Arrays.stream(GlobeWorldSize.values())
            .mapToInt(s -> s.borderRadiusBlocks * 2)
            .max()
            .orElse(40000);

    // ── Band native colors (ARGB, indexed by Band.ordinal()) ──
    private static final int[] BAND_COLORS = {
            0xFF1A6B3C, // tropical
            0xFF8B7332, // subtropical
            0xFF3D6B4A, // temperate
            0xFF4A6A7D, // subpolar
            0xFF6A8599  // polar
    };

    private static final int OCEAN_COLOR = 0xFF162A3F;
    private static final int GOLD = 0xFFD4A74A;
    private static final int MUTED = 0xFF8C8078;
    private static final int GRID_COLOR = 0x60FFFFFF; // semi-transparent white for latitude lines
    private static final float DISC_SCALE_BOOST = 1.15f;
    private static final float LABEL_BASE_SCALE = 0.90f;
    private static final float LABEL_MAX_SCALE = 1.10f;
    private static final float LABEL_MIN_SCALE = 0.75f;

    /**
     * Render the planisphere disc.
     *
     * @param ctx          draw context
     * @param cx           center X in screen coords
     * @param cy           center Y in screen coords
     * @param maxRadius    maximum pixel radius (for the largest world size)
     * @param size         current world size selection
     * @param selectedZone currently selected latitude band
     * @param panelRight   right edge of the panel (for label clipping)
     * @param panelTop     top edge of the panel (for label clipping)
     * @param panelBottom  bottom edge of the panel (for label clipping)
     * @param textRenderer text renderer for degree labels
     */
    public static void render(DrawContext ctx, int cx, int cy, int maxRadius,
                              GlobeWorldSize size, LatitudeBands.Band selectedZone,
                              int panelRight, int panelTop, int panelBottom,
                              net.minecraft.client.font.TextRenderer textRenderer) {

        // Disc pixel radius scaled by world diameter ratio (mild easing to smooth jumps)
        int diameter = size.borderRadiusBlocks * 2;
        float sizeRatio = diameter / (float) MAX_DIAMETER;
        float eased = (float) Math.pow(sizeRatio, 0.92f);
        int radius = Math.round(maxRadius * eased * DISC_SCALE_BOOST);
        radius = Math.min(radius, maxRadius);
        if (radius < 5) return;

        // Label/caption scale tiers driven by preview size
        float labelScale = computePreviewTextScale(sizeRatio);

        // ════════════════════════════════════════════
        // Frozen draw order (bottom to top):
        // 1. Ocean base
        // 2. Dashed ring (Itty Bitty / Tiny only)
        // 3. Band fills (10 strips)
        // 4. Latitude boundary lines
        // 5. Selected-band gold edge lines
        // 6. Degree labels
        // ════════════════════════════════════════════

        // ── 1. Ocean base ──
        fillCircle(ctx, cx, cy, radius, OCEAN_COLOR);

        // ── 2. Dashed ring (Itty Bitty and Tiny only) ──
        if (size == GlobeWorldSize.ITTY_BITTY || size == GlobeWorldSize.TINY) {
            int dashRadius = (int) (radius * 0.70f);
            drawDashedCircle(ctx, cx, cy, dashRadius, 0x30FFFFFF);
        }

        // ── 3. Band fills ──
        LatitudeBands.Band[] bands = LatitudeBands.Band.values();
        for (int i = 0; i < bands.length; i++) {
            LatitudeBands.Band band = bands[i];
            boolean selected = (band == selectedZone);
            int baseColor = BAND_COLORS[i];

            int fillColor;
            if (selected) {
                // Luminance lift: multiply RGB by 1.25, clamp to 255
                int r = Math.min(255, (int) (((baseColor >> 16) & 0xFF) * 1.25f));
                int g = Math.min(255, (int) (((baseColor >> 8) & 0xFF) * 1.25f));
                int b = Math.min(255, (int) ((baseColor & 0xFF) * 1.25f));
                fillColor = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                // Unselected: alpha 0x8C (55%)
                fillColor = (baseColor & 0x00FFFFFF) | (0x8C << 24);
            }

            int yLow = (int) (radius * band.lowDeg() / 90.0);
            int yHigh = (int) (radius * band.highDeg() / 90.0);

            // South hemisphere (positive Y = south in screen coords)
            fillBandStrip(ctx, cx, cy, radius, yLow, yHigh, fillColor);
            // North hemisphere
            fillBandStrip(ctx, cx, cy, radius, -yHigh, -yLow, fillColor);

            // Selected band: warm gold halo overlay (15% opacity gold #FFCC66)
            if (selected) {
                int haloColor = 0x26FFCC66; // 15% opacity
                fillBandStrip(ctx, cx, cy, radius, yLow, yHigh, haloColor);
                fillBandStrip(ctx, cx, cy, radius, -yHigh, -yLow, haloColor);
            }
        }

        // ── 4. Latitude boundary lines (at all band edges) ──
        // Boundaries: 0° (equator), 23.5°, 35°, 50°, 66.5°, 90° (pole)
        double[] boundaries = {0.0, 23.5, 35.0, 50.0, 66.5};
        for (double deg : boundaries) {
            int yOff = (int) (radius * deg / 90.0);
            drawLatitudeLine(ctx, cx, cy, radius, yOff, GRID_COLOR);
            if (deg > 0) {
                drawLatitudeLine(ctx, cx, cy, radius, -yOff, GRID_COLOR);
            }
        }

        // ── 5. Selected-band gold edge lines ──
        {
            int selLow = (int) (radius * selectedZone.lowDeg() / 90.0);
            int selHigh = (int) (radius * selectedZone.highDeg() / 90.0);
            // South hemisphere edges
            drawLatitudeLine(ctx, cx, cy, radius, selLow, GOLD);
            drawLatitudeLine(ctx, cx, cy, radius, selHigh, GOLD);
            // North hemisphere edges
            drawLatitudeLine(ctx, cx, cy, radius, -selLow, GOLD);
            drawLatitudeLine(ctx, cx, cy, radius, -selHigh, GOLD);
        }

        // ── 6. Degree labels (south hemisphere, right-aligned outside disc edge) ──
        double[] labelDegs = {0.0, 23.5, 35.0, 50.0, 66.5, 90.0};
        for (double deg : labelDegs) {
            String label = LatitudeCreateWorldScreen.formatDegree(deg);
            int yOff = (int) (radius * deg / 90.0);
            int labelY = cy + yOff - Math.round(textRenderer.fontHeight * labelScale / 2f); // center text vertically on the line

            // Disc edge X at this Y offset
            float frac = 1.0f - (float) (yOff * yOff) / (float) (radius * radius);
            int halfW = frac > 0 ? (int) (Math.sqrt(frac) * radius) : 0;
            int labelX = cx + halfW + Math.max(6, (int) (radius * 0.08f)); // extra breathing room scales with disc

            // Clipping fallback:
            // (1) Normal placement 2px outside disc
            // (2) Nudge inward if overflows panel bounds
            // (3) Omit if still overflowing
            int scaledW = Math.round(textRenderer.getWidth(label) * labelScale);
            if (labelX + scaledW > panelRight - 2) {
                labelX = panelRight - 2 - scaledW; // nudge inward
            }
            if (labelY < panelTop || labelY + Math.round(textRenderer.fontHeight * labelScale) > panelBottom) {
                continue; // omit — vertical overflow
            }
            if (labelX + scaledW > panelRight - 2) {
                continue; // still overflows after nudge — omit
            }

            boolean isSelected = isOnSelectedEdge(deg, selectedZone);
            int labelColor = isSelected ? GOLD : MUTED;
            drawScaledText(ctx, textRenderer, label, labelX, labelY, labelScale, labelColor);
        }

        // Optional diameter label below disc (single centered line)
        String diaLabel = String.format(java.util.Locale.ROOT, "%,d blocks", diameter);
        int diaW = Math.round(textRenderer.getWidth(diaLabel) * labelScale);
        int diaY = cy + radius + Math.max(6, (int) (radius * 0.08f));
        if (diaY + Math.round(textRenderer.fontHeight * labelScale) <= panelBottom) {
            drawScaledText(ctx, textRenderer, diaLabel, cx - diaW / 2, diaY, labelScale, MUTED);
        }
    }

    // ── Check if a degree value falls on the selected band's edges ──
    private static boolean isOnSelectedEdge(double deg, LatitudeBands.Band selected) {
        return Math.abs(deg - selected.lowDeg()) < 0.01 || Math.abs(deg - selected.highDeg()) < 0.01;
    }

    // ── Draw a single horizontal latitude line clipped to the disc ──
    private static void drawLatitudeLine(DrawContext ctx, int cx, int cy, int radius, int yOff, int color) {
        float frac = 1.0f - (float) (yOff * yOff) / (float) (radius * radius);
        if (frac <= 0) return;
        int halfW = (int) (Math.sqrt(frac) * radius);
        if (halfW <= 0) return;
        ctx.fill(cx - halfW, cy + yOff, cx + halfW, cy + yOff + 1, color);
    }

    private static float computePreviewTextScale(float sizeRatio) {
        float scaled = LABEL_MIN_SCALE + sizeRatio * (LABEL_MAX_SCALE - LABEL_MIN_SCALE);
        return Math.max(LABEL_MIN_SCALE, Math.min(LABEL_MAX_SCALE, scaled * LABEL_BASE_SCALE / LABEL_MIN_SCALE));
    }

    private static void drawScaledText(DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
        String text, int x, int y, float scale, int color) {
        var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate((float) x, (float) y, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        ctx.drawText(tr, text, 0, 0, color, false);
        matrices.pop();
    }

    // ── Dashed circle (decorative, for small worlds) ──
    private static void drawDashedCircle(DrawContext ctx, int cx, int cy, int radius, int color) {
        int dashLen = 3;
        int gapLen = 3;
        for (int dy = -radius; dy <= radius; dy++) {
            float frac = 1.0f - (float) (dy * dy) / (float) (radius * radius);
            if (frac <= 0) continue;
            int halfW = (int) (Math.sqrt(frac) * radius);
            if (halfW <= 0) continue;

            // Left edge dash
            int pos = (dy + radius) % (dashLen + gapLen);
            if (pos < dashLen) {
                ctx.fill(cx - halfW, cy + dy, cx - halfW + 1, cy + dy + 1, color);
            }
            // Right edge dash
            if (pos < dashLen) {
                ctx.fill(cx + halfW - 1, cy + dy, cx + halfW, cy + dy + 1, color);
            }
        }
    }

    // ── Fill a circular disc ──
    private static void fillCircle(DrawContext ctx, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            float frac = 1.0f - (float) (dy * dy) / (float) (radius * radius);
            if (frac <= 0) continue;
            int halfW = (int) (Math.sqrt(frac) * radius);
            if (halfW <= 0) continue;
            ctx.fill(cx - halfW, cy + dy, cx + halfW, cy + dy + 1, color);
        }
    }

    // ── Fill a horizontal band strip within a circular disc ──
    private static void fillBandStrip(DrawContext ctx, int cx, int cy, int radius, int yStart, int yEnd, int color) {
        for (int dy = yStart; dy < yEnd; dy++) {
            float frac = 1.0f - (float) (dy * dy) / (float) (radius * radius);
            if (frac <= 0) continue;
            int halfW = (int) (Math.sqrt(frac) * radius);
            if (halfW <= 0) continue;
            ctx.fill(cx - halfW, cy + dy, cx + halfW, cy + dy + 1, color);
        }
    }

    /**
     * Render a compact latitude disc within a {@code size × size} pixel area.
     * 5-band filled disc with a warm-gold ring outline on the selected band arc.
     * No labels, no grid lines. Returns immediately if {@code size < 20}.
     *
     * @param context      draw context
     * @param x            top-left X of the bounding square
     * @param y            top-left Y of the bounding square
     * @param size         both width and height of the bounding square (pixels)
     * @param selectedBand the currently selected latitude band
     */
    public static void renderCompact(DrawContext context, int x, int y, int size, LatitudeBands.Band selectedBand) {
        if (size < 20) return;

        int radius = size / 2;
        int cx = x + radius;
        int cy = y + radius;

        // ── Ocean base ──
        fillCircle(context, cx, cy, radius, OCEAN_COLOR);

        // ── Band fills ──
        LatitudeBands.Band[] bands = LatitudeBands.Band.values();
        for (int i = 0; i < bands.length; i++) {
            LatitudeBands.Band band = bands[i];
            boolean selected = (band == selectedBand);
            int baseColor = BAND_COLORS[i];

            int fillColor;
            if (selected) {
                int r = Math.min(255, (int) (((baseColor >> 16) & 0xFF) * 1.25f));
                int g = Math.min(255, (int) (((baseColor >> 8) & 0xFF) * 1.25f));
                int b = Math.min(255, (int) ((baseColor & 0xFF) * 1.25f));
                fillColor = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                fillColor = (baseColor & 0x00FFFFFF) | (0x8C << 24);
            }

            int yLow  = (int) (radius * band.lowDeg()  / 90.0);
            int yHigh = (int) (radius * band.highDeg() / 90.0);

            fillBandStrip(context, cx, cy, radius, yLow,  yHigh,  fillColor);
            fillBandStrip(context, cx, cy, radius, -yHigh, -yLow, fillColor);
        }

        // ── Gold ring outline on selected band edges ──
        int selLow  = (int) (radius * selectedBand.lowDeg()  / 90.0);
        int selHigh = (int) (radius * selectedBand.highDeg() / 90.0);
        drawLatitudeLine(context, cx, cy, radius,  selLow,  GOLD);
        drawLatitudeLine(context, cx, cy, radius,  selHigh, GOLD);
        drawLatitudeLine(context, cx, cy, radius, -selLow,  GOLD);
        drawLatitudeLine(context, cx, cy, radius, -selHigh, GOLD);
    }
}
