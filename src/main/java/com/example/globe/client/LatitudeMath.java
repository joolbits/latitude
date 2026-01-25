package com.example.globe.client;

import net.minecraft.world.border.WorldBorder;

public final class LatitudeMath {
    private LatitudeMath() {
    }

    public static int latitudeDegRounded(double playerZ, WorldBorder border) {
        double radius = border.getSize() * 0.5;
        if (radius <= 0.0001) return 0;
        double frac = Math.abs(playerZ) / radius;
        if (frac < 0.0) frac = 0.0;
        if (frac > 1.0) frac = 1.0;
        int deg = (int) Math.round(frac * 90.0);
        if (deg < 0) deg = 0;
        if (deg > 90) deg = 90;
        return deg;
    }

    public static String formatLatitudeDeg(double playerZ, WorldBorder border) {
        int deg = latitudeDegRounded(playerZ, border);
        if (deg == 0) return "0\u00b0";
        String hemi = (playerZ < 0) ? "N" : "S";
        return deg + "\u00b0" + hemi;
    }
}
