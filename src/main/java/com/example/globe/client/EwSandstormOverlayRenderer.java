package com.example.globe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class EwSandstormOverlayRenderer {

    private EwSandstormOverlayRenderer() {
    }

    public static void render(PoseStack matrices, MultiBufferSource consumers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        double dist = GlobeClientState.ewDistToBorder(mc.player.getX());

        float t = 0.0f;
        if (dist < 500.0) {
            double clamped = Math.max(dist, 100.0);
            t = (float) ((500.0 - clamped) / (500.0 - 100.0));
            t = Mth.clamp(t, 0.0f, 1.0f);
        }
        if (t <= 0.0f) return;

        float tt = t * t;

        float r = 0.78f;
        float g = 0.67f;
        float b = 0.48f;
        float a = 0.40f * tt;

        matrices.pushPose();
        Matrix4f m = matrices.last().pose();

        RenderType layer = RenderTypes.debugQuads();
        VertexConsumer vc = consumers.getBuffer(layer);

        vc.addVertex(m, -1f, -1f, 0f).setColor(r, g, b, a).setUv(0f, 1f).setLight(0xF000F0);
        vc.addVertex(m, 1f, -1f, 0f).setColor(r, g, b, a).setUv(1f, 1f).setLight(0xF000F0);
        vc.addVertex(m, 1f, 1f, 0f).setColor(r, g, b, a).setUv(1f, 0f).setLight(0xF000F0);
        vc.addVertex(m, -1f, 1f, 0f).setColor(r, g, b, a).setUv(0f, 0f).setLight(0xF000F0);

        if (consumers instanceof BufferSource imm) {
            imm.endBatch();
        }
        matrices.popPose();
    }
}
