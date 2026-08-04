package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The support laws of the chamber's own shell: nothing the planner writes may end up attached to air, and
 * nothing it carves may be left open to the natural cave through a hole the DRESSING punched in its own
 * seal obligations.
 *
 * <h2>What this class exists to stop happening again</h2>
 * <ul>
 *   <li><b>Roof holes.</b> {@code dress()} runs before {@code addWallSeals()}, and the seal seed set used to
 *       be read off the roles as they stood AFTER dressing. Every dressing role that overwrites the topmost
 *       {@link HiddenChamberPlan.CellRole#CHAMBER_CLEAR} of a column -- an icicle cluster, an ice column, a
 *       frost mote -- therefore deleted that column from the seed set, and the cell above it never got its
 *       {@link HiddenChamberPlan.CellRole#WALL_SEAL}. The adversarial sweep measured 199 of 600 plans with at
 *       least one open ceiling cell, and 26.2 speleothems are {@code Fallable}: an icicle hung under a hole
 *       falls on the first block update.</li>
 *   <li><b>Frost motes at the ceiling.</b> {@code dressLake} placed every {@link
 *       HiddenChamberPlan.CellRole#FROST_MOTE} at {@code landingY + height - 1} -- a single snow LAYER at the
 *       roof, over open air. All 494 motes the sweep produced were invalid and would pop on first update.</li>
 *   <li><b>Chamber floor over a natural void.</b> The carve starts at {@code landingY} and only the cushion,
 *       the shelf and the lake ever authored anything at {@code landingY - 1}, so every other interior column
 *       stood on whatever the terrain happened to have -- including nothing. Lanterns
 *       ({@code canSurvive} needs {@code canSupportCenter} below) and chests inherited that.</li>
 * </ul>
 *
 * <p>The sweep grid is {@link HiddenChamberTerrainFixtures#roomy()} on purpose: its plateau stands six blocks
 * above the surrounding cave floor, so chamber ceilings genuinely poke into the surrounding cave's air band
 * and the roof-seal obligation is real. A flat cave buries every ceiling in rock and would pass all three
 * laws while proving nothing.
 */
class HiddenChamberInteriorSupportLawTest {

    /** Twelve seeds x nine chunks x three themes = 324 accepted plans. */
    private static final long[] SEEDS = {
        1L, 0x5EED_C0FFEEL, -1234L, 987_654_321L,
        7L, -99L, 424_242L, 0x1234_5678L, -0x5EED_C0FFEEL, 31L, -777_777L, 20_260_803L};
    private static final int[][] CHUNKS = {
        {0, 0}, {4, -9}, {11, 7}, {-13, 21}, {-3, -5}, {28, 14}, {2, 2}, {-40, 8}, {17, -31}};
    private static final int MIN_SWEEP_PLANS = 300;

    /**
     * Roles that stand in the chamber's carved body. The topmost of these in a column IS that column's
     * ceiling, whatever the dressing put there.
     */
    private static final Set<HiddenChamberPlan.CellRole> CHAMBER_BODY = EnumSet.of(
            HiddenChamberPlan.CellRole.CHAMBER_CLEAR,
            HiddenChamberPlan.CellRole.RECESS_CLEAR,
            HiddenChamberPlan.CellRole.ICICLE_ICE,
            HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED,
            HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE,
            HiddenChamberPlan.CellRole.FROST_MOTE);

    /**
     * Roles that are NOT something a player, a lantern or a speleothem can stand on or hang from: the carves
     * plus the three deliberately soft or empty materials. Everything else the planner writes is firm.
     *
     * <p>Deliberately a NEGATIVE list so this law keeps holding when a new firm role is added.
     */
    private static boolean isFirmMaterial(HiddenChamberPlan.CellRole role) {
        return !role.isCarve()
                && role != HiddenChamberPlan.CellRole.MOUTH_COLLAPSE
                && role != HiddenChamberPlan.CellRole.CUSHION_POWDER
                && role != HiddenChamberPlan.CellRole.FROST_MOTE
                && role != HiddenChamberPlan.CellRole.LAKE_WATER
                && role != HiddenChamberPlan.CellRole.LANTERN
                && role != HiddenChamberPlan.CellRole.CACHE_CHEST
                && role != HiddenChamberPlan.CellRole.DEBRIS_WOOD;
    }

    private static boolean isNaturallySolid(HiddenChamberPlan.CellKind kind) {
        return kind == HiddenChamberPlan.CellKind.SOLID_SAFE
                || kind == HiddenChamberPlan.CellKind.SOLID_UNSAFE
                || kind == HiddenChamberPlan.CellKind.BEDROCK
                || kind == HiddenChamberPlan.CellKind.BLOCK_ENTITY;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. No roof hole: nothing dressing writes may delete a seal obligation                                 */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void noDressedCeilingCellEverLeavesUnplannedNaturalAirDirectlyAboveIt() {
        List<Sample> sweep = sweep();
        assertTrue(sweep.size() >= MIN_SWEEP_PLANS,
                "the sweep must exercise at least " + MIN_SWEEP_PLANS + " accepted plans, got " + sweep.size());
        int ceilings = 0;
        for (Sample sample : sweep) {
            Map<Long, HiddenChamberPlan.CellRole> written = writtenCells(sample.plan());
            for (Map.Entry<Long, Integer> ceiling : chamberCeilings(sample.plan()).entrySet()) {
                int x = columnX(ceiling.getKey());
                int z = columnZ(ceiling.getKey());
                int above = ceiling.getValue() + 1;
                ceilings++;
                if (written.containsKey(key(x, above, z))) {
                    continue;
                }
                assertFalse(sample.terrain().cell(x, above, z) == HiddenChamberPlan.CellKind.AIR,
                        sample + ": the chamber ceiling at (" + x + "," + ceiling.getValue() + "," + z
                                + ") carries " + written.get(key(x, ceiling.getValue(), z)) + " and the cell "
                                + "above it is unplanned natural AIR -- the dressing deleted that column's "
                                + "wall-seal obligation, so the roof is open to the cave and anything hanging "
                                + "from it is attached to nothing");
            }
        }
        assertTrue(ceilings > 0, "the sweep must actually have chamber ceilings to check");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. Every icicle hangs from something sturdy                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * 26.2's {@code SpeleothemBlock} implements {@code Fallable} and its {@code canSurvive} for
     * {@code TIP_DIRECTION=DOWN} asks for a sturdy face on the block ABOVE. An icicle whose stack top has air
     * over it is a falling block waiting for the first neighbour update.
     */
    @Test
    void everyIcicleStackHangsFromAnAuthoredOrNaturallySolidCell() {
        List<Sample> sweep = sweep();
        int stacks = 0;
        for (Sample sample : sweep) {
            Map<Long, HiddenChamberPlan.CellRole> written = writtenCells(sample.plan());
            Map<Long, Integer> tops = new HashMap<>();
            for (HiddenChamberPlan.Cell cell
                    : sample.plan().writesWithRole(HiddenChamberPlan.CellRole.ICICLE_ICE)) {
                tops.merge(columnKey(cell.x(), cell.z()), cell.y(), Math::max);
            }
            for (Map.Entry<Long, Integer> top : tops.entrySet()) {
                int x = columnX(top.getKey());
                int z = columnZ(top.getKey());
                int above = top.getValue() + 1;
                stacks++;
                HiddenChamberPlan.CellRole role = written.get(key(x, above, z));
                boolean sturdy = role != null
                        ? isFirmMaterial(role)
                        : isNaturallySolid(sample.terrain().cell(x, above, z));
                assertTrue(sturdy,
                        sample + ": the icicle stack topping out at (" + x + "," + top.getValue() + "," + z
                                + ") hangs from " + (role != null ? role.name()
                                        : "natural " + sample.terrain().cell(x, above, z))
                                + " -- a 26.2 speleothem with no sturdy face above it FALLS");
            }
        }
        assertTrue(stacks > 0, "the sweep must actually contain icicle clusters");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 3. Every frost mote rests on something                                                                */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void everyFrostMoteRestsOnAnAuthoredOrNaturallySolidCell() {
        List<Sample> sweep = sweep();
        int motes = 0;
        for (Sample sample : sweep) {
            Map<Long, HiddenChamberPlan.CellRole> written = writtenCells(sample.plan());
            for (HiddenChamberPlan.Cell mote
                    : sample.plan().writesWithRole(HiddenChamberPlan.CellRole.FROST_MOTE)) {
                motes++;
                HiddenChamberPlan.CellRole below = written.get(key(mote.x(), mote.y() - 1, mote.z()));
                boolean supported = below != null
                        ? isFirmMaterial(below)
                        : isNaturallySolid(sample.terrain().cell(mote.x(), mote.y() - 1, mote.z()));
                assertTrue(supported,
                        sample + ": the frost mote at " + mote + " stands on "
                                + (below != null ? below.name()
                                        : "natural " + sample.terrain().cell(mote.x(), mote.y() - 1, mote.z()))
                                + " -- a snow layer with nothing under it pops on the first block update");
            }
        }
        assertTrue(motes > 0, "the sweep must actually contain frost motes");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 4. The chamber floor is never a hole over a natural void                                              */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * The sweep grid's terrain is solid under the chamber, so it can never see this defect. This case digs a
     * void band through the whole fixture at every Y a chamber floor can land on, which is the terrain a real
     * glacial cave system routinely offers, and then asks the write list whether the room still has a floor.
     */
    @Test
    void everyInteriorColumnEndsOnAFirmFloorEvenWhenTheTerrainUnderItIsHollow() {
        int checked = 0;
        for (long seed : SEEDS) {
            for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
                HiddenChamberTerrainFixtures.Mutable terrain =
                        HiddenChamberTerrainFixtures.roomy().voidBand(HOLLOW_MIN_Y, HOLLOW_MAX_Y);
                HiddenChamberPlan.PlanResult result =
                        HiddenChamberPlan.plan(seed, 0, 0, terrain, theme);
                if (!result.isAccepted()) {
                    continue;
                }
                checked++;
                HiddenChamberPlan.Plan plan = result.accepted();
                Map<Long, HiddenChamberPlan.CellRole> written = writtenCells(plan);
                String label = "seed=" + seed + " theme=" + theme;

                Set<Long> interiorColumns = new HashSet<>();
                for (HiddenChamberPlan.Cell cell : plan.writes()) {
                    if (CHAMBER_BODY.contains(cell.role()) && cell.y() == plan.landingY()) {
                        interiorColumns.add(columnKey(cell.x(), cell.z()));
                    }
                }
                for (HiddenChamberPlan.Cell cell : plan.writes()) {
                    if (cell.role() == HiddenChamberPlan.CellRole.LANTERN
                            || cell.role() == HiddenChamberPlan.CellRole.CACHE_CHEST
                            || cell.role() == HiddenChamberPlan.CellRole.DEBRIS_BONE
                            || cell.role() == HiddenChamberPlan.CellRole.DEBRIS_WOOD
                            || cell.role() == HiddenChamberPlan.CellRole.FOSSIL_BONE) {
                        interiorColumns.add(columnKey(cell.x(), cell.z()));
                    }
                }
                assertTrue(interiorColumns.size() > 4, label + ": too few interior columns to be a room");

                for (long column : interiorColumns) {
                    int x = columnX(column);
                    int z = columnZ(column);
                    int floorY = plan.landingY() - 1;
                    HiddenChamberPlan.CellRole role = written.get(key(x, floorY, z));
                    boolean firm = role != null
                            ? isFirmMaterial(role)
                            : isNaturallySolid(terrain.cell(x, floorY, z));
                    assertTrue(firm,
                            label + ": the interior column (" + x + "," + z + ") stands on "
                                    + (role != null ? role.name() : "natural " + terrain.cell(x, floorY, z))
                                    + " at y=" + floorY + " -- the chamber floor is a hole over a natural void");
                }
            }
        }
        assertTrue(checked >= 12, "the hollow-terrain case must accept at least 12 plans, got " + checked);
    }

    /**
     * The band of Y values a chamber floor ({@code landingY - 1}) can land on over {@link
     * HiddenChamberTerrainFixtures#roomy()}: its plateau floor is 40 and the legal drop band is 12..20, so
     * the landing is 20..28 and the floor under it is 19..27. Hollowing exactly that band puts a natural void
     * under every interior column of every legal plan, whatever drop the planner picks.
     */
    private static final int HOLLOW_MIN_Y =
            HiddenChamberTerrainFixtures.ROOMY_FLOOR_Y - HiddenChamberPlan.DROP_MAX - 1;
    private static final int HOLLOW_MAX_Y =
            HiddenChamberTerrainFixtures.ROOMY_FLOOR_Y - HiddenChamberPlan.DROP_MIN - 1;

    /* ---------------------------------------------------------------------------------------------------- */
    /* Helpers                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    /** The topmost chamber-body cell of every column the chamber occupies. */
    private static Map<Long, Integer> chamberCeilings(HiddenChamberPlan.Plan plan) {
        Map<Long, Integer> ceilings = new HashMap<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (CHAMBER_BODY.contains(cell.role())) {
                ceilings.merge(columnKey(cell.x(), cell.z()), cell.y(), Math::max);
            }
        }
        return ceilings;
    }

    private static Map<Long, HiddenChamberPlan.CellRole> writtenCells(HiddenChamberPlan.Plan plan) {
        Map<Long, HiddenChamberPlan.CellRole> written = new HashMap<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            written.put(key(cell.x(), cell.y(), cell.z()), cell.role());
        }
        return written;
    }

    /** One accepted plan plus the terrain it was planned over, so a failure can re-read the natural block. */
    private record Sample(long seed, int chunkX, int chunkZ, HiddenChamberPlan.Theme theme,
                          HiddenChamberPlan.Plan plan, HiddenChamberTerrainFixtures.Mutable terrain) {
        @Override
        public String toString() {
            return "seed=" + seed + " chunk=(" + chunkX + "," + chunkZ + ") theme=" + theme;
        }
    }

    private static List<Sample> cachedSweep;

    private static List<Sample> sweep() {
        if (cachedSweep != null) {
            return cachedSweep;
        }
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.roomy();
        List<Sample> samples = new ArrayList<>();
        for (long seed : SEEDS) {
            for (int[] chunk : CHUNKS) {
                for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
                    HiddenChamberPlan.PlanResult result =
                            HiddenChamberPlan.plan(seed, chunk[0], chunk[1], terrain, theme);
                    assertTrue(result.isAccepted(), "seed=" + seed + " chunk=(" + chunk[0] + "," + chunk[1]
                            + ") theme=" + theme + " must accept on a roomy terrain: " + result.detail());
                    samples.add(new Sample(seed, chunk[0], chunk[1], theme, result.accepted(), terrain));
                }
            }
        }
        assertEquals(SEEDS.length * CHUNKS.length * HiddenChamberPlan.Theme.values().length, samples.size());
        cachedSweep = samples;
        return samples;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 42) | ((long) (y + 4096) << 21) | (z + 4096);
    }

    private static long columnKey(int x, int z) {
        return ((long) (x + 4096) << 32) | (z + 4096);
    }

    private static int columnX(long columnKey) {
        return (int) (columnKey >> 32) - 4096;
    }

    private static int columnZ(long columnKey) {
        return (int) (columnKey & 0xFFFFFFFFL) - 4096;
    }
}
