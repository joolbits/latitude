# Architecture fix history

## 2026-02-12 — First World Load message restored + locked
- Symptom: first-load informational message disappeared on new world creation.
- Root cause: implementation lived only in jar/branch drift; mixin + strings missing from source/manifest.
- Fix: restored CreateWorldScreen flag + LevelLoadingScreen overlay mixin; ensured mixins registered; config/state present.
- Verification: new world shows 2-line message during LevelLoading/Downloading Terrain; clears on close; second world requires flag set again.
- Anti-regression: invariant scan task (`latitudeInvariantScan`) + release checklist item + invariant doc.
