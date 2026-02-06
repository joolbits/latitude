---
name: latitude-bug-triage-playbook
description: Fast triage checklist for Latitude bug reports. Turns vague “something is wrong” reports into a small set of buckets (band math, biome pick, surface rules, features, write path, UI lifecycle, rendering/shaders, release mismatch) with minimal proof steps and lowest-effort fixes first.
---

# Latitude — Bug Triage Playbook (Authoritative)

Use this skill whenever:
- a player reports a bug
- a regression appears after a change
- you feel “this is going to be another 14-hour thing”

Goal: classify the bug into a known bucket in under ~10 minutes.

---

## Step 0 — Required report bundle (ask for this first)
If any are missing, request them (do not guess):

1) MC version: (e.g., 1.21.11 / 1.21.1)
2) Latitude version (jar filename preferred)
3) Mod loader + key mods:
   - Fabric Loader version
   - Fabric API version
   - Sodium? Iris? (and versions)
4) World type:
   - New world or existing world?
   - Seed (if relevant)
   - World border preset / diameter
5) Repro steps:
   - exact coords and what to do
6) Evidence:
   - F3 screenshot (shows biome + coords)
   - last ~120 lines of `latest.log` around the event (or filtered tags)

Minimal “fast ask” script:
- “Send: jar filename, MC version, Sodium/Iris versions, F3 screenshot at the spot, and your coords.”

---

## Step 1 — Identify the bucket (choose ONE)

### Bucket A — Release / version mismatch
Symptoms:
- jar name doesn’t match release notes
- behavior doesn’t match expected fix
- “I updated but nothing changed”

Checks:
- verify jar filename
- verify `mod_version` / tag used
- verify branch for the target

Skill to invoke:
- `latitude-release-discipline` 

---

### Bucket B — Zone math / band gating wrong
Symptoms:
- equator/poles feel shifted
- bands appear at wrong Z distances
- warm/cold classification wrong everywhere

Checks:
- compute `radius = worldBorderSize/2` 
- compute `t = abs(z)/radius` 
- confirm zone selection method is used everywhere

Skill to invoke:
- `latitude-zone-math-authority` 

---

### Bucket C — Biome eligibility / missing biome
Symptoms:
- biome never appears
- band has wrong biome family distribution
- “X biome is gone”

Checks:
- consult biome authority table
- confirm tags/logic includes biome
- treat as omission/logic bug, not rarity

Skill to invoke:
- `latitude-biome-authority` 

---

### Bucket D — “F3 says warm biome but cold blocks/features appear”
Symptoms:
- snow/powder snow/ice in jungle/desert
- “biome is correct but blocks are wrong”

Checks:
- locate `[LAT_PICK] base=... out=... zone=...` near the coords
- if base is cold while out is warm, suspect feature climate checks or late placement

Skill to invoke:
- `latitude-biome-selection-contract` 

---

### Bucket E — Wrong blocks placed (write-path problem)
Symptoms:
- “impossible block appears” (snow in tropics, ice in desert, etc.)
- tends to occur near cave mouths, ridges, or after features run

Checks:
- determine write path with one-shot log/counter:
  - Feature cancel logs (e.g., FreezeTopLayer)
  - ChunkRegion/ProtoChunk rewrite counters
- trap at choke point

Skill to invoke:
- `latitude-write-path-guards` 
- also: `latitude-debug-instrumentation-rules` for safe logging

---

### Bucket F — UI/HUD regression
Symptoms:
- a screen opens unexpectedly after joining world
- overlays render over menus/inventory
- controls/keys trigger at wrong times

Checks:
- confirm opening trigger (join tick vs keybind vs debug flag)
- enforce “no auto-open in-world”

Skill to invoke:
- `latitude-ui-and-hud-lifecycle` 

---

### Bucket G — Rendering / shaders / Sodium issues
Symptoms:
- fog/wall invisible with shaders
- flicker
- works vanilla but not with Sodium/Iris

Checks:
- confirm render callback is firing (single-shot ping)
- switch to minimal debug lines behind a toggle
- verify RenderLayer choice and entry-based vertex emission

Skill to invoke:
- `latitude-rendering-compat-rules` 

---

## Step 2 — Minimal proof strategy (avoid “new world fatigue”)

### Prefer deleting a region over making a new world
If the bug is worldgen-related and localized:
- delete only the affected region file to force regen:
  - `run/saves/<world>/region/r.<x>.<z>.mca` 

Proof steps:
1) Stand near the bad area.
2) Note coords.
3) Delete region file covering the coords.
4) Reload world to regen chunks.

If the bug is global/systemic (zone math, selection), then a fresh world may still be necessary.

---

## Step 3 — Fix policy (smallest effective fix)
- If write-path confirmed: fix at choke point (feature cancel → ChunkRegion → ProtoChunk)
- If selection wrong everywhere: fix zone math authority helper
- If missing biome: fix tag/eligibility logic, never delete the biome
- If UI: gate behind keybind/debug flag; default OFF
- If rendering: avoid GL state; use shader-safe layer/emission patterns

---

## Step 4 — Required exit criteria
Before closing a bug:
- one proof screenshot (or log line) showing “before vs after”
- debug toggles remain OFF by default
- one focused commit with clear message
- tag/savepoint if it touched worldgen or rendering

---

## Required assistant output format
For any bug report, assistant must output:

1) Bucket chosen (A–G)
2) Missing info request (if any)
3) Single next command / step
4) Minimal proof plan (no new world unless necessary)
5) Minimal fix plan (1–3 files)
6) Rollback plan
