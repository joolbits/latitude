# Latitude 1.5 Phase 5 — Minecraft 26.2 Port Manifest

`status: ACTIVE — foundation` ·
`root: <home>/CascadeProjects/Latitude-1.5-26.2` ·
`branch: codex/1.5-mini-launch-26.2` ·
`adopted base: 9972d1623a78f0554e37edd3aecbb97a4aca8ffe` ·
`recorded: 2026-07-18`

## Port law

This is an independent Latitude 1.5 port. It carries the accepted 26.1.x behavior and changes only
what Minecraft 26.2 requires. It does not copy, merge, cherry-pick, or inspect Pivot/Latitude 2.0
source. A compile or runtime red must name the exact target path before that path may be edited.

The accepted 26.1.x HOLDs remain known limitations rather than passes:

- exact reconstruction/runtime acceptance of the original 46-degree-south beach scene;
- the July 18 Terralith correctness refresh;
- a new statistically decisive Terralith performance interval;
- Promenade and combined-provider exact-output parity.

## Candidate platform tuple

The first target-native resolution slice must prove this tuple through Gradle dependency resolution:

| Surface | Candidate value |
| --- | --- |
| Minecraft | `26.2` |
| Java | `25` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.154.0+26.2` |
| Fabric Loom | `1.17.13` |
| Gradle | `9.5.1` |
| Latitude version | `1.5.0+26.2` |
| Minecraft metadata fence | `>=26.2 <26.3` |

Do not borrow the Pivot's product version or open-ended metadata fence.

## Frozen subsystem/path manifest

### A. First compile slice — admitted now

- `build.gradle` — Loom version only.
- `gradle.properties` — Minecraft, Loader, Fabric API, and Latitude version values only.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle distribution only.
- `src/main/resources/fabric.mod.json` — Minecraft dependency fence only.

Run a clean Java 25 compile immediately after this slice. Compiler output owns the next exact path
admission; do not preemptively modernize source.

### B. Mechanical API candidates — admit individually after compile RED

- `src/main/java/com/example/globe/GlobeModClient.java`
- `src/main/java/com/example/globe/PolarCapScrubber.java`
- `src/main/java/com/example/globe/client/CompassHud.java`
- `src/main/java/com/example/globe/client/EwSandstormOverlayHud.java`
- `src/main/java/com/example/globe/client/EwSandstormOverlayRenderer.java`
- `src/main/java/com/example/globe/client/EwStormWallRenderer.java`
- `src/main/java/com/example/globe/client/GlobeClientState.java`
- `src/main/java/com/example/globe/client/LatitudeHudAdjustScreen.java`
- `src/main/java/com/example/globe/client/LatitudeHudStudioScreen.java`
- `src/main/java/com/example/globe/client/LatitudeSettingsScreen.java`
- `src/main/java/com/example/globe/client/SpawnZoneScreen.java`
- `src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java`
- `src/main/java/com/example/globe/client/create/LatitudeWorldLauncher.java`
- `src/main/java/com/example/globe/dev/AutoCreateWorldProbe.java`
- `src/main/java/com/example/globe/dev/DevCaptureKeybind.java`
- `src/main/java/com/example/globe/dev/client/SeamAuditClientBridge.java`
- `src/main/java/com/example/globe/mixin/SurfaceDripstoneLawnmowerMixin.java`
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenInitRedirectMixin.java`
- `src/main/java/com/example/globe/mixin/client/CreateWorldScreenShowMixin.java`
- `src/main/java/com/example/globe/mixin/client/EwStormWallRendererMixin.java`
- `src/main/java/com/example/globe/mixin/client/InGameHudMixin.java`
- `src/main/java/com/example/globe/mixin/client/LevelLoadingScreenLatitudeOverlayMixin.java`

Expected mechanical families include 26.2 screen access, screen setting, camera/render-target access,
visible-section counting, HUD-hidden ownership, blend-context signatures, removed dead render paths,
renamed speleothem features, and reconstruction of the removed `BlockTags.SAPLINGS` constant. Each
edit must be justified by actual compiler output.

### C. Mandatory runtime compatibility paths

These paths may be admitted only by their named mandatory gate:

| Gate | Candidate path | Required behavior |
| --- | --- | --- |
| Sodium startup | `src/main/java/com/example/globe/mixin/client/compat/sodium/RenderSectionManagerVisibilityMixin.java` | Missing optional Sodium culling target cannot crash mixin application. |
| HUD owner | `src/main/java/com/example/globe/mixin/client/InGameHudMixin.java` | Target the 26.2 HUD owner while preserving Latitude 1.5 HUD behavior. |
| Village-start guard | `src/main/java/com/example/globe/mixin/ExtremePolarVillageStartGuardMixin.java` and, only if required, `src/main/resources/globe.mixins.json` | Preserve no-new-starts-beyond-80 behavior on the exact 26.2 structure-start path. |
| Sulfur caves | `src/main/java/com/example/globe/mixin/ChunkGeneratorPopulateBiomesMixin.java`, `src/main/java/com/example/globe/world/LatitudeBiomeSource.java`, `src/main/java/com/example/globe/world/LatitudeBiomes.java` | Treat `minecraft:sulfur_caves` as underground; preserve and locate it. |
| E/W warning compatibility | `src/main/java/com/example/globe/mixin/client/FogRendererEwMixin.java` | Existing 1.x barriers/warnings remain non-crossable and do not silently lose their target hooks. No passage feature is admitted. |

### D. Behavior-preservation subsystems

These are proof surfaces, not blanket mutation authority:

- exact-ID biome cohesion, climate law, Pale Garden, bounded biome fixes, biome caches, and beach
  structural guard;
- existing-save historical policy and accepted legacy village locator residue;
- create-world anti-popping, partial-row input, World/Rules tabs, planisphere, bonus chest,
  structures toggle, and Re-create truth;
- polar fog/warnings, digital/analog HUD, HUD Studio, four location-detail modes, persistence, and
  legacy JSON migration;
- loading overlay through first-safe rendered frame;
- supported provider absent/present behavior;
- fresh dedicated-server creation.

If 26.2 data/schema parsing produces an exact red, append only the exact resource path needed.
No broad `src/main/resources/data/**` rewrite is pre-authorized.

## Conditional-only bundles

Admit only after an exact 26.2 red with a named narrow green proof:

- climate-mismatched structures;
- chunk-generation performance;
- new-world `/locate` disagreement with actual Latitude biome;
- bounded distribution anomalies;
- missing or clustered loading messages;
- HUD reset/default drift or disappearing tooltips.

New provider integrations are not admitted. Test only version-matched combinations already
advertised and available for 26.2; record unavailable providers as not applicable.

## Hard denylist

- Mercator or selectable world shapes.
- Longitude.
- Pole or east/west crossings/passages.
- GeoAuthority, ClimateAuthority, or Terrain V2.
- Tectonics, geology, continents, or new ocean systems.
- Random Spawn Zone.
- Rejected book UI.
- Later 2.0 HUD/create-screen redesign.
- Atlas redesign.
- Broad province-wavelength, polar-tree-line, alpine-massif, or jungle-margin work.
- New Lithosphere, Connector/NeoForge, or Serene Seasons integration.
- General cleanup, dead-code sweeps, refactors, or modernization.
- Any Pivot/2.0 source or history inspection, merge, cherry-pick, or ancestry.
- Removal or crossability changes to existing 1.x east/west barriers.

## Mandatory Phase 5 exit gates

1. Clean Java 25 compile, build, invariants, and target-native impact scan.
2. Exact-ID and climate proof across three fixed seeds and Itty Bitty, Regular, and Ginormous.
3. Fresh and copied-old-save load, save/reload, shutdown, and historical-policy proof.
4. Exact-jar fresh/existing-world loading lifecycle through render warmup and first-safe-frame closure.
5. Create-world state and anti-popping UI matrix, including one real disposable world creation.
6. HUD/Studio/config persistence and legacy migration.
7. Frozen available 26.2 provider absent/present matrix.
8. Fresh dedicated server with Latitude present before world creation.
9. Supported Sodium launch and world load.
10. Sulfur-cave underground preservation and locate proof.
11. Village-start boundary proof through 80 degrees and beyond it, preserving the accepted
    legacy-locator policy.
12. Existing 1.x east/west barriers and warnings remain active and non-crossable, with no passage
    behavior.
13. Exact `1.5.0+26.2` metadata, jar purity/hash, 2.0 denylist, and changed-file-to-roadmap audit.

No mandatory gate may be inferred from compile success or from 26.1.x evidence alone.
