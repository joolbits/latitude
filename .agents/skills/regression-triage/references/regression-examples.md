# Regression Triage Examples

## Savanna Appearing In Temperate Band

- most likely pipeline stage: latitude / band selection
- primary failure mode: band drift or incorrect band resolution
- recommended slice: inspect the band-to-biome mapping before any downstream eligibility or override logic
- do not touch: humidity, coastal/river overrides, or weighted selection

## Biome Seams Look Like Straight Lines

- most likely pipeline stage: candidate biome pool selection or terrain gates
- primary failure mode: quantized acceptance
- recommended slice: inspect the acceptance boundary for one stage only
- do not touch: special overrides, later clamps, or unrelated rendering cleanup

## Confetti Pale Garden Patches

- most likely pipeline stage: special biome overrides or weighted selection
- primary failure mode: fallback or clamp contamination
- recommended slice: inspect the override path before adding new downstream fixes
- do not touch: band logic, unrelated worldgen stages, or broad rerouting

## Atlas Output Looks Suspicious

- most likely pipeline stage: validator/tooling mismatch or atlas/tooling stage
- primary failure mode: validator/tooling mismatch
- recommended slice: inspect the atlas-generation contract before changing atlas consumers
- do not touch: worldgen pipeline stages unless the diff proves the atlas is only exposing upstream data

## Unclear Bug Location

- most likely pipeline stage: the earliest stage that the symptom can support
- primary failure mode: clean until proven otherwise
- recommended slice: isolate one subsystem and one stage
- do not touch: multiple speculative fixes, broad refactors, or downstream clamps
