package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for the S44 {@link CaveDropTrap} law (Peetsa 2026-07-25: "traps inside caves that drop
 * the player down to a deeper layer of the cave"). Pins the walkability/shell/drop thresholds, the
 * fraction gate's half-open interval, and the one-floor-panel patch join rule.
 */
class CaveDropTrapTest {

    @Test
    void cellNeedsWalkableGalleryThinShellAndRealDrop() {
        assertTrue(CaveDropTrap.cellQualifies(2, 1, 8), "the minimal legal cell");
        assertTrue(CaveDropTrap.cellQualifies(10, 3, 30), "roomy gallery, max shell, deep drop");
        assertFalse(CaveDropTrap.cellQualifies(1, 1, 8), "1 air above is not walking clearance");
        assertFalse(CaveDropTrap.cellQualifies(2, 0, 8), "no shell means already-open floor");
        assertFalse(CaveDropTrap.cellQualifies(2, 4, 8), "a 4-thick floor is honest ground, never mined");
        assertFalse(CaveDropTrap.cellQualifies(2, 1, 7), "a 7-air drop is a crawlspace, not a deeper layer");
    }

    @Test
    void thresholdsAreTheS44Contract() {
        assertEquals(2, CaveDropTrap.MIN_GALLERY_AIR);
        assertEquals(3, CaveDropTrap.MAX_SHELL_THICKNESS);
        assertEquals(8, CaveDropTrap.MIN_DROP_AIR);
        assertEquals(12, CaveDropTrap.PATCH_MAX_AREA, "a floor panel, not a floor deletion");
    }

    @Test
    void fractionGateIsHalfOpenAndSafeOutOfRange() {
        assertEquals(0.10f, CaveDropTrap.TRAP_FRACTION, 1e-6f, "census-calibrated: 0.35 was a minefield");
        assertEquals(1, CaveDropTrap.MAX_PATCHES_PER_CHUNK, "one hidden false floor per chunk at most");
        assertTrue(CaveDropTrap.shouldTrapPatch(0.0f));
        assertTrue(CaveDropTrap.shouldTrapPatch(0.09f));
        assertFalse(CaveDropTrap.shouldTrapPatch(0.10f), "exactly the fraction does NOT trap (half-open)");
        assertFalse(CaveDropTrap.shouldTrapPatch(-0.01f), "out-of-range never traps (floors stay honest)");
        assertFalse(CaveDropTrap.shouldTrapPatch(1.5f));
    }

    @Test
    void patchJoinKeepsOneCoherentFloorPanel() {
        assertTrue(CaveDropTrap.cellsJoinPatch(40, 40));
        assertTrue(CaveDropTrap.cellsJoinPatch(40, 41), "one block of floor undulation joins");
        assertFalse(CaveDropTrap.cellsJoinPatch(40, 42), "two apart is a different gallery level");
    }
}
