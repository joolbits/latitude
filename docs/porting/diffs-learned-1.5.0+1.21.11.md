# Diffs learned — `1.5.0+1.21.11` (thread 2 of 4) → for thread 3 (1.21.1)

`date: 2026-08-07` · `from: port/1.5.0-1.21.11` · `to: kickoff-1.5-1.21.1.md`

Per the campaign's Phase-9 closeout. Written here rather than in the protected `Latitude-1.5-26.2`
checkout, which port threads must never modify — **paste this into the next kickoff's diffs-learned
section; thread 2 cannot.**

> **Status: thread 2 is not finished.** The port compiles, gates green, boots, generates worlds, and
> the harvest of thread 1's nine post-tag fixes is **complete**. Outstanding: the maintainer's live acceptance
> on `TEST 6`, her two open worldgen questions (both need measurement), the fog A/B, and release.
> Current state lives in
> [`docs/binder/latitude-1-5-port-1p21p11-slice-g-closure-20260807.md`](../binder/latitude-1-5-port-1p21p11-slice-g-closure-20260807.md).

---

## 1. Run the mappings spike statically. It costs minutes and it is accurate.

Do not compile to count REDs. Stream Mojang's ProGuard mapping file for the target (URL is in
`<home>/.gradle/caches/fabric-loom/<ver>/mojang_minecraft_info.json` → `downloads.client_mappings`),
collect its class set, and test every `import net.minecraft.*` in the tree against it. Read the 26.2
side with `javap` against the **unobfuscated** 26.2 jar already in the Loom cache. Both sides are
primary artifacts.

Thread 2's result: **171 of 178 imports resolved, 7 REDs — and the compiler later produced exactly
those 7.** The prediction was perfect.

**Its blind spot, which cost real time:** it only scans `net.minecraft`. Three further REDs were
Fabric API (`ServerLevelEvents`→`ServerWorldEvents`, `KeyMappingHelper`(`keymapping.v1`)→
`KeyBindingHelper`(`keybinding.v1`), `PayloadTypeRegistry.clientboundPlay/serverboundPlay`→
`playS2C/playC2S`). **Scan `net.fabricmc.*` too.**

## 2. Structural facts that will repeat

- **26.2 ships unobfuscated.** Its version JSON has no `client_mappings`, which is why `build.gradle`
  has no `mappings` line at all. Older targets are obfuscated, so Slice A must **add**
  `mappings loom.officialMojangMappings()` — an addition, not a swap.
- **Loom 1.17.x refuses Mojang mappings** with *"Cannot use Mojang mappings in a non-obfuscated
  environment"*. Drop to the older line (1.14.10 worked for 1.21.11, with Gradle 9.5.1 unchanged).
- **`fabric-loader`'s POM declares no dependencies.** With plain `implementation` you get the loader
  jar and **no Mixin at all** (62 errors of `package org.spongepowered.asm.mixin does not exist`).
  Loom 1.17.x injected it regardless; 1.14.10 only does through its own configurations. Use
  `modImplementation` / `modCompileOnly` / `modLocalRuntime`.
- **Version-coupled pins hide outside `gradle.properties`.** `latitudeSodiumFogVerifier` pinned a
  26.2 Sodium file *and is wired into `check`* — a stale id fails the gate, not the build.

## 3. The render-extraction reversal is a verified rename table, not a design problem

Valid for any target at **1.21.6 or newer**. Left column from `javap` on the 26.2 jar, right from the
target's mapping file:

| 26.2 | target | delta |
| --- | --- | --- |
| `GuiGraphicsExtractor` | `GuiGraphics` | rename |
| `text(...)` ×6 overloads | `drawString(...)` ×6, same parameter lists | rename |
| `centeredText(...)` ×3 | `drawCenteredString(...)` ×3 | rename |
| `Hud.extractRenderState` | `Gui.render` | 1:1 |
| `Hud.extractHotbarAndDecorations` | `Gui.renderHotbarAndDecorations` | 1:1 |
| `LevelLoadingScreen.extractRenderState` | `.render` | 1:1 |
| `WorldListEntry.extractContent` | `.renderContent` | 1:1 (**not** `render`) |
| `AbstractWidget.extractWidgetRenderState` | `renderWidget` | 1:1 |
| `fill`/`pose`/`enableScissor`/`guiWidth` | identical | none |

The whole draw layer is **8 distinct primitives**, only two of which are renames.

**Below 1.21.6 this table does not apply** — the fog package and the deferred GUI render-state
pipeline both arrive at 1.21.6. Thread 3's target (1.21.1) has the *flat*
`net.minecraft.client.renderer.FogRenderer` with no `FogData` at all, so thread 2's fog work does
**not** transfer.

## 4. The 1.21 line is not one API surface

Measured REDs against this 1.5 tree: 1.21.1 → **20**, 1.21.4 → 16, 1.21.5 → 15, 1.21.6–1.21.8 → 13
(identical), 1.21.9–1.21.10 → 11 (identical), 1.21.11 → 7.

Compile-time span is impossible; **runtime** span is decided by whether intermediary members survive,
so mojmap-vs-Yarn is irrelevant to fence width. the maintainer's standing policy: build once, boot the same
jar on adjacent-version profiles, ship the widest fence that passes, record the tested bounds. Thread
2 **skipped** that test on her instruction and shipped pinned to `>=1.21.11 <1.21.12`.

## 5. What static checks cannot catch — the expensive lessons

Thread 2 built `tools/verify_mixin_targets.py` (wired into `check` as `latitudeMixinTargetVerify`),
which resolves every `@Mixin` target, injector `method` selector, and `@At` descriptor against the
remapped Minecraft classpath, following inheritance. **Take it.** It caught real defects. But:

- **It cannot check an `@Inject` handler's own parameter list.** Mixin does not validate that against
  the target descriptor until runtime weaving. A stale 26.2 handler signature compiles, passes the
  verifier, and passes every server boot — then crashes the client.
- **A dedicated-server boot never loads client-only classes.** `Minecraft.doWorldLoad` and every
  screen mixin need a *client* boot. Server boots proved ~21 mixins; the rest needed the real client.
- **`@Local(name = ...)` does not survive a port.** Local variable names come from the mappings.
  Prefer type matching, then an enclosing parameter, then an ordinal — in that order.
- **A biome ID is a string.** `CaveBiomeRepresentationProfile` required `minecraft:sulfur_caves`, a
  26.x biome absent below 26.x — a hard crash on world creation that no compiler or mixin check can
  see. **Check every presence-requiring biome list against the target's own registry.**
- **A policy test's synthetic registry must model the target's real registry.** Ours was built from a
  descriptor ledger still carrying `sulfur_caves`, so the suite was green while the game crashed.
- **`gradle runClient`/`runServer` can print `BUILD SUCCESSFUL` while the game crashed on startup.**
  Grep the log. Never trust the exit code.
- **The same failure, one layer up: a custom Gradle task that packages a "TEST" or staging jar must
  build from `remapJar`'s output, never from the plain `jar` task's.** Loom's `jar` task leaves
  classes in *named* (Mojang) mappings; only `remapJar` remaps them to *intermediary*, which is what
  a real production Fabric Loader (Modrinth App, CurseForge, any non-dev launch) actually loads. This
  is invisible to compilation, to `latitudeMixinTargetVerify`, and to the dev client and headless
  server smoke tests — **all of those run on Loom's own dev classpath, which is itself named-mapped**,
  so unmapped mod code matches it natively and nothing ever needs remapping there. It only manifests
  under a genuine production launch, as a `Mixin transformation ... failed` /
  `ClassMetadataNotFoundException` crash naming a real Minecraft class by its *named* form. If a
  target inherits TEST-jar tooling built for an unobfuscated source line (26.x, where `jar` and
  `remapJar` output are identical because there's nothing to remap), check explicitly whether that
  tooling assumed away a remap step the new, obfuscated target now requires. Full account:
  [`docs/binder/latitude-1-5-port-1p21p11-test-jar-remap-crash-20260807.md`](../binder/latitude-1-5-port-1p21p11-test-jar-remap-crash-20260807.md).

## 6. Sweep the predecessor's whole history, not its handoff

Thread 1's handoff named **four** bugs. Its branch carried **nine** functional fixes after the shared
tag. Thread 2 harvested four in Slice D and only found the rest after Maintainer hit two of them live —
then had to spend a whole extra slice harvesting them. Sweep the full history **before** the live
lane, not after.

Method: `git log v1.5.0+26.2..port/1.5.0-<prev>` and list every commit touching `src/`. Object stores
are shared across the port worktrees, so commits are reachable by SHA with no fetch.

**Harvest in chronological order, and read each diff before you pick it.** A later commit can revert
or rewrite part of an earlier one, and the subject line can bury that in a second clause. Two cases
in one slice:

- `249cd1d7` was a wrong turn superseded by `179e6200` — cherry-picking only the latter gives the
  correct final state.
- `89e9f07b` (*"Guard structure siting against Latitude's biome; **scope the land gate to
  temperate**"*) reverts part of `dde70c88`. Harvesting `dde70c88` alone ships a measured regression:
  widening the land-cohesion gate to subtropical routes warm highlands into the temperate
  `LAT_TEMPERATE_MOUNTAIN` pool and leaves `minecraft:eroded_badlands` unplaceable at
  `topologyEligible=0`.
- `533dd0e3` and `68716f22` are **one fix in two halves**, 24 minutes apart, both rewriting the same
  block of `GlobeMod.initLatitudeBiomesForWorld`. Taking only the first ships a fail-loud guard that
  crashes on **every** CliffTree world — worse than the bug. Take both, in order.

A per-commit sweep table that compresses each commit to one phrase will hide all three. Read the
bodies; they say so.

## 7. Bugs that are 1.5-wide, not port artifacts — check them on every target

Both were **diagnosed by thread 1 and never fixed**, and reproduce on the released `1.5.0+26.2`:

1. **Windswept snow stripping.** `ProtoChunkSnowBlockGuardMixin` strips snow whenever the latitude
   band is "warm" and `y < ALPINE_ROCK_Y`. **TEMPERATE counts as warm**, but vanilla snows a
   temperature-0.2 biome (windswept forest/hills) above roughly y=120. Result: bare desaturated grey
   grass at temperate latitudes with no snow to explain it. The grey grass itself is *correct
   vanilla colour*; only the missing snow is ours. Thread 2 fixed it by testing the biome's own
   `coldEnoughToSnow` instead of the band (commit `a626c45c`). Note `FreezeTopLayerFeatureGuardMixin`
   was **removed in the 26.2 pivot**, leaving `SnowAndFreezeFeature` unguarded — which is why the
   damage was patchy rather than uniform.
2. **Create-screen Cancel at GUI scale 5x+.** Clipped climate rows keep their full widget rectangle,
   so vanilla's `mouseClicked` sends a Cancel click to whichever row overlaps it — Cancel selects a
   climate. Fixed by `179e6200`.

3. **Silent degradation to vanilla under a worldgen-mod conflict** (`533dd0e3` + `68716f22`). Worth
   understanding before you meet it: the failure is **undetectable before launch**, because vanilla's
   `createLevelFromExistingSettings` reloads datapacks and rebuilds the registries *internally*, after
   the last point Latitude can observe. `RegistryFileCodec` then fails `canSerializeIn` and silently
   inlines the settings (639 bytes → 84 791). The recognition fix is architectural and applies to
   every target: **record the create screen's pending radius before consulting the generator**, so
   recognition flows from the create screen's own testimony via `latitude_world_state.dat`. Holder-key
   recognition must never be load-bearing — lithostitched legitimately mutates that holder in place.

**Still open and likely 1.5-wide:** `savanna_plateau` can be preserved by a pure noise field with no
height test (`preserveSavannaPlateauAtSanitize`). Needs *measurement*, not code reading. The
land-cohesion gate being inert under `latitude.skipPreviewHeightForWorldgen` is fixed by `dde70c88`
(harvest it **with** `89e9f07b`), but note the atlas sampler is structurally blind to that fix —
`hasPreviewTerrainInputs` is false under `callerContext=ATLAS_SAMPLER`, so a zero-diff census proves
nothing in either direction. It has to be checked live.

## 8. Worldgen parity is measurable, and it is the strongest claim you can make

Seed-matched headless atlas against a 26.2 baseline built in a **throwaway `git worktree` at the
tag** (never touch the protected checkout): 1,565,001 samples, Latitude's own band assignment
**bit-identical**, 62/66 biome rows exact, total variation distance 0.0014%. The only residual was
zero-sum within-band swaps between climate-adjacent vanilla biomes — Minecraft's own cross-version
difference, not port drift.

**Method trap:** the same requested `seed=` can still yield different derived `worldSeed` per
worktree, because each `run-headless/world` is independently generated. Pin `level-seed` in both run
dirs and delete both worlds. (Thread 2's re-run proved the census is actually worldSeed-independent —
so the comparison was valid either way, but is now proven rather than assumed.)
