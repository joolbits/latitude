---
name: latitude-write-path-guards
description: Canonical debugging + fix strategy for “wrong blocks in the wrong place” bugs. Forces evidence-first identification of the write path and mandates minimal, choke-point fixes (feature cancel / ChunkRegion / ProtoChunk). Forbids guessing via biome reassignment or pipeline invention.
---

# Latitude — Write-Path Guards (Authoritative)

This skill is used when:
- a block appears where it should be impossible (snow/powder snow in warm bands, ice in tropics, etc.)
- a “biome says X but blocks say Y” contradiction occurs
- a symptom persists despite precipitation/surface-rule changes

**Core rule:** Do not guess the cause. Identify the **write path** that placed the block, then block/rewrite it at the narrowest safe choke point.

---

## Definitions
- **Base biome:** vanilla biome selection prior to Latitude override (often logged as `base=`).
- **Out biome:** Latitude-selected biome returned to generation (often logged as `out=` / what F3 shows).
- **Write path:** the actual code path that calls `setBlockState` to place the offending block.

---

## Mandatory protocol (evidence-first)

### Step 1 — Capture the symptom precisely
Record:
- offending block(s) (e.g., `powder_snow`, `snow_block`)
- coords (x,y,z)
- F3 biome (out biome)
- zone/band if available

No fix proposals until these exist.

### Step 2 — Determine write path
Allowed methods (choose the least invasive that gives proof):

**A) Single-shot logs (preferred)**
- Add a guard that logs once when it sees the offending block type.
- Include:
  - block id
  - pos
  - band/zone result
  - (optional) chunk origin

**B) Stack trace capture (nuclear, one-time)**
- On first sighting, throw a controlled exception or log a stack trace.
- Use only once, then remove.

**C) Counter overlay**
- Maintain counters for:
  - calls
  - matches
  - rewrites
- Expose via debug HUD/actionbar behind a flag.

---

## Allowed choke points (use this order)

### 1) Feature-level cancel (best when a known offender exists)
Examples:
- `FreezeTopLayerFeature` (snow/ice)
- other single features when proven by stack trace

Rule:
- Cancel only in the disallowed bands/zones.
- Log once behind a debug flag.

### 2) `ChunkRegion#setBlockState` rewrite (best general guard)
- Catches many feature/placement writes during generation.
- Use `@ModifyVariable` or `@Redirect` carefully.
- Must be non-recursive.

### 3) `ProtoChunk#setBlockState` rewrite (good for direct chunk writes)
- Catches many worldgen surface placements.
- Often simpler than MaterialRules interception.

### 4) Biome precipitation hooks (last resort, limited scope)
- Only affects weather-driven snow layers / precipitation checks.
- Does not stop worldgen-placed `snow_block` or `powder_snow`.

---

## Band/zone gating rules (no hardcoding)
Warm-band detection must:
- derive radius from world border:
  - `radius = world.getWorldBorder().getSize() * 0.5` 
- compute:
  - `t = abs(z) / radius` 
- determine zone using the same helper/constants used by Latitude (do not duplicate thresholds unless unavoidable).

If a helper is not available, the assistant must:
- locate it in code first, or
- mark thresholds as UNKNOWN and ask for the file/constant.

---

## Canonical rewrite policy (snow family)
When in warm bands (equator/tropics/subtropics as defined by Latitude):

- `POWDER_SNOW` -> `AIR` 
- `SNOW` (layer) -> `AIR` 
- `SNOW_BLOCK` -> `DIRT` above sea level, else `STONE` 

All logging must be behind `-Dlatitude.debugSnowGuard=true` (or equivalent).

---

## Required safety constraints
- Guard must be **non-recursive** (do not call the same setBlockState path in a way that re-triggers).
- Guard must be band-gated (never global nukes unless explicitly set as a one-run diagnostic).
- Debug output must be gated and off by default for release.
- Fix must not change biome eligibility tables to hide the symptom.

---

## Forbidden fixes
- “Just move snowy biomes out of warm bands” (unless authority table explicitly says so)
- Reordering worldgen stages without proof
- Intercepting deep MaterialRules nested types unless all choke points above are proven insufficient
- Shipping with debug spam enabled

---

## Output format expected from the assistant
When asked to fix a block-placement bug, the assistant must produce:

1) **Hypothesis list** (max 3) tied to evidence
2) **Chosen choke point** and why
3) **Exact file(s) + mixin target + method descriptor**
4) **One commit plan** + test step
5) **Rollback path** (revert commit / toggle flag)
