---
name: latitude-biome-authority
description: >
  Authoritative list of all vanilla Minecraft biomes and the latitude bands
  they are allowed to generate in for the Latitude (Globe) mod. This skill
  forbids guessing, omission, or reassignment of biomes and serves as the
  single source of truth for biome inclusion and climate eligibility.

---
# Latitude Biome Authority — Vanilla Minecraft (Java 1.21.11)
_Generated: 2026-01-31_

## RULES (MANDATORY)
- Every vanilla biome listed here **must exist** in at least one valid generation path.
- Windsurf must **never invent, remove, or reassign** biomes outside this table.
- If a biome is missing in-world, the cause is **tag omission or logic error**, not intentional rarity.
- **Latitude bands are authoritative**; variants and overlays are secondary.
- **Rarity is a design knob**, not a probability guarantee.
- Caves, rivers, oceans, Nether, and End biomes are **not latitude-decided unless explicitly stated**.
- Mountain / peak biomes are **overlays** and may restrict placement but **must not override latitude truth**.

---

## Latitude Bands

* **Equator** — hottest + wettest  
* **Tropics** — hot + wet-ish  
* **Arid** — hot + dry  
* **Temperate** — mild  
* **Subpolar** — cold  
* **Polar** — coldest  

**Overlay: Mountain / Peaks**  
Only allowed when “mountainness” is true (height, slope, or climate params), regardless of latitude.

---

## Plains / general land

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Plains | Temperate | Common | Core mid-latitude flatland. |
| Sunflower Plains | Temperate | Rare | Rare plains variant. |
| Savanna | Arid ↔ Tropics | Uncommon | Warm transition biome. |
| Savanna Plateau | Arid / Tropics | Uncommon | Highland warm biome. |
| Snowy Plains | Subpolar | Common | Main cold flatland. |
| Ice Spikes | Polar | Very rare | Cold extreme. |
| Mushroom Fields | Any (special) | Very rare | Special-case island biome; not latitude-driven. |

---

## Forests / woodlands

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Forest | Temperate | Common | |
| Birch Forest | Temperate | Common | |
| Flower Forest | Temperate | Rare | Discovery biome. |
| Dark Forest | Temperate | Uncommon | Dense canopy. |
| Old Growth Birch Forest | Temperate | Rare | Large birch variant. |
| Taiga | Temperate-high → Subpolar-low | Common | Key transition biome. |
| Old Growth Pine Taiga | Temperate-high → Subpolar | Uncommon | |
| Old Growth Spruce Taiga | Subpolar | Uncommon | |
| Snowy Taiga | Subpolar | Uncommon | |
| Pale Garden | Temperate (rare) | Very rare | Dark-forest-adjacent oddity. |

---

## Jungle family (warm + wet)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Jungle | Equator / Tropics | Uncommon | Dense near equator. |
| Bamboo Jungle | Equator / Tropics | Rare | |
| Sparse Jungle | Tropics | Uncommon | Transition biome. |
| Mangrove Swamp | Equator / Tropics | Rare | Must not appear in cool bands. |

---

## Swamps / wetlands

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Swamp | Tropics ↔ Temperate-low | Uncommon | Warm temperate transition. |
| Mangrove Swamp | Equator / Tropics | Rare | Warm-only swamp. |

---

## Sandy / dry / badlands

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Desert | Arid | Uncommon | |
| Badlands | Arid | Rare | |
| Wooded Badlands | Arid | Rare | |
| Eroded Badlands | Arid | Very rare | Extreme terrain. |

---

## Mountains / highlands (overlay-only)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Meadow | Temperate (overlay) | Uncommon | |
| Grove | Temperate-high / Subpolar (overlay) | Uncommon | Must be explicitly preserved. |
| Cherry Grove | Temperate (overlay) | Rare | |
| Windswept Hills | Temperate (overlay) | Uncommon | |
| Windswept Forest | Temperate (overlay) | Uncommon | |
| Windswept Gravelly Hills | Temperate (overlay) | Rare | |
| Windswept Savanna | Arid / Tropics (overlay) | Uncommon | |
| Stony Peaks | Arid / Tropics / Temperate (overlay) | Rare | |
| Jagged Peaks | Subpolar / Polar (overlay) | Rare | |
| Frozen Peaks | Subpolar / Polar (overlay) | Rare | |
| Snowy Slopes | Subpolar / Polar (overlay) | Uncommon | |
| Stony Shore | Any (adjacency overlay) | Uncommon | Mountains touching ocean only. |

---

## Caves (not latitude-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Lush Caves | Any | Uncommon | |
| Dripstone Caves | Any | Uncommon | |
| Deep Dark | Any | Rare | |

---

## Beaches / shores (adjacency-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Beach | Equator / Tropics / Arid / Temperate | Common | |
| Snowy Beach | Subpolar / Polar | Uncommon | |
| Stony Shore | Any (overlay) | Uncommon | |

---

## Rivers (temperature-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| River | Equator / Tropics / Arid / Temperate | Common | |
| Frozen River | Subpolar / Polar | Uncommon | |

---

## Oceans (temperature-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Warm Ocean | Equator / Tropics | Uncommon | |
| Lukewarm Ocean | Tropics / Temperate-low | Uncommon | |
| Ocean | Temperate | Common | |
| Cold Ocean | Subpolar | Uncommon | |
| Frozen Ocean | Polar | Uncommon | |
| Deep Lukewarm Ocean | Depth overlay | Uncommon | |
| Deep Ocean | Depth overlay | Uncommon | |
| Deep Cold Ocean | Depth overlay | Uncommon | |
| Deep Frozen Ocean | Depth overlay | Uncommon | |

---

## The Nether (not latitude-driven)

| Biome | Band(s) | Rarity |
| --- | --- | --- |
| Nether Wastes | N/A | Common |
| Crimson Forest | N/A | Common |
| Warped Forest | N/A | Common |
| Soul Sand Valley | N/A | Uncommon |
| Basalt Deltas | N/A | Uncommon |

---

## The End (not latitude-driven)

| Biome | Band(s) | Rarity |
| --- | --- | --- |
| The End | N/A | Special |
| End Highlands | N/A | Common |
| End Midlands | N/A | Common |
| End Barrens | N/A | Common |
| Small End Islands | N/A | Uncommon |
| The Void | N/A | Special |
