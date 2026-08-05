# Port Kickoff — Latitude 1.5.0 → Minecraft 1.21.11 (thread 2 of 4)

Read `docs/porting/LATITUDE_1_5_BACKPORT_CAMPAIGN.md` first. This doc is only what is specific to
1.21.11. Market note: 1.21.11 is Latitude's best-performing version ever (792 downloads) with a
17.7k-mod ecosystem — this port matters.

## Working card
- **Objective**: `1.5.0+1.21.11` beta, feature-identical to 26.2.
- **Root**: worktree `<home>/CascadeProjects/Latitude-port-1.5-1.21.11`, branch
  `port/1.5.0-1.21.11` (cut from `v1.5.0+26.2`; verify clean).
- **Hour-1 mojmap spike** per campaign doc; fallback Yarn `1.21.11+build.1`.
- Toolchain: Java 21, loader 0.17.3, fabric-api 0.140.x+1.21.11, Loom ~1.14.10.

## The good news specific to this target
- **Worldgen JSON: zero changes.** Byte-identical against the old 1.21.11 port (verified diff = 0
  lines on `noise_settings/overworld.json`); `find_top_surface` exists here.
- 1.21.11 ≥ 1.21.6, so `MouseButtonEvent`/`KeyEvent`, `client.renderer.fog`, `BlockTintSources`,
  `KeyMapping.Category` **all exist** — the input/fog surface survives as-is.
- Java: nothing above 17 in use; `release = 21` compiles unchanged.

## The expected compile ladder (census-derived)
1. Package moves & renames: `Identifier` → check package (26.2 `net.minecraft.resources.Identifier`
   ↔ target), `Hud` → `Gui`, `LevelLoadListener` → `ChunkProgressListener`,
   `net.minecraft.world.level.gamerules.GameRules` → old package, `Relative` (1.21.2+ — exists),
   `DataComponents`/`BundleContents` (1.20.5+ — exist).
2. **Render-extraction reversal — the bulk.** 5 mixins target 26.x-only shapes:
   `InGameHudMixin` (`Hud.extractHotbarAndDecorations`/`extractRenderState` → `Gui.render`),
   `LevelLoadingScreenLatitudeOverlayMixin` (`extractRenderState` → `render`),
   `WorldSelectionListEntryMixin` (`extractContent` → `render`), `WorldRendererWorldBorderMixin`
   (`WorldBorderRenderState` era), `FogRendererEwMixin` (fog package exists here; verify shape).
   Plus 4 screens' signature surgery (`LatitudeCreateWorldScreen` 2,448 LOC, `LatitudeHudStudioScreen`
   1,212, `CompassHud` 1,123, `GlobeWarningOverlay` 559): `GuiGraphicsExtractor` → `GuiGraphics`,
   `extractRenderState` → `render`, `extractWidgetRenderState` → `renderWidget`. Draw calls are ~150
   primitive `fill`/`text`/`pose`/`scissor` — mechanical. The old port's screens are the working
   idiom reference (in Yarn; translate idiom, not names, if the mojmap spike passed).
3. `LatitudeWorldState`: `SavedDataType` codec (26.2) → `SavedData.Factory` + NBT `load/save`
   (238-LOC file, full persistence-layer rewrite; keep all 7 fields including `last_known_band`
   and `globe_radius`).
4. Create-world plumbing: `WorldCreationContextMapper` / `DataPackReloadCookie` /
   `LevelBasedPermissionSet` (26.x-only, `LatitudeCreateWorldScreen` ~450–465) → target-era
   equivalents; `MinecraftClientStartIntegratedMixin.doWorldLoad` → target method name.
5. Networking (`GlobeNet` records + `StreamCodec`): fine on 1.21.11 (1.20.5+ API) — no rewrite.

## Harvest pointers
- `port/1.4.0-beta-1.21.11` worktree `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11`:
  `docs/porting/HANDOFF-1.21.11-worldgen-treeline-alpine.md` (§ the `18f2629f` settings-key bug:
  keys don't survive level.dat → verify the `globe_radius`/`shouldApplyLatitudeWorldgen` rail
  works here, or worlds silently generate vanilla); working GuiGraphics-form UI.
- **That worktree holds 13 UNCOMMITTED files** incl. untracked
  `docs/porting/HANDOFF-1.21.11-state-and-province-gap-20260614.md` and source edits (GlobeMod,
  LatitudeBiomes, EwSandstormOverlayHud, GlobeClientState, LatitudeDevCommand). **Read them early**
  — the handoff likely documents the province-gap analysis. Do not commit/discard without Maintainer.
- Old-line regressions that must NOT return: E/W warning text lagging the storm visual; spawn on a
  sea-level rock; worldgen gate not dimension-aware (1.5's versions of all three are the law).

## Live lane
Fresh profile `Lat 1.5 - 1.21.11 - TEST`; seed from `world proof latitude 1.3+1.21.11` (fabric-api
0.141.3+1.21.11, sodium 0.8.7+mc1.21.11, lithium, journeymap, Chunky versions known-good).
Provider jars (BoP/Terralith for 1.21.11) must be downloaded. Dedicated-server smoke per campaign.
Client mixins fail soft per GitHub #7 rule.

## Definition of done
Campaign gates green → the maintainer's live acceptance → `1.5.0+1.21.11` beta → tag → binder → "diffs
learned" appended to `kickoff-1.5-1.21.1.md`.

## Diffs learned from thread 1 (26.1.2)
_(appended by the 26.1.2 thread at closeout)_
