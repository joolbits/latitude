package com.example.globe.terrain;

import com.example.globe.adapter.geo.GeoAuthorityProvider;
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
 *   <li><b>The {@code instanceof GeoAuthorityProvider} realness check happens HERE, per call, not once
 *       at install time.</b> A real-world ordering bug (found live, 2026-07-06) showed the terrain-bias
 *       mixin fires on {@code ChunkMap} construction BEFORE {@code GlobeMod}'s create-world flow rebuilds
 *       {@code GEO_V2_PROVIDER} for that world's actual seed/radius -- so an install-time-only check saw
 *       the still-NoOp provider and permanently refused to ever install, on every freshly created world,
 *       regardless of seed. Checking here instead means the wrapper structurally installs unconditionally
 *       (once the flag/globe gates pass) and simply no-ops on any call where the provider isn't real
 *       <i>yet</i> -- which naturally resolves itself moments later once the real world-load rebuild runs,
 *       long before any player-visible chunk is generated. On a genuine seed-0 world the provider never
 *       becomes real for that world's entire lifetime, so this correctly stays a no-op forever there too --
 *       do not move this check back to the one-time install gate.</li>
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

    // --- Slice C (audit P0-1, refutation-hardened): Y-aware taper ---------------------------------------
    //
    // The audit's threshold sweeps proved the Y-UNIFORM bias has an EMPTY usable strength window: the
    // high-altitude density margin over land is a near-constant ~0.023-0.025 of the vanilla/Terralith noise
    // config, so any uniform bias >= that detaches a solid stone slab at Y256-319 (measured at S=0.10 on
    // 3/3 seeds) while the walkable ground moves <= +-1 block below it; pushing the ocean side instead
    // (oceanStrengthRatio) hollows lava-floored voids at Y-64..-55 before the sea floor moves meaningfully.
    // The bias's job is to shape the SURFACE zone (sea floors ~Y30-62, land surfaces ~Y60-130); it has no
    // business adding density to the upper sky or the deepslate floor. taperWeight(y) therefore scales the
    // bias by a smoothstep envelope: zero at/below TAPER_BOTTOM_START_Y (protects the measured lava-void
    // band), full across [TAPER_BOTTOM_END_Y, TAPER_TOP_START_Y] (the whole surface-shaping band), fading
    // to zero by TAPER_TOP_END_Y (well below both the slab band and the alpine/tree-line constants at
    // Y=168+, so the wrapper structurally cannot manufacture warm-band snowcaps either). Continuous in y
    // (C1 via smoothstep), so it introduces no new discontinuity in the density field. The column memo is
    // unaffected: it caches land01, which stays Y-independent; the taper applies AFTER the memo read.
    // maxAbsBias() stays a valid (now slightly generous) bound because taperWeight is in [0,1].
    private static final int TAPER_BOTTOM_START_Y = -32;
    private static final int TAPER_BOTTOM_END_Y = 0;
    private static final int TAPER_TOP_START_Y = 96;
    private static final int TAPER_TOP_END_Y = 160;

    // --- Slice C-2 (TEST 27 live finding 1): ocean-side "bathymetry" regime -----------------------------
    //
    // The additive negative bias SHATTERED terrain: a uniform subtraction flips every marginal-positive
    // pocket in a column negative, and ocean-intent columns' undergrounds sit at +0.03..+0.11 density
    // (measured at Peetsa's spawn: ONE solid mass [-64..98] became FOUR fragments + 63 void blocks at
    // S=0.4). A subtraction cannot be Y-windowed out of this, because the marginal band IS the seabed band
    // on other columns. The ocean side therefore stops being additive entirely: geography prescribes a
    // TARGET FLOOR Y* = seaLevel − depth(land01, shelf01), and the density becomes min(base, ramp(Y* − y)).
    // Properties, all structural: min() can never RAISE density (no slab, ever); below Y* the ramp is
    // large-positive and stops binding (the underground is UNTOUCHED — no hollowing, ever); above Y* it
    // carves air down to the target floor (real bathymetry); shelf01 (1 at the coastline, fading over the
    // shelf width) keeps a shallow apron near shore so coasts get a continental shelf instead of a
    // cliff-then-deep drop (TEST 27 finding 4). depth scales with |S|·|r|·smoothstep(|d|): r=0 keeps the
    // ocean side byte-identical to unbiased (the verified live recipe), and depth→0 as land01→0.5, so the
    // regime meets the additive land side continuously at the coast crossing.
    /** Max carve depth in blocks at |sd|=1, no shelf, |S·r|=1 (S=0.4,r=1 → 24 blocks of open-ocean depth). */
    private static final double K_DEPTH_BLOCKS = 60.0;
    /** The vanilla sea surface; the carve ceiling is measured down from here. */
    private static final double SEA_LEVEL_Y = 63.0;
    /** Density-per-block slope of the carve ramp around Y* (comparable to vanilla near-surface gradients). */
    private static final double CEIL_SLOPE = 0.08;
    /** Ramp floor: caps how negative the carve can force a cell (≈ vanilla sky density), keeps bounds tight. */
    private static final double CEIL_FLOOR = -0.5;
    /** On-shelf depth fraction: at shelf01=1 (the shoreline) only 20% of full depth — the shallow apron. */
    private static double shelfApron(double shelf01) {
        double s01 = Math.max(0.0, Math.min(1.0, shelf01));
        return 1.0 - 0.8 * s01;
    }

    // --- Slice C-3 (TEST 29 live finding: the "cursed giant wall") -- grade the GRIP ---------------------
    //
    // C-2 graded the carve's DEPTH with ocean-ward distance, but its GRIP was instant: the moment a column
    // crossed to ocean-intent (d<0), min(base, ceil) erased everything above the (still-shallow) ceiling,
    // no matter how tall the old-map terrain stood -- so wherever the geography coastline crossed tall
    // vanilla hills, the d=0 contour became a sheer planing cliff with aquifer/lava bleeds (live-confirmed
    // at 40S 135E; headless transect test29wall-r1.json: land01 ramps 1->0 over ~300 blocks while the
    // surface snapped 80->63 at the contour).
    //
    // The fix grades the CEILING'S HEIGHT, not the density: the effective ceiling descends from
    // CEIL_ONSET_Y (above the bias's whole operating envelope -- nothing to bite) down to the C-2 target
    // Y* as grip01 = smoothstep(|d| / GRIP_WIDTH) goes 0 -> 1 across the coastal band. min() semantics are
    // PRESERVED EXACTLY, which matters: the first C-3 attempt blended densities
    // (base + grip*(carved-base)) and the gap tripwire immediately caught it re-creating the TEST 27
    // hollowing class at partial grip (probe(14950,4426): gapBlocks 0->8 -- marginal pockets sandwiched
    // under the graded surface). A graded-height ceiling under min() cannot do that: air strictly above
    // the effective ceiling, untouched terrain below it, no intermediate densities anywhere.
    /** Where the graded ceiling STARTS its descent: the top of the bias's own operating envelope
     *  (== TAPER_TOP_END_Y). A ceiling at/above this height can only touch terrain the wrapper never
     *  shapes at all (rare vanilla 160+ coastal peaks get a flat cut there — accepted, bounded). */
    private static final double CEIL_ONSET_Y = 160.0;

    /** grip01 for an ocean-side magnitude m=|d|: 0 at the coastline, 1 from GRIP_WIDTH outward. */
    private static double gripAt(double m) {
        double w = LatitudeV2Flags.TERRAIN_V2_GRIP_WIDTH;
        if (w <= 0.0) {
            return 1.0; // ramp disabled: legacy instant grip
        }
        double t = Math.min(1.0, m / w);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Slice C-2: the ocean-carve ceiling Y for a column, or {@code +Infinity} when no carve applies
     * (S==0, r==0, NoOp provider, land side, or degenerate depth). THE single source of the bathymetry
     * target, shared by this class's compute() and by {@link GeoTerrainPrelimSurfaceFunction} — the
     * {@code preliminarySurfaceLevel} companion wrapper. Wrapping #11 became necessary (and, unlike the r1
     * design's rejected additive form, UNIT-CORRECT: both quantities are block-Y levels) when the first
     * bathymetry gate run showed carved sea cavities NOT flooding: the aquifer/fluid system distinguishes
     * open-to-sea from underground via the preliminary surface, which still claimed the old (uncarved)
     * height — so a 24-block carve read as a giant cave and got perched aquifers + air pockets instead of
     * open ocean. Uses the same per-thread column memo as compute(), so the second consumer is
     * effectively free on the hot path.
     */
    static double carveCeilYOrInfinity(int blockX, int blockZ) {
        double s = LatitudeV2Flags.TERRAIN_V2_STRENGTH;
        double r = LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO;
        if (s == 0.0 || r == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        GeoSummaryProvider provider = LatitudeBiomes.geoProviderForTerrain();
        if (!(provider instanceof GeoAuthorityProvider)) {
            return Double.POSITIVE_INFINITY;
        }
        ColumnMemo memo = COLUMN_MEMO.get();
        double land01;
        double shelf01;
        if (memo.provider == provider && memo.blockX == blockX && memo.blockZ == blockZ) {
            land01 = memo.land01;
            shelf01 = memo.shelf01;
        } else {
            GeoSummary summary = provider.summarize(blockX, blockZ);
            if (summary == null) {
                return Double.POSITIVE_INFINITY;
            }
            land01 = summary.land01();
            shelf01 = summary.shelf01();
            memo.provider = provider;
            memo.blockX = blockX;
            memo.blockZ = blockZ;
            memo.land01 = land01;
            memo.shelf01 = shelf01;
        }
        double d = 2.0 * land01 - 1.0;
        if (d >= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double m = -d;
        double sm = m * m * (3.0 - 2.0 * m);
        double depthBlocks = Math.abs(s) * Math.abs(r) * K_DEPTH_BLOCKS * sm * shelfApron(shelf01);
        if (depthBlocks <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double target = SEA_LEVEL_Y - depthBlocks;
        // Slice C-3: grade the grip (see the CEIL_ONSET_Y block comment). The returned ceiling is
        // ALREADY gripped — both consumers (compute() and the prelim wrapper) keep pure min()
        // semantics against it, so they can never disagree and never hollow.
        double grip = gripAt(m);
        if (grip >= 1.0) {
            return target;
        }
        if (grip <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return CEIL_ONSET_Y + grip * (target - CEIL_ONSET_Y);
    }

    /** Package hook so the prelim companion wrapper shares this class's one-shot failure log. */
    static void logBiasFailureOnce(Throwable t) {
        if (!BIAS_FAILURE_LOGGED.get() && BIAS_FAILURE_LOGGED.compareAndSet(false, true)) {
            com.example.globe.GlobeMod.LOGGER.warn(
                    "[Latitude] Phase 4 terrain-bias computation failed; passing through unbiased vanilla "
                            + "density (logs once per world).", t);
        }
    }

    /** Smoothstep envelope in [0,1]: 0 outside [BOTTOM_START, TOP_END], 1 across [BOTTOM_END, TOP_START]. */
    private static double taperWeight(int y) {
        if (y >= TAPER_TOP_END_Y || y <= TAPER_BOTTOM_START_Y) {
            return 0.0;
        }
        if (y > TAPER_TOP_START_Y) {
            double t = (TAPER_TOP_END_Y - y) / (double) (TAPER_TOP_END_Y - TAPER_TOP_START_Y);
            return t * t * (3.0 - 2.0 * t);
        }
        if (y < TAPER_BOTTOM_END_Y) {
            double t = (y - TAPER_BOTTOM_START_Y) / (double) (TAPER_BOTTOM_END_Y - TAPER_BOTTOM_START_Y);
            return t * t * (3.0 - 2.0 * t);
        }
        return 1.0;
    }

    /** The original {@code finalDensity} (#12); passed through untouched at S=0 and on any failure. */
    private final DensityFunction delegate;

    /**
     * Per-thread, single-entry memo of the last {@code (blockX, blockZ)} column's {@code land01} sample.
     *
     * <p><b>Why this exists.</b> {@code GeoAuthority.sample(x, z)} does real work (domain warp, plate-cell
     * lookup, several noise evaluations) and does NOT depend on Y at all -- but {@code compute()} is invoked
     * once per vertical noise-lattice cell (roughly every 8 blocks of world height, so on the order of a few
     * dozen times per column for a full-height world), not once per column. Without this memo, every one of
     * those calls redundantly re-ran the full {@code GeoAuthority.sample()} cost for the exact same
     * {@code (x, z)} -- a real, previously-unmeasured performance cost that only shows up at a nonzero
     * strength (the {@code S == 0.0} fast path above never reaches this code at all, which is exactly why
     * the Phase 4 Spark baseline, captured at strength 0, never caught it). Found live 2026-07-06 as
     * noticeably slower chunk generation once the terrain-install-timing fix let the wrapper actually engage.
     *
     * <p>Chunk generation genuinely visits a column's Y-levels back-to-back before moving to the next column
     * (that is how {@code NoiseChunk} fills its lattice), so a single last-value memo -- not a full LRU --
     * is sufficient to catch essentially all of the redundancy; it is intentionally NOT sized larger, to keep
     * this a trivial, allocation-free check. {@code ThreadLocal} because chunk generation is multi-threaded
     * (a worker-pool-shared cache would need synchronization for a benefit this cheap check doesn't need).
     */
    private static final ThreadLocal<ColumnMemo> COLUMN_MEMO = ThreadLocal.withInitial(ColumnMemo::new);

    private static final class ColumnMemo {
        int blockX = Integer.MIN_VALUE;
        int blockZ = Integer.MIN_VALUE;
        /** Reference to the provider this memo's land01 was computed against; invalidated if it changes
         *  (e.g. the world-load provider-rebuild the terrain-install-timing fix depends on) so a memo entry
         *  populated against the NoOp placeholder is never reused once the real provider is in place. */
        GeoSummaryProvider provider;
        double land01;
        /** Slice C-2: the bathymetry regime also needs the column's shelf01 (Y-independent, same memo). */
        double shelf01;
    }

    // --- Slice B observability latches (audit P1-2). Re-armed per world via resetLogLatchesForNewWorld()
    // (chained from TerrainRouterWrapping.resetLogLatchesForNewWorld, which GlobeMod already calls on each
    // overworld load). All are informational one-shots in the codebase's debugAlpine CAS idiom; none affect
    // computed values. Each is guarded by a plain get() before the CAS so the latched hot path costs one
    // predictable volatile read, not a CAS attempt per density sample.
    private static final java.util.concurrent.atomic.AtomicBoolean PROVIDER_NOT_READY_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean BIAS_ENGAGED_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean BIAS_FAILURE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Re-arms the one-shot logs above for a newly loaded world (see TerrainRouterWrapping's counterpart). */
    public static void resetLogLatchesForNewWorld() {
        PROVIDER_NOT_READY_LOGGED.set(false);
        BIAS_ENGAGED_LOGGED.set(false);
        BIAS_FAILURE_LOGGED.set(false);
    }

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
        // Delegate evaluation deliberately OUTSIDE the safety try (Slice B, audit P1-2): if the DELEGATE
        // itself throws, that is a vanilla/Terralith failure that must propagate exactly as it would with no
        // wrapper installed. The old shape re-invoked the delegate from inside the catch, which doubled the
        // delegate's cost on the failure path and let a second throw escape the "safety" net uncaught anyway.
        double base = delegate.compute(ctx);
        try {
            // Sweeper finding #23 -- DECISION: a NEGATIVE strength is INTENTIONALLY ALLOWED, not clamped. It
            // is a deliberate "flip" knob: S<0 inverts the bias direction (land columns sink, ocean columns
            // rise), which is a legitimate live-tuning experiment (e.g. probing the sign convention, or an
            // inverted-relief aesthetic). It is bounds-safe (maxAbsBias() uses Math.abs(s)) and stays a true
            // no-op at exactly S==0. This comment is the explicit record of the decision-not-to-clamp.
            double s = LatitudeV2Flags.TERRAIN_V2_STRENGTH;
            if (s == 0.0) {
                // Exact no-op fast path (design §2): biased == base, bit-for-bit, for every column/Y.
                return base;
            }

            // Real per-call safety net (moved here from the one-time install gate -- see
            // TerrainRouterWrapping's class javadoc "Install-time-vs-per-call provider check" note for the
            // full story). GEO_V2_PROVIDER may still be the NoOpGeoSummaryProvider (land01==0.0 everywhere,
            // i.e. reads as "100% ocean") at THIS exact call, either because a seed-0 world never builds a
            // real GeoAuthorityProvider at all (rebuildGeoAuthority()'s own seed!=0 guard is permanent for
            // that world), or -- the bug this re-homing fixes -- because a brand-new world is still mid-load
            // and simply hasn't rebuilt it YET. Checking this per call (not once at install time) means the
            // wrapper engages automatically the instant the real provider becomes available, instead of a
            // stale install-time snapshot permanently disabling it for the rest of the world's life.
            GeoSummaryProvider provider = LatitudeBiomes.geoProviderForTerrain();
            if (!(provider instanceof GeoAuthorityProvider)) {
                // Slice B observability (audit P1-2 / Lane 1 F6): "working", "not yet ready", and
                // "never will (seed-0)" used to read identically in the log. Say once per world that the
                // wrapper is installed but geography is not live.
                if (!PROVIDER_NOT_READY_LOGGED.get() && PROVIDER_NOT_READY_LOGGED.compareAndSet(false, true)) {
                    com.example.globe.GlobeMod.LOGGER.info(
                            "[Latitude] Phase 4 terrain bias is INSTALLED but GeoAuthority is not (yet) real -- "
                                    + "passing vanilla density through per column. Normal transiently during world "
                                    + "load; PERMANENT on a literal seed-0 / zero-radius world (logs once per world).");
                }
                return base;
            }
            if (!BIAS_ENGAGED_LOGGED.get() && BIAS_ENGAGED_LOGGED.compareAndSet(false, true)) {
                com.example.globe.GlobeMod.LOGGER.info(
                        "[Latitude] Phase 4 terrain bias ENGAGED: real GeoAuthority provider live "
                                + "(strength={}, oceanStrengthRatio={}).",
                        LatitudeV2Flags.TERRAIN_V2_STRENGTH, LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO);
            }

            int blockX = ctx.blockX();
            int blockZ = ctx.blockZ();
            ColumnMemo memo = COLUMN_MEMO.get();
            double land01;
            double shelf01;
            if (memo.provider == provider && memo.blockX == blockX && memo.blockZ == blockZ) {
                // Same column, same provider instance as last call on this thread -- reuse the sample
                // instead of re-running GeoAuthority's real work for the Nth time this column.
                land01 = memo.land01;
                shelf01 = memo.shelf01;
            } else {
                GeoSummary summary = provider.summarize(blockX, blockZ);
                if (summary == null) {
                    return base;
                }
                land01 = summary.land01();
                shelf01 = summary.shelf01();
                memo.provider = provider;
                memo.blockX = blockX;
                memo.blockZ = blockZ;
                memo.land01 = land01;
                memo.shelf01 = shelf01;
            }

            // (a) Signed drive centered at the neutral midpoint: d in [-1, +1]; land01 == 0.5 -> d == 0.
            double d = 2.0 * land01 - 1.0;

            // (b) Smoothstep shaping of the magnitude, sign-preserved. sm(0)=0, sm(1)=1, sm'(0)=sm'(1)=0
            //     so the transform is C1 across d=0 (no slope kink at the neutral point).
            double m = Math.abs(d);
            double sm = m * m * (3.0 - 2.0 * m);

            if (d >= 0.0) {
                // LAND side (Slice C, unchanged): additive lift under the Y-taper -- proven no-slab,
                // gap-healing. NOTE the documented negative-strength "flip" knob (sweeper #23) survives
                // here, but post-C-2 honesty: S<0 turns this into a SUBTRACTION on land and reintroduces
                // the shatter hazard TEST 27 exposed -- it remains a diagnostics-only knob, never a
                // shipping configuration.
                return base + s * K * sm * taperWeight(ctx.blockY());
            }

            // OCEAN side (Slice C-2 bathymetry -- see the K_DEPTH_BLOCKS block comment): carve down to a
            // geography-prescribed floor; never add, never touch below the floor. The ceiling comes from
            // the shared helper (memo already warm from the fetch above) so this class and the
            // preliminarySurfaceLevel companion wrapper can never disagree about the target.
            double ceilY = carveCeilYOrInfinity(blockX, blockZ);
            if (Double.isInfinite(ceilY)) {
                // r == 0 (the TEST 27 retry recipe) or degenerate: ocean side exactly untouched.
                return base;
            }
            // The ceiling is already grip-graded (Slice C-3) inside the shared helper; pure min()
            // semantics stay untouched here.
            double ceil = Math.max(CEIL_FLOOR, CEIL_SLOPE * (ceilY - ctx.blockY()));
            return Math.min(base, ceil);
        } catch (Throwable t) {
            // Bias-math failure only (the delegate already evaluated fine above): fall back to the unbiased
            // value -- and say so ONCE (Slice B, audit P1-2 / Lane 8): the old fully-silent catch could
            // erase the entire feature with zero log trace.
            if (!BIAS_FAILURE_LOGGED.get() && BIAS_FAILURE_LOGGED.compareAndSet(false, true)) {
                com.example.globe.GlobeMod.LOGGER.warn(
                        "[Latitude] Phase 4 terrain-bias computation failed; passing through unbiased vanilla "
                                + "density (logs once per world).", t);
            }
            return base;
        }
    }

    /**
     * Bounds post-C-2. The ADDITIVE regime now exists only on the land side and never uses the ocean
     * ratio, so its bound is {@code |S| * K} ({@code Math.abs(s)} still covers the documented negative-
     * strength "flip" knob; sweeper #15/#22's r-amplification concern is retired with the r-multiplied
     * additive path itself). The CLAMP (bathymetry) regime can only LOWER values, never raise them, and
     * its ramp is floored at {@link #CEIL_FLOOR} -- so when it can bind at all ({@code S != 0} and
     * {@code r != 0}), {@code minValue} must additionally admit {@code CEIL_FLOOR}. At {@code S == 0}
     * (or {@code r == 0} for the min side's clamp term) the corresponding widening vanishes, so the
     * S=0 bounds equal the delegate's exactly (the byte-identity contract).
     */
    private double maxAbsBias() {
        return Math.abs(LatitudeV2Flags.TERRAIN_V2_STRENGTH) * K;
    }

    private static boolean clampRegimeCanBind() {
        return LatitudeV2Flags.TERRAIN_V2_STRENGTH != 0.0
                && LatitudeV2Flags.TERRAIN_V2_OCEAN_STRENGTH_RATIO != 0.0;
    }

    @Override
    public double minValue() {
        double additiveMin = delegate.minValue() - maxAbsBias();
        return clampRegimeCanBind() ? Math.min(additiveMin, CEIL_FLOOR) : additiveMin;
    }

    @Override
    public double maxValue() {
        return delegate.maxValue() + maxAbsBias();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return codec;
    }

    /**
     * Overridden -- the design's own §6.1(b)(ii)/§9-R5 structural byte-identity leg found the default
     * {@code SimpleFunction.mapChildren} ({@code return this;}, verified via javap on the 26.2 jar) breaks
     * {@code NoiseChunk}'s cache/interpolation substitution: {@code NoiseChunk}'s constructor calls
     * {@code NoiseRouter.mapAll(visitor)}, which recursively walks the router's density graph via
     * {@code mapChildren} so the visitor can swap in chunk-scoped cache/interpolation nodes (e.g. the
     * {@code interpolated(blendDensity(...))} node buried inside vanilla's real {@code finalDensity}
     * graph, per §0). Returning {@code this} verbatim (the default) hides {@code delegate} from that
     * visitor entirely, so the ORIGINAL RandomState-construction-time density graph is used instead of
     * NoiseChunk's per-chunk one -- an installed-but-S=0 run then differs from the never-install run in
     * specific generated blocks (confirmed empirically: this exact defect was caught by the §6.1(b)(ii)
     * structural probe before this override existed).
     *
     * <p>The fix mirrors vanilla's own single-child wrapper pattern (e.g.
     * {@code DensityFunctions.MulOrAdd.mapChildren}, verified via javap): call {@code visitor.apply(delegate)}
     * to get the (possibly substituted) child, and construct a NEW {@code GeoTerrainBiasFunction} wrapping
     * it -- never mutate or return {@code this} directly, since visitors may run more than once with
     * different substitution behavior.
     */
    @Override
    public DensityFunction mapChildren(Visitor visitor) {
        return new GeoTerrainBiasFunction(visitor.apply(delegate));
    }

    // fillArray keeps SimpleFunction's default (per-cell delegation to compute() via
    // ContextProvider.fillAllDirectly) -- that is value-correct or this class's compute() itself would be
    // wrong, and the structural concern was specifically about mapChildren/cache-substitution, not
    // fillArray's per-cell evaluation strategy.
}
