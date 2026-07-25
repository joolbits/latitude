package com.example.globe.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared scaffolding for the small hand-drawn / hand-scaled glyphs painted directly onto
 * {@link LatitudeHudStudioScreen} widgets -- NOT a library of artwork.
 *
 * <p>Minecraft's built-in font/unicode fallback renders arrows, grid icons, and similar tiny
 * symbols far too small and fuzzy to read at button size, so each of these glyphs is hand-drawn or
 * hand-scaled instead. That workaround has been independently reinvented at least three times in
 * {@code LatitudeHudStudioScreen} alone: the undo/redo arrows (pose-scaled font text, see
 * {@code drawButtonGlyph}), the canvas snap-grid icon (procedural fills, see {@code drawSnapGlyph}),
 * and the Color-Cycle-Speed lightning bolt (a hand-plotted bitmap of fills, see
 * {@code drawLightningGlyph}) -- each solving the same "make it readable" problem with its own copy
 * of the scale/center/draw mechanics. This class is THE place to put the next one.
 *
 * <p>Only the reusable <b>mechanics</b> live here: matrix scale+centering for a scaled font-text
 * glyph ({@link #drawScaledCenteredText}), and pixel-plotting for a hand-plotted bitmap glyph
 * ({@link #drawBitmap}). The snap-grid icon's own mechanic (procedural evenly-spaced gridlines +
 * lit/dim state color, centered in its own widget rect) was checked against these two and does not
 * share real structure with either -- it stays bespoke in {@code drawSnapGlyph}, same as the
 * create-screen's {@code RulesIcons} painters intentionally stay bespoke. The actual <b>artwork</b>
 * -- which glyph, which bit pattern, which color -- always stays with its caller; this class never
 * decides what a glyph looks like, only how it gets scaled/centered/plotted onto the screen.
 */
final class GlyphDraw {

    private GlyphDraw() {}

    /**
     * Draws {@code glyph} using {@code font}, scaled by {@code scale} and centered on
     * {@code (cx, cy)}. For glyphs where the plain font/unicode rendering is legible in shape but
     * simply too small at the target size (e.g. the HUD Studio undo/redo arrows) -- as opposed to a
     * hand-plotted bitmap glyph, see {@link #drawBitmap}.
     *
     * <p>Pushes/pops the graphics pose matrix around the draw so callers never need to manage the
     * matrix stack themselves. Text is drawn via the plain (un-shadowed) {@code ctx.text} overload,
     * matching every current call site.
     */
    static void drawScaledCenteredText(GuiGraphicsExtractor ctx, Font font, String glyph,
            int cx, int cy, float scale, int color) {
        int glyphW = font.width(glyph);
        var m = ctx.pose();
        m.pushMatrix();
        try {
            m.translate(cx, cy);
            m.scale(scale, scale);
            ctx.text(font, glyph, -glyphW / 2, -font.lineHeight / 2, color);
        } finally {
            m.popMatrix();
        }
    }

    /**
     * Radius (screen pixels, already {@code +1} padded) of a square backing plate sized to fully
     * cover a glyph drawn via {@link #drawScaledCenteredText} with the given {@code font}/
     * {@code scale} -- i.e. callers should {@code ctx.fill(cx - r, cy - r, cx + r, cy + r, ...)}
     * behind the glyph for a High-Contrast legibility plate. Pure sizing math; whether/how to paint
     * the plate (alpha, when it applies) stays a11y-mode policy owned by the caller.
     */
    static int scaledTextPlateRadius(Font font, float scale) {
        return (int) Math.ceil(font.lineHeight * scale / 2f) + 1;
    }

    /**
     * Plots a small hand-drawn bitmap glyph from {@code rows}, one {@code int} per row, read as the
     * low {@code bitsPerRow} bits (MSB-first: bit {@code bitsPerRow - 1} is the leftmost pixel).
     * Each set bit becomes one {@code color} pixel at {@code (x + col, y + row)}, top-left anchored.
     * This is the plotting mechanic only -- the bit pattern itself (the artwork, e.g. the
     * Color-Cycle-Speed lightning bolt) stays with the caller.
     */
    static void drawBitmap(GuiGraphicsExtractor ctx, int[] rows, int bitsPerRow, int x, int y, int color) {
        for (int ry = 0; ry < rows.length; ry++) {
            int bits = rows[ry];
            for (int rx = 0; rx < bitsPerRow; rx++) {
                if ((bits & (1 << (bitsPerRow - 1 - rx))) != 0) {
                    ctx.fill(x + rx, y + ry, x + rx + 1, y + ry + 1, color);
                }
            }
        }
    }
}
