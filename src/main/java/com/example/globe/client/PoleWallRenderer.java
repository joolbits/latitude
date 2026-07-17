package com.example.globe.client;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.util.LatitudeMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * <b>RETIRED -- S10b, owner decision 2026-07-16 (TEST 99 flight).</b> Built at P3 on the owner's TEST 97 ask
 * ("There needs to be the diagonal lines like vanilla"), then REVERSED after he flew it: "get rid of the
 * appearance of this diagonal wall... that's how 90 should feel" -- the S10b fog law's near-total whiteout
 * ({@code core.PolarFogLaw}: ~4-block visibility at the pole line) IS the wall's appearance now. DEAD-GATED,
 * not deleted: {@code GlobeModClient} no longer calls {@link #register()} AND register() itself no-ops, so a
 * future owner reversal is a two-line revival (B-6 taught us reversals happen both ways). The clamp,
 * ice-chime, pack-ice actionbar and frost particles stay live as the wall's TOUCH feedback.
 *
 * <p><i>Original design, kept for that revival:</i> the vanilla-world-border-style WALL VISUAL at the pole
 * line {@code z = centerZ +- zRadius} on WIDE worlds. On Classic worlds the vanilla square border IS the pole
 * wall and renders itself; on Wide the border is sized to the wider X axis, so the pole clamp (S2,
 * server-side) is an INVISIBLE wall -- this plane was its presentation: a translucent, animated
 * diagonal-stripe forcefield in the vanilla border's visual language.
 *
 * <p><b>Vanilla mimicry, minimally.</b> Vanilla 26.2 draws its border as a GPU-buffered quad wall of the
 * scrolling {@code textures/misc/forcefield.png} ({@link WorldBorderRenderer#FORCEFIELD_LOCATION} -- reused
 * verbatim, so the stripes ARE the vanilla diagonals), tinted and distance-faded. Rebuilding that GpuBuffer
 * machinery for one plane is not worth it; instead the plane is submitted through the 26.2 custom-geometry
 * path ({@code SubmitNodeCollector.submitCustomGeometry}, the supported idiom for exactly this) as a strip of
 * camera-relative quads with time-scrolled UVs -- same texture, same crawl period (~3 s), same
 * approach-fade shape. {@code entityTranslucentEmissive} keeps it translucent, depth-tested (terrain occludes
 * it; it shows above AND below the frozen sea) and FULLBRIGHT (a forcefield does not go dark at night).
 * Both windings are emitted so the wall is visible from both sides regardless of cull state.
 *
 * <p><b>Gates (perf-sane, honesty law).</b> Renders ONLY when: {@link LatitudeV2Flags#POLE_PASSAGE_V2_ENABLED}
 * (the flag that owns the S2 clamp this plane presents -- flag-off there is no wall, so nothing may be drawn),
 * a globe overworld, a WIDE world (X radius exceeds the Z radius), and the camera within
 * {@link #FADE_START_DIST} of the pole line. Everything else is a cheap early return each frame.
 */
public final class PoleWallRenderer {

    /** Distance (blocks) from the pole line at which the wall starts fading in (vanilla-style approach fade). */
    private static final double FADE_START_DIST = 40.0;
    /** Distance at/inside which the wall is at full strength. */
    private static final double FADE_FULL_DIST = 8.0;
    /** Peak plane alpha (0-1): translucent -- the world beyond stays visible through the forcefield. */
    private static final float MAX_ALPHA = 0.55f;
    /** Glacial blue-green tint (the vanilla stationary border is 0x20A0FF; this shifts it toward pack-ice
     *  teal while staying in the vanilla forcefield family). One-line P4 tunable. */
    private static final int WALL_R = 0x59;
    private static final int WALL_G = 0xD8;
    private static final int WALL_B = 0xE6;
    /** Half-span (blocks) of wall drawn either side of the camera X (covers the loaded extent at any render
     *  distance that matters this close to the wall). */
    private static final int X_HALF_SPAN = 256;
    /** Quad segment width (blocks). */
    private static final int X_STEP = 32;
    /** Texture repeats: one forcefield tile per this many blocks (vanilla's border density). */
    private static final float TEX_BLOCKS_PER_REPEAT = 2.0f;
    /** UV scroll period (ms) -- one full tile of diagonal crawl per period, matching vanilla's ~3 s drift. */
    private static final long SCROLL_PERIOD_MS = 3000L;
    /** Vanilla full-bright packed light (sky 15 / block 15) -- the LightTexture.FULL_BRIGHT value; the
     *  constant's home class was removed in 26.2, so the literal is pinned here. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private static boolean registered;

    private PoleWallRenderer() {
    }

    /** RETIRED (S10b, owner 2026-07-16): intentionally a NO-OP -- the striped plane must not render; the fog
     *  law's whiteout is the wall's appearance. To revive: restore the registration line below AND the
     *  {@code register()} call in {@code GlobeModClient.onInitializeClient}. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // LevelRenderEvents.COLLECT_SUBMITS.register(PoleWallRenderer::collectSubmits); // RETIRED S10b 2026-07-16
    }

    private static void collectSubmits(LevelRenderContext ctx) {
        if (!LatitudeV2Flags.POLE_PASSAGE_V2_ENABLED) {
            return; // no flag, no clamp, no wall -- nothing may present a barrier that does not exist.
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        if (!GlobeClientState.isGlobeWorld()) {
            return;
        }
        if (!mc.level.dimension().identifier().equals(Level.OVERWORLD.identifier())) {
            return; // the pole line exists only in the globe overworld.
        }
        var border = mc.level.getWorldBorder();
        double zRadius = LatitudeMath.latitudeRadius(border);
        double xHalf = LatitudeMath.halfSize(border);
        if (!(zRadius > 0.0) || xHalf <= zRadius + 1.0) {
            return; // Classic (square) world: the vanilla border already walls |z| = zRadius and draws itself.
        }
        var camState = ctx.levelState().cameraRenderState;
        if (camState == null || camState.pos == null) {
            return;
        }
        Vec3 cam = camState.pos;
        double centerZ = border.getCenterZ();
        double polewardSign = cam.z >= centerZ ? 1.0 : -1.0;
        double wallZ = centerZ + polewardSign * zRadius; // the nearer pole's wall plane
        double dist = Math.abs(wallZ - cam.z);
        if (dist > FADE_START_DIST) {
            return;
        }
        float fade = (float) Math.min(1.0, Math.max(0.0,
                (FADE_START_DIST - dist) / (FADE_START_DIST - FADE_FULL_DIST)));
        int alpha = Math.round(MAX_ALPHA * fade * 255.0f);
        if (alpha <= 1) {
            return;
        }
        int argb = (alpha << 24) | (WALL_R << 16) | (WALL_G << 8) | WALL_B;

        // World-height extent (the vanilla border wall reads as full-height).
        float yBottom = (float) (mc.level.getMinY() - cam.y);
        float yTop = (float) (mc.level.getMaxY() + 1 - cam.y);
        float rz = (float) (wallZ - cam.z);
        // Diagonal crawl: the same offset advances u AND v, so the (already diagonal) vanilla stripes drift
        // along their own axis exactly like the border forcefield.
        float scroll = (System.currentTimeMillis() % SCROLL_PERIOD_MS) / (float) SCROLL_PERIOD_MS;
        float vBottom = (float) ((mc.level.getMinY()) / TEX_BLOCKS_PER_REPEAT) + scroll;
        float vTop = (float) ((mc.level.getMaxY() + 1) / TEX_BLOCKS_PER_REPEAT) + scroll;

        // Segment grid snapped to X_STEP so UVs tile seamlessly as the camera moves.
        int xStart = (int) Math.floor((cam.x - X_HALF_SPAN) / (double) X_STEP) * X_STEP;
        int xEnd = (int) Math.ceil((cam.x + X_HALF_SPAN) / (double) X_STEP) * X_STEP;
        final double camX = cam.x;

        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(),
                RenderTypes.entityTranslucentEmissive(WorldBorderRenderer.FORCEFIELD_LOCATION),
                (pose, vc) -> {
                    for (int x = xStart; x < xEnd; x += X_STEP) {
                        float rx0 = (float) (x - camX);
                        float rx1 = (float) (x + X_STEP - camX);
                        float u0 = x / TEX_BLOCKS_PER_REPEAT + scroll;
                        float u1 = (x + X_STEP) / TEX_BLOCKS_PER_REPEAT + scroll;
                        // Facing equatorward (the side the approaching player sees) ...
                        quad(pose, vc, rx0, rx1, yBottom, yTop, rz, u0, u1, vBottom, vTop, argb,
                                (float) -polewardSign);
                        // ... and the reverse winding so a beyond-the-line survivor still sees the wall.
                        quadReversed(pose, vc, rx0, rx1, yBottom, yTop, rz, u0, u1, vBottom, vTop, argb,
                                (float) polewardSign);
                    }
                });
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float x1, float y0, float y1,
                             float z, float u0, float u1, float v0, float v1, int argb, float normalZ) {
        vertex(pose, vc, x0, y0, z, u0, v0, argb, normalZ);
        vertex(pose, vc, x0, y1, z, u0, v1, argb, normalZ);
        vertex(pose, vc, x1, y1, z, u1, v1, argb, normalZ);
        vertex(pose, vc, x1, y0, z, u1, v0, argb, normalZ);
    }

    private static void quadReversed(PoseStack.Pose pose, VertexConsumer vc, float x0, float x1, float y0,
                                     float y1, float z, float u0, float u1, float v0, float v1, int argb,
                                     float normalZ) {
        vertex(pose, vc, x1, y0, z, u1, v0, argb, normalZ);
        vertex(pose, vc, x1, y1, z, u1, v1, argb, normalZ);
        vertex(pose, vc, x0, y1, z, u0, v1, argb, normalZ);
        vertex(pose, vc, x0, y0, z, u0, v0, argb, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z,
                               float u, float v, int argb, float normalZ) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0f, 0.0f, normalZ);
    }
}
