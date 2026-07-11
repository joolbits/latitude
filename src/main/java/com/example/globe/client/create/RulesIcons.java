package com.example.globe.client.create;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Code-drawn iconography for the create-screen Rules sidebar (UI round 13 pass B).
 *
 * <p>Every glyph here is painted with {@code ctx.fill} rectangles / scanline discs -- no external
 * textures, no font glyphs (MC's unicode control glyphs render "comically small", the documented
 * reason the HUD Studio undo/redo icons had to be hand-drawn). Each icon reads at a glance inside an
 * ~16px box and honors two states: <b>lit</b> (option ON / a live action) = full warm color plus an
 * optional gold bloom, and <b>dim</b> (option OFF) = a desaturated grey-brown. State is <i>never</i>
 * conveyed by color alone -- the row also prints an On/Off word and (for the chest) changes the icon's
 * shape (closed vs. open lid), so the signal survives color-blindness (WCAG 1.4.1).
 *
 * <p>All methods are pure drawing given a normalized origin {@code (ox,oy)} and box size {@code s};
 * positions are computed as fractions of {@code s} so a glyph scales cleanly to any icon box. The
 * caller supplies the animation clock ({@code timeMs}, wall-clock, the same idiom every other
 * animation on this screen uses) and a {@code glow} intensity so motion can later be gated by a
 * Reduce-Motion setting from one place.
 */
final class RulesIcons {

    private RulesIcons() {}

    // ── Palette ──
    static final int GOLD      = 0xFFF2C24E;
    static final int GOLD_DK   = 0xFFB8892F;
    private static final int DIM       = 0xFF6E625A; // OFF icon body
    private static final int DIM_DK    = 0xFF4A423C; // OFF icon shade / outline
    private static final int WARM       = 0xFFE8D8B8; // structures / scroll parchment (lit)
    private static final int WOOD       = 0xFF9A6636; // chest wood, body (lit)
    private static final int WOOD_DK    = 0xFF5E3C20; // chest wood, outline / trim (lit)
    private static final int WOOD_LID   = 0xFFC08A4E; // chest wood, lid -- slightly lighter than body (lit)
    private static final int WOOD_LOWER = 0xFF7A4F29; // chest wood, lower body -- slightly darker (lit)
    private static final int DIM_LID    = 0xFF8B7F76; // chest lid (OFF) -- slightly lighter than DIM
    private static final int DIM_LOWER  = 0xFF585049; // chest lower body (OFF) -- slightly darker than DIM
    private static final int LATCH_DK   = 0xFF1A1410; // chest keyhole slit -- near-black metal, both states
    private static final int NEEDLE_N   = 0xFFE0533B; // compass north point (red)
    private static final int NEEDLE_S   = 0xFFF0E7D6; // compass south point (ivory)
    private static final int SCREEN_DK  = 0xFF201B15; // HUD monitor face
    private static final int GLOW_RGB   = 0x00F2C24E; // warm bloom (alpha added per-layer)

    private static int px(int ox, float frac, int s) {
        return ox + Math.round(frac * s);
    }

    private static int pick(boolean lit, int litColor, int dimColor) {
        return lit ? litColor : dimColor;
    }

    // ── Primitive helpers ──

    private static void strokeRect(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int argb) {
        ctx.fill(x0, y0, x1, y0 + 1, argb);
        ctx.fill(x0, y1 - 1, x1, y1, argb);
        ctx.fill(x0, y0, x0 + 1, y1, argb);
        ctx.fill(x1 - 1, y0, x1, y1, argb);
    }

    private static void fillDisc(GuiGraphicsExtractor ctx, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            ctx.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, argb);
        }
    }

    private static void strokeRing(GuiGraphicsExtractor ctx, int cx, int cy, int r, int thick, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                double d = Math.sqrt((double) dx * dx + (double) dy * dy);
                if (d <= r + 0.5 && d >= r - thick + 0.5) {
                    ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
                }
            }
        }
    }

    /** Soft warm bloom behind a lit icon -- two translucent gold discs (big+faint over small+brighter). */
    static void glow(GuiGraphicsExtractor ctx, int cx, int cy, int r, float intensity) {
        if (intensity <= 0f || r <= 0) return;
        int aOuter = Math.round(Math.min(1f, intensity) * 15f);
        int aInner = Math.round(Math.min(1f, intensity) * 22f);
        if (aOuter > 0) fillDisc(ctx, cx, cy, r, (aOuter << 24) | GLOW_RGB);
        if (aInner > 0) fillDisc(ctx, cx, cy, Math.max(1, r * 2 / 3), (aInner << 24) | GLOW_RGB);
    }

    // ── Icons ──

    /** Commands: a "/" command-prompt slash inside a rounded chip -- MC commands begin with "/". */
    static void commands(GuiGraphicsExtractor ctx, int ox, int oy, int s, boolean lit) {
        int chip = pick(lit, GOLD_DK, DIM_DK);
        int slash = pick(lit, GOLD, DIM);
        strokeRect(ctx, px(ox, 0.10f, s), px(oy, 0.14f, s), px(ox, 0.90f, s), px(oy, 0.86f, s), chip);
        int steps = s;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = px(ox, 0.30f + 0.40f * t, s);
            int y = px(oy, 0.72f - 0.44f * t, s);
            ctx.fill(x - 1, y - 1, x + 1, y + 1, slash);
        }
    }

    /** Starting Compass: a rose -- outer ring + a N/S needle diamond (red north, ivory south) + hub. */
    static void compass(GuiGraphicsExtractor ctx, int ox, int oy, int s, boolean lit) {
        int cx = px(ox, 0.5f, s);
        int cy = px(oy, 0.5f, s);
        int r = Math.round(0.42f * s);
        int ring = pick(lit, GOLD, DIM);
        strokeRing(ctx, cx, cy, r, 2, ring);
        // North point (up) -- a triangle from hub to the top, filled by narrowing scanlines.
        int half = Math.max(2, Math.round(0.20f * s));
        int nColor = pick(lit, NEEDLE_N, DIM);
        int sColor = pick(lit, NEEDLE_S, DIM_DK);
        for (int i = 0; i < r; i++) {
            int w = Math.max(0, half - (half * i) / r);
            // north (above hub)
            ctx.fill(cx - w, cy - i, cx + w + 1, cy - i + 1, nColor);
            // south (below hub)
            ctx.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, sColor);
        }
        // Hub dot
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, pick(lit, GOLD_DK, DIM_DK));
    }

    /** Generate Structures: a pitched-roof village house outline with a doorway. */
    static void structures(GuiGraphicsExtractor ctx, int ox, int oy, int s, boolean lit) {
        int wall = pick(lit, WARM, DIM);
        int shade = pick(lit, GOLD_DK, DIM_DK);
        int roofApexY = px(oy, 0.12f, s);
        int eaveY = px(oy, 0.46f, s);
        int baseY = px(oy, 0.88f, s);
        int leftX = px(ox, 0.16f, s);
        int rightX = px(ox, 0.84f, s);
        int cx = px(ox, 0.5f, s);
        // Roof: filled triangle by widening scanlines from apex to the eave line.
        int roofH = Math.max(1, eaveY - roofApexY);
        int roofHalf = (rightX - leftX) / 2;
        for (int i = 0; i <= roofH; i++) {
            int w = (roofHalf * i) / roofH;
            ctx.fill(cx - w, roofApexY + i, cx + w + 1, roofApexY + i + 1, i == roofH ? shade : wall);
        }
        // Body walls (outline). NOTE: strokeRect's x1 is exclusive (its right edge lands at
        // x1-1), so a naive "rightX - 1" here made the right wall edge fall 1px short of the
        // left wall's inset -- the walls were centered ~0.5px right of cx while the roof (which
        // already used the exclusive-bound convention correctly) was centered exactly on cx.
        // Passing rightX (not rightX - 1) gives the right edge the same 1px inset from rightX
        // that leftX + 1 gives the left edge from leftX, so the body is symmetric about cx.
        strokeRect(ctx, leftX + 1, eaveY, rightX, baseY, wall);
        // Door
        int dW = Math.max(1, Math.round(0.10f * s));
        ctx.fill(cx - dW, px(oy, 0.60f, s), cx + dW + 1, baseY - 1, shade);
    }

    /**
     * Bonus Chest: closed lidded chest + dim when OFF, open chest + gold + rising glitter when ON.
     * Both states read as an actual MC chest: a lid band across the top third, a body below it that's
     * slightly darker near the base, and a centered dark keyhole/latch plate where the lid meets the
     * body -- the signature MC chest detail. The open lid is a raised trapezoid tilted back above the
     * box; 3 sparkles twinkle and rise on a slow wall-clock cycle. {@code glitter} gates the sparkles
     * (pass false for Reduce Motion later).
     */
    static void chest(GuiGraphicsExtractor ctx, int ox, int oy, int s, boolean lit, long timeMs, boolean glitter) {
        int lidC    = pick(lit, WOOD_LID, DIM_LID);     // lid -- slightly lighter
        int bodyC   = pick(lit, WOOD, DIM);             // upper body
        int lowerC  = pick(lit, WOOD_LOWER, DIM_LOWER); // lower body -- slightly darker
        int outline = pick(lit, WOOD_DK, DIM_DK);
        int trim    = pick(lit, GOLD_DK, DIM_DK);       // metal band at the lid/body seam
        int leftX = px(ox, 0.14f, s);
        int rightX = px(ox, 0.86f, s);
        int cx = px(ox, 0.5f, s);
        // Shared seam line: lid occupies the top third, body (upper + darker lower band) below it.
        int seamY  = px(oy, 0.42f, s);
        int lowerY = px(oy, 0.68f, s);
        int baseY  = px(oy, 0.86f, s);

        // Keyhole/latch plate centered on the seam -- drawn identically for both states.
        int plateW = Math.max(2, Math.round(0.16f * s));
        int plateHalf = plateW / 2;
        int plateTop = seamY - Math.max(1, Math.round(0.05f * s));
        int plateBot = seamY + Math.max(2, Math.round(0.08f * s));

        if (!lit) {
            // Closed lidded chest.
            int lidTop = px(oy, 0.20f, s);
            ctx.fill(leftX, lidTop, rightX, seamY, lidC);
            ctx.fill(leftX, seamY, rightX, lowerY, bodyC);
            ctx.fill(leftX, lowerY, rightX, baseY, lowerC);
            strokeRect(ctx, leftX, lidTop, rightX, baseY, outline);
            ctx.fill(leftX + 1, seamY, rightX - 1, seamY + 1, trim); // hinge/seam line

            ctx.fill(cx - plateHalf, plateTop, cx - plateHalf + plateW, plateBot, outline);
            ctx.fill(cx - 1, plateTop + 1, cx + 1, plateBot - 1, LATCH_DK); // keyhole slit
        } else {
            // Open chest: body stays put, lid tilts back, dark interior shows, keyhole plate stays
            // on the front lip.
            ctx.fill(leftX, seamY, rightX, lowerY, bodyC);
            ctx.fill(leftX, lowerY, rightX, baseY, lowerC);
            strokeRect(ctx, leftX, seamY, rightX, baseY, outline);

            // Open interior (dark opening at the top of the box)
            ctx.fill(leftX + 2, seamY, rightX - 2, seamY + Math.max(1, Math.round(0.08f * s)), 0xFF201008);

            // Latch plate remnant on the front lip
            ctx.fill(cx - plateHalf, seamY, cx - plateHalf + plateW, plateTop + Math.max(2, Math.round(0.05f * s)), outline);
            ctx.fill(cx - 1, seamY, cx + 1, seamY + Math.max(1, Math.round(0.04f * s)), LATCH_DK);

            // Raised, tilted-back lid: a trapezoid narrower at the top rear.
            int lidFrontY = seamY - 1;
            int lidBackY = px(oy, 0.14f, s);
            int lidH = Math.max(1, lidFrontY - lidBackY);
            for (int i = 0; i <= lidH; i++) {
                float t = i / (float) lidH;                 // 0 at front(bottom) -> 1 at back(top)
                int inset = Math.round(t * 0.10f * s);       // narrows toward the back
                int y = lidFrontY - i;
                ctx.fill(leftX + inset, y, rightX - inset, y + 1, i == lidH ? trim : lidC);
            }
            strokeRect(ctx, leftX + Math.round(0.10f * s), lidBackY, rightX - Math.round(0.10f * s), lidFrontY + 1, trim);
            // Gold rim highlight on the lid's front edge
            ctx.fill(cx - 1, lidFrontY, cx + 2, lidFrontY + 2, GOLD);

            // Glitter: 3 sparkles rising out of the chest, twinkling on a slow cycle.
            if (glitter) {
                float[] phase = {0.0f, 0.33f, 0.66f};
                int[] sx = {px(ox, 0.34f, s), px(ox, 0.52f, s), px(ox, 0.68f, s)};
                for (int k = 0; k < 3; k++) {
                    double cyc = ((timeMs / 1000.0) * 0.55 + phase[k]) % 1.0; // ~1.8s loop
                    int rise = (int) Math.round(cyc * (0.34f * s));            // travels upward
                    int y = seamY - 1 - rise;
                    // fade in then out across the cycle
                    float a = (float) Math.sin(cyc * Math.PI);
                    int alpha = Math.round(a * a * 235f);
                    if (alpha <= 8) continue;
                    int spark = (Math.min(255, alpha) << 24) | (GOLD & 0xFFFFFF);
                    int x = sx[k];
                    ctx.fill(x, y, x + 1, y + 1, spark);          // center
                    ctx.fill(x - 1, y, x, y + 1, spark);          // arms (a tiny +)
                    ctx.fill(x + 1, y, x + 2, y + 1, spark);
                    ctx.fill(x, y - 1, x + 1, y, spark);
                    ctx.fill(x, y + 1, x + 1, y + 2, spark);
                }
            }
        }
    }

    /** HUD Studio (action): a small monitor/layout glyph -- screen frame + a couple of HUD widgets + stand. */
    static void hudStudio(GuiGraphicsExtractor ctx, int ox, int oy, int s) {
        int frame = GOLD;
        int leftX = px(ox, 0.12f, s);
        int rightX = px(ox, 0.88f, s);
        int top = px(oy, 0.16f, s);
        int bot = px(oy, 0.70f, s);
        ctx.fill(leftX, top, rightX, bot, SCREEN_DK);
        strokeRect(ctx, leftX, top, rightX, bot, frame);
        // Mock HUD widgets on the "screen": a top bar + a corner block.
        ctx.fill(leftX + 2, top + 2, rightX - 4, top + 4, GOLD_DK);
        ctx.fill(rightX - 6, bot - 5, rightX - 3, bot - 2, GOLD);
        // Stand
        int cx = px(ox, 0.5f, s);
        ctx.fill(cx - 2, bot, cx + 3, bot + 1, GOLD_DK);
        ctx.fill(px(ox, 0.30f, s), px(oy, 0.86f, s), px(ox, 0.70f, s), px(oy, 0.90f, s), frame);
    }

    /** Game Rules (action): a scroll/list -- parchment with three text lines and a soft top/bottom curl. */
    static void scroll(GuiGraphicsExtractor ctx, int ox, int oy, int s) {
        int page = WARM;
        int ink = GOLD_DK;
        int leftX = px(ox, 0.20f, s);
        int rightX = px(ox, 0.80f, s);
        int top = px(oy, 0.14f, s);
        int bot = px(oy, 0.86f, s);
        ctx.fill(leftX, top, rightX, bot, page);
        strokeRect(ctx, leftX, top, rightX, bot, ink);
        // Curl accents top & bottom
        ctx.fill(leftX - 1, top, rightX + 1, top + 1, GOLD);
        ctx.fill(leftX - 1, bot - 1, rightX + 1, bot, GOLD);
        // Three list lines
        for (int i = 0; i < 3; i++) {
            int ly = px(oy, 0.34f + i * 0.18f, s);
            ctx.fill(leftX + 2, ly, rightX - 2, ly + 1, ink);
        }
    }
}
