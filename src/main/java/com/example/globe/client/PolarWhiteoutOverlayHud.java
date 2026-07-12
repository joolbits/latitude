package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Phase 5 B-3b: the VISIBLE polar whiteout -- a full-screen fill whose alpha ramps CONTINUOUSLY over
 * the ambient window [85,90] deg ({@code PolarHazardWindow.fogIntensity}, read through the existing
 * {@code GlobeClientState.computePoleWhiteoutFactor} screen-fog path). Mirrors the
 * {@link EwSandstormOverlayHud} precedent: a HUD-layer fill, always-on for globe worlds (atmosphere,
 * not config-gated), rendered from {@code InGameHudMixin} before the hotbar.
 *
 * <p><b>TEST 77 round 2 -- demoted to an "engulfed" top-coat.</b> The genuine, wall-aware "heavy exterior fog
 * seen through a doorway" is now carried by real depth fog ({@code FogRendererPolarSetupMixin} tightens
 * Minecraft's own render-distance fog), which is correct exposed OR sheltered-looking-out. This flat screen
 * fill can't tell a near wall from far terrain, so it KEEPS its sky-exposure gate (see below) and is reduced
 * to a light veil: its only remaining job is the sensation of being ENGULFED -- snow blowing into your face --
 * when you actually stand out in the open deep whiteout. So {@link #MAX_ALPHA} dropped 0.90 -> 0.35 and its
 * curve steepened (was intensity^0.65, now intensity^1.7) so mid-latitudes are carried purely by the depth fog
 * (no flat wash) and this only lifts near the pole. The two compose without double-hazing: depth fog does the
 * distance-correct heavy haze everywhere; this adds a soft close-in white ONLY when exposed and deep in.
 */
public final class PolarWhiteoutOverlayHud {
    private PolarWhiteoutOverlayHud() {
    }

    // B-4 stormy cast: the fill lerps from a cold grey-blue STORM tint at low/mid intensity toward the
    // near-white WHITEOUT endpoint at the pole. Because the fill covers the whole screen this also dims
    // the sunny/blue sky Peetsa complained about -- the approach reads as a gathering storm, not clear skies.
    private static final int STORM_R = 92;
    private static final int STORM_G = 108;
    private static final int STORM_B = 132;
    // Near-white cool tint -- the same endpoint color the EW cold-band whiteout lerps toward.
    private static final int WHITE_R = 238;
    private static final int WHITE_G = 242;
    private static final int WHITE_B = 248;
    // TEST 77 round 2: a LIGHT engulfment top-coat over the depth fog (was 0.90, the sole heavy haze). The
    // real heavy fog is now the depth-based FogRendererPolarSetupMixin; this only adds a soft close-in white
    // when standing exposed deep in the whiteout, so it must not stack toward opacity.
    private static final float MAX_ALPHA = 0.35f;

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // B-4 round 3 item 6: NO isHidden self-guard here. This is WORLD ATMOSPHERE (a storm/whiteout fill),
        // not a HUD element, so it STAYS visible under F1. The mixin renders it from the F1 path too (vanilla
        // skips extractHotbarAndDecorations when the HUD is hidden); the visible HUD chrome is what F1 hides.

        // Interior-storm split (Peetsa's "storm vanishes indoors" bug): this whiteout is a SCREEN-SPACE HUD
        // fill with no depth -- walls cannot occlude it, so painting it at full while a player is sealed in a
        // room would haze the interior itself rather than only the view out a window. It represents being
        // ENGULFED in the whiteout, which happens out in the open. TEST 78: instead of a HARD sky-exposure
        // gate (which dropped the whiteout entirely under Peetsa's open arch), its alpha now SCALES by the
        // graded enclosure estimate exposure01 -- ~full under the open arch, ~0 in a sealed room, partial at a
        // doorway. The wall-aware far haze is still carried separately by the depth-based render-distance fog
        // (FogRendererPolarSetupMixin), which correctly hazes only the far exterior past an opening.
        var eval = GlobeClientState.evaluate(mc);
        if (!eval.active()) {
            return;
        }
        float exposure = eval.exposure01();
        if (exposure <= 0.001f) {
            return;
        }

        // Continuous 85->90 ramp: 0 at/below 85 deg, 1.0 at the pole (both hemispheres via |lat|).
        float intensity = GlobeClientState.computePoleWhiteoutFactor(mc.player.getZ());
        if (intensity <= 0.001f) {
            return;
        }

        // TEST 77 round 2 curve: STEEPENED to intensity^1.7 (was the sub-linear 0.65). Now that the depth fog
        // carries the low/mid-latitude haze correctly, this veil should stay quiet through 85-88 deg (no flat
        // wash competing with the depth fog) and only lift near the pole as the "engulfed" close-in white.
        // Starts at 0 at 85 deg (no seam); slope INCREASES toward the pole. TEST 78: multiplied by the
        // exposure scale so the engulfment fades with enclosure instead of snapping off a single overhead block.
        float eased = (float) Math.pow(intensity, 1.7)
                * com.example.globe.core.PolarExposure.whiteoutScale(exposure);
        int alpha = (int) (Math.min(MAX_ALPHA, eased * MAX_ALPHA) * 255.0f);
        if (alpha <= 0) {
            return;
        }

        // Colour follows the raw 85->90 ramp: grey-blue storm low, white-out high.
        float t = Math.min(1.0f, Math.max(0.0f, intensity));
        int r = lerp(STORM_R, WHITE_R, t);
        int g = lerp(STORM_G, WHITE_G, t);
        int b = lerp(STORM_B, WHITE_B, t);

        int argb = (alpha << 24) | (r << 16) | (g << 8) | b;
        ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), argb);
    }

    private static int lerp(int from, int to, float t) {
        return from + Math.round((to - from) * t);
    }
}
