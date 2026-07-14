package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link ColdProtection} (B-7 S1). Pins the piece-count -&gt; freeze-DAMAGE multiplier
 * table, the full-set negation, the {@code protectionLevel} exposed for the P2 text swap, and out-of-range
 * clamping. The multiplier is the SAME factor GlobeMod applies to BOTH the frostbite band and the lethal core
 * at the single computed-amount point (so full leather = zero damage in both -- exercised here via the table
 * with a comment; the per-band wiring is verified by GlobeMod compile + the band tests).
 */
class ColdProtectionTest {

    @Test
    void multiplierTableIsQuarterStepsFromOneToZero() {
        assertEquals(1.00, ColdProtection.damageMultiplier(0), 1e-9, "0 pieces = full damage");
        assertEquals(0.75, ColdProtection.damageMultiplier(1), 1e-9);
        assertEquals(0.50, ColdProtection.damageMultiplier(2), 1e-9);
        assertEquals(0.25, ColdProtection.damageMultiplier(3), 1e-9);
        assertEquals(0.00, ColdProtection.damageMultiplier(4), 1e-9, "full set = zero freeze damage");
    }

    @Test
    void fullSetNegatesFreezeDamageInBothBands() {
        // The multiplier is 0 for a full set, so ANY curve amount (frostbite's 1.0 HP or the lethal core's
        // 2.2 HP at 89.2) multiplies to 0 -- full leather negates freeze DAMAGE in both bands.
        assertTrue(ColdProtection.negatesFreezeDamage(4));
        assertEquals(0.0, 1.0f * ColdProtection.damageMultiplier(4), 1e-9, "frostbite 1.0 HP -> 0");
        assertEquals(0.0, 2.2f * ColdProtection.damageMultiplier(4), 1e-9, "lethal 2.2 HP -> 0");
        assertFalse(ColdProtection.negatesFreezeDamage(3), "three pieces still take a quarter of the damage");
    }

    @Test
    void protectionLevelIsTheClampedPieceCount() {
        for (int p = 0; p <= ColdProtection.MAX_PIECES; p++) {
            assertEquals(p, ColdProtection.protectionLevel(p));
        }
    }

    @Test
    void outOfRangeCountsClampNotThrow() {
        // Negative or >4 (defensive against a bad shim read) clamp to the ends.
        assertEquals(1.0, ColdProtection.damageMultiplier(-3), 1e-9);
        assertEquals(0.0, ColdProtection.damageMultiplier(9), 1e-9);
        assertEquals(0, ColdProtection.protectionLevel(-3));
        assertEquals(ColdProtection.MAX_PIECES, ColdProtection.protectionLevel(9));
        assertTrue(ColdProtection.negatesFreezeDamage(9));
    }
}
