# Latitude Porting Front Door

`status: active front door`
`updated: 2026-07-02`
`current planning target: Minecraft 26.2`

Read this before any Minecraft-version pivot, backport, forward-port, or version-family carryover.

## Start Here

1. `docs/LATITUDE_2_0_OVERHAUL.md`
2. `docs/porting/VERSION_MATRIX.md`
3. `docs/porting/PORTABILITY_ARCHITECTURE.md`
4. `docs/porting/PORTING_BOOTSTRAP.md`
5. `docs/porting/PORTING_RISK_FILES.md`
6. Relevant dated handoff, especially `docs/porting/HANDOFF-1.4-beta-backport.md` for the older 1.4 backport lessons.

## Canonical Rule

Latitude 2.0 should pivot to Minecraft `26.2` before the earthlike-world overhaul begins. Do not build the large geography/climate rewrite on `26.1.2` and then port it later.

The current 26.1.2 line remains a reference/proven baseline until the 26.2 pivot compiles and passes deterministic proof.

## Porting Goal

Future ports should mostly touch:

- Build metadata.
- Platform adapters.
- Mixin targets.
- Version docs.

Future ports should not require hand-transplanting climate algorithms through `LatitudeBiomes.java`.

## Standard Port Start

1. Run repo preflight:

   ```bash
   git rev-parse --show-toplevel
   git status -sb
   git branch --show-current
   git rev-parse --short HEAD
   git tag --points-at HEAD
   ```

2. Confirm the target version in `VERSION_MATRIX.md`.
3. Create or switch to an isolated target branch/worktree.
4. Change build/version metadata only.
5. Compile.
6. Repair API/mixin drift with the smallest safe patches.
7. Run deterministic proof before profile staging:
   - compile/build proof
   - exact-ID Atlas proof
   - analyzer proof
   - flag-off regression proof when relevant
8. Stage a Modrinth profile jar only after deterministic proof is green.
9. Record external record evidence and update docs before savepoint/tag/push.

## 26.2 Pivot Scope

The 26.2 pivot is not the earthlike overhaul yet.

Allowed:

- Build metadata retarget.
- Loader/API/Loom/Gradle updates.
- Narrow mapping/API repairs.
- Mixin target repairs.
- Headless Atlas proof recovery.
- Docs and version matrix updates.

Forbidden until the pivot is green:

- New continent behavior.
- New climate behavior.
- Terrain-density rewiring.
- Boundary UX changes.
- Profile staging without deterministic proof.
- Release tags or public uploads.

## Stop Conditions

Stop and write a blocker note if:

- Upstream coordinates are unavailable or contradictory.
- The root/branch/HEAD is not the intended target.
- Dirty files overlap the port slice and are unexplained.
- Compile drift is broad after two repair attempts.
- A mixin target cannot prove it applies.
- Flag-off output changes.
- The exact profile jar cannot be proven after staging.
- A future worker starts copying algorithms by hand instead of using/adapting the portability spine.

## Required Documentation

For every port or pivot:

- Update `VERSION_MATRIX.md`.
- Add/update a external record evidence row.
- Link proof artifacts from the evidence row.
- Update the active handoff/current implementation stateer if the current truth changed.
- Update `PORTABILITY_ARCHITECTURE.md` when a new adapter/mixin boundary is created.

## Suggested Commit Shape

For the 26.2 pivot:

```text
build: retarget Latitude to Minecraft 26.2
port: repair 26.2 API drift
test: restore 26.2 atlas proof
docs: record canonical 26.2 pivot
```

For portability foundation:

```text
arch: add Latitude core summary contracts
port: add Minecraft adapter boundary
test: prove flag-off geography summaries
docs: record portability foundation
```

---

## Appendix: origin/main porting guide (preserved for reference)

_The section below is `docs/porting/PORTING.md` as it existed on `origin/main` at the time of this merge —
a parallel porting front door that treated Minecraft `26.1.2` as canonical and routed ports through a
separate `Latitude-custom-biome-expansion-26.1.2` worktree. This is an add/add conflict (both sides added a
`docs/porting/PORTING.md`); nothing from main's version has been dropped. The phase5 front door above
(targeting the `26.2` pivot, dated 2026-07-02, newer than main's 2026-06-18) is the current/active guide._

# Latitude Porting Guide

`scope: Latitude ports and backports` · `status: active` · `updated: 2026-06-18`

This is the operational front door for Latitude porting. It turns the scattered porting lessons into a short checklist so each backport starts from known truth instead of rediscovery.

## Read Order

1. `README.md` for current project state.
2. `docs/porting/PORTING.md` for durable traps and stop rules.
3. This file for porting procedure.
4. Dated external record or port handoff docs only when this file links to them.

## Current Porting Law

- Current canonical line: Minecraft 26.1.2.
- Older lines, including 1.21.11, 1.21.1, and 1.20.1, are backports.
- 26.2 is a later evaluation target, not current canon.
- Do not resume older-version implementation until the 26.1.2 source of truth is accepted or Maintainer explicitly opens a bounded emergency lane.

## Six-Line Port Working Card

Every port/backport slice starts with this card:

```text
Objective:
Root/profile:
Allowed work:
Forbidden lanes:
Proof gate:
Stop condition:
```

If the target version, symptom, donor system, or proof gate changes, rewrite the card before continuing.

## Target Matrix

| Target | Role | Expected root/profile | Primary risk |
| --- | --- | --- | --- |
| 26.1.2 | Canonical Latitude 1.4 source of truth | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`; Modrinth `Lat 1.4+26.1.2` | Do not treat public release naming, dev candidate, and profile jar as the same surface. |
| 1.21.11 | Future backport | `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11`; Modrinth `Lat 1.4+1.21.11` | Partial v1.4 state; missing `ProvinceAuthority`; port and canonical have drifted both directions. |
| 1.21.1 | Future backport | `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.1`; Modrinth `Lat 1.4+1.21.1` | Yarn/API drift and possible missing local live-test front door. |
| 1.20.1 | Future backport | `<home>/CascadeProjects/Latitude-port-1.4.0-1.20.1`; Modrinth `Lat 1.4+1.20.1` | Older Java/API/refmap regime; hardest target; expect partials and document blockers. |

Always verify the current root, branch, HEAD, `gradle.properties`, and active profile contents before using these paths as current truth.

## Port Preflight

Before code changes:

1. Run repo preflight in the target root:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
```

2. Read `gradle.properties` for `minecraft_version`, loader/Fabric versions, and `mod_version`.
3. Identify the donor commit, branch, or file from 26.1.2.
4. Fill the subsystem manifest below.
5. Choose the narrowest proof gate before editing.
6. Stop if the tree has unrelated dirt, the profile contains more than one active Latitude jar, or the donor/target relationship is unclear.

## Subsystem Manifest

Do not call a backport complete until every relevant row is explicitly marked included, excluded, already present, or blocked.

| Subsystem | Donor / evidence | Target status | Proof needed |
| --- | --- | --- | --- |
| `ProvinceAuthority` warm/province coherence | 26.1.2 canonical v1.4 | Must be checked for every older target | Exact-ID atlas metrics for confetti and arid leakage. |
| Equator dry-biome demotion | 26.1.2 plus 1.21.11-specific port drift | Check both directions; port may have behavior canonical lacks | Exact-ID atlas band metrics. |
| Cherry / sakura / pale garden gates | 26.1.2 and 1.21.11 beta docs | Must distinguish vanilla `cherry_grove` from Terralith sakura biomes | Atlas plus mod-present proof when Terralith is loaded. |
| Tree-line / alpine / snow caps | 26.1.2 canonical roadmap and 1.21.11 status docs | Needs canonical acceptance first | Headless/tall-terrain proof plus live visual when requested. |
| Custom-biome source wrapping and tags | 26.1.2 custom-biome expansion docs | Do not drag into a smaller Stage 1 worldgen port unless authorized | Mod-present atlas proof. |
| `/latdev` and proof commands | Port-specific code and cruise-control docs | Verify per target; keybinds and commands may drift | Command availability in live or branch-client proof. |
| HUD, overlays, warning timing, loading overlays | Client docs and port handoffs | Keep separate from worldgen backports unless scoped | Modrinth live proof. |
| Vanilla world-creation escape hatch | 26.2 issue #19 implementation (`CreateWorldScreenInitRedirectMixin`, `WorldCreatorMixin`, and `LatitudeCreateWorldScreen`) | Carry to every supported version; recheck each target's screen lifecycle, preset APIs, and modded world-type registration | For each target Minecraft/Latitude pair: create a non-Latitude modded world (including the reported One Dimension case when available), customize a non-default Superflat preset, enable a datapack and create a world, then create an ordinary Latitude world and confirm its bespoke flow is unchanged. |
| Release metadata and profile jar | `gradle.properties`, `fabric.mod.json`, Modrinth profile | Must match target MC version | Jar scan, active profile truth table, and build output path. |

## Mapping And API Rule

Do not assume cherry-picks will work across Minecraft lines.

- The 26.1.2 line uses official Mojang mappings in the known canonical worktree.
- Older targets use Yarn-era APIs.
- Pure logic such as constants, string biome IDs, smoothstep, and coherent noise can often port near-verbatim.
- Thin Minecraft API surfaces such as registries, biome holders, chunk hooks, mixin targets, keybinds, and refmaps must follow the target branch idioms.

If two attempts fail on one blocker, stop, document the blocker, and move to the next authorized target or ask Maintainer.

## Proof Gates

Use the narrowest proof that checks the claim.

| Claim | Minimum proof |
| --- | --- |
| The target compiles | Target-root compile with the target JDK/toolchain. |
| Biome distribution improved | Exact-ID atlas output plus band metrics, not natural-color screenshots alone. |
| A port is structurally complete | Subsystem manifest filled and all included systems have target proof. |
| A client/HUD/render behavior works | Modrinth-profile live proof, because dev clients can be misleading or undrivable. |
| A release jar is ready | Build output, jar path/hash, profile/version truth, and Maintainer-owned publication decision. |

Never flatten these into each other. A compile is not live proof. Atlas proof is not HUD proof. A GitHub tag is not a Modrinth release.

## Live/Profile Truth Table

Before any live or release-readiness check, record:

```text
Repo root:
Branch:
HEAD:
MC version:
Profile name:
Profile mods path:
Active Latitude jar(s):
Jar version/hash if relevant:
Surface type: development / candidate / public release
Proof target:
```

Stop if more than one active `latitude-*.jar` exists in the profile.

## Existing Evidence Pointers

- `README.md` - current current implementation stateer.
- `docs/porting/PORTING.md` - durable mistakes and future stop rules.
- `<external-notes>/canonical-26-1-2-decision-20260618.md` - canonical-line decision.
- `<external-notes>/1211-province-gap-and-structure-question-20260614.md` - 1.21.11 ProvinceAuthority gap.
- `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11/CODEX_CRUISE_CONTROL.md` - live Modrinth testing route for Latitude.
- `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/26.1.2-canonical-roadmap.md` - current canonical 26.1.2 gates.
- `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/porting/HANDOFF-1.4-beta-backport.md` - older Stage 1 backport plan; use as historical detail, not automatic current authorization.
