# Release checklist

## Latitude 1.4.1-beta.2+26.1.2 candidate readiness

Status: partial, not release-authorized. A chain of 2026-06-20 Itty existing-save render/load, spawn-prep, high-column terrain, palm-rendering, and performance/shutdown findings invalidated the prior pre-release-ready state for candidate SHA `972159d1c825dff5803ff1e56eaa881a40c470c4a4e9f9640a06680856545b84`. The current staged profile/runtime jar SHA is `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`; the prior `d51eace9...` runtime jar is preserved as a non-loadable backup. The current `e09ea003...` live run proved exact Java-window launch, fresh SMALL `New Expedition` entry, render-gated overlay order, playable terrain, and save/quit shutdown. The 2026-06-21 continuation added headless Itty Atlas diversity proof for the same SHA, and the maintainer's 2026-06-21 manual retest reports the loading screen and chunk loading are now green in the staged Modrinth profile. The checklist still does not prove scenic delta, direct palm-canopy visuals, broader decoration/biome visual review, or a clean single-client movement soak. Do not publish, tag, push, upload, or public-release without the maintainer's separate explicit authorization.

Current requirement-level audit: `docs/release/current-readiness-audit.md`.

Machine-readable gate manifest: `docs/release/current-gates.json`.

Live proof route and final run record: `docs/release/live-proof-runbook.md`.

### Source-of-truth table

| Surface | Current truth |
| --- | --- |
| Canonical source root | `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2` |
| Branch / HEAD | `feat/custom-biome-expansion-26.1.2` / `e5d092ca7f09a397afc413137f62ea409566e1e7` |
| Tag at HEAD | `save/clifftree-expansion-26.1.2` |
| Working tree | dirty candidate/proof state; no savepoint yet |
| Minecraft / mod version | `26.1.2` / `1.4.1-beta.2+26.1.2` |
| Latest local build jar | `build/libs/latitude-1.4.1-beta.2+26.1.2.jar` |
| Latest local build SHA-256 | `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` (staged into the test profile on 2026-06-20) |
| Latest local sources SHA-256 | `b5d1c358098730fb565a611e89de04757a811e087bc2e11dbf146287f2a18efc` |
| Latest local build manifest provenance | `Git-Commit=e5d092ca7f09a397afc413137f62ea409566e1e7`, `Git-Branch=feat/custom-biome-expansion-26.1.2`, `Build-Dirty=true`, `Build-Time=2026-06-21T01:59:22Z` |
| Modrinth profile | `<home>/Library/Application Support/ModrinthApp/profiles/Lat 1.4+26.1.2` |
| Active profile jar after code-red hardening | `latitude-1.4.1-beta.2+26.1.2.jar`, SHA-256 `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` |
| Active profile jar manifest provenance | `Git-Commit=e5d092ca7f09a397afc413137f62ea409566e1e7`, `Git-Branch=feat/custom-biome-expansion-26.1.2`, `Build-Dirty=true`, `Build-Time=2026-06-21T01:59:22Z` |
| Preserved prior profile jar | `latitude-1.4.1-beta.2+26.1.2.jar.pre-e09-stage-20260620-220852.bak`, SHA-256 `d51eace9e517db5e53c8754e581e44b49ef68a6778b0f367ee60c8eefa5df073` |
| Current test profile video settings | `renderDistance=16`, `simulationDistance=8`; previous `32/12` options preserved as `options.txt.pre-e09-stage-20260620-220852.bak` |
| Public version name | undecided; do not publish as `1.4.0`, `1.4.1`, or beta without the maintainer's explicit release decision |

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
- [x] PASS: Stage rebuilt candidate SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` into `Lat 1.4+26.1.2` by filesystem copy, preserve the prior `d51eace9...` profile jar as a non-loadable `.bak`, lower the local test profile from render/simulation `32/12` to `16/8`, and prove the active profile jar/manifest/settings match the cleaner proof target (`tmp/post-140-hardening-continuation-20260620-220852`).
- [x] PASS: Non-live live-control helper safety preflight: `tools/mc-window` now accepts only Java-owned game windows whose title starts with `Minecraft` and rejects launcher/Modrinth-shaped windows; `tools/mc-focus` and `tools/mc-chat` also refuse launcher windows; `tools/mc-shot` and `tools/mc-wait-shot` now fail fast when the macOS session is locked, instead of misclassifying exact-window capture failures as Minecraft-only control bugs. Shell syntax and the no-UI helper checks pass (`tmp/readiness-1.4-candidate-20260618-184901/live-control-helper-safety-20260618.log`, `tmp/atlas-worldsize-parity-20260619-081729/live/locked-session-test-repo.txt`). This is a control-path safety repair, not live command/control proof.
- [x] PASS: Read-only readiness status helper: `tools/lat-readiness-nonlive-status` now verifies current staged profile/runtime SHA `e09ea003...`, records whether mutable `build/libs` matches the staged profile jar, checks both local-build and profile manifests, and explicitly warns that fresh-world/scenic/performance/shutdown proof remains open.
- [x] PASS: Requirement-level readiness audit maps every P0/P1/P2 objective item to current evidence and records partial status while keeping public release/savepoint actions Maintainer-owned (`docs/release/current-readiness-audit.md`).
- [x] PASS: Live proof runbook records the authorized completed route for fresh first-load, scenic delta, and non-teleport soak gates, including a hard prohibition on the vanilla Minecraft Launcher as a control surface (`docs/release/live-proof-runbook.md`).
- [x] PASS: Fresh `New Expedition` / first-load smoke on current candidate SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458` is green for SMALL: exact Java-window proof, `globe 1.4.1-beta.2+26.1.2` log, `size=SMALL`, bespoke overlay first render, `first safe playable tick`, normal overlay clear, and rendered terrain screenshots (`tmp/post-140-hardening-continuation-20260620-220852/live-e09-fresh-small-clean-log-lines.txt`, `live-e09-new-expedition-current.png`, `live-e09-new-expedition-after-210s-static.png`). The world name included `Itty`, but the lifecycle log says SMALL; do not count this as Itty proof.
- [x] PASS: Headless Itty Atlas diversity/no-collapse proof on current candidate SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`: seed `220220260619002`, radius `3750`, `step64`, 74 discovered biomes across `minecraft:43`, `biomesoplenty:17`, `terralith:11`, `promenade:3`, with all five placement bands represented and temperate shoulder rows showing no warm-dry leakage in the sampled window (`tmp/post-140-hardening-continuation-20260620-220852/live-continue-20260621/headless-itty-step64-inventory-summary.txt`, `docs/binder/e09-itty-headless-atlas-live-lock-20260621.md`). This is non-live Atlas evidence for diversity/no-collapse; the maintainer's 2026-06-21 manual retest separately reports live chunk loading green. Palm visuals, scenic delta, broader decoration/biome visual review, and performance remain separate.
- [x] PASS: Repeatable post-1.4.0 findings classifier on current candidate SHA `e09ea00313307ae84f8e32a0470db523874fe11d8314d133ddadfe9d85bf2458`: `tools/lat-post140-findings-classifier` proves the four concrete pasted-report source findings are fixed/present in source and active profile jar: `ChunkGeneratorGenerateFeaturesBiomeSetMixin`, optional tag pools, equatorial anti-arid safeguards, and polar ice-spike cap/snowy fallback. The classifier returns `PASS-PARTIAL` because live performance/visual proof remains open (`tmp/post-140-hardening-continuation-20260620-220852/findings-classifier-20260621/post140-findings-classifier-current.txt`).
- [x] PASS: `/latdev` / proof-command policy resolved for the rebuilt candidate: release jars currently exclude `com.example.globe.dev.*` including `LatitudeDevCommand`, while `GlobeMod`/`GlobeModClient` tolerate the missing dev classes through reflective/gated registration. The shipping proof path is external/headless tooling plus live scenic evidence, not in-jar `/latdev` commands (`tmp/readiness-1.4-candidate-20260618-184901/candidate-direct-scan-after-overlay-fix.txt`, `src/main/java/com/example/globe/GlobeMod.java`, `src/main/java/com/example/globe/GlobeModClient.java`).
- [ ] OPEN: Scenic-drive delta and representative visual sanity must be rerun on current candidate SHA `e09ea003...` after the code-red report findings are accepted as fixed/deferred.
- [x] PASS (manual Maintainer): Loading screen and chunk loading are green in the staged `Lat 1.4+26.1.2` Modrinth profile on current candidate SHA `e09ea003...` as of 2026-06-21. This closes the loading/chunk symptom for the commit boundary; it does not close scenic, palm, decoration/biome visual, or performance gates.
- [ ] PARTIAL: Clean non-teleport live movement soak/performance on current candidate SHA `e09ea003...`: current SMALL run reached playable terrain and later went quiet, but logged three early two-second `Can't keep up` warnings, under high machine pressure with a second Java client running. Thread sample points at the current `surfaceDecisionY`/`previewHeight` terrain-authority path rather than the older `previewTerrain` recursion (`tmp/post-140-hardening-continuation-20260620-220852/live-e09-thread-dump-after-static-soak.txt`, `live-e09-vm-stat-after-static-soak.txt`, `live-e09-top-after-static-soak.txt`).
- [x] PASS: Save/quit shutdown on current candidate SHA `e09ea003...` for the fresh SMALL run: latest log shows `Stopping server`, `Saving worlds`, and all dimensions saved; crash-report scan found no new crash after the proof (`tmp/post-140-hardening-continuation-20260620-220852/live-e09-savequit-log-final.txt`, `live-e09-crash-reports-after-savequit.txt`, `live-e09-after-savequit-final.png`).
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
