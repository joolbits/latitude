package com.example.globe.world;

/**
 * The final geography routes that may enter the provider-ticket lottery.
 *
 * <p>These are deliberately geography terms rather than source-tag names.  Tags are an
 * implementation detail and must never be allowed to add an undescribed optional biome to a
 * route.  Ocean, river, Pale Garden, mangrove and late terrain clamps remain hard authorities
 * outside this enum's lottery.</p>
 */
public enum BiomeRoute {
    TROPICAL_HUMID_LOWLAND,
    SUBTROPICAL_HUMID_LOWLAND,
    TEMPERATE_LOWLAND,
    TEMPERATE_WETLAND,
    TEMPERATE_UPLAND,
    COLD_UPLAND,
    WARM_TRANSITION,
    WARM_UPLAND,
    ARID_LOWLAND,
    ARID_UPLAND,
    /**
     * Cold wetland — bogs and frozen marshes at 50-66.5 degrees.
     *
     * <p>Until this existed, {@code TEMPERATE_WETLAND} was the ONLY wetland route and the ledger
     * invariant required every wetland-terrain descriptor to own it, so a cold wetland could not be
     * authored anywhere: the constructor threw. That is why {@code biomesoplenty:muskeg}
     * (temperature 0.0 — below the 0.15 snow threshold at every altitude, so it snows permanently)
     * and {@code terralith:ice_marsh} (0.14, frozen modifier) sat at 35-50 degrees in the same pool
     * as {@code minecraft:swamp} at 0.8. The two defects were the mechanical output of the type
     * system, not authoring slips.
     *
     * <p>Vanilla ships no cold wetland, so on a vanilla-only world this route's pool is simply
     * empty — the band falls through to its other routes exactly as before.
     */
    SUBPOLAR_WETLAND,
    SUBPOLAR_LOWLAND,
    POLAR_LOWLAND,
    /** Underground cave climate selected only after the donor source has identified a real cave cell. */
    CAVE_SHALLOW,
    /** Deep underground cave climate, including the Deep Dark hard authority. */
    CAVE_DEEP
}
