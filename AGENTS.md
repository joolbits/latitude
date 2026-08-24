# AGENTS.md

## Commit discipline — commit proven work; never commit prohibited content

**Commit proven, task-owned work as you go.** This is not a blanket ban on ordinary commits.
Once a focused change has its required proof, inspect the exact staged paths and make the normal
focused commit with the repository hook enabled. The public-repository safety rules, `.gitignore`,
and S-6/S-7 hook remain hard boundaries: never weaken or bypass them with `--no-verify`.

## Latitude Binder Instructions (Codex-native)

For work in `<home>/CascadeProjects/Latitude (Globe)`, Codex must use the binder as the next-step authority for documentation and evidence workflows.

- Before any Latitude evidence, proof, reporting, savepoint, release, Bug Blaster, or project-record statement, review:
  - `docs/binder/index.md`
  - `docs/binder/update-contract.md`
  - `docs/binder/evidence-schema.json`
  - `docs/binder/evidence-registry.md`
- Treat this as a mandatory precondition for workflow handoff, especially on savepoint completion and release notes.
- Use `docs/binder/update-contract.md` for append/update triggers and stale/blocking rules.
- Enforce evidence rows in `docs/binder/evidence-registry.md` with schema keys and enum constraints from `docs/binder/evidence-schema.json`.
- For stale tracker updates, require refreshed binder state before stating proof-complete or release-ready.

## Headless verification (before calling anything "live-test only")

Codex cannot reliably pilot the real Minecraft client (GLFW input is flaky) and must never use the Mojang
launcher (Modrinth App only — hard rule). That does NOT mean worldgen/world-creation changes are unverifiable
without Peetsa at the keyboard. Before writing off a check as "needs a live test," read
`docs/binder/headless-verification-playbook.md` and apply, in order: (1) read the source and confirm the
value/flag actually reaches its call site, (2) if the behavior is a deterministic function, call the REAL
compiled method directly across the input space (never reimplement the spec in a throwaway script — that
only proves you can read a comment) — for this, `tools/dev/run_probe.sh` is a turnkey, no-new-Java wrapper:
it boots a real globe world headlessly and reflectively sweeps any static method over a parameter grid into
a CSV (run it with no args to see usage), (3) if it needs real terrain/chunk context, boot the existing
headless server dev harness (`BiomePreviewHeadlessRunner`, see the playbook for the gradle invocation), (4) for biome
distribution/representation questions, render and measure the headless atlas
(`tools/atlas/atlas_runner.py` + `band_correctness_check.py`). Only the genuine residue — real client UI flow,
subjective/visual judgment, or terrain that didn't generate near the sampled spawn — goes in a "needs Peetsa
in-game" list at the end of a report. Do not claim something is "verified" when only the logic that would
produce verification was proven, not its real-world precondition.

## Tiered multi-agent workflow (Peetsa's preferred delegation shape, 2026-07-09)

Reference implementation + full rationale: https://github.com/orionmilos0-jpg/fabletieredworkflow
(`docs/workflow/TIERED-AGENTS.md` + `HANDOFF-TEMPLATE.md` there). For any substantive multi-pass slice
(worldgen fixes, feature batches, audits), run this crew shape — pick model strength per role by TOKEN
PROFILE, not prestige (repetitive/well-specified → cheaper tier; novel/logic-heavy → stronger tier):

- **Architect / director** (strongest available reasoning model, the MAIN loop): plans, decomposes,
  writes the handoff packages, does the final eval per pass. **Never writes code** — keeps its context
  clean for judgment; never ingests raw logs or code churn (workers return synthesized reports only).
  Also personally runs anything long-running or lock-contended (headless atlas / world-gen runs:
  sequenced, never delegated to subagents, never concurrent — see CLAUDE.md).
- **Developer** (Opus-tier): the heavy lifting — real implementation plus the build/fix/retry loop.
  One bounded pass per handoff package.
- **Test-writer** (Sonnet-tier): boilerplate tests, mocks, fixtures, smoke checks, matching the
  project's existing pure-JVM test idioms.
- **Sweeper** (Opus-tier, adversarial — this project's addition to the reference shape): checks EACH
  pass for bugs and compliance (law/flag-gating/byte-identity) before it's accepted — prompted to
  refute, not confirm.
- **Reviewer** (strongest-tier, read-only): reads the diff + worker reports and checks the
  DOCUMENTATION against what the pass actually did; returns a structured verdict
  (approve / changes-required, routed back to the right worker). Catches doc drift the same pass it
  happens (indexing discipline is absolute law).
- **Creative Director** (Opus-tier, read-only on src/ — defined at `.claude/agents/creative-director.md`;
  added 2026-07-11 after the title art-direction review, which Peetsa "absolutely LOVED"): an
  art-direction / UX-delight review of ONE player-facing surface per engagement (screens, HUD, titles,
  loading, atlas, icons). Deliverable = a ranked, plain-language findings doc in the binder (readability
  first, tested against real worst-case backdrops; claims grounded in the actual draw code with numbers)
  + effort-sized recommendations + a "v2 recipe" + the single highest-impact change. NOT a per-pass
  role — dispatch when a surface LOOKS done and the question is beauty/readability/coherence ("is this
  a signature moment?"), typically before a big UI round or after Peetsa reacts lukewarmly to a look.
  Findings that conflict with Peetsa's explicit taste decisions are reported with data but flagged as
  HER call — the review never overrides the owner.
- **Creative Understudy** (Sonnet-tier, read-only, no deliverable doc — defined at
  `.claude/agents/creative-understudy.md`; locked in 2026-07-11 at Peetsa's request): the fresh-eyes
  junior. Skims ALL player-facing surfaces at once (where the Creative Director deep-dives one) and
  pitches a raw ranked one-liner list — paper cuts, quick delights, max 3 marked SWINGs — to the
  DIRECTOR, who approves/dismisses each; only survivors reach Peetsa or a dev crew. Cheap by design:
  run every few UI rounds or before a milestone jar, not per-pass.
- **Codex CLI** (optional second-opinion reviewer from a different model family — catches different
  blind spots): use if installed; as of 2026-07-09 it is NOT installed on this machine — the
  sweeper/reviewer split substitutes. If installed later: https://github.com/openai/codex .

**Handoff packages** (the two disciplines that make or break this: explicit handoffs + a boring shared
run log). Every handoff must be SELF-CONTAINED — a worker with zero prior context executes it cold and
knows exactly when it's done. Follow the reference template's fields: tier+model, depends-on,
blast radius, goal, context (name files/link docs — don't paste bodies), explicit IN and OUT of scope
(the out-list is the cheapest scope-creep prevention), implementation notes, TESTABLE acceptance
criteria ("works" is not a criterion; "returns 422 on empty id" is), test command + what green looks
like, and the report-back format (synthesized summary only — files changed one line each, pass/fail,
deviations — no raw logs). One concern per package; if it needs two subsystems, split it. The architect
must pass this Definition of Ready BEFORE routing — a cheaper worker will confidently wander down the
wrong path on a vague handoff. On this project, packages may be embedded in the delegated prompt (small
slices) or written to files; the slice's binder plan doc (e.g.
`docs/binder/consumer-law-compliance-plan-20260709.md`) serves as the shared RUN LOG every tier's
outcome gets appended to — the reviewer reads what happened there instead of reconstructing it from chat.

Cadence: document as you go; **commit each successful pass locally; push only after the slice-level
map/headless proof gate is green** (map-based proof is project law). Skip the ceremony for small tasks —
tiering pays off on multi-step work. Every delegated prompt must carry the CLAUDE.md riders: do not
spawn subagents; run long commands in the foreground with a generous timeout; never start headless
world-gen from a subagent.

## Scope guardrail

- Update only this file for Codex-instruction behavior unless the user explicitly requests broader doc changes.

---

## Appendix: origin/main AGENTS.md (preserved verbatim, merge conflict resolution)

_The section below is the complete content of `AGENTS.md` as it existed on `origin/main` at the time of
this merge. It describes an earlier/alternate instruction set for this repo (a different checkout path
convention and "Maintainer" rather than "Peetsa"). It is preserved here in full — nothing from main's version
was dropped — per the merge policy that phase5's `AGENTS.md` above is the actively-used instruction set
(referenced from `CLAUDE.md`), while this appendix retains every rule main had that isn't already covered
above._

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

## Automatic Documentation Upkeep

Documentation coherence is part of finishing non-trivial Latitude work. Maintainer should not need to ask for it separately.

Before reporting a non-trivial code, proof, release-readiness, port/backport, live-client, or docs slice as done:

- Update `docs/HANDOFF.md` when the current resume point, release/readiness state, root/profile truth, blocker status, or next gate changed.
- Add or update a dated `docs/binder/` note when the slice created chronology, evidence, decisions, or proof that future workers need to reconstruct.
- Update `docs/binder/README.md` when adding a binder note.
- Update `docs/LESSONS.md` only when a durable rule, repeated mistake, or future required behavior changed.
- Keep current truth, historical notes, and speculative next work separate.
- Do not wait for Maintainer to say "$docs" when the work itself changed the docs truth.

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
