package com.example.globe.client.create;

import com.example.globe.util.LatitudeBands;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Renders the live latitude preview for the bespoke create-world screen (compact square/2:1 atlas +
 * vanilla parchment frame). The original full planisphere disc renderer (labels, dashed rings, eased
 * radius scaling) was superseded by renderCompact and deleted in U-E — recover from git history if a
 * disc view is ever wanted again.
 */
public final class LatitudePlanisphereRenderer {

    private LatitudePlanisphereRenderer() {}

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
    private static final int GRID_COLOR = 0x60FFFFFF; // semi-transparent white for latitude lines

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
     * warm-gold outline on the selected band's edges and a faint graticule at each band boundary; degree
     * labels are drawn by the caller (which knows the panel bounds). Returns immediately if
     * {@code height < 20}.
     *
     * @param context      draw context
     * @param x            top-left X of the bounding area
     * @param y            top-left Y of the bounding area
     * @param width        bounding width in pixels (== height for Legacy, == 2×height for Mercator)
     * @param height       bounding height in pixels
     * @param selectedBand the currently selected latitude band, or null for no highlight (the create
     *                     screen's "Random" spawn-zone selection — every band renders unselected)
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
                fillColor = (0xE6 << 24) | (r << 16) | (g << 8) | b;   // selected band: pops
            } else {
                fillColor = (baseColor & 0x00FFFFFF) | (0x86 << 24);   // others: bands read clearly, land still shows
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

        // ── Gold outline on the selected band's edges (spawn-zone highlight; none for Random) ──
        if (selectedBand != null) {
            int selLow  = (int) (halfH * selectedBand.lowDeg()  / 90.0);
            int selHigh = (int) (halfH * selectedBand.highDeg() / 90.0);
            drawLatitudeLineRect(context, cx, cy, halfW,  selLow,  GOLD);
            drawLatitudeLineRect(context, cx, cy, halfW,  selHigh, GOLD);
            drawLatitudeLineRect(context, cx, cy, halfW, -selLow,  GOLD);
            drawLatitudeLineRect(context, cx, cy, halfW, -selHigh, GOLD);
        }
    }

    // Frame the atlas with the VANILLA parchment map texture (Peetsa TEST 7: "grab the minecraft map graphic ...
    // don't invent one"). Drawn slightly larger than the atlas so the texture's decorative wooden border
    // surrounds the climate map; the parchment center is covered by the atlas drawn on top.
    private static final net.minecraft.resources.Identifier MAP_BG =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "textures/map/map_background.png");

    public static void drawAtlasFrame(GuiGraphicsExtractor ctx, int frameLeft, int frameTop, int frameW, int frameH) {
        // Fill the reserved box with the vanilla map texture; the climate map draws inset on top, leaving the
        // texture's decorative border showing as a frame. Keeps the frame INSIDE the layout box (no overflow /
        // clipping), so the atlas sits centered in the frame (Peetsa TEST 9).
        //
        // map_background.png is 64x64. Use the region-blit overload (destination size separate from source
        // region) to STRETCH the whole texture over the frame box, so the parchment border scales evenly on all
        // four sides. The prior call passed 128x128 as the texture size on the 1:1-mapping overload, which made
        // the frame sample texels 1:1 into a 128-space that was really 64px — so the right/bottom of the frame
        // read past the real texture (garbage border that changed with each atlas size). Args:
        // (x, y, uOffset, vOffset, destWidth, destHeight, regionWidth, regionHeight, textureWidth, textureHeight).
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MAP_BG,
                frameLeft, frameTop, 0.0f, 0.0f, frameW, frameH, 64, 64, 64, 64);
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
