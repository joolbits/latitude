package com.example.globe.core;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) -- the SINGLE SOURCE OF TRUTH for the mirror-band geometry, the sibling
 * of {@link EdgeGeometry}. Pure math, zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM).
 * Reuses {@link EdgeGeometry}'s degree/block conversion so the two axes never drift.
 *
 * <h2>The band (design "mirror geometry" section)</h2>
 * The outermost band of longitude at each E/W (X) edge is generated as a mirror of the opposite edge so that a
 * one-shot teleport across the seam lands in identical terrain. The band spans {@code [175 deg, 180 deg]}
 * (5 deg wide); its OUTER edge is the antimeridian wall (180 deg) and its INNER FRONTIER is at 175 deg -- the
 * C0-discontinuous join between mirrored band terrain and normal interior terrain that the storm fog exists to
 * hide. Two more lines live inside the band for the (P2) state machine:
 * <ul>
 *   <li>{@link #TRIGGER_DEG} 179.5 -- the one-shot fires as the player meets the wall (deeper than B-5's
 *       179-deg prompt), landing them at the mirrored edge in identical terrain.</li>
 *   <li>{@link #PRE_WARM_DEG} 177.0 -- the destination ring is force-loaded as the player enters here, so even
 *       a late chunk is a fog-masked non-event.</li>
 * </ul>
 *
 * <h2>Intended-radius anchored, symmetric, floored (inherited discipline)</h2>
 * Every distance is derived from the mod's OWN INTENDED X radius (NOT the live {@code WorldBorder} size -- the
 * TEST-86 lerping-border finding, {@link EdgeGeometry}'s reason for being), and the band is SYMMETRIC about the
 * world center: {@link #inBand} keys on {@code |x - centerX|}, so both the east ({@code +x}) and west
 * ({@code -x}) bands are identical (design amendment 3/4 -- a symmetric strip on both edges). On the smallest
 * world (Itty-Bitty Classic, xRadius 3750) one degree is only ~20.8 blocks, so a small-world FLOOR keeps the
 * trigger line off the wall and re-tightens the ordering {@code trigger < preWarm < frontier} exactly the way
 * {@link EdgeGeometry} floors its lines; the pure 5-deg band width holds un-floored on every larger world.
 *
 * <h2>Reflection, not translation (the load-bearing map)</h2>
 * {@link #reflect} is {@code 2*centerX - x} -- the reflection about the world center that makes BOTH walls line
 * up (east edge terrain falls away toward {@code -x}, west toward {@code +x}), and is exactly
 * {@link HemispherePassage#mirrorX}. An involution: {@code reflect(reflect(x)) == x}.
 *
 * <h2>Band-width table (5 deg, floored on tiny worlds)</h2>
 * <pre>
 *   Itty  Classic (xR 3750)  -> ~104 blk    Small Wide/Merc (15000) -> ~417 blk
 *   Small Classic (5000)     -> ~139 blk    Regular Wide/Merc (20000) -> ~555 blk
 *   Regular Classic (7500)   -> ~208 blk
 * </pre>
 */
public final class MirrorGeometry {

    private MirrorGeometry() {
    }

    // ---- degree anchors (longitude degrees from world center; the antimeridian edge is 180) ----

    /** The band's inner frontier (equatorward boundary) -- the masked mirror/interior seam. */
    public static final double BAND_INNER_DEG = 175.0;
    /** The antimeridian wall (the band's outer edge). */
    public static final double EDGE_DEG = 180.0;
    /** The one-shot crossing fires at/inside this (deeper than B-5's 179-deg prompt -- fire AT the wall). */
    public static final double TRIGGER_DEG = 179.5;
    /** The destination ring is force-loaded once the player is inside this (belt-and-suspenders pre-warm). */
    public static final double PRE_WARM_DEG = 177.0;

    // ---- block floors (small-world readability; same discipline as EdgeGeometry) ----

    /** The trigger line never sits closer than this to the wall, so the one-shot always has a little runway
     *  even on Itty-Bitty (1 deg = ~20.8 blk). Smaller than {@link EdgeGeometry#PROMPT_MIN_DIST_BLOCKS} (40)
     *  because the evator deliberately fires DEEPER than the B-5 prompt. */
    public static final double TRIGGER_MIN_DIST_BLOCKS = 24.0;
    /** The pre-warm line stays at least this far past the trigger so there is real force-load lead time. */
    public static final double PRE_WARM_MIN_GAP_BLOCKS = 32.0;
    /** Strict-ordering epsilon, shared with {@link EdgeGeometry#ORDER_MIN_STEP_BLOCKS}: when a floor pushes a
     *  nearer line out, the next line out is kept at least this much farther so
     *  {@code trigger < preWarm < frontier} never inverts or ties. */
    public static final double ORDER_MIN_STEP_BLOCKS = EdgeGeometry.ORDER_MIN_STEP_BLOCKS;

    /** Blocks per longitude degree for a given intended X radius -- shared with {@link EdgeGeometry}. */
    public static double blocksPerDegree(double xRadiusIntended) {
        return EdgeGeometry.blocksPerDegree(xRadiusIntended);
    }

    /** Distance-from-edge (blocks) for a longitude-degree anchor -- shared with {@link EdgeGeometry}. */
    public static double distForDeg(double deg, double xRadiusIntended) {
        return EdgeGeometry.distForDeg(deg, xRadiusIntended);
    }

    /** Distance (blocks, {@code >= 0}) from world-X {@code x} to the nearest E/W edge, measured against the
     *  INTENDED X radius -- shared with {@link EdgeGeometry}. */
    public static double distanceToEdge(double xRadiusIntended, double centerX, double x) {
        return EdgeGeometry.distanceToEdge(xRadiusIntended, centerX, x);
    }

    /** Reflection about the world center ({@code 2*centerX - x}) -- the map that makes both walls line up.
     *  Identical to {@link HemispherePassage#mirrorX}; an involution. */
    public static double reflect(double x, double centerX) {
        return 2.0 * centerX - x;
    }

    /**
     * The resolved, per-world block geometry: every distance is measured FROM the E/W edge (shrinks toward the
     * wall) and derived purely from the intended X radius, with the small-world floor applied and the strict
     * ordering {@code triggerDist < preWarmDist < frontierDist} guaranteed.
     *
     * @param xRadiusIntended the intended X radius the whole geometry is anchored to
     * @param triggerDist     distance-from-edge of the one-shot trigger line (nearest the wall)
     * @param preWarmDist     distance-from-edge of the destination-ring pre-warm line
     * @param frontierDist    distance-from-edge of the band's inner frontier (== the band width in blocks)
     */
    public record Resolved(double xRadiusIntended,
                           double triggerDist,
                           double preWarmDist,
                           double frontierDist) {

        /** Band width in blocks (== {@link #frontierDist()}): how deep the mirror band reaches inward. */
        public double bandWidthBlocks() {
            return frontierDist;
        }

        /** {@code |x - centerX|} of the band's inner frontier (the positive-side seam; the negative side is
         *  its mirror). Clamped non-negative for degenerate tiny radii. */
        public double frontierAbsX() {
            return Math.max(0.0, xRadiusIntended - frontierDist);
        }
    }

    /**
     * Resolve the block geometry for a world of the given intended X radius. Pure function of
     * {@code xRadiusIntended} ONLY (immune to a lerping/vandalized live border, same as {@link EdgeGeometry}).
     */
    public static Resolved resolve(double xRadiusIntended) {
        // Nearest-to-edge first, flooring then re-tightening the ordering outward so no floor can invert it.
        double trigger = Math.max(distForDeg(TRIGGER_DEG, xRadiusIntended), TRIGGER_MIN_DIST_BLOCKS);
        double preWarm = Math.max(distForDeg(PRE_WARM_DEG, xRadiusIntended), trigger + PRE_WARM_MIN_GAP_BLOCKS);
        double frontier = Math.max(distForDeg(BAND_INNER_DEG, xRadiusIntended), preWarm + ORDER_MIN_STEP_BLOCKS);
        return new Resolved(xRadiusIntended, trigger, preWarm, frontier);
    }

    /** Band width in blocks for a world of the given intended X radius (== {@code resolve(xR).frontierDist()}). */
    public static double bandWidthBlocks(double xRadiusIntended) {
        return resolve(xRadiusIntended).frontierDist();
    }

    /** {@code |x - centerX|} of the band's inner frontier (positive side) for a given intended X radius. */
    public static double frontierX(double xRadiusIntended) {
        return resolve(xRadiusIntended).frontierAbsX();
    }

    /**
     * True iff the column at world-X {@code x} lies within the mirror band of the nearest E/W (X) edge, i.e.
     * {@code xRadius - |x - centerX| <= bandWidth}. SYMMETRIC -- east ({@code +x}) and west ({@code -x}) bands
     * are identical. Degenerate geometry ({@code xRadius <= 0}) returns {@code false} (never in band), so a
     * bad border read fails open (the strips do nothing). A column at or beyond the edge is inside the band.
     */
    public static boolean inBand(double x, double centerX, double xRadiusIntended) {
        if (!(xRadiusIntended > 0.0)) {
            return false;
        }
        double distToEdge = xRadiusIntended - Math.abs(x - centerX);
        return distToEdge <= bandWidthBlocks(xRadiusIntended);
    }

    /**
     * True iff the column at world-X {@code x} lies in the EAST ({@code +x}) mirror band -- {@link #inBand} AND
     * on the {@code +x} side of {@code centerX}. Only the east band reflects (the WEST band is canonical, design
     * "copy direction"), so this is the predicate the production reflectors gate on. Equivalent to
     * {@code x >= frontierX(xRadius)} on the east side (the fast form the density-path predicate uses).
     */
    public static boolean inEastBand(double x, double centerX, double xRadiusIntended) {
        return x > centerX && inBand(x, centerX, xRadiusIntended);
    }
}
