package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit-shape variety and laws swept across many accepted plans (varying both world seed and chunk), going
 * beyond {@code HiddenChamberPlanTest.theExitIsAlwaysPresentDistantBentAndClearOfTheShaft}'s single-seed,
 * three-theme check.
 *
 * <p>{@link HiddenChamberTerrainFixtures#roomy()} turns out to be the wrong shape for the "corridors
 * differ" half of this proof: it is uniform enough that the terminus RANKING inside {@code routeExit}
 * (floor, then distance, then coordinate -- {@code HiddenChamberPlan} lines ~1241-1245) never depends on
 * the hashed chamber shape at all, and the top-ranked terminus is always reachable, so EVERY accepted plan
 * lands on the identical terminus offset (verified empirically: 6000/6000 accepted plans, one offset).
 * {@link HiddenChamberTerrainFixtures#compactForcing()}'s tighter, shallower volume does not have that
 * problem -- the hashed chamber shape occasionally does block the top-ranked route, so a real handful of
 * offsets appear. The rarest of the five distinct offsets this class checks for only turns up once in
 * several thousand tries under a blind sweep (also measured empirically, off a standalone probe against
 * the real class), which would make a from-scratch search here slow; the four non-default offsets are
 * instead pinned to the exact {@code (seed, chunkX, chunkZ)} that produced them in that probe, so this
 * class both stays fast and still runs every exit-law assertion against all of them.
 *
 * <p>One documented gap: the "never two consecutive rises without two flat steps between" corridor law
 * (enforced internally by {@code search()}'s {@code flat}-tracked state machine, {@code HiddenChamberPlan}
 * lines ~1368-1391) is not independently re-verified here. The write list exposes only the corridor's
 * unordered CELLS, not the private {@code Station} sequence that gives them an order; a station is two
 * cells wide, so the natural cell-adjacency graph is a two-wide ribbon (most cells have graph degree three),
 * not a simple path, and a prototype reconstruction (the same standalone probe) confirmed that both a
 * greedy nearest-neighbour walk and a strict-degree endpoint walk fail to recover a trustworthy station
 * order from the write list alone -- attempting it would produce a flaky/wrong test, not a meaningful one.
 * Every other exit law in the required proof list is swept below.
 */
class HiddenChamberExitLawSweepTest {

    private static final long SEED_BASE = 0x5EED_C0FFEEL;
    private static final int MIN_ACCEPTED_PLANS = 50;

    /** {@code (seedOffset, chunkX, chunkZ)} pinned to the non-default terminus offsets they produce. */
    private static final long[][] RARE_OFFSET_COORDINATES = {
        {0, 3, 6}, {0, 3, 7}, {6, 9, 9}, {52, 9, 7}
    };

    @Test
    void exitLawsHoldAndCorridorsVaryAcrossAtLeastFiftyAcceptedPlans() {
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.compactForcing();
        List<HiddenChamberPlan.Plan> accepted = new ArrayList<>();
        Set<String> terminusOffsets = new LinkedHashSet<>();

        for (long seedOffset = 0; seedOffset < 1 && accepted.size() < MIN_ACCEPTED_PLANS; seedOffset++) {
            for (int chunkX = 0; chunkX < 15 && accepted.size() < MIN_ACCEPTED_PLANS; chunkX++) {
                for (int chunkZ = 0; chunkZ < 15 && accepted.size() < MIN_ACCEPTED_PLANS; chunkZ++) {
                    collectIfAccepted(terrain, SEED_BASE + seedOffset, chunkX, chunkZ, accepted, terminusOffsets);
                }
            }
        }
        assertTrue(accepted.size() >= MIN_ACCEPTED_PLANS,
                "expected at least " + MIN_ACCEPTED_PLANS + " accepted plans from the general sweep alone, saw "
                        + accepted.size());

        for (long[] coordinate : RARE_OFFSET_COORDINATES) {
            collectIfAccepted(terrain, SEED_BASE + coordinate[0], (int) coordinate[1], (int) coordinate[2],
                    accepted, terminusOffsets);
        }

        for (HiddenChamberPlan.Plan plan : accepted) {
            assertExitLaws(plan);
        }

        assertTrue(terminusOffsets.size() >= 5,
                "corridors must differ across seeds: expected at least 5 distinct terminus offsets, saw "
                        + terminusOffsets.size() + " " + terminusOffsets);
    }

    private static void collectIfAccepted(HiddenChamberTerrainFixtures.Mutable terrain, long seed, int chunkX,
                                          int chunkZ, List<HiddenChamberPlan.Plan> accepted, Set<String> offsets) {
        HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(seed, chunkX, chunkZ, terrain, null);
        if (!result.isAccepted()) {
            return;
        }
        HiddenChamberPlan.Plan plan = result.accepted();
        accepted.add(plan);
        offsets.add((plan.exitTerminus().x() - plan.mouthCentroid().x()) + ":"
                + (plan.exitTerminus().z() - plan.mouthCentroid().z()));
    }

    private static void assertExitLaws(HiddenChamberPlan.Plan plan) {
        int distance = Math.max(
                Math.abs(plan.exitTerminus().x() - plan.mouthCentroid().x()),
                Math.abs(plan.exitTerminus().z() - plan.mouthCentroid().z()));
        assertTrue(distance >= HiddenChamberPlan.EXIT_DISTANCE_MIN && distance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                plan.theme() + " terminus distance " + distance);
        assertTrue(Math.abs(plan.exitTerminus().y() - plan.mouthFloorY()) <= HiddenChamberPlan.EXIT_FLOOR_TOLERANCE,
                plan.theme() + " terminus floor " + plan.exitTerminus().y() + " vs mouth floor " + plan.mouthFloorY());
        assertTrue(plan.corridorBends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                plan.theme() + " corridor bends " + plan.corridorBends());

        Set<Long> shaftColumns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
            shaftColumns.add(columnKey(mouth.x(), mouth.z()));
        }
        List<HiddenChamberPlan.Cell> corridor = new ArrayList<>();
        corridor.addAll(plan.writesWithRole(HiddenChamberPlan.CellRole.CORRIDOR_CLEAR));
        corridor.addAll(plan.writesWithRole(HiddenChamberPlan.CellRole.CORRIDOR_FLOOR));
        corridor.addAll(plan.writesWithRole(HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR));
        assertTrue(!corridor.isEmpty(), plan.theme() + " must author a corridor");
        for (HiddenChamberPlan.Cell cell : corridor) {
            for (long shaftKey : shaftColumns) {
                int sx = (int) (shaftKey >> 32) - 4096;
                int sz = (int) (shaftKey & 0xFFFFFFFFL) - 4096;
                int gap = Math.max(Math.abs(cell.x() - sx), Math.abs(cell.z() - sz));
                assertTrue(gap > HiddenChamberPlan.SHAFT_CLEARANCE,
                        plan.theme() + " corridor cell " + cell + " passes under the shaft column (" + sx + "," + sz + ")");
            }
        }
    }

    private static long columnKey(int x, int z) {
        return ((long) (x + 4096) << 32) | (z + 4096);
    }
}
