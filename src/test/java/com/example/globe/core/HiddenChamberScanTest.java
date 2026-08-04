package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM smoke laws for the physical chamber scanner. Every fixture is a hand-built block grid — no server,
 * no generator, no plan — so each test states exactly which REAL blocks make the scanner say "complete" and
 * which single missing piece makes it say why not.
 *
 * <p>The reference world is one compact chamber under a flat glacial cave: a three-cell diagonal mouth in the
 * cave floor at y=40, a fourteen-block fall, a two-deep powder cushion on packed ice, a two-by-two snow shelf
 * beside it, an irregular room with three ceiling heights, and a two-wide winding corridor that climbs back to
 * the cave floor and breaks through it a short walk away.
 */
class HiddenChamberScanTest {

    /* Reference geometry. Every test derives from these, so a change is visible in one place. */
    private static final int CAVE_FLOOR_Y = 40;
    private static final int CAVE_CEILING_Y = 45;
    private static final int MOUTH_FLOOR_Y = CAVE_FLOOR_Y;
    private static final int LANDING_Y = 26;
    private static final int DROP = MOUTH_FLOOR_Y - LANDING_Y;
    private static final int CHAMBER_FLOOR_Y = LANDING_Y - 1;
    private static final int SIZE = 64;

    private static final int[][] MOUTH_CELLS = {{19, 19}, {20, 20}, {21, 21}};

    /** The deep frigid-lake fixture's own geometry; see the section-7 tests for what it proves. */
    private static final int LAKE_LANDING_Y = 21;
    private static final int LAKE_FLOOR_Y = LAKE_LANDING_Y - 1;
    private static final int LAKE_DROP = MOUTH_FLOOR_Y - LAKE_LANDING_Y;
    private static final int[][] LAKE_MOUTH_CELLS = {{12, 20}, {13, 20}, {13, 21}};
    private static final HiddenChamberScan.Position LAKE_CENTROID =
            new HiddenChamberScan.Position(13, MOUTH_FLOOR_Y, 20);

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. A complete chamber                                                                                 */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void theReferenceChamberReadsBackCompleteFromItsBlocks() {
        HiddenChamberScan.ChamberScanReport report = scanAll(referenceWorld());

        assertEquals(3, report.collapseCells(), "the mouth is three collapse cells");
        assertEquals(1, report.patches());
        assertEquals(0, report.partialCount(), "no partial: " + report.partial());
        assertEquals(0, report.legacyCount());
        assertEquals(1, report.completedCount(), "the reference chamber must reconstruct");

        HiddenChamberScan.Completed chamber = report.completed().get(0);
        assertEquals(new HiddenChamberScan.Position(20, MOUTH_FLOOR_Y, 20), chamber.mouthCentroid());
        assertEquals(3, chamber.mouthCells().size());
        assertEquals(new HiddenChamberScan.Position(20, LANDING_Y, 20), chamber.landing());
        assertEquals(DROP, chamber.drop(), "the measured fall is the authored fall");
        assertTrue(chamber.drop() >= HiddenChamberPlan.DROP_MIN && chamber.drop() <= HiddenChamberPlan.DROP_MAX,
                "the reference drop sits inside the authored band");

        // The marker stands on the authored corridor, on its second leg. It is NOT the final breach column:
        // once "an opening is where the head air joins the mouth's own cave" replaced the old shape tests,
        // every station from the breach back down the bore qualifies -- each one PROVES the bore surfaces
        // ahead of it -- and the breadth-first walk names the first it reaches. See the class javadoc of
        // HiddenChamberScan#isOpening; the bands below are what keep the marker meaningful.
        assertEquals(new HiddenChamberScan.Position(32, 34, 26), chamber.exitOpening());
        assertTrue(isOnReferenceCorridor(chamber.exitOpening()),
                "the marker stands on the corridor the fixture authored, not on some unrelated cave floor: "
                        + chamber.exitOpening());
        int distance = Math.max(Math.abs(chamber.exitOpening().x() - chamber.mouthCentroid().x()),
                Math.abs(chamber.exitOpening().z() - chamber.mouthCentroid().z()));
        assertTrue(distance >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                        && distance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                "the second opening is a short walk from the mouth, not a neighbour and not a hike: " + distance);
        assertTrue(Math.abs(chamber.exitOpening().y() - MOUTH_FLOOR_Y) <= HiddenChamberPlan.EXIT_FLOOR_TOLERANCE);
        assertTrue(chamber.bends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                "the route winds; a drain pipe is not an escape: " + chamber.bends());
        assertTrue(chamber.voidVolume() >= HiddenChamberScan.MIN_VOID_VOLUME,
                "the room is a room: " + chamber.voidVolume());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. One missing piece at a time                                                                        */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void aMissingCushionIsReportedAsNoCushion() {
        Grid world = referenceWorld();
        world.fill(18, 22, LANDING_Y, LANDING_Y + 1, 18, 22, HiddenChamberScan.ScanCell.AIR);

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(0, report.completedCount());
        assertEquals(1, report.partialCount());
        assertEquals(HiddenChamberScan.PartialReason.NO_CUSHION, report.partial().get(0).reason());
    }

    @Test
    void aSealedChamberIsReportedAsNoExit() {
        Grid world = referenceWorld();
        fillCorridor(world, HiddenChamberScan.ScanCell.OTHER_SOLID);
        world.fill(0, SIZE - 1, CAVE_FLOOR_Y, CAVE_FLOOR_Y, 0, SIZE - 1, HiddenChamberScan.ScanCell.SNOW_FIRM);
        mouth(world);

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(0, report.completedCount());
        assertEquals(1, report.partialCount());
        assertEquals(HiddenChamberScan.PartialReason.NO_EXIT, report.partial().get(0).reason());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 3. The legacy drop trap is not a chamber                                                              */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void aSixCellCarpetIsTheLegacyTrapAndNeverAChamber() {
        Grid world = emptyCave();
        world.fill(50, 52, CAVE_FLOOR_Y, CAVE_FLOOR_Y, 50, 51,
                HiddenChamberScan.ScanCell.COLLAPSE_POWDER);

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(6, report.collapseCells());
        assertEquals(1, report.patches());
        assertEquals(0, report.completedCount());
        assertEquals(0, report.partialCount(), "a legacy trap is never a failed chamber");
        assertEquals(1, report.legacyCount());
        assertEquals(6, report.legacy().get(0).patchSize());
    }

    @Test
    void aLoneCollapseCellIsAPartialMouth() {
        Grid world = emptyCave();
        world.set(50, CAVE_FLOOR_Y, 50, HiddenChamberScan.ScanCell.COLLAPSE_POWDER);

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(1, report.partialCount());
        assertEquals(HiddenChamberScan.PartialReason.MOUTH_SIZE, report.partial().get(0).reason());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 4. Theme guesses come from the dressing, and never decide validity                                    */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void sixIciclesReadAsAnIceCathedral() {
        Grid world = referenceWorld();
        for (int x = 14; x <= 16; x++) {
            for (int z = 15; z <= 16; z++) {
                world.set(x, 29, z, HiddenChamberScan.ScanCell.ICICLE); // six roof icicles
            }
        }

        HiddenChamberScan.Completed chamber = onlyChamber(world);
        assertEquals(6, chamber.tallies().icicles());
        assertEquals(HiddenChamberScan.Theme.ICE_CATHEDRAL, chamber.themeGuess());
    }

    @Test
    void aFourByFourPoolReadsAsAFrigidLake() {
        Grid world = referenceWorld();
        world.fill(14, 17, LANDING_Y, LANDING_Y, 15, 18, HiddenChamberScan.ScanCell.WATER);

        HiddenChamberScan.Completed chamber = onlyChamber(world);
        assertEquals(16, chamber.tallies().water());
        assertTrue(chamber.tallies().waterRectangle());
        assertEquals(HiddenChamberScan.Theme.FRIGID_LAKE, chamber.themeGuess());
    }

    @Test
    void aChestAndALanternReadAsALostExpedition() {
        Grid world = referenceWorld();
        // West of x=16, so the props stand on the chamber floor and not in the escape shelf's headroom: the
        // shelf is x=16..17 by z=19..20, and a lantern in the two blocks of air above it is a blocked shelf.
        world.set(14, LANDING_Y, 20, HiddenChamberScan.ScanCell.CHEST);
        world.set(15, LANDING_Y, 20, HiddenChamberScan.ScanCell.LANTERN);

        HiddenChamberScan.Completed chamber = onlyChamber(world);
        assertEquals(1, chamber.tallies().chests());
        assertEquals(1, chamber.tallies().lanterns());
        assertEquals(HiddenChamberScan.Theme.LOST_EXPEDITION, chamber.themeGuess());
    }

    /**
     * The real defect, in hand-built blocks: a lake chamber whose floor is HOLLOW underneath.
     *
     * <p>Where the ground under the room is hollow the planner seals a packed-ice floor beneath each interior
     * column, and each of those seals stands proud with open void on all four sides. The dressing census used
     * to count those blocks as "interior ice" and the classifier used to ask the cathedral question first, so
     * a forced {@code FRIGID_LAKE} on real terrain came back {@link HiddenChamberScan.Theme#ICE_CATHEDRAL}
     * with its lake sitting right there in the blocks. Every earlier fixture stood on SOLID ground and so
     * needed almost no seals, which is why nothing caught it.
     *
     * <p>Both halves of the fix are pinned here: the seals are counted (under the old rule's own condition,
     * evaluated straight off the blocks) and there are more than enough of them to have tripped the old
     * cathedral threshold — yet the reconstructed theme is {@code FRIGID_LAKE}, and the ice tally reads ZERO
     * columns because a one-block seal is not a pillar.
     */
    @Test
    void aLakeChamberOnAHollowUnderFloorIsStillAFrigidLake() {
        Grid world = hollowUnderFloorLakeWorld();

        int seals = freeStandingIceBlocksTheOldTallyWouldHaveCounted(world, LOW_CHAMBER_FLOOR_Y);
        assertTrue(seals >= 4 * HiddenChamberPlan.CATHEDRAL_MIN_ICE_COLUMNS,
                "the fixture must lay enough floor seals to trip the OLD block-counting threshold ("
                        + (4 * HiddenChamberPlan.CATHEDRAL_MIN_ICE_COLUMNS) + "), else it proves nothing: "
                        + seals);

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(0, report.partialCount(), "the lake chamber must still reconstruct: " + report.partial());
        assertEquals(1, report.completedCount());

        HiddenChamberScan.Completed chamber = report.completed().get(0);
        assertTrue(chamber.tallies().water() >= HiddenChamberScan.THEME_MIN_WATER
                        && chamber.tallies().waterRectangle(),
                "the lake is unmistakable: " + chamber.tallies());
        assertEquals(0, chamber.tallies().interiorIce(),
                "a one-block floor seal is not an ice column, however many of them there are: "
                        + chamber.tallies());
        assertEquals(HiddenChamberScan.Theme.FRIGID_LAKE, chamber.themeGuess(),
                "water first, ice last: " + chamber.tallies());
    }

    /**
     * ...and the cathedral is still a cathedral when its ice really is standing in pillars.
     *
     * <p>Tightening the tally to true columns would be no good if it stopped naming the theme it exists for.
     * Two free-standing packed-ice pillars, four blocks tall, in a room with no water and no props: the ice
     * census alone must carry it, with not one icicle to help.
     */
    @Test
    void twoFreeStandingIcePillarsStillReadAsAnIceCathedral() {
        Grid world = referenceWorld();
        for (int z : new int[] {16, 22}) {
            world.fill(15, 15, LANDING_Y, LANDING_Y + 3, z, z, HiddenChamberScan.ScanCell.ICE_PACKED);
        }

        HiddenChamberScan.Completed chamber = onlyChamber(world);
        assertEquals(0, chamber.tallies().icicles(), "no icicles: the ice columns must carry this alone");
        assertEquals(0, chamber.tallies().water(), "and the floor is puddle-free");
        assertEquals(HiddenChamberPlan.CATHEDRAL_MIN_ICE_COLUMNS, chamber.tallies().interiorIce(),
                "two pillars read as two columns: " + chamber.tallies());
        assertEquals(HiddenChamberScan.Theme.ICE_CATHEDRAL, chamber.themeGuess());
    }

    /**
     * The OLD interior-ice rule, evaluated off the raw blocks: a packed or blue ice block at {@code floorY}
     * with open space on at least three of its four horizontal sides counted as one "interior ice".
     */
    private static int freeStandingIceBlocksTheOldTallyWouldHaveCounted(Grid world, int floorY) {
        int counted = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                HiddenChamberScan.ScanCell cell = world.cell(x, floorY, z);
                if (cell != HiddenChamberScan.ScanCell.ICE_PACKED
                        && cell != HiddenChamberScan.ScanCell.ICE_BLUE) {
                    continue;
                }
                int open = 0;
                for (int[] side : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                    if (world.cell(x + side[0], floorY, z + side[1]).isVoid()) {
                        open++;
                    }
                }
                if (open >= 3) {
                    counted++;
                }
            }
        }
        return counted;
    }

    @Test
    void anUndressedChamberIsStillComplete() {
        HiddenChamberScan.Completed chamber = onlyChamber(referenceWorld());
        assertEquals(HiddenChamberScan.Theme.UNKNOWN, chamber.themeGuess(),
                "dressing classifies; geometry validates");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 5. Fail closed                                                                                        */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void anUnreadableRingAroundTheMouthFailsClosed() {
        Grid world = referenceWorld();
        HiddenChamberScan.CellReader blinkered = (x, y, z) -> {
            for (int[] cell : MOUTH_CELLS) {
                if (x == cell[0] && z == cell[1]) {
                    return world.cell(x, y, z);
                }
            }
            for (int[] cell : MOUTH_CELLS) {
                if (Math.max(Math.abs(x - cell[0]), Math.abs(z - cell[1])) <= 1) {
                    return HiddenChamberScan.ScanCell.UNREADABLE;
                }
            }
            return world.cell(x, y, z);
        };

        HiddenChamberScan.ChamberScanReport report = HiddenChamberScan.scan(blinkered, wholeGrid());
        assertEquals(0, report.completedCount());
        assertEquals(1, report.partialCount());
        assertEquals(HiddenChamberScan.PartialReason.BOUNDARY_UNREADABLE, report.partial().get(0).reason(),
                "an unseen cell never becomes a geometric verdict");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 6. Determinism                                                                                        */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void twoScansOfOneWorldProduceOneIdenticalReport() {
        Grid world = referenceWorld();
        world.fill(50, 52, CAVE_FLOOR_Y, CAVE_FLOOR_Y, 50, 51,
                HiddenChamberScan.ScanCell.COLLAPSE_POWDER);
        world.set(4, CAVE_FLOOR_Y, 58, HiddenChamberScan.ScanCell.COLLAPSE_POWDER);

        HiddenChamberScan.ChamberScanReport first = scanAll(world);
        HiddenChamberScan.ChamberScanReport second = scanAll(world);
        assertEquals(first, second, "identical blocks must give a byte-identical report, ordering included");
        assertEquals(1, first.completedCount());
        assertEquals(1, first.legacyCount());
        assertEquals(1, first.partialCount());
        assertEquals(3, first.patches());
    }

    @Test
    void patchGroupingToleratesTheAuthoredFloorSpreadAndIsOrdered() {
        List<List<HiddenChamberScan.Position>> patches = HiddenChamberScan.groupCollapsePatches(List.of(
                new HiddenChamberScan.Position(9, 41, 9),
                new HiddenChamberScan.Position(3, 40, 3),
                new HiddenChamberScan.Position(4, 41, 4),
                new HiddenChamberScan.Position(8, 40, 8)));
        assertEquals(2, patches.size(), "two mouths a long way apart stay two mouths");
        assertEquals(new HiddenChamberScan.Position(3, 40, 3), patches.get(0).get(0), "ordered by coordinate");
        assertEquals(2, patches.get(0).size(), "a one-block floor spread is still one patch");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 7. A deep frigid lake: the walk starts in the ROOM, not inside the exit ring                          */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * The regression this pair of tests was written for, reproduced in hand-built blocks.
     *
     * <p>A {@code FRIGID_LAKE} chamber under a deep fall is the one authored layout that leaves NO dry footing
     * near the mouth: the cushion takes the middle, the open water takes the rest of the near half, and the
     * only place a player can stand within eight blocks of the mouth centroid is the escape shelf itself — a
     * two-by-two island with water on every side. The corridor therefore leaves from the FAR shore, sixteen
     * blocks out.
     *
     * <p>The scanner used to seed its exit walk only from stations INSIDE
     * {@link HiddenChamberPlan#EXIT_DISTANCE_MIN} of the mouth centroid, so on this shape it started on the
     * island, found the water on all four sides, and reported {@link HiddenChamberScan.PartialReason#NO_EXIT}
     * for a corridor standing in the blocks. Measured on this fixture against that rule: one patch, zero
     * complete, one partial, reason {@code NO_EXIT}. It is the same verdict a real world gave at mouth
     * (-2798,33,9318) — chunk (-175,582), seed 987654 — where the chamber wrote atomically and the physical
     * audit reconstructed mouth, shaft, cushion, shelf and void before failing on the exit, with the fluid
     * census proving nothing had flooded.
     *
     * <p>The room is where a player lands, so the room is where the walk starts. Every walkable station the
     * chamber void stands on now seeds it, at any distance.
     */
    @Test
    void aDeepLakeChamberWhoseOnlyNearFootingIsAnIslandStillWalksOutOfItsFarShoreCorridor() {
        HiddenChamberScan.ChamberScanReport report = scanAll(deepLakeWorld());

        assertEquals(1, report.patches(), "the lake mouth is one collapse patch");
        assertEquals(0, report.legacyCount());
        assertEquals(0, report.partialCount(),
                "the deep lake chamber must reconstruct COMPLETE, got " + report.partial());
        assertEquals(1, report.completedCount());

        HiddenChamberScan.Completed chamber = report.completed().get(0);
        assertEquals(LAKE_CENTROID, chamber.mouthCentroid());
        assertEquals(new HiddenChamberScan.Position(13, LAKE_LANDING_Y, 20), chamber.landing());
        assertEquals(LAKE_DROP, chamber.drop(), "the measured fall is the authored fall");
        assertTrue(chamber.drop() >= 18,
                "the failure only shows on a DEEP fall, which is what puts the room's footing out of the ring: "
                        + chamber.drop());
        assertEquals(new HiddenChamberScan.Position(29, 36, 32), chamber.exitOpening(),
                "the reconstructed opening stands on the far-shore corridor's last leg -- the walk names the "
                        + "first station whose head air joins the mouth's own cave, which is a few blocks "
                        + "back down the bore from the breach column itself");
        int distance = Math.max(Math.abs(chamber.exitOpening().x() - LAKE_CENTROID.x()),
                Math.abs(chamber.exitOpening().z() - LAKE_CENTROID.z()));
        assertTrue(distance >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                        && distance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                "the opening still obeys the authored exit band: " + distance);
        assertTrue(chamber.bends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                "the switchback climb still reads as a winding route: " + chamber.bends());
        assertEquals(HiddenChamberScan.Theme.FRIGID_LAKE, chamber.themeGuess(),
                "the fixture is the shape that fails: an open lake, not a dry room " + chamber.tallies());
    }

    /**
     * The fixture above is only a proof if it really is the shape that broke: nothing to start from inside the
     * exit ring, and a corridor whose chamber entrance is well beyond it. Both halves are pinned here off the
     * raw blocks, so a future narrowing of the seed rule cannot quietly turn the test above into a chamber the
     * old rule could have walked anyway.
     */
    @Test
    void theDeepLakeFixtureStrandsEveryNearMouthFootingOnAnIslandAndPutsTheCorridorTenBlocksOut() {
        Grid world = deepLakeWorld();

        List<HiddenChamberScan.Position> nearFootings = new ArrayList<>();
        for (int x = LAKE_CENTROID.x() - HiddenChamberPlan.EXIT_DISTANCE_MIN + 1;
                x <= LAKE_CENTROID.x() + HiddenChamberPlan.EXIT_DISTANCE_MIN - 1; x++) {
            for (int z = LAKE_CENTROID.z() - HiddenChamberPlan.EXIT_DISTANCE_MIN + 1;
                    z <= LAKE_CENTROID.z() + HiddenChamberPlan.EXIT_DISTANCE_MIN - 1; z++) {
                for (int y = 1; y < MOUTH_FLOOR_Y; y++) {
                    if (isFooting(world, x, y, z)) {
                        nearFootings.add(new HiddenChamberScan.Position(x, y, z));
                    }
                }
            }
        }
        assertEquals(List.of(
                        new HiddenChamberScan.Position(15, LAKE_FLOOR_Y, 20),
                        new HiddenChamberScan.Position(15, LAKE_FLOOR_Y, 21),
                        new HiddenChamberScan.Position(16, LAKE_FLOOR_Y, 20),
                        new HiddenChamberScan.Position(16, LAKE_FLOOR_Y, 21)),
                nearFootings.stream().sorted().toList(),
                "inside the old seed ring the shelf island is the ONLY footing; everything else is open water, "
                        + "cushion powder or the shaft itself");

        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int x = 15; x <= 16; x++) {
                for (int z = 20; z <= 21; z++) {
                    int neighbourX = x + step[0];
                    int neighbourZ = z + step[1];
                    if (neighbourX >= 15 && neighbourX <= 16 && neighbourZ >= 20 && neighbourZ <= 21) {
                        continue; // still on the island
                    }
                    for (int y = LAKE_FLOOR_Y - 1; y <= LAKE_FLOOR_Y + 1; y++) {
                        assertFalse(isFooting(world, neighbourX, y, neighbourZ),
                                "the island must be closed: (" + neighbourX + "," + y + "," + neighbourZ
                                        + ") is somewhere to step off it");
                    }
                }
            }
        }

        // ...and the way out starts on the far shore, three times the old ring's reach from the centroid.
        assertTrue(isFooting(world, 28, LAKE_FLOOR_Y, 18), "the far shore is walkable");
        assertTrue(isFooting(world, 29, LAKE_FLOOR_Y, 18), "the corridor's first station is walkable");
        assertTrue(Math.max(Math.abs(28 - LAKE_CENTROID.x()), Math.abs(18 - LAKE_CENTROID.z())) >= 10,
                "the corridor's chamber entrance stands at least ten blocks from the mouth centroid");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 8. One straight route out never condemns a chamber that has a winding one                             */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * A chamber has an exit when ANY route out of it obeys every law, not when the first one found does.
     *
     * <p>The walk holds one route per opening — the shortest from the whole seed set — so the opening here is
     * judged on a route that leaves the room heading east, runs the corridor east, and turns north twice at the
     * end: ONE bend, short of {@link HiddenChamberPlan#CORRIDOR_MIN_BENDS}, from the room station that happens
     * to stand square in front of the corridor mouth. Every other station in that same room reaches the same
     * opening by turning into the corridor first, which is two bends and a legal escape. Measured on this
     * fixture with acceptance resting on the first route alone: zero complete, one partial,
     * {@link HiddenChamberScan.PartialReason#NO_EXIT} — a chamber refused for the accident of which seed sat
     * nearest its own way out.
     *
     * <p>This is what makes widening the seed set safe. A wider set can only make the FIRST route shorter, so
     * without this law a chamber that completed under the old near-mouth ring could start failing for want of
     * a bend the moment more stations were allowed to start a walk.
     */
    @Test
    void anOpeningWhoseNearestRouteIsTooStraightIsStillReachedByTheWindingOne() {
        HiddenChamberScan.ChamberScanReport report = scanAll(straightestRouteWorld());

        assertEquals(0, report.partialCount(),
                "a straight route to an opening must not condemn a chamber that also has a winding one, got "
                        + report.partial());
        assertEquals(1, report.completedCount());

        HiddenChamberScan.Completed chamber = report.completed().get(0);
        assertEquals(new HiddenChamberScan.Position(34, 34, 22), chamber.exitOpening(),
                "the opening is the same one either route arrives at, named one station back down the bore");
        assertTrue(chamber.bends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                "the route REPORTED is the qualifying one, not the straight one it was found by: "
                        + chamber.bends());
    }

    /**
     * The reference room with its winding corridor walled up and an L-shaped one bored in its place: nine
     * blocks east out of the room's east wall, climbing a block a step, then two steps north onto a station
     * whose column is breached up through the cave floor. Straight in, one corner, out — unless the walk
     * starts anywhere in the room but directly in front of the corridor mouth.
     */
    private static Grid straightestRouteWorld() {
        Grid world = referenceWorld();
        fillCorridor(world, HiddenChamberScan.ScanCell.OTHER_SOLID);
        world.fill(0, SIZE - 1, CAVE_FLOOR_Y, CAVE_FLOOR_Y, 0, SIZE - 1, HiddenChamberScan.ScanCell.SNOW_FIRM);
        mouth(world);

        for (int x = 26; x <= 34; x++) {
            int floor = x - 1;
            world.fill(x, x, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, 20, 21,
                    HiddenChamberScan.ScanCell.AIR);
        }
        for (int z = 22; z <= 23; z++) {
            int floor = z + 12;
            world.fill(34, 35, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, z, z,
                    HiddenChamberScan.ScanCell.AIR);
        }
        // One column of the terminal station is breached, so the chamber offers exactly ONE opening and the
        // walk cannot answer a straight route to it by finding a bendier route to its neighbour.
        world.fill(34, 34, 36, CAVE_FLOOR_Y, 23, 23, HiddenChamberScan.ScanCell.AIR);
        return world;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 9. A corridor that surfaces into a LOW cave section is still a way out                                 */
    /* ---------------------------------------------------------------------------------------------------- */

    /*
     * The encounter this section is built from, in the same shape the real world produced it: mouth floor
     * y=33, landing y=13 (a twenty-block fall), a corridor that climbs eighteen blocks in three legs, and a
     * terminus at y=30 standing twelve blocks from the mouth centroid in a cave section only two or three
     * blocks high. See {@link #aCorridorThatSurfacesIntoATwoBlockHighCaveIsStillAnExit}.
     */
    private static final int LOW_MOUTH_FLOOR_Y = 33;
    private static final int LOW_LANDING_Y = 13;
    private static final int LOW_CHAMBER_FLOOR_Y = LOW_LANDING_Y - 1;
    private static final int LOW_DROP = LOW_MOUTH_FLOOR_Y - LOW_LANDING_Y;
    private static final int[][] LOW_MOUTH_CELLS = {{20, 20}, {21, 20}, {21, 21}};
    private static final HiddenChamberScan.Position LOW_CENTROID =
            new HiddenChamberScan.Position(21, LOW_MOUTH_FLOOR_Y, 20);
    /** The low cave's own floor, and with it the authored terminal station. */
    private static final int LOW_CAVE_FLOOR_Y = 30;
    /** The column the corridor's last leg breaks the NARROW tunnel open on, and the station marked for it. */
    private static final HiddenChamberScan.Position NARROW_BREACH =
            new HiddenChamberScan.Position(33, 30, 17);
    private static final HiddenChamberScan.Position NARROW_EXIT =
            new HiddenChamberScan.Position(37, 27, 19);

    /** The column the corridor's last leg breaks the low cave open on. */
    private static final HiddenChamberScan.Position LOW_BREACH =
            new HiddenChamberScan.Position(33, LOW_CAVE_FLOOR_Y, 17);
    /**
     * The station the scan MARKS as the second opening.
     *
     * <p>Not the breach column, and correctly so. An opening is now "a station whose head air joins the air
     * the mouth stands in", and every station from the breach back down the bore satisfies that — each one
     * proving the bore surfaces ahead of it — so the breadth-first walk names the first one it reaches. Here
     * that is the climb's second leg, at the lowest floor still inside
     * {@link HiddenChamberPlan#EXIT_FLOOR_TOLERANCE} of the mouth floor and the furthest still inside
     * {@link HiddenChamberPlan#EXIT_DISTANCE_MAX}. Both bands are asserted on it below, which is what keeps
     * the marker a short walk from the mouth at the mouth's own level.
     */
    private static final HiddenChamberScan.Position LOW_EXIT =
            new HiddenChamberScan.Position(37, 27, 19);

    /**
     * The regression this section was written for, reproduced in hand-built blocks.
     *
     * <p>{@link HiddenChamberPlan}'s terminus law accepts any roofed column whose headroom is at least
     * {@link HiddenChamberPlan#MOUTH_MIN_HEADROOM} — TWO — so a corridor may legally surface into a cave
     * section two or three blocks high, bore its own {@link HiddenChamberPlan#CORRIDOR_CLEAR_HEIGHT} blocks
     * of portal, and find that cave's ceiling directly above them. The scanner's old breakout test asked for
     * one block of air ABOVE the bore, which is four blocks of clear space the planner never promised, so
     * every such exit was invisible and the whole chamber reconstructed
     * {@link HiddenChamberScan.PartialReason#NO_EXIT} with its way out standing open in the blocks.
     *
     * <p>Measured on a real world before the fix: seed 987654, the {@code FRIGID_LAKE} chamber at mouth
     * (-2798,33,9318), landing 13. The exit search seeded 122 stations, walked 212 of them, climbed the
     * authored corridor to the station floor at y=30 — inside the authored floor band, inside the eight-to
     * -sixteen ring — and offered not ONE opening candidate before reporting {@code frontier-exhausted}.
     * Every end-to-end fixture until now happened to breach into a TALL cave, which is the only reason four
     * figures of green tests never saw it.
     *
     * <p>{@link #theLowRoofBreachOffersNoOpeningAtAllUnderTheOldTallColumnRule} pins that this fixture really
     * is that shape and not merely a chamber the old rule could have walked anyway.
     */
    @Test
    void aCorridorThatSurfacesIntoATwoBlockHighCaveIsStillAnExit() {
        assertLowRoofBreachReconstructs(2);
    }

    /** The same law one block higher: three is still shorter than the four the old rule silently demanded. */
    @Test
    void aCorridorThatSurfacesIntoAThreeBlockHighCaveIsStillAnExit() {
        assertLowRoofBreachReconstructs(3);
    }

    private static void assertLowRoofBreachReconstructs(int caveHeadroom) {
        String label = "cave headroom " + caveHeadroom + ": ";
        HiddenChamberScan.ChamberScanReport report = scanAll(lowRoofBreachWorld(caveHeadroom));

        assertEquals(1, report.patches(), label + "the mouth is one collapse patch");
        assertEquals(0, report.legacyCount(), label + "a chamber mouth is never the legacy trap");
        assertEquals(0, report.partialCount(),
                label + "a corridor that surfaces into a low cave is still an exit, got " + report.partial());
        assertEquals(1, report.completedCount());

        HiddenChamberScan.Completed chamber = report.completed().get(0);
        assertEquals(LOW_CENTROID, chamber.mouthCentroid(), label + "mouth centroid");
        assertEquals(new HiddenChamberScan.Position(21, LOW_LANDING_Y, 20), chamber.landing(), label + "landing");
        assertEquals(LOW_DROP, chamber.drop(), label + "the measured fall is the authored fall");
        assertTrue(chamber.drop() >= HiddenChamberPlan.DROP_MIN && chamber.drop() <= HiddenChamberPlan.DROP_MAX,
                label + "the drop sits inside the authored band: " + chamber.drop());
        assertEquals(LOW_EXIT, chamber.exitOpening(), label + "the marked opening");
        assertTrue(isOnLowRoofCorridor(chamber.exitOpening()),
                label + "the marker stands on the corridor this fixture authored, not on unrelated cave "
                        + "floor: " + chamber.exitOpening());
        int distance = Math.max(Math.abs(chamber.exitOpening().x() - LOW_CENTROID.x()),
                Math.abs(chamber.exitOpening().z() - LOW_CENTROID.z()));
        assertTrue(distance >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                        && distance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                label + "the opening still obeys the authored exit band: " + distance);
        assertTrue(Math.abs(chamber.exitOpening().y() - LOW_MOUTH_FLOOR_Y)
                        <= HiddenChamberPlan.EXIT_FLOOR_TOLERANCE,
                label + "the opening still obeys the authored floor band");
        assertTrue(chamber.bends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                label + "the three-leg climb still reads as a winding route: " + chamber.bends());
        assertTrue(chamber.voidVolume() >= HiddenChamberScan.MIN_VOID_VOLUME,
                label + "the room is a room: " + chamber.voidVolume());

        // ...and the fixture really is a LOW breach: the cave the corridor surfaces into is capped
        // `caveHeadroom` blocks over its own floor, so neither retired shape test could ever have passed it.
        Grid world = lowRoofBreachWorld(caveHeadroom);
        assertFalse(world.cell(LOW_BREACH.x(),
                        LOW_BREACH.y() + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + 1, LOW_BREACH.z()).isAir(),
                label + "the breach column is capped by the cave's ceiling, which is the whole point");
        assertFalse(world.cell(LOW_EXIT.x(), LOW_EXIT.y() + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + 1,
                        LOW_EXIT.z()).isAir(),
                label + "and so is the marked station's own bore");
    }

    /**
     * The fixture is only a proof if the OLD rule really could not walk it: not one station anywhere in
     * either low-roof world satisfies "floor band, distance band, one block of air above the bore, roofed",
     * which was the whole of the acceptance test before this round. So the chambers that complete above
     * complete on the NEW clause and on nothing else.
     */
    @Test
    void theLowRoofBreachOffersNoOpeningAtAllUnderTheOldTallColumnRule() {
        for (int caveHeadroom = 2; caveHeadroom <= 3; caveHeadroom++) {
            assertEquals(List.of(), openingsUnderTheOldTallColumnRule(lowRoofBreachWorld(caveHeadroom)),
                    "cave headroom " + caveHeadroom + ": the old tall-column rule must find NO opening here");
        }
    }

    /**
     * "The same upper cave" is the product law, and it is enforced rather than assumed.
     *
     * <p>Identical blocks except that the two steps joining the low section to the mouth's own cave are gone,
     * so the corridor surfaces into a sealed pocket. The breach is still there, the terminus is still a
     * station inside both authored bands, and it is still roofed — only the cave it opens into is not the one
     * the victim fell out of, and an opening into that is not a way back.
     */
    @Test
    void aBreachIntoACaveThatIsNotTheMouthsOwnIsNotAnExit() {
        Grid world = lowRoofBreachWorld(2, false, true);
        assertTrue(isFooting(world, LOW_BREACH.x(), LOW_BREACH.y(), LOW_BREACH.z()),
                "the breach column must still be walkable, or this test proves nothing");

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(0, report.completedCount(), "an isolated pocket is not the mouth's own cave");
        assertEquals(1, report.partialCount());
        assertEquals(HiddenChamberScan.PartialReason.NO_EXIT, report.partial().get(0).reason());
    }

    /**
     * A corridor that breaches NOTHING is not a way out, however far it climbs.
     *
     * <p>This is the negative the whole opening rule now rests on. Identical blocks except that the last leg
     * stops two columns short, so the bore never touches the low cave: it is a dead end in the rock. Every
     * station along it is walkable, many are inside both authored bands, and the low cave beyond is still
     * joined to the mouth's own — but the bore's air is joined to NOTHING except the chamber, and the flood
     * cannot come the other way to meet it, because the shaft is stoppered by the collapse cells and the
     * exit law's floor band ends far above the room. So the chamber reads
     * {@link HiddenChamberScan.PartialReason#NO_EXIT}, and it is the sealed shaft and the band that make it
     * do so.
     *
     * @see #theCollapseCellsStopTheFloodFromShortCircuitingDownTheShaft the seal, asserted on its own
     */
    @Test
    void aCorridorThatDeadEndsInTheRockIsNotAnExit() {
        Grid world = lowRoofBreachWorld(2, true, false);

        /* The bore climbs to standing room inside both bands, and is roofed... */
        HiddenChamberScan.Position deadEnd = new HiddenChamberScan.Position(35, LOW_CAVE_FLOOR_Y, 17);
        assertTrue(isFooting(world, deadEnd.x(), deadEnd.y(), deadEnd.z()),
                "the dead end is walkable standing room");
        int distance = Math.max(Math.abs(deadEnd.x() - LOW_CENTROID.x()),
                Math.abs(deadEnd.z() - LOW_CENTROID.z()));
        assertTrue(distance >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                        && distance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                "it stands inside the authored distance band: " + distance);
        assertTrue(Math.abs(deadEnd.y() - LOW_MOUTH_FLOOR_Y) <= HiddenChamberPlan.EXIT_FLOOR_TOLERANCE,
                "it stands inside the authored floor band");

        /* ...but two solid columns stand between its head air and the cave, so it breaches nothing. */
        for (int x = LOW_BREACH.x() + 1; x < deadEnd.x(); x++) {
            for (int level = 1; level <= HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT; level++) {
                assertFalse(world.cell(x, deadEnd.y() + level, deadEnd.z()).isAir(),
                        "the bore must stop short: (" + x + "," + (deadEnd.y() + level) + "," + deadEnd.z()
                                + ") is open, so the dead end is not a dead end");
            }
        }

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(0, report.completedCount(), "a bore that breaches nothing is not a second opening");
        assertEquals(1, report.partialCount());
        assertEquals(HiddenChamberScan.PartialReason.NO_EXIT, report.partial().get(0).reason());
    }

    /**
     * The collapse cells stopper the shaft, and that is load-bearing.
     *
     * <p>The upper-cave flood starts in the air over the mouth. Directly under it is
     * {@link HiddenChamberScan.ScanCell#COLLAPSE_POWDER} — the false floor — which is not air, so the flood
     * cannot fall down the shaft into the room and out along the corridor. Were it able to, every dead-end
     * bore in the world would read as connected to the mouth's cave and the negative above would be
     * meaningless. Asserted on the blocks rather than argued: the mouth's own standing space is open, the
     * block under it is the collapse powder, and the whole shaft below it is open air the flood never gets
     * to use.
     */
    @Test
    void theCollapseCellsStopTheFloodFromShortCircuitingDownTheShaft() {
        Grid world = lowRoofBreachWorld(2, true, false);
        for (int[] cell : LOW_MOUTH_CELLS) {
            for (int level = 1; level <= HiddenChamberPlan.MOUTH_MIN_HEADROOM; level++) {
                assertTrue(world.cell(cell[0], LOW_MOUTH_FLOOR_Y + level, cell[1]).isAir(),
                        "the flood's own seed over the mouth must be open air");
            }
            assertEquals(HiddenChamberScan.ScanCell.COLLAPSE_POWDER,
                    world.cell(cell[0], LOW_MOUTH_FLOOR_Y, cell[1]),
                    "the block under that air is the false floor, and it is not air");
            assertFalse(world.cell(cell[0], LOW_MOUTH_FLOOR_Y, cell[1]).isAir(),
                    "so the flood cannot step down into the shaft");
            assertTrue(world.cell(cell[0], LOW_MOUTH_FLOOR_Y - 1, cell[1]).isAir(),
                    "even though the shaft under it is wide open all the way to the cushion");
        }
        // The dead-end fixture proves the consequence: open shaft, open room, open corridor -- and NO_EXIT.
        assertEquals(HiddenChamberScan.PartialReason.NO_EXIT,
                scanAll(world).partial().get(0).reason());
    }

    /**
     * The shape the SECOND wrong rule refused: a breach into a low, NARROW glacial tunnel.
     *
     * <p>Modelled on the instrumented re-scan of the real failing world, whose decisive line reads
     * {@code openingCheck pos=-2790,28,9324 dist=8 tall=false open3x3=false inMouthCave=true}: five blocks
     * under the mouth floor, eight blocks out, connected to the mouth's own cave, and refused because the
     * tunnel it surfaces into is neither four blocks tall nor three columns wide. Real glacial caves are
     * neither. Here the corridor breaks into a two-block-high, two-column-wide tunnel that winds west and
     * then north through two step-downs into the mouth's cave, and the chamber must reconstruct.
     *
     * <p>The two retired tests are asserted FAILING on the breach column off the raw blocks, so this stays a
     * proof that the product law alone carries it, and not a fixture that some shape test would have passed.
     */
    @Test
    void aCorridorThatSurfacesIntoALowNarrowWindingTunnelIsStillAnExit() {
        Grid world = narrowTunnelBreachWorld();

        assertFalse(world.cell(NARROW_BREACH.x(),
                        NARROW_BREACH.y() + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + 1,
                        NARROW_BREACH.z()).isAir(),
                "tall=false: the tunnel is capped one block over the bore");
        assertFalse(hasClearStandingSquare(world, NARROW_BREACH),
                "open3x3=false: nowhere in this tunnel is three columns wide");

        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(0, report.partialCount(),
                "a low narrow tunnel is still the cave the mouth stands in, got " + report.partial());
        assertEquals(1, report.completedCount());

        HiddenChamberScan.Completed chamber = report.completed().get(0);
        assertEquals(NARROW_EXIT, chamber.exitOpening(), "the marked opening");
        assertTrue(isOnLowRoofCorridor(chamber.exitOpening()),
                "the marker stands on the authored corridor: " + chamber.exitOpening());
        int distance = Math.max(Math.abs(chamber.exitOpening().x() - LOW_CENTROID.x()),
                Math.abs(chamber.exitOpening().z() - LOW_CENTROID.z()));
        assertTrue(distance >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                        && distance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                "the opening obeys the authored distance band: " + distance);
        assertTrue(Math.abs(chamber.exitOpening().y() - LOW_MOUTH_FLOOR_Y)
                        <= HiddenChamberPlan.EXIT_FLOOR_TOLERANCE,
                "the opening obeys the authored floor band");
        assertTrue(chamber.bends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                "the winding climb still reads as a winding route: " + chamber.bends());

        assertEquals(List.of(), openingsUnderTheOldTallColumnRule(world),
                "and the old tall-column rule finds NO opening anywhere in it");
    }

    /**
     * {@code HiddenChamberScan.standsInOpenCave}, mirrored: is this station inside any square of
     * {@link HiddenChamberPlan#CORRIDOR_WIDTH}{@code + 1} columns clear for
     * {@link HiddenChamberPlan#MOUTH_MIN_HEADROOM} blocks above its own floor?
     */
    private static boolean hasClearStandingSquare(Grid world, HiddenChamberScan.Position station) {
        int span = HiddenChamberPlan.CORRIDOR_WIDTH + 1;
        for (int originX = station.x() - span + 1; originX <= station.x(); originX++) {
            for (int originZ = station.z() - span + 1; originZ <= station.z(); originZ++) {
                boolean clear = true;
                for (int dx = 0; dx < span && clear; dx++) {
                    for (int dz = 0; dz < span && clear; dz++) {
                        for (int level = 1; level <= HiddenChamberPlan.MOUTH_MIN_HEADROOM && clear; level++) {
                            clear = world.cell(originX + dx, station.y() + level, originZ + dz).isAir();
                        }
                    }
                }
                if (clear) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The exit acceptance test as it stood BEFORE the low-cave clause, run over a whole world: a station
     * (floor, corridor headroom, a partner beside it, clear of the shaft) inside both authored bands, with
     * one block of air above its own bore, under a roof.
     */
    private static List<HiddenChamberScan.Position> openingsUnderTheOldTallColumnRule(Grid world) {
        List<HiddenChamberScan.Position> found = new ArrayList<>();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = LOW_MOUTH_FLOOR_Y - HiddenChamberPlan.EXIT_FLOOR_TOLERANCE;
                        y <= LOW_MOUTH_FLOOR_Y + HiddenChamberPlan.EXIT_FLOOR_TOLERANCE; y++) {
                    int distance = Math.max(Math.abs(x - LOW_CENTROID.x()), Math.abs(z - LOW_CENTROID.z()));
                    if (distance < HiddenChamberPlan.EXIT_DISTANCE_MIN
                            || distance > HiddenChamberPlan.EXIT_DISTANCE_MAX) {
                        continue;
                    }
                    if (!isLowStation(world, x, y, z)) {
                        continue;
                    }
                    if (!world.cell(x, y + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + 1, z).isAir()) {
                        continue; // the old breakout test: taller than the bore
                    }
                    if (isRoofed(world, x, y, z)) {
                        found.add(new HiddenChamberScan.Position(x, y, z));
                    }
                }
            }
        }
        return found;
    }

    /** {@code HiddenChamberScan.station}, mirrored: two-wide standing room clear of the shaft. */
    private static boolean isLowStation(Grid world, int x, int y, int z) {
        if (!isLowStandingRoom(world, x, y, z)) {
            return false;
        }
        for (int[] step : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
            if (isLowStandingRoom(world, x + step[0], y, z + step[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLowStandingRoom(Grid world, int x, int y, int z) {
        for (int[] cell : LOW_MOUTH_CELLS) {
            if (Math.max(Math.abs(x - cell[0]), Math.abs(z - cell[1])) <= HiddenChamberPlan.SHAFT_CLEARANCE) {
                return false;
            }
        }
        return isFooting(world, x, y, z);
    }

    /** {@code HiddenChamberScan}'s roof scan: the first non-air block over the bore, within forty. */
    private static boolean isRoofed(Grid world, int x, int y, int z) {
        for (int level = HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + 1; level <= 40; level++) {
            HiddenChamberScan.ScanCell above = world.cell(x, y + level, z);
            if (above == HiddenChamberScan.ScanCell.UNREADABLE) {
                return false;
            }
            if (!above.isAir()) {
                return true;
            }
        }
        return false;
    }

    /** The low-roof fixture's own corridor columns: leg A east, leg B north, leg C west. */
    private static boolean isOnLowRoofCorridor(HiddenChamberScan.Position marker) {
        for (int x = 25; x <= 38; x++) {
            if (marker.x() == x && marker.y() == LOW_CHAMBER_FLOOR_Y + (x - 25)
                    && (marker.z() == 22 || marker.z() == 23)) {
                return true;
            }
        }
        for (int z = 16; z <= 21; z++) {
            if (marker.z() == z && marker.y() == 25 + (21 - z)
                    && (marker.x() == 37 || marker.x() == 38)) {
                return true;
            }
        }
        return marker.y() == LOW_CAVE_FLOOR_Y && marker.x() >= LOW_BREACH.x() && marker.x() <= 36
                && (marker.z() == 16 || marker.z() == 17);
    }

    /** The reference fixture's own corridor columns, so a marker can be checked against what was authored. */
    private static boolean isOnReferenceCorridor(HiddenChamberScan.Position marker) {
        for (int x = 26; x <= 32; x++) {
            if (marker.x() == x && marker.y() == x - 1 && (marker.z() == 22 || marker.z() == 23)) {
                return true;
            }
        }
        for (int z = 24; z <= 28; z++) {
            if (marker.z() == z && marker.y() == z + 8 && (marker.x() == 31 || marker.x() == 32)) {
                return true;
            }
        }
        for (int x = 27; x <= 30; x++) {
            if (marker.x() == x && marker.y() == 67 - x && (marker.z() == 28 || marker.z() == 29)) {
                return true;
            }
        }
        return false;
    }

    /** Floor with a corridor's worth of clear air over it: what {@code HiddenChamberScan} calls standing room. */
    private static boolean isFooting(Grid world, int x, int y, int z) {
        if (!world.cell(x, y, z).isFloor()) {
            return false;
        }
        for (int level = 1; level <= HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT; level++) {
            if (!world.cell(x, y + level, z).isAir()) {
                return false;
            }
        }
        return true;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Fixtures                                                                                              */
    /* ---------------------------------------------------------------------------------------------------- */

    private static HiddenChamberScan.ChamberScanReport scanAll(Grid world) {
        return HiddenChamberScan.scan(world, wholeGrid());
    }

    private static HiddenChamberScan.Bounds wholeGrid() {
        return new HiddenChamberScan.Bounds(0, 0, 0, SIZE - 1, SIZE - 1, SIZE - 1);
    }

    private static HiddenChamberScan.Completed onlyChamber(Grid world) {
        HiddenChamberScan.ChamberScanReport report = scanAll(world);
        assertEquals(0, report.partialCount(), "expected one complete chamber, got " + report.partial());
        assertEquals(1, report.completedCount());
        return report.completed().get(0);
    }

    /** Flat glacial cave: solid rock, a snow floor at y=40, four blocks of air, a solid roof. */
    private static Grid emptyCave() {
        Grid world = new Grid();
        world.fill(0, SIZE - 1, CAVE_FLOOR_Y, CAVE_FLOOR_Y, 0, SIZE - 1, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(0, SIZE - 1, CAVE_FLOOR_Y + 1, CAVE_CEILING_Y - 1, 0, SIZE - 1,
                HiddenChamberScan.ScanCell.AIR);
        return world;
    }

    /** The full reference encounter: mouth, shafts, cushion, shelf, three-height room, winding corridor. */
    private static Grid referenceWorld() {
        Grid world = emptyCave();

        // The room: three ceiling bands, so the roof reads as a roof and not a lid.
        world.fill(14, 17, LANDING_Y, LANDING_Y + 3, 15, 24, HiddenChamberScan.ScanCell.AIR);
        world.fill(18, 21, LANDING_Y, LANDING_Y + 4, 15, 24, HiddenChamberScan.ScanCell.AIR);
        world.fill(22, 25, LANDING_Y, LANDING_Y + 5, 15, 24, HiddenChamberScan.ScanCell.AIR);

        // The cushion on its packed-ice base, then the shafts down to it, then the false floor on top.
        world.fill(18, 22, CHAMBER_FLOOR_Y, CHAMBER_FLOOR_Y, 18, 22, HiddenChamberScan.ScanCell.ICE_PACKED);
        world.fill(18, 22, LANDING_Y, LANDING_Y + 1, 18, 22, HiddenChamberScan.ScanCell.POWDER_SNOW);
        for (int[] cell : MOUTH_CELLS) {
            world.fill(cell[0], cell[0], LANDING_Y + 2, MOUTH_FLOOR_Y - 1, cell[1], cell[1],
                    HiddenChamberScan.ScanCell.AIR);
        }
        mouth(world);

        // The escape shelf: firm footing level with the powder, one step off it, with room to stand.
        world.fill(16, 17, CHAMBER_FLOOR_Y, CHAMBER_FLOOR_Y, 19, 20, HiddenChamberScan.ScanCell.SNOW_FIRM);

        fillCorridor(world, HiddenChamberScan.ScanCell.AIR);
        return world;
    }

    private static void mouth(Grid world) {
        for (int[] cell : MOUTH_CELLS) {
            world.set(cell[0], MOUTH_FLOOR_Y, cell[1], HiddenChamberScan.ScanCell.COLLAPSE_POWDER);
        }
    }

    /**
     * The two-wide winding climb out: east along z=22..23, north along x=31..32, then west along z=28..29 until
     * it breaks the cave floor. Two bends minimum, and it never passes within
     * {@link HiddenChamberPlan#SHAFT_CLEARANCE} of a shaft column.
     */
    private static void fillCorridor(Grid world, HiddenChamberScan.ScanCell cell) {
        for (int x = 26; x <= 32; x++) {
            int floor = x - 1;
            world.fill(x, x, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, 22, 23, cell);
        }
        for (int z = 24; z <= 28; z++) {
            int floor = z + 8;
            world.fill(31, 32, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, z, z, cell);
        }
        for (int x = 30; x >= 27; x--) {
            int floor = 67 - x;
            world.fill(x, x, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, 28, 29, cell);
        }
    }

    /**
     * A deep frigid-lake encounter: a nineteen-block fall into a twenty-one by fifteen room whose near half is
     * open water, with the escape shelf standing in it as a two-by-two island and the only dry ground — and
     * with it the corridor — on the far shore.
     *
     * <p>The room is roofed in three bands so its ceiling reads as a roof rather than a lid, and the corridor
     * is a switchback: east out of the shore, north up the wall, then west until it breaks the cave floor
     * twelve blocks from the mouth centroid.
     */
    private static Grid deepLakeWorld() {
        Grid world = emptyCave();

        // The room, in three ceiling bands, on one packed-ice floor.
        world.fill(8, 15, LAKE_LANDING_Y, 26, 14, 28, HiddenChamberScan.ScanCell.AIR);
        world.fill(16, 21, LAKE_LANDING_Y, 28, 14, 28, HiddenChamberScan.ScanCell.AIR);
        world.fill(22, 28, LAKE_LANDING_Y, 30, 14, 28, HiddenChamberScan.ScanCell.AIR);
        world.fill(8, 28, LAKE_FLOOR_Y, LAKE_FLOOR_Y, 14, 28, HiddenChamberScan.ScanCell.ICE_PACKED);

        // The lake. Everything west of the far shore stands under water, so none of it can be walked.
        world.fill(8, 21, LAKE_LANDING_Y, LAKE_LANDING_Y, 14, 28, HiddenChamberScan.ScanCell.WATER);
        // ...except the escape shelf, which the water leaves as an island.
        world.fill(15, 16, LAKE_LANDING_Y, LAKE_LANDING_Y, 20, 21, HiddenChamberScan.ScanCell.AIR);

        // The cushion on its ice base, the shafts down to it, and the false floor on top.
        world.fill(11, 14, LAKE_LANDING_Y, LAKE_LANDING_Y + 1, 19, 22,
                HiddenChamberScan.ScanCell.POWDER_SNOW);
        for (int[] cell : LAKE_MOUTH_CELLS) {
            world.fill(cell[0], cell[0], LAKE_LANDING_Y + 2, MOUTH_FLOOR_Y - 1, cell[1], cell[1],
                    HiddenChamberScan.ScanCell.AIR);
            world.set(cell[0], MOUTH_FLOOR_Y, cell[1], HiddenChamberScan.ScanCell.COLLAPSE_POWDER);
        }

        lakeCorridor(world, HiddenChamberScan.ScanCell.AIR);
        return world;
    }

    /**
     * The lake room's way out, bored through the rock beyond the room's east wall: east along z=18..19 out of
     * the far shore, north along x=33..34, then west along z=32..33 to a final station that climbs through the
     * cave floor. Every leg is two wide, the climb never rises more than a block a step, and the whole corridor
     * stays far outside {@link HiddenChamberPlan#SHAFT_CLEARANCE} of the shaft.
     */
    private static void lakeCorridor(Grid world, HiddenChamberScan.ScanCell cell) {
        for (int x = 29; x <= 33; x++) {
            int floor = x - 9;
            world.fill(x, x, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, 18, 19, cell);
        }
        for (int z = 20; z <= 31; z++) {
            int floor = z + 5;
            world.fill(33, 34, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, z, z, cell);
        }
        for (int x = 25; x <= 33; x++) {
            world.fill(x, x, 37, 39, 32, 33, cell);
        }
        world.fill(24, 24, 38, 40, 32, 33, cell);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* The low-roof breach fixture                                                                            */
    /* ---------------------------------------------------------------------------------------------------- */

    private static Grid lowRoofBreachWorld(int caveHeadroom) {
        return lowRoofBreachWorld(caveHeadroom, true, true);
    }

    /**
     * A deep encounter whose corridor surfaces into a LOW cave section, modelled on the real world that
     * failed: mouth floor y=33, landing y=13, and a terminus at y=30 twelve blocks from the mouth centroid.
     *
     * <p>The upper cave is deliberately only {@link HiddenChamberPlan#MOUTH_MIN_HEADROOM} blocks high — the
     * least the planner's own mouth and terminus laws accept — and it steps down twice, from the mouth's
     * floor at y=33 to the low section's floor at y=30, so both openings stand in ONE cave whose air is
     * continuous. The corridor climbs eighteen blocks out of the room in three legs (east, north, west) and
     * bores its last leg through the rock into the low section's first column.
     *
     * @param caveHeadroom  how many blocks of natural clear space the low section carries: two or three,
     *                      both shorter than the four the scanner's old breakout test silently demanded
     * @param connectCaves  false leaves the low section a sealed pocket, so the exit surfaces into a cave
     *                      that is NOT the one the mouth stands in
     * @param boreTheTerminus false stops the last leg one column short, so the walk's furthest station is
     *                      the last BORED column instead of the cave's own
     */
    private static Grid lowRoofBreachWorld(int caveHeadroom, boolean connectCaves, boolean boreTheTerminus) {
        Grid world = new Grid();

        // 1. The upper cave the mouth is hidden in: floor y=33, two blocks of headroom, solid rock over it.
        world.fill(5, 24, LOW_MOUTH_FLOOR_Y, LOW_MOUTH_FLOOR_Y, 5, 35, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(5, 24, LOW_MOUTH_FLOOR_Y + 1,
                LOW_MOUTH_FLOOR_Y + HiddenChamberPlan.MOUTH_MIN_HEADROOM, 5, 35,
                HiddenChamberScan.ScanCell.AIR);

        // 2. Two steps down to the low section, so one continuous body of air holds both openings.
        if (connectCaves) {
            world.fill(25, 25, 32, 32, 12, 26, HiddenChamberScan.ScanCell.SNOW_FIRM);
            world.fill(25, 25, 33, 34, 12, 26, HiddenChamberScan.ScanCell.AIR);
            world.fill(26, 26, 31, 31, 12, 26, HiddenChamberScan.ScanCell.SNOW_FIRM);
            world.fill(26, 26, 32, 33, 12, 26, HiddenChamberScan.ScanCell.AIR);
        }

        // 3. The low section itself: floor y=30, and a ceiling exactly `caveHeadroom` blocks over it.
        world.fill(27, 33, LOW_CAVE_FLOOR_Y, LOW_CAVE_FLOOR_Y, 12, 26, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(27, 33, LOW_CAVE_FLOOR_Y + 1, LOW_CAVE_FLOOR_Y + caveHeadroom, 12, 26,
                HiddenChamberScan.ScanCell.AIR);

        // 4. The room, its cushion, the shafts up to the cave, and the false floor on top.
        lowRoofRoomAndShaft(world);

        lowRoofCorridor(world, boreTheTerminus);
        return world;
    }

    /**
     * A lake chamber standing on a HOLLOW under-floor, which is what real terrain gives and no earlier
     * fixture did.
     *
     * <p>Built on the low-roof encounter, then two things are added. A six-by-six pool of standing water on
     * its packed-ice bed, which is the lake. And a dug-out patch of the room's own floor sown with a
     * checkerboard of single packed-ice blocks: those are the FLOOR SEALS the planner lays under interior
     * columns wherever the ground beneath the room is hollow. Every one of them is free-standing with void on
     * all four sides, which is exactly what the old interior-ice tally counted and exactly what a floor is
     * not.
     */
    private static Grid hollowUnderFloorLakeWorld() {
        Grid world = lowRoofBreachWorld(2);

        // The lake: standing water on the room's own packed-ice floor, well clear of the shaft and shelf.
        world.fill(10, 15, LOW_LANDING_Y, LOW_LANDING_Y, 14, 19, HiddenChamberScan.ScanCell.WATER);

        // The hollow under-floor: dig the floor away and leave the seals standing proud of it.
        world.fill(16, 23, LOW_CHAMBER_FLOOR_Y, LOW_CHAMBER_FLOOR_Y, 14, 19,
                HiddenChamberScan.ScanCell.AIR);
        for (int x = 17; x <= 22; x++) {
            for (int z = 15; z <= 18; z++) {
                if ((x + z) % 2 == 0) {
                    world.set(x, LOW_CHAMBER_FLOOR_Y, z, HiddenChamberScan.ScanCell.ICE_PACKED);
                }
            }
        }
        return world;
    }

    /**
     * The same encounter, but the corridor surfaces into a low NARROW winding tunnel instead of a room-sized
     * low section: two blocks of headroom over its floor and never more than
     * {@link HiddenChamberPlan#CORRIDOR_WIDTH} columns wide anywhere along it.
     *
     * <p>From the breach at x=33 it runs west along z=16..17 to x=27, turns north along x=27..28 to z=10,
     * and then climbs two single-column steps back up to the mouth's own cave floor at y=33. Every leg is
     * two columns wide, so no square of three ever exists — which is precisely the shape the second retired
     * shape test refused, and precisely what the real world had.
     */
    private static Grid narrowTunnelBreachWorld() {
        Grid world = new Grid();

        world.fill(5, 24, LOW_MOUTH_FLOOR_Y, LOW_MOUTH_FLOOR_Y, 5, 35, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(5, 24, LOW_MOUTH_FLOOR_Y + 1,
                LOW_MOUTH_FLOOR_Y + HiddenChamberPlan.MOUTH_MIN_HEADROOM, 5, 35,
                HiddenChamberScan.ScanCell.AIR);

        // Two single-column steps down from the mouth's cave into the tunnel's far end.
        world.fill(25, 25, 32, 32, 10, 11, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(25, 25, 33, 34, 10, 11, HiddenChamberScan.ScanCell.AIR);
        world.fill(26, 26, 31, 31, 10, 11, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(26, 26, 32, 33, 10, 11, HiddenChamberScan.ScanCell.AIR);

        // The tunnel: a north leg and a west leg, each two columns wide, two blocks of headroom.
        world.fill(27, 28, LOW_CAVE_FLOOR_Y, LOW_CAVE_FLOOR_Y, 10, 15, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(27, 28, LOW_CAVE_FLOOR_Y + 1,
                LOW_CAVE_FLOOR_Y + HiddenChamberPlan.MOUTH_MIN_HEADROOM, 10, 15,
                HiddenChamberScan.ScanCell.AIR);
        world.fill(27, 33, LOW_CAVE_FLOOR_Y, LOW_CAVE_FLOOR_Y, 16, 17, HiddenChamberScan.ScanCell.SNOW_FIRM);
        world.fill(27, 33, LOW_CAVE_FLOOR_Y + 1,
                LOW_CAVE_FLOOR_Y + HiddenChamberPlan.MOUTH_MIN_HEADROOM, 16, 17,
                HiddenChamberScan.ScanCell.AIR);

        lowRoofRoomAndShaft(world);
        lowRoofCorridor(world, true);
        return world;
    }

    /** The room, cushion, shafts and false floor the low-roof fixtures share. */
    private static void lowRoofRoomAndShaft(Grid world) {
        world.fill(10, 24, LOW_CHAMBER_FLOOR_Y, LOW_CHAMBER_FLOOR_Y, 14, 26,
                HiddenChamberScan.ScanCell.ICE_PACKED);
        world.fill(10, 14, LOW_LANDING_Y, 17, 14, 26, HiddenChamberScan.ScanCell.AIR);
        world.fill(15, 19, LOW_LANDING_Y, 18, 14, 26, HiddenChamberScan.ScanCell.AIR);
        world.fill(20, 24, LOW_LANDING_Y, 19, 14, 26, HiddenChamberScan.ScanCell.AIR);
        for (int[] cell : LOW_MOUTH_CELLS) {
            world.fill(cell[0], cell[0], LOW_LANDING_Y, LOW_LANDING_Y + HiddenChamberPlan.CUSHION_DEPTH - 1,
                    cell[1], cell[1], HiddenChamberScan.ScanCell.POWDER_SNOW);
            world.fill(cell[0], cell[0], LOW_LANDING_Y + HiddenChamberPlan.CUSHION_DEPTH,
                    LOW_MOUTH_FLOOR_Y - 1, cell[1], cell[1], HiddenChamberScan.ScanCell.AIR);
            world.set(cell[0], LOW_MOUTH_FLOOR_Y, cell[1], HiddenChamberScan.ScanCell.COLLAPSE_POWDER);
        }
    }

    /**
     * The eighteen-block climb out, two wide the whole way and never within
     * {@link HiddenChamberPlan#SHAFT_CLEARANCE} of a shaft column: east out of the room along z=22..23
     * rising a block a step, north along x=37..38 rising a block a step, then west along z=16..17 at the low
     * section's own floor until it breaks into it. Three headings, so two bends.
     */
    private static void lowRoofCorridor(Grid world, boolean boreTheTerminus) {
        for (int x = 25; x <= 38; x++) {
            int floor = LOW_CHAMBER_FLOOR_Y + (x - 25);
            world.fill(x, x, floor, floor, 22, 23, HiddenChamberScan.ScanCell.SNOW_FIRM);
            world.fill(x, x, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, 22, 23,
                    HiddenChamberScan.ScanCell.AIR);
        }
        for (int z = 21; z >= 16; z--) {
            int floor = 25 + (21 - z);
            world.fill(37, 38, floor, floor, z, z, HiddenChamberScan.ScanCell.SNOW_FIRM);
            world.fill(37, 38, floor + 1, floor + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, z, z,
                    HiddenChamberScan.ScanCell.AIR);
        }
        for (int x = boreTheTerminus ? LOW_BREACH.x() : LOW_BREACH.x() + 2; x <= 36; x++) {
            world.fill(x, x, LOW_CAVE_FLOOR_Y, LOW_CAVE_FLOOR_Y, 16, 17,
                    HiddenChamberScan.ScanCell.SNOW_FIRM);
            world.fill(x, x, LOW_CAVE_FLOOR_Y + 1,
                    LOW_CAVE_FLOOR_Y + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT, 16, 17,
                    HiddenChamberScan.ScanCell.AIR);
        }
    }

    /** Array-backed world. Anything outside it is {@link HiddenChamberScan.ScanCell#UNREADABLE}, by contract. */
    private static final class Grid implements HiddenChamberScan.CellReader {
        private final HiddenChamberScan.ScanCell[][][] cells =
                new HiddenChamberScan.ScanCell[SIZE][SIZE][SIZE];

        private Grid() {
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    for (int z = 0; z < SIZE; z++) {
                        cells[x][y][z] = HiddenChamberScan.ScanCell.OTHER_SOLID;
                    }
                }
            }
        }

        @Override
        public HiddenChamberScan.ScanCell cell(int x, int y, int z) {
            if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
                return HiddenChamberScan.ScanCell.UNREADABLE;
            }
            return cells[x][y][z];
        }

        private void set(int x, int y, int z, HiddenChamberScan.ScanCell cell) {
            cells[x][y][z] = cell;
        }

        private void fill(int x0, int x1, int y0, int y1, int z0, int z1, HiddenChamberScan.ScanCell cell) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        cells[x][y][z] = cell;
                    }
                }
            }
        }
    }
}
