---
name: biomes
description: a list of all vanilla biomes that should be included in the Latitude (Globe) mod
---

Here is a list of all vanilla biomes that should be included in the Latitude (Globe) mod:

# Vanilla Minecraft Biomes -> Latitude Bands (Java 1.21.11)

_Generated: 2026-01-31_

## Bands

* **Equator** (hottest + wettest)
* **Tropics** (hot + wet-ish)
* **Arid** (hot + dry)
* **Temperate** (mild)
* **Subpolar** (cold)
* **Polar** (coldest)
* **Overlay: Mountain/Peaks = only allowed when “mountainness” is true (height/slope or climate params), regardless of latitude.**

> Notes

> * This mapping is designed for **Latitude** (Minecraft Java 1.21.11).
> * **Rarity** is a design knob for your tag pools/weights (Common/Uncommon/Rare/Very rare/Special), not a claim about exact vanilla probabilities.

---

## Plains / general land

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Plains | Temperate | Common | Core “default” mid-latitude flatland. |
| Sunflower Plains | Temperate | Rare | Rare plains variant; keep as spice. |
| Savanna | Arid ↔ Tropics | Uncommon | Warm transition biome; use on arid edge / warm band. |
| Savanna Plateau | Arid / Tropics | Uncommon | Highland flavor for warm areas. |
| Snowy Plains | Subpolar | Common | Main cold flatland. |
| Ice Spikes | Polar | Very rare | Cold extreme; keep scarce. |
| Mushroom Fields | Any (special exception) | Very rare | Treat as special-case island biome, not latitude-driven. |
---

## Forests / woodlands

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Forest | Temperate | Common |  |
| Birch Forest | Temperate | Common |  |
| Flower Forest | Temperate | Rare | Good “find” biome; keep rarer than Forest. |
| Dark Forest | Temperate | Uncommon | Heavier canopy; good for “moody” temperate regions. |
| Old Growth Birch Forest | Temperate | Rare | Large birch variant; special temperate. |
| Taiga | Temperate-high → Subpolar-low | Common | Should be common near the temperate↔subpolar boundary. |
| Old Growth Pine Taiga | Temperate-high → Subpolar | Uncommon | Variant taiga; moderate rarity. |
| Old Growth Spruce Taiga | Subpolar | Uncommon | Colder, denser taiga variant. |
| Snowy Taiga | Subpolar | Uncommon | Snowy spruce forest; not too rare. |
| Pale Garden | Temperate (rare) | Very rare | Place as a rare “dark-forest-weirdness” temperate variant. |
---

## Jungle family (warm + wet)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Jungle | Equator / Tropics | Uncommon | Equator = denser jungle; tropics = more mixed. |
| Bamboo Jungle | Equator / Tropics | Rare | Rare-ish jungle variant. |
| Sparse Jungle | Tropics | Uncommon | Perfect blend biome near equator↔tropics. |
| Mangrove Swamp | Equator / Tropics | Rare | Warm swamp; keep out of temperate/subpolar. |
---

## Swamps / wetlands

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Swamp | Tropics ↔ Temperate-low | Uncommon | Warm-ish temperate; good transition biome. |
| Mangrove Swamp | Equator / Tropics | Rare | Listed above too; keep warm. |
---

## Sandy / dry / badlands

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Desert | Arid | Uncommon | Core hot-dry biome. |
| Badlands | Arid | Rare | Mesa biome; rarer than Desert. |
| Wooded Badlands | Arid | Rare | Badlands variant; keep scarce. |
| Eroded Badlands | Arid | Very rare | Spires/extreme; keep very rare. |
---

## Mountains / highlands (treat as overlays)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Meadow | Temperate (mountain overlay) | Uncommon | Great temperate mountain foothill. |
| Grove | Temperate-high / Subpolar (mountain overlay) | Uncommon | Cold foothill near mountains; often disappears if not handled specially. |
| Cherry Grove | Temperate (mountain overlay) | Rare | Keep rarer than Meadow. |
| Windswept Hills | Temperate (mountain overlay) | Uncommon |  |
| Windswept Forest | Temperate (mountain overlay) | Uncommon |  |
| Windswept Gravelly Hills | Temperate (mountain overlay) | Rare | Stonier windswept variant; keep rarer. |
| Windswept Savanna | Arid/Tropics (mountain overlay) | Uncommon | Warm mountains. |
| Stony Peaks | Arid/Tropics/Temperate (mountain overlay) | Rare | Warm peaks; keep out of subpolar/polar. |
| Jagged Peaks | Subpolar/Polar (mountain overlay) | Rare | Cold peaks. |
| Frozen Peaks | Subpolar/Polar (mountain overlay) | Rare |  |
| Snowy Slopes | Subpolar/Polar (mountain overlay) | Uncommon |  |
| Stony Shore | Any (adjacency overlay) | Uncommon | Only when mountains touch ocean. |
---

## Caves (not latitude-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Lush Caves | Any | Uncommon | Underground ecology independent of latitude. |
| Dripstone Caves | Any | Uncommon |  |
| Deep Dark | Any | Rare | Deep underground; keep rare. |
---

## Beaches / shores (adjacency-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Beach | Equator/Tropics/Arid/Temperate | Common | Non-frozen coast. |
| Snowy Beach | Subpolar/Polar | Uncommon | Frozen coast. |
| Stony Shore | Any (adjacency overlay) | Uncommon | Listed above too; cliffy coast next to mountains. |
---

## Rivers (temperature-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| River | Equator/Tropics/Arid/Temperate | Common |  |
| Frozen River | Subpolar/Polar | Uncommon |  |
---

## Oceans (temperature-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Warm Ocean | Equator/Tropics | Uncommon | Coral-capable warm seas. |
| Lukewarm Ocean | Tropics/Temperate-low | Uncommon |  |
| Ocean | Temperate | Common | Default ocean. |
| Cold Ocean | Subpolar | Uncommon |  |
| Frozen Ocean | Polar | Uncommon |  |
| Deep Lukewarm Ocean | Depth overlay (Tropics/Temperate-low) | Uncommon | Use as depth overlay, not latitude-only. |
| Deep Ocean | Depth overlay (Temperate) | Uncommon | Depth overlay. |
| Deep Cold Ocean | Depth overlay (Subpolar) | Uncommon | Depth overlay. |
| Deep Frozen Ocean | Depth overlay (Polar) | Uncommon | Depth overlay. |
---

## The Nether (not latitude-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| Nether Wastes | N/A (Nether) | Common |  |
| Crimson Forest | N/A (Nether) | Common |  |
| Warped Forest | N/A (Nether) | Common |  |
| Soul Sand Valley | N/A (Nether) | Uncommon |  |
| Basalt Deltas | N/A (Nether) | Uncommon |  |
---

## The End (not latitude-driven)

| Biome | Band(s) | Rarity | Notes |
| --- | --- | --- | --- |
| The End | N/A (End) | Special | Main island biome. |
| End Highlands | N/A (End) | Common | Outer islands main terrain. |
| End Midlands | N/A (End) | Common |  |
| End Barrens | N/A (End) | Common |  |
| Small End Islands | N/A (End) | Uncommon |  |
| The Void | N/A (special) | Special | Void biome; mostly technical/edge cases. |