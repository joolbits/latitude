# Latitude 2.0 Overhaul Plan

`status: canonical planning front door`
`date: 2026-07-02`
`active root: <home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`
`active branch at capture: feat/custom-biome-expansion-26.1.2`
`planning target: Minecraft 26.2`

This is the easy-access front door for the Latitude 2.0 overhaul. The dated external record note remains the chronological research log:

- `<external-notes>/longitude-earthlike-world-overhaul-20260702.md`

This document is the compact but comprehensive execution guide for future workers, especially Fable 5. It does not authorize release, upload, profile mutation, or source behavior changes by itself.

## Executive Decision

Latitude 2.0 should become a projected, earthlike world with coherent continents, connected ocean basins, shared geography/climate authority, and a real porting spine.

The key decisions are:

- Target Minecraft `26.2` for the Latitude 2.0 canonical implementation, after a proven pivot branch.
- Keep the 2:1 world foundation, but describe it as a `Longitude world` or `2:1 projected planet`, not literal Mercator.
- Do not revive E/W teleport wrapping, ocean seams, or terrain sinking as the 2.0 centerpiece.
- Build measurement first, then macro geography, then climate, then terrain, then boundary experience.
- Treat portability as a first-class deliverable, not cleanup after the overhaul.

## Current Red

The current 2:1 atlas can have decent biome variety while still reading as a flat rectangle. The red Atlas run `20260702-064708` showed:

- Land fraction around 64%.
- Water fraction around 36%.
- Largest land component around 95% of all land.
- Thousands of small water components.
- No dominant connected ocean basin.

Interpretation: Latitude currently reads like one giant connected land sheet with water holes. Latitude 2.0 needs macro geography before deeper climate tuning can feel believable.

## Version Strategy

### Rule

Build the overhaul on Minecraft `26.2`, not on `26.1.2` followed by a port.

### Why

The 2.0 overhaul is large enough that building it on `26.1.2` and then moving it to `26.2` repeats the old porting pain. The project should pivot first, then build the new geography and climate systems on the new canonical target.

### Verified Public Baseline

As of 2026-07-02:

- Minecraft Java `26.2` is the stable target.
- Fabric's 26.2 guidance points developers at Loom `1.17`, Gradle `9.5.1`, and stable Fabric Loader `0.19.3`.
- Fabric API `0.154.0+26.2` is available.
- `26.3` exists as snapshot work and should stay in watcher status until stable.

Sources:

- https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2
- https://fabricmc.net/2026/06/15/262.html
- https://github.com/FabricMC/fabric-api/releases

### Pivot Contract

The 26.2 pivot is its own pre-phase:

- Create an isolated branch/worktree.
- Change only build/version metadata first.
- Compile before any worldgen changes.
- Repair API and mixin drift narrowly.
- Run headless Atlas/exact-ID proof before any Modrinth profile smoke.
- Record the pivot in external record, porting docs, and release gates.

Recommended branch names:

- `port/canonical-26.2-pivot`
- `feat/latitude-2.0-26.2`

Recommended commit sequence:

- `build: retarget Latitude to Minecraft 26.2`
- `port: repair 26.2 API drift`
- `test: restore 26.2 atlas proof`
- `docs: record canonical 26.2 pivot`

Recommended save tags after proof only:

- `save/canonical-26.2-baseline`
- `save/lat2-portability-foundation`

## Portability Spine

Future ports should not be hand-transplants of `LatitudeBiomes.java`.

### Target Layers

Core layer:

- Pure Java.
- No Minecraft imports.
- Owns latitude math, projected-planet math, noise helpers, climate/geography summaries, biome-family taxonomy, and analyzer fixtures.

Platform adapter layer:

- Owns `Holder<Biome>`, registries, Fabric/Mojang mapping surfaces, `Climate.Sampler`, chunk-generator integration, and tag/provider access.

Mixin hook layer:

- Thin, named entrypoints.
- Calls adapters or core.
- Each mixin has documented owner, method, signature, reason, and proof that it applies.

Data/config layer:

- Biome-family taxonomy.
- Climate classes.
- World-size definitions.
- Analyzer expected fixtures.
- Provider-pack policy.

Proof layer:

- Pure JVM unit tests for core logic.
- Synthetic atlas fixtures.
- Headless Atlas integration proof.
- Exact-ID biome proof.
- Live Modrinth smoke only after deterministic proof.

### Success Criterion

A future Minecraft version should mostly touch:

- Build files.
- Adapter files.
- Mixin target descriptors.
- Version matrix/docs.

If a future worker must recopy the climate algorithm into another branch by hand, the 2.0 portability work failed.

## World Shape Model

The 2:1 world remains useful because a projected planet naturally has more east-west room than north-south distance. But the implementation is not a true Mercator projection and not a spherical topology.

Use these terms:

- `Longitude world`
- `2:1 projected planet`
- `projection edge`

Avoid these as technical promises:

- Literal Mercator.
- Seamless wrap.
- Antimeridian, unless only discussing atlas/design language.
- Real sphere.

Boundary goal:

- Less wall-like.
- Mostly ocean, ice, storm, whiteout, hostile sea, or other intentional climate edge.
- No major continents across the edge unless a separate topology spike proves continuity.
- No false promise that players can see or travel seamlessly around a sphere.

## Architecture

### GeoAuthority

`GeoAuthority` owns macro geography before climate tries to explain it.

Minimum summary fields:

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
- reserved hydrology fields: `drainageBasinId`, `flowDirection`, `coastOutletId`

Rules:

- Seed/radius/world-shape aware.
- Deterministic.
- Cheap in the hot path.
- Provides a `sample(blockX, blockZ)` path for worldgen and a separate slower analyzer path.
- No unbounded BFS, fluid simulation, repeated height probes, or global synchronized locks in the biome resolver.
- Visible in Atlas before it changes biome selection.

### ClimateAuthority

`ClimateAuthority` should be a shared summary, not another pile of final clamps inside `LatitudeBiomes.pick()`.

Minimum summary fields:

- `latitudeDeg`
- `band`
- `temperature01`
- `altitudeCooling01` or terrain-height proxy
- `continentality01`
- `prevailingWindX`
- `prevailingWindZ`
- `upwindOceanFetchBlocks`
- `precipitation01`
- `windwardLift01`
- `rainShadow01`
- `currentModifier01`
- `seasonalityClass`
- `climateClass`
- `diagnosticFlags`

Rules:

- Rain shadows require real mountain/ruggedness truth. If that truth is missing, rain-shadow output is diagnostic only.
- Monsoon is a regional seasonality class, not a global wetness boost.
- Ocean currents are schematic and basin-relative, not hard-coded east/west stripes.
- Climate chooses biome families; final exact biome IDs remain tag/provider-aware.
- Biomes, features, structures, Atlas overlays, and `/latdev` probes must consume the same summary.

## Measurement First

Phase A is not optional. Build the analyzer before visible behavior.

Analyzer requirements:

- Raw land/water/coast shares.
- Cosine-latitude-weighted land/water/coast shares.
- Biome water, terrain water, and intended ocean-basin authority separated.
- Land components.
- Ocean-basin components.
- River/lake components.
- Coast components.
- Largest landmass share, raw and area-weighted.
- Largest ocean-basin share, raw and area-weighted.
- Projection-edge composition by latitude band.
- Bridge sensitivity for one-cell rivers and one-cell isthmuses.
- Size-aware gates for Itty, Small, Regular, Large, and Massive worlds.
- Synthetic fixture tests before trusting real Atlas runs.

Proof rules:

- Exact-ID biome artifacts are biome truth.
- Height export is required for terrain-water truth.
- Step64 is iteration only; use step16 or better for final evidence when affordable.
- Every report records seed, radius, step, aspect, source commit, build/profile jar if relevant, and raw-vs-area-weighted status.

## Roadmap

### Phase -2: Version Truth And 26.2 Availability

Document review:

- `AGENTS.md`
- `<external-notes>/index.md`
- `<external-notes>/update-contract.md`
- `<external-notes>/evidence-registry.md`
- `<external-notes>/longitude-earthlike-world-overhaul-20260702.md`
- `docs/porting/PORTING.md`
- `docs/porting/VERSION_MATRIX.md`

Objective:

- Confirm the 26.2 target and toolchain before any branch or code changes.

Action plan:

- Verify upstream Minecraft/Fabric/Fabric API/Loom/Gradle/Java truth.
- Confirm current local root, branch, HEAD, tags, dirty tree.
- Write or refresh version matrix.

Potential problems:

- Companion biome mods may lag 26.2.
- Fabric API/Loader/Loom versions can move.
- 26.3 snapshots can distract from stable 26.2.

Commits/tags/pushes:

- No code commit required unless docs are stale.
- No tag.
- No push unless docs update is intentionally saved.

Documentation:

- external record note.
- `docs/porting/VERSION_MATRIX.md`.

### Phase -1: Canonical 26.2 Pivot

Document review:

- Version matrix.
- Porting front door.
- Current release gates.
- Existing 26.1.2 build files.

Objective:

- Make the existing 2.0 line compile and run deterministic proof on 26.2 before earthlike behavior starts.

Action plan:

- Create/switch to the agreed 26.2 branch/worktree.
- Update build metadata only.
- Run compile.
- Repair API/mixin drift narrowly.
- Run headless Atlas and exact-ID proof.
- Stage profile smoke only after deterministic proof.

Potential problems:

- Mixin signatures.
- Client render API drift.
- World-creation UI drift.
- Stale jars in Modrinth profile.
- Server/worldgen/HUD issue regressions.

Commits/tags/pushes:

- Narrow build/port/test/docs commits.
- Local save tag after proof.
- Push only with Maintainer authorization.

Documentation:

- external record.
- `docs/porting/VERSION_MATRIX.md`.
- `docs/release/current-gates.json` when implementation truth changes.

### Phase 0: Portability Foundation

Document review:

- Porting front door.
- Portability architecture.
- Port risk files.

Objective:

- Split pure world logic from Minecraft adapter/mixin logic.

Action plan:

- Add pure no-op summary types.
- Add adapter interfaces.
- Keep behavior disabled by default.
- Add pure tests.
- Prove flag-off output unchanged.

Potential problems:

- Over-refactor.
- Accidentally changing output.
- Static world state leaking across loads.

Commits/tags/pushes:

- Architecture commits only after tests and flag-off proof.
- Save tag after no-op foundation proof.

Documentation:

- `docs/porting/PORTABILITY_ARCHITECTURE.md`.
- `docs/porting/ADAPTER_MAP.md` when adapters exist.

### Phase 1: Measurement Harness

Document review:

- Current overhaul plan.
- Existing Atlas tools.
- Band correctness and cohesion tools.

Objective:

- Make the current red measurable and repeatable.

Action plan:

- Add analyzer fixtures.
- Run old red `20260702-064708`.
- Run a fresh current-HEAD baseline.
- Add Atlas overlay plan after CLI report is trusted.

Potential problems:

- Color-PNG-only metrics.
- River/ocean confusion.
- Resolution-sensitive components.
- Area weighting forgotten.

Commits/tags/pushes:

- Analyzer/test/docs commit.
- No behavior tag.

Documentation:

- external record evidence.
- Analyzer methodology.

### Phase 2: GeoAuthority Prototype

Document review:

- GeoAuthority plan.
- Analyzer red/green reports.
- Projection/boundary design.

Objective:

- Produce coherent macro land/ocean/continent/ocean-basin intent behind an opt-in flag.

Action plan:

- Core pure logic first.
- Atlas overlays second.
- Opt-in biome consumer third.

Potential problems:

- Too little playable land.
- Uniform ocean moat at projection edge.
- Hot-path cost.
- Macro geography diverges from terrain.

Commits/tags/pushes:

- Save tag only when metrics improve and flag-off output is unchanged.

Documentation:

- GeoAuthority design note.
- Metrics delta external record note.

### Phase 3: ClimateAuthority Prototype

Document review:

- Climate model plan.
- GeoAuthority outputs.
- Existing ProvinceAuthority behavior.

Objective:

- Make climate explainable, shared, and earthlike enough to guide biome families.

Action plan:

- Wind belts.
- Ocean fetch.
- Continentality.
- Seasonality classes.
- Diagnostic rain shadows where terrain truth is not ready.

Potential problems:

- Monsoon over-wetting.
- Hard-coded ocean currents.
- Feature/structure drift.
- Hidden one-off clamps creeping back into `LatitudeBiomes.pick()`.

Commits/tags/pushes:

- Climate opt-in savepoint after metrics and proof.

Documentation:

- Climate model note.
- Acceptance table.

### Phase 4: Terrain Integration Spike

Document review:

- Active density-function docs.
- Height export status.
- Prior ocean-seam failure records.

Objective:

- Decide whether and how terrain can follow macro geography safely.

Action plan:

- Audit active density functions.
- Enable height export if needed.
- Try one narrow hook only after measurement/geography layers are green.
- Run all-thread Spark proof.

Potential problems:

- Recreating the ocean-sink seam failure.
- Broad `NoiseRouter` surgery.
- Severe generation stalls.
- Terrain-water proof blocked by missing height export.

Commits/tags/pushes:

- Spike branch/tag only if proof green.
- Otherwise write blocked report and stop.

Documentation:

- Terrain go/no-go decision note.

### Phase 5: Boundary Experience

Document review:

- Projection-edge plan.
- Boundary UX records.
- Current warning/overlay behavior.

Objective:

- Make the world edge intentional and less wall-like without claiming seamless topology.

Action plan:

- Bias projection edge toward ocean/ice/storm geography.
- Adjust warning language and visuals.
- Run live visual proof after Atlas green.

Potential problems:

- False wrap promise.
- Boring ocean moat.
- Land cliffs at edge.
- Pretty live view with bad exact-ID truth.

Commits/tags/pushes:

- Visual/UX commit after live proof.

Documentation:

- Boundary proof note.

### Phase 6: Release Candidate Hardening

Document review:

- external record evidence.
- Release gates.
- Live proof runbook.
- Porting status.

Objective:

- Make the 2.0 candidate believable, performant, and documented.

Action plan:

- Matrix seeds/sizes.
- Mod-present proofs.
- All-thread profiles.
- Live flyovers.
- Docs closure.

Potential problems:

- Atlas green but live ugly.
- Live pretty but exact-ID wrong.
- Proof clutter mistaken for release truth.
- Porting or publication started before Maintainer authorizes it.

Commits/tags/pushes:

- Release-candidate tag/push only with Maintainer authorization.

Documentation:

- Handoff.
- external record.
- Release gates.
- Lessons if a durable rule changed.

## Hard Stops

Stop immediately if:

- The worktree has unexplained source/runtime dirt in files the slice would touch.
- 26.2 coordinates are unavailable or contradictory.
- `compileJava` fails from broad API drift and the second repair pass cannot isolate a small cause.
- A mixin target cannot prove it applies.
- Flag-off output differs for Classic/current Longitude.
- Analyzer cannot distinguish biome water, terrain water, and intended ocean basin.
- Fresh baseline disagrees with old-red assumptions and no one can explain why.
- New authority code leaks seed/radius/shape across consecutive world loads.
- Spark or counters show generation stalls regressing.
- Terrain proof needs broad density rewiring before measurement and authority are green.
- A worker tries to fix visual ugliness with another one-off biome clamp instead of shared authority logic.

## Fable 5 First Tasks

1. Refresh `docs/porting/PORTING.md` and `docs/porting/VERSION_MATRIX.md`.
2. Create/switch to the agreed 26.2 pivot branch/worktree.
3. Bump build metadata only.
4. Repair 26.2 compile/mixin/API drift narrowly.
5. Add the Atlas geography analyzer with synthetic fixtures.
6. Run old-red plus fresh-baseline reports.
7. Add no-op `GeoSummary` and `ClimateSummary` contracts behind disabled flags.
8. Prove flag-off output unchanged.

Do not start with visible continent generation. The first green is a proven 26.2 baseline plus a measurement harness that can show the current red honestly.

## Linked Docs

- `<external-notes>/longitude-earthlike-world-overhaul-20260702.md`
- `<external-notes>/current-state-handoff-20260701.md`
- `<external-notes>/test1-live-findings-20260701.md`
- `<external-notes>/atlas-worldshape-longitude-20260701.md`
- `docs/26.1.2-canonical-roadmap.md`
- `docs/design/revamped-world-shape.md`
- `docs/design/horizontal-wrapping-feasibility-20260623.md`
- `docs/design/ocean-seam-wrap-plan-20260623.md`
- `docs/porting/PORTING.md`
- `docs/porting/VERSION_MATRIX.md`
- `docs/porting/PORTABILITY_ARCHITECTURE.md`
