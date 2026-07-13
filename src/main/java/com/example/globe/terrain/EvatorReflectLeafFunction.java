package com.example.globe.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Phase 5 Slice B-6 (Teleport-Evator) P1 -- the PRODUCTION leaf-level terrain reflector. This is the promoted,
 * band-limited, per-world-gated successor of the throwaway spike {@code EvatorLeafReflectFunction} (which
 * reflected the WHOLE east half unconditionally for the mechanism proof). The spike files are retained,
 * untouched, as evidence; this is the shipping code.
 *
 * <h2>Why leaf level (mechanism "b", spike-proven)</h2>
 * Reflecting the {@code finalDensity} graph at its ROOT (mechanism "a") fails on the real chunk-fill path:
 * {@code NoiseChunk.NoiseInterpolator} only returns its interpolated slice value for its OWN
 * {@link FunctionContext}; a reflected root context trips a fallback to raw, un-interpolated evaluation, so the
 * east band comes out raw while the west band is interpolated (proven, design doc). Wrapping every
 * COORDINATE-BEARING LEAF individually -- strictly BELOW the interpolation markers -- fixes this: the
 * interpolator's own context reaches the marker un-reflected (so it stores/returns its slice normally), and the
 * reflection happens one level deeper, when the interpolator fills its slice by sampling genuine cell corners.
 * Those corner contexts flow into this reflector, which reflects {@code x -> -x} for EAST-band corners before
 * the underlying noise leaf samples. The slice fills with reflected-at-true-corner values and interpolation
 * runs identically on both sides -- a smooth (spike-measured &le; ~2-block-wobble) mirror.
 *
 * <h2>Band-limited + gated (the differences from the spike)</h2>
 * The spike reflected {@code blockX > 0} everywhere. Production consults {@link EvatorMirror#reflectEastBlock}
 * per sample: reflect ONLY when the column is in the EAST mirror band AND the evator is live for this world
 * ({@code EVATOR_V2_ENABLED && LatitudeBiomes.isEvatorActive()}). West band, interior, and non-evator worlds
 * pass through un-reflected -- byte-identical. The predicate is a couple of int ops over a memoized threshold.
 *
 * <h2>Once-only outermost flip (coherence, preserved from the spike)</h2>
 * When an EAST context ({@code x} in the east band) hits this reflector it flips to {@code -x} (a WEST
 * coordinate) and hands the reflected context to the wrapped leaf. If that leaf is itself a composite that
 * reads {@code ctx} directly AND has reflector-wrapped shift children (e.g. {@code ShiftedNoise}), the children
 * now see an ALREADY-west {@code x}, and {@link EvatorMirror#reflectEastBlock} returns {@code false} for them --
 * so along any root-to-leaf path the reflection fires AT MOST ONCE. Double-wrapping is harmless by construction.
 */
public final class EvatorReflectLeafFunction implements DensityFunction.SimpleFunction {

    /** Reflected {@link FunctionContext} -- negates blockX (mirror about the origin-centered border), keeps Y/Z. */
    private record ReflectedCtx(int blockX, int blockY, int blockZ) implements FunctionContext {
    }

    private final DensityFunction leaf;

    private final KeyDispatchDataCodec<EvatorReflectLeafFunction> codec =
            KeyDispatchDataCodec.of(MapCodec.unit(this));

    public EvatorReflectLeafFunction(DensityFunction leaf) {
        this.leaf = leaf;
    }

    @Override
    public double compute(FunctionContext ctx) {
        int blockX = ctx.blockX();
        if (EvatorMirror.reflectEastBlock(blockX)) {
            return leaf.compute(new ReflectedCtx(EvatorMirror.reflectBlockX(blockX), ctx.blockY(), ctx.blockZ()));
        }
        return leaf.compute(ctx);
    }

    // fillArray: inherit SimpleFunction's default (contextProvider.fillAllDirectly(array, this)) -- a per-cell
    // compute over the provider's genuine corner contexts. On the interpolator slice-fill path each corner is
    // reflected individually, so the slice buffer holds reflected-at-true-corner values and the interpolator
    // lerps them normally. The wrapped leaves (Noise/ShiftedNoise/ShiftA/ShiftB/BlendedNoise) are themselves
    // SimpleFunctions using this same default per-cell fillArray, so a NON-reflecting pass-through is numerically
    // identical to the bare leaf -- which is why a flag-on-but-captured-off world stays byte-identical.

    @Override
    public double minValue() {
        return leaf.minValue();
    }

    @Override
    public double maxValue() {
        return leaf.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return codec;
    }

    /**
     * Re-wrap the (possibly substituted) leaf so a later {@code mapAll} pass -- notably {@code NoiseChunk}'s own
     * marker-wrapping visitor -- recurses THROUGH this reflector rather than dropping it (same structural reason
     * {@link GeoTerrainBiasFunction#mapChildren} exists).
     */
    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        return new EvatorReflectLeafFunction(visitor.apply(leaf));
    }
}
