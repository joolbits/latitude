package com.example.globe.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pure-JVM tests for {@link PolarInstrument} -- the S20 polar flight recorder. Verifies the gate defaults OFF
 * (so the recorder is inert unless a {@code -Dlatitude.debug*} prop is set), the exact log-line formats (which
 * the live flight reads), and the ~5 s tick-window rolling + counter reset on both channels.
 */
class PolarInstrumentTest {

    @BeforeEach
    void reset() {
        PolarInstrument.resetForTest();
    }

    @Test
    void gatesDefaultOff() {
        // The whole point of the "zero cost when unset" contract: with neither prop set (the normal test/run JVM),
        // both static-final gates read false, so every guarded call site is dead-code-eliminated.
        assertFalse(PolarInstrument.FREEZE, "-Dlatitude.debugFreeze default OFF");
        assertFalse(PolarInstrument.SPARKLE, "-Dlatitude.debugSparkle default OFF");
    }

    @Test
    void freezeLineFormat_exact() {
        // v6 added spreadFroze (convert-at-spread proof); S23 added sourceFroze (the LAST-law completion
        // claim -- the TEST 115 owner ask "the source water always stays liquid" made it a proof channel).
        assertEquals("[LAT][FREEZE] flowTicks=1 passedFalling=2 hunterFroze=3 sweptSettled=4 sweptFroze=5"
                        + " spreadFroze=6 sourceFroze=7",
                PolarInstrument.formatFreezeLine(1, 2, 3, 4, 5, 6, 7));
    }

    @Test
    void sparkleLineFormat_exact() {
        // window/band/clock are fractions rendered %.2f under Locale.ROOT (a '.' decimal always, never a locale comma).
        // S24 appended clock= (the GLINT CLOCK day curve) after band=.
        assertEquals("[LAT][SPARKLE] budget=8 spawned=5 window=0.50 band=0.80 clock=1.00",
                PolarInstrument.formatSparkleLine(8, 5, 0.5, 0.8, 1.0));
    }

    @Test
    void freezeWindowRollsAtFiveSeconds_thenResetsCounters() {
        // First poll seeds the window and never flushes.
        assertNull(PolarInstrument.pollFreezeLine(1000L), "first poll seeds the window (no line)");
        // Accumulate across the window.
        PolarInstrument.freezeFlowTick();
        PolarInstrument.freezeFlowTick();
        PolarInstrument.freezePassedFalling();
        PolarInstrument.freezeHunterFroze();
        PolarInstrument.freezeSweptSettled();
        PolarInstrument.freezeSweptSettled();
        PolarInstrument.freezeSweptSettled();
        PolarInstrument.freezeSweptFroze();
        PolarInstrument.freezeSpreadFroze();
        PolarInstrument.freezeSpreadFroze();
        PolarInstrument.freezeSourceFroze();
        // Before the window elapses (< WINDOW_TICKS since the seed) -> no line.
        assertNull(PolarInstrument.pollFreezeLine(1000L + PolarInstrument.WINDOW_TICKS - 1),
                "still inside the window -> no flush");
        // At/after the window -> flush the accumulated counts.
        String line = PolarInstrument.pollFreezeLine(1000L + PolarInstrument.WINDOW_TICKS);
        assertEquals("[LAT][FREEZE] flowTicks=2 passedFalling=1 hunterFroze=1 sweptSettled=3 sweptFroze=1"
                + " spreadFroze=2 sourceFroze=1", line);
        // Counters reset + window re-anchored: the NEXT window (no events) reports all zeros.
        assertNull(PolarInstrument.pollFreezeLine(1000L + PolarInstrument.WINDOW_TICKS + PolarInstrument.WINDOW_TICKS - 1),
                "new window, still inside -> no flush");
        String zeros = PolarInstrument.pollFreezeLine(1000L + 2 * PolarInstrument.WINDOW_TICKS);
        assertEquals("[LAT][FREEZE] flowTicks=0 passedFalling=0 hunterFroze=0 sweptSettled=0 sweptFroze=0"
                        + " spreadFroze=0 sourceFroze=0", zeros,
                "counters reset after the flush");
    }

    @Test
    void sparkleWindowSumsBudgetSpawned_keepsLatestWindowBandClock() {
        assertNull(PolarInstrument.pollSparkleLine(500L), "first poll seeds the window (no line)");
        PolarInstrument.sparkleSample(4, 2, 1.0, 0.8, 1.0);
        PolarInstrument.sparkleSample(4, 3, 0.5, 0.6, 0.5); // budget/spawned SUM (8/5); window/band/clock = LATEST
        assertNull(PolarInstrument.pollSparkleLine(500L + PolarInstrument.WINDOW_TICKS - 1), "inside the window");
        String line = PolarInstrument.pollSparkleLine(500L + PolarInstrument.WINDOW_TICKS);
        assertEquals("[LAT][SPARKLE] budget=8 spawned=5 window=0.50 band=0.60 clock=0.50", line);
        // Sums reset; a fresh window with a single sample reports just that sample. clock=0.00 here is the
        // "midday-bright band but clock read as night" diagnostic (S24).
        PolarInstrument.sparkleSample(4, 0, 1.0, 0.0, 0.0); // budget>0 but spawned=0 (the "scaling zeroed it" signal)
        String next = PolarInstrument.pollSparkleLine(500L + 2 * PolarInstrument.WINDOW_TICKS);
        assertEquals("[LAT][SPARKLE] budget=4 spawned=0 window=1.00 band=0.00 clock=0.00", next,
                "sums reset after the flush; budget>0 with spawned=0 is diagnosable");
    }

    @Test
    void pollBeforeAnySample_returnsNull() {
        // A poll with no prior activity just seeds the window (channels are independent).
        assertNull(PolarInstrument.pollFreezeLine(42L));
        assertNull(PolarInstrument.pollSparkleLine(42L));
    }
}
