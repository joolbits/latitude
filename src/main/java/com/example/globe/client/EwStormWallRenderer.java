package com.example.globe.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class EwStormWallRenderer {
    private static final int WALL_Z_HALFSPAN = 2048;
    private static final int WALL_Z_STEP = 16;
    private static final Identifier WALL_TEXTURE = Identifier.of("minecraft", "textures/entity/beacon_beam.png");
    private EwStormWallRenderer() {
    }

    public static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    public static double t500(double dist) {
        return clamp01(1.0 - dist / 500.0);
    }

    public static double t100(double dist) {
        return clamp01(1.0 - dist / 100.0);
    }

    public static boolean isEastCloser(double camX, double westX, double eastX) {
        double dWest = Math.abs(camX - westX);
        double dEast = Math.abs(eastX - camX);
        return dEast <= dWest;
    }

    public static void renderWall(MatrixStack.Entry entry, VertexConsumer vc, double camX, double camZ,
                                  double westX, double eastX, double dist) {
        if (dist > 600.0) {
            return;
        }

        int alphaBottom = 90;
        int alphaTop = 180;
        float r = 0.15f;
        float g = 0.20f;
        float b = 0.30f;

        long time = System.currentTimeMillis();
        float shimmer = (float) (Math.sin(time * 0.001) * 0.05 + 0.05);
        g = 0.20f + shimmer;

        int zStart = (int) Math.floor((camZ - WALL_Z_HALFSPAN) / (double) WALL_Z_STEP) * WALL_Z_STEP;
        int zEnd = (int) Math.ceil((camZ + WALL_Z_HALFSPAN) / (double) WALL_Z_STEP) * WALL_Z_STEP;

        double y1 = -64.0;
        double y2 = 320.0;

        double inset = 2.5;
        boolean eastCloser = isEastCloser(camX, westX, eastX);
        double planeX = eastCloser ? (eastX - inset) : (westX + inset);

        for (int z = zStart; z < zEnd; z += WALL_Z_STEP) {
            int z0 = z;
            int z1 = z + WALL_Z_STEP;
            float rz0 = (float) (z0 - camZ);
            float rz1 = (float) (z1 - camZ);

            float rx = (float) (planeX - camX);

            int ir = Math.round(r * 255.0f);
            int ig = Math.round(g * 255.0f);
            int ib = Math.round(b * 255.0f);

            int argbBottom = (alphaBottom << 24) | (ir << 16) | (ig << 8) | ib;
            int argbTop = (alphaTop << 24) | (ir << 16) | (ig << 8) | ib;

            vc.vertex(entry, rx, (float) y1, rz0).color(argbBottom);
            vc.vertex(entry, rx, (float) y2, rz0).color(argbTop);
            vc.vertex(entry, rx, (float) y2, rz1).color(argbTop);
            vc.vertex(entry, rx, (float) y1, rz1).color(argbBottom);
        }
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.gameRenderer == null) return;
        if (!GlobeClientState.DEBUG_EW_WALL) return;
        // TEMP: fog-only verification (wall disabled)
        return;
    }
}
