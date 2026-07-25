# Architecture fix history

## 2026-06-04 - 1.20.1 refmap startup hotfix closed
- Symptom: the public MC 1.20.1 build could fail at startup or world launch from mixin/refmap/runtime-surface drift.
- Root cause: the 1.20.1 release path needed `globe.mixins.json` aligned to the generated `latitude-refmap.json`; the post-merge launch pass also found Java 17 mixin compatibility and excluded `WarmSnowTrapStats` debug dependency issues in the release jar.
- Fix: PR #6 corrected the refmap name, the review fix derived early spawn radius from the new world's generator, and the release branch changed mixin compatibility to `JAVA_17` while removing the production dependency on excluded warm-snow debug stats.
- Verification: `git diff --check`, invariant scan, clean Gradle build, jar/refmap/purity scans, and manual Modrinth profile launch passed for `1.3.0+1.20.1-r1`.
- Release closure: PR #6 merged; tag `v1.3.0+1.20.1-r1` points to `189054a1`; Modrinth version `BPVweInp` is listed; issue #5 is closed as completed.

## 2026-02-12 — First World Load message restored + locked
- Symptom: first-load informational message disappeared on new world creation.
- Root cause: implementation lived only in jar/branch drift; mixin + strings missing from source/manifest.
- Fix: restored CreateWorldScreen flag + LevelLoadingScreen overlay mixin; ensured mixins registered; config/state present.
- Verification: new world shows 2-line message during LevelLoading/Downloading Terrain; clears on close; second world requires flag set again.
- Anti-regression: invariant scan task (`latitudeInvariantScan`) + release checklist item + invariant doc.
