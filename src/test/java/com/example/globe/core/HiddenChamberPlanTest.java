package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM smoke laws for the hidden glacial chamber plan layer. The matrix is deliberately thin: it pins the
 * laws a later pass must not silently break (determinism, the drop and envelope bands, fail-closed biome
 * checking, the guaranteed exit, and the single occurrence gate) and leaves exhaustive coverage to the
 * dedicated test-writer pass.
 */
class HiddenChamberPlanTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. Determinism                                                                                        */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void identicalInputsProduceAnIdenticalWriteList() {
        HiddenChamberPlan.PlanResult first =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, validTerrain(), null);
        HiddenChamberPlan.PlanResult second =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, validTerrain(), null);
        assertTrue(first.isAccepted(), "the reference terrain must accept: " + first.detail());
        assertTrue(second.isAccepted());
        assertEquals(first.accepted().writes(), second.accepted().writes());
        assertEquals(first.accepted().theme(), second.accepted().theme());
        assertEquals(first.accepted().variant(), second.accepted().variant());
        assertEquals(first.accepted().direction(), second.accepted().direction());
        assertEquals(first.accepted().landing(), second.accepted().landing());
        assertEquals(first.accepted().exitTerminus(), second.accepted().exitTerminus());
    }

    @Test
    void aDifferentChunkProducesADifferentPlan() {
        HiddenChamberPlan.PlanResult here =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, validTerrain(), null);
        HiddenChamberPlan.PlanResult there =
                HiddenChamberPlan.plan(SEED, CHUNK_X + 1, CHUNK_Z + 3, validTerrain(), null);
        assertTrue(here.isAccepted());
        assertTrue(there.isAccepted(), "the reference terrain must accept: " + there.detail());
        assertNotEquals(here.accepted().writes(), there.accepted().writes());
    }

    @Test
    void writesAreOrderedByRoleRankThenCoordinate() {
        HiddenChamberPlan.Plan plan = acceptedPlan(null);
        List<HiddenChamberPlan.Cell> writes = plan.writes();
        for (int index = 1; index < writes.size(); index++) {
            HiddenChamberPlan.Cell previous = writes.get(index - 1);
            HiddenChamberPlan.Cell current = writes.get(index);
            assertTrue(compare(previous, current) < 0,
                    "write list must be strictly ordered at " + index);
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. Themes                                                                                             */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void lostExpeditionCarriesExactlyOneCacheChestAndNoHighTierPrize() {
        HiddenChamberPlan.Plan plan = acceptedPlan(HiddenChamberPlan.Theme.LOST_EXPEDITION);
        assertEquals(HiddenChamberPlan.Theme.LOST_EXPEDITION, plan.theme());
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
        assertTrue(plan.writesWithRole(HiddenChamberPlan.CellRole.LAKE_WATER).isEmpty());
    }

    @Test
    void frigidLakeKeepsAFourByFourOfOpenUndressedWater() {
        HiddenChamberPlan.Plan plan = acceptedPlan(HiddenChamberPlan.Theme.FRIGID_LAKE);
        assertEquals(HiddenChamberPlan.Theme.FRIGID_LAKE, plan.theme());
        Set<Long> water = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(HiddenChamberPlan.CellRole.LAKE_WATER)) {
            water.add(columnKey(cell.x(), cell.z()));
        }
        Set<Long> dressed = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (cell.role() == HiddenChamberPlan.CellRole.FLOE_ICE
                    || cell.role() == HiddenChamberPlan.CellRole.FROST_MOTE
                    || cell.role() == HiddenChamberPlan.CellRole.ICICLE_ICE) {
                dressed.add(columnKey(cell.x(), cell.z()));
            }
        }
        assertFalse(plan.writesWithRole(HiddenChamberPlan.CellRole.LAKE_BED).isEmpty());
        assertTrue(plan.writesWithRole(HiddenChamberPlan.CellRole.FLOE_ICE).size()
                >= HiddenChamberPlan.LAKE_MIN_FLOES * HiddenChamberPlan.LAKE_FLOE_MIN_CELLS,
                "a frigid lake carries at least two floes");
        assertFalse(plan.writesWithRole(HiddenChamberPlan.CellRole.LEDGE_FIRM).isEmpty(),
                "a frigid lake must have a dry ledge route");
        assertTrue(plan.writesWithRole(HiddenChamberPlan.CellRole.FROST_MOTE).size()
                <= HiddenChamberPlan.LAKE_MAX_FROST_MOTES);
        assertTrue(hasOpenSquare(water, dressed, HiddenChamberPlan.LAKE_OPEN_CENTRE_SPAN),
                "a frigid lake keeps a four-by-four of undressed open water");
    }

    @Test
    void iceCathedralKeepsOneUndressedRecessLobe() {
        HiddenChamberPlan.Plan plan = acceptedPlan(HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertEquals(HiddenChamberPlan.Theme.ICE_CATHEDRAL, plan.theme());
        List<HiddenChamberPlan.Cell> recess =
                plan.writesWithRole(HiddenChamberPlan.CellRole.RECESS_CLEAR);
        assertFalse(recess.isEmpty(), "a cathedral always keeps a recess");
        Set<Long> recessColumns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : recess) {
            recessColumns.add(columnKey(cell.x(), cell.z()));
        }
        assertTrue(hasOpenSquare(recessColumns, Set.of(), HiddenChamberPlan.RECESS_SPAN),
                "the recess floor is at least three by three");
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (cell.role() == HiddenChamberPlan.CellRole.ICICLE_ICE
                    || cell.role() == HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE
                    || cell.role() == HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED
                    || cell.role() == HiddenChamberPlan.CellRole.FOSSIL_BONE) {
                assertFalse(recessColumns.contains(columnKey(cell.x(), cell.z())),
                        "the recess stays dark and undressed");
            }
        }
        int iceColumns = plan.writesWithRole(HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED).size()
                + plan.writesWithRole(HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE).size();
        assertTrue(iceColumns > 0, "a cathedral carries ice columns");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 3. Drop and world-floor law                                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void terrainThatOnlyAllowsAShallowDropAboveTheWorldFloorIsRejected() {
        Terrain terrain = validTerrain();
        terrain.fillFloor(HiddenChamberPlan.MOUTH_FLOOR_MIN_Y, HiddenChamberPlan.MOUTH_FLOOR_MIN_Y);
        terrain.bedrockTop = 5;
        HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, null);
        assertFalse(result.isAccepted());
        assertEquals(HiddenChamberPlan.Rejection.UNSAFE_VOLUME, result.rejection());
        assertNull(result.accepted());
    }

    @Test
    void acceptedPlansLandAboveTheWorldFloorAndNeverAuthorBelowIt() {
        for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
            HiddenChamberPlan.Plan plan = acceptedPlan(theme);
            assertTrue(plan.landingY() >= HiddenChamberPlan.MIN_LANDING_Y,
                    theme + " landing " + plan.landingY());
            assertTrue(plan.drop() >= HiddenChamberPlan.DROP_MIN
                    && plan.drop() <= HiddenChamberPlan.DROP_MAX, theme + " drop " + plan.drop());
            assertEquals(plan.mouthFloorY() - plan.landingY(), plan.drop());
            for (HiddenChamberPlan.Cell cell : plan.writes()) {
                assertTrue(cell.y() >= HiddenChamberPlan.MIN_AUTHORED_Y,
                        theme + " authored " + cell + " at or below the world floor");
            }
        }
    }

    @Test
    void theCushionIsTwoDeepOverTheDilatedShaftWithASolidBaseAndAFirmShelf() {
        HiddenChamberPlan.Plan plan = acceptedPlan(null);
        List<HiddenChamberPlan.Cell> powder =
                plan.writesWithRole(HiddenChamberPlan.CellRole.CUSHION_POWDER);
        Set<Long> powderColumns = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : powder) {
            assertTrue(cell.y() == plan.landingY() || cell.y() == plan.landingY() + 1,
                    "powder only occupies the landing and the block above it");
            powderColumns.add(columnKey(cell.x(), cell.z()));
        }
        assertEquals(powderColumns.size() * HiddenChamberPlan.CUSHION_DEPTH, powder.size());
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(HiddenChamberPlan.CellRole.CUSHION_BASE)) {
            assertEquals(plan.landingY() - 1, cell.y());
        }
        assertEquals(HiddenChamberPlan.SHELF_SPAN * HiddenChamberPlan.SHELF_SPAN,
                plan.writesWithRole(HiddenChamberPlan.CellRole.SHELF_FIRM).size());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 4. Envelope                                                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void everyAcceptedCellStaysInsideTheOwnerPlusOneNeighbourWriteEnvelope() {
        for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
            HiddenChamberPlan.Plan plan = acceptedPlan(theme);
            HiddenChamberPlan.EnvelopeDirection direction = plan.direction();
            for (HiddenChamberPlan.Cell cell : plan.writes()) {
                assertTrue(direction.containsWriteCell(cell.x(), cell.z()),
                        theme + " wrote outside the canonical envelope: " + cell);
                assertTrue(direction.containsReadShellCell(cell.x(), cell.z()));
            }
            for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
                assertTrue(mouth.x() >= 0 && mouth.x() < 16 && mouth.z() >= 0 && mouth.z() < 16,
                        theme + " mouth cell left the owner chunk: " + mouth);
                assertEquals(HiddenChamberPlan.CellRole.MOUTH_COLLAPSE, mouth.role());
            }
            int mouthCells = plan.mouthCells().size();
            assertTrue(mouthCells >= HiddenChamberPlan.MOUTH_MIN_CELLS
                    && mouthCells <= HiddenChamberPlan.MOUTH_MAX_CELLS, "mouth cells " + mouthCells);
        }
    }

    @Test
    void aWindowTooSmallForOwnerPlusOneNeighbourIsRejected() {
        Terrain terrain = validTerrain();
        terrain.readableMinX = 0;
        terrain.readableMaxX = 9;
        terrain.readableMinZ = 0;
        terrain.readableMaxZ = 9;
        HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, null);
        assertFalse(result.isAccepted(),
                "no candidate can fit inside a window smaller than one owner chunk");
        assertNotNull(result.detail());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 5. Biome fail-closed                                                                                  */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void oneForeignQuartUnderTheChamberFailsClosed() {
        /*
         * The narrow plateau confines every possible mouth to a single quart column, and the shaft always
         * spans Y32..35 whatever legal drop is chosen, so exactly one foreign quart is unavoidable.
         */
        Terrain reference = narrowTerrain();
        HiddenChamberPlan.PlanResult before = HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, reference, null);
        assertTrue(before.isAccepted(), "the narrow reference terrain must accept: " + before.detail());

        Terrain terrain = narrowTerrain();
        terrain.foreignQuarts.add(quartKey(1, 8, 1));
        HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, null);
        assertFalse(result.isAccepted(), "one foreign quart under the shaft must fail closed");
        assertEquals(HiddenChamberPlan.Rejection.UNSAFE_VOLUME, result.rejection());
        assertEquals(HiddenChamberPlan.RejectionDetail.BIOME_QUART_NOT_GLACIAL, result.detail());
    }

    @Test
    void everyOverlappedQuartIsReportedForTheWorldLayerToReVerify() {
        HiddenChamberPlan.Plan plan = acceptedPlan(null);
        Set<String> reported = new LinkedHashSet<>();
        for (HiddenChamberPlan.Quart quart : plan.biomeQuarts()) {
            reported.add(quart.quartX() + ":" + quart.quartY() + ":" + quart.quartZ());
        }
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            String key = Math.floorDiv(cell.x(), 4) + ":" + Math.floorDiv(cell.y(), 4)
                    + ":" + Math.floorDiv(cell.z(), 4);
            assertTrue(reported.contains(key), "unreported quart for " + cell);
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 6. Occurrence gate                                                                                    */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void theOccurrenceGateIsOneInFourteenStableAndSeedSensitive() {
        int accepted = 0;
        List<String> winners = new ArrayList<>();
        for (int chunkX = 0; chunkX < 140; chunkX++) {
            for (int chunkZ = 0; chunkZ < 100; chunkZ++) {
                boolean gate = HiddenChamberPlan.occurrenceGate(SEED, chunkX, chunkZ);
                assertEquals(gate, HiddenChamberPlan.occurrenceGate(SEED, chunkX, chunkZ),
                        "the gate must be stable per seed and chunk");
                if (gate) {
                    accepted++;
                    winners.add(chunkX + ":" + chunkZ);
                }
            }
        }
        assertEquals(14, HiddenChamberPlan.OCCURRENCE_MODULUS);
        assertTrue(accepted >= 850 && accepted <= 1150,
                "expected roughly one chunk in fourteen of 14000, saw " + accepted);

        List<String> otherWinners = new ArrayList<>();
        for (int chunkX = 0; chunkX < 140; chunkX++) {
            for (int chunkZ = 0; chunkZ < 100; chunkZ++) {
                if (HiddenChamberPlan.occurrenceGate(SEED + 1, chunkX, chunkZ)) {
                    otherWinners.add(chunkX + ":" + chunkZ);
                }
            }
        }
        assertNotEquals(winners, otherWinners, "a different world seed must move the winner set");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 7. Mask catalogue                                                                                     */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void everyMouthMaskIsIrregularAndTwoToFourCells() {
        List<HiddenChamberPlan.MouthMask> catalogue = HiddenChamberPlan.mouthMaskCatalog();
        assertFalse(catalogue.isEmpty());
        Set<String> shapes = new LinkedHashSet<>();
        for (HiddenChamberPlan.MouthMask mask : catalogue) {
            shapes.add(mask.name());
            List<HiddenChamberPlan.MaskOffset> offsets = mask.offsets();
            assertTrue(offsets.size() >= HiddenChamberPlan.MOUTH_MIN_CELLS
                    && offsets.size() <= HiddenChamberPlan.MOUTH_MAX_CELLS, mask.name());
            boolean sameX = true;
            boolean sameZ = true;
            for (HiddenChamberPlan.MaskOffset offset : offsets) {
                sameX &= offset.dx() == offsets.get(0).dx();
                sameZ &= offset.dz() == offsets.get(0).dz();
            }
            assertFalse(sameX || sameZ, mask.name() + " rotation " + mask.rotation() + " is a straight line");
            assertFalse(isTwoByTwoSquare(offsets), mask.name() + " is a two-by-two square");
        }
        assertEquals(Set.of("diagonal-2", "L-3", "zigzag-3", "S-4", "T-4", "skew-4"), shapes);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 8. Exit law                                                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void theExitIsAlwaysPresentDistantBentAndClearOfTheShaft() {
        for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
            HiddenChamberPlan.Plan plan = acceptedPlan(theme);
            int distance = Math.max(
                    Math.abs(plan.exitTerminus().x() - plan.mouthCentroid().x()),
                    Math.abs(plan.exitTerminus().z() - plan.mouthCentroid().z()));
            assertTrue(distance >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                            && distance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                    theme + " terminus distance " + distance);
            assertTrue(plan.corridorBends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                    theme + " corridor bends " + plan.corridorBends());
            assertFalse(plan.writesWithRole(HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR).isEmpty(),
                    theme + " must breach a second opening");

            List<HiddenChamberPlan.Cell> corridor = new ArrayList<>();
            corridor.addAll(plan.writesWithRole(HiddenChamberPlan.CellRole.CORRIDOR_CLEAR));
            corridor.addAll(plan.writesWithRole(HiddenChamberPlan.CellRole.CORRIDOR_FLOOR));
            corridor.addAll(plan.writesWithRole(HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR));
            assertFalse(corridor.isEmpty(), theme + " must author a corridor");
            for (HiddenChamberPlan.Cell cell : corridor) {
                for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
                    int gap = Math.max(Math.abs(cell.x() - mouth.x()), Math.abs(cell.z() - mouth.z()));
                    assertTrue(gap > HiddenChamberPlan.SHAFT_CLEARANCE,
                            theme + " corridor cell " + cell + " passes under the shaft");
                }
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Helpers                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    private static HiddenChamberPlan.Plan acceptedPlan(HiddenChamberPlan.Theme theme) {
        HiddenChamberPlan.PlanResult result =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, validTerrain(), theme);
        assertTrue(result.isAccepted(), "expected an accepted plan for " + theme + ", got " + result.detail());
        return result.accepted();
    }

    private static int compare(HiddenChamberPlan.Cell left, HiddenChamberPlan.Cell right) {
        int byRank = Integer.compare(left.role().rank(), right.role().rank());
        if (byRank != 0) {
            return byRank;
        }
        int byX = Integer.compare(left.x(), right.x());
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(left.y(), right.y());
        return byY != 0 ? byY : Integer.compare(left.z(), right.z());
    }

    private static boolean isTwoByTwoSquare(List<HiddenChamberPlan.MaskOffset> offsets) {
        if (offsets.size() != 4) {
            return false;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (HiddenChamberPlan.MaskOffset offset : offsets) {
            keys.add(offset.dx() + ":" + offset.dz());
        }
        return keys.equals(Set.of("0:0", "0:1", "1:0", "1:1"));
    }

    private static boolean hasOpenSquare(Set<Long> columns, Set<Long> excluded, int span) {
        for (long key : columns) {
            int x = (int) (key >> 32) - 512;
            int z = (int) (key & 0xFFFFFFFFL) - 512;
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
                return true;
            }
        }
        return false;
    }

    private static long columnKey(int x, int z) {
        return ((long) (x + 512) << 32) | (z + 512);
    }

    private static String quartKey(int quartX, int quartY, int quartZ) {
        return quartX + ":" + quartY + ":" + quartZ;
    }

    /**
     * The reference terrain: a snowy roofed plateau in the middle of the owner chunk sitting six blocks above
     * the surrounding roofed cave floor, with solid safe stone all the way down to the world floor.
     */
    private static Terrain validTerrain() {
        Terrain terrain = new Terrain();
        terrain.fillFloor(34, 40);
        return terrain;
    }

    /** The same terrain with the snowy plateau shrunk to one quart column, so every mouth shares a quart. */
    private static Terrain narrowTerrain() {
        Terrain terrain = new Terrain();
        terrain.plateauMax = 7;
        terrain.fillFloor(34, 40);
        return terrain;
    }

    /** Array-backed synthetic probe over owner-local {@code x,z in -24..39} and {@code y in 0..95}. */
    private static final class Terrain implements HiddenChamberPlan.TerrainProbe {
        private static final int MIN = -24;
        private static final int MAX = 39;
        private static final int SIDE = MAX - MIN + 1;
        private static final int HEADROOM = 6;
        /** The snowy plateau the collapse mouth may open in, in owner-local coordinates. */
        private static final int PLATEAU_MIN = 4;

        private final int[][] floor = new int[SIDE][SIDE];
        private final boolean[][] snowy = new boolean[SIDE][SIDE];
        private int plateauMax = 11;
        private int bedrockTop;
        private int readableMinX = MIN;
        private int readableMaxX = MAX;
        private int readableMinZ = MIN;
        private int readableMaxZ = MAX;
        private final Set<String> foreignQuarts = new LinkedHashSet<>();

        void fillFloor(int surroundingFloorY, int plateauFloorY) {
            for (int x = MIN; x <= MAX; x++) {
                for (int z = MIN; z <= MAX; z++) {
                    boolean plateau = x >= PLATEAU_MIN && x <= plateauMax
                            && z >= PLATEAU_MIN && z <= plateauMax;
                    floor[x - MIN][z - MIN] = plateau ? plateauFloorY : surroundingFloorY;
                    snowy[x - MIN][z - MIN] = plateau;
                }
            }
        }

        private boolean readable(int localX, int localZ) {
            return localX >= readableMinX && localX <= readableMaxX
                    && localZ >= readableMinZ && localZ <= readableMaxZ
                    && localX >= MIN && localX <= MAX && localZ >= MIN && localZ <= MAX;
        }

        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            if (!readable(localX, localZ)) {
                return null;
            }
            return new HiddenChamberPlan.ColumnInfo(
                    floor[localX - MIN][localZ - MIN], snowy[localX - MIN][localZ - MIN], true, HEADROOM);
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            if (!readable(localX, localZ)) {
                return HiddenChamberPlan.CellKind.UNREADABLE;
            }
            if (y <= bedrockTop) {
                return HiddenChamberPlan.CellKind.BEDROCK;
            }
            int floorY = floor[localX - MIN][localZ - MIN];
            if (y <= floorY) {
                return HiddenChamberPlan.CellKind.SOLID_SAFE;
            }
            return y <= floorY + HEADROOM
                    ? HiddenChamberPlan.CellKind.AIR
                    : HiddenChamberPlan.CellKind.SOLID_SAFE;
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            return !foreignQuarts.contains(quartKey(quartLocalX, quartY, quartLocalZ));
        }
    }
}
