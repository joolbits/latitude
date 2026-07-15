package com.example.globe.core;

/**
 * Pure latitude-driven placement + surface math for Phase 5 Slice B-8 "Polar Barrens"
 * ({@code latitude.polarBarrens.enabled}). Peetsa 2026-07-14: past the vegetation-fade finish line the
 * deep polar cap should stop being a dirt-and-snow {@code snowy_plains} monoculture and become a
 * first-party frozen waste ({@code globe:polar_barrens}) -- snow blocks, snow carpet, powder-snow
 * pockets and ice, no dirt, its own name.
 *
 * <p>This class holds ONLY the decision math (mirroring {@link PolarVegetationFade} and
 * {@link com.example.globe.core.geo.EdgeOceanRamp}): a poleward {@link #barrensFraction01(double)} ramp
 * plus an {@link #isBarrens(double, double)} fray decision the caller feeds a coherent
 * {@code ValueNoise2D} sample, and a pure {@link #surfaceKind(double, double)} block-substitution
 * classifier. The coherent noise (Art VI -- never a hard ring/shelf) makes the barrens edge a natural
 * frayed fade rather than a straight cutoff line. NO {@code floorDiv}/cell-hash anywhere.
 *
 * <p>Zero Minecraft imports -- Core Logic layer, unit-testable in a plain JVM. The world-side wiring
 * ({@code LatitudeBiomes}) derives {@code |lat|} from the column Z, supplies the fray/pocket/patch
 * noise, and maps the surface kind onto block states; this class owns the ramp and the thresholds.
 *
 * <h2>Onset / band (owner decision, 2026-07-14)</h2>
 * The barrens ONSET defaults to {@link PolarVegetationFade#FULL_DEG} (86 deg) -- the exact latitude the
 * small-vegetation fade finishes, so the barrens begin where the grass ends and there is no visible
 * double transition (the owner's chosen "invisible seam"). It ramps to fully dominant by
 * {@code FULL_DEG} (88 deg). Both are live-tunable ({@code latitude.polarBarrens.onsetDeg} /
 * {@code .fullDeg}); the onset default is KEEP-SHARED with the veg fade's finish so tuning that dial
 * moves the seam coherently.
 *
 * <h2>Honesty notes (design sweep A6)</h2>
 * <ul>
 *   <li>The ramp is a smoothstep on a coherent noise fray, so the band is NOT 100% barrens until 88 deg:
 *       the barrens fraction is 0% at/below 86, ~50% at 87, ~84% at 87.5, and 100% at/above 88. Below 88
 *       there are still {@code snowy_plains} patches showing through (the owner accepted this as the
 *       price of the invisible seam).</li>
 *   <li>Placement rewrites ONLY inland {@code minecraft:snowy_plains}; coasts stay {@code snowy_beach}
 *       and rivers stay {@code frozen_river}, so "zero dirt" is an INLAND claim.</li>
 *   <li>The biome id {@code globe:polar_barrens} deliberately contains no {@code snow}/{@code ice} token,
 *       so path-substring cold classifiers ignore it (verified harmless today, noted for the future).</li>
 * </ul>
 */
public final class PolarBarrensBand {

    /**
     * Absolute latitude (deg) at/below which NO barrens appear (barrens fraction exactly 0.0, so those
     * columns are bitwise-untouched by the placement override). Defaults to
     * {@link PolarVegetationFade#FULL_DEG} (the veg-fade finish, 86 deg) so the barrens begin exactly
     * where the grass ends -- the owner's invisible seam. Live-tunable via
     * {@code -Dlatitude.polarBarrens.onsetDeg}; sourced from {@link LatitudeV2Flags} so the flag class
     * owns the one {@code System.getProperty} read (defensive parse, malformed -> default).
     */
    public static final double ONSET_DEG = LatitudeV2Flags.POLAR_BARRENS_ONSET_DEG;

    /**
     * Absolute latitude (deg) at/above which the barrens are fully dominant (fraction exactly 1.0 --
     * every non-mountain, non-ice-spike {@code snowy_plains} column becomes barrens). Defaults to 88 deg.
     * Clamped to at least {@code ONSET_DEG + 0.5} so the smoothstep denominator can never be zero or
     * negative even under a hostile {@code -D} pairing. Live-tunable via
     * {@code -Dlatitude.polarBarrens.fullDeg}.
     */
    public static final double FULL_DEG = Math.max(LatitudeV2Flags.POLAR_BARRENS_FULL_DEG, ONSET_DEG + 0.5);

    // --- Surface substitution thresholds (PolarBarrensSurfaceMixin) --------------------------------
    // Compared against INDEPENDENT coherent ValueNoise2D fields in [0,1). The pockets/patches are
    // coherent (blobby), never per-block confetti, so they read as discrete features you navigate --
    // and powder is capped WELL below a "death-carpet" (design 3.4: traversable, not lethal; leather
    // boots negate powder-snow sink, which is vanilla behavior needing no code here).

    /**
     * Powder-snow pocket keep-threshold: a barrens surface block becomes {@code powder_snow} where its
     * pocket-noise sample is {@code >=} this. 0.92 -> the top ~8% of the (coherent) pocket field, so
     * powder forms a small minority of hidden, walk-around-able traps rather than a lethal carpet.
     */
    public static final double POWDER_SNOW_KEEP_THRESHOLD = 0.92;

    /**
     * Ice sheet/patch keep-threshold: a barrens surface block (that did NOT become powder) turns to ice
     * where its patch-noise sample is {@code >=} this. 0.85 -> the top ~15% of the (coherent) patch
     * field; after the powder field takes precedence the net ice share is ~14%, leaving ~78% snow_block.
     */
    public static final double ICE_KEEP_THRESHOLD = 0.85;

    /** Surface-kind: leave the block unchanged (column is not barrens). */
    public static final int SURFACE_KIND_NONE = 0;
    /** Surface-kind: replace exposed dirt/grass with {@code snow_block} (the barrens default ground). */
    public static final int SURFACE_KIND_SNOW_BLOCK = 1;
    /** Surface-kind: replace with {@code powder_snow} (a coherent hidden pocket). */
    public static final int SURFACE_KIND_POWDER_SNOW = 2;
    /** Surface-kind: replace with ice (a coherent sheet/patch). */
    public static final int SURFACE_KIND_ICE = 3;

    /**
     * Vertical margin (blocks) below the column's world-surface heightmap within which a dirt/grass/
     * gravel write still counts as the barrens SURFACE SKIN and is eligible for substitution. The
     * surface rule's grass + dirt band is ~1+4-7 blocks deep, so 8 comfortably covers it (plus any
     * ore_dirt blob poking into the skin -- whitening those is consistent: they'd otherwise show the
     * moment the top is dug). Everything deeper is the LIVING UNDERGROUND (coordinator order
     * 2026-07-14: a player tunneling under the pole must find ores, dirt veins, gravel pockets, lakes
     * and geodes like anywhere else -- the shelter/frozen-wounds mechanics reward digging in), which
     * the barrens biome now decorates with snowy_plains' full ore/lake/spring/geode subset. Mirrors
     * {@link PolarVegetationFade#SURFACE_MARGIN} in spirit (5 there, for placements ON the surface;
     * 8 here, for the dirt band UNDER it).
     */
    public static final int SURFACE_SKIN_MARGIN_BLOCKS = 8;

    private PolarBarrensBand() {
    }

    /**
     * True when a block write at {@code y} is part of the column's exposed SURFACE SKIN -- i.e. eligible
     * for the barrens snow/powder/ice substitution -- given the column's two worldgen heightmaps:
     * {@code worldSurfaceY} ({@code WORLD_SURFACE_WG}: highest non-air, so fluids count) and
     * {@code oceanFloorY} ({@code OCEAN_FLOOR_WG}: highest motion-blocking, so fluids do NOT count).
     *
     * <p>Two clauses, both pure integer math:
     * <ul>
     *   <li><b>Land column only:</b> {@code worldSurfaceY - oceanFloorY <= 1}. In a water/lava column
     *       the two heightmaps split apart (surface = fluid top, floor = submerged ground), so this
     *       excludes ocean/river/pond floors -- their dirt/gravel (surface-rule floors,
     *       disk_sand/clay/gravel) stays native. The {@code <= 1} tolerance absorbs a thin
     *       non-motion-blocking top (e.g. a snow layer).</li>
     *   <li><b>Skin depth:</b> {@code y >= worldSurfaceY - }{@link #SURFACE_SKIN_MARGIN_BLOCKS}.
     *       Underground writes (ore_dirt / ore_gravel veins, deep disk edges) fall outside and stay
     *       native -- the underground is not part of the barrens identity.</li>
     * </ul>
     */
    public static boolean isSurfaceSkin(int y, int worldSurfaceY, int oceanFloorY) {
        if (worldSurfaceY - oceanFloorY > 1) {
            return false; // fluid column: the "floor" is submerged ground, not barrens skin
        }
        return y >= worldSurfaceY - SURFACE_SKIN_MARGIN_BLOCKS;
    }

    /**
     * Fraction of columns that should be barrens at the given (signed or unsigned) latitude degrees.
     * Monotonically NON-DECREASING in {@code |deg|}: exactly {@code 0.0} at/below {@link #ONSET_DEG}
     * (incl. the exact boundary, so those columns are bitwise-untouched), smoothstep-rising to exactly
     * {@code 1.0} at/above {@link #FULL_DEG}. A {@code NaN} input returns {@code 0.0} (never place barrens
     * on bad data -- the safe, byte-identical direction).
     */
    public static double barrensFraction01(double deg) {
        double a = Math.abs(deg);
        if (Double.isNaN(a)) {
            return 0.0;
        }
        if (a <= ONSET_DEG) {
            return 0.0;
        }
        if (a >= FULL_DEG) {
            return 1.0;
        }
        double t = (a - ONSET_DEG) / (FULL_DEG - ONSET_DEG);
        return t * t * (3.0 - 2.0 * t); // smoothstep 0->1
    }

    /**
     * Decide whether a column at {@code deg} should be a barrens column, given a coherent fray-noise
     * sample in {@code [0,1)}. A column IS barrens when {@code noise01 < }{@link #barrensFraction01(double)},
     * so the expected barrens fraction over a neighbourhood equals the ramp value there. Fraction
     * {@code 0.0} (at/below onset, or NaN) never yields barrens; fraction {@code 1.0} (the pole cap)
     * always does (since {@code noise01 < 1.0}).
     */
    public static boolean isBarrens(double deg, double noise01) {
        return noise01 < barrensFraction01(deg);
    }

    /**
     * The full flag-independent placement predicate for the {@code pick()} final override: rewrite the
     * chosen biome to {@code globe:polar_barrens} iff the current pick is {@code minecraft:snowy_plains}
     * (owner decision #3 mechanism: ice_spikes accents and real-mountain alpine picks are NOT snowy_plains,
     * so they survive by construction -- no explicit exclusion needed), the column is in the polar land
     * band, and it lands on the barrens side of the coherent fray at this latitude. Pure -- the caller
     * ANDs the {@code latitude.polarBarrens.enabled} flag in front so flag-off is byte-identical.
     */
    public static boolean overridesSnowyPlains(boolean pickIsSnowyPlains,
                                               boolean landBandIsPolar,
                                               double deg,
                                               double frayNoise01) {
        return pickIsSnowyPlains && landBandIsPolar && isBarrens(deg, frayNoise01);
    }

    /**
     * Classify the surface-block substitution for a KNOWN barrens column from two INDEPENDENT coherent
     * noise samples in {@code [0,1)}. Powder pockets take precedence over ice patches (a powder pocket
     * that overlaps an ice patch reads as the hidden trap, which is the gameplay point). Never returns
     * {@link #SURFACE_KIND_NONE} -- the caller only invokes this once it has confirmed the column is
     * barrens; non-barrens columns are filtered out before this is reached.
     *
     * @param powderNoise01 coherent pocket field; {@code >= }{@link #POWDER_SNOW_KEEP_THRESHOLD} -> powder
     * @param iceNoise01    coherent patch field; {@code >= }{@link #ICE_KEEP_THRESHOLD} -> ice (unless powder won)
     */
    public static int surfaceKind(double powderNoise01, double iceNoise01) {
        if (powderNoise01 >= POWDER_SNOW_KEEP_THRESHOLD) {
            return SURFACE_KIND_POWDER_SNOW;
        }
        if (iceNoise01 >= ICE_KEEP_THRESHOLD) {
            return SURFACE_KIND_ICE;
        }
        return SURFACE_KIND_SNOW_BLOCK;
    }
}
