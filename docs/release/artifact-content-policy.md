# Latitude Release Artifact Content Policy

`status: active` · `established: 2026-08-04` · `authority: Maintainer (owner directive)`

## Scope

This policy governs what may and may not be packaged inside a **public release artifact** of Latitude,
for every version and every port line, from 2026-08-04 forward. It supersedes the narrower rule recorded
in [`checklist.md`](./checklist.md) (the 1.4 line's "release jars exclude `com.example.globe.dev.*`,
so the shipping proof path is external tooling, not in-jar `/latdev`").

TEST artifacts are not release artifacts and are not governed here; they may carry the full development
surface under their existing artifact-identity gating.

## The rule

A public release artifact **ships** operator-usable Latitude commands, and **never** ships recording
or auto-harness work. Background work is excluded except for the single, explicitly bounded
`/latitude retrofit` maintenance operation defined below.

The exclusion exists for one reason: recording and sentinel work is the class of payload that can prevent
a public release from being greenlit. Keeping it out of the artifact is what keeps the greenlight
uncontestable. The exclusion is behavioral, not cosmetic — "it lives in a dev-named package" is not the
test, and neither is "it is only used by developers."

### Excluded class 1 — Recording

Anything that persists observations of a play session. The test: **does it write anything to disk, or
accumulate a record of technical changes?**

Includes file writes of any kind (text, CSV, PNG, screenshots, clipboard capture), session/trace
accumulators, and export tooling. A command that produces the same information as transient chat output
is not recording; the moment it also writes a file, it is.

### Excluded class 2 — Sentinel

Anything that watches, waits, or acts on its own after the player's command returns. The test: **does it
install a tick listener, background job, watchdog, or budgeted worker that continues to act?**

Includes background chunk jobs, pregeneration and regeneration workers with tick budgets, audit
coordinators, and any pause/resume/status surface that exists to manage such a worker.

### Excluded class 3 — Auto-harness

Anything that arms itself without an explicit player command. The test: **can it start without a player
typing a command in the current session?**

Includes auto-create/auto-close probes, client-side audit harnesses and bridges, keybind-triggered
capture, and `-D` system-property-driven headless runners.

### Permitted — synchronous operator queries

A command may ship when **all** of the following hold:

1. It runs synchronously and returns within the command call — no listener, job, or continuation.
2. Its entire output is chat text. It creates no files and no directories.
3. It requires operator permission (`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`).
4. It cannot arm itself; it only runs when a player types it.

### Permitted exception — operator-confirmed decoration retrofit

`/latitude retrofit` is the only background worker permitted in a public artifact. It repairs
already-generated Latitude chunks whose provider-biome decoration predates the fixed decoration
index. The exception is valid only while all of these constraints remain mechanically enforced:

1. The root command requires operator permission and refuses non-Latitude worlds.
2. Activation requires `enable` followed by `confirm` within 60 seconds; it never auto-arms.
3. The pending queue and deduplication set are capped at 2,048 chunks.
4. Work is capped at two chunks per server tick.
5. A chunk rejected at capacity is not marked handled and may retry on a later load.
6. Overflow warnings are limited to one per 60 seconds and per-chunk success is DEBUG-only.
7. `status` reports pending capacity, processed/scanned, deferred, retrofitted, and placed work.
8. `disable` and server shutdown clear all transient queue, confirmation, counter, and cache state.

Any second worker, hidden activation route, wider queue, faster tick budget, or weaker cleanup is a
policy failure rather than an implied extension of this exception.

## Current classification

| Class | Members | In release artifact |
| --- | --- | --- |
| Permitted operator queries | `help`, `here`, `explainHere` (chat-only variant), `probe`, `tpLat`, `tpBand`, `flyspeed` | **Yes** |
| Permitted bounded maintenance | `retrofit` (`status`, `enable`, `confirm`, `disable`) | **Yes, under the exact exception above** |
| Recording | `DevTestSession` (`case`/`start`/`mark`/`capture`/`finish`), `DevPresentationTrace` (`presentationTrace`), `BiomePreviewExporter` (`biomePng`, `biomePngY`), `DevCaptureKeybind` + `ClipboardImageWriter`, `BiomePreviewHeadlessRunner`, `LatitudeDevCommand.writeExplainLog` | No |
| Sentinel | `ChunkPregenerator` jobs (`transect`, `transectDeg`, `slicePoleNS`, `pause`, `resume`, `stop`, `status`, `budgetMs`, `budgetAuto`), `ChunkRegenerator` (`regen`, `regenChunk`), `SeamAuditCoordinator` (`seamAudit`), `audit/AutonomousSeamAuditJob` | No |
| Auto-harness | `AutoCreateWorldProbe`, `client/SeamAuditClientBridge`, `client/audit/SeamAuditHarness` | No |

`explainHere` ships **only** with its file-writing path removed. The development variant writes
`run/latdev/explain/<timestamp>_x<X>_z<Z>.txt` and `latest.txt`; the shipped variant returns the same
content as chat output and touches no filesystem path.

### Root verb

The shipping surface is rooted at **`/latitude`**. `/latdev` remains development- and TEST-only.
Separate roots are deliberate: the two trees never have to merge at runtime, and a public artifact
does not advertise a development surface.

### Rulings on the boundary (owner, 2026-08-04)

- **Chunk generation is game state, not a record.** `tpLat` and `tpBand` force-generate the
  destination chunk, which persists region files. That is the same thing walking there would do, so
  it does not count as recording under excluded-class 1. Recorded here so the boundary is explicit
  rather than re-litigated each release.
- **`flyspeed` persistence is game state.** It writes the player's flying speed through Minecraft's
  own save path; it stores no observation.

## Enforcement points

These are mechanical and must be green for every release. They are deliberately redundant: no single
edit can quietly widen what ships.

1. **Package boundary.** Shipping commands live in `com.example.globe.tools`. Recording, sentinel, and
   auto-harness code lives in `com.example.globe.dev` and never moves out of it. A command that needs to
   ship gets *extracted*, not relocated wholesale.
2. **Packaging exclusion.** `build.gradle`'s `releaseArtifactExcludeSpec` keeps `com/example/globe/dev/**`
   (and `com/example/globe/debug/**`, `tools/**`, `scripts/**`, native/binary payload) out of `jar` and
   `remapJar`. This exclusion is never relaxed to ship a command; the command is extracted instead.
3. **Verifier assertions.** `tools/verify_phase6_dev_tooling.py` asserts that a public jar contains zero
   `com/example/globe/dev/**` entries, that it *does* contain the shipping `com/example/globe/tools/**`
   surface, and that shipping command sources contain no file-write, tick-listener, or harness references.
   Its `verify_tools_sources()` also pins the shipped subcommand and argument sets **exactly**, so an
   unknown new subcommand fails the gate rather than widening the surface silently. Its retrofit gate
   separately pins the sole exception's command, queue, tick budget, retry, logging, status, and cleanup
   invariants.
6. **Build-enforced, not script-enforced.** `latitudeShippingToolsPolicyTest` and
   `latitudeArtifactPolicySourceScan` run under `check`, and CI runs the same `build check` gate. A
   verifier that nothing invokes is not an enforcement point — the boundary stays wired into both the
   local build and CI.
7. **Registration containment.** Command registration exists only in `com.example.globe.tools` and
   `com.example.globe.dev`; no other package may reference Brigadier. A new command therefore cannot
   reach a release artifact without passing through the gated surface.
4. **Artifact identity.** A release artifact carries no `Latitude-Artifact-Role: TEST`, no
   `latitude:test_artifact` custom value, and `Build-Dirty: false`. `LatitudeDevRuntime`'s identity gate
   keeps TEST-only tooling inert in anything that is not a valid TEST artifact.
5. **Development-environment gates.** Client-side harnesses stay behind
   `FabricLoader.getInstance().isDevelopmentEnvironment()` in `GlobeModClient`, so they remain inert even
   if a class ever reaches a packaged artifact.

## Per-release verification

Run against the built candidate jar, before staging and again before publication:

```bash
unzip -l <candidate>.jar | grep -c "com/example/globe/dev/"    # must be 0
unzip -l <candidate>.jar | grep -c "com/example/globe/tools/"  # must be > 0
unzip -p <candidate>.jar META-INF/MANIFEST.MF                  # Build-Dirty: false, no Latitude-Artifact-Role
python3 tools/verify_phase6_dev_tooling.py --public-jar <candidate>.jar
```

Then, in a live session on the staged artifact: confirm the permitted commands exist and require operator
permission, confirm no excluded subcommand resolves, confirm no `run/latdev/` directory is created by any
shipped command, and confirm no Latitude capture keybind appears in the vanilla Controls list.

## Amendment protocol

This policy changes only by owner directive. Release evidence and amendment chronology live in the
private Latitude notes for the applicable source line; this public document records only the current,
stable artifact contract. Git history preserves prior public versions of that contract.
