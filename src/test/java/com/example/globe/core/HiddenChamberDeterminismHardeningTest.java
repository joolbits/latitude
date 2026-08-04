package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hardens the determinism claim in {@link HiddenChamberPlan}'s own class Javadoc ("the same inputs always
 * produce a byte-identical result... No {@code java.util.Random} is used anywhere and no roll is consumed
 * in sequence") two ways: (1) two {@link HiddenChamberPlan.TerrainProbe} implementations that answer
 * identically but are evaluated in a different internal order must still yield byte-identical plans, and
 * (2) a reflection sweep confirms the class carries no ordinary mutable static state that a future pass
 * could accidentally corrupt across calls.
 *
 * <h2>FINDING (reported, not fixed here -- frozen main source)</h2>
 * Four {@code private static final int[][]} fields -- {@code CARDINALS} (line 220),
 * {@code NEIGHBOURHOOD_8} (line 221), {@code ENVELOPE_FIT_SAMPLES} (line 225), and {@code SHELL_OFFSETS}
 * (line 2140) -- are raw Java arrays. A {@code final} reference to an array only fixes the BINDING; the
 * array's own contents remain mutable through that same reference (unlike {@code List.copyOf} or
 * {@code Set.of}, which return a genuinely unmodifiable view). None of the four is ever written to today
 * (confirmed: every appearance in the class is a read, always by index inside a loop), so this is not a
 * live bug, but it does not literally satisfy "every static field is primitive, String, enum, or an
 * immutable collection" the way {@code MOUTH_MASKS} (built via {@code List.copyOf}, verified below) does.
 * This test therefore excludes exactly those four names from the immutable-collection assertion, with this
 * comment as the record of why, rather than either silently passing a false claim or disabling the whole
 * check (which would stop guarding every OTHER static field against a future regression).
 */
class HiddenChamberDeterminismHardeningTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;

    /** See the FINDING above: raw arrays, never mutated in practice, but not literally immutable collections. */
    private static final Set<String> KNOWN_MUTABLE_CONTAINER_ARRAYS =
            Set.of("CARDINALS", "NEIGHBOURHOOD_8", "ENVELOPE_FIT_SAMPLES", "SHELL_OFFSETS");

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. Identical answers, different internal evaluation order                                             */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void aMemoizedAndANonMemoizedProbeThatAnswerIdenticallyProduceByteIdenticalPlans() {
        for (HiddenChamberPlan.Theme theme : HiddenChamberPlan.Theme.values()) {
            HiddenChamberPlan.PlanResult direct =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, new DirectProbe(), theme);
            HiddenChamberPlan.PlanResult memoized =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, new PrecomputedProbe(), theme);
            assertTrue(direct.isAccepted(), theme + " direct probe must accept: " + direct.detail());
            assertTrue(memoized.isAccepted(), theme + " memoized probe must accept: " + memoized.detail());
            assertEquals(direct.accepted().writes(), memoized.accepted().writes(),
                    theme + ": two probes answering identically in a different internal order must "
                            + "still produce a byte-identical write list");
            assertEquals(direct.accepted().theme(), memoized.accepted().theme());
            assertEquals(direct.accepted().variant(), memoized.accepted().variant());
            assertEquals(direct.accepted().direction(), memoized.accepted().direction());
            assertEquals(direct.accepted().exitTerminus(), memoized.accepted().exitTerminus());
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. No mutable static state                                                                            */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void everyStaticFieldIsFinalAndPrimitiveStringEnumOrAnImmutableValue() throws IllegalAccessException {
        for (Field field : HiddenChamberPlan.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    "static field " + field.getName() + " must be final");

            Class<?> type = field.getType();
            if (type.isPrimitive() || type == String.class || type.isEnum()) {
                continue;
            }
            if (Comparator.class.isAssignableFrom(type)) {
                // A stateless strategy object (built once via Comparator.comparing*/thenComparing* chains):
                // it carries no mutable field of its own and is never assigned to twice.
                continue;
            }
            if (type.isArray()) {
                assertTrue(KNOWN_MUTABLE_CONTAINER_ARRAYS.contains(field.getName()),
                        "new array-typed static field " + field.getName() + " found: arrays are never truly "
                                + "immutable through a final reference -- wrap it (List.copyOf/Set.of) or add "
                                + "it to the documented exception list only after confirming it is read-only");
                continue;
            }
            if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)
                    || Map.class.isAssignableFrom(type)) {
                Object value = field.get(null);
                assertTrue(value != null, field.getName() + " must not be null");
                assertThrows(UnsupportedOperationException.class, () -> mutate(value),
                        field.getName() + " must be an unmodifiable collection (List.copyOf/Set.of/Map.copyOf), "
                                + "not a plain mutable one exposed through a final reference");
                continue;
            }
            throw new AssertionError("static field " + field.getName() + " has an unrecognised type "
                    + type + " -- extend this test to classify it as safe or flag it as mutable state");
        }
    }

    @SuppressWarnings("unchecked")
    private static void mutate(Object collection) {
        if (collection instanceof List<?> list) {
            ((List<Object>) list).add(new Object());
        } else if (collection instanceof Set<?> set) {
            ((Set<Object>) set).add(new Object());
        } else if (collection instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).put(new Object(), new Object());
        } else {
            throw new IllegalArgumentException("unexpected collection type " + collection.getClass());
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Fixtures                                                                                              */
    /* ---------------------------------------------------------------------------------------------------- */

    private static final int MIN = -30;
    private static final int MAX = 45;
    private static final int HEADROOM = 6;
    private static final int SURROUND_FLOOR_Y = 34;
    private static final int PLATEAU_FLOOR_Y = 40;
    private static final int PLATEAU_MIN = 4;
    private static final int PLATEAU_MAX = 11;

    private static boolean inPlateau(int x, int z) {
        return x >= PLATEAU_MIN && x <= PLATEAU_MAX && z >= PLATEAU_MIN && z <= PLATEAU_MAX;
    }

    private static boolean inBounds(int x, int z) {
        return x >= MIN && x <= MAX && z >= MIN && z <= MAX;
    }

    private static HiddenChamberPlan.ColumnInfo columnFor(int localX, int localZ) {
        if (!inBounds(localX, localZ)) {
            return null;
        }
        boolean plateau = inPlateau(localX, localZ);
        return new HiddenChamberPlan.ColumnInfo(plateau ? PLATEAU_FLOOR_Y : SURROUND_FLOOR_Y, plateau, true, HEADROOM);
    }

    private static HiddenChamberPlan.CellKind cellFor(int localX, int y, int localZ) {
        if (!inBounds(localX, localZ)) {
            return HiddenChamberPlan.CellKind.UNREADABLE;
        }
        if (y <= 0) {
            return HiddenChamberPlan.CellKind.BEDROCK;
        }
        int floorY = inPlateau(localX, localZ) ? PLATEAU_FLOOR_Y : SURROUND_FLOOR_Y;
        if (y <= floorY) {
            return HiddenChamberPlan.CellKind.SOLID_SAFE;
        }
        return y <= floorY + HEADROOM ? HiddenChamberPlan.CellKind.AIR : HiddenChamberPlan.CellKind.SOLID_SAFE;
    }

    /** Recomputes every answer fresh, on demand, in whatever order {@code HiddenChamberPlan} happens to ask. */
    private static final class DirectProbe implements HiddenChamberPlan.TerrainProbe {
        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            return columnFor(localX, localZ);
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            return cellFor(localX, y, localZ);
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            return true;
        }
    }

    /**
     * Answers the SAME underlying shape as {@link DirectProbe}, but every column and cell answer within a
     * generous bounding box is pre-computed and cached at CONSTRUCTION time, walking the box in the
     * OPPOSITE order (Z descending then X descending, versus any natural ascending scan) from how a naive
     * cache-builder would iterate it. Once built, this probe only ever reads its own maps; it never
     * recomputes.
     */
    private static final class PrecomputedProbe implements HiddenChamberPlan.TerrainProbe {
        private final Map<Long, HiddenChamberPlan.ColumnInfo> columns = new HashMap<>();
        private final Map<Long, HiddenChamberPlan.CellKind> cells = new HashMap<>();

        PrecomputedProbe() {
            for (int x = MAX; x >= MIN; x--) {
                for (int z = MAX; z >= MIN; z--) {
                    HiddenChamberPlan.ColumnInfo info = columnFor(x, z);
                    if (info != null) {
                        columns.put(columnKey(x, z), info);
                    }
                    for (int y = 90; y >= -5; y--) {
                        cells.put(cellKey(x, y, z), cellFor(x, y, z));
                    }
                }
            }
        }

        private static long columnKey(int x, int z) {
            return ((long) (x + 4096) << 32) | (z + 4096);
        }

        private static long cellKey(int x, int y, int z) {
            return ((long) (x + 4096) << 42) | ((long) (y + 4096) << 21) | (z + 4096);
        }

        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            return columns.get(columnKey(localX, localZ));
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            HiddenChamberPlan.CellKind cached = cells.get(cellKey(localX, y, localZ));
            return cached == null ? HiddenChamberPlan.CellKind.UNREADABLE : cached;
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            return true;
        }
    }
}
