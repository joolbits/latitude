# E-W Horizontal World Wrapping — Feasibility Verdict & Recommendation

**Date:** 2026-06-23
**Target:** Latitude mod, MC 26.1.2 (canonical, Art II)
**Goal stated:** Remove the E-W border + sandstorm walls; make the world wrap E-W (travel east → reappear from the west). N-S stays walled (poles). World width `W = 2 * X_RADIUS`.
**Status:** Feasibility verdict. Not an implementation green-light. No code written.

---

## 1. Verdict in one paragraph

**True see-through seamless E-W wrapping — where you stand near the east edge, look east, and see the western terrain continuously joined to it — is NOT feasible in Latitude as a Fabric mod in any realistic timeframe.** Two independent walls block it, either of which alone is fatal: (1) **Minecraft has no coordinate-wrap primitive** — chunk identity, render-section position, lighting propagation, entity bucketing, and pathfinding are all keyed off one globally-unique integer `ChunkPos`; making `X=+X_RADIUS` render adjacent to `X=-X_RADIUS` means forking five core subsystems at once, which is an engine rewrite, not a mixin; and (2) **live terrain is non-periodic Terralith** — the globe's own terrain density functions are orphaned/dead, the world actually generates from `minecraft:overworld/sloped_cheese`, which is a black box we can only feed `floorMod(X, W)` *into* (producing a hard cliff at the seam), never re-sample on a circle (which is the only thing that yields true periodicity). What **IS** achievable and shippable is a **teleport/loop wrap**: remove the E-W wall, X-periodic the biome layer, and teleport the player (plus mount) across the seam so "go east → come around from west" works as a gameplay loop — with the honest caveat that you cannot *see* across the seam (there is an edge-fog/transition, then a reload), and the two edges are different terrain because terrain is not periodic. Notably, the existing E-W "wall" is mostly cosmetic and already dead in this build, so the removal work is small; the engineering is entirely in the "go around" behavior and the square-border knock-on for the poles.

---

## 2. The two distinct things people mean by "wrapping"

These have wildly different feasibility and must be kept separate in every conversation about this feature.

### (a) See-through seamless cylinder
You walk toward the east edge and the western world is rendered continuously joined — no boundary visible, terrain/lighting/entities flow across. This is what makes it feel like a real globe.
- **Feasibility: NO** (as a mod). Requires forking `ServerChunkCache`/`ChunkMap`/`DistanceManager`, the client render octree (`SectionRenderDispatcher`/`RenderSection`), `LevelLightEngine`, `PersistentEntitySectionManager`, and `PathNavigation` to agree on a wrapped topology — all `final`/hot-path. The one project that cracked the *render* half (Circumnavigate, see §3/Prior Art) did it with a deep mixin layer AND still left terrain, structures, and pathfinding unwrapped, and still has a non-matching terrain seam. Even it is self-described alpha with "no expected functionality guaranteed."

### (b) Teleport / loop boundary
An invisible (or themed) line at `|X| = X_RADIUS`; crossing it sets your position to the opposite side. You never see across; there is a brief transition + chunk reload.
- **Feasibility: YES.** Small server-side surface: a per-tick check in the loop you already run (`ServerTickEvents.END_SERVER_TICK`, where `borderUxTick` lives) calling `ServerPlayer.teleportTo`. Works *with* the engine, fights almost nothing. This is the only version that ships.

> **Plain statement for Peetsa: when the recon says "wrapping is feasible," it means (b). When it says "not feasible," it means (a). The deliverable is (b).**

---

## 3. The terrain blocker (the dominant obstacle)

Even setting aside the renderer (§2a), terrain continuity alone defeats seamlessness:

- **Live terrain is not ours.** `noise_settings/overworld.json`'s `noise_router.final_density` references `minecraft:overworld/sloped_cheese` (Terralith/vanilla). The globe's own `base_terrain.json` / `globe:sloped_cheese` graph is **orphaned** — nothing live points at it. So you cannot make terrain periodic by editing globe JSON; the live graph isn't yours. (Confirmed; matches `docs/binder/amplitude-df-wrapper-design-20260622.md`.)
- **The only lever is a runtime DF wrapper**, and it can only remap *into* the Terralith field. `DensityFunction$FunctionContext.blockX()` is honored by the noise leaves, so a custom wrapper CAN feed a rewritten X. But `remapX(x) = floorMod(x, W)` makes the field identical at `X` and `X+W` while the underlying noise is non-periodic → adjacent columns `X=W-1` and `X=W(≡0)` sample **unrelated** parts of the field → a **hard vertical cliff at the seam** in terrain height, cave walls, and ore. That is the opposite of seamless.
- **True periodicity needs circular sampling** (`nx = R·cos(2πx/W)`, `nz`-pass-through), which requires controlling the noise *inputs* — impossible against an opaque Terralith DF you only see at its root. Reviving the dead globe-native graph and rewriting every terrain DF to sample X on a circle is effectively building a new chunk generator (and abandons the "keep Terralith terrain" decision). Out of scope.
- **The bulk path makes it worse.** Chunk gen uses `DensityFunction.fillArray` over `NoiseChunk`'s cell lattice (`cellWidth=4`, tri-linear interpolation, `cache_once`/`interpolated`/`flat_cache` markers). A wrapper at the `NoiseRouter` root sits *outside* those caches, so any remap is sampled at 4-block cell granularity and interpolated across the seam — smearing, not fixing.
- **Terrain isn't even the whole problem.** Caves/aquifers/ores need all ~15 `NoiseRouter` fields wrapped identically (not the 2 the amplitude doc proposes), and **structures, surface rules, feature placement, and `Aquifer`/carver application are chunk-coordinate-seeded** via the world seed — categorically non-periodic, untouched by any DF wrapper. A village at chunk `X` has no relation to chunk `X + W/16`.

**Partial mitigations and their cost:**
| Mitigation | Result | Cost |
|---|---|---|
| Raw `floorMod` X into DFs | Repeating terrain with a hard cliff every W blocks | Low effort, **bad UX** — a visible terrain wall, worse than a fog edge |
| Cross-fade "blend band" near the seam | C0 (no cliff) but an "ironed-flat" mirror stripe; still doesn't fix caves/structures/biomes; smeared by the cell lattice | Medium effort, cosmetic band-aid, only helps if the seam is *seen* (it isn't, in option b) |
| Revive globe terrain + circular X sampling | Genuinely periodic terrain | Multi-month rewrite; abandons Terralith; new chunk generator. **Out of scope.** |

**Coupling to note:** `OceanDistanceField` measures Terralith continentalness via a BFS over `floorDiv(blockX,256)` cells. You can wrap the BFS indices, but the continentalness it samples is non-periodic — so **coastlines/beaches/mangrove/mushroom-island gates won't agree across the seam** even when interior biomes do. Coastal correctness is hostage to the terrain blocker.

---

## 4. Achievable options, ranked

### Option 1 — Teleport-loop with X-periodic BIOMES only (terrain has a seam) ★ RECOMMENDED
- **What the player experiences:** Walk east; near `|X|=X_RADIUS` you hit a themed transition (reuse `GlobeWarningOverlay`/`EwSandstormOverlayHud` HUD infra for a 1–2 s fade/swirl) → teleport to `X -= W` → brief chunk reload hitch → you're on the "west" side. Biomes are continuous *by latitude* (the band you were in continues), so it feels like the same climate zone wrapped around. Terrain height and coastlines at the two edges are unrelated (different places), but you never see both at once, so the discontinuity is hidden behind the transition.
- **Why biomes are the tractable half:** Latitude owns 100% of the X inputs to `pick()`. Z (latitude) is provably untouched by any wrap. Making the biome map periodic = make ~3 helpers periodic (`ValueNoise2D`, `blobNoise01`, `blobNoise01ScaledBlocks`) at the *lattice-index* level (`floorMod(x0, W/scale)`, requiring W divisible by the scales) plus wrap the integer cell grids (16, 38 `VARIANT_CELL`, mangrove cells). Gated on a wrap-X flag → Classic stays byte-identical.
- **Feasibility:** HIGH. **Effort:** ~1–2 weeks (teleport + border rework + biome periodicity + transition polish + far-edge pre-load). **Risk:** LOW-MEDIUM (square-border/pole knock-on is the main one, see §7).
- **Meets the spirit?** YES for "go east, come around from west." NO for "see across / look at the far side." Honest framing required.

### Option 2 — Teleport-loop + best-effort periodic-terrain DF wrap + seam blend band
- **What the player experiences:** Same loop as Option 1, but terrain is *also* run through a `floorMod`-X DF wrapper so the two edges are nearer in height, with a cross-fade band to soften the cliff.
- **Reality:** The blend band only matters if you can *see* the seam — and in a teleport-loop you can't. So this spends weeks of high-risk DF work (wrap all 15 `NoiseRouter` fields, fight the cell lattice, accept smearing) to polish a seam the player never observes. Caves/structures/ores/coastlines still won't match.
- **Feasibility:** MEDIUM (DF wrapper is sound for amplitude; periodicity is the hard part). **Effort:** weeks more than Option 1. **Risk:** HIGH. **Value:** LOW — it polishes the invisible. **Not recommended** unless the goal later shifts to see-through (Option 5).

### Option 3 — Remove E-W walls, leave the world open/infinite E-W (no wrap)
- **What the player experiences:** No east wall, no sandstorm; walk east forever, content repeats only if you also X-periodic biomes (a "treadmill"), and coordinates grow without bound. You never return to your base — east does NOT bring you around to the same place from the west.
- **Reality:** Trivial to do (the wall is mostly already dead), but it is **not "wrapping"** by the stated goal. Precision degrades (`double` jitter ~16M, `int`/BlockPos hard wall at 30M; at W=30 000 that's ~1000 laps). **Does not meet the request.** Listed only as the cheap floor.

### Option 4 — Keep a wall but make it nicer
- Not what was asked. Noted only: the current cosmetic storm-wall could be re-skinned, but the user explicitly wants the wall *gone*. **Reject.**

### Option 5 — See-through seamless (Circumnavigate-style) — FUTURE / NOT NOW
- The only existence proof (Circumnavigate, AGPL-3.0, Fabric, 1.20.4/1.21.1, alpha) renders across the seam by **capping render distance ≤ wrap period and swapping chunks by closest-unwrapped-coordinate**. It still does NOT match terrain at the seam (its wiki: "World generation is still vanilla, so world edges aren't seamlessly blended"), leaves structures/pathfinding/maps unwrapped, and forces a render-distance ceiling tied to world width. Adopting its architecture (license-compatible with our GPL-3.0 repo) + our terrain blocker is a multi-month research project on a moving MC version. **Reserve for a dedicated future effort, not this feature.**

---

## 5. Recommendation

**Ship Option 1: a teleport/loop E-W wrap with X-periodic biomes and a themed transition.**

This is the single best path because it (a) actually delivers "travel east → come around from the west" as a gameplay loop, (b) keeps the tractable, self-contained biome work where Latitude owns the inputs, (c) keeps Z/latitude provably byte-identical and Classic worlds byte-identical via a wrap-X flag (legacy-pin satisfied), and (d) avoids every blocker that has no solution in a mod (renderer fork, periodic Terralith).

**What it WILL do:**
- Remove the E-W wall + sandstorm + EW fog (most already dead).
- Let a player (and their mount/passengers) cross the east edge and reappear at the west, preserving Z/Y/look/velocity.
- Keep biomes climate-continuous across the seam (same latitude band wraps).
- Keep the N-S poles walled.

**What it will NOT do (state this up front, every time):**
- **You will NOT be able to see across the seam.** There is a transition + reload, not a continuous view. This is a loop illusion, not a sphere.
- The two seam edges are **different, unrelated terrain** (terrain is non-periodic) — coastlines, mountains, structures will not line up. Hidden by the transition, but real if you teleport back and forth and compare.
- **Only the player + mount wrap.** Free mobs, boats, arrows, thrown items, redstone, fluids, explosions do NOT wrap across the seam.

---

## 6. Phased plan for the recommended option (Option 1)

Real symbols are from `<home>/CascadeProjects/Latitude-custom-biome-expansion-26.1.2`.

**Phase 0 — Spike & gate (0.5 wk).** Add a world-shape `wrapX` flag carrying `W` (reuse the Mercator world-shape flag plumbing). Prove Classic byte-identical with flag off (map-proof headless atlas). **Go/no-go:** flag-off diff is zero.

**Phase 1 — Remove the E-W wall, keep the poles (server) (0.5 wk).**
- Repurpose `GlobeMod.setGlobeBorder` (`GlobeMod.java:312-340`): stop sizing the square `WorldBorder` to `xRadius`. Because the border is square (can't be rectangular — confirmed `:316-318`), **delete/neutralize the vanilla border** and add a **custom N-S hard stop** keyed on `getZ()` vs `latitudeRadius`, reusing the `latitudeZRadiusOverride` Z value (`setLatitudeZRadius`, `:330`). The pole hazard band already does effects; this adds the hard backstop the square border used to provide.
- No-op `clampSpawnAwayFromEwWarning` (`:649-662`, called `:615`) and the `EW_*` spawn constants (`:75-76`).
- **Go/no-go:** player cannot pass the poles; no E-W block in-game (LIVE, Modrinth App).

**Phase 2 — X-wrap teleport (server) (0.5 wk).**
- In the existing `END_SERVER_TICK` loop (alongside `borderUxTick`, `GlobeMod.java:342`), detect `getX() >= W/2` / `<= -W/2`, call `ServerPlayer.teleportTo` to `X ∓ W`, carry mount/passengers (`Entity.getPassengers()`/vehicle). Free entities/projectiles explicitly NOT handled (documented limitation).
- Force-load a far-edge chunk band via a `TicketType` (`ServerChunkCache.addRegionTicket`) when a player is within N chunks of the seam, to kill generation lag.
- **Go/no-go (LIVE):** cross east → arrive west; reload hitch is a tolerable <~2 s; no crash, no fall damage, no suffocation on landing (add a safe-landing nudge like Looping World does).

**Phase 3 — X-periodic biomes (1 wk).**
- Make `ValueNoise2D` (`util/ValueNoise2D.java:28-49`) periodic at the **lattice-index** level (`floorMod(x0, P)`, `P=W/scale`), plus `blobNoise01` / `blobNoise01ScaledBlocks`, all gated on the wrap flag carrying `W`. Wrap the integer cell grids: chunk 16, `VARIANT_CELL=38`, mangrove cells (`floorMod(cell, W/cellSize)`).
- Constrain `W` (= `2*X_RADIUS`) so it is divisible by the integer noise scales/cell sizes (snap each scale to a divisor of W at sample time, or pick radii so W % 256 == 0 and quantize). This is bookkeeping, not research.
- Wrap the X fed to `LatitudeBiomeSource.getNoiseBiome` (`:103`) / `LatitudeBiomes.pick` and `ProvinceAuthority` (`:146-147,184`).
- Key `OceanDistanceField` caches on the **wrapped** cell index; wrap its BFS neighbor arithmetic. Accept that coastal gates won't fully agree at the seam (terrain-coupled) — interior biomes will.
- **Go/no-go (MAP-PROOF, headless atlas):** biome class at `X=+R` matches `X=-R` at every Z; no cut-line across the categorical band/province/tag map; Z column unchanged vs flag-off.

**Phase 4 — Transition polish + cleanup (0.5 wk).**
- Themed 1–2 s fade/swirl on teleport reusing `GlobeWarningOverlay` / `EwSandstormOverlayHud` infra to mask the reload.
- Delete dead EW code surfaced by recon: `EwStormWallRenderer(+Mixin)`, `EwSandstormOverlayRenderer`, `GlobeRegions.storm*`, `FogRendererEwMixin` (unregister from `globe.mixins.json:36`), the Sodium X-cull `RenderSectionManagerVisibilityMixin` (`:37` — would hide wrapped terrain), and the EW arms of `GlobeWarningOverlay`/`GlobeClientState`/`LatitudeMath.hazardProgress`. Make `WorldRendererWorldBorderMixin` (`:30`) consistent with the no-vanilla-border state.
- **Go/no-go (LIVE):** transition reads as intentional; no leftover EW fog/haze/warnings; poles still walled with their visuals intact.

**Cross-cutting gates:** Classic byte-identical at every phase (flag off). Biomes proven headless; teleport/transition/terrain-seam-invisibility proven LIVE in Modrinth App (`Lat 1.4+26.1.2` staging profile). Legacy saves: wrap flag defaults off; existing worlds keep their square-border behavior unless explicitly converted.

---

## 7. Risks & open questions for Peetsa

1. **The square-border / pole knock-on is the #1 technical risk.** Vanilla `WorldBorder` is square and can't be rectangular or wrap. Removing the E-W clamp removes the N-S clamp too. We must re-implement the pole hard stop as a custom Z-clamp/teleport-back. Needs LIVE verification that nothing lets a player slip past the poles.
2. **Reload hitch is the #1 UX risk.** Even with far-edge pre-loading, the client must receive+build render sections after the jump (it culls chunks 30k blocks away, so we can't pre-send them). Expected: a themed 1–2 s transition, not invisible. **Is that acceptable to you?** If you expected to *see* across, this option does not deliver that.
3. **Only player + mount wrap.** Mobs you're herding, boats, minecarts, arrows, anything mid-flight, and base infrastructure spanning the seam will NOT come across. **Acceptable?** (Standard compromise; even Circumnavigate left pathfinding unwrapped.)
4. **Coastal mismatch at the seam.** Because `OceanDistanceField` reads non-periodic Terralith continentalness, beaches/mangroves/mushroom-island gating won't agree at the two edges. Hidden by the teleport, but real. Interior biomes are fine.
5. **W divisibility constrains world sizes.** For seamless biome noise, `W=2*X_RADIUS` should be divisible by the noise scales/cell sizes (incl. the awkward 38-block `VARIANT_CELL`). This may **quantize the allowed world sizes** (e.g. snap radii to W % 256 == 0). Acceptable to constrain the size picker?
6. **Coordinate precision (only relevant if you ever consider Option 3's open-infinite path).** Not a concern for the bounded teleport-loop, where X stays within `±X_RADIUS`.
7. **Does the loop satisfy the vision, or is "see across" a hard requirement?** If see-across is non-negotiable, the honest answer is this becomes a multi-month Circumnavigate-style + periodic-terrain research effort (Option 5), not a 1.4-era feature. Recommend deciding this *before* Phase 0.
8. **Governance:** plan respects Art II (canonical 26.1.2 first), legacy-pin (flag off → byte-identical, existing saves untouched), and map-proof for biomes + LIVE-proof for terrain/teleport. No conflict identified.

---

### Key references
- E-W wall inventory + square-border interaction: `src/main/java/com/example/globe/GlobeMod.java:312-340, :342, :615, :649-662, :75-76`; `mixin/client/WorldRendererWorldBorderMixin.java`; dead: `client/EwStormWallRenderer*`, `client/EwSandstormOverlay*`, `mixin/client/FogRendererEwMixin.java`, `mixin/client/compat/sodium/RenderSectionManagerVisibilityMixin.java`; `client/GlobeClientState.java` (EW arms); `util/LatitudeMath.java:109-142` (EW) vs `:118-122` (pole).
- Terrain blocker: `src/main/resources/data/globe/worldgen/noise_settings/overworld.json` (live `final_density` → `minecraft:overworld/sloped_cheese`); orphaned `data/globe/worldgen/density_function/base_terrain.json` + `sloped_cheese.json`; `docs/binder/amplitude-df-wrapper-design-20260622.md`; MC `DensityFunction$FunctionContext`, `DensityFunctions$TransformerWithContext`, `NoiseChunk`, `NoiseRouter` (15 fields).
- Biome periodicity: `src/main/java/com/example/globe/world/LatitudeBiomes.java` (`pick`, `ValueNoise2D`/`blob*` call sites, `VARIANT_CELL=38`), `world/LatitudeBiomeSource.java:103`, `world/ProvinceAuthority.java:146-147,184`, `util/ValueNoise2D.java:28-49`, `world/OceanDistanceField.java`.
- Prior art: Circumnavigate (AGPL-3.0, render-distance-≤-period + chunk-swap; terrain seam unsolved) — github.com/FamroFexl/Circumnavigate. Teleport-loop commodity: Looping World, World Border, WorldWrapper (E-W cylindrical, Bukkit). Vanilla/Fabric have no wrap primitive.
