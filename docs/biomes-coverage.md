# Biome Coverage Audit

Authoritative checklist for vanilla biomes. Each entry includes where it is selected in Latitude:

- band tag (e.g., `lat_temperate`)
- adjacency/preservation (beach/river preservation)
- special-case override
- vanilla (not overridden)

Legend: check a box once coverage is verified in a new world.

## surface_to_band

| ✅ | Biome | Where selected |
| --- | --- | --- |
| [ ] | minecraft:plains | band tag: `lat_equator`, `lat_temperate` |
| [ ] | minecraft:sunflower_plains | rare override from plains in `LatitudeBiomes` (equator/temperate) |
| [ ] | minecraft:forest | band tag: `lat_equator`, `lat_temperate` |
| [ ] | minecraft:flower_forest | band tag: `lat_equator`, `lat_temperate` |
| [ ] | minecraft:birch_forest | band tag: `lat_equator`, `lat_temperate` |
| [ ] | minecraft:old_growth_birch_forest | band tag: `lat_temperate` |
| [ ] | minecraft:dark_forest | band tag: `lat_temperate` |
| [ ] | minecraft:pale_garden | rare override from dark_forest in `LatitudeBiomes` (temperate) |
| [ ] | minecraft:cherry_grove | band tag: `lat_temperate` |
| [ ] | minecraft:taiga | band tag: `lat_temperate`, `lat_subpolar` |
| [ ] | minecraft:snowy_taiga | band tag: `lat_subpolar` |
| [ ] | minecraft:old_growth_pine_taiga | band tag: `lat_temperate`, `lat_subpolar` |
| [ ] | minecraft:old_growth_spruce_taiga | band tag: `lat_subpolar` |
| [ ] | minecraft:savanna | band tag: `lat_tropical`, `lat_trans_arid_tropics_2` |
| [ ] | minecraft:savanna_plateau | band tag: `lat_tropical`, `lat_trans_arid_tropics_2` |
| [ ] | minecraft:windswept_savanna | band tag: `lat_tropical` |
| [ ] | minecraft:windswept_hills | band tag: `lat_temperate` |
| [ ] | minecraft:windswept_forest | band tag: `lat_temperate` |
| [ ] | minecraft:windswept_gravelly_hills | band tag: `lat_temperate` |
| [ ] | minecraft:jungle | band tag: `lat_tropical`, `lat_tropics` |
| [ ] | minecraft:sparse_jungle | band tag: `lat_tropical`, `lat_tropics`, `lat_trans_arid_tropics_2` |
| [ ] | minecraft:bamboo_jungle | band tag: `lat_tropical`, `lat_tropics` |
| [ ] | minecraft:swamp | band tag: `lat_tropical` |
| [ ] | minecraft:mangrove_swamp | band tag: `lat_tropical` |
| [ ] | minecraft:desert | band tag: `lat_tropical`, `lat_arid`, `lat_trans_arid_tropics_1` |
| [ ] | minecraft:badlands | band tag: `lat_tropical`, `lat_arid`, `lat_trans_arid_tropics_1` |
| [ ] | minecraft:wooded_badlands | band tag: `lat_tropical`, `lat_arid`, `lat_trans_arid_tropics_1` |
| [ ] | minecraft:eroded_badlands | band tag: `lat_arid`, `lat_trans_arid_tropics_1` |
| [ ] | minecraft:snowy_plains | band tag: `lat_subpolar` |
| [ ] | minecraft:ice_spikes | band tag: `lat_polar` |
| [ ] | minecraft:meadow | band tag: `lat_temperate` |
| [ ] | minecraft:grove | band tag: `lat_temperate`, `lat_subpolar` |
| [ ] | minecraft:snowy_slopes | band tag: `lat_subpolar`, `lat_polar` |
| [ ] | minecraft:stony_peaks | rare override from meadow/windswept_hills in `LatitudeBiomes` |
| [ ] | minecraft:jagged_peaks | band tag: `lat_polar` |
| [ ] | minecraft:frozen_peaks | band tag: `lat_subpolar`, `lat_polar` |
| [ ] | minecraft:mushroom_fields | special-case deep-ocean override (rare) |

## shores_edges_only

Preserved in `LatitudeBiomes.pick` via `BiomeTags.IS_BEACH` and `BiomeTags.IS_RIVER`.

| ✅ | Biome | Where selected |
| --- | --- | --- |
| [ ] | minecraft:beach | preserved (BiomeTags.IS_BEACH) |
| [ ] | minecraft:snowy_beach | preserved (BiomeTags.IS_BEACH) |
| [ ] | minecraft:stony_shore | preserved (BiomeTags.IS_BEACH) |
| [ ] | minecraft:river | preserved (BiomeTags.IS_RIVER) |
| [ ] | minecraft:frozen_river | preserved (BiomeTags.IS_RIVER) |

## underground_preserve

| ✅ | Biome | Where selected |
| --- | --- | --- |
| [ ] | minecraft:dripstone_caves | vanilla (not overridden) |
| [ ] | minecraft:lush_caves | vanilla (not overridden) |
| [ ] | minecraft:deep_dark | vanilla (not overridden) |

## oceans

| ✅ | Biome | Where selected |
| --- | --- | --- |
| [ ] | minecraft:ocean | band tag: `lat_ocean_temperate` |
| [ ] | minecraft:deep_ocean | band tag: `lat_ocean_temperate` |
| [ ] | minecraft:warm_ocean | band tag: `lat_ocean_tropical` |
| [ ] | minecraft:lukewarm_ocean | band tag: `lat_ocean_tropical` |
| [ ] | minecraft:deep_lukewarm_ocean | band tag: `lat_ocean_tropical` |
| [ ] | minecraft:cold_ocean | band tag: `lat_ocean_subpolar` |
| [ ] | minecraft:deep_cold_ocean | band tag: `lat_ocean_subpolar` |
| [ ] | minecraft:frozen_ocean | band tag: `lat_ocean_polar` |
| [ ] | minecraft:deep_frozen_ocean | band tag: `lat_ocean_polar` |
