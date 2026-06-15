# Latitude Handoff

`scope: Latitude (Globe) main repo` · `status: PAUSED pending the maintainer's next choice` · `branch: main` · `date: 2026-06-15`

> **What this doc is.** The single live "start here / resume here" pointer for `<home>/CascadeProjects/Latitude (Globe)`. It states the current repo state, the immediate next decision, and the load-bearing boundaries. It is meant to be overwritten as current truth changes. The chronological running log lives in `docs/binder/`.

---

## Current state

`Latitude (Globe)` was re-anchored and hygiene-synced on 2026-06-15. The repo is on `main` at `4fcdee31` (`Relicense from MIT to GPL-3.0-or-later`) with **no tracked diff**. The only remaining untracked surface is `docs/binder/`, which is intentional: Maintainer uses binder notes as the running local log of Latitude work. The earlier pull-blocking headless-world files were moved out to an external archive so `main` could fast-forward cleanly from `7f55a30f` to `4fcdee31`. After the sync, non-canon Altitude docs were moved into the Altitude repo, and the local proof/runtime folders `Manual atlas/` and `run-headless-base/` were archived outside the repo rather than committed or deleted.

## What changed in the hygiene pass

- Fast-forwarded `main` from `7f55a30f` to `4fcdee31` after removing three pull blockers from `run-headless/world/`.
- Moved the three pull blockers to `<home>/CascadeProjects/Latitude-Globe-main-pull-blockers-20260615-103718/`.
- Moved Altitude-specific docs out of this repo and into `<home>/CascadeProjects/Altitude/`.
- Archived `Manual atlas/` to `<home>/CascadeProjects/Latitude-Globe-manual-atlas-archive-20260615/`.
- Archived `run-headless-base/` to `<home>/CascadeProjects/Latitude-Globe-run-headless-base-archive-20260615/`.
- Left `docs/binder/` in place on purpose as the continuing log surface.

## Read this next

1. `docs/binder/main-repo-hygiene-sync-20260615.md` for the repo-hygiene chronology and archive paths.
2. `docs/binder/1211-province-gap-and-structure-question-20260614.md` for the active structural decision about canonical-vs-port authority.
3. `docs/binder/1211-worldgen-treeline-alpine-status-20260608.md` for the older 1.21.11 tree-line/alpine status note that remains historical context, not the current controlling task.

## Active boundary

Do not resume old 26.1.2 proof work, cruise-control work, release/upload/savepoint work, or 1.21.11 implementation work from this repo handoff unless Maintainer explicitly reactivates that slice. The current repo is hygienic and paused; the next real work should start from the maintainer's choice, not from leftover local dirt.

## Immediate next

The main open product decision is still the one recorded in the 2026-06-14 binder note: whether the 1.21.11 line is a backport to reconcile from canonical 26.1.2, or whether the project structure itself needs to be reconsidered. Until Maintainer chooses, the correct state here is "clean main repo, binder kept live, no implied implementation lane."
