# Latitude Version Matrix

`status: planning matrix`
`updated: 2026-07-02`

## 26.2 Pivot Status (2026-07-02)

Build-metadata retarget is done and compiles cleanly at the toolchain level (Gradle 9.5.1, Loom 1.17.13,
Fabric Loader 0.19.3, Fabric API 0.154.0+26.2 all resolve; MC 26.2 deobf jars download). `compileJava` is
**RED**: 70 errors / 23 files, spanning four independent subsystems (client screen management, camera/render-
target access, `MultiBufferSource` relocation, and a real removal/consolidation of the
`DripstoneClusterFeature`/`PointedDripstoneFeature` worldgen classes). This is a Hard Stop per
`docs/LATITUDE_2_0_OVERHAUL.md` and `docs/porting/PORTING.md` — broad API drift, not a small isolatable cause.
Full detail in `docs/binder/canonical-26.2-pivot-20260702.md`. Headless Atlas/exact-ID proof was not attempted
(blocked behind compile). Root: `<home>/CascadeProjects/Latitude-2.0-26.2-pivot`, branch
`port/canonical-26.2-pivot`.

This matrix records the intended port/pivot truth for Latitude work. Verify upstream metadata before implementation because Minecraft, Fabric API, Loader, and Loom move quickly.

## Current Planning Baseline

| Line | Minecraft | Role | Local status | Notes |
| --- | --- | --- | --- | --- |
| Latitude 2.0 overhaul | 26.2 | planned canonical target | pivot in progress, compile RED | Build metadata retargeted; `compileJava` blocked on broad API drift (Hard Stop). See pivot status above. |
| Latitude 2.0 current feature branch | 26.1.2 | active planning/source reference | current local branch | Do not build the large overhaul here and port later. |
| Latitude 1.4 / early 2.0 reference | 26.1.2 | proven reference | local proof history exists | Use for behavioral comparison and regression baselines. |
| Older backport | 1.21.11 | backport target | structurally partial historically | Do not patch as primary without explicit Maintainer decision. |
| Older backport | 1.21.1 | backport target | historical port line | Requires scoped backport plan. |
| Older backport | 1.20.1 | backport target | hardest old line | Yarn/refmap/Java 17 risks. |
| Snapshot watch | 26.3 snapshots | watch only | not canonical | Track upstream, do not chase during 2.0 pivot. |

## 26.2 Public Metadata Snapshot

Verified during the 2026-07-02 planning pass:

- Minecraft Java `26.2` is the stable target.
- Fabric 26.2 guidance: Loom `1.17`, Gradle `9.5.1`, latest stable Loader `0.19.3`.
- Fabric API `0.154.0+26.2` is available.

Sources:

- https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2
- https://fabricmc.net/2026/06/15/262.html
- https://github.com/FabricMC/fabric-api/releases

## Local 26.1.2 Feature-Branch Metadata

At the planning pass, the active feature branch still used 26.1.2 metadata:

- `gradle.properties`: `minecraft_version=26.1.2`
- `gradle.properties`: `loader_version=0.18.4`
- `gradle.properties`: `fabric_api_version=0.145.4+26.1.2`
- `build.gradle`: Loom `1.15.5`
- `fabric.mod.json`: Minecraft dependency `>=26.1.2`

Do not update these values casually. The 26.2 pivot needs an isolated branch/worktree and deterministic proof.

## 26.2 Pivot Gate

The pivot becomes canonical only after:

- `compileJava` succeeds.
- Build metadata and `fabric.mod.json` agree.
- Mixin targets apply or are repaired.
- Headless Atlas proof runs.
- Exact-ID analyzer proof runs.
- Flag-off output is unchanged where applicable.
- Binder and porting docs record the new truth.

Until then, 26.2 is the planned canonical target, not a completed source truth.
