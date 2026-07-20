package com.example.globe.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure detection + roofing math for Phase 5 S25b "powder-snow roofed crevasse traps" (the collapsing-snow V1
 * the owner went hunting for -- Peetsa 2026-07-20: "I was looking for the snow-covered traps covering
 * crevasses and couldn't find any", banked in the B-9 design doc's S-BANK item 1). Where the {@code
 * globe:crevasse} carver cuts a NARROW slot through the barrens snowfield, SOME slot openings are bridged at
 * the snowfield surface with a single flush layer of {@code powder_snow}: vanilla powder-snow physics IS the
 * trap (walk on, sink through, fall into the crevasse), leather boots are the free counter (they already walk
 * on powder snow), and the subtle powder-vs-snow texture difference is the learnable tell. Grand canyons stay
 * open (only narrow slots get roofed) and the trap is only SOME slots (a deterministic minority), so a
 * crevasse field stays readable rather than a lethal minefield.
 *
 * <p>This class holds ONLY the decision math (mirroring {@link PolarBarrensBand}): the {@link
 * #isTrapCandidate(int, int)} depth test, {@link #roofableSpans(boolean[])} span detection, and {@link
 * #shouldRoofSpan(float)} fraction gate. Zero Minecraft imports -- Core Logic layer, unit-testable in a plain
 * JVM. The world-side wiring ({@code world.PowderCrevasseRoofFeature}) reads the WORLD_SURFACE heightmap of
 * the decorating chunk, derives the per-column snowfield reference (a windowed local maximum, so a slot
 * column's cut floor reads as a deep shaft while the surrounding snowfield reads its true height), feeds this
 * class, and places the powder roof + the snow cushion.
 *
 * <h2>Why a windowed local-max reference (design note, orientation-independent)</h2>
 * A crevasse (canyon carver) winds in any direction. Rather than measure slot WIDTH perpendicular to an
 * unknown run direction, the feature marks a column a candidate when the local snowfield maximum
 * ({@link #REFERENCE_WINDOW_RADIUS}-radius window max of WORLD_SURFACE) sits {@link #MIN_SHAFT_DEPTH_BLOCKS}
 * or more above the column's own surface. This has two clean consequences the span pass then refines:
 * <ul>
 *   <li>A WIDE canyon's interior columns have no snowfield inside their small window -- their window max is
 *       itself canyon-low -- so they read depth ~0 and are NOT candidates. Only the canyon RIM lights up,
 *       and rim runs are long, so the span-width gate rejects them.</li>
 *   <li>A NARROW slot's columns always have snowfield within the window (a &le;{@code 2*R}-wide slot is never
 *       more than {@code R} from open ground perpendicular), so the whole slot lights up as candidates and
 *       the short span survives the width gate.</li>
 * </ul>
 * The feature runs the span pass along BOTH chunk axes and unions the roofed columns, so a slot of either
 * orientation is caught; per-span roll independence is accepted (a crossing may roof slightly more often).
 */
public final class PowderRoofTrap {

    private PowderRoofTrap() {
    }

    /**
     * Minimum air-shaft depth (blocks) below the local snowfield for a column to be a trap candidate. A slot
     * must descend at least this far for the powder roof to be a genuine fall trap (and for a merely uneven
     * snowfield or a shallow scoop to never qualify). 10 mirrors the design bank's ">= 10 blocks" cut. Also
     * the fall floor at which a landing is a real cost (a 10-block fall is ~3.5 hearts before the cushion).
     */
    public static final int MIN_SHAFT_DEPTH_BLOCKS = 10;

    /**
     * Maximum span WIDTH (columns) of a candidate run that may be roofed. Narrow slots (&le; this) get the
     * hidden powder bridge; wider gaps -- grand canyons and wide-open crevasse mouths -- stay open so a
     * crevasse field reads honestly (design: "narrow slots get roofed; grand canyons stay open"). 5 is a slot
     * a player can stride and never notice was bridged.
     */
    public static final int MAX_ROOF_SPAN_WIDTH = 5;

    /**
     * Half-extent (blocks) of the square window the feature scans for each column's snowfield reference (the
     * local WORLD_SURFACE maximum). 4 -> a 9x9 window, wide enough that any slot up to {@code 2*4=8} columns
     * across still sees open snowfield within it (so its columns light up as candidates), while a canyon
     * interior wider than that correctly sees no rim and stays open. Bounds the feature's per-column reference
     * work at {@code (2*R+1)^2} array reads.
     */
    public static final int REFERENCE_WINDOW_RADIUS = 4;

    /**
     * Deterministic fraction of eligible narrow spans that actually get roofed (per-span roll {@code < } this,
     * via the feature's own vanilla-seeded {@link java.util.Random}-equivalent RandomSource -- Art VI: no new
     * noise field, the vanilla-native per-placement stream is deterministic per seed+chunk). 0.40 -> a minority
     * of slots are trapped, so the field stays learnable and fair rather than a wall-to-wall minefield.
     */
    public static final float ROOF_FRACTION = 0.40f;

    /**
     * Is a column a trap candidate: does its local snowfield reference sit at least {@link
     * #MIN_SHAFT_DEPTH_BLOCKS} above the column's own surface (a deep air shaft cut through the snowfield)?
     * Pure integer compare; both arguments are absolute block-Y heightmap reads.
     *
     * @param columnSurfaceY  this column's WORLD_SURFACE height (a cut column reads its crevasse floor;
     *                        a water-ponded slot reads its water top, so filled slots -- shallow -- are
     *                        naturally excluded)
     * @param referenceSnowY  the local snowfield reference (windowed WORLD_SURFACE maximum around the column)
     */
    public static boolean isTrapCandidate(int columnSurfaceY, int referenceSnowY) {
        return referenceSnowY - columnSurfaceY >= MIN_SHAFT_DEPTH_BLOCKS;
    }

    /**
     * Detect the ROOFABLE spans in a 1-D candidate run (one row or column of the chunk's candidate mask): the
     * contiguous runs of {@code true} whose length is in {@code [1, }{@link #MAX_ROOF_SPAN_WIDTH}{@code ]}.
     * Longer runs (grand-canyon rims / wide mouths) are dropped -- they stay open. Each returned span is a
     * {@code {start, length}} pair, in ascending {@code start} order.
     *
     * <p>Pure and allocation-only; no randomness (the fraction roll is a separate {@link #shouldRoofSpan}
     * decision the caller makes per returned span). A {@code null} or empty input returns an empty list.
     */
    public static List<int[]> roofableSpans(boolean[] candidateRun) {
        List<int[]> spans = new ArrayList<>();
        if (candidateRun == null) {
            return spans;
        }
        int runStart = -1;
        for (int i = 0; i < candidateRun.length; i++) {
            if (candidateRun[i]) {
                if (runStart < 0) {
                    runStart = i;
                }
            } else if (runStart >= 0) {
                addIfRoofable(spans, runStart, i - runStart);
                runStart = -1;
            }
        }
        if (runStart >= 0) {
            addIfRoofable(spans, runStart, candidateRun.length - runStart);
        }
        return spans;
    }

    private static void addIfRoofable(List<int[]> spans, int start, int length) {
        if (length >= 1 && length <= MAX_ROOF_SPAN_WIDTH) {
            spans.add(new int[]{start, length});
        }
    }

    /**
     * The fraction gate: should a candidate span be roofed, given a uniform roll in {@code [0,1)} (the
     * feature supplies {@code random.nextFloat()})? True iff {@code 0 <= roll01 < }{@link #ROOF_FRACTION}.
     * A negative or out-of-range roll never roofs (defensive -- the safe direction is "leave the slot open").
     */
    public static boolean shouldRoofSpan(float roll01) {
        return roll01 >= 0.0f && roll01 < ROOF_FRACTION;
    }
}
