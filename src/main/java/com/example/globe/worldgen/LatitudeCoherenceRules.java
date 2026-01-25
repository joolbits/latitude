package com.example.globe.worldgen;

public final class LatitudeCoherenceRules {
    private LatitudeCoherenceRules() {
    }

    public static final boolean ENABLE_ARID_RIVER_MASKING = true;
    public static final boolean ENABLE_BADLANDS_MIN_REGION = true;

    public static final int ERODED_BADLANDS_MIN_MATCH = 9;
    public static final int WOODED_BADLANDS_MIN_MATCH = 7;

    public static boolean isRiver(String biomeId) {
        return "minecraft:river".equals(biomeId) || "minecraft:frozen_river".equals(biomeId);
    }

    public static boolean isBadlandsFamily(String biomeId) {
        return "minecraft:badlands".equals(biomeId)
                || "minecraft:wooded_badlands".equals(biomeId)
                || "minecraft:eroded_badlands".equals(biomeId);
    }

    public static boolean isFeatureHeavyBadlands(String biomeId) {
        return "minecraft:wooded_badlands".equals(biomeId)
                || "minecraft:eroded_badlands".equals(biomeId);
    }

    public static boolean isAridHost(String hostBiomeId) {
        if (hostBiomeId == null) return false;
        return hostBiomeId.contains("desert")
                || hostBiomeId.contains("savanna")
                || hostBiomeId.contains("badlands");
    }

    public static String maskRiverInArid(String candidateBiomeId, String hostBiomeId) {
        if (!ENABLE_ARID_RIVER_MASKING) return candidateBiomeId;
        if (!isRiver(candidateBiomeId)) return candidateBiomeId;
        if (!isAridHost(hostBiomeId)) return candidateBiomeId;

        if (hostBiomeId != null && hostBiomeId.contains("badlands")) {
            return "minecraft:badlands";
        }
        if (hostBiomeId != null && hostBiomeId.contains("savanna")) {
            return "minecraft:savanna";
        }
        return "minecraft:desert";
    }

    public static String gateBadlandsByMinRegion(String candidateBiomeId, int badlandsFamilyMatches) {
        if (!ENABLE_BADLANDS_MIN_REGION) return candidateBiomeId;
        if (!isFeatureHeavyBadlands(candidateBiomeId)) return candidateBiomeId;

        int min = "minecraft:eroded_badlands".equals(candidateBiomeId)
                ? ERODED_BADLANDS_MIN_MATCH
                : WOODED_BADLANDS_MIN_MATCH;

        if (badlandsFamilyMatches < min) {
            return "minecraft:badlands";
        }
        return candidateBiomeId;
    }
}
