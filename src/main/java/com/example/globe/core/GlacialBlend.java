package com.example.globe.core;

/**
 * Pure latitude-driven decision for the UNDERGROUND glacial identity -- Phase 5 Crew 7 Slice S28
 * "The Underground Glacial Blend" (Peetsa 2026-07-20: "Can we not blend? A transition?" ... "Yes, go
 * ahead with the blend build" -- a GRADUAL widening of the glacial underground, not a hard switch).
 *
 * <p><b>Why this exists (the S27 fray diagnosis, {@code docs/binder/phase5-b9-glacial-caves-design-20260719.md}):</b>
 * everything glacial UNDERGROUND (the {@code globe:glacial_caves} biome swap, the crevasse/tunnel carver
 * append, the {@code /latdev} locator) rode {@link PolarBarrensBand#isBarrens}, whose fray is the
 * chunk-scale (64-block) SURFACE barrens noise -- so directly at the 82-84 test latitudes the underground
 * flickered glacial/not-glacial in chunk-sized coin flips exactly where the owner keeps testing. This
 * class replaces that per-underground-family decision with a WIDE, coherent-REGION blend: the glacial
 * underground onsets far equatorward (78 deg) and densifies poleward to a full cap (86 deg), and the
 * per-column choice is driven by a LARGE-cell region field (see
 * {@code LatitudeBiomes.glacialBlendRegionNoise}, 640-block cells) so the transition reads as long natural
 * stretches of geography rather than chunk-confetti. The SURFACE barrens family (biome id, glacier body,
 * vegetation fade, water-freeze tick front) is DELIBERATELY untouched -- it keeps its existing 82-84 fray;
 * only the underground blends.
 *
 * <p>Zero Minecraft imports -- Core Logic layer, unit-testable in a plain JVM (mirrors
 * {@link PolarBarrensBand}). The world-side wiring ({@code LatitudeBiomes}) derives {@code |lat|} from the
 * column Z and supplies the region-noise sample; this class owns the onset/full band and the threshold.
 * NO {@code floorDiv}/cell-hash anywhere (Art VI) -- the coherence lives entirely in the caller's
 * {@code ValueNoise2D} region field.
 *
 * <h2>The band (owner decision, 2026-07-20)</h2>
 * A smoothstep threshold {@code t(|lat|)} rising over {@code [}{@link #BLEND_ONSET_DEG}{@code (78),
 * }{@link #BLEND_FULL_DEG}{@code (86)]}: at/below 78 the threshold is 0 (never glacial), at/above 86 it is
 * 1 (always glacial), and a column's underground is glacial iff its region-noise sample is BELOW the
 * threshold -- so the expected glacial fraction over a neighbourhood equals {@code t} there (0% at 78,
 * ~50% at 82, 100% at 86+). These are FIXED literals (not {@code -D}-tunable): the owner asked for this
 * specific wide transition, and keeping them constant makes the seam every consumer shares immovable.
 */
public final class GlacialBlend {

    /**
     * Absolute latitude (deg) at/below which the underground is NEVER glacial (threshold exactly 0.0, so
     * those columns are bitwise-untouched by the swap/append/locator). 78 deg -- deliberately FAR
     * equatorward of the surface barrens onset (82) so the underground glacial identity STARTS well before
     * the visible barrens and blends in gradually, the owner's "transition, not a hard switch".
     */
    public static final double BLEND_ONSET_DEG = 78.0;

    /**
     * Absolute latitude (deg) at/above which the underground is FULLY glacial (threshold exactly 1.0 --
     * every non-ocean land column is glacial underground). 86 deg -- an 8-degree-wide blend band, long
     * enough that the transition reads as coherent geography (with 640-block region cells) rather than a
     * chunk-scale flicker. Guaranteed strictly greater than {@link #BLEND_ONSET_DEG} so the smoothstep
     * denominator can never be zero.
     */
    public static final double BLEND_FULL_DEG = 86.0;

    private GlacialBlend() {
    }

    /**
     * The glacial-underground THRESHOLD at the given (signed or unsigned) latitude degrees: the region-noise
     * cutoff below which a column's underground is glacial, equal to the expected glacial areal fraction
     * there. Monotonically NON-DECREASING in {@code |deg|}: exactly {@code 0.0} at/below
     * {@link #BLEND_ONSET_DEG} (incl. the boundary, so those columns never swap), smoothstep-rising to
     * exactly {@code 1.0} at/above {@link #BLEND_FULL_DEG}. A {@code NaN} input returns {@code 0.0} (never
     * glacial on bad data -- the safe, byte-identical direction, mirroring
     * {@link PolarBarrensBand#barrensFraction01}).
     */
    public static double undergroundGlacialThreshold(double absLatDeg) {
        double a = Math.abs(absLatDeg);
        if (Double.isNaN(a)) {
            return 0.0;
        }
        if (a <= BLEND_ONSET_DEG) {
            return 0.0;
        }
        if (a >= BLEND_FULL_DEG) {
            return 1.0;
        }
        double t = (a - BLEND_ONSET_DEG) / (BLEND_FULL_DEG - BLEND_ONSET_DEG);
        return t * t * (3.0 - 2.0 * t); // smoothstep 0->1
    }

    /**
     * Decide whether the UNDERGROUND at a column of {@code absLatDeg}, given a coherent region-noise sample
     * in {@code [0,1)}, is glacial: {@code regionNoise01 < }{@link #undergroundGlacialThreshold(double)}.
     * So the expected glacial fraction over a neighbourhood equals the threshold there -- large coherent
     * patches that densify poleward. Threshold {@code 0.0} (at/below onset) never yields glacial;
     * {@code 1.0} (the cap) always does (since {@code regionNoise01 < 1.0}).
     *
     * <p>NaN idioms (both degrade toward a defined answer, never an exception): a {@code NaN} latitude
     * returns {@code false} outright (never place glacial on a bad column); a {@code NaN} region-noise
     * degrades to {@code 0.5} (mid-region -- so a bad noise sample inside the band still resolves the same
     * way a median region would, never no-glacial-on-bad-data past the midpoint). Pure, deterministic,
     * unit-testable.
     */
    public static boolean undergroundGlacial(double absLatDeg, double regionNoise01) {
        if (Double.isNaN(absLatDeg)) {
            return false;
        }
        double n = Double.isNaN(regionNoise01) ? 0.5 : regionNoise01;
        return n < undergroundGlacialThreshold(absLatDeg);
    }
}
