package com.example.globe.terrain;

import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.VoidTamingLaw;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S36 VOID TAMING density wrapper -- caps the SKY-BREACHING polar noise voids by pulling near-surface
 * underground AIR density toward solid, riding the Phase 4 terrain-wrapper rails ({@link
 * GeoTerrainBiasFunction} is the shipped precedent for every discipline used here). Installed by {@link
 * TerrainRouterWrapping} as the OUTERMOST layer of router field #12 ({@code finalDensity}); the
 * underground-vs-sky discriminator is the router's own {@code depth} field (#9), passed in UNWRAPPED at
 * install time -- cave-noise-free, positive strictly below the smooth nominal surface, negative in open
 * sky (verified vs the 26.2 jar; architect + sweep, 2026-07-21).
 *
 * <p><b>What it can and cannot touch (the two-sentence proof, S36).</b> The fill term is nonzero only
 * where the delegate says AIR ({@code base < 0}), the depth field says UNDERGROUND ({@code dv > 0} -- open
 * sky has {@code dv < 0}), the cell sits ABOVE the protect floor (feathered -- the glacial-cave labyrinth
 * and S35 trap deep-drops below it are untouchable), and the column is poleward of the S36 onset; every
 * other cell returns {@code delegate.compute(ctx)} verbatim. Carvers ({@code globe:crevasse},
 * {@code glacial_tunnels}) and features (trap covers) run AFTER noise, so carved air is re-cut through any
 * fill -- the sweep's crevasse-coexistence finding (6).
 *
 * <p><b>Install honesty (sweep REQUIRED-FIX 3):</b> this class is only ever constructed when
 * {@code VOID_TAMING_ENABLED && VOID_TAMING_STRENGTH > 0} ({@link TerrainRouterWrapping}'s gate), so
 * "strength 0 = exact no-op" holds by CONSTRUCTION (the wrapper is absent), not by proof. The in-compute
 * strength read below is therefore belt-and-suspenders symmetry with the precedent, never the primary gate.
 *
 * <p><b>Threading/interpolation:</b> {@link #mapChildren} is overridden per the precedent's R5 lesson --
 * NoiseChunk's {@code mapAll} visitor must reach BOTH children (the delegate graph AND the depth graph) so
 * chunk-scoped cache/interpolation nodes substitute correctly; the depth graph's own {@code flat_cache}
 * markers localize per chunk exactly like the router's #9 copy (sweep finding 7: benign double
 * localization, two independent 2D column caches).
 */
public final class VoidTamingFunction implements DensityFunction.SimpleFunction {

    private final DensityFunction delegate;
    private final DensityFunction depthDelegate;

    /** One-shot failure latch (per JVM; reset per world via {@link #resetLogLatchesForNewWorld}). */
    private static final AtomicBoolean COMPUTE_FAILED_LOGGED = new AtomicBoolean(false);

    /** Cached, never-serialized codec (precedent: built in-JVM at RandomState time, never data-round-tripped). */
    private final KeyDispatchDataCodec<VoidTamingFunction> codec =
            KeyDispatchDataCodec.of(MapCodec.unit(this));

    public VoidTamingFunction(DensityFunction delegate, DensityFunction depthDelegate) {
        this.delegate = delegate;
        this.depthDelegate = depthDelegate;
    }

    /** Chained from {@link TerrainRouterWrapping#resetLogLatchesForNewWorld()}. */
    public static void resetLogLatchesForNewWorld() {
        COMPUTE_FAILED_LOGGED.set(false);
    }

    @Override
    public double compute(FunctionContext ctx) {
        // The delegate is evaluated OUTSIDE the try (precedent discipline): its own failures are its own
        // story and must propagate exactly as they would un-wrapped.
        double base = delegate.compute(ctx);
        try {
            double k = LatitudeV2Flags.VOID_TAMING_STRENGTH;
            if (k <= 0.0 || !(base < 0.0)) {
                return base; // solid cell, or belt-and-suspenders strength gate: nothing to fill.
            }
            int y = ctx.blockY();
            int floor = LatitudeV2Flags.VOID_TAMING_PROTECT_FLOOR_Y;
            if (y <= floor) {
                return base; // the labyrinth below is untouchable, hard.
            }
            int radius = LatitudeBiomes.getActiveRadiusBlocks();
            if (radius <= 0) {
                return base; // not an armed globe world (defensive; install already gated on globe).
            }
            double absLat = Math.abs((double) ctx.blockZ()) * 90.0 / radius;
            double latGate = VoidTamingLaw.latGate(absLat,
                    LatitudeV2Flags.VOID_TAMING_ONSET_DEG, LatitudeV2Flags.VOID_TAMING_FULL_DEG);
            if (latGate <= 0.0) {
                return base;
            }
            double dv = depthDelegate.compute(ctx);
            double band = VoidTamingLaw.bandWeight(dv);
            if (band <= 0.0) {
                return base; // open sky (dv<=0) or deeper than the fade band -- both untouchable.
            }
            double floorF = VoidTamingLaw.floorFeather(y, floor,
                    LatitudeV2Flags.VOID_TAMING_FLOOR_FEATHER_BLOCKS);
            if (floorF <= 0.0) {
                return base;
            }
            return VoidTamingLaw.fillDensity(base, k, latGate * band * floorF);
        } catch (Throwable t) {
            if (COMPUTE_FAILED_LOGGED.compareAndSet(false, true)) {
                com.example.globe.GlobeMod.LOGGER.warn(
                        "[Latitude] S36 void-taming computation failed; passing through untamed density "
                                + "(logs once per world).", t);
            }
            return base;
        }
    }

    /**
     * The fill only ADDS density to negative (air) cells: the minimum can never drop below the delegate's.
     * The maximum exceeds the delegate's only when {@code K > 1} pushes a formerly-negative cell positive,
     * bounded by {@code (K-1) * |delegate.minValue()|} (architect formula 5, sweep-verified claim 4).
     */
    @Override
    public double minValue() {
        return delegate.minValue();
    }

    @Override
    public double maxValue() {
        double k = Math.max(0.0, LatitudeV2Flags.VOID_TAMING_STRENGTH);
        double overshoot = Math.max(0.0, k - 1.0) * Math.max(0.0, -delegate.minValue());
        return delegate.maxValue() + overshoot;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return codec;
    }

    /**
     * R5-precedent override (see {@link GeoTerrainBiasFunction#mapChildren}): NoiseChunk's {@code mapAll}
     * visitor must reach BOTH children so chunk-scoped cache/interpolation nodes substitute into the real
     * per-chunk graph; returning {@code this} (the SimpleFunction default) would hide them and desync the
     * generated blocks from the RandomState-time graph. Always construct anew -- visitors may run more
     * than once with different substitution behavior.
     */
    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        return new VoidTamingFunction(visitor.apply(delegate), visitor.apply(depthDelegate));
    }
}
