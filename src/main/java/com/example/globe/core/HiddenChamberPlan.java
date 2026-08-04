package com.example.globe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure-Java plan layer for the {@code globe:hidden_glacial_chamber} worldgen encounter.
 *
 * <p>The encounter is a concealed collapse mouth in the snowy floor of an upper glacial cave. A victim falls
 * twelve to twenty blocks onto a deep powder cushion inside an authored irregular chamber and leaves through a
 * winding two-wide corridor that breaches back into the same upper cave a short walk from the mouth. The
 * emotional target is "what is this place?", so the chamber is never a small sealed box: it is a union of
 * overlapping elliptical lobes with a varying ceiling and a themed dressing pass.
 *
 * <p>This class knows nothing about Minecraft. All terrain access goes through {@link TerrainProbe}; the world
 * layer supplies the real implementation and tests supply array-backed fakes. Every decision is a pure hash of
 * {@code (worldSeed ^ SALT, chunkX, chunkZ)} folded with role-specific constants, so the same inputs always
 * produce a byte-identical write list, including its order. No {@link java.util.Random} is used anywhere and no
 * roll is consumed in sequence.
 *
 * <h2>Envelope</h2>
 * The authored volume occupies the owner chunk plus exactly one cardinal neighbour, exactly as
 * {@link SubterraneanTrapPlan}'s cavern does. Canonical space places the owner at {@code x 0..15} and the
 * neighbour at {@code x 16..31}, with {@code z 0..15}, and {@link EnvelopeDirection} rotates that canonical
 * strip onto the real cardinal. Write cells must satisfy canonical {@code x in 1..30, z in 1..14}; read and
 * probe cells may use the full {@code 0..31 / 0..15} shell. Volume columns are held one further block inside
 * ({@code x in 2..29, z in 2..13}) so that every wall seal derived from them still lands inside the write
 * envelope, and mouth columns are held inside the owner half ({@code x in 3..15, z in 3..12}) so that the
 * cushion, which is the mouth footprint dilated by one, keeps the same guarantee.
 *
 * <h2>Rarity</h2>
 * {@link #occurrenceGate(long, int, int)} is the one and only rarity in this encounter. It is never called from
 * {@link #plan}: the world layer calls it exactly once, after a plan has been accepted, so that rarity never
 * changes which chunk would have been eligible.
 */
public final class HiddenChamberPlan {

    /**
     * Fold salt for every hash in this class. ASCII {@code "HIDDNCHA"}; it exists purely so the hidden-chamber
     * decision stream can never alias another feature's stream for the same world seed and chunk.
     */
    public static final long SALT = 0x48494444_4E434841L;

    /** The single occurrence rarity: one eligible chunk in fourteen actually builds the encounter. */
    public static final int OCCURRENCE_MODULUS = 14;

    /* ---------------------------------------------------------------------------------------------------- */
    /* Geometry law constants. Nothing in this class inlines a bare number that a designer might want to move. */
    /* ---------------------------------------------------------------------------------------------------- */

    /** A collapse mouth is an irregular two-to-four cell hole; never a line and never a two-by-two square. */
    public static final int MOUTH_MIN_CELLS = 2;
    public static final int MOUTH_MAX_CELLS = 4;
    /** Every mouth column must be snowy, roofed, and carry at least this much walkable headroom. */
    public static final int MOUTH_MIN_HEADROOM = 2;
    /** The mouth reads as one flat patch of floor: its column surfaces may differ by at most one block. */
    public static final int MOUTH_MAX_FLOOR_SPREAD = 1;
    /**
     * Block heights are measured in eighths here, because a glacial cave floor is not made of whole blocks.
     * Step 7's {@code glacial_frost_carpet} lays {@code minecraft:snow} LAYERS on it, and a layer's real
     * walking surface is {@code layers/8} of a block above its own cell floor. Two columns whose integer
     * floor Y is identical can therefore stand almost a whole block apart, which is exactly the flatness the
     * mouth patch has to judge.
     */
    public static final int EIGHTHS_PER_BLOCK = 8;
    /**
     * How much of a partial top layer the mouth cube is allowed to sit under. The cube is a FULL block, so on
     * a partial-layer column it replaces the block one cell lower and its top lands at the whole-block line
     * just under the real surface; the leftover lip is {@code topEighths/8} of a block. A player walks up at
     * most 0.6 of a block, so a lip over half a block would turn the concealed floor into a visible dimple
     * with a wall on the far side. The shipped carpet only ever lays one to three layers.
     */
    public static final int MOUTH_MAX_TOP_LAYER_EIGHTHS = 4;
    /** Absolute Y band the upper glacial cave floor must sit in for a mouth to be eligible. */
    public static final int MOUTH_FLOOR_MIN_Y = 16;
    public static final int MOUTH_FLOOR_MAX_Y = 45;

    /** The fall itself, measured mouth floor minus landing. Deeper is preferred. */
    public static final int DROP_MIN = 12;
    public static final int DROP_MAX = 20;
    /** No authored cell may exist at or below Y0. */
    public static final int MIN_AUTHORED_Y = 1;
    /** Powder cushion depth in blocks, occupying {@code landingY} and {@code landingY + 1}. */
    public static final int CUSHION_DEPTH = 2;
    /** The cushion base is one block under the landing, so the landing can never sit lower than this. */
    public static final int MIN_LANDING_Y = MIN_AUTHORED_Y + 1;
    /** The powder cushion is the shaft footprint dilated by one block in the eight-neighbourhood. */
    public static final int CUSHION_DILATION = 1;

    /** Square side of the firm escape shelf beside the cushion. */
    public static final int SHELF_SPAN = 2;
    /** Clear blocks required above the shelf surface so a player can climb out of the powder. */
    public static final int SHELF_MIN_HEADROOM = 2;

    public static final int COMPACT_SPAN_X_MIN = 9;
    public static final int COMPACT_SPAN_X_MAX = 14;
    public static final int COMPACT_SPAN_Z_MIN = 7;
    public static final int COMPACT_SPAN_Z_MAX = 12;
    public static final int COMPACT_HEIGHT_MIN = 5;
    public static final int COMPACT_HEIGHT_MAX = 8;

    public static final int CATHEDRAL_SPAN_X_MIN = 16;
    public static final int CATHEDRAL_SPAN_X_MAX = 28;
    public static final int CATHEDRAL_SPAN_Z_MIN = 10;
    public static final int CATHEDRAL_SPAN_Z_MAX = 14;
    public static final int CATHEDRAL_HEIGHT_MIN = 10;
    public static final int CATHEDRAL_HEIGHT_MAX = 16;

    /** Chamber silhouette: a union of this many overlapping elliptical lobes. */
    public static final int CHAMBER_MIN_LOBES = 2;
    public static final int CHAMBER_MAX_LOBES = 4;
    /** A chamber roof that reads as a roof, not a lid: at least this many distinct clear heights. */
    public static final int CHAMBER_MIN_DISTINCT_HEIGHTS = 3;
    /** Carved columns as a percentage of the chamber bounding box. Outside this band the shape is repaired. */
    public static final int CHAMBER_FILL_MIN_PERCENT = 45;
    public static final int CHAMBER_FILL_MAX_PERCENT = 75;
    /** A fully carved axis-aligned block of this side at one uniform height would read as a square room. */
    public static final int SQUARE_ROOM_SPAN = 5;
    /** Cathedral spans are trimmed by this much so a corridor field always survives beside the chamber. */
    public static final int CHAMBER_CORRIDOR_RESERVE_X = 8;
    public static final int CHAMBER_CORRIDOR_RESERVE_Z = 2;

    /** Horizontal Chebyshev distance from the mouth centroid to the second opening. */
    public static final int EXIT_DISTANCE_MIN = 8;
    public static final int EXIT_DISTANCE_MAX = 16;
    /** The second opening must sit in the same upper cave, within this many blocks of the mouth floor. */
    public static final int EXIT_FLOOR_TOLERANCE = 6;
    public static final int CORRIDOR_WIDTH = 2;
    public static final int CORRIDOR_CLEAR_HEIGHT = 3;
    /** A corridor that never turns reads as a drain pipe; it must bend at least this often. */
    public static final int CORRIDOR_MIN_BENDS = 2;
    /** Flat stations required between two consecutive one-block rises, so the climb never reads as a stair. */
    public static final int CORRIDOR_MIN_FLAT_RUN = 2;
    /** The corridor never passes within this many columns of a shaft column: no route back under the trap. */
    public static final int SHAFT_CLEARANCE = 2;
    /** Rock left between the chamber ceiling and any corridor floor that crosses above the chamber. */
    public static final int CHAMBER_ROOF_CLEARANCE = 2;

    public static final int CATHEDRAL_MIN_ICE_COLUMNS = 2;
    public static final int CATHEDRAL_MAX_ICE_COLUMNS = 4;
    public static final int CATHEDRAL_MIN_ICICLE_CLUSTERS = 3;
    public static final int CATHEDRAL_MAX_ICICLE_CLUSTERS = 6;
    public static final int CATHEDRAL_MIN_ICICLE_LENGTH = 1;
    public static final int CATHEDRAL_MAX_ICICLE_LENGTH = 3;
    /** The fossil accent is a rare grace note, not a fixture: one chamber in six carries it. */
    public static final int CATHEDRAL_FOSSIL_MODULUS = 6;
    public static final int CATHEDRAL_FOSSIL_MIN_CELLS = 2;
    public static final int CATHEDRAL_FOSSIL_MAX_CELLS = 3;
    /** The undressed dark side lobe a cathedral always keeps. */
    public static final int RECESS_SPAN = 3;

    public static final int LAKE_MIN_DIG = 1;
    public static final int LAKE_MAX_DIG = 2;
    /** The dark open water at the middle of a frigid lake, kept free of every dressing. */
    public static final int LAKE_OPEN_CENTRE_SPAN = 4;
    /** How far the water is grown outward from the open centre. */
    public static final int LAKE_GROWTH = 2;
    public static final int LAKE_MIN_FLOES = 2;
    public static final int LAKE_MAX_FLOES = 4;
    public static final int LAKE_FLOE_MIN_CELLS = 2;
    public static final int LAKE_FLOE_MAX_CELLS = 4;
    public static final int LAKE_MAX_FROST_MOTES = 8;

    public static final int EXPEDITION_LANTERNS = 2;
    public static final int EXPEDITION_CACHE_CHESTS = 1;
    public static final int EXPEDITION_MIN_BONE_DEBRIS = 2;
    public static final int EXPEDITION_MAX_BONE_DEBRIS = 4;
    public static final int EXPEDITION_MIN_WOOD_DEBRIS = 3;
    public static final int EXPEDITION_MAX_WOOD_DEBRIS = 6;

    /* Canonical envelope bounds. */
    private static final int WRITE_MIN_X = 1;
    private static final int WRITE_MAX_X = 30;
    private static final int WRITE_MIN_Z = 1;
    private static final int WRITE_MAX_Z = 14;
    private static final int SHELL_MIN_X = 0;
    private static final int SHELL_MAX_X = 31;
    private static final int SHELL_MIN_Z = 0;
    private static final int SHELL_MAX_Z = 15;
    private static final int VOLUME_MIN_X = WRITE_MIN_X + 1;
    private static final int VOLUME_MAX_X = WRITE_MAX_X - 1;
    private static final int VOLUME_MIN_Z = WRITE_MIN_Z + 1;
    private static final int VOLUME_MAX_Z = WRITE_MAX_Z - 1;
    private static final int OWNER_MAX_CANONICAL_X = 15;
    private static final int MOUTH_MIN_CANONICAL_X = VOLUME_MIN_X + CUSHION_DILATION;
    private static final int MOUTH_MAX_CANONICAL_X = OWNER_MAX_CANONICAL_X;
    private static final int MOUTH_MIN_CANONICAL_Z = VOLUME_MIN_Z + CUSHION_DILATION;
    private static final int MOUTH_MAX_CANONICAL_Z = VOLUME_MAX_Z - CUSHION_DILATION;
    private static final int CHUNK_SIDE = 16;
    private static final int QUART_SIDE = 4;

    /* Owner-local anchor scan window for mouth masks. */
    private static final int ANCHOR_MIN = 1;
    private static final int ANCHOR_MAX = 14;

    /*
     * Deterministic evaluation budgets. They never change which candidate wins inside the prefix they allow;
     * they only bound the worst case so a hostile or unusually rich probe window cannot make chunk generation
     * unbounded. Pass 2/3 may tune them once real profiles exist.
     */
    private static final int MAX_MOUTH_FOOTPRINTS = 24;
    private static final int MAX_CANDIDATES = 96;
    private static final int MAX_VOLUME_VALIDATIONS = 256;
    private static final int MAX_EXIT_SEARCHES = 8;
    private static final int MAX_ROUTE_NODES = 120_000;
    private static final int MAX_TERMINI_PER_CANDIDATE = 3;
    private static final int MAX_ROUTED_DROPS_PER_CANDIDATE = 3;

    /* Role-specific fold constants; each keeps one decision stream away from every other. */
    private static final long FOLD_OCCURRENCE = 0x4F43435552454E43L;
    private static final long FOLD_THEME = 0x5448454D45504943L;
    private static final long FOLD_VARIANT = 0x56415249414E5421L;
    private static final long FOLD_SPAN = 0x5350414E53495A45L;
    private static final long FOLD_LOBES = 0x4C4F42455343544EL;
    private static final long FOLD_LOBE_SHAPE = 0x4C4F42455348504CL;
    private static final long FOLD_SHAPE_RANK = 0x53484150455241EL;
    private static final long FOLD_HEIGHT = 0x4345494C494E4748L;
    private static final long FOLD_OFFSET = 0x4348414D4F464653L;
    private static final long FOLD_ICE_COLUMN = 0x49434543304C554DL;
    private static final long FOLD_ICICLE = 0x4943494349434C45L;
    private static final long FOLD_FOSSIL = 0x464F5353494C4245L;
    private static final long FOLD_LAKE = 0x4C414B4557415445L;
    private static final long FOLD_FLOE = 0x464C4F45494345DL;
    private static final long FOLD_MOTE = 0x46524F53544D4F54L;
    private static final long FOLD_EXPEDITION = 0x4558504544495449L;

    /* The four offset tables below are shared, immutable geometry: never mutated; contents are read-only by
     * convention. Java cannot express a deep-immutable array, so this note is the guarantee. */

    /** Never mutated; contents are read-only by convention. */
    private static final int[][] CARDINALS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    /** Never mutated; contents are read-only by convention. */
    private static final int[][] NEIGHBOURHOOD_8 = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };
    /**
     * Six cheap neighbour-chunk sample columns used to score how much of the envelope a direction can read.
     * Never mutated; contents are read-only by convention.
     */
    private static final int[][] ENVELOPE_FIT_SAMPLES = {
        {18, 4}, {18, 10}, {23, 4}, {23, 10}, {28, 7}, {21, 7}
    };

    private HiddenChamberPlan() {
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Public vocabulary                                                                                     */
    /* ---------------------------------------------------------------------------------------------------- */

    /** Which dressing language the chamber speaks. */
    public enum Theme {
        ICE_CATHEDRAL,
        FRIGID_LAKE,
        LOST_EXPEDITION
    }

    /** How large the chamber is allowed to grow. */
    public enum Variant {
        COMPACT,
        CATHEDRAL
    }

    /**
     * The material vocabulary an accepted plan speaks. The world layer owns the role-to-block mapping; nothing
     * in this class knows what any of these become. The attached rank is both the write order and the
     * overwrite precedence when two generators land on one coordinate: higher wins, and the mouth always wins.
     */
    public enum CellRole {
        MOUTH_COLLAPSE(80),
        /**
         * The partial snow LAYER a lowered mouth cube displaces, cleared so the cube's own top IS the floor
         * the victim walks on. Part of the OPENING, exactly like {@link #MOUTH_COLLAPSE}: never a seal seed.
         */
        MOUTH_CLEAR(81),
        SHAFT_CLEAR(30),
        CHAMBER_CLEAR(31),
        RECESS_CLEAR(32),
        CORRIDOR_CLEAR(33),
        EXIT_PORTAL_CLEAR(34),
        CUSHION_POWDER(70),
        CUSHION_BASE(20),
        SHELF_FIRM(22),
        CORRIDOR_FLOOR(23),
        WALL_SEAL(10),
        /**
         * The room's own floor, one cell under the carve, authored only where the terrain does not already
         * offer a firm one. Distinct from {@link #WALL_SEAL} even though it is the same material: a wall seal
         * always touches a carved interior cell, while a floor seal may end up under a lantern or a chest
         * that has replaced the carve above it, and conflating the two would make the wall-seal law a lie.
         */
        FLOOR_SEAL(11),
        COLUMN_ICE_PACKED(50),
        COLUMN_ICE_BLUE(51),
        ICICLE_ICE(52),
        FOSSIL_BONE(53),
        LAKE_WATER(40),
        LAKE_BED(21),
        SHORE_ICE(41),
        FLOE_ICE(42),
        LEDGE_FIRM(43),
        FROST_MOTE(54),
        LANTERN(55),
        CACHE_CHEST(56),
        DEBRIS_WOOD(57),
        DEBRIS_BONE(58);

        private final int rank;

        CellRole(int rank) {
            this.rank = rank;
        }

        /** Write order and overwrite precedence. Higher ranks are written later and win coordinate conflicts. */
        public int rank() {
            return rank;
        }

        /** True when this role carves space rather than placing material; gravity above it is a hazard. */
        public boolean isCarve() {
            return this == SHAFT_CLEAR || this == CHAMBER_CLEAR || this == RECESS_CLEAR
                    || this == CORRIDOR_CLEAR || this == EXIT_PORTAL_CLEAR || this == MOUTH_COLLAPSE
                    || this == MOUTH_CLEAR;
        }

        /**
         * True when the world layer renders this role as one of the four blocks {@code MagmaQuenchSweepFeature}
         * counts as ice: packed ice, blue ice, ice, and the snow BLOCK. Step 10's quench sweep runs AFTER this
         * feature and rewrites the whole shell of every magma block it finds -- a flooded magma turns the
         * shell's ice and water to obsidian, a dry one MELTS its face-adjacent ice to air -- and it neither
         * knows nor cares that the ice it is eating is an authored chamber wall. The planner's only defence is
         * to decline a chunk whose ice would be in reach, so it has to know which of its own roles are ice.
         *
         * <p>This is the one place the pure planner names a material property. It mirrors
         * {@code HiddenGlacialChamberFeature.roleStates()}; the plan-to-blocks gate asserts the two agree.
         */
        public boolean isIceFamily() {
            return this == CUSHION_BASE || this == SHELF_FIRM || this == CORRIDOR_FLOOR
                    || this == LEDGE_FIRM || this == WALL_SEAL || this == FLOOR_SEAL
                    || this == COLUMN_ICE_PACKED || this == COLUMN_ICE_BLUE
                    || this == LAKE_BED || this == SHORE_ICE || this == FLOE_ICE;
        }

        /**
         * True when this role carves the ENCLOSED interior of the encounter -- the shaft, the chamber, its
         * recess, and the corridor bore -- and therefore seals the natural cave air it touches.
         *
         * <p>This is deliberately a strict subset of {@link #isCarve()}. The carve roles left out,
         * {@link #MOUTH_COLLAPSE}, {@link #MOUTH_CLEAR} and {@link #EXIT_PORTAL_CLEAR}, are the encounter's two OPENINGS and must
         * stay open by product law: the air directly above a mouth cell is the upper cave the victim is
         * walking through when the floor gives way (sealing it caps every mouth with a visible packed-ice
         * patch and nothing can fall through), and the exit portal is the second opening that breaches back
         * into that same upper cave (sealing its cave-facing faces walls the chamber shut). Every other
         * carved cell is interior, so the seal law -- natural cave air adjacent to the interior is closed --
         * still stands exactly as written.
         */
        public boolean isSealedInterior() {
            return this == SHAFT_CLEAR || this == CHAMBER_CLEAR
                    || this == RECESS_CLEAR || this == CORRIDOR_CLEAR;
        }
    }

    /**
     * Owner chunk plus exactly one cardinal neighbour. Canonical space is the east-going strip
     * {@code x 0..31, z 0..15} with the owner at {@code x 0..15}; each direction rotates that strip onto the
     * real cardinal exactly as {@link SubterraneanTrapPlan.CavernDirection} does.
     */
    public enum EnvelopeDirection {
        EAST(1, 0),
        WEST(-1, 0),
        NORTH(0, -1),
        SOUTH(0, 1);

        private final int chunkDx;
        private final int chunkDz;

        EnvelopeDirection(int chunkDx, int chunkDz) {
            this.chunkDx = chunkDx;
            this.chunkDz = chunkDz;
        }

        public int chunkDx() {
            return chunkDx;
        }

        public int chunkDz() {
            return chunkDz;
        }

        public int canonicalX(int localX, int localZ) {
            return switch (this) {
                case EAST -> localX;
                case SOUTH -> localZ;
                case WEST -> 15 - localX;
                case NORTH -> 15 - localZ;
            };
        }

        public int canonicalZ(int localX, int localZ) {
            return switch (this) {
                case EAST -> localZ;
                case SOUTH -> 15 - localX;
                case WEST -> 15 - localZ;
                case NORTH -> localX;
            };
        }

        public int localX(int canonicalX, int canonicalZ) {
            return switch (this) {
                case EAST -> canonicalX;
                case SOUTH -> 15 - canonicalZ;
                case WEST -> 15 - canonicalX;
                case NORTH -> canonicalZ;
            };
        }

        public int localZ(int canonicalX, int canonicalZ) {
            return switch (this) {
                case EAST -> canonicalZ;
                case SOUTH -> canonicalX;
                case WEST -> 15 - canonicalZ;
                case NORTH -> 15 - canonicalX;
            };
        }

        /** Canonical {@code x 1..30, z 1..14}: the roughly thirty-by-fourteen authored write envelope. */
        public boolean containsWriteCell(int localX, int localZ) {
            int x = canonicalX(localX, localZ);
            int z = canonicalZ(localX, localZ);
            return x >= WRITE_MIN_X && x <= WRITE_MAX_X && z >= WRITE_MIN_Z && z <= WRITE_MAX_Z;
        }

        /** Canonical {@code x 0..31, z 0..15}: everything the planner is allowed to read or probe. */
        public boolean containsReadShellCell(int localX, int localZ) {
            int x = canonicalX(localX, localZ);
            int z = canonicalZ(localX, localZ);
            return x >= SHELL_MIN_X && x <= SHELL_MAX_X && z >= SHELL_MIN_Z && z <= SHELL_MAX_Z;
        }
    }

    /** One planned write in owner-local coordinates: the owner chunk origin is {@code (0, 0)}. */
    public record Cell(int x, int y, int z, CellRole role) {
        public Cell {
            Objects.requireNonNull(role, "role");
        }
    }

    /** One owner-local position; used for the mouth centroid, the landing, and the exit terminus. */
    public record Position(int x, int y, int z) {
    }

    /** One owner-local biome quart the authored volume overlaps. */
    public record Quart(int quartX, int quartY, int quartZ) {
    }

    /** Kinds the world layer must be able to tell apart for the planner to reason about safety. */
    public enum CellKind {
        AIR,
        SOLID_SAFE,
        /** Magma, ore, or any protected-tag solid the encounter must never cut into. */
        SOLID_UNSAFE,
        FLUID,
        BLOCK_ENTITY,
        BEDROCK,
        GRAVITY,
        UNREADABLE
    }

    /**
     * The walkable upper-cave floor surface of one column. {@code floorY} is the Y of the topmost floor
     * block, and {@code headroom} counts the clear blocks above it.
     *
     * <p>{@code topEighths} is how much of that top block's own cell it actually fills, in eighths, so the
     * column's REAL walking surface is at {@code floorY + topEighths/8}. A full block is {@code 8} and a
     * player stands at {@code floorY + 1}; a {@code minecraft:snow} layer of N is {@code N} and the player
     * stands {@code 7/8} of a block lower than that at N=1. The distinction is not cosmetic: the mouth writes
     * a FULL cube, so on a partial top it has to displace the SUPPORTING block instead of the layer's own
     * cell, and the flat-patch law has to compare these real surfaces rather than integer floor Y.
     */
    public record ColumnInfo(int floorY, boolean snowyTop, boolean roofed, int headroom, int topEighths) {
        public ColumnInfo {
            if (topEighths < 1 || topEighths > EIGHTHS_PER_BLOCK) {
                throw new IllegalArgumentException(
                        "a floor top fills one to eight eighths of its cell, not " + topEighths);
            }
        }

        /** A column whose floor top is a whole block: the ordinary stone, ice or snow-block cave floor. */
        public ColumnInfo(int floorY, boolean snowyTop, boolean roofed, int headroom) {
            this(floorY, snowyTop, roofed, headroom, EIGHTHS_PER_BLOCK);
        }

        /** The column's real walking surface, measured in eighths of a block from Y zero. */
        public int surfaceEighths() {
            return floorY * EIGHTHS_PER_BLOCK + topEighths;
        }

        /** True when the top block is a partial layer, so the mouth cube must be lowered under it. */
        public boolean partialTop() {
            return topEighths < EIGHTHS_PER_BLOCK;
        }

        /**
         * The Y a full-block collapse cube must occupy for its top face to land on the whole-block line just
         * under this column's real surface. Purely a function of {@link #surfaceEighths()}, which is why a
         * mouth patch level to within one block of real surface is always level to within one block of
         * collapse cells too.
         */
        public int collapseY() {
            return Math.floorDiv(surfaceEighths(), EIGHTHS_PER_BLOCK) - 1;
        }
    }

    /**
     * The one and only terrain access this planner has. All coordinates are owner-local. The world layer
     * implements it against a real chunk region; tests implement it over arrays.
     */
    public interface TerrainProbe {
        /** The upper-cave walkable floor at this column, or {@code null} when the column cannot be read. */
        ColumnInfo column(int localX, int localZ);

        /** The kind of one block. Anything the implementation cannot see must be {@link CellKind#UNREADABLE}. */
        CellKind cell(int localX, int y, int localZ);

        /**
         * Saved-biome check for one quart. Treated fail-closed: a {@code false} anywhere under the authored
         * volume rejects the plan, so an implementation that cannot answer must answer {@code false}.
         */
        boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ);
    }

    /** Why a chunk did not get an encounter, at the coarse grain the world layer records. */
    public enum Rejection {
        NO_ENTRANCE,
        UNSAFE_VOLUME,
        NO_EXIT
    }

    /** Fine-grained diagnostic for a rejection. Each detail belongs to exactly one {@link Rejection}. */
    public enum RejectionDetail {
        INVALID_INPUT(Rejection.NO_ENTRANCE),
        NO_MOUTH_ANCHOR(Rejection.NO_ENTRANCE),
        MOUTH_COLUMN_UNREADABLE(Rejection.NO_ENTRANCE),
        MOUTH_NOT_SNOWY(Rejection.NO_ENTRANCE),
        MOUTH_NOT_ROOFED(Rejection.NO_ENTRANCE),
        MOUTH_HEADROOM_TOO_LOW(Rejection.NO_ENTRANCE),
        MOUTH_FLOOR_SPREAD_TOO_WIDE(Rejection.NO_ENTRANCE),
        MOUTH_TOP_LAYER_TOO_THICK(Rejection.NO_ENTRANCE),
        MOUTH_FLOOR_OUT_OF_BAND(Rejection.NO_ENTRANCE),
        MOUTH_OUTSIDE_OWNER_CHUNK(Rejection.NO_ENTRANCE),
        MOUTH_OUTSIDE_ENVELOPE(Rejection.NO_ENTRANCE),
        DROP_BAND_UNREACHABLE(Rejection.UNSAFE_VOLUME),
        LANDING_BELOW_WORLD_FLOOR(Rejection.UNSAFE_VOLUME),
        CELL_BELOW_WORLD_FLOOR(Rejection.UNSAFE_VOLUME),
        CELL_OUTSIDE_ENVELOPE(Rejection.UNSAFE_VOLUME),
        BLOCK_ENTITY_IN_VOLUME(Rejection.UNSAFE_VOLUME),
        BEDROCK_IN_VOLUME(Rejection.UNSAFE_VOLUME),
        UNREADABLE_IN_VOLUME(Rejection.UNSAFE_VOLUME),
        FLUID_IN_VOLUME(Rejection.UNSAFE_VOLUME),
        UNSAFE_SOLID_IN_VOLUME(Rejection.UNSAFE_VOLUME),
        GRAVITY_ABOVE_CARVED_CELL(Rejection.UNSAFE_VOLUME),
        /**
         * An unsafe solid -- magma, as far as this law is concerned -- shares a face with the chamber's own
         * water or ice. Step 10's magma quench sweep would eat it. See {@link CellRole#isIceFamily()}.
         */
        MAGMA_ADJACENT_TO_ICE_OR_WATER(Rejection.UNSAFE_VOLUME),
        BIOME_QUART_NOT_GLACIAL(Rejection.UNSAFE_VOLUME),
        CHAMBER_SHAPE_INVALID(Rejection.UNSAFE_VOLUME),
        SHELF_UNAVAILABLE(Rejection.UNSAFE_VOLUME),
        RECESS_UNAVAILABLE(Rejection.UNSAFE_VOLUME),
        THEME_GEOMETRY_UNAVAILABLE(Rejection.UNSAFE_VOLUME),
        LAKE_OPEN_CENTRE_UNAVAILABLE(Rejection.UNSAFE_VOLUME),
        LAKE_TOO_SHALLOW_TO_DIG(Rejection.UNSAFE_VOLUME),
        LAKE_FLOES_UNAVAILABLE(Rejection.UNSAFE_VOLUME),
        LAKE_LEDGE_UNAVAILABLE(Rejection.UNSAFE_VOLUME),
        CATHEDRAL_DRESSING_UNAVAILABLE(Rejection.UNSAFE_VOLUME),
        EXPEDITION_FLOOR_TOO_SMALL(Rejection.UNSAFE_VOLUME),
        NO_TERMINUS_CANDIDATE(Rejection.NO_EXIT),
        ROUTE_NOT_FOUND(Rejection.NO_EXIT),
        ROUTE_TOO_FEW_BENDS(Rejection.NO_EXIT),
        ROUTE_BUDGET_EXHAUSTED(Rejection.NO_EXIT);

        private final Rejection rejection;

        RejectionDetail(Rejection rejection) {
            this.rejection = rejection;
        }

        public Rejection rejection() {
            return rejection;
        }
    }

    /**
     * Immutable accepted geometry. Writes are ordered by {@link CellRole#rank()} then by coordinate, so a plan
     * replays identically every time; every coordinate appears exactly once.
     */
    public record Plan(List<Cell> writes,
                       Theme theme,
                       Variant variant,
                       EnvelopeDirection direction,
                       List<Cell> mouthCells,
                       Position mouthCentroid,
                       Position landing,
                       Position exitTerminus,
                       List<Quart> biomeQuarts,
                       int mouthFloorY,
                       int landingY,
                       int drop,
                       int corridorBends) {
        public Plan {
            writes = List.copyOf(writes);
            mouthCells = List.copyOf(mouthCells);
            biomeQuarts = List.copyOf(biomeQuarts);
            Objects.requireNonNull(theme, "theme");
            Objects.requireNonNull(variant, "variant");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(mouthCentroid, "mouthCentroid");
            Objects.requireNonNull(landing, "landing");
            Objects.requireNonNull(exitTerminus, "exitTerminus");
            Set<Coordinate> seen = new java.util.HashSet<>();
            for (Cell cell : writes) {
                if (!seen.add(new Coordinate(cell.x(), cell.y(), cell.z()))) {
                    throw new IllegalArgumentException("plan writes must have unique coordinates: "
                            + cell.x() + "," + cell.y() + "," + cell.z() + " " + cell.role());
                }
            }
            if (mouthCells.isEmpty()) {
                throw new IllegalArgumentException("an accepted plan always has a mouth");
            }
        }

        /** Every write carrying one role, in write order. */
        public List<Cell> writesWithRole(CellRole role) {
            return writes.stream().filter(cell -> cell.role() == role).toList();
        }
    }

    /** Named all-or-nothing result: exactly one of {@code accepted} and {@code rejection} is present. */
    public record PlanResult(Plan accepted, Rejection rejection, RejectionDetail detail) {
        public PlanResult {
            if ((accepted == null) == (rejection == null)) {
                throw new IllegalArgumentException("result must contain exactly one of accepted or rejection");
            }
            if ((rejection == null) != (detail == null)) {
                throw new IllegalArgumentException("a rejection must carry exactly one detail");
            }
            if (detail != null && detail.rejection() != rejection) {
                throw new IllegalArgumentException("detail " + detail + " does not belong to " + rejection);
            }
        }

        public boolean isAccepted() {
            return accepted != null;
        }
    }

    /** One offset inside a mouth mask, relative to the mask anchor. */
    public record MaskOffset(int dx, int dz) {
    }

    /** One irregular collapse-mouth mask at one of its four rotations. */
    public record MouthMask(String name, int rotation, List<MaskOffset> offsets) {
        public MouthMask {
            Objects.requireNonNull(name, "name");
            offsets = List.copyOf(offsets);
            if (offsets.size() < MOUTH_MIN_CELLS || offsets.size() > MOUTH_MAX_CELLS) {
                throw new IllegalArgumentException("a mouth mask holds two to four cells");
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Public entry points                                                                                   */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * The single deterministic rarity for this encounter: one chunk in {@link #OCCURRENCE_MODULUS}. It is a
     * pure hash of world identity and chunk, it consults no terrain, and {@link #plan} never calls it. The
     * world layer calls it exactly once after a plan has been accepted, so the gate can never change which
     * chunk would have been buildable.
     */
    public static boolean occurrenceGate(long worldSeed, int chunkX, int chunkZ) {
        long hash = fold(chunkSeed(worldSeed, chunkX, chunkZ), FOLD_OCCURRENCE, chunkX, chunkZ);
        return Math.floorMod(hash, OCCURRENCE_MODULUS) == 0;
    }

    /** The immutable catalogue of irregular collapse-mouth masks, every shape at every rotation. */
    public static List<MouthMask> mouthMaskCatalog() {
        return MOUTH_MASKS;
    }

    /**
     * Plan one hidden glacial chamber for one chunk.
     *
     * <p>Fully deterministic: the same {@code (worldSeed, chunkX, chunkZ, probe, forcedThemeOrNull)} always
     * yields a byte-identical result, including write order. Rejection is attributed to the deepest stage the
     * best candidate reached: no eligible mouth at all gives {@link Rejection#NO_ENTRANCE}; mouths that all
     * died on volume or safety law give {@link Rejection#UNSAFE_VOLUME}; a candidate that cleared its volume
     * but could not be given a second opening gives {@link Rejection#NO_EXIT}.
     *
     * @param forcedThemeOrNull overrides the hashed theme pick; {@code null} lets the hash decide
     */
    public static PlanResult plan(long worldSeed, int chunkX, int chunkZ,
                                  TerrainProbe probe, Theme forcedThemeOrNull) {
        if (probe == null) {
            return rejected(RejectionDetail.INVALID_INPUT);
        }
        long chunkSeed = chunkSeed(worldSeed, chunkX, chunkZ);
        ProbeCache cache = new ProbeCache(probe);

        Failure best = new Failure(0, RejectionDetail.NO_MOUTH_ANCHOR);
        List<Footprint> footprints = new ArrayList<>();
        best = collectFootprints(cache, footprints, best);
        if (footprints.isEmpty()) {
            return rejected(best.detail());
        }

        List<Candidate> candidates = rankCandidates(chunkSeed, footprints, cache);
        if (candidates.isEmpty()) {
            return rejected(RejectionDetail.MOUTH_OUTSIDE_ENVELOPE);
        }

        Budget budget = new Budget();
        for (Candidate candidate : candidates) {
            int maxDrop = Math.min(DROP_MAX, candidate.footprint().collapseFloorY() - MIN_LANDING_Y);
            if (maxDrop < DROP_MIN) {
                best = deeper(best, new Failure(1, RejectionDetail.DROP_BAND_UNREACHABLE));
                continue;
            }
            int routedDrops = 0;
            for (int drop = maxDrop; drop >= DROP_MIN; drop--) {
                if (budget.volumeExhausted()) {
                    break;
                }
                Attempt attempt = attempt(chunkSeed, candidate, drop, cache, forcedThemeOrNull, budget);
                if (attempt.plan() != null) {
                    return new PlanResult(attempt.plan(), null, null);
                }
                best = deeper(best, attempt.failure());
                if (attempt.failure().stage() >= 2) {
                    routedDrops++;
                    if (routedDrops >= MAX_ROUTED_DROPS_PER_CANDIDATE) {
                        break;
                    }
                }
            }
            if (budget.volumeExhausted()) {
                break;
            }
        }
        return rejected(best.detail());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Stage one: eligible collapse mouths                                                                   */
    /* ---------------------------------------------------------------------------------------------------- */

    private static Failure collectFootprints(ProbeCache cache, List<Footprint> out, Failure best) {
        List<Footprint> all = new ArrayList<>();
        for (int anchorX = ANCHOR_MIN; anchorX <= ANCHOR_MAX; anchorX++) {
            for (int anchorZ = ANCHOR_MIN; anchorZ <= ANCHOR_MAX; anchorZ++) {
                for (MouthMask mask : MOUTH_MASKS) {
                    FootprintResult result = evaluateFootprint(cache, anchorX, anchorZ, mask);
                    if (result.footprint() != null) {
                        all.add(result.footprint());
                    } else {
                        best = deeper(best, new Failure(0, result.detail()));
                    }
                }
            }
        }
        all.sort(FOOTPRINT_ORDER);
        for (int index = 0; index < all.size() && index < MAX_MOUTH_FOOTPRINTS; index++) {
            out.add(all.get(index));
        }
        return best;
    }

    /**
     * One candidate mouth patch.
     *
     * <p>Flatness is judged on REAL surface height in eighths, not on integer floor Y. A snow-carpeted cave
     * floor routinely puts a one-layer column ({@code floorY 31}, surface {@code 31.125}) beside a full-block
     * one ({@code floorY 31}, surface {@code 32.0}): identical floor Y, seven eighths of a block apart, and a
     * step a player cannot walk up. The old integer test called that patch perfectly flat.
     *
     * <p>Each column also carries the Y its collapse cube must occupy. On a full-block top that is the floor
     * block itself; on a partial layer it is the block UNDER the layer, so the cube's top face lands level
     * with the surrounding snow instead of standing a raised pad above it. Because {@link
     * ColumnInfo#collapseY()} is a pure function of the surface height, a patch flat to within
     * {@link #MOUTH_MAX_FLOOR_SPREAD} blocks of surface is automatically flat to within one collapse cell.
     */
    private static FootprintResult evaluateFootprint(ProbeCache cache, int anchorX, int anchorZ, MouthMask mask) {
        List<int[]> columns = new ArrayList<>();
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int minCollapse = Integer.MAX_VALUE;
        List<Integer> floors = new ArrayList<>();
        List<Integer> collapses = new ArrayList<>();
        for (MaskOffset offset : mask.offsets()) {
            int lx = anchorX + offset.dx();
            int lz = anchorZ + offset.dz();
            if (lx < 0 || lx >= CHUNK_SIDE || lz < 0 || lz >= CHUNK_SIDE) {
                return new FootprintResult(null, RejectionDetail.MOUTH_OUTSIDE_OWNER_CHUNK);
            }
            ColumnInfo info = cache.column(lx, lz);
            if (info == null) {
                return new FootprintResult(null, RejectionDetail.MOUTH_COLUMN_UNREADABLE);
            }
            if (!info.snowyTop()) {
                return new FootprintResult(null, RejectionDetail.MOUTH_NOT_SNOWY);
            }
            if (!info.roofed()) {
                return new FootprintResult(null, RejectionDetail.MOUTH_NOT_ROOFED);
            }
            if (info.headroom() < MOUTH_MIN_HEADROOM) {
                return new FootprintResult(null, RejectionDetail.MOUTH_HEADROOM_TOO_LOW);
            }
            if (info.floorY() < MOUTH_FLOOR_MIN_Y || info.floorY() > MOUTH_FLOOR_MAX_Y) {
                return new FootprintResult(null, RejectionDetail.MOUTH_FLOOR_OUT_OF_BAND);
            }
            if (info.partialTop() && info.topEighths() > MOUTH_MAX_TOP_LAYER_EIGHTHS) {
                return new FootprintResult(null, RejectionDetail.MOUTH_TOP_LAYER_TOO_THICK);
            }
            columns.add(new int[] {lx, lz});
            floors.add(info.floorY());
            collapses.add(info.collapseY());
            minSurface = Math.min(minSurface, info.surfaceEighths());
            maxSurface = Math.max(maxSurface, info.surfaceEighths());
            minCollapse = Math.min(minCollapse, info.collapseY());
        }
        if (maxSurface - minSurface > MOUTH_MAX_FLOOR_SPREAD * EIGHTHS_PER_BLOCK) {
            return new FootprintResult(null, RejectionDetail.MOUTH_FLOOR_SPREAD_TOO_WIDE);
        }
        int spread = Math.floorDiv(maxSurface, EIGHTHS_PER_BLOCK)
                - Math.floorDiv(minSurface, EIGHTHS_PER_BLOCK);
        return new FootprintResult(
                new Footprint(anchorX, anchorZ, mask, List.copyOf(columns), List.copyOf(floors),
                        List.copyOf(collapses), minCollapse, spread),
                null);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Stage one and a half: candidate ranking                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    private static List<Candidate> rankCandidates(long chunkSeed, List<Footprint> footprints, ProbeCache cache) {
        List<Candidate> candidates = new ArrayList<>();
        for (Footprint footprint : footprints) {
            for (EnvelopeDirection direction : EnvelopeDirection.values()) {
                if (!mouthFitsEnvelope(footprint, direction)) {
                    continue;
                }
                int achievableDrop = Math.min(DROP_MAX, footprint.collapseFloorY() - MIN_LANDING_Y);
                if (achievableDrop < DROP_MIN) {
                    continue;
                }
                int score = achievableDrop * 100
                        + (footprint.spread() == 0 ? 40 : 0)
                        + envelopeFitScore(cache, direction);
                candidates.add(new Candidate(footprint, direction, score));
            }
        }
        candidates.sort(CANDIDATE_ORDER);
        return candidates.size() <= MAX_CANDIDATES ? candidates : candidates.subList(0, MAX_CANDIDATES);
    }

    private static boolean mouthFitsEnvelope(Footprint footprint, EnvelopeDirection direction) {
        for (int[] column : footprint.columns()) {
            int cx = direction.canonicalX(column[0], column[1]);
            int cz = direction.canonicalZ(column[0], column[1]);
            if (cx < MOUTH_MIN_CANONICAL_X || cx > MOUTH_MAX_CANONICAL_X
                    || cz < MOUTH_MIN_CANONICAL_Z || cz > MOUTH_MAX_CANONICAL_Z) {
                return false;
            }
        }
        return true;
    }

    private static int envelopeFitScore(ProbeCache cache, EnvelopeDirection direction) {
        int score = 0;
        for (int[] sample : ENVELOPE_FIT_SAMPLES) {
            int lx = direction.localX(sample[0], sample[1]);
            int lz = direction.localZ(sample[0], sample[1]);
            if (cache.column(lx, lz) != null) {
                score += 4;
            }
        }
        return score;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Stages two and three: volume, exit, dressing                                                          */
    /* ---------------------------------------------------------------------------------------------------- */

    private static Attempt attempt(long chunkSeed, Candidate candidate, int drop, ProbeCache cache,
                                   Theme forcedThemeOrNull, Budget budget) {
        EnvelopeDirection direction = candidate.direction();
        Footprint footprint = candidate.footprint();
        int mouthFloorY = footprint.collapseFloorY();
        int landingY = mouthFloorY - drop;
        if (landingY < MIN_LANDING_Y) {
            return Attempt.failed(1, RejectionDetail.LANDING_BELOW_WORLD_FLOOR);
        }

        Theme theme = forcedThemeOrNull != null
                ? forcedThemeOrNull
                : Theme.values()[Math.floorMod(fold(chunkSeed, FOLD_THEME, mouthFloorY, landingY),
                        Theme.values().length)];

        Set<Column> shaft = new LinkedHashSet<>();
        for (int[] column : footprint.columns()) {
            shaft.add(new Column(direction.canonicalX(column[0], column[1]),
                    direction.canonicalZ(column[0], column[1])));
        }
        Set<Column> cushion = dilate(shaft, CUSHION_DILATION);

        Map<Coordinate, CellRole> cells = new LinkedHashMap<>();
        for (int index = 0; index < footprint.columns().size(); index++) {
            int[] column = footprint.columns().get(index);
            int columnFloorY = footprint.floors().get(index);
            int collapseY = footprint.collapses().get(index);
            int cx = direction.canonicalX(column[0], column[1]);
            int cz = direction.canonicalZ(column[0], column[1]);
            place(cells, cx, collapseY, cz, CellRole.MOUTH_COLLAPSE);
            /*
             * A partial snow layer on top means the cube went in one cell LOWER, so the layer's own cell has
             * to be cleared: leaving it there would put a walkable layer over the false floor and the victim
             * would stroll across the trap. On a whole-block top the two are the same cell and nothing is
             * cleared.
             */
            if (columnFloorY > collapseY) {
                place(cells, cx, columnFloorY, cz, CellRole.MOUTH_CLEAR);
            }
            for (int y = collapseY - 1; y >= landingY + CUSHION_DEPTH; y--) {
                place(cells, cx, y, cz, CellRole.SHAFT_CLEAR);
            }
        }
        for (Column column : cushion) {
            place(cells, column.x(), landingY - 1, column.z(), CellRole.CUSHION_BASE);
            for (int depth = 0; depth < CUSHION_DEPTH; depth++) {
                place(cells, column.x(), landingY + depth, column.z(), CellRole.CUSHION_POWDER);
            }
        }

        Variant variant = pickVariant(chunkSeed, theme, mouthFloorY, landingY);
        Chamber chamber = buildChamber(chunkSeed, theme, variant, shaft, cushion, mouthFloorY, landingY);
        if (chamber == null) {
            return Attempt.failed(1, RejectionDetail.CHAMBER_SHAPE_INVALID);
        }
        for (Column column : chamber.columns()) {
            int height = chamber.height(column);
            for (int y = landingY; y < landingY + height; y++) {
                place(cells, column.x(), y, column.z(), CellRole.CHAMBER_CLEAR);
            }
        }
        authorChamberFloor(cells, cache, direction, chamber, landingY);

        List<Column> shelf = findShelf(chamber, cushion, shaft);
        if (shelf == null) {
            return Attempt.failed(1, RejectionDetail.SHELF_UNAVAILABLE);
        }
        for (Column column : shelf) {
            place(cells, column.x(), landingY - 1, column.z(), CellRole.SHELF_FIRM);
        }

        budget.countVolumeValidation();
        RejectionDetail volumeFailure = validate(cells, cache, direction);
        if (volumeFailure != null) {
            return Attempt.failed(1, volumeFailure);
        }

        Position mouthCentroid = mouthCentroid(footprint, mouthFloorY);
        if (budget.exitExhausted()) {
            return Attempt.failed(2, RejectionDetail.ROUTE_BUDGET_EXHAUSTED);
        }
        RouteResult routeResult = routeExit(cache, direction, chamber, shaft, cushion,
                landingY, mouthFloorY, mouthCentroid, footprint, budget);
        if (routeResult.route() == null) {
            return Attempt.failed(2, routeResult.detail());
        }
        Route route = routeResult.route();
        for (int index = 0; index < route.stations().size(); index++) {
            Station station = route.stations().get(index);
            boolean terminal = index == route.stations().size() - 1;
            for (Column column : station.columns()) {
                if (cache.cell(direction, column.x(), station.y(), column.z()) == CellKind.AIR) {
                    place(cells, column.x(), station.y(), column.z(), CellRole.CORRIDOR_FLOOR);
                }
                for (int level = 1; level <= CORRIDOR_CLEAR_HEIGHT; level++) {
                    place(cells, column.x(), station.y() + level, column.z(),
                            terminal ? CellRole.EXIT_PORTAL_CLEAR : CellRole.CORRIDOR_CLEAR);
                }
            }
        }

        /*
         * The seal seed set is taken HERE, before dressing, and it is the set addWallSeals works from. Every
         * dressing role outranks the carve it lands on, so reading the seeds off the finished map lost one
         * column per icicle cluster, ice column and mote -- and the cell above each of them, which the seal
         * pass would have closed, stayed open to the natural cave. An icicle hung under such a hole is a
         * 26.2 Fallable attached to air.
         */
        Set<Coordinate> carvedFootprint = new LinkedHashSet<>();
        for (Map.Entry<Coordinate, CellRole> entry : cells.entrySet()) {
            if (entry.getValue().isSealedInterior()) {
                carvedFootprint.add(entry.getKey());
            }
        }

        RejectionDetail dressingFailure = dress(chunkSeed, theme, cells, chamber, shaft, cushion, shelf,
                route, landingY, cache, direction);
        if (dressingFailure != null) {
            return Attempt.failed(3, dressingFailure);
        }

        addWallSeals(cells, cache, direction, carvedFootprint);

        budget.countVolumeValidation();
        RejectionDetail finalFailure = validate(cells, cache, direction);
        if (finalFailure != null) {
            return Attempt.failed(3, finalFailure);
        }
        RejectionDetail quenchFailure = quenchScarRisk(cells, cache, direction);
        if (quenchFailure != null) {
            return Attempt.failed(3, quenchFailure);
        }

        List<Cell> writes = toWriteList(cells, direction);
        List<Cell> mouthCells = writes.stream().filter(cell -> cell.role() == CellRole.MOUTH_COLLAPSE).toList();
        Position landing = new Position(
                direction.localX(centroidX(cushion), centroidZ(cushion)),
                landingY,
                direction.localZ(centroidX(cushion), centroidZ(cushion)));
        Station terminal = route.stations().get(route.stations().size() - 1);
        Position terminus = new Position(
                direction.localX(routeResult.terminus().x(), routeResult.terminus().z()),
                terminal.y(),
                direction.localZ(routeResult.terminus().x(), routeResult.terminus().z()));
        Plan plan = new Plan(writes, theme, variant, direction, mouthCells, mouthCentroid, landing, terminus,
                biomeQuarts(writes), mouthFloorY, landingY, drop, route.bends());
        return Attempt.accepted(plan);
    }

    private static Position mouthCentroid(Footprint footprint, int mouthFloorY) {
        int sumX = 0;
        int sumZ = 0;
        for (int[] column : footprint.columns()) {
            sumX += column[0];
            sumZ += column[1];
        }
        int count = footprint.columns().size();
        return new Position(roundedMean(sumX, count), mouthFloorY, roundedMean(sumZ, count));
    }

    /**
     * Only {@link Theme#LOST_EXPEDITION} takes its variant from the hash. A cathedral prefers the tall lobe by
     * name, and a frigid lake needs the wider footprint by law: a four-by-four of dark open water, a dry ledge
     * ring around it, and at least two floes do not fit inside a compact silhouette.
     */
    private static Variant pickVariant(long chunkSeed, Theme theme, int mouthFloorY, int landingY) {
        boolean cathedral = theme != Theme.LOST_EXPEDITION
                || Math.floorMod(fold(chunkSeed, FOLD_VARIANT, landingY, theme.ordinal()), 2) == 0;
        if (!cathedral) {
            return Variant.COMPACT;
        }
        int usableX = VOLUME_MAX_X - VOLUME_MIN_X + 1 - CHAMBER_CORRIDOR_RESERVE_X;
        int usableZ = VOLUME_MAX_Z - VOLUME_MIN_Z + 1 - CHAMBER_CORRIDOR_RESERVE_Z;
        boolean tallEnough = ceilingCap(mouthFloorY, landingY)
                >= CATHEDRAL_HEIGHT_MIN + CHAMBER_MIN_DISTINCT_HEIGHTS - 1;
        return usableX >= CATHEDRAL_SPAN_X_MIN && usableZ >= CATHEDRAL_SPAN_Z_MIN && tallEnough
                ? Variant.CATHEDRAL
                : Variant.COMPACT;
    }

    /**
     * The tallest interior clear height a chamber may take. The roof always keeps
     * {@link #CHAMBER_ROOF_CLEARANCE} blocks of rock under the mouth floor, so the chamber can never eat the
     * upper cave it is hiding beneath.
     */
    private static int ceilingCap(int mouthFloorY, int landingY) {
        return mouthFloorY - CHAMBER_ROOF_CLEARANCE - landingY + 1;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Chamber                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    private static Chamber buildChamber(long chunkSeed, Theme theme, Variant variant, Set<Column> shaft,
                                        Set<Column> cushion, int mouthFloorY, int landingY) {
        int spanXMin = variant == Variant.CATHEDRAL ? CATHEDRAL_SPAN_X_MIN : COMPACT_SPAN_X_MIN;
        int spanXMax = variant == Variant.CATHEDRAL ? CATHEDRAL_SPAN_X_MAX : COMPACT_SPAN_X_MAX;
        int spanZMin = variant == Variant.CATHEDRAL ? CATHEDRAL_SPAN_Z_MIN : COMPACT_SPAN_Z_MIN;
        int spanZMax = variant == Variant.CATHEDRAL ? CATHEDRAL_SPAN_Z_MAX : COMPACT_SPAN_Z_MAX;
        spanXMax = Math.min(spanXMax, VOLUME_MAX_X - VOLUME_MIN_X + 1 - CHAMBER_CORRIDOR_RESERVE_X);
        spanZMax = Math.min(spanZMax, VOLUME_MAX_Z - VOLUME_MIN_Z + 1 - CHAMBER_CORRIDOR_RESERVE_Z);
        if (spanXMax < spanXMin || spanZMax < spanZMin) {
            return null;
        }
        /* A frigid lake needs a dry boundary ring around open water, so it always takes the wider half. */
        if (theme == Theme.FRIGID_LAKE) {
            spanXMin = Math.max(spanXMin, spanXMin + (spanXMax - spanXMin) / 2);
            spanZMin = Math.max(spanZMin, spanZMin + (spanZMax - spanZMin) / 2);
        }
        int spanX = spanXMin + Math.floorMod(fold(chunkSeed, FOLD_SPAN, landingY, 1), spanXMax - spanXMin + 1);
        int spanZ = spanZMin + Math.floorMod(fold(chunkSeed, FOLD_SPAN, landingY, 2), spanZMax - spanZMin + 1);

        int centreX = centroidX(shaft);
        int centreZ = centroidZ(shaft);
        int offsetX = Math.floorMod(fold(chunkSeed, FOLD_OFFSET, centreX, 1), 5) - 2;
        int offsetZ = Math.floorMod(fold(chunkSeed, FOLD_OFFSET, centreZ, 2), 5) - 2;
        int minX = clamp(centreX + offsetX - spanX / 2, VOLUME_MIN_X, VOLUME_MAX_X - spanX + 1);
        int minZ = clamp(centreZ + offsetZ - spanZ / 2, VOLUME_MIN_Z, VOLUME_MAX_Z - spanZ + 1);
        int maxX = minX + spanX - 1;
        int maxZ = minZ + spanZ - 1;
        for (Column column : cushion) {
            minX = Math.min(minX, column.x());
            maxX = Math.max(maxX, column.x());
            minZ = Math.min(minZ, column.z());
            maxZ = Math.max(maxZ, column.z());
        }
        if (minX < VOLUME_MIN_X || maxX > VOLUME_MAX_X || minZ < VOLUME_MIN_Z || maxZ > VOLUME_MAX_Z) {
            return null;
        }
        if (maxX - minX + 1 > spanXMax || maxZ - minZ + 1 > spanZMax) {
            return null;
        }
        spanX = maxX - minX + 1;
        spanZ = maxZ - minZ + 1;

        Set<Column> columns = lobeUnion(chunkSeed, minX, minZ, maxX, maxZ, landingY);
        columns.addAll(cushion);
        columns = repairFill(chunkSeed, columns, cushion, minX, minZ, maxX, maxZ);
        if (columns.size() < CUSHION_DEPTH) {
            return null;
        }
        if (isFullRectangle(columns, minX, minZ, maxX, maxZ)) {
            return null;
        }

        Map<Column, Integer> heights =
                ceilingHeights(chunkSeed, columns, variant, minX, minZ, maxX, maxZ, mouthFloorY, landingY);
        if (heights == null || containsSquareRoom(columns, heights)) {
            return null;
        }
        int fillPercent = columns.size() * 100 / (spanX * spanZ);
        if (fillPercent < CHAMBER_FILL_MIN_PERCENT || fillPercent > CHAMBER_FILL_MAX_PERCENT) {
            return null;
        }
        return new Chamber(orderedColumns(columns), heights, minX, minZ, maxX, maxZ, variant);
    }

    private static Set<Column> lobeUnion(long chunkSeed, int minX, int minZ, int maxX, int maxZ, int landingY) {
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int lobes = CHAMBER_MIN_LOBES + Math.floorMod(fold(chunkSeed, FOLD_LOBES, landingY, spanX),
                CHAMBER_MAX_LOBES - CHAMBER_MIN_LOBES + 1);

        int radiusAx = Math.max(2, spanX * 32 / 100);
        int radiusAz = Math.max(2, spanZ * 34 / 100);
        int radiusBx = Math.max(2, spanX * 32 / 100);
        int radiusBz = Math.max(2, spanZ * 34 / 100);

        Set<Column> columns = new LinkedHashSet<>();
        addEllipse(columns, minX + radiusAx, maxZ - radiusAz, radiusAx, radiusAz, minX, minZ, maxX, maxZ);
        addEllipse(columns, maxX - radiusBx, minZ + radiusBz, radiusBx, radiusBz, minX, minZ, maxX, maxZ);
        boolean needsBridge = !isConnected(columns);
        if (lobes >= 3 || needsBridge) {
            int centreRadiusX = Math.max(2, spanX * 26 / 100);
            int centreRadiusZ = Math.max(2, spanZ * 28 / 100);
            addEllipse(columns, minX + spanX / 2, minZ + spanZ / 2, centreRadiusX, centreRadiusZ,
                    minX, minZ, maxX, maxZ);
        }
        if (lobes >= 4) {
            int extraRadiusX = Math.max(2, spanX * 22 / 100);
            int extraRadiusZ = Math.max(2, spanZ * 24 / 100);
            int shiftX = Math.floorMod(fold(chunkSeed, FOLD_LOBE_SHAPE, spanX, 1), Math.max(1, spanX / 3));
            int shiftZ = Math.floorMod(fold(chunkSeed, FOLD_LOBE_SHAPE, spanZ, 2), Math.max(1, spanZ / 3));
            addEllipse(columns, minX + extraRadiusX + shiftX, maxZ - extraRadiusZ - shiftZ,
                    extraRadiusX, extraRadiusZ, minX, minZ, maxX, maxZ);
        }
        return columns;
    }

    private static void addEllipse(Set<Column> columns, int centreX, int centreZ, int radiusX, int radiusZ,
                                   int minX, int minZ, int maxX, int maxZ) {
        for (int x = Math.max(minX, centreX - radiusX); x <= Math.min(maxX, centreX + radiusX); x++) {
            for (int z = Math.max(minZ, centreZ - radiusZ); z <= Math.min(maxZ, centreZ + radiusZ); z++) {
                int dx = x - centreX;
                int dz = z - centreZ;
                long left = (long) dx * dx * radiusZ * radiusZ + (long) dz * dz * radiusX * radiusX;
                if (left <= (long) radiusX * radiusX * radiusZ * radiusZ) {
                    columns.add(new Column(x, z));
                }
            }
        }
    }

    private static Set<Column> repairFill(long chunkSeed, Set<Column> columns, Set<Column> pinned,
                                          int minX, int minZ, int maxX, int maxZ) {
        int boxArea = (maxX - minX + 1) * (maxZ - minZ + 1);
        Set<Column> working = new LinkedHashSet<>(columns);
        final Set<Column> asBuilt = Set.copyOf(columns);

        List<Column> removalOrder = new ArrayList<>(working);
        removalOrder.sort(Comparator
                .comparingInt((Column column) -> neighbourCount(asBuilt, column))
                .thenComparingInt(column -> shapeRank(chunkSeed, column))
                .thenComparingInt(Column::x)
                .thenComparingInt(Column::z));
        for (Column column : removalOrder) {
            if (working.size() * 100 <= CHAMBER_FILL_MAX_PERCENT * boxArea) {
                break;
            }
            if (pinned.contains(column) || touchesBoxEdge(column, minX, minZ, maxX, maxZ)) {
                continue;
            }
            Set<Column> trial = new LinkedHashSet<>(working);
            trial.remove(column);
            if (isConnected(trial)) {
                working = trial;
            }
        }

        List<Column> additionOrder = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Column column = new Column(x, z);
                if (!working.contains(column)) {
                    additionOrder.add(column);
                }
            }
        }
        final Set<Column> eroded = Set.copyOf(working);
        additionOrder.sort(Comparator
                .comparingInt((Column column) -> -neighbourCount(eroded, column))
                .thenComparingInt(column -> shapeRank(chunkSeed, column))
                .thenComparingInt(Column::x)
                .thenComparingInt(Column::z));
        for (Column column : additionOrder) {
            if (working.size() * 100 >= CHAMBER_FILL_MIN_PERCENT * boxArea) {
                break;
            }
            if (neighbourCount(working, column) == 0) {
                continue;
            }
            working.add(column);
        }
        return working;
    }

    private static Map<Column, Integer> ceilingHeights(long chunkSeed, Set<Column> columns, Variant variant,
                                                       int minX, int minZ, int maxX, int maxZ,
                                                       int mouthFloorY, int landingY) {
        int heightMin = variant == Variant.CATHEDRAL ? CATHEDRAL_HEIGHT_MIN : COMPACT_HEIGHT_MIN;
        int heightMax = variant == Variant.CATHEDRAL ? CATHEDRAL_HEIGHT_MAX : COMPACT_HEIGHT_MAX;
        heightMax = Math.min(heightMax, ceilingCap(mouthFloorY, landingY));
        if (heightMax - heightMin < CHAMBER_MIN_DISTINCT_HEIGHTS - 1) {
            return null;
        }
        int centreX = (minX + maxX) / 2;
        int centreZ = (minZ + maxZ) / 2;
        int maxRing = Math.max(1, Math.max(maxX - minX, maxZ - minZ) / 2);
        Map<Column, Integer> heights = new LinkedHashMap<>();
        for (Column column : orderedColumns(columns)) {
            int ring = Math.max(Math.abs(column.x() - centreX), Math.abs(column.z() - centreZ));
            int ringTier = Math.min(3, ring * 4 / (maxRing + 1));
            int jitter = Math.floorMod(fold(chunkSeed, FOLD_HEIGHT, column.x(), column.z()), 3) - 1;
            int tier = clamp(3 - ringTier + jitter, 0, 3);
            heights.put(column, heightMin + tier * (heightMax - heightMin) / 3);
        }
        Set<Integer> distinct = new TreeSet<>(heights.values());
        if (distinct.size() < CHAMBER_MIN_DISTINCT_HEIGHTS) {
            int index = 0;
            for (Column column : orderedColumns(columns)) {
                int tier = index % 4;
                heights.put(column, heightMin + tier * (heightMax - heightMin) / 3);
                index++;
            }
            if (new TreeSet<>(heights.values()).size() < CHAMBER_MIN_DISTINCT_HEIGHTS) {
                return null;
            }
        }
        return heights;
    }

    private static boolean containsSquareRoom(Set<Column> columns, Map<Column, Integer> heights) {
        for (Column column : columns) {
            boolean uniformBlock = true;
            int height = heights.get(column);
            for (int dx = 0; dx < SQUARE_ROOM_SPAN && uniformBlock; dx++) {
                for (int dz = 0; dz < SQUARE_ROOM_SPAN && uniformBlock; dz++) {
                    Column probe = new Column(column.x() + dx, column.z() + dz);
                    Integer probeHeight = heights.get(probe);
                    if (probeHeight == null || probeHeight != height) {
                        uniformBlock = false;
                    }
                }
            }
            if (!uniformBlock) {
                continue;
            }
            boolean straightWalls = true;
            for (int offset = -1; offset <= SQUARE_ROOM_SPAN && straightWalls; offset++) {
                if (columns.contains(new Column(column.x() + offset, column.z() - 1))
                        || columns.contains(new Column(column.x() + offset, column.z() + SQUARE_ROOM_SPAN))
                        || columns.contains(new Column(column.x() - 1, column.z() + offset))
                        || columns.contains(new Column(column.x() + SQUARE_ROOM_SPAN, column.z() + offset))) {
                    straightWalls = false;
                }
            }
            if (straightWalls) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFullRectangle(Set<Column> columns, int minX, int minZ, int maxX, int maxZ) {
        return columns.size() == (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    private static List<Column> findShelf(Chamber chamber, Set<Column> cushion, Set<Column> shaft) {
        int centreX = centroidX(cushion);
        int centreZ = centroidZ(cushion);
        List<List<Column>> options = new ArrayList<>();
        for (Column column : chamber.columns()) {
            List<Column> block = new ArrayList<>();
            boolean usable = true;
            boolean touchesCushion = false;
            for (int dx = 0; dx < SHELF_SPAN && usable; dx++) {
                for (int dz = 0; dz < SHELF_SPAN && usable; dz++) {
                    Column member = new Column(column.x() + dx, column.z() + dz);
                    if (!chamber.columns().contains(member) || cushion.contains(member)
                            || shaft.contains(member)
                            || chamber.height(member) < SHELF_MIN_HEADROOM + 1) {
                        usable = false;
                        break;
                    }
                    block.add(member);
                    for (int[] offset : NEIGHBOURHOOD_8) {
                        if (cushion.contains(new Column(member.x() + offset[0], member.z() + offset[1]))) {
                            touchesCushion = true;
                        }
                    }
                }
            }
            if (usable && touchesCushion) {
                options.add(List.copyOf(block));
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        options.sort(Comparator
                .comparingInt((List<Column> block) -> chebyshev(block.get(0), centreX, centreZ))
                .thenComparingInt(block -> block.get(0).x())
                .thenComparingInt(block -> block.get(0).z()));
        return options.get(0);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Exit corridor                                                                                         */
    /* ---------------------------------------------------------------------------------------------------- */

    private static RouteResult routeExit(ProbeCache cache, EnvelopeDirection direction, Chamber chamber,
                                         Set<Column> shaft, Set<Column> cushion, int landingY, int mouthFloorY,
                                         Position mouthCentroid, Footprint footprint, Budget budget) {
        int centroidCanonicalX = direction.canonicalX(mouthCentroid.x(), mouthCentroid.z());
        int centroidCanonicalZ = direction.canonicalZ(mouthCentroid.x(), mouthCentroid.z());
        Set<Column> mouthColumns = new LinkedHashSet<>(shaft);

        List<Terminus> termini = new ArrayList<>();
        for (int x = VOLUME_MIN_X; x <= VOLUME_MAX_X; x++) {
            for (int z = VOLUME_MIN_Z; z <= VOLUME_MAX_Z; z++) {
                Column column = new Column(x, z);
                if (mouthColumns.contains(column)) {
                    continue;
                }
                int distance = Math.max(Math.abs(x - centroidCanonicalX), Math.abs(z - centroidCanonicalZ));
                if (distance < EXIT_DISTANCE_MIN || distance > EXIT_DISTANCE_MAX) {
                    continue;
                }
                ColumnInfo info = cache.column(direction.localX(x, z), direction.localZ(x, z));
                if (info == null || !info.roofed() || info.headroom() < MOUTH_MIN_HEADROOM) {
                    continue;
                }
                if (Math.abs(info.floorY() - mouthFloorY) > EXIT_FLOOR_TOLERANCE) {
                    continue;
                }
                if (info.floorY() < landingY - 1) {
                    continue;
                }
                termini.add(new Terminus(column, info.floorY(), distance));
            }
        }
        if (termini.isEmpty()) {
            return new RouteResult(null, null, RejectionDetail.NO_TERMINUS_CANDIDATE);
        }
        termini.sort(Comparator
                .comparingInt(Terminus::floorY)
                .thenComparing(Comparator.comparingInt(Terminus::distance).reversed())
                .thenComparingInt(terminus -> terminus.column().x())
                .thenComparingInt(terminus -> terminus.column().z()));

        RejectionDetail detail = RejectionDetail.ROUTE_NOT_FOUND;
        int attempts = 0;
        for (Terminus terminus : termini) {
            if (attempts >= MAX_TERMINI_PER_CANDIDATE) {
                break;
            }
            if (budget.exitExhausted()) {
                return new RouteResult(null, null, RejectionDetail.ROUTE_BUDGET_EXHAUSTED);
            }
            attempts++;
            budget.countExitSearch();
            Field field = buildField(cache, direction, chamber, shaft, landingY, terminus.floorY());
            Route route = search(field, chamber, terminus, landingY);
            if (route == null) {
                continue;
            }
            if (route.bends() < CORRIDOR_MIN_BENDS) {
                detail = RejectionDetail.ROUTE_TOO_FEW_BENDS;
                continue;
            }
            return new RouteResult(route, terminus.column(), null);
        }
        return new RouteResult(null, null, detail);
    }

    /**
     * Pre-reads every column in the volume inset across the corridor's Y range once, so the route search reads
     * arrays rather than the probe. A column is blocked at a Y when the real block is hostile, when the chamber
     * still owns that height, or when the column sits within {@link #SHAFT_CLEARANCE} of the shaft.
     */
    private static Field buildField(ProbeCache cache, EnvelopeDirection direction, Chamber chamber,
                                    Set<Column> shaft, int landingY, int terminusFloorY) {
        int baseY = landingY - 1;
        int topY = terminusFloorY + CORRIDOR_CLEAR_HEIGHT + 1;
        int width = VOLUME_MAX_X - VOLUME_MIN_X + 1;
        int depth = VOLUME_MAX_Z - VOLUME_MIN_Z + 1;
        int levels = topY - baseY + 1;
        boolean[][] blocked = new boolean[width * depth][levels];
        boolean[][] gravity = new boolean[width * depth][levels];
        Set<Column> forbidden = dilate(shaft, SHAFT_CLEARANCE);
        for (int x = VOLUME_MIN_X; x <= VOLUME_MAX_X; x++) {
            for (int z = VOLUME_MIN_Z; z <= VOLUME_MAX_Z; z++) {
                Column column = new Column(x, z);
                int index = (x - VOLUME_MIN_X) * depth + (z - VOLUME_MIN_Z);
                boolean nearShaft = forbidden.contains(column);
                boolean chamberColumn = chamber.columns().contains(column);
                int chamberCeiling = chamberColumn ? landingY + chamber.height(column) - 1 : Integer.MIN_VALUE;
                for (int level = 0; level < levels; level++) {
                    int y = baseY + level;
                    if (nearShaft || (chamberColumn && y <= chamberCeiling + CHAMBER_ROOF_CLEARANCE)) {
                        blocked[index][level] = true;
                        continue;
                    }
                    CellKind kind = cache.cell(direction, x, y, z);
                    blocked[index][level] = kind == CellKind.BEDROCK || kind == CellKind.BLOCK_ENTITY
                            || kind == CellKind.UNREADABLE || kind == CellKind.FLUID
                            || kind == CellKind.SOLID_UNSAFE;
                    gravity[index][level] = kind == CellKind.GRAVITY;
                }
            }
        }
        return new Field(blocked, gravity, baseY, topY, depth);
    }

    private static Route search(Field field, Chamber chamber, Terminus terminus, int landingY) {
        int startY = landingY - 1;
        int goalY = terminus.floorY();
        if (goalY < startY) {
            return null;
        }
        int width = VOLUME_MAX_X - VOLUME_MIN_X + 1;
        int depth = VOLUME_MAX_Z - VOLUME_MIN_Z + 1;
        int levels = goalY - startY + 1;
        int stateCount = width * depth * 4 * levels * (CORRIDOR_MIN_FLAT_RUN + 1);
        int[] parent = new int[stateCount];
        java.util.Arrays.fill(parent, -1);

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<Integer> seeds = new ArrayList<>();
        for (Column column : chamber.columns()) {
            for (int heading = 0; heading < 4; heading++) {
                int[] step = CARDINALS[heading];
                Column lead = new Column(column.x() + step[0], column.z() + step[1]);
                if (chamber.columns().contains(lead)) {
                    continue;
                }
                if (!stationUsable(field, lead, heading, startY)) {
                    continue;
                }
                seeds.add(stateIndex(lead, heading, startY - startY, CORRIDOR_MIN_FLAT_RUN, depth, levels));
            }
        }
        seeds.sort(Comparator.naturalOrder());
        for (int seed : seeds) {
            if (parent[seed] == -1) {
                parent[seed] = -2;
                queue.addLast(seed);
            }
        }

        int expanded = 0;
        int goal = -1;
        while (!queue.isEmpty() && goal < 0) {
            int state = queue.removeFirst();
            expanded++;
            if (expanded > MAX_ROUTE_NODES) {
                return null;
            }
            int flat = state % (CORRIDOR_MIN_FLAT_RUN + 1);
            int rest = state / (CORRIDOR_MIN_FLAT_RUN + 1);
            int level = rest % levels;
            rest /= levels;
            int heading = rest % 4;
            rest /= 4;
            int z = rest % depth + VOLUME_MIN_Z;
            int x = rest / depth + VOLUME_MIN_X;
            int y = startY + level;
            if (y == goalY && stationCovers(new Column(x, z), heading, terminus.column())) {
                goal = state;
                break;
            }
            for (int turn = -1; turn <= 1; turn++) {
                int nextHeading = Math.floorMod(heading + turn, 4);
                int[] step = CARDINALS[nextHeading];
                Column lead = new Column(x + step[0], z + step[1]);
                for (int rise = 0; rise <= 1; rise++) {
                    if (rise == 1 && flat < CORRIDOR_MIN_FLAT_RUN) {
                        continue;
                    }
                    int nextY = y + rise;
                    if (nextY > goalY) {
                        continue;
                    }
                    if (!stationUsable(field, lead, nextHeading, nextY)) {
                        continue;
                    }
                    int nextFlat = rise == 1 ? 0 : Math.min(CORRIDOR_MIN_FLAT_RUN, flat + 1);
                    int nextState = stateIndex(lead, nextHeading, nextY - startY, nextFlat, depth, levels);
                    if (nextState < 0 || parent[nextState] != -1) {
                        continue;
                    }
                    if (crossesOwnBore(parent, state, lead, nextHeading, nextY, startY, depth, levels)) {
                        continue;
                    }
                    parent[nextState] = state;
                    queue.addLast(nextState);
                }
            }
        }
        if (goal < 0) {
            return null;
        }
        List<Station> stations = new ArrayList<>();
        int cursor = goal;
        while (cursor >= 0) {
            int flat = cursor % (CORRIDOR_MIN_FLAT_RUN + 1);
            int rest = cursor / (CORRIDOR_MIN_FLAT_RUN + 1);
            int level = rest % levels;
            rest /= levels;
            int heading = rest % 4;
            rest /= 4;
            int z = rest % depth + VOLUME_MIN_Z;
            int x = rest / depth + VOLUME_MIN_X;
            stations.add(new Station(new Column(x, z), heading, startY + level, flat));
            cursor = parent[cursor] >= 0 ? parent[cursor] : -1;
        }
        java.util.Collections.reverse(stations);
        int bends = 0;
        for (int index = 1; index < stations.size(); index++) {
            if (stations.get(index).heading() != stations.get(index - 1).heading()) {
                bends++;
            }
        }
        return new Route(List.copyOf(stations), bends);
    }

    private static boolean stationUsable(Field field, Column lead, int heading, int y) {
        for (Column column : stationColumns(lead, heading)) {
            if (column.x() < VOLUME_MIN_X || column.x() > VOLUME_MAX_X
                    || column.z() < VOLUME_MIN_Z || column.z() > VOLUME_MAX_Z) {
                return false;
            }
            for (int level = 0; level <= CORRIDOR_CLEAR_HEIGHT; level++) {
                if (field.blockedAt(column, y + level)) {
                    return false;
                }
            }
            if (field.gravityAt(column, y + CORRIDOR_CLEAR_HEIGHT + 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The route search is a genuine 3D walk, so a winding corridor may pass back over a column it has already
     * used lower down. That is only legal when the two passes are far enough apart in Y to leave the upper
     * pass a floor to stand on, and this is the check that enforces it.
     *
     * <p>Every station owns exactly {@link #CORRIDOR_WIDTH} columns and, in each of them, one floor cell at
     * {@code y} plus {@link #CORRIDOR_CLEAR_HEIGHT} bored cells at {@code y + 1 .. y + CORRIDOR_CLEAR_HEIGHT}.
     * So two stations sharing a column conflict exactly when their heights differ by one, two or three: the
     * upper station's FLOOR is then one of the lower station's BORE cells, and because a carve outranks a
     * floor ({@link CellRole#CORRIDOR_CLEAR} over {@link CellRole#CORRIDOR_FLOOR}) the floor is never written
     * -- the upper pass stands on air, its partner column loses its neighbour, and the guaranteed two-wide
     * corridor pinches to one. Both directions of that conflict are the same predicate on {@code |dy|}, and
     * both are rejected here.
     *
     * <p>The check runs at EXPANSION time against the candidate state's own parent chain, so a route that
     * reaches the goal cannot contain the conflict at all -- it is not repaired afterwards, it is never built.
     * A rejected transition does not claim the state, so another winding may still reach it later.
     *
     * <p>Route heights never decrease (a step rises by zero or one), so walking the chain backwards walks Y
     * downwards and the first ancestor more than {@link #CORRIDOR_CLEAR_HEIGHT} below the candidate ends the
     * walk: everything earlier is lower still.
     */
    private static boolean crossesOwnBore(int[] parent, int state, Column lead, int heading, int y,
                                          int startY, int depth, int levels) {
        int[] side = CARDINALS[(heading + 1) & 3];
        int leadX = lead.x();
        int leadZ = lead.z();
        int sideX = leadX + side[0];
        int sideZ = leadZ + side[1];
        int cursor = state;
        while (cursor >= 0) {
            int rest = cursor / (CORRIDOR_MIN_FLAT_RUN + 1);
            int level = rest % levels;
            rest /= levels;
            int ancestorHeading = rest % 4;
            rest /= 4;
            int ancestorZ = rest % depth + VOLUME_MIN_Z;
            int ancestorX = rest / depth + VOLUME_MIN_X;
            int ancestorY = startY + level;
            if (ancestorY < y - CORRIDOR_CLEAR_HEIGHT) {
                return false;
            }
            int gap = Math.abs(ancestorY - y);
            if (gap >= 1 && gap <= CORRIDOR_CLEAR_HEIGHT) {
                int[] ancestorSide = CARDINALS[(ancestorHeading + 1) & 3];
                int ancestorSideX = ancestorX + ancestorSide[0];
                int ancestorSideZ = ancestorZ + ancestorSide[1];
                if ((ancestorX == leadX && ancestorZ == leadZ)
                        || (ancestorX == sideX && ancestorZ == sideZ)
                        || (ancestorSideX == leadX && ancestorSideZ == leadZ)
                        || (ancestorSideX == sideX && ancestorSideZ == sideZ)) {
                    return true;
                }
            }
            cursor = parent[cursor] >= 0 ? parent[cursor] : -1;
        }
        return false;
    }

    private static boolean stationCovers(Column lead, int heading, Column target) {
        for (Column column : stationColumns(lead, heading)) {
            if (column.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static List<Column> stationColumns(Column lead, int heading) {
        int[] side = CARDINALS[(heading + 1) & 3];
        return List.of(lead, new Column(lead.x() + side[0], lead.z() + side[1]));
    }

    private static int stateIndex(Column lead, int heading, int level, int flat, int depth, int levels) {
        if (lead.x() < VOLUME_MIN_X || lead.x() > VOLUME_MAX_X
                || lead.z() < VOLUME_MIN_Z || lead.z() > VOLUME_MAX_Z
                || level < 0 || level >= levels) {
            return -1;
        }
        int base = (lead.x() - VOLUME_MIN_X) * depth + (lead.z() - VOLUME_MIN_Z);
        return (((base * 4 + heading) * levels) + level) * (CORRIDOR_MIN_FLAT_RUN + 1) + flat;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Theme dressing                                                                                        */
    /* ---------------------------------------------------------------------------------------------------- */

    private static RejectionDetail dress(long chunkSeed, Theme theme, Map<Coordinate, CellRole> cells,
                                         Chamber chamber, Set<Column> shaft, Set<Column> cushion,
                                         List<Column> shelf, Route route, int landingY,
                                         ProbeCache cache, EnvelopeDirection direction) {
        Set<Column> reserved = new LinkedHashSet<>(cushion);
        reserved.addAll(shaft);
        reserved.addAll(shelf);
        for (Station station : route.stations()) {
            reserved.addAll(station.columns());
        }
        return switch (theme) {
            case ICE_CATHEDRAL ->
                    dressCathedral(chunkSeed, cells, chamber, reserved, landingY, cache, direction);
            case FRIGID_LAKE -> dressLake(chunkSeed, cells, chamber, reserved, shelf, route, landingY);
            case LOST_EXPEDITION -> dressExpedition(chunkSeed, cells, chamber, reserved, landingY);
        };
    }

    private static RejectionDetail dressCathedral(long chunkSeed, Map<Coordinate, CellRole> cells,
                                                  Chamber chamber, Set<Column> reserved, int landingY,
                                                  ProbeCache cache, EnvelopeDirection direction) {
        List<Column> recess = findBlock(chamber, reserved, RECESS_SPAN, true);
        if (recess == null) {
            return RejectionDetail.RECESS_UNAVAILABLE;
        }
        Set<Column> recessColumns = new LinkedHashSet<>(recess);
        for (Column column : recessColumns) {
            for (int y = landingY; y < landingY + chamber.height(column); y++) {
                place(cells, column.x(), y, column.z(), CellRole.RECESS_CLEAR);
            }
        }
        Set<Column> blocked = new LinkedHashSet<>(reserved);
        blocked.addAll(recessColumns);

        List<Column> dressable = dressableColumns(chamber, blocked);
        if (dressable.isEmpty()) {
            return RejectionDetail.CATHEDRAL_DRESSING_UNAVAILABLE;
        }
        int iceColumns = CATHEDRAL_MIN_ICE_COLUMNS
                + Math.floorMod(fold(chunkSeed, FOLD_ICE_COLUMN, landingY, 0),
                        CATHEDRAL_MAX_ICE_COLUMNS - CATHEDRAL_MIN_ICE_COLUMNS + 1);
        for (int index = 0; index < iceColumns; index++) {
            Column column = pick(dressable, chunkSeed, FOLD_ICE_COLUMN, index);
            CellRole role = index % 2 == 0 ? CellRole.COLUMN_ICE_PACKED : CellRole.COLUMN_ICE_BLUE;
            for (int y = landingY; y < landingY + chamber.height(column); y++) {
                place(cells, column.x(), y, column.z(), role);
            }
        }
        int clusters = CATHEDRAL_MIN_ICICLE_CLUSTERS
                + Math.floorMod(fold(chunkSeed, FOLD_ICICLE, landingY, 0),
                        CATHEDRAL_MAX_ICICLE_CLUSTERS - CATHEDRAL_MIN_ICICLE_CLUSTERS + 1);
        for (int index = 0; index < clusters; index++) {
            Column column = pick(dressable, chunkSeed, FOLD_ICICLE, index);
            int length = CATHEDRAL_MIN_ICICLE_LENGTH
                    + Math.floorMod(fold(chunkSeed, FOLD_ICICLE, column.x(), column.z()),
                            CATHEDRAL_MAX_ICICLE_LENGTH - CATHEDRAL_MIN_ICICLE_LENGTH + 1);
            int ceiling = landingY + chamber.height(column) - 1;
            /*
             * 26.2's SpeleothemBlock is Fallable and its canSurvive for TIP_DIRECTION=DOWN wants a sturdy
             * face on the block ABOVE. A cluster with nothing over its top cell is a falling block waiting
             * for the first neighbour update, so it is dropped rather than hung.
             */
            if (!hangsFromSomethingSturdy(cells, cache, direction, column, ceiling)) {
                continue;
            }
            for (int offset = 0; offset < length; offset++) {
                place(cells, column.x(), ceiling - offset, column.z(), CellRole.ICICLE_ICE);
            }
        }
        if (Math.floorMod(fold(chunkSeed, FOLD_FOSSIL, landingY, chamber.minX()), CATHEDRAL_FOSSIL_MODULUS) == 0) {
            int bones = CATHEDRAL_FOSSIL_MIN_CELLS
                    + Math.floorMod(fold(chunkSeed, FOLD_FOSSIL, landingY, 1),
                            CATHEDRAL_FOSSIL_MAX_CELLS - CATHEDRAL_FOSSIL_MIN_CELLS + 1);
            for (int index = 0; index < bones; index++) {
                Column column = pick(dressable, chunkSeed, FOLD_FOSSIL, index + 7);
                place(cells, column.x(), landingY, column.z(), CellRole.FOSSIL_BONE);
            }
        }
        return null;
    }

    private static RejectionDetail dressLake(long chunkSeed, Map<Coordinate, CellRole> cells, Chamber chamber,
                                             Set<Column> reserved, List<Column> shelf, Route route,
                                             int landingY) {
        Set<Column> interior = new LinkedHashSet<>();
        for (Column column : chamber.columns()) {
            if (isBoundaryColumn(chamber, column) || reserved.contains(column)) {
                continue;
            }
            interior.add(column);
        }
        List<Column> openCentre = findBlockIn(interior, reserved, LAKE_OPEN_CENTRE_SPAN, chamber, false);
        if (openCentre == null) {
            return RejectionDetail.LAKE_OPEN_CENTRE_UNAVAILABLE;
        }
        int dig = LAKE_MIN_DIG
                + Math.floorMod(fold(chunkSeed, FOLD_LAKE, landingY, 0), LAKE_MAX_DIG - LAKE_MIN_DIG + 1);
        if (landingY - 1 - dig < MIN_AUTHORED_Y) {
            dig = landingY - 1 - MIN_AUTHORED_Y;
        }
        if (dig < LAKE_MIN_DIG) {
            return RejectionDetail.LAKE_TOO_SHALLOW_TO_DIG;
        }

        Set<Column> water = new LinkedHashSet<>(openCentre);
        for (int step = 0; step < LAKE_GROWTH; step++) {
            Set<Column> grown = new LinkedHashSet<>(water);
            for (Column column : water) {
                for (int[] offset : CARDINALS) {
                    Column neighbour = new Column(column.x() + offset[0], column.z() + offset[1]);
                    if (interior.contains(neighbour)
                            && Math.floorMod(fold(chunkSeed, FOLD_LAKE, neighbour.x(), neighbour.z()), 4) != 0) {
                        grown.add(neighbour);
                    }
                }
            }
            water = grown;
        }
        Set<Column> openCentreColumns = new LinkedHashSet<>(openCentre);

        for (Column column : orderedColumns(water)) {
            place(cells, column.x(), landingY - 1 - dig, column.z(), CellRole.LAKE_BED);
            for (int y = landingY - dig; y <= landingY; y++) {
                place(cells, column.x(), y, column.z(), CellRole.LAKE_WATER);
            }
        }

        for (Column column : orderedColumns(water)) {
            for (int[] offset : NEIGHBOURHOOD_8) {
                Column neighbour = new Column(column.x() + offset[0], column.z() + offset[1]);
                if (water.contains(neighbour) || reserved.contains(neighbour)
                        || !chamber.columns().contains(neighbour)) {
                    continue;
                }
                if (Math.floorMod(fold(chunkSeed, FOLD_LAKE, neighbour.x(), neighbour.z() + 1), 3) == 0) {
                    continue;
                }
                place(cells, neighbour.x(), landingY - 1, neighbour.z(), CellRole.SHORE_ICE);
            }
        }

        List<Column> floeCandidates = new ArrayList<>();
        for (Column column : orderedColumns(water)) {
            if (!openCentreColumns.contains(column)) {
                floeCandidates.add(column);
            }
        }
        if (floeCandidates.size() < LAKE_MIN_FLOES * LAKE_FLOE_MIN_CELLS) {
            return RejectionDetail.LAKE_FLOES_UNAVAILABLE;
        }
        int floes = LAKE_MIN_FLOES
                + Math.floorMod(fold(chunkSeed, FOLD_FLOE, landingY, 0), LAKE_MAX_FLOES - LAKE_MIN_FLOES + 1);
        floeCandidates.sort(Comparator
                .comparingInt((Column column) ->
                        Math.floorMod(fold(chunkSeed, FOLD_FLOE, column.x(), column.z()), 4096))
                .thenComparingInt(Column::x)
                .thenComparingInt(Column::z));
        Set<Column> claimed = new LinkedHashSet<>();
        int built = 0;
        for (Column seed : floeCandidates) {
            if (built >= floes) {
                break;
            }
            if (claimed.contains(seed)) {
                continue;
            }
            int size = LAKE_FLOE_MIN_CELLS
                    + Math.floorMod(fold(chunkSeed, FOLD_FLOE, seed.x(), seed.z()),
                            LAKE_FLOE_MAX_CELLS - LAKE_FLOE_MIN_CELLS + 1);
            List<Column> floe = growFloe(seed, size, water, openCentreColumns, claimed);
            if (floe.size() < LAKE_FLOE_MIN_CELLS) {
                continue;
            }
            claimed.addAll(floe);
            for (Column column : floe) {
                place(cells, column.x(), landingY, column.z(), CellRole.FLOE_ICE);
            }
            built++;
        }
        if (built < LAKE_MIN_FLOES) {
            return RejectionDetail.LAKE_FLOES_UNAVAILABLE;
        }

        Column corridorEntry = route.stations().get(0).columns().get(0);
        List<Column> ledge = dryPath(chamber, water, shelf.get(0), corridorEntry);
        if (ledge == null) {
            return RejectionDetail.LAKE_LEDGE_UNAVAILABLE;
        }
        for (Column column : ledge) {
            place(cells, column.x(), landingY - 1, column.z(), CellRole.LEDGE_FIRM);
        }

        List<Column> moteCandidates = new ArrayList<>();
        for (Column column : chamber.columns()) {
            if (!water.contains(column) && !reserved.contains(column)) {
                moteCandidates.add(column);
            }
        }
        int motes = Math.min(LAKE_MAX_FROST_MOTES,
                Math.floorMod(fold(chunkSeed, FOLD_MOTE, landingY, 0), LAKE_MAX_FROST_MOTES + 1));
        /*
         * Frost settles on the FLOOR, not on the ceiling. A mote is one snow LAYER, and a snow layer needs
         * something under it: placed at landingY + height - 1 every one of them hung in the air over the room
         * and popped on the first block update. landingY is the room's own floor, which authorChamberFloor
         * has already guaranteed is firm, and the lake's open water is excluded by moteCandidates.
         */
        for (int index = 0; index < motes && !moteCandidates.isEmpty(); index++) {
            Column column = pick(moteCandidates, chunkSeed, FOLD_MOTE, index);
            place(cells, column.x(), landingY, column.z(), CellRole.FROST_MOTE);
        }
        return null;
    }

    /**
     * True when the cell directly over a chamber column's ceiling will be something an icicle can hang from.
     *
     * <p>Natural AIR counts, because the wall-seal pass closes every natural air cell over the carved
     * footprint and this column's ceiling is in that footprint whatever the dressing writes into it -- the
     * seal that lands there is packed ice. Everything else has to be solid already; a GRAVITY block is not
     * good enough, because it may not still be there when the icicle asks.
     */
    private static boolean hangsFromSomethingSturdy(Map<Coordinate, CellRole> cells, ProbeCache cache,
                                                    EnvelopeDirection direction, Column column, int ceiling) {
        Coordinate above = new Coordinate(column.x(), ceiling + 1, column.z());
        CellRole planned = cells.get(above);
        if (planned != null) {
            return !planned.isCarve();
        }
        CellKind natural = cache.cell(direction, above.x(), above.y(), above.z());
        if (natural == CellKind.AIR) {
            return above.y() >= MIN_AUTHORED_Y
                    && above.x() >= WRITE_MIN_X && above.x() <= WRITE_MAX_X
                    && above.z() >= WRITE_MIN_Z && above.z() <= WRITE_MAX_Z;
        }
        return natural == CellKind.SOLID_SAFE || natural == CellKind.SOLID_UNSAFE
                || natural == CellKind.BEDROCK;
    }

    private static RejectionDetail dressExpedition(long chunkSeed, Map<Coordinate, CellRole> cells,
                                                   Chamber chamber, Set<Column> reserved, int landingY) {
        List<Column> dressable = dressableColumns(chamber, reserved);
        int needed = EXPEDITION_LANTERNS + EXPEDITION_CACHE_CHESTS
                + EXPEDITION_MAX_BONE_DEBRIS + EXPEDITION_MAX_WOOD_DEBRIS;
        if (dressable.size() < needed) {
            return RejectionDetail.EXPEDITION_FLOOR_TOO_SMALL;
        }
        List<Column> pool = new ArrayList<>(dressable);
        for (int index = 0; index < EXPEDITION_LANTERNS; index++) {
            Column column = take(pool, chunkSeed, FOLD_EXPEDITION, index);
            place(cells, column.x(), landingY, column.z(), CellRole.LANTERN);
        }
        Column chest = take(pool, chunkSeed, FOLD_EXPEDITION, 100);
        place(cells, chest.x(), landingY, chest.z(), CellRole.CACHE_CHEST);
        int bones = EXPEDITION_MIN_BONE_DEBRIS
                + Math.floorMod(fold(chunkSeed, FOLD_EXPEDITION, landingY, 1),
                        EXPEDITION_MAX_BONE_DEBRIS - EXPEDITION_MIN_BONE_DEBRIS + 1);
        for (int index = 0; index < bones; index++) {
            Column column = take(pool, chunkSeed, FOLD_EXPEDITION, 200 + index);
            place(cells, column.x(), landingY, column.z(), CellRole.DEBRIS_BONE);
        }
        int wood = EXPEDITION_MIN_WOOD_DEBRIS
                + Math.floorMod(fold(chunkSeed, FOLD_EXPEDITION, landingY, 2),
                        EXPEDITION_MAX_WOOD_DEBRIS - EXPEDITION_MIN_WOOD_DEBRIS + 1);
        for (int index = 0; index < wood; index++) {
            Column column = take(pool, chunkSeed, FOLD_EXPEDITION, 300 + index);
            place(cells, column.x(), landingY, column.z(), CellRole.DEBRIS_WOOD);
        }
        return null;
    }

    /** One floe: a cardinally connected run of open water, never touching the dark centre or another floe. */
    private static List<Column> growFloe(Column seed, int size, Set<Column> water,
                                         Set<Column> openCentre, Set<Column> claimed) {
        List<Column> floe = new ArrayList<>();
        Set<Column> taken = new LinkedHashSet<>();
        ArrayDeque<Column> queue = new ArrayDeque<>();
        queue.add(seed);
        taken.add(seed);
        while (!queue.isEmpty() && floe.size() < size) {
            Column current = queue.removeFirst();
            if (!water.contains(current) || openCentre.contains(current) || claimed.contains(current)) {
                continue;
            }
            floe.add(current);
            for (int[] offset : CARDINALS) {
                Column next = new Column(current.x() + offset[0], current.z() + offset[1]);
                if (taken.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return floe;
    }

    private static List<Column> dressableColumns(Chamber chamber, Set<Column> reserved) {
        List<Column> columns = new ArrayList<>();
        for (Column column : chamber.columns()) {
            if (!reserved.contains(column)) {
                columns.add(column);
            }
        }
        return columns;
    }

    private static List<Column> findBlock(Chamber chamber, Set<Column> reserved, int span, boolean farthest) {
        Set<Column> pool = new LinkedHashSet<>(chamber.columns());
        pool.removeAll(reserved);
        return findBlockIn(pool, reserved, span, chamber, farthest);
    }

    private static List<Column> findBlockIn(Set<Column> pool, Set<Column> reserved, int span,
                                            Chamber chamber, boolean farthest) {
        int centreX = (chamber.minX() + chamber.maxX()) / 2;
        int centreZ = (chamber.minZ() + chamber.maxZ()) / 2;
        List<List<Column>> options = new ArrayList<>();
        for (Column column : orderedColumns(pool)) {
            List<Column> block = new ArrayList<>();
            boolean usable = true;
            for (int dx = 0; dx < span && usable; dx++) {
                for (int dz = 0; dz < span && usable; dz++) {
                    Column member = new Column(column.x() + dx, column.z() + dz);
                    if (!pool.contains(member) || reserved.contains(member)) {
                        usable = false;
                        break;
                    }
                    block.add(member);
                }
            }
            if (usable) {
                options.add(List.copyOf(block));
            }
        }
        if (options.isEmpty()) {
            return null;
        }
        Comparator<List<Column>> order = Comparator
                .comparingInt((List<Column> block) -> chebyshev(block.get(0), centreX, centreZ))
                .thenComparingInt(block -> block.get(0).x())
                .thenComparingInt(block -> block.get(0).z());
        options.sort(farthest ? order.reversed() : order);
        return options.get(0);
    }

    /** Wall-adjacent in the cardinal sense: a lake must not run straight into the chamber wall. */
    private static boolean isBoundaryColumn(Chamber chamber, Column column) {
        for (int[] offset : CARDINALS) {
            if (!chamber.columns().contains(new Column(column.x() + offset[0], column.z() + offset[1]))) {
                return true;
            }
        }
        return false;
    }

    private static List<Column> dryPath(Chamber chamber, Set<Column> water, Column from, Column to) {
        Map<Column, Column> parents = new LinkedHashMap<>();
        ArrayDeque<Column> queue = new ArrayDeque<>();
        Column start = chamber.columns().contains(from) ? from : null;
        if (start == null) {
            return null;
        }
        queue.add(start);
        parents.put(start, start);
        Column goal = null;
        while (!queue.isEmpty() && goal == null) {
            Column current = queue.removeFirst();
            for (int[] offset : CARDINALS) {
                Column next = new Column(current.x() + offset[0], current.z() + offset[1]);
                if (parents.containsKey(next) || water.contains(next)) {
                    continue;
                }
                if (!chamber.columns().contains(next)) {
                    continue;
                }
                parents.put(next, current);
                if (chebyshev(next, to.x(), to.z()) <= 1) {
                    goal = next;
                    break;
                }
                queue.addLast(next);
            }
        }
        if (goal == null) {
            return null;
        }
        List<Column> path = new ArrayList<>();
        Column cursor = goal;
        while (!cursor.equals(parents.get(cursor))) {
            path.add(cursor);
            cursor = parents.get(cursor);
        }
        path.add(cursor);
        java.util.Collections.reverse(path);
        return path;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Sealing, validation, assembly                                                                         */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * Close the natural cave air that touches the encounter's ENCLOSED interior, so the chamber and its
     * corridor read as authored rock rather than as a hole into whatever cave system happens to run past.
     *
     * <p>The seed set is {@link CellRole#isSealedInterior()}, NOT {@link CellRole#isCarve()}. The difference
     * is the whole point: the mouth and the exit portal are carved, but they are the two OPENINGS, and a seal
     * derived from them closes the very thing they exist to open. Sealing from {@link CellRole#MOUTH_COLLAPSE}
     * capped every mouth with packed ice (the probe only ever accepts a floor with air above it, so the cell
     * at {@code y + 1} was guaranteed to be sealable) -- a visible ice patch in a snowy floor that nothing
     * could fall through; sealing from {@link CellRole#EXIT_PORTAL_CLEAR} walled the second opening shut.
     * Both are excluded here and the two named laws below are what keeps the exclusion honest:
     *
     * <ul>
     *   <li>The shaft's top cell sits directly under the mouth, but the mouth cell is itself planned, so the
     *       {@code cells.containsKey} guard skips it and {@link CellRole#SHAFT_CLEAR} can never re-cap the
     *       mouth from below.</li>
     *   <li>Corridor cells run right up to the portal plane, but every portal cell is planned too, so the
     *       same guard keeps {@link CellRole#CORRIDOR_CLEAR} from sealing inside the opening.</li>
     * </ul>
     *
     * <p>A natural air pocket that touches ONLY a mouth or a portal cell now stays open. That is correct: it
     * is part of the upper cave the two openings deliberately breach into.
     *
     * <p>{@code carvedFootprint} is the seed set as it stood BEFORE the dressing pass, and it is why this
     * takes an argument at all. Dressing outranks the carve it lands on, so a seed set read off the finished
     * map is missing one cell per icicle cluster, ice column and frost mote -- and the seal each of those
     * cells owed. Sealing from the footprint the encounter CARVED, rather than from what happens to be
     * standing in it afterwards, makes the roof obligation independent of the dressing entirely.
     */
    private static void addWallSeals(Map<Coordinate, CellRole> cells, ProbeCache cache,
                                     EnvelopeDirection direction, Set<Coordinate> carvedFootprint) {
        Set<Coordinate> seeds = new LinkedHashSet<>(carvedFootprint);
        for (Map.Entry<Coordinate, CellRole> entry : cells.entrySet()) {
            if (entry.getValue().isSealedInterior()) {
                seeds.add(entry.getKey());
            }
        }
        List<Coordinate> carved = new ArrayList<>(seeds);
        carved.sort(COORDINATE_ORDER);
        List<Coordinate> seals = new ArrayList<>();
        for (Coordinate coordinate : carved) {
            for (int[] offset : SHELL_OFFSETS) {
                Coordinate neighbour = new Coordinate(
                        coordinate.x() + offset[0], coordinate.y() + offset[1], coordinate.z() + offset[2]);
                if (cells.containsKey(neighbour)) {
                    continue;
                }
                if (neighbour.x() < WRITE_MIN_X || neighbour.x() > WRITE_MAX_X
                        || neighbour.z() < WRITE_MIN_Z || neighbour.z() > WRITE_MAX_Z
                        || neighbour.y() < MIN_AUTHORED_Y) {
                    continue;
                }
                if (cache.cell(direction, neighbour.x(), neighbour.y(), neighbour.z()) == CellKind.AIR) {
                    seals.add(neighbour);
                }
            }
        }
        for (Coordinate seal : seals) {
            place(cells, seal.x(), seal.y(), seal.z(), CellRole.WALL_SEAL);
        }
    }

    /**
     * Give the room a floor. The carve starts at {@code landingY}, so {@code landingY - 1} is what a player
     * stands on, what a lantern's {@code canSurvive} asks {@code canSupportCenter} about, and what a chest
     * needs under it -- and until now only the cushion, the shelf and the lake ever authored anything there.
     * Every other interior column stood on whatever the terrain happened to hold, including nothing at all.
     *
     * <p>Natural firm rock stays natural: the encounter incorporates the cave it is cut into rather than
     * paving it. Anything else -- open void, a gravity block that will not stay put -- is authored firm.
     * Water, a block entity or an unreadable cell under the floor is authored too and then refused by
     * {@link #validate}, which is the honest answer: the planner cannot prove that floor is safe, so it
     * declines the chunk rather than shipping a room with a hole in the bottom of it.
     */
    private static void authorChamberFloor(Map<Coordinate, CellRole> cells, ProbeCache cache,
                                           EnvelopeDirection direction, Chamber chamber, int landingY) {
        int floorY = landingY - 1;
        if (floorY < MIN_AUTHORED_Y) {
            return;
        }
        for (Column column : chamber.ordered()) {
            if (isFirmNaturalFloor(cache.cell(direction, column.x(), floorY, column.z()))) {
                continue;
            }
            place(cells, column.x(), floorY, column.z(), CellRole.FLOOR_SEAL);
        }
    }

    /** A cell the encounter may stand on and must not touch: solid, stationary, already there. */
    private static boolean isFirmNaturalFloor(CellKind kind) {
        return kind == CellKind.SOLID_SAFE || kind == CellKind.SOLID_UNSAFE || kind == CellKind.BEDROCK;
    }

    /**
     * Decline any plan step 10's {@code globe:magma_quench_sweep} would scar.
     *
     * <p>The quench runs at the end of TOP_LAYER_MODIFICATION, after this feature, and transforms the shell
     * of every magma block in the glacial biomes: a FLOODED magma turns the shell's ice and water to
     * obsidian, a DRY ice-touching magma MELTS the ice on its own faces to AIR. Neither reading knows the ice
     * it is eating is a chamber wall or the water a frigid lake, so a chamber built within reach of magma
     * ships with an obsidian bite out of its lake, or a hole punched clean through the seal that was holding
     * the natural cave out. The planner cannot un-run the sweep; it can only decline the chunk.
     *
     * <p>Deliberately conservative in one place: {@link CellKind} cannot tell magma from ore or from the
     * protected deepslate country, so EVERY {@link CellKind#SOLID_UNSAFE} on a face counts. All of them are
     * blocks this encounter is already forbidden to cut into, so the cost is a handful of chunks beside an
     * ore vein and the benefit is that no reading of "unsafe solid" can smuggle a magma block past.
     */
    private static RejectionDetail quenchScarRisk(Map<Coordinate, CellRole> cells, ProbeCache cache,
                                                  EnvelopeDirection direction) {
        List<Coordinate> quenchable = new ArrayList<>();
        for (Map.Entry<Coordinate, CellRole> entry : cells.entrySet()) {
            CellRole role = entry.getValue();
            if (role == CellRole.LAKE_WATER || role.isIceFamily()) {
                quenchable.add(entry.getKey());
            }
        }
        quenchable.sort(COORDINATE_ORDER);
        for (Coordinate coordinate : quenchable) {
            for (int[] offset : SHELL_OFFSETS) {
                Coordinate neighbour = new Coordinate(
                        coordinate.x() + offset[0], coordinate.y() + offset[1], coordinate.z() + offset[2]);
                if (cells.containsKey(neighbour)) {
                    continue;
                }
                if (cache.cell(direction, neighbour.x(), neighbour.y(), neighbour.z())
                        == CellKind.SOLID_UNSAFE) {
                    return RejectionDetail.MAGMA_ADJACENT_TO_ICE_OR_WATER;
                }
            }
        }
        return null;
    }

    private static RejectionDetail validate(Map<Coordinate, CellRole> cells, ProbeCache cache,
                                            EnvelopeDirection direction) {
        Set<Quart> quarts = new LinkedHashSet<>();
        for (Map.Entry<Coordinate, CellRole> entry : cells.entrySet()) {
            Coordinate coordinate = entry.getKey();
            if (coordinate.y() <= 0) {
                return RejectionDetail.CELL_BELOW_WORLD_FLOOR;
            }
            if (coordinate.x() < WRITE_MIN_X || coordinate.x() > WRITE_MAX_X
                    || coordinate.z() < WRITE_MIN_Z || coordinate.z() > WRITE_MAX_Z) {
                return RejectionDetail.CELL_OUTSIDE_ENVELOPE;
            }
            CellKind kind = cache.cell(direction, coordinate.x(), coordinate.y(), coordinate.z());
            switch (kind) {
                case BLOCK_ENTITY -> {
                    return RejectionDetail.BLOCK_ENTITY_IN_VOLUME;
                }
                case BEDROCK -> {
                    return RejectionDetail.BEDROCK_IN_VOLUME;
                }
                case UNREADABLE -> {
                    return RejectionDetail.UNREADABLE_IN_VOLUME;
                }
                case FLUID -> {
                    return RejectionDetail.FLUID_IN_VOLUME;
                }
                case SOLID_UNSAFE -> {
                    return RejectionDetail.UNSAFE_SOLID_IN_VOLUME;
                }
                default -> { /* AIR, SOLID_SAFE and GRAVITY are all workable here. */ }
            }
            int lx = direction.localX(coordinate.x(), coordinate.z());
            int lz = direction.localZ(coordinate.x(), coordinate.z());
            quarts.add(new Quart(Math.floorDiv(lx, QUART_SIDE), Math.floorDiv(coordinate.y(), QUART_SIDE),
                    Math.floorDiv(lz, QUART_SIDE)));
        }
        for (Map.Entry<Coordinate, CellRole> entry : cells.entrySet()) {
            if (!entry.getValue().isCarve()) {
                continue;
            }
            Coordinate above = new Coordinate(entry.getKey().x(), entry.getKey().y() + 1, entry.getKey().z());
            if (cells.containsKey(above)) {
                continue;
            }
            if (cache.cell(direction, above.x(), above.y(), above.z()) == CellKind.GRAVITY) {
                return RejectionDetail.GRAVITY_ABOVE_CARVED_CELL;
            }
        }
        for (Quart quart : quarts) {
            if (!cache.savedGlacialQuart(quart)) {
                return RejectionDetail.BIOME_QUART_NOT_GLACIAL;
            }
        }
        return null;
    }

    private static List<Cell> toWriteList(Map<Coordinate, CellRole> cells, EnvelopeDirection direction) {
        List<Cell> writes = new ArrayList<>(cells.size());
        for (Map.Entry<Coordinate, CellRole> entry : cells.entrySet()) {
            Coordinate coordinate = entry.getKey();
            writes.add(new Cell(direction.localX(coordinate.x(), coordinate.z()), coordinate.y(),
                    direction.localZ(coordinate.x(), coordinate.z()), entry.getValue()));
        }
        writes.sort(Comparator
                .comparingInt((Cell cell) -> cell.role().rank())
                .thenComparingInt(Cell::x)
                .thenComparingInt(Cell::y)
                .thenComparingInt(Cell::z));
        return List.copyOf(writes);
    }

    private static List<Quart> biomeQuarts(List<Cell> writes) {
        Set<Quart> quarts = new TreeSet<>(Comparator
                .comparingInt(Quart::quartX)
                .thenComparingInt(Quart::quartY)
                .thenComparingInt(Quart::quartZ));
        for (Cell cell : writes) {
            quarts.add(new Quart(Math.floorDiv(cell.x(), QUART_SIDE), Math.floorDiv(cell.y(), QUART_SIDE),
                    Math.floorDiv(cell.z(), QUART_SIDE)));
        }
        return List.copyOf(quarts);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Small helpers                                                                                         */
    /* ---------------------------------------------------------------------------------------------------- */

    private static void place(Map<Coordinate, CellRole> cells, int x, int y, int z, CellRole role) {
        Coordinate coordinate = new Coordinate(x, y, z);
        CellRole existing = cells.get(coordinate);
        if (existing == null || role.rank() > existing.rank()) {
            cells.put(coordinate, role);
        }
    }

    private static Set<Column> dilate(Set<Column> columns, int radius) {
        Set<Column> grown = new LinkedHashSet<>();
        for (Column column : orderedColumns(columns)) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    grown.add(new Column(column.x() + dx, column.z() + dz));
                }
            }
        }
        return grown;
    }

    private static List<Column> orderedColumns(Set<Column> columns) {
        List<Column> ordered = new ArrayList<>(columns);
        ordered.sort(Comparator.comparingInt(Column::x).thenComparingInt(Column::z));
        return ordered;
    }

    private static boolean isConnected(Set<Column> columns) {
        if (columns.isEmpty()) {
            return true;
        }
        Column start = orderedColumns(columns).get(0);
        Set<Column> seen = new LinkedHashSet<>();
        ArrayDeque<Column> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            Column current = queue.removeFirst();
            for (int[] offset : CARDINALS) {
                Column next = new Column(current.x() + offset[0], current.z() + offset[1]);
                if (columns.contains(next) && seen.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return seen.size() == columns.size();
    }

    private static int neighbourCount(Set<Column> columns, Column column) {
        int count = 0;
        for (int[] offset : CARDINALS) {
            if (columns.contains(new Column(column.x() + offset[0], column.z() + offset[1]))) {
                count++;
            }
        }
        return count;
    }

    private static boolean touchesBoxEdge(Column column, int minX, int minZ, int maxX, int maxZ) {
        return column.x() == minX || column.x() == maxX || column.z() == minZ || column.z() == maxZ;
    }

    private static int shapeRank(long chunkSeed, Column column) {
        return Math.floorMod(fold(chunkSeed, FOLD_SHAPE_RANK, column.x(), column.z()), 1024);
    }

    private static Column pick(List<Column> columns, long chunkSeed, long foldConstant, int index) {
        return columns.get(Math.floorMod(fold(chunkSeed, foldConstant, index, columns.size()), columns.size()));
    }

    private static Column take(List<Column> columns, long chunkSeed, long foldConstant, int index) {
        int at = Math.floorMod(fold(chunkSeed, foldConstant, index, columns.size()), columns.size());
        return columns.remove(at);
    }

    private static int centroidX(Set<Column> columns) {
        int sum = 0;
        for (Column column : columns) {
            sum += column.x();
        }
        return roundedMean(sum, columns.size());
    }

    private static int centroidZ(Set<Column> columns) {
        int sum = 0;
        for (Column column : columns) {
            sum += column.z();
        }
        return roundedMean(sum, columns.size());
    }

    private static int roundedMean(int sum, int count) {
        return Math.floorDiv(2 * sum + count, 2 * count);
    }

    private static int chebyshev(Column column, int x, int z) {
        return Math.max(Math.abs(column.x() - x), Math.abs(column.z() - z));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PlanResult rejected(RejectionDetail detail) {
        return new PlanResult(null, detail.rejection(), detail);
    }

    private static Failure deeper(Failure current, Failure candidate) {
        if (candidate == null) {
            return current;
        }
        return candidate.stage() > current.stage() ? candidate : current;
    }

    /* Deterministic hash chain: splitmix64 finalizer rounds folded over world identity and coordinates. */

    private static long chunkSeed(long worldSeed, int chunkX, int chunkZ) {
        long mixed = worldSeed ^ SALT;
        mixed = mix64(mixed ^ Integer.toUnsignedLong(chunkX));
        mixed = mix64(mixed ^ Long.rotateLeft(Integer.toUnsignedLong(chunkZ), 32));
        return mixed;
    }

    private static long fold(long base, long foldConstant, int first, int second) {
        long mixed = mix64(base ^ foldConstant);
        mixed = mix64(mixed ^ Integer.toUnsignedLong(first));
        mixed = mix64(mixed ^ Long.rotateLeft(Integer.toUnsignedLong(second), 32));
        return mixed;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Mouth mask catalogue                                                                                  */
    /* ---------------------------------------------------------------------------------------------------- */

    private static final List<MouthMask> MOUTH_MASKS = buildMouthMasks();

    private static List<MouthMask> buildMouthMasks() {
        Map<String, int[][]> shapes = new LinkedHashMap<>();
        shapes.put("diagonal-2", new int[][] {{0, 0}, {1, 1}});
        shapes.put("L-3", new int[][] {{0, 0}, {1, 0}, {1, 1}});
        shapes.put("zigzag-3", new int[][] {{0, 0}, {1, 1}, {2, 0}});
        shapes.put("S-4", new int[][] {{0, 0}, {1, 0}, {1, 1}, {2, 1}});
        shapes.put("T-4", new int[][] {{0, 0}, {1, 0}, {2, 0}, {1, 1}});
        shapes.put("skew-4", new int[][] {{0, 0}, {1, 1}, {2, 1}, {2, 2}});
        List<MouthMask> masks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, int[][]> shape : shapes.entrySet()) {
            int[][] offsets = shape.getValue();
            for (int rotation = 0; rotation < 4; rotation++) {
                List<MaskOffset> rotated = normalise(offsets, rotation);
                StringBuilder key = new StringBuilder();
                for (MaskOffset offset : rotated) {
                    key.append(offset.dx()).append(':').append(offset.dz()).append(',');
                }
                if (seen.add(key.toString())) {
                    masks.add(new MouthMask(shape.getKey(), rotation, rotated));
                }
            }
        }
        return List.copyOf(masks);
    }

    private static List<MaskOffset> normalise(int[][] offsets, int rotation) {
        List<int[]> rotated = new ArrayList<>();
        for (int[] offset : offsets) {
            int dx = offset[0];
            int dz = offset[1];
            for (int turn = 0; turn < rotation; turn++) {
                int nextDx = -dz;
                int nextDz = dx;
                dx = nextDx;
                dz = nextDz;
            }
            rotated.add(new int[] {dx, dz});
        }
        int minDx = Integer.MAX_VALUE;
        int minDz = Integer.MAX_VALUE;
        for (int[] offset : rotated) {
            minDx = Math.min(minDx, offset[0]);
            minDz = Math.min(minDz, offset[1]);
        }
        List<MaskOffset> result = new ArrayList<>();
        for (int[] offset : rotated) {
            result.add(new MaskOffset(offset[0] - minDx, offset[1] - minDz));
        }
        result.sort(Comparator.comparingInt(MaskOffset::dx).thenComparingInt(MaskOffset::dz));
        return List.copyOf(result);
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Internal value types                                                                                  */
    /* ---------------------------------------------------------------------------------------------------- */

    /** The six block faces. Never mutated; contents are read-only by convention. */
    private static final int[][] SHELL_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static final Comparator<Coordinate> COORDINATE_ORDER = Comparator
            .comparingInt(Coordinate::x).thenComparingInt(Coordinate::y).thenComparingInt(Coordinate::z);

    private static final Comparator<Footprint> FOOTPRINT_ORDER = Comparator
            .comparingInt((Footprint footprint) -> -footprint.collapseFloorY())
            .thenComparingInt(Footprint::spread)
            .thenComparingInt(Footprint::anchorX)
            .thenComparingInt(Footprint::anchorZ)
            .thenComparing(footprint -> footprint.mask().name())
            .thenComparingInt(footprint -> footprint.mask().rotation());

    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparingInt((Candidate candidate) -> -candidate.score())
            .thenComparingInt(candidate -> candidate.footprint().anchorX())
            .thenComparingInt(candidate -> candidate.footprint().anchorZ())
            .thenComparing(candidate -> candidate.footprint().mask().name())
            .thenComparingInt(candidate -> candidate.footprint().mask().rotation())
            .thenComparingInt(candidate -> candidate.direction().ordinal());

    private record Coordinate(int x, int y, int z) {
    }

    private record Column(int x, int z) {
    }

    /**
     * {@code floors} is each column's floor-top Y (the cell a partial snow layer occupies) and
     * {@code collapses} is each column's collapse-cube Y; the two differ by one exactly where the top is a
     * partial layer. {@code collapseFloorY} is the LOWEST collapse cell, which is what the drop, the landing
     * and the reported mouth centroid are all measured from -- so the twelve-to-twenty law is measured from
     * the block the victim actually falls through.
     */
    private record Footprint(int anchorX, int anchorZ, MouthMask mask, List<int[]> columns,
                             List<Integer> floors, List<Integer> collapses, int collapseFloorY, int spread) {
    }

    private record FootprintResult(Footprint footprint, RejectionDetail detail) {
    }

    private record Candidate(Footprint footprint, EnvelopeDirection direction, int score) {
    }

    private record Failure(int stage, RejectionDetail detail) {
    }

    private record Attempt(Plan plan, Failure failure) {
        static Attempt accepted(Plan plan) {
            return new Attempt(plan, null);
        }

        static Attempt failed(int stage, RejectionDetail detail) {
            return new Attempt(null, new Failure(stage, detail));
        }
    }

    private record Terminus(Column column, int floorY, int distance) {
    }

    private record Station(Column lead, int heading, int y, int flat) {
        List<Column> columns() {
            return stationColumns(lead, heading);
        }
    }

    private record Route(List<Station> stations, int bends) {
    }

    private record RouteResult(Route route, Column terminus, RejectionDetail detail) {
    }

    private record Chamber(List<Column> ordered, Map<Column, Integer> heights,
                           int minX, int minZ, int maxX, int maxZ, Variant variant) {
        Set<Column> columns() {
            return heights.keySet();
        }

        int height(Column column) {
            Integer value = heights.get(column);
            return value == null ? 0 : value;
        }
    }

    private static final class Field {
        private final boolean[][] blocked;
        private final boolean[][] gravity;
        private final int baseY;
        private final int topY;
        private final int depth;

        Field(boolean[][] blocked, boolean[][] gravity, int baseY, int topY, int depth) {
            this.blocked = blocked;
            this.gravity = gravity;
            this.baseY = baseY;
            this.topY = topY;
            this.depth = depth;
        }

        boolean blockedAt(Column column, int y) {
            if (y < baseY || y > topY) {
                return true;
            }
            int index = (column.x() - VOLUME_MIN_X) * depth + (column.z() - VOLUME_MIN_Z);
            if (index < 0 || index >= blocked.length) {
                return true;
            }
            return blocked[index][y - baseY];
        }

        boolean gravityAt(Column column, int y) {
            if (y < baseY || y > topY) {
                return false;
            }
            int index = (column.x() - VOLUME_MIN_X) * depth + (column.z() - VOLUME_MIN_Z);
            if (index < 0 || index >= gravity.length) {
                return false;
            }
            return gravity[index][y - baseY];
        }
    }

    private static final class Budget {
        private int volumeValidations;
        private int exitSearches;

        void countVolumeValidation() {
            volumeValidations++;
        }

        void countExitSearch() {
            exitSearches++;
        }

        boolean volumeExhausted() {
            return volumeValidations >= MAX_VOLUME_VALIDATIONS;
        }

        boolean exitExhausted() {
            return exitSearches >= MAX_EXIT_SEARCHES;
        }
    }

    /** Memoises probe reads so repeated candidate evaluation never re-asks the world for the same block. */
    private static final class ProbeCache {
        private final TerrainProbe probe;
        private final Map<Long, ColumnInfo> columns = new HashMap<>();
        private final Set<Long> columnsRead = new java.util.HashSet<>();
        private final Map<Long, CellKind> cells = new HashMap<>();
        private final Map<Quart, Boolean> quarts = new HashMap<>();

        ProbeCache(TerrainProbe probe) {
            this.probe = probe;
        }

        ColumnInfo column(int localX, int localZ) {
            long key = ((long) (localX + 512) << 32) | (localZ + 512);
            if (columnsRead.add(key)) {
                ColumnInfo info = probe.column(localX, localZ);
                if (info != null) {
                    columns.put(key, info);
                }
            }
            return columns.get(key);
        }

        CellKind cell(EnvelopeDirection direction, int canonicalX, int y, int canonicalZ) {
            int localX = direction.localX(canonicalX, canonicalZ);
            int localZ = direction.localZ(canonicalX, canonicalZ);
            long key = ((long) (localX + 512) << 42) | ((long) (y + 4096) << 21) | (localZ + 512);
            CellKind cached = cells.get(key);
            if (cached != null) {
                return cached;
            }
            CellKind kind = probe.cell(localX, y, localZ);
            CellKind resolved = kind == null ? CellKind.UNREADABLE : kind;
            cells.put(key, resolved);
            return resolved;
        }

        boolean savedGlacialQuart(Quart quart) {
            Boolean cached = quarts.get(quart);
            if (cached != null) {
                return cached;
            }
            boolean saved = probe.savedGlacialQuart(quart.quartX(), quart.quartY(), quart.quartZ());
            quarts.put(quart, saved);
            return saved;
        }
    }
}
