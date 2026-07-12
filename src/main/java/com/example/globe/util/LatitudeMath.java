package com.example.globe.util;

import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;

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

    // Canonical LCMM v2 band boundaries (source of truth: LatitudeBands).
    // EQUATOR is a display-only sub-zone within Tropical (worldgen has no EQUATOR band).
    public static final double EQUATOR_MAX_FRAC = 0.10;          //  ~9°  (display sub-zone of Tropical)
    public static final double TROPICAL_MAX_FRAC = 23.5 / 90.0;  // 23.5° (canonical Tropical/Subtropical boundary)
    public static final double SUBTROPICAL_MAX_FRAC = 35.0 / 90.0; // 35° (canonical Subtropical/Temperate boundary)
    public static final double TEMPERATE_MAX_FRAC = 50.0 / 90.0;  // 50° (canonical Temperate/Subpolar boundary)
    public static final double SUBPOLAR_MAX_FRAC = 66.5 / 90.0;   // 66.5° (canonical Subpolar/Polar boundary)

    public static final int EQUATOR_MAX_DEG = (int) Math.ceil(EQUATOR_MAX_FRAC * 90.0);
    public static final int TROPICAL_MAX_DEG = (int) Math.ceil(TROPICAL_MAX_FRAC * 90.0);
    public static final int SUBTROPICAL_MAX_DEG = (int) Math.ceil(SUBTROPICAL_MAX_FRAC * 90.0);
    public static final int TEMPERATE_MAX_DEG = (int) Math.ceil(TEMPERATE_MAX_FRAC * 90.0);
    public static final int SUBPOLAR_MAX_DEG = (int) Math.ceil(SUBPOLAR_MAX_FRAC * 90.0);

    public static final double POLAR_START_FRAC = SUBPOLAR_MAX_FRAC;
    public static final int POLAR_START_DEG = (int) Math.floor(POLAR_START_FRAC * 90.0);

    // Polar WARNING-ladder thresholds (progress = |z|/latitudeRadius = deg/90). Re-anchored (B-3-P3)
    // to P1's PolarHazardWindow milestones so each warning fires at or just before the mechanic it warns
    // about: snow onset 85, blizzard visuals build from 87, hazard/slowness onset 88.5 (moved poleward
    // 2026-07-12; the ladder stays put -- shared with the EW storm axis -- so stage 2 is now a pure
    // "worsening" warning ~1.5 deg ahead of the first mechanic), blindness ~89, freeze near-max ~89.7.
    public static final double POLAR_STAGE_1_PROGRESS = 0.9444;      // 85.0 deg -- snow onset (ambient window opens)
    public static final double POLAR_STAGE_2_PROGRESS = 0.9667;      // 87.0 deg -- blizzard-worsening warning (mechanics start 88.5)
    public static final double POLAR_STAGE_3_PROGRESS = 0.9889;      // 89.0 deg -- ~blindness; ~1 deg lead before freeze damage
    public static final double POLAR_STAGE_LETHAL_PROGRESS = 0.9967; // 89.7 deg -- freeze near-max (death at 90.0)

    /** WorldBorder#getSize() is DIAMETER. Half-size is radius in blocks. This is the X (border) radius. */
    public static double halfSize(WorldBorder border) {
        if (border == null) return 1.0;
        double size = border.getSize();
        if (!(size > 0.0)) return 1.0;
        return size * 0.5;
    }

    // --- Mercator latitude radius override (Phase 1) ---
    // In a Mercator world the square WorldBorder is sized to the WIDER (X) axis, so halfSize(border) is the
    // X radius, NOT the latitude (Z) radius. Latitude/pole math must divide Z by the Z radius. Both server
    // (GlobeMod.setGlobeBorder) and client (GlobeStatePayload handler) push the Z radius here. 0 = use the
    // border half-size (Classic: Z radius == border half, so latitudeRadius == halfSize → byte-identical).
    private static volatile int latitudeZRadiusOverride = 0;

    public static void setLatitudeZRadius(int zRadius) {
        latitudeZRadiusOverride = Math.max(0, zRadius);
    }

    public static int getLatitudeZRadiusOverride() {
        return latitudeZRadiusOverride;
    }

    /** The latitude (Z) radius: the override if set (Mercator), else the border half-size (Classic). */
    public static double latitudeRadius(WorldBorder border) {
        int o = latitudeZRadiusOverride;
        return o > 0 ? o : halfSize(border);
    }

    /** Returns normalized latitude in [-1..1] from Z using the latitude (Z) radius. */
    public static double latNormFromZ(WorldBorder border, double z) {
        double half = latitudeRadius(border);
        double norm = z / half;
        return Mth.clamp(norm, -1.0, 1.0);
    }

    /** Returns degrees latitude in [-90..90]. */
    public static double degreesFromZ(WorldBorder border, double z) {
        return latNormFromZ(border, z) * 90.0;
    }

    public static double worldRadiusBlocks(WorldBorder border) {
        return halfSize(border);
    }

    public static double absLatFraction(WorldBorder border, double z) {
        return Math.abs(latNormFromZ(border, z));
    }

    public static double absLatDegExact(WorldBorder border, double z) {
        return absLatFraction(border, z) * 90.0;
    }

    /** Returns remaining distance to the N/S pole in blocks (>= 0), measured against the latitude radius. */
    public static double poleRemainingBlocks(WorldBorder border, double z) {
        double half = latitudeRadius(border);
        double remaining = half - Math.abs(z);
        return Math.max(0.0, remaining);
    }

    /** Returns remaining distance to the N/S pole as a fraction of the latitude radius. */
    public static double poleRemainingFrac(WorldBorder border, double z) {
        double half = latitudeRadius(border);
        if (half <= 0.0) return 0.0;
        return poleRemainingBlocks(border, z) / half;
    }

    /** Returns normalized progress to the X (E-W) border in [0..1]. Use for east-west storm hazards. */
    public static double hazardProgress(WorldBorder border, double coord) {
        double half = halfSize(border);
        if (half <= 0.0) return 1.0;
        return Mth.clamp(Math.abs(coord) / half, 0.0, 1.0);
    }

    /** Returns normalized progress to the N/S pole in [0..1], measured against the latitude (Z) radius.
     *  Use for pole hazards: in Mercator the pole is interior to the (X-sized) border, so this differs
     *  from {@link #hazardProgress} which would only reach 1.0 at the far X border. */
    public static double hazardProgressZ(WorldBorder border, double z) {
        double half = latitudeRadius(border);
        if (half <= 0.0) return 1.0;
        return Mth.clamp(Math.abs(z) / half, 0.0, 1.0);
    }

    /** Returns hazard stage index (0..4) based on normalized progress. */
    public static int hazardStageIndex(WorldBorder border, double z, double progress) {
        // TODO (Hard Mode): allow hazards to start at POLAR entry (zone-gated), not just near border.

        if (progress >= POLAR_STAGE_LETHAL_PROGRESS) return 4;
        if (progress >= POLAR_STAGE_3_PROGRESS) return 3;
        if (progress >= POLAR_STAGE_2_PROGRESS) return 2;
        if (progress >= POLAR_STAGE_1_PROGRESS) return 1;
        return 0;
    }

    /** Returns hazard stage index for X-axis (EW) storms. */
    public static int hazardStageIndexEW(double progress) {
        if (progress >= POLAR_STAGE_LETHAL_PROGRESS) return 4;
        if (progress >= POLAR_STAGE_3_PROGRESS) return 3;
        if (progress >= POLAR_STAGE_2_PROGRESS) return 2;
        if (progress >= POLAR_STAGE_1_PROGRESS) return 1;
        return 0;
    }

    public static int latitudeDegrees(WorldBorder border, double z) {
        int deg = (int) Math.round(Math.abs(degreesFromZ(border, z)));
        return Mth.clamp(deg, 0, 90);
    }

    /** Returns |z| in blocks for a target absolute latitude degree [0..90]. */
    public static int zForLatitudeDeg(double deg, int radiusBlocks) {
        if (radiusBlocks <= 0) return 0;
        double clampedDeg = Mth.clamp(Math.abs(deg), 0.0, 90.0);
        double t = clampedDeg / 90.0;
        int z = (int) Math.round(t * radiusBlocks);
        return Mth.clamp(z, 0, radiusBlocks);
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

    // --- Longitude (2.0 "Longitude" release) ---
    // West = negative X, East = positive X (matches vanilla F3's "Towards negative X (West)" convention and
    // the existing /latdev tpedge west/east mapping). Measured against halfSize(border) \u2014 the X/border
    // radius \u2014 which is ALREADY the correct radius on both Classic (== Z radius) and Mercator (== 2x Z
    // radius) worlds, so this needs no shape branching. 0 at the world's center X, 180 at the E/W border.
    public static char hemisphereEW(WorldBorder border, double x) {
        double centerX = border != null ? border.getCenterX() : 0.0;
        return x < centerX ? 'W' : 'E';
    }

    public static int longitudeDegrees(WorldBorder border, double x) {
        double half = halfSize(border);
        if (half <= 0.0) return 0;
        double norm = Mth.clamp(Math.abs(x) / half, 0.0, 1.0);
        int deg = (int) Math.round(norm * 180.0);
        return Mth.clamp(deg, 0, 180);
    }

    public static String formatLongitudeDeg(WorldBorder border, double x) {
        int deg = longitudeDegrees(border, x);
        if (deg == 0) return "0\u00b0";
        char hemi = hemisphereEW(border, x);
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

    public static LatitudeZone zoneForRadius(int radiusBlocks, double z) {
        if (radiusBlocks <= 0) return LatitudeZone.EQUATOR;
        double t = Math.abs(z) / (double) radiusBlocks;
        t = Mth.clamp(t, 0.0, 1.0);
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
            case "EQUATOR" -> 0.05;
            case "TROPICAL" -> 0.20;
            case "SUBTROPICAL" -> 0.40;
            case "TEMPERATE" -> 0.472;
            case "SUBPOLAR" -> 0.725;
            case "POLAR" -> 0.89;
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

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double hash01(long seed, int x, int z, int salt) {
        long h = seed ^ ((long) x * 312289L) ^ ((long) z * 420559L) ^ (long) salt;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        h = h ^ (h >>> 33);
        return (h & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
    }
}
