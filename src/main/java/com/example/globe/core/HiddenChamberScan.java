package com.example.globe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Physical reconstruction of a {@code globe:hidden_glacial_chamber} encounter from REAL blocks.
 *
 * <p>{@link HiddenChamberPlan} is the authoring law: given terrain it decides what to write. This class is its
 * mirror image and shares none of its state — given a world that already exists it reads blocks back and asks
 * "is a complete chamber standing here, and if not, exactly which stage is missing?". Nothing here consults a
 * world seed, a planner, or a debug counter: a chamber is complete when its blocks say so and never because a
 * generator claims it wrote one. That is the whole point of the class, and it is why the locator commands and
 * the dev audit can both trust one implementation.
 *
 * <p>Like the planner, this class knows nothing about Minecraft. All terrain access goes through
 * {@link CellReader}, which answers with the small {@link ScanCell} vocabulary; the world layer owns the
 * block-to-{@code ScanCell} mapping and tests supply array-backed fakes. Coordinates are WORLD coordinates
 * throughout (contrast {@link HiddenChamberPlan.Position}, which is owner-chunk-local).
 *
 * <h2>Fail closed</h2>
 * Every positive claim rests on a cell that was actually read. A cell the reader cannot see comes back
 * {@link ScanCell#UNREADABLE}; unreadable cells are treated as walls (they can only make a candidate look
 * smaller, never larger) and they raise a sticky flag. If the candidate then fails, the reported reason is
 * {@link PartialReason#BOUNDARY_UNREADABLE} rather than a geometric verdict this scan was never in a position
 * to reach. A candidate that still completes did so on evidence that was read, cell by cell.
 *
 * <h2>Thresholds</h2>
 * Every band is derived from a named {@link HiddenChamberPlan} constant wherever one exists, so a designer
 * retune of the encounter moves the scanner with it instead of leaving a silently stale copy behind. The
 * scanner-only additions (the drop tolerance either side of the authored band, the void box, the minimum void
 * volume) are named constants here.
 */
public final class HiddenChamberScan {

    /* ---------------------------------------------------------------------------------------------------- */
    /* Vocabulary                                                                                            */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * What one real block means to this scan. The world layer maps blocks onto these:
     * {@link #COLLAPSE_POWDER} is exactly {@code globe:cave_trap_powder_snow} (the worldgen-only false floor,
     * never vanilla powder), {@link #POWDER_SNOW} is vanilla powder snow (the landing cushion),
     * {@link #SNOW_FIRM} is a snow block, {@link #ICICLE} is {@code globe:icicle}, and everything the reader
     * cannot see is {@link #UNREADABLE}.
     */
    public enum ScanCell {
        COLLAPSE_POWDER,
        POWDER_SNOW,
        SNOW_FIRM,
        ICE_PACKED,
        ICE_BLUE,
        ICE_PLAIN,
        ICICLE,
        BONE,
        WATER,
        LANTERN,
        CHEST,
        WOOD_DEBRIS,
        AIR,
        OTHER_SOLID,
        OTHER_FLUID,
        UNREADABLE;

        /** Empty walkable space. Water is deliberately excluded: a corridor is dry. */
        public boolean isAir() {
            return this == AIR;
        }

        /** Counted as chamber void by the flood fill: open air, plus the water of a frigid-lake floor. */
        public boolean isVoid() {
            return this == AIR || this == WATER;
        }

        /** Can a player stand on it? Powder (of either kind) and dressing props deliberately cannot. */
        public boolean isFloor() {
            return this == SNOW_FIRM || this == ICE_PACKED || this == ICE_BLUE || this == ICE_PLAIN
                    || this == BONE || this == OTHER_SOLID;
        }

        /** The two firm materials the escape shelf is ever built from. */
        public boolean isShelf() {
            return this == SNOW_FIRM || this == ICE_PACKED;
        }
    }

    /**
     * The one and only terrain access this scan has. Coordinates are WORLD coordinates. An implementation that
     * cannot read a coordinate — outside its region, an unloaded chunk, a closed world — must answer
     * {@link ScanCell#UNREADABLE}; it must never guess.
     */
    public interface CellReader {
        ScanCell cell(int worldX, int y, int worldZ);
    }

    /** An inclusive world-coordinate box; the sweep window of {@link #scan(CellReader, Bounds)}. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("bounds must be ordered min <= max");
            }
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /** One world position. Ordered by x, then y, then z, so every report list has one stable order. */
    public record Position(int x, int y, int z) implements Comparable<Position> {
        @Override
        public int compareTo(Position other) {
            int byX = Integer.compare(x, other.x);
            if (byX != 0) {
                return byX;
            }
            int byY = Integer.compare(y, other.y);
            return byY != 0 ? byY : Integer.compare(z, other.z);
        }
    }

    /**
     * The dressing language the standing blocks speak. The three named values mirror
     * {@link HiddenChamberPlan.Theme}; {@link #UNKNOWN} is the honest answer when the dressing is ambiguous or
     * has been mined out. A theme guess is never a validity test: a chamber whose geometry passed is complete
     * even when its dressing reads {@code UNKNOWN}, which is why the raw {@link Tallies} ride along.
     */
    public enum Theme {
        ICE_CATHEDRAL,
        FRIGID_LAKE,
        LOST_EXPEDITION,
        UNKNOWN
    }

    /** The deepest stage a candidate reached before it stopped being provable. */
    public enum PartialReason {
        MOUTH_SIZE,
        NO_SHAFT,
        NO_CUSHION,
        NO_SHELF,
        NO_CHAMBER_VOID,
        NO_EXIT,
        BOUNDARY_UNREADABLE
    }

    /** Exactly one of these comes back per collapse patch. */
    public sealed interface PatchOutcome permits Completed, Partial, Legacy {
    }

    /** The dressing census taken inside the chamber void; classification input, and evidence for callers. */
    public record Tallies(int icicles, int interiorIce, int water, boolean waterRectangle,
                          int chests, int lanterns, int bones, int woodDebris) {
    }

    /** A chamber whose mouth, shaft, cushion, shelf, void and second opening were all read back from blocks. */
    public record Completed(Position mouthCentroid,
                            List<Position> mouthCells,
                            Position landing,
                            Position exitOpening,
                            Theme themeGuess,
                            int drop,
                            int voidVolume,
                            int bends,
                            Tallies tallies) implements PatchOutcome {
        public Completed {
            mouthCells = List.copyOf(mouthCells);
            Objects.requireNonNull(mouthCentroid, "mouthCentroid");
            Objects.requireNonNull(landing, "landing");
            Objects.requireNonNull(exitOpening, "exitOpening");
            Objects.requireNonNull(themeGuess, "themeGuess");
            Objects.requireNonNull(tallies, "tallies");
        }
    }

    /** A collapse patch of chamber size whose reconstruction stopped at {@code reason}. */
    public record Partial(Position mouthCentroid, PartialReason reason) implements PatchOutcome {
        public Partial {
            Objects.requireNonNull(mouthCentroid, "mouthCentroid");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * A patch too big to be a chamber mouth: the older inner-cave drop trap laid exactly six collapse cells, so
     * any patch of {@link #LEGACY_MIN_CELLS} or more is that encounter and is reported on its own line. It is
     * never a chamber and never a failed chamber.
     */
    public record Legacy(Position centroid, int patchSize) implements PatchOutcome {
        public Legacy {
            Objects.requireNonNull(centroid, "centroid");
        }
    }

    /** Everything one sweep found, each list in {@link Position} order. */
    public record ChamberScanReport(List<Completed> completed,
                                    List<Partial> partial,
                                    List<Legacy> legacy,
                                    int collapseCells,
                                    int patches) {
        public ChamberScanReport {
            completed = List.copyOf(completed);
            partial = List.copyOf(partial);
            legacy = List.copyOf(legacy);
        }

        public int completedCount() {
            return completed.size();
        }

        public int partialCount() {
            return partial.size();
        }

        public int legacyCount() {
            return legacy.size();
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Thresholds                                                                                            */
    /* ---------------------------------------------------------------------------------------------------- */

    /** The old inner-cave drop trap laid six collapse cells; a mouth never exceeds four. */
    public static final int LEGACY_MIN_CELLS = HiddenChamberPlan.MOUTH_MAX_CELLS + 1;

    /**
     * Slack under {@link HiddenChamberPlan#DROP_MIN} and over {@link HiddenChamberPlan#DROP_MAX} when the drop
     * is measured back off real blocks. One block low covers a mouth column that has been walked over and
     * re-frozen; two blocks high covers the authored floor spread ({@link
     * HiddenChamberPlan#MOUTH_MAX_FLOOR_SPREAD}) plus one block of settled cushion.
     */
    public static final int DROP_TOLERANCE_LOW = 1;
    public static final int DROP_TOLERANCE_HIGH = 2;
    public static final int MEASURED_DROP_MIN = HiddenChamberPlan.DROP_MIN - DROP_TOLERANCE_LOW;
    public static final int MEASURED_DROP_MAX = HiddenChamberPlan.DROP_MAX + DROP_TOLERANCE_HIGH;

    /** How far below a mouth cell the shaft descent gives up; bounds the probe count on a broken candidate. */
    private static final int MAX_SHAFT_DESCENT = MEASURED_DROP_MAX + 4;
    /** How deep the powder cushion is counted before the base check; the authored depth plus settling slack. */
    private static final int MAX_CUSHION_DEPTH = HiddenChamberPlan.CUSHION_DEPTH + 2;
    /** How far from the mouth footprint the escape shelf is looked for. */
    private static final int SHELF_SEARCH_RADIUS = 6;

    /**
     * The chamber-void analysis box, 36 x 20 x 26 blocks (X, Y, Z), anchored on the landing. It is the scan's
     * horizon, not a claim about the chamber: cells outside it bound the flood fill exactly as rock would.
     *
     * <p>Deliberately much smaller than {@link #ROUTE_HALF_SPAN}'s box, and correctly so: this box measures
     * the ROOM, and every test it feeds is a minimum ({@link #MIN_VOID_VOLUME}, {@link #MIN_VOID_SPAN},
     * {@link HiddenChamberPlan#CHAMBER_MIN_DISTINCT_HEIGHTS}), so clipping the far end of the very widest
     * cathedral can only under-report {@code voidVolume}, never manufacture a pass. It bounds no corridor:
     * {@link #findExit} carries its own box and only takes its SEEDS from the void this one holds, so the
     * two meet at the chamber and the corridor is walked in the wider one.
     */
    public static final int VOID_BOX_X = 36;
    public static final int VOID_BOX_Y = 20;
    public static final int VOID_BOX_Z = 26;
    /** The lake floor may be dug below the landing ({@link HiddenChamberPlan#LAKE_MAX_DIG}) plus its bed. */
    private static final int VOID_BOX_BELOW_LANDING = HiddenChamberPlan.LAKE_MAX_DIG + 1;

    /**
     * Minimum void bounding span. Taken on the LONGER horizontal axis on purpose: the narrow axis of a compact
     * chamber is allowed down to {@link HiddenChamberPlan#COMPACT_SPAN_Z_MIN} (seven), so a both-axes test
     * would reject the smallest legal silhouette.
     */
    public static final int MIN_VOID_SPAN = 8;
    /** A carved room, not a pocket: below this many void cells the fill is some other cave feature. */
    public static final int MIN_VOID_VOLUME = 120;
    /** Bounds the fill on pathological terrain; the box already caps it far below this. */
    private static final int MAX_VOID_CELLS = VOID_BOX_X * VOID_BOX_Y * VOID_BOX_Z;

    /** Two icicles for each of the minimum authored clusters. */
    public static final int THEME_MIN_ICICLES = 2 * HiddenChamberPlan.CATHEDRAL_MIN_ICICLE_CLUSTERS;
    /** The minimum authored ice columns, counted as COLUMNS -- see {@link #tally} for why not as blocks. */
    public static final int THEME_MIN_INTERIOR_ICE = HiddenChamberPlan.CATHEDRAL_MIN_ICE_COLUMNS;
    /** The authored open centre is a {@link HiddenChamberPlan#LAKE_OPEN_CENTRE_SPAN} square of dark water. */
    public static final int THEME_MIN_WATER =
            HiddenChamberPlan.LAKE_OPEN_CENTRE_SPAN * HiddenChamberPlan.LAKE_OPEN_CENTRE_SPAN;
    /** A free-standing ice column has void air on at least this many of its four horizontal sides. */
    private static final int INTERIOR_ICE_MIN_OPEN_SIDES = 3;
    /**
     * ...and it is at least this TALL, which is what tells a pillar from a patch of floor. Derived, not
     * picked: the thickest slab of ice the encounter ever lays under the room is the lake bed at its deepest
     * dig ({@link HiddenChamberPlan#LAKE_MAX_DIG}), and a floor seal is a single block, so one more than the
     * deepest bed cannot be either of them.
     */
    private static final int INTERIOR_ICE_MIN_COLUMN_HEIGHT = HiddenChamberPlan.LAKE_MAX_DIG + 1;

    /**
     * The authored envelope's own reach. {@link HiddenChamberPlan} builds inside the owner chunk PLUS exactly
     * one cardinal neighbour — a two-chunk strip, canonical {@code x 0..31} by {@code z 0..15} (see that
     * class's "Envelope" section) — and {@link HiddenChamberPlan.EnvelopeDirection} rotates that strip onto
     * any cardinal, so in world coordinates the long axis may be x or z and a containing box must be square on
     * both. The planner's own {@code WRITE_MIN_X}/{@code WRITE_MAX_X} bounds are private, so the span is named
     * here rather than imported: {@code 2 * 16} is the two-chunk span the envelope is defined in terms of.
     */
    private static final int ENVELOPE_TWO_CHUNK_SPAN = 2 * 16;
    /**
     * One block for the two-wide station partner {@link #station} probes beside every node, and one for the
     * block a walk needs to turn around in at the far wall.
     */
    private static final int ROUTE_SPAN_MARGIN = 2;
    /**
     * Horizontal half-span of the route search, measured from the mouth centroid: 32 (two-chunk span) + 2
     * margin.
     *
     * <p>It was once {@code EXIT_DISTANCE_MAX + 2} — eighteen — on the reasoning that the route only has to
     * reach as far as its own goal band. That is wrong, and it was the single cause of every rendered plan
     * reconstructing as {@link PartialReason#NO_EXIT}: the OPENING lands within
     * {@link HiddenChamberPlan#EXIT_DISTANCE_MAX} of the mouth, but the corridor that reaches it winds
     * anywhere in the authored envelope, so a mouth near the owner-chunk edge can have corridor cells
     * twenty-six blocks away. The walk hit the box wall mid-climb and reported no exit for a corridor that was
     * standing there in the blocks. The box must therefore contain the ENVELOPE, not the goal band; the two
     * are unrelated. Nothing about the opening law moves with it — {@link #isOpening} still holds the goal to
     * the authored distance, floor and roof bands, so a wider box can only find corridors that were always
     * legal, never accept an opening that was not.
     */
    private static final int ROUTE_HALF_SPAN = ENVELOPE_TWO_CHUNK_SPAN + ROUTE_SPAN_MARGIN;
    /**
     * Bounds the walk on a cave system that opens into half a continent. Derived, not guessed, so that the cap
     * can never be the thing that clips a LEGAL route the way {@link #ROUTE_HALF_SPAN} once was: the widened
     * box holds {@code (2 * ROUTE_HALF_SPAN + 1)^2} columns, and {@link #findExit}'s Y range spans at most
     * {@link #MEASURED_DROP_MAX} + {@link HiddenChamberPlan#EXIT_FLOOR_TOLERANCE} +
     * {@link #VOID_BOX_BELOW_LANDING} + 1 levels, so no walk holds more DISTINCT nodes than their product.
     * (The planner's own route budget is the same order: {@code HiddenChamberPlan.MAX_ROUTE_NODES} is
     * 120,000. Real walks are nowhere near either number — an exhaustive walk over a whole flat cave floor
     * inside this box settles around five thousand — because a column carries one station, not thirty-two.)
     */
    private static final int MAX_ROUTE_NODES =
            (2 * ROUTE_HALF_SPAN + 1) * (2 * ROUTE_HALF_SPAN + 1)
                    * (MEASURED_DROP_MAX + HiddenChamberPlan.EXIT_FLOOR_TOLERANCE
                            + VOID_BOX_BELOW_LANDING + 1);
    /**
     * How many openings the exit search may re-derive a route for before it stops trying (see
     * {@link #reroute}). One encounter offers a handful of goals — the {@link HiddenChamberPlan#CORRIDOR_WIDTH}
     * columns of its own terminus, plus whatever neighbouring cave floor the breach exposes — so eight
     * re-derivations is more than a legal chamber can present and the cap only ever bites on a cave that opens
     * into a maze. It bounds the COUNT; {@link #MAX_ROUTE_NODES} bounds the work, and the first walk and every
     * re-derivation spend that one allowance between them.
     */
    private static final int MAX_ROUTE_REROUTES = 8;
    /**
     * A second opening stands under a roof, exactly as the authored terminus does (the world layer's own roof
     * probe is {@code HiddenGlacialChamberFeature.ROOF_SCAN_HEIGHT}); a hole to the open sky is not one.
     */
    private static final int EXIT_ROOF_SCAN_HEIGHT = 40;

    /**
     * How wide a patch of standing space proves the bore has ENDED. The authored corridor is exactly
     * {@link HiddenChamberPlan#CORRIDOR_WIDTH} columns wide, so a square of {@code CORRIDOR_WIDTH + 1}
     * columns of clear standing space cannot be corridor — not on a straight run, and not at a bend either,
     * where two two-wide strips meet in an L whose outer corner column is still rock. Three is therefore the
     * narrowest square that is proof rather than coincidence, and it is derived from the corridor's own width
     * so a designer who widens the corridor moves this with it.
     */
    private static final int CAVE_OPENNESS_SPAN = HiddenChamberPlan.CORRIDOR_WIDTH + 1;
    /**
     * How tall that patch must be clear: exactly the headroom the planner's terminus law demands of the cave
     * it breaches into ({@link HiddenChamberPlan#MOUTH_MIN_HEADROOM}). Asking for more would re-introduce the
     * very bug this rule exists to fix, because the planner never promised more.
     */
    private static final int CAVE_OPENNESS_LEVELS = HiddenChamberPlan.MOUTH_MIN_HEADROOM;
    /**
     * Horizontal half-span of the upper-cave flood, around the mouth centroid. Both ends of the relation it
     * measures — the mouth and any station that could ever be an opening — lie within
     * {@link HiddenChamberPlan#EXIT_DISTANCE_MAX} of that centroid, and {@link #ROUTE_SPAN_MARGIN} covers the
     * one block of slack a flood needs to round a pillar standing on the band's own edge.
     */
    private static final int CAVE_FLOOD_HALF_SPAN =
            HiddenChamberPlan.EXIT_DISTANCE_MAX + ROUTE_SPAN_MARGIN;
    /**
     * How far under the mouth floor the upper-cave flood may sink: exactly the exit law's own floor tolerance.
     * This is the rule that keeps the flood OUT of the encounter — it cannot follow the shaft down to the
     * chamber, because the chamber is deeper than any legal opening — and it needs no separate shaft ban to
     * do it. (The chamber void is refused outright as well; see {@link #floodMouthCaveAir}.)
     */
    private static final int CAVE_FLOOD_BELOW_MOUTH = HiddenChamberPlan.EXIT_FLOOR_TOLERANCE;
    /**
     * How far over the mouth floor it may climb: a station may stand {@link
     * HiddenChamberPlan#EXIT_FLOOR_TOLERANCE} above the mouth floor and its own head room reaches
     * {@link HiddenChamberPlan#CORRIDOR_CLEAR_HEIGHT} higher again, so this is the highest cell the relation
     * is ever asked about.
     */
    private static final int CAVE_FLOOD_ABOVE_MOUTH =
            HiddenChamberPlan.EXIT_FLOOR_TOLERANCE + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT;
    /**
     * Node budget for that flood. Derived, not guessed: it is the exact cell count of the box above, so the
     * cap can never be the thing that clips a LEGAL connection the way {@link #ROUTE_HALF_SPAN} once was. One
     * flood serves a whole {@link #findExit} (it is seeded at the MOUTH, and every station asks the same
     * finished set whether it stands in it), so this is spent once per candidate rather than once per station.
     */
    private static final int MAX_CAVE_FLOOD_CELLS =
            (2 * CAVE_FLOOD_HALF_SPAN + 1) * (2 * CAVE_FLOOD_HALF_SPAN + 1)
                    * (CAVE_FLOOD_BELOW_MOUTH + CAVE_FLOOD_ABOVE_MOUTH + 1);

    /**
     * How the upper-cave flood moves, as {@code {dx, dy, dz}} in one fixed order.
     *
     * <p>The four cardinals with a block of climb or fall, which is the exit walk's own diagonal-free step —
     * PLUS the two straight-up and straight-down moves, which the walk does not have and this flood must.
     * A walking player never moves without going somewhere horizontally; AIR does. Leaving the vertical pair
     * out silently made the flood unable to descend a one-column chimney at all: it could reach the top cell
     * of the shaft by stepping in sideways-and-down, and then had nowhere to go, because every remaining move
     * needed a second air column beside it that a one-wide breach does not have. Measured on the section-8
     * straight-route fixture, whose only opening is breached straight up through the cave floor: the chamber
     * reconstructed {@link PartialReason#NO_EXIT} with the flood stopped one block into the hole.
     */
    private static final int[][] CAVE_FLOOD_STEPS = {
        {0, 1, 0}, {0, -1, 0},
        {1, 0, 0}, {1, 1, 0}, {1, -1, 0},
        {-1, 0, 0}, {-1, 1, 0}, {-1, -1, 0},
        {0, 0, 1}, {0, 1, 1}, {0, -1, 1},
        {0, 0, -1}, {0, 1, -1}, {0, -1, -1}
    };

    private static final int[][] CARDINALS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[][] NEIGHBOURHOOD_8 = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    private HiddenChamberScan() {
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Public entry points                                                                                   */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * Sweep a box for collapse mouths and reconstruct each one.
     *
     * <p>This walks every cell in {@code bounds} to find the collapse signature, which is the honest thing to
     * do for a dev audit but wasteful inside a running server: the locator commands run their own section
     * palette prefilter first and then call {@link #classifyPatch(CellReader, List)} per patch, which is the
     * identical reconstruction on identical inputs.
     */
    public static ChamberScanReport scan(CellReader reader, Bounds bounds) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(bounds, "bounds");
        List<Position> collapse = new ArrayList<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (reader.cell(x, y, z) == ScanCell.COLLAPSE_POWDER) {
                        collapse.add(new Position(x, y, z));
                    }
                }
            }
        }
        return classifyPatches(reader, groupCollapsePatches(collapse), collapse.size());
    }

    /** Reconstruct every already-grouped patch and assemble one deterministic report. */
    public static ChamberScanReport classifyPatches(CellReader reader,
                                                    List<List<Position>> patches,
                                                    int collapseCells) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(patches, "patches");
        List<Completed> completed = new ArrayList<>();
        List<Partial> partial = new ArrayList<>();
        List<Legacy> legacy = new ArrayList<>();
        for (List<Position> patch : patches) {
            PatchOutcome outcome = classifyPatch(reader, patch);
            if (outcome instanceof Completed hit) {
                completed.add(hit);
            } else if (outcome instanceof Partial miss) {
                partial.add(miss);
            } else if (outcome instanceof Legacy old) {
                legacy.add(old);
            }
        }
        completed.sort(Comparator.comparing(Completed::mouthCentroid));
        partial.sort(Comparator.comparing(Partial::mouthCentroid)
                .thenComparing(miss -> miss.reason().ordinal()));
        legacy.sort(Comparator.comparing(Legacy::centroid));
        return new ChamberScanReport(completed, partial, legacy, collapseCells, patches.size());
    }

    /**
     * Group loose collapse cells into one patch per mouth: eight-connected in the horizontal plane with the
     * authored one-block floor spread ({@link HiddenChamberPlan#MOUTH_MAX_FLOOR_SPREAD}) tolerated in Y, so a
     * mouth laid across a slightly uneven cave floor still reads as one hole. Output order and each patch's
     * internal order are deterministic.
     */
    public static List<List<Position>> groupCollapsePatches(Collection<Position> cells) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        Set<Position> remaining = new HashSet<>(cells);
        List<Position> seeds = new ArrayList<>(remaining);
        seeds.sort(Comparator.naturalOrder());
        List<List<Position>> patches = new ArrayList<>();
        for (Position seed : seeds) {
            if (!remaining.remove(seed)) {
                continue;
            }
            List<Position> patch = new ArrayList<>();
            ArrayDeque<Position> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                Position cell = queue.removeFirst();
                patch.add(cell);
                for (int[] step : NEIGHBOURHOOD_8) {
                    for (int dy = -HiddenChamberPlan.MOUTH_MAX_FLOOR_SPREAD;
                            dy <= HiddenChamberPlan.MOUTH_MAX_FLOOR_SPREAD; dy++) {
                        Position neighbour =
                                new Position(cell.x() + step[0], cell.y() + dy, cell.z() + step[1]);
                        if (remaining.remove(neighbour)) {
                            queue.addLast(neighbour);
                        }
                    }
                }
            }
            patch.sort(Comparator.naturalOrder());
            patches.add(List.copyOf(patch));
        }
        patches.sort(Comparator.comparing(patch -> patch.get(0)));
        return List.copyOf(patches);
    }

    /**
     * Reconstruct one collapse patch. A patch of {@link HiddenChamberPlan#MOUTH_MIN_CELLS} to
     * {@link HiddenChamberPlan#MOUTH_MAX_CELLS} cells is a chamber candidate and is followed all the way down;
     * a bigger patch is the legacy drop trap; a single cell is a mouth that cannot be judged.
     */
    public static PatchOutcome classifyPatch(CellReader reader, List<Position> patchCells) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(patchCells, "patchCells");
        List<Position> mouth = new ArrayList<>(new TreeSet<>(patchCells));
        if (mouth.isEmpty()) {
            throw new IllegalArgumentException("a collapse patch holds at least one cell");
        }
        Position centroid = mouthCentroid(mouth);
        if (mouth.size() >= LEGACY_MIN_CELLS) {
            return new Legacy(centroid, mouth.size());
        }
        if (mouth.size() < HiddenChamberPlan.MOUTH_MIN_CELLS) {
            return new Partial(centroid, PartialReason.MOUTH_SIZE);
        }
        return reconstruct(new Probe(reader), mouth, centroid);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Reconstruction                                                                                        */
    /* ---------------------------------------------------------------------------------------------------- */

    private static PatchOutcome reconstruct(Probe probe, List<Position> mouth, Position centroid) {
        int mouthFloorY = centroid.y();

        /* 1. Shaft: a clear fall under EVERY mouth cell, all landing on one cushion. */
        int cushionTopY = Integer.MIN_VALUE;
        for (Position cell : mouth) {
            int y = cell.y() - 1;
            int floor = Integer.MIN_VALUE;
            for (int steps = 0; steps < MAX_SHAFT_DESCENT; steps++, y--) {
                if (!probe.at(cell.x(), y, cell.z()).isAir()) {
                    floor = y;
                    break;
                }
            }
            if (floor == Integer.MIN_VALUE) {
                return fail(probe, centroid, PartialReason.NO_SHAFT);
            }
            if (cushionTopY == Integer.MIN_VALUE) {
                cushionTopY = floor;
            } else if (Math.abs(floor - cushionTopY) > HiddenChamberPlan.MOUTH_MAX_FLOOR_SPREAD) {
                return fail(probe, centroid, PartialReason.NO_CUSHION);
            } else {
                cushionTopY = Math.max(cushionTopY, floor);
            }
        }
        // The cushion occupies landingY and landingY + 1 (HiddenChamberPlan.CUSHION_DEPTH), so the first block
        // a falling player touches is landingY + 1 and the authored drop is mouth floor minus landing.
        int landingY = cushionTopY - (HiddenChamberPlan.CUSHION_DEPTH - 1);
        int drop = mouthFloorY - landingY;
        for (Position cell : mouth) {
            int columnDrop = cell.y() - landingY;
            if (columnDrop < MEASURED_DROP_MIN || columnDrop > MEASURED_DROP_MAX) {
                return fail(probe, centroid, PartialReason.NO_SHAFT);
            }
        }

        /* 2. Cushion: deep powder under the whole shaft footprint, resting on something solid. */
        for (Position cell : mouth) {
            int powder = 0;
            int y = cushionTopY;
            while (powder < MAX_CUSHION_DEPTH && probe.at(cell.x(), y, cell.z()) == ScanCell.POWDER_SNOW) {
                powder++;
                y--;
            }
            if (powder < HiddenChamberPlan.CUSHION_DEPTH || !probe.at(cell.x(), y, cell.z()).isFloor()) {
                return fail(probe, centroid, PartialReason.NO_CUSHION);
            }
        }
        Position landing = new Position(centroid.x(), landingY, centroid.z());

        /* 3. Shelf: firm square footing beside the powder, with room to stand up on it. */
        if (!hasShelf(probe, mouth, landingY)) {
            return fail(probe, centroid, PartialReason.NO_SHELF);
        }

        /* 4. Chamber void: one carved room, not a pocket and not a corridor. */
        ChamberVoid chamber = floodVoid(probe, mouth, landingY, mouthFloorY);
        if (chamber == null) {
            return fail(probe, centroid, PartialReason.NO_CHAMBER_VOID);
        }

        /* 5. Second opening: a walkable, winding route out that surfaces a short walk from the mouth. */
        Route route = findExit(probe, mouth, centroid, chamber, landingY, mouthFloorY);
        if (route == null) {
            return fail(probe, centroid, PartialReason.NO_EXIT);
        }

        /* 6. Dressing: classification only — a complete chamber stays complete when its theme is unknown. */
        Tallies tallies = tally(probe, chamber);
        return new Completed(centroid, mouth, landing, route.opening(), classify(tallies),
                drop, chamber.cells().size(), route.bends(), tallies);
    }

    /** Fail closed: an unreadable cell anywhere in this reconstruction outranks every geometric verdict. */
    private static Partial fail(Probe probe, Position centroid, PartialReason reason) {
        return new Partial(centroid, probe.sawUnreadable() ? PartialReason.BOUNDARY_UNREADABLE : reason);
    }

    private static Position mouthCentroid(List<Position> mouth) {
        int sumX = 0;
        int sumZ = 0;
        int floorY = Integer.MAX_VALUE;
        for (Position cell : mouth) {
            sumX += cell.x();
            sumZ += cell.z();
            floorY = Math.min(floorY, cell.y());
        }
        // roundedMean and the lowest mouth floor are exactly HiddenChamberPlan's own centroid law, so a
        // reconstructed centroid can be compared with a planned one without a conversion step.
        return new Position(roundedMean(sumX, mouth.size()), floorY, roundedMean(sumZ, mouth.size()));
    }

    private static int roundedMean(int sum, int count) {
        return Math.floorDiv(2 * sum + count, 2 * count);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Shelf                                                                                                 */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * The authored shelf is firm material at {@code landingY - 1} — its top surface level with the powder, so a
     * player climbs out rather than up — in a square of {@link HiddenChamberPlan#SHELF_SPAN}, with
     * {@link HiddenChamberPlan#SHELF_MIN_HEADROOM} clear above it, beside the cushion.
     */
    private static boolean hasShelf(Probe probe, List<Position> mouth, int landingY) {
        int shelfY = landingY - 1;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Position cell : mouth) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
        }
        for (int x = minX - SHELF_SEARCH_RADIUS; x <= maxX + SHELF_SEARCH_RADIUS; x++) {
            for (int z = minZ - SHELF_SEARCH_RADIUS; z <= maxZ + SHELF_SEARCH_RADIUS; z++) {
                if (!shelfSquareAt(probe, x, z, shelfY)) {
                    continue;
                }
                if (touchesCushion(mouth, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shelfSquareAt(Probe probe, int originX, int originZ, int shelfY) {
        for (int dx = 0; dx < HiddenChamberPlan.SHELF_SPAN; dx++) {
            for (int dz = 0; dz < HiddenChamberPlan.SHELF_SPAN; dz++) {
                int x = originX + dx;
                int z = originZ + dz;
                if (!probe.at(x, shelfY, z).isShelf()) {
                    return false;
                }
                for (int level = 1; level <= HiddenChamberPlan.SHELF_MIN_HEADROOM; level++) {
                    if (!probe.at(x, shelfY + level, z).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The shelf must border the powder it rescues you from: the cushion is the mouth footprint, dilated. */
    private static boolean touchesCushion(List<Position> mouth, int originX, int originZ) {
        int reach = HiddenChamberPlan.CUSHION_DILATION + 1;
        for (Position cell : mouth) {
            for (int dx = 0; dx < HiddenChamberPlan.SHELF_SPAN; dx++) {
                for (int dz = 0; dz < HiddenChamberPlan.SHELF_SPAN; dz++) {
                    if (Math.max(Math.abs(originX + dx - cell.x()), Math.abs(originZ + dz - cell.z())) <= reach) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Chamber void                                                                                          */
    /* ---------------------------------------------------------------------------------------------------- */

    private record ChamberVoid(Set<Position> cells, int minX, int maxX, int minZ, int maxZ) {
    }

    /**
     * Flood the open space the fall drops into. The fill is six-connected through air and lake water, bounded
     * by the {@link #VOID_BOX_X} x {@link #VOID_BOX_Y} x {@link #VOID_BOX_Z} box around the landing and capped
     * one block under the mouth floor — the chamber is by definition the room BELOW the false floor, so the
     * cap keeps the upper cave the corridor breaks into out of the chamber's own measurements.
     */
    private static ChamberVoid floodVoid(Probe probe, List<Position> mouth, int landingY, int mouthFloorY) {
        int centreX = roundedMeanX(mouth);
        int centreZ = roundedMeanZ(mouth);
        int minX = centreX - VOID_BOX_X / 2;
        int maxX = minX + VOID_BOX_X - 1;
        int minZ = centreZ - VOID_BOX_Z / 2;
        int maxZ = minZ + VOID_BOX_Z - 1;
        int minY = landingY - VOID_BOX_BELOW_LANDING;
        int maxY = Math.min(minY + VOID_BOX_Y - 1, mouthFloorY - 1);

        Set<Position> filled = new LinkedHashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        for (Position cell : mouth) {
            Position seed = new Position(cell.x(), landingY + HiddenChamberPlan.CUSHION_DEPTH, cell.z());
            if (seed.y() <= maxY && probe.at(seed.x(), seed.y(), seed.z()).isVoid() && filled.add(seed)) {
                queue.add(seed);
            }
        }
        if (queue.isEmpty()) {
            return null;
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty() && filled.size() < MAX_VOID_CELLS) {
            Position cell = queue.removeFirst();
            for (int[] step : steps) {
                int x = cell.x() + step[0];
                int y = cell.y() + step[1];
                int z = cell.z() + step[2];
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                    continue;
                }
                Position next = new Position(x, y, z);
                if (filled.contains(next) || !probe.at(x, y, z).isVoid()) {
                    continue;
                }
                filled.add(next);
                queue.addLast(next);
            }
        }
        if (filled.size() < MIN_VOID_VOLUME) {
            return null;
        }
        int fillMinX = Integer.MAX_VALUE;
        int fillMaxX = Integer.MIN_VALUE;
        int fillMinZ = Integer.MAX_VALUE;
        int fillMaxZ = Integer.MIN_VALUE;
        for (Position cell : filled) {
            fillMinX = Math.min(fillMinX, cell.x());
            fillMaxX = Math.max(fillMaxX, cell.x());
            fillMinZ = Math.min(fillMinZ, cell.z());
            fillMaxZ = Math.max(fillMaxZ, cell.z());
        }
        int span = Math.max(fillMaxX - fillMinX + 1, fillMaxZ - fillMinZ + 1);
        if (span < MIN_VOID_SPAN) {
            return null;
        }
        ChamberVoid chamber = new ChamberVoid(filled, fillMinX, fillMaxX, fillMinZ, fillMaxZ);
        if (distinctClearHeights(probe, chamber, mouth) < HiddenChamberPlan.CHAMBER_MIN_DISTINCT_HEIGHTS) {
            return null; // a lid, not a roof
        }
        return chamber;
    }

    /**
     * How many different floor-to-ceiling clear heights the room carries. The shaft footprint is excluded: its
     * height is the fall, not the roof. Columns whose lowest void cell rests on powder (the cushion) are
     * excluded by the floor test itself, for the same reason.
     */
    private static int distinctClearHeights(Probe probe, ChamberVoid chamber, List<Position> mouth) {
        Map<Long, Integer> lowest = new HashMap<>();
        for (Position cell : chamber.cells()) {
            long key = columnKey(cell.x(), cell.z());
            Integer current = lowest.get(key);
            if (current == null || cell.y() < current) {
                lowest.put(key, cell.y());
            }
        }
        Set<Long> mouthColumns = new HashSet<>();
        for (Position cell : mouth) {
            mouthColumns.add(columnKey(cell.x(), cell.z()));
        }
        Set<Integer> heights = new HashSet<>();
        for (Position cell : chamber.cells()) {
            long key = columnKey(cell.x(), cell.z());
            if (mouthColumns.contains(key) || lowest.get(key) != cell.y()) {
                continue;
            }
            if (!probe.at(cell.x(), cell.y() - 1, cell.z()).isFloor()) {
                continue;
            }
            int height = 0;
            while (chamber.cells().contains(new Position(cell.x(), cell.y() + height, cell.z()))) {
                height++;
            }
            heights.add(height);
        }
        return heights.size();
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int roundedMeanX(List<Position> cells) {
        int sum = 0;
        for (Position cell : cells) {
            sum += cell.x();
        }
        return roundedMean(sum, cells.size());
    }

    private static int roundedMeanZ(List<Position> cells) {
        int sum = 0;
        for (Position cell : cells) {
            sum += cell.z();
        }
        return roundedMean(sum, cells.size());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Exit route                                                                                            */
    /* ---------------------------------------------------------------------------------------------------- */

    private record Route(Position opening, int bends) {
    }

    /**
     * Walk out of the chamber the way a player would. A station is a floor block with
     * {@link HiddenChamberPlan#CORRIDOR_CLEAR_HEIGHT} clear above it and a same-height neighbour beside it, so
     * the route is genuinely two wide; steps are one block up, level, or one block down. Stations within
     * {@link HiddenChamberPlan#SHAFT_CLEARANCE} of a shaft column are not part of the graph at all — the
     * authored corridor never routes back under the trap.
     *
     * <p>The goal is a second opening: a station that stands on the same upper-cave floor as the mouth (within
     * {@link HiddenChamberPlan#EXIT_FLOOR_TOLERANCE}), under a roof, at a horizontal Chebyshev distance of
     * {@link HiddenChamberPlan#EXIT_DISTANCE_MIN} to {@link HiddenChamberPlan#EXIT_DISTANCE_MAX} from the mouth
     * centroid, whose route bends at least {@link HiddenChamberPlan#CORRIDOR_MIN_BENDS} times. Breadth-first
     * order means the first qualifying opening found is the nearest one along the corridor, not some distant
     * patch of cave floor beyond it.
     *
     * <h2>Where the walk starts</h2>
     * EVERY walkable station the chamber void stands on is a seed, at ANY horizontal distance from the mouth.
     * Seeding was once limited to stations closer to the mouth centroid than
     * {@link HiddenChamberPlan#EXIT_DISTANCE_MIN}, so that starts and goals would be disjoint without anyone
     * having to say so. That ring was a scanner convenience and never a product law, and it was wrong: a deep
     * {@code FRIGID_LAKE} whose open water, cushion and shelf crowd the mouth leaves no dry foothold inside
     * eight blocks at all, so the ring held no seed, the walk never entered the authored corridor, and the
     * chamber reconstructed {@link PartialReason#NO_EXIT} with its corridor standing in the blocks. (Read off
     * a real world: seed 987654, chunk (-175,582), a lake chamber written atomically at mouth
     * (-2798,33,9318). Cathedral runs at that same site completed; only the lake layouts failed, because only
     * they push the footing out that far.) Disjointness is now STATED rather than implied — a station that
     * already satisfies {@link #isOpening} is never a seed, so no walk can begin standing on its own goal.
     *
     * <p>One qualification survives the widening, and it is about the BENDS rather than about reachability.
     * The void flood is six-connected, so it runs up the corridor as readily as it fills the room; out beyond
     * the exit ring, a station whose clear column is exactly the corridor bore and no more is a yard of that
     * corridor, not a place the fall leaves anyone standing. Seeding from those would start the route a step
     * short of its own end and the corridor's winding would be measured as the last two blocks of it. So a
     * station beyond the ring seeds the walk only where the space over it is {@link #clearerThanABore} — the
     * room's own headroom. Inside the ring nothing is asked, which keeps every seed the old rule ever had.
     *
     * <h2>Acceptance</h2>
     * A chamber has an exit when ANY route this walk can discover satisfies every law, bends included. An
     * opening whose first route is too straight never condemns the chamber on its own: the walk carries on to
     * later openings, and for each one it re-derives a route from the OTHER seeds first (see {@link #reroute}).
     * That is what makes the widening above monotone. A wider seed set can only make the first route found
     * SHORTER — breadth-first search returns the shortest route from the whole seed set — so without the
     * re-derivation a chamber that completed under the old ring could fail under the new rule for want of a
     * bend, which is the one way a strictly larger set of starts could ever lose a chamber.
     *
     * <p>Containment: horizontally {@link #ROUTE_HALF_SPAN} around the mouth centroid, which holds the whole
     * authored envelope. Vertically the walk runs from the deepest cell the chamber may occupy
     * ({@link #VOID_BOX_BELOW_LANDING} under the landing, two blocks below the chamber floor at
     * {@code landingY - 1}) up to {@code mouthFloorY + }{@link HiddenChamberPlan#EXIT_FLOOR_TOLERANCE}. That
     * upper edge is the exit law itself, not a scanner horizon: an authored corridor climbs from the chamber
     * floor to its terminus, and a terminus is only legal within that tolerance of the mouth floor, so the
     * band already contains every corridor floor the planner can write and raising it would only let the walk
     * wander above every station that could ever be an opening.
     */
    /**
     * Dev-only diagnosis hook (2026-08-03, architect instrumentation): when non-null, {@link #findExit}
     * narrates its seed set, walk extent, and per-opening verdicts as plain lines. Never set on any
     * production path; the headless audit sets it around a single re-classification and clears it in a
     * {@code finally}. Reads are volatile so a stale non-null consumer can never leak between scans.
     */
    public static volatile java.util.function.Consumer<String> EXIT_TRACE = null;

    private static void trace(String line) {
        java.util.function.Consumer<String> sink = EXIT_TRACE;
        if (sink != null) {
            sink.accept(line);
        }
    }

    private static Route findExit(Probe probe, List<Position> mouth, Position centroid, ChamberVoid chamber,
                                  int landingY, int mouthFloorY) {
        int minX = centroid.x() - ROUTE_HALF_SPAN;
        int maxX = centroid.x() + ROUTE_HALF_SPAN;
        int minZ = centroid.z() - ROUTE_HALF_SPAN;
        int maxZ = centroid.z() + ROUTE_HALF_SPAN;
        int minY = landingY - VOID_BOX_BELOW_LANDING;
        int maxY = mouthFloorY + HiddenChamberPlan.EXIT_FLOOR_TOLERANCE;

        Bounds box = new Bounds(minX, minY, minZ, maxX, maxY, maxZ);

        MouthCave mouthCave = new MouthCave(probe, mouth, centroid, mouthFloorY);

        Set<Position> seeds = new TreeSet<>();
        for (Position cell : chamber.cells()) {
            Position station = new Position(cell.x(), cell.y() - 1, cell.z());
            if (station.y() < minY || seeds.contains(station)) {
                continue;
            }
            if (!station(probe, mouth, station.x(), station.y(), station.z())) {
                continue;
            }
            if (horizontalDistance(centroid, station) >= HiddenChamberPlan.EXIT_DISTANCE_MIN
                    && !clearerThanABore(probe, station.x(), station.y(), station.z())) {
                continue; // out here a bare bore is the corridor itself, not somewhere to start
            }
            if (isOpening(probe, centroid, station, mouthFloorY, mouthCave)) {
                continue; // a walk never begins standing on its own goal
            }
            seeds.add(station);
        }
        if (EXIT_TRACE != null) {
            StringBuilder sample = new StringBuilder();
            int shown = 0;
            for (Position seed : seeds) {
                if (shown++ >= 6) {
                    break;
                }
                sample.append(seed.x()).append(',').append(seed.y()).append(',').append(seed.z()).append(' ');
            }
            trace("seeds=" + seeds.size() + " chamberCells=" + chamber.cells().size()
                    + " mouthCaveAir=" + mouthCave.air().size()
                    + " centroid=" + centroid.x() + "," + centroid.y() + "," + centroid.z()
                    + " mouthFloorY=" + mouthFloorY + " landingY=" + landingY
                    + " box=[" + minX + ".." + maxX + " | " + minY + ".." + maxY + " | " + minZ + ".." + maxZ + "]"
                    + " sample=" + sample);
        }
        if (seeds.isEmpty()) {
            trace("verdict=NO_EXIT reason=no-seeds");
            return null;
        }

        Walk walk = new Walk();
        Map<Position, Position> parents = new HashMap<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        for (Position seed : seeds) {
            parents.put(seed, seed);
            queue.add(seed);
        }

        int reroutes = 0;
        while (!queue.isEmpty()) {
            List<Position> layerGoals = new ArrayList<>();
            int layerSize = queue.size();
            for (int index = 0; index < layerSize; index++) {
                Position node = queue.removeFirst();
                if (!walk.spend()) {
                    return null;
                }
                for (int[] step : CARDINALS) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int x = node.x() + step[0];
                        int y = node.y() + dy;
                        int z = node.z() + step[1];
                        if (!box.contains(x, y, z)) {
                            continue;
                        }
                        Position next = new Position(x, y, z);
                        if (parents.containsKey(next) || !station(probe, mouth, x, y, z)) {
                            continue;
                        }
                        parents.put(next, node);
                        queue.addLast(next);
                        if (isOpening(probe, centroid, next, mouthFloorY, mouthCave)) {
                            layerGoals.add(next);
                        }
                    }
                }
            }
            layerGoals.sort(Comparator.naturalOrder());
            for (Position goal : layerGoals) {
                int bends = countBends(chain(parents, goal));
                if (bends >= HiddenChamberPlan.CORRIDOR_MIN_BENDS) {
                    trace("verdict=ROUTE goal=" + goal.x() + "," + goal.y() + "," + goal.z() + " bends=" + bends);
                    return new Route(goal, bends);
                }
                trace("openingCandidate goal=" + goal.x() + "," + goal.y() + "," + goal.z()
                        + " bends=" + bends + " verdict=too-straight");
                if (reroutes++ < MAX_ROUTE_REROUTES) {
                    Route longerWayRound = reroute(probe, mouth, goal, seeds, box, walk);
                    if (longerWayRound != null) {
                        trace("verdict=ROUTE-REROUTED goal=" + longerWayRound.opening().x() + ","
                                + longerWayRound.opening().y() + "," + longerWayRound.opening().z()
                                + " bends=" + longerWayRound.bends());
                        return longerWayRound;
                    }
                    trace("reroute goal=" + goal.x() + "," + goal.y() + "," + goal.z() + " verdict=failed");
                }
            }
        }
        if (EXIT_TRACE != null) {
            int highestSeenY = Integer.MIN_VALUE;
            int minSeenX = Integer.MAX_VALUE;
            int maxSeenX = Integer.MIN_VALUE;
            int minSeenZ = Integer.MAX_VALUE;
            int maxSeenZ = Integer.MIN_VALUE;
            for (Position visited : parents.keySet()) {
                highestSeenY = Math.max(highestSeenY, visited.y());
                minSeenX = Math.min(minSeenX, visited.x());
                maxSeenX = Math.max(maxSeenX, visited.x());
                minSeenZ = Math.min(minSeenZ, visited.z());
                maxSeenZ = Math.max(maxSeenZ, visited.z());
            }
            trace("verdict=NO_EXIT reason=frontier-exhausted visited=" + parents.size()
                    + " maxY=" + highestSeenY + " xSpan=" + minSeenX + ".." + maxSeenX
                    + " zSpan=" + minSeenZ + ".." + maxSeenZ);
        }
        return null;
    }

    /**
     * The same opening, reached from a different seed: the walk's second opinion on an opening whose first
     * route was too straight to be an escape.
     *
     * <p>The forward walk holds ONE route per opening — the shortest from the whole seed set — so an opening
     * standing a step from some seed is judged on those two blocks even when a long winding route to it also
     * exists. This is that other route. It is the same breadth-first walk over the same station graph, run
     * BACKWARDS from the opening, and the first seed it reaches whose route bends at least
     * {@link HiddenChamberPlan#CORRIDOR_MIN_BENDS} times is the answer. Bends are counted the same either way
     * round — reversing a route reverses its turns without adding or losing one — so the number reported here
     * means exactly what the forward walk's does.
     *
     * <p>Deterministic and bounded: layers in breadth-first order, seeds within a layer in {@link Position}
     * order, and every node spent from the caller's own {@link Walk} allowance.
     */
    private static Route reroute(Probe probe, List<Position> mouth, Position goal, Set<Position> seeds,
                                 Bounds box, Walk walk) {
        Map<Position, Position> parents = new HashMap<>();
        parents.put(goal, goal);
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.add(goal);
        while (!queue.isEmpty()) {
            List<Position> layerSeeds = new ArrayList<>();
            int layerSize = queue.size();
            for (int index = 0; index < layerSize; index++) {
                Position node = queue.removeFirst();
                if (!walk.spend()) {
                    return null;
                }
                for (int[] step : CARDINALS) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int x = node.x() + step[0];
                        int y = node.y() + dy;
                        int z = node.z() + step[1];
                        if (!box.contains(x, y, z)) {
                            continue;
                        }
                        Position next = new Position(x, y, z);
                        if (parents.containsKey(next) || !station(probe, mouth, x, y, z)) {
                            continue;
                        }
                        parents.put(next, node);
                        queue.addLast(next);
                        if (seeds.contains(next)) {
                            layerSeeds.add(next);
                        }
                    }
                }
            }
            layerSeeds.sort(Comparator.naturalOrder());
            for (Position seed : layerSeeds) {
                // These parent links point back toward the opening, so the chain from a seed IS the route out.
                int bends = countBends(chain(parents, seed));
                if (bends >= HiddenChamberPlan.CORRIDOR_MIN_BENDS) {
                    return new Route(goal, bends);
                }
            }
        }
        return null;
    }

    /**
     * One node allowance for a chamber's whole exit search. The first walk and every {@link #reroute} spend
     * from it, so a chamber costs at most {@link #MAX_ROUTE_NODES} nodes however many openings it offers.
     */
    private static final class Walk {
        private int spent;

        private boolean spend() {
            return ++spent <= MAX_ROUTE_NODES;
        }
    }

    /** A two-wide walkable station: floor under it, corridor headroom over it, and a partner beside it. */
    private static boolean station(Probe probe, List<Position> mouth, int x, int y, int z) {
        if (!clearStanding(probe, mouth, x, y, z)) {
            return false;
        }
        for (int[] step : CARDINALS) {
            if (clearStanding(probe, mouth, x + step[0], y, z + step[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean clearStanding(Probe probe, List<Position> mouth, int x, int y, int z) {
        for (Position cell : mouth) {
            if (Math.max(Math.abs(x - cell.x()), Math.abs(z - cell.z()))
                    <= HiddenChamberPlan.SHAFT_CLEARANCE) {
                return false;
            }
        }
        if (!probe.at(x, y, z).isFloor()) {
            return false;
        }
        for (int level = 1; level <= HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT; level++) {
            if (!probe.at(x, y + level, z).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Is this station the second opening — a place a player who climbed the corridor stands in the very cave
     * the mouth is hidden in?
     *
     * <p>Four clauses, and each one is a PRODUCT law rather than a scanner's guess at what a corridor looks
     * like from the inside:
     * <ol>
     *   <li>the floor band, within {@link HiddenChamberPlan#EXIT_FLOOR_TOLERANCE} of the mouth floor;</li>
     *   <li>the distance band, {@link HiddenChamberPlan#EXIT_DISTANCE_MIN} to
     *       {@link HiddenChamberPlan#EXIT_DISTANCE_MAX} in horizontal Chebyshev;</li>
     *   <li>{@link #standsInTheMouthsOwnCave} — the station's head air is part of the air the MOUTH stands
     *       in, proven cell by cell by {@link #floodMouthCaveAir}, which never crosses the chamber and cannot
     *       enter the shaft. This is "a second opening in the SAME upper cave", stated rather than
     *       approximated, and it is the whole of the openness test;</li>
     *   <li>a roof over it, so the way out surfaces in a cave and not under open sky.</li>
     * </ol>
     *
     * <h2>Two shape tests were tried here, and both were wrong</h2>
     * Neither was a product law. Each was a guess about what surfacing looks like, and each refused a legal
     * exit that was standing open in the blocks.
     *
     * <p>{@code clearerThanABore} asked for one block of air ABOVE the
     * {@link HiddenChamberPlan#CORRIDOR_CLEAR_HEIGHT} bore — four blocks of clear space in all. The planner
     * never promised four: its terminus law ({@code HiddenChamberPlan.routeExit}) accepts any roofed column
     * carrying {@link HiddenChamberPlan#MOUTH_MIN_HEADROOM} of headroom, so a corridor surfacing legally into
     * a two- or three-block-high cave bores its portal and meets that cave's ceiling directly above it. Read
     * off a real world: seed 987654, the {@code FRIGID_LAKE} chamber at mouth (-2798,33,9318), whose walk
     * climbed the authored corridor to the station floor at y=30 and was offered not one candidate. Every
     * earlier end-to-end fixture happened to breach into a TALL cave, which is why four figures of green
     * tests never saw it.
     *
     * <p>{@code standsInOpenCave} then asked for a {@link #CAVE_OPENNESS_SPAN}-column square of standing
     * space, on the reasoning that a {@link HiddenChamberPlan#CORRIDOR_WIDTH}-wide bore can never make one.
     * True, and still beside the point. The same real chamber breaches into a LOW, NARROW glacial tunnel,
     * and the instrumented re-scan of it reads
     * {@code openingCheck pos=-2790,28,9324 dist=8 tall=false open3x3=false inMouthCave=true} — inside both
     * authored bands, genuinely connected to the mouth's own cave, and refused for not being three columns
     * wide. Glacial caves are not three columns wide. Both shape tests are gone as REQUIREMENTS; only the
     * product law is left, and {@link #standsInOpenCave} survives as a diagnosis field on the trace above so
     * that the next surprise is readable off one line.
     *
     * <h2>What a mid-bore station now means, and why it is not a false positive</h2>
     * A station part-way along the bore can satisfy clause 3: its head air runs up the bore, out of the
     * breach and into the mouth's cave. That is not a mistake, it is a PROOF that the bore breaches ahead of
     * it — the flood cannot reach the mouth any other way, because the chamber void is refused outright and
     * the shaft is sealed at the top by collapse cells, which are not air. So the exit EXISTS, which is the
     * only thing this method is asked. The one consequence is cosmetic: the reported
     * {@link Completed#exitOpening} may name a column short of the authored portal. It is bounded by the two
     * bands either way, so the marker still stands a short walk from the mouth at the mouth's own level. A
     * bore that dead-ends and breaches NOTHING is still refused, because then the only way back to the mouth
     * runs through the chamber.
     *
     * <h2>The roof scan, re-derived</h2>
     * It starts at {@code CORRIDOR_CLEAR_HEIGHT + 1} because {@link #clearStanding} has already proved every
     * level below that is air, and that holds for every station whatever its breach looks like. From there:
     * the first non-air block is a roof, whether it comes on the first probe (a two-block-high section) or
     * thirty blocks up (a cathedral); an {@link ScanCell#UNREADABLE} cell fails closed; and a column still
     * clear at {@link #EXIT_ROOF_SCAN_HEIGHT} is a hole to the sky and no opening at all. The note this
     * javadoc used to carry — that the first probe is non-air "by definition" — was only ever true while a
     * tall column short-circuited ahead of it. With no shape test in front, this is a genuine scan again, and
     * it is now the ONLY clause that can reject a station that surfaces to open sky.
     */
    private static boolean isOpening(Probe probe, Position centroid, Position station, int mouthFloorY,
                                     MouthCave mouthCave) {
        if (Math.abs(station.y() - mouthFloorY) > HiddenChamberPlan.EXIT_FLOOR_TOLERANCE) {
            return false;
        }
        int distance = horizontalDistance(centroid, station);
        if (distance < HiddenChamberPlan.EXIT_DISTANCE_MIN || distance > HiddenChamberPlan.EXIT_DISTANCE_MAX) {
            return false;
        }
        boolean inMouthCave = standsInTheMouthsOwnCave(mouthCave, station);
        if (EXIT_TRACE == null) {
            return inMouthCave && roofedLikeACave(probe, station);
        }
        // Dev diagnosis: every in-band station gets one line, with both retired shape tests alongside the
        // clause that now decides, so a surprise reads off a single row. Same field order as before; the
        // roof verdict is appended rather than replacing anything.
        boolean roofed = roofedLikeACave(probe, station);
        trace("openingCheck pos=" + station.x() + "," + station.y() + "," + station.z()
                + " dist=" + distance
                + " tall=" + clearerThanABore(probe, station.x(), station.y(), station.z())
                + " open3x3=" + standsInOpenCave(probe, station)
                + " inMouthCave=" + inMouthCave
                + " roofed=" + roofed);
        return inMouthCave && roofed;
    }

    /**
     * Does something roof this station within {@link #EXIT_ROOF_SCAN_HEIGHT}? See {@link #isOpening}'s "roof
     * scan, re-derived" note for why the scan starts one block over the bore and what each answer means.
     */
    private static boolean roofedLikeACave(Probe probe, Position station) {
        for (int level = HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + 1; level <= EXIT_ROOF_SCAN_HEIGHT; level++) {
            ScanCell above = probe.at(station.x(), station.y() + level, station.z());
            if (above == ScanCell.UNREADABLE) {
                return false; // fail closed: an unseen cell is never a roof
            }
            if (!above.isAir()) {
                return true; // roofed: this opening is in a cave, not a hole to the sky
            }
        }
        return false; // clear to the scan height: a hole to the sky, not a second opening
    }

    /**
     * Is the standing space over this station wider than a corridor can be bored?
     *
     * <p>Reads a {@link #CAVE_OPENNESS_SPAN} x {@code CAVE_OPENNESS_SPAN} square of columns, all clear for
     * {@link #CAVE_OPENNESS_LEVELS} blocks above the station's OWN floor level. The station's column has to
     * be one of the nine, but it may be any of them, so a station on the very lip of a cave passes on the
     * square that reaches into the cave rather than the one that reaches back into the rock.
     *
     * <p>Two blocks of clear, not three, and for a stated reason: the planner's terminus law only ever
     * guaranteed {@link HiddenChamberPlan#MOUTH_MIN_HEADROOM}, so a three-block test would refuse the
     * shallowest legal breach exactly as the old rule did. Three COLUMNS, not two, for the mirror reason: two
     * is the corridor's own width, and a rule a corridor satisfies is not a rule.
     *
     * <p>Iteration order is fixed (origin x ascending, then z), so the answer never depends on hash order.
     */
    private static boolean standsInOpenCave(Probe probe, Position station) {
        for (int originX = station.x() - CAVE_OPENNESS_SPAN + 1; originX <= station.x(); originX++) {
            for (int originZ = station.z() - CAVE_OPENNESS_SPAN + 1; originZ <= station.z(); originZ++) {
                if (openStandingSquare(probe, originX, originZ, station.y())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean openStandingSquare(Probe probe, int originX, int originZ, int floorY) {
        for (int dx = 0; dx < CAVE_OPENNESS_SPAN; dx++) {
            for (int dz = 0; dz < CAVE_OPENNESS_SPAN; dz++) {
                for (int level = 1; level <= CAVE_OPENNESS_LEVELS; level++) {
                    if (!probe.at(originX + dx, floorY + level, originZ + dz).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * The mouth's own upper cave, flooded at most once per {@link #findExit} and only when something asks.
     *
     * <p>Laziness is measured, not assumed. Across the seventy-two-case plan-to-blocks sweep the flood costs
     * about 1.9 ms a chamber (136 ms of an 879 ms run) and not one of those chambers needs it: they all
     * breach into a TALL cave and stop at the fast path. So the flood is deferred until a station actually
     * reaches the low-cave clause — which is exactly the shape it was written for — and the tall-cave case
     * pays nothing at all for it. The trace hook forces it, because a diagnosis that omits the number is
     * worth less than the microseconds it saves, and the trace is never on in production.
     */
    private static final class MouthCave {
        private final Probe probe;
        private final List<Position> mouth;
        private final Position centroid;
        private final int mouthFloorY;
        private Set<Position> air;

        private MouthCave(Probe probe, List<Position> mouth, Position centroid, int mouthFloorY) {
            this.probe = probe;
            this.mouth = mouth;
            this.centroid = centroid;
            this.mouthFloorY = mouthFloorY;
        }

        private Set<Position> air() {
            if (air == null) {
                air = floodMouthCaveAir(probe, mouth, centroid, mouthFloorY);
            }
            return air;
        }
    }

    /** Does this station stand in the air the MOUTH stands in? Membership only; the flood is done once. */
    private static boolean standsInTheMouthsOwnCave(MouthCave mouthCave, Position station) {
        for (int level = 1; level <= CAVE_OPENNESS_LEVELS; level++) {
            if (mouthCave.air().contains(new Position(station.x(), station.y() + level, station.z()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The upper cave the mouth stands in, read back from air alone.
     *
     * <p>Seeded on the standing space over every mouth cell — the {@link HiddenChamberPlan#MOUTH_MIN_HEADROOM}
     * blocks a victim is walking through when the false floor gives way — and grown through {@link
     * ScanCell#AIR} only, one cardinal step at a time with a block of climb or fall, which is the same
     * diagonal-free move the exit walk uses. Water is deliberately not air here: the chamber's own lake is
     * not the cave the mouth is in.
     *
     * <p>It is run ONCE per {@link #findExit} and asked the same question by every station, which is both
     * cheaper than a flood per station and identical in meaning: reachability is symmetric, so "the station's
     * head air reaches the mouth's" and "the mouth's air reaches the station's head" are one relation.
     *
     * <h2>What keeps it out of the encounter, and what deliberately does not</h2>
     * The chamber-void CELL SET is NOT refused, and that is a correction rather than an omission. Refusing it
     * looked right and was wrong: the void fill is six-connected, so it runs up the corridor and up the
     * breach column itself, and refusing every cell it touched walled the flood out of the very opening it
     * was sent to find. (Measured: the section-8 straight-route fixture, whose one opening is breached
     * upward through the cave floor, reconstructed {@link PartialReason#NO_EXIT} for exactly that reason.)
     * The room is excluded by the two laws below instead, which exclude the room without also excluding the
     * way out of it — and nothing is lost by the change, because a chamber station can never be an opening
     * anyway: the chamber floor lies {@link HiddenChamberPlan#DROP_MIN} or more under the mouth, and the exit
     * law's floor band is {@link HiddenChamberPlan#EXIT_FLOOR_TOLERANCE}.
     *
     * <p>Three things bound it, and each is a law rather than a horizon:
     * <ul>
     *   <li>The collapse cells seal the shaft. The mouth's own floor block is
     *       {@link ScanCell#COLLAPSE_POWDER}, which is not air, so the flood standing on top of it cannot
     *       step DOWN into the shaft at all. That is the whole reason a chamber whose corridor breaches
     *       nothing reads {@link PartialReason#NO_EXIT}: the only path from the mouth's cave to that bore
     *       would run through the room, and the flood can neither fall into it nor reach it.</li>
     *   <li>The Y band is the exit law's own floor tolerance ({@link #CAVE_FLOOD_BELOW_MOUTH} under the mouth
     *       floor, {@link #CAVE_FLOOD_ABOVE_MOUTH} over it), so even a flood that entered the corridor
     *       through a legal breach runs out of band long before it reaches the room the victim fell into.</li>
     *   <li>The horizontal box is {@link #CAVE_FLOOD_HALF_SPAN} around the mouth centroid, which holds every
     *       station the exit band can contain, and {@link #MAX_CAVE_FLOOD_CELLS} is that box's exact cell
     *       count, so the cap cannot clip a connection the bands allow.</li>
     * </ul>
     */
    private static Set<Position> floodMouthCaveAir(Probe probe, List<Position> mouth, Position centroid,
                                                   int mouthFloorY) {
        int minX = centroid.x() - CAVE_FLOOD_HALF_SPAN;
        int maxX = centroid.x() + CAVE_FLOOD_HALF_SPAN;
        int minZ = centroid.z() - CAVE_FLOOD_HALF_SPAN;
        int maxZ = centroid.z() + CAVE_FLOOD_HALF_SPAN;
        int minY = mouthFloorY - CAVE_FLOOD_BELOW_MOUTH;
        int maxY = mouthFloorY + CAVE_FLOOD_ABOVE_MOUTH;

        Set<Position> air = new LinkedHashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        List<Position> seeds = new ArrayList<>();
        for (Position cell : mouth) {
            for (int level = 1; level <= HiddenChamberPlan.MOUTH_MIN_HEADROOM; level++) {
                seeds.add(new Position(cell.x(), cell.y() + level, cell.z()));
            }
        }
        seeds.sort(Comparator.naturalOrder());
        for (Position seed : seeds) {
            if (seed.y() < minY || seed.y() > maxY || !probe.at(seed.x(), seed.y(), seed.z()).isAir()) {
                continue;
            }
            if (air.add(seed)) {
                queue.addLast(seed);
            }
        }
        while (!queue.isEmpty() && air.size() < MAX_CAVE_FLOOD_CELLS) {
            Position cell = queue.removeFirst();
            for (int[] step : CAVE_FLOOD_STEPS) {
                int x = cell.x() + step[0];
                int y = cell.y() + step[1];
                int z = cell.z() + step[2];
                if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                    continue;
                }
                Position next = new Position(x, y, z);
                if (air.contains(next) || !probe.at(x, y, z).isAir()) {
                    continue;
                }
                air.add(next);
                queue.addLast(next);
            }
        }
        return air;
    }

    /**
     * Is the clear column over this station TALLER than the corridor is bored? One block of air above a
     * {@link HiddenChamberPlan#CORRIDOR_CLEAR_HEIGHT} bore says the space over it is room rather than tunnel.
     *
     * <p>{@link #findExit}'s SEEDING still asks it, and that use is sound: out beyond the exit ring a bare
     * bore is a yard of the corridor and not somewhere the fall leaves anyone standing, so a walk should not
     * begin there. {@link #isOpening} no longer asks it — as a test for SURFACING it was wrong, and a real
     * world proved it wrong; see that method's own note. It survives there only as a field on the
     * {@code openingCheck} trace.
     */
    private static boolean clearerThanABore(Probe probe, int x, int y, int z) {
        return probe.at(x, y + HiddenChamberPlan.CORRIDOR_CLEAR_HEIGHT + 1, z).isAir();
    }

    /** Horizontal Chebyshev distance: the metric every exit band in {@link HiddenChamberPlan} is written in. */
    private static int horizontalDistance(Position from, Position to) {
        return Math.max(Math.abs(from.x() - to.x()), Math.abs(from.z() - to.z()));
    }

    /** Follow parent links from {@code from} to the root of its walk; the route, in link order. */
    private static List<Position> chain(Map<Position, Position> parents, Position from) {
        List<Position> route = new ArrayList<>();
        Position cursor = from;
        while (true) {
            route.add(cursor);
            Position parent = parents.get(cursor);
            if (parent == null || parent.equals(cursor)) {
                break;
            }
            cursor = parent;
        }
        return route;
    }

    /**
     * How many times a route changes horizontal heading. The first step sets the heading rather than bending
     * it, and a climb or a drop along one heading is not a bend, so this counts corners in PLAN — which is
     * what "the corridor winds" means. Reversing a route reverses its corners without adding or losing one,
     * so a route may be counted from either end.
     */
    private static int countBends(List<Position> route) {
        int bends = 0;
        int lastDx = 0;
        int lastDz = 0;
        for (int index = 1; index < route.size(); index++) {
            int dx = Integer.signum(route.get(index).x() - route.get(index - 1).x());
            int dz = Integer.signum(route.get(index).z() - route.get(index - 1).z());
            if (index > 1 && (dx != lastDx || dz != lastDz)) {
                bends++;
            }
            lastDx = dx;
            lastDz = dz;
        }
        return bends;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Dressing                                                                                              */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * Census the dressing the chamber void touches: the void cells themselves (water) plus their six-neighbour
     * shell (icicles hanging off the roof, props standing on the floor, ice columns rising through the room).
     *
     * <h2>What counts as an ice COLUMN, and what used to</h2>
     * A block of packed or blue ice joins the census only when it is free-standing — void on at least
     * {@link #INTERIOR_ICE_MIN_OPEN_SIDES} of its four horizontal sides — because the chamber's own wall seal
     * is packed ice and a wall is not a column. That much is unchanged, and it is not enough on its own.
     * {@code interiorIce} used to be a count of those BLOCKS, and on real terrain it counted the wrong thing:
     * where the ground under the room is hollow the planner seals a packed-ice floor beneath each interior
     * column, and every one of those seals is a free-standing block with void on all four sides. A lake
     * chamber on hollow ground therefore tallied dozens of "interior ice" and read as a cathedral.
     *
     * <p>So {@code interiorIce} is now a count of COLUMNS: maximal vertical runs of free-standing ice at
     * least {@link #INTERIOR_ICE_MIN_COLUMN_HEIGHT} blocks tall. A floor seal is one block thick and a lake
     * bed no more than {@link HiddenChamberPlan#LAKE_MAX_DIG}, so neither can ever reach that height, while a
     * pillar rising through the room trivially does. {@link #THEME_MIN_INTERIOR_ICE} moves with it — it is
     * now {@link HiddenChamberPlan#CATHEDRAL_MIN_ICE_COLUMNS} columns, which is what the planner's own
     * constant always meant.
     */
    private static Tallies tally(Probe probe, ChamberVoid chamber) {
        Set<Position> cells = chamber.cells();
        int icicles = 0;
        Set<Position> freeStandingIce = new HashSet<>();
        int water = 0;
        int chests = 0;
        int lanterns = 0;
        int bones = 0;
        int wood = 0;
        Map<Integer, Set<Long>> waterColumnsByY = new HashMap<>();
        Set<Position> counted = new HashSet<>();
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (Position cell : cells) {
            if (probe.at(cell.x(), cell.y(), cell.z()) == ScanCell.WATER) {
                water++;
                waterColumnsByY.computeIfAbsent(cell.y(), y -> new HashSet<>())
                        .add(columnKey(cell.x(), cell.z()));
            }
            for (int[] step : steps) {
                Position neighbour =
                        new Position(cell.x() + step[0], cell.y() + step[1], cell.z() + step[2]);
                if (cells.contains(neighbour) || !counted.add(neighbour)) {
                    continue;
                }
                switch (probe.at(neighbour.x(), neighbour.y(), neighbour.z())) {
                    case ICICLE -> icicles++;
                    case CHEST -> chests++;
                    case LANTERN -> lanterns++;
                    case BONE -> bones++;
                    case WOOD_DEBRIS -> wood++;
                    case ICE_PACKED, ICE_BLUE -> {
                        int open = 0;
                        for (int[] side : CARDINALS) {
                            if (cells.contains(new Position(neighbour.x() + side[0], neighbour.y(),
                                    neighbour.z() + side[1]))) {
                                open++;
                            }
                        }
                        if (open >= INTERIOR_ICE_MIN_OPEN_SIDES) {
                            freeStandingIce.add(neighbour);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return new Tallies(icicles, countFreeStandingIceColumns(freeStandingIce), water,
                hasWaterRectangle(waterColumnsByY), chests, lanterns, bones, wood);
    }

    /**
     * How many free-standing ice COLUMNS those blocks make: maximal vertical runs at least
     * {@link #INTERIOR_ICE_MIN_COLUMN_HEIGHT} tall, counted once each on the block that completes them.
     *
     * <p>Two separate runs in one column count twice, which is right — a pillar broken by a gap is two
     * pillars, not none. The result is a sum over independent columns, so it does not depend on the order the
     * map is walked in.
     */
    private static int countFreeStandingIceColumns(Set<Position> freeStandingIce) {
        Map<Long, TreeSet<Integer>> runsByColumn = new HashMap<>();
        for (Position cell : freeStandingIce) {
            runsByColumn.computeIfAbsent(columnKey(cell.x(), cell.z()), key -> new TreeSet<>())
                    .add(cell.y());
        }
        int columns = 0;
        for (TreeSet<Integer> heights : runsByColumn.values()) {
            int run = 0;
            int previous = Integer.MIN_VALUE;
            for (int y : heights) {
                run = y == previous + 1 ? run + 1 : 1;
                previous = y;
                if (run == INTERIOR_ICE_MIN_COLUMN_HEIGHT) {
                    columns++;
                }
            }
        }
        return columns;
    }

    /** The authored frigid lake keeps a {@link HiddenChamberPlan#LAKE_OPEN_CENTRE_SPAN} square of open water. */
    private static boolean hasWaterRectangle(Map<Integer, Set<Long>> waterColumnsByY) {
        int side = HiddenChamberPlan.LAKE_OPEN_CENTRE_SPAN;
        for (Set<Long> columns : waterColumnsByY.values()) {
            if (columns.size() < side * side) {
                continue;
            }
            for (long key : columns) {
                int originX = (int) (key >> 32);
                int originZ = (int) (key & 0xFFFFFFFFL);
                boolean full = true;
                for (int dx = 0; dx < side && full; dx++) {
                    for (int dz = 0; dz < side && full; dz++) {
                        full = columns.contains(columnKey(originX + dx, originZ + dz));
                    }
                }
                if (full) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Name the theme from the dressing, most-certain evidence first.
     *
     * <p>The ORDER is the law here, and it is the opposite of the one this method shipped with. A theme is
     * only as good as the thing that cannot be mistaken for something else:
     * <ol>
     *   <li>{@link Theme#FRIGID_LAKE} — {@link #THEME_MIN_WATER} cells of standing water INCLUDING a full
     *       {@link HiddenChamberPlan#LAKE_OPEN_CENTRE_SPAN} square of it. Nothing else in the encounter lays
     *       water at all, so this is unambiguous and goes first.</li>
     *   <li>{@link Theme#LOST_EXPEDITION} — a cache chest AND a lantern. Nothing else lays either.</li>
     *   <li>{@link Theme#ICE_CATHEDRAL} — icicles or free-standing ice columns. This one is a JUDGEMENT about
     *       ice, and every theme is built out of ice, so it is asked last and only once the two unambiguous
     *       questions have said no.</li>
     * </ol>
     *
     * <p>It used to ask the cathedral question first, and a real world showed what that costs: a
     * {@code FRIGID_LAKE} chamber standing on genuine terrain came back {@link Theme#ICE_CATHEDRAL}. Its
     * under-floor was hollow, so the planner sealed a packed-ice floor under every interior column — and each
     * of those seals stands proud with open void on every side, which is exactly what the ice tally was
     * counting. The lake's own packed-ice bed added more. The cathedral threshold was met before the water
     * was ever looked at. Every fixture missed it because a hand-built fixture sits on SOLID ground and
     * therefore needs almost no floor seals; only real terrain produces them in numbers.
     *
     * <p>The tally behind clause 3 was tightened in the same pass (see {@link #tally}), so the reorder is not
     * carrying the fix alone: a floor seal is one block thick and no longer counts as a column at all.
     */
    private static Theme classify(Tallies tallies) {
        if (tallies.water() >= THEME_MIN_WATER && tallies.waterRectangle()) {
            return Theme.FRIGID_LAKE;
        }
        if (tallies.chests() >= HiddenChamberPlan.EXPEDITION_CACHE_CHESTS && tallies.lanterns() >= 1) {
            return Theme.LOST_EXPEDITION;
        }
        if (tallies.icicles() >= THEME_MIN_ICICLES || tallies.interiorIce() >= THEME_MIN_INTERIOR_ICE) {
            return Theme.ICE_CATHEDRAL;
        }
        return Theme.UNKNOWN;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Probe cache                                                                                           */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * One memoised view of the reader for one candidate. The flood fill and the route walk read the same cells
     * many times over; the world adapters behind {@link CellReader} are chunk lookups, so caching keeps a
     * reconstruction to roughly one read per cell inside its own box. It also latches the unreadable flag that
     * {@link #fail} consults.
     */
    private static final class Probe {
        private final CellReader reader;
        private final Map<Position, ScanCell> cache = new HashMap<>();
        private boolean unreadable;

        private Probe(CellReader reader) {
            this.reader = reader;
        }

        private ScanCell at(int x, int y, int z) {
            Position key = new Position(x, y, z);
            ScanCell cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            ScanCell cell = reader.cell(x, y, z);
            if (cell == null) {
                cell = ScanCell.UNREADABLE;
            }
            if (cell == ScanCell.UNREADABLE) {
                unreadable = true;
            }
            cache.put(key, cell);
            return cell;
        }

        private boolean sawUnreadable() {
            return unreadable;
        }
    }
}
