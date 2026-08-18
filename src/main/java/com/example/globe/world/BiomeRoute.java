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
    /**
     * Vegetated cold mountains — the windswept family at 50-66.5 degrees, mountain columns only.
     *
     * <p>Split out of {@code COLD_UPLAND} on 2026-08-18 (maintainer ruling). COLD_UPLAND spans
     * subpolar AND polar, and its "mountain" half was enforced nowhere downstream: the
     * terrain-compatibility reroll drew from the raw band pool, so on a vanilla-only world
     * {@code minecraft:windswept_hills} became the second most common land biome at the pole —
     * green grass, flowers and passive animals at 80 degrees north, with no snow. The pole must
     * read white, so polar mountains keep the bare alpine set (snowy_slopes, frozen_peaks,
     * jagged_peaks) and the vegetated wind-scoured identity lives only here, in subpolar mountains,
     * where {@link WindsweptSnowLinePolicy} already lowers its snow line.
     *
     * <p>A route rather than an extra condition on COLD_UPLAND because the band pool, the vanilla
     * coverage plan and the ledger all key off routes: giving the windswept family its own route
     * makes "not legal at the pole" true by construction in all three at once, instead of three
     * places that have to remember to agree.
     */
    SUBPOLAR_UPLAND,
    SUBPOLAR_LOWLAND,
    POLAR_LOWLAND,
    /** Underground cave climate selected only after the donor source has identified a real cave cell. */
    CAVE_SHALLOW,
    /** Deep underground cave climate, including the Deep Dark hard authority. */
    CAVE_DEEP
}
