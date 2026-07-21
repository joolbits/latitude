# Slice C-4 design — bathymetric relief: abyssal profile + tectonic trenches (2026-07-08)

`status: DESIGN ONLY — not authorized to build; Peetsa exploring the "deeper oceans / earth-y trenches" idea`
Origin: after the C-3 wall fix live-closed (continental shelves confirmed, TEST 30), Peetsa asked about
deepening oceans "or even beyond" — "trenches far out, or just kind of earth-y where nonexistent
tectonic plates / fault lines would create trenches." This doc scopes that as a bounded slice and, most
importantly, records that the geology it needs **already exists in GeoAuthority** — trenches here would
emerge from the same plate model that already places island arcs and mountain belts, not a bolt-on.

## The key finding: the signals already exist and are already exposed

`GeoSummary` (the per-column record the carve function already consumes for `shelf01`) carries, populated
by `GeoAuthority.summarize()` when `geoV2` is on:

- `coastDistanceBlocks` — signed distance to the nearest coast (negative offshore). The natural driver of
  an earth-like **bathymetric profile**: shelf → continental slope → abyssal plain, deepening with
  distance. Already used to derive `shelf01`.
- `islandArc01` — ocean-side **island-arc intent**, computed as `seamProx · typeContrast · arcN`:
  - `seamProx` = proximity to a **plate seam** (the boundary between two plate cells).
  - `typeContrast` = 1.0 when the two adjacent plates are **different types** (oceanic vs continental),
    0.3 otherwise. Different-type convergent boundary = a **subduction zone** — precisely where Earth's
    deep ocean trenches form (Mariana, Japan, Aleutian, Peru-Chile).
  - So `islandArc01` is high exactly along the arc-trench systems, and on Earth the trench sits on the
    ocean side of the arc. This is a ready-made "put a trench here" signal.
- `oceanBasinId` — connected-component ocean basin identity (deepen basin interiors distinctly if wanted).
- `archipelago01`, `ruggednessIntent01` — ocean-side texture signals (could roughen trench flanks / seamounts).

**Consequence:** C-4 needs ZERO changes to the geology engine. It's a change to the carve's depth formula
only — consuming already-computed, already-exposed per-column signals, the same pattern as `shelf01`
today. Zero extra per-column cost (these are already in the `GeoSummary` the carve fetches).

## Available vertical headroom

World build floor is Y=-64 (bedrock ~-64..-59; deepslate begins ~Y0-8). Current ocean floor bottoms at
Y39 (24 blocks deep at S=0.4). A trench floor at ~Y-20 would be **~83 blocks deep** — more than triple
today's oceans, unmistakably abyssal, and still comfortably above bedrock. "Beyond vanilla" is real:
vanilla never uses that range for oceans.

## Proposed depth model (three composable terms, all feeding the existing carve target Y*)

Today: `Y* = SEA_LEVEL − |S·r| · K_DEPTH_BLOCKS(60) · sm(land01) · shelfApron(shelf01)`, clamped/gripped
by C-3. C-4 keeps the C-3 grip and the C-2 min()/prelim machinery unchanged and enriches the DEPTH:

1. **Abyssal profile (broad, earth-like):** let depth keep growing with `coastDistanceBlocks` beyond the
   shelf, toward an `ABYSSAL_MAX_BLOCKS` plateau in basin interiors — replacing today's flat offshore
   depth with shelf → slope → abyssal plain. Knob: `abyssalMaxDepth` (e.g. 40-50 blocks).
2. **Tectonic trench (narrow, dramatic):** add extra depth proportional to `islandArc01` (or, more
   directly, the ocean-side `seamProx · typeContrast` before the arc-noise term if we want the trench to
   track the fault rather than the arc islands), up to `TRENCH_EXTRA_BLOCKS` at the seam, falling off
   sharply to each side so it's a gash, not a wide basin. Knobs: `trenchDepth`, `trenchWidth`.
3. **Uniform floor (baseline):** the existing `K_DEPTH_BLOCKS` stays the everywhere-minimum; raising it
   alone is the "just deeper flat seas" cheap option if Peetsa wants only that.

All three sum into one deeper `Y*`, then flow through the **unchanged** C-2 min()-clamp + prelim-surface
flooding + C-3 grip. Because the pipeline downstream of `Y*` is untouched, the structural safety proofs
(no slab, no hollow — L22) carry over by construction.

## Hard design constraints (carried from earned lessons)

- **Grade the trench walls (L22).** A trench is a linear carve-onset; its sides are exactly the C-3 wall
  failure family. The trench term must ramp its onset (reuse the grip idea across the trench's cross
  -section), or we reintroduce sheer underwater walls. Design the graded cross-section from the start.
- **Verify deep flooding.** The prelim-surface wrapper floods carved seas; a ~80-block carve is far deeper
  than anything tested. Aquifer/fluid behavior at that depth is a required gate (air-pocket / perched
  -water check), not an assumption.
- **Audit with the density ladder, not getBaseHeight (L24).** Depth verification must read the ground
  -truth density field; the `*_WG` estimators misreport under the carve.
- **Keep the gap-delta + r=0 byte-identity + coherence gates (C-2/C-3 gate set).** r=0 must stay
  byte-identical; no column may gain voids/fragments.
- **Live-tunable (Peetsa at the keyboard).** Every magnitude a `-D latitude.terrainV2.*` knob
  (`abyssalMaxDepth`, `trenchDepth`, `trenchWidth`), forwarded in build.gradle (L17), so Peetsa dials it
  in-world like the amplitude/grip work.

## Recommended first step (cheap, high-value, do this before carving anything)

**Map where the trenches would fall on Peetsa's seed.** Add `islandArc01` (and `seamProx`,
`typeContrast`, `coastDistanceBlocks`) to the TerrainProofHarness coherence probe output, run the 81-grid
on her seed, and render/print where the subduction seams and arcs actually are. This (a) confirms the
signal is non-trivial on her world before we build on it, and (b) IS the "here's where your world's
Mariana Trench would be" preview Peetsa wanted. Bounded, read-only, no worldgen change.

## Sequencing / relationship to other open work

- Independent of the biome-side decoupling work (L21) — C-4 is terrain-only. But note: deeper oceans will
  make the phantom-ocean-label class (TEST 28) more prominent, so the coastline-label alignment is a
  natural companion.
- Does not gate, and is not gated by, the consumer law-compliance slice or Phase 5.
- Cheap alternative if Peetsa wants depth tonight without the slice: raise `K_DEPTH_BLOCKS` (uniform
  deepen) — 5-minute knob, gets abyssal-flat but no trenches.

## Decision points for Peetsa
1. Just deeper flat seas now (uniform `K_DEPTH` bump), or the full C-4 relief slice?
2. If C-4: start with the trench-location MAP on your seed (recommended), or go straight to the carve?
3. Trench tracks the ARC (`islandArc01`, trench beside island chains) or the raw FAULT
   (`seamProx·typeContrast`, trench on the bare subduction line even where no arc islands formed)?
