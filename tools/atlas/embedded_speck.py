#!/usr/bin/env python3
"""Band-resolved TRUE-confetti metric for a Latitude atlas run.

Confetti that matters = a small component of biome A fully enclosed by a SINGLE
other land biome B (a marooned speck), NOT a fragmented boundary between regions.
Reads exact biome_ids.png (red channel) + biome_palette.json. Reports embedded
specks overall, per latitude band, and for the canonical user-complaint pairs
(jungle-in-arid, taiga-in-plains/forest, old_growth_pine_taiga anywhere).

Usage: embedded_speck.py <run_dir> [--kmax N]
Geometry (radius/step) parsed from path or biomes.txt.
"""
from __future__ import annotations
import json, re, sys
from collections import Counter
from pathlib import Path
from PIL import Image

BANDS = [("tropical",0.0,23.5),("subtropical",23.5,35.0),("temperate",35.0,50.0),
         ("subpolar",50.0,66.5),("polar",66.5,90.0)]
def band_of(deg):
    for n,lo,hi in BANDS:
        if deg<hi: return n
    return "polar"

def geom(run: Path):
    s=str(run); r=re.search(r"R(\d+)",s); st=re.search(r"step(\d+)",s)
    radius=int(r.group(1)) if r else None; step=int(st.group(1)) if st else None
    txt=run/"biomes.txt"
    if (radius is None or step is None) and txt.exists():
        b=txt.read_text()
        if radius is None: m=re.search(r"radiusBlocks=(\d+)",b); radius=int(m.group(1)) if m else None
        if step is None: m=re.search(r"stepBlocks=(\d+)",b); step=int(m.group(1)) if m else None
    return radius, step

def land(b): return "ocean" not in b and b not in ("minecraft:river","minecraft:frozen_river")
def fam(b):
    for k in ("jungle","badlands","savanna","taiga","sakura","desert"):
        if k in b: return k
    if "forest" in b or b.endswith("plains") or b=="minecraft:plains": return "leafy"
    return b
ARID={"desert","badlands"}; JUNGLE={"jungle"}; PLAINSY={"leafy"}

def main():
    run=Path(sys.argv[1])
    kmax=10
    if "--kmax" in sys.argv: kmax=int(sys.argv[sys.argv.index("--kmax")+1])
    radius,step=geom(run)
    pal={int(p["index"]):p["biome_id"] for p in json.loads((run/"biome_palette.json").read_text())["biomes"]}
    img=Image.open(run/"biome_ids.png").convert("RGB"); w,h=img.size
    px=[r[0] for r in img.getdata()]
    def lat_of(iz): return min(90.0, abs(-radius+iz*step)/radius*90.0)
    vis=bytearray(w*h); comps=[]
    for s in range(w*h):
        if vis[s]: continue
        idx=px[s]; vis[s]=1; q=[s]; c=0
        while c<len(q):
            p=q[c]; c+=1; x=p%w; y=p//w
            for nx,ny in((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
                if 0<=nx<w and 0<=ny<h:
                    n=ny*w+nx
                    if not vis[n] and px[n]==idx: vis[n]=1; q.append(n)
        comps.append((idx,q[:]))
    band_land=Counter()
    for iz in range(h):
        b=band_of(lat_of(iz))
        for ix in range(w):
            if land(pal.get(px[iz*w+ix],"?")): band_land[b]+=1
    band_emb=Counter(); band_embpx=Counter(); band_cross=Counter(); band_crosspx=Counter()
    complaint=Counter()
    for idx,cells in comps:
        bid=pal.get(idx,"?")
        if not land(bid) or len(cells)>kmax: continue
        cs=set(cells); border=Counter(); zsum=0
        for p in cells:
            x=p%w; y=p//w; zsum+=y
            for nx,ny in((x+1,y),(x-1,y),(x,y+1),(x,y-1)):
                if 0<=nx<w and 0<=ny<h:
                    n=ny*w+nx
                    if n not in cs:
                        nb=pal.get(px[n],"?")
                        if land(nb): border[nb]+=1
        if not border or len(border)!=1: continue
        out=next(iter(border))
        b=band_of(lat_of(zsum//len(cells)))
        band_emb[b]+=1; band_embpx[b]+=len(cells)
        if fam(bid)!=fam(out):
            band_cross[b]+=1; band_crosspx[b]+=len(cells)
        if fam(bid) in JUNGLE and fam(out) in ARID: complaint["jungle_in_arid"]+=1
        if fam(bid)=="taiga" and fam(out) in PLAINSY: complaint["taiga_in_plains/forest"]+=1
        if bid=="minecraft:old_growth_pine_taiga": complaint["old_growth_pine_taiga_speck"]+=1
        if "sparse_jungle" in bid and fam(out) in ARID: complaint["sparse_jungle_in_arid"]+=1
    print(f"== {run}  (R{radius} step{step} {w}x{h})  embedded specks <= {kmax}px, single-enclosure ==")
    print(f"{'band':12s} {'land_px':>8s} {'embed':>6s} {'embed%':>7s} {'crossFAM':>8s} {'cross%':>7s}")
    for b,_,_ in BANDS:
        lp=band_land.get(b,0)
        if not lp: continue
        print(f"{b:12s} {lp:8d} {band_emb.get(b,0):6d} {100*band_embpx.get(b,0)/lp:6.3f}% {band_cross.get(b,0):8d} {100*band_crosspx.get(b,0)/lp:6.3f}%")
    tot=sum(band_land.values()) or 1
    print(f"{'TOTAL':12s} {tot:8d} {sum(band_emb.values()):6d} {100*sum(band_embpx.values())/tot:6.3f}% {sum(band_cross.values()):8d} {100*sum(band_crosspx.values())/tot:6.3f}%")
    print("canonical user-complaint specks:", dict(complaint) if complaint else "NONE")

if __name__=="__main__":
    main()
