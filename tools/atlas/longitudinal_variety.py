#!/usr/bin/env python3
"""Measure LONGITUDINAL variety (anti-striping) within a latitude band.

Striping = a band that is uniform along its length (same biome at every longitude).
Metrics per band, land-only:
  - per-biome shares (top N) and the single largest biome share (high = bland/striped)
  - distinct land biome count
  - mean horizontal run length in blocks (long runs = striped)
"""
from __future__ import annotations
import json, re, sys
from collections import Counter
from pathlib import Path
from PIL import Image

BANDS = [("tropical",0.0,23.5),("subtropical",23.5,35.0),("temperate",35.0,50.0),
         ("subpolar",50.0,66.5),("polar",66.5,90.0)]

def geom(path: Path):
    s=str(path); r=re.search(r"R(\d+)",s); st=re.search(r"step(\d+)",s)
    radius=int(r.group(1)) if r else None; step=int(st.group(1)) if st else None
    txt=path/"biomes.txt"
    if (radius is None or step is None) and txt.exists():
        b=txt.read_text()
        if radius is None: m=re.search(r"radiusBlocks=(\d+)",b); radius=int(m.group(1)) if m else None
        if step is None: m=re.search(r"stepBlocks=(\d+)",b); step=int(m.group(1)) if m else None
    return radius, step

def band_of(deg):
    for n,lo,hi in BANDS:
        if deg<hi: return n
    return "polar"

def is_land(bid): return "ocean" not in bid and bid not in ("minecraft:river","minecraft:frozen_river")

def analyze(path: Path, band_name="tropical", inner_only=False):
    radius, step = geom(path)
    pal={int(p["index"]):p["biome_id"] for p in json.loads((path/"biome_palette.json").read_text())["biomes"]}
    img=Image.open(path/"biome_ids.png").convert("RGB"); w,h=img.size
    px=[rgb[0] for rgb in img.getdata()]
    counts=Counter(); runs=[];
    for iz in range(h):
        bz=-radius+iz*step; lat=min(90.0,abs(bz)/radius*90.0)
        if band_of(lat)!=band_name: continue
        if inner_only and lat>=12.0: continue
        # horizontal run lengths over land pixels in this row
        prev=None; run=0
        for ix in range(w):
            bid=pal.get(px[iz*w+ix],"?")
            if not is_land(bid):
                if run>0: runs.append(run); run=0
                prev=None; continue
            counts[bid]+=1
            if bid==prev: run+=1
            else:
                if run>0: runs.append(run)
                run=1; prev=bid
        if run>0: runs.append(run)
    total=sum(counts.values())
    mean_run_blocks=(sum(runs)/len(runs))*step if runs else 0
    return {"radius":radius,"step":step,"total":total,"counts":counts,
            "distinct":len(counts),"mean_run_blocks":mean_run_blocks}

def report(label, a):
    print(f"== {label}: {('inner-equator(0-12) ' if a.get('inner') else '')}band, land n={a['total']} ==")
    print(f"   distinct land biomes: {a['distinct']}   mean longitudinal run: {a['mean_run_blocks']:.0f} blocks")
    top=a["counts"].most_common(10)
    for bid,c in top:
        print(f"     {bid:40s} {100*c/a['total']:5.1f}%")
    if top:
        print(f"   >> largest single biome share: {100*top[0][1]/a['total']:.1f}%  ({top[0][0]})")

if __name__=="__main__":
    band = sys.argv[1] if len(sys.argv)>1 and not Path(sys.argv[1]).exists() else "tropical"
    dirs = [Path(p) for p in sys.argv[1:] if Path(p).exists()]
    inner = "--inner" in sys.argv
    for d in dirs:
        a=analyze(d, band, inner); a["inner"]=inner
        report(str(d), a); print()
