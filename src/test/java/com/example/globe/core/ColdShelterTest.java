package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link ColdShelter} (B-7 S4) -- the raw-sky-light shelter classifier behind the
 * cold-damage pause.
 *
 * <p><b>Why the classifier boundary, not a light engine (documented per the S4 mandate).</b> Real sky-light
 * propagation needs a running level + chunk light engine, which the pure-JVM suite deliberately excludes; the
 * shim ({@code GlobeMod.isColdSheltered}) is a ONE-LINE read
 * ({@code getLightEngine().getLayerListener(SKY).getLightValue(eyePos)}) wired straight into
 * {@link ColdShelter#isSheltered}, so the classifier boundary IS the testable decision. The MANDATORY
 * log-trap and sealed-box cases are pinned here at the raw values the vanilla light engine produces for
 * those geometries.
 */
class ColdShelterTest {

    @Test
    void openSkyIsNeverSheltered() {
        assertFalse(ColdShelter.isSheltered(15), "open sky (15) = exposed");
        assertFalse(ColdShelter.isSheltered(14));
    }

    @Test
    void logTrapStaysExposed_damageContinues() {
        // THE MANDATORY LOG-TRAP CASE (Peetsa's callout; the old warning-banner-under-a-tree bug class): a
        // single solid block one above the head with OPEN SIDES. The vanilla light engine side-propagates
        // diffuse sky light into that column -- the eye position reads ~11-13, NOT 0 -- so the classifier
        // keeps the player EXPOSED and cold damage continues. (Binary canSeeSky would have lied "sheltered"
        // here; the graded raw-sky-light read is the whole point of S4's predicate choice.)
        assertFalse(ColdShelter.isSheltered(13), "log trap ~13 -> NOT sheltered");
        assertFalse(ColdShelter.isSheltered(12), "log trap ~12 -> NOT sheltered");
        assertFalse(ColdShelter.isSheltered(11), "log trap ~11 -> NOT sheltered");
    }

    @Test
    void sealedShelterIsSheltered_damagePaused() {
        // A sealed hut / cave / snow burrow: raw sky light 0-2 at the eye -> genuinely sheltered.
        assertTrue(ColdShelter.isSheltered(0), "sealed box (0) -> sheltered");
        assertTrue(ColdShelter.isSheltered(1));
        assertTrue(ColdShelter.isSheltered(2), "snow burrow with a crack (2) -> sheltered");
    }

    @Test
    void thresholdBoundaryIsExactlyThree() {
        assertTrue(ColdShelter.isSheltered(ColdShelter.SHELTERED_MAX_SKY_LIGHT),
                "3 = the last sheltered value (a cracked doorway)");
        assertFalse(ColdShelter.isSheltered(ColdShelter.SHELTERED_MAX_SKY_LIGHT + 1),
                "4 = the first exposed value");
    }
}
