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

## Scope guardrail

- Update only this file for Codex-instruction behavior unless the user explicitly requests broader doc changes.
