---
name: latitude-biome-selection-contract
description: Defines the contract between vanilla “base” biomes and Latitude “out” biomes. Prevents incorrect assumptions about F3 biome, temperature/precip flags, and feature placement. Provides the required debugging interpretation for LAT_PICK logs.
---

# Latitude — Biome Selection Contract (Authoritative)

This skill prevents a common error:
> “F3 says biome = jungle, therefore cold/snow features cannot trigger.”

That is false when:
- vanilla selects a cold **base** biome, and
- Latitude overrides to a warm **out** biome, but
- some vanilla systems still consult base-biome temperature/precip rules during placement.

---

## Definitions
- **Base biome**: the vanilla biome prior to Latitude override. Logged as `base=...` in `[LAT_PICK]`.
- **Out biome**: the biome Latitude returns to worldgen. Logged as `out=...` and typically what F3 shows.
- **Wrapper illusion**: you can see `out` while generation decisions still reference `base`.

---

## Contract rules (must be respected)
1) Latitude may override biome output by latitude band (Z-based), but vanilla may still:
   - consult base biome temperature,
   - consult precipitation flags,
   - run features keyed off base-biome climate.
2) Therefore:
   - Seeing cold blocks/features in a warm F3 biome implies **base leakage** or **write-path placement**, not that F3 is “wrong”.
3) Fixes must be either:
   - ensure temperature/precip checks use out-biome (harder), OR
   - block the offending feature/write path in disallowed bands (preferred for robustness).

---

## How to interpret `[LAT_PICK]` logs
Given a log line like:
- `zone=EQUATOR base=minecraft:snowy_taiga out=minecraft:jungle` 

Interpretation:
- Vanilla asked for a biome and would have used `snowy_taiga`.
- Latitude returned `jungle`.
- Any system that still consults base-biome climate may try to place snow/ice even though out-biome is warm.

This is expected in the presence of base leakage and does not mean Latitude selection failed.

### Representative `[LAT_PICK]` template

```
[LAT_PICK]
x=<x> z=<z> absZ=<absZ> radius=<radius> t=<t>
zone=<EQUATOR|TROPICS|ARID|TEMPERATE|SUBPOLAR|POLAR>
base=<minecraft:...>
out=<minecraft:...>
beachOverride=<true|false>
rareOverride=<true|false>
mangroveDecision=<...> cont=<...> ero=<...> weird=<...>
```

How to read it:
- If `out` is warm but `base` is cold, vanilla temperature-driven features may still attempt cold placements unless guarded (e.g., FreezeTopLayer).
- That’s not a biome-table bug; it’s a base-leakage / write-path issue.

---

## Debugging obligations
When a “contradiction” occurs (warm biome but cold artifacts):
1) Capture coords + F3 out-biome.
2) Find nearby `[LAT_PICK]` entries:
   - confirm `zone` and compare `base` vs `out`.
3) Identify write path:
   - feature hook logs (e.g., `FreezeTopLayerFeature`)
   - ChunkRegion/ProtoChunk traps
4) Apply minimal fix (feature cancel / write-path rewrite).

Do not propose band-table changes based only on F3.

---

## Canonical example: warm-band snow
Observed:
- F3: `minecraft:jungle` 
- Snow blocks or powder snow appear at cave mouths.

Common cause:
- `[LAT_PICK] base=snowy_plains out=jungle` 
- Vanilla `FreezeTopLayerFeature` consults base biome climate → attempts snow.
- LatitudeSnowGuard blocks/cancels in warm bands.

---

## Forbidden assumptions
- “Out biome implies all placements consult out biome.”
- “If snow appears, Latitude chose the wrong biome.”
- “Fix by moving snowy biomes out of authority table.”
- “Fix by rewriting MaterialRules first.”

---

## Required assistant output when diagnosing biome contradictions
The assistant must provide:
- base vs out interpretation
- suspected offender (feature or write path)
- the minimum guard/cancel plan
- a proof plan (log/counter) gated behind a debug flag
