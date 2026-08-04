package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end-to-end gate the hidden-chamber suite was missing: PLAN -> BLOCKS -> SCAN, in one pure JVM test.
 *
 * <p>{@link HiddenChamberPlan} decides what to write and {@link HiddenChamberScan} reads a standing chamber
 * back out of real blocks, and until now NOTHING ran one into the other. That gap is exactly how a planner
 * that sealed a slab of packed ice over every collapse mouth -- and capped its own exit bore -- passed a
 * four-figure green suite: every test looked at the plan's own vocabulary, and the scanner was only ever fed
 * hand-built worlds. This test closes the loop. It renders an accepted plan onto the very terrain the planner
 * was given, using the same role-to-material vocabulary the world layer ships, and then asks the blocks
 * themselves whether a chamber a player can fall into and climb out of is standing there.
 *
 * <h2>The rendering</h2>
 * {@link #ROLE_CELLS} mirrors {@code HiddenGlacialChamberFeature.roleStates()} composed with
 * {@code LatitudeDevCommands.ChamberCellReader.classify}: role -> block -> {@link HiddenChamberScan.ScanCell}.
 * It is asserted TOTAL over {@link HiddenChamberPlan.CellRole} for the same reason the shipping map is -- a
 * new role with no entry must fail here loudly instead of rendering as rock. Two entries are worth naming:
 * {@code FROST_MOTE} is a single snow LAYER, which the shipping reader resolves through its empty collision
 * shape to {@link HiddenChamberScan.ScanCell#AIR}, and {@code DEBRIS_WOOD} is a spruce fence, which the
 * shipping reader names explicitly as {@link HiddenChamberScan.ScanCell#WOOD_DEBRIS} before the collision
 * test can reach it.
 *
 * <h2>How far the scan gets, and why</h2>
 * The reconstruction runs mouth -> shaft -> cushion -> shelf -> chamber void -> second opening, and it must
 * run all the way: EVERY rendered plan reconstructs as exactly one {@link HiddenChamberScan.Completed}, whose
 * mouth, landing, drop, exit and theme are checked against the plan's own metadata. That is the standing
 * regression floor for the scanner and it is asserted twice over -- once per theme on one case, and once
 * across a seventy-two-case mixed seed/chunk sweep.
 *
 * <p>It was not always so. This gate first shipped asserting only that the reconstruction REACHED the exit
 * stage, because every rendered plan died there with {@link HiddenChamberScan.PartialReason#NO_EXIT}. That
 * was a SCANNER bug, now fixed: the exit walk was boxed to {@code EXIT_DISTANCE_MAX + 2} blocks around the
 * mouth centroid, while the planner winds its corridor anywhere in the authored envelope -- out to
 * twenty-six blocks from a mouth sitting at the near edge of the owner chunk -- so the walk hit the box wall
 * mid-climb. The box now contains the ENVELOPE instead of the goal band, and the soft acceptance that stood
 * in for the fix is gone with it. Nothing here may be relaxed back to it: a {@code Partial} of any reason is
 * a failure.
 *
 * <p>Coordinates: {@link HiddenChamberPlan} speaks owner-chunk-local and {@link HiddenChamberScan} speaks
 * world, so this test places the owner chunk at world origin and the two frames coincide.
 */
class HiddenChamberPlanToBlocksScanTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;

    /**
     * The breadth sweep: twelve seeds x six chunks = seventy-two mixed cases, themes rotating through all
     * three. The first four seeds and the first four chunks are the original sixteen-case grid, in their
     * original order, so every case that grid ever named still means the same {@code (seed, chunk, theme)}
     * triple -- including the two the planner used to fail on. It is also still a superset of the grid
     * {@link HiddenChamberSealLawTest} sweeps, so a case that fails here and a case that fails there name
     * each other.
     */
    private static final long[] SWEEP_SEEDS = {
        1L, 0x5EED_C0FFEEL, -1234L, 987_654_321L,
        7L, -99L, 424_242L, 0x1234_5678L, -0x5EED_C0FFEEL, 31L, -777_777L, 20_260_803L};
    private static final int[][] SWEEP_CHUNKS = {
        {0, 0}, {4, -9}, {11, 7}, {-13, 21}, {-3, -5}, {28, 14}};
    /**
     * How many cases must reconstruct COMPLETE for the sweep to be worth anything. Every case in the grid
     * now completes, so this is the floor that keeps the grid from being quietly shrunk back to a size that
     * no longer exercises the corridor shapes a deep drop into a compact room produces.
     */
    private static final int MIN_SWEEP_CASES = 72;

    /** The rendered window. Wide enough for the whole owner-plus-one-neighbour envelope in any direction. */
    private static final int SCAN_MIN_XZ = -20;
    private static final int SCAN_MAX_XZ = 35;
    private static final int SCAN_MIN_Y = 1;
    private static final int SCAN_MAX_Y = 47;
    /** Outside this the reader answers UNREADABLE, as a real world does past its build limits. */
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_MAX_Y = 320;
    /** {@code HiddenChamberScan}'s own roof scan over a candidate opening; its constant is private there. */
    private static final int EXIT_ROOF_SCAN_HEIGHT = 40;

    /* ---------------------------------------------------------------------------------------------------- */
    /* The gate                                                                                              */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void everyThemesAcceptedPlanRendersToBlocksThatReconstructAsOneChamber() {
        for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
            HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(
                    SEED, CHUNK_X, CHUNK_Z, HiddenChamberTerrainFixtures.flatCave(), theme);
            assertTrue(result.isAccepted(), theme + " must accept on a flat cave: " + result.detail());
            HiddenChamberPlan.Plan plan = result.accepted();

            HiddenChamberScan.CellReader world = render(HiddenChamberTerrainFixtures.flatCave(), plan);
            reconstructExactlyOneCompleteChamber(world, plan, theme, theme.toString());

            /* The two openings, read straight off the rendered blocks -- this is the seal law's own gate. */
            assertTheFallLandsOnTheAuthoredCushion(world, plan, theme.name());
            assertAuthoredExitPortalIsOpen(world, plan, theme.name());
        }
    }

    /**
     * The breadth floor: seventy-two mixed {@code (seed, chunk, theme)} cases render to blocks and
     * reconstruct. One case proves the pipeline runs; a sweep is what catches a containment bound that
     * happens to be wide enough for one corridor and not the next, which is exactly the failure this file was
     * written around.
     *
     * <p>EVERY case reconstructs as one complete chamber. Two of them -- {@code seed=-1234} at chunks
     * {@code (0,0)} and {@code (-13,21)}, both {@code LOST_EXPEDITION}, {@code COMPACT}, {@code drop=20},
     * the deepest legal fall inside the smallest legal room -- used to be PINNED here as known planner
     * failures, and they are the reason the sweep grid keeps its original sixteen cases as a prefix. Their
     * corridor had to climb twenty-one blocks in a tight spiral and CROSSED BACK OVER ITSELF: where it did,
     * the earlier pass's three-block bore had already carved away the floor of a station the later pass stood
     * on. At {@code (0,0)} the column {@code (12,3)} was clear from y=26 to y=30, which left the later station
     * at y=27 standing on air, so the only floored column left at that height, {@code (12,2)}, had no partner
     * beside it and the two-wide corridor pinched to one -- and the scanner was RIGHT to refuse to walk it.
     * The route search now refuses to build that station at all, so nothing here is pinned and nothing may be
     * pinned again: a {@code Partial} of any reason, for any case, is a failure.
     *
     * @see #noCorridorEverBoresThroughItsOwnFloorAcrossTheSweepGrid() the write-list law that keeps it fixed
     */
    @Test
    void everyCaseInAMixedSeedAndChunkSweepReconstructsAsOneCompleteChamber() {
        int completeCases = 0;
        for (int seedIndex = 0; seedIndex < SWEEP_SEEDS.length; seedIndex++) {
            for (int chunkIndex = 0; chunkIndex < SWEEP_CHUNKS.length; chunkIndex++) {
                long seed = SWEEP_SEEDS[seedIndex];
                int chunkX = SWEEP_CHUNKS[chunkIndex][0];
                int chunkZ = SWEEP_CHUNKS[chunkIndex][1];
                HiddenChamberPlan.Theme theme = sweepTheme(seedIndex, chunkIndex);
                String label = "seed=" + seed + " chunk=(" + chunkX + "," + chunkZ + ") theme=" + theme;

                HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(
                        seed, chunkX, chunkZ, HiddenChamberTerrainFixtures.flatCave(), theme);
                assertTrue(result.isAccepted(), label + " must accept on a flat cave: " + result.detail());
                HiddenChamberPlan.Plan plan = result.accepted();

                HiddenChamberScan.CellReader world =
                        render(HiddenChamberTerrainFixtures.flatCave(), plan);
                reconstructExactlyOneCompleteChamber(world, plan, theme, label);
                completeCases++;
                /* Both openings' own blocks are asserted for EVERY case: neither a capped mouth nor a
                 * walled-shut portal may hide behind a reconstruction that happened to complete. */
                assertTheFallLandsOnTheAuthoredCushion(world, plan, label);
                assertAuthoredExitPortalIsOpen(world, plan, label);
            }
        }
        assertEquals(SWEEP_SEEDS.length * SWEEP_CHUNKS.length, completeCases,
                "every case in the sweep grid must reconstruct");
        assertTrue(completeCases >= MIN_SWEEP_CASES,
                "the round-trip sweep must reconstruct at least " + MIN_SWEEP_CASES
                        + " complete chambers, got " + completeCases);
    }

    /** The grid's theme rotation, shared by both sweeps so one case means one thing in each. */
    private static HiddenChamberPlan.Theme sweepTheme(int seedIndex, int chunkIndex) {
        return HiddenChamberPlan.Theme.values()[
                (seedIndex + chunkIndex) % HiddenChamberPlan.Theme.values().length];
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* The planner law the pin above became                                                                  */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * A corridor never bores through its own floor: the direct, planner-level form of the defect the sweep
     * above used to pin, read off the write list with no rendering and no scanner in the way.
     *
     * <p>The write list does not expose the private station sequence, but it does not have to. Every station
     * owns {@link HiddenChamberPlan#CORRIDOR_WIDTH} columns and, in each, one floor cell plus exactly
     * {@link HiddenChamberPlan#CORRIDOR_CLEAR_HEIGHT} bored cells directly above it. So in ONE column, two
     * stations standing four or more blocks apart bore two runs with a solid block between them, and two
     * stations standing one, two or three blocks apart bore ONE merged run longer than the bore height --
     * and that merged run is precisely the upper station's missing floor, because a carve outranks a floor
     * ({@link HiddenChamberPlan.CellRole#CORRIDOR_CLEAR} over
     * {@link HiddenChamberPlan.CellRole#CORRIDOR_FLOOR}) so the floor write never lands. A maximal vertical
     * run of corridor clear cells longer than the bore height is therefore exactly the defect, with no false
     * positives and nothing else to measure.
     *
     * <p>Two weaker formulations were tried against the pre-fix planner and REJECTED for not catching it:
     * that no {@code CORRIDOR_FLOOR} coordinate also carries a clear role (the {@code Plan} constructor's
     * unique-coordinate check already guarantees that, so it can never fail), and that the bottom of every
     * clear run stands on something firm (measured green on all seventy-two pre-fix cases -- a run's bottom
     * belongs to the LOWEST station in that column, which is exactly the one station whose floor no other
     * pass can have taken). The carved floor is always in the MIDDLE of a run; only run length sees it.
     */
    @Test
    void noCorridorEverBoresThroughItsOwnFloorAcrossTheSweepGrid() {
        int checked = 0;
        for (int seedIndex = 0; seedIndex < SWEEP_SEEDS.length; seedIndex++) {
            for (int chunkIndex = 0; chunkIndex < SWEEP_CHUNKS.length; chunkIndex++) {
                long seed = SWEEP_SEEDS[seedIndex];
                int chunkX = SWEEP_CHUNKS[chunkIndex][0];
                int chunkZ = SWEEP_CHUNKS[chunkIndex][1];
                HiddenChamberPlan.Theme theme = sweepTheme(seedIndex, chunkIndex);
                String label = "seed=" + seed + " chunk=(" + chunkX + "," + chunkZ + ") theme=" + theme;

                HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(
                        seed, chunkX, chunkZ, HiddenChamberTerrainFixtures.flatCave(), theme);
                assertTrue(result.isAccepted(), label + " must accept on a flat cave: " + result.detail());
                assertNoStationStandsInAnotherStationsBore(result.accepted(), label);
                checked++;
            }
        }
        assertTrue(checked >= MIN_SWEEP_CASES,
                "the corridor self-crossing law must be swept over at least " + MIN_SWEEP_CASES
                        + " accepted plans, got " + checked);
    }

    private static void assertNoStationStandsInAnotherStationsBore(HiddenChamberPlan.Plan plan, String label) {
        Map<Long, TreeSet<Integer>> bore = new TreeMap<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (cell.role() != HiddenChamberPlan.CellRole.CORRIDOR_CLEAR
                    && cell.role() != HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR) {
                continue;
            }
            bore.computeIfAbsent(columnKey(cell.x(), cell.z()), key -> new TreeSet<>()).add(cell.y());
        }
        assertTrue(!bore.isEmpty(), label + ": an accepted plan always bores an exit corridor");

        for (Map.Entry<Long, TreeSet<Integer>> column : bore.entrySet()) {
            int x = (int) (column.getKey() >> 32) - 4096;
            int z = (int) (column.getKey() & 0xFFFFFFFFL) - 4096;
            int runStart = Integer.MIN_VALUE;
            int previous = Integer.MIN_VALUE;
            for (int y : column.getValue()) {
                if (y != previous + 1) {
                    runStart = y;
                }
                previous = y;
                assertTrue(previous - runStart + 1 <= HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT,
                        label + ": the corridor column (" + x + "," + z + ") is bored clear from y=" + runStart
                                + " to y=" + previous + ", which is longer than the "
                                + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + "-block bore one station owns -- "
                                + "two stations stand within a bore of each other there, so the upper one's "
                                + "floor was carved away and the two-wide corridor pinches to one. Bored "
                                + "heights in that column: " + column.getValue());
            }
        }

        /* The same law from the floor's side: every corridor floor the planner writes carries its own bore. */
        for (HiddenChamberPlan.Cell floor : plan.writesWithRole(HiddenChamberPlan.CellRole.CORRIDOR_FLOOR)) {
            TreeSet<Integer> column = bore.get(columnKey(floor.x(), floor.z()));
            assertNotNull(column, label + ": corridor floor " + floor + " has no bore above it at all");
            for (int level = 1; level <= HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT; level++) {
                assertTrue(column.contains(floor.y() + level),
                        label + ": corridor floor " + floor + " is not clear at y=" + (floor.y() + level)
                                + " -- a station's floor and its own bore are written together");
            }
        }
    }

    /**
     * Render-and-read the one law this file exists for: one plan in, exactly one {@code Completed} out, and
     * every field of it agreeing with the plan that built it. A {@code Partial} is a failure whatever its
     * reason -- there is no stage short of the second opening that counts as passing.
     */
    private static void reconstructExactlyOneCompleteChamber(HiddenChamberScan.CellReader world,
                                                             HiddenChamberPlan.Plan plan,
                                                             HiddenChamberPlan.Theme theme,
                                                             String label) {
        HiddenChamberScan.ChamberScanReport report = HiddenChamberScan.scan(world,
                new HiddenChamberScan.Bounds(SCAN_MIN_XZ, SCAN_MIN_Y, SCAN_MIN_XZ,
                        SCAN_MAX_XZ, SCAN_MAX_Y, SCAN_MAX_XZ));

        /* One encounter went in, so exactly one collapse patch comes out, and it is never the old trap. */
        assertEquals(0, report.legacyCount(), label + ": a chamber mouth is never the legacy drop trap");
        assertEquals(1, report.patches(), label + ": the rendered world holds exactly one collapse patch");
        assertEquals(plan.mouthCells().size(), report.collapseCells(),
                label + ": every authored collapse cell is readable back as one");
        assertEquals(0, report.partialCount(),
                label + ": an accepted plan rendered to blocks must reconstruct COMPLETE, got "
                        + report.partial());
        assertEquals(1, report.completedCount(),
                label + ": exactly one complete chamber must stand in the rendered blocks");

        assertCompleteChamberMatchesThePlan(report.completed().get(0), plan, theme, label);
    }

    private static void assertCompleteChamberMatchesThePlan(HiddenChamberScan.Completed chamber,
                                                            HiddenChamberPlan.Plan plan,
                                                            HiddenChamberPlan.Theme theme,
                                                            String label) {
        HiddenChamberScan.Position centroid = new HiddenChamberScan.Position(
                plan.mouthCentroid().x(), plan.mouthCentroid().y(), plan.mouthCentroid().z());
        assertEquals(centroid, chamber.mouthCentroid(), label + ": reconstructed mouth centroid");
        assertEquals(plan.mouthCells().size(), chamber.mouthCells().size(), label + ": mouth cells");
        assertEquals(plan.landingY(), chamber.landing().y(), label + ": reconstructed landing height");
        assertEquals(plan.drop(), chamber.drop(), label + ": reconstructed drop");
        // The planner reports the CUSHION centroid and the scanner the MOUTH centroid; the cushion is the
        // mouth footprint dilated by one, so the two agree to within that dilation plus rounding.
        assertTrue(Math.max(Math.abs(chamber.landing().x() - plan.landing().x()),
                        Math.abs(chamber.landing().z() - plan.landing().z()))
                        <= HiddenChamberPlan.CUSHION_DILATION + 1,
                label + ": landing " + chamber.landing() + " is not the authored cushion " + plan.landing());
        int exitDistance = Math.max(Math.abs(chamber.exitOpening().x() - chamber.mouthCentroid().x()),
                Math.abs(chamber.exitOpening().z() - chamber.mouthCentroid().z()));
        assertTrue(exitDistance >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                        && exitDistance <= HiddenChamberPlan.EXIT_DISTANCE_MAX,
                label + ": the second opening sits at " + exitDistance + " from the mouth");
        assertTrue(chamber.bends() >= HiddenChamberPlan.CORRIDOR_MIN_BENDS,
                label + ": the escape route winds, " + chamber.bends() + " bends");
        assertTrue(chamber.voidVolume() >= HiddenChamberScan.MIN_VOID_VOLUME,
                label + ": the carved room is " + chamber.voidVolume() + " cells");
        assertEquals(HiddenChamberScan.Theme.valueOf(theme.name()), chamber.themeGuess(),
                label + ": the standing dressing must read back as its own theme, tallies "
                        + chamber.tallies());
        assertExitIsTheAuthoredPortal(chamber, plan, label);
    }

    /**
     * The reconstructed second opening stands on the way out the PLANNER bored, not on some unrelated patch
     * of cave floor the walk wandered onto.
     *
     * <p>This assertion exists so the exit checks above cannot be satisfied by any old floor in the right
     * distance band -- which is precisely what a scanner with too wide a walk could go and find. It used to
     * say something narrower: that the marker coincides with the authored TERMINAL station to within
     * two blocks and sits at exactly the terminus floor. That is no longer the
     * law, and the reason is a fix rather than a slip.
     *
     * <p>{@code HiddenChamberScan.isOpening} now recognises an opening by the product law alone -- a station
     * whose head air joins the air the MOUTH stands in -- because both of the shape tests that used to stand
     * in for it (a column taller than the bore, then a square wider than the bore) each refused a legal exit
     * standing open in the blocks of a real world. Under the product law every station from the breach back
     * down the bore qualifies, and each one qualifies for a good reason: its head air can only have reached
     * the mouth's cave by going out through the breach ahead of it, so it PROVES the corridor surfaces. The
     * breadth-first walk names the first such station it reaches, which is a few blocks short of the
     * terminus. Thirteen of the seventy-two cases below moved that way when the law changed; every one of
     * them stayed inside both authored bands, and every one of them still lands where this method insists:
     *
     * <ul>
     *   <li>on a column this plan BORED -- {@link HiddenChamberPlan.CellRole#CORRIDOR_CLEAR} or
     *       {@link HiddenChamberPlan.CellRole#EXIT_PORTAL_CLEAR} -- so the marker is always somewhere the
     *       planner built, never a neighbouring cave;</li>
     *   <li>at that bore's own station floor, one block under its lowest clear cell, so the marker is a
     *       station of the authored corridor rather than a coordinate that merely lands near one;</li>
     *   <li>never above the authored terminus floor, which is the corridor's own summit: the route search
     *       climbs to {@code terminus.floorY} and stops, so a marker higher than that could not be on it.</li>
     * </ul>
     *
     * <p>The distance and floor bands are asserted separately in
     * {@link #assertCompleteChamberMatchesThePlan}, and between them and this method the marker is pinned to
     * the planner's own corridor, at the mouth's own level, a short walk away.
     */
    private static void assertExitIsTheAuthoredPortal(HiddenChamberScan.Completed chamber,
                                                      HiddenChamberPlan.Plan plan, String label) {
        HiddenChamberScan.Position exit = chamber.exitOpening();
        boolean authoredColumn = false;
        boolean authoredStationFloor = false;
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (cell.role() != HiddenChamberPlan.CellRole.CORRIDOR_CLEAR
                    && cell.role() != HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR) {
                continue;
            }
            if (cell.x() == exit.x() && cell.z() == exit.z()) {
                authoredColumn = true;
                authoredStationFloor |= cell.y() == exit.y() + 1;
            }
        }
        assertTrue(authoredColumn,
                label + ": the reconstructed opening " + exit + " does not stand in a column this plan bored "
                        + "at all (terminus " + plan.exitTerminus() + ") -- the scan found SOME way out, not "
                        + "the one the planner built");
        assertTrue(authoredStationFloor,
                label + ": the reconstructed opening " + exit + " stands in an authored bore column but not "
                        + "on one of its stations -- nothing was bored at y=" + (exit.y() + 1));
        assertTrue(exit.y() <= plan.exitTerminus().y(),
                label + ": the reconstructed opening stands on y=" + exit.y() + ", above the authored "
                        + "terminal station floor y=" + plan.exitTerminus().y() + " -- the corridor climbs "
                        + "TO the terminus and stops, so nothing on it can be higher");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Block-level laws for the encounter's two openings                                                     */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * A victim standing in the upper cave walks onto the false floor and falls to the powder.
     *
     * <p>Read straight off the rendered blocks: the block ABOVE every collapse cell is clear (this is what
     * the old seal law paved with packed ice -- a visible ice patch in a snowy floor that nobody could fall
     * through), the collapse cell is the worldgen-only powder, the shaft below is clear all the way down,
     * and the fall stops on two blocks of cushion powder standing on something firm, exactly
     * {@link HiddenChamberPlan.Plan#drop()} blocks under the mouth.
     */
    private static void assertTheFallLandsOnTheAuthoredCushion(HiddenChamberScan.CellReader world,
                                                               HiddenChamberPlan.Plan plan, String label) {
        for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
            assertEquals(HiddenChamberScan.ScanCell.COLLAPSE_POWDER,
                    world.cell(mouth.x(), mouth.y(), mouth.z()),
                    label + ": the mouth cell " + mouth + " is not the collapse powder");
            assertTrue(world.cell(mouth.x(), mouth.y() + 1, mouth.z()).isAir(),
                    label + ": the mouth at " + mouth + " is CAPPED by "
                            + world.cell(mouth.x(), mouth.y() + 1, mouth.z())
                            + " -- the block over a collapse cell is the cave the victim walks in");
            for (int y = mouth.y() - 1; y > plan.landingY() + 1; y--) {
                assertTrue(world.cell(mouth.x(), y, mouth.z()).isAir(),
                        label + ": the shaft under " + mouth + " is blocked at y=" + y + " by "
                                + world.cell(mouth.x(), y, mouth.z()));
            }
            for (int depth = 0; depth < HiddenChamberPlan.CUSHION_DEPTH; depth++) {
                assertEquals(HiddenChamberScan.ScanCell.POWDER_SNOW,
                        world.cell(mouth.x(), plan.landingY() + depth, mouth.z()),
                        label + ": the cushion under " + mouth + " is thin at y="
                                + (plan.landingY() + depth));
            }
            assertTrue(world.cell(mouth.x(), plan.landingY() - 1, mouth.z()).isFloor(),
                    label + ": the cushion under " + mouth + " rests on nothing");
        }
    }

    /**
     * The authored exit portal stands OPEN in the rendered blocks: every portal cell is clear, the block
     * directly over the top of each portal column is clear too (the BREACH -- one block of air above a
     * three-block bore is precisely what tells a surfaced opening from another yard of tunnel, and it is the
     * block {@code HiddenChamberScan.isOpening} reads to decide), and something roofs it so the exit comes up
     * in a cave and not under open sky.
     *
     * <p>This is the assertion the capped-portal bug fails outright: the old seal law wrote packed ice on the
     * breach block of every portal column, so no authored exit could ever read as an opening.
     *
     * <p>Also asserted here, which nothing guaranteed until now: that the terminal station HAS a floor. The
     * route search is a genuine 3D walk and used to be free to cross back over its own columns lower down, in
     * which case the earlier pass's bore had already carved a later station's floor away -- the defect the
     * sweep above used to pin. It never happened to land on the TERMINAL station in this grid, which is why
     * this assertion is a companion to that law and not the proof of it; but nothing stopped it, and the
     * search now refuses any station that would stand in an earlier station's bore, so the block under the
     * lowest portal cell of every portal column is something a player can stand on: the exit is a floor to
     * walk out on, not a hole to fall back down.
     */
    private static void assertAuthoredExitPortalIsOpen(HiddenChamberScan.CellReader world,
                                                       HiddenChamberPlan.Plan plan, String label) {
        Map<Long, int[]> columns = new HashMap<>();
        Map<Long, int[]> floors = new HashMap<>();
        for (HiddenChamberPlan.Cell cell : plan.writesWithRole(
                HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR)) {
            assertTrue(world.cell(cell.x(), cell.y(), cell.z()).isAir(),
                    label + ": portal cell " + cell + " did not render as clear space");
            columns.merge(columnKey(cell.x(), cell.z()), new int[] {cell.x(), cell.y(), cell.z()},
                    (left, right) -> left[1] >= right[1] ? left : right);
            floors.merge(columnKey(cell.x(), cell.z()), new int[] {cell.x(), cell.y(), cell.z()},
                    (left, right) -> left[1] <= right[1] ? left : right);
        }
        assertTrue(!columns.isEmpty(), label + ": an accepted plan always breaches a second opening");
        for (int[] bottom : floors.values()) {
            assertTrue(world.cell(bottom[0], bottom[1] - 1, bottom[2]).isFloor(),
                    label + ": the terminal station column (" + bottom[0] + "," + bottom[2] + ") is bored "
                            + "clear from y=" + bottom[1] + " but stands on "
                            + world.cell(bottom[0], bottom[1] - 1, bottom[2]) + " -- the corridor crossed back "
                            + "over its own columns and carved this station's floor away");
        }
        for (int[] top : columns.values()) {
            int x = top[0];
            int z = top[2];
            int breachY = top[1] + 1;
            assertTrue(world.cell(x, breachY, z).isAir(),
                    label + ": the exit portal column (" + x + "," + z + ") is CAPPED at y=" + breachY
                            + " by " + world.cell(x, breachY, z) + " -- a sealed bore never reads as a "
                            + "second opening, so the whole chamber reconstructs as an unreachable void");
            boolean roofed = false;
            for (int level = 1; level <= EXIT_ROOF_SCAN_HEIGHT && !roofed; level++) {
                HiddenChamberScan.ScanCell above = world.cell(x, breachY + level, z);
                assertTrue(above != HiddenChamberScan.ScanCell.UNREADABLE,
                        label + ": the roof over the exit is unreadable at y=" + (breachY + level));
                roofed = !above.isAir();
            }
            assertTrue(roofed,
                    label + ": the exit column (" + x + "," + z + ") surfaces into open sky, not a cave");
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Role -> material                                                                                      */
    /* ---------------------------------------------------------------------------------------------------- */

    private static final Map<HiddenChamberPlan.CellRole, HiddenChamberScan.ScanCell> ROLE_CELLS =
            buildRoleCells();

    private static Map<HiddenChamberPlan.CellRole, HiddenChamberScan.ScanCell> buildRoleCells() {
        Map<HiddenChamberPlan.CellRole, HiddenChamberScan.ScanCell> cells =
                new EnumMap<>(HiddenChamberPlan.CellRole.class);
        cells.put(HiddenChamberPlan.CellRole.MOUTH_COLLAPSE, HiddenChamberScan.ScanCell.COLLAPSE_POWDER);
        // The snow layer a lowered mouth cube displaces is cleared, so the cube's own top is the floor.
        cells.put(HiddenChamberPlan.CellRole.MOUTH_CLEAR, HiddenChamberScan.ScanCell.AIR);
        cells.put(HiddenChamberPlan.CellRole.SHAFT_CLEAR, HiddenChamberScan.ScanCell.AIR);
        cells.put(HiddenChamberPlan.CellRole.CHAMBER_CLEAR, HiddenChamberScan.ScanCell.AIR);
        cells.put(HiddenChamberPlan.CellRole.RECESS_CLEAR, HiddenChamberScan.ScanCell.AIR);
        cells.put(HiddenChamberPlan.CellRole.CORRIDOR_CLEAR, HiddenChamberScan.ScanCell.AIR);
        cells.put(HiddenChamberPlan.CellRole.EXIT_PORTAL_CLEAR, HiddenChamberScan.ScanCell.AIR);
        cells.put(HiddenChamberPlan.CellRole.CUSHION_POWDER, HiddenChamberScan.ScanCell.POWDER_SNOW);
        cells.put(HiddenChamberPlan.CellRole.CUSHION_BASE, HiddenChamberScan.ScanCell.ICE_PACKED);
        cells.put(HiddenChamberPlan.CellRole.SHELF_FIRM, HiddenChamberScan.ScanCell.SNOW_FIRM);
        cells.put(HiddenChamberPlan.CellRole.CORRIDOR_FLOOR, HiddenChamberScan.ScanCell.SNOW_FIRM);
        cells.put(HiddenChamberPlan.CellRole.LEDGE_FIRM, HiddenChamberScan.ScanCell.SNOW_FIRM);
        cells.put(HiddenChamberPlan.CellRole.WALL_SEAL, HiddenChamberScan.ScanCell.ICE_PACKED);
        cells.put(HiddenChamberPlan.CellRole.FLOOR_SEAL, HiddenChamberScan.ScanCell.ICE_PACKED);
        cells.put(HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED, HiddenChamberScan.ScanCell.ICE_PACKED);
        cells.put(HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE, HiddenChamberScan.ScanCell.ICE_BLUE);
        cells.put(HiddenChamberPlan.CellRole.ICICLE_ICE, HiddenChamberScan.ScanCell.ICICLE);
        cells.put(HiddenChamberPlan.CellRole.FOSSIL_BONE, HiddenChamberScan.ScanCell.BONE);
        cells.put(HiddenChamberPlan.CellRole.DEBRIS_BONE, HiddenChamberScan.ScanCell.BONE);
        cells.put(HiddenChamberPlan.CellRole.LAKE_WATER, HiddenChamberScan.ScanCell.WATER);
        cells.put(HiddenChamberPlan.CellRole.LAKE_BED, HiddenChamberScan.ScanCell.ICE_PACKED);
        cells.put(HiddenChamberPlan.CellRole.SHORE_ICE, HiddenChamberScan.ScanCell.ICE_PLAIN);
        cells.put(HiddenChamberPlan.CellRole.FLOE_ICE, HiddenChamberScan.ScanCell.ICE_PLAIN);
        // A single snow layer: the shipping reader finds no collision shape and calls it clear space.
        cells.put(HiddenChamberPlan.CellRole.FROST_MOTE, HiddenChamberScan.ScanCell.AIR);
        cells.put(HiddenChamberPlan.CellRole.LANTERN, HiddenChamberScan.ScanCell.LANTERN);
        cells.put(HiddenChamberPlan.CellRole.CACHE_CHEST, HiddenChamberScan.ScanCell.CHEST);
        cells.put(HiddenChamberPlan.CellRole.DEBRIS_WOOD, HiddenChamberScan.ScanCell.WOOD_DEBRIS);
        return cells;
    }

    /**
     * {@link HiddenChamberPlan.CellRole#isIceFamily()} is the one place the pure planner claims to know what
     * a role is MADE of, and it exists so the planner can decline a chunk step 10's magma quench sweep would
     * eat. A role whose rendered material stops being ice -- or a new ice role that never joins the
     * predicate -- silently un-protects that chamber, so the claim is checked against the rendering here.
     *
     * <p>The quench's own {@code isIceFamily} counts packed ice, blue ice, ice and the snow BLOCK, which are
     * exactly the four scan cells named below. Powder snow, the collapse powder, a snow LAYER and the custom
     * icicle are deliberately NOT ice to the quench, and so must not be ice to the planner either.
     */
    @Test
    void theIceFamilyPredicateNamesExactlyTheRolesRenderedAsQuenchableIce() {
        for (HiddenChamberPlan.CellRole role : HiddenChamberPlan.CellRole.values()) {
            HiddenChamberScan.ScanCell material = ROLE_CELLS.get(role);
            assertNotNull(material, "role " + role + " has no rendered material");
            boolean renderedAsIce = material == HiddenChamberScan.ScanCell.ICE_PACKED
                    || material == HiddenChamberScan.ScanCell.ICE_BLUE
                    || material == HiddenChamberScan.ScanCell.ICE_PLAIN
                    || material == HiddenChamberScan.ScanCell.SNOW_FIRM;
            assertEquals(renderedAsIce, role.isIceFamily(),
                    "role " + role + " renders as " + material + " but isIceFamily() says "
                            + role.isIceFamily() + " -- the planner's magma-adjacency law would then protect "
                            + "the wrong cells, and the quench eats whatever it missed");
        }
    }

    @Test
    void theRenderingVocabularyIsTotalOverEveryCellRole() {
        for (HiddenChamberPlan.CellRole role : HiddenChamberPlan.CellRole.values()) {
            assertNotNull(ROLE_CELLS.get(role),
                    "role " + role + " has no rendered material: add it here AND in "
                            + "HiddenGlacialChamberFeature.roleStates(), or this gate silently stops "
                            + "testing whatever that role builds");
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Rendering                                                                                             */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * Lay an accepted plan's writes over the fixture's own terrain and answer as a world would.
     *
     * <p>Natural rock is {@link HiddenChamberScan.ScanCell#OTHER_SOLID} except at the top of a snowy column,
     * which is the snow the mouth is hidden in. Anything the fixture cannot answer for is rock too -- the
     * world does not stop where the fixture's array does, it is simply undug -- and only the world's own
     * build limits read {@link HiddenChamberScan.ScanCell#UNREADABLE}, which is what a fail-closed scanner is
     * entitled to see.
     */
    private static HiddenChamberScan.CellReader render(HiddenChamberTerrainFixtures.Mutable terrain,
                                                       HiddenChamberPlan.Plan plan) {
        Map<Long, HiddenChamberScan.ScanCell> authored = new HashMap<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            HiddenChamberScan.ScanCell material = ROLE_CELLS.get(cell.role());
            assertNotNull(material, "no rendered material for role " + cell.role());
            authored.put(key(cell.x(), cell.y(), cell.z()), material);
        }
        return (x, y, z) -> {
            if (y < WORLD_MIN_Y || y > WORLD_MAX_Y) {
                return HiddenChamberScan.ScanCell.UNREADABLE;
            }
            HiddenChamberScan.ScanCell placed = authored.get(key(x, y, z));
            if (placed != null) {
                return placed;
            }
            HiddenChamberPlan.CellKind kind = terrain.cell(x, y, z);
            if (kind == HiddenChamberPlan.CellKind.AIR) {
                return HiddenChamberScan.ScanCell.AIR;
            }
            HiddenChamberPlan.ColumnInfo column = terrain.column(x, z);
            if (kind == HiddenChamberPlan.CellKind.SOLID_SAFE
                    && column != null && column.snowyTop() && y == column.floorY()) {
                return HiddenChamberScan.ScanCell.SNOW_FIRM;
            }
            return HiddenChamberScan.ScanCell.OTHER_SOLID;
        };
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 42) | ((long) (y + 4096) << 21) | (z + 4096);
    }

    private static long columnKey(int x, int z) {
        return ((long) (x + 4096) << 32) | (z + 4096);
    }
}
