package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-theme write-list assertions with every theme FORCED, going beyond {@code HiddenChamberPlanTest}'s own
 * thin per-theme smoke checks: exact ice-column and icicle-cluster COUNTS (not just presence) and the
 * fossil rarity ratio for {@code ICE_CATHEDRAL}; shore raggedness, floe grouping, and ledge connectivity
 * for {@code FRIGID_LAKE}; and total role-vocabulary closure for {@code LOST_EXPEDITION}.
 */
class HiddenChamberThemeDressingSweepTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] NEIGHBOURHOOD_8 = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    /* ---------------------------------------------------------------------------------------------------- */
    /* ICE_CATHEDRAL                                                                                         */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void iceCathedralCarriesTwoToFourColumnsAtLeastThreeIcicleClustersAndExactlyOneNineCellRecess() {
        HiddenChamberPlan.Plan plan = acceptedPlan(HiddenChamberPlan.Theme.ICE_CATHEDRAL, CHUNK_X, CHUNK_Z);

        Set<Long> iceColumns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (cell.role() == HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED
                    || cell.role() == HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE) {
                iceColumns.add(columnKey(cell.x(), cell.z()));
            }
        }
        assertTrue(iceColumns.size() >= HiddenChamberPlan.CATHEDRAL_MIN_ICE_COLUMNS
                        && iceColumns.size() <= HiddenChamberPlan.CATHEDRAL_MAX_ICE_COLUMNS,
                "ice column count " + iceColumns.size());

        Set<Long> icicleColumns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(HiddenChamberPlan.CellRole.ICICLE_ICE)) {
            icicleColumns.add(columnKey(cell.x(), cell.z()));
        }
        assertTrue(icicleColumns.size() >= HiddenChamberPlan.CATHEDRAL_MIN_ICICLE_CLUSTERS
                        && icicleColumns.size() <= HiddenChamberPlan.CATHEDRAL_MAX_ICICLE_CLUSTERS,
                "icicle cluster (distinct column) count " + icicleColumns.size());

        List<HiddenChamberPlan.Cell> recess = plan.writesWithRole(HiddenChamberPlan.CellRole.RECESS_CLEAR);
        Set<Long> recessColumns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : recess) {
            recessColumns.add(columnKey(cell.x(), cell.z()));
        }
        int expectedRecessColumns = HiddenChamberPlan.RECESS_SPAN * HiddenChamberPlan.RECESS_SPAN;
        assertEquals(expectedRecessColumns, recessColumns.size(),
                "the recess is exactly one " + HiddenChamberPlan.RECESS_SPAN + "x" + HiddenChamberPlan.RECESS_SPAN
                        + " lobe: " + recessColumns.size() + " columns");
        assertEquals(1, connectedComponents(recessColumns).size(), "the recess is exactly one lobe, not several");
        for (long key : recessColumns) {
            assertFalse(iceColumns.contains(key), "the recess stays undressed of ice columns");
            assertFalse(icicleColumns.contains(key), "the recess stays undressed of icicles");
        }
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(HiddenChamberPlan.CellRole.FOSSIL_BONE)) {
            assertFalse(recessColumns.contains(columnKey(cell.x(), cell.z())), "the recess stays undressed of fossils");
        }
    }

    @Test
    void iceCathedralFossilRatioIsRoughlyOneInSixAcrossManyDistinctChunks() {
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.roomy();
        int total = 0;
        int withFossil = 0;
        for (int chunkX = 0; chunkX < 12; chunkX++) {
            for (int chunkZ = 0; chunkZ < 12; chunkZ++) {
                HiddenChamberPlan.PlanResult result =
                        HiddenChamberPlan.plan(SEED, chunkX, chunkZ, terrain, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
                assertTrue(result.isAccepted(), "chunk (" + chunkX + "," + chunkZ + ") must accept: " + result.detail());
                total++;
                if (!result.accepted().writesWithRole(HiddenChamberPlan.CellRole.FOSSIL_BONE).isEmpty()) {
                    withFossil++;
                }
            }
        }
        assertTrue(total >= 120, "the sweep must cover at least 120 distinct chunks, saw " + total);
        double ratio = (double) withFossil / total;
        assertTrue(ratio >= 0.05 && ratio <= 0.35,
                "fossil ratio " + ratio + " (" + withFossil + "/" + total + ") should sit near one in "
                        + HiddenChamberPlan.CATHEDRAL_FOSSIL_MODULUS + " within a generous band");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* FRIGID_LAKE                                                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void frigidLakeCarriesARaggedShoreTwoToFourCellFloesAndAContinuousLedgeWalk() {
        HiddenChamberPlan.Plan plan = acceptedPlan(HiddenChamberPlan.Theme.FRIGID_LAKE, CHUNK_X, CHUNK_Z);

        Set<Long> water = columnsOf(plan, HiddenChamberPlan.CellRole.LAKE_WATER);
        Set<Long> shore = columnsOf(plan, HiddenChamberPlan.CellRole.SHORE_ICE);
        Set<Long> chamberClear = columnsOf(plan, HiddenChamberPlan.CellRole.CHAMBER_CLEAR);

        // Every shore cell sits directly (eight-neighbourhood) against open water: never more than one
        // cell wide from the water's own edge, so it is always "one to two wide" by the authoring law.
        for (long shoreKey : shore) {
            assertTrue(touchesAny(shoreKey, water), "shore ice " + describe(shoreKey) + " must border open water");
        }
        // Raggedness: at least one water-adjacent boundary cell is neither shore nor water -- a gap in the
        // ring, not an unbroken seawall.
        boolean gapFound = false;
        for (long waterKey : water) {
            for (long neighbour : neighbours8(waterKey)) {
                if (!water.contains(neighbour) && !shore.contains(neighbour) && chamberClear.contains(neighbour)) {
                    gapFound = true;
                }
            }
        }
        assertTrue(gapFound, "the shore ring must carry at least one gap");

        // Floes: cardinally-connected FLOE_ICE components, each two to four cells, at least two of them.
        Set<Long> floeCells = columnsOf(plan, HiddenChamberPlan.CellRole.FLOE_ICE);
        List<Set<Long>> floeComponents = connectedComponents(floeCells);
        assertTrue(floeComponents.size() >= HiddenChamberPlan.LAKE_MIN_FLOES,
                "expected at least " + HiddenChamberPlan.LAKE_MIN_FLOES + " floes, saw " + floeComponents.size());
        for (Set<Long> floe : floeComponents) {
            assertTrue(floe.size() >= HiddenChamberPlan.LAKE_FLOE_MIN_CELLS
                            && floe.size() <= HiddenChamberPlan.LAKE_FLOE_MAX_CELLS,
                    "floe size " + floe.size() + " outside " + HiddenChamberPlan.LAKE_FLOE_MIN_CELLS + ".."
                            + HiddenChamberPlan.LAKE_FLOE_MAX_CELLS);
        }

        // A continuous dry ledge walk: LEDGE_FIRM forms one connected run that touches the shelf at one end
        // and a corridor entrance at the other.
        Set<Long> ledge = columnsOf(plan, HiddenChamberPlan.CellRole.LEDGE_FIRM);
        assertFalse(ledge.isEmpty(), "a frigid lake must author a dry ledge");
        List<Set<Long>> ledgeComponents = connectedComponents(ledge);
        assertEquals(1, ledgeComponents.size(), "the ledge must be one continuous walk, not several fragments");

        Set<Long> shelf = columnsOf(plan, HiddenChamberPlan.CellRole.SHELF_FIRM);
        Set<Long> corridorEntrance = new LinkedHashSet<>();
        corridorEntrance.addAll(columnsOf(plan, HiddenChamberPlan.CellRole.CORRIDOR_FLOOR));
        corridorEntrance.addAll(columnsOf(plan, HiddenChamberPlan.CellRole.CORRIDOR_CLEAR));
        boolean touchesShelf = false;
        boolean touchesCorridor = false;
        for (long ledgeKey : ledge) {
            if (touchesAny(ledgeKey, shelf)) {
                touchesShelf = true;
            }
            if (touchesAny(ledgeKey, corridorEntrance)) {
                touchesCorridor = true;
            }
        }
        assertTrue(touchesShelf, "the ledge walk must start beside the cushion's own shelf");
        assertTrue(touchesCorridor, "the ledge walk must reach a corridor entrance");

        assertTrue(plan.writesWithRole(HiddenChamberPlan.CellRole.FROST_MOTE).size()
                <= HiddenChamberPlan.LAKE_MAX_FROST_MOTES);
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(HiddenChamberPlan.CellRole.LAKE_WATER)) {
            assertTrue(cell.y() >= HiddenChamberPlan.MIN_AUTHORED_Y, "lake water " + cell + " below the world floor");
        }
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(HiddenChamberPlan.CellRole.LAKE_BED)) {
            assertTrue(cell.y() >= HiddenChamberPlan.MIN_AUTHORED_Y, "lake bed " + cell + " below the world floor");
        }
    }

    @Test
    void frigidLakeOpenCentreCarriesNoDressingDirectlyAboveIt() {
        HiddenChamberPlan.Plan plan = acceptedPlan(HiddenChamberPlan.Theme.FRIGID_LAKE, CHUNK_X, CHUNK_Z);
        Set<Long> water = columnsOf(plan, HiddenChamberPlan.CellRole.LAKE_WATER);
        Set<Long> dressedAbove = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (cell.y() > plan.landingY()
                    && (cell.role() == HiddenChamberPlan.CellRole.FLOE_ICE
                            || cell.role() == HiddenChamberPlan.CellRole.FROST_MOTE
                            || cell.role() == HiddenChamberPlan.CellRole.ICICLE_ICE
                            || cell.role() == HiddenChamberPlan.CellRole.SHORE_ICE)) {
                dressedAbove.add(columnKey(cell.x(), cell.z()));
            }
        }
        long openSquareKey = findOpenSquare(water, dressedAbove, HiddenChamberPlan.LAKE_OPEN_CENTRE_SPAN);
        assertTrue(openSquareKey != Long.MIN_VALUE,
                "expected a " + HiddenChamberPlan.LAKE_OPEN_CENTRE_SPAN + "x"
                        + HiddenChamberPlan.LAKE_OPEN_CENTRE_SPAN + " open water square with no dressing above it");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* LOST_EXPEDITION                                                                                       */
    /* ---------------------------------------------------------------------------------------------------- */

    private static final Set<HiddenChamberPlan.CellRole> LOST_EXPEDITION_ALLOWED_ROLES = Set.of(
            HiddenChamberPlan.CellRole.MOUTH_COLLAPSE, HiddenChamberPlan.CellRole.SHAFT_CLEAR,
            HiddenChamberPlan.CellRole.CHAMBER_CLEAR, HiddenChamberPlan.CellRole.CORRIDOR_CLEAR,
            HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR, HiddenChamberPlan.CellRole.CUSHION_POWDER,
            HiddenChamberPlan.CellRole.CUSHION_BASE, HiddenChamberPlan.CellRole.SHELF_FIRM,
            HiddenChamberPlan.CellRole.CORRIDOR_FLOOR, HiddenChamberPlan.CellRole.WALL_SEAL,
            HiddenChamberPlan.CellRole.LANTERN, HiddenChamberPlan.CellRole.CACHE_CHEST,
            HiddenChamberPlan.CellRole.DEBRIS_WOOD, HiddenChamberPlan.CellRole.DEBRIS_BONE);

    @Test
    void lostExpeditionRoleVocabularyIsClosedOverExactlyItsOwnDressing() {
        HiddenChamberPlan.Plan plan = acceptedPlan(HiddenChamberPlan.Theme.LOST_EXPEDITION, CHUNK_X, CHUNK_Z);
        assertEquals(HiddenChamberPlan.EXPEDITION_CACHE_CHESTS,
                plan.writesWithRole(HiddenChamberPlan.CellRole.CACHE_CHEST).size());
        assertEquals(HiddenChamberPlan.EXPEDITION_LANTERNS,
                plan.writesWithRole(HiddenChamberPlan.CellRole.LANTERN).size());
        int bones = plan.writesWithRole(HiddenChamberPlan.CellRole.DEBRIS_BONE).size();
        assertTrue(bones >= HiddenChamberPlan.EXPEDITION_MIN_BONE_DEBRIS
                && bones <= HiddenChamberPlan.EXPEDITION_MAX_BONE_DEBRIS, "bone debris " + bones);
        int wood = plan.writesWithRole(HiddenChamberPlan.CellRole.DEBRIS_WOOD).size();
        assertTrue(wood >= HiddenChamberPlan.EXPEDITION_MIN_WOOD_DEBRIS
                && wood <= HiddenChamberPlan.EXPEDITION_MAX_WOOD_DEBRIS, "wood debris " + wood);

        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            assertTrue(LOST_EXPEDITION_ALLOWED_ROLES.contains(cell.role()),
                    "unexpected role " + cell.role() + " on a lost-expedition chamber: no spawner-like or "
                            + "reward role beyond lantern/chest/debris may appear");
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Helpers                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    private static HiddenChamberPlan.Plan acceptedPlan(HiddenChamberPlan.Theme theme, int chunkX, int chunkZ) {
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.roomy();
        HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(SEED, chunkX, chunkZ, terrain, theme);
        assertTrue(result.isAccepted(), theme + " must accept on a roomy terrain: " + result.detail());
        return result.accepted();
    }

    private static Set<Long> columnsOf(HiddenChamberPlan.Plan plan, HiddenChamberPlan.CellRole role) {
        Set<Long> columns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(role)) {
            columns.add(columnKey(cell.x(), cell.z()));
        }
        return columns;
    }

    private static long columnKey(int x, int z) {
        return ((long) (x + 4096) << 32) | (z + 4096);
    }

    private static String describe(long key) {
        int x = (int) (key >> 32) - 4096;
        int z = (int) (key & 0xFFFFFFFFL) - 4096;
        return "(" + x + "," + z + ")";
    }

    private static List<Long> neighbours8(long key) {
        int x = (int) (key >> 32) - 4096;
        int z = (int) (key & 0xFFFFFFFFL) - 4096;
        List<Long> result = new ArrayList<>(8);
        for (int[] offset : NEIGHBOURHOOD_8) {
            result.add(columnKey(x + offset[0], z + offset[1]));
        }
        return result;
    }

    private static boolean touchesAny(long key, Set<Long> targets) {
        for (long neighbour : neighbours8(key)) {
            if (targets.contains(neighbour)) {
                return true;
            }
        }
        return false;
    }

    /** Cardinally-connected components of a column set, in stable iteration order. */
    private static List<Set<Long>> connectedComponents(Set<Long> columns) {
        Set<Long> remaining = new LinkedHashSet<>(columns);
        List<Set<Long>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            remaining.remove(seed);
            Set<Long> component = new LinkedHashSet<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(seed);
            component.add(seed);
            while (!queue.isEmpty()) {
                long current = queue.removeFirst();
                int x = (int) (current >> 32) - 4096;
                int z = (int) (current & 0xFFFFFFFFL) - 4096;
                for (int[] offset : CARDINALS) {
                    long neighbour = columnKey(x + offset[0], z + offset[1]);
                    if (remaining.remove(neighbour)) {
                        component.add(neighbour);
                        queue.addLast(neighbour);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    /** First column key whose {@code span x span} block is entirely in {@code columns} and never {@code excluded}. */
    private static long findOpenSquare(Set<Long> columns, Set<Long> excluded, int span) {
        for (long key : columns) {
            int x = (int) (key >> 32) - 4096;
            int z = (int) (key & 0xFFFFFFFFL) - 4096;
            boolean complete = true;
            for (int dx = 0; dx < span && complete; dx++) {
                for (int dz = 0; dz < span && complete; dz++) {
                    long member = columnKey(x + dx, z + dz);
                    if (!columns.contains(member) || excluded.contains(member)) {
                        complete = false;
                    }
                }
            }
            if (complete) {
                return key;
            }
        }
        return Long.MIN_VALUE;
    }
}
