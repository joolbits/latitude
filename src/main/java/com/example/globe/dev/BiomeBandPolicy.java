package com.example.globe.dev;

import com.example.globe.util.LatitudeBands;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BiomeBandPolicy {
    private static final Map<String, List<LatitudeBands.Band>> POLICY = Map.ofEntries(
            exempt("minecraft:ocean"),
            exempt("minecraft:deep_ocean"),
            exempt("minecraft:cold_ocean"),
            exempt("minecraft:deep_cold_ocean"),
            exempt("minecraft:lukewarm_ocean"),
            exempt("minecraft:deep_lukewarm_ocean"),
            exempt("minecraft:warm_ocean"),
            exempt("minecraft:deep_frozen_ocean"),
            exempt("minecraft:frozen_ocean"),
            exempt("minecraft:river"),
            exempt("minecraft:frozen_river"),
            exempt("minecraft:beach"),
            exempt("minecraft:snowy_beach"),
            exempt("minecraft:stony_shore"),
            exempt("minecraft:mushroom_fields"),

            entry("minecraft:jungle", LatitudeBands.Band.TROPICAL),
            entry("minecraft:sparse_jungle", LatitudeBands.Band.TROPICAL),
            entry("minecraft:bamboo_jungle", LatitudeBands.Band.TROPICAL),
            entry("minecraft:mangrove_swamp", LatitudeBands.Band.TROPICAL),
            entry("minecraft:savanna", LatitudeBands.Band.TROPICAL),
            entry("minecraft:savanna_plateau", LatitudeBands.Band.TROPICAL),
            entry("minecraft:windswept_savanna", LatitudeBands.Band.TROPICAL),
            entry("minecraft:desert", LatitudeBands.Band.TROPICAL),
            entry("minecraft:badlands", LatitudeBands.Band.TROPICAL),
            entry("minecraft:wooded_badlands", LatitudeBands.Band.TROPICAL),
            entry("minecraft:eroded_badlands", LatitudeBands.Band.TROPICAL),

            entry("minecraft:swamp", LatitudeBands.Band.TROPICAL, LatitudeBands.Band.TEMPERATE),

            entry("minecraft:plains", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:sunflower_plains", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:forest", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:flower_forest", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:birch_forest", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:old_growth_birch_forest", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:dark_forest", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:pale_garden", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:meadow", LatitudeBands.Band.TEMPERATE),
            entry("minecraft:cherry_grove", LatitudeBands.Band.TEMPERATE),

            entry("minecraft:taiga", LatitudeBands.Band.TEMPERATE, LatitudeBands.Band.SUBPOLAR),
            entry("minecraft:old_growth_pine_taiga", LatitudeBands.Band.TEMPERATE, LatitudeBands.Band.SUBPOLAR),
            entry("minecraft:old_growth_spruce_taiga", LatitudeBands.Band.TEMPERATE, LatitudeBands.Band.SUBPOLAR),
            entry("minecraft:windswept_hills", LatitudeBands.Band.TEMPERATE, LatitudeBands.Band.SUBPOLAR),
            entry("minecraft:windswept_forest", LatitudeBands.Band.TEMPERATE, LatitudeBands.Band.SUBPOLAR),
            entry("minecraft:windswept_gravelly_hills", LatitudeBands.Band.TEMPERATE, LatitudeBands.Band.SUBPOLAR),
            entry("minecraft:stony_peaks", LatitudeBands.Band.TEMPERATE, LatitudeBands.Band.SUBPOLAR),

            entry("minecraft:snowy_plains", LatitudeBands.Band.SUBPOLAR, LatitudeBands.Band.POLAR),
            entry("minecraft:snowy_taiga", LatitudeBands.Band.SUBPOLAR, LatitudeBands.Band.POLAR),
            entry("minecraft:grove", LatitudeBands.Band.SUBPOLAR, LatitudeBands.Band.POLAR),
            entry("minecraft:snowy_slopes", LatitudeBands.Band.SUBPOLAR, LatitudeBands.Band.POLAR),
            entry("minecraft:jagged_peaks", LatitudeBands.Band.SUBPOLAR, LatitudeBands.Band.POLAR),
            entry("minecraft:frozen_peaks", LatitudeBands.Band.SUBPOLAR, LatitudeBands.Band.POLAR),
            entry("minecraft:ice_spikes", LatitudeBands.Band.SUBPOLAR, LatitudeBands.Band.POLAR)
    );

    private BiomeBandPolicy() {
    }

    static List<String> canonicalBandIdsFor(String biomeId) {
        String normalized = normalizeBiomeId(biomeId);
        if (normalized == null) {
            return List.of();
        }
        List<LatitudeBands.Band> bands = POLICY.get(normalized);
        if (bands == null || bands.isEmpty()) {
            return List.of();
        }
        return bands.stream().map(LatitudeBands.Band::id).toList();
    }

    static Map<String, List<LatitudeBands.Band>> policy() {
        return POLICY;
    }

    private static String normalizeBiomeId(String biomeId) {
        if (biomeId == null) {
            return null;
        }
        String normalized = biomeId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private static Map.Entry<String, List<LatitudeBands.Band>> exempt(String biomeId) {
        return Map.entry(biomeId, List.of());
    }

    private static Map.Entry<String, List<LatitudeBands.Band>> entry(String biomeId, LatitudeBands.Band... bands) {
        return Map.entry(biomeId, List.of(bands));
    }
}
