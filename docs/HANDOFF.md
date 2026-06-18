# Latitude Handoff

`scope: Latitude (Globe) main repo` · `status: PAUSED with canonical 26.1.2 recorded` · `branch: main` · `date: 2026-06-18`

> **What this doc is.** The single live "start here / resume here" pointer for `<home>/CascadeProjects/Latitude (Globe)`. It states the current repo state, the immediate next decision, and the load-bearing boundaries. It is meant to be overwritten as current truth changes. Durable lessons live in `docs/LESSONS.md`; port/backport operating rules live in `docs/porting/PORTING.md`; the chronological running log lives in `docs/binder/`.

---

## Current state

`Latitude (Globe)` was re-anchored and hygiene-synced on 2026-06-15. The repo is on `main` at `f05b73ff` (`Latitude 1.x world 2.0 concept`) with pre-existing documentation updates in progress as of 2026-06-18. The only local documentation surface that should carry active chronology is `docs/binder/`, which is intentional: Maintainer uses binder notes as the running local log of Latitude work. Durable lessons and porting rules are kept separate so future workers do not have to rediscover the same mistakes from dated entries. The earlier pull-blocking headless-world files were moved out to an external archive so `main` could fast-forward cleanly from `7f55a30f` to `4fcdee31`; `main` has since advanced to `f05b73ff`.

## Project truth recorded 2026-06-18

Maintainer resolved the structural question raised in the 2026-06-14 binder note:

- **Current canonical line:** Minecraft **26.1.2**.
- **1.21.11 status:** future backport, not the active canonical development line, and not ready to resume until the 26.1.2 truth is stabilized.
- **26.2 note:** Maintainer noted that Minecraft **26.2** is now released; treat it as an external target to consider later, not as a change to the immediate canonical choice. For now, continue treating **26.1.2** as the active canon.

Practical consequence: do not continue patching the 1.21.11 beta line as if it were primary. If older-version work resumes, it should be framed as a backport from the accepted 26.1.2 source of truth, with an explicit scope and proof gate.

## What changed in the hygiene pass

- Fast-forwarded `main` from `7f55a30f` to `4fcdee31` after removing three pull blockers from `run-headless/world/`.
- Moved the three pull blockers to `<home>/CascadeProjects/Latitude-Globe-main-pull-blockers-20260615-103718/`.
- Moved Altitude-specific docs out of this repo and into `<home>/CascadeProjects/Altitude/`.
- Archived `Manual atlas/` to `<home>/CascadeProjects/Latitude-Globe-manual-atlas-archive-20260615/`.
- Archived `run-headless-base/` to `<home>/CascadeProjects/Latitude-Globe-run-headless-base-archive-20260615/`.
- Left `docs/binder/` in place on purpose as the continuing log surface.

## Read this next

1. `docs/LESSONS.md` for durable "do not relearn this" rules.
2. `docs/porting/PORTING.md` before any port/backport work.
3. `docs/binder/main-repo-hygiene-sync-20260615.md` for the repo-hygiene chronology and archive paths.
4. `docs/binder/canonical-26-1-2-decision-20260618.md` for the maintainer's decision that 26.1.2 is the current canonical line.
5. `docs/binder/1211-province-gap-and-structure-question-20260614.md` for the investigation that led to the canonical-vs-port question.
6. `docs/binder/1211-worldgen-treeline-alpine-status-20260608.md` for the older 1.21.11 tree-line/alpine status note that remains historical context, not the current controlling task.

## Active boundary

Do not resume cruise-control work, release/upload/savepoint work, or 1.21.11 implementation work from this repo handoff unless Maintainer explicitly reactivates that slice. The current repo is hygienic and paused around **26.1.2 as canonical**; the next real implementation work should start from a fresh 26.1.2 scope and proof gate, not from leftover 1.21.11 beta dirt.

## Immediate next

The main product decision is now recorded: 26.1.2 is the current canonical line, and 1.21.11 is a later backport. The immediate next work should be a narrow 26.1.2 status/proof slice before any 1.21.11 or 26.2 planning work.
