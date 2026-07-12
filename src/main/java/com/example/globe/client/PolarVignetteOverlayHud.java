package com.example.globe.client;

import com.example.globe.core.PolarWarningEpisode;
import com.example.globe.core.ui.PolarWarningVignette;
import com.example.globe.util.LatitudeMath;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Phase 5 (understudy SWING): the VISIBLE polar-warning VIGNETTE -- a subtle dark edge-darkening pulse that
 * fires in time with the DANGER / LETHAL episodic warning text ({@code GlobeWarningOverlay}), so the words and
 * the world darken as ONE moment. This is punctuation, NOT a new atmosphere layer: only the screen EDGES
 * darken; the center ~60% stays fully transparent (the {@link PolarWhiteoutOverlayHud} already owns full-screen
 * atmosphere). It draws UNDER the warning text but OVER the whiteout fill (see {@code InGameHudMixin} ordering:
 * whiteout in extractHotbarAndDecorations, then this + text in the extractRenderState TAIL).
 *
 * <p>The envelope (rise/settle/hold/melt alphas over elapsed wall-clock ms, per tier, plus the lethal linger
 * and Reduce Motion static level) is the pure {@link PolarWarningVignette}. This class only reads the armed
 * episode state from {@link GlobeWarningOverlay}, computes elapsed wall-clock ms + whether the lethal state
 * still persists, and paints the edge gradient. It is a provable no-op whenever no DANGER/LETHAL episode is
 * armed ({@code tier == 0} -> return) or the resolved edge alpha rounds to zero.
 */
public final class PolarVignetteOverlayHud {

    private PolarVignetteOverlayHud() {
    }

    // A cold near-black, matching the storm palette's cool cast rather than a flat pure black.
    private static final int TINT_R = 8;
    private static final int TINT_G = 10;
    private static final int TINT_B = 16;

    // The clear center: each side's darkening occupies this fraction of the screen, leaving the middle
    // ~60% fully transparent (0.20 margin per side on both axes).
    private static final float EDGE_MARGIN_FRAC = 0.20f;
    // Bands per side of the code-drawn gradient (adjacent, non-overlapping thin rects ramping edge->inner).
    private static final int BANDS = 16;

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // Punctuation for the warning TEXT: gated with the text it accompanies. If warnings are off there is
        // no message to punctuate, so there is no vignette.
        if (!LatitudeConfig.showWarningMessages) {
            return;
        }

        int tier = GlobeWarningOverlay.poleVignetteTier();
        if (tier != PolarWarningVignette.TIER_DANGER && tier != PolarWarningVignette.TIER_LETHAL) {
            return; // provable no-op: no serious episode armed.
        }

        // Same activation + surface gate as the warning text / whiteout: a globe world, sky-exposed.
        var eval = GlobeClientState.evaluate(mc);
        if (!eval.active() || !eval.surfaceOk()) {
            return;
        }

        long startMs = GlobeWarningOverlay.poleVignetteStartMs();
        long elapsedMs = startMs == Long.MIN_VALUE ? -1L : System.currentTimeMillis() - startMs;

        // Lethal persists while the player is still at/poleward of the lethal latitude.
        boolean lethalPersists = false;
        if (tier == PolarWarningVignette.TIER_LETHAL) {
            var border = mc.level.getWorldBorder();
            double absLatDeg = LatitudeMath.absLatDegExact(border, mc.player.getZ());
            lethalPersists = absLatDeg >= PolarWarningEpisode.TIER_4_DEG;
        }

        float edge = PolarWarningVignette.edgeAlpha(tier, elapsedMs, lethalPersists, LatitudeConfig.reduceMotion);
        if (edge <= 0.001f) {
            return;
        }

        drawVignette(ctx, ctx.guiWidth(), ctx.guiHeight(), edge);
    }

    /**
     * Paints the edge-darkening gradient with a handful of adjacent thin rects per side (top/bottom/left/right),
     * each ramping from {@code edgeAlpha} at the screen edge to 0 at the inner margin. The center stays
     * untouched (transparent). Perpendicular strips overlap only in the four corners, so the corners read a
     * touch darker -- exactly the vignette falloff. Cost is {@code 4 * BANDS} solid fills (~64), all skipped
     * when their rounded alpha is 0.
     */
    private static void drawVignette(GuiGraphicsExtractor ctx, int w, int h, float edgeAlpha) {
        int marginX = Math.max(1, Math.round(w * EDGE_MARGIN_FRAC));
        int marginY = Math.max(1, Math.round(h * EDGE_MARGIN_FRAC));
        int bandW = Math.max(1, marginX / BANDS);
        int bandH = Math.max(1, marginY / BANDS);

        for (int i = 0; i < BANDS; i++) {
            // frac = 1 at the outer edge (i=0), 0 at the inner margin; squared for a soft inner falloff.
            float frac = (float) (BANDS - 1 - i) / (float) (BANDS - 1);
            float a = edgeAlpha * frac * frac;
            int alpha = Math.round(a * 255.0f);
            if (alpha <= 0) {
                continue;
            }
            int argb = (alpha << 24) | (TINT_R << 16) | (TINT_G << 8) | TINT_B;

            int topY = i * bandH;
            int botY = h - (i + 1) * bandH;
            // Top + bottom strips span the full width.
            ctx.fill(0, topY, w, topY + bandH, argb);
            ctx.fill(0, botY, w, botY + bandH, argb);
            int leftX = i * bandW;
            int rightX = w - (i + 1) * bandW;
            // Left + right strips span the full height (corners overlap top/bottom => darker, as intended).
            ctx.fill(leftX, 0, leftX + bandW, h, argb);
            ctx.fill(rightX, 0, rightX + bandW, h, argb);
        }
    }
}
