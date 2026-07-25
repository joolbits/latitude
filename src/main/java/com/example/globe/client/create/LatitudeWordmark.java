package com.example.globe.client.create;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The shared LATITUDE wordmark (TEST 29: "I thought 'LATITUDE' at the top was going to look a little more
 * special"): scaled-up, letter-spaced gold with a dark-bronze letterpress under-layer, a slow warm bloom
 * breath, a few twinkling sparkle motes and flanking rules with diamond tips — chartroom-brass, matching the
 * panel-heading motif.
 *
 * <p>Lifted out of {@code LatitudeCreateWorldScreen.drawLatitudeWordmark} (UI round 13) so BOTH the create
 * screen and the bespoke loading overlay ({@code LevelLoadingScreenLatitudeOverlayMixin}) render the exact
 * same nameplate — the same coherence precedent as {@link LatitudePlanisphereRenderer#drawAtlasFrame}, already
 * shared between exactly these two screens. The create screen calls it at full strength ({@code scale 1.5},
 * {@code sparkleCount 4}); the loading screen calls it a touch quieter ({@code scale ~1.4},
 * {@code sparkleCount 2}) because that card already animates the compass needle, the phrase fade and the
 * reading-light word-wave — full twinkle would tip into clutter (creative-director loading-look review,
 * 2026-07-11 §4 R1).
 *
 * <p>One unified {@link #GOLD} ({@code 0xFFE8B64A}) drives the wordmark on both surfaces (that review's F2:
 * the loading screen and the create screen had drifted to two different golds for the same brand mark; the
 * same-day zone-title pass standardized on {@code 0xE8B64A}). This is the WORDMARK gold only — the create
 * screen keeps its own screen-wide {@code GOLD} for headings/dividers/zone names.
 *
 * <p>Reduce Motion (owned by the caller's {@code reduceMotion} argument) freezes the bloom breath and the gold
 * lift to a steady mid state and suppresses the sparkles entirely — exactly as the create screen did before
 * extraction.
 */
public final class LatitudeWordmark {

    private LatitudeWordmark() {
    }

    /** The one unified wordmark gold (chartroom latitude gold, Pillar 6 token). See the class doc for why both
     *  screens converge here. */
    public static final int GOLD = 0xFFE8B64A;

    private static final double TITLE_GLOW_PERIOD_SEC = 3.4;   // one full bloom breath (slow, not a strobe)
    private static final float  TITLE_GLOW_MIN = 0.35f;        // bloom intensity floor
    private static final float  TITLE_GLOW_MAX = 0.85f;        // bloom intensity ceiling
    private static final float  TITLE_GOLD_LIFT = 0.10f;       // +/-10% brightness breath on the gold letters
    private static final double TITLE_SPARKLE_PERIOD_SEC = 2.6; // one spawn->twinkle->fade->respawn cycle per mote

    /**
     * Draws the wordmark centered inside the rect {@code (rx, ry, rw, rh)}. Returns the height consumed, or
     * {@code 0} when the rect can't fit it (the caller then falls back to a plain line).
     *
     * @param scale        glyph scale (create screen 1.5, loading screen ~1.4)
     * @param sparkleCount concurrent twinkling motes (create screen 4, loading screen 0-2); 0 = none
     * @param reduceMotion when true, freeze the bloom/lift to a steady mid and suppress sparkles
     */
    public static int draw(GuiGraphicsExtractor context, Font font, int rx, int ry, int rw, int rh,
                           float scale, int sparkleCount, boolean reduceMotion) {
        final String text = "LATITUDE";
        int spacing = Math.max(1, 2);
        int rawW = 0;
        for (int i = 0; i < text.length(); i++) {
            rawW += font.width(String.valueOf(text.charAt(i)));
            if (i + 1 < text.length()) rawW += spacing;
        }
        int drawW = Math.round(rawW * scale);
        int drawH = Math.round(font.lineHeight * scale);
        if (drawW > rw - 8 || drawH > rh) {
            return 0;
        }
        int startX = rx + (rw - drawW) / 2;

        final double nowSec = System.currentTimeMillis() / 1000.0;
        final int glowMidY = ry + drawH / 2;

        // (1) Soft warm bloom pulsing on/behind the letters -- a row of translucent gold discs (RulesIcons.glow)
        // spanning the wordmark, breathing between TITLE_GLOW_MIN..MAX on a slow sine. Drawn BEFORE the glyphs.
        float glowT = reduceMotion
                ? 0.5f
                : (float) (0.5 + 0.5 * Math.sin(nowSec * 2.0 * Math.PI / TITLE_GLOW_PERIOD_SEC));
        float glowIntensity = TITLE_GLOW_MIN + (TITLE_GLOW_MAX - TITLE_GLOW_MIN) * glowT;
        int glowR = Math.max(2, drawH * 3 / 4);
        int glowStep = Math.max(1, glowR);
        for (int gx = startX; gx <= startX + drawW; gx += glowStep) {
            RulesIcons.glow(context, gx, glowMidY, glowR, glowIntensity);
        }

        // Slight brightness lift on the gold, breathing in lockstep with the bloom (steady mid when reduced).
        float lift = reduceMotion ? 1.0f : (float) (1.0 + TITLE_GOLD_LIFT * Math.sin(nowSec * 2.0 * Math.PI / TITLE_GLOW_PERIOD_SEC));
        int goldLit = liftBrightness(GOLD, lift);

        var m = context.pose();
        m.pushMatrix();
        try {
            m.translate(startX, ry);
            m.scale(scale, scale);
            int cx = 0;
            for (int i = 0; i < text.length(); i++) {
                String s = String.valueOf(text.charAt(i));
                context.text(font, s, cx + 1, 1, 0xFF3A2410, false); // letterpress under-layer
                context.text(font, s, cx, 0, goldLit, false);
                cx += font.width(s) + spacing;
            }
        } finally {
            m.popMatrix();
        }

        // (2) Sparkle motes: a few tiny gold/white twinkles at hash-scattered points over the wordmark, each
        // fading in then out over TITLE_SPARKLE_PERIOD_SEC and respawning elsewhere on the next cycle. Positions
        // come from hashing (mote index + cycle bucket) -- deterministic, no per-frame java.util.Random. Drawn on
        // top of the glyphs. Suppressed entirely under Reduce Motion or when sparkleCount <= 0.
        if (!reduceMotion && sparkleCount > 0) {
            int boxX0 = startX;
            int boxW = Math.max(1, drawW);
            int boxY0 = ry;
            int boxH = Math.max(1, drawH);
            for (int k = 0; k < sparkleCount; k++) {
                double staggered = nowSec / TITLE_SPARKLE_PERIOD_SEC + (double) k / sparkleCount;
                long bucket = (long) Math.floor(staggered);
                double frac = staggered - bucket; // 0..1 within this mote's current cycle
                float a = (float) Math.sin(frac * Math.PI);
                int alpha = Math.round(a * a * 235f); // fade in -> twinkle -> fade out
                if (alpha <= 8) continue;
                int hx = hash32((int) (k * 73856093 ^ bucket * 19349663));
                int hy = hash32((int) (k * 83492791 ^ bucket * 2971215073L));
                int sxp = boxX0 + Math.floorMod(hx, boxW);
                int syp = boxY0 + Math.floorMod(hy, boxH);
                // Alternate gold / warm-white cores by hash bit; size 1-2px.
                int core = ((hx & 1) == 0) ? (0xFFF2C24E) : 0xFFFFF4DC;
                int spark = (Math.min(255, alpha) << 24) | (core & 0xFFFFFF);
                boolean big = (frac > 0.35 && frac < 0.65); // briefly 2px at peak twinkle
                context.fill(sxp, syp, sxp + 1, syp + 1, spark);          // center
                context.fill(sxp - 1, syp, sxp, syp + 1, spark);          // + arms
                context.fill(sxp + 1, syp, sxp + 2, syp + 1, spark);
                context.fill(sxp, syp - 1, sxp + 1, syp, spark);
                context.fill(sxp, syp + 1, sxp + 1, syp + 2, spark);
                if (big) {
                    int dim = (Math.min(255, alpha) / 2 << 24) | (core & 0xFFFFFF);
                    context.fill(sxp - 2, syp, sxp - 1, syp + 1, dim);
                    context.fill(sxp + 2, syp, sxp + 3, syp + 1, dim);
                    context.fill(sxp, syp - 2, sxp + 1, syp - 1, dim);
                    context.fill(sxp, syp + 2, sxp + 1, syp + 3, dim);
                }
            }
        }
        // Flanking rules with a small diamond at each inner end, vertically centered on the wordmark.
        int midY = ry + drawH / 2;
        int gap = 8;
        int lineLen = (rw - drawW) / 2 - gap * 2;
        if (lineLen >= 10) {
            int lw = 0x66000000 | (GOLD & 0xFFFFFF);
            context.fill(startX - gap - lineLen, midY, startX - gap, midY + 1, lw);
            context.fill(startX + drawW + gap, midY, startX + drawW + gap + lineLen, midY + 1, lw);
            context.fill(startX - gap - 2, midY - 1, startX - gap + 1, midY + 2, GOLD);
            context.fill(startX + drawW + gap - 1, midY - 1, startX + drawW + gap + 2, midY + 2, GOLD);
        }
        return drawH;
    }

    /** Multiplies an ARGB color's RGB channels by {@code mult} (clamped to 255), preserving alpha. Used for the
     *  wordmark's gentle gold "breath" lift. */
    private static int liftBrightness(int argb, float mult) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * mult));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * mult));
        int b = Math.min(255, Math.round((argb & 0xFF) * mult));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Small fast integer hash (Murmur-ish finalizer) for deterministic sparkle placement -- no per-frame RNG. */
    private static int hash32(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }
}
