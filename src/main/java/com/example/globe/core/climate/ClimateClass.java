package com.example.globe.core.climate;

import java.util.List;

/**
 * Köppen-like climate taxonomy for ClimateAuthority (Phase 3). Every class names a concrete VANILLA
 * biome family FIRST (so a vanilla-only world classifies fully — see [[vanilla-first-overhaul-constraint]]);
 * custom-pack biomes are optional tag enrichment a later consumer phase layers on top. The class is
 * stored in {@link ClimateSummary#climateClass()} as {@link #name()}.
 *
 * <p>Pure Java, no Minecraft imports — Core Logic layer. The vanilla family is a list of vanilla biome
 * ids (namespace {@code minecraft:}); the eventual biome consumer resolves within the family.
 */
public enum ClimateClass {
    TROPICAL_RAINFOREST("jungle", "bamboo_jungle", "sparse_jungle"),
    TROPICAL_MONSOON("jungle", "sparse_jungle"),
    TROPICAL_SAVANNA("savanna", "savanna_plateau", "windswept_savanna"),
    SAVANNA("savanna"),
    HOT_DESERT("desert", "badlands"),
    COOL_DESERT("badlands", "eroded_badlands", "desert"),
    HUMID_SUBTROPICAL("forest", "flower_forest", "sparse_jungle"),
    MEDITERRANEAN("forest", "plains"),
    TEMPERATE_OCEANIC("forest", "birch_forest", "meadow", "dark_forest"),
    HUMID_CONTINENTAL("taiga", "forest", "plains", "old_growth_spruce_taiga"),
    BOREAL("taiga", "snowy_taiga", "old_growth_spruce_taiga"),
    COLD_STEPPE("snowy_plains", "windswept_gravelly_hills"),
    TUNDRA("snowy_plains", "snowy_slopes", "grove"),
    ICE_CAP("ice_spikes", "snowy_plains", "frozen_peaks"),
    OCEAN_WARM("warm_ocean"),
    OCEAN_LUKEWARM("lukewarm_ocean"),
    OCEAN("ocean", "cold_ocean"),
    OCEAN_FROZEN("frozen_ocean", "deep_frozen_ocean");

    private final List<String> vanillaFamily;

    ClimateClass(String... vanillaBiomes) {
        this.vanillaFamily = List.of(vanillaBiomes);
    }

    /** Vanilla biome ids (short names) this climate implies; never empty. */
    public List<String> vanillaFamily() {
        return vanillaFamily;
    }

    /**
     * Altitude/alpine biome family for a cold class, consumed by the biome consumer when a reroll
     * repaint is accepted below the 45&deg; snowy-plains acceptance line (P1-B, audit
     * {@code fable5-biome-geography-audit-20260707.md} §5). {@link #vanillaFamily()} for the cold
     * classes LEADS with FLAT-POLAR biomes ({@code snowy_plains}, {@code ice_spikes},
     * {@code windswept_gravelly_hills}) that carry no altitude meaning; painting them at 10-45&deg;
     * latitude produced the confirmed "snowy_plains in the warm band" defect. This returns the
     * ALTITUDE-family subset ({@code grove}, {@code snowy_slopes}, {@code frozen_peaks} — vanilla
     * mountain-slope biomes) so an elevated warm-band column reads as an alpine cap, never flat polar.
     * Classes with no flat-polar family member fall back to {@link #vanillaFamily()} unchanged (e.g.
     * {@code BOREAL} = taiga, itself a legitimate montane biome).
     *
     * <p>ADDITIVE: {@link #vanillaFamily()} and every other class's behavior are untouched. Vanilla-only
     * (see class doc — {@code snowy_slopes}/{@code grove}/{@code frozen_peaks} are all {@code minecraft:}
     * mountain biomes, so a vanilla-only world resolves fully); never empty.
     */
    public List<String> alpineFamily() {
        return switch (this) {
            case COLD_STEPPE, TUNDRA -> List.of("grove", "snowy_slopes");
            case ICE_CAP -> List.of("frozen_peaks", "snowy_slopes");
            default -> vanillaFamily();
        };
    }

    public boolean isOcean() {
        return this == OCEAN_WARM || this == OCEAN_LUKEWARM || this == OCEAN || this == OCEAN_FROZEN;
    }
}
