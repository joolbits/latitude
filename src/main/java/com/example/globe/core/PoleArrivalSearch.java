package com.example.globe.core;

import java.util.LinkedHashSet;

/**
 * Phase 5 Slice B-7 (Pole Passage) -- the PURE ordering + clamping logic for the over-the-pole arrival search.
 * Zero Minecraft imports (Core Logic layer, unit-testable in a plain JVM). The MC-coupled part (spending the
 * {@code placeSafeY} 3x3-FULL-ring probes, classifying biomes) lives in {@code HemispherePassageService}; this
 * class owns only the decisions that must be provable without a running server:
 * <ul>
 *   <li>{@link #xClampAbs} -- the corner X-clamp (sweep amendment A2): keep every arrival candidate equatorward
 *       of the E/W passage band so a corner-of-the-world pole crossing can never dump the player inside the EW
 *       prompt/fog band and stack ceremonies.</li>
 *   <li>{@link #candidateXs} -- the ordered ±X arrival-parallel search offsets, each already clamped, so the
 *       search "keeps latitude exactly, walks the arrival parallel" (the pole mirror of B-5's ±Z search).</li>
 *   <li>{@link #PREFERRED_PROBE_BUDGET} -- the medium-matching sub-budget: how many of the total probes the
 *       preferred-surface-class (land-vs-ocean) pass may spend before falling back to any-safe (design §6:
 *       mismatch beats no-op, safety is law).</li>
 * </ul>
 */
public final class PoleArrivalSearch {

    private PoleArrivalSearch() {
    }

    /** Extra clearance (blocks) the arrival clamp keeps EQUATORWARD of the E/W re-arm line, so a clamped pole
     *  arrival lands outside the entire EW ceremony (prompt/fog/re-arm) -- the EW arm stays ARMED, never
     *  self-prompts post-cross (sweep corner pin). */
    public static final double EW_MARGIN_BLOCKS = 64.0;

    /** ±X arrival-parallel search step (blocks): one chunk, matching the {@code placeSafeY} 3x3-ring granularity
     *  so consecutive probes overlap chunk loads (as B-5's ±Z search does). */
    public static final int ARRIVAL_SEARCH_STEP = 16;

    /** How many of the total probe budget the preferred-surface-class pass may spend before the search falls
     *  back to accepting ANY safe column (medium mismatch beats a failed crossing). 24 of the shared 40. */
    public static final int PREFERRED_PROBE_BUDGET = 24;

    /**
     * The corner X-clamp half-extent (A2): {@code xRadiusIntended - EW rearmDist - }{@link #EW_MARGIN_BLOCKS},
     * clamped {@code >= 0}. Every mirrored target and every ±X search candidate must satisfy
     * {@code |x - centerX| <= this}, so a pole crossing that mirrors a corner player (high |x| AND high |z|) can
     * never land them inside the EW passage band. Pure function of the intended X radius (reuses the pure
     * {@link EdgeGeometry#resolve}).
     */
    public static double xClampAbs(double xRadiusIntended) {
        double rearm = EdgeGeometry.resolve(xRadiusIntended).rearmDist();
        return Math.max(0.0, xRadiusIntended - rearm - EW_MARGIN_BLOCKS);
    }

    /** Clamp a single X coordinate to {@code [centerX - clampAbs, centerX + clampAbs]} (rounded to int). */
    public static int clampX(int x, double centerX, double clampAbs) {
        long lo = Math.round(centerX - clampAbs);
        long hi = Math.round(centerX + clampAbs);
        return (int) Math.max(lo, Math.min(hi, x));
    }

    /**
     * The ordered, clamped ±X arrival-parallel candidates, starting at {@code baseX} then alternating outward
     * ({@code baseX}, {@code baseX+step}, {@code baseX-step}, {@code baseX+2*step}, ...), each clamped to
     * {@code [centerX ± clampAbs]}, de-duplicated (clamping collapses out-of-range offsets onto the boundary),
     * capped at {@code maxCount} distinct columns. The caller probes these in order, applying its medium-class
     * preference / budget split. Pure -- the whole search order is provable without a server.
     */
    public static int[] candidateXs(int baseX, double centerX, double clampAbs, int step, int maxCount) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        int s = Math.max(1, step);
        out.add(clampX(baseX, centerX, clampAbs));
        // Expand outward until we have maxCount distinct clamped columns or the offsets exceed the clamp span.
        int span = (int) Math.ceil(2.0 * Math.max(0.0, clampAbs)) + s;
        for (int off = s; off <= span && out.size() < Math.max(1, maxCount); off += s) {
            out.add(clampX(baseX + off, centerX, clampAbs));
            if (out.size() >= Math.max(1, maxCount)) {
                break;
            }
            out.add(clampX(baseX - off, centerX, clampAbs));
        }
        int n = Math.min(out.size(), Math.max(1, maxCount));
        int[] arr = new int[n];
        int i = 0;
        for (int x : out) {
            if (i >= n) {
                break;
            }
            arr[i++] = x;
        }
        return arr;
    }
}
