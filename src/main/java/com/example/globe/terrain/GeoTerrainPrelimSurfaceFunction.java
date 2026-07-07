package com.example.globe.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Slice C-2 companion wrapper for {@code NoiseRouter.preliminarySurfaceLevel} (field #11).
 *
 * <p><b>Why #11 is wrapped now after the locked design deliberately refused to.</b> The r1 design was
 * rejected for adding the same DENSITY-unit bias term to #11 (a block-Y-unit field) — a unit mismatch,
 * and correctly retired (LESSONS L16). The bathymetry regime changes the calculus: its carve target IS a
 * block-Y level, so {@code min(prelim, ceilY)} is unit-correct — and it is REQUIRED, not optional: the
 * first bathymetry gate run showed carved sea cavities not flooding (air pockets + perched aquifers under
 * the ocean), because the aquifer/fluid system distinguishes "open to the sea" from "underground cave"
 * via the preliminary surface, which still reported the old, uncarved height. Aligning #11 with the carve
 * ceiling makes the fluid system treat carved ocean as ocean.
 *
 * <p>The land side passes through untouched (the additive lift is small and density-unit; the historical
 * unit-mismatch rejection stands for that side). No carve (S==0, r==0, NoOp provider, land column) means
 * exact pass-through, preserving the S=0 byte-identity contract. The carve ceiling comes from
 * {@link GeoTerrainBiasFunction#carveCeilYOrInfinity}, the single source shared with the finalDensity
 * wrapper, so the two fields can never disagree about the target; the shared per-thread column memo makes
 * the second consumer effectively free.
 */
public final class GeoTerrainPrelimSurfaceFunction implements DensityFunction.SimpleFunction {

    private final DensityFunction delegate;

    private final KeyDispatchDataCodec<GeoTerrainPrelimSurfaceFunction> codec =
            KeyDispatchDataCodec.of(MapCodec.unit(this));

    public GeoTerrainPrelimSurfaceFunction(DensityFunction delegate) {
        this.delegate = delegate;
    }

    @Override
    public double compute(FunctionContext ctx) {
        // Delegate outside the safety net, mirroring GeoTerrainBiasFunction: its own failures propagate
        // exactly as they would unwrapped.
        double base = delegate.compute(ctx);
        try {
            double ceilY = GeoTerrainBiasFunction.carveCeilYOrInfinity(ctx.blockX(), ctx.blockZ());
            return Double.isInfinite(ceilY) ? base : Math.min(base, ceilY);
        } catch (Throwable t) {
            GeoTerrainBiasFunction.logBiasFailureOnce(t);
            return base;
        }
    }

    /**
     * min: the clamp can pull the reported surface down to {@code SEA_LEVEL − maxDepth} when it can bind
     * (same condition as the finalDensity wrapper's clamp regime); never below both that and the
     * delegate's own floor is unnecessary — a safe wide bound is the min of the two. max: min() never
     * raises, so the delegate's ceiling stands.
     */
    @Override
    public double minValue() {
        double maxDepth = Math.abs(com.example.globe.core.LatitudeV2Flags.TERRAIN_V2_STRENGTH)
                * Math.abs(com.example.globe.core.LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO) * 60.0;
        return maxDepth > 0.0 ? Math.min(delegate.minValue(), 63.0 - maxDepth) : delegate.minValue();
    }

    @Override
    public double maxValue() {
        return delegate.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return codec;
    }

    /** Same structural contract as GeoTerrainBiasFunction.mapChildren (design §9-R5): rewrap the visited child. */
    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        return new GeoTerrainPrelimSurfaceFunction(visitor.apply(delegate));
    }
}
