# AGENTS.md

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

## Scope guardrail

- Update only this file for Codex-instruction behavior unless the user explicitly requests broader doc changes.
