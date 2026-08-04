package com.example.globe.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collapse mouth is FLUSH with the cave floor it hides in, on snow-LAYER floors as well as full-block
 * ones -- and a mouth patch whose columns are not really level is rejected instead of built.
 *
 * <h2>What this class exists to stop happening again</h2>
 * The probe admits a snow LAYER as a column's floor top (correctly: it is what the player stands on), but the
 * mouth wrote a FULL {@code cave_trap_powder_snow} cube at that layer's own Y. On the ordinary glacial cave
 * floor -- {@code stone} with one to three snow layers on top, which step 7's {@code glacial_frost_carpet}
 * lays down BEFORE this feature runs -- the cube's top surface then stood at {@code floorY + 1.0} while every
 * neighbouring column's real surface stood at {@code floorY + layers/8}. At one layer that is a 0.875-block
 * step: taller than the 0.6 a player can walk up, so the "concealed" trap was a raised pad the victim
 * bounced off instead of falling through.
 *
 * <p>The fix lowers the cube onto the SUPPORTING block and clears the layer cell, so the cube's top is level
 * with the surrounding snow to within one layer. The second law here is its companion: the flat-patch spread
 * test now compares REAL surface heights in eighths rather than integer floor Y, so a patch mixing a
 * full-block top with a thin snow layer -- identical floor Y, nearly two blocks of real height between them --
 * is refused rather than built as a step.
 */
class HiddenChamberMouthFlushLawTest {

    private static final long SEED = 0x5EED_C0FFEEL;
    private static final int CHUNK_X = 4;
    private static final int CHUNK_Z = -9;

    /**
     * The sweep's exact repro stack. The carpeted middle of the cave floor is {@code stone} at 30 with one
     * to three snow layers at 31; the bare stone around it tops out at 30, so its real walking surface
     * ({@code 31.0}) is the same height a flush mouth cube must reach. Only the carpeted middle is
     * mouth-eligible, exactly as the shipped fixtures restrict their snowy plateau -- a floor that is snowy
     * everywhere crowds every candidate footprint onto the first anchor column and none of them fit the
     * envelope, which is a property of the anchor budget and not of this law.
     */
    private static final int PLATEAU_MIN = 4;
    private static final int PLATEAU_MAX = 11;
    private static final int CARPET_FLOOR_Y = 31;
    private static final int STONE_FLOOR_Y = 30;
    private static final int ROOF_Y = 39;

    private static boolean inPlateau(int localX, int localZ) {
        return localX >= PLATEAU_MIN && localX <= PLATEAU_MAX
                && localZ >= PLATEAU_MIN && localZ <= PLATEAU_MAX;
    }

    /** A player walks up at most 0.6 of a block; anything taller is a wall, and a visible one. */
    private static final double MAX_STEP = 0.6;

    /* ---------------------------------------------------------------------------------------------------- */
    /* 1. The mouth cube is flush with the snow it hides in                                                  */
    /* ---------------------------------------------------------------------------------------------------- */

    @Test
    void aMouthOnASnowLayerFloorTopsOutFlushWithEveryNeighbouringColumn() {
        for (int layers = 1; layers <= 3; layers++) {
            SnowCarpetCave terrain = new SnowCarpetCave(layers);
            HiddenChamberPlan.PlanResult result =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, null);
            assertTrue(result.isAccepted(),
                    "layers=" + layers + ": a snow-carpet cave floor must still take a chamber: "
                            + result.detail());
            HiddenChamberPlan.Plan plan = result.accepted();

            Set<Long> mouthColumns = new HashSet<>();
            for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
                mouthColumns.add(columnKey(mouth.x(), mouth.z()));
            }
            for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
                double cubeTop = mouth.y() + 1.0;
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = mouth.x() + step[0];
                    int nz = mouth.z() + step[1];
                    if (mouthColumns.contains(columnKey(nx, nz))) {
                        continue;
                    }
                    double neighbourSurface = inPlateau(nx, nz)
                            ? CARPET_FLOOR_Y + layers / 8.0
                            : STONE_FLOOR_Y + 1.0;
                    assertTrue(Math.abs(cubeTop - neighbourSurface) <= MAX_STEP,
                            "layers=" + layers + ": the mouth cube at " + mouth + " tops out at y=" + cubeTop
                                    + " while its neighbour (" + nx + "," + nz + ") stands at y="
                                    + neighbourSurface + " -- a " + Math.abs(cubeTop - neighbourSurface)
                                    + "-block step. The concealed floor is a raised pad the victim cannot "
                                    + "walk onto, so nothing ever falls through it");
                }
            }
        }
    }

    /**
     * The lowered cube must not leave the old snow layer standing on top of it: the cell the layer occupied
     * is cleared by the plan, so the powder cube IS the surface a victim steps on.
     */
    @Test
    void theSnowLayerCellOverALoweredMouthCubeIsClearedByThePlan() {
        for (int layers = 1; layers <= 3; layers++) {
            SnowCarpetCave terrain = new SnowCarpetCave(layers);
            HiddenChamberPlan.PlanResult result =
                    HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, terrain, null);
            assertTrue(result.isAccepted(), "layers=" + layers + ": " + result.detail());
            HiddenChamberPlan.Plan plan = result.accepted();

            for (HiddenChamberPlan.Cell mouth : plan.mouthCells()) {
                assertEquals(CARPET_FLOOR_Y - 1, mouth.y(),
                        "layers=" + layers + ": a partial snow layer top means the cube replaces the block "
                                + "UNDER it, not the layer's own cell");
                HiddenChamberPlan.Cell cleared = null;
                for (HiddenChamberPlan.Cell cell : plan.writes()) {
                    if (cell.x() == mouth.x() && cell.z() == mouth.z() && cell.y() == mouth.y() + 1) {
                        cleared = cell;
                    }
                }
                assertTrue(cleared != null && cleared.role().isCarve(),
                        "layers=" + layers + ": the snow layer over the mouth cube at " + mouth
                                + " is not cleared (found " + cleared + ") -- the cube would sit under a "
                                + "layer the victim walks on instead of through");
                assertFalse(cleared.role().isSealedInterior(),
                        "layers=" + layers + ": the cleared layer cell is part of the OPENING; sealing from "
                                + "it would cap the mouth exactly as the old seal law did");
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* 2. A patch that is level only in integer Y is refused                                                 */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * Every mouth mask spans at least two distinct X values, so on an X-striped floor -- full snow blocks
     * topping out at 33.0 beside single snow layers topping out at 31.125 -- no candidate footprint is level.
     * Judged on integer floor Y the two stripes look one block apart, which the spread law allows; judged on
     * real surface height they are nearly two blocks apart, which it must not.
     */
    @Test
    void aPatchLevelOnlyInIntegerFloorYIsRejectedForSpread() {
        /* The arithmetic the law now runs on, pinned before the planner is asked: the two stripes have
         * ADJACENT integer floor Y -- one apart, which MOUTH_MAX_FLOOR_SPREAD allows -- and fifteen eighths
         * of real height between them, which is nearly two blocks and must not be called flat. */
        HiddenChamberPlan.ColumnInfo fullBlock = new HiddenChamberPlan.ColumnInfo(32, true, true, 6, 8);
        HiddenChamberPlan.ColumnInfo thinLayer = new HiddenChamberPlan.ColumnInfo(31, true, true, 7, 1);
        assertEquals(1, fullBlock.floorY() - thinLayer.floorY(),
                "judged on integer floor Y these two columns are one block apart, which the flat-patch law "
                        + "allows -- that is exactly how the defect got in");
        assertTrue(fullBlock.surfaceEighths() - thinLayer.surfaceEighths()
                        > HiddenChamberPlan.MOUTH_MAX_FLOOR_SPREAD * HiddenChamberPlan.EIGHTHS_PER_BLOCK,
                "judged on real surface height they are " + (fullBlock.surfaceEighths()
                        - thinLayer.surfaceEighths()) + " eighths apart, which the law must refuse");

        HiddenChamberPlan.PlanResult result =
                HiddenChamberPlan.plan(SEED, CHUNK_X, CHUNK_Z, new StripedSurfaceCave(), null);
        assertFalse(result.isAccepted(),
                "every mouth mask spans at least two X values, so on an X-striped floor every candidate "
                        + "patch mixes the two stripes: there is no level mouth anywhere and the chunk must "
                        + "be refused rather than built as a step");
        /*
         * The rejection is NO_ENTRANCE and its detail is the planner's standing stage-zero attribution
         * (NO_MOUTH_ANCHOR): deeper() only ever promotes a failure to a LATER stage, so every footprint-stage
         * detail -- not snowy, not roofed, spread too wide -- reports under the same name. That is existing
         * law and this fix does not change it; what the fix changes is that these patches now fail at all.
         */
        assertEquals(HiddenChamberPlan.Rejection.NO_ENTRANCE, result.rejection(),
                "an uneven floor is an ENTRANCE problem: " + result.detail());
    }

    /* ---------------------------------------------------------------------------------------------------- */
    /* Terrain                                                                                               */
    /* ---------------------------------------------------------------------------------------------------- */

    /**
     * One glacial cave floor with a frost carpet in the middle: bare stone topping out at
     * {@link #STONE_FLOOR_Y} everywhere, plus {@code layers} eighths of snow on top of it inside the
     * plateau. Both surfaces stand within one snow layer of each other, so a flush mouth is possible.
     */
    private static final class SnowCarpetCave implements HiddenChamberPlan.TerrainProbe {
        private final int layers;

        private SnowCarpetCave(int layers) {
            this.layers = layers;
        }

        private int floorY(int localX, int localZ) {
            return inPlateau(localX, localZ) ? CARPET_FLOOR_Y : STONE_FLOOR_Y;
        }

        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            if (outsideWindow(localX, localZ)) {
                return null;
            }
            boolean carpet = inPlateau(localX, localZ);
            int floorY = floorY(localX, localZ);
            return new HiddenChamberPlan.ColumnInfo(
                    floorY, carpet, true, ROOF_Y - floorY - 1, carpet ? layers : 8);
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            if (outsideWindow(localX, localZ)) {
                return HiddenChamberPlan.CellKind.UNREADABLE;
            }
            if (y <= 0) {
                return HiddenChamberPlan.CellKind.BEDROCK;
            }
            if (y <= floorY(localX, localZ)) {
                return HiddenChamberPlan.CellKind.SOLID_SAFE;
            }
            return y < ROOF_Y ? HiddenChamberPlan.CellKind.AIR : HiddenChamberPlan.CellKind.SOLID_SAFE;
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            return true;
        }
    }

    /**
     * The carpeted plateau striped by X parity: even columns carry a FULL snow block topping out at 33.0,
     * odd columns a single snow layer topping out at 31.125. Their integer floor Y differs by one -- which
     * the flat-patch law allows -- while their real surfaces are nearly two blocks apart.
     */
    private static final class StripedSurfaceCave implements HiddenChamberPlan.TerrainProbe {
        private static final int HIGH_FLOOR_Y = 32;

        private static boolean high(int localX) {
            return Math.floorMod(localX, 2) == 0;
        }

        private static int floorY(int localX, int localZ) {
            if (!inPlateau(localX, localZ)) {
                return STONE_FLOOR_Y;
            }
            return high(localX) ? HIGH_FLOOR_Y : CARPET_FLOOR_Y;
        }

        @Override
        public HiddenChamberPlan.ColumnInfo column(int localX, int localZ) {
            if (outsideWindow(localX, localZ)) {
                return null;
            }
            boolean carpet = inPlateau(localX, localZ);
            int floorY = floorY(localX, localZ);
            int topEighths = !carpet || high(localX) ? 8 : 1;
            return new HiddenChamberPlan.ColumnInfo(
                    floorY, carpet, true, ROOF_Y - floorY - 1, topEighths);
        }

        @Override
        public HiddenChamberPlan.CellKind cell(int localX, int y, int localZ) {
            if (outsideWindow(localX, localZ)) {
                return HiddenChamberPlan.CellKind.UNREADABLE;
            }
            if (y <= 0) {
                return HiddenChamberPlan.CellKind.BEDROCK;
            }
            if (y <= floorY(localX, localZ)) {
                return HiddenChamberPlan.CellKind.SOLID_SAFE;
            }
            return y < ROOF_Y ? HiddenChamberPlan.CellKind.AIR : HiddenChamberPlan.CellKind.SOLID_SAFE;
        }

        @Override
        public boolean savedGlacialQuart(int quartLocalX, int quartY, int quartLocalZ) {
            return true;
        }
    }

    private static boolean outsideWindow(int localX, int localZ) {
        return localX < -40 || localX > 55 || localZ < -40 || localZ > 55;
    }

    private static long columnKey(int x, int z) {
        return ((long) (x + 4096) << 32) | (z + 4096);
    }
}
