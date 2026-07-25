package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link FrostMoteLaw} (S25 SLUSH v1, client half -- frost motes over glacial-caves water,
 * owner TEST 117). Pins: the biome gate (globe:glacial_caves only), the water-surface predicate as a pure
 * table (minecraft:water only, never the ice floes), the budget bounds (flag AND biome required; sparse
 * default; clamped >= 0), the constant alignment (the mirrored biome id tracks the world-layer constant), and
 * the placement-reach constants being sane.
 */
class FrostMoteLawTest {

    // ---- biome gate -----------------------------------------------------------------------------

    @Test
    void biomeGateAcceptsOnlyGlacialCaves() {
        assertTrue(FrostMoteLaw.isGlacialCavesBiome("globe:glacial_caves"));
        assertFalse(FrostMoteLaw.isGlacialCavesBiome("globe:polar_barrens"));
        assertFalse(FrostMoteLaw.isGlacialCavesBiome("minecraft:frozen_ocean"));
        assertFalse(FrostMoteLaw.isGlacialCavesBiome(null), "null biome id -> no motes (conservative)");
        assertFalse(FrostMoteLaw.isGlacialCavesBiome(""));
    }

    @Test
    void mirroredBiomeIdTracksTheWorldLayerConstant() {
        // The pure core mirrors the world-layer id (LatitudeBiomes is not importable from core; alignment is
        // pinned here where the test classpath can load it) -- the same discipline SnowSparkleLaw uses.
        assertEquals(com.example.globe.world.LatitudeBiomes.GLACIAL_CAVES_ID, FrostMoteLaw.TARGET_BIOME_ID,
                "FrostMoteLaw.TARGET_BIOME_ID == LatitudeBiomes.GLACIAL_CAVES_ID");
    }

    // ---- water-surface predicate (pure table) ---------------------------------------------------

    @Test
    void waterPredicateAcceptsOnlyWaterNeverIceFloes() {
        assertTrue(FrostMoteLaw.isWaterMoteBlock("minecraft:water"), "cave-pool source block glints motes");
        // Crew 1's ice floes are ICE blocks -- deliberately NOT mote surfaces (motes drift over the water).
        assertFalse(FrostMoteLaw.isWaterMoteBlock("minecraft:ice"));
        assertFalse(FrostMoteLaw.isWaterMoteBlock("minecraft:packed_ice"));
        assertFalse(FrostMoteLaw.isWaterMoteBlock("minecraft:blue_ice"));
        assertFalse(FrostMoteLaw.isWaterMoteBlock("minecraft:stone"));
        assertFalse(FrostMoteLaw.isWaterMoteBlock("minecraft:lava"), "never lava");
        assertFalse(FrostMoteLaw.isWaterMoteBlock(null), "null -> no mote");
    }

    // ---- budget bounds --------------------------------------------------------------------------

    @Test
    void budgetRequiresBothFlagAndBiome() {
        int peak = FrostMoteLaw.DEFAULT_PEAK_BUDGET;
        assertEquals(peak, FrostMoteLaw.moteBudget(true, true, peak), "flag ON + in caves -> full sparse peak");
        assertEquals(0, FrostMoteLaw.moteBudget(false, true, peak), "flag OFF -> no motes");
        assertEquals(0, FrostMoteLaw.moteBudget(true, false, peak), "outside glacial caves -> no motes");
        assertEquals(0, FrostMoteLaw.moteBudget(false, false, peak), "neither -> no motes");
    }

    @Test
    void budgetIsSparseAndNeverNegative() {
        assertTrue(FrostMoteLaw.DEFAULT_PEAK_BUDGET > 0, "there is a default budget");
        assertTrue(FrostMoteLaw.DEFAULT_PEAK_BUDGET <= 8, "SPARSE -- atmosphere, not weather");
        assertEquals(0, FrostMoteLaw.moteBudget(true, true, 0), "zero peak -> zero");
        assertEquals(0, FrostMoteLaw.moteBudget(true, true, -5), "negative peak clamps to zero");
        assertEquals(1, FrostMoteLaw.moteBudget(true, true, 1), "a peak passes through when gated on");
    }

    // ---- placement-reach constants are sane -----------------------------------------------------

    @Test
    void placementReachConstantsAreSane() {
        assertTrue(FrostMoteLaw.SAMPLE_RADIUS > 0 && FrostMoteLaw.SAMPLE_RADIUS <= 16.0,
                "within ~12 blocks of the player");
        assertTrue(FrostMoteLaw.SAMPLE_Y_BELOW >= 0 && FrostMoteLaw.SAMPLE_Y_ABOVE >= 0,
                "non-negative vertical scan span");
        assertTrue(FrostMoteLaw.MOTE_Y_MIN >= 0.0 && FrostMoteLaw.MOTE_Y_MAX > FrostMoteLaw.MOTE_Y_MIN
                        && FrostMoteLaw.MOTE_Y_MAX <= 1.0,
                "motes hug the pool surface (a shallow skim band)");
        assertTrue(FrostMoteLaw.MOTE_DRIFT_LATERAL >= 0.0 && FrostMoteLaw.MOTE_DRIFT_LATERAL <= 0.1,
                "a faint lateral drift, not a gust");
    }
}
