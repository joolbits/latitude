package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link PolarSurfaceSpawns} (S13 (e) polar surface allowlist,
 * {@code latitude.polarSurfaceSpawns.enabled}). Covers the band boundary, the sky/latitude gating of the
 * non-stray veto, and the 1-in-3 stray thinning roll semantics. These run with the DEFAULT onset (no
 * {@code -D} set), so the onset is the storm/ambient onset and the flag class's default is exercised.
 */
class PolarSurfaceSpawnsTest {

    private static final double EPS = 1e-9;

    // --- ONSET_DEG: coupled to the storm/ambient onset ------------------------------------------

    @Test
    void onsetDefaultsToTheStormOnset() {
        assertEquals(PolarHazardWindow.AMBIENT_ONSET_DEG, PolarSurfaceSpawns.ONSET_DEG, EPS,
                "the allowlist begins where polar storm country begins (80 deg)");
    }

    // --- inPolarBand: boundary + hemispheres + NaN ----------------------------------------------

    @Test
    void inBandAtAndAboveTheOnset() {
        assertTrue(PolarSurfaceSpawns.inPolarBand(PolarSurfaceSpawns.ONSET_DEG), "inclusive at the onset");
        assertTrue(PolarSurfaceSpawns.inPolarBand(85.0));
        assertTrue(PolarSurfaceSpawns.inPolarBand(90.0));
        assertTrue(PolarSurfaceSpawns.inPolarBand(-88.0), "hemisphere-symmetric (south pole)");
    }

    @Test
    void outOfBandEquatorward() {
        assertFalse(PolarSurfaceSpawns.inPolarBand(PolarSurfaceSpawns.ONSET_DEG - 0.001));
        assertFalse(PolarSurfaceSpawns.inPolarBand(60.0));
        assertFalse(PolarSurfaceSpawns.inPolarBand(0.0));
        assertFalse(PolarSurfaceSpawns.inPolarBand(-40.0));
    }

    @Test
    void nanLatitudeNeverVetoes() {
        assertFalse(PolarSurfaceSpawns.inPolarBand(Double.NaN), "bad read: never veto (vanilla-preserving)");
    }

    // --- vetoesNonStrayMonster: sky + band gate -------------------------------------------------

    @Test
    void nonStrayMonsterVetoedOnTheExposedPolarSurface() {
        assertTrue(PolarSurfaceSpawns.vetoesNonStrayMonster(85.0, true),
                "a sky-exposed monster in the storm cap is vetoed (cave-only)");
        assertTrue(PolarSurfaceSpawns.vetoesNonStrayMonster(90.0, true));
    }

    @Test
    void nonStrayMonsterSurvivesInCaves() {
        assertFalse(PolarSurfaceSpawns.vetoesNonStrayMonster(85.0, false),
                "no sky (cave/roofed): untouched, vanilla dungeon crawl under the ice");
        assertFalse(PolarSurfaceSpawns.vetoesNonStrayMonster(90.0, false));
    }

    @Test
    void nonStrayMonsterSurvivesEquatorward() {
        assertFalse(PolarSurfaceSpawns.vetoesNonStrayMonster(70.0, true), "below the storm onset: untouched");
        assertFalse(PolarSurfaceSpawns.vetoesNonStrayMonster(0.0, true));
    }

    // --- thinsStray: 1-in-DENOM surface thinning ------------------------------------------------

    @Test
    void strayThinningDenominatorIsOneInThree() {
        assertEquals(3, PolarSurfaceSpawns.STRAY_SURFACE_KEEP_DENOM,
                "~2/3 of surface strays are cut, matching the barrens spawner cut (S13 d)");
    }

    @Test
    void strayKeptOnZeroRollVetoedOtherwise() {
        // roll in [0,DENOM): keep only roll==0 (1-in-3), veto 1 and 2.
        assertFalse(PolarSurfaceSpawns.thinsStray(85.0, true, 0), "roll 0 -> kept");
        assertTrue(PolarSurfaceSpawns.thinsStray(85.0, true, 1), "roll 1 -> vetoed");
        assertTrue(PolarSurfaceSpawns.thinsStray(85.0, true, 2), "roll 2 -> vetoed");
    }

    @Test
    void strayThinningOnlyOnTheExposedPolarSurface() {
        // Even a would-veto roll never thins a cave stray or an equatorward stray.
        assertFalse(PolarSurfaceSpawns.thinsStray(85.0, false, 2), "cave stray: never thinned");
        assertFalse(PolarSurfaceSpawns.thinsStray(70.0, true, 2), "equatorward stray: never thinned");
    }

    @Test
    void strayThinningRoughlyKeepsOneInThree() {
        int kept = 0;
        for (int roll = 0; roll < PolarSurfaceSpawns.STRAY_SURFACE_KEEP_DENOM; roll++) {
            if (!PolarSurfaceSpawns.thinsStray(88.0, true, roll)) {
                kept++;
            }
        }
        assertEquals(1, kept, "exactly one roll value (0) keeps the stray");
    }
}
