package com.example.globe.client;

import com.mojang.blaze3d.vertex.PoseStack;

public final class EwSandstormOverlayRenderer {

    private EwSandstormOverlayRenderer() {
    }

    // 26.2 PORT NOTE: this world-space immediate-mode overlay relied on
    // net.minecraft.client.renderer.MultiBufferSource / MultiBufferSource.BufferSource, both of which were
    // removed in 26.2 when immediate-mode rendering was replaced by the FrameGraph/extract pipeline. This
    // method is NOT called anywhere -- the live east-west dust haze is drawn by EwSandstormOverlayHud via the
    // 26.2 GuiGraphicsExtractor. The original body is preserved below as reference; if this world-space variant
    // is ever revived it must be re-ported to the new render API. Stubbed to a no-op so the pivot compiles with
    // no behavior change (it was already dead code).
    public static void render(PoseStack matrices) {
        // no-op (see 26.2 PORT NOTE above)
    }

    /*
     * Original pre-26.2 immediate-mode implementation (references removed MultiBufferSource / BufferSource):
     *
     * public static void render(PoseStack matrices, MultiBufferSource consumers) {
     *     Minecraft mc = Minecraft.getInstance();
     *     if (mc == null || mc.player == null || mc.level == null) return;
     *     double dist = GlobeClientState.ewDistToBorder(mc.player.getX());
     *     float t = 0.0f;
     *     if (dist < 500.0) {
     *         double clamped = Math.max(dist, 100.0);
     *         t = (float) ((500.0 - clamped) / (500.0 - 100.0));
     *         t = Mth.clamp(t, 0.0f, 1.0f);
     *     }
     *     if (t <= 0.0f) return;
     *     float tt = t * t;
     *     float r = 0.78f, g = 0.67f, b = 0.48f, a = 0.40f * tt;
     *     matrices.pushPose();
     *     Matrix4f m = matrices.last().pose();
     *     RenderType layer = RenderTypes.debugQuads();
     *     VertexConsumer vc = consumers.getBuffer(layer);
     *     vc.addVertex(m, -1f, -1f, 0f).setColor(r, g, b, a).setUv(0f, 1f).setLight(0xF000F0);
     *     vc.addVertex(m,  1f, -1f, 0f).setColor(r, g, b, a).setUv(1f, 1f).setLight(0xF000F0);
     *     vc.addVertex(m,  1f,  1f, 0f).setColor(r, g, b, a).setUv(1f, 0f).setLight(0xF000F0);
     *     vc.addVertex(m, -1f,  1f, 0f).setColor(r, g, b, a).setUv(0f, 0f).setLight(0xF000F0);
     *     if (consumers instanceof BufferSource imm) { imm.endBatch(); }
     *     matrices.popPose();
     * }
     */
}
