package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link SpawnCalmBand} (S10a, ZONE-AWARE since the 2026-07-17 owner correction: an
 * explicit POLAR pick spawns "at the lowest latitude of polar" instead of being neutered to 50 deg). Pins:
 * the per-zone windows on the three canonical radii, the band-constant alignment (windows sit on the REAL
 * {@code LatitudeBands} onsets), the everyone-ceiling (nothing at/above 74, ever), the default/non-polar
 * flat-50 law, the jitter clamp (cannot escape a window on either bound), and the retirement of the illegal
 * legacy POLAR fraction (0.89 = 80.1 deg, which sat exactly ON the S8 storm-country onset).
 */
class SpawnCalmBandTest {

    private static final int[] RADII = {3750, 10000, 20000};

    private static double deg(int absZ, int zRadius) {
        return (double) absZ * 90.0 / zRadius;
    }

    // ---- explicit POLAR pick: the lowest latitudes of polar, on every radius ---------------------

    @Test
    void polarPickLandsInTheLowestLatitudesOfPolar() {
        for (int r : RADII) {
            SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow("POLAR", r);
            int target = SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r);
            assertTrue(deg(w.loAbsZ(), r) >= 66.4 && deg(w.loAbsZ(), r) <= 66.5,
                    "polar window floor sits on the 66.5 band onset @" + r);
            assertTrue(deg(w.hiAbsZ(), r) <= 70.0, "polar window ceiling <= 70 @" + r);
            assertTrue(target >= w.loAbsZ() && target <= w.hiAbsZ(),
                    "polar target inside [66.5, 70] @" + r);
            assertEquals((w.loAbsZ() + w.hiAbsZ()) / 2, target, "target = window midpoint (~68.25 deg)");
            // The owner's original complaint can never recur: the legacy 0.89 fraction (80.1 deg) is ignored.
            assertTrue(deg(target, r) < 70.1, "the illegal 80.1-deg legacy fraction is retired @" + r);
        }
    }

    @Test
    void subpolarPickLandsInTheLowEdge() {
        for (int r : RADII) {
            SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow("SUBPOLAR", r);
            int target = SpawnCalmBand.spawnTargetAbsZ("SUBPOLAR", 0.725, r);
            assertTrue(deg(w.loAbsZ(), r) >= 49.9 && deg(w.loAbsZ(), r) <= 50.0,
                    "subpolar window floor sits on the 50 band onset @" + r);
            assertTrue(deg(target, r) >= 50.0 - 0.1 && deg(target, r) <= 60.0,
                    "subpolar pick lands in [50, ~60] (actual low-edge window [50,55]) @" + r);
            assertTrue(target >= w.loAbsZ() && target <= w.hiAbsZ());
        }
    }

    @Test
    void windowsAlignToTheRealBandConstants() {
        // The pure constants must track the game's actual band table (LatitudeBands is not importable from
        // the pure core, so the alignment is pinned here, where the test classpath can load it).
        assertEquals(com.example.globe.util.LatitudeBands.Band.SUBPOLAR.lowDeg(),
                SpawnCalmBand.SUBPOLAR_WINDOW_LO_DEG, 1e-9, "subpolar window lo == the SUBPOLAR band onset");
        assertEquals(com.example.globe.util.LatitudeBands.Band.POLAR.lowDeg(),
                SpawnCalmBand.POLAR_WINDOW_LO_DEG, 1e-9, "polar window lo == the POLAR band onset");
    }

    // ---- default / non-polar: the original flat 50 law stands -----------------------------------

    @Test
    void defaultAndNonPolarZonesKeepTheFlatFiftyCap() {
        for (int r : RADII) {
            for (String zone : new String[]{"TEMPERATE", "EQUATOR", "TROPICAL", "SUBTROPICAL", null, "JUNK"}) {
                SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow(zone, r);
                assertEquals(0, w.loAbsZ(), "default window floor is the equator");
                assertTrue(deg(w.hiAbsZ(), r) <= 50.0, "default cap 50 for zone " + zone + " @" + r);
            }
            // TEMPERATE keeps its legacy fraction target (0.472 -> 42.48 deg), unclamped.
            int temperate = SpawnCalmBand.spawnTargetAbsZ("TEMPERATE", 0.472, r);
            assertEquals(Math.round(r * 0.472), temperate, 1.0, "non-polar targets keep the legacy fraction");
        }
        // Exact default-cap block values (floor of radius * 50/90) -- the original S10a pins.
        assertEquals(2083, SpawnCalmBand.maxAbsZ(3750));
        assertEquals(5555, SpawnCalmBand.maxAbsZ(10000));
        assertEquals(11111, SpawnCalmBand.maxAbsZ(20000));
    }

    // ---- the everyone-ceiling: storm country is unspawnable for ALL zones -----------------------

    @Test
    void nothingEverSpawnsAtOrAboveTheStormCeiling() {
        assertEquals(74.0, SpawnCalmBand.STORM_SPAWN_CEILING_DEG, 1e-9,
                "ceiling pick: 74 (inside the recommended 72-75; 6 deg of calm before the 80 onset)");
        assertTrue(SpawnCalmBand.STORM_SPAWN_CEILING_DEG <= 80.0 - 5.0,
                "a comfortable margin under the S8 polar-country onset");
        for (int r : RADII) {
            for (String zone : new String[]{"POLAR", "SUBPOLAR", "TEMPERATE", "EQUATOR", null, "JUNK", "RANDOM"}) {
                SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow(zone, r);
                int target = SpawnCalmBand.spawnTargetAbsZ(zone, 0.89, r);
                assertTrue(deg(w.hiAbsZ(), r) < SpawnCalmBand.STORM_SPAWN_CEILING_DEG + 1e-9,
                        "window ceiling under the storm ceiling: " + zone + " @" + r);
                assertTrue(deg(target, r) < SpawnCalmBand.STORM_SPAWN_CEILING_DEG,
                        "target under the storm ceiling: " + zone + " @" + r);
            }
        }
    }

    // ---- jitter cannot escape -------------------------------------------------------------------

    @Test
    void jitterCannotEscapeTheWindowOnEitherBound() {
        SpawnCalmBand.Window polar = SpawnCalmBand.spawnWindow("POLAR", 10000);
        int target = SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, 10000);
        for (int jitter = -300; jitter <= 300; jitter += 25) {
            int clamped = SpawnCalmBand.clampToWindow(target + jitter, polar);
            assertTrue(clamped >= polar.loAbsZ() && clamped <= polar.hiAbsZ(),
                    "northern jitter escaped the polar window: " + clamped);
            int south = SpawnCalmBand.clampToWindow(-target + jitter, polar);
            assertTrue(Math.abs(south) >= polar.loAbsZ() && Math.abs(south) <= polar.hiAbsZ(),
                    "southern jitter escaped the polar window: " + south);
            assertTrue(south <= 0, "hemisphere sign preserved");
        }
        // Default window: jitter across the equator stays legal (lo == 0).
        SpawnCalmBand.Window dflt = SpawnCalmBand.spawnWindow("EQUATOR", 10000);
        assertEquals(-20, SpawnCalmBand.clampToWindow(-20, dflt), "equator crossings stay legal by default");
    }

    // ---- degenerate radius ----------------------------------------------------------------------

    @Test
    void degenerateRadiusYieldsNoBand() {
        assertEquals(0, SpawnCalmBand.maxAbsZ(0));
        assertEquals(0, SpawnCalmBand.maxAbsZ(-5));
        assertEquals(0, SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, 0));
        SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow("POLAR", 0);
        assertEquals(0, w.loAbsZ());
        assertEquals(0, w.hiAbsZ());
    }
}
