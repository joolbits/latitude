# Latitude Portability Architecture

`status: target architecture, Phase 0 no-op scaffolding landed`
`updated: 2026-07-03`

This document describes the target structure for making future Latitude Minecraft-version ports less painful.

## Phase 0 scaffolding status (2026-07-03)

Phase 0 (`docs/LATITUDE_2_0_OVERHAUL.md`) added the no-op contracts and adapter shells below, all behind
disabled flags, with a flag-off byte-identical proof against `save/canonical-26.2-baseline`. Full detail in
`docs/binder/phase0-portability-foundation-20260703.md`.

- **`GeoSummary`: IMPLEMENTED behind `latitude.geoV2.enabled` (Phase 2, 2026-07-03).**
  `com.example.globe.core.geo.GeoAuthority` populates real macro-geography intent (coherent continents +
  a dominant connected ocean basin) into all 15 fields except the reserved hydrology trio
  (`drainageBasinId`/`flowDirection`/`coastOutletId`, still neutral). Flag defaults off → worldgen
  byte-identical; the Phase 2 consumer discards the summary (does not yet drive biome selection). Design:
  `docs/design/geoauthority-design-20260703.md`; proof: `docs/binder/phase2-geoauthority-20260703.md`.
- **`ClimateSummary`: IMPLEMENTED behind `latitude.climateV2.enabled` (Phase 3, 2026-07-03).**
  `com.example.globe.core.climate.ClimateAuthority` ("Fetch & Lift") populates all 14 fields with a real
  earthlike climate transported from Phase 2's geography (bounded upwind ocean-fetch, three-cell winds,
  basin-relative currents, diagnostic orography). `seasonalityClass`/`climateClass` are now typed
  taxonomies (`SeasonalityClass`, `ClimateClass`) with a vanilla biome family per class. Flag defaults
  off → worldgen byte-identical; the Phase 3 consumer discards the summary (does not yet drive biome
  selection). Design: `docs/design/climateauthority-design-20260703.md`; proof:
  `docs/binder/phase3-climateauthority-20260703.md`.
- **Flags: implemented.** `latitude.geoV2.enabled` / `latitude.climateV2.enabled`
  (`com.example.globe.core.LatitudeV2Flags`), both default `false`.
- **Biome registry lookup / tag membership / Holder conversion / Climate.Sampler adapters: scaffolded,
  not wired.** Four interfaces under `com.example.globe.adapter.biome` and
  `com.example.globe.adapter.climate`, each with one trivial pass-through implementation. Nothing calls
  them yet — they exist so a future port or Phase 2/3 consumer has a stable seam to replace instead of
  touching `LatitudeBiomes` directly.
- **Disabled-by-default call site: wired.** Both `LatitudeBiomes.pick(...)` overloads call
  `NoOpGeoSummaryProvider`/`NoOpClimateSummaryProvider` behind their respective flags. Dead code while
  the flags stay off; this is the seam Phase 2/3 replace.

## Problem

Latitude worldgen logic, Minecraft API calls, mapping names, registry lookups, Fabric hooks, and mixin targets are currently too tangled. When the project moves to a new Minecraft version, the work can feel like rebuilding the mod from scratch.

The 2.0 overhaul must fix this before adding more climate complexity.

## Target Layers

### 1. Core Logic

Pure Java. No Minecraft imports.

Owns:

- Latitude and longitude/projection math.
- Seed/radius/world-shape coordinate helpers.
- Low-frequency noise helpers.
- `GeoSummary`.
- `ClimateSummary`.
- Biome-family taxonomy.
- Climate-class thresholds.
- Synthetic analyzer fixtures.

Must not own:

- `Holder<Biome>`.
- Registries.
- Fabric API.
- Mixins.
- Chunk generator classes.
- Client rendering classes.

### 2. Platform Adapters

Minecraft/Fabric-specific boundary code.

Owns:

- Biome registry lookup.
- Tag/provider membership.
- `Holder<Biome>` conversion.
- `Climate.Sampler` access.
- Chunk-generator and biome-source entrypoints.
- Version-specific API compatibility.

Adapters should be small enough that future port workers can replace or repair them without reading the full climate algorithm.

### 3. Mixin Hooks

Thin integration points.

Each mixin should document:

- Target owner.
- Target method/signature.
- Injection reason.
- What adapter/core method it calls.
- Proof that the target applies in the current Minecraft version.

Mixins should not contain climate/geography policy.

### 4. Data And Config

Owns:

- Biome-family tables.
- Provider-pack policy.
- Climate-class thresholds.
- Fixture seeds and expected analyzer outputs.
- World-size gates.

Config should make representation/balance easier to tune without editing hot-path Java.

### 5. Proof Tools

Owns:

- Pure JVM tests.
- Synthetic atlas fixtures.
- Headless Atlas integration proof.
- Exact-ID analyzers.
- Area-weighted continent/ocean metrics.
- Performance counters.

Live proof comes last, after deterministic proof.

## Required Contracts

### GeoSummary

Minimum intended fields:

- `land01`
- `isOceanIntent`
- `continentId`
- `oceanBasinId`
- `coastDistanceBlocks`
- `shelf01`
- `islandArc01`
- `archipelago01`
- `mountainIntent01`
- `orogenId`
- `ruggednessIntent01`
- `projectionEdgeSuitability01`
- `drainageBasinId`
- `flowDirection`
- `coastOutletId`

### ClimateSummary

Minimum intended fields:

- `latitudeDeg`
- `band`
- `temperature01`
- `altitudeCooling01`
- `continentality01`
- `prevailingWindX`
- `prevailingWindZ`
- `upwindOceanFetchBlocks`
- `precipitation01`
- `windwardLift01`
- `rainShadow01`
- `currentModifierSigned` (SIGNED [-1,+1], unlike the `*01`-suffixed fields above which are [0,1]
  magnitudes -- renamed 2026-07-05, sweeper audit #2 finding #8, to stop implying a magnitude contract
  the field never had)
- `seasonalityClass`
- `climateClass`
- `diagnosticFlags`

## Flags

Behavior-changing layers should be opt-in until proven:

- `latitude.geoV2.enabled=false`
- `latitude.climateV2.enabled=false`

Exact property names can change during implementation. The contract cannot: measurement first, behavior disabled by default, flag-off regression proof before opt-in behavior.

## Hot-Path Rules

- One memoizable summary per `(blockX, blockZ)` column in resolver paths.
- No unbounded global maps keyed by every sampled coordinate.
- No synchronized global BFS in generation hot paths.
- No repeated height probes inside biome selection.
- No fluid simulation inside biome selection.
- Separate slow analyzer/debug sampling from worldgen sampling.

## Future-Port Success Test

A future Minecraft port should mostly change:

- Build metadata.
- Platform adapters.
- Mixin signatures.
- Version matrix docs.

If the port needs to re-copy geography or climate algorithms by hand, this architecture has not done its job.
