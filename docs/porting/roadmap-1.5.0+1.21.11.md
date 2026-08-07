# Roadmap — `1.5.0+1.21.11` (backport campaign, thread 2 of 4)

`date: 2026-08-06` · `owner: Maintainer (she/her)` · `branch: port/1.5.0-1.21.11` · `status: active`

Evidence for every claim below lives in
[`<external-notes>/latitude-1-5-port-1p21p11-mojmap-spike-20260806.md`](../external record/latitude-1-5-port-1p21p11-mojmap-spike-20260806.md).
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
| Slice F — live lane | **IN PROGRESS** — 4 live defects fixed; 5 un-harvested fixes found; 2 questions open |
| Slice G — harvest the remaining 5 thread-1 fixes | **DONE** `8640c14c`/`1eb1ecf0`/`2ca3a76a`/`1f4a68e9`/`0e831d38` — harvest complete; all 9 of thread 1's post-tag fixes are in |
| Release | pending, **needs the maintainer's authorization** |

> **current implementation state:** [`<external-notes>/latitude-1-5-port-1p21p11-slice-g-closure-20260807.md`](../external record/latitude-1-5-port-1p21p11-slice-g-closure-20260807.md)
> carries current state — `TEST 6` staged, the harvest closed, and the four things still outstanding
> before release. The earlier
> [`…slice-f-live-findings…`](../external record/latitude-1-5-port-1p21p11-slice-f-live-findings-20260807.md)
> remains the record of the four live defects and both open worldgen questions.

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

- Fresh Modrinth profile `Lat 1.5 - 1.21.11 - TEST`. **Modrinth App only, never the Mojang launcher.**
  Dev client for routine testing; staged `TEST N.jar` with built-vs-staged SHA parity for acceptance.
- Provider jars (BoP/Terralith for 1.21.11) must be downloaded — not on this machine.
- One adversarial sweep on the staged jar before the maintainer's live acceptance.
- Regressions that must not return: E/W warning text lagging the storm visual; spawn on a sea-level
  rock; worldgen gate not dimension-aware.
- Then `1.5.0+1.21.11` beta, per-version changelog, tag `v1.5.0+1.21.11`, GitHub #7/#8 replies.

## external record discipline

Every slice lands its evidence row in `<external-notes>/evidence-registry.md` and its pointer in
`<external-notes>/index.md` in the **same pass** as the work.

## Closeout

Harvest-complete note; archive `port/1.4.0-beta-1.21.11`; write
`docs/porting/diffs-learned-1.5.0+1.21.11.md` for thread 3.

## Open items owned by Maintainer

- Modrinth / CurseForge upload of `1.5.0+26.1.2` (no publish tooling in-repo).
- Pasting thread 1's diffs-learned into the protected `kickoff-1.5-1.21.11.md`.
- `tools/verify_structure_climate_guard.py` retirement (Slice E).
- The 1.4-era `port/1.4.0-beta-1.21.11` worktree is still dirty (10 modified + 3 untracked, incl.
  the untracked `HANDOFF-1.21.11-state-and-province-gap-20260614.md`). Read it early for the
  province-gap analysis; **do not commit or discard without her.**
