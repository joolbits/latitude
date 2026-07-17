package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.util.LatitudeMath;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEST 77 round 2 item 1: DEPTH-BASED polar fog -- the genuine, wall-aware "heavy fog on the outside while
 * you look out from indoors" Peetsa asked for. Unlike {@code PolarWhiteoutOverlayHud} (a flat screen-space
 * fill with no depth, which is why it must stay sky-exposure-gated -- ungated it would haze the player's own
 * interior walls), this drives Minecraft's OWN fog. In 26.2 {@link FogRenderer#setupFog} builds a mutable
 * {@link FogData} (fields {@code renderDistanceStart/End}, {@code environmentalStart/End}, {@code color}, ...)
 * that {@code updateBuffer} later packs into the fog UBO; the core fog shader
 * ({@code assets/minecraft/shaders/include/fog.glsl}) applies {@code renderDistance*} as a LINEAR fog of the
 * CYLINDRICAL per-fragment distance {@code max(|xz|,|y|)}. So tightening those two distances is genuine,
 * depth-correct distance fog: geometry nearer than START takes zero fog (a shelter wall two blocks away stays
 * crisp) while distant terrain past END fades to fog colour (the exterior seen through a doorway reads heavy).
 * It is correct exposed OR sheltered-looking-out, so -- unlike the overlay -- it needs no {@code surfaceOk}
 * gate. The whiteout overlay is retuned to a light "engulfed" top-coat that still only paints when exposed.
 *
 * <p><b>Composition / discipline (S10b FOG LAW v2).</b> The latitude->visibility LAW is the single
 * {@code core.PolarFogLaw} cap table (80 -> 90, absolute blocks -- see its class doc for why absolute replaced
 * the old eased-from-vanilla form); this mixin only implements it on the depth-fog side. Only ever TIGHTENS
 * vanilla's fog (min-guarded) and is SEAM-FREE at/below the 80-deg onset (the first table row sits above every
 * realistic view distance). Atmospheric only -- if the camera is in water/lava/powder-snow vanilla owns the fog
 * and we do nothing. Globe worlds only; honours the existing debug fog kill switches. No {@code require = 0}:
 * if {@code setupFog} ever renames this fails loudly at load rather than silently no-opping (the failure mode
 * of the dead {@code applyFog}-targeted fog mixins).
 */
@Mixin(FogRenderer.class)
public class FogRendererPolarSetupMixin {

    // Same storm->white palette as PolarWhiteoutOverlayHud so the depth fog and the overlay read as one storm.
    private static final int STORM_R = 92;
    private static final int STORM_G = 108;
    private static final int STORM_B = 132;
    private static final int WHITE_R = 238;
    private static final int WHITE_G = 242;
    private static final int WHITE_B = 248;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void globe$polarDepthFog(Camera camera, int viewDistance, DeltaTracker deltaTracker, float tickDelta,
                                     ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (GlobeClientState.DEBUG_DISABLE_FOG || GlobeClientState.DEBUG_DISABLE_WARNINGS) {
            return;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        // Atmospheric only: let vanilla own underwater / lava / powder-snow fog (and don't white-tint them).
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != level) {
            return;
        }

        // S10b FOG LAW v2 (owner, TEST 99: "fog is very lacking... at 90 you can't see really anything").
        // The latitude->visibility law is the ABSOLUTE cap table in core.PolarFogLaw (light haze 80, heavy 40
        // blocks by 88, NEAR-TOTAL whiteout -- ~4 blocks -- at the pole line; the fog IS the wall now, replacing
        // the retired striped plane). ABSOLUTE, not eased-from-vanilla like the superseded
        // PolarHazardWindow.polarFogEnd: the old form scaled with the video render distance, so long-view
        // players saw a fraction of the intended fog at every latitude (the TEST 99 "lacking" root).
        double absLatDeg = LatitudeMath.absLatDegExact(level.getWorldBorder(), mc.player.getZ());
        float endCap = com.example.globe.core.PolarFogLaw.fogEndCapBlocks(absLatDeg);
        if (endCap == Float.MAX_VALUE) {
            return; // below the 80-deg onset -- no polar fog, vanilla untouched (seam-free)
        }

        FogData data = cir.getReturnValue();
        if (data == null || data.color == null) {
            return; // defensive: sweeper note -- this mixin has no live launch yet to prove color is ever set
        }

        // Cap the cylindrical render-distance fog (only ever tighter than vanilla; min() = seam-free onset).
        float newEnd = Math.min(data.renderDistanceEnd, endCap);
        float newStart = Math.min(data.renderDistanceStart,
                Math.min(com.example.globe.core.PolarFogLaw.fogStartBlocks(newEnd), newEnd - 1.0f));
        data.renderDistanceEnd = newEnd;
        data.renderDistanceStart = newStart;

        // Tint the fog COLOUR toward the storm->white whiteout palette so the far terrain fades to a white/grey
        // blizzard haze rather than the biome's cold-blue fog. rgb only; leave alpha to vanilla. Grey-blue storm
        // at the low end, near-white at the pole (linear01 palette), blended in on the front-loaded curve.
        float blend = com.example.globe.core.PolarFogLaw.colorBlend01(absLatDeg);
        float ambient = com.example.globe.core.PolarFogLaw.linear01(absLatDeg);
        float tr = lerp255(STORM_R, WHITE_R, ambient);
        float tg = lerp255(STORM_G, WHITE_G, ambient);
        float tb = lerp255(STORM_B, WHITE_B, ambient);
        Vector4f c = data.color;
        c.x += (tr - c.x) * blend;
        c.y += (tg - c.y) * blend;
        c.z += (tb - c.z) * blend;
    }

    private static float lerp255(int from, int to, float t) {
        return (from + (to - from) * t) / 255.0f;
    }
}
