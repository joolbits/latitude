# E-W Wrap via Ocean Seam — Implementation Plan (canonical 26.1.2)

> **2026-07-02 direction update:** This is a scrapped historical record, not a future Latitude 2.0 plan. The
> current overhaul front door is `docs/LATITUDE_2_0_OVERHAUL.md`; it explicitly retires E/W wrap and ocean-seam
> terrain sinking as the 2.0 centerpiece.

`status: SCRAPPED 2026-06-24 — feature abandoned after live testing` · `scope: worldgen + server + client` · `date: 2026-06-23`

> ## ❌ SCRAPPED (Peetsa, 2026-06-24) — E-W wrapping abandoned
> Built end-to-end (wrap teleport + pole clamp + ocean biome band + Y-aware terrain-sink density function),
> compiled, mixins applied, Classic byte-identical, staged, and LIVE-TESTED. The ocean sink did produce real
> water at the edges — but live testing confirmed the teleport-loop **cannot be made seamless**, exactly as
> the feasibility verdict predicted:
> - The carved ocean edge looks artificial (flat shelves / abrupt cliffs).
> - Clouds shift on teleport (world-positioned) → the wrap is visible.
> - Ocean features/layout differ across the seam → still obviously a teleport, not a continuation.
> Peetsa: "It's broken... I don't think this is going to work. Let's scrap the project." **All wrapping code
> was reverted** (deleted `LatitudeOceanSinkDensityFunction`, `RandomStateOceanSinkMixin`, `RandomStateAccessor`;
> removed `GlobeMod.wrapAndClampTick` + the pick() ocean band; un-registered the mixins). The **Mercator 2:1
> world type is unaffected and remains** (it is a separate feature). Staged jar after revert: `bd134c2`.
> This doc + `horizontal-wrapping-feasibility-20260623.md` are kept as the design record / "don't retry" note.


> ## BUILD STATUS (2026-06-24) — OCEAN SEAM NOW BUILT (live-tune pending)
> - **BUILT — wrap mechanics** (`GlobeMod.wrapAndClampTick`, END_SERVER_TICK, gated isMercator): E-W wrap
>   teleport at `|X|>=X_RADIUS-WRAP_EDGE_MARGIN(640)` (inside the 500-block EW storm zone → sandstorm + the
>   suppressed square border are never reached = removed); preserves heading + horizontal momentum; safe-Y
>   landing. N-S hard pole clamp at `|Z|>=Z_RADIUS`.
> - **BUILT — ocean seam (Piece 1):** (a) `LatitudeBiomes.pick()` forces latitude-appropriate ocean in the
>   edge band (`|X| >= X_RADIUS - OCEAN_BAND`, both pick bodies, gated isMercator). (b) Terrain SINK:
>   `LatitudeOceanSinkDensityFunction` (custom DF, 0 interior → −SINK_STRENGTH near edges, X-only) wrapped
>   onto `NoiseRouter.finalDensity` via `RandomStateOceanSinkMixin` (inject `RandomState.<init>` TAIL, gate
>   isMercator + overworld seaLevel==63, rebuild router by exact 15-field ctor, set via `RandomStateAccessor`
>   @Mutable; try/catch → vanilla on failure). Verified: compile+build green; worldgen mixins apply headless;
>   Classic atlas byte-identical (`37904cda`); staged jar `58c17117`.
> - **LIVE-TUNE PENDING (Peetsa, in-game):** the sink knobs are guesses — tune via -D (or future /latdev):
>   `latitude.oceanSinkK` (default 3.0, terrain bias depth), `latitude.oceanSinkBand` (1200, how far inland
>   the ocean reaches — must be ≥ render distance AND ≥ wrap margin 640), `latitude.oceanSinkFullMargin`
>   (640, full-sink distance from the edge = wrap margin). Confirm in a Mercator world: approaching the E-W
>   edge becomes open ocean, the wrap happens in water (no land jump), water actually fills (aquifer/sea), and
>   the coastline ramps in naturally rather than cliffing.
> - **Follow-ups:** mount/passenger carry across the seam; far-edge preloading (kill the reload hitch);
>   a /latdev command to live-set the sink knobs without a restart; verify aquifer water-fill depth vs K.


Supersedes the RECOMMENDED "Option 1" in `horizontal-wrapping-feasibility-20260623.md`. Peetsa's ocean-seam
cheat removes the hardest, riskiest parts of that option.

## The idea (Peetsa, 2026-06-23)
Bound the world's **east and west edges with open ocean**. The player sails into ocean approaching the edge,
gets teleported across the seam (east→west) **inside the ocean**, and keeps sailing in ocean — eventually
reaching land again on the far interior. Because open water is visually uniform, the teleport is invisible:
there is no terrain to match across the seam, no "other side" to render, nothing to blend.

## Why this beats the feasibility doc's Option 1
Option 1 needed **X-periodic biomes** (periodic noise, world-size divisibility constraints, wrapping the
16/38/mangrove cell grids) + a **themed transition** + accepted a **coastal mismatch** at the seam. The ocean
seam makes **all three unnecessary**:
- No periodic biomes — biomes don't need to match across an all-ocean seam.
- No themed transition — open ocean *is* the transition.
- No coastal-mismatch worry — there are no coasts at the seam (it's deep ocean).

What's left is much smaller and mostly mechanical, plus one live-tuned terrain piece.

## What it WILL / WON'T do (unchanged honest caveats)
- WILL: remove the E-W storm wall/sandstorm; let the player + mount cross east→west; keep climate continuous
  by latitude (you exit and re-enter the same ocean type at the same Z); keep the N-S poles walled.
- WON'T: let you literally *see* across the seam (open ocean → there's nothing to see; a brief reload hitch
  is masked by water). Only the **player + mount** wrap — free mobs, boats, arrows, dropped items do NOT.

## Scope: gated to Mercator worlds
All NEW worlds are already Mercator (2:1). Wrapping is the natural E-W behavior of the globe, so **Mercator ⇒
wraps** (`isMercator()` gates the ocean band + teleport + wall removal). Classic/legacy saves: unchanged,
keep their square border. One flag, byte-identical Classic (legacy-pin).

## The four pieces (must land together to be coherent)

### Piece 1 — Ocean edge band  (biome: source-provable · terrain: LIVE-tuned)
Define `EW_SEAM_OCEAN_BAND` (blocks from each X edge that are forced to ocean). Must be ≥ client render
distance (≤ 32 chunks = 512 blocks) so you're fully surrounded by water at the seam → start ~768–1024,
live-tune. World half-width on X = `getActiveXRadiusBlocks()`.

- **1a. Biome (Latitude owns this — atlas-provable).** In `LatitudeBiomes.pick()`, right after `bandIndex`
  is computed (`~L2610`, before the beach shortcut `~L2612`), add:
  ```java
  if (isMercator() && Math.abs(blockX) >= getActiveXRadiusBlocks() - EW_SEAM_OCEAN_BAND) {
      Holder<Biome> oceanBase;
      try { oceanBase = biome(biomeRegistry, "minecraft:ocean"); } catch (Throwable t2) { oceanBase = base; }
      Holder<Biome> oceanPick = oceanByLatitudeBandOrBase(biomeRegistry, oceanBase, blockX, blockZ, bandIndex);
      if (oceanPick == null || !oceanPick.is(BiomeTags.IS_OCEAN)) oceanPick = firstPresentOcean(biomeRegistry);
      return oceanPick;   // whole band is latitude-appropriate ocean, overriding land/beach/river
  }
  ```
  Apply to BOTH pick bodies (Registry `~L2610`, Collection `~L3170+`). Gated on `isMercator()` → Classic
  byte-identical. **Proof:** headless atlas with shape forced to Mercator shows ocean strips at the ±X edges,
  ocean type correct by Z, interior unchanged. (Needs a `-Dlatitude.debugForceShape=mercator` dev hook in
  `LatitudeBiomes` so the headless path can exercise the Mercator branch.)

- **1b. Terrain sink (LIVE-tuned — the amplitude DF wrapper, finally).** Forcing the *biome* to ocean does
  NOT lower the land — Terralith terrain is decoupled. Without sinking, the band is ocean-biome on possible
  mountains (jank). Use the runtime DF wrapper from `amplitude-df-wrapper-design-20260622.md` (hook
  `RandomState.create` → wrap `NoiseRouter.finalDensity` + `initialDensityWithoutJaggedness`, gated to
  `globe:overworld`): add a strong downward density bias when `|X|` is in the seam band, with a ramp so the
  coastline descends into the band instead of cliffing:
  ```
  wrapped = add(orig, mul(constant(-K_SINK), xEdgeGradient(|X|, X_RADIUS - BAND, X_RADIUS)))   // 0 interior → 1 at edge
  ```
  This is the same mechanism Peetsa deferred to an at-keyboard session; it needs live K/ramp/band tuning.
  K=0 / try-catch safe (failure → vanilla terrain). NOTE: this is "sink near the X edges," NOT periodicity —
  far simpler and lower-risk than periodic terrain (only 2 DF fields, additive bias, no cell-lattice fight).

### Piece 2 — X-wrap teleport (server · LIVE)
In the existing `END_SERVER_TICK` loop (next to `borderUxTick`, `GlobeMod.java ~L342`), for each player in the
globe overworld: if `getX() >= X_RADIUS` teleport to `X - 2*X_RADIUS` (and symmetric for the west), preserving
Y/Z/look/velocity; carry `getPassengers()`/vehicle. Force-load a far-edge chunk band (a `TicketType` via
`ServerChunkCache.addRegionTicket`) when a player nears the seam to cut the reload hitch. Free entities/
projectiles explicitly not handled (documented). Safe-landing nudge so you never wrap into a wall/void.

### Piece 3 — Remove E-W walls + border (server + client · mostly dead code)
- `GlobeMod.setGlobeBorder`: stop using the square `WorldBorder` as the E-W bound. (Square border can't be
  X-only, and we wrap on X anyway.) Either expand the border well beyond `X_RADIUS` so it never blocks before
  the teleport, or neutralize it — see Piece 4 for the N-S consequence.
- No-op the EW spawn clamp (`clampSpawnAwayFromEwWarning`, `EW_*` constants).
- Delete/disable dead EW visuals (per feasibility §6 Phase 4): `EwStormWallRenderer(+Mixin)`,
  `EwSandstormOverlayRenderer`, `FogRendererEwMixin`, the Sodium X-cull `RenderSectionManagerVisibilityMixin`
  (it would hide wrapped terrain), the EW arms of `GlobeWarningOverlay`/`GlobeClientState`/`LatitudeMath`
  hazard; reconcile `WorldRendererWorldBorderMixin`.

### Piece 4 — Custom N-S pole backstop (server · LIVE)
Removing/expanding the square border drops the N-S physical stop too. Re-add a hard Z-clamp at
`|Z| = latitudeRadius` (the `latitudeZRadiusOverride` Z value): in the same tick loop, block/teleport the
player back inside when crossing the pole. The pole hazard band already applies effects; this adds the hard
wall the square border used to provide. **Must be LIVE-verified that nothing lets a player slip past the poles.**

## Phasing
1. **Foundation + ocean biome band (1a)** — flag + `EW_SEAM_OCEAN_BAND` + the pick() gate + `debugForceShape`
   hook. **Map-proof (atlas, Mercator-forced):** ocean band at edges, correct by latitude, interior + Classic
   unchanged. SAFE / provable.
2. **Terrain sink (1b)** — the DF wrapper. LIVE session with Peetsa to tune K / ramp / band depth (matches the
   amplitude at-keyboard plan). Coherent with 1a (band is now real water).
3. **Teleport (2) + wall removal (3) + pole backstop (4)** — server + cleanup. LIVE-verified: cross
   east→arrive west in ocean (seam invisible), no E-W wall, poles still hard-walled, no crash/fall/suffocate.
4. **Polish** — far-edge preloading tuning, delete dead EW code, ensure no leftover EW fog/haze.

## Risks / open questions
- **Terrain sink is LIVE-tuned + DF-wrapper risk** (mapping-sensitive NoiseRouter rebuild; K=0/try-catch safe).
  This is Peetsa's flagged "at the keyboard" area → do Phase 2 with Peetsa available.
- **Pole backstop** (Piece 4) — must prove players can't slip past the poles once the square border is gone.
- **Band width vs render distance** — `EW_SEAM_OCEAN_BAND` ≥ max render distance or you'd glimpse your own
  land across the seam; live-tune.
- **Player + mount only wrap** — accept (standard compromise).
- **All new worlds now wrap** (Mercator ⇒ wraps). Confirm that's intended (vs a separate opt-in).
- Art II: port after canonical live-proof. Legacy-pin: flag off for Classic/existing saves.
