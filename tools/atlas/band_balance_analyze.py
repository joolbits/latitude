#!/usr/bin/env python3
"""Band-aware Latitude atlas balance analyzer.

Measures the *product* complaints that the generic confetti tool can't see:
  - tropical/equatorial arid share (desert+badlands intruding on humid equator)
  - cross-province "islands" (jungle blobs marooned in arid, desert blobs in jungle)
  - per-biome fragmentation (confetti) focused on the named offenders

Input: one or two final R*/step*/ atlas dirs containing biome_ids.png +
biome_palette.json (+ world_biome_inventory.json optional).

Pixel->latitude geometry mirrors BiomePreviewExporter: blockZ = -radius + imageZ*step,
latDeg = |blockZ|/radius*90. Radius/step are parsed from the dir path (R<rad>/step<step>).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image

# Latitude band edges (degrees), from util/LatitudeBands.java
BANDS = [
    ("tropical", 0.0, 23.5),
    ("subtropical", 23.5, 35.0),
    ("temperate", 35.0, 50.0),
    ("subpolar", 50.0, 66.5),
    ("polar", 66.5, 90.0),
]

JUNGLE = {"minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
          "biomesoplenty:tropics", "biomesoplenty:rainforest"}
DESERT = {"minecraft:desert"}
BADLANDS = {"minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands"}
SAVANNA = {"minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"}
TAIGA = {"minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
         "minecraft:snowy_taiga"}
OCEAN_RIVER = {"minecraft:river", "minecraft:frozen_river"}

def family(bid: str) -> str:
    if bid in JUNGLE: return "jungle"
    if bid in DESERT: return "desert"
    if bid in BADLANDS: return "badlands"
    if bid in SAVANNA: return "savanna"
    if bid in TAIGA: return "taiga"
    if "ocean" in bid: return "ocean"
    if bid in OCEAN_RIVER: return "river"
    return "other"

ARID = {"desert", "badlands"}

def band_for(deg: float) -> str:
    for name, lo, hi in BANDS:
        if deg < hi:
            return name
    return "polar"

def parse_geom(path: Path):
    s = str(path)
    r = re.search(r"/R(\d+)/", s) or re.search(r"R(\d+)", s)
    st = re.search(r"step(\d+)", s)
    radius = int(r.group(1)) if r else None
    step = int(st.group(1)) if st else None
    # Fallback: read radiusBlocks/stepBlocks from biomes.txt (handles flattened dirs).
    txt = path / "biomes.txt"
    if (radius is None or step is None) and txt.exists():
        body = txt.read_text()
        if radius is None:
            m = re.search(r"radiusBlocks=(\d+)", body)
            radius = int(m.group(1)) if m else None
        if step is None:
            m = re.search(r"stepBlocks=(\d+)", body)
            step = int(m.group(1)) if m else None
    return radius, step

def load(path: Path):
    palette = json.loads((path / "biome_palette.json").read_text())["biomes"]
    idx_to_id = {int(p["index"]): p["biome_id"] for p in palette}
    img = Image.open(path / "biome_ids.png").convert("RGB")
    w, h = img.size
    px = [rgb[0] for rgb in img.getdata()]  # red channel == biome index
    return idx_to_id, px, w, h

def analyze(path: Path, window=2):
    radius, step = parse_geom(path)
    idx_to_id, px, w, h = load(path)
    # exporter: width=height=(2*radius/step)+1, blockZ = -radius + imageZ*step
    def lat_of_row(iz):
        bz = -radius + iz * step
        return min(90.0, abs(bz) / radius * 90.0)

    # land mask (exclude ocean/river for composition shares)
    def is_land(bid):
        f = family(bid)
        return f not in ("ocean", "river") and "ocean" not in bid

    band_counts = defaultdict(Counter)      # band -> family -> count
    band_land_total = Counter()
    # finer tropical split: inner equator (0-12deg) vs outer tropics (12-23.5deg)
    trop_sub = defaultdict(Counter)         # "inner"/"outer" -> family -> count
    trop_sub_total = Counter()
    for iz in range(h):
        lat = lat_of_row(iz)
        band = band_for(lat)
        base = iz * w
        for ix in range(w):
            bid = idx_to_id.get(px[base + ix], "?")
            if not is_land(bid):
                continue
            fam = family(bid)
            band_counts[band][fam] += 1
            band_land_total[band] += 1
            if band == "tropical":
                key = "inner(0-12)" if lat < 12.0 else "outer(12-23.5)"
                trop_sub[key][fam] += 1
                trop_sub_total[key] += 1

    # cross-province islands: a land pixel whose family is incompatible with the
    # dominant family of its (2*window+1)^2 neighborhood.
    incompat_islands = Counter()  # "jungle_in_arid" / "arid_in_jungle"
    island_examples = defaultdict(list)
    def fam_at(ix, iz):
        bid = idx_to_id.get(px[iz * w + ix], "?")
        return family(bid)
    for iz in range(h):
        for ix in range(w):
            f = fam_at(ix, iz)
            if f not in ("jungle",) and f not in ARID:
                continue
            neigh = Counter()
            for dz in range(-window, window + 1):
                for dx in range(-window, window + 1):
                    nx, nz = ix + dx, iz + dz
                    if 0 <= nx < w and 0 <= nz < h:
                        nf = fam_at(nx, nz)
                        if nf not in ("ocean", "river"):
                            neigh[nf] += 1
            if not neigh:
                continue
            dom = neigh.most_common(1)[0][0]
            if f == "jungle" and dom in ARID and neigh[f] <= neigh[dom] // 2:
                incompat_islands["jungle_in_arid"] += 1
                if len(island_examples["jungle_in_arid"]) < 5:
                    island_examples["jungle_in_arid"].append((-radius + ix*step, -radius + iz*step))
            elif f in ARID and dom == "jungle" and neigh[f] <= neigh[dom] // 2:
                incompat_islands["arid_in_jungle"] += 1
                if len(island_examples["arid_in_jungle"]) < 5:
                    island_examples["arid_in_jungle"].append((-radius + ix*step, -radius + iz*step))

    # confetti / components (4-connected) for named offenders + overall
    visited = bytearray(w * h)
    comp_sizes = defaultdict(list)
    for start in range(w * h):
        if visited[start]:
            continue
        idx = px[start]
        visited[start] = 1
        q = [start]; cur = 0
        while cur < len(q):
            p = q[cur]; cur += 1
            x = p % w; y = p // w
            for nx, ny in ((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
                if 0 <= nx < w and 0 <= ny < h:
                    npos = ny*w+nx
                    if not visited[npos] and px[npos] == idx:
                        visited[npos] = 1; q.append(npos)
        comp_sizes[idx_to_id.get(idx, "?")].append(len(q))

    THRESH = 50
    confetti = {}
    for bid, sizes in comp_sizes.items():
        total = sum(sizes)
        tiny = sum(s for s in sizes if s < THRESH)
        confetti[bid] = {
            "total": total, "components": len(sizes),
            "tiny_pixels": tiny, "tiny_share": tiny/total if total else 0,
            "largest_share": max(sizes)/total if total else 0,
        }
    return {
        "path": str(path), "radius": radius, "step": step, "wh": (w, h),
        "band_counts": {b: dict(c) for b, c in band_counts.items()},
        "band_land_total": dict(band_land_total),
        "trop_sub": {k: dict(v) for k, v in trop_sub.items()},
        "trop_sub_total": dict(trop_sub_total),
        "islands": dict(incompat_islands), "island_examples": {k: v for k, v in island_examples.items()},
        "confetti": confetti,
    }

OFFENDERS = ["minecraft:old_growth_pine_taiga", "minecraft:sparse_jungle", "minecraft:jungle",
             "minecraft:bamboo_jungle", "minecraft:desert", "minecraft:badlands",
             "minecraft:wooded_badlands", "minecraft:eroded_badlands", "minecraft:savanna",
             "minecraft:taiga", "minecraft:dark_forest"]

def pct(n, d):
    return f"{100*n/d:.1f}%" if d else "—"

def report(a):
    L = []
    L.append(f"== {a['path']}  (R{a['radius']} step{a['step']} {a['wh'][0]}x{a['wh'][1]}) ==")
    L.append("Per-band land composition (family shares):")
    fam_order = ["jungle", "desert", "badlands", "savanna", "taiga", "other"]
    for band, _lo, _hi in BANDS:
        tot = a["band_land_total"].get(band, 0)
        if not tot: continue
        c = a["band_counts"].get(band, {})
        parts = "  ".join(f"{f}={pct(c.get(f,0),tot)}" for f in fam_order)
        arid = c.get("desert",0)+c.get("badlands",0)
        L.append(f"  {band:11s} (n={tot:6d}) {parts}   ARID={pct(arid,tot)}")
    L.append("  tropical sub-bands (is the equator core lush?):")
    for key in ("inner(0-12)", "outer(12-23.5)"):
        tot = a["trop_sub_total"].get(key, 0)
        if not tot: continue
        c = a["trop_sub"].get(key, {})
        arid = c.get("desert",0)+c.get("badlands",0)
        L.append(f"    {key:16s} (n={tot:6d}) jungle={pct(c.get('jungle',0),tot)}  "
                 f"savanna={pct(c.get('savanna',0),tot)}  ARID={pct(arid,tot)}")
    L.append("")
    L.append("Cross-province islands (incompatible pixel marooned in foreign family):")
    for k in ("jungle_in_arid", "arid_in_jungle"):
        ex = a["island_examples"].get(k, [])
        L.append(f"  {k}: {a['islands'].get(k,0)} px   e.g. {ex[:3]}")
    L.append("")
    L.append("Fragmentation for named offenders (components / tiny_share / largest_share):")
    for bid in OFFENDERS:
        m = a["confetti"].get(bid)
        if not m or m["total"] == 0: continue
        L.append(f"  {bid:38s} total={m['total']:6d}  comps={m['components']:5d}  "
                 f"tiny={pct(m['tiny_share'],1)}  largest={pct(m['largest_share'],1)}")
    return "\n".join(L)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dirs", type=Path, nargs="+")
    ap.add_argument("--window", type=int, default=2)
    args = ap.parse_args()
    results = [analyze(d, args.window) for d in args.dirs]
    for a in results:
        print(report(a)); print()
    if len(results) == 2:
        b, c = results
        print("== DELTAS (baseline -> candidate) ==")
        for band, _lo, _hi in BANDS:
            tb = b["band_land_total"].get(band, 0); tc = c["band_land_total"].get(band, 0)
            if not tb and not tc: continue
            ab = b["band_counts"].get(band, {}); ac = c["band_counts"].get(band, {})
            aridb = (ab.get("desert",0)+ab.get("badlands",0))
            aridc = (ac.get("desert",0)+ac.get("badlands",0))
            jb = ab.get("jungle",0); jc = ac.get("jungle",0)
            print(f"  {band:11s} ARID {pct(aridb,tb)} -> {pct(aridc,tc)}   "
                  f"jungle {pct(jb,tb)} -> {pct(jc,tc)}")
        print(f"  islands jungle_in_arid {b['islands'].get('jungle_in_arid',0)} -> {c['islands'].get('jungle_in_arid',0)}")
        print(f"  islands arid_in_jungle {b['islands'].get('arid_in_jungle',0)} -> {c['islands'].get('arid_in_jungle',0)}")
        print("  offender components:")
        for bid in OFFENDERS:
            mb = b["confetti"].get(bid); mc = c["confetti"].get(bid)
            if not mb and not mc: continue
            cb = mb["components"] if mb else 0; cc = mc["components"] if mc else 0
            tb2 = mb["total"] if mb else 0; tc2 = mc["total"] if mc else 0
            print(f"    {bid:38s} comps {cb:5d} -> {cc:5d}   total {tb2:6d} -> {tc2:6d}")

if __name__ == "__main__":
    sys.exit(main())
