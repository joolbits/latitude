# Roadmap — `1.5.0+1.21.11` (backport campaign, thread 2 of 4)

`date: 2026-08-06` · `owner: Maintainer (she/her)` · `branch: port/1.5.0-1.21.11` · `status: active`

Evidence for every claim below lives in
[`<external-notes>/port-1.5-1.21.11/external record-recovered-20260807/latitude-1-5-port-1p21p11-mojmap-spike-20260806.md`](../../../Latitude-notes/port-1.5-1.21.11/external record-recovered-20260807/latitude-1-5-port-1p21p11-mojmap-spike-20260806.md).
Thread 1's carried lessons live in
[`diffs-learned-1.5.0+26.1.2.md`](diffs-learned-1.5.0+26.1.2.md).
The method is `LATITUDE_1_5_BACKPORT_CAMPAIGN.md` in the protected `Latitude-1.5-26.2` checkout.

## Objective

`1.5.0+1.21.11` beta, feature-identical to `1.5.0+26.1.2` (not merely to the `v1.5.0+26.2` tag — see
Slice D). 1.21.11 is Latitude's best-performing version ever, so this port carries the most user
weight of the four.

## Status

| Step | State |
| --- | --- |
| Preflight (campaign step 1) | **PASS** — clean, `git diff v1.5.0+26.2` empty, HEAD `2a1074ae` |
| Hour-1 mappings spike (step 2) | **PASS** — mojmap viable, 171/178 imports resolve, 7 REDs |
| Slice A — toolchain metadata | **DONE** `44d3924c` — Loom 1.14.10 (1.17.x rejects mojmap), modImplementation required |
| Slice B — compile ladder | **DONE** `e0dde52e` — 7 predicted REDs exactly + 3 Fabric-API REDs the scan could not see |
| Slice C — runtime hooks + fog redesign | **DONE** `859c9fa2`/`66a54c72`/`9e9314d9`/`b639f142` — fog split in two, 5 mixin defects, persistence rail proven |
| Slice D — harvest thread 1's fixes | **PARTIAL** `8c26f0d5` — took only the 4 named in the handoff; 5 more found later, see Slice G |
| Slice E — gates + fence adjacency test | **DONE** — atlas parity bit-identical to 26.2; fence ships pinned; guard verifier retired |
| Slice F — live lane | **IN PROGRESS** — `TEST 10` GREEN; 8 live defects fixed total (4 pre-Slice-G + remap crash + `/locate` Y=0 + Settings-tab label + `/locate` async/village-accuracy); 2 worldgen questions still open |
| Slice G — harvest the remaining 5 thread-1 fixes | **DONE** `8640c14c`/`1eb1ecf0`/`2ca3a76a`/`1f4a68e9`/`0e831d38` — harvest complete; all 9 of thread 1's post-tag fixes are in |
| Release | pending, **needs the maintainer's authorization** |

> **current implementation state:** [`<external-notes>/port-1.5-1.21.11/external record-recovered-20260807/latitude-1-5-port-1p21p11-locate-async-village-fix-20260807.md`](../../../Latitude-notes/port-1.5-1.21.11/external record-recovered-20260807/latitude-1-5-port-1p21p11-locate-async-village-fix-20260807.md)
> carries current state — `TEST 10` staged and confirmed GREEN by Maintainer. What's left before release is
> listed in full under **Slice F remainder**, below.

## Decisions of record (Maintainer, 2026-08-06)

1. **Mappings: Mojang official.** No Yarn fallback.
2. **Escalation retargeted** from the render-extraction reversal *design* (now settled by evidence)
   to the `FogRendererEwMixin` redesign plus an adversarial audit of the mapping table.
3. **Thread 1's post-tag fixes come forward** in a dedicated Slice D, after the core port is green.
4. **Campaign coverage policy:** targets stay 26.1.2 · 1.21.11 · 1.21.1 · 1.20.1; every port widens
   its fence empirically rather than adding threads.

## Slices

### Slice A — toolchain metadata only *(commit before any source edit)*

| File | Change |
| --- | --- |
| `build.gradle` | Loom `1.17.13` try-as-is, fall back to proven `1.14.10`; **add** `mappings loom.officialMojangMappings()`; `options.release = 25` → `21` at **10 sites**; `sourceCompatibility`/`targetCompatibility` `VERSION_25` → `VERSION_21` |
| `gradle.properties` | `minecraft_version=1.21.11`, `loader_version=0.17.3`, `fabric_api_version`/`fabric_version=0.140.x+1.21.11`, `mod_version=1.5.0+1.21.11` |
| `gradle/wrapper/gradle-wrapper.properties` | `9.5.1` → `9.2.0` **only if** Loom demands it |
| `src/main/resources/fabric.mod.json` | `minecraft` → `">=1.21.11 <1.21.12"` (pinned; widen only on Slice-E proof); `java` `">=25"` → `">=21"`; `fabricloader ">=0.17.3"` already correct |

`globe.mixins.json` needs no change — `compatibilityLevel` is already `JAVA_21`, refmap already
`globe.refmap.json`.

### Slice B — compile ladder *(each file admitted only on a named compiler RED)*

1. Five one-line REDs: `Hud`→`Gui`, `WorldCreationGameRulesScreen`→`EditGameRulesScreen`,
   `WorldBorderRenderState` package move, `SpeleothemClusterFeature`→`DripstoneClusterFeature`,
   `SpeleothemFeature`→`PointedDripstoneFeature`.
2. `GlobeModClient.java:127` — `BlockTintSources.constant(tint)` → a `BlockColor` lambda into
   `BlockColors.register(BlockColor, Block…)`.
3. The reversal across 10 files, from the locked table: `GuiGraphicsExtractor`→`GuiGraphics`,
   `text`→`drawString`, `centeredText`→`drawCenteredString`, entry-point renames.
4. Whatever member-level REDs the compiler then names — the residue import analysis cannot see.

Any RED **not** on the table is new information: record it, do not hand-solve past it.

### Slice C — runtime hooks

- Mixin-apply proof for **every** retargeted mixin. A green build does not prove application;
  `defaultRequire: 1` stays. Highest risk: `InGameHudMixin`,
  `LevelLoadingScreenLatitudeOverlayMixin` (646 LOC, carries a second `@Mixin(Minecraft.class)` at
  line 481), `WorldSelectionListEntryMixin`.
- **`FogRendererEwMixin` redesign** — the escalated item.
- **Settings-key persistence rail** (`18f2629f` bug class): confirm `globe_radius` /
  `shouldApplyLatitudeWorldgen` survive a `level.dat` round-trip, or worlds silently generate vanilla.
  `SavedDataType` existing does *not* prove the rail works.
- Per GitHub #7: client overlay/UI mixins **fail soft** (`require = 0` + guards) where degradation is
  acceptable — a missed loading-overlay target means the vanilla loading screen, never a crash.
- Sodium tolerance.

### Slice D — harvest thread 1's fixes *(after the core port is green)*

From `port/1.5.0-26.1.2`, absent from `v1.5.0+26.2`:

| Commit | What |
| --- | --- |
| `d521ff19` | `LatitudeStructureLocateService` — border-bound, biome-true `/locate structure` |
| `5ad06b0b` | Site structures by the repainted biome (this branch has the pre-fix shape — verified) |
| `c1540247` | Spawn-zone `centerFrac` fix |
| `c7014de5`, `3b05a3fc` | Live-flight checklist + title-screen build watermark |

**Do not** reintroduce a placement-time structure guard — it produces a locatable ghost with no blocks.

### Slice E — gates

- `./gradlew clean check build -PenableInvariantScan=true latitudeInvariantScan` — the pure-core
  policy suites (11 `src/*PolicyTest/` source sets) must pass **unchanged**; that is itself a
  port-correctness signal.
- `python3 tools/verify_phase6_dev_tooling.py` **and** `--public-jar build/libs/<jar>`.
- Headless exact-ID atlas vs a seed-matched 26.2 reference export (`max-tick-time=-1`, or the watchdog
  kills it).
- **Dedicated-server smoke** (GitHub #8) via `KnotServer` on Loom's dev classpath.
- Pale Garden / Mushroom Fields spot checks.
- **Fence adjacency test:** boot the *same* jar on 1.21.9 and 1.21.10 profiles; mixin-apply audit plus
  worldgen smoke. Ship the widest fence that passes; record the tested bounds.
- **Open flag:** `tools/verify_structure_climate_guard.py` may be checking for the rejected
  placement-time design — the maintainer's call whether to retire it.

### Slice F — live lane and release *(no push, tag, or upload without explicit authorization)*

**Done:** fresh Modrinth profile `Lat 1.5 - 1.21.11 - TEST` (11 mods incl. the real provider stack);
dev client + staged `TEST N.jar` (now at `TEST 10`, confirmed GREEN) with built-vs-staged SHA parity
every time; 8 live defects found and fixed — see the resume-point doc above for the full list and
[`…test-jar-remap-crash…`](../../../Latitude-notes/port-1.5-1.21.11/external record-recovered-20260807/latitude-1-5-port-1p21p11-test-jar-remap-crash-20260807.md) for
the one that mattered most (every TEST jar through `TEST 6` was silently unable to launch in
production Fabric Loader at all).

**Still open, in rough priority order:**

1. **the maintainer's two worldgen questions from her first flythrough, both still needing *measurement* not
   code-reading** — see
   [`…slice-f-live-findings…`](../../../Latitude-notes/port-1.5-1.21.11/external record-recovered-20260807/latitude-1-5-port-1p21p11-slice-f-live-findings-20260807.md):
   arid-edge fragment-size distribution, and real surface-Y under `savanna_plateau`
   (`preserveSavannaPlateauAtSanitize` still has no height test at all).
2. **Live confirmation that `dde70c88`'s land-cohesion gate actually did something** — the atlas is
   structurally blind to it; the honest check is Maintainer re-flying the Sunflower-Plains-on-a-ridge
   location from her first screenshot.
3. **Live confirmation that a `/locate structure` village result actually has a village on the
   ground** — the headless rig that verified today's fix could only prove the mechanism, not physical
   placement.
4. Fog A/B against 26.2, at an E/W storm longitude and a polar latitude.
5. Old-line regressions that must not return: E/W warning text lagging the storm visual; spawn on a
   sea-level rock; worldgen gate not dimension-aware. Not yet explicitly re-checked on this thread.
6. **Fence adjacency test** (deferred from Slice E on the maintainer's instruction — "skip it, pin the fence"):
   boot the same jar on 1.21.9/1.21.10 profiles, mixin-apply audit + worldgen smoke. Currently shipping
   pinned `>=1.21.11 <1.21.12`; this only matters if Maintainer wants the wider fence.
7. One adversarial sweep on the staged jar before calling live acceptance complete.
8. Then, **on the maintainer's explicit authorization**: `1.5.0+1.21.11` beta, per-version changelog, tag
   `v1.5.0+1.21.11`, GitHub #7/#8 replies.

## external record discipline

Every slice lands its evidence row in `<external-notes>/port-1.5-1.21.11/external record-recovered-20260807/evidence-registry.md` and its pointer in
`<external-notes>/port-1.5-1.21.11/external record-recovered-20260807/index.md` in the **same pass** as the work.

## Closeout

Harvest-complete note; archive `port/1.4.0-beta-1.21.11`; write
`docs/porting/diffs-learned-1.5.0+1.21.11.md` for thread 3.

## Open items owned by Maintainer

- Pasting thread 1's diffs-learned into the protected `kickoff-1.5-1.21.11.md`.
- `tools/verify_structure_climate_guard.py` retirement (Slice E).
- The 1.4-era `port/1.4.0-beta-1.21.11` worktree is still dirty (10 modified + 3 untracked, incl.
  the untracked `HANDOFF-1.21.11-state-and-province-gap-20260614.md`). Read it early for the
  province-gap analysis; **do not commit or discard without her.**
