package com.example.globe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Phase 5 B-3b: the VISIBLE polar whiteout -- the exposure-gated "engulfed" TOP-COAT of the S10b fog law. A
 * full-screen fill whose strength is {@code core.PolarFogLaw.whiteoutTopcoat01} (quiet through the mid-band,
 * lifting from ~86, FULL in the last half-degree -- composited over the depth fog's ~4-block visibility it
 * completes TEST 99's "at 90 you can't see really anything in front of you"). Mirrors the
 * {@link EwSandstormOverlayHud} precedent: a HUD-layer fill, always-on for globe worlds (atmosphere,
 * not config-gated), rendered from {@code InGameHudMixin} before the hotbar.
 *
 * <p><b>TEST 77 round 2 -- demoted to an "engulfed" top-coat; S10b -- re-lawed.</b> The genuine, wall-aware
 * "heavy exterior fog seen through a doorway" is carried by real depth fog ({@code FogRendererPolarSetupMixin},
 * the other implementer of the SAME {@code PolarFogLaw}), which is correct exposed OR sheltered-looking-out.
 * This flat screen fill can't tell a near wall from far terrain, so it KEEPS its sky-exposure gate (see below)
 * and stays a close-in veil: the sensation of being ENGULFED -- snow blowing into your face -- when you
 * actually stand out in the open deep whiteout. Its latitude envelope moved from the local
 * {@code intensity^1.7} curve into {@code PolarFogLaw.WHITEOUT_TOPCOAT_CURVE} (one law, two renderers); the
 * two compose without double-hazing exactly as before.
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
    // real heavy fog is the depth-based FogRendererPolarSetupMixin; this only adds a soft close-in white
    // when standing exposed deep in the whiteout, so it must not stack toward opacity.
    // S10b (TEST 99): 0.35 -> 0.45. The last half-degree is now a NEAR-TOTAL whiteout and this veil is what
    // whitens the few blocks the depth fog leaves clear. Still far under the interior-hazing 0.90, and still
    // exposure-scaled, so the interior-storm split holds.
    private static final float MAX_ALPHA = 0.45f;

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

        // S10b: the latitude envelope is the LAW's top-coat curve (0 at/below 86, 1.0 at the pole line --
        // both hemispheres via |lat|), replacing the local intensity^1.7. Quiet through the mid-band (the
        // depth fog carries the distance haze), lifting from 88, full engulfment in the last half-degree.
        double absLatDeg = com.example.globe.util.LatitudeMath.absLatDegExact(
                mc.level.getWorldBorder(), mc.player.getZ());
        float topcoat = com.example.globe.core.PolarFogLaw.whiteoutTopcoat01(absLatDeg);
        if (topcoat <= 0.001f) {
            return;
        }

        // TEST 78: multiplied by the exposure scale so the engulfment fades with enclosure instead of
        // snapping off a single overhead block (the interior-storm split -- unchanged by S10b).
        float eased = topcoat * com.example.globe.core.PolarExposure.whiteoutScale(exposure);
        int alpha = (int) (Math.min(MAX_ALPHA, eased * MAX_ALPHA) * 255.0f);
        if (alpha <= 0) {
            return;
        }

        // Colour follows the plain 80->90 fog-law ramp: grey-blue storm low, white-out high (same palette
        // the depth fog tints toward, so the veil and the fog read as ONE storm).
        float t = com.example.globe.core.PolarFogLaw.linear01(absLatDeg);
        int rgb = (lerp(STORM_R, WHITE_R, t) << 16) | (lerp(STORM_G, WHITE_G, t) << 8) | lerp(STORM_B, WHITE_B, t);

        // S14(c): the SAME night/dusk darkness modulation the depth fog gets (GlobeClientState.polarSkyTint ->
        // SolarSkyMood.atmosphereTint). The owner's "the topcoat composites pure white at night" fix: the white
        // scales DOWN toward the gloomed near-black at night / polar night (a DARK-out, not a bright white wall)
        // and toward the held dusk under the midnight sun -- one source of truth with the sky dome and the fog.
        rgb = GlobeClientState.polarSkyTint(rgb, mc.level, mc.player.getZ());

        int argb = (alpha << 24) | (rgb & 0xFFFFFF);
        ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), argb);
    }

    private static int lerp(int from, int to, float t) {
        return from + Math.round((to - from) * t);
    }
}
