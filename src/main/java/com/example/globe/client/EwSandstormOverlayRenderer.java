package com.example.globe.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public final class EwSandstormOverlayRenderer {

    private EwSandstormOverlayRenderer() {
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        double dist = GlobeClientState.ewDistToBorder(mc.player.getX());

        float t = 0.0f;
        if (dist < 500.0) {
            double clamped = Math.max(dist, 100.0);
            t = (float) ((500.0 - clamped) / (500.0 - 100.0));
            t = MathHelper.clamp(t, 0.0f, 1.0f);
        }
        if (t <= 0.0f) return;

        float tt = t * t;

        float r = 0.78f;
        float g = 0.67f;
        float b = 0.48f;
        float a = 0.40f * tt;

        matrices.push();
        Matrix4f m = matrices.peek().getPositionMatrix();

        RenderLayer layer = RenderLayers.debugQuads();
        VertexConsumer vc = consumers.getBuffer(layer);

        vc.vertex(m, -1f, -1f, 0f).color(r, g, b, a).texture(0f, 1f).light(0xF000F0);
        vc.vertex(m, 1f, -1f, 0f).color(r, g, b, a).texture(1f, 1f).light(0xF000F0);
        vc.vertex(m, 1f, 1f, 0f).color(r, g, b, a).texture(1f, 0f).light(0xF000F0);
        vc.vertex(m, -1f, 1f, 0f).color(r, g, b, a).texture(0f, 0f).light(0xF000F0);

        if (consumers instanceof Immediate imm) {
            imm.draw();
        }
        matrices.pop();
    }
}
