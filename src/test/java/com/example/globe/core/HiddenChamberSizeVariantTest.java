package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves both {@link HiddenChamberPlan.Variant} values are independently reachable, and that the CARVED
 * VOID measured back off the write list -- not {@code buildChamber}'s own internal fill-percent metadata --
 * lands inside the authored bands.
 *
 * <p>Variant selection turns out to hinge on the achieved DROP, not on terrain span: the canonical volume's
 * fixed {@code usableX}/{@code usableZ} bounds ({@code HiddenChamberPlan} lines ~908-909) already exceed
 * {@code CATHEDRAL_SPAN_X_MIN}/{@code Z_MIN} for any terrain, so {@code pickVariant} only falls back to
 * {@code COMPACT} when {@code ceilingCap(mouthFloorY, landingY) < CATHEDRAL_HEIGHT_MIN + CHAMBER_MIN_DISTINCT_HEIGHTS
 * - 1}. {@link HiddenChamberTerrainFixtures#compactForcing()} exploits exactly that: a legal-minimum mouth
 * floor plus a base-layer-only width hazard (armed only at the {@code landingY} rows a shallow mouth's
 * cathedral attempts would use) squeezes every accepted plan down to {@code COMPACT} while leaving the
 * corridor/exit search untouched. {@link HiddenChamberTerrainFixtures#roomy()} needs no such trick: it
 * naturally lets {@code ICE_CATHEDRAL}/{@code FRIGID_LAKE} reach {@code CATHEDRAL} at the deepest legal drop.
 */
class HiddenChamberSizeVariantTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;

    /**
     * Every role that can be part of the chamber's own two-dimensional footprint (carved void, ice pillars,
     * lake water/bed) -- i.e. the roles a column can carry and still count as "this column belongs to the
     * chamber", regardless of which specific role won the write at any one cell within it. Shaft, cushion,
     * corridor, mouth and wall-seal roles are deliberately excluded: they measure a DIFFERENT structure.
     */
    private static final Set<HiddenChamberPlan.CellRole> CHAMBER_FOOTPRINT_ROLES = Set.of(
            HiddenChamberPlan.CellRole.CHAMBER_CLEAR,
            HiddenChamberPlan.CellRole.RECESS_CLEAR,
            HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED,
            HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE,
            HiddenChamberPlan.CellRole.ICICLE_ICE,
            HiddenChamberPlan.CellRole.FOSSIL_BONE,
            HiddenChamberPlan.CellRole.LAKE_WATER,
            HiddenChamberPlan.CellRole.LAKE_BED,
            HiddenChamberPlan.CellRole.SHORE_ICE,
            HiddenChamberPlan.CellRole.FLOE_ICE,
            HiddenChamberPlan.CellRole.FROST_MOTE,
            HiddenChamberPlan.CellRole.LANTERN,
            HiddenChamberPlan.CellRole.CACHE_CHEST,
            HiddenChamberPlan.CellRole.DEBRIS_WOOD,
            HiddenChamberPlan.CellRole.DEBRIS_BONE);

    @Test
    void aTerrainThatOnlyFitsCompactChoosesCompactWithSpanAndHeightInsideItsBand() {
        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.compactForcing();
        HiddenChamberPlan.PlanResult result =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertTrue(result.isAccepted(), "the compact-forcing terrain must still accept: " + result.detail());
        HiddenChamberPlan.Plan plan = result.accepted();
        assertEquals(HiddenChamberPlan.Variant.COMPACT, plan.variant());

        ChamberVoid voidShape = measureVoid(plan);
        assertTrue(voidShape.spanX >= HiddenChamberPlan.COMPACT_SPAN_X_MIN
                        && voidShape.spanX <= HiddenChamberPlan.COMPACT_SPAN_X_MAX,
                "compact span X " + voidShape.spanX);
        assertTrue(voidShape.spanZ >= HiddenChamberPlan.COMPACT_SPAN_Z_MIN
                        && voidShape.spanZ <= HiddenChamberPlan.COMPACT_SPAN_Z_MAX,
                "compact span Z " + voidShape.spanZ);
        for (int height : voidShape.distinctHeights) {
            assertTrue(height >= HiddenChamberPlan.COMPACT_HEIGHT_MIN && height <= HiddenChamberPlan.COMPACT_HEIGHT_MAX,
                    "compact per-column height " + height + " outside its band " + voidShape.distinctHeights);
        }
        assertBoundingFillAndHeights(voidShape);
    }

    @Test
    void aRoomyTerrainAdmittingCathedralThemesChoosesCathedralWithSpanAndHeightInsideItsBand() {
        for (HiddenChamberPlan.Theme theme
                : new HiddenChamberPlan.Theme[] {HiddenChamberPlan.Theme.ICE_CATHEDRAL, HiddenChamberPlan.Theme.FRIGID_LAKE}) {
            HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.roomy();
            HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, theme);
            assertTrue(result.isAccepted(), theme + ": the roomy terrain must accept: " + result.detail());
            HiddenChamberPlan.Plan plan = result.accepted();
            assertEquals(HiddenChamberPlan.Variant.CATHEDRAL, plan.variant(),
                    theme + " must naturally reach CATHEDRAL on a roomy terrain");

            ChamberVoid voidShape = measureVoid(plan);
            assertTrue(voidShape.spanX >= HiddenChamberPlan.CATHEDRAL_SPAN_X_MIN
                            && voidShape.spanX <= HiddenChamberPlan.CATHEDRAL_SPAN_X_MAX,
                    theme + " cathedral span X " + voidShape.spanX);
            assertTrue(voidShape.spanZ >= HiddenChamberPlan.CATHEDRAL_SPAN_Z_MIN
                            && voidShape.spanZ <= HiddenChamberPlan.CATHEDRAL_SPAN_Z_MAX,
                    theme + " cathedral span Z " + voidShape.spanZ);
            for (int height : voidShape.distinctHeights) {
                assertTrue(height >= HiddenChamberPlan.CATHEDRAL_HEIGHT_MIN
                                && height <= HiddenChamberPlan.CATHEDRAL_HEIGHT_MAX,
                        theme + " cathedral per-column height " + height + " outside its band "
                                + voidShape.distinctHeights);
            }
            assertBoundingFillAndHeights(voidShape);
        }
    }

    private static void assertBoundingFillAndHeights(ChamberVoid voidShape) {
        assertTrue(voidShape.fillPercent >= HiddenChamberPlan.CHAMBER_FILL_MIN_PERCENT
                        && voidShape.fillPercent <= HiddenChamberPlan.CHAMBER_FILL_MAX_PERCENT,
                "bounding-fill " + voidShape.fillPercent + "% measured from the emitted writes, not metadata");
        assertTrue(voidShape.distinctHeights.size() >= HiddenChamberPlan.CHAMBER_MIN_DISTINCT_HEIGHTS,
                "distinct clear heights " + voidShape.distinctHeights + " measured from the emitted writes");
    }

    /** The chamber's own two-dimensional footprint and per-column heights, reconstructed from writes alone. */
    private record ChamberVoid(int spanX, int spanZ, int fillPercent, Set<Integer> distinctHeights) {
    }

    private static ChamberVoid measureVoid(HiddenChamberPlan.Plan plan) {
        Map<Long, Integer> maxYPerColumn = new HashMap<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (!CHAMBER_FOOTPRINT_ROLES.contains(cell.role())) {
                continue;
            }
            long key = ((long) cell.x() << 32) | (cell.z() & 0xFFFFFFFFL);
            maxYPerColumn.merge(key, cell.y(), Math::max);
        }
        assertTrue(!maxYPerColumn.isEmpty(), "an accepted plan must carve at least one chamber column");
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : maxYPerColumn.keySet()) {
            int x = (int) (key >> 32);
            int z = (int) (key & 0xFFFFFFFFL);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int area = spanX * spanZ;
        int fillPercent = maxYPerColumn.size() * 100 / area;
        Set<Integer> distinctHeights = new TreeSet<>();
        for (int maxYAt : maxYPerColumn.values()) {
            distinctHeights.add(maxYAt - plan.landingY() + 1);
        }
        return new ChamberVoid(spanX, spanZ, fillPercent, distinctHeights);
    }
}
