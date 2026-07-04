# Latitude 2.0 Overhaul Plan

`status: canonical planning front door`
`date: 2026-07-02`
`active root: <home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`
`active branch at capture: feat/custom-biome-expansion-26.1.2`
`planning target: Minecraft 26.2`

This is the easy-access front door for the Latitude 2.0 overhaul. The dated binder note remains the chronological research log:

- `docs/binder/longitude-earthlike-world-overhaul-20260702.md`

This document is the compact but comprehensive execution guide for future workers, especially Fable 5. It does not authorize release, upload, profile mutation, or source behavior changes by itself.

## Executive Decision

Latitude 2.0 should become a projected, earthlike world with coherent continents, connected ocean basins, shared geography/climate authority, and a real porting spine.

The key decisions are:

- Target Minecraft `26.2` for the Latitude 2.0 canonical implementation, after a proven pivot branch.
- Keep the 2:1 world foundation, but describe it as a `Longitude world` or `2:1 projected planet`, not literal Mercator.
- Do not revive E/W teleport wrapping, ocean seams, or terrain sinking as the 2.0 centerpiece.
- Build measurement first, then macro geography, then climate, then terrain, then boundary experience.
- Treat portability as a first-class deliverable, not cleanup after the overhaul.

## Execution Model & Reasoning-Effort Strategy

Read `docs/binder/model-effort-strategy-20260702.md` before starting any phase below. Short version: most of
this roadmap (Phases -2, -1, 0, 1, 5, 6) is bounded, test-gated engineering work suited to a fast/cheap model at
low-to-medium reasoning effort. The two phases that genuinely warrant premium reasoning are **Phase 2
(GeoAuthority)** and **Phase 3 (ClimateAuthority)** — from-scratch algorithm design with the highest blast
radius. That note also answers whether a thread can self-select model/effort automatically as it works (short
answer: it can delegate bounded sub-tasks to a chosen model via the `Agent`/`Workflow` tools' `model` param, but
cannot change its own ambient model without the user running `/model`), and states plainly that Fable is not in
the default rotation for this project (cost/cadence mismatch with a many-small-phases roadmap, not a capability
judgment).

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
- Record the pivot in binder, porting docs, and release gates.

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
- `docs/binder/index.md`
- `docs/binder/update-contract.md`
- `docs/binder/evidence-registry.md`
- `docs/binder/longitude-earthlike-world-overhaul-20260702.md`
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

- Binder note.
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

- Binder.
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

- Binder evidence.
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
- Metrics delta binder note.

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

- Binder evidence.
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
- Binder.
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

## First Tasks

This checklist was originally addressed to Fable 5 by name; it is generalized now (see the Execution Model
section above — Fable is not in the default rotation). The tasks themselves are unchanged and apply regardless
of which model/thread executes them:

1. Refresh `docs/porting/PORTING.md` and `docs/porting/VERSION_MATRIX.md`.
2. Create/switch to the agreed 26.2 pivot branch/worktree.
3. Bump build metadata only.
4. Repair 26.2 compile/mixin/API drift narrowly.
5. Add the Atlas geography analyzer with synthetic fixtures.
6. Run old-red plus fresh-baseline reports.
7. Add no-op `GeoSummary` and `ClimateSummary` contracts behind disabled flags.
8. Prove flag-off output unchanged.

Do not start with visible continent generation. The first green is a proven 26.2 baseline plus a measurement harness that can show the current red honestly.

## Kickoff Slice Prompt (Phase -2 + Phase -1 only)

Copy-pasteable prompt for starting a fresh thread on the first slice. Deliberately scoped to just items 1-4 of
First Tasks above (Version Truth + the 26.2 pivot) — items 5-8 (analyzer, no-op summary contracts) are separate
slices with their own working cards, not bundled into this one.

```
Kick off Latitude 2.0 overhaul work: Phase -2 (Version Truth) + Phase -1 (Canonical 26.2 Pivot) only.
Stop before Phase 0 or Phase 1 — those are separate slices with their own working cards.

MODEL: Sonnet, low-to-medium reasoning effort. This is bounded, compiler/proof-gated work,
not novel design — do not switch to Opus or run ultracode for this slice. See
docs/binder/model-effort-strategy-20260702.md for why.

READ FIRST (in order):
1. docs/LATITUDE_2_0_OVERHAUL.md (front door — read the whole thing)
2. docs/binder/model-effort-strategy-20260702.md
3. docs/binder/longitude-earthlike-world-overhaul-20260702.md
4. docs/porting/PORTING.md and docs/porting/VERSION_MATRIX.md
5. docs/LESSONS.md (main worktree) — especially L7 (working card discipline)

WORKING CARD — fill this in before touching anything:
- Objective: prove the existing 2.0-beta.1 line compiles and passes deterministic
  headless proof on Minecraft 26.2, with build-metadata-only changes. No worldgen
  behavior changes, no visible continent/geography work.
- Root/profile: confirm current root, branch, HEAD, and Modrinth profile truth
  before any edit (repo preflight per LESSONS L3).
- Allowed work: verify 26.2/Fabric/Fabric-API/Loom/Gradle/Java toolchain truth;
  create/switch to an isolated 26.2 pivot branch or worktree (recommended name
  `port/canonical-26.2-pivot` or `feat/latitude-2.0-26.2`); bump build/version
  metadata ONLY; run compileJava; repair API/mixin drift NARROWLY; run the
  existing headless Atlas + exact-ID biome proof to confirm nothing broke.
- Forbidden lanes: no GeoAuthority/ClimateAuthority code, no analyzer work, no
  portability-layer scaffolding, no visible geography/continent behavior, no
  Modrinth profile smoke until deterministic proof is green, no tag/push
  without explicit authorization.
- Proof gate: compileJava green AND headless Atlas/exact-ID proof green on the
  new 26.2 branch, with flag-off/Classic output unchanged from the 26.1.2
  baseline.
- Stop condition (pulled verbatim from the plan's Hard Stops):
  - the worktree has unexplained source/runtime dirt in files this slice touches
  - 26.2 coordinates are unavailable or contradictory
  - compileJava fails from broad API drift and a second repair pass can't isolate
    a small cause
  - a mixin target can't prove it applies
  - flag-off output differs for Classic/current Longitude
  If any of these trigger, stop and write up what's blocking rather than pushing
  through with a bigger hammer (that's also the cue to reconsider model/effort
  per the strategy doc, not to brute-force it on the current tier).

DELIVERABLE: a dated binder note recording what was verified, the branch/worktree
created, the before/after build metadata, compile + Atlas/exact-ID proof results,
and an updated docs/porting/VERSION_MATRIX.md. Local commits only — ask before
tagging or pushing. End by stating clearly whether Phase 0 (portability
foundation) is next, or whether something needs Peetsa's input first.
```

## Kickoff Slice Prompt (Phase 0 only)

Added 2026-07-03, once Phase -2 + Phase -1 proof-completed and were tagged `save/canonical-26.2-baseline`
(HEAD `93c21a6a` on `port/canonical-26.2-pivot`, pushed). Copy-pasteable prompt for starting a fresh thread on
the Portability Foundation slice, following the same discipline as the prompt above.

```
Kick off Latitude 2.0 overhaul work: Phase 0 (Portability Foundation) only.
Stop before Phase 1 (Measurement Harness) -- that is a separate slice with its own working card.

MODEL: Sonnet, low-to-medium reasoning effort. This is bounded, test-gated scaffolding
work, not novel design -- do not switch to Opus or run ultracode for this slice. See
docs/binder/model-effort-strategy-20260702.md for why.

READ FIRST (in order):
1. docs/LATITUDE_2_0_OVERHAUL.md (front door -- read the whole thing, especially Phase 0)
2. docs/porting/PORTABILITY_ARCHITECTURE.md (the 5-layer target structure and the
   GeoSummary/ClimateSummary field contracts this slice scaffolds)
3. docs/porting/PORTING_RISK_FILES.md (files a future port would touch -- do not further
   entangle these while adding adapter shells)
4. docs/binder/model-effort-strategy-20260702.md
5. docs/porting/VERSION_MATRIX.md (confirms the Phase -1 proof-complete state and the
   save/canonical-26.2-baseline tag this slice builds on)

WORKING CARD -- fill this in before touching anything:
- Objective: split pure world logic from Minecraft adapter/mixin logic per
  docs/porting/PORTABILITY_ARCHITECTURE.md's 5-layer target structure (Core Logic /
  Platform Adapters / Mixin Hooks / Data+Config / Proof Tools). Add no-op GeoSummary
  and ClimateSummary contracts (field lists already specified in that doc) and thin
  adapter interfaces, all behind disabled flags (latitude.geoV2.enabled=false,
  latitude.climateV2.enabled=false). No behavior change of any kind.
- Root/profile: confirm current root, branch, HEAD, and Modrinth profile truth before
  any edit (repo preflight per LESSONS L3). Expected: port/canonical-26.2-pivot, HEAD
  93c21a6a or later, tag save/canonical-26.2-baseline present and pushed.
- Allowed work: add pure-Java no-op GeoSummary/ClimateSummary record/class types (zero
  Minecraft imports, live in the Core Logic layer); add thin adapter interface shells
  for the Platform Adapters layer (biome registry lookup, tag/provider membership,
  Holder<Biome> conversion, Climate.Sampler access) -- interfaces plus trivial
  pass-through implementations only, no new algorithm; add the two disabled feature
  flags; add pure JVM tests for the no-op types; prove flag-off output is byte-identical
  (headless Atlas + exact-ID) before and after against the save/canonical-26.2-baseline
  tag.
- Forbidden lanes: no GeoAuthority/ClimateAuthority algorithm work (Phase 2/3), no
  analyzer/measurement work (Phase 1), no visible geography/climate behavior change, no
  touching existing biome-selection hot paths beyond wiring in a disabled-by-default
  call site, no tag/push without explicit authorization. Do not "clean up while in
  there" -- e.g. the confirmed-dead ZoneEntryNotifier.java/ui/ZoneTitleOverlay.java
  (already flagged as its own follow-up, task_7003cfac, still open) is exactly the kind
  of adjacent temptation to leave alone unless the user asks for it in this slice.
- Proof gate: compileJava green; new pure-Java tests green; headless Atlas + exact-ID
  proof byte-identical to the save/canonical-26.2-baseline tag with both flags left at
  their disabled defaults.
- Stop condition (pulled verbatim from the plan's Hard Stops, filtered to this slice):
  - flag-off output differs for Classic/current Longitude at all
  - new authority code leaks seed/radius/shape across consecutive world loads
  - static world state leaks across loads
  - a worker is tempted to over-refactor beyond the minimum no-op/adapter shells
  If any of these trigger, stop and write up what's blocking rather than pushing
  through with a bigger hammer.

DELIVERABLE: a dated binder note recording what layers/types/flags were added, the
before/after compile + test + Atlas/exact-ID proof results, and an updated
docs/porting/PORTABILITY_ARCHITECTURE.md marking which contracts are now scaffolded
vs. still target-only. Local commits only -- ask before tagging or pushing. End by
stating clearly whether Phase 1 (Measurement Harness) is next, or whether something
needs Peetsa's input first.
```

## Kickoff Slice Prompt (Biome Consumer only)

Added 2026-07-03, once Phase 0 (Portability Foundation), Phase 1 (Measurement Harness), Phase 2
(GeoAuthority Prototype), and Phase 3 (ClimateAuthority Prototype) all proof-completed. This is the
first slice where a flag flip changes what a generated world actually looks like — everything before
it was provably inert (flag-off byte-identical, flag-on computed-and-discarded). Read the stakes
paragraph in the working card before starting; this is not a "green checkmark = done" phase.

**Status (2026-07-04): implemented, flag-off byte-identical, NOT ready for a live pass.** The stakes
paragraph's caution was warranted — a real finding surfaced before a live session was reached: with the
consumer flag on, live land fraction collapses to ~13% (vs GeoAuthority's own calibrated ~39%) because
the existing `base.is(BiomeTags.IS_OCEAN) || oceanAuthority` union in `pick()` compounds two now-largely-
independent "is this ocean" fields instead of the old, highly-overlapping ODF-vs-terrain pair. Full
mechanism, diagnostic evidence, and three options for how to proceed in
`docs/binder/biome-consumer-slice-20260704.md`. Needs Peetsa's decision before the next thread proceeds.

```
Kick off Latitude 2.0 overhaul work: the Biome Consumer slice only (wiring GeoAuthority + ClimateAuthority
into actual biome selection). Stop before Phase 4 (Terrain Integration Spike) — that is a separate,
higher-risk slice (documented prior ocean-sink seam failure) with its own working card.

MODEL: Sonnet, medium reasoning effort by default. Escalate to Opus (or delegate a bounded sub-question
to an Opus sub-agent) ONLY for a specific integration-design fork you can't resolve mechanically — e.g.
"does GeoAuthority-driven ocean placement replace OceanDistanceField outright, or run alongside it."
This is consumer/integration work on an already-locked design, not from-scratch algorithm design, so it
does not default to Opus the way Phase 2/3 did. See docs/binder/model-effort-strategy-20260702.md.

STAKES (read this before touching anything):
Phases 0-3 were deliberately inert: every proof was mechanical (SHA-256 diff, unit-test assertions), and
nothing a player has ever seen could change, because both v2 flags default off and every consumer so far
discards its computed summary. This slice is the first one where flipping a flag changes generated
terrain/biomes for real. That flips the proof model: a green checkmark here is necessary but NOT
sufficient. The gate is Peetsa's live eyeball on a real generated world, not just automated diffs.
Two disclosed tuning risks from Phase 2/3 could become visible problems here instead of diagnostic
curiosities: the supercontinent tail (docs/binder/phase2-geoauthority-20260703.md residual risk #1,
tune via -Dlatitude.geoV2.lplateRatio) and the aggressive alpine cooling (docs/binder/phase3-
climateauthority-20260703.md residual risk #1, tune via -Dlatitude.climateV2.kAlt). Have both knobs in
hand when doing the live pass. Slow down, use small worlds first, and stop and ask rather than pushing
through if something looks wrong that you can't explain.

READ FIRST (in order):
1. docs/LATITUDE_2_0_OVERHAUL.md (front door — read the whole thing, especially Portability Spine,
   Architecture, and this working card)
2. docs/design/geoauthority-design-20260703.md (GeoAuthority field contract + residual risks)
3. docs/design/climateauthority-design-20260703.md (ClimateAuthority field contract + residual risks)
4. docs/binder/phase2-geoauthority-20260703.md and docs/binder/phase3-climateauthority-20260703.md
   (what was measured, what was deliberately deferred to this slice)
5. docs/porting/PORTABILITY_ARCHITECTURE.md (current scaffolded-vs-implemented status)
6. Vanilla-first constraint (memory: vanilla-first-overhaul-constraint) — this slice is where it becomes
   load-bearing instead of automatic. Phases 0-3 satisfied it for free (pure math, no biome lookups);
   this slice is exactly the place it could quietly fail (a climateClass resolving to a family whose
   vanilla biome doesn't exist in some registry state, or silently depending on a pack tag).
7. src/main/java/com/example/globe/world/LatitudeBiomes.java's existing pick() cascade (10,000+ lines of
   tuned province/band/tag logic) — understand what this slice needs to coexist with, not fight or
   silently duplicate.

WORKING CARD — fill this in before touching anything:
- Objective: decide and implement the integration boundary where GeoAuthority's isOceanIntent/
  continentId/land01 and ClimateAuthority's climateClass/seasonalityClass actually select a vanilla
  biome family for a column, behind BOTH latitude.geoV2.enabled and latitude.climateV2.enabled staying
  the on/off gate (or a new dedicated consumer flag if the integration-design fork above resolves that
  way — state which and why). Vanilla-first: climateClass -> vanilla family must resolve correctly with
  zero datapacks; custom-pack biomes are optional enrichment within that family, never required.
- Root/profile: confirm current root, branch, HEAD, and Modrinth profile truth before any edit (repo
  preflight per LESSONS L3). Expected: port/canonical-26.2-pivot, HEAD 8de7b832 or later.
- Allowed work: a new biome-selection path (or a clearly-scoped modification to the existing pick()
  cascade) that consumes GeoSummary/ClimateSummary to choose a biome family, gated behind the agreed
  flag(s); vanilla-biome resolution logic; the mapping from ClimateClass's vanillaFamily() to concrete
  Holder<Biome> lookups; whatever adapter-layer plumbing (BiomeRegistryAdapter etc., already scaffolded
  in Phase 0 but unwired) this needs.
- Forbidden lanes: no Phase 4 terrain work (density functions, height integration); no changing
  flag-off behavior (Classic/current Longitude with both flags off must stay byte-identical — this is
  still a Hard Stop); no silently rewriting the existing province/band cascade wholesale — integrate
  alongside it first, replace pieces only with a clear before/after rationale; no shipping this with the
  flags defaulted on.
- Proof gate (both required, neither alone is sufficient):
  1. Mechanical: compileJava green; unit tests green; flag-off byte-identical headless exact-ID atlas
     proof (same discipline as Phases 0-3); a VANILLA-ONLY headless atlas run (zero datapacks) with the
     flag(s) on, proving every reachable climateClass/geography combination resolves to a real vanilla
     biome (no fallback-to-base-biome silent failures).
  2. Live: a real generated world, small size first, with Peetsa looking at it — does it read as a
     believable world, not just a technically-correct one. Do not consider this slice done from
     automated proof alone.
- Stop condition (in addition to the plan's own Hard Stops):
  - flag-off output differs for Classic/current Longitude at all (unchanged Hard Stop)
  - a climateClass or geography combination has no reachable vanilla biome under a vanilla-only run
  - the new consumer path silently depends on a pack tag/biome being present
  - the existing tuned province/band behavior visibly regresses (e.g. a previously-fixed overrep/leak
    issue reappears) without an explicit, reasoned decision to accept that tradeoff
  - anything looks wrong live that can't be explained by a known, disclosed tuning knob
  If any of these trigger, stop and write up what's blocking rather than pushing through.

DELIVERABLE: a dated binder note recording the integration-boundary decision, what was wired, the
vanilla-only + flag-off proof results, and (once Peetsa has done a live pass) the live-eyeball findings
and any tuning-knob adjustments made. Local commits only — ask before tagging or pushing. End by stating
plainly whether this slice is ready for a live session with Peetsa at the keyboard, or whether something
needs his input first.
```

## Linked Docs

- `docs/binder/model-effort-strategy-20260702.md` — which model/reasoning-effort per phase, and what a future
  thread can/can't self-adjust automatically.
- `docs/binder/longitude-earthlike-world-overhaul-20260702.md`
- `docs/binder/current-state-handoff-20260701.md`
- `docs/binder/test1-live-findings-20260701.md`
- `docs/binder/atlas-worldshape-longitude-20260701.md`
- `docs/26.1.2-canonical-roadmap.md`
- `docs/design/revamped-world-shape.md`
- `docs/design/horizontal-wrapping-feasibility-20260623.md`
- `docs/design/ocean-seam-wrap-plan-20260623.md`
- `docs/porting/PORTING.md`
- `docs/porting/VERSION_MATRIX.md`
- `docs/porting/PORTABILITY_ARCHITECTURE.md`
- `docs/binder/phase0-portability-foundation-20260703.md`
- `docs/binder/phase1-measurement-harness-20260703.md`
- `docs/design/atlas-geography-overlay-plan-20260703.md`
- `docs/design/geoauthority-design-20260703.md`
- `docs/binder/phase2-geoauthority-20260703.md`
- `docs/design/climateauthority-design-20260703.md`
- `docs/binder/phase3-climateauthority-20260703.md`
