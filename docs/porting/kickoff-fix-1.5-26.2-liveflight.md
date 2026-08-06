# Kickoff — port the 26.1.2 live-flight fixes back to 26.2

`status: ready, do not start yet` · `established: 2026-08-05` · `owner: Maintainer (she/her)`
· `worktree: <home>/CascadeProjects/Latitude-fix-1.5-26.2` · `branch: fix/1.5.0-26.2-liveflight`
· `cut from: tag v1.5.0+26.2` (clean — `git diff v1.5.0+26.2` empty)

Open this in **its own conversation thread**, not a background task. Everything below was found by
flying the 26.1.2 port; each item is shared 1.5 code and therefore **already present in the released
`1.5.0+26.2`**.

## Do not start until the 26.1.2 thread says the list is frozen

The fixes are still landing over there and some are still design calls. Cherry-picking a moving
target means doing the work twice and reconciling two divergent versions of the same worldgen edits.
The 26.1.2 thread will mark this file `frozen` and fill in the final commit list. **Wait for that.**

Do **not** touch `<home>/CascadeProjects/Latitude-1.5-26.2` — that is the maintainer's protected
checkout and currently carries 62 dirty files. This worktree exists so that checkout is never needed.

## Method

These are not retargets — 26.1.2 and 26.2 share this source. Each fix should `git cherry-pick` clean
or apply with trivial context. The only expected conflicts are in files Slice B renamed for 26.1.2
(`screen()` / `setScreen()`, `Gui`/`Hud`, `Speleothem*`/`Dripstone*`): **take the 26.2 form of those
identifiers**, keep the logic. Then run the same gates: `clean check build -PenableInvariantScan=true
latitudeInvariantScan`, the phase-6 verifier, and a live dev-client flight
(`./gradlew runClient -Dlatitude.debug.autoCreateWorldProbe.disable=true` — title screen, autoplay
off; Maintainer creates the world herself).

## Landed on 26.1.2 and ready to carry (commit list grows until frozen)

| Commit | Fix | Notes for the port back |
| --- | --- | --- |
| `68716f22` | **Create-screen authority over globe recognition.** Restores the pre-pivot trust order so lithostitched's patched settings holder cannot make Latitude fail to recognise its own world. | **Highest value.** Latent on 26.2 only because no lithostitched-dependent worldgen mod ships for 26.2 yet; the first one that does reproduces the whole TEST 1 failure (vanilla biomes on Latitude terrain, infinite border). |
| `4660d45e` | **Bonus chest anchored to dry land.** Vanilla's `BonusChestFeature` re-rolls the whole chunk and its heightmap sits above water, so the chest floats and its torches all fail. | Pure win, no design question. |
| `dde70c88` | **On-demand terrain probe + subtropical land gate.** The land-cohesion gate was inert because `skipPreviewHeightForWorldgen` substitutes `robustDelta = 0`; now probes only when a flat-family candidate lands in a gated band. | Changes biome placement, so **expect atlas output to move** — re-baseline on 26.2 rather than treating the diff as a regression. Carries a deliberate `WorldgenAuthorityPolicyTest` source-scan update. |
| `533dd0e3` | Fail-loud on a degraded world + `CompassHud` refuses to render outside Latitude worlds. | The HUD gate is worth having on 26.2 regardless — a meaningless `0°` in a vanilla world is what disguised the TEST 1 failure. |

## Diagnosed here, not yet fixed anywhere — fix once, on whichever line moves first

- **Structure siting ignores Latitude's biome.** Only villages have a start guard
  (`ExtremePolarVillageStartGuardMixin`); every other structure is sited off the raw multi-noise
  biome and never re-checked, so desert temples land in snow. Tractable path: vanilla passes the
  structure's own `Predicate<Holder<Biome>> validBiome` into `Structure.generate`, the method that
  guard already wraps — re-test it against `chunkGenerator.getBiomeSource()`'s biome and return
  `StructureStart.INVALID_START` on mismatch. **Verify carefully: a too-strict guard silently
  deletes every structure in the world.**
- **Temperate snow stripped below y=168.** `ProtoChunkSnowBlockGuardMixin` replaces snow with
  air/dirt/stone whenever `globe$isWarmBand(z)` — which **includes TEMPERATE** — and
  `y < ALPINE_ROCK_Y (168)`. Vanilla snows a temperature-0.2 biome above y≈120, so windswept forest
  at temperate latitudes loses its snow and reads as bare grey grass. The guard is meant to stop
  "snow at cave mouths in jungle"; it should test the column's biome/temperature, not the band alone.
- **Snow guards disagree across write paths.** The 26.2 pivot removed
  `FreezeTopLayerFeatureGuardMixin` (`@Mixin(SnowAndFreezeFeature.class)`) and that feature is now
  guarded **nowhere**, so ProtoChunk-written snow is stripped while feature-written snow survives —
  hence patchy results. Restore one consistent guard across both paths.
- **Create-world screen.** Cancel is dead on the World tab (an input bug: that tab is the only one
  mounting scroll panels, and one overlaps the button row and eats the click) and the layout is
  cramped at default GUI scale. Maintainer has authorised improving the layout to taste.
- **Alpine grass/foliage at temperate latitudes.** Either the post-pivot narrowing of foliage
  guarding to `ExtremePolar*` surfaces, or a downstream effect of the inert terrain gate. The
  26.1.2 flight of `dde70c88` separates these — read its result before touching anything here.

## Standing rules

Modrinth staging is **not** the routine test lane any more; run the dev client with autoplay
disabled and leave it on the title screen for Maintainer. No push, tag, upload, or release without her
explicit authorisation at that step. Binder evidence rows and indexed docs in the same pass as the
work.
