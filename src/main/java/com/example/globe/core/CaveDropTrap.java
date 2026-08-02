package com.example.globe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * S44 IN-CAVE DROP TRAPS (Peetsa 2026-07-25, B-9 punchlist item 1: "I want to add traps inside caves that
 * drop the player down to a deeper layer of the cave"). Pure decision math -- zero Minecraft imports (Core
 * Logic layer, plain-JVM testable). The world-side wiring is {@code world.CaveDropTrapFeature}.
 *
 * <p><b>The trap.</b> Inside the glacial underground, a genuine walkable cave-gallery floor can hide a
 * deliberately authored descent. The entrance begins as a safely certified mass of natural solid floor,
 * never a thin roof over an existing void. An irregular mixed patch of supported firm snow and
 * {@code powder_snow} creates the stumble surface; only the powder stations open into authored shafts.
 * Every shaft reaches a common powder cushion and a dry authored route back to the original gallery (the
 * S35 fall law: the fall itself never kills; the cost is position, cold, and finding the way back up).
 *
 * <p><b>Laws carried over from the surface traps (S35):</b> cushion at EVERY drop cell's landing; a
 * water landing is never trapped (the skinned-pond law -- gen-time "safe water" can grow an ice skin
 * later and break the cushion guarantee, so water-floored cells are excluded outright); the landing must
 * be horizontally traversable (the entombment law -- a sealed pocket with no exit is the one outlawed
 * outcome); deterministic seeded rolls, no new noise (Art VI).
 */
public final class CaveDropTrap {

    private CaveDropTrap() {
    }

    /** Minimum standing room (air blocks) ABOVE the floor for the gallery to be walkable trap ground --
     *  a player is ~1.8 blocks; 2 is vanilla walking clearance. */
    public static final int MIN_GALLERY_AIR = 2;

    /** Minimum authored fall from the embedded cave floor to its powder cushion. */
    public static final int MIN_DROP_AIR = 10;

    /** The conformal throat is an upper glacial-cave encounter, never a deep-slate or surface trap. */
    public static final int MIN_CONFORMAL_THROAT_FLOOR_Y = 24;

    /** Y45 leaves two air cells beneath the glacial cave ceiling at Y48. */
    public static final int MAX_CONFORMAL_THROAT_FLOOR_Y = 45;

    /** Natural destination galleries belong to the lower glacial layer, including its Y0 floor. */
    public static final int MIN_BETWEEN_LAYER_LOWER_FLOOR_Y = 0;

    /** Y23 is the last lower-layer floor; Y24 begins the upper encounter band. */
    public static final int MAX_BETWEEN_LAYER_LOWER_FLOOR_Y = 23;

    /** A true layer change must be materially deeper than the retired ten-block authored pocket. */
    public static final int MIN_BETWEEN_LAYER_SEPARATION = 16;

    /** Ordinary between-layer drops stay bounded and survivable on their powder cushions. */
    public static final int MAX_BETWEEN_LAYER_SEPARATION = 32;

    /** A lower opening must continue through a meaningful natural gallery, not a six-cell pocket. */
    public static final int MIN_NATURAL_LOWER_CONTINUATION = 12;

    /** A one-block floor undulation can make an individual station one block deeper than the
     * twelve-block common approach descent. */
    public static final int MAX_ORDINARY_DROP_AIR = 13;

    /** The entrance must begin as genuine solid cave-floor mass, never a 1-3-block roof over an existing
     * void. Six additional natural blocks cover the cushion support and bounded stair/pocket preflight. */
    public static final int MIN_CERTIFIED_SOLID_DEPTH = MIN_DROP_AIR + 6;

    /** S50 CARPET REDESIGN (owner, TEST 138 flight: the in-cave traps read as "just single blocks right
     *  now. make it more like a carpet of powder/regular to stumble into"): a trap is a broad powder
     *  CARPET now, surface-class. Minimum area kills the single-block lies outright -- a patch smaller
     *  than this never traps; maximum approaches the surface traps' 48. */
    public static final int MIN_PATCH_AREA = 6;

    /** Maximum patch area (cells) a single drop carpet may cover -- broad, but never a floor deletion. */
    public static final int PATCH_MAX_AREA = 40;

    /** A deceptive entrance is a mixed patch, not an all-powder white panel. At least one genuinely
     * supported firm-snow shoulder is required for every two powder cells, with four as the floor. */
    public static final int MIN_FIRM_CAMOUFLAGE_CELLS = 4;

    /** At most this many deceptive powder cells may be represented by one firm camouflage cell. */
    public static final int MAX_POWDER_CELLS_PER_FIRM_CAMOUFLAGE = 2;

    /** A lower branch search is bounded so malformed masks cannot turn decoration into an unbounded
     * combinatorial search. Normal 6..40-cell carpets settle far below this ceiling. */
    private static final int MAX_CERTIFIED_BRANCH_SEARCH_STATES = 8_192;

    /** Each side of the walk-through approach is a real run, never two disconnected lucky pixels. */
    public static final int MIN_APPROACH_BANK_RUN = 2;

    /** Each approach bank must open into this many additional stable-floor cells beyond the immediate
     * trap collar. This proves that the bank belongs to a cave floor rather than a tiny ledge. */
    public static final int MIN_APPROACH_CONTINUATION_AREA = 4;

    /** Natural floor beneath each untouched approach continuation must be more than a one-block lip. */
    public static final int APPROACH_CONTINUATION_SUPPORT_DEPTH = 4;

    /** Every column in the complete embedded-ribbon contract must remain this far below the maintained
     * feature-stage surface. This rejects exposed snowfields and isolated ice-spire height tokens without
     * depending on a stale world-generation heightmap. */
    public static final int MIN_CAVE_ENCLOSURE_RISE = 6;

    /** The natural ceiling proving enclosure may not begin inside the walker's own headroom. */
    public static final int MIN_ROOF_SEARCH_RISE = 3;

    /**
     * How far above the carpet the enclosure proof will look for that ceiling. This is a search
     * bound, not a safety property: what keeps a trap honest is that a ceiling EXISTS, is at least
     * two natural blocks thick, carries no sky above it, and holds no water above it -- all of which
     * hold at any height. The former six-block bound silently required a LOW roof and so rejected
     * 752 of 762 traced glacial passages (census v6-s, owner call 2026-07-27: "there is no limit...
     * I don't know why that is even there"), because open glacial galleries are tall rooms.
     */
    public static final int MAX_ROOF_SEARCH_RISE = 64;

    /** Natural blocks inspected above the located ceiling: one thickness block plus the water band. */
    public static final int ROOF_CLEARANCE_ABOVE = 5;

    /** A deep reward chamber needs at least this many connected horizontal gallery cells. */
    public static final int MIN_FLOODED_GALLERY_FOOTPRINT = 36;

    /** It must also exceed its entrance footprint by this margin, so a broad entrance cannot satisfy
     * the law with a merely decorative rim around the landing water. */
    public static final int MIN_FLOODED_GALLERY_ENTRANCE_MARGIN = 16;

    /** Deterministic fraction of eligible patches that become traps (per-patch roll {@code <} this).
     *  S44 census-calibrated 0.10 at patch-min 1; S50's 0.30 landed 1 carpet per ~10 chunks -- survey-proof
     *  rarity (owner TEST 139: "nowhere to be found...easily"). S51 runs 0.50 with the per-chunk cap at 2:
     *  a carpet every few chunks, still hidden, no minefield (min-size + cap keep the ceiling). */
    public static final float TRAP_FRACTION = 0.85f;

    /** At most this many patches fire per chunk (the census cap): a chunk may hide a couple of false
     *  floors; a chunk riddled with them reads as broken terrain, not danger. S51: 1 -> 2. */
    public static final int MAX_PATCHES_PER_CHUNK = 4;

    /**
     * Does a column qualify as embedded trap floor? The player needs ordinary cave headroom and the
     * complete authored depth must be certified safe natural solid. Any pre-existing void under the
     * entrance is the old overhang grammar and rejects outright.
     */
    public static boolean cellQualifies(
            int galleryAirAbove, int certifiedSolidDepth, boolean preexistingVoidBelow) {
        return galleryAirAbove >= MIN_GALLERY_AIR
                && certifiedSolidDepth >= MIN_CERTIFIED_SOLID_DEPTH
                && !preexistingVoidBelow;
    }

    /** Whether a conformal cave throat belongs to the authored upper-glacial vertical band. */
    public static boolean isConformalThroatFloor(int floorY) {
        return floorY >= MIN_CONFORMAL_THROAT_FLOOR_Y
                && floorY <= MAX_CONFORMAL_THROAT_FLOOR_Y;
    }

    /** Exact upper-layer boundary used by the new natural between-layer materializer. */
    public static boolean isBetweenLayerUpperFloor(int floorY) {
        return isConformalThroatFloor(floorY);
    }

    /** Exact lower natural-gallery floor boundary used by the new materializer. */
    public static boolean isBetweenLayerLowerFloor(int floorY) {
        return floorY >= MIN_BETWEEN_LAYER_LOWER_FLOOR_Y
                && floorY <= MAX_BETWEEN_LAYER_LOWER_FLOOR_Y;
    }

    /** Exact vertical-separation boundary for an ordinary natural layer change. */
    public static boolean isBetweenLayerSeparation(int separation) {
        return separation >= MIN_BETWEEN_LAYER_SEPARATION
                && separation <= MAX_BETWEEN_LAYER_SEPARATION;
    }

    /** Immutable vertical choice; the destination itself remains untouched natural evidence. */
    public record BetweenLayerDropPlan(
            int upperFloorY,
            int lowerGalleryFloorY,
            int verticalSeparation) {

        public BetweenLayerDropPlan {
            if (!isBetweenLayerUpperFloor(upperFloorY)
                    || !isBetweenLayerLowerFloor(lowerGalleryFloorY)
                    || verticalSeparation != upperFloorY - lowerGalleryFloorY
                    || !isBetweenLayerSeparation(verticalSeparation)) {
                throw new IllegalArgumentException("invalid between-layer drop plan");
            }
        }
    }

    /**
     * Chooses the deepest qualified lower gallery deterministically. Observation order and duplicate
     * candidates cannot change the result; an absent qualified gallery fails closed.
     */
    public static BetweenLayerDropPlan planBetweenLayerDrop(
            int upperFloorY, List<Integer> observedLowerFloors) {
        if (!isBetweenLayerUpperFloor(upperFloorY) || observedLowerFloors == null) {
            return null;
        }
        Integer deepest = observedLowerFloors.stream()
                .filter(Objects::nonNull)
                .filter(CaveDropTrap::isBetweenLayerLowerFloor)
                .filter(lower -> isBetweenLayerSeparation(upperFloorY - lower))
                .min(Integer::compareTo)
                .orElse(null);
        return deepest == null
                ? null
                : new BetweenLayerDropPlan(
                        upperFloorY, deepest, upperFloorY - deepest);
    }

    /**
     * One read-only natural-column profile. Element zero is {@code floorY + 1}; the final element is
     * {@code upperFloorY - 1}. The adapter separately proves that the floor and its three supports are
     * safe natural mass.
     */
    public record LowerColumnObservation(
            Cell cell,
            int floorY,
            boolean fourSafeSupports,
            List<ThroatBlockKind> blocksAboveThroughUpper,
            int firstUnsafeSupportDepth,
            ThroatBlockKind firstUnsafeSupportKind) {

        public LowerColumnObservation {
            blocksAboveThroughUpper = blocksAboveThroughUpper == null
                    ? List.of() : List.copyOf(blocksAboveThroughUpper);
            firstUnsafeSupportKind = firstUnsafeSupportKind == null
                    ? ThroatBlockKind.OTHER : firstUnsafeSupportKind;
        }

        public LowerColumnObservation(
                Cell cell,
                int floorY,
                boolean fourSafeSupports,
                List<ThroatBlockKind> blocksAboveThroughUpper) {
            this(
                    cell,
                    floorY,
                    fourSafeSupports,
                    blocksAboveThroughUpper,
                    fourSafeSupports ? -1 : 0,
                    fourSafeSupports ? ThroatBlockKind.SAFE_NATURAL
                            : ThroatBlockKind.OTHER);
        }
    }

    /** Exact first clause returned by the shared lower-column qualifier. */
    public enum LowerColumnFailureClause {
        PASSED,
        MISSING_COLUMN,
        UNSAFE_SUPPORT_MASS,
        LOWER_FLOOR_OUT_OF_BAND,
        SEPARATION_OUT_OF_BAND,
        INCOMPLETE_VERTICAL_PROFILE,
        INSUFFICIENT_NATURAL_AIR,
        MISSING_NATURAL_PLUG,
        UNSAFE_NATURAL_PLUG,
        NO_COHERENT_SIX_COLUMN_SURFACE,
        INVALID_FOOTPRINT
    }

    /** One lower-column decision plus the exact voxel fact that made it fail. */
    public record LowerColumnAssessment(
            LowerColumnPlan plan,
            LowerColumnFailureClause failureClause,
            Cell cell,
            int floorY,
            int failureY,
            ThroatBlockKind blockKind) {

        public LowerColumnAssessment {
            failureClause = Objects.requireNonNull(failureClause);
            blockKind = blockKind == null ? ThroatBlockKind.OTHER : blockKind;
            if ((plan != null) != (failureClause == LowerColumnFailureClause.PASSED)) {
                throw new IllegalArgumentException(
                        "only a passed lower-column assessment may carry a plan");
            }
        }

        public boolean feasible() {
            return plan != null;
        }
    }

    /** One selected landing column, including every untouched cave-air Y and the first authored plug Y. */
    public record LowerColumnPlan(
            Cell cell,
            int floorY,
            int plugBottomY,
            List<Integer> preservedAirYs) {

        public LowerColumnPlan {
            preservedAirYs = List.copyOf(preservedAirYs);
        }
    }

    /** A deterministic six-column lower interface; columns are ordered by local X then Z. */
    public record BetweenLayerSurfacePlan(
            int upperFloorY,
            List<LowerColumnPlan> columns) {

        public BetweenLayerSurfacePlan {
            columns = List.copyOf(columns);
        }

        public int minimumFloorY() {
            return columns.stream().mapToInt(LowerColumnPlan::floorY).min().orElseThrow();
        }

        public int maximumFloorY() {
            return columns.stream().mapToInt(LowerColumnPlan::floorY).max().orElseThrow();
        }

        public LowerColumnPlan column(Cell cell) {
            return columns.stream()
                    .filter(column -> column.cell().equals(cell))
                    .findFirst().orElse(null);
        }
    }

    /**
     * Returns every legal six-column surface in deterministic deepest-first order. A column must expose
     * at least two contiguous natural-air blocks, followed by a first safe-natural plug block and only
     * safe-natural plug mass through {@code upperFloorY - 1}. The footprint may step by one block, but
     * never has more than one block of total relief.
     */
    public static List<BetweenLayerSurfacePlan> planBetweenLayerSurfaces(
            int upperFloorY,
            List<Cell> footprint,
            List<LowerColumnObservation> observations) {
        if (!isBetweenLayerUpperFloor(upperFloorY)
                || footprint == null
                || observations == null
                || footprint.size() != MIN_PATCH_AREA
                || footprint.stream().anyMatch(Objects::isNull)
                || new HashSet<>(footprint).size() != MIN_PATCH_AREA) {
            return List.of();
        }
        Comparator<Cell> cellOrder = Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z);
        List<Cell> canonicalFootprint = footprint.stream().sorted(cellOrder).toList();
        if (!isFourConnected(canonicalFootprint)) {
            return List.of();
        }

        Map<Cell, Map<Integer, LowerColumnPlan>> qualified = new java.util.LinkedHashMap<>();
        for (Cell cell : canonicalFootprint) {
            qualified.put(cell, new TreeMap<>());
        }
        for (LowerColumnObservation observation : observations) {
            if (observation == null || !qualified.containsKey(observation.cell())) {
                continue;
            }
            LowerColumnPlan column = assessLowerColumn(upperFloorY, observation).plan();
            if (column != null) {
                qualified.get(observation.cell()).putIfAbsent(column.floorY(), column);
            }
        }
        if (qualified.values().stream().anyMatch(Map::isEmpty)) {
            return List.of();
        }

        List<BetweenLayerSurfacePlan> result = new ArrayList<>();
        for (int baseY = MIN_BETWEEN_LAYER_LOWER_FLOOR_Y;
                baseY <= MAX_BETWEEN_LAYER_LOWER_FLOOR_Y;
                baseY++) {
            List<List<LowerColumnPlan>> choices = new ArrayList<>();
            boolean complete = true;
            for (Cell cell : canonicalFootprint) {
                Map<Integer, LowerColumnPlan> byFloor = qualified.get(cell);
                List<LowerColumnPlan> cellChoices = new ArrayList<>(2);
                if (byFloor.containsKey(baseY)) {
                    cellChoices.add(byFloor.get(baseY));
                }
                if (byFloor.containsKey(baseY + 1)) {
                    cellChoices.add(byFloor.get(baseY + 1));
                }
                if (cellChoices.isEmpty()) {
                    complete = false;
                    break;
                }
                choices.add(List.copyOf(cellChoices));
            }
            if (!complete) {
                continue;
            }
            List<List<LowerColumnPlan>> candidates = new ArrayList<>();
            collectLowerSurfaces(choices, 0, new ArrayList<>(), candidates);
            int expectedBase = baseY;
            candidates.removeIf(columns -> columns.stream()
                            .mapToInt(LowerColumnPlan::floorY).min().orElseThrow() != expectedBase
                    || columns.stream().mapToInt(LowerColumnPlan::floorY).max().orElseThrow()
                            - expectedBase > 1
                    || !stepConnectedSurface(columns));
            candidates.sort((first, second) -> compareLowerSurfaces(first, second));
            for (List<LowerColumnPlan> columns : candidates) {
                result.add(new BetweenLayerSurfacePlan(upperFloorY, columns));
            }
        }
        return List.copyOf(result);
    }

    /** The first result from {@link #planBetweenLayerSurfaces}; absent evidence fails closed. */
    public static BetweenLayerSurfacePlan planBetweenLayerSurface(
            int upperFloorY,
            List<Cell> footprint,
            List<LowerColumnObservation> observations) {
        List<BetweenLayerSurfacePlan> plans =
                planBetweenLayerSurfaces(upperFloorY, footprint, observations);
        return plans.isEmpty() ? null : plans.getFirst();
    }

    /**
     * Runs the exact qualifier used by {@link #planBetweenLayerSurfaces} while retaining its first
     * failing clause and voxel. Diagnostics therefore cannot drift from the placement decision.
     */
    public static LowerColumnAssessment assessLowerColumn(
            int upperFloorY, LowerColumnObservation observation) {
        if (observation == null) {
            return rejectedLowerColumn(
                    LowerColumnFailureClause.MISSING_COLUMN,
                    null,
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE,
                    ThroatBlockKind.OTHER);
        }
        int floorY = observation.floorY();
        List<ThroatBlockKind> vertical = observation.blocksAboveThroughUpper();
        if (observation.cell() == null) {
            return rejectedLowerColumn(
                    LowerColumnFailureClause.MISSING_COLUMN,
                    null, floorY, floorY, ThroatBlockKind.OTHER);
        }
        if (!observation.fourSafeSupports()) {
            int depth = Math.max(0, observation.firstUnsafeSupportDepth());
            return rejectedLowerColumn(
                    LowerColumnFailureClause.UNSAFE_SUPPORT_MASS,
                    observation.cell(), floorY, floorY - depth,
                    observation.firstUnsafeSupportKind());
        }
        if (!isBetweenLayerLowerFloor(floorY)) {
            return rejectedLowerColumn(
                    LowerColumnFailureClause.LOWER_FLOOR_OUT_OF_BAND,
                    observation.cell(), floorY, floorY, ThroatBlockKind.OTHER);
        }
        if (!isBetweenLayerSeparation(upperFloorY - floorY)) {
            return rejectedLowerColumn(
                    LowerColumnFailureClause.SEPARATION_OUT_OF_BAND,
                    observation.cell(), floorY, floorY, ThroatBlockKind.OTHER);
        }
        if (vertical.size() != upperFloorY - floorY - 1) {
            int failureY = floorY + Math.min(vertical.size() + 1,
                    Math.max(1, upperFloorY - floorY - 1));
            return rejectedLowerColumn(
                    LowerColumnFailureClause.INCOMPLETE_VERTICAL_PROFILE,
                    observation.cell(), floorY, failureY, ThroatBlockKind.OTHER);
        }
        int airCount = 0;
        while (airCount < vertical.size()
                && vertical.get(airCount) == ThroatBlockKind.AIR) {
            airCount++;
        }
        if (airCount < MIN_GALLERY_AIR) {
            ThroatBlockKind kind = airCount < vertical.size()
                    ? vertical.get(airCount) : ThroatBlockKind.OTHER;
            return rejectedLowerColumn(
                    LowerColumnFailureClause.INSUFFICIENT_NATURAL_AIR,
                    observation.cell(), floorY, floorY + airCount + 1, kind);
        }
        if (airCount == vertical.size()) {
            return rejectedLowerColumn(
                    LowerColumnFailureClause.MISSING_NATURAL_PLUG,
                    observation.cell(), floorY, upperFloorY - 1,
                    ThroatBlockKind.AIR);
        }
        for (int index = airCount; index < vertical.size(); index++) {
            if (vertical.get(index) != ThroatBlockKind.SAFE_NATURAL) {
                return rejectedLowerColumn(
                        LowerColumnFailureClause.UNSAFE_NATURAL_PLUG,
                        observation.cell(), floorY, floorY + index + 1,
                        vertical.get(index));
            }
        }
        List<Integer> preservedAir = new ArrayList<>(airCount);
        for (int offset = 1; offset <= airCount; offset++) {
            preservedAir.add(floorY + offset);
        }
        LowerColumnPlan plan = new LowerColumnPlan(
                observation.cell(), floorY, floorY + airCount + 1, preservedAir);
        return new LowerColumnAssessment(
                plan,
                LowerColumnFailureClause.PASSED,
                observation.cell(),
                floorY,
                plan.plugBottomY(),
                ThroatBlockKind.SAFE_NATURAL);
    }

    private static LowerColumnAssessment rejectedLowerColumn(
            LowerColumnFailureClause clause,
            Cell cell,
            int floorY,
            int failureY,
            ThroatBlockKind kind) {
        return new LowerColumnAssessment(null, clause, cell, floorY, failureY, kind);
    }

    /**
     * Diagnoses an already-empty surface result. The first canonical column with no qualified
     * profile wins; if every column has one, the exact residual is cross-column step coherence.
     */
    public static LowerColumnAssessment diagnoseBetweenLayerSurfaceFailure(
            int upperFloorY,
            List<Cell> footprint,
            List<LowerColumnObservation> observations) {
        if (!isBetweenLayerUpperFloor(upperFloorY)
                || footprint == null
                || observations == null
                || footprint.size() != MIN_PATCH_AREA
                || footprint.stream().anyMatch(Objects::isNull)
                || new HashSet<>(footprint).size() != MIN_PATCH_AREA
                || !isFourConnected(footprint)) {
            Cell first = footprint == null || footprint.isEmpty() ? null : footprint.getFirst();
            return rejectedLowerColumn(
                    LowerColumnFailureClause.INVALID_FOOTPRINT,
                    first, Integer.MIN_VALUE, Integer.MIN_VALUE, ThroatBlockKind.OTHER);
        }
        Comparator<Cell> cellOrder = Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z);
        List<Cell> canonicalFootprint = footprint.stream().sorted(cellOrder).toList();
        Map<Cell, List<LowerColumnAssessment>> byCell = new java.util.LinkedHashMap<>();
        for (Cell cell : canonicalFootprint) {
            byCell.put(cell, new ArrayList<>());
        }
        for (LowerColumnObservation observation : observations) {
            if (observation != null && byCell.containsKey(observation.cell())) {
                byCell.get(observation.cell()).add(assessLowerColumn(upperFloorY, observation));
            }
        }
        for (Cell cell : canonicalFootprint) {
            List<LowerColumnAssessment> assessments = byCell.get(cell);
            if (assessments.stream().noneMatch(LowerColumnAssessment::feasible)) {
                return assessments.stream()
                        .min(Comparator.comparingInt(LowerColumnAssessment::floorY)
                                .thenComparingInt(assessment ->
                                        assessment.failureClause().ordinal())
                                .thenComparingInt(LowerColumnAssessment::failureY))
                        .orElseGet(() -> rejectedLowerColumn(
                                LowerColumnFailureClause.MISSING_COLUMN,
                                cell, Integer.MIN_VALUE, Integer.MIN_VALUE,
                                ThroatBlockKind.OTHER));
            }
        }
        LowerColumnAssessment firstQualified = byCell.get(canonicalFootprint.getFirst()).stream()
                .filter(LowerColumnAssessment::feasible)
                .min(Comparator.comparingInt(LowerColumnAssessment::floorY))
                .orElseThrow();
        return rejectedLowerColumn(
                LowerColumnFailureClause.NO_COHERENT_SIX_COLUMN_SURFACE,
                firstQualified.cell(),
                firstQualified.floorY(),
                firstQualified.floorY(),
                ThroatBlockKind.SAFE_NATURAL);
    }

    private static void collectLowerSurfaces(
            List<List<LowerColumnPlan>> choices,
            int index,
            List<LowerColumnPlan> selected,
            List<List<LowerColumnPlan>> result) {
        if (index == choices.size()) {
            result.add(List.copyOf(selected));
            return;
        }
        for (LowerColumnPlan choice : choices.get(index)) {
            selected.add(choice);
            collectLowerSurfaces(choices, index + 1, selected, result);
            selected.removeLast();
        }
    }

    private static boolean stepConnectedSurface(List<LowerColumnPlan> columns) {
        if (columns.size() != MIN_PATCH_AREA) {
            return false;
        }
        Map<Cell, Integer> floors = new java.util.LinkedHashMap<>();
        for (LowerColumnPlan column : columns) {
            if (floors.put(column.cell(), column.floorY()) != null) {
                return false;
            }
        }
        Set<Cell> unseen = new HashSet<>(floors.keySet());
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Cell start = columns.getFirst().cell();
        unseen.remove(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            for (Cell next : neighbours(current)) {
                if (unseen.contains(next)
                        && Math.abs(floors.get(current) - floors.get(next)) <= 1) {
                    unseen.remove(next);
                    queue.addLast(next);
                }
            }
        }
        return unseen.isEmpty();
    }

    private static int compareLowerSurfaces(
            List<LowerColumnPlan> first, List<LowerColumnPlan> second) {
        int firstSum = first.stream().mapToInt(LowerColumnPlan::floorY).sum();
        int secondSum = second.stream().mapToInt(LowerColumnPlan::floorY).sum();
        int comparison = Integer.compare(firstSum, secondSum);
        if (comparison != 0) {
            return comparison;
        }
        for (int index = 0; index < first.size(); index++) {
            comparison = Integer.compare(
                    first.get(index).floorY(), second.get(index).floorY());
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /**
     * Plans the vertical part of an ordinary embedded descent. This deliberately rejects the former
     * overhang grammar before returning a top-down shaft, solid cushion target, and upper dry-exit level.
     */
    public static EmbeddedDropPlan planEmbeddedDrop(
            int galleryAirAbove,
            int certifiedSolidDepth,
            boolean preexistingVoidBelow,
            int floorY,
            int requestedDrop) {
        if (!cellQualifies(galleryAirAbove, certifiedSolidDepth, preexistingVoidBelow)
                || requestedDrop < MIN_DROP_AIR
                || requestedDrop > MAX_ORDINARY_DROP_AIR) {
            return null;
        }
        int landingY = floorY - requestedDrop;
        List<Integer> shaft = new ArrayList<>();
        for (int y = floorY - 1; y > landingY; y--) {
            shaft.add(y);
        }
        return new EmbeddedDropPlan(
                floorY, landingY, shaft, landingY, floorY + 1);
    }

    /** The per-patch fraction gate: {@code 0 <= roll01 < }{@link #TRAP_FRACTION}; out-of-range never traps
     *  (the safe direction is "leave the floor honest"). */
    public static boolean shouldTrapPatch(float roll01) {
        return roll01 >= 0.0f && roll01 < TRAP_FRACTION;
    }

    /** May two vertically-nearby drop cells join one patch? Their floor tops must sit within one block --
     *  a single coherent floor panel, never a stair-stepped diagonal tear across gallery levels. */
    public static boolean cellsJoinPatch(int floorYa, int floorYb) {
        return Math.abs(floorYa - floorYb) <= 1;
    }

    /**
     * Selects the floor that belongs to one active approach-level carpet. An approach may legitimately
     * include a one-block floor undulation, but a second cave floor in the same world column must never
     * win merely because it appeared first in the vertical scan.
     */
    public static Integer selectApproachFloor(
            List<Integer> candidateFloorYs, int approachFloorY) {
        if (candidateFloorYs == null) {
            return null;
        }
        return candidateFloorYs.stream()
                .filter(Objects::nonNull)
                .filter(floorY -> floorY == approachFloorY
                        || floorY == approachFloorY + 1)
                .min(Comparator.comparingInt(floorY -> floorY - approachFloorY))
                .orElse(null);
    }

    /** Does a connected deep-gallery footprint materially exceed the trap entrance? */
    public static boolean floodedGalleryQualifies(int entranceArea, int galleryArea) {
        return entranceArea >= MIN_PATCH_AREA
                && galleryArea >= MIN_FLOODED_GALLERY_FOOTPRINT
                && galleryArea >= entranceArea + MIN_FLOODED_GALLERY_ENTRANCE_MARGIN;
    }

    /**
     * Does every distinct column in the complete template remain meaningfully below the maintained surface?
     * The caller supplies feature-stage first-air heights from the real world; one high ice-spire column can
     * never rescue an otherwise exposed footprint.
     */
    public static boolean caveEnclosureQualifies(
            int floorY,
            List<Cell> enclosureColumns,
            ToIntFunction<Cell> maintainedSurfaceFirstAir) {
        if (enclosureColumns == null || enclosureColumns.isEmpty()
                || maintainedSurfaceFirstAir == null
                || new HashSet<>(enclosureColumns).size() != enclosureColumns.size()) {
            return false;
        }
        int requiredFirstAir = floorY + MIN_CAVE_ENCLOSURE_RISE;
        return enclosureColumns.stream()
                .allMatch(cell -> maintainedSurfaceFirstAir.applyAsInt(cell) >= requiredFirstAir);
    }

    /**
     * Exact untouched evidence volume for named natural floor: four safe-natural support voxels beginning
     * at the floor, plus the two existing-air headroom voxels above it.
     */
    public static Set<Voxel> naturalFloorEvidenceEnvelope(List<Voxel> floors) {
        if (floors == null || floors.isEmpty() || floors.stream().anyMatch(Objects::isNull)) {
            return Set.of();
        }
        LinkedHashSet<Voxel> protectedVoxels = new LinkedHashSet<>();
        for (Voxel floor : floors) {
            for (int offset = 0; offset < APPROACH_CONTINUATION_SUPPORT_DEPTH; offset++) {
                protectedVoxels.add(new Voxel(floor.x(), floor.y() - offset, floor.z()));
            }
            for (int offset = 1; offset <= MIN_GALLERY_AIR; offset++) {
                protectedVoxels.add(new Voxel(floor.x(), floor.y() + offset, floor.z()));
            }
        }
        return Set.copyOf(protectedVoxels);
    }

    /** A rising escape step may neither stand in nor clear/support itself with named natural evidence. */
    public static boolean escapeStandingPreservesEvidence(
            Voxel standing, Set<Voxel> protectedEvidence) {
        return standing != null
                && protectedEvidence != null
                && !protectedEvidence.contains(standing.below())
                && !protectedEvidence.contains(standing)
                && !protectedEvidence.contains(standing.above());
    }

    /** Compatibility view of the certified lower-branch planner's active powder stations. */
    public static List<Cell> planActiveStations(List<Cell> carpetCells) {
        CertifiedBranchPlan plan = planCertifiedBranches(carpetCells, carpetCells);
        return plan == null ? List.of() : plan.activePowderCells();
    }

    /** One integer world-space cell for pure projected-route certification. */
    public record Voxel(int x, int y, int z) {

        public Voxel above() {
            return new Voxel(x, y + 1, z);
        }

        public Voxel below() {
            return new Voxel(x, y - 1, z);
        }
    }

    /**
     * Certifies dry routes against one combined projected scene. Index zero is the powder cushion;
     * every later node needs two-block headroom and a support cell that remains solid after every
     * branch and stair write has been unioned.
     */
    public static boolean dryRoutesRemainSupported(
            List<List<Voxel>> routes,
            Predicate<Voxel> projectedAir,
            Predicate<Voxel> projectedSolidSupport) {
        if (routes == null || routes.isEmpty()
                || projectedAir == null || projectedSolidSupport == null) {
            return false;
        }
        for (List<Voxel> route : routes) {
            if (route == null || route.size() < 2) {
                return false;
            }
            for (int index = 1; index < route.size(); index++) {
                Voxel standing = route.get(index);
                if (!projectedAir.test(standing)
                        || !projectedAir.test(standing.above())
                        || !projectedSolidSupport.test(standing.below())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Builds and certifies the one complete embedded false-floor ribbon used by production. The
     * predicates inspect local template voxels: every required solid must be safe natural mass, while
     * every required air voxel must already be clear cave headroom. A thin roof or partial envelope
     * therefore returns {@code null}; the planner never searches for a pre-existing lower void. Both
     * opposed banks must continue onto untouched natural cave floor, and the authored stair must join
     * that floor through an untouched upper return rather than ending in a sealed landing pocket.
     */
    public static EmbeddedRibbonPlan planEmbeddedRibbon(
            int floorY,
            RibbonOrientation orientation,
            Predicate<Voxel> safeNaturalSolid,
            Predicate<Voxel> existingAir) {
        if (orientation == null || safeNaturalSolid == null || existingAir == null) {
            return null;
        }
        int landingY = floorY - MIN_DROP_AIR;
        List<Cell> powder = orient(canonicalRibbonPowder(), orientation);
        List<Cell> rail = orient(canonicalRibbonRail(), orientation);
        boolean reversesNamedApproaches =
                orientation == RibbonOrientation.EAST
                        || orientation == RibbonOrientation.SOUTH;
        List<Cell> firstBank = orient(
                reversesNamedApproaches ? canonicalSecondBank() : canonicalFirstBank(),
                orientation);
        List<Cell> secondBank = orient(
                reversesNamedApproaches ? canonicalFirstBank() : canonicalSecondBank(),
                orientation);
        List<Cell> firstContinuation =
                orient(
                        reversesNamedApproaches
                                ? canonicalSecondContinuation()
                                : canonicalFirstContinuation(),
                        orientation);
        List<Cell> secondContinuation =
                orient(
                        reversesNamedApproaches
                                ? canonicalFirstContinuation()
                                : canonicalSecondContinuation(),
                        orientation);
        List<Cell> exitBank = orient(canonicalExitBank(), orientation);
        List<Cell> stair = orient(canonicalStair(), orientation);
        List<Cell> upperReturn = orient(canonicalUpperReturn(), orientation);
        ProspectorLayout prospector = orient(canonicalProspectorLayout(), orientation);
        List<RibbonEntry> entries = canonicalRibbonEntries().stream()
                .map(entry -> new RibbonEntry(
                        orient(entry.powder(), orientation),
                        orient(entry.corridor(), orientation)))
                .toList();
        LinkedHashSet<Cell> enclosure = new LinkedHashSet<>();
        enclosure.addAll(powder);
        enclosure.addAll(rail);
        enclosure.addAll(firstContinuation);
        enclosure.addAll(secondContinuation);
        enclosure.addAll(exitBank);
        enclosure.addAll(stair);
        enclosure.addAll(upperReturn);

        Set<Cell> powderSet = new LinkedHashSet<>(powder);
        Set<Cell> railSet = new LinkedHashSet<>(rail);
        Set<Cell> prospectorPositions = new HashSet<>(List.of(
                prospector.cushion(),
                prospector.foot(),
                prospector.head(),
                prospector.chest(),
                prospector.front()));
        int bodyDx = prospector.head().x() - prospector.foot().x();
        int bodyDz = prospector.head().z() - prospector.foot().z();
        int chestDx = prospector.chest().x() - prospector.head().x();
        int chestDz = prospector.chest().z() - prospector.head().z();
        int frontDx = prospector.front().x() - prospector.chest().x();
        int frontDz = prospector.front().z() - prospector.chest().z();
        if (powder.size() < MIN_PATCH_AREA
                || powder.size() > PATCH_MAX_AREA
                || powderSet.size() != powder.size()
                || railSet.size() != rail.size()
                || !isFourConnected(powder)
                || !isIrregular(powder)
                || !isFourConnected(rail)
                || !railSet.containsAll(firstBank)
                || !railSet.containsAll(secondBank)
                || firstBank.size() < MIN_APPROACH_BANK_RUN
                || secondBank.size() < MIN_APPROACH_BANK_RUN
                || !isFourConnected(firstBank)
                || !isFourConnected(secondBank)
                || projectedBankOverlap(firstBank, secondBank, orientation.axis())
                        < MIN_APPROACH_BANK_RUN
                || !bankBordersPowder(
                        firstBank, powderSet, orientation.axis(), true)
                || !bankBordersPowder(
                        secondBank, powderSet, orientation.axis(), false)
                || firstContinuation.size() < MIN_APPROACH_CONTINUATION_AREA
                || secondContinuation.size() < MIN_APPROACH_CONTINUATION_AREA
                || !isFourConnected(firstContinuation)
                || !isFourConnected(secondContinuation)
                || !touches(firstContinuation, firstBank)
                || !touches(secondContinuation, secondBank)
                || !railSet.contains(stair.getFirst())
                || !exitBank.contains(stair.getLast())
                || !isFourConnected(upperReturn)
                || !exitBank.contains(upperReturn.getFirst())
                || manhattan(stair.getLast(), upperReturn.getFirst()) != 1
                || (!firstContinuation.contains(upperReturn.getLast())
                        && !secondContinuation.contains(upperReturn.getLast()))
                || powder.stream().anyMatch(railSet::contains)
                || firstContinuation.stream().anyMatch(powderSet::contains)
                || firstContinuation.stream().anyMatch(railSet::contains)
                || secondContinuation.stream().anyMatch(powderSet::contains)
                || secondContinuation.stream().anyMatch(railSet::contains)
                || upperReturn.stream().anyMatch(powderSet::contains)
                || upperReturn.stream().anyMatch(railSet::contains)
                || upperReturn.stream().anyMatch(stair::contains)
                || entries.size() != powder.size()
                || enclosure.isEmpty()
                || prospectorPositions.size() != 5
                || !powderSet.contains(prospector.cushion())
                || !railSet.containsAll(List.of(
                        prospector.foot(),
                        prospector.head(),
                        prospector.chest(),
                        prospector.front()))
                || manhattan(prospector.cushion(), prospector.foot()) != 1
                || manhattan(prospector.foot(), prospector.head()) != 1
                || manhattan(prospector.head(), prospector.chest()) != 1
                || manhattan(prospector.chest(), prospector.front()) != 1
                || chestDx != frontDx
                || chestDz != frontDz
                || bodyDx * chestDx + bodyDz * chestDz != 0) {
            return null;
        }
        Set<Cell> witnessed = new HashSet<>();
        for (RibbonEntry entry : entries) {
            if (!powderSet.contains(entry.powder())
                    || !railSet.contains(entry.corridor())
                    || manhattan(entry.powder(), entry.corridor()) != 1
                    || !witnessed.add(entry.powder())) {
                return null;
            }
        }
        Set<Cell> prospectorBlocked = Set.of(
                prospector.foot(), prospector.head(), prospector.chest());
        Set<Cell> entryToFront = new HashSet<>(powderSet);
        entryToFront.addAll(railSet);
        entryToFront.removeAll(prospectorBlocked);
        if (!entryToFront.contains(prospector.front())
                || powder.stream().anyMatch(
                        cell -> connectedPath(cell, prospector.front(), entryToFront) == null)) {
            return null;
        }
        Set<Cell> frontToStair = new HashSet<>(railSet);
        frontToStair.removeAll(prospectorBlocked);
        if (connectedPath(prospector.front(), stair.getFirst(), frontToStair) == null) {
            return null;
        }

        LinkedHashSet<Voxel> requiredSolid = new LinkedHashSet<>();
        LinkedHashSet<Voxel> requiredAir = new LinkedHashSet<>();
        LinkedHashSet<Cell> fullDepthColumns = new LinkedHashSet<>(powder);
        fullDepthColumns.addAll(rail);
        for (Cell cell : fullDepthColumns) {
            for (int y = floorY;
                    y >= floorY - MIN_CERTIFIED_SOLID_DEPTH + 1;
                    y--) {
                requiredSolid.add(voxel(cell, y));
            }
            requiredAir.add(voxel(cell, floorY + 1));
            requiredAir.add(voxel(cell, floorY + 2));
        }
        for (Cell cell : exitBank) {
            for (int y = floorY; y >= floorY - 3; y--) {
                requiredSolid.add(voxel(cell, y));
            }
            requiredAir.add(voxel(cell, floorY + 1));
            requiredAir.add(voxel(cell, floorY + 2));
        }
        LinkedHashSet<Cell> naturalContinuations = new LinkedHashSet<>(firstContinuation);
        naturalContinuations.addAll(secondContinuation);
        for (Cell cell : naturalContinuations) {
            for (int y = floorY;
                    y >= floorY - APPROACH_CONTINUATION_SUPPORT_DEPTH + 1;
                    y--) {
                requiredSolid.add(voxel(cell, y));
            }
            requiredAir.add(voxel(cell, floorY + 1));
            requiredAir.add(voxel(cell, floorY + 2));
        }
        for (Cell cell : upperReturn) {
            for (int y = floorY;
                    y >= floorY - APPROACH_CONTINUATION_SUPPORT_DEPTH + 1;
                    y--) {
                requiredSolid.add(voxel(cell, y));
            }
            requiredAir.add(voxel(cell, floorY + 1));
            requiredAir.add(voxel(cell, floorY + 2));
        }
        for (int index = 1; index < stair.size() - 1; index++) {
            Cell cell = stair.get(index);
            int standingY = landingY + index;
            for (int y = floorY; y >= standingY - 4; y--) {
                requiredSolid.add(voxel(cell, y));
            }
            if (standingY + 1 > floorY) {
                requiredAir.add(voxel(cell, standingY + 1));
            }
        }
        if (requiredSolid.stream().anyMatch(requiredAir::contains)
                || requiredSolid.stream().anyMatch(solid -> !safeNaturalSolid.test(solid))
                || requiredAir.stream().anyMatch(air -> !existingAir.test(air))) {
            return null;
        }

        List<List<Voxel>> routes = new ArrayList<>();
        for (RibbonEntry entry : entries) {
            List<Cell> corridorPath =
                    connectedPath(entry.corridor(), stair.getFirst(), railSet);
            if (corridorPath == null) {
                return null;
            }
            List<Voxel> route = new ArrayList<>();
            route.add(voxel(entry.powder(), landingY));
            corridorPath.forEach(cell -> route.add(voxel(cell, landingY)));
            for (int index = 1; index < stair.size(); index++) {
                route.add(voxel(stair.get(index), landingY + index));
            }
            upperReturn.forEach(cell -> route.add(voxel(cell, floorY + 1)));
            routes.add(List.copyOf(route));
        }

        return new EmbeddedRibbonPlan(
                orientation,
                orientation.axis(),
                floorY,
                landingY,
                powder,
                rail,
                firstBank,
                secondBank,
                firstContinuation,
                secondContinuation,
                rail,
                exitBank,
                stair,
                upperReturn,
                List.copyOf(enclosure),
                prospector,
                entries,
                List.copyOf(requiredSolid),
                List.copyOf(requiredAir),
                List.copyOf(routes));
    }

    /** One local owner-grid cell. */
    public record Cell(int x, int z) {
    }

    /** One exact active entrance-to-certified-branch relationship. */
    public record BranchWitness(Cell entry, Cell branch) {
    }

    /**
     * A deterministic partition of an already-legal carpet. Active powder remains a connected,
     * irregular 6..40-cell stumble surface. Reserved branch cells form a connected firm-snow network,
     * and every active entry names one horizontally adjacent branch witness.
     */
    public record CertifiedBranchPlan(
            List<Cell> activePowderCells,
            List<Cell> reservedBranchCells,
            List<BranchWitness> witnesses) {

        public CertifiedBranchPlan {
            activePowderCells = List.copyOf(activePowderCells);
            reservedBranchCells = List.copyOf(reservedBranchCells);
            witnesses = List.copyOf(witnesses);
        }
    }

    /**
     * Partitions a legal candidate carpet into active powder and certified firm branch columns.
     *
     * <p>Only cells explicitly present in {@code certifiedBranchCells} may be demoted into the lower
     * dry-gallery network. An arbitrary neighbouring column is never accepted. The branch network and
     * remaining powder are both connected, every active entry touches the network, and the remaining
     * powder retains the original six-cell and irregularity laws.
     */
    public static CertifiedBranchPlan planCertifiedBranches(
            List<Cell> carpetCells, List<Cell> certifiedBranchCells) {
        if (carpetCells == null || certifiedBranchCells == null) {
            return null;
        }
        Comparator<Cell> order = Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z);
        List<Cell> carpet = carpetCells.stream().filter(Objects::nonNull).distinct()
                .sorted(order).toList();
        if (carpet.size() != carpetCells.size()
                || carpet.size() <= MIN_PATCH_AREA
                || carpet.size() > PATCH_MAX_AREA
                || !isFourConnected(carpet)
                || !isIrregular(carpet)) {
            return null;
        }

        Set<Cell> carpetSet = new HashSet<>(carpet);
        Set<Cell> certified = new HashSet<>();
        for (Cell cell : certifiedBranchCells) {
            if (cell != null && carpetSet.contains(cell)) {
                certified.add(cell);
            }
        }
        if (certified.isEmpty()) {
            return null;
        }

        int size = carpet.size();
        long fullMask = (1L << size) - 1L;
        long certifiedMask = 0L;
        long[] adjacency = new long[size];
        for (int index = 0; index < size; index++) {
            if (certified.contains(carpet.get(index))) {
                certifiedMask |= 1L << index;
            }
            for (int neighbour = 0; neighbour < size; neighbour++) {
                if (manhattan(carpet.get(index), carpet.get(neighbour)) == 1) {
                    adjacency[index] |= 1L << neighbour;
                }
            }
        }

        List<Integer> seeds = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            if ((certifiedMask & (1L << index)) != 0L) {
                seeds.add(index);
            }
        }
        seeds.sort(Comparator
                .comparingInt((Integer index) -> -Long.bitCount(adjacency[index]))
                .thenComparing(index -> carpet.get(index), order));

        Set<Long> visited = new HashSet<>();
        int[] budget = {MAX_CERTIFIED_BRANCH_SEARCH_STATES};
        Long branchMask = null;
        for (int seed : seeds) {
            long trial = 1L << seed;
            branchMask = searchCertifiedBranchNetwork(
                    carpet, adjacency, fullMask, certifiedMask,
                    trial, visited, budget);
            if (branchMask != null || budget[0] <= 0) {
                break;
            }
        }
        if (branchMask == null) {
            return null;
        }

        long activeMask = fullMask & ~branchMask;
        List<Cell> active = cellsFromMask(carpet, activeMask);
        List<Cell> branches = cellsFromMask(carpet, branchMask);
        List<BranchWitness> witnesses = new ArrayList<>();
        for (int entry = 0; entry < size; entry++) {
            long entryBit = 1L << entry;
            if ((activeMask & entryBit) == 0L) {
                continue;
            }
            long choices = adjacency[entry] & branchMask;
            if (choices == 0L) {
                return null;
            }
            int branch = Long.numberOfTrailingZeros(choices);
            witnesses.add(new BranchWitness(carpet.get(entry), carpet.get(branch)));
        }
        return new CertifiedBranchPlan(active, branches, witnesses);
    }

    private static Long searchCertifiedBranchNetwork(
            List<Cell> carpet,
            long[] adjacency,
            long fullMask,
            long certifiedMask,
            long branchMask,
            Set<Long> visited,
            int[] budget) {
        if (budget[0]-- <= 0 || !visited.add(branchMask)) {
            return null;
        }
        long activeMask = fullMask & ~branchMask;
        if (Long.bitCount(activeMask) < MIN_PATCH_AREA
                || !maskConnected(activeMask, adjacency)) {
            return null;
        }

        long uncovered = 0L;
        long activeCursor = activeMask;
        while (activeCursor != 0L) {
            int entry = Long.numberOfTrailingZeros(activeCursor);
            long bit = 1L << entry;
            if ((adjacency[entry] & branchMask) == 0L) {
                uncovered |= bit;
            }
            activeCursor &= ~bit;
        }
        if (uncovered == 0L
                && isIrregular(cellsFromMask(carpet, activeMask))) {
            return branchMask;
        }
        if (Long.bitCount(activeMask) == MIN_PATCH_AREA) {
            return null;
        }

        long frontier = 0L;
        long branchCursor = branchMask;
        while (branchCursor != 0L) {
            int branch = Long.numberOfTrailingZeros(branchCursor);
            long bit = 1L << branch;
            frontier |= adjacency[branch];
            branchCursor &= ~bit;
        }
        frontier &= activeMask & certifiedMask;

        List<Integer> additions = new ArrayList<>();
        while (frontier != 0L) {
            int index = Long.numberOfTrailingZeros(frontier);
            long bit = 1L << index;
            long remaining = activeMask & ~bit;
            if (Long.bitCount(remaining) >= MIN_PATCH_AREA
                    && maskConnected(remaining, adjacency)) {
                additions.add(index);
            }
            frontier &= ~bit;
        }
        long uncoveredMask = uncovered;
        additions.sort(Comparator
                .comparingInt((Integer index) ->
                        -(Long.bitCount(adjacency[index] & uncoveredMask)
                                + (((uncoveredMask & (1L << index)) != 0L) ? 1 : 0)))
                .thenComparing(index -> carpet.get(index),
                        Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z)));
        for (int addition : additions) {
            Long result = searchCertifiedBranchNetwork(
                    carpet, adjacency, fullMask, certifiedMask,
                    branchMask | (1L << addition), visited, budget);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static boolean maskConnected(long mask, long[] adjacency) {
        if (mask == 0L) {
            return false;
        }
        long visited = Long.lowestOneBit(mask);
        long frontier = visited;
        while (frontier != 0L) {
            int index = Long.numberOfTrailingZeros(frontier);
            long bit = 1L << index;
            long next = adjacency[index] & mask & ~visited;
            visited |= next;
            frontier = (frontier & ~bit) | next;
        }
        return visited == mask;
    }

    private static List<Cell> cellsFromMask(List<Cell> cells, long mask) {
        List<Cell> selected = new ArrayList<>();
        for (int index = 0; index < cells.size(); index++) {
            if ((mask & (1L << index)) != 0L) {
                selected.add(cells.get(index));
            }
        }
        return List.copyOf(selected);
    }

    /** The opposed pair of supported ordinary-floor banks that makes a carpet walk-through terrain. */
    public enum ApproachAxis {
        NORTH_SOUTH,
        EAST_WEST
    }

    /** Four deterministic rotations of the same embedded false-floor template. */
    public enum RibbonOrientation {
        NORTH(ApproachAxis.NORTH_SOUTH),
        EAST(ApproachAxis.EAST_WEST),
        SOUTH(ApproachAxis.NORTH_SOUTH),
        WEST(ApproachAxis.EAST_WEST);

        private final ApproachAxis axis;

        RibbonOrientation(ApproachAxis axis) {
            this.axis = axis;
        }

        public ApproachAxis axis() {
            return axis;
        }
    }

    /** One active ribbon cell and its predetermined adjacent lower-corridor cell. */
    public record RibbonEntry(Cell powder, Cell corridor) {
    }

    /**
     * Complete deterministic lower-corridor composition for the lost prospector. Entry, body, and chest
     * directions are deliberately independent relationships rather than one overloaded facing.
     */
    public record ProspectorLayout(
            Cell cushion,
            Cell foot,
            Cell head,
            Cell chest,
            Cell front) {
    }

    /** Complete local geometry and preflight evidence for one embedded ribbon. */
    public record EmbeddedRibbonPlan(
            RibbonOrientation orientation,
            ApproachAxis approachAxis,
            int floorY,
            int landingY,
            List<Cell> powderCells,
            List<Cell> firmRailCells,
            List<Cell> firstApproachBank,
            List<Cell> secondApproachBank,
            List<Cell> firstNaturalContinuation,
            List<Cell> secondNaturalContinuation,
            List<Cell> lowerCorridorCells,
            List<Cell> upperExitBankCells,
            List<Cell> stairCells,
            List<Cell> upperReturnPathCells,
            List<Cell> enclosureColumns,
            ProspectorLayout prospectorLayout,
            List<RibbonEntry> entries,
            List<Voxel> requiredSolidVoxels,
            List<Voxel> requiredAirVoxels,
            List<List<Voxel>> dryRoutes) {

        public EmbeddedRibbonPlan {
            powderCells = List.copyOf(powderCells);
            firmRailCells = List.copyOf(firmRailCells);
            firstApproachBank = List.copyOf(firstApproachBank);
            secondApproachBank = List.copyOf(secondApproachBank);
            firstNaturalContinuation = List.copyOf(firstNaturalContinuation);
            secondNaturalContinuation = List.copyOf(secondNaturalContinuation);
            lowerCorridorCells = List.copyOf(lowerCorridorCells);
            upperExitBankCells = List.copyOf(upperExitBankCells);
            stairCells = List.copyOf(stairCells);
            upperReturnPathCells = List.copyOf(upperReturnPathCells);
            enclosureColumns = List.copyOf(enclosureColumns);
            prospectorLayout = Objects.requireNonNull(prospectorLayout);
            entries = List.copyOf(entries);
            requiredSolidVoxels = List.copyOf(requiredSolidVoxels);
            requiredAirVoxels = List.copyOf(requiredAirVoxels);
            dryRoutes = dryRoutes.stream().map(List::copyOf).toList();
        }
    }

    /**
     * Pass-A's immutable two-mouth roofed-gallery throat. This is deliberately separate from
     * {@link EmbeddedRibbonPlan}: the census may measure the future topology without changing the
     * currently shipped placement path.
     */
    public record RoofedGalleryThroatPlan(
            RibbonOrientation orientation,
            int floorY,
            int landingY,
            List<Cell> coverCells,
            List<Cell> powderCells,
            List<Cell> firmCells,
            List<List<Cell>> firmBanks,
            List<Cell> approachA,
            List<Cell> returnB,
            List<Cell> relevantFloorColumns,
            List<Voxel> continuationFloorVoxels,
            List<Voxel> headroomVoxels,
            List<Voxel> ordinaryAuthoredVoxels,
            List<Voxel> carvedAirVoxels,
            List<Voxel> firmSupportVoxels,
            List<Voxel> cushionSupportVoxels,
            List<Voxel> lateralShellVoxels,
            List<Voxel> lowerRouteStandingVoxels,
            List<Voxel> stairStandingVoxels,
            List<Voxel> ownerEnvelopeVoxels) {

        public RoofedGalleryThroatPlan {
            coverCells = List.copyOf(coverCells);
            powderCells = List.copyOf(powderCells);
            firmCells = List.copyOf(firmCells);
            firmBanks = firmBanks.stream().map(List::copyOf).toList();
            approachA = List.copyOf(approachA);
            returnB = List.copyOf(returnB);
            relevantFloorColumns = List.copyOf(relevantFloorColumns);
            continuationFloorVoxels = List.copyOf(continuationFloorVoxels);
            headroomVoxels = List.copyOf(headroomVoxels);
            ordinaryAuthoredVoxels = List.copyOf(ordinaryAuthoredVoxels);
            carvedAirVoxels = List.copyOf(carvedAirVoxels);
            firmSupportVoxels = List.copyOf(firmSupportVoxels);
            cushionSupportVoxels = List.copyOf(cushionSupportVoxels);
            lateralShellVoxels = List.copyOf(lateralShellVoxels);
            lowerRouteStandingVoxels = List.copyOf(lowerRouteStandingVoxels);
            stairStandingVoxels = List.copyOf(stairStandingVoxels);
            ownerEnvelopeVoxels = List.copyOf(ownerEnvelopeVoxels);
        }

        /** Locked Pass-A schema value, ordered from mouth A to mouth B. */
        public List<Integer> firmBankSizes() {
            return firmBanks.stream().map(List::size).toList();
        }
    }

    /** World observations accepted by the pure shared Pass-A predicate. */
    public enum ThroatBlockKind {
        SAFE_NATURAL,
        /** Explicitly unsafe for the lower glacial destination, even when a broad carver tag matches. */
        DEEPSLATE,
        AIR,
        /** Latitude's non-structural hanging ice decoration; never floor, support, or roof mass. */
        HANGING_ICICLE,
        FLUID,
        GRAVEL,
        ORE_OR_BLOCK_ENTITY,
        PROTECTED,
        OTHER
    }

    /**
     * Read-only world adapter for the shared predicate. Implementations may translate the local
     * template voxels to Minecraft positions, but the predicate itself remains pure Java.
     */
    public interface RoofedThroatWorldView {
        ThroatBlockKind blockKind(Voxel voxel);

        boolean insideOwnerInset(Voxel voxel);

        boolean authored(Voxel voxel);

        boolean seesSky(Voxel voxel);

        int minimumBuildY();
    }

    /** One and only one terminal structural bucket for every unique Pass-A anchor. */
    public enum PassARejection {
        PASS,
        SAME_DOOR,
        INVALID_OR_SELF_AUTHORED_WITNESS,
        EXPOSED_SKY_WATER_OR_THIN_ROOF,
        SEAFLOOR_OR_FLUID_STANDING,
        FIRM_BYPASS_OR_WRONG_ROLES,
        CAVITY_AIR_OR_FLUID,
        ORE_BLOCK_ENTITY_OR_PROTECTED,
        GRAVITY_OR_PARTIAL_STABILIZATION,
        SHELL_OR_SUPPORT,
        OWNER,
        DISCONNECTED_LOWER_OR_REWARD
    }

    /** Branch-local reason that a rare outcome must resolve through the ordinary fallback. */
    public enum RewardFeasibilityFailure {
        NONE,
        NOT_EVALUATED,
        REAL_PLANNER_REJECTED,
        BUILD_LIMIT,
        OWNER,
        AIR_OR_FLUID,
        ORE_BLOCK_ENTITY_OR_PROTECTED,
        GRAVITY_OR_PARTIAL_STABILIZATION
    }

    /** Shared structural result consumed unchanged by the census and a future placement pass. */
    public record RoofedThroatEvaluation(
            PassARejection rejection,
            boolean ordinaryFeasible,
            boolean floodedFeasible,
            RewardFeasibilityFailure floodedFailure,
            boolean prospectorFeasible,
            RewardFeasibilityFailure prospectorFailure) {
    }

    /** Upper passage result for the separate natural between-layer materializer. */
    public record UpperThroatEvaluation(
            PassARejection rejection,
            List<Voxel> naturalWitnessVoxels) {

        public UpperThroatEvaluation {
            rejection = Objects.requireNonNull(rejection);
            naturalWitnessVoxels = List.copyOf(naturalWitnessVoxels);
        }

        public boolean feasible() {
            return rejection == PassARejection.PASS;
        }
    }

    /**
     * Exact selector trace for an already feasible unique anchor. It calls the established fraction,
     * cap, reward-roll and reward-plan seams rather than restating their logic in census code.
     */
    public record PassAPrediction(
            boolean fractionAccepted,
            boolean capAccepted,
            int rewardRoll,
            RewardKind requestedKind,
            RewardKind resolvedKind,
            boolean ordinaryFallback) {
    }

    /** Measured facts from one returned throat plan; absent census rows carry no instance. */
    public record RoofedThroatMeasurements(
            int coverCount,
            int powderCount,
            int firmCount,
            List<Integer> firmComponentSizes,
            int mouthCount,
            boolean distinctMouths,
            boolean coverConnected,
            boolean powderConnected,
            boolean firmRolesCorrect,
            boolean powderVertexCut,
            boolean witnessWriteDisjoint) {

        public RoofedThroatMeasurements {
            firmComponentSizes = List.copyOf(firmComponentSizes);
        }

        public boolean lockedTopologyMatches() {
            return coverCount == 12
                    && powderCount == 6
                    && firmCount == 6
                    && firmComponentSizes.equals(List.of(3, 3))
                    && mouthCount == 2
                    && distinctMouths
                    && coverConnected
                    && powderConnected
                    && firmRolesCorrect
                    && powderVertexCut
                    && witnessWriteDisjoint;
        }
    }

    /**
     * Builds the locked Pass-A throat: one 22-cell cover component, one 8-cell powder vertex cut,
     * and two ordered seven-cell firm banks between separate untouched 2x2 continuations.
     */
    public static RoofedGalleryThroatPlan planRoofedGalleryThroat(
            int floorY, RibbonOrientation orientation) {
        if (orientation == null) {
            return null;
        }
        int landingY = floorY - MIN_DROP_AIR;
        List<Cell> coverCanonical = new ArrayList<>();
        for (int u = 0; u <= 3; u++) {
            for (int v = -1; v <= 1; v++) {
                coverCanonical.add(new Cell(u, v));
            }
        }
        // The carpet is deliberately NOT a rectangle: a clean block reads as a placed
        // platform (owner, TEST 127: "they look like distinct compact platforms"). This
        // L-shaped run still spans the passage wall to wall, so no all-firm route bypasses it.
        List<Cell> powderCanonical = List.of(
                new Cell(1, -1), new Cell(1, 0), new Cell(1, 1),
                new Cell(2, 0), new Cell(2, 1),
                new Cell(3, 1));
        List<Cell> firstFirmCanonical = List.of(
                new Cell(0, -1), new Cell(0, 0), new Cell(0, 1));
        List<Cell> secondFirmCanonical = List.of(
                new Cell(2, -1), new Cell(3, -1), new Cell(3, 0));
        List<Cell> approachCanonical = List.of(
                new Cell(-2, 0), new Cell(-2, 1),
                new Cell(-1, 0), new Cell(-1, 1));
        List<Cell> returnCanonical = List.of(
                new Cell(4, -1), new Cell(4, 0),
                new Cell(5, -1), new Cell(5, 0));

        List<Cell> cover = orient(coverCanonical, orientation);
        List<Cell> powder = orient(powderCanonical, orientation);
        List<Cell> firstFirm = orient(firstFirmCanonical, orientation);
        List<Cell> secondFirm = orient(secondFirmCanonical, orientation);
        List<Cell> firm = new ArrayList<>(firstFirm);
        firm.addAll(secondFirm);
        List<Cell> approach = orient(approachCanonical, orientation);
        List<Cell> returned = orient(returnCanonical, orientation);
        List<Cell> relevant = new ArrayList<>(cover);
        relevant.addAll(approach);
        relevant.addAll(returned);

        LinkedHashSet<Voxel> continuationFloor = new LinkedHashSet<>();
        for (Cell cell : approach) {
            continuationFloor.add(voxel(cell, floorY));
        }
        for (Cell cell : returned) {
            continuationFloor.add(voxel(cell, floorY));
        }
        LinkedHashSet<Voxel> headroom = new LinkedHashSet<>();
        for (Cell cell : relevant) {
            headroom.add(voxel(cell, floorY + 1));
            headroom.add(voxel(cell, floorY + 2));
        }

        LinkedHashSet<Voxel> ordinary = new LinkedHashSet<>();
        LinkedHashSet<Voxel> carvedAir = new LinkedHashSet<>();
        for (Cell cell : cover) {
            ordinary.add(voxel(cell, floorY));
        }
        for (Cell cell : powder) {
            for (int y = floorY - 1; y >= landingY; y--) {
                Voxel target = voxel(cell, y);
                ordinary.add(target);
                if (y > landingY) {
                    carvedAir.add(target);
                }
            }
        }

        // The stair's first step rises from column (5,2); that column stays untouched natural
        // mass so the step has a retained support witness rather than one the trap itself cut.
        List<Cell> lowerCorridorCanonical = List.of(
                new Cell(2, 2), new Cell(3, 2), new Cell(3, 3),
                new Cell(3, 4), new Cell(4, 2));
        List<Cell> lowerCorridor = orient(lowerCorridorCanonical, orientation);
        LinkedHashSet<Voxel> lowerStanding = new LinkedHashSet<>();
        for (Cell cell : powder) {
            lowerStanding.add(voxel(cell, landingY + 1));
        }
        for (Cell cell : lowerCorridor) {
            Voxel standing = voxel(cell, landingY);
            lowerStanding.add(standing);
            carvedAir.add(standing);
            carvedAir.add(standing.above());
        }

        List<Cell> stairCanonical = List.of(
                new Cell(5, 2), new Cell(5, 1), new Cell(4, 1),
                new Cell(4, 2), new Cell(5, 2), new Cell(5, 1),
                new Cell(4, 1), new Cell(4, 2), new Cell(5, 2),
                new Cell(5, 1), new Cell(4, 1));
        List<Cell> stairCells = orient(stairCanonical, orientation);
        List<Voxel> stairStanding = new ArrayList<>();
        for (int index = 0; index < stairCells.size(); index++) {
            Voxel standing = voxel(stairCells.get(index), landingY + 1 + index);
            stairStanding.add(standing);
            lowerStanding.add(standing);
            carvedAir.add(standing);
            carvedAir.add(standing.above());
        }
        ordinary.addAll(carvedAir);

        LinkedHashSet<Voxel> firmSupports = new LinkedHashSet<>();
        for (Cell cell : firm) {
            for (int depth = 1; depth <= APPROACH_CONTINUATION_SUPPORT_DEPTH; depth++) {
                firmSupports.add(voxel(cell, floorY - depth));
            }
        }
        Set<Cell> powderSetForSupports = Set.copyOf(powder);
        for (Voxel standing : lowerStanding) {
            if (!powderSetForSupports.contains(new Cell(standing.x(), standing.z()))) {
                firmSupports.add(standing.below());
            }
        }
        LinkedHashSet<Voxel> cushionSupports = new LinkedHashSet<>();
        for (Cell cell : powder) {
            for (int depth = 1; depth <= APPROACH_CONTINUATION_SUPPORT_DEPTH; depth++) {
                cushionSupports.add(voxel(cell, landingY - depth));
            }
        }

        Set<Cell> mouthColumns = new HashSet<>(approach);
        mouthColumns.addAll(returned);
        LinkedHashSet<Voxel> shell = new LinkedHashSet<>();
        Set<Voxel> ordinarySet = Set.copyOf(ordinary);
        for (Voxel target : ordinary) {
            if (target.y() > floorY) {
                continue;
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dz);
                    if (distance < 1 || distance > 2) {
                        continue;
                    }
                    Voxel witness =
                            new Voxel(target.x() + dx, target.y(), target.z() + dz);
                    if (!ordinarySet.contains(witness)
                            && !mouthColumns.contains(new Cell(witness.x(), witness.z()))) {
                        shell.add(witness);
                    }
                }
            }
        }

        LinkedHashSet<Voxel> ownerEnvelope = new LinkedHashSet<>();
        ownerEnvelope.addAll(continuationFloor);
        ownerEnvelope.addAll(headroom);
        ownerEnvelope.addAll(ordinary);
        ownerEnvelope.addAll(firmSupports);
        ownerEnvelope.addAll(cushionSupports);
        ownerEnvelope.addAll(shell);
        for (Cell cell : relevant) {
            for (int y = floorY + MIN_ROOF_SEARCH_RISE;
                    y <= floorY + MAX_ROOF_SEARCH_RISE + ROOF_CLEARANCE_ABOVE;
                    y++) {
                ownerEnvelope.add(voxel(cell, y));
            }
        }

        return new RoofedGalleryThroatPlan(
                orientation, floorY, landingY,
                cover, powder, firm, List.of(firstFirm, secondFirm),
                approach, returned, relevant,
                List.copyOf(continuationFloor), List.copyOf(headroom),
                List.copyOf(ordinary), List.copyOf(carvedAir),
                List.copyOf(firmSupports), List.copyOf(cushionSupports),
                List.copyOf(shell), List.copyOf(lowerStanding),
                List.copyOf(stairStanding), List.copyOf(ownerEnvelope));
    }

    /** Pure topology proof used by both focused tests and the shared world predicate. */
    public static RoofedThroatMeasurements measureRoofedGalleryThroat(
            RoofedGalleryThroatPlan plan) {
        if (plan == null) {
            return null;
        }
        Set<Cell> approach = Set.copyOf(plan.approachA());
        Set<Cell> returned = Set.copyOf(plan.returnB());
        Set<Cell> complete = new HashSet<>(plan.coverCells());
        complete.addAll(approach);
        complete.addAll(returned);
        Set<Cell> withoutPowder = new HashSet<>(plan.firmCells());
        withoutPowder.addAll(approach);
        withoutPowder.addAll(returned);
        boolean completePath = !plan.approachA().isEmpty()
                && !plan.returnB().isEmpty()
                && connectedPath(
                        plan.approachA().getFirst(), plan.returnB().getFirst(), complete) != null;
        boolean bypass = !plan.approachA().isEmpty()
                && !plan.returnB().isEmpty()
                && connectedPath(
                        plan.approachA().getFirst(), plan.returnB().getFirst(), withoutPowder)
                        != null;
        Set<Voxel> authored = Set.copyOf(plan.ordinaryAuthoredVoxels());
        Set<Voxel> witnesses = new HashSet<>(plan.continuationFloorVoxels());
        witnesses.addAll(plan.headroomVoxels());
        witnesses.addAll(plan.firmSupportVoxels());
        witnesses.addAll(plan.cushionSupportVoxels());
        witnesses.addAll(plan.lateralShellVoxels());
        List<Cell> mouths = new ArrayList<>(approach);
        mouths.addAll(returned);
        boolean rolesCorrect = plan.firmBanks().size() == 2
                && touches(plan.approachA(), plan.firmBanks().get(0))
                && !touches(plan.approachA(), plan.firmBanks().get(1))
                && touches(plan.returnB(), plan.firmBanks().get(1))
                && !touches(plan.returnB(), plan.firmBanks().get(0));
        return new RoofedThroatMeasurements(
                new HashSet<>(plan.coverCells()).size(),
                new HashSet<>(plan.powderCells()).size(),
                new HashSet<>(plan.firmCells()).size(),
                components(plan.firmCells()).stream()
                        .map(List::size).sorted().toList(),
                mouths.isEmpty() ? 0 : components(mouths).size(),
                !approach.isEmpty()
                        && !returned.isEmpty()
                        && java.util.Collections.disjoint(approach, returned),
                isFourConnected(plan.coverCells()),
                isFourConnected(plan.powderCells()),
                rolesCorrect,
                completePath && !bypass,
                java.util.Collections.disjoint(authored, witnesses));
    }

    /** Pure topology proof used by both focused tests and the shared world predicate. */
    public static PassARejection validateRoofedGalleryThroatTopology(
            RoofedGalleryThroatPlan plan) {
        if (plan == null
                || duplicate(plan.coverCells())
                || duplicate(plan.powderCells())
                || duplicate(plan.firmCells())
                || duplicate(plan.approachA())
                || duplicate(plan.returnB())) {
            return PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS;
        }
        Set<Cell> approach = Set.copyOf(plan.approachA());
        Set<Cell> returned = Set.copyOf(plan.returnB());
        if (approach.equals(returned)
                || !java.util.Collections.disjoint(approach, returned)) {
            return PassARejection.SAME_DOOR;
        }
        Set<Cell> cover = Set.copyOf(plan.coverCells());
        Set<Cell> powder = Set.copyOf(plan.powderCells());
        Set<Cell> firm = Set.copyOf(plan.firmCells());
        Set<Cell> union = new HashSet<>(powder);
        union.addAll(firm);
        List<List<Cell>> firmComponents = components(plan.firmCells());
        RoofedThroatMeasurements measurements = measureRoofedGalleryThroat(plan);
        if (measurements == null
                || !measurements.lockedTopologyMatches()
                || !cover.equals(union)
                || !java.util.Collections.disjoint(powder, firm)
                || !isFourConnected(plan.coverCells())
                || !isFourConnected(plan.powderCells())
                || plan.firmBanks().size() != 2
                || !plan.firmBankSizes().equals(List.of(3, 3))
                || firmComponents.size() != 2
                || firmComponents.stream().map(List::size).sorted().toList()
                        .equals(List.of(3, 3)) == false
                || !Set.copyOf(plan.firmBanks().get(0)).equals(
                        Set.copyOf(firmComponents.stream()
                                .filter(component -> component.containsAll(plan.firmBanks().get(0)))
                                .findFirst().orElse(List.of())))
                || !Set.copyOf(plan.firmBanks().get(1)).equals(
                        Set.copyOf(firmComponents.stream()
                                .filter(component -> component.containsAll(plan.firmBanks().get(1)))
                                .findFirst().orElse(List.of())))
                || plan.approachA().size() != 4
                || plan.returnB().size() != 4
                || !isFourConnected(plan.approachA())
                || !isFourConnected(plan.returnB())
                || !touches(plan.approachA(), plan.firmBanks().get(0))
                || touches(plan.approachA(), plan.firmBanks().get(1))
                || !touches(plan.returnB(), plan.firmBanks().get(1))
                || touches(plan.returnB(), plan.firmBanks().get(0))) {
            return PassARejection.FIRM_BYPASS_OR_WRONG_ROLES;
        }
        Set<Cell> complete = new HashSet<>(cover);
        complete.addAll(approach);
        complete.addAll(returned);
        Set<Cell> withoutPowder = new HashSet<>(firm);
        withoutPowder.addAll(approach);
        withoutPowder.addAll(returned);
        if (connectedPath(plan.approachA().getFirst(), plan.returnB().getFirst(), complete) == null
                || connectedPath(
                        plan.approachA().getFirst(), plan.returnB().getFirst(), withoutPowder)
                        != null) {
            return PassARejection.FIRM_BYPASS_OR_WRONG_ROLES;
        }
        if (!measurements.witnessWriteDisjoint()) {
            return PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS;
        }
        if (!walkRouteConnected(plan.lowerRouteStandingVoxels())
                || plan.stairStandingVoxels().isEmpty()) {
            return PassARejection.DISCONNECTED_LOWER_OR_REWARD;
        }
        Voxel upperExit = plan.stairStandingVoxels().getLast();
        Cell upperExitCell = new Cell(upperExit.x(), upperExit.z());
        if (!touches(List.of(upperExitCell), plan.returnB())
                || touches(List.of(upperExitCell), plan.approachA())) {
            return PassARejection.DISCONNECTED_LOWER_OR_REWARD;
        }
        return PassARejection.PASS;
    }

    /**
     * Proves only the locked upper conformal passage. The historical lower pocket and stair remain
     * part of {@link RoofedGalleryThroatPlan} for compatibility, but this predicate deliberately
     * never reads them. Its returned witnesses are the untouched upper floor, headroom, support,
     * and roof cells that the separate between-layer materializer must preserve.
     */
    public static UpperThroatEvaluation evaluateRoofedGalleryUpper(
            RoofedGalleryThroatPlan plan, RoofedThroatWorldView world) {
        PassARejection topology = validateRoofedGalleryThroatTopology(plan);
        if (topology != PassARejection.PASS || world == null
                || !isBetweenLayerUpperFloor(plan.floorY())) {
            return rejectedUpper(topology == PassARejection.PASS
                    ? PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS : topology);
        }

        LinkedHashSet<Voxel> witnesses = new LinkedHashSet<>();
        for (Voxel voxel : plan.continuationFloorVoxels()) {
            if (!upperWitnessIsOwned(world, voxel) || world.authored(voxel)) {
                return rejectedUpper(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
            PassARejection failure = naturalFloorFailure(world.blockKind(voxel));
            if (failure != PassARejection.PASS) {
                return rejectedUpper(failure);
            }
            witnesses.add(voxel);
        }
        for (Cell cell : plan.coverCells()) {
            Voxel floor = voxel(cell, plan.floorY());
            if (!upperWitnessIsOwned(world, floor) || world.authored(floor)) {
                return rejectedUpper(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
            PassARejection failure = naturalFloorFailure(world.blockKind(floor));
            if (failure != PassARejection.PASS) {
                return rejectedUpper(failure);
            }
        }
        for (Voxel voxel : plan.headroomVoxels()) {
            if (!upperWitnessIsOwned(world, voxel) || world.authored(voxel)) {
                return rejectedUpper(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
            ThroatBlockKind kind = world.blockKind(voxel);
            if (kind == ThroatBlockKind.FLUID) {
                return rejectedUpper(PassARejection.SEAFLOOR_OR_FLUID_STANDING);
            }
            if (kind != ThroatBlockKind.AIR) {
                return rejectedUpper(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
            witnesses.add(voxel);
        }
        for (Cell cell : plan.firmCells()) {
            for (int depth = 1; depth <= APPROACH_CONTINUATION_SUPPORT_DEPTH; depth++) {
                Voxel support = voxel(cell, plan.floorY() - depth);
                if (!upperWitnessIsOwned(world, support)
                        || world.authored(support)
                        || world.blockKind(support) != ThroatBlockKind.SAFE_NATURAL) {
                    return rejectedUpper(PassARejection.SHELL_OR_SUPPORT);
                }
                witnesses.add(support);
            }
        }
        for (Cell cell : plan.relevantFloorColumns()) {
            Voxel ceiling = null;
            for (int rise = MIN_ROOF_SEARCH_RISE; rise <= MAX_ROOF_SEARCH_RISE; rise++) {
                Voxel candidate = voxel(cell, plan.floorY() + rise);
                if (!upperWitnessIsOwned(world, candidate)) {
                    return rejectedUpper(PassARejection.OWNER);
                }
                ThroatBlockKind kind = world.blockKind(candidate);
                if (kind == ThroatBlockKind.FLUID) {
                    return rejectedUpper(PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
                }
                if (kind == ThroatBlockKind.AIR) {
                    if (world.authored(candidate)) {
                        return rejectedUpper(
                                PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
                    }
                    witnesses.add(candidate);
                    continue;
                }
                if (kind == ThroatBlockKind.HANGING_ICICLE) {
                    if (world.authored(candidate)) {
                        return rejectedUpper(
                                PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
                    }
                    // Hanging icicles decorate the open gallery below the structural ceiling.
                    // Search past them, but never retain them as natural roof evidence.
                    continue;
                }
                if (kind != ThroatBlockKind.SAFE_NATURAL || world.authored(candidate)) {
                    return rejectedUpper(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
                }
                ceiling = candidate;
                witnesses.add(candidate);
                break;
            }
            if (ceiling == null) {
                return rejectedUpper(PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
            }
            for (int above = 1; above <= ROOF_CLEARANCE_ABOVE; above++) {
                Voxel roofWitness = new Voxel(
                        ceiling.x(), ceiling.y() + above, ceiling.z());
                if (!upperWitnessIsOwned(world, roofWitness)) {
                    return rejectedUpper(PassARejection.OWNER);
                }
                ThroatBlockKind kind = world.blockKind(roofWitness);
                if (kind == ThroatBlockKind.FLUID) {
                    return rejectedUpper(PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
                }
                if (world.authored(roofWitness)
                        || (kind != ThroatBlockKind.SAFE_NATURAL
                                && kind != ThroatBlockKind.AIR)
                        || (above == 1 && kind != ThroatBlockKind.SAFE_NATURAL)) {
                    return rejectedUpper(
                            PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
                }
                witnesses.add(roofWitness);
            }
            if (world.seesSky(ceiling.above().above())) {
                return rejectedUpper(PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
            }
        }
        return new UpperThroatEvaluation(PassARejection.PASS, List.copyOf(witnesses));
    }

    private static boolean upperWitnessIsOwned(
            RoofedThroatWorldView world, Voxel voxel) {
        return world.insideOwnerInset(voxel);
    }

    private static UpperThroatEvaluation rejectedUpper(PassARejection rejection) {
        return new UpperThroatEvaluation(rejection, List.of());
    }

    /**
     * Runs the exact read-only legacy Pass-A predicate. No branch writes or substitutes authored air
     * for natural evidence; gravel is accepted only when its complete gravity column is owner-contained.
     */
    public static RoofedThroatEvaluation evaluateRoofedGalleryThroat(
            RoofedGalleryThroatPlan plan, RoofedThroatWorldView world) {
        PassARejection topology = validateRoofedGalleryThroatTopology(plan);
        if (topology != PassARejection.PASS || world == null) {
            return rejected(topology == PassARejection.PASS
                    ? PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS : topology);
        }
        for (Voxel voxel : plan.ownerEnvelopeVoxels()) {
            if (!world.insideOwnerInset(voxel)) {
                return rejected(PassARejection.OWNER);
            }
        }
        for (Voxel voxel : plan.continuationFloorVoxels()) {
            if (world.authored(voxel)) {
                return rejected(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
            PassARejection failure = naturalFloorFailure(world.blockKind(voxel));
            if (failure != PassARejection.PASS) {
                return rejected(failure);
            }
        }
        for (Cell cell : plan.coverCells()) {
            Voxel floor = voxel(cell, plan.floorY());
            if (world.authored(floor)) {
                return rejected(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
            PassARejection failure = naturalFloorFailure(world.blockKind(floor));
            if (failure != PassARejection.PASS) {
                return rejected(failure);
            }
        }
        for (Voxel voxel : plan.headroomVoxels()) {
            if (world.authored(voxel)) {
                return rejected(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
            ThroatBlockKind kind = world.blockKind(voxel);
            if (kind == ThroatBlockKind.FLUID) {
                return rejected(PassARejection.SEAFLOOR_OR_FLUID_STANDING);
            }
            if (kind != ThroatBlockKind.AIR) {
                return rejected(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
            }
        }
        for (Cell cell : plan.relevantFloorColumns()) {
            Voxel ceiling = null;
            for (int rise = MIN_ROOF_SEARCH_RISE; rise <= MAX_ROOF_SEARCH_RISE; rise++) {
                Voxel candidate = voxel(cell, plan.floorY() + rise);
                ThroatBlockKind kind = world.blockKind(candidate);
                if (kind == ThroatBlockKind.FLUID) {
                    return rejected(PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
                }
                if (kind == ThroatBlockKind.AIR) {
                    continue;
                }
                if (kind != ThroatBlockKind.SAFE_NATURAL || world.authored(candidate)) {
                    return rejected(PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS);
                }
                ceiling = candidate;
                break;
            }
            if (ceiling == null
                    || world.blockKind(ceiling.above()) != ThroatBlockKind.SAFE_NATURAL
                    || world.authored(ceiling.above())
                    || world.seesSky(ceiling.above().above())) {
                return rejected(PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
            }
            for (int above = 2; above <= ROOF_CLEARANCE_ABOVE; above++) {
                if (world.blockKind(new Voxel(
                        ceiling.x(), ceiling.y() + above, ceiling.z()))
                        == ThroatBlockKind.FLUID) {
                    return rejected(PassARejection.EXPOSED_SKY_WATER_OR_THIN_ROOF);
                }
            }
        }

        Set<Voxel> ordinary = Set.copyOf(plan.ordinaryAuthoredVoxels());
        for (Voxel voxel : ordinary) {
            ThroatBlockKind kind = world.blockKind(voxel);
            if (kind == ThroatBlockKind.FLUID) {
                return rejected(PassARejection.CAVITY_AIR_OR_FLUID);
            }
            // A pre-existing void only lies about the trap BELOW the carpet, where it would turn
            // the entrance into a thin lid over an open hole. At and above the carpet plane the
            // authored volume is the climb-out stair and its clearance, which is supposed to
            // emerge into the passage: carving air out of air there is a no-op, and the stair's
            // own footing is still proven by the untouched retained-support law below. Cover
            // cells at the carpet plane are already required to be solid natural floor above.
            if (kind == ThroatBlockKind.AIR && voxel.y() < plan.floorY()) {
                return rejected(PassARejection.CAVITY_AIR_OR_FLUID);
            }
            if (kind != ThroatBlockKind.AIR
                    && kind != ThroatBlockKind.SAFE_NATURAL
                    && kind != ThroatBlockKind.GRAVEL) {
                return rejected(PassARejection.ORE_BLOCK_ENTITY_OR_PROTECTED);
            }
        }
        if (!gravityColumnsContained(plan.ordinaryAuthoredVoxels(), world)
                || exposedGravity(plan.carvedAirVoxels(), ordinary, world)) {
            return rejected(PassARejection.GRAVITY_OR_PARTIAL_STABILIZATION);
        }
        for (Voxel voxel : retainedSupportVoxels(plan)) {
            if (world.authored(voxel)
                    || world.blockKind(voxel) != ThroatBlockKind.SAFE_NATURAL) {
                return rejected(PassARejection.SHELL_OR_SUPPORT);
            }
        }

        return new RoofedThroatEvaluation(
                PassARejection.PASS,
                true,
                false,
                RewardFeasibilityFailure.NOT_EVALUATED,
                false,
                RewardFeasibilityFailure.NOT_EVALUATED);
    }

    /** Predicts the exact later selector/fallback outcome without any world write. */
    public static PassAPrediction predictRoofedThroatOutcome(
            float fractionRoll,
            int alreadyAcceptedInChunk,
            long worldSeed,
            int anchorX,
            int anchorY,
            int anchorZ,
            RoofedGalleryThroatPlan plan,
            RoofedThroatEvaluation evaluation,
            int minimumBuildY) {
        boolean fractionAccepted = shouldTrapPatch(fractionRoll);
        boolean capAccepted =
                fractionAccepted && alreadyAcceptedInChunk < MAX_PATCHES_PER_CHUNK;
        if (!capAccepted || plan == null || evaluation == null || !evaluation.ordinaryFeasible()) {
            return new PassAPrediction(
                    fractionAccepted, false, -1, null, null, false);
        }
        int roll = rewardRoll(worldSeed, anchorX, anchorY, anchorZ);
        DeepFloodedLanding deep =
                planDeepFloodedLanding(plan.floorY(), plan.floorY(), minimumBuildY);
        int rareLandingY = evaluation.floodedFeasible() && deep != null
                ? deep.surfaceY() : 0;
        int waterDepth = evaluation.floodedFeasible() ? 3 : 0;
        int oreBudget = evaluation.floodedFeasible() ? 16 : 0;
        RewardPlan requested =
                planReward(roll, plan.landingY(), rareLandingY, waterDepth, oreBudget);
        RewardPlan resolved = requested;
        RewardKind requestedKind = requestedRewardKind(roll);
        boolean fallback = requestedKind == RewardKind.FLOODED_ORE_GALLERY
                && requested.kind() == RewardKind.ORDINARY;
        if (requestedKind == RewardKind.LOST_PROSPECTOR
                && !evaluation.prospectorFeasible()) {
            resolved = planReward(0, plan.landingY(), 0, 0, 0);
            fallback = true;
        }
        return new PassAPrediction(
                true, true, roll, requestedKind, resolved.kind(), fallback);
    }

    private static RoofedThroatEvaluation rejected(PassARejection rejection) {
        return new RoofedThroatEvaluation(
                rejection, false, false, RewardFeasibilityFailure.NONE,
                false, RewardFeasibilityFailure.NONE);
    }

    private static PassARejection naturalFloorFailure(ThroatBlockKind kind) {
        if (kind == ThroatBlockKind.FLUID || kind == ThroatBlockKind.AIR) {
            return PassARejection.SEAFLOOR_OR_FLUID_STANDING;
        }
        if (kind == ThroatBlockKind.ORE_OR_BLOCK_ENTITY
                || kind == ThroatBlockKind.PROTECTED) {
            return PassARejection.ORE_BLOCK_ENTITY_OR_PROTECTED;
        }
        return kind == ThroatBlockKind.SAFE_NATURAL
                ? PassARejection.PASS
                : PassARejection.INVALID_OR_SELF_AUTHORED_WITNESS;
    }

    private static List<Voxel> retainedSupportVoxels(RoofedGalleryThroatPlan plan) {
        LinkedHashSet<Voxel> retained = new LinkedHashSet<>();
        retained.addAll(plan.firmSupportVoxels());
        retained.addAll(plan.cushionSupportVoxels());
        retained.addAll(plan.lateralShellVoxels());
        return List.copyOf(retained);
    }

    private static boolean gravityColumnsContained(
            List<Voxel> authoredTargets, RoofedThroatWorldView world) {
        Set<Voxel> authored = Set.copyOf(authoredTargets);
        for (Voxel target : authoredTargets) {
            if (world.blockKind(target) != ThroatBlockKind.GRAVEL) {
                continue;
            }
            for (int direction : List.of(-1, 1)) {
                Voxel cursor = new Voxel(
                        target.x(), target.y() + direction, target.z());
                int checked = 0;
                while (checked++ < MAX_DROP_SCAN_FOR_PURE_CENSUS
                        && world.blockKind(cursor) == ThroatBlockKind.GRAVEL) {
                    if (!world.insideOwnerInset(cursor) || !authored.contains(cursor)) {
                        return false;
                    }
                    cursor = new Voxel(
                            cursor.x(), cursor.y() + direction, cursor.z());
                }
            }
        }
        return true;
    }

    private static boolean exposedGravity(
            List<Voxel> carvedAir,
            Set<Voxel> allAuthored,
            RoofedThroatWorldView world) {
        for (Voxel target : carvedAir) {
            Voxel above = target.above();
            if (!allAuthored.contains(above)
                    && world.blockKind(above) == ThroatBlockKind.GRAVEL) {
                return true;
            }
        }
        return false;
    }

    private static final int MAX_DROP_SCAN_FOR_PURE_CENSUS = 128;

    private static boolean duplicate(List<?> values) {
        return values == null
                || values.stream().anyMatch(Objects::isNull)
                || new HashSet<>(values).size() != values.size();
    }

    private static boolean walkRouteConnected(List<Voxel> voxels) {
        if (voxels == null || voxels.isEmpty() || duplicate(voxels)) {
            return false;
        }
        Set<Voxel> remaining = new HashSet<>(voxels);
        ArrayDeque<Voxel> queue = new ArrayDeque<>();
        queue.add(voxels.getFirst());
        remaining.remove(voxels.getFirst());
        while (!queue.isEmpty()) {
            Voxel current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        Voxel neighbour = new Voxel(
                                current.x() + dx, current.y() + dy, current.z() + dz);
                        if (remaining.remove(neighbour)) {
                            queue.addLast(neighbour);
                        }
                    }
                }
            }
        }
        return remaining.isEmpty();
    }

    /**
     * Immutable pure plan for one inset stumble carpet. Powder cells are the deceptive stations that
     * receive authored shafts; regular cells are their genuinely supported firm-snow camouflage collar.
     * The two named bank runs are the opposed walk-through approaches within that collar.
     */
    public record InsetCarpetPlan(
            List<Cell> powderCells,
            List<Cell> regularCells,
            List<Cell> firstApproachBank,
            List<Cell> secondApproachBank,
            ApproachAxis approachAxis) {

        public InsetCarpetPlan {
            powderCells = List.copyOf(powderCells);
            regularCells = List.copyOf(regularCells);
            firstApproachBank = List.copyOf(firstApproachBank);
            secondApproachBank = List.copyOf(secondApproachBank);
        }
    }

    /** The stable outcome selected once for an accepted trap. */
    public enum RewardKind {
        ORDINARY,
        FLOODED_ORE_GALLERY,
        LOST_PROSPECTOR
    }

    /**
     * Immutable outcome contract. Fields irrelevant to a kind are deliberately harmless zero/false
     * values, which keeps the ordinary fallback one exact value rather than a second hidden reroll.
     */
    public record RewardPlan(
            RewardKind kind,
            int landingY,
            int waterDepth,
            int oreBudget,
            boolean freezingForbidden,
            boolean powderCushion,
            boolean solidLanding,
            boolean staticRemains,
            boolean reachableChest,
            int valuableLootBudget) {
    }

    /** Numeric plan for the one-in-sixteen deep flooded reward before any world writes are attempted. */
    public record DeepFloodedLanding(
            int surfaceY, int fallMin, int fallMax) {
    }

    /** Vertical contract shared by ordinary production planning and the focused pure-JVM proof. */
    public record EmbeddedDropPlan(
            int floorY,
            int landingY,
            List<Integer> orderedShaftAirY,
            int cushionY,
            int dryExitStandingY) {

        public EmbeddedDropPlan {
            orderedShaftAirY = List.copyOf(orderedShaftAirY);
        }
    }

    /** One state transition in the production transaction adapter. */
    public record AtomicStateChange<S>(S expected, S desired) {
    }

    /** Minimal adapter that lets the pure transaction law drive real world writes without importing
     * Minecraft into the core layer. Indices correspond exactly to the immutable planned-write list. */
    public interface AtomicStateAdapter<S> {
        S read(int index);

        boolean write(int index, S state);
    }

    /** A failed transaction is safe to retry only when every applied state was read back as restored. */
    public record AtomicResult(boolean success, boolean rollbackVerified) {
    }

    /**
     * Applies a bounded state plan atomically, post-reading every write. A deterministic failure seam
     * runs before the indexed write; a failed write is still included in rollback because an adapter may
     * return false after partially changing its target. The finalizer is part of the transaction (the
     * world adapter uses it to seed the reward chest).
     */
    public static <S> AtomicResult applyAtomically(
            List<AtomicStateChange<S>> changes,
            AtomicStateAdapter<S> adapter,
            IntPredicate failureInjector,
            BooleanSupplier finalizer) {
        if (changes == null || adapter == null || finalizer == null) {
            return new AtomicResult(false, true);
        }
        for (int index = 0; index < changes.size(); index++) {
            if (!Objects.equals(adapter.read(index), changes.get(index).expected())) {
                return new AtomicResult(false, true);
            }
        }

        List<Integer> applied = new ArrayList<>();
        try {
            for (int index = 0; index < changes.size(); index++) {
                if (failureInjector != null && failureInjector.test(index)) {
                    return new AtomicResult(false, rollbackAtomic(changes, adapter, applied));
                }
                applied.add(index);
                AtomicStateChange<S> change = changes.get(index);
                if (!adapter.write(index, change.desired())
                        || !Objects.equals(adapter.read(index), change.desired())) {
                    return new AtomicResult(false, rollbackAtomic(changes, adapter, applied));
                }
            }
            if (!finalizer.getAsBoolean()) {
                return new AtomicResult(false, rollbackAtomic(changes, adapter, applied));
            }
            for (int index = 0; index < changes.size(); index++) {
                if (!Objects.equals(adapter.read(index), changes.get(index).desired())) {
                    return new AtomicResult(false, rollbackAtomic(changes, adapter, applied));
                }
            }
            return new AtomicResult(true, true);
        } catch (RuntimeException failure) {
            return new AtomicResult(false, rollbackAtomic(changes, adapter, applied));
        }
    }

    private static <S> boolean rollbackAtomic(
            List<AtomicStateChange<S>> changes,
            AtomicStateAdapter<S> adapter,
            List<Integer> applied) {
        boolean callsSucceeded = true;
        for (int cursor = applied.size() - 1; cursor >= 0; cursor--) {
            int index = applied.get(cursor);
            try {
                callsSucceeded &= adapter.write(index, changes.get(index).expected());
            } catch (RuntimeException failure) {
                callsSucceeded = false;
            }
        }
        boolean statesRestored = true;
        for (int index : applied) {
            try {
                statesRestored &= Objects.equals(
                        adapter.read(index), changes.get(index).expected());
            } catch (RuntimeException failure) {
                statesRestored = false;
            }
        }
        return callsSucceeded && statesRestored;
    }

    /**
     * Plans a complete cave-specific inset carpet from already-inspected terrain.
     *
     * <p>The candidate mask must contain one complete 6..40-cell component, wholly inside the owner
     * observation grid. The component must be irregular rather than a filled rectangular panel. A legal
     * carpet has contiguous overlapping stable-floor bank runs immediately beyond both north/south edges
     * or both west/east edges, a meaningful firm-snow collar, and larger stable floor behind each bank.
     * One bank, an unsupported panel, or an edge-clipped component is rejected before any world mutation.
     */
    public static InsetCarpetPlan planInsetCarpet(
            boolean[][] candidateMask, boolean[][] stableFloorMask) {
        int width = rectangularWidth(candidateMask);
        if (width < 0 || rectangularWidth(stableFloorMask) != width
                || candidateMask.length != stableFloorMask.length) {
            return null;
        }

        List<Cell> powder = new ArrayList<>();
        for (int x = 0; x < candidateMask.length; x++) {
            for (int z = 0; z < width; z++) {
                if (!candidateMask[x][z]) {
                    continue;
                }
                if (x == 0 || z == 0 || x == candidateMask.length - 1 || z == width - 1) {
                    return null;
                }
                powder.add(new Cell(x, z));
            }
        }
        if (powder.size() < MIN_PATCH_AREA || powder.size() > PATCH_MAX_AREA
                || !isFourConnected(powder) || !isIrregular(powder)) {
            return null;
        }

        List<Cell> firmCollar = stableCollar(candidateMask, stableFloorMask);
        int requiredFirm = Math.max(
                MIN_FIRM_CAMOUFLAGE_CELLS,
                (powder.size() + MAX_POWDER_CELLS_PER_FIRM_CAMOUFLAGE - 1)
                        / MAX_POWDER_CELLS_PER_FIRM_CAMOUFLAGE);
        if (firmCollar.size() < requiredFirm) {
            return null;
        }

        BankPair northSouth = bankPair(
                candidateMask, stableFloorMask, 0, -1, 0, 1, ApproachAxis.NORTH_SOUTH);
        if (northSouth != null) {
            return new InsetCarpetPlan(
                    powder, firmCollar, northSouth.first(), northSouth.second(), northSouth.axis());
        }

        BankPair eastWest = bankPair(
                candidateMask, stableFloorMask, -1, 0, 1, 0, ApproachAxis.EAST_WEST);
        if (eastWest != null) {
            return new InsetCarpetPlan(
                    powder, firmCollar, eastWest.first(), eastWest.second(), eastWest.axis());
        }
        return null;
    }

    /**
     * Selects deterministic bounded inset plans from a larger natural thin-floor mask. The original
     * mask is never treated as authored wholesale: each returned plan independently satisfies the
     * complete irregularity, firm-camouflage, opposed-bank, and continuation laws above. Rectangle
     * bounds are only a deterministic selection device; a filled rectangular result still rejects.
     */
    public static List<InsetCarpetPlan> planInsetCarpetCandidates(
            boolean[][] naturalCandidateMask, boolean[][] stableFloorMask, int limit) {
        int width = rectangularWidth(naturalCandidateMask);
        if (limit <= 0 || width < 0 || rectangularWidth(stableFloorMask) != width
                || naturalCandidateMask.length != stableFloorMask.length) {
            return List.of();
        }

        List<InsetCarpetPlan> plans = new ArrayList<>();
        Set<String> seenPowderMasks = new HashSet<>();
        Set<String> seenBoundedMasks = new HashSet<>();
        InsetCarpetPlan exact = planInsetCarpet(naturalCandidateMask, stableFloorMask);
        if (exact != null) {
            plans.add(exact);
            seenPowderMasks.add(cellKey(exact.powderCells()));
        }

        int[][] prefix = maskPrefix(naturalCandidateMask);
        List<InsetCarpetPlan> bounded = new ArrayList<>();
        for (int minX = 1; minX < naturalCandidateMask.length - 1; minX++) {
            for (int maxX = minX + 1; maxX < naturalCandidateMask.length - 1; maxX++) {
                for (int minZ = 1; minZ < width - 1; minZ++) {
                    for (int maxZ = minZ + 1; maxZ < width - 1; maxZ++) {
                        int area = maskArea(prefix, minX, maxX, minZ, maxZ);
                        if (area < MIN_PATCH_AREA || area > PATCH_MAX_AREA) {
                            continue;
                        }
                        boolean[][] subset = boundedMask(
                                naturalCandidateMask, minX, maxX, minZ, maxZ);
                        if (!seenBoundedMasks.add(maskKey(subset))) {
                            continue;
                        }
                        InsetCarpetPlan plan = planInsetCarpet(subset, stableFloorMask);
                        if (plan == null || !seenPowderMasks.add(cellKey(plan.powderCells()))) {
                            continue;
                        }
                        bounded.add(plan);
                    }
                }
            }
        }
        bounded.sort(Comparator
                .comparingInt((InsetCarpetPlan plan) -> Math.abs(plan.powderCells().size() - 16))
                .thenComparingInt(plan -> plan.powderCells().stream()
                        .mapToInt(Cell::x).min().orElseThrow())
                .thenComparingInt(plan -> plan.powderCells().stream()
                        .mapToInt(Cell::z).min().orElseThrow())
                .thenComparingInt(plan -> -plan.powderCells().size())
                .thenComparing(plan -> plan.approachAxis().name()));
        plans.addAll(bounded);
        return List.copyOf(plans.subList(0, Math.min(limit, plans.size())));
    }

    private static int[][] maskPrefix(boolean[][] mask) {
        int[][] prefix = new int[mask.length + 1][mask[0].length + 1];
        for (int x = 0; x < mask.length; x++) {
            for (int z = 0; z < mask[x].length; z++) {
                prefix[x + 1][z + 1] = (mask[x][z] ? 1 : 0)
                        + prefix[x][z + 1] + prefix[x + 1][z] - prefix[x][z];
            }
        }
        return prefix;
    }

    private static int maskArea(
            int[][] prefix, int minX, int maxX, int minZ, int maxZ) {
        return prefix[maxX + 1][maxZ + 1]
                - prefix[minX][maxZ + 1]
                - prefix[maxX + 1][minZ]
                + prefix[minX][minZ];
    }

    private static boolean[][] boundedMask(
            boolean[][] source, int minX, int maxX, int minZ, int maxZ) {
        boolean[][] subset = new boolean[source.length][source[0].length];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                subset[x][z] = source[x][z];
            }
        }
        return subset;
    }

    private static String cellKey(List<Cell> cells) {
        return cells.stream()
                .sorted(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z))
                .map(cell -> cell.x() + ":" + cell.z())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String maskKey(boolean[][] mask) {
        StringBuilder key = new StringBuilder();
        for (int x = 0; x < mask.length; x++) {
            for (int z = 0; z < mask[x].length; z++) {
                if (mask[x][z]) {
                    key.append(x).append(':').append(z).append(',');
                }
            }
        }
        return key.toString();
    }

    /** Stable 0..15 selector derived only from world identity and the canonical accepted-trap anchor. */
    public static int rewardRoll(long worldSeed, int anchorX, int anchorY, int anchorZ) {
        long mixed = worldSeed ^ 0x4341564554524150L;
        mixed = mix64(mixed ^ Integer.toUnsignedLong(anchorX));
        mixed = mix64(mixed ^ Long.rotateLeft(Integer.toUnsignedLong(anchorY), 21));
        mixed = mix64(mixed ^ Long.rotateLeft(Integer.toUnsignedLong(anchorZ), 42));
        return (int) (mixed & 15L);
    }

    /**
     * Maps one bounded roll to one immutable reward. A flooded-gallery request that fails any hard
     * budget/landing precondition returns the exact ordinary value; it never consumes or invents another
     * rare outcome.
     */
    public static RewardPlan planReward(
            int rewardRoll, int ordinaryLandingY, int rareLandingY, int waterDepth, int oreBudget) {
        RewardKind requested = requestedRewardKind(rewardRoll);
        if (requested == null) {
            return null;
        }
        RewardPlan ordinary = ordinaryReward(ordinaryLandingY);
        if (requested == RewardKind.ORDINARY) {
            return ordinary;
        }
        if (requested == RewardKind.FLOODED_ORE_GALLERY) {
            if (rareLandingY >= 0 || waterDepth < 3 || oreBudget < 12 || oreBudget > 24) {
                return ordinary;
            }
            return new RewardPlan(
                    RewardKind.FLOODED_ORE_GALLERY,
                    rareLandingY,
                    waterDepth,
                    oreBudget,
                    true,
                    false,
                    true,
                    false,
                    false,
                    0);
        }
        return new RewardPlan(
                RewardKind.LOST_PROSPECTOR,
                ordinaryLandingY,
                0,
                0,
                false,
                true,
                true,
                true,
                true,
                12);
    }

    /** Raw 0..15 request mapping shared by production planning and census request tracing. */
    public static RewardKind requestedRewardKind(int rewardRoll) {
        if (rewardRoll < 0 || rewardRoll >= 16) {
            return null;
        }
        if (rewardRoll <= 13) {
            return RewardKind.ORDINARY;
        }
        return rewardRoll == 14
                ? RewardKind.FLOODED_ORE_GALLERY
                : RewardKind.LOST_PROSPECTOR;
    }

    /**
     * Chooses the highest legal deep-water surface that is both below Y=0 and at least 32 blocks below
     * every entrance. The caller must still certify every real block in the shaft, gallery, pool, and
     * route before writing; this method pins only the depth and build-limit law.
     */
    public static DeepFloodedLanding planDeepFloodedLanding(
            int lowestEntranceY, int highestEntranceY, int minimumBuildY) {
        if (highestEntranceY < lowestEntranceY) {
            return null;
        }
        int surfaceY = Math.min(-1, lowestEntranceY - 32);
        int fallMin = lowestEntranceY - surfaceY;
        int fallMax = highestEntranceY - surfaceY;
        if (surfaceY - 3 <= minimumBuildY || fallMin < 32 || fallMax > 96) {
            return null;
        }
        return new DeepFloodedLanding(surfaceY, fallMin, fallMax);
    }

    private static RewardPlan ordinaryReward(int landingY) {
        return new RewardPlan(
                RewardKind.ORDINARY,
                landingY,
                0,
                0,
                false,
                true,
                true,
                false,
                false,
                0);
    }

    private static int rectangularWidth(boolean[][] mask) {
        if (mask == null || mask.length == 0 || mask[0] == null || mask[0].length == 0) {
            return -1;
        }
        int width = mask[0].length;
        for (boolean[] row : mask) {
            if (row == null || row.length != width) {
                return -1;
            }
        }
        return width;
    }

    private static List<Cell> bankCells(
            boolean[][] candidateMask, boolean[][] stableFloorMask, int dx, int dz) {
        List<Cell> bank = new ArrayList<>();
        for (int x = 0; x < candidateMask.length; x++) {
            for (int z = 0; z < candidateMask[x].length; z++) {
                if (!candidateMask[x][z]) {
                    continue;
                }
                int bx = x + dx;
                int bz = z + dz;
                if (bx < 0 || bx >= candidateMask.length || bz < 0 || bz >= candidateMask[x].length
                        || candidateMask[bx][bz] || !stableFloorMask[bx][bz]) {
                    continue;
                }
                Cell cell = new Cell(bx, bz);
                if (!bank.contains(cell)) {
                    bank.add(cell);
                }
            }
        }
        return bank;
    }

    private record BankPair(List<Cell> first, List<Cell> second, ApproachAxis axis) {
    }

    private static BankPair bankPair(
            boolean[][] candidateMask,
            boolean[][] stableFloorMask,
            int firstDx,
            int firstDz,
            int secondDx,
            int secondDz,
            ApproachAxis axis) {
        List<List<Cell>> firstRuns = components(
                bankCells(candidateMask, stableFloorMask, firstDx, firstDz));
        List<List<Cell>> secondRuns = components(
                bankCells(candidateMask, stableFloorMask, secondDx, secondDz));
        BankPair best = null;
        int bestOverlap = -1;
        for (List<Cell> first : firstRuns) {
            if (first.size() < MIN_APPROACH_BANK_RUN
                    || !hasStableContinuation(first, candidateMask, stableFloorMask)) {
                continue;
            }
            for (List<Cell> second : secondRuns) {
                if (second.size() < MIN_APPROACH_BANK_RUN
                        || !hasStableContinuation(second, candidateMask, stableFloorMask)) {
                    continue;
                }
                int overlap = projectedOverlap(first, second, axis);
                if (overlap >= MIN_APPROACH_BANK_RUN && overlap > bestOverlap) {
                    best = new BankPair(List.copyOf(first), List.copyOf(second), axis);
                    bestOverlap = overlap;
                }
            }
        }
        return best;
    }

    private static List<Cell> stableCollar(
            boolean[][] candidateMask, boolean[][] stableFloorMask) {
        List<Cell> collar = new ArrayList<>();
        for (int x = 0; x < candidateMask.length; x++) {
            for (int z = 0; z < candidateMask[x].length; z++) {
                if (candidateMask[x][z] || !stableFloorMask[x][z]) {
                    continue;
                }
                Cell cell = new Cell(x, z);
                if (touchesMask(cell, candidateMask)) {
                    collar.add(cell);
                }
            }
        }
        return List.copyOf(collar);
    }

    private static boolean hasStableContinuation(
            List<Cell> bank, boolean[][] candidateMask, boolean[][] stableFloorMask) {
        Set<Cell> component = floodStable(bank.getFirst(), stableFloorMask, candidateMask);
        int beyondCollar = 0;
        for (Cell cell : component) {
            if (!touchesMask(cell, candidateMask)) {
                beyondCollar++;
            }
        }
        return beyondCollar >= MIN_APPROACH_CONTINUATION_AREA;
    }

    private static int projectedOverlap(
            List<Cell> first, List<Cell> second, ApproachAxis axis) {
        Set<Integer> firstProjection = new HashSet<>();
        for (Cell cell : first) {
            firstProjection.add(axis == ApproachAxis.NORTH_SOUTH ? cell.x() : cell.z());
        }
        Set<Integer> secondProjection = new HashSet<>();
        for (Cell cell : second) {
            secondProjection.add(axis == ApproachAxis.NORTH_SOUTH ? cell.x() : cell.z());
        }
        firstProjection.retainAll(secondProjection);
        return firstProjection.size();
    }

    private static boolean isFourConnected(List<Cell> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        Set<Cell> remaining = new HashSet<>(cells);
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(cells.getFirst());
        remaining.remove(cells.getFirst());
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            for (Cell next : neighbours(cell)) {
                if (remaining.remove(next)) {
                    queue.addLast(next);
                }
            }
        }
        return remaining.isEmpty();
    }

    private static boolean isIrregular(List<Cell> cells) {
        int minX = cells.stream().mapToInt(Cell::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(Cell::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(Cell::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(Cell::z).max().orElseThrow();
        int boundingArea = (maxX - minX + 1) * (maxZ - minZ + 1);
        return cells.size() < boundingArea;
    }

    private static List<List<Cell>> components(List<Cell> cells) {
        Set<Cell> remaining = new HashSet<>(cells);
        List<List<Cell>> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Cell start = remaining.stream()
                    .min(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z))
                    .orElseThrow();
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            List<Cell> component = new ArrayList<>();
            queue.add(start);
            remaining.remove(start);
            while (!queue.isEmpty()) {
                Cell cell = queue.removeFirst();
                component.add(cell);
                for (Cell next : neighbours(cell)) {
                    if (remaining.remove(next)) {
                        queue.addLast(next);
                    }
                }
            }
            result.add(component);
        }
        return result;
    }

    private static Set<Cell> floodStable(
            Cell start, boolean[][] stableFloorMask, boolean[][] candidateMask) {
        Set<Cell> visited = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            for (Cell next : neighbours(cell)) {
                if (next.x() < 0 || next.x() >= stableFloorMask.length
                        || next.z() < 0 || next.z() >= stableFloorMask[next.x()].length
                        || candidateMask[next.x()][next.z()]
                        || !stableFloorMask[next.x()][next.z()]
                        || !visited.add(next)) {
                    continue;
                }
                queue.addLast(next);
            }
        }
        return visited;
    }

    private static boolean touchesMask(Cell cell, boolean[][] mask) {
        for (Cell neighbour : neighbours(cell)) {
            if (neighbour.x() >= 0 && neighbour.x() < mask.length
                    && neighbour.z() >= 0 && neighbour.z() < mask[neighbour.x()].length
                    && mask[neighbour.x()][neighbour.z()]) {
                return true;
            }
        }
        return false;
    }

    private static List<Cell> neighbours(Cell cell) {
        return List.of(
                new Cell(cell.x() - 1, cell.z()),
                new Cell(cell.x() + 1, cell.z()),
                new Cell(cell.x(), cell.z() - 1),
                new Cell(cell.x(), cell.z() + 1));
    }

    private static List<Cell> canonicalRibbonPowder() {
        return List.of(
                new Cell(-2, -4),
                new Cell(-1, -4),
                new Cell(-1, -3),
                new Cell(-1, -2),
                new Cell(-2, -1),
                new Cell(-1, -1),
                new Cell(-2, 0),
                new Cell(-1, 0));
    }

    private static List<Cell> canonicalRibbonRail() {
        LinkedHashSet<Cell> cells = new LinkedHashSet<>();
        for (int z = -5; z <= 1; z++) {
            cells.add(new Cell(0, z));
        }
        cells.addAll(canonicalFirstBank());
        cells.addAll(canonicalSecondBank());
        for (int z = -1; z <= 1; z++) {
            cells.add(new Cell(-3, z));
        }
        return List.copyOf(cells);
    }

    private static List<Cell> canonicalFirstBank() {
        return List.of(
                new Cell(-2, -5),
                new Cell(-1, -5));
    }

    private static List<Cell> canonicalSecondBank() {
        return List.of(
                new Cell(-2, 1),
                new Cell(-1, 1));
    }

    private static List<Cell> canonicalFirstContinuation() {
        return List.of(
                new Cell(-2, -7),
                new Cell(-1, -7),
                new Cell(-2, -6),
                new Cell(-1, -6));
    }

    private static List<Cell> canonicalSecondContinuation() {
        return List.of(
                new Cell(-2, 2),
                new Cell(-1, 2),
                new Cell(-2, 3),
                new Cell(-1, 3));
    }

    private static List<Cell> canonicalExitBank() {
        return List.of(
                new Cell(3, 2),
                new Cell(4, 2),
                new Cell(3, 3),
                new Cell(4, 3));
    }

    private static List<Cell> canonicalStair() {
        return List.of(
                new Cell(0, 0),
                new Cell(1, 0),
                new Cell(2, 0),
                new Cell(3, 0),
                new Cell(4, 0),
                new Cell(4, 1),
                new Cell(3, 1),
                new Cell(2, 1),
                new Cell(1, 1),
                new Cell(1, 2),
                new Cell(2, 2),
                new Cell(3, 2));
    }

    private static List<Cell> canonicalUpperReturn() {
        return List.of(
                new Cell(3, 3),
                new Cell(2, 3),
                new Cell(1, 3),
                new Cell(0, 3),
                new Cell(-1, 3));
    }

    private static ProspectorLayout canonicalProspectorLayout() {
        return new ProspectorLayout(
                new Cell(-2, 0),
                new Cell(-3, 0),
                new Cell(-3, 1),
                new Cell(-2, 1),
                new Cell(-1, 1));
    }

    private static List<RibbonEntry> canonicalRibbonEntries() {
        return List.of(
                new RibbonEntry(new Cell(-2, -4), new Cell(-2, -5)),
                new RibbonEntry(new Cell(-1, -4), new Cell(0, -4)),
                new RibbonEntry(new Cell(-1, -3), new Cell(0, -3)),
                new RibbonEntry(new Cell(-1, -2), new Cell(0, -2)),
                new RibbonEntry(new Cell(-2, -1), new Cell(-3, -1)),
                new RibbonEntry(new Cell(-1, -1), new Cell(0, -1)),
                new RibbonEntry(new Cell(-2, 0), new Cell(-3, 0)),
                new RibbonEntry(new Cell(-1, 0), new Cell(0, 0)));
    }

    private static List<Cell> orient(
            List<Cell> cells, RibbonOrientation orientation) {
        return cells.stream().map(cell -> orient(cell, orientation)).toList();
    }

    private static Cell orient(Cell cell, RibbonOrientation orientation) {
        return switch (orientation) {
            case NORTH -> cell;
            case EAST -> new Cell(-cell.z(), cell.x());
            case SOUTH -> new Cell(-cell.x(), -cell.z());
            case WEST -> new Cell(cell.z(), -cell.x());
        };
    }

    private static ProspectorLayout orient(
            ProspectorLayout layout, RibbonOrientation orientation) {
        return new ProspectorLayout(
                orient(layout.cushion(), orientation),
                orient(layout.foot(), orientation),
                orient(layout.head(), orientation),
                orient(layout.chest(), orientation),
                orient(layout.front(), orientation));
    }

    private static Voxel voxel(Cell cell, int y) {
        return new Voxel(cell.x(), y, cell.z());
    }

    private static boolean bankBordersPowder(
            List<Cell> bank,
            Set<Cell> powder,
            ApproachAxis axis,
            boolean first) {
        int dx = axis == ApproachAxis.EAST_WEST ? (first ? 1 : -1) : 0;
        int dz = axis == ApproachAxis.NORTH_SOUTH ? (first ? 1 : -1) : 0;
        return bank.stream().allMatch(
                cell -> powder.contains(new Cell(cell.x() + dx, cell.z() + dz)));
    }

    private static int projectedBankOverlap(
            List<Cell> first,
            List<Cell> second,
            ApproachAxis axis) {
        Set<Integer> projection = new HashSet<>();
        for (Cell cell : first) {
            projection.add(axis == ApproachAxis.NORTH_SOUTH ? cell.x() : cell.z());
        }
        Set<Integer> other = new HashSet<>();
        for (Cell cell : second) {
            other.add(axis == ApproachAxis.NORTH_SOUTH ? cell.x() : cell.z());
        }
        projection.retainAll(other);
        return projection.size();
    }

    private static boolean touches(List<Cell> first, List<Cell> second) {
        return first.stream().anyMatch(
                cell -> second.stream().anyMatch(other -> manhattan(cell, other) == 1));
    }

    private static List<Cell> connectedPath(
            Cell start, Cell target, Set<Cell> allowed) {
        if (!allowed.contains(start) || !allowed.contains(target)) {
            return null;
        }
        java.util.LinkedHashMap<Cell, Cell> parents = new java.util.LinkedHashMap<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        parents.put(start, null);
        queue.add(start);
        while (!queue.isEmpty() && !parents.containsKey(target)) {
            Cell current = queue.removeFirst();
            for (Cell next : neighbours(current)) {
                if (allowed.contains(next) && !parents.containsKey(next)) {
                    parents.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!parents.containsKey(target)) {
            return null;
        }
        ArrayDeque<Cell> reverse = new ArrayDeque<>();
        for (Cell cursor = target; cursor != null; cursor = parents.get(cursor)) {
            reverse.addFirst(cursor);
        }
        return List.copyOf(reverse);
    }

    private static int manhattan(Cell first, Cell second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z());
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
