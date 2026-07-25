package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-JVM laws for the bounded snowfield-cap layout and its chunk gate. */
class SubterraneanTrapLayoutTest {

    @Test
    void catalogPinsBothSizesAndAxes() {
        List<SubterraneanTrapLayout.Template> catalog = SubterraneanTrapLayout.catalog();
        assertEquals(4, catalog.size());
        assertEquals(2, catalog.stream().filter(template -> template.axis() == SubterraneanTrapLayout.Axis.X).count());
        assertEquals(2, catalog.stream().filter(template -> template.axis() == SubterraneanTrapLayout.Axis.Z).count());
        assertEquals(Set.of(12, 14), catalog.stream().map(SubterraneanTrapLayout.Template::stations)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(catalog.stream().allMatch(template -> template.width() == 7));
    }

    @Test
    void everyPowderFootprintIsSparseConnectedVariableAndHasTwoMeaningfulEdgeBays() {
        for (SubterraneanTrapLayout.Template template : SubterraneanTrapLayout.catalog()) {
            int xExtent = template.axis() == SubterraneanTrapLayout.Axis.X ? template.stations() : template.width();
            int zExtent = template.axis() == SubterraneanTrapLayout.Axis.X ? template.width() : template.stations();
            Set<SubterraneanTrapLayout.Cell> powder = Set.copyOf(template.powder());
            Set<SubterraneanTrapLayout.Cell> solid = Set.copyOf(template.solid());
            Set<SubterraneanTrapLayout.Cell> implied = Set.copyOf(template.implied());

            assertEquals(template.powder().size(), powder.size(), "powder cells are unique");
            assertEquals(template.solid().size(), solid.size(), "solid cells are unique");
            assertEquals(template.powder().size() + template.solid().size(), implied.size(), "subsets do not overlap");
            assertEquals(template.stations() * template.width(), implied.size(), "no holes in implied patch");
            assertTrue(implied.stream().allMatch(cell -> cell.x() >= 0 && cell.x() < xExtent
                    && cell.z() >= 0 && cell.z() < zExtent), "template dimensions bound every cell");
            assertConnected(powder);
            assertTrue(powder.size() * 100 <= powderBoundingBoxArea(powder) * 80,
                    "powder uses at most 80% of its own bounding box, leaving a snowfield-like silhouette");
            assertTransverseStationWidths(template, powder);
            assertTrue(countMeaningfulEdgeBays(powder) >= 2,
                    "at least two inset boundary bays keep the cap from reading as a filled platform");
        }
    }

    @Test
    void placementEnumerationIsDeterministicCompleteAndRingBounded() {
        long seed = -0x1234_5678_9ABCDEFL;
        List<SubterraneanTrapLayout.Placement> one = SubterraneanTrapLayout.placements(seed, Integer.MIN_VALUE,
                Integer.MAX_VALUE);
        List<SubterraneanTrapLayout.Placement> two = SubterraneanTrapLayout.placements(seed, Integer.MIN_VALUE,
                Integer.MAX_VALUE);
        assertEquals(one, two);
        assertEquals(64, one.size(), "all ring-preserving offsets of all four templates are considered");
        assertEquals(Set.copyOf(SubterraneanTrapLayout.catalog()),
                one.stream().map(SubterraneanTrapLayout.Placement::template).collect(java.util.stream.Collectors.toSet()));
        assertEquals(one.size(), Set.copyOf(one).size(), "placement catalogue is deduplicated");
        for (SubterraneanTrapLayout.Placement placement : one) {
            assertTrue(placement.implied().stream().allMatch(cell -> cell.x() >= 1 && cell.x() <= 14
                    && cell.z() >= 1 && cell.z() <= 14), "the complete one-cell approach ring remains solid");
        }
    }

    @Test
    void priorityIsStableForNegativeAndExtremeInputs() {
        long[][] inputs = {
                {0L, 0, 0}, {Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE},
                {Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE}, {-1L, -1, -1}
        };
        Set<Long> observed = new HashSet<>();
        for (long[] input : inputs) {
            long first = SubterraneanTrapLayout.priority(input[0], (int) input[1], (int) input[2]);
            assertEquals(first, SubterraneanTrapLayout.priority(input[0], (int) input[1], (int) input[2]));
            observed.add(first);
        }
        assertEquals(inputs.length, observed.size(), "extreme coordinates do not collapse through signed arithmetic");
        assertNotEquals(SubterraneanTrapLayout.priority(99L, -7, 3), SubterraneanTrapLayout.priority(99L, 3, -7));
    }

    @Test
    void templateRejectsInvalidPublicGeometry() {
        SubterraneanTrapLayout.Cell cell = new SubterraneanTrapLayout.Cell(0, 0);
        assertThrows(NullPointerException.class,
                () -> new SubterraneanTrapLayout.Template(null, 3, 3, List.of(cell), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SubterraneanTrapLayout.Template(SubterraneanTrapLayout.Axis.X, 3, 3,
                        List.of(cell, cell), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SubterraneanTrapLayout.Template(SubterraneanTrapLayout.Axis.X, 3, 3,
                        List.of(cell), List.of(cell)));
        assertThrows(IllegalArgumentException.class,
                () -> new SubterraneanTrapLayout.Template(SubterraneanTrapLayout.Axis.X, 3, 3,
                        List.of(new SubterraneanTrapLayout.Cell(3, 0)), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SubterraneanTrapLayout.Template(SubterraneanTrapLayout.Axis.X, 15, 3,
                        List.of(), List.of()));
    }

    @Test
    void placementRejectsNullTemplatesAndOffsetsThatCrossTheApproachRing() {
        SubterraneanTrapLayout.Template template = SubterraneanTrapLayout.catalog().getFirst();
        assertThrows(NullPointerException.class, () -> new SubterraneanTrapLayout.Placement(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SubterraneanTrapLayout.Placement(template, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SubterraneanTrapLayout.Placement(template, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SubterraneanTrapLayout.Placement(template, 15, 1));
        assertThrows(IllegalArgumentException.class, () -> new SubterraneanTrapLayout.Placement(template, 1, 15));
    }

    @Test
    void rawGateAdmitsEveryOwnerChunkWithoutQuadrantBias() {
        long seed = 0x5EED_F00DL;
        int raw = 0;
        int total = 0;
        int[] quadrants = new int[4];
        for (int z = -256; z < 256; z++) {
            for (int x = -256; x < 256; x++) {
                total++;
                if (SubterraneanTrapLayout.rateQualified(seed, x, z)) {
                    raw++;
                }
                if (SubterraneanTrapLayout.chunkGate(seed, x, z)) {
                    int quadrant = (x >= 0 ? 1 : 0) + (z >= 0 ? 2 : 0);
                    quadrants[quadrant]++;
                }
            }
        }
        double density = raw / (double) total;
        assertEquals(1.0, density, "structural eligibility is the only encounter-rate gate");
        for (int count : quadrants) {
            assertEquals(total / 4, count, "every owner chunk in every quadrant reaches planning");
        }
    }

    @Test
    void rawGateRemainsStableAtIntegerChunkBoundaries() {
        int[][] boundaryChunks = {
                {Integer.MIN_VALUE, Integer.MIN_VALUE}, {Integer.MIN_VALUE, Integer.MAX_VALUE},
                {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE},
                {Integer.MIN_VALUE, 0}, {Integer.MAX_VALUE, 0}, {0, Integer.MIN_VALUE}, {0, Integer.MAX_VALUE}
        };
        for (long seed = 0; seed < 256; seed++) {
            for (int[] chunk : boundaryChunks) {
                assertEquals(SubterraneanTrapLayout.rateQualified(seed, chunk[0], chunk[1]),
                        SubterraneanTrapLayout.chunkGate(seed, chunk[0], chunk[1]),
                        "raw gate has no neighbour arithmetic at the int chunk boundary");
            }
        }
    }

    @Test
    void everyStructurallyEligibleOwnerChunkHasNoExtraRarityCoin() {
        assertEquals(1, SubterraneanTrapLayout.RAW_RATE_DENOMINATOR,
                "the final encounter rate is structural eligibility only, with no second rarity coin");
        for (int z = -64; z <= 64; z++) {
            for (int x = -64; x <= 64; x++) {
                assertTrue(SubterraneanTrapLayout.rateQualified(0x5EED_F00DL, x, z),
                        "every owner chunk reaches the structural-plan gate");
                assertTrue(SubterraneanTrapLayout.chunkGate(0x5EED_F00DL, x, z),
                        "no neighbour or chunk-border filter suppresses an eligible owner chunk");
            }
        }
    }

    private static void assertTransverseStationWidths(SubterraneanTrapLayout.Template template,
                                                       Set<SubterraneanTrapLayout.Cell> powder) {
        int narrowest = Integer.MAX_VALUE;
        int widest = Integer.MIN_VALUE;
        for (int station = 0; station < template.stations(); station++) {
            int currentStation = station;
            int width = (int) powder.stream().filter(cell -> longitudinal(template, cell) == currentStation).count();
            if (width > 0) {
                assertTrue(width >= 3,
                        "every powder-bearing station is at least three blocks wide across the snowfield");
                narrowest = Math.min(narrowest, width);
                widest = Math.max(widest, width);
            }
        }
        assertTrue(widest - narrowest >= 3,
                "powder station widths vary by at least three blocks across the snowfield");
    }

    /**
     * Counts one-cell insets on a powder bounding-box edge. A cell is meaningful only when it is not a corner,
     * has powder directly inward, and is bracketed by powder on both sides of the same edge. That makes the
     * absence a visible bay rather than an accidental clipped corner or a one-sided taper.
     */
    private static int countMeaningfulEdgeBays(Set<SubterraneanTrapLayout.Cell> cells) {
        int minX = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::z).max().orElseThrow();
        int bays = 0;
        for (int x = minX + 1; x < maxX; x++) {
            bays += isBracketedBay(cells, new SubterraneanTrapLayout.Cell(x, minZ),
                    new SubterraneanTrapLayout.Cell(x, minZ + 1),
                    new SubterraneanTrapLayout.Cell(x - 1, minZ), new SubterraneanTrapLayout.Cell(x + 1, minZ)) ? 1 : 0;
            bays += isBracketedBay(cells, new SubterraneanTrapLayout.Cell(x, maxZ),
                    new SubterraneanTrapLayout.Cell(x, maxZ - 1),
                    new SubterraneanTrapLayout.Cell(x - 1, maxZ), new SubterraneanTrapLayout.Cell(x + 1, maxZ)) ? 1 : 0;
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            bays += isBracketedBay(cells, new SubterraneanTrapLayout.Cell(minX, z),
                    new SubterraneanTrapLayout.Cell(minX + 1, z),
                    new SubterraneanTrapLayout.Cell(minX, z - 1), new SubterraneanTrapLayout.Cell(minX, z + 1)) ? 1 : 0;
            bays += isBracketedBay(cells, new SubterraneanTrapLayout.Cell(maxX, z),
                    new SubterraneanTrapLayout.Cell(maxX - 1, z),
                    new SubterraneanTrapLayout.Cell(maxX, z - 1), new SubterraneanTrapLayout.Cell(maxX, z + 1)) ? 1 : 0;
        }
        return bays;
    }

    private static boolean isBracketedBay(Set<SubterraneanTrapLayout.Cell> cells, SubterraneanTrapLayout.Cell bay,
                                          SubterraneanTrapLayout.Cell inward, SubterraneanTrapLayout.Cell before,
                                          SubterraneanTrapLayout.Cell after) {
        return !cells.contains(bay) && cells.contains(inward) && cells.contains(before) && cells.contains(after);
    }

    private static int powderBoundingBoxArea(Set<SubterraneanTrapLayout.Cell> cells) {
        int minX = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(SubterraneanTrapLayout.Cell::z).max().orElseThrow();
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    private static int longitudinal(SubterraneanTrapLayout.Template template, SubterraneanTrapLayout.Cell cell) {
        return template.axis() == SubterraneanTrapLayout.Axis.X ? cell.x() : cell.z();
    }

    private static void assertConnected(Set<SubterraneanTrapLayout.Cell> cells) {
        Set<SubterraneanTrapLayout.Cell> seen = new HashSet<>();
        ArrayDeque<SubterraneanTrapLayout.Cell> todo = new ArrayDeque<>();
        todo.add(cells.iterator().next());
        while (!todo.isEmpty()) {
            SubterraneanTrapLayout.Cell cell = todo.removeFirst();
            if (!seen.add(cell)) {
                continue;
            }
            for (SubterraneanTrapLayout.Cell neighbour : List.of(
                    new SubterraneanTrapLayout.Cell(cell.x() - 1, cell.z()),
                    new SubterraneanTrapLayout.Cell(cell.x() + 1, cell.z()),
                    new SubterraneanTrapLayout.Cell(cell.x(), cell.z() - 1),
                    new SubterraneanTrapLayout.Cell(cell.x(), cell.z() + 1))) {
                if (cells.contains(neighbour) && !seen.contains(neighbour)) {
                    todo.addLast(neighbour);
                }
            }
        }
        assertEquals(cells, seen, "powder cells are 4-connected");
    }
}
