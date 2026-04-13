---
name: workspace-sync-handoff
description: Safe Git-based workspace handoff for the same repo between a Mac and a laptop. Use only for leave-this-machine, arrive-on-other-machine, and safe-sync-status checks when switching devices and you need to inspect repo state, preserve uncommitted work, and sync safely without force-pushes or hard resets.
---

# Workspace Sync Handoff

## Overview

Use this skill only when switching the same Git repo between a Mac and a laptop. It is for leave-this-machine, arrive-on-other-machine, and safe-sync-status checks that must preserve local work and avoid silent overwrites.

## Core Rules

- Detect the repo root with `git rev-parse --show-toplevel`.
- Fail clearly if the current directory is not inside a Git repo.
- Show branch, short HEAD hash, and dirty/clean state before changing anything.
- Detect ahead/behind against `origin` when available.
- This skill is not for ordinary git cleanup, branch surgery, conflict resolution, release tagging, or force-repair workflows.
- Never force-push.
- Never hard-reset unless explicitly asked.
- If the tree is dirty, do not destroy changes.
- If divergence or conflict risk appears, stop and explain exactly what happened.

## Quick Check

Use the helper script for a read-only status snapshot:

```powershell
python .codex/skills/workspace-sync-handoff/scripts/workspace_sync_handoff.py status
```

## Quick Commands (Aliases)

These are short commands for fast use. Treat them as shorthand for the standard workflows.

- `endmac`  
  → Leave This Machine (Mac context)

- `startmac`  
  → Arrive On The Other Machine (Mac context)

- `endpc`  
  → Leave This Machine (PC/laptop context)

- `startpc`  
  → Arrive On The Other Machine (PC/laptop context)

- `status`  
  → Safe Sync Status (read-only check)

When one of these commands is used:

- Run the corresponding workflow immediately.
- Default to read-only behavior unless the user explicitly asks to commit, stash, pull, or push.
- Always show the standard status summary before suggesting any action.

## Standard Workflows

### Leave This Machine

Use when you are done working on the current device and want to hand off to the other one.

1. Run the status check.
2. If the tree is dirty, choose one safe path:
   - Commit tracked work with a handoff-style message.
   - Stash tracked and untracked work with a labeled stash entry.
3. Fetch remote updates.
4. Rebase or fast-forward only if safe.
5. Push the current branch.
6. Summarize branch, commit, push result, and stash state.

### Arrive On The Other Machine

Use when you open the repo on the other device and want the latest safe state.

1. Run the status check first.
2. Fetch all remotes with pruning.
3. Pull with `--rebase` when the branch is tracking `origin` and there is no obvious divergence risk.
4. If there is local dirt, restore it only from a stash you created on the other machine, or stop and explain the conflict risk.
5. End with a short summary.

### Safe Sync Status

Use when you only need a quick, read-only answer about whether a handoff is safe right now.

- Repo root
- Branch
- HEAD short hash
- Dirty or clean
- Ahead/behind versus `origin`
- Stash presence
- Recommended next step

This check is also the right entry point when you only want to verify the repo is safe before leaving or arriving.

## Action Selection

When the tree is dirty:

- Prefer commit if the changes are ready to keep in history and should travel with the branch.
- Prefer stash if the work is unfinished, experimental, or you want the smallest possible footprint.
- Never auto-commit untracked files unless the user explicitly wants that behavior.

When the branch is behind `origin`, fetch first and rebase only if the local work is safe to replay.

## References

- See [handoff examples](references/handoff-examples.md) for common user phrases and the intended workflow.
- See [helper script](scripts/workspace_sync_handoff.py) for the read-only status command and command suggestions.
