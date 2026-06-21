# Latitude 1.4 Current Readiness Audit

`status: partial` · `updated: 2026-06-21` · `candidate: 1.4.1-beta.2+26.1.2` · `sha256: e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`

This audit maps the release-readiness objective to current evidence. It is not a release authorization. A chain of 2026-06-20 Itty existing-save render/load, spawn-prep, high-column terrain, palm-rendering, and performance/shutdown findings invalidated the previous `pre-release-ready` state for SHA `972159d1c825dff5803ff1e56eaa881a40c470c4a4e9f9640a06680856545b84`. The active Modrinth profile/runtime jar has been rebuilt and restaged to SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`; the prior live-tested `d51eace9...` jar is preserved as a non-loadable backup. Current-SHA fresh SMALL smoke and save/quit shutdown proof are green. Current-SHA headless Itty Atlas diversity evidence is also green for seed `220220260619002`. the maintainer's 2026-06-21 manual retest reports the existing-world loading screen and chunk loading are now green on the staged Modrinth profile. Scenic delta, direct palm-canopy visual proof, broader decoration/biome visual review, and a clean single-client movement soak remain open.

## Source Of Truth

| Field | Current value |
| --- | --- |
| Canonical source root | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2` |
| Canonical branch / HEAD | `feat/custom-biome-expansion-26.1.2` / `e5d092ca7f09a397afc413137f62ea409566e1e7` |
| Tag at HEAD | `save/clifftree-expansion-26.1.2` |
| Latest local build jar | `build/libs/latitude-1.4.1-beta.2+26.1.2.jar` |
| Latest local build SHA-256 | `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` (staged) |
| Profile jar | `<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2/mods/latitude-1.4.1-beta.2+26.1.2.jar` |
| Profile jar SHA-256 | `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` |
| Profile settings for current live retest | `renderDistance=16`, `simulationDistance=8`; previous `32/12` preserved in a timestamped options backup |
| Public version name | Undecided; Maintainer-owned release decision |
| Globe root build metadata | Historical `1.21.11` / `1.3.0` only, guarded in `<home>/CascadeProjects/Latitude (Globe)/gradle.properties` |
| Machine-readable gate manifest | `docs/release/current-gates.json` |
| Live proof runbook | `docs/release/live-proof-runbook.md` |
| Current blocker/fix evidence folder | `tmp/post-140-hardening-continuation-20260620-220852` |

## Requirement Matrix

| Objective item | Status | Evidence | Remaining work |
| --- | --- | --- | --- |
| P0: Reconcile release source of truth | GREEN | `docs/release/checklist.md`; `<home>/CascadeProjects/Latitude (Globe)/docs/release/checklist.md`; `tools/lat-readiness-nonlive-status`; `tmp/readiness-1.4-candidate-20260618-184901/globe-gradle-historical-guard-validation-20260618.log` | None for local source-of-truth routing. |
| P0: Close actual release checklist | GREEN for pre-release proof; MANUAL for publication/savepoint | `docs/release/checklist.md`; `<home>/CascadeProjects/Latitude (Globe)/docs/release/checklist.md`; `tools/lat-readiness-nonlive-status`; `tmp/readiness-1.4-candidate-20260618-live-20260618-211834/final-readiness-status-validation-20260618.log` | None for proof readiness. Maintainer-owned savepoint/tag/push/upload/release decisions remain separate. |
| P1: Build final candidate from canonical 26.1.2 source and prove it | GREEN for source/build/profile stage | `tmp/readiness-1.4-candidate-20260618-184901/compileJava-final-nonlive-refresh.log`; `clean-build-final-nonlive-refresh.log`; `latitudeInvariantScan-final-nonlive-refresh.log`; fresh verification build/profile SHA `e09ea003...`; `tmp/post-140-hardening-continuation-20260620-220852/profile-jar-after-e09-stage.sha256` | Live proof target is now profile SHA `e09ea003...`. |
| P1: Fix or document jar provenance | GREEN | `tmp/readiness-1.4-candidate-20260618-184901/final-nonlive-candidate-manifest.txt`; `tools/lat-readiness-nonlive-status` checks local-build and profile manifests for `Git-Commit`, `Git-Branch`, `Build-Dirty`, Minecraft version, and implementation version | None for non-live provenance. |
| P1: Run fresh first-load smoke | GREEN for current-SHA SMALL; GREEN by Maintainer manual retest for existing-world load/chunk entry | `tmp/post-140-hardening-continuation-20260620-220852/live-e09-fresh-small-clean-log-lines.txt`; `live-e09-new-expedition-current.png`; `live-e09-new-expedition-after-210s-static.png`; lifecycle log says `size=SMALL`, `first safe playable tick`, then overlay clear; Maintainer manual report on 2026-06-21 says loading screen and chunk loading are green in the staged Modrinth profile | Keep the Maintainer manual result scoped to loading/chunk entry. It does not close scenic, palm, decoration, or performance gates. |
| P1/P2: Itty headless Atlas diversity | GREEN for non-live Atlas; GREEN by Maintainer manual retest for live chunk loading | `tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621/headless-itty-step64-inventory-summary.txt`; `docs/binder/e09-itty-headless-atlas-live-lock-20260621.md`; seed `220220260619002`, radius `3750`, step `64`, 74 biomes, providers `minecraft:43`, `biomesoplenty:17`, `terralith:11`, `promenade:3`, all five placement bands represented; Maintainer manual report on 2026-06-21 says chunk loading is green | Atlas answers the diversity/no-collapse question; the maintainer's manual retest closes the live chunk-loading symptom. Palm visuals, scenic delta, broader decoration/biome visual review, and performance remain separate. |
| P1/P2: Post-1.4.0 report source findings | GREEN for concrete source/jar findings; PARTIAL overall | `tools/lat-post140-findings-classifier`; `tmp/post-140-hardening-continuation-20260620-220852/findings-classifier-20260621/post140-findings-classifier-current.txt`; classifier passes custom-biome feature retention, optional tag pools, equatorial anti-arid safeguards, and polar ice-spike cap/snowy fallback in current source and profile jar | The pasted report's concrete source findings are closed for the active candidate. Performance and visual/live gates remain separate and partial/open. |
| P1: Resolve `/latdev` / proof-command policy | GREEN | `docs/release/checklist.md`; `tmp/readiness-1.4-candidate-20260618-184901/candidate-direct-scan-after-overlay-fix.txt`; `src/main/java/com/example/globe/GlobeMod.java`; `src/main/java/com/example/globe/GlobeModClient.java` | None unless Maintainer wants a separate proof-build dev-command lane. |
| P1/P2: Resolve public copy/version drift | GREEN for local copy; manual for publication | `README.md`; `CHANGELOG.md`; `release/README-beta.txt`; `docs/release/modrinth-description-1.4.md`; `<home>/CascadeProjects/Latitude (Globe)/docs/release/checklist.md` | Maintainer chooses final public version name, filename, Modrinth/GitHub copy, and publication timing. |
| P2: Scenic delta on current SHA | OPEN on current SHA | Prior green proof was on superseded SHA `972159d1...`; current profile SHA is `e09ea003...`. | Rerun representative scenic delta after report findings are accepted as fixed/deferred. |
| P2: Clean non-teleport soak/perf pass | PARTIAL on current SHA | `tmp/post-140-hardening-continuation-20260620-220852/live-e09-log-review-after-static-soak.txt`; `live-e09-thread-dump-after-static-soak.txt`; `live-e09-vm-stat-after-static-soak.txt`; current SMALL run logged three early 2.0-2.7s `Can't keep up` warnings, then no new log lines during the later static wait; machine pressure and a second Java client contaminate the result | Rerun normal movement/static soak in a clean single-client profile; if still red, benchmark/trace `surfaceDecisionY`/`previewHeight` and feature decoration cost before product changes. |
| P2: Save/quit shutdown | GREEN for current-SHA SMALL | `tmp/post-140-hardening-continuation-20260620-220852/live-e09-savequit-log-final.txt`; `live-e09-crash-reports-after-savequit.txt`; `live-e09-after-savequit-final.png` | Keep this green for the current SMALL run; rerun if a later perf/scenic proof mutates product code or reproduces shutdown delay. |

## Current Verdict

Latitude 1.4 is **partial, not pre-release-ready** as of 2026-06-21. The Itty existing-save render/load issue showed the staged SHA `972159d1c825dff5803ff1e56eaa881a40c470c4a4e9f9640a06680856545b84` could clear the Latitude loading state before the render gate reached `first safe playable tick`. The current profile/runtime jar SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` has compile/build/profile/bytecode proof plus fresh SMALL live smoke and save/quit proof. Headless Itty Atlas evidence on the same SHA does not show a one-biome or one-provider diversity collapse for seed `220220260619002`, `tools/lat-post140-findings-classifier` repeats the source/jar checks for the concrete pasted-report findings, and the maintainer's 2026-06-21 manual retest closes the loading-screen/chunk-loading symptom on the staged Modrinth profile. Scenic delta, direct palm-canopy visual proof, broader decoration/biome visual review, and a clean single-client movement soak remain open.

This is still **not a publication authorization**. Savepoint, tag, push, upload, public release, final public version name, final public filename, and final public release text remain Maintainer-owned release actions.

## Safe Next Slice

Recommended next slice: use the staged profile jar SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` for representative scenic delta, direct palm-canopy visual proof if available, broader decoration/biome visual review, and a clean single-client movement soak. Savepoint, tag, push, upload, and publication remain blocked until those checks are green or explicitly deferred by Maintainer.
