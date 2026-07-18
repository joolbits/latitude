package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.client.PolarColdClient;
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
        // S15(a) FOG LAW SPLIT (owner, TEST 105: "looking OUT a shelter window the depth fog vanishes"). The
        // depth fog is WEATHER -- it applies UNLESS the player is genuinely SEALED -- so its gate is now the
        // S4-style raw-sky-light SEALED predicate (PolarColdClient.isSheltered: raw SKY light at the eye <= 3,
        // ColdShelter), NOT the graded enclosure estimate exposure01. Why the change: exposure01 is 13 canSeeSky
        // samples around the head, so a WINDOW shelter (a roof overhead, all 13 samples blocked) read exposure
        // ~0 and the depth fog EARLY-OUT wrongly released it to near-clear vanilla -- the bug. Raw sky light is
        // graded and side-lit: a window/doorway shelter floods the eye to > 3 (NOT sealed -> the full exterior
        // fog cap applies, "heavy blizzard seen out the window"), while a deep sealed cave / snow burrow reads
        // 0-2 (sealed -> vanilla cave fog returns, preserving the TEST 101 "no bright fog in caves" fix). The
        // screen-space whiteout TOPCOAT keeps its OWN graded exposure01 scaling (frost-on-eyes fades with real
        // enclosure -- that law was right where it was, and is deliberately NOT touched here).
        if (PolarColdClient.isSheltered(mc)) {
            return; // genuinely sealed (raw sky light <= 3): vanilla owns the fog.
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

        Vector4f c = data.color;

        // (1) STORM HAZE (the DAY blizzard look). Pull the fog COLOUR toward the storm->white whiteout palette
        // so the far terrain fades to a white/grey blizzard haze rather than the biome's cold-blue fog. rgb only;
        // leave alpha to vanilla. Grey-blue storm at the low end, near-white at the pole (linear01 palette),
        // front-loaded (colorBlend01). NO night tint baked in here -- the gloom is applied at full strength in (2).
        float blend = com.example.globe.core.PolarFogLaw.colorBlend01(absLatDeg);
        float ambient = com.example.globe.core.PolarFogLaw.linear01(absLatDeg);
        float hr = lerp255i(STORM_R, WHITE_R, ambient) / 255.0f;
        float hg = lerp255i(STORM_G, WHITE_G, ambient) / 255.0f;
        float hb = lerp255i(STORM_B, WHITE_B, ambient) / 255.0f;
        c.x += (hr - c.x) * blend;
        c.y += (hg - c.y) * blend;
        c.z += (hb - c.z) * blend;

        // (2) S15(c) HORIZON GLOOM -- make the horizon fog MATCH the gloomed sky. The fog colour FOLLOWS the
        // FINAL sky (GlobeClientState.polarSkyTint -> SolarSkyMood.atmosphereTint, the SAME palette + curves the
        // sky DOME uses): night/polar-night -> gloomed near-black, midnight-sun night -> held pink-gold dusk,
        // day/sun-up -> ~identity (the (1) haze shows through). Owner, TEST 105: the horizon band stayed LIGHT
        // under a DARK polar-night sky because this tint used to ride the SLOW colorBlend01 curve (~0.84 even at
        // 88 deg), under-darkening the fog vs the fully-gloomed dome. Now it is applied at FULL strength, eased in
        // only by horizonGloomReach01 (0 at the 80-deg onset -> 1 by 85, so the fog colour is seam-free at onset
        // and fully matches the sky by 85 through the pole). Un-storm-damped night darkening (via atmosphereTint)
        // keeps a night blizzard a DARK-out, not a white wall.
        float reach = com.example.globe.core.PolarFogLaw.horizonGloomReach01(absLatDeg);
        if (reach > 0.0f) {
            int packed = (to255(c.x) << 16) | (to255(c.y) << 8) | to255(c.z);
            int gloomed = GlobeClientState.polarSkyTint(packed, level, mc.player.getZ());
            float gr = ((gloomed >> 16) & 0xFF) / 255.0f;
            float gg = ((gloomed >> 8) & 0xFF) / 255.0f;
            float gb = (gloomed & 0xFF) / 255.0f;
            c.x += (gr - c.x) * reach;
            c.y += (gg - c.y) * reach;
            c.z += (gb - c.z) * reach;
        }
    }

    private static int lerp255i(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    /** Clamp a 0..1 colour component to a packed 0-255 byte (defensive against float drift before packing). */
    private static int to255(float v) {
        int i = Math.round(v * 255.0f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
