package com.example.globe.core;

/**
 * S13 (e) POLAR SURFACE ALLOWLIST (Peetsa, TEST-103 flight, 2026-07-17): in polar storm country
 * ({@code |lat| >= } the storm/ambient onset, {@link PolarHazardWindow#AMBIENT_ONSET_DEG} = 80 deg) the
 * SKY-EXPOSED surface is too hostile for the ordinary hostile menagerie -- only the STRAY (the polar
 * biome's own signature skeleton, which vanilla already restricts to open sky) belongs out on the ice.
 * Zombies / creepers / spiders / skeletons / etc. become CAVE-ONLY in the storm cap. This composes with
 * the polar-night 24/7 dark-surface rule (Solar Tilt): that rule keeps the surface "dark enough to spawn"
 * around the clock, and this allowlist then means only strays actually take that surface.
 *
 * <p>This pure core owns the decision math for {@code SpawnPlacementsPolarSurfaceMixin}. The mixin injects
 * at {@code SpawnPlacements.checkSpawnRules} HEAD -- the TYPE-aware seam, one level OUTSIDE the entity's own
 * spawn predicate (which is where {@code MonsterSolarSpawnMixin} rides {@code Monster.isDarkEnoughToSpawn}).
 *
 * <p><b>Composition order (documented):</b> this type+sky+lat allowlist runs FIRST -- a vetoed zombie never
 * reaches the darkness check at all; a surviving stray then still faces the solar darkness rule AND vanilla's
 * own {@code Stray} open-sky rule. The two rules are orthogonal and composable: this one gates TYPE, that one
 * gates DARKNESS. Both are first-line flag-gated so either can be switched off independently.
 *
 * <p>Zero Minecraft imports -- Core Logic layer, unit-testable in a plain JVM. The mixin supplies the derived
 * booleans (sky-exposed) and, for the stray thinning, a spawn-attempt RNG roll (never worldgen RNG -- the
 * mixin only rolls under the {@code NATURAL} spawn reason).
 */
public final class PolarSurfaceSpawns {

    /**
     * Absolute latitude (deg) at/above which the surface allowlist applies. Defaults to the storm/ambient
     * onset ({@link PolarHazardWindow#AMBIENT_ONSET_DEG} = 80) -- the allowlist begins exactly where polar
     * storm country begins (the first snow + first warning). Coupled SYMBOLICALLY to that constant: this is a
     * runtime spawn/immersion anchor, NOT worldgen, so -- unlike the village veto -- it is safe (and honest)
     * to chase the storm onset if it ever moves. Live-tunable via
     * {@code -Dlatitude.polarSurfaceSpawns.onsetDeg}; a malformed value degrades to the default.
     */
    public static final double ONSET_DEG = parseDegOrDefault(
            System.getProperty("latitude.polarSurfaceSpawns.onsetDeg"), PolarHazardWindow.AMBIENT_ONSET_DEG);

    /**
     * Stray surface-spawn thinning denominator: on the exposed polar surface only 1-in-{@value} stray spawn
     * attempts survive (the rest are vetoed), so the NON-barrens polar biomes -- {@code snowy_plains} on the
     * 82->84 barrens fray, real-mountain alpine, existing chunks, and barrens-flag-off worlds, all of which
     * list {@code stray} at weight 80 -- also thin toward the Barrens' own reduced stray glut (S13 (d) cut the
     * barrens spawner weight 80->27, ~2/3). 3 => ~2/3 fewer surface strays everywhere in the storm cap.
     */
    public static final int STRAY_SURFACE_KEEP_DENOM = 3;

    private PolarSurfaceSpawns() {
    }

    /**
     * True iff {@code absLatDeg} is in polar storm country ({@code |deg| >= }{@link #ONSET_DEG}). A NaN input
     * returns {@code false} -- never veto on a bad read (the safe, vanilla-preserving direction). Keys on
     * {@code Math.abs} so both hemispheres are covered.
     */
    public static boolean inPolarBand(double absLatDeg) {
        double a = Math.abs(absLatDeg);
        return !Double.isNaN(a) && a >= ONSET_DEG;
    }

    /**
     * {@code true} => VETO this non-stray monster spawn. Fires only for a sky-exposed monster in polar storm
     * country; caves (not sky-exposed) and sub-onset columns are never vetoed. The caller has already
     * confirmed the entity is a {@code MONSTER} category and is NOT a stray before calling this. No RNG.
     */
    public static boolean vetoesNonStrayMonster(double absLatDeg, boolean skyExposed) {
        return skyExposed && inPolarBand(absLatDeg);
    }

    /**
     * {@code true} => VETO this stray spawn (thinning). Fires for a sky-exposed stray in polar storm country
     * whose spawn-attempt roll (in {@code [0,}{@link #STRAY_SURFACE_KEEP_DENOM}{@code )}) is non-zero --
     * keeping 1-in-DENOM. The roll MUST come from the spawn attempt's own {@code RandomSource} and the caller
     * must roll ONLY under the {@code NATURAL} spawn reason so a chunk-generation (worldgen) RNG stream is
     * never touched. A roll outside {@code [0,DENOM)} still keeps only {@code roll == 0}, so it degrades safely.
     */
    public static boolean thinsStray(double absLatDeg, boolean skyExposed, int roll) {
        return skyExposed && inPolarBand(absLatDeg) && roll != 0;
    }

    private static double parseDegOrDefault(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            double v = Double.parseDouble(raw);
            return Double.isFinite(v) ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
