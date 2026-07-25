package com.example.globe.core;

/**
 * Phase 5 B-4 (polish pass 1) -- pure anti-spam state machine for the ZONE (climate-band) title,
 * mirroring the hemisphere-title band hysteresis in {@link HemisphereCrossing}. Zero Minecraft
 * imports (Core Logic layer, unit-testable in a plain JVM).
 *
 * <p>Problem: walking/building along a climate-band edge (e.g. temperate<->subtropical) re-fired the
 * big zone title on every re-crossing (the caller only had a coarse moved-far/10-tick throttle). Fix:
 * the FULL title fires ONCE on a genuine zone change; while the player LINGERS within one band-width
 * (3 deg of latitude, floored) of the nearest band boundary, subsequent zone flips show only the SMALL
 * action-bar message; the FULL title re-arms once the player SETTLES more than a band-width from the
 * nearest boundary (i.e. deep inside a zone).
 *
 * <p>The band boundaries are the {@link com.example.globe.util.LatitudeBands} absolute-latitude edges
 * {@code {23.5, 35, 50, 66.5}} deg (both hemispheres, since the caller feeds {@code |lat|}). They are
 * duplicated here as plain constants -- like {@code LatitudeMath}'s own boundary fractions -- so this
 * class stays free of any Minecraft/util import and is testable in isolation.
 *
 * <p>Unit-agnostic {@link #evaluate}: the caller passes {@code distToBoundary} and {@code band} in the
 * SAME unit (the caller uses BLOCKS, so the {@code DEAD_ZONE+32} floor applies), and this decides
 * FULL/SMALL/NONE plus the next {@code fullArmed} state to persist. Zone titles are latitude(Z)-axis
 * only, so there is a single armed flag (no per-axis bookkeeping).
 */
public final class ZoneTitleBanding {

    /** Absolute-latitude band boundaries in degrees (mirrors {@code LatitudeBands.Band} low/high edges). */
    public static final double[] BOUNDARY_ABS_LAT_DEG = {23.5, 35.0, 50.0, 66.5};

    private ZoneTitleBanding() {
    }

    /** Which zone title (if any) a re-announcement should show. Mirrors {@link HemisphereCrossing.Fire}. */
    public enum Fire {
        NONE, FULL, SMALL
    }

    /** Smallest absolute-latitude distance (deg) from {@code absLatDeg} to any band boundary. The pole
     *  (90) and equator (0) are NOT boundaries -- only the four {@link #BOUNDARY_ABS_LAT_DEG} edges are,
     *  matching {@code LatitudeBands.fromAbsoluteLatitudeDeg}, so a player deep in the polar or tropical
     *  interior reads as "settled" (far from any boundary) and re-arms the FULL title. */
    public static double nearestBoundaryDistanceDeg(double absLatDeg) {
        double best = Double.MAX_VALUE;
        for (double b : BOUNDARY_ABS_LAT_DEG) {
            best = Math.min(best, Math.abs(absLatDeg - b));
        }
        return best;
    }

    /**
     * Pure decision for one throttled sample. Given whether the zone key genuinely changed this sample,
     * the current armed state, and how far (same unit as {@code band}) the player is from the nearest
     * band boundary, returns which title to show and the next armed state to persist.
     *
     * @param zoneChanged   true iff the canonical zone key differs from the last announced one
     * @param fullArmed     whether the FULL title is currently armed (true on a fresh approach)
     * @param distToBoundary distance from the nearest band boundary, SAME unit as {@code band}
     * @param band          one band-width (3 deg of latitude in blocks, floored) -- the linger radius
     */
    public static Result evaluate(boolean zoneChanged, boolean fullArmed,
                                  double distToBoundary, double band) {
        Fire kind = Fire.NONE;
        boolean nextArmed = fullArmed;
        if (zoneChanged) {
            if (fullArmed) {
                kind = Fire.FULL;
                nextArmed = false; // full consumed until the player settles deep in a zone again
            } else {
                kind = Fire.SMALL;
            }
        }
        // Settle re-arm: more than one band-width from the nearest boundary == deep inside a zone.
        if (distToBoundary >= band) {
            nextArmed = true;
        }
        return new Result(kind, nextArmed);
    }

    /** Outcome of {@link #evaluate}: which title kind to show plus the next armed state to store. */
    public record Result(Fire fire, boolean nextFullArmed) {
    }
}
