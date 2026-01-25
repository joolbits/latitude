package com.example.globe.client;

import net.minecraft.world.border.WorldBorder;

public final class LatitudeMath {
    private LatitudeMath() {
    }

    public static int latitudeDegRounded(double playerZ, WorldBorder border) {
        return com.example.globe.util.LatitudeMath.latitudeDegrees(border, playerZ);
    }

    public static String formatLatitudeDeg(double playerZ, WorldBorder border) {
        return com.example.globe.util.LatitudeMath.formatLatitudeDeg(border, playerZ);
    }
}
