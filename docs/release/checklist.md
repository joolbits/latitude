# Release checklist

`scope: Latitude release-readiness front door` · `status: partial proof, release not authorized` · `updated: 2026-06-21`

This file is the permanent rerun entrypoint for Latitude release-readiness. It is not the candidate ledger itself. Use it to route each future pass to the right source-of-truth checklist, keep the candidate jar identity explicit, and avoid mixing old 1.21.11 surfaces with the canonical 26.1.2 line.

## Current release truth

| Surface | Current truth |
| --- | --- |
| Canonical Latitude 1.4 line | Minecraft `26.1.2` |
| Canonical source root | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2` |
| Canonical branch / HEAD | `feat/custom-biome-expansion-26.1.2` / `e5d092ca7f09a397afc413137f62ea409566e1e7` |
| This checkout's `gradle.properties` | Historical `1.21.11` / `1.3.0` metadata only; not the active 26.1.2 candidate source |
| Latest local build jar | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/build/libs/latitude-1.4.1-beta.2+26.1.2.jar` |
| Latest local build SHA-256 | `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` (staged into the test profile on 2026-06-20) |
| Profile jar | `<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2/mods/latitude-1.4.1-beta.2+26.1.2.jar` |
| Profile jar SHA-256 | `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` |
| Profile proof settings | `renderDistance=16`, `simulationDistance=8`; prior `32/12` saved as a timestamped options backup |
| Public version name | Undecided; Maintainer-owned release decision |
| Canonical candidate ledger | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/checklist.md` |
| Current readiness audit | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/current-readiness-audit.md` |
| Machine-readable gate manifest | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/current-gates.json` |
| Live proof runbook / final live run record | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/live-proof-runbook.md` |
| Current blocker/fix evidence folder | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/tmp/post-140-hardening-continuation-20260620-220852` |
| Current Itty headless/live-lock continuation | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621` |
| Live scenic proof checklist | `docs/release/scenic-drive-green-checklist.md` |

Maintainer-owned publication gates remain separate from proof gates.

## Future rerun order

1. Re-anchor both the current root and the canonical 26.1.2 root before any proof or edits.
2. Record the candidate jar SHA, manifest provenance, profile path, and active profile jar truth.
3. Refresh the canonical non-live checklist first:
   - compile/build proof
   - invariant scan
   - jar purity and manifest provenance
   - `/latdev` packaging policy
   - profile staging/hash proof
4. If live proof is authorized, run the scenic live checklist against that exact jar SHA.
5. Review public copy/version drift only after the candidate SHA and live proof target are settled.
6. Stop before savepoint, tag, push, upload, or publication unless Maintainer explicitly authorizes that separate lane.

## Permanent gate map

| Gate | Canonical doc | GREEN means |
| --- | --- | --- |
| Candidate truth table | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/checklist.md` | Root, branch, HEAD, jar SHA, manifest, profile, and world/proof target all match. |
| Non-live build and purity | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/checklist.md` | Compile/build/invariant scan pass, jar provenance is current, and the release jar packaging is understood. |
| Live scenic pass | `docs/release/scenic-drive-green-checklist.md` | Control, world identity, EW haze/fog, borders, biome cohesion, decoration, HUD, persistence, performance, and evidence are all green. |
| Public copy/version drift | this file + `README.md` + `CHANGELOG.md` | Repo-facing status text does not misstate the active canonical line or candidate state. |
| Publication | Maintainer decision | Savepoint/tag/push/upload/release work is explicitly authorized and executed. |

## Current gate status

This table mirrors the current canonical checklist status so scenic proof cannot substitute for release readiness.

| Gate | Current status | Evidence / blocker |
| --- | --- | --- |
| Source-of-truth table | GREEN | Candidate/root/profile truth is recorded above and in `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/docs/release/checklist.md`. |
| Globe root build metadata guard | GREEN | This checkout's `gradle.properties` now explicitly labels its `1.21.11` / `1.3.0` values as historical and points to the canonical 26.1.2 source/checklist. |
| Non-live build, invariant scan, jar provenance, and jar purity | GREEN for current candidate/profile | Canonical checklist records code-red hardening proof and current staged SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`. |
| Profile staging/hash proof | GREEN | Canonical checklist records filesystem-only staging into `Lat 1.4+26.1.2`; active profile/runtime jar SHA is `e09ea003...`. The prior `d51eace9...` jar is preserved as a non-loadable backup. |
| `/latdev` packaging policy | GREEN | Release candidate intentionally excludes in-jar dev commands; proof path is external/headless tooling plus authorized live evidence. |
| Live-control helper safety | GREEN for non-live helper preflight only | Canonical checklist records launcher/Modrinth window rejection and no-UI helper self-test. This does not prove live command/control. |
| Read-only readiness status helper | GREEN for partial state | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2/tools/lat-readiness-nonlive-status` verifies source/profile/provenance evidence, warns when mutable `build/libs` differs from the staged profile jar, and reports partial status while live relaunch/scenic/soak proof remains open. |
| Requirement-level readiness audit | PARTIAL | Canonical audit maps the 2026-06-20 Itty render/load regression and current `e09ea003...` evidence: SMALL fresh smoke, save/quit, headless Itty Atlas diversity, and post-1.4.0 source/jar classifier are green, while live Itty/scenic/performance proof is still partial/open. |
| Machine-readable gate manifest | PARTIAL | Canonical manifest records release readiness as `partial`, release authorization as false, candidate/profile SHA `e09ea003...`, fresh SMALL smoke and shutdown green, headless Itty diversity and post-1.4.0 source classifier green-nonlive, and live Itty/scenic/performance gates partial/open. |
| Live proof runbook | HISTORICAL GREEN for SHA `972159d1...`; PARTIAL for SHA `e09ea003...` | The current `e09ea003...` SMALL first-load, save/quit, and headless Itty Atlas proof is recorded; Itty-specific live load/terrain, scenic, and movement-soak proof remains open. |
| Fresh `New Expedition` first-load smoke | GREEN for current-SHA SMALL; Itty still open | Exact Java-window proof and lifecycle log show `size=SMALL`, bespoke overlay first render, `first safe playable tick`, normal overlay clear, and rendered terrain screenshots. The world name said Itty, but the lifecycle log did not. |
| Itty headless Atlas diversity/no-collapse | GREEN for non-live Atlas; live proof still open | Seed `220220260619002`, radius `3750`, step `64`, 74 discovered biomes across Minecraft, BoP, Terralith, and Promenade with all five placement bands represented. This does not close live chunk loading, rendering, palm visuals, scenic delta, or performance. |
| Post-1.4.0 source findings classifier | GREEN for source/profile jar; live proof still open | `tools/lat-post140-findings-classifier` returns `PASS-PARTIAL` with PASS for custom-biome feature retention, optional tag pools, equatorial anti-arid safeguards, and polar ice-spike cap/snowy fallback in current source/profile jar. It remains partial because live performance/visual gates are separate. |
| Scenic-drive rerun/delta | OPEN on current SHA | Must be rerun on current profile jar SHA `e09ea003...` after report findings are accepted as fixed/deferred. |
| Itty-specific load/terrain proof | OPEN on current SHA | Must be rerun on current profile jar SHA `e09ea003...`; the current fresh smoke was SMALL despite the world name, and the 2026-06-21 live continuation hit a locked-session gate before gameplay proof could safely continue. |
| Non-teleport movement soak/performance | PARTIAL on current SHA | Current SMALL run reached playable terrain and later went quiet, but logged three early two-second `Can't keep up` warnings under high system pressure with a second Java client running. |
| Save/quit shutdown | GREEN for current-SHA SMALL | Current `e09ea003...` SMALL run returned to title; latest log records all dimensions saved and crash-report scan found no new crash after proof. |
| Public copy/version drift | GREEN for local copy; publication text still manual | Local README/CHANGELOG/checklist/beta-note/Modrinth-description drift is fenced. Public version name, filename, and final Modrinth/GitHub text remain Maintainer-owned publication work. |
| Savepoint/tag/push/upload/release | MANUAL PEETSA | Not authorized in this checklist pass. |

## Current durable split

- Use the canonical 26.1.2 checklist as the active candidate ledger.
- Use the scenic checklist for future live reruns and visual release-readiness.
- Treat old one-line release bullets such as "first-load message appears on NEW world only" as historical only; they are no longer sufficient release gates.
