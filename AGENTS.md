# Latitude — agent guide

Rules for ANY coding agent working in this repo (Claude, Codex, or otherwise). CLAUDE.md and
AGENTS.md are identical copies; edit both together.

## ⛔ NEVER COMMIT — read this before your first `git add`

This is a PUBLIC repository. A 2026-08-07 incident force-rewrote its entire history to remove
13,246 accidentally-tracked files of decompiled Minecraft source and the development activity log.
These rules exist so that never happens again. The commit hook (S-6/S-7) and .gitignore enforce
them mechanically — do not weaken either, do not commit with --no-verify.

1. **No decompiled or extracted Minecraft sources. EVER.** No `_mcsrc*`, no `net/minecraft/**`
   Java files, no mappings dumps. That is proprietary code; tracking it in a public repo is
   redistribution. Reference extractions live OUTSIDE the repo or in gitignored dirs only.
2. **No development diaries in the repo.** `docs/binder/**`, session reports, evidence
   registries, handoffs, live-test ledgers, campaign notes — ALL of it goes to the
   maintainer's local notes directory, never into the tree. If you are writing a
   dated narrative of what you did, you are writing a notes file, not a repo file.
3. **No personal data.** No personal names, no machine usernames, no absolute home-directory
   paths, no session/recorder UUIDs — in ANY tracked file, source comments and commit messages
   included. Cite decisions as "maintainer ruling, <date>".
4. **No runtime state.** `run-headless/**`, `run/**` worlds, `tmp/**`, logs, archives
   (`*.zip`, `*.7z`) stay untracked.

## Working rules

- The maintainer's local notes directory (outside the repository; its location is recorded
  in the maintainer's private agent configuration, not here) is the home of
  ALL process narrative. Read its HANDOFF.md for current state; append your session notes there.
- Verification: run this line's suite before claiming green (1.5 lines: `./gradlew check`;
  2.0 line: `./gradlew test` — count-gated). A green with a wrong count is a false green.
- Release artifacts are gated (`tools/verify_release_artifact.py` on 2.0;
  `tools/verify_phase6_dev_tooling.py` on 1.5): dev/debug tooling never ships. Do not remove
  exclusion clauses; the drift baseline will fail the build if you do.
- `git config core.hooksPath tools/hooks` once per checkout (the build self-installs it where
  wired). Gates: S-6 (no personal/machine-local strings in added lines), S-7 (no forbidden
  paths staged: `_mcsrc*`, `docs/binder/`, `run-headless/`, `tmp/`, archives).
