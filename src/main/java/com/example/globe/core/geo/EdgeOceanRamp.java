package com.example.globe.core.geo;

/**
 * Phase 5 Slice B-2 (Fix 1) -- pure threshold/ramp math for the latitude-aware EDGE OCEAN intent.
 *
 * <p>The projection X-edge (the east/west world border) otherwise paints ~50% ordinary land that a
 * player flying east hits guillotined by the border. Fix 1 biases the outer band toward the ocean
 * family so the edge reads as an intentional ocean moat. This class holds ONLY the decision math:
 * given the column's X-only edge term {@code edgeB} (= {@code smoothstep(EDGE_START,1,|x|/xRadius)},
 * exposed as {@code GeoSummary.projectionEdgeXOnly01()}) and a coherent fray-noise sample in
 * {@code [0,1]}, decide whether the column should become ocean-authority. Frayed on a per-column
 * noise field so the moat's coastline is jagged (Art VI -- no straight ring); the ocean share rises
 * smoothly with {@code edgeB}.
 *
 * <p>Zero Minecraft imports -- Core Logic layer, unit-testable in a plain JVM. The caller supplies
 * the fray noise (from {@code ValueNoise2D}) and the {@code edgeB} term; this class owns the ramp.
 *
 * <p><b>Bitwise flag-off / edgeB==0 guarantee:</b> {@link #oceanChance01(double)} returns exactly
 * {@code 0.0} for any {@code edgeB <= EDGE_OCEAN_START} (in particular {@code edgeB == 0}, i.e.
 * {@code |x| <= 0.80*xRadius} where the authority's edge term has not yet engaged), so
 * {@link #frayedEdgeOcean(double, double)} returns {@code false} there and no column flips.
 */
public final class EdgeOceanRamp {

    /**
     * edgeB below which no ocean push is applied. edgeB is {@code smoothstep(0.80,1,|x|/xR)}, so
     * edgeB==0 already means {@code |x| <= 0.80*xR}; this small positive onset keeps the innermost
     * sliver of the edge band (just past 0.80*xR, ~0.84*xR) essentially untouched so the moat fades
     * in rather than starting with a hard step.
     */
    public static final double EDGE_OCEAN_START = 0.10;

    /**
     * edgeB at/above which the ocean share reaches its cap. Chosen low enough that ~0.90*xR
     * (edgeB≈0.5) is already ocean-dominated: with START=0.10 and FULL=0.70 the ramp there is
     * ~0.70*MAX_SHARE.
     */
    public static final double EDGE_OCEAN_FULL = 0.70;

    /**
     * Maximum ocean flip share, capped below 1.0 on purpose: even at the very border a small frayed
     * fraction of land islands survives, so the moat is a frayed coast (Art VI / honest framing), not
     * a perfect uniform ring. 0.94 still drives the outer cells' land-biome share well under 10%.
     */
    public static final double EDGE_OCEAN_MAX_SHARE = 0.94;

    private EdgeOceanRamp() {
    }

    /**
     * Ocean flip probability in {@code [0, EDGE_OCEAN_MAX_SHARE]} for a column with the given X-only
     * edge term. Returns exactly {@code 0.0} for {@code edgeB <= EDGE_OCEAN_START} (bitwise-untouched
     * onset); ramps via a smoothstep from START to FULL and saturates at {@link #EDGE_OCEAN_MAX_SHARE}.
     */
    public static double oceanChance01(double edgeB) {
        if (!(edgeB > EDGE_OCEAN_START)) {
            return 0.0;
        }
        double t = clamp01((edgeB - EDGE_OCEAN_START) / (EDGE_OCEAN_FULL - EDGE_OCEAN_START));
        double s = t * t * (3.0 - 2.0 * t); // smoothstep
        return EDGE_OCEAN_MAX_SHARE * s;
    }

    /**
     * Decide whether a column at the projection X-edge should become ocean-authority. The
     * {@code frayNoise01} is a coherent per-column value in {@code [0,1]} (a province-scale
     * {@code ValueNoise2D} sample); a column flips when it falls below {@link #oceanChance01(double)},
     * so the expected flip fraction over a neighbourhood equals the ramp value there. Returns
     * {@code false} whenever the chance is {@code 0.0} (including {@code edgeB == 0}).
     */
    public static boolean frayedEdgeOcean(double edgeB, double frayNoise01) {
        double chance = oceanChance01(edgeB);
        if (chance <= 0.0) {
            return false;
        }
        return frayNoise01 < chance;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
