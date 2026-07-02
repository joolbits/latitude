# Latitude Version Matrix

`status: planning matrix`
`updated: 2026-07-02`

## 26.2 Pivot Status (2026-07-02)

**GREEN for the deterministic-headless proof gate.** Build-metadata retarget + narrow API-drift repair are
done; `compileJava` is green (134 → 0 errors) and the headless Atlas proof runs green on 26.2.

- Toolchain: Gradle 9.5.1, Loom 1.17.13, Fabric Loader 0.19.3, Fabric API 0.154.0+26.2, MC 26.2, Java 25.
- `compileJava`: green.
- Headless Atlas (`runBiomePreview`): green — mod loads as `globe 2.0-beta.1+26.2`, mixins apply, biome export
  succeeds, distribution is banded and sane, and output is **deterministic** across consecutive runs (no
  seed/shape/radius leak).
- Full production `build`: green — `latitude-2.0-beta.1+26.2.jar` produced. Its mixin-apply structure
  (`"refmap"` declared, no standalone refmap file) is **identical to the live-proven 26.1.2 jar**, so mixins
  will apply in the Modrinth runtime the same way.
- The 134-error drift turned out to be mostly mechanical renames (screen state moved to `Gui.screen()`,
  `setScreen`→`setScreenAndShow`, camera/render-target getters, dripstone→speleothem feature renames) plus
  dead-code stubs — NOT the architectural wall the first pass feared. Full detail + before/after table in
  `docs/binder/canonical-26.2-pivot-20260702.md`.

Remaining before release-ready (separate lanes): (1) **live 26.2 client pass** — client mixins/HUD/menus are
compile-proven only, the one item needing Peetsa at the keyboard; (2) no cross-version byte-identical flag-off
diff (not meaningful across MC versions). NOTE: the earlier "frozen-river reflection" item was investigated and
is **not** a regression — that feature has been inert since before 26.1.2 (the `getFeatures` name never existed
in current mappings), so it was correctly left untouched to preserve behavior.

Root: `<home>/CascadeProjects/Latitude-2.0-26.2-pivot`, branch `port/canonical-26.2-pivot`.

This matrix records the intended port/pivot truth for Latitude work. Verify upstream metadata before implementation because Minecraft, Fabric API, Loader, and Loom move quickly.

## Current Planning Baseline

| Line | Minecraft | Role | Local status | Notes |
| --- | --- | --- | --- | --- |
| Latitude 2.0 overhaul | 26.2 | planned canonical target | pivot GREEN (compile + headless Atlas) | Build metadata + narrow API-drift repair done; deterministic headless proof green. Live client pass + frozen-river reflection fix still pending. See pivot status above. |
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
