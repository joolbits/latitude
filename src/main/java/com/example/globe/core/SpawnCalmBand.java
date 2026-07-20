package com.example.globe.core;

/**
 * Phase 5 S10(a) -- the WORLD-SPAWN CALM BAND, ZONE-AWARE since the owner's correction (Peetsa 2026-07-17:
 * "you completely neutered the spawn zone of 'polar' that I chose. Player should still be able to initially
 * spawn in polar; however, they should only be spawned at the lowest latitude of polar"). Pure Java, zero
 * Minecraft imports (Core Logic layer, unit-testable in a plain JVM). The shim is
 * {@code GlobeMod.resolveSpawnChoice}/{@code findLandSpawn}, which take their Z target and jitter bounds from
 * this class; land preference and X-axis logic are untouched.
 *
 * <h2>The zone-aware law (owner revision folded over the original flat cap)</h2>
 * <ul>
 *   <li><b>Default spawns + non-polar zones</b> (TEMPERATE default, EQUATOR/TROPICAL/SUBTROPICAL, and a
 *       RANDOM roll landing on any of them): the original degree-anchored flat cap stands --
 *       {@code |lat| <= }{@link #MAX_SPAWN_LAT_DEG} (50).</li>
 *   <li><b>Explicit SUBPOLAR pick</b>: consent to the cold -- lands in subpolar's LOW edge, the
 *       {@code [50, 55]}-deg window ({@link #SUBPOLAR_WINDOW_LO_DEG} aligned to the real band constant,
 *       {@code LatitudeBands.Band.SUBPOLAR.lowDeg()} = 50; alignment pinned by test).</li>
 *   <li><b>Explicit POLAR pick</b> (and a RANDOM roll that selects POLAR -- RANDOM resolves to a concrete
 *       zone id BEFORE this law runs, so it inherits the zone's window): lands UNIFORMLY anywhere in the
 *       {@code [66.5, 79]}-deg approach band ({@link #POLAR_WINDOW_LO_DEG} aligned to
 *       {@code LatitudeBands.Band.POLAR.lowDeg()} = 66.5; {@link #POLAR_WINDOW_HI_DEG} = 79). WIDENED from the
 *       old {@code [66.5, 70]} midpoint pick (owner S25, TEST 117: "random for polar between the boundary of
 *       polar and subpolar and... maybe seventy nine. Because it's quite a long distance from beginning of
 *       polar to polar storm country"). The zone TARGET is a SEEDED UNIFORM pick across the window
 *       ({@link #spawnTargetAbsZ(String, double, int, double)}, driven by the picker's existing {@code hash01}
 *       idiom), so each seed lands at a different point on the long walk to the storm. The legacy 0.89 fraction
 *       (80.1 deg) stays ILLEGAL (it sat exactly ON the S8 polar-country onset; 79 is one degree below it).</li>
 *   <li><b>Ceilings -- 74 for everyone, 79 for the POLAR expedition</b>: storm country stays unspawnable for
 *       the default/temperate/subpolar/tropical picks -- no such spawn ever at/above
 *       {@link #STORM_SPAWN_CEILING_DEG} (74; a full SIX degrees of calm before the 80-deg onset). The EXPLICIT
 *       POLAR pick is deliberate cold consent (the create screen warns), so it -- and only it -- may reach its
 *       own {@link #POLAR_SPAWN_CEILING_DEG} (79), one degree under the onset (owner S25). Both are
 *       SPAWN-PLACEMENT caps only; neither gates any live storm mechanic. Structurally enforced: each window's
 *       hi is clamped under its OWN zone ceiling.</li>
 * </ul>
 *
 * <p><b>The original TEST 99 root cause (kept for the record):</b> the pre-S10a guard was BLOCK-anchored
 * ({@code zRadius - 756} = 83.2 deg on Regular-Wide, worse on bigger worlds) and the POLAR fraction (0.89 =
 * 80.1 deg) sat exactly ON the storm-country onset. Both remain impossible under the window law.
 */
public final class SpawnCalmBand {

    private SpawnCalmBand() {
    }

    /** Maximum absolute spawn latitude (deg) for DEFAULT spawns and non-polar zones: the Temperate/Subpolar
     *  boundary. Degree-anchored so it scales with the radius by construction (the block guard the original
     *  S10a replaced did not). */
    public static final double MAX_SPAWN_LAT_DEG = 50.0;

    /** The hard everyone-ceiling (deg): no first spawn at/above this, ever, any zone (74 -- inside the
     *  recommended 72-75; see the class doc for the rationale). */
    public static final double STORM_SPAWN_CEILING_DEG = 74.0;

    /** Explicit-SUBPOLAR window (deg): the band's LOW edge. LO aligned to the real {@code LatitudeBands}
     *  SUBPOLAR onset (50); alignment pinned by test. */
    public static final double SUBPOLAR_WINDOW_LO_DEG = 50.0;
    /** Explicit-SUBPOLAR window ceiling (deg). */
    public static final double SUBPOLAR_WINDOW_HI_DEG = 55.0;

    /** Explicit-POLAR window floor (deg): "the boundary of polar and subpolar" -- the band's low edge. LO
     *  aligned to the real {@code LatitudeBands} POLAR onset (66.5); alignment pinned by test. */
    public static final double POLAR_WINDOW_LO_DEG = 66.5;
    /** Explicit-POLAR window ceiling (deg). WIDENED 70 -> 79 (owner S25, TEST 117 flight 2026-07-20: "random
     *  for polar between the boundary of polar and subpolar and... maybe seventy nine. Because it's quite a
     *  long distance from beginning of polar to polar storm country"). The POLAR pick is now UNIFORM across
     *  the whole [66.5, 79] approach band (the seeded pick in {@link #spawnTargetAbsZ(String, double, int,
     *  double)}, not the old midpoint), so different seeds land at different points along the long walk to the
     *  80-deg storm country. Wood scarcity above ~76 is accepted as the expedition fantasy -- the create
     *  screen already warns for a POLAR pick. 79 sits ONE degree under the 80-deg polar-country onset, so even
     *  a ceiling-grazing spawn still stands OUTSIDE the storm and walks in. */
    public static final double POLAR_WINDOW_HI_DEG = 79.0;

    /** The POLAR zone's OWN spawn ceiling (deg): 79, the owner's expedition override of the {@link
     *  #STORM_SPAWN_CEILING_DEG} everyone-ceiling (74). An explicit POLAR pick is deliberate cold consent, so
     *  it is the ONE zone permitted above the everyone-ceiling; every other zone keeps the 74 cap. Split out
     *  from the everyone-ceiling (owner S25) so widening the POLAR reach does not touch what 74 protects for
     *  the default/temperate/subpolar/tropical picks. NB: 74/79 are SPAWN-PLACEMENT caps only -- neither
     *  gates any live storm/hazard mechanic (the real polar-country onset is
     *  {@link PolarHazardWindow#AMBIENT_ONSET_DEG}, 80); they exist purely to keep a FIRST spawn out of / near
     *  the wall. */
    public static final double POLAR_SPAWN_CEILING_DEG = 79.0;

    /** A zone's allowed |Z| spawn window in blocks (inclusive bounds). Default zones: {@code [0, 50 deg]}. */
    public record Window(int loAbsZ, int hiAbsZ) {
    }

    /** Blocks for an absolute latitude degree at this radius ({@code floor(zRadius * deg/90)}). */
    private static int degToAbsZ(double deg, int zRadius) {
        return (int) Math.floor(zRadius * (deg / 90.0));
    }

    /** The calm band's |Z| ceiling in blocks for DEFAULT/non-polar spawns ({@code floor(zRadius * 50/90)}).
     *  Non-positive radius yields 0 (no band -- callers no-op). */
    public static int maxAbsZ(int zRadius) {
        if (zRadius <= 0) {
            return 0;
        }
        return degToAbsZ(MAX_SPAWN_LAT_DEG, zRadius);
    }

    /**
     * The allowed spawn window for a zone pick at this radius. SUBPOLAR/POLAR get their band-edge windows
     * (explicit pick = consent to the cold); every other key (TEMPERATE/EQUATOR/TROPICAL/SUBTROPICAL, null,
     * garbage) gets the flat default band {@code [0, 50 deg]}. Each window's hi is clamped under its ZONE's
     * ceiling: POLAR under {@link #POLAR_SPAWN_CEILING_DEG} (79, the owner's expedition override), every other
     * zone under the {@link #STORM_SPAWN_CEILING_DEG} everyone-ceiling (74). The split (owner S25) lets the
     * POLAR pick reach 79 without changing what 74 protects for the non-polar picks (whose windows already sit
     * far below it; those invariants are pinned by test).
     */
    public static Window spawnWindow(String zoneKey, int zRadius) {
        if (zRadius <= 0) {
            return new Window(0, 0);
        }
        double loDeg;
        double hiDeg;
        double ceilingDeg;
        if ("SUBPOLAR".equals(zoneKey)) {
            loDeg = SUBPOLAR_WINDOW_LO_DEG;
            hiDeg = SUBPOLAR_WINDOW_HI_DEG;
            ceilingDeg = STORM_SPAWN_CEILING_DEG;
        } else if ("POLAR".equals(zoneKey)) {
            loDeg = POLAR_WINDOW_LO_DEG;
            hiDeg = POLAR_WINDOW_HI_DEG;
            ceilingDeg = POLAR_SPAWN_CEILING_DEG; // the ONE zone allowed above the 74 everyone-ceiling (S25)
        } else {
            loDeg = 0.0;
            hiDeg = MAX_SPAWN_LAT_DEG;
            ceilingDeg = STORM_SPAWN_CEILING_DEG;
        }
        int ceiling = degToAbsZ(ceilingDeg, zRadius);
        int lo = degToAbsZ(loDeg, zRadius);
        int hi = Math.min(degToAbsZ(hiDeg, zRadius), ceiling);
        return new Window(Math.min(lo, hi), hi);
    }

    /**
     * The zone's spawn TARGET |Z| (blocks): SUBPOLAR targets its window's MIDPOINT (52.5 deg -- centered in
     * the safe slice so the +-96-block search jitter has room on both sides); POLAR targets the MIDPOINT of
     * its widened window (equivalent to {@link #spawnTargetAbsZ(String, double, int, double)} with
     * {@code frac01 == 0.5}); every other zone keeps its legacy fraction ({@code spawnFracForZoneKey}) clamped
     * into the default window -- behaviorally identical to the original flat-cap law for those zones.
     *
     * <p>The picker (server {@code resolveSpawnChoice}) now calls the SEEDED overload for POLAR so the pick is
     * UNIFORM across [66.5, 79] (owner S25) rather than always landing at the midpoint; this 3-arg form is the
     * midpoint idiom kept for SUBPOLAR / the non-polar zones and for the legacy/documentation tests.
     */
    public static int spawnTargetAbsZ(String zoneKey, double legacyZoneFrac, int zRadius) {
        if (zRadius <= 0) {
            return 0;
        }
        Window w = spawnWindow(zoneKey, zRadius);
        if ("SUBPOLAR".equals(zoneKey) || "POLAR".equals(zoneKey)) {
            return (w.loAbsZ() + w.hiAbsZ()) / 2;
        }
        int legacy = (int) Math.round(zRadius * legacyZoneFrac);
        return Math.max(w.loAbsZ(), Math.min(w.hiAbsZ(), legacy));
    }

    /**
     * The zone's spawn TARGET |Z| (blocks) with a seeded fraction -- the S25 UNIFORM POLAR pick (owner TEST
     * 117: "random for polar between the boundary of polar and subpolar and... maybe seventy nine"). For POLAR
     * the {@code frac01} (a deterministic 0..1 value the picker draws from its existing seeded {@code hash01}
     * idiom -- NOT a new RNG channel) maps LINEARLY across the whole [loAbsZ, hiAbsZ] = [66.5, 79] window, so
     * each seed spawns at a different point along the long approach walk instead of all seeds clustering at the
     * midpoint. {@code frac01} is clamped to [0, 1] (NaN -> 0.5, the midpoint -- the conservative centre). All
     * OTHER zones ignore {@code frac01} and delegate to the 3-arg {@link #spawnTargetAbsZ(String, double, int)}
     * (SUBPOLAR midpoint, non-polar legacy fraction) -- their placement is byte-identical to before S25.
     */
    public static int spawnTargetAbsZ(String zoneKey, double legacyZoneFrac, int zRadius, double frac01) {
        if (zRadius <= 0) {
            return 0;
        }
        if (!"POLAR".equals(zoneKey)) {
            return spawnTargetAbsZ(zoneKey, legacyZoneFrac, zRadius);
        }
        Window w = spawnWindow("POLAR", zRadius);
        double f = Double.isNaN(frac01) ? 0.5 : (frac01 < 0.0 ? 0.0 : (frac01 > 1.0 ? 1.0 : frac01));
        return w.loAbsZ() + (int) Math.round(f * (w.hiAbsZ() - w.loAbsZ()));
    }

    /** Clamp a SIGNED spawn Z into the zone window, preserving hemisphere: {@code |z|} clamps into
     *  {@code [lo, hi]} and the sign of {@code z} is kept ({@code z == 0} resolves to {@code +lo}, reachable
     *  only for default windows whose lo is 0). The ONE clamp both the zone target and the
     *  {@code findLandSpawn} jitter pass through -- jitter cannot escape the window in either direction. */
    public static int clampToWindow(int z, Window w) {
        int abs = Math.max(w.loAbsZ(), Math.min(w.hiAbsZ(), Math.abs(z)));
        return z >= 0 ? abs : -abs;
    }

    /** Legacy flat clamp (the default-zone law), kept for the original S10a tests/documentation. */
    public static int clampZ(int z, int zRadius) {
        int max = maxAbsZ(zRadius);
        return Math.max(-max, Math.min(max, z));
    }
}
