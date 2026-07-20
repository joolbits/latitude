package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link SpawnCalmBand} (S10a ZONE-AWARE; S25 owner TEST 117: the explicit POLAR pick is
 * now UNIFORM across the widened [66.5, 79] approach band, driven by a seeded fraction, instead of a fixed
 * low-edge midpoint). Pins: the per-zone windows on the three canonical radii, the band-constant alignment
 * (windows sit on the REAL {@code LatitudeBands} onsets), the CEILING SPLIT (non-polar zones under the 74
 * everyone-ceiling; POLAR under its own 79 expedition ceiling, still below the 80 onset), the seeded POLAR
 * pick (uniform + monotonic + clamped), the default/non-polar flat-50 law, and the jitter clamp (cannot
 * escape a window on either bound). The illegal legacy POLAR fraction (0.89 = 80.1 deg, ON the S8 onset)
 * stays retired -- even the 79-ceiling pick is one degree under it.
 */
class SpawnCalmBandTest {

    private static final int[] RADII = {3750, 10000, 20000};

    private static double deg(int absZ, int zRadius) {
        return (double) absZ * 90.0 / zRadius;
    }

    // ---- explicit POLAR pick: UNIFORM across the widened [66.5, 79] approach band (S25) ----------

    @Test
    void polarWindowIsTheWidenedApproachBand() {
        for (int r : RADII) {
            SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow("POLAR", r);
            assertTrue(deg(w.loAbsZ(), r) >= 66.4 && deg(w.loAbsZ(), r) <= 66.5,
                    "polar window floor sits on the 66.5 band onset @" + r);
            assertTrue(deg(w.hiAbsZ(), r) >= 78.9 && deg(w.hiAbsZ(), r) <= 79.0,
                    "polar window ceiling sits at the 79-deg expedition ceiling @" + r);
        }
    }

    @Test
    void polarSeededPickIsUniformAcrossTheBand() {
        for (int r : RADII) {
            SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow("POLAR", r);
            // frac 0 -> floor, frac 1 -> ceiling, frac 0.5 -> midpoint; monotonic non-decreasing between.
            assertEquals(w.loAbsZ(), SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, 0.0),
                    "frac 0 lands at the 66.5 floor @" + r);
            assertEquals(w.hiAbsZ(), SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, 1.0),
                    "frac 1 lands at the 79 ceiling @" + r);
            assertEquals((w.loAbsZ() + w.hiAbsZ()) / 2,
                    SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, 0.5), 1.0,
                    "frac 0.5 lands at the window midpoint @" + r);
            int prev = Integer.MIN_VALUE;
            for (int k = 0; k <= 10; k++) {
                int t = SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, k / 10.0);
                assertTrue(t >= w.loAbsZ() && t <= w.hiAbsZ(), "seeded pick stays in the window @" + r);
                assertTrue(t >= prev, "seeded pick is monotonic in frac @" + r);
                prev = t;
            }
            // Out-of-range / NaN fracs are conservative: clamped to the window (NaN -> midpoint).
            assertEquals(w.loAbsZ(), SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, -3.0), "frac<0 clamps to floor");
            assertEquals(w.hiAbsZ(), SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, 9.0), "frac>1 clamps to ceiling");
            assertEquals((w.loAbsZ() + w.hiAbsZ()) / 2,
                    SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, Double.NaN), 1.0, "NaN frac -> midpoint");
            // The 3-arg (legacy/SUBPOLAR) form still returns the midpoint, and the illegal 80.1-deg fraction
            // stays retired: even the ceiling-grazing pick is one degree under the 80-deg storm onset.
            assertEquals((w.loAbsZ() + w.hiAbsZ()) / 2, SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r),
                    "3-arg polar target = window midpoint");
            assertTrue(deg(w.hiAbsZ(), r) < 80.0, "even the ceiling pick stays under the 80-deg onset @" + r);
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

    // ---- the ceilings: 74 for everyone, 79 for the POLAR expedition (S25 split) ------------------

    @Test
    void nonPolarZonesStayUnderThe74EveryoneCeiling() {
        assertEquals(74.0, SpawnCalmBand.STORM_SPAWN_CEILING_DEG, 1e-9,
                "everyone-ceiling: 74 (inside the recommended 72-75; 6 deg of calm before the 80 onset)");
        assertTrue(SpawnCalmBand.STORM_SPAWN_CEILING_DEG <= 80.0 - 5.0,
                "a comfortable margin under the S8 polar-country onset");
        for (int r : RADII) {
            // POLAR is deliberately EXCLUDED -- it has its own 79 ceiling (asserted below).
            for (String zone : new String[]{"SUBPOLAR", "TEMPERATE", "EQUATOR", null, "JUNK"}) {
                SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow(zone, r);
                int target = SpawnCalmBand.spawnTargetAbsZ(zone, 0.89, r);
                assertTrue(deg(w.hiAbsZ(), r) < SpawnCalmBand.STORM_SPAWN_CEILING_DEG + 1e-9,
                        "window ceiling under the everyone-ceiling: " + zone + " @" + r);
                assertTrue(deg(target, r) < SpawnCalmBand.STORM_SPAWN_CEILING_DEG,
                        "target under the everyone-ceiling: " + zone + " @" + r);
            }
        }
    }

    @Test
    void polarHasItsOwn79ExpeditionCeilingUnderTheOnset() {
        assertEquals(79.0, SpawnCalmBand.POLAR_SPAWN_CEILING_DEG, 1e-9,
                "the POLAR expedition ceiling is 79 (owner S25) -- above the 74 everyone-ceiling");
        assertEquals(SpawnCalmBand.POLAR_WINDOW_HI_DEG, SpawnCalmBand.POLAR_SPAWN_CEILING_DEG, 1e-9,
                "the POLAR window hi and its own ceiling are the same 79");
        assertTrue(SpawnCalmBand.POLAR_SPAWN_CEILING_DEG > SpawnCalmBand.STORM_SPAWN_CEILING_DEG,
                "POLAR is the ONE zone allowed above the everyone-ceiling");
        assertTrue(SpawnCalmBand.POLAR_SPAWN_CEILING_DEG < 80.0,
                "even the POLAR ceiling stays under the 80-deg polar-country onset");
        for (int r : RADII) {
            SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow("POLAR", r);
            for (double frac = 0.0; frac <= 1.0; frac += 0.1) {
                int target = SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, r, frac);
                assertTrue(deg(target, r) < 80.0, "every seeded POLAR pick stays under the 80 onset @" + r);
                assertTrue(target <= w.hiAbsZ(), "seeded POLAR pick under its own 79 ceiling @" + r);
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
        assertEquals(0, SpawnCalmBand.spawnTargetAbsZ("POLAR", 0.89, 0, 1.0), "4-arg seeded pick no-band @ r<=0");
        SpawnCalmBand.Window w = SpawnCalmBand.spawnWindow("POLAR", 0);
        assertEquals(0, w.loAbsZ());
        assertEquals(0, w.hiAbsZ());
    }
}
