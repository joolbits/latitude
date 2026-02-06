---
name: latitude-release-discipline
description: Release guardrails for Latitude. Prevents tagging/building/uploading from the wrong branch, mismatched version strings, dirty working trees, or shipping dev/debug artifacts. Enforces one-step-at-a-time release checklist.
---

# Latitude — Release Discipline (Authoritative)

This skill exists to prevent release mistakes:
- building the wrong branch
- tagging the wrong commit
- uploading a jar whose version string doesn't match the release
- shipping with debug spam enabled
- releasing from a feature branch that contains extracted sources / tooling artifacts
- confusion between MC targets (1.21.11 vs 1.21.1)

If any checklist item fails, the assistant must say **STOP** and provide the single next command to fix it.

---

## Supported release targets (current)
- **Main target:** MC **1.21.11** (branch: `main`)
- **Compat target:** MC **1.21.1** (branch: `compat/1.21.1`)

> Never release 1.21.11 from a `feature/*` branch.
> Never assume `build/libs` contains the correct jar until after a clean build on the target branch.

---

## Preflight invariants (must be true)
1) Working tree clean (or only intended version bump changes)
2) Correct branch checked out for the target
3) `gradle.properties` contains the correct `mod_version` for that target
4) `./gradlew clean build` succeeds
5) The built jar filename matches the intended release string
6) Jar contents do not include extracted sources or tooling folders
7) Tag points at the commit that produced the jar

---

## One-step-at-a-time release protocol (mandatory)

### Step A — Confirm branch + cleanliness
Run:
- `git status` 
- `git branch --show-current` 

Rules:
- If `git status` shows local changes that would block checkout, **stash** with a message.
- If the branch is wrong, checkout the correct branch before doing anything else.

---

### Step B — Confirm version string (no guessing)
On the target branch:
- Inspect `gradle.properties` and confirm:
  - `mod_version=...` 

Required patterns:
- 1.21.11 build: `1.2.5+1.21.11` (example)
- 1.21.1 build: `1.2.5+1.21.1` (example)

If the jar name later shows a different version, the branch’s `mod_version` is wrong. Fix `gradle.properties`, commit, rebuild.

---

### Step C — Clean build with isolated Gradle home
Run from repo root:
```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-user-home"
.\gradlew clean build --no-daemon
```

Then list jars:

```powershell
Get-ChildItem .\build\libs\*.jar | Select-Object Name, Length, LastWriteTime
```

Rule:

* If the expected jar is not present, do not proceed.

---

### Step D — Verify jar contents (must do once per release file)

Inspect the jar you will upload:

```powershell
jar tf .\build\libs\<YOUR_JAR_NAME>.jar | findstr /I "_mcsrc_extract .windsurf SKILL.md com/mojang blaze3d"
```

Pass criteria:

* No matches.

If there are matches:

* STOP. Rebuild from a clean branch and fix `.gitignore` or build config.

---

### Step E — Tag discipline (tags must match jar)

Preferred tags:

* `v<mod_version>` (example: `v1.2.5+1.21.11`)

Rules:

* Tag must be created/moved only **after** the jar is confirmed correct.
* If you must move a tag, use `-f` and force-push only that tag.

Commands:

```powershell
git tag -f v<mod_version>
git push origin -f v<mod_version>
```

---

### Step F — Upload discipline (Modrinth)

Rules:

* A single Modrinth release may contain multiple files.
* Each file must be assigned the correct game versions.
* Never upload a “fixed jar” without also ensuring the tag/branch state matches.

Minimum changelog bullets must include:

* the user-facing fix summary
* any important regression fix (UI, crashes)
* optional: debug flags are dev-only

---

## Branch hygiene rules (absolute)

* `main` is the only allowed release base for 1.21.11.
* `compat/1.21.1` is the only allowed release base for 1.21.1.
* Feature branches may contain experiments and must not own release tags.
* Extracted sources and tooling folders must never be part of a release commit:

  * `_mcsrc_extract/` 
  * `.windsurf/` 
  * other generated extraction folders

---

## If something is confusing, diagnose like this

1. List jars in `build/libs` 
2. Confirm current branch
3. Confirm `mod_version` in `gradle.properties` 
4. Rebuild
5. Re-list jars
6. Only then tag/upload

No other order is allowed.
