# GeoAuthority design — "Inverted-Plate Continentality" (Phase 2)

`status: implemented behind latitude.geoV2.enabled (default off)` · `date: 2026-07-03`
`layer: Core Logic (com.example.globe.core.geo)` · `zero Minecraft imports`

The locked macro-geography algorithm for Latitude 2.0, produced by a 4-design adversarial judge-panel
(geographic-realism / hot-path-cost / failure-mode lenses) and synthesized into one implementation-ready
spec. It replaces the "current red" (one ~63%-land sheet, largest land component ~95%, no dominant ocean
basin) with coherent continents in a connected ocean. Pure math from `(worldSeed, zRadius, xRadius)`;
**no biome-pack dependency** (see [[vanilla-first-overhaul-constraint]]) — geography is identical vanilla
or modded.

## Core idea

Land is the **minority upper tail** of a continentality field, so **ocean is the connected complement**
(a dominant connected basin emerges by construction — the red's worst failure is eliminated, not
relocated). The field is:

1. **Jacobian-safe domain warp** (2 value-noise): displace `(x,z)` by `warpAmp=0.06R` at wavelength
   `Lwarp=0.60R`. `warpAmp/Lwarp = 0.10` guarantees the warp never folds (proven monotone → bijective),
   so it organicises coastlines without tearing a landmass in two.
2. **4-octave continentality FBM** at the warped point (4 value-noise), weights `0.55/0.27/0.13/0.05` at
   `Lc=0.42R` — the dominant low octave gives few-large continents, not confetti.
3. **Rank-normalized plate bias**: a jittered Worley plate lattice (`Lplate=0.24R`, ~209 plates any
   size); each plate's continentalness is its **rank** among all plates mapped to `[0,1]` (a deterministic
   uniform distribution, 0-mean by construction), added as a smooth bias (`PLATE_BIAS_W=0.34`).
   **Correction (2026-07-05, sweeper audit #2 finding #22):** this used to also claim the rank
   normalization gives a "seed-variance-free land fraction." That overstates it — rank-normalization
   only makes the *plate-bias term itself* seed-invariant in distribution and mean; `contEdged` sums
   three terms (the seed-dependent 4-octave FBM above, this plate bias, and the edge/pole bias below),
   and the FBM term is fully seed-dependent. Land fraction is NOT seed-invariant: `phase2-geoauthority-
   20260703.md`'s own seed sweep measured 31.1–44.3% (a 13-point spread) at the same `SEA_LEVEL`. What
   the rank normalization actually buys is a bounded, well-distributed *bias* term with no seed able to
   push it to a degenerate extreme — not a fixed land fraction.
4. **Edge + pole ocean bias**: subtract `1.30·smoothstep(0.80,1,|x|/xRadius)` and
   `0.75·smoothstep(0.92,1,|z|/zRadius)` so the projection edge and poles are always ocean/ice, never a
   land cliff.
5. **Threshold**: `isOceanIntent = contEdged < SEA_LEVEL` (`SEA_LEVEL=0.04`, the single land-fraction
   knob, hard-clamped to `[0,0.10]` so land stays ~28–44%). `land01` is a `smoothstep` across the coast.

## continentId / oceanBasinId — the crux (no hot-path flood-fill)

Connected-component ids cannot be produced O(1) locally. So:
- **Hot path**: one array lookup into an offline-baked `idCell → componentId` table (`Lid=0.08R` id-cells,
  ~1404 entries). No BFS.
- **Offline** (`GeoIdLabeling`, flood-fill allowed — the analyzer/export path): rasterize `isOceanIntent`,
  4-connected union-find (same connectivity as `tools/atlas/geography_analyzer.py`), majority-vote each
  id-cell to a component, canonicalize a stable id from the component's lexicographically-min id-cell key.
- **Fallback** (no table attached): the plate-cell id — spatially coherent but a tectonic label, not a
  connected-component label (largest-basin share under fallback ≈ 1/nPlates). Used only when no table
  is baked; Phase 2 does not drive biomes from ids, so live worldgen uses the fallback and is unaffected.
- **Namespace disjointness**: land ids are forced **even**, ocean ids **odd** (parity), enforced from the
  column's own type at the end of `sample()`. This is a hardening fix over the synthesized spec, which
  XORed a type bit *into* the hash — that does **not** guarantee disjoint outputs after mixing (two
  different keys can collide across namespaces). Parity does. Ids in the coastal fringe (|coastDistance| ≤
  `COAST_BAND = 0.5·Lid`) are unreliable regardless and consumers must gate on that.

## GeoSummary field mapping (all 15 fields)

`land01`, `isOceanIntent`, `continentId`, `oceanBasinId`, `coastDistanceBlocks` (analytic proxy from the
field gradient, + on land / − on ocean, clamped to `±0.5·Lc`), `shelf01` (offshore shallow ring),
`islandArc01` (plate-seam subduction arcs, ocean side), `archipelago01` (shelf-gated so open ocean never
speckles), `mountainIntent01` (collision seams + interior uplift), `orogenId` (unordered plate-pair hash,
shared along a seam), `ruggednessIntent01`, `projectionEdgeSuitability01` (`max(edgeBias, poleBias)`).
Hydrology (`drainageBasinId`, `flowDirection`, `coastOutletId`) is **reserved/neutral** this phase — real
drainage needs an offline flow-accumulation pass (a later phase).

## Hot-path cost

≤ 8 value-noise + ≤ 19 hash + 1 table lookup per `sample()`, fixed 3×3 Worley scan, no loops of unknown
length, no BFS/height-probe/lock, one allocation (the returned record). The per-world precompute
(`plateNorm` rank-normalization of ~209 plates) is O(n log n) at construction, not a flood-fill.

## Parameter table (radius-relative; `-Dlatitude.geoV2.*` overrides)

| knob | default | override | meaning |
|---|---|---|---|
| `SEA_LEVEL` | 0.04 (clamp 0–0.10) | `seaLevel` | land-fraction lever (higher = less land) |
| `Lc` | 0.42·R | `lcRatio` | continentality FBM wavelength |
| `Lwarp` / `warpAmp` | 0.60·R / 0.06·R | — | warp (ratio 0.10 = no fold) |
| `Lplate` | 0.24·R | `lplateRatio` | plate size / continent count+size |
| `Lid` | 0.08·R | — | id-cell (table index) |
| `PLATE_BIAS_W` | 0.34 | `plateBiasW` | plate-continentalness weight |
| `EDGE_START/STR` | 0.80 / 1.30 | — | projection-edge ocean bias |
| `POLE_START/STR` | 0.92 / 0.75 | — | pole ocean bias |

## Residual risks (disclosed, tunable)

1. **Heavy-tail supercontinent** — ~1 seed in several (e.g. `424242`) yields one landmass ~95% *of land*.
   Earth-plausible (Pangaea), and NOT the red: land there is only ~44% of the world and the ocean basin is
   still dominant (~97%). The largest continent is ≤ ~42% of the *world* (vs the red's ~60%). Drop
   `-Dlatitude.geoV2.lplateRatio=0.22` to fragment more if a flatter distribution is wanted.
2. **Coastal id fringe** — within `COAST_BAND` an id-cell's majority type can disagree with a pixel; ids
   there are unreliable. Contractual mitigation: consumers gate on `|coastDistanceBlocks| > coastBand`.
   **Why the fringe is unreliable (added 2026-07-05, sweeper audit #2 finding #19):**
   `coastDistanceBlocks` is `(contEdged - SEA_LEVEL) / gradConst`, where `gradConst` is the analytic
   per-block slope of ONLY the dominant FBM octave. `contEdged` also sums 3 more octaves (each
   comparable in slope to the dominant one), the domain warp, a piecewise-constant plate-bias term that
   steps at plate seams, and the edge/pole ramps (whose local slope is ~3.7x `gradConst` near their
   transition) — so `coastDistanceBlocks` over/under-estimates true distance-to-coast depending on
   whether the local field happens to be flatter or steeper than the dominant octave alone. Currently
   latent (no live consumer implements the `|coastDistanceBlocks| > coastBand` gate this risk itself
   prescribes yet), but any future consumer relying on that gate for id reliability should know it is
   only approximate, not exact.
3. **Offline-table lifecycle** — connected-component ids need `GeoIdLabeling.build`; the live hot path
   uses the plate-id fallback until a table is baked/persisted. Fine for Phase 2 (ids don't drive biomes
   yet); persisting the table into level data is a later-phase task.
4. **Coastlines are somewhat plate-influenced** (mildly angular in places) — a live-tuning matter, not a
   correctness issue.
5. **Mercator-aspect calibration** — `SEA_LEVEL` was tuned at `xRadius = 2·zRadius`; formulas read the real
   radii and stay radius-relative, but a materially different aspect should re-check land fraction.
   **Related, added 2026-07-05 (sweeper audit #2 finding #26, PLAUSIBLE):** the pole ocean bias
   (`poleB`, keyed solely on `|z|/zRadius`) has no `xRadius` dependence, so at an EXTREME non-2:1 aspect
   (`zRadius` much smaller than `xRadius`) the poleward ocean band could fragment into thin strips
   rather than one dominant basin, breaking the "ocean is the connected complement" invariant
   `dominantOceanBasin`/`largestContinentNotHalfTheWorld` assert. Not reachable at the two currently
   shipped world shapes (Classic 1:1, Mercator 2:1) — flagged for whenever a materially different aspect
   ratio is considered, not a live bug today.
6. **Plate bias defaults to neutral (0.5) beyond the world's populated plate-cell range** (added
   2026-07-05, sweeper audit #2 finding #15): `buildPlateNorm()` only populates plate-cell keys for a
   1-cell pad around `[-xRadius, +xRadius] x [-zRadius, +zRadius]`. ClimateAuthority's fetch/orographic
   probes can sample `geo.sample()` up to `0.42*R` beyond a column near the world edge, landing in an
   unpopulated plate cell and silently getting zero plate-continentality bias for that probe. Confirmed
   deterministic and edge-of-world-only; not fixed this pass (would mean widening `buildPlateNorm`'s
   padding to cover the maximum possible probe reach) -- worth a robustness pass alongside Phase 4.
7. **`seed == 0` is treated as an "uninitialized" sentinel** (added 2026-07-05, sweeper audit #2 finding
   #13): `LatitudeBiomes.rebuildGeoAuthority`/`rebuildClimateAuthority` guard on `seed != 0L`, mirroring
   the pre-existing `rebuildProvinceAuthority` convention -- a world genuinely seeded `0` would keep the
   NoOp provider even with the flag on. Pre-existing, shared, mod-wide convention (not new to Phase 2/3);
   out of this design's scope to change unilaterally.
8. **JUnit `GeoAuthorityTest` land/ocean metrics are raw-pixel, not cosine-latitude area-weighted**
   (added 2026-07-05, sweeper audit #2 finding #10, PLAUSIBLE): `GeoIdLabeling.computeMetrics` (consumed
   by `landFractionInTargetBand`/`dominantOceanBasin`/`largestContinentNotHalfTheWorld`) computes plain
   raw-pixel shares, while `tools/atlas/geography_analyzer.py` (used for the offline proof numbers this
   design and the binder cite) IS cosine-latitude area-weighted. The two are measuring different
   quantities that happen to correlate on the canonical 2:1 grid; not reconciled this pass (would need
   either porting area-weighting into the JUnit metrics or re-deriving their thresholds under it).

## Where this lives

- `core/geo/GeoNoise.java` — verbatim value-noise + hash01 + mix64 (parity test guards drift).
- `core/geo/GeoAuthorityParams.java` — the locked parameter table + overrides.
- `core/geo/GeoAuthority.java` — the field + `sample()` + `plateNorm` precompute + fallback ids.
- `core/geo/GeoIdLabeling.java` + `GeoIdTable.java` — offline flood-fill labeling + metrics.
- `adapter/geo/GeoAuthorityProvider.java` — real `GeoSummaryProvider`, wired flag-gated in `LatitudeBiomes`.
- `dev/GeoAuthorityAtlas.java` — pure-Java offline proof tool (`geoAuthorityAtlas` Gradle task).
