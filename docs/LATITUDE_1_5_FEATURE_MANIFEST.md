# Latitude 1.5 Feature Manifest

`status: Phase 6 dev-only testing toolkit PASSED locally` · `branch: codex/1.5-mini-launch-26.2` · `Phase 6 base: 51277a3467ddba69fde516b7969f5a6034238b81` · `recorded: 2026-07-19`

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
accessed for Phase 4. At that historical boundary, Phase 5 authorization was a separate gate and the
26.2 port remained unauthorized. Profile staging, tagging, pushing, release, upload, and publication
remain unauthorized now.

## Post-Phase 4 local-polish record

The following commits are local, bounded follow-up work after the committed Phase 4 savepoint. They
do not extend the Phase 4 compatibility matrix or authorize Phase 5, a port, a release, a push, or
any profile/world action.

| Commit | Bounded local change | Current proof boundary |
| --- | --- | --- |
| `8fe97d051ca5b1a367916f2200d16c5ec31ad678` | Replaces polar blindness with 85–90° whiteout fog and finite, poleward warning episodes. | Live visual acceptance PASSED for fog density/color, warning outline/fade/direction, equatorward re-arm, and later poleward re-entry on the disposable 26.1.2 surface. |
| `d252770c258f9f4a7da6e8c11e98231cdd84233b` | Adds player-selectable HUD location detail modes: off, biome, zone, or biome plus zone. | Historical implementation commit; its live matrix exposed a pristine-default detached overlap that is superseded by `d2cdee02`. |
| `54b62988df85f8fc6ec5c8e882679bb82a6c91db` | Allows village origins through exactly 80° absolute latitude and vetoes only origins beyond it. | Historical placement-only implementation; its live matrix exposed stored-start `/locate` ghosts in fresh worlds and is superseded by `27327f20`. |
| `bf6e07281bb133977f25992b88136ced30cd6b6f` | Adds a lambda-local populated-biome column cache. | Base output, climate, and disposable create/save/reload proof passed locally; the exact Promenade and combined-provider output gate remains HOLD. |
| `188b2f52ed170b3c2a57937ea600ccbb10617607` | Caches constant biome IDs and immutable launch flags only. | Structural/fresh-JVM semantics and clean-build proof passed locally; no measured speed claim is made. |
| `0aacc55aebc1c74d5e71e73652052486ea5be024` | Batches analog-compass disc spans while retaining the existing visible-pixel model. | Exact raster/model parity, clean-build proof, and the live size/theme/transparency/preview/placement matrix PASSED on the disposable 26.1.2 surface. |
| `e6327c5715adc3acb295223d24963e74d682044c` | Rejects mountain-class temperate beach ridges while retaining low and rolling beaches. | Existing focused policy/model, accepted-output, climate, and build proof remains valid. Exact 46°S identity is irrecoverable, and three replacement proof harnesses were withheld from runtime after fresh QA false-green/safety findings; exact new BEACH runtime remains HOLD. |
| `d2cdee02051476e1681b7bb9620eac4aa5acbd80` | Separates pristine-default detached HUD detail from the compass while preserving explicit placement. | The 16-state digital/analog, Follow/Detach, Off/Biome/Zone/Biome+Zone live matrix, bounded-width representatives, real legacy-JSON migration, corrected default captures, and detail-only drag proof passed. |
| `27327f205f21f2b88625f60e49b614eed99278ee` | Prevents new beyond-80° village starts while retaining the existing placement shield. | The live allowed village at 79.104°, fresh-world `/locate`/bell/NBT checks, and `beyond80=0` passed; executable policy proof separately allows exact 80° and non-village paths. Copied old saves may retain stored-start `/locate` results while physical placement remains suppressed; Maintainer accepted that legacy compatibility residue for the pre-2.0 1.5 release on 2026-07-18. |

Pasture decision: rolling and upland terrain remains allowed. No pasture patch was made or is
implied by the beach-ridge guard.

### Exact partial/manual HOLDs

- Promenade exact parity remains HOLD: `8/55,225` sampled cells differ. Combined-provider exact
  parity also remains HOLD: `23/55,225` cells differ and the frozen environments are not exact
  matches. Matching palettes and stable provider presence do not turn either result into parity.
- The committed Phase 3/4 Terralith provider-compatibility matrix remains the authoritative pass.
  The July 18 correctness refresh is HOLD after three tooling candidates; the final closed-world
  candidate passed 56 pure tests but fresh QA found three remaining false-green paths. This does
  not revoke Phase 3/4 support and does not turn Promenade or combined-provider exact parity green.
- The controlled 56-pair Terralith performance harness/design is adversarially green, but execution
  has not started: fresh normalized non-benchmark CPU samples of `12.53%`, `14.47%`, `12.64%`,
  and `13.62%` exceeded the
  frozen `10.00%` ceiling. Protected Creative Cloud, WindowServer, Codex, OBS, and external Gradle
  activity remained untouched. The only Terralith performance result remains the prior
  inconclusive `2.5%` mean whose interval crosses zero. No FPS or MSPT claim is made.
- The accepted beach screenshot establishes `minecraft:beach` at 46°S but does not preserve seed,
  coordinates, profile, or jar identity. Exact-scene acceptance therefore remains identity-blocked
  HOLD. Three candidate/replacement harnesses were kept out of a new runtime after fresh QA found
  safety/provenance false-green paths; no substitute tuning is authorized.

Current acceptance chronology, exact candidate hashes, QA verdicts, and stop state:

`docs/binder/latitude-1-5-beach-terralith-acceptance-20260718.md`

### Accepted legacy behavior

- Copied old saves may retain beyond-80° stored village starts that `/locate` can return. Physical
  placement remains suppressed, and new worlds do not store new starts beyond 80°. Maintainer accepted
  this old-save locator residue for the pre-2.0 Latitude 1.5 polish release on 2026-07-18; no saved
  NBT deletion or migration is required for 1.5.

Polar presentation, the HUD 16-state/width/migration matrix, and the analog
size/theme/transparency/preview/placement matrix passed on the disposable 26.1.2 live surface.
The 26.2 port preserves those behaviors through focused policy, structural, config, and disposable
runtime proof; it does not claim a new 26.2 pixel-by-pixel visual review.

## Phase 5 result

The independent Minecraft 26.2 port passed all thirteen mandatory Phase 5 gates at implementation
and artifact commit `96c43b452027a2e89e7899b7f60227656dab151d`.

| Gate family | Status | Evidence boundary |
| --- | --- | --- |
| Java 25 build, invariants, and target-native impact audit | **PASSED** | Clean build plus all four executable policy suites. |
| Three-seed × three-size exact-ID and climate matrix | **PASSED** | Nine rows, no failures; summary SHA-256 `b563e1ef30c414bf70afe89c57b8b7e13250b543c170b3cb330dd49fed08cef7`. |
| Fresh and copied-old-save lifecycle/policy | **PASSED** | Fresh server/reload and copied 26.1-to-26.2 save migration; migration hash index SHA-256 `c4e4f56737a52ba30928383192bf5d2dcb98a71a185d4095783ffccd0ccc3739`. |
| Exact-jar client, create-world, HUD/config, and E/W barriers | **PASSED** | Exact candidate bytes loaded; fresh/reload first-safe-frame proof; create matrix `8ac866be…`; HUD matrix `03eab996…`; restart verdict `aee28c84…`; non-crossing barrier runtime proof. |
| Available providers and Sodium | **PASSED** | Absent, Terralith, Tectonic, and Biomes O' Plenty matrix SHA-256 `81c93856bd6a77eac19924e7247cfe444f185f5d4dbdae5e0ad1ad5a59d773b9`; Sodium `0.9.1+mc26.2` launch/world load. |
| Dedicated server, sulfur caves, and villages | **PASSED** | Fresh/reloaded server; exact sulfur-cave assertion at Y=2; villages through the allowed band and zero stored starts beyond 80° in the fresh proof world. |
| Metadata, purity, hash, and 2.0 denylist | **PASSED** | `1.5.0+26.2`, `>=26.2 <26.3`, `Build-Dirty: false`; jar SHA-256 `1753f8d038d3387b4c152a84623d85a2eaa0286a40ff510e2c00c6e30d793cc0`. |

The final docs exit savepoint, created after this text is reconciled, is intentionally distinct from
the implementation/artifact commit embedded in the proved jar.

## Phase 6 dev-only human-testing toolkit

Maintainer authorized a bounded Phase 6 tooling slice after the clean 26.2 port. It adds development-
environment-only commands for structured test cases, safe signed-latitude positioning, computed
polar-presentation policy traces, provenance-rich screenshots, and flight-speed control.

The public `/flyspeed` command was removed. Its replacement and every new Phase 6 command live
under permission-level-2 `/latdev`, whose implementation is excluded from the distributable jar.
The only production-source change is deletion of that public command and its unused imports.

Local proof passed:

- `55` dependency-free assertions cover path containment, nonzero border centers, representable
  latitude targets, unsafe ±90° rejection, movement transitions, append-only case ordering,
  overlapping-capture rejection, failed-I/O state preservation, and idempotent finish-summary
  recovery with no post-finish events.
- A disposable development dedicated server produced ordered case evidence, reported itself as
  `dedicated_server`, carried exact branch/commit/dirty identity, recorded marker-only capture
  semantics, and explicitly refused to call server coordinates rendered-presentation evidence.
- The Java 25 clean build, invariant scan, create-screen, polar, HUD-detail, and village policy
  suites passed.
- The public-jar verifier found no `com/example/globe/dev/**`, Phase 6 action payload, task
  evidence, or local absolute path.

The integrated-client screenshot and computed-policy trace paths are compiled, structurally
audited, request-time frozen, and development-gated, but were not driven in a live client during
this slice. No screenshot pixels, visible fog, warning rendering, or human aesthetic acceptance
is claimed by the tooling savepoint.

### Phase 7 and release boundary

Phase 6 stops at its local tooling savepoint. Real profile/world staging, tagging, pushing,
release packaging, upload, publication, and every Latitude 2.0 behavior require separate Maintainer
authorization.

Decision record:

`docs/binder/latitude-1-5-holds-accepted-phase5-authorized-20260718.md`

Phase 5 target manifest:

`docs/LATITUDE_1_5_PHASE5_PORT_MANIFEST.md`

Phase 5 chronology:

`docs/binder/latitude-1-5-phase5-20260718.md`

Phase 6 tooling chronology:

`docs/binder/latitude-1-5-phase6-tooling-20260719.md`
