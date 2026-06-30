#!/usr/bin/env python3
"""Band-correctness invariant check for a Latitude atlas run.

Unlike overrep_rank.py (which flags MONOCULTURE — a biome taking too LARGE a share of a band),
this flags WRONG-BAND CONTAMINATION — a climate-incompatible biome appearing in a band at ANY share.
It is the check that would have caught, the moment they appeared:
  - badlands/desert leaking poleward into TEMPERATE (the 2026-06-25 fix),
  - frozen_river / ice spikes leaking equatorward into TEMPERATE (the ice-spire source),
  - any future "biome in a band it has no business being in" leak.

It reads the atlas biome_ids.png (R channel = biome index) + biome_palette.json, bins LAND by latitude
band, and asserts each band's FORBIDDEN climate classes stay under a small tolerance. Exits non-zero on
any violation, so it can be wired into a release/CI gate.

Usage:  python3 band_correctness_check.py <atlas-step-dir> [--prefix step64_] [--strict]
  <atlas-step-dir> contains <prefix>biome_ids.png, <prefix>biome_palette.json, <prefix>biomes.txt
  --strict : treat WARN-level (edge-tolerance) findings as failures too.

Bands (|lat| deg): tropical 0-23.5, subtropical 23.5-35, temperate 35-50, subpolar 50-66.5, polar 66.5-90.
"""
import sys, os, json, re
import numpy as np
from PIL import Image

BANDS = [
    ("tropical", 0.0, 23.5),
    ("subtropical", 23.5, 35.0),
    ("temperate", 35.0, 50.0),
    ("subpolar", 50.0, 66.5),
    ("polar", 66.5, 90.0),
]

# Climate class membership by short biome name substring (namespace-agnostic).
def classes_for(biome_id):
    s = biome_id.split(":", 1)[-1]
    cls = set()
    # "arid" = the badlands/desert family the tropical-no-arid + poleward clamps govern. Deliberately does
    # NOT include BoP wasteland/dryland/steppe — those are legitimate tropics-transition biomes placed via
    # the lat_trans_arid tags, not a wrong-band leak.
    if any(k in s for k in ("badlands", "mesa", "desert")):
        cls.add("arid")
    if "ice_spikes" in s or "frozen_peaks" in s:
        cls.add("frozen_land")
    if s in ("frozen_river",) or s.endswith("frozen_river"):
        cls.add("frozen_river")
    return cls

def is_water(biome_id):
    s = biome_id.split(":", 1)[-1]
    return "ocean" in s or s == "river" or s.endswith("_river") or "beach" in s or "shore" in s

# Per-band forbidden classes -> (max fraction tolerated, severity). Tolerances absorb the coherent
# noise-warp edge bleed at band boundaries; anything above is a real leak.
#   FAIL = hard violation; WARN = boundary-edge tolerance (fail only under --strict).
# frozen_river is counted against RIVER cells (not land); handled separately below.
RULES = {
    "tropical":    {"arid": (0.005, "FAIL"), "frozen_land": (0.002, "FAIL")},
    "subtropical": {"frozen_land": (0.002, "FAIL")},   # arid is LEGAL here (the arid belt)
    "temperate":   {"arid": (0.010, "FAIL"), "frozen_land": (0.005, "FAIL")},
    "subpolar":    {"arid": (0.010, "FAIL")},          # frozen is legal here
    "polar":       {"arid": (0.010, "FAIL")},
}
# frozen_river is legal only subpolar+; tolerate a small edge bleed in temperate (the 48-50 deg ramp).
FROZEN_RIVER_MAX = {"tropical": 0.0, "subtropical": 0.0, "temperate": 0.10, "subpolar": 1.0, "polar": 1.0}

# FLOORS — guard against the OPPOSITE failure (a band losing a biome it SHOULD have). The arid belt lives in
# subtropical; if over-tightening clamps make it vanish (the "too sparse" regression), flag it. WARN-level.
BAND_FLOORS = {"subtropical": {"arid": 0.15}}  # subtropical land should be >= 15% badlands/desert (a real belt)


def main(argv):
    if not argv:
        print("usage: band_correctness_check.py <atlas-step-dir> [--prefix step64_] [--strict]")
        return 2
    d = argv[0]
    prefix = "step64_"
    strict = "--strict" in argv
    if "--prefix" in argv:
        prefix = argv[argv.index("--prefix") + 1]
    if not os.path.exists(f"{d}/{prefix}biome_ids.png"):
        # auto-detect prefix
        for f in os.listdir(d):
            if f.endswith("biome_ids.png"):
                prefix = f[:-len("biome_ids.png")]
                break

    pal = json.load(open(f"{d}/{prefix}biome_palette.json"))["biomes"]
    idx2id = {b["index"]: b["biome_id"] for b in pal}
    txt = open(f"{d}/{prefix}biomes.txt").read()
    radius = int(re.search(r"radiusBlocks=(\d+)", txt).group(1))
    seed = re.search(r"seed=(-?\d+)", txt).group(1)
    R = np.array(Image.open(f"{d}/{prefix}biome_ids.png").convert("RGB"))[:, :, 0].astype(int)
    H, W = R.shape
    zs = -radius + np.arange(H) * (2.0 * radius / (H - 1))
    lat = np.abs(zs) / radius * 90.0

    print(f"=== Band-correctness check ===  seed={seed} radius={radius} img={W}x{H}  strict={strict}\n")
    failures = 0
    warnings = 0
    for name, lo, hi in BANDS:
        rows = np.where((lat >= lo) & (lat < hi))[0]
        land = 0
        river = 0
        frozen_river = 0
        class_counts = {}
        for r in rows:
            row = R[r]
            for c in range(W):
                bid = idx2id.get(int(row[c]), "unknown")
                if is_water(bid):
                    s = bid.split(":", 1)[-1]
                    if s == "river" or s.endswith("_river"):
                        river += 1
                        if "frozen_river" in classes_for(bid):
                            frozen_river += 1
                    continue
                land += 1
                for cl in classes_for(bid):
                    class_counts[cl] = class_counts.get(cl, 0) + 1
        print(f"--- {name.upper()} ({lo:.0f}-{hi:.0f})  land={land} river={river} ---")
        rules = RULES.get(name, {})
        for cl, (maxfrac, sev) in rules.items():
            frac = (class_counts.get(cl, 0) / land) if land else 0.0
            ok = frac <= maxfrac
            tag = "OK  " if ok else ("FAIL" if sev == "FAIL" else "WARN")
            if not ok:
                if sev == "FAIL":
                    failures += 1
                else:
                    warnings += 1
            print(f"    [{tag}] {cl:12s} {frac*100:5.2f}%  (max {maxfrac*100:.2f}%)")
        # floors (a band that should HAVE a class but lost it — e.g. the arid belt going too sparse)
        for cl, minfrac in BAND_FLOORS.get(name, {}).items():
            frac = (class_counts.get(cl, 0) / land) if land else 0.0
            ok = frac >= minfrac
            if not ok:
                warnings += 1
            print(f"    [{'OK  ' if ok else 'WARN'}] {cl:12s} {frac*100:5.2f}%  (FLOOR {minfrac*100:.0f}% — below = belt too sparse)")
        # frozen_river vs river cells
        frmax = FROZEN_RIVER_MAX.get(name, 1.0)
        frfrac = (frozen_river / river) if river else 0.0
        if frmax < 1.0:
            ok = frfrac <= frmax
            sev = "FAIL" if name in ("tropical", "subtropical") else "WARN"
            tag = "OK  " if ok else sev
            if not ok:
                if sev == "FAIL":
                    failures += 1
                else:
                    warnings += 1
            print(f"    [{tag}] frozen_river {frfrac*100:5.2f}% of river  (max {frmax*100:.0f}%)")
        print()

    eff_fail = failures + (warnings if strict else 0)
    print(f"=== {'PASS' if eff_fail == 0 else 'FAIL'} ===  hard-failures={failures} warnings={warnings}"
          f"{' (warnings counted as failures: --strict)' if strict else ''}")
    return 1 if eff_fail else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
