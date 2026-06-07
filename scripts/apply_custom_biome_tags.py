#!/usr/bin/env python3
"""Stage-2 custom-biome TAG admission for the 1.4-beta backport.

Idempotently appends `{"id": "<mod>:<biome>", "required": false}` entries into
the existing per-version `lat_*` worldgen biome tag files, preserving each
version's own vanilla membership. Absent-mod-safe (`required:false`).

Climate placement mirrors the proven 26.1.2 set (commit 0965687c family) and
extends per-version with the popular biome-mod ecosystem for that MC line.

Usage:
    apply_custom_biome_tags.py <tagdir> <modset>
      tagdir : .../data/globe/tags/worldgen/biome
      modset : comma list from {bop,terralith,promenade,natures_spirit,
               regions_unexplored,byg,terrestria,wilder_wild,traverse}
"""
import json
import sys
import os

# tag-file (without .json) -> list of "namespace:biome" custom IDs to admit.
# Grouped by mod so the per-version driver can include/exclude whole mods.
#
# Band climate analogs:
#   subpolar          = boreal / snowy taiga (~50-66 deg)
#   temperate(_*)     = mid-lat forest/plains (~35-50 deg); _mountain = alpine
#   subtropical_humid = humid subtropical (SE-US / E-China, ~25-35 deg)
#   arid(_*)          = hot desert belt (~20-35 deg)
#   trans_arid_tropics_1/2 = savanna / semi-arid transition
#   tropics(_*)       = jungle / rainforest (~10-25 deg)
#   equator(_*)       = deep rainforest (0-10 deg)

MODSETS = {
    # ---- Biomes O' Plenty (PROVEN base, mirrors 26.1.2) ----
    "bop": {
        "lat_subpolar_primary": [
            "biomesoplenty:tundra", "biomesoplenty:snowy_maple_woods",
            "biomesoplenty:coniferous_forest", "biomesoplenty:fir_clearing",
            "biomesoplenty:snowy_coniferous_forest", "biomesoplenty:snowy_fir_clearing",
        ],
        "lat_temperate_primary": [
            "biomesoplenty:seasonal_forest", "biomesoplenty:maple_woods",
        ],
        "lat_temperate_secondary": [
            "biomesoplenty:woodland", "biomesoplenty:old_growth_woodland",
            "biomesoplenty:forested_field", "biomesoplenty:orchard",
            "biomesoplenty:pasture",
        ],
        "lat_temperate_accent": ["biomesoplenty:prairie"],
        "lat_arid_accent": ["biomesoplenty:dryland"],
        "lat_trans_arid_tropics_1_secondary": [
            "biomesoplenty:scrubland", "biomesoplenty:shrubland",
        ],
        "lat_trans_arid_tropics_2_secondary": [
            "biomesoplenty:rocky_shrubland", "biomesoplenty:wasteland",
            "biomesoplenty:wasteland_steppe",
        ],
        "lat_tropics_secondary": ["biomesoplenty:tropics"],
    },
    # ---- Terralith (PROVEN base, mirrors 26.1.2) ----
    "terralith": {
        "lat_subpolar_primary": [
            "terralith:siberian_taiga", "terralith:wintry_forest",
        ],
        "lat_subpolar_secondary": [
            "terralith:siberian_grove", "terralith:wintry_lowlands",
            "terralith:snowy_maple_forest", "terralith:snowy_shield",
        ],
        "lat_temperate_secondary": [
            "terralith:birch_taiga", "terralith:sakura_grove",
            "terralith:sakura_valley",
        ],
        "lat_temperate_accent": [
            "terralith:blooming_valley", "terralith:lavender_valley",
            "terralith:valley_clearing",
        ],
    },
    # ---- Promenade (PROVEN base, mirrors 26.1.2 commit 0965687c) ----
    "promenade": {
        "lat_subpolar_primary": ["promenade:glacarian_taiga"],
        "lat_temperate_secondary": [
            "promenade:blush_sakura_grove", "promenade:cotton_sakura_grove",
        ],
    },
    # ---- Nature's Spirit (1.21.1 / 1.20.1 ecosystem; IDs from wiki, snake_case) ----
    #   NEEDS-JAR-VALIDATION: no jar available for these MC versions.
    "natures_spirit": {
        "lat_subpolar_primary": [
            "natures_spirit:boreal_taiga", "natures_spirit:fir_forest",
            "natures_spirit:coniferous_covert",
        ],
        "lat_temperate_primary": [
            "natures_spirit:aspen_forest", "natures_spirit:maple_woodlands",
        ],
        "lat_temperate_secondary": [
            "natures_spirit:blooming_sugi_forest", "natures_spirit:amber_covert",
        ],
        "lat_temperate_accent": [
            "natures_spirit:lavender_fields", "natures_spirit:heather_fields",
            "natures_spirit:marigold_meadows", "natures_spirit:carnation_fields",
            "natures_spirit:prairie",
        ],
        "lat_temperate_mountain": [
            "natures_spirit:alpine_highlands", "natures_spirit:alpine_clearings",
            "natures_spirit:red_peaks",
        ],
        "lat_arid_accent": [
            "natures_spirit:drylands", "natures_spirit:blooming_dunes",
            "natures_spirit:lively_dunes",
        ],
        "lat_trans_arid_tropics_1_secondary": [
            "natures_spirit:arid_savanna", "natures_spirit:oak_savanna",
            "natures_spirit:chaparral",
        ],
        "lat_trans_arid_tropics_2_secondary": [
            "natures_spirit:arid_highlands", "natures_spirit:flowering_shrubland",
        ],
        "lat_tropics_secondary": ["natures_spirit:bamboo_wetlands"],
    },
    # ---- Regions Unexplored (1.21.1 / 1.20.1 ecosystem; IDs verified from gist list) ----
    #   NEEDS-JAR-VALIDATION: no jar available for these MC versions.
    "regions_unexplored": {
        "lat_polar_secondary": [
            "regions_unexplored:frozen_tundra", "regions_unexplored:frozen_pine_taiga",
        ],
        "lat_subpolar_primary": [
            "regions_unexplored:boreal_taiga", "regions_unexplored:cold_boreal_taiga",
            "regions_unexplored:pine_taiga", "regions_unexplored:blackwood_taiga",
        ],
        "lat_subpolar_secondary": [
            "regions_unexplored:golden_boreal_taiga", "regions_unexplored:pine_slopes",
        ],
        "lat_temperate_primary": [
            "regions_unexplored:deciduous_forest", "regions_unexplored:maple_forest",
            "regions_unexplored:silver_birch_forest",
        ],
        "lat_temperate_secondary": [
            "regions_unexplored:autumnal_maple_forest", "regions_unexplored:redwoods",
            "regions_unexplored:magnolia_woodland", "regions_unexplored:willow_forest",
        ],
        "lat_temperate_accent": [
            "regions_unexplored:flower_fields", "regions_unexplored:prairie",
            "regions_unexplored:clover_plains", "regions_unexplored:poppy_fields",
        ],
        "lat_temperate_mountain": [
            "regions_unexplored:mountains", "regions_unexplored:highland_fields",
        ],
        # subtropical-humid band does not exist in the 1.3.0 baseline tag set;
        # bayou (tropical wetland) + eucalyptus (humid) home into tropics_secondary.
        "lat_arid_accent": [
            "regions_unexplored:joshua_desert", "regions_unexplored:saguaro_desert",
            "regions_unexplored:outback", "regions_unexplored:arid_mountains",
        ],
        "lat_trans_arid_tropics_1_secondary": [
            "regions_unexplored:baobab_savanna", "regions_unexplored:steppe",
            "regions_unexplored:dry_brushland",
        ],
        "lat_trans_arid_tropics_2_secondary": [
            "regions_unexplored:shrubland",
        ],
        "lat_tropics_secondary": [
            "regions_unexplored:rainforest", "regions_unexplored:sparse_rainforest",
            "regions_unexplored:tropics", "regions_unexplored:bayou",
            "regions_unexplored:eucalyptus_forest",
        ],
    },
    # ======================================================================
    # STAGE 3 (2026-06-06): broaden to more popular biome mods, ALL versions.
    # Real biome IDs verified from source/jar (see RESULTS.md Stage 3).
    # All entries required:false (absent-mod-safe). Ambiguous placements are
    # marked with an inline "# REVIEW" comment and listed under
    # NEEDS-PEETSA-PLACEMENT-REVIEW in the RESULTS report.
    # ======================================================================

    # ---- Oh The Biomes You'll Go (BYG) — large (~50 overworld biomes). ----
    #   IDs from Potion-Studios/BYG BYGBiomes.java (overworld + beach only;
    #   nether/end excluded). Climate by name analog; the genuinely uncertain
    #   ones are flagged REVIEW. NEEDS-JAR-VALIDATION (no BYG jar on machine).
    "byg": {
        "lat_polar_secondary": [
            "byg:shattered_glacier",            # glacier -> polar
        ],
        "lat_subpolar_primary": [
            "byg:frosted_taiga", "byg:frosted_coniferous_forest",
            "byg:cardinal_tundra", "byg:borealis_grove",
        ],
        "lat_subpolar_secondary": [
            "byg:maple_taiga", "byg:autumnal_taiga",
            "byg:canadian_shield",              # boreal shield
        ],
        "lat_temperate_primary": [
            "byg:aspen_forest", "byg:red_oak_forest", "byg:zelkova_forest",
            "byg:temperate_grove", "byg:autumnal_forest", "byg:autumnal_valley",
        ],
        "lat_temperate_secondary": [
            "byg:black_forest", "byg:ebony_woods", "byg:forgotten_forest",
            "byg:weeping_witch_forest", "byg:cherry_blossom_forest",
            "byg:jacaranda_forest",             # REVIEW: jacaranda is subtropical IRL; placed temperate (flowering analog)
            "byg:redwood_thicket", "byg:cika_woods", "byg:fragment_forest",
        ],
        "lat_temperate_accent": [
            "byg:rose_fields", "byg:allium_fields", "byg:amaranth_fields",
            "byg:prairie", "byg:orchard", "byg:coconino_meadow",
            "byg:twilight_meadow",
        ],
        "lat_temperate_mountain": [
            "byg:howling_peaks", "byg:skyris_vale", "byg:dacite_ridges",
            "byg:crag_gardens",                 # REVIEW: rocky garden, alpine guess
        ],
        "lat_arid_accent": [
            "byg:mojave_desert", "byg:atacama_desert", "byg:windswept_desert",
            "byg:red_rock_valley", "byg:dead_sea",
            "byg:sierra_badlands",              # badlands analog -> arid
        ],
        "lat_trans_arid_tropics_1_secondary": [
            "byg:baobab_savanna", "byg:araucaria_savanna",
            "byg:firecracker_shrubland",
        ],
        "lat_tropics_secondary": [
            "byg:tropical_rainforest", "byg:guiana_shield", "byg:bayou",
            "byg:white_mangrove_marshes", "byg:cypress_swamplands",
            "byg:temperate_rainforest",         # REVIEW: WW/RU call this humid-warm; BYG temperate_rainforest is wet -> tropics
            "byg:lush_stacks",                  # REVIEW: lush wet stacks, tropical guess
            "byg:coniferous_forest",            # REVIEW: name says coniferous(cold) but BYG places it warm/humid; left in tropics, FLAG
        ],
        # UNADMITTED on purpose (no confident land band): byg:rainbow_beach,
        # byg:basalt_barrera, byg:dacite_shore (beaches — need a coast gate).
    },
    # ---- Terrestria (TerraformersMC) — IDs from TerrestriaBiomes.java. ----
    #   NEEDS-JAR-VALIDATION (no Terrestria jar on machine).
    "terrestria": {
        "lat_subpolar_primary": [
            "terrestria:snowy_hemlock_forest",
        ],
        "lat_subpolar_secondary": [
            "terrestria:snowy_hemlock_treeline",
        ],
        "lat_temperate_primary": [
            "terrestria:redwood_forest", "terrestria:lush_redwood_forest",
            "terrestria:dense_woodlands", "terrestria:japanese_maple_forest",
        ],
        "lat_temperate_secondary": [
            "terrestria:cypress_forest", "terrestria:sakura_forest",
            "terrestria:hemlock_rainforest", "terrestria:hemlock_treeline",
            "terrestria:windswept_redwood_forest",
        ],
        "lat_temperate_mountain": [
            "terrestria:caldera", "terrestria:canyon",
        ],
        "lat_arid_accent": [
            "terrestria:dunes", "terrestria:lush_desert", "terrestria:outback",
        ],
        "lat_tropics_secondary": [
            "terrestria:cypress_swamp", "terrestria:rainbow_rainforest",
            "terrestria:oasis",                 # REVIEW: oasis is desert-water; placed tropics(humid) — could be arid
            "terrestria:volcanic_island",       # REVIEW: tropical volcanic island guess
        ],
    },
    # ---- Wilder Wild — IDs + temp/downfall VERIFIED from the 26.1.2 jar ----
    #   (WilderWild-4.2.9-mc26.1.jar worldgen/biome/*.json). Caves/coast/river
    #   specials excluded (need cave/coast/river gates, not lat bands).
    #   26.1.2 jar IS present -> atlas-validatable on the 26.1.2 line.
    "wilder_wild": {
        "lat_subpolar_primary": [
            "wilderwild:snowy_old_growth_pine_taiga",   # temp -0.5
            "wilderwild:snowy_dying_mixed_forest",      # temp -0.4
            "wilderwild:snowy_dying_forest",            # temp 0.05
            "wilderwild:tundra",                        # temp 0.25
        ],
        "lat_subpolar_secondary": [
            "wilderwild:dark_taiga",                    # temp 0.45
            "wilderwild:old_growth_birch_taiga",        # temp 0.45
            "wilderwild:birch_taiga",                   # temp 0.45
        ],
        "lat_temperate_primary": [
            "wilderwild:mixed_forest",                  # temp 0.5
            "wilderwild:maple_forest",                  # temp 0.6
            "wilderwild:dark_birch_forest",             # temp 0.65
            "wilderwild:semi_birch_forest",             # temp 0.65
        ],
        "lat_temperate_secondary": [
            "wilderwild:dying_forest",                  # temp 0.35
            "wilderwild:dying_mixed_forest",            # temp 0.35
            "wilderwild:old_growth_dark_forest",        # temp 0.7
            "wilderwild:sparse_forest",                 # temp 0.75
        ],
        "lat_temperate_accent": [
            "wilderwild:flower_field",                  # temp 0.8, floral
        ],
        "lat_arid_accent": [
            "wilderwild:arid_forest",                   # temp 1.75 downfall 0.05
            "wilderwild:parched_forest",                # temp 1.35 downfall 0.2
        ],
        "lat_trans_arid_tropics_1_secondary": [
            "wilderwild:arid_savanna",                  # temp 2.0 downfall 0.0
        ],
        "lat_tropics_secondary": [
            "wilderwild:rainforest",                    # temp 0.7 downfall 0.8
            "wilderwild:temperate_rainforest",          # temp 0.7 downfall 0.8
            "wilderwild:birch_jungle",                  # temp 0.825 downfall 0.85
            "wilderwild:sparse_birch_jungle",           # temp 0.825 downfall 0.85
        ],
    },
    # ---- Traverse (TerraformersMC) — IDs from TraverseBiomes.java. ----
    #   NEEDS-JAR-VALIDATION (no Traverse jar on machine).
    "traverse": {
        "lat_subpolar_primary": [
            "traverse:snowy_coniferous_forest",
        ],
        "lat_subpolar_secondary": [
            "traverse:coniferous_forest",
        ],
        "lat_temperate_primary": [
            "traverse:woodlands", "traverse:autumnal_woods",
        ],
        "lat_temperate_accent": [
            "traverse:flatlands",               # plains/meadow analog
        ],
        "lat_arid_accent": [
            "traverse:desert_shrubland",
        ],
        "lat_tropics_secondary": [
            "traverse:lush_swamp",              # REVIEW: warm wetland -> tropics; could be temperate
        ],
    },
}


def load(path):
    with open(path) as f:
        return json.load(f)


def existing_ids(values):
    ids = set()
    for v in values:
        if isinstance(v, str):
            ids.add(v)
        elif isinstance(v, dict) and "id" in v:
            ids.add(v["id"])
    return ids


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    tagdir, modset_arg = sys.argv[1], sys.argv[2]
    mods = [m.strip() for m in modset_arg.split(",") if m.strip()]
    for m in mods:
        if m not in MODSETS:
            print(f"unknown mod: {m}", file=sys.stderr)
            sys.exit(2)

    # merge per-tag additions across selected mods, preserving order
    additions = {}
    for m in mods:
        for tag, biomes in MODSETS[m].items():
            additions.setdefault(tag, [])
            for b in biomes:
                if b not in additions[tag]:
                    additions[tag].append(b)

    total_added = 0
    for tag, biomes in additions.items():
        path = os.path.join(tagdir, tag + ".json")
        if not os.path.exists(path):
            print(f"SKIP (no such tag file): {tag}", file=sys.stderr)
            continue
        data = load(path)
        vals = data.setdefault("values", [])
        have = existing_ids(vals)
        added_here = 0
        for b in biomes:
            if b in have:
                continue
            vals.append({"id": b, "required": False})
            have.add(b)
            added_here += 1
        if added_here:
            with open(path, "w") as f:
                json.dump(data, f, indent=2)
                f.write("\n")
            total_added += added_here
            print(f"  {tag}: +{added_here}")
    print(f"TOTAL custom entries added: {total_added}")


if __name__ == "__main__":
    main()
