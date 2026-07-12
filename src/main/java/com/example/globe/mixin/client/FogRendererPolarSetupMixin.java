package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.core.PolarHazardWindow;
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
 * <p><b>Composition / discipline.</b> Envelope = the SAME ambient window {@code [85,90]} as the snow + overlay
 * ({@link PolarHazardWindow#ambientProgress}); the tightening + fog-colour tint are pure, tested curves in
 * {@code PolarHazardWindow}. Only ever TIGHTENS vanilla's fog (min-guarded; NEAR anchors sit far closer than
 * any view distance) and is SEAM-FREE at/below 85 deg. Atmospheric only -- if the camera is in water/lava/
 * powder-snow vanilla owns the fog and we do nothing. Globe worlds only; honours the existing debug fog kill
 * switches. No {@code require = 0}: if {@code setupFog} ever renames this fails loudly at load rather than
 * silently no-opping (the failure mode of the dead {@code applyFog}-targeted fog mixins).
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

        double absLatDeg = LatitudeMath.absLatDegExact(level.getWorldBorder(), mc.player.getZ());
        float endFraction = PolarHazardWindow.polarFogEndFraction(absLatDeg);
        if (endFraction <= 0.001f) {
            return; // below ~85 deg -- no polar fog, vanilla untouched
        }

        FogData data = cir.getReturnValue();
        if (data == null || data.color == null) {
            return; // defensive: sweeper note -- this mixin has no live launch yet to prove color is ever set
        }

        // Pull the cylindrical render-distance fog IN toward the pole values (only ever tighter than vanilla).
        float newEnd = PolarHazardWindow.polarFogEnd(data.renderDistanceEnd, absLatDeg);
        float newStart = PolarHazardWindow.polarFogStart(data.renderDistanceStart, newEnd, absLatDeg);
        data.renderDistanceEnd = Math.min(data.renderDistanceEnd, newEnd);
        data.renderDistanceStart = Math.min(data.renderDistanceStart, newStart);

        // Tint the fog COLOUR toward the storm->white whiteout palette so the far terrain fades to a white/grey
        // blizzard haze rather than the biome's cold-blue fog. rgb only; leave alpha to vanilla. Grey-blue storm
        // at the low end, near-white at the pole (raw ambient), blended in on the end curve.
        float ambient = (float) PolarHazardWindow.ambientProgress(absLatDeg);
        float tr = lerp255(STORM_R, WHITE_R, ambient);
        float tg = lerp255(STORM_G, WHITE_G, ambient);
        float tb = lerp255(STORM_B, WHITE_B, ambient);
        Vector4f c = data.color;
        c.x += (tr - c.x) * endFraction;
        c.y += (tg - c.y) * endFraction;
        c.z += (tb - c.z) * endFraction;
    }

    private static float lerp255(int from, int to, float t) {
        return (from + (to - from) * t) / 255.0f;
    }
}
