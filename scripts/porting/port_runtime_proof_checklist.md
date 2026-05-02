# Latitude Runtime Proof Checklist

Use this checklist after the bootstrap audit and before any release or savepoint decision.

## Compile And Build

- [ ] `compileJava`
- [ ] `clean build`
- [ ] confirm the build is green without assuming that means runtime proof is complete

## Fresh World Runtime Proof

- [ ] run `runClient` from a fresh world
- [ ] record timing markers:
  - [ ] Begin click
  - [ ] overlay first render
  - [ ] spawn prep
  - [ ] login
  - [ ] overlay clear
  - [ ] joined game
- [ ] grep logs for `Can't keep up` warnings
- [ ] prove live biome hook logs or gated markers

## Existing-World Loading

- [ ] run `runClient` from an existing save
- [ ] confirm the bespoke Latitude overlay appears there too
- [ ] confirm the overlay clears when `LevelLoadingScreen` closes

## Client UI Parity

- [ ] press `F9` and confirm it opens the intended settings/front-door screen
- [ ] verify old capture, CSV, and dev controls do not appear in public F9
- [ ] open HUD Studio and check widgets, scrollbars, and previews are crisp
- [ ] confirm settings and HUD Studio suppress vanilla blur/background leaks
- [ ] press `F1` and confirm the expected HUD visibility behavior remains correct

## EW Haze HUD Readability

- [ ] test near the active EW border using the current world radius, not hardcoded regular-world coordinates
- [ ] remember the edge test starts near `R - 500`
- [ ] confirm haze does not bury hotbar or warning text
- [ ] note whether opening chat makes the haze disappear and the hotbar visible
- [ ] keep EW haze proof separate from F9/loading proof

## /latdev Smoke Proof

- [ ] `/latdev help`
- [ ] `/latdev here`
- [ ] `/latdev tpBand tropical center + here + probe`
- [ ] `/latdev tpBand temperate center + here + probe`
- [ ] `/latdev tpBand subpolar center + here + probe`

## World-Size Sanity

- [ ] run one non-`Regular` world-size check before release
- [ ] make sure the proof matches the intended world-size scaling

## Artifact Purity

- [ ] scan the public jar before upload
- [ ] confirm it does not contain dev, headless, tooling, or process-launch classes
- [ ] confirm provenance matches the expected commit and release target

## Release Reminder

- Build success is not release proof.
- Atlas is evidence, not absolute truth; if fresh runtime disagrees, the runtime wins.
