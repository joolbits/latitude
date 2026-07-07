# HUD & UI Overhaul Design — pin-and-grow layout, truthful Studio, one front door (2026-07-07)

`status: APPROVED r3 — Peetsa approved ALL implementations 2026-07-07 ("I absolutely love all of these
ideas and approve their implementations"); r3 adds Pillar 6, the "Chartroom" visual language for the
create/loading screens ("prettier and smoother, meaningful for Lat 2.0"). U-E absorbs Pillar 6.
Implementation in progress; the UI test jar is built but HELD until the parallel worldgen Slice E live
session (TEST 27) concludes, so staging can't clobber an in-flight test` · companion evaluation: `../binder/ui-audit-20260707.md` (every claim there is
Read-verified with file:line) · honors the 2026-06-22 create-screen decision: the create screen gets
refinements only, no new direction.

> **r2 revision note (same day):** Peetsa approved Pin & Grow and asked for: (1) free player placement
> confirmed as a first-class property; (2) an "attach to hotbar" dock (compass + text to the RIGHT of the
> hotbar, never off-screen, never clipping the hotbar — the historical failure); (3) improved compass
> visuals beyond color themes; (4) better organization/flow for the Settings/Studio menus; (5) a deeper
> improvement pass on the create + loading screens. All five are folded in below. The historical clipping
> bug is now precisely understood: `computeAttachedCompassPosition` CENTERS the analog dial ON the hotbar
> (`CompassHud.java:589-592` — a ~74px dial into the 22px hotbar row), which is why analog attach was
> disabled outright in commit `4778a5ed`; the digital path (:594-601) side-teleports to the hotbar's LEFT
> when text outgrows the right side, and accounts for neither the offhand slot (renders RIGHT of the
> hotbar for left-handed players) nor the attack-indicator-on-hotbar. The dock design below makes hotbar
> overlap structurally impossible rather than tuned-around.

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

**Free placement is unchanged and first-class:** dragging in the Studio moves the pin — anywhere on
screen, snap-to-grid optional. Pin & Grow does not constrain WHERE elements go; it only makes WHERE
independent of content width.

**Migration:** on first load of an old config, compute each element's CURRENT effective position using
the old math with the old sample text, convert to pin+grow (grow defaults to the old anchor side), bump a
new `configVersion` field (the config finally gets one). Behavior-preserving on day one; stable forever
after. `sanitize()` clamps pins into the visible screen at load AND save (kills the invisible
out-of-range-offset class).

### 1b. The hotbar dock (requested; replaces `attachToHotbarCompass`)

A dock is a special pin that is COMPUTED each frame from live HUD geometry instead of stored:

- **Dock point:** `dockX = screenW/2 + 91 + offhandRight + attackIndicatorRight + GAP(6)`, where
  `offhandRight = 29` only when the player's main hand is LEFT (vanilla renders the offhand slot on the
  opposite side), and `attackIndicatorRight = 22` only when the attack-indicator setting is HOTBAR (it
  draws right of the hotbar). `dockY =` vertical center of the hotbar row (`screenH - 11`). These two
  conditional offsets are exactly the overlaps the old implementation missed.
- **Grow: rightward only.** The dial is left-aligned AT the dock point; text sits right of the dial (or
  stacked beneath it), left-aligned — growth is structurally AWAY from the hotbar. No configuration can
  make the box extend left into the hotbar, so the historical clipping class is unreachable, not patched.
- **Fit ladder (deterministic, no side-teleporting):** available width = `screenW - dockX - margin`.
  If dial + longest text doesn't fit: (1) stack the text under the dial (half the width); still no fit →
  (2) scale the dial down to a floor (e.g. 48px); still no fit (extreme GUI scale) → (3) lift the whole
  dock to sit ABOVE the hotbar's right end, right-aligned to the screen edge — above the offhand/armor
  row, never overlapping. Each rung is a pure function of screen size + reserved text width, so the dock
  never jumps because a biome NAME changed — only when the SCREEN changes.
- Works for both looks (this re-enables analog attach, closing the `4778a5ed` disablement) and for the
  satellites: zone/biome/coords in dock mode join the dock's own layout flow instead of free pins.
- The compass-ITEM gate stays orthogonal: `showMode=COMPASS_PRESENT` continues to control visibility;
  the dock only controls position. (Today's attach silently requires a compass item in the hotbar to
  attach at all — position and visibility rules were tangled; they separate.)

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

## Pillar 2b — compass looks: visuals beyond color (requested)

A new `CompassLook` axis, orthogonal to the existing 12 color themes (theme = palette; look = shape).
Five looks, all sharing the pin/dock/needle/degree machinery:

- **DISC** — today's filled dial (kept as default; migrates silently).
- **RING** — open bezel: rim ticks + cardinal letters with the world visible through the center; reads
  lighter, doesn't block view at large sizes.
- **ROSE** — an 8-point compass rose (cartographic star, alternating long/short points), matching the
  planisphere/atlas identity of the mod; the needle becomes the north point's highlight.
- **TAPE** — a horizontal bearing strip (FPS-style): fixed center caret, cardinal + intercardinal letters
  and tick marks sliding with yaw, degree readout beneath. Pairs naturally with the hotbar dock and with
  top-center placement; inherently width-stable (fixed strip width setting).
- **MINIMAL** — needle + N glyph only, no chrome; for players who want a whisper.

**Rendering change that makes this feasible:** the current disc is drawn with one `fill` per pixel
(~4k-16k quads/frame — the Pillar-4 finding). Looks are instead baked as a small texture atlas in mod
resources (`assets/globe/textures/gui/compass/<look>.png`, one file per look, needle/ticks/degree text
still drawn procedurally in theme colors on top). One `blit` per frame instead of thousands of fills,
AND resource packs can reskin every look without touching code — the modding-culture win. Procedural
fallback stays behind a flag for packs that delete the textures.

Studio integration: the Compass page gets a Look selector rendered as five live thumbnails (the preview
machinery already renders the real draw path — each thumbnail is the real renderer at small scale).

## Pillar 3 — one front door (F9 → Studio) + Studio IA v2 (requested: "convoluted and clunky")

**Why it feels clunky today:** settings for ONE element are scattered across CONCERN-tabs — the Compass
tab holds style/theme, the Placement tab holds anchors for everything, the Title tab holds title style,
General mixes global and per-element toggles — and a second screen (F9 Settings) duplicates five fields
with different save semantics. Configuring "the biome readout" means visiting three tabs on two screens.

**IA v2 — element-centric pages.** One surface (the Studio), F9 opens it. Sidebar (or tab row) of
ELEMENTS matching the Pin & Grow model, so the mental model and the menu are the same shape:

- **Compass** — visibility + show-mode · Look (5 thumbnails) · Theme/colors · size/scale ·
  Placement (pin/grow/dock card + "drag in preview" hint + per-element reset) · degree readout options
  (incl. the currently-hidden-but-live `directionMode` knob, surfaced here or deleted).
- **Zone & Biome** — visibility, attach-to-compass vs free pin, order (biome↔zone), compact separator,
  rainbow, placement card.
- **Coordinates** — lat/lon toggles, format, attach vs free pin, placement card.
- **Title (zone entry)** — enable, duration, style (color/rainbow/case/spacing), draggable toggle,
  placement card.
- **General** — global HUD scale, show mode, warning messages, preview text source (Sample / Longest /
  Live), Reset ALL, and layout presets (save/load named layouts — small, optional, high delight).

Every page = the same top-to-bottom rhythm: *Visibility → Look → Placement → Extras*. The preview stays
live behind the panel the whole time (existing machinery). Single save model: changes apply live,
persist on close, one Revert button — killing the Esc-loses-Settings-edits asymmetry by having exactly
one screen with exactly one rule. `LatitudeSettingsScreen` is deleted (or one release as a redirect
stub).

## Pillar 3 (continued) — config hygiene

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

## Pillar 5 — create screen + loading refinements (deeper pass, requested; respects the 06-22 decision)

**Create screen** (healthy per the audit — these sharpen, none redirect):
1. **Seed-0 guard** (top accuracy item, ties to worldgen audit P0-2): as-you-type amber hint under the
   seed field when the parsed seed is literally `0` — "Seed 0 disables Latitude's geography engine — pick
   any other number, or leave blank for random." On launch attempt with 0: one confirm dialog. Never
   silently launch an inert world from the bespoke screen.
2. Seed affordances: a randomize (die) button and a copy-seed button on the field, if not already
   present — cheap, standard, and pairs with the guard.
3. Size selector honesty: fix/retire the stale `GlobeWorldSize.label` square block-counts (the screen
   already computes honest Mercator-aware dims via `worldDimsLabel`); add one info line tying size to
   meaning — "≈ N blocks pole-to-pole · M-block latitude bands" — so size reads as gameplay, not just
   numbers. Log `size.worldPresetId` at launch (makes the UI-Small=`globe_regular` preset trap observable
   in every log/report).
4. Planisphere upgrades that stay honest AND cheap (no GeoAuthority): latitude gridlines with degree
   labels at the real band edges (23.5°/35°/50°/66.5°) — the create screen becomes a quiet teacher of the
   zone system; spawn-band highlight stays. The REAL land-mask preview remains a flagged future feature
   (needs debounced background sampling at ~64×32) — not in this overhaul.
5. Wire-or-inline the `scaledUi`/`compactUi` identity no-ops; per-tab scroll memory in the narrow
   (tabbed) fallback layout; keyboard navigation across panes.
6. Restyle `SpawnZoneScreen` to the bespoke look (the last vanilla-look Latitude screen).

**Loading screen** (honest and robust per the audit — additions are informational, not structural):
7. **World summary card** during load: shape · size · the typed seed — reinforces the bespoke identity
   with data already in hand, and doubles as L20-friendly evidence in screenshots.
8. **Truthful phase label**: the render gate already runs discrete stages (player settle → spawn-ring →
   renderer warmup → render signal) — surface the CURRENT stage as the status line instead of generic
   flavor phrases. If a fail-safe tier (6s/15s) fires, say "taking longer than usual…" rather than
   staying silent; flavor phrases can remain as a secondary rotating line.
9. Remove (or actually render) the dead `globe$displayProgress` field; keep the progress bar strictly
   bound to vanilla `smoothedProgress` (already true — preserve in any refactor).
10. Delete the dead set (`OverlayProof`, `ZoneEntryNotifier` + `ui/ZoneTitleOverlay`,
    `EwSandstormOverlayRenderer` + its stale import, `GlobeModMenu`, old planisphere `render()`) —
    subtractive commit, compile + S=0 byte-identity gate.

## Pillar 6 — the "Chartroom" visual language for create + loading (r3, requested: "prettier and smoother, meaningful for Lat 2.0")

Structure stays exactly as-is (the 06-22 decision holds); this is a visual and motion elevation with one
coherent identity: **a cartographer's chartroom** — the aesthetic the planisphere, the atlas frame, and
the gold compass already gesture at, made consistent and alive.

**Tokens** (formalized from what's already in use): latitude gold `#E8B64A` (accents, focus, needles) ·
deep-sea `#1E3A5F` (ocean fills) · parchment `#E8DCC0` on ink `#2B2620` (map surfaces) · slate panel base
matching MC dark UI. One 9-slice map-frame border used by EVERY bespoke panel (create screen, Studio,
loading plaque, restyled SpawnZoneScreen) with small compass-rose corner ornaments on major panels only.
Section headers: gold smallcaps with a thin rule — the same hierarchy everywhere.

**Motion inventory** (each ≤200-300ms, every one gated on reduced-motion/accessibility):
1. Screen open: panels fade + rise 8px, staggered left→planisphere→right (~40ms stagger).
2. Shape toggle: the planisphere ASPECT animates (200ms lerp) between 2:1 and 1:1; band lines slide with
   it — the toggle becomes a little moment instead of a swap.
3. Size change: the world-dims label rolls to its new numbers; planisphere border gives a single soft pulse.
4. Hover: 1px gold underglow fade-in on interactive elements; press: brief fill sweep.
5. Seed randomize: die button spins ~300ms; the seed text settles new digits in.
6. Create: the button fills gold left→right, then the screen crossfades into loading (no hard cut).
7. Loading: a compass rose DRAWS ITSELF in (stroke sweep) as the centerpiece; the progress bar is a
   latitude band that fills through the band colors (tropics→polar) as real progress advances; the
   truthful phase captions (Pillar 5 #8) crossfade beneath; the world summary plaque (Pillar 5 #7) slides
   up when the render gate reaches its final stage; 300ms fade into the world at reveal (the render gate
   already guarantees no void frame — the fade makes the handoff feel intentional).

Progress stays strictly bound to vanilla's real `smoothedProgress`; motion NEVER masks state (the
truthful phase labels are the source of truth, the animation is their presentation).

## Proposed slice plan r2 (fix-all-then-test-once, per the standing workflow)

- **U-A — layout model + dock**: pin+grow + satellites + normalized storage + versioned migration + the
  hotbar dock (offsets, fit ladder, analog re-enable). Gate: mechanical — a layout unit probe asserting
  (a) pin invariance under a biome-name sweep (longest/shortest names, all 9 anchors, scales 1-4) and
  (b) dock non-overlap invariants (dock box ∩ hotbar/offhand/attack-indicator rects = ∅ across screen
  widths 320-3840, both hand modes, both indicator modes); compile+test; S=0 byte-identity untouched.
- **U-B — truthful Studio + IA v2**: element-centric pages, F9→Studio + Settings fold-in/delete,
  text-source selector, pin visualization, one drag model, show-mode honesty, cosmetic truth,
  per-element reset, title hit-test fix. Gate: Studio-vs-live position parity probe (place at known
  pins, assert live bounds equal Studio bounds for Longest text) + duplicated-field list empty.
- **U-C — config hygiene**: configVersion + defaults unification + dead-field pruning + `directionMode`
  decision + unified save model. Gate: config round-trip tests (old file → migrated → stable).
- **U-D — compass looks + render hygiene**: the 5-look system with texture-atlas rendering (replacing
  per-pixel fills), state-change string caching, world-switch resets, boss-bar default nudge for fresh
  configs. Gate: compile + a frame-cost sanity probe (fills-per-frame counter) + look thumbnails render
  in Studio.
- **U-E — create + loading refinements**: seed-0 guard + seed affordances, size-label fix + preset-id
  log, planisphere band gridlines, loading summary card + truthful phase label, dead-class deletion,
  SpawnZoneScreen restyle. Gate: compile + S=0 byte-identity + worldgen suite untouched.
- Then ONE live UI pass on a single staged TEST jar: the ~15-minute checklist (drag each element with a
  long-name biome underfoot; hotbar dock with offhand + hotbar attack indicator + narrow window; GUI
  scale 1→4→Auto; resize; dimension hop for title reset; F9 flow; each compass look; create-screen
  seed-0 hint; loading phase labels; boss-bar clearance).

Estimated shape: U-A and U-B are the substance; U-C mechanical; U-D is the visual craft slice; U-E is a
half-day of small wins. Nothing here touches worldgen, terrain, or the consumer — fully parallel to the
Phase-4/5 track.
