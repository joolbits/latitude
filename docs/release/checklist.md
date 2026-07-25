# Release checklist

> **Latitude 2.0 overhaul note (2026-07-02):** The future overhaul plan lives at
> `docs/LATITUDE_2_0_OVERHAUL.md`. That plan does not authorize release, upload, branch/tag push, or profile
> mutation. This release checklist remains the current 26.1.2 release/readiness gate.

## Latitude 1.4.1-beta.2+26.1.2 candidate readiness

Status: live-proof green, not release-authorized. The current savepoint is `c9da0f93029f7f16c50a7bc89eb766c576a85b48` / `save/biome-tuning-followup-26.1.2`. A fresh build from that savepoint has been staged into the `Lat 1.4+26.1.2` Modrinth profile as SHA `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477`, with manifest commit `c9da0f93029f7f16c50a7bc89eb766c576a85b48`. The 2026-06-21 final live attempt proves the c9 jar launches, loads an existing Globe world, accepts commands, reports a 20,000-block border, locates/renders real desert, survives a short non-teleport movement soak with no new warnings, and save/quits cleanly. A narrow stony follow-up then resolved the remaining caveat: fresh `/locate biome minecraft:stony_peaks` returned `[1452,170,4201]`, the settled HUD at that coordinate read `minecraft:stony_peaks`, and `/execute if biome ~ ~ ~ minecraft:stony_peaks run say stony_peaks_here` passed. Do not publish, push, upload, or public-release without the maintainer's separate explicit authorization.

Current requirement-level audit: `docs/release/current-readiness-audit.md`.

Machine-readable gate manifest: `docs/release/current-gates.json`.

Live proof route and final run record: `docs/release/live-proof-runbook.md`.

### Source-of-truth table

| Surface | Current truth |
| --- | --- |
| Canonical source root | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2` |
| Branch / candidate savepoint | `feat/custom-biome-expansion-26.1.2` / `c9da0f93029f7f16c50a7bc89eb766c576a85b48` |
| Candidate tag | `save/biome-tuning-followup-26.1.2` points at `c9da0f93029f7f16c50a7bc89eb766c576a85b48` |
| Working tree | release-doc/tool sync committed after the c9 candidate; generated proof/evidence folders remain local |
| Minecraft / mod version | `26.1.2` / `1.4.1-beta.2+26.1.2` |
| Latest local build jar | `build/libs/latitude-1.4.1-beta.2+26.1.2.jar` |
| Latest local build SHA-256 | `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477` (staged into the test profile on 2026-06-21) |
| Latest local sources SHA-256 | `b5d1c358098730fb565a611e89de04757a811e087bc2e11dbf146287f2a18efc` |
| Latest local build manifest provenance | `Git-Commit=c9da0f93029f7f16c50a7bc89eb766c576a85b48`, `Git-Branch=feat/custom-biome-expansion-26.1.2`, `Build-Dirty=true`, `Build-Time=2026-06-21T21:53:47Z` |
| Modrinth profile | `<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2` |
| Active profile jar after savepoint rebuild | `latitude-1.4.1-beta.2+26.1.2.jar`, SHA-256 `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477` |
| Active profile jar manifest provenance | `Git-Commit=c9da0f93029f7f16c50a7bc89eb766c576a85b48`, `Git-Branch=feat/custom-biome-expansion-26.1.2`, `Build-Dirty=true`, `Build-Time=2026-06-21T21:53:47Z` |
| Preserved prior profile jar | `latitude-1.4.1-beta.2+26.1.2.jar.pre-e09-stage-20260620-220852.bak`, SHA-256 `d51eace9e517db5e53c8754e581e44b49ef68a6778b0f367ee60c8eefa5df073` |
| Preserved biome-tuning profile jar | `latitude-1.4.1-beta.2+26.1.2.jar.pre-c9da0f93-20260621-175417`, SHA-256 `af1579b2e7f885ace1567e7400fd94cf0e958e160201edaccca020b2b1c6231c` |
| Current test profile video settings | `renderDistance=16`, `simulationDistance=8`; previous `32/12` options preserved as `options.txt.pre-e09-stage-20260620-220852.bak` |
| Public version name | undecided; likely `1.4.1+26.1.2` unless Maintainer chooses beta wording |

### Current candidate gates

- [x] PASS: Source-of-truth re-anchored to the canonical 26.1.2 root, branch, HEAD, tag, Minecraft version, mod version, candidate jar path, candidate jar SHA, profile path, and active profile jar SHA.
- [x] PASS: `./gradlew compileJava` with JDK 25 after the 2026-06-20 Itty render-load lifecycle fix (`tmp/itty-terrain-render-bug-20260620-072856/compileJava-after-lifecycle-fix.log`).
- [x] PASS: `./gradlew -PenableInvariantScan latitudeInvariantScan` on the final non-live refresh (`tmp/readiness-1.4-candidate-20260618-184901/latitudeInvariantScan-final-nonlive-refresh.log`).
- [x] PASS: `./gradlew -PenableInvariantScan clean build` on the final non-live refresh (`tmp/readiness-1.4-candidate-20260618-184901/clean-build-final-nonlive-refresh.log`).
- [x] PASS: Candidate jar embeds current source/build provenance in `META-INF/MANIFEST.MF` (`tmp/readiness-1.4-candidate-20260618-184901/final-nonlive-candidate-manifest.txt`).
- [x] PASS: Direct jar scan on the current rebuilt candidate found no packaged `com/example/globe/dev`, `LatitudeDevCommand`, `AutoCreateWorldProbe`, headless runner, tools/scripts, shell/binary/native payload, or `ProcessBuilder` marker; only the small `LatitudeClientState$AutoCreateWorldProbePhase` enum residue still ships (`tmp/readiness-1.4-candidate-20260618-184901/final-nonlive-candidate-purity-and-manifest.txt`, `tmp/readiness-1.4-candidate-20260618-184901/final-nonlive-candidate-jar-contents.txt`).
- [x] PASS: Tree-line/alpine structural proof (`tmp/readiness-1.4-candidate-20260618-184901/check-tree-line-port.log`).
- [x] PASS: Tree-line/alpine runtime and sweep proofs show forest below tree line, fade band, meadow shelf, alpine rock, then latitude-graded snow caps (`tmp/readiness-1.4-candidate-20260618-184901/treeline-alpine-runtime-proof-after-helper-fix.txt`, `tmp/readiness-1.4-candidate-20260618-184901/treeline-alpine-sweep-proof.txt`).
- [x] PASS: Mod-present headless atlas smoke with BoP, Terralith, and Promenade loaded produced 74 distinct biomes with expected cold/subpolar/tropical/sakura/pale-garden signals (`tmp/readiness-1.4-candidate-20260618-184901/mod-present-atlas.log`, `tmp/readiness-1.4-candidate-20260618-184901/mod-present-atlas-summary.txt`, `run-headless/tmp/readiness-1.4-candidate-20260618-184901/mod-present-atlas/.../world_biome_inventory.json`).
- [x] PASS: Rebuild and stage savepoint candidate SHA `1f50c5954cef3c91de1b071e78172ad6940a41abe5bace3e6febde5ac449a477` into `Lat 1.4+26.1.2` by filesystem copy, preserve the prior biome-tuning profile jar `af1579b2...` as a timestamped backup, and prove the active profile jar/manifest matches savepoint commit `c9da0f93...` (`tmp/closeout-1.4-20260621/rebuild-c9da0f93-175345`; `tmp/closeout-1.4-20260621/profile-stage-c9da0f93-175416`).
- [~] SUPERSEDED: Final live cruise was temporarily blocked by a windowless Modrinth process; Maintainer reopened Modrinth and the c9 candidate later launched. Historical evidence remains in `tmp/closeout-1.4-20260621/final-live-launch-blocker-181416`.
- [x] PASS: Final live cruise on staged SHA `1f50c595...` produced green launch/load/desert/soak/savequit evidence; the subsequent narrow stony follow-up produced green current-world stony locate/HUD/server-predicate evidence. Evidence: `tmp/closeout-1.4-20260621/final-live-c9-192419`; `tmp/closeout-1.4-20260621/stony-followup-c9-195009`.
- [x] PASS: Non-live live-control helper safety preflight: `tools/mc-window` now accepts only Java-owned game windows whose title starts with `Minecraft` and rejects launcher/Modrinth-shaped windows; `tools/mc-focus` and `tools/mc-chat` also refuse launcher windows; `tools/mc-shot` and `tools/mc-wait-shot` now fail fast when the macOS session is locked, instead of misclassifying exact-window capture failures as Minecraft-only control bugs. Shell syntax and the no-UI helper checks pass (`tmp/readiness-1.4-candidate-20260618-184901/live-control-helper-safety-20260618.log`, `tmp/atlas-worldsize-parity-20260619-081729/live/locked-session-test-repo.txt`). This is a control-path safety repair, not live command/control proof.
- [x] PASS-PARTIAL: Read-only readiness status helper now verifies staged profile/runtime SHA `1f50c595...`, savepoint `c9da0f93`, release docs, final c9 live evidence, and the green stony follow-up; it intentionally leaves manual publication/push gates open (`tools/lat-readiness-nonlive-status`).
- [x] PASS: Requirement-level readiness audit maps every P0/P1/P2 objective item to current evidence and records partial status while keeping public release/savepoint actions Maintainer-owned (`docs/release/current-readiness-audit.md`).
- [x] PASS: Live proof runbook records the authorized final route for fresh/existing load sanity, scenic/palm/decoration checks, non-teleport soak, and save/quit gates, including a hard prohibition on the vanilla Minecraft Launcher as a control surface (`docs/release/live-proof-runbook.md`).
- [x] HISTORICAL: Fresh `New Expedition` / first-load smoke was green on prior staged SHA `e09ea003...` for SMALL (`tmp/post-140-hardening-continuation-20260620-220852/live-e09-fresh-small-clean-log-lines.txt`, `live-e09-new-expedition-current.png`, `live-e09-new-expedition-after-210s-static.png`). This supports the closeout route but does not replace the pending c9/`1f50c595...` load sanity check.
- [x] HISTORICAL: Headless Itty Atlas diversity/no-collapse proof on prior staged SHA `e09ea003...` found 74 discovered biomes across Minecraft, BoP, Terralith, and Promenade (`tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621/headless-itty-step64-inventory-summary.txt`, `docs/binder/e09-itty-headless-atlas-live-lock-20260621.md`). The c9 closeout does not need another deep atlas investigation unless the final live cruise exposes a new symptom.
- [ ] REFRESH NEEDED: Repeatable post-1.4.0 findings classifier should be refreshed against active profile jar SHA `1f50c595...` before publication. Prior classifier proof on `e09ea003...` passed concrete source findings but kept live performance/visual proof open.
- [x] PASS: `/latdev` / proof-command policy resolved for the rebuilt candidate: release jars currently exclude `com.example.globe.dev.*` including `LatitudeDevCommand`, while `GlobeMod`/`GlobeModClient` tolerate the missing dev classes through reflective/gated registration. The shipping proof path is external/headless tooling plus live scenic evidence, not in-jar `/latdev` commands (`tmp/readiness-1.4-candidate-20260618-184901/candidate-direct-scan-after-overlay-fix.txt`, `src/main/java/com/example/globe/GlobeMod.java`, `src/main/java/com/example/globe/GlobeModClient.java`).
- [x] PASS: Scenic-drive delta, representative visual sanity, palm/fronds, and decoration checks ran on current candidate SHA `1f50c595...`; desert and tropics/decor visuals are supportive, and the current-world stony locate/HUD follow-up is green.
- [x] PASS-PARTIAL: Existing load sanity is green on candidate SHA `1f50c595...`: `New World` loaded with `isGlobe=true`, border/radius setup, first safe playable tick, and clean overlay close. Fresh new-world creation was not rerun.
- [x] PASS: Clean 20-second non-teleport movement soak on current candidate SHA `1f50c595...` produced zero new WARN/ERROR/crash lines.
- [x] PASS: Save/quit shutdown at the end of the final live cruise on candidate SHA `1f50c595...` logged singleplayer server stop, chunk saves, and all dimensions saved.
- [x] PASS: Local public copy/version drift fenced: `README`, `CHANGELOG`, root/canonical release checklists, beta release notes, and Modrinth description draft no longer present stale `1.21.11`/`1.3.0`/Sodium fog facts as current candidate truth. Public filename/version and final Modrinth/GitHub release text remain Maintainer-owned (`README.md`, `CHANGELOG.md`, `release/README-beta.txt`, `docs/release/modrinth-description-1.4.md`, `<home>/CascadeProjects/Latitude (Globe)/docs/release/checklist.md`).
- [ ] MANUAL PEETSA: savepoint/tag/push/release/upload authorization.

## Historical release records

The sections below are retained for prior public release history and do not define the current `1.4.1-beta.2+26.1.2` candidate gate above.

## Latitude 1.3.0+1.20.1-r1 hotfix

- [x] PASS: PR #6 merged into `port/1.3.0-1.20.1` at merge commit `8bcef6e131545576cda1a2cef2e90586f761cef0`.
- [x] PASS: Release tag `v1.3.0+1.20.1-r1` dereferences to `189054a10b1d718e074d13b1d85dbf0765490a63`.
- [x] PASS: Early-spawn-radius review thread on `GlobeMod.java` is resolved and outdated.
- [x] PASS: Hotfix proof gates passed: `git diff --check`, invariant scan, clean Gradle build, jar/refmap/purity scan, and manual Modrinth profile launch.
- [x] PASS: Runtime jar `latitude-1.3.0+1.20.1-r1.jar` declares MC `~1.20.1`, Java `>=17`, `globe.mixins.json`, `JAVA_17`, and `latitude-refmap.json`.
- [x] PASS: Desktop/runtime jar SHA-256 is `2e6cab07bc3c2820de1607de2b60c352c4a396176c123b97eb058ea48db5992b`.
- [x] PASS: Modrinth version `BPVweInp` is listed as `1.3.0+1.20.1-r1`.
- [x] PASS: GitHub issue #5 is closed as completed.

## Historical 26.1.x public-release record

This section records the earlier public `1.4.0+26.1.2` line. It is superseded for current readiness by the `1.4.1-beta.2+26.1.2` candidate section above.

- [~] DISCARDED (2026-06-06, Maintainer): "First-load message appears on NEW world only" — stale/obsolete item. The bespoke loading overlay intentionally shows on both new and existing Latitude saves. World-entry render-gate (overlay holds until render-ready) + latitude early-spawn landed & live-verified — tag `save/world-entry-render-gate-early-spawn`.
- [x] PASS: ./gradlew -PenableInvariantScan latitudeInvariantScan (2026-06-06 — after first-load-message discard `eec31d79` removed the stale DownloadingTerrain invariant lock; re-verified 2026-06-06 post-audit, ran not skipped, exit 0)
- [x] PASS: Clean build (`./gradlew clean build`) → BUILD SUCCESSFUL, `latitude-1.4.0+26.1.2.jar` (1.49 MB) + sources jar (2026-06-06 readiness audit).
- [x] PASS: Jar purity — no `com/example/globe/dev/`, `atlas/`, `ProcessBuilder`, or `HeadlessRunner` classes ship; `globe.mixins.json` + `fabric.mod.json` present; debug mixins limited to the two expected (`PlacedFeatureBopDebugMixin` inert/flag-gated, `ChunkRegionWarmSnowTrapMixin` required prod dep). Version `1.4.0+26.1.2`; depends loader ≥0.17.3 / mc ≥26.1.2 / java ≥25 / fabric-api.
- [x] PASS: Dev auto-create-world probe extracted out of the shipping `GlobeModClient` into the jar-excluded `com.example.globe.dev.AutoCreateWorldProbe` (`253ce798`, tag `save/globemodclient-probe-extraction`, pushed). `GlobeModClient.class` 71 KB → 13 KB; probe absent from the release jar. Client init + create-world path verified clean in `runClient` (no exceptions). RESIDUAL (minor, optional): `LatitudeClientState` still ships the small `AutoCreateWorldProbePhase` enum + probe-phase fields (state only, no dev tooling).
- [ ] PASS: No fog/EW/HUD render diffs in this release — **requires a manual interactive (real-GPU) client launch.** Not provable via automated `runClient`: in the offscreen/automated harness the world-entry render-gate's "sections compiled+visible" condition never trips, so the bespoke overlay holds until its 10-min `FAIL_SAFE_CLEAR_MS` backstop (looks like a freeze but is by-design; cleared normally at ~13s on a real GPU, verified at `508c3231`).
- [x] PASS: Release tag `v1.4.0+26.1.2` created + pushed (annotated, → commit `2deab50e`) 2026-06-06.
- [x] PASS: Pre-release tree hygiene (`2deab50e`) — governance/porting docs+tooling tracked; `Manual atlas/` + `.claude` local state ignored; server.properties churn restored.
- [x] PASS: GitHub release published — [v1.4.0+26.1.2](https://github.com/joolbits/latitude/releases/tag/v1.4.0%2B26.1.2) (not draft/prerelease), assets `latitude-1.4.0+26.1.2.jar` (1,460,266 B, **SHA-256 `8a01f69c99513ed0011924945a3df0a8c5f28515203ac8adc93e82079824e015`**) + sources jar; notes from CHANGELOG 1.4.
- [ ] PUBLISH (manual, Maintainer): Modrinth upload of the same `latitude-1.4.0+26.1.2.jar` (SHA above) + version listing; close any tracking issue.
- [ ] PASS (manual, Maintainer): real-GPU client launch — confirm no fog/EW/HUD render diffs (see note above; not automatable).

---

## Appendix: origin/main release-readiness front door (preserved for reference)

_The section below is `docs/release/checklist.md` as it existed on `origin/main` at the time of this merge —
a routing document that treated this checkout as a docs/history root pointing to a separate canonical
worktree (`Latitude-custom-biome-expansion-26.1.2`) for the same 1.4 candidate, with its own mirrored gate
table. Preserved here in full so every checklist item from main is kept (union of items, nothing dropped);
the candidate-readiness checklist above, maintained directly in this checkout, is the actively-used gate.
Note that some claims below (e.g. about this checkout's `gradle.properties` still being on the historical
`1.21.11`/`1.3.0` line) predate the 26.2 pivot and no longer reflect this checkout's current state — retained
verbatim as a historical record of main's last release-readiness pass, not as current fact._

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
