package com.example.globe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure-Java validation and write planning for a surface powder trap.
 *
 * <p>The landing is deliberately a one-way transition into a deeper natural cave layer. A plan is returned
 * only when the caller supplies a connected, untouched lower-cave witness. The authored connection is always
 * two blocks wide, three blocks high, owner-local, and level-or-descending from the cushion. There is no
 * surface stair, mining tail, or intact plug in this grammar.
 */
public final class SubterraneanTrapPlan {

    public static final int OTHER = 0;
    public static final int FULL_SNOW = 1;
    public static final int POWDER = 2;
    public static final int THIN_SNOW = 3;
    /** A top snow layer directly over a snow block; the effective surface is the block beneath the layer. */
    public static final int THIN_OVER_FULL_SNOW = 4;
    /** Exposed packed/blue ice: stable glacial bearing around, never a concealed powder-mouth surface. */
    public static final int FIRM_GLACIAL_ICE = 5;

    public static final int PREFERRED_DEPTH = 32;
    public static final int MIN_LEGAL_DEPTH = 24;
    public static final int MAX_LEGAL_DEPTH = 96;
    public static final int MIN_HARD_DEPTH = 18;
    public static final int MAX_HARD_DEPTH = 96;
    public static final int MIN_ROUTE_DESCENT = 3;
    public static final int ROUTE_WIDTH = 2;
    public static final int ROUTE_CLEAR_HEIGHT = 3;
    public static final int MIN_NATURAL_CONTINUATION_FLOORS = 8;
    private static final int MAX_ROUTE_DESCENT = 8;
    private static final int MAX_ALTERNATIVES_PER_DEPTH = 34;
    private static final List<Integer> PREFERRED_DEPTH_ORDER =
            List.of(32, 33, 31, 34, 30, 35, 29, 36, 28);
    private static final List<String> AUTHORED_ROUTE_ACTION_WORDS = authoredRouteActionWords();
    private static final Comparator<RouteCell> ROUTE_CELL_ORDER = Comparator
            .comparingInt(RouteCell::x).thenComparingInt(RouteCell::y).thenComparingInt(RouteCell::z);

    /* Canonical east-going two-lobe floor.  It deliberately has a narrow, bent throat rather than a room. */
    private static final List<SubterraneanTrapLayout.Cell> CANONICAL_CAVERN = cavernCells();

    private enum CavernRegion { PRIMARY, SECONDARY, THROAT }

    /** Three firm cells establish a walkable bearing; the fourth is a local relief probe only. */
    private static final int FIRM_APPROACH_LENGTH = 3;
    private static final int APPROACH_RELIEF_PROBE_LENGTH = 4;

    /** The only material actions an accepted plan can ask a runtime adapter to perform. */
    public enum Phase {
        CUSHION_BASE,
        CUSHION,
        DESCENT_FLOOR,
        CLEAR,
        DESCENT_CLEAR,
        /** Irregular cross-chunk glacial-cavern floor at the foot of an otherwise unchanged descent. */
        AUTHORED_CAVERN_FLOOR,
        /** Variable-height headroom in the irregular cross-chunk glacial cavern. */
        AUTHORED_CAVERN_CLEAR,
        SURFACE_POWDER,
        REMOVE_SURFACE_LAYER,
        /** Legacy compatibility only; the active planner never emits this phase. */
        ESCAPE_FLOOR,
        /** Legacy compatibility only; the active planner never emits this phase. */
        ESCAPE_MINE_TAIL,
        /** Legacy compatibility only; the active planner never emits this phase. */
        ESCAPE_CLEAR
    }

    /** Whether the stair ends in pre-existing cave space or the bounded fallback cavern. */
    public enum DestinationKind {
        NATURAL_CAVE,
        AUTHORED_CAVERN
    }

    /** Why a requested plan was rejected before any write list was created. */
    public enum Rejection {
        INVALID_INPUT,
        POWDER_RELIEF_EXCEEDS_ONE,
        APPROACH_RING_UNSTABLE,
        UNSUPPORTED_SURFACE,
        DEPTH_OUTSIDE_HARD_LIMIT,
        DEPTH_OUTSIDE_LEGAL_RANGE,
        LOWER_CAVE_REQUIRED,
        DESCENT_ROUTE_CAPACITY,
        DESCENT_OWNER_BOUNDS
    }

    /** First failed surface law when the public rejection remains {@link Rejection#UNSUPPORTED_SURFACE}. */
    public enum SurfaceRejectionReason {
        MOUTH_FIRM_GLACIAL,
        MOUTH_OTHER,
        COLLAR_OTHER,
        APPROACH_OTHER,
        APPROACH_POWDER,
        INVALID_INPUT
    }

    /** One immutable world-coordinate write. */
    public record Write(int x, int y, int z, Phase phase) {
        public Write {
            Objects.requireNonNull(phase, "phase");
        }
    }

    /** One local floor, headroom, or shell coordinate. */
    public record RouteCell(int x, int y, int z) {
    }

    /** One two-wide route station. Both floor cells share a Y and touch cardinally. */
    public record RouteStep(int y, List<RouteCell> floorCells) {
        public RouteStep {
            floorCells = List.copyOf(floorCells);
            if (floorCells.size() != ROUTE_WIDTH
                    || floorCells.stream().anyMatch(cell -> cell == null || cell.y() != y)
                    || cardinalDistance(floorCells.get(0), floorCells.get(1)) != 1) {
                throw new IllegalArgumentException("a route step must be exactly two adjacent floor cells");
            }
        }
    }

    /**
     * A geometry probe whose target opening must be certified by the runtime before it becomes a destination.
     * The target row is natural and untouched; authored steps stop one row before it.
     */
    public record DestinationProbe(int entryX, int entryZ, int targetX, int targetZ,
                                   int widthDx, int widthDz, int forwardDx, int forwardDz,
                                   int floorY, int distance) {
        public DestinationProbe {
            if (!isCardinalUnit(widthDx, widthDz) || !isCardinalUnit(forwardDx, forwardDz)
                    || widthDx * forwardDx + widthDz * forwardDz != 0
                    || distance < MIN_ROUTE_DESCENT || distance > MAX_ROUTE_DESCENT
                    || floorY <= 0
                    || targetX != entryX + forwardDx * distance
                    || targetZ != entryZ + forwardDz * distance) {
                throw new IllegalArgumentException("destination probe must describe a straight two-wide descent");
            }
        }

        public List<RouteCell> targetFloors() {
            return List.of(
                    new RouteCell(targetX, floorY, targetZ),
                    new RouteCell(targetX + widthDx, floorY, targetZ + widthDz));
        }
    }

    /** Runtime-certified natural lower cave: target row plus a connected, untouched continuation footprint. */
    public record NaturalCaveDestination(DestinationProbe probe, List<RouteCell> continuationFloors) {
        public NaturalCaveDestination {
            Objects.requireNonNull(probe, "probe");
            continuationFloors = List.copyOf(continuationFloors);
            Set<RouteCell> unique = new LinkedHashSet<>(continuationFloors);
            if (continuationFloors.size() < MIN_NATURAL_CONTINUATION_FLOORS
                    || unique.size() != continuationFloors.size()
                    || continuationFloors.stream().anyMatch(Objects::isNull)
                    || !unique.containsAll(probe.targetFloors())
                    || !continuationAvoidsAuthoredRoute(probe, unique)
                    || !connectedNaturalFloors(unique)) {
                throw new IllegalArgumentException("destination must contain a connected lower natural cave");
            }
        }
    }

    /** Distinct endpoint authority: natural evidence is never reused to stand in for authored geometry. */
    public sealed interface Endpoint permits NaturalEndpoint, AuthoredCavernEndpoint {
        int floorY();
    }

    public record NaturalEndpoint(NaturalCaveDestination destination) implements Endpoint {
        public NaturalEndpoint {
            Objects.requireNonNull(destination, "destination");
        }

        @Override
        public int floorY() {
            return destination.probe().floorY();
        }
    }

    /** Rotation of the canonical east-going cavern into exactly one cardinal neighbour. */
    public enum CavernDirection {
        EAST(1, 0), SOUTH(0, 1), WEST(-1, 0), NORTH(0, -1);

        private final int chunkDx;
        private final int chunkDz;

        CavernDirection(int chunkDx, int chunkDz) {
            this.chunkDx = chunkDx;
            this.chunkDz = chunkDz;
        }

        public int chunkDx() { return chunkDx; }
        public int chunkDz() { return chunkDz; }

        public RouteCell rotate(int canonicalX, int y, int canonicalZ) {
            return switch (this) {
                case EAST -> new RouteCell(canonicalX, y, canonicalZ);
                case SOUTH -> new RouteCell(15 - canonicalZ, y, canonicalX);
                case WEST -> new RouteCell(15 - canonicalX, y, 15 - canonicalZ);
                case NORTH -> new RouteCell(canonicalZ, y, 15 - canonicalX);
            };
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

        public boolean containsWriteCell(int localX, int localZ) {
            int x = canonicalX(localX, localZ);
            int z = canonicalZ(localX, localZ);
            return x >= 1 && x <= 30 && z >= 1 && z <= 14;
        }

        public boolean containsReadShellCell(int localX, int localZ) {
            int x = canonicalX(localX, localZ);
            int z = canonicalZ(localX, localZ);
            return x >= 0 && x <= 31 && z >= 0 && z <= 15;
        }
    }

    /** One of several concealed two-wide entries on the owner-chunk side of the cavern. */
    public enum CavernPortal {
        WEST_3(10, 3, 10, 4, -1, 0),
        WEST_4(10, 4, 10, 5, -1, 0),
        WEST_5(10, 5, 10, 6, -1, 0),
        WEST_6(10, 6, 10, 7, -1, 0),
        NORTH_12(11, 2, 12, 2, 0, -1),
        NORTH_13(12, 2, 13, 2, 0, -1),
        SOUTH_12(11, 8, 12, 8, 0, 1),
        SOUTH_13(12, 8, 13, 8, 0, 1);

        private final int firstX;
        private final int firstZ;
        private final int secondX;
        private final int secondZ;
        private final int outwardX;
        private final int outwardZ;

        CavernPortal(int firstX, int firstZ, int secondX, int secondZ, int outwardX, int outwardZ) {
            this.firstX = firstX;
            this.firstZ = firstZ;
            this.secondX = secondX;
            this.secondZ = secondZ;
            this.outwardX = outwardX;
            this.outwardZ = outwardZ;
        }
    }

    /**
     * An irregular two-lobe cavern extending from the owner into exactly one cardinal neighbour.
     * Canonical writes occupy x=10..29,z=2..13 and its read-only shell occupies x=9..30,z=1..14.
     * The extra outer inset keeps the complete two-block magma dilation inside the authorized two chunks.
     */
    public record AuthoredCavernEndpoint(
            int floorY, CavernDirection direction, CavernPortal portal) implements Endpoint {
        public AuthoredCavernEndpoint {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(portal, "portal");
            if (floorY <= 0) {
                throw new IllegalArgumentException("authored cavern must remain entirely above Y0");
            }
        }

        public List<RouteCell> primaryLobeFloors() { return floorsFor(CavernRegion.PRIMARY); }
        public List<RouteCell> secondaryLobeFloors() { return floorsFor(CavernRegion.SECONDARY); }
        public List<RouteCell> throatFloors() { return floorsFor(CavernRegion.THROAT); }

        public List<RouteCell> floorCells() {
            return CANONICAL_CAVERN.stream().map(this::floorCell).sorted(ROUTE_CELL_ORDER).toList();
        }

        public List<RouteCell> clearCells() {
            List<RouteCell> result = new ArrayList<>();
            for (SubterraneanTrapLayout.Cell cell : CANONICAL_CAVERN) {
                RouteCell floor = floorCell(cell);
                for (int dy = 1; dy <= clearHeight(cell); dy++) {
                    result.add(new RouteCell(floor.x(), floor.y() + dy, floor.z()));
                }
            }
            return result.stream().sorted(ROUTE_CELL_ORDER).toList();
        }

        public List<RouteCell> portalFloors() {
            return List.of(direction.rotate(portal.firstX, floorY + 2, portal.firstZ),
                    direction.rotate(portal.secondX, floorY + 2, portal.secondZ));
        }

        public List<RouteCell> approachFloors() {
            return List.of(direction.rotate(portal.firstX + portal.outwardX, floorY + 2,
                            portal.firstZ + portal.outwardZ),
                    direction.rotate(portal.secondX + portal.outwardX, floorY + 2,
                            portal.secondZ + portal.outwardZ));
        }

        public List<RouteCell> substrateProbes() {
            return floorCells().stream().map(cell -> new RouteCell(cell.x(), cell.y() - 1, cell.z())).toList();
        }

        public List<RouteCell> ceilingProbes() {
            return CANONICAL_CAVERN.stream().map(cell -> {
                RouteCell floor = floorCell(cell);
                return new RouteCell(floor.x(), floor.y() + clearHeight(cell) + 1, floor.z());
            }).sorted(ROUTE_CELL_ORDER).toList();
        }

        public List<RouteCell> perimeterProbes() {
            Set<RouteCell> volume = new HashSet<>(floorCells());
            volume.addAll(clearCells());
            Set<String> approaches = approachFloors().stream()
                    .map(cell -> cell.x() + ":" + cell.z()).collect(java.util.stream.Collectors.toSet());
            Set<RouteCell> perimeter = new LinkedHashSet<>();
            int[][] horizontal = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (RouteCell cell : volume) {
                for (int[] offset : horizontal) {
                    RouteCell neighbour = new RouteCell(cell.x() + offset[0], cell.y(), cell.z() + offset[1]);
                    if (!volume.contains(neighbour)
                            && !approaches.contains(neighbour.x() + ":" + neighbour.z())) {
                        perimeter.add(neighbour);
                    }
                }
            }
            return perimeter.stream().sorted(ROUTE_CELL_ORDER).toList();
        }

        public List<RouteCell> shellProbes() {
            Set<RouteCell> shell = new LinkedHashSet<>(substrateProbes());
            shell.addAll(ceilingProbes());
            shell.addAll(perimeterProbes());
            return shell.stream().sorted(ROUTE_CELL_ORDER).toList();
        }

        private List<RouteCell> floorsFor(CavernRegion region) {
            return CANONICAL_CAVERN.stream().filter(cell -> inRegion(cell.x(), cell.z(), region))
                    .map(this::floorCell).sorted(ROUTE_CELL_ORDER).toList();
        }

        private RouteCell floorCell(SubterraneanTrapLayout.Cell cell) {
            return direction.rotate(cell.x(), floorY + floorOffset(cell.x(), cell.z()), cell.z());
        }
    }

    /** Complete owner-local descending connection proof. */
    public record DescentRoute(List<RouteStep> steps, List<RouteCell> shellProbes,
                               Endpoint endpoint, int turns, int initialHeadingX,
                               int initialHeadingZ, String actionWord) {
        public DescentRoute {
            steps = List.copyOf(steps);
            shellProbes = List.copyOf(shellProbes);
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(actionWord, "actionWord");
            if (steps.isEmpty() || turns < 0 || turns > 2
                    || !isCardinalUnit(initialHeadingX, initialHeadingZ)
                    || actionWord.length() != steps.size() - 1
                    || !actionWordHeadingsNeverReverseInitial(
                            initialHeadingX, initialHeadingZ, actionWord)
                    || actionWordTurnCount(actionWord) != turns) {
                throw new IllegalArgumentException("a descent route needs authored steps");
            }
        }

        public DescentRoute(List<RouteStep> steps, List<RouteCell> shellProbes,
                            NaturalCaveDestination destination) {
            this(steps, shellProbes, new NaturalEndpoint(destination), 0,
                    destination.probe().forwardDx(), destination.probe().forwardDz(),
                    "F".repeat(Math.max(0, steps.size() - 1)));
        }

        /** Compatibility accessor for the unchanged natural path; authored routes return {@code null}. */
        public NaturalCaveDestination destination() {
            return endpoint instanceof NaturalEndpoint natural ? natural.destination() : null;
        }

        public boolean headingsNeverReverseInitial() {
            return actionWordHeadingsNeverReverseInitial(initialHeadingX, initialHeadingZ, actionWord);
        }
    }

    /** Immutable accepted geometry; depth is {@code roofY - landingY}. */
    public record Plan(int roofY, int landingY, DescentRoute descentRoute, DestinationKind destinationKind,
                       List<Write> writes) {
        public Plan {
            Objects.requireNonNull(descentRoute, "descentRoute");
            Objects.requireNonNull(destinationKind, "destinationKind");
            boolean naturalEndpoint = descentRoute.endpoint() instanceof NaturalEndpoint;
            if (naturalEndpoint != (destinationKind == DestinationKind.NATURAL_CAVE)) {
                throw new IllegalArgumentException("destination kind must match the route endpoint type");
            }
            writes = List.copyOf(writes);
            Set<Coordinate> coordinates = new HashSet<>();
            for (Write write : writes) {
                if (!coordinates.add(new Coordinate(write.x(), write.y(), write.z()))) {
                    throw new IllegalArgumentException("plan writes must have unique coordinates: "
                            + write.x() + "," + write.y() + "," + write.z() + " " + write.phase());
                }
            }
        }

        /** Compatibility constructor for the original, natural-cave-only plan surface. */
        public Plan(int roofY, int landingY, DescentRoute descentRoute, List<Write> writes) {
            this(roofY, landingY, descentRoute, DestinationKind.NATURAL_CAVE, writes);
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

    /**
     * Optional placement-level detail for a surface rejection. The ordinary {@link Result} remains the
     * compatibility API; callers that need a census can opt in without changing the placement verdict.
     */
    public record SurfaceDiagnosticResult(Result result, SurfaceRejectionReason surfaceRejectionReason) {
        public SurfaceDiagnosticResult {
            Objects.requireNonNull(result, "result");
            boolean surfaceRejected = result.rejection() == Rejection.UNSUPPORTED_SURFACE
                    || result.rejection() == Rejection.INVALID_INPUT;
            if (surfaceRejected != (surfaceRejectionReason != null)) {
                throw new IllegalArgumentException("surface detail must match a surface rejection");
            }
        }
    }

    private record Coordinate(int x, int y, int z) {
    }

    private record EntryGeometry(SubterraneanTrapLayout.Cell firstDoorway,
                                 SubterraneanTrapLayout.Cell secondDoorway,
                                 int widthDx, int widthDz, int forwardDx, int forwardDz) {
    }

    private record PlanningContext(List<SubterraneanTrapLayout.Cell> powder, int roofY, int landingY) {
        private PlanningContext {
            powder = List.copyOf(powder);
        }
    }

    private record PlanningContextResult(PlanningContext context, Rejection rejection,
                                         SurfaceRejectionReason surfaceRejectionReason) {
    }

    private record Footprint(SubterraneanTrapLayout.Cell first, SubterraneanTrapLayout.Cell second) {
        private Footprint {
            if (CELL_ORDER.compare(first, second) > 0) {
                SubterraneanTrapLayout.Cell swap = first;
                first = second;
                second = swap;
            }
            if (Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) != 1) {
                throw new IllegalArgumentException("station footprint must contain two adjacent cells");
            }
        }

        List<SubterraneanTrapLayout.Cell> cells() {
            return List.of(first, second);
        }
    }

    private record RoutePath(List<Footprint> stations, int turns) {
        private RoutePath {
            stations = List.copyOf(stations);
        }
    }

    private record AuthoredRoute(List<RouteStep> steps, int turns, int initialHeadingX,
                                 int initialHeadingZ, String actionWord) {
        private AuthoredRoute {
            steps = List.copyOf(steps);
        }
    }

    private record CataloguedRoute(RoutePath path, int initialHeadingX, int initialHeadingZ,
                                   String actionWord) {
    }

    private record AuthoredRouteIndexBuild(
            Map<Footprint, List<CataloguedRoute>> routes,
            int entryGeometries,
            int expansionCalls,
            int indexedPaths) {
    }

    /**
     * Direction- and depth-independent planar route catalogue for one mouth placement.
     *
     * <p>Runtime selection visits one placement at a time, so retaining this immutable value only for the
     * active placement avoids rebuilding the same dogleg grammar for every direction and landing depth.
     */
    public static final class AuthoredCavernCatalogue {
        private final SubterraneanTrapLayout.Placement placement;
        private final Map<Footprint, List<CataloguedRoute>> routes;
        private final AuthoredRouteBuildMeter buildMeter;

        private AuthoredCavernCatalogue(
                SubterraneanTrapLayout.Placement placement, AuthoredRouteIndexBuild build) {
            this.placement = placement;
            this.routes = build.routes();
            this.buildMeter = new AuthoredRouteBuildMeter(
                    build.entryGeometries(), AUTHORED_ROUTE_ACTION_WORDS.size(),
                    build.expansionCalls(), build.indexedPaths());
        }

        AuthoredRouteBuildMeter buildMeter() {
            return buildMeter;
        }
    }

    /** Package-private deterministic work meter used by the full 64-placement planner contract. */
    record AuthoredRouteBuildMeter(
            int entryGeometries, int actionWords, int expansionCalls, int indexedPaths) {
    }

    /** Pure diagnostic seam proving why a catalogue placement has no authored direction. */
    record AuthoredCavernRouteCensus(int indexedRoutes, int geometricallyEligible,
                                     int cushionCollisions, int cushionClear) {
    }

    private record AlternativesResult(List<Plan> plans, Rejection rejection,
                                      SurfaceRejectionReason surfaceRejectionReason) {
        private AlternativesResult {
            plans = List.copyOf(plans);
        }
    }

    private record TerrainFitCells(Set<SubterraneanTrapLayout.Cell> collar,
                                   Set<SubterraneanTrapLayout.Cell> firmApproach,
                                   Set<SubterraneanTrapLayout.Cell> reliefSample,
                                   Set<SubterraneanTrapLayout.Cell> stepSample) {
    }

    private static final Comparator<SubterraneanTrapLayout.Cell> CELL_ORDER =
            Comparator.comparingInt(SubterraneanTrapLayout.Cell::x).thenComparingInt(SubterraneanTrapLayout.Cell::z);
    private static final Comparator<DestinationProbe> PROBE_ORDER = Comparator
            .comparingInt(DestinationProbe::entryX).thenComparingInt(DestinationProbe::entryZ)
            .thenComparingInt(DestinationProbe::floorY)
            .thenComparingInt(DestinationProbe::targetX).thenComparingInt(DestinationProbe::targetZ);

    private SubterraneanTrapPlan() {
    }

    /** Missing lower-cave evidence always fails closed. */
    public static Result plan(SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir,
                              int[][] surfaceKind) {
        return plan(placement, surfaceFirstAir, surfaceKind, List.of());
    }

    /** Validates the surface plus caller-certified natural lower destinations. */
    public static Result plan(SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir,
                              int[][] surfaceKind, List<NaturalCaveDestination> destinations) {
        return planWithSurfaceDiagnostics(placement, surfaceFirstAir, surfaceKind, destinations).result();
    }

    /**
     * Returns the unchanged placement verdict plus the first surface subreason, when one exists.
     * This is diagnostic-only: it does not alter accepted plans or public rejection values.
     */
    public static SurfaceDiagnosticResult planWithSurfaceDiagnostics(
            SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir,
            int[][] surfaceKind, List<NaturalCaveDestination> destinations) {
        AlternativesResult result = planAlternativesResult(
                placement, surfaceFirstAir, surfaceKind, PREFERRED_DEPTH, destinations);
        if (!result.plans().isEmpty()) {
            return new SurfaceDiagnosticResult(new Result(result.plans().getFirst(), null), null);
        }
        return new SurfaceDiagnosticResult(new Result(null, result.rejection() == null
                ? Rejection.DESCENT_ROUTE_CAPACITY : result.rejection()), result.surfaceRejectionReason());
    }

    /** Exact near-first landing-depth order used by the bounded runtime selector. */
    public static List<Integer> preferredDepthOrder() {
        return PREFERRED_DEPTH_ORDER;
    }

    /**
     * Exact legacy near-first prefix followed by every deeper legal landing. The lower end is pruned per
     * surface so even the deepest possible destination probe remains strictly above Y0.
     */
    public static List<Integer> landingDepthOrder(int surfaceRoofY) {
        int deepestSafeLanding = Math.min(MAX_HARD_DEPTH, surfaceRoofY - MAX_ROUTE_DESCENT - 1);
        if (deepestSafeLanding < 36) {
            return PREFERRED_DEPTH_ORDER.stream()
                    .filter(depth -> depth <= deepestSafeLanding)
                    .toList();
        }
        List<Integer> depths = new ArrayList<>(
                PREFERRED_DEPTH_ORDER.size() + Math.max(0, deepestSafeLanding - 36));
        depths.addAll(PREFERRED_DEPTH_ORDER);
        for (int depth = 37; depth <= deepestSafeLanding; depth++) {
            depths.add(depth);
        }
        return List.copyOf(depths);
    }

    /** Missing lower-cave evidence always returns no alternatives. */
    public static List<Plan> planAlternatives(SubterraneanTrapLayout.Placement placement,
                                              int[][] surfaceFirstAir, int[][] surfaceKind, int depth) {
        return List.of();
    }

    /**
     * Bounded authored fallback. Natural evidence remains the preferred public path; this is a separate
     * cross-chunk cavern authority and never pretends that excavated space was a natural cave.
     */
    public static List<Plan> authoredCavernAlternatives(
            SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir, int[][] surfaceKind,
            int depth, CavernDirection direction) {
        if (placement == null || direction == null) {
            return List.of();
        }
        return authoredCavernAlternatives(
                authoredCavernCatalogue(placement), surfaceFirstAir, surfaceKind, depth, direction);
    }

    /** Builds the planar dogleg grammar once for reuse across every direction and landing depth. */
    public static AuthoredCavernCatalogue authoredCavernCatalogue(
            SubterraneanTrapLayout.Placement placement) {
        Objects.requireNonNull(placement, "placement");
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(placement.powder());
        return new AuthoredCavernCatalogue(
                placement, authoredRouteIndex(entryGeometries(powder)));
    }

    /**
     * Generates the same ordered alternatives as the compatibility overload without rebuilding planar routes.
     * Surface, landing-Y, collision, and cavern checks remain per invocation.
     */
    public static List<Plan> authoredCavernAlternatives(
            AuthoredCavernCatalogue catalogue, int[][] surfaceFirstAir, int[][] surfaceKind,
            int depth, CavernDirection direction) {
        if (catalogue == null || direction == null) {
            return List.of();
        }
        PlanningContextResult checked = planningContext(
                catalogue.placement, surfaceFirstAir, surfaceKind, depth);
        if (checked.context() == null || checked.context().landingY() <= 8) {
            return List.of();
        }
        PlanningContext context = checked.context();
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(context.powder());
        List<Plan> plans = new ArrayList<>();
        for (CavernPortal portal : CavernPortal.values()) {
            AuthoredCavernEndpoint endpoint = new AuthoredCavernEndpoint(
                    context.landingY() - 8, direction, portal);
            Footprint target = footprint(endpoint.approachFloors());
            for (CataloguedRoute catalogued : catalogue.routes.getOrDefault(target, List.of())) {
                AuthoredRoute route = authoredRoute(catalogued, endpoint, context.landingY(), powder);
                if (route == null) {
                    continue;
                }
                try {
                    plans.add(buildCavernPlan(context, surfaceFirstAir, surfaceKind, endpoint, route));
                } catch (IllegalArgumentException overlap) {
                    continue;
                }
                if (plans.size() == MAX_ALTERNATIVES_PER_DEPTH) {
                    return List.copyOf(plans);
                }
            }
        }
        return List.copyOf(plans);
    }

    static AuthoredCavernRouteCensus authoredCavernRouteCensus(
            SubterraneanTrapLayout.Placement placement, int landingY, CavernDirection direction) {
        if (placement == null || direction == null || landingY <= 8) {
            return new AuthoredCavernRouteCensus(0, 0, 0, 0);
        }
        return authoredCavernRouteCensus(authoredCavernCatalogue(placement), landingY, direction);
    }

    static AuthoredCavernRouteCensus authoredCavernRouteCensus(
            AuthoredCavernCatalogue catalogue, int landingY, CavernDirection direction) {
        if (catalogue == null || direction == null || landingY <= 8) {
            return new AuthoredCavernRouteCensus(0, 0, 0, 0);
        }
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(catalogue.placement.powder());
        int indexed = 0;
        int eligible = 0;
        int collisions = 0;
        int clear = 0;
        for (CavernPortal portal : CavernPortal.values()) {
            AuthoredCavernEndpoint endpoint = new AuthoredCavernEndpoint(landingY - 8, direction, portal);
            Footprint target = footprint(endpoint.approachFloors());
            Set<SubterraneanTrapLayout.Cell> cavern = endpoint.floorCells().stream()
                    .map(cell -> new SubterraneanTrapLayout.Cell(cell.x(), cell.z()))
                    .collect(java.util.stream.Collectors.toSet());
            for (CataloguedRoute route : catalogue.routes.getOrDefault(target, List.of())) {
                indexed++;
                if (route.path().stations().stream().flatMap(station -> station.cells().stream())
                        .anyMatch(cavern::contains)) {
                    continue;
                }
                char finalAction = route.actionWord().isEmpty() ? 'F'
                        : route.actionWord().charAt(route.actionWord().length() - 1);
                int finalX = headingX(route.initialHeadingX(), route.initialHeadingZ(), finalAction);
                int finalZ = headingZ(route.initialHeadingX(), route.initialHeadingZ(), finalAction);
                if (!translate(route.path().stations().getLast(), finalX, finalZ)
                        .equals(footprint(endpoint.portalFloors()))) {
                    continue;
                }
                eligible++;
                List<RouteStep> steps = new ArrayList<>(route.path().stations().size());
                for (int index = 0; index < route.path().stations().size(); index++) {
                    int y = landingY - Math.min(index + 1, 6);
                    Footprint station = route.path().stations().get(index);
                    steps.add(new RouteStep(y, station.cells().stream()
                            .map(cell -> new RouteCell(cell.x(), y, cell.z())).toList()));
                }
                if (!routeVolumeClearsLanding(steps, landingY, powder)
                        || authoredCavernOverlapsCushionOrShaft(endpoint, landingY, powder)) {
                    collisions++;
                } else {
                    clear++;
                }
            }
        }
        return new AuthoredCavernRouteCensus(indexed, eligible, collisions, clear);
    }

    private static boolean authoredCavernOverlapsCushionOrShaft(
            AuthoredCavernEndpoint endpoint, int landingY, Set<SubterraneanTrapLayout.Cell> powder) {
        return java.util.stream.Stream.concat(endpoint.floorCells().stream(), endpoint.clearCells().stream())
                .anyMatch(cell -> cell.y() >= landingY - 1
                        && powder.contains(new SubterraneanTrapLayout.Cell(cell.x(), cell.z())));
    }

    /** Complete alternatives for one landing and its runtime-certified lower cave witnesses. */
    public static List<Plan> planAlternatives(SubterraneanTrapLayout.Placement placement,
                                              int[][] surfaceFirstAir, int[][] surfaceKind, int depth,
                                              List<NaturalCaveDestination> destinations) {
        return planAlternativesResult(placement, surfaceFirstAir, surfaceKind, depth, destinations).plans();
    }

    /**
     * Returns the bounded world-read probes for a placement/landing. A runtime must certify three-air-high
     * target columns and a connected lower natural continuation before passing any probe back to the planner.
     */
    public static List<DestinationProbe> destinationProbes(SubterraneanTrapLayout.Placement placement,
                                                            int landingY) {
        if (placement == null) {
            return List.of();
        }
        List<DestinationProbe> probes = new ArrayList<>();
        Set<DestinationProbe> unique = new HashSet<>();
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(placement.powder());
        for (EntryGeometry entry : entryGeometries(powder)) {
            for (int descent = MIN_ROUTE_DESCENT; descent <= MAX_ROUTE_DESCENT; descent++) {
                int targetFloorY = landingY - descent;
                if (targetFloorY <= 0) {
                    continue;
                }
                int targetX = entry.firstDoorway().x() + entry.forwardDx() * descent;
                int targetZ = entry.firstDoorway().z() + entry.forwardDz() * descent;
                int secondX = targetX + entry.widthDx();
                int secondZ = targetZ + entry.widthDz();
                if (!shellCellInOwner(targetX, targetZ) || !shellCellInOwner(secondX, secondZ)) {
                    continue;
                }
                DestinationProbe probe = new DestinationProbe(
                        entry.firstDoorway().x(), entry.firstDoorway().z(), targetX, targetZ,
                        entry.widthDx(), entry.widthDz(), entry.forwardDx(), entry.forwardDz(),
                        targetFloorY, descent);
                if (routeVolumeClearsLanding(probe, landingY, powder)
                        && unique.add(probe)) {
                    probes.add(probe);
                }
            }
        }
        probes.sort(PROBE_ORDER);
        return List.copyOf(probes);
    }

    /**
     * Keeps the authored route disjoint from the landing's cushion and shaft writes before a probe can be
     * certified. Irregular powder ribbons can curl back in front of an otherwise-valid edge doorway; the first
     * few descending stations still reach the landing's vertical write range and therefore cannot pass through
     * one of those other powder columns.
     */
    private static boolean routeVolumeClearsLanding(DestinationProbe probe, int landingY,
                                                     Set<SubterraneanTrapLayout.Cell> powder) {
        return routeVolumeClearsLanding(routeSteps(probe, landingY), landingY, powder);
    }

    private static boolean routeVolumeClearsLanding(
            List<RouteStep> steps, int landingY, Set<SubterraneanTrapLayout.Cell> powder) {
        for (RouteStep step : steps) {
            for (RouteCell floor : step.floorCells()) {
                SubterraneanTrapLayout.Cell projection = new SubterraneanTrapLayout.Cell(floor.x(), floor.z());
                if (powder.contains(projection)
                        && floor.y() + ROUTE_CLEAR_HEIGHT >= landingY - 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static AlternativesResult planAlternativesResult(
            SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir, int[][] surfaceKind,
            int depth, List<NaturalCaveDestination> destinations) {
        if (destinations == null || destinations.stream().anyMatch(Objects::isNull)) {
            return rejected(Rejection.INVALID_INPUT, SurfaceRejectionReason.INVALID_INPUT);
        }
        PlanningContextResult checked = planningContext(placement, surfaceFirstAir, surfaceKind, depth);
        if (checked.context() == null) {
            return rejected(checked.rejection(), checked.surfaceRejectionReason());
        }
        PlanningContext context = checked.context();
        if (destinations.isEmpty()) {
            return rejected(Rejection.LOWER_CAVE_REQUIRED);
        }
        Set<DestinationProbe> legalProbes = Set.copyOf(destinationProbes(placement, context.landingY()));
        List<NaturalCaveDestination> ordered = destinations.stream()
                .filter(destination -> legalProbes.contains(destination.probe()))
                .filter(destination -> destination.probe().floorY() < context.landingY())
                .sorted(Comparator.comparing(NaturalCaveDestination::probe, PROBE_ORDER))
                .toList();
        if (ordered.isEmpty()) {
            return rejected(Rejection.DESCENT_ROUTE_CAPACITY);
        }
        List<Plan> plans = new ArrayList<>();
        for (NaturalCaveDestination destination : ordered) {
            plans.add(buildPlan(context, surfaceFirstAir, surfaceKind, new NaturalEndpoint(destination),
                    DestinationKind.NATURAL_CAVE, routeSteps(destination.probe(), context.landingY()), 0,
                    destination.probe().forwardDx(), destination.probe().forwardDz(),
                    "F".repeat(Math.max(0, destination.probe().distance() - 1))));
            if (plans.size() == MAX_ALTERNATIVES_PER_DEPTH) {
                break;
            }
        }
        return new AlternativesResult(plans, null, null);
    }

    private static PlanningContextResult planningContext(
            SubterraneanTrapLayout.Placement placement, int[][] surfaceFirstAir, int[][] surfaceKind, int depth) {
        if (placement == null || !isLocalSurface(surfaceFirstAir) || !isLocalSurface(surfaceKind)) {
            return invalidContext(Rejection.INVALID_INPUT, SurfaceRejectionReason.INVALID_INPUT);
        }
        List<SubterraneanTrapLayout.Cell> powder = placement.powder().stream().sorted(CELL_ORDER).toList();
        if (powder.isEmpty()) {
            return invalidContext(Rejection.INVALID_INPUT, SurfaceRejectionReason.INVALID_INPUT);
        }
        Set<SubterraneanTrapLayout.Cell> powderSet = Set.copyOf(powder);
        for (SubterraneanTrapLayout.Cell cell : powder) {
            int kind = surfaceKind[cell.x()][cell.z()];
            if (!isSnowCovered(kind)) {
                return invalidContext(Rejection.UNSUPPORTED_SURFACE,
                        kind == FIRM_GLACIAL_ICE ? SurfaceRejectionReason.MOUTH_FIRM_GLACIAL
                                : SurfaceRejectionReason.MOUTH_OTHER);
            }
        }
        // A real globe snowfield can gain several blocks across this long ribbon while remaining locally
        // smooth. The old whole-footprint range cap rejected that ordinary gentle grade even though every
        // powder block still replaced the exact visible surface. Camouflage integrity is the local law:
        // connected powder neighbours may step by at most one block.
        if (!cardinalStepsStable(powderSet, surfaceFirstAir)) {
            return invalidContext(Rejection.POWDER_RELIEF_EXCEEDS_ONE, null);
        }
        int roofY = powder.stream().mapToInt(cell -> surfaceFirstAir[cell.x()][cell.z()] - 1).min().orElseThrow();
        int landingY = roofY - depth;
        for (SubterraneanTrapLayout.Cell cell : powder) {
            int cellDepth = surfaceFirstAir[cell.x()][cell.z()] - 1 - landingY;
            if (cellDepth < MIN_HARD_DEPTH || cellDepth > MAX_HARD_DEPTH) {
                return invalidContext(Rejection.DEPTH_OUTSIDE_HARD_LIMIT, null);
            }
            if (cellDepth < MIN_LEGAL_DEPTH || cellDepth > MAX_LEGAL_DEPTH) {
                return invalidContext(Rejection.DEPTH_OUTSIDE_LEGAL_RANGE, null);
            }
        }

        TerrainFitCells terrain = terrainFitCells(placement, powderSet);
        if (terrain == null) {
            return invalidContext(Rejection.INVALID_INPUT, SurfaceRejectionReason.INVALID_INPUT);
        }
        for (SubterraneanTrapLayout.Cell cell : terrain.collar().stream().sorted(CELL_ORDER).toList()) {
            if (!isSnowCoveredOrFirmGlacial(surfaceKind[cell.x()][cell.z()])) {
                return invalidContext(Rejection.UNSUPPORTED_SURFACE, SurfaceRejectionReason.COLLAR_OTHER);
            }
        }
        for (SubterraneanTrapLayout.Cell cell : terrain.firmApproach().stream().sorted(CELL_ORDER).toList()) {
            int kind = surfaceKind[cell.x()][cell.z()];
            if (!isFirmSnowOrGlacial(kind)) {
                return invalidContext(Rejection.UNSUPPORTED_SURFACE,
                        kind == POWDER ? SurfaceRejectionReason.APPROACH_POWDER
                                : SurfaceRejectionReason.APPROACH_OTHER);
            }
        }
        // Apply the same local-grade law to the collar and four-block approaches. Cumulative elevation gain
        // is not itself a cliff; the per-column legal-depth checks above still bound every fall independently.
        if (!cardinalStepsStable(terrain.reliefSample(), surfaceFirstAir)) {
            return invalidContext(Rejection.APPROACH_RING_UNSTABLE, null);
        }
        return new PlanningContextResult(new PlanningContext(powder, roofY, landingY), null, null);
    }

    private static PlanningContextResult invalidContext(
            Rejection rejection, SurfaceRejectionReason surfaceRejectionReason) {
        return new PlanningContextResult(null, rejection, surfaceRejectionReason);
    }

    private static Plan buildCavernPlan(PlanningContext context, int[][] surfaceFirstAir,
                                       int[][] surfaceKind, AuthoredCavernEndpoint endpoint,
                                       AuthoredRoute route) {
        return buildPlan(context, surfaceFirstAir, surfaceKind, endpoint,
                DestinationKind.AUTHORED_CAVERN, route.steps(), route.turns(),
                route.initialHeadingX(), route.initialHeadingZ(), route.actionWord());
    }

    private static Plan buildPlan(PlanningContext context, int[][] surfaceFirstAir,
                                  int[][] surfaceKind, Endpoint endpoint,
                                  DestinationKind destinationKind, List<RouteStep> routeSteps, int routeTurns,
                                  int initialHeadingX, int initialHeadingZ, String actionWord) {
        List<SubterraneanTrapLayout.Cell> powder = context.powder();
        int roofY = context.roofY();
        int landingY = context.landingY();
        List<Write> writes = new ArrayList<>();
        for (SubterraneanTrapLayout.Cell cell : powder) {
            writes.add(new Write(cell.x(), landingY - 1, cell.z(), Phase.CUSHION_BASE));
        }
        for (SubterraneanTrapLayout.Cell cell : powder) {
            writes.add(new Write(cell.x(), landingY, cell.z(), Phase.CUSHION));
        }
        for (RouteStep step : routeSteps) {
            for (RouteCell floor : step.floorCells()) {
                writes.add(new Write(floor.x(), floor.y(), floor.z(), Phase.DESCENT_FLOOR));
            }
        }
        if (endpoint instanceof AuthoredCavernEndpoint cavern) {
            for (RouteCell floor : cavern.floorCells()) {
                writes.add(new Write(floor.x(), floor.y(), floor.z(), Phase.AUTHORED_CAVERN_FLOOR));
            }
            for (RouteCell clear : cavern.clearCells()) {
                writes.add(new Write(clear.x(), clear.y(), clear.z(), Phase.AUTHORED_CAVERN_CLEAR));
            }
        }
        for (SubterraneanTrapLayout.Cell cell : powder) {
            int cellRoofY = surfaceFirstAir[cell.x()][cell.z()] - 1;
            for (int y = cellRoofY - 1; y >= landingY + 1; y--) {
                writes.add(new Write(cell.x(), y, cell.z(), Phase.CLEAR));
            }
        }
        for (RouteStep step : routeSteps) {
            for (RouteCell floor : step.floorCells()) {
                for (int dy = 1; dy <= ROUTE_CLEAR_HEIGHT; dy++) {
                    writes.add(new Write(floor.x(), floor.y() + dy, floor.z(), Phase.DESCENT_CLEAR));
                }
            }
        }

        Set<Coordinate> planned = new HashSet<>();
        for (Write write : writes) {
            planned.add(new Coordinate(write.x(), write.y(), write.z()));
        }
        List<RouteCell> shellProbes = routeShellProbes(writes, planned);
        DescentRoute route = new DescentRoute(routeSteps, shellProbes, endpoint, routeTurns,
                initialHeadingX, initialHeadingZ, actionWord);

        for (SubterraneanTrapLayout.Cell cell : powder) {
            writes.add(new Write(cell.x(), surfaceFirstAir[cell.x()][cell.z()] - 1,
                    cell.z(), Phase.SURFACE_POWDER));
        }
        for (SubterraneanTrapLayout.Cell cell : powder) {
            if (surfaceKind[cell.x()][cell.z()] == THIN_SNOW
                    || surfaceKind[cell.x()][cell.z()] == THIN_OVER_FULL_SNOW) {
                writes.add(new Write(cell.x(), surfaceFirstAir[cell.x()][cell.z()],
                        cell.z(), Phase.REMOVE_SURFACE_LAYER));
            }
        }
        return new Plan(roofY, landingY, route, destinationKind, writes);
    }

    private static AuthoredRoute authoredRoute(
            CataloguedRoute catalogued, AuthoredCavernEndpoint endpoint,
            int landingY, Set<SubterraneanTrapLayout.Cell> powder) {
        Set<SubterraneanTrapLayout.Cell> cavern = endpoint.floorCells().stream()
                .map(cell -> new SubterraneanTrapLayout.Cell(cell.x(), cell.z()))
                .collect(java.util.stream.Collectors.toSet());
        if (catalogued.path().stations().stream().flatMap(station -> station.cells().stream())
                .anyMatch(cavern::contains)) {
            return null;
        }
        int finalHeadingX = headingX(catalogued.initialHeadingX(), catalogued.initialHeadingZ(),
                catalogued.actionWord().isEmpty() ? 'F' : catalogued.actionWord().charAt(
                        catalogued.actionWord().length() - 1));
        int finalHeadingZ = headingZ(catalogued.initialHeadingX(), catalogued.initialHeadingZ(),
                catalogued.actionWord().isEmpty() ? 'F' : catalogued.actionWord().charAt(
                        catalogued.actionWord().length() - 1));
        Footprint portal = footprint(endpoint.portalFloors());
        if (!translate(catalogued.path().stations().getLast(), finalHeadingX, finalHeadingZ).equals(portal)) {
            return null;
        }
        List<RouteStep> steps = new ArrayList<>(catalogued.path().stations().size());
        for (int index = 0; index < catalogued.path().stations().size(); index++) {
            int y = landingY - Math.min(index + 1, 6);
            Footprint station = catalogued.path().stations().get(index);
            steps.add(new RouteStep(y, station.cells().stream()
                    .map(cell -> new RouteCell(cell.x(), y, cell.z())).toList()));
        }
        if (!routeVolumeClearsLanding(steps, landingY, powder)) {
            return null;
        }
        return new AuthoredRoute(steps, catalogued.path().turns(), catalogued.initialHeadingX(),
                catalogued.initialHeadingZ(), catalogued.actionWord());
    }

    private static int headingX(int initialX, int initialZ, char action) {
        return switch (action) {
            case 'F' -> initialX;
            case 'L' -> -initialZ;
            case 'R' -> initialZ;
            default -> throw new IllegalArgumentException("unknown route action");
        };
    }

    private static int headingZ(int initialX, int initialZ, char action) {
        return switch (action) {
            case 'F' -> initialZ;
            case 'L' -> initialX;
            case 'R' -> -initialX;
            default -> throw new IllegalArgumentException("unknown route action");
        };
    }

    private static AuthoredRouteIndexBuild authoredRouteIndex(List<EntryGeometry> entries) {
        Map<Footprint, List<CataloguedRoute>> index = new LinkedHashMap<>();
        int expansionCalls = 0;
        int indexedPaths = 0;
        for (EntryGeometry entry : entries) {
            Footprint start = new Footprint(entry.firstDoorway(), entry.secondDoorway());
            if (!footprintInWriteBounds(start)) {
                continue;
            }
            for (String actionWord : AUTHORED_ROUTE_ACTION_WORDS) {
                expansionCalls++;
                for (RoutePath path : expandDogleg(
                        start, entry.forwardDx(), entry.forwardDz(), actionWord)) {
                    CataloguedRoute route = new CataloguedRoute(
                            path, entry.forwardDx(), entry.forwardDz(), actionWord);
                    index.computeIfAbsent(path.stations().getLast(), ignored -> new ArrayList<>()).add(route);
                    indexedPaths++;
                }
            }
        }
        Map<Footprint, List<CataloguedRoute>> frozen = new LinkedHashMap<>();
        index.forEach((terminal, routes) -> frozen.put(terminal, List.copyOf(routes)));
        return new AuthoredRouteIndexBuild(
                Map.copyOf(frozen), entries.size(), expansionCalls, indexedPaths);
    }

    private static List<RoutePath> expandDogleg(
            Footprint start, int initialHeadingX, int initialHeadingZ, String actionWord) {
        List<RoutePath> paths = List.of(new RoutePath(List.of(start), 0));
        char previousAction = 'F';
        for (int actionIndex = 0; actionIndex < actionWord.length(); actionIndex++) {
            char action = actionWord.charAt(actionIndex);
            int headingX = switch (action) {
                case 'F' -> initialHeadingX;
                case 'L' -> -initialHeadingZ;
                case 'R' -> initialHeadingZ;
                default -> throw new IllegalArgumentException("unknown route action");
            };
            int headingZ = switch (action) {
                case 'F' -> initialHeadingZ;
                case 'L' -> initialHeadingX;
                case 'R' -> -initialHeadingX;
                default -> throw new IllegalArgumentException("unknown route action");
            };
            if (!headingNeverReversesInitial(
                    initialHeadingX, initialHeadingZ, headingX, headingZ)) {
                return List.of();
            }
            boolean turn = action != previousAction;
            List<RoutePath> expanded = new ArrayList<>();
            for (RoutePath path : paths) {
                Footprint current = path.stations().getLast();
                List<Footprint> nextFootprints = turn
                        ? turnFootprints(current, headingX, headingZ)
                        : List.of(translate(current, headingX, headingZ));
                for (Footprint next : nextFootprints) {
                    Set<SubterraneanTrapLayout.Cell> occupied = path.stations().stream()
                            .flatMap(station -> station.cells().stream())
                            .collect(java.util.stream.Collectors.toSet());
                    if (!footprintInWriteBounds(next)
                            || next.cells().stream().anyMatch(occupied::contains)) {
                        continue;
                    }
                    int turns = path.turns() + (turn ? 1 : 0);
                    if (turns > 2) {
                        continue;
                    }
                    List<Footprint> stations = new ArrayList<>(path.stations());
                    stations.add(next);
                    expanded.add(new RoutePath(stations, turns));
                }
            }
            paths = List.copyOf(expanded);
            if (paths.isEmpty()) {
                break;
            }
            previousAction = action;
        }
        return paths;
    }

    private static List<String> authoredRouteActionWords() {
        List<String> words = new ArrayList<>();
        for (int moves = 5; moves <= 11; moves++) {
            List<String> sameLength = new ArrayList<>();
            sameLength.add("F".repeat(moves));
            for (char side : new char[]{'L', 'R'}) {
                for (int a = 0; a < moves; a++) {
                    int b = moves - a;
                    sameLength.add("F".repeat(a) + String.valueOf(side).repeat(b));
                }
                for (int a = 0; a <= moves - 2; a++) {
                    for (int b = 1; b <= moves - a - 1; b++) {
                        int c = moves - a - b;
                        sameLength.add("F".repeat(a) + String.valueOf(side).repeat(b) + "F".repeat(c));
                    }
                }
            }
            sameLength.sort(String::compareTo);
            words.addAll(sameLength);
        }
        return List.copyOf(words);
    }

    static boolean headingNeverReversesInitial(
            int initialX, int initialZ, int candidateX, int candidateZ) {
        return isCardinalUnit(initialX, initialZ)
                && isCardinalUnit(candidateX, candidateZ)
                && (candidateX != -initialX || candidateZ != -initialZ);
    }

    private static boolean actionWordHeadingsNeverReverseInitial(
            int initialX, int initialZ, String actionWord) {
        for (int index = 0; index < actionWord.length(); index++) {
            char action = actionWord.charAt(index);
            int candidateX = switch (action) {
                case 'F' -> initialX;
                case 'L' -> -initialZ;
                case 'R' -> initialZ;
                default -> Integer.MIN_VALUE;
            };
            int candidateZ = switch (action) {
                case 'F' -> initialZ;
                case 'L' -> initialX;
                case 'R' -> -initialX;
                default -> Integer.MIN_VALUE;
            };
            if (!headingNeverReversesInitial(initialX, initialZ, candidateX, candidateZ)) {
                return false;
            }
        }
        return true;
    }

    private static int actionWordTurnCount(String actionWord) {
        int turns = 0;
        char previous = 'F';
        for (int index = 0; index < actionWord.length(); index++) {
            char action = actionWord.charAt(index);
            if (action != previous) {
                turns++;
            }
            previous = action;
        }
        return turns;
    }

    private static Footprint translate(Footprint footprint, int dx, int dz) {
        return new Footprint(new SubterraneanTrapLayout.Cell(
                footprint.first().x() + dx, footprint.first().z() + dz),
                new SubterraneanTrapLayout.Cell(
                        footprint.second().x() + dx, footprint.second().z() + dz));
    }

    private static List<Footprint> turnFootprints(Footprint current, int headingX, int headingZ) {
        Set<Footprint> candidates = new HashSet<>();
        for (SubterraneanTrapLayout.Cell anchor : current.cells()) {
            SubterraneanTrapLayout.Cell first = new SubterraneanTrapLayout.Cell(
                    anchor.x() + headingX, anchor.z() + headingZ);
            for (int sign : new int[]{-1, 1}) {
                SubterraneanTrapLayout.Cell second = new SubterraneanTrapLayout.Cell(
                        first.x() - headingZ * sign, first.z() + headingX * sign);
                candidates.add(new Footprint(first, second));
            }
        }
        return candidates.stream().sorted(Comparator
                .comparingInt((Footprint footprint) -> footprint.first().x())
                .thenComparingInt(footprint -> footprint.first().z())
                .thenComparingInt(footprint -> footprint.second().x())
                .thenComparingInt(footprint -> footprint.second().z())).toList();
    }

    private static Footprint footprint(List<RouteCell> cells) {
        return new Footprint(new SubterraneanTrapLayout.Cell(cells.get(0).x(), cells.get(0).z()),
                new SubterraneanTrapLayout.Cell(cells.get(1).x(), cells.get(1).z()));
    }

    private static boolean footprintInWriteBounds(Footprint footprint) {
        return footprint.cells().stream().allMatch(cell -> writeCellInOwner(cell.x(), cell.z()));
    }

    private static List<RouteStep> routeSteps(DestinationProbe probe, int landingY) {
        List<RouteStep> steps = new ArrayList<>(probe.distance());
        for (int index = 0; index < probe.distance(); index++) {
            int floorY = landingY - index - 1;
            int x = probe.entryX() + probe.forwardDx() * index;
            int z = probe.entryZ() + probe.forwardDz() * index;
            steps.add(new RouteStep(floorY, List.of(
                    new RouteCell(x, floorY, z),
                    new RouteCell(x + probe.widthDx(), floorY, z + probe.widthDz()))));
        }
        return List.copyOf(steps);
    }

    private static List<RouteCell> routeShellProbes(List<Write> writes, Set<Coordinate> planned) {
        Set<Coordinate> probes = new HashSet<>();
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
        for (Write write : writes) {
            if (write.phase() != Phase.DESCENT_FLOOR && write.phase() != Phase.DESCENT_CLEAR) {
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
                .map(probe -> new RouteCell(probe.x(), probe.y(), probe.z()))
                .toList();
    }

    private static List<EntryGeometry> entryGeometries(Set<SubterraneanTrapLayout.Cell> powder) {
        List<EntryGeometry> entries = new ArrayList<>();
        Set<EntryGeometry> unique = new HashSet<>();
        int[][] forwards = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (SubterraneanTrapLayout.Cell first : powder.stream().sorted(CELL_ORDER).toList()) {
            for (int[] forward : forwards) {
                SubterraneanTrapLayout.Cell firstDoorway = new SubterraneanTrapLayout.Cell(
                        first.x() + forward[0], first.z() + forward[1]);
                if (powder.contains(firstDoorway) || !writeCellInOwner(firstDoorway.x(), firstDoorway.z())) {
                    continue;
                }
                for (int widthSign : List.of(-1, 1)) {
                    int widthDx = -forward[1] * widthSign;
                    int widthDz = forward[0] * widthSign;
                    SubterraneanTrapLayout.Cell secondPowder = new SubterraneanTrapLayout.Cell(
                            first.x() + widthDx, first.z() + widthDz);
                    SubterraneanTrapLayout.Cell secondDoorway = new SubterraneanTrapLayout.Cell(
                            firstDoorway.x() + widthDx, firstDoorway.z() + widthDz);
                    if (!powder.contains(secondPowder) || powder.contains(secondDoorway)
                            || !writeCellInOwner(secondDoorway.x(), secondDoorway.z())) {
                        continue;
                    }
                    EntryGeometry geometry = new EntryGeometry(
                            firstDoorway, secondDoorway, widthDx, widthDz, forward[0], forward[1]);
                    if (unique.add(geometry)) {
                        entries.add(geometry);
                    }
                }
            }
        }
        entries.sort(Comparator.comparingInt((EntryGeometry entry) -> entry.firstDoorway().x())
                .thenComparingInt(entry -> entry.firstDoorway().z())
                .thenComparingInt(EntryGeometry::forwardDx)
                .thenComparingInt(EntryGeometry::forwardDz)
                .thenComparingInt(EntryGeometry::widthDx)
                .thenComparingInt(EntryGeometry::widthDz));
        return List.copyOf(entries);
    }

    private static boolean connectedNaturalFloors(Set<RouteCell> floors) {
        Set<RouteCell> unseen = new HashSet<>(floors);
        ArrayDeque<RouteCell> queue = new ArrayDeque<>();
        RouteCell first = unseen.iterator().next();
        unseen.remove(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            RouteCell current = queue.removeFirst();
            List<RouteCell> neighbours = unseen.stream()
                    .filter(candidate -> Math.abs(candidate.x() - current.x())
                            + Math.abs(candidate.z() - current.z()) == 1
                            && Math.abs(candidate.y() - current.y()) <= 1)
                    .toList();
            for (RouteCell neighbour : neighbours) {
                unseen.remove(neighbour);
                queue.addLast(neighbour);
            }
        }
        return unseen.isEmpty();
    }

    private static boolean continuationAvoidsAuthoredRoute(DestinationProbe probe, Set<RouteCell> floors) {
        return floors.stream().allMatch(floor -> naturalContinuationColumnAvoidsAuthoredRoute(probe, floor));
    }

    /**
     * A lower-cave witness is a whole usable column, not merely its floor coordinate. This keeps a BFS from
     * certifying the headroom that the plan will later excavate as "natural cave" after it loops alongside an
     * authored stair. The target opening remains legal because authored route steps stop one station before it.
     */
    public static boolean naturalContinuationColumnAvoidsAuthoredRoute(
            DestinationProbe probe, RouteCell naturalFloor) {
        if (probe == null || naturalFloor == null) {
            return false;
        }
        int landingY = probe.floorY() + probe.distance();
        Set<Coordinate> authoredVolume = new HashSet<>();
        for (RouteStep step : routeSteps(probe, landingY)) {
            for (RouteCell floor : step.floorCells()) {
                authoredVolume.add(new Coordinate(floor.x(), floor.y(), floor.z()));
                for (int dy = 1; dy <= ROUTE_CLEAR_HEIGHT; dy++) {
                    authoredVolume.add(new Coordinate(floor.x(), floor.y() + dy, floor.z()));
                }
            }
        }
        for (int dy = 0; dy <= ROUTE_CLEAR_HEIGHT; dy++) {
            if (authoredVolume.contains(new Coordinate(
                    naturalFloor.x(), naturalFloor.y() + dy, naturalFloor.z()))) {
                return false;
            }
        }
        return true;
    }

    private static AlternativesResult rejected(Rejection rejection) {
        return rejected(rejection, null);
    }

    private static AlternativesResult rejected(Rejection rejection, SurfaceRejectionReason surfaceRejectionReason) {
        return new AlternativesResult(List.of(), rejection, surfaceRejectionReason);
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

    /** The rim may expose glacier-body ice, but the collapsed powder cells themselves remain snow-covered. */
    private static boolean isSnowCoveredOrFirmGlacial(int kind) {
        return isSnowCovered(kind) || kind == FIRM_GLACIAL_ICE;
    }

    private static boolean isFirmSnowOrGlacial(int kind) {
        return kind == FULL_SNOW || kind == THIN_SNOW || kind == THIN_OVER_FULL_SNOW
                || kind == FIRM_GLACIAL_ICE;
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
                        && Math.abs((long) firstAir[cell.x()][cell.z()]
                        - firstAir[neighbour.x()][neighbour.z()]) > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isCardinalUnit(int dx, int dz) {
        return Math.abs(dx) + Math.abs(dz) == 1;
    }

    private static List<SubterraneanTrapLayout.Cell> cavernCells() {
        Set<SubterraneanTrapLayout.Cell> cells = new LinkedHashSet<>();
        for (int x = 10; x <= 17; x++) {
            for (int z = 2; z <= 8; z++) {
                cells.add(new SubterraneanTrapLayout.Cell(x, z));
            }
        }
        // The three-wide connector turns south before opening into the dominant neighbour lobe.
        for (int x = 16; x <= 21; x++) {
            for (int z = 5; z <= 7; z++) {
                cells.add(new SubterraneanTrapLayout.Cell(x, z));
            }
        }
        for (int x = 19; x <= 21; x++) {
            for (int z = 5; z <= 9; z++) {
                cells.add(new SubterraneanTrapLayout.Cell(x, z));
            }
        }
        for (int x = 20; x <= 29; x++) {
            for (int z = 7; z <= 13; z++) {
                cells.add(new SubterraneanTrapLayout.Cell(x, z));
            }
        }
        // Corner bites and permanent pillars keep both lobes irregular and rule out every filled 5x5 room.
        cells.removeAll(Set.of(
                new SubterraneanTrapLayout.Cell(10, 2), new SubterraneanTrapLayout.Cell(17, 2),
                new SubterraneanTrapLayout.Cell(10, 8), new SubterraneanTrapLayout.Cell(17, 8),
                new SubterraneanTrapLayout.Cell(13, 5), new SubterraneanTrapLayout.Cell(16, 5),
                new SubterraneanTrapLayout.Cell(20, 7), new SubterraneanTrapLayout.Cell(29, 7),
                new SubterraneanTrapLayout.Cell(20, 13), new SubterraneanTrapLayout.Cell(29, 13),
                new SubterraneanTrapLayout.Cell(23, 10), new SubterraneanTrapLayout.Cell(27, 10)));
        return cells.stream().sorted(Comparator.comparingInt(SubterraneanTrapLayout.Cell::x)
                .thenComparingInt(SubterraneanTrapLayout.Cell::z)).toList();
    }

    private static boolean inRegion(int x, int z, CavernRegion region) {
        return switch (region) {
            case PRIMARY -> x >= 20 && x <= 29 && z >= 7 && z <= 13;
            case SECONDARY -> x >= 10 && x <= 17 && z >= 2 && z <= 8;
            case THROAT -> (x >= 16 && x <= 21 && z >= 5 && z <= 7)
                    || (x >= 19 && x <= 21 && z >= 5 && z <= 9);
        };
    }

    private static int floorOffset(int x, int z) {
        if (inRegion(x, z, CavernRegion.PRIMARY)) {
            return 0;
        }
        if (inRegion(x, z, CavernRegion.THROAT)) {
            return 1;
        }
        return 2;
    }

    private static int clearHeight(SubterraneanTrapLayout.Cell cell) {
        return 4 + Math.floorMod((cell.x() + 1) + cell.z() * 2, 4);
    }

    private static boolean writeCellInOwner(int x, int z) {
        return x >= 1 && x <= SubterraneanTrapLayout.LOCAL_SIZE - 2
                && z >= 1 && z <= SubterraneanTrapLayout.LOCAL_SIZE - 2;
    }

    private static boolean shellCellInOwner(int x, int z) {
        return x >= 0 && x < SubterraneanTrapLayout.LOCAL_SIZE
                && z >= 0 && z < SubterraneanTrapLayout.LOCAL_SIZE;
    }

    private static int cardinalDistance(RouteCell first, RouteCell second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z());
    }
}
