---
name: latitude-repo-hygiene
description: Repo hygiene rules for Latitude. Prevents generated/extracted artifacts from entering commits, release branches, tags, or jars. Provides the canonical ignore list and cleanup commands.
---

# Latitude — Repo Hygiene (Authoritative)

This skill prevents:
- accidental commits of extracted Minecraft sources
- accidental commits of tooling folders (Windsurf skills, caches)
- polluted feature branches being used for releases
- confusion around huge diffs unrelated to mod code

---

## Never-commit folders (absolute)
These must never be committed to any release branch (and should generally be ignored everywhere):

- `_mcsrc_extract/` 
- `run/` (worlds, logs, caches)
- `logs/` 
- `.gradle/` 
- `.gradle-user-home/` 
- `build/` 
- `out/` 
- `.idea/` 
- `.vscode/` 
- `.classpath` 
- `.project` 
- `.settings/` 
- `*.iml` 

Tooling:
- `.windsurf/` (skills, internal state) — **never ship inside jars**
- any `processedMods/` cache folder under `run/.fabric/` 

OS noise:
- `Thumbs.db` 
- `.DS_Store` 

If any of these appear in `git status`, STOP and fix before continuing.

---

## Release branch hygiene rules
- 1.21.11 releases must be tagged from `main` 
- 1.21.1 releases must be tagged from `compat/1.21.1` 
- Do not tag releases from `feature/*` branches, especially if they contain any forbidden folders.

---

## Required .gitignore policy
The repo must include ignore rules for every “never-commit folder” above.

If the assistant proposes adding a generated folder to the repo, it must justify why and confirm it is safe for release.

---

## Cleanup commands (PowerShell)
### Remove generated artifacts
```powershell
Remove-Item -Recurse -Force .\build -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\.gradle -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\.gradle-user-home -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\run\.fabric\processedMods\* -ErrorAction SilentlyContinue
```

### If forbidden files were accidentally staged

```powershell
git restore --staged .
git restore .
```

### If forbidden files were committed (do not panic)

Preferred fix:

* revert the commit on the branch
* or rewrite history only if absolutely necessary and coordinated

---

## Jar safety check (must pass before upload)

Always check the jar you will upload:

```powershell
jar tf .\build\libs\<YOUR_JAR_NAME>.jar | findstr /I "_mcsrc_extract .windsurf SKILL.md com/mojang blaze3d"
```

Pass criteria:

* no matches.

---

## Required assistant output when repo pollution is detected

The assistant must:

1. identify the forbidden path(s)
2. provide the smallest safe cleanup action
3. ensure `.gitignore` prevents recurrence
4. ensure release tags are on clean branches
