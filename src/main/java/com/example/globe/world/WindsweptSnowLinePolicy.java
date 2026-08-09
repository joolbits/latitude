package com.example.globe.world;

import java.util.Set;

/**
 * Latitude's lowered snow line for the windswept family — the pure decision behind the maintainer's
 * grey-grass report, shared by the two snow write paths so they can never disagree again.
 *
 * <p>The facts, measured from stored chunks (binder:
 * {@code latitude-1-5-port-1p21p11-windswept-snow-line-20260807.md}): vanilla snows a
 * temperature-0.2 biome only above roughly {@code seaLevel + 57} (y≈120), and vanilla's own
 * windswept terrain mostly clears that line — which is why windswept reads as snow-dusted there.
 * Latitude paints the same biomes across ordinary temperate uplands from y≈50 up, so the majority
 * of a Latitude windswept surface sits below the vanilla line and generates as bare desaturated
 * grass: technically vanilla-correct, visually broken.
 *
 * <p>The remedy is a windswept-only snow line at {@link #SNOW_LINE_OFFSET_ABOVE_SEA} blocks above
 * sea level (y=90 at vanilla sea level 63) — low enough to carpet the upland bulk of the biome
 * (measured surface concentration y80–111), high enough to leave its valley fringes grassy so the
 * transition into neighboring temperate biomes stays gradual. Deliberately excludes
 * {@code windswept_savanna}, which is a warm biome that should never snow.
 *
 * <p>Both consumers must apply this identically: {@code SnowAndFreezeWindsweptSnowLineMixin}
 * (places the carpet during {@code freeze_top_layer}) and {@code ProtoChunkSnowBlockGuardMixin}
 * (must not strip what the former places). A one-sided change recreates the incoherent
 * snowy-grass-without-carpet state this family of bugs started with.
 */
public final class WindsweptSnowLinePolicy {

    /** Cold-capable windswept biomes; windswept_savanna is deliberately absent. */
    public static final Set<String> WINDSWEPT_SNOW_BIOMES = Set.of(
            "minecraft:windswept_hills",
            "minecraft:windswept_forest",
            "minecraft:windswept_gravelly_hills");

    /** Vanilla's effective line for these biomes is ~seaLevel + 57; Latitude lowers it to +27. */
    public static final int SNOW_LINE_OFFSET_ABOVE_SEA = 27;

    private WindsweptSnowLinePolicy() {
    }

    /** True when Latitude's lowered windswept snow line covers this biome at this height. */
    public static boolean appliesTo(String biomeId, int blockY, int seaLevel) {
        return biomeId != null
                && WINDSWEPT_SNOW_BIOMES.contains(biomeId)
                && blockY >= seaLevel + SNOW_LINE_OFFSET_ABOVE_SEA;
    }
}
