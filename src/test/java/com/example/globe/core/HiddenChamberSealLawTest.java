package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wall-seal law: {@link HiddenChamberPlan} closes the natural cave air around the encounter's ENCLOSED
 * interior, and never around its two OPENINGS.
 *
 * <h2>What this class exists to stop happening again</h2>
 * {@code addWallSeals} used to derive its seals from {@link HiddenChamberPlan.CellRole#isCarve()}, which
 * includes {@link HiddenChamberPlan.CellRole#MOUTH_COLLAPSE} and
 * {@link HiddenChamberPlan.CellRole#EXIT_PORTAL_CLEAR}. Because the mouth probe only ever accepts a floor
 * with air above it, the cell at {@code (mouth.x, mouth.y + 1, mouth.z)} was ALWAYS unplanned air and so was
 * ALWAYS sealed: every collapse mouth shipped capped with a slab of packed ice -- a visible ice patch in a
 * snowy cave floor, and nothing could fall through it. The same law walled the second opening shut, so every
 * chamber generated as a sealed, unreachable void. Every existing test passed throughout, because none of
 * them looked at what stood ABOVE a mouth or BESIDE the portal.
 *
 * <p>The seals that remain are asserted too ({@link #theInteriorIsStillSealed()}): the fix narrows the seed
 * set, it does not switch sealing off.
 */
class HiddenChamberSealLawTest {

    /** Four seeds x four chunks x three themes = 48 accepted plans per sweep. */
    private static final long[] SEEDS = {1L, 0x5EED_C0FFEEL, -1234L, 987_654_321L};
    private static final int[][] CHUNKS = {{0, 0}, {4, -9}, {11, 7}, {-13, 21}};
    private static final int MIN_SWEEP_PLANS = 30;

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. The mouth is never capped                                                                          */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void noAcceptedPlanEverWritesTheBlockDirectlyAboveACollapseMouth() {
        List<Sample> sweep = sweep();
        assertTrue(sweep.size() >= MIN_SWEEP_PLANS,
                "the sweep must exercise at least " + MIN_SWEEP_PLANS + " accepted plans, got " + sweep.size());
        for (Sample sample : sweep) {
            HiddenChamberPlan.Plan plan = sample.plan();
            Set<Long> written = new LinkedHashSet<>();
            for (HiddenChamberPlan.Cell cell : plan.writes()) {
                written.add(key(cell.x(), cell.y(), cell.z()));
            }
            for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
                assertFalse(written.contains(key(mouth.x(), mouth.y() + 1, mouth.z())),
                        sample + ": the mouth at " + mouth + " is capped -- the block above a collapse cell "
                                + "is the upper cave the victim walks in and must stay open");
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. The second opening is never walled shut                                                            */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * The portal is never capped and its own column never carries a seal.
     *
     * <p>Note what is deliberately NOT asserted: that no seal is orthogonally adjacent to any portal cell.
     * Seals derived from {@link HiddenChamberPlan.CellRole#CORRIDOR_CLEAR} legitimately stand beside the
     * opening -- the corner where the bore's own wall meets the breach -- and 107 of them appear across this
     * sweep's 48 plans. Every one of those is in a NEIGHBOURING column; zero stand in a portal column and
     * zero cap one, which is exactly the difference between a corridor with walls and an exit bricked shut.
     */
    @Test
    void theExitPortalIsNeverCappedAndItsOwnColumnCarriesNoSeal() {
        List<Sample> sweep = sweep();
        assertTrue(sweep.size() >= MIN_SWEEP_PLANS, "sweep size " + sweep.size());
        for (Sample sample : sweep) {
            HiddenChamberPlan.Plan plan = sample.plan();
            List<HiddenChamberPlan.Cell> portal =
                    plan.writesWithRole(HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR);
            assertFalse(portal.isEmpty(), sample + ": an accepted plan always breaches a second opening");

            Set<Long> written = new LinkedHashSet<>();
            for (HiddenChamberPlan.Cell cell : plan.writes()) {
                written.add(key(cell.x(), cell.y(), cell.z()));
            }
            Map<Long, int[]> portalBand = new LinkedHashMap<>();
            for (HiddenChamberPlan.Cell cell : portal) {
                portalBand.compute(columnKey(cell.x(), cell.z()), (column, band) -> band == null
                        ? new int[] {cell.y(), cell.y()}
                        : new int[] {Math.min(band[0], cell.y()), Math.max(band[1], cell.y())});
            }
            for (HiddenChamberPlan.Cell cell : portal) {
                int[] band = portalBand.get(columnKey(cell.x(), cell.z()));
                assertFalse(written.contains(key(cell.x(), band[1] + 1, cell.z())),
                        sample + ": the exit portal column of " + cell + " is capped at y=" + (band[1] + 1)
                                + " -- the portal is the second opening into the upper cave, not a bore "
                                + "that stops in packed ice");
            }
            for (HiddenChamberPlan.Cell seal : plan.writesWithRole(HiddenChamberPlan.CellRole.WALL_SEAL)) {
                int[] band = portalBand.get(columnKey(seal.x(), seal.z()));
                assertFalse(band != null && seal.y() >= band[0],
                        sample + ": wall seal " + seal + " stands inside the exit portal's own column");
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 3. The seals that must remain                                                                         */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * No seal without a reason: every {@link HiddenChamberPlan.CellRole#WALL_SEAL} touches a cell that stands
     * INSIDE the encounter's enclosed interior.
     *
     * <p>"Stands inside" is the operative phrase, and it is broader than {@link
     * HiddenChamberPlan.CellRole#isSealedInterior()} for a reason the finished write list cannot show. The
     * seal pass is seeded from the footprint the encounter CARVED, taken before the dressing pass runs;
     * dressing then fills some of those carved cells with an ice column, an icicle, a lantern, a floe. Read
     * off the finished map, such a cell no longer looks carved -- but the wall of natural cave air beside it
     * is exactly as open as it was, and closing it is exactly as necessary. Seeding from the carve rather
     * than from what happens to be standing in it afterwards is the whole of the roof-hole fix; this list is
     * that same idea stated from the seal's side.
     *
     * <p>Roles NOT in the list are the ones that never occupy a carved cell: the floor materials at
     * {@code landingY - 1} and below (cushion base, shelf, ledge, shore ice, lake bed, floor seal) and the
     * two openings.
     */
    private static final Set<HiddenChamberPlan.CellRole> STANDS_IN_CARVED_INTERIOR = java.util.EnumSet.of(
            HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED,
            HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE,
            HiddenChamberPlan.CellRole.ICICLE_ICE,
            HiddenChamberPlan.CellRole.FOSSIL_BONE,
            HiddenChamberPlan.CellRole.FROST_MOTE,
            HiddenChamberPlan.CellRole.LANTERN,
            HiddenChamberPlan.CellRole.CACHE_CHEST,
            HiddenChamberPlan.CellRole.DEBRIS_WOOD,
            HiddenChamberPlan.CellRole.DEBRIS_BONE,
            HiddenChamberPlan.CellRole.LAKE_WATER,
            HiddenChamberPlan.CellRole.FLOE_ICE,
            HiddenChamberPlan.CellRole.CUSHION_POWDER);

    @Test
    void theInteriorIsStillSealed() {
        List<Sample> sweep = sweep();
        assertTrue(sweep.size() >= MIN_SWEEP_PLANS, "sweep size " + sweep.size());
        for (Sample sample : sweep) {
            List<HiddenChamberPlan.Cell> seals =
                    sample.plan().writesWithRole(HiddenChamberPlan.CellRole.WALL_SEAL);
            assertFalse(seals.isEmpty(),
                    sample + ": narrowing the seal seed set must not switch sealing off -- the interior "
                            + "still closes the natural cave air it touches");
            Set<Long> interior = new LinkedHashSet<>();
            for (HiddenChamberPlan.Cell cell : sample.plan().writes()) {
                if (cell.role().isSealedInterior()
                        || STANDS_IN_CARVED_INTERIOR.contains(cell.role())) {
                    interior.add(key(cell.x(), cell.y(), cell.z()));
                }
            }
            for (HiddenChamberPlan.Cell seal : seals) {
                boolean touchesInterior = false;
                for (int[] offset : ORTHOGONAL) {
                    if (interior.contains(
                            key(seal.x() + offset[0], seal.y() + offset[1], seal.z() + offset[2]))) {
                        touchesInterior = true;
                        break;
                    }
                }
                assertTrue(touchesInterior,
                        sample + ": wall seal " + seal + " touches nothing standing inside the encounter's "
                                + "enclosed interior, so nothing in the encounter asked for it");
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 4. The exact case the adversarial sweep reproduced                                                    */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * Seed 1, chunk (0,0), {@link HiddenChamberTerrainFixtures#roomy()}, hashed theme: the case the sweeper
     * ran against the compiled planner. It reported {@code WALL_SEAL} at {@code y + 1} over all three mouth
     * cells and a capped exit portal at {@code y = 38}. Both are pinned here directly, so the repro itself is
     * a standing test and not a paragraph in a report.
     */
    @Test
    void theSweeperReproHasAnOpenMouthAndAnOpenPortal() {
        HiddenChamberPlan.PlanResult result =
                HiddenChamberPlan.plan(1L, 0, 0, HiddenChamberTerrainFixtures.roomy(), null);
        assertTrue(result.isAccepted(), "the sweeper's repro terrain must accept: " + result.detail());
        HiddenChamberPlan.Plan plan = result.accepted();

        Set<Long> seals = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(HiddenChamberPlan.CellRole.WALL_SEAL)) {
            seals.add(key(cell.x(), cell.y(), cell.z()));
        }
        Set<Long> written = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            written.add(key(cell.x(), cell.y(), cell.z()));
        }

        assertTrue(plan.mouthCells().size() >= HiddenChamberPlan.MOUTH_MIN_CELLS);
        for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
            assertFalse(written.contains(key(mouth.x(), mouth.y() + 1, mouth.z())),
                    "seed 1 chunk (0,0): mouth " + mouth + " is capped");
        }

        List<HiddenChamberPlan.Cell> portal =
                plan.writesWithRole(HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR);
        assertFalse(portal.isEmpty());
        int lowestPortalY = Integer.MAX_VALUE;
        int highestPortalY = Integer.MIN_VALUE;
        Set<Long> portalColumns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : portal) {
            lowestPortalY = Math.min(lowestPortalY, cell.y());
            highestPortalY = Math.max(highestPortalY, cell.y());
            portalColumns.add(columnKey(cell.x(), cell.z()));
        }
        for (HiddenChamberPlan.Cell seal : plan.writesWithRole(HiddenChamberPlan.CellRole.WALL_SEAL)) {
            boolean inPortalColumn = portalColumns.contains(columnKey(seal.x(), seal.z()));
            assertFalse(inPortalColumn && seal.y() >= lowestPortalY,
                    "seed 1 chunk (0,0): wall seal " + seal + " stands in the portal's own cave-facing "
                            + "column -- the bore must break out, not stop in packed ice");
        }
        // The sweeper's exact finding: the portal's cave-facing top was capped at y = 38.
        for (HiddenChamberPlan.Cell cell : portal) {
            assertFalse(written.contains(key(cell.x(), highestPortalY + 1, cell.z())),
                    "seed 1 chunk (0,0): the portal column of " + cell + " is capped at y="
                            + (highestPortalY + 1));
        }
        assertFalse(seals.isEmpty(), "the interior of this chamber is still sealed");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Helpers                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    private static final int[][] ORTHOGONAL = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /** One accepted plan plus the inputs that produced it, so a failure names the case it came from. */
    private record Sample(long seed, int chunkX, int chunkZ, HiddenChamberPlan.Theme theme,
                          HiddenChamberPlan.Plan plan) {
        @Override
        public String toString() {
            return "seed=" + seed + " chunk=(" + chunkX + "," + chunkZ + ") theme=" + theme;
        }
    }

    private static List<Sample> sweep() {
        List<Sample> samples = new ArrayList<>();
        for (long seed : SEEDS) {
            for (int[] chunk : CHUNKS) {
                for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
                    HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(
                            seed, chunk[0], chunk[1], HiddenChamberTerrainFixtures.roomy(), theme);
                    assertTrue(result.isAccepted(), "seed=" + seed + " chunk=(" + chunk[0] + "," + chunk[1]
                            + ") theme=" + theme + " must accept on a roomy terrain: " + result.detail());
                    samples.add(new Sample(seed, chunk[0], chunk[1], theme, result.accepted()));
                }
            }
        }
        return samples;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 42) | ((long) (y + 4096) << 21) | (z + 4096);
    }

    private static long columnKey(int x, int z) {
        return ((long) (x + 4096) << 32) | (z + 4096);
    }
}
