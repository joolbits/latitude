---
name: latitude-zone-math-authority
description: Canonical authority for Latitude band math (radius, t, thresholds, and helpers). Prevents hardcoding, duplicated thresholds, or inconsistent warm/cold checks across worldgen, guards, fog, hazards, HUD, and features.
---

# Latitude — Zone Math Authority (Authoritative)

This skill ensures every system in Latitude uses the **same** band math:
- biome picking
- warm/cold guards (snow/ice/etc.)
- E/W storm fog borders
- hazards and effects
- HUD displays

If different parts of the code use different math, the mod becomes inconsistent and bugs reappear.

---

## Primary invariant: derive radius from world border
**Never hardcode radius.**

Use:
- `radius = world.getWorldBorder().getSize() * 0.5` 

Notes:
- WorldBorder size is a diameter-like value; radius is half.
- All band logic must use the same radius for the same world.

---

## Canonical latitude coordinate
Latitude is based on Z (north/south):

- `absZ = Math.abs(pos.getZ())` 
- `t = absZ / radius` 

Where:
- `t` is normalized latitude from 0.0 (equator) to 1.0 (border/pole).

Rules:
- Clamp t if needed: `t = Math.min(1.0, Math.max(0.0, t))` 
- Do not use X for latitude bands.
- Any “zone label” shown to player must correspond to this same t.

---

## Single source of truth for thresholds
There must be exactly one implementation that maps `t -> zone/band`:
- `LatitudeMath` or equivalent helper

All other systems must call that helper, not re-implement thresholds.

Allowed call shapes (examples):
- `LatitudeMath.zoneFor(world, pos)` 
- `LatitudeMath.zoneForT(t)` 
- `LatitudeZones.fromT(t)` 

If the helper does not exist, the assistant must:
1) locate where thresholds currently live, and
2) refactor so thresholds live in one place, then
3) update all call sites to reference it.

---

## Band naming contract
Band names used across logs/HUD/guards must be consistent.
Example canonical set:
- EQUATOR
- TROPICAL
- SUBTROPICAL
- TEMPERATE
- SUBPOLAR
- POLAR

If the project uses different names, mirror the project’s names exactly.

---

## Warm band / cold band predicates (required helpers)
Add and use helpers (names optional, behavior mandatory):

- `isWarmBand(zone)` returns true for bands that must never produce snow family blocks.
- `isColdBand(zone)` returns true for snow/ice-permitted bands.

Do not duplicate “warm band = t < X” logic in random places.

---

## Sea level authority
Avoid hardcoding y=63 unless intentionally matching vanilla sea level.

Preferred:
- `seaLevel = world.getSeaLevel()` (if available at the call site)

If not available, use a single project constant and document it.

---

## Debug output format requirements (when band math is involved)
If logging band math decisions, include:
- x, z, absZ
- radius
- t
- zone result

Example:
- `x=1514 z=-120 absZ=120 radius=10000 t=0.012 zone=EQUATOR` 

All such logs must be gated behind a debug flag.

---

## Forbidden behaviors
- Hardcoding radius (e.g., `radius=10000`) except in tests/tools
- Copying threshold numbers into multiple files
- Mixing X and Z for latitude decisions
- Using different t formulas for different features
- “Fixing” bugs by adjusting thresholds without referencing the authority helper

---

## Required assistant output when math is questioned
When asked “why is zone X here?” or “why is guard firing?” the assistant must:
1) show the formula used
2) show the radius source
3) show t computation
4) point to the helper/constant location in code
5) propose changes only in that single authority location
