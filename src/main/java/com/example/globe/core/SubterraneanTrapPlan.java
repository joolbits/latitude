package com.example.globe.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure-Java validation and write planning for a {@link SubterraneanTrapLayout.Placement}.
 *
 * <p>The caller supplies a chunk-local surface snapshot. This class either returns one immutable plan whose
 * writes are conflict-free, or a named rejection without exposing a partial plan. It deliberately contains no
 * Minecraft types: a runtime adapter may apply the returned writes only after this law has accepted them.
 */
public final class SubterraneanTrapPlan {

    public static final int OTHER = 0;
    public static final int FULL_SNOW = 1;
    public static final int POWDER = 2;
    public static final int THIN_SNOW = 3;
    /** A top snow layer directly over a snow block; the effective surface is the block beneath the layer. */
    public static final int THIN_OVER_FULL_SNOW = 4;

    public static final int PREFERRED_DEPTH = 32;
    public static final int MIN_LEGAL_DEPTH = 24;
    public static final int MAX_LEGAL_DEPTH = 48;
    public static final int MIN_HARD_DEPTH = 18;
    public static final int MAX_HARD_DEPTH = 96;
    private static final int MAX_ALTERNATIVES_PER_DEPTH = 34;
    private static final List<Integer> PREFERRED_DEPTH_ORDER =
            List.of(32, 33, 31, 34, 30, 35, 29, 36, 28);

    /** Three firm cells establish a walkable bearing; the fourth is a local relief probe only. */
    private static final int FIRM_APPROACH_LENGTH = 3;
    private static final int APPROACH_RELIEF_PROBE_LENGTH = 4;

    /** The only material actions an accepted plan can ask a runtime adapter to perform. */
    public enum Phase {
        CUSHION_BASE,
        CUSHION,
        ESCAPE_FLOOR,
        ESCAPE_MINE_TAIL,
        CLEAR,
        ESCAPE_CLEAR,
        SURFACE_POWDER,
        REMOVE_SURFACE_LAYER
    }

    /** Why a requested plan was rejected before any write list was created. */
    public enum Rejection {
        INVALID_INPUT,
        POWDER_RELIEF_EXCEEDS_ONE,
        APPROACH_RING_UNSTABLE,
        UNSUPPORTED_SURFACE,
        DEPTH_OUTSIDE_HARD_LIMIT,
        DEPTH_OUTSIDE_LEGAL_RANGE,
        ESCAPE_ELEVATION_CAPACITY,
        ESCAPE_OWNER_BOUNDS
    }

    /** One immutable world-coordinate write. */
    public record Write(int x, int y, int z, Phase phase) {
        public Write {
            Objects.requireNonNull(phase, "phase");
        }
    }

    /** One scanner-readable local route coordinate. */
    public record EscapeStep(int x, int y, int z) {
    }

    /**
     * Complete owner-local escape proof. {@code steps} is the already-open rising corridor;
     * {@code tailSteps} is the short snow-block mining tail below the untouched surface plug.
     */
    public record EscapeRoute(List<EscapeStep> steps, List<EscapeStep> shellProbes,
                              List<EscapeStep> tailSteps, EscapeStep entry, EscapeStep exit,
                              EscapeStep closureCell, EscapeStep surfacePlug) {
        public EscapeRoute {
            steps = List.copyOf(steps);
            shellProbes = List.copyOf(shellProbes);
            tailSteps = List.copyOf(tailSteps);
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(exit, "exit");
            Objects.requireNonNull(closureCell, "closureCell");
            Objects.requireNonNull(surfacePlug, "surfacePlug");
        }
    }

    /** Immutable accepted geometry; {@code roofY} is the minimum powder roof and depth is {@code roofY - landingY}. */
    public record Plan(int roofY, int landingY, EscapeRoute escapeRoute, List<Write> writes) {
        public Plan {
            Objects.requireNonNull(escapeRoute, "escapeRoute");
            writes = List.copyOf(writes);
            Set<Coordinate> coordinates = new HashSet<>();
            for (Write write : writes) {
                if (!coordinates.add(new Coordinate(write.x(), write.y(), write.z()))) {
                    throw new IllegalArgumentException("plan writes must have unique coordinates");
                }
            }
        }
    }

    /** Named all-or-nothing result: exactly one of {@code accepted} and {@code rejection} is present. */
    public record Result(Plan accepted, Rejection rejection) {
        public Result {
            if ((accepted == null) == (rejection == null)) {
                throw new IllegalArgumentException("result must contain exactly one of accepted or rejection");
            }
        }

        public boolean isAccepted() {
            return accepted != null;
        }
    }

    private record Coordinate(int x, int y, int z) {
    }

    private record EdgeCandidate(SubterraneanTrapLayout.Cell doorway,
                                 SubterraneanTrapLayout.Cell routeStart) {
    }

    private record EscapeGeometry(List<EscapeStep> steps, List<EscapeStep> tailSteps,
                                  EscapeStep entry, EscapeStep exit,
                                  EscapeStep closureCell, EscapeStep surfacePlug,
                                  int directionOrder, int routeLength, int tailLength) {
        private EscapeGeometry {
            steps = List.copyOf(steps);
            tailSteps = List.copyOf(tailSteps);
        }
    }

    private record AlternativesResult(List<Plan> plans, Rejection rejection) {
        private AlternativesResult {
            plans = List.copyOf(plans);
            if (!plans.isEmpty() && rejection != null) {
                throw new IllegalArgumentException("accepted alternatives cannot also carry a rejection");
            }
        }
    }

    private record TerrainFitCells(Set<SubterraneanTrapLayout.Cell> collar,
                                   Set<SubterraneanTrapLayout.Cell> firmApproach,
                                   Set<SubterraneanTrapLayout.Cell> reliefSample,
                                   Set<SubterraneanTrapLayout.Cell> stepSample) {
    }

    private static final Comparator<SubterraneanTrapLayout.Cell> CELL_ORDER =
            Comparator.comparingInt(SubterraneanTrapLayout.Cell::x).thenComparingInt(SubterraneanTrapLayout.Cell::z);

    private SubterraneanTrapPlan() {
    }

    /**
     * Validates the supplied local surface and returns either a complete write plan or a named rejection.
     * {@code surfaceFirstAir[x][z]} is the effective first open block above each natural surface. Powder roofs
     * retain their individual y values; the common landing is the minimum powder roof minus the preferred depth.
     */
    public static Result plan(SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir,
                              int[][] surfaceKind) {
        AlternativesResult result =
                planAlternativesResult(placement, surfaceFirstAir, surfaceKind, PREFERRED_DEPTH);
        if (!result.plans().isEmpty()) {
            return new Result(result.plans().getFirst(), null);
        }
        return rejected(result.rejection() == null ? Rejection.ESCAPE_ELEVATION_CAPACITY : result.rejection());
    }

    /** Exact near-first depth order used by the bounded runtime selector. */
    public static List<Integer> preferredDepthOrder() {
        return PREFERRED_DEPTH_ORDER;
    }

    /**
     * Returns every complete, immutable route alternative for one exact landing depth. Alternatives are ordered
     * by doorway X/Z, clockwise before counterclockwise, ascending route length, then a two-step tail before a
     * three-step tail. Invalid or geometrically impossible inputs return an empty immutable list.
     */
    public static List<Plan> planAlternatives(SubterraneanTrapLayout.Placement placement,
                                              int[][] surfaceFirstAir, int[][] surfaceKind, int depth) {
        return planAlternativesResult(placement, surfaceFirstAir, surfaceKind, depth).plans();
    }

    private static AlternativesResult planAlternativesResult(
            SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir,
            int[][] surfaceKind, int depth) {
        if (placement == null || !isLocalSurface(surfaceFirstAir) || !isLocalSurface(surfaceKind)) {
            return noAlternatives(Rejection.INVALID_INPUT);
        }
        List<SubterraneanTrapLayout.Cell> powder = placement.powder().stream().sorted(CELL_ORDER).toList();
        if (powder.isEmpty()) {
            return noAlternatives(Rejection.INVALID_INPUT);
        }
        Set<SubterraneanTrapLayout.Cell> powderSet = Set.copyOf(powder);
        for (SubterraneanTrapLayout.Cell cell : powder) {
            int kind = surfaceKind[cell.x()][cell.z()];
            if (!isSnowCovered(kind)) {
                return noAlternatives(Rejection.UNSUPPORTED_SURFACE);
            }
        }
        if (heightRangeExceeds(powderSet, surfaceFirstAir, 1)) {
            return noAlternatives(Rejection.POWDER_RELIEF_EXCEEDS_ONE);
        }
        int roofY = powder.stream().mapToInt(cell -> surfaceFirstAir[cell.x()][cell.z()] - 1).min().orElseThrow();
        int landingY = roofY - depth;
        for (SubterraneanTrapLayout.Cell cell : powder) {
            int cellDepth = surfaceFirstAir[cell.x()][cell.z()] - 1 - landingY;
            if (cellDepth < MIN_HARD_DEPTH || cellDepth > MAX_HARD_DEPTH) {
                return noAlternatives(Rejection.DEPTH_OUTSIDE_HARD_LIMIT);
            }
            if (cellDepth < MIN_LEGAL_DEPTH || cellDepth > MAX_LEGAL_DEPTH) {
                return noAlternatives(Rejection.DEPTH_OUTSIDE_LEGAL_RANGE);
            }
        }

        List<SubterraneanTrapLayout.Cell> perimeter = expandedPowderPerimeter(powderSet);
        // Capacity is structural and therefore precedes local approach validation. A geometry too short to rise
        // from the landing to any possible intact plug must get its own atomic rejection, not an incidental ring
        // or bounds failure.
        if (!hasRawElevationCapacity(powderSet, perimeter, surfaceFirstAir, landingY)) {
            return noAlternatives(Rejection.ESCAPE_ELEVATION_CAPACITY);
        }

        TerrainFitCells terrain = terrainFitCells(placement, powderSet);
        if (terrain == null) {
            return noAlternatives(Rejection.INVALID_INPUT);
        }
        for (SubterraneanTrapLayout.Cell cell : terrain.collar()) {
            if (!isSnowCovered(surfaceKind[cell.x()][cell.z()])) {
                return noAlternatives(Rejection.UNSUPPORTED_SURFACE);
            }
        }
        for (SubterraneanTrapLayout.Cell cell : terrain.firmApproach()) {
            if (!isFirmSnow(surfaceKind[cell.x()][cell.z()])) {
                return noAlternatives(Rejection.UNSUPPORTED_SURFACE);
            }
        }
        if (heightRangeExceeds(terrain.reliefSample(), surfaceFirstAir, 3)
                || !cardinalStepsStable(terrain.stepSample(), surfaceFirstAir)) {
            return noAlternatives(Rejection.APPROACH_RING_UNSTABLE);
        }

        List<EscapeGeometry> escapes =
                escapeGeometries(powderSet, perimeter, surfaceFirstAir, surfaceKind, landingY);
        if (escapes.isEmpty()) {
            // Every edge, direction, possible exit, tail length, and surface plug has been considered.
            return noAlternatives(Rejection.ESCAPE_ELEVATION_CAPACITY);
        }
        return new AlternativesResult(escapes.stream()
                .map(escape -> buildPlan(powder, surfaceFirstAir, surfaceKind, roofY, landingY, escape))
                .toList(), null);
    }

    private static Plan buildPlan(List<SubterraneanTrapLayout.Cell> powder,
                                  int[][] surfaceFirstAir, int[][] surfaceKind,
                                  int roofY, int landingY, EscapeGeometry escape) {
        List<Write> writes = new ArrayList<>();
        for (SubterraneanTrapLayout.Cell cell : powder) {
            writes.add(new Write(cell.x(), landingY - 1, cell.z(), Phase.CUSHION_BASE));
        }
        for (SubterraneanTrapLayout.Cell cell : powder) {
            writes.add(new Write(cell.x(), landingY, cell.z(), Phase.CUSHION));
        }
        for (EscapeStep step : openEscapeCells(escape)) {
            writes.add(new Write(step.x(), step.y(), step.z(), Phase.ESCAPE_FLOOR));
        }
        for (EscapeStep step : escape.tailSteps()) {
            writes.add(new Write(step.x(), step.y(), step.z(), Phase.ESCAPE_MINE_TAIL));
            writes.add(new Write(step.x(), step.y() + 1, step.z(), Phase.ESCAPE_MINE_TAIL));
            writes.add(new Write(step.x(), step.y() + 2, step.z(), Phase.ESCAPE_MINE_TAIL));
        }
        for (SubterraneanTrapLayout.Cell cell : powder) {
            int cellRoofY = surfaceFirstAir[cell.x()][cell.z()] - 1;
            for (int y = cellRoofY - 1; y >= landingY + 1; y--) {
                writes.add(new Write(cell.x(), y, cell.z(), Phase.CLEAR));
            }
        }
        for (EscapeStep step : openEscapeCells(escape)) {
            writes.add(new Write(step.x(), step.y() + 1, step.z(), Phase.ESCAPE_CLEAR));
            writes.add(new Write(step.x(), step.y() + 2, step.z(), Phase.ESCAPE_CLEAR));
        }

        Set<Coordinate> plannedBeforeSurface = new HashSet<>();
        for (Write write : writes) {
            plannedBeforeSurface.add(new Coordinate(write.x(), write.y(), write.z()));
        }
        List<EscapeStep> shellProbes = escapeShellProbes(writes, plannedBeforeSurface);
        EscapeRoute escapeRoute = new EscapeRoute(escape.steps(), shellProbes, escape.tailSteps(),
                escape.entry(), escape.exit(), escape.closureCell(), escape.surfacePlug());

        // Surface powder follows cushion and clear work; a valid thin layer is removed only after replacement.
        for (SubterraneanTrapLayout.Cell cell : powder) {
            writes.add(new Write(cell.x(), surfaceFirstAir[cell.x()][cell.z()] - 1, cell.z(), Phase.SURFACE_POWDER));
        }
        for (SubterraneanTrapLayout.Cell cell : powder) {
            if (surfaceKind[cell.x()][cell.z()] == THIN_SNOW
                    || surfaceKind[cell.x()][cell.z()] == THIN_OVER_FULL_SNOW) {
                writes.add(new Write(cell.x(), surfaceFirstAir[cell.x()][cell.z()], cell.z(),
                        Phase.REMOVE_SURFACE_LAYER));
            }
        }
        return new Plan(roofY, landingY, escapeRoute, writes);
    }

    private static AlternativesResult noAlternatives(Rejection rejection) {
        return new AlternativesResult(List.of(), rejection);
    }

    private static Result rejected(Rejection rejection) {
        return new Result(null, rejection);
    }

    private static boolean isLocalSurface(int[][] surface) {
        if (surface == null || surface.length != SubterraneanTrapLayout.LOCAL_SIZE) {
            return false;
        }
        for (int[] column : surface) {
            if (column == null || column.length != SubterraneanTrapLayout.LOCAL_SIZE) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSnowCovered(int kind) {
        return kind == FULL_SNOW || kind == POWDER || kind == THIN_SNOW || kind == THIN_OVER_FULL_SNOW;
    }

    private static boolean isFirmSnow(int kind) {
        return kind == FULL_SNOW || kind == THIN_SNOW || kind == THIN_OVER_FULL_SNOW;
    }

    private static TerrainFitCells terrainFitCells(SubterraneanTrapLayout.Placement placement,
                                                    Set<SubterraneanTrapLayout.Cell> powder) {
        Set<SubterraneanTrapLayout.Cell> collar = new HashSet<>();
        for (SubterraneanTrapLayout.Cell cell : powder) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    SubterraneanTrapLayout.Cell neighbour =
                            new SubterraneanTrapLayout.Cell(cell.x() + dx, cell.z() + dz);
                    if ((dx != 0 || dz != 0) && inLocalBounds(neighbour) && !powder.contains(neighbour)) {
                        collar.add(neighbour);
                    }
                }
            }
        }

        int minStation = powder.stream().mapToInt(cell -> longitudinal(placement, cell)).min().orElseThrow();
        int maxStation = powder.stream().mapToInt(cell -> longitudinal(placement, cell)).max().orElseThrow();
        SubterraneanTrapLayout.Cell lowEnd = bearingCell(placement, powder, minStation);
        SubterraneanTrapLayout.Cell highEnd = bearingCell(placement, powder, maxStation);
        Set<SubterraneanTrapLayout.Cell> firmApproach = new HashSet<>();
        Set<SubterraneanTrapLayout.Cell> reliefProbes = new HashSet<>();
        for (int distance = 1; distance <= APPROACH_RELIEF_PROBE_LENGTH; distance++) {
            SubterraneanTrapLayout.Cell low = alongAxis(placement, lowEnd, -distance);
            SubterraneanTrapLayout.Cell high = alongAxis(placement, highEnd, distance);
            if (!inLocalBounds(low) || !inLocalBounds(high)) {
                return null;
            }
            reliefProbes.add(low);
            reliefProbes.add(high);
            if (distance <= FIRM_APPROACH_LENGTH) {
                firmApproach.add(low);
                firmApproach.add(high);
            }
        }

        Set<SubterraneanTrapLayout.Cell> reliefSample = new HashSet<>(powder);
        reliefSample.addAll(collar);
        reliefSample.addAll(reliefProbes);
        Set<SubterraneanTrapLayout.Cell> stepSample = new HashSet<>(powder);
        stepSample.addAll(collar);
        stepSample.addAll(firmApproach);
        return new TerrainFitCells(Set.copyOf(collar), Set.copyOf(firmApproach), Set.copyOf(reliefSample),
                Set.copyOf(stepSample));
    }

    private static int longitudinal(SubterraneanTrapLayout.Placement placement,
                                    SubterraneanTrapLayout.Cell cell) {
        return placement.template().axis() == SubterraneanTrapLayout.Axis.X ? cell.x() : cell.z();
    }

    private static int transverse(SubterraneanTrapLayout.Placement placement,
                                  SubterraneanTrapLayout.Cell cell) {
        return placement.template().axis() == SubterraneanTrapLayout.Axis.X ? cell.z() : cell.x();
    }

    private static SubterraneanTrapLayout.Cell bearingCell(SubterraneanTrapLayout.Placement placement,
                                                           Set<SubterraneanTrapLayout.Cell> powder, int station) {
        return powder.stream().filter(cell -> longitudinal(placement, cell) == station)
                .min(Comparator.comparingInt(cell -> transverse(placement, cell))).orElseThrow();
    }

    private static SubterraneanTrapLayout.Cell alongAxis(SubterraneanTrapLayout.Placement placement,
                                                         SubterraneanTrapLayout.Cell origin, int distance) {
        return placement.template().axis() == SubterraneanTrapLayout.Axis.X
                ? new SubterraneanTrapLayout.Cell(origin.x() + distance, origin.z())
                : new SubterraneanTrapLayout.Cell(origin.x(), origin.z() + distance);
    }

    private static boolean inLocalBounds(SubterraneanTrapLayout.Cell cell) {
        return cell.x() >= 0 && cell.x() < SubterraneanTrapLayout.LOCAL_SIZE
                && cell.z() >= 0 && cell.z() < SubterraneanTrapLayout.LOCAL_SIZE;
    }

    private static boolean heightRangeExceeds(Set<SubterraneanTrapLayout.Cell> cells, int[][] firstAir,
                                              int allowedRange) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (SubterraneanTrapLayout.Cell cell : cells) {
            minimum = Math.min(minimum, firstAir[cell.x()][cell.z()]);
            maximum = Math.max(maximum, firstAir[cell.x()][cell.z()]);
        }
        return (long) maximum - minimum > allowedRange;
    }

    private static boolean cardinalStepsStable(Set<SubterraneanTrapLayout.Cell> cells, int[][] firstAir) {
        for (SubterraneanTrapLayout.Cell cell : cells) {
            for (SubterraneanTrapLayout.Cell neighbour : List.of(
                    new SubterraneanTrapLayout.Cell(cell.x() + 1, cell.z()),
                    new SubterraneanTrapLayout.Cell(cell.x(), cell.z() + 1))) {
                if (cells.contains(neighbour)
                        && Math.abs((long) firstAir[cell.x()][cell.z()] - firstAir[neighbour.x()][neighbour.z()]) > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Clockwise, duplicate-free perimeter of the powder bounding box expanded by two cells. The low doorway
     * consumes the inner bank cell; the first rising route cell is therefore on this perimeter.
     */
    private static List<SubterraneanTrapLayout.Cell> expandedPowderPerimeter(
            Set<SubterraneanTrapLayout.Cell> powder) {
        int minX = powder.stream().mapToInt(SubterraneanTrapLayout.Cell::x).min().orElseThrow() - 2;
        int maxX = powder.stream().mapToInt(SubterraneanTrapLayout.Cell::x).max().orElseThrow() + 2;
        int minZ = powder.stream().mapToInt(SubterraneanTrapLayout.Cell::z).min().orElseThrow() - 2;
        int maxZ = powder.stream().mapToInt(SubterraneanTrapLayout.Cell::z).max().orElseThrow() + 2;
        List<SubterraneanTrapLayout.Cell> perimeter = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            perimeter.add(new SubterraneanTrapLayout.Cell(x, minZ));
        }
        for (int z = minZ + 1; z <= maxZ; z++) {
            perimeter.add(new SubterraneanTrapLayout.Cell(maxX, z));
        }
        for (int x = maxX - 1; x >= minX; x--) {
            perimeter.add(new SubterraneanTrapLayout.Cell(x, maxZ));
        }
        for (int z = maxZ - 1; z > minZ; z--) {
            perimeter.add(new SubterraneanTrapLayout.Cell(minX, z));
        }
        return List.copyOf(perimeter);
    }

    private static boolean hasRawElevationCapacity(Set<SubterraneanTrapLayout.Cell> powder,
                                                   List<SubterraneanTrapLayout.Cell> perimeter,
                                                   int[][] firstAir, int landingY) {
        for (EdgeCandidate edge : edgeCandidates(powder, perimeter)) {
            int start = perimeter.indexOf(edge.routeStart());
            if (start < 0) {
                continue;
            }
            for (int direction : List.of(1, -1)) {
                for (int routeLength = 5; routeLength <= perimeter.size() - 1; routeLength++) {
                    SubterraneanTrapLayout.Cell finalTail = perimeter.get(
                            Math.floorMod(start + direction * (routeLength - 1), perimeter.size()));
                    if (inSurfaceBounds(finalTail)
                            && landingY + routeLength == firstAir[finalTail.x()][finalTail.z()] - 2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<EscapeGeometry> escapeGeometries(Set<SubterraneanTrapLayout.Cell> powder,
                                                         List<SubterraneanTrapLayout.Cell> perimeter,
                                                         int[][] firstAir, int[][] surfaceKind,
                                                         int landingY) {
        List<EscapeGeometry> alternatives = new ArrayList<>();
        for (EdgeCandidate edge : edgeCandidates(powder, perimeter)) {
            int start = perimeter.indexOf(edge.routeStart());
            if (start < 0 || !writeCellInOwner(edge.doorway())) {
                continue;
            }
            for (int direction : List.of(1, -1)) {
                List<SubterraneanTrapLayout.Cell> rotated =
                        rotatedPerimeter(perimeter, start, direction);
                // Three already-open steps plus a two-block mining tail is the minimum meaningful route. The
                // final perimeter cell is reserved as at least one intact closure between the high and low ends.
                for (int routeLength = 5; routeLength <= rotated.size() - 1; routeLength++) {
                    SubterraneanTrapLayout.Cell finalTail = rotated.get(routeLength - 1);
                    SubterraneanTrapLayout.Cell closure = rotated.get(routeLength);
                    if (!inSurfaceBounds(finalTail) || !shellCellInOwner(closure)
                            || !isSnowBlockPlug(surfaceKind[finalTail.x()][finalTail.z()])
                            || landingY + routeLength != firstAir[finalTail.x()][finalTail.z()] - 2
                            || cardinalDistance(finalTail, edge.routeStart()) <= 1
                            || cardinalDistance(finalTail, edge.doorway()) <= 1) {
                        continue;
                    }
                    for (int tailLength : List.of(2, 3)) {
                        int clearCount = routeLength - tailLength;
                        if (clearCount < 3
                                || !usedRouteCellsAreSafe(rotated, routeLength, edge.doorway(), powder)
                                || !tailWritesStayConcealed(rotated, clearCount, routeLength, landingY,
                                        firstAir, surfaceKind)) {
                            continue;
                        }
                        List<EscapeStep> steps = new ArrayList<>(clearCount);
                        for (int index = 0; index < clearCount; index++) {
                            SubterraneanTrapLayout.Cell cell = rotated.get(index);
                            steps.add(new EscapeStep(cell.x(), landingY + index, cell.z()));
                        }
                        List<EscapeStep> tail = new ArrayList<>(tailLength);
                        for (int index = clearCount; index < routeLength; index++) {
                            SubterraneanTrapLayout.Cell cell = rotated.get(index);
                            tail.add(new EscapeStep(cell.x(), landingY + index, cell.z()));
                        }
                        EscapeStep entry = new EscapeStep(edge.doorway().x(), landingY, edge.doorway().z());
                        EscapeStep surfacePlug = new EscapeStep(
                                finalTail.x(), landingY + routeLength, finalTail.z());
                        EscapeStep closureCell =
                                new EscapeStep(closure.x(), landingY + routeLength, closure.z());
                        alternatives.add(new EscapeGeometry(
                                steps, tail, entry, tail.getLast(), closureCell, surfacePlug,
                                direction == 1 ? 0 : 1, routeLength, tailLength));
                    }
                }
            }
        }
        alternatives.sort(Comparator
                .comparingInt((EscapeGeometry geometry) -> geometry.entry().x())
                .thenComparingInt(geometry -> geometry.entry().z())
                .thenComparingInt(EscapeGeometry::directionOrder)
                .thenComparingInt(EscapeGeometry::routeLength)
                .thenComparingInt(EscapeGeometry::tailLength));
        List<EscapeGeometry> unique = new ArrayList<>();
        Set<EscapeGeometry> seen = new HashSet<>();
        for (EscapeGeometry alternative : alternatives) {
            if (seen.add(alternative)) {
                unique.add(alternative);
                if (unique.size() == MAX_ALTERNATIVES_PER_DEPTH) {
                    break;
                }
            }
        }
        return List.copyOf(unique);
    }

    /**
     * Derives powder-edge doorway candidates from the same expanded perimeter. The caller opens exactly one
     * two-block-high bank cell; no broad chamber dilation remains.
     */
    private static List<EdgeCandidate> edgeCandidates(Set<SubterraneanTrapLayout.Cell> powder,
                                                      List<SubterraneanTrapLayout.Cell> perimeter) {
        int minX = perimeter.stream().mapToInt(SubterraneanTrapLayout.Cell::x).min().orElseThrow() + 2;
        int maxX = perimeter.stream().mapToInt(SubterraneanTrapLayout.Cell::x).max().orElseThrow() - 2;
        int minZ = perimeter.stream().mapToInt(SubterraneanTrapLayout.Cell::z).min().orElseThrow() + 2;
        int maxZ = perimeter.stream().mapToInt(SubterraneanTrapLayout.Cell::z).max().orElseThrow() - 2;
        List<EdgeCandidate> candidates = new ArrayList<>();
        Set<EdgeCandidate> unique = new HashSet<>();
        for (SubterraneanTrapLayout.Cell cell : powder.stream().sorted(CELL_ORDER).toList()) {
            if (cell.x() == minX) {
                addEdgeCandidate(candidates, unique, cell, -1, 0);
            }
            if (cell.x() == maxX) {
                addEdgeCandidate(candidates, unique, cell, 1, 0);
            }
            if (cell.z() == minZ) {
                addEdgeCandidate(candidates, unique, cell, 0, -1);
            }
            if (cell.z() == maxZ) {
                addEdgeCandidate(candidates, unique, cell, 0, 1);
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt((EdgeCandidate edge) -> edge.doorway().x())
                        .thenComparingInt(edge -> edge.doorway().z())
                        .thenComparingInt(edge -> edge.routeStart().x())
                        .thenComparingInt(edge -> edge.routeStart().z()))
                .toList();
    }

    private static void addEdgeCandidate(List<EdgeCandidate> candidates, Set<EdgeCandidate> unique,
                                         SubterraneanTrapLayout.Cell powder, int dx, int dz) {
        EdgeCandidate candidate = new EdgeCandidate(
                new SubterraneanTrapLayout.Cell(powder.x() + dx, powder.z() + dz),
                new SubterraneanTrapLayout.Cell(powder.x() + 2 * dx, powder.z() + 2 * dz));
        if (unique.add(candidate)) {
            candidates.add(candidate);
        }
    }

    private static List<SubterraneanTrapLayout.Cell> rotatedPerimeter(
            List<SubterraneanTrapLayout.Cell> perimeter, int start, int direction) {
        List<SubterraneanTrapLayout.Cell> rotated = new ArrayList<>(perimeter.size());
        for (int index = 0; index < perimeter.size(); index++) {
            rotated.add(perimeter.get(Math.floorMod(start + direction * index, perimeter.size())));
        }
        return List.copyOf(rotated);
    }

    private static boolean usedRouteCellsAreSafe(List<SubterraneanTrapLayout.Cell> rotated,
                                                 int plugDistance,
                                                 SubterraneanTrapLayout.Cell doorway,
                                                 Set<SubterraneanTrapLayout.Cell> powder) {
        if (powder.contains(doorway) || !writeCellInOwner(doorway)) {
            return false;
        }
        Set<SubterraneanTrapLayout.Cell> unique = new HashSet<>();
        unique.add(doorway);
        for (int index = 0; index < plugDistance; index++) {
            SubterraneanTrapLayout.Cell cell = rotated.get(index);
            if (!writeCellInOwner(cell) || powder.contains(cell) || !unique.add(cell)) {
                return false;
            }
        }
        return true;
    }

    private static boolean tailWritesStayConcealed(List<SubterraneanTrapLayout.Cell> rotated,
                                                   int clearCount, int routeLength, int landingY,
                                                   int[][] firstAir, int[][] surfaceKind) {
        SubterraneanTrapLayout.Cell finalTail = rotated.get(routeLength - 1);
        int finalHeadY = landingY + routeLength + 1;
        if (finalHeadY != firstAir[finalTail.x()][finalTail.z()] - 1
                || !isSnowBlockPlug(surfaceKind[finalTail.x()][finalTail.z()])) {
            return false;
        }
        for (int index = clearCount; index < routeLength; index++) {
            SubterraneanTrapLayout.Cell cell = rotated.get(index);
            int surfaceSupportY = firstAir[cell.x()][cell.z()] - 1;
            boolean isFinalTail = index == routeLength - 1;
            for (int y = landingY + index; y <= landingY + index + 2; y++) {
                if (y > surfaceSupportY
                        || (y == surfaceSupportY
                        && (!isFinalTail || !isSnowBlockPlug(surfaceKind[cell.x()][cell.z()])))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<EscapeStep> openEscapeCells(EscapeGeometry escape) {
        List<EscapeStep> open = new ArrayList<>(escape.steps().size() + 1);
        open.add(escape.entry());
        open.addAll(escape.steps());
        return List.copyOf(open);
    }

    private static List<EscapeStep> escapeShellProbes(List<Write> writes, Set<Coordinate> planned) {
        Set<Coordinate> probes = new HashSet<>();
        int[][] offsets = {
                {-1, 0, 0}, {1, 0, 0}, {0, -1, 0},
                {0, 1, 0}, {0, 0, -1}, {0, 0, 1}
        };
        for (Write write : writes) {
            if (write.phase() != Phase.ESCAPE_CLEAR && write.phase() != Phase.ESCAPE_MINE_TAIL) {
                continue;
            }
            for (int[] offset : offsets) {
                Coordinate probe = new Coordinate(write.x() + offset[0], write.y() + offset[1],
                        write.z() + offset[2]);
                if (!planned.contains(probe)) {
                    probes.add(probe);
                }
            }
        }
        return probes.stream()
                .sorted(Comparator.comparingInt(Coordinate::x).thenComparingInt(Coordinate::y)
                        .thenComparingInt(Coordinate::z))
                .map(probe -> new EscapeStep(probe.x(), probe.y(), probe.z()))
                .toList();
    }

    private static boolean isSnowBlockPlug(int kind) {
        return kind == FULL_SNOW || kind == THIN_OVER_FULL_SNOW;
    }

    private static boolean inSurfaceBounds(SubterraneanTrapLayout.Cell cell) {
        return cell.x() >= 0 && cell.x() < SubterraneanTrapLayout.LOCAL_SIZE
                && cell.z() >= 0 && cell.z() < SubterraneanTrapLayout.LOCAL_SIZE;
    }

    private static boolean writeCellInOwner(SubterraneanTrapLayout.Cell cell) {
        return cell.x() >= 1 && cell.x() <= SubterraneanTrapLayout.LOCAL_SIZE - 2
                && cell.z() >= 1 && cell.z() <= SubterraneanTrapLayout.LOCAL_SIZE - 2;
    }

    private static boolean shellCellInOwner(SubterraneanTrapLayout.Cell cell) {
        return cell.x() >= 0 && cell.x() < SubterraneanTrapLayout.LOCAL_SIZE
                && cell.z() >= 0 && cell.z() < SubterraneanTrapLayout.LOCAL_SIZE;
    }

    private static int cardinalDistance(SubterraneanTrapLayout.Cell first,
                                        SubterraneanTrapLayout.Cell second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z());
    }
}
