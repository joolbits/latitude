package com.example.globe.client;

import net.minecraft.world.border.WorldBorder;

public final class LatitudeMath {
    private LatitudeMath() {
    }

    public static int latitudeDegRounded(double playerZ, WorldBorder border) {
        double radius = border.getSize() * 0.5;
        if (radius <= 0.0001) return 0;
        double frac = Math.min(1.0, Math.abs(playerZ) / radius);
        return (int) Math.round(frac * 90.0);
    }

    public static String formatLatitudeDeg(double playerZ, WorldBorder border) {
        int deg = latitudeDegRounded(playerZ, border);
        if (deg == 0) return "0\u00b0";
        String hemi = (playerZ < 0) ? "N" : "S";
        return deg + "\u00b0" + hemi;
    }
}
