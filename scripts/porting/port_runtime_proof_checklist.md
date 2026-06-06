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
