I have confirmed the prototype files exist and verified the load-bearing constants in `OceanDistanceField.java` (`CELL_SIZE=256` line 20, `OCEAN_LIKE_THRESHOLD=-0.19` line 22, and the `floorDiv`-based cell sampling at line 108). Here is the report.

---

# Latitude — "Revamped" Earth-like Continents + Mercator Stretch
## Design & Feasibility Report

**For:** Maintainer (mod author)
**Date:** 2026-06-06
**Status:** Design proposal — feasibility-validated against current code + a working visual prototype
**Effort estimate:** Large (L). The plumbing is small; the *content tuning* is the work.

---

## 1. Concept restatement

Today every Latitude world is a square, equator-centered globe whose land/sea pattern comes straight from vanilla Minecraft's continent noise. At default scales that noise reads as a **spatter of small islands separated by water** rather than recognizable continents.

This proposal adds a player-chosen **"Legacy vs Revamped"** toggle at world creation:

- **Legacy** — exactly today's behavior, byte-for-byte. Existing worlds and existing players are untouched.
- **Revamped** — a new land/sea + terrain-height field that produces **a few large, coherent, Earth-like continents with real coastlines and genuinely deep oceans**, clustered toward one hemisphere (the ~29% land / ~71% ocean feel).

Separately and orthogonally, we add a **Mercator-style horizontal stretch** as a *display* feature: the square biome map "reads as" a Mercator planisphere (narrow tropics, fanning wider toward the poles, with polar caps). This is a renderer/minimap concern only — it never touches worldgen.

The guiding principle, borrowed from Stardust Labs' *Continents* datapack: **change only WHERE the coastline is, never WHAT biome it is.** Latitude's entire latitude-band climate system stays exactly where it is.

> **Prototype image:** `tmp/worldshape-poc/comparison.png` (script: `tmp/worldshape-poc/worldshape_poc.py`, deterministic `SEED=1337`, numpy+PIL only). It renders Legacy and Revamped side-by-side as ~2:1 Mercator rectangles sharing an identical latitude-tinted climate layout. Legacy = ~441 disconnected specks / 203 confetti islets at ~20% land; Revamped = 17 coherent landmasses (4 confetti) at ~28% land with deep oceans. The climate bands are pixel-for-pixel identical across both panels — that *is* the separation-of-concerns proof. (It mimics the proposal's noise/spline/Mercator math, not the real Java density functions, so shapes are illustrative.)

---

## 2. How Latitude works today + the hard constraints

### 2.1 The pipeline in one breath

1. **Vanilla density functions decide land/sea + terrain height.** `globe:noise/raw_continents` (Perlin) → spline in `full_continents.json` → `island_selector` (range_choice threshold at -0.5) → the noise router's `continents` density function → `final_density`. This is the only thing that actually raises or lowers terrain.
2. **`OceanDistanceField` decides "is this cell ocean" for biome purposes.** It samples `continentalness` from the Climate Sampler at 256-block cells, treats anything below **-0.19** as ocean, BFS-computes distance to the nearest ocean cell, and bilinearly interpolates to block resolution.
3. **`LatitudeBiomes.pick()` chooses the biome.** If `base.is(IS_OCEAN) || oceanAuthority` (oceanDistance == 0) it returns an ocean biome; otherwise it runs the latitude-band picker.
4. **The latitude-band picker** normalizes `t = |blockZ| / effectiveRadius` to a band (hard boundaries at 23.5° / 35° / 50° / 66.5° / 90°), runs `ProvinceAuthority` warm/cold moisture classification (with the equatorial wet-bias ramp), and rolls a final biome from weighted tag pools at `TIER_COHERENCE_BLOCKS = 160`, all via seeded `ValueNoise2D`.

The crucial, load-bearing observation: **the continent shape (steps 1–2) and the biome classification (steps 3–4) are already two separate layers.** Revamped only needs to touch the first.

### 2.2 The hard constraints any new shape system MUST respect

These come straight from the architecture findings and the Constitution. Violating any of them is, by definition, a regression.

| # | Constraint | Where it bites |
|---|---|---|
| **C1** | **Determinism / byte-identical replay.** `(seed, effectiveRadius, blockX, blockZ, blockY) → biome` must be a pure function. No `Math.random`, no system time, no per-chunk hashes. `WORLD_SEED` is read-only during generation. | Any shape noise must be seeded `ValueNoise2D` or vanilla Perlin density functions. |
| **C2** | **Latitude-driven biomes are sacred.** Band boundaries are fixed *degrees*; the only scale-invariant model is `t = |blockZ| / effectiveRadius`. Revamped must not change band degrees or the wet-bias ramp. | Touching `LatitudeBands` / `ProvinceAuthority` is out of scope. Revamped is land/sea only. |
| **C3** | **Scale invariance.** Feature scale must be expressed as a *frequency* (`xz_scale`), independent of world size, so continents look proportionally the same on the smallest (3750) and largest (20000) radius. Radius-scaled noise (jitter wavelength, coherence blocks) stays coupled to `effectiveRadius`. | Revamped's continent `xz_scale` is world-size-independent, like today's settings. |
| **C4** | **Art VI — no floorDiv / cell-hash.** Shape boundaries must come from smooth `ValueNoise2D`/Perlin fields, never hard hash-grid cells. This is *the* documented confetti root cause (the old 38-block VARIANT_CELL tier). | Use low-frequency Perlin continentalness + splines; no per-cell land/sea hashing. |
| **C5** | **Art X — monoculture = regression.** Equator must stay mostly humid with *rare coherent* arid, not a single biome and not even-scatter confetti. | Validate Revamped doesn't accidentally homogenize or speckle any band. |
| **C6** | **Save-format transparency.** `LatitudeBiomeSource.codec()` encodes the *underlying vanilla BiomeSource*, not the wrapper. **No shape data is persisted to NBT** — terrain is re-derived per chunk from seed + the `noise_settings` baked into the saved dimension generator. | This is what makes Revamped a clean *preset selection* problem, not a save-migration problem. |
| **C7** | **Atlas-headless provability.** `pick()` is called with null generator/noiseConfig/heightView during headless export; shape must degrade to noise-only gracefully. | Density-function shape is inherently fine here; it runs upstream of `pick()`. |
| **C8** | **The square→rectangle ripple.** The square aspect is hardcoded at the foundation: a single `WorldBorder` diameter on all axes; only `blockZ` enters latitude math; atlas/planisphere/spawn/hazard logic all assume `radiusZ == radiusX`. Making `blockX` *participate in latitude* (a true rectangular world) ripples through the WorldBorder API (needs a mixin), every biome-pick call site, EW-hazard tuning, spawn margins, NBT format, and UI labels. | **This is exactly why Mercator must be display-only.** See §5 and §8. |

**The single most important alignment contract (the one real worldgen risk):** `OceanDistanceField` hardcodes `OCEAN_LIKE_THRESHOLD = -0.19` (line 22) and `CELL_SIZE = 256` (line 20), and at line 108 classifies a cell as ocean via `cont < OCEAN_LIKE_THRESHOLD`. It samples the **same** Climate Sampler continentalness that the terrain spline reads. If Revamped's spline moves the land/sea crossover off `-0.19`, the ocean-biome layer desyncs from the actual terrain height → **ocean biomes painted on dry land, or phantom oceans over real terrain.** Revamped must either keep its sea-level crossover at continentalness ≈ -0.19, or parameterize that threshold per-variant. This is non-negotiable.

---

## 3. Candidate approaches + judge scoreboard

Four approaches were evaluated. Scores are `(architecture-fit-and-determinism, earth-like-realism-and-look, feasibility-effort-and-risk)`, 1–10.

| Approach | Arch fit | Realism | Feasibility | **Avg** |
|---|:---:|:---:|:---:|:---:|
| **A. Data-only "Tectonic-style continents" noise_settings variant**, selected by a parallel world-preset set; latitude layer + Mercator stay display/runtime-side | **9** | 6 | **8** | **7.7** |
| **B. Tessera** — offline-baked plate-tectonic continent control map driving a JSON density-function overlay | 8 | **9** | 5 | 7.3 |
| **C. Curated continent tile-atlas** via a baked low-res control map sampled by a new density-function source | 7.5 | 8 | 5 | 6.8 |
| **D. Domain-warped continent mask** via density-function swap ("Revamped" noise-router overlay) | 7 | 6 | 5 | 6.0 |

**Reading the board:** B and C win on raw Earth-likeness because a baked plate/tectonic control map produces causally-consistent mountains-on-plate-boundaries and correct bimodal hypsometry. But both pay for it: they introduce a **shipped asset + build pipeline**, a finite (tileable) world, and bilinear-interpolation grid-artifact risk that is itself an Art VI concern. They drag feasibility down to 5. D (pure domain-warped noise swap) gets most of A's plumbing simplicity but without A's clean "Legacy == literally unchanged" framing or A's proven Tectonic lever set, so it scores lower on both fit and realism.

A wins because it is the only approach where **Legacy is *defined as today's exact behavior*** (zero risk to existing worlds) and the entire Revamped delta is expressible as datapack JSON + a tiny create-world toggle — while still delivering "big coherent continents with deep oceans" via the proven Tectonic lever set.

---

## 4. RECOMMENDED approach (with a hybrid escape hatch)

### 4.1 Recommendation: Approach A — data-only Tectonic-style continents variant

**Core idea.** Treat "Revamped" purely as a different **land/sea + terrain-height layer.** Leave Latitude's latitude-band classifier (`LatitudeBiomes` / `ProvinceAuthority` / `OceanDistanceField`) completely untouched. Ship:

- a second family of noise_settings, `globe:overworld_revamped_<size>`, and
- a parallel world-preset family, `globe:globe_<size>_revamped`,

whose only delta from the live settings is that the `continents` / `depth` / `erosion` router entries point at a **retuned, Tectonic-style continent chain** with lower continentalness `xz_scale` and a stretched continentalness→offset spline.

**Why this is the right call:**

1. **It honors every hard constraint by construction.** It's pure density-function JSON (C1, C4, C7), expresses scale as frequency (C3), doesn't touch the band system or wet-bias (C2, C5), and rides on the existing codec-transparency guarantee so save-compat is trivial (C6).
2. **Legacy == today, exactly.** "Legacy" is literally the unchanged `globe:overworld_<size>` settings. There is no migration, no risk to existing saves — they already reference those settings.
3. **The proven cure for "scattered islands"** is precisely this: derive land/sea + coastline from one continuous low-frequency continentalness Perlin field mapped through splines to terrain height. Every major mod (Tectonic, Continents, Terralith) uses this; it directly attacks Latitude's confetti root cause.
4. **Almost zero Java.** The heavy lift is JSON spline tuning + create-world toggle plumbing that's already half-scaffolded for `worldTypeIdx`.

A load-bearing discovery from the code: the custom `globe:noise/raw_continents` + `full_continents` + `island_selector` + `continent_selector` chain **already exists in-tree but is effectively dormant** — the live `globe:overworld_<size>` settings currently route `"continents": "minecraft:overworld/continents"` (vanilla). So Revamped is partly *activating and retuning machinery that's already there.*

### 4.2 The hybrid escape hatch (recommended for a later phase, not v1)

Approach A's only weakness is realism ceiling: pure domain-warped noise gives clustered continents but **no causal geology** — mountains won't line up with coasts, and fBm hypsometry is too Gaussian vs Earth's lowland-skewed curve.

If, after shipping A, the continents read as "good but a bit too noisy/blobby," the clean upgrade is the **baked-control-map hybrid (B/Tessera)**: run a plate-sim/Voronoi scaffold *offline*, freeze a low-res global control map as a shipped asset (so its determinism doesn't matter), and have the Revamped density-function source sample it with smooth interpolation + high-frequency noise detail. This is a *drop-in replacement for A's continent chain* — same toggle, same presets, same save story — so **A is forward-compatible with the hybrid.** Ship A first; treat the hybrid as an optional realism patch later.

---

## 5. The Mercator horizontal-stretch design

**Decision: Mercator is a display/projection layer only. The square deterministic grid stays the single source of truth.** This is the research's explicit recommendation and the only path that doesn't trip constraint **C8**.

### 5.1 The math, distilled

In any cylindrical projection the east-west scale is fixed by geometry: a parallel at latitude φ has circumference ∝ cos φ, so to make every parallel as wide as the equator (a rectangle) you stretch its width by

```
k = sec(φ) = 1 / cos(φ)
```

`k = 1.00` at the equator, `1.15` at 30°, `1.41` at 45°, `2.00` at 60°, and **→ ∞ at the poles.**

Latitude's world *already is* an equirectangular vertical layout: equator at Z=0, poles at Z=±radius, latitude linear in normalized |Z|. So the cheapest faithful Mercator "read" is **scheme 2 — horizontal-only `sec(φ)` widening**: keep the linear latitude rows exactly as they are and stretch *only display columns* by `sec(φ)` per row, centered on the equator.

```
x_display = x_world * sec(φ(row))      # vertical rows unchanged
```

This produces the unmistakable Mercator "fan" (narrow tropics, wide mid-latitudes) with **zero change to worldgen determinism, Art VI coherence, or band balance.** It's technically a stretched-equirectangular, not strictly-conformal Mercator — but it reads as Mercator and is bounded as long as we truncate before the pole.

### 5.2 Mandatory truncation + polar caps

`sec(φ)` diverges at ±90°, so we **must** cut off before the pole or the top/bottom rows explode:

- **For the requested ~2:1 rectangle:** truncate at **±66°–70°** and render a solid **polar cap** (ice band / azimuthal inset) for the residual last degrees. This conveniently hides the worst `sec(φ)` area-inflation, which lives in the final ~20°. (66.5° is also Latitude's existing subpolar/polar boundary — a natural cut line.)
- **For a true 1:1 square render:** the canonical web-map cut is ±85.05113° (`2·atan(e^π) − π/2`), exactly where a full Mercator becomes square.

The polar cap is display-only, so a missing cap can never corrupt worldgen — but it *is* a visible UI break if omitted, so it's enforced in the renderer.

### 5.3 Where it lives

- **Live/cheap path — `LatitudePlanisphereRenderer` (client).** It currently maps degrees linearly to pixel radius (`yPixel = radius * deg/90`). Add a per-row x-scale by `sec(φ)`, add the truncation cut + polar-cap fill. Pure renderer change.
- **Offline/pretty path — `BiomePreviewExporter` + the atlas viewer (`tools/atlas`).** Add an *optional* full conformal Mercator (scheme 1: nonlinear row resample `y = R·ln(tan(π/4 + φ/2))` **and** column `sec(φ)`) run on the rendered `step16_biome_ids.png` — **never** on the live grid. This is where an offline reprojection naturally belongs.

### 5.4 What is explicitly OUT of scope

A **true 2D Mercator** that makes `blockX` participate in latitude math — separate E/W radius, a `WorldBorder` width≠height mixin, EW-hazard re-tuning, rectangular NBT format — is the high-risk path the World-Size/Aspect findings warn against (C8). It is a save-format-breaking change and is **not** part of this proposal. Mercator-as-display gives the look without any of that risk, and ships fully decoupled from the Revamped generator.

---

## 6. The Legacy/Revamped toggle + create-world + save-compat plan

Wire it through the **already-scaffolded `worldTypeIdx` path** rather than inventing a new system.

### 6.1 UI (LatitudeCreateWorldScreen)

Add `LatitudeWorldVariant { LEGACY, REVAMPED }` as a **sub-choice shown only when `worldTypeIdx == 0` (Latitude).** Keep `WORLD_TYPE_NAMES` as `{Latitude, Vanilla, Vanilla Superflat}` so the spawn-zone UI visibility logic (lines ~739/835) is unaffected.

- Add a `selectedVariant` field + a small stepper/toggle near `worldTypeIdx`.
- Optionally drive a live preview thumbnail (Legacy spatter vs Revamped continents) so players see the difference.

### 6.2 Launch (LatitudeWorldLauncher.beginExpedition)

At preset resolution (lines ~79–84), append `"_revamped"` to `size.worldPresetId` when `REVAMPED`:

```
globe:globe_<size>  →  globe:globe_<size>_revamped
```

The revamped preset's *only* difference is `"settings": "globe:overworld_revamped_<size>"`.

### 6.3 Persistence

Store the variant in a small client-side holder mirroring `GlobeWorldSizeSelection` (`GlobeWorldVariant.set/get`), set alongside it at launcher line ~195.

### 6.4 Save-compat — the strong point of this design

Because `LatitudeBiomeSource.codec()` encodes the underlying vanilla source and **no shape data is persisted to NBT** (C6):

- A **Revamped** world bakes `globe:overworld_revamped_<size>` into its level NBT dimension generator and reloads identically. Terrain is re-derived per chunk from seed + that settings reference.
- A **Legacy** world is byte-identical to today.
- **Existing saved worlds are untouched** — they already reference `globe:overworld_<size>`.
- The variant **does not need to round-trip as a separate field**, because the chosen `noise_settings` ID already lives in the saved generator. (Caveat: if the create-world screen is ever reopened to *edit* a world, the variant won't re-display in the UI — acceptable per the existing `GlobePending` pattern, but confirm with Maintainer.)
- The server gate (`GlobeMod` spawn/border logic) needs **no** variant branch — spawn zones and border radius are variant-independent.

### 6.5 Preset proliferation — the honest cost

Size × variant = **12 settings + 12 presets**. But the revamped *delta* (retuned continents/depth/erosion + the stretched spline) is identical across all sizes, so it factors into **shared density-function files** referenced by all six revamped settings — only the size-specific noise block differs, exactly as the six existing settings already differ only by size. The 6 revamped `world_preset` JSONs are one-line settings swaps. The real content lives in the shared revamped density-function files. **Guard against silent fallback:** the launcher silently falls back to vanilla if a preset/settings ID is missing (lines ~105–112), so all 12 pairs must be registered and a guard/log added.

---

## 7. Phased implementation roadmap

Each phase is **independently shippable** and **headless-atlas-provable**, ordered smallest-first. Every worldgen phase locks in only after passing the existing proof discipline: `latdevBandAudit` + `tools/atlas` (`band_balance_analyze.py`, `distinct_render.py` for confetti < 0.5% land) on **≥2 seeds and all world sizes**, asserting no Art X monoculture and no confetti regression vs Legacy.

### Phase 0 — Toggle plumbing, Revamped == Legacy (no visual change yet)
- Add `LatitudeWorldVariant`, the UI sub-choice, the `GlobeWorldVariant` holder, and the `_revamped` preset-ID resolution.
- Ship 6 `globe:globe_<size>_revamped` presets that point at **copies of the existing settings** (so Revamped renders *identically* to Legacy for now).
- **Provable:** create a Revamped world, confirm atlas output is byte-identical to Legacy; confirm save/reload and that existing worlds are unaffected. This de-risks all the plumbing before any content tuning. **Effort: S.**

### Phase 1 — Mercator display layer (fully decoupled, can ship in parallel)
- Add `sec(φ)` per-row column stretch + ±66–70° truncation + polar cap to `LatitudePlanisphereRenderer`.
- **Provable:** screenshot diff of the planisphere; assert truncation bound and polar cap present; assert worldgen output unchanged (it's display-only). **Effort: S.**

### Phase 2 — Revamped continent chain v1 (the heavy content lift)
- Activate + retune the dormant `globe:noise/raw_continents` + `full_continents` + `island_selector` chain into `globe:overworld/continents_revamped`: lower continentalness `xz_scale` toward ~0.0625–0.125 (≈4× larger features per halving), stretch the continentalness→offset spline so ocean-floor → coast → inland-plateau spans far more blocks and deep oceans get genuinely deep.
- **Retune `continents`, `depth`, AND `erosion` together** (the #1 Tectonic datapack failure is editing only one file) and keep `final_density` aligned.
- **Honor the OceanDistanceField contract:** keep the revamped spline's sea-level crossover at continentalness ≈ -0.19, OR parameterize `OCEAN_LIKE_THRESHOLD` per-variant via the constructor.
- **Provable:** atlas on ≥2 seeds × all sizes; assert continent count up / confetti down vs Legacy (the prototype's 17-vs-441 contrast is the target shape), no ocean-on-land desync, climate bands identical to Legacy. **Effort: M–L (this is where most of the L lives).**

### Phase 3 — Earth-like clustering + hemispheric bias
- Add domain warping to the continent coordinate (feed `shift_x`/`shift_z` or an added warp noise into the continentalness `shifted_noise`) to break blue-noise even-scatter into clustered, lobed continents.
- Add a mild hemispheric land bias (land-clustered north, mostly-ocean south) targeting the ~29/71 feel as a stopping condition.
- **Provable:** atlas land% by hemisphere; confetti audit; confirm clustering vs even scatter; confirm no high-frequency land/sea flips at the 256-block OceanDistanceField cell scale (Art VI). **Effort: M.**

### Phase 4 — Smallest-world tuning + polish
- Tune revamped feature scale so even the smallest radius (3750) fits ≥1–2 coherent landmasses; verify largest (20000) doesn't produce uncrossable oceans.
- **Provable:** atlas at min and max sizes. **Effort: S.**

### Phase 5 (optional, later) — Offline conformal Mercator for atlas renders
- Add scheme-1 reprojection to `BiomePreviewExporter`/atlas viewer for pretty offline planispheres. **Effort: S.**

### Phase 6 (optional, future) — Baked-control-map hybrid (Tessera)
- If Revamped-v1 reads too noisy, swap the continent chain for a baked plate/Voronoi control map sampled by the density-function source. Drop-in under the same toggle/preset/save story. **Effort: L+** (adds an asset pipeline).

---

## 8. Top risks + open questions for Maintainer

### Risks (ranked)

1. **OceanDistanceField desync (highest worldgen risk).** `OCEAN_LIKE_THRESHOLD = -0.19` (line 22) and `CELL_SIZE = 256` (line 20) sample the same continentalness as terrain. If the revamped spline moves the land/sea crossover, you get ocean biomes on dry land or phantom oceans. **Mitigation:** pin the revamped crossover at ≈ -0.19, or parameterize the threshold per-variant. *Verify on the atlas before locking any spline.*
2. **Coupled-density-function tuning is the L effort.** Getting big coherent continents + real deep oceans that *still align with sea level* is iterative spline work across `continents`/`depth`/`erosion`/`final_density` together. This is content, not plumbing, and it's where the schedule risk lives.
3. **Silent vanilla fallback on missing presets.** Launcher falls back to vanilla if a `globe:overworld_revamped_<size>` is missing/misnamed (lines ~105–112). Register all 12 pairs + add a guard/log.
4. **Mercator truncation/polar-cap must be enforced.** Without the ±66–70° cut, `sec(φ)` blows up the top rows. Display-only (can't corrupt worldgen) but a visible break if forgotten.
5. **Confetti / Art VI regression.** Domain-warp or spline changes could introduce high-frequency land/sea flips at the 256-block cell scale → speckled coastlines. Gate every lock-in on `distinct_render.py` (< 0.5% land confetti) + `band_balance_analyze.py`, ≥2 seeds, all sizes.
6. **Variant not persisted for re-editing.** The design relies on the `noise_settings` ID baked into the saved generator (true for MC dimension NBT). If the create screen is reopened to edit a world, the variant won't re-display — acceptable per `GlobePending` precedent, but confirm.
7. **Scope creep into true 2D Mercator.** If anyone later wants `blockX` in latitude math (a real rectangular world), that's a `WorldBorder` width≠height mixin + E/W radius threaded through every pick + EW-hazard re-tuning + a save-format break. Kept firmly out of scope here.

### Open questions for Maintainer

- **Sea-level crossover:** pin the revamped spline at continentalness ≈ -0.19, or parameterize `OceanDistanceField`'s threshold per-variant? (Parameterizing is cleaner long-term but adds the one bit of Java.)
- **Continent size target on small worlds:** how big should a Revamped continent feel at radius 3750 — one dominant landmass, or 2–3 reachable ones?
- **Hemispheric bias:** do you want the Earth-like "mostly-ocean southern hemisphere" feel, or does that fight player expectations of symmetric spawn options?
- **Mercator default:** should the planisphere default to the Mercator fan, or keep linear and make Mercator a toggle?
- **Variant re-display:** is it acceptable that reopening a world's create screen won't show its variant (per the existing pattern)?
- **Realism ceiling:** ship pure-noise Revamped (A) first and judge, or commit up front to the baked-control-map hybrid for maximum Earth-likeness?

---

## 9. Suggested names for the Revamped type

Grouped by vibe; the toggle stays "Legacy / Revamped" internally, but the player-facing label can be richer.

- **Geological / Earth-like:** *Pangaea*, *Continental*, *Tectonic*, *Terra*, *Mainland*, *Cohesive Horizons* (ties to the current release name).
- **Cartographic (pairs with the Mercator look):** *Atlas*, *Cartograph*, *Planisphere*, *Meridian*.
- **Evocative:** *True Earth*, *Old World*, *Whole Earth*, *Cohesive Continents*.

**Top picks:** **"Continental"** (clear, descriptive, pairs naturally with "Legacy") or **"Pangaea"** (evocative, instantly signals "big connected land"). For UI: **Legacy** ⇄ **Continental**.

---

## 10. Reference: the prototype image

- **Primary deliverable:** `<home>/CascadeProjects/Latitude (Globe)/tmp/worldshape-poc/comparison.png` — side-by-side Legacy vs Revamped, with header + climate-band legend.
- Legacy alone: `<home>/CascadeProjects/Latitude (Globe)/tmp/worldshape-poc/legacy_spatter.png`
- Revamped alone: `<home>/CascadeProjects/Latitude (Globe)/tmp/worldshape-poc/revamped_continents.png`
- Generator: `<home>/CascadeProjects/Latitude (Globe)/tmp/worldshape-poc/worldshape_poc.py` (deterministic `SEED=1337`, numpy+PIL only)

Both panels are ~2:1 Mercator rectangles sharing an identical latitude-tinted climate layout (gold equator band fading lime→green→teal→white toward the poles, white band-boundary lines, solid polar caps where `sec(φ)` is truncated at ±66°). Legacy is fine confetti (~441 specks / 203 islets, ~20% land); Revamped resolves into **17 coherent landmasses (4 confetti) at ~28% land with deep oceans**, clustered toward one hemisphere. The bands are identical across both panels — the visual proof of "change WHERE the coast is, never WHAT biome it is."

> **Honest caveat:** this is a stateless concept sketch. It mimics the proposal's value-noise/spline/Mercator math, **not** the actual Java density functions — so the specific shapes are illustrative of the *contrast and concept*, not the real generator's output. The real shapes come out of Phase 2's spline tuning, validated on the atlas.

---

### Bottom line for Maintainer

The plumbing is genuinely small and the save-compat story is clean *because Legacy is defined as "exactly today."* The effort is **Large**, and essentially all of it is **iterative density-function spline tuning** to get coherent continents that stay aligned with sea level and the `-0.19` OceanDistanceField crossover — not engineering. Mercator is a cheap, fully-decoupled display win that can ship in parallel. Phase 0 + Phase 1 are low-risk and shippable almost immediately; Phase 2 is where the real work — and the real payoff — lives.