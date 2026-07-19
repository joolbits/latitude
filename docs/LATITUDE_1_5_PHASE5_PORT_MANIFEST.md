# Latitude 1.5 Phase 5 — Minecraft 26.2 Port Manifest

`status: PASSED locally — Phase 6 decision gate` ·
`root: <home>/CascadeProjects/Latitude-1.5-26.2` ·
`branch: codex/1.5-mini-launch-26.2` ·
`adopted base: 9972d1623a78f0554e37edd3aecbb97a4aca8ffe` ·
`candidate commit: 96c43b452027a2e89e7899b7f60227656dab151d` ·
`recorded: 2026-07-19`

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

## Phase 5 exit result

| # | Mandatory gate | Status | Exact evidence |
| ---: | --- | --- | --- |
| 1 | Java 25 compile/build/invariants/impact | **PASSED** | `tmp/latitude-1.5-phase5-20260718/final-artifact/post-commit-clean-build.log` (SHA-256 `85e22b300f90c7a73ffdc5947aa21450fbb3aeef0eeeebbf85544c2cf4cf3c9a`); clip, location-detail, polar-presentation, and village-latitude suites green. |
| 2 | Three seeds × Itty Bitty/Regular/Ginormous | **PASSED** | `worldgen-matrix/logs/matrix-9-row-summary.tsv`, SHA-256 `b563e1ef30c414bf70afe89c57b8b7e13250b543c170b3cb330dd49fed08cef7`; every row passed build, strict climate, and exact-ID integrity. |
| 3 | Fresh and copied-old-save lifecycle/policy | **PASSED** | Fresh server/reload hash index `server-runtime-proof-sha256.txt` SHA-256 `5767022b54462bc9b97a5c13305db5aab82107bc1c19b2fad1280e2561f23c10`; copied 26.1 migration hash index SHA-256 `c4e4f56737a52ba30928383192bf5d2dcb98a71a185d4095783ffccd0ccc3739`. |
| 4 | Exact-jar fresh/existing client lifecycle | **PASSED** | Exact candidate origin and bytes; fresh log SHA-256 `577c64ff3d9c017c30bf01775b033990751328d0f71a9c9367d0cafa3bbf7bbe`; reload log SHA-256 `af322613f78daf2ed76ff690e59734493636dae4c704abaf9d53bb2f94d52918`; both reached normal first-safe-frame closure and saved all dimensions. |
| 5 | Create-world state and anti-popping | **PASSED** | Executable nine-assertion clip policy, structural anti-popping verifier, real disposable world creation, and runtime state matrix SHA-256 `8ac866bee7320064230944b1b1e61aa6b8ae85ffcaa8b7992d4fb6c683c7f521`. |
| 6 | HUD/Studio/config and legacy migration | **PASSED** | Runtime HUD/Studio matrix SHA-256 `03eab99631cc2ac5523e17c4770cf5a14f11f361c93142eda63e02b0b0e6ca69`; restart verdict SHA-256 `aee28c84afd6e7d90cd6b6f8c49a4f5ffda3b7ccfc7027719322e20a444847b9`; restart log SHA-256 `3a0124cd59641810808ad9f61463b1b1dc8bc485d24f14ea21c83583f8c02161`. |
| 7 | Available provider matrix | **PASSED** | Absent, Terralith `2.6.4`, Tectonic `3.0.26`, and Biomes O' Plenty `26.2.0.0.24`; report SHA-256 `81c93856bd6a77eac19924e7247cfe444f185f5d4dbdae5e0ad1ad5a59d773b9`. Promenade has no official Fabric 26.2 build and is N/A, not a parity pass. |
| 8 | Fresh dedicated server | **PASSED** | Latitude present before fresh world creation; create/save/stop/reload proof is indexed by `server-runtime-proof-sha256.txt`. |
| 9 | Sodium | **PASSED** | Official Sodium `0.9.1+mc26.2`; exact launch, world creation/load, first-safe tick, save, and clean stop in `sodium-green-client.log`. The removed optional visibility hook is tolerated rather than reimplemented. |
| 10 | Sulfur caves | **PASSED** | Fresh server located `minecraft:sulfur_caves` at `[-192, 2, 208]` and emitted `PHASE5_SULFUR_EXACT_PASS`. |
| 11 | Village boundary | **PASSED** | Executable exact-80 policy plus fresh-world NBT: villages at 79.2° and `beyond80=0`; copied-old-save locator residue remains the accepted compatibility policy. |
| 12 | Existing E/W barriers and warnings | **PASSED** | Exact-jar client exercised ordinary forward input at both borders; client/server positions remained inside the ±10,000 boundary and both warning directions fired. Runtime verdict SHA-256 `f7f3086fa014bede7ccb7c7c2a509ca53cb9dc97a5e37185a515ae617386cd5b`. |
| 13 | Metadata, purity, hash, and denylist | **PASSED** | Candidate `96c43b452027a2e89e7899b7f60227656dab151d`; SHA-256 `1753f8d038d3387b4c152a84623d85a2eaa0286a40ff510e2c00c6e30d793cc0`; SHA-512 `a3aeaaf6aee6a8e174ba9d1ad1146c27a3ca2173780ff3bb5b6186a2e182f1ed61307a577497b92c1ca14b80361c5586ffefdc2a63692fb4d217113750fcd94a`; `Build-Dirty: false`; no forbidden dev/debug or 2.0 payload. |

The combined Gate 5/6 report is
`tmp/latitude-1.5-phase5-20260718/ui-matrix/PHASE5_G5_G6_RUNTIME_REPORT.md`
(SHA-256 `f88cafb73b46235a335512930096031a56701185e20f2ddd3cb9cdda29208b43`).

## Truth and authorization boundary

- The 26.2 HUD/Create proof is policy, structural, config, and disposable runtime evidence. It does
  not claim a new player-reviewed pixel matrix or manual HUD Studio interaction; the accepted
  26.1.2 visual proofs remain historical evidence.
- The four accepted HOLDs at the top of this manifest remain limitations, not new passes.
- Phase 5 ends at a local docs exit savepoint layered after the separately identified candidate
  artifact commit. Phase 6, real profiles/worlds, tags, pushes, release work, uploads, publication,
  and Latitude 2.0 remain unauthorized.
