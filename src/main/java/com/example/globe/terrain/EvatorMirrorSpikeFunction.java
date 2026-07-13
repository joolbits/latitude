package com.example.globe.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * ============================ SPIKE — THROWAWAY PROOF CODE (B-6 P1 STEP 0) ============================
 *
 * <p>This class exists ONLY to answer the B-6 hard-gate question: can east-band terrain SHAPE be
 * generated as a reflection ({@code x -> -x}) of the west band such that the mirror identity survives on
 * the REAL chunk-fill path (the batched {@code NoiseChunk} fillArray/interpolation path), not merely on the
 * single-column direct-{@code compute} path? It implements the mechanism the B-6 design originally
 * specified ("mechanism (a)"): a context-remapping {@link DensityFunction} wrapper installed at the
 * {@code finalDensity} seam — exactly like the shipped {@link GeoTerrainBiasFunction}, but instead of
 * biasing it REFLECTS the input world-X coordinate for east-side columns before delegating.
 *
 * <p>It is gated by the spike-only system property {@code latitude.evatorSpike} (read at install time by
 * {@link EvatorSpike}); there is NO {@code LatitudeV2Flags} entry and NO build.gradle forwarding for the
 * feature flag itself — this is proof code, to be deleted once the verdict is recorded. It must never ship.
 *
 * <p><b>The reflection.</b> The border is centered at world X = 0, so the reflection about the border
 * centre is simply {@code x -> -x} (design "mirror geometry": {@code mirrorX = 2*centerX - x}, centre 0).
 * For a spike we reflect the ENTIRE east half ({@code blockX > 0}) rather than only a thin band — that
 * maximally exercises the mechanism and keeps the geometry trivial; the real feature would gate to the
 * ~5&deg; edge band. West columns ({@code blockX <= 0}) pass through untouched (canonical), so the
 * identity under test is: {@code eastFill(+x)} vs {@code westFill(-x)}.
 *
 * <p><b>Why this is the honest test of mechanism (a).</b> A context-remap wrapper cannot mutate the
 * {@code NoiseChunk}'s internal cell coordinates; the only lever it has is to hand its delegate a DIFFERENT
 * {@link FunctionContext} carrying reflected coordinates. Verified against the 26.2 jar
 * ({@code NoiseChunk.NoiseInterpolator.compute}): a substituted interpolator returns its pre-filled slice
 * value ONLY when {@code context == NoiseChunk.this}; for any other context object it falls back to
 * {@code noiseFiller.compute(context)} (a raw re-evaluation). So a reflected context object rides through
 * the interpolator via that fallback — at the cost of turning the reflected side into RAW (un-interpolated)
 * noise while the canonical side stays interpolated. Whether that asymmetry breaks the block-level mirror
 * identity is precisely what the {@link EvatorSpike} probe measures on real chunks.
 */
public final class EvatorMirrorSpikeFunction implements DensityFunction.SimpleFunction {

    /** SPIKE: reflect the whole east half about the border centre (world X = 0). */
    private static boolean inReflectBand(int blockX) {
        return blockX > 0;
    }

    /** SPIKE: reflected FunctionContext — negates blockX (mirror about X=0), keeps Y and Z. */
    private record ReflectedCtx(int blockX, int blockY, int blockZ) implements FunctionContext {
    }

    private final DensityFunction delegate;

    private final KeyDispatchDataCodec<EvatorMirrorSpikeFunction> codec =
            KeyDispatchDataCodec.of(MapCodec.unit(this));

    public EvatorMirrorSpikeFunction(DensityFunction delegate) {
        this.delegate = delegate;
    }

    @Override
    public double compute(FunctionContext ctx) {
        if (inReflectBand(ctx.blockX())) {
            // Hand the delegate a reflected coordinate. On the direct (marker-transparent) path this
            // re-samples the noise at -x; on the NoiseChunk fill path this trips the interpolator's
            // non-NoiseChunk fallback (raw re-eval at -x). Either way the delegate never sees +x.
            return delegate.compute(new ReflectedCtx(-ctx.blockX(), ctx.blockY(), ctx.blockZ()));
        }
        return delegate.compute(ctx);
    }

    @Override
    public double minValue() {
        return delegate.minValue();
    }

    @Override
    public double maxValue() {
        return delegate.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return codec;
    }

    /**
     * Mirrors {@link GeoTerrainBiasFunction#mapChildren} exactly (and for the same reason): the default
     * {@code SimpleFunction.mapChildren} returns {@code this}, hiding {@code delegate} from
     * {@code NoiseChunk}'s cache/interpolation-substitution visitor, which would make the wrapper use the
     * original construction-time graph instead of the chunk-scoped one. Re-wrap the substituted child.
     */
    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        return new EvatorMirrorSpikeFunction(visitor.apply(delegate));
    }
}
