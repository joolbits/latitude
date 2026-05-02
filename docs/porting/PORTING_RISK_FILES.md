# Port Risk File Index

This index was assembled from current repo paths using grep/file existence, not broad source reading.

## Build / Dependency Metadata

- `build.gradle` - central dependency and plugin wiring; version drift here changes the whole port surface.
- `gradle.properties` - pins Minecraft, Yarn, Fabric, and mod versions that usually move first.
- `src/main/resources/fabric.mod.json` - declares entrypoints, mixins, and dependency compatibility for the built mod.

## Mixin Manifest

- `src/main/resources/globe.mixins.json` - mixin load order and client/server injections can break silently on mappings drift.

## Worldgen Live Hook

- `src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java` - the live biome hook path; a bad inject point breaks world population proof.
- `src/main/java/com/example/globe/world/LatitudeBiomes.java` - biome selection authority that can drift when mappings or worldgen assumptions move.

## Biome Authority

- `src/main/java/com/example/globe/dev/BiomeBandPolicy.java` - band policy logic is sensitive to terrain and band-order assumptions.
- `src/main/java/com/example/globe/client/ZoneEntryNotifier.java` - runtime zone reporting can hide whether the right biome authority actually won.
- `src/main/java/com/example/globe/world/LatitudeBiomes.java` - shared biome source truth for bands, variants, and fallback behavior.

## /latdev Tooling

- `src/main/java/com/example/globe/GlobeMod.java` - command registration and dev-only wiring often move when loaders or env checks change.
- `src/main/java/com/example/globe/LatitudeDevCommand.java` - the `/latdev` surface used for smoke proofs and runtime checks.
- `src/main/java/com/example/globe/dev/BiomePreviewExporter.java` - atlas/exporter tooling is a common source of runtime-only assumptions.
- `src/main/java/com/example/globe/dev/BiomePreviewHeadlessRunner.java` - headless runner code can leak into public jars if not audited.
- `src/main/java/com/example/globe/dev/ChunkPregenerator.java` - dev-only chunk work can affect timing and launch behavior.
- `src/main/java/com/example/globe/dev/ChunkRegenerator.java` - regeneration helpers often imply tooling that should stay out of release jars.
- `src/main/java/com/example/globe/dev/DevCaptureKeybind.java` - debug capture paths are easy to leave enabled by accident.
- `src/main/java/com/example/globe/dev/InvariantScan.java` - invariant scans are useful during ports but should stay clearly dev-only.

## Create-World UI

- `src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java` - bespoke world setup UI is a high-risk port touchpoint.
- `src/main/java/com/example/globe/client/create/LatitudePlanisphereRenderer.java` - create-world rendering depends on client UI APIs that drift often.
- `src/main/java/com/example/globe/client/create/LatitudeWorldLauncher.java` - launch logic can bridge UI, runtime setup, and proof flow.
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenMixin.java` - create-world hook points can shift with UI refactors.
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenShowMixin.java` - show/open flow for the world screen can break on constructor or init changes.
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenInitRedirectMixin.java` - init redirection is fragile when the target method signature changes.
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenLatitudeToggleMixin.java` - latitude toggle behavior belongs to the bespoke UI path and should be watched closely.
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenSpawnZoneMixin.java` - spawn-zone selection can silently drift from the intended band choice.

## Loading Overlay / First-Load UX

- `src/main/java/com/example/globe/mixin/client/LevelLoadingScreenLatitudeOverlayMixin.java` - loading overlay injection can fail without obvious compile errors.
- `src/main/java/com/example/globe/mixin/client/DownloadingTerrainScreenFirstLoadMessageMixin.java` - first-load messaging is part of the runtime proof path and easy to regress.
- `src/main/java/com/example/globe/client/GlobeWarningOverlay.java` - runtime warnings can mask or confirm world-entry stall behavior.
- `src/main/java/com/example/globe/client/LatitudeClientState.java` - client state often coordinates overlay timing and world-entry transitions.

## Fog / EW Visuals

- `src/main/java/com/example/globe/mixin/client/FogRendererMixin.java` - fog hooks are mapping-sensitive and frequently move between versions.
- `src/main/java/com/example/globe/mixin/client/FogRendererEwMixin.java` - east/west visual adjustments can regress when render plumbing changes.
- `src/main/java/com/example/globe/mixin/client/BackgroundRendererFogMixin.java` - background renderer hooks are another common render API drift point.

## Release Artifact Purity / Dev Tooling

- `src/main/java/com/example/globe/client/ClipboardImageWriter.java` - process-launch or desktop integration here is a release-jar purity risk.
- `src/main/java/com/example/globe/dev/BiomePreviewExporter.java` - exporter code should stay out of public jars unless explicitly intended.
- `src/main/java/com/example/globe/dev/BiomePreviewHeadlessRunner.java` - headless runner logic is tooling-only and must not leak into release artifacts.
- `src/main/java/com/example/globe/client/LatitudeSettingsScreen.java` - client settings code sometimes drags in dev-only helpers during refactors.
- `src/main/java/com/example/globe/GlobeModClient.java` - client bootstrap can accidentally keep dev tooling reachable in production.

## Symbols And Patterns To Grep During Future Ports

- `populateBiomes`
- `fillBiomesFromNoise`
- `doCreateBiomes`
- `LevelLoadingScreen`
- `DownloadingTerrainScreen`
- `CreateWorldScreen`
- `BackgroundRenderer`
- `ProcessBuilder`
- `Runtime.exec`
- `Desktop.getDesktop`
- `modLocalRuntime`
- `pale_garden`
- `Equator`

