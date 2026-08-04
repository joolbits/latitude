package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-JVM laws for a surface trap that continues downward into a real lower cave. */
class SubterraneanTrapPlanTest {

    private static final SubterraneanTrapLayout.Placement PLACEMENT = placementWithProbe();

    @Test
    void missingLowerNaturalCaveFailsClosed() {
        SubterraneanTrapPlan.Result result = SubterraneanTrapPlan.plan(
                PLACEMENT, firstAir(101), fullSnow());
        assertFalse(result.isAccepted());
        assertEquals(SubterraneanTrapPlan.Rejection.LOWER_CAVE_REQUIRED, result.rejection());
        assertNull(result.accepted());
        assertTrue(SubterraneanTrapPlan.planAlternatives(
                PLACEMENT, firstAir(101), fullSnow(), 32).isEmpty());
    }

    @Test
    void connectedLowerNaturalCaveProducesThePreferredLanding() {
        SubterraneanTrapPlan.Result result = validPlan();
        assertTrue(result.isAccepted());
        assertEquals(100, result.accepted().roofY());
        assertEquals(68, result.accepted().landingY());
        assertNull(result.rejection());
    }

    @Test
    void everyCataloguePlacementAndRotationHasTheIrregularCrossChunkCavernContract() {
        assertTimeoutPreemptively(Duration.ofSeconds(60),
                SubterraneanTrapPlanTest::assertFullAuthoredCatalogueContract);
    }

    private static void assertFullAuthoredCatalogueContract() {
        List<SubterraneanTrapLayout.Placement> placements = SubterraneanTrapLayout.placements(73L, 4, -9);
        assertEquals(64, placements.size());
        int catalogueBuilds = 0;
        int entryGeometries = 0;
        int expansionCalls = 0;
        int indexedPaths = 0;
        List<String> routeFingerprints = new ArrayList<>();
        java.util.Map<Integer, Integer> directionCountMatrix = new java.util.TreeMap<>();
        for (int directions = 0; directions <= 4; directions++) directionCountMatrix.put(directions, 0);
        List<Integer> zeroDirectionOrdinals = new ArrayList<>();
        int weightedDirectionTotal = 0;
        for (int ordinal = 0; ordinal < placements.size(); ordinal++) {
            SubterraneanTrapLayout.Placement placement = placements.get(ordinal);
            SubterraneanTrapPlan.AuthoredCavernCatalogue catalogue =
                    SubterraneanTrapPlan.authoredCavernCatalogue(placement);
            SubterraneanTrapPlan.AuthoredRouteBuildMeter meter = catalogue.buildMeter();
            catalogueBuilds++;
            entryGeometries += meter.entryGeometries();
            expansionCalls += meter.expansionCalls();
            indexedPaths += meter.indexedPaths();
            assertEquals(539, meter.actionWords(), "the finite action-word grammar must not expand");
            int viableDirections = 0;
            for (SubterraneanTrapPlan.CavernDirection direction : SubterraneanTrapPlan.CavernDirection.values()) {
                List<SubterraneanTrapPlan.Plan> alternatives = SubterraneanTrapPlan.authoredCavernAlternatives(
                        catalogue, firstAir(101), fullSnow(), 32, direction);
                routeFingerprints.add((ordinal + 1) + "|" + direction + "|count=" + alternatives.size());
                for (int routeIndex = 0; routeIndex < alternatives.size(); routeIndex++) {
                    routeFingerprints.add(authoredRouteFingerprint(
                            ordinal + 1, direction, routeIndex + 1, alternatives.get(routeIndex)));
                }
                if (alternatives.isEmpty()) {
                    continue;
                }
                viableDirections++;
                assertEquals(alternatives, SubterraneanTrapPlan.authoredCavernAlternatives(
                        catalogue, firstAir(101), fullSnow(), 32, direction),
                        "candidate order must be deterministic");
                SubterraneanTrapPlan.Plan cavern = alternatives.getFirst();
                assertEquals(SubterraneanTrapPlan.DestinationKind.AUTHORED_CAVERN, cavern.destinationKind());
                SubterraneanTrapPlan.AuthoredCavernEndpoint endpoint =
                        (SubterraneanTrapPlan.AuthoredCavernEndpoint) cavern.descentRoute().endpoint();
                assertEquals(direction, endpoint.direction());
                assertEquals(cavern.landingY() - 8, endpoint.floorY());

                List<SubterraneanTrapPlan.RouteCell> floors = endpoint.floorCells();
                Set<PlanarCell> floorPlan = planarCells(floors);
                assertEquals(floors.size(), floorPlan.size());
                assertTrue(floors.size() >= 96);
                int minX = floors.stream().mapToInt(cell -> direction.canonicalX(cell.x(), cell.z())).min().orElseThrow();
                int maxX = floors.stream().mapToInt(cell -> direction.canonicalX(cell.x(), cell.z())).max().orElseThrow();
                int minZ = floors.stream().mapToInt(cell -> direction.canonicalZ(cell.x(), cell.z())).min().orElseThrow();
                int maxZ = floors.stream().mapToInt(cell -> direction.canonicalZ(cell.x(), cell.z())).max().orElseThrow();
                assertEquals(20, maxX - minX + 1);
                assertEquals(12, maxZ - minZ + 1);
                double fill = floors.size() / (double) ((maxX - minX + 1) * (maxZ - minZ + 1));
                assertTrue(fill >= 0.45 && fill <= 0.75, "cavity fill stays irregular rather than box-like");
                assertTrue(canonicalSpan(endpoint.primaryLobeFloors(), direction, true) >= 8);
                assertTrue(canonicalSpan(endpoint.primaryLobeFloors(), direction, false) >= 7);
                assertTrue(canonicalSpan(endpoint.secondaryLobeFloors(), direction, true) >= 6);
                assertTrue(canonicalSpan(endpoint.secondaryLobeFloors(), direction, false) >= 5);
                assertTrue(canonicalSpan(endpoint.throatFloors(), direction, false) >= 3);
                assertTrue(canonicalSpan(endpoint.throatFloors(), direction, false) <= 5);
                assertTrue(connectedWalkable(floors));
                assertTrue(floors.stream().map(SubterraneanTrapPlan.RouteCell::y).distinct().count() >= 3);
                assertFalse(hasFilledCanonicalFiveByFive(floors, direction));

                java.util.Map<PlanarCell, Long> clearByColumn = endpoint.clearCells().stream().collect(
                        java.util.stream.Collectors.groupingBy(cell -> new PlanarCell(cell.x(), cell.z()),
                                java.util.stream.Collectors.counting()));
                assertEquals(floorPlan, clearByColumn.keySet());
                assertTrue(clearByColumn.values().stream().allMatch(height -> height >= 4 && height <= 7));
                assertTrue(clearByColumn.values().stream().distinct().count() >= 3);
                assertFalse(endpoint.substrateProbes().isEmpty());
                assertFalse(endpoint.ceilingProbes().isEmpty());
                assertFalse(endpoint.perimeterProbes().isEmpty());
                assertTrue(endpoint.shellProbes().stream().allMatch(cell ->
                        direction.containsReadShellCell(cell.x(), cell.z())));
                Set<String> chunks = floors.stream().map(cell ->
                                Math.floorDiv(cell.x(), 16) + ":" + Math.floorDiv(cell.z(), 16))
                        .collect(java.util.stream.Collectors.toSet());
                assertEquals(Set.of("0:0", direction.chunkDx() + ":" + direction.chunkDz()), chunks,
                        "the cavern occupies the owner and exactly one cardinal neighbour");

                assertTrue(cavern.descentRoute().steps().size() >= 6
                        && cavern.descentRoute().steps().size() <= 12);
                IndependentRouteGeometry geometry = independentlyVerifyRouteGeometry(
                        placement, cavern.descentRoute().steps());
                Heading storedInitial = new Heading(cavern.descentRoute().initialHeadingX(),
                        cavern.descentRoute().initialHeadingZ());
                assertTrue(geometry.viableInitialHeadings().contains(storedInitial));
                assertEquals(independentlyCountTurns(storedInitial, geometry.headings()),
                        cavern.descentRoute().turns());
                assertTrue(geometry.maximumTurns() <= 2);
                assertTrue(cavern.descentRoute().headingsNeverReverseInitial());
                Set<String> routeFootprint = new HashSet<>();
                int previousY = cavern.landingY();
                for (int index = 0; index < cavern.descentRoute().steps().size(); index++) {
                    SubterraneanTrapPlan.RouteStep step = cavern.descentRoute().steps().get(index);
                    assertEquals(index < 6 ? previousY - 1 : endpoint.floorY() + 2, step.y());
                    assertTrue(step.y() <= previousY);
                    previousY = step.y();
                    for (SubterraneanTrapPlan.RouteCell floor : step.floorCells()) {
                        assertTrue(routeFootprint.add(floor.x() + ":" + floor.z()));
                        assertEquals(1, writesAt(cavern, floor, SubterraneanTrapPlan.Phase.DESCENT_FLOOR));
                        for (int dy = 1; dy <= 3; dy++) {
                            assertEquals(1, writesAt(cavern, new SubterraneanTrapPlan.RouteCell(
                                            floor.x(), floor.y() + dy, floor.z()),
                                    SubterraneanTrapPlan.Phase.DESCENT_CLEAR));
                        }
                    }
                }
                assertEquals(endpoint.floorY() + 2, previousY);
                Heading portalHeading = geometry.headings().isEmpty() ? storedInitial : geometry.headings().getLast();
                assertEquals(planarCells(endpoint.portalFloors()),
                        translate(planarCells(endpoint.approachFloors()), portalHeading));
                assertTrue(cavern.writes().stream()
                        .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_FLOOR
                                || write.phase() == SubterraneanTrapPlan.Phase.AUTHORED_CAVERN_CLEAR)
                        .allMatch(write -> direction.containsWriteCell(write.x(), write.z())));
                assertEquals(cavern.writes().size(), cavern.writes().stream()
                        .map(write -> write.x() + ":" + write.y() + ":" + write.z()).distinct().count());
            }
            directionCountMatrix.merge(viableDirections, 1, Integer::sum);
            weightedDirectionTotal += viableDirections;
            if (viableDirections == 0) zeroDirectionOrdinals.add(ordinal + 1);
            SubterraneanTrapPlan.authoredCavernAlternatives(
                    catalogue, firstAir(101), fullSnow(), 33,
                    SubterraneanTrapPlan.CavernDirection.EAST);
            assertEquals(meter, catalogue.buildMeter(),
                    "directions and per-depth planning must not rebuild planar routes");
        }
        assertEquals(java.util.Map.of(0, 14, 1, 36, 2, 14, 3, 0, 4, 0), directionCountMatrix,
                "the exact sparse direction matrix is the catalogue law, not artificial 256/256 availability");
        assertEquals(64, weightedDirectionTotal);
        assertEquals(64, catalogueBuilds, "one planar catalogue build per placement");
        assertEquals(1_600, entryGeometries, "the fixed 64-mouth catalogue entry census");
        assertEquals(862_400, expansionCalls,
                "directions and depths must not multiply the 1,600 entries by the 539-word grammar");
        assertEquals(655_188, indexedPaths, "the cold catalogue keeps its exact accepted-path census");
        assertTrue(indexedPaths <= 3_057_600,
                "the full cold sweep must remain under the finite three-way/two-turn route bound");
        assertEquals("e12a6c11ae7de2d5fb9bae7ae127c3684fcc2b96b7b281280136b7fec5a5be82",
                sha256(routeFingerprints),
                "ordered alternatives and every route coordinate remain fingerprint-pinned");
        assertEquals(List.of(6, 17, 25, 27, 28, 30, 32, 37, 39, 40, 42, 44, 46, 54),
                zeroDirectionOrdinals);
        assertZeroDirectionPlacementsAreRealCushionCollisions(placements);
        assertCatalogueSelectionGate(placements);
        assertEquals(SubterraneanTrapPlan.DestinationKind.NATURAL_CAVE, validPlan().accepted().destinationKind(),
                "the pre-existing natural cave remains the ordinary path");
    }

    private static String authoredRouteFingerprint(
            int placementOrdinal, SubterraneanTrapPlan.CavernDirection direction,
            int routeOrdinal, SubterraneanTrapPlan.Plan plan) {
        SubterraneanTrapPlan.DescentRoute route = plan.descentRoute();
        SubterraneanTrapPlan.AuthoredCavernEndpoint endpoint =
                (SubterraneanTrapPlan.AuthoredCavernEndpoint) route.endpoint();
        String stations = route.steps().stream().map(step -> step.y() + ":"
                        + step.floorCells().stream().map(cell -> cell.x() + "," + cell.z())
                        .collect(java.util.stream.Collectors.joining("/")))
                .collect(java.util.stream.Collectors.joining(";"));
        return placementOrdinal + "|" + direction + "|" + routeOrdinal + "|" + endpoint.portal()
                + "|" + route.initialHeadingX() + "," + route.initialHeadingZ()
                + "|" + route.turns() + "|" + route.actionWord() + "|" + stations;
    }

    private static String sha256(List<String> lines) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    String.join("\n", lines).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void assertZeroDirectionPlacementsAreRealCushionCollisions(
            List<SubterraneanTrapLayout.Placement> placements) {
        assertEquals(List.of(
                        "6:Z:12:7:4:2", "17:Z:12:7:5:2", "25:X:14:7:1:4",
                        "27:X:14:7:1:5", "28:X:14:7:1:7", "30:X:14:7:1:3",
                        "32:X:14:7:1:6", "37:Z:14:7:6:1", "39:Z:14:7:7:1",
                        "40:Z:14:7:5:1", "42:X:12:7:1:5", "44:X:12:7:2:5",
                        "46:X:12:7:1:6", "54:X:12:7:3:5"),
                List.of(6, 17, 25, 27, 28, 30, 32, 37, 39, 40, 42, 44, 46, 54)
                        .stream().map(ordinal -> {
                    SubterraneanTrapLayout.Placement placement = placements.get(ordinal - 1);
                    return ordinal + ":" + placement.template().axis() + ":"
                            + placement.template().stations() + ":" + placement.template().width() + ":"
                            + placement.offsetX() + ":" + placement.offsetZ();
                }).toList(), "zero-direction catalogue rows keep their exact template and offset identity");
        for (int ordinal : List.of(6, 17, 25, 27, 28, 30, 32, 37, 39, 40, 42, 44, 46, 54)) {
            SubterraneanTrapLayout.Placement placement = placements.get(ordinal - 1);
            SubterraneanTrapPlan.AuthoredCavernCatalogue catalogue =
                    SubterraneanTrapPlan.authoredCavernCatalogue(placement);
            int eligible = 0;
            int collisions = 0;
            int cushionClear = 0;
            for (SubterraneanTrapPlan.CavernDirection direction
                    : SubterraneanTrapPlan.CavernDirection.values()) {
                assertTrue(SubterraneanTrapPlan.authoredCavernAlternatives(
                        catalogue, firstAir(101), fullSnow(), 32, direction).isEmpty());
                SubterraneanTrapPlan.AuthoredCavernRouteCensus census =
                        SubterraneanTrapPlan.authoredCavernRouteCensus(catalogue, 68, direction);
                eligible += census.geometricallyEligible();
                collisions += census.cushionCollisions();
                cushionClear += census.cushionClear();
            }
            assertTrue(eligible > 0, "ordinal " + ordinal + " has real indexed portal candidates");
            assertEquals(eligible, collisions,
                    "ordinal " + ordinal + " rejects every geometrically eligible route for cushion collision");
            assertEquals(0, cushionClear,
                    "ordinal " + ordinal + " has no collision-free candidate hidden by another gate");
        }
    }

    private static void assertCatalogueSelectionGate(List<SubterraneanTrapLayout.Placement> placements) {
        List<SubterraneanTrapPlan.CavernDirection> order = rotatedDirections(2);
        assertEquals(List.of(SubterraneanTrapPlan.CavernDirection.WEST,
                SubterraneanTrapPlan.CavernDirection.NORTH,
                SubterraneanTrapPlan.CavernDirection.EAST,
                SubterraneanTrapPlan.CavernDirection.SOUTH), order);
        assertEquals(order, rotatedDirections(2), "direction order is deterministic");

        java.util.Map<Integer, SubterraneanTrapPlan.AuthoredCavernCatalogue> catalogues =
                java.util.stream.Stream.of(27, 1, 3).collect(java.util.stream.Collectors.toMap(
                        ordinal -> ordinal,
                        ordinal -> SubterraneanTrapPlan.authoredCavernCatalogue(
                                placements.get(ordinal - 1))));
        List<String> events = new ArrayList<>();
        CatalogueCandidate selected = firstSafeCatalogueCandidate(
                List.of(27, 1, 3), placements, order,
                (ordinal, direction) -> {
                    events.add("generate:" + ordinal + ":" + direction);
                    return SubterraneanTrapPlan.authoredCavernAlternatives(
                                    catalogues.get(ordinal), firstAir(101), fullSnow(), 32, direction)
                            .stream().map(plan -> new CatalogueCandidate(ordinal, direction, plan)).toList();
                },
                candidate -> {
                    events.add("safe:" + candidate.ordinal() + ":" + candidate.direction());
                    return candidate.ordinal() == 3;
                });
        assertTrue(selected != null);
        assertEquals(3, selected.ordinal(), "zero and fully unsafe placements yield to the later safe placement");
        assertEquals(java.util.stream.Stream.of(27, 1, 3).flatMap(ordinal -> order.stream()
                        .map(direction -> "generate:" + ordinal + ":" + direction)).toList(),
                events.stream().filter(event -> event.startsWith("generate:")).toList(),
                "all four directions are generated in deterministic order for each visited placement");
        assertTrue(events.stream().noneMatch(event -> event.startsWith("safe:27:")),
                "zero-direction placement never reaches world safety");
        assertTrue(events.indexOf("safe:1:EAST") > events.indexOf("generate:1:SOUTH"),
                "all four direction plans exist before the first world-safety callback");
        assertTrue(events.indexOf("safe:3:EAST") > events.indexOf("generate:3:SOUTH"),
                "later placement also completes generation before safety selection");
        java.util.concurrent.atomic.AtomicInteger mutationBoundary = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<CatalogueCandidate> mutate = ignored -> mutationBoundary.incrementAndGet();
        mutate.accept(selected);
        assertEquals(1, mutationBoundary.get(), "exactly the selected safe candidate crosses mutation boundary");
    }

    private static List<SubterraneanTrapPlan.CavernDirection> rotatedDirections(int first) {
        SubterraneanTrapPlan.CavernDirection[] directions = SubterraneanTrapPlan.CavernDirection.values();
        return java.util.stream.IntStream.range(0, directions.length)
                .mapToObj(index -> directions[Math.floorMod(first + index, directions.length)]).toList();
    }

    private static <T> T firstSafeCatalogueCandidate(
            List<Integer> ordinals, List<SubterraneanTrapLayout.Placement> placements,
            List<SubterraneanTrapPlan.CavernDirection> directions,
            java.util.function.BiFunction<Integer, SubterraneanTrapPlan.CavernDirection, List<T>> planFactory,
            java.util.function.Predicate<T> worldSafe) {
        for (int ordinal : ordinals) {
            List<T> generated = new ArrayList<>();
            for (SubterraneanTrapPlan.CavernDirection direction : directions) {
                generated.addAll(planFactory.apply(ordinal, direction));
            }
            for (T candidate : generated) {
                if (worldSafe.test(candidate)) return candidate;
            }
        }
        return null;
    }

    private record CatalogueCandidate(int ordinal, SubterraneanTrapPlan.CavernDirection direction,
                                      SubterraneanTrapPlan.Plan plan) {
    }

    @Test
    void canonicalEastPortalCatalogueIsExactInsideTheCavernAndOutsideAtItsApproaches() {
        assertEquals(List.of(
                        "WEST_3", "WEST_4", "WEST_5", "WEST_6",
                        "NORTH_12", "NORTH_13", "SOUTH_12", "SOUTH_13"),
                java.util.Arrays.stream(SubterraneanTrapPlan.CavernPortal.values())
                        .map(Enum::name).toList());
        java.util.Map<String, List<PlanarCell>> expectedPortals = java.util.Map.of(
                "WEST_3", List.of(new PlanarCell(10, 3), new PlanarCell(10, 4)),
                "WEST_4", List.of(new PlanarCell(10, 4), new PlanarCell(10, 5)),
                "WEST_5", List.of(new PlanarCell(10, 5), new PlanarCell(10, 6)),
                "WEST_6", List.of(new PlanarCell(10, 6), new PlanarCell(10, 7)),
                "NORTH_12", List.of(new PlanarCell(11, 2), new PlanarCell(12, 2)),
                "NORTH_13", List.of(new PlanarCell(12, 2), new PlanarCell(13, 2)),
                "SOUTH_12", List.of(new PlanarCell(11, 8), new PlanarCell(12, 8)),
                "SOUTH_13", List.of(new PlanarCell(12, 8), new PlanarCell(13, 8)));
        for (SubterraneanTrapPlan.CavernPortal portal : SubterraneanTrapPlan.CavernPortal.values()) {
            SubterraneanTrapPlan.AuthoredCavernEndpoint endpoint =
                    new SubterraneanTrapPlan.AuthoredCavernEndpoint(
                            60, SubterraneanTrapPlan.CavernDirection.EAST, portal);
            List<PlanarCell> portalCells = endpoint.portalFloors().stream()
                    .map(cell -> new PlanarCell(cell.x(), cell.z())).toList();
            List<PlanarCell> approaches = endpoint.approachFloors().stream()
                    .map(cell -> new PlanarCell(cell.x(), cell.z())).toList();
            assertEquals(expectedPortals.get(portal.name()), portalCells);
            assertTrue(endpoint.portalFloors().stream().allMatch(cell -> cell.y() == 62));
            Set<PlanarCell> cavern = planarCells(endpoint.floorCells());
            assertTrue(cavern.containsAll(portalCells), "portal floor belongs to the cavern mask");
            assertTrue(approaches.stream().noneMatch(cavern::contains),
                    "route approach stays outside the cavern mask");
            assertTrue(approaches.stream().allMatch(cell ->
                    cell.x() >= 1 && cell.x() <= 14 && cell.z() >= 1 && cell.z() <= 14));
            for (int index = 0; index < 2; index++) {
                PlanarCell inside = portalCells.get(index);
                PlanarCell outside = approaches.get(index);
                int outwardX = outside.x() - inside.x();
                int outwardZ = outside.z() - inside.z();
                assertEquals(1, Math.abs(outwardX) + Math.abs(outwardZ));
                assertEquals(inside, new PlanarCell(outside.x() - outwardX, outside.z() - outwardZ),
                        "one inward translation returns the exact portal cell");
            }
        }
    }

    private static int canonicalSpan(List<SubterraneanTrapPlan.RouteCell> cells,
                                     SubterraneanTrapPlan.CavernDirection direction, boolean xAxis) {
        java.util.function.ToIntFunction<SubterraneanTrapPlan.RouteCell> coordinate = xAxis
                ? cell -> direction.canonicalX(cell.x(), cell.z())
                : cell -> direction.canonicalZ(cell.x(), cell.z());
        return cells.stream().mapToInt(coordinate).max().orElseThrow()
                - cells.stream().mapToInt(coordinate).min().orElseThrow() + 1;
    }

    private static boolean connectedWalkable(List<SubterraneanTrapPlan.RouteCell> floors) {
        Set<SubterraneanTrapPlan.RouteCell> unseen = new HashSet<>(floors);
        ArrayDeque<SubterraneanTrapPlan.RouteCell> queue = new ArrayDeque<>();
        queue.add(unseen.iterator().next());
        unseen.remove(queue.getFirst());
        while (!queue.isEmpty()) {
            SubterraneanTrapPlan.RouteCell current = queue.removeFirst();
            List<SubterraneanTrapPlan.RouteCell> neighbours = unseen.stream().filter(candidate ->
                    Math.abs(candidate.x() - current.x()) + Math.abs(candidate.z() - current.z()) == 1
                            && Math.abs(candidate.y() - current.y()) <= 1).toList();
            neighbours.forEach(cell -> { unseen.remove(cell); queue.addLast(cell); });
        }
        return unseen.isEmpty();
    }

    private static boolean hasFilledCanonicalFiveByFive(
            List<SubterraneanTrapPlan.RouteCell> floors, SubterraneanTrapPlan.CavernDirection direction) {
        Set<PlanarCell> canonical = floors.stream().map(cell -> new PlanarCell(
                        direction.canonicalX(cell.x(), cell.z()), direction.canonicalZ(cell.x(), cell.z())))
                .collect(java.util.stream.Collectors.toSet());
        for (int x = 11; x <= 26; x++) {
            for (int z = 2; z <= 9; z++) {
                boolean full = true;
                for (int dx = 0; dx < 5; dx++) for (int dz = 0; dz < 5; dz++) {
                    full &= canonical.contains(new PlanarCell(x + dx, z + dz));
                }
                if (full) return true;
            }
        }
        return false;
    }

    private static IndependentRouteGeometry independentlyVerifyRouteGeometry(
            SubterraneanTrapLayout.Placement placement,
            List<SubterraneanTrapPlan.RouteStep> steps) {
        List<Station> stations = steps.stream()
                .map(SubterraneanTrapPlanTest::station).toList();
        Set<PlanarCell> powder = placement.powder().stream()
                .map(cell -> new PlanarCell(cell.x(), cell.z()))
                .collect(java.util.stream.Collectors.toSet());
        List<Heading> possibleInitialHeadings = cardinalHeadings().stream()
                .filter(heading -> stations.getFirst().cells().stream().allMatch(cell ->
                        powder.contains(new PlanarCell(cell.x() - heading.x(), cell.z() - heading.z()))))
                .toList();
        assertFalse(possibleInitialHeadings.isEmpty(),
                "the first two-wide station must adjoin a valid two-cell powder mouth");
        List<Heading> headings = new ArrayList<>();
        for (int index = 1; index < stations.size(); index++) {
            Station previous = stations.get(index - 1);
            Station next = stations.get(index);
            assertTrue(java.util.Collections.disjoint(previous.cells(), next.cells()),
                    "consecutive two-wide stations may not overlap");
            int dx2 = next.centerX2() - previous.centerX2();
            int dz2 = next.centerZ2() - previous.centerZ2();
            boolean forward = (Math.abs(dx2) == 2 && dz2 == 0)
                    || (dx2 == 0 && Math.abs(dz2) == 2);
            boolean quarterTurn = (Math.abs(dx2) == 3 && Math.abs(dz2) == 1)
                    || (Math.abs(dx2) == 1 && Math.abs(dz2) == 3);
            assertTrue(forward || quarterTurn,
                    "station centers permit only one-block forward or contiguous quarter-turn transforms");
            Heading heading = forward
                    ? new Heading(Integer.signum(dx2), Integer.signum(dz2))
                    : new Heading(Math.abs(dx2) == 3 ? Integer.signum(dx2) : 0,
                            Math.abs(dz2) == 3 ? Integer.signum(dz2) : 0);
            int adjacency = cardinalAdjacencyCount(previous.cells(), next.cells());
            assertEquals(0, next.orientationX() * heading.x() + next.orientationZ() * heading.z(),
                    "the next two-cell station stays perpendicular to its movement heading");
            if (forward) {
                assertEquals(previous.orientationX(), next.orientationX(),
                        "a forward transform preserves station orientation");
                assertEquals(previous.orientationZ(), next.orientationZ(),
                        "a forward transform preserves station orientation");
                assertEquals(2, adjacency,
                        "a contiguous forward transform advances both cells by one cardinal block");
            } else {
                assertEquals(1, Math.abs(previous.orientationX() * heading.x()
                                + previous.orientationZ() * heading.z()),
                        "a quarter turn advances along the prior station width");
                assertEquals(1, adjacency,
                        "a contiguous quarter turn pivots from exactly one prior edge cell");
            }
            headings.add(heading);
        }

        Set<Heading> viableInitialHeadings = possibleInitialHeadings.stream()
                .filter(initial -> headings.stream().noneMatch(heading ->
                        heading.x() == -initial.x() && heading.z() == -initial.z()))
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(viableInitialHeadings.isEmpty(),
                "coordinate-derived moves must leave at least one mouth heading unreversed");
        int maximumTurns = viableInitialHeadings.stream()
                .mapToInt(initial -> independentlyCountTurns(initial, headings)).max().orElseThrow();
        return new IndependentRouteGeometry(
                List.copyOf(headings), Set.copyOf(viableInitialHeadings), maximumTurns, stations.getLast());
    }

    private static int independentlyCountTurns(Heading initial, List<Heading> headings) {
        int turns = 0;
        Heading previous = initial;
        for (Heading heading : headings) {
            if (!heading.equals(previous)) {
                turns++;
            }
            previous = heading;
        }
        return turns;
    }

    private static String independentlyDeriveActionWord(Heading initial, List<Heading> headings) {
        StringBuilder actionWord = new StringBuilder();
        Heading left = new Heading(-initial.z(), initial.x());
        Heading right = new Heading(initial.z(), -initial.x());
        for (Heading heading : headings) {
            if (heading.equals(initial)) {
                actionWord.append('F');
            } else if (heading.equals(left)) {
                actionWord.append('L');
            } else if (heading.equals(right)) {
                actionWord.append('R');
            } else {
                throw new AssertionError("coordinate-derived heading is not forward or a quarter turn");
            }
        }
        return actionWord.toString();
    }

    private static Station station(SubterraneanTrapPlan.RouteStep step) {
        List<SubterraneanTrapPlan.RouteCell> cells = step.floorCells().stream()
                .sorted(java.util.Comparator.comparingInt(SubterraneanTrapPlan.RouteCell::x)
                        .thenComparingInt(SubterraneanTrapPlan.RouteCell::z))
                .toList();
        assertEquals(2, cells.size(), "every station is exactly two cells wide");
        int orientationX = cells.get(1).x() - cells.get(0).x();
        int orientationZ = cells.get(1).z() - cells.get(0).z();
        assertEquals(1, Math.abs(orientationX) + Math.abs(orientationZ),
                "a station is a cardinal pair, never diagonal or separated");
        return new Station(planarCells(cells),
                cells.get(0).x() + cells.get(1).x(),
                cells.get(0).z() + cells.get(1).z(),
                orientationX, orientationZ);
    }

    private static Set<PlanarCell> planarCells(List<SubterraneanTrapPlan.RouteCell> cells) {
        return cells.stream().map(cell -> new PlanarCell(cell.x(), cell.z()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Set<PlanarCell> translate(Set<PlanarCell> cells, Heading heading) {
        return cells.stream().map(cell -> new PlanarCell(
                        cell.x() + heading.x(), cell.z() + heading.z()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private static int cardinalAdjacencyCount(Set<PlanarCell> first, Set<PlanarCell> second) {
        return (int) first.stream().flatMap(left -> second.stream().map(right ->
                        Math.abs(left.x() - right.x()) + Math.abs(left.z() - right.z())))
                .filter(distance -> distance == 1).count();
    }

    private static List<Heading> cardinalHeadings() {
        return List.of(new Heading(-1, 0), new Heading(1, 0),
                new Heading(0, -1), new Heading(0, 1));
    }

    private record PlanarCell(int x, int z) {
    }

    private record Heading(int x, int z) {
    }

    private record Station(Set<PlanarCell> cells, int centerX2, int centerZ2,
                           int orientationX, int orientationZ) {
    }

    private record IndependentRouteGeometry(
            List<Heading> headings, Set<Heading> viableInitialHeadings,
            int maximumTurns, Station terminal) {
    }

    @Test
    void destinationKindCannotMislabelEitherEndpointVariant() {
        SubterraneanTrapPlan.Plan natural = validPlan().accepted();
        assertThrows(IllegalArgumentException.class, () -> new SubterraneanTrapPlan.Plan(
                natural.roofY(), natural.landingY(), natural.descentRoute(),
                SubterraneanTrapPlan.DestinationKind.AUTHORED_CAVERN, natural.writes()));

        SubterraneanTrapPlan.Plan cavern = SubterraneanTrapPlan.authoredCavernAlternatives(
                PLACEMENT, firstAir(101), fullSnow(), 32,
                SubterraneanTrapPlan.CavernDirection.EAST).getFirst();
        assertThrows(IllegalArgumentException.class, () -> new SubterraneanTrapPlan.Plan(
                        cavern.roofY(), cavern.landingY(), cavern.descentRoute(), cavern.writes()),
                "the compatibility constructor must not label an authored endpoint as natural");
    }

    @Test
    void twoSameDirectionQuarterTurnsCannotReverseTheInitialHeading() {
        int initialX = 1;
        int initialZ = 0;
        int[][] leftTwice = {{1, 0}, {0, 1}, {-1, 0}};
        int[][] rightTwice = {{1, 0}, {0, -1}, {-1, 0}};
        assertTrue(java.util.Arrays.stream(leftTwice, 0, 2).allMatch(heading ->
                SubterraneanTrapPlan.headingNeverReversesInitial(
                        initialX, initialZ, heading[0], heading[1])));
        assertFalse(java.util.Arrays.stream(leftTwice).allMatch(heading ->
                SubterraneanTrapPlan.headingNeverReversesInitial(
                        initialX, initialZ, heading[0], heading[1])));
        assertFalse(java.util.Arrays.stream(rightTwice).allMatch(heading ->
                SubterraneanTrapPlan.headingNeverReversesInitial(
                        initialX, initialZ, heading[0], heading[1])));
    }

    @Test
    void destinationProbesExcludeClearanceThatReentersTheLandingShaft() {
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(PLACEMENT.powder());
        List<SubterraneanTrapPlan.DestinationProbe> probes =
                SubterraneanTrapPlan.destinationProbes(PLACEMENT, 68);
        assertFalse(probes.isEmpty());
        for (SubterraneanTrapPlan.DestinationProbe probe : probes) {
            for (int index = 0; index < probe.distance(); index++) {
                int floorY = 68 - index - 1;
                int x = probe.entryX() + probe.forwardDx() * index;
                int z = probe.entryZ() + probe.forwardDz() * index;
                for (int width = 0; width < SubterraneanTrapPlan.ROUTE_WIDTH; width++) {
                    SubterraneanTrapLayout.Cell projection = new SubterraneanTrapLayout.Cell(
                            x + probe.widthDx() * width, z + probe.widthDz() * width);
                    assertFalse(powder.contains(projection)
                                    && floorY + SubterraneanTrapPlan.ROUTE_CLEAR_HEIGHT >= 67,
                            "route floor/headroom must not overlap a cushion or shaft column");
                }
            }
        }
    }

    @Test
    void everyRouteStationIsTwoWideThreeHighAndStrictlyDescendsFromTheLanding() {
        SubterraneanTrapPlan.Plan plan = validPlan().accepted();
        int previousY = plan.landingY();
        boolean descended = false;
        Set<String> floors = new HashSet<>();
        for (SubterraneanTrapPlan.RouteStep step : plan.descentRoute().steps()) {
            assertEquals(2, step.floorCells().size(), "passage is exactly two floor blocks wide");
            assertTrue(step.y() <= previousY, "no authored station climbs toward the surface");
            descended |= step.y() < previousY;
            previousY = step.y();
            for (SubterraneanTrapPlan.RouteCell floor : step.floorCells()) {
                assertTrue(floors.add(floor.x() + ":" + floor.y() + ":" + floor.z()));
                assertEquals(1, writesAt(plan, floor, SubterraneanTrapPlan.Phase.DESCENT_FLOOR));
                for (int dy = 1; dy <= 3; dy++) {
                    assertEquals(1, writesAt(plan,
                            new SubterraneanTrapPlan.RouteCell(
                                    floor.x(), floor.y() + dy, floor.z()),
                            SubterraneanTrapPlan.Phase.DESCENT_CLEAR),
                            "every width cell has three clear blocks above its safe floor");
                }
            }
        }
        assertTrue(descended, "the route makes real downward progress");
        assertEquals(plan.descentRoute().destination().probe().floorY(), previousY,
                "authored stairs terminate level with the untouched lower cave floor");
    }

    @Test
    void activePlanHasNoUpwardEscapeMineTailOrSurfacePlugWrites() {
        SubterraneanTrapPlan.Plan plan = validPlan().accepted();
        Set<SubterraneanTrapPlan.Phase> active = plan.writes().stream()
                .map(SubterraneanTrapPlan.Write::phase).collect(java.util.stream.Collectors.toSet());
        assertTrue(active.containsAll(Set.of(
                SubterraneanTrapPlan.Phase.CUSHION_BASE,
                SubterraneanTrapPlan.Phase.CUSHION,
                SubterraneanTrapPlan.Phase.DESCENT_FLOOR,
                SubterraneanTrapPlan.Phase.DESCENT_CLEAR,
                SubterraneanTrapPlan.Phase.CLEAR,
                SubterraneanTrapPlan.Phase.SURFACE_POWDER)));
        assertFalse(active.contains(SubterraneanTrapPlan.Phase.ESCAPE_FLOOR));
        assertFalse(active.contains(SubterraneanTrapPlan.Phase.ESCAPE_CLEAR));
        assertFalse(active.contains(SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL));
        for (SubterraneanTrapPlan.Write write : plan.writes()) {
            if (write.phase() == SubterraneanTrapPlan.Phase.DESCENT_FLOOR
                    || write.phase() == SubterraneanTrapPlan.Phase.DESCENT_CLEAR) {
                assertTrue(write.y() <= plan.landingY() + SubterraneanTrapPlan.ROUTE_CLEAR_HEIGHT,
                        "the descending passage never approaches the surface shell");
            }
        }
    }

    @Test
    void lowerNaturalDestinationRemainsUntouchedAndProvidesConnectedContinuation() {
        SubterraneanTrapPlan.Plan plan = validPlan().accepted();
        SubterraneanTrapPlan.NaturalCaveDestination destination = plan.descentRoute().destination();
        assertTrue(destination.continuationFloors().size()
                >= SubterraneanTrapPlan.MIN_NATURAL_CONTINUATION_FLOORS);
        for (SubterraneanTrapPlan.RouteCell naturalFloor : destination.continuationFloors()) {
            assertEquals(0, writesAtAnyPhase(plan, naturalFloor),
                    "the certified cave stays natural rather than becoming an authored floor patch");
        }
        assertTrue(destination.continuationFloors().containsAll(destination.probe().targetFloors()));
        for (SubterraneanTrapPlan.RouteCell naturalFloor : destination.continuationFloors()) {
            assertTrue(SubterraneanTrapPlan.naturalContinuationColumnAvoidsAuthoredRoute(
                    destination.probe(), naturalFloor),
                    "each natural witness column, including three blocks of headroom, is disjoint from the stair");
        }
    }

    @Test
    void malformedOrDisconnectedLowerWitnessesRejectBeforePlanning() {
        SubterraneanTrapPlan.DestinationProbe probe = probe();
        assertThrows(IllegalArgumentException.class, () ->
                new SubterraneanTrapPlan.NaturalCaveDestination(probe, probe.targetFloors()));

        List<SubterraneanTrapPlan.RouteCell> disconnected = new ArrayList<>(probe.targetFloors());
        for (int index = 0; index < 6; index++) {
            disconnected.add(new SubterraneanTrapPlan.RouteCell(
                    15 - index, probe.floorY(), 15));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new SubterraneanTrapPlan.NaturalCaveDestination(probe, disconnected));
    }

    @Test
    void naturalContinuationRejectsAHeadroomLoopBackIntoDescentClearance() {
        SubterraneanTrapPlan.DestinationProbe probe = probe();
        int landingY = probe.floorY() + probe.distance();
        SubterraneanTrapPlan.RouteCell routeFloor = new SubterraneanTrapPlan.RouteCell(
                probe.entryX(), landingY - 1, probe.entryZ());
        SubterraneanTrapPlan.RouteCell loopBackFloor = new SubterraneanTrapPlan.RouteCell(
                routeFloor.x(), routeFloor.y() - 1, routeFloor.z());
        assertFalse(SubterraneanTrapPlan.naturalContinuationColumnAvoidsAuthoredRoute(probe, routeFloor),
                "the authored floor itself cannot be recast as natural continuation");
        assertFalse(SubterraneanTrapPlan.naturalContinuationColumnAvoidsAuthoredRoute(probe, loopBackFloor),
                "a natural floor whose three-block headroom enters DESCENT_CLEAR must be rejected");

        List<SubterraneanTrapPlan.RouteCell> witness = new ArrayList<>(destination().continuationFloors());
        witness.set(witness.size() - 1, loopBackFloor);
        assertThrows(IllegalArgumentException.class, () ->
                new SubterraneanTrapPlan.NaturalCaveDestination(probe, witness));
    }

    @Test
    void cataloguePlacementsAndPreferredDepthsKeepEveryNaturalWitnessColumnOutOfAuthoredRouteVolume() {
        List<SubterraneanTrapLayout.Placement> placements = SubterraneanTrapLayout.placements(73L, 4, -9);
        assertEquals(64, placements.size(), "the fixed full local placement catalogue remains covered");
        int placementDepths = 0;
        int legalProbes = 0;
        for (SubterraneanTrapLayout.Placement placement : placements) {
            for (int depth : SubterraneanTrapPlan.preferredDepthOrder()) {
                int landingY = 100 - depth;
                List<SubterraneanTrapPlan.DestinationProbe> probes =
                        SubterraneanTrapPlan.destinationProbes(placement, landingY);
                placementDepths++;
                assertFalse(probes.isEmpty(), "every catalogue placement/depth retains a legal descent probe");
                for (SubterraneanTrapPlan.DestinationProbe probe : probes) {
                    SubterraneanTrapPlan.NaturalCaveDestination destination = destinationFor(probe);
                    for (SubterraneanTrapPlan.RouteCell floor : destination.continuationFloors()) {
                        assertTrue(SubterraneanTrapPlan.naturalContinuationColumnAvoidsAuthoredRoute(
                                destination.probe(), floor));
                    }
                    legalProbes++;
                }
            }
        }
        assertEquals(576, placementDepths, "64 catalogue placements times nine preferred depths are exercised");
        assertEquals(17_280, legalProbes, "every fixed legal route probe remains covered");
    }

    @Test
    void routeWritesAndShellReadsStayInsideTheOwnerChunkDiscipline() {
        SubterraneanTrapPlan.Plan plan = validPlan().accepted();
        Set<String> writes = new HashSet<>();
        for (SubterraneanTrapPlan.Write write : plan.writes()) {
            assertTrue(write.x() >= 1 && write.x() <= 14 && write.z() >= 1 && write.z() <= 14);
            assertTrue(writes.add(write.x() + ":" + write.y() + ":" + write.z()));
        }
        for (SubterraneanTrapPlan.RouteCell probe : plan.descentRoute().shellProbes()) {
            assertTrue(probe.x() >= 0 && probe.x() <= 15 && probe.z() >= 0 && probe.z() <= 15);
        }
    }

    @Test
    void surfaceReliefAndSupportLawsRemainInForce() {
        int[][] raisedPowder = firstAir(101);
        SubterraneanTrapLayout.Cell powder = PLACEMENT.powder().getFirst();
        raisedPowder[powder.x()][powder.z()] = 103;
        SubterraneanTrapPlan.Result cliff = SubterraneanTrapPlan.plan(
                PLACEMENT, raisedPowder, fullSnow(), List.of(destination()));
        assertFalse(cliff.isAccepted());
        assertEquals(SubterraneanTrapPlan.Rejection.POWDER_RELIEF_EXCEEDS_ONE, cliff.rejection(),
                "an abrupt two-block break inside the powder cap still rejects");

        int[][] kinds = fullSnow();
        kinds[powder.x()][powder.z()] = SubterraneanTrapPlan.OTHER;
        assertFalse(SubterraneanTrapPlan.plan(
                PLACEMENT, firstAir(101), kinds, List.of(destination())).isAccepted());
    }

    @Test
    void snowyMouthMayUseOnlyFirmGlacialIceForItsUnchangedRimAndApproach() {
        int[][] kinds = surfaceKind(SubterraneanTrapPlan.FIRM_GLACIAL_ICE);
        for (SubterraneanTrapLayout.Cell powder : PLACEMENT.powder()) {
            kinds[powder.x()][powder.z()] = SubterraneanTrapPlan.THIN_OVER_FULL_SNOW;
        }

        SubterraneanTrapPlan.Result supported = SubterraneanTrapPlan.plan(
                PLACEMENT, firstAir(101), kinds, List.of(destination()));
        assertTrue(supported.isAccepted(),
                "a thin-snow mouth may meet an untouched packed/blue-ice glacier rim and firm approach");

        SubterraneanTrapLayout.Cell powder = PLACEMENT.powder().getFirst();
        kinds[powder.x()][powder.z()] = SubterraneanTrapPlan.FIRM_GLACIAL_ICE;
        SubterraneanTrapPlan.Result exposedIceMouth = SubterraneanTrapPlan.plan(
                PLACEMENT, firstAir(101), kinds, List.of(destination()));
        assertFalse(exposedIceMouth.isAccepted(),
                "bare glacier ice is never a concealed powder-mouth replacement surface");
        assertEquals(SubterraneanTrapPlan.Rejection.UNSUPPORTED_SURFACE, exposedIceMouth.rejection());
    }

    @Test
    void surfaceDiagnosticsNameEachExistingSurfaceExitWithoutChangingItsVerdict() {
        assertSurfaceDiagnostic(surfaceKind(SubterraneanTrapPlan.FIRM_GLACIAL_ICE), PLACEMENT.powder().getFirst(),
                SubterraneanTrapPlan.SurfaceRejectionReason.MOUTH_FIRM_GLACIAL);
        assertSurfaceDiagnostic(surfaceKind(SubterraneanTrapPlan.OTHER), PLACEMENT.powder().getFirst(),
                SubterraneanTrapPlan.SurfaceRejectionReason.MOUTH_OTHER);

        int[][] collarKinds = firmGlacierWithSnowMouth();
        SubterraneanTrapLayout.Cell collar = firstCollarCell();
        collarKinds[collar.x()][collar.z()] = SubterraneanTrapPlan.OTHER;
        assertSurfaceDiagnostic(collarKinds, null, SubterraneanTrapPlan.SurfaceRejectionReason.COLLAR_OTHER);

        SubterraneanTrapLayout.Cell approach = thirdApproachCell();
        int[][] approachOtherKinds = firmGlacierWithSnowMouth();
        approachOtherKinds[approach.x()][approach.z()] = SubterraneanTrapPlan.OTHER;
        assertSurfaceDiagnostic(approachOtherKinds, null, SubterraneanTrapPlan.SurfaceRejectionReason.APPROACH_OTHER);

        int[][] approachPowderKinds = firmGlacierWithSnowMouth();
        approachPowderKinds[approach.x()][approach.z()] = SubterraneanTrapPlan.POWDER;
        assertSurfaceDiagnostic(approachPowderKinds, null, SubterraneanTrapPlan.SurfaceRejectionReason.APPROACH_POWDER);

        SubterraneanTrapPlan.SurfaceDiagnosticResult invalid =
                SubterraneanTrapPlan.planWithSurfaceDiagnostics(null, firstAir(101), fullSnow(), List.of(destination()));
        assertEquals(SubterraneanTrapPlan.Rejection.INVALID_INPUT, invalid.result().rejection());
        assertEquals(SubterraneanTrapPlan.SurfaceRejectionReason.INVALID_INPUT, invalid.surfaceRejectionReason());
    }

    @Test
    void realSnowLayerGradeSurvivesSurfaceRingDepthAndRouteCertification() {
        int[][] generatedSurface = gentleGeneratedSurface();
        int minimumRoof = PLACEMENT.powder().stream()
                .mapToInt(cell -> generatedSurface[cell.x()][cell.z()] - 1)
                .min().orElseThrow();
        int maximumRoof = PLACEMENT.powder().stream()
                .mapToInt(cell -> generatedSurface[cell.x()][cell.z()] - 1)
                .max().orElseThrow();
        assertTrue(maximumRoof - minimumRoof > 3,
                "fixture reproduces the cumulative real-world slope rejected by the old flat-surface range cap");

        int landingY = minimumRoof - SubterraneanTrapPlan.PREFERRED_DEPTH;
        SubterraneanTrapPlan.DestinationProbe route =
                SubterraneanTrapPlan.destinationProbes(PLACEMENT, landingY).getFirst();
        SubterraneanTrapPlan.Result result = SubterraneanTrapPlan.plan(
                PLACEMENT, generatedSurface, surfaceKind(SubterraneanTrapPlan.THIN_OVER_FULL_SNOW),
                List.of(destinationFor(route)));

        assertTrue(result.isAccepted(),
                "a locally one-block-smooth snow-layer grade reaches lower-cave route certification");
        assertEquals(minimumRoof, result.accepted().roofY());
        assertTrue(result.accepted().writes().stream()
                        .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.SURFACE_POWDER)
                        .allMatch(write -> write.y() == generatedSurface[write.x()][write.z()] - 1),
                "each powder cell still follows the exact captured visible surface");
        assertTrue(result.accepted().writes().stream()
                        .filter(write -> write.phase() == SubterraneanTrapPlan.Phase.SURFACE_POWDER)
                        .allMatch(write -> write.y() - result.accepted().landingY()
                                >= SubterraneanTrapPlan.MIN_LEGAL_DEPTH
                                && write.y() - result.accepted().landingY()
                                <= SubterraneanTrapPlan.MAX_LEGAL_DEPTH),
                "every sloped column retains the independent legal fall-depth bound");
    }

    @Test
    void thinSnowReplacementStillOccursOnlyAfterTheCompleteUndergroundPlan() {
        int[][] kinds = fullSnow();
        SubterraneanTrapLayout.Cell powder = PLACEMENT.powder().getFirst();
        kinds[powder.x()][powder.z()] = SubterraneanTrapPlan.THIN_SNOW;
        SubterraneanTrapPlan.Plan plan = SubterraneanTrapPlan.plan(
                PLACEMENT, firstAir(101), kinds, List.of(destination())).accepted();
        assertEquals(SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER,
                plan.writes().getLast().phase());
        assertEquals(1, plan.writes().stream()
                .filter(write -> write.x() == powder.x() && write.y() == 101 && write.z() == powder.z()
                        && write.phase() == SubterraneanTrapPlan.Phase.REMOVE_SURFACE_LAYER)
                .count());
    }

    @Test
    void alternativesAreDeterministicUniqueAndBounded() {
        List<SubterraneanTrapPlan.NaturalCaveDestination> targets = List.of(destination());
        List<SubterraneanTrapPlan.Plan> first = SubterraneanTrapPlan.planAlternatives(
                PLACEMENT, firstAir(101), fullSnow(), 32, targets);
        List<SubterraneanTrapPlan.Plan> second = SubterraneanTrapPlan.planAlternatives(
                PLACEMENT, firstAir(101), fullSnow(), 32, targets);
        assertEquals(first, second);
        assertFalse(first.isEmpty());
        assertTrue(first.size() <= 34);
        assertEquals(first.size(), new HashSet<>(first).size());
        assertEquals(List.of(32, 33, 31, 34, 30, 35, 29, 36, 28),
                SubterraneanTrapPlan.preferredDepthOrder());
    }

    @Test
    void deeperDepth64FindsOnlyItsCertifiedNaturalDestinationAfterTheLegacyPrefix() {
        List<Integer> depths = SubterraneanTrapPlan.landingDepthOrder(100);
        assertEquals(List.of(32, 33, 31, 34, 30, 35, 29, 36, 28),
                depths.subList(0, SubterraneanTrapPlan.preferredDepthOrder().size()));
        assertEquals(64, depths.size(), "roof Y100 searches nine legacy depths plus 37..91 exactly once");
        assertEquals(91, depths.getLast(),
                "the deepest candidate keeps an eight-block destination strictly above Y0");

        int landingY = 100 - 64;
        SubterraneanTrapPlan.DestinationProbe depth64Probe =
                SubterraneanTrapPlan.destinationProbes(PLACEMENT, landingY).getFirst();
        SubterraneanTrapPlan.NaturalCaveDestination depth64Destination = destinationFor(depth64Probe);
        List<SubterraneanTrapPlan.Plan> plans = SubterraneanTrapPlan.planAlternatives(
                PLACEMENT, firstAir(101), fullSnow(), 64, List.of(depth64Destination));

        assertFalse(plans.isEmpty(), "a certified depth64 natural glacial cave becomes reachable");
        SubterraneanTrapPlan.Plan selected = plans.getFirst();
        assertEquals(64, selected.roofY() - selected.landingY());
        assertEquals(36, selected.landingY());
        assertTrue(selected.descentRoute().destination().probe().floorY() > 0);
        int previousY = selected.landingY();
        for (SubterraneanTrapPlan.RouteStep step : selected.descentRoute().steps()) {
            assertEquals(2, step.floorCells().size());
            assertTrue(step.y() <= previousY, "the depth64 route is level-or-descending only");
            previousY = step.y();
        }
        assertEquals(selected.descentRoute().destination().probe().floorY(), previousY);
        assertTrue(selected.descentRoute().steps().stream()
                .flatMap(step -> step.floorCells().stream())
                .allMatch(floor -> java.util.stream.IntStream.rangeClosed(1, 3)
                        .allMatch(dy -> writesAt(selected,
                                new SubterraneanTrapPlan.RouteCell(floor.x(), floor.y() + dy, floor.z()),
                                SubterraneanTrapPlan.Phase.DESCENT_CLEAR) == 1)));
        assertTrue(selected.writes().stream().noneMatch(write ->
                write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_FLOOR
                        || write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_CLEAR
                        || write.phase() == SubterraneanTrapPlan.Phase.ESCAPE_MINE_TAIL));
        assertTrue(selected.writes().stream().allMatch(write ->
                write.x() >= 1 && write.x() <= 14 && write.z() >= 1 && write.z() <= 14));
    }

    @Test
    void y0BoundaryAndHardDepthRemainFailClosed() {
        List<Integer> y0Pruned = SubterraneanTrapPlan.landingDepthOrder(72);
        assertEquals(63, y0Pruned.getLast());
        assertFalse(y0Pruned.contains(64),
                "roof Y72 cannot try depth64 because its deepest target would be Y0");
        SubterraneanTrapPlan.DestinationProbe ordinary =
                SubterraneanTrapPlan.destinationProbes(PLACEMENT, 36).getFirst();
        assertThrows(IllegalArgumentException.class, () -> new SubterraneanTrapPlan.DestinationProbe(
                ordinary.entryX(), ordinary.entryZ(), ordinary.targetX(), ordinary.targetZ(),
                ordinary.widthDx(), ordinary.widthDz(), ordinary.forwardDx(), ordinary.forwardDz(),
                0, ordinary.distance()), "a Y0 destination can never become certified evidence");

        assertEquals(SubterraneanTrapPlan.MAX_HARD_DEPTH, SubterraneanTrapPlan.MAX_LEGAL_DEPTH,
                "deep natural landings use, but never relax, the existing hard bound");
        List<Integer> highRoofDepths = SubterraneanTrapPlan.landingDepthOrder(110);
        assertEquals(SubterraneanTrapPlan.MAX_HARD_DEPTH, highRoofDepths.getLast());
        int hardLandingY = 110 - SubterraneanTrapPlan.MAX_HARD_DEPTH;
        SubterraneanTrapPlan.NaturalCaveDestination hardBoundDestination = destinationFor(
                SubterraneanTrapPlan.destinationProbes(PLACEMENT, hardLandingY).getFirst());
        assertFalse(SubterraneanTrapPlan.planAlternatives(
                PLACEMENT, firstAir(111), fullSnow(), SubterraneanTrapPlan.MAX_HARD_DEPTH,
                List.of(hardBoundDestination)).isEmpty(), "the exact hard bound remains legal above Y0");

        int beyondHardLandingY = 110 - SubterraneanTrapPlan.MAX_HARD_DEPTH - 1;
        SubterraneanTrapPlan.NaturalCaveDestination beyondHardDestination = destinationFor(
                SubterraneanTrapPlan.destinationProbes(PLACEMENT, beyondHardLandingY).getFirst());
        assertTrue(SubterraneanTrapPlan.planAlternatives(
                PLACEMENT, firstAir(111), fullSnow(), SubterraneanTrapPlan.MAX_HARD_DEPTH + 1,
                List.of(beyondHardDestination)).isEmpty(), "depth97 remains outside the immutable hard bound");
    }

    private static SubterraneanTrapPlan.Result validPlan() {
        return SubterraneanTrapPlan.plan(
                PLACEMENT, firstAir(101), fullSnow(), List.of(destination()));
    }

    private static void assertSurfaceDiagnostic(int[][] kinds, SubterraneanTrapLayout.Cell changedMouth,
                                                SubterraneanTrapPlan.SurfaceRejectionReason expected) {
        if (changedMouth != null) {
            kinds[changedMouth.x()][changedMouth.z()] = expected == SubterraneanTrapPlan.SurfaceRejectionReason.MOUTH_FIRM_GLACIAL
                    ? SubterraneanTrapPlan.FIRM_GLACIAL_ICE : SubterraneanTrapPlan.OTHER;
        }
        SubterraneanTrapPlan.Result ordinary = SubterraneanTrapPlan.plan(
                PLACEMENT, firstAir(101), kinds, List.of(destination()));
        SubterraneanTrapPlan.SurfaceDiagnosticResult diagnostic =
                SubterraneanTrapPlan.planWithSurfaceDiagnostics(PLACEMENT, firstAir(101), kinds, List.of(destination()));
        assertFalse(ordinary.isAccepted());
        assertEquals(SubterraneanTrapPlan.Rejection.UNSUPPORTED_SURFACE, ordinary.rejection());
        assertEquals(ordinary, diagnostic.result(), "diagnostics must not alter the ordinary planner verdict");
        assertEquals(expected, diagnostic.surfaceRejectionReason());
    }

    private static int[][] firmGlacierWithSnowMouth() {
        int[][] kinds = surfaceKind(SubterraneanTrapPlan.FIRM_GLACIAL_ICE);
        for (SubterraneanTrapLayout.Cell powder : PLACEMENT.powder()) {
            kinds[powder.x()][powder.z()] = SubterraneanTrapPlan.FULL_SNOW;
        }
        return kinds;
    }

    private static SubterraneanTrapLayout.Cell firstCollarCell() {
        Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(PLACEMENT.powder());
        return powder.stream().flatMap(cell -> java.util.stream.IntStream.rangeClosed(-1, 1).boxed()
                        .flatMap(dx -> java.util.stream.IntStream.rangeClosed(-1, 1)
                                .mapToObj(dz -> new SubterraneanTrapLayout.Cell(cell.x() + dx, cell.z() + dz))))
                .filter(cell -> cell.x() >= 0 && cell.x() < 16 && cell.z() >= 0 && cell.z() < 16)
                .filter(cell -> !powder.contains(cell))
                .findFirst().orElseThrow();
    }

    private static SubterraneanTrapLayout.Cell thirdApproachCell() {
        boolean axisX = PLACEMENT.template().axis() == SubterraneanTrapLayout.Axis.X;
        SubterraneanTrapLayout.Cell lowEnd = PLACEMENT.powder().stream()
                .min(axisX ? java.util.Comparator.comparingInt(SubterraneanTrapLayout.Cell::x)
                        .thenComparingInt(SubterraneanTrapLayout.Cell::z)
                        : java.util.Comparator.comparingInt(SubterraneanTrapLayout.Cell::z)
                                .thenComparingInt(SubterraneanTrapLayout.Cell::x))
                .orElseThrow();
        return axisX ? new SubterraneanTrapLayout.Cell(lowEnd.x() - 3, lowEnd.z())
                : new SubterraneanTrapLayout.Cell(lowEnd.x(), lowEnd.z() - 3);
    }

    private static SubterraneanTrapPlan.NaturalCaveDestination destination() {
        return destinationFor(probe());
    }

    private static SubterraneanTrapPlan.NaturalCaveDestination destinationFor(
            SubterraneanTrapPlan.DestinationProbe probe) {
        Set<SubterraneanTrapPlan.RouteCell> floors = new java.util.LinkedHashSet<>(probe.targetFloors());
        ArrayDeque<SubterraneanTrapPlan.RouteCell> queue = new ArrayDeque<>(probe.targetFloors());
        while (floors.size() < SubterraneanTrapPlan.MIN_NATURAL_CONTINUATION_FLOORS) {
            SubterraneanTrapPlan.RouteCell current = queue.removeFirst();
            for (int[] offset : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                SubterraneanTrapPlan.RouteCell next = new SubterraneanTrapPlan.RouteCell(
                        current.x() + offset[0], current.y(), current.z() + offset[1]);
                if (next.x() >= 0 && next.x() <= 15 && next.z() >= 0 && next.z() <= 15
                        && SubterraneanTrapPlan.naturalContinuationColumnAvoidsAuthoredRoute(probe, next)
                        && floors.add(next)) {
                    queue.addLast(next);
                    if (floors.size() == SubterraneanTrapPlan.MIN_NATURAL_CONTINUATION_FLOORS) {
                        break;
                    }
                }
            }
        }
        return new SubterraneanTrapPlan.NaturalCaveDestination(probe, List.copyOf(floors));
    }

    private static SubterraneanTrapPlan.DestinationProbe probe() {
        return SubterraneanTrapPlan.destinationProbes(PLACEMENT, 68).getFirst();
    }

    private static SubterraneanTrapLayout.Placement placementWithProbe() {
        return SubterraneanTrapLayout.placements(73L, 4, -9).stream()
                .filter(placement -> !SubterraneanTrapPlan.destinationProbes(placement, 68).isEmpty())
                .findFirst().orElseThrow();
    }

    private static int writesAt(SubterraneanTrapPlan.Plan plan,
                                SubterraneanTrapPlan.RouteCell cell,
                                SubterraneanTrapPlan.Phase phase) {
        return (int) plan.writes().stream()
                .filter(write -> write.x() == cell.x() && write.y() == cell.y() && write.z() == cell.z()
                        && write.phase() == phase)
                .count();
    }

    private static int writesAtAnyPhase(SubterraneanTrapPlan.Plan plan,
                                        SubterraneanTrapPlan.RouteCell cell) {
        return (int) plan.writes().stream()
                .filter(write -> write.x() == cell.x() && write.y() == cell.y() && write.z() == cell.z())
                .count();
    }

    private static int[][] firstAir(int value) {
        int[][] heights = new int[16][16];
        for (int x = 0; x < 16; x++) {
            java.util.Arrays.fill(heights[x], value);
        }
        return heights;
    }

    private static int[][] gentleGeneratedSurface() {
        int[][] heights = new int[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                heights[x][z] = 101 + x / 2 + z / 2;
            }
        }
        return heights;
    }

    private static int[][] surfaceKind(int value) {
        int[][] kinds = new int[16][16];
        for (int x = 0; x < 16; x++) {
            java.util.Arrays.fill(kinds[x], value);
        }
        return kinds;
    }

    private static int[][] fullSnow() {
        return surfaceKind(SubterraneanTrapPlan.FULL_SNOW);
    }
}
