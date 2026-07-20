package com.example.globe.core;

import java.util.Locale;

/**
 * TEMP-DIAG (2026-07-20) -- per-condition rejection counters for the settled-sweep ZERO-CLAIMS diagnosis
 * (TEST 114/115 OPEN FINDING: {@code sweptSettled=0 sweptFroze=0} across whole live sessions while the
 * heartbeat, the hunter, and the spread-converter all run). One counter per gate in
 * {@code ServerLevelRoofedWaterFreezeMixin.globe$sweepColumn}, so ONE {@code [LAT][SWEEPPROBE]} line per
 * recorder window says exactly which condition rejects every column. Same idiom as {@link PolarInstrument}:
 * pure, MC-free, server-thread-only, every call site guarded by {@code if (PolarInstrument.FREEZE)}.
 *
 * <p><b>DELETE THIS CLASS when the diagnosis round closes</b> (it is not part of the shipped recorder;
 * the binder's TEST 114 OPEN FINDING paragraph tracks the round).
 */
public final class PolarSweepProbe {

    private PolarSweepProbe() {
    }

    private static long windowStart = Long.MIN_VALUE;
    private static long cols;
    private static long rejFront;
    private static long retSurfaceSource;
    private static long retRoofedSource;
    private static long rejDry;
    private static long rejNotLanded;
    private static long rejBarrensOff;
    private static long rejOcean;
    private static long rejFloor;
    private static long probed;
    private static long baseNotFlowing;
    private static long pendFlowKey;
    private static long pendWaterKey;
    private static long pendBothKeys;
    private static String floorDetail = "-";
    private static String pendDetail = "-";

    /** A column visit entered globe$sweepColumn (the denominator for every rejection below). */
    public static void visit() {
        cols++;
    }

    /** The per-column latitude/front gate rejected (NaN, below onset, or fray-losing column). */
    public static void rejectFront() {
        rejFront++;
    }

    /** Early return: the MOTION_BLOCKING surface was open SOURCE water (vanilla owns the skin). */
    public static void returnSurfaceSource() {
        retSurfaceSource++;
    }

    /** Phase-1 return: topmost water within the roof reach was a SOURCE (sky-waiver branch ran). */
    public static void returnRoofedSource() {
        retRoofedSource++;
    }

    /** Phase-1 found no water at all within the roof reach (dry column / sealed B-9 reservoir). */
    public static void rejectDry() {
        rejDry++;
    }

    /** The landed check rejected: the base's below was air or fluid (still falling / over water). */
    public static void rejectNotLanded() {
        rejNotLanded++;
    }

    /** The barrens family flag was off at the flowing-eligibility step. */
    public static void rejectBarrensOff() {
        rejBarrensOff++;
    }

    /** tickFreezesFlowing rejected because the column's biome is ocean-family. */
    public static void rejectOcean() {
        rejOcean++;
    }

    /** tickFreezesFlowing rejected because the base sat at/below the tick freeze floor. */
    public static void rejectFloor(int x, int z, int baseY, int worldSurfaceY, int oceanFloorY) {
        rejFloor++;
        if ("-".equals(floorDetail)) {
            floorDetail = String.format(Locale.ROOT, "x%d,z%d,base%d,ws%d,of%d",
                    x, z, baseY, worldSurfaceY, oceanFloorY);
        }
    }

    /** All WHERE gates passed; phase-3 run discovery started for this column. */
    public static void probedColumn() {
        probed++;
    }

    /** Phase-3 first block was not FLOWING_WATER (structurally unexpected after phase 2). */
    public static void baseNotFlowing() {
        baseNotFlowing++;
    }

    /** Phase-3 first block had a pending scheduled fluid tick -- which key(s) held it. */
    public static void basePending(int x, int y, int z, boolean flowKeyPending, boolean waterKeyPending) {
        if (flowKeyPending && waterKeyPending) {
            pendBothKeys++;
        } else if (flowKeyPending) {
            pendFlowKey++;
        } else {
            pendWaterKey++;
        }
        if ("-".equals(pendDetail)) {
            pendDetail = String.format(Locale.ROOT, "x%d,y%d,z%d,%s%s",
                    x, y, z, flowKeyPending ? "F" : "", waterKeyPending ? "W" : "");
        }
    }

    /**
     * Poll from the sweep driver's heartbeat (same cadence/idiom as {@link PolarInstrument#pollFreezeLine}):
     * returns the formatted line and resets when the window has elapsed, else {@code null}.
     */
    public static String poll(long gameTime) {
        if (windowStart == Long.MIN_VALUE) {
            windowStart = gameTime;
            return null;
        }
        if (gameTime - windowStart < PolarInstrument.WINDOW_TICKS) {
            return null;
        }
        String line = String.format(Locale.ROOT,
                "[LAT][SWEEPPROBE] cols=%d front=%d surfSrc=%d roofSrc=%d dry=%d notLanded=%d barrensOff=%d"
                        + " ocean=%d floor=%d probed=%d baseNotFlow=%d pendF=%d pendW=%d pendFW=%d"
                        + " floorAt=%s pendAt=%s",
                cols, rejFront, retSurfaceSource, retRoofedSource, rejDry, rejNotLanded, rejBarrensOff,
                rejOcean, rejFloor, probed, baseNotFlowing, pendFlowKey, pendWaterKey, pendBothKeys,
                floorDetail, pendDetail);
        cols = 0;
        rejFront = 0;
        retSurfaceSource = 0;
        retRoofedSource = 0;
        rejDry = 0;
        rejNotLanded = 0;
        rejBarrensOff = 0;
        rejOcean = 0;
        rejFloor = 0;
        probed = 0;
        baseNotFlowing = 0;
        pendFlowKey = 0;
        pendWaterKey = 0;
        pendBothKeys = 0;
        floorDetail = "-";
        pendDetail = "-";
        windowStart = gameTime;
        return line;
    }
}
