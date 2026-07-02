# Revamped World Shape — Design Document

> **2026-07-02 direction update:** This document is a historical design record. The current Latitude 2.0
> overhaul front door is `docs/LATITUDE_2_0_OVERHAUL.md`. The new plan keeps the 2:1 projected-planet
> foundation, but avoids treating it as literal Mercator, moves the planned canonical implementation target
> to Minecraft `26.2` after a pivot phase, and starts with measurement plus `GeoAuthority`/`ClimateAuthority`
> contracts rather than reviving the older continent-mask or wrap/seam work directly.

## 1. Concept and Motivation

Latitude's current world shape is a square globe where biome bands run east-west and provinces scatter
across vanilla continentalness noise. The land distribution looks plausible at small scale but reads
as "scattered spatter" at globe scale — no coherent landmasses, no asymmetric coasts, no ocean basins.

The Revamped world type addresses this at two levels:

1. **Mercator horizontal stretch**: the globe is remaped to a 2:1 rectangle so that east-west travel
   covers more geographic distance per block, matching the feel of looking at one face of a spinning
   globe projected flat. Latitude bands stay horizontal; the stretch adds geographic width without
   warping climate logic.
2. **Continent shape classifier**: vanilla continentalness is augmented with a domain-warped noise
   mask that steers land into coherent, tectonic-plausible landmasses rather than allowing the vanilla
   DF to scatter small islands.

Both are opt-in at world creation. Legacy worlds (all current saves) are never touched.

The concept originated in a Notion planning session (Peetsa/Maintainer, 2026-06-06) and a prior Ultracode
workflow (wf_f7d51f99-7f6) that produced design and feasibility analysis. That output was not committed;
this document is the canonical recovery and extension of that work.

---

## 2. The Two Components

### 2.1 Mercator Horizontal Stretch

> **IMPLEMENTED DIFFERENTLY (Peetsa, 2026-06-23): NO coordinate stretch.** The literal stretch below was
> rejected because it produces the *same* biomes drawn 2× wide (a projection look) rather than *more*
> biomes per band. Phase 1 instead simply widens the world (X half-extent = 2× Z) and samples the biome
> map at **real X**, so each band holds ~2× more biome regions. `pick()` is unchanged; there is no
> coordinate transform. See `docs/design/mercator-phase1-impl-plan.md` (top banner) for what shipped.
> The "two radii" idea below is still correct (Z = latitude authority, X = border/spawn authority).

The stretch is a coordinate remap applied before any latitude or province math. Two canonical
parameters replace the single `ACTIVE_RADIUS_BLOCKS`:

- `Z_RADIUS` — north-south half-extent in blocks. The pole lives at `|blockZ| = Z_RADIUS`.
- `X_RADIUS = Z_RADIUS * ASPECT` — east-west half-extent. Default `ASPECT = 2.0`.

For a Regular globe (`Z_RADIUS = 7500`), `X_RADIUS = 15000`. The east-west footprint doubles;
the latitude poles stay at the same Z distance.

**Latitude formula**: unchanged. `t = |blockZ| / Z_RADIUS`. Latitude bands remain horizontal
constant-Z stripes. The stretch does not warp them.

**Province and noise coordinates**: all `ValueNoise2D`, `ProvinceAuthority`, and `OceanDistanceField`
calls receive de-stretched X so that province blobs appear circular in geographic space rather than
elliptical in block space:

```java
// At the top of LatitudeBiomes.pick(), Revamped path only:
final int effectiveX = (int)(blockX / ASPECT);
final int effectiveZ = blockZ;
// All downstream noise/province/ODF calls use effectiveX, not blockX.
```

**World border**: Option A (recommended) — rectangular border. `WorldBorder` width = `X_RADIUS * 2`,
height = `Z_RADIUS * 2`. Players see a wider east-west fence, shorter north-south fence. The polar
hazard threshold `activePoleBandStartAbsZ` stays in Z-block coordinates, unchanged.

**Spawn search**: `findLandSpawn` must search an ellipse `(X_RADIUS * f, Z_RADIUS * f)` rather than
a circle so spawn candidates draw from the full geographic area.

### 2.2 Continent Shape Generation

Vanilla continentalness noise produces scattered small islands at globe scale. The continent classifier
overrides the land/ocean decision in `OceanDistanceField.isOceanCell()` for Revamped worlds.

Four approaches were evaluated: noise mask, curated template, continentalness zero-crossing shift,
and plate simulation. The recommendation is:

**Phase 1: domain-warped noise mask.** Replace `continentalness < -0.19` with:

```
(continentalness + continentNoise(effectiveX, effectiveZ, worldSeed)) < -0.19
```

`continentNoise` is a two-level domain-warped Simplex function:
- Outer noise period: `globeRadius * 0.5` blocks — controls continent size.
- Inner domain warp amplitude: `globeRadius * 0.15` blocks — controls coastline irregularity.
- Bias constant: tuned to ~35% land fraction. Adjustable without rebuild.

Domain warping (warp input coords with a second noise field before evaluating the continent signal)
produces the convex-headland / irregular-bay coast geometry that reads as tectonic without simulating
plates. This satisfies Art VI: a Simplex function with radius-proportional period is continuous,
seedable, and contains no `floorDiv` or cell-hash.

**Phase 3 target: plate simulation (Option D).** The noise mask is designed behind a stable interface
contract — `isOceanCell(cellX, cellZ, sampler) → boolean` — so that a plate simulation can slot in
later with no change to downstream consumers. The simulation would run at world-create time, store a
serialised 2D mask in `LatitudeWorldState`, and be queried O(1) per cell.

---

## 3. World Type Toggle — Player Experience and UI

> **SUPERSEDED (Peetsa, 2026-06-23): there is NO toggle.** "Just make it 2.0 — nobody needs square."
> All NEW worlds are Mercator automatically; there is no create-world choice. `GlobeShape.CLASSIC` exists
> only so pre-feature saves stay square (grandfathered). The toggle/UI design below is retained as history.


**Placement**: one new row on the create-world screen, positioned immediately below the world size
selector, aligned left to match the existing button column. Widget type: `CycleButton`, matching the
world-size control in visual weight.

```
[ World Size: Regular  <  > ]
[ World Shape: Classic <  > ]    ← new
[ ... rest of screen unchanged ... ]
```

**Option labels**:
- **Classic** — existing scattered-province layout.
- **[Chosen name]** — Mercator-stretch, continent-classifier layout.

**Flavour text** (single line, updates live on cycle):
- Classic: *"Biomes distributed across the globe as they are."*
- New mode: *"Landmasses form like a planet. East-west travel covers more ground."*

No tooltip popup. One sentence fits the bespoke screen's existing text density.

**Globe widget**: in Classic mode, no change. In new mode, the widget shows 2-3 large coherent blob
shapes in the existing biome-colour palette, slightly wider than tall. These are hardcoded decorative
paths baked into the widget texture — no live noise sampling at create time.

**Default**: Classic. The feature is opt-in until it has shipped a full release cycle without
regressions. When stability is confirmed, flip the default.

**In-game identity**:
1. Zone-entry subtitle fires once on first join to a Revamped world: *"Continental world — landmasses
   form across the horizon."* Never repeats.
2. `/latdev status` prints world shape alongside radius and seed.

**Existing-world lock**: the toggle is disabled (greyed out) when opening an existing world. Continent
topology is baked into vanilla continentalness at worldgen time; toggling shape on an existing world
would produce biome discontinuities at chunk boundaries. No upgrade path is planned.

---

## 4. World Type Names (Recommendation)

Evaluated ten candidates across geo resonance, player intuition, brand fit, memorability, and cool
factor. Top three:

| Rank | Name | Score /25 | Notes |
|------|------|-----------|-------|
| 1 | **Mercator** | 23 | Literal: the stretch is a Mercator projection. Fits Latitude's cartographic vocabulary. Short, pronounceable, no fantasy baggage. |
| 2 | **Orbis** | 22 | Latin for "disk of the Earth." Evocative, ages well. Risk: etymology not self-evident to all players. |
| 3 | **Planisphere** | 21 | Already present as a UI element — internal resonance. Less intuitive cold. |

Recommended pair: **Classic / Mercator**.

The pairing name should be "Classic" (not "Legacy") — "Legacy" implies tolerated obsolescence;
"Classic" treats the existing mode as a legitimate choice.

---

## 5. Architecture and Implementation Approach

### 5.1 Coordinate Transform

The transform is applied once, at the top of `LatitudeBiomes.pick()`, inside the Revamped gate:

```java
if (worldShape == WorldShape.REVAMPED) {
    final int effectiveX = (int)(blockX / ASPECT);
    final int effectiveZ = blockZ;
    // All noise, province, and ODF calls use effectiveX / effectiveZ from here.
}
```

`ASPECT` is a mod constant (initially `2.0`). It is not per-world configurable at Phase 1; world
size already provides the relevant scale knob.

The `ACTIVE_RADIUS_BLOCKS` field is split into `Z_RADIUS` (latitude authority) and `X_RADIUS`
(border authority). All callers that previously used `ACTIVE_RADIUS_BLOCKS` for latitude math receive
`Z_RADIUS`; border and spawn callers receive `X_RADIUS`.

`OceanDistanceField` receives transformed coordinates. Its BFS cell-size logic must operate in
geographic (transformed) space so coastal-biome gates fire at correct distances regardless of axis.

### 5.2 Continent Noise Mask (Recommended Approach and Why)

The noise mask is injected into `OceanDistanceField.isOceanCell()`:

```java
private boolean isOceanCell(int cellX, int cellZ, ChunkNoiseSampler sampler) {
    double continentalness = sampleContinentalness(cellX, cellZ, sampler);
    if (worldShape == WorldShape.REVAMPED) {
        continentalness += continentNoise(cellX, cellZ, worldSeed, globeRadius);
    }
    return continentalness < OCEAN_LIKE_THRESHOLD;
}
```

The noise function is not stored — it is evaluated on demand, like any other `ValueNoise2D` call.
For Phase 3 (plate simulation), this call is replaced by a lookup into a stored mask; the consumer
(`isOceanCell`) is unchanged.

Why not the other options: curated template requires substantial authoring work and is better suited
to v2.0; zero-crossing shift collapses architecturally into the noise mask (it modifies the same gate
via a spatially varying offset); plate simulation is the correct long-term target but requires world-state
schema growth and simulation infrastructure that is premature before continent classification is proven.

### 5.3 World State Storage

A new `globe_shape` field is added to `LatitudeWorldState` (the `PersistentState` stored at
`globe/globe_latitude_world_state`):

```java
public static final Codec<LatitudeWorldState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
    Codec.BOOL.fieldOf("spawn_picker_dismissed").forGetter(s -> s.spawnPickerDismissed),
    Codec.STRING.optionalFieldOf("globe_shape", "legacy").forGetter(s -> s.globeShape)
).apply(inst, LatitudeWorldState::new));
```

`optionalFieldOf("globe_shape", "legacy")` ensures all existing worlds deserialise cleanly with
`"legacy"` as the default. No DataFixer entry and no `level.dat` change are required.

At world creation, the toggle writes `"revamped"` or `"legacy"` into the initial state. All worldgen
code paths gate on this value; it is read once per world load and cached as a `WorldShape` enum.

### 5.4 Art VI and Governance Compliance

- **Art VI (no floorDiv / cell-hash in biome placement)**: the Mercator scale factor is applied
  upstream of all noise calls, producing pre-transformed integer coordinates. `ValueNoise2D` receives
  only already-transformed values. Its internal arithmetic is not modified. The continent noise function
  uses Simplex with radius-proportional period — continuous, no cell-hash.
- **Art X (no monoculture)**: Revamped must pass the same province-distribution regression tests as
  Legacy. Domain warping distributes land non-uniformly but does not concentrate a single biome >80%
  in any latitude band. This is verified per phase via headless atlas.
- **Art II (canonical first)**: all Revamped work is done on `26.1.2` canonical before any port
  branch receives it.
- **Determinism**: `continentNoise` is seeded from `worldSeed`. All transforms are purely arithmetic.
  Same seed + same radius = byte-identical output across runs and machines.
- **Scale-invariance**: period and warp amplitude are proportional to `globeRadius`. Revamped at
  Small and Regular produce qualitatively equivalent continent coverage fractions.
- **Legacy isolation**: every Revamped code path is inside `if (worldShape == WorldShape.REVAMPED)`.
  The Legacy branch is the existing unmodified code. A headless atlas regression on a Legacy seed
  runs on every Phase 2 commit.

---

## 6. Prerequisites

The following must be complete and stable before any Revamped work begins:

1. **1.4.1 overrep and snow-line passes landed and map-proven on `26.1.2`.** The coordinate transform
   and continent classifier invalidate existing biome-distribution evidence. A clean, proven Legacy
   baseline must exist first.
2. **`LatitudeWorldState.globe_shape` field added (Phase 1 prerequisite zero).** Both Mercator and
   continent work share this flag. It must be in place and serialised correctly before either
   component's code is merged.
3. **No concurrent restructuring of `LatitudeBiomes.pick()`.** If a refactor or Art VI cleanup is
   in-flight on the same method, it must land before the coordinate transform is introduced to avoid
   merge-conflict noise in the audit step.

---

## 7. Phased Roadmap

**Phase 1 — Mercator coordinate transform**

Add `globe_shape` to `LatitudeWorldState`. Gate the coordinate remap in `LatitudeBiomes.pick()`.
Split `ACTIVE_RADIUS_BLOCKS` into `Z_RADIUS` / `X_RADIUS`. Set rectangular world border. Fix spawn
search ellipse. Fix ODF coordinate input.

Go/no-go: headless atlas for Revamped and Legacy seeds are byte-identical when `ASPECT = 1.0` (identity
transform sanity check). Province distribution on `ASPECT = 2.0` passes Art X tolerance. World border
visually matches playable area in-game.

Phase 1 is the only phase that is safely shippable independently. The world is still
vanilla-continentalness-shaped, but the horizontal feel is already improved.

**Phase 2 — Continent classifier override**

Override `isOceanCell()` for Revamped worlds with the domain-warped noise mask. No change to BFS,
interpolation cache, or `oceanDistanceBlocks()` wrapper. `ProvinceAuthority` is unchanged at this
phase.

Go/no-go: headless atlas shows at least two visually distinct, coherent continent silhouettes across
four seeds; tropical-equator fractions within Art X tolerance; ODF BFS completion under 200 ms per
chunk-column (measured with `--latitude.debugODF`).

**Phase 3 — Level 2 climate modifiers**

Add ocean-distance gradient to `ProvinceAuthority.classify()`: inland → WARM_DRY / COLD_DRY; coastal
→ WARM_WET / COLD_WET. Rain-shadow noise layer keyed to continent interior distance. Gated to Revamped
worlds only; Legacy worlds are byte-identical to their pre-Phase-3 state.

This phase corresponds to Notion "Level 2." Level 3 (true continent + ocean basin generation, authority
shift, new Constitution) is explicitly deferred to a post-2.0 planning session.

---

## 8. Risks

**R1 — Missed coordinate transform call sites (Likelihood: High)**
`LatitudeBiomes.pick()` contains approximately 40 distinct noise, province, and ODF calls, each
currently receiving raw `blockX`. A single missed site produces province blobs stretched 2:1, visible
as unnaturally elongated biome zones in the atlas. Mitigation: introduce named locals
`final int mx = effectiveX(blockX)` at the top of `pick()` after the gate, then perform a full-text
search for `blockX` within `pick()` and `ProvinceAuthority.java` before any Phase 1 commit. A checkstyle
rule flagging raw `blockX` usage inside `pick()` after the transform point is the durable mitigation.

**R2 — OceanDistanceField BFS cell-size mismatch (Likelihood: Medium)**
The BFS uses a fixed 256-block cell grid in raw block coordinates. After the Mercator stretch the
effective geographic distance per cell changes along Z. The 64-cell cap silently represents a different
coast-distance horizon. Mitigation: pass transformed coordinates into ODF so it operates in
geographic space consistently, and scale the BFS cap to `max(X_RADIUS, Z_RADIUS) / CELL_SIZE`.

**R3 — Spawn finder returning wrong latitude zone (Likelihood: Medium)**
`findLandSpawn` computes target zone fraction from raw `blockZ` against `ACTIVE_RADIUS_BLOCKS`. After
the transform, the equator band fraction changes. Mitigation: spawn search must use `mercatorZ(blockZ)`
consistent with `pick()`.

**R4 — Legacy worlds broken by Phase 2 continent classifier (Likelihood: Low, consequence: High)**
If the `globe_shape` flag guard is incorrect or absent at Phase 2 merge time, all existing worlds
silently regenerate with wrong continent topology. Mitigation: `isOceanCell()` override is behind a
hard `if (worldShape == REVAMPED)` check with Legacy in the else branch. Headless atlas regression
on a Legacy seed runs on every Phase 2 commit. The `optionalFieldOf` default-to-`"legacy"` codec
ensures no existing world can accidentally deserialise as Revamped.

**R5 — Art VI violation in ValueNoise2D with transformed coordinates (Likelihood: Medium)**
If the Mercator scale factor is applied inside `ValueNoise2D` rather than upstream, integer rounding
of the transformed input may introduce cell-hash-equivalent alignment artifacts. Mitigation: scale is
applied before any noise call; `ValueNoise2D` receives only pre-transformed integer coordinates; the
constructor is not modified.

---

## 9. Open Questions for Maintainer/Peetsa

> **RESOLVED (Peetsa, 2026-06-23):**
> - **Q1 `ASPECT`** → **fixed at 2.0** for all world sizes. No slider ("not sure why anyone would care to
>   choose a 1:1 or 2:1 world").
> - **Q3 name** → **Mercator** (pair: Classic / Mercator).
> - **Q4 Phase 1 ship timing** → **ship Phase 1 (transform only)** now; begin implementation. The explicit
>   goal is *more biome representation by widening the world* — a 2:1 face gives each latitude band ~2× the
>   E-W length, so more biomes fit per band.
> - Parallel test-infra ask: **add more biome mods to the test set** (currently only BoP/Promenade/Terralith);
>   players usually run one custom biome mod, but we should test several kinds.
> - Q2 (live continent-noise tuning) and Q5 (rain-shadow) pertain to Phase 2/3 and remain deferred.

1. **`ASPECT` value**: 2.0 is the design default (a 2:1 rectangle, one full face of a globe). Should
   this be exposed as a world-creation slider for small/regular/large, or fixed at 2.0 for all sizes?
   → **Resolved: fixed at 2.0.**

2. **Continent noise parameters**: outer period at `globeRadius * 0.5` and warp amplitude at
   `globeRadius * 0.15` are starting values for live tuning. These can only be validated in-game.
   Confirm that Peetsa will be at the keyboard for the first live tuning session (consistent with
   the amplitude-knob protocol established for the DF wrapper work).

3. **World type name**: the recommendation is **Mercator** (Classic / Mercator). Does Peetsa/Maintainer
   prefer Orbis or Planisphere instead? This determines UI strings, the world-state enum value, and
   the in-game subtitle copy.

4. **Phase 1 ship timing**: Phase 1 (Mercator transform only, no continent classifier) is independently
   shippable as an experimental opt-in. Is there value in shipping it in 1.4.1 to gather player
   feedback on the horizontal-feel change, or should Revamped wait until Phase 2 is complete?

5. **Phase 3 continent-interior rain-shadow**: is this a 1.x feature or strictly post-2.0? Clarifying
   this determines whether `ProvinceAuthority` should be designed now to accept an ocean-distance
   signal or whether that API is deferred entirely.

---

## 10. Status and Next Steps

**Status**: design complete. **Phase 1 (Mercator stretch) approved by Peetsa 2026-06-23 and implementation
started.** ASPECT fixed at 2.0; name "Mercator". The exact real-symbol build checklist lives in
`docs/design/mercator-phase1-impl-plan.md`. Phase 2 (continent classifier) and Phase 3 (Level-2 climate)
remain deferred.

**Prior blocker (now accepted as concurrent):** 1.4.1 overrep/snow-line work is map-proven on `26.1.2`
and is the Classic baseline; Phase 1 is gated to Mercator worlds only, so Classic stays byte-identical and
the two streams do not conflict.

**Next steps when unblocked**:

1. Resolve open questions 1 and 3 (aspect value, world type name) so UI strings and the enum are
   settled before any code lands.
2. Add `globe_shape` to `LatitudeWorldState` codec (prerequisite zero, ~30 lines including tests).
3. Begin Phase 1 coordinate transform in a feature branch off canonical `26.1.2`. Run headless atlas
   identity-transform sanity check before touching `ASPECT`.
4. Phase 2 continent classifier follows Phase 1 go/no-go. Phases 2 and 3 cannot ship in separate
   mod versions; their code paths share the same `isOceanCell()` hook and must be merged and
   map-proven together.
5. Port chain (1.21.11 / 1.21.1 / 1.20.1) receives Revamped only after canonical Phase 2 is proven,
   per Art II.
