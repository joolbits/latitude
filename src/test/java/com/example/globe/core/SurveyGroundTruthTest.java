package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM table for {@link SurveyGroundTruth} (S25 addendum -- the /latdev survey "over open water" lie at
 * 78N on solid ground). The gatherer feeds the narrator the REALIZED where-you-are fact and drops the
 * intent-derived distance line on divergence; these tables pin both decisions.
 */
class SurveyGroundTruthTest {

    @Test
    void realizedOceanTable() {
        // (surfaceIsWater, biomeIsOceanFamily) -> realized "over water". Either alone suffices:
        assertFalse(SurveyGroundTruth.realizedOcean(false, false),
                "solid ground, land biome: the owner's snowy plains column -- LAND, whatever intent says");
        assertTrue(SurveyGroundTruth.realizedOcean(true, false),
                "water surface on a non-ocean biome (inland realized lake): over water underfoot");
        assertTrue(SurveyGroundTruth.realizedOcean(false, true),
                "ocean-family biome with a non-water surface block (frozen sea ice): still the sea");
        assertTrue(SurveyGroundTruth.realizedOcean(true, true), "open realized sea");
    }

    @Test
    void dropCoastDistanceLineOnAnyDivergence() {
        // (intentOcean, realizedOcean) -> drop the traveler's-note distance line (it is computed from the
        // intent coast field, so it only means something when intent and realized agree).
        assertTrue(SurveyGroundTruth.dropCoastDistanceLine(true, false),
                "the owner's screenshot: intent-ocean over realized land -> '576 blocks to land' dropped");
        assertTrue(SurveyGroundTruth.dropCoastDistanceLine(false, true),
                "mirror divergence: realized water over intent-land -> same intent field, opposite meaning");
        assertFalse(SurveyGroundTruth.dropCoastDistanceLine(false, false),
                "agreement on land: the coast-distance line stands");
        assertFalse(SurveyGroundTruth.dropCoastDistanceLine(true, true),
                "agreement on ocean: the nearest-land line stands");
    }
}
