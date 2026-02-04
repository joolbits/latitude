package com.example.globe.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class EwStormWallRenderer {
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

    public static void renderWall(MatrixStack.Entry entry, VertexConsumer vc, Vec3d cam,
                                  double westX, double eastX, double dist) {
        if (dist > 600.0) {
            return;
        }

        int alphaBottom = 70;
        int alphaTop = 130;
        int r = 255;
        int g = 0;
        int b = 255;

        double z0 = cam.z - 192.0;
        double z1 = cam.z + 192.0;
        double zStep = 8.0;

        double y1 = -64.0;
        double y2 = 320.0;

        double inset = 2.5;
        boolean eastCloser = isEastCloser(cam.x, westX, eastX);
        double planeX = eastCloser ? (eastX - inset) : (westX + inset);

        for (double z = z0; z < z1; z += zStep) {
            double zStart = z;
            double zEnd = Math.min(z + zStep, z1);
            float rz0 = (float) (zStart - cam.z);
            float rz1 = (float) (zEnd - cam.z);

            float rx = (float) (planeX - cam.x);

            int argbBottom = (alphaBottom << 24) | (r << 16) | (g << 8) | b;
            int argbTop = (alphaTop << 24) | (r << 16) | (g << 8) | b;

            vc.vertex(entry, rx, (float) y1, rz0).color(argbBottom);
            vc.vertex(entry, rx, (float) y2, rz0).color(argbTop);
            vc.vertex(entry, rx, (float) y2, rz1).color(argbTop);
            vc.vertex(entry, rx, (float) y1, rz1).color(argbBottom);
        }
    }
}
