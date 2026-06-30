#!/usr/bin/env python3
"""Cohesion + representation metrics for one atlas run, as JSON.

COHESION (is the biome map coherent vs confetti?):
  - single_cell_frac: fraction of LAND cells whose biome matches NONE of its 4 neighbours (isolated specks).
    Lower = more cohesive. This is the confetti proxy.
  - per-biome fragment stats: connected-component count + largest-component share, for the biomes with the
    most fragments (the worst offenders).
  - mean_component_size: average connected-component size across land biomes (bigger = more cohesive).

REPRESENTATION (are all biomes shown, is any band a monoculture?):
  - distinct_land_biomes + per-namespace counts.
  - per-band: distinct biome count, dominant biome + its share (monoculture flag if >0.55).
  - starved: biomes present in world_biome_inventory.json (placeable) but absent from the rendered palette.

Usage: cohesion_representation.py <atlas-run-dir> [--prefix step64_]
Prints JSON to stdout.
"""
import sys, os, json, re
import numpy as np
from PIL import Image
from collections import deque, Counter

BANDS = [("tropical",0,23.5),("subtropical",23.5,35),("temperate",35,50),("subpolar",50,66.5),("polar",66.5,90)]

def is_water(bid):
    s = bid.split(":",1)[-1]
    return "ocean" in s or s=="river" or s.endswith("_river") or "beach" in s or "shore" in s

def main(argv):
    d = argv[0]
    prefix = "step64_"
    if "--prefix" in argv: prefix = argv[argv.index("--prefix")+1]
    if not os.path.exists(f"{d}/{prefix}biome_ids.png"):
        for f in os.listdir(d):
            if f.endswith("biome_ids.png"): prefix = f[:-len("biome_ids.png")]; break

    pal = json.load(open(f"{d}/{prefix}biome_palette.json"))["biomes"]
    idx2id = {b["index"]: b["biome_id"] for b in pal}
    txt = open(f"{d}/{prefix}biomes.txt").read()
    radius = int(re.search(r"radiusBlocks=(\d+)", txt).group(1))
    R = np.array(Image.open(f"{d}/{prefix}biome_ids.png").convert("RGB"))[:,:,0].astype(int)
    H, W = R.shape
    zs = -radius + np.arange(H) * (2.0*radius/(H-1))
    lat = np.abs(zs)/radius*90.0

    # land mask
    water_idx = {i for i,b in idx2id.items() if is_water(b)}
    land = ~np.isin(R, list(water_idx)) if water_idx else np.ones_like(R, bool)
    land_n = int(land.sum())

    # --- cohesion: single-cell fraction (4-neighbour) over land ---
    iso = 0
    for r in range(H):
        for c in range(W):
            if not land[r,c]: continue
            v = R[r,c]; same = False
            for dr,dc in ((1,0),(-1,0),(0,1),(0,-1)):
                rr,cc = r+dr,c+dc
                if 0<=rr<H and 0<=cc<W and land[rr,cc] and R[rr,cc]==v: same=True; break
            if not same: iso += 1
    single_cell_frac = iso/max(1,land_n)

    # --- connected components per biome (4-connectivity) over land ---
    seen = np.zeros_like(R, bool)
    comp_sizes_by_biome = {}   # biome_id -> list of component sizes
    for r in range(H):
        for c in range(W):
            if not land[r,c] or seen[r,c]: continue
            v = R[r,c]; q = deque([(r,c)]); seen[r,c]=True; sz=0
            while q:
                y,x = q.popleft(); sz+=1
                for dr,dc in ((1,0),(-1,0),(0,1),(0,-1)):
                    yy,xx = y+dr,x+dc
                    if 0<=yy<H and 0<=xx<W and land[yy,xx] and not seen[yy,xx] and R[yy,xx]==v:
                        seen[yy,xx]=True; q.append((yy,xx))
            comp_sizes_by_biome.setdefault(idx2id.get(int(v),"?"),[]).append(sz)

    all_sizes = [s for v in comp_sizes_by_biome.values() for s in v]
    mean_component_size = float(np.mean(all_sizes)) if all_sizes else 0.0
    # worst-fragmented biomes (most components, and small largest-share)
    frag = []
    for bid, sizes in comp_sizes_by_biome.items():
        tot = sum(sizes)
        frag.append({"biome": bid, "cells": tot, "components": len(sizes),
                     "largest_share": round(max(sizes)/tot,3),
                     "singletons": sum(1 for s in sizes if s==1)})
    frag.sort(key=lambda x:(-x["components"], x["largest_share"]))

    # --- representation ---
    land_idx = R[land]
    cnt = Counter(int(v) for v in land_idx)
    distinct = [idx2id.get(i,"?") for i in cnt]
    ns = Counter(b.split(":",1)[0] for b in distinct)

    per_band = {}
    for name,lo,hi in BANDS:
        rows = np.where((lat>=lo)&(lat<hi))[0]
        bc = Counter()
        for r in rows:
            for c in range(W):
                if land[r,c]: bc[idx2id.get(int(R[r,c]),"?")]+=1
        tot = sum(bc.values())
        if tot==0: per_band[name]={"land":0,"distinct":0}; continue
        dom,domn = bc.most_common(1)[0]
        per_band[name] = {"land":tot,"distinct":len(bc),
                          "dominant":dom,"dominant_share":round(domn/tot,3),
                          "monoculture": domn/tot>0.55,
                          "top5":[(b,round(n/tot,3)) for b,n in bc.most_common(5)]}

    # starved: inventory (placeable) minus palette-that-appears-on-land
    starved = []
    inv_path = f"{d}/{prefix}world_biome_inventory.json"
    if os.path.exists(inv_path):
        inv = json.load(open(inv_path))
        inv_biomes = set()
        b = inv.get("biomes", inv) if isinstance(inv, dict) else inv
        for e in (b if isinstance(b,list) else []):
            bid = e.get("biome_id") or e.get("id") or (e if isinstance(e,str) else None)
            if bid: inv_biomes.add(bid)
        # "appearing" = every biome in the rendered palette (land OR water), so a water biome that shows up
        # as ocean isn't mis-flagged as starved. Then drop water ids from the starved list entirely —
        # starvation is about land biomes that never got placed.
        appearing = set(idx2id.values())
        starved = sorted(b for b in (inv_biomes - appearing) if not is_water(b))

    out = {
        "run_dir": d, "radius": radius, "img_w": W, "img_h": H, "land_cells": land_n,
        "cohesion": {
            "single_cell_frac": round(single_cell_frac,4),
            "mean_component_size": round(mean_component_size,2),
            "worst_fragmented": frag[:8],
        },
        "representation": {
            "distinct_land_biomes": len(distinct),
            "by_namespace": dict(ns),
            "per_band": per_band,
            "starved_count": len(starved),
            "starved": starved[:60],
        },
    }
    print(json.dumps(out, indent=2))

if __name__ == "__main__":
    main(sys.argv[1:])
