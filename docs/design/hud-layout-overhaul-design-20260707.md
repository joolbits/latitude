# HUD & UI Overhaul Design — pin-and-grow layout, truthful Studio, one front door (2026-07-07)

`status: PROPOSED — awaiting Peetsa's approval; no implementation yet` · companion evaluation:
`../binder/ui-audit-20260707.md` (every claim there is Read-verified with file:line) · honors the
2026-06-22 create-screen decision: the create screen gets refinements only, no new direction.

## Design goal, in one sentence

Make every HUD element's position depend on nothing but its anchor — never on how long today's biome
name happens to be — and make the Studio show exactly what the game will show.

## Pillar 1 — the "pin + grow" layout model (fixes the compass shift at the root)

**Today:** one box measures dial + all attached text; the anchor equation consumes the box width
(`anchoredX = (screenW - boxW)/2`), so content width moves everything, dial included.

**Proposed:** every HUD element (compass, zone, biome, coords, title) becomes an independent element with
three stored properties:

1. **Pin** — WHERE it lives: a 9-grid screen anchor (L/C/R × T/M/B) + an offset stored in
   scale-independent units (fraction of gui-scaled screen, quantized; not absolute pixels). The pin is a
   POINT, not a box.
2. **Grow** — HOW content extends from the pin: alignment per axis (left/center/right × top/middle/
   bottom). A center-grow element widens symmetrically around its pin; a left-grow element's left edge
   never moves. Content width no longer participates in WHERE the pin is — only in how far the box
   extends from it.
3. **Parent (optional)** — satellite anchoring: "attached" zone/biome/coords become elements pinned to a
   NAMED EDGE of the compass element (below-center, right-of, etc.) instead of being concatenated into
   the compass's measured line. The dial's pin is fixed; satellites grow away from it; nothing feeds back.

**Effect on the complaint:** the dial never moves again, at any anchor, for any biome name. A
center-grown biome label breathes symmetrically beneath a stationary dial — visually calm. Optional
**reserved-width mode** per text element (measure the longest biome display-name in the registry once per
world join) makes even the text box fully static for players who want zero motion.

**Migration:** on first load of an old config, compute each element's CURRENT effective position using
the old math with the old sample text, convert to pin+grow (grow defaults to the old anchor side), bump a
new `configVersion` field (the config finally gets one). Behavior-preserving on day one; stable forever
after. `sanitize()` clamps pins into the visible screen at load AND save (kills the invisible
out-of-range-offset class).

## Pillar 2 — the truthful Studio

The preview already renders through the real `CompassHud` code — only its inputs lie. Fix the inputs:

- **Text source selector** in the Studio: `Sample` (short, current behavior) / **`Longest`** (real
  longest biome name + longest zone word + widest coords — the honest worst case, default) / `Live`
  (real values when opened in-world). Hitboxes always use the same source as the render.
- **Pin visualization**: a small crosshair at each element's pin + a ghost outline at reserved/longest
  width, so "what can this grow into" is visible while dragging.
- **One drag model**: dragging moves the PIN (identical semantics for compass, title, and every
  satellite — replaces today's top-left-grab vs center-grab split). Snap-to-grid snaps the pin.
- **Show-mode honesty**: if `showMode` would hide an element in-game, the Studio dims it and badges
  "hidden in-game (show mode)" instead of rendering it as if visible.
- **Cosmetic truth**: drop the checkerboard/fake-hotbar mismatch (render the preview over the actual
  in-world backdrop when opened in-world; a flat dark backdrop in the main menu), and stop drawing
  borders live elements don't have.
- **Title hit-test** measures the actual styled title (case/letter-spacing aware), not `"TROPICAL 0°"`.
- **Per-element reset** (and per-tab), alongside the existing global reset.

## Pillar 3 — one front door (F9 → Studio) + config hygiene

- **F9 opens the Studio.** Fold the two Settings-only fields (`showMode`, `showWarningMessages`) into the
  Studio's General tab; `LatitudeSettingsScreen` is deleted (or left one release as a stub that opens the
  Studio). This removes the five duplicated fields, the Esc-loses-Settings-edits asymmetry, and the
  `compactHud` visibility mismatch in one move. No config migration needed — same fields.
- **Config model**: add `configVersion`; give `LatitudeConfig` a `fresh()`-style single default source
  (ending the three-hardcoded-default-sites drift class); unified save-on-close semantics everywhere;
  prune the dead fields (`zoneEntryNotifyMode`, `showLatitudeDegrees`, `latitudeBandBlend*`,
  `enableEwStormWall`, `debugLatitudeBlend`, both dead compass-degree flags) via one-time migration;
  either surface `directionMode` in the Compass tab or remove it — no hidden live knobs.

## Pillar 4 — render hygiene (invisible, but overdue)

- Compose HUD strings/components on STATE CHANGE (biome/zone/coords/position tick), not per frame; cache
  the biome lookup per block-position change.
- Draw the analog disc as scanline spans (O(diameter) fills) or one cached texture — not one fill per
  pixel (today ~4k-16k quads/frame at max size).
- Clear `ZoneEnterTitleOverlay` statics + `cachedHasCompass` on disconnect/world-switch (the existing
  `ClientPlayConnectionEvents.DISCONNECT` handler in `GlobeModClient` is the hook).
- Nudge the default compass position out of the vanilla boss-bar band (only for FRESH configs; existing
  pins untouched).

## Pillar 5 — create screen + loading refinements (small; respects the 06-22 decision)

1. **Seed-0 guard** (top accuracy item, ties to worldgen audit P0-2): as-you-type amber hint under the
   seed field when the parsed seed is literally `0` — "Seed 0 disables Latitude's geography engine — pick
   any other number, or leave blank for random." On launch attempt with 0: one confirm dialog. Never
   silently launch an inert world from the bespoke screen.
2. Fix/retire the stale `GlobeWorldSize.label` square block-counts (screen already computes honest
   Mercator-aware dims); log `size.worldPresetId` at launch (the UI-Small=`globe_regular` trap becomes
   observable in every log).
3. Delete the dead set (`OverlayProof`, `ZoneEntryNotifier` + `ui/ZoneTitleOverlay`,
   `EwSandstormOverlayRenderer` + its stale import, `GlobeModMenu`, old planisphere `render()`, loading
   overlay's unused `globe$displayProgress`) — subtractive commit, compile + S=0 byte-identity gate.
4. Wire-or-inline the `scaledUi`/`compactUi` identity no-ops; restyle `SpawnZoneScreen` to the bespoke
   look (it's the last vanilla-look Latitude screen).
5. Planisphere: stays decorative (it is honest about shape/size/latitude/spawn-band). A real
   GeoAuthority land-mask preview is noted as a FUTURE feature behind cost analysis (needs debounced
   background sampling at ~64×32) — not part of this overhaul.

## Proposed slice plan (fix-all-then-test-once, per the standing workflow)

- **U-A — layout model**: pin+grow + satellites + migration + normalized storage. Gate: mechanical — a
  layout unit probe asserting pin invariance under biome-name sweep (longest/shortest names, all 9
  anchors, scales 1-4); compile+test; S=0 byte-identity untouched (HUD-only).
- **U-B — truthful Studio**: text-source selector, pin visualization, one drag model, show-mode honesty,
  cosmetic truth, per-element reset, title hit-test. Gate: Studio-vs-live position parity probe (place at
  known pins, assert live bounds equal Studio bounds for Longest text).
- **U-C — front door + config hygiene**: F9→Studio, Settings fold-in, configVersion + defaults
  unification + dead-field pruning + `directionMode` decision. Gate: config round-trip tests (old file →
  migrated → stable), duplicated-field list empty.
- **U-D — hygiene + refinements**: render-path caching + disc batching, world-switch resets, boss-bar
  default nudge, seed-0 guard, size-label fix, dead-class deletion, SpawnZoneScreen restyle. Gate:
  compile + S=0 byte-identity + the worldgen suite untouched.
- Then ONE live UI pass on a single staged TEST jar: the 10-minute checklist (drag each element with a
  long-name biome underfoot, GUI scale 1→4→Auto flip, resize, dimension hop for title reset, F9 flow,
  create-screen seed-0 hint, boss-bar clearance).

Estimated shape: U-A and U-B are the substance (roughly a day's focused work together); U-C/U-D are
mechanical. Nothing here touches worldgen, terrain, or the consumer — fully parallel to the Phase-4/5
track.
