package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.core.HemispherePassage;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.util.LatitudeBands;
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
 * Phase 5 Slice B-5-P2 (Hemisphere Passage) -- the APPROACH FOG (design amendment A2). Peetsa vetoed a flat
 * screen tint this session, so the E/W-edge approach drives Minecraft's OWN render-distance fog exactly like
 * the pole's {@link FogRendererPolarSetupMixin}: depth-correct and wall-aware. Geometry nearer than
 * {@code renderDistanceStart} takes zero fog (a shelter wall two blocks away stays crisp) while distant terrain
 * past {@code renderDistanceEnd} fades to fog colour (the exterior seen out a doorway reads as a heavy weather
 * front). It is correct exposed OR sheltered-looking-out, so -- like the polar depth fog and unlike the flat
 * overlays -- it needs no exposure gate (A3: EXPOSURE-INDEPENDENT; it never touches PolarExposure/surfaceOk).
 *
 * <p><b>Driven by border distance, not latitude.</b> The tightening ramps on the SAME distance-to-the-E/W-edge
 * the crossing prompt arms on ({@link GlobeClientState#distanceToEwBorderBlocks(double)}), anchored to the
 * mod's INTENDED X radius (immune to a lerping border). The band is degree-anchored per world by
 * {@link com.example.globe.core.EdgeGeometry}: no effect at/beyond {@code rampStartDist} (~177.5 deg longitude,
 * seam-free -- vanilla fog untouched), thickening on the pure ease-in curves in {@link HemispherePassage}
 * (a weather front rolling in) to near-opaque by the fog climax ({@code fogClimaxDist}, ~179 deg). Edge-flow
 * rework note: the fog climax (179 deg) is now one degree poleward of the 178-deg crossing prompt line -- the
 * fog band (onset 177.5, full 179) is unchanged, so a crossing offered at 178 still fires while the fog is
 * mid-build, and the arrival at 178 emerges in thinning fog rather than a full whiteout.
 *
 * <p><b>Composition with the polar depth fog (A2 note).</b> A corner at high |lat| near the X edge gets BOTH
 * this and {@link FogRendererPolarSetupMixin} (both @Inject at {@code setupFog} RETURN). Both only ever TIGHTEN
 * (min-guarded), so {@code Math.min} composes them naturally -- whichever storm is heavier wins, and the result
 * is never looser than vanilla. Colour: each tints toward its own storm palette by its own fraction; in the
 * rare shared corner the later injector tints on top, but both palettes are storm-coherent (the cold-band edge
 * haze and the polar whiteout share the same grey->white endpoint) so there is no jarring clash.
 *
 * <p><b>Palette (A2).</b> Tints toward the EXISTING EW sandstorm haze palette ({@code EwSandstormOverlayHud}) so
 * the edge stays one coherent weather story: warm bands get the tan dust haze, the cold bands (subpolar/polar)
 * get the cool grey->near-white whiteout -- the exact same climate split the flat EW haze used before this fog
 * replaced it in the band.
 *
 * <p>Whole thing gated on {@link LatitudeV2Flags#PASSAGE_V2_ENABLED}: flag-off is byte-identical (the first
 * check returns immediately -- vanilla fog untouched, the live EW haze behaves exactly as today). Globe worlds
 * only; honours the debug fog kill switches; atmospheric only (vanilla owns underwater/lava/powder-snow fog).
 * No {@code require = 0}: if {@code setupFog} ever renames this fails loudly at load (the discipline the dead
 * {@code applyFog}-targeted fog mixins lacked), matching the polar mixin exactly.
 */
@Mixin(FogRenderer.class)
public class FogRendererPassageSetupMixin {

    // The EW sandstorm haze palette (EwSandstormOverlayHud): tan dust in the warm bands.
    private static final int DUST_R = 214;
    private static final int DUST_G = 186;
    private static final int DUST_B = 132;
    // The EW cold-band whiteout endpoint (cool grey -> near-white), shared with the polar storm palette.
    private static final int COLD_R = 220;
    private static final int COLD_G = 226;
    private static final int COLD_B = 234;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void globe$passageApproachFog(Camera camera, int viewDistance, DeltaTracker deltaTracker, float tickDelta,
                                          ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!LatitudeV2Flags.PASSAGE_V2_ENABLED) {
            return; // feature off: vanilla fog + live EW haze untouched (byte-identical).
        }
        if (GlobeClientState.DEBUG_DISABLE_FOG || GlobeClientState.DEBUG_DISABLE_WARNINGS) {
            return;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        // Atmospheric only: let vanilla own underwater / lava / powder-snow fog (and don't haze-tint them).
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != level) {
            return;
        }
        // Item 2 (surface-only): underground the approach does not exist -- return vanilla fog so a cave near
        // the edge shows only the plain border wall, no gathering haze. Same "genuinely below the surface with
        // no sky" test the prompt machine and the server cross-reject use. P3-EYEBALL: the transition is a hard
        // on/off at the surface line, so surfacing right at the edge could pop from vanilla fog into the
        // approach haze in one frame; no fade is built (a depth-fog crossfade here is non-trivial).
        if (GlobeClientState.isDeepUnderground(mc)) {
            return;
        }

        double distToEdge = GlobeClientState.distanceToEwBorderBlocks(mc.player.getX());
        // Per-world fog band, degree-anchored to the intended X radius: onset at rampStartDist (~177.5 deg),
        // full at the 179-deg climax (one degree poleward of the prompt) (climax). Immune to a lerping border; shared with the prompt/banner.
        com.example.globe.core.EdgeGeometry.Resolved geo =
                GlobeClientState.edgeGeometry(level.getWorldBorder());
        double rampStart = geo.rampStartDist();
        double climax = geo.fogClimaxDist();
        float endFraction = HemispherePassage.approachFogEndFraction(distToEdge, rampStart, climax);
        if (endFraction <= 0.001f) {
            return; // at/beyond the fog onset -- no approach fog, vanilla untouched (seam-free).
        }

        FogData data = cir.getReturnValue();
        if (data == null || data.color == null) {
            return; // defensive, mirroring the polar mixin's null-guard on data AND data.color.
        }

        // Pull the cylindrical render-distance fog IN toward the edge values (only ever tighter than vanilla,
        // so this composes with the polar depth fog via Math.min -- the heavier storm wins).
        float newEnd = HemispherePassage.approachFogEnd(data.renderDistanceEnd, distToEdge, rampStart, climax);
        float newStart = HemispherePassage.approachFogStart(data.renderDistanceStart, newEnd, distToEdge, rampStart, climax);
        data.renderDistanceEnd = Math.min(data.renderDistanceEnd, newEnd);
        data.renderDistanceStart = Math.min(data.renderDistanceStart, newStart);

        // Tint the fog COLOUR toward the EW haze palette (climate-split like the flat haze it replaced): tan dust
        // in the warm bands, cool grey->white in the cold bands. rgb only; leave alpha to vanilla.
        double absDeg = Math.abs(LatitudeMath.degreesFromZ(level.getWorldBorder(), mc.player.getZ()));
        LatitudeBands.Band band = LatitudeBands.fromAbsoluteLatitudeDeg(absDeg);
        boolean cold = band == LatitudeBands.Band.SUBPOLAR || band == LatitudeBands.Band.POLAR;
        int tr = cold ? COLD_R : DUST_R;
        int tg = cold ? COLD_G : DUST_G;
        int tb = cold ? COLD_B : DUST_B;
        Vector4f c = data.color;
        c.x += (tr / 255.0f - c.x) * endFraction;
        c.y += (tg / 255.0f - c.y) * endFraction;
        c.z += (tb / 255.0f - c.z) * endFraction;
    }
}
