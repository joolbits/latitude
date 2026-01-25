package com.example.globe.util;

import net.minecraft.world.border.WorldBorder;

public final class LatitudeMath {
    private LatitudeMath() {
    }

    public enum LatitudeZone {
        EQUATOR,
        TROPICAL,
        SUBTROPICAL,
        TEMPERATE,
        SUBPOLAR,
        POLAR
    }

    public static final double EQUATOR_MAX_FRAC = 0.10;
    public static final double TROPICAL_MAX_FRAC = 0.30;
    public static final double SUBTROPICAL_MAX_FRAC = 0.50;
    public static final double TEMPERATE_MAX_FRAC = 0.666;
    public static final double SUBPOLAR_MAX_FRAC = 0.783;

    public static final int EQUATOR_MAX_DEG = (int) Math.ceil(EQUATOR_MAX_FRAC * 90.0);
    public static final int TROPICAL_MAX_DEG = (int) Math.ceil(TROPICAL_MAX_FRAC * 90.0);
    public static final int SUBTROPICAL_MAX_DEG = (int) Math.ceil(SUBTROPICAL_MAX_FRAC * 90.0);
    public static final int TEMPERATE_MAX_DEG = (int) Math.ceil(TEMPERATE_MAX_FRAC * 90.0);
    public static final int SUBPOLAR_MAX_DEG = (int) Math.ceil(SUBPOLAR_MAX_FRAC * 90.0);

    public static final double POLAR_START_FRAC = SUBPOLAR_MAX_FRAC;
    public static final int POLAR_START_DEG = (int) Math.floor(POLAR_START_FRAC * 90.0);

    public static double worldRadiusBlocks(WorldBorder border) {
        if (border == null) return 0.0;
        return border.getSize() * 0.5;
    }

    public static double absLatFraction(WorldBorder border, double z) {
        if (border == null) return 0.0;

        double radius = worldRadiusBlocks(border);
        if (radius <= 0.0001) return 0.0;

        double centerZ = border.getCenterZ();
        double frac = Math.abs(z - centerZ) / radius;
        if (frac < 0.0) frac = 0.0;
        if (frac > 1.0) frac = 1.0;
        return frac;
    }

    public static double absLatDegExact(WorldBorder border, double z) {
        return absLatFraction(border, z) * 90.0;
    }

    public static int latitudeDegrees(WorldBorder border, double z) {
        int deg = (int) Math.round(absLatDegExact(border, z));
        if (deg < 0) deg = 0;
        if (deg > 90) deg = 90;
        return deg;
    }

    public static char hemisphere(WorldBorder border, double z) {
        double centerZ = border != null ? border.getCenterZ() : 0.0;
        return z < centerZ ? 'N' : 'S';
    }

    public static String formatLatitudeDeg(WorldBorder border, double z) {
        int deg = latitudeDegrees(border, z);
        if (deg == 0) return "0\u00b0";
        char hemi = hemisphere(border, z);
        return deg + "\u00b0" + hemi;
    }

    public static LatitudeZone zoneForDeg(int deg) {
        if (deg < EQUATOR_MAX_DEG) return LatitudeZone.EQUATOR;
        if (deg < TROPICAL_MAX_DEG) return LatitudeZone.TROPICAL;
        if (deg < SUBTROPICAL_MAX_DEG) return LatitudeZone.SUBTROPICAL;
        if (deg < TEMPERATE_MAX_DEG) return LatitudeZone.TEMPERATE;
        if (deg < SUBPOLAR_MAX_DEG) return LatitudeZone.SUBPOLAR;
        return LatitudeZone.POLAR;
    }

    public static LatitudeZone zoneFor(WorldBorder border, double z) {
        double t = absLatFraction(border, z);
        if (t < EQUATOR_MAX_FRAC) return LatitudeZone.EQUATOR;
        if (t < TROPICAL_MAX_FRAC) return LatitudeZone.TROPICAL;
        if (t < SUBTROPICAL_MAX_FRAC) return LatitudeZone.SUBTROPICAL;
        if (t < TEMPERATE_MAX_FRAC) return LatitudeZone.TEMPERATE;
        if (t < SUBPOLAR_MAX_FRAC) return LatitudeZone.SUBPOLAR;
        return LatitudeZone.POLAR;
    }

    public static String zoneKey(WorldBorder border, double z) {
        return zoneFor(border, z).name();
    }

    public static double spawnFracForZoneKey(String zoneKey) {
        if (zoneKey == null) return 0.0;
        return switch (zoneKey) {
            case "EQUATOR" -> 0.0;
            case "TROPICAL" -> 0.20;
            case "SUBTROPICAL" -> 0.40;
            case "TEMPERATE" -> 0.583;
            case "SUBPOLAR" -> 0.725;
            case "POLAR" -> 0.83;
            default -> 0.0;
        };
    }

    public static int zoneCenterDeg(String zoneKey) {
        double t = spawnFracForZoneKey(zoneKey);
        int deg = (int) Math.round(t * 90.0);
        if (deg < 0) deg = 0;
        if (deg > 90) deg = 90;
        return deg;
    }
}
