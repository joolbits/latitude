---
name: diff-explainer
description: Read-only Git diff explainer for changelogs, PR summaries, slice review, and scope checks. Use when you need plain-English explanations of working tree diffs, staged diffs, or comparisons between two refs, with subsystem grouping and risk/scope assessment. Do not use for commit, tag, push, stash, checkout, rebase, reset, or file modification.
---

# Diff Explainer

## Overview

Use this skill to understand what changed in a Git repo without mutating state. It explains diffs in plain English, grouped by subsystem and risk, so you can write changelogs, PR summaries, or do a scope check before committing.

## Read Only

- Inspect diffs only.
- Do not commit.
- Do not tag.
- Do not push.
- Do not stash.
- Do not checkout.
- Do not rebase.
- Do not reset.
- Do not modify files.

## What To Explain

- Unstaged changes
- Staged changes
- Diff between two refs
- Current `HEAD` versus working tree
- File-by-file changes in normal English

## Required Commands

- `git status -sb`
- `git rev-parse --show-toplevel`
- `git rev-parse --abbrev-ref HEAD`
- `git rev-parse --short HEAD`
- `git diff --stat`
- `git diff --cached --stat`
- `git diff --name-only`
- `git diff --cached --name-only`
- `git diff`
- `git diff --cached`
- `git diff <ref1> <ref2> --stat`
- `git diff <ref1> <ref2> --name-only`
- `git diff <ref1> <ref2>`

## Output Shape

Always include:

- repo root
- branch
- HEAD short hash
- compared scope: working tree, staged, or two refs
- changed files
- subsystem grouping
- plain-English explanation
- risk level: low / medium / high
- scope read: narrow / broad

## Grouping Rules

Group changes by subsystem when possible:

- worldgen Java logic
- client/UI/rendering
- atlas/viewer/tooling
- docs/specs/reference
- build/config/mixins
- runtime/generated/log/world files

Call out whether the diff is:

- behavior change
- tooling-only change
- doc-only change
- config/build change

## Risk Heuristics

- Flag likely risky mechanisms:
  - band logic
  - fallback/clamp/sanitize logic
  - mixins
  - build/config
  - rendering order
  - tooling/report-only changes
- Say if the diff looks broad or narrow.
- If multiple subsystems changed, call that out directly.
- Do not invent intent that is not supported by the diff.
- Distinguish confirmed changes from probable implications.

## References

- See [examples](references/diff-examples.md) for common prompts and expected output shape.
