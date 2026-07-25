# Atlas geography overlay plan (design only, not implemented)

`status: plan, no implementation` · `date: 2026-07-03` · `depends on: tools/atlas/geography_analyzer.py`

Per `docs/LATITUDE_2_0_OVERHAUL.md` Phase 1's action plan: "Add Atlas overlay plan after CLI
report is trusted." The CLI report (`geography_analyzer.py`) is now trusted (19 synthetic-fixture
tests green, cross-checked against the documented old-red numbers). This document plans the visual
overlay; it does not implement one. Implementation is a separate, later slice — likely folded into
Phase 2 once GeoAuthority gives the overlays something new to show, rather than done standalone now.

## Why not implement overlays this phase

The CLI report already answers "how bad is the current red, precisely" without any rendering work.
An overlay's value is investigative — spotting *where* a landmass/ocean-basin boundary looks wrong
by eye — which matters more once Phase 2 starts iterating on GeoAuthority macro-geography output.
Building the overlay against the current no-op geography (Phase 0's `GeoSummary.NEUTRAL`) would
have nothing interesting to render yet.

## Planned overlay layers (for Phase 2, not now)

1. **Component-id overlay.** Recolor each connected land component (and separately, each
   ocean-basin component) with a distinct hue from `connected_components()`'s labels, so a human
   can see at a glance whether "one land component at 95%" is one sane continent or one
   accidentally-bridged mess of should-be-separate landmasses.
2. **Coast-distance overlay.** Grayscale/heatmap of distance-to-nearest-water, reusing the existing
   `coast_mask()` boolean as the zero-distance ring and extending outward (a real distance
   transform, not just adjacency) once `GeoSummary.coastDistanceBlocks` has a real implementation
   behind `latitude.geoV2.enabled`.
3. **Area-weight overlay.** A cosine-latitude shading overlay (darker near the poles) so viewers
   stop eyeballing raw pixel area on the flat 2:1 raster and misjudging how much a polar ocean band
   "really" represents.
4. **Projection-edge composition strip.** A thin column-chart strip along the west/east edges of
   the existing Atlas viewer (`tools/atlas/viewer.html`) showing the per-band land/ocean/river split
   that `projection_edge_composition()` already computes headlessly — turns the existing CLI numbers
   into an at-a-glance boundary-health check without a full re-render.

## Non-goals

- No live client-side rendering. This stays in the same headless/dev-tool space as the rest of
  `tools/atlas/` — Atlas Viewer is dev-only (see `[[atlas-viewer-dev-only]]` in memory), not a
  release feature.
- No terrain-height overlay until an `--emitHeight` run exists to back it with real terrain-water
  truth (see `geography_analyzer.py`'s explicit "biome water, not terrain water" caveat).
- No new noise/authority algorithm smuggled in as "just an overlay" — if an overlay needs a real
  geography signal that doesn't exist yet, that signal is Phase 2/3 work, and the overlay waits for
  it.

## Trigger to revisit

Once Phase 2 (GeoAuthority Prototype) has real (non-neutral) `GeoSummary` output behind
`latitude.geoV2.enabled=true` on an opt-in branch, revisit this plan and scope the first overlay
(most likely #1, component-id) as its own small slice with its own working card.
