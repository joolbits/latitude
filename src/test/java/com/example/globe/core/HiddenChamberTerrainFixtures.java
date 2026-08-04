package com.example.globe.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared array-backed {@link HiddenChamberPlan.TerrainProbe} fixtures for the hidden-chamber test-writer
 * pass. Follows the idiom {@code HiddenChamberPlanTest}'s own private {@code Terrain} class already
 * established: owner-local coordinates, a snowy plateau, a uniform safe floor, and deterministic answers.
 *
 * <p>Three ready-made shapes are provided:
 * <ul>
 *   <li>{@link #roomy()} -- a wide, fully safe volume around a snowy plateau, generous enough for the
 *       widest {@code CATHEDRAL} chamber. Chunk-agnostic (owner-local only), so one instance may be reused
 *       across many {@code (seed, chunkX, chunkZ)} calls to {@link HiddenChamberPlan#plan}.</li>
 *   <li>{@link #compactForcing()} -- a shallow mouth floor (the legal minimum) plus a narrow safe-width
 *       corridor at the two {@code landingY} rows a shallow mouth tries for a cathedral attempt, so every
 *       accepted plan is squeezed down to {@code COMPACT}. Empirically verified: {@code ICE_CATHEDRAL} and
 *       {@code LOST_EXPEDITION} both accept as {@code COMPACT} under this shape.</li>
 *   <li>{@link #narrow()} -- the plateau shrunk to one quart column (mirrors {@code HiddenChamberPlanTest}'s
 *       own {@code narrowTerrain()}), so only a handful of candidate mouths ever tie for best; a single
 *       {@link Mutable#hazardAt} on the resulting plan's own writes reliably rejects the WHOLE plan instead
 *       of the planner quietly falling through to an alternate candidate elsewhere on a wide-open plateau.</li>
 * </ul>
 *
 * <p>Both expose {@link Mutable#hazardAt} and {@link Mutable#foreignQuartAt} so a caller can start from a
 * known-good shape and inject exactly one safety fault, and {@link Mutable#extraSnowyIsland} so a caller
 * can add a second, independent mouth-eligible patch far outside the owner chunk (never itself anchor-
 * eligible, since mouth anchors are always scanned in owner-local {@code 0..15}) to prove neighbourhood
 * independence.
 */
public final class HiddenChamberTerrainFixtures {

    private HiddenChamberTerrainFixtures() {
    }

    /** The snowy mouth-eligible patch {@link #roomy()} and {@link #compactForcing()} carry. */
    public static final int PLATEAU_MIN = 4;
    public static final int PLATEAU_MAX = 11;
    public static final int PLATEAU_CENTRE_X = (PLATEAU_MIN + PLATEAU_MAX) / 2;
    public static final int PLATEAU_CENTRE_Z = (PLATEAU_MIN + PLATEAU_MAX) / 2;
    /** {@link #narrow()}'s plateau: one quart column, exactly {@code HiddenChamberPlanTest}'s own shrink. */
    public static final int NARROW_PLATEAU_MAX = 7;

    /** {@link #roomy()}'s deep mouth floor: caps every achievable drop at {@link HiddenChamberPlan#DROP_MAX}. */
    public static final int ROOMY_FLOOR_Y = 40;
    /** {@link #roomy()}'s surrounding (non-snowy) upper-cave floor, within exit-floor tolerance of the mouth. */
    public static final int ROOMY_SURROUND_FLOOR_Y = 34;

    /** {@link #compactForcing()}'s mouth floor: the legal minimum, so only two drops ever try CATHEDRAL. */
    public static final int SHALLOW_FLOOR_Y = HiddenChamberPlan.MOUTH_FLOOR_MIN_Y;
    private static final int COMPACT_SQUARE_HALF = 7;
    /** {@code landingY} rows a shallow (16) mouth's two cathedral-eligible drops (14, 13) land on. */
    private static final Set<Integer> COMPACT_HAZARD_ROWS = Set.of(2, 3);

    private static final int HEADROOM = 6;
    private static final int ARRAY_MIN = -40;
    private static final int ARRAY_MAX = 55;

    public static Mutable roomy() {
        return new Mutable(ROOMY_SURROUND_FLOOR_Y, ROOMY_FLOOR_Y, false, PLATEAU_MAX);
    }

    public static Mutable compactForcing() {
        return new Mutable(SHALLOW_FLOOR_Y, SHALLOW_FLOOR_Y, true, PLATEAU_MAX);
    }

    /** Roomy, but with the plateau shrunk to one quart column: see the class doc for why. */
    public static Mutable narrow() {
        return new Mutable(ROOMY_SURROUND_FLOOR_Y, ROOMY_FLOOR_Y, false, NARROW_PLATEAU_MAX);
    }

    /**
     * One flat upper cave: the snowy mouth patch and the surrounding cave floor sit at the SAME height
     * ({@link #ROOMY_FLOOR_Y}), so the whole cave is one six-block band of air over one floor.
     *
     * <p>{@link #roomy()} deliberately raises its snowy plateau six blocks above the surrounding floor, which
     * is the widest legal mouth-to-exit floor gap ({@link HiddenChamberPlan#EXIT_FLOOR_TOLERANCE}) and is
     * exactly what a planner test wants. It is the wrong shape for a test that renders a plan to BLOCKS and
     * reads it back, because the surrounding floor then sits BELOW the mouth floor and the scanner's
     * chamber-void box -- which is capped one block under the mouth floor -- swallows the whole surrounding
     * cave through the corridor. A flat cave is the ordinary case and keeps the room the room.
     */
    public static Mutable flatCave() {
        return new Mutable(ROOMY_FLOOR_Y, ROOMY_FLOOR_Y, false, PLATEAU_MAX);
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) (x + 4096) << 42) | ((long) (y + 4096) << 21) | (z + 4096);
    }

    private static long quartKey(int qx, int qy, int qz) {
        return ((long) (qx + 4096) << 42) | ((long) (qy + 4096) << 21) | (qz + 4096);
    }

    /**
     * Mutable array-backed probe. The base shape is fixed at construction (surrounding floor, plateau
     * floor, and whether the base-layer compact-forcing hazard is armed); callers layer additional single
     * -cell hazards, foreign quarts, and extra snowy islands on top before calling {@link HiddenChamberPlan#plan}.
     */
    public static final class Mutable implements HiddenChamberPlan.TerrainProbe {
        private final int surroundFloorY;
        private final int plateauFloorY;
        private final boolean compactHazardArmed;
        private final int plateauMaxX;
        private final int plateauMaxZ;
        private final Map<Long, HiddenChamberPlan.CellKind> cellOverrides = new HashMap<>();
        private final Set<Long> foreignQuarts = new HashSet<>();
        private final java.util.List<int[]> islands = new java.util.ArrayList<>();
        private final java.util.List<int[]> voidBands = new java.util.ArrayList<>();

        private Mutable(int surroundFloorY, int plateauFloorY, boolean compactHazardArmed, int plateauMax) {
            this.surroundFloorY = surroundFloorY;
            this.plateauFloorY = plateauFloorY;
            this.compactHazardArmed = compactHazardArmed;
            this.plateauMaxX = plateauMax;
            this.plateauMaxZ = plateauMax;
        }

        /** Override one cell's kind; takes precedence over every base-shape and island rule. */
        public Mutable hazardAt(int x, int y, int z, HiddenChamberPlan.CellKind kind) {
            cellOverrides.put(cellKey(x, y, z), kind);
            return this;
        }

        /** Mark one quart as NOT saved-glacial (the fail-closed biome check). */
        public Mutable foreignQuartAt(int quartX, int quartY, int quartZ) {
            foreignQuarts.add(quartKey(quartX, quartY, quartZ));
            return this;
        }

        /**
         * Add a second, independent snowy+roofed rectangle (its own would-be mouth patch) at the given
         * owner-local bounds and floor. Never itself anchor-eligible for the SAME plan() call unless it
         * falls inside owner-local {@code 0..15}, matching how a real neighbour chunk's own terrain would
         * appear in this probe's coordinate frame.
         */
        public Mutable extraSnowyIsland(int minX, int maxX, int minZ, int maxZ, int floorY) {
            islands.add(new int[] {minX, maxX, minZ, maxZ, floorY});
            return this;
        }

        /**
         * Hollow one Y band out of the WHOLE fixture: every cell in {@code minY..maxY} reads
         * {@link HiddenChamberPlan.CellKind#AIR} whatever the base shape says. This is the natural sub-floor
         * void a real glacial cave system routinely puts under a chamber, and it is the only way to see
         * whether the planner authors a floor under its own room or leaves the terrain's hole in it.
         *
         * <p>Column tops are untouched: {@link #column} still answers with the same walkable cave floor, so
         * the mouth, the exit termini and the drop band all stay exactly where they were.
         */
        public Mutable voidBand(int minY, int maxY) {
            voidBands.add(new int[] {minY, maxY});
            return this;
        }

        private boolean inPlateau(int x, int z) {
            return x >= PLATEAU_MIN && x <= plateauMaxX && z >= PLATEAU_MIN && z <= plateauMaxZ;
        }

        private int[] islandAt(int x, int z) {
            for (int[] island : islands) {
                if (x >= island[0] && x <= island[1] && z >= island[2] && z <= island[3]) {
                    return island;
                }
            }
            return null;
        }

        private boolean inSafeSquare(int x, int z) {
            return x >= PLATEAU_CENTRE_X - COMPACT_SQUARE_HALF && x <= PLATEAU_CENTRE_X + COMPACT_SQUARE_HALF
                    && z >= PLATEAU_CENTRE_Z - COMPACT_SQUARE_HALF && z <= PLATEAU_CENTRE_Z + COMPACT_SQUARE_HALF;
        }

        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            if (localX < ARRAY_MIN || localX > ARRAY_MAX || localZ < ARRAY_MIN || localZ > ARRAY_MAX) {
                return null;
            }
            int[] island = islandAt(localX, localZ);
            if (island != null) {
                return new HiddenChamberPlan.ColumnInfo(island[4], true, true, HEADROOM);
            }
            boolean plateau = inPlateau(localX, localZ);
            return new HiddenChamberPlan.ColumnInfo(
                    plateau ? plateauFloorY : surroundFloorY, plateau, true, HEADROOM);
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            HiddenChamberPlan.CellKind override = cellOverrides.get(cellKey(localX, y, localZ));
            if (override != null) {
                return override;
            }
            if (localX < ARRAY_MIN || localX > ARRAY_MAX || localZ < ARRAY_MIN || localZ > ARRAY_MAX) {
                return HiddenChamberPlan.CellKind.UNREADABLE;
            }
            if (y <= 0) {
                return HiddenChamberPlan.CellKind.BEDROCK;
            }
            for (int[] band : voidBands) {
                if (y >= band[0] && y <= band[1]) {
                    return HiddenChamberPlan.CellKind.AIR;
                }
            }
            if (compactHazardArmed && COMPACT_HAZARD_ROWS.contains(y) && !inSafeSquare(localX, localZ)) {
                return HiddenChamberPlan.CellKind.BEDROCK;
            }
            int[] island = islandAt(localX, localZ);
            int floorY = island != null ? island[4] : (inPlateau(localX, localZ) ? plateauFloorY : surroundFloorY);
            if (y <= floorY) {
                return HiddenChamberPlan.CellKind.SOLID_SAFE;
            }
            return y <= floorY + HEADROOM ? HiddenChamberPlan.CellKind.AIR : HiddenChamberPlan.CellKind.SOLID_SAFE;
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            return !foreignQuarts.contains(quartKey(quartLocalX, quartY, quartLocalZ));
        }
    }
}
