package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chamber never builds where step 10's {@code globe:magma_quench_sweep} would scar it.
 *
 * <h2>What this class exists to stop happening again</h2>
 * The quench runs at the END of TOP_LAYER_MODIFICATION -- after this feature -- and it transforms the 3x3x3
 * shell of every magma block it finds in the glacial biomes: a FLOODED magma (water anywhere in its shell)
 * turns every ice-family and water cell of that shell to OBSIDIAN, and a DRY ice-touching magma MELTS its
 * face-adjacent ice to AIR. Both readings are blind to who authored the ice or the water. A chamber built
 * one cell from a magma block therefore ships with an obsidian bite taken out of its frigid lake, or with a
 * hole punched straight through the packed-ice seal that was holding the natural cave out.
 *
 * <p>The planner cannot un-run the quench, so it declines the chunk instead: any {@link
 * HiddenChamberPlan.CellRole#LAKE_WATER} cell or ice-family authored cell with an unsafe solid on a face is
 * an {@link HiddenChamberPlan.Rejection#UNSAFE_VOLUME}. The probe's {@link HiddenChamberPlan.CellKind} cannot
 * tell magma from ore, so the law is deliberately conservative: it treats every {@link
 * HiddenChamberPlan.CellKind#SOLID_UNSAFE} neighbour as if it were magma. Both are blocks the encounter is
 * already forbidden to cut into, so the loss is a handful of chunks near an ore vein.
 */
class HiddenChamberQuenchScarLawTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;

    /**
     * The roles the quench's {@code isIceFamily} sees as ice: packed ice, blue ice, ice and snow BLOCK.
     * Mirrored here from the world layer's role-to-block map so this test names the same set the planner does.
     */
    private static final Set<HiddenChamberPlan.CellRole> QUENCHABLE_ICE = EnumSet.of(
            HiddenChamberPlan.CellRole.CUSHION_BASE,
            HiddenChamberPlan.CellRole.SHELF_FIRM,
            HiddenChamberPlan.CellRole.CORRIDOR_FLOOR,
            HiddenChamberPlan.CellRole.LEDGE_FIRM,
            HiddenChamberPlan.CellRole.WALL_SEAL,
            HiddenChamberPlan.CellRole.COLUMN_ICE_PACKED,
            HiddenChamberPlan.CellRole.COLUMN_ICE_BLUE,
            HiddenChamberPlan.CellRole.LAKE_BED,
            HiddenChamberPlan.CellRole.SHORE_ICE,
            HiddenChamberPlan.CellRole.FLOE_ICE);

    private static final int[][] FACES = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    @Test
    void magmaOneCellOutsideThePlannedLakeRejectsTheChunk() {
        HiddenChamberPlan.PlanResult reference = HiddenChamberPlan.plan(
                SEED, CHUNK_X, CHUNK_Z, HiddenChamberTerrainFixtures.narrow(),
                HiddenChamberPlan.Theme.FRIGID_LAKE);
        assertTrue(reference.isAccepted(), "the reference terrain must accept: " + reference.detail());

        List<int[]> exposed = exposedFaces(reference.accepted(),
                EnumSet.of(HiddenChamberPlan.CellRole.LAKE_WATER));
        assertFalse(exposed.isEmpty(), "a frigid lake always has open water with an unauthored face");

        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.narrow();
        int[] magma = exposed.get(0);
        terrain.hazardAt(magma[0], magma[1], magma[2], HiddenChamberPlan.CellKind.SOLID_UNSAFE);

        HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(
                SEED, CHUNK_X, CHUNK_Z, terrain, HiddenChamberPlan.Theme.FRIGID_LAKE);
        assertFalse(result.isAccepted(),
                "magma at (" + magma[0] + "," + magma[1] + "," + magma[2] + ") sits on a face of the planned "
                        + "lake: step 10 would quench that water to obsidian, so the chunk must be declined");
        assertEquals(HiddenChamberPlan.Rejection.UNSAFE_VOLUME, result.rejection(), "" + result.detail());
    }

    @Test
    void magmaOneCellOutsideTheChambersIceSealRejectsTheChunk() {
        HiddenChamberPlan.PlanResult reference = HiddenChamberPlan.plan(
                SEED, CHUNK_X, CHUNK_Z, HiddenChamberTerrainFixtures.narrow(),
                HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertTrue(reference.isAccepted(), "the reference terrain must accept: " + reference.detail());

        List<int[]> exposed = exposedFaces(reference.accepted(), QUENCHABLE_ICE);
        assertFalse(exposed.isEmpty(), "an accepted plan always has ice with an unauthored face");

        HiddenChamberTerrainFixtures.Mutable terrain = HiddenChamberTerrainFixtures.narrow();
        int[] magma = exposed.get(0);
        terrain.hazardAt(magma[0], magma[1], magma[2], HiddenChamberPlan.CellKind.SOLID_UNSAFE);

        HiddenChamberPlan.PlanResult result = HiddenChamberPlan.plan(
                SEED, CHUNK_X, CHUNK_Z, terrain, HiddenChamberPlan.Theme.ICE_CATHEDRAL);
        assertFalse(result.isAccepted(),
                "magma at (" + magma[0] + "," + magma[1] + "," + magma[2] + ") sits on a face of the "
                        + "chamber's own ice: the dry quench melts that seal to AIR, opening the room to the "
                        + "natural cave, so the chunk must be declined");
        assertEquals(HiddenChamberPlan.RejectionDetail.MAGMA_ADJACENT_TO_ICE_OR_WATER, result.detail());
    }

    /** Every unauthored cell sharing a face with a cell in {@code roles}, in write order. */
    private static List<int[]> exposedFaces(HiddenChamberPlan.Plan plan,
                                            Set<HiddenChamberPlan.CellRole> roles) {
        Set<Long> written = new HashSet<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            written.add(key(cell.x(), cell.y(), cell.z()));
        }
        List<int[]> exposed = new ArrayList<>();
        for (HiddenChamberPlan.Cell cell : plan.writes()) {
            if (!roles.contains(cell.role())) {
                continue;
            }
            for (int[] face : FACES) {
                int x = cell.x() + face[0];
                int y = cell.y() + face[1];
                int z = cell.z() + face[2];
                if (y >= HiddenChamberPlan.MIN_AUTHORED_Y && !written.contains(key(x, y, z))) {
                    exposed.add(new int[] {x, y, z});
                }
            }
        }
        return exposed;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 42) | ((long) (y + 4096) << 21) | (z + 4096);
    }
}
