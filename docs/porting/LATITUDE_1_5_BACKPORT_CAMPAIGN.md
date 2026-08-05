# Latitude 1.5 Backport Campaign — Front Door

`status: active` · `established: 2026-08-05` · `owner: Maintainer (she/her)` · `source of truth: tag v1.5.0+26.2`

Bring `1.5.0+<mcver>` to four Minecraft targets. Each port runs in its **own conversation thread**
(never a background task), reads ONLY its kickoff doc plus this file, and follows the Phase-5
manifest method below. The 2.0 line is untouchable from port threads.

## Decisions of record (Maintainer, 2026-08-05)

- **Order (sequential):** 26.1.2 warm-up → 1.21.11 → 1.21.1 → 1.20.1. Each thread hands a
  "diffs learned" section forward to the next kickoff.
- **Cadence:** each port releases `1.5.0+<mcver>` on the **beta channel** the moment its gates and
  the maintainer's live acceptance are green. No batch wave.
- **Versioning:** `1.5.0+<mcver>` — same version number = same features everywhere.
- **Branches:** `port/1.5.0-<mcver>`, cut fresh from tag `v1.5.0+26.2`. The 1.4-era port branches
  are **harvest, never bases** (they fork from v1.3 and lack 22 of 28 `world/` classes).
- **Worktrees:** `<home>/CascadeProjects/Latitude-port-1.5-<ver>`, one per port, isolated.
- **Artifact content policy** (`docs/release/artifact-content-policy.md`) is standing law on every
  port: `dev/**` excluded from public jars, `tools/**` (the `/latitude` operator set) ships,
  the phase-6 verifier must pass per port.

## Why this is not another 26.2-scale effort (measured evidence)

- Porting 1.5 across one MC major (26.1x→26.2, Phase 5) cost **33 files, ±180 lines, 2 days** under
  the manifest method. The 1.4-era 1.20.1 port's ~5 weeks was the **ad-hoc method's** cost.
- The 1.4 Stage-1 worldgen backport landed an **identical +152/−7 diff on all three old versions**:
  the worldgen brain transplants near-verbatim. The 26.2 campaign's hard work was algorithm design;
  it is done and does not recur.
- **Worldgen JSON (173 files, ~22.9k lines) is byte-identical to the 1.21.11 port's** and needs only
  a ~118-line `preliminary_surface_level` patch on 1.20.1 (`find_top_surface` → explicit spline
  chain) — already written on `port/1.4.0-beta-1.20.1`.
- **Java audit is clean**: nothing above Java 17 in use (records/arrow-switch/`var` only;
  `Math.clamp` hits are the project's own `LatitudeMath.clamp`; `options.release = 25` is
  aspirational). No de-sugaring needed anywhere.
- Pure core (41 files / 5,839 LOC, zero MC imports) + near-pure (2,101 LOC) port free.
  `LatitudeBiomes.java` (11.5k LOC) is a data table with 16 imports. `dev/` (12.4k LOC) never ships
  and can be deferred per port.
- The recurring cost is the **client layer**: ~5,900 MC-touching LOC across 4 screens + 9 client
  mixins, of which 5 target 26.x render-extraction APIs. Mitigation: the entire draw layer is ~150
  primitive `fill`/`text`/`pose`/`scissor` calls (1:1 equivalents everywhere; zero blits/pipelines),
  and the old 1.21.11 port contains a working GuiGraphics-form UI as idiom reference.

## The method (Phase-5 manifest regime — copied here because PORTING.md lives on the 2.0 lineage,
## which port threads must not browse)

1. **Preflight**: `git rev-parse --show-toplevel`, `status -sb`, `branch --show-current`,
   `rev-parse --short HEAD`; worktree content must equal `v1.5.0+26.2` (`git diff v1.5.0+26.2`
   empty) before work starts.
2. **Hour-1 mappings spike**: retarget metadata only, attempt compile on **Mojang official
   mappings** (Loom supports them on all targets). Count REDs. Viable → whole tree's naming
   survives; port = package moves + API-generation gaps. Not viable → fall back to Yarn with the
   old branch as idiom reference. **Record the decision in the binder either way.**
3. **Slice A — toolchain metadata only** (exactly 4 files): `build.gradle` Loom line,
   `gradle.properties` versions, gradle wrapper, `fabric.mod.json` MC fence + java requirement.
   Commit before any source edit.
4. **Slice B — compile ladder**: each source file admitted **only on a named compiler RED**;
   consult the per-target delta table and harvest pointers before hand-solving anything.
5. **Slice C — runtime hooks**: mixin-apply proof for every retargeted mixin (a green build does
   NOT prove a mixin applies; `defaultRequire: 1` stays); the settings-key-persistence check
   (`globe_radius` rail in LatitudeWorldState survives → no silent vanilla worlds — the `18f2629f`
   bug class); Sodium tolerance per target.
6. **Gates**: full `clean check build -PenableInvariantScan=true latitudeInvariantScan` (the
   pure-core policy suites must pass **unchanged** — that is itself a port-correctness signal);
   `python3 tools/verify_phase6_dev_tooling.py` + `--public-jar` (dev/ absent, tools/ present);
   headless exact-ID atlas (`max-tick-time=-1` in server.properties or the watchdog kills it) with
   seed-matched distribution comparison against a 26.2 reference export; **dedicated-server smoke**
   (see GitHub crosswalk); Pale Garden / Mushroom Fields spot checks.
7. **Live lane**: per-port Modrinth profile `Lat 1.5 - <ver> - TEST` (none exist yet; provider
   jars for old MC lines are NOT on this machine and must be downloaded), TEST N staging with
   built-vs-staged SHA parity, dev-client pass, **the maintainer's live acceptance**.
8. **Release**: `1.5.0+<mcver>` beta; per-version changelog (re-frame "Before you update" — a
   1.20.1 player is NOT crossing the 26.2 save-format conversion); tag `v1.5.0+<mcver>`; binder
   evidence rows throughout (append-only, schema-checked); update applicable GitHub issues.
9. **Closeout**: harvest-complete note; archive the corresponding `port/1.4.0-beta-*` branch;
   write the "diffs learned" section into the next kickoff.

**Stop conditions** (inherited): broad compile drift after two repair attempts on one blocker; a
mixin that cannot prove it applies; any temptation to copy algorithms by hand instead of
transplanting (the algorithm is already on your branch — you cut from the 1.5 tag); any 2.0
source/history inspection; scope creep beyond the kickoff's allowlist.

## Per-target toolchain (from the proven 1.4-era branches; revalidate in Slice A)

| Target | Loom | Java | Loader | Fabric API | Mappings (fallback) | Mixin level |
| --- | --- | --- | --- | --- | --- | --- |
| 26.1.2 | (Phase-5 manifest reversal) | 25 | 0.17.x | 26.1.2 line | official | JAVA_21 |
| 1.21.11 | ~1.14.10 | 21 | 0.17.3 | 0.140.x+1.21.11 | Yarn 1.21.11+build.1 | JAVA_21 |
| 1.21.1 | ~1.14.10 | 21 | 0.17.3 | 0.115.x+1.21.1 | Yarn 1.21.1+build.3 | JAVA_21 |
| 1.20.1 | ~1.9.2 | 17 | 0.16.14 | 0.92.x+1.20.1 | Yarn 1.20.1+build.10 | **JAVA_17** |

## Harvest map (the 1.4-era branches: value by exact location)

- `port/1.4.0-beta-1.20.1` → `docs/porting/port-1.20.1-*.txt` (~25 one-file API-delta notes — the
  lookup table); refmap rule commit `764887d5` (`globe.mixins.json` must name
  `latitude-refmap.json` on 1.20.1); bootstrap diff `169fbda9`/`0c1a184b` (30 files, "what breaks
  dropping two generations").
- `port/1.4.0-beta-1.21.1` → the 2,514-line Yarn-idiom create-world stack (`fd7360b5`); the mixin
  ordering rule (init-redirect at `init()` HEAD preempts toggle/spawn-zone mixins, `c7fe597a`).
- `port/1.4.0-beta-1.21.11` → `docs/porting/HANDOFF-1.21.11-worldgen-treeline-alpine.md`; the
  `18f2629f` settings-key-persistence diagnosis; working GuiGraphics-form screens; **plus 13
  uncommitted files in that worktree including an untracked
  `docs/porting/HANDOFF-1.21.11-state-and-province-gap-20260614.md`** — review before harvesting;
  do not commit or discard them without Maintainer.
- Cross-version constants: `ProtoChunk#setBlockState` `int flags`→`boolean`; `Biome#doesNotSnow`
  seaLevel param (its mixin was deleted — stays deleted); `ChunkStatus` moved packages at 1.20.2;
  `tags/block` (1.21+) vs `tags/blocks` (1.20.1); ConventionalBiomeTags v2 (1.20.5+) vs v1.

## GitHub issues crosswalk (fold-in per Maintainer, 2026-08-05)

| Issue | What it is | Campaign obligation |
| --- | --- | --- |
| **#8** Server issues | Zones/HUD reported dead on a dedicated server; likely added-after-creation (documented-expected in 1.5) but a real server gap was never ruled out | **Every port's gates include a dedicated-server smoke**: server jar boots, fresh world generates globe worldgen, `GlobeStatePayload` handshake reaches a Latitude client, zone titles/HUD live. The 26.1.2 warm-up runs it first; report the result on #8 with 1.5 links + the existing-worlds documentation |
| **#7** Connector crash | `ExistingWorldLoadingOverlayStartMixin` target-miss under Sinytra Connector; root cause was a 1.20.1 jar on 1.21.1 NeoForge — the only-available-file trap this campaign removes | Per-port rule: **client overlay/UI mixins fail soft** (`require = 0` + guards where degradation is acceptable — a missed loading-overlay target means vanilla loading screen, never a crash; the old 1.20.1 build warned on exactly these targets). Strict MC fences per target. Connector/NeoForge stays out of scope, stated in each release's known limitations. Reply on #7 when the 1.21.1 file ships |
| **#3** Worldgen-mod compat (deferred) | Lithosphere biome-source compat exists as a saved slice: `compat/1.21.1-lithosphere-runtimebase` @ `621ac4eb`, tag `save/1.21.1-lithosphere-biome-source-compat-runtimebase`; remaining: `populateBiomes` gate + blocked true-ocean control proof. Live Modrinth description says "in active development" | **1.21.1 kickoff carries a stretch slice** (only after core port green): harvest `621ac4eb`, carry the gated acceptance into `ChunkGeneratorPopulateBiomesMixin`, rerun the bounded proof; stop condition = no valid true-ocean controls → re-defer and update #3 honestly. Serene Seasons hemisphere bridge stays on the 2.0 docket |

## Campaign completion

Four `1.5.0+<mcver>` betas live; Modrinth shows current files on every major target; 1.4-era port
branches archived; #7/#8 answered, #3 honestly updated; binder rows for every gate.
