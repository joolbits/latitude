package com.example.globe.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests for {@link EvatorCapture} -- the B-6 per-world capture resolution rules. Pins the birth-time
 * capture (flag AND not-boundaryV2), the boundaryV2 refusal signal, and the effective read where an unstamped
 * (existing / pre-evator) world is {@code false} forever independent of the global default.
 */
class EvatorCaptureTest {

    // ---- birth-time capture (folds in the boundaryV2 antagonism) ----------------------------

    @Test
    void captureAtBirth_armsOnlyWhenFlagOnAndBoundaryOff() {
        assertTrue(EvatorCapture.captureAtBirth(true, false), "flag on, boundary off -> arm");
        assertFalse(EvatorCapture.captureAtBirth(false, false), "flag off -> never arm");
        assertFalse(EvatorCapture.captureAtBirth(true, true), "boundary active -> refuse (antagonistic)");
        assertFalse(EvatorCapture.captureAtBirth(false, true), "flag off + boundary -> off");
    }

    // ---- the refusal signal is ONLY for the flag-wanted-on-but-boundary-vetoed case ---------

    @Test
    void refusedByBoundary_distinguishesRefusalFromPlainOff() {
        assertTrue(EvatorCapture.refusedByBoundary(true, true), "wanted on, vetoed by boundary -> refused");
        assertFalse(EvatorCapture.refusedByBoundary(true, false), "no boundary -> not a refusal");
        assertFalse(EvatorCapture.refusedByBoundary(false, true), "flag off -> plain off, not a refusal");
        assertFalse(EvatorCapture.refusedByBoundary(false, false), "flag off -> plain off");
    }

    // ---- effective read: unstamped world is false forever -----------------------------------

    @Test
    void resolveEffective_unstampedIsFalseForever() {
        assertFalse(EvatorCapture.resolveEffective(null),
                "an existing / pre-evator world (no capture) reads false regardless of any later default flip");
        assertFalse(EvatorCapture.resolveEffective(Boolean.FALSE), "captured off -> off");
        assertTrue(EvatorCapture.resolveEffective(Boolean.TRUE), "captured on -> on");
    }

    // ---- the composite birth->read contract -------------------------------------------------

    @Test
    void fullBirthToReadContract() {
        // A world born with the flag on and no boundary captures true and reads true.
        Boolean cap = EvatorCapture.captureAtBirth(true, false);
        assertTrue(EvatorCapture.resolveEffective(cap));
        // A world born with boundary active captures false (refused) and reads false.
        Boolean capRefused = EvatorCapture.captureAtBirth(true, true);
        assertFalse(EvatorCapture.resolveEffective(capRefused));
        assertTrue(EvatorCapture.refusedByBoundary(true, true));
    }
}
