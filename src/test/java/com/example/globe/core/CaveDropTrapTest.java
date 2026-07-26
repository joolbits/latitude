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
        assertTrue(CaveDropTrap.cellQualifies(2, 1, 10), "the minimal legal cell (S50: 10-block drop floor)");
        assertTrue(CaveDropTrap.cellQualifies(10, 3, 30), "roomy gallery, max shell, deep drop");
        assertFalse(CaveDropTrap.cellQualifies(1, 1, 10), "1 air above is not walking clearance");
        assertFalse(CaveDropTrap.cellQualifies(2, 0, 10), "no shell means already-open floor");
        assertFalse(CaveDropTrap.cellQualifies(2, 4, 10), "a 4-thick floor is honest ground, never mined");
        assertFalse(CaveDropTrap.cellQualifies(2, 1, 9), "a 9-air drop is under the owner's 10-block spec");
    }

    @Test
    void thresholdsAreTheS50CarpetContract() {
        assertEquals(2, CaveDropTrap.MIN_GALLERY_AIR);
        assertEquals(3, CaveDropTrap.MAX_SHELL_THICKNESS);
        assertEquals(10, CaveDropTrap.MIN_DROP_AIR,
                "S50 owner spec: \"falling through down like at least 10 blocks down\"");
        assertEquals(6, CaveDropTrap.MIN_PATCH_AREA,
                "S50: single blocks and slivers never trap (owner: \"they are just single blocks right now\")");
        assertEquals(40, CaveDropTrap.PATCH_MAX_AREA,
                "S50: a carpet to stumble into -- surface-class breadth, never a floor deletion");
        assertTrue(CaveDropTrap.MIN_PATCH_AREA < CaveDropTrap.PATCH_MAX_AREA, "a real window");
    }

    @Test
    void fractionGateIsHalfOpenAndSafeOutOfRange() {
        assertEquals(0.30f, CaveDropTrap.TRAP_FRACTION, 1e-6f,
                "S50: hotter roll over the few carpet-scale panels the MIN_PATCH_AREA floor leaves eligible");
        assertEquals(1, CaveDropTrap.MAX_PATCHES_PER_CHUNK, "one hidden false floor per chunk at most");
        assertTrue(CaveDropTrap.shouldTrapPatch(0.0f));
        assertTrue(CaveDropTrap.shouldTrapPatch(0.29f));
        assertFalse(CaveDropTrap.shouldTrapPatch(0.30f), "exactly the fraction does NOT trap (half-open)");
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
