---
description: Code Manager Autopilot for savepoints/tags/push (Latitude client visuals)
---

# /wsf — Latitude Code Manager Autopilot: Savepoints / Tags / Push

Standing rule: apply `latitude-regression-fix-workflow` unless explicitly doing a release upload (then use `latitude-pre-release-gate-workflow`).

## Trigger A — “Things are perfect now” / Visuals PASS
1) Ensure canonical workspace:
   - `cd "C:\Users\jscho\CascadeProjects\Latitude (Globe)"`
2) Preflight: `git status -sb` / `git rev-parse --abbrev-ref HEAD` / `git rev-parse --short HEAD`
3) Build gate: `./gradlew clean build`
4) Client smoke if visuals touched: `./gradlew runClient`
5) Remove debug instrumentation/log spam.
6) Commit intended files only: `git add -p` → `git commit -m "save: <short description of verified-good state>"`
7) Annotated save tag: `git tag -a save/<desc> -m "PASS: shader on/off + sodium on/off; evidence: docs/qa/..."`
8) Push branch + tags: `git push` && `git push --tags`
Done when: commit exists, annotated save tag exists, both pushed, `git status -sb` clean.
Stop if: not canonical workspace, build fails, runClient fails, more than one client-visual axis in same commit.

## Trigger B — “Uh oh fog is broken” / Any regression observed
1) Tag broken state immediately (no code changes): `git tag -a save/broken-<desc> -m "Regression observed; evidence pending"` then `git push --tags`
2) Find last known-good tag: `git tag --list "save/*" --sort=-creatordate | head`
3) Scope diff vs last good: `git diff <GOOD_TAG>..HEAD -- src/main/java/**/client/** src/main/resources/globe.mixins.json docs/architecture/**`
Done when: broken state tagged + pushed before attempting fix.

## Trigger C — “About to do risky client visuals”
1) Pre-experiment savepoint: `git tag -a save/pre-<desc> -m "Pre client-visual experiment"`
2) `git push --tags`
Done when: pre tag pushed before edits.
