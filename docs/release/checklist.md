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
- [x] PASS: ./gradlew -PenableInvariantScan latitudeInvariantScan (2026-06-06 — after first-load-message discard `eec31d79` removed the stale DownloadingTerrain invariant lock)
- [ ] PASS: No fog/EW/HUD render diffs in this release
