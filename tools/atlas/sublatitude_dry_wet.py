#!/usr/bin/env python3
"""Sub-latitude dry/wet land-composition analyzer for a Latitude atlas run.

Whole-band tools (band_balance_analyze.py) average over an entire latitude band.
This one bins LAND by fine |latitude| sub-bands and reports the wet-jungle vs
savanna vs desert/badlands split — the diagnostic for "is the deep equator (0-10
deg) staying rainforest+savanna like Earth, or is desert/badlands leaking in?".

Exact, no color collisions: biome_ids.png encodes the biome INDEX in its RED
channel (see distinct_render.py docstring); biome_palette.json maps index->id.
Do NOT use biomes.png natural colors here (jungle/savanna/desert collide).

Usage:  python3 sublatitude_dry_wet.py <atlas-step-dir>
  where <atlas-step-dir> contains biome_ids.png + biome_palette.json + biomes.txt
"""
import sys, json, re
import numpy as np
from PIL import Image
from collections import Counter

def main(d):
    pal = json.load(open(f"{d}/biome_palette.json"))["biomes"]
    idx2id = {b["index"]: b["biome_id"] for b in pal}
    txt = open(f"{d}/biomes.txt").read()
    radius = int(re.search(r"radiusBlocks=(\d+)", txt).group(1))
    R = np.array(Image.open(f"{d}/biome_ids.png").convert("RGB"))[:, :, 0]
    H, _ = R.shape
    assert len(np.unique(R)) <= len(pal), "R-channel index out of palette range"
    zs = -radius + np.arange(H) * (2.0 * radius / (H - 1))
    lat = np.abs(zs) / radius * 90.0

    def cat(bid):
        s = bid.split(":", 1)[-1]
        if "ocean" in s or s == "river" or s.endswith("_river") or "beach" in s or "shore" in s:
            return "water"
        if any(k in s for k in ["jungle", "rainforest", "tropic", "mangrove"]):
            return "wet_jungle"
        if "savanna" in s:
            return "savanna"
        if any(k in s for k in ["desert", "badlands", "mesa", "dune", "wasteland", "dryland", "arid"]):
            return "desert_badlands"
        if any(k in s for k in ["snow", "frozen", "ice_spikes", "ice_", "grove", "glacar", "taiga"]):
            return "cold"
        return "other_land"

    idx2cat = {i: cat(b) for i, b in idx2id.items()}
    catR = np.vectorize(lambda v: idx2cat.get(int(v), "other_land"))(R)
    edges = [0, 5, 10, 15, 20, 23.5, 30, 35, 40, 90]
    landcats = ["wet_jungle", "savanna", "desert_badlands", "other_land", "cold"]
    print(f"seed atlas: {d}")
    print(f"{'lat band':>9} | {'wetJung':>7} {'savanna':>7} {'des/bl':>7} {'DRY':>6} | {'other':>6} {'cold':>6} | nLand")
    print("-" * 78)
    for i in range(len(edges) - 1):
        lo, hi = edges[i], edges[i + 1]
        rows = np.where((lat >= lo) & (lat < hi))[0]
        if len(rows) == 0:
            continue
        cnt = Counter(catR[rows].ravel())
        land = sum(cnt.get(k, 0) for k in landcats)
        if land == 0:
            continue
        p = lambda k: 100.0 * cnt.get(k, 0) / land
        dry = p("savanna") + p("desert_badlands")
        print(f"{lo:>4.0f}-{hi:<4.0f}| {p('wet_jungle'):>6.1f}% {p('savanna'):>6.1f}% "
              f"{p('desert_badlands'):>6.1f}% {dry:>5.1f}% | {p('other_land'):>5.1f}% {p('cold'):>5.1f}% | {land}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__); sys.exit(1)
    main(sys.argv[1].rstrip("/"))
