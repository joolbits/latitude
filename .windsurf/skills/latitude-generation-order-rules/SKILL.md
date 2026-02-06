---
name: latitude-generation-order-rules
description: Canonical rules for how Latitude decides biomes and applies them across generation stages. Prevents the model from inventing steps, reordering pipeline stages, or “fixing” bugs by changing generation order.
---

# Latitude — Generation Order Rules (Authoritative)

This skill defines the **only allowed** high-level order of operations for Latitude worldgen and related runtime guards.
It exists to prevent the assistant from:
- Inventing new pipeline steps
- Reordering stages without evidence
- “Fixing” biome leaks by guessing different order
- Removing vanilla biomes or changing eligibility tables

If the assistant cannot prove a step from code, logs, or an attached authority table, it must say **UNKNOWN** and ask for the specific file / method signature.

---

## Primary authority files (must consult)
- **Vanilla biome latitude eligibility table** (single source of truth):
  - `.windsurf/skills/latitude-biome-authority/SKILL.md`
- **Biome bands reference (if present in repo docs / exports):**
  - `cc-latitude-comprehensive-2026-02-05.md` (project notes)
- **Any “LatitudeBiomes” / picker implementation**:
  - `src/main/java/com/example/globe/world/LatitudeBiomes.java` (or the current equivalent)

> Rule: If there is any conflict between a guess and an authority file, the authority file wins.

---

## Non-negotiable invariants
1) **All vanilla biomes must still exist** somewhere unless the authority list explicitly excludes them.
2) **Latitude bands constrain selection**; variants/overlays are secondary and may only restrict, not invent.
3) “Fixes” must not rely on changing worldgen order unless:
   - a concrete call site is identified, and
   - a targeted change is made with minimal surface area.
4) If snow/cold artifacts appear in warm bands, the allowed fixes are **write-path guards** (ProtoChunk/ChunkRegion/Feature cancel) or **correct biome container propagation** — not “change band table.”

---

## Canonical generation pipeline (high level)
When reasoning about how a block/biome appears, assume this order unless code proves otherwise:

### Stage A — Base / vanilla biome resolution
- Vanilla chooses a **base biome** (the “base=” shown in logs).
- Latitude may log this (e.g. `[LAT_PICK] base=...`).

### Stage B — Latitude selection and output biome override
- Latitude computes:
  - `radius = worldBorderSize / 2`
  - `t = abs(z)/radius`
  - `zone = band(t)`
- Latitude chooses an **out biome** (the biome returned to the generator).
- Latitude logs (example shape):
  - `[LAT_PICK] zone=EQUATOR base=snowy_taiga out=jungle ...`
- If the assistant proposes threshold changes, it must reference the exact helper/constants used.

### Stage C — Surface rules / material placement
- Surface rules (MaterialRules) place top blocks (grass/sand/snow_block, etc.) based on biome + noise.
- If “out biome” is warm but cold blocks appear, suspect that:
  - some placements still consult **base biome** temperature/precip flags, OR
  - placements happen through features after surface rules.

### Stage D — Feature placement / post-processing
- Features can place blocks using region/world access (e.g. `FreezeTopLayerFeature`).
- These may use biome temperature flags and can be triggered even when out biome is warm if base biome data leaks.

### Stage E — Runtime & write-path guards (Latitude protections)
Allowed guard layers (in order of preference for robustness):
1) **Feature-level cancel** for known offenders (e.g., cancel `FreezeTopLayerFeature` in warm bands)
2) **Write-path trap** at `ChunkRegion#setBlockState` during worldgen
3) **ProtoChunk#setBlockState` trap** for direct chunk writes
4) **Biome precipitation hooks** (only affect weather-driven snow layers, not worldgen blocks)

> Rule: If a block is still appearing, identify which write path is used by logging/stack trace; do not guess.

---

## Debugging protocol (mandatory)
When a worldgen artifact appears (snow/powder snow in warm bands, wrong biome, etc.):

1) **Confirm out biome** (F3) and capture coords (x,y,z).
2) Search logs for the decision record near that location:
   - `[LAT_PICK] ... base=... out=... zone=...`
3) Determine which system wrote the offending block:
   - Feature hook logs (e.g., `[FREEZE_GUARD]`)
   - Write-path logs (e.g., `[SNOWBLOCK_GUARD]`)
4) Only then propose the smallest fix:
   - cancel the feature, or
   - rewrite the blockstate at the write choke point, gated by warm band logic.

If any of these steps lack evidence, the assistant must request the relevant file/method signature.

---

## Allowed outputs the assistant may produce
- Exact “next step” commands (PowerShell) to locate call sites (`Select-String`, `git grep`, `jar tf`, etc.)
- Minimal patch plans: 1–3 files, 1–2 commits, with revert path
- Explicit “proof” instrumentation plans: single-shot logs, counters, or stack trace capture
- Release discipline steps: build, jar verify, tag placement, and Modrinth upload checklist

---

## Forbidden behaviors
- Inventing a “worldgen order” not supported by code/logs
- Reassigning biomes to bands to “fix” bugs
- Removing vanilla biomes because they’re “rare” or “problematic”
- Proposing broad refactors when a write-path guard would solve the symptom
- Shipping releases from branches polluted with extracted MC sources or tooling folders

---

## Reminder: one variable at a time
When making changes:
- One change per commit
- Test
- Tag savepoint if it touches generation
- Then proceed
