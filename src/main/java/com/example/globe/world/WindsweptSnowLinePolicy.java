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

    /** Latitude's fully-lowered line, applied at subpolar and poleward. */
    public static final int SNOW_LINE_OFFSET_ABOVE_SEA = 27;

    /** Vanilla's own effective line for a temperature-0.2 biome (~y120 at sea level 63). */
    public static final int VANILLA_SNOW_LINE_OFFSET_ABOVE_SEA = 57;

    /** Poleward of this the line is fully lowered; matches the Subpolar band edge. */
    public static final double FULLY_LOWERED_AT_LATITUDE_DEG = 50.0;

    /** Equatorward of this the line stays at vanilla's; matches the Temperate band edge. */
    public static final double VANILLA_LINE_UNTIL_LATITUDE_DEG = 35.0;

    private WindsweptSnowLinePolicy() {
    }

    /** Shared latitude derivation, so every consumer asks the question the same way. */
    public static double absoluteLatitudeDegrees(
            double blockZ, int activeRadiusBlocks, int borderRadiusFallback) {
        int radius = activeRadiusBlocks > 0 ? activeRadiusBlocks : borderRadiusFallback;
        return Math.min(90.0, Math.abs(blockZ) * 90.0 / Math.max(1, radius));
    }

    /**
     * The snow line descends toward the poles, as Earth's does.
     *
     * <p>A single flat offset was the root of BOTH reported defects. At {@code +27} everywhere it
     * carpeted temperate windswept in snow that does not belong at 35-50 degrees (maintainer,
     * 2026-08-10). Reverting to vanilla's {@code +57} everywhere brings back the original
     * complaint: Latitude paints windswept across temperate uplands from y50 up, so most of the
     * surface sat below the line as bare desaturated grass with no snow to explain the cold tint.
     *
     * <p>Ramping between the two by latitude resolves both, and is coherent by construction —
     * latitude varies slowly and smoothly across the map, so a whole mountainside lands on one
     * side of the line rather than breaking into the patchy "grey grass with snow edges" a hard
     * cutoff produced.
     */
    public static int snowLineOffsetForLatitude(double absLatDeg) {
        if (absLatDeg >= FULLY_LOWERED_AT_LATITUDE_DEG) {
            return SNOW_LINE_OFFSET_ABOVE_SEA;
        }
        if (absLatDeg <= VANILLA_LINE_UNTIL_LATITUDE_DEG) {
            return VANILLA_SNOW_LINE_OFFSET_ABOVE_SEA;
        }
        double t = (absLatDeg - VANILLA_LINE_UNTIL_LATITUDE_DEG)
                / (FULLY_LOWERED_AT_LATITUDE_DEG - VANILLA_LINE_UNTIL_LATITUDE_DEG);
        double smooth = t * t * (3.0 - 2.0 * t);
        return (int) Math.round(VANILLA_SNOW_LINE_OFFSET_ABOVE_SEA
                + (SNOW_LINE_OFFSET_ABOVE_SEA - VANILLA_SNOW_LINE_OFFSET_ABOVE_SEA) * smooth);
    }

    /**
     * True when Latitude's windswept snow line covers this biome at this height and latitude.
     *
     * <p>Every consumer must call this exact method. There are THREE — the placer
     * ({@code SnowAndFreezeWindsweptSnowLineMixin}) and two guards that must not strip what it
     * places ({@code ProtoChunkSnowBlockGuardMixin}, {@code ChunkRegionWarmSnowTrapMixin}). A
     * one-sided change recreates the incoherent snowy-grass-without-carpet state this family of
     * bugs started with. (The class javadoc said "both consumers" and undercounted; corrected
     * 2026-08-10.)
     */
    public static boolean appliesTo(String biomeId, int blockY, int seaLevel, double absLatDeg) {
        return biomeId != null
                && WINDSWEPT_SNOW_BIOMES.contains(biomeId)
                && blockY >= seaLevel + snowLineOffsetForLatitude(absLatDeg);
    }
}
