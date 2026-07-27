# Latitude 1.5.0 — Pre-2.0 Polish Roadmap

`status: Phase 4 passed locally; 26.1.x candidate proof clean; savepoint pending` · `owner: Maintainer` · `recorded: 2026-07-17` · `campaign branch: codex/1.5-mini-launch`

> **Purpose.** Ship a compact Latitude update that visibly confirms the project is still maintained,
> releases the finished pre-2.0 polish that never reached players, and fixes bounded older bugs without
> importing Latitude 2.0 geography or touching the dirty Pivot worktree.
>
> **Use this document as the campaign ledger.** Update the phase table, evidence links, decisions, and
> holds as work progresses. Do not silently widen the feature manifest. Dated chronology belongs in
> `<external-notes>/`; the current current implementation stateer belongs in `README.md`.

---

## 1. Release identity and target matrix

Working product name: **Latitude 1.5.0 — Pre-2.0 Polish**

Java has no Minecraft 26.0 release. The Java targets are 26.1, 26.1.1, 26.1.2, and 26.2.
The release uses two artifacts:

| Release lane | Artifact | Supported Minecraft versions | Dependency fence |
| --- | --- | --- | --- |
| 26.1.x | `latitude-1.5.0+26.1.jar` | 26.1, 26.1.1, 26.1.2 | `>=26.1 <26.2` |
| 26.2 | `latitude-1.5.0+26.2.jar` | 26.2 only | `>=26.2 <26.3` |

The shared 26.1.x jar compiles against the lowest target, Minecraft 26.1. Its pre-2.0 source has
already compiled and run headlessly on 26.1, while the same source lineage has live 26.1.2 evidence.
That is architecture evidence, not final compatibility proof: the finished candidate must still pass
client runtime checks on 26.1, 26.1.1, and 26.1.2.

Minecraft 26.2 requires its own artifact because its Loader, Fabric API, build tooling, mappings/API
surfaces, HUD owner, and biome set differ materially from the 26.1.x line.

Official version references:

- [Minecraft Java Edition 26.1](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1)
- [Minecraft Java Edition 26.1.1](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1-1)
- [Minecraft Java Edition 26.1.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1-2)
- [Minecraft Java Edition 26.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2)
- [Minecraft Bedrock Edition 26.0](https://feedback.minecraft.net/hc/en-us/articles/43274629736717-Minecraft-Bedrock-Edition-26-0-Changelog)

---

## 2. Branch and worktree law

Current campaign state at plan approval:

| Surface | Branch / HEAD | State | Law |
| --- | --- | --- | --- |
| Main campaign checkout | `codex/1.5-mini-launch` / `9f44f9ea` | Historical 1.21.11 tree carrying only the authorized campaign documentation delta | Keep as the docs/roadmap owner; do not re-anchor it onto implementation history. |
| Clean 26.1.x implementation worktree | `codex/1.5-mini-launch-26.1x` / `3a79b608` | Phase 4 proof-clean candidate working tree; identity/docs uncommitted | Only frozen Phases 0-4 source/docs/tests and task-owned evidence belong here. |
| Safe 26.1.2 checkpoint | `f26d5f58` | Proven pre-2.0 checkpoint | Candidate base for the 26.1.x line. Branch from the commit object, not the dirty donor worktree. |
| 26.1.2 donor worktree | `feat/custom-biome-expansion-26.1.2` | Protected `run-headless/server.properties` dirt | Never edit, restore, stage, normalize, or adopt the dirt. |
| 26.2 Pivot worktree | `port/canonical-26.2-pivot` / `f5539e35` | Concurrent 2.0 work plus protected `run-headless/server.properties` dirt | Never use as the 1.5 worktree and never alter its dirt. Committed history is behavior evidence only. |

Phase 0 deliberately avoided archive refs, re-anchoring, and history changes. The clean implementation
worktree was created directly from the pinned commit object while this checkout remained the
campaign documentation owner.

The 26.2 lane must be created in a new clean branch/worktree from the approved 26.1.x feature manifest.
Its API adaptations are written target-natively. Do not merge or wholesale cherry-pick the Pivot,
`save/canonical-26.2-baseline`, or another 2.0-bearing tip.

---

## 3. Release contract

### 3.1 Headline worldgen repair: biome confetti

The release headline is fragmented **biomes**, not fragmented buttons.

Required behavior:

- Use exact biome IDs to reduce isolated biome specks and incoherent cross-family fragments.
- Preserve the proven ProvinceAuthority/cohesion behavior from the safe pre-2.0 line.
- Enforce climate laws:
  - no tropical desert or badlands leakage;
  - no temperate arid or frozen-river leakage;
  - bounded polar accent biomes rather than polar domination.
- Consolidate Pale Garden into a coherent region.
- Carry only bounded, already-developed fixes for mushroom-island ocean placement, steep-terrain plains,
  polar/custom-biome tags, mountain wetlands, and comparable invariant-backed defects.
- Keep existing saves on their historical worldgen policy; do not silently rewrite them.

Natural-color screenshots are context only. Exact-ID atlases and metrics carry acceptance.

### 3.2 World-creation anti-popping

All three reported surfaces are in scope:

1. **Spawn-zone choices**
   - Rows remain rendered whenever they intersect the viewport.
   - Partially visible rows are scissored into true half-rows instead of vanishing whole.
   - A partially visible row can be selected through its visible portion; its off-page portion and
     every fully hidden row cannot be selected.
   - Wide three-column clipping stops below the fixed heading; tabbed mode uses its ordinary viewport.

2. **Rules panel**
   - Buttons are registered for input, focus, and narration without being auto-rendered outside the
     panel scissor.
   - Rules controls have exactly one manual render path inside the scissor.
   - Buttons, labels, values, hover, focus, narration eligibility, and click hitboxes share one clip
     boundary.
   - Partially visible controls render continuously; hidden portions never accept clicks.

3. **Rules heading and opaque bar**
   - The fixed Rules heading is drawn outside the scrolling content.
   - The dead clip shelf under the heading is removed.
   - Tabbed mode has no redundant internal Rules heading or blank top strip.
   - Resize, world-size change, Game Rules return, and HUD Studio return cannot produce a doubled,
     frozen, or ghost Rules layer.
   - Rules layout is applied once per rendered frame and private widget lists are cleared on re-init.

Smooth scroll easing is optional polish after clipping and input correctness are green. It cannot
substitute for the anti-popping proof.

Responsive composition:

- At low GUI scale with a roomy logical viewport, World, Spawn Zone, and Rules remain together in the
  three-panel layout.
- At GUI scale 3 or higher, or below a 720-logical-pixel viewport, the screen uses only **World** and
  **Rules** tabs.
- Compact World places Spawn Zone choices beside the planisphere.
- Compact Rules uses four rows across two columns rather than one stack of full-width buttons.

### 3.3 Finished, bounded pre-2.0 polish

- Rename player-facing “Expedition” wording to “World.”
- Re-enable bonus chest and ensure it reaches the created world.
- Add the Generate Structures toggle and ensure it reaches the created world.
- Preserve the existing fix that lets scrolling reach HUD Studio controls.
- Carry the six finished analog compass themes without changing the digital compass.
- Make HUD/HUD Studio sample latitude and zone labels agree.
- Preserve loading-overlay/world-entry lifecycle fixes.
- Keep safe Latitude feature copy while excluding Mercator/longitude-specific loading phrases.
- Preserve public-jar purity and strict version metadata.
- Verify version-matched advertised custom-biome providers select, decorate, render, and load safely.

### 3.4 Explicit exclusions

Latitude 1.5 must not include:

- pole crossings or passage mechanics;
- east/west crossings or passage mechanics;
- longitude;
- Mercator or selectable world shapes;
- GeoAuthority, ClimateAuthority, Terrain V2, tectonics, geology, continents, or new ocean systems;
- the rejected book-style world-creation redesign;
- Random Spawn Zone restoration;
- the later 2.0 HUD/create-screen overhaul;
- atlas aesthetic redesigns;
- broad province-wavelength, polar tree-line, alpine-massif, or jungle-margin expansion;
- new Lithosphere, Connector/NeoForge, or Serene Seasons integration;
- general cleanup, dead-code sweeps, refactors, or “while here” modernization.

Existing 1.x east/west barriers and warnings remain as they are. This release neither removes them nor
makes them crossable.

---

## 4. Phase ledger

Update `Status` and `Evidence / decision` as the campaign progresses.

| Phase | Status | Work | Exit gate | Evidence / decision |
| --- | --- | --- | --- | --- |
| 0. Safe foundation | **PASSED** | Preserve the current pointer, create a clean implementation worktree, freeze the feature manifest and 2.0 denylist. | Current state recoverable; all protected dirt untouched; every candidate behavior marked include, conditional, hold, or exclude. | Clean worktree `<home>/CascadeProjects/Latitude-1.5-26.1x`, branch `codex/1.5-mini-launch-26.1x`, base `f26d5f58`; Java 25 baseline green; no re-anchor/history mutation. |
| 1. Canonical 26.1.x core | **PASSED** | Assemble the safe pre-2.0 worldgen repairs and finished polish on the 26.1.x lane. Audit mixed commits; port behavior rather than contamination. | Compile/build green; exact-ID cohesion and climate invariants meet or improve the proven c9 baseline. | Java 25 build/invariants green. Fixed-seed Regular/Itty/Ginormous exact-ID evidence improves or preserves single-cell cohesion; strict tropical/temperate/frozen-river band checks green. Feature manifest records each included provenance and 2.0 exclusion. |
| 2. World-creation anti-popping | **PASSED** | Implement target-native clipping, input bounds, shared Rules clip geometry, one render path, one layout pass, clean re-init, and the approved responsive composition. | Wide and compact proof shows no pop/dead shelf/ghost layer; correct clicks/focus; two-tab World/Rules layout at high scale. | Maintainer confirmed anti-popping and the two-tab layout. The last clipped-row red now uses an explicit production input policy before generic dispatch. Its `9`-assertion executable regression, structural verifier, clean build, and invariant scan pass. No post-fix live screen retest is claimed. |
| 3. Bounded bug sweep | **PASSED** | Exercise loading, saves, HUD/config truth, world options, providers, `/locate`, structures, performance, and dedicated-server creation. Fix only eligible reproduced reds. | All mandatory gates green; conditional findings green or recorded as HOLD. | Local savepoint `3a79b608` (parent `9b910956`). Two exact reds admitted and fixed: Re-create/seed state hydration and public-jar dev/debug contamination. Fixed-seed Itty/Regular/Ginormous exact-ID and strict climate gates, base/provider atlases, fresh/copied-old-save lifecycle, save policy, create-state NBT, HUD config parity, exact public-jar fresh server/reload, `/locate`, purity, metadata, and Java 25 build are green. Dropdown tooltips, Promenade's non-fatal first-run config warning, and frozen-river reflective warning are HOLD. Evidence: `<home>/CascadeProjects/Latitude-1.5-26.1x/<external-notes>/latitude-1-5-phase3-20260717.md`. |
| 4. 26.1.x candidate | **PASSED** | Test the identical jar on 26.1, 26.1.1, and 26.1.2 with fresh worlds, an older save, and supported provider stacks. | One clean `1.5.0+26.1` candidate passes all three Minecraft versions. | Frozen jar SHA-256 `c2c001e9a042d02d33b6f5a207ca1b3de35ab5191b8869b17ad1df370c8820a6`. Java 25 lowest-target build/invariants, three fresh create/reload rows, 30 fresh-world NBT assertions, two copied-old-save upgrades with 14 assertions, provider stacks with 74 assertions, three disposable exact-jar clients with 42 assertions, and candidate purity/metadata audits passed. The jar is artifact-pure but truthfully records `Build-Dirty: true` while identity/docs changes remain uncommitted. Evidence: `<home>/CascadeProjects/Latitude-1.5-26.1x/<external-notes>/latitude-1-5-phase4-20260717.md`. |
| 5. Clean 26.2 port | **PENDING** | Branch from the approved manifest and manually adapt only the 26.2 toolchain/API differences. | 26.2 launch/load/save, HUD, Sodium, sulfur-cave, biome, UI, and jar gates green. | — |
| 6. Release packet | **PENDING** | Update changelog, support matrix, known limitations, hashes, evidence registry, handoff, external record, and player-facing maintenance copy. | Two independently releasable candidates; stop before tags, pushes, uploads, or publication until authorized. | — |

---

## 5. Mandatory behavior gates

“Mandatory” means the behavior must pass on every applicable target. It does not mean copying a patch
when the target is already green.

| Gate | Required proof |
| --- | --- |
| Exact-ID biome cohesion and family integrity | Multi-seed, multi-size component/contact metrics; Pale Garden region check. |
| Climate-law correctness | Tropical arid zero; no temperate arid/frozen-river leakage; bounded polar accents. |
| Loading and world-entry lifecycle | Exact-jar fresh and existing worlds; overlay remains through render warmup and closes on the first safe rendered frame. |
| Supported custom-biome integrity | Provider absent/present; active source hook; exact IDs; expected decoration; no invisible foliage or registry/load crash. |
| Existing-save policy | Pre-update save retains historical policy/state; new and old chunks behave as documented; save/reload. |
| Create-world state truth | Name/seed survive changes; Re-create truth; bonus chest and structures ON/OFF reach world creation. |
| HUD/config truth | Live and Studio labels agree at known latitudes; controls have no resistance wall; settings survive reload. |
| Fresh dedicated server | Latitude installed before world creation on server and client; expected zones and client HUD behavior. |
| Public-jar purity | No dev/debug/automation classes, generated evidence, local paths, or stray diagnostics; metadata matches source. |
| 26.2 platform correctness | Clean launch; supported Sodium world load; sulfur caves remain locatable/preserved underground. |

---

## 6. Conditional bug bundles

These enter implementation only after an exact-target red:

- climate-mismatched structures;
- chunk-generation performance;
- bounded distribution anomalies such as steep plains, mountain wetlands, or an invalid polar provider tag;
- `/locate` disagreeing with the actual Latitude biome;
- missing or visibly clustered loading feature messages;
- HUD reset/default drift or disappearing dropdown tooltips.

Current public issue routing:

- [#8 Server issues](https://github.com/peetsamods/latitude/issues/8): fresh dedicated-server proof is
  mandatory. Adding Latitude after world creation does not promise retroactive worldgen replacement.
- [#7 Connector incompatibility](https://github.com/peetsamods/latitude/issues/7): current evidence is a
  wrong-version Latitude jar. Strict metadata/docs are in scope; Connector/NeoForge support is not.
- [#3 Terrain-mod compatibility](https://github.com/peetsamods/latitude/issues/3): test advertised,
  version-matched combinations; new Lithosphere/Tectonic/Serene Seasons integration remains deferred
  without a narrow 26.x repro and a separate scope decision.

---

## 7. New-bug admission and stop rule

A newly found bug enters Latitude 1.5 only when all are true:

1. It reproduces on the exact target Minecraft version, candidate jar, profile, seed/world, and action.
2. It breaks existing pre-2.0 behavior or is a target-version port regression.
3. Its fix stays inside one existing subsystem and adds no new architecture, persisted feature state,
   or product design.
4. A narrow green proof can be named before editing.
5. It is candidate-caused, a crash, a save/data risk, an inability to create/load a world, a silent
   old-save migration, or an advertised control/compatibility promise that demonstrably lies.

Everything else becomes **HOLD**. Two failed narrow fixes on the same symptom automatically return it
to HOLD and trigger a shared-cause check before any further attempt.

Once all applicable mandatory gates and already-selected conditional reds are green, stop searching and
prepare the candidate packet.

---

## 8. Integrated proof matrix

### Worldgen

- Three fixed seeds across Itty Bitty, Regular, and Ginormous atlases.
- Exact biome IDs, palettes, connected components, contact metrics, and band invariants.
- Base stack plus each version-matched advertised provider stack.
- Fresh world and existing-save policy checks.

### World-creation UI

- Roomy low-scale three-panel layout and high-scale/constrained two-tab layout.
- Compact World has Spawn Zone beside the planisphere; compact Rules uses two columns.
- Relevant GUI scales and short/normal/tall viewport heights.
- Slow scrolling through every top and bottom boundary.
- Spawn-zone clipped slivers select only through the visible portion; hidden portions cannot select.
- Rules hidden portions cannot hover, click, toggle, or capture focus.
- Tab switching, resize, world-size change, Game Rules return, and HUD Studio return.
- One real world creation using the tested state controls.

### Runtime and compatibility

- Exact-jar launch on 26.1, 26.1.1, 26.1.2, and 26.2.
- Fresh world, older save, save/reload, and clean shutdown.
- Fresh dedicated server with Latitude present before world creation.
- Provider absent/present and supported Sodium checks.
- 26.2 sulfur-cave preservation.

### Release integrity

- Compile, build, invariant, and target-native impact scans.
- Jar contents, metadata, name/version/hash, and active-profile identity.
- Final ancestry/content denylist scan for Mercator, longitude, crossings, GeoAuthority,
  ClimateAuthority, Terrain V2, and their configuration keys.
- Final diff audit mapping every changed file to this roadmap.

---

## 9. Campaign update protocol

At the end of every phase:

1. Update that phase's `Status` and `Evidence / decision` cell here.
2. Add a dated external record note for chronology, proof, decisions, or holds.
3. Update `<external-notes>/README.md` and any active external record index/evidence registry in the worktree that
   owns the phase.
4. Update `README.md` when the current implementation state, root, branch, profile, blocker, or next gate changes.
5. Keep candidate proof, release authorization, tagging, pushing, and publication as separate states.

Allowed phase statuses:

- `PENDING`
- `ACTIVE`
- `PROOF PENDING`
- `PASSED`
- `FAILED`
- `BLOCKED`
- `HOLD`
- `INTENTIONALLY NOT DONE`

No phase may be marked PASSED without a command, log, screenshot, artifact, or explicit inspection
result named in its evidence cell or linked external record note.
