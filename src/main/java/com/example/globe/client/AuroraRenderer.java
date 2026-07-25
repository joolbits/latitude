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
 * aurora rides that path, emitting camera-relative sheets (like the solar family's camera-relative sky).
 *
 * <p><b>S15(f) -- render type: {@link RenderTypes#debugQuads()}, NOT {@code lightning()} (the cloud carve-out
 * fix).</b> The owner (TEST 105) saw the suppressed aurora leave a "strange carve-out over the clouds". Receipt
 * (decompiled 26.2): {@code RenderTypes.lightning()} rides {@code RenderPipelines.LIGHTNING}, whose depth state
 * is {@code DepthStencilState.DEFAULT = (GREATER_THAN_OR_EQUAL, writeDepth=TRUE)} -- so every aurora quad, even
 * a near-invisible additive one, WRITES DEPTH into the shared weather target; clouds drawn behind it then fail
 * the reversed-Z depth test and vanish, a cloud-shaped hole. The fix is a render type that depth-TESTS (terrain
 * still occludes the sheet base) but never depth-WRITES: {@code debugQuads()} rides {@code DEBUG_QUADS} whose
 * {@code DEBUG_FILLED_SNIPPET} is {@code POSITION_COLOR} + QUADS + {@code BlendFunction.TRANSLUCENT} +
 * {@code DepthStencilState(GREATER_THAN_OR_EQUAL, writeDepth=FALSE)} -- untextured (shader {@code position_color},
 * no asset), the SAME vertex format the aurora already emits, so nothing downstream (clouds included) is ever
 * occluded by the sheet. TRANSLUCENT (was additive) also serves S15(e): a soft colour wash reads "ethereal"
 * where the additive glow read "glitchy". ("It actually draws"/compositing-over-clouds is a P4 live check, same
 * honesty caveat as every renderer here; the depth receipt is the pinned decision.)
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

    /** Along-arc segments per sheet. S15(e): 16 -> 24 for a smoother broad wash. {@code BAND_COUNT(2) x SEGMENTS
     *  x (LEVELS-1=4) x 4} verts = a few hundred. */
    private static final int SEGMENTS = 24;
    /** Arc width (rad) each sheet sweeps across the poleward sky. S15(e): 150 -> 168 deg -- broad sheets. */
    private static final double ARC_SPAN_RAD = Math.toRadians(168.0);
    /** Horizontal distance (blocks) of the sheets from the camera. Kept conservative for the far plane (see
     *  class doc); with {@link #BASE_HEIGHT} it sets the apparent elevation of the aurora. */
    private static final double SKY_RADIUS = 96.0;
    /** Height (blocks) of the lowest sheet's base above the camera -- "high in the sky above the celestial
     *  bodies' band" without pinning to the zenith (where a sheet reads flat). */
    private static final double BASE_HEIGHT = 40.0;
    /** Vertical extent (blocks) of a sheet from its soft lower edge to its soft top. S15(e): 56 -> 72 -- a
     *  taller sheet spreads the soft vertical gradient so no edge reads as a hard line. */
    private static final double CURTAIN_HEIGHT = 72.0;
    /** Master alpha scale (0..1) folded into every vertex's alpha -- the one dial for "how strong is the whole
     *  aurora". S15(e): 0.8 -> 0.45 (roughly halved) -- with TRANSLUCENT blending this is an opacity, so the
     *  sheet is a faint colour wash, not a bright glow. */
    private static final double GLOBAL_ALPHA_SCALE = 0.45;
    /** Below this master intensity the aurora is not worth drawing (perf early-out AND the S15(f) hard skip:
     *  this returns BEFORE any {@code submitCustomGeometry}, so a fully-suppressed aurora emits NO geometry at
     *  all -- nothing to occlude clouds). */
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

        // S15(f): debugQuads() = POSITION_COLOR + TRANSLUCENT + depth-test-but-NO-depth-write, so the sheet is
        // occluded BY terrain yet never occludes (carves) the clouds behind it. See the class doc for the receipt.
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.debugQuads(),
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
                            // Stacked quad rows between adjacent vertical levels: a soft green-teal->purple wash
                            // whose per-vertex alpha fades to near-zero at the top AND bottom edges (S15(e)).
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
