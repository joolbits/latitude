---
description: Latitude pre-release gate for EW client-visual work (never regress EW fog)
---

# /wsf — Latitude: “Never regress EW fog again” guardrails

## Workflow Invocation (MANDATORY)
Apply this workflow for any client-visual subsystem slice (EW haze / fog / HUD layering).

## Step 0 — Canonical workspace hard-stop
1. `cd "C:\Users\jscho\CascadeProjects\Latitude (Globe)"`
2. `git status -sb`
3. `git rev-parse --abbrev-ref HEAD`
4. `git rev-parse --short HEAD`

Done when: path is canonical; branch is correct for client-visual work; working tree clean (or only intended slice files).
Stop if: not in canonical workspace; dirty tree with unrelated files; wrong branch.

## Step 1 — Baseline selection + pin
Pick last known good EW visuals tag (baseline). If already trust `v1.2.5+1.21.11`, use it; otherwise tag only after matrix pass.
Record in `docs/architecture/client-render-baseline.md`:
- baseline_tag:
- tested_date:
- shaderpack used:
- sodium version:

Done when: baseline tag written in docs and treated as authority for diffs.

## Step 2 — Mixin manifest parity gate (client list must not drift)
Run: `git diff <BASELINE_TAG>..HEAD -- src/main/resources/globe.mixins.json`
Done when: no unexpected client mixin removals/renames; any intentional change is isolated to a dedicated commit with rationale.
Stop if: any client mixin changed unintentionally.

## Step 3 — One-axis-per-commit enforcement (render axes)
Before staging, verify diff touches ONLY ONE axis:
1) fog geometry
2) fog color tint
3) sky tint
4) EW overlay
5) HUD overlays
6) sodium compat

Done when: diff only touches one axis.
Stop if: multiple axes → split into separate commits/slices.

## Step 4 — Build gate
Run: `./gradlew clean build`
Done when: BUILD SUCCESSFUL.
Stop if: build fails (fix before proceeding).

## Step 5 — runClient visual matrix (must pass all)
Run: `./gradlew runClient`
Test quadrants (capture evidence):
A) Shaders OFF, Sodium OFF
B) Shaders ON,  Sodium OFF
C) Shaders OFF, Sodium ON
D) Shaders ON,  Sodium ON (if supported)

For each quadrant validate:
- Approach East border → EW haze appears + thickens smoothly
- HUD readable and on top
- No rectangular horizon artifacts
- No global fog takeover away from border
- Relog + chunk unload/reload stable

Evidence: 1 screenshot or 10–20s clip per quadrant; 1 log snippet proving EW hook fired (or equivalent debug indicator).
Done when: all quadrants PASS and evidence exists.
Stop if: any quadrant fails → do NOT tag/release; return to baseline diff and isolate cause.

## Step 6 — Attach evidence (required before any tag)
Create `docs/qa/ew/ew-rendering-PASS-YYYY-MM-DD/`
Put:
- A/B/C/D media
- `notes.md` with baseline tag, commit hash, shaderpack name, sodium present? (y/n), short PASS statement

Done when: evidence exists in-repo and committed with slice (or referenced via stable path).

## Step 7 — Commit + savepoint tag (ONLY after PASS)
1. Stage intended files: `git add <files...>`
2. Commit: `git commit -m "fix(client): <single-axis EW rendering change>"`
3. Tag: `git tag -a save/ew-render-pass -m "EW rendering PASS: shader on/off + sodium on/off; evidence in docs/qa/ew/..."`
4. Push: `git push` && `git push --tags`

Done when: commit + annotated save tag pushed, and evidence path exists.
Rollback: `git reset --hard <BASELINE_TAG>`
