package com.example.globe.terrain;

import com.example.globe.adapter.geo.GeoSummaryProvider;
import com.example.globe.core.LatitudeV2Flags;
import com.example.globe.core.geo.GeoSummary;
import com.example.globe.world.LatitudeBiomes;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Phase 4 (Terrain Integration Spike) density-function wrapper. Biases terrain surface height toward
 * GeoAuthority's continuous land/ocean field ({@code GeoSummary.land01}) so land columns are more likely
 * to sit above sea level and ocean columns below it, WITHOUT replacing or re-characterising the existing
 * Terralith/vanilla terrain. Wraps exactly ONE {@code NoiseRouter} field: {@code finalDensity} (#12), the
 * density value whose zero-crossing defines the rendered surface. {@code preliminarySurfaceLevel} (#11)
 * is deliberately NOT wrapped (design r2 §1.1: one additive K cannot bias a density field and a Y-block
 * field commensurably, and spawn/heightmap re-samples {@code finalDensity}, not #11 -- verified against
 * the 26.2 jar: {@code NoiseBasedChunkGenerator.getBaseHeight -> iterateNoiseColumn} builds a
 * {@code NoiseChunk} whose {@code BlockStateFiller} is fed by {@code finalDensity}).
 *
 * <p>Design of record: {@code docs/design/terrain-wrapper-design-20260705.md} (locked r2). See §2 (the
 * bias formula) and §4 (safety discipline).
 *
 * <p><b>Package note:</b> this class is ordinary (non-mixin) code deliberately kept OUT of
 * {@code com.example.globe.mixin.*} -- that package tree is reserved for {@code @Mixin} classes by
 * {@code globe.mixins.json}'s {@code "package"} declaration, and Fabric/Mixin's {@code IllegalClassLoadError}
 * forbids referencing anything under it directly from ordinary code. Both call sites that construct a
 * {@code RandomState} (the vanilla-{@code ChunkMap}-targeting mixin in
 * {@code com.example.globe.mixin.terrain}, AND this mod's own {@code com.example.globe.dev.BiomePreviewExporter}
 * dev/atlas tooling, which builds its own {@code RandomState} directly and must NOT be a mixin) need to call
 * into this class, so it lives in plain code where both can reach it.
 *
 * <p><b>Load-bearing invariants (do not "optimise" away):</b>
 * <ul>
 *   <li>The geo provider is read <b>lazily per {@code compute()} call</b> via
 *       {@link LatitudeBiomes#geoProviderForTerrain()}, NOT captured at construction -- the volatile
 *       static may not hold its final per-world value when the {@code RandomState} is built early in
 *       world load, but it does by the time any gameplay chunk is generated (design §1.1).</li>
 *   <li>{@code S == 0.0} is an exact bit-for-bit no-op fast path that returns {@code delegate.compute(ctx)}
 *       verbatim (design §2 "true no-op at S=0").</li>
 *   <li>{@code compute()} is wrapped in {@code try/catch(Throwable)}; any failure falls back to the
 *       unmodified delegate value (design §4.1). Two independent fallback layers exist: this inner one and
 *       the outer per-router-rebuild one in {@link TerrainRouterWrapping#installIfArmed}.</li>
 * </ul>
 */
public final class GeoTerrainBiasFunction implements DensityFunction.SimpleFunction {

    /**
     * Compile-time vertical-push scale in <b>density units</b> (NOT block units -- {@code finalDensity} is
     * a density field on the order of ~[-1,+1] near the surface, whose zero-crossing is the surface).
     * This is a STARTING constant, not a public knob: the live-tunable magnitude is
     * {@link LatitudeV2Flags#TERRAIN_V2_STRENGTH} ({@code S}). Because the delegate's native density scale
     * differs between pure-vanilla and Terralith routers, a per-terrain {@code K} recalibration is an
     * accepted implementation-time item (design §9-R4), informed by the §6.4 vanilla-only proof leg.
     */
    private static final double K = 0.25;

    /** The original {@code finalDensity} (#12); passed through untouched at S=0 and on any failure. */
    private final DensityFunction delegate;

    /**
     * Cached, never-serialized codec. Constructed in-JVM at {@code RandomState}-build time and never round
     * -tripped through data, so a trivial unit codec is sufficient and correct (design §4.1
     * "documented non-serializable").
     */
    private final KeyDispatchDataCodec<GeoTerrainBiasFunction> codec =
            KeyDispatchDataCodec.of(MapCodec.unit(this));

    public GeoTerrainBiasFunction(DensityFunction delegate) {
        this.delegate = delegate;
    }

    @Override
    public double compute(FunctionContext ctx) {
        try {
            double base = delegate.compute(ctx);

            double s = LatitudeV2Flags.TERRAIN_V2_STRENGTH;
            if (s == 0.0) {
                // Exact no-op fast path (design §2): biased == base, bit-for-bit, for every column/Y.
                return base;
            }

            GeoSummaryProvider provider = LatitudeBiomes.geoProviderForTerrain();
            if (provider == null) {
                return base;
            }
            GeoSummary summary = provider.summarize(ctx.blockX(), ctx.blockZ());
            if (summary == null) {
                return base;
            }
            double land01 = summary.land01();

            // (a) Signed drive centered at the neutral midpoint: d in [-1, +1]; land01 == 0.5 -> d == 0.
            double d = 2.0 * land01 - 1.0;

            // (b) Smoothstep shaping of the magnitude, sign-preserved. sm(0)=0, sm(1)=1, sm'(0)=sm'(1)=0
            //     so the transform is C1 across d=0 (no slope kink at the neutral point).
            double m = Math.abs(d);
            double sm = m * m * (3.0 - 2.0 * m);
            double sd = Math.signum(d) * sm;

            // Optional ocean-side asymmetry (off by default; r == 1.0 collapses to the symmetric form).
            // The two branches meet at value 0 at d == 0 regardless of r, so it stays continuous there.
            double r = LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO;
            double gain = (r == 1.0 || d >= 0.0) ? 1.0 : r;

            return base + s * K * gain * sd;
        } catch (Throwable t) {
            // Provider-null/throw safety and any other failure: fall back to the unmodified delegate value.
            return delegate.compute(ctx);
        }
    }

    /**
     * Widen the delegate's bounds by the maximum possible bias {@code B = |S| * K * max(1.0, oceanRatio)}.
     * At {@code S == 0} this is {@code 0}, so the bounds equal the delegate's exactly (no-op). A safe
     * over-estimate is fine and required (density bounds must contain every value {@code compute} can
     * return), so {@code max(1.0, oceanRatio)} covers both the symmetric and ocean-amplified branches.
     */
    private double maxAbsBias() {
        double s = LatitudeV2Flags.TERRAIN_V2_STRENGTH;
        double r = LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO;
        return Math.abs(s) * K * Math.max(1.0, r);
    }

    @Override
    public double minValue() {
        return delegate.minValue() - maxAbsBias();
    }

    @Override
    public double maxValue() {
        return delegate.maxValue() + maxAbsBias();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return codec;
    }

    // SimpleFunction supplies default fillArray/mapChildren; correct for a leaf wrapper (design §4.1).
    // Not overridden unless a structural byte-identity proof leg (design §6.1/§9-R5) shows the default
    // strips interpolation/cache markers -- that is separate follow-up work, not part of this task.
}
