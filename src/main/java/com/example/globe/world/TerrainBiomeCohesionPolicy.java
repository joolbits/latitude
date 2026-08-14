package com.example.globe.world;

/**
 * Pure terrain/biome cohesion rules shared by both Latitude biome-picker paths.
 *
 * <p>The thresholds describe visibly raised terrain, not ordinary rolling ground:
 * six blocks of robust local relief is already a steep biome-scale shoulder, while
 * forty blocks above sea level is safely beyond a coastal or lowland shelf. A river
 * is only rejected when real terrain evidence, that high elevation, and the climate
 * sampler's mountain signal all agree; this keeps ordinary rivers and upland valleys.
 */
public final class TerrainBiomeCohesionPolicy {
    static final int RUGGED_RELIEF_BLOCKS =
            Integer.getInteger("latitude.temperatePlainsRelief", 6);
    static final int HIGH_ABOVE_SEA_BLOCKS =
            Integer.getInteger("latitude.temperatePlainsHighAboveSea", 40);

    private TerrainBiomeCohesionPolicy() {
    }

    static boolean shouldApplyLandGate(
            boolean temperateBand,
            boolean colderBand,
            boolean terrainEvidenceAvailable,
            int surfaceY,
            int robustRelief,
            int seaLevel) {
        if (colderBand) {
            return true;
        }
        if (!temperateBand || !terrainEvidenceAvailable) {
            return false;
        }
        return robustRelief >= RUGGED_RELIEF_BLOCKS
                || surfaceY >= seaLevel + HIGH_ABOVE_SEA_BLOCKS;
    }

    /**
     * Warm bands read the same terrain evidence as temperate. A flat-family biome draped over a
     * measured ridge looks wrong at any latitude, and the subtropical band was previously exempt
     * only because the gate was written for the temperate rework — not because warm ridges are
     * meant to be flat.
     */
    static boolean shouldUseWarmUplandFamily(
            boolean terrainEvidenceAvailable,
            int surfaceY,
            int robustRelief,
            int seaLevel) {
        return isPhysicalUpland(
                terrainEvidenceAvailable,
                surfaceY,
                robustRelief,
                seaLevel);
    }

    /**
     * Final physical terrain class shared by warm and temperate selection. This deliberately uses
     * measured terrain only; biome namespace, name fragments, and provider presence are not terrain
     * evidence.
     */
    static boolean isPhysicalUpland(
            boolean terrainEvidenceAvailable,
            int surfaceY,
            int robustRelief,
            int seaLevel) {
        return shouldApplyLandGate(
                true,
                false,
                terrainEvidenceAvailable,
                surfaceY,
                robustRelief,
                seaLevel);
    }

    /**
     * High or genuinely rugged temperate terrain must use the dedicated upland pool. The old
     * compatibility reroll could select another lowland-family biome (notably sunflower plains or
     * flower forest), which made a real terrain gate visually inert.
     */
    static boolean shouldUseTemperateUplandFamily(
            boolean terrainEvidenceAvailable,
            int surfaceY,
            int robustRelief,
            int seaLevel) {
        return shouldApplyLandGate(
                true,
                false,
                terrainEvidenceAvailable,
                surfaceY,
                robustRelief,
                seaLevel);
    }

    /**
     * Final-return safeguard for the physical terrain authority. Intermediate climate and provider
     * compatibility passes may legitimately rewrite a candidate, but they must not turn a measured
     * high/rugged temperate column back into a lowland family.
     */
    static boolean shouldEnforceFinalTemperateUpland(
            boolean forceTemperateUpland,
            boolean finalBiomeInTemperateMountainTag) {
        return forceTemperateUpland && !finalBiomeInTemperateMountainTag;
    }

    /**
     * Arid identities must agree with the final physical terrain class declared by their ledger
     * routes. A descriptorless biome is outside this rule: admission remains the provider-ticket
     * ledger's responsibility, and this policy must not promote it merely because its name sounds
     * arid.
     */
    static boolean isAridBiomeCompatibleWithTerrain(
            boolean physicalUpland,
            boolean ownsAridLowlandRoute,
            boolean ownsAridUplandRoute) {
        if (!ownsAridLowlandRoute && !ownsAridUplandRoute) {
            return true;
        }
        return physicalUpland ? ownsAridUplandRoute : ownsAridLowlandRoute;
    }

    static boolean shouldReplaceRiverWithLand(
            boolean terrainEvidenceAvailable,
            int surfaceY,
            int seaLevel,
            boolean mountainNoiseLike) {
        return terrainEvidenceAvailable
                && mountainNoiseLike
                && surfaceY >= seaLevel + HIGH_ABOVE_SEA_BLOCKS;
    }
}
