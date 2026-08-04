package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end mask-catalog coverage for {@link HiddenChamberPlan#mouthMaskCatalog()}: every entry is run
 * through the real {@link HiddenChamberPlan#plan} pipeline against a terrain whose ONLY snowy cells are
 * exactly that mask's own footprint at one fixed anchor, so the mouth actually accepted can be checked
 * against the mask that was meant to produce it.
 *
 * <p>{@code HiddenChamberPlanTest.everyMouthMaskIsIrregularAndTwoToFourCells} already pins the catalog's
 * own shape law (2-4 cells, never a line, never a two-by-two square); this class does not repeat it.
 *
 * <h2>A structural note the isolation exposed</h2>
 * The catalog's 20 mask+rotation entries are not all independently selectable this way. {@code L-3} and
 * {@code diagonal-2} are the two smallest shapes; several larger shapes ({@code T-4}, {@code S-4},
 * {@code zigzag-3}, and two of {@code skew-4}'s four rotations) literally CONTAIN one of those two smaller
 * shapes as a same-anchor sub-pattern of their own required cells. Candidate ranking
 * ({@code HiddenChamberPlan.CANDIDATE_ORDER}, lines ~2155-2161) scores by achievable drop and spread, both
 * of which a subset of cells can only match or beat a superset on (never lose to it), and ties at the same
 * anchor fall through to the mask's own name in plain string order. Since {@code "L-3" < "diagonal-2"} and
 * both sort before every other name, whenever one of these larger shapes is eligible its embedded smaller
 * shape is eligible too, ties or wins on score, and wins the name tie-break -- so a terrain that makes
 * {@code T-4} (for example) eligible can never make it the WINNING candidate; the embedded {@code L-3}
 * always is instead. This was verified empirically (a standalone probe against the real class, zero
 * Minecraft dependencies) before writing these tests: exactly 8 of the 20 entries survive isolation with
 * their own exact footprint (both {@code diagonal-2} rotations, all four {@code L-3} rotations, and two of
 * {@code skew-4}'s four rotations); the other 12 always resolve to their embedded {@code L-3} or
 * {@code diagonal-2} sub-shape. This is not a defect -- {@code plan()} never claims every catalog entry is
 * independently reachable from an arbitrary terrain, and the deterministic, safe tie-break it does use
 * (simpler mouth wins) is itself desirable -- but it does mean the exact-match proof below is necessarily
 * partial, and the full-catalog sweep after it verifies the DETERMINISTIC, catalog-consistent behaviour of
 * every entry instead of an exact match.
 */
class HiddenChamberMaskCatalogPlanTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;
    private static final int ANCHOR_X = 5;
    private static final int ANCHOR_Z = 5;

    /**
     * Empirically confirmed mask+rotation pairs whose isolated footprint is never a same-anchor subset of
     * another catalog shape, so the planner's own ranking has no alternative candidate to prefer instead.
     */
    private static final Set<String> DIRECTLY_ISOLABLE = Set.of(
            "diagonal-2:0", "diagonal-2:1",
            "L-3:0", "L-3:1", "L-3:2", "L-3:3",
            "skew-4:1", "skew-4:3");

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. Exact match for every directly isolable mask + rotation                                            */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void everyDirectlyIsolableMaskAndRotationProducesExactlyItsOwnRotatedFootprint() {
        int checked = 0;
        for (HiddenChamberPlan.MouthMask mask : HiddenChamberPlan.mouthMaskCatalog()) {
            if (!DIRECTLY_ISOLABLE.contains(mask.name() + ":" + mask.rotation())) {
                continue;
            }
            checked++;
            IsolatingTerrain terrain = new IsolatingTerrain(ANCHOR_X, ANCHOR_Z, mask);
            HiddenChamberPlan.PlanResult result =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, null);
            assertTrue(result.isAccepted(),
                    mask.name() + " rot=" + mask.rotation() + " must accept: " + result.detail());

            Set<String> expected = new LinkedHashSet<>();
            for (HiddenChamberPlan.MaskOffset offset : mask.offsets()) {
                expected.add((ANCHOR_X + offset.dx()) + ":" + (ANCHOR_Z + offset.dz()));
            }
            Set<String> actual = new LinkedHashSet<>();
            for (HiddenChamberPlan.Cell cell : result.accepted().mouthCells()) {
                actual.add(cell.x() + ":" + cell.z());
            }
            assertEquals(expected, actual,
                    mask.name() + " rot=" + mask.rotation() + " mouth cells must exactly match its own mask");
        }
        assertEquals(DIRECTLY_ISOLABLE.size(), checked,
                "every catalogued directly-isolable entry must actually be present in mouthMaskCatalog()");
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. Full-catalog sweep: every entry resolves to SOME catalog-consistent, contained, accepted mouth     */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void everyCatalogMaskAndRotationResolvesToAnAcceptedCatalogConsistentMouth() {
        List<HiddenChamberPlan.MouthMask> catalog = HiddenChamberPlan.mouthMaskCatalog();
        assertTrue(catalog.size() >= 8, "the catalog must carry at least the directly-isolable entries");
        for (HiddenChamberPlan.MouthMask mask : catalog) {
            IsolatingTerrain terrain = new IsolatingTerrain(ANCHOR_X, ANCHOR_Z, mask);
            HiddenChamberPlan.PlanResult result =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, null);
            assertTrue(result.isAccepted(),
                    mask.name() + " rot=" + mask.rotation() + " isolation terrain must still accept: "
                            + result.detail());

            Set<String> allowedCells = new LinkedHashSet<>();
            for (HiddenChamberPlan.MaskOffset offset : mask.offsets()) {
                allowedCells.add((ANCHOR_X + offset.dx()) + ":" + (ANCHOR_Z + offset.dz()));
            }
            List<HiddenChamberPlan.Cell> mouth = result.accepted().mouthCells();
            for (HiddenChamberPlan.Cell cell : mouth) {
                assertTrue(allowedCells.contains(cell.x() + ":" + cell.z()),
                        mask.name() + " rot=" + mask.rotation() + " produced a mouth cell " + cell
                                + " outside the only snowy cells the terrain ever offered");
            }

            // The winning shape, re-normalised to zero-based offsets, must itself be a genuine catalog
            // entry: whichever mask actually won the tie-break, it was never a malformed or partial shape.
            Set<HiddenChamberPlan.MaskOffset> normalised = normalise(mouth);
            boolean matchesSomeCatalogEntry = false;
            for (HiddenChamberPlan.MouthMask candidate : catalog) {
                if (new LinkedHashSet<>(candidate.offsets()).equals(normalised)) {
                    matchesSomeCatalogEntry = true;
                    break;
                }
            }
            assertTrue(matchesSomeCatalogEntry,
                    mask.name() + " rot=" + mask.rotation() + " resolved to a mouth shape " + normalised
                            + " that is not any catalogued mask");
        }
    }

    private static Set<HiddenChamberPlan.MaskOffset> normalise(List<HiddenChamberPlan.Cell> mouth) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (HiddenChamberPlan.Cell cell : mouth) {
            minX = Math.min(minX, cell.x());
            minZ = Math.min(minZ, cell.z());
        }
        Set<HiddenChamberPlan.MaskOffset> offsets = new LinkedHashSet<>();
        for (HiddenChamberPlan.Cell cell : mouth) {
            offsets.add(new HiddenChamberPlan.MaskOffset(cell.x() - minX, cell.z() - minZ));
        }
        return offsets;
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Fixture                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * Snowy ONLY at the given mask's own footprint (translated to the anchor); everywhere else in the
     * owner-local window is roofed, correctly-banded floor but NOT snowy, so the exit-terminus search still
     * finds a home while no OTHER anchor position can ever assemble a fully-snowy mouth of its own.
     */
    private static final class IsolatingTerrain implements HiddenChamberPlan.TerrainProbe {
        private static final int MIN = -30;
        private static final int MAX = 45;
        private static final int HEADROOM = 6;
        private static final int FLOOR_Y = 40;

        private final Set<Long> snowyColumns = new LinkedHashSet<>();

        IsolatingTerrain(int anchorX, int anchorZ, HiddenChamberPlan.MouthMask mask) {
            for (HiddenChamberPlan.MaskOffset offset : mask.offsets()) {
                snowyColumns.add(key(anchorX + offset.dx(), anchorZ + offset.dz()));
            }
        }

        private static long key(int x, int z) {
            return ((long) (x + 4096) << 32) | (z + 4096);
        }

        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            if (localX < MIN || localX > MAX || localZ < MIN || localZ > MAX) {
                return null;
            }
            boolean snowy = snowyColumns.contains(key(localX, localZ));
            return new HiddenChamberPlan.ColumnInfo(FLOOR_Y, snowy, true, HEADROOM);
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            if (localX < MIN || localX > MAX || localZ < MIN || localZ > MAX) {
                return HiddenChamberPlan.CellKind.UNREADABLE;
            }
            if (y <= 0) {
                return HiddenChamberPlan.CellKind.BEDROCK;
            }
            if (y <= FLOOR_Y) {
                return HiddenChamberPlan.CellKind.SOLID_SAFE;
            }
            return y <= FLOOR_Y + HEADROOM ? HiddenChamberPlan.CellKind.AIR : HiddenChamberPlan.CellKind.SOLID_SAFE;
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            return true;
        }
    }
}
