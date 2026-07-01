package com.example.globe.client.create;

import com.example.globe.client.GlobeWorldSize;
import com.example.globe.util.LatitudeBands;
import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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
    // Chosen for a clear climate progression with real value+hue contrast between adjacent bands (Peetsa TEST 7:
    // "bands don't have enough contrast, they all compete"): deep tropical green -> warm arid ochre ->
    // temperate green -> cool subpolar blue -> pale polar ice.
    private static final int[] BAND_COLORS = {
            0xFF157A34, // tropical    — deep green
            0xFFC7A648, // subtropical — warm arid ochre
            0xFF5FA85C, // temperate   — lighter green
            0xFF5A86AC, // subpolar    — cool blue
            0xFFBBD3E0  // polar        — pale ice
    };

    private static final int OCEAN_COLOR = 0xFF162A3F;
    // Muted map-land tones for the continent outline drawn under the (now translucent) climate bands.
    private static final int LAND_COLOR = 0xFF4E6B41;
    private static final int LAND_COLOR_2 = 0xFF5A7049;
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
    public static void render(GuiGraphicsExtractor ctx, int cx, int cy, int maxRadius,
                              GlobeWorldSize size, LatitudeBands.Band selectedZone,
                              int panelRight, int panelTop, int panelBottom,
                              net.minecraft.client.gui.Font textRenderer) {

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
            int labelY = cy + yOff - Math.round(textRenderer.lineHeight * labelScale / 2f); // center text vertically on the line

            // Disc edge X at this Y offset
            float frac = 1.0f - (float) (yOff * yOff) / (float) (radius * radius);
            int halfW = frac > 0 ? (int) (Math.sqrt(frac) * radius) : 0;
            int labelX = cx + halfW + Math.max(6, (int) (radius * 0.08f)); // extra breathing room scales with disc

            // Clipping fallback:
            // (1) Normal placement 2px outside disc
            // (2) Nudge inward if overflows panel bounds
            // (3) Omit if still overflowing
            int scaledW = Math.round(textRenderer.width(label) * labelScale);
            if (labelX + scaledW > panelRight - 2) {
                labelX = panelRight - 2 - scaledW; // nudge inward
            }
            if (labelY < panelTop || labelY + Math.round(textRenderer.lineHeight * labelScale) > panelBottom) {
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
        int diaW = Math.round(textRenderer.width(diaLabel) * labelScale);
        int diaY = cy + radius + Math.max(6, (int) (radius * 0.08f));
        if (diaY + Math.round(textRenderer.lineHeight * labelScale) <= panelBottom) {
            drawScaledText(ctx, textRenderer, diaLabel, cx - diaW / 2, diaY, labelScale, MUTED);
        }
    }

    // ── Check if a degree value falls on the selected band's edges ──
    private static boolean isOnSelectedEdge(double deg, LatitudeBands.Band selected) {
        return Math.abs(deg - selected.lowDeg()) < 0.01 || Math.abs(deg - selected.highDeg()) < 0.01;
    }

    // ── Draw a single horizontal latitude line clipped to the disc ──
    private static void drawLatitudeLine(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int yOff, int color) {
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

    private static void drawScaledText(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr,
                                       String text, int x, int y, float scale, int color) {
        var matrices = ctx.pose();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        ctx.text(tr, text, 0, 0, color, false);
        matrices.popMatrix();
    }

    // ── Dashed circle (decorative, for small worlds) ──
    private static void drawDashedCircle(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int color) {
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
    private static void fillCircle(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            float frac = 1.0f - (float) (dy * dy) / (float) (radius * radius);
            if (frac <= 0) continue;
            int halfW = (int) (Math.sqrt(frac) * radius);
            if (halfW <= 0) continue;
            ctx.fill(cx - halfW, cy + dy, cx + halfW, cy + dy + 1, color);
        }
    }

    // ── Fill a horizontal band strip within a circular disc ──
    private static void fillBandStrip(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int yStart, int yEnd, int color) {
        for (int dy = yStart; dy < yEnd; dy++) {
            float frac = 1.0f - (float) (dy * dy) / (float) (radius * radius);
            if (frac <= 0) continue;
            int halfW = (int) (Math.sqrt(frac) * radius);
            if (halfW <= 0) continue;
            ctx.fill(cx - halfW, cy + dy, cx + halfW, cy + dy + 1, color);
        }
    }

    // ── Rectangle equivalents of the circle primitives above, for the Mercator (2:1) atlas preview: same
    // shapes, just without the chord-mask clip — a constant halfW instead of a per-scanline sqrt(frac)*radius.
    // Guarded the same way the circle primitives above are (halfW <= 0 -> no-op): width is now an
    // independently-supplied parameter rather than derived from a single radius, so a degenerate caller-
    // supplied width can't be assumed away the way it could when width and height always came from one value. ──
    private static void fillRect(GuiGraphicsExtractor ctx, int cx, int cy, int halfW, int halfH, int color) {
        if (halfW <= 0) return;
        ctx.fill(cx - halfW, cy - halfH, cx + halfW, cy + halfH, color);
    }

    private static void fillBandStripRect(GuiGraphicsExtractor ctx, int cx, int cy, int halfW, int yStart, int yEnd, int color) {
        if (halfW <= 0) return;
        ctx.fill(cx - halfW, cy + yStart, cx + halfW, cy + yEnd, color);
    }

    private static void drawLatitudeLineRect(GuiGraphicsExtractor ctx, int cx, int cy, int halfW, int yOff, int color) {
        if (halfW <= 0) return;
        ctx.fill(cx - halfW, cy + yOff, cx + halfW, cy + yOff + 1, color);
    }

    /**
     * Render a compact latitude preview within a {@code width × height} pixel area — a circle for a Legacy 1:1
     * (CLASSIC) world, a rectangle for a Mercator 2:1 world (width == 2 × height). 5-band fill with a
     * warm-gold outline on the selected band's edges. No labels, no grid lines. Returns immediately if
     * {@code height < 20}.
     *
     * @param context      draw context
     * @param x            top-left X of the bounding area
     * @param y            top-left Y of the bounding area
     * @param width        bounding width in pixels (== height for Legacy, == 2×height for Mercator)
     * @param height       bounding height in pixels
     * @param selectedBand the currently selected latitude band
     * @param shape        world shape (Legacy 1:1 → square, Mercator → 2:1 rectangle). Retained for
     *                     call-site clarity; the actual square-vs-rectangle difference is carried by the
     *                     width:height ratio passed in, not branched on here.
     */
    public static void renderCompact(GuiGraphicsExtractor context, int x, int y, int width, int height,
                                     LatitudeBands.Band selectedBand, com.example.globe.world.LatitudeBiomes.GlobeShape shape) {
        if (height < 20) return;

        int halfW = width / 2;
        int halfH = height / 2;
        int cx = x + halfW;
        int cy = y + halfH;
        // Both shapes draw as rectangles: a wide 2:1 rectangle for Mercator, and a SQUARE for Legacy 1:1
        // (where halfW == halfH). The atlas matches the world border shape — square for Legacy — per Peetsa's
        // TEST 1 feedback (finding A2). The square-vs-rectangle difference is carried entirely by the
        // width:height ratio, computed upstream in computePreviewLayout; band-boundary math stays halfH-based
        // (bands vary along the vertical/latitude axis in both shapes), only the horizontal extent (halfW)
        // differs between the two world shapes.

        int left = cx - halfW, top = cy - halfH, right = cx + halfW, bottom = cy + halfH;

        // ── Ocean base ──
        fillRect(context, cx, cy, halfW, halfH, OCEAN_COLOR);

        // ── Continent outline ── stylized landmasses so the latitude bands read as a climate overlay on a
        // world map (not a flag). Deterministic blobs positioned by fraction of the atlas, clipped to bounds;
        // scales with both shapes (Peetsa TEST 4 A1).
        drawContinents(context, cx, cy, halfW, halfH, left, top, right, bottom);

        // ── Latitude bands as a TRANSLUCENT climate wash over ocean + land ──
        LatitudeBands.Band[] bands = LatitudeBands.Band.values();
        for (int i = 0; i < bands.length; i++) {
            LatitudeBands.Band band = bands[i];
            boolean selected = (band == selectedBand);
            int baseColor = BAND_COLORS[i];

            int fillColor;
            if (selected) {
                int r = Math.min(255, (int) (((baseColor >> 16) & 0xFF) * 1.30f));
                int g = Math.min(255, (int) (((baseColor >> 8) & 0xFF) * 1.30f));
                int b = Math.min(255, (int) ((baseColor & 0xFF) * 1.30f));
                fillColor = (0xD6 << 24) | (r << 16) | (g << 8) | b;   // selected band: pops
            } else {
                fillColor = (baseColor & 0x00FFFFFF) | (0x62 << 24);   // others: present but land still shows
            }

            int yLow  = (int) (halfH * band.lowDeg()  / 90.0);
            int yHigh = (int) (halfH * band.highDeg() / 90.0);

            fillBandStripRect(context, cx, cy, halfW, yLow,  yHigh,  fillColor);
            fillBandStripRect(context, cx, cy, halfW, -yHigh, -yLow, fillColor);
        }

        // ── Faint graticule at each band boundary + equator (map look) ──
        for (LatitudeBands.Band band : bands) {
            int yb = (int) (halfH * band.highDeg() / 90.0);
            drawLatitudeLineRect(context, cx, cy, halfW,  yb, GRID_COLOR);
            drawLatitudeLineRect(context, cx, cy, halfW, -yb, GRID_COLOR);
        }
        drawLatitudeLineRect(context, cx, cy, halfW, 0, GRID_COLOR);

        // ── Gold outline on the selected band's edges (spawn-zone highlight) ──
        int selLow  = (int) (halfH * selectedBand.lowDeg()  / 90.0);
        int selHigh = (int) (halfH * selectedBand.highDeg() / 90.0);
        drawLatitudeLineRect(context, cx, cy, halfW,  selLow,  GOLD);
        drawLatitudeLineRect(context, cx, cy, halfW,  selHigh, GOLD);
        drawLatitudeLineRect(context, cx, cy, halfW, -selLow,  GOLD);
        drawLatitudeLineRect(context, cx, cy, halfW, -selHigh, GOLD);
    }

    // Frame the atlas with the VANILLA parchment map texture (Peetsa TEST 7: "grab the minecraft map graphic ...
    // don't invent one"). Drawn slightly larger than the atlas so the texture's decorative wooden border
    // surrounds the climate map; the parchment center is covered by the atlas drawn on top.
    private static final net.minecraft.resources.Identifier MAP_BG =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "textures/map/map_background");

    public static void drawAtlasFrame(GuiGraphicsExtractor ctx, int atlasLeft, int atlasTop, int atlasW, int atlasH) {
        int inset = Math.max(6, Math.min(atlasW, atlasH) / 8);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MAP_BG,
                atlasLeft - inset, atlasTop - inset, 0.0f, 0.0f,
                atlasW + inset * 2, atlasH + inset * 2, 128, 128);
    }

    private static final long CONTINENT_SEED = 0x1A7D0BE59C4F3E21L;

    // Organic continents from fractal value-noise instead of ovals (Peetsa TEST 7: "the continents are just
    // ovals, looks dumb"). Land where the noise field, suppressed toward the atlas edges so seas surround the
    // landmasses, exceeds sea level. Filled as horizontal runs per row (few draw calls, not per-pixel).
    private static void drawContinents(GuiGraphicsExtractor ctx, int cx, int cy, int halfW, int halfH,
                                       int clipL, int clipT, int clipR, int clipB) {
        int w = clipR - clipL, h = clipB - clipT;
        if (w <= 1 || h <= 1) return;
        double scale = Math.max(7.0, Math.min(w, h) / 6.0); // noise cell size in px
        for (int py = clipT; py < clipB; py++) {
            double v = (double) (py - clipT) / (h - 1);
            int runStart = -1, runCol = 0;
            for (int px = clipL; px <= clipR; px++) {
                int col = 0;
                if (px < clipR) {
                    double u = (double) (px - clipL) / (w - 1);
                    double edge = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v)); // 0 at border, ~0.5 center
                    double land = fbm((px - clipL) / scale, (py - clipT) / scale) - 0.52 + edge * 0.55;
                    if (land > 0.0) {
                        col = land > 0.14 ? LAND_COLOR_2 : LAND_COLOR; // interior slightly lighter than coast
                    }
                }
                if (col != runCol) {
                    if (runStart >= 0 && runCol != 0) ctx.fill(runStart, py, px, py + 1, runCol);
                    runStart = col != 0 ? px : -1;
                    runCol = col;
                }
            }
        }
    }

    private static double fbm(double x, double y) {
        double sum = 0, amp = 0.5, freq = 1.0;
        for (int o = 0; o < 3; o++) {
            sum += amp * valueNoise(x * freq, y * freq, CONTINENT_SEED + o * 1013L);
            freq *= 2.03;
            amp *= 0.5;
        }
        return sum; // ~[0,1]
    }

    private static double valueNoise(double x, double y, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        double n00 = hash01(x0, y0, seed), n10 = hash01(x0 + 1, y0, seed);
        double n01 = hash01(x0, y0 + 1, seed), n11 = hash01(x0 + 1, y0 + 1, seed);
        double a = n00 + (n10 - n00) * sx;
        double b = n01 + (n11 - n01) * sx;
        return a + (b - a) * sy;
    }

    private static double hash01(int x, int y, long seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ seed;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return ((h >>> 40) & 0xFFFFFFL) / (double) 0xFFFFFF;
    }
}
