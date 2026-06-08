# Architecture fix history

## 2026-06-08 - Existing-world loading overlay timing restored on 1.4 / MC 1.21.11
- Symptom: when reloading an already saved Latitude world, the bespoke loading screen did not appear until the last moment before entering the world.
- Root cause: the 1.4 port only activated the Latitude loading flag from the late `GlobeStatePayload` path. A direct port of the earlier detector was not safe because 1.4's saved-world authority is persisted `globe_radius`, not the generator settings key.
- Fix: added a `MinecraftClient.startIntegratedServer` HEAD client mixin for reloads (`newWorld=false`) that reads `data/globe_latitude_world_state.dat` and activates the bespoke overlay from persisted `globe_radius > 0`, with generator-key matching only as fallback.
- Lifecycle guard: JOIN is observation-only; the client-ready tick owns clearing the loading flag so late S2C fallback cannot reactivate and produce a `-1ms` double-clear.
- Verification: branch-local `runClient` quick-play reload proof showed existing-save activation before first render and before `S2C globe state: isGlobe=true`, then normal client-ready clear with positive elapsed time.

## 2026-02-12 — First World Load message restored + locked
- Symptom: first-load informational message disappeared on new world creation.
- Root cause: implementation lived only in jar/branch drift; mixin + strings missing from source/manifest.
- Fix: restored CreateWorldScreen flag + LevelLoadingScreen overlay mixin; ensured mixins registered; config/state present.
- Verification: new world shows 2-line message during LevelLoading/Downloading Terrain; clears on close; second world requires flag set again.
- Anti-regression: invariant scan task (`latitudeInvariantScan`) + release checklist item + invariant doc.
