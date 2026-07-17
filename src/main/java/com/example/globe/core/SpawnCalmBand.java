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
 *       zone id BEFORE this law runs, so it inherits the zone's window): lands in the polar band's LOWEST
 *       latitudes, the {@code [66.5, 70]}-deg window ({@link #POLAR_WINDOW_LO_DEG} aligned to
 *       {@code LatitudeBands.Band.POLAR.lowDeg()} = 66.5) -- the owner's words verbatim. The zone TARGET is
 *       the window midpoint, replacing the legacy 0.89 fraction (80.1 deg), which is now ILLEGAL (it sat
 *       exactly ON the S8 polar-country onset).</li>
 *   <li><b>Hard ceiling for EVERYONE</b>: storm country stays unspawnable -- no spawn ever at/above
 *       {@link #STORM_SPAWN_CEILING_DEG} (74). Chosen inside the recommended 72-75 range: a full SIX degrees
 *       of calm approach before the 80-deg polar-country onset (~250 blocks even on Itty, ~667 on
 *       Regular-Wide) and still under the 78-deg veg-fade onset, so even a ceiling-grazing spawn stands in
 *       living, green-flecked terrain and WALKS into the storm ("you gotta earn that"). Structurally
 *       enforced: every window's hi is clamped under it.</li>
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

    /** Explicit-POLAR window (deg): "only be spawned at the lowest latitude of polar" -- the band's low
     *  slice. LO aligned to the real {@code LatitudeBands} POLAR onset (66.5); alignment pinned by test. */
    public static final double POLAR_WINDOW_LO_DEG = 66.5;
    /** Explicit-POLAR window ceiling (deg). */
    public static final double POLAR_WINDOW_HI_DEG = 70.0;

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
     * garbage) gets the flat default band {@code [0, 50 deg]}. Every window's hi is additionally clamped
     * under the {@link #STORM_SPAWN_CEILING_DEG} everyone-ceiling (belt -- both windows already sit far
     * below it; the invariant is pinned by test).
     */
    public static Window spawnWindow(String zoneKey, int zRadius) {
        if (zRadius <= 0) {
            return new Window(0, 0);
        }
        int ceiling = degToAbsZ(STORM_SPAWN_CEILING_DEG, zRadius);
        double loDeg;
        double hiDeg;
        if ("SUBPOLAR".equals(zoneKey)) {
            loDeg = SUBPOLAR_WINDOW_LO_DEG;
            hiDeg = SUBPOLAR_WINDOW_HI_DEG;
        } else if ("POLAR".equals(zoneKey)) {
            loDeg = POLAR_WINDOW_LO_DEG;
            hiDeg = POLAR_WINDOW_HI_DEG;
        } else {
            loDeg = 0.0;
            hiDeg = MAX_SPAWN_LAT_DEG;
        }
        int lo = degToAbsZ(loDeg, zRadius);
        int hi = Math.min(degToAbsZ(hiDeg, zRadius), ceiling);
        return new Window(Math.min(lo, hi), hi);
    }

    /**
     * The zone's spawn TARGET |Z| (blocks): SUBPOLAR/POLAR target their window's MIDPOINT (52.5 / 68.25 deg
     * -- centered in the safe slice so the +-96-block search jitter has room on both sides before the window
     * clamp even engages); every other zone keeps its legacy fraction ({@code spawnFracForZoneKey}) clamped
     * into the default window -- behaviorally identical to the original flat-cap law for those zones.
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
