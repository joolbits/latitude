package com.example.globe.mixin.client;

import com.example.globe.client.GlobeClientState;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarHazardWindow;
import com.example.globe.core.SolarPose;
import com.example.globe.core.SolarSkyMood;
import com.example.globe.core.SolarTilt;
import com.example.globe.util.LatitudeMath;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.Level;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Solar Tilt P2 — the visible sky ({@code docs/binder/solar-tilt-design-20260716.md} §5/§6, sweep amendments
 * A3/A4/A6 BINDING). Three surfaces on {@link SkyRenderer}, all first-line gated on
 * {@link LatitudeV2Flags#SOLAR_TILT_V2_ENABLED} (byte-identical flag-off):
 *
 * <ol>
 *   <li><b>The celestial tilt (A6 option B, PRIMARY).</b> {@code @WrapOperation} on the PER-BODY
 *       {@code PoseStack.mulPose} calls inside {@code renderSunMoonAndStars} (bytecode receipt: ordinal 0 =
 *       the shared {@code YP(-90)} yaw — untouched; 1 = sun {@code XP(sunAngle)}; 2 = moon; 3 = stars).
 *       Each body's vanilla rotation argument is replaced with {@link SolarPose#tiltedBodyPose}
 *       ({@code ZP(−φ)·q·ZP(+δ)}; stars tilt-only) — the numerically-solved composition that lands the body
 *       exactly on {@link SolarTilt#solarDirection} and reduces to vanilla BY CONSTRUCTION at φ = 0, δ = 0
 *       (the equator regression guard, pinned headlessly by {@code SolarPoseTest}; the live equator
 *       screenshot-diff stays the P3 gate). Moon keeps its vanilla phase and rides the same tilt (§5 v1);
 *       the star wheel circles a celestial pole at altitude φ — the polar-night payoff.</li>
 *   <li><b>Horizon glow suppression (A3).</b> {@code renderSunriseAndSunset} is cancelled at HEAD poleward of
 *       the VISUAL onset ({@link SolarTilt#onsetLatDeg}: 60° at δ = 30) — inside the midnight-sun /
 *       polar-night bands the vanilla glow would paint dawn-gold at the wrong compass point (and fire at
 *       vanilla dusk during polar night, when no sun crosses any horizon). Equatorward of the onset the glow
 *       is untouched. Rotating the glow with the tilt = future polish, per the sweep.</li>
 *   <li><b>Sky mood (§6, A4).</b> At the tail of {@code extractRenderState}: polar-night gloom (sky colour
 *       toward a deep blue-grey night wash; {@code starBrightness} lifted so stars show in the dark
 *       day-hours) and midnight-sun gold (a low-sun warm wash). Pure curves in {@link SolarSkyMood}; every
 *       blend is damped by {@code (1 − stormLevel)} so the S10 overcast ramp and the 85°+ storm sky ALWAYS
 *       win (A4: never un-grey {@code rainLevel}/{@code rainBrightness} — those fields are not touched).
 *       No light-engine work: block light and torches stay on the global clock (the documented seam).</li>
 * </ol>
 *
 * <p><b>Visual onset vs functional floor:</b> everything here keys on the VISUAL band geometry (onset
 * {@code 90 − |δ|}, 60° at full tilt). The narrower {@code functionalMinDeg} floor (74.5, sweep A2) belongs
 * to the P1 MOB rules only and is deliberately not consulted.
 *
 * <p><b>Honesty line (mixin application).</b> Like the P1 mob mixins and {@code FogRendererPolarSetupMixin},
 * these targets have no non-launching application proof: loom has no mixin-audit-without-launch task, so
 * "the mixin applies and the sky actually tilts" falls to the owner flight / orchestrator live lane. The
 * math half is pinned headlessly ({@code SolarPoseTest}); no {@code require = 0} anywhere, so a renamed
 * target fails LOUDLY at load instead of silently no-opping.
 */
@Mixin(SkyRenderer.class)
public class SkyRendererSolarTiltMixin {

    /**
     * The shared per-call context: {@code {signed φ (north-positive), δ(today)}} — or {@code null} when the
     * tilt is inactive (flag off / not a globe world / not the overworld / no player), in which case every
     * surface below passes vanilla through untouched. φ = {@code -degreesFromZ} (§4a: the project's north is
     * −Z; the kernel wants north-positive). δ comes from the SAME vanilla-synced clock the server mob rules
     * read ({@code getOverworldClockTime}, §7 zero-netcode law).
     */
    @Unique
    private static double[] globe$tiltContext() {
        if (!LatitudeV2Flags.SOLAR_TILT_V2_ENABLED) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return null;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return null;
        }
        if (!mc.level.dimension().identifier().equals(Level.OVERWORLD.identifier())) {
            return null;
        }
        double phi = -LatitudeMath.degreesFromZ(mc.level.getWorldBorder(), mc.player.getZ());
        double day = SolarTilt.dayCount(mc.level.getOverworldClockTime());
        double delta = SolarTilt.deltaDeg(day, LatitudeV2Flags.SOLAR_TILT_DELTA_MAX_DEG,
                LatitudeV2Flags.SOLAR_TILT_YEAR_LENGTH_DAYS, LatitudeV2Flags.SOLAR_TILT_FROZEN_PHASE_DEG);
        return new double[] {phi, delta};
    }

    /** SUN (ordinal 1): full tilt + declination, ROLL-FREE (S11d — the bare composition rolled the quad into
     *  the TEST 101 "diamond sun"; the horizon-locked rebuild keeps the direction and uprights the billboard).
     *  Ordinal 0 (the shared yaw) is not wrapped, so flag-off/off-globe frames execute vanilla's exact chain. */
    @WrapOperation(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 1))
    private void globe$tiltSunPose(PoseStack stack, Quaternionfc vanillaRotation, Operation<Void> original) {
        double[] ctx = globe$tiltContext();
        if (ctx == null) {
            original.call(stack, vanillaRotation);
            return;
        }
        original.call(stack, SolarPose.rollFreeTiltedBodyPose(vanillaRotation, ctx[0], ctx[1]));
    }

    /** MOON (ordinal 2): full tilt + the MIRROR declination −δ (S11e — the TEST 101 "sun AND moon both up
     *  under the midnight sun" bug). The moon's own vanilla angle (antipodal H+π) always survived the wrap;
     *  the bug was giving the moon the SUN's +δ, which hoisted it onto the sun's never-setting small circle —
     *  in the midnight-sun band EVERY point of that circle is above the horizon, so the phase offset no
     *  longer implied "below it". With −δ (the full-moon-antipode simplification: the moon rides the mirror
     *  circle) the moon is the sun's EXACT antipode — midnight sun ⇒ moon permanently down, polar night ⇒
     *  the moon owns the sky — and δ = 0 still reduces to vanilla. Roll-free like the sun (same billboard). */
    @WrapOperation(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 2))
    private void globe$tiltMoonPose(PoseStack stack, Quaternionfc vanillaRotation, Operation<Void> original) {
        double[] ctx = globe$tiltContext();
        if (ctx == null) {
            original.call(stack, vanillaRotation);
            return;
        }
        original.call(stack, SolarPose.rollFreeTiltedBodyPose(vanillaRotation, ctx[0], -ctx[1]));
    }

    /** Stars (ordinal 3): tilt only — the sphere wheels around a celestial pole at altitude φ (§5: the δ
     *  offset is meaningless for a full-sphere field). */
    @WrapOperation(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 3))
    private void globe$tiltStarPose(PoseStack stack, Quaternionfc vanillaRotation, Operation<Void> original) {
        double[] ctx = globe$tiltContext();
        if (ctx == null) {
            original.call(stack, vanillaRotation);
            return;
        }
        original.call(stack, SolarPose.tiltedStarPose(vanillaRotation, ctx[0]));
    }

    /** A3: no vanilla sunrise/sunset glow inside the midnight-sun / polar-night bands (wrong compass point,
     *  wrong hours). Equatorward of the visual onset the glow is vanilla-untouched. */
    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void globe$suppressPolarGlow(PoseStack poseStack, float alpha, int color, CallbackInfo ci) {
        double[] ctx = globe$tiltContext();
        if (ctx == null) {
            return;
        }
        if (Math.abs(ctx[0]) >= SolarTilt.onsetLatDeg(ctx[1])) {
            ci.cancel();
        }
    }

    /** §6/A4: the band moods, painted onto the freshly-extracted state. Gated to the client's own level. */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void globe$applySkyMood(ClientLevel level, float partialTick, Camera camera, SkyRenderState state,
                                    CallbackInfo ci) {
        double[] ctx = globe$tiltContext();
        if (ctx == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            return; // only the level the client player is actually in (the storm-sky mixin's discipline)
        }
        double phi = ctx[0];
        double delta = ctx[1];
        // Elevation from the same clock (H's sign convention differs from the render pose's vanilla angle,
        // but elevation is EVEN in H, so the mood cannot feel it).
        double hourAngle = SolarTilt.hourAngleRadians(SolarTilt.timeOfDayFrac(level.getOverworldClockTime()));
        double elevation = SolarTilt.solarElevationDeg(phi, delta, hourAngle);
        // A4: the storm always wins — both moods dissolve into the 85->87.5 overcast (and rainBrightness is
        // never touched here).
        float storm = PolarHazardWindow.stormLevel(Math.abs(phi));
        if (SolarTilt.isPolarNight(phi, delta)) {
            double gloom = SolarSkyMood.stormDamp(SolarSkyMood.polarNightGloom01(elevation), storm);
            state.skyColor = SolarSkyMood.blendRgb(state.skyColor, SolarSkyMood.POLAR_NIGHT_SKY_RGB,
                    gloom * SolarSkyMood.GLOOM_MAX_BLEND);
            state.starBrightness = SolarSkyMood.liftedStarBrightness(state.starBrightness, gloom);
        } else if (SolarTilt.isMidnightSun(phi, delta)) {
            double gold = SolarSkyMood.stormDamp(SolarSkyMood.midnightSunGold01(elevation), storm);
            state.skyColor = SolarSkyMood.blendRgb(state.skyColor, SolarSkyMood.MIDNIGHT_SUN_SKY_RGB,
                    gold * SolarSkyMood.GOLD_MAX_BLEND);
        }
    }
}
