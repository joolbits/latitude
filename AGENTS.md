# Latitude (Globe) Agent Instructions

These rules apply to this checkout:

```text
<home>/CascadeProjects/Latitude (Globe)
```

## Required First Reads

Before non-trivial Latitude work, automatically read the repo front-door docs that match the task. Maintainer should not need to request them.

1. `AGENTS.md`
2. `docs/HANDOFF.md`
3. `docs/LESSONS.md`
4. `docs/porting/PORTING.md` for ports, backports, release-state checks, profile/jar checks, biome/world-shape carryovers, or version migration work
5. Relevant `docs/binder/*` notes for the active blocker or decision

## Working Card

Use the maintainer's six-line working card before acting on non-trivial code, docs, proof, port, release, automation, or live-client work:

```text
Objective:
Root/profile:
Allowed work:
Forbidden lanes:
Proof gate:
Stop condition:
```

If the root, version target, profile, proof gate, or scope changes, rewrite the card before continuing.

## Guardrails

- Verify root, branch, HEAD, and dirty state before editing.
- Treat `docs/HANDOFF.md` as the current orientation page, but verify it against Git and active profile evidence before source or release work.
- Treat `docs/LESSONS.md` as durable memory for repeated mistakes and required checks.
- Treat `docs/porting/PORTING.md` as mandatory for any port/backport or version-family carryover.
- Do not edit Slabbed, Altitude, Modrinth profiles, jars, generated output, or external worktrees from this checkout unless Maintainer explicitly asks.
