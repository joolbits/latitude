package com.example.globe.core;

import java.util.Locale;

/**
 * Phase 5 Slice S20 -- the polar-cold FLIGHT RECORDER (the "instrument, don't fix" law after THREE failed
 * live water-freeze rounds). A tiny, opt-in, TICK-COUNTER-based counter class (no wall clock) that records
 * two independent channels and, once per ~5 s window, hands the caller a formatted line to log:
 *
 * <ul>
 *   <li><b>FREEZE</b> ({@code -Dlatitude.debugFreeze}, default OFF): the server water-freeze pipeline --
 *       {@code [LAT][FREEZE] flowTicks=N passedFalling=N hunterFroze=N sweptSettled=N sweptFroze=N
 *       spreadFroze=N}. Fed by the flow-tick mixin (flow ticks seen / falls passed to vanilla / the
 *       ice-touch hunter's certain locks), the v6 tickChunk sweep driver (settled-landed candidates found /
 *       frozen), and the v6 spread-converter ({@code spreadFroze} = water spreads converted to ice at the
 *       destination -- the proof channel for the next self-fly that the TEST-113 respread ratchet is dead).
 *       Diagnoses "water is not freezing": flow ticks happening but nothing swept = the settled sweep is not
 *       reaching the pool; zero flow ticks = the pour never enters the zone/eligibility.</li>
 *   <li><b>SPARKLE</b> ({@code -Dlatitude.debugSparkle}, default OFF): the client snow-sparkle spawn path --
 *       {@code [LAT][SPARKLE] budget=N spawned=N window=X band=X}. {@code budget}/{@code spawned} are the
 *       per-window SUMS of the pure {@link SnowSparkleLaw#sparkleBudget} (glint CLUSTERS) and the actually-created
 *       particle count; {@code band} is the latest {@link SnowSparkleLaw#bandRamp01} sample and {@code window}
 *       the snowfall-gate factor {@code 1 - }{@link SnowSparkleLaw#snowfallRamp01} (their product is the GLINT v4
 *       {@link SnowSparkleLaw#glintWeight} crossfade). Diagnoses "sparkle invisible live": budget&gt;0 with
 *       spawned=0 = the perf/enclosure/reduce-snow scaling zeroed it; both&gt;0 yet nothing seen = a
 *       particle/height/brightness problem, not a budget one.</li>
 * </ul>
 *
 * <p><b>Pure, MC-free, unit-testable.</b> This class holds only primitive counters + tick-window arithmetic
 * and RETURNS the log line as a {@link String} (or {@code null} when the window has not elapsed) -- the actual
 * {@code LOGGER.info(...)} call happens at the MC-side call site (the mixin / client), which is the only place
 * that has the mod logger on its classpath. So the whole recorder is a Core-Logic-layer state machine that the
 * pure-JVM test set can drive directly (no SLF4J, no Minecraft).
 *
 * <p><b>Zero cost when off (the static-final gate).</b> {@link #FREEZE} / {@link #SPARKLE} are
 * {@code static final} booleans read once from {@code System.getProperty} at class-init (the
 * {@link LatitudeV2Flags} idiom). Every call site guards with {@code if (PolarInstrument.FREEZE) { ... }} /
 * {@code if (PolarInstrument.SPARKLE) { ... }} so the JIT constant-folds the branch away entirely when the
 * prop is unset -- the record/poll methods are never even called. The counter methods themselves do NOT
 * re-check the gate (so the state machine is fully exercisable in tests, where the gate is off); the gate lives
 * exclusively at the call sites, which are all guarded.
 *
 * <p><b>Threading.</b> The two channels never share mutable state. FREEZE is touched only by the server thread
 * (both freeze mixins run there); SPARKLE only by the client thread (the particle spawn path). Each channel is
 * therefore single-threaded and needs no synchronisation.
 */
public final class PolarInstrument {

    private PolarInstrument() {
    }

    /** {@code -Dlatitude.debugFreeze} -- the server freeze-pipeline recorder gate. Default OFF; static-final so
     *  the JIT elides every guarded call site when the prop is unset. */
    public static final boolean FREEZE = Boolean.getBoolean("latitude.debugFreeze");

    /** {@code -Dlatitude.debugSparkle} -- the client sparkle-spawn recorder gate. Default OFF; static-final so
     *  the JIT elides every guarded call site when the prop is unset. */
    public static final boolean SPARKLE = Boolean.getBoolean("latitude.debugSparkle");

    /** Window length in game ticks: ~5 s at 20 tps. Tick-counter driven (no wall clock), so it is deterministic
     *  and pauses exactly when the game does. */
    static final long WINDOW_TICKS = 100L;

    // ---- FREEZE channel (server thread) --------------------------------------------------------------------
    private static long freezeWindowStart = Long.MIN_VALUE;
    private static long flowTicks;
    private static long passedFalling;
    private static long hunterFroze;
    private static long sweptSettled;
    private static long sweptFroze;
    private static long spreadFroze;
    private static long sourceFroze;

    /** A flow tick seen for an eligible in-zone flowing-water block (the denominator). */
    public static void freezeFlowTick() {
        flowTicks++;
    }

    /** An eligible flow tick that was still FALLING (unsupported) and so passed to vanilla flow (falls run free). */
    public static void freezePassedFalling() {
        passedFalling++;
    }

    /** The ice-touch hunter locked a landed, touching-ice flowing block (certain -- the reroute lock / zipper). */
    public static void freezeHunterFroze() {
        hunterFroze++;
    }

    /** The sweep found a landed flowing block that was SETTLED (no pending fluid tick) -- a freeze candidate. */
    public static void freezeSweptSettled() {
        sweptSettled++;
    }

    /** A settled-landed candidate was eligible and frozen by the sweep (certain, no dice). */
    public static void freezeSweptFroze() {
        sweptFroze++;
    }

    /** S22 (WATER v6): the spread-converter turned a water spread into ice at the destination (certain, no
     *  dice) -- the CONVERT-AT-SPREAD counter that lets the next self-fly prove the respread ratchet is dead
     *  ({@code spreadFroze=} climbing while a pour meets ice, then falling to 0 as the path terminates). */
    public static void freezeSpreadFroze() {
        spreadFroze++;
    }

    /** S23 (TEST 115): the sweep driver claimed an OPEN SOURCE whose children had all frozen (the LAST law
     *  completing on our cadence instead of vanilla's ~205 s lottery) -- {@code sourceFroze=} ticking is the
     *  live proof that "the fall dies bottom-up, source last" now finishes. */
    public static void freezeSourceFroze() {
        sourceFroze++;
    }

    /**
     * Poll the FREEZE channel from the sweep driver's heartbeat (v6: the {@code ServerLevel.tickChunk} inject,
     * which fires every tick for every in-band chunk -- multiple polls per tick are fine, the window flushes at
     * most once).
     * Returns the formatted {@code [LAT][FREEZE] ...} line and RESETS the counters when the ~5 s window has
     * elapsed, else {@code null}. {@code gameTime} is {@code ServerLevel.getGameTime()} (a tick counter). The
     * first poll seeds the window and returns {@code null}.
     */
    public static String pollFreezeLine(long gameTime) {
        if (freezeWindowStart == Long.MIN_VALUE) {
            freezeWindowStart = gameTime;
            return null;
        }
        if (gameTime - freezeWindowStart < WINDOW_TICKS) {
            return null;
        }
        String line = formatFreezeLine(flowTicks, passedFalling, hunterFroze, sweptSettled, sweptFroze,
                spreadFroze, sourceFroze);
        flowTicks = 0;
        passedFalling = 0;
        hunterFroze = 0;
        sweptSettled = 0;
        sweptFroze = 0;
        spreadFroze = 0;
        sourceFroze = 0;
        freezeWindowStart = gameTime;
        return line;
    }

    /** The exact FREEZE line format (extracted pure so the format is unit-testable without touching the gate). */
    static String formatFreezeLine(long flowTicks, long passedFalling, long hunterFroze, long sweptSettled,
                                   long sweptFroze, long spreadFroze, long sourceFroze) {
        return String.format(Locale.ROOT,
                "[LAT][FREEZE] flowTicks=%d passedFalling=%d hunterFroze=%d sweptSettled=%d sweptFroze=%d"
                        + " spreadFroze=%d sourceFroze=%d",
                flowTicks, passedFalling, hunterFroze, sweptSettled, sweptFroze, spreadFroze, sourceFroze);
    }

    // ---- SPARKLE channel (client thread) -------------------------------------------------------------------
    private static long sparkleWindowStart = Long.MIN_VALUE;
    private static long budgetSum;
    private static long spawnedSum;
    private static double lastWindow01;
    private static double lastBand01;

    /**
     * Record one sparkle spawn-tick sample: the pure per-tick {@code budget} ({@link SnowSparkleLaw#sparkleBudget}),
     * the {@code spawned} particle count actually created (post perf/enclosure/reduce-snow scaling), and the
     * current {@code window01} / {@code band01} gate values. Budget and spawned accumulate into the window sums;
     * window/band keep the latest sample.
     */
    public static void sparkleSample(int budget, int spawned, double window01, double band01) {
        budgetSum += budget;
        spawnedSum += spawned;
        lastWindow01 = window01;
        lastBand01 = band01;
    }

    /**
     * Poll the SPARKLE channel once per spawn-tick from the client particle path. Returns the formatted
     * {@code [LAT][SPARKLE] ...} line and RESETS the sums when the ~5 s window has elapsed, else {@code null}.
     * {@code gameTime} is {@code client.level.getGameTime()} (a tick counter). The first poll seeds the window.
     */
    public static String pollSparkleLine(long gameTime) {
        if (sparkleWindowStart == Long.MIN_VALUE) {
            sparkleWindowStart = gameTime;
            return null;
        }
        if (gameTime - sparkleWindowStart < WINDOW_TICKS) {
            return null;
        }
        String line = formatSparkleLine(budgetSum, spawnedSum, lastWindow01, lastBand01);
        budgetSum = 0;
        spawnedSum = 0;
        sparkleWindowStart = gameTime;
        return line;
    }

    /** The exact SPARKLE line format (extracted pure so the format is unit-testable without touching the gate). */
    static String formatSparkleLine(long budget, long spawned, double window01, double band01) {
        return String.format(Locale.ROOT,
                "[LAT][SPARKLE] budget=%d spawned=%d window=%.2f band=%.2f",
                budget, spawned, window01, band01);
    }

    /** Test-only: clear both channels' counters + window anchors so each test starts from a known state. */
    static void resetForTest() {
        freezeWindowStart = Long.MIN_VALUE;
        flowTicks = 0;
        passedFalling = 0;
        hunterFroze = 0;
        sweptSettled = 0;
        sweptFroze = 0;
        spreadFroze = 0;
        sparkleWindowStart = Long.MIN_VALUE;
        budgetSum = 0;
        spawnedSum = 0;
        lastWindow01 = 0.0;
        lastBand01 = 0.0;
    }
}
