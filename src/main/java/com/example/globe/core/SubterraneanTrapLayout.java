package com.example.globe.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure-Java, local-coordinate layouts for the broad snowfield form of a subterranean trap.
 *
 * <p>A template describes every implied station in a small 16 by 16 chunk-local patch. Its powder cap is
 * deliberately an irregular, connected ribbon, rather than a compact rectangle: solid bookends stop the
 * run at both approaches and the solid lateral cells keep a visible bank on both sides of every powder
 * station. This class only supplies deterministic geometry and selection; world generation decides whether
 * and how to materialize those cells.
 */
public final class SubterraneanTrapLayout {

    /** The local chunk envelope accepted by {@link #placement(long, int, int)}. */
    public static final int LOCAL_SIZE = 16;
    /** Structural eligibility is the complete occurrence gate; there is no second rarity coin. */
    public static final int RAW_RATE_DENOMINATOR = 1;

    /** Chunk-local integer cell. */
    public record Cell(int x, int z) {
    }

    /** The longitudinal direction of a template's stations. */
    public enum Axis {
        X,
        Z
    }

    /**
     * Immutable local template. {@code stations} includes both solid bookends; {@code width} is transverse.
     * The union of {@code powder} and {@code solid} is the whole implied rectangular patch, without overlap.
     */
    public record Template(Axis axis, int stations, int width, List<Cell> powder, List<Cell> solid) {
        public Template {
            Objects.requireNonNull(axis, "axis");
            powder = List.copyOf(powder);
            solid = List.copyOf(solid);
            if (stations < 1 || width < 1 || stations > LOCAL_SIZE - 2 || width > LOCAL_SIZE - 2) {
                throw new IllegalArgumentException("template dimensions must fit inside the one-cell local ring");
            }
            if (new HashSet<>(powder).size() != powder.size() || new HashSet<>(solid).size() != solid.size()) {
                throw new IllegalArgumentException("template cells must be unique within each material");
            }
            Set<Cell> overlap = new HashSet<>(powder);
            overlap.retainAll(solid);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException("powder and solid cells must not overlap");
            }
            int xExtent = axis == Axis.X ? stations : width;
            int zExtent = axis == Axis.X ? width : stations;
            if (powder.stream().anyMatch(cell -> !inBounds(cell, xExtent, zExtent))
                    || solid.stream().anyMatch(cell -> !inBounds(cell, xExtent, zExtent))) {
                throw new IllegalArgumentException("template cells must lie inside its dimensions");
            }
            if (powder.size() + solid.size() != stations * width) {
                throw new IllegalArgumentException("template must classify every implied cell");
            }
        }

        /** All implied cells in deterministic coordinate order. */
        public List<Cell> implied() {
            List<Cell> all = new ArrayList<>(powder.size() + solid.size());
            all.addAll(powder);
            all.addAll(solid);
            all.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z));
            return List.copyOf(all);
        }

        /** Rounded whole-percent fraction of longitudinal stations carrying powder. */
        public int powderStationPercent() {
            int powderStations = axis == Axis.X
                    ? powder.stream().mapToInt(Cell::x).distinct().toArray().length
                    : powder.stream().mapToInt(Cell::z).distinct().toArray().length;
            return (int) Math.round(100.0 * powderStations / stations);
        }
    }

    /** A selected, centered template translated into the 16 by 16 local envelope. */
    public record Placement(Template template, int offsetX, int offsetZ) {
        public Placement {
            Objects.requireNonNull(template, "template");
            int xExtent = template.axis() == Axis.X ? template.stations() : template.width();
            int zExtent = template.axis() == Axis.X ? template.width() : template.stations();
            if (offsetX < 1 || offsetZ < 1 || offsetX + xExtent > LOCAL_SIZE - 1
                    || offsetZ + zExtent > LOCAL_SIZE - 1) {
                throw new IllegalArgumentException("placement must preserve the one-cell local ring");
            }
        }

        public List<Cell> powder() {
            return translate(template.powder());
        }

        public List<Cell> solid() {
            return translate(template.solid());
        }

        public List<Cell> implied() {
            return translate(template.implied());
        }

        private List<Cell> translate(List<Cell> cells) {
            return cells.stream().map(cell -> new Cell(cell.x() + offsetX, cell.z() + offsetZ)).toList();
        }
    }

    private static final long X_SALT = 0x9E3779B97F4A7C15L;
    private static final long Z_SALT = 0xD1B54A32D192ED03L;
    private static final long TEMPLATE_SALT = 0x94D049BB133111EBL;
    private static final long OFFSET_X_SALT = 0xBF58476D1CE4E5B9L;
    private static final long OFFSET_Z_SALT = 0x632BE59BD9B4E019L;

    private static final List<Template> CATALOG = List.of(
            horizontalTemplate(14, new int[][]{
                    {1, 3}, {0, 5}, {1, 3}, {0, 5}, {2, 4}, {0, 5}, {1, 3}, {0, 5}
            }),
            verticalOf(horizontalTemplate(14, new int[][]{
                    {1, 3}, {0, 5}, {1, 3}, {0, 5}, {2, 4}, {0, 5}, {1, 3}, {0, 5}
            })),
            horizontalTemplate(12, new int[][]{
                    {1, 3}, {0, 5}, {1, 3}, {0, 5}, {2, 4}, {0, 5}
            }),
            verticalOf(horizontalTemplate(12, new int[][]{
                    {1, 3}, {0, 5}, {1, 3}, {0, 5}, {2, 4}, {0, 5}
            }))
    );

    private SubterraneanTrapLayout() {
    }

    /** The two sizes (preferred 14 by 7, fallback 12 by 7) in both axes. */
    public static List<Template> catalog() {
        return CATALOG;
    }

    /**
     * Stable 64-bit chunk priority. It deliberately uses normal two's-complement overflow, never
     * {@code Math.abs}, so {@link Long#MIN_VALUE} and negative chunk coordinates remain valid inputs.
     */
    public static long priority(long seed, int chunkX, int chunkZ) {
        long folded = seed ^ (X_SALT * (long) chunkX) ^ (Z_SALT * (long) chunkZ);
        return mix64(folded);
    }

    /** Every invoked owner chunk reaches structural planning; geometry and safety decide the final incidence. */
    public static boolean rateQualified(long seed, int chunkX, int chunkZ) {
        return Long.remainderUnsigned(priority(seed, chunkX, chunkZ), RAW_RATE_DENOMINATOR) == 0;
    }

    /** The stable raw gate. Nearby owner chunks may both be eligible because their plans remain chunk-local. */
    public static boolean chunkGate(long seed, int chunkX, int chunkZ) {
        return rateQualified(seed, chunkX, chunkZ);
    }

    /** Hash-rotated template order for a chunk. The first item is the selected shape. */
    public static List<Template> orderedTemplates(long seed, int chunkX, int chunkZ) {
        int first = (int) Long.remainderUnsigned(mix64(priority(seed, chunkX, chunkZ) ^ TEMPLATE_SALT),
                CATALOG.size());
        List<Template> ordered = new ArrayList<>(CATALOG.size());
        for (int index = 0; index < CATALOG.size(); index++) {
            ordered.add(CATALOG.get((first + index) % CATALOG.size()));
        }
        return List.copyOf(ordered);
    }

    /**
     * Every ring-preserving template offset in deterministic seed/chunk order. This deliberately searches the
     * full local placement catalogue before the sparse gate is consulted, so normal one-block snow relief can
     * find a natural fit instead of requiring one artificial centred platform.
     */
    public static List<Placement> placements(long seed, int chunkX, int chunkZ) {
        List<Placement> placements = new ArrayList<>();
        Set<Placement> unique = new HashSet<>();
        int templateIndex = 0;
        for (Template template : orderedTemplates(seed, chunkX, chunkZ)) {
            int xExtent = template.axis() == Axis.X ? template.stations() : template.width();
            int zExtent = template.axis() == Axis.X ? template.width() : template.stations();
            List<Offset> offsets = new ArrayList<>();
            for (int offsetX = 1; offsetX <= LOCAL_SIZE - xExtent - 1; offsetX++) {
                for (int offsetZ = 1; offsetZ <= LOCAL_SIZE - zExtent - 1; offsetZ++) {
                    offsets.add(new Offset(offsetX, offsetZ));
                }
            }
            final int orderIndex = templateIndex++;
            offsets.sort(Comparator.<Offset>comparingLong(offset -> mix64(priority(seed, chunkX, chunkZ)
                    ^ TEMPLATE_SALT * (orderIndex + 1L) ^ OFFSET_X_SALT * offset.x() ^ OFFSET_Z_SALT * offset.z()))
                    .thenComparingInt(Offset::x).thenComparingInt(Offset::z));
            for (Offset offset : offsets) {
                Placement placement = new Placement(template, offset.x, offset.z);
                if (unique.add(placement)) {
                    placements.add(placement);
                }
            }
        }
        return List.copyOf(placements);
    }

    /** Convenience accessor for the first deterministic candidate from {@link #placements(long, int, int)}. */
    public static Placement placement(long seed, int chunkX, int chunkZ) {
        return placements(seed, chunkX, chunkZ).getFirst();
    }

    private static boolean inBounds(Cell cell, int xExtent, int zExtent) {
        return cell.x() >= 0 && cell.x() < xExtent && cell.z() >= 0 && cell.z() < zExtent;
    }

    private record Offset(int x, int z) {
    }

    private static Template horizontalTemplate(int stations, int[][] powderSpans) {
        int width = 7;
        int firstPowderStation = 3;
        List<Cell> powder = new ArrayList<>();
        for (int station = 0; station < powderSpans.length; station++) {
            int[] span = powderSpans[station];
            for (int lateral = span[0]; lateral <= span[1]; lateral++) {
                powder.add(new Cell(firstPowderStation + station, lateral));
            }
        }
        List<Cell> solid = new ArrayList<>();
        for (int x = 0; x < stations; x++) {
            for (int z = 0; z < width; z++) {
                Cell cell = new Cell(x, z);
                if (!powder.contains(cell)) {
                    solid.add(cell);
                }
            }
        }
        return new Template(Axis.X, stations, width, powder, solid);
    }

    private static Template verticalOf(Template horizontal) {
        return new Template(Axis.Z, horizontal.stations(), horizontal.width(),
                horizontal.powder().stream().map(cell -> new Cell(cell.z(), cell.x())).toList(),
                horizontal.solid().stream().map(cell -> new Cell(cell.z(), cell.x())).toList());
    }

    private static long mix64(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
