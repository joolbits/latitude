package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class EwSandstormOverlayHud {
    private EwSandstormOverlayHud() {}

    // Start fading in at 500 blocks from border; reach “full” at 100 blocks.
    private static final double FADE_START = 500.0;
    private static final double FADE_FULL  = 100.0;

    // Dust tint (tan)
    private static final int DUST_R = 214;
    private static final int DUST_G = 186;
    private static final int DUST_B = 132;

    // Debug-only tick log, off by default (was unconditional -- fired every 40 ticks for every player
    // near the EW border, spamming normal players' logs). Matches the house pattern used by
    // GlobeModClient's -Dlatitude.debugPolarSnow.
    private static final boolean DEBUG_EW_HAZE = Boolean.getBoolean("latitude.debugEwHaze");

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // B-4 round 3 item 6: NO isHidden self-guard -- this haze is WORLD ATMOSPHERE and STAYS visible under
        // F1 (the mixin drives it from the F1 path too). Only the HUD chrome hides under F1.

        double distToBorder = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());

        // B-5-P2 composition (A5): when the Hemisphere Passage is ON, the wall-aware DEPTH fog
        // (FogRendererPassageSetupMixin) owns the edge haze inside the fog band -- it starts at the SAME 500-block
        // FADE_START and is depth-correct (crisp shelter walls, heavy exterior). This flat, depth-blind screen
        // fill would double-haze on top of it (and haze interior walls the depth fog correctly leaves clear), so
        // suppress it entirely inside the band. Flag OFF: byte-identical -- the guard is skipped and the flat
        // haze renders exactly as today. (Only suppressed where the depth fog is active, i.e. dist < FADE_START;
        // outside the band both do nothing.)
        if (com.example.globe.core.LatitudeV2Flags.PASSAGE_V2_ENABLED
                && GlobeClientState.isGlobeWorld()
                && distToBorder < FADE_START) {
            return;
        }

        // Far from border => no overlay
        if (distToBorder >= FADE_START) return;

        // dist=500 => 0, dist=100 => 1
        double t = (FADE_START - distToBorder) / (FADE_START - FADE_FULL);
        float a = (float) Mth.clamp(t, 0.0, 1.0);

        a = a * a; // ramps up faster as you approach the border

        if (DEBUG_EW_HAZE && (net.minecraft.client.Minecraft.getInstance().level.getGameTime() % 40L) == 0L) {
            com.example.globe.GlobeMod.LOGGER.info("[Latitude] EW haze tick: x={} a={}", mc.player.getX(), a);
        }

        // Climate-aware haze (TEST 1 E1): warm bands get a tan dust haze; the cold bands (subpolar/polar) get
        // a cool grey fog that lerps toward a near-white whiteout as you close on the border.
        var border = mc.level.getWorldBorder();
        double absDeg = Math.abs(com.example.globe.util.LatitudeMath.degreesFromZ(border, mc.player.getZ()));
        com.example.globe.util.LatitudeBands.Band band =
                com.example.globe.util.LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        boolean cold = band == com.example.globe.util.LatitudeBands.Band.SUBPOLAR
                || band == com.example.globe.util.LatitudeBands.Band.POLAR;

        int r, g, b;
        float maxAlpha;
        if (cold) {
            // cool grey -> near-white whiteout as `a` ramps toward the border
            r = 200 + (int) ((238 - 200) * a);
            g = 208 + (int) ((242 - 208) * a);
            b = 218 + (int) ((248 - 218) * a);
            maxAlpha = 0.96f;
        } else {
            r = DUST_R;
            g = DUST_G;
            b = DUST_B;
            maxAlpha = 0.90f;
        }

        float baseAlpha = Math.min(maxAlpha, 0.10f + a * (maxAlpha - 0.10f));
        int alpha = (int) (baseAlpha * 255.0f);
        if (alpha <= 0) return;

        int argb = (alpha << 24) | (r << 16) | (g << 8) | (b);

        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        ctx.fill(0, 0, w, h, argb);
    }
}
