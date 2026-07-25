---
name: savepoint-guard
description: Read-only pre-flight guard before using the savepoint skill. Use when deciding whether the current Git repo is safe and narrow enough for a one-slice savepoint, with a clear safe-or-stop decision based on repo root, branch, HEAD, staged and unstaged changes, and generated junk. Do not use for commit/tag/push, stash, reset, cleanup, branch surgery, or conflict resolution.
---

# Savepoint Guard

## Overview

Use this skill to decide whether the current repo state is narrow enough for a savepoint. It is a read-only pre-flight check only.

## What To Check

Run only read-only Git checks.

- `git rev-parse --show-toplevel`
- `git status -sb`
- `git rev-parse --abbrev-ref HEAD`
- `git rev-parse --short HEAD`
- `git diff --stat`
- `git diff --cached --stat`

## Decision Rules

Return one of these decisions:

- `safe to savepoint`
- `stop: unrelated files present`
- `stop: mixed subsystems`
- `stop: working tree too broad`
- `stop: unstaged intended work remains`
- `stop: staged scope unclear`
- `stop: repo not ready`

Prefer `stop` if the diff is broad, ambiguous, or mixes source edits with runtime-generated junk.

## Scope Checks

Inspect whether the current state is narrow enough for one validated slice.

- Verify the repo root is valid.
- Record branch and HEAD.
- List staged files and unstaged files.
- Check for runtime outputs, generated artifacts, logs, or world files.
- Check whether staged and unstaged work are confusingly mixed.
- Check whether the change looks like one subsystem or one slice.

Normal subsystem buckets:

- worldgen Java logic
- client/UI/rendering
- atlas/viewer/tooling
- docs/specs/reference files
- build/config/mixins
- runtime outputs/logs/generated artifacts/worlds

## Required Summary

Report:

- repo root
- branch
- HEAD short hash
- staged files
- unstaged files
- whether runtime/generated junk is present
- whether the slice appears single-axis
- final decision

If the decision is `safe to savepoint`, explicitly say: `Proceed with the savepoint skill.`

## Safety Boundaries

- Do not commit.
- Do not tag.
- Do not push.
- Do not stash automatically.
- Do not reset.
- Do not clean files automatically.
- Do not guess that a broad diff is fine.
