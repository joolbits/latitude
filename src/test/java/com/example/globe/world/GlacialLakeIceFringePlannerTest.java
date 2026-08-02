package com.example.globe.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure contract coverage for the lake-fringe planner; Minecraft block/fluid reads live in the feature adapter. */
class GlacialLakeIceFringePlannerTest {

    @Test
    void sameSeedProducesTheSameAnchoredClusters() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(-8, 24, -8, 24, 32);
        long seed = seedWithClusters(lake);
        assertEquals(GlacialLakeIceFringePlanner.planClusters(seed, lake),
                GlacialLakeIceFringePlanner.planClusters(seed, lake));
    }

    @Test
    void everyClusterIsConnectedAndHasTwoToFourCells() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(-8, 24, -8, 24, 32);
        List<GlacialLakeIceFringePlanner.Cluster> clusters =
                GlacialLakeIceFringePlanner.planClusters(seedWithClusters(lake), lake);
        assertFalse(clusters.isEmpty(), "fixture supplies a broad enough lake and a seed with visible fringe clusters");
        for (GlacialLakeIceFringePlanner.Cluster cluster : clusters) {
            assertTrue(cluster.cells().size() >= 2 && cluster.cells().size() <= 4,
                    "each floe is a small connected 2-4 block shape");
            assertTrue(isConnected(cluster.cells()), "each selected cluster stays connected");
        }
    }

    @Test
    void fringeTouchesTheShoreButNeverExtendsPastTheSecondWaterCell() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(-8, 24, -8, 24, 32);
        List<GlacialLakeIceFringePlanner.Cluster> clusters =
                GlacialLakeIceFringePlanner.planClusters(seedWithDirectShoreCluster(lake), lake);
        Set<GlacialLakeIceFringePlanner.Cell> writes = flatten(clusters);
        assertTrue(writes.stream().anyMatch(GlacialLakeIceFringePlannerTest::directlyBordersLand),
                "at least one selected ice cell directly meets the shoreline -- no open-water moat");
        assertTrue(writes.stream().allMatch(cell -> oneBasedSquareShoreDistance(cell, -8, 24, -8, 24) <= 2),
                "the ragged fringe is only the first or second water cell from shore");
    }

    @Test
    void belowYZeroNeverProducesAPlan() {
        Set<GlacialLakeIceFringePlanner.Cell> belowZero = squareLake(-8, 24, -8, 24, -1);
        assertTrue(GlacialLakeIceFringePlanner.planClusters(0L, belowZero).isEmpty());
    }

    @Test
    void narrowPoolsStayFullyOpenWithoutAnInteriorCore() {
        Set<GlacialLakeIceFringePlanner.Cell> narrow = squareLake(0, 3, 0, 8, 32);
        for (long seed = 0; seed < 128; seed++) {
            assertTrue(GlacialLakeIceFringePlanner.planClusters(seed, narrow).isEmpty(),
                    "a narrow channel has no protected 2x2 interior core and receives no ice");
        }
    }

    @Test
    void aTwoByTwoInteriorCoreRemainsOpen() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(0, 12, 0, 12, 32);
        Set<GlacialLakeIceFringePlanner.Cell> writes = new HashSet<>();
        for (GlacialLakeIceFringePlanner.Cluster cluster :
                GlacialLakeIceFringePlanner.planClusters(seedWithClusters(lake), lake)) {
            writes.addAll(cluster.cells());
        }
        for (int x = 5; x <= 6; x++) {
            for (int z = 5; z <= 6; z++) {
                assertFalse(writes.contains(new GlacialLakeIceFringePlanner.Cell(x, 32, z)),
                        "distance-three interior water remains a visible open 2x2 core");
            }
        }
    }

    @Test
    void ownerOutputsAreDisjointAndStableAcrossTheChunkBorder() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(-8, 24, -8, 24, 32);
        long seed = seedWithClusters(lake);
        List<GlacialLakeIceFringePlanner.Cell> left = GlacialLakeIceFringePlanner.plan(
                seed, lake, new GlacialLakeIceFringePlanner.OwnerChunk(0, 0));
        List<GlacialLakeIceFringePlanner.Cell> right = GlacialLakeIceFringePlanner.plan(
                seed, lake, new GlacialLakeIceFringePlanner.OwnerChunk(1, 0));
        Set<GlacialLakeIceFringePlanner.Cell> overlap = new HashSet<>(left);
        overlap.retainAll(right);
        assertTrue(overlap.isEmpty(), "a surface cell has exactly one owner chunk");
        assertEquals(left, GlacialLakeIceFringePlanner.plan(
                seed, lake, new GlacialLakeIceFringePlanner.OwnerChunk(0, 0)),
                "replanning the left owner yields the same border cells");
        assertEquals(right, GlacialLakeIceFringePlanner.plan(
                seed, lake, new GlacialLakeIceFringePlanner.OwnerChunk(1, 0)),
                "replanning the right owner yields the same border cells");
    }

    @Test
    void shiftedBoundedHalosKeepEachOwnerOutputEqualToTheFullLakePlan() {
        Set<GlacialLakeIceFringePlanner.Cell> fullLake = squareLake(-4, 36, -20, 40, 32);
        long seed = seedWithClusters(fullLake);
        int halo = 16; // feature read: (16 + 2 * 16)^2 * 48 = 110,592 positions at most
        GlacialLakeIceFringePlanner.OwnerChunk leftOwner = new GlacialLakeIceFringePlanner.OwnerChunk(0, 0);
        GlacialLakeIceFringePlanner.OwnerChunk rightOwner = new GlacialLakeIceFringePlanner.OwnerChunk(1, 0);

        assertEquals(GlacialLakeIceFringePlanner.plan(seed, fullLake, leftOwner),
                GlacialLakeIceFringePlanner.plan(seed, boundedView(fullLake, leftOwner, halo), leftOwner),
                "the left owner sees the same writes through its shifted bounded halo");
        assertEquals(GlacialLakeIceFringePlanner.plan(seed, fullLake, rightOwner),
                GlacialLakeIceFringePlanner.plan(seed, boundedView(fullLake, rightOwner, halo), rightOwner),
                "the right owner sees the same writes through its shifted bounded halo");
    }

    @Test
    void priorIceTopologyMakesBothChunkOrdersMatchTheImmutableLakePlan() {
        Set<GlacialLakeIceFringePlanner.Cell> lake = squareLake(0, 31, 0, 12, 32);
        GlacialLakeIceFringePlanner.OwnerChunk ownerA = new GlacialLakeIceFringePlanner.OwnerChunk(0, 0);
        GlacialLakeIceFringePlanner.OwnerChunk ownerB = new GlacialLakeIceFringePlanner.OwnerChunk(1, 0);
        int halo = 16;
        long seed = 2L; // Fixed order-sensitive fixture: immutable topology yields A=12, B=15 writes.

        Set<GlacialLakeIceFringePlanner.Cell> viewA = boundedView(lake, ownerA, halo);
        Set<GlacialLakeIceFringePlanner.Cell> viewB = boundedView(lake, ownerB, halo);
        List<GlacialLakeIceFringePlanner.Cell> baselineA =
                GlacialLakeIceFringePlanner.plan(seed, viewA, viewA, ownerA);
        List<GlacialLakeIceFringePlanner.Cell> baselineB =
                GlacialLakeIceFringePlanner.plan(seed, viewB, viewB, ownerB);
        Set<GlacialLakeIceFringePlanner.Cell> overlap = new HashSet<>(baselineA);
        overlap.retainAll(baselineB);
        assertTrue(overlap.isEmpty(), "owner outputs are disjoint");

        Set<GlacialLakeIceFringePlanner.Cell> sourcesAfterA = new HashSet<>(lake);
        sourcesAfterA.removeAll(baselineA); // A's writes are now plain ice, but stay in topology.
        List<GlacialLakeIceFringePlanner.Cell> bAfterA = GlacialLakeIceFringePlanner.plan(
                seed, viewB, boundedView(sourcesAfterA, ownerB, halo), ownerB);
        assertEquals(baselineB, bAfterA, "A-write-then-B matches the immutable baseline");

        Set<GlacialLakeIceFringePlanner.Cell> sourcesAfterB = new HashSet<>(lake);
        sourcesAfterB.removeAll(baselineB);
        List<GlacialLakeIceFringePlanner.Cell> aAfterB = GlacialLakeIceFringePlanner.plan(
                seed, viewA, boundedView(sourcesAfterB, ownerA, halo), ownerA);
        assertEquals(baselineA, aAfterB, "B-write-then-A matches the immutable baseline");

        Set<GlacialLakeIceFringePlanner.Cell> finalAB = new HashSet<>(baselineA);
        finalAB.addAll(bAfterA);
        Set<GlacialLakeIceFringePlanner.Cell> finalBA = new HashSet<>(baselineB);
        finalBA.addAll(aAfterB);
        assertEquals(finalAB, finalBA, "both invocation orders produce the identical final union");

        List<GlacialLakeIceFringePlanner.Cell> aRewriteAttempt = GlacialLakeIceFringePlanner.plan(
                seed, viewA, boundedView(sourcesAfterA, ownerA, halo), ownerA);
        Set<GlacialLakeIceFringePlanner.Cell> rewritten = new HashSet<>(aRewriteAttempt);
        rewritten.retainAll(baselineA);
        assertTrue(rewritten.isEmpty(), "existing plain ice remains topology but is never a write output");

        List<GlacialLakeIceFringePlanner.Cell> waterOnlyB = GlacialLakeIceFringePlanner.plan(
                seed,
                boundedView(sourcesAfterA, ownerB, halo),
                boundedView(sourcesAfterA, ownerB, halo),
                ownerB);
        List<GlacialLakeIceFringePlanner.Cell> waterOnlyA = GlacialLakeIceFringePlanner.plan(
                seed,
                boundedView(sourcesAfterB, ownerA, halo),
                boundedView(sourcesAfterB, ownerA, halo),
                ownerA);
        assertTrue(!baselineB.equals(waterOnlyB) || !baselineA.equals(waterOnlyA),
                "negative control: dropping prior ice from topology makes at least one order diverge");
    }

    @Test
    void disconnectedIceOnlyComponentCannotCreateAPlan() {
        Set<GlacialLakeIceFringePlanner.Cell> water = squareLake(0, 12, 0, 12, 32);
        Set<GlacialLakeIceFringePlanner.Cell> iceOnly = squareLake(40, 52, 0, 12, 32);
        Set<GlacialLakeIceFringePlanner.Cell> topology = new HashSet<>(water);
        topology.addAll(iceOnly);
        List<GlacialLakeIceFringePlanner.Cluster> clusters =
                GlacialLakeIceFringePlanner.planClusters(seedWithClusters(water), topology, water);
        assertTrue(clusters.stream().flatMap(cluster -> cluster.cells().stream()).noneMatch(iceOnly::contains),
                "plain ice is topology only when its connected eligible component still contains source water");
        assertTrue(GlacialLakeIceFringePlanner.planClusters(0L, iceOnly, Set.of()).isEmpty(),
                "an all-ice component has no write-bearing lake authority");
    }

    private static long seedWithClusters(Set<GlacialLakeIceFringePlanner.Cell> lake) {
        for (long seed = 0; seed < 10_000; seed++) {
            if (!GlacialLakeIceFringePlanner.planClusters(seed, lake).isEmpty()) {
                return seed;
            }
        }
        throw new AssertionError("fixture should yield a deterministic fringe cluster for one of the bounded seeds");
    }

    private static long seedWithDirectShoreCluster(Set<GlacialLakeIceFringePlanner.Cell> lake) {
        for (long seed = 0; seed < 10_000; seed++) {
            if (flatten(GlacialLakeIceFringePlanner.planClusters(seed, lake)).stream()
                    .anyMatch(GlacialLakeIceFringePlannerTest::directlyBordersLand)) {
                return seed;
            }
        }
        throw new AssertionError("fixture should yield a deterministic cluster that touches shore");
    }

    private static Set<GlacialLakeIceFringePlanner.Cell> boundedView(
            Set<GlacialLakeIceFringePlanner.Cell> lake,
            GlacialLakeIceFringePlanner.OwnerChunk owner,
            int halo) {
        int minX = owner.x() * 16 - halo;
        int maxX = owner.x() * 16 + 15 + halo;
        int minZ = owner.z() * 16 - halo;
        int maxZ = owner.z() * 16 + 15 + halo;
        Set<GlacialLakeIceFringePlanner.Cell> view = new HashSet<>();
        for (GlacialLakeIceFringePlanner.Cell cell : lake) {
            if (cell.x() >= minX && cell.x() <= maxX && cell.z() >= minZ && cell.z() <= maxZ) {
                view.add(cell);
            }
        }
        return view;
    }

    private static Set<GlacialLakeIceFringePlanner.Cell> flatten(
            List<GlacialLakeIceFringePlanner.Cluster> clusters) {
        Set<GlacialLakeIceFringePlanner.Cell> cells = new HashSet<>();
        for (GlacialLakeIceFringePlanner.Cluster cluster : clusters) {
            cells.addAll(cluster.cells());
        }
        return cells;
    }

    private static boolean directlyBordersLand(GlacialLakeIceFringePlanner.Cell cell) {
        return cell.x() == -8 || cell.x() == 24 || cell.z() == -8 || cell.z() == 24;
    }

    private static int oneBasedSquareShoreDistance(
            GlacialLakeIceFringePlanner.Cell cell, int minX, int maxX, int minZ, int maxZ) {
        return Math.min(Math.min(cell.x() - minX, maxX - cell.x()),
                Math.min(cell.z() - minZ, maxZ - cell.z())) + 1;
    }

    private static Set<GlacialLakeIceFringePlanner.Cell> squareLake(
            int minX, int maxX, int minZ, int maxZ, int y) {
        Set<GlacialLakeIceFringePlanner.Cell> cells = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cells.add(new GlacialLakeIceFringePlanner.Cell(x, y, z));
            }
        }
        return cells;
    }

    private static boolean isConnected(List<GlacialLakeIceFringePlanner.Cell> cells) {
        Set<GlacialLakeIceFringePlanner.Cell> unseen = new HashSet<>(cells);
        ArrayDeque<GlacialLakeIceFringePlanner.Cell> queue = new ArrayDeque<>();
        queue.add(unseen.iterator().next());
        unseen.remove(queue.peek());
        while (!queue.isEmpty()) {
            GlacialLakeIceFringePlanner.Cell cell = queue.removeFirst();
            for (GlacialLakeIceFringePlanner.Cell other : new HashSet<>(unseen)) {
                if (cell.y() == other.y() && Math.abs(cell.x() - other.x()) + Math.abs(cell.z() - other.z()) == 1) {
                    unseen.remove(other);
                    queue.addLast(other);
                }
            }
        }
        return unseen.isEmpty();
    }
}
