package com.example.globe.core;

/**
 * TEST 78 -- pure, Minecraft-free math for the CONTINUOUS enclosure estimate ({@code exposure01}) that
 * replaces the old binary sky-exposure bit for the polar-storm PRESENTATION systems (Core Logic layer,
 * unit-testable in a plain JVM; the actual sky sampling is a thin client shim in {@code GlobeClientState}).
 *
 * <p><b>Why this exists.</b> Every polar presentation system used to key off {@code GlobeClientState
 * .isSurfaceOk} ~= {@code world.canSeeSky(pos.above())} -- a BINARY single-column check. One block over the
 * player's head flipped them fully "indoors": standing under Peetsa's open freestanding arch (two pillars +
 * a flat lintel over open terrain) muffled the wind, cut the particles, and dropped the whiteout, even though
 * he was outdoors in every meaningful sense. The fix is a graded estimate: the client samples {@code canSeeSky}
 * at a small grid of offsets around the player's head, and {@code exposure01 = seen / total}. Under the arch
 * most samples still see sky -> {@code exposure01 ~= 0.9} (effectively outdoors); in a sealed room -> {@code 0}
 * (gated off as before); at an open doorway -> intermediate. This class holds the pure blend CURVES each
 * consumer applies to that fraction. (The wind muffle curve lives in {@link PolarWindSound} beside its own
 * shelter-scale constant; whiteout + particle-budget curves are here.)
 *
 * <p><b>No state / no accumulator.</b> Every method is a pure function of its arguments, so the B-3b
 * anti-backlog law is untouched -- scaling a fixed per-tick budget by {@code exposure01} introduces no counter
 * to "catch up" on resume.
 */
public final class PolarExposure {

    private PolarExposure() {
    }

    /** Number of sky samples the client shim takes around the player's head (documented here so the pure
     *  {@link #fraction(int, int)} contract and the shim's offset table stay in sync). */
    public static final int SAMPLE_COUNT = 13;

    /**
     * Enclosure estimate in {@code [0,1]} from a sky-sample tally: {@code seen / total}. {@code total <= 0}
     * defensively returns {@code 1.0} (treat "no samples" as fully exposed rather than falsely indoors -- the
     * shim never passes 0, but a caller must not accidentally silence the whole storm on a bad count).
     */
    public static float fraction(int seen, int total) {
        if (total <= 0) {
            return 1.0f;
        }
        int s = seen < 0 ? 0 : (seen > total ? total : seen);
        return (float) s / (float) total;
    }

    /**
     * Whiteout-overlay alpha scale for an exposure: LINEAR in {@code exposure01} (clamped to {@code [0,1]}).
     * The flat screen-space whiteout fill is a "you are engulfed" close-in top-coat; it should be full out in
     * the open ({@code exposure 1}), absent in a sealed room ({@code 0}), and partial at a doorway. Linear so
     * a doorway reads proportionally, matching the wind muffle and particle budget.
     */
    public static float whiteoutScale(float exposure01) {
        return clamp01(exposure01);
    }

    /**
     * Scales a fixed per-tick particle {@code base} budget by an exposure, rounded to the nearest whole
     * particle, clamped {@code >= 0}. Full storm out in the open, nothing in a sealed box, proportional at a
     * doorway. Pure function (no state) -- composes with the {@link ParticleDensity} perf scale without adding
     * any accumulator.
     */
    public static int particleBudget(int base, float exposure01) {
        if (base <= 0) {
            return 0;
        }
        long scaled = Math.round(base * clamp01(exposure01));
        return scaled < 0L ? 0 : (int) scaled;
    }

    /**
     * B-5 item 3 (TEST 83 "the warning pops out of view whenever you are under anything, like a tree").
     * Exposure below which a column is treated as "genuinely below the surface" by the storm PRESENTATION
     * systems -- the SAME {@code seaLevel - 2} margin the enclosure sampler ({@code GlobeClientState
     * .sampleExposure01}) already short-circuits on. Centralised here so the client freeze/gate and the server
     * cross-reject (item 2) share ONE definition of "underground" instead of re-inventing it.
     */
    public static final int SURFACE_DEPTH_MARGIN = 2;

    /**
     * True iff block-Y {@code y} is genuinely below the surface layer: {@code y < seaLevel - }{@link
     * #SURFACE_DEPTH_MARGIN}. Pure half of the "deep underground" test (item 2); callers AND this with an
     * actual sky check ({@code !canSeeSky}) so open low-lying terrain a couple blocks under sea level is not
     * mistaken for a cave. Matches {@code isSurfaceOk}/{@code sampleExposure01}'s existing depth cut exactly.
     */
    public static boolean isBelowSurface(int y, int seaLevel) {
        return y < seaLevel - SURFACE_DEPTH_MARGIN;
    }

    /**
     * B-5 item 3: visibility scale in {@code [0,1]} for the WARNING-TEXT family (the polar + E/W storm banners)
     * and its punctuation VIGNETTE, as a function of the graded enclosure estimate {@code exposure01}. Replaces
     * the old BINARY {@code surfaceOk} gate that let ONE block/leaf overhead fully hide the banners.
     *
     * <p>Full ({@code 1.0}) at {@code exposure01 >= }{@link #WARNING_FULL_EXPOSURE} (0.5) -- comfortably met
     * under a tree or Peetsa's open arch, where the center sample is blocked but the ring still sees sky
     * ({@code exposure ~0.9}). LINEAR fade below 0.5, reaching 0 as {@code exposure01 -> 0}, so the banners are
     * hidden ONLY when genuinely sealed in / deep underground (where the sampler returns {@code exposure01 == 0}
     * via {@link #isBelowSurface}'s short-circuit -- the same "no storm banners in a cave" rule item 2's
     * surface-only passage uses). A doorway (~0.3) reads at ~60% -- present but dimmed, matching the wind/
     * whiteout/particle systems that already fade with partial shelter.
     */
    public static final float WARNING_FULL_EXPOSURE = 0.5f;

    public static float warningAlpha(float exposure01) {
        float e = clamp01(exposure01);
        if (e >= WARNING_FULL_EXPOSURE) {
            return 1.0f;
        }
        return e / WARNING_FULL_EXPOSURE;
    }

    /** Clamp a float to {@code [0,1]} (NaN -> 0). */
    public static float clamp01(float v) {
        if (Float.isNaN(v) || v < 0.0f) {
            return 0.0f;
        }
        return v > 1.0f ? 1.0f : v;
    }
}
