package com.example.globe.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * ============================ SPIKE — THROWAWAY PROOF CODE (B-6 P1 STEP 0, mechanism "b") ============================
 *
 * <p>The leaf-level reflector. Where mechanism (a) ({@link EvatorMirrorSpikeFunction}) wrapped the WHOLE
 * {@code finalDensity} at its ROOT — above the interpolation markers, where a reflected {@link FunctionContext}
 * trips {@code NoiseChunk.NoiseInterpolator}'s foreign-context fallback to RAW noise (refuted, see the design
 * doc) — mechanism (b) wraps every COORDINATE-BEARING LEAF individually, at install time, via
 * {@link EvatorSpike#installMirrorLeaf}. Because a leaf reflector sits strictly BELOW the marker/interpolator
 * layer (markers are inserted later by {@code NoiseChunk.wrapNew}, which only transforms {@code Marker}
 * nodes), the interpolator's own context — the {@code NoiseChunk} itself — reaches the marker UN-reflected,
 * so the interpolator returns/stores its slice normally. The reflection happens one level deeper, when the
 * interpolator fills its slice by calling {@code noiseFiller.fillArray(slice, sliceFillingContextProvider)}
 * at genuine cell corners: those corner contexts flow down into this reflector, which reflects {@code x -> -x}
 * for east corners before the underlying noise leaf samples. The slice therefore fills with reflected values
 * at TRUE corners, and interpolation runs identically on both sides — the mirror is smooth.
 *
 * <p><b>Coordinate-bearing leaves wrapped</b> (the reflect whitelist, {@link EvatorSpike#REFLECT_LEAF_NAMES}):
 * {@code DensityFunctions$Noise}, {@code $ShiftedNoise}, {@code $ShiftA}, {@code $ShiftB}, {@code $Shift},
 * {@code $ShiftNoise} (and, defensively, {@code $WeirdScaledSampler} / {@code BlendedNoise} /
 * {@code $EndIslandDensityFunction} — none of which appear in the 26.2 overworld router). Everything else is
 * either X-independent ({@code Constant}, {@code YClampedGradient}), a marker/cache, or an arithmetic /
 * transformer / spline node that reaches coordinates ONLY through its children (which are themselves visited
 * and wrapped bottom-up) — so wrapping it would double-reflect. See the design doc's leaf inventory.
 *
 * <p><b>{@code ShiftedNoise} coherence.</b> {@code ShiftedNoise} both has shift children AND reads {@code ctx}
 * directly ({@code noise.getValue(x*xzScale + shiftX(ctx), ...)}). It IS wrapped. When an east context
 * (x&gt;0) hits its reflector, the reflector flips to x&lt;0 and hands the reflected context to
 * {@code ShiftedNoise.compute}; the shift children (each independently reflector-wrapped) then see an
 * ALREADY-negative x and pass through unchanged. So along any root→leaf path the reflection fires AT MOST
 * ONCE (the outermost wrapper flips; every inner wrapper sees x&lt;0 and no-ops), giving exactly
 * {@code east(+x) = noise((-x)*s + shiftX(-x), ...) = west(-x)} — bit-exact at every corner. Double-wrapping
 * is harmless by construction.
 *
 * <p>Gated (via {@link EvatorSpike#armed()} / {@link EvatorSpike#mode()}) on {@code -Dlatitude.evatorSpike=leaf}.
 * No {@code LatitudeV2Flags} entry, no feature-flag forwarding — proof code, delete with the rest of the spike.
 */
public final class EvatorLeafReflectFunction implements DensityFunction.SimpleFunction {

    /** SPIKE: reflect the whole east half about the border centre (world X = 0), matching mechanism (a). */
    private static boolean inReflectBand(int blockX) {
        return blockX > 0;
    }

    /** SPIKE: reflected FunctionContext — negates blockX (mirror about X=0), keeps Y and Z. */
    private record ReflectedCtx(int blockX, int blockY, int blockZ) implements FunctionContext {
    }

    private final DensityFunction leaf;

    private final KeyDispatchDataCodec<EvatorLeafReflectFunction> codec =
            KeyDispatchDataCodec.of(MapCodec.unit(this));

    public EvatorLeafReflectFunction(DensityFunction leaf) {
        this.leaf = leaf;
    }

    @Override
    public double compute(FunctionContext ctx) {
        if (inReflectBand(ctx.blockX())) {
            return leaf.compute(new ReflectedCtx(-ctx.blockX(), ctx.blockY(), ctx.blockZ()));
        }
        return leaf.compute(ctx);
    }

    // fillArray: inherit SimpleFunction's default (contextProvider.fillAllDirectly(array, this)), i.e. a
    // per-cell compute over the provider's genuine corner contexts. That is exactly what we want on the
    // interpolator slice-fill path: each corner is reflected individually, so the slice buffer holds
    // reflected-at-true-corner values and the interpolator lerps them normally. We deliberately forgo the
    // underlying leaf's vectorised fillArray (which would sample un-reflected coords) — correctness over speed
    // for the spike.

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
     * Re-wrap the (possibly substituted) leaf so a later {@code mapAll} pass — notably {@code NoiseChunk}'s
     * own marker-wrapping visitor — recurses THROUGH this reflector rather than dropping it. (In practice the
     * leaves below us are never markers, so the substituted child is the identical leaf; this is here for the
     * same structural correctness reason {@link GeoTerrainBiasFunction#mapChildren} exists.)
     */
    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        return new EvatorLeafReflectFunction(visitor.apply(leaf));
    }
}
