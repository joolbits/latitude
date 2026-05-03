package com.example.globe.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
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
        float a = 0.94f * tt;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        Matrix4f projection = new Matrix4f().setOrtho(0.0f, width, height, 0.0f, -1.0f, 1.0f);
        MatrixStack modelView = RenderSystem.getModelViewStack();

        RenderSystem.backupProjectionMatrix();
        modelView.push();
        try {
            RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);
            modelView.loadIdentity();
            RenderSystem.applyModelViewMatrix();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(0.0, height, 0.0).color(r, g, b, a).next();
            buffer.vertex(width, height, 0.0).color(r, g, b, a).next();
            buffer.vertex(width, 0.0, 0.0).color(r, g, b, a).next();
            buffer.vertex(0.0, 0.0, 0.0).color(r, g, b, a).next();
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
