package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link CrevasseLocator} (S25(A) -- the /latdev locateCrevasse prediction core). The
 * vanilla-fidelity anchor is the 26.2 bytecode ground truth documented in the class javadoc (javap of
 * {@code WorldgenRandom.setLargeFeatureSeed} / {@code *WorldCarver.isStartChunk} / {@code LegacyRandomSource}
 * == {@code java.util.Random}); these tests pin the replay's own algebra, determinism, distribution, and the
 * spiral's nearest-hit contract.
 */
class CrevasseLocatorTest {

    @Test
    void rollIsTheDocumentedVanillaAlgebra() {
        // Pin the exact three-step derivation (setSeed(seed+i); a,b = nextLong x2; reseed x*a ^ z*b ^ seed;
        // one nextFloat) against an independent inline computation, over a spread of seeds/chunks/indices.
        long[] seeds = {0L, 1L, -1L, 8362845L, Long.MAX_VALUE, Long.MIN_VALUE / 3};
        int[] coords = {-1000, -37, 0, 1, 53, 812};
        for (long worldSeed : seeds) {
            for (int idx = 0; idx <= 4; idx++) {
                for (int cx : coords) {
                    for (int cz : coords) {
                        long seed = worldSeed + idx;
                        Random reference = new Random(seed);
                        long a = reference.nextLong();
                        long b = reference.nextLong();
                        reference.setSeed((long) cx * a ^ (long) cz * b ^ seed);
                        boolean expected = reference.nextFloat() <= 0.14f;
                        assertEquals(expected,
                                CrevasseLocator.carverStartsAt(worldSeed, idx, cx, cz, 0.14f),
                                "replay algebra at seed=" + worldSeed + " idx=" + idx + " (" + cx + "," + cz + ")");
                    }
                }
            }
        }
    }

    @Test
    void rollIsDeterministicAndIndexSensitive() {
        // Determinism: the same inputs always agree (a pure function -- the whole point of the replay).
        assertEquals(CrevasseLocator.carverStartsAt(123456789L, 3, 40, -900, 0.14f),
                CrevasseLocator.carverStartsAt(123456789L, 3, 40, -900, 0.14f));
        // Index sensitivity: index 3 (crevasse after a 3-carver vanilla biome) and index 4 (tunnels) are
        // DIFFERENT streams -- over many chunks they must disagree somewhere (vanilla seeds per list index).
        boolean anyDifference = false;
        for (int cz = 500; cz < 700 && !anyDifference; cz++) {
            anyDifference = CrevasseLocator.carverStartsAt(42L, 3, 7, cz, 0.14f)
                    != CrevasseLocator.carverStartsAt(42L, 4, 7, cz, 0.14f);
        }
        assertTrue(anyDifference, "adjacent list indices roll independent streams");
        // Probability envelope: 1.0 always starts; 0-ish probability effectively never does.
        assertTrue(CrevasseLocator.carverStartsAt(42L, 3, 7, 500, 1.0f), "p=1: every chunk starts");
        int zeroHits = 0;
        for (int cz = 0; cz < 200; cz++) {
            if (CrevasseLocator.carverStartsAt(42L, 3, 7, cz, 0.0f)) {
                zeroHits++;
            }
        }
        assertEquals(0, zeroHits, "p=0: no starts (nextFloat()==0.0 is a ~2^-24 event, absent in this sample)");
    }

    @Test
    void hitRateTracksTheProbability() {
        // Deterministic distribution check (fixed seed, fixed grid -- no flakiness): the 0.14 crevasse roll
        // over a 100x100 chunk grid must land near 14%. A wide +-3% acceptance band = ~7 sigma for n=10000.
        int hits = 0;
        int n = 0;
        for (int cx = -50; cx < 50; cx++) {
            for (int cz = 800; cz < 900; cz++) {
                n++;
                if (CrevasseLocator.carverStartsAt(987654321L, 3, cx, cz, 0.14f)) {
                    hits++;
                }
            }
        }
        double rate = hits / (double) n;
        assertTrue(rate > 0.11 && rate < 0.17,
                "start rate ~= the JSON probability (got " + rate + " for p=0.14)");
    }

    @Test
    void findNearestReturnsTheTrueNearestNotJustTheFirstRing() {
        // A hit at Chebyshev ring 2 can be EUCLIDEAN-nearer than a diagonal hit in ring 1 relative to an
        // off-center origin. Predicate: hits at chunk (1,1) (ring-1 diagonal) and (2,0) (ring 2). Origin at
        // the east edge of chunk 0 (block 15.9, 8): center of (2,0) is ~24.6 blocks; center of (1,1) is
        // ~17.7 blocks -> (1,1) is nearer and must win... then move the origin so (2,0) wins instead.
        CrevasseLocator.StartChunkPredicate pred = (cx, cz) -> (cx == 1 && cz == 1) || (cx == 2 && cz == 0);
        CrevasseLocator.Hit nearDiagonal = CrevasseLocator.findNearest(15.9, 8.0, 8, pred);
        assertNotNull(nearDiagonal);
        assertEquals(1, nearDiagonal.chunkX());
        assertEquals(1, nearDiagonal.chunkZ());
        // Origin at (15.9, 0): center (1,1)=(24.5,24.5) is ~26 blocks; center (2,0)=(40.5,8.5) is ~26.1 --
        // move the origin north a touch to make the ring-2 hit decisively nearer: origin (15.9, -6).
        CrevasseLocator.Hit nearStraight = CrevasseLocator.findNearest(15.9, -6.0, 8,
                (cx, cz) -> (cx == 1 && cz == 1) || (cx == 2 && cz == -1));
        assertNotNull(nearStraight);
        assertEquals(2, nearStraight.chunkX());
        assertEquals(-1, nearStraight.chunkZ());
    }

    @Test
    void findNearestContractBasics() {
        // No hit inside the cap -> null (the command's "no crevasse within N chunks" branch).
        assertNull(CrevasseLocator.findNearest(0.0, 0.0, 4, (cx, cz) -> false));
        // The origin's own chunk is candidate #0 (ring 0) and reports its center block.
        CrevasseLocator.Hit self = CrevasseLocator.findNearest(100.0, -200.0, 4, (cx, cz) -> true);
        assertNotNull(self);
        assertEquals(6, self.chunkX(), "origin block 100 -> chunk 6");
        assertEquals(-13, self.chunkZ(), "origin block -200 -> chunk -13");
        assertEquals(6 * 16 + 8, self.blockX());
        assertEquals(-13 * 16 + 8, self.blockZ());
        assertTrue(self.distanceBlocks() < 16.0, "inside the origin chunk: sub-chunk distance");
        // Negative-coordinate origins floor correctly (floorDiv, not truncation).
        CrevasseLocator.Hit negative = CrevasseLocator.findNearest(-0.5, -0.5, 0, (cx, cz) -> true);
        assertNotNull(negative);
        assertEquals(-1, negative.chunkX());
        assertEquals(-1, negative.chunkZ());
        // The search never calls the predicate outside the cap: radius 0 sees exactly one chunk.
        int[] calls = {0};
        CrevasseLocator.findNearest(8.0, 8.0, 0, (cx, cz) -> {
            calls[0]++;
            return false;
        });
        assertEquals(1, calls[0], "radius 0 = the origin chunk only");
        // The documented constants (the command surfaces both).
        assertEquals(64, CrevasseLocator.DEFAULT_SEARCH_RADIUS_CHUNKS);
        assertEquals(8, CrevasseLocator.CARVER_ARC_REACH_CHUNKS);
        // Roll purity: findNearest with a carverStartsAt-backed predicate is reproducible end-to-end.
        CrevasseLocator.StartChunkPredicate roll =
                (cx, cz) -> CrevasseLocator.carverStartsAt(2026L, 3, cx, cz, 0.14f);
        CrevasseLocator.Hit a = CrevasseLocator.findNearest(0.0, 13000.0, 16, roll);
        CrevasseLocator.Hit b = CrevasseLocator.findNearest(0.0, 13000.0, 16, roll);
        assertEquals(a, b, "the whole prediction is a pure function of (seed, origin, radius)");
        assertNotNull(a, "a 33x33-chunk window at p=0.14 statistically always holds a start (fixed seed)");
        assertFalse(a.distanceBlocks() < 0.0);
    }
}
