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

    // ---- B-10 unified weighted path (design §3.2 mixing table, sweep A2/A3) ----

    @Test
    void legacyPathIsBitIdenticalToTodayAcrossTheWholeDomain() {
        // Sweep A3: the flag-OFF path is the single-count method, and it must be EXACTLY today's contract for
        // every 0..4 count -- this is the no-protection-gap guarantee. (The multiplier table above pins the
        // values; this asserts the legacy methods stay the ones the flag-off shim calls.)
        for (int p = 0; p <= ColdProtection.MAX_PIECES; p++) {
            assertEquals((4 - p) / 4.0, ColdProtection.damageMultiplier(p), 1e-9);
            assertEquals(p >= 4, ColdProtection.negatesFreezeDamage(p));
        }
    }

    @Test
    void unifiedMixingTableMatchesTheDesign() {
        // Design §3.2 worked examples: (suitPieces, leatherPieces) -> freeze-damage multiplier.
        assertEquals(1.00, ColdProtection.weightedMultiplier(0, 0), 1e-9, "bare = full damage");
        assertEquals(0.50, ColdProtection.weightedMultiplier(0, 4), 1e-9, "4 leather = 50% (capped)");
        assertEquals(0.75, ColdProtection.weightedMultiplier(0, 2), 1e-9, "2 leather");
        assertEquals(0.25, ColdProtection.weightedMultiplier(2, 2), 1e-9, "2 suit + 2 leather");
        assertEquals(0.125, ColdProtection.weightedMultiplier(3, 1), 1e-9, "3 suit + 1 leather -- still short");
        assertEquals(0.00, ColdProtection.weightedMultiplier(4, 0), 1e-9, "4 suit = zero damage");
        // partial suit alone (no leather): straight off the 0.25 weights.
        assertEquals(0.75, ColdProtection.weightedMultiplier(1, 0), 1e-9);
        assertEquals(0.50, ColdProtection.weightedMultiplier(2, 0), 1e-9);
        assertEquals(0.25, ColdProtection.weightedMultiplier(3, 0), 1e-9);
    }

    @Test
    void leatherCanNeverReachTotalProtection() {
        // The whole point of the demotion: even a "full" 4-leather set is capped at 0.5 protection (mult 0.5),
        // strictly worse than any 4-piece outfit containing a suit piece, and never fully protected.
        assertEquals(0.50, ColdProtection.weightedMultiplier(0, 4), 1e-9);
        assertFalse(ColdProtection.fullyProtected(0), "4 leather (0 suit) is NOT fully protected");
        // even over-reading leather beyond 4 stays capped (belt-and-suspenders cap).
        assertEquals(0.50, ColdProtection.weightedMultiplier(0, 9), 1e-9);
    }

    @Test
    void fullyProtectedIsSuitCountFourOnly_notWeight() {
        // Sweep: the predicate keys on suitPieces == 4 ALONE, never totalWeight >= 1.0.
        assertFalse(ColdProtection.fullyProtected(0));
        assertFalse(ColdProtection.fullyProtected(3), "3 suit + any leather is still not the full SET");
        assertTrue(ColdProtection.fullyProtected(4));
        assertTrue(ColdProtection.fullyProtected(9), "clamps, still fully protected");
        // 3 suit + 1 leather sums to 0.875 weight but is NOT fully protected -- must complete the set.
        assertFalse(ColdProtection.fullyProtected(3));
        assertEquals(0.125, ColdProtection.weightedMultiplier(3, 1), 1e-9, "and still takes 12.5% damage");
    }

    @Test
    void unifiedPathClampsBadShimReads() {
        assertEquals(1.0, ColdProtection.weightedMultiplier(-2, -2), 1e-9);
        assertEquals(0.0, ColdProtection.weightedMultiplier(9, 9), 1e-9);
    }

    @Test
    void fullSuitSuppressesColdEffects_sweepA8_partialAndLeatherDoNot() {
        // A8: the full-suit predicate is the MASTER exemption for the whole hazard-effects family (slowness /
        // weakness / mining fatigue / immersion staging), not just damage -- and it keys on suit == 4 alone.
        assertTrue(ColdProtection.suppressesColdEffects(4), "full suit walks freely (effects lifted)");
        assertFalse(ColdProtection.suppressesColdEffects(3), "3 suit: effects still seep (must complete the set)");
        assertFalse(ColdProtection.suppressesColdEffects(0), "bare/leather: effects always seep");
        assertTrue(ColdProtection.suppressesColdEffects(9), "clamps");
        // It IS the fullyProtected predicate -- one evaluator, one truth across the family.
        for (int p = 0; p <= 6; p++) {
            assertEquals(ColdProtection.fullyProtected(p), ColdProtection.suppressesColdEffects(p),
                    "effects exemption == fullyProtected at suitPieces=" + p);
        }
    }
}
