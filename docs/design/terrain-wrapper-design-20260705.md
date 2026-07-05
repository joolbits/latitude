# Terrain Integration Wrapper -- Phase 4 design (locked 2026-07-05, post-adversarial-review revision r2)

**Status:** LOCKED design, pre-implementation. No code written yet. All MC-26.2 API facts below were verified this session by reading the actual `minecraft-merged-deobf-26.2.jar` and the live repo source (see the Verified-facts and Pre-implementation-verification sections). Items that genuinely cannot be settled without touching code / running the live pass at implementation time are called out explicitly as **verification items** or **accepted residual risks**, not guessed and not silently dropped.

**Revision note (r2):** This revision incorporates 12 findings that survived an independent adversarial verification pass. The single most consequential change is that the wrapper now biases **only `finalDensity` (#12)** and **no longer wraps `preliminarySurfaceLevel` (#11)** — because (a) #11 is a Y-block-level field and #12 is a density field, so one additive `K` cannot bias both commensurably, and (b) the stated rationale for wrapping #11 (that spawn/heightmap reads it) was verified false: `NoiseChunkGenerator.getHeight()` re-samples `finalDensity`, not `preliminarySurfaceLevel`. See the Change-Impact block at the end of §1 and the Residual-Risks register in §9.

**Scope:** ONE narrow density-function wrapper that biases terrain surface height toward GeoAuthority's continuous land/ocean field (`GeoSummary.land01`) so land columns are more likely to sit above sea level and ocean columns below it -- **without** replacing, flattening, or re-characterising the existing Terralith/vanilla terrain. Everything about terrain character (roughness, spline shape, cave carving, aquifers, ores) is passed through untouched. Exactly **one** router field is wrapped.

---

## 0. Verified MC-26.2 facts (ground truth, read from the dependency jar + repo this session)

- `net.minecraft.world.level.levelgen.NoiseRouter` is a **record with exactly 15 `DensityFunction` fields**, in this canonical constructor order: 1. `barrierNoise` 2. `fluidLevelFloodednessNoise` 3. `fluidLevelSpreadNoise` 4. `lavaNoise` 5. `temperature` 6. `vegetation` 7. `continents` 8. `erosion` 9. `depth` 10. `ridges` 11. `preliminarySurfaceLevel` 12. `finalDensity` 13. `veinToggle` 14. `veinRidged` 15. `veinGap`.
- **Field-role facts (verified, corrected from r1):**
  - `finalDensity` (#12) is a **density value** on the order of ~[-1,+1] near the surface whose **zero-crossing defines the rendered surface**. `min(mul(interpolated(blendDensity(...)),0.64).squeeze(), noodle)`.
  - `preliminarySurfaceLevel` (#11) is a **`FindTopSurface` density function whose output is a Y-BLOCK LEVEL**, clamped to [-40,320], and consumed as such: `ChunkNoiseSampler.calculateSurfaceHeightEstimate` does `MathHelper.floor(preliminarySurfaceLevel.sample(UnblendedNoisePos(i,0,j)))` and treats the returned double directly as a world Y. It is a **coarse estimate** used only for surface-rule / aquifer / carver bounds and a debug string.
  - **Spawn/heightmap does NOT read #11.** `NoiseChunkGenerator.getHeight() -> sampleHeightmap()` builds a `ChunkNoiseSampler` and walks the actual `finalDensity` interpolation loop (`sampleBlockState` against `add(finalDensity, Beardifier)`); it never consults `preliminarySurfaceLevel` for the returned height. Therefore biasing `finalDensity` alone is sufficient for spawn/heightmap to track the new surface automatically. This is the corrected basis for the r2 decision to wrap only #12.
- `net.minecraft.world.level.levelgen.RandomState` has `private final NoiseRouter router;` with a public getter `router()` and no setter -> mutation requires a Mixin `@Mutable` accessor, exactly as the reverted `RandomStateOceanSinkMixin` precedent did. Its constructor is `RandomState(NoiseGeneratorSettings, HolderGetter, long)` — it holds **no `ServerLevel`, no `ChunkGenerator`, and no settings `ResourceKey`** (only the flattened `NoiseGeneratorSettings` value). This is load-bearing for the gating design (§1.2).
- `DensityFunction` is an interface with: `double compute(FunctionContext)`, `void fillArray(double[], ContextProvider)`, `DensityFunction mapChildren(Visitor)`, `double minValue()`, `double maxValue()`, `KeyDispatchDataCodec codec()`.
- `DensityFunction.FunctionContext` exposes `int blockX()`, `int blockY()`, `int blockZ()`.
- `DensityFunction.SimpleFunction` provides default `fillArray`/`mapChildren`; our wrapper implements it, supplying only `compute`, `minValue`, `maxValue`, `codec`.

Repo facts re-verified this session:
- `GeoAuthority.sample(int x, int z)` is the underlying method; the provider surface is `GeoSummaryProvider.summarize(int blockX, int blockZ)`. The wrapper goes through `LatitudeBiomes.GEO_V2_PROVIDER`, never `GeoAuthority` directly.
- `LatitudeBiomes.GEO_V2_PROVIDER` is `private static volatile GeoSummaryProvider`, initialised to `NoOpGeoSummaryProvider.INSTANCE`, rebuilt only when `LatitudeV2Flags.GEO_V2_ENABLED` is true. A new package-visible static accessor `LatitudeBiomes.geoProviderForTerrain()` must be added.
- `NoOpGeoSummaryProvider.summarize` always returns `GeoSummary.NEUTRAL` (`land01=0.0`) -- the documented trap: reads as 100% ocean, not "no signal". The wrapper must gate on `GEO_V2_ENABLED`.
- **`GeoSummary.land01` is edge- and pole-coupled** (load-bearing — see §7 HARD STOP B and §9 residual R1). In `GeoAuthority.sample` (GeoAuthority.java:~219-224): `contEdged = blended - EDGE_STR*smoothstep(EDGE_START,1,|x|/xRadius) - POLE_STR*smoothstep(POLE_START,1,|z|/zRadius)`, then `land01 = smoothstep(-COAST_W, COAST_W, contEdged - SEA_LEVEL)`. With `EDGE_START=0.80`/`EDGE_STR=1.30` and `POLE_START=0.92`/`POLE_STR=0.75` (GeoAuthorityParams.java:37-38), `land01` is deterministically ramped toward 0 (ocean) in a band near every world edge and pole. **The whole terrain bias is keyed on this field**, so the bias inherits an edge/pole ocean ramp. This is disclosed and dispositioned in §7-B and §9-R1 rather than asserted away.
- **`land01` is domain-warped with integer snapping**, not a smooth field: `GeoAuthority.sample` L188-189 snaps warped coords with `(int)Math.round(...)`, so `land01(x,z)` is a **bounded step function on the integer block lattice** (piecewise-continuous, sub-block-quantized), NOT C-infinity. The plate F1-owner switch and `coastDist` clamp add further piecewise behavior. See §2 (corrected wording) and §6.2 (Lipschitz probe is the correct instrument).
- `LatitudeBiomes.GlobeShape` (`CLASSIC`/`MERCATOR`) -- both shapes share the same `globe:overworld*` settings keys; shape is a runtime property. **Mercator's `xRadius = round(zRadius * MERCATOR_ASPECT)`** (GlobeMod.java:343-344), so the edge ramp keyed on `ex=|x|/xRadius` sits at a different absolute E-W block distance than N-S and differs between Classic and Mercator. This makes the edge terrain artifact **anisotropic and shape-dependent** — surfaced as a Peetsa decision in §3, not buried.

---

## 1. Interception point and mixin shape

### 1.1 Target and injection
- Target class: `net.minecraft.world.level.levelgen.RandomState`.
- Injection: `@Inject` at `@At("TAIL")` of the `RandomState` constructor -- the same point/technique proven to compile/apply headless/produce byte-identical Classic atlas in the reverted `RandomStateOceanSinkMixin`.
- Router mutation via a re-created `RandomStateAccessor` (`@Accessor("router") @Mutable void globe$setRouter(NoiseRouter r)`). Rebuild the `NoiseRouter` via its full 15-argument canonical constructor, passing every field through unchanged **except `finalDensity` (#12)**, which is replaced by `new GeoTerrainBiasFunction(originalFinalDensity)`. **`preliminarySurfaceLevel` (#11) is passed through UNCHANGED.**
- **Lazy read, not captured at construction:** the wrapper density function reads `LatitudeBiomes.GEO_V2_PROVIDER` lazily, inside `compute()`, per column, at real chunk-generation time -- NOT captured once at RandomState-construction time, because the volatile static may not hold its final per-world value at the exact instant RandomState is constructed early in world load, but by the time any chunk is generated for gameplay, world load has completed. A future maintainer must not "optimise" this by caching the provider at construction time.

**Change-Impact block (r2 — why only #12 is wrapped):**
- `finalDensity` (#12) is a density field; its zero-crossing is the surface. Biasing it moves the surface Y. Both the rendered terrain AND the heightmap/spawn probe read #12 (verified §0), so a single wrap keeps rendered surface and spawn height mutually consistent **automatically** — no second field needed.
- `preliminarySurfaceLevel` (#11) is a Y-block-level field, two orders of magnitude off #12's density scale. Adding the same `S*K*sd` term to both is **not unit-commensurable**: a `K` tuned to move #12's zero-crossing by ~N blocks becomes a literal +N/−N block offset on the Y-valued #11 (or, inversely, a `K` sized for #11 saturates #12 to all-solid/all-air). The r1 claim that identical bias on both "mechanically proves spawn-height consistency" was **false**; it would have desynced the coarse surface-rule estimate from the rendered surface. Wrapping #11 is therefore **removed**.
- Consequence to accept: the coarse `preliminarySurfaceLevel` estimate now lags the biased surface by up to the bias magnitude, for surface-rule/aquifer/carver **bounds only**. Vanilla already treats #11 as a coarse estimate with slack; the surface system re-derives stone-depth from the actually-generated column top-down (§9-R3). This lag is an accepted residual (§9-R2), not a correctness break.

### 1.2 Gating (three independent conditions, ALL required)

**Design constraint (verified):** the mixin fires at the `RandomState` constructor TAIL, where `this` is a `RandomState` that holds no `ServerLevel`/`ChunkGenerator`/settings-`ResourceKey`. There is therefore **no `isGlobeWorld(RandomState)` method and none can be implemented from inside `RandomState`** — the repo's server-side globe check is `GlobeMod.isGlobeNoiseGenerator(NoiseBasedChunkGenerator)` (GlobeMod.java:452), which calls `noise.stable(ResourceKey)` on the **generator's** keyed settings, none of which `RandomState` can see. The r1 gate citing `GlobeMod.isGlobeWorld(RandomState)` was unimplementable and is replaced below. This is a HARD STOP A load-bearing correction: without a working globe check, the two remaining gates are process-global booleans and every `NoiseBasedChunkGenerator`'s `RandomState` in the JVM would become eligible.

Globe detection at the `RandomState` construction site is done by matching the **flattened `NoiseGeneratorSettings` value** that the constructor DOES receive against the six known `globe:overworld{,_xsmall,_small,_regular,_large,_massive}` settings **values**, resolved once at mixin-init from `BuiltInRegistries`/the settings registry. Concretely, one of the following two mechanisms (settle which at implementation time — §5 item 6, this is a mechanism choice, not a guessed fact):
  - **(1a) Value-identity match.** Capture the six `Holder<NoiseGeneratorSettings>` for the globe keys from the registry and compare the incoming settings value (available in the constructor args / capturable via `@Inject` locals or a companion `@ModifyVariable`-free capture) by reference/`equals` against those six resolved values.
  - **(1b) Settings-fingerprint match.** If value identity is unreliable through datapack reloads, compare a stable fingerprint of the incoming `NoiseGeneratorSettings` (its `NoiseSettings` + sea level + default block/fluid) against the six globe settings' fingerprints.

  If neither (1a) nor (1b) can be made reliable from the `RandomState` constructor site at implementation time, **the interception point moves** to a site that DOES have the generator/settings key in scope (e.g. inject where `RandomState` is constructed by `NoiseBasedChunkGenerator`, or wrap at `ChunkGenerator` creation and reuse `GlobeMod.isGlobeNoiseGenerator`), preserving the exact same "wrap only #12" router rebuild. **The globe gate MUST resolve to a real, positive globe check; a process-global boolean-only gate is explicitly forbidden.** (§5 item 6, §8 step 6a.)

2. `LatitudeV2Flags.TERRAIN_V2_ENABLED` must be true (new flag).
3. `LatitudeV2Flags.GEO_V2_ENABLED` must be true -- the mandatory precondition that defuses the NEUTRAL trap; without it the wrapper is never even installed.

If any is false, the constructor inject returns without touching the router -> byte-identical output.

Shape-blindness: Classic and Mercator share `globe:overworld*` keys/values, so condition (1) is satisfied by both; when the flag is ON the wrapper biases both shapes. The edge-ramp anisotropy this implies is a surfaced decision — see §3.

---

## 2. The bias formula

Per `compute(FunctionContext ctx)` call at `(x,z)=(ctx.blockX(),ctx.blockZ())`:
- `base = delegate.compute(ctx)` -- original **finalDensity** density value, untouched.
- `land01 in [0,1] = GEO_V2_PROVIDER.summarize(x,z).land01()` -- continuous-per-block, read lazily per column.
- `S = TERRAIN_V2_STRENGTH` (double, default 0.0).
- `K` -- compile-time constant, vertical-push scale in **density units** (NOT block units — see §0 unit facts), not a public knob. `K` is calibrated so that at `S=1.0`, a fully-land column (`land01=1`) shifts `finalDensity` upward by roughly the density delta corresponding to a modest surface lift on the **delegate's native density scale**. Because that native scale differs between pure-vanilla and Terralith routers (§6.4), `K` is a starting constant and the **live-tunable magnitude is `S`**; a per-terrain `K` recalibration is an accepted implementation-time item, not a shipped guarantee (§9-R4).

**(a) Signed drive centered at the neutral midpoint:** `d = 2*land01 - 1` (in [-1,+1]; land01=0.5 -> d=0).

**(b) Smoothstep shaping of the magnitude, sign-preserved:** `m=|d|`; `sm = m*m*(3-2*m)` (smoothstep, C1, sm(0)=0, sm(1)=1, sm'(0)=sm'(1)=0); `sd = sign(d)*sm`.

**Final bias:** `biased = base + S*K*sd`. `compute()` returns `biased`.

**Optional asymmetry (off by default):** `TERRAIN_V2_OCEAN_STRENGTH_RATIO` (default 1.0); if `r != 1.0`: `gain = (d>=0) ? 1.0 : r`; `biased = base + S*K*gain*sd`. At `r=1.0` this collapses to the symmetric form. The two branches meet at value 0 at `d=0` regardless of `r`, so it stays continuous at the boundary (slope changes on the ocean side only).

**Why no new cliffs (corrected wording — NOT "C-infinity"):** The wrapper's own transform `d -> sm -> sd` of `land01` is **C1** across `d=0` (because `sm(0)=0` and `sm'(0)=0`, no slope kink at the neutral point), and the coast is a *softened band* (smoothstep probability), not a hard boolean cutoff like the scrapped attempt. However, `land01(x,z)` itself is **NOT smooth**: it is a bounded step function on the integer block lattice (domain warp uses `(int)Math.round`, §0), with additional piecewise behavior from the plate F1-owner switch and the `coastDist` clamp. The correct property is therefore: **`land01` has bounded per-block first-differences (Lipschitz on the block lattice, sub-block-quantized micro-steps), and the wrapper is a monotone C1 map of it, so `S*K*sd` introduces no cliff — only bounded per-block steps whose size scales with `S*K`.** This is exactly what the §6.2 max-absolute-first-difference probe measures. The r1 "C-infinity, cannot create a cliff" phrasing is retired.

Edge/pole coupling of `land01` is real and intentional-direction but is a boundary-keyed effect on terrain — it is NOT hand-waved here; see §7-B and §9-R1.

**Why true no-op at S=0:** `biased = base + 0 = base`, bit-for-bit identical for every column/Y. Even at S>0, any column at land01=0.5 gets d=0 -> sd=0 -> biased=base (no-op wherever geography is undecided). Default configuration is `TERRAIN_V2_ENABLED=false` anyway, so the wrapper is never even installed by default; S=0.0 is the belt-and-suspenders second guarantee. (Byte-identity scope of the S=0 *installed* path is bounded honestly in §6.1 / §9-R5.)

---

## 3. Flags and knobs

Added to `com.example.globe.core.LatitudeV2Flags`, following the exact existing pattern (`public static final`, read once via `Boolean.parseBoolean`/`Double.parseDouble` on `System.getProperty` at class-init):

```java
public static final boolean TERRAIN_V2_ENABLED =
        Boolean.parseBoolean(System.getProperty("latitude.terrainV2.enabled", "false"));

public static final double TERRAIN_V2_STRENGTH =
        Double.parseDouble(System.getProperty("latitude.terrainV2.strength", "0.0"));

public static final double TERRAIN_V2_OCEAN_STRENGTH_RATIO =
        Double.parseDouble(System.getProperty("latitude.terrainV2.oceanStrengthRatio", "1.0"));
```

- `latitude.terrainV2.enabled` (boolean, default false) -- the install gate.
- `latitude.terrainV2.strength` (double, default 0.0) -- the one user-facing strength knob; the primary knob for the live pass.
- `latitude.terrainV2.oceanStrengthRatio` (double, default 1.0) -- optional grafted asymmetry knob, defaulted to symmetric, documented as advanced/live-tuning only.

Parse defensively (try/catch NumberFormatException -> fallback default) so a malformed -D degrades to the no-op default rather than throwing at class-init.

**Explicit decision surfaced to Peetsa (not silently locked): `isMercator()` gating.** The default is to bias both shapes identically (no `isMercator()` gate). **However, this is a substantive per-shape aesthetic decision, not a shape-neutral default**, because the edge/pole ocean ramp baked into `land01` (and thus the sunken-terrain ring it produces — §7-B/§9-R1) sits at `ex=|x|/xRadius`, and Mercator's `xRadius = round(zRadius*MERCATOR_ASPECT)` differs from Classic's. The visible edge-ring artifact is therefore **anisotropic and different between Classic and Mercator**. This is called out **up front** as a decision Peetsa should make at the live pass, not something to be discovered live and patched: if Classic's edge ring looks acceptable but Mercator's (wider E-W) does not, or vice-versa, `&& LatitudeBiomes.isMercator()` (or a per-shape strength) is the follow-up lever. HARD STOP A (byte-identical flag-OFF) is unaffected either way.

---

## 4. Safety and fallback discipline

### 4.1 The wrapper class
`com.example.globe.mixin.terrain.GeoTerrainBiasFunction implements DensityFunction.SimpleFunction`, holds `private final DensityFunction delegate` (the original `finalDensity`).
- `compute(ctx)` wrapped in try/catch(Throwable); on any throw, return `delegate.compute(ctx)`. Inside try: read S; if S==0.0 return delegate.compute(ctx) immediately (fast path); else read land01 via the provider, apply the formula, return biased.
- Provider-null/throw safety: try/catch returns delegate value.
- `minValue()`/`maxValue()`: widen delegate bounds by max possible bias `B = abs(S)*K*max(1.0, oceanRatio)`; return `delegate.minValue()-B` / `delegate.maxValue()+B`. At S=0, B=0, bounds equal delegate's exactly.
- `codec()`: never serialized (constructed in-JVM at RandomState-build time); return a throwing/no-op codec, documented non-serializable. `mapChildren` defaults to returning `this` (correct for a leaf wrapper); `fillArray` defaults to per-cell compute (correct); do not override either **unless §5 item 4 / §6.1 shows the default breaks interpolation/caching structure** (see §9-R5).

### 4.2 The mixin-level fallback
The entire router-rebuild in the RandomState constructor-TAIL inject is wrapped in try/catch(Throwable); on any failure, catch, log once, leave the original vanilla NoiseRouter in place. Two independent layers (inner per-compute, outer per-router-rebuild), matching the proven reverted-precedent discipline.

### 4.3 Precise definition of "no-op"
**Sampled-value no-op:** `finalDensity` returns the identical double it would return without the mixin, for every (x,y,z) -- achieved either by (a) the gate never installing the wrapper, or (b) the wrapper installed but S==0.0 returning `delegate.compute(ctx)` verbatim.
**Honest scope of (b):** path (a) is provably byte-identical end-to-end (no wrapper in the router). Path (b) is byte-identical **in sampled value via direct `compute`**; whether the opaque `SimpleFunction` wrapper alters the router's interpolation/cache **structure** (via `fillArray`/`mapChildren`/the caching visitor) so that some downstream consumer sees a structurally-different-but-value-equal graph is NOT proven by direct-`compute` equality alone. §6.1 adds a real NoiseChunk-build S=0 leg to close this, and §9-R5 records the residual until that leg is green. Field #11 is untouched in every path.

---

## 5. Open questions -- MUST be settled by reading real MC-26.2 code before implementation
1. Re-confirm the 15-field NoiseRouter constructor order at the exact commit being built (fresh javap immediately before writing the constructor call).
2. Confirm `finalDensity` is still field #12, and re-confirm (per §0) that `getHeight()`/`sampleHeightmap()` still re-samples `finalDensity` (not `preliminarySurfaceLevel`) so that wrapping only #12 keeps spawn/heightmap consistent.
3. Confirm RandomState still has `private final NoiseRouter router` and no setter, so @Mutable @Accessor is still required/valid; re-create RandomStateAccessor (deleted with the reverted feature).
4. Confirm `DensityFunction.SimpleFunction`'s default mapChildren/fillArray behavior is acceptable for a leaf wrapper in 26.2 **and does not strip interpolation/cache markers from the wrapped subtree** (§4.1, §9-R5); if it does, override to preserve structure.
5. Confirm RandomState's constructor signature/arity hasn't changed such that the @Inject descriptor needs updating, and that `router` is already assigned by the point TAIL runs.
6. **Settle the globe-detection mechanism at the interception site (§1.2 1a/1b), OR relocate the interception point to one with the generator/settings key in scope so `GlobeMod.isGlobeNoiseGenerator` can be reused.** The globe gate MUST be a real positive check; boolean-only global gating is forbidden. This is the HARD STOP A implementation-time blocker.

---

## 6. Mechanical proof (given the atlas is blind to terrain height)

### 6.1 Byte-identical no-op regression (HARD STOP A, mechanical)
- **Never-install path (a):** run headless atlas with `latitude.terrainV2.enabled=false`. Assert byte-identical to pre-change baseline for a Classic seed and a Mercator seed.
- **Installed-S=0 path (b), value AND structure:** run with `terrainV2.enabled=true, strength=0.0`. (i) Assert atlas byte-identical to baseline. (ii) **Additionally build a real NoiseChunk** (not just direct `compute`) at S=0 for a fixed globe seed and assert the generated chunk's block column is byte-identical to the vanilla-router chunk — this exercises `fillArray`/interpolation/cache visitor, closing the §4.3(b)/§9-R5 structural gap. If this leg cannot be made green with the default `SimpleFunction`, override `mapChildren`/`fillArray` to preserve structure (§5 item 4) and re-run.
- **GeoV2-off safety:** run with `terrainV2.enabled=true, strength=1.0, geoV2.enabled=false`. Assert byte-identical to vanilla -- proving the NEUTRAL trap is defused (wrapper never installed).
- **Globe-gate isolation (new):** run with `terrainV2.enabled=true, strength=1.0, geoV2.enabled=true` against a **non-globe** world (vanilla overworld settings and, separately, a Terralith-only non-globe world). Assert byte-identical to vanilla — proving the §1.2 globe gate actually excludes non-globe `RandomState`s and the process-global booleans alone cannot arm the wrapper. This is the mechanical guard for the §1.2 correction.

### 6.2 Direct density-output numeric probe (the core mechanical evidence)
Add a small headless probe harness that: builds a RandomState for `globe:overworld` at a fixed test seed with terrainV2/geoV2 enabled and a chosen strength; picks known columns spanning the geography field (land01 near 1.0, near 0.0, near 0.5) by querying `GEO_V2_PROVIDER.summarize(x,z)` directly; calls `router.finalDensity().compute(ctx)` at those columns for a vertical stack of Y values, once at strength=0 (baseline) and once at strength>0. Asserts:
- at strength=0, biased==baseline exactly for `finalDensity`;
- at strength>0, land columns shift + (more solid), ocean columns shift - (less solid), neutral columns unchanged;
- **Surface-Y consistency (replaces the retired #11==#12 identical-bias assertion):** derive the surface Y as the highest Y where `finalDensity` crosses zero, at baseline vs biased, and assert land columns' surface **rose** and ocean columns' surface **fell**. Separately assert that the heightmap probe (`getHeight`/`sampleHeightmap`, which re-samples `finalDensity`) returns a height consistent with that biased zero-crossing — this is the real "spawn agrees with rendered surface" evidence, and it holds **because both read #12**, not because two fields got the same additive term.
- **Smoothness / Lipschitz probe at the RIGHT locations (corrected):** run the dense max-absolute-first-difference (per-block Lipschitz) check along **three** transects, not one: (i) an ordinary interior coast, (ii) **a transect through the EDGE_START=0.80..1.0 edge band and the POLE band**, where `land01` carries the strong `EDGE_STR=1.30`/`POLE_STR=0.75` smoothstep ramp, and (iii) **a transect swept across several `(int)Math.round` domain-warp snap crossings**, where `land01` takes its ±1-block micro-steps. Assert the biased-density per-block first-difference stays below a small threshold on all three (bounded step, no cliff). The absolute threshold is documented as scaling with `S*K`; the assertion is that steps stay sub-cliff, matching the corrected §2 "Lipschitz on the block lattice" claim rather than a false smoothness claim.
- **Biome-mask / land-fraction assertion (new — for the ocean-authority feedback, see §6.3a):** at a fixed globe seed, compute the biome atlas with `terrainV2` OFF vs ON at the intended live strength and record the land/ocean biome fraction and the set of columns whose ocean-authority veto outcome flips. Assert the land fraction moves in the intended direction (ocean union stops over-compounding) and that no column flips ocean→land where `land01` is clearly ocean (`land01<0.1`) or land→ocean where `land01` is clearly land (`land01>0.9`) — i.e. the mask change is monotone-with-geography, not chaotic.

### 6.3 Biome-mask feedback is INTENDED and must be documented (was "what proof cannot cover")
Enabling this wrapper **changes the biome map, not only terrain height**, and this is the intended Phase 4 mechanism (per `docs/binder/biome-consumer-slice-20260704.md:125`): `LatitudeBiomes.pick()`'s ocean-authority veto (LatitudeBiomes.java:~2948-2957) computes `realHeight` via `previewHeight() -> generator.getBaseHeight(WORLD_SURFACE_WG)` (line ~1397), which resolves through the biased `finalDensity`, and flips `oceanAuthority=false` when `realHeight >= seaLevel` — reassigning land vs ocean biomes so terrain height correlates with GeoAuthority and the ocean union stops compounding.
- **(a)** This is a feature, not a regression, but it MUST be stated: turning on `terrainV2` moves the land/ocean biome mask through the veto. The §6.2 land-fraction/veto-flip assertion is the mechanical guard that the movement is monotone and geography-aligned.
- **(b) Documented caveat:** the veto reads surface height that now derives from the biased `finalDensity`; that keeps veto and rendered surface on the SAME field (good). No `preliminarySurfaceLevel` desync affects the veto, because the veto path uses `getBaseHeight` (finalDensity-derived), not #11.

### 6.4 Vanilla-only (no-Terralith) proof leg (NEW — vanilla-first hard constraint)
Every §6.1/§6.2 probe MUST be run in **two datapack configurations**: (A) the Terralith-influenced live config, and (B) **pure vanilla — zero custom biome/terrain datapacks, vanilla `NoiseRouter`, no Terralith `sloped_cheese` DF graph.** Rationale: the density magnitude that reads as a believable coastline is a function of the delegate's native density scale, which differs sharply between vanilla overworld and Terralith. A single compile-time `K` hand-tuned against a Terralith world can be too weak (no effect) or too strong (cliffs) on pure vanilla. The project's memory records the hard constraint "packs are enrichment, never required," so the feature must be shown to behave on vanilla. This leg produces the vanilla-vs-Terralith density-scale ratio that informs whether `K` needs a per-terrain value (§9-R4).

### 6.5 What proof cannot cover (aesthetics + absolute-Y surface bands)
Whether the aesthetic result reads as believable coastlines (no artificial shelves, no cloud-pop, character preserved) is Peetsa's live-pass call, with K/strength/oceanStrengthRatio dialed at the keyboard. **The live-pass checklist MUST explicitly include a badlands/coastal surface-band visual check** — see §9-R3 for why absolute-Y-anchored surface rules (badlands terracotta banding at abs y 63/74, sea-level water/beach fill at abs 60/62, mountain coarse_dirt at abs 97) do not track the lifted/sunk surface and can detach from the new waterline. This artifact class is invisible to the density probe.

---

## 7. Explicit non-goals / hard-stop compliance summary
- **A (byte-identical flag-off, Classic+Mercator):** guaranteed by the install gate (now backed by a REAL positive globe check, §1.2) and S=0 inner no-op; proven by 6.1 never-install + geoV2-off + **globe-gate-isolation** legs on both shapes. The r1 unimplementable `isGlobeWorld(RandomState)` gate is replaced; a boolean-only global gate is explicitly forbidden.
- **B (no teleport-loop/ocean-seam/edge-band revival):** The wrapper **formula** references no world-edge/pole distance. **HOWEVER, honesty requires disclosing that the single field it keys on, `land01`, is itself edge- and pole-coupled** (`EDGE_STR`/`POLE_STR` ramps, §0), so the height bias DOES push terrain down in a deterministic ring hugging the world boundary/poles. This is **intended-direction** (the geography map legitimately says "ocean near the projection edge") and is the same edge ocean the biome map already draws — but it is a boundary-keyed *terrain* effect and is NOT claimed to be absent. It is dispositioned as accepted residual **R1** (§9) with the isMercator/per-shape lever (§3) and a live edge-ring visual check as its guards. The hard stop that MUST hold — and does — is that the wrapper introduces **no NEW edge/seam/band concept of its own** and no discontinuous cliff (the ramp is smoothstep-softened and Lipschitz-bounded, §2/§6.2); the scrapped attempt's failure was a hard discontinuous seam, which this is not.
- **C (no broad NoiseRouter rewrite):** exactly **one** of 15 fields wrapped (`finalDensity` #12); other 14 passed through by identity.
- **D (does not fold in the Y-only amplitude wrapper):** this wrapper's bias is a function of (x,z) geography, Y-independent (same sd at every Y in a column); the separate amplitude design dampens peak height as a function of elevation (Y), independent of geography -- share the same interception surface but independent in purpose, not merged here.
- **E (single strength knob, default true no-op):** `latitude.terrainV2.strength` default 0.0; optional asymmetry knob defaults to symmetric 1.0.
- **F (try/catch fallback):** two independent layers, both fall back to unmodified vanilla density on any Throwable.

---

## 8. Pre-implementation checklist (do these first, in order)
1. Re-javap NoiseRouter, RandomState, DensityFunction, DensityFunction$SimpleFunction, DensityFunction$FunctionContext from the 26.2 deobf jar -> confirm section-0 facts and section-5 items 1-5, **including re-confirming `getHeight()`/`sampleHeightmap()` reads `finalDensity` not `preliminarySurfaceLevel`**.
2. Re-create RandomStateAccessor (@Accessor("router") @Mutable), register the mixin in the Fabric mixin JSON.
3. Add the three flags to LatitudeV2Flags (section 3), with defensive double parsing.
4. Add `LatitudeBiomes.geoProviderForTerrain()` package accessor for GEO_V2_PROVIDER.
5. Write `GeoTerrainBiasFunction implements DensityFunction.SimpleFunction` (section 4) — wraps `finalDensity` only.
6. **Settle §5 item 6 FIRST:** implement/verify the real positive globe gate (value/fingerprint match at the RandomState site, OR relocate the interception to reuse `GlobeMod.isGlobeNoiseGenerator`). Do not proceed to 6b until the globe gate is proven positive-and-exclusive.
   6a. Write the RandomState (or relocated) constructor-TAIL mixin with the section-1.2 triple gate + section-4.2 outer try/catch + 15-arg router rebuild wrapping **only field #12**.
7. Write the headless density-probe harness (section 6.2), including the three-transect Lipschitz probe, the surface-Y consistency check, and the land-fraction/veto-flip assertion.
8. Run §6.1 (never-install, installed-S=0 value+structure, geoV2-off, globe-gate-isolation) + §6.2 sign/smoothness/consistency + **§6.4 vanilla-only leg** on Classic + Mercator.
9. Hand off to Peetsa's live pass to dial K/strength/oceanStrengthRatio, with the §3 isMercator decision and the §6.5 badlands/coastal + edge-ring visual checks on the checklist.

---

## 9. Residual-risk register (accepted, disclosed, with guards)

Each item below is a survivor finding that is NOT fixed by a design change because doing so would exceed the scope of one narrow density wrapper, or is a genuine live-tuning/implementation-time question — and where a fix would conflict with a hard stop, the hard stop wins and the concern becomes a verification item. None are silently dropped.

- **R1 — `land01` is edge/pole-coupled, so the bias sinks a terrain ring near edges/poles (critical finding).** Accepted because keying the bias on `land01` is the entire point (geography-driven terrain), and stripping the edge/pole term out of `land01` just for terrain would (a) desync terrain from the biome ocean map the same field already draws and (b) reintroduce exactly the kind of special-case boundary logic HARD STOP B forbids. The ring is intended-direction and smoothstep-soft (no cliff). **Guards:** §3 surfaces the anisotropic/per-shape nature to Peetsa with the `isMercator`/per-shape-strength lever; §6.5 live edge-ring visual check; the effect scales with `S`, so it is dialable to zero.
- **R2 — `preliminarySurfaceLevel` (#11) coarse estimate now lags the biased surface.** Accepted deliberately: wrapping #11 with the same additive term is unit-incoherent (§0/§1.1) and would corrupt the surface-rule/aquifer estimate, and #11 does not drive spawn/heightmap anyway. Vanilla treats #11 as coarse-with-slack and re-derives stone depth from the generated column (R3). **Guard:** implementation-time verification (§5 item 2) that no consumer treats #11 as an exact surface; live surface-rule visual check.
- **R3 — Absolute-Y-anchored surface bands do not track the lifted/sunk surface.** Surface-relative stone-depth rules (~53 of them) follow the surface correctly (SurfaceSystem scans the generated column top-down), but absolute-Y bands do not: badlands terracotta/orange banding (abs y 63/74), sea-level water-pool/beach fill (abs 60/62), mountain coarse_dirt (abs 97). When a column is lifted or sunk, these detach from the new surface/waterline. Fixing this would require surface-rule surgery — out of scope for a density wrapper. **Guard:** documented as an accepted limitation of the height-bias approach; §6.5 live badlands/coastal surface-band check is mandatory on the checklist; if unacceptable, surface-rule attention is a separate follow-up, not part of this wrapper.
- **R4 — Single compile-time `K` may be mis-scaled between vanilla and Terralith density ranges.** Accepted at ship as "K is a starting constant, S is the live knob"; a per-terrain `K` is a possible follow-up. **Guard:** §6.4 vanilla-only proof leg produces the density-scale ratio and shows whether S alone can cover both, or whether a per-terrain K is needed before the live pass.
- **R5 — Installed-but-S=0 byte-identity is proven in sampled value, not yet in pipeline structure.** The opaque `SimpleFunction` wrapper may alter interpolation/cache structure even at S=0. **Guard:** §6.1 adds a real NoiseChunk-build S=0 byte-identity leg that exercises `fillArray`/`mapChildren`/caching; §5 item 4 mandates a structure-preserving override if that leg is not green. Until that leg is green, the accurate claim is "byte-identical in sampled value (path b) / byte-identical end-to-end (path a, never-install)."
- **R6 — Vanilla biome `depth` climate parameter (router field #9, unwrapped) is now computed relative to a surface `finalDensity` has moved.** On pure vanilla (no Terralith), a lifted column can have its shore/beach-vs-inland biome variant chosen as if still at the lower elevation, producing coastal/shore biome bands at a slightly wrong elevation relative to the new coastline. Leaving #9 unwrapped is correct (wrapping it would move biome climate, out of scope and against the vanilla-first "geography, not climate" separation). **Guard:** documented caveat; the §6.2 land-fraction/veto assertion and §6.5 coastal visual check are the observation points; if the shore-band mismatch is objectionable on vanilla it is a scoped follow-up, not a change to this wrapper.

---

## 10. Summary of r2 changes vs the locked r1 design
1. **Wrap only `finalDensity` (#12); stop wrapping `preliminarySurfaceLevel` (#11).** Fixes the unit-incommensurability (one K cannot bias a density field and a Y-level field) and the false "spawn reads #11" premise; spawn/heightmap tracks #12 automatically.
2. **Replace the unimplementable `isGlobeWorld(RandomState)` gate** with a real value/fingerprint globe match at the interception site, or relocation to reuse `isGlobeNoiseGenerator`; forbid boolean-only global gating; add a globe-gate-isolation proof leg.
3. **Disclose `land01`'s edge/pole coupling** (HARD STOP B is honestly "no new seam/cliff of our own," not "no boundary effect at all"), surface the Mercator anisotropy as a Peetsa decision, and register it as accepted residual R1.
4. **Correct the smoothness claim** from "C-infinity" to "Lipschitz on the block lattice / bounded per-block steps" and target the Lipschitz probe at the edge band and warp-snap crossings, not just an interior coast.
5. **Add a vanilla-only proof leg (§6.4)** and a **land-fraction/veto-flip assertion (§6.2/§6.3)** documenting the intended biome-mask feedback.
6. **Add residual register (§9)** for R1–R6 with explicit guards, and add the badlands/coastal + edge-ring live checks to the §6.5 checklist.