package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link ParticleDensity} (Phase 5 performance-scaling helper).
 *
 * <p>Peetsa's intent: the N/S polar ambient-snow budget must honor the player's vanilla Particles
 * video setting so a low setting is a real performance win at the pole, WITHOUT collapsing the pole
 * to bare (MINIMAL is a thin blizzard, not zero). Vanilla has exactly THREE tiers (ALL/DECREASED/
 * MINIMAL); {@link ParticleDensity.Tier#FULL} mirrors ALL and is identity so the default look is
 * unchanged. Every function under test is a pure, stateless function of its arguments -- consistent
 * with the surrounding anti-backlog law (no accumulator, no per-tick state).
 */
class ParticleDensityTest {

    @Test
    void fullIsIdentityForVariousCounts() {
        // WHY: FULL == vanilla ALL must be bit-for-bit the current behavior at every count, so
        // players on the default setting see exactly today's blizzard.
        assertEquals(0, ParticleDensity.scale(ParticleDensity.Tier.FULL, 0));
        assertEquals(1, ParticleDensity.scale(ParticleDensity.Tier.FULL, 1));
        assertEquals(2, ParticleDensity.scale(ParticleDensity.Tier.FULL, 2));
        assertEquals(30, ParticleDensity.scale(ParticleDensity.Tier.FULL, 30));
        assertEquals(1000, ParticleDensity.scale(ParticleDensity.Tier.FULL, 1000));
    }

    @Test
    void decreasedIsAboutHalfForSmallAndLargeCounts() {
        // WHY: DECREASED == roughly half. Check a small count (rounding-sensitive) and a large one.
        assertEquals(15, ParticleDensity.scale(ParticleDensity.Tier.DECREASED, 30));
        assertEquals(500, ParticleDensity.scale(ParticleDensity.Tier.DECREASED, 1000));
        // small count: 3 * 0.5 = 1.5 -> rounds to 2 (Math.round rounds half up)
        assertEquals(2, ParticleDensity.scale(ParticleDensity.Tier.DECREASED, 3));
    }

    @Test
    void minimalIsMuchSmallerButNonNegative() {
        // WHY: MINIMAL is a low floor (0.15) -- a real perf win but still a visible thin blizzard.
        int minimal30 = ParticleDensity.scale(ParticleDensity.Tier.MINIMAL, 30);
        assertEquals(5, minimal30);                 // 30 * 0.15 = 4.5 -> 5
        assertTrue(minimal30 >= 0);
        assertTrue(minimal30 < ParticleDensity.scale(ParticleDensity.Tier.DECREASED, 30));
        // large count stays proportional and non-negative
        assertEquals(150, ParticleDensity.scale(ParticleDensity.Tier.MINIMAL, 1000));
    }

    @Test
    void monotonicFullGreaterEqualDecreasedGreaterEqualMinimal() {
        // WHY: for the SAME input, more-reduced tiers must never produce MORE particles.
        for (int count : new int[] {0, 1, 2, 5, 10, 30, 100, 999, 5000}) {
            int full = ParticleDensity.scale(ParticleDensity.Tier.FULL, count);
            int decreased = ParticleDensity.scale(ParticleDensity.Tier.DECREASED, count);
            int minimal = ParticleDensity.scale(ParticleDensity.Tier.MINIMAL, count);
            assertTrue(full >= decreased, "FULL >= DECREASED at count=" + count);
            assertTrue(decreased >= minimal, "DECREASED >= MINIMAL at count=" + count);
            assertTrue(minimal >= 0, "MINIMAL >= 0 at count=" + count);
        }
    }

    @Test
    void zeroInputStaysZeroForEveryTier() {
        // WHY: a 0-budget spawn-tick (below the snow onset) must scale to 0 regardless of tier --
        // no special-casing needed downstream, a 0-iteration loop is fine.
        for (ParticleDensity.Tier tier : ParticleDensity.Tier.values()) {
            assertEquals(0, ParticleDensity.scale(tier, 0), "zero stays zero for " + tier);
        }
    }

    @Test
    void negativeInputClampsToZero() {
        // WHY: defensive -- a negative count should never yield a negative (or spurious) result.
        for (ParticleDensity.Tier tier : ParticleDensity.Tier.values()) {
            assertEquals(0, ParticleDensity.scale(tier, -5), "negative clamps to zero for " + tier);
        }
    }
}
