# Latitude 1.5 Feature Manifest

`status: Phase 4 committed; seven bounded post-Phase 4 local-polish commits committed; manual and measured-proof HOLDs remain` · `branch: codex/1.5-mini-launch-26.1x` · `base: f26d5f58` · `recorded: 2026-07-17`

This is the implementation-side allowlist for the Latitude 1.5 pre-2.0 polish campaign. The campaign
roadmap lives in the docs root at:

`<home>/CascadeProjects/Latitude (Globe)/docs/LATITUDE_1_5_PRE_2_0_POLISH_ROADMAP.md`

## Phase 0 safety result

- Implementation uses this clean worktree rather than re-anchoring the historical docs checkout.
- Base commit is pinned to `f26d5f586ed7fbf617b81cc366fac3255ab0bf1e`.
- The dirty 26.1.2 donor and 26.2 Pivot worktrees are read-only/protected.
- No Modrinth profile, jar, tag, push, publication surface, or protected dirty path is in scope.

## Included Phase 1 behavior

Each entry must either cherry-pick cleanly without excluded behavior or be reimplemented as a bounded
target-native patch.

| Evidence commit | Included behavior |
| --- | --- |
| `48e7f241` | Mushroom-island ocean gate, bounded polar diversification, savanna variety, and accepted alpine snow-line fixes. |
| `62658ff2` | Tropical-arid prohibition, bounded arid-belt rules, and poleward arid/frozen-river clamps. |
| `333e04db` | Temperate plains-on-steep terrain compatibility gate. |
| `bbde92b3` | Rename player-facing “Expedition” wording to “World.” |
| `7baa1acc` | Bonus chest and Generate Structures state truth. |
| `67e26ea5` | Six append-only analog compass themes. |
| `26e5dd9d` | CliffTree polar tag correction. |
| `0e806c07` | Keep flat wetlands off mountain terrain. |
| `1f6f278c` | HUD/HUD Studio sample zone and latitude truth. |
| `e8ad62bf` | Port only the loading-screen hunk that always starts with a Latitude feature phrase. The E/W warning hunk is excluded as crossing-adjacent. |
| `3c50c5d4` | Consolidate Pale Garden into one coherent region. |

## Included Phase 2 behavior

The following commits are **behavior evidence only**. Their source belongs to later APIs and/or
2.0-bearing history, so they must not be wholesale cherry-picked:

| Evidence commit | Required target behavior |
| --- | --- |
| `91eb6764` | Optional smooth scroll target/display separation after correctness. |
| `18a82995` | Spawn-zone rows render through a scissor while intersecting the viewport. |
| `b1e9a68b` | Rules controls render manually inside the Rules scissor. |
| `53ccd98a` | Rules layout is applied once per frame. |
| `0aa3d840` | Private Rules widget collection is cleared on every init. |
| `fbfbd8fa` | Fixed headings remain outside scrolling content. |
| `29a37881` | Remove the dead clip shelf under the Rules heading. |
| `10a61055` | One shared Rules clip boundary owns render, culling, hover, focus, and input. |

Target-native layout rule added from the maintainer's high-GUI-scale live review:

- GUI scale 3 or higher uses two tabs: World and Rules.
- The compact World tab places Spawn Zone choices beside the planisphere.
- The compact Rules tab uses a four-row, two-column control grid.
- Normal GUI scales also use tabs when the logical pane viewport is below 720 pixels.
- Three-column mode remains available only for normal-scale, genuinely roomy viewports.
- Any visible portion of a Spawn Zone row is selectable; the shared clip gate rejects only the
  off-page portion and fully hidden rows.

## Conditional only after an exact red

- Climate-mismatched structures (`7f0e5694`).
- Chunk-generation performance.
- `/locate` disagreement with actual Latitude biome.
- Additional distribution anomalies beyond the included bounded fixes.
- HUD reset/default drift or disappearing dropdown tooltips.

## Explicit holds

- Province wavelength (`6b834272`).
- New polar tree-line behavior (`611a307b`).
- Sparse-jungle-margin expansion (`aeb6bcb4`).
- Alpine-massif shaping (`6191f0fc`).
- Dev-command expansion (`4e92eb9d`, `db4dd160`).
- Mercator-specific size copy (`dc052342`).
- Mercator-width loading phrases (`ebcea32c`).

## Hard denylist

- `9a71caa1` Mercator world shape.
- `a476e86c` 2.0 version transition and every 2.0 product feature.
- Pole crossings or passage mechanics.
- East/west crossings or passage mechanics.
- Longitude or selectable world shapes.
- GeoAuthority, ClimateAuthority, Terrain V2, tectonics, geology, continents, or new ocean systems.
- Random Spawn Zone, the rejected book UI, later HUD/create-screen redesign, or atlas redesign.
- Wholesale merges/cherry-picks from the Pivot or a 2.0-bearing branch tip.

## Phase 0-2 result

| Phase | Status | Evidence |
| --- | --- | --- |
| 0. Safe foundation | **PASSED** | Clean worktree at `f26d5f58`; Java 25 baseline compile/invariant green; this manifest frozen; protected donor/Pivot dirt excluded. |
| 1. Canonical 26.1.x core | **PASSED** | Java 25 compile/invariant green; fixed-seed Regular/Itty/Ginormous exact-ID comparisons; strict band checks green; save-policy owner unchanged; final denylist audit green. |
| 2. World-creation anti-popping | **PASSED** | Maintainer live-confirmed anti-popping and the two-tab composition. The final clipped-row red has a shared production input policy, a dependency-free executable regression (`9` assertions), structural wiring proof, and a clean Java 25 build/invariant pass. This is a local pass; no post-fix screen retest was performed. |

Detailed chronology and metrics:

`docs/binder/latitude-1-5-phases-0-2-20260717.md`

These phase results are frozen by the single local implementation savepoint with parent
`f26d5f586ed7fbf617b81cc366fac3255ab0bf1e`. The resulting commit hash is verified from Git after
creation rather than embedded in its own contents. No tag, push, profile staging, upload,
publication, 26.2 port, or release authorization is included.

## Phase 3 result

| Gate | Status |
| --- | --- |
| Exact-ID cohesion and climate law | **PASSED** |
| Fresh/copy-old lifecycle and save policy | **PASSED** |
| Supported provider absent/present integrity | **PASSED** |
| Create-world and Re-create state truth | **PASSED** |
| HUD/HUD Studio truth and config persistence | **PASSED** |
| Fresh dedicated server and exact public-jar reload | **PASSED** |
| Public-jar purity and metadata | **PASSED** |

Phase 3 admitted two exact reds only: Re-create/seed state hydration and public-jar dev/debug
contamination. Their bounded fixes, mandatory proof matrix, conditional HOLD decisions, and evidence
paths are recorded in:

`docs/binder/latitude-1-5-phase3-20260717.md`

Phase 3 is frozen in local implementation savepoint
`3a79b608039c85e0ffca5f4f9a47e2e0935f7fa6`. It did not authorize the 26.2 port, profile staging,
tagging, pushing, release, upload, or publication.

## Phase 4 result

| Gate | Status | Evidence |
| --- | --- | --- |
| Lowest-target 26.1 clean build | **PASSED** | Java 25 clean build, invariant scan, and `9` clip-policy assertions. |
| Identical jar on 26.1 / 26.1.1 / 26.1.2 | **PASSED** | SHA-256 `c2c001e9a042d02d33b6f5a207ca1b3de35ab5191b8869b17ad1df370c8820a6` in every server, provider, and client row. |
| Fresh create, save/reload, and clean shutdown | **PASSED** | Three fresh dedicated-server worlds and three reloads; `30` NBT assertions. |
| Older-save carry-forward | **PASSED** | A disposable 26.1 world loaded under 26.1.1 and 26.1.2; `14` NBT assertions preserve seed, preset, and save policy. |
| Supported provider stacks | **PASSED** | Terralith/Lithostitched/Promenade/Biolith on all three targets; Biomes O' Plenty/GlitchCore/TerraBlender additionally on its supported 26.1.2 target; `74` assertions. |
| Exact public-jar client entry | **PASSED** | Three disposable clients joined task-owned servers, reached Latitude's first-safe playable tick, cleared the bespoke loading overlay, and left clean server stops; `42` assertions. |
| Candidate purity and metadata | **PASSED** | Entry/content denylist, mixin closure, metadata fence `>=26.1 <26.2`, and `12` exact candidate copies audited. |

Canonical Phase 4 evidence:

`docs/binder/latitude-1-5-phase4-20260717.md`

This local Phase 4 compatibility pass was committed as local savepoint
`858b815002ef82b20d55deadb0ac5ad970a9c823` (`Latitude 1.5: save 26.1.x candidate proof`). The
earlier frozen candidate correctly recorded `Build-Dirty: true` before that savepoint; that is
historical candidate metadata, not a current-tree claim. No real profile/world or screen/window was
accessed for Phase 4. Phase 5 authorization remains a separate gate. The 26.2 port, profile
staging, tagging, pushing, release, upload, and publication remain unauthorized.

## Post-Phase 4 local-polish record

The following commits are local, bounded follow-up work after the committed Phase 4 savepoint. They
do not extend the Phase 4 compatibility matrix or authorize Phase 5, a port, a release, a push, or
any profile/world action.

| Commit | Bounded local change | Current proof boundary |
| --- | --- | --- |
| `8fe97d051ca5b1a367916f2200d16c5ec31ad678` | Replaces polar blindness with 85–90° whiteout fog and finite, poleward warning episodes. | Dependency-free policy/integration proof is included in the local build; a polar live-visual check remains HOLD. |
| `d252770c258f9f4a7da6e8c11e98231cdd84233b` | Adds player-selectable HUD location detail modes: off, biome, zone, or biome plus zone. | Policy/config proof is included in the local build; live-cycle all four Off/Biome/Zone/Biome+Zone modes in digital and analog HUD, Follow and Detach positioning, at intended GUI widths, plus a saved legacy-JSON migration spot-check; this remains HOLD. |
| `54b62988df85f8fc6ec5c8e882679bb82a6c91db` | Allows village origins through exactly 80° absolute latitude and vetoes only origins beyond it. | Boundary/integration proof is included in the local build; a live village `/locate` check is unrun and remains HOLD. |
| `bf6e07281bb133977f25992b88136ced30cd6b6f` | Adds a lambda-local populated-biome column cache. | Base output, climate, and disposable create/save/reload proof passed locally; the exact Promenade and combined-provider output gate remains HOLD. |
| `188b2f52ed170b3c2a57937ea600ccbb10617607` | Caches constant biome IDs and immutable launch flags only. | Structural/fresh-JVM semantics and clean-build proof passed locally; no measured speed claim is made. |
| `0aacc55aebc1c74d5e71e73652052486ea5be024` | Batches analog-compass disc spans while retaining the existing visible-pixel model. | Exact raster/model parity and clean-build proof passed locally; the analog live visual matrix remains HOLD. |
| `e6327c5715adc3acb295223d24963e74d682044c` | Rejects mountain-class temperate beach ridges while retaining low and rolling beaches. | Focused policy/model, accepted-output, climate, and build proof passed locally; the same-world 46°S visual retest remains HOLD. |

Pasture decision: rolling and upland terrain remains allowed. No pasture patch was made or is
implied by the beach-ridge guard.

### Exact partial/manual HOLDs

- Polar whiteout and warning presentation still need a live visual check.
- HUD Studio still needs a live cycle of all four Off/Biome/Zone/Biome+Zone modes in both digital
  and analog HUD, in Follow and Detach positioning, at intended GUI widths, plus an actual saved
  legacy-JSON migration spot-check.
- The analog compass still needs the live visual matrix across size, theme, transparency, preview,
  and live placement.
- The accepted beach scene still needs a same-world 46°S visual retest with the exact candidate jar
  and world identity frozen first.
- Village behavior still needs a live `/locate` check; no such manual run is recorded here.
- Performance Slice A's Promenade and combined-provider same-session exact-output comparisons remain
  HOLD: their palettes match, but the ID maps and inventories differ in the current harness.
- Controlled baseline-versus-candidate Spark/JFR measurement remains pending. No FPS, MSPT,
  frame-time, GPU-time, or full visual claim follows from the local model/build proofs.

### Version and authorization boundary

The only compatibility proof claimed here is the committed Phase 4 matrix for Minecraft 26.1,
26.1.1, and 26.1.2. Minecraft 26.0 and 26.2 are separate and unperformed; the 26.2 worktree stays
protected. Nothing in this manifest authorizes Phase 5, a port, profile staging, tagging, pushing,
release, upload, or publication.
