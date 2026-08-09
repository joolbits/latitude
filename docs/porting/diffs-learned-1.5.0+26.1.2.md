# Diffs learned from thread 1 (`1.5.0+26.1.2`) — carried forward into thread 2 (1.21.11)

`date: 2026-08-06` · `source: Latitude-port-1.5-26.1.2/docs/porting/diffs-learned-1.5.0+26.1.2.md`
· `carried by: thread 2 (port/1.5.0-1.21.11)`

## Why this file exists here

The campaign's Phase-9 closeout says each thread appends its diffs-learned into the next thread's
kickoff. `kickoff-1.5-1.21.11.md` has an empty `## Diffs learned from thread 1 (26.1.2)` section
waiting for exactly this — but that file lives in the **protected, read-only** `Latitude-1.5-26.2`
checkout, which port threads must never modify. Thread 1 hit the same wall and wrote its copy locally
for the same reason.

So this is the carried copy, annotated with **thread-2 status** for each item: whether it applies to
1.21.11, and what has already been checked. A verbatim copy would restate what thread 1 already wrote;
the value added here is the verification status.

**Still owed to Maintainer:** the paste into the protected kickoff. Thread 2 cannot do it.

---

## Part 1 — Real bugs, not port artifacts. Check on every remaining target.

### 1. Structure siting and `/locate structure` judge different biomes

`ServerLevel` builds its `StructureCheck` from `ChunkGenerator.getBiomeSource()` — which
`ChunkGeneratorBiomeSourceMixin` overrides to Latitude's repainted wrapper — but
`ChunkGenerator.tryGenerateStructure` reads the raw `biomeSource` **field** directly (`getfield`,
confirmed by `javap -c`, not by reading the getter). Structure *prediction* therefore judged the
repainted biome while *generation* judged the raw one.

One split, two opposite-looking symptoms: `/locate` promising structures that never generate, and
structures generating in a biome Latitude then repaints away (the "desert temple in snow" report).

**Fix:** substitute the Latitude-wrapped biome source into the existing `Structure.generate`
`@WrapOperation` *before* `original.call(...)`, so siting judges the biome the player will stand in.

> **Thread-2 status: NOT PRESENT — must be ported.** Verified on this branch:
> `src/main/java/com/example/globe/mixin/ExtremePolarVillageStartGuardMixin.java` has the
> `@WrapOperation` (line 43) and takes a `BiomeSource biomeSource` parameter (line 54), but passes
> that **raw** source straight through to `original.call(...)` (line 139). This is the pre-fix shape.
> Harvest `5ad06b0b` from `port/1.5.0-26.1.2` in Slice D.

### 2. `/locate structure` is architecturally blind to Latitude

Same class of gap already solved for `/locate biome` (`LocateCommandMixin` →
`LatitudeBiomeLocateService`) and never extended to structures. Vanilla's search is a hardcoded
100-ring sweep with no world-border awareness, tested against the raw biome.

`LatitudeStructureLocateService` (new in thread 1) mirrors vanilla's own
`RandomSpreadStructurePlacement.getPotentialStructureChunk` ring algorithm, bounded to
`WorldBorder.isWithinBounds`, testing the repainted biome only. Cheap enough to run synchronously —
no tick-slicing, unlike the biome locate service's exhaustive block scan.

> **Thread-2 status: ABSENT — new file to harvest.** Confirmed no
> `LatitudeStructureLocateService.java` anywhere under `src/`. Harvest `d521ff19` in Slice D.

### 3. Placement-time structure guards are a proven anti-pattern — do not reintroduce one

A mixin cancelling `StructureStart.placeInChunk` runs *after* `tryGenerateStructure` has already
called `StructureManager.setStartForStructure`. The cancelled structure is still stored, serialized,
and reports as existing after a world reload — with no blocks. A ghost.

26.2 tried exactly this (`StructureBiomeMatchGuardMixin`, `78a09e2d`) and consolidated it away into a
single generation-time guard. The reasoning survives in `VillageLatitudePolicyTest`'s
`locatableStartsAfterReload` assertions even though the removal commit itself was an unexplained
squash.

If a target's `tools/verify_structure_climate_guard.py` still demands the placement-time mixin, the
**verifier is checking for the rejected design** — retire it rather than satisfy it. Recommendation
only; the maintainer's call.

> **Thread-2 status: OPEN FLAG.** `tools/verify_structure_climate_guard.py` exists on this branch.
> Raise it at Slice E gate time rather than pre-emptively.

### 4. Zone spawn fractions must derive from the canonical band

`LatitudeMath.spawnFracForZoneKey` had `SUBTROPICAL -> 0.40` hardcoded — 36°, one degree past its own
23.5°–35° band — so every Subtropical spawn landed in Temperate. 100% reproducible, not an edge case.
26.2's `ddb531b4` fixed it with `centerFrac(band) = (band.lowDeg() + band.highDeg()) * 0.5 / 90.0`.

**Audit any zone/band fraction table on sight.** This bug class hides easily: it compiles, and the
value looks reasonable.

> **Thread-2 status: to port.** Harvest `c1540247` in Slice D and re-audit the fraction table.

---

## Part 2 — Tooling and technique

- **A headless dedicated server needs no Fabric Installer.** Loom's dev classpath — the `-classpath`
  line in `build/loom-cache/argFiles/runClient`, generated by briefly running `./gradlew runClient`
  and killing it once the file appears — already contains the merged client+server jar and every
  dependency. Launch `net.fabricmc.loader.impl.launch.knot.KnotServer` against it with
  `-Dfabric.gameDir=<throwaway>`. Faster than installing a server jar and fully isolated from any
  shared dev-client window. *(Thread 2 uses this for the GitHub #8 dedicated-server smoke in Slice E.)*
- **Region-file heightmaps use padded long packing** in 26.x — `floor(64/bits)` values per long,
  remainder wasted (37 longs for 256 entries at 9 bits is `ceil(256/7)`, not the no-padding
  `ceil(256*9/64)=36`). Block-state packing went no-padding at 1.16; heightmaps evidently did not.
  **Re-verify per target** — thread 2 has not yet confirmed this holds on 1.21.11.
- **A headless server with no players connected does not tick entity gravity**, even in forceloaded
  chunks. Summoned probes sit at their spawn Y indefinitely. Chunk *generation* (including structure
  placement) still runs fine, being part of generation rather than simulation. Read the heightmap NBT
  directly instead of building entity-physics probes.
- **Build comparison baselines in a throwaway `git worktree`** off the tag
  (`git worktree add -b <temp> <path> <tag>`, remove both after). A clean way to diff against a
  shipped build without touching a protected checkout.
- **The dev/tools split needs two checks.** `tools/verify_phase6_dev_tooling.py` with no args proves
  the source-level convention; only `--public-jar build/libs/<jar>` proves nothing under `dev/`
  leaked into a real packaged build. Run both before release, not just the structural one.

---

## Part 3 — Process

- **A shared dev-client window may be someone's live session.** Thread 1 sent automated input into a
  window Maintainer was actively driving. Confirm before automating into a shared surface — the same
  discipline as confirming a save directory isn't someone's live evidence before `rm -rf` (thread 1
  destroyed a bug-repro world that way earlier in the same session).
- **When a live report contradicts a fix that should have worked, measure — don't re-theorize.** Add
  diagnostic counters, reproduce headlessly, and let the numbers name the failing gate. Thread 1's
  structure root cause surfaced only after instrumenting rejection reasons, which showed 354 of 361
  candidates dying at a gate nobody suspected.
- **Concurrent agent sessions run on this machine.** Check for foreign `java`/`gradle` processes
  before timing work or live testing, and never run a full gate alongside a game client — a
  predecessor hit an exit-137 OOM doing exactly that.

---

## Part 4 — Thread-1 outcomes that are not yet done

- **`1.5.0+26.1.2` is on GitHub** (tag, branch `port/1.5.0-26.1.2`, pre-release with both jars,
  digests verified). GitHub issue #8 answered with the dedicated-server smoke result.
- **Modrinth / CurseForge upload of `1.5.0+26.1.2` is still outstanding and Maintainer-owned.** No publish
  tooling or credentials exist in this repo. Do not treat 26.1.2 as live on the platforms.

---

## Part 5 — What thread 2 adds for thread 3

Recorded in full in
[`<home>/CascadeProjects/Latitude-notes/port-1.5-1.21.11/binder-recovered-20260807/latitude-1-5-port-1p21p11-mojmap-spike-20260806.md`](../../../Latitude-notes/port-1.5-1.21.11/binder-recovered-20260807/latitude-1-5-port-1p21p11-mojmap-spike-20260806.md);
the headline items:

1. **Run the mappings spike statically.** Stream Mojang's ProGuard mapping file and check every
   `import net.minecraft.*` against the real class set; read the 26.2 side with `javap` against the
   unobfuscated jar. Gives an exact RED count in minutes with no build and no disk write.
2. **The render-extraction reversal is a verified rename table, not a design problem** — reproduced in
   that doc, valid for any target at 1.21.6 or newer.
3. **Nine kickoff-census corrections**, including that `SavedDataType` already exists on 1.21.11, so
   `LatitudeWorldState` needs no persistence rewrite.
4. **The 1.21 line is not one API surface.** The fog package moved at **1.21.6**, not at Pale Garden.
   Measured clusters: 1.21.1 (20 REDs) · 1.21.4/5 (16/15) · 1.21.6–1.21.8 (13, identical) ·
   1.21.9–1.21.10 (11, identical) · 1.21.11 (7).
5. **Fence width is a runtime question, not a mappings question.** Shipped jars are intermediary-named,
   so mojmap-vs-Yarn is irrelevant to how wide a fence can be. Per the maintainer's 2026-08-06 decision, every
   port now boots its built jar on adjacent-version profiles and ships the widest fence that passes the
   mixin-apply audit, recording the tested bounds.
6. **Thread 1's four fixes are not on the `v1.5.0+26.2` tag**, so every remaining thread inherits the
   Slice-D harvest obligation. "Same version number = same features everywhere" does not hold for free.
