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

    // ── "Random" spawn-zone animated sweep (Peetsa) ── one glow pulse travels the equator→pole latitude
    // range each period; SIGMA sets how many bands are lit at once (wider = softer, more overlap); the
    // front runs to POLE_FADE_DEG (past 90°) so the polar band fades out before the loop wraps back to the
    // equator. Purely cosmetic on the create screen; tune freely.
    private static final long RANDOM_SWEEP_PERIOD_MS = 3200L;
    private static final double RANDOM_SWEEP_SIGMA_DEG = 16.0;
    private static final double POLE_FADE_DEG = 108.0;
    // Fraction of each loop spent easing the pulse in (at the equator restart) and out (past the pole), so
    // the wrap breathes instead of popping. 0 = hard pop (old behavior); larger = longer, gentler fades.
    // Shared by the Random vertical sweep and the selected-band horizontal sweep below.
    private static final double SWEEP_FADE_FRAC = 0.22;

    // ── Selected-band horizontal glow (Peetsa) ── a picked band's OWN highlight shimmers with a glow crest
    // sweeping left→right across it, the same Gaussian idea as the Random sweep turned on its side. The band
    // never drops below SELECTED_BASE_GLOW so it always reads as clearly selected; the crest brightens it to
    // the full selected pop (glow == 1) as it passes. SIGMA is in fraction-of-width units.
    private static final long SELECTED_SWEEP_PERIOD_MS = 2600L;
    private static final double SELECTED_SWEEP_SIGMA = 0.18;
    // Lowered floor + stronger peak so the crest reads BOLD like the Random sweep (Peetsa: the shimmer was
    // too subtle). Base 0.35 still keeps the band clearly selected between crests; the crest drives it to a
    // brighter, fully-opaque pop (bolder than Random's 1.30x/0xE6).
    private static final double SELECTED_BASE_GLOW = 0.35;
    private static final float SELECTED_BRIGHT_GAIN = 0.45f;

    // ── Rectangle equivalents of the circle primitives above, for the Mercator (2:1) atlas preview: same
    // shapes, just without the chord-mask clip — a constant halfW instead of a per-scanline sqrt(frac)*radius.
    // Guarded the same way the circle primitives above are (halfW <= 0 -> no-op): width is now an
    // independently-supplied parameter rather than derived from a single radius, so a degenerate caller-
    // supplied width can't be assumed away the way it could when width and height always came from one value. ──
    private static void fillRect(GuiGraphicsExtractor ctx, int cx, int cy, int halfW, int halfH, int color) {
        if (halfW <= 0) return;
        ctx.fill(cx - halfW, cy - halfH, cx + halfW, cy + halfH, color);
    }

    /** Classic Hermite smoothstep: 0 at/below edge0, 1 at/above edge1, eased in between. */
    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static void fillBandStripRect(GuiGraphicsExtractor ctx, int cx, int cy, int halfW, int yStart, int yEnd, int color) {
        if (halfW <= 0) return;
        ctx.fill(cx - halfW, cy + yStart, cx + halfW, cy + yEnd, color);
    }

    private static void drawLatitudeLineRect(GuiGraphicsExtractor ctx, int cx, int cy, int halfW, int yOff, int color) {
        if (halfW <= 0) return;
        ctx.fill(cx - halfW, cy + yOff, cx + halfW, cy + yOff + 1, color);
    }

    // Draws a band strip whose HIGHLIGHT sweeps left→right: each vertical column's brightness/opacity follows
    // a Gaussian crest centered on `front` (fraction across the strip width), floored at SELECTED_BASE_GLOW so
    // the strip always reads as selected. Uses the exact bright/alpha mapping the Random sweep uses, so at the
    // crest peak (glow == 1) it matches the old static selected pop. Drawn in short column slices — a handful
    // of extra fills, only for the one selected band, only on a menu screen.
    private static void fillSelectedGlowStrip(GuiGraphicsExtractor ctx, int cx, int cy, int halfW,
                                              int yStart, int yEnd, int baseColor, double front, double env) {
        if (halfW <= 0) return;
        int left = cx - halfW, right = cx + halfW, width = right - left;
        if (width <= 0) return;
        int br = (baseColor >> 16) & 0xFF, bg = (baseColor >> 8) & 0xFF, bb = baseColor & 0xFF;
        int step = 2;
        for (int px = left; px < right; px += step) {
            double u = (px + step * 0.5 - left) / (double) width;   // column-center fraction 0..1
            double d = u - front;
            double crest = Math.exp(-(d * d) / (2.0 * SELECTED_SWEEP_SIGMA * SELECTED_SWEEP_SIGMA)) * env;
            double glow = SELECTED_BASE_GLOW + (1.0 - SELECTED_BASE_GLOW) * crest;
            float bright = 1.0f + SELECTED_BRIGHT_GAIN * (float) glow;
            int r = Math.min(255, (int) (br * bright));
            int g = Math.min(255, (int) (bg * bright));
            int b = Math.min(255, (int) (bb * bright));
            int a = 0x86 + (int) ((0xFF - 0x86) * glow);
            int col = (a << 24) | (r << 16) | (g << 8) | b;
            int x1 = Math.min(px + step, right);
            ctx.fill(px, cy + yStart, x1, cy + yEnd, col);
        }
    }

    // ── Shared zone-bar glow (Peetsa) ── the create screen's Spawn Zone tab draws a thin horizontal 5-segment
    // color bar (tropical→polar). Peetsa asked for the same Gaussian glow treatment the Atlas already carries,
    // so the crest math above is exposed here as two absolute-rect helpers the screen calls with ITS OWN
    // BAND_COLORS (the screen's bar palette differs from this renderer's atlas palette; keep them independent).
    // Both are wall-clock driven (same System.currentTimeMillis() idiom) and self-contained so the caller only
    // passes geometry + color — no per-frame phase bookkeeping at the call site.

    /**
     * SELECTED zone: fill one bar segment [{@code left},{@code top})..[{@code right},{@code bottom}) with a glow
     * crest sweeping left→right ACROSS that segment only, mirroring the Atlas selected-band shimmer
     * ({@link #fillSelectedGlowStrip}) turned into an absolute-rect draw. Floored at SELECTED_BASE_GLOW so the
     * segment always reads selected; the crest drives it to the full opaque pop as it passes. Same seam-fade
     * envelope (SWEEP_FADE_FRAC) so the loop restart breathes instead of popping. No-op on a degenerate rect.
     */
    public static void fillSelectedGlowSegment(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int baseColor) {
        if (right <= left || bottom <= top) return;
        int width = right - left;
        double sp = (System.currentTimeMillis() % SELECTED_SWEEP_PERIOD_MS) / (double) SELECTED_SWEEP_PERIOD_MS;
        double env = smoothstep(0.0, SWEEP_FADE_FRAC, sp) * smoothstep(0.0, SWEEP_FADE_FRAC, 1.0 - sp);
        int br = (baseColor >> 16) & 0xFF, bg = (baseColor >> 8) & 0xFF, bb = baseColor & 0xFF;
        int step = 2;
        for (int px = left; px < right; px += step) {
            double u = (px + step * 0.5 - left) / (double) width;   // column-center fraction 0..1 across the segment
            double d = u - sp;
            double crest = Math.exp(-(d * d) / (2.0 * SELECTED_SWEEP_SIGMA * SELECTED_SWEEP_SIGMA)) * env;
            double glow = SELECTED_BASE_GLOW + (1.0 - SELECTED_BASE_GLOW) * crest;
            float bright = 1.0f + SELECTED_BRIGHT_GAIN * (float) glow;
            int r = Math.min(255, (int) (br * bright));
            int g = Math.min(255, (int) (bg * bright));
            int b = Math.min(255, (int) (bb * bright));
            int a = 0x86 + (int) ((0xFF - 0x86) * glow);
            int col = (a << 24) | (r << 16) | (g << 8) | b;
            int x1 = Math.min(px + step, right);
            ctx.fill(px, top, x1, bottom, col);
        }
    }

    /**
     * RANDOM zone: overlay ONE glow crest that travels the WHOLE bar [{@code barLeft},{@code barRight}) left→right
     * once per period, taking on each segment's OWN color as it passes through — the Atlas Random sweep's
     * per-band color pickup, here horizontal. Each column brightens/alpha-boosts whichever segment's color it
     * currently sits over (a hard hue switch at each boundary, but a CONTINUOUS crest so the glow bleeds smoothly
     * across boundaries); away from the crest, alpha falls to 0 and the caller's dim base shows through untouched.
     * The seam-fade envelope eases the crest in at the left restart and out past the right, so the loop never pops.
     * {@code segColors} indexes the segments left→right; the bar is assumed evenly divided into segColors.length.
     */
    public static void fillRandomGlowBar(GuiGraphicsExtractor ctx, int barLeft, int barRight, int top, int bottom, int[] segColors) {
        if (barRight <= barLeft || bottom <= top || segColors == null || segColors.length == 0) return;
        int width = barRight - barLeft;
        int segCount = segColors.length;
        double phase = (System.currentTimeMillis() % SELECTED_SWEEP_PERIOD_MS) / (double) SELECTED_SWEEP_PERIOD_MS;
        double env = smoothstep(0.0, SWEEP_FADE_FRAC, phase) * smoothstep(0.0, SWEEP_FADE_FRAC, 1.0 - phase);
        int step = 2;
        for (int px = barLeft; px < barRight; px += step) {
            double u = (px + step * 0.5 - barLeft) / (double) width;   // column-center fraction 0..1 across the full bar
            double d = u - phase;
            double crest = Math.exp(-(d * d) / (2.0 * SELECTED_SWEEP_SIGMA * SELECTED_SWEEP_SIGMA)) * env;
            if (crest <= 0.004) continue;   // negligible: leave the caller's dim base showing, no wasted fill
            int seg = (int) (u * segCount);
            if (seg < 0) seg = 0;
            if (seg >= segCount) seg = segCount - 1;
            int baseColor = segColors[seg];
            float bright = 1.0f + SELECTED_BRIGHT_GAIN * (float) crest;
            int r = Math.min(255, (int) (((baseColor >> 16) & 0xFF) * bright));
            int g = Math.min(255, (int) (((baseColor >> 8) & 0xFF) * bright));
            int b = Math.min(255, (int) ((baseColor & 0xFF) * bright));
            int a = Math.min(255, (int) (0xFF * crest));   // alpha grows with the crest → boosts over the dim base
            int col = (a << 24) | (r << 16) | (g << 8) | b;
            int x1 = Math.min(px + step, barRight);
            ctx.fill(px, top, x1, bottom, col);
        }
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
        // "Random" spawn zone (selectedBand == null) gets a playful animated flourish instead of a static
        // wash: a glow pulse that starts at the equator and travels OUTWARD to both poles at once (Peetsa's
        // request). Because every band is drawn mirrored north+south, one advancing pulse reads as a double,
        // opposed outward scroll. frontDeg travels a little past the pole (to POLE_FADE_DEG) so the polar band
        // fully fades before the loop restarts back at the equator, keeping the wrap from popping. Wall-clock
        // driven (same System.currentTimeMillis() idiom as the Aurora compass theme); the create screen
        // redraws every frame, so it animates smoothly.
        boolean randomSweep = (selectedBand == null);
        double frontDeg = 0.0;
        double sweepEnv = 1.0;
        if (randomSweep) {
            double phase = (System.currentTimeMillis() % RANDOM_SWEEP_PERIOD_MS) / (double) RANDOM_SWEEP_PERIOD_MS;
            frontDeg = phase * POLE_FADE_DEG;
            // Ease the whole pulse IN as it (re)starts at the equator and OUT as it clears the pole, so the
            // loop breathes at the seam instead of popping a fully-lit equator band the instant it wraps
            // (Peetsa: visual harmony). Peak (env == 1) holds across the middle of the sweep.
            sweepEnv = smoothstep(0.0, SWEEP_FADE_FRAC, phase) * smoothstep(0.0, SWEEP_FADE_FRAC, 1.0 - phase);
        }
        // A picked band gets a glow crest sweeping left→right across its own highlighted color (Peetsa) --
        // the Random idea turned on its side. The band holds a SELECTED_BASE_GLOW floor so it always reads
        // selected; the crest brightens it to the full pop as it passes. Same seam-fade envelope as above.
        double selFront = 0.0, selEnv = 0.0;
        if (selectedBand != null) {
            double sp = (System.currentTimeMillis() % SELECTED_SWEEP_PERIOD_MS) / (double) SELECTED_SWEEP_PERIOD_MS;
            selFront = sp;
            selEnv = smoothstep(0.0, SWEEP_FADE_FRAC, sp) * smoothstep(0.0, SWEEP_FADE_FRAC, 1.0 - sp);
        }
        for (int i = 0; i < bands.length; i++) {
            LatitudeBands.Band band = bands[i];
            int baseColor = BAND_COLORS[i];
            int yLow  = (int) (halfH * band.lowDeg()  / 90.0);
            int yHigh = (int) (halfH * band.highDeg() / 90.0);

            if (band == selectedBand) {
                fillSelectedGlowStrip(context, cx, cy, halfW, yLow,  yHigh,  baseColor, selFront, selEnv);
                fillSelectedGlowStrip(context, cx, cy, halfW, -yHigh, -yLow, baseColor, selFront, selEnv);
                continue;
            }

            if (!randomSweep) {
                // A specific zone IS selected: every OTHER band stays fully transparent so the atlas map
                // graphic (ocean + continents + the faint latitude graticule) shows through cleanly -- only
                // the picked band carries color (Peetsa). The selected band was already drawn above.
                continue;
            }

            // Random sweep: every band animates. Gaussian glow keyed on how close this band's center
            // latitude is to the advancing pulse -- near the front it brightens and turns nearly opaque,
            // away from it it settles to the muted wash.
            double mid = (band.lowDeg() + band.highDeg()) / 2.0;
            double d = mid - frontDeg;
            double glow = Math.exp(-(d * d) / (2.0 * RANDOM_SWEEP_SIGMA_DEG * RANDOM_SWEEP_SIGMA_DEG)) * sweepEnv;
            float bright = 1.0f + 0.30f * (float) glow;
            int r = Math.min(255, (int) (((baseColor >> 16) & 0xFF) * bright));
            int g = Math.min(255, (int) (((baseColor >> 8) & 0xFF) * bright));
            int b = Math.min(255, (int) ((baseColor & 0xFF) * bright));
            int a = 0x86 + (int) ((0xE6 - 0x86) * glow);
            int fillColor = (a << 24) | (r << 16) | (g << 8) | b;

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
        if (selectedBand != null) {
            int selLow  = (int) (halfH * selectedBand.lowDeg()  / 90.0);
            int selHigh = (int) (halfH * selectedBand.highDeg() / 90.0);
            drawLatitudeLineRect(context, cx, cy, halfW,  selLow,  GOLD);
            drawLatitudeLineRect(context, cx, cy, halfW,  selHigh, GOLD);
            drawLatitudeLineRect(context, cx, cy, halfW, -selLow,  GOLD);
            drawLatitudeLineRect(context, cx, cy, halfW, -selHigh, GOLD);
        } else if (frontDeg <= 90.0) {
            // Random: a gold crest line rides the pulse front outward (mirrored N+S), echoing the selected-
            // zone gold edge. Only while the front is on-map (<=90°); it vanishes into the frame as the
            // pulse fades past the pole, then reappears at the equator on the next loop. Alpha eases in from
            // the equator and out toward the pole so it doesn't blink on/off at the loop seam.
            float edge = (float) Math.sin(Math.PI * (frontDeg / 90.0)); // 0 at equator/pole, 1 mid-sweep
            int ga = (int) (0xFF * Math.max(0.0f, edge));
            int goldFade = (ga << 24) | (GOLD & 0x00FFFFFF);
            int yFront = (int) (halfH * frontDeg / 90.0);
            drawLatitudeLineRect(context, cx, cy, halfW,  yFront, goldFade);
            drawLatitudeLineRect(context, cx, cy, halfW, -yFront, goldFade);
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
