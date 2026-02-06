---
name: latitude-player-facing-invariants
description: Authoritative “promise of Latitude” rules. Encodes the non-negotiable player-facing behaviors (climate continuity, band logic, no warm-band snow, intuitive exploration). Prevents technically-correct changes that break the mod’s feel.
---

# Latitude — Player-Facing Invariants (Authoritative)

This skill encodes the *promise* of Latitude to players. Any change that violates these invariants is a regression, even if it compiles.

---

## Core promise
Latitude turns Minecraft’s overworld into a planet-like climate experience:
- climate changes meaningfully with north/south travel
- bands feel continuous and intentional
- exploration “makes sense” without reading a wiki

---

## Non-negotiable invariants

### 1) Warm-band snow must be impossible
In warm bands (equator/tropical/subtropical as defined by the mod):
- No `snow_block` 
- No `snow` layers
- No `powder_snow` 
- No “frozen top layer” behavior

If any of these appear, it is a bug. Fix must use write-path guards or feature cancels, not biome reassignment.

### 2) Climate continuity over patchiness
- Bands should feel continuous across distance.
- “Islands” of totally wrong climate should be treated as bugs unless explicitly designed (and documented).

### 3) Distance must correlate with temperature
- Moving north/south changes temperature/biomes in a predictable direction.
- Any system that causes temperature to decorrelate from latitude needs strong justification.

### 4) All vanilla biomes still exist
- Unless explicitly excluded by the biome authority table, every vanilla biome must remain obtainable somewhere.
- Fixing a bug by effectively deleting a biome is forbidden.

### 5) The mod must be playable without debug knowledge
- Default gameplay should not require JVM flags.
- Debug features must not leak into normal play (no auto-open screens, no actionbar spam).

### 6) “Surprises” require communication
If the mod introduces hazards/effects (storms, polar danger, etc.):
- players must receive clear in-game feedback (HUD text, warning, particles, etc.)
- avoid silent punishment

---

## Required assistant behavior
When proposing changes, the assistant must state:
- which invariants are affected
- whether the change preserves them
- how to test the invariants quickly (minimal proof steps)

---

## Forbidden changes
- Adjusting biome bands to hide symptoms
- Shipping with debug toggles enabled by default
- Removing access to vanilla biomes because they are “problematic”
- Making climate feel random/patchy to “increase variety”
