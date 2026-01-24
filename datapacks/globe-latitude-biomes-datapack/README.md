# Globe: Latitude biome pools (datapack)

This datapack extends Globe's biome tag pools (`globe:*`) so you can add modded biomes (e.g., Terralith) **without editing the Globe mod jar**.

## How to add Terralith (or other modded) biomes

Edit the JSON files under:

`data/globe/tags/worldgen/biome/`

Add additional biome IDs into the `values` array, for example:

- `"terralith:<biome_id>"`

Terralith biome definitions live under `data/terralith/worldgen/biome/` inside the Terralith jar/datapack, so you can copy biome IDs from there.

- Terralith: https://modrinth.com/datapack/terralith

## Important

Worldgen is fixed at world creation; changes to these biome tags generally require creating a new world to see full effect.

- Reference discussion: https://forums.minecraftforge.net/topic/114252-1182-how-can-i-refer-to-a-datapack-biome-during-mod-init/

## Note on pack_format

This datapack uses `pack_format: 48` (works for 1.21.x; Minecraft may warn on minor version mismatches). If Minecraft complains, adjust `pack_format` accordingly.
