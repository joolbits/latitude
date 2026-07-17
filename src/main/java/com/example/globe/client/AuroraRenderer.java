package com.example.globe.client;

import com.example.globe.core.AuroraLaw;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.PolarHazardWindow;
import com.example.globe.core.SolarTilt;
import com.example.globe.util.LatitudeMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Phase 5 Slice S13(a) -- AURORA BOREALIS renderer: translucent animated ribbon curtains high in the poleward
 * sky. The pure VISUAL LAW (gate, colours, waver, per-vertex alpha) lives in {@link AuroraLaw}; this class is
 * the thin renderer that maps it to camera-relative geometry.
 *
 * <p><b>Render path -- why {@code COLLECT_SUBMITS}, not a {@code SkyRenderer} mixin.</b> Vanilla 26.2 draws the
 * sun/moon/stars through the low-level GPU pipeline ({@code RenderSystem.getDevice().createCommandEncoder()
 * .createRenderPass(...)} + {@code RenderPipelines.CELESTIAL} + pre-built {@code GpuBuffer}s), which cannot take
 * dynamic per-frame immediate geometry from a mixin without unverifiable render-pipeline work. The one PROVEN
 * custom-geometry idiom in this codebase is the Fabric {@link LevelRenderEvents#COLLECT_SUBMITS} +
 * {@code submitCustomGeometry} path {@link PoleWallRenderer} already ships on -- it hands us an immediate
 * {@code VertexConsumer} for a chosen {@code RenderType} and owns all the pipeline/buffer/pass machinery. So the
 * aurora rides that path, emitting camera-relative strips (like the solar family's camera-relative sky) through
 * {@link RenderTypes#lightning()}: an UNTEXTURED, ADDITIVE, {@code POSITION_COLOR} type -- inherently FULLBRIGHT
 * (no light-texture darkening; the colour IS the emission) and translucent, so the curtains GLOW against the
 * night sky with no shaders and no texture asset. Depth-tested (terrain at the horizon occludes the curtain
 * base, physically right).
 *
 * <p><b>The one live-tuning caveat (P4).</b> Because this is world-pass (not sky-pass) geometry it is subject to
 * the camera far plane, so at very low render distances a far curtain could clip. The layout constants below
 * ({@link #SKY_RADIUS} / {@link #BASE_HEIGHT}) are kept within a conservative distance and are the P4 dials if
 * it clips on the owner's render distance; the feature reads best at render distance &gt;= ~12. The math half
 * ({@link AuroraLaw}) is pinned headlessly; like the solar mixins, "it actually draws" falls to the owner
 * flight / orchestrator live lane.
 *
 * <p><b>Gates (perf-sane, honesty law).</b> Renders ONLY when {@link LatitudeV2Flags#SOLAR_TILT_V2_ENABLED}
 * (the aurora rides inside the solar sky family -- the polar-night/midnight-sun bands it keys on only exist
 * with the tilt on) AND {@link LatitudeV2Flags#AURORA_ENABLED} (its own kill switch), on a globe overworld with
 * a player, at {@code |lat| >=} {@link AuroraLaw#LAT_ONSET_DEG}, and with a non-negligible
 * {@link AuroraLaw#intensity01}. Every other frame is a cheap early return -- including, before ANY solar math,
 * the flag + latitude checks.
 */
public final class AuroraRenderer {

    // ---- render-space layout (P4 dials; the visual LAW is in AuroraLaw) --------------------------------

    /** Along-arc segments per band. {@code BAND_COUNT(3) x SEGMENTS x (LEVELS-1=2) x 4} verts = a few hundred. */
    private static final int SEGMENTS = 16;
    /** Arc width (rad) each curtain sweeps across the poleward sky. */
    private static final double ARC_SPAN_RAD = Math.toRadians(150.0);
    /** Horizontal distance (blocks) of the curtains from the camera. Kept conservative for the far plane (see
     *  class doc); with {@link #BASE_HEIGHT} it sets the apparent elevation of the aurora band. */
    private static final double SKY_RADIUS = 96.0;
    /** Height (blocks) of the lowest curtain's base above the camera -- "high in the sky above the celestial
     *  bodies' band" without pinning to the zenith (where a curtain reads flat). */
    private static final double BASE_HEIGHT = 40.0;
    /** Vertical extent (blocks) of a curtain from its bright lower edge to its wispy top. */
    private static final double CURTAIN_HEIGHT = 56.0;
    /** Master brightness scale (0..1) folded into every vertex's alpha -- the one dial for "how bright is the
     *  whole aurora". Additive blending means this is a glow strength, not an opacity. */
    private static final double GLOBAL_ALPHA_SCALE = 0.8;
    /** Below this master intensity the aurora is not worth drawing (perf early-out). */
    private static final double MIN_VISIBLE_INTENSITY = 0.02;

    private static boolean registered;

    private AuroraRenderer() {
    }

    /** Register the COLLECT_SUBMITS hook once (idempotent). Called from {@code GlobeModClient.onInitializeClient}. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        LevelRenderEvents.COLLECT_SUBMITS.register(AuroraRenderer::collectSubmits);
    }

    private static void collectSubmits(LevelRenderContext ctx) {
        if (!LatitudeV2Flags.SOLAR_TILT_V2_ENABLED || !LatitudeV2Flags.AURORA_ENABLED) {
            return; // aurora rides inside the solar sky family + has its own kill switch: byte-identical off.
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        if (!mc.level.dimension().identifier().equals(Level.OVERWORLD.identifier())) {
            return; // the polar sky exists only in the globe overworld.
        }
        WorldBorder border = mc.level.getWorldBorder();
        // North-positive signed latitude (project north is -Z; the kernel wants north-positive), same as the
        // solar tilt mixin's context.
        double phi = -LatitudeMath.degreesFromZ(border, mc.player.getZ());
        double absLat = Math.abs(phi);
        if (absLat < AuroraLaw.LAT_ONSET_DEG) {
            return; // equatorward of the ramp: skip the solar math entirely.
        }
        // Dark-sky signal from the SAME vanilla-synced clock the solar mood reads (§7 zero-netcode; elevation is
        // even in H, so the pose's differing angle sign cannot be felt).
        long clock = mc.level.getOverworldClockTime();
        double delta = SolarTilt.deltaDeg(SolarTilt.dayCount(clock), LatitudeV2Flags.SOLAR_TILT_DELTA_MAX_DEG,
                LatitudeV2Flags.SOLAR_TILT_YEAR_LENGTH_DAYS, LatitudeV2Flags.SOLAR_TILT_FROZEN_PHASE_DEG);
        double elevation = SolarTilt.solarElevationDeg(phi, delta,
                SolarTilt.hourAngleRadians(SolarTilt.timeOfDayFrac(clock)));
        float storm = PolarHazardWindow.stormLevel(absLat);
        double intensity = AuroraLaw.intensity01(absLat, elevation, storm) * GLOBAL_ALPHA_SCALE;
        if (intensity < MIN_VISIBLE_INTENSITY) {
            return; // daylight / midnight sun / storm / too faint: nothing to draw.
        }

        // Deterministic motion off the WORLD CLOCK (monotonic game-time ticks) -- never a wall-clock accumulator.
        final double t = mc.level.getGameTime();
        // Poleward direction in world Z: north player (phi >= 0) faces the -Z pole, south faces +Z.
        final double poleZ = phi >= 0.0 ? -1.0 : 1.0;
        final double az0 = AuroraLaw.azimuthDriftRad(t);
        final double finalIntensity = intensity;

        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.lightning(),
                (pose, vc) -> {
                    for (int b = 0; b < AuroraLaw.BAND_COUNT; b++) {
                        double bandAz = az0 + AuroraLaw.bandAzimuthOffsetRad(b);
                        double bandBaseY = BASE_HEIGHT + AuroraLaw.bandHeightOffsetBlocks(b);
                        for (int i = 0; i < SEGMENTS; i++) {
                            double u0 = i / (double) SEGMENTS;
                            double u1 = (i + 1) / (double) SEGMENTS;
                            double a0 = bandAz + (u0 - 0.5) * ARC_SPAN_RAD;
                            double a1 = bandAz + (u1 - 0.5) * ARC_SPAN_RAD;
                            double w0 = AuroraLaw.verticalWaverBlocks(t, b, u0);
                            double w1 = AuroraLaw.verticalWaverBlocks(t, b, u1);
                            // Stacked quad rows between adjacent vertical levels (bright green base -> purple top).
                            for (int r = 0; r < AuroraLaw.LEVELS - 1; r++) {
                                vertex(pose, vc, a0, u0, w0, bandBaseY, r, poleZ, finalIntensity);
                                vertex(pose, vc, a0, u0, w0, bandBaseY, r + 1, poleZ, finalIntensity);
                                vertex(pose, vc, a1, u1, w1, bandBaseY, r + 1, poleZ, finalIntensity);
                                vertex(pose, vc, a1, u1, w1, bandBaseY, r, poleZ, finalIntensity);
                            }
                        }
                    }
                });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, double az, double u, double waver,
                               double bandBaseY, int level, double poleZ, double intensity) {
        double y = bandBaseY + waver + AuroraLaw.LEVEL_V[level] * CURTAIN_HEIGHT;
        double x = SKY_RADIUS * Math.sin(az);
        double z = SKY_RADIUS * Math.cos(az) * poleZ;
        vc.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(AuroraLaw.vertexArgb(level, u, intensity));
    }
}
