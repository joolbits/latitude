# Latitude 1.5 Mini-Launch Performance Audit

**Audit date:** 2026-07-17  
**Branch:** `codex/1.5-mini-launch-26.1x`  
**Original audit anchor:** `858b815002ef82b20d55deadb0ac5ad970a9c823`  
**Core findings revalidated at:** `8fe97d05`  
**Mode:** Read-only source, history, and existing-evidence audit  
**Overall verdict:** **RED for performance readiness**

The audit found three high-confidence performance regressions. Clean runtime profiling is still required to measure their exact impact and to decide whether the lower-priority candidates need changes.

## Executive Triage

| Priority | Finding | Confidence | Why it matters |
|---|---|---:|---|
| P1 | Biome selection reruns for nearly every vertical biome cell | Confirmed | Up to approximately 1,536 expensive `LatitudeBiomes.pick()` calls per chunk instead of roughly one per column |
| P1 | Fixed-height base biome is resampled for every vertical cell | Confirmed | Roughly 96 identical source-biome samples per column, especially costly with Terralith and custom providers |
| P1 conditional | Analog compass draws its circle pixel by pixel every frame | Confirmed | Thousands of render commands per frame; severity rises sharply with compass size |
| P2 | Repeated ID parsing, system-property reads, list creation, tag scans, and sorting | Confirmed overhead | Multiplies the P1 worldgen cost; likely secondary after column caching |
| P2 | Ocean-distance search serializes workers during cache misses | Plausible | A synchronized breadth-first search can stall parallel chunk workers |
| P3 | Smaller feature-decoration, inventory-scan, logging, and global-cache costs | Confirmed or plausible | Worth cleaning only after the dominant paths are fixed and profiled |

## P1 — Biome Picker Repeated Through the Whole Vertical Column

The biome-population loop visits 4 × 4 horizontal cells through the full vertical biome height—around 96 quart cells in a normal 384-block-tall overworld.

Relevant source:

- `src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java:393-421`

That produces approximately:

```text
4 × 4 × 96 = 1,536 resolver calls per chunk
```

Each non-cave cell invokes the full Latitude biome picker:

- `ChunkGeneratorPopulateBiomesMixin.java:255-323`
- `LatitudeBiomes.java:2585-3227`

The current picker computes `biomeY`, but in this committed implementation it is only passed to an assertion. The selected surface biome is effectively column-stable, while the expensive selection machinery is repeated vertically.

### Independent history evidence

Later commit:

```text
7d5918ef perf(worldgen): memoize biome pick per column — the real chunk-gen lag fix
```

Its recorded mechanism matches the current 1.5 source exactly:

- the resolver runs about 1,536 times per chunk;
- the full picker repeats through the column;
- per-column memoization reduced picker calls by approximately 30–90×.

This commit is not in the 1.5 branch and sits on later 2.0-bearing history. Port the proven behavior only; do not cherry-pick the mixed commit wholesale.

## P1 — Fixed Base Biome Resampled for Every Vertical Cell

The resolver samples two source biomes per vertical cell:

```java
Holder<Biome> current = sourceSupplier.getNoiseBiome(x, y, z, sampler);
Holder<Biome> base = sourceSupplier.getNoiseBiome(
        x, LatitudeBiomes.SURFACE_CLASSIFY_Y >> 2, z, sampler);
```

Relevant source:

- `ChunkGeneratorPopulateBiomesMixin.java:262-263`

`current` legitimately varies by Y. `base` always uses the same fixed classify Y and is identical for the entire X/Z column, yet it is resampled roughly 96 times.

Later commit:

```text
a54297cb perf(worldgen): cache the column-invariant base biome sample
```

That removed about 95 of 96 redundant base samples per column. This is particularly important when the underlying provider is expensive, such as Terralith.

## P1 Conditional — Analog Compass Pixel-By-Pixel Rendering

Analog compass mode builds its circle with one `fill()` call per pixel, every frame:

- `src/main/java/com/example/globe/client/CompassHud.java:338-353`

At ordinary sizes this creates thousands of render commands. At the allowed maximum diameter of 128 pixels, the cost grows sharply.

Later commit:

```text
6b11c849 feat(client): U-D — five compass looks, span-batched dial, change-driven HUD strings
```

That commit replaced the pixel loop with horizontal span batching. It also contains 2.0 HUD work, so only the renderer optimization should be behavior-ported.

Digital compass mode does not have this circle-rendering problem.

## P2 — Repeated Parsing and Allocation Inside the Picker

### Identifier parsing

`isBiomeId()` reparses constant identifier strings repeatedly:

- `LatitudeBiomes.java:6690-6697`
- a second equivalent path is present around `LatitudeBiomes.java:6605`

There are more than 200 static `isBiomeId(` call sites in `LatitudeBiomes.java`.

Later commit:

```text
1bc2f8a4 perf(worldgen): memoize Identifier.parse in isBiomeId hot path
```

That was a safe improvement, but its own commit record correctly says it was not the primary bottleneck.

### Launch-time property reads

The picker repeatedly reads launch-time JVM properties such as:

- `latitude.disableRadiusOverride`
- `latitude.skipPreviewHeightForWorldgen`
- `latitude.skipPreviewHeightForBiomePng`

Relevant source:

- `LatitudeBiomes.java:1211-1228`
- `LatitudeBiomes.java:2585-2603`
- `LatitudeBiomes.java:3234-3252`

Later commit `d14bbf5b` cached these immutable launch flags.

### Tag-pool reconstruction

Tag selection repeatedly:

1. creates an `ArrayList`;
2. scans the biome collection;
3. converts identifiers to strings;
4. sorts the result.

Relevant source:

- `LatitudeBiomes.java:5361-5372`
- `LatitudeBiomes.java:5430-5464`
- `LatitudeBiomes.java:5986-6025`

This is real allocation overhead, but it should be profiled after per-column picker caching. Caching these pools incorrectly could cause stale datapack or provider state.

## P2 — Ocean-Distance Locking and Search Cost

`OceanDistanceField.oceanDistanceBlocks()` samples four surrounding coarse cells. A cache miss enters a synchronized breadth-first search:

- `src/main/java/com/example/globe/world/OceanDistanceField.java:34-95`

While holding the object lock, it allocates:

- an `ArrayDeque`;
- a visited `Long2IntOpenHashMap`;
- coordinate arrays for queued cells;
- neighbor arrays for every visited node.

Consequences:

- only one chunk worker can execute a cache-miss search at a time;
- adjacent cold cells repeat overlapping searches;
- even cache hits pass through the synchronized method;
- distance and ocean-flag caches grow for the lifetime of the world.

This is a strong profiling target, particularly while generating new inland chunks. It is not yet proven to dominate after the P1 picker fixes.

## Lower-Priority Findings

### Custom-biome feature-decoration allocation

The `retainAll` redirect creates lists and scans biome sets around decoration:

- `src/main/java/com/example/globe/mixin/ChunkGeneratorGenerateFeaturesBiomeSetMixin.java:190-213`

With no custom provider, this is a small allocation tax. With supported custom providers, the scans grow.

### Compass inventory scan

Compass visibility scans the player's inventory and recursively inspects bundle contents once per game tick:

- `CompassHud.java:596-630`

This is lower priority than analog rendering.

### EW haze log pressure

The EW haze writes an info log every 40 ticks while near the EW border:

- `src/main/java/com/example/globe/client/EwSandstormOverlayHud.java:36-38`

This is unnecessary disk and log noise in that region.

### Global block-state interception

`AlpineSurfaceMixin` intercepts every `ProtoChunk.setBlockState` invocation:

- `src/main/java/com/example/globe/mixin/AlpineSurfaceMixin.java:30-61`

Its early exits are cheap, so measure it before changing it.

### Ocean-distance cache lifetime

`distanceCache` and `oceanFlagCache` have no size bound:

- `OceanDistanceField.java:24-26`

The grid is coarse, making this a long-session exploration risk rather than an immediate emergency.

## Candidates Ruled Down

### Source candidate expansion

`expandSourceCandidatePool()` currently returns the original collection unchanged:

- `LatitudeBiomes.java:548-550`

It is not rebuilding or copying the pool per call in this branch.

### Old nine-height-probe path

The older nine-point `previewTerrain()` probe is disabled by default for live worldgen:

- `LatitudeBiomes.java:1211-1228`

The current repeated-picker problem remains, but the old nine-probe explanation is not the default live path.

### Historical “Can't keep up” logs

Older live evidence recorded two-to-three-second server delays, but those runs also had:

- high render and simulation distances;
- substantial system memory pressure;
- another Minecraft client running;
- samples inside vanilla feature decoration as well as Latitude code.

Those logs demonstrate an unresolved performance risk, not a clean attribution. The current source and later matching performance commits provide stronger mechanism evidence.

### Polar server effects

Polar effects are evaluated only every ten ticks per player. This is not a leading performance suspect.

### Loading readiness checks

Loading-screen readiness checks are temporary and bounded. They are not a steady-state performance cost.

## Recommended Implementation Order

### Slice A — Dominant Server Worldgen Cost

1. Behavior-port the per-column biome-pick cache from `7d5918ef`.
2. Cache the fixed-height base source sample as in `a54297cb`.
3. Preserve cave-biome behavior and deep-cell correctness.
4. Run exact-ID, climate-law, provider, save/reload, and atlas-parity regression gates.
5. Commit this as one independently proven server-performance slice.

### Slice B — Safe Hot-Path Micro-Caches

1. Memoize constant biome identifiers as in `1bc2f8a4`.
2. Cache immutable launch properties as in `d14bbf5b`.
3. Confirm no behavior or configuration reload semantics change.

This may be combined with Slice A only if its diff and proof remain easy to audit.

### Slice C — Analog HUD Renderer

1. Replace per-pixel disc drawing with horizontal spans.
2. Do not import the 2.0 HUD overhaul.
3. Verify every analog size, theme, transparency level, preview, and live HUD placement.
4. Commit as a separate client-performance slice.

### Measure Before Touching

Do not redesign these without profiler evidence:

- ocean-distance concurrency and cache policy;
- tag-pool caching;
- alpine block-state interception;
- custom-provider feature-decoration filtering.

## Runtime Proof Plan

Use one clean client and no other Minecraft process.

Keep the comparison controlled:

- identical disposable world seed;
- identical world size;
- render distance 16;
- simulation distance 8;
- same provider set;
- same flight path through virgin chunks;
- 60–120 seconds per run.

Capture:

- Spark or JFR CPU profile;
- allocation profile;
- monitor/lock contention;
- p95 and p99 MSPT;
- generated chunks per second;
- total and maximum “Can't keep up” events;
- samples in `LatitudeBiomes.pick`;
- samples in source `getNoiseBiome`;
- samples and blocked time in `OceanDistanceField.cellDistance`;
- samples in `entriesForTag`.

Run at least:

1. base provider only;
2. Terralith;
3. Biomes O' Plenty or another supported custom-biome provider;
4. combined supported-provider case.

For the HUD:

1. stand still in the same scene;
2. compare digital mode, analog size 48, and analog size 128;
3. record average and 1% low FPS or frame time;
4. repeat while moving and while HUD Studio is open.

## Audit Safety and Scope

The audit inspected committed Git objects and existing project evidence. It did not:

- edit source during the audit;
- run or control Minecraft;
- open an existing profile or world;
- stage or commit files;
- touch the Latitude 2.0 / Minecraft 26.2 Pivot worktree;
- adopt concurrent dirty work.

At save time, the branch contained concurrent uncommitted HUD work and task evidence. Those paths remain protected and are not part of this report.

