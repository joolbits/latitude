package com.example.globe.core;

/**
 * Phase 5 S10(a) -- the WORLD-SPAWN CALM BAND ("first spawn must never land in polar storm country -- you
 * gotta earn that", Peetsa 2026-07-16, TEST 99). Pure Java, zero Minecraft imports (Core Logic layer,
 * unit-testable in a plain JVM). The shim is {@code GlobeMod.resolveSpawnChoice}/{@code findLandSpawn},
 * which clamp their Z targets/search band through this class; land preference and X-axis logic are untouched.
 *
 * <p><b>The TEST 99 root cause this fixes (two-part).</b> The spawn picker always HAD a latitude preference
 * (zone fraction x Z radius), but its only poleward guard was BLOCK-anchored, not degree-anchored:
 * {@code maxAbsZ = zRadius - 256 - 500}. That band does not scale with the radius -- on the owner's
 * Regular-Wide world (zRadius 10000) it allowed spawns up to 83.2 deg, and it gets WORSE on bigger worlds
 * (86.6 deg at 20000). On top of that, the POLAR spawn-zone fraction (0.89 = 80.1 deg -- also reachable via
 * RANDOM's six-zone pool) sits exactly ON the S8 polar-country onset (80), so a POLAR/RANDOM-&gt;POLAR world
 * spawned the player INTO storm country on the first snowflake.
 *
 * <p><b>The law.</b> The world-spawn SEARCH is clamped to {@code |lat| <= }{@link #MAX_SPAWN_LAT_DEG}
 * (50 deg -- the Temperate/Subpolar boundary, a full 30 deg of calm before the 80-deg storm country), on
 * EVERY world shape and size, degree-anchored so it scales with the radius by construction. Consequence
 * (deliberate, S10a): the SUBPOLAR (65.25 deg) and POLAR (80.1 deg) spawn-zone fractions now saturate at
 * the 50-deg calm edge -- deep-cold starts are retired; you walk there. The 80-deg onset itself is KEPT
 * (owner ruling: the felt cheapness was the spawn, not the threshold).
 */
public final class SpawnCalmBand {

    private SpawnCalmBand() {
    }

    /** Maximum absolute spawn latitude (deg): the Temperate/Subpolar boundary. Every first-spawn Z target
     *  and every spawn-search jitter bound clamps inside this, all shapes, all radii. */
    public static final double MAX_SPAWN_LAT_DEG = 50.0;

    /** The calm band's |Z| ceiling in blocks for a world of the given Z (latitude) radius:
     *  {@code floor(zRadius * 50/90)}. Degree-anchored -- scales with the radius by construction (the block
     *  guard this replaces did not). Non-positive radius yields 0 (no band -- callers no-op). */
    public static int maxAbsZ(int zRadius) {
        if (zRadius <= 0) {
            return 0;
        }
        return (int) Math.floor(zRadius * (MAX_SPAWN_LAT_DEG / 90.0));
    }

    /** Clamp a spawn Z target into the calm band (symmetric about the equator). */
    public static int clampZ(int z, int zRadius) {
        int max = maxAbsZ(zRadius);
        return Math.max(-max, Math.min(max, z));
    }
}
