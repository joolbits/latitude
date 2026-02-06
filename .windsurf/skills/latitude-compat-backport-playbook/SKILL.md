---
name: latitude-compat-backport-playbook
description: Step-by-step playbook for backporting Latitude changes across MC 1.21.x targets (e.g., 1.21.11 -> 1.21.1). Enforces one-variable-at-a-time, cherry-pick discipline, minimal tests, version/tag correctness, and prevents branch/build confusion.
---

# Latitude — Compat Backport Playbook (Authoritative)

This skill is used whenever porting fixes between Minecraft versions (1.21.x), especially:
- main target: **1.21.11** (`main`)
- compat target: **1.21.1** (`compat/1.21.1`)

Goal: make backports boring and safe.

**Default mode: STRICT** — behavior must match across targets.
- Allowed exceptions only when vanilla signatures/behavior truly diverge between MC versions.
- Any exception must be documented in the commit/PR as: `Pragmatic exception: <why>` including the exact method signature difference.

---

## Golden rules
1) **One variable at a time**: one fix, one commit, one test.
2) Prefer **cherry-pick** of known-good commits over manual reimplementation.
3) Do not mix backport fixes with unrelated refactors.
4) Always confirm:
   - branch
   - mod_version
   - jar name
   - tags
5) If a change touches worldgen, require at least one runtime proof.

---

## Prep checklist (must pass)
On source branch (e.g., `main`):
- fix is committed and tested
- commit(s) are small and focused
- optional debug is gated and OFF by default

On target branch (e.g., `compat/1.21.1`):
- baseline builds cleanly before backport
- working tree is clean (or stash)

---

## Backport procedure (mandatory order)

### Step 1 — Baseline build on target branch
```powershell
git checkout compat/1.21.1
git pull
$env:GRADLE_USER_HOME = "$PWD\.gradle-user-home"
.\gradlew clean build --no-daemon
```

If baseline fails, STOP and fix baseline first.

---

### Step 2 — Identify minimal commits to port

Preferred: port only commits that touch:

* the specific mixin/feature/fix
* required config entries (mixins.json)
* required debug gating

Avoid porting:

* docs
* extracted sources
* tooling folders
* large unrelated refactors

Gather commit list using:

* `git log --oneline <sourceBranch> -- <filePath>` 
* `git show <hash> --name-only` 

---

### Step 3 — Cherry-pick onto target

From target branch:

```powershell
git cherry-pick <hash1> <hash2> ...
```

If conflicts:

* resolve by matching target MC signatures (method params/owners)
* do not change fix behavior while resolving compile errors
* keep edits minimal and localized

---

### Step 4 — Build on target

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-user-home"
.\gradlew clean build --no-daemon
```

If build fails:

* paste first ~60 lines of error
* adjust only signatures/descriptors until it compiles

---

### Step 5 — Minimal runtime proof (no “new world fatigue”)

Require exactly one proof run:

* start client
* generate a small area near a known test coordinate (or equator zone)
* confirm the fix triggers (via visual result or gated logs/counters)

If old chunks contain the old bug:

* delete only the relevant region file (`r.<x>.<z>.mca`) to regen

---

### Step 6 — Version + tag correctness (do NOT skip)

On the target branch, ensure `gradle.properties`:

* `mod_version=<release>+<mcVersion>` 

Then:

* build jars again
* confirm jar names match `mod_version` 

Tag must point to the commit that produces that jar:

```powershell
git tag -f v<mod_version>
git push origin -f v<mod_version>
```

---

## Common failure patterns (and required fixes)

### A) “I can’t checkout branch: gradle.properties would be overwritten”

Fix:

* `git stash push -m "temp: gradle.properties bump"` 
* checkout
* build
* later `git stash pop` on the original branch

### B) “I built but jar name is wrong”

Cause:

* target branch still has old `mod_version` 
  Fix:
* update `gradle.properties` on THAT branch
* commit
* rebuild

### C) “Incompatible mods found” during dev run

Cause:

* leftover jars in `run/mods` from a different MC target
  Fix:
* clear `run/mods/*` and `run/.fabric/processedMods/*` 

### D) Mixins apply failures on older target

Fix:

* remove non-essential mixin (especially precipitation hooks) if it fails apply
* keep the core guard/fix mixins
* re-run, then optionally reintroduce later

---

## Required assistant output format

When asked to backport:

1. Source branch + target branch
2. Commit list to cherry-pick (with reasons)
3. Exact commands (PowerShell)
4. Expected success outputs
5. Minimal runtime proof steps
6. Version/tag actions
7. Rollback strategy
