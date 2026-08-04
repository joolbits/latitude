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
    SUBPOLAR_LOWLAND,
    POLAR_LOWLAND,
    /** Underground cave climate selected only after the donor source has identified a real cave cell. */
    CAVE_SHALLOW,
    /** Deep underground cave climate, including the Deep Dark hard authority. */
    CAVE_DEEP
}
