# beta.26 pale_garden Atlas Proof

Date: 2026-06-10

Root: `<home>/CascadeProjects/Latitude-port-1.4.0-1.21.11`

Branch: `port/1.4.0-beta-1.21.11`

Code commit under proof: `1b5e043c`

## Command

```bash
./gradlew --no-daemon runBiomePreview --args="--seed 2591890304012655616 --size small --step 16 --emitbiomeindex --out tmp/beta26-palegarden-atlas"
```

Result: `BUILD SUCCESSFUL`

## Fresh Artifacts

- `run-headless/tmp/beta26-palegarden-atlas/seed_2591890304012655616/R5000/step16/biomes.png`
- `run-headless/tmp/beta26-palegarden-atlas/seed_2591890304012655616/R5000/step16/biome_ids.png`
- `run-headless/tmp/beta26-palegarden-atlas/seed_2591890304012655616/R5000/step16/biomes.txt`
- `run-headless/tmp/beta26-palegarden-atlas/seed_2591890304012655616/R5000/step16/world_biome_inventory.json`
- `run-headless/tmp/beta26-palegarden-atlas/seed_2591890304012655616/R5000/step16/biome_palette.json`

## Sidecar Summary

- `minecraft:pale_garden` present: yes
- `minecraft:pale_garden` sampled hits: 1472
- `minecraft:pale_garden` first seen: x=-2472, z=-2648, Temperate 48N

## Connected-Component Check

Source: `biome_ids.png`

- target palette/index: 28 (`minecraft:pale_garden`)
- component count: 1
- total target pixels: 1472
- component bbox in pixels: `(151,147)..(201,185)`
- component bbox in blocks: x `[-2584..-1784]`, z `[-2648..-2040]`

Verdict: pale_garden Atlas gate passed for this seed/radius/step; no scattered pale_garden specks were detected in the sampled map.

Limit: this is Atlas biome proof only. It does not prove tree placement, alpine surface blocks, snow cap feel, or manual F3 Y calibration.
