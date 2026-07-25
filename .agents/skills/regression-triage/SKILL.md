---
name: regression-triage
description: Read-only triage assistant for worldgen and system regressions. Use when a symptom needs to be mapped to the most likely pipeline stage and reduced to exactly one narrow next slice, especially for biome placement, seams, atlas output, or unclear bug location. Do not use for code changes, multi-fix brainstorming, or broad debugging spirals.
---

# Regression Triage

## Overview

Use this skill to turn a regression symptom into one likely pipeline stage, one primary failure mode, and one next slice only. It exists to prevent fix stacking and keep debugging focused.

## Read Only

- Do not modify code.
- Do not suggest multiple fixes.
- Do not stack slices.
- Do not guess wildly.
- Do not propose "try these 5 things".

## Canonical Pipeline Order

Always reason in this order:

1. latitude / band selection
2. candidate biome pool selection
3. terrain gates
4. humidity rules
5. coastal / river constraints
6. special biome overrides
7. weighted selection

Identify the most likely stage first and do not jump ahead unless the symptom clearly rules out earlier stages.

## Failure Modes

Choose one primary failure mode:

- quantized acceptance
- band drift or incorrect band resolution
- eligibility too early or too late
- hard gate artifact
- terrain misclassification
- coastal/river override bleed
- fallback or clamp contamination
- validator/tooling mismatch
- clean

## One-Slice Rule

Return exactly one recommended slice.

- Keep the slice to one subsystem.
- Make the slice describable in one sentence.
- Prefer upstream fixes over downstream clamps.
- Do not stack fixes across stages.

## Guard Rails

If multiple causes are possible, choose the most probable one and explicitly block broader follow-up ideas.

- Do not touch unrelated subsystems.
- Do not touch later pipeline stages if an earlier stage is the likely source.
- Do not touch multiple candidate fixes.
- Do not touch broad cleanup or refactors.

## Required Output

Restate:

- symptom
- most likely pipeline stage
- primary failure mode
- why that stage is responsible
- one recommended slice
- explicit do not touch list

## Typical Trigger Areas

- biome appears in the wrong place
- seams look wrong
- atlas output looks suspicious
- bug location is unclear
- there is pressure to try several fixes at once

## References

- See [examples](references/regression-examples.md) for symptom-to-stage mapping patterns and wording.
