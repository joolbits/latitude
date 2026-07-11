package com.example.globe.core;

/**
 * Phase 5 performance-scaling helper -- pure, Minecraft-free particle-count scaling keyed off the
 * player's vanilla Particles video setting. Zero Minecraft imports (Core Logic layer, unit-testable
 * in a plain JVM); the tiny glue that reads the LIVE vanilla {@code ParticleStatus} and maps it onto
 * {@link Tier} lives in {@code GlobeModClient} (a trivial 1:1 mapping, not math).
 *
 * <p><b>Why this exists (Peetsa, perf concern):</b> the N/S polar ambient-snow blizzard spawns a
 * FIXED per-tick particle budget that ramps very heavy near the pole. If a player has turned their
 * vanilla Particles setting down for performance, the pole storm must honor that too or it can slow
 * their game right where the particle load is highest. This scales the polar snow budget down in
 * lock-step with the vanilla setting.
 *
 * <p><b>Vanilla tier count (disclosure):</b> vanilla Minecraft's {@code net.minecraft.server.level
 * .ParticleStatus} has exactly THREE tiers -- {@code ALL}, {@code DECREASED}, {@code MINIMAL} --
 * there is no separate "off" tier (verified against the mapped 26.2 jar). {@code MINIMAL} is the
 * lowest vanilla tier and is treated here as our floor: still a real (thin) blizzard, not literal
 * zero, because Peetsa wants the pole to always read as snowy.
 *
 * <p><b>Anti-backlog note:</b> this is a pure multiplicative scale of a count the caller already
 * computes fresh every spawn-tick. It holds no state, no counters, no accumulator -- calling
 * {@link #scale(Tier, int)} is a stateless function of its two arguments, so it cannot introduce any
 * per-frame/per-tick backlog. The caller's fixed-budget / {@code isPaused()} / spawn-tick guards are
 * untouched.
 */
public final class ParticleDensity {

    private ParticleDensity() {
    }

    /**
     * MC-neutral mirror of vanilla's three particle tiers (named {@code Tier} so it does not collide
     * with vanilla's {@code ParticleStatus}). {@link #FULL} == vanilla {@code ALL} (unchanged /
     * current behavior); {@link #MINIMAL} == vanilla {@code MINIMAL} == our lowest, thinnest floor.
     */
    public enum Tier {
        /** Vanilla ALL: no reduction, identical to the pre-scaling behavior. */
        FULL(1.0),
        /** Vanilla DECREASED: roughly half the particles. */
        DECREASED(0.5),
        /** Vanilla MINIMAL: a low floor -- still recognizably snowy at the pole, a real perf win. */
        MINIMAL(0.15);

        private final double multiplier;

        Tier(double multiplier) {
            this.multiplier = multiplier;
        }

        /** The multiplicative particle-budget factor for this tier, in {@code [0,1]}. */
        public double multiplier() {
            return multiplier;
        }
    }

    /**
     * Scales a fixed per-tick particle {@code count} by the given {@code tier}'s multiplier, rounded
     * to the nearest whole particle and clamped so it never goes below 0. {@code count == 0} is not
     * special-cased: zero in stays zero out. This is a pure function of its arguments (no state).
     *
     * @param tier  the density tier (from the live vanilla Particles setting)
     * @param count the unscaled fixed per-tick budget the caller computed this spawn-tick
     * @return the scaled budget, {@code >= 0}
     */
    public static int scale(Tier tier, int count) {
        if (count <= 0) {
            return 0;
        }
        long scaled = Math.round(count * tier.multiplier());
        if (scaled < 0L) {
            return 0;
        }
        return (int) scaled;
    }
}
