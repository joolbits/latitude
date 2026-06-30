# World-creation book UI — implementation spec (next iteration)

`date: 2026-06-22` · target `src/main/java/com/example/globe/client/create/LatitudeCreateWorldScreen.java`
Builds on the recon control-flow map (`tmp/1.4.1-prep-20260621/ui/world-creation-bookui.md`) + the
approved mockup. Peetsa "likes" the book mockup but wants to see it in action — this restructure
needs a visual iteration loop (the agent can build+stage but cannot pilot the running game to verify
layout), so it is staged separately from the already-shipped squish fix + behavior changes.

## Already shipped this slice (in the staged jar, testable now)
- Real `scaledUi()` (was a no-op) → spacing compresses on short/high-GUI-scale viewports; scale 1/2 unchanged. Squish relief on the CURRENT layout.
- `expedition`→`world` user-facing strings (title "Create World", button "Create World", header, rail label, size desc).
- Vanilla Amplified world type (4th stepper option → `minecraft:amplified`).
- Bonus chest enabled for Latitude worlds (UI no longer greys it; launcher already passed the flag).

## Book restructure — to implement next (with visual loop)
Goal: 3 turnable spreads matching the mockup. Reuse the existing single-column "tabbed" machinery
(it already renders one page at a time and is well-exercised at high GUI scale) rather than rewriting
layout from scratch.

### Page model
- Replace `activeTab` semantics with `currentPage ∈ {0,1,2}`; rename `switchTab`→`goToPage`, `applyTabbedVisibility`→`applyPageVisibility`. Force the single-column page path ALWAYS (drop the 3-column branch / `tabbedMode` width test, or pin `tabbedMode=true`).
- Re-bucket widgets to the mockup pages (current tabs are World / Spawn Zone / Rules):
  - **P1 Identity**: `worldNameField`, `seedField`.
  - **P2 Planisphere**: planisphere (`LatitudePlanisphereRenderer` labelled render), zone rows / band picker, world-size stepper + description. (Restore a "Random" band option here — see legacy mixin note.)
  - **P3 Options**: world type stepper (incl. Amplified), game-mode stepper, commands/compass/bonus-chest toggles, optional difficulty, Game Rules… button.
- State already lives on the screen for the whole session → page turns lose nothing; snapshot `worldNameField`/`seedField` into mirrors at the top of `goToPage` (pattern already used in `cycleSize`).

### Navigation + framing (mockup)
- Page-turn affordances: left arrow (disabled P1), right arrow (disabled P3), 3-dot indicator centered on the spine; Left/Right or PageUp/PageDown when no EditBox focused. Reuse `handleTabClick` hit-testing.
- Begin (`Create World`) + Cancel on a persistent spine footer (every page), not page-bound.
- Optional decorative left leaf (title + small art / spinning compass) per the mockup; collapses on narrow widths (one-leaf breakpoint replaces the width test).

### Responsive layout
- `scaledUi` is now real — lay each page out via book-rect fractions + a row-budget (distribute `bookH` across rows with `minRowH` floor; single vertical scroll only if it overflows, via existing `drawPaneScrollbar`). No literal `+38/+40` offsets.

### Compass tease on bespoke loading screen ("maybe", needs visual placement)
- In `LevelLoadingScreenLatitudeOverlayMixin`, render a small analog compass disc (one of the new
  themes) as a hint that the compass is customizable. `CompassHud.drawAnalogCompass` is private + takes
  a `GuiGraphicsExtractor` — expose a small public `renderPreview(GuiGraphics, x, y, size, theme)` helper
  rather than duplicating the draw. Placement/scale need a visual check.

### Cleanup (verify-then-delete)
- Dead legacy mixins `CreateWorldScreenLatitudeToggleMixin` + `CreateWorldScreenSpawnZoneMixin` (vanilla-init TAIL never fires under the redirect HEAD-cancel) — the only home of the "Random" band; fold Random into P2 then retire them.

## Proof
compile + build + jar-verify + Peetsa live launch at GUI scale 1/2/3/4 (no overlap; page turns keep
state; each world type launches; bonus chest spawns in a Latitude world). Art II: port to the version chain.
