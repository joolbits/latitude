---
name: atlas-audit
description: Read-only analyzer for Latitude atlas and headless proof artifacts. Use when you have atlas or report outputs and need a structured audit of what looks wrong, what category of wrong it is, and whether the evidence suggests real worldgen regression versus validator or viewer/report drift. Do not use for rerunning jobs, rebuilding, file modification, or multi-fix brainstorming.
---

# Atlas Audit

## Overview

Use this skill to inspect existing atlas and proof artifacts, summarize the important findings in plain English, and decide whether the likely next slice is worldgen, validator sync, or viewer/report-only.

## Read Only

- Do not rerun atlas jobs.
- Do not rebuild.
- Do not modify files.
- Do not propose multiple fix slices.
- Do not treat validator output as automatically authoritative if other evidence conflicts.

## Inputs To Inspect

When present, inspect:

- `world_biome_inventory.json`
- `seam_temperate_composition.txt`
- `seam_rows.txt`
- `seam_row_markers.txt`
- `seam_band_legend.txt`
- biome list or text summaries
- atlas-generated report text files
- related viewer or export summaries

## What To Report

Identify what files are present, then summarize the most important findings in plain English.

- biome presence or absence
- suspicious seam behavior
- confetti or scatter patterns
- first-seen band violations
- validator or report mismatch
- likely tooling-only issue

## Classification

Choose one primary read of the artifact set:

- biome missing or starved
- suspicious seam / abrupt boundary
- confetti / scatter artifact
- band leakage
- validator drift
- viewer-only/report-only issue
- clean / no actionable issue

## Locus

Set one likely locus:

- worldgen logic
- atlas validator drift
- viewer/report-only issue
- inconclusive

If inventory contradicts seam output, say so directly. If a biome is present but rare, distinguish missing from underrepresented. If the report appears stale or mismatched to doctrine, classify possible validator drift.

## One Slice

Recommend exactly one next slice only.

- Keep it to one subsystem.
- Do not recommend broad multi-axis fixes.
- Prefer the most evidence-backed slice.

## Required Output

Include these sections:

- artifact set found
- key findings
- primary classification
- confidence level: low / medium / high
- likely locus
- one recommended next slice
- explicit do not touch boundaries

## Heuristics

- If evidence is mixed, choose the strongest primary read and say what remains uncertain.
- If a report is stale or mismatched, call out validator drift as a possibility.
- If the artifact set is clean, say so plainly.
- Do not recommend multiple competing fixes.

## References

- See [examples](references/atlas-examples.md) for artifact patterns and wording.
