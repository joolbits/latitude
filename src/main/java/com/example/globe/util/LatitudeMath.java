package com.example.globe.util;

import net.minecraft.world.border.WorldBorder;

public final class LatitudeMath {
    private LatitudeMath() {
    }

    public static int latitudeDegrees(WorldBorder border, double z) {
        double centerZ = border.getCenterZ();
        double radius = border.getSize() * 0.5;
        double absZ = Math.abs(z - centerZ);
        if (radius <= 0.0) return 0;
        int deg = (int) Math.round(90.0 * (absZ / radius));
        if (deg < 0) deg = 0;
        if (deg > 90) deg = 90;
        return deg;
    }

    public static char hemisphere(WorldBorder border, double z) {
        double centerZ = border.getCenterZ();
        return z < centerZ ? 'N' : 'S';
    }
}
