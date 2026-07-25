---
name: workspace-cleaner
description: Read-only workspace hygiene checker for Git repos. Use when repo state feels cluttered, before handoff, savepoint, or release hygiene, or when you need to distinguish runtime junk, generated files, temp world files, and machine-specific clutter from real source edits. Do not use for deletion, git clean, reset, stash automation, or .gitignore edits.
---

# Workspace Cleaner

## Overview

Use this skill to inventory likely clutter in a Git workspace and classify it conservatively. It only inspects and recommends; it never deletes anything automatically.

## Read Only

- Do not delete files.
- Do not run `git clean`.
- Do not reset.
- Do not stash automatically.
- Do not modify `.gitignore`.
- Do not guess that unknown files are junk.

## Checks

Run these read-only checks:

- `git rev-parse --show-toplevel`
- `git status -sb`
- `git ls-files --others --exclude-standard`
- directory inspection for common clutter locations when present

## Clutter Buckets

When present, inspect common clutter buckets:

- `run/`
- `logs/`
- `latest.log`
- `crash-reports/`
- temp/export/output folders
- local world files
- generated atlas/report artifacts
- OS-specific junk
- editor temp files

## Classification

Classify each detected item as one of:

- runtime junk
- generated artifact
- world/state data
- likely source file
- unknown: review manually

## Output Shape

Always include:

- repo root
- branch
- HEAD short hash
- tracked dirty files
- untracked files
- likely clutter by bucket
- risky items to preserve
- likely safe manual cleanup candidates
- overall repo-noise level: low / medium / high
- recommendation

## Heuristics

- Never classify source files as junk.
- Treat world files, saves, and stateful run artifacts as risky unless obviously disposable.
- Treat logs and crash reports as usually safe to remove manually.
- Treat generated reports and exports as review-first, not auto-trash.
- If unsure, classify as `unknown: review manually`.
- Prefer false caution over aggressive cleanup.

## Relationships

- Use before `workspace-sync-handoff`.
- Use before `savepoint-guard`.
- Do not replace either skill.

## References

- See [examples](references/workspace-cleaner-examples.md) for conservative classifications and wording.
