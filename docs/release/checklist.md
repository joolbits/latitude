# Release checklist

> Historical 1.4 checklist. It does not describe the active 1.5.1 Beta 1 candidate. See
> [`beta1-checklist.md`](./beta1-checklist.md) for the current local release gates.

## Standing policy (all versions)

Artifact content is governed by [`artifact-content-policy.md`](./artifact-content-policy.md), established
2026-08-04 by owner directive: public release artifacts ship operator-usable Latitude commands and never
ship recording, sentinel, or auto-harness work. That policy's enforcement points and per-release
verification apply to every candidate below, and supersede the 1.4-era `/latdev` line in the
"Current candidate gates" section.

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
- [x] PASS *(SUPERSEDED 2026-08-04 by [`artifact-content-policy.md`](./artifact-content-policy.md): release artifacts now ship the permitted operator-query subset of `/latdev` from `com.example.globe.tools`, while recording, sentinel, and auto-harness work stays excluded. The record below stands as the rule the 1.4 line shipped under.)*: `/latdev` / proof-command policy resolved for the rebuilt candidate: release jars currently exclude `com.example.globe.dev.*` including `LatitudeDevCommand`, while `GlobeMod`/`GlobeModClient` tolerate the missing dev classes through reflective/gated registration. The shipping proof path is external/headless tooling plus live scenic evidence, not in-jar `/latdev` commands (`tmp/readiness-1.4-candidate-20260618-184901/candidate-direct-scan-after-overlay-fix.txt`, `src/main/java/com/example/globe/GlobeMod.java`, `src/main/java/com/example/globe/GlobeModClient.java`).
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
