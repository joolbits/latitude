package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link SpawnCalmBand} (S10a, the TEST 99 polar-spawn fix) -- the degree-anchored
 * world-spawn calm band. Pins the 50-deg ceiling on the three canonical Z radii (the block-anchored guard
 * this replaces allowed 80.9 / 83.2 / 86.6 deg on 3750 / 10000 / 20000 -- it did NOT scale), the clamp
 * symmetry, and the deliberate saturation of the deep spawn-zone fractions at the calm edge.
 */
class SpawnCalmBandTest {

    @Test
    void spawnBandMaxesAtFiftyDegreesOnEveryRadius() {
        for (int zRadius : new int[]{3750, 10000, 20000}) {
            int maxAbsZ = SpawnCalmBand.maxAbsZ(zRadius);
            double maxDeg = (double) maxAbsZ * 90.0 / zRadius;
            assertTrue(maxDeg <= SpawnCalmBand.MAX_SPAWN_LAT_DEG,
                    "spawn band exceeded 50 deg on zRadius " + zRadius + " (" + maxDeg + ")");
            assertTrue(maxDeg > 49.9,
                    "spawn band should sit AT the 50-deg edge, not far inside it, on zRadius " + zRadius);
        }
        // Exact block values (floor of radius * 50/90).
        assertEquals(2083, SpawnCalmBand.maxAbsZ(3750));
        assertEquals(5555, SpawnCalmBand.maxAbsZ(10000));
        assertEquals(11111, SpawnCalmBand.maxAbsZ(20000));
    }

    @Test
    void theOldBlockAnchoredGuardWasTheBug_documented() {
        // The TEST 99 root cause pin: the replaced guard (zRadius - 256 - 500) permitted these latitudes --
        // all deep inside or beyond the 80-deg polar-country onset. The calm band forbids every one of them.
        for (int zRadius : new int[]{3750, 10000, 20000}) {
            int oldGuard = zRadius - 256 - 500;
            double oldDeg = (double) oldGuard * 90.0 / zRadius;
            assertTrue(oldDeg > 80.0 || zRadius == 3750,
                    "the old guard was already in polar country on zRadius " + zRadius);
            assertTrue(oldGuard > SpawnCalmBand.maxAbsZ(zRadius),
                    "the calm band must be strictly tighter than the old block guard");
        }
        // Regular-Wide (the owner's world): old guard allowed 83.2 deg.
        assertEquals(83.196, (10000 - 756) * 90.0 / 10000.0, 0.001);
    }

    @Test
    void clampZIsSymmetricAndSaturatesDeepZoneFractions() {
        int zRadius = 10000;
        int max = SpawnCalmBand.maxAbsZ(zRadius);
        assertEquals(max, SpawnCalmBand.clampZ(99999, zRadius));
        assertEquals(-max, SpawnCalmBand.clampZ(-99999, zRadius));
        assertEquals(1234, SpawnCalmBand.clampZ(1234, zRadius), "inside the band: untouched");
        // The POLAR spawn-zone fraction (0.89 -> 8900 blocks = 80.1 deg, exactly ON the S8 polar-country
        // onset -- reachable directly or via RANDOM's six-zone pool) now saturates at the 50-deg calm edge:
        // deep-cold starts are retired by S10a law ("you gotta earn that").
        int polarZoneZ = (int) Math.round(zRadius * 0.89);
        assertEquals(max, SpawnCalmBand.clampZ(polarZoneZ, zRadius));
        // SUBPOLAR (0.725 -> 65.25 deg) saturates too -- deliberate; TEMPERATE (0.472 -> 42.5 deg) passes.
        assertEquals(max, SpawnCalmBand.clampZ((int) Math.round(zRadius * 0.725), zRadius));
        assertEquals(4720, SpawnCalmBand.clampZ((int) Math.round(zRadius * 0.472), zRadius));
    }

    @Test
    void degenerateRadiusYieldsNoBand() {
        assertEquals(0, SpawnCalmBand.maxAbsZ(0));
        assertEquals(0, SpawnCalmBand.maxAbsZ(-5));
        assertEquals(0, SpawnCalmBand.clampZ(500, 0));
    }
}
