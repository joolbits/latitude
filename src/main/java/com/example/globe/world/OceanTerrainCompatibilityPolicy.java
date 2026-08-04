package com.example.globe.world;

/**
 * Keeps donor ocean biomes from owning terrain that is unambiguously inland-height land.
 *
 * <p>The caller supplies an already-computed surface height. This policy deliberately does no
 * terrain sampling of its own, so applying it in biome generation adds no chunk or noise lookups.
 */
public final class OceanTerrainCompatibilityPolicy {
    private OceanTerrainCompatibilityPolicy() {
    }

    public static boolean isClearlyRaisedLand(
            boolean hasTerrainInputs,
            int terrainHeight,
            int seaLevel,
            int maximumCoastalReliefAboveSea) {
        if (!hasTerrainInputs || maximumCoastalReliefAboveSea < 0) {
            return false;
        }
        return terrainHeight > seaLevel + maximumCoastalReliefAboveSea;
    }
}
