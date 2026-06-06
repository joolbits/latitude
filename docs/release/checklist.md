# Release checklist

## Latitude 1.3.0+1.20.1-r1 hotfix

- [x] PASS: PR #6 merged into `port/1.3.0-1.20.1` at merge commit `8bcef6e131545576cda1a2cef2e90586f761cef0`.
- [x] PASS: Release tag `v1.3.0+1.20.1-r1` dereferences to `189054a10b1d718e074d13b1d85dbf0765490a63`.
- [x] PASS: Early-spawn-radius review thread on `GlobeMod.java` is resolved and outdated.
- [x] PASS: Hotfix proof gates passed: `git diff --check`, invariant scan, clean Gradle build, jar/refmap/purity scan, and manual Modrinth profile launch.
- [x] PASS: Runtime jar `latitude-1.3.0+1.20.1-r1.jar` declares MC `~1.20.1`, Java `>=17`, `globe.mixins.json`, `JAVA_17`, and `latitude-refmap.json`.
- [x] PASS: Desktop/runtime jar SHA-256 is `2e6cab07bc3c2820de1607de2b60c352c4a396176c123b97eb058ea48db5992b`.
- [x] PASS: Modrinth version `BPVweInp` is listed as `1.3.0+1.20.1-r1`.
- [x] PASS: GitHub issue #5 is closed as completed.

## Current 26.1.x line

- [~] DISCARDED (2026-06-06, Maintainer): "First-load message appears on NEW world only" — stale/obsolete item. The bespoke loading overlay intentionally shows on both new and existing Latitude saves. World-entry render-gate (overlay holds until render-ready) + latitude early-spawn landed & live-verified — tag `save/world-entry-render-gate-early-spawn`.
- [x] PASS: ./gradlew -PenableInvariantScan latitudeInvariantScan (2026-06-06 — after first-load-message discard `eec31d79` removed the stale DownloadingTerrain invariant lock; re-verified 2026-06-06 post-audit, ran not skipped, exit 0)
- [x] PASS: Clean build (`./gradlew clean build`) → BUILD SUCCESSFUL, `latitude-1.4.0+26.1.2.jar` (1.49 MB) + sources jar (2026-06-06 readiness audit).
- [x] PASS: Jar purity — no `com/example/globe/dev/`, `atlas/`, `ProcessBuilder`, or `HeadlessRunner` classes ship; `globe.mixins.json` + `fabric.mod.json` present; debug mixins limited to the two expected (`PlacedFeatureBopDebugMixin` inert/flag-gated, `ChunkRegionWarmSnowTrapMixin` required prod dep). Version `1.4.0+26.1.2`; depends loader ≥0.17.3 / mc ≥26.1.2 / java ≥25 / fabric-api.
- [x] PASS: Dev auto-create-world probe extracted out of the shipping `GlobeModClient` into the jar-excluded `com.example.globe.dev.AutoCreateWorldProbe` (`253ce798`, tag `save/globemodclient-probe-extraction`, pushed). `GlobeModClient.class` 71 KB → 13 KB; probe absent from the release jar. Client init + create-world path verified clean in `runClient` (no exceptions). RESIDUAL (minor, optional): `LatitudeClientState` still ships the small `AutoCreateWorldProbePhase` enum + probe-phase fields (state only, no dev tooling).
- [ ] PASS: No fog/EW/HUD render diffs in this release — **requires a manual interactive (real-GPU) client launch.** Not provable via automated `runClient`: in the offscreen/automated harness the world-entry render-gate's "sections compiled+visible" condition never trips, so the bespoke overlay holds until its 10-min `FAIL_SAFE_CLEAR_MS` backstop (looks like a freeze but is by-design; cleared normally at ~13s on a real GPU, verified at `508c3231`).
- [x] PASS: Release tag `v1.4.0+26.1.2` created + pushed (annotated, → commit `2deab50e`) 2026-06-06.
- [x] PASS: Pre-release tree hygiene (`2deab50e`) — governance/porting docs+tooling tracked; `Manual atlas/` + `.claude` local state ignored; server.properties churn restored.
- [ ] PUBLISH (manual, Maintainer): GitHub release + Modrinth upload of `build/libs/latitude-1.4.0+26.1.2.jar` (1,460,266 bytes, **SHA-256 `8a01f69c99513ed0011924945a3df0a8c5f28515203ac8adc93e82079824e015`**, built from `2deab50e`), Modrinth version listing, close any tracking issue. Mirror the 1.3.0 checklist pattern.
- [ ] PASS (manual, Maintainer): real-GPU client launch — confirm no fog/EW/HUD render diffs (see note above; not automatable).
